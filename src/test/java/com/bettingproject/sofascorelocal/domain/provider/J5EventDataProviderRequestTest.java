package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J5EventDataProviderRequestTest {

    private static final URI ORIGIN = URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN);

    @Test
    void buildsOnlyTheThreeExactEventUrisAndCanonicalKeys() {
        assertThat(request(SofascoreEndpointType.EVENT_STATISTICS).targetUri().toString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16391135/statistics");
        assertThat(request(SofascoreEndpointType.EVENT_INCIDENTS).targetUri().toString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16391135/incidents");
        assertThat(request(SofascoreEndpointType.EVENT_LINEUPS).targetUri().toString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16391135/lineups");
        assertThat(request(SofascoreEndpointType.EVENT_INCIDENTS).requestKey())
                .isEqualTo("EVENT_INCIDENTS|eventId=16391135");
    }

    @Test
    void rejectsAnyOtherEndpointOriginOrUnboundedIdentifier() {
        assertThatThrownBy(() -> new J5EventDataProviderRequest(
                ORIGIN, 16391135L, SofascoreEndpointType.EVENT_DETAILS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new J5EventDataProviderRequest(
                URI.create("https://sofascore.com"),
                16391135L,
                SofascoreEndpointType.EVENT_STATISTICS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new J5EventDataProviderRequest(
                ORIGIN, 0L, SofascoreEndpointType.EVENT_STATISTICS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static J5EventDataProviderRequest request(SofascoreEndpointType endpoint) {
        return new J5EventDataProviderRequest(ORIGIN, 16391135L, endpoint);
    }
}
