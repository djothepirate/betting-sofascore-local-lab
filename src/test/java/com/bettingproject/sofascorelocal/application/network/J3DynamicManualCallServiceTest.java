package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportFailure;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3CachedScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallExecutionResult;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3DynamicManualCallServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-13");
    private static final UUID REQUEST_ID = UUID.fromString(
            "3ccfd0a0-7825-4bfa-977b-358be086b1e2");
    private static final URI ORIGIN = URI.create("https://www.sofascore.com");

    @Test
    void waitsForTheSharedManualProviderCoordinatorBeforeStartingJ3Transport()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        CountDownLatch transportEntered = new CountDownLatch(1);
        byte[] body = Files.readAllBytes(Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = request -> {
            transportEntered.countDown();
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(request, requestedAt, clock.instant(), 200, body);
        };
        var coordinator = new ManualProviderRequestCoordinator(
                clock, Duration.ofSeconds(3), ignored -> { });
        var service = new J3DynamicManualCallService(
                control,
                transport,
                new J3ScheduledEventsOutcomeProcessor(
                        store,
                        new ScheduledEventsV1Parser(),
                        control.circuit()),
                new RecordingCache(),
                new ScheduledEventsV1Parser(),
                new J3SingleCallGuard(),
                evidenceService,
                clock,
                Duration.ofMinutes(10),
                Duration.ofSeconds(3),
                ignored -> { },
                coordinator);
        var executor = Executors.newSingleThreadExecutor();

        try {
            Future<J3ManualCallExecutionResult> future;
            try (var heldByAnotherCampaign = coordinator.acquire()) {
                future = executor.submit(() -> service.execute(REQUEST_ID));
                assertThat(transportEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();
            }

            assertThat(transportEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(future.get(1, TimeUnit.SECONDS).completed()).isTrue();
        }
        finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void followsHasNextPageUntilFalseWithThreeSecondsBetweenStarts() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        List<Instant> starts = new ArrayList<>();
        List<Duration> waits = new ArrayList<>();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            starts.add(clock.instant());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    200,
                    withHasNextPage(terminalBody, request.page() < 5));
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(control, transport, store, evidenceService, clock, duration -> {
            waits.add(duration);
            clock.advance(duration);
        });

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.providerRequests()).isEqualTo(5);
        assertThat(result.cacheHits()).isZero();
        assertThat(result.localJsonImports()).isZero();
        assertThat(pages).containsExactly(1, 2, 3, 4, 5);
        assertThat(starts).hasSize(5);
        for (int index = 1; index < starts.size(); index++) {
            assertThat(Duration.between(starts.get(index - 1), starts.get(index)))
                    .isGreaterThanOrEqualTo(Duration.ofSeconds(3));
        }
        assertThat(waits).hasSize(4);
        assertThat(store.saved).hasSize(5);
        assertThat(store.classifiedStatuses)
                .containsOnly(RawSnapshotSchemaStatus.PARSED);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.COMPLETED);
        assertThat(control.snapshot().intent().completedPages()).isEqualTo(5);
        assertThat(control.snapshot().globalStopActive()).isTrue();
        assertThat(control.snapshot().circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        var evidence = evidenceService.latestDocument().orElseThrow();
        assertThat(evidence.evidence().pageAttempts()).hasSize(5);
        assertThat(evidence.reportText())
                .contains("PAGES_RESOLVED=1,2,3,4,5")
                .contains("PAGINATION_MODE=HAS_NEXT_PAGE")
                .contains("CACHE_POLICY=FRESH_PARSED_SNAPSHOT_FIRST")
                .contains("CACHE_TTL_SECONDS=600")
                .contains("PROVIDER_PAGES_REQUESTED=1,2,3,4,5")
                .contains("CACHE_HIT_PAGES=NONE")
                .contains("MAXIMUM_PAGE_LIMIT=25")
                .contains("PAGE_1_HAS_NEXT_PAGE=true")
                .contains("PAGE_5_HAS_NEXT_PAGE=false")
                .contains("FINAL_GLOBAL_STOP=ACTIVE")
                .contains("FINAL_CIRCUIT_REASON=MANUAL_COLLECTION_TERMINAL_LOCK")
                .contains("RAW_PAYLOAD_INCLUDED=NO")
                .doesNotContain("https://www.sofascore.com")
                .doesNotContain("\"events\"");

        assertThatThrownBy(() -> service.execute(REQUEST_ID))
                .isInstanceOfSatisfying(
                        J3ManualCallControlException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(J3ManualCallControlError.EXECUTION_ALREADY_STARTED));
    }

    @Test
    void stopsNormallyAfterPageOneWhenItIsTheLastAvailablePage()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] body = Files.readAllBytes(Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(request, requestedAt, clock.instant(), 200, body);
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(control, transport, store, evidenceService, clock, clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(1);
        assertThat(pages).containsExactly(1);
        assertThat(store.saved).hasSize(1);
        assertThat(control.snapshot().intent().firstPage()).isEqualTo(1);
        assertThat(control.snapshot().intent().completedPages()).isEqualTo(1);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("J3_MINIMIZED_EVIDENCE_VERSION=5")
                .contains("PROVIDER_FIRST_PAGE=1")
                .contains("PAGES_RESOLVED=1")
                .contains("PAGES_COMPLETED_COUNT=1")
                .contains("LAST_COMPLETED_PAGE=1")
                .contains("PAGE_1_HAS_NEXT_PAGE=false")
                .doesNotContain("PAGE_2_REQUESTED_AT");
    }

    @Test
    void persistsTheFailingPageAndStopsBeforePageThreeWithoutRetry() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        byte[] validBody = withHasNextPage(terminalBody, true);
        byte[] forbiddenBody = "{\"error\":\"forbidden\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    request.page() == 2 ? 403 : 200,
                    request.page() == 2 ? forbiddenBody : validBody);
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(
                control,
                transport,
                store,
                evidenceService,
                clock,
                clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.completedPages()).isEqualTo(1);
        assertThat(result.failedPage()).isEqualTo(2);
        assertThat(result.terminalCode()).isEqualTo("HTTP_FORBIDDEN");
        assertThat(pages).containsExactly(1, 2);
        assertThat(store.saved).hasSize(2);
        assertThat(store.saved.get(1).schemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.TRANSPORT_ERROR);
        assertThat(control.snapshot().circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.FAILED);
        var evidence = evidenceService.latestDocument().orElseThrow();
        assertThat(evidence.reportText())
                .contains("TERMINAL_CODE=HTTP_FORBIDDEN")
                .contains("PAGE_2_SCHEMA_STATUS=TRANSPORT_ERROR")
                .contains("PAGE_2_TERMINAL_CODE=HTTP_FORBIDDEN")
                .contains("PAGE_2_SNAPSHOT_RECORDED=YES")
                .contains("PAGE_2_CACHE_STORED_AT=NONE")
                .doesNotContain("forbidden");
    }

    @Test
    void recordsOnlyMinimizedAttemptMetadataWhenTransportFailsBeforeSnapshot() {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        ScheduledEventsProviderPageTransport transport = request -> {
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.TIMEOUT);
        };
        J3ManualCallControlService control = readyControl(clock);
        var service = service(
                control,
                transport,
                store,
                evidenceService,
                clock,
                clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.failedPage()).isEqualTo(1);
        assertThat(result.terminalCode()).isEqualTo("TIMEOUT");
        assertThat(store.saved).isEmpty();
        assertThat(control.snapshot().globalStopActive()).isTrue();
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("PAGES_RESOLVED=1")
                .contains("PAGE_1_SNAPSHOT_RECORDED=NO")
                .contains("PAGE_1_HTTP_STATUS=NONE")
                .contains("PAGE_1_PAYLOAD_SHA256=NONE")
                .contains("PAGE_1_TERMINAL_CODE=TIMEOUT");
    }

    @Test
    void resolvesFreshParsedPagesFromCacheWithoutDelayTransportOrPersistence()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        RecordingCache cache = new RecordingCache();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        cache.put(cachedPage(1, withHasNextPage(terminalBody, true)));
        cache.put(cachedPage(2, withHasNextPage(terminalBody, false)));
        List<Duration> waits = new ArrayList<>();
        ScheduledEventsProviderPageTransport transport = request -> {
            throw new AssertionError("a fresh parsed cache hit must prevent transport");
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(
                control,
                transport,
                store,
                cache,
                evidenceService,
                clock,
                waits::add);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(2);
        assertThat(result.providerRequests()).isZero();
        assertThat(result.cacheHits()).isEqualTo(2);
        assertThat(cache.lookups).containsExactly(1, 2);
        assertThat(cache.recordedPages).isEmpty();
        assertThat(waits).isEmpty();
        assertThat(store.saved).isEmpty();
        assertThat(store.classifiedStatuses).isEmpty();
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("PROVIDER_PAGES_REQUESTED=NONE")
                .contains("CACHE_HIT_PAGES=1,2")
                .contains("PROVIDER_REQUEST_COUNT=0")
                .contains("CACHE_HIT_COUNT=2")
                .contains("PAGE_1_RESOLUTION_SOURCE=CACHE")
                .contains("PAGE_1_PROVIDER_REQUEST_EXECUTED=NO")
                .contains("PAGE_1_PERSISTENCE_OUTCOME=CACHE_HIT")
                .contains("PAGE_2_HAS_NEXT_PAGE=false");
    }

    @Test
    void appliesDelayOnlyBetweenActualProviderStartsAcrossAnIntermediateCacheHit()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        RecordingCache cache = new RecordingCache();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        cache.put(cachedPage(2, withHasNextPage(terminalBody, true)));
        List<Integer> transportedPages = new ArrayList<>();
        List<Instant> providerStarts = new ArrayList<>();
        List<Duration> waits = new ArrayList<>();
        ScheduledEventsProviderPageTransport transport = request -> {
            transportedPages.add(request.page());
            providerStarts.add(clock.instant());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    200,
                    withHasNextPage(terminalBody, request.page() == 1));
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(
                control,
                transport,
                store,
                cache,
                evidenceService,
                clock,
                duration -> {
                    waits.add(duration);
                    clock.advance(duration);
                });

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(3);
        assertThat(result.providerRequests()).isEqualTo(2);
        assertThat(result.cacheHits()).isEqualTo(1);
        assertThat(cache.lookups).containsExactly(1, 2, 3);
        assertThat(cache.recordedPages).containsExactly(1, 3);
        assertThat(transportedPages).containsExactly(1, 3);
        assertThat(waits).singleElement().satisfies(wait ->
                assertThat(wait).isEqualTo(Duration.ofMillis(2_975)));
        assertThat(Duration.between(providerStarts.get(0), providerStarts.get(1)))
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(store.saved).hasSize(2);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("PROVIDER_PAGES_REQUESTED=1,3")
                .contains("CACHE_HIT_PAGES=2")
                .contains("PAGE_2_RESOLUTION_SOURCE=CACHE")
                .contains("PAGE_3_RESOLUTION_SOURCE=PROVIDER");
    }

    @Test
    void stopsBeforePageTwentySixWhenProviderStillAnnouncesAnotherPage()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        byte[] continuingBody = withHasNextPage(terminalBody, true);
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(request, requestedAt, clock.instant(), 200, continuingBody);
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(
                control, transport, store, evidenceService, clock, clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.completedPages()).isEqualTo(25);
        assertThat(result.failedPage()).isEqualTo(26);
        assertThat(result.terminalCode()).isEqualTo("PAGINATION_LIMIT_REACHED");
        assertThat(pages).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 25).boxed().toList());
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("MAXIMUM_PAGE_LIMIT=25")
                .contains("FAILED_PAGE=26")
                .contains("PAGE_25_HAS_NEXT_PAGE=true")
                .doesNotContain("PAGE_26_REQUESTED_AT");
    }

    private static J3DynamicManualCallService service(
            J3ManualCallControlService control,
            ScheduledEventsProviderPageTransport transport,
            RecordingStore store,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            J3DynamicManualCallService.InterPageDelay delay) {
        return service(
                control,
                transport,
                store,
                new RecordingCache(),
                evidenceService,
                clock,
                delay);
    }

    private static J3DynamicManualCallService service(
            J3ManualCallControlService control,
            ScheduledEventsProviderPageTransport transport,
            RecordingStore store,
            J3ScheduledEventsPageCache cache,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            J3DynamicManualCallService.InterPageDelay delay) {
        return new J3DynamicManualCallService(
                control,
                transport,
                new J3ScheduledEventsOutcomeProcessor(
                        store,
                        new ScheduledEventsV1Parser(),
                        control.circuit()),
                cache,
                new ScheduledEventsV1Parser(),
                new J3SingleCallGuard(),
                evidenceService,
                clock,
                Duration.ofMinutes(10),
                Duration.ofSeconds(3),
                delay);
    }

    private static J3ManualCallControlService readyControl(Clock clock) {
        J3ManualCallControlService control = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> J3ProviderQualificationSnapshot.available(ORIGIN));
        control.rearmAfterGlobalStop();
        control.activateByOperator();
        var prepared = control.prepare(DATE);
        control.confirm(
                REQUEST_ID,
                prepared.intent().confirmationPhrase(),
                true);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);
        return control;
    }

    private static byte[] withHasNextPage(byte[] source, boolean value) {
        String json = new String(source, java.nio.charset.StandardCharsets.UTF_8);
        return json.replace(
                        "\"hasNextPage\": false",
                        "\"hasNextPage\": " + value)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static ScheduledEventsTransportResponse response(
            ScheduledEventsProviderPageRequest request,
            Instant requestedAt,
            Instant receivedAt,
            int status,
            byte[] body) {
        return new ScheduledEventsTransportResponse(
                request.requestKey(),
                requestedAt,
                receivedAt,
                status,
                "application/json",
                Duration.between(requestedAt, receivedAt),
                RawPayloadEvidence.capture(body));
    }

    private static J3CachedScheduledEventsPage cachedPage(int page, byte[] body) {
        Instant requestedAt = NOW.minus(Duration.ofMinutes(1));
        return new J3CachedScheduledEventsPage(
                page,
                "SCHEDULED_EVENTS|date=" + DATE + "|page=" + page,
                NOW.minus(Duration.ofSeconds(30)),
                requestedAt,
                requestedAt.plusMillis(25),
                200,
                "application/json",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture(body),
                ScheduledEventsV1Parser.PARSER_VERSION);
    }

    private static final class RecordingCache implements J3ScheduledEventsPageCache {

        private final Map<Integer, J3CachedScheduledEventsPage> pages = new HashMap<>();
        private final List<Integer> lookups = new ArrayList<>();
        private final List<Integer> recordedPages = new ArrayList<>();

        private void put(J3CachedScheduledEventsPage page) {
            pages.put(Math.toIntExact(page.snapshotId()), page);
        }

        @Override
        public Optional<J3CachedScheduledEventsPage> findFreshParsed(
                ScheduledEventsProviderPageRequest request,
                Instant evaluatedAt,
                Duration timeToLive,
                String parserVersion) {
            lookups.add(request.page());
            assertThat(timeToLive).isEqualTo(Duration.ofMinutes(10));
            assertThat(parserVersion).isEqualTo(ScheduledEventsV1Parser.PARSER_VERSION);
            return Optional.ofNullable(pages.get(request.page()));
        }

        @Override
        public void recordParsed(
                ScheduledEventsProviderPageRequest request,
                ScheduledEventsTransportResponse response,
                RawSnapshotPersistenceResult persistence,
                String parserVersion) {
            recordedPages.add(request.page());
            pages.put(request.page(), new J3CachedScheduledEventsPage(
                    persistence.snapshotId(),
                    request.requestKey(),
                    response.receivedAt(),
                    response.requestedAt(),
                    response.receivedAt(),
                    response.httpStatus(),
                    response.contentType(),
                    response.latency(),
                    response.payload(),
                    parserVersion));
        }
    }

    private static final class RecordingStore implements RawManualCallSnapshotStore {

        private final List<RawManualCallSnapshot> saved = new ArrayList<>();
        private final List<RawSnapshotSchemaStatus> classifiedStatuses = new ArrayList<>();

        @Override
        public RawSnapshotPersistenceResult save(RawManualCallSnapshot snapshot) {
            saved.add(snapshot);
            return new RawSnapshotPersistenceResult(
                    saved.size(),
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        }

        @Override
        public void classify(
                long snapshotId,
                RawSnapshotSchemaStatus schemaStatus,
                String errorCode) {
            classifiedStatuses.add(schemaStatus);
        }
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
