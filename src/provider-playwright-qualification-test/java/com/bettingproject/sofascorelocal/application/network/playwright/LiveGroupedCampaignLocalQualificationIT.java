package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.adapter.persistence.live.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.live.LivePayloadNormalizer;
import com.bettingproject.sofascorelocal.application.live.*;
import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.port.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit, synthetic loopback qualification. No application startup or provider access. */
class LiveGroupedCampaignLocalQualificationIT {
    private static final boolean V8 = Boolean.getBoolean("wo058.grouped.v8");
    private static final boolean V7 = !V8 && Boolean.getBoolean("wo058.grouped.v7");
    private static final boolean V6 = !V8 && (V7 || Boolean.getBoolean("wo058.grouped.v6"));
    private static final boolean V6_OR_LATER = V8 || V6;
    private static final boolean V5 = !V8 && Boolean.getBoolean("wo058.grouped.v5");
    private static final String POLICY = V8 ? "live-v8" : V7 ? "live-v7" : V6 ? "live-v6" : V5 ? "live-v5" : "live-v4";
    private static final int MATCHES = V8 ? 10 : V7 ? 3 : V6 ? 7 : V5 ? 20 : 10;
    private static final int CRITICAL_SECONDS = V8 || V7 ? 60 : V6 || V5 ? 100 : 60;
    private static final int GROUP_GAP_MILLIS = V8 ? 500 : V6_OR_LATER || V5 ? 1_000 : 3_000;
    private static final int BODY_BYTES = 64 * 1024;
    private static final List<SofascoreEndpointType> FAMILIES =
            List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private static final Duration REQUEST_ENVELOPE = Duration.ofMillis(500);
    private static final Duration PROCESSING_ENVELOPE = Duration.ofMillis(200);
    private static final long[] SERVER_DELAYS_MILLIS = {0, 30, 80, 150};

    @Test @Timeout(value = 4, unit = TimeUnit.MINUTES)
    void smokeIncludesFirstTenGroupsAndTransactionalNormalization() throws Exception {
        // The eighty v5 initial 5 MiB responses need a wider cold-start window.
        // This checks coverage/transactions/cleanup only;
        // the separate fixed 300+1800-second run qualifies cadence.
        run(0, V6_OR_LATER || V5 ? 180 : 120, false, Path.of(".tmp/wo058-" + POLICY.substring(5) + "-smoke-qualification.json"));
    }

    @Test @Timeout(value = 45, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "wo058.grouped.sustained", matches = "true")
    void tenMatchesRemainFreshForThirtyMinutesAfterFiveMinuteWarmup() throws Exception {
        // These durations are deliberately fixed: a short run cannot accidentally qualify production.
        run(300, 1800, true, Path.of(".tmp/wo058-" + POLICY.substring(5) + "-sustained-qualification.json"));
    }

    private static void run(int warmupSeconds, int steadySeconds, boolean sustained, Path reportPath) throws Exception {
        var samples = new ArrayList<Sample>();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "EXPERIMENTAL LOCAL_ONLY SYNTHETIC_LOOPBACK");
        report.put("policyVersion", POLICY);
        report.put("criticalIntervalSeconds", CRITICAL_SECONDS);
        report.put("lineupsIntervalSeconds", V8 || V7 ? 60 : 300);
        if (V8) {
            // V8's hard cadence claim concerns an actual request departure in the
            // normal path. Receipt and persistence timestamps remain diagnostic
            // evidence, because a slow response is not a second departure.
            report.put("normalPathDepartureCadenceSeconds", 60);
            report.put("normalPathDepartureTimestamp", "requestedNanos");
            report.put("normalPathDepartureCadenceScope", "per-event-family");
            report.put("receiptAndPublicationCadence", "observed as latency evidence; not a hard V8 provider receipt SLA");
        }
        // V7's already committed builder intentionally reads this legacy field as
        // an integer. V8 adds the millisecond field for its half-second fence.
        report.put("interGroupDelaySeconds", V8 ? 0.5d : GROUP_GAP_MILLIS / 1_000);
        report.put("interGroupDelayMillis", GROUP_GAP_MILLIS);
        report.put("productionPersistentResilience", V6_OR_LATER);
        report.put("productionTransportDiagnosticPersistence", V6_OR_LATER);
        if (V6_OR_LATER) {
            report.put("minimumPostCompletionDelaySeconds", V8 ? 0.5d : 2);
            report.put("minimumPostCompletionDelayMillis", V8 ? 500 : 2_000);
            report.put("maximumDeparturesPer60Seconds", V8 ? 45 : 25);
            report.put("maximumDeparturesPerHour", V8 ? 2_756 : 1_000);
            report.put("measurementContract", "requestSeconds includes eligible dispatch, IPC, transport callbacks and durable completion; processing includes local preflight, deferral publication and authorization work excluding the measured limiter sleep; waiting is reported separately; httpSeconds is the worker exchange latency");
        }
        report.put("realProviderCalls", 0);
        report.put("operatorDatabaseUsed", false);
        report.put("matches", MATCHES);
        report.put("bodyBytesPerResponse", BODY_BYTES);
        report.put("initialFirstResponsePerFamilyBytes", RawPayloadEvidence.MAXIMUM_BYTES);
        report.put("normalizedFixtureShape", Map.of("statisticMetrics", 135, "statisticSignals", 270, "incidents", 30, "lineupPlayers", 44));
        if (V6_OR_LATER) report.put("lineupV3Fixture", Map.of("captains", 2, "playersWithStatistics", 44,
                "statisticsPerPlayer", 20, "ratingVersionsPerPlayer", 2, "missingPlayers", 4));
        report.put("warmupSeconds", warmupSeconds);
        report.put("requiredSteadySeconds", steadySeconds);
        report.put("sustainedQualification", sustained);
        report.put("manifestProof", V8
                ? "local loopback measurement bound to the live-v8 contract; it does not establish provider acceptance"
                : "synthetic-test-only; not an existing capacity qualification");
        if (V6_OR_LATER || V5) {
            var candidateEnvelopes = new LinkedHashMap<String, Object>();
            candidateProfile().endpointEnvelopes().forEach((endpoint, envelope) -> candidateEnvelopes.put(endpoint.name(),
                    Map.of("requestMillis", envelope.requestEnvelope().toMillis(),
                            "processingMillis", envelope.processingEnvelope().toMillis())));
            report.put("candidateEndpointEnvelopes", candidateEnvelopes);
            report.put("candidateEnvelopeSource", V8 ? "V7 steady loopback envelopes reused as the initial V8 local hypothesis; this V8 sustained run is the required independent measurement" : V7 ? "earlier costs used only as a starting hypothesis; V7 requires new measured evidence" : V6
                    ? "historical v5 costs used only as starting hypotheses; no v6 qualification is inferred"
                    : "measured 75-second candidate; unchanged cost floors for the 100-second run");
        } else {
            report.put("candidateRequestEnvelopeMs", REQUEST_ENVELOPE.toMillis());
            report.put("candidateProcessingEnvelopeMs", PROCESSING_ENVELOPE.toMillis());
        }
        report.put("candidateCapacityAtCurrentAdmission", V8 ? LiveAdmissionPolicy.qualifiedCapacityV8(candidateProfile()) : V7 ? LiveAdmissionPolicy.qualifiedCapacityV7(candidateProfile()) : V6 ? LiveAdmissionPolicy.qualifiedCapacityV6(candidateProfile())
                : V5 ? LiveAdmissionPolicy.qualifiedCapacityV5(candidateProfile())
                : LiveAdmissionPolicy.qualifiedCapacityV4(candidateProfile()));
        report.put("admissionMode", MATCHES + " matches under measurement; temporal admission is calculated separately from measured envelope validity");
        report.put("serverDelayPatternMs", SERVER_DELAYS_MILLIS);
        report.put("startedAt", Instant.now().toString());
        report.put("status", "FAILED");
        try (var postgres = new PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("live_grouped_qualification")
                .withUsername("qualification_only").withPassword("synthetic-local-test-only")) {
            postgres.start();
            try (Database database = new Database(postgres);
                 var fixture = new LiveProviderSessionQualificationIT.Fixture(true, MATCHES)) {
                if (V8) {
                    String loopbackOrigin = "http://127.0.0.1:" + fixture.server.getAddress().getPort();
                    report.put("loopbackOrigin", loopbackOrigin);
                    report.put("loopbackTransportOnly", true);
                }
                var storageProperties = new LiveCampaignProperties();
                storageProperties.setDockerExecutable(dockerExecutable());
                storageProperties.setPostgresContainer(postgres.getContainerName().replaceFirst("^/", ""));
                var storage = new DockerLiveStorageCapacityProbe(storageProperties);
                report.put("productionDockerDfProbePerRequest", true);
                Map<String, String> templates = templates();
                for (long eventId = 17_000_001; eventId <= 17_000_000 + MATCHES; eventId++) {
                    for (var endpoint : FAMILIES)
                        fixture.bodies.put(path(eventId, endpoint), body(templates, eventId, endpoint, 0));
                }
                List<Target> targets = new ArrayList<>();
                for (long eventId = 17_000_001; eventId <= 17_000_000 + MATCHES; eventId++)
                    targets.add(database.seed(eventId, fixture.bodies.get(path(eventId, EVENT_DETAILS))));
                var supervisor = V6_OR_LATER ? fixture.supervisor(Duration.ofSeconds(30)) : fixture.supervisor();
                AtomicLong limiterSleep = new AtomicLong();
                PlaywrightProviderCampaignFactory factory = V6_OR_LATER
                        ? new ResilientPlaywrightProviderCampaignFactory(supervisor, database.resilience, java.time.Clock.systemUTC(), duration -> {
                            long started = System.nanoTime();
                            try { Thread.sleep(duration); } finally { limiterSleep.addAndGet(System.nanoTime() - started); }
                        }) : supervisor;
                Manifest manifest = manifest(targets);
                database.store.prepare(manifest);
                var owner = new Owner(UUID.randomUUID(), ProcessHandle.current().pid(),
                        ProcessHandle.current().info().startInstant().orElseThrow());
                Ownership ownership = database.guard.tryAcquire(manifest.campaignId(), owner, Instant.now())
                        .orElseThrow().ownership();
                List<ProcessHandle> children;
                long elapsedNanos;
                try (var campaign = V8 ? factory.openLiveGroupedV8(manifest.campaignId(), LiveProviderSession.ENDPOINTS)
                        : V7 ? factory.openLiveGroupedV7(manifest.campaignId(), LiveProviderSession.ENDPOINTS)
                        : V6 ? factory.openLiveGroupedV6(manifest.campaignId(), LiveProviderSession.ENDPOINTS)
                        : V5 ? factory.openLiveGroupedV5(manifest.campaignId(), LiveProviderSession.ENDPOINTS)
                        : factory.openLiveGrouped(manifest.campaignId(), LiveProviderSession.ENDPOINTS)) {
                    // Browser/bootstrap and seed transactions are outside the cadence measurement.
                    Instant origin = Instant.now();
                    long originNano = System.nanoTime();
                    Launch launch = database.store.launch(manifest.campaignId(), manifest.manifestSha256(), ownership, origin);
                    var schedule = new LiveSchedule(targets.stream().map(Target::canonicalEventId).toList(),
                            origin, launch.endsAt(), Duration.ofSeconds(CRITICAL_SECONDS), POLICY, manifest.campaignId(),
                            V8 ? manifest.admissionProfile().groupedProfile() : null);
                    var publishedFamilies = new HashMap<String, FamilySchedule>();
                    var publishedStates = new HashMap<UUID, LiveSchedule.EventState>();
                    var familyVersions = new HashMap<String, Long>();
                    var uiReadNanos = new ArrayList<Long>();
                    long nextUiRead = originNano;
                    long deadline = originNano + TimeUnit.SECONDS.toNanos(warmupSeconds + steadySeconds);
                    long nextProgress = originNano + TimeUnit.SECONDS.toNanos(60);
                    long deferredWorkNanos = 0, pressureStartedNano = 0;
                    long previousResilienceSql = database.resilience.sqlNanos;
                    int pressureDeferrals = 0;
                    while (System.nanoTime() < deadline) {
                        if (System.nanoTime() >= nextUiRead) {
                            long uiStart = System.nanoTime();
                            database.store.find(manifest.campaignId()).orElseThrow();
                            uiReadNanos.add(System.nanoTime() - uiStart);
                            nextUiRead += TimeUnit.SECONDS.toNanos(5);
                        }
                        Instant now = at(origin, originNano);
                        var next = schedule.next(now);
                        assertThat(schedule.terminal()).as("scheduler must remain active under qualified load").isFalse();
                        if (next.isEmpty()) { Thread.sleep(20); continue; }
                        LiveSchedule.Due due = next.orElseThrow();
                        long operationStart = System.nanoTime();
                        if (V6_OR_LATER) {
                            var decision = V8
                                    ? database.resilience.departureDecision(
                                            ProviderResilienceData.DepartureProfile.LIVE_V8, Instant.now())
                                    : database.resilience.departureDecision(Instant.now());
                            if (!decision.allowed()) {
                                assertThat(decision.reason()).isEqualTo(ProviderResilienceData.DepartureReason.RATE_LIMITED);
                                if (pressureStartedNano == 0) pressureStartedNano = operationStart;
                                schedule.defer(due, decision.nextAllowedAt());
                                publishSchedule(database, ownership, schedule, publishedFamilies, publishedStates);
                                deferredWorkNanos += System.nanoTime() - operationStart;
                                pressureDeferrals++;
                                continue;
                            }
                        }
                        long pressureElapsedNanos = pressureStartedNano == 0 ? 0 : operationStart - pressureStartedNano;
                        DispatchBudget budget = database.store.dispatchBudget(ownership, due.eventId());
                        assertThat(budget.reservedCalls()).isLessThan(manifest.maximumCalls() - 4);
                        assertThat(budget.eventReservedCalls()).isLessThan(manifest.maximumCallsPerEvent() - 4);
                        assertThat(storage.availableBytes()).isGreaterThanOrEqualTo(
                                2 * (manifest.maximumBytes() - budget.receivedBytes()) + storageProperties.getDiskReserveBytes());
                        var reservation = database.store.reserveAttempt(new AttemptRequest(ownership, UUID.randomUUID(),
                                due.eventId(), due.cycle(), due.endpoint(), due.kind(), due.dueAt(), Instant.now(), due.finalCycle(),
                                due.groupId(), due.groupSequence(), due.groupOrdinal())).orElseThrow();
                        long eventId = reservation.providerEventId();
                        // A -> A -> B -> B gives both semantic changes and identical-payload new receipts.
                        String familyKey = path(eventId, due.endpoint());
                        long familyVersion = familyVersions.merge(familyKey, 1L, Long::sum) - 1;
                        int responseBytes = familyVersion == 0 ? RawPayloadEvidence.MAXIMUM_BYTES : BODY_BYTES;
                        fixture.bodies.put(familyKey, body(templates, eventId, due.endpoint(), familyVersion / 2, responseBytes));
                        long serverDelay = SERVER_DELAYS_MILLIS[(int) ((familyVersion + eventId + FAMILIES.indexOf(due.endpoint())) % SERVER_DELAYS_MILLIS.length)];
                        fixture.responseDelayMillis.set(serverDelay);
                        AtomicLong dispatchStart = new AtomicLong();
                        AtomicLong sleepBeforeDispatch = new AtomicLong();
                        var admission = new PlaywrightDispatchAdmission() {
                            public void onTransportProgress(PlaywrightTransportDiagnostic observed) {
                                recordObservedV8Departure(schedule, due, observed.requestedAt());
                                if (V6_OR_LATER) database.diagnostics.recordTransport(manifest.campaignId(), reservation.attemptId(), due.endpoint(), observed);
                            }
                            public void check() {
                                if (!schedule.mayDispatch(due, at(origin, originNano))) throw new PlaywrightDispatchCancelledException();
                            }
                            public Permit acquireDispatchPermit() {
                                dispatchStart.set(System.nanoTime());
                                sleepBeforeDispatch.set(limiterSleep.get());
                                check();
                                if (!database.guard.isOwned(ownership)) throw new PlaywrightDispatchCancelledException();
                                database.store.recordDispatch(ownership, reservation.attemptId(), Instant.now());
                                schedule.started(due, at(origin, originNano));
                                return () -> { };
                            }
                        };
                        long beforeTransport = System.nanoTime();
                        long beforeAuthorizationSleep = limiterSleep.get();
                        var phase = due.endpoint() == EVENT_DETAILS ? LiveProviderDispatchGroup.Phase.CHECK
                                : LiveProviderDispatchGroup.Phase.IN_PLAY;
                        var response = campaign.executeGrouped(new PlaywrightProviderRequest(due.endpoint(), null, 0, 0, eventId),
                                new LiveProviderDispatchGroup(manifest.campaignId(), due.groupId(), eventId, phase), admission);
                        // The local loopback transport normally sends REQUEST_SENT progress.  Keep
                        // the validated response timestamp as an idempotent fallback so this
                        // qualification exercises the same V8 rephase contract as production.
                        recordObservedV8Departure(schedule, due, response.requestedAt());
                        long responseNano = System.nanoTime();
                        if (V6_OR_LATER) {
                            assertThat(response.diagnostic()).isNotNull();
                            int timeout = response.diagnostic().requestTimeoutMillis();
                            Object previousTimeout = report.putIfAbsent("effectiveRequestTimeoutMillis", timeout);
                            if (previousTimeout != null) assertThat(timeout).isEqualTo((Integer) previousTimeout);
                            admission.onTransportProgress(response.diagnostic());
                        }
                        assertThat(response.httpStatus()).isEqualTo(200);
                        assertThat(response.payload().sizeBytes()).isEqualTo(responseBytes);
                        var raw = raw(due.endpoint(), eventId, response);
                        var receipt = database.store.saveReceipt(ownership, reservation.attemptId(), raw);
                        var processed = database.processor.process(CanonicalEventIdentity.sofascore(eventId), due.endpoint(), response, receipt);
                        assertThat(processed.outcome()).as("%s %s", due.endpoint(), processed.code()).isEqualTo(LiveProcessedResponse.Outcome.PARSED);
                        assertThat(processed.scope()).isEqualTo(LiveProcessedResponse.FailureScope.NONE);
                        Map<String, Boolean> signals = new LinkedHashMap<>();
                        processed.signals().forEach(s -> signals.put(s.key(), s.kind().name().equals("FINISH_CHECK")));
                        schedule.completed(due, processed.sportStatus().orElse(null), false, signals, at(origin, originNano));
                        if (V8 && due.endpoint() == EVENT_DETAILS) {
                            Instant expectedFirstJ5 = response.requestedAt()
                                    .plus(manifest.admissionProfile().groupedProfile().envelope(EVENT_DETAILS).exchangeEnvelope())
                                    .plusMillis(GROUP_GAP_MILLIS);
                            Instant scheduledFirstJ5 = schedule.states().stream()
                                    .filter(event -> event.eventId().equals(due.eventId())).findFirst().orElseThrow().nextDueAt();
                            assertThat(scheduledFirstJ5).as("V8 J5 phase must follow authenticated worker departure")
                                    .isEqualTo(expectedFirstJ5);
                        }
                        String state = schedule.states().stream().filter(e -> e.eventId().equals(due.eventId())).findFirst().orElseThrow().state();
                        var completeness = processed.completeness();
                        var publication = new Publication(processed.outcome().name(), processed.scope().name(), processed.code(),
                                Instant.now(), processed.parserVersion(), true, state, processed.sportStatus().orElse(null),
                                processed.projectionJson(), processed.projectionVersion(), completeness.map(c -> c.status().name()).orElse(null),
                                completeness.map(c -> c.scorePercent()).orElse(null));
                        database.store.publishResult(ownership, reservation.attemptId(), publication,
                                () -> database.processor.persistProcessed(processed));
                        publishSchedule(database, ownership, schedule, publishedFamilies, publishedStates);
                        long committedNano = System.nanoTime();
                        long requestNanos = responseNano - dispatchStart.get();
                        long authorizationDelayNanos = dispatchStart.get() - beforeTransport;
                        long limiterSleepNanos = sleepBeforeDispatch.get() - beforeAuthorizationSleep;
                        long processingNanos = beforeTransport - operationStart + committedNano - responseNano
                                + deferredWorkNanos + (V6_OR_LATER ? authorizationDelayNanos - limiterSleepNanos : 0);
                        samples.add(new Sample(eventId, due.endpoint(), due.cycle(), due.groupId().toString(),
                                Duration.between(origin, due.dueAt()).toNanos(),
                                Duration.between(origin, response.receivedAt()).toNanos(), committedNano - originNano,
                                requestNanos, processingNanos, fixture.arrivals.getLast() - originNano,
                                responseNano - originNano, serverDelay, responseBytes,
                                Duration.between(origin, response.requestedAt()).toNanos(), response.latency().toNanos(),
                                authorizationDelayNanos, pressureElapsedNanos, limiterSleepNanos,
                                database.resilience.sqlNanos - previousResilienceSql,
                                V6_OR_LATER ? Duration.between(origin, database.resilience.lastFinished).toNanos() : 0));
                        deferredWorkNanos = 0; pressureStartedNano = 0;
                        previousResilienceSql = database.resilience.sqlNanos;
                        assertThat(fixture.worker.get().isAlive()).isTrue();
                        if (committedNano >= nextProgress) {
                            System.out.println("WO058_GROUPED_PROGRESS_SECONDS=" + TimeUnit.NANOSECONDS.toSeconds(committedNano - originNano)
                                    + ";REQUESTS=" + samples.size() + ";MISSED=" + schedule.states().stream().mapToLong(LiveSchedule.EventState::missedCycles).sum());
                            nextProgress += TimeUnit.SECONDS.toNanos(60);
                        }
                    }
                    elapsedNanos = System.nanoTime() - originNano;
                    report.put("elapsedSeconds", seconds(elapsedNanos));
                    report.put("steadyElapsedSeconds", seconds(elapsedNanos) - warmupSeconds);
                    report.put("uiReadsEveryFiveSeconds", summary(uiReadNanos));
                    assertThat(elapsedNanos).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(warmupSeconds + steadySeconds));
                    report.put("missedCycles", schedule.states().stream().mapToLong(LiveSchedule.EventState::missedCycles).sum());
                    assertThat(schedule.states()).allSatisfy(s -> assertThat(s.missedCycles()).isZero());
                    CampaignView finalView = database.store.find(manifest.campaignId()).orElseThrow();
                    assertThat(finalView.attempts()).hasSize(samples.size());
                    assertThat(finalView.attempts()).allSatisfy(a -> {
                        assertThat(a.result()).isNotNull();
                        assertThat(a.snapshotId()).isNotNull(); assertThat(a.occurrenceId()).isNotNull();
                        assertThat(a.result().normalized().normalizedSha256()).isNotBlank();
                    });
                    report.put("durableAttempts", finalView.attempts().size());
                    report.put("receivedBytes", finalView.receivedBytes());
                    report.put("immutableObservations", database.jdbc.queryForObject("select count(*) from j5_event_data_observation", Long.class));
                    report.put("familyScheduleRevisions", database.jdbc.queryForObject("select count(*) from live_family_schedule_revision", Long.class));
                    if (V6_OR_LATER) {
                        report.put("pressureDeferrals", pressureDeferrals);
                        long departures = database.jdbc.queryForObject("select count(*) from provider_departure_reservation", Long.class);
                        long completions = database.jdbc.queryForObject("select count(*) from provider_departure_completion", Long.class);
                        long diagnostics = database.jdbc.queryForObject("select count(*) from live_attempt_transport_diagnostic where response_complete", Long.class);
                        report.put("durableDepartures", departures); report.put("durableDepartureCompletions", completions);
                        report.put("durableCompleteTransportDiagnostics", diagnostics);
                        assertThat(departures).isEqualTo(samples.size());
                        assertThat(completions).isEqualTo(departures);
                        assertThat(diagnostics).isEqualTo(departures);
                        assertThat(database.resilience.snapshot().unresolvedDispatchId()).isNull();
                    }
                    schedule.stopAll("STOPPED_OPERATOR");
                    database.store.transition(ownership, null, "STOPPED_OPERATOR", "STOPPED_OPERATOR", Instant.now(), null);
                    children = fixture.worker.get().descendants().toList();
                }
                assertThat(supervisor.activeCampaignId()).isEmpty();
                assertThat(fixture.worker.get().toHandle().isAlive()).isFalse();
                assertThat(children).allSatisfy(child -> assertThat(child.isAlive()).isFalse());
                // Native process exit is already verified. Java's Process notification may arrive later on Windows.
                assertThat(fixture.worker.get().waitFor(5, TimeUnit.SECONDS)).isTrue();
                assertThat(fixture.worker.get().isAlive()).isFalse();
                database.guard.releaseAfterVerifiedCleanup(ownership, Instant.now());
                assertThat(fixture.offScope.get()).isZero();
                fixture.assertNoArtifacts();
                report.put("workerAndChildrenClosed", true);
                report.put("offScopeRequests", fixture.offScope.get());
                evaluate(samples, warmupSeconds, sustained, report);
                report.put("status", "PASSED");
            }
        } catch (Exception | AssertionError failure) {
            report.put("failureType", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            report.put("finishedAt", Instant.now().toString());
            report.put("requests", samples.size());
            report.put("samples", samples);
            Files.createDirectories(reportPath.toAbsolutePath().getParent());
            Files.writeString(reportPath, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        }
    }

    private static void evaluate(List<Sample> samples, int warmupSeconds, boolean sustained, Map<String, Object> report) {
        long warmupNanos = TimeUnit.SECONDS.toNanos(warmupSeconds);
        var metrics = new LinkedHashMap<String, Object>();
        var checks = new ArrayList<Runnable>();
        report.put("steadyMetrics", metrics);
        var initialMetrics = new LinkedHashMap<String, Object>();
        report.put("initialMetrics", initialMetrics);
        for (var endpoint : FAMILIES) {
            var initial = samples.stream().filter(s -> s.endpoint() == endpoint && s.receivedNanos() < warmupNanos).toList();
            initialMetrics.put(endpoint.name(), Map.of("requestSeconds", summary(initial.stream().map(Sample::requestNanos).toList()),
                    "processingIncludingSqlSeconds", summary(initial.stream().map(Sample::processingNanos).toList()),
                    "nominalLatenessSeconds", summary(initial.stream().map(s -> Math.max(0, s.committedNanos() - s.dueNanos())).toList())));
            var steady = samples.stream().filter(s -> s.endpoint() == endpoint && s.receivedNanos() >= warmupNanos).toList();
            var intervals = new ArrayList<Long>();
            var availableIntervals = new ArrayList<Long>();
            var departureIntervals = new ArrayList<Long>();
            var perEventCounts = new LinkedHashMap<Long, Integer>();
            var perEventMetrics = new LinkedHashMap<Long, Object>();
            for (long id = 17_000_001; id <= 17_000_000 + MATCHES; id++) {
                long eventId = id;
                var eventSamples = steady.stream().filter(s -> s.providerEventId() == eventId).toList();
                perEventCounts.put(id, eventSamples.size());
                checks.add(() -> assertThat(eventSamples).hasSizeGreaterThanOrEqualTo(sustained ? V8 || V7 ? 28 : endpoint == EVENT_LINEUPS ? 5 : (V6 || V5 ? 17 : 28) : 1));
                var eventIntervals = new ArrayList<Long>();
                var eventAvailableIntervals = new ArrayList<Long>();
                var eventDepartureIntervals = new ArrayList<Long>();
                var nominalIntervals = new ArrayList<Long>();
                for (int i = 1; i < eventSamples.size(); i++) {
                    eventIntervals.add(eventSamples.get(i).receivedNanos() - eventSamples.get(i - 1).receivedNanos());
                    eventAvailableIntervals.add(eventSamples.get(i).committedNanos() - eventSamples.get(i - 1).committedNanos());
                    eventDepartureIntervals.add(eventSamples.get(i).requestedNanos() - eventSamples.get(i - 1).requestedNanos());
                    nominalIntervals.add(eventSamples.get(i).dueNanos() - eventSamples.get(i - 1).dueNanos());
                }
                intervals.addAll(eventIntervals);
                availableIntervals.addAll(eventAvailableIntervals);
                departureIntervals.addAll(eventDepartureIntervals);
                var eventLateness = eventSamples.stream().map(s -> Math.max(0, s.receivedNanos() - s.dueNanos())).toList();
                perEventMetrics.put(id, Map.of("receiptIntervalSeconds", summary(eventIntervals),
                        "availableIntervalSeconds", summary(eventAvailableIntervals),
                        "departureIntervalSeconds", summary(eventDepartureIntervals),
                        "nominalIntervalSeconds", summary(nominalIntervals),
                        "nominalLatenessSeconds", summary(eventLateness)));
                if (sustained) checks.add(() -> {
                    if (V8) {
                        assertThat(eventDepartureIntervals).as("V8 normal-path departure intervals for %s/%s", eventId, endpoint)
                                .isNotEmpty().allSatisfy(interval -> assertThat(interval)
                                        .isGreaterThan(0)
                                        .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(60)));
                    } else if (endpoint == EVENT_LINEUPS && !V7) {
                        if (V6_OR_LATER) {
                            // Shared holds intentionally coalesce/rephase v6; old absolute phase
                            // congruences are not its contract. Measure the resulting cadence instead.
                            assertThat(nominalIntervals).isNotEmpty().allSatisfy(interval ->
                                    assertThat(interval).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(300)));
                            assertThat(percentile(eventIntervals, .95)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(315));
                            assertThat(Collections.max(eventIntervals)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(330));
                            assertThat(percentile(eventAvailableIntervals, .95)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(315));
                            assertThat(Collections.max(eventAvailableIntervals)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(330));
                        } else {
                            assertThat(nominalIntervals).as("lineup nominal intervals for event %s", eventId)
                                    .isNotEmpty().allSatisfy(interval -> assertThat(interval).isEqualTo(TimeUnit.SECONDS.toNanos(300)));
                            long eventIndex = eventId - 17_000_001;
                            long criticalNanos = TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS);
                            long eventPhase = criticalNanos * eventIndex / MATCHES;
                            long phaseCount = 300 / CRITICAL_SECONDS;
                            assertThat(eventSamples).as("lineup phase distribution for event %s", eventId)
                                    .allSatisfy(sample -> assertThat((sample.dueNanos() - eventPhase) / criticalNanos % phaseCount)
                                            .isEqualTo(eventIndex % phaseCount));
                        }
                        assertThat(percentile(eventLateness, .95)).as("lineup lateness for event %s", eventId)
                                .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(15));
                    } else {
                        assertThat(percentile(eventIntervals, .95)).as("receipt interval P95 for %s/%s", eventId, endpoint)
                                .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 5));
                        assertThat(Collections.max(eventIntervals)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 15));
                        assertThat(percentile(eventAvailableIntervals, .95)).as("publication interval P95 for %s/%s", eventId, endpoint)
                                .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 5));
                        assertThat(Collections.max(eventAvailableIntervals)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 15));
                    }
                });
            }
            List<Long> lateness = steady.stream().map(s -> Math.max(0, s.receivedNanos() - s.dueNanos())).toList();
            Map<String, Object> endpointMetrics = new LinkedHashMap<>();
            endpointMetrics.put("countsPerEvent", perEventCounts);
            endpointMetrics.put("metricsPerEvent", perEventMetrics);
            endpointMetrics.put("receiptIntervalSeconds", summary(intervals));
            endpointMetrics.put("availableIntervalSeconds", summary(availableIntervals));
            endpointMetrics.put("departureIntervalSeconds", summary(departureIntervals));
            endpointMetrics.put("nominalLatenessSeconds", summary(lateness));
            endpointMetrics.put("requestSeconds", summary(steady.stream().map(Sample::requestNanos).toList()));
            endpointMetrics.put("processingIncludingSqlSeconds", summary(steady.stream().map(Sample::processingNanos).toList()));
            if (V6_OR_LATER) {
                endpointMetrics.put("httpSeconds", summary(steady.stream().map(Sample::httpNanos).toList()));
                endpointMetrics.put("authorizationDelaySeconds", summary(steady.stream().map(Sample::authorizationDelayNanos).toList()));
                endpointMetrics.put("schedulerDeferralElapsedSeconds", summary(steady.stream().map(Sample::pressureElapsedNanos).toList()));
                endpointMetrics.put("limiterSleepSeconds", summary(steady.stream().map(Sample::limiterSleepNanos).toList()));
                endpointMetrics.put("resilienceSqlSeconds", summary(steady.stream().map(Sample::resilienceSqlNanos).toList()));
            }
            var candidateEnvelope = candidateProfile().envelope(endpoint);
            endpointMetrics.put("candidateEnvelopesPassed", steady.stream().allMatch(s -> s.requestNanos() <= candidateEnvelope.requestEnvelope().toNanos()
                    && s.processingNanos() <= candidateEnvelope.processingEnvelope().toNanos()));
            var peak = samples.stream().filter(s -> s.endpoint() == endpoint && s.bodyBytes() == RawPayloadEvidence.MAXIMUM_BYTES).toList();
            endpointMetrics.put("initialMaximumBodyRequestSeconds", summary(peak.stream().map(Sample::requestNanos).toList()));
            endpointMetrics.put("initialMaximumBodyProcessingSeconds", summary(peak.stream().map(Sample::processingNanos).toList()));
            metrics.put(endpoint.name(), endpointMetrics);
            if (sustained && !V8) {
                if (endpoint == EVENT_LINEUPS && !(V8 || V7)) checks.add(() -> assertThat(percentile(lateness, .95)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(15)));
                else checks.add(() -> {
                    assertThat(percentile(intervals, .95)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 5));
                    assertThat(Collections.max(intervals)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 15));
                    assertThat(percentile(availableIntervals, .95)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 5));
                    assertThat(Collections.max(availableIntervals)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(CRITICAL_SECONDS + 15));
                });
            }
        }
        var steadyCritical = samples.stream().filter(s -> (V8 || V7 || s.endpoint() != EVENT_LINEUPS) && s.receivedNanos() >= warmupNanos).toList();
        var queueDebt = steadyCritical.stream().map(s -> Math.max(0, s.committedNanos() - s.dueNanos())).toList();
        int quarter = Math.max(1, steadyCritical.size() / 4);
        long firstDebt = percentile(queueDebt.subList(0, quarter), .95);
        long lastDebt = percentile(queueDebt.subList(queueDebt.size() - quarter, queueDebt.size()), .95);
        report.put("criticalPublicationQueueDebtSeconds", summary(queueDebt));
        report.put("queueDebtFirstQuarterP95Seconds", seconds(firstDebt));
        report.put("queueDebtLastQuarterP95Seconds", seconds(lastDebt));
        if (sustained) checks.add(() -> {
            assertThat(lastDebt - firstDebt).as("steady queue debt must not grow over the run")
                    .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(5));
            assertThat(percentile(queueDebt, .95)).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(15));
        });
        report.put("initialPhaseRequests", samples.stream().filter(s -> s.receivedNanos() < warmupNanos).count());
        report.put("initialPhaseMaximumReceiptLatenessSeconds", seconds(samples.stream().filter(s -> s.receivedNanos() < warmupNanos)
                .mapToLong(s -> Math.max(0, s.receivedNanos() - s.dueNanos())).max().orElse(0)));
        long minBetweenGroups = Long.MAX_VALUE;
        for (int i = 1; i < samples.size(); i++) {
            Sample previous = samples.get(i - 1), current = samples.get(i);
            if (!current.groupId().equals(previous.groupId())) {
                long gap = current.serverArrivalNanos() - previous.responseCompleteNanos();
                // Server arrival is after the parent's gate completion; allow only clock quantization (1 ms).
                checks.add(() -> assertThat(gap).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(GROUP_GAP_MILLIS) - TimeUnit.MILLISECONDS.toNanos(1)));
                minBetweenGroups = Math.min(minBetweenGroups, gap);
            }
        }
        report.put("minimumObservedBetweenGroupGapSeconds", seconds(minBetweenGroups));
        if (V6_OR_LATER) {
            long minimumPostCompletion = Long.MAX_VALUE;
            for (int i = 1; i < samples.size(); i++) {
                Sample previous = samples.get(i - 1), current = samples.get(i);
                // Both timestamps come from the same host clock. The worker timestamps
                // requests at millisecond precision; SQL rounds completion up to a microsecond.
                long gap = current.requestedNanos() - previous.resilienceFinishedNanos();
                minimumPostCompletion = Math.min(minimumPostCompletion, gap);
                checks.add(() -> assertThat(gap).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(V8 ? 500 : 2_000) - TimeUnit.MILLISECONDS.toNanos(1)));
            }
            int minutePeak = rollingPeak(samples, TimeUnit.SECONDS.toNanos(60), Sample::requestedNanos);
            int hourPeak = rollingPeak(samples, TimeUnit.HOURS.toNanos(1), Sample::requestedNanos);
            int wireMinutePeak = rollingPeak(samples, TimeUnit.SECONDS.toNanos(60), Sample::serverArrivalNanos);
            int wireHourPeak = rollingPeak(samples, TimeUnit.HOURS.toNanos(1), Sample::serverArrivalNanos);
            report.put("observedMinimumPostCompletionDelaySeconds", seconds(minimumPostCompletion));
            report.put("observedMaximumDeparturesPer60Seconds", minutePeak);
            report.put("observedMaximumDeparturesPerHour", hourPeak);
            report.put("observedMaximumWireArrivalsPer60Seconds", wireMinutePeak);
            report.put("observedMaximumWireArrivalsPerHour", wireHourPeak);
            report.put("rollingWindowDefinition", "requestedAt in (t-window,t], all samples including initialization; sub-hour run does not prove a saturated full hour");
            checks.add(() -> assertThat(minutePeak).isLessThanOrEqualTo(V8 ? 45 : 25));
            checks.add(() -> assertThat(hourPeak).isLessThanOrEqualTo(V8 ? 2_756 : 1_000));
            checks.add(() -> assertThat(wireMinutePeak).isLessThanOrEqualTo(V8 ? 45 : 25));
            checks.add(() -> assertThat(wireHourPeak).isLessThanOrEqualTo(V8 ? 2_756 : 1_000));
        }
        checks.forEach(Runnable::run);
    }

    private static int rollingPeak(List<Sample> samples, long windowNanos, java.util.function.ToLongFunction<Sample> time) {
        int start = 0, maximum = 0;
        for (int end = 0; end < samples.size(); end++) {
            long lower = time.applyAsLong(samples.get(end)) - windowNanos;
            while (start <= end && time.applyAsLong(samples.get(start)) <= lower) start++;
            maximum = Math.max(maximum, end - start + 1);
        }
        return maximum;
    }

    private static void publishSchedule(Database database, Ownership ownership, LiveSchedule schedule,
                                        Map<String, FamilySchedule> families, Map<UUID, LiveSchedule.EventState> states) {
        for (var state : schedule.states()) {
            for (var family : schedule.familySchedules(state.eventId())) {
                String key = state.eventId() + ":" + family.endpoint();
                if (!family.equals(families.get(key))) {
                    database.store.updateFamilySchedule(ownership, state.eventId(), family, Instant.now());
                    families.put(key, family);
                }
            }
            var previous = states.get(state.eventId());
            if (previous == null || !previous.state().equals(state.state()))
                database.store.transition(ownership, state.eventId(), state.state(), state.state(), Instant.now(), null);
            if (!state.equals(previous)) {
                database.store.updateScheduleMetrics(ownership, state.eventId(), state.nextDueAt(), state.missedCycles(), state.finalComplete(), Instant.now());
                states.put(state.eventId(), state);
            }
        }
    }

    private static Manifest manifest(List<Target> targets) {
        var grouped = candidateProfile();
        var profile = new AdmissionProfile(Duration.ofSeconds(1), Duration.ofSeconds(1), "", grouped);
        Instant now = Instant.now();
        return new Manifest(UUID.randomUUID(), "d".repeat(64), POLICY, now, now.plusSeconds(300), Duration.ofHours(4),
                V6_OR_LATER || V5 ? 2500 : 1000, V6_OR_LATER || V5 ? 20000 : 3000, 3000L * RawPayloadEvidence.MAXIMUM_BYTES,
                V8 ? 10 : V7 ? 3 : V6 ? 7 : 20, targets, profile,
                Duration.ofSeconds(CRITICAL_SECONDS));
    }

    private static GroupedAdmissionProfile candidateProfile() {
        EnumMap<SofascoreEndpointType, EndpointEnvelope> envelopes = new EnumMap<>(SofascoreEndpointType.class);
        if (V8) {
            // V8 starts from the independently measured V7 steady envelopes. The
            // V8 sustained loopback run below must still keep every sample within them.
            envelopes.put(EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)));
            envelopes.put(EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(550)));
            envelopes.put(EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(500)));
            envelopes.put(EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(500)));
        } else if (V6 || V5) {
            // Keep the established maxima from the 75-second candidate. A longer
            // cadence must not manufacture capacity by lowering measured costs.
            envelopes.put(EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(600)));
            envelopes.put(EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(500)));
            envelopes.put(EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(500)));
            envelopes.put(EVENT_LINEUPS, V6
                    ? new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(450))
                    : new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)));
        } else FAMILIES.forEach(endpoint -> envelopes.put(endpoint, new EndpointEnvelope(REQUEST_ENVELOPE, PROCESSING_ENVELOPE)));
        return new GroupedAdmissionProfile(envelopes, "b".repeat(64), POLICY);
    }

    private static Map<String, String> templates() throws Exception {
        Map<String, String> result = new HashMap<>();
        for (String family : List.of("details", "statistics", "incidents", "lineups"))
            result.put(family, Files.readString(Path.of("fixtures/event-" + family + "/nominal.json")));
        var mapper = JsonMapper.builder().build();
        var statistics = (ObjectNode) mapper.readTree(result.get("statistics"));
        var basePeriod = (ObjectNode) statistics.get("statistics").get(0);
        var baseItems = (ArrayNode) basePeriod.get("groups").get(0).get("statisticsItems");
        var periods = statistics.putArray("statistics");
        for (String periodName : List.of("ALL", "1ST", "2ND")) {
            var period = basePeriod.deepCopy();
            period.put("period", periodName);
            var items = ((ObjectNode) period.get("groups").get(0)).putArray("statisticsItems");
            for (int i = 0; i < 45; i++) {
                var item = ((ObjectNode) baseItems.get(i % 3)).deepCopy();
                if (i >= 3) { item.put("key", "syntheticMetric" + i); item.put("name", "Synthetic metric " + i); }
                items.add(item);
            }
            periods.add(period);
        }
        result.put("statistics", mapper.writeValueAsString(statistics));
        var incidents = (ObjectNode) mapper.readTree(result.get("incidents"));
        var originalIncidents = (ArrayNode) incidents.get("incidents");
        var expandedIncidents = incidents.putArray("incidents");
        for (int i = 0; i < 30; i++) {
            var incident = ((ObjectNode) originalIncidents.get(i % 3)).deepCopy();
            incident.put("time", i + 1);
            expandedIncidents.add(incident);
        }
        result.put("incidents", mapper.writeValueAsString(incidents));
        var lineups = (ObjectNode) mapper.readTree(result.get("lineups"));
        int sideIndex = 0;
        for (String sideName : List.of("home", "away")) {
            var side = (ObjectNode) lineups.get(sideName);
            var originals = (ArrayNode) side.get("players");
            var players = side.putArray("players");
            for (int i = 0; i < 22; i++) {
                var player = ((ObjectNode) originals.get(i == 0 ? 0 : 1)).deepCopy();
                player.put("shirtNumber", i + 1); player.put("substitute", i >= 11);
                ((ObjectNode) player.get("player")).put("id", 10000 + sideIndex * 1000 + i);
                if (V6_OR_LATER) {
                    player.put("captain", i == 1);
                    var individual = player.putObject("statistics");
                    for (String key : List.of("minutesPlayed", "touches", "totalPass", "accuratePass", "totalShots",
                            "onTargetScoringAttempt", "shotOffTarget", "goals", "goalAssist", "keyPass", "totalTackle",
                            "wonTackle", "duelWon", "duelLost", "fouls", "wasFouled", "saves")) individual.put(key, i % 4);
                    individual.put("expectedGoals", new java.math.BigDecimal("0.01852610000000000001"));
                    individual.put("expectedAssists", new java.math.BigDecimal("0.21"));
                    individual.put("rating", new java.math.BigDecimal("6.8"));
                    individual.putObject("ratingVersions").put("original", new java.math.BigDecimal("6.8"))
                            .put("alternative", new java.math.BigDecimal("6.75"));
                }
                players.add(player);
            }
            if (V6_OR_LATER) {
                var missing = side.putArray("missingPlayers");
                for (int i = 0; i < 2; i++) {
                    var entry = missing.addObject();
                    entry.putObject("player").put("id", 20000 + sideIndex * 1000 + i).put("name", "Synthetic Missing " + sideIndex + " " + i);
                    entry.put("type", "missing").put("reason", 1).put("externalType", 5).put("description", "Synthetic reason")
                            .put("expectedEndDate", "2026-10-01T12:00:00+02:00");
                }
            }
            sideIndex++;
        }
        result.put("lineups", mapper.writeValueAsString(lineups));
        return result;
    }

    private static byte[] body(Map<String, String> templates, long eventId, SofascoreEndpointType endpoint, long version) {
        return body(templates, eventId, endpoint, version, BODY_BYTES);
    }
    private static byte[] body(Map<String, String> templates, long eventId, SofascoreEndpointType endpoint, long version, int size) {
        String family = endpoint == EVENT_DETAILS ? "details" : endpoint == EVENT_INCIDENTS ? "incidents"
                : endpoint == EVENT_STATISTICS ? "statistics" : "lineups";
        String json = templates.get(family).replace("900001", Long.toString(eventId));
        if (endpoint == EVENT_DETAILS) json = "{\"event\":" + json.replace("notstarted", "inprogress")
                .replace("Not started", "Second half").replace("Synthetic Park", "Synthetic Park " + version) + "}";
        if (endpoint == EVENT_STATISTICS && version % 2 != 0) json = json.replace("54%", "55%").replace("46%", "45%");
        if (endpoint == EVENT_INCIDENTS) json = json.replace("Synthetic Home Striker", "Synthetic Home Striker " + version);
        if (endpoint == EVENT_LINEUPS && version % 2 != 0) json = json.replace("4-3-3", "4-4-2");
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[size];
        Arrays.fill(padded, (byte) ' ');
        System.arraycopy(content, 0, padded, 0, content.length);
        return padded;
    }

    private static String path(long eventId, SofascoreEndpointType endpoint) {
        return "/api/v1/event/" + eventId + (endpoint == EVENT_DETAILS ? "" : endpoint == EVENT_INCIDENTS ? "/incidents"
                : endpoint == EVENT_STATISTICS ? "/statistics" : "/lineups");
    }
    private static RawManualCallSnapshot raw(SofascoreEndpointType endpoint, long eventId, PlaywrightProviderResponse response) {
        return new RawManualCallSnapshot(endpoint, endpoint.name() + "|eventId=" + eventId, response.requestedAt(), response.receivedAt(),
                response.httpStatus(), response.contentType(), response.latency(), response.payload(), LivePayloadNormalizer.parserVersion(endpoint),
                RawSnapshotSchemaStatus.RAW_ONLY, null);
    }

    /** Mirrors the V8 production path: only authenticated worker request evidence may rephase its schedule. */
    private static void recordObservedV8Departure(LiveSchedule schedule, LiveSchedule.Due due, Instant requestedAt) {
        if (V8 && requestedAt != null) schedule.departed(due, requestedAt);
    }

    private static Instant at(Instant origin, long originNano) { return origin.plusNanos(System.nanoTime() - originNano); }
    private static Path dockerExecutable() {
        String configured = System.getProperty("wo058.grouped.docker");
        if (configured != null) return Path.of(configured).toAbsolutePath();
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "docker.exe" : "docker";
        for (String directory : System.getenv("PATH").split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(directory).resolve(executable);
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        throw new IllegalStateException("qualification requires an explicit local Docker executable");
    }
    private static double seconds(long nanos) { return nanos / 1_000_000_000d; }
    private static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) return 0;
        var sorted = values.stream().sorted().toList();
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * p) - 1));
    }
    private static Map<String, Object> summary(List<Long> values) {
        return Map.of("count", values.size(), "p95", seconds(percentile(values, .95)), "maximum", seconds(percentile(values, 1)));
    }
    record Sample(long providerEventId, SofascoreEndpointType endpoint, long round, String groupId, long dueNanos,
                  long receivedNanos, long committedNanos, long requestNanos, long processingNanos,
                  long serverArrivalNanos, long responseCompleteNanos, long serverDelayMillis, int bodyBytes,
                  long requestedNanos, long httpNanos, long authorizationDelayNanos, long pressureElapsedNanos,
                  long limiterSleepNanos, long resilienceSqlNanos, long resilienceFinishedNanos) { }

    private static final class Database implements AutoCloseable {
        final HikariDataSource dataSource;
        final JdbcTemplate jdbc;
        final RawManualCallSnapshotStore rawStore;
        final LiveCampaignStore store;
        final ProviderCampaignGuardStore guard;
        final LiveResponseProcessor processor;
        final MeteredResilienceStore resilience;
        final LiveDiagnosticStore diagnostics;
        final TransactionTemplate transaction;
        Database(PostgreSQLContainer postgres) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(postgres.getJdbcUrl()); config.setUsername(postgres.getUsername()); config.setPassword(postgres.getPassword());
            config.setMaximumPoolSize(4); config.setMinimumIdle(1); config.setPoolName("grouped-qualification");
            dataSource = new HikariDataSource(config);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            jdbc = new JdbcTemplate(dataSource);
            var named = new NamedParameterJdbcTemplate(dataSource);
            var manager = new JdbcTransactionManager(dataSource);
            transaction = new TransactionTemplate(manager);
            rawStore = transactional(new JdbcRawManualCallSnapshotStore(named), RawManualCallSnapshotStore.class, manager);
            var canonical = transactional(new JdbcCanonicalEventStore(named), CanonicalEventStore.class, manager);
            var details = transactional(new JdbcEventDetailsStore(named), EventDetailsStore.class, manager);
            var data = transactional(new JdbcJ5EventDataStore(named), J5EventDataStore.class, manager);
            store = transactional(new JdbcLiveCampaignStore(jdbc, rawStore), LiveCampaignStore.class, manager);
            guard = transactional(new JdbcProviderCampaignGuardStore(jdbc), ProviderCampaignGuardStore.class, manager);
            resilience = new MeteredResilienceStore(transactional(new JdbcProviderResilienceStore(jdbc), ProviderResilienceStore.class, manager));
            diagnostics = transactional(new JdbcLiveDiagnosticStore(jdbc), LiveDiagnosticStore.class, manager);
            processor = transactional(new LiveResponseProcessor(canonical, details, data, rawStore), LiveResponseProcessor.class, manager);
        }
        Target seed(long eventId, byte[] body) {
            Instant at = Instant.now().minusSeconds(60);
            var response = new PlaywrightProviderResponse(at, at, 200, "application/json", Duration.ZERO, RawPayloadEvidence.capture(body));
            var receipt = rawStore.save(raw(EVENT_DETAILS, eventId, response));
            var parsed = processor.process(CanonicalEventIdentity.sofascore(eventId), EVENT_DETAILS, response, receipt);
            assertThat(parsed.outcome()).as("seed %s", parsed.code()).isEqualTo(LiveProcessedResponse.Outcome.PARSED);
            var normalized = Objects.requireNonNull(transaction.execute(status -> processor.persistProcessed(parsed)));
            return new Target(CanonicalEventIdentity.sofascore(eventId).value(), eventId, normalized.canonicalObservationId(), receipt.snapshotId());
        }
        public void close() { dataSource.close(); }
    }
    /** Only observes real repository costs; decisions and clocks are never replaced. */
    private static final class MeteredResilienceStore implements ProviderResilienceStore {
        private final ProviderResilienceStore delegate;
        long sqlNanos;
        Instant lastFinished;
        MeteredResilienceStore(ProviderResilienceStore delegate) { this.delegate = delegate; }
        private <T> T measured(Supplier<T> work) {
            long started = System.nanoTime();
            try { return work.get(); } finally { sqlNanos += System.nanoTime() - started; }
        }
        public ProviderResilienceData.Snapshot snapshot() { return measured(delegate::snapshot); }
        public ProviderResilienceData.DepartureDecision departureDecision(Instant at) {
            return measured(() -> delegate.departureDecision(at));
        }
        @Override
        public ProviderResilienceData.DepartureDecision departureDecision(
                ProviderResilienceData.DepartureProfile profile, Instant at) {
            return measured(() -> delegate.departureDecision(profile, at));
        }
        public ProviderResilienceData.DepartureDecision tryReserveDeparture(UUID id, Instant at) {
            return measured(() -> delegate.tryReserveDeparture(id, at));
        }
        @Override
        public ProviderResilienceData.DepartureDecision tryReserveDeparture(UUID id,
                ProviderResilienceData.DepartureProfile profile, Instant at) {
            return measured(() -> delegate.tryReserveDeparture(id, profile, at));
        }
        @Override
        public void recordAuthenticatedV8Departure(UUID id, Instant requestedAt, Instant observedAt) {
            measured(() -> {
                delegate.recordAuthenticatedV8Departure(id, requestedAt, observedAt);
                return null;
            });
        }
        public ProviderResilienceData.Snapshot markDepartureFinished(UUID id, Instant at) {
            var result = measured(() -> delegate.markDepartureFinished(id, at));
            lastFinished = result.lastDepartureFinishedAt();
            return result;
        }
        public ProviderResilienceData.Snapshot suspend(UUID evidence, UUID campaign, int status, Instant at, Instant retry) {
            return measured(() -> delegate.suspend(evidence, campaign, status, at, retry));
        }
        public ProviderResilienceData.Snapshot rearm(long version, Instant at) {
            return measured(() -> delegate.rearm(version, at));
        }
    }
    private static <T> T transactional(T object, Class<T> type, JdbcTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(object);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
}
