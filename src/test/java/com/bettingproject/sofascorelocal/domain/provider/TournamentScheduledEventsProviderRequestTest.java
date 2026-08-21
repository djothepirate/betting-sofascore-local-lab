package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TournamentScheduledEventsProviderRequestTest {

    private static final URI ORIGIN = URI.create(
            TournamentScheduledEventsProviderRequest.EXPECTED_ORIGIN);
    private static final LocalDate DATE = LocalDate.parse("2026-08-18");

    @Test
    void buildsTheExactNumericTournamentPathAndCanonicalDateIdKey() {
        var request = new TournamentScheduledEventsProviderRequest(ORIGIN, DATE, 7L);

        assertThat(request.endpointType())
                .isEqualTo(SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS);
        assertThat(request.requestKey()).isEqualTo(
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7");
        assertThat(request.targetUri()).hasToString(
                "https://www.sofascore.com/api/v1/unique-tournament/7/"
                        + "scheduled-events/2026-08-18");
        assertThat(request.targetUri().getRawQuery()).isNull();
        assertThat(request.targetUri().getRawUserInfo()).isNull();
        assertThat(request.targetUri().getPort()).isEqualTo(-1);
    }

    @Test
    void acceptsTheWholePositiveLongRangeButRejectsZeroAndNegativeIds() {
        assertThat(new TournamentScheduledEventsProviderRequest(
                ORIGIN,
                DATE,
                Long.MAX_VALUE).targetUri()).hasToString(
                        "https://www.sofascore.com/api/v1/unique-tournament/"
                                + Long.MAX_VALUE + "/scheduled-events/2026-08-18");

        assertThatThrownBy(() -> request(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> request(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsEveryOriginAliasDecorationAndNonHttpsScheme() {
        assertRejectedOrigin("http://www.sofascore.com");
        assertRejectedOrigin("https://sofascore.com");
        assertRejectedOrigin("https://www.sofascore.com:443");
        assertRejectedOrigin("https://operator@www.sofascore.com");
        assertRejectedOrigin("https://www.sofascore.com/api/v1");
        assertRejectedOrigin("https://www.sofascore.com?date=2026-08-18");
        assertRejectedOrigin("https://www.sofascore.com#fragment");
    }

    private static TournamentScheduledEventsProviderRequest request(long id) {
        return new TournamentScheduledEventsProviderRequest(ORIGIN, DATE, id);
    }

    private static void assertRejectedOrigin(String value) {
        assertThatThrownBy(() -> new TournamentScheduledEventsProviderRequest(
                URI.create(value),
                DATE,
                7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly");
    }
}
