package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.config.*;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.port.*;
import com.bettingproject.sofascorelocal.security.Sha256;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic.Phase.*;

@Service
public final class LiveCampaignService {
    private static final Logger LOG = LoggerFactory.getLogger(LiveCampaignService.class);
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
    private final LiveOrphanProcessProbe orphanProcesses;

    @org.springframework.beans.factory.annotation.Autowired
    public LiveCampaignService(SofascoreProperties provider, ProviderPlaywrightProperties playwright,
            LiveCampaignProperties properties, LiveAdmissionPolicy admission, LiveCampaignStore store,
            CanonicalEventStore events, ManualProviderRequestCoordinator coordinator,
            ProviderCampaignGuardStore guard, PlaywrightProviderCampaignFactory factory,
            PlaywrightProviderSupervisor supervisor, LiveResponseProcessor processor,
            LiveOrphanProcessProbe orphanProcesses) {
        this(provider, playwright, properties, admission, store, events, coordinator, guard, factory,
                supervisor, processor, Clock.systemUTC(), orphanProcesses);
    }

    public LiveCampaignService(SofascoreProperties provider, ProviderPlaywrightProperties playwright,
            LiveCampaignProperties properties, LiveAdmissionPolicy admission, LiveCampaignStore store,
            CanonicalEventStore events, ManualProviderRequestCoordinator coordinator,
            ProviderCampaignGuardStore guard, PlaywrightProviderCampaignFactory factory,
            PlaywrightProviderSupervisor supervisor, LiveResponseProcessor processor) {
        this(provider, playwright, properties, admission, store, events, coordinator, guard, factory,
                supervisor, processor, Clock.systemUTC(), new LiveOrphanProcessProbe());
    }

    LiveCampaignService(SofascoreProperties provider, ProviderPlaywrightProperties playwright,
            LiveCampaignProperties properties, LiveAdmissionPolicy admission, LiveCampaignStore store,
            CanonicalEventStore events, ManualProviderRequestCoordinator coordinator,
            ProviderCampaignGuardStore guard, PlaywrightProviderCampaignFactory factory,
            PlaywrightProviderSupervisor supervisor, LiveResponseProcessor processor, Clock clock) {
        this(provider, playwright, properties, admission, store, events, coordinator, guard, factory,
                supervisor, processor, clock, new LiveOrphanProcessProbe());
    }

    LiveCampaignService(SofascoreProperties provider, ProviderPlaywrightProperties playwright,
            LiveCampaignProperties properties, LiveAdmissionPolicy admission, LiveCampaignStore store,
            CanonicalEventStore events, ManualProviderRequestCoordinator coordinator,
            ProviderCampaignGuardStore guard, PlaywrightProviderCampaignFactory factory,
            PlaywrightProviderSupervisor supervisor, LiveResponseProcessor processor, Clock clock,
            LiveOrphanProcessProbe orphanProcesses) {
        this.provider = provider; this.playwright = playwright; this.properties = properties;
        this.admission = admission; this.store = store; this.events = events; this.coordinator = coordinator;
        this.guard = guard; this.factory = factory; this.supervisor = supervisor; this.processor = processor;
        this.clock = Objects.requireNonNull(clock);
        this.orphanProcesses = Objects.requireNonNull(orphanProcesses);
    }

    public record Preparation(Manifest manifest, List<CanonicalEventObservationView> excludedFinished,
                              List<CanonicalEventObservationView> excludedPostponed) {
        public Preparation {
            excludedFinished = List.copyOf(excludedFinished);
            excludedPostponed = List.copyOf(excludedPostponed);
        }
        public Preparation(Manifest manifest, List<CanonicalEventObservationView> excludedFinished) {
            this(manifest, excludedFinished, List.of());
        }
    }

    public Manifest prepare(List<UUID> selected) {
        Preparation preparation = prepareSelection(selected);
        if (preparation.manifest() == null) throw new IllegalArgumentException(
                preparation.excludedPostponed().isEmpty() ? "LIVE_ALL_EVENTS_FINISHED" : "LIVE_ALL_EVENTS_INELIGIBLE");
        return preparation.manifest();
    }

    public Preparation prepareSelection(List<UUID> selected) {
        Objects.requireNonNull(selected);
        if (selected.isEmpty() || selected.size() > 100 || new HashSet<>(selected).size() != selected.size())
            throw new IllegalArgumentException("LIVE_SELECTION_INVALID");
        requireSelectable(selected);
        List<CanonicalEventObservationView> observations = selected.stream().map(this::latestEligibleSource).toList();
        List<CanonicalEventObservationView> excludedFinished = observations.stream()
                .filter(event -> "finished".equals(event.status().type())).toList();
        List<CanonicalEventObservationView> excludedPostponed = observations.stream()
                .filter(event -> "postponed".equals(event.status().type())).toList();
        List<Target> targets = observations.stream().filter(event -> exclusionState(event) == null)
                .map(event -> new Target(event.identity().value(), event.identity().providerEventId(),
                        event.observationId(), event.source().snapshotId().orElseThrow())).toList();
        if (targets.isEmpty()) return new Preparation(null, excludedFinished, excludedPostponed);
        AdmissionProfile profile = currentAdmissionProfile("live-v5");
        admission.admitV5(targets.size(), profile.groupedProfile());
        Duration cycleInterval = Duration.ofSeconds(100);
        UUID id = UUID.randomUUID(); Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        long bytes = admission.maximumBytesV5(targets.size());
        // Fixed order and explicit rules: a historic proof or a changed family envelope cannot
        // silently authorize a new grouped manifest.
        String material = id + "|live-v5|" + now + "|" + properties.getDuration() + "|2500|20000|" + bytes
                + "|" + selectionMaximum() + "|" + profile + "|critical=100|lineups=300|prematch=100"
                + "|intra=0|inter=1|sequential|maxGroup=4|order=J4,incidents,statistics,lineups"
                + "|phaseCount=3|utilization=0.9|" + targets;
        Manifest manifest = new Manifest(id, Sha256.hex(material.getBytes(StandardCharsets.UTF_8)), "live-v5",
                now, now.plusSeconds(300), properties.getDuration(), 2500, 20000, bytes,
                selectionMaximum(), targets, profile, cycleInterval);
        return new Preparation(store.prepare(manifest), excludedFinished, excludedPostponed);
    }

    private CanonicalEventObservationView latestEligibleSource(UUID id) {
        var event = events.findLatestByCanonicalId(id)
                .orElseThrow(() -> new IllegalArgumentException("LIVE_EVENT_NOT_FOUND"));
        if (!event.identity().value().equals(id)) throw new IllegalArgumentException("LIVE_EVENT_IDENTITY_MISMATCH");
        if (event.source().kind() != EventSourceKind.PROVIDER_SNAPSHOT)
            throw new IllegalArgumentException("LIVE_PROVIDER_PROVENANCE_REQUIRED");
        if (event.identity().providerEventId() > EventDetailsProviderRequest.MAXIMUM_PARAMETERIZED_EVENT_ID)
            throw new IllegalArgumentException("LIVE_EVENT_ID_OUT_OF_RANGE");
        return event;
    }

    private static String exclusionState(CanonicalEventObservationView event) {
        return switch (event.status().type()) {
            case "finished" -> "STOPPED_ALREADY_FINISHED";
            case "postponed" -> "STOPPED_ALREADY_POSTPONED";
            default -> null;
        };
    }

    private Map<UUID, String> locallyExcluded(Manifest manifest) {
        Map<UUID, String> excluded = new LinkedHashMap<>();
        for (Target target : manifest.targets()) {
            String state = exclusionState(latestEligibleSource(target.canonicalEventId()));
            if (state != null) excluded.put(target.canonicalEventId(), state);
        }
        return excluded;
    }

    public CampaignView state(UUID id) { return store.find(id).orElseThrow(() -> new NoSuchElementException("LIVE_CAMPAIGN_NOT_FOUND")); }

    /** Process observation only: never replaces or fabricates a durable ledger state. */
    public record RuntimeStatus(String state, String reason, boolean collectionStopped,
                                boolean cleanupPending, boolean cleanupInProgress,
                                LiveCampaignDiagnostic firstFailure, LiveCampaignDiagnostic cleanupFailure) {
        public RuntimeStatus(String state, String reason, boolean collectionStopped,
                             boolean cleanupPending, boolean cleanupInProgress) {
            this(state, reason, collectionStopped, cleanupPending, cleanupInProgress, null, null);
        }
    }

    public Optional<RuntimeStatus> runtimeStatus(UUID campaignId) {
        Session s = active.get();
        if (s == null || !s.manifest.campaignId().equals(campaignId)
                || s.stopReason == null && !s.finished) return Optional.empty();
        String state = s.stopReason != null ? s.stopReason
                : s.cleanupPending ? "CLEANUP_REQUIRED" : "COMPLETED";
        return Optional.of(new RuntimeStatus(state,
                s.cleanupPending ? "LOCAL_CLEANUP_PENDING" : state,
                true, s.cleanupPending, s.cleanupInProgress, s.firstFailure.get(), s.cleanupFailure));
    }
    public int selectionMaximum() {
        return selectionMaximum("live-v5");
    }
    private int selectionMaximum(String policyVersion) {
        try { return Math.min(properties.getQualifiedMatchCapacity(),
                "live-v5".equals(policyVersion)
                        ? LiveAdmissionPolicy.qualifiedCapacityV5(properties.groupedAdmissionProfileV5())
                        : LiveAdmissionPolicy.qualifiedCapacityV4(properties.groupedAdmissionProfile())); }
        catch (IllegalArgumentException | IllegalStateException invalidProfile) { return 0; }
    }
    public Set<UUID> selectionBlockedEvents(Collection<UUID> ids) {
        Set<UUID> blocked = new HashSet<>();
        for (UUID id : ids) {
            if (store.latestForEvent(id).filter(campaign -> campaign.blocksSelection(id)).isPresent()) blocked.add(id);
        }
        return Set.copyOf(blocked);
    }

    private void requireSelectable(Collection<UUID> ids) {
        if (!selectionBlockedEvents(ids).isEmpty()) throw new IllegalStateException("LIVE_EVENT_ALREADY_IN_CAMPAIGN");
    }

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
        requireSelectable(current.manifest().targets().stream().map(Target::canonicalEventId).toList());
        Map<UUID, String> alreadyExcluded = locallyExcluded(current.manifest());
        if (alreadyExcluded.size() == current.manifest().targets().size())
            throw new IllegalArgumentException(alreadyExcluded.containsValue("STOPPED_ALREADY_POSTPONED")
                    ? "LIVE_ALL_EVENTS_INELIGIBLE" : "LIVE_ALL_EVENTS_FINISHED");
        if (!properties.isEnabled() || !provider.isEnabled() || !playwright.isEnabled())
            throw new IllegalStateException("LIVE_DISABLED");
        if (playwright.getRequestTimeout() == null || playwright.getRequestTimeout().isNegative()
                || playwright.getRequestTimeout().isZero()
                || playwright.getRequestTimeout().compareTo(Duration.ofSeconds(10)) > 0)
            throw new IllegalStateException("LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY");
        if (!clock.instant().isBefore(current.manifest().expiresAt())) throw new IllegalArgumentException("LIVE_MANIFEST_EXPIRED");
        String policyVersion = current.manifest().policyVersion();
        boolean grouped = groupedPolicy(policyVersion);
        if (!current.manifest().admissionProfile().equals(currentAdmissionProfile(policyVersion))
                || current.manifest().qualifiedMatchCapacity() != (grouped ? selectionMaximum(policyVersion) : properties.getQualifiedMatchCapacity())
                || !current.manifest().duration().equals(properties.getDuration()))
            throw new IllegalArgumentException("LIVE_PREPARED_POLICY_CHANGED");
        if ("live-v5".equals(policyVersion)) admission.admitV5(current.manifest().targets().size() - alreadyExcluded.size(),
                current.manifest().admissionProfile().groupedProfile());
        else if (grouped) admission.admitV4(current.manifest().targets().size() - alreadyExcluded.size(),
                current.manifest().admissionProfile().groupedProfile());
        else admission.admit(current.manifest().targets().size() - alreadyExcluded.size(), current.manifest().cycleInterval());
        if (providerCleanupRequired()) throw new IllegalStateException("LIVE_PROVIDER_CLEANUP_REQUIRED");
        Session session = new Session(current.manifest());
        session.alreadyExcluded.putAll(alreadyExcluded);
        if (!active.compareAndSet(null, session)) {
            Session existing = active.get();
            if (existing != null && existing.manifest.campaignId().equals(id)) return state(id);
            throw new IllegalStateException("LIVE_PROVIDER_BUSY");
        }
        Thread.ofPlatform().daemon(true).name("live-campaign-" + id).start(() -> run(session));
        try { session.launched.get(5, TimeUnit.SECONDS); }
        catch (Exception failure) {
            session.stopAll("STOPPED_ERROR");
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            if (failure instanceof ExecutionException && failure.getCause() instanceof IllegalStateException cause
                    && "LIVE_PROVIDER_CLEANUP_REQUIRED".equals(cause.getMessage())) throw cause;
            throw new IllegalStateException("LIVE_LAUNCH_FAILED");
        }
        return state(id);
    }

    private AdmissionProfile currentAdmissionProfile(String policyVersion) {
        return new AdmissionProfile(properties.getRequestEnvelope(), properties.getProcessingEnvelope(),
                properties.getQualificationSha256(), switch (policyVersion) {
                    case "live-v4" -> properties.groupedAdmissionProfile();
                    case "live-v5" -> properties.groupedAdmissionProfileV5();
                    default -> null;
                });
    }

    private static boolean groupedPolicy(String policyVersion) {
        return "live-v4".equals(policyVersion) || "live-v5".equals(policyVersion);
    }

    private boolean providerCleanupRequired() {
        Guard current = guard.snapshot();
        return current != null && "CLEANUP_REQUIRED".equals(current.state());
    }

    /** Durable local observation only. No process scan or cleanup is performed by a GET. */
    public Optional<UUID> providerCleanupCampaignId() {
        Guard current = guard.snapshot();
        return current != null && "CLEANUP_REQUIRED".equals(current.state())
                ? Optional.ofNullable(current.campaignId()) : Optional.empty();
    }

    public Optional<Guard> orphanCleanupGuard(UUID campaignId) {
        Session session = active.get();
        if (session != null) return Optional.empty();
        Guard current = guard.snapshot();
        if (current == null || !"CLEANUP_REQUIRED".equals(current.state()) || current.owner() == null
                || !campaignId.equals(current.campaignId())) return Optional.empty();
        CampaignView campaign = state(campaignId);
        return recoverableTerminal(campaign.state()) && current.ownership().equals(campaign.ownership())
                ? Optional.of(current) : Optional.empty();
    }

    /** Explicit local closure of an orphan, never a transfer/restart of its provider session. */
    public void finalizeInterruptedCleanup(UUID campaignId, long expectedGeneration) {
        if (expectedGeneration < 1) throw new IllegalStateException("LIVE_CLEANUP_STATE_CHANGED");
        try {
            coordinator.withExclusiveLocalCleanup(() -> {
                if (active.get() != null || supervisor.activeCampaignId().isPresent())
                    throw new IllegalStateException("LIVE_CLEANUP_BUSY");
                CampaignView campaign = state(campaignId);
                Guard current = guard.snapshot();
                if (current == null || current.generation() != expectedGeneration
                        || campaign.ownership() == null || campaign.ownership().generation() != expectedGeneration
                        || !recoverableTerminal(campaign.state()))
                    throw new IllegalStateException("LIVE_CLEANUP_STATE_CHANGED");
                // An acknowledged or response-lost successful closure is repeatable, but never
                // across a new acquisition/generation or without this campaign's durable trace.
                if ("FREE".equals(current.state()) && current.owner() == null && current.campaignId() == null
                        && campaign.transitions().stream().anyMatch(t -> "LOCAL_CLEANUP_VERIFIED".equals(t.state())))
                    return;
                if (!"CLEANUP_REQUIRED".equals(current.state()) || current.owner() == null
                        || !campaignId.equals(current.campaignId())
                        || !current.ownership().equals(campaign.ownership()))
                    throw new IllegalStateException("LIVE_CLEANUP_STATE_CHANGED");
                orphanProcesses.requireAbsent(current.owner(), playwright.getWorkerJar());
                if (active.get() != null || supervisor.activeCampaignId().isPresent())
                    throw new IllegalStateException("LIVE_CLEANUP_BUSY");
                // SQL takes the guard lock and compares its exact owner/generation before the
                // audit and release commit together. No store state is inferred from memory.
                store.completeOrphanCleanup(current, clock.instant());
            });
        } catch (ManualProviderRequestCoordinator.CoordinationException busy) {
            throw new IllegalStateException("LIVE_CLEANUP_BUSY");
        }
    }

    private static boolean recoverableTerminal(String state) {
        return state != null && (state.startsWith("STOPPED_")
                || "INTERRUPTED".equals(state) || "COMPLETED".equals(state));
    }

    /** Cancels only a durable preparation; provider opt-in, capacity and process ownership are irrelevant. */
    public void cancelPreparation(UUID campaignId, String manifestHash) {
        store.cancelPreparation(campaignId,manifestHash,clock.instant());
    }

    public void stop(UUID campaignId, UUID eventId) {
        Session s = active.get();
        if (s == null || !s.manifest.campaignId().equals(campaignId)) {
            if ("RUNNING".equals(state(campaignId).state())) throw new IllegalStateException("LIVE_CAMPAIGN_NOT_OWNED");
            return;
        }
        if (eventId != null && s.manifest.targets().stream().noneMatch(t -> t.canonicalEventId().equals(eventId)))
            throw new IllegalArgumentException("LIVE_EVENT_NOT_SELECTED");
        if (s.finished) {
            // One explicit, coalesced local command; the lease still belongs to its original thread.
            if (eventId == null) s.requestCleanup();
            return;
        }
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
            s.phase = LEASE_ACQUISITION;
            lease = coordinator.acquireLiveCampaign(s.manifest.campaignId());
            s.ownership = lease.ownership();
            s.phase = CAMPAIGN_LAUNCH;
            Launch started = store.launch(s.manifest.campaignId(), s.manifest.manifestSha256(), s.ownership, clock.instant());
            s.launchConfirmed = true;
            s.monotonicOrigin = System.nanoTime(); s.timeOrigin = started.startedAt();
            s.schedule = new LiveSchedule(s.manifest.targets().stream().map(Target::canonicalEventId).toList(),
                    started.startedAt(), started.endsAt(), s.manifest.cycleInterval(), s.manifest.policyVersion(), s.manifest.campaignId());
            // Recheck local observations after admission and acquisition, before any browser exists.
            s.phase = SELECTION_RECHECK;
            s.alreadyExcluded.putAll(locallyExcluded(s.manifest));
            s.alreadyExcluded.forEach(s.schedule::stopEvent);
            publishStates(s);
            s.launched.complete(null);
            if (s.stopReason != null || s.schedule.terminal()) return;
            startWatchdog(s);
            s.phase = TRANSPORT_OPEN;
            transport = new LiveProviderSession(factory, s.manifest.campaignId(), s.manifest.policyVersion());
            long lastWake = System.nanoTime(); Instant lastWall = clock.instant();
            while (!s.schedule.terminal() && s.stopReason == null) {
                Instant now = clock.instant();
                long monotonic = System.nanoTime();
                long wallDelta = Duration.between(lastWall, now).toMillis();
                long monotonicDelta = TimeUnit.NANOSECONDS.toMillis(monotonic - lastWake);
                if (monotonicDelta > 2500 || Math.abs(wallDelta - monotonicDelta) > 2000) {
                    s.stopAll("STOPPED_INTERRUPTED"); break;
                }
                s.phase = SCHEDULING;
                for (UUID stopped : s.stoppedEvents) s.schedule.stopEvent(stopped, "STOPPED_OPERATOR");
                var due = s.schedule.next(s.now());
                if (due.isPresent()) execute(s, transport, due.orElseThrow());
                publishStates(s);
                lastWake = System.nanoTime(); lastWall = clock.instant();
                if (due.isEmpty() || !groupedPolicy(s.manifest.policyVersion())) Thread.sleep(100);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); s.stopAll("STOPPED_INTERRUPTED");
        } catch (RuntimeException failure) {
            recordFirstFailure(s, failure);
            s.stopAll("STOPPED_ERROR");
            String code = "LIVE_LAUNCH_FAILED";
            if (s.ownership == null && failure instanceof ManualProviderRequestCoordinator.CoordinationException) {
                try { if (providerCleanupRequired()) code = "LIVE_PROVIDER_CLEANUP_REQUIRED"; }
                catch (RuntimeException ignored) { /* an unreadable guard is not evidence of cleanup state */ }
            }
            s.launched.completeExceptionally(new IllegalStateException(code));
        } finally {
            s.finished = true;
            if (s.schedule != null && s.stopReason != null) s.schedule.stopAll(s.stopReason);
            s.beginCleanup();
            boolean cleaned = cleanup(s, transport, lease);
            // Never return a thread-owned lease to an HTTP thread. A failed close leaves this
            // owner waiting without provider work, a scheduler, or timed SQL retries.
            boolean interrupted = Thread.interrupted();
            try {
                while (!cleaned && lease != null && s.awaitCleanupRequest())
                    cleaned = cleanup(s, transport, lease);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private boolean cleanup(Session s, LiveProviderSession transport,
                            ManualProviderRequestCoordinator.CampaignLease lease) {
        boolean absent = false, cleaned = false, exclusionRequested = false;
        LiveCampaignDiagnostic.Phase phase = CLEANUP_TRANSPORT_CLOSE;
        try {
            if (!s.transportClosed && transport != null) transport.close();
            phase = CLEANUP_TRANSPORT_ABSENCE;
            absent = supervisor.activeCampaignId().filter(s.manifest.campaignId()::equals).isEmpty();
            if (!absent) throw new IllegalStateException("LIVE_PROVIDER_CLEANUP_UNVERIFIED");
            s.transportClosed = true;
            if (s.ownership != null && !s.executionReconciled) {
                // This transaction locks the same guard row as launch. Its successful return
                // settles any earlier commit whose response was lost before PREPARED can be
                // used as evidence, and closes further dispatch admission for this owner.
                exclusionRequested = true;
                phase = CLEANUP_EXCLUSION;
                guard.requireCleanup(s.ownership, clock.instant());
                phase = CLEANUP_STATE_READ;
                CampaignView current = state(s.manifest.campaignId());
                if (!unlaunchedPreparation(s, current)) {
                    phase = CLEANUP_OWNERSHIP_CHECK;
                    if (!s.ownership.equals(current.ownership()))
                        throw new IllegalStateException("LIVE_CLEANUP_OWNERSHIP_CHANGED");
                    String terminal = s.stopReason != null ? s.stopReason
                            : s.schedule != null && s.schedule.globalStop() != null ? s.schedule.globalStop() : "COMPLETED";
                    phase = CLEANUP_SCHEDULE_PUBLICATION;
                    if (s.schedule != null) publishStates(s);
                    else {
                        // The launch may have committed before its response was lost. Its durable
                        // owner and events are authoritative even though no schedule was created.
                        for (EventView event : current.events()) {
                            publishState(s, new LiveSchedule.EventState(event.target().canonicalEventId(), terminal,
                                    null, null, event.missedCycles(), event.finalComplete()));
                        }
                    }
                    phase = CLEANUP_ATTEMPT_RECONCILIATION;
                    resolveUnpublishedAttempts(s, current);
                    phase = CLEANUP_TERMINAL_PUBLICATION;
                    store.transition(s.ownership, null, terminal, terminal, clock.instant(), null);
                }
                // A preparation that never launched (including a concurrent cancellation) has
                // no execution ledger to close. Preserve it and release only the acquired lease.
                s.executionReconciled = true;
            }
            if (lease != null) {
                phase = CLEANUP_LEASE_RELEASE;
                if (s.leaseCloseAttempted) lease.retryCloseAfterVerifiedCleanup();
                else { s.leaseCloseAttempted = true; lease.close(); }
            }
            cleaned = true;
            active.compareAndSet(s, null);
            return true;
        } catch (RuntimeException failure) {
            s.cleanupFailure = LiveCampaignDiagnostic.from(phase, failure, clock.instant());
            logFailure(s, "CLEANUP_FAILURE", s.cleanupFailure);
            if (!absent) {
                try { supervisor.stopCampaign(s.manifest.campaignId(), LiveProviderSession.ENDPOINTS); }
                catch (RuntimeException ignored) { /* unverifiable transport remains excluded */ }
            }
            if (s.ownership != null && !exclusionRequested) {
                try { guard.requireCleanup(s.ownership, clock.instant()); }
                catch (RuntimeException ignored) { /* SQL failure must never release durable exclusion */ }
            }
            if (lease == null) active.compareAndSet(s, null);
            return false;
        } finally {
            s.completeCleanup(cleaned);
        }
    }

    private boolean unlaunchedPreparation(Session s, CampaignView current) {
        boolean cancelled="STOPPED_OPERATOR".equals(current.state()) && "PREPARATION_CANCELLED".equals(current.reason());
        return !s.launchConfirmed && current.manifest().equals(s.manifest) && ("PREPARED".equals(current.state()) || cancelled)
                && current.ownership() == null && current.startedAt() == null && current.endsAt() == null
                && current.reservedCalls() == 0 && current.receivedBytes() == 0 && current.attempts().isEmpty()
                && current.events().stream().map(EventView::target).toList().equals(s.manifest.targets())
                && current.events().stream().allMatch(event -> (cancelled
                        ? "STOPPED_OPERATOR".equals(event.state()) && "PREPARATION_CANCELLED".equals(event.reason())
                        : "PREPARED".equals(event.state()))
                        && event.reservedCalls() == 0 && event.receivedBytes() == 0 && event.nextDueAt() == null);
    }

    private void resolveUnpublishedAttempts(Session s, CampaignView current) {
        for (AttemptView attempt : current.attempts()) {
            if (attempt.result() == null) {
                // Absence of a result proves neither transport success nor failure. Preserve any
                // existing receipt and append only local uncertainty, without parsing or replay.
                store.publishResult(s.ownership, attempt.attempt().attemptId(),
                        new Publication("UNKNOWN", "CAMPAIGN", "LOCAL_CLEANUP_UNRESOLVED",
                                clock.instant(), null, false, null), NormalizedReferences::none);
            }
        }
    }

    private void execute(Session s, LiveProviderSession transport, LiveSchedule.Due due) {
        s.phase = BUDGET_READ;
        DispatchBudget budget = store.dispatchBudget(s.ownership, due.eventId());
        int reservedRemaining = s.manifest.maximumCallsPerEvent() - budget.eventReservedCalls();
        if (!due.finalCycle() && (reservedRemaining <= 4 || s.manifest.maximumCalls() - budget.reservedCalls() <= 4)) {
            s.schedule.reserveFinalCheck(due.eventId(), s.now()); return;
        }
        s.phase = STORAGE_CHECK;
        admission.requireStorage(s.manifest.maximumBytes() - budget.receivedBytes());
        s.phase = ATTEMPT_RESERVATION;
        var request = new AttemptRequest(s.ownership, UUID.randomUUID(), due.eventId(), due.cycle(), due.endpoint(),
                due.kind(), due.dueAt(), clock.instant(), due.finalCycle(), due.groupId(), due.groupSequence(), due.groupOrdinal());
        var reservation = store.reserveAttempt(request);
        if (reservation.isEmpty()) {
            // The ledger protects four final calls for every active event, so its
            // ordinary budget may end while more than four global calls remain.
            // A refused attempt has consumed no call or group identity. V4 can
            // therefore use that event's final reserve without starving its peers.
            if (groupedPolicy(s.manifest.policyVersion()) && !due.finalCycle())
                s.schedule.reserveFinalCheck(due.eventId(), s.now());
            else s.schedule.stopEvent(due.eventId(), "STOPPED_LIMIT");
            return;
        }
        ReservedAttempt attempt = reservation.orElseThrow();
        try {
            var dispatchAdmission = new PlaywrightDispatchAdmission() {
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
            };
            s.phase = TRANSPORT;
            var response = due.groupId() == null ? transport.execute(attempt.providerEventId(), due.endpoint(), dispatchAdmission)
                    : transport.executeGrouped(attempt.providerEventId(), due.endpoint(),
                            new LiveProviderDispatchGroup(s.manifest.campaignId(), due.groupId(), attempt.providerEventId(),
                                    due.endpoint() == SofascoreEndpointType.EVENT_DETAILS ? LiveProviderDispatchGroup.Phase.CHECK
                                    : due.finalCycle() || "FINALIZING".equals(budget.eventState()) ? LiveProviderDispatchGroup.Phase.FINALIZING
                                    : "WAITING_START".equals(budget.eventState()) ? LiveProviderDispatchGroup.Phase.PREMATCH
                                    : LiveProviderDispatchGroup.Phase.IN_PLAY), dispatchAdmission);
            s.phase = RAW_SAVE;
            String parser = due.endpoint() == SofascoreEndpointType.EVENT_DETAILS ? "event-details-v3"
                    : due.endpoint() == SofascoreEndpointType.EVENT_INCIDENTS ? "event-incidents-v17"
                    : due.endpoint() == SofascoreEndpointType.EVENT_STATISTICS ? "event-statistics-v2" : "event-lineups-v2";
            RawManualCallSnapshot raw = new RawManualCallSnapshot(due.endpoint(), due.endpoint().name() + "|eventId=" + attempt.providerEventId(),
                    response.requestedAt(), response.receivedAt(), response.httpStatus(), response.contentType(), response.latency(),
                    response.payload(), parser, RawSnapshotSchemaStatus.RAW_ONLY, null);
            var receipt = store.saveReceipt(s.ownership, attempt.attemptId(), raw);
            s.phase = NORMALIZATION;
            LiveProcessedResponse processed = processor.process(CanonicalEventIdentity.sofascore(attempt.providerEventId()), due.endpoint(), response, receipt);
            s.phase = SCHEDULING;
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
            s.phase = RESULT_PUBLICATION;
            store.publishResult(s.ownership, attempt.attemptId(), publication, () -> processor.persistProcessed(processed));
        } catch (PlaywrightDispatchCancelledException cancelled) {
            s.phase = RESULT_PUBLICATION;
            store.publishResult(s.ownership, attempt.attemptId(), new Publication("NOT_DISPATCHED", "EVENT", "DISPATCH_CANCELLED",
                    clock.instant(), null, false, null), NormalizedReferences::none);
            if (s.schedule.mayDispatch(due, s.now()) && !s.stoppedEvents.contains(due.eventId())) s.stopAll("STOPPED_ERROR");
        } catch (RuntimeException failure) {
            // Capture before a failed FAILED publication or cleanup can obscure this cause.
            recordFirstFailure(s, failure);
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
        s.phase = SCHEDULE_PUBLICATION;
        for (var state : s.schedule.states()) publishState(s, state);
    }

    private void recordFirstFailure(Session s, RuntimeException failure) {
        if (s.firstFailure.get() != null) return;
        LiveCampaignDiagnostic diagnostic = LiveCampaignDiagnostic.from(s.phase, failure, clock.instant());
        if (s.firstFailure.compareAndSet(null, diagnostic)) logFailure(s, "INITIAL_FAILURE", diagnostic);
    }

    private static void logFailure(Session s, String kind, LiveCampaignDiagnostic diagnostic) {
        // Only bounded, server-owned values. Never pass the Throwable as a logging argument.
        LOG.warn("LIVE_CAMPAIGN_DIAGNOSTIC campaignId={} kind={} phase={} code={} occurredAt={}",
                s.manifest.campaignId(), kind, diagnostic.phase(), diagnostic.code(), diagnostic.occurredAt());
    }

    private void publishState(Session s, LiveSchedule.EventState state) {
        if (s.schedule != null && groupedPolicy(s.manifest.policyVersion())) {
            for (FamilySchedule family : s.schedule.familySchedules(state.eventId())) {
                String key = state.eventId() + ":" + family.endpoint();
                if (!family.equals(s.publishedFamilies.get(key))) {
                    store.updateFamilySchedule(s.ownership, state.eventId(), family, clock.instant());
                    s.publishedFamilies.put(key, family);
                }
            }
        }
        String previous = s.publishedStates.get(state.eventId());
        if (!state.state().equals(previous)) {
            store.transition(s.ownership, state.eventId(), state.state(), state.state(), clock.instant(), null);
            s.publishedStates.put(state.eventId(), state.state());
        }
        if (!state.equals(s.publishedMetrics.get(state.eventId()))) {
            store.updateScheduleMetrics(s.ownership, state.eventId(), state.nextDueAt(), state.missedCycles(), state.finalComplete(), clock.instant());
            s.publishedMetrics.put(state.eventId(), state);
        }
    }

    private void startWatchdog(Session s) {
        Thread.ofPlatform().daemon(true).name("live-session-watchdog").start(() -> {
            long before = System.nanoTime(); Instant wall = clock.instant();
            while (!s.finished && s.stopReason == null) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
                if (s.finished || s.stopReason != null) return;
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
    @PreDestroy public void shutdown() {
        Session s = active.get();
        if (s != null) {
            s.endCleanupWait();
            if (!s.finished) s.stopAll("STOPPED_INTERRUPTED");
            supervisor.stopCampaign(s.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
        }
    }
    private static final class Session {
        final Manifest manifest; final CompletableFuture<Void> launched = new CompletableFuture<>();
        final Map<UUID, String> alreadyExcluded = new LinkedHashMap<>();
        final ReentrantLock dispatchLock = new ReentrantLock(); final Set<UUID> stoppedEvents = ConcurrentHashMap.newKeySet();
        final Map<UUID,String> publishedStates = new HashMap<>(); final Map<UUID,LiveSchedule.EventState> publishedMetrics = new HashMap<>();
        final Map<String,FamilySchedule> publishedFamilies = new HashMap<>();
        volatile String stopReason; volatile LiveSchedule schedule; volatile Ownership ownership; volatile boolean finished;
        volatile LiveCampaignDiagnostic.Phase phase = LEASE_ACQUISITION;
        final AtomicReference<LiveCampaignDiagnostic> firstFailure = new AtomicReference<>();
        volatile LiveCampaignDiagnostic cleanupFailure;
        volatile boolean cleanupPending, cleanupInProgress;
        final Object cleanupMonitor = new Object();
        boolean cleanupRequested, shuttingDown, transportClosed, executionReconciled, leaseCloseAttempted, launchConfirmed;
        volatile long monotonicOrigin; volatile Instant timeOrigin;
        Session(Manifest manifest) { this.manifest = manifest; }
        Instant now() { return timeOrigin.plusNanos(System.nanoTime() - monotonicOrigin); }
        void stopAll(String reason) { dispatchLock.lock(); try { stopReason = reason; if (schedule != null) schedule.stopAll(reason); } finally { dispatchLock.unlock(); } }
        void beginCleanup() { synchronized (cleanupMonitor) { cleanupInProgress = true; } }
        void completeCleanup(boolean cleaned) {
            synchronized (cleanupMonitor) { cleanupPending = !cleaned; cleanupInProgress = false; }
        }
        void requestCleanup() {
            synchronized (cleanupMonitor) {
                if (cleanupPending && !cleanupInProgress && !shuttingDown) {
                    cleanupRequested = true;
                    cleanupMonitor.notifyAll();
                }
            }
        }
        boolean awaitCleanupRequest() {
            synchronized (cleanupMonitor) {
                while (!cleanupRequested && !shuttingDown) {
                    try { cleanupMonitor.wait(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
                }
                if (shuttingDown) return false;
                cleanupRequested = false;
                cleanupInProgress = true;
                return true;
            }
        }
        void endCleanupWait() { synchronized (cleanupMonitor) { shuttingDown = true; cleanupMonitor.notifyAll(); } }
    }
}
