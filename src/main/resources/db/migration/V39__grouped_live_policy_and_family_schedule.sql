-- WO-058 live-v4. Additive evidence only: no old manifest, attempt or source is enriched.
create table live_grouped_policy (
    campaign_id uuid primary key references live_campaign(campaign_id) on delete restrict,
    critical_interval_seconds integer not null check (critical_interval_seconds = 60),
    lineup_interval_seconds integer not null check (lineup_interval_seconds = 300),
    intra_group_delay_nanos bigint not null check (intra_group_delay_nanos = 0),
    inter_group_delay_nanos bigint not null check (inter_group_delay_nanos = 3000000000),
    maximum_utilization_percent integer not null check (maximum_utilization_percent = 90),
    qualification_sha256 varchar(64) not null check (qualification_sha256 ~ '^[0-9a-f]{64}$'),
    endpoint_envelopes jsonb not null check (jsonb_typeof(endpoint_envelopes) = 'object')
);

create function validate_live_grouped_policy() returns trigger language plpgsql as $$
declare endpoint_name text; envelope jsonb; field_name text;
begin
    if not exists (select 1 from live_campaign where campaign_id=new.campaign_id
            and policy_version='live-v4' and cycle_interval_seconds=60) then
        raise exception 'grouped profile requires a live-v4 sixty-second manifest';
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
create trigger live_grouped_policy_validate before insert on live_grouped_policy
    for each row execute function validate_live_grouped_policy();

create function require_live_grouped_policy() returns trigger language plpgsql as $$
begin
    if new.policy_version='live-v4' and not exists(select 1 from live_grouped_policy where campaign_id=new.campaign_id) then
        raise exception 'live-v4 requires its immutable qualified grouped profile';
    end if;
    return new;
end $$;
create constraint trigger live_grouped_policy_required after insert on live_campaign
    deferrable initially deferred for each row execute function require_live_grouped_policy();

create table live_call_group (
    group_id uuid primary key,
    campaign_id uuid not null references live_grouped_policy(campaign_id) on delete restrict,
    canonical_event_id uuid not null,
    group_sequence bigint not null check (group_sequence >= 0),
    owner_instance_id uuid not null,
    generation bigint not null check (generation > 0),
    created_at timestamptz not null,
    foreign key(campaign_id,canonical_event_id) references live_event(campaign_id,canonical_event_id) on delete restrict,
    unique(campaign_id,group_sequence),
    unique(group_id,campaign_id,canonical_event_id,group_sequence,owner_instance_id,generation)
);
alter table live_call add column group_id uuid;
alter table live_call add column group_sequence bigint;
alter table live_call add column group_ordinal smallint;
alter table live_call add constraint live_call_group_reference_check check (
    (group_id is null and group_sequence is null and group_ordinal is null)
    or (group_id is not null and group_sequence >= 0 and group_sequence is not null
        and group_ordinal between 0 and 3 and group_ordinal is not null));
alter table live_call add constraint live_call_group_reference_fk
    foreign key(group_id,campaign_id,canonical_event_id,group_sequence,owner_instance_id,generation)
    references live_call_group(group_id,campaign_id,canonical_event_id,group_sequence,owner_instance_id,generation) on delete restrict;
create unique index live_call_group_endpoint_once on live_call(group_id,endpoint) where group_id is not null;
create unique index live_call_group_ordinal_once on live_call(group_id,group_ordinal) where group_id is not null;

create function validate_live_group_call() returns trigger language plpgsql as $$
declare expected_ordinal bigint;
begin
    if (select policy_version='live-v4' from live_campaign where campaign_id=new.campaign_id)
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
create trigger live_group_call_validate before insert on live_call for each row execute function validate_live_group_call();

create table live_family_schedule (
    campaign_id uuid not null,
    canonical_event_id uuid not null,
    endpoint varchar(32) not null check (endpoint in ('EVENT_DETAILS','EVENT_INCIDENTS','EVENT_STATISTICS','EVENT_LINEUPS')),
    next_due_at timestamptz,
    interval_seconds integer not null check (interval_seconds between 1 and 3600),
    missed_cycles bigint not null check (missed_cycles >= 0),
    changed_at timestamptz not null,
    owner_instance_id uuid not null,
    generation bigint not null check (generation > 0),
    primary key(campaign_id,canonical_event_id,endpoint),
    foreign key(campaign_id,canonical_event_id) references live_event(campaign_id,canonical_event_id) on delete restrict
);
create table live_family_schedule_revision (
    campaign_id uuid not null,
    revision bigint not null,
    canonical_event_id uuid not null,
    endpoint varchar(32) not null,
    next_due_at timestamptz,
    interval_seconds integer not null check (interval_seconds between 1 and 3600),
    missed_cycles bigint not null check (missed_cycles >= 0),
    primary key(campaign_id,revision),
    foreign key(campaign_id,revision) references live_transition(campaign_id,revision) on delete restrict,
    foreign key(campaign_id,canonical_event_id,endpoint) references live_family_schedule(campaign_id,canonical_event_id,endpoint) on delete restrict
);
create function reject_grouped_live_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception 'grouped live evidence is append-only'; end $$;
create trigger live_grouped_policy_immutable before update or delete on live_grouped_policy
    for each row execute function reject_grouped_live_evidence_mutation();
create trigger live_call_group_immutable before update or delete on live_call_group
    for each row execute function reject_grouped_live_evidence_mutation();
create trigger live_family_schedule_revision_immutable before update or delete on live_family_schedule_revision
    for each row execute function reject_grouped_live_evidence_mutation();
do $$ declare table_name text; begin
    foreach table_name in array array['live_grouped_policy','live_call_group','live_family_schedule','live_family_schedule_revision'] loop
        execute format('create trigger %I before truncate on %I for each statement execute function reject_grouped_live_evidence_mutation()',
            table_name || '_reject_truncate',table_name);
    end loop;
end $$;

comment on table live_family_schedule is 'Scheduler-owned current per-family projection. No transport trigger or automatic recovery.';
comment on table live_family_schedule_revision is 'Append-only schedule decisions correlated to the campaign revision.';
comment on table live_call_group is 'Server-created immutable same-event ordered call groups, live-v4 only.';
