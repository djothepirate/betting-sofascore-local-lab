package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.application.live.DockerLiveStorageCapacityProbe;
import com.bettingproject.sofascorelocal.application.live.LiveAdmissionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Actual YAML/environment binding with no application, process, database or browser startup. */
class LiveCampaignPropertiesTest {
    @TempDir Path temporary;

    @Test
    void defaultsKeepLiveDisabledAndRefuseTheStorageProbeWithoutAnExplicitExecutable() throws Exception {
        var properties = bind(Map.of());

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getPreparationPolicyVersion()).isEqualTo("live-v10");
        assertThat(properties.getQualifiedMatchCapacity()).isEqualTo(1);
        assertThat(properties.getPostgresContainer()).isEqualTo("betting-sofascore-local-lab-postgres");
        assertThat(properties.getDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(properties.getRequestEnvelope()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getProcessingEnvelope()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getGroupedV9().getQualificationSha256()).isEmpty();
        assertThat(properties.getGroupedV9().getEndpoints().values()).allSatisfy(envelope -> {
            assertThat(envelope.getRequestEnvelope()).isEqualTo(Duration.ofSeconds(10));
            assertThat(envelope.getProcessingEnvelope()).isEqualTo(Duration.ofSeconds(1));
        });
        assertThatThrownBy(properties::groupedAdmissionProfileV9)
                .hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.getGroupedV10().getQualificationSha256()).isEmpty();
        assertThat(properties.getGroupedV10().getEndpoints().values()).allSatisfy(envelope -> {
            assertThat(envelope.getRequestEnvelope()).isEqualTo(Duration.ofSeconds(10));
            assertThat(envelope.getProcessingEnvelope()).isEqualTo(Duration.ofSeconds(1));
        });
        assertThatThrownBy(properties::groupedAdmissionProfileV10)
                .hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.getDiskReserveBytes()).isEqualTo(1_073_741_824L);
        assertThatThrownBy(() -> new DockerLiveStorageCapacityProbe(properties).availableBytes())
                .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_STORAGE_PROBE_NOT_CONFIGURED");
    }

    @Test
    void eclipseEnvironmentNamesBindAnExactAbsolutePathWithSpacesAndContainerWithoutEnablingLive() throws Exception {
        Path executable = temporary.resolve("Docker Desktop").resolve("docker.exe").toAbsolutePath();
        var properties = bind(Map.of(
                "SOFASCORE_LIVE_DOCKER_EXECUTABLE", executable.toString(),
                "SOFASCORE_LIVE_POSTGRES_CONTAINER", "synthetic-postgres-058"));

        assertThat(properties.getDockerExecutable()).isEqualTo(executable);
        assertThat(properties.getPostgresContainer()).isEqualTo("synthetic-postgres-058");
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getQualifiedMatchCapacity()).isEqualTo(1);
    }

    @Test
    void liveOptInAndAnExplicitlyEmptyPathStillDoNotMakeTheProbeConfigured() throws Exception {
        var properties = bind(Map.of(
                "SOFASCORE_LIVE_ENABLED", "true",
                "SOFASCORE_LIVE_DOCKER_EXECUTABLE", ""));

        assertThat(properties.isEnabled()).isTrue();
        assertThatThrownBy(() -> new DockerLiveStorageCapacityProbe(properties).availableBytes())
                .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_STORAGE_PROBE_NOT_CONFIGURED");
    }

    @ParameterizedTest
    @CsvSource({"2,3000ms", "3,750ms", "4,1000ms", "5,1000ms", "10,1000ms", "25,1000ms"})
    void eclipseCapacitySettingsBindTogetherAndPassAdmissionWithoutEnablingNetwork(int matches, String request) throws Exception {
        var properties = bind(Map.of(
                "SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY", String.valueOf(matches),
                "SOFASCORE_LIVE_REQUEST_ENVELOPE", request,
                "SOFASCORE_LIVE_PROCESSING_ENVELOPE", "1000ms",
                "SOFASCORE_LIVE_QUALIFICATION_SHA256", "b".repeat(64)));
        assertThat(properties.getQualifiedMatchCapacity()).isEqualTo(matches);
        assertThat(properties.getRequestEnvelope().toMillis()).isEqualTo(matches == 2 ? 3000 : matches == 3 ? 750 : 1000);
        assertThat(properties.getProcessingEnvelope()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getQualificationSha256()).isEqualTo("b".repeat(64));
        assertThat(properties.isEnabled()).isFalse();
        new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(matches);
    }

    @Test
    void raisingOnlyTheCeilingAcceptsOneMatchAndDoesNotInventQualificationForAMultipleSelection() throws Exception {
        var properties = bind(Map.of("SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY", "25"));
        properties.validate();
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        policy.admit(1);
        assertThatThrownBy(() -> policy.admit(2)).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
    }

    private static LiveCampaignProperties bind(Map<String, Object> values) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, values));
        for (var source : new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bind("sofascore.live", Bindable.of(LiveCampaignProperties.class)).get();
    }

    @Test
    void historicQualificationNeverGrantsTheGroupedException() throws Exception {
        var properties = bind(Map.of("SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY", "20",
                "SOFASCORE_LIVE_QUALIFICATION_SHA256", "a".repeat(64),
                "SOFASCORE_LIVE_REQUEST_ENVELOPE", "1000ms"));
        assertThatThrownBy(properties::groupedAdmissionProfile).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThatThrownBy(properties::groupedAdmissionProfileV5).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
    }

    @Test
    void dedicatedProofBindsFamilyCostsWithoutLoweringHistoricalOrUnspecifiedEnvelopes() throws Exception {
        var properties = bind(Map.of("SOFASCORE_LIVE_GROUPED_QUALIFICATION_SHA256", "b".repeat(64),
                "SOFASCORE_LIVE_GROUPED_J4_REQUEST_ENVELOPE", "250ms",
                "SOFASCORE_LIVE_GROUPED_INCIDENTS_PROCESSING_ENVELOPE", "100ms"));
        var profile = properties.groupedAdmissionProfile();
        assertThat(profile.qualificationSha256()).isEqualTo("b".repeat(64));
        assertThat(profile.envelope(com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS)
                .requestEnvelope()).isEqualTo(Duration.ofMillis(250));
        assertThat(profile.envelope(com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS)
                .processingEnvelope()).isEqualTo(Duration.ofMillis(100));
        assertThat(profile.envelope(com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS)
                .requestEnvelope()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getRequestEnvelope()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void v4ProofAndCostsNeverImplicitlyQualifyTheFasterV5Transport() throws Exception {
        var properties = bind(Map.of("SOFASCORE_LIVE_GROUPED_QUALIFICATION_SHA256", "b".repeat(64),
                "SOFASCORE_LIVE_GROUPED_J4_REQUEST_ENVELOPE", "250ms"));
        assertThat(properties.groupedAdmissionProfile().policyVersion()).isEqualTo("live-v4");
        assertThatThrownBy(properties::groupedAdmissionProfileV5).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.getGroupedV5().getEndpoints().get(
                com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS).getRequestEnvelope())
                .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void v5DedicatedEnvironmentBindsItsProofAndEachFamilyWithoutChangingV4() throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY", "20");
        values.put("SOFASCORE_LIVE_GROUPED_V5_QUALIFICATION_SHA256", "c".repeat(64));
        for (String family : java.util.List.of("J4", "INCIDENTS", "STATISTICS", "LINEUPS")) {
            values.put("SOFASCORE_LIVE_GROUPED_V5_" + family + "_REQUEST_ENVELOPE", "400ms");
            values.put("SOFASCORE_LIVE_GROUPED_V5_" + family + "_PROCESSING_ENVELOPE", "100ms");
        }
        var properties = bind(values);
        var profile = properties.groupedAdmissionProfileV5();
        assertThat(profile.policyVersion()).isEqualTo("live-v5");
        assertThat(profile.qualificationSha256()).isEqualTo("c".repeat(64));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(100));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(300));
        assertThat(profile.interGroupDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(profile.endpointEnvelopes().values()).allSatisfy(cost -> {
            assertThat(cost.requestEnvelope()).isEqualTo(Duration.ofMillis(400));
            assertThat(cost.processingEnvelope()).isEqualTo(Duration.ofMillis(100));
        });
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(profile)).isEqualTo(20);
        assertThatThrownBy(properties::groupedAdmissionProfile).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.isEnabled()).isFalse();
        assertThatThrownBy(properties::groupedAdmissionProfileV6).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
    }

    @Test
    void v7EnvironmentBindsASeparateProofAndAllFourMinuteEnvelopesWithoutEnablingTransport() throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("SOFASCORE_LIVE_GROUPED_V7_QUALIFICATION_SHA256", "f".repeat(64));
        for (String family : java.util.List.of("J4", "INCIDENTS", "STATISTICS", "LINEUPS")) {
            values.put("SOFASCORE_LIVE_GROUPED_V7_" + family + "_REQUEST_ENVELOPE", "500ms");
            values.put("SOFASCORE_LIVE_GROUPED_V7_" + family + "_PROCESSING_ENVELOPE", "100ms");
        }
        var properties = bind(values);
        var profile = properties.groupedAdmissionProfileV7();
        assertThat(profile.policyVersion()).isEqualTo("live-v7");
        assertThat(profile.qualificationSha256()).isEqualTo("f".repeat(64));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.endpointEnvelopes().values()).allSatisfy(cost -> {
            assertThat(cost.requestEnvelope()).isEqualTo(Duration.ofMillis(500));
            assertThat(cost.processingEnvelope()).isEqualTo(Duration.ofMillis(100));
        });
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV7(profile)).isEqualTo(3);
        assertThatThrownBy(properties::groupedAdmissionProfileV6).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void v8EnvironmentBindsItsOwnProofAndTheQualifiedHalfSecondPressureProfileWithoutEnablingTransport() throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("SOFASCORE_LIVE_GROUPED_V8_QUALIFICATION_SHA256", "9".repeat(64));
        for (String family : java.util.List.of("J4", "INCIDENTS", "STATISTICS", "LINEUPS")) {
            values.put("SOFASCORE_LIVE_GROUPED_V8_" + family + "_REQUEST_ENVELOPE", "500ms");
            values.put("SOFASCORE_LIVE_GROUPED_V8_" + family + "_PROCESSING_ENVELOPE", "100ms");
        }
        var properties = bind(values);
        var profile = properties.groupedAdmissionProfileV8();
        assertThat(profile.policyVersion()).isEqualTo("live-v8");
        assertThat(profile.qualificationSha256()).isEqualTo("9".repeat(64));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.minimumRequestStartInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(profile.interGroupDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV8(profile)).isEqualTo(10);
        assertThatThrownBy(properties::groupedAdmissionProfileV7).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThatThrownBy(properties::groupedAdmissionProfileV9).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void v9EnvironmentBindsAnIndependentProofWhileKeepingTheV8ProfileUnavailableAndTransportDisabled() throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("SOFASCORE_LIVE_GROUPED_V9_QUALIFICATION_SHA256", "a".repeat(64));
        for (String family : java.util.List.of("J4", "INCIDENTS", "STATISTICS", "LINEUPS")) {
            values.put("SOFASCORE_LIVE_GROUPED_V9_" + family + "_REQUEST_ENVELOPE", "500ms");
            values.put("SOFASCORE_LIVE_GROUPED_V9_" + family + "_PROCESSING_ENVELOPE", "100ms");
        }

        var properties = bind(values);
        var profile = properties.groupedAdmissionProfileV9();

        assertThat(profile.policyVersion()).isEqualTo("live-v9");
        assertThat(profile.qualificationSha256()).isEqualTo("a".repeat(64));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.minimumRequestStartInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(profile.interGroupDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV9(profile)).isEqualTo(10);
        assertThatThrownBy(properties::groupedAdmissionProfileV8).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void v10EnvironmentBindsAnIndependentProofWhileKeepingTheV9ProfileUnavailableAndTransportDisabled() throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("SOFASCORE_LIVE_GROUPED_V10_QUALIFICATION_SHA256", "b".repeat(64));
        for (String family : java.util.List.of("J4", "INCIDENTS", "STATISTICS", "LINEUPS")) {
            values.put("SOFASCORE_LIVE_GROUPED_V10_" + family + "_REQUEST_ENVELOPE", "500ms");
            values.put("SOFASCORE_LIVE_GROUPED_V10_" + family + "_PROCESSING_ENVELOPE", "100ms");
        }

        var properties = bind(values);
        var profile = properties.groupedAdmissionProfileV10();

        // The V10 evidence and configuration are independent. The nested profile
        // retains V9's immutable scheduler shape so historic V9 evidence remains valid.
        assertThat(properties.getPreparationPolicyVersion()).isEqualTo("live-v10");
        assertThat(profile.policyVersion()).isEqualTo("live-v9");
        assertThat(profile.qualificationSha256()).isEqualTo("b".repeat(64));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.minimumRequestStartInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(profile.interGroupDelay()).isEqualTo(Duration.ofMillis(500));
        assertThatThrownBy(properties::groupedAdmissionProfileV9).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void v6HasSeparateBoundedCostsAndEvidenceWithoutInheritingV5OrEnablingTransport() {
        var properties = new LiveCampaignProperties();
        properties.getGroupedV5().setQualificationSha256("a".repeat(64));
        assertThatThrownBy(properties::groupedAdmissionProfileV6).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        properties.getGroupedV6().setQualificationSha256("d".repeat(64));
        properties.getGroupedV6().getEndpoints().values().forEach(cost -> {
            cost.setRequestEnvelope(Duration.ofMillis(400));
            cost.setProcessingEnvelope(Duration.ofMillis(100));
        });
        var profile = properties.groupedAdmissionProfileV6();
        assertThat(profile.policyVersion()).isEqualTo("live-v6");
        assertThat(profile.qualificationSha256()).isEqualTo("d".repeat(64));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(100));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(300));
        assertThat(profile.minimumRequestStartInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV6(profile)).isEqualTo(7);
        assertThat(properties.groupedAdmissionProfileV5().endpointEnvelopes().values()).allSatisfy(cost ->
                assertThat(cost.requestEnvelope()).isEqualTo(Duration.ofSeconds(10)));
        assertThat(properties.isEnabled()).isFalse();
    }
}
