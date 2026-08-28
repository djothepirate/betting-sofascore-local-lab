package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J4RealEventDetailsPhase1Result(
        UUID requestId,
        boolean completed,
        String terminalCode,
        int providerCallAttempts,
        int cacheHits,
        List<J4RealEventDetailsEventResult> events,
        List<J4RealEventDetailsUnavailableResult> unavailableEvents) {

    public J4RealEventDetailsPhase1Result {
        Objects.requireNonNull(requestId, "requestId");
        terminalCode = requireSafeCode(terminalCode);
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        unavailableEvents = List.copyOf(Objects.requireNonNull(
                unavailableEvents, "unavailableEvents"));
        if (providerCallAttempts < 0
                || providerCallAttempts > 2
                || cacheHits < 0
                || cacheHits > 2
                || events.size() + unavailableEvents.size() > 2) {
            throw new IllegalArgumentException("phase-1 result counts are outside bounds");
        }
        int resolvedTargets = events.size() + unavailableEvents.size();
        long distinctTargets = java.util.stream.Stream.concat(
                        events.stream().map(J4RealEventDetailsEventResult::eventId),
                        unavailableEvents.stream()
                                .map(J4RealEventDetailsUnavailableResult::eventId))
                .distinct()
                .count();
        long providerOutcomes = events.stream()
                .filter(event -> event.resolutionSource()
                        == J4RealEventDetailsResolutionSource.PROVIDER)
                .count() + unavailableEvents.size();
        long cacheOutcomes = events.stream()
                .filter(event -> event.resolutionSource()
                        == J4RealEventDetailsResolutionSource.CACHE)
                .count();
        boolean knownTargets = java.util.stream.Stream.concat(
                        events.stream().map(J4RealEventDetailsEventResult::eventId),
                        unavailableEvents.stream()
                                .map(J4RealEventDetailsUnavailableResult::eventId))
                .allMatch(EventDetailsProviderRequest.PHASE_1_EVENT_IDS::contains);
        if (providerCallAttempts + cacheHits > 2
                || resolvedTargets > providerCallAttempts + cacheHits
                || distinctTargets != resolvedTargets
                || providerOutcomes > providerCallAttempts
                || cacheOutcomes > cacheHits
                || !knownTargets) {
            throw new IllegalArgumentException("phase-1 outcomes exceed resolved requests");
        }
        if (completed && (!"COMPLETED".equals(terminalCode)
                || resolvedTargets != 2
                || providerCallAttempts + cacheHits != 2)) {
            throw new IllegalArgumentException("completion status is inconsistent");
        }
        if (!completed && "COMPLETED".equals(terminalCode)) {
            throw new IllegalArgumentException("completion status is inconsistent");
        }
    }

    public J4RealEventDetailsPhase1Result(
            UUID requestId,
            boolean completed,
            String terminalCode,
            int providerCallAttempts,
            int cacheHits,
            List<J4RealEventDetailsEventResult> events) {
        this(requestId, completed, terminalCode, providerCallAttempts, cacheHits, events, List.of());
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
