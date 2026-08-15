package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDetailsProviderRequestTest {

    private static final URI ORIGIN = URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN);

    @Test
    void constructsOnlyTheTwoAuthorizedExactTargets() {
        var first = new EventDetailsProviderRequest(
                ORIGIN,
                EventDetailsProviderRequest.SAINT_ETIENNE_CLERMONT_EVENT_ID);
        var second = new EventDetailsProviderRequest(
                ORIGIN,
                EventDetailsProviderRequest.SEVILLA_RAYO_EVENT_ID);

        assertThat(first.targetUri().toString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16386245");
        assertThat(first.requestKey()).isEqualTo("EVENT_DETAILS|eventId=16386245");
        assertThat(second.targetUri().toString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16421052");
        assertThat(second.requestKey()).isEqualTo("EVENT_DETAILS|eventId=16421052");
    }

    @Test
    void rejectsEveryOtherEventBeforeAnUriCanBeResolved() {
        assertThatThrownBy(() -> new EventDetailsProviderRequest(ORIGIN, 16421053L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not authorized");
        assertThatThrownBy(() -> new EventDetailsProviderRequest(ORIGIN, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOriginAliasesPortsPathsQueriesAndNonHttpsSchemes() {
        assertThatThrownBy(() -> new EventDetailsProviderRequest(
                URI.create("http://www.sofascore.com"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventDetailsProviderRequest(
                URI.create("https://sofascore.com"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventDetailsProviderRequest(
                URI.create("https://www.sofascore.com:443"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventDetailsProviderRequest(
                URI.create("https://www.sofascore.com/api"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventDetailsProviderRequest(
                URI.create("https://www.sofascore.com?event=16386245"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
