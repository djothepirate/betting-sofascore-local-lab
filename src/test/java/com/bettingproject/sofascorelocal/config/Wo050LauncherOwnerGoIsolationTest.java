package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Validation;
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
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline PowerShell environment scopes and Spring binding; no application, DB or socket. */
class Wo050LauncherOwnerGoIsolationTest {
    private static final String GO = "OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID";
    private static final String HASH = "OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256";
    private static final String[] LAUNCHERS = {"Start-Local.ps1", "Start-J3PlaywrightLocal.ps1",
            "Start-J4PlaywrightLocal.ps1", "Start-J5PlaywrightLocal.ps1",
            "Export-J8Benchmark.ps1", "Invoke-J6Retention.ps1"};

    private static OptionalLocalPushProperties bind(Map<String, Object> values) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, values));
        for (var source : new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bind("optional-integration", Bindable.of(OptionalLocalPushProperties.class)).get();
    }

    @Test
    void inheritedReferencesStillFailClosedWithoutLauncherSanitization() throws Exception {
        for (int mask = 0; mask < 4; mask++) {
            var values = new LinkedHashMap<String, Object>();
            values.put("OPTIONAL_INTEGRATION_ENABLED", "false");
            values.put("OPTIONAL_INTEGRATION_EXECUTION_MODE", "DISABLED");
            if ((mask & 1) != 0) values.put(GO, "00000000-0000-4000-8000-000000000050");
            if ((mask & 2) != 0) values.put(HASH, "b".repeat(64));
            var properties = bind(values);
            assertThat(properties.isRuntimeActivationCoherent()).as("ambient mask %s", mask).isEqualTo(mask == 0);
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(properties).isEmpty()).isEqualTo(mask == 0);
            }
        }
    }

    @Test
    void allSixLaunchersSaveAndClearBothCanonicalNames() throws Exception {
        for (String launcher : LAUNCHERS) {
            String script = Files.readString(Path.of("scripts", launcher));
            for (String name : new String[]{GO, HASH}) {
                assertThat(script).as(launcher).contains("'" + name + "'");
                assertThat(script.contains("$env:" + name + " = ''")
                        || script.contains("Set-ProcessEnvironment -Name '" + name + "' -Value ''")).isTrue();
            }
            assertThat(script).contains("finally", "SetEnvironmentVariable");
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void actualPowerShellIsolationRestoresParentAcrossAll72CasesAndBindsDisabled() throws Exception {
        Path pwsh = Path.of("C:/Program Files/PowerShell/7/pwsh.exe");
        assertThat(Files.isRegularFile(pwsh)).as("PowerShell 7 offline prerequisite").isTrue();
        var builder = new ProcessBuilder(pwsh.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                "-File", Path.of("scripts/wo050/Test-WO050LauncherIsolation.ps1").toAbsolutePath().toString())
                .redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.toUpperCase(Locale.ROOT).matches(
                "^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|SPRING_.*|OPTIONAL.*|SOFASCORE_.*|BETTING_.*|POSTGRES_.*)$"));
        Process child = builder.start();
        var captured = CompletableFuture.supplyAsync(() -> {
            try { return child.getInputStream().readNBytes(131_073); }
            catch (java.io.IOException failure) { throw new IllegalStateException("WO050_CAPTURE_FAILED"); }
        });
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).as("offline fixture deadline").isTrue();
            assertThat(child.exitValue()).as("offline fixture exit code").isZero();
            byte[] bytes = captured.get(5, TimeUnit.SECONDS);
            assertThat(bytes.length).isLessThanOrEqualTo(131_072);
            var cases = JsonMapper.builder().build().readTree(new String(bytes, StandardCharsets.UTF_8));
            assertThat(cases.size()).isEqualTo(72);
            var identities = new java.util.HashSet<String>();
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                for (var entry : cases) {
                    String identity = entry.get("script").asString() + ":" + entry.get("mask").asInt()
                            + ":" + entry.get("outcome").asString();
                    assertThat(identities.add(identity)).isTrue();
                    assertThat(entry.get("restored").asBoolean()).as(identity).isTrue();
                    Map<String, Object> values = new LinkedHashMap<>();
                    entry.get("environment").properties().forEach(value -> {
                        if (!value.getValue().isNull()) values.put(value.getKey(), value.getValue().asString());
                    });
                    var properties = bind(values);
                    assertThat(properties.isEnabled()).as(identity).isFalse();
                    assertThat(properties.isRemoteDeliveryAuthorized()).isFalse();
                    assertThat(properties.getProviderOwnerGo().isAbsent()).isTrue();
                    assertThat(properties.isRuntimeActivationCoherent()).isTrue();
                    assertThat(factory.getValidator().validate(properties)).isEmpty();
                }
            }
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
}
