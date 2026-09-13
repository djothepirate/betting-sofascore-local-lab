-- WO-058 live-v9: V9 is a campaign/scheduling policy only.  It reuses the
-- exact, already qualified local live-v8 pressure envelope: 60-second groups,
-- four 500 ms terminal fences, one 1 s static inter-group reserve, and the
-- shared live-v8 departure profile (45 / 60 s, 2 756 / h).  This migration
-- neither raises a local budget nor creates a new provider identity, circuit,
-- retry, rearm, proxy, or transport path.
--
-- In particular, provider_departure_reservation.admission_profile remains
-- 'live-v8' for V9 departures.  V48 and V50 deliberately remain unchanged:
-- their durable accounting continues to identify the pressure profile, not
-- the campaign policy that selected it.  Historical manifests and evidence are
-- not rewritten.

alter table public.live_campaign drop constraint live_campaign_maximum_calls_per_event_check;
alter table public.live_campaign add constraint live_campaign_maximum_calls_per_event_check
    check(maximum_calls_per_event between 4 and case when policy_version in
        ('live-v5','live-v6','live-v7','live-v8','live-v9') then 2500 else 1000 end);

alter table public.live_campaign drop constraint live_campaign_maximum_calls_check;
alter table public.live_campaign add constraint live_campaign_maximum_calls_check
    check(maximum_calls between 4 and case when policy_version in
        ('live-v5','live-v6','live-v7','live-v8','live-v9') then 20000 else 3000 end);

-- Keep V49's V8-specific constraint intact.  V9 receives an independent
-- identical bound so a later V9-only revision cannot silently widen V8.
alter table public.live_campaign add constraint live_campaign_v9_policy_bounds_check
    check(policy_version<>'live-v9' or (cycle_interval_seconds=60
        and qualified_match_capacity<=10 and maximum_bytes<=15728640000));

create or replace function validate_live_event_policy_budget() returns trigger language plpgsql as $$
declare policy text; per_event_limit integer;
begin
    select policy_version,maximum_calls_per_event into strict policy,per_event_limit
        from public.live_campaign where campaign_id=new.campaign_id;
    if (policy in ('live-v5','live-v6','live-v7','live-v8','live-v9') and new.reserved_calls>per_event_limit)
            or (policy not in ('live-v5','live-v6','live-v7','live-v8','live-v9') and new.reserved_calls>1000) then
        raise exception 'event call counter exceeds its policy budget';
    end if;
    return new;
end $$;

create or replace function validate_live_grouped_policy() returns trigger language plpgsql as $$
declare endpoint_name text; envelope jsonb; field_name text;
    policy text; qualified_capacity integer; cycle_interval integer; v8_pressure_group_nanos numeric := 0;
begin
    select policy_version,qualified_match_capacity,cycle_interval_seconds into strict policy,qualified_capacity,cycle_interval
        from public.live_campaign where campaign_id=new.campaign_id;
    if not (
            (policy='live-v4' and cycle_interval=60
                    and new.critical_interval_seconds=60 and new.lineup_interval_seconds=300 and new.inter_group_delay_nanos=3000000000)
                or (policy in ('live-v5','live-v6') and cycle_interval=100
                    and new.critical_interval_seconds=100 and new.lineup_interval_seconds=300 and new.inter_group_delay_nanos=1000000000)
                or (policy='live-v7' and cycle_interval=60
                    and new.critical_interval_seconds=60 and new.lineup_interval_seconds=60 and new.inter_group_delay_nanos=1000000000)
                or (policy in ('live-v8','live-v9') and cycle_interval=60
                    and new.critical_interval_seconds=60 and new.lineup_interval_seconds=60 and new.inter_group_delay_nanos=500000000)
        ) then
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
        if policy in ('live-v8','live-v9') then
            v8_pressure_group_nanos := v8_pressure_group_nanos
                + (envelope ->> 'requestNanos')::numeric
                + (envelope ->> 'processingNanos')::numeric
                + 500000000;
        end if;
    end loop;
    if policy in ('live-v8','live-v9') then
        -- V9 deliberately reuses the V8 one-second reservation, distinct from
        -- the four terminal fences.  It preserves the effective 500 ms closing
        -- fence when the normal J4 uses bounded worker-start jitter.
        v8_pressure_group_nanos := v8_pressure_group_nanos + 1000000000;
        if qualified_capacity::numeric * v8_pressure_group_nanos > 60000000000 then
            raise exception 'live-v8 grouped envelopes plus worker-start reserve exceed the sixty-second capacity window';
        end if;
    end if;
    return new;
end $$;

create or replace function require_live_grouped_policy() returns trigger language plpgsql as $$
begin
    if new.policy_version in ('live-v4','live-v5','live-v6','live-v7','live-v8','live-v9')
            and not exists(select 1 from public.live_grouped_policy where campaign_id=new.campaign_id) then
        raise exception '% requires its immutable qualified grouped profile',new.policy_version;
    end if;
    return new;
end $$;

create or replace function validate_live_group_call() returns trigger language plpgsql as $$
declare expected_ordinal bigint;
begin
    if (select policy_version in ('live-v4','live-v5','live-v6','live-v7','live-v8','live-v9')
            from public.live_campaign where campaign_id=new.campaign_id)
            is distinct from (new.group_id is not null) then
        raise exception 'call group policy does not match manifest';
    end if;
    if new.group_id is not null then
        perform 1 from public.live_call_group where group_id=new.group_id for update;
        select count(*) into expected_ordinal from public.live_call where group_id=new.group_id;
        if new.group_ordinal <> expected_ordinal or (new.endpoint='EVENT_DETAILS' and new.group_ordinal<>0) then
            raise exception 'group endpoints must be consecutive with J4 first when present';
        end if;
        if exists(select 1 from public.live_call_group where campaign_id=new.campaign_id and group_sequence>new.group_sequence) then
            raise exception 'a completed group cannot be resumed';
        end if;
    end if;
    return new;
end $$;

comment on table public.live_grouped_policy is
    'Immutable qualified grouped policy: V4 60 s / 3 s, V5-V6 100 s / 1 s, V7 60 s / 1 s, V8 60 s / 0.5 s, V9 reuses the exact V8 local pressure profile. V8/V9 require capacity times all four exchange envelopes, all four 0.5 s terminal fences, and one independent 1 s scheduling slot reserve within 60 seconds.';
