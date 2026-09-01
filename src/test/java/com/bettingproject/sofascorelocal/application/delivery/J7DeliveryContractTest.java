package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgement;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryContractTest {

    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000001");
    private static final UUID REMOTE_IMPORT_ID = UUID.fromString(
            "71000000-0000-4000-8000-000000000001");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-01T10:00:00Z");
    private static final J7DeliveryIdentity IDENTITY =
            new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256);

    @Test
    void fixesThePureV1ProtocolWithoutAnEndpointOrNetworkClient() {
        assertThat(J7DeliveryContract.PROTOCOL_VERSION).isEqualTo("1.0");
        assertThat(J7DeliveryContract.HTTP_METHOD).isEqualTo("POST");
        assertThat(J7DeliveryContract.RELATIVE_IMPORT_PATH)
                .isEqualTo("/api/imports/sofascore/j7-canonical-events");
        assertThat(J7DeliveryContract.REQUEST_MEDIA_TYPE)
                .isEqualTo("application/vnd.betting-project.j7-canonical-event+json;version=1.0");
        assertThat(J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE)
                .isEqualTo("application/vnd.betting-project.j7-delivery-ack+json;version=1.0");
        assertThat(J7DeliveryContract.IDEMPOTENCY_HEADER)
                .isEqualTo("Idempotency-Key");
        assertThat(J7DeliveryContract.PROTOCOL_HEADER)
                .isEqualTo("X-J7-Protocol-Version");
        assertThat(J7DeliveryContract.EXPORT_ID_HEADER)
                .isEqualTo("X-J7-Export-Id");
        assertThat(J7DeliveryContract.FILE_SHA256_HEADER)
                .isEqualTo("X-J7-File-SHA256");
        assertThat(J7DeliveryContract.DATA_SHA256_HEADER)
                .isEqualTo("X-J7-Data-SHA256");
        assertThat(J7DeliveryContract.MAXIMUM_PAYLOAD_BYTES)
                .isEqualTo(5L * 1024L * 1024L);
    }

    @Test
    void exposesOnlyTheStableIdentityDerivedIdempotencyKey() {
        assertThat(J7DeliveryContract.idempotencyKey(IDENTITY))
                .isEqualTo("j7:" + EXPORT_ID + ":sha256:" + FILE_SHA256);

        assertError(
                () -> J7DeliveryContract.idempotencyKey(null),
                J7DeliveryError.INVALID_EXPORT_ID);
    }

    @Test
    void enforcesTheExistingJ7PayloadBoundaryExactly() {
        assertThatCode(() -> J7DeliveryContract.requirePayloadSize(1))
                .doesNotThrowAnyException();
        assertThatCode(() -> J7DeliveryContract.requirePayloadSize(
                J7DeliveryContract.MAXIMUM_PAYLOAD_BYTES))
                .doesNotThrowAnyException();

        for (long invalid : new long[] {
                Long.MIN_VALUE,
                -1,
                0,
                J7DeliveryContract.MAXIMUM_PAYLOAD_BYTES + 1,
                Long.MAX_VALUE
        }) {
            assertError(
                    () -> J7DeliveryContract.requirePayloadSize(invalid),
                    J7DeliveryError.INVALID_PAYLOAD_SIZE);
        }
    }

    @Test
    void acceptsOnlyAnAcknowledgementMatchingAllLocalEvidence() {
        J7DeliveryAcknowledgement imported = acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256);
        J7DeliveryAcknowledgement duplicate = acknowledgement(
                J7DeliveryAcknowledgementStatus.DUPLICATE,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256);

        assertThatCode(() -> J7DeliveryContract.requireAcknowledgementMatches(
                IDENTITY, DATA_SHA256, imported)).doesNotThrowAnyException();
        assertThat(J7DeliveryContract.acknowledgedState(
                J7DeliveryState.IN_FLIGHT,
                IDENTITY,
                DATA_SHA256,
                imported)).isEqualTo(J7DeliveryState.DELIVERED);
        assertThat(J7DeliveryContract.acknowledgedState(
                J7DeliveryState.IN_FLIGHT,
                IDENTITY,
                DATA_SHA256,
                duplicate)).isEqualTo(J7DeliveryState.DUPLICATE_CONFIRMED);
    }

    @Test
    void rejectsEveryEvidenceMismatchWithOneSafeCode() {
        J7DeliveryAcknowledgement wrongExport = acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                UUID.fromString("70000000-0000-4000-8000-000000000002"),
                FILE_SHA256,
                DATA_SHA256);
        J7DeliveryAcknowledgement wrongFile = acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                "c".repeat(64),
                DATA_SHA256);
        J7DeliveryAcknowledgement wrongData = acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                FILE_SHA256,
                "d".repeat(64));

        assertMismatch(null, DATA_SHA256, acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256));
        assertMismatch(IDENTITY, null, acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256));
        assertMismatch(IDENTITY, "B".repeat(64), acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256));
        assertMismatch(IDENTITY, DATA_SHA256, null);
        assertMismatch(IDENTITY, DATA_SHA256, wrongExport);
        assertMismatch(IDENTITY, DATA_SHA256, wrongFile);
        assertMismatch(IDENTITY, DATA_SHA256, wrongData);
    }

    @Test
    void validatesEvidenceBeforeApplyingTheStateTransition() {
        J7DeliveryAcknowledgement acknowledgement = acknowledgement(
                J7DeliveryAcknowledgementStatus.IMPORTED,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256);

        assertError(
                () -> J7DeliveryContract.acknowledgedState(
                        J7DeliveryState.NOT_ATTEMPTED,
                        IDENTITY,
                        DATA_SHA256,
                        acknowledgement),
                J7DeliveryError.INVALID_TRANSITION);
    }

    private static J7DeliveryAcknowledgement acknowledgement(
            J7DeliveryAcknowledgementStatus status,
            UUID exportId,
            String fileSha256,
            String dataSha256) {
        return new J7DeliveryAcknowledgement(
                J7DeliveryContract.PROTOCOL_VERSION,
                REMOTE_IMPORT_ID,
                status,
                exportId,
                fileSha256,
                dataSha256,
                RECEIVED_AT);
    }

    private static void assertMismatch(
            J7DeliveryIdentity identity,
            String dataSha256,
            J7DeliveryAcknowledgement acknowledgement) {
        assertError(
                () -> J7DeliveryContract.requireAcknowledgementMatches(
                        identity, dataSha256, acknowledgement),
                J7DeliveryError.ACKNOWLEDGEMENT_MISMATCH);
    }

    private static void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            J7DeliveryError expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(J7DeliveryException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(expected);
                    assertThat(exception.getMessage()).isEqualTo(expected.name());
                    assertThat(exception.getMessage())
                            .doesNotContain(FILE_SHA256, DATA_SHA256);
                });
    }
}
