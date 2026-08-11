package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.Objects;
import java.util.Optional;

public record ScheduledEventStatus(String type, Optional<String> description) {

    public ScheduledEventStatus {
        Objects.requireNonNull(type, "type");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        description = Objects.requireNonNull(description, "description");
        description.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        });
    }
}
