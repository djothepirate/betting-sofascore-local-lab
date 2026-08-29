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
                'event-incidents-v13',
                'event-incidents-v14',
                'event-incidents-unavailable-v1'
            ))
        OR (endpoint_type = 'EVENT_LINEUPS'
            AND parser_version IN (
                'event-lineups-v1',
                'event-lineups-v2',
                'event-lineups-unavailable-v1'
            ))
    );

COMMENT ON COLUMN j5_event_data_observation.parser_version IS
    'Versioned normalizer. event-incidents-v14 accepts the exact live Extra time period label while retaining all V13 rules.';
