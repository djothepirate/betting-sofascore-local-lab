package com.bettingproject.sofascorelocal.domain.eventdetails;

import java.util.Objects;
import java.util.Optional;

public record EventVenue(long providerVenueId, String name, Optional<String> city) {

    public EventVenue {
        if (providerVenueId < 1) {
            throw new IllegalArgumentException("providerVenueId must be positive");
        }
        name = requireText(name, "name", 200);
        city = Objects.requireNonNull(city, "city")
                .map(value -> requireText(value, "city", 200));
    }

    private static String requireText(String value, String fieldName, int maximumLength) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must be bounded non-blank text");
        }
        return normalized;
    }
}
