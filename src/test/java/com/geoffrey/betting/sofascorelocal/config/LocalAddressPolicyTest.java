package com.geoffrey.betting.sofascorelocal.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAddressPolicyTest {

    @Test
    void acceptsIpv4Loopback() {
        assertThatCode(() -> LocalAddressPolicy.requireLoopback("127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsLocalhost() {
        assertThatCode(() -> LocalAddressPolicy.requireLoopback("localhost"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWildcardAddress() {
        assertThatThrownBy(() -> LocalAddressPolicy.requireLoopback("0.0.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }
}
