package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import org.junit.jupiter.api.Test;

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

    private void assertInvalid(String value, J7DeliveryError expectedError) {
        assertThatThrownBy(() -> parser.parse(value.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(expectedError.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(expectedError);
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
