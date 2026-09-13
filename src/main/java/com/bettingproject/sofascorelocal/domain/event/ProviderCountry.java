package com.bettingproject.sofascorelocal.domain.event;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Optional provider country metadata; no nationality is inferred from the player's team. */
public record ProviderCountry(Optional<String> name, Optional<String> alpha2) {
    public ProviderCountry {
        name = Objects.requireNonNull(name).map(value -> {
            String text = value.trim();
            if (text.isEmpty() || text.length() > 120 || text.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("invalid provider country name");
            return text;
        });
        alpha2 = Objects.requireNonNull(alpha2).map(value -> {
            String code = value.trim().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z]{2}")) throw new IllegalArgumentException("invalid provider country code");
            return code;
        });
        if (name.isEmpty() && alpha2.isEmpty()) throw new IllegalArgumentException("empty provider country");
    }
}
