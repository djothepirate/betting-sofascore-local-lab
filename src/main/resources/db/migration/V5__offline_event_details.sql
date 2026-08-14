create table event_detail_observation (
    id bigint generated always as identity primary key,
    canonical_event_id uuid not null,
    source_fixture_id varchar(100) not null,
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
    venue_provider_id bigint,
    venue_name varchar(200),
    venue_city varchar(200),
    season_provider_id bigint,
    season_name varchar(100),
    event_round varchar(64),
    normalized_sha256 varchar(64) not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_event_detail_observation_event
        foreign key (canonical_event_id)
        references canonical_event (id)
        on delete restrict,
    constraint uq_event_detail_observation_source_version
        unique (canonical_event_id, source_fixture_id, normalized_sha256),
    constraint ck_event_detail_observation_fixture
        check (source_fixture_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint ck_event_detail_observation_sha256
        check (
            source_payload_sha256 ~ '^[0-9a-f]{64}$'
            and normalized_sha256 ~ '^[0-9a-f]{64}$'
        ),
    constraint ck_event_detail_observation_parser
        check (parser_version = 'event-details-v1'),
    constraint ck_event_detail_observation_team_ids
        check (home_team_provider_id > 0 and away_team_provider_id > 0),
    constraint ck_event_detail_observation_tournament
        check (
            (tournament_provider_id is null and tournament_name is null)
            or (tournament_provider_id > 0 and tournament_name is not null)
        ),
    constraint ck_event_detail_observation_venue
        check (
            (venue_provider_id is null and venue_name is null and venue_city is null)
            or (venue_provider_id > 0 and venue_name is not null)
        ),
    constraint ck_event_detail_observation_season
        check (
            (season_provider_id is null and season_name is null)
            or (season_provider_id > 0 and season_name is not null)
        )
);

create index ix_event_detail_observation_latest
    on event_detail_observation (canonical_event_id, source_received_at desc, id desc);

create trigger event_detail_observation_append_only
before update or delete on event_detail_observation
for each row execute function reject_canonical_event_observation_mutation();

comment on table event_detail_observation is
    'J4 append-only EVENT_DETAILS observations. Only synthetic fixture provenance is authorized.';
comment on column event_detail_observation.source_payload_sha256 is
    'SHA-256 of exact classpath fixture bytes; no provider payload is stored in this table.';
