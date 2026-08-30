package com.bettingproject.sofascorelocal.domain.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record J8BenchmarkUnit(
        UUID campaignId,
        int unitOrdinal,
        SofascoreEndpointType endpointType,
        String requestKey,
        Optional<UUID> canonicalEventId,
        OptionalLong providerEventId,
        Instant declaredAt) {

    public static final long MAXIMUM_PROVIDER_EVENT_ID = 999_999_999L;

    private static final Pattern SCHEDULED_EVENTS_REQUEST_KEY = Pattern.compile(
            "SCHEDULED_EVENTS\\|date=([0-9]{4}-[0-9]{2}-[0-9]{2})"
                    + "\\|page=([1-9]|1[0-9]|2[0-5])");
    private static final Pattern TOURNAMENT_EVENTS_REQUEST_KEY = Pattern.compile(
            "TOURNAMENT_SCHEDULED_EVENTS\\|date=([0-9]{4}-[0-9]{2}-[0-9]{2})"
                    + "\\|uniqueTournamentId=([1-9][0-9]*)");

    public J8BenchmarkUnit {
        campaignId = Objects.requireNonNull(campaignId, "campaignId");
        endpointType = Objects.requireNonNull(endpointType, "endpointType");
        requestKey = requireSafeText(requestKey, "requestKey", 512);
        canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
        declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");

        if (unitOrdinal < 1 || unitOrdinal > 25) {
            throw new IllegalArgumentException("unitOrdinal must be between 1 and 25");
        }
        boolean eventEndpoint = endpointType == SofascoreEndpointType.EVENT_DETAILS
                || endpointType == SofascoreEndpointType.EVENT_STATISTICS
                || endpointType == SofascoreEndpointType.EVENT_INCIDENTS
                || endpointType == SofascoreEndpointType.EVENT_LINEUPS;
        if (eventEndpoint) {
            if (providerEventId.isEmpty()
                    || providerEventId.getAsLong() < 1
                    || providerEventId.getAsLong() > MAXIMUM_PROVIDER_EVENT_ID) {
                throw new IllegalArgumentException(
                        "event benchmark units require a providerEventId between 1 and "
                                + MAXIMUM_PROVIDER_EVENT_ID);
            }
            requireExactRequestKey(
                    requestKey,
                    endpointType.name() + "|eventId=" + providerEventId.getAsLong());
        }
        else if (endpointType == SofascoreEndpointType.SCHEDULED_EVENTS
                || endpointType == SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS) {
            if (canonicalEventId.isPresent() || providerEventId.isPresent()) {
                throw new IllegalArgumentException(
                        "discovery benchmark units cannot identify one event");
            }
            if (endpointType == SofascoreEndpointType.SCHEDULED_EVENTS) {
                Matcher request = requireRequestKeyShape(
                        requestKey,
                        SCHEDULED_EVENTS_REQUEST_KEY,
                        "scheduled-events request key");
                requireIsoDate(request.group(1), "scheduled-events request date");
                if (Integer.parseInt(request.group(2)) != unitOrdinal) {
                    throw new IllegalArgumentException(
                            "scheduled-events page must equal unitOrdinal");
                }
            }
            else {
                Matcher request = requireRequestKeyShape(
                        requestKey,
                        TOURNAMENT_EVENTS_REQUEST_KEY,
                        "tournament request key");
                requireIsoDate(request.group(1), "tournament request date");
                try {
                    Long.parseLong(request.group(2));
                }
                catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "tournament request id must be a positive numeric long",
                            exception);
                }
            }
        }
        else {
            throw new IllegalArgumentException(
                    "endpointType is outside the J8 benchmark scope");
        }
        if (canonicalEventId.isPresent() && providerEventId.isEmpty()) {
            throw new IllegalArgumentException(
                    "canonicalEventId requires providerEventId correlation");
        }
    }

    private static void requireExactRequestKey(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "requestKey must be the exact canonical endpoint key");
        }
    }

    private static Matcher requireRequestKeyShape(
            String requestKey,
            Pattern pattern,
            String description) {
        Matcher matcher = pattern.matcher(requestKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "requestKey must use the exact " + description);
        }
        return matcher;
    }

    private static void requireIsoDate(String value, String description) {
        try {
            LocalDate.parse(value);
        }
        catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(description + " must be an ISO date", exception);
        }
    }

    private static String requireSafeText(String value, String name, int maximumLength) {
        String required = Objects.requireNonNull(value, name);
        String normalized = required.trim();
        if (!required.equals(normalized)
                || normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    name + " must be bounded non-control text");
        }
        return normalized;
    }
}
