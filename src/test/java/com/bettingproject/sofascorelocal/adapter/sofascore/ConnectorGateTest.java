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
    void remainsCodeLockedDuringOfflineJ3PolicyDevelopment() {
        assertThat(gate.mode()).isEqualTo(ConnectorMode.LOCKED_OFFLINE_J3_POLICY);
        assertThatThrownBy(() -> gate.requireNetworkCallAllowed(SofascoreEndpointType.SCHEDULED_EVENTS))
                .isInstanceOf(NetworkAccessDisabledException.class)
                .hasMessageContaining("NETWORK_TRANSPORT_NOT_AUTHORIZED_J3");
    }
}
