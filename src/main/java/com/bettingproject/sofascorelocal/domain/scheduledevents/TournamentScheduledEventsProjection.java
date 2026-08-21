package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record TournamentScheduledEventsProjection(
        LocalDate date,
        ZoneId zone,
        Instant fromInclusive,
        Instant toExclusive,
        List<ScheduledEvent> events,
        TournamentEventCountStatus countStatus,
        OptionalInt expectedCount,
        int actualCount,
        int exactDuplicateCount,
        int excludedOtherTournamentCount,
        int excludedOutsideDateCount) {

    public TournamentScheduledEventsProjection {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(countStatus, "countStatus");
        Objects.requireNonNull(expectedCount, "expectedCount");
        if (!toExclusive.isAfter(fromInclusive)
                || actualCount != events.size()
                || exactDuplicateCount < 0
                || excludedOtherTournamentCount < 0
                || excludedOutsideDateCount < 0
                || expectedCount.stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("projection metadata is inconsistent");
        }
        if ((countStatus == TournamentEventCountStatus.COUNT_VERIFIED
                || countStatus == TournamentEventCountStatus.COUNT_MISMATCH)
                != expectedCount.isPresent()) {
            throw new IllegalArgumentException(
                    "only a verified or mismatched count may expose an expected value");
        }
        if (countStatus == TournamentEventCountStatus.COUNT_VERIFIED
                && expectedCount.orElseThrow() != actualCount) {
            throw new IllegalArgumentException("verified count must equal actual count");
        }
        if (countStatus == TournamentEventCountStatus.COUNT_MISMATCH
                && expectedCount.orElseThrow() == actualCount) {
            throw new IllegalArgumentException("mismatched count must differ from actual count");
        }
    }

    public boolean normalizationAllowed() {
        return countStatus != TournamentEventCountStatus.COUNT_MISMATCH;
    }
}
