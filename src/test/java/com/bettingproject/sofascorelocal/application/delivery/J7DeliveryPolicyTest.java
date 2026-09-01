package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import org.junit.jupiter.api.Test;

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

    private static void assertError(Runnable action, J7DeliveryError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(error.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(error);
    }
}
