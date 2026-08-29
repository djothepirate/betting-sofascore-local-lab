package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPlaywrightLocalQualificationIT {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 27);
    private static final Path RUNTIME_SANDBOX_ROOT = Path.of(
            "target", "provider-playwright-runtime", "qualification-sandboxes")
            .toAbsolutePath().normalize();
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

                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.eventStatistics(J5_EVENT_ID)),
                        200,
                        "application/json; charset=utf-8",
                        J5_STATISTICS_RESPONSE);
                assertThat(worker.get()).isSameAs(exactWorker);

                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.eventIncidents(J5_EVENT_ID)),
                        404,
                        "application/problem+json",
                        J5_INCIDENTS_NOT_FOUND_RESPONSE);
                assertThat(worker.get()).isSameAs(exactWorker);

                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.eventLineups(J5_EVENT_ID)),
                        200,
                        "application/json",
                        J5_LINEUPS_RESPONSE);
                assertThat(worker.get()).isSameAs(exactWorker);

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
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS))) {
                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.scheduledEvents(DATE, 1)),
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
            try (PlaywrightProviderCampaign campaign = supervisor.open(
                    UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS))) {
                assertExactResponse(
                        campaign.execute(PlaywrightProviderRequest.scheduledEvents(DATE, 1)),
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
            assertWorkerExited(supervisor, secondWorker, secondTree, secondOutput, secondError);
            fixture.assertExactTraffic(
                    FixtureServer.PAGE_ONE_PATH,
                    FixtureServer.PAGE_ONE_PATH);
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
    void stopsJ5DuringIncidentsWithinEveryBoundWithoutCallingLineupsOrLeavingResidue()
            throws Exception {
        Path workerJar = requiredRegularFile("provider.playwright.worker-jar");
        Path browserCache = requiredDirectory("provider.playwright.browser-cache");
        AtomicReference<Process> worker = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardOutput = new AtomicReference<>();
        AtomicReference<CompletableFuture<byte[]>> standardError = new AtomicReference<>();

        try (FixtureServer fixture = FixtureServer.startSlowJ5Incidents();
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
            CompletableFuture<Throwable> execution = CompletableFuture.supplyAsync(() -> {
                try {
                    campaign.execute(PlaywrightProviderRequest.eventIncidents(
                            J5_STOP_EVENT_ID));
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
                fixture.assertExactTraffic(
                        FixtureServer.J5_STOP_STATISTICS_PATH,
                        FixtureServer.J5_STOP_INCIDENTS_PATH);
                assertThat(fixture.requestCount(FixtureServer.J5_STOP_LINEUPS_PATH)).isZero();
                assertNoForbiddenRuntimeArtifacts(RUNTIME_SANDBOX_ROOT);
            }
            finally {
                fixture.releaseSlowResponse();
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
        private final boolean slowJ5Incidents;
        private final String sensitiveCanaryValue;
        private final CountDownLatch slowRequestReceived = new CountDownLatch(1);
        private final CountDownLatch releaseSlowResponse = new CountDownLatch(1);
        private final List<ObservedRequest> requests = new CopyOnWriteArrayList<>();

        private FixtureServer(
                HttpServer server,
                ExecutorService executor,
                boolean slowPageOne,
                boolean slowEventDetails,
                boolean slowJ5Incidents) {
            this.server = server;
            this.executor = executor;
            this.slowPageOne = slowPageOne;
            this.slowEventDetails = slowEventDetails;
            this.slowJ5Incidents = slowJ5Incidents;
            this.sensitiveCanaryValue = "local-canary-" + UUID.randomUUID();
        }

        static FixtureServer start() throws IOException {
            return start(false, false, false);
        }

        static FixtureServer startSlow() throws IOException {
            return start(true, false, false);
        }

        static FixtureServer startSlowEventDetails() throws IOException {
            return start(false, true, false);
        }

        static FixtureServer startSlowJ5Incidents() throws IOException {
            return start(false, false, true);
        }

        private static FixtureServer start(
                boolean slowPageOne,
                boolean slowEventDetails,
                boolean slowJ5Incidents) throws IOException {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            FixtureServer fixture = new FixtureServer(
                    server, executor, slowPageOne, slowEventDetails, slowJ5Incidents);
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
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            try {
                requests.add(new ObservedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().toASCIIString(),
                        exchange.getRemoteAddress().getAddress().getHostAddress(),
                        containsSensitiveHeader(exchange.getRequestHeaders()),
                        requestBody.length));
                if (PAGE_ONE_PATH.equals(exchange.getRequestURI().getRawPath())) {
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
                    if (slowJ5Incidents) {
                        slowRequestReceived.countDown();
                        try {
                            releaseSlowResponse.await(30, TimeUnit.SECONDS);
                        }
                        catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
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
