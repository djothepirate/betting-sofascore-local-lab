package com.bettingproject.sofascorelocal.domain.history;

import java.util.Objects;
import java.util.Optional;

public record J6SemanticChange(
        String field,
        Optional<String> beforeValue,
        Optional<String> afterValue,
        J6ChangeKind kind) {

    public J6SemanticChange {
        field = boundedText(field, "field", 512);
        beforeValue = boundedOptional(beforeValue, "beforeValue", 2_000);
        afterValue = boundedOptional(afterValue, "afterValue", 2_000);
        kind = Objects.requireNonNull(kind, "kind");
        if (beforeValue.equals(afterValue)) {
            throw new IllegalArgumentException("a semantic change requires different values");
        }
        if (kind == J6ChangeKind.ADDED && beforeValue.isPresent()) {
            throw new IllegalArgumentException("ADDED cannot contain a before value");
        }
        if (kind == J6ChangeKind.REMOVED && afterValue.isPresent()) {
            throw new IllegalArgumentException("REMOVED cannot contain an after value");
        }
    }

    private static Optional<String> boundedOptional(
            Optional<String> value,
            String name,
            int maximumLength) {
        return Objects.requireNonNull(value, name)
                .map(item -> boundedText(item, name, maximumLength));
    }

    private static String boundedText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded non-control text");
        }
        return normalized;
    }
}
