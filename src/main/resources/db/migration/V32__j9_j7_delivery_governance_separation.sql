alter table j7_provider_delivery_owner_go_grant
    add column owner_go_format varchar(64) not null
        default 'J7_PROVIDER_DERIVED_OWNER_GO_V1',
    add column provider_permission_audit_reference varchar(512),
    add column provider_permission_audit_sha256 varchar(64),
    add column provider_permission_audit_status varchar(32),
    add column j7_transfer_governance_basis_reference varchar(512),
    add column j7_transfer_governance_basis_commit varchar(40),
    add column j7_transfer_governance_basis_sha256 varchar(64),
    add column j7_transfer_governance_basis_status varchar(64);

alter table j7_provider_delivery_owner_go_grant
    alter column owner_go_format drop default,
    alter column official_permission_evidence_reference drop not null,
    alter column official_permission_evidence_sha256 drop not null,
    alter column official_permission_status drop not null,
    drop constraint ck_j7_provider_owner_go_exact_constants,
    add constraint ck_j7_provider_owner_go_shared_exact_constants check ((
        execution_actor = 'CODEX_LOCAL_UI'
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
    ) is true),
    add constraint ck_j7_provider_owner_go_format_shape check (
        ((
            owner_go_format = 'J7_PROVIDER_DERIVED_OWNER_GO_V1'
            and official_permission_evidence_reference is not null
            and official_permission_evidence_sha256 is not null
            and official_permission_status = 'EVIDENCED_COMPATIBLE'
            and provider_permission_audit_reference is null
            and provider_permission_audit_sha256 is null
            and provider_permission_audit_status is null
            and j7_transfer_governance_basis_reference is null
            and j7_transfer_governance_basis_commit is null
            and j7_transfer_governance_basis_sha256 is null
            and j7_transfer_governance_basis_status is null
        )
        or
        (
            owner_go_format = 'J7_PROVIDER_DERIVED_OWNER_GO_V2'
            and official_permission_evidence_reference is null
            and official_permission_evidence_sha256 is null
            and official_permission_status is null
            and provider_permission_audit_reference is not null
            and provider_permission_audit_reference
                ~ '^docs/validation/[A-Za-z0-9][A-Za-z0-9._/-]*$'
            and char_length(provider_permission_audit_reference) <= 512
            and provider_permission_audit_reference !~ '\.\.'
            and provider_permission_audit_sha256 is not null
            and provider_permission_audit_sha256 ~ '^[0-9a-f]{64}$'
            and provider_permission_audit_status is not null
            and provider_permission_audit_status in (
                'NOT_EVIDENCED',
                'EVIDENCED_COMPATIBLE'
            )
            and j7_transfer_governance_basis_reference is not null
            and j7_transfer_governance_basis_reference =
                'ADR-SS-003-optional-integration-topology.md'
            and j7_transfer_governance_basis_commit is not null
            and j7_transfer_governance_basis_commit =
                'e1ec9936467dd570f7ed00c51227c8e7d5a35945'
            and j7_transfer_governance_basis_sha256 is not null
            and j7_transfer_governance_basis_sha256 =
                'ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f'
            and j7_transfer_governance_basis_status is not null
            and j7_transfer_governance_basis_status =
                'ADR_ACCEPTED_NO_EXECUTION_AUTHORITY'
        )
    ) is true);

create function public.j7_provider_owner_go_canonical_block_v2(
    owner_go public.j7_provider_delivery_owner_go_grant)
returns text
language sql
immutable
set search_path = pg_catalog
as $$
    select 'FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2' || E'\n'
        || 'GO_ID=' || owner_go.go_uuid::text || E'\n'
        || 'WORK_ORDER=' || owner_go.work_order || E'\n'
        || 'CAMPAIGN_MANIFEST_REFERENCE=' || owner_go.campaign_manifest_reference || E'\n'
        || 'CAMPAIGN_MANIFEST_SHA256=' || owner_go.campaign_manifest_sha256 || E'\n'
        || 'LOCAL_LAB_COMMIT=' || owner_go.local_lab_commit || E'\n'
        || 'RECEIVER_COMMIT=' || owner_go.receiver_commit || E'\n'
        || 'PROVIDER_PERMISSION_AUDIT_REFERENCE='
            || owner_go.provider_permission_audit_reference || E'\n'
        || 'PROVIDER_PERMISSION_AUDIT_SHA256='
            || owner_go.provider_permission_audit_sha256 || E'\n'
        || 'PROVIDER_PERMISSION_AUDIT_STATUS='
            || owner_go.provider_permission_audit_status || E'\n'
        || 'J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE='
            || owner_go.j7_transfer_governance_basis_reference || E'\n'
        || 'J7_TRANSFER_GOVERNANCE_BASIS_COMMIT='
            || owner_go.j7_transfer_governance_basis_commit || E'\n'
        || 'J7_TRANSFER_GOVERNANCE_BASIS_SHA256='
            || owner_go.j7_transfer_governance_basis_sha256 || E'\n'
        || 'J7_TRANSFER_GOVERNANCE_BASIS_STATUS='
            || owner_go.j7_transfer_governance_basis_status || E'\n'
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

create function public.j7_provider_owner_go_canonical_block_for_format(
    owner_go public.j7_provider_delivery_owner_go_grant)
returns text
language plpgsql
immutable
set search_path = pg_catalog, public
as $$
begin
    case owner_go.owner_go_format
        when 'J7_PROVIDER_DERIVED_OWNER_GO_V1' then
            return public.j7_provider_owner_go_canonical_block(owner_go);
        when 'J7_PROVIDER_DERIVED_OWNER_GO_V2' then
            return public.j7_provider_owner_go_canonical_block_v2(owner_go);
        else
            raise exception 'provider-derived owner-go format is unsupported';
    end case;
end;
$$;

create or replace function public.guard_j7_provider_owner_go_grant_insert()
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
                public.j7_provider_owner_go_canonical_block_for_format(new), 'UTF8')),
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

comment on column j7_provider_delivery_owner_go_grant.owner_go_format is
    'Strict canonical format discriminator; historical V31 rows remain V1.';
comment on column j7_provider_delivery_owner_go_grant.provider_permission_audit_status is
    'Provider permission audit fact for V2; NOT_EVIDENCED is non-blocking only for local J7 transfer.';
comment on column j7_provider_delivery_owner_go_grant.j7_transfer_governance_basis_status is
    'Exact accepted ADR basis for V2; the ADR carries no execution authority.';
comment on function public.j7_provider_owner_go_canonical_block_v2(
    public.j7_provider_delivery_owner_go_grant) is
    'Byte-exact V2 owner-go block; the immutable V1 canonical function remains authoritative for V1.';
comment on function public.j7_provider_owner_go_canonical_block_for_format(
    public.j7_provider_delivery_owner_go_grant) is
    'Strict V1/V2 canonical dispatcher; unknown formats fail closed.';
