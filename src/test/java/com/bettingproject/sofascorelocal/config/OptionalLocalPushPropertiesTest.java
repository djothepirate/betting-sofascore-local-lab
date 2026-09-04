package com.bettingproject.sofascorelocal.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalLocalPushPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsAreFailClosedBoundedAndPermissionIsNotEvidenced() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getExecutionMode())
                .isEqualTo(OptionalLocalPushProperties.ExecutionMode.DISABLED);
        assertThat(properties.isRemoteDeliveryAuthorized()).isFalse();
        assertThat(properties.getOfficialPermissionStatus())
                .isEqualTo(OptionalLocalPushProperties.PermissionStatus.NOT_EVIDENCED);
        assertThat(properties.isLoopbackQualification()).isFalse();
        assertThat(properties.getReceiverQualification())
                .isEqualTo(OptionalLocalPushProperties.QualificationStatus.NOT_QUALIFIED);
        assertThat(properties.getSenderQualification())
                .isEqualTo(OptionalLocalPushProperties.QualificationStatus.NOT_QUALIFIED);
        assertThat(properties.getReceiverOrigin()).isEmpty();
        assertThat(properties.getLoopbackOrigin()).isEmpty();
        assertThat(properties.getMaximumConcurrency()).isEqualTo(1);
        assertThat(properties.getMaximumPayloadBytes()).isEqualTo(5 * 1024 * 1024);
        assertThat(properties.getMaximumAcknowledgementBytes()).isEqualTo(16 * 1024);
        assertThat(properties.getProtocolVersion()).isEqualTo("1.0");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.isAutomaticRetryEnabled()).isFalse();
        assertThat(properties.getMtls().isRequired()).isTrue();
        assertThat(properties.getMtls().getKeyStoreType()).isEqualTo("Windows-MY");
        assertThat(properties.getMtls().getClientCertificateSha256()).isEmpty();
        assertThat(properties.getProviderOwnerGo().isAbsent()).isTrue();
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void acceptsOnlyACompleteSyntheticRuntimeProfile() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setEnabled(true);
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.SYNTHETIC_LOOPBACK);
        properties.setReceiverOrigin(OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        properties.setReceiverQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.setSenderQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.getMtls().setClientCertificateSha256("a".repeat(64));

        assertThat(validator.validate(properties)).isEmpty();

        properties.setReceiverOrigin("https://127.0.0.1:8445");
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("receiverOriginSafe"));

        properties.setReceiverOrigin(OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        properties.getMtls().setClientCertificateSha256("");
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("runtimeActivationCoherent"));
    }

    @Test
    void providerDerivedConfigurationRequiresPermissionAndRemoteAuthorization() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setEnabled(true);
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        properties.setReceiverOrigin(OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        properties.setReceiverQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.setSenderQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.getMtls().setClientCertificateSha256("a".repeat(64));

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("runtimeActivationCoherent"));

        properties.setRemoteDeliveryAuthorized(true);
        properties.setOfficialPermissionStatus(
                OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE);
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("runtimeActivationCoherent"));

        properties.getProviderOwnerGo().setGoId(
                "50000000-0000-4000-8000-000000000005");
        properties.getProviderOwnerGo().setOwnerGoDocumentSha256("b".repeat(64));
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void ownerGoReferenceIsAbsentByDefaultCompleteForProviderAndForbiddenElsewhere() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.getProviderOwnerGo().setGoId(
                "50000000-0000-4000-8000-000000000005");

        assertThat(properties.getProviderOwnerGo().isAbsentOrComplete()).isFalse();
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("providerOwnerGo.absentOrComplete"));

        properties.getProviderOwnerGo().setOwnerGoDocumentSha256("b".repeat(64));
        assertThat(properties.getProviderOwnerGo().isComplete()).isTrue();
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("runtimeActivationCoherent"));
    }

    @Test
    void acceptsOnlyAnExplicitExactHttpsIpv4LoopbackOriginForQualification() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setLoopbackQualification(true);
        properties.setLoopbackOrigin("https://127.0.0.1:49152");
        assertThat(validator.validate(properties)).isEmpty();

        properties.setLoopbackOrigin("http://127.0.0.1:49152");
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("https://localhost:49152");
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("https://127.0.0.1:49152/import");
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("https://user@127.0.0.1:49152");
        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void acceptsOnlyTheClosedTcpPortRangeBoundaries() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setLoopbackQualification(true);

        properties.setLoopbackOrigin("https://127.0.0.1");
        assertThat(properties.isLoopbackQualificationSafe()).isFalse();
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("https://127.0.0.1:0");
        assertThat(properties.isLoopbackQualificationSafe()).isFalse();
        assertThat(validator.validate(properties)).isNotEmpty();

        properties.setLoopbackOrigin("https://127.0.0.1:1");
        assertThat(properties.isLoopbackQualificationSafe()).isTrue();
        assertThat(validator.validate(properties)).isEmpty();

        properties.setLoopbackOrigin("https://127.0.0.1:65535");

        assertThat(properties.isLoopbackQualificationSafe()).isTrue();
        assertThat(validator.validate(properties)).isEmpty();

        properties.setLoopbackOrigin("https://127.0.0.1:65536");

        assertThat(properties.isLoopbackQualificationSafe()).isFalse();
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("loopbackQualificationSafe"));
    }

    @Test
    void refusesUnsafeContractOverridesRetryTimeoutsAndMtls() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setMaximumConcurrency(2);
        properties.setMaximumPayloadBytes(1);
        properties.setMaximumAcknowledgementBytes(1);
        properties.setProtocolVersion("2.0");
        properties.setConnectTimeout(Duration.ZERO);
        properties.setRequestTimeout(Duration.ofSeconds(11));
        properties.setAutomaticRetryEnabled(true);
        properties.getMtls().setRequired(false);
        properties.getMtls().setKeyStoreType("PKCS12");

        assertThat(validator.validate(properties)).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void allowsOnlyAValidPublicCertificateFingerprintOrNoConfiguredCertificate() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.getMtls().setClientCertificateSha256("A".repeat(64));
        assertThat(properties.getMtls().getClientCertificateSha256())
                .isEqualTo("a".repeat(64));
        assertThat(validator.validate(properties)).isEmpty();

        properties.getMtls().setClientCertificateSha256("not-a-fingerprint");
        assertThat(validator.validate(properties)).isNotEmpty();
    }
}
