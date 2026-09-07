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
        assertThat(properties.getQualifiedMatchCapacity()).isEqualTo(1);
        assertThat(properties.getPostgresContainer()).isEqualTo("betting-sofascore-local-lab-postgres");
        assertThat(properties.getDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(properties.getRequestEnvelope()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getProcessingEnvelope()).isEqualTo(Duration.ofSeconds(1));
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
    @CsvSource({"2,3000ms", "3,750ms"})
    void eclipseCapacitySettingsBindTogetherAndPassAdmissionWithoutEnablingNetwork(int matches, String request) throws Exception {
        var properties = bind(Map.of(
                "SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY", String.valueOf(matches),
                "SOFASCORE_LIVE_REQUEST_ENVELOPE", request,
                "SOFASCORE_LIVE_PROCESSING_ENVELOPE", "1000ms",
                "SOFASCORE_LIVE_QUALIFICATION_SHA256", "b".repeat(64)));
        assertThat(properties.getQualifiedMatchCapacity()).isEqualTo(matches);
        assertThat(properties.getRequestEnvelope().toMillis()).isEqualTo(matches == 2 ? 3000 : 750);
        assertThat(properties.getProcessingEnvelope()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.getQualificationSha256()).isEqualTo("b".repeat(64));
        assertThat(properties.isEnabled()).isFalse();
        new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE).admit(matches);
    }

    @Test
    void raisingOnlyTheCapacityDoesNotInventItsQualification() throws Exception {
        var properties = bind(Map.of("SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY", "3"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
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
}
