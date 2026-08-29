package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkReadEvidence;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import com.bettingproject.sofascorelocal.application.benchmark.J8DirectObservationCohorts;
import com.bettingproject.sofascorelocal.application.benchmark.J8LateChangeEvidence;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J8BenchmarkReadStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class JdbcJ8BenchmarkReadStore implements J8BenchmarkReadStore {

    private static final String CAMPAIGN_SQL = """
            select
                campaign.campaign_id,
                campaign.campaign_type,
                campaign.execution_mode,
                campaign.started_at,
                campaign.maximum_units,
                result.finished_at,
                result.terminal_state,
                result.completed_units
            from j8_benchmark_campaign campaign
            left join j8_benchmark_campaign_result result
              on result.campaign_id = campaign.campaign_id
             and result.created_at <= :asOf
             and result.finished_at <= :asOf
            where campaign.created_at <= :asOf
              and campaign.started_at <= :asOf
              and (
                    :hasWindow = false
                    or (
                        campaign.started_at >= :fromInclusive
                        and campaign.started_at < :toExclusive
                    )
                    or exists (
                        select 1
                        from j8_benchmark_unit selected_unit
                        where selected_unit.campaign_id = campaign.campaign_id
                          and selected_unit.created_at <= :asOf
                          and selected_unit.declared_at <= :asOf
                          and selected_unit.declared_at >= :fromInclusive
                          and selected_unit.declared_at < :toExclusive
                    )
                    or exists (
                        select 1
                        from j8_benchmark_unit attempted_unit
                        join j8_provider_call_attempt selected_attempt
                          on selected_attempt.unit_id = attempted_unit.id
                        where attempted_unit.campaign_id = campaign.campaign_id
                          and attempted_unit.created_at <= :asOf
                          and selected_attempt.created_at <= :asOf
                          and selected_attempt.started_at <= :asOf
                          and selected_attempt.started_at >= :fromInclusive
                          and selected_attempt.started_at < :toExclusive
                    )
              )
            order by campaign.started_at, campaign.campaign_id
            """;

    private static final String UNIT_SQL = """
            select
                campaign.campaign_id,
                campaign.campaign_type,
                campaign.execution_mode,
                unit.id as unit_id,
                unit.unit_ordinal,
                unit.logical_endpoint,
                unit.canonical_event_id,
                unit.provider_event_id,
                unit.declared_at,
                attempt.id as attempt_id,
                attempt.started_at as attempt_started_at,
                result.resolved_at,
                result.resolution_source,
                result.outcome_type,
                coalesce(result.response_received, false) as response_received,
                result.http_status,
                result.latency_ms,
                result.snapshot_id,
                result.snapshot_occurrence_id,
                coalesce(occurrence.persistence_outcome = 'DEDUPLICATED', false)
                    as deduplicated_response,
                result.parser_version,
                result.schema_status,
                coalesce(result.parser_warning_count, 0) as parser_warning_count,
                result.completeness_status,
                result.completeness_score
            from j8_benchmark_unit unit
            join j8_benchmark_campaign campaign
              on campaign.campaign_id = unit.campaign_id
            left join j8_provider_call_attempt attempt
              on attempt.unit_id = unit.id
             and attempt.created_at <= :asOf
             and attempt.started_at <= :asOf
             and (:hasWindow = false or attempt.started_at >= :fromInclusive)
             and (:hasWindow = false or attempt.started_at < :toExclusive)
            left join j8_benchmark_unit_result result
              on result.unit_id = unit.id
             and result.created_at <= :asOf
             and result.resolved_at <= :asOf
            left join provider_snapshot_occurrence occurrence
              on occurrence.id = result.snapshot_occurrence_id
             and occurrence.created_at <= :asOf
            where campaign.created_at <= :asOf
              and unit.created_at <= :asOf
              and unit.declared_at <= :asOf
              and (
                    :hasWindow = false
                    or (
                        unit.declared_at >= :fromInclusive
                        and unit.declared_at < :toExclusive
                    )
                    or attempt.id is not null
              )
            order by unit.declared_at, campaign.campaign_id, unit.unit_ordinal, unit.id
            """;

    private static final String LEGACY_RESPONSE_SQL = """
            select
                occurrence.id as occurrence_id,
                snapshot.id as snapshot_id,
                normalized_observation.ids as normalized_observation_ids,
                snapshot.logical_endpoint,
                occurrence.requested_at,
                occurrence.received_at,
                occurrence.http_status,
                occurrence.latency_ms,
                occurrence.parser_version,
                case
                    when occurrence.http_status = 404 then 'ENDPOINT_UNAVAILABLE'
                    when cardinality(normalized_observation.ids) > 0 then 'PARSED'
                    else null
                end as schema_status,
                occurrence.persistence_outcome
            from provider_snapshot_occurrence occurrence
            join provider_snapshot snapshot
              on snapshot.id = occurrence.snapshot_id
            left join lateral (
                select array_agg(proof.id order by proof.id) as ids
                from (
                    select observation.id
                    from canonical_event_observation observation
                    where snapshot.logical_endpoint in (
                            'SCHEDULED_EVENTS', 'TOURNAMENT_SCHEDULED_EVENTS'
                    )
                      and observation.source_snapshot_id = snapshot.id
                      and observation.source_kind = 'PROVIDER_SNAPSHOT'
                      and observation.source_received_at = occurrence.received_at
                      and observation.parser_version = occurrence.parser_version
                      and observation.created_at <= :asOf
                      and observation.source_received_at <= :asOf
                    union all
                    select observation.id
                    from event_detail_observation observation
                    where snapshot.logical_endpoint = 'EVENT_DETAILS'
                      and observation.source_snapshot_id = snapshot.id
                      and observation.source_kind = 'PROVIDER_SNAPSHOT'
                      and observation.source_received_at = occurrence.received_at
                      and observation.parser_version = occurrence.parser_version
                      and observation.created_at <= :asOf
                      and observation.source_received_at <= :asOf
                    union all
                    select observation.id
                    from j5_event_data_observation observation
                    where snapshot.logical_endpoint in (
                            'EVENT_STATISTICS', 'EVENT_INCIDENTS', 'EVENT_LINEUPS'
                    )
                      and observation.source_snapshot_id = snapshot.id
                      and observation.endpoint_type = snapshot.logical_endpoint
                      and observation.source_kind = 'PROVIDER_SNAPSHOT'
                      and observation.source_received_at = occurrence.received_at
                      and observation.parser_version = occurrence.parser_version
                      and observation.created_at <= :asOf
                      and observation.source_received_at <= :asOf
                ) proof
            ) normalized_observation on true
            where snapshot.provider = 'SOFASCORE'
              and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
              and snapshot.created_at <= :asOf
              and occurrence.created_at <= :asOf
              and occurrence.requested_at <= :asOf
              and occurrence.received_at is not null
              and occurrence.received_at <= :asOf
              and (:hasWindow = false or occurrence.requested_at >= :fromInclusive)
              and (:hasWindow = false or occurrence.requested_at < :toExclusive)
              and not exists (
                    select 1
                    from j8_benchmark_unit_result result
                    where result.snapshot_occurrence_id = occurrence.id
                      and result.created_at <= :asOf
              )
              and not exists (
                    select 1
                    from j8_provider_call_attempt j8_attempt
                    join j8_benchmark_unit j8_unit
                      on j8_unit.id = j8_attempt.unit_id
                    where j8_unit.logical_endpoint = snapshot.logical_endpoint
                      and j8_unit.request_key = snapshot.request_key
                      and j8_attempt.created_at <= occurrence.created_at
                      and j8_attempt.created_at <= :asOf
              )
            order by occurrence.requested_at, occurrence.id
            """;

    private static final String MANUAL_IMPORT_OCCURRENCE_COUNT_SQL = """
            select count(*)
            from provider_snapshot_occurrence occurrence
            join provider_snapshot snapshot
              on snapshot.id = occurrence.snapshot_id
            where snapshot.provider = 'SOFASCORE'
              and snapshot.acquisition_mode = 'MANUAL_LOCAL_JSON_IMPORT'
              and snapshot.created_at <= :asOf
              and occurrence.created_at <= :asOf
              and occurrence.requested_at <= :asOf
              and occurrence.received_at is not null
              and occurrence.received_at <= :asOf
              and (:hasWindow = false or occurrence.requested_at >= :fromInclusive)
              and (:hasWindow = false or occurrence.requested_at < :toExclusive)
            """;

    private static final String SYNTHETIC_OBSERVATION_COUNT_SQL = """
            select count(*)
            from (
                select observation.source_received_at, observation.created_at
                from canonical_event_observation observation
                where observation.source_kind = 'SYNTHETIC_FIXTURE'
                union all
                select observation.source_received_at, observation.created_at
                from event_detail_observation observation
                where observation.source_kind = 'SYNTHETIC_FIXTURE'
                union all
                select observation.source_received_at, observation.created_at
                from j5_event_data_observation observation
                where observation.source_kind = 'SYNTHETIC_FIXTURE'
            ) evidence
            where evidence.created_at <= :asOf
              and evidence.source_received_at <= :asOf
              and (:hasWindow = false or evidence.source_received_at >= :fromInclusive)
              and (:hasWindow = false or evidence.source_received_at < :toExclusive)
            """;

    private static final String COHORT_SQL = """
            select
                unit.id as unit_id,
                campaign.campaign_type,
                unit.provider_event_id,
                coalesce(unit.canonical_event_id, target_event.id)
                    as canonical_event_id,
                unit.logical_endpoint,
                (attempt.id is not null and result.resolution_source = 'PROVIDER')
                    as provider_attempted,
                (
                    :hasWindow = false
                    or (
                        unit.declared_at >= :fromInclusive
                        and unit.declared_at < :toExclusive
                    )
                ) as declared_in_window,
                result.outcome_type,
                result.schema_status,
                result.parser_version,
                result.completeness_status,
                result.completeness_score,
                family_observation.present_signals,
                family_observation.expected_signals,
                case
                    when unit.logical_endpoint = 'EVENT_DETAILS'
                         and result_detail_observation.correlation_count = 1
                        then result_detail_observation.id
                    when unit.logical_endpoint in (
                            'EVENT_STATISTICS', 'EVENT_INCIDENTS', 'EVENT_LINEUPS'
                         )
                         and family_observation.correlation_count = 1
                        then family_observation.id
                    else null
                end as correlated_observation_id,
                case unit.logical_endpoint
                    when 'EVENT_DETAILS' then result_detail_observation.correlation_count = 1
                    when 'EVENT_STATISTICS' then family_observation.correlation_count = 1
                    when 'EVENT_INCIDENTS' then family_observation.correlation_count = 1
                    when 'EVENT_LINEUPS' then family_observation.correlation_count = 1
                    else false
                end as normalized_observation_present,
                state_observation.id is not null as canonical_state_present,
                case
                    when state_observation.id is not null then 'AVAILABLE'
                    else 'ABSENT'
                end as current_canonical_state,
                case
                    when current_detail_snapshot.id is null then 'ABSENT'
                    when current_detail_snapshot.http_status = 404
                        then 'UNAVAILABLE'
                    when detail_observation.id is not null
                        then 'AVAILABLE'
                    else 'INCOMPATIBLE'
                end as current_event_details,
                case
                    when current_statistics_snapshot.id is null then 'ABSENT'
                    when current_statistics_snapshot.http_status = 404
                        then 'UNAVAILABLE'
                    when current_statistics_observation.id is not null
                        then 'AVAILABLE'
                    else 'INCOMPATIBLE'
                end as current_statistics,
                case
                    when current_incidents_snapshot.id is null then 'ABSENT'
                    when current_incidents_snapshot.http_status = 404
                        then 'UNAVAILABLE'
                    when current_incidents_observation.id is not null
                        then 'AVAILABLE'
                    else 'INCOMPATIBLE'
                end as current_incidents,
                case
                    when current_lineups_snapshot.id is null then 'ABSENT'
                    when current_lineups_snapshot.http_status = 404
                        then 'UNAVAILABLE'
                    when current_lineups_observation.id is not null
                        then 'AVAILABLE'
                    else 'INCOMPATIBLE'
                end as current_lineups,
                current_statistics_observation.completeness_status
                    as current_statistics_completeness,
                current_incidents_observation.completeness_status
                    as current_incidents_completeness,
                current_lineups_observation.completeness_status
                    as current_lineups_completeness,
                state_observation.id as current_canonical_observation_id,
                current_detail_snapshot.snapshot_occurrence_id
                    as current_detail_snapshot_occurrence_id,
                current_detail_snapshot.id as current_detail_snapshot_id,
                detail_observation.id as current_detail_observation_id,
                current_statistics_snapshot.snapshot_occurrence_id
                    as current_statistics_snapshot_occurrence_id,
                current_statistics_snapshot.id as current_statistics_snapshot_id,
                current_statistics_observation.id
                    as current_statistics_observation_id,
                current_incidents_snapshot.snapshot_occurrence_id
                    as current_incidents_snapshot_occurrence_id,
                current_incidents_snapshot.id as current_incidents_snapshot_id,
                current_incidents_observation.id as current_incidents_observation_id,
                current_lineups_snapshot.snapshot_occurrence_id
                    as current_lineups_snapshot_occurrence_id,
                current_lineups_snapshot.id as current_lineups_snapshot_id,
                current_lineups_observation.id as current_lineups_observation_id,
                state_observation.tournament_name as competition,
                detail_observation.season_name as season,
                state_observation.status_type as event_status
            from j8_benchmark_unit unit
            join j8_benchmark_campaign campaign
              on campaign.campaign_id = unit.campaign_id
             and campaign.execution_mode = 'GUARDED_PROVIDER'
             and campaign.campaign_type in (
                    'J4_EVENT_DETAILS_PHASE1',
                    'J4_EVENT_DETAILS_PHASE2',
                    'J5_EVENT_DATA'
             )
            left join canonical_event target_event
              on target_event.provider = 'SOFASCORE'
             and target_event.provider_event_id = unit.provider_event_id
            left join j8_provider_call_attempt attempt
              on attempt.unit_id = unit.id
             and attempt.created_at <= :asOf
             and attempt.started_at <= :asOf
             and (:hasWindow = false or attempt.started_at >= :fromInclusive)
             and (:hasWindow = false or attempt.started_at < :toExclusive)
            left join j8_benchmark_unit_result result
              on result.unit_id = unit.id
             and result.created_at <= :asOf
             and result.resolved_at <= :asOf
            left join provider_snapshot_occurrence result_occurrence
              on result_occurrence.id = result.snapshot_occurrence_id
             and result_occurrence.snapshot_id = result.snapshot_id
             and result_occurrence.created_at <= :asOf
             and result_occurrence.received_at <= :asOf
            left join lateral (
                select observation.id, count(*) over () as correlation_count
                from event_detail_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_snapshot_id = result.snapshot_id
                  and observation.parser_version = result.parser_version
                  and (
                        observation.source_received_at = result_occurrence.received_at
                        or result_occurrence.persistence_outcome = 'DEDUPLICATED'
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) result_detail_observation on unit.logical_endpoint = 'EVENT_DETAILS'
            left join lateral (
                select observation.id,
                       observation.present_signals,
                       observation.expected_signals,
                       count(*) over () as correlation_count
                from j5_event_data_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.endpoint_type = unit.logical_endpoint
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_snapshot_id = result.snapshot_id
                  and observation.parser_version = result.parser_version
                  and observation.completeness_status = result.completeness_status
                  and observation.completeness_score = result.completeness_score
                  and (
                        observation.source_received_at = result_occurrence.received_at
                        or result_occurrence.persistence_outcome = 'DEDUPLICATED'
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) family_observation on unit.logical_endpoint in (
                'EVENT_STATISTICS', 'EVENT_INCIDENTS', 'EVENT_LINEUPS'
            )
            left join lateral (
                select observation.id, observation.tournament_name, observation.status_type
                from canonical_event_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) state_observation on true
            left join lateral (
                select occurrence.id as snapshot_occurrence_id,
                       snapshot.id,
                       occurrence.http_status,
                       occurrence.received_at,
                       occurrence.parser_version as occurrence_parser_version,
                       occurrence.persistence_outcome,
                       exists (
                           select 1
                           from j8_provider_call_attempt j8_attempt
                           join j8_benchmark_unit j8_unit
                             on j8_unit.id = j8_attempt.unit_id
                           where j8_unit.logical_endpoint = snapshot.logical_endpoint
                             and j8_unit.request_key = snapshot.request_key
                             and j8_attempt.created_at <= occurrence.created_at
                             and j8_attempt.created_at <= :asOf
                       ) as j8_attempt_present,
                       occurrence_result.unit_id as result_unit_id,
                       occurrence_result.outcome_type as result_outcome_type,
                       occurrence_result.schema_status as result_schema_status,
                       occurrence_result.parser_version as result_parser_version
                from provider_snapshot_occurrence occurrence
                join provider_snapshot snapshot
                  on snapshot.id = occurrence.snapshot_id
                left join j8_benchmark_unit_result occurrence_result
                  on occurrence_result.snapshot_occurrence_id = occurrence.id
                 and occurrence_result.created_at <= :asOf
                 and occurrence_result.resolved_at <= :asOf
                where snapshot.provider = 'SOFASCORE'
                  and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                  and snapshot.logical_endpoint = 'EVENT_DETAILS'
                  and snapshot.request_key = 'EVENT_DETAILS|eventId='
                        || unit.provider_event_id::text
                  and occurrence.created_at <= :asOf
                  and occurrence.requested_at <= :asOf
                  and (occurrence.received_at is null
                       or occurrence.received_at <= :asOf)
                  and snapshot.created_at <= :asOf
                order by occurrence.requested_at desc, occurrence.id desc
                limit 1
            ) current_detail_snapshot on true
            left join lateral (
                select observation.id,
                       observation.source_snapshot_id,
                       observation.season_name
                from event_detail_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_snapshot_id = current_detail_snapshot.id
                  and (
                        (
                            current_detail_snapshot.result_unit_id is null
                            and not current_detail_snapshot.j8_attempt_present
                            and observation.parser_version
                                = current_detail_snapshot.occurrence_parser_version
                            and observation.source_received_at
                                = current_detail_snapshot.received_at
                        )
                        or (
                            current_detail_snapshot.result_outcome_type = 'PARSED'
                            and current_detail_snapshot.result_schema_status = 'PARSED'
                            and current_detail_snapshot.result_parser_version
                                = current_detail_snapshot.occurrence_parser_version
                            and observation.parser_version
                                = current_detail_snapshot.result_parser_version
                            and (
                                current_detail_snapshot.persistence_outcome
                                    = 'DEDUPLICATED'
                                or observation.source_received_at
                                    = current_detail_snapshot.received_at
                            )
                        )
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) detail_observation on true
            left join lateral (
                select occurrence.id as snapshot_occurrence_id,
                       snapshot.id,
                       occurrence.http_status,
                       occurrence.received_at,
                       occurrence.parser_version as occurrence_parser_version,
                       occurrence.persistence_outcome,
                       exists (
                           select 1
                           from j8_provider_call_attempt j8_attempt
                           join j8_benchmark_unit j8_unit
                             on j8_unit.id = j8_attempt.unit_id
                           where j8_unit.logical_endpoint = snapshot.logical_endpoint
                             and j8_unit.request_key = snapshot.request_key
                             and j8_attempt.created_at <= occurrence.created_at
                             and j8_attempt.created_at <= :asOf
                       ) as j8_attempt_present,
                       occurrence_result.unit_id as result_unit_id,
                       occurrence_result.outcome_type as result_outcome_type,
                       occurrence_result.schema_status as result_schema_status,
                       occurrence_result.parser_version as result_parser_version,
                       occurrence_result.completeness_status
                            as result_completeness_status,
                       occurrence_result.completeness_score
                            as result_completeness_score
                from provider_snapshot_occurrence occurrence
                join provider_snapshot snapshot
                  on snapshot.id = occurrence.snapshot_id
                left join j8_benchmark_unit_result occurrence_result
                  on occurrence_result.snapshot_occurrence_id = occurrence.id
                 and occurrence_result.created_at <= :asOf
                 and occurrence_result.resolved_at <= :asOf
                where snapshot.provider = 'SOFASCORE'
                  and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                  and snapshot.logical_endpoint = 'EVENT_STATISTICS'
                  and snapshot.request_key = 'EVENT_STATISTICS|eventId='
                        || unit.provider_event_id::text
                  and occurrence.created_at <= :asOf
                  and occurrence.requested_at <= :asOf
                  and (occurrence.received_at is null
                       or occurrence.received_at <= :asOf)
                  and snapshot.created_at <= :asOf
                order by occurrence.requested_at desc, occurrence.id desc
                limit 1
            ) current_statistics_snapshot on true
            left join lateral (
                select observation.id, observation.completeness_status
                from j5_event_data_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.endpoint_type = 'EVENT_STATISTICS'
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_snapshot_id = current_statistics_snapshot.id
                  and (
                        (
                            current_statistics_snapshot.result_unit_id is null
                            and not current_statistics_snapshot.j8_attempt_present
                            and observation.parser_version
                                = current_statistics_snapshot.occurrence_parser_version
                            and observation.source_received_at
                                = current_statistics_snapshot.received_at
                        )
                        or (
                            current_statistics_snapshot.result_outcome_type = 'PARSED'
                            and current_statistics_snapshot.result_schema_status = 'PARSED'
                            and current_statistics_snapshot.result_parser_version
                                = current_statistics_snapshot.occurrence_parser_version
                            and observation.parser_version
                                = current_statistics_snapshot.result_parser_version
                            and observation.completeness_status
                                = current_statistics_snapshot.result_completeness_status
                            and observation.completeness_score
                                = current_statistics_snapshot.result_completeness_score
                            and (
                                current_statistics_snapshot.persistence_outcome
                                    = 'DEDUPLICATED'
                                or observation.source_received_at
                                    = current_statistics_snapshot.received_at
                            )
                        )
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) current_statistics_observation on true
            left join lateral (
                select occurrence.id as snapshot_occurrence_id,
                       snapshot.id,
                       occurrence.http_status,
                       occurrence.received_at,
                       occurrence.parser_version as occurrence_parser_version,
                       occurrence.persistence_outcome,
                       exists (
                           select 1
                           from j8_provider_call_attempt j8_attempt
                           join j8_benchmark_unit j8_unit
                             on j8_unit.id = j8_attempt.unit_id
                           where j8_unit.logical_endpoint = snapshot.logical_endpoint
                             and j8_unit.request_key = snapshot.request_key
                             and j8_attempt.created_at <= occurrence.created_at
                             and j8_attempt.created_at <= :asOf
                       ) as j8_attempt_present,
                       occurrence_result.unit_id as result_unit_id,
                       occurrence_result.outcome_type as result_outcome_type,
                       occurrence_result.schema_status as result_schema_status,
                       occurrence_result.parser_version as result_parser_version,
                       occurrence_result.completeness_status
                            as result_completeness_status,
                       occurrence_result.completeness_score
                            as result_completeness_score
                from provider_snapshot_occurrence occurrence
                join provider_snapshot snapshot
                  on snapshot.id = occurrence.snapshot_id
                left join j8_benchmark_unit_result occurrence_result
                  on occurrence_result.snapshot_occurrence_id = occurrence.id
                 and occurrence_result.created_at <= :asOf
                 and occurrence_result.resolved_at <= :asOf
                where snapshot.provider = 'SOFASCORE'
                  and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                  and snapshot.logical_endpoint = 'EVENT_INCIDENTS'
                  and snapshot.request_key = 'EVENT_INCIDENTS|eventId='
                        || unit.provider_event_id::text
                  and occurrence.created_at <= :asOf
                  and occurrence.requested_at <= :asOf
                  and (occurrence.received_at is null
                       or occurrence.received_at <= :asOf)
                  and snapshot.created_at <= :asOf
                order by occurrence.requested_at desc, occurrence.id desc
                limit 1
            ) current_incidents_snapshot on true
            left join lateral (
                select observation.id, observation.completeness_status
                from j5_event_data_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.endpoint_type = 'EVENT_INCIDENTS'
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_snapshot_id = current_incidents_snapshot.id
                  and (
                        (
                            current_incidents_snapshot.result_unit_id is null
                            and not current_incidents_snapshot.j8_attempt_present
                            and observation.parser_version
                                = current_incidents_snapshot.occurrence_parser_version
                            and observation.source_received_at
                                = current_incidents_snapshot.received_at
                        )
                        or (
                            current_incidents_snapshot.result_outcome_type = 'PARSED'
                            and current_incidents_snapshot.result_schema_status = 'PARSED'
                            and current_incidents_snapshot.result_parser_version
                                = current_incidents_snapshot.occurrence_parser_version
                            and observation.parser_version
                                = current_incidents_snapshot.result_parser_version
                            and observation.completeness_status
                                = current_incidents_snapshot.result_completeness_status
                            and observation.completeness_score
                                = current_incidents_snapshot.result_completeness_score
                            and (
                                current_incidents_snapshot.persistence_outcome
                                    = 'DEDUPLICATED'
                                or observation.source_received_at
                                    = current_incidents_snapshot.received_at
                            )
                        )
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) current_incidents_observation on true
            left join lateral (
                select occurrence.id as snapshot_occurrence_id,
                       snapshot.id,
                       occurrence.http_status,
                       occurrence.received_at,
                       occurrence.parser_version as occurrence_parser_version,
                       occurrence.persistence_outcome,
                       exists (
                           select 1
                           from j8_provider_call_attempt j8_attempt
                           join j8_benchmark_unit j8_unit
                             on j8_unit.id = j8_attempt.unit_id
                           where j8_unit.logical_endpoint = snapshot.logical_endpoint
                             and j8_unit.request_key = snapshot.request_key
                             and j8_attempt.created_at <= occurrence.created_at
                             and j8_attempt.created_at <= :asOf
                       ) as j8_attempt_present,
                       occurrence_result.unit_id as result_unit_id,
                       occurrence_result.outcome_type as result_outcome_type,
                       occurrence_result.schema_status as result_schema_status,
                       occurrence_result.parser_version as result_parser_version,
                       occurrence_result.completeness_status
                            as result_completeness_status,
                       occurrence_result.completeness_score
                            as result_completeness_score
                from provider_snapshot_occurrence occurrence
                join provider_snapshot snapshot
                  on snapshot.id = occurrence.snapshot_id
                left join j8_benchmark_unit_result occurrence_result
                  on occurrence_result.snapshot_occurrence_id = occurrence.id
                 and occurrence_result.created_at <= :asOf
                 and occurrence_result.resolved_at <= :asOf
                where snapshot.provider = 'SOFASCORE'
                  and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                  and snapshot.logical_endpoint = 'EVENT_LINEUPS'
                  and snapshot.request_key = 'EVENT_LINEUPS|eventId='
                        || unit.provider_event_id::text
                  and occurrence.created_at <= :asOf
                  and occurrence.requested_at <= :asOf
                  and (occurrence.received_at is null
                       or occurrence.received_at <= :asOf)
                  and snapshot.created_at <= :asOf
                order by occurrence.requested_at desc, occurrence.id desc
                limit 1
            ) current_lineups_snapshot on true
            left join lateral (
                select observation.id, observation.completeness_status
                from j5_event_data_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        unit.canonical_event_id, target_event.id)
                  and observation.endpoint_type = 'EVENT_LINEUPS'
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_snapshot_id = current_lineups_snapshot.id
                  and (
                        (
                            current_lineups_snapshot.result_unit_id is null
                            and not current_lineups_snapshot.j8_attempt_present
                            and observation.parser_version
                                = current_lineups_snapshot.occurrence_parser_version
                            and observation.source_received_at
                                = current_lineups_snapshot.received_at
                        )
                        or (
                            current_lineups_snapshot.result_outcome_type = 'PARSED'
                            and current_lineups_snapshot.result_schema_status = 'PARSED'
                            and current_lineups_snapshot.result_parser_version
                                = current_lineups_snapshot.occurrence_parser_version
                            and observation.parser_version
                                = current_lineups_snapshot.result_parser_version
                            and observation.completeness_status
                                = current_lineups_snapshot.result_completeness_status
                            and observation.completeness_score
                                = current_lineups_snapshot.result_completeness_score
                            and (
                                current_lineups_snapshot.persistence_outcome
                                    = 'DEDUPLICATED'
                                or observation.source_received_at
                                    = current_lineups_snapshot.received_at
                            )
                        )
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) current_lineups_observation on true
            where campaign.created_at <= :asOf
              and unit.created_at <= :asOf
              and unit.declared_at <= :asOf
              and unit.provider_event_id is not null
              and (
                    :hasWindow = false
                    or (
                        unit.declared_at >= :fromInclusive
                        and unit.declared_at < :toExclusive
                    )
                    or attempt.id is not null
              )
            order by unit.provider_event_id, unit.logical_endpoint, unit.id
            """;

    private static final String HISTORICAL_COHORT_SQL = """
            select
                occurrence.id as occurrence_id,
                snapshot.id as snapshot_id,
                case when family_observation.correlation_count = 1
                    then family_observation.id
                    else null
                end as normalized_observation_id,
                state_observation.id as state_observation_id,
                detail_observation.id as detail_observation_id,
                occurrence.persistence_outcome,
                snapshot.logical_endpoint,
                occurrence.requested_at,
                case
                    when occurrence.http_status = 404
                        then 'UNAVAILABLE'
                    when family_observation.correlation_count = 1
                        then family_observation.completeness_status
                    else null
                end as completeness_status,
                case when family_observation.correlation_count = 1
                    then family_observation.present_signals else null
                end as present_signals,
                case when family_observation.correlation_count = 1
                    then family_observation.expected_signals else null
                end as expected_signals,
                family_observation.correlation_count = 1
                    as normalized_observation_present,
                state_observation.tournament_name as competition,
                detail_observation.season_name as season,
                state_observation.status_type as event_status
            from provider_snapshot_occurrence occurrence
            join provider_snapshot snapshot
              on snapshot.id = occurrence.snapshot_id
             and snapshot.provider = 'SOFASCORE'
             and snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
             and snapshot.logical_endpoint in (
                    'EVENT_STATISTICS', 'EVENT_INCIDENTS', 'EVENT_LINEUPS'
             )
            left join canonical_event event
              on event.provider = 'SOFASCORE'
             and event.provider_event_id = cast(
                    substring(snapshot.request_key from 'eventId=([0-9]+)$')
                    as bigint
             )
            left join lateral (
                select observation.id,
                       observation.canonical_event_id,
                       observation.completeness_status,
                       observation.present_signals,
                       observation.expected_signals,
                       count(*) over () as correlation_count
                from j5_event_data_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.source_snapshot_id = snapshot.id
                  and observation.endpoint_type = snapshot.logical_endpoint
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.source_received_at = occurrence.received_at
                  and observation.parser_version = occurrence.parser_version
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) family_observation on true
            left join lateral (
                select observation.id,
                       observation.tournament_name,
                       observation.status_type
                from canonical_event_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        family_observation.canonical_event_id, event.id)
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) state_observation on true
            left join lateral (
                select observation.id, observation.season_name
                from event_detail_observation observation
                join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                 and source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where observation.canonical_event_id = coalesce(
                        family_observation.canonical_event_id, event.id)
                  and observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                order by observation.source_received_at desc, observation.id desc
                limit 1
            ) detail_observation on true
            where snapshot.created_at <= :asOf
              and occurrence.created_at <= :asOf
              and occurrence.requested_at <= :asOf
              and occurrence.received_at is not null
              and occurrence.received_at <= :asOf
              and (:hasWindow = false or occurrence.requested_at >= :fromInclusive)
              and (:hasWindow = false or occurrence.requested_at < :toExclusive)
              and not exists (
                    select 1
                    from j8_benchmark_unit_result result
                    where result.snapshot_occurrence_id = occurrence.id
                      and result.created_at <= :asOf
              )
              and not exists (
                    select 1
                    from j8_provider_call_attempt j8_attempt
                    join j8_benchmark_unit j8_unit
                      on j8_unit.id = j8_attempt.unit_id
                    where j8_unit.logical_endpoint = snapshot.logical_endpoint
                      and j8_unit.request_key = snapshot.request_key
                      and j8_attempt.created_at <= occurrence.created_at
                      and j8_attempt.created_at <= :asOf
              )
            order by occurrence.requested_at, occurrence.id
            """;

    private static final String LATE_CHANGE_SQL = """
            with all_versions as (
                select
                    observation.canonical_event_id,
                    'EVENT_STATE' as history_stream,
                    observation.id as observation_id,
                    observation.source_snapshot_id,
                    source_snapshot.acquisition_mode,
                    observation.source_received_at,
                    observation.created_at
                from canonical_event_observation observation
                left join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                where observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                union all
                select
                    observation.canonical_event_id,
                    'EVENT_DETAILS' as history_stream,
                    observation.id as observation_id,
                    observation.source_snapshot_id,
                    source_snapshot.acquisition_mode,
                    observation.source_received_at,
                    observation.created_at
                from event_detail_observation observation
                left join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                where observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
                union all
                select
                    observation.canonical_event_id,
                    observation.endpoint_type as history_stream,
                    observation.id as observation_id,
                    observation.source_snapshot_id,
                    source_snapshot.acquisition_mode,
                    observation.source_received_at,
                    observation.created_at
                from j5_event_data_observation observation
                left join provider_snapshot source_snapshot
                  on source_snapshot.id = observation.source_snapshot_id
                where observation.endpoint_type in (
                        'EVENT_STATISTICS', 'EVENT_INCIDENTS', 'EVENT_LINEUPS'
                  )
                  and observation.created_at <= :asOf
                  and observation.source_received_at <= :asOf
            ), direct_versions as (
                select
                    version.*,
                    lag(version.observation_id) over version_order
                        as previous_observation_id
                from all_versions version
                where version.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                window version_order as (
                    partition by version.canonical_event_id, version.history_stream
                    order by version.source_received_at, version.observation_id
                )
            ), paired_direct_versions as (
                select *
                from direct_versions version
                where version.previous_observation_id is not null
            )
            select
                version.canonical_event_id,
                version.history_stream,
                version.observation_id,
                version.previous_observation_id,
                version.source_snapshot_id,
                version.source_received_at,
                previous_state.observation_id
                    as previous_direct_state_observation_id,
                previous_state.status_type as previous_direct_state_status,
                previous_state.source_received_at as previous_direct_state_at
            from paired_direct_versions version
            left join lateral (
                select
                    state_observation.id as observation_id,
                    state_observation.source_received_at,
                    state_observation.status_type
                from canonical_event_observation state_observation
                join provider_snapshot state_snapshot
                  on state_snapshot.id = state_observation.source_snapshot_id
                 and state_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'
                where state_observation.canonical_event_id = version.canonical_event_id
                  and state_observation.source_kind = 'PROVIDER_SNAPSHOT'
                  and state_observation.created_at <= :asOf
                  and state_observation.source_received_at <= :asOf
                  and (
                        state_observation.source_received_at
                                < version.source_received_at
                        or (
                            version.history_stream = 'EVENT_STATE'
                            and state_observation.source_received_at
                                    = version.source_received_at
                            and state_observation.id < version.observation_id
                        )
                  )
                order by state_observation.source_received_at desc,
                         state_observation.id desc
                limit 1
            ) previous_state on true
            where (:hasWindow = false or version.source_received_at >= :fromInclusive)
              and (:hasWindow = false or version.source_received_at < :toExclusive)
            order by version.source_received_at,
                     version.canonical_event_id,
                     version.history_stream,
                     version.observation_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ8BenchmarkReadStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(readOnly = true)
    public J8BenchmarkReadEvidence readEvidence(J8BenchmarkWindow window, Instant asOf) {
        MapSqlParameterSource parameters = parameters(window, asOf);
        List<J8BenchmarkReadEvidence.CampaignEvidence> campaigns = jdbcTemplate.query(
                CAMPAIGN_SQL, parameters, this::campaign);
        List<J8BenchmarkReadEvidence.UnitEvidence> units = jdbcTemplate.query(
                UNIT_SQL, parameters, this::unit);
        List<J8BenchmarkReadEvidence.LegacyResponseEvidence> legacy = jdbcTemplate.query(
                LEGACY_RESPONSE_SQL, parameters, this::legacyResponse);
        Long manualImportCount = jdbcTemplate.queryForObject(
                MANUAL_IMPORT_OCCURRENCE_COUNT_SQL, parameters, Long.class);
        Long syntheticCount = jdbcTemplate.queryForObject(
                SYNTHETIC_OBSERVATION_COUNT_SQL, parameters, Long.class);
        if (manualImportCount == null || manualImportCount < 0
                || syntheticCount == null || syntheticCount < 0) {
            throw new IllegalStateException("excluded evidence count is unavailable");
        }
        return new J8BenchmarkReadEvidence(
                campaigns, units, legacy, manualImportCount, syntheticCount);
    }

    @Override
    @Transactional(readOnly = true)
    public J8DirectObservationCohorts readDirectObservationCohorts(
            J8BenchmarkWindow window,
            Instant asOf) {
        MapSqlParameterSource parameters = parameters(window, asOf);
        return new J8DirectObservationCohorts(
                jdbcTemplate.query(COHORT_SQL, parameters, this::cohort),
                jdbcTemplate.query(
                        HISTORICAL_COHORT_SQL, parameters, this::historicalCohort));
    }

    @Override
    @Transactional(readOnly = true)
    public List<J8LateChangeEvidence> readLateChanges(
            J8BenchmarkWindow window,
            Instant asOf) {
        return List.copyOf(jdbcTemplate.query(
                LATE_CHANGE_SQL, parameters(window, asOf), this::lateChange));
    }

    private J8BenchmarkReadEvidence.CampaignEvidence campaign(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new J8BenchmarkReadEvidence.CampaignEvidence(
                resultSet.getObject("campaign_id", UUID.class),
                enumValue(resultSet, "campaign_type", J8BenchmarkCampaignType.class),
                enumValue(resultSet, "execution_mode", J8BenchmarkExecutionMode.class),
                instant(resultSet, "started_at"),
                resultSet.getInt("maximum_units"),
                optionalInstant(resultSet, "finished_at"),
                optionalEnum(resultSet, "terminal_state",
                        J8BenchmarkCampaignTerminalState.class),
                optionalInt(resultSet, "completed_units"));
    }

    private J8BenchmarkReadEvidence.UnitEvidence unit(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new J8BenchmarkReadEvidence.UnitEvidence(
                resultSet.getObject("campaign_id", UUID.class),
                enumValue(resultSet, "campaign_type", J8BenchmarkCampaignType.class),
                enumValue(resultSet, "execution_mode", J8BenchmarkExecutionMode.class),
                resultSet.getLong("unit_id"),
                resultSet.getInt("unit_ordinal"),
                enumValue(resultSet, "logical_endpoint", SofascoreEndpointType.class),
                Optional.ofNullable(resultSet.getObject("canonical_event_id", UUID.class)),
                optionalLong(resultSet, "provider_event_id"),
                instant(resultSet, "declared_at"),
                optionalLong(resultSet, "attempt_id"),
                optionalInstant(resultSet, "attempt_started_at"),
                optionalInstant(resultSet, "resolved_at"),
                optionalEnum(resultSet, "resolution_source",
                        J8BenchmarkResolutionSource.class),
                optionalEnum(resultSet, "outcome_type", J8BenchmarkOutcomeType.class),
                resultSet.getBoolean("response_received"),
                optionalInt(resultSet, "http_status"),
                optionalLong(resultSet, "latency_ms"),
                optionalLong(resultSet, "snapshot_id"),
                optionalLong(resultSet, "snapshot_occurrence_id"),
                resultSet.getBoolean("deduplicated_response"),
                optionalString(resultSet, "parser_version"),
                optionalEnum(resultSet, "schema_status", RawSnapshotSchemaStatus.class),
                resultSet.getInt("parser_warning_count"),
                optionalEnum(resultSet, "completeness_status", J5CompletenessStatus.class),
                optionalInt(resultSet, "completeness_score"));
    }

    private J8BenchmarkReadEvidence.LegacyResponseEvidence legacyResponse(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new J8BenchmarkReadEvidence.LegacyResponseEvidence(
                resultSet.getLong("occurrence_id"),
                resultSet.getLong("snapshot_id"),
                longList(resultSet, "normalized_observation_ids"),
                enumValue(resultSet, "logical_endpoint", SofascoreEndpointType.class),
                instant(resultSet, "requested_at"),
                optionalInstant(resultSet, "received_at"),
                optionalInt(resultSet, "http_status"),
                optionalLong(resultSet, "latency_ms"),
                optionalString(resultSet, "parser_version"),
                optionalEnum(resultSet, "schema_status", RawSnapshotSchemaStatus.class),
                enumValue(resultSet, "persistence_outcome",
                        J6SnapshotOccurrenceOutcome.class));
    }

    private J8DirectObservationCohorts.ObservationCohort cohort(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new J8DirectObservationCohorts.ObservationCohort(
                resultSet.getLong("unit_id"),
                enumValue(resultSet, "campaign_type", J8BenchmarkCampaignType.class),
                resultSet.getLong("provider_event_id"),
                Optional.ofNullable(resultSet.getObject("canonical_event_id", UUID.class)),
                enumValue(resultSet, "logical_endpoint", SofascoreEndpointType.class),
                resultSet.getBoolean("provider_attempted"),
                resultSet.getBoolean("declared_in_window"),
                optionalEnum(resultSet, "outcome_type", J8BenchmarkOutcomeType.class),
                optionalEnum(resultSet, "schema_status", RawSnapshotSchemaStatus.class),
                optionalString(resultSet, "parser_version"),
                optionalEnum(resultSet, "completeness_status", J5CompletenessStatus.class),
                optionalInt(resultSet, "completeness_score"),
                optionalInt(resultSet, "present_signals"),
                optionalInt(resultSet, "expected_signals"),
                resultSet.getBoolean("normalized_observation_present"),
                resultSet.getBoolean("canonical_state_present"),
                enumValue(resultSet, "current_canonical_state",
                        J8DirectObservationCohorts.DirectComponentState.class),
                enumValue(resultSet, "current_event_details",
                        J8DirectObservationCohorts.DirectComponentState.class),
                enumValue(resultSet, "current_statistics",
                        J8DirectObservationCohorts.DirectComponentState.class),
                enumValue(resultSet, "current_incidents",
                        J8DirectObservationCohorts.DirectComponentState.class),
                enumValue(resultSet, "current_lineups",
                        J8DirectObservationCohorts.DirectComponentState.class),
                optionalEnum(resultSet, "current_statistics_completeness",
                        J5CompletenessStatus.class),
                optionalEnum(resultSet, "current_incidents_completeness",
                        J5CompletenessStatus.class),
                optionalEnum(resultSet, "current_lineups_completeness",
                        J5CompletenessStatus.class),
                new J8DirectObservationCohorts.CurrentDirectEvidence(
                        optionalLong(resultSet, "correlated_observation_id"),
                        optionalLong(resultSet, "current_canonical_observation_id"),
                        componentEvidence(resultSet, "current_detail"),
                        componentEvidence(resultSet, "current_statistics"),
                        componentEvidence(resultSet, "current_incidents"),
                        componentEvidence(resultSet, "current_lineups")),
                optionalString(resultSet, "competition"),
                optionalString(resultSet, "season"),
                optionalString(resultSet, "event_status"));
    }

    private J8DirectObservationCohorts.HistoricalObservationCohort historicalCohort(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new J8DirectObservationCohorts.HistoricalObservationCohort(
                resultSet.getLong("occurrence_id"),
                resultSet.getLong("snapshot_id"),
                optionalLong(resultSet, "normalized_observation_id"),
                optionalLong(resultSet, "state_observation_id"),
                optionalLong(resultSet, "detail_observation_id"),
                enumValue(resultSet, "persistence_outcome",
                        J6SnapshotOccurrenceOutcome.class),
                enumValue(resultSet, "logical_endpoint", SofascoreEndpointType.class),
                instant(resultSet, "requested_at"),
                optionalEnum(resultSet, "completeness_status",
                        J5CompletenessStatus.class),
                optionalInt(resultSet, "present_signals"),
                optionalInt(resultSet, "expected_signals"),
                resultSet.getBoolean("normalized_observation_present"),
                optionalString(resultSet, "competition"),
                optionalString(resultSet, "season"),
                optionalString(resultSet, "event_status"));
    }

    private J8DirectObservationCohorts.ComponentEvidence componentEvidence(
            ResultSet resultSet,
            String prefix) throws SQLException {
        return new J8DirectObservationCohorts.ComponentEvidence(
                optionalLong(resultSet, prefix + "_snapshot_occurrence_id"),
                optionalLong(resultSet, prefix + "_snapshot_id"),
                optionalLong(resultSet, prefix + "_observation_id"));
    }

    private J8LateChangeEvidence lateChange(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new J8LateChangeEvidence(
                resultSet.getObject("canonical_event_id", UUID.class),
                enumValue(resultSet, "history_stream", J6HistoryStream.class),
                resultSet.getLong("observation_id"),
                resultSet.getLong("previous_observation_id"),
                resultSet.getLong("source_snapshot_id"),
                instant(resultSet, "source_received_at"),
                optionalLong(resultSet, "previous_direct_state_observation_id"),
                optionalString(resultSet, "previous_direct_state_status"),
                optionalInstant(resultSet, "previous_direct_state_at"));
    }

    private static MapSqlParameterSource parameters(
            J8BenchmarkWindow window,
            Instant asOf) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(asOf, "asOf");
        return new MapSqlParameterSource()
                .addValue("hasWindow", window.explicit(), Types.BOOLEAN)
                .addValue("fromInclusive", utc(window.fromInclusive().orElse(asOf)),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("toExclusive", utc(window.toExclusive().orElse(asOf)),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("asOf", utc(asOf), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        if (value == null) {
            throw new IllegalStateException(column + " cannot be null");
        }
        return value.toInstant();
    }

    private static Optional<Instant> optionalInstant(
            ResultSet resultSet,
            String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static OptionalLong optionalLong(ResultSet resultSet, String column)
            throws SQLException {
        Long value = resultSet.getObject(column, Long.class);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static List<Long> longList(ResultSet resultSet, String column)
            throws SQLException {
        Array sqlArray = resultSet.getArray(column);
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object raw = sqlArray.getArray();
            if (!(raw instanceof Object[] values)) {
                throw new IllegalStateException(column + " is not a SQL array");
            }
            return java.util.Arrays.stream(values)
                    .map(value -> {
                        if (!(value instanceof Number number)) {
                            throw new IllegalStateException(
                                    column + " contains a non-numeric identifier");
                        }
                        return number.longValue();
                    })
                    .toList();
        }
        finally {
            sqlArray.free();
        }
    }

    private static OptionalInt optionalInt(ResultSet resultSet, String column)
            throws SQLException {
        Integer value = resultSet.getObject(column, Integer.class);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static Optional<String> optionalString(ResultSet resultSet, String column)
            throws SQLException {
        return Optional.ofNullable(resultSet.getString(column));
    }

    private static <E extends Enum<E>> E enumValue(
            ResultSet resultSet,
            String column,
            Class<E> type) throws SQLException {
        String value = resultSet.getString(column);
        if (value == null) {
            throw new IllegalStateException(column + " cannot be null");
        }
        return Enum.valueOf(type, value);
    }

    private static <E extends Enum<E>> Optional<E> optionalEnum(
            ResultSet resultSet,
            String column,
            Class<E> type) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? Optional.empty() : Optional.of(Enum.valueOf(type, value));
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
