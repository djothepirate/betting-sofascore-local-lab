-- J3 manual pagination: preserve append-only historical campaigns capped at
-- 25 pages while permitting newly created J3 campaigns to declare the revised
-- local bound of 35 pages. No evidence row is updated or deleted; the existing
-- J8 trigger continues to enforce each campaign's stored maximum_units value.

alter table j8_benchmark_campaign
    drop constraint ck_j8_benchmark_campaign_bound;

alter table j8_benchmark_campaign
    add constraint ck_j8_benchmark_campaign_bound
        check (
            (campaign_type = 'J3_SCHEDULED_EVENTS' and maximum_units in (25, 35))
            or (campaign_type = 'J3_TOURNAMENT_DISCOVERY' and maximum_units = 1)
            or (campaign_type = 'J4_EVENT_DETAILS_PHASE1' and maximum_units = 2)
            or (campaign_type = 'J4_EVENT_DETAILS_PHASE2' and maximum_units = 1)
            or (campaign_type = 'J5_EVENT_DATA' and maximum_units = 3)
        );

alter table j8_benchmark_unit
    drop constraint ck_j8_benchmark_unit_canonical_request_key;

alter table j8_benchmark_unit
    add constraint ck_j8_benchmark_unit_canonical_request_key
        check (
            (
                logical_endpoint in (
                    'EVENT_DETAILS',
                    'EVENT_STATISTICS',
                    'EVENT_INCIDENTS',
                    'EVENT_LINEUPS'
                )
                and request_key = logical_endpoint || '|eventId=' || provider_event_id::text
            )
            or (
                logical_endpoint = 'SCHEDULED_EVENTS'
                and request_key ~ '^SCHEDULED_EVENTS[|]date=[0-9]{4}-[0-9]{2}-[0-9]{2}[|]page=([1-9]|[12][0-9]|3[0-5])$'
            )
            or (
                logical_endpoint = 'TOURNAMENT_SCHEDULED_EVENTS'
                and request_key ~ '^TOURNAMENT_SCHEDULED_EVENTS[|]date=[0-9]{4}-[0-9]{2}-[0-9]{2}[|]uniqueTournamentId=[1-9][0-9]{0,18}$'
            )
        );
