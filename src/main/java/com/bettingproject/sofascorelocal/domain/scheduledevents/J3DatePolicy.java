package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** Bounds for new collections and plan revisions, independent of stored history. */
public final class J3DatePolicy {
    public static final LocalDate MINIMUM_COLLECTION_DATE = LocalDate.of(2000, 1, 1);

    private J3DatePolicy() { }

    /** Inclusive last civil date, using twelve calendar months rather than 365 days. */
    public static LocalDate maximumDate(Instant now) {
        return LocalDate.ofInstant(now, J3AutomationData.ZONE).plusMonths(12);
    }

    public static void requireCollectionDate(LocalDate date, Instant now) {
        if (date == null || date.isBefore(MINIMUM_COLLECTION_DATE) || date.isAfter(maximumDate(now))) {
            throw new IllegalArgumentException("J3_COLLECTION_DATE_OUT_OF_RANGE");
        }
    }

    public static void requirePlanTime(LocalDateTime at, Instant now) {
        Objects.requireNonNull(at);
        if (at.toLocalDate().isAfter(maximumDate(now))) {
            throw new IllegalArgumentException("J3_PLAN_TIME_OUT_OF_RANGE");
        }
        // The ledger checks that the resolved instant is strictly in the future.
        // A civil-time comparison here would reject a valid second occurrence at autumn DST.
    }
}
