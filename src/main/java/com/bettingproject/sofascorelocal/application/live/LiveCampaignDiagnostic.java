package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderFailure;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Bounded process evidence. Never retains an exception, message, cause or provider payload. */
public record LiveCampaignDiagnostic(Phase phase, String code, Instant occurredAt) {
    public enum Phase {
        LEASE_ACQUISITION, CAMPAIGN_LAUNCH, SELECTION_RECHECK, TRANSPORT_OPEN, SCHEDULING,
        BUDGET_READ, STORAGE_CHECK, ATTEMPT_RESERVATION, TRANSPORT, RAW_SAVE, NORMALIZATION,
        RESULT_PUBLICATION, SCHEDULE_PUBLICATION,
        CLEANUP_TRANSPORT_CLOSE, CLEANUP_TRANSPORT_ABSENCE, CLEANUP_EXCLUSION,
        CLEANUP_STATE_READ, CLEANUP_OWNERSHIP_CHECK, CLEANUP_SCHEDULE_PUBLICATION,
        CLEANUP_ATTEMPT_RECONCILIATION, CLEANUP_TERMINAL_PUBLICATION, CLEANUP_LEASE_RELEASE
    }

    private static final Set<String> LOCAL_CODES = Set.of(
            "LIVE_STORAGE_PROBE_NOT_CONFIGURED", "LIVE_STORAGE_PROBE_TIMEOUT", "LIVE_STORAGE_PROBE_FAILED",
            "LIVE_STORAGE_PROBE_INVALID", "LIVE_STORAGE_PROBE_INTERRUPTED", "LIVE_STORAGE_CAPACITY_REFUSED",
            "LIVE_RAW_PREVIOUSLY_PURGED", "LIVE_PROVIDER_CLEANUP_UNVERIFIED", "LIVE_CLEANUP_OWNERSHIP_CHANGED");
    private static final Set<String> CODES = Stream.concat(LOCAL_CODES.stream(),
            Stream.concat(Stream.of("RUNTIME_OR_STORAGE_FAILURE", "PROVIDER_COORDINATION_FAILURE"),
                    Arrays.stream(PlaywrightProviderFailure.values()).map(value -> "PLAYWRIGHT_" + value.name())))
            .collect(Collectors.toUnmodifiableSet());

    public LiveCampaignDiagnostic {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(occurredAt);
        if (code == null || !CODES.contains(code)) throw new IllegalArgumentException("LIVE_DIAGNOSTIC_CODE_INVALID");
    }

    static LiveCampaignDiagnostic from(Phase phase, RuntimeException failure, Instant occurredAt) {
        String code;
        if (failure instanceof PlaywrightProviderException transport) code = "PLAYWRIGHT_" + transport.failure().name();
        else if (failure instanceof ManualProviderRequestCoordinator.CoordinationException)
            code = "PROVIDER_COORDINATION_FAILURE";
        else {
            // An exact match yields a known constant; no prefix matching or cause traversal.
            String message = failure.getMessage();
            code = message != null && LOCAL_CODES.contains(message) ? message : "RUNTIME_OR_STORAGE_FAILURE";
        }
        return new LiveCampaignDiagnostic(phase, code, occurredAt);
    }
}
