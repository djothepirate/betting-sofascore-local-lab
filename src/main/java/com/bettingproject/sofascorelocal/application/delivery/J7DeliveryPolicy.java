package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Runtime gate kept separate from every SofaScore acquisition property. */
public final class J7DeliveryPolicy {

    private final OptionalLocalPushProperties properties;

    public J7DeliveryPolicy(OptionalLocalPushProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void requireSyntheticLoopbackQualification() {
        if (properties.isEnabled() || properties.isRemoteDeliveryAuthorized()) {
            throw new J7DeliveryException(J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
        if (!properties.isLoopbackQualification()) {
            throw new J7DeliveryException(J7DeliveryError.LOOPBACK_QUALIFICATION_DISABLED);
        }
        if (!properties.isLoopbackQualificationSafe()) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_LOOPBACK_ORIGIN);
        }
        if (properties.isAutomaticRetryEnabled()) {
            throw new J7DeliveryException(J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
        if (!providerOwnerGoIsAbsent()) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_NOT_ALLOWED);
        }
    }

    public void requireRealDelivery() {
        if (!properties.isEnabled()) {
            throw new J7DeliveryException(J7DeliveryError.DELIVERY_DISABLED);
        }
        requireJ7TransferEligiblePermissionAudit();
        if (!properties.isRemoteDeliveryAuthorized()) {
            throw new J7DeliveryException(
                    J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
        providerOwnerGoReference();
    }

    public List<String> realDeliveryBlockers() {
        List<String> blockers = new ArrayList<>();
        if (!properties.isEnabled()) {
            blockers.add("DELIVERY_DISABLED");
        }
        addPermissionAuditBlocker(blockers);
        if (!properties.isRemoteDeliveryAuthorized()) {
            blockers.add("REMOTE_DELIVERY_NOT_AUTHORIZED");
        }
        OptionalLocalPushProperties.ProviderOwnerGo configured =
                properties.getProviderOwnerGo();
        if (configured == null || configured.isAbsent()) {
            blockers.add("PROVIDER_OWNER_GO_REQUIRED");
        }
        else if (!configured.isComplete()) {
            blockers.add("PROVIDER_OWNER_GO_INVALID");
        }
        return List.copyOf(blockers);
    }

    public URI syntheticLoopbackOrigin() {
        requireSyntheticLoopbackQualification();
        return URI.create(properties.getLoopbackOrigin());
    }

    /**
     * Applies the WO-035 runtime gates as amended by WO-047, without consulting a certificate
     * store or opening a client. Provider-derived delivery admits only the explicit permission
     * audit allow-list and requires the complete public reference of the owner-go document.
     * Durable availability, identity and one-time consumption are enforced later by the
     * persistence boundary, before transport creation.
     */
    public void requireRuntimeDelivery(J7DeliveryPayloadClass payloadClass) {
        Objects.requireNonNull(payloadClass, "payloadClass");
        if (payloadClass == J7DeliveryPayloadClass.MIXED_OR_UNKNOWN) {
            throw new J7DeliveryException(
                    J7DeliveryError.PAYLOAD_PROVENANCE_NOT_ELIGIBLE);
        }
        if (!properties.isEnabled()) {
            throw new J7DeliveryException(J7DeliveryError.DELIVERY_DISABLED);
        }
        OptionalLocalPushProperties.ExecutionMode expectedMode =
                payloadClass == J7DeliveryPayloadClass.SYNTHETIC_ONLY
                        ? OptionalLocalPushProperties.ExecutionMode.SYNTHETIC_LOOPBACK
                        : OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED;
        if (properties.getExecutionMode() != expectedMode) {
            throw new J7DeliveryException(J7DeliveryError.EXECUTION_MODE_MISMATCH);
        }
        if (!properties.isReceiverOriginSafe()
                || properties.getReceiverOrigin().isEmpty()) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_RECEIVER_ORIGIN);
        }
        if (properties.getReceiverQualification()
                != OptionalLocalPushProperties.QualificationStatus.PASS) {
            throw new J7DeliveryException(J7DeliveryError.RECEIVER_NOT_QUALIFIED);
        }
        if (properties.getSenderQualification()
                != OptionalLocalPushProperties.QualificationStatus.PASS) {
            throw new J7DeliveryException(J7DeliveryError.SENDER_NOT_QUALIFIED);
        }
        if (properties.getMtls() == null
                || !properties.getMtls().isProfileSafe()
                || properties.getMtls().getClientCertificateSha256().isEmpty()) {
            throw new J7DeliveryException(
                    J7DeliveryError.MTLS_CERTIFICATE_NOT_CONFIGURED);
        }
        if (properties.isAutomaticRetryEnabled()) {
            throw new J7DeliveryException(
                    J7DeliveryError.AUTOMATIC_RETRY_NOT_ALLOWED);
        }
        if (payloadClass == J7DeliveryPayloadClass.SYNTHETIC_ONLY) {
            if (properties.isRemoteDeliveryAuthorized()) {
                throw new J7DeliveryException(
                        J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
            }
            if (!providerOwnerGoIsAbsent()) {
                throw new J7DeliveryException(
                        J7DeliveryError.PROVIDER_OWNER_GO_NOT_ALLOWED);
            }
            return;
        }
        requireJ7TransferEligiblePermissionAudit();
        if (!properties.isRemoteDeliveryAuthorized()) {
            throw new J7DeliveryException(
                    J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
        providerOwnerGoReference();
    }

    public List<String> runtimeBlockers(J7DeliveryPayloadClass payloadClass) {
        Objects.requireNonNull(payloadClass, "payloadClass");
        List<String> blockers = new ArrayList<>();
        if (payloadClass == J7DeliveryPayloadClass.MIXED_OR_UNKNOWN) {
            blockers.add("PAYLOAD_PROVENANCE_NOT_ELIGIBLE");
            return List.copyOf(blockers);
        }
        if (!properties.isEnabled()) {
            blockers.add("DELIVERY_DISABLED");
        }
        OptionalLocalPushProperties.ExecutionMode expectedMode =
                payloadClass == J7DeliveryPayloadClass.SYNTHETIC_ONLY
                        ? OptionalLocalPushProperties.ExecutionMode.SYNTHETIC_LOOPBACK
                        : OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED;
        if (properties.getExecutionMode() != expectedMode) {
            blockers.add("EXECUTION_MODE_MISMATCH");
        }
        if (!properties.isReceiverOriginSafe()
                || properties.getReceiverOrigin().isEmpty()) {
            blockers.add("INVALID_RECEIVER_ORIGIN");
        }
        if (properties.getReceiverQualification()
                != OptionalLocalPushProperties.QualificationStatus.PASS) {
            blockers.add("RECEIVER_NOT_QUALIFIED");
        }
        if (properties.getSenderQualification()
                != OptionalLocalPushProperties.QualificationStatus.PASS) {
            blockers.add("SENDER_NOT_QUALIFIED");
        }
        if (properties.getMtls() == null
                || !properties.getMtls().isProfileSafe()
                || properties.getMtls().getClientCertificateSha256().isEmpty()) {
            blockers.add("MTLS_CERTIFICATE_NOT_CONFIGURED");
        }
        if (properties.isAutomaticRetryEnabled()) {
            blockers.add("AUTOMATIC_RETRY_NOT_ALLOWED");
        }
        if (payloadClass == J7DeliveryPayloadClass.PROVIDER_DERIVED) {
            addPermissionAuditBlocker(blockers);
            if (!properties.isRemoteDeliveryAuthorized()) {
                blockers.add("REMOTE_DELIVERY_NOT_AUTHORIZED");
            }
            OptionalLocalPushProperties.ProviderOwnerGo configured =
                    properties.getProviderOwnerGo();
            if (configured == null || configured.isAbsent()) {
                blockers.add("PROVIDER_OWNER_GO_REQUIRED");
            }
            else if (!configured.isComplete()) {
                blockers.add("PROVIDER_OWNER_GO_INVALID");
            }
        }
        else if (properties.isRemoteDeliveryAuthorized()) {
            blockers.add("REMOTE_DELIVERY_NOT_AUTHORIZED");
        }
        else if (!providerOwnerGoIsAbsent()) {
            blockers.add("PROVIDER_OWNER_GO_NOT_ALLOWED");
        }
        return List.copyOf(blockers);
    }

    public J7ProviderDerivedOwnerGo.Reference providerOwnerGoReference() {
        OptionalLocalPushProperties.ProviderOwnerGo configured =
                properties.getProviderOwnerGo();
        if (configured == null || configured.isAbsent()) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_REQUIRED);
        }
        if (!configured.isComplete()) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_INVALID);
        }
        try {
            return new J7ProviderDerivedOwnerGo.Reference(
                    UUID.fromString(configured.getGoId()),
                    configured.getOwnerGoDocumentSha256());
        }
        catch (IllegalArgumentException exception) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_INVALID);
        }
    }

    /** Verifies that the durable grant repeats every current non-secret activation gate. */
    public void requireProviderOwnerGoGrantConfiguration(
            J7ProviderDerivedOwnerGo.Grant grant) {
        Objects.requireNonNull(grant, "grant");
        requireRuntimeDelivery(J7DeliveryPayloadClass.PROVIDER_DERIVED);
        if (!grant.reference().equals(providerOwnerGoReference())
                || !grant.permissionAuditStatus()
                .equals(properties.getOfficialPermissionStatus().name())
                || !grant.receiverQualification()
                .equals(properties.getReceiverQualification().name())
                || !grant.senderQualification()
                .equals(properties.getSenderQualification().name())
                || !grant.receiverOrigin().toString()
                .equals(properties.getReceiverOrigin())
                || !grant.clientCertificateSha256()
                .equals(properties.getMtls().getClientCertificateSha256())) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_MISMATCH);
        }
    }

    public J7DeliveryTransportFactory.Configuration runtimeTransportConfiguration(
            J7DeliveryPayloadClass payloadClass) {
        requireRuntimeDelivery(payloadClass);
        return new J7DeliveryTransportFactory.Configuration(
                URI.create(properties.getReceiverOrigin()),
                properties.getMtls().getClientCertificateSha256(),
                properties.getConnectTimeout(),
                properties.getRequestTimeout());
    }

    private boolean providerOwnerGoIsAbsent() {
        return properties.getProviderOwnerGo() != null
                && properties.getProviderOwnerGo().isAbsent();
    }

    private void requireJ7TransferEligiblePermissionAudit() {
        OptionalLocalPushProperties.PermissionStatus status =
                properties.getOfficialPermissionStatus();
        if (status == OptionalLocalPushProperties.PermissionStatus.NOT_EVIDENCED
                || status
                == OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE) {
            return;
        }
        if (status == OptionalLocalPushProperties.PermissionStatus.EVIDENCED_INCOMPATIBLE) {
            throw new J7DeliveryException(
                    J7DeliveryError.OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE);
        }
        throw new J7DeliveryException(
                J7DeliveryError.OFFICIAL_PERMISSION_STATUS_INVALID);
    }

    private void addPermissionAuditBlocker(List<String> blockers) {
        OptionalLocalPushProperties.PermissionStatus status =
                properties.getOfficialPermissionStatus();
        if (status == OptionalLocalPushProperties.PermissionStatus.NOT_EVIDENCED
                || status
                == OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE) {
            return;
        }
        if (status
                == OptionalLocalPushProperties.PermissionStatus.EVIDENCED_INCOMPATIBLE) {
            blockers.add(
                    J7DeliveryError.OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE.name());
            return;
        }
        blockers.add(J7DeliveryError.OFFICIAL_PERMISSION_STATUS_INVALID.name());
    }
}
