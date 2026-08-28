package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
    }
}
