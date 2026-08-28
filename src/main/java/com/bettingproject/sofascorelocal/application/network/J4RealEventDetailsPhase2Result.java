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
        List<J4RealEventDetailsEventResult> events,
        List<J4RealEventDetailsUnavailableResult> unavailableEvents) {

    public J4RealEventDetailsPhase2Result {
        Objects.requireNonNull(requestId, "requestId");
        EventDetailsProviderRequest.requirePhase2EventId(requestedEventId);
        terminalCode = requireSafeCode(terminalCode);
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        unavailableEvents = List.copyOf(Objects.requireNonNull(
                unavailableEvents, "unavailableEvents"));
        if (providerCallAttempts < 0
                || providerCallAttempts > 1
                || events.size() + unavailableEvents.size() > 1) {
            throw new IllegalArgumentException("phase-2 result counts are outside bounds");
        }
        if (events.size() + unavailableEvents.size() > providerCallAttempts) {
            throw new IllegalArgumentException("phase-2 outcome exceeds provider attempts");
        }
        if (!events.isEmpty() && events.getFirst().eventId() != requestedEventId) {
            throw new IllegalArgumentException("phase-2 result contains another event id");
        }
        if (!unavailableEvents.isEmpty()
                && unavailableEvents.getFirst().eventId() != requestedEventId) {
            throw new IllegalArgumentException("phase-2 result contains another event id");
        }
        if (completed && (!("COMPLETED".equals(terminalCode)
                        || "COMPLETED_UNAVAILABLE".equals(terminalCode))
                || providerCallAttempts != 1
                || events.size() + unavailableEvents.size() != 1)) {
            throw new IllegalArgumentException("completion status is inconsistent");
        }
        if (!completed && terminalCode.startsWith("COMPLETED")) {
            throw new IllegalArgumentException("completion status is inconsistent");
        }
        if ("COMPLETED_UNAVAILABLE".equals(terminalCode)
                != !unavailableEvents.isEmpty()) {
            throw new IllegalArgumentException("unavailability status is inconsistent");
        }
    }

    public J4RealEventDetailsPhase2Result(
            UUID requestId,
            long requestedEventId,
            boolean completed,
            String terminalCode,
            int providerCallAttempts,
            List<J4RealEventDetailsEventResult> events) {
        this(
                requestId,
                requestedEventId,
                completed,
                terminalCode,
                providerCallAttempts,
                events,
                List.of());
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
