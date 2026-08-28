package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentEventDiscoveryQualificationPolicyTest {

    private static final String WORKER_START_CLASS =
            "com.bettingproject.sofascorelocal.provider.playwright.worker."
                    + "ProviderPlaywrightWorkerMain";

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsTheExactDedicatedConfigurationOnlyWithAValidatedPlaywrightRuntime()
            throws Exception {
        SofascoreProperties properties = configured();
        ProviderPlaywrightProperties playwright = configuredPlaywright(
                "tournament-worker.jar");

        var snapshot = new TournamentEventDiscoveryQualificationPolicy(
                properties, playwright).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        assertThat(properties.isTournamentEventDiscoveryConfigurationSafe()).isTrue();
    }

    @Test
    void blocksTheDefaultAndAnExpandedEndpointUnion() throws Exception {
        SofascoreProperties defaults = new SofascoreProperties();

        assertThat(new TournamentEventDiscoveryQualificationPolicy(defaults)
                .snapshot().blockers())
                .contains(
                        "TOURNAMENT_EVENT_DISCOVERY_DISABLED",
                        "J3_QUALIFICATION_DISABLED",
                        "CONNECTOR_DISABLED",
                        "PLAYWRIGHT_RUNTIME_DISABLED",
                        "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");

        SofascoreProperties expanded = configured();
        expanded.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS));
        assertThat(new TournamentEventDiscoveryQualificationPolicy(
                expanded, configuredPlaywright("expanded-worker.jar"))
                .snapshot().blockers())
                .contains("QUALIFICATION_ENDPOINTS_NOT_EXACTLY_ALLOWED");
    }

    @Test
    void neverAllowsPreparationOrClaimWithAnAbsentWorker() {
        SofascoreProperties properties = configured();
        ProviderPlaywrightProperties playwright = new ProviderPlaywrightProperties();
        playwright.setEnabled(true);
        playwright.setWorkerJar(temporaryDirectory.resolve("absent-worker.jar"));

        var snapshot = new TournamentEventDiscoveryQualificationPolicy(
                properties, playwright).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.providerOrigin()).isNull();
        assertThat(snapshot.blockers())
                .containsExactly("PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
    }

    @Test
    void rejectsLoopbackQualificationForABusinessTournamentClaim()
            throws Exception {
        SofascoreProperties properties = configured();
        ProviderPlaywrightProperties playwright = configuredPlaywright(
                "loopback-worker.jar");
        playwright.setLoopbackQualification(true);
        playwright.setLoopbackOrigin("http://127.0.0.1:49152");

        var snapshot = new TournamentEventDiscoveryQualificationPolicy(
                properties, playwright).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.providerOrigin()).isNull();
        assertThat(snapshot.blockers())
                .containsExactly("PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE");
    }

    @Test
    void localImportIgnoresOnlyPlaywrightReadinessBlockers() {
        SofascoreProperties properties = configured();
        var policy = new TournamentEventDiscoveryQualificationPolicy(
                properties, new ProviderPlaywrightProperties());

        assertThat(policy.snapshot().available()).isFalse();
        assertThat(policy.snapshot().blockers()).contains(
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
        assertThat(policy.localImportSnapshot().available()).isTrue();

        properties.setStoreRawPayloads(false);

        assertThat(policy.localImportSnapshot().available()).isFalse();
        assertThat(policy.localImportSnapshot().blockers())
                .containsExactly("RAW_SNAPSHOT_STORAGE_DISABLED");
    }

    private static SofascoreProperties configured() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setTournamentEventDiscoveryEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN + "/");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS));
        return properties;
    }

    private ProviderPlaywrightProperties configuredPlaywright(String fileName)
            throws IOException {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        Path worker = temporaryDirectory.resolve(fileName);
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Start-Class", WORKER_START_CLASS);
        try (JarOutputStream ignored = new JarOutputStream(
                java.nio.file.Files.newOutputStream(worker), manifest)) {
            // No class is loaded: the policy only authenticates the bounded artifact metadata.
        }
        properties.setWorkerJar(worker);
        return properties;
    }
}
