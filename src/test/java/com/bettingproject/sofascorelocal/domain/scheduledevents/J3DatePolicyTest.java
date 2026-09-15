package com.bettingproject.sofascorelocal.domain.scheduledevents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static com.bettingproject.sofascorelocal.domain.scheduledevents.J3DatePolicy.*;

class J3DatePolicyTest {
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"1999-12-31", "2027-09-15", "9999-01-31"})
    void rejectsCollectionDatesOutsideTheInclusiveRange(String date) {
        assertThatThrownBy(() -> requireCollectionDate(LocalDate.parse(date), NOW))
                .hasMessage("J3_COLLECTION_DATE_OUT_OF_RANGE");
    }

    @Test void acceptsTheFirstDateHistoryAndTheLastDate() {
        for (String date : new String[]{"2000-01-01", "2026-09-02", "2027-09-14"}) {
            assertThatCode(() -> requireCollectionDate(LocalDate.parse(date), NOW)).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027-09-15T00:00", "9999-09-14T15:00"})
    void rejectsTriggersAfterTheLastParisDay(String at) {
        assertThatThrownBy(() -> requirePlanTime(LocalDateTime.parse(at), NOW))
                .hasMessage("J3_PLAN_TIME_OUT_OF_RANGE");
    }

    @Test void acceptsTheLastMinuteOfTheLastDay() {
        assertThatCode(() -> requirePlanTime(LocalDateTime.parse("2027-09-14T23:59"), NOW))
                .doesNotThrowAnyException();
    }

    @Test void advancesAtParisMidnightEvenWhenUtcIsStillThePreviousDay() {
        assertThat(maximumDate(Instant.parse("2026-09-13T21:59:59Z"))).isEqualTo(LocalDate.parse("2027-09-13"));
        assertThat(maximumDate(Instant.parse("2026-09-13T22:00:00Z"))).isEqualTo(LocalDate.parse("2027-09-14"));
    }

    @Test void twelveCalendarMonthsAccountForLeapYears() {
        assertThat(maximumDate(Instant.parse("2027-03-01T12:00:00Z"))).isEqualTo(LocalDate.parse("2028-03-01"));
        assertThat(maximumDate(Instant.parse("2028-02-29T12:00:00Z"))).isEqualTo(LocalDate.parse("2029-02-28"));
        assertThatCode(() -> requirePlanTime(LocalDateTime.parse("2028-02-29T10:00"),
                Instant.parse("2027-03-01T12:00:00Z"))).doesNotThrowAnyException();
    }

    @Test void leavesFutureInstantValidationToTheDstAwareLedger() {
        Instant betweenOccurrences = Instant.parse("2026-10-25T00:45:00Z");
        LocalDateTime secondOccurrence = LocalDateTime.parse("2026-10-25T02:30");
        assertThatCode(() -> requirePlanTime(secondOccurrence, betweenOccurrences)).doesNotThrowAnyException();
        assertThat(J3AutomationData.resolveOneShot(secondOccurrence, java.time.ZoneOffset.ofHours(1)))
                .isAfter(betweenOccurrences);
    }
}
