package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

final class ProviderPlaywrightPolicyTestSupport {

    private static final String WORKER_START_CLASS =
            "com.bettingproject.sofascorelocal.provider.playwright.worker."
                    + "ProviderPlaywrightWorkerMain";

    private ProviderPlaywrightPolicyTestSupport() {
    }

    static ProviderPlaywrightProperties configured(Path directory, String fileName)
            throws IOException {
        ProviderPlaywrightProperties properties = new ProviderPlaywrightProperties();
        properties.setEnabled(true);
        Path worker = directory.resolve(fileName);
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Start-Class", WORKER_START_CLASS);
        try (JarOutputStream ignored = new JarOutputStream(
                Files.newOutputStream(worker), manifest)) {
            // The policy validates only the inert executable manifest in unit tests.
        }
        properties.setWorkerJar(worker);
        return properties;
    }
}
