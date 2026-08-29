package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.LongFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaywrightProviderRequestTest {

    @Test
    void exposesOnlyBoundedEventDetailsScalarsWithoutAnIpcUri() {
        PlaywrightProviderRequest request = PlaywrightProviderRequest.eventDetails(16_386_245L);

        assertThat(request.endpoint()).isEqualTo(SofascoreEndpointType.EVENT_DETAILS);
        assertThat(request.date()).isNull();
        assertThat(request.page()).isZero();
        assertThat(request.uniqueTournamentId()).isZero();
        assertThat(request.eventId()).isEqualTo(16_386_245L);
        assertThat(PlaywrightProviderRequest.eventDetails(1).eventId()).isEqualTo(1);
        assertThat(PlaywrightProviderRequest.eventDetails(999_999_999L).eventId())
                .isEqualTo(999_999_999L);

        assertThatThrownBy(() -> PlaywrightProviderRequest.eventDetails(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid event-details request");
        assertThatThrownBy(() -> PlaywrightProviderRequest.eventDetails(1_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid event-details request");
    }

    @Test
    void exposesOnlyBoundedJ5EventDataScalarsWithoutAnIpcUri() {
        assertJ5Request(
                PlaywrightProviderRequest.eventStatistics(16_386_245L),
                SofascoreEndpointType.EVENT_STATISTICS);
        assertJ5Request(
                PlaywrightProviderRequest.eventIncidents(16_386_245L),
                SofascoreEndpointType.EVENT_INCIDENTS);
        assertJ5Request(
                PlaywrightProviderRequest.eventLineups(16_386_245L),
                SofascoreEndpointType.EVENT_LINEUPS);

        List<LongFunction<PlaywrightProviderRequest>> factories = List.of(
                PlaywrightProviderRequest::eventStatistics,
                PlaywrightProviderRequest::eventIncidents,
                PlaywrightProviderRequest::eventLineups);
        for (LongFunction<PlaywrightProviderRequest> factory : factories) {
            assertThat(factory.apply(1).eventId()).isEqualTo(1);
            assertThat(factory.apply(999_999_999L).eventId()).isEqualTo(999_999_999L);
            assertThatThrownBy(() -> factory.apply(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("invalid J5 event-data request");
            assertThatThrownBy(() -> factory.apply(1_000_000_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("invalid J5 event-data request");
        }
    }

    @Test
    void rejectsFieldsBelongingToAnotherEndpointFamily() {
        assertThatThrownBy(() -> new PlaywrightProviderRequest(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27),
                1,
                0,
                16_386_245L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid scheduled-events request");
        assertThatThrownBy(() -> new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_DETAILS,
                LocalDate.of(2026, 8, 27),
                0,
                0,
                16_386_245L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid event-details request");
        assertThatThrownBy(() -> new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_INCIDENTS,
                LocalDate.of(2026, 8, 27),
                0,
                0,
                16_386_245L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid J5 event-data request");
        assertThatThrownBy(() -> new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_LINEUPS,
                null,
                1,
                0,
                16_386_245L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid J5 event-data request");
        assertThatThrownBy(() -> new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_STATISTICS,
                null,
                0,
                119_880L,
                16_386_245L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid J5 event-data request");
    }

    private static void assertJ5Request(
            PlaywrightProviderRequest request,
            SofascoreEndpointType expectedEndpoint) {
        assertThat(request.endpoint()).isEqualTo(expectedEndpoint);
        assertThat(request.date()).isNull();
        assertThat(request.page()).isZero();
        assertThat(request.uniqueTournamentId()).isZero();
        assertThat(request.eventId()).isEqualTo(16_386_245L);
    }
}
