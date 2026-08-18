alter table provider_snapshot
    add column payload_purged_at timestamptz;

alter table provider_snapshot
    drop constraint ck_provider_snapshot_raw_payload;

alter table provider_snapshot
    add constraint ck_provider_snapshot_raw_payload
        check (
            (
                payload_raw is not null
                and payload_size_bytes = octet_length(payload_raw)
                and payload_size_bytes between 0 and 5242880
                and payload_sha256 is not null
                and payload_purged_at is null
            )
            or (
                payload_raw is null
                and payload_size_bytes between 0 and 5242880
                and payload_sha256 is not null
                and payload_purged_at is not null
                and (received_at is null or payload_purged_at >= received_at)
            )
            or (
                payload_raw is null
                and payload_size_bytes is null
                and payload_purged_at is null
            )
        );

create index ix_provider_snapshot_j6_retention_candidates
    on provider_snapshot (received_at, id)
    where payload_raw is not null;

create table j6_raw_payload_purge_audit (
    id bigint generated always as identity primary key,
    batch_id uuid not null,
    snapshot_id bigint not null,
    snapshot_received_at_before timestamptz not null,
    payload_size_bytes_before bigint not null,
    payload_sha256_before varchar(64) not null,
    retention_days integer not null,
    cutoff_at timestamptz not null,
    plan_sha256 varchar(64) not null,
    backup_manifest_sha256 varchar(64) not null,
    backup_cipher_sha256 varchar(64) not null,
    backup_qualified_at timestamptz not null,
    backup_coverage_max_snapshot_id bigint not null,
    backup_coverage_received_at timestamptz not null,
    executed_at timestamptz not null default current_timestamp,
    constraint fk_j6_raw_payload_purge_snapshot
        foreign key (snapshot_id)
        references provider_snapshot (id)
        on delete restrict,
    constraint uq_j6_raw_payload_purge_snapshot unique (snapshot_id),
    constraint uq_j6_raw_payload_purge_batch_snapshot unique (batch_id, snapshot_id),
    constraint ck_j6_raw_payload_purge_size
        check (payload_size_bytes_before between 0 and 5242880),
    constraint ck_j6_raw_payload_purge_payload_sha
        check (payload_sha256_before ~ '^[0-9a-f]{64}$'),
    constraint ck_j6_raw_payload_purge_plan_sha
        check (plan_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ck_j6_raw_payload_purge_backup_manifest_sha
        check (backup_manifest_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ck_j6_raw_payload_purge_backup_cipher_sha
        check (backup_cipher_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ck_j6_raw_payload_purge_retention
        check (retention_days between 1 and 3650),
    constraint ck_j6_raw_payload_purge_coverage
        check (
            backup_coverage_max_snapshot_id >= snapshot_id
            and backup_coverage_received_at >= snapshot_received_at_before
            and backup_qualified_at <= executed_at
            and cutoff_at < executed_at
        )
);

create index ix_j6_raw_payload_purge_audit_batch
    on j6_raw_payload_purge_audit (batch_id, snapshot_id);

create function reject_j6_raw_payload_purge_audit_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'j6_raw_payload_purge_audit is append-only';
end;
$$;

create trigger j6_raw_payload_purge_audit_append_only
before update or delete on j6_raw_payload_purge_audit
for each row execute function reject_j6_raw_payload_purge_audit_mutation();

create function guard_provider_snapshot_j6_mutation()
returns trigger
language plpgsql
as $$
declare
    configured_batch text;
begin
    if tg_op = 'DELETE' then
        raise exception 'provider_snapshot deletion is forbidden';
    end if;

    if new is not distinct from old then
        return new;
    end if;

    if old.schema_status = 'RAW_ONLY'
       and new.schema_status in (
            'PARSED',
            'COHERENCE_CHECKED',
            'HUMAN_VALIDATED',
            'REJECTED',
            'SCHEMA_INCOMPATIBLE',
            'UNEXPECTED_CONTENT',
            'TRANSPORT_ERROR',
            'ENDPOINT_UNAVAILABLE'
       )
       and new.id is not distinct from old.id
       and new.provider is not distinct from old.provider
       and new.logical_endpoint is not distinct from old.logical_endpoint
       and new.request_key is not distinct from old.request_key
       and new.requested_at is not distinct from old.requested_at
       and new.received_at is not distinct from old.received_at
       and new.http_status is not distinct from old.http_status
       and new.content_type is not distinct from old.content_type
       and new.latency_ms is not distinct from old.latency_ms
       and new.payload_jsonb is not distinct from old.payload_jsonb
       and new.payload_sha256 is not distinct from old.payload_sha256
       and new.parser_version is not distinct from old.parser_version
       and new.created_at is not distinct from old.created_at
       and new.acquisition_mode is not distinct from old.acquisition_mode
       and new.payload_raw is not distinct from old.payload_raw
       and new.payload_size_bytes is not distinct from old.payload_size_bytes
       and new.payload_purged_at is not distinct from old.payload_purged_at then
        return new;
    end if;

    configured_batch := current_setting('sofascore.j6_purge_batch', true);
    if old.payload_raw is not null
       and old.payload_purged_at is null
       and new.payload_raw is null
       and new.payload_purged_at is not null
       and new.id is not distinct from old.id
       and new.provider is not distinct from old.provider
       and new.logical_endpoint is not distinct from old.logical_endpoint
       and new.request_key is not distinct from old.request_key
       and new.requested_at is not distinct from old.requested_at
       and new.received_at is not distinct from old.received_at
       and new.http_status is not distinct from old.http_status
       and new.content_type is not distinct from old.content_type
       and new.latency_ms is not distinct from old.latency_ms
       and new.payload_jsonb is not distinct from old.payload_jsonb
       and new.payload_sha256 is not distinct from old.payload_sha256
       and new.parser_version is not distinct from old.parser_version
       and new.schema_status is not distinct from old.schema_status
       and new.error_code is not distinct from old.error_code
       and new.created_at is not distinct from old.created_at
       and new.acquisition_mode is not distinct from old.acquisition_mode
       and new.payload_size_bytes is not distinct from old.payload_size_bytes
       and configured_batch is not null
       and configured_batch <> ''
       and exists (
            select 1
            from j6_raw_payload_purge_audit audit
            where audit.batch_id::text = configured_batch
              and audit.snapshot_id = old.id
              and audit.payload_size_bytes_before = old.payload_size_bytes
              and audit.payload_sha256_before = old.payload_sha256
              and audit.executed_at = new.payload_purged_at
       ) then
        return new;
    end if;

    raise exception 'provider_snapshot is immutable outside one-time classification or audited J6 payload purge';
end;
$$;

create trigger provider_snapshot_j6_guard
before update or delete on provider_snapshot
for each row execute function guard_provider_snapshot_j6_mutation();

comment on column provider_snapshot.payload_purged_at is
    'J6 audited purge timestamp. Metadata, hash, occurrences and normalized observations remain append-only.';
comment on table j6_raw_payload_purge_audit is
    'Append-only J6 evidence for a backup-qualified raw payload purge; no payload bytes or secrets are stored.';
