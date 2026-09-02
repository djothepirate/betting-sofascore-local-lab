package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryPolicyTest {

    @Test
    void defaultsBlockRealAndSyntheticDeliveryBeforeAnyTransportExists() {
        J7DeliveryPolicy policy = new J7DeliveryPolicy(new OptionalLocalPushProperties());

        assertThat(policy.realDeliveryBlockers()).containsExactly(
                "DELIVERY_DISABLED",
                "OFFICIAL_PERMISSION_NOT_EVIDENCED",
                "REMOTE_DELIVERY_NOT_AUTHORIZED",
                "REAL_RECEIVER_WORK_ORDER_NOT_SATISFIED");
        assertError(policy::requireRealDelivery, J7DeliveryError.DELIVERY_DISABLED);
        assertError(
                policy::requireSyntheticLoopbackQualification,
                J7DeliveryError.LOOPBACK_QUALIFICATION_DISABLED);
    }

    @Test
    void syntheticLoopbackQualificationRequiresAnExactExplicitHttpsOrigin() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setLoopbackQualification(true);
        properties.setLoopbackOrigin("https://127.0.0.1:49152");
        J7DeliveryPolicy policy = new J7DeliveryPolicy(properties);

        policy.requireSyntheticLoopbackQualification();
        assertThat(policy.syntheticLoopbackOrigin().toString())
                .isEqualTo("https://127.0.0.1:49152");

        properties.setLoopbackOrigin("https://localhost:49152");
        assertError(
                policy::requireSyntheticLoopbackQualification,
                J7DeliveryError.INVALID_LOOPBACK_ORIGIN);
    }

    @Test
    void evenAnEvidencedPermissionCannotArmRealDeliveryUnderWo027Properties() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setOfficialPermissionStatus(
                OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE);
        J7DeliveryPolicy policy = new J7DeliveryPolicy(properties);

        assertError(policy::requireRealDelivery, J7DeliveryError.DELIVERY_DISABLED);
        assertThat(policy.realDeliveryBlockers())
                .contains("DELIVERY_DISABLED", "REMOTE_DELIVERY_NOT_AUTHORIZED",
                        "REAL_RECEIVER_WORK_ORDER_NOT_SATISFIED")
                .doesNotContain("OFFICIAL_PERMISSION_NOT_EVIDENCED");
    }

    @Test
    void notEvidencedPermissionRemainsAnExecutableGateEvenIfOtherFlagsAreForced() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setEnabled(true);
        properties.setRemoteDeliveryAuthorized(true);
        J7DeliveryPolicy policy = new J7DeliveryPolicy(properties);

        assertError(
                policy::requireRealDelivery,
                J7DeliveryError.OFFICIAL_PERMISSION_NOT_EVIDENCED);
        assertThat(policy.realDeliveryBlockers()).containsExactly(
                "OFFICIAL_PERMISSION_NOT_EVIDENCED",
                "REAL_RECEIVER_WORK_ORDER_NOT_SATISFIED");
    }

    @Test
    void exactQualifiedSyntheticRuntimeProfileIsTheOnlyWo035ExecutablePath() {
        OptionalLocalPushProperties properties = syntheticRuntimeProperties();
        J7DeliveryPolicy policy = new J7DeliveryPolicy(properties);

        policy.requireRuntimeDelivery(J7DeliveryPayloadClass.SYNTHETIC_ONLY);
        assertThat(policy.runtimeBlockers(J7DeliveryPayloadClass.SYNTHETIC_ONLY))
                .isEmpty();

        J7DeliveryTransportFactory.Configuration configuration =
                policy.runtimeTransportConfiguration(
                        J7DeliveryPayloadClass.SYNTHETIC_ONLY);
        assertThat(configuration.receiverOrigin().toString())
                .isEqualTo(OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        assertThat(configuration.clientCertificateSha256()).isEqualTo("a".repeat(64));
        assertThat(configuration.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(configuration.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void mixedOrUnknownProvenanceFailsBeforeEveryRuntimeProperty() {
        J7DeliveryPolicy policy = new J7DeliveryPolicy(
                syntheticRuntimeProperties());

        assertError(
                () -> policy.requireRuntimeDelivery(
                        J7DeliveryPayloadClass.MIXED_OR_UNKNOWN),
                J7DeliveryError.PAYLOAD_PROVENANCE_NOT_ELIGIBLE);
        assertThat(policy.runtimeBlockers(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN))
                .containsExactly("PAYLOAD_PROVENANCE_NOT_ELIGIBLE");
    }

    @Test
    void syntheticRuntimeFailsClosedAcrossEveryMaterialActivationGate() {
        OptionalLocalPushProperties disabled = syntheticRuntimeProperties();
        disabled.setEnabled(false);
        assertRuntimeError(disabled, J7DeliveryError.DELIVERY_DISABLED);

        OptionalLocalPushProperties wrongMode = syntheticRuntimeProperties();
        wrongMode.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        assertRuntimeError(wrongMode, J7DeliveryError.EXECUTION_MODE_MISMATCH);

        OptionalLocalPushProperties wrongOrigin = syntheticRuntimeProperties();
        wrongOrigin.setReceiverOrigin("https://localhost:8444");
        assertRuntimeError(wrongOrigin, J7DeliveryError.INVALID_RECEIVER_ORIGIN);

        OptionalLocalPushProperties receiverNotQualified = syntheticRuntimeProperties();
        receiverNotQualified.setReceiverQualification(
                OptionalLocalPushProperties.QualificationStatus.NOT_QUALIFIED);
        assertRuntimeError(receiverNotQualified, J7DeliveryError.RECEIVER_NOT_QUALIFIED);

        OptionalLocalPushProperties senderNotQualified = syntheticRuntimeProperties();
        senderNotQualified.setSenderQualification(
                OptionalLocalPushProperties.QualificationStatus.NOT_QUALIFIED);
        assertRuntimeError(senderNotQualified, J7DeliveryError.SENDER_NOT_QUALIFIED);

        OptionalLocalPushProperties noCertificate = syntheticRuntimeProperties();
        noCertificate.getMtls().setClientCertificateSha256("");
        assertRuntimeError(
                noCertificate,
                J7DeliveryError.MTLS_CERTIFICATE_NOT_CONFIGURED);

        OptionalLocalPushProperties remoteFlag = syntheticRuntimeProperties();
        remoteFlag.setRemoteDeliveryAuthorized(true);
        assertRuntimeError(
                remoteFlag,
                J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);

        OptionalLocalPushProperties automaticRetry = syntheticRuntimeProperties();
        automaticRetry.setAutomaticRetryEnabled(true);
        assertRuntimeError(
                automaticRetry,
                J7DeliveryError.AUTOMATIC_RETRY_NOT_ALLOWED);
        assertThat(new J7DeliveryPolicy(automaticRetry).runtimeBlockers(
                J7DeliveryPayloadClass.SYNTHETIC_ONLY))
                .containsExactly("AUTOMATIC_RETRY_NOT_ALLOWED");
    }

    @Test
    void providerDerivedRuntimeRemainsStructurallyBlockedByOneTimeOwnerGo() {
        OptionalLocalPushProperties properties = syntheticRuntimeProperties();
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        properties.setOfficialPermissionStatus(
                OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE);
        properties.setRemoteDeliveryAuthorized(true);
        J7DeliveryPolicy policy = new J7DeliveryPolicy(properties);

        assertError(
                () -> policy.requireRuntimeDelivery(
                        J7DeliveryPayloadClass.PROVIDER_DERIVED),
                J7DeliveryError.PROVIDER_OWNER_GO_REQUIRED);
        assertThat(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .containsExactly("PROVIDER_OWNER_GO_REQUIRED");
    }

    @Test
    void providerPermissionAndRemoteAuthorizationAreIndependentGates() {
        OptionalLocalPushProperties properties = syntheticRuntimeProperties();
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        J7DeliveryPolicy policy = new J7DeliveryPolicy(properties);

        assertError(
                () -> policy.requireRuntimeDelivery(
                        J7DeliveryPayloadClass.PROVIDER_DERIVED),
                J7DeliveryError.OFFICIAL_PERMISSION_NOT_EVIDENCED);
        assertThat(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .containsExactly(
                        "OFFICIAL_PERMISSION_NOT_EVIDENCED",
                        "REMOTE_DELIVERY_NOT_AUTHORIZED",
                        "PROVIDER_OWNER_GO_REQUIRED");
    }

    private static OptionalLocalPushProperties syntheticRuntimeProperties() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setEnabled(true);
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.SYNTHETIC_LOOPBACK);
        properties.setReceiverOrigin(
                OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        properties.setReceiverQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.setSenderQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.getMtls().setClientCertificateSha256("a".repeat(64));
        return properties;
    }

    private static void assertRuntimeError(
            OptionalLocalPushProperties properties,
            J7DeliveryError error) {
        assertError(
                () -> new J7DeliveryPolicy(properties).requireRuntimeDelivery(
                        J7DeliveryPayloadClass.SYNTHETIC_ONLY),
                error);
    }

    private static void assertError(Runnable action, J7DeliveryError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(error.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(error);
    }
}
