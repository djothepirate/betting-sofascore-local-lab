create table canonical_event (
    id uuid primary key,
    provider varchar(32) not null,
    provider_event_id bigint not null,
    created_at timestamptz not null default current_timestamp,
    constraint uq_canonical_event_provider_identity
        unique (provider, provider_event_id),
    constraint ck_canonical_event_provider
        check (provider = 'SOFASCORE'),
    constraint ck_canonical_event_provider_id
        check (provider_event_id > 0)
);

create table canonical_event_observation (
    id bigint generated always as identity primary key,
    canonical_event_id uuid not null,
    source_kind varchar(32) not null,
    source_reference varchar(128) not null,
    source_snapshot_id bigint,
    source_fixture_id varchar(100),
    source_payload_sha256 varchar(64) not null,
    parser_version varchar(32) not null,
    source_received_at timestamptz not null,
    starts_at timestamptz not null,
    home_team_provider_id bigint not null,
    home_team_name varchar(200) not null,
    away_team_provider_id bigint not null,
    away_team_name varchar(200) not null,
    status_type varchar(64) not null,
    status_description varchar(200),
    tournament_provider_id bigint,
    tournament_name varchar(200),
    normalized_sha256 varchar(64) not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_canonical_event_observation_event
        foreign key (canonical_event_id)
        references canonical_event (id)
        on delete restrict,
    constraint fk_canonical_event_observation_snapshot
        foreign key (source_snapshot_id)
        references provider_snapshot (id)
        on delete restrict,
    constraint uq_canonical_event_observation_source_version
        unique (
            canonical_event_id,
            source_kind,
            source_reference,
            normalized_sha256
        ),
    constraint ck_canonical_event_observation_source
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
                and source_reference = source_fixture_id
            )
        ),
    constraint ck_canonical_event_observation_sha256
        check (
            source_payload_sha256 ~ '^[0-9a-f]{64}$'
            and normalized_sha256 ~ '^[0-9a-f]{64}$'
        ),
    constraint ck_canonical_event_observation_parser
        check (parser_version ~ '^[A-Za-z0-9._-]+$'),
    constraint ck_canonical_event_observation_team_ids
        check (home_team_provider_id > 0 and away_team_provider_id > 0),
    constraint ck_canonical_event_observation_tournament
        check (
            (tournament_provider_id is null and tournament_name is null)
            or (tournament_provider_id > 0 and tournament_name is not null)
        )
);

create index ix_canonical_event_observation_starts_at
    on canonical_event_observation (starts_at, canonical_event_id);

create index ix_canonical_event_observation_latest
    on canonical_event_observation (canonical_event_id, source_received_at desc, id desc);

create function reject_canonical_event_observation_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'canonical_event_observation is append-only';
end;
$$;

create trigger canonical_event_observation_append_only
before update or delete on canonical_event_observation
for each row execute function reject_canonical_event_observation_mutation();

comment on table canonical_event is
    'J4 stable local identity. Names, schedule and status never participate in identity.';
comment on table canonical_event_observation is
    'J4 append-only normalized event observations with mandatory source provenance.';
comment on column canonical_event_observation.source_payload_sha256 is
    'SHA-256 of the exact snapshot or fixture bytes from which the observation was parsed.';
comment on column canonical_event_observation.normalized_sha256 is
    'SHA-256 of the versioned local event fields used for exact-version deduplication.';
