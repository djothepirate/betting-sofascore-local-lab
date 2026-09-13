package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/** Missing-player source attributes; reason and externalType remain uninterpreted provider codes. */
public record MissingLineupPlayer(long providerPlayerId, String name, Optional<Integer> shirtNumber,
        Optional<String> position, Optional<String> type, Optional<Integer> reason,
        Optional<String> description, Optional<Integer> externalType, Optional<OffsetDateTime> expectedEndDate,
        Optional<ProviderCountry> country) {
    public MissingLineupPlayer(long providerPlayerId, String name, Optional<Integer> shirtNumber,
            Optional<String> position, Optional<String> type, Optional<Integer> reason,
            Optional<String> description, Optional<Integer> externalType, Optional<OffsetDateTime> expectedEndDate) {
        this(providerPlayerId, name, shirtNumber, position, type, reason, description, externalType,
                expectedEndDate, Optional.empty());
    }
    public MissingLineupPlayer {
        if (providerPlayerId < 1) throw new IllegalArgumentException("providerPlayerId must be positive");
        name = boundedText(name, 200);
        shirtNumber = Objects.requireNonNull(shirtNumber, "shirtNumber");
        if (shirtNumber.filter(value -> value < 1 || value > 999).isPresent())
            throw new IllegalArgumentException("shirtNumber must be between 1 and 999");
        position = boundedOptional(position, 32);
        type = boundedOptional(type, 64);
        reason = Objects.requireNonNull(reason, "reason");
        description = boundedOptional(description, 300);
        externalType = Objects.requireNonNull(externalType, "externalType");
        expectedEndDate = Objects.requireNonNull(expectedEndDate, "expectedEndDate");
        country = Objects.requireNonNull(country, "country");
    }

    private static Optional<String> boundedOptional(Optional<String> value, int maximum) {
        return Objects.requireNonNull(value, "optional text").map(text -> boundedText(text, maximum));
    }

    private static String boundedText(String value, int maximum) {
        String text = Objects.requireNonNull(value, "text").trim();
        if (text.isEmpty() || text.length() > maximum || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("missing-player text must be bounded and non-control");
        return text;
    }
}
