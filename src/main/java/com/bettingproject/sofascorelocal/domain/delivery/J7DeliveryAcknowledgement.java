package com.bettingproject.sofascorelocal.domain.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * Strict minimized acknowledgement. It intentionally has no payload, free-form
 * message, path, URI, header, certificate or session field.
 */
public record J7DeliveryAcknowledgement(
        String protocolVersion,
        UUID remoteImportId,
        J7DeliveryAcknowledgementStatus status,
        UUID exportId,
        String fileSha256,
        String dataSha256,
        Instant receivedAt) {

    public static final String SUPPORTED_PROTOCOL_VERSION = "1.0";

    public J7DeliveryAcknowledgement {
        if (!SUPPORTED_PROTOCOL_VERSION.equals(protocolVersion)) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_PROTOCOL_VERSION);
        }
        remoteImportId = J7DeliveryValueGuard.requireUuid(
                remoteImportId, J7DeliveryError.INVALID_REMOTE_IMPORT_ID);
        if (status == null) {
            throw new J7DeliveryException(
                    J7DeliveryError.INVALID_ACKNOWLEDGEMENT_STATUS);
        }
        exportId = J7DeliveryValueGuard.requireUuid(
                exportId, J7DeliveryError.INVALID_EXPORT_ID);
        fileSha256 = J7DeliveryValueGuard.requireSha256(
                fileSha256, J7DeliveryError.INVALID_FILE_SHA256);
        dataSha256 = J7DeliveryValueGuard.requireSha256(
                dataSha256, J7DeliveryError.INVALID_DATA_SHA256);
        receivedAt = J7DeliveryReceivedAtPolicy.requireExactlyPersistable(
                receivedAt, J7DeliveryError.INVALID_RECEIVED_AT);
    }
}
