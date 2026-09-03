package com.bettingproject.sofascorelocal.domain.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryAcknowledgementTest {

    private static final UUID REMOTE_IMPORT_ID = UUID.fromString(
            "71000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000001");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void keepsOnlyTheSevenStrictMinimizedProtocolFields() {
        assertThat(J7DeliveryAcknowledgement.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "protocolVersion",
                        "remoteImportId",
                        "status",
                        "exportId",
                        "fileSha256",
                        "dataSha256",
                        "receivedAt");
        assertThat(J7DeliveryAcknowledgementStatus.values())
                .containsExactly(
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        J7DeliveryAcknowledgementStatus.DUPLICATE);
    }

    @Test
    void acceptsImportedAndDuplicateAcknowledgementsWithTypedUtcEvidence() {
        for (J7DeliveryAcknowledgementStatus status
                : J7DeliveryAcknowledgementStatus.values()) {
            J7DeliveryAcknowledgement acknowledgement = acknowledgement(status);

            assertThat(acknowledgement.protocolVersion()).isEqualTo("1.0");
            assertThat(acknowledgement.remoteImportId()).isEqualTo(REMOTE_IMPORT_ID);
            assertThat(acknowledgement.status()).isEqualTo(status);
            assertThat(acknowledgement.exportId()).isEqualTo(EXPORT_ID);
            assertThat(acknowledgement.fileSha256()).isEqualTo(FILE_SHA256);
            assertThat(acknowledgement.dataSha256()).isEqualTo(DATA_SHA256);
            assertThat(acknowledgement.receivedAt()).isEqualTo(RECEIVED_AT);
        }
    }

    @Test
    void rejectsAnyProtocolVersionOtherThanTheExactSupportedValue() {
        for (String invalid : new String[] {null, "", "1", "1.0 ", "v1.0", "2.0"}) {
            assertError(
                    () -> new J7DeliveryAcknowledgement(
                            invalid,
                            REMOTE_IMPORT_ID,
                            J7DeliveryAcknowledgementStatus.IMPORTED,
                            EXPORT_ID,
                            FILE_SHA256,
                            DATA_SHA256,
                            RECEIVED_AT),
                    J7DeliveryError.INVALID_PROTOCOL_VERSION,
                    invalid);
        }
    }

    @Test
    void rejectsNullNilOrNonRfc4122RemoteAndExportIdentifiers() {
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", null, J7DeliveryAcknowledgementStatus.IMPORTED,
                        EXPORT_ID, FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_REMOTE_IMPORT_ID,
                null);
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", new UUID(0, 0),
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        EXPORT_ID, FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_REMOTE_IMPORT_ID,
                null);
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0",
                        UUID.fromString("71000000-0000-4000-0000-000000000001"),
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        EXPORT_ID, FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_REMOTE_IMPORT_ID,
                null);
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", REMOTE_IMPORT_ID,
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        null, FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_EXPORT_ID,
                null);
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", REMOTE_IMPORT_ID,
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        UUID.fromString("70000000-0000-4000-0000-000000000001"),
                        FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_EXPORT_ID,
                null);
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", REMOTE_IMPORT_ID,
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        new UUID(0, 0), FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_EXPORT_ID,
                null);
    }

    @Test
    void rejectsMissingStatusOrNonPersistableReceivedInstant() {
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", REMOTE_IMPORT_ID, null, EXPORT_ID,
                        FILE_SHA256, DATA_SHA256, RECEIVED_AT),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT_STATUS,
                null);
        assertError(
                () -> new J7DeliveryAcknowledgement(
                        "1.0", REMOTE_IMPORT_ID,
                        J7DeliveryAcknowledgementStatus.IMPORTED,
                        EXPORT_ID, FILE_SHA256, DATA_SHA256, null),
                J7DeliveryError.INVALID_RECEIVED_AT,
                null);
        for (Instant invalid : new Instant[] {
                Instant.parse("2026-09-01T10:00:00.123456789Z"),
                Instant.parse("0000-12-31T23:59:59.999999Z"),
                Instant.parse("+10000-01-01T00:00:00Z")
        }) {
            assertError(
                    () -> new J7DeliveryAcknowledgement(
                            "1.0", REMOTE_IMPORT_ID,
                            J7DeliveryAcknowledgementStatus.IMPORTED,
                            EXPORT_ID, FILE_SHA256, DATA_SHA256, invalid),
                    J7DeliveryError.INVALID_RECEIVED_AT,
                    null);
        }
    }

    @Test
    void rejectsMalformedFileAndDataHashesWithoutLeakingValues() {
        String sensitive = "do-not-report-this-rejected-value";
        String[] invalidHashes = {
                null,
                "",
                "a".repeat(63),
                "A".repeat(64),
                "z".repeat(64),
                sensitive
        };
        for (String invalid : invalidHashes) {
            assertError(
                    () -> new J7DeliveryAcknowledgement(
                            "1.0", REMOTE_IMPORT_ID,
                            J7DeliveryAcknowledgementStatus.IMPORTED,
                            EXPORT_ID, invalid, DATA_SHA256, RECEIVED_AT),
                    J7DeliveryError.INVALID_FILE_SHA256,
                    sensitive);
            assertError(
                    () -> new J7DeliveryAcknowledgement(
                            "1.0", REMOTE_IMPORT_ID,
                            J7DeliveryAcknowledgementStatus.IMPORTED,
                            EXPORT_ID, FILE_SHA256, invalid, RECEIVED_AT),
                    J7DeliveryError.INVALID_DATA_SHA256,
                    sensitive);
        }
    }

    private static J7DeliveryAcknowledgement acknowledgement(
            J7DeliveryAcknowledgementStatus status) {
        return new J7DeliveryAcknowledgement(
                "1.0",
                REMOTE_IMPORT_ID,
                status,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                RECEIVED_AT);
    }

    private static void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            J7DeliveryError expected,
            String forbidden) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(J7DeliveryException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(expected);
                    assertThat(exception.getMessage()).isEqualTo(expected.name());
                    if (forbidden != null && !forbidden.isEmpty()) {
                        assertThat(exception.getMessage()).doesNotContain(forbidden);
                    }
                });
    }
}
