package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgement;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict, bounded parser for the minimized receiver acknowledgement. */
public final class J7DeliveryAcknowledgementParser {

    public static final int MAXIMUM_BYTES = 16 * 1024;

    private static final Set<String> EXACT_FIELDS = Set.of(
            "protocolVersion",
            "remoteImportId",
            "status",
            "exportId",
            "fileSha256",
            "dataSha256",
            "receivedAt");
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public J7DeliveryAcknowledgement parse(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < 1) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
        }
        if (bytes.length > MAXIMUM_BYTES) {
            throw new J7DeliveryException(J7DeliveryError.ACKNOWLEDGEMENT_TOO_LARGE);
        }
        try {
            JsonNode parsed = JSON_MAPPER.readTree(strictUtf8(bytes));
            if (!(parsed instanceof ObjectNode object)
                    || object.size() != EXACT_FIELDS.size()
                    || !EXACT_FIELDS.stream().allMatch(object::has)) {
                throw invalidAcknowledgement();
            }
            return new J7DeliveryAcknowledgement(
                    text(object, "protocolVersion"),
                    canonicalUuid(object, "remoteImportId"),
                    J7DeliveryAcknowledgementStatus.valueOf(text(object, "status")),
                    canonicalUuid(object, "exportId"),
                    text(object, "fileSha256"),
                    text(object, "dataSha256"),
                    canonicalInstant(object, "receivedAt"));
        }
        catch (J7DeliveryException exception) {
            throw exception;
        }
        catch (DateTimeParseException | IllegalArgumentException | tools.jackson.core.JacksonException exception) {
            throw invalidAcknowledgement();
        }
    }

    private static String strictUtf8(byte[] bytes) {
        if (startsWith(bytes, 0xef, 0xbb, 0xbf)
                || startsWith(bytes, 0xfe, 0xff)
                || startsWith(bytes, 0xff, 0xfe)) {
            throw invalidAcknowledgement();
        }
        for (byte value : bytes) {
            if (value == 0) {
                throw invalidAcknowledgement();
            }
        }
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (decoded.indexOf('\ufeff') >= 0 || decoded.indexOf('\u0000') >= 0) {
                throw invalidAcknowledgement();
            }
            return decoded;
        }
        catch (CharacterCodingException exception) {
            throw invalidAcknowledgement();
        }
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static String text(ObjectNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isString()) {
            throw invalidAcknowledgement();
        }
        return value.stringValue();
    }

    private static UUID canonicalUuid(ObjectNode object, String name) {
        String value = text(object, name);
        if (!CANONICAL_UUID.matcher(value).matches()) {
            throw invalidAcknowledgement();
        }
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw invalidAcknowledgement();
        }
        return parsed;
    }

    private static Instant canonicalInstant(ObjectNode object, String name) {
        String value = text(object, name);
        if (value.length() < 20 || value.length() > 35) {
            throw invalidAcknowledgement();
        }
        Instant parsed = Instant.parse(value);
        if (!value.endsWith("Z") || !parsed.toString().equals(value)) {
            throw invalidAcknowledgement();
        }
        return parsed;
    }

    private static J7DeliveryException invalidAcknowledgement() {
        return new J7DeliveryException(J7DeliveryError.INVALID_ACKNOWLEDGEMENT);
    }
}
