package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportFailure;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderStopReceipt;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
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
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J3ScheduledEventsPageCache;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            transportEntered.countDown();
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(request, requestedAt, clock.instant(), 200, body);
        });
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
        AtomicInteger openedCampaigns = new AtomicInteger();
        AtomicInteger closedCampaigns = new AtomicInteger();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = new ScheduledEventsProviderPageTransport() {

            @Override
            public Campaign openCampaign(UUID campaignId) {
                assertThat(campaignId).isEqualTo(REQUEST_ID);
                openedCampaigns.incrementAndGet();
                return new Campaign() {

                    @Override
                    public ScheduledEventsTransportResponse execute(
                            ScheduledEventsProviderPageRequest request) {
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
                    }

                    @Override
                    public void close() {
                        closedCampaigns.incrementAndGet();
                    }
                };
            }
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
        assertThat(openedCampaigns).hasValue(1);
        assertThat(closedCampaigns).hasValue(1);
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
    void auditsOneNominalProviderPageInLifecycleOrder() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport.Campaign providerCampaign =
                mock(ScheduledEventsProviderPageTransport.Campaign.class);
        when(providerCampaign.execute(any())).thenAnswer(invocation -> {
            ScheduledEventsProviderPageRequest request = invocation.getArgument(0);
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    200,
                    withHasNextPage(terminalBody, false));
        });
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(11L);
        when(evidenceStore.startProviderAttempt(any())).thenReturn(12L);
        ManualProviderRequestCoordinator coordinator = new ManualProviderRequestCoordinator(
                clock, Duration.ofSeconds(3), ignored -> { });
        J3DynamicManualCallService service = auditedService(
                control,
                ignored -> providerCampaign,
                store,
                new RecordingCache(),
                evidenceService,
                clock,
                coordinator,
                evidenceStore);

        J3ManualCallExecutionResult result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.providerRequests()).isEqualTo(1);
        InOrder order = inOrder(evidenceStore, providerCampaign);
        order.verify(evidenceStore).startCampaign(any());
        order.verify(evidenceStore).declareUnit(any());
        order.verify(evidenceStore).startProviderAttempt(any());
        order.verify(providerCampaign).execute(any());
        order.verify(evidenceStore).recordUnitResult(any());
        order.verify(providerCampaign).close();
        order.verify(evidenceStore).finishCampaign(any());
        ArgumentCaptor<J8BenchmarkUnitResult> unitResult =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(unitResult.capture());
        assertThat(unitResult.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.PROVIDER);
        assertThat(unitResult.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PARSED);
        ArgumentCaptor<J8BenchmarkCampaignResult> campaignResult =
                ArgumentCaptor.forClass(J8BenchmarkCampaignResult.class);
        verify(evidenceStore).finishCampaign(campaignResult.capture());
        assertThat(campaignResult.getValue().terminalState())
                .isEqualTo(J8BenchmarkCampaignTerminalState.COMPLETED);
    }

    @Test
    void closesProviderResourcesBeforeFailingTheAuditWhenCampaignCleanupFails()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        List<String> lifecycle = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger();
        ScheduledEventsProviderPageTransport.Campaign providerCampaign =
                new ScheduledEventsProviderPageTransport.Campaign() {

                    @Override
                    public ScheduledEventsTransportResponse execute(
                            ScheduledEventsProviderPageRequest request) {
                        Instant requestedAt = clock.instant();
                        clock.advance(Duration.ofMillis(25));
                        return response(
                                request,
                                requestedAt,
                                clock.instant(),
                                200,
                                withHasNextPage(terminalBody, false));
                    }

                    @Override
                    public void close() {
                        closeCount.incrementAndGet();
                        lifecycle.add("PROVIDER_CLOSED");
                        throw new ScheduledEventsTransportException(
                                ScheduledEventsTransportFailure.IO_FAILURE);
                    }
                };
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(11L);
        when(evidenceStore.startProviderAttempt(any())).thenReturn(12L);
        org.mockito.Mockito.doAnswer(invocation -> {
            J8BenchmarkCampaignResult result = invocation.getArgument(0);
            lifecycle.add("AUDIT_" + result.terminalState().name());
            return null;
        }).when(evidenceStore).finishCampaign(any());
        ManualProviderRequestCoordinator coordinator = new ManualProviderRequestCoordinator(
                clock, Duration.ofSeconds(3), ignored -> { });
        J3DynamicManualCallService service = new J3DynamicManualCallService(
                control,
                ignored -> providerCampaign,
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
                coordinator,
                new J8BenchmarkAuditService(evidenceStore, clock));

        assertThatThrownBy(() -> service.execute(REQUEST_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("provider campaign cleanup failed")
                .hasCauseInstanceOf(ScheduledEventsTransportException.class);

        assertThat(closeCount).hasValue(1);
        assertThat(lifecycle).containsExactly("PROVIDER_CLOSED", "AUDIT_FAILED");
        assertThat(control.snapshot().globalStopActive()).isTrue();
        ArgumentCaptor<J8BenchmarkUnitResult> unitResult =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(unitResult.capture());
        assertThat(unitResult.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PARSED);
        ArgumentCaptor<J8BenchmarkCampaignResult> campaignResult =
                ArgumentCaptor.forClass(J8BenchmarkCampaignResult.class);
        verify(evidenceStore).finishCampaign(campaignResult.capture());
        assertThat(campaignResult.getValue().terminalState())
                .isEqualTo(J8BenchmarkCampaignTerminalState.FAILED);
        assertThat(campaignResult.getValue().terminalCode())
                .contains("PROCESSING_FAILURE");
        verify(evidenceStore).startProviderAttempt(any());
        try (var releasedLease = coordinator.acquireCampaign(UUID.randomUUID())) {
            assertThat(releasedLease).isNotNull();
        }
    }

    @Test
    void auditsACacheHitWithoutProviderAttempt() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        RecordingCache cache = new RecordingCache();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        cache.put(cachedPage(1, withHasNextPage(terminalBody, false)));
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        ScheduledEventsProviderPageTransport transport =
                mock(ScheduledEventsProviderPageTransport.class);
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(21L);
        ManualProviderRequestCoordinator coordinator = new ManualProviderRequestCoordinator(
                clock, Duration.ofSeconds(3), ignored -> { });
        J3DynamicManualCallService service = auditedService(
                control,
                transport,
                store,
                cache,
                evidenceService,
                clock,
                coordinator,
                evidenceStore);

        J3ManualCallExecutionResult result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.providerRequests()).isZero();
        assertThat(result.cacheHits()).isEqualTo(1);
        verify(transport, never()).openCampaign(any());
        verify(evidenceStore, never()).startProviderAttempt(any());
        ArgumentCaptor<J8BenchmarkUnitResult> unitResult =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(unitResult.capture());
        assertThat(unitResult.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.CACHE);
        assertThat(unitResult.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PARSED);
        assertThat(unitResult.getValue().attemptId()).isEmpty();
        InOrder order = inOrder(evidenceStore);
        order.verify(evidenceStore).startCampaign(any());
        order.verify(evidenceStore).declareUnit(any());
        order.verify(evidenceStore).recordUnitResult(any());
        order.verify(evidenceStore).finishCampaign(any());
    }

    @Test
    void blocksProviderExecutionWhenAttemptAuditPersistenceFails() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        ScheduledEventsProviderPageTransport.Campaign providerCampaign =
                mock(ScheduledEventsProviderPageTransport.Campaign.class);
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(31L);
        when(evidenceStore.startProviderAttempt(any()))
                .thenThrow(new IllegalStateException("attempt audit unavailable"));
        ManualProviderRequestCoordinator coordinator = new ManualProviderRequestCoordinator(
                clock, Duration.ofSeconds(3), ignored -> { });
        J3DynamicManualCallService service = auditedService(
                control,
                ignored -> providerCampaign,
                store,
                new RecordingCache(),
                evidenceService,
                clock,
                coordinator,
                evidenceStore);

        assertThatThrownBy(() -> service.execute(REQUEST_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt audit unavailable");

        InOrder order = inOrder(evidenceStore, providerCampaign);
        order.verify(evidenceStore).declareUnit(any());
        order.verify(evidenceStore).startProviderAttempt(any());
        order.verify(providerCampaign).close();
        verify(providerCampaign, never()).execute(any());
        verify(evidenceStore, never()).recordUnitResult(any());
        verify(evidenceStore, never()).finishCampaign(any());
        assertThat(control.snapshot().globalStopActive()).isTrue();
        try (var releasedLease = coordinator.acquireCampaign(UUID.randomUUID())) {
            assertThat(releasedLease).isNotNull();
        }
    }

    @Test
    void stopsNormallyAfterPageOneWhenItIsTheLastAvailablePage()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] body = Files.readAllBytes(Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(request, requestedAt, clock.instant(), 200, body);
        });
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
                .contains("J3_MINIMIZED_EVIDENCE_VERSION=6")
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
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    request.page() == 2 ? 403 : 200,
                    request.page() == 2 ? forbiddenBody : validBody);
        });
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
    void persistsA404AsUnavailableAndStopsPaginationWithoutRetry() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] unavailableBody = "{\"error\":\"not-found\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    404,
                    unavailableBody);
        });
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
        assertThat(result.completedPages()).isZero();
        assertThat(result.failedPage()).isEqualTo(1);
        assertThat(result.terminalCode()).isEqualTo("ENDPOINT_UNAVAILABLE");
        assertThat(result.providerRequests()).isEqualTo(1);
        assertThat(pages).containsExactly(1);
        assertThat(store.saved).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.httpStatus()).isEqualTo(404);
            assertThat(snapshot.payload().bytes()).isEqualTo(unavailableBody);
            assertThat(snapshot.schemaStatus())
                    .isEqualTo(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
            assertThat(snapshot.errorCode()).isEqualTo("ENDPOINT_UNAVAILABLE");
        });
        var evidence = evidenceService.latestDocument().orElseThrow();
        assertThat(evidence.reportText())
                .contains("TERMINAL_CODE=ENDPOINT_UNAVAILABLE")
                .contains("PAGE_1_SCHEMA_STATUS=ENDPOINT_UNAVAILABLE")
                .contains("PAGE_1_TERMINAL_CODE=ENDPOINT_UNAVAILABLE")
                .contains("PAGE_1_SNAPSHOT_RECORDED=YES")
                .doesNotContain("not-found");
    }

    @Test
    void recordsOnlyMinimizedAttemptMetadataWhenTransportFailsBeforeSnapshot() {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.TIMEOUT);
        });
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
    void workerStartupFailureDoesNotClaimThatAProviderRequestWasExecuted() {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        ScheduledEventsProviderPageTransport transport =
                new ScheduledEventsProviderPageTransport() {

            @Override
            public Campaign openCampaign(UUID campaignId) {
                throw new ScheduledEventsTransportException(
                        ScheduledEventsTransportFailure.IO_FAILURE);
            }
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
        assertThat(result.providerRequests()).isZero();
        assertThat(store.saved).isEmpty();
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("J3_MINIMIZED_EVIDENCE_VERSION=6")
                .contains("PROVIDER_PAGES_REQUESTED=NONE")
                .contains("PROVIDER_REQUEST_COUNT=0")
                .contains("PAGE_1_RESOLUTION_SOURCE=PROVIDER")
                .contains("PAGE_1_PROVIDER_REQUEST_EXECUTED=NO")
                .contains("PAGE_1_SNAPSHOT_RECORDED=NO");
    }

    @Test
    void operatorStopThatWinsDuringTransportRemainsTheTerminalOutcome() {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        J3ManualCallControlService control = readyControl(clock);
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            control.stopGlobally();
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.OPERATOR_STOP);
        });
        var service = service(
                control,
                transport,
                store,
                evidenceService,
                clock,
                clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.completedPages()).isZero();
        assertThat(result.failedPage()).isEqualTo(1);
        assertThat(result.terminalCode()).isEqualTo("OPERATOR_STOP");
        assertThat(result.providerRequests()).isEqualTo(1);
        assertThat(store.saved).isEmpty();
        assertThat(control.snapshot().globalStopActive()).isTrue();
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.OPERATOR_STOP);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("TERMINAL_CODE=OPERATOR_STOP")
                .contains("PAGE_1_SNAPSHOT_RECORDED=NO")
                .contains("PAGE_1_TERMINAL_CODE=OPERATOR_STOP");
    }

    @Test
    void concurrentOperatorStopSignalsTransportBeforeLockingTheBusinessControl()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        J3ManualCallControlService control = readyControl(clock);
        CountDownLatch transportEntered = new CountDownLatch(1);
        CountDownLatch transportCancelled = new CountDownLatch(1);
        ScheduledEventsProviderPageTransport transport =
                new ScheduledEventsProviderPageTransport() {

            @Override
            public Campaign openCampaign(UUID campaignId) {
                assertThat(campaignId).isEqualTo(REQUEST_ID);
                return new Campaign() {

                    @Override
                    public ScheduledEventsTransportResponse execute(
                            ScheduledEventsProviderPageRequest request) {
                        transportEntered.countDown();
                        try {
                            assertThat(transportCancelled.await(2, TimeUnit.SECONDS))
                                    .isTrue();
                        }
                        catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                        throw new ScheduledEventsTransportException(
                                ScheduledEventsTransportFailure.OPERATOR_STOP);
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
        PlaywrightProviderSupervisor supervisor = new PlaywrightProviderSupervisor() {

            @Override
            public PlaywrightProviderStopReceipt stopCampaign(
                    UUID campaignId,
                    Set<SofascoreEndpointType> allowedEndpoints) {
                assertThat(campaignId).isEqualTo(REQUEST_ID);
                assertThat(allowedEndpoints)
                        .containsExactly(SofascoreEndpointType.SCHEDULED_EVENTS);
                assertThat(control.executionMayContinue(REQUEST_ID)).isTrue();
                transportCancelled.countDown();
                return new PlaywrightProviderStopReceipt(
                        campaignId, true, clock.instant(), Duration.ZERO);
            }

            @Override
            public Optional<UUID> activeCampaignId() {
                return Optional.of(REQUEST_ID);
            }
        };
        var stopService = new J3ProviderCampaignStopService(
                supervisor,
                control,
                mock(TournamentEventDiscoveryControlService.class));
        var executionService = service(
                control,
                transport,
                store,
                evidenceService,
                clock,
                clock::advance);
        var executor = Executors.newSingleThreadExecutor();

        try {
            Future<J3ManualCallExecutionResult> future = executor.submit(
                    () -> executionService.execute(REQUEST_ID));
            assertThat(transportEntered.await(1, TimeUnit.SECONDS)).isTrue();

            stopService.stopScheduledEvents();
            J3ManualCallExecutionResult result = future.get(2, TimeUnit.SECONDS);

            assertThat(result.completed()).isFalse();
            assertThat(result.terminalCode()).isEqualTo("OPERATOR_STOP");
            assertThat(result.failedPage()).isEqualTo(1);
            assertThat(store.saved).isEmpty();
            assertThat(control.snapshot().globalStopActive()).isTrue();
            assertThat(control.snapshot().circuitReason())
                    .isEqualTo(J3CircuitReason.OPERATOR_STOP);
        }
        finally {
            transportCancelled.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
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
        AtomicInteger openedCampaigns = new AtomicInteger();
        ScheduledEventsProviderPageTransport transport = new ScheduledEventsProviderPageTransport() {

            @Override
            public Campaign openCampaign(UUID campaignId) {
                openedCampaigns.incrementAndGet();
                throw new AssertionError("a cache-only J3 run must not open a campaign");
            }
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
        assertThat(openedCampaigns).hasValue(0);
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
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
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
        });
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
        ScheduledEventsProviderPageTransport transport = campaignTransport(request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(request, requestedAt, clock.instant(), 200, continuingBody);
        });
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

    private static ScheduledEventsProviderPageTransport campaignTransport(
            Function<ScheduledEventsProviderPageRequest, ScheduledEventsTransportResponse>
                    execution) {
        return ignoredCampaignId -> execution::apply;
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

    private static J3DynamicManualCallService auditedService(
            J3ManualCallControlService control,
            ScheduledEventsProviderPageTransport transport,
            RecordingStore store,
            J3ScheduledEventsPageCache cache,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            ManualProviderRequestCoordinator coordinator,
            J8BenchmarkEvidenceStore evidenceStore) {
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
                ignored -> { },
                coordinator,
                new J8BenchmarkAuditService(evidenceStore, clock));
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
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(saved.size()));
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
