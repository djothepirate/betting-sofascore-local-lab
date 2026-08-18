ALTER TABLE j5_event_incident
    ADD COLUMN incident_class varchar(64),
    ADD COLUMN reason varchar(200),
    ADD CONSTRAINT ck_j5_event_incident_class CHECK (
        incident_class IS NULL OR length(trim(incident_class)) BETWEEN 1 AND 64
    ),
    ADD CONSTRAINT ck_j5_event_incident_reason CHECK (
        reason IS NULL OR length(trim(reason)) BETWEEN 1 AND 200
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
    'Normalized provider match minute. V5 uses benchTime only for cards carrying the observed time=-5 technical marker.';
COMMENT ON COLUMN j5_event_incident.incident_class IS
    'Optional provider card class retained by event-incidents-v5, for example yellow.';
COMMENT ON COLUMN j5_event_incident.reason IS
    'Optional provider card reason retained by event-incidents-v5.';
COMMENT ON COLUMN j5_event_data_observation.parser_version IS
    'Versioned normalizer. event-incidents-v5 preserves V4 substitutions and provider bench-card details.';
