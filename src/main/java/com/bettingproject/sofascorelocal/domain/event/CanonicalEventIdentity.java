package com.bettingproject.sofascorelocal.domain.event;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record CanonicalEventIdentity(UUID value, String provider, long providerEventId) {

    public static final String SOFASCORE = "SOFASCORE";

    private static final String NAMESPACE = "sofascore-local-lab:event:v1";
    private static final Pattern PROVIDER_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,31}");

    public CanonicalEventIdentity {
        value = Objects.requireNonNull(value, "value");
        provider = Objects.requireNonNull(provider, "provider").trim();
        if (!PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException("provider must be a bounded canonical identifier");
        }
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        UUID expected = deterministicValue(provider, providerEventId);
        if (!value.equals(expected)) {
            throw new IllegalArgumentException(
                    "canonical event UUID must match provider and providerEventId");
        }
    }

    public static CanonicalEventIdentity sofascore(long providerEventId) {
        return of(SOFASCORE, providerEventId);
    }

    public static CanonicalEventIdentity of(String provider, long providerEventId) {
        Objects.requireNonNull(provider, "provider");
        String canonicalProvider = provider.trim();
        return new CanonicalEventIdentity(
                deterministicValue(canonicalProvider, providerEventId),
                canonicalProvider,
                providerEventId);
    }

    private static UUID deterministicValue(String provider, long providerEventId) {
        String name = NAMESPACE + "|" + provider + "|" + providerEventId;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
