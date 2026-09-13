-- WO-058: additive local live evidence; no historical collection is backfilled.
create table provider_campaign_guard (
    singleton_id smallint primary key check (singleton_id = 1),
    state varchar(24) not null check (state in ('FREE','OWNED','CLEANUP_REQUIRED')),
    campaign_id uuid, owner_instance_id uuid, owner_process_id bigint, owner_process_started_at timestamptz,
    generation bigint not null default 0 check (generation >= 0), changed_at timestamptz not null,
    constraint ck_live_guard_owner check (((state = 'FREE' and campaign_id is null
        and owner_instance_id is null and owner_process_id is null and owner_process_started_at is null)
        or (state <> 'FREE' and campaign_id is not null and owner_instance_id is not null
        and owner_process_id > 0 and owner_process_started_at is not null and generation > 0)) is true)
);
insert into provider_campaign_guard(singleton_id,state,changed_at) values (1,'FREE',current_timestamp);

create table live_campaign (
    campaign_id uuid primary key, manifest_sha256 varchar(64) not null check (manifest_sha256 ~ '^[0-9a-f]{64}$'),
    policy_version varchar(96) not null check (policy_version ~ '^[A-Za-z0-9._-]+$'),
    prepared_at timestamptz not null, expires_at timestamptz not null,
    duration_seconds integer not null check (duration_seconds between 1 and 14400),
    maximum_calls_per_event integer not null check (maximum_calls_per_event between 4 and 1000),
    maximum_calls integer not null check (maximum_calls between 4 and 3000),
    maximum_bytes bigint not null check (maximum_bytes > 0), qualified_match_capacity smallint not null check (qualified_match_capacity between 1 and 3),
    target_count smallint not null check (target_count between 1 and qualified_match_capacity),
    request_envelope_nanos bigint not null check (request_envelope_nanos between 1 and 10000000000),
    processing_envelope_nanos bigint not null check (processing_envelope_nanos between 0 and 60000000000),
    qualification_sha256 varchar(64) not null check (qualification_sha256 = '' or qualification_sha256 ~ '^[0-9a-f]{64}$'),
    state varchar(64) not null default 'PREPARED' check (state ~ '^[A-Z0-9_]+$'), reason varchar(96),
    started_at timestamptz, ends_at timestamptz, owner_instance_id uuid, generation bigint,
    reserved_calls integer not null default 0 check (reserved_calls >= 0),
    received_bytes bigint not null default 0 check (received_bytes >= 0),
    revision bigint not null default 0 check (revision >= 0),
    check (expires_at > prepared_at and expires_at <= prepared_at + interval '5 minutes'),
    check (reserved_calls <= maximum_calls), check (received_bytes <= maximum_bytes),
    check (((started_at is null and ends_at is null and owner_instance_id is null and generation is null)
        or (started_at is not null and ends_at = started_at + duration_seconds * interval '1 second'
        and owner_instance_id is not null and generation > 0)) is true)
);
create table live_event (
    campaign_id uuid not null references live_campaign(campaign_id) on delete restrict,
    canonical_event_id uuid not null references canonical_event(id) on delete restrict,
    provider_event_id bigint not null check (provider_event_id between 1 and 999999999),
    target_order smallint not null check (target_order between 0 and 2),
    source_observation_id bigint not null references canonical_event_observation(id) on delete restrict,
    source_snapshot_id bigint not null references provider_snapshot(id) on delete restrict,
    state varchar(64) not null default 'PREPARED' check (state ~ '^[A-Z0-9_]+$'), reason varchar(96),
    reserved_calls integer not null default 0 check (reserved_calls between 0 and 1000),
    received_bytes bigint not null default 0 check (received_bytes >= 0), next_due_at timestamptz,
    missed_cycles bigint not null default 0 check (missed_cycles >= 0), final_complete boolean not null default false,
    primary key (campaign_id, canonical_event_id), unique (campaign_id, target_order)
);
create table live_call (
    attempt_id uuid primary key, ordinal bigint generated always as identity unique,
    campaign_id uuid not null, canonical_event_id uuid not null,
    cycle_number bigint not null check (cycle_number >= 0),
    endpoint varchar(32) not null check (endpoint in ('EVENT_DETAILS','EVENT_STATISTICS','EVENT_INCIDENTS','EVENT_LINEUPS')),
    kind varchar(96) not null check (kind ~ '^[A-Za-z0-9._-]+$'),
    due_at timestamptz not null, reserved_at timestamptz not null, final_cycle boolean not null,
    owner_instance_id uuid not null, generation bigint not null check (generation > 0),
    foreign key (campaign_id,canonical_event_id) references live_event(campaign_id,canonical_event_id) on delete restrict,
    unique (campaign_id,canonical_event_id,cycle_number,endpoint)
);
create table live_call_dispatch (
    attempt_id uuid primary key references live_call(attempt_id) on delete restrict,
    authorized_at timestamptz not null
);
create table live_call_receipt (
    attempt_id uuid primary key references live_call_dispatch(attempt_id) on delete restrict,
    snapshot_id bigint not null references provider_snapshot(id) on delete restrict,
    occurrence_id bigint not null unique,
    requested_at timestamptz not null, received_at timestamptz not null,
    payload_sha256 varchar(64) not null check (payload_sha256 ~ '^[0-9a-f]{64}$'),
    payload_size_bytes integer not null check (payload_size_bytes between 0 and 5242880),
    persistence_outcome varchar(16) not null check (persistence_outcome in ('INSERTED','DEDUPLICATED')),
    foreign key (occurrence_id,snapshot_id) references provider_snapshot_occurrence(id,snapshot_id) on delete restrict,
    check (received_at >= requested_at)
);
create table live_call_result (
    attempt_id uuid primary key references live_call(attempt_id) on delete restrict,
    outcome varchar(96) not null check (outcome ~ '^[A-Za-z0-9._-]+$'),
    scope varchar(96) not null check (scope ~ '^[A-Za-z0-9._-]+$'), code varchar(96),
    resolved_at timestamptz not null, parser_version varchar(96), successful boolean not null,
    next_event_state varchar(64), sport_status varchar(96), projection_json jsonb, projection_version varchar(96),
    projection_sha256 varchar(64), completeness_status varchar(96), completeness_score smallint,
    canonical_observation_id bigint references canonical_event_observation(id) on delete restrict,
    detail_observation_id bigint references event_detail_observation(id) on delete restrict,
    j5_observation_id bigint references j5_event_data_observation(id) on delete restrict,
    normalized_sha256 varchar(64),
    check (completeness_score is null or completeness_score between 0 and 100),
    check ((projection_json is null and projection_version is null and projection_sha256 is null)
        or (projection_json is not null and jsonb_typeof(projection_json) = 'object'
        and octet_length(projection_json::text) <= 524288 and projection_version is not null
        and projection_sha256 is not null and projection_sha256 ~ '^[0-9a-f]{64}$'
        and projection_sha256 = encode(sha256(convert_to(projection_json::text,'UTF8')),'hex'))),
    check (normalized_sha256 is null or normalized_sha256 ~ '^[0-9a-f]{64}$')
);
create table live_transition (
    campaign_id uuid not null references live_campaign(campaign_id) on delete restrict,
    revision bigint not null check (revision > 0), canonical_event_id uuid,
    state varchar(96) not null check (state ~ '^[A-Za-z0-9._-]+$'), reason varchar(96),
    changed_at timestamptz not null, attempt_id uuid references live_call(attempt_id) on delete restrict,
    primary key(campaign_id,revision),
    foreign key(campaign_id,canonical_event_id) references live_event(campaign_id,canonical_event_id) on delete restrict
);
create index ix_live_campaign_recent on live_campaign(prepared_at desc,campaign_id);
create index ix_live_event_identity on live_event(canonical_event_id,campaign_id);
create index ix_live_call_campaign on live_call(campaign_id,ordinal);
create index ix_live_call_family on live_call(campaign_id,canonical_event_id,endpoint,ordinal desc);
create unique index uq_live_final_endpoint_once on live_call(campaign_id,canonical_event_id,endpoint) where final_cycle;

create function validate_live_target() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from canonical_event e join canonical_event_observation o on o.canonical_event_id=e.id
        where e.id=new.canonical_event_id and e.provider='SOFASCORE' and e.provider_event_id=new.provider_event_id
        and o.id=new.source_observation_id and o.source_kind='PROVIDER_SNAPSHOT' and o.source_snapshot_id=new.source_snapshot_id) then
        raise exception 'live target provenance does not match canonical identity';
    end if;
    if new.target_order >= (select target_count from live_campaign where campaign_id=new.campaign_id) then
        raise exception 'live target exceeds qualified capacity';
    end if;
    return new;
end $$;
create trigger live_target_validate before insert on live_event for each row execute function validate_live_target();

create function validate_live_receipt() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from live_call c join live_event e using(campaign_id,canonical_event_id)
        join provider_snapshot s on s.id=new.snapshot_id join provider_snapshot_occurrence o on o.id=new.occurrence_id
        where c.attempt_id=new.attempt_id and s.provider='SOFASCORE' and s.acquisition_mode='DIRECT_LOCAL_ENDPOINT'
        and s.logical_endpoint=c.endpoint and s.request_key=c.endpoint || '|eventId=' || e.provider_event_id::text
        and s.payload_sha256=new.payload_sha256 and s.payload_size_bytes=new.payload_size_bytes
        and o.snapshot_id=s.id and o.requested_at=new.requested_at and o.received_at=new.received_at
        and o.persistence_outcome=new.persistence_outcome) then
        raise exception 'live receipt evidence is not correlated';
    end if;
    return new;
end $$;
create trigger live_receipt_validate before insert on live_call_receipt for each row execute function validate_live_receipt();

create function validate_live_result() returns trigger language plpgsql as $$
declare c live_call; r live_call_receipt;
begin
    select * into strict c from live_call where attempt_id=new.attempt_id;
    select * into r from live_call_receipt where attempt_id=new.attempt_id;
    if new.successful and r.attempt_id is null then raise exception 'live success requires receipt'; end if;
    if new.canonical_observation_id is not null and not exists(select 1 from canonical_event_observation
        where id=new.canonical_observation_id and canonical_event_id=c.canonical_event_id and source_snapshot_id=r.snapshot_id) then
        raise exception 'live canonical observation is not correlated'; end if;
    if new.detail_observation_id is not null and (c.endpoint <> 'EVENT_DETAILS' or not exists(select 1 from event_detail_observation
        where id=new.detail_observation_id and canonical_event_id=c.canonical_event_id and source_snapshot_id=r.snapshot_id)) then
        raise exception 'live detail observation is not correlated'; end if;
    if new.j5_observation_id is not null and (c.endpoint='EVENT_DETAILS' or not exists(select 1 from j5_event_data_observation
        where id=new.j5_observation_id and canonical_event_id=c.canonical_event_id and endpoint_type=c.endpoint and source_snapshot_id=r.snapshot_id)) then
        raise exception 'live J5 observation is not correlated'; end if;
    if new.successful and ((c.endpoint='EVENT_DETAILS' and (new.canonical_observation_id is null or new.detail_observation_id is null))
        or (c.endpoint <> 'EVENT_DETAILS' and new.j5_observation_id is null)) then
        raise exception 'live success lacks normalized observations'; end if;
    if new.successful and (new.normalized_sha256 is null
        or (c.endpoint='EVENT_DETAILS' and not exists(select 1 from event_detail_observation where id=new.detail_observation_id and normalized_sha256=new.normalized_sha256))
        or (c.endpoint<>'EVENT_DETAILS' and not exists(select 1 from j5_event_data_observation where id=new.j5_observation_id and normalized_sha256=new.normalized_sha256))) then
        raise exception 'live normalized hash does not match its observation'; end if;
    return new;
end $$;
create trigger live_result_validate before insert on live_call_result for each row execute function validate_live_result();

create function reject_live_evidence_mutation() returns trigger language plpgsql as $$
begin raise exception '% is append-only',tg_table_name; end $$;
do $$ declare t text; begin
    foreach t in array array['live_call','live_call_dispatch','live_call_receipt','live_call_result','live_transition'] loop
        execute format('create trigger %I before update or delete on %I for each row execute function reject_live_evidence_mutation()',t || '_append_only',t);
        execute format('create trigger %I before truncate on %I for each statement execute function reject_live_evidence_mutation()',t || '_reject_truncate',t);
    end loop;
end $$;
create function protect_live_manifest() returns trigger language plpgsql as $$
begin
    if tg_op <> 'UPDATE' then raise exception 'live manifest cannot be removed'; end if;
    if (to_jsonb(new) - array['state','reason','started_at','ends_at','owner_instance_id','generation','reserved_calls','received_bytes','revision'])
        is distinct from (to_jsonb(old) - array['state','reason','started_at','ends_at','owner_instance_id','generation','reserved_calls','received_bytes','revision']) then
        raise exception 'live manifest is immutable'; end if;
    if old.started_at is not null and row(new.started_at,new.ends_at,new.owner_instance_id,new.generation)
        is distinct from row(old.started_at,old.ends_at,old.owner_instance_id,old.generation) then
        raise exception 'live execution cannot be relaunched'; end if;
    return new;
end $$;
create trigger live_manifest_immutable before update or delete on live_campaign for each row execute function protect_live_manifest();
create function protect_live_selection() returns trigger language plpgsql as $$
begin
    if tg_op <> 'UPDATE' then raise exception 'live selection cannot be removed'; end if;
    if (to_jsonb(new) - array['state','reason','reserved_calls','received_bytes','next_due_at','missed_cycles','final_complete'])
        is distinct from (to_jsonb(old) - array['state','reason','reserved_calls','received_bytes','next_due_at','missed_cycles','final_complete']) then
        raise exception 'live selection is immutable'; end if;
    return new;
end $$;
create trigger live_selection_immutable before update or delete on live_event for each row execute function protect_live_selection();
create trigger live_campaign_reject_truncate before truncate on live_campaign for each statement execute function reject_live_evidence_mutation();
create trigger live_event_reject_truncate before truncate on live_event for each statement execute function reject_live_evidence_mutation();

comment on table provider_campaign_guard is 'Local provider exclusion shared by manual and live sessions. No TTL takeover; cleanup proof precedes release.';
comment on table live_call_dispatch is 'Dispatch authorization is not an on-wire timestamp. A crash may leave an unknown external outcome.';
comment on table live_call_receipt is 'Raw snapshot and acquisition occurrence committed before parsing. Identical contents retain new receipt evidence.';
comment on table live_call_result is 'Append-only per-attempt parser result and versioned local projection, independent of historical snapshot classification.';

-- Hold the same short SQL guard during an audited purge; concurrent provider acquisition waits.
create function guard_live_payload_retention() returns trigger language plpgsql as $$
declare provider_state text;
begin
    if old.payload_raw is not null and new.payload_raw is null then
        select state into strict provider_state from provider_campaign_guard where singleton_id=1 for update;
        if provider_state <> 'FREE' or exists(select 1 from live_campaign where state in ('RUNNING','CLEANUP_REQUIRED')) then
            raise exception 'J6 payload retention requires provider campaigns to be quiescent';
        end if;
    end if;
    return new;
end $$;
create trigger provider_snapshot_live_retention_guard before update on provider_snapshot for each row execute function guard_live_payload_retention();
