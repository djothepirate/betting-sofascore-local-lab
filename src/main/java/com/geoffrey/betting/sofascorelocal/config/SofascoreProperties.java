package com.geoffrey.betting.sofascorelocal.config;

import com.geoffrey.betting.sofascorelocal.domain.provider.SofascoreEndpointType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "sofascore")
@Validated
public class SofascoreProperties {

    private boolean enabled;
    private String baseUrl = "";

    @Min(1)
    @Max(1)
    private int maximumConcurrency = 1;

    private Duration minimumDelay = Duration.ofSeconds(3);
    private boolean automaticRefreshEnabled;
    private boolean livePollingEnabled;
    private boolean storeRawPayloads = true;

    @Min(1)
    @Max(3650)
    private int rawPayloadRetentionDays = 30;

    private Path exportDirectory = Path.of("./exports");
    private Set<SofascoreEndpointType> allowedEndpoints = new LinkedHashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public int getMaximumConcurrency() {
        return maximumConcurrency;
    }

    public void setMaximumConcurrency(int maximumConcurrency) {
        this.maximumConcurrency = maximumConcurrency;
    }

    public Duration getMinimumDelay() {
        return minimumDelay;
    }

    public void setMinimumDelay(Duration minimumDelay) {
        this.minimumDelay = minimumDelay;
    }

    public boolean isAutomaticRefreshEnabled() {
        return automaticRefreshEnabled;
    }

    public void setAutomaticRefreshEnabled(boolean automaticRefreshEnabled) {
        this.automaticRefreshEnabled = automaticRefreshEnabled;
    }

    public boolean isLivePollingEnabled() {
        return livePollingEnabled;
    }

    public void setLivePollingEnabled(boolean livePollingEnabled) {
        this.livePollingEnabled = livePollingEnabled;
    }

    public boolean isStoreRawPayloads() {
        return storeRawPayloads;
    }

    public void setStoreRawPayloads(boolean storeRawPayloads) {
        this.storeRawPayloads = storeRawPayloads;
    }

    public int getRawPayloadRetentionDays() {
        return rawPayloadRetentionDays;
    }

    public void setRawPayloadRetentionDays(int rawPayloadRetentionDays) {
        this.rawPayloadRetentionDays = rawPayloadRetentionDays;
    }

    public Path getExportDirectory() {
        return exportDirectory;
    }

    public void setExportDirectory(Path exportDirectory) {
        this.exportDirectory = exportDirectory;
    }

    public Set<SofascoreEndpointType> getAllowedEndpoints() {
        return allowedEndpoints;
    }

    public void setAllowedEndpoints(Set<SofascoreEndpointType> allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allowedEndpoints);
    }

    @AssertTrue(message = "minimum-delay must be at least 3 seconds")
    public boolean isMinimumDelaySafe() {
        return minimumDelay != null && minimumDelay.compareTo(Duration.ofSeconds(3)) >= 0;
    }

    @AssertTrue(message = "automatic refresh and live polling must stay disabled during J1")
    public boolean isAutomaticNetworkActivityDisabled() {
        return !automaticRefreshEnabled && !livePollingEnabled;
    }

    @AssertTrue(message = "an export directory is required")
    public boolean isExportDirectoryConfigured() {
        return exportDirectory != null;
    }
}
