package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Read-only, fixed argv probe of the actual Docker data volume; never runs a shell. */
@Component
public final class DockerLiveStorageCapacityProbe implements LiveStorageCapacityProbe {
    private final LiveCampaignProperties properties;
    public DockerLiveStorageCapacityProbe(LiveCampaignProperties properties) { this.properties = properties; }
    @Override public long availableBytes() {
        properties.validate();
        if (properties.getDockerExecutable() == null || !Files.isRegularFile(properties.getDockerExecutable()))
            throw new IllegalStateException("LIVE_STORAGE_PROBE_NOT_CONFIGURED");
        Process process = null;
        try {
            process = new ProcessBuilder(properties.getDockerExecutable().toString(), "exec",
                    properties.getPostgresContainer(), "df", "-Pk", "/var/lib/postgresql")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) throw new IllegalStateException("LIVE_STORAGE_PROBE_TIMEOUT");
            byte[] bytes = process.getInputStream().readNBytes(4097);
            if (process.exitValue() != 0 || bytes.length > 4096) throw new IllegalStateException("LIVE_STORAGE_PROBE_FAILED");
            String[] lines = new String(bytes, StandardCharsets.UTF_8).trim().split("\\R");
            String[] values = lines[lines.length - 1].trim().split("\\s+");
            if (values.length < 6) throw new IllegalStateException("LIVE_STORAGE_PROBE_INVALID");
            return Math.multiplyExact(Long.parseLong(values[3]), 1024);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("LIVE_STORAGE_PROBE_INTERRUPTED");
        } catch (java.io.IOException | NumberFormatException failure) {
            throw new IllegalStateException("LIVE_STORAGE_PROBE_FAILED");
        } finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
    }
}
