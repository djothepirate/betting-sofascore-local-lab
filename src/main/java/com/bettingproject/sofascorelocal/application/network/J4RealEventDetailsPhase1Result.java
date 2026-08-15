package com.bettingproject.sofascorelocal.application.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J4RealEventDetailsPhase1Result(
        UUID requestId,
        boolean completed,
        String terminalCode,
        int providerCallAttempts,
        int cacheHits,
        List<J4RealEventDetailsEventResult> events) {

    public J4RealEventDetailsPhase1Result {
        Objects.requireNonNull(requestId, "requestId");
        terminalCode = requireSafeCode(terminalCode);
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (providerCallAttempts < 0
                || providerCallAttempts > 2
                || cacheHits < 0
                || cacheHits > 2
                || events.size() > 2) {
            throw new IllegalArgumentException("phase-1 result counts are outside bounds");
        }
        if (completed && (!"COMPLETED".equals(terminalCode) || events.size() != 2)) {
            throw new IllegalArgumentException("completion status is inconsistent");
        }
        if (!completed && "COMPLETED".equals(terminalCode)) {
            throw new IllegalArgumentException("completion status is inconsistent");
        }
    }

    private static String requireSafeCode(String value) {
        Objects.requireNonNull(value, "terminalCode");
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("terminalCode must be a bounded safe code");
        }
        return normalized;
    }
}
