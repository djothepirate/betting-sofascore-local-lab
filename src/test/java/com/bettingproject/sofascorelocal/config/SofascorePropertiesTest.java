package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.application.network.J3ProviderQualificationPolicy;
import com.bettingproject.sofascorelocal.application.network.J4EventDetailsQualificationPolicy;
import com.bettingproject.sofascorelocal.application.network.J4EventDetailsPhase2QualificationPolicy;
import com.bettingproject.sofascorelocal.application.network.J5RealQualificationPolicy;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
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
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SofascorePropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // A locally armed J3/J4/J5 campaign must not override the scenario under test.
            .withInitializer(context -> context.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EnvironmentBindingConfiguration.class);

    @Test
    void defaultsRemainSafeForManualJ3() {
        SofascoreProperties properties = new SofascoreProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isJ3QualificationEnabled()).isFalse();
        assertThat(properties.isJ4EventDetailsQualificationEnabled()).isFalse();
        assertThat(properties.isJ4EventDetailsPhase2Enabled()).isFalse();
        assertThat(properties.isJ5EventDataQualificationEnabled()).isFalse();
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
                .withSystemProperties(
                        "SOFASCORE_ENABLED=true",
                        "SOFASCORE_J3_QUALIFICATION_ENABLED=true",
                        "SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false",
                        "SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false",
                        "SOFASCORE_BASE_URL="
                                + ScheduledEventsProviderPageRequest.EXPECTED_ORIGIN,
                        "SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(System.getProperty("SOFASCORE_J3_QUALIFICATION_ENABLED"))
                            .as("isolated J3 system property")
                            .isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty(
                            "SOFASCORE_J3_QUALIFICATION_ENABLED"))
                            .as("resolved J3 placeholder input")
                            .isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty(
                            "sofascore.j3-qualification-enabled"))
                            .as("bound J3 configuration property")
                            .isEqualTo("true");

                    SofascoreProperties properties = context.getBean(
                            SofascoreProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isJ3QualificationEnabled()).isTrue();
                    assertThat(properties.isJ4EventDetailsQualificationEnabled()).isFalse();
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
                .withSystemProperties(
                        "SOFASCORE_ENABLED=true",
                        "SOFASCORE_J3_QUALIFICATION_ENABLED=false",
                        "SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true",
                        "SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false",
                        "SOFASCORE_BASE_URL=" + EventDetailsProviderRequest.EXPECTED_ORIGIN,
                        "SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    SofascoreProperties properties = context.getBean(
                            SofascoreProperties.class);
                    assertThat(properties.isJ4EventDetailsQualificationEnabled()).isTrue();
                    assertThat(properties.isJ4EventDetailsPhase2Enabled()).isFalse();
                    assertThat(properties.isJ3QualificationEnabled()).isFalse();
                    assertThat(properties.getAllowedEndpoints())
                            .containsExactly(SofascoreEndpointType.EVENT_DETAILS);
                    assertThat(context.getBean(J4EventDetailsQualificationPolicy.class)
                            .snapshot().available()).isTrue();
                });
    }

    @Test
    void bindsTheDedicatedPhaseTwoOptInWithoutMakingPhaseOneAvailable() {
        contextRunner
                .withSystemProperties(
                        "SOFASCORE_ENABLED=true",
                        "SOFASCORE_J3_QUALIFICATION_ENABLED=false",
                        "SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true",
                        "SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true",
                        "SOFASCORE_BASE_URL=" + EventDetailsProviderRequest.EXPECTED_ORIGIN,
                        "SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    SofascoreProperties properties = context.getBean(SofascoreProperties.class);
                    assertThat(properties.isJ4EventDetailsPhase2Enabled()).isTrue();
                    assertThat(context.getBean(J4EventDetailsQualificationPolicy.class)
                            .snapshot().available()).isFalse();
                    assertThat(context.getBean(J4EventDetailsPhase2QualificationPolicy.class)
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

    @Test
    void rejectsPhaseTwoWithoutTheMainJ4QualificationOptIn() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setJ4EventDetailsPhase2Enabled(true);

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getMessage().contains("phase 2"));
    }

    @Test
    void bindsOnlyTheDocumentedJ5RealQualificationEnvironmentKeys() {
        contextRunner
                .withSystemProperties(
                        "SOFASCORE_ENABLED=true",
                        "SOFASCORE_J3_QUALIFICATION_ENABLED=false",
                        "SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false",
                        "SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false",
                        "SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true",
                        "SOFASCORE_BASE_URL=" + EventDetailsProviderRequest.EXPECTED_ORIGIN,
                        "SOFASCORE_ALLOWED_ENDPOINTS="
                                + "EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    SofascoreProperties properties = context.getBean(SofascoreProperties.class);
                    assertThat(properties.isJ5EventDataQualificationEnabled()).isTrue();
                    assertThat(properties.isJ3QualificationEnabled()).isFalse();
                    assertThat(properties.isJ4EventDetailsQualificationEnabled()).isFalse();
                    assertThat(properties.isJ4EventDetailsPhase2Enabled()).isFalse();
                    assertThat(properties.getAllowedEndpoints())
                            .containsExactlyInAnyOrderElementsOf(
                                    J5EventDataProviderRequest.ALLOWED_ENDPOINTS);
                    assertThat(context.getBean(J5RealQualificationPolicy.class)
                            .snapshot().available()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SofascoreProperties.class)
    @Import({
            J3ProviderQualificationPolicy.class,
            J4EventDetailsQualificationPolicy.class,
            J4EventDetailsPhase2QualificationPolicy.class,
            J5RealQualificationPolicy.class})
    static class EnvironmentBindingConfiguration {
    }
}
