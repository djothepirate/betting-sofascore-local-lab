package com.bettingproject.sofascorelocal.adapter.sofascore;

import com.bettingproject.sofascorelocal.application.ConnectorGate;
import com.bettingproject.sofascorelocal.application.NetworkAccessDisabledException;
import com.bettingproject.sofascorelocal.domain.provider.ConnectorMode;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorGateTest {

    private final ConnectorGate gate = new ConnectorGate();

    @Test
    void remainsCodeLockedDuringJ1() {
        assertThat(gate.mode()).isEqualTo(ConnectorMode.LOCKED_OFFLINE_J1);
        assertThatThrownBy(() -> gate.requireNetworkCallAllowed(SofascoreEndpointType.SCHEDULED_EVENTS))
                .isInstanceOf(NetworkAccessDisabledException.class)
                .hasMessageContaining("NETWORK_CALLS_NOT_IMPLEMENTED_J1");
    }
}
