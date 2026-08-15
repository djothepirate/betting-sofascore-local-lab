package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDetailsProviderRequestTest {

    private static final URI ORIGIN = URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN);

    @Test
    void constructsOnlyTheTwoAuthorizedExactTargets() {
        var first = EventDetailsProviderRequest.phase1(
                ORIGIN,
                EventDetailsProviderRequest.SAINT_ETIENNE_CLERMONT_EVENT_ID);
        var second = EventDetailsProviderRequest.phase1(
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
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(ORIGIN, 16421053L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not authorized");
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(ORIGIN, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructsOneBoundedPhaseTwoTargetAndCanRefreshAFixedEvent() {
        var request = EventDetailsProviderRequest.phase2(ORIGIN, 17000001L);
        var fixedRefresh = EventDetailsProviderRequest.phase2(ORIGIN, 16386245L);

        assertThat(request.targetUri().toString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/17000001");
        assertThat(request.requestKey()).isEqualTo("EVENT_DETAILS|eventId=17000001");
        assertThat(fixedRefresh.requestKey()).isEqualTo("EVENT_DETAILS|eventId=16386245");
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase2(ORIGIN, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase2(ORIGIN, 1_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOriginAliasesPortsPathsQueriesAndNonHttpsSchemes() {
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(
                URI.create("http://www.sofascore.com"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(
                URI.create("https://sofascore.com"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(
                URI.create("https://www.sofascore.com:443"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(
                URI.create("https://www.sofascore.com/api"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventDetailsProviderRequest.phase1(
                URI.create("https://www.sofascore.com?event=16386245"), 16386245L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
