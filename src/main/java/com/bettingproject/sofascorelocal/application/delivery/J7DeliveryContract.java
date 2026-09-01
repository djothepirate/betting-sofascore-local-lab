package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgement;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryStateMachine;

import java.util.regex.Pattern;

/**
 * Pure protocol contract for a future optional sender. This class performs no
 * I/O and grants no network or implementation authorization.
 */
public final class J7DeliveryContract {

    public static final String PROTOCOL_VERSION =
            J7DeliveryAcknowledgement.SUPPORTED_PROTOCOL_VERSION;
    public static final String HTTP_METHOD = "POST";
    public static final String RELATIVE_IMPORT_PATH =
            "/api/imports/sofascore/j7-canonical-events";
    public static final String REQUEST_MEDIA_TYPE =
            "application/vnd.betting-project.j7-canonical-event+json;version=1.0";
    public static final String ACKNOWLEDGEMENT_MEDIA_TYPE =
            "application/vnd.betting-project.j7-delivery-ack+json;version=1.0";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String PROTOCOL_HEADER = "X-J7-Protocol-Version";
    public static final String EXPORT_ID_HEADER = "X-J7-Export-Id";
    public static final String FILE_SHA256_HEADER = "X-J7-File-SHA256";
    public static final String DATA_SHA256_HEADER = "X-J7-Data-SHA256";
    public static final long MAXIMUM_PAYLOAD_BYTES = J7ExportContract.MAXIMUM_BYTES;

    private static final Pattern LOWER_CASE_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private J7DeliveryContract() {
    }

    public static String idempotencyKey(J7DeliveryIdentity identity) {
        if (identity == null) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_EXPORT_ID);
        }
        return identity.idempotencyKey();
    }

    public static void requirePayloadSize(long payloadSizeBytes) {
        if (payloadSizeBytes < 1 || payloadSizeBytes > MAXIMUM_PAYLOAD_BYTES) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_PAYLOAD_SIZE);
        }
    }

    public static J7DeliveryState acknowledgedState(
            J7DeliveryState current,
            J7DeliveryIdentity expectedIdentity,
            String expectedDataSha256,
            J7DeliveryAcknowledgement acknowledgement) {
        requireAcknowledgementMatches(
                expectedIdentity, expectedDataSha256, acknowledgement);
        return J7DeliveryStateMachine.completeWithAcknowledgement(
                current, acknowledgement.status());
    }

    public static void requireAcknowledgementMatches(
            J7DeliveryIdentity expectedIdentity,
            String expectedDataSha256,
            J7DeliveryAcknowledgement acknowledgement) {
        if (expectedIdentity == null
                || acknowledgement == null
                || expectedDataSha256 == null
                || !LOWER_CASE_SHA_256.matcher(expectedDataSha256).matches()
                || !PROTOCOL_VERSION.equals(acknowledgement.protocolVersion())
                || !expectedIdentity.exportId().equals(acknowledgement.exportId())
                || !expectedIdentity.fileSha256().equals(
                        acknowledgement.fileSha256())
                || !expectedDataSha256.equals(acknowledgement.dataSha256())) {
            throw new J7DeliveryException(
                    J7DeliveryError.ACKNOWLEDGEMENT_MISMATCH);
        }
    }
}
