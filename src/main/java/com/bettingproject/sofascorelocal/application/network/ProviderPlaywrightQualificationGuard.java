package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

final class ProviderPlaywrightQualificationGuard {

    private static final int REQUIRED_JAVA_FEATURE = 25;
    private static final String EXPECTED_WORKER_START_CLASS =
            "com.bettingproject.sofascorelocal.provider.playwright.worker."
                    + "ProviderPlaywrightWorkerMain";

    private ProviderPlaywrightQualificationGuard() {
    }

    static void appendBlockers(
            ProviderPlaywrightProperties properties,
            List<String> blockers) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(blockers, "blockers");

        if (!properties.isEnabled()) {
            blockers.add("PLAYWRIGHT_RUNTIME_DISABLED");
        }
        if (!configurationIsSafe(properties)) {
            blockers.add("PLAYWRIGHT_CONFIGURATION_UNSAFE");
        }
        if (properties.isLoopbackQualification()) {
            blockers.add("PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE");
        }
        if (!workerArtifactIsUsable(properties.getWorkerJar())) {
            blockers.add("PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
        }
        if (!javaRuntimeIsUsable()) {
            blockers.add("PLAYWRIGHT_JAVA_RUNTIME_INVALID");
        }
    }

    private static boolean configurationIsSafe(ProviderPlaywrightProperties properties) {
        return properties.getMaximumHeapMib() >= 64
                && properties.getMaximumHeapMib() <= 512
                && properties.isSafeConfiguration();
    }

    private static boolean workerArtifactIsUsable(Path configured) {
        if (configured == null) {
            return false;
        }
        try {
            Path normalized = configured.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized)
                    || !Files.isReadable(normalized)
                    || !normalized.getFileName().toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".jar")) {
                return false;
            }
            try (JarFile jar = new JarFile(normalized.toFile(), false)) {
                return jar.getManifest() != null
                        && EXPECTED_WORKER_START_CLASS.equals(
                                jar.getManifest().getMainAttributes().getValue(
                                        new Attributes.Name("Start-Class")));
            }
        }
        catch (IOException | InvalidPathException | SecurityException exception) {
            return false;
        }
    }

    private static boolean javaRuntimeIsUsable() {
        if (Runtime.version().feature() != REQUIRED_JAVA_FEATURE) {
            return false;
        }
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return false;
        }
        try {
            String executable = System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT)
                    .contains("win") ? "java.exe" : "java";
            Path java = Path.of(javaHome, "bin", executable).toAbsolutePath().normalize();
            return Files.isRegularFile(java) && Files.isReadable(java);
        }
        catch (InvalidPathException | SecurityException exception) {
            return false;
        }
    }
}
