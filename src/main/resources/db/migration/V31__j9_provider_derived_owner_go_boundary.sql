alter table j7_delivery_attempt
    add column payload_class varchar(32) default 'SYNTHETIC_ONLY';

update j7_delivery_attempt
set payload_class = 'SYNTHETIC_ONLY'
where payload_class is null;

alter table j7_delivery_attempt
    alter column payload_class set not null,
    alter column payload_class drop default,
    add constraint ck_j7_delivery_attempt_payload_class
        check (payload_class in ('SYNTHETIC_ONLY', 'PROVIDER_DERIVED'));

create table j7_provider_delivery_owner_go_grant (
    id bigint generated always as identity primary key,
    go_uuid uuid not null,
    owner_decision_block_sha256 varchar(64) not null,
    work_order varchar(192) not null,
    campaign_manifest_reference varchar(512) not null,
    campaign_manifest_sha256 varchar(64) not null,
    local_lab_commit varchar(40) not null,
    receiver_commit varchar(40) not null,
    official_permission_evidence_reference varchar(512) not null,
    official_permission_evidence_sha256 varchar(64) not null,
    official_permission_status varchar(32) not null,
    receiver_qualification varchar(16) not null,
    sender_qualification varchar(16) not null,
    execution_actor varchar(32) not null,
    export_manifest_id bigint not null,
    canonical_event_id uuid not null,
    provider_event_id bigint not null,
    export_uuid uuid not null,
    file_sha256 varchar(64) not null,
    data_sha256 varchar(64) not null,
    file_size_bytes bigint not null,
    schema_id text not null,
    schema_version varchar(32) not null,
    receiver_origin varchar(128) not null,
    client_certificate_sha256 varchar(64) not null,
    expected_attempt_number integer not null,
    maximum_direct_import_calls integer not null,
    valid_from timestamptz not null,
    valid_until timestamptz not null,
    owner_decision varchar(16) not null,
    go_use varchar(16) not null,
    payload_class varchar(32) not null,
    validation_status varchar(32) not null,
    provider_derived_real_post_authorized boolean not null,
    provider_network_authorized boolean not null,
    remote_receiver_network_authorized boolean not null,
    vps_deployment_authorized boolean not null,
    production_authorized boolean not null,
    automatic_retry_authorized boolean not null,
    registered_at timestamptz not null default clock_timestamp(),
    constraint fk_j7_provider_owner_go_export
        foreign key (export_manifest_id)
        references export_manifest (id)
        on delete restrict,
    constraint fk_j7_provider_owner_go_event
        foreign key (canonical_event_id)
        references canonical_event (id)
        on delete restrict,
    constraint uq_j7_provider_owner_go_uuid unique (go_uuid),
    constraint uq_j7_provider_owner_go_decision unique (owner_decision_block_sha256),
    constraint uq_j7_provider_owner_go_manifest unique (campaign_manifest_sha256),
    constraint ck_j7_provider_owner_go_uuid check (
        go_uuid <> '00000000-0000-0000-0000-000000000000'::uuid
        and go_uuid::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    constraint ck_j7_provider_owner_go_hashes check (
        owner_decision_block_sha256 ~ '^[0-9a-f]{64}$'
        and campaign_manifest_sha256 ~ '^[0-9a-f]{64}$'
        and official_permission_evidence_sha256 ~ '^[0-9a-f]{64}$'
        and file_sha256 ~ '^[0-9a-f]{64}$'
        and data_sha256 ~ '^[0-9a-f]{64}$'
        and client_certificate_sha256 ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_j7_provider_owner_go_commits check (
        local_lab_commit ~ '^[0-9a-f]{40}$'
        and receiver_commit ~ '^[0-9a-f]{40}$'
    ),
    constraint ck_j7_provider_owner_go_references check (
        work_order ~ '^WO-SS-20260904-046-[a-z0-9-]{1,160}$'
        and campaign_manifest_reference ~ '^docs/validation/[A-Za-z0-9][A-Za-z0-9._/-]+$'
        and char_length(campaign_manifest_reference) <= 512
        and campaign_manifest_reference !~ '\.\.'
        and official_permission_evidence_reference ~ '^docs/validation/[A-Za-z0-9][A-Za-z0-9._/-]+$'
        and char_length(official_permission_evidence_reference) <= 512
        and official_permission_evidence_reference !~ '\.\.'
    ),
    constraint ck_j7_provider_owner_go_exact_constants check (
        execution_actor = 'CODEX_LOCAL_UI'
        and official_permission_status = 'EVIDENCED_COMPATIBLE'
        and receiver_qualification = 'PASS'
        and sender_qualification = 'PASS'
        and schema_id =
            'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1'
        and schema_version = '1.0.0'
        and receiver_origin = 'https://127.0.0.1:8444'
        and expected_attempt_number = 1
        and maximum_direct_import_calls = 1
        and owner_decision = 'GRANT'
        and go_use = 'ONE_TIME'
        and payload_class = 'PROVIDER_DERIVED'
        and validation_status = 'HUMAN_VALIDATED'
        and provider_derived_real_post_authorized
        and not provider_network_authorized
        and not remote_receiver_network_authorized
        and not vps_deployment_authorized
        and not production_authorized
        and not automatic_retry_authorized
    ),
    constraint ck_j7_provider_owner_go_identity check (
        canonical_event_id <> '00000000-0000-0000-0000-000000000000'::uuid
        and export_uuid <> '00000000-0000-0000-0000-000000000000'::uuid
        and provider_event_id > 0
        and file_size_bytes between 1 and 5242880
    ),
    constraint ck_j7_provider_owner_go_window check (
        valid_until > valid_from
        and valid_until <= valid_from + interval '60 minutes'
        and isfinite(valid_from)
        and isfinite(valid_until)
        and isfinite(registered_at)
    )
);

create table j7_provider_delivery_owner_go_revocation (
    id bigint generated always as identity primary key,
    grant_id bigint not null,
    revocation_decision_block_sha256 varchar(64) not null,
    revoked_at timestamptz not null default clock_timestamp(),
    constraint fk_j7_provider_owner_go_revocation_grant
        foreign key (grant_id)
        references j7_provider_delivery_owner_go_grant (id)
        on delete restrict,
    constraint uq_j7_provider_owner_go_revocation_grant unique (grant_id),
    constraint uq_j7_provider_owner_go_revocation_decision
        unique (revocation_decision_block_sha256),
    constraint ck_j7_provider_owner_go_revocation_hash
        check (revocation_decision_block_sha256 ~ '^[0-9a-f]{64}$')
);

create table j7_provider_delivery_owner_go_consumption (
    id bigint generated always as identity primary key,
    grant_id bigint not null,
    attempt_id bigint not null,
    consumed_at timestamptz not null default clock_timestamp(),
    constraint fk_j7_provider_owner_go_consumption_grant
        foreign key (grant_id)
        references j7_provider_delivery_owner_go_grant (id)
        on delete restrict,
    constraint fk_j7_provider_owner_go_consumption_attempt
        foreign key (attempt_id)
        references j7_delivery_attempt (id)
        on delete restrict,
    constraint uq_j7_provider_owner_go_consumption_grant unique (grant_id),
    constraint uq_j7_provider_owner_go_consumption_attempt unique (attempt_id)
);

create function public.j7_provider_owner_go_canonical_block(
    owner_go public.j7_provider_delivery_owner_go_grant)
returns text
language sql
immutable
set search_path = pg_catalog
as $$
    select 'FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V1' || E'\n'
        || 'GO_ID=' || owner_go.go_uuid::text || E'\n'
        || 'WORK_ORDER=' || owner_go.work_order || E'\n'
        || 'CAMPAIGN_MANIFEST_REFERENCE=' || owner_go.campaign_manifest_reference || E'\n'
        || 'CAMPAIGN_MANIFEST_SHA256=' || owner_go.campaign_manifest_sha256 || E'\n'
        || 'LOCAL_LAB_COMMIT=' || owner_go.local_lab_commit || E'\n'
        || 'RECEIVER_COMMIT=' || owner_go.receiver_commit || E'\n'
        || 'OFFICIAL_PERMISSION_EVIDENCE_REFERENCE='
            || owner_go.official_permission_evidence_reference || E'\n'
        || 'OFFICIAL_PERMISSION_EVIDENCE_SHA256='
            || owner_go.official_permission_evidence_sha256 || E'\n'
        || 'OFFICIAL_PERMISSION_STATUS=' || owner_go.official_permission_status || E'\n'
        || 'RECEIVER_QUALIFICATION=' || owner_go.receiver_qualification || E'\n'
        || 'SENDER_QUALIFICATION=' || owner_go.sender_qualification || E'\n'
        || 'EXECUTION_ACTOR=' || owner_go.execution_actor || E'\n'
        || 'CANONICAL_EVENT_ID=' || owner_go.canonical_event_id::text || E'\n'
        || 'PROVIDER_EVENT_ID=' || owner_go.provider_event_id::text || E'\n'
        || 'EXPORT_ID=' || owner_go.export_uuid::text || E'\n'
        || 'FILE_SHA256=' || owner_go.file_sha256 || E'\n'
        || 'DATA_SHA256=' || owner_go.data_sha256 || E'\n'
        || 'FILE_SIZE_BYTES=' || owner_go.file_size_bytes::text || E'\n'
        || 'SCHEMA_ID=' || owner_go.schema_id || E'\n'
        || 'SCHEMA_VERSION=' || owner_go.schema_version || E'\n'
        || 'RECEIVER_ORIGIN=' || owner_go.receiver_origin || E'\n'
        || 'CLIENT_CERTIFICATE_SHA256=' || owner_go.client_certificate_sha256 || E'\n'
        || 'EXPECTED_ATTEMPT_NUMBER=' || owner_go.expected_attempt_number::text || E'\n'
        || 'MAXIMUM_DIRECT_IMPORT_CALLS='
            || owner_go.maximum_direct_import_calls::text || E'\n'
        || 'VALID_FROM=' || pg_catalog.to_char(
            owner_go.valid_from at time zone 'UTC',
            'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') || E'\n'
        || 'VALID_UNTIL=' || pg_catalog.to_char(
            owner_go.valid_until at time zone 'UTC',
            'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') || E'\n'
        || 'OWNER_DECISION=' || owner_go.owner_decision || E'\n'
        || 'GO_USE=' || owner_go.go_use || E'\n'
        || 'PAYLOAD_CLASS=' || owner_go.payload_class || E'\n'
        || 'VALIDATION_STATUS=' || owner_go.validation_status || E'\n'
        || 'PROVIDER_DERIVED_REAL_POST_AUTHORIZED='
            || case when owner_go.provider_derived_real_post_authorized then 'YES' else 'NO' end
            || E'\n'
        || 'PROVIDER_NETWORK_AUTHORIZED='
            || case when owner_go.provider_network_authorized then 'YES' else 'NO' end || E'\n'
        || 'REMOTE_RECEIVER_NETWORK_AUTHORIZED='
            || case when owner_go.remote_receiver_network_authorized then 'YES' else 'NO' end
            || E'\n'
        || 'VPS_DEPLOYMENT_AUTHORIZED='
            || case when owner_go.vps_deployment_authorized then 'YES' else 'NO' end || E'\n'
        || 'PRODUCTION_AUTHORIZED='
            || case when owner_go.production_authorized then 'YES' else 'NO' end || E'\n'
        || 'AUTOMATIC_RETRY_AUTHORIZED='
            || case when owner_go.automatic_retry_authorized then 'YES' else 'NO' end || E'\n';
$$;

create function public.guard_j7_provider_owner_go_grant_insert()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
declare
    manifest public.export_manifest%rowtype;
    event_record public.canonical_event%rowtype;
begin
    new.registered_at := pg_catalog.clock_timestamp();
    if new.owner_decision_block_sha256 is distinct from pg_catalog.encode(
            pg_catalog.sha256(pg_catalog.convert_to(
                public.j7_provider_owner_go_canonical_block(new), 'UTF8')),
            'hex') then
        raise exception 'provider-derived owner decision block hash is not exact';
    end if;
    select * into manifest
    from public.export_manifest
    where id = new.export_manifest_id
    for key share;

    select * into event_record
    from public.canonical_event
    where id = new.canonical_event_id
    for key share;

    if manifest.id is null
       or event_record.id is null
       or manifest.export_kind is distinct from 'J7_CANONICAL_EVENT'
       or manifest.validation_status is distinct from 'HUMAN_VALIDATED'
       or manifest.canonical_event_id is distinct from new.canonical_event_id
       or manifest.export_uuid is distinct from new.export_uuid
       or manifest.content_sha256 is distinct from new.file_sha256
       or manifest.data_sha256 is distinct from new.data_sha256
       or manifest.content_size_bytes is distinct from new.file_size_bytes
       or manifest.schema_id is distinct from new.schema_id
       or manifest.schema_version is distinct from new.schema_version
       or event_record.provider is distinct from 'SOFASCORE'
       or event_record.provider_event_id is distinct from new.provider_event_id
       or jsonb_array_length(manifest.source_observations) <> 5
       or not exists (
            select 1
            from pg_catalog.jsonb_array_elements(manifest.source_observations) source(value)
            where source.value ->> 'sourceKind' = 'PROVIDER_SNAPSHOT'
       )
       or exists (
            select 1
            from pg_catalog.jsonb_array_elements(manifest.source_observations) source(value)
            where coalesce(source.value ->> 'availability', '') not in (
                    'PRESENT', 'EMPTY_VALID', 'UNAVAILABLE', 'MISSING')
               or (
                    source.value ->> 'availability' = 'MISSING'
                    and source.value -> 'sourceKind' is distinct from 'null'::jsonb
               )
               or (
                    source.value ->> 'availability' <> 'MISSING'
                    and (
                        source.value ->> 'sourceKind' is distinct from 'PROVIDER_SNAPSHOT'
                    )
               )
       ) then
        raise exception 'provider-derived owner go does not match exact J7 provenance';
    end if;
    return new;
end;
$$;

create trigger j7_provider_owner_go_grant_insert_guard
before insert on public.j7_provider_delivery_owner_go_grant
for each row execute function public.guard_j7_provider_owner_go_grant_insert();

create function public.guard_j7_provider_owner_go_revocation_insert()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    new.revoked_at := pg_catalog.clock_timestamp();
    perform 1
    from public.j7_provider_delivery_owner_go_grant
    where id = new.grant_id
    for update;
    if not found
       or exists (
            select 1 from public.j7_provider_delivery_owner_go_consumption
            where grant_id = new.grant_id
       ) then
        raise exception 'provider-derived owner go cannot be revoked';
    end if;
    return new;
end;
$$;

create trigger j7_provider_owner_go_revocation_insert_guard
before insert on public.j7_provider_delivery_owner_go_revocation
for each row execute function public.guard_j7_provider_owner_go_revocation_insert();

create function public.guard_j7_provider_owner_go_consumption_insert()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
declare
    grant_record public.j7_provider_delivery_owner_go_grant%rowtype;
    attempt_record public.j7_delivery_attempt%rowtype;
    delivery_record public.j7_delivery%rowtype;
begin
    new.consumed_at := pg_catalog.clock_timestamp();
    select * into grant_record
    from public.j7_provider_delivery_owner_go_grant
    where id = new.grant_id
    for update;

    select * into attempt_record
    from public.j7_delivery_attempt
    where id = new.attempt_id;

    select * into delivery_record
    from public.j7_delivery
    where id = attempt_record.delivery_id
    for update;

    perform 1 from public.export_manifest
    where id = grant_record.export_manifest_id
      and export_kind = 'J7_CANONICAL_EVENT'
      and validation_status = 'HUMAN_VALIDATED'
      and canonical_event_id = grant_record.canonical_event_id
      and export_uuid = grant_record.export_uuid
      and content_sha256 = grant_record.file_sha256
      and data_sha256 = grant_record.data_sha256
      and content_size_bytes = grant_record.file_size_bytes
      and schema_id = grant_record.schema_id
      and schema_version = grant_record.schema_version
      and exists (
            select 1
            from public.canonical_event event
            where event.id = grant_record.canonical_event_id
              and event.provider = 'SOFASCORE'
              and event.provider_event_id = grant_record.provider_event_id
      )
      and jsonb_array_length(source_observations) = 5
      and exists (
            select 1 from pg_catalog.jsonb_array_elements(source_observations) source(value)
            where source.value ->> 'sourceKind' = 'PROVIDER_SNAPSHOT'
      )
      and not exists (
            select 1 from pg_catalog.jsonb_array_elements(source_observations) source(value)
            where coalesce(source.value ->> 'availability', '') not in (
                    'PRESENT', 'EMPTY_VALID', 'UNAVAILABLE', 'MISSING')
               or (
                    source.value ->> 'availability' = 'MISSING'
                    and source.value -> 'sourceKind' is distinct from 'null'::jsonb
               )
               or (
                    source.value ->> 'availability' <> 'MISSING'
                    and source.value ->> 'sourceKind' is distinct from 'PROVIDER_SNAPSHOT'
               )
      )
    for key share;

    if grant_record.id is null
       or attempt_record.id is null
       or delivery_record.id is null
       or not found
       or exists (
            select 1 from public.j7_provider_delivery_owner_go_revocation
            where grant_id = new.grant_id
       )
       or attempt_record.payload_class is distinct from 'PROVIDER_DERIVED'
       or attempt_record.attempt_number is distinct from grant_record.expected_attempt_number
       or delivery_record.export_manifest_id is distinct from grant_record.export_manifest_id
       or delivery_record.export_uuid is distinct from grant_record.export_uuid
       or delivery_record.file_sha256 is distinct from grant_record.file_sha256
       or delivery_record.data_sha256 is distinct from grant_record.data_sha256
       or delivery_record.file_size_bytes is distinct from grant_record.file_size_bytes
       or new.consumed_at < grant_record.valid_from
       or new.consumed_at >= grant_record.valid_until then
        raise exception 'provider-derived owner go consumption is not exact and available';
    end if;
    return new;
end;
$$;

create trigger j7_provider_owner_go_consumption_insert_guard
before insert on public.j7_provider_delivery_owner_go_consumption
for each row execute function public.guard_j7_provider_owner_go_consumption_insert();

create function public.guard_j7_provider_delivery_claim_update()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
declare
    attempt_record public.j7_delivery_attempt%rowtype;
begin
    if old.current_state in ('NOT_ATTEMPTED', 'UNKNOWN_RECONCILIATION_REQUIRED')
       and new.current_state = 'IN_FLIGHT' then
        select * into attempt_record
        from public.j7_delivery_attempt
        where delivery_id = old.id
        order by attempt_number desc
        limit 1;
        if attempt_record.payload_class = 'PROVIDER_DERIVED'
           and (select count(*)
                from public.j7_provider_delivery_owner_go_consumption
                where attempt_id = attempt_record.id) <> 1 then
            raise exception 'provider-derived delivery claim requires one owner-go consumption';
        end if;
        if attempt_record.payload_class = 'SYNTHETIC_ONLY'
           and exists (
                select 1 from public.j7_provider_delivery_owner_go_consumption
                where attempt_id = attempt_record.id
           ) then
            raise exception 'synthetic delivery cannot consume a provider owner go';
        end if;
    end if;
    return new;
end;
$$;

create trigger j7_provider_delivery_claim_update_guard
before update on public.j7_delivery
for each row execute function public.guard_j7_provider_delivery_claim_update();

create function public.enforce_j7_provider_attempt_consumption()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
declare
    attempt_identifier bigint;
    attempt_class varchar(32);
    consumption_count bigint;
    attempt_delivery_id bigint;
    attempt_number_value integer;
    attempt_started_at timestamptz;
    delivery_state varchar(48);
    delivery_state_changed_at timestamptz;
    latest_attempt_id bigint;
    consumed_at_value timestamptz;
    expected_attempt_number_value integer;
begin
    if tg_table_name = 'j7_delivery_attempt' then
        attempt_identifier := new.id;
    else
        attempt_identifier := new.attempt_id;
    end if;
    select payload_class, delivery_id, attempt_number, started_at
    into attempt_class, attempt_delivery_id, attempt_number_value, attempt_started_at
    from public.j7_delivery_attempt where id = attempt_identifier;
    select count(*) into consumption_count
    from public.j7_provider_delivery_owner_go_consumption
    where attempt_id = attempt_identifier;
    if attempt_class = 'PROVIDER_DERIVED' and consumption_count = 1 then
        select delivery.current_state, delivery.state_changed_at,
               latest.id, consumption.consumed_at, owner_go.expected_attempt_number
        into delivery_state, delivery_state_changed_at, latest_attempt_id,
             consumed_at_value, expected_attempt_number_value
        from public.j7_delivery delivery
        join lateral (
            select candidate.id
            from public.j7_delivery_attempt candidate
            where candidate.delivery_id = delivery.id
            order by candidate.attempt_number desc
            limit 1
        ) latest on true
        join public.j7_provider_delivery_owner_go_consumption consumption
          on consumption.attempt_id = attempt_identifier
        join public.j7_provider_delivery_owner_go_grant owner_go
          on owner_go.id = consumption.grant_id
        where delivery.id = attempt_delivery_id;
    end if;
    if (attempt_class = 'PROVIDER_DERIVED' and (
            consumption_count <> 1
            or delivery_state is distinct from 'IN_FLIGHT'
            or latest_attempt_id is distinct from attempt_identifier
            or attempt_number_value is distinct from expected_attempt_number_value
            or delivery_state_changed_at is distinct from attempt_started_at
            or consumed_at_value < attempt_started_at
       ))
       or (attempt_class = 'SYNTHETIC_ONLY' and consumption_count <> 0) then
        raise exception 'delivery attempt owner-go correlation is incomplete';
    end if;
    return new;
end;
$$;

create constraint trigger j7_provider_attempt_consumption_complete
after insert on public.j7_delivery_attempt
deferrable initially deferred
for each row execute function public.enforce_j7_provider_attempt_consumption();

create constraint trigger j7_provider_consumption_attempt_complete
after insert on public.j7_provider_delivery_owner_go_consumption
deferrable initially deferred
for each row execute function public.enforce_j7_provider_attempt_consumption();

create function public.reject_j7_provider_owner_go_mutation()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    raise exception 'provider-derived owner-go evidence is append-only';
end;
$$;

create trigger j7_provider_owner_go_grant_append_only
before update or delete on public.j7_provider_delivery_owner_go_grant
for each row execute function public.reject_j7_provider_owner_go_mutation();

create trigger j7_provider_owner_go_revocation_append_only
before update or delete on public.j7_provider_delivery_owner_go_revocation
for each row execute function public.reject_j7_provider_owner_go_mutation();

create trigger j7_provider_owner_go_consumption_append_only
before update or delete on public.j7_provider_delivery_owner_go_consumption
for each row execute function public.reject_j7_provider_owner_go_mutation();

comment on column j7_delivery_attempt.payload_class is
    'Persisted classification at claim time; existing pre-V31 attempts are synthetic because provider delivery was structurally blocked.';
comment on table j7_provider_delivery_owner_go_grant is
    'Append-only exact owner grant metadata for one provider-derived J7 POST; no payload or secret.';
comment on table j7_provider_delivery_owner_go_revocation is
    'Append-only owner revocation, mutually exclusive with consumption.';
comment on table j7_provider_delivery_owner_go_consumption is
    'Atomic one-time owner-go consumption correlated with exactly one provider-derived delivery attempt.';
