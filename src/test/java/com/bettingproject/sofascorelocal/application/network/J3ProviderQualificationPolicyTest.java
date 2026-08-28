package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
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

class J3ProviderQualificationPolicyTest {

    private static final String WORKER_START_CLASS =
            "com.bettingproject.sofascorelocal.provider.playwright.worker."
                    + "ProviderPlaywrightWorkerMain";

    @TempDir
    Path temporaryDirectory;

    @Test
    void staysBlockedByDefaultWithNoProviderOriginExposed() {
        var snapshot = new J3ProviderQualificationPolicy(
                new SofascoreProperties()).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.providerOrigin()).isNull();
        assertThat(snapshot.blockers()).contains(
                "J3_QUALIFICATION_DISABLED",
                "CONNECTOR_DISABLED",
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID",
                "PROVIDER_ORIGIN_NOT_EXACT");
    }

    @Test
    void opensOnlyAfterTheConnectorPlaywrightArtifactAndAllowlistAreValid()
            throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        ProviderPlaywrightProperties playwright = configuredPlaywright("j3-worker.jar");

        var snapshot = new J3ProviderQualificationPolicy(properties, playwright).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin()).hasToString("https://www.sofascore.com");
        assertThat(snapshot.blockers()).isEmpty();
    }

    @Test
    void rejectsAPathPortOrAdditionalLogicalFamily() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com/api");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS));

        var snapshot = new J3ProviderQualificationPolicy(
                properties, configuredPlaywright("invalid-origin-worker.jar")).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).containsExactly(
                "QUALIFICATION_ENDPOINTS_NOT_EXACTLY_ALLOWED",
                "PROVIDER_ORIGIN_NOT_EXACT");
    }

    @Test
    void acceptsJ3InsideTheExactJ3J4PhaseTwoAndJ5EndpointUnion() throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));

        var snapshot = new J3ProviderQualificationPolicy(
                properties, configuredPlaywright("combined-worker.jar")).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(properties.isJ3QualificationConfigurationSafe()).isTrue();
        assertThat(properties.isCombinedJ3J4SelectionSafe()).isTrue();
    }

    @Test
    void blocksJ4PhaseOneFromSharingTheJ3SessionEvenWithAnExactUnion()
            throws Exception {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.EVENT_DETAILS));

        assertThat(new J3ProviderQualificationPolicy(
                properties, configuredPlaywright("phase-one-worker.jar"))
                .snapshot().blockers())
                .contains("J4_PHASE_1_CANNOT_SHARE_J3_SESSION");
    }

    @Test
    void neverExposesAClaimWhenPlaywrightIsDisabledOrTheWorkerIsMissing() {
        SofascoreProperties properties = exactJ3Configuration();
        ProviderPlaywrightProperties disabled = new ProviderPlaywrightProperties();

        var disabledSnapshot = new J3ProviderQualificationPolicy(
                properties, disabled).snapshot();

        assertThat(disabledSnapshot.available()).isFalse();
        assertThat(disabledSnapshot.providerOrigin()).isNull();
        assertThat(disabledSnapshot.blockers()).contains(
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");

        ProviderPlaywrightProperties missingWorker = new ProviderPlaywrightProperties();
        missingWorker.setEnabled(true);
        missingWorker.setWorkerJar(temporaryDirectory.resolve("missing-worker.jar"));

        var missingSnapshot = new J3ProviderQualificationPolicy(
                properties, missingWorker).snapshot();

        assertThat(missingSnapshot.available()).isFalse();
        assertThat(missingSnapshot.providerOrigin()).isNull();
        assertThat(missingSnapshot.blockers())
                .contains("PLAYWRIGHT_WORKER_ARTIFACT_INVALID")
                .doesNotContain("PLAYWRIGHT_RUNTIME_DISABLED");
    }

    @Test
    void rejectsAnUnsafeOrLoopbackRuntimeBeforeAProviderClaim() throws Exception {
        SofascoreProperties properties = exactJ3Configuration();
        ProviderPlaywrightProperties playwright = configuredPlaywright(
                "unsafe-worker.jar");
        playwright.setMaximumHeapMib(63);
        playwright.setLoopbackQualification(true);
        playwright.setLoopbackOrigin("http://127.0.0.1:49152");

        var snapshot = new J3ProviderQualificationPolicy(
                properties, playwright).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.providerOrigin()).isNull();
        assertThat(snapshot.blockers()).contains(
                "PLAYWRIGHT_CONFIGURATION_UNSAFE",
                "PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE");
    }

    @Test
    void rejectsAReadableJarThatDoesNotDeclareTheExactWorkerMainClass()
            throws Exception {
        SofascoreProperties properties = exactJ3Configuration();
        ProviderPlaywrightProperties playwright = new ProviderPlaywrightProperties();
        playwright.setEnabled(true);
        Path wrongJar = temporaryDirectory.resolve("wrong-worker.jar");
        writeWorkerJar(wrongJar, "example.WrongMain");
        playwright.setWorkerJar(wrongJar);

        var snapshot = new J3ProviderQualificationPolicy(
                properties, playwright).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.providerOrigin()).isNull();
        assertThat(snapshot.blockers())
                .containsExactly("PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
    }

    @Test
    void localImportIgnoresOnlyPlaywrightReadinessBlockers() {
        SofascoreProperties properties = exactJ3Configuration();
        var policy = new J3ProviderQualificationPolicy(
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

    private SofascoreProperties exactJ3Configuration() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        return properties;
    }

    private ProviderPlaywrightProperties configuredPlaywright(String fileName)
            throws IOException {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        Path worker = temporaryDirectory.resolve(fileName);
        writeWorkerJar(worker, WORKER_START_CLASS);
        properties.setWorkerJar(worker);
        return properties;
    }

    private static void writeWorkerJar(Path destination, String startClass)
            throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Start-Class", startClass);
        try (JarOutputStream ignored = new JarOutputStream(
                java.nio.file.Files.newOutputStream(destination), manifest)) {
            // The policy authenticates the executable manifest before any claim is exposed.
        }
    }
}
