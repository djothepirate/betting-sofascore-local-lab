package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;
import java.time.Instant;
import java.util.UUID;

/** Shared durable admission for every real J3/J4/J5 departure; no network or implicit rearm. */
public interface ProviderResilienceStore {
    Snapshot snapshot();
    /** Read only, never reserves. An eligible result does not authorize sending. */
    DepartureDecision departureDecision(Instant at);
    /** Atomic reservation just before sending. A reserved UUID cannot authorize another send. */
    DepartureDecision tryReserveDeparture(UUID dispatchId, Instant at);
    /** Close only after return/failure and verified transport cleanup. An unresolved reservation blocks all departures. */
    Snapshot markDepartureFinished(UUID dispatchId, Instant at);
    /** Publish confirmed response status as soon as known, independently of body completion. */
    Snapshot suspend(UUID evidenceId, UUID campaignId, int httpStatus, Instant observedAt, Instant retryNotBefore);
    /** Explicit operator command; compare version and respect Retry-After, preserving all pressure/history. */
    Snapshot rearm(long expectedVersion, Instant at);
}
