package com.bettingproject.sofascorelocal.domain.scheduledevents;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;

class J3AutomationDataTest {
    @Test void recurringGapUsesFirstValidInstantAndOverlapUsesFirstOccurrence() {
        assertThat(dailyOccurrence(LocalDate.parse("2027-03-28"),LocalTime.of(2,30)))
                .isEqualTo(Instant.parse("2027-03-28T01:00:00Z"));
        assertThat(dailyOccurrence(LocalDate.parse("2026-10-25"),LocalTime.of(2,30)))
                .isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
    }
    @Test void oneShotRejectsMissingTimeAndRequiresExplicitOverlapOffset() {
        assertThatThrownBy(()->resolveOneShot(LocalDateTime.parse("2027-03-28T02:30"),null)).hasMessage("J3_TIME_DOES_NOT_EXIST");
        var overlap=LocalDateTime.parse("2026-10-25T02:30");
        assertThatThrownBy(()->resolveOneShot(overlap,null)).hasMessage("J3_TIME_OFFSET_REQUIRED");
        assertThat(resolveOneShot(overlap,ZoneOffset.ofHours(2))).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
        assertThat(resolveOneShot(overlap,ZoneOffset.ofHours(1))).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"));
        assertThatThrownBy(()->resolveOneShot(overlap,ZoneOffset.UTC)).hasMessage("J3_TIME_OFFSET_INVALID");
    }
    @Test void dailyIdentitySurvivesConfigurationRevisionsWhileExplicitSchedulesAreVersioned() {
        var day=LocalDate.parse("2026-09-13");var at=Instant.parse("2026-09-13T06:00:00Z");
        var first=new Settings(true,Mode.DAILY_AT,LocalTime.of(9,0),1,at);
        var revised=new Settings(true,Mode.DAILY_AT,LocalTime.of(10,0),2,at);
        assertThat(timedKey(first,day)).isNotEqualTo(timedKey(revised,day));
        assertThat(dailyKey(day)).isEqualTo("DAILY|2026-09-13");
    }
}
