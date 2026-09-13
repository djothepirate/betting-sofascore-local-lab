package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledEventsProviderPageRequestTest {

    private static final LocalDate DATE = LocalDate.parse("2026-08-13");

    @Test
    void buildsTheQualifiedProviderPathForAnyDateWithinTheLocalPageLimit() {
        for (int page : new int[] {1, 5, 35}) {
            ScheduledEventsProviderPageRequest request =
                    new ScheduledEventsProviderPageRequest(
                            URI.create("https://www.sofascore.com"), DATE, page);

            assertThat(request.requestKey())
                    .isEqualTo("SCHEDULED_EVENTS|date=2026-08-13|page=" + page);
            assertThat(request.targetUri()).hasToString(
                    "https://www.sofascore.com/api/v1/sport/football/"
                            + "scheduled-tournaments/2026-08-13/page/" + page);
        }
    }

    @Test
    void rejectsEveryDecoratedOriginAndPageOutsideTheLocalLimit() {
        assertRejectedOrigin("http://www.sofascore.com");
        assertRejectedOrigin("https://sofascore.com");
        assertRejectedOrigin("https://www.sofascore.com:443");
        assertRejectedOrigin("https://user@www.sofascore.com");
        assertRejectedOrigin("https://www.sofascore.com/api");
        assertRejectedOrigin("https://www.sofascore.com?x=1");
        assertRejectedOrigin("https://www.sofascore.com#fragment");

        assertThatThrownBy(() -> request(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 35");
        assertThatThrownBy(() -> request(36))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 35");

        var anotherDate = new ScheduledEventsProviderPageRequest(
                URI.create("https://www.sofascore.com"),
                LocalDate.parse("2026-08-14"),
                1);
        assertThat(anotherDate.targetUri()).hasToString(
                "https://www.sofascore.com/api/v1/sport/football/"
                        + "scheduled-tournaments/2026-08-14/page/1");
    }

    private static ScheduledEventsProviderPageRequest request(int page) {
        return new ScheduledEventsProviderPageRequest(
                URI.create("https://www.sofascore.com"), DATE, page);
    }

    private static void assertRejectedOrigin(String origin) {
        assertThatThrownBy(() -> new ScheduledEventsProviderPageRequest(
                URI.create(origin), DATE, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly");
    }
}
