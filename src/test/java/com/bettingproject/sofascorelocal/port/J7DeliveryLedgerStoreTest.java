package com.bettingproject.sofascorelocal.port;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryLedgerStoreTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "27000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000027");
    private static final String FILE_SHA = "a".repeat(64);
    private static final String DATA_SHA = "b".repeat(64);
    private static final String KEY = "j7:" + EXPORT_ID + ":sha256:" + FILE_SHA;
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void exposesExactlyTheSixAdrStatesAndOnlyUnknownAsARepeatableResult() {
        assertThat(J7DeliveryLedgerStore.DeliveryState.values())
                .containsExactly(
                        J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED,
                        J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                        J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                        J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED,
                        J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                        J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED
                .forbidsAnotherClaim()).isFalse();
        assertThat(J7DeliveryLedgerStore.DeliveryState.DELIVERED.forbidsAnotherClaim()).isTrue();
        assertThat(J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED
                .forbidsAnotherClaim()).isTrue();
        assertThat(J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL
                .forbidsAnotherClaim()).isTrue();
    }

    @Test
    void validatesClaimAndSnapshotMetadataWithoutAcceptingPayloadOrDiagnostics() {
        var receipt = new J7DeliveryLedgerStore.ClaimReceipt(
                DELIVERY_ID,
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                KEY,
                STARTED_AT);
        var snapshot = new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                KEY,
                "1.0",
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                1,
                STARTED_AT,
                STARTED_AT);
        var notAttempted = new J7DeliveryLedgerStore.DeliverySnapshot(
                UUID.fromString("27000000-0000-4000-8000-000000000002"),
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                KEY,
                "1.0",
                J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED,
                0,
                STARTED_AT,
                STARTED_AT);

        assertThat(receipt.idempotencyKey()).isEqualTo(KEY);
        assertThat(notAttempted.attemptCount()).isZero();
        assertThat(snapshot)
                .extracting(
                        J7DeliveryLedgerStore.DeliverySnapshot::exportId,
                        J7DeliveryLedgerStore.DeliverySnapshot::fileSha256,
                        J7DeliveryLedgerStore.DeliverySnapshot::dataSha256,
                        J7DeliveryLedgerStore.DeliverySnapshot::protocolVersion,
                        J7DeliveryLedgerStore.DeliverySnapshot::attemptCount)
                .containsExactly(EXPORT_ID, FILE_SHA, DATA_SHA, "1.0", 1);
        assertThat(J7DeliveryLedgerStore.DeliverySnapshot.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("payload", "body", "diagnostic", "privateKey", "certificate");
    }

    @Test
    void rejectsMalformedIdentityAndKeepsLedgerErrorsValueFree() {
        String sensitive = "do-not-leak-this-value";

        assertThatThrownBy(() -> new J7DeliveryLedgerStore.ClaimReceipt(
                DELIVERY_ID,
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                sensitive,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(sensitive);

        var exception = new J7DeliveryLedgerStore.LedgerException(
                J7DeliveryLedgerStore.LedgerFailure.STORAGE_UNAVAILABLE);
        assertThat(exception.getMessage())
                .isEqualTo("J7_DELIVERY_LEDGER_STORAGE_UNAVAILABLE")
                .doesNotContain(sensitive);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void rejectsNonCanonicalSnapshotCorrelationAndSubMicrosecondClaimTimes() {
        assertThatThrownBy(() -> new J7DeliveryLedgerStore.ClaimReceipt(
                DELIVERY_ID,
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                KEY,
                STARTED_AT.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");

        assertThatThrownBy(() -> new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                "j7:" + EXPORT_ID + ":sha256:" + "c".repeat(64),
                "1.0",
                J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED,
                0,
                STARTED_AT,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match exportId and fileSha256");

        assertThatThrownBy(() -> new J7DeliveryLedgerStore.ClaimReceipt(
                new UUID(0, 0),
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                KEY,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-nil RFC 4122 UUID");
    }
}
