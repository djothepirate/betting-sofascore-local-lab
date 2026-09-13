package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Ownership;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** An admitted J3 order borrows emission authority, never the live lease or its generation. */
public interface J3LivePauseStore {
    record MissedSlot(UUID eventId, String endpoint, Instant dueAt, long count) { }
    record Pause(UUID runId, UUID campaignId, long generation, String phase, Instant requestedAt,
                 Instant deadline, Instant changedAt, String reason) { }
    void request(UUID runId, Ownership owner, Instant now, Instant deadline);
    void transition(UUID runId, Ownership owner, String expectedPhase, String phase,
                    Instant now, String reason, List<MissedSlot> missed);
    Optional<Pause> latest(UUID campaignId);
    Optional<Pause> forRun(UUID runId);
}
