package com.bettingproject.sofascorelocal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.nio.file.Path;

/** Separate opt-in: historical automatic-refresh/live-polling properties remain false. */
@Component
@ConfigurationProperties(prefix = "sofascore.live")
public final class LiveCampaignProperties {
    private boolean enabled;
    private int qualifiedMatchCapacity = 1;
    private String qualificationSha256 = "";
    private Duration requestEnvelope = Duration.ofSeconds(10);
    private Duration processingEnvelope = Duration.ofSeconds(1);
    private Path dockerExecutable;
    private String postgresContainer = "betting-sofascore-local-lab-postgres";
    private Duration duration = Duration.ofHours(4);
    private long diskReserveBytes = 1024L * 1024 * 1024;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public int getQualifiedMatchCapacity() { return qualifiedMatchCapacity; }
    public void setQualifiedMatchCapacity(int v) { qualifiedMatchCapacity = v; }
    public String getQualificationSha256() { return qualificationSha256; }
    public void setQualificationSha256(String v) { qualificationSha256 = v; }
    public Duration getRequestEnvelope() { return requestEnvelope; }
    public void setRequestEnvelope(Duration v) { requestEnvelope = v; }
    public Duration getProcessingEnvelope() { return processingEnvelope; }
    public void setProcessingEnvelope(Duration v) { processingEnvelope = v; }
    public Path getDockerExecutable() { return dockerExecutable; }
    public void setDockerExecutable(Path v) { dockerExecutable = v; }
    public String getPostgresContainer() { return postgresContainer; }
    public void setPostgresContainer(String v) { postgresContainer = v; }
    public Duration getDuration() { return duration; }
    public void setDuration(Duration v) { duration = v; }
    public long getDiskReserveBytes() { return diskReserveBytes; }
    public void setDiskReserveBytes(long v) { diskReserveBytes = v; }
    public void validate() {
        if (qualifiedMatchCapacity < 1 || qualifiedMatchCapacity > 3 || duration == null
                || duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofHours(4)) > 0
                || requestEnvelope == null || requestEnvelope.isNegative() || requestEnvelope.isZero()
                || requestEnvelope.compareTo(Duration.ofSeconds(10)) > 0
                || processingEnvelope == null || processingEnvelope.isNegative()
                || diskReserveBytes < 1024L * 1024 * 1024
                || !postgresContainer.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))
            throw new IllegalStateException("LIVE_POLICY_INVALID");
        if ((qualifiedMatchCapacity > 1 || requestEnvelope.compareTo(Duration.ofSeconds(10)) < 0)
                && !qualificationSha256.matches("[0-9a-f]{64}"))
            throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
    }
}
