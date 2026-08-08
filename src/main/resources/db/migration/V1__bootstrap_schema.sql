create table provider_snapshot (
    id bigint generated always as identity primary key,
    provider varchar(32) not null,
    logical_endpoint varchar(64) not null,
    request_key varchar(512) not null,
    requested_at timestamptz not null,
    received_at timestamptz,
    http_status integer,
    content_type varchar(160),
    latency_ms bigint,
    payload_jsonb jsonb,
    payload_sha256 varchar(64),
    parser_version varchar(32),
    schema_status varchar(32) not null default 'RAW_ONLY',
    error_code varchar(96),
    created_at timestamptz not null default current_timestamp,
    constraint ck_provider_snapshot_http_status
        check (http_status is null or http_status between 100 and 599),
    constraint ck_provider_snapshot_latency
        check (latency_ms is null or latency_ms >= 0),
    constraint ck_provider_snapshot_sha256
        check (payload_sha256 is null or payload_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ck_provider_snapshot_schema_status
        check (schema_status in (
            'RAW_ONLY',
            'PARSED',
            'COHERENCE_CHECKED',
            'HUMAN_VALIDATED',
            'REJECTED',
            'SCHEMA_INCOMPATIBLE',
            'TRANSPORT_ERROR'
        ))
);

create unique index uq_provider_snapshot_request_hash
    on provider_snapshot (provider, logical_endpoint, request_key, payload_sha256)
    where payload_sha256 is not null;

create index ix_provider_snapshot_created_at
    on provider_snapshot (created_at desc);

create index ix_provider_snapshot_endpoint_received_at
    on provider_snapshot (logical_endpoint, received_at desc);

create table export_manifest (
    id bigint generated always as identity primary key,
    schema_version varchar(32) not null,
    export_path text not null,
    content_sha256 varchar(64) not null,
    created_at timestamptz not null default current_timestamp,
    validation_status varchar(32) not null,
    source_snapshot_ids bigint[] not null default '{}'::bigint[],
    warnings jsonb not null default '[]'::jsonb,
    constraint uq_export_manifest_content_sha256 unique (content_sha256),
    constraint ck_export_manifest_sha256
        check (content_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ck_export_manifest_validation_status
        check (validation_status in (
            'RAW_ONLY',
            'PARSED',
            'COHERENCE_CHECKED',
            'HUMAN_VALIDATED',
            'REJECTED'
        ))
);

create table connector_control (
    singleton_id smallint primary key,
    network_enabled boolean not null default false,
    circuit_state varchar(24) not null default 'LOCKED',
    last_reason varchar(256) not null,
    changed_at timestamptz not null default current_timestamp,
    constraint ck_connector_control_singleton check (singleton_id = 1),
    constraint ck_connector_control_state
        check (circuit_state in ('LOCKED', 'CLOSED', 'OPEN', 'HALF_OPEN'))
);

insert into connector_control (
    singleton_id,
    network_enabled,
    circuit_state,
    last_reason
) values (
    1,
    false,
    'LOCKED',
    'J1 bootstrap: network calls are not implemented'
);

comment on table provider_snapshot is
    'Raw SofaScore Local Lab transport snapshots. DIRECT_LOCAL_ENDPOINT provenance only.';
comment on table connector_control is
    'Persistent operator state; J1 remains code-locked even if this row is modified.';
