package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7ValidatedExportArtifactTest {

    private static final UUID EXPORT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EVENT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");

    @Test
    void acceptsOnlyTheExactBoundedJ7CanonicalV1ArtifactAndDefensivelyCopiesBytes() {
        byte[] bytes = new byte[] {'{', '}'};
        J7ValidatedExportArtifact artifact = artifact(
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                bytes);

        bytes[0] = '[';
        byte[] exposed = artifact.content();
        exposed[0] = '[';

        assertThat(artifact.content()).containsExactly((byte) '{', (byte) '}');
        assertThat(artifact.sizeBytes()).isEqualTo(2);
    }

    @Test
    void rejectsAnotherSchemaAnEmptyBodyAndNilIdentifiers() {
        assertThatThrownBy(() -> artifact("wrong", J7ExportContract.SCHEMA_VERSION,
                new byte[] {'{', '}'}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new J7ValidatedExportArtifact(
                EXPORT_ID,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                "a".repeat(64),
                "b".repeat(64),
                new byte[] {'{', '}'}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content does not match fileSha256");
        assertThatThrownBy(() -> artifact(J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new J7ValidatedExportArtifact(
                new UUID(0, 0),
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                "a".repeat(64),
                "b".repeat(64),
                new byte[] {'{', '}'}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static J7ValidatedExportArtifact artifact(
            String schemaId,
            String schemaVersion,
            byte[] bytes) {
        return new J7ValidatedExportArtifact(
                EXPORT_ID,
                EVENT_ID,
                schemaId,
                schemaVersion,
                "a".repeat(64),
                Sha256.hex(bytes),
                bytes);
    }
}
