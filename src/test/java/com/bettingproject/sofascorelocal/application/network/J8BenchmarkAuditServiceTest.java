package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaign;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnit;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8ProviderCallAttempt;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J8BenchmarkAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CAMPAIGN_ID = UUID.fromString(
            "f60ed763-76f4-45f2-894a-86254284cf64");
    private static final UUID EVENT_ID = UUID.fromString(
            "d025b796-ac11-4838-bced-009864999697");
    private static final String SHA256 = "a".repeat(64);

    @Test
    void persistsOneCompleteProviderUnitInTheRequiredOrder() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService service = new J8BenchmarkAuditService(store, CLOCK);

        J8BenchmarkAuditService.Session session = service.start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                1,
                Optional.empty());
        J8BenchmarkAuditService.Unit unit = session.declare(
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16691018",
                Optional.of(EVENT_ID),
                OptionalLong.of(16_691_018L));
        session.startAttempt(unit);
        session.captureResponse(unit, 200, 37L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-details-v2");
        session.resolve(
                unit,
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty());
        session.finish(J8BenchmarkCampaignTerminalState.COMPLETED, Optional.empty());

        InOrder order = inOrder(store);
        ArgumentCaptor<J8BenchmarkCampaign> campaign =
                ArgumentCaptor.forClass(J8BenchmarkCampaign.class);
        ArgumentCaptor<J8BenchmarkUnit> declaration =
                ArgumentCaptor.forClass(J8BenchmarkUnit.class);
        ArgumentCaptor<J8ProviderCallAttempt> attempt =
                ArgumentCaptor.forClass(J8ProviderCallAttempt.class);
        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        ArgumentCaptor<J8BenchmarkCampaignResult> finish =
                ArgumentCaptor.forClass(J8BenchmarkCampaignResult.class);
        order.verify(store).startCampaign(campaign.capture());
        order.verify(store).declareUnit(declaration.capture());
        order.verify(store).startProviderAttempt(attempt.capture());
        order.verify(store).recordUnitResult(result.capture());
        order.verify(store).finishCampaign(finish.capture());

        assertThat(campaign.getValue().startedAt()).isEqualTo(NOW);
        assertThat(declaration.getValue().declaredAt()).isEqualTo(NOW);
        assertThat(attempt.getValue())
                .isEqualTo(new J8ProviderCallAttempt(11L, NOW));
        assertThat(result.getValue().unitId()).isEqualTo(11L);
        assertThat(result.getValue().attemptId()).hasValue(21L);
        assertThat(result.getValue().responseReceived()).isTrue();
        assertThat(result.getValue().httpStatus()).hasValue(200);
        assertThat(result.getValue().latencyMillis()).hasValue(37L);
        assertThat(result.getValue().snapshotId()).hasValue(31L);
        assertThat(result.getValue().snapshotOccurrenceId()).hasValue(41L);
        assertThat(result.getValue().parserVersion()).contains("event-details-v2");
        assertThat(result.getValue().schemaStatus())
                .contains(RawSnapshotSchemaStatus.PARSED);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PARSED);
        assertThat(finish.getValue().terminalState())
                .isEqualTo(J8BenchmarkCampaignTerminalState.COMPLETED);
        assertThat(finish.getValue().completedUnits()).isEqualTo(1);
    }

    @Test
    void failedFinishResolvesAttemptBlockedAndRefusedUnitsWithoutInventingRetries() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L, 12L, 13L);
        when(store.startProviderAttempt(any())).thenReturn(21L, 22L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                3,
                Optional.empty());

        J8BenchmarkAuditService.Unit statistics = declareJ5(
                session, 1, SofascoreEndpointType.EVENT_STATISTICS);
        J8BenchmarkAuditService.Unit incidents = declareJ5(
                session, 2, SofascoreEndpointType.EVENT_INCIDENTS);
        J8BenchmarkAuditService.Unit lineups = declareJ5(
                session, 3, SofascoreEndpointType.EVENT_LINEUPS);
        session.startAttempt(statistics);
        session.startAttempt(lineups);
        session.captureResponse(lineups, 429, 51L);

        session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("TERMINAL_FAILURE"));

        ArgumentCaptor<J8BenchmarkUnitResult> results =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store, times(3)).recordUnitResult(results.capture());
        List<J8BenchmarkUnitResult> values = results.getAllValues();
        assertThat(values).extracting(J8BenchmarkUnitResult::unitId)
                .containsExactly(11L, 12L, 13L);
        assertThat(values).extracting(J8BenchmarkUnitResult::resolutionSource)
                .containsExactly(
                        J8BenchmarkResolutionSource.PROVIDER,
                        J8BenchmarkResolutionSource.BLOCKED,
                        J8BenchmarkResolutionSource.PROVIDER);
        assertThat(values).extracting(J8BenchmarkUnitResult::outcomeType)
                .containsExactly(
                        J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                        J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                        J8BenchmarkOutcomeType.HTTP_REFUSED);
        assertThat(values.get(0).attemptId()).hasValue(21L);
        assertThat(values.get(0).responseReceived()).isFalse();
        assertThat(values.get(1).attemptId()).isEmpty();
        assertThat(values.get(2).attemptId()).hasValue(22L);
        assertThat(values.get(2).httpStatus()).hasValue(429);
        assertThat(values).allSatisfy(value ->
                assertThat(value.terminalCode()).contains("TERMINAL_FAILURE"));

        ArgumentCaptor<J8BenchmarkCampaignResult> campaignResult =
                ArgumentCaptor.forClass(J8BenchmarkCampaignResult.class);
        verify(store).finishCampaign(campaignResult.capture());
        assertThat(campaignResult.getValue().completedUnits()).isEqualTo(3);
        assertThat(campaignResult.getValue().terminalState())
                .isEqualTo(J8BenchmarkCampaignTerminalState.FAILED);
    }

    @Test
    void cancelledFinishClassifiesAnOpenAttemptAsOperatorStop() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);

        session.finish(
                J8BenchmarkCampaignTerminalState.CANCELLED,
                Optional.of("OPERATOR_CANCELLED"));

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.PROVIDER);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.OPERATOR_STOP);
        assertThat(result.getValue().responseReceived()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "200, PROCESSING_FAILURE",
            "404, PROCESSING_FAILURE",
            "429, HTTP_REFUSED",
            "500, HTTP_ERROR"
    })
    void failedFinishClassifiesCapturedResponsesConservatively(
            int httpStatus,
            J8BenchmarkOutcomeType expectedOutcome) {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, httpStatus, 12L);

        session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("UNRESOLVED_RESPONSE"));

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().outcomeType()).isEqualTo(expectedOutcome);
        assertThat(result.getValue().responseReceived()).isTrue();
        assertThat(result.getValue().httpStatus()).hasValue(httpStatus);
        assertThat(result.getValue().schemaStatus()).isEmpty();
    }

    @Test
    void failedFinishPreservesAnUnresolvedCacheSnapshot() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.captureSnapshot(unit, cachedSnapshot(31L), "event-details-v2");

        session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("CACHE_PROCESSING_FAILED"));

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.CACHE);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PROCESSING_FAILURE);
        assertThat(result.getValue().attemptId()).isEmpty();
        assertThat(result.getValue().snapshotId()).hasValue(31L);
        assertThat(result.getValue().snapshotOccurrenceId()).isEmpty();
    }

    @Test
    void resolveFailureMapsAStored404ToEndpointUnavailable() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, 404, 19L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-details-v2");

        session.resolveFailure(unit, "ENDPOINT_RETURNED_404");

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.PROVIDER);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE);
        assertThat(result.getValue().schemaStatus())
                .contains(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
        assertThat(result.getValue().httpStatus()).hasValue(404);
        assertThat(result.getValue().snapshotId()).hasValue(31L);
    }

    @Test
    void resolveFailureAddsUnavailableCompletenessForAStoredJ5Response() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                3,
                Optional.empty());
        J8BenchmarkAuditService.Unit unit = declareJ5(
                session, 1, SofascoreEndpointType.EVENT_STATISTICS);
        session.startAttempt(unit);
        session.captureResponse(unit, 404, 19L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-statistics-v1");

        session.resolveFailure(unit, "ENDPOINT_RETURNED_404");

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE);
        assertThat(result.getValue().completenessStatus())
                .contains(J5CompletenessStatus.UNAVAILABLE);
        assertThat(result.getValue().completenessScore()).hasValue(0);
    }

    @Test
    void resolveFailurePreservesACacheSnapshotOnOperatorStop() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.captureSnapshot(unit, cachedSnapshot(31L), "event-details-v2");

        session.resolveFailure(unit, "OPERATOR_STOP");

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.CACHE);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.OPERATOR_STOP);
        assertThat(result.getValue().snapshotId()).hasValue(31L);
        assertThat(result.getValue().snapshotOccurrenceId()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEMA_INCOMPATIBLE, SCHEMA_INCOMPATIBLE, SCHEMA_INCOMPATIBLE",
            "CACHE_REPARSE_INCOMPATIBLE, SCHEMA_INCOMPATIBLE, SCHEMA_INCOMPATIBLE",
            "EVENT_ID_MISMATCH, SCHEMA_INCOMPATIBLE, SCHEMA_INCOMPATIBLE",
            "UNEXPECTED_CONTENT, UNEXPECTED_CONTENT, UNEXPECTED_CONTENT"
    })
    void resolveFailureCentralizesParserAndSchemaCodes(
            String terminalCode,
            J8BenchmarkOutcomeType expectedOutcome,
            RawSnapshotSchemaStatus expectedSchema) {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, 200, 19L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-details-v2");

        session.resolveFailure(unit, terminalCode);

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().outcomeType()).isEqualTo(expectedOutcome);
        assertThat(result.getValue().schemaStatus()).contains(expectedSchema);
        assertThat(result.getValue().terminalCode()).contains(terminalCode);
    }

    @ParameterizedTest
    @CsvSource({"PARSER_FAILURE", "PROJECTION_FAILURE"})
    void genericParserAndProjectionExceptionsRemainProcessingFailures(
            String terminalCode) {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, 200, 19L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-details-v2");

        session.resolveFailure(unit, terminalCode);

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PROCESSING_FAILURE);
        assertThat(result.getValue().schemaStatus()).isEmpty();
        assertThat(result.getValue().terminalCode()).contains(terminalCode);
    }

    @ParameterizedTest
    @CsvSource({
            "RAW_SNAPSHOT_PERSISTENCE_FAILURE",
            "RAW_PAYLOAD_PERSISTENCE_FAILED",
            "RAW_CLASSIFICATION_ERROR",
            "NORMALIZATION_PERSISTENCE_FAILURE",
            "CACHE_WRITE_ERROR"
    })
    void resolveFailureCentralizesPersistenceCodes(String terminalCode) {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, 200, 19L);

        session.resolveFailure(unit, terminalCode);

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.PROVIDER);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PERSISTENCE_FAILURE);
        assertThat(result.getValue().responseReceived()).isTrue();
        assertThat(result.getValue().snapshotId()).isEmpty();
    }

    @Test
    void localImportPersistenceFailureRemainsReachedWithoutInventingASnapshot() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT,
                1,
                Optional.of(LocalDate.of(2026, 8, 29)));
        J8BenchmarkAuditService.Unit unit = session.declare(
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-29|uniqueTournamentId=17",
                Optional.empty(),
                OptionalLong.empty());

        session.reach(unit);
        session.resolveFailure(unit, "LOCAL_IMPORT_PERSISTENCE_ERROR");
        session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("LOCAL_IMPORT_PERSISTENCE_ERROR"));

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PERSISTENCE_FAILURE);
        assertThat(result.getValue().snapshotId()).isEmpty();
        assertThat(result.getValue().snapshotOccurrenceId()).isEmpty();
    }

    @Test
    void explicitNotReachedResolutionDoesNotTurnFutureUnitsIntoOperatorStops() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                3,
                Optional.empty());
        J8BenchmarkAuditService.Unit unit = declareJ5(
                session, 2, SofascoreEndpointType.EVENT_INCIDENTS);

        session.resolveNotReached(unit, "OPERATOR_STOP");
        session.finish(
                J8BenchmarkCampaignTerminalState.CANCELLED,
                Optional.of("OPERATOR_STOP"));

        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.BLOCKED);
        assertThat(result.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE);
    }

    @Test
    void failedFinishDistinguishesTheReachedUnitFromFutureUnitsWithoutEvidence() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L, 12L, 13L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                3,
                Optional.empty());
        J8BenchmarkAuditService.Unit statistics = declareJ5(
                session, 1, SofascoreEndpointType.EVENT_STATISTICS);
        declareJ5(session, 2, SofascoreEndpointType.EVENT_INCIDENTS);
        declareJ5(session, 3, SofascoreEndpointType.EVENT_LINEUPS);
        session.reach(statistics);

        session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("CACHE_READ_ERROR"));

        ArgumentCaptor<J8BenchmarkUnitResult> results =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store, times(3)).recordUnitResult(results.capture());
        assertThat(results.getAllValues()).extracting(J8BenchmarkUnitResult::outcomeType)
                .containsExactly(
                        J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                        J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                        J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE);
        assertThat(results.getAllValues()).extracting(J8BenchmarkUnitResult::resolutionSource)
                .containsOnly(J8BenchmarkResolutionSource.BLOCKED);
    }

    @Test
    void conditionalFailureResolutionDoesNotWriteASecondTerminalResult() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.resolveFailure(unit, "TRANSPORT_IO_FAILURE");

        assertThat(session.resolveFailureIfPending(unit, "TRANSPORT_IO_FAILURE")).isFalse();

        verify(store, times(1)).recordUnitResult(any());
    }

    @Test
    void resolveFailureDistinguishesTransportOperatorAndNotReached() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L, 12L, 13L);
        when(store.startProviderAttempt(any())).thenReturn(21L, 22L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                3,
                Optional.empty());
        J8BenchmarkAuditService.Unit statistics = declareJ5(
                session, 1, SofascoreEndpointType.EVENT_STATISTICS);
        J8BenchmarkAuditService.Unit incidents = declareJ5(
                session, 2, SofascoreEndpointType.EVENT_INCIDENTS);
        J8BenchmarkAuditService.Unit lineups = declareJ5(
                session, 3, SofascoreEndpointType.EVENT_LINEUPS);
        session.startAttempt(statistics);
        session.startAttempt(lineups);

        session.resolveFailure(statistics, "TRANSPORT_ABORTED");
        session.resolveFailure(incidents, "PREVIOUS_UNIT_TERMINAL_FAILURE");
        session.resolveFailure(lineups, "OPERATOR_STOP");
        session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("CAMPAIGN_FAILED"));

        ArgumentCaptor<J8BenchmarkUnitResult> results =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store, times(3)).recordUnitResult(results.capture());
        assertThat(results.getAllValues()).extracting(J8BenchmarkUnitResult::resolutionSource)
                .containsExactly(
                        J8BenchmarkResolutionSource.PROVIDER,
                        J8BenchmarkResolutionSource.BLOCKED,
                        J8BenchmarkResolutionSource.PROVIDER);
        assertThat(results.getAllValues()).extracting(J8BenchmarkUnitResult::outcomeType)
                .containsExactly(
                        J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                        J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                        J8BenchmarkOutcomeType.OPERATOR_STOP);
    }

    @Test
    void completedFinishRejectsPendingUnitsBeforeWritingAnyInventedResult() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        declareDetails(session);

        assertThatThrownBy(() -> session.finish(
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a completed benchmark campaign cannot contain pending units");

        verify(store, never()).recordUnitResult(any());
        verify(store, never()).finishCampaign(any());
    }

    @Test
    void aProviderUnitCannotBeRetried() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);

        assertThatThrownBy(() -> session.startAttempt(unit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a benchmark unit cannot be retried");

        verify(store, times(1)).startProviderAttempt(any());
    }

    @Test
    void aStoreFailurePoisonsTheSessionAndCannotBeRetriedOrClosed() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);

        assertThatThrownBy(() -> session.startAttempt(unit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThatThrownBy(() -> session.startAttempt(unit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "benchmark audit session is unusable after an audit failure");
        assertThatThrownBy(() -> session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("DATABASE_FAILURE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "benchmark audit session is unusable after an audit failure");

        verify(store, times(1)).startProviderAttempt(any());
        verify(store, never()).finishCampaign(any());
    }

    @Test
    void aPostAttemptAuditValidationFailurePoisonsEveryFollowingOperation() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        when(store.startProviderAttempt(any())).thenReturn(21L);
        J8BenchmarkAuditService.Session session = providerDetailsSession(store);
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, 200, 5L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-details-v2");

        assertThatThrownBy(() -> session.resolve(
                unit,
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                Optional.empty(),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.of("INVALID_AUDIT_RESULT")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> declareDetails(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "benchmark audit session is unusable after an audit failure");
        assertThatThrownBy(() -> session.resolveFailure(unit, "PROCESSING_FAILURE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "benchmark audit session is unusable after an audit failure");
        assertThatThrownBy(() -> session.finish(
                J8BenchmarkCampaignTerminalState.FAILED,
                Optional.of("INVALID_AUDIT_RESULT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "benchmark audit session is unusable after an audit failure");

        verify(store, never()).recordUnitResult(any());
        verify(store, never()).finishCampaign(any());
    }

    @Test
    void disabledAuditKeepsExistingTestsIndependentFromPersistence() {
        J8BenchmarkAuditService.Session session = J8BenchmarkAuditService.disabled(CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                1,
                Optional.empty());
        J8BenchmarkAuditService.Unit unit = declareDetails(session);
        session.startAttempt(unit);
        session.captureResponse(unit, 200, 5L);
        session.captureSnapshot(unit, insertedSnapshot(31L, 41L), "event-details-v2");
        session.resolve(
                unit,
                J8BenchmarkResolutionSource.PROVIDER,
                J8BenchmarkOutcomeType.PARSED,
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty());

        session.finish(J8BenchmarkCampaignTerminalState.COMPLETED, Optional.empty());

        assertThatThrownBy(() -> session.finish(
                J8BenchmarkCampaignTerminalState.COMPLETED,
                Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("benchmark audit session is already finished");
    }

    @Test
    void tournamentDiscoveryImportRecordsAResponseOccurrenceWithoutProviderAttempt() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY,
                J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT,
                1,
                Optional.of(LocalDate.of(2026, 8, 29)));
        J8BenchmarkAuditService.Unit unit = session.declare(
                1,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-29|uniqueTournamentId=17",
                Optional.empty(),
                OptionalLong.empty());
        session.captureSnapshot(
                unit,
                insertedSnapshot(31L, 41L),
                "tournament-scheduled-events-v1");

        session.resolve(
                unit,
                J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT,
                J8BenchmarkOutcomeType.PARSED,
                Optional.of(RawSnapshotSchemaStatus.PARSED),
                0,
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty());
        session.finish(J8BenchmarkCampaignTerminalState.COMPLETED, Optional.empty());

        verify(store, never()).startProviderAttempt(any());
        ArgumentCaptor<J8BenchmarkUnitResult> result =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(store).recordUnitResult(result.capture());
        assertThat(result.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(result.getValue().attemptId()).isEmpty();
        assertThat(result.getValue().snapshotOccurrenceId()).hasValue(41L);
    }

    @Test
    void declarationsMatchJ3CampaignDateAndTheFixedPhase1Targets() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService service = new J8BenchmarkAuditService(store, CLOCK);
        J8BenchmarkAuditService.Session scheduled = service.start(
                UUID.randomUUID(),
                J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                25,
                Optional.of(LocalDate.of(2026, 8, 29)));

        assertThatThrownBy(() -> scheduled.declare(
                1,
                SofascoreEndpointType.SCHEDULED_EVENTS,
                "SCHEDULED_EVENTS|date=2026-08-28|page=1",
                Optional.empty(),
                OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("campaign date and ordinal");

        J8BenchmarkAuditService.Session phase1 = service.start(
                UUID.randomUUID(),
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                2,
                Optional.empty());
        assertThatThrownBy(() -> phase1.declare(
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16691018",
                Optional.empty(),
                OptionalLong.of(16_691_018L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed allowlist");
        phase1.declare(
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16386245",
                Optional.empty(),
                OptionalLong.of(16_386_245L));
        assertThatThrownBy(() -> phase1.declare(
                2,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16386245",
                Optional.empty(),
                OptionalLong.of(16_386_245L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("targets must be distinct");
    }

    @Test
    void j5DeclarationsKeepFixedFamiliesAndOneCanonicalEvent() {
        J8BenchmarkEvidenceStore store = mock(J8BenchmarkEvidenceStore.class);
        when(store.declareUnit(any())).thenReturn(11L);
        J8BenchmarkAuditService.Session session = new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J5_EVENT_DATA,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                3,
                Optional.empty());

        assertThatThrownBy(() -> session.declare(
                1,
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=16691018",
                Optional.of(EVENT_ID),
                OptionalLong.of(16_691_018L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed endpoint families");
        declareJ5(session, 1, SofascoreEndpointType.EVENT_STATISTICS);
        assertThatThrownBy(() -> session.declare(
                2,
                SofascoreEndpointType.EVENT_INCIDENTS,
                "EVENT_INCIDENTS|eventId=16691019",
                Optional.of(UUID.randomUUID()),
                OptionalLong.of(16_691_019L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same canonical event");
    }

    private static J8BenchmarkAuditService.Session providerDetailsSession(
            J8BenchmarkEvidenceStore store) {
        return new J8BenchmarkAuditService(store, CLOCK).start(
                CAMPAIGN_ID,
                J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2,
                J8BenchmarkExecutionMode.GUARDED_PROVIDER,
                1,
                Optional.empty());
    }

    private static J8BenchmarkAuditService.Unit declareDetails(
            J8BenchmarkAuditService.Session session) {
        return session.declare(
                1,
                SofascoreEndpointType.EVENT_DETAILS,
                "EVENT_DETAILS|eventId=16691018",
                Optional.of(EVENT_ID),
                OptionalLong.of(16_691_018L));
    }

    private static J8BenchmarkAuditService.Unit declareJ5(
            J8BenchmarkAuditService.Session session,
            int ordinal,
            SofascoreEndpointType endpoint) {
        return session.declare(
                ordinal,
                endpoint,
                endpoint.name() + "|eventId=16691018",
                Optional.of(EVENT_ID),
                OptionalLong.of(16_691_018L));
    }

    private static RawSnapshotPersistenceResult insertedSnapshot(
            long snapshotId,
            long occurrenceId) {
        return new RawSnapshotPersistenceResult(
                snapshotId,
                RawSnapshotPersistenceOutcome.INSERTED,
                SHA256,
                128,
                OptionalLong.of(occurrenceId));
    }

    private static RawSnapshotPersistenceResult cachedSnapshot(long snapshotId) {
        return new RawSnapshotPersistenceResult(
                snapshotId,
                RawSnapshotPersistenceOutcome.CACHE_HIT,
                SHA256,
                128,
                OptionalLong.empty());
    }
}
