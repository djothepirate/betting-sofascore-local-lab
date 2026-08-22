package com.bettingproject.sofascorelocal.application.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record J5OfflineBatchResult(
        UUID requestId,
        boolean completed,
        String terminalCode,
        int eventCount,
        int localJsonImports,
        long totalBytes,
        List<J5OfflineBatchEventResult> events) {

    public J5OfflineBatchResult {
        requestId = Objects.requireNonNull(requestId, "requestId");
        terminalCode = requireSafeCode(terminalCode);
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (eventCount < 1
                || eventCount > J5OfflineBatchPlanService.MAXIMUM_EVENTS
                || localJsonImports < 0
                || localJsonImports > eventCount * 3
                || totalBytes < 0
                || totalBytes > J5OfflineBatchUploadService.MAXIMUM_TOTAL_BYTES
                || events.size() > eventCount) {
            throw new IllegalArgumentException("offline batch result counters are inconsistent");
        }
        if (completed && (!"COMPLETED".equals(terminalCode)
                || localJsonImports != eventCount * 3
                || events.size() != eventCount)) {
            throw new IllegalArgumentException("completed offline batch result is inconsistent");
        }
        if (!completed && "COMPLETED".equals(terminalCode)) {
            throw new IllegalArgumentException("failed batch cannot be completed");
        }
    }

    public static J5OfflineBatchResult failed(
            J5OfflineBatchPlan plan,
            J5OfflineBatchError error,
            long totalBytes) {
        return new J5OfflineBatchResult(
                plan.requestId(),
                false,
                error.name(),
                plan.events().size(),
                0,
                totalBytes,
                List.of());
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
