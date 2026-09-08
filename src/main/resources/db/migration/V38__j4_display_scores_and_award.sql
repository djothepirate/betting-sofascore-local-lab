-- New J4 contract only. Existing observations, hashes and provenance remain unchanged.
alter table event_detail_observation
    add column is_awarded boolean,
    add column home_display_score integer,
    add column away_display_score integer;

alter table event_detail_observation
    drop constraint ck_event_detail_observation_parser;

alter table event_detail_observation
    add constraint ck_event_detail_observation_parser
        check (parser_version in ('event-details-v1', 'event-details-v2', 'event-details-v3')),
    add constraint ck_event_detail_observation_display_score_range
        check (
            (home_display_score is null or home_display_score between 0 and 999)
            and (away_display_score is null or away_display_score between 0 and 999)
        ),
    add constraint ck_event_detail_observation_display_score_contract
        check (
            parser_version = 'event-details-v3'
            or (is_awarded is null and home_display_score is null and away_display_score is null)
        );

comment on column event_detail_observation.is_awarded is
    'Optional event.isAwarded from event-details-v3; null means no normalized award flag.';
comment on column event_detail_observation.home_display_score is
    'Optional event.homeScore.display from event-details-v3, 0..999; null is distinct from zero.';
comment on column event_detail_observation.away_display_score is
    'Optional event.awayScore.display from event-details-v3, 0..999; null is distinct from zero.';
