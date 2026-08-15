create table j5_event_data_observation (
    id bigint generated always as identity primary key,
    canonical_event_id uuid not null,
    endpoint_type varchar(32) not null,
    source_kind varchar(32) not null,
    source_reference varchar(128) not null,
    source_snapshot_id bigint,
    source_fixture_id varchar(100),
    source_payload_sha256 varchar(64) not null,
    parser_version varchar(32) not null,
    source_received_at timestamptz not null,
    completeness_status varchar(32) not null,
    completeness_score smallint not null,
    present_signals integer not null,
    expected_signals integer not null,
    missing_paths_json jsonb not null,
    lineups_confirmed boolean,
    normalized_sha256 varchar(64) not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_j5_event_data_event
        foreign key (canonical_event_id)
        references canonical_event (id)
        on delete restrict,
    constraint fk_j5_event_data_snapshot
        foreign key (source_snapshot_id)
        references provider_snapshot (id)
        on delete restrict,
    constraint uq_j5_event_data_source_version
        unique (
            canonical_event_id,
            endpoint_type,
            source_kind,
            source_reference,
            normalized_sha256
        ),
    constraint uq_j5_event_data_id_endpoint
        unique (id, endpoint_type),
    constraint ck_j5_event_data_endpoint
        check (endpoint_type in (
            'EVENT_STATISTICS',
            'EVENT_INCIDENTS',
            'EVENT_LINEUPS'
        )),
    constraint ck_j5_event_data_source
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
    constraint ck_j5_event_data_sha256
        check (
            source_payload_sha256 ~ '^[0-9a-f]{64}$'
            and normalized_sha256 ~ '^[0-9a-f]{64}$'
        ),
    constraint ck_j5_event_data_parser
        check (
            (endpoint_type = 'EVENT_STATISTICS' and parser_version = 'event-statistics-v1')
            or (endpoint_type = 'EVENT_INCIDENTS' and parser_version = 'event-incidents-v1')
            or (endpoint_type = 'EVENT_LINEUPS' and parser_version = 'event-lineups-v1')
        ),
    constraint ck_j5_event_data_completeness_shape
        check (
            completeness_score between 0 and 100
            and present_signals >= 0
            and expected_signals >= 0
            and present_signals <= expected_signals
            and jsonb_typeof(missing_paths_json) = 'array'
        ),
    constraint ck_j5_event_data_completeness_status
        check (
            (
                completeness_status = 'COMPLETE'
                and expected_signals > 0
                and present_signals = expected_signals
                and completeness_score = 100
                and jsonb_array_length(missing_paths_json) = 0
            )
            or (
                completeness_status = 'PARTIAL'
                and expected_signals > 0
                and present_signals < expected_signals
                and completeness_score = ((present_signals::bigint * 100) / expected_signals)
                and jsonb_array_length(missing_paths_json) = expected_signals - present_signals
            )
            or (
                completeness_status = 'EMPTY_VALID'
                and expected_signals = 0
                and present_signals = 0
                and completeness_score = 100
                and jsonb_array_length(missing_paths_json) = 0
            )
        ),
    constraint ck_j5_event_data_lineups_confirmation
        check (
            (endpoint_type = 'EVENT_LINEUPS' and lineups_confirmed is not null)
            or (endpoint_type <> 'EVENT_LINEUPS' and lineups_confirmed is null)
        )
);

create table j5_event_metric (
    id bigint generated always as identity primary key,
    observation_id bigint not null,
    endpoint_type varchar(32) not null default 'EVENT_STATISTICS',
    metric_order integer not null,
    period varchar(32) not null,
    group_name varchar(100) not null,
    metric_code varchar(100) not null,
    metric_name varchar(150) not null,
    home_value varchar(100),
    away_value varchar(100),
    constraint fk_j5_event_metric_observation
        foreign key (observation_id, endpoint_type)
        references j5_event_data_observation (id, endpoint_type)
        on delete restrict,
    constraint uq_j5_event_metric_order
        unique (observation_id, metric_order),
    constraint ck_j5_event_metric_endpoint
        check (endpoint_type = 'EVENT_STATISTICS'),
    constraint ck_j5_event_metric_order
        check (metric_order >= 0)
);

create table j5_event_incident (
    id bigint generated always as identity primary key,
    observation_id bigint not null,
    endpoint_type varchar(32) not null default 'EVENT_INCIDENTS',
    incident_order integer not null,
    incident_type varchar(64) not null,
    minute smallint not null,
    added_time smallint,
    is_home boolean,
    participant_provider_id bigint,
    player_provider_id bigint,
    player_name varchar(200),
    home_score smallint,
    away_score smallint,
    constraint fk_j5_event_incident_observation
        foreign key (observation_id, endpoint_type)
        references j5_event_data_observation (id, endpoint_type)
        on delete restrict,
    constraint uq_j5_event_incident_order
        unique (observation_id, incident_order),
    constraint ck_j5_event_incident_endpoint
        check (endpoint_type = 'EVENT_INCIDENTS'),
    constraint ck_j5_event_incident_order
        check (incident_order >= 0),
    constraint ck_j5_event_incident_time
        check (minute between 0 and 300 and (added_time is null or added_time between 0 and 30)),
    constraint ck_j5_event_incident_participant
        check (participant_provider_id is null or participant_provider_id > 0),
    constraint ck_j5_event_incident_player
        check (
            (player_provider_id is null and player_name is null)
            or (player_provider_id > 0 and player_name is not null)
        ),
    constraint ck_j5_event_incident_score
        check (
            (home_score is null and away_score is null)
            or (
                home_score between 0 and 99
                and away_score between 0 and 99
            )
        )
);

create table j5_event_lineup_side (
    observation_id bigint not null,
    endpoint_type varchar(32) not null default 'EVENT_LINEUPS',
    side varchar(8) not null,
    formation varchar(32),
    primary key (observation_id, side),
    constraint uq_j5_event_lineup_side_endpoint
        unique (observation_id, endpoint_type, side),
    constraint fk_j5_event_lineup_side_observation
        foreign key (observation_id, endpoint_type)
        references j5_event_data_observation (id, endpoint_type)
        on delete restrict,
    constraint ck_j5_event_lineup_side_endpoint
        check (endpoint_type = 'EVENT_LINEUPS'),
    constraint ck_j5_event_lineup_side_value
        check (side in ('HOME', 'AWAY'))
);

create table j5_event_lineup_player (
    id bigint generated always as identity primary key,
    observation_id bigint not null,
    endpoint_type varchar(32) not null default 'EVENT_LINEUPS',
    side varchar(8) not null,
    player_order integer not null,
    player_provider_id bigint not null,
    player_name varchar(200) not null,
    shirt_number smallint,
    position varchar(32),
    starter boolean not null,
    constraint fk_j5_event_lineup_player_side
        foreign key (observation_id, endpoint_type, side)
        references j5_event_lineup_side (observation_id, endpoint_type, side)
        on delete restrict,
    constraint uq_j5_event_lineup_player_order
        unique (observation_id, side, player_order),
    constraint ck_j5_event_lineup_player_endpoint
        check (endpoint_type = 'EVENT_LINEUPS'),
    constraint ck_j5_event_lineup_player_order
        check (player_order >= 0),
    constraint ck_j5_event_lineup_player_id
        check (player_provider_id > 0),
    constraint ck_j5_event_lineup_player_shirt
        check (shirt_number is null or shirt_number between 1 and 999)
);

create index ix_j5_event_data_latest
    on j5_event_data_observation (
        canonical_event_id,
        endpoint_type,
        source_received_at desc,
        id desc
    );

create function reject_j5_event_data_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'J5 normalized event data is append-only';
end;
$$;

create trigger j5_event_data_observation_append_only
before update or delete on j5_event_data_observation
for each row execute function reject_j5_event_data_mutation();

create trigger j5_event_metric_append_only
before update or delete on j5_event_metric
for each row execute function reject_j5_event_data_mutation();

create trigger j5_event_incident_append_only
before update or delete on j5_event_incident
for each row execute function reject_j5_event_data_mutation();

create trigger j5_event_lineup_side_append_only
before update or delete on j5_event_lineup_side
for each row execute function reject_j5_event_data_mutation();

create trigger j5_event_lineup_player_append_only
before update or delete on j5_event_lineup_player
for each row execute function reject_j5_event_data_mutation();

comment on table j5_event_data_observation is
    'J5 append-only source, completeness and normalized-family batch metadata.';
comment on column j5_event_data_observation.missing_paths_json is
    'Documented completeness gaps only; never a copy of the provider payload.';
comment on table j5_event_metric is
    'J5 normalized EVENT_STATISTICS rows separated from their source fixture bytes.';
comment on table j5_event_incident is
    'J5 normalized EVENT_INCIDENTS rows in source order.';
comment on table j5_event_lineup_side is
    'J5 HOME and AWAY composition metadata, including explicit empty sides.';
comment on table j5_event_lineup_player is
    'J5 normalized EVENT_LINEUPS players in source order.';
