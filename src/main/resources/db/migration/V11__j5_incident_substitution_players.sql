ALTER TABLE j5_event_incident
    ADD COLUMN player_in_provider_id bigint,
    ADD COLUMN player_in_name varchar(200),
    ADD COLUMN player_out_provider_id bigint,
    ADD COLUMN player_out_name varchar(200),
    ADD CONSTRAINT ck_j5_event_incident_player_in CHECK (
        (player_in_provider_id IS NULL AND player_in_name IS NULL)
        OR (player_in_provider_id > 0 AND player_in_name IS NOT NULL)
    ),
    ADD CONSTRAINT ck_j5_event_incident_player_out CHECK (
        (player_out_provider_id IS NULL AND player_out_name IS NULL)
        OR (player_out_provider_id > 0 AND player_out_name IS NOT NULL)
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
                'event-incidents-unavailable-v1'
            ))
        OR (endpoint_type = 'EVENT_LINEUPS'
            AND parser_version IN (
                'event-lineups-v1',
                'event-lineups-v2',
                'event-lineups-unavailable-v1'
            ))
    );

COMMENT ON COLUMN j5_event_incident.player_in_provider_id IS
    'Provider identifier of the incoming player for a substitution, when supplied.';
COMMENT ON COLUMN j5_event_incident.player_in_name IS
    'Provider name of the incoming player paired with player_in_provider_id.';
COMMENT ON COLUMN j5_event_incident.player_out_provider_id IS
    'Provider identifier of the outgoing player for a substitution, when supplied.';
COMMENT ON COLUMN j5_event_incident.player_out_name IS
    'Provider name of the outgoing player paired with player_out_provider_id.';
COMMENT ON COLUMN j5_event_data_observation.parser_version IS
    'Versioned normalizer. event-incidents-v4 preserves incoming and outgoing substitution players.';
