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
                'event-incidents-v7',
                'event-incidents-v8',
                'event-incidents-v9',
                'event-incidents-v10',
                'event-incidents-v11',
                'event-incidents-v12',
                'event-incidents-unavailable-v1'
            ))
        OR (endpoint_type = 'EVENT_LINEUPS'
            AND parser_version IN (
                'event-lineups-v1',
                'event-lineups-v2',
                'event-lineups-unavailable-v1'
            ))
    );

ALTER TABLE j5_event_incident
    DROP CONSTRAINT ck_j5_event_incident_time;

ALTER TABLE j5_event_incident
    ALTER COLUMN minute DROP NOT NULL;

ALTER TABLE j5_event_incident
    ADD CONSTRAINT ck_j5_event_incident_time CHECK (
        (
            (minute IS NOT NULL AND minute BETWEEN 0 AND 300)
            OR (
                minute IS NULL
                AND added_time IS NULL
                AND (
                    incident_type = 'penaltyShootout'
                    OR (incident_type = 'period' AND period_text = 'PEN')
                )
            )
        )
        AND (added_time IS NULL OR added_time BETWEEN 0 AND 300)
    );

COMMENT ON COLUMN j5_event_data_observation.parser_version IS
    'Versioned normalizer. event-incidents-v12 preserves an absent effective minute for an exact coherent terminal shootout while retaining all V11 rules.';

COMMENT ON COLUMN j5_event_incident.minute IS
    'Optional normalized effective football minute. V12 leaves it null only for an exact coherent unminuted penaltyShootout series and its terminal PEN marker.';
