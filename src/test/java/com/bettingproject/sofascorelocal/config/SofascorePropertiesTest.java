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
        assertThat(properties.isJ3QualificationEnabled()).isFalse();
        assertThat(properties.getMaximumConcurrency()).isEqualTo(1);
        assertThat(properties.getMinimumDelay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
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

    @Test
    void rejectsAnIncompleteOrExpandedQualificationOptIn() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setJ3QualificationEnabled(true);

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getMessage().contains("J3 qualification"));

        properties.setEnabled(true);
        properties.setAllowedEndpoints(java.util.Set.of(
                com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.SCHEDULED_EVENTS));

        assertThat(validator.validate(properties)).isEmpty();
    }
}
