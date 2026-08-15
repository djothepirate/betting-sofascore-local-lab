package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.Objects;
import java.util.Optional;

public record EventStatisticMetric(
        String period,
        String groupName,
        String metricCode,
        String metricName,
        Optional<String> homeValue,
        Optional<String> awayValue) {

    public EventStatisticMetric {
        period = boundedText(period, "period", 32);
        groupName = boundedText(groupName, "groupName", 100);
        metricCode = boundedText(metricCode, "metricCode", 100);
        metricName = boundedText(metricName, "metricName", 150);
        homeValue = boundedOptional(homeValue, "homeValue", 100);
        awayValue = boundedOptional(awayValue, "awayValue", 100);
    }

    private static Optional<String> boundedOptional(
            Optional<String> value,
            String name,
            int maximumLength) {
        return Objects.requireNonNull(value, name)
                .map(item -> boundedText(item, name, maximumLength));
    }

    private static String boundedText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded non-control text");
        }
        return normalized;
    }
}
