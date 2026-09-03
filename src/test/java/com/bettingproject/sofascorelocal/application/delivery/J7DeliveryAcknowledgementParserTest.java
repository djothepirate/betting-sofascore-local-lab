package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryAcknowledgementParserTest {

    private static final UUID REMOTE_IMPORT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-01T08:00:00Z");

    private final J7DeliveryAcknowledgementParser parser =
            new J7DeliveryAcknowledgementParser();

    @Test
    void parsesTheExactMinimizedAcknowledgement() {
        var acknowledgement = parser.parse(json("IMPORTED").getBytes(StandardCharsets.UTF_8));

        assertThat(acknowledgement.protocolVersion()).isEqualTo("1.0");
        assertThat(acknowledgement.remoteImportId()).isEqualTo(REMOTE_IMPORT_ID);
        assertThat(acknowledgement.status())
                .isEqualTo(J7DeliveryAcknowledgementStatus.IMPORTED);
        assertThat(acknowledgement.exportId()).isEqualTo(EXPORT_ID);
        assertThat(acknowledgement.fileSha256()).isEqualTo(FILE_SHA256);
        assertThat(acknowledgement.dataSha256()).isEqualTo(DATA_SHA256);
        assertThat(acknowledgement.receivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void acceptsTheOnlyOtherContractStatusDuplicate() {
        assertThat(parser.parse(json("DUPLICATE").getBytes(StandardCharsets.UTF_8)).status())
                .isEqualTo(J7DeliveryAcknowledgementStatus.DUPLICATE);
    }

    @Test
    void rejectsUnknownMissingDuplicateOrWronglyTypedFieldsWithoutLeakingInput() {
        assertInvalid(json("UNKNOWN"), J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED").replace(
                ",\"receivedAt\":\"2026-09-01T08:00:00Z\"", ""),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED").replace(
                "\"status\":\"IMPORTED\"",
                "\"status\":\"IMPORTED\",\"extra\":\"forbidden\""),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED").replace(
                "\"protocolVersion\":\"1.0\"",
                "\"protocolVersion\":\"1.0\",\"protocolVersion\":\"1.0\""),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED").replace(
                "\"remoteImportId\":\"" + REMOTE_IMPORT_ID + "\"",
                "\"remoteImportId\":1"),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED").replace(
                REMOTE_IMPORT_ID.toString(), "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED").replace(
                RECEIVED_AT.toString(), "2026-09-01T08:00:00.1234567890123456Z"),
                J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
    }

    @Test
    void rejectsMalformedTrailingEmptyAndOversizedDocuments() {
        assertInvalid("{", J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid(json("IMPORTED") + "{}", J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        assertInvalid("", J7DeliveryError.INVALID_ACKNOWLEDGEMENT);

        byte[] oversized = new byte[J7DeliveryAcknowledgementParser.MAXIMUM_BYTES + 1];
        assertThatThrownBy(() -> parser.parse(oversized))
                .isInstanceOf(J7DeliveryException.class)
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(J7DeliveryError.ACKNOWLEDGEMENT_TOO_LARGE);
    }

    @Test
    void rejectsBomUtf16NulAndMalformedUtf8Encodings() {
        byte[] validUtf8 = json("IMPORTED").getBytes(StandardCharsets.UTF_8);
        byte[] utf8Bom = new byte[validUtf8.length + 3];
        utf8Bom[0] = (byte) 0xef;
        utf8Bom[1] = (byte) 0xbb;
        utf8Bom[2] = (byte) 0xbf;
        System.arraycopy(validUtf8, 0, utf8Bom, 3, validUtf8.length);

        assertInvalid(utf8Bom);
        assertInvalid(json("IMPORTED").getBytes(StandardCharsets.UTF_16LE));
        assertInvalid(json("IMPORTED").getBytes(StandardCharsets.UTF_16BE));
        assertInvalid(new byte[] {'{', 0, '}'});
        assertInvalid(new byte[] {(byte) 0xc3, 0x28});
    }

    @Test
    void requiresTheCanonicalUtcInstantRepresentation() {
        assertInvalid(json("IMPORTED").replace(
                RECEIVED_AT.toString(),
                "2026-09-01T08:00:00+00:00").getBytes(StandardCharsets.UTF_8));
        assertInvalid(json("IMPORTED").replace(
                RECEIVED_AT.toString(),
                "2026-09-01T08:00:00.000Z").getBytes(StandardCharsets.UTF_8));

        String canonicalFractional = "2026-09-01T08:00:00.123456Z";
        var acknowledgement = parser.parse(json("IMPORTED").replace(
                RECEIVED_AT.toString(),
                canonicalFractional).getBytes(StandardCharsets.UTF_8));
        assertThat(acknowledgement.receivedAt()).isEqualTo(Instant.parse(canonicalFractional));

        assertInvalid(
                json("IMPORTED").replace(
                        RECEIVED_AT.toString(),
                        "2026-09-01T08:00:00.123456789Z"),
                J7DeliveryError.INVALID_RECEIVED_AT);
        assertInvalid(
                json("IMPORTED").replace(
                        RECEIVED_AT.toString(),
                        "+10000-01-01T00:00:00Z"),
                J7DeliveryError.INVALID_RECEIVED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0001-01-01T00:00:00Z",
            "9999-12-31T23:59:59.999999Z"
    })
    void acceptsTheExactInclusivePersistenceBoundaryInstants(String boundary) {
        var acknowledgement = parser.parse(json("IMPORTED").replace(
                RECEIVED_AT.toString(), boundary).getBytes(StandardCharsets.UTF_8));

        assertThat(acknowledgement.receivedAt()).isEqualTo(Instant.parse(boundary));
    }

    private void assertInvalid(String value, J7DeliveryError expectedError) {
        assertThatThrownBy(() -> parser.parse(value.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(expectedError.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(expectedError);
    }

    private void assertInvalid(byte[] value) {
        assertThatThrownBy(() -> parser.parse(value))
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(J7DeliveryError.INVALID_ACKNOWLEDGEMENT.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
    }

    private static String json(String status) {
        return "{" +
                "\"protocolVersion\":\"1.0\"," +
                "\"remoteImportId\":\"" + REMOTE_IMPORT_ID + "\"," +
                "\"status\":\"" + status + "\"," +
                "\"exportId\":\"" + EXPORT_ID + "\"," +
                "\"fileSha256\":\"" + FILE_SHA256 + "\"," +
                "\"dataSha256\":\"" + DATA_SHA256 + "\"," +
                "\"receivedAt\":\"" + RECEIVED_AT + "\"" +
                "}";
    }
}
