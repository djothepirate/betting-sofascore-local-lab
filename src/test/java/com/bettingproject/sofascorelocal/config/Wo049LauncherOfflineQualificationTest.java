package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts only a bounded PowerShell fixture exporter, never a Spring application or listener. */
@EnabledOnOs(OS.WINDOWS)
class Wo049LauncherOfflineQualificationTest {
    private static JsonNode fixture;
    private static Map<String, Object> values;

    @BeforeAll
    static void readActualPowerShellPreparationWithoutStartingApplication() throws Exception {
        Path pwsh = Path.of("C:/Program Files/PowerShell/7/pwsh.exe");
        assertThat(Files.isRegularFile(pwsh)).as("PowerShell 7 offline prerequisite").isTrue();
        ProcessBuilder builder = new ProcessBuilder(pwsh.toString(), "-NoLogo", "-NoProfile",
                "-NonInteractive", "-File", Path.of("scripts/wo049/Export-WO049SyntheticLaunchPreparation.ps1")
                .toAbsolutePath().toString()).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.toUpperCase(java.util.Locale.ROOT)
                .matches("^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|SPRING_.*|OPTIONAL.*|SOFASCORE_.*|BETTING_.*|POSTGRES_.*)$"));
        Process child = builder.start();
        CompletableFuture<byte[]> captured = CompletableFuture.supplyAsync(() -> {
            try { return child.getInputStream().readNBytes(65_537); }
            catch (java.io.IOException failure) { throw new IllegalStateException("WO049_CAPTURE_FAILED"); }
        });
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).as("offline fixture deadline").isTrue();
            assertThat(child.exitValue()).as("offline fixture exit code").isZero();
            byte[] bytes = captured.get(5, TimeUnit.SECONDS);
            assertThat(bytes.length).isLessThanOrEqualTo(65_536);
            fixture = JsonMapper.builder().build().readTree(new String(bytes, StandardCharsets.UTF_8));
            values = new LinkedHashMap<>();
            fixture.get("environment").properties().forEach(entry ->
                    values.put(entry.getKey(), entry.getValue().asString()));
        }
        finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertThat(child.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
            child.getInputStream().close();
            child.getOutputStream().close();
            child.getErrorStream().close();
        }
    }

    private static StandardEnvironment environment(Map<String, Object> overrides) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, overrides));
        for (var source : new YamlPropertySourceLoader().load("qualified-defaults",
                new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private static OptionalLocalPushProperties bind(Map<String, Object> overrides) throws Exception {
        return Binder.get(environment(overrides)).bind("optional-integration",
                Bindable.of(OptionalLocalPushProperties.class)).get();
    }

    @Test
    void actualPreparedNamesBindCompleteProviderDerivedMatrixAndAllConstraints() throws Exception {
        var properties = bind(values);
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getExecutionMode()).isEqualTo(OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        assertThat(properties.isRemoteDeliveryAuthorized()).isTrue();
        assertThat(properties.getReceiverQualification()).isEqualTo(OptionalLocalPushProperties.QualificationStatus.PASS);
        assertThat(properties.getSenderQualification()).isEqualTo(OptionalLocalPushProperties.QualificationStatus.PASS);
        assertThat(properties.getReceiverOrigin()).isEqualTo("https://127.0.0.1:8444");
        assertThat(properties.getProviderOwnerGo().getGoId()).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(properties.getProviderOwnerGo().getOwnerGoDocumentSha256()).isEqualTo("b".repeat(64));
        assertThat(properties.getMtls().getClientCertificateSha256()).isEqualTo("a".repeat(64));
        assertThat(properties.getMtls().getKeyStoreType()).isEqualTo("Windows-MY");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.isRuntimeActivationCoherent()).isTrue();
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            assertThat(validator.getValidator().validate(properties)).isEmpty();
        }
    }

    @Test
    void reproducesR1BadSuffixesAndUnboundOwnerGoWithoutAnyStart() throws Exception {
        Map<String, Object> broken = new LinkedHashMap<>(values);
        for (String suffix : new String[]{"EXECUTION_MODE", "REMOTE_DELIVERY_AUTHORIZED",
                "OFFICIAL_PERMISSION_STATUS", "RECEIVER_QUALIFICATION", "SENDER_QUALIFICATION",
                "RECEIVER_ORIGIN", "LOOPBACK_QUALIFICATION", "AUTOMATIC_RETRY_ENABLED",
                "CONNECT_TIMEOUT", "REQUEST_TIMEOUT"}) {
            Object value = broken.remove("OPTIONAL_INTEGRATION_" + suffix);
            broken.put("OPTIONAL_INTEGRATION_" + suffix.replace("_", ""), value);
        }
        for (var pair : Map.of("MTLS_CLIENT_CERTIFICATE_SHA256", "MTLS_CLIENTCERTIFICATESHA256",
                "MTLS_KEY_STORE_TYPE", "MTLS_KEYSTORETYPE", "PROVIDER_OWNER_GO_GO_ID", "PROVIDEROWNERGO_GOID",
                "PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256", "PROVIDEROWNERGO_OWNERGODOCUMENTSHA256").entrySet()) {
            broken.put("OPTIONAL_INTEGRATION_" + pair.getValue(), broken.remove("OPTIONAL_INTEGRATION_" + pair.getKey()));
        }
        var properties = bind(broken);
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getExecutionMode()).isEqualTo(OptionalLocalPushProperties.ExecutionMode.DISABLED);
        assertThat(properties.getReceiverOrigin()).isEmpty();
        assertThat(properties.getMtls().getClientCertificateSha256()).isEmpty();
        assertThat(properties.getProviderOwnerGo().isAbsent()).isTrue();
        assertThat(properties.isRuntimeActivationCoherent()).isFalse();
    }

    @Test
    void missingSafetyReferencesStayFailClosedInJavaIndependentlyOfPowerShellGuard() throws Exception {
        for (String suffix : new String[]{"PROVIDER_OWNER_GO_GO_ID", "PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256",
                "MTLS_CLIENT_CERTIFICATE_SHA256", "RECEIVER_QUALIFICATION", "SENDER_QUALIFICATION", "RECEIVER_ORIGIN"}) {
            var missing = new LinkedHashMap<>(values);
            missing.remove("OPTIONAL_INTEGRATION_" + suffix);
            assertThat(bind(missing).isRuntimeActivationCoherent()).as(suffix).isFalse();
        }
    }

    @Test
    void permissionSeparationAndIncompatibilityRemainUnchanged() throws Exception {
        for (String permission : new String[]{"NOT_EVIDENCED", "EVIDENCED_COMPATIBLE", "EVIDENCED_INCOMPATIBLE"}) {
            var changed = new LinkedHashMap<>(values);
            changed.put("OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS", permission);
            assertThat(bind(changed).isRuntimeActivationCoherent()).isEqualTo(!permission.equals("EVIDENCED_INCOMPATIBLE"));
        }
    }

    @Test
    void providerGatesLoopbackTimeoutsAndSecretFreeLoggingRemainBounded() throws Exception {
        var env = environment(values);
        var provider = Binder.get(env).bind("sofascore", Bindable.of(SofascoreProperties.class)).get();
        var playwright = Binder.get(env).bind("sofascore.playwright", Bindable.of(ProviderPlaywrightProperties.class)).get();
        assertThat(provider.isEnabled()).isFalse();
        assertThat(provider.isJ3QualificationEnabled()).isFalse();
        assertThat(provider.isJ4EventDetailsQualificationEnabled()).isFalse();
        assertThat(provider.isJ4EventDetailsPhase2Enabled()).isFalse();
        assertThat(provider.isJ5EventDataQualificationEnabled()).isFalse();
        assertThat(provider.isTournamentEventDiscoveryEnabled()).isFalse();
        assertThat(provider.isAutomaticNetworkActivityDisabled()).isTrue();
        assertThat(playwright.isEnabled()).isFalse();
        assertThat(playwright.isLoopbackQualification()).isFalse();
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(env.getProperty("server.port")).isEqualTo("8087");
        assertThat(env.getProperty("spring.config.import")).isEmpty();
        assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(bind(values).isAutomaticRetryDisabled()).isTrue();
    }

    @Test
    void stopProofMatchesIndependentGoldenBytesAndJavaDigest() throws Exception {
        String golden = "FORMAT=WO046_FAIL_CLOSED_TECHNICAL_STOP_V1\n"
                + "ACTOR=CODEX_LOCAL_AGENT\n"
                + "AUTHORITY=FROZEN_MANIFEST_STOP_AND_ONE_TIME_EXECUTION_BOUNDARY\n"
                + "GO_ID=00000000-0000-4000-8000-000000000001\n"
                + "OWNER_GO_DOCUMENT_SHA256=" + "b".repeat(64) + "\n"
                + "STOPPED_AT_UTC=2030-01-02T03:04:05.123456Z\n"
                + "REASON=LOCAL_LAB_START_CONFIGURATION_BINDING_REFUSED\n"
                + "ACTION=REVOKE_UNUSED_GO_AND_STOP_EXACT_OWNED_RESOURCES\n"
                + "PROVIDER_DERIVED_IMPORT_POSTS=0\n"
                + "NEW_GO_OR_RETRY_AUTHORIZED=NO\n";
        byte[] actual = Base64.getDecoder().decode(fixture.get("stopDocumentBase64").asString());
        assertThat(actual).containsExactly(golden.getBytes(StandardCharsets.UTF_8));
        assertThat(fixture.get("stopByteCount").asInt()).isEqualTo(476);
        assertThat(fixture.get("stopSha256").asString()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)))
                .isEqualTo("058022ed74ef13e2c9c51f69bd7b87bec612d33baf5f06cf5e8756a8e810d2c1");
    }

    @Test
    void preparationPreservesArgumentsWithoutLaunchingOrWriting() {
        assertThat(fixture.get("fixtureOnly").asBoolean()).isTrue();
        assertThat(fixture.get("processStarted").asBoolean()).isFalse();
        assertThat(fixture.get("filesWritten").asInt()).isZero();
        assertThat(fixture.get("connectionsOpened").asInt()).isZero();
        var arguments = fixture.get("argumentList");
        assertThat(arguments.size()).isEqualTo(6);
        assertThat(arguments.get(0).asString()).isEqualTo("-Dwo046.instance=00000000-0000-4000-8000-000000000002");
        assertThat(arguments.get(1).asString()).isEqualTo("-Djdk.httpclient.disableRetryConnect=true");
        assertThat(arguments.get(2).asString()).isEqualTo("-Djdk.httpclient.redirects.retrylimit=1");
        assertThat(arguments.get(3).asString()).isEqualTo("-Djdk.httpclient.enableAllMethodRetry=false");
        assertThat(arguments.get(4).asString()).isEqualTo("-jar");
        assertThat(arguments.get(5).asString()).endsWith("application with spaces.jar").doesNotContain("\"");
    }
}
