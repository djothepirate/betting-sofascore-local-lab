create unique index uq_provider_snapshot_cache_reference
    on provider_snapshot (provider, logical_endpoint, request_key, id);

create table provider_response_cache (
    provider varchar(32) not null,
    logical_endpoint varchar(64) not null,
    request_key varchar(512) not null,
    snapshot_id bigint not null,
    cached_at timestamptz not null,
    parser_version varchar(32) not null,
    primary key (provider, logical_endpoint, request_key),
    constraint fk_provider_response_cache_snapshot
        foreign key (provider, logical_endpoint, request_key, snapshot_id)
        references provider_snapshot (provider, logical_endpoint, request_key, id)
        on delete restrict,
    constraint ck_provider_response_cache_scope
        check (
            provider = 'SOFASCORE'
            and logical_endpoint = 'SCHEDULED_EVENTS'
        ),
    constraint ck_provider_response_cache_parser_version
        check (parser_version ~ '^[A-Za-z0-9._-]+$')
);

create index ix_provider_response_cache_cached_at
    on provider_response_cache (cached_at desc);

comment on table provider_response_cache is
    'J3 cache checkpoint only. Raw payload bytes remain exclusively in provider_snapshot.';
comment on column provider_response_cache.cached_at is
    'Time of the latest successfully parsed provider observation for the exact request key.';
