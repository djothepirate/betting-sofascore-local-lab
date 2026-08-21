alter table provider_snapshot
    drop constraint ck_provider_snapshot_acquisition_mode;

alter table provider_snapshot
    add constraint ck_provider_snapshot_acquisition_mode
        check (acquisition_mode in (
            'DIRECT_LOCAL_ENDPOINT',
            'MANUAL_LOCAL_JSON_IMPORT'
        ));

drop index uq_provider_snapshot_request_hash;

create unique index uq_provider_snapshot_request_hash
    on provider_snapshot (
        provider,
        acquisition_mode,
        logical_endpoint,
        request_key,
        payload_sha256
    )
    where payload_sha256 is not null;

comment on column provider_snapshot.acquisition_mode is
    'Provenance locale: réponse reçue directement ou JSON fourni par import manuel explicite.';
