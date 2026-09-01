package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.security.Sha256;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable, reverified J7 artifact eligible for a separately authorized delivery attempt.
 */
public record J7ValidatedExportArtifact(
        UUID exportId,
        UUID canonicalEventId,
        String schemaId,
        String schemaVersion,
        String dataSha256,
        String fileSha256,
        byte[] content) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final UUID NIL_UUID = new UUID(0, 0);

    public J7ValidatedExportArtifact {
        exportId = requireUuid(exportId, "exportId");
        canonicalEventId = requireUuid(canonicalEventId, "canonicalEventId");
        if (!J7ExportContract.SCHEMA_ID.equals(schemaId)
                || !J7ExportContract.SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("artifact is not the exact J7 canonical v1 schema");
        }
        requireSha256(dataSha256, "dataSha256");
        requireSha256(fileSha256, "fileSha256");
        content = Objects.requireNonNull(content, "content").clone();
        if (content.length < 1 || content.length > J7ExportContract.MAXIMUM_BYTES) {
            throw new IllegalArgumentException("content must be between one byte and 5 MiB");
        }
        if (!fileSha256.equals(Sha256.hex(content))) {
            throw new IllegalArgumentException("content does not match fileSha256");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long sizeBytes() {
        return content.length;
    }

    private static void requireSha256(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
    }

    private static UUID requireUuid(UUID value, String name) {
        if (value == null || NIL_UUID.equals(value)) {
            throw new IllegalArgumentException(name + " must be a non-nil UUID");
        }
        return value;
    }
}
