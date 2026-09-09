package com.bettingproject.sofascorelocal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

/** Separate opt-in: historical automatic-refresh/live-polling properties remain false. */
@Component
@ConfigurationProperties(prefix = "sofascore.live")
public final class LiveCampaignProperties {
    private boolean enabled;
    private int qualifiedMatchCapacity = 1;
    private String qualificationSha256 = "";
    private Duration requestEnvelope = Duration.ofSeconds(10);
    private Duration processingEnvelope = Duration.ofSeconds(1);
    private final Grouped grouped = new Grouped();
    private final Grouped groupedV5 = new Grouped();
    private final Grouped groupedV6 = new Grouped();
    private final Grouped groupedV7 = new Grouped();
    private Path dockerExecutable;
    private String postgresContainer = "betting-sofascore-local-lab-postgres";
    private Duration duration = Duration.ofHours(4);
    private long diskReserveBytes = 1024L * 1024 * 1024;
    public boolean isEnabled() { return enabled; }
    /** New preparations always use the current safeguards; no configuration rollback exists. */
    public String getPreparationPolicyVersion() { return "live-v7"; }
    public void setEnabled(boolean v) { enabled = v; }
    public int getQualifiedMatchCapacity() { return qualifiedMatchCapacity; }
    public void setQualifiedMatchCapacity(int v) { qualifiedMatchCapacity = v; }
    public String getQualificationSha256() { return qualificationSha256; }
    public void setQualificationSha256(String v) { qualificationSha256 = v; }
    public Duration getRequestEnvelope() { return requestEnvelope; }
    public void setRequestEnvelope(Duration v) { requestEnvelope = v; }
    public Duration getProcessingEnvelope() { return processingEnvelope; }
    public void setProcessingEnvelope(Duration v) { processingEnvelope = v; }
    public Grouped getGrouped() { return grouped; }
    public Grouped getGroupedV5() { return groupedV5; }
    public Grouped getGroupedV6() { return groupedV6; }
    public Grouped getGroupedV7() { return groupedV7; }
    public GroupedAdmissionProfile groupedAdmissionProfile() {
        return groupedAdmissionProfile(grouped, "live-v4");
    }
    public GroupedAdmissionProfile groupedAdmissionProfileV5() {
        return groupedAdmissionProfile(groupedV5, "live-v5");
    }
    public GroupedAdmissionProfile groupedAdmissionProfileV6() {
        return groupedAdmissionProfile(groupedV6, "live-v6");
    }
    public GroupedAdmissionProfile groupedAdmissionProfileV7() {
        return groupedAdmissionProfile(groupedV7, "live-v7");
    }
    private static GroupedAdmissionProfile groupedAdmissionProfile(Grouped settings, String policyVersion) {
        if (settings.qualificationSha256 == null || !settings.qualificationSha256.matches("[0-9a-f]{64}"))
            throw new IllegalStateException("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        settings.endpoints.forEach((endpoint, budget) -> envelopes.put(endpoint,
                new EndpointEnvelope(budget.requestEnvelope, budget.processingEnvelope)));
        return new GroupedAdmissionProfile(envelopes, settings.qualificationSha256, policyVersion);
    }
    /** Separate opt-in evidence and envelopes. Historical settings never qualify grouped traffic. */
    public static final class Grouped {
        private String qualificationSha256 = "";
        private Map<SofascoreEndpointType, EndpointBudget> endpoints = new EnumMap<>(SofascoreEndpointType.class);
        public Grouped() {
            for (var endpoint : new SofascoreEndpointType[]{SofascoreEndpointType.EVENT_DETAILS,
                    SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_STATISTICS,
                    SofascoreEndpointType.EVENT_LINEUPS}) endpoints.put(endpoint, new EndpointBudget());
        }
        public String getQualificationSha256() { return qualificationSha256; }
        public void setQualificationSha256(String value) { qualificationSha256 = value; }
        public Map<SofascoreEndpointType, EndpointBudget> getEndpoints() { return endpoints; }
        public void setEndpoints(Map<SofascoreEndpointType, EndpointBudget> value) { endpoints = value; }
    }
    public static final class EndpointBudget {
        private Duration requestEnvelope = Duration.ofSeconds(10);
        private Duration processingEnvelope = Duration.ofSeconds(1);
        public Duration getRequestEnvelope() { return requestEnvelope; }
        public void setRequestEnvelope(Duration value) { requestEnvelope = value; }
        public Duration getProcessingEnvelope() { return processingEnvelope; }
        public void setProcessingEnvelope(Duration value) { processingEnvelope = value; }
    }
    public Path getDockerExecutable() { return dockerExecutable; }
    public void setDockerExecutable(Path v) { dockerExecutable = v; }
    public String getPostgresContainer() { return postgresContainer; }
    public void setPostgresContainer(String v) { postgresContainer = v; }
    public Duration getDuration() { return duration; }
    public void setDuration(Duration v) { duration = v; }
    public long getDiskReserveBytes() { return diskReserveBytes; }
    public void setDiskReserveBytes(long v) { diskReserveBytes = v; }
    public void validate() {
        if (qualifiedMatchCapacity < 1 || duration == null
                || duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofHours(4)) > 0
                || requestEnvelope == null || requestEnvelope.isNegative() || requestEnvelope.isZero()
                || requestEnvelope.compareTo(Duration.ofSeconds(10)) > 0
                || processingEnvelope == null || processingEnvelope.isNegative()
                || processingEnvelope.compareTo(Duration.ofMinutes(1)) > 0
                || diskReserveBytes < 1024L * 1024 * 1024
                || !postgresContainer.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))
            throw new IllegalStateException("LIVE_POLICY_INVALID");
        if (requestEnvelope.compareTo(Duration.ofSeconds(10)) < 0
                && (qualificationSha256 == null || !qualificationSha256.matches("[0-9a-f]{64}")))
            throw new IllegalStateException("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
    }
}
