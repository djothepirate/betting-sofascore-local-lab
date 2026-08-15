package com.bettingproject.sofascorelocal.config;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
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
    private boolean j3QualificationEnabled;
    private boolean j4EventDetailsQualificationEnabled;
    private boolean j4EventDetailsPhase2Enabled;
    private boolean j5EventDataQualificationEnabled;

    @Min(1)
    @Max(1)
    private int maximumConcurrency = 1;

    private Duration minimumDelay = Duration.ofSeconds(3);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
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

    public boolean isJ3QualificationEnabled() {
        return j3QualificationEnabled;
    }

    public void setJ3QualificationEnabled(boolean j3QualificationEnabled) {
        this.j3QualificationEnabled = j3QualificationEnabled;
    }

    public boolean isJ4EventDetailsQualificationEnabled() {
        return j4EventDetailsQualificationEnabled;
    }

    public void setJ4EventDetailsQualificationEnabled(
            boolean j4EventDetailsQualificationEnabled) {
        this.j4EventDetailsQualificationEnabled = j4EventDetailsQualificationEnabled;
    }

    public boolean isJ4EventDetailsPhase2Enabled() {
        return j4EventDetailsPhase2Enabled;
    }

    public void setJ4EventDetailsPhase2Enabled(boolean j4EventDetailsPhase2Enabled) {
        this.j4EventDetailsPhase2Enabled = j4EventDetailsPhase2Enabled;
    }

    public boolean isJ5EventDataQualificationEnabled() {
        return j5EventDataQualificationEnabled;
    }

    public void setJ5EventDataQualificationEnabled(
            boolean j5EventDataQualificationEnabled) {
        this.j5EventDataQualificationEnabled = j5EventDataQualificationEnabled;
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

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
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

    @AssertTrue(message = "connect and read timeouts must be positive and no greater than 10 seconds")
    public boolean isTransportTimeoutsSafe() {
        return isBoundedTimeout(connectTimeout) && isBoundedTimeout(readTimeout);
    }

    @AssertTrue(message = "automatic refresh and live polling must stay disabled during manual J3")
    public boolean isAutomaticNetworkActivityDisabled() {
        return !automaticRefreshEnabled && !livePollingEnabled;
    }

    @AssertTrue(message = "an export directory is required")
    public boolean isExportDirectoryConfigured() {
        return exportDirectory != null;
    }

    @AssertTrue(message = "J3 qualification requires explicit connector, raw storage and only SCHEDULED_EVENTS")
    public boolean isJ3QualificationConfigurationSafe() {
        return !j3QualificationEnabled
                || (enabled
                && maximumConcurrency == 1
                && storeRawPayloads
                && !automaticRefreshEnabled
                && !livePollingEnabled
                && allowedEndpoints.equals(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)));
    }

    @AssertTrue(message = "J4 EVENT_DETAILS qualification requires explicit connector, raw storage and only EVENT_DETAILS")
    public boolean isJ4EventDetailsQualificationConfigurationSafe() {
        return !j4EventDetailsQualificationEnabled
                || (enabled
                && !j3QualificationEnabled
                && !j5EventDataQualificationEnabled
                && maximumConcurrency == 1
                && storeRawPayloads
                && !automaticRefreshEnabled
                && !livePollingEnabled
                && allowedEndpoints.equals(Set.of(SofascoreEndpointType.EVENT_DETAILS)));
    }

    @AssertTrue(message = "J5 event-data qualification requires its three exact endpoints and no other provider qualification")
    public boolean isJ5EventDataQualificationConfigurationSafe() {
        return !j5EventDataQualificationEnabled
                || (enabled
                && !j3QualificationEnabled
                && !j4EventDetailsQualificationEnabled
                && !j4EventDetailsPhase2Enabled
                && maximumConcurrency == 1
                && storeRawPayloads
                && !automaticRefreshEnabled
                && !livePollingEnabled
                && allowedEndpoints.equals(Set.of(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS)));
    }

    @AssertTrue(message = "J3, J4 and J5 provider qualification paths are mutually exclusive")
    public boolean isOnlyOneProviderQualificationPathEnabled() {
        int enabledPaths = (j3QualificationEnabled ? 1 : 0)
                + (j4EventDetailsQualificationEnabled ? 1 : 0)
                + (j5EventDataQualificationEnabled ? 1 : 0);
        return enabledPaths <= 1;
    }

    @AssertTrue(message = "J4 phase 2 requires the J4 EVENT_DETAILS qualification path")
    public boolean isJ4EventDetailsPhase2SelectionSafe() {
        return !j4EventDetailsPhase2Enabled || j4EventDetailsQualificationEnabled;
    }

    private static boolean isBoundedTimeout(Duration timeout) {
        return timeout != null
                && !timeout.isZero()
                && !timeout.isNegative()
                && timeout.compareTo(Duration.ofSeconds(10)) <= 0;
    }
}
