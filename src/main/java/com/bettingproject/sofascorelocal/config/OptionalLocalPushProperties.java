package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "optional-integration")
@Validated
public class OptionalLocalPushProperties {

    public static final int MAXIMUM_PAYLOAD_BYTES = 5 * 1024 * 1024;
    public static final int MAXIMUM_ACKNOWLEDGEMENT_BYTES = 16 * 1024;
    public static final String PROTOCOL_VERSION = "1.0";
    public static final String LOCAL_RECEIVER_ORIGIN = "https://127.0.0.1:8444";
    private static final int MAXIMUM_TCP_PORT = 65_535;

    private boolean enabled;
    @NotNull
    private ExecutionMode executionMode = ExecutionMode.DISABLED;
    private boolean remoteDeliveryAuthorized;
    @NotNull
    private PermissionStatus officialPermissionStatus = PermissionStatus.NOT_EVIDENCED;

    @NotNull
    private QualificationStatus receiverQualification = QualificationStatus.NOT_QUALIFIED;

    @NotNull
    private QualificationStatus senderQualification = QualificationStatus.NOT_QUALIFIED;
    private String receiverOrigin = "";
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
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(10);
    private boolean automaticRetryEnabled;

    @Valid
    @NotNull
    private Mtls mtls = new Mtls();

    @Valid
    @NotNull
    private ProviderOwnerGo providerOwnerGo = new ProviderOwnerGo();

    public enum PermissionStatus {
        NOT_EVIDENCED,
        EVIDENCED_COMPATIBLE,
        EVIDENCED_INCOMPATIBLE
    }

    public enum ExecutionMode {
        DISABLED,
        SYNTHETIC_LOOPBACK,
        PROVIDER_DERIVED
    }

    public enum QualificationStatus {
        NOT_QUALIFIED,
        PASS
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
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

    public QualificationStatus getReceiverQualification() {
        return receiverQualification;
    }

    public void setReceiverQualification(QualificationStatus receiverQualification) {
        this.receiverQualification = receiverQualification;
    }

    public QualificationStatus getSenderQualification() {
        return senderQualification;
    }

    public void setSenderQualification(QualificationStatus senderQualification) {
        this.senderQualification = senderQualification;
    }

    public String getReceiverOrigin() {
        return receiverOrigin;
    }

    public void setReceiverOrigin(String receiverOrigin) {
        this.receiverOrigin = receiverOrigin == null ? "" : receiverOrigin.trim();
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

    public ProviderOwnerGo getProviderOwnerGo() {
        return providerOwnerGo;
    }

    public void setProviderOwnerGo(ProviderOwnerGo providerOwnerGo) {
        this.providerOwnerGo = providerOwnerGo;
    }

    @AssertTrue(message = "the WO-035 runtime activation matrix is inconsistent")
    public boolean isRuntimeActivationCoherent() {
        if (executionMode == null
                || receiverQualification == null
                || senderQualification == null) {
            return false;
        }
        if (!enabled) {
            return executionMode == ExecutionMode.DISABLED
                    && !remoteDeliveryAuthorized
                    && receiverOrigin.isEmpty()
                    && receiverQualification == QualificationStatus.NOT_QUALIFIED
                    && senderQualification == QualificationStatus.NOT_QUALIFIED
                    && providerOwnerGo != null
                    && providerOwnerGo.isAbsent();
        }
        if (executionMode == ExecutionMode.DISABLED
                || !isExactLocalReceiverOrigin(receiverOrigin)
                || receiverQualification != QualificationStatus.PASS
                || senderQualification != QualificationStatus.PASS
                || mtls == null
                || providerOwnerGo == null
                || mtls.getClientCertificateSha256().isEmpty()) {
            return false;
        }
        return switch (executionMode) {
            case DISABLED -> false;
            case SYNTHETIC_LOOPBACK -> !remoteDeliveryAuthorized
                    && providerOwnerGo.isAbsent();
            case PROVIDER_DERIVED -> remoteDeliveryAuthorized
                    && officialPermissionStatus == PermissionStatus.EVIDENCED_COMPATIBLE
                    && providerOwnerGo.isComplete();
        };
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
                    && origin.getPort() <= MAXIMUM_TCP_PORT
                    && origin.getRawUserInfo() == null
                    && origin.getRawQuery() == null
                    && origin.getRawFragment() == null
                    && (origin.getRawPath() == null || origin.getRawPath().isEmpty());
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "the receiver origin must be empty or the exact local HTTPS receiver")
    public boolean isReceiverOriginSafe() {
        return receiverOrigin.isEmpty() || isExactLocalReceiverOrigin(receiverOrigin);
    }

    private static boolean isExactLocalReceiverOrigin(String value) {
        try {
            URI origin = URI.create(value);
            return LOCAL_RECEIVER_ORIGIN.equals(value)
                    && "https".equals(origin.getScheme())
                    && "127.0.0.1".equals(origin.getHost())
                    && origin.getPort() == 8_444
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

    /**
     * Public, non-secret reference to the exact owner-go document. The document itself remains
     * outside configuration; both fields are deliberately absent by default and must be supplied
     * together only for a provider-derived one-shot execution.
     */
    public static class ProviderOwnerGo {

        private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

        private String goId = "";
        private String ownerGoDocumentSha256 = "";

        public String getGoId() {
            return goId;
        }

        public void setGoId(String goId) {
            this.goId = goId == null ? "" : goId.trim().toLowerCase(Locale.ROOT);
        }

        public String getOwnerGoDocumentSha256() {
            return ownerGoDocumentSha256;
        }

        public void setOwnerGoDocumentSha256(String ownerGoDocumentSha256) {
            this.ownerGoDocumentSha256 = ownerGoDocumentSha256 == null
                    ? ""
                    : ownerGoDocumentSha256.trim().toLowerCase(Locale.ROOT);
        }

        public boolean isAbsent() {
            return goId.isEmpty() && ownerGoDocumentSha256.isEmpty();
        }

        public boolean isComplete() {
            if (!SHA_256.matcher(ownerGoDocumentSha256).matches()) {
                return false;
            }
            try {
                UUID parsed = UUID.fromString(goId);
                return parsed.variant() == 2
                        && parsed.version() >= 1
                        && parsed.version() <= 5
                        && !parsed.equals(new UUID(0, 0));
            }
            catch (IllegalArgumentException exception) {
                return false;
            }
        }

        @AssertTrue(message = "provider owner-go reference must be absent or complete")
        public boolean isAbsentOrComplete() {
            return isAbsent() || isComplete();
        }
    }
}
