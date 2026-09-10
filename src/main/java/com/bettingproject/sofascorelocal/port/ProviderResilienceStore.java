package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Shared durable admission for every real J3/J4/J5 departure; no network or implicit rearm. */
public interface ProviderResilienceStore {
    Snapshot snapshot();
    /** Read only, never reserves. An eligible result does not authorize sending. */
    DepartureDecision departureDecision(Instant at);
    /**
     * Read-only preview for an explicit durable pressure profile. Existing
     * implementations remain V1-only until they explicitly support the profile.
     */
    default DepartureDecision departureDecision(DepartureProfile profile, Instant at) {
        if (Objects.requireNonNull(profile) != DepartureProfile.LEGACY_V1)
            throw new IllegalStateException("PROVIDER_DEPARTURE_PROFILE_UNSUPPORTED");
        return departureDecision(at);
    }
    /**
     * Read-only admission for an initial wave that must fit as a whole inside
     * the shared durable pressure windows. It never reserves a departure.
     *
     * <p>An implementation which has not explicitly implemented multi-slot
     * accounting must fail closed. Treating a single-departure preview as a
     * capacity proof would let a new V8 campaign advertise freshness that the
     * already-accounted shared traffic cannot provide.</p>
     */
    default DepartureDecision departureCapacityDecision(DepartureProfile profile, int requiredDepartures, Instant at) {
        Objects.requireNonNull(profile); Objects.requireNonNull(at);
        if (requiredDepartures < 1) throw new IllegalArgumentException("PROVIDER_DEPARTURE_CAPACITY_INVALID");
        if (requiredDepartures != 1) throw new IllegalStateException("PROVIDER_DEPARTURE_CAPACITY_UNSUPPORTED");
        return departureDecision(profile, at);
    }
    /** Atomic reservation just before sending. A reserved UUID cannot authorize another send. */
    DepartureDecision tryReserveDeparture(UUID dispatchId, Instant at);
    /**
     * Atomic reservation for an explicit durable pressure profile. Legacy callers
     * retain the V1 overload; a non-upgraded store fails closed for live-v8.
     */
    default DepartureDecision tryReserveDeparture(UUID dispatchId, DepartureProfile profile, Instant at) {
        if (Objects.requireNonNull(profile) != DepartureProfile.LEGACY_V1)
            throw new IllegalStateException("PROVIDER_DEPARTURE_PROFILE_UNSUPPORTED");
        return tryReserveDeparture(dispatchId, at);
    }
    /**
     * Persist the immutable authenticated worker-side request emission for an
     * already reserved live-v8 departure.  It never grants, releases or
     * replaces a reservation; callers must supply the parent observation time
     * so incoherent worker evidence can fail closed.
     */
    default void recordAuthenticatedV8Departure(UUID dispatchId, Instant requestedAt, Instant observedAt) {
        throw new IllegalStateException("PROVIDER_AUTHENTICATED_DEPARTURE_UNSUPPORTED");
    }
    /** Close only after return/failure and verified transport cleanup. An unresolved reservation blocks all departures. */
    Snapshot markDepartureFinished(UUID dispatchId, Instant at);
    /** Publish confirmed response status as soon as known, independently of body completion. */
    Snapshot suspend(UUID evidenceId, UUID campaignId, int httpStatus, Instant observedAt, Instant retryNotBefore);
    /** Explicit operator command; compare version and respect Retry-After, preserving all pressure/history. */
    Snapshot rearm(long expectedVersion, Instant at);
}
