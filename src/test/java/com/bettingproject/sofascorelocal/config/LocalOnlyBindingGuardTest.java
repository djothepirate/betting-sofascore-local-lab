package com.bettingproject.sofascorelocal.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalOnlyBindingGuardTest {

    @Test
    void validatesTheBindingBeforeRegularBeansAreCreated() {
        LocalOnlyBindingGuard guard = new LocalOnlyBindingGuard();
        guard.setEnvironment(new MockEnvironment().withProperty("server.address", "127.0.0.1"));

        assertThatCode(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWildcardBindingDuringContextPreparation() {
        LocalOnlyBindingGuard guard = new LocalOnlyBindingGuard();
        guard.setEnvironment(new MockEnvironment().withProperty("server.address", "0.0.0.0"));

        assertThatThrownBy(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }
}
