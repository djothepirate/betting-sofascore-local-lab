package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaign;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnit;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8ProviderCallAttempt;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@Repository
public class JdbcJ8BenchmarkEvidenceStore implements J8BenchmarkEvidenceStore {

    private static final String INSERT_CAMPAIGN_SQL = """
            insert into j8_benchmark_campaign (
                campaign_id,
                campaign_type,
                execution_mode,
                started_at,
                maximum_units,
                collection_date
            ) values (
                :campaignId,
                :campaignType,
                :executionMode,
                :startedAt,
                :maximumUnits,
                :collectionDate
            )
            """;

    private static final String INSERT_UNIT_SQL = """
            insert into j8_benchmark_unit (
                campaign_id,
                unit_ordinal,
                logical_endpoint,
                request_key,
                canonical_event_id,
                provider_event_id,
                declared_at
            ) values (
                :campaignId,
                :unitOrdinal,
                :logicalEndpoint,
                :requestKey,
                :canonicalEventId,
                :providerEventId,
                :declaredAt
            )
            returning id
            """;

    private static final String INSERT_ATTEMPT_SQL = """
            insert into j8_provider_call_attempt (
                unit_id,
                started_at
            ) values (
                :unitId,
                :startedAt
            )
            returning id
            """;

    private static final String INSERT_UNIT_RESULT_SQL = """
            insert into j8_benchmark_unit_result (
                unit_id,
                attempt_id,
                resolved_at,
                resolution_source,
                outcome_type,
                response_received,
                http_status,
                latency_ms,
                snapshot_id,
                snapshot_occurrence_id,
                parser_version,
                schema_status,
                parser_warning_count,
                completeness_status,
                completeness_score,
                terminal_code
            ) values (
                :unitId,
                :attemptId,
                :resolvedAt,
                :resolutionSource,
                :outcomeType,
                :responseReceived,
                :httpStatus,
                :latencyMs,
                :snapshotId,
                :snapshotOccurrenceId,
                :parserVersion,
                :schemaStatus,
                :parserWarningCount,
                :completenessStatus,
                :completenessScore,
                :terminalCode
            )
            """;

    private static final String INSERT_CAMPAIGN_RESULT_SQL = """
            insert into j8_benchmark_campaign_result (
                campaign_id,
                finished_at,
                terminal_state,
                terminal_code,
                completed_units
            ) values (
                :campaignId,
                :finishedAt,
                :terminalState,
                :terminalCode,
                :completedUnits
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJ8BenchmarkEvidenceStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCampaign(J8BenchmarkCampaign campaign) {
        Objects.requireNonNull(campaign, "campaign");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("campaignId", campaign.campaignId())
                .addValue("campaignType", campaign.campaignType().name())
                .addValue("executionMode", campaign.executionMode().name())
                .addValue("startedAt", utc(campaign.startedAt()))
                .addValue("maximumUnits", campaign.maximumUnits())
                .addValue(
                        "collectionDate",
                        campaign.collectionDate().orElse(null),
                        Types.DATE);
        requireOne(jdbcTemplate.update(INSERT_CAMPAIGN_SQL, parameters), "campaign insert");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long declareUnit(J8BenchmarkUnit unit) {
        Objects.requireNonNull(unit, "unit");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("campaignId", unit.campaignId())
                .addValue("unitOrdinal", unit.unitOrdinal())
                .addValue("logicalEndpoint", unit.endpointType().name())
                .addValue("requestKey", unit.requestKey())
                .addValue(
                        "canonicalEventId",
                        unit.canonicalEventId().orElse(null),
                        Types.OTHER)
                .addValue(
                        "providerEventId",
                        optionalLong(unit.providerEventId()),
                        Types.BIGINT)
                .addValue("declaredAt", utc(unit.declaredAt()));
        return requireIdentifier(
                jdbcTemplate.queryForObject(INSERT_UNIT_SQL, parameters, Long.class),
                "unit insert");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long startProviderAttempt(J8ProviderCallAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("unitId", attempt.unitId())
                .addValue("startedAt", utc(attempt.startedAt()));
        return requireIdentifier(
                jdbcTemplate.queryForObject(INSERT_ATTEMPT_SQL, parameters, Long.class),
                "provider attempt insert");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnitResult(J8BenchmarkUnitResult result) {
        Objects.requireNonNull(result, "result");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("unitId", result.unitId())
                .addValue("attemptId", optionalLong(result.attemptId()), Types.BIGINT)
                .addValue("resolvedAt", utc(result.resolvedAt()))
                .addValue("resolutionSource", result.resolutionSource().name())
                .addValue("outcomeType", result.outcomeType().name())
                .addValue("responseReceived", result.responseReceived())
                .addValue("httpStatus", optionalInt(result.httpStatus()), Types.INTEGER)
                .addValue("latencyMs", optionalLong(result.latencyMillis()), Types.BIGINT)
                .addValue("snapshotId", optionalLong(result.snapshotId()), Types.BIGINT)
                .addValue(
                        "snapshotOccurrenceId",
                        optionalLong(result.snapshotOccurrenceId()),
                        Types.BIGINT)
                .addValue("parserVersion", optional(result.parserVersion()), Types.VARCHAR)
                .addValue(
                        "schemaStatus",
                        result.schemaStatus().map(Enum::name).orElse(null),
                        Types.VARCHAR)
                .addValue("parserWarningCount", result.parserWarningCount())
                .addValue(
                        "completenessStatus",
                        result.completenessStatus().map(Enum::name).orElse(null),
                        Types.VARCHAR)
                .addValue(
                        "completenessScore",
                        optionalInt(result.completenessScore()),
                        Types.SMALLINT)
                .addValue("terminalCode", optional(result.terminalCode()), Types.VARCHAR);
        requireOne(jdbcTemplate.update(INSERT_UNIT_RESULT_SQL, parameters), "unit result insert");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishCampaign(J8BenchmarkCampaignResult result) {
        Objects.requireNonNull(result, "result");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("campaignId", result.campaignId())
                .addValue("finishedAt", utc(result.finishedAt()))
                .addValue("terminalState", result.terminalState().name())
                .addValue("terminalCode", optional(result.terminalCode()), Types.VARCHAR)
                .addValue("completedUnits", result.completedUnits());
        requireOne(
                jdbcTemplate.update(INSERT_CAMPAIGN_RESULT_SQL, parameters),
                "campaign result insert");
    }

    private static OffsetDateTime utc(java.time.Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Long optionalLong(OptionalLong value) {
        return value.isPresent() ? value.getAsLong() : null;
    }

    private static Integer optionalInt(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static String optional(Optional<String> value) {
        return value.orElse(null);
    }

    private static long requireIdentifier(Long identifier, String operation) {
        if (identifier == null || identifier < 1) {
            throw new IllegalStateException(operation + " did not return a positive identifier");
        }
        return identifier;
    }

    private static void requireOne(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    operation + " affected an unexpected number of rows: " + affectedRows);
        }
    }
}
