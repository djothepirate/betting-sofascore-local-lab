package com.bettingproject.sofascorelocal.adapter.sofascore;

import com.bettingproject.sofascorelocal.application.ConnectorGate;
import com.bettingproject.sofascorelocal.application.NetworkAccessDisabledException;
import com.bettingproject.sofascorelocal.domain.provider.ProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledSofascoreDataProviderTest {

    @Test
    void refusesEveryProviderRequestAtJ1() {
        DisabledSofascoreDataProvider provider =
                new DisabledSofascoreDataProvider(new ConnectorGate());
        ProviderRequest request = new ProviderRequest(
                SofascoreEndpointType.EVENT_DETAILS,
                Map.of("eventId", "fixture-only"));

        assertThatThrownBy(() -> provider.load(request))
                .isInstanceOf(NetworkAccessDisabledException.class)
                .hasMessageContaining("NETWORK_CALLS_NOT_IMPLEMENTED_J1");
    }
}
