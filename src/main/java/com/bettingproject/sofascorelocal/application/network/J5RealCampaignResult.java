package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J5RealCampaignResult(
        UUID requestId,
        UUID canonicalEventId,
        long eventId,
        boolean completed,
        String terminalCode,
        int providerCallAttempts,
        List<J5RealEndpointResult> endpoints) {

    public J5RealCampaignResult {
        requestId = Objects.requireNonNull(requestId, "requestId");
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        J5EventDataProviderRequest.requireEventId(eventId);
        if (!CanonicalEventIdentity.sofascore(eventId).value().equals(canonicalEventId)) {
            throw new IllegalArgumentException("campaign identity is inconsistent");
        }
        terminalCode = requireSafeCode(terminalCode);
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if (providerCallAttempts < 0
                || providerCallAttempts > 3
                || endpoints.size() > providerCallAttempts
                || endpoints.size() > 3) {
            throw new IllegalArgumentException("campaign counts are outside bounds");
        }
        for (int i = 0; i < endpoints.size(); i++) {
            if (endpoints.get(i).endpointType()
                    != J5RealControlService.ORDERED_ENDPOINTS.get(i)) {
                throw new IllegalArgumentException("campaign results are out of order");
            }
        }
        if (completed && (!"COMPLETED".equals(terminalCode)
                || providerCallAttempts != 3
                || endpoints.size() != 3)) {
            throw new IllegalArgumentException("completed campaign is inconsistent");
        }
        if (!completed && "COMPLETED".equals(terminalCode)) {
            throw new IllegalArgumentException("failed campaign cannot be COMPLETED");
        }
    }

    private static String requireSafeCode(String value) {
        String normalized = Objects.requireNonNull(value, "terminalCode").trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("terminalCode must be a bounded safe code");
        }
        return normalized;
    }
}
