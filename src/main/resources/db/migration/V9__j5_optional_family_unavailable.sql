ALTER TABLE provider_snapshot
    DROP CONSTRAINT ck_provider_snapshot_schema_status;

ALTER TABLE provider_snapshot
    ADD CONSTRAINT ck_provider_snapshot_schema_status
        CHECK (schema_status IN (
            'RAW_ONLY',
            'PARSED',
            'COHERENCE_CHECKED',
            'HUMAN_VALIDATED',
            'REJECTED',
            'SCHEMA_INCOMPATIBLE',
            'UNEXPECTED_CONTENT',
            'TRANSPORT_ERROR',
            'ENDPOINT_UNAVAILABLE'
        ));

UPDATE provider_snapshot
SET schema_status = 'ENDPOINT_UNAVAILABLE',
    error_code = NULL
WHERE logical_endpoint IN (
        'EVENT_STATISTICS',
        'EVENT_INCIDENTS',
        'EVENT_LINEUPS'
    )
  AND http_status = 404
  AND schema_status = 'TRANSPORT_ERROR'
  AND error_code = 'HTTP_STATUS_404';

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
                'event-incidents-unavailable-v1'
            ))
        OR (endpoint_type = 'EVENT_LINEUPS'
            AND parser_version IN (
                'event-lineups-v1',
                'event-lineups-v2',
                'event-lineups-unavailable-v1'
            ))
    );

ALTER TABLE j5_event_data_observation
    DROP CONSTRAINT ck_j5_event_data_completeness_status;

ALTER TABLE j5_event_data_observation
    ADD CONSTRAINT ck_j5_event_data_completeness_status
        CHECK (
            (
                completeness_status = 'COMPLETE'
                AND expected_signals > 0
                AND present_signals = expected_signals
                AND completeness_score = 100
                AND jsonb_array_length(missing_paths_json) = 0
            )
            OR (
                completeness_status = 'PARTIAL'
                AND expected_signals > 0
                AND present_signals < expected_signals
                AND completeness_score = ((present_signals::bigint * 100) / expected_signals)
                AND jsonb_array_length(missing_paths_json) = expected_signals - present_signals
            )
            OR (
                completeness_status = 'EMPTY_VALID'
                AND expected_signals = 0
                AND present_signals = 0
                AND completeness_score = 100
                AND jsonb_array_length(missing_paths_json) = 0
            )
            OR (
                completeness_status = 'UNAVAILABLE'
                AND expected_signals = 0
                AND present_signals = 0
                AND completeness_score = 0
                AND jsonb_array_length(missing_paths_json) = 0
            )
        );

COMMENT ON COLUMN j5_event_data_observation.completeness_status IS
    'COMPLETE, PARTIAL, EMPTY_VALID, or UNAVAILABLE when the exact J5 endpoint returned HTTP 404.';
