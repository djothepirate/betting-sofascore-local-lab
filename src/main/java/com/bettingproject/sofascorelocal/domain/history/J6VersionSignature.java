package com.bettingproject.sofascorelocal.domain.history;

import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;

import java.util.Objects;
import java.util.regex.Pattern;

public record J6VersionSignature(
        EventSourceTrace source,
        String normalizedSha256) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public J6VersionSignature {
        source = Objects.requireNonNull(source, "source");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (!SHA_256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new IllegalArgumentException("normalizedSha256 must be a lower-case SHA-256");
        }
    }
}
