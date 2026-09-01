package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPlaywrightPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsAreInertBoundedAndDoNotSelectAWorkerArtifact() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getWorkerJar()).isNull();
        assertThat(properties.getMaximumHeapMib()).isEqualTo(192);
        assertThat(properties.getStartupTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getGracefulCloseTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.isLoopbackQualification()).isFalse();
        assertThat(properties.getLoopbackOrigin()).isEmpty();
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void acceptsOnlyAnExplicitExactLoopbackOriginForLocalQualification() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setLoopbackQualification(true);
        properties.setLoopbackOrigin("http://127.0.0.1:49152");
        assertThat(validator.validate(properties)).isEmpty();

        properties.setLoopbackOrigin("https://www.sofascore.com");
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("http://localhost:49152");
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("http://127.0.0.1:49152/path");
        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void acceptsOnlyTheClosedTcpPortRangeBoundaries() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setLoopbackQualification(true);

        properties.setLoopbackOrigin("http://127.0.0.1");
        assertThat(properties.isSafeConfiguration()).isFalse();
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("http://127.0.0.1:0");
        assertThat(properties.isSafeConfiguration()).isFalse();
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("http://127.0.0.1:1");
        assertThat(properties.isSafeConfiguration()).isTrue();
        assertThat(validator.validate(properties)).isEmpty();

        properties.setLoopbackOrigin("http://127.0.0.1:65535");
        assertThat(properties.isSafeConfiguration()).isTrue();
        assertThat(validator.validate(properties)).isEmpty();

        properties.setLoopbackOrigin("http://127.0.0.1:65536");
        assertThat(properties.isSafeConfiguration()).isFalse();
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("safeConfiguration"));
    }

    @Test
    void refusesOriginOverridesOutsideQualificationAndUnboundedTimeouts() {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setLoopbackOrigin("http://127.0.0.1:49152");
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("");
        properties.setStartupTimeout(Duration.ofSeconds(61));
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setStartupTimeout(Duration.ofSeconds(30));
        properties.setRequestTimeout(Duration.ZERO);
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setRequestTimeout(Duration.ofSeconds(10));
        properties.setGracefulCloseTimeout(Duration.ofMillis(5_001));
        assertThat(validator.validate(properties)).isNotEmpty();
    }
}
