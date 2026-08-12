package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SofascorePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsRemainSafeForManualJ3() {
        SofascoreProperties properties = new SofascoreProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMaximumConcurrency()).isEqualTo(1);
        assertThat(properties.getMinimumDelay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.isAutomaticRefreshEnabled()).isFalse();
        assertThat(properties.isLivePollingEnabled()).isFalse();
        assertThat(properties.getAllowedEndpoints()).isEmpty();
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsUnsafeConcurrencyAndDelay() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setMaximumConcurrency(2);
        properties.setMinimumDelay(Duration.ofSeconds(1));

        var violations = validator.validate(properties);

        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(violations)
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("maximumConcurrency"));
        assertThat(violations)
                .anyMatch(violation -> violation.getMessage().contains("minimum-delay"));
    }
}
