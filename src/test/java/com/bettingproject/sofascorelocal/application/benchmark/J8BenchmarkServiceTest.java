package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.application.history.J6HistoryQueryService;
import com.bettingproject.sofascorelocal.application.history.J6HistoryClassifier;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.history.J6ExactTransitionClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J8BenchmarkReadStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class J8BenchmarkServiceTest {

    private static final Instant AS_OF = Instant.parse("2026-08-29T12:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-08-29T10:00:00Z");
    private static final long PROVIDER_EVENT_ID = 8_100_001L;
    private static final CanonicalEventIdentity EVENT_IDENTITY =
            CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID);
    private static final UUID EVENT_ID = EVENT_IDENTITY.value();

    @Mock
    private J8BenchmarkReadStore readStore;

    @Mock
    private J6HistoryQueryService historyQueryService;

    @Test
    void aggregatesExactLedgerRatesPercentilesAndDossierDenominators() {
        UUID j3Campaign = UUID.fromString("83000000-0000-0000-0000-000000000001");
        UUID j4Campaign = UUID.fromString("84000000-0000-0000-0000-000000000001");
        UUID j5Campaign = UUID.fromString("85000000-0000-0000-0000-000000000001");
        List<J8BenchmarkReadEvidence.CampaignEvidence> campaigns = List.of(
                campaign(j3Campaign, J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS),
                campaign(j4Campaign, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2),
                campaign(j5Campaign, J8BenchmarkCampaignType.J5_EVENT_DATA));
        List<J8BenchmarkReadEvidence.UnitEvidence> units = List.of(
                unit(1, j3Campaign, J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                        SofascoreEndpointType.SCHEDULED_EVENTS, 100,
                        Optional.empty()),
                unit(2, j4Campaign, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                        SofascoreEndpointType.EVENT_DETAILS, 200,
                        Optional.empty()),
                unit(3, j5Campaign, J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_STATISTICS, 300,
                        Optional.of(J5CompletenessStatus.COMPLETE)),
                unit(4, j5Campaign, J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_INCIDENTS, 400,
                        Optional.of(J5CompletenessStatus.EMPTY_VALID)),
                unit(5, j5Campaign, J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_LINEUPS, 500,
                        Optional.of(J5CompletenessStatus.PARTIAL)));
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                campaigns,
                units,
                List.of(
                        legacy(98, J6SnapshotOccurrenceOutcome.INSERTED,
                                RawSnapshotSchemaStatus.PARSED, 175),
                        legacy(99, J6SnapshotOccurrenceOutcome.BASELINE,
                                RawSnapshotSchemaStatus.PARSED, 150)),
                0,
                2);
        J8DirectObservationCohorts cohorts = new J8DirectObservationCohorts(List.of(
                cohort(2, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                        SofascoreEndpointType.EVENT_DETAILS, Optional.empty()),
                cohort(3, J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_STATISTICS,
                        Optional.of(J5CompletenessStatus.COMPLETE)),
                cohort(4, J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        Optional.of(J5CompletenessStatus.EMPTY_VALID)),
                cohort(5, J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_LINEUPS,
                        Optional.of(J5CompletenessStatus.PARTIAL))));
        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                Instant.parse("2026-08-29T09:00:00Z"), AS_OF);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(cohorts);
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());
        J8BenchmarkService service = service();

        J8BenchmarkReport first = service.load(window, AS_OF);
        J8BenchmarkReport second = service.load(window, AS_OF);

        assertThat(first.generatedAt()).isEqualTo(AS_OF);
        assertThat(first.asOf()).isEqualTo(AS_OF);
        assertThat(first.evidenceScope().coverage())
                .isEqualTo(J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER);
        assertThat(first.callSummary().directAttemptCount()).isEqualTo(5);
        assertThat(first.evidenceScope().legacyBaselineCount()).isEqualTo(1);
        assertThat(first.evidenceStrata())
                .extracting(J8BenchmarkReport.EvidenceStratumMetrics::coverage)
                .containsExactly(
                        J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                        J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY,
                        J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE);
        assertThat(first.evidenceStrata()).allSatisfy(stratum ->
                assertThat(stratum.evidenceCount()).isPositive());
        assertThat(first.evidenceStrata().get(1).callSummary().state())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(first.evidenceStrata().get(1).endpointMetrics())
                .singleElement()
                .satisfies(endpoint -> assertThat(endpoint.responseEvidenceCount())
                        .isEqualTo(1));
        assertThat(first.evidenceStrata().get(2).endpointMetrics())
                .singleElement()
                .satisfies(endpoint -> assertThat(endpoint.responseEvidenceCount())
                        .isEqualTo(1));
        assertThat(first.callSummary().responseRate().percent().orElseThrow())
                .isEqualByComparingTo("100.00");
        assertThat(first.pageAndFamilyAvailability().observedPageCount()).isEqualTo(1);
        assertThat(first.endpointMetrics()).hasSize(5);
        assertThat(first.completenessBreakdowns()).hasSize(3);
        assertThat(first.dossierEfficiency().targetedEventCount()).isEqualTo(1);
        assertThat(first.dossierEfficiency().exploitableEventCount()).isEqualTo(1);
        assertThat(first.dossierEfficiency().strictlyCompleteEventCount()).isZero();
        assertThat(first.dossierEfficiency().discoveryOverhead()).isEqualTo(1);
        assertThat(first.dossierEfficiency().marginalCallCount()).isEqualTo(4);
        assertThat(first.dossierEfficiency().effectiveCallCount()).isEqualTo(5);
        assertThat(first.dossierEfficiency()
                .marginalCallsPerExploitableDossier().orElseThrow())
                .isEqualByComparingTo("4.00");
        assertThat(first.dossierEfficiency()
                .effectiveCallsPerExploitableDossier().orElseThrow())
                .isEqualByComparingTo("5.00");
        assertThat(first.populationHash()).isEqualTo(second.populationHash());
        assertThat(first.decisionDimensions())
                .extracting(J8BenchmarkReport.DecisionAssessment::dimension)
                .containsExactly(J8BenchmarkReport.DecisionDimension.values());
        verify(readStore, times(2)).readEvidence(window, AS_OF);
        verify(readStore, times(2)).readDirectObservationCohorts(window, AS_OF);
        verify(readStore, times(2)).readLateChanges(window, AS_OF);
    }

    @Test
    void acceptsFiveDirectComponentsAtAsOfWhenOnlyJ4TargetsTheEventInWindow() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000088");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(campaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                List.of(unit(1, campaignId,
                        J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                        SofascoreEndpointType.EVENT_DETAILS, 100, Optional.empty())),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of(currentDossierCohort(
                        1, J8DirectObservationCohorts.DirectComponentState.AVAILABLE))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.dossierEfficiency().targetedEventCount()).isEqualTo(1);
        assertThat(report.dossierEfficiency().exploitableEventCount()).isEqualTo(1);
        assertThat(report.dossierEfficiency().strictlyCompleteEventCount()).isEqualTo(1);
    }

    @Test
    void availabilityKeepsDeclaredBlockedPagesOutsideTheDirectCallCount() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("83000000-0000-0000-0000-000000000084");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(openCampaign(
                        campaignId, J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS)),
                List.of(
                        unit(1, campaignId,
                                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                                SofascoreEndpointType.SCHEDULED_EVENTS,
                                100,
                                Optional.empty()),
                        blockedScheduledPage(2, campaignId, 2)),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.callSummary().directAttemptCount()).isEqualTo(1);
        assertThat(report.pageAndFamilyAvailability().observedPageCount()).isEqualTo(2);
        assertThat(report.pageAndFamilyAvailability().lowestScheduledPage()
                .orElseThrow()).isEqualTo(1);
        assertThat(report.pageAndFamilyAvailability().highestScheduledPage()
                .orElseThrow()).isEqualTo(2);
        assertThat(report.pageAndFamilyAvailability().state())
                .isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.dossierEfficiency().state())
                .isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.dossierEfficiency().targetedEventCount()).isZero();
        assertThat(report.dossierEfficiency().exploitableEventCount()).isZero();
        assertThat(report.dossierEfficiency().strictlyCompleteEventCount()).isZero();
        assertThat(report.dossierEfficiency().discoveryOverhead()).isEqualTo(1);
        assertThat(report.dossierEfficiency().marginalCallCount()).isZero();
        assertThat(report.dossierEfficiency().effectiveCallCount()).isEqualTo(1);
        assertThat(report.dossierEfficiency().exploitableRate().percent()).isEmpty();
        assertThat(report.dossierEfficiency().strictlyCompleteRate().percent()).isEmpty();
        assertThat(report.dossierEfficiency().marginalCallsPerExploitableDossier())
                .isEmpty();
        assertThat(report.dossierEfficiency().effectiveCallsPerExploitableDossier())
                .isEmpty();
    }

    @Test
    void keepsACompletedJ3OnlyLedgerPartialAndUsesTransportLatencyForFreshness() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("83000000-0000-0000-0000-000000000085");
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(campaign(
                                campaignId,
                                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS)),
                        List.of(unit(
                                1,
                                campaignId,
                                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                                SofascoreEndpointType.SCHEDULED_EVENTS,
                                100,
                                Optional.empty())),
                        List.of(),
                        0,
                        0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.callSummary().state()).isEqualTo(J8MeasurementState.MEASURED);
        assertThat(report.state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.dossierEfficiency().state())
                .isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.dossierEfficiency().discoveryOverhead()).isEqualTo(1);
        assertThat(report.dossierEfficiency().marginalCallCount()).isZero();
        assertThat(report.dossierEfficiency().effectiveCallCount()).isEqualTo(1);
        assertThat(report.dossierEfficiency().marginalCallsPerExploitableDossier())
                .isEmpty();
        assertThat(report.dossierEfficiency().effectiveCallsPerExploitableDossier())
                .isEmpty();
        assertThat(report.lateChangeMetrics().state())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.decisionDimensions())
                .filteredOn(assessment -> assessment.dimension()
                        == J8BenchmarkReport.DecisionDimension.FRESHNESS)
                .singleElement()
                .satisfies(freshness -> {
                    assertThat(freshness.state()).isEqualTo(J8MeasurementState.PARTIAL);
                    assertThat(freshness.explanation()).contains(
                            "retard heure source vers réception reste NOT_MEASURED");
                });
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .contains("MANDATORY_DIMENSIONS_INCOMPLETE");
    }

    @Test
    void rejectsADossierWhenTheLatestDirectComponentIsIncompatible() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000087");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(campaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                List.of(unit(1, campaignId,
                        J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                        SofascoreEndpointType.EVENT_DETAILS, 100, Optional.empty())),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of(currentDossierCohort(
                        1, J8DirectObservationCohorts.DirectComponentState.INCOMPATIBLE))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.dossierEfficiency().targetedEventCount()).isEqualTo(1);
        assertThat(report.dossierEfficiency().exploitableEventCount()).isZero();
        assertThat(report.dossierEfficiency().strictlyCompleteEventCount()).isZero();
    }

    @Test
    void rejectsAnAvailableDossierWhoseJ5CompletenessIsUnavailable() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000089");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(campaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                List.of(unit(
                        1,
                        campaignId,
                        J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                        SofascoreEndpointType.EVENT_DETAILS,
                        100,
                        Optional.empty())),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of(
                        withCurrentLineupsCompleteness(
                                currentDossierCohort(
                                        1,
                                        J8DirectObservationCohorts
                                                .DirectComponentState.AVAILABLE),
                                J5CompletenessStatus.UNAVAILABLE))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.dossierEfficiency().targetedEventCount()).isEqualTo(1);
        assertThat(report.dossierEfficiency().exploitableEventCount()).isZero();
        assertThat(report.dossierEfficiency().effectiveCallsPerExploitableDossier())
                .isEmpty();
    }

    @Test
    void keepsResponseOnlyEvidenceAndExactAttemptMetricsNotMeasured() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(),
                List.of(),
                List.of(
                        legacy(20, SofascoreEndpointType.EVENT_STATISTICS,
                                J6SnapshotOccurrenceOutcome.BASELINE,
                                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE, 500),
                        legacy(21, SofascoreEndpointType.EVENT_STATISTICS,
                                J6SnapshotOccurrenceOutcome.INSERTED,
                                RawSnapshotSchemaStatus.PARSED, 250)),
                2,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(
                        List.of(),
                        List.of(
                                historicalCohort(
                                        20,
                                        J6SnapshotOccurrenceOutcome.BASELINE,
                                        J5CompletenessStatus.COMPLETE,
                                        4,
                                        4,
                                        "Baseline competition"),
                                historicalCohort(
                                        21,
                                        J6SnapshotOccurrenceOutcome.INSERTED,
                                        J5CompletenessStatus.PARTIAL,
                                        1,
                                        4,
                                        "Response competition"))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.evidenceScope().coverage())
                .isEqualTo(J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY);
        assertThat(report.evidenceScope().persistedResponseCount()).isEqualTo(1);
        assertThat(report.evidenceScope().responseOnlyCount()).isEqualTo(1);
        assertThat(report.evidenceScope().legacyBaselineCount()).isEqualTo(1);
        assertThat(report.evidenceScope().manualImportCount()).isEqualTo(2);
        assertThat(report.evidenceScope().directAttemptCount()).isZero();
        assertThat(report.callSummary().state())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.dossierEfficiency().state())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.attemptMeasurementState())
                    .isEqualTo(J8MeasurementState.NOT_MEASURED);
            assertThat(endpoint.directAttemptCount()).isZero();
            assertThat(endpoint.responseEvidenceCount()).isEqualTo(1);
            assertThat(endpoint.parsedCount()).isEqualTo(1);
            assertThat(endpoint.errorRate().percent()).isEmpty();
            assertThat(endpoint.schemaBreakMeasurementState())
                    .isEqualTo(J8MeasurementState.NOT_MEASURED);
            assertThat(endpoint.compatibilityRate().percent()).isEmpty();
            assertThat(endpoint.latency().sampleCount()).isEqualTo(1);
            assertThat(endpoint.latency().minimumMs().orElseThrow()).isEqualTo(250);
            assertThat(endpoint.latency().p50Ms().orElseThrow()).isEqualTo(250);
            assertThat(endpoint.latency().p95Ms().orElseThrow()).isEqualTo(250);
            assertThat(endpoint.latency().maximumMs().orElseThrow()).isEqualTo(250);
        });
        assertThat(report.parserStability().state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.parserStability().parsedCount()).isEqualTo(1);
        assertThat(report.parserStability().schemaIncompatibleCount()).isZero();
        assertThat(report.parserStability().schemaBreakMeasurementState())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.completenessBreakdowns()).singleElement().satisfies(row -> {
            assertThat(row.competition()).contains("Response competition");
            assertThat(row.partialCount()).isEqualTo(1);
            assertThat(row.completeCount()).isZero();
            assertThat(row.weightedSignalCoveragePercent().orElseThrow())
                    .isEqualByComparingTo("25.00");
        });
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .contains("ATTEMPT_LEDGER_ABSENT");
    }

    @Test
    void keepsHistoricalResponseEvidencePartialWhenNoCampaignExists() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(),
                        List.of(),
                        List.of(legacy(
                                22,
                                J6SnapshotOccurrenceOutcome.INSERTED,
                                RawSnapshotSchemaStatus.PARSED,
                                150)),
                        0,
                        0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.evidenceScope().providerCampaignCount()).isZero();
        assertThat(report.evidenceScope().coverage())
                .isEqualTo(J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY);
        assertThat(report.state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.responseEvidenceCount()).isEqualTo(1);
            assertThat(endpoint.parsedCount()).isEqualTo(1);
        });
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .doesNotContain("NO_ELIGIBLE_EVIDENCE");
    }

    @Test
    void treatsAScheduledPageWithSeveralNormalizedEventsAsOneParsedResponse() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        J8BenchmarkReadEvidence.LegacyResponseEvidence response =
                new J8BenchmarkReadEvidence.LegacyResponseEvidence(
                        29,
                        4_029,
                        List.of(4_531L, 4_529L, 4_530L),
                        SofascoreEndpointType.SCHEDULED_EVENTS,
                        STARTED_AT,
                        Optional.of(STARTED_AT.plusMillis(120)),
                        OptionalInt.of(200),
                        OptionalLong.of(120),
                        Optional.of(parser(SofascoreEndpointType.SCHEDULED_EVENTS)),
                        Optional.of(RawSnapshotSchemaStatus.PARSED),
                        J6SnapshotOccurrenceOutcome.INSERTED);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(), List.of(), List.of(response), 0, 0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(response.normalizedObservationIds())
                .containsExactly(4_529L, 4_530L, 4_531L);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.responseEvidenceCount()).isEqualTo(1);
            assertThat(endpoint.parsedCount()).isEqualTo(1);
            assertThat(endpoint.latency().sampleCount()).isEqualTo(1);
        });
    }

    @Test
    void usesLegacyBaselinesOnlyWhenNoProspectiveResponseExists() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(),
                List.of(),
                List.of(
                        legacy(31, SofascoreEndpointType.EVENT_STATISTICS,
                                J6SnapshotOccurrenceOutcome.BASELINE,
                                RawSnapshotSchemaStatus.PARSED, 100),
                        legacy(32, SofascoreEndpointType.EVENT_STATISTICS,
                                J6SnapshotOccurrenceOutcome.BASELINE,
                                RawSnapshotSchemaStatus.UNEXPECTED_CONTENT, 900)),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(
                        List.of(),
                        List.of(historicalCohort(
                                31,
                                J6SnapshotOccurrenceOutcome.BASELINE,
                                J5CompletenessStatus.COMPLETE,
                                4,
                                4,
                                "Baseline competition"))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.evidenceScope().coverage())
                .isEqualTo(J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE);
        assertThat(report.evidenceScope().persistedResponseCount()).isEqualTo(2);
        assertThat(report.evidenceScope().responseOnlyCount()).isZero();
        assertThat(report.evidenceScope().legacyBaselineCount()).isEqualTo(2);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.responseEvidenceCount()).isEqualTo(2);
            assertThat(endpoint.directAttemptCount()).isZero();
            assertThat(endpoint.schemaBreakMeasurementState())
                    .isEqualTo(J8MeasurementState.NOT_MEASURED);
            assertThat(endpoint.compatibilityRate().percent()).isEmpty();
            assertThat(endpoint.latency().sampleCount()).isEqualTo(2);
            assertThat(endpoint.latency().minimumMs().orElseThrow()).isEqualTo(100);
            assertThat(endpoint.latency().p50Ms().orElseThrow()).isEqualTo(100);
            assertThat(endpoint.latency().p95Ms().orElseThrow()).isEqualTo(900);
            assertThat(endpoint.latency().maximumMs().orElseThrow()).isEqualTo(900);
        });
        assertThat(report.parserStability().schemaBreakMeasurementState())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.parserStability().compatibilityRate().percent())
                .contains(new BigDecimal("100.00"));
        assertThat(report.completenessBreakdowns()).singleElement().satisfies(row -> {
            assertThat(row.competition()).contains("Baseline competition");
            assertThat(row.completeCount()).isEqualTo(1);
        });
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .contains("LEGACY_BASELINE_STRATUM");
    }

    @Test
    void requiresANewRepeatableReadTransactionForEachReport() {
        Transactional annotation = J8BenchmarkService.class
                .getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
        assertThat(annotation.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void keepsTheGlobalReportPartialWhenAProviderCampaignFailed() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000086");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(failedCampaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                List.of(unit(1, campaignId,
                        J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                        SofascoreEndpointType.EVENT_DETAILS, 100, Optional.empty())),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of(
                        currentDossierCohort(
                                1,
                                J8DirectObservationCohorts.DirectComponentState
                                        .AVAILABLE))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.callSummary().state()).isEqualTo(J8MeasurementState.MEASURED);
        assertThat(report.state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .contains("NON_COMPLETED_CAMPAIGN");
    }

    @Test
    void rejectsAParsedJ4ResultWithoutItsCorrelatedNormalizedObservation() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000085");
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(campaign(
                                campaignId,
                                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                        List.of(unit(
                                1,
                                campaignId,
                                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                                SofascoreEndpointType.EVENT_DETAILS,
                                100,
                                Optional.empty())),
                        List.of(),
                        0,
                        0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        assertThatThrownBy(() -> service().load(window, AS_OF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("normalized observation proof");
    }

    @Test
    void acceptsTwoAttemptedDeduplicatedJ4ResponsesSharingOneUniqueNormalizedProof() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000095");
        J8BenchmarkReadEvidence.CampaignEvidence campaign =
                new J8BenchmarkReadEvidence.CampaignEvidence(
                        campaignId,
                        J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                        J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                        STARTED_AT,
                        J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1.maximumUnits(),
                        Optional.of(STARTED_AT.plusSeconds(600)),
                        Optional.of(J8BenchmarkCampaignTerminalState.COMPLETED),
                        OptionalInt.of(2));
        J8BenchmarkReadEvidence.UnitEvidence first = unit(
                1,
                campaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                SofascoreEndpointType.EVENT_DETAILS,
                100,
                Optional.empty());
        J8BenchmarkReadEvidence.UnitEvidence second = withDeduplicatedResponse(unit(
                2,
                campaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                SofascoreEndpointType.EVENT_DETAILS,
                80,
                Optional.empty()));
        J8DirectObservationCohorts.ObservationCohort firstCohort = cohort(
                1,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                SofascoreEndpointType.EVENT_DETAILS,
                Optional.empty());
        J8DirectObservationCohorts.ObservationCohort secondCohort =
                withCurrentEvidence(
                        cohort(
                                2,
                                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                                SofascoreEndpointType.EVENT_DETAILS,
                                Optional.empty()),
                        firstCohort.currentEvidence());
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(campaign), List.of(first, second), List.of(), 0, 0));
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of(firstCohort, secondCohort)));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.callSummary().directAttemptCount()).isEqualTo(2);
        assertThat(report.callSummary().responseReceivedCount()).isEqualTo(2);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.responseEvidenceCount()).isEqualTo(2);
            assertThat(endpoint.deduplicatedResponseCount()).isEqualTo(1);
        });
    }

    @Test
    void countsOperationalErrorsAndParserBreakRecoveryWithoutTreating404AsError() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000099");
        List<J8BenchmarkReadEvidence.UnitEvidence> units = List.of(
                outcomeUnit(1, campaignId,
                        Optional.of(J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE),
                        true, OptionalInt.of(200),
                        Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE)),
                outcomeUnit(2, campaignId,
                        Optional.of(J8BenchmarkOutcomeType.UNEXPECTED_CONTENT),
                        true, OptionalInt.of(200),
                        Optional.of(RawSnapshotSchemaStatus.UNEXPECTED_CONTENT)),
                outcomeUnit(3, campaignId,
                        Optional.of(J8BenchmarkOutcomeType.PARSED),
                        true, OptionalInt.of(200),
                        Optional.of(RawSnapshotSchemaStatus.PARSED)),
                outcomeUnit(4, campaignId,
                        Optional.of(J8BenchmarkOutcomeType.HTTP_REFUSED),
                        true, OptionalInt.of(403), Optional.empty()),
                outcomeUnit(5, campaignId,
                        Optional.of(J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE),
                        true, OptionalInt.of(404),
                        Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE)),
                outcomeUnit(6, campaignId,
                        Optional.of(J8BenchmarkOutcomeType.OPERATOR_STOP),
                        false, OptionalInt.empty(), Optional.empty()),
                outcomeUnit(7, campaignId, Optional.empty(),
                        false, OptionalInt.empty(), Optional.empty()));
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(openCampaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                units,
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of(
                        cohort(
                                3,
                                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                                SofascoreEndpointType.EVENT_DETAILS,
                                Optional.empty()))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.callSummary().state()).isEqualTo(J8MeasurementState.PARTIAL);
        assertThat(report.callSummary().errorCount()).isEqualTo(5);
        assertThat(report.callSummary().incompleteAttemptCount()).isEqualTo(1);
        assertThat(report.callSummary().errorRate().numerator()).isEqualTo(5);
        assertThat(report.callSummary().endpointUnavailableCount()).isEqualTo(1);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.errorRate().numerator()).isEqualTo(5);
            assertThat(endpoint.errorRate().denominator()).isEqualTo(7);
            assertThat(endpoint.otherErrorCount()).isEqualTo(1);
            assertThat(endpoint.incompleteAttemptCount()).isEqualTo(1);
        });
        assertThat(report.parserStability().schemaIncompatibleCount()).isEqualTo(1);
        assertThat(report.parserStability().unexpectedContentCount()).isEqualTo(1);
        assertThat(report.parserStability().state())
                .isEqualTo(J8MeasurementState.MEASURED);
        assertThat(report.parserStability().schemaBreakMeasurementState())
                .isEqualTo(J8MeasurementState.MEASURED);
        assertThat(report.parserStability().compatibilityRate()).satisfies(rate -> {
            assertThat(rate.numerator()).isEqualTo(1);
            assertThat(rate.denominator()).isEqualTo(3);
            assertThat(rate.percent()).contains(new BigDecimal("33.33"));
        });
        assertThat(report.parserStability().firstBreakAt())
                .contains(STARTED_AT.plusSeconds(11));
        assertThat(report.parserStability().lastBreakAt())
                .contains(STARTED_AT.plusSeconds(12));
        assertThat(report.parserStability().firstRecoveryAt())
                .contains(STARTED_AT.plusSeconds(13));
    }

    @Test
    void derivesARefusalFromHttpStatusEvenWhenPersistenceIsTheTerminalOutcome() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000094");
        J8BenchmarkReadEvidence.UnitEvidence refusedThenFailed = outcomeUnit(
                1,
                campaignId,
                Optional.of(J8BenchmarkOutcomeType.PERSISTENCE_FAILURE),
                true,
                OptionalInt.of(401),
                Optional.empty());
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(openCampaign(
                                campaignId,
                                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                        List.of(refusedThenFailed),
                        List.of(),
                        0,
                        0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.callSummary().directAttemptCount()).isEqualTo(1);
        assertThat(report.callSummary().refusalCount()).isEqualTo(1);
        assertThat(report.callSummary().errorCount()).isEqualTo(1);
        assertThat(report.callSummary().refusalRate().numerator()).isEqualTo(1);
        assertThat(report.callSummary().errorRate().numerator()).isEqualTo(1);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.refusalCount()).isEqualTo(1);
            assertThat(endpoint.otherErrorCount()).isEqualTo(1);
            assertThat(endpoint.errorRate().numerator()).isEqualTo(1);
            assertThat(endpoint.errorRate().denominator()).isEqualTo(1);
        });
    }

    @Test
    void correlatesParserRecoveryToTheEndpointThatActuallyBroke() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID j5Campaign = UUID.fromString("85000000-0000-0000-0000-000000000083");
        UUID j3Campaign = UUID.fromString("83000000-0000-0000-0000-000000000083");
        List<J8BenchmarkReadEvidence.UnitEvidence> units = List.of(
                outcomeUnit(
                        1,
                        j5Campaign,
                        J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_LINEUPS,
                        Optional.of(J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE),
                        true,
                        OptionalInt.of(200),
                        Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE)),
                outcomeUnit(
                        2,
                        j3Campaign,
                        J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                        SofascoreEndpointType.SCHEDULED_EVENTS,
                        Optional.of(J8BenchmarkOutcomeType.PARSED),
                        true,
                        OptionalInt.of(200),
                        Optional.of(RawSnapshotSchemaStatus.PARSED)),
                outcomeUnit(
                        3,
                        j5Campaign,
                        J8BenchmarkCampaignType.J5_EVENT_DATA,
                        SofascoreEndpointType.EVENT_LINEUPS,
                        Optional.of(J8BenchmarkOutcomeType.PARSED),
                        true,
                        OptionalInt.of(200),
                        Optional.of(RawSnapshotSchemaStatus.PARSED)));
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(
                                campaign(j5Campaign, J8BenchmarkCampaignType.J5_EVENT_DATA),
                                campaign(j3Campaign,
                                        J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS)),
                        units,
                        List.of(),
                        0,
                        0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of(
                        cohort(
                                3,
                                J8BenchmarkCampaignType.J5_EVENT_DATA,
                                SofascoreEndpointType.EVENT_LINEUPS,
                                Optional.empty()))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.parserStability().lastBreakAt())
                .contains(STARTED_AT.plusSeconds(11));
        assertThat(report.parserStability().firstRecoveryAt())
                .contains(STARTED_AT.plusSeconds(13));
    }

    @Test
    void weightsCompletenessSignalsAndMakesMissingDimensionsExplicit() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("85000000-0000-0000-0000-000000000099");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(campaign(campaignId, J8BenchmarkCampaignType.J5_EVENT_DATA)),
                List.of(
                        unit(1, campaignId, J8BenchmarkCampaignType.J5_EVENT_DATA,
                                SofascoreEndpointType.EVENT_STATISTICS, 100,
                                Optional.of(J5CompletenessStatus.PARTIAL)),
                        unit(2, campaignId, J8BenchmarkCampaignType.J5_EVENT_DATA,
                                SofascoreEndpointType.EVENT_STATISTICS, 200,
                                Optional.of(J5CompletenessStatus.PARTIAL)),
                        unit(3, campaignId, J8BenchmarkCampaignType.J5_EVENT_DATA,
                                SofascoreEndpointType.EVENT_INCIDENTS, 300,
                                Optional.of(J5CompletenessStatus.PARTIAL))),
                List.of(),
                0,
                0);
        J8DirectObservationCohorts cohorts = new J8DirectObservationCohorts(List.of(
                weightedCohort(1, SofascoreEndpointType.EVENT_STATISTICS,
                        1, 2, Optional.of("Ligue 1"), Optional.of("2026/27"),
                        Optional.of("finished")),
                weightedCohort(2, SofascoreEndpointType.EVENT_STATISTICS,
                        9, 10, Optional.of("Ligue 1"), Optional.of("2026/27"),
                        Optional.of("finished")),
                weightedCohort(4, SofascoreEndpointType.EVENT_STATISTICS,
                        7, 0, Optional.of("Ligue 1"), Optional.of("2026/27"),
                        Optional.of("finished")),
                weightedCohort(3, SofascoreEndpointType.EVENT_INCIDENTS,
                        1, 2, Optional.empty(), Optional.empty(), Optional.empty())));
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(cohorts);
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.completenessBreakdowns())
                .filteredOn(row -> row.endpoint()
                        == SofascoreEndpointType.EVENT_STATISTICS)
                .singleElement()
                .satisfies(row -> assertThat(row.weightedSignalCoveragePercent()
                        .orElseThrow()).isEqualByComparingTo("83.33"));
        assertThat(report.completenessBreakdowns())
                .filteredOn(row -> row.endpoint()
                        == SofascoreEndpointType.EVENT_INCIDENTS)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.competition()).contains("UNKNOWN");
                    assertThat(row.season()).contains("UNKNOWN");
                    assertThat(row.eventStatus()).contains("UNKNOWN");
                    assertThat(row.state()).isEqualTo(J8MeasurementState.PARTIAL);
                });
    }

    @Test
    void countsUnavailableJ5WithoutInventingAZeroCompletenessRatio() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("85000000-0000-0000-0000-000000000098");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(campaign(campaignId, J8BenchmarkCampaignType.J5_EVENT_DATA)),
                List.of(unavailableUnit(1, campaignId)),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of(unavailableCohort(1))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.completenessBreakdowns()).singleElement().satisfies(row -> {
            assertThat(row.endpoint()).isEqualTo(SofascoreEndpointType.EVENT_LINEUPS);
            assertThat(row.unavailableCount()).isEqualTo(1);
            assertThat(row.weightedSignalCoveragePercent()).isEmpty();
        });
    }

    @Test
    void usesOnlyTheCurrentEndpointAttemptsForItsUnavailableRate() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID j5Campaign = UUID.fromString("85000000-0000-0000-0000-000000000096");
        UUID j3Campaign = UUID.fromString("83000000-0000-0000-0000-000000000096");
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(
                        openCampaign(j5Campaign, J8BenchmarkCampaignType.J5_EVENT_DATA),
                        openCampaign(
                                j3Campaign,
                                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS)),
                List.of(
                        unavailableUnit(1, j5Campaign),
                        unit(
                                2,
                                j3Campaign,
                                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                                SofascoreEndpointType.SCHEDULED_EVENTS,
                                120,
                                Optional.empty())),
                List.of(),
                0,
                0);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of(unavailableCohort(1))));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.callSummary().endpointUnavailableRate()).satisfies(rate -> {
            assertThat(rate.numerator()).isEqualTo(1);
            assertThat(rate.denominator()).isEqualTo(2);
            assertThat(rate.percent().orElseThrow()).isEqualByComparingTo("50.00");
        });
        assertThat(report.endpointMetrics())
                .filteredOn(metric -> metric.endpoint()
                        == SofascoreEndpointType.EVENT_LINEUPS)
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.endpointUnavailableCount()).isEqualTo(1);
                    assertThat(metric.endpointUnavailableRate().numerator()).isEqualTo(1);
                    assertThat(metric.endpointUnavailableRate().denominator()).isEqualTo(1);
                    assertThat(metric.endpointUnavailableRate().percent().orElseThrow())
                            .isEqualByComparingTo("100.00");
                });
        assertThat(report.endpointMetrics())
                .filteredOn(metric -> metric.endpoint()
                        == SofascoreEndpointType.SCHEDULED_EVENTS)
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.endpointUnavailableCount()).isZero();
                    assertThat(metric.endpointUnavailableRate().numerator()).isZero();
                    assertThat(metric.endpointUnavailableRate().denominator()).isEqualTo(1);
                    assertThat(metric.endpointUnavailableRate().percent().orElseThrow())
                            .isEqualByComparingTo("0.00");
                });
    }

    @Test
    void keepsDeduplicatedResponseEvidenceButNotAZeroParsingMeasurement() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        J8BenchmarkReadEvidence.LegacyResponseEvidence deduplicated = legacy(
                41,
                SofascoreEndpointType.EVENT_DETAILS,
                J6SnapshotOccurrenceOutcome.DEDUPLICATED,
                RawSnapshotSchemaStatus.RAW_ONLY,
                180);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(), List.of(), List.of(deduplicated), 0, 0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.evidenceScope().coverage())
                .isEqualTo(J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY);
        assertThat(report.endpointMetrics()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.state()).isEqualTo(J8MeasurementState.PARTIAL);
            assertThat(endpoint.responseEvidenceCount()).isEqualTo(1);
            assertThat(endpoint.deduplicatedResponseCount()).isEqualTo(1);
            assertThat(endpoint.latency().sampleCount()).isEqualTo(1);
            assertThat(endpoint.parsingMeasurementState())
                    .isEqualTo(J8MeasurementState.NOT_MEASURED);
            assertThat(endpoint.compatibilityRate().percent()).isEmpty();
            assertThat(endpoint.parserVersions()).isEmpty();
        });
        assertThat(report.parserStability().state())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.parserStability().eligibleResponseCount()).isZero();
        assertThat(report.parserStability().parsedCount()).isZero();
        assertThat(report.parserStability().parserVersionCount()).isZero();
        assertThat(report.parserStability().parserVersionTransitionCount()).isZero();
        assertThat(report.parserStability().compatibilityRate().percent()).isEmpty();
    }

    @Test
    void reusesExactJ6TransitionsAndMeasuresFromTheBoundedTerminalProof() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        List<J8LateChangeEvidence> changes = List.of(
                new J8LateChangeEvidence(
                        EVENT_ID, J6HistoryStream.EVENT_STATE, 102, 101, 1_001,
                        STARTED_AT.plusSeconds(30), OptionalLong.of(901),
                        Optional.of("finished"),
                        Optional.of(STARTED_AT)),
                new J8LateChangeEvidence(
                        EVENT_ID, J6HistoryStream.EVENT_DETAILS, 104, 103, 1_002,
                        STARTED_AT.plusSeconds(90), OptionalLong.of(902),
                        Optional.of("finished"),
                        Optional.of(STARTED_AT)),
                new J8LateChangeEvidence(
                        EVENT_ID, J6HistoryStream.EVENT_STATISTICS, 106, 105, 1_004,
                        STARTED_AT.plusSeconds(150), OptionalLong.empty(),
                        Optional.empty(), Optional.empty()),
                new J8LateChangeEvidence(
                        EVENT_ID, J6HistoryStream.EVENT_INCIDENTS, 108, 107, 1_006,
                        STARTED_AT.plusSeconds(240), OptionalLong.of(903),
                        Optional.of("finished"),
                        Optional.of(STARTED_AT.plusSeconds(180))),
                new J8LateChangeEvidence(
                        EVENT_ID, J6HistoryStream.EVENT_LINEUPS, 110, 109, 1_008,
                        STARTED_AT.plusSeconds(420), OptionalLong.of(904),
                        Optional.of("finished"),
                        Optional.of(STARTED_AT.plusSeconds(300))),
                new J8LateChangeEvidence(
                        EVENT_ID, J6HistoryStream.EVENT_LINEUPS, 111, 110, 1_009,
                        STARTED_AT.plusSeconds(480), OptionalLong.of(905),
                        Optional.of("finished"),
                        Optional.of(STARTED_AT.plusSeconds(450))));
        stubExactTransition(changes.get(0),
                J6HistoryClassification.TECHNICAL_DUPLICATE);
        stubExactTransition(changes.get(1),
                J6HistoryClassification.LOCAL_REPARSE);
        stubExactTransition(changes.get(2),
                J6HistoryClassification.PROVIDER_UPDATE);
        stubExactTransition(changes.get(3),
                J6HistoryClassification.LATE_ENRICHMENT);
        stubExactTransition(changes.get(4),
                J6HistoryClassification.LATE_CORRECTION);
        stubExactTransition(changes.get(5),
                J6HistoryClassification.LATE_CORRECTION);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(List.of(), List.of(), List.of(), 0, 0));
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(changes);

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.lateChangeMetrics().state())
                .isEqualTo(J8MeasurementState.MEASURED);
        assertThat(report.lateChangeMetrics().analyzedEventCount()).isEqualTo(1);
        assertThat(report.lateChangeMetrics().analyzedProviderVersionCount()).isEqualTo(6);
        assertThat(report.lateChangeMetrics().lateEnrichmentCount()).isEqualTo(1);
        assertThat(report.lateChangeMetrics().lateCorrectionCount()).isEqualTo(2);
        assertThat(report.lateChangeMetrics().lateChangeDelay().sampleCount()).isEqualTo(3);
        assertThat(report.lateChangeMetrics().lateChangeDelay().minimumMs()
                .orElseThrow()).isEqualTo(30_000);
        assertThat(report.lateChangeMetrics().lateChangeDelay().p95Ms()
                .orElseThrow()).isEqualTo(120_000);
        J8BenchmarkReport replay = service().load(window, AS_OF);
        assertThat(replay.populationHash()).isEqualTo(report.populationHash());
        assertThat(replay.lateChangeMetrics()).isEqualTo(report.lateChangeMetrics());
        verify(historyQueryService, times(2)).classifyExactTransition(
                EVENT_ID,
                J6HistoryStream.EVENT_STATISTICS,
                105,
                106,
                false);
        verify(historyQueryService, never()).findHistory(
                EVENT_ID,
                Optional.of(J6HistoryStream.EVENT_STATISTICS),
                0,
                J6HistoryQueryService.MAXIMUM_PAGE_SIZE);
    }

    @Test
    void keepsTheDirectPairMeasuredWhenImportAndFixtureStrataAreInterleaved() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        J8LateChangeEvidence target = new J8LateChangeEvidence(
                EVENT_ID,
                J6HistoryStream.EVENT_DETAILS,
                202,
                201,
                2_002,
                STARTED_AT.plusSeconds(300),
                OptionalLong.of(910),
                Optional.of("finished"),
                Optional.of(STARTED_AT));
        stubExactTransition(target, J6HistoryClassification.LATE_CORRECTION);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(List.of(), List.of(), List.of(), 1, 1));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of(target));

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.lateChangeMetrics().state())
                .isEqualTo(J8MeasurementState.MEASURED);
        assertThat(report.lateChangeMetrics().analyzedProviderVersionCount()).isOne();
        assertThat(report.lateChangeMetrics().lateCorrectionCount()).isOne();
        assertThat(report.lateChangeMetrics().lateChangeDelay().sampleCount()).isOne();
    }

    @Test
    void populationHashCommitsToThePreviousDirectStateObservationIdentity() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        J8LateChangeEvidence firstStateProof = new J8LateChangeEvidence(
                EVENT_ID,
                J6HistoryStream.EVENT_DETAILS,
                302,
                301,
                3_002,
                STARTED_AT.plusSeconds(300),
                OptionalLong.of(920),
                Optional.of("finished"),
                Optional.of(STARTED_AT));
        J8LateChangeEvidence substitutedStateProof = new J8LateChangeEvidence(
                EVENT_ID,
                J6HistoryStream.EVENT_DETAILS,
                302,
                301,
                3_002,
                STARTED_AT.plusSeconds(300),
                OptionalLong.of(921),
                Optional.of("finished"),
                Optional.of(STARTED_AT));
        stubExactTransition(firstStateProof, J6HistoryClassification.PROVIDER_UPDATE);
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(List.of(), List.of(), List.of(), 0, 0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF))
                .thenReturn(List.of(firstStateProof), List.of(substitutedStateProof));
        J8BenchmarkService service = service();

        String firstHash = service.load(window, AS_OF).populationHash();
        String substitutedHash = service.load(window, AS_OF).populationHash();

        assertThat(substitutedHash).isNotEqualTo(firstHash);
    }

    @Test
    void populationHashCommitsToContractWindowAndAsOf() {
        J8BenchmarkWindow allAvailable = J8BenchmarkWindow.allAvailable();
        J8BenchmarkWindow bounded = J8BenchmarkWindow.between(STARTED_AT, AS_OF);
        Instant laterAsOf = AS_OF.plusSeconds(1);
        J8BenchmarkReadEvidence empty = new J8BenchmarkReadEvidence(
                List.of(), List.of(), List.of(), 0, 0);
        J8DirectObservationCohorts noCohorts =
                new J8DirectObservationCohorts(List.of());
        when(readStore.readEvidence(allAvailable, AS_OF)).thenReturn(empty);
        when(readStore.readDirectObservationCohorts(allAvailable, AS_OF))
                .thenReturn(noCohorts);
        when(readStore.readLateChanges(allAvailable, AS_OF)).thenReturn(List.of());
        when(readStore.readEvidence(allAvailable, laterAsOf)).thenReturn(empty);
        when(readStore.readDirectObservationCohorts(allAvailable, laterAsOf))
                .thenReturn(noCohorts);
        when(readStore.readLateChanges(allAvailable, laterAsOf)).thenReturn(List.of());
        when(readStore.readEvidence(bounded, AS_OF)).thenReturn(empty);
        when(readStore.readDirectObservationCohorts(bounded, AS_OF))
                .thenReturn(noCohorts);
        when(readStore.readLateChanges(bounded, AS_OF)).thenReturn(List.of());

        J8BenchmarkService service = service();
        String first = service.load(allAvailable, AS_OF).populationHash();
        String later = service.load(allAvailable, laterAsOf).populationHash();
        String boundedHash = service.load(bounded, AS_OF).populationHash();
        String expected = Sha256.hex(String.join("\n", List.of(
                "asOf|2026-08-29T12:00:00Z",
                "j8-benchmark-v1",
                "manual-import-occurrences|0",
                "synthetic-observations|0",
                "window|from|ALL_AVAILABLE",
                "window|to|ALL_AVAILABLE"))
                .getBytes(StandardCharsets.UTF_8));

        assertThat(first).isEqualTo(expected);
        assertThat(later).isNotEqualTo(first);
        assertThat(boundedHash).isNotEqualTo(first);
    }

    @Test
    void populationHashCommitsToCampaignResultsAndCurrentEvidenceIds() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        UUID campaignId = UUID.fromString("84000000-0000-0000-0000-000000000085");
        List<J8BenchmarkReadEvidence.UnitEvidence> units = List.of(unit(
                1,
                campaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                SofascoreEndpointType.EVENT_DETAILS,
                100,
                Optional.empty()));
        J8BenchmarkReadEvidence completed = new J8BenchmarkReadEvidence(
                List.of(campaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                units,
                List.of(),
                0,
                0);
        J8BenchmarkReadEvidence failed = new J8BenchmarkReadEvidence(
                List.of(failedCampaign(
                        campaignId, J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2)),
                units,
                List.of(),
                0,
                0);
        J8DirectObservationCohorts firstCohort = new J8DirectObservationCohorts(
                List.of(currentDossierCohort(
                        1,
                        J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                        currentEvidence(10_000))));
        J8DirectObservationCohorts changedCohort = new J8DirectObservationCohorts(
                List.of(currentDossierCohort(
                        1,
                        J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                        currentEvidence(20_000))));
        when(readStore.readEvidence(window, AS_OF))
                .thenReturn(completed, failed, failed);
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(firstCohort, firstCohort, changedCohort);
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkService service = service();
        String completedHash = service.load(window, AS_OF).populationHash();
        String failedHash = service.load(window, AS_OF).populationHash();
        String changedEvidenceHash = service.load(window, AS_OF).populationHash();

        assertThat(failedHash).isNotEqualTo(completedHash);
        assertThat(changedEvidenceHash).isNotEqualTo(failedHash);
    }

    @Test
    void populationHashCommitsToHistoricalDimensionObservationIds() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        long occurrenceId = 91;
        J8BenchmarkReadEvidence evidence = new J8BenchmarkReadEvidence(
                List.of(),
                List.of(),
                List.of(legacy(
                        occurrenceId,
                        SofascoreEndpointType.EVENT_STATISTICS,
                        J6SnapshotOccurrenceOutcome.INSERTED,
                        RawSnapshotSchemaStatus.PARSED,
                        120)),
                0,
                0);
        var firstDimensions = new J8DirectObservationCohorts.HistoricalObservationCohort(
                occurrenceId,
                4_000 + occurrenceId,
                OptionalLong.of(4_500 + occurrenceId),
                OptionalLong.of(7_001),
                OptionalLong.of(8_001),
                J6SnapshotOccurrenceOutcome.INSERTED,
                SofascoreEndpointType.EVENT_STATISTICS,
                STARTED_AT.plusSeconds(occurrenceId),
                Optional.of(J5CompletenessStatus.COMPLETE),
                OptionalInt.of(10),
                OptionalInt.of(10),
                true,
                Optional.of("Competition"),
                Optional.of("2026/27"),
                Optional.of("finished"));
        var changedStateProof = new J8DirectObservationCohorts.HistoricalObservationCohort(
                occurrenceId,
                4_000 + occurrenceId,
                OptionalLong.of(4_500 + occurrenceId),
                OptionalLong.of(7_002),
                OptionalLong.of(8_001),
                J6SnapshotOccurrenceOutcome.INSERTED,
                SofascoreEndpointType.EVENT_STATISTICS,
                STARTED_AT.plusSeconds(occurrenceId),
                Optional.of(J5CompletenessStatus.COMPLETE),
                OptionalInt.of(10),
                OptionalInt.of(10),
                true,
                Optional.of("Competition"),
                Optional.of("2026/27"),
                Optional.of("finished"));
        when(readStore.readEvidence(window, AS_OF)).thenReturn(evidence);
        when(readStore.readDirectObservationCohorts(window, AS_OF)).thenReturn(
                new J8DirectObservationCohorts(
                        List.of(), List.of(firstDimensions)),
                new J8DirectObservationCohorts(
                        List.of(), List.of(changedStateProof)));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkService benchmarkService = service();
        String firstHash = benchmarkService.load(window, AS_OF).populationHash();
        String changedHash = benchmarkService.load(window, AS_OF).populationHash();

        assertThat(changedHash).isNotEqualTo(firstHash);
    }

    @Test
    void producesAnExplicitNotMeasuredReportForAnEmptyWindow() {
        J8BenchmarkWindow window = J8BenchmarkWindow.allAvailable();
        when(readStore.readEvidence(window, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(List.of(), List.of(), List.of(), 0, 0));
        when(readStore.readDirectObservationCohorts(window, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(window, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(window, AS_OF);

        assertThat(report.state()).isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.callSummary().responseRate().percent()).isEmpty();
        assertThat(report.dossierEfficiency()
                .effectiveCallsPerExploitableDossier()).isEmpty();
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .contains("NO_ELIGIBLE_EVIDENCE");
    }

    @Test
    void truncatesAFutureWindowEndAtAsOfBeforeReadingAnyEvidence() {
        J8BenchmarkWindow requestedWindow = J8BenchmarkWindow.between(
                Instant.parse("2026-08-29T10:00:00Z"),
                Instant.parse("2026-08-29T13:00:00Z"));
        J8BenchmarkWindow effectiveWindow = J8BenchmarkWindow.between(
                Instant.parse("2026-08-29T10:00:00Z"), AS_OF);
        when(readStore.readEvidence(effectiveWindow, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(List.of(), List.of(), List.of(), 0, 0));
        when(readStore.readDirectObservationCohorts(effectiveWindow, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(effectiveWindow, AS_OF)).thenReturn(List.of());

        J8BenchmarkReport report = service().load(requestedWindow, AS_OF);

        assertThat(report.effectiveWindow()).isEqualTo(effectiveWindow);
        assertThat(report.asOf()).isEqualTo(AS_OF);
        assertThat(report.state()).isEqualTo(J8MeasurementState.NOT_MEASURED);
        verify(readStore).readEvidence(effectiveWindow, AS_OF);
        verify(readStore).readDirectObservationCohorts(effectiveWindow, AS_OF);
        verify(readStore).readLateChanges(effectiveWindow, AS_OF);
        verify(readStore, never()).readEvidence(requestedWindow, AS_OF);
    }

    @Test
    void returnsAnExplicitEmptyWindowWhenFromIsAtOrAfterAsOf() {
        J8BenchmarkWindow futureOnlyWindow = J8BenchmarkWindow.between(
                AS_OF,
                AS_OF.plusSeconds(3_600));

        J8BenchmarkReport report = service().load(futureOnlyWindow, AS_OF);

        assertThat(report.effectiveWindow().fromInclusive()).contains(AS_OF);
        assertThat(report.effectiveWindow().toExclusive()).contains(AS_OF);
        assertThat(report.effectiveWindow().empty()).isTrue();
        assertThat(report.state()).isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(report.callSummary().responseRate().percent()).isEmpty();
        assertThat(report.dossierEfficiency().exploitableRate().percent()).isEmpty();
        assertThat(report.endpointMetrics()).isEmpty();
        assertThat(report.limitations())
                .extracting(J8BenchmarkReport.Limitation::code)
                .contains("NO_ELIGIBLE_EVIDENCE");

        verifyNoInteractions(readStore);
    }

    @Test
    void capturesTheInjectedClockOnceForThePublicLoadAndEveryReadBoundary() {
        Instant requestedTo = AS_OF.plusSeconds(3_600);
        J8BenchmarkWindow requestedWindow = J8BenchmarkWindow.between(
                STARTED_AT, requestedTo);
        J8BenchmarkWindow effectiveWindow = J8BenchmarkWindow.between(
                STARTED_AT, AS_OF);
        Clock countingClock = mock(Clock.class);
        when(countingClock.instant()).thenReturn(AS_OF, AS_OF.plusSeconds(1));
        when(readStore.readEvidence(effectiveWindow, AS_OF)).thenReturn(
                new J8BenchmarkReadEvidence(
                        List.of(), List.of(), List.of(), 0, 0));
        when(readStore.readDirectObservationCohorts(effectiveWindow, AS_OF))
                .thenReturn(new J8DirectObservationCohorts(List.of()));
        when(readStore.readLateChanges(effectiveWindow, AS_OF)).thenReturn(List.of());
        J8BenchmarkService service = new J8BenchmarkService(
                readStore,
                historyQueryService,
                new J6HistoryClassifier(),
                countingClock);

        J8BenchmarkReport report = service.load(requestedWindow);

        assertThat(report.generatedAt()).isEqualTo(AS_OF);
        assertThat(report.asOf()).isEqualTo(AS_OF);
        assertThat(report.effectiveWindow()).isEqualTo(effectiveWindow);
        verify(countingClock, times(1)).instant();
        verify(readStore).readEvidence(effectiveWindow, AS_OF);
        verify(readStore).readDirectObservationCohorts(effectiveWindow, AS_OF);
        verify(readStore).readLateChanges(effectiveWindow, AS_OF);
    }

    private J8BenchmarkService service() {
        return new J8BenchmarkService(
                readStore,
                historyQueryService,
                new J6HistoryClassifier(),
                Clock.fixed(AS_OF, ZoneOffset.UTC));
    }

    private void stubExactTransition(
            J8LateChangeEvidence evidence,
            J6HistoryClassification classification) {
        J6ExactTransitionClassification comparison =
                new J6ExactTransitionClassification(
                        evidence.stream(),
                        evidence.previousObservationId(),
                        evidence.observationId(),
                        classification,
                        List.of());
        when(historyQueryService.classifyExactTransition(
                evidence.canonicalEventId(),
                evidence.stream(),
                evidence.previousObservationId(),
                evidence.observationId(),
                evidence.previousDirectStateStatus()
                        .map(new J6HistoryClassifier()::isTerminalStatus)
                        .orElse(false)))
                .thenReturn(Optional.of(comparison));
    }

    private static J8BenchmarkReadEvidence.CampaignEvidence campaign(
            UUID campaignId,
            J8BenchmarkCampaignType campaignType) {
        return new J8BenchmarkReadEvidence.CampaignEvidence(
                campaignId,
                campaignType,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                STARTED_AT,
                campaignType.maximumUnits(),
                Optional.of(STARTED_AT.plusSeconds(600)),
                Optional.of(J8BenchmarkCampaignTerminalState.COMPLETED),
                OptionalInt.of(campaignType == J8BenchmarkCampaignType.J5_EVENT_DATA
                        ? 3 : 1));
    }

    private static J8BenchmarkReadEvidence.CampaignEvidence openCampaign(
            UUID campaignId,
            J8BenchmarkCampaignType campaignType) {
        return new J8BenchmarkReadEvidence.CampaignEvidence(
                campaignId,
                campaignType,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                STARTED_AT,
                campaignType.maximumUnits(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty());
    }

    private static J8BenchmarkReadEvidence.CampaignEvidence failedCampaign(
            UUID campaignId,
            J8BenchmarkCampaignType campaignType) {
        return new J8BenchmarkReadEvidence.CampaignEvidence(
                campaignId,
                campaignType,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                STARTED_AT,
                campaignType.maximumUnits(),
                Optional.of(STARTED_AT.plusSeconds(600)),
                Optional.of(J8BenchmarkCampaignTerminalState.FAILED),
                OptionalInt.of(1));
    }

    private static J8BenchmarkReadEvidence.UnitEvidence unit(
            long unitId,
            UUID campaignId,
            J8BenchmarkCampaignType campaignType,
            SofascoreEndpointType endpoint,
            long latency,
            Optional<J5CompletenessStatus> completeness) {
        boolean discovery = endpoint == SofascoreEndpointType.SCHEDULED_EVENTS;
        int completenessScore = completeness.map(status -> switch (status) {
            case COMPLETE, EMPTY_VALID -> 100;
            case PARTIAL -> 75;
            case UNAVAILABLE -> 0;
        }).orElse(-1);
        return new J8BenchmarkReadEvidence.UnitEvidence(
                campaignId,
                campaignType,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                unitId,
                1,
                endpoint,
                discovery ? Optional.empty() : Optional.of(EVENT_ID),
                discovery ? OptionalLong.empty() : OptionalLong.of(PROVIDER_EVENT_ID),
                STARTED_AT.plusSeconds(unitId),
                OptionalLong.of(100 + unitId),
                Optional.of(STARTED_AT.plusSeconds(unitId + 10)),
                Optional.of(STARTED_AT.plusSeconds(unitId + 20)),
                Optional.of(J8BenchmarkResolutionSource.PROVIDER),
                Optional.of(J8BenchmarkOutcomeType.PARSED),
                true,
                OptionalInt.of(200),
                OptionalLong.of(latency),
                OptionalLong.of(200 + unitId),
                OptionalLong.of(300 + unitId),
                false,
                Optional.of(parser(endpoint)),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                completeness,
                completenessScore < 0
                        ? OptionalInt.empty()
                        : OptionalInt.of(completenessScore));
    }

    private static J8DirectObservationCohorts.ObservationCohort cohort(
            long unitId,
            J8BenchmarkCampaignType campaignType,
            SofascoreEndpointType endpoint,
            Optional<J5CompletenessStatus> completeness) {
        int completenessScore = completeness.map(status -> switch (status) {
            case COMPLETE, EMPTY_VALID -> 100;
            case PARTIAL -> 75;
            case UNAVAILABLE -> 0;
        }).orElse(-1);
        return new J8DirectObservationCohorts.ObservationCohort(
                unitId,
                campaignType,
                PROVIDER_EVENT_ID,
                Optional.of(EVENT_ID),
                endpoint,
                true,
                true,
                Optional.of(J8BenchmarkOutcomeType.PARSED),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                Optional.of(parser(endpoint)),
                completeness,
                completenessScore < 0
                        ? OptionalInt.empty()
                        : OptionalInt.of(completenessScore),
                completeness.isEmpty()
                        ? OptionalInt.empty()
                        : OptionalInt.of(completeness.orElseThrow()
                                == J5CompletenessStatus.PARTIAL ? 3
                                        : completeness.orElseThrow()
                                                == J5CompletenessStatus.COMPLETE ? 4 : 0),
                completeness.isEmpty()
                        ? OptionalInt.empty()
                        : OptionalInt.of(completeness.orElseThrow()
                                == J5CompletenessStatus.PARTIAL
                                        || completeness.orElseThrow()
                                                == J5CompletenessStatus.COMPLETE ? 4 : 0),
                true,
                true,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                Optional.of(J5CompletenessStatus.COMPLETE),
                Optional.of(J5CompletenessStatus.EMPTY_VALID),
                Optional.of(J5CompletenessStatus.PARTIAL),
                currentEvidence(10_000 + unitId * 100),
                Optional.of("UEFA Champions League"),
                Optional.of("2026/27"),
                Optional.of("finished"));
    }

    private static J8BenchmarkReadEvidence.UnitEvidence outcomeUnit(
            long unitId,
            UUID campaignId,
            Optional<J8BenchmarkOutcomeType> outcome,
            boolean responseReceived,
            OptionalInt httpStatus,
            Optional<RawSnapshotSchemaStatus> schemaStatus) {
        return outcomeUnit(
                unitId,
                campaignId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                SofascoreEndpointType.EVENT_DETAILS,
                outcome,
                responseReceived,
                httpStatus,
                schemaStatus);
    }

    private static J8BenchmarkReadEvidence.UnitEvidence outcomeUnit(
            long unitId,
            UUID campaignId,
            J8BenchmarkCampaignType campaignType,
            SofascoreEndpointType endpoint,
            Optional<J8BenchmarkOutcomeType> outcome,
            boolean responseReceived,
            OptionalInt httpStatus,
            Optional<RawSnapshotSchemaStatus> schemaStatus) {
        boolean resolved = outcome.isPresent();
        boolean parsingEligible = outcome.filter(value ->
                value == J8BenchmarkOutcomeType.PARSED
                        || value == J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE
                        || value == J8BenchmarkOutcomeType.UNEXPECTED_CONTENT).isPresent();
        return new J8BenchmarkReadEvidence.UnitEvidence(
                campaignId,
                campaignType,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                unitId,
                Math.toIntExact(unitId),
                endpoint,
                endpoint == SofascoreEndpointType.SCHEDULED_EVENTS
                        ? Optional.empty()
                        : Optional.of(EVENT_ID),
                endpoint == SofascoreEndpointType.SCHEDULED_EVENTS
                        ? OptionalLong.empty()
                        : OptionalLong.of(PROVIDER_EVENT_ID),
                STARTED_AT.plusSeconds(unitId),
                OptionalLong.of(900 + unitId),
                Optional.of(STARTED_AT.plusSeconds(unitId + 10)),
                resolved ? Optional.of(STARTED_AT.plusSeconds(unitId + 20))
                        : Optional.empty(),
                resolved ? Optional.of(J8BenchmarkResolutionSource.PROVIDER)
                        : Optional.empty(),
                outcome,
                responseReceived,
                httpStatus,
                responseReceived ? OptionalLong.of(100 + unitId) : OptionalLong.empty(),
                responseReceived ? OptionalLong.of(1_000 + unitId) : OptionalLong.empty(),
                responseReceived ? OptionalLong.of(2_000 + unitId) : OptionalLong.empty(),
                false,
                parsingEligible ? Optional.of(parser(endpoint)) : Optional.empty(),
                schemaStatus,
                0,
                Optional.empty(),
                OptionalInt.empty());
    }

    private static J8BenchmarkReadEvidence.UnitEvidence withDeduplicatedResponse(
            J8BenchmarkReadEvidence.UnitEvidence source) {
        return new J8BenchmarkReadEvidence.UnitEvidence(
                source.campaignId(),
                source.campaignType(),
                source.executionMode(),
                source.unitId(),
                source.unitOrdinal(),
                source.endpoint(),
                source.canonicalEventId(),
                source.providerEventId(),
                source.declaredAt(),
                source.attemptId(),
                source.attemptStartedAt(),
                source.resolvedAt(),
                source.resolutionSource(),
                source.outcomeType(),
                source.responseReceived(),
                source.httpStatus(),
                source.latencyMillis(),
                source.snapshotId(),
                source.snapshotOccurrenceId(),
                true,
                source.parserVersion(),
                source.schemaStatus(),
                source.parserWarningCount(),
                source.completenessStatus(),
                source.completenessScore());
    }

    private static J8BenchmarkReadEvidence.LegacyResponseEvidence legacy(
            long occurrenceId,
            J6SnapshotOccurrenceOutcome persistenceOutcome,
            RawSnapshotSchemaStatus schemaStatus,
            long latencyMillis) {
        return legacy(
                occurrenceId,
                SofascoreEndpointType.EVENT_DETAILS,
                persistenceOutcome,
                schemaStatus,
                latencyMillis);
    }

    private static J8BenchmarkReadEvidence.LegacyResponseEvidence legacy(
            long occurrenceId,
            SofascoreEndpointType endpoint,
            J6SnapshotOccurrenceOutcome persistenceOutcome,
            RawSnapshotSchemaStatus schemaStatus,
            long latencyMillis) {
        return new J8BenchmarkReadEvidence.LegacyResponseEvidence(
                occurrenceId,
                4_000 + occurrenceId,
                schemaStatus == RawSnapshotSchemaStatus.PARSED
                        ? List.of(4_500 + occurrenceId)
                        : List.of(),
                endpoint,
                STARTED_AT.plusSeconds(occurrenceId),
                Optional.of(STARTED_AT.plusSeconds(occurrenceId)
                        .plusMillis(latencyMillis)),
                OptionalInt.of(200),
                OptionalLong.of(latencyMillis),
                Optional.of(parser(endpoint)),
                schemaStatus == RawSnapshotSchemaStatus.PARSED
                        || schemaStatus == RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE
                                ? Optional.of(schemaStatus)
                                : Optional.empty(),
                persistenceOutcome);
    }

    private static J8DirectObservationCohorts.HistoricalObservationCohort
            historicalCohort(
                    long occurrenceId,
                    J6SnapshotOccurrenceOutcome persistenceOutcome,
                    J5CompletenessStatus completenessStatus,
                    int presentSignals,
                    int expectedSignals,
                    String competition) {
        return new J8DirectObservationCohorts.HistoricalObservationCohort(
                occurrenceId,
                5_000 + occurrenceId,
                OptionalLong.of(6_000 + occurrenceId),
                OptionalLong.of(7_000 + occurrenceId),
                OptionalLong.of(8_000 + occurrenceId),
                persistenceOutcome,
                SofascoreEndpointType.EVENT_STATISTICS,
                STARTED_AT.plusSeconds(occurrenceId),
                Optional.of(completenessStatus),
                OptionalInt.of(presentSignals),
                OptionalInt.of(expectedSignals),
                true,
                Optional.of(competition),
                Optional.of("2026/27"),
                Optional.of("finished"));
    }

    private static J8BenchmarkReadEvidence.UnitEvidence unavailableUnit(
            long unitId,
            UUID campaignId) {
        return new J8BenchmarkReadEvidence.UnitEvidence(
                campaignId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                unitId,
                1,
                SofascoreEndpointType.EVENT_LINEUPS,
                Optional.of(EVENT_ID),
                OptionalLong.of(PROVIDER_EVENT_ID),
                STARTED_AT.plusSeconds(unitId),
                OptionalLong.of(700 + unitId),
                Optional.of(STARTED_AT.plusSeconds(unitId + 10)),
                Optional.of(STARTED_AT.plusSeconds(unitId + 20)),
                Optional.of(J8BenchmarkResolutionSource.PROVIDER),
                Optional.of(J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE),
                true,
                OptionalInt.of(404),
                OptionalLong.of(100),
                OptionalLong.of(800 + unitId),
                OptionalLong.of(900 + unitId),
                false,
                Optional.empty(),
                Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                0,
                Optional.of(J5CompletenessStatus.UNAVAILABLE),
                OptionalInt.of(0));
    }

    private static J8BenchmarkReadEvidence.UnitEvidence blockedScheduledPage(
            long unitId,
            UUID campaignId,
            int page) {
        return new J8BenchmarkReadEvidence.UnitEvidence(
                campaignId,
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                unitId,
                page,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                Optional.empty(),
                OptionalLong.empty(),
                STARTED_AT.plusSeconds(unitId),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.of(STARTED_AT.plusSeconds(unitId + 20)),
                Optional.of(J8BenchmarkResolutionSource.BLOCKED),
                Optional.of(J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE),
                false,
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty(),
                OptionalInt.empty());
    }

    private static J8DirectObservationCohorts.ObservationCohort unavailableCohort(
            long unitId) {
        return new J8DirectObservationCohorts.ObservationCohort(
                unitId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                PROVIDER_EVENT_ID,
                Optional.of(EVENT_ID),
                SofascoreEndpointType.EVENT_LINEUPS,
                true,
                true,
                Optional.of(J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE),
                Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                Optional.empty(),
                Optional.of(J5CompletenessStatus.UNAVAILABLE),
                OptionalInt.of(0),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                true,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.UNAVAILABLE,
                Optional.of(J5CompletenessStatus.COMPLETE),
                Optional.of(J5CompletenessStatus.COMPLETE),
                Optional.empty(),
                J8DirectObservationCohorts.CurrentDirectEvidence.none(),
                Optional.of("UEFA Champions League"),
                Optional.of("2026/27"),
                Optional.of("finished"));
    }

    private static J8DirectObservationCohorts.ObservationCohort currentDossierCohort(
            long unitId,
            J8DirectObservationCohorts.DirectComponentState currentLineups) {
        return currentDossierCohort(
                unitId,
                currentLineups,
                currentEvidence(20_000 + unitId * 100));
    }

    private static J8DirectObservationCohorts.ObservationCohort currentDossierCohort(
            long unitId,
            J8DirectObservationCohorts.DirectComponentState currentLineups,
            J8DirectObservationCohorts.CurrentDirectEvidence currentEvidence) {
        return new J8DirectObservationCohorts.ObservationCohort(
                unitId,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                PROVIDER_EVENT_ID,
                Optional.of(EVENT_ID),
                SofascoreEndpointType.EVENT_DETAILS,
                true,
                true,
                Optional.of(J8BenchmarkOutcomeType.PARSED),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                Optional.of("event-details-v2"),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                true,
                true,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                currentLineups,
                Optional.of(J5CompletenessStatus.COMPLETE),
                Optional.of(J5CompletenessStatus.COMPLETE),
                currentLineups == J8DirectObservationCohorts.DirectComponentState.AVAILABLE
                        ? Optional.of(J5CompletenessStatus.COMPLETE)
                        : Optional.empty(),
                currentEvidence,
                Optional.of("UEFA Champions League"),
                Optional.of("2026/27"),
                Optional.of("finished"));
    }

    private static J8DirectObservationCohorts.CurrentDirectEvidence currentEvidence(
            long baseId) {
        return new J8DirectObservationCohorts.CurrentDirectEvidence(
                OptionalLong.of(baseId),
                OptionalLong.of(baseId + 1),
                componentEvidence(baseId + 10),
                componentEvidence(baseId + 20),
                componentEvidence(baseId + 30),
                componentEvidence(baseId + 40));
    }

    private static J8DirectObservationCohorts.ObservationCohort
            withCurrentLineupsCompleteness(
                    J8DirectObservationCohorts.ObservationCohort source,
                    J5CompletenessStatus lineupsCompleteness) {
        return new J8DirectObservationCohorts.ObservationCohort(
                source.unitId(),
                source.campaignType(),
                source.providerEventId(),
                source.canonicalEventId(),
                source.endpoint(),
                source.providerAttempted(),
                source.declaredInWindow(),
                source.outcomeType(),
                source.schemaStatus(),
                source.parserVersion(),
                source.completenessStatus(),
                source.completenessScore(),
                source.presentSignals(),
                source.expectedSignals(),
                source.normalizedObservationPresent(),
                source.canonicalStatePresent(),
                source.currentCanonicalState(),
                source.currentEventDetails(),
                source.currentStatistics(),
                source.currentIncidents(),
                source.currentLineups(),
                source.currentStatisticsCompleteness(),
                source.currentIncidentsCompleteness(),
                Optional.of(lineupsCompleteness),
                source.currentEvidence(),
                source.competition(),
                source.season(),
                source.eventStatus());
    }

    private static J8DirectObservationCohorts.ObservationCohort withCurrentEvidence(
            J8DirectObservationCohorts.ObservationCohort source,
            J8DirectObservationCohorts.CurrentDirectEvidence currentEvidence) {
        return new J8DirectObservationCohorts.ObservationCohort(
                source.unitId(),
                source.campaignType(),
                source.providerEventId(),
                source.canonicalEventId(),
                source.endpoint(),
                source.providerAttempted(),
                source.declaredInWindow(),
                source.outcomeType(),
                source.schemaStatus(),
                source.parserVersion(),
                source.completenessStatus(),
                source.completenessScore(),
                source.presentSignals(),
                source.expectedSignals(),
                source.normalizedObservationPresent(),
                source.canonicalStatePresent(),
                source.currentCanonicalState(),
                source.currentEventDetails(),
                source.currentStatistics(),
                source.currentIncidents(),
                source.currentLineups(),
                source.currentStatisticsCompleteness(),
                source.currentIncidentsCompleteness(),
                source.currentLineupsCompleteness(),
                currentEvidence,
                source.competition(),
                source.season(),
                source.eventStatus());
    }

    private static J8DirectObservationCohorts.ComponentEvidence componentEvidence(
            long baseId) {
        return new J8DirectObservationCohorts.ComponentEvidence(
                OptionalLong.of(baseId),
                OptionalLong.of(baseId + 1),
                OptionalLong.of(baseId + 2));
    }

    private static J8DirectObservationCohorts.ObservationCohort weightedCohort(
            long unitId,
            SofascoreEndpointType endpoint,
            int presentSignals,
            int expectedSignals,
            Optional<String> competition,
            Optional<String> season,
            Optional<String> eventStatus) {
        int score = expectedSignals == 0
                ? 0
                : Math.floorDiv(presentSignals * 100, expectedSignals);
        return new J8DirectObservationCohorts.ObservationCohort(
                unitId,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                PROVIDER_EVENT_ID,
                Optional.of(EVENT_ID),
                endpoint,
                true,
                true,
                Optional.of(J8BenchmarkOutcomeType.PARSED),
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                Optional.of(parser(endpoint)),
                Optional.of(expectedSignals > 0 && presentSignals == expectedSignals
                        ? J5CompletenessStatus.COMPLETE
                        : J5CompletenessStatus.PARTIAL),
                OptionalInt.of(score),
                OptionalInt.of(presentSignals),
                OptionalInt.of(expectedSignals),
                true,
                true,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE,
                Optional.of(J5CompletenessStatus.PARTIAL),
                Optional.of(J5CompletenessStatus.PARTIAL),
                Optional.of(J5CompletenessStatus.PARTIAL),
                currentEvidence(30_000 + unitId * 100),
                competition,
                season,
                eventStatus);
    }

    private static String parser(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case SCHEDULED_EVENTS -> "scheduled-events-v1";
            case EVENT_DETAILS -> "event-details-v2";
            case EVENT_STATISTICS -> "event-statistics-v2";
            case EVENT_INCIDENTS -> "event-incidents-v14";
            case EVENT_LINEUPS -> "event-lineups-v2";
            default -> "benchmark-v1";
        };
    }
}
