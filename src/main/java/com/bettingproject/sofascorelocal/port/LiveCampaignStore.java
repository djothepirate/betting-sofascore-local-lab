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
    Optional<CampaignView> find(UUID campaignId);
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
    /** Caller first proves the old owner is absent and holds exclusive recovery authority. Never starts transport. */
    void interruptOrphan(Ownership ownership, Instant at, String reason);
}
