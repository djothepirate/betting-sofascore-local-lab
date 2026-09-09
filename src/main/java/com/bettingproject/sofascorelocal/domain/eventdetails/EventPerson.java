package com.bettingproject.sofascorelocal.domain.eventdetails;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import java.util.Objects;
import java.util.Optional;

/** Named official supplied by the event details, without an inferred identity. */
public record EventPerson(String name, Optional<ProviderCountry> country) {
    public EventPerson {
        name = Objects.requireNonNull(name).trim();
        if (name.isEmpty() || name.length() > 200 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid event person name");
        country = Objects.requireNonNull(country);
    }
}
