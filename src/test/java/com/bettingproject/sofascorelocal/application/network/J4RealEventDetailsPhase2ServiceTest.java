package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV4Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.EventDetailsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.EventDetailsTransportFailure;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J4RealEventDetailsPhase2ServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final UUID FIRST_REQUEST_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000004");
    private static final UUID SECOND_REQUEST_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000005");
    private static final long EVENT_ID = 17000001L;

    private J4RealPhase2ControlService control;
    private EventDetailsProviderTransport transport;
    private EventDetailsProviderTransport.Campaign providerCampaign;
    private RawManualCallSnapshotStore rawStore;
    private J4ParsedEventDetailsPersistenceService parsedPersistence;
    private List<Duration> pauses;
    private MutableClock clock;
    private J4RealEventDetailsPhase2Service service;

    @BeforeEach
    void setUp() {
        control = mock(J4RealPhase2ControlService.class);
        transport = mock(EventDetailsProviderTransport.class);
        providerCampaign = mock(EventDetailsProviderTransport.Campaign.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        parsedPersistence = mock(J4ParsedEventDetailsPersistenceService.class);
        pauses = new ArrayList<>();
        clock = new MutableClock(NOW);
        when(control.executionMayContinue(any())).thenReturn(true);
        when(transport.openCampaign(any())).thenReturn(providerCampaign);
        AtomicLong snapshotIds = new AtomicLong(200L);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            var snapshot = (com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot)
                    invocation.getArgument(0);
            long snapshotId = snapshotIds.incrementAndGet();
            return new RawSnapshotPersistenceResult(
                    snapshotId,
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(snapshotId));
        });
        when(parsedPersistence.persistParsed(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    EventDetailsProviderRequest request = invocation.getArgument(0);
                    return new J4ParsedEventDetailsPersistenceResult(
                            CanonicalEventIdentity.sofascore(request.eventId()).value(),
                            request.eventId(),
                            request.eventId() + 1,
                            true,
                            true);
                });
        service = new J4RealEventDetailsPhase2Service(
                control,
                transport,
                rawStore,
                parsedPersistence,
                new EventDetailsV4Parser(),
                clock,
                Duration.ofSeconds(3),
                this::pauseAndAdvance);
    }

    @Test
    void persistsRawBeforeParsingAndPerformsExactlyOneProviderCall() {
        EventDetailsProviderRequest request = request();
        when(providerCampaign.execute(any())).thenReturn(
                response(request, 200, nominal(request.eventId(), "inprogress")));

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isTrue();
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.events()).singleElement()
                .satisfies(event -> {
                    assertThat(event.eventId()).isEqualTo(EVENT_ID);
                    assertThat(event.resolutionSource())
                            .isEqualTo(J4RealEventDetailsResolutionSource.PROVIDER);
                    assertThat(event.eventObservationInserted()).isTrue();
                });
        verify(transport).openCampaign(FIRST_REQUEST_ID);
        verify(providerCampaign, times(1)).execute(any());
        verify(providerCampaign).close();
        verify(control).recordEventCompleted(FIRST_REQUEST_ID, EVENT_ID);
        verify(control).complete(FIRST_REQUEST_ID);

        InOrder persistenceOrder = inOrder(rawStore, parsedPersistence);
        persistenceOrder.verify(rawStore).save(any());
        persistenceOrder.verify(parsedPersistence).persistParsed(any(), any(), any(), any());
    }

    @Test
    void aSecondConfirmedCampaignForTheSameEventForcesAnotherProviderSnapshot() {
        EventDetailsProviderRequest request = request();
        when(providerCampaign.execute(any())).thenReturn(
                response(request, 200, nominal(request.eventId(), "inprogress")));

        var first = service.execute(claim(FIRST_REQUEST_ID));
        var second = service.execute(claim(SECOND_REQUEST_ID));

        assertThat(first.completed()).isTrue();
        assertThat(second.completed()).isTrue();
        assertThat(first.events().getFirst().snapshotId())
                .isNotEqualTo(second.events().getFirst().snapshotId());
        verify(transport, times(2)).openCampaign(any());
        verify(providerCampaign, times(2)).execute(any());
        verify(providerCampaign, times(2)).close();
        verify(rawStore, times(2)).save(any());
        verify(parsedPersistence, times(2)).persistParsed(any(), any(), any(), any());
        assertThat(pauses).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void stopsOnTheFirst429WithoutRetryOrNormalization() {
        when(providerCampaign.execute(any())).thenReturn(response(
                request(),
                429,
                "{\"error\":\"rate-limited\"}"));

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("HTTP_429");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(providerCampaign, times(1)).execute(any());
        verify(rawStore).classify(201L, RawSnapshotSchemaStatus.TRANSPORT_ERROR, "HTTP_429");
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control).fail(FIRST_REQUEST_ID, "HTTP_429");
    }

    @Test
    void reportsCleanupFailureInsteadOfMaskingAProviderTransportFailure() {
        when(providerCampaign.execute(any())).thenThrow(
                new EventDetailsTransportException(EventDetailsTransportFailure.TIMEOUT));
        doThrow(new EventDetailsTransportException(EventDetailsTransportFailure.IO_FAILURE))
                .when(providerCampaign).close();

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("TRANSPORT_IO_FAILURE");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(rawStore, never()).save(any());
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control).fail(FIRST_REQUEST_ID, "TRANSPORT_IO_FAILURE");
    }

    @Test
    void keepsADeduplicated404OccurrenceWithoutReclassifyingHistoricalEvidence() {
        when(providerCampaign.execute(any())).thenReturn(response(
                request(),
                404,
                "{\"error\":\"not-found\"}"));
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"error\":\"not-found\"}".getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.doReturn(new RawSnapshotPersistenceResult(
                201L,
                RawSnapshotPersistenceOutcome.DEDUPLICATED,
                payload.sha256(),
                payload.sizeBytes(),
                java.util.OptionalLong.of(202L))).when(rawStore).save(any());
        doThrow(new IllegalStateException("classification divergence"))
                .when(rawStore).classify(
                        201L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED_UNAVAILABLE");
        assertThat(result.unavailableEvents())
                .extracting(J4RealEventDetailsUnavailableResult::eventId)
                .containsExactly(EVENT_ID);
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(rawStore, never()).classify(
                201L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);
        verify(control).completeUnavailable(FIRST_REQUEST_ID);
    }

    @Test
    void aFailedAttemptAuditWritePreventsTheProviderCall() {
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(11L);
        when(evidenceStore.startProviderAttempt(any()))
                .thenThrow(new IllegalStateException("audit unavailable"));
        service = auditedService(evidenceStore);

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("BENCHMARK_AUDIT_FAILURE");
        assertThat(result.providerCallAttempts()).isZero();
        verify(providerCampaign, never()).execute(any());
        verify(control).fail(FIRST_REQUEST_ID, "BENCHMARK_AUDIT_FAILURE");
    }

    @Test
    void aFailedResultAuditWriteLocksTheCompletedUnit() {
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(11L);
        when(evidenceStore.startProviderAttempt(any())).thenReturn(21L);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(evidenceStore).recordUnitResult(any(J8BenchmarkUnitResult.class));
        service = auditedService(evidenceStore);
        when(providerCampaign.execute(any())).thenReturn(response(
                request(), 200, nominal(EVENT_ID, "inprogress")));

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("BENCHMARK_AUDIT_FAILURE");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(providerCampaign, times(1)).execute(any());
        verify(evidenceStore, times(1)).recordUnitResult(any());
        verify(control).fail(FIRST_REQUEST_ID, "BENCHMARK_AUDIT_FAILURE");
    }

    @Test
    void promotesAnEventIdMismatchClassificationFailureToRawClassificationError() {
        when(providerCampaign.execute(any())).thenReturn(response(
                request(),
                200,
                nominal(EVENT_ID + 1, "inprogress")));
        doThrow(new IllegalStateException("classification divergence"))
                .when(rawStore).classify(
                        201L,
                        RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                        "SCHEMA_INCOMPATIBLE");

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("RAW_CLASSIFICATION_ERROR");
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control).fail(FIRST_REQUEST_ID, "RAW_CLASSIFICATION_ERROR");
    }

    @Test
    void completesAnExact404AsUnavailableWithoutParsingOrRetrying() {
        when(providerCampaign.execute(any())).thenReturn(response(
                request(),
                404,
                "{\"error\":\"not-found\"}"));

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED_UNAVAILABLE");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.events()).isEmpty();
        assertThat(result.unavailableEvents()).singleElement().satisfies(unavailable -> {
            assertThat(unavailable.eventId()).isEqualTo(EVENT_ID);
            assertThat(unavailable.httpStatus()).isEqualTo(404);
            assertThat(unavailable.schemaStatus())
                    .isEqualTo(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
        });
        verify(rawStore).classify(
                201L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(providerCampaign, times(1)).execute(any());
        verify(control).recordEventCompleted(FIRST_REQUEST_ID, EVENT_ID);
        verify(control).completeUnavailable(FIRST_REQUEST_ID);
    }

    @Test
    void persistsTheRawResponseThenReportsCampaignCleanupFailure() {
        when(providerCampaign.execute(any())).thenReturn(
                response(request(), 200, nominal(EVENT_ID, "inprogress")));
        doThrow(new EventDetailsTransportException(EventDetailsTransportFailure.IO_FAILURE))
                .when(providerCampaign).close();

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("TRANSPORT_IO_FAILURE");
        verify(rawStore).save(any());
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control).fail(FIRST_REQUEST_ID, "TRANSPORT_IO_FAILURE");
    }

    @Test
    void preservesCleanupFailureWhenAnOperatorStopWinsTheControlRace() {
        when(providerCampaign.execute(any())).thenReturn(
                response(request(), 200, nominal(EVENT_ID, "inprogress")));
        when(control.executionMayContinue(FIRST_REQUEST_ID)).thenReturn(
                true, true, true, true, false);
        doThrow(new EventDetailsTransportException(EventDetailsTransportFailure.IO_FAILURE))
                .when(providerCampaign).close();

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("TRANSPORT_IO_FAILURE");
        verify(rawStore).save(any());
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control, never()).fail(any(), any());
    }

    private J4RealEventDetailsPhase2Service auditedService(
            J8BenchmarkEvidenceStore evidenceStore) {
        return new J4RealEventDetailsPhase2Service(
                control,
                transport,
                rawStore,
                parsedPersistence,
                new EventDetailsV4Parser(),
                clock,
                Duration.ofSeconds(3),
                this::pauseAndAdvance,
                new J8BenchmarkAuditService(
                        evidenceStore, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private void pauseAndAdvance(Duration duration) {
        pauses.add(duration);
        clock.advance(duration);
    }

    private static J4RealPhase2ExecutionClaim claim(UUID requestId) {
        return new J4RealPhase2ExecutionClaim(
                requestId,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                CanonicalEventIdentity.sofascore(EVENT_ID).value(),
                EVENT_ID);
    }

    private static EventDetailsProviderRequest request() {
        return EventDetailsProviderRequest.phase2(
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                EVENT_ID);
    }

    private static EventDetailsTransportResponse response(
            EventDetailsProviderRequest request,
            int status,
            String body) {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                body.getBytes(StandardCharsets.UTF_8));
        return new EventDetailsTransportResponse(
                request.requestKey(),
                NOW,
                NOW.plusMillis(250),
                status,
                "application/json; charset=utf-8",
                Duration.ofMillis(250),
                payload);
    }

    private static String nominal(long eventId, String status) {
        return """
                {
                  "event": {
                    "id": %d,
                    "startTimestamp": 1786793400,
                    "homeTeam": {"id": 21, "name": "Parameterized Home"},
                    "awayTeam": {"id": 22, "name": "Parameterized Away"},
                    "status": {"type": "%s", "description": "In progress"},
                    "tournament": {"id": 23, "name": "Parameterized League"},
                    "season": {"id": 24, "name": "2026"},
                    "roundInfo": {"round": 1}
                  }
                }
                """.formatted(eventId, status);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock is UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
