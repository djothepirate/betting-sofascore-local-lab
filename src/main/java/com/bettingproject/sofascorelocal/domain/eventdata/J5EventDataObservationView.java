package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;

import java.util.Objects;
import java.util.regex.Pattern;

public record J5EventDataObservationView(
        long observationId,
        CanonicalEventIdentity identity,
        J5EventData data,
        EventSourceTrace source,
        J5CompletenessReport completeness,
        String normalizedSha256) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public J5EventDataObservationView {
        if (observationId < 1) {
            throw new IllegalArgumentException("observationId must be positive");
        }
        identity = Objects.requireNonNull(identity, "identity");
        data = Objects.requireNonNull(data, "data");
        source = Objects.requireNonNull(source, "source");
        completeness = Objects.requireNonNull(completeness, "completeness");
        normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
        if (identity.providerEventId() != data.providerEventId()) {
            throw new IllegalArgumentException(
                    "persisted J5 data provider identity must match its canonical event");
        }
        if (!SHA_256_PATTERN.matcher(normalizedSha256).matches()) {
            throw new IllegalArgumentException("normalizedSha256 must be a lower-case SHA-256");
        }
    }
}
