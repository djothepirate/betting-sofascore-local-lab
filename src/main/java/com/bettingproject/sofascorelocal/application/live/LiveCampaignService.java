package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.config.*;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.port.*;
import com.bettingproject.sofascorelocal.security.Sha256;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Service
public final class LiveCampaignService {
    private final SofascoreProperties provider;
    private final ProviderPlaywrightProperties playwright;
    private final LiveCampaignProperties properties;
    private final LiveAdmissionPolicy admission;
    private final LiveCampaignStore store;
    private final CanonicalEventStore events;
    private final ManualProviderRequestCoordinator coordinator;
    private final ProviderCampaignGuardStore guard;
    private final PlaywrightProviderCampaignFactory factory;
    private final PlaywrightProviderSupervisor supervisor;
    private final LiveResponseProcessor processor;
    private final AtomicReference<Session> active = new AtomicReference<>();
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LiveCampaignService(SofascoreProperties provider, ProviderPlaywrightProperties playwright,
            LiveCampaignProperties properties, LiveAdmissionPolicy admission, LiveCampaignStore store,
            CanonicalEventStore events, ManualProviderRequestCoordinator coordinator,
            ProviderCampaignGuardStore guard, PlaywrightProviderCampaignFactory factory,
            PlaywrightProviderSupervisor supervisor, LiveResponseProcessor processor) {
        this(provider, playwright, properties, admission, store, events, coordinator, guard, factory,
                supervisor, processor, Clock.systemUTC());
    }

    LiveCampaignService(SofascoreProperties provider, ProviderPlaywrightProperties playwright,
            LiveCampaignProperties properties, LiveAdmissionPolicy admission, LiveCampaignStore store,
            CanonicalEventStore events, ManualProviderRequestCoordinator coordinator,
            ProviderCampaignGuardStore guard, PlaywrightProviderCampaignFactory factory,
            PlaywrightProviderSupervisor supervisor, LiveResponseProcessor processor, Clock clock) {
        this.provider = provider; this.playwright = playwright; this.properties = properties;
        this.admission = admission; this.store = store; this.events = events; this.coordinator = coordinator;
        this.guard = guard; this.factory = factory; this.supervisor = supervisor; this.processor = processor;
        this.clock = Objects.requireNonNull(clock);
    }

    public Manifest prepare(List<UUID> selected) {
        Objects.requireNonNull(selected);
        if (selected.isEmpty() || selected.size() > 3 || new HashSet<>(selected).size() != selected.size())
            throw new IllegalArgumentException("LIVE_SELECTION_INVALID");
        admission.admit(selected.size());
        List<Target> targets = selected.stream().map(id -> {
            var event = events.findLatestByCanonicalId(id).orElseThrow(() -> new IllegalArgumentException("LIVE_EVENT_NOT_FOUND"));
            if (event.source().kind() != EventSourceKind.PROVIDER_SNAPSHOT)
                throw new IllegalArgumentException("LIVE_PROVIDER_PROVENANCE_REQUIRED");
            if (event.identity().providerEventId() > EventDetailsProviderRequest.MAXIMUM_PARAMETERIZED_EVENT_ID)
                throw new IllegalArgumentException("LIVE_EVENT_ID_OUT_OF_RANGE");
            return new Target(id, event.identity().providerEventId(), event.observationId(), event.source().snapshotId().orElseThrow());
        }).toList();
        UUID id = UUID.randomUUID(); Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        long bytes = admission.maximumBytes(targets.size());
        String material = id + "|live-v1|" + now + "|" + properties.getDuration() + "|1000|3000|" + bytes
                + "|" + properties.getQualifiedMatchCapacity() + "|" + properties.getQualificationSha256()
                + "|" + properties.getRequestEnvelope() + "|" + properties.getProcessingEnvelope() + "|" + targets;
        Manifest manifest = new Manifest(id, Sha256.hex(material.getBytes(StandardCharsets.UTF_8)), "live-v1",
                now, now.plusSeconds(300), properties.getDuration(), 1000, 3000, bytes,
                properties.getQualifiedMatchCapacity(), targets, currentAdmissionProfile());
        return store.prepare(manifest);
    }

    public CampaignView state(UUID id) { return store.find(id).orElseThrow(() -> new NoSuchElementException("LIVE_CAMPAIGN_NOT_FOUND")); }
    public List<CampaignView> eventStates(List<UUID> ids) {
        Set<UUID> wanted = Set.copyOf(ids);
        if (wanted.size() > 100) throw new IllegalArgumentException("LIVE_SELECTION_INVALID");
        Map<UUID,CampaignView> selected = new LinkedHashMap<>();
        for (UUID id : wanted) store.latestForEvent(id).ifPresent(c -> selected.put(c.manifest().campaignId(), c));
        return List.copyOf(selected.values());
    }

    public CampaignView launch(UUID id, String hash) {
        CampaignView current = state(id);
        if (!current.manifest().manifestSha256().equals(hash)) throw new IllegalArgumentException("LIVE_MANIFEST_MISMATCH");
        if (!"PREPARED".equals(current.state())) return current;
        if (!properties.isEnabled() || !provider.isEnabled() || !playwright.isEnabled())
            throw new IllegalStateException("LIVE_DISABLED");
        if (playwright.getRequestTimeout() == null || playwright.getRequestTimeout().isNegative()
                || playwright.getRequestTimeout().isZero()
                || playwright.getRequestTimeout().compareTo(Duration.ofSeconds(10)) > 0)
            throw new IllegalStateException("LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY");
        if (!clock.instant().isBefore(current.manifest().expiresAt())) throw new IllegalArgumentException("LIVE_MANIFEST_EXPIRED");
        if (!current.manifest().admissionProfile().equals(currentAdmissionProfile())
                || current.manifest().qualifiedMatchCapacity() != properties.getQualifiedMatchCapacity()
                || !current.manifest().duration().equals(properties.getDuration()))
            throw new IllegalArgumentException("LIVE_PREPARED_POLICY_CHANGED");
        admission.admit(current.manifest().targets().size());
        Session session = new Session(current.manifest());
        if (!active.compareAndSet(null, session)) {
            Session existing = active.get();
            if (existing != null && existing.manifest.campaignId().equals(id)) return state(id);
            throw new IllegalStateException("LIVE_PROVIDER_BUSY");
        }
        Thread.ofPlatform().daemon(true).name("live-campaign-" + id).start(() -> run(session));
        try { session.launched.get(5, TimeUnit.SECONDS); }
        catch (Exception failure) {
            session.stopAll("STOPPED_ERROR");
            throw new IllegalStateException("LIVE_LAUNCH_FAILED");
        }
        return state(id);
    }

    private AdmissionProfile currentAdmissionProfile() {
        return new AdmissionProfile(properties.getRequestEnvelope(), properties.getProcessingEnvelope(), properties.getQualificationSha256());
    }

    public void stop(UUID campaignId, UUID eventId) {
        Session s = active.get();
        if (s == null || !s.manifest.campaignId().equals(campaignId)) {
            if ("RUNNING".equals(state(campaignId).state())) throw new IllegalStateException("LIVE_CAMPAIGN_NOT_OWNED");
            return;
        }
        if (eventId != null && s.manifest.targets().stream().noneMatch(t -> t.canonicalEventId().equals(eventId)))
            throw new IllegalArgumentException("LIVE_EVENT_NOT_SELECTED");
        if (eventId == null) {
            s.stopAll("STOPPED_OPERATOR");
            supervisor.stopCampaign(campaignId, LiveProviderSession.ENDPOINTS);
        } else {
            s.dispatchLock.lock();
            try { if (s.schedule != null) s.schedule.stopEvent(eventId, "STOPPED_OPERATOR"); s.stoppedEvents.add(eventId); }
            finally { s.dispatchLock.unlock(); }
        }
        // SQL evidence is appended by the owner thread, so acknowledgement never waits for PostgreSQL.
    }

    private void run(Session s) {
        ManualProviderRequestCoordinator.CampaignLease lease = null;
        LiveProviderSession transport = null;
        try {
            lease = coordinator.acquireLiveCampaign(s.manifest.campaignId());
            s.ownership = lease.ownership();
            Launch started = store.launch(s.manifest.campaignId(), s.manifest.manifestSha256(), s.ownership, clock.instant());
            s.monotonicOrigin = System.nanoTime(); s.timeOrigin = started.startedAt();
            s.schedule = new LiveSchedule(s.manifest.targets().stream().map(Target::canonicalEventId).toList(), started.startedAt(), started.endsAt());
            s.launched.complete(null);
            if (s.stopReason != null) return;
            startWatchdog(s);
            transport = new LiveProviderSession(factory, s.manifest.campaignId());
            long lastWake = System.nanoTime(); Instant lastWall = clock.instant();
            while (!s.schedule.terminal() && s.stopReason == null) {
                Instant now = clock.instant();
                long monotonic = System.nanoTime();
                long wallDelta = Duration.between(lastWall, now).toMillis();
                long monotonicDelta = TimeUnit.NANOSECONDS.toMillis(monotonic - lastWake);
                if (monotonicDelta > 2500 || Math.abs(wallDelta - monotonicDelta) > 2000) {
                    s.stopAll("STOPPED_INTERRUPTED"); break;
                }
                for (UUID stopped : s.stoppedEvents) s.schedule.stopEvent(stopped, "STOPPED_OPERATOR");
                var due = s.schedule.next(s.now());
                if (due.isPresent()) execute(s, transport, due.orElseThrow());
                publishStates(s);
                lastWake = System.nanoTime(); lastWall = clock.instant();
                Thread.sleep(100);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); s.stopAll("STOPPED_INTERRUPTED");
        } catch (RuntimeException failure) {
            s.stopAll("STOPPED_ERROR"); s.launched.completeExceptionally(new IllegalStateException("LIVE_LAUNCH_FAILED"));
        } finally {
            s.finished = true;
            if (s.schedule != null && s.stopReason != null) s.schedule.stopAll(s.stopReason);
            boolean cleaned = false;
            try {
                if (transport != null) transport.close();
                cleaned = supervisor.activeCampaignId().filter(s.manifest.campaignId()::equals).isEmpty();
                if (s.ownership != null) {
                    publishStates(s);
                    String terminal = cleaned ? s.stopReason != null ? s.stopReason
                            : s.schedule != null && s.schedule.globalStop() != null ? s.schedule.globalStop() : "COMPLETED" : "CLEANUP_REQUIRED";
                    store.transition(s.ownership, null, terminal, terminal, clock.instant(), null);
                }
            } catch (RuntimeException cleanupFailure) { cleaned = false; }
            if (!cleaned && s.ownership != null) {
                try { guard.requireCleanup(s.ownership, clock.instant()); } catch (RuntimeException ignored) { /* durable exclusion is not released */ }
            }
            if (cleaned && lease != null) {
                try { lease.close(); } catch (RuntimeException failure) { cleaned = false; }
            }
            if (cleaned || lease == null) active.compareAndSet(s, null);
        }
    }

    private void execute(Session s, LiveProviderSession transport, LiveSchedule.Due due) {
        CampaignView view = state(s.manifest.campaignId());
        EventView event = view.events().stream().filter(e -> e.target().canonicalEventId().equals(due.eventId())).findFirst().orElseThrow();
        int reservedRemaining = s.manifest.maximumCallsPerEvent() - event.reservedCalls();
        if (!due.finalCycle() && (reservedRemaining <= 4 || s.manifest.maximumCalls() - view.reservedCalls() <= 4)) {
            s.schedule.reserveFinalCheck(due.eventId(), s.now()); return;
        }
        admission.requireStorage(s.manifest.maximumBytes() - view.receivedBytes());
        var request = new AttemptRequest(s.ownership, UUID.randomUUID(), due.eventId(), due.cycle(), due.endpoint(),
                due.kind(), due.dueAt(), clock.instant(), due.finalCycle());
        var reservation = store.reserveAttempt(request);
        if (reservation.isEmpty()) { s.schedule.stopEvent(due.eventId(), "STOPPED_LIMIT"); return; }
        ReservedAttempt attempt = reservation.orElseThrow();
        try {
            var response = transport.execute(attempt.providerEventId(), due.endpoint(), new PlaywrightDispatchAdmission() {
                @Override public void check() {
                    if (s.stopReason != null || s.stoppedEvents.contains(due.eventId())
                            || !s.schedule.mayDispatch(due, s.now())
                            || !clock.instant().isBefore(s.timeOrigin.plus(s.manifest.duration())))
                        throw new PlaywrightDispatchCancelledException();
                }
                @Override public Permit acquireDispatchPermit() {
                    // DB ownership check occurs before the short stop/dispatch critical section.
                    check();
                    if (!guard.isOwned(s.ownership)) throw new PlaywrightDispatchCancelledException();
                    store.recordDispatch(s.ownership, attempt.attemptId(), clock.instant());
                    s.dispatchLock.lock();
                    try {
                        check();
                        s.schedule.started(due, s.now());
                        return s.dispatchLock::unlock;
                    } catch (RuntimeException failure) { s.dispatchLock.unlock(); throw failure; }
                }
            });
            String parser = due.endpoint() == SofascoreEndpointType.EVENT_DETAILS ? "event-details-v2"
                    : due.endpoint() == SofascoreEndpointType.EVENT_INCIDENTS ? "event-incidents-v15"
                    : due.endpoint() == SofascoreEndpointType.EVENT_STATISTICS ? "event-statistics-v2" : "event-lineups-v2";
            RawManualCallSnapshot raw = new RawManualCallSnapshot(due.endpoint(), due.endpoint().name() + "|eventId=" + attempt.providerEventId(),
                    response.requestedAt(), response.receivedAt(), response.httpStatus(), response.contentType(), response.latency(),
                    response.payload(), parser, RawSnapshotSchemaStatus.RAW_ONLY, null);
            var receipt = store.saveReceipt(s.ownership, attempt.attemptId(), raw);
            LiveProcessedResponse processed = processor.process(CanonicalEventIdentity.sofascore(attempt.providerEventId()), due.endpoint(), response, receipt);
            Map<String, Boolean> signals = new LinkedHashMap<>();
            processed.signals().forEach(signal -> signals.put(signal.key(), signal.kind().name().equals("FINISH_CHECK")));
            boolean unavailable = processed.outcome().name().equals("ENDPOINT_UNAVAILABLE");
            if (processed.scope().name().equals("NONE")) {
                s.schedule.completed(due, processed.sportStatus().orElse(null), unavailable, signals, s.now());
            } else s.schedule.failed(due, processed.scope().name(), processed.scope().name().equals("EVENT")
                    ? processed.outcome().name().equals("SCHEMA_INCOMPATIBLE") ? "STOPPED_SCHEMA_INCOMPATIBLE" : "STOPPED_REVIEW_REQUIRED"
                    : "STOPPED_ERROR");
            String state = s.schedule.states().stream().filter(e -> e.eventId().equals(due.eventId())).findFirst().orElseThrow().state();
            var complete = processed.completeness();
            Publication publication = new Publication(processed.outcome().name(), processed.scope().name(), processed.code(), clock.instant(),
                    processed.parserVersion(), processed.outcome().name().equals("PARSED"), state,
                    processed.sportStatus().orElse(null), processed.projectionJson(), processed.projectionVersion(),
                    complete.map(c -> c.status().name()).orElse(null), complete.map(c -> c.scorePercent()).orElse(null));
            store.publishResult(s.ownership, attempt.attemptId(), publication, () -> processor.persistProcessed(processed));
        } catch (PlaywrightDispatchCancelledException cancelled) {
            store.publishResult(s.ownership, attempt.attemptId(), new Publication("NOT_DISPATCHED", "EVENT", "DISPATCH_CANCELLED",
                    clock.instant(), null, false, null), NormalizedReferences::none);
            if (s.schedule.mayDispatch(due, s.now()) && !s.stoppedEvents.contains(due.eventId())) s.stopAll("STOPPED_ERROR");
        } catch (RuntimeException failure) {
            s.schedule.failed(due, "CAMPAIGN", "STOPPED_ERROR");
            String code = failure instanceof PlaywrightProviderException transportFailure
                    ? "PLAYWRIGHT_" + transportFailure.failure().name()
                    : "LIVE_RAW_PREVIOUSLY_PURGED".equals(failure.getMessage())
                            ? "LIVE_RAW_PREVIOUSLY_PURGED" : "RUNTIME_OR_STORAGE_FAILURE";
            try { store.publishResult(s.ownership, attempt.attemptId(), new Publication("FAILED", "CAMPAIGN", code,
                    clock.instant(), null, false, null), NormalizedReferences::none); } catch (RuntimeException ignored) { /* receipt remains durable */ }
            throw failure;
        }
    }

    private void publishStates(Session s) {
        if (s.schedule == null || s.ownership == null) return;
        for (var state : s.schedule.states()) {
            String previous = s.publishedStates.put(state.eventId(), state.state());
            if (!state.state().equals(previous)) store.transition(s.ownership, state.eventId(), state.state(), state.state(), clock.instant(), null);
            if (!state.equals(s.publishedMetrics.put(state.eventId(), state)))
                store.updateScheduleMetrics(s.ownership, state.eventId(), state.nextDueAt(), state.missedCycles(), state.finalComplete(), clock.instant());
        }
    }

    private void startWatchdog(Session s) {
        Thread.ofPlatform().daemon(true).name("live-session-watchdog").start(() -> {
            long before = System.nanoTime(); Instant wall = clock.instant();
            while (!s.finished && s.stopReason == null) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
                long now = System.nanoTime(); Instant next = clock.instant();
                long monotonic = TimeUnit.NANOSECONDS.toMillis(now - before);
                if (monotonic > 2500 || Math.abs(Duration.between(wall, next).toMillis() - monotonic) > 2000) {
                    s.stopAll("STOPPED_INTERRUPTED");
                    supervisor.stopCampaign(s.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
                    return;
                }
                before = now; wall = next;
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markProvenOrphanWithoutRestart() {
        Guard g = guard.snapshot();
        if (g.owner() == null || "FREE".equals(g.state())) return;
        Optional<ProcessHandle> process = ProcessHandle.of(g.owner().processId());
        if (process.isPresent() && process.orElseThrow().isAlive()) {
            Optional<Instant> observedStart = process.orElseThrow().info().startInstant();
            if (observedStart.isEmpty()) return; // An inaccessible identity is not proof of death.
            if (observedStart.orElseThrow().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                    .equals(g.owner().processStartedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))) return;
        }
        {
            // No automatic release: an orphan worker may still exist even when its parent died.
            if (store.find(g.campaignId()).isPresent()) store.interruptOrphan(g.ownership(), clock.instant(), "OWNER_PROCESS_ABSENT");
            guard.requireCleanup(g.ownership(), clock.instant());
        }
    }
    @PreDestroy public void shutdown() { Session s = active.get(); if (s != null) { s.stopAll("STOPPED_INTERRUPTED"); supervisor.stopCampaign(s.manifest.campaignId(), LiveProviderSession.ENDPOINTS); } }
    private static final class Session {
        final Manifest manifest; final CompletableFuture<Void> launched = new CompletableFuture<>();
        final ReentrantLock dispatchLock = new ReentrantLock(); final Set<UUID> stoppedEvents = ConcurrentHashMap.newKeySet();
        final Map<UUID,String> publishedStates = new HashMap<>(); final Map<UUID,LiveSchedule.EventState> publishedMetrics = new HashMap<>();
        volatile String stopReason; volatile LiveSchedule schedule; volatile Ownership ownership; volatile boolean finished;
        volatile long monotonicOrigin; volatile Instant timeOrigin;
        Session(Manifest manifest) { this.manifest = manifest; }
        Instant now() { return timeOrigin.plusNanos(System.nanoTime() - monotonicOrigin); }
        void stopAll(String reason) { dispatchLock.lock(); try { stopReason = reason; if (schedule != null) schedule.stopAll(reason); } finally { dispatchLock.unlock(); } }
    }
}
