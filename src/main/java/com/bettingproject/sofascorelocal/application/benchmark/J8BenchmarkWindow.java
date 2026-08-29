package com.bettingproject.sofascorelocal.application.benchmark;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

public record J8BenchmarkWindow(
        Optional<Instant> fromInclusive,
        Optional<Instant> toExclusive) {

    private static final int MAXIMUM_PARAMETER_LENGTH = 64;

    public J8BenchmarkWindow {
        fromInclusive = Objects.requireNonNull(fromInclusive, "fromInclusive");
        toExclusive = Objects.requireNonNull(toExclusive, "toExclusive");
        if (fromInclusive.isPresent() != toExclusive.isPresent()) {
            throw new IllegalArgumentException(
                    "benchmark window bounds must either both be present or both be absent");
        }
        if (fromInclusive.isPresent()
                && fromInclusive.orElseThrow().isAfter(toExclusive.orElseThrow())) {
            throw new IllegalArgumentException(
                    "benchmark window bounds are reversed");
        }
    }

    public static J8BenchmarkWindow allAvailable() {
        return new J8BenchmarkWindow(Optional.empty(), Optional.empty());
    }

    public static J8BenchmarkWindow between(Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "benchmark window must be a non-empty half-open interval");
        }
        return new J8BenchmarkWindow(
                Optional.of(fromInclusive),
                Optional.of(toExclusive));
    }

    public static J8BenchmarkWindow parse(String from, String to) {
        boolean fromAbsent = from == null;
        boolean toAbsent = to == null;
        if (fromAbsent && toAbsent) {
            return allAvailable();
        }
        if (fromAbsent || toAbsent) {
            throw new IllegalArgumentException(
                    "benchmark window requires both from and to");
        }
        String normalizedFrom = bounded(from, "from");
        String normalizedTo = bounded(to, "to");
        if (!normalizedFrom.endsWith("Z") || !normalizedTo.endsWith("Z")) {
            throw new IllegalArgumentException(
                    "benchmark window bounds must use the UTC Z suffix");
        }
        try {
            return between(Instant.parse(normalizedFrom), Instant.parse(normalizedTo));
        }
        catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "benchmark window bounds must be ISO-8601 UTC instants", exception);
        }
    }

    public boolean explicit() {
        return fromInclusive.isPresent();
    }

    J8BenchmarkWindow boundedAt(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf");
        if (!explicit()) {
            return this;
        }
        Instant from = fromInclusive.orElseThrow();
        if (!from.isBefore(asOf)) {
            return emptyAt(asOf);
        }
        Instant to = toExclusive.orElseThrow();
        return to.isAfter(asOf) ? between(from, asOf) : this;
    }

    boolean empty() {
        return explicit()
                && fromInclusive.orElseThrow().equals(toExclusive.orElseThrow());
    }

    public Instant effectiveTo(Instant generatedAt) {
        return toExclusive.orElse(Objects.requireNonNull(generatedAt, "generatedAt"));
    }

    private static J8BenchmarkWindow emptyAt(Instant instant) {
        Instant boundedInstant = Objects.requireNonNull(instant, "instant");
        return new J8BenchmarkWindow(
                Optional.of(boundedInstant), Optional.of(boundedInstant));
    }

    private static String bounded(String value, String name) {
        if (value.isEmpty() || value.length() > MAXIMUM_PARAMETER_LENGTH) {
            throw new IllegalArgumentException(
                    "benchmark window " + name
                            + " raw value must contain between 1 and 64 characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "benchmark window " + name + " cannot contain control characters");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "benchmark window " + name
                            + " must contain between 1 and 64 characters");
        }
        return normalized;
    }
}
