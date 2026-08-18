ALTER TABLE j5_event_incident
    ADD COLUMN period_text varchar(64),
    ADD COLUMN injury boolean,
    ADD COLUMN assist_provider_id bigint,
    ADD COLUMN assist_name varchar(200),
    ADD COLUMN goal_origin varchar(64),
    ADD COLUMN injury_time_length smallint,
    ADD COLUMN var_confirmed boolean,
    ADD COLUMN rescinded boolean,
    ADD COLUMN description varchar(200),
    ADD COLUMN shootout_sequence smallint;

ALTER TABLE j5_event_incident
    DROP CONSTRAINT ck_j5_event_incident_time,
    DROP CONSTRAINT ck_j5_event_incident_player,
    DROP CONSTRAINT ck_j5_event_incident_player_in,
    DROP CONSTRAINT ck_j5_event_incident_player_out;

ALTER TABLE j5_event_incident
    ADD CONSTRAINT ck_j5_event_incident_time CHECK (
        minute BETWEEN 0 AND 300
        AND (added_time IS NULL OR added_time BETWEEN 0 AND 300)
    ),
    ADD CONSTRAINT ck_j5_event_incident_player CHECK (
        (player_provider_id IS NULL AND player_name IS NULL)
        OR (player_name IS NOT NULL AND (player_provider_id IS NULL OR player_provider_id > 0))
    ),
    ADD CONSTRAINT ck_j5_event_incident_player_in CHECK (
        (player_in_provider_id IS NULL AND player_in_name IS NULL)
        OR (player_in_name IS NOT NULL
            AND (player_in_provider_id IS NULL OR player_in_provider_id > 0))
    ),
    ADD CONSTRAINT ck_j5_event_incident_player_out CHECK (
        (player_out_provider_id IS NULL AND player_out_name IS NULL)
        OR (player_out_name IS NOT NULL
            AND (player_out_provider_id IS NULL OR player_out_provider_id > 0))
    ),
    ADD CONSTRAINT ck_j5_event_incident_assist CHECK (
        (assist_provider_id IS NULL AND assist_name IS NULL)
        OR (assist_name IS NOT NULL AND (assist_provider_id IS NULL OR assist_provider_id > 0))
    ),
    ADD CONSTRAINT ck_j5_event_incident_period_text CHECK (
        period_text IS NULL OR length(trim(period_text)) BETWEEN 1 AND 64
    ),
    ADD CONSTRAINT ck_j5_event_incident_goal_origin CHECK (
        goal_origin IS NULL OR length(trim(goal_origin)) BETWEEN 1 AND 64
    ),
    ADD CONSTRAINT ck_j5_event_incident_injury_time_length CHECK (
        injury_time_length IS NULL OR injury_time_length BETWEEN 0 AND 300
    ),
    ADD CONSTRAINT ck_j5_event_incident_description CHECK (
        description IS NULL OR length(trim(description)) BETWEEN 1 AND 200
    ),
    ADD CONSTRAINT ck_j5_event_incident_shootout_sequence CHECK (
        shootout_sequence IS NULL OR shootout_sequence BETWEEN 1 AND 999
    );

ALTER TABLE j5_event_data_observation
    DROP CONSTRAINT ck_j5_event_data_parser;

ALTER TABLE j5_event_data_observation
    ADD CONSTRAINT ck_j5_event_data_parser CHECK (
        (endpoint_type = 'EVENT_STATISTICS'
            AND parser_version IN (
                'event-statistics-v1',
                'event-statistics-v2',
                'event-statistics-unavailable-v1'
            ))
        OR (endpoint_type = 'EVENT_INCIDENTS'
            AND parser_version IN (
                'event-incidents-v1',
                'event-incidents-v2',
                'event-incidents-v3',
                'event-incidents-v4',
                'event-incidents-v5',
                'event-incidents-v6',
                'event-incidents-unavailable-v1'
            ))
        OR (endpoint_type = 'EVENT_LINEUPS'
            AND parser_version IN (
                'event-lineups-v1',
                'event-lineups-v2',
                'event-lineups-unavailable-v1'
            ))
    );

COMMENT ON COLUMN j5_event_incident.minute IS
    'Normalized effective football minute. A negative provider card time uses benchTime; a shootout may use its first nested action time.';
COMMENT ON COLUMN j5_event_incident.added_time IS
    'Optional provider added time. The period-only 999 technical sentinel remains raw-only.';
COMMENT ON COLUMN j5_event_incident.period_text IS
    'Optional period marker text retained by event-incidents-v6.';
COMMENT ON COLUMN j5_event_incident.injury IS
    'Optional substitution injury flag retained by event-incidents-v6.';
COMMENT ON COLUMN j5_event_incident.assist_provider_id IS
    'Optional provider identifier of the goal assist.';
COMMENT ON COLUMN j5_event_incident.assist_name IS
    'Optional goal-assist display name; may exist without a provider identifier.';
COMMENT ON COLUMN j5_event_incident.goal_origin IS
    'Optional normalized special goal origin, for example penalty or ownGoal.';
COMMENT ON COLUMN j5_event_incident.injury_time_length IS
    'Optional announced injury-time length in minutes.';
COMMENT ON COLUMN j5_event_incident.var_confirmed IS
    'Optional confirmation flag for a VAR decision.';
COMMENT ON COLUMN j5_event_incident.rescinded IS
    'Optional provider flag indicating that a card was rescinded.';
COMMENT ON COLUMN j5_event_incident.description IS
    'Optional provider penalty outcome description.';
COMMENT ON COLUMN j5_event_incident.shootout_sequence IS
    'Optional provider order of a penalty-shootout attempt.';
COMMENT ON COLUMN j5_event_data_observation.parser_version IS
    'Versioned normalizer. event-incidents-v6 covers the reviewed football incident business rules.';
