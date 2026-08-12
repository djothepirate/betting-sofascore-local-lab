alter table provider_snapshot
    add column acquisition_mode varchar(32) not null default 'DIRECT_LOCAL_ENDPOINT',
    add column payload_raw bytea,
    add column payload_size_bytes bigint;

alter table provider_snapshot
    add constraint ck_provider_snapshot_acquisition_mode
        check (acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'),
    add constraint ck_provider_snapshot_raw_payload
        check (
            (payload_raw is null and payload_size_bytes is null)
            or (
                payload_raw is not null
                and payload_size_bytes = octet_length(payload_raw)
                and payload_size_bytes between 0 and 5242880
                and payload_sha256 is not null
            )
        );

alter table provider_snapshot
    drop constraint ck_provider_snapshot_schema_status;

alter table provider_snapshot
    add constraint ck_provider_snapshot_schema_status
        check (schema_status in (
            'RAW_ONLY',
            'PARSED',
            'COHERENCE_CHECKED',
            'HUMAN_VALIDATED',
            'REJECTED',
            'SCHEMA_INCOMPATIBLE',
            'UNEXPECTED_CONTENT',
            'TRANSPORT_ERROR'
        ));

comment on column provider_snapshot.acquisition_mode is
    'J3 provenance marker. Only DIRECT_LOCAL_ENDPOINT is accepted in this laboratory.';
comment on column provider_snapshot.payload_raw is
    'Exact response bytes kept locally before parsing; never export automatically.';
comment on column provider_snapshot.payload_size_bytes is
    'Exact octet length of payload_raw, bounded to five MiB.';
