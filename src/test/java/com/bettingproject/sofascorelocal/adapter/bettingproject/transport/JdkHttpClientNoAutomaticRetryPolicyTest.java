package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;

class JdkHttpClientNoAutomaticRetryPolicyTest {

    @Test
    void provesTheThreeExactPropertiesWereJvmStartupArguments() {
        assertThat(ManagementFactory.getRuntimeMXBean().getInputArguments())
                .contains(
                        "-Djdk.httpclient.disableRetryConnect=true",
                        "-Djdk.httpclient.redirects.retrylimit=1",
                        "-Djdk.httpclient.enableAllMethodRetry=false");
        assertThat(JdkHttpClientNoAutomaticRetryPolicy.startupObservationSatisfied())
                .isTrue();

        JdkHttpClientNoAutomaticRetryPolicy.requireSatisfiedFromStartupAndNow();
    }
}
