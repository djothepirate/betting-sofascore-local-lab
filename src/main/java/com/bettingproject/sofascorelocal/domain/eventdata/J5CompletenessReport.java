package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.List;
import java.util.Objects;

public record J5CompletenessReport(
        J5CompletenessStatus status,
        int scorePercent,
        int presentSignals,
        int expectedSignals,
        List<String> missingPaths) {

    public J5CompletenessReport {
        status = Objects.requireNonNull(status, "status");
        missingPaths = Objects.requireNonNull(missingPaths, "missingPaths").stream()
                .map(J5CompletenessReport::normalizePath)
                .toList();
        if (scorePercent < 0 || scorePercent > 100) {
            throw new IllegalArgumentException("scorePercent must be between 0 and 100");
        }
        if (presentSignals < 0
                || expectedSignals < 0
                || presentSignals > expectedSignals) {
            throw new IllegalArgumentException("completeness signal counts are inconsistent");
        }
        if (missingPaths.stream().distinct().count() != missingPaths.size()) {
            throw new IllegalArgumentException("missingPaths must not contain duplicates");
        }
        switch (status) {
            case COMPLETE -> {
                if (expectedSignals == 0
                        || presentSignals != expectedSignals
                        || scorePercent != 100
                        || !missingPaths.isEmpty()) {
                    throw new IllegalArgumentException(
                            "COMPLETE requires every non-empty signal to be present");
                }
            }
            case PARTIAL -> {
                if (expectedSignals == 0 || presentSignals >= expectedSignals) {
                    throw new IllegalArgumentException(
                            "PARTIAL requires at least one missing expected signal");
                }
                int expectedScore = Math.toIntExact(Math.floorDiv(
                        (long) presentSignals * 100,
                        expectedSignals));
                if (scorePercent != expectedScore
                        || missingPaths.size() != expectedSignals - presentSignals) {
                    throw new IllegalArgumentException(
                            "PARTIAL requires a conservative score and every missing signal path");
                }
            }
            case EMPTY_VALID -> {
                if (expectedSignals != 0
                        || presentSignals != 0
                        || scorePercent != 100
                        || !missingPaths.isEmpty()) {
                    throw new IllegalArgumentException(
                            "EMPTY_VALID represents an explicit structurally valid empty list");
                }
            }
        }
    }

    public static J5CompletenessReport emptyValid() {
        return new J5CompletenessReport(
                J5CompletenessStatus.EMPTY_VALID,
                100,
                0,
                0,
                List.of());
    }

    public static J5CompletenessReport measured(
            int presentSignals,
            int expectedSignals,
            List<String> missingPaths) {
        Objects.requireNonNull(missingPaths, "missingPaths");
        if (expectedSignals < 1) {
            throw new IllegalArgumentException("measured completeness requires expected signals");
        }
        int score = Math.toIntExact(Math.floorDiv(
                (long) presentSignals * 100,
                expectedSignals));
        J5CompletenessStatus status = presentSignals == expectedSignals
                ? J5CompletenessStatus.COMPLETE
                : J5CompletenessStatus.PARTIAL;
        return new J5CompletenessReport(
                status,
                score,
                presentSignals,
                expectedSignals,
                missingPaths);
    }

    private static String normalizePath(String path) {
        String normalized = Objects.requireNonNull(path, "missing path").trim();
        if (normalized.isEmpty()
                || normalized.length() > 300
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "missingPaths must contain bounded non-control JSON paths");
        }
        return normalized;
    }
}
