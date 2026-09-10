package com.bettingproject.sofascorelocal.domain.live;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

/** Durable live evidence. Provider status, collection outcome and orchestration state are separate. */
public final class LiveCampaignData {
    private LiveCampaignData() { }

    public record Target(UUID canonicalEventId, long providerEventId,
                         long sourceObservationId, long sourceSnapshotId) {
        public Target {
            Objects.requireNonNull(canonicalEventId);
            if (!CanonicalEventIdentity.sofascore(providerEventId).value().equals(canonicalEventId)
                    || sourceObservationId < 1 || sourceSnapshotId < 1) {
                throw new IllegalArgumentException("live target identity/provenance is invalid");
            }
        }
    }

    public record EndpointEnvelope(Duration requestEnvelope, Duration processingEnvelope) {
        public EndpointEnvelope {
            Objects.requireNonNull(requestEnvelope); Objects.requireNonNull(processingEnvelope);
            if (requestEnvelope.isNegative() || requestEnvelope.isZero()
                    || requestEnvelope.compareTo(Duration.ofSeconds(10)) > 0
                    || processingEnvelope.isNegative() || processingEnvelope.compareTo(Duration.ofMinutes(1)) > 0)
                throw new IllegalArgumentException("invalid grouped endpoint envelope");
        }
        public Duration exchangeEnvelope() { return requestEnvelope.plus(processingEnvelope); }
    }
    /** Separate evidence for the grouped transport; an old per-call proof cannot qualify this policy. */
    public record GroupedAdmissionProfile(Map<SofascoreEndpointType, EndpointEnvelope> endpointEnvelopes,
                                          String qualificationSha256, String policyVersion) {
        public GroupedAdmissionProfile {
            requireHash(qualificationSha256);
            if (!"live-v4".equals(policyVersion) && !"live-v5".equals(policyVersion) && !"live-v6".equals(policyVersion)
                    && !"live-v7".equals(policyVersion) && !"live-v8".equals(policyVersion))
                throw new IllegalArgumentException("unknown grouped live policy");
            EnumMap<SofascoreEndpointType, EndpointEnvelope> copy = new EnumMap<>(SofascoreEndpointType.class);
            copy.putAll(Objects.requireNonNull(endpointEnvelopes));
            if (copy.size() != 4) throw new IllegalArgumentException("four grouped endpoint envelopes required");
            copy.forEach((endpoint, envelope) -> { requireEndpoint(endpoint); Objects.requireNonNull(envelope); });
            endpointEnvelopes = Collections.unmodifiableMap(copy);
        }
        public GroupedAdmissionProfile(Map<SofascoreEndpointType, EndpointEnvelope> endpointEnvelopes,
                                       String qualificationSha256) {
            this(endpointEnvelopes, qualificationSha256, "live-v4");
        }
        public EndpointEnvelope envelope(SofascoreEndpointType endpoint) { requireEndpoint(endpoint); return endpointEnvelopes.get(endpoint); }
        public Duration criticalInterval() {
            return Duration.ofSeconds("live-v4".equals(policyVersion) || "live-v7".equals(policyVersion)
                    || "live-v8".equals(policyVersion) ? 60 : 100);
        }
        public Duration lineupInterval() {
            return Duration.ofSeconds("live-v7".equals(policyVersion) || "live-v8".equals(policyVersion) ? 60 : 300);
        }
        public Duration intraGroupDelay() { return Duration.ZERO; }
        public Duration minimumRequestStartInterval() {
            return "live-v8".equals(policyVersion) ? Duration.ofMillis(500)
                    : Duration.ofSeconds("live-v6".equals(policyVersion) || "live-v7".equals(policyVersion) ? 2 : 0);
        }
        public Duration interGroupDelay() {
            return "live-v8".equals(policyVersion) ? Duration.ofMillis(500)
                    : Duration.ofSeconds(!"live-v4".equals(policyVersion) ? 1 : 3);
        }
        public double maximumUtilization() { return 0.9d; }
    }

    public record AdmissionProfile(Duration requestEnvelope, Duration processingEnvelope,
                                   String qualificationSha256, GroupedAdmissionProfile groupedProfile) {
        public AdmissionProfile {
            Objects.requireNonNull(requestEnvelope); Objects.requireNonNull(processingEnvelope);
            Objects.requireNonNull(qualificationSha256);
            if (requestEnvelope.isNegative() || requestEnvelope.isZero()
                    || requestEnvelope.compareTo(Duration.ofSeconds(10)) > 0
                    || processingEnvelope.isNegative() || processingEnvelope.compareTo(Duration.ofMinutes(1)) > 0
                    || (!qualificationSha256.isEmpty() && !qualificationSha256.matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("invalid live admission profile");
        }
        public AdmissionProfile(Duration requestEnvelope, Duration processingEnvelope, String qualificationSha256) {
            this(requestEnvelope, processingEnvelope, qualificationSha256, null);
        }
        public static AdmissionProfile conservative() {
            return new AdmissionProfile(Duration.ofSeconds(10), Duration.ofSeconds(1), "");
        }
    }

    public record Manifest(UUID campaignId, String manifestSha256, String policyVersion,
                           Instant preparedAt, Instant expiresAt, Duration duration,
                           int maximumCallsPerEvent, int maximumCalls, long maximumBytes,
                           int qualifiedMatchCapacity, List<Target> targets, AdmissionProfile admissionProfile,
                           Duration cycleInterval) {
        public Manifest {
            Objects.requireNonNull(campaignId); requireHash(manifestSha256);
            requireCode(policyVersion); Objects.requireNonNull(preparedAt);
            Objects.requireNonNull(expiresAt); Objects.requireNonNull(duration);
            Objects.requireNonNull(admissionProfile);
            LiveCadence.validate(cycleInterval);
            preparedAt = preparedAt.truncatedTo(ChronoUnit.MICROS);
            expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
            targets = List.copyOf(targets);
            if (!expiresAt.isAfter(preparedAt)
                    || Duration.between(preparedAt, expiresAt).compareTo(Duration.ofMinutes(5)) > 0
                    || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(4)) > 0
                    || duration.toSeconds() < 1 || duration.getNano() != 0 || maximumCallsPerEvent < 4
                    || maximumCallsPerEvent > (("live-v5".equals(policyVersion) || "live-v6".equals(policyVersion)
                        || "live-v7".equals(policyVersion) || "live-v8".equals(policyVersion)) ? 2500 : 1000)
                    || maximumCalls < 4 || maximumCalls > (("live-v5".equals(policyVersion) || "live-v6".equals(policyVersion)
                        || "live-v7".equals(policyVersion) || "live-v8".equals(policyVersion)) ? 20000 : 3000) || maximumBytes < 1
                    || qualifiedMatchCapacity < 1
                    || targets.isEmpty() || targets.size() > qualifiedMatchCapacity
                    || targets.size() > LiveCadence.MAXIMUM_SELECTION_SIZE
                    || (("live-v2".equals(policyVersion) || "live-v3".equals(policyVersion))
                        && !cycleInterval.equals(LiveCadence.forMatches(targets.size())))
                    || (("live-v4".equals(policyVersion) || "live-v5".equals(policyVersion) || "live-v6".equals(policyVersion)
                        || "live-v7".equals(policyVersion) || "live-v8".equals(policyVersion))
                        && (admissionProfile.groupedProfile() == null
                        || !policyVersion.equals(admissionProfile.groupedProfile().policyVersion())
                        || !cycleInterval.equals(admissionProfile.groupedProfile().criticalInterval())))
                    || ("live-v5".equals(policyVersion) && (targets.size() > 20 || qualifiedMatchCapacity > 20
                        || maximumBytes > 15_728_640_000L))
                    || ("live-v6".equals(policyVersion) && (targets.size() > 7 || qualifiedMatchCapacity > 7 || maximumBytes > 15_728_640_000L))
                    || ("live-v7".equals(policyVersion) && (targets.size() > 3 || qualifiedMatchCapacity > 3 || maximumBytes > 15_728_640_000L))
                    || ("live-v8".equals(policyVersion) && (targets.size() > 10 || qualifiedMatchCapacity > 10 || maximumBytes > 15_728_640_000L))
                    || maximumCalls < 4 * targets.size()
                    || targets.stream().map(Target::canonicalEventId).distinct().count() != targets.size()) {
                throw new IllegalArgumentException("live manifest is outside accepted bounds");
            }
        }
        /** Pre-adaptive manifests retain their original one-minute schedule. */
        public Manifest(UUID campaignId, String manifestSha256, String policyVersion,
                        Instant preparedAt, Instant expiresAt, Duration duration,
                        int maximumCallsPerEvent, int maximumCalls, long maximumBytes,
                        int qualifiedMatchCapacity, List<Target> targets, AdmissionProfile admissionProfile) {
            this(campaignId, manifestSha256, policyVersion, preparedAt, expiresAt, duration,
                    maximumCallsPerEvent, maximumCalls, maximumBytes, qualifiedMatchCapacity, targets,
                    admissionProfile, Duration.ofSeconds(60));
        }
        public Manifest(UUID campaignId, String manifestSha256, String policyVersion,
                        Instant preparedAt, Instant expiresAt, Duration duration,
                        int maximumCallsPerEvent, int maximumCalls, long maximumBytes,
                        int qualifiedMatchCapacity, List<Target> targets) {
            this(campaignId, manifestSha256, policyVersion, preparedAt, expiresAt, duration,
                    maximumCallsPerEvent, maximumCalls, maximumBytes, qualifiedMatchCapacity, targets,
                    AdmissionProfile.conservative());
        }
    }

    public record Owner(UUID instanceId, long processId, Instant processStartedAt) {
        public Owner { Objects.requireNonNull(instanceId); Objects.requireNonNull(processStartedAt);
            if (processId < 1) throw new IllegalArgumentException("process id must be positive"); }
    }
    public record Ownership(UUID campaignId, UUID instanceId, long generation) {
        public Ownership { Objects.requireNonNull(campaignId); Objects.requireNonNull(instanceId);
            if (generation < 1) throw new IllegalArgumentException("generation must be positive"); }
    }
    public record Guard(String state, UUID campaignId, Owner owner, long generation, Instant changedAt) {
        public Ownership ownership() {
            if (campaignId == null || owner == null) throw new IllegalStateException("guard is free");
            return new Ownership(campaignId, owner.instanceId(), generation);
        }
    }
    public record Launch(Ownership ownership, Instant startedAt, Instant endsAt, boolean newlyLaunched) { }

    public record AttemptRequest(Ownership ownership, UUID attemptId, UUID canonicalEventId,
                                 long cycleNumber, SofascoreEndpointType endpoint, String kind,
                                 Instant dueAt, Instant reservedAt, boolean finalCycle,
                                 UUID groupId, long groupSequence, int groupOrdinal) {
        public AttemptRequest { Objects.requireNonNull(ownership); Objects.requireNonNull(attemptId);
            Objects.requireNonNull(canonicalEventId); requireEndpoint(endpoint); requireCode(kind);
            Objects.requireNonNull(dueAt); Objects.requireNonNull(reservedAt);
            dueAt = dueAt.truncatedTo(ChronoUnit.MICROS); reservedAt = reservedAt.truncatedTo(ChronoUnit.MICROS);
            if (cycleNumber < 0) throw new IllegalArgumentException("negative cycle");
            requireGroup(groupId, groupSequence, groupOrdinal); }
        public AttemptRequest(Ownership ownership, UUID attemptId, UUID canonicalEventId, long cycleNumber,
                              SofascoreEndpointType endpoint, String kind, Instant dueAt, Instant reservedAt, boolean finalCycle) {
            this(ownership, attemptId, canonicalEventId, cycleNumber, endpoint, kind, dueAt, reservedAt, finalCycle, null, -1, -1);
        }
    }
    public record ReservedAttempt(UUID attemptId, UUID canonicalEventId, long providerEventId,
                                  SofascoreEndpointType endpoint, long cycleNumber, String kind,
                                  Instant dueAt, Instant reservedAt, boolean finalCycle,
                                  UUID groupId, long groupSequence, int groupOrdinal) {
        public ReservedAttempt { requireGroup(groupId, groupSequence, groupOrdinal); }
        public ReservedAttempt(UUID attemptId, UUID canonicalEventId, long providerEventId, SofascoreEndpointType endpoint,
                               long cycleNumber, String kind, Instant dueAt, Instant reservedAt, boolean finalCycle) {
            this(attemptId, canonicalEventId, providerEventId, endpoint, cycleNumber, kind, dueAt, reservedAt, finalCycle, null, -1, -1);
        }
    }

    /** IDs returned by existing normalized stores inside the publication transaction. */
    public record NormalizedReferences(Long canonicalObservationId, Long detailObservationId,
                                       Long j5ObservationId, String normalizedSha256) {
        public NormalizedReferences { if (normalizedSha256 != null) requireHash(normalizedSha256);
            if ((canonicalObservationId != null && canonicalObservationId < 1)
                    || (detailObservationId != null && detailObservationId < 1)
                    || (j5ObservationId != null && j5ObservationId < 1))
                throw new IllegalArgumentException("observation ids must be positive"); }
        public static NormalizedReferences none() { return new NormalizedReferences(null, null, null, null); }
    }
    public record Publication(String outcome, String scope, String code, Instant resolvedAt,
                              String parserVersion, boolean successful, String nextEventState,
                              String sportStatus, String projectionJson, String projectionVersion,
                              String completenessStatus, Integer completenessScore) {
        public Publication { requireCode(outcome); requireCode(scope); Objects.requireNonNull(resolvedAt);
            resolvedAt = resolvedAt.truncatedTo(ChronoUnit.MICROS);
            if (code != null) requireCode(code); if (parserVersion != null) requireCode(parserVersion);
            if (nextEventState != null) requireCode(nextEventState);
            if (sportStatus != null && (sportStatus.isBlank() || sportStatus.length() > 64
                    || sportStatus.chars().anyMatch(Character::isISOControl)))
                throw new IllegalArgumentException("invalid provider sport status");
            if (projectionVersion != null) requireCode(projectionVersion);
            if (completenessStatus != null) requireCode(completenessStatus);
            if ((projectionJson == null) != (projectionVersion == null)
                    || (projectionJson != null && projectionJson.length() > 262144)
                    || (completenessScore != null && (completenessScore < 0 || completenessScore > 100)))
                throw new IllegalArgumentException("invalid live projection"); }
        public Publication(String outcome, String scope, String code, Instant resolvedAt,
                           String parserVersion, boolean successful, String nextEventState) {
            this(outcome, scope, code, resolvedAt, parserVersion, successful, nextEventState,
                    null, null, null, null, null);
        }
    }
    public record Result(UUID attemptId, Publication publication, NormalizedReferences normalized) { }
    public record FamilySchedule(SofascoreEndpointType endpoint, Instant nextDueAt, long intervalSeconds, long missedCycles) {
        public FamilySchedule {
            requireEndpoint(endpoint);
            if (intervalSeconds < 1 || intervalSeconds > 3600 || missedCycles < 0)
                throw new IllegalArgumentException("invalid live family schedule");
            if (nextDueAt != null) nextDueAt = nextDueAt.truncatedTo(ChronoUnit.MICROS);
        }
    }
    public record FamilyCursor(SofascoreEndpointType endpoint, UUID lastAttemptId,
                               UUID lastReceivedAttemptId, UUID lastSuccessfulAttemptId,
                               UUID lastChangedAttemptId, Instant lastReceivedAt, Instant lastSuccessfulAt,
                               Instant lastChangedAt, NormalizedReferences normalized,
                               Result latestResult, Result latestSuccessfulResult, FamilySchedule schedule) {
        public FamilyCursor(SofascoreEndpointType endpoint, UUID lastAttemptId, UUID lastReceivedAttemptId,
                            UUID lastSuccessfulAttemptId, UUID lastChangedAttemptId, Instant lastReceivedAt,
                            Instant lastSuccessfulAt, Instant lastChangedAt, NormalizedReferences normalized,
                            Result latestResult, Result latestSuccessfulResult) {
            this(endpoint, lastAttemptId, lastReceivedAttemptId, lastSuccessfulAttemptId, lastChangedAttemptId,
                    lastReceivedAt, lastSuccessfulAt, lastChangedAt, normalized, latestResult, latestSuccessfulResult, null);
        }
    }
    public record EventView(Target target, String state, String reason, int reservedCalls,
                            long receivedBytes, Instant nextDueAt, long missedCycles,
                            boolean finalComplete, List<FamilyCursor> families) {
        public EventView(Target target,String state,String reason,int reservedCalls,long receivedBytes,
                         Instant nextDueAt,List<FamilyCursor> families) {
            this(target,state,reason,reservedCalls,receivedBytes,nextDueAt,0,false,families);
        }
    }
    /** Bounded operational read; ledger history is not needed to authorize the next reservation. */
    public record DispatchBudget(int reservedCalls, long receivedBytes, int eventReservedCalls, String eventState) {
        public DispatchBudget {
            if (reservedCalls < 0 || receivedBytes < 0 || eventReservedCalls < 0)
                throw new IllegalArgumentException("negative live dispatch counters");
            requireCode(eventState);
        }
    }
    public record AttemptView(ReservedAttempt attempt, Instant dispatchAuthorizedAt,
                              Long snapshotId, Long occurrenceId, Instant receivedAt, Result result) { }
    public record Transition(long revision, UUID canonicalEventId, String state, String reason,
                             Instant changedAt, UUID attemptId) { }
    public record CampaignView(Manifest manifest, String state, String reason, Instant startedAt,
                               Instant endsAt, int reservedCalls, long receivedBytes, long revision,
                               Ownership ownership, List<EventView> events, List<AttemptView> attempts,
                               List<Transition> transitions) {
        public boolean blocksSelection(UUID canonicalEventId) {
            return ("RUNNING".equals(state) || "CLEANUP_REQUIRED".equals(state))
                    && events.stream().anyMatch(event -> event.target().canonicalEventId().equals(canonicalEventId)
                            && !"STOPPED_ERROR".equals(event.state()));
        }
    }

    public static void requireEndpoint(SofascoreEndpointType endpoint) {
        Objects.requireNonNull(endpoint);
        if (endpoint != SofascoreEndpointType.EVENT_DETAILS && endpoint != SofascoreEndpointType.EVENT_STATISTICS
                && endpoint != SofascoreEndpointType.EVENT_INCIDENTS && endpoint != SofascoreEndpointType.EVENT_LINEUPS)
            throw new IllegalArgumentException("endpoint outside live scope");
    }
    private static void requireGroup(UUID groupId, long sequence, int ordinal) {
        if (groupId == null ? sequence != -1 || ordinal != -1 : sequence < 0 || ordinal < 0 || ordinal > 3)
            throw new IllegalArgumentException("invalid live group reference");
    }
    public static void requireCode(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,96}"))
            throw new IllegalArgumentException("unsafe live code");
    }
    public static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid SHA-256");
    }
}
