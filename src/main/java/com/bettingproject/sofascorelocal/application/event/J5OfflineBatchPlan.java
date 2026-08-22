package com.bettingproject.sofascorelocal.application.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record J5OfflineBatchPlan(
        UUID requestId,
        LocalDate date,
        ZoneId zoneId,
        Instant fromInclusive,
        Instant toExclusive,
        Instant preparedAt,
        Instant expiresAt,
        List<J5OfflineBatchPlanEvent> events,
        String planSha256,
        String confirmationPhrase) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public J5OfflineBatchPlan {
        requestId = Objects.requireNonNull(requestId, "requestId");
        date = Objects.requireNonNull(date, "date");
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        fromInclusive = Objects.requireNonNull(fromInclusive, "fromInclusive");
        toExclusive = Objects.requireNonNull(toExclusive, "toExclusive");
        preparedAt = Objects.requireNonNull(preparedAt, "preparedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        planSha256 = Objects.requireNonNull(planSha256, "planSha256");
        confirmationPhrase = Objects.requireNonNull(confirmationPhrase, "confirmationPhrase");
        if (!toExclusive.isAfter(fromInclusive)
                || !expiresAt.isAfter(preparedAt)
                || events.isEmpty()
                || events.size() > J5OfflineBatchPlanService.MAXIMUM_EVENTS
                || !SHA_256.matcher(planSha256).matches()
                || confirmationPhrase.length() > 160) {
            throw new IllegalArgumentException("offline batch plan is inconsistent");
        }
    }

    public int payloadCount() {
        return events.size() * 3;
    }
}
