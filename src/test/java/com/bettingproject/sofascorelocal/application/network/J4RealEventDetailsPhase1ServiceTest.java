package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV2Parser;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.J4CachedEventDetails;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J4RealEventDetailsPhase1ServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000004");

    private J4RealPhase1ControlService control;
    private EventDetailsProviderTransport transport;
    private RawManualCallSnapshotStore rawStore;
    private J4EventDetailsCache cache;
    private J4ParsedEventDetailsPersistenceService parsedPersistence;
    private List<Duration> pauses;
    private J4RealEventDetailsPhase1Service service;

    @BeforeEach
    void setUp() {
        control = mock(J4RealPhase1ControlService.class);
        transport = mock(EventDetailsProviderTransport.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        cache = mock(J4EventDetailsCache.class);
        parsedPersistence = mock(J4ParsedEventDetailsPersistenceService.class);
        pauses = new ArrayList<>();
        when(control.executionMayContinue(REQUEST_ID)).thenReturn(true);
        when(cache.findFreshParsed(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(rawStore.save(any())).thenAnswer(invocation -> {
            var snapshot = (com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot)
                    invocation.getArgument(0);
            long snapshotId = snapshot.requestKey().endsWith("16386245") ? 101L : 102L;
            return new RawSnapshotPersistenceResult(
                    snapshotId,
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
        service = new J4RealEventDetailsPhase1Service(
                control,
                transport,
                rawStore,
                cache,
                parsedPersistence,
                new EventDetailsV2Parser(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                Duration.ofSeconds(3),
                pauses::add);
    }

    @Test
    void persistsEachRawResponseBeforeParsingAndCompletesExactlyTwoEvents() {
        when(transport.execute(any())).thenAnswer(invocation -> {
            EventDetailsProviderRequest request = invocation.getArgument(0);
            return response(request, 200, nominal(request.eventId()));
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(2);
        assertThat(result.cacheHits()).isZero();
        assertThat(result.events())
                .extracting(J4RealEventDetailsEventResult::eventId)
                .containsExactly(16386245L, 16421052L);
        assertThat(pauses).containsExactly(Duration.ofSeconds(3));
        verify(transport, times(2)).execute(any());
        verify(control).recordEventCompleted(REQUEST_ID, 16386245L);
        verify(control).recordEventCompleted(REQUEST_ID, 16421052L);
        verify(control).complete(REQUEST_ID);

        InOrder persistenceOrder = inOrder(rawStore, parsedPersistence);
        persistenceOrder.verify(rawStore).save(any());
        persistenceOrder.verify(parsedPersistence).persistParsed(any(), any(), any(), any());
        persistenceOrder.verify(rawStore).save(any());
        persistenceOrder.verify(parsedPersistence).persistParsed(any(), any(), any(), any());
    }

    @Test
    void usesFreshParsedCacheBeforeAnyProviderTransport() {
        when(cache.findFreshParsed(any(), any(), any(), any())).thenAnswer(invocation -> {
            EventDetailsProviderRequest request = invocation.getArgument(0);
            RawPayloadEvidence payload = RawPayloadEvidence.capture(
                    nominal(request.eventId()).getBytes(StandardCharsets.UTF_8));
            long snapshotId = request.eventId() == 16386245L ? 101L : 102L;
            return Optional.of(new J4CachedEventDetails(
                    snapshotId,
                    request.requestKey(),
                    NOW.plusMillis(250),
                    NOW,
                    NOW.plusMillis(250),
                    200,
                    "application/json; charset=utf-8",
                    Duration.ofMillis(250),
                    payload,
                    EventDetailsV2Parser.PARSER_VERSION));
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.providerCallAttempts()).isZero();
        assertThat(result.cacheHits()).isEqualTo(2);
        assertThat(result.events())
                .extracting(J4RealEventDetailsEventResult::resolutionSource)
                .containsOnly(J4RealEventDetailsResolutionSource.CACHE);
        assertThat(pauses).isEmpty();
        verify(transport, never()).execute(any());
        verify(rawStore, never()).save(any());
        verify(parsedPersistence, times(2)).persistParsed(any(), any(), any(), any());
    }

    @Test
    void stopsAfterTheFirst429WithoutRetryingOrCallingTheSecondEvent() {
        when(transport.execute(any())).thenAnswer(invocation -> response(
                invocation.getArgument(0),
                429,
                "{\"error\":\"rate-limited\"}"));

        var result = service.execute(claim());

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("HTTP_429");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.events()).isEmpty();
        assertThat(pauses).isEmpty();
        verify(transport, times(1)).execute(any());
        verify(rawStore).classify(101L, RawSnapshotSchemaStatus.TRANSPORT_ERROR, "HTTP_429");
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
        verify(control).fail(REQUEST_ID, "HTTP_429");
    }

    @Test
    void keepsTheFirstRawSnapshotAndStopsOnSchemaIncompatibility() {
        when(transport.execute(any())).thenAnswer(invocation -> response(
                invocation.getArgument(0),
                200,
                "{\"event\":{}}"));

        var result = service.execute(claim());

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(transport, times(1)).execute(any());
        verify(rawStore).classify(
                101L,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(parsedPersistence, never()).persistParsed(any(), any(), any(), any());
    }

    private static J4RealPhase1ExecutionClaim claim() {
        return new J4RealPhase1ExecutionClaim(
                REQUEST_ID,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN));
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

    private static String nominal(long eventId) {
        String home = eventId == 16386245L ? "Saint-Etienne" : "Sevilla";
        String away = eventId == 16386245L ? "Clermont Foot" : "Rayo Vallecano";
        return """
                {
                  "event": {
                    "id": %d,
                    "startTimestamp": 1786793400,
                    "homeTeam": {"id": 11, "name": "%s"},
                    "awayTeam": {"id": 12, "name": "%s"},
                    "status": {"type": "notstarted", "description": "Not started"},
                    "tournament": {"id": 13, "name": "Qualification League"},
                    "season": {"id": 14, "name": "2026"},
                    "roundInfo": {"round": 1}
                  }
                }
                """.formatted(eventId, home, away);
    }
}
