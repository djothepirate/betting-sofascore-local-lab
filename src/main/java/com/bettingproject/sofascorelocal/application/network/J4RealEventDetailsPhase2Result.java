package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J4RealEventDetailsPhase2Result(
        UUID requestId,
        long requestedEventId,
        boolean completed,
        String terminalCode,
        int providerCallAttempts,
        List<J4RealEventDetailsEventResult> events) {

    public J4RealEventDetailsPhase2Result {
        Objects.requireNonNull(requestId, "requestId");
        EventDetailsProviderRequest.requirePhase2EventId(requestedEventId);
        terminalCode = requireSafeCode(terminalCode);
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (providerCallAttempts < 0 || providerCallAttempts > 1 || events.size() > 1) {
            throw new IllegalArgumentException("phase-2 result counts are outside bounds");
        }
        if (!events.isEmpty() && events.getFirst().eventId() != requestedEventId) {
            throw new IllegalArgumentException("phase-2 result contains another event id");
        }
        if (completed && (!"COMPLETED".equals(terminalCode)
                || providerCallAttempts != 1
                || events.size() != 1)) {
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
