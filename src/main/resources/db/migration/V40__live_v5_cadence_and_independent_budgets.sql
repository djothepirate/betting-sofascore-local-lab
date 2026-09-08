-- WO-058 live-v5. New policy only; no historical manifest, hash or observation is rewritten.
alter table live_campaign drop constraint live_campaign_maximum_calls_per_event_check;
alter table live_campaign add constraint live_campaign_maximum_calls_per_event_check
    check (maximum_calls_per_event between 4 and case when policy_version='live-v5' then 2500 else 1000 end);
alter table live_campaign drop constraint live_campaign_maximum_calls_check;
alter table live_campaign add constraint live_campaign_maximum_calls_check
    check (maximum_calls between 4 and case when policy_version='live-v5' then 20000 else 3000 end);
alter table live_campaign add constraint live_campaign_v5_policy_bounds_check
    check (policy_version<>'live-v5' or (cycle_interval_seconds=100
        and qualified_match_capacity<=20 and maximum_bytes<=15728640000));

-- The byte budget remains independent of the call budget. Per-receipt bounds and
-- received_bytes<=maximum_bytes keep their existing meaning for every policy.
alter table live_event drop constraint live_event_reserved_calls_check;
alter table live_event add constraint live_event_reserved_calls_check check (reserved_calls between 0 and 2500);
create function validate_live_event_policy_budget() returns trigger language plpgsql as $$
declare policy text; per_event_limit integer;
begin
    select policy_version,maximum_calls_per_event into strict policy,per_event_limit
        from live_campaign where campaign_id=new.campaign_id;
    if (policy='live-v5' and new.reserved_calls>per_event_limit)
            or (policy<>'live-v5' and new.reserved_calls>1000) then
        raise exception 'event call counter exceeds its policy budget';
    end if;
    return new;
end $$;
create trigger live_event_policy_budget_validate before insert or update of reserved_calls on live_event
    for each row execute function validate_live_event_policy_budget();

alter table live_grouped_policy drop constraint live_grouped_policy_critical_interval_seconds_check;
alter table live_grouped_policy add constraint live_grouped_policy_critical_interval_seconds_check
    check (critical_interval_seconds in (60,100));
alter table live_grouped_policy drop constraint live_grouped_policy_inter_group_delay_nanos_check;
alter table live_grouped_policy add constraint live_grouped_policy_inter_group_delay_nanos_check
    check (inter_group_delay_nanos in (1000000000,3000000000));

create or replace function validate_live_grouped_policy() returns trigger language plpgsql as $$
declare endpoint_name text; envelope jsonb; field_name text;
begin
    if not exists (select 1 from live_campaign where campaign_id=new.campaign_id
            and ((policy_version='live-v4' and cycle_interval_seconds=60
                    and new.critical_interval_seconds=60 and new.inter_group_delay_nanos=3000000000)
                or (policy_version='live-v5' and cycle_interval_seconds=100
                    and new.critical_interval_seconds=100 and new.inter_group_delay_nanos=1000000000))) then
        raise exception 'grouped profile cadence and delay must match its manifest policy';
    end if;
    if (select count(*) from jsonb_object_keys(new.endpoint_envelopes)) <> 4 then
        raise exception 'four grouped endpoint envelopes required';
    end if;
    foreach endpoint_name in array array['EVENT_DETAILS','EVENT_INCIDENTS','EVENT_STATISTICS','EVENT_LINEUPS'] loop
        envelope := new.endpoint_envelopes -> endpoint_name;
        if envelope is null or jsonb_typeof(envelope) <> 'object'
            or (select count(*) from jsonb_object_keys(envelope)) <> 2 then
            raise exception 'invalid grouped endpoint envelope';
        end if;
        foreach field_name in array array['requestNanos','processingNanos'] loop
            if jsonb_typeof(envelope -> field_name) is distinct from 'number'
                    or (envelope ->> field_name) !~ '^[0-9]+$' then
                raise exception 'grouped envelope must be integer nanoseconds';
            end if;
        end loop;
        if (envelope ->> 'requestNanos')::numeric not between 1 and 10000000000
            or (envelope ->> 'processingNanos')::numeric not between 0 and 60000000000 then
            raise exception 'grouped envelope outside accepted bounds';
        end if;
    end loop;
    return new;
end $$;

create or replace function require_live_grouped_policy() returns trigger language plpgsql as $$
begin
    if new.policy_version in ('live-v4','live-v5')
            and not exists(select 1 from live_grouped_policy where campaign_id=new.campaign_id) then
        raise exception '% requires its immutable qualified grouped profile',new.policy_version;
    end if;
    return new;
end $$;

create or replace function validate_live_group_call() returns trigger language plpgsql as $$
declare expected_ordinal bigint;
begin
    if (select policy_version in ('live-v4','live-v5') from live_campaign where campaign_id=new.campaign_id)
            is distinct from (new.group_id is not null) then
        raise exception 'call group policy does not match manifest';
    end if;
    if new.group_id is not null then
        perform 1 from live_call_group where group_id=new.group_id for update;
        select count(*) into expected_ordinal from live_call where group_id=new.group_id;
        if new.group_ordinal <> expected_ordinal or (new.endpoint='EVENT_DETAILS' and new.group_ordinal<>0) then
            raise exception 'group endpoints must be consecutive with J4 first when present';
        end if;
        if exists(select 1 from live_call_group where campaign_id=new.campaign_id and group_sequence>new.group_sequence) then
            raise exception 'a completed group cannot be resumed';
        end if;
    end if;
    return new;
end $$;

comment on table live_grouped_policy is 'Immutable qualified policy: live-v4 60 s / 3 s; live-v5 100 s / 1 s. Historical rows retain their original values.';
comment on table live_call_group is 'Server-created immutable same-event ordered call groups for live-v4 and live-v5.';
