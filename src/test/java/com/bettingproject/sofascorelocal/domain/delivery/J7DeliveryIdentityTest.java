package com.bettingproject.sofascorelocal.domain.delivery;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryIdentityTest {

    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000001");
    private static final String FILE_SHA256 = "a".repeat(64);

    @Test
    void derivesAStableExactIdempotencyKeyFromExportAndTerminalFileHash() {
        J7DeliveryIdentity first = new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256);
        J7DeliveryIdentity second = new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256);

        assertThat(first).isEqualTo(second);
        assertThat(first.idempotencyKey()).isEqualTo(
                "j7:" + EXPORT_ID + ":sha256:" + FILE_SHA256);
        assertThat(first.idempotencyKey()).hasSize(111);
    }

    @Test
    void rejectsNullNilOrNonRfc4122ExportIdentifiersWithSafeErrors() {
        assertError(
                () -> new J7DeliveryIdentity(null, FILE_SHA256),
                J7DeliveryError.INVALID_EXPORT_ID);
        assertError(
                () -> new J7DeliveryIdentity(new UUID(0, 0), FILE_SHA256),
                J7DeliveryError.INVALID_EXPORT_ID);
        assertError(
                () -> new J7DeliveryIdentity(
                        UUID.fromString("70000000-0000-4000-0000-000000000001"),
                        FILE_SHA256),
                J7DeliveryError.INVALID_EXPORT_ID);
    }

    @Test
    void acceptsOnlyAnExactLowerCaseSha256WithoutNormalization() {
        String sensitiveRejectedValue = "secret-value-that-must-not-be-reported";
        String[] invalidValues = {
                null,
                "",
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                " " + FILE_SHA256,
                FILE_SHA256 + " ",
                "g".repeat(64),
                sensitiveRejectedValue
        };

        for (String invalidValue : invalidValues) {
            assertThatThrownBy(() -> new J7DeliveryIdentity(EXPORT_ID, invalidValue))
                    .isInstanceOfSatisfying(J7DeliveryException.class, exception -> {
                        assertThat(exception.error())
                                .isEqualTo(J7DeliveryError.INVALID_FILE_SHA256);
                        assertThat(exception.getMessage())
                                .isEqualTo(J7DeliveryError.INVALID_FILE_SHA256.name())
                                .doesNotContain(sensitiveRejectedValue);
                    });
        }
    }

    private static void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            J7DeliveryError expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(J7DeliveryException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(expected);
                    assertThat(exception.getMessage()).isEqualTo(expected.name());
                    assertThat(exception.getCause()).isNull();
                });
    }
}
