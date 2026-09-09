package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderPlaywrightLocalQualificationIT {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 27);
    private static final Path RUNTIME_SANDBOX_ROOT = Path.of(
            "target", "provider-playwright-runtime", "qualification-sandboxes")
            .toAbsolutePath().normalize();
    private static final Duration MINIMUM_PROVIDER_START_GAP = Duration.ofSeconds(3);
    private static final Duration FENCE_OBSERVATION_WINDOW = Duration.ofMillis(250);
    private static final byte[] PAGE_ONE =
            "{\"events\":[],\"hasNextPage\":true,\"marker\":\"caf\u00e9\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] PAGE_TWO_404 =
            "{\"error\":{\"code\":404},\"marker\":\"page-2\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] TOURNAMENT_RESPONSE =
            "{\"events\":[],\"marker\":\"tournament-17\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_DETAILS_ONE_RESPONSE =
            "{\"event\":{\"id\":16386245},\"marker\":\"event-details-one\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_DETAILS_TWO_RESPONSE =
            "{\"event\":{\"id\":16421052},\"marker\":\"event-details-two\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_DETAILS_NOT_FOUND_RESPONSE =
            "{\"error\":{\"code\":404},\"eventId\":17000001}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_DETAILS_STOP_RESPONSE =
            "{\"event\":{\"id\":17000002},\"marker\":\"event-details-stop\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final long J5_EVENT_ID = 17_000_003L;
    private static final long J5_STOP_EVENT_ID = 17_000_004L;
    private static final byte[] J5_STATISTICS_RESPONSE =
            "{\"statistics\":[],\"marker\":\"j5-statistics\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] J5_INCIDENTS_NOT_FOUND_RESPONSE =
            "{\"error\":{\"code\":404},\"marker\":\"j5-incidents-unavailable\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] J5_LINEUPS_RESPONSE =
            "{\"confirmed\":true,\"marker\":\"j5-lineups\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] J5_STOP_STATISTICS_RESPONSE =
            "{\"statistics\":[],\"marker\":\"j5-stop-statistics\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] J5_STOP_INCIDENTS_RESPONSE =
            "{\"incidents\":[],\"marker\":\"j5-stop-incidents\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] J5_STOP_LINEUPS_RESPONSE =
            "{\"confirmed\":true,\"marker\":\"j5-stop-lineups\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] FORBIDDEN_RESPONSE =
            "{\"error\":{\"code\":403}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RATE_LIMIT_RESPONSE =
            "{\"error\":{\"code\":429}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_ERROR_RESPONSE =
            "{\"error\":{\"code\":503}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTML_CHALLENGE_RESPONSE =
            "<!doctype html><title>local challenge fixture</title>"
                    .getBytes(StandardCharsets.UTF_8);
    private static final Set<SofascoreEndpointType> LIVE_ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS);

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Timeout(60)
    void liveV6AcceptsDelayedHeadersOrBodyWithinTheUniqueDeadline(boolean delayedBody) throws Exception {
        exerciseLiveV6Delay(delayedBody, false, false);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Timeout(60)
    void liveV6RequiresTerminalProofBeforeReusingTimedOutContext(boolean delayedBody) throws Exception {
        exerciseLiveV6Delay(delayedBody, true, false);
    }

    @Test @Timeout(60)
    void liveV6DiscardsLateCompletedBodyAndReusesContextOnlyAfterFinishedProof() throws Exception {
        exerciseLiveV6Delay(true, true, true);
    }

    private void exerciseLiveV6Delay(boolean delayedBody, boolean timeout, boolean lateCompletion) throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> out = new AtomicReference<>(), err = new AtomicReference<>();
        boolean reusable = !timeout || !delayedBody || lateCompletion;
        try (FixtureServer fixture = FixtureServer.startDelayedEvent(delayedBody, lateCompletion ? 2_400 : timeout ? 30_000 : 750);
                ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                    properties(workerJar, fixture.origin(), Duration.ofSeconds(timeout ? 2 : 5)),
                    Clock.systemUTC(), new SecureRandom(), builder -> startObservedWorker(
                            builder, fixture.origin(), browserCache, worker, out, err, readers));
            UUID id = UUID.randomUUID();
            var campaign = supervisor.openLiveGroupedV6(id, LIVE_ENDPOINTS);
            Process exactWorker = worker.get();
            List<ProcessIdentity> tree = captureOwnedProcessTree(exactWorker);
            try {
                var abandonedGroup = new LiveProviderDispatchGroup(id, UUID.randomUUID(), 17_000_006L,
                        LiveProviderDispatchGroup.Phase.CHECK);
                long started = System.nanoTime();
                if (timeout) {
                    assertThatThrownBy(() -> campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(17_000_006L),
                            abandonedGroup, PlaywrightDispatchAdmission.UNRESTRICTED))
                            .isInstanceOfSatisfying(PlaywrightProviderException.class, failure -> {
                                assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.TIMEOUT);
                                assertThat(failure.recoverableTimeout()).isEqualTo(reusable);
                                assertThat(failure.diagnostic().exchangeEndReason()).isEqualTo(!reusable ? null
                                        : lateCompletion ? PlaywrightTransportDiagnostic.ExchangeEndReason.FINISHED
                                        : PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED);
                                assertThat(failure.diagnostic().responseComplete()).isFalse();
                                assertThat(failure.diagnostic().requestTimeoutMillis()).isEqualTo(2_000);
                                assertThat(failure.diagnostic().httpStatus()).isEqualTo(delayedBody ? 200 : null);
                            });
                    assertThat(Duration.ofNanos(System.nanoTime() - started))
                            .isGreaterThanOrEqualTo(Duration.ofMillis(1_800)).isLessThan(Duration.ofSeconds(5));
                    assertThat(fixture.releaseSlowResponse.getCount()).isOne();
                    if (reusable) {
                        assertThatThrownBy(() -> campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(17_000_006L),
                                abandonedGroup, PlaywrightDispatchAdmission.UNRESTRICTED))
                                .isInstanceOfSatisfying(PlaywrightProviderException.class, failure ->
                                        assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.INVALID_REQUEST));
                        fixture.releaseSlowResponse();
                    }
                } else {
                    var response = campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(17_000_006L),
                            abandonedGroup, PlaywrightDispatchAdmission.UNRESTRICTED);
                    assertExactResponse(response, 200, "application/json", EVENT_DETAILS_STOP_RESPONSE);
                    assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofMillis(650))
                            .isLessThan(Duration.ofSeconds(5));
                }
                var next = new LiveProviderDispatchGroup(id, UUID.randomUUID(), 16_386_245L,
                        LiveProviderDispatchGroup.Phase.CHECK);
                if (reusable) {
                    assertExactResponse(campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(16_386_245L), next,
                            PlaywrightDispatchAdmission.UNRESTRICTED), 200, "application/json", EVENT_DETAILS_ONE_RESPONSE);
                    assertThat(exactWorker.isAlive()).isTrue();
                } else {
                    // No terminal CDP event was observed for the committed, incomplete document.
                    // The fatal timeout poisons further admission, even before campaign cleanup.
                    assertThatThrownBy(() -> campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(16_386_245L), next,
                            PlaywrightDispatchAdmission.UNRESTRICTED)).isInstanceOf(PlaywrightProviderException.class);
                    assertThat(fixture.releaseSlowResponse.getCount()).isOne();
                }
                assertThat(worker.get()).isSameAs(exactWorker);
                // The authenticated nonfatal frame required the existing context to pass local cleanup checks.
                assertThat(supervisor.activeCampaignId()).contains(id);
                campaign.close();
                assertWorkerExited(supervisor, exactWorker, tree, out.get(), err.get());
                if (reusable) fixture.assertExactTraffic(FixtureServer.DELAYED_EVENT_PATH, FixtureServer.EVENT_DETAILS_ONE_PATH);
                else fixture.assertExactTraffic(FixtureServer.DELAYED_EVENT_PATH);
                assertThat(fixture.requestCount(FixtureServer.DELAYED_EVENT_PATH)).isOne();
                assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
            } finally {
                supervisor.shutdown(); fixture.releaseSlowResponse();
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {403, 429}) @Timeout(60)
    void liveV6RetainsRefusalBeforeSlowBodyAndBlocksAnotherWrapperBeforeWorkerCreation(int status) throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> out = new AtomicReference<>(), err = new AtomicReference<>();
        var store = new LocalRefusalStore();
        try (FixtureServer fixture = FixtureServer.startIncompleteBody(status);
                ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                    properties(workerJar, fixture.origin(), Duration.ofSeconds(2)), Clock.systemUTC(), new SecureRandom(),
                    builder -> startObservedWorker(builder, fixture.origin(), browserCache, worker, out, err, readers));
            var factory = new ResilientPlaywrightProviderCampaignFactory(supervisor, store);
            UUID id = UUID.randomUUID();
            var campaign = factory.openLiveGroupedV6(id, LIVE_ENDPOINTS);
            Process exactWorker = worker.get();
            List<ProcessIdentity> tree = captureOwnedProcessTree(exactWorker);
            try {
                var group = new LiveProviderDispatchGroup(id, UUID.randomUUID(), 17_000_005L,
                        LiveProviderDispatchGroup.Phase.CHECK);
                assertThatThrownBy(() -> campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(17_000_005L),
                        group, new PlaywrightDispatchAdmission() {
                            public void check() { }
                            public Permit acquireDispatchPermit() { return () -> { }; }
                            public void onTransportProgress(PlaywrightTransportDiagnostic diagnostic) {
                                if (diagnostic.httpStatus() != null) {
                                    assertThat(store.snapshot().state()).isEqualTo(
                                            com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State.SUSPENDED);
                                    assertThat(fixture.releaseSlowResponse.getCount()).isOne();
                                }
                            }
                        })).isInstanceOfSatisfying(PlaywrightProviderException.class, failure -> {
                            assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.TIMEOUT);
                            assertThat(failure.recoverableTimeout()).isFalse();
                            assertThat(failure.diagnostic().httpStatus()).isEqualTo(status);
                            assertThat(failure.diagnostic().retryAfterNotBefore()).isNotNull();
                        });
                campaign.close();
                assertWorkerExited(supervisor, exactWorker, tree, out.get(), err.get());
                var newWrapper = new ResilientPlaywrightProviderCampaignFactory(supervisor, store);
                assertThatThrownBy(() -> newWrapper.openLiveGroupedV6(UUID.randomUUID(), LIVE_ENDPOINTS))
                        .hasMessage("PROVIDER_SUSPENDED");
                assertThat(worker.get()).isSameAs(exactWorker);
                assertThat(fixture.releaseSlowResponse.getCount()).isOne();
                fixture.assertExactTraffic(FixtureServer.INCOMPLETE_BODY_PATH);
                assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
            } finally { supervisor.shutdown(); fixture.releaseSlowResponse(); }
        }
    }

    /** Deterministic shared store for native transport tests; SQL durability is qualified separately. */
    private static final class LocalRefusalStore implements com.bettingproject.sofascorelocal.port.ProviderResilienceStore {
        private com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot state =
                new com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot(
                        com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State.OPEN,
                        0, Instant.now(), null, null, null, null, null, null, null, null);
        public synchronized com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot snapshot() { return state; }
        public synchronized com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureDecision departureDecision(Instant at) {
            boolean allowed = state.state() == com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State.OPEN;
            return new com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureDecision(allowed,
                    allowed ? com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureReason.ALLOWED
                            : com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureReason.PROVIDER_SUSPENDED, at, state);
        }
        public synchronized com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureDecision tryReserveDeparture(UUID id, Instant at) { return departureDecision(at); }
        public synchronized com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot markDepartureFinished(UUID id, Instant at) { return state; }
        public synchronized com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot suspend(UUID evidence, UUID campaign,
                int status, Instant at, Instant retry) {
            state = new com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot(
                    com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State.SUSPENDED,
                    1, at, status, at, retry, null, evidence, campaign, null, null);
            return state;
        }
        public com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.Snapshot rearm(long version, Instant at) { throw new UnsupportedOperationException(); }
    }

    @Test
    @Timeout(90)
    void exercisesOneRealWorkerAcrossExactLoopbackPagesAndLeavesNoOwnedProcess() throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        assertThat(requiredEnvironmentPath("PLAYWRIGHT_BROWSERS_PATH"))
                .isEqualTo(browserCache);

        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();
        try (FixtureServer fixture = FixtureServer.start();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ProviderPlaywrightProperties properties = properties(workerJar, fixture.origin());
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties,
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            List<ProcessIdentity> ownedProcesses;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS))) {
                Process exactWorker = worker.get();
                assertThat(exactWorker).isNotNull();
                assertThat(exactWorker.isAlive()).isTrue();

                PlaywrightProviderResponse pageOne = campaign.execute(
                        PlaywrightProviderRequest.scheduledEvents(DATE, 1));
                assertExactResponse(
                        pageOne, 200, "application/json; charset=utf-8", PAGE_ONE);

                PlaywrightProviderResponse pageTwo = campaign.execute(
                        PlaywrightProviderRequest.scheduledEvents(DATE, 2));
                assertExactResponse(
                        pageTwo, 404, "application/problem+json", PAGE_TWO_404);

                assertThat(worker.get()).isSameAs(exactWorker);
                assertThat(exactWorker.isAlive()).isTrue();
                ownedProcesses = captureOwnedProcessTree(exactWorker);
            }

            Process exactWorker = worker.get();
            assertThat(exactWorker.waitFor(5, TimeUnit.SECONDS)).isTrue();
            assertThat(exactWorker.exitValue()).isZero();
            assertThat(supervisor.activeCampaignId()).isEmpty();
            assertThat(ownedProcesses).allMatch(identity -> !identity.isSameProcessAlive());
            assertThat(standardOutput.get().get(5, TimeUnit.SECONDS)).isEmpty();
            assertThat(standardError.get().get(5, TimeUnit.SECONDS)).isEmpty();
            fixture.assertExactTraffic(FixtureServer.PAGE_ONE_PATH, FixtureServer.PAGE_TWO_PATH);
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void routesTheExactTournamentRequestThroughRealChromiumAndLeavesNoOwnedProcess()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.start();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            Process exactWorker;
            List<ProcessIdentity> ownedProcesses;
            CompletableFuture<byte[]> output;
            CompletableFuture<byte[]> error;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(),
                    Set.of(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS))) {
                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.tournamentScheduledEvents(
                                DATE, 17)),
                        200,
                        "application/json",
                        TOURNAMENT_RESPONSE);
                exactWorker = worker.get();
                ownedProcesses = captureOwnedProcessTree(exactWorker);
                output = standardOutput.get();
                error = standardError.get();
            }

            assertWorkerExited(supervisor, exactWorker, ownedProcesses, output, error);
            fixture.assertExactTraffic(FixtureServer.TOURNAMENT_PATH);
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(120)
    void routesEventDetailsAcrossFreshCampaignsAndPreservesA404Exactly()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.start();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            Process firstWorker;
            ProcessIdentity firstIdentity;
            List<ProcessIdentity> firstOwnedProcesses;
            CompletableFuture<byte[]> firstOutput;
            CompletableFuture<byte[]> firstError;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.EVENT_DETAILS))) {
                firstWorker = worker.get();
                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.eventDetails(16_386_245L)),
                        200,
                        "application/json",
                        EVENT_DETAILS_ONE_RESPONSE);
                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.eventDetails(16_421_052L)),
                        200,
                        "application/json",
                        EVENT_DETAILS_TWO_RESPONSE);
                assertThat(worker.get()).isSameAs(firstWorker);
                firstIdentity = ProcessIdentity.capture(firstWorker.toHandle()).orElseThrow();
                firstOwnedProcesses = captureOwnedProcessTree(firstWorker);
                firstOutput = standardOutput.get();
                firstError = standardError.get();
            }

            assertWorkerExited(
                    supervisor,
                    firstWorker,
                    firstOwnedProcesses,
                    firstOutput,
                    firstError);

            Process secondWorker;
            ProcessIdentity secondIdentity;
            List<ProcessIdentity> secondOwnedProcesses;
            CompletableFuture<byte[]> secondOutput;
            CompletableFuture<byte[]> secondError;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.EVENT_DETAILS))) {
                PlaywrightProviderResponse notFound = campaign.execute(
                        PlaywrightProviderRequest.eventDetails(17_000_001L));
                assertExactResponse(
                        notFound,
                        404,
                        "application/problem+json",
                        EVENT_DETAILS_NOT_FOUND_RESPONSE);
                secondWorker = worker.get();
                secondIdentity = ProcessIdentity.capture(secondWorker.toHandle()).orElseThrow();
                secondOwnedProcesses = captureOwnedProcessTree(secondWorker);
                secondOutput = standardOutput.get();
                secondError = standardError.get();
            }

            assertThat(secondWorker).isNotSameAs(firstWorker);
            assertThat(secondIdentity).isNotEqualTo(firstIdentity);
            assertWorkerExited(
                    supervisor,
                    secondWorker,
                    secondOwnedProcesses,
                    secondOutput,
                    secondError);
            fixture.assertExactTraffic(
                    FixtureServer.EVENT_DETAILS_ONE_PATH,
                    FixtureServer.EVENT_DETAILS_TWO_PATH,
                    FixtureServer.EVENT_DETAILS_NOT_FOUND_PATH);
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(120)
    void routesJ5StatisticsIncidentsAndLineupsThroughOneWorkerAndPreservesA404()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.start();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            Process exactWorker;
            List<ProcessIdentity> ownedProcesses;
            CompletableFuture<byte[]> output;
            CompletableFuture<byte[]> error;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(),
                    Set.of(
                            SofascoreEndpointType.EVENT_STATISTICS,
                            SofascoreEndpointType.EVENT_INCIDENTS,
                            SofascoreEndpointType.EVENT_LINEUPS))) {
                exactWorker = worker.get();
                assertThat(exactWorker).isNotNull();
                assertThat(exactWorker.isAlive()).isTrue();

                PlaywrightProviderResponse statistics = campaign.execute(
                        PlaywrightProviderRequest.eventStatistics(J5_EVENT_ID));
                assertExactResponse(
                        statistics,
                        200,
                        "application/json; charset=utf-8",
                        J5_STATISTICS_RESPONSE);
                assertThat(worker.get()).isSameAs(exactWorker);

                PlaywrightProviderResponse incidents = campaign.execute(
                        PlaywrightProviderRequest.eventIncidents(J5_EVENT_ID));
                assertExactResponse(
                        incidents,
                        404,
                        "application/problem+json",
                        J5_INCIDENTS_NOT_FOUND_RESPONSE);
                assertThat(worker.get()).isSameAs(exactWorker);

                PlaywrightProviderResponse lineups = campaign.execute(
                        PlaywrightProviderRequest.eventLineups(J5_EVENT_ID));
                assertExactResponse(
                        lineups,
                        200,
                        "application/json",
                        J5_LINEUPS_RESPONSE);
                assertThat(worker.get()).isSameAs(exactWorker);
                assertMinimumRequestedAtGap(statistics, incidents);
                assertMinimumRequestedAtGap(incidents, lineups);

                ownedProcesses = captureOwnedProcessTree(exactWorker);
                output = standardOutput.get();
                error = standardError.get();
            }

            assertWorkerExited(
                    supervisor,
                    exactWorker,
                    ownedProcesses,
                    output,
                    error);
            fixture.assertExactTraffic(
                    FixtureServer.J5_STATISTICS_PATH,
                    FixtureServer.J5_INCIDENTS_PATH,
                    FixtureServer.J5_LINEUPS_PATH);
            assertThat(fixture.arrivalGapsNanos())
                    .hasSize(2)
                    .allMatch(gap -> gap >= MINIMUM_PROVIDER_START_GAP.toNanos());
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void preserves403RateLimit5xxAndHtmlBytesExactlyWithoutRetry() throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            assertResponseAndCleanup(
                    fixture, workerJar, browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 3),
                    403, "application/problem+json", FORBIDDEN_RESPONSE);
            assertResponseAndCleanup(
                    fixture, workerJar, browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 4),
                    429, "application/problem+json", RATE_LIMIT_RESPONSE);
            assertResponseAndCleanup(
                    fixture, workerJar, browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 5),
                    503, "application/problem+json", SERVER_ERROR_RESPONSE);
            assertResponseAndCleanup(
                    fixture, workerJar, browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 10),
                    200, "text/html; charset=utf-8", HTML_CHALLENGE_RESPONSE);
            fixture.assertExactTraffic(
                    FixtureServer.PAGE_THREE_PATH,
                    FixtureServer.PAGE_FOUR_PATH,
                    FixtureServer.PAGE_FIVE_PATH,
                    FixtureServer.HTML_CHALLENGE_PATH);
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void blocksARedirectBeforeItsTargetAndCleansTheExactProcessTree() throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            assertFailureAndCleanup(
                    fixture,
                    workerJar,
                    browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 6),
                    Duration.ofSeconds(10),
                    PlaywrightProviderFailure.REDIRECT_BLOCKED);
            fixture.assertExactTraffic(FixtureServer.REDIRECT_PATH);
            assertThat(fixture.requestCount(FixtureServer.REDIRECT_TARGET_PATH)).isZero();
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void blocksASecondarySubrequestBeforeNetworkAndCleansTheExactProcessTree()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            assertFailureAndCleanup(
                    fixture,
                    workerJar,
                    browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 7),
                    Duration.ofSeconds(10),
                    PlaywrightProviderFailure.UNEXPECTED_ROUTE);
            fixture.assertExactTraffic(FixtureServer.SECONDARY_ROUTE_PATH);
            assertThat(fixture.requestCount(FixtureServer.SECONDARY_TARGET_PATH)).isZero();
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void blocksASecondNavigationToTheSameExactUriBeforeNetworkAndCleansTheProcessTree()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            assertFailureAndCleanup(
                    fixture,
                    workerJar,
                    browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 12),
                    Duration.ofSeconds(10),
                    PlaywrightProviderFailure.UNEXPECTED_ROUTE);
            fixture.assertExactTraffic(FixtureServer.SAME_URI_SECONDARY_ROUTE_PATH);
            assertThat(fixture.requestCount(FixtureServer.SAME_URI_SECONDARY_ROUTE_PATH)).isOne();
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void timesOutARealChromiumRequestWithoutRetryAndCleansTheExactProcessTree()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            assertFailureAndCleanup(
                    fixture,
                    workerJar,
                    browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 8),
                    Duration.ofMillis(250),
                    PlaywrightProviderFailure.TIMEOUT);
            fixture.assertExactTraffic(FixtureServer.TIMEOUT_PATH);
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void acceptsALiveV5J4ResponseAfterTwelveSecondsWithTwentySecondTimeout()
            throws Exception {
        assertLiveV5TwentySecondTimeout(true);
    }

    @Test
    @Timeout(90)
    void timesOutALiveV5J4RequestAtTwentySecondsWithoutRetryAndCleansTheExactProcessTree()
            throws Exception {
        assertLiveV5TwentySecondTimeout(false);
    }

    @Test
    @Timeout(90)
    void rejectsARealResponseOverFiveMibAndCleansTheExactProcessTree() throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            assertFailureAndCleanup(
                    fixture,
                    workerJar,
                    browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 9),
                    Duration.ofSeconds(10),
                    PlaywrightProviderFailure.PAYLOAD_TOO_LARGE);
            fixture.assertExactTraffic(FixtureServer.OVERSIZED_PATH);
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(90)
    void rejectsASensitiveCanaryWithoutWritingItToWorkerStreamsOrRuntimeFiles()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");

        try (FixtureServer fixture = FixtureServer.start()) {
            String sensitiveCanaryValue = fixture.sensitiveCanaryValue();
            assertFailureAndCleanup(
                    fixture,
                    workerJar,
                    browserCache,
                    PlaywrightProviderRequest.scheduledEvents(DATE, 11),
                    Duration.ofSeconds(10),
                    PlaywrightProviderFailure.SENSITIVE_CONTENT_REJECTED,
                    sensitiveCanaryValue);
            fixture.assertExactTraffic(FixtureServer.SENSITIVE_CANARY_PATH);
            assertNoForbiddenRuntimeArtifacts(
                    RUNTIME_SANDBOX_ROOT, sensitiveCanaryValue);
        }
    }

    @Test
    @Timeout(120)
    void createsANewWorkerAndContextForTheNextExplicitCampaign() throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.start();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            Process firstWorker;
            ProcessIdentity firstIdentity;
            List<ProcessIdentity> firstTree;
            CompletableFuture<byte[]> firstOutput;
            CompletableFuture<byte[]> firstError;
            PlaywrightProviderResponse firstResponse;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS))) {
                firstResponse = campaign.execute(
                        PlaywrightProviderRequest.scheduledEvents(DATE, 1));
                assertExactResponse(
                        firstResponse,
                        200,
                        "application/json; charset=utf-8",
                        PAGE_ONE);
                firstWorker = worker.get();
                firstIdentity = ProcessIdentity.capture(firstWorker.toHandle()).orElseThrow();
                firstTree = captureOwnedProcessTree(firstWorker);
                firstOutput = standardOutput.get();
                firstError = standardError.get();
            }
            assertWorkerExited(supervisor, firstWorker, firstTree, firstOutput, firstError);

            Process secondWorker;
            ProcessIdentity secondIdentity;
            List<ProcessIdentity> secondTree;
            CompletableFuture<byte[]> secondOutput;
            CompletableFuture<byte[]> secondError;
            PlaywrightProviderResponse secondResponse;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS))) {
                secondResponse = campaign.execute(
                        PlaywrightProviderRequest.scheduledEvents(DATE, 1));
                assertExactResponse(
                        secondResponse,
                        200,
                        "application/json; charset=utf-8",
                        PAGE_ONE);
                secondWorker = worker.get();
                secondIdentity = ProcessIdentity.capture(secondWorker.toHandle()).orElseThrow();
                secondTree = captureOwnedProcessTree(secondWorker);
                secondOutput = standardOutput.get();
                secondError = standardError.get();
            }

            assertThat(secondWorker).isNotSameAs(firstWorker);
            assertThat(secondIdentity).isNotEqualTo(firstIdentity);
            assertMinimumRequestedAtGap(firstResponse, secondResponse);
            assertWorkerExited(supervisor, secondWorker, secondTree, secondOutput, secondError);
            fixture.assertExactTraffic(
                    FixtureServer.PAGE_ONE_PATH,
                    FixtureServer.PAGE_ONE_PATH);
            assertThat(fixture.arrivalGapsNanos())
                    .hasSize(1)
                    .allMatch(gap -> gap >= MINIMUM_PROVIDER_START_GAP.toNanos());
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    @Timeout(120)
    void stopsARealInFlightEventDetailsWorkerWithinEveryBoundAndLeavesNoOwnedProcess()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.startSlowEventDetails();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));
            UUID campaignId = UUID.randomUUID();
            Set<SofascoreEndpointType> allowlist =
                    Set.of(SofascoreEndpointType.EVENT_DETAILS);
            PlaywrightProviderCampaign campaign = supervisor.open(campaignId, allowlist);
            Process exactWorker = worker.get();
            List<ProcessIdentity> ownedProcesses = captureOwnedProcessTree(exactWorker);
            CompletableFuture<Throwable> execution = CompletableFuture.supplyAsync(() -> {
                try {
                    campaign.execute(PlaywrightProviderRequest.eventDetails(17_000_002L));
                    return null;
                }
                catch (Throwable failure) {
                    return failure;
                }
            });
            try {
                assertThat(fixture.awaitSlowRequest(Duration.ofSeconds(10))).isTrue();
                long stopStartedAt = System.nanoTime();
                PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                        campaignId, allowlist);
                Duration acknowledgement = Duration.ofNanos(System.nanoTime() - stopStartedAt);
                Throwable executionFailure = execution.get(2, TimeUnit.SECONDS);
                Duration cancellation = Duration.ofNanos(System.nanoTime() - stopStartedAt);

                assertThat(receipt.activeCampaignSignalled()).isTrue();
                assertThat(receipt.acknowledgementLatency()).isLessThanOrEqualTo(
                        Duration.ofMillis(500));
                assertThat(acknowledgement).isLessThanOrEqualTo(Duration.ofMillis(500));
                assertThat(executionFailure).isInstanceOf(PlaywrightProviderException.class);
                assertThat(((PlaywrightProviderException) executionFailure).failure())
                        .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
                assertThat(cancellation).isLessThanOrEqualTo(Duration.ofSeconds(2));
                assertThat(awaitCleanup(supervisor, exactWorker, ownedProcesses,
                        Duration.ofSeconds(5))).isTrue();
                assertThat(Duration.ofNanos(System.nanoTime() - stopStartedAt))
                        .isLessThanOrEqualTo(Duration.ofSeconds(5));
                assertThat(standardOutput.get().get(5, TimeUnit.SECONDS)).isEmpty();
                assertThat(standardError.get().get(5, TimeUnit.SECONDS)).isEmpty();
                fixture.assertExactTraffic(FixtureServer.EVENT_DETAILS_STOP_PATH);
                assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
            }
            finally {
                fixture.releaseSlowResponse();
                campaign.close();
            }
        }
    }

    @Test
    @Timeout(120)
    void stopsJ5DuringMinimumDelayFenceWithinEveryBoundWithoutStartingNextRequest()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.start();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));
            UUID campaignId = UUID.randomUUID();
            Set<SofascoreEndpointType> allowlist = Set.of(
                    SofascoreEndpointType.EVENT_STATISTICS,
                    SofascoreEndpointType.EVENT_INCIDENTS,
                    SofascoreEndpointType.EVENT_LINEUPS);
            PlaywrightProviderCampaign campaign = supervisor.open(campaignId, allowlist);
            Process exactWorker = worker.get();
            assertExactResponse(
                    campaign.execute(PlaywrightProviderRequest.eventStatistics(
                            J5_STOP_EVENT_ID)),
                    200,
                    "application/json",
                    J5_STOP_STATISTICS_RESPONSE);
            assertThat(worker.get()).isSameAs(exactWorker);
            List<ProcessIdentity> ownedProcesses = captureOwnedProcessTree(exactWorker);
            CountDownLatch fencedExecutionStarted = new CountDownLatch(1);
            CompletableFuture<Throwable> execution = CompletableFuture.supplyAsync(() -> {
                try {
                    fencedExecutionStarted.countDown();
                    campaign.execute(PlaywrightProviderRequest.eventIncidents(
                            J5_STOP_EVENT_ID));
                    return null;
                }
                catch (Throwable failure) {
                    return failure;
                }
            });
            try {
                assertThat(fencedExecutionStarted.await(1, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> execution.get(
                        FENCE_OBSERVATION_WINDOW.toNanos(), TimeUnit.NANOSECONDS))
                        .isInstanceOf(TimeoutException.class);
                assertThat(fixture.requestCount(FixtureServer.J5_STOP_INCIDENTS_PATH)).isZero();
                assertThat(fixture.requestCount(FixtureServer.J5_STOP_LINEUPS_PATH)).isZero();
                long stopStartedAt = System.nanoTime();
                PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(
                        campaignId, allowlist);
                Duration acknowledgement = Duration.ofNanos(
                        System.nanoTime() - stopStartedAt);
                Throwable executionFailure = execution.get(2, TimeUnit.SECONDS);
                Duration cancellation = Duration.ofNanos(System.nanoTime() - stopStartedAt);

                assertThat(receipt.activeCampaignSignalled()).isTrue();
                assertThat(receipt.acknowledgementLatency()).isLessThanOrEqualTo(
                        Duration.ofMillis(500));
                assertThat(acknowledgement).isLessThanOrEqualTo(Duration.ofMillis(500));
                assertThat(executionFailure).isInstanceOf(PlaywrightProviderException.class);
                assertThat(((PlaywrightProviderException) executionFailure).failure())
                        .isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP);
                assertThat(cancellation).isLessThanOrEqualTo(Duration.ofSeconds(2));
                assertThat(awaitCleanup(
                        supervisor,
                        exactWorker,
                        ownedProcesses,
                        Duration.ofSeconds(5))).isTrue();
                assertThat(Duration.ofNanos(System.nanoTime() - stopStartedAt))
                        .isLessThanOrEqualTo(Duration.ofSeconds(5));
                assertThat(worker.get()).isSameAs(exactWorker);
                assertThat(standardOutput.get().get(5, TimeUnit.SECONDS)).isEmpty();
                assertThat(standardError.get().get(5, TimeUnit.SECONDS)).isEmpty();
                fixture.assertExactTraffic(FixtureServer.J5_STOP_STATISTICS_PATH);
                assertThat(fixture.requestCount(FixtureServer.J5_STOP_INCIDENTS_PATH)).isZero();
                assertThat(fixture.requestCount(FixtureServer.J5_STOP_LINEUPS_PATH)).isZero();
                assertThat(fixture.arrivalGapsNanos()).isEmpty();
                assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
            }
            finally {
                campaign.close();
            }
        }
    }

    private static void assertResponseAndCleanup(
            FixtureServer fixture,
            Path workerJar,
            Path browserCache,
            PlaywrightProviderRequest request,
            int expectedStatus,
            String expectedContentType,
            byte[] expectedBody) throws Exception {
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();
        try (ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin()),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            Process exactWorker;
            List<ProcessIdentity> ownedProcesses;
            CompletableFuture<byte[]> output;
            CompletableFuture<byte[]> error;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(request.endpoint()))) {
                assertExactResponse(
                        campaign.execute(request),
                        expectedStatus,
                        expectedContentType,
                        expectedBody);
                exactWorker = worker.get();
                ownedProcesses = captureOwnedProcessTree(exactWorker);
                output = standardOutput.get();
                error = standardError.get();
            }

            assertWorkerExited(supervisor, exactWorker, ownedProcesses, output, error);
        }
    }

    private static void assertLiveV5TwentySecondTimeout(boolean releaseAfterTwelveSeconds)
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();
        try (FixtureServer fixture = FixtureServer.startSlowEventDetails();
             ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin(), Duration.ofSeconds(20)),
                            Clock.systemUTC(), new SecureRandom(),
                            builder -> startObservedWorker(builder, fixture.origin(), browserCache,
                                    worker, standardOutput, standardError, streamReaders));
            UUID campaignId = UUID.randomUUID();
            Process exactWorker;
            List<ProcessIdentity> ownedProcesses;
            try (PlaywrightProviderCampaign campaign = supervisor.openLiveGroupedV5(campaignId,
                    Set.of(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS))) {
                exactWorker = worker.get();
                ownedProcesses = captureOwnedProcessTree(exactWorker);
                CompletableFuture<Void> responseRelease = releaseAfterTwelveSeconds
                        ? CompletableFuture.runAsync(() -> {
                            try {
                                assertThat(fixture.awaitSlowRequest(Duration.ofSeconds(10))).isTrue();
                                Thread.sleep(Duration.ofSeconds(12));
                                fixture.releaseSlowResponse();
                            }
                            catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("local fixture release interrupted");
                            }
                        }, streamReaders) : CompletableFuture.completedFuture(null);
                var group = new LiveProviderDispatchGroup(campaignId, UUID.randomUUID(),
                        17_000_002L, LiveProviderDispatchGroup.Phase.CHECK);
                long started = System.nanoTime();
                if (releaseAfterTwelveSeconds) {
                    PlaywrightProviderResponse response = campaign.executeGrouped(
                            PlaywrightProviderRequest.eventDetails(17_000_002L), group,
                            PlaywrightDispatchAdmission.UNRESTRICTED);
                    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                    responseRelease.get(1, TimeUnit.SECONDS);
                    assertExactResponse(response, 200, "application/json", EVENT_DETAILS_STOP_RESPONSE);
                    assertThat(response.latency()).isGreaterThanOrEqualTo(Duration.ofSeconds(12));
                    assertThat(elapsed).isLessThan(Duration.ofSeconds(20));
                    System.out.printf(Locale.ROOT,
                            "LIVE_V5_TIMEOUT_20S delayed_response=RECEIVED_HTTP_200 elapsedMillis=%d%n",
                            elapsed.toMillis());
                }
                else {
                    assertThatThrownBy(() -> campaign.executeGrouped(
                            PlaywrightProviderRequest.eventDetails(17_000_002L), group,
                            PlaywrightDispatchAdmission.UNRESTRICTED))
                            .isInstanceOfSatisfying(PlaywrightProviderException.class,
                                    failure -> assertThat(failure.failure())
                                            .isEqualTo(PlaywrightProviderFailure.TIMEOUT));
                    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
                    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(19_500))
                            .isLessThan(Duration.ofSeconds(25));
                    System.out.printf(Locale.ROOT,
                            "LIVE_V5_TIMEOUT_20S withheld_response=TIMEOUT elapsedMillis=%d%n",
                            elapsed.toMillis());
                }
            }
            finally {
                fixture.releaseSlowResponse();
            }
            assertThat(awaitCleanup(supervisor, exactWorker, ownedProcesses, Duration.ofSeconds(5)))
                    .isTrue();
            assertWorkerExited(supervisor, exactWorker, ownedProcesses,
                    standardOutput.get(), standardError.get());
            fixture.assertExactTraffic(FixtureServer.EVENT_DETAILS_STOP_PATH);
            assertThat(fixture.requestCount(FixtureServer.EVENT_DETAILS_STOP_PATH)).isOne();
            assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
        }
    }

    @Test
    void preservesForbiddenHeadersBeforeAnIncompleteBodyTimesOut() throws Exception {
        assertIncompleteBodyDiagnostic(403, false);
    }

    @Test
    void preservesRateLimitAndRetryAfterBeforeAnIncompleteBodyTimesOut() throws Exception {
        assertIncompleteBodyDiagnostic(429, false);
    }

    @Test
    void distinguishesSuccessfulHeadersFromACompleteResponseWhenTheBodyTimesOut() throws Exception {
        assertIncompleteBodyDiagnostic(200, false);
    }

    @Test
    @Timeout(60)
    void keepsTheCampaignExcludedWhenGracefulCloseCannotAuthenticateAfterAnIncompleteBodyTimeout()
            throws Exception {
        assertIncompleteBodyDiagnostic(403, true);
    }

    private void assertIncompleteBodyDiagnostic(int status, boolean verifyConservativeGracefulClose)
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();
        List<PlaywrightTransportDiagnostic> progress = new CopyOnWriteArrayList<>();
        CountDownLatch headersObserved = new CountDownLatch(1);
        try (FixtureServer fixture = FixtureServer.startIncompleteBody(status);
                ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var supervisor = new ChildJvmPlaywrightProviderSupervisor(
                    properties(workerJar, fixture.origin(), Duration.ofSeconds(2)), Clock.systemUTC(), new SecureRandom(),
                    builder -> startObservedWorker(builder, fixture.origin(), browserCache,
                            worker, standardOutput, standardError, readers));
            UUID campaignId = UUID.randomUUID();
            Set<SofascoreEndpointType> allowlist = Set.of(SofascoreEndpointType.EVENT_DETAILS);
            var campaign = supervisor.open(campaignId, allowlist);
            Process exactWorker = worker.get();
            List<ProcessIdentity> ownedProcesses = captureOwnedProcessTree(exactWorker);
            try {
                long started = System.nanoTime();
                CompletableFuture<Throwable> result = CompletableFuture.supplyAsync(() -> {
                    try {
                        campaign.execute(PlaywrightProviderRequest.eventDetails(17_000_005L), new PlaywrightDispatchAdmission() {
                            public void check() { }
                            public Permit acquireDispatchPermit() { return () -> { }; }
                            public void onTransportProgress(PlaywrightTransportDiagnostic diagnostic) {
                                progress.add(diagnostic);
                                if (diagnostic.httpStatus() != null) headersObserved.countDown();
                            }
                        });
                        return null;
                    } catch (Throwable failure) { return failure; }
                }, readers);
                assertThat(headersObserved.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
                var headers = progress.stream().filter(d -> d.httpStatus() != null).findFirst().orElseThrow();
                assertThat(headers.phase()).isEqualTo(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED);
                assertThat(headers.httpStatus()).isEqualTo(status);
                assertThat(headers.responseComplete()).isFalse();
                assertThat(headers.retryAfterNotBefore()).isEqualTo(headers.headersReceivedAt().plusSeconds(60));
                assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOfSatisfying(PlaywrightProviderException.class, failure -> {
                    assertThat(failure.failure()).isIn(PlaywrightProviderFailure.TIMEOUT, PlaywrightProviderFailure.IPC_TIMEOUT);
                    if (verifyConservativeGracefulClose) {
                        assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.IPC_TIMEOUT);
                    }
                    assertThat(failure.diagnostic().httpStatus()).isEqualTo(status);
                    assertThat(failure.diagnostic().requestTimeoutMillis()).isEqualTo(2_000);
                    assertThat(failure.diagnostic().responseComplete()).isFalse();
                    assertThat(failure.diagnostic().retryAfterNotBefore()).isEqualTo(headers.retryAfterNotBefore());
                });
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
                assertThat(fixture.releaseSlowResponse.getCount()).isOne();
                if (verifyConservativeGracefulClose) {
                    // The ordinary live cleanup uses close(), not an operator-stop upgrade.
                    // A missing authenticated terminal frame must not release its exclusion.
                    assertThatThrownBy(campaign::close)
                            .isInstanceOfSatisfying(PlaywrightProviderException.class, failure ->
                                    assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.RUNTIME_FAILURE));
                    assertThat(supervisor.activeCampaignId()).contains(campaignId);
                    assertThat(awaitOwnedProcessAbsence(exactWorker, ownedProcesses, Duration.ofSeconds(5)))
                            .as("physical process absence does not authenticate graceful cleanup")
                            .isTrue();
                    assertThatThrownBy(() -> supervisor.open(UUID.randomUUID(), allowlist))
                            .isInstanceOfSatisfying(PlaywrightProviderException.class, failure ->
                                    assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.CAMPAIGN_ALREADY_ACTIVE));
                    assertThatThrownBy(campaign::close)
                            .isInstanceOfSatisfying(PlaywrightProviderException.class, failure ->
                                    assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.RUNTIME_FAILURE));
                    assertThat(supervisor.activeCampaignId()).contains(campaignId);
                    assertThat(standardOutput.get().get(5, TimeUnit.SECONDS)).isEmpty();
                    assertThat(standardError.get().get(5, TimeUnit.SECONDS)).isEmpty();
                }
                else {
                    // Explicit local cancellation is independently qualified while the fixture
                    // is still withholding its body. It must not rely on an artificial response.
                    long stopStartedAt = System.nanoTime();
                    PlaywrightProviderStopReceipt receipt = supervisor.stopCampaign(campaignId, allowlist);
                    assertThat(receipt.activeCampaignSignalled()).isTrue();
                    assertThat(receipt.acknowledgementLatency()).isLessThanOrEqualTo(Duration.ofMillis(500));
                    assertThat(Duration.ofNanos(System.nanoTime() - stopStartedAt))
                            .isLessThanOrEqualTo(Duration.ofMillis(500));
                    assertThat(awaitOwnedProcessAbsence(exactWorker, ownedProcesses, Duration.ofSeconds(2))).isTrue();
                    assertThat(Duration.ofNanos(System.nanoTime() - stopStartedAt))
                            .isLessThanOrEqualTo(Duration.ofSeconds(2));
                    assertThat(awaitCleanup(supervisor, exactWorker, ownedProcesses, Duration.ofSeconds(5))).isTrue();
                    assertThat(Duration.ofNanos(System.nanoTime() - stopStartedAt))
                            .isLessThanOrEqualTo(Duration.ofSeconds(5));
                    campaign.close();
                    assertWorkerExited(supervisor, exactWorker, ownedProcesses, standardOutput.get(), standardError.get());
                }
                assertThat(fixture.releaseSlowResponse.getCount()).isOne();
                assertThat(worker.get()).isSameAs(exactWorker);
                fixture.assertExactTraffic(FixtureServer.INCOMPLETE_BODY_PATH);
                assertThat(fixture.requestCount(FixtureServer.INCOMPLETE_BODY_PATH)).isOne();
                assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
            }
            finally {
                // Shutdown is cleanup of this exact supervisor only; it never opens a worker.
                // It also deliberately retains an already inconclusive graceful-close state.
                supervisor.shutdown();
                fixture.releaseSlowResponse();
            }
        }
    }

    private static void assertFailureAndCleanup(
            FixtureServer fixture,
            Path workerJar,
            Path browserCache,
            PlaywrightProviderRequest request,
            Duration requestTimeout,
            PlaywrightProviderFailure expectedFailure) throws Exception {
        assertFailureAndCleanup(
                fixture,
                workerJar,
                browserCache,
                request,
                requestTimeout,
                expectedFailure,
                null);
    }

    private static void assertFailureAndCleanup(
            FixtureServer fixture,
            Path workerJar,
            Path browserCache,
            PlaywrightProviderRequest request,
            Duration requestTimeout,
            PlaywrightProviderFailure expectedFailure,
            String sensitiveCanaryValue) throws Exception {
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();
        try (ExecutorService streamReaders = Executors.newVirtualThreadPerTaskExecutor()) {
            ChildJvmPlaywrightProviderSupervisor supervisor =
                    new ChildJvmPlaywrightProviderSupervisor(
                            properties(workerJar, fixture.origin(), requestTimeout),
                            Clock.systemUTC(),
                            new SecureRandom(),
                            builder -> startObservedWorker(
                                    builder,
                                    fixture.origin(),
                                    browserCache,
                                    worker,
                                    standardOutput,
                                    standardError,
                                    streamReaders));

            Process exactWorker;
            List<ProcessIdentity> ownedProcesses;
            CompletableFuture<byte[]> output;
            CompletableFuture<byte[]> error;
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(request.endpoint()))) {
                exactWorker = worker.get();
                ownedProcesses = captureOwnedProcessTree(exactWorker);
                output = standardOutput.get();
                error = standardError.get();
                PlaywrightProviderException observed = null;
                try {
                    campaign.execute(request);
                }
                catch (PlaywrightProviderException failure) {
                    observed = failure;
                }
                assertThat(observed).isNotNull();
                assertThat(observed.failure()).isEqualTo(expectedFailure);
                if (expectedFailure == PlaywrightProviderFailure.TIMEOUT) {
                    assertThat(observed.diagnostic()).isNotNull();
                    assertThat(observed.diagnostic().phase()).isEqualTo(PlaywrightTransportDiagnostic.Phase.REQUEST_SENT);
                    assertThat(observed.diagnostic().httpStatus()).isNull();
                    assertThat(observed.diagnostic().responseComplete()).isFalse();
                    assertThat(observed.diagnostic().requestTimeoutMillis()).isEqualTo((int) requestTimeout.toMillis());
                }
                assertThat(observed.getMessage())
                        .doesNotContain("local-only-value")
                        .doesNotContain("access_token");
                if (sensitiveCanaryValue != null) {
                    assertThat(observed.getMessage()).doesNotContain(sensitiveCanaryValue);
                }
            }

            assertWorkerExited(supervisor, exactWorker, ownedProcesses, output, error);
        }
    }

    private static ProviderPlaywrightProperties properties(Path workerJar, String loopbackOrigin) {
        return properties(workerJar, loopbackOrigin, Duration.ofSeconds(10));
    }

    private static ProviderPlaywrightProperties properties(
            Path workerJar,
            String loopbackOrigin,
            Duration requestTimeout) {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        properties.setWorkerJar(workerJar);
        properties.setMaximumHeapMib(192);
        properties.setStartupTimeout(Duration.ofSeconds(30));
        properties.setRequestTimeout(requestTimeout);
        properties.setGracefulCloseTimeout(Duration.ofSeconds(5));
        properties.setLoopbackQualification(true);
        properties.setLoopbackOrigin(loopbackOrigin);
        assertThat(properties.isSafeConfiguration()).isTrue();
        return properties;
    }

    private static Process startObservedWorker(
            ProcessBuilder builder,
            String loopbackOrigin,
            Path browserCache,
            AtomicReference<Process> worker,
            AtomicReference<CompletableFuture<byte[]>> standardOutput,
            AtomicReference<CompletableFuture<byte[]>> standardError,
            ExecutorService streamReaders) throws IOException {
        assertThat(builder.command())
                .noneMatch(argument -> argument.contains("http://")
                        || argument.contains("https://")
                        || argument.contains("{\"")
                        || argument.contains("cookie"));
        Map<String, String> environment = builder.environment();
        assertThat(environment.get("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD")).isEqualTo("1");
        assertThat(environment.get("SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION"))
                .isEqualTo("true");
        assertThat(environment.get("SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN"))
                .isEqualTo(loopbackOrigin);
        assertThat(Path.of(environment.get("PLAYWRIGHT_BROWSERS_PATH"))
                .toAbsolutePath().normalize()).isEqualTo(browserCache);
        assertThat(environment).doesNotContainKeys(
                "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY",
                "SOFASCORE_BASE_URL", "SOFASCORE_ALLOWED_ENDPOINTS");

        Path runtimeSandbox = Files.createDirectories(
                RUNTIME_SANDBOX_ROOT.resolve(UUID.randomUUID().toString()));
        Path temporaryDirectory = Files.createDirectory(runtimeSandbox.resolve("temp"));
        Path profileDirectory = Files.createDirectory(runtimeSandbox.resolve("profile"));
        Path localAppDataDirectory = Files.createDirectory(
                runtimeSandbox.resolve("local-app-data"));
        builder.directory(runtimeSandbox.toFile());
        environment.put("TEMP", temporaryDirectory.toString());
        environment.put("TMP", temporaryDirectory.toString());
        environment.put("TMPDIR", temporaryDirectory.toString());
        environment.put("USERPROFILE", profileDirectory.toString());
        environment.put("HOME", profileDirectory.toString());
        environment.put("LOCALAPPDATA", localAppDataDirectory.toString());

        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = builder.start();
        worker.set(process);
        standardOutput.set(CompletableFuture.supplyAsync(
                () -> readAll(process.getInputStream()), streamReaders));
        standardError.set(CompletableFuture.supplyAsync(
                () -> readAll(process.getErrorStream()), streamReaders));
        return process;
    }

    private static byte[] readAll(java.io.InputStream stream) {
        try (stream) {
            return stream.readAllBytes();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void assertExactResponse(
            PlaywrightProviderResponse response,
            int expectedStatus,
            String expectedContentType,
            byte[] expectedBody) {
        assertThat(response.httpStatus()).isEqualTo(expectedStatus);
        assertThat(response.contentType()).isEqualTo(expectedContentType);
        assertThat(response.payload().sizeBytes()).isEqualTo(expectedBody.length);
        assertThat(response.payload().bytes()).containsExactly(expectedBody);
        assertThat(response.receivedAt()).isAfterOrEqualTo(response.requestedAt());
        assertThat(response.latency()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    private static void assertMinimumRequestedAtGap(
            PlaywrightProviderResponse previous,
            PlaywrightProviderResponse next) {
        assertThat(Duration.between(previous.requestedAt(), next.requestedAt()))
                .isGreaterThanOrEqualTo(MINIMUM_PROVIDER_START_GAP);
    }

    private static List<ProcessIdentity> captureOwnedProcessTree(Process process) {
        try (Stream<ProcessHandle> descendants = process.toHandle().descendants()) {
            List<ProcessIdentity> result = new ArrayList<>();
            descendants.map(ProcessIdentity::capture)
                    .flatMap(java.util.Optional::stream)
                    .forEach(result::add);
            ProcessIdentity.capture(process.toHandle()).ifPresent(result::add);
            return List.copyOf(result);
        }
    }

    private static void assertWorkerExited(
            ChildJvmPlaywrightProviderSupervisor supervisor,
            Process worker,
            List<ProcessIdentity> ownedProcesses,
            CompletableFuture<byte[]> standardOutput,
            CompletableFuture<byte[]> standardError) throws Exception {
        assertThat(worker.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(supervisor.activeCampaignId()).isEmpty();
        assertThat(ownedProcesses).allMatch(identity -> !identity.isSameProcessAlive());
        assertThat(standardOutput.get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(standardError.get(5, TimeUnit.SECONDS)).isEmpty();
    }

    private static boolean awaitCleanup(
            ChildJvmPlaywrightProviderSupervisor supervisor,
            Process worker,
            List<ProcessIdentity> ownedProcesses,
            Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean allGone = !worker.isAlive()
                    && ownedProcesses.stream().noneMatch(ProcessIdentity::isSameProcessAlive)
                    && supervisor.activeCampaignId().isEmpty();
            if (allGone) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static boolean awaitOwnedProcessAbsence(
            Process worker,
            List<ProcessIdentity> ownedProcesses,
            Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!worker.isAlive()
                    && ownedProcesses.stream().noneMatch(ProcessIdentity::isSameProcessAlive)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static Path requiredRegularFile(String propertyName) throws IOException {
        String configured = System.getProperty(propertyName, "");
        assertThat(configured)
                .as("%s must be supplied by the explicit Maven profile", propertyName)
                .isNotBlank();
        Path path = Path.of(configured).toAbsolutePath().normalize().toRealPath();
        assertThat(path).isRegularFile();
        return path;
    }

    private static Path requiredDirectory(String propertyName) throws IOException {
        String configured = System.getProperty(propertyName, "");
        assertThat(configured)
                .as("%s must be supplied by the explicit qualification command", propertyName)
                .isNotBlank()
                .doesNotContain("${");
        Path path = Path.of(configured).toAbsolutePath().normalize().toRealPath();
        assertThat(path).isDirectory();
        return path;
    }

    private static Path requiredEnvironmentPath(String name) throws IOException {
        String configured = System.getenv(name);
        assertThat(configured).as("%s must be explicit", name).isNotBlank();
        return Path.of(configured).toAbsolutePath().normalize().toRealPath();
    }

    private static void assertNoForbiddenRuntimeArtifacts(Path root) throws IOException {
        assertNoForbiddenRuntimeArtifacts(root, null);
    }

    private static void assertNoForbiddenRuntimeArtifacts(
            Path root,
            String sensitiveCanaryValue) throws IOException {
        assertThat(Files.isDirectory(root))
                .as("the isolated worker runtime root must exist")
                .isTrue();
        List<Path> regularFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            regularFiles = paths.filter(Files::isRegularFile).toList();
            List<Path> forbiddenArtifacts = new ArrayList<>();
            for (Path path : regularFiles) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (isForbiddenArtifact(name)
                        && !isExactPackagedPlaywrightResource(root, path)) {
                    forbiddenArtifacts.add(root.relativize(path));
                }
            }
            assertThat(forbiddenArtifacts).isEmpty();
        }
        if (sensitiveCanaryValue == null) {
            return;
        }
        byte[] canary = sensitiveCanaryValue.getBytes(StandardCharsets.UTF_8);
        try {
            List<Path> containingCanary = new ArrayList<>();
            for (Path file : regularFiles) {
                if (containsSequence(file, canary)) {
                    containingCanary.add(file);
                }
            }
            assertThat(containingCanary)
                    .as("the per-run sensitive canary must not be persisted in runtime files")
                    .isEmpty();
        }
        finally {
            Arrays.fill(canary, (byte) 0);
        }
    }

    private static boolean containsSequence(Path file, byte[] needle) throws IOException {
        int[] prefixLengths = new int[needle.length];
        for (int i = 1, matched = 0; i < needle.length; i++) {
            while (matched > 0 && needle[i] != needle[matched]) {
                matched = prefixLengths[matched - 1];
            }
            if (needle[i] == needle[matched]) {
                matched++;
            }
            prefixLengths[i] = matched;
        }
        try (java.io.InputStream input = new java.io.BufferedInputStream(
                Files.newInputStream(file), 64 * 1024)) {
            int matched = 0;
            for (int value = input.read(); value >= 0; value = input.read()) {
                byte current = (byte) value;
                while (matched > 0 && current != needle[matched]) {
                    matched = prefixLengths[matched - 1];
                }
                if (current == needle[matched]) {
                    matched++;
                }
                if (matched == needle.length) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isForbiddenArtifact(String name) {
        return name.endsWith(".har")
                || name.endsWith(".webm")
                || name.endsWith(".mp4")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".trace")
                || name.endsWith(".download")
                || name.contains("storage-state")
                || name.contains("storagestate");
    }

    private static boolean isExactPackagedPlaywrightResource(Path root, Path path)
            throws IOException {
        Path relative = root.relativize(path);
        if (relative.getNameCount() < 5
                || !"temp".equals(relative.getName(1).toString())
                || !relative.getName(2).toString().startsWith("playwright-java-")) {
            return false;
        }
        try {
            UUID.fromString(relative.getName(0).toString());
        }
        catch (IllegalArgumentException exception) {
            return false;
        }

        StringBuilder extractedResource = new StringBuilder();
        for (int index = 3; index < relative.getNameCount(); index++) {
            if (!extractedResource.isEmpty()) {
                extractedResource.append('/');
            }
            extractedResource.append(relative.getName(index));
        }
        String classpathResource = "driver/" + extractedResource;
        if (!List.of(
                        "driver/package/lib/server/chromium/appIcon.png",
                        "driver/package/lib/tools/dashboard/appIcon.png",
                        "driver/package/lib/tools/skills/playwright-cli/references/storage-state.md")
                .contains(classpathResource)) {
            return false;
        }
        try (java.io.InputStream packaged = ProviderPlaywrightLocalQualificationIT.class
                .getClassLoader()
                .getResourceAsStream(classpathResource)) {
            return packaged != null && Arrays.equals(Files.readAllBytes(path), packaged.readAllBytes());
        }
    }

    @AfterEach
    void removeIsolatedRuntimeSandboxes() throws IOException {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            if (!Files.exists(RUNTIME_SANDBOX_ROOT)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(RUNTIME_SANDBOX_ROOT)) {
                List<Path> pathsToDelete = paths
                        .sorted(Comparator.reverseOrder())
                        .toList();
                for (Path path : pathsToDelete) {
                    Files.deleteIfExists(path);
                }
                return;
            }
            catch (IOException exception) {
                lastFailure = exception;
                try {
                    Thread.sleep(50L);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while deleting the runtime sandbox", interrupted);
                }
            }
        }
        throw lastFailure;
    }

    private record ProcessIdentity(ProcessHandle handle, Instant startedAt) {

        static java.util.Optional<ProcessIdentity> capture(ProcessHandle handle) {
            return handle.info().startInstant().map(started -> new ProcessIdentity(handle, started));
        }

        boolean isSameProcessAlive() {
            return handle.isAlive()
                    && handle.info().startInstant().filter(startedAt::equals).isPresent();
        }
    }

    private static final class FixtureServer implements AutoCloseable {

        private static final String PAGE_ONE_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/1";
        private static final String PAGE_TWO_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/2";
        private static final String PAGE_THREE_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/3";
        private static final String PAGE_FOUR_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/4";
        private static final String PAGE_FIVE_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/5";
        private static final String REDIRECT_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/6";
        private static final String SECONDARY_ROUTE_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/7";
        private static final String TIMEOUT_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/8";
        private static final String OVERSIZED_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/9";
        private static final String HTML_CHALLENGE_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/10";
        private static final String SENSITIVE_CANARY_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/11";
        private static final String SAME_URI_SECONDARY_ROUTE_PATH =
                "/api/v1/sport/football/scheduled-tournaments/2026-08-27/page/12";
        private static final String TOURNAMENT_PATH =
                "/api/v1/unique-tournament/17/scheduled-events/2026-08-27";
        private static final String EVENT_DETAILS_ONE_PATH = "/api/v1/event/16386245";
        private static final String EVENT_DETAILS_TWO_PATH = "/api/v1/event/16421052";
        private static final String EVENT_DETAILS_NOT_FOUND_PATH = "/api/v1/event/17000001";
        private static final String EVENT_DETAILS_STOP_PATH = "/api/v1/event/17000002";
        private static final String INCOMPLETE_BODY_PATH = "/api/v1/event/17000005";
        private static final String DELAYED_EVENT_PATH = "/api/v1/event/17000006";
        private static final String J5_STATISTICS_PATH =
                "/api/v1/event/17000003/statistics";
        private static final String J5_INCIDENTS_PATH =
                "/api/v1/event/17000003/incidents";
        private static final String J5_LINEUPS_PATH =
                "/api/v1/event/17000003/lineups";
        private static final String J5_STOP_STATISTICS_PATH =
                "/api/v1/event/17000004/statistics";
        private static final String J5_STOP_INCIDENTS_PATH =
                "/api/v1/event/17000004/incidents";
        private static final String J5_STOP_LINEUPS_PATH =
                "/api/v1/event/17000004/lineups";
        private static final String REDIRECT_TARGET_PATH = "/redirect-target";
        private static final String SECONDARY_TARGET_PATH = "/secondary-target";

        private final HttpServer server;
        private final ExecutorService executor;
        private final boolean slowPageOne;
        private final boolean slowEventDetails;
        private volatile int incompleteBodyStatus;
        private boolean delayedBody;
        private long delayMillis;
        private final String sensitiveCanaryValue;
        private final CountDownLatch slowRequestReceived = new CountDownLatch(1);
        private final CountDownLatch releaseSlowResponse = new CountDownLatch(1);
        private final List<ObservedRequest> requests = new CopyOnWriteArrayList<>();
        private final List<Long> arrivalNanos = new CopyOnWriteArrayList<>();

        private FixtureServer(
                HttpServer server,
                ExecutorService executor,
                boolean slowPageOne,
                boolean slowEventDetails) {
            this.server = server;
            this.executor = executor;
            this.slowPageOne = slowPageOne;
            this.slowEventDetails = slowEventDetails;
            this.sensitiveCanaryValue = "local-canary-" + UUID.randomUUID();
        }

        static FixtureServer start() throws IOException {
            return start(false, false);
        }

        static FixtureServer startSlow() throws IOException {
            return start(true, false);
        }

        static FixtureServer startSlowEventDetails() throws IOException {
            return start(false, true);
        }

        static FixtureServer startIncompleteBody(int status) throws IOException {
            FixtureServer fixture = start();
            fixture.incompleteBodyStatus = status;
            return fixture;
        }

        static FixtureServer startDelayedEvent(boolean body, long millis) throws IOException {
            FixtureServer fixture = start(); fixture.delayedBody = body; fixture.delayMillis = millis;
            return fixture;
        }

        private static FixtureServer start(
                boolean slowPageOne,
                boolean slowEventDetails) throws IOException {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            FixtureServer fixture = new FixtureServer(
                    server, executor, slowPageOne, slowEventDetails);
            server.createContext("/", fixture::handle);
            server.setExecutor(executor);
            server.start();
            return fixture;
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String sensitiveCanaryValue() {
            return sensitiveCanaryValue;
        }

        private void handle(HttpExchange exchange) throws IOException {
            arrivalNanos.add(System.nanoTime());
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            try {
                requests.add(new ObservedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().toASCIIString(),
                        exchange.getRemoteAddress().getAddress().getHostAddress(),
                        containsSensitiveHeader(exchange.getRequestHeaders()),
                        requestBody.length));
                if (DELAYED_EVENT_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    if (delayedBody) {
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, EVENT_DETAILS_STOP_RESPONSE.length);
                        exchange.getResponseBody().write(EVENT_DETAILS_STOP_RESPONSE, 0, 1);
                        exchange.getResponseBody().flush();
                    }
                    slowRequestReceived.countDown();
                    try { releaseSlowResponse.await(delayMillis, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    if (delayedBody) exchange.getResponseBody().write(EVENT_DETAILS_STOP_RESPONSE, 1, EVENT_DETAILS_STOP_RESPONSE.length - 1);
                    else respond(exchange, 200, "application/json", EVENT_DETAILS_STOP_RESPONSE);
                }
                else if (INCOMPLETE_BODY_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Retry-After", "60");
                    exchange.sendResponseHeaders(incompleteBodyStatus, 128);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    slowRequestReceived.countDown();
                    try { releaseSlowResponse.await(30, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                else if (PAGE_ONE_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    if (slowPageOne) {
                        slowRequestReceived.countDown();
                        try {
                            releaseSlowResponse.await(30, TimeUnit.SECONDS);
                        }
                        catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    respond(exchange, 200, "application/json; charset=utf-8", PAGE_ONE);
                }
                else if (PAGE_TWO_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 404, "application/problem+json", PAGE_TWO_404);
                }
                else if (PAGE_THREE_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 403, "application/problem+json", FORBIDDEN_RESPONSE);
                }
                else if (PAGE_FOUR_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 429, "application/problem+json", RATE_LIMIT_RESPONSE);
                }
                else if (PAGE_FIVE_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 503, "application/problem+json", SERVER_ERROR_RESPONSE);
                }
                else if (REDIRECT_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    exchange.getResponseHeaders().set("Location", REDIRECT_TARGET_PATH);
                    respond(exchange, 302, "text/plain", new byte[0]);
                }
                else if (SECONDARY_ROUTE_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respondWithSecondaryRoute(exchange);
                }
                else if (TIMEOUT_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    slowRequestReceived.countDown();
                    try {
                        releaseSlowResponse.await(30, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    respond(exchange, 200, "application/json", PAGE_ONE);
                }
                else if (OVERSIZED_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respondOversized(exchange);
                }
                else if (HTML_CHALLENGE_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "text/html; charset=utf-8", HTML_CHALLENGE_RESPONSE);
                }
                else if (SENSITIVE_CANARY_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    byte[] canary = ("{\"access_" + "token\":\""
                            + sensitiveCanaryValue + "\"}")
                            .getBytes(StandardCharsets.UTF_8);
                    try {
                        respond(exchange, 200, "application/json", canary);
                    }
                    finally {
                        Arrays.fill(canary, (byte) 0);
                    }
                }
                else if (SAME_URI_SECONDARY_ROUTE_PATH.equals(
                        exchange.getRequestURI().getRawPath())) {
                    respondWithSameUriSecondaryRoute(exchange);
                }
                else if (TOURNAMENT_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", TOURNAMENT_RESPONSE);
                }
                else if (EVENT_DETAILS_ONE_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", EVENT_DETAILS_ONE_RESPONSE);
                }
                else if (EVENT_DETAILS_TWO_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", EVENT_DETAILS_TWO_RESPONSE);
                }
                else if (EVENT_DETAILS_NOT_FOUND_PATH.equals(
                        exchange.getRequestURI().getRawPath())) {
                    respond(
                            exchange,
                            404,
                            "application/problem+json",
                            EVENT_DETAILS_NOT_FOUND_RESPONSE);
                }
                else if (EVENT_DETAILS_STOP_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    if (slowEventDetails) {
                        slowRequestReceived.countDown();
                        try {
                            releaseSlowResponse.await(30, TimeUnit.SECONDS);
                        }
                        catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    respond(exchange, 200, "application/json", EVENT_DETAILS_STOP_RESPONSE);
                }
                else if (J5_STATISTICS_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(
                            exchange,
                            200,
                            "application/json; charset=utf-8",
                            J5_STATISTICS_RESPONSE);
                }
                else if (J5_INCIDENTS_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(
                            exchange,
                            404,
                            "application/problem+json",
                            J5_INCIDENTS_NOT_FOUND_RESPONSE);
                }
                else if (J5_LINEUPS_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", J5_LINEUPS_RESPONSE);
                }
                else if (J5_STOP_STATISTICS_PATH.equals(
                        exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", J5_STOP_STATISTICS_RESPONSE);
                }
                else if (J5_STOP_INCIDENTS_PATH.equals(
                        exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", J5_STOP_INCIDENTS_RESPONSE);
                }
                else if (J5_STOP_LINEUPS_PATH.equals(exchange.getRequestURI().getRawPath())) {
                    respond(exchange, 200, "application/json", J5_STOP_LINEUPS_RESPONSE);
                }
                else {
                    respond(exchange, 500, "text/plain", new byte[0]);
                }
            }
            finally {
                Arrays.fill(requestBody, (byte) 0);
                exchange.close();
            }
        }

        private static boolean containsSensitiveHeader(Headers headers) {
            return headers.keySet().stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.equals("authorization")
                            || name.equals("cookie")
                            || name.equals("proxy-authorization"));
        }

        private static void respond(
                HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        private static void respondWithSecondaryRoute(HttpExchange exchange) throws IOException {
            byte[] prefix = ("<!doctype html><img src=\"" + SECONDARY_TARGET_PATH + "\">")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] suffix = "<p>local fixture</p>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, prefix.length + suffix.length);
            OutputStream response = exchange.getResponseBody();
            response.write(prefix);
            response.flush();
            try {
                Thread.sleep(250);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            response.write(suffix);
        }

        private static void respondWithSameUriSecondaryRoute(HttpExchange exchange)
                throws IOException {
            byte[] prefix = ("<!doctype html><iframe src=\""
                    + SAME_URI_SECONDARY_ROUTE_PATH + "\"></iframe>")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] suffix = "<p>local fixture</p>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, prefix.length + suffix.length);
            OutputStream response = exchange.getResponseBody();
            response.write(prefix);
            response.flush();
            try {
                Thread.sleep(250);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            response.write(suffix);
        }

        private static void respondOversized(HttpExchange exchange) throws IOException {
            int length = (5 * 1024 * 1024) + 1;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, length);
            byte[] chunk = new byte[8192];
            Arrays.fill(chunk, (byte) 'x');
            int remaining = length;
            try {
                while (remaining > 0) {
                    int count = Math.min(remaining, chunk.length);
                    exchange.getResponseBody().write(chunk, 0, count);
                    remaining -= count;
                }
            }
            finally {
                Arrays.fill(chunk, (byte) 0);
            }
        }

        boolean awaitSlowRequest(Duration timeout) throws InterruptedException {
            return slowRequestReceived.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void releaseSlowResponse() {
            releaseSlowResponse.countDown();
        }

        void assertExactTraffic(String... paths) {
            assertThat(requests).containsExactly(Arrays.stream(paths)
                    .map(path -> new ObservedRequest(
                            "GET", path, "127.0.0.1", false, 0))
                    .toArray(ObservedRequest[]::new));
        }

        long requestCount(String path) {
            return requests.stream().filter(request -> path.equals(request.path())).count();
        }

        List<Long> arrivalGapsNanos() {
            List<Long> result = new ArrayList<>();
            for (int index = 1; index < arrivalNanos.size(); index++) {
                result.add(arrivalNanos.get(index) - arrivalNanos.get(index - 1));
            }
            return List.copyOf(result);
        }

        @Override
        public void close() {
            releaseSlowResponse();
            server.stop(0);
            executor.close();
        }
    }

    private record ObservedRequest(
            String method,
            String path,
            String remoteAddress,
            boolean sensitiveHeader,
            int requestBodyBytes) {
    }
}
