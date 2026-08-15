package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
    private RawManualCallSnapshotStore rawStore;
    private J4ParsedEventDetailsPersistenceService parsedPersistence;
    private List<Duration> pauses;
    private J4RealEventDetailsPhase2Service service;

    @BeforeEach
    void setUp() {
        control = mock(J4RealPhase2ControlService.class);
        transport = mock(EventDetailsProviderTransport.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        parsedPersistence = mock(J4ParsedEventDetailsPersistenceService.class);
        pauses = new ArrayList<>();
        when(control.executionMayContinue(any())).thenReturn(true);
        AtomicLong snapshotIds = new AtomicLong(200L);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            var snapshot = (com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot)
                    invocation.getArgument(0);
            return new RawSnapshotPersistenceResult(
                    snapshotIds.incrementAndGet(),
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
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
                new EventDetailsV2Parser(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(3),
                pauses::add);
    }

    @Test
    void persistsRawBeforeParsingAndPerformsExactlyOneProviderCall() {
        EventDetailsProviderRequest request = request();
        when(transport.execute(any())).thenReturn(
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
        verify(transport, times(1)).execute(any());
        verify(control).recordEventCompleted(FIRST_REQUEST_ID, EVENT_ID);
        verify(control).complete(FIRST_REQUEST_ID);

        InOrder persistenceOrder = inOrder(rawStore, parsedPersistence);
        persistenceOrder.verify(rawStore).save(any());
        persistenceOrder.verify(parsedPersistence).persistParsed(any(), any(), any(), any());
    }

    @Test
    void aSecondConfirmedCampaignForTheSameEventForcesAnotherProviderSnapshot() {
        EventDetailsProviderRequest request = request();
        when(transport.execute(any())).thenReturn(
                response(request, 200, nominal(request.eventId(), "inprogress")));

        var first = service.execute(claim(FIRST_REQUEST_ID));
        var second = service.execute(claim(SECOND_REQUEST_ID));

        assertThat(first.completed()).isTrue();
        assertThat(second.completed()).isTrue();
        assertThat(first.events().getFirst().snapshotId())
                .isNotEqualTo(second.events().getFirst().snapshotId());
        verify(transport, times(2)).execute(any());
        verify(rawStore, times(2)).save(any());
        verify(parsedPersistence, times(2)).persistParsed(any(), any(), any(), any());
        assertThat(pauses).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void stopsOnTheFirst429WithoutRetryOrNormalization() {
        when(transport.execute(any())).thenReturn(response(
                request(),
                429,
                "{\"error\":\"rate-limited\"}"));

        var result = service.execute(claim(FIRST_REQUEST_ID));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("HTTP_429");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(transport, times(1)).execute(any());
        verify(rawStore).classify(201L, RawSnapshotSchemaStatus.TRANSPORT_ERROR, "HTTP_429");
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control).fail(FIRST_REQUEST_ID, "HTTP_429");
    }

    private static J4RealPhase2ExecutionClaim claim(UUID requestId) {
        return new J4RealPhase2ExecutionClaim(
                requestId,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
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
}
