package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.application.network.J3ProviderQualificationPolicy;
import com.bettingproject.sofascorelocal.application.network.J4EventDetailsQualificationPolicy;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SofascorePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EnvironmentBindingConfiguration.class);

    @Test
    void defaultsRemainSafeForManualJ3() {
        SofascoreProperties properties = new SofascoreProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isJ3QualificationEnabled()).isFalse();
        assertThat(properties.isJ4EventDetailsQualificationEnabled()).isFalse();
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
    void bindsDocumentedJ3EnvironmentKeysThroughApplicationYaml() {
        contextRunner
                .withPropertyValues(
                        "SOFASCORE_ENABLED=true",
                        "SOFASCORE_J3_QUALIFICATION_ENABLED=true",
                        "SOFASCORE_BASE_URL="
                                + ScheduledEventsProviderPageRequest.EXPECTED_ORIGIN,
                        "SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();

                    SofascoreProperties properties = context.getBean(
                            SofascoreProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isJ3QualificationEnabled()).isTrue();
                    assertThat(properties.getBaseUrl())
                            .isEqualTo(ScheduledEventsProviderPageRequest.EXPECTED_ORIGIN);
                    assertThat(properties.getAllowedEndpoints())
                            .containsExactly(SofascoreEndpointType.SCHEDULED_EVENTS);

                    J3ProviderQualificationPolicy qualificationPolicy = context.getBean(
                            J3ProviderQualificationPolicy.class);
                    assertThat(qualificationPolicy.snapshot().available()).isTrue();
                });
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

    @Test
    void bindsTheDisabledByDefaultJ4EventDetailsQualificationKeys() {
        contextRunner
                .withPropertyValues(
                        "SOFASCORE_ENABLED=true",
                        "SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true",
                        "SOFASCORE_BASE_URL=" + EventDetailsProviderRequest.EXPECTED_ORIGIN,
                        "SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    SofascoreProperties properties = context.getBean(
                            SofascoreProperties.class);
                    assertThat(properties.isJ4EventDetailsQualificationEnabled()).isTrue();
                    assertThat(properties.isJ3QualificationEnabled()).isFalse();
                    assertThat(properties.getAllowedEndpoints())
                            .containsExactly(SofascoreEndpointType.EVENT_DETAILS);
                    assertThat(context.getBean(J4EventDetailsQualificationPolicy.class)
                            .snapshot().available()).isTrue();
                });
    }

    @Test
    void rejectsExpandedOrConcurrentJ4ProviderQualification() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setAllowedEndpoints(java.util.Set.of(
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.SCHEDULED_EVENTS));

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getMessage().contains("J4 EVENT_DETAILS"));

        properties.setAllowedEndpoints(java.util.Set.of(SofascoreEndpointType.EVENT_DETAILS));
        properties.setJ3QualificationEnabled(true);
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getMessage().contains("mutually exclusive"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SofascoreProperties.class)
    @Import({J3ProviderQualificationPolicy.class, J4EventDetailsQualificationPolicy.class})
    static class EnvironmentBindingConfiguration {
    }
}
