-- WO-060: additive live-v11 policy. Historic manifests and qualification hashes are retained.
alter table public.live_campaign drop constraint live_campaign_maximum_calls_per_event_check;
alter table public.live_campaign add constraint live_campaign_maximum_calls_per_event_check
    check(maximum_calls_per_event between 4 and case when policy_version in
        ('live-v5','live-v6','live-v7','live-v8','live-v9','live-v10','live-v11') then 2500 else 1000 end);

alter table public.live_campaign drop constraint live_campaign_maximum_calls_check;
alter table public.live_campaign add constraint live_campaign_maximum_calls_check
    check(maximum_calls between 4 and case when policy_version in
        ('live-v5','live-v6','live-v7','live-v8','live-v9','live-v10','live-v11') then 20000 else 3000 end);

alter table public.live_campaign add constraint live_campaign_v11_policy_bounds_check
    check(policy_version<>'live-v11' or (cycle_interval_seconds=60
        and qualified_match_capacity<=8 and maximum_bytes<=15728640000));

create or replace function validate_live_event_policy_budget() returns trigger language plpgsql as $$
declare policy text; per_event_limit integer;
begin
    select policy_version,maximum_calls_per_event into strict policy,per_event_limit
        from public.live_campaign where campaign_id=new.campaign_id;
    if (policy in ('live-v5','live-v6','live-v7','live-v8','live-v9','live-v10','live-v11') and new.reserved_calls>per_event_limit)
            or (policy not in ('live-v5','live-v6','live-v7','live-v8','live-v9','live-v10','live-v11') and new.reserved_calls>1000) then
        raise exception 'event call counter exceeds its policy budget';
    end if;
    return new;
end $$;

create or replace function validate_live_grouped_policy() returns trigger language plpgsql as $$
declare endpoint_name text; envelope jsonb; field_name text;
    policy text; qualified_capacity integer; cycle_interval integer; pressure_group_nanos numeric := 0;
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
                or (policy in ('live-v8','live-v9','live-v10','live-v11') and cycle_interval=60
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
        if policy in ('live-v8','live-v9','live-v10','live-v11') then
            pressure_group_nanos := pressure_group_nanos
                + (envelope ->> 'requestNanos')::numeric
                + (envelope ->> 'processingNanos')::numeric
                + 500000000;
        end if;
    end loop;
    if policy in ('live-v8','live-v9','live-v10','live-v11') then
        pressure_group_nanos := pressure_group_nanos + 1000000000;
        if qualified_capacity::numeric * pressure_group_nanos > 60000000000 then
            if policy='live-v10' then
                raise exception 'live-v10 grouped envelopes plus worker-start reserve exceed the sixty-second capacity window';
            end if;
            raise exception 'live-v8 grouped envelopes plus worker-start reserve exceed the sixty-second capacity window';
        end if;
    end if;
    return new;
end $$;

create or replace function require_live_grouped_policy() returns trigger language plpgsql as $$
begin
    if new.policy_version in ('live-v4','live-v5','live-v6','live-v7','live-v8','live-v9','live-v10','live-v11')
            and not exists(select 1 from public.live_grouped_policy where campaign_id=new.campaign_id) then
        raise exception '% requires its immutable qualified grouped profile',new.policy_version;
    end if;
    return new;
end $$;

create or replace function validate_live_group_call() returns trigger language plpgsql as $$
declare expected_ordinal bigint;
begin
    if (select policy_version in ('live-v4','live-v5','live-v6','live-v7','live-v8','live-v9','live-v10','live-v11')
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


create table j3_live_pause (
    run_id uuid primary key references j3_order(order_id),
    campaign_id uuid not null references live_campaign(campaign_id),
    owner_instance_id uuid not null,
    generation bigint not null check(generation>0),
    phase text not null check(phase in ('REQUESTED','QUIESCENT','J3_ACTIVE','CLEANED','RESUMING','RESUMED','STOPPED')),
    requested_at timestamptz not null,
    deadline_at timestamptz not null check(deadline_at>requested_at),
    changed_at timestamptz not null,
    reason text,
    missed_slots jsonb not null default '[]'::jsonb check(jsonb_typeof(missed_slots)='array')
);
create unique index j3_live_pause_one_active on j3_live_pause(campaign_id)
    where phase not in ('RESUMED','STOPPED');
create table j3_live_pause_transition (
    transition_id bigint generated always as identity primary key,
    run_id uuid not null references j3_live_pause(run_id),
    phase text not null,
    recorded_at timestamptz not null,
    reason text,
    missed_slots jsonb not null
);

create function validate_j3_live_pause() returns trigger language plpgsql as $$
declare g public.provider_campaign_guard%rowtype; o public.j3_order%rowtype;
begin
    if tg_op='DELETE' then raise exception 'J3 live pause evidence is immutable'; end if;
    select * into strict g from public.provider_campaign_guard where singleton_id=1 for update;
    if g.campaign_id is distinct from new.campaign_id or g.owner_instance_id is distinct from new.owner_instance_id
            or g.generation<>new.generation or (g.state<>'OWNED' and new.phase<>'STOPPED') then
        raise exception 'J3 live pause owner is stale';
    end if;
    if tg_op='INSERT' then
        select * into strict o from public.j3_order where order_id=new.run_id;
        if new.phase<>'REQUESTED' or o.state<>'RUNNING' or o.trigger_kind='MANUAL_IMPORT'
                or new.deadline_at>o.deadline or new.changed_at<>new.requested_at
                or not exists(select 1 from public.live_campaign where campaign_id=new.campaign_id
                    and policy_version='live-v11' and state='RUNNING'
                    and owner_instance_id=new.owner_instance_id and generation=new.generation
                    and ends_at>=new.deadline_at) then
            raise exception 'J3 live pause requires a bounded admitted order and live-v11 owner';
        end if;
    else
        if (new.run_id,new.campaign_id,new.owner_instance_id,new.generation,new.requested_at,new.deadline_at)
                is distinct from (old.run_id,old.campaign_id,old.owner_instance_id,old.generation,old.requested_at,old.deadline_at)
                or old.phase in ('RESUMED','STOPPED') or new.changed_at<old.changed_at
                or not (new.phase='STOPPED'
                    or old.phase='REQUESTED' and new.phase='QUIESCENT'
                    or old.phase='QUIESCENT' and new.phase in ('J3_ACTIVE','CLEANED')
                    or old.phase='J3_ACTIVE' and new.phase='CLEANED'
                    or old.phase='CLEANED' and new.phase='RESUMING'
                    or old.phase='RESUMING' and new.phase='RESUMED') then
            raise exception 'J3 live pause transition is invalid';
        end if;
    end if;
    if new.phase='CLEANED' and not exists(
            select 1 from public.j3_order terminal_order join public.j3_collection_run r on r.run_id=terminal_order.order_id
            where terminal_order.order_id=new.run_id and terminal_order.state in ('COMPLETED','FAILED','CANCELLED') and r.state=terminal_order.state) then
        raise exception 'J3 cleanup publication requires its terminal collection and order';
    end if;
    return new;
end $$;
create trigger j3_live_pause_guard before insert or update or delete on j3_live_pause
    for each row execute function validate_j3_live_pause();
create function audit_j3_live_pause() returns trigger language plpgsql as $$
begin
    insert into public.j3_live_pause_transition(run_id,phase,recorded_at,reason,missed_slots)
        values(new.run_id,new.phase,new.changed_at,new.reason,new.missed_slots);
    return new;
end $$;
create trigger j3_live_pause_audit after insert or update on j3_live_pause
    for each row execute function audit_j3_live_pause();
create function reject_j3_pause_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'J3 pause transitions are append-only'; end $$;
create trigger j3_live_pause_transition_immutable before update or delete on j3_live_pause_transition
    for each row execute function reject_j3_pause_evidence_mutation();

-- Defense in depth: live dispatch admission shares the guard row with the pause request.
create function exclude_live_dispatch_during_j3() returns trigger language plpgsql as $$
declare c uuid;
begin
    select campaign_id into strict c from public.live_call where attempt_id=new.attempt_id;
    perform 1 from public.provider_campaign_guard where singleton_id=1 for update;
    if exists(select 1 from public.j3_live_pause where campaign_id=c and phase not in ('RESUMED','STOPPED')) then
        raise exception 'live dispatch is paused for J3';
    end if;
    if exists(select 1 from public.live_call a join public.live_call_group g on g.group_id=a.group_id
            join public.j3_live_pause p on p.campaign_id=a.campaign_id
            where a.attempt_id=new.attempt_id and g.created_at<=p.requested_at) then
        raise exception 'live group was abandoned for J3';
    end if;
    return new;
end $$;
create trigger live_dispatch_j3_exclusion before insert on live_call_dispatch
    for each row execute function exclude_live_dispatch_during_j3();
