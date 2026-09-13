package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface LiveCampaignStore {
    Manifest prepare(Manifest manifest);
    /** Local-only cancellation; serialized with launch and idempotent for the same manifest. */
    void cancelPreparation(UUID campaignId, String expectedManifestSha256, Instant cancelledAt);
    Optional<CampaignView> find(UUID campaignId);
    /** Production implementations read current counters without materializing the campaign ledger. */
    default DispatchBudget dispatchBudget(Ownership ownership, UUID canonicalEventId) {
        CampaignView campaign = find(ownership.campaignId()).orElseThrow();
        if (!ownership.equals(campaign.ownership())) throw new IllegalStateException("live provider ownership is stale or closed");
        EventView event = campaign.events().stream().filter(value -> value.target().canonicalEventId().equals(canonicalEventId))
                .findFirst().orElseThrow();
        return new DispatchBudget(campaign.reservedCalls(), campaign.receivedBytes(), event.reservedCalls(), event.state());
    }
    List<CampaignView> findRecent(int limit);
    Optional<CampaignView> latestForEvent(UUID canonicalEventId);
    Launch launch(UUID campaignId, String expectedManifestSha256, Ownership ownership, Instant startedAt);
    Optional<ReservedAttempt> reserveAttempt(AttemptRequest request);
    void recordDispatch(Ownership ownership, UUID attemptId, Instant authorizedAt);
    RawSnapshotPersistenceResult saveReceipt(Ownership ownership, UUID attemptId, RawManualCallSnapshot raw);
    /** Parsing precedes this method; callback performs local normalized persistence only. */
    Result publishResult(Ownership ownership, UUID attemptId, Publication publication,
                         Supplier<NormalizedReferences> persistNormalized);
    void transition(Ownership ownership, UUID canonicalEventId, String state, String reason,
                    Instant at, UUID attemptId);
    void updateNextDueAt(Ownership ownership, UUID canonicalEventId, Instant nextDueAt, Instant at);
    void updateScheduleMetrics(Ownership ownership, UUID canonicalEventId, Instant nextDueAt,
                               long missedCycles, boolean finalComplete, Instant at);
    /** Scheduler-owned projection, independent of successful publication; never a new collection trigger. */
    default void updateFamilySchedule(Ownership ownership, UUID canonicalEventId, FamilySchedule schedule, Instant at) {
        throw new UnsupportedOperationException("family schedules require the V39 store");
    }
    /** Caller first proves the old owner is absent and holds exclusive recovery authority. Never starts transport. */
    void interruptOrphan(Ownership ownership, Instant at, String reason);
    /**
     * Caller first proves the old owner and provider processes are absent, under local recovery exclusion.
     * Atomically appends cleanup evidence and releases only the exact terminal orphan's guard.
     * Repetition is accepted only with the same guard evidence and without a newer acquisition.
     */
    void completeOrphanCleanup(Guard expectedGuard, Instant verifiedAt);
    /**
     * Caller first proves the former owner and provider processes absent under local recovery
     * exclusion. The campaign remains an untouched, non-launched preparation; this method only
     * atomically records that proof and frees the exact guard which was acquired before launch.
     */
    void completePreLaunchOrphanCleanup(Guard expectedGuard, Instant verifiedAt);
}
