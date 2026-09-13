package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invalid configuration must fail before starting any process. */
class DockerLiveStorageCapacityProbeTest {
    @TempDir Path temporary;

    @Test
    void aMissingExecutableIsReportedAsConfigurationNotProviderOptIn() {
        var properties = new LiveCampaignProperties();
        assertNotConfigured(properties);
        properties.setEnabled(true);
        assertNotConfigured(properties);
    }

    @Test
    void anAbsentFileAndAnExistingDirectoryAreNotAcceptedAsDockerExecutables() {
        var properties = new LiveCampaignProperties();
        properties.setDockerExecutable(temporary.resolve("absent-docker.exe"));
        assertNotConfigured(properties);
        properties.setDockerExecutable(temporary);
        assertNotConfigured(properties);
    }

    private static void assertNotConfigured(LiveCampaignProperties properties) {
        assertThatThrownBy(() -> new DockerLiveStorageCapacityProbe(properties).availableBytes())
                .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_STORAGE_PROBE_NOT_CONFIGURED");
    }
}
