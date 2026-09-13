package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable exclusion complements the thread-affine in-process provider lease. No time-based takeover. */
public interface ProviderCampaignGuardStore {
    Guard snapshot();
    Optional<Guard> tryAcquire(UUID campaignId, Owner owner, Instant at);
    boolean isOwned(Ownership ownership);
    void requireCleanup(Ownership ownership, Instant at);
    /** Only invoke after supervisor/process-identity cleanup has been verified. */
    void releaseAfterVerifiedCleanup(Ownership ownership, Instant at);
    /**
     * Releases only the exact non-live manual orphan proved absent by the caller. The expected
     * guard is compared atomically, so a stale form can never free a newer acquisition.
     */
    void releaseManualOrphanAfterVerifiedCleanup(Guard expected, Instant at);
}
