package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseEvidence;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalEventPersistenceService;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalizationResult;
import com.bettingproject.sofascorelocal.application.event.TournamentDiscoveredEventView;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.CachedTournamentScheduledEventsResponse;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoverySource;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryState;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsCache;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsProviderTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentEventDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");
    private static final UUID REQUEST_ID =
            UUID.fromString("d9498338-4d8f-4127-9c16-276af31464be");

    private TournamentEventDiscoveryControlService control;
    private TournamentScheduledEventsProviderTransport transport;
    private TournamentScheduledEventsCache cache;
    private RawManualCallSnapshotStore rawStore;
    private TournamentCanonicalEventPersistenceService persistence;
    private TournamentEventDiscoveryService service;

    @BeforeEach
    void setUp() {
        control = mock(TournamentEventDiscoveryControlService.class);
        transport = mock(TournamentScheduledEventsProviderTransport.class);
        cache = mock(TournamentScheduledEventsCache.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        persistence = mock(TournamentCanonicalEventPersistenceService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = serviceWith(
                new TournamentScheduledEventsV1Parser(),
                new TournamentScheduledEventsProjectionService(),
                clock);
        when(control.executionMayContinue(REQUEST_ID)).thenReturn(true);
        when(cache.findFreshParsed(
                any(), any(), eq(TournamentEventDiscoveryService.CACHE_TTL),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION)))
                .thenReturn(Optional.empty());
        when(control.executeWhileActive(eq(REQUEST_ID), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(control.executeAndComplete(eq(REQUEST_ID), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    }

    @Test
    void persistsRawBeforeClassificationCacheAndCanonicalizationForOneProviderGet() {
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        var raw = persistenceResult(81, payload);
        when(transport.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(raw);
        when(persistence.persist(any(), eq(81L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isTrue();
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.snapshotId()).isEqualTo(81);
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
        assertThat(result.events()).hasSize(1);
        InOrder order = inOrder(rawStore, cache, persistence, control);
        order.verify(rawStore).save(any());
        order.verify(rawStore).classify(81, RawSnapshotSchemaStatus.PARSED, null);
        order.verify(cache).recordParsed(
                any(), eq(response), eq(raw),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION));
        order.verify(control).executeAndComplete(eq(REQUEST_ID), any());
        order.verify(persistence).persist(any(), eq(81L), eq(payload.sha256()), eq(NOW));
    }

    @Test
    void importsAResponseBodyLocallyWithDistinctProvenanceAndNoProviderOrCacheCall() {
        RawPayloadEvidence payload = payload(7);
        var raw = persistenceResult(90, payload);
        when(rawStore.save(any())).thenReturn(raw);
        when(persistence.persist(any(), eq(90L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());

        var result = service.importLocalJson(claim(Map.of(7200, 1)), payload);

        assertThat(result.completed()).isTrue();
        assertThat(result.source()).isEqualTo(
                TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
        assertThat(result.providerCallAttempts()).isZero();
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.snapshotId()).isEqualTo(90);
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
        assertThat(result.events()).hasSize(1);

        ArgumentCaptor<RawManualCallSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(RawManualCallSnapshot.class);
        verify(rawStore).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().acquisitionMode())
                .isEqualTo(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(snapshotCaptor.getValue().requestKey())
                .isEqualTo(
                        "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7");
        assertThat(snapshotCaptor.getValue().httpStatus()).isEqualTo(200);
        assertThat(snapshotCaptor.getValue().latency()).isZero();
        assertThat(snapshotCaptor.getValue().payload()).isEqualTo(payload);
        verify(rawStore).classify(90, RawSnapshotSchemaStatus.PARSED, null);
        verify(transport, never()).execute(any());
        verify(cache, never()).findFreshParsed(any(), any(), any(), any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence).persist(any(), eq(90L), eq(payload.sha256()), eq(NOW));
    }

    @Test
    void reparsesAFreshCacheHitWithoutExecutingOrPersistingTransport() {
        RawPayloadEvidence payload = payload(7);
        var cached = new CachedTournamentScheduledEventsResponse(
                82,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7",
                NOW,
                NOW.minusMillis(25),
                NOW,
                200,
                "application/json",
                Duration.ofMillis(25),
                payload,
                TournamentScheduledEventsV1Parser.PARSER_VERSION);
        when(cache.findFreshParsed(
                any(), eq(NOW), eq(TournamentEventDiscoveryService.CACHE_TTL),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION)))
                .thenReturn(Optional.of(cached));
        when(persistence.persist(any(), eq(82L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isTrue();
        assertThat(result.cacheHit()).isTrue();
        assertThat(result.providerCallAttempts()).isZero();
        verify(transport, never()).execute(any());
        verify(rawStore, never()).save(any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
    }

    @Test
    void refusesCanonicalizationOnCountMismatchButKeepsTheParsedSnapshotCached() {
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        var raw = persistenceResult(83, payload);
        when(transport.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(raw);

        var result = service.execute(claim(Map.of(7200, 2)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("EVENT_COUNT_MISMATCH");
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_MISMATCH);
        assertThat(result.expectedCount()).isEqualTo(2);
        assertThat(result.actualCount()).isEqualTo(1);
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
        verify(cache).recordParsed(
                any(), eq(response), eq(raw),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION));
        verify(control).fail(REQUEST_ID, "EVENT_COUNT_MISMATCH");
    }

    @Test
    void rejectsAUniqueTournamentContradictionBeforeCacheOrCanonicalWrites() {
        RawPayloadEvidence payload = payload(8);
        var response = response(200, payload);
        when(transport.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(84, payload));

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("UNIQUE_TOURNAMENT_ID_MISMATCH");
        verify(rawStore).classify(
                84,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void classifiesAParserContractBreakWithTheCanonicalRawErrorCode() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"events\":{}}".getBytes(StandardCharsets.UTF_8));
        var response = response(200, payload);
        when(transport.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(87, payload));

        var result = service.execute(claim(Map.of()));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("SCHEMA_INCOMPATIBLE");
        verify(rawStore).classify(
                87,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void keepsParserFailureDetailedForControlButCanonicalForRawClassification() {
        TournamentScheduledEventsV1Parser failingParser =
                mock(TournamentScheduledEventsV1Parser.class);
        when(failingParser.parse(
                any(byte[].class),
                any(String.class),
                any(TournamentScheduledEventsParseEvidence.class)))
                .thenThrow(new IllegalStateException("synthetic parser failure"));
        service = serviceWith(
                failingParser,
                new TournamentScheduledEventsProjectionService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        when(transport.execute(any())).thenReturn(response(200, payload));
        when(rawStore.save(any())).thenReturn(persistenceResult(88, payload));

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("PARSER_FAILURE");
        verify(rawStore).classify(
                88,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void keepsProjectionFailureDetailedForControlButCanonicalForRawClassification() {
        TournamentScheduledEventsProjectionService failingProjection =
                mock(TournamentScheduledEventsProjectionService.class);
        when(failingProjection.project(
                any(LocalDate.class),
                any(J3TournamentCatalogOption.class),
                anyList()))
                .thenThrow(new IllegalStateException("synthetic projection failure"));
        service = serviceWith(
                new TournamentScheduledEventsV1Parser(),
                failingProjection,
                Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        when(transport.execute(any())).thenReturn(response(200, payload));
        when(rawStore.save(any())).thenReturn(persistenceResult(89, payload));

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("PROJECTION_FAILURE");
        verify(rawStore).classify(
                89,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void recordsAndClassifiesANonSuccessResponseWithoutRetry() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"error\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8));
        var response = response(403, payload);
        when(transport.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(85, payload));

        var result = service.execute(claim(Map.of()));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("HTTP_403");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(transport).execute(any());
        verify(rawStore).classify(85, RawSnapshotSchemaStatus.TRANSPORT_ERROR, "HTTP_403");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void operatorStopDuringTransportKeepsRawButPreventsCacheAndCanonicalWrites() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog());
        var realControl = new TournamentEventDiscoveryControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> TournamentEventDiscoveryQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)),
                catalogService);
        var prepared = realControl.prepare(119_880);
        var realClaim = realControl.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true);
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        when(transport.execute(any())).thenAnswer(invocation -> {
            realControl.stop();
            return response;
        });
        when(rawStore.save(any())).thenReturn(persistenceResult(86, payload));
        var realService = new TournamentEventDiscoveryService(
                realControl,
                transport,
                cache,
                rawStore,
                new TournamentScheduledEventsProjectionService(),
                persistence,
                new TournamentScheduledEventsV1Parser(),
                new ManualProviderRequestCoordinator(
                        clock,
                        Duration.ofSeconds(3),
                        ignored -> { }),
                clock);

        var result = realService.execute(realClaim);

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("OPERATOR_STOP");
        assertThat(result.snapshotId()).isEqualTo(86);
        assertThat(realControl.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
        verify(rawStore).save(any());
        verify(rawStore, never()).classify(anyLong(), any(), any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    private static TournamentEventDiscoveryExecutionClaim claim(
            Map<Integer, Integer> counts) {
        return new TournamentEventDiscoveryExecutionClaim(
                REQUEST_ID,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                LocalDate.of(2026, 8, 18),
                new J3TournamentCatalogOption(
                        119_880,
                        "UEFA Champions League, Playoff Round",
                        "Europe",
                        7,
                        "UEFA Champions League",
                        counts,
                        List.of(41L)));
    }

    private static J3TournamentCatalog catalog() {
        return J3TournamentCatalog.available(
                LocalDate.of(2026, 8, 18),
                List.of(41L),
                List.of(claim(Map.of(7200, 1)).selection()),
                0);
    }

    private static TournamentScheduledEventsTransportResponse response(
            int status,
            RawPayloadEvidence payload) {
        return new TournamentScheduledEventsTransportResponse(
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7",
                NOW.minusMillis(25),
                NOW,
                status,
                "application/json",
                Duration.ofMillis(25),
                payload);
    }

    private static RawSnapshotPersistenceResult persistenceResult(
            long snapshotId,
            RawPayloadEvidence payload) {
        return new RawSnapshotPersistenceResult(
                snapshotId,
                RawSnapshotPersistenceOutcome.INSERTED,
                payload.sha256(),
                payload.sizeBytes());
    }

    private static TournamentCanonicalizationResult canonicalization() {
        return new TournamentCanonicalizationResult(
                1,
                0,
                List.of(new TournamentDiscoveredEventView(
                        CanonicalEventIdentity.sofascore(501).value(),
                        501,
                        Instant.parse("2026-08-18T19:00:00Z"),
                        "Home",
                        "Away",
                        "finished")));
    }

    private static RawPayloadEvidence payload(long uniqueTournamentId) {
        String json = """
                {
                  "events": [
                    {
                      "id": 501,
                      "startTimestamp": 1787079600,
                      "homeTeam": {"id": 10, "name": "Home"},
                      "awayTeam": {"id": 20, "name": "Away"},
                      "status": {"type": "finished"},
                      "tournament": {
                        "id": 119880,
                        "name": "UEFA Champions League, Playoff Round",
                        "uniqueTournament": {
                          "id": %d,
                          "name": "UEFA Champions League"
                        }
                      }
                    }
                  ]
                }
                """.formatted(uniqueTournamentId);
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private TournamentEventDiscoveryService serviceWith(
            TournamentScheduledEventsV1Parser selectedParser,
            TournamentScheduledEventsProjectionService selectedProjection,
            Clock selectedClock) {
        return new TournamentEventDiscoveryService(
                control,
                transport,
                cache,
                rawStore,
                selectedProjection,
                persistence,
                selectedParser,
                new ManualProviderRequestCoordinator(
                        selectedClock,
                        Duration.ofSeconds(3),
                        ignored -> { }),
                selectedClock);
    }
}
