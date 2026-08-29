package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcJ8BenchmarkReadStoreTest {

    private static final Instant AS_OF = Instant.parse("2026-08-29T12:00:00Z");

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcJ8BenchmarkReadStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcJ8BenchmarkReadStore(jdbcTemplate);
        doReturn(List.of()).when(jdbcTemplate).query(
                anyString(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
    }

    @Test
    void readsOnlyDirectLegacyResponsesAndCountsManualImportsSeparately() {
        when(jdbcTemplate.queryForObject(
                anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(3L, 4L);

        var evidence = store.readEvidence(J8BenchmarkWindow.allAvailable(), AS_OF);

        ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).query(
                querySql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String legacySql = querySql.getAllValues().stream()
                .filter(sql -> sql.contains("occurrence.persistence_outcome"))
                .filter(sql -> sql.contains("normalized_observation.ids"))
                .findFirst()
                .orElseThrow();
        assertThat(legacySql)
                .contains("snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'")
                .contains("cardinality(normalized_observation.ids) > 0 then 'PARSED'")
                .contains("array_agg(proof.id order by proof.id) as ids")
                .contains("observation.created_at <= :asOf")
                .contains("observation.source_received_at <= :asOf")
                .contains("observation.source_received_at = occurrence.received_at")
                .contains("observation.parser_version = occurrence.parser_version")
                .contains("occurrence.received_at is not null")
                .contains("occurrence.received_at <= :asOf")
                .contains("from j8_provider_call_attempt j8_attempt")
                .contains("j8_unit.logical_endpoint = snapshot.logical_endpoint")
                .contains("j8_unit.request_key = snapshot.request_key")
                .contains("j8_attempt.created_at <= occurrence.created_at")
                .doesNotContain("snapshot.schema_status")
                .contains("not exists");
        String campaignSql = querySql.getAllValues().stream()
                .filter(sql -> sql.contains("from j8_benchmark_campaign campaign"))
                .findFirst()
                .orElseThrow();
        assertThat(campaignSql)
                .contains("result.finished_at <= :asOf")
                .contains("exists (")
                .contains("selected_unit.campaign_id = campaign.campaign_id")
                .contains("selected_unit.declared_at >= :fromInclusive")
                .contains("selected_unit.declared_at < :toExclusive")
                .contains("selected_attempt.started_at >= :fromInclusive")
                .contains("selected_attempt.started_at < :toExclusive");
        String unitSql = querySql.getAllValues().stream()
                .filter(sql -> sql.contains("from j8_benchmark_unit unit"))
                .filter(sql -> sql.contains("occurrence.persistence_outcome"))
                .findFirst()
                .orElseThrow();
        assertThat(unitSql)
                .contains("attempt.started_at >= :fromInclusive")
                .contains("attempt.started_at < :toExclusive")
                .contains("or attempt.id is not null");

        ArgumentCaptor<String> scalarSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(
                scalarSql.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertThat(scalarSql.getAllValues())
                .anySatisfy(sql -> assertThat(sql)
                        .contains("snapshot.acquisition_mode = 'MANUAL_LOCAL_JSON_IMPORT'")
                        .contains("occurrence.received_at is not null")
                        .contains("occurrence.received_at <= :asOf"));
        assertThat(evidence.manualImportOccurrenceCount()).isEqualTo(3);
        assertThat(evidence.excludedSyntheticObservationCount()).isEqualTo(4);
    }

    @Test
    void keepsTheSameLegacyAsOfProjectionBeforeAndAfterMutableSnapshotClassification() {
        when(jdbcTemplate.queryForObject(
                anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        store.readEvidence(J8BenchmarkWindow.allAvailable(), AS_OF);

        ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).query(
                querySql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String legacySql = querySql.getAllValues().stream()
                .filter(sql -> sql.contains("occurrence.persistence_outcome"))
                .filter(sql -> sql.contains("normalized_observation"))
                .findFirst()
                .orElseThrow();

        assertThat(legacySql)
                .contains("snapshot.id as snapshot_id")
                .contains("normalized_observation.ids as normalized_observation_ids")
                .contains("array_agg(proof.id order by proof.id) as ids")
                .contains("observation.created_at <= :asOf")
                .contains("observation.source_received_at <= :asOf")
                .contains("occurrence.parser_version")
                .doesNotContain("snapshot.schema_status", "snapshot.parser_version");
    }

    @Test
    void correlatesCompletenessSignalsAndDimensionsOnlyToDirectSnapshots() {
        store.readDirectObservationCohorts(J8BenchmarkWindow.allAvailable(), AS_OF);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(
                sql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String prospectiveSql = sql.getAllValues().stream()
                .filter(value -> value.contains("from j8_benchmark_unit unit"))
                .findFirst()
                .orElseThrow();
        assertThat(prospectiveSql)
                .contains("family_observation.present_signals")
                .contains("family_observation.expected_signals")
                .contains("source_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'")
                .contains("observation.source_snapshot_id = current_detail_snapshot.id")
                .contains("current_canonical_state")
                .contains("current_event_details")
                .contains("current_statistics")
                .contains("current_incidents")
                .contains("current_lineups")
                .contains("result_detail_observation.id")
                .contains("observation.source_snapshot_id = result.snapshot_id")
                .contains("result_occurrence.id = result.snapshot_occurrence_id")
                .contains("result_occurrence.snapshot_id = result.snapshot_id")
                .contains("observation.source_received_at = result_occurrence.received_at")
                .contains("result_occurrence.persistence_outcome = 'DEDUPLICATED'")
                .contains("count(*) over () as correlation_count")
                .contains("observation.parser_version = result.parser_version")
                .contains("observation.completeness_status = result.completeness_status")
                .contains("observation.completeness_score = result.completeness_score")
                .contains("occurrence_result.snapshot_occurrence_id = occurrence.id")
                .contains("occurrence_result.created_at <= :asOf")
                .contains("occurrence_result.resolved_at <= :asOf")
                .contains("result_outcome_type = 'PARSED'")
                .contains("result_schema_status = 'PARSED'")
                .contains("result_parser_version")
                .contains("occurrence_parser_version")
                .contains("result_completeness_status")
                .contains("result_completeness_score")
                .contains("current_detail_snapshot.persistence_outcome")
                .contains("current_statistics_snapshot.persistence_outcome")
                .contains("current_incidents_snapshot.persistence_outcome")
                .contains("current_lineups_snapshot.persistence_outcome")
                .contains("not current_detail_snapshot.j8_attempt_present")
                .contains("not current_statistics_snapshot.j8_attempt_present")
                .contains("not current_incidents_snapshot.j8_attempt_present")
                .contains("not current_lineups_snapshot.j8_attempt_present")
                .contains("observation.created_at <= :asOf")
                .contains("observation.source_received_at <= :asOf")
                .contains("snapshot.request_key = 'EVENT_DETAILS|eventId='")
                .contains("snapshot.request_key = 'EVENT_STATISTICS|eventId='")
                .contains("snapshot.request_key = 'EVENT_INCIDENTS|eventId='")
                .contains("snapshot.request_key = 'EVENT_LINEUPS|eventId='")
                .contains("attempt.started_at >= :fromInclusive")
                .contains("attempt.started_at < :toExclusive");
        String historicalSql = sql.getAllValues().stream()
                .filter(value -> value.contains(
                        "occurrence.id as occurrence_id"))
                .findFirst()
                .orElseThrow();
        assertThat(historicalSql)
                .contains("snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'")
                .contains("occurrence.persistence_outcome")
                .contains("family_observation.completeness_status")
                .contains("state_observation.id as state_observation_id")
                .contains("detail_observation.id as detail_observation_id")
                .contains("occurrence.http_status = 404")
                .contains("observation.source_received_at = occurrence.received_at")
                .contains("observation.parser_version = occurrence.parser_version")
                .contains("occurrence.received_at is not null")
                .contains("occurrence.received_at <= :asOf")
                .contains("from j8_provider_call_attempt j8_attempt")
                .contains("j8_unit.logical_endpoint = snapshot.logical_endpoint")
                .contains("j8_unit.request_key = snapshot.request_key")
                .contains("j8_attempt.created_at <= occurrence.created_at")
                .doesNotContain("snapshot.schema_status")
                .contains("not exists");
    }

    @Test
    void selectsTheLatestOccurrenceForAnAtoBtoADeduplicatedCurrentValue() {
        store.readDirectObservationCohorts(J8BenchmarkWindow.allAvailable(), AS_OF);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(
                sql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String prospectiveSql = sql.getAllValues().stream()
                .filter(value -> value.contains("from j8_benchmark_unit unit"))
                .findFirst()
                .orElseThrow();

        assertThat(prospectiveSql)
                .contains("from provider_snapshot_occurrence occurrence")
                .contains("join provider_snapshot snapshot")
                .contains("order by occurrence.requested_at desc, occurrence.id desc")
                .contains("current_detail_snapshot.snapshot_occurrence_id")
                .contains("current_statistics_snapshot.snapshot_occurrence_id")
                .contains("current_incidents_snapshot.snapshot_occurrence_id")
                .contains("current_lineups_snapshot.snapshot_occurrence_id")
                .containsIgnoringWhitespaces("observation.source_received_at"
                        + " = current_detail_snapshot.received_at")
                .containsIgnoringWhitespaces("observation.source_received_at"
                        + " = current_statistics_snapshot.received_at")
                .containsIgnoringWhitespaces("observation.source_received_at"
                        + " = current_incidents_snapshot.received_at")
                .containsIgnoringWhitespaces("observation.source_received_at"
                        + " = current_lineups_snapshot.received_at")
                .doesNotContain("order by snapshot.requested_at desc");
    }

    @Test
    void resolvesAJ4Phase1TargetThroughItsProviderEventIdentity() {
        store.readDirectObservationCohorts(J8BenchmarkWindow.allAvailable(), AS_OF);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(
                sql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String prospectiveSql = sql.getAllValues().stream()
                .filter(value -> value.contains("from j8_benchmark_unit unit"))
                .findFirst()
                .orElseThrow();

        assertThat(prospectiveSql)
                .contains("'J4_EVENT_DETAILS_PHASE1'")
                .contains("left join canonical_event target_event")
                .contains("target_event.provider = 'SOFASCORE'")
                .contains("target_event.provider_event_id = unit.provider_event_id")
                .contains("coalesce(unit.canonical_event_id, target_event.id)");
    }

    @Test
    void selectsTheDirectSubseriesAndCarriesTheLatestPreviousDirectState() {
        store.readLateChanges(J8BenchmarkWindow.allAvailable(), AS_OF);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                any(SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        assertThat(sql.getValue())
                .contains("from canonical_event_observation observation")
                .contains("from event_detail_observation observation")
                .contains("from j5_event_data_observation observation")
                .contains("'EVENT_STATE' as history_stream")
                .contains("'EVENT_DETAILS' as history_stream")
                .contains("'EVENT_STATISTICS', 'EVENT_INCIDENTS', 'EVENT_LINEUPS'")
                .contains("lag(version.observation_id) over version_order")
                .contains("version.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'")
                .contains("from paired_direct_versions version")
                .contains("state_snapshot.acquisition_mode = 'DIRECT_LOCAL_ENDPOINT'")
                .contains("state_observation.source_received_at")
                .contains("< version.source_received_at")
                .contains("observation.created_at <= :asOf")
                .containsIgnoringWhitespaces(
                        "previous_state.observation_id"
                                + " as previous_direct_state_observation_id",
                        "previous_state.status_type"
                                + " as previous_direct_state_status",
                        "previous_state.source_received_at"
                                + " as previous_direct_state_at")
                .doesNotContain(
                        "lag(version.acquisition_mode)",
                        "direct_predecessor",
                        "finished', 'canceled",
                        "additions_only",
                        "source_kind = 'SYNTHETIC_FIXTURE'");
    }
}
