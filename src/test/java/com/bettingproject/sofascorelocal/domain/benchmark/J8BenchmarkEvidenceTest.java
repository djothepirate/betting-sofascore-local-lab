package com.bettingproject.sofascorelocal.domain.benchmark;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J8BenchmarkEvidenceTest {

    @Test
    void keepsCampaignBoundsAndJ3DatesExplicit() {
        UUID campaignId = UUID.randomUUID();
        var campaign = new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT,
                Instant.parse("2026-08-29T08:00:00Z"),
                1,
                Optional.of(LocalDate.of(2026, 8, 29)));

        assertThat(campaign.maximumUnits()).isOne();
        assertThatThrownBy(() -> new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                campaign.startedAt(),
                2,
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounded campaign type");
        assertThatThrownBy(() -> new J8BenchmarkCampaign(
                campaignId,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                campaign.startedAt(),
                1,
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("J3 campaigns require");
    }

    @Test
    void requiresEventCorrelationOnlyForEventEndpoints() {
        UUID campaignId = UUID.randomUUID();
        var unit = new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=27000001",
                Optional.empty(),
                OptionalLong.of(27000001L),
                Instant.parse("2026-08-29T08:00:01Z"));

        assertThat(unit.providerEventId()).hasValue(27000001L);
        assertThatThrownBy(() -> new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2026-08-29|page=1",
                Optional.of(UUID.randomUUID()),
                OptionalLong.of(27000001L),
                unit.declaredAt()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot identify one event");
    }

    @Test
    void requiresCanonicalBoundedRequestKeysForEveryEndpointShape() {
        UUID campaignId = UUID.randomUUID();
        Instant declaredAt = Instant.parse("2026-08-29T08:00:01Z");

        assertThatThrownBy(() -> new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "event:27000001:details",
                Optional.empty(),
                OptionalLong.of(27_000_001L),
                declaredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact canonical endpoint key");
        assertThatThrownBy(() -> new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=1000000000",
                Optional.empty(),
                OptionalLong.of(1_000_000_000L),
                declaredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 999999999");
        assertThatThrownBy(() -> new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2026-08-29|page=2",
                Optional.empty(),
                OptionalLong.empty(),
                declaredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must equal unitOrdinal");
        assertThatThrownBy(() -> new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-29|uniqueTournamentId=abc",
                Optional.empty(),
                OptionalLong.empty(),
                declaredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact tournament request key");
        assertThatThrownBy(() -> new J8BenchmarkUnit(
                campaignId,
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-29"
                        + "|uniqueTournamentId=9223372036854775808",
                Optional.empty(),
                OptionalLong.empty(),
                declaredAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive numeric long");
    }

    @Test
    void distinguishesProviderResponsesFromCacheAndBlockedUnits() {
        var parsed = providerParsed();

        assertThat(parsed.responseReceived()).isTrue();
        assertThat(parsed.snapshotOccurrenceId()).hasValue(41L);
        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                parsed.unitId(),
                parsed.attemptId(),
                parsed.resolvedAt(),
                parsed.resolutionSource(),
                parsed.outcomeType(),
                parsed.responseReceived(),
                parsed.httpStatus(),
                parsed.latencyMillis(),
                parsed.snapshotId(),
                OptionalLong.empty(),
                parsed.parserVersion(),
                parsed.schemaStatus(),
                parsed.parserWarningCount(),
                parsed.completenessStatus(),
                parsed.completenessScore(),
                parsed.terminalCode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact occurrence");

        var blocked = new J8BenchmarkUnitResult(
                2L,
                OptionalLong.empty(),
                Instant.parse("2026-08-29T08:00:04Z"),
                J8BenchmarkResolutionSource.BLOCKED,
                J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("PRIOR_UNIT_FAILED"));
        assertThat(blocked.attemptId()).isEmpty();
    }

    @Test
    void restrictsDirectEndpointUnavailableEvidenceToHttp404() {
        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                1L,
                OptionalLong.of(11L),
                Instant.parse("2026-08-29T08:00:03Z"),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE,
                true,
                OptionalInt.of(500),
                OptionalLong.of(25L),
                OptionalLong.of(31L),
                OptionalLong.of(41L),
                Optional.of("event-lineups-v2"),
                Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                0,
                Optional.of(J5CompletenessStatus.UNAVAILABLE),
                OptionalInt.of(0),
                Optional.of("HTTP_500")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void refusesParserOutcomesWithoutTheirSnapshotAndParserVersion() {
        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                1L,
                OptionalLong.of(11L),
                Instant.parse("2026-08-29T08:00:03Z"),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                true,
                OptionalInt.of(200),
                OptionalLong.of(25L),
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("SCHEMA_INCOMPATIBLE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persisted snapshot evidence");
    }

    @Test
    void bindsProviderSnapshotsAndParserEvidenceToARealResponse() {
        Instant resolvedAt = Instant.parse("2026-08-29T08:00:03Z");
        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                1L,
                OptionalLong.of(11L),
                resolvedAt,
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.of(31L),
                OptionalLong.of(41L),
                Optional.of("event-details-v2"),
                Optional.empty(),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("PROCESSING_FAILURE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("received response");

        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                1L,
                OptionalLong.of(11L),
                resolvedAt,
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                Optional.of("event-details-v2"),
                Optional.empty(),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("TRANSPORT_FAILURE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parser metadata requires");

        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                1L,
                OptionalLong.empty(),
                resolvedAt,
                J8BenchmarkResolutionSource.CACHE,
                J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.of(31L),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                1,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("PROCESSING_FAILURE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warnings require a parser version");

        assertThatThrownBy(() -> new J8BenchmarkUnitResult(
                1L,
                OptionalLong.of(11L),
                resolvedAt,
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                true,
                OptionalInt.of(500),
                OptionalLong.of(25L),
                OptionalLong.of(31L),
                OptionalLong.of(41L),
                Optional.of("event-details-v2"),
                Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("SCHEMA_INCOMPATIBLE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP 2xx");
    }

    private static J8BenchmarkUnitResult providerParsed() {
        return new J8BenchmarkUnitResult(
                1L,
                OptionalLong.of(11L),
                Instant.parse("2026-08-29T08:00:03Z"),
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                true,
                OptionalInt.of(200),
                OptionalLong.of(25L),
                OptionalLong.of(31L),
                OptionalLong.of(41L),
                Optional.of("event-statistics-v2"),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                1,
                Optional.of(J5CompletenessStatus.COMPLETE),
                OptionalInt.of(100),
                Optional.empty());
    }
}
