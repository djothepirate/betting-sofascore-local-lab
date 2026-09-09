package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Provider pressure and refusal evidence are independent of a campaign/process lease. */
public final class ProviderResilienceData {
    private ProviderResilienceData() { }

    public static final String POLICY_VERSION = "provider-resilience-v1";
    public static final Duration MINIMUM_DEPARTURE_INTERVAL = Duration.ofSeconds(2);
    public static final int MAXIMUM_DEPARTURES_PER_MINUTE = 25;
    public static final int MAXIMUM_DEPARTURES_PER_HOUR = 1000;
    public static final Duration MAXIMUM_RETRY_AFTER = Duration.ofDays(365);

    public enum State { OPEN, SUSPENDED }
    public enum DepartureReason {
        ALLOWED, PROVIDER_SUSPENDED, RATE_LIMITED, DISPATCH_ALREADY_RESERVED, CLOCK_REGRESSION,
        DEPARTURE_UNRESOLVED
    }

    /** Last refusal metadata remains available after manual rearming; state alone grants eligibility. */
    public record Snapshot(State state, long version, Instant changedAt, Integer httpStatus,
                           Instant suspendedAt, Instant retryNotBefore, Instant lastDepartureAt,
                           UUID evidenceId, UUID campaignId, Instant lastDepartureFinishedAt, UUID unresolvedDispatchId) {
        public Snapshot {
            Objects.requireNonNull(state); Objects.requireNonNull(changedAt);
            if (version < 0 || (httpStatus != null && httpStatus != 403 && httpStatus != 429)
                    || (state == State.SUSPENDED && (httpStatus == null || suspendedAt == null || evidenceId == null)))
                throw new IllegalArgumentException("invalid provider resilience snapshot");
        }
    }

    /** nextAllowedAt is advisory; an admission must always be retried atomically at actual dispatch. */
    public record DepartureDecision(boolean allowed, DepartureReason reason, Instant nextAllowedAt,
                                    Snapshot snapshot) {
        public DepartureDecision {
            Objects.requireNonNull(reason); Objects.requireNonNull(snapshot);
            if (allowed != (reason == DepartureReason.ALLOWED))
                throw new IllegalArgumentException("invalid provider departure decision");
        }
    }

    public static Instant timestamp(Instant at) {
        return Objects.requireNonNull(at).truncatedTo(ChronoUnit.MICROS);
    }

    /** Only the parsed date is retained. Raw headers, arbitrary errors and payloads are never accepted. */
    public static Instant validateRetryNotBefore(Instant observedAt, Instant retryNotBefore) {
        if (retryNotBefore == null) return null;
        Instant observed = timestamp(observedAt), retry = timestamp(retryNotBefore);
        if (retry.isBefore(observed) || Duration.between(observed, retry).compareTo(MAXIMUM_RETRY_AFTER) > 0)
            throw new IllegalArgumentException("invalid provider retry deadline");
        return retry;
    }
}
