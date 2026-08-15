alter table event_detail_observation
    add column source_kind varchar(32),
    add column source_reference varchar(128),
    add column source_snapshot_id bigint;

update event_detail_observation
set source_kind = 'SYNTHETIC_FIXTURE',
    source_reference = source_fixture_id;

alter table event_detail_observation
    alter column source_kind set not null,
    alter column source_reference set not null,
    alter column source_fixture_id drop not null;

alter table event_detail_observation
    drop constraint uq_event_detail_observation_source_version,
    drop constraint ck_event_detail_observation_fixture,
    drop constraint ck_event_detail_observation_parser;

alter table event_detail_observation
    add constraint fk_event_detail_observation_snapshot
        foreign key (source_snapshot_id)
        references provider_snapshot (id)
        on delete restrict,
    add constraint uq_event_detail_observation_source_version
        unique (
            canonical_event_id,
            source_kind,
            source_reference,
            normalized_sha256
        ),
    add constraint ck_event_detail_observation_source
        check (
            (
                source_kind = 'PROVIDER_SNAPSHOT'
                and source_snapshot_id is not null
                and source_fixture_id is null
                and source_reference = 'snapshot:' || source_snapshot_id::text
            )
            or (
                source_kind = 'SYNTHETIC_FIXTURE'
                and source_snapshot_id is null
                and source_fixture_id is not null
                and source_fixture_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
                and source_reference = source_fixture_id
            )
        ),
    add constraint ck_event_detail_observation_parser
        check (parser_version in ('event-details-v1', 'event-details-v2'));

alter table provider_response_cache
    drop constraint ck_provider_response_cache_scope;

alter table provider_response_cache
    add constraint ck_provider_response_cache_scope
        check (
            provider = 'SOFASCORE'
            and logical_endpoint in ('SCHEDULED_EVENTS', 'EVENT_DETAILS')
        );

comment on table event_detail_observation is
    'J4 append-only EVENT_DETAILS observations from synthetic fixtures or persisted provider snapshots.';
comment on column event_detail_observation.source_snapshot_id is
    'Provider snapshot persisted before parsing; null for synthetic fixtures.';
comment on table provider_response_cache is
    'J3/J4 parsed-response cache checkpoints. Raw bytes remain exclusively in provider_snapshot.';
