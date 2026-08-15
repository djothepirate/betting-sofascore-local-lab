ALTER TABLE j5_event_data_observation
    DROP CONSTRAINT ck_j5_event_data_parser;

ALTER TABLE j5_event_data_observation
    ADD CONSTRAINT ck_j5_event_data_parser CHECK (
        (endpoint_type = 'EVENT_STATISTICS'
            AND parser_version IN ('event-statistics-v1', 'event-statistics-v2'))
        OR (endpoint_type = 'EVENT_INCIDENTS'
            AND parser_version IN ('event-incidents-v1', 'event-incidents-v2'))
        OR (endpoint_type = 'EVENT_LINEUPS'
            AND parser_version IN ('event-lineups-v1', 'event-lineups-v2'))
    );
