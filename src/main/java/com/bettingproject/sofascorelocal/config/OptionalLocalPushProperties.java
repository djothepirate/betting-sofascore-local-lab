package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "optional-integration")
@Validated
public class OptionalLocalPushProperties {

    public static final int MAXIMUM_PAYLOAD_BYTES = 5 * 1024 * 1024;
    public static final int MAXIMUM_ACKNOWLEDGEMENT_BYTES = 16 * 1024;
    public static final String PROTOCOL_VERSION = "1.0";

    private boolean enabled;
    private boolean remoteDeliveryAuthorized;
    private PermissionStatus officialPermissionStatus = PermissionStatus.NOT_EVIDENCED;
    private boolean loopbackQualification;
    private String loopbackOrigin = "";

    @Min(1)
    @Max(1)
    private int maximumConcurrency = 1;

    @Min(MAXIMUM_PAYLOAD_BYTES)
    @Max(MAXIMUM_PAYLOAD_BYTES)
    private int maximumPayloadBytes = MAXIMUM_PAYLOAD_BYTES;

    @Min(MAXIMUM_ACKNOWLEDGEMENT_BYTES)
    @Max(MAXIMUM_ACKNOWLEDGEMENT_BYTES)
    private int maximumAcknowledgementBytes = MAXIMUM_ACKNOWLEDGEMENT_BYTES;

    private String protocolVersion = PROTOCOL_VERSION;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private boolean automaticRetryEnabled;

    @Valid
    private Mtls mtls = new Mtls();

    public enum PermissionStatus {
        NOT_EVIDENCED,
        EVIDENCED_COMPATIBLE,
        EVIDENCED_INCOMPATIBLE
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRemoteDeliveryAuthorized() {
        return remoteDeliveryAuthorized;
    }

    public void setRemoteDeliveryAuthorized(boolean remoteDeliveryAuthorized) {
        this.remoteDeliveryAuthorized = remoteDeliveryAuthorized;
    }

    public PermissionStatus getOfficialPermissionStatus() {
        return officialPermissionStatus;
    }

    public void setOfficialPermissionStatus(PermissionStatus officialPermissionStatus) {
        this.officialPermissionStatus = officialPermissionStatus;
    }

    public boolean isLoopbackQualification() {
        return loopbackQualification;
    }

    public void setLoopbackQualification(boolean loopbackQualification) {
        this.loopbackQualification = loopbackQualification;
    }

    public String getLoopbackOrigin() {
        return loopbackOrigin;
    }

    public void setLoopbackOrigin(String loopbackOrigin) {
        this.loopbackOrigin = loopbackOrigin == null ? "" : loopbackOrigin.trim();
    }

    public int getMaximumConcurrency() {
        return maximumConcurrency;
    }

    public void setMaximumConcurrency(int maximumConcurrency) {
        this.maximumConcurrency = maximumConcurrency;
    }

    public int getMaximumPayloadBytes() {
        return maximumPayloadBytes;
    }

    public void setMaximumPayloadBytes(int maximumPayloadBytes) {
        this.maximumPayloadBytes = maximumPayloadBytes;
    }

    public int getMaximumAcknowledgementBytes() {
        return maximumAcknowledgementBytes;
    }

    public void setMaximumAcknowledgementBytes(int maximumAcknowledgementBytes) {
        this.maximumAcknowledgementBytes = maximumAcknowledgementBytes;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion == null ? "" : protocolVersion.trim();
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isAutomaticRetryEnabled() {
        return automaticRetryEnabled;
    }

    public void setAutomaticRetryEnabled(boolean automaticRetryEnabled) {
        this.automaticRetryEnabled = automaticRetryEnabled;
    }

    public Mtls getMtls() {
        return mtls;
    }

    public void setMtls(Mtls mtls) {
        this.mtls = mtls;
    }

    @AssertTrue(message = "WO-027 must keep real optional delivery disabled and unauthorized")
    public boolean isRealDeliveryFailClosed() {
        return !enabled && !remoteDeliveryAuthorized;
    }

    @AssertTrue(message = "automatic delivery retry must stay disabled")
    public boolean isAutomaticRetryDisabled() {
        return !automaticRetryEnabled;
    }

    @AssertTrue(message = "delivery timeouts must be positive and no greater than ten seconds")
    public boolean isTransportTimeoutsSafe() {
        return bounded(connectTimeout) && bounded(requestTimeout);
    }

    @AssertTrue(message = "the delivery protocol version is fixed to 1.0 under WO-027")
    public boolean isProtocolVersionFixed() {
        return PROTOCOL_VERSION.equals(protocolVersion);
    }

    @AssertTrue(message = "only an exact HTTPS IPv4 loopback origin is allowed for qualification")
    public boolean isLoopbackQualificationSafe() {
        if (!loopbackQualification) {
            return loopbackOrigin.isEmpty();
        }
        try {
            URI origin = URI.create(loopbackOrigin);
            return "https".equals(origin.getScheme())
                    && "127.0.0.1".equals(origin.getHost())
                    && origin.getPort() >= 1
                    && origin.getRawUserInfo() == null
                    && origin.getRawQuery() == null
                    && origin.getRawFragment() == null
                    && (origin.getRawPath() == null || origin.getRawPath().isEmpty());
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean bounded(Duration value) {
        return value != null
                && !value.isZero()
                && !value.isNegative()
                && value.compareTo(Duration.ofSeconds(10)) <= 0;
    }

    public static class Mtls {

        private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

        private boolean required = true;
        private String keyStoreType = "Windows-MY";
        private String clientCertificateSha256 = "";

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getKeyStoreType() {
            return keyStoreType;
        }

        public void setKeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType == null ? "" : keyStoreType.trim();
        }

        public String getClientCertificateSha256() {
            return clientCertificateSha256;
        }

        public void setClientCertificateSha256(String clientCertificateSha256) {
            this.clientCertificateSha256 = clientCertificateSha256 == null
                    ? ""
                    : clientCertificateSha256.trim().toLowerCase(Locale.ROOT);
        }

        @AssertTrue(message = "mTLS and the Windows user certificate store are mandatory")
        public boolean isProfileSafe() {
            return required
                    && "Windows-MY".equals(keyStoreType)
                    && (clientCertificateSha256.isEmpty()
                    || SHA_256.matcher(clientCertificateSha256).matches());
        }
    }
}
