package com.bettingproject.sofascorelocal.domain.event;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

public record EventSourceTrace(
        EventSourceKind kind,
        OptionalLong snapshotId,
        Optional<String> fixtureId,
        String payloadSha256,
        String parserVersion,
        Instant receivedAt) {

    private static final Pattern FIXTURE_ID_PATTERN =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PARSER_VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    public EventSourceTrace {
        kind = Objects.requireNonNull(kind, "kind");
        snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        fixtureId = Objects.requireNonNull(fixtureId, "fixtureId")
                .map(value -> value.trim());
        payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256")
                .trim()
                .toLowerCase(Locale.ROOT);
        parserVersion = Objects.requireNonNull(parserVersion, "parserVersion").trim();
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");

        if (!SHA_256_PATTERN.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be a lower-case SHA-256");
        }
        if (parserVersion.length() > 32
                || !PARSER_VERSION_PATTERN.matcher(parserVersion).matches()) {
            throw new IllegalArgumentException("parserVersion must be a bounded safe identifier");
        }
        switch (kind) {
            case PROVIDER_SNAPSHOT -> {
                if (snapshotId.isEmpty() || snapshotId.getAsLong() < 1 || fixtureId.isPresent()) {
                    throw new IllegalArgumentException(
                            "provider snapshot provenance requires only a positive snapshotId");
                }
            }
            case SYNTHETIC_FIXTURE -> {
                if (snapshotId.isPresent()
                        || fixtureId.isEmpty()
                        || fixtureId.orElseThrow().length() > 100
                        || !FIXTURE_ID_PATTERN.matcher(fixtureId.orElseThrow()).matches()) {
                    throw new IllegalArgumentException(
                            "synthetic fixture provenance requires only a kebab-case fixtureId");
                }
            }
        }
    }

    public static EventSourceTrace providerSnapshot(
            long snapshotId,
            String payloadSha256,
            String parserVersion,
            Instant receivedAt) {
        return new EventSourceTrace(
                EventSourceKind.PROVIDER_SNAPSHOT,
                OptionalLong.of(snapshotId),
                Optional.empty(),
                payloadSha256,
                parserVersion,
                receivedAt);
    }

    public static EventSourceTrace syntheticFixture(
            String fixtureId,
            String payloadSha256,
            String parserVersion,
            Instant recordedAt) {
        return new EventSourceTrace(
                EventSourceKind.SYNTHETIC_FIXTURE,
                OptionalLong.empty(),
                Optional.of(fixtureId),
                payloadSha256,
                parserVersion,
                recordedAt);
    }

    public String sourceReference() {
        return switch (kind) {
            case PROVIDER_SNAPSHOT -> "snapshot:" + snapshotId.orElseThrow();
            case SYNTHETIC_FIXTURE -> fixtureId.orElseThrow();
        };
    }
}
