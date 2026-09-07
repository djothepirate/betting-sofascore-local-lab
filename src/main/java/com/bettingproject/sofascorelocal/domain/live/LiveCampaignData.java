package com.bettingproject.sofascorelocal.domain.live;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    public record AdmissionProfile(Duration requestEnvelope, Duration processingEnvelope,
                                   String qualificationSha256) {
        public AdmissionProfile {
            Objects.requireNonNull(requestEnvelope); Objects.requireNonNull(processingEnvelope);
            Objects.requireNonNull(qualificationSha256);
            if (requestEnvelope.isNegative() || requestEnvelope.isZero()
                    || requestEnvelope.compareTo(Duration.ofSeconds(10)) > 0
                    || processingEnvelope.isNegative() || processingEnvelope.compareTo(Duration.ofMinutes(1)) > 0
                    || (!qualificationSha256.isEmpty() && !qualificationSha256.matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("invalid live admission profile");
        }
        public static AdmissionProfile conservative() {
            return new AdmissionProfile(Duration.ofSeconds(10), Duration.ofSeconds(1), "");
        }
    }

    public record Manifest(UUID campaignId, String manifestSha256, String policyVersion,
                           Instant preparedAt, Instant expiresAt, Duration duration,
                           int maximumCallsPerEvent, int maximumCalls, long maximumBytes,
                           int qualifiedMatchCapacity, List<Target> targets, AdmissionProfile admissionProfile) {
        public Manifest {
            Objects.requireNonNull(campaignId); requireHash(manifestSha256);
            requireCode(policyVersion); Objects.requireNonNull(preparedAt);
            Objects.requireNonNull(expiresAt); Objects.requireNonNull(duration);
            Objects.requireNonNull(admissionProfile);
            preparedAt = preparedAt.truncatedTo(ChronoUnit.MICROS);
            expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
            targets = List.copyOf(targets);
            if (!expiresAt.isAfter(preparedAt)
                    || Duration.between(preparedAt, expiresAt).compareTo(Duration.ofMinutes(5)) > 0
                    || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(4)) > 0
                    || duration.toSeconds() < 1 || duration.getNano() != 0 || maximumCallsPerEvent < 4 || maximumCallsPerEvent > 1000
                    || maximumCalls < 4 || maximumCalls > 3000 || maximumBytes < 1
                    || qualifiedMatchCapacity < 1 || qualifiedMatchCapacity > 3
                    || targets.isEmpty() || targets.size() > qualifiedMatchCapacity
                    || maximumCalls < 4 * targets.size()
                    || targets.stream().map(Target::canonicalEventId).distinct().count() != targets.size()) {
                throw new IllegalArgumentException("live manifest is outside accepted bounds");
            }
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
                                 Instant dueAt, Instant reservedAt, boolean finalCycle) {
        public AttemptRequest { Objects.requireNonNull(ownership); Objects.requireNonNull(attemptId);
            Objects.requireNonNull(canonicalEventId); requireEndpoint(endpoint); requireCode(kind);
            Objects.requireNonNull(dueAt); Objects.requireNonNull(reservedAt);
            dueAt = dueAt.truncatedTo(ChronoUnit.MICROS); reservedAt = reservedAt.truncatedTo(ChronoUnit.MICROS);
            if (cycleNumber < 0) throw new IllegalArgumentException("negative cycle"); }
    }
    public record ReservedAttempt(UUID attemptId, UUID canonicalEventId, long providerEventId,
                                  SofascoreEndpointType endpoint, long cycleNumber, String kind,
                                  Instant dueAt, Instant reservedAt, boolean finalCycle) { }

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
    public record FamilyCursor(SofascoreEndpointType endpoint, UUID lastAttemptId,
                               UUID lastReceivedAttemptId, UUID lastSuccessfulAttemptId,
                               UUID lastChangedAttemptId, Instant lastReceivedAt, Instant lastSuccessfulAt,
                               Instant lastChangedAt, NormalizedReferences normalized,
                               Result latestResult, Result latestSuccessfulResult) { }
    public record EventView(Target target, String state, String reason, int reservedCalls,
                            long receivedBytes, Instant nextDueAt, long missedCycles,
                            boolean finalComplete, List<FamilyCursor> families) {
        public EventView(Target target,String state,String reason,int reservedCalls,long receivedBytes,
                         Instant nextDueAt,List<FamilyCursor> families) {
            this(target,state,reason,reservedCalls,receivedBytes,nextDueAt,0,false,families);
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
    public static void requireCode(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,96}"))
            throw new IllegalArgumentException("unsafe live code");
    }
    public static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid SHA-256");
    }
}
