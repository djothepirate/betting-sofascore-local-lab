package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.security.SensitiveContentScanner;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public final class J7ExportIntegrityGuard {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "payload_raw",
            "payloadraw",
            "requesturi",
            "requesturl",
            "headers",
            "cookie",
            "cookies",
            "token",
            "tokens",
            "authorization",
            "password",
            "apikey",
            "clientsecret",
            "session",
            "sessionid",
            ".env",
            "backupid",
            "backupidentifier",
            "passphrase",
            "secretphrase");
    private static final Pattern FORBIDDEN_TEXT = Pattern.compile(
            "(?i)(?:[a-z][a-z0-9+.-]*://|(?:^|[^a-z0-9])(?:\\.env|authorization|"
                    + "cookies?|set[\\s_-]?cookies?|bearer|tokens?|passwords?|passwd|"
                    + "api[\\s_-]?keys?|client[\\s_-]?secrets?|passphrase|"
                    + "phrase\\s+secr[eè]te|backup[\\s_-]?(?:id|identifier)|"
                    + "sessions?(?:[\\s_-]?(?:id|data))?|"
                    + "request[\\s_-]?(?:uri|url)s?|headers?)"
                    + "(?:$|[^a-z0-9]))");

    private final J7JsonSchemaValidator schemaValidator;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public J7ExportIntegrityGuard(J7JsonSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    public J7VerifiedEnvelope verify(
            byte[] bytes,
            UUID expectedCanonicalEventId,
            UUID expectedExportId) {
        if (bytes == null || bytes.length == 0 || bytes.length > J7ExportContract.MAXIMUM_BYTES) {
            throw new J7ExportException(J7ExportError.FILE_TOO_LARGE);
        }
        String text = decodeStrictUtf8(bytes);
        if (text.indexOf('\r') >= 0) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
        if (!SensitiveContentScanner.findings(bytes).isEmpty()
                || FORBIDDEN_TEXT.matcher(text).find()) {
            throw new J7ExportException(J7ExportError.SENSITIVE_CONTENT);
        }

        ObjectNode root = schemaValidator.parseAndValidate(bytes);
        if (containsForbiddenKey(root)) {
            throw new J7ExportException(J7ExportError.SENSITIVE_CONTENT);
        }
        try {
            ObjectNode manifest = requiredObject(root, "manifest");
            ObjectNode data = requiredObject(root, "data");
            UUID exportId = UUID.fromString(manifest.required("exportId").stringValue());
            ObjectNode identity = requiredObject(data, "identity");
            UUID canonicalEventId = UUID.fromString(
                    identity.required("canonicalEventId").stringValue());
            if (!exportId.equals(expectedExportId)
                    || !canonicalEventId.equals(expectedCanonicalEventId)) {
                throw new J7ExportException(J7ExportError.IDENTITY_MISMATCH);
            }
            String provider = identity.required("provider").stringValue();
            long providerEventId = identity.required("providerEventId").longValue();
            CanonicalEventIdentity canonicalIdentity = CanonicalEventIdentity.sofascore(
                    providerEventId);
            if (!CanonicalEventIdentity.SOFASCORE.equals(provider)
                    || !canonicalIdentity.value().equals(canonicalEventId)) {
                throw new J7ExportException(J7ExportError.IDENTITY_MISMATCH);
            }

            String dataSha256 = Sha256.hex(mapper.writeValueAsBytes(data));
            ArrayNode sources = requiredArray(manifest, "sources");
            String sourceSetSha256 = Sha256.hex(mapper.writeValueAsBytes(sources));
            if (!dataSha256.equals(manifest.required("dataSha256").stringValue())
                    || !sourceSetSha256.equals(
                    manifest.required("sourceSetSha256").stringValue())) {
                throw new J7ExportException(J7ExportError.INVALID_HASH);
            }
            J7ExportStatus status = J7ExportStatus.valueOf(
                    requiredObject(manifest, "validation").required("status").stringValue());
            requireSourceAndDataCoherence(sources, data);
            requireWarnings(sources, requiredArray(manifest, "warnings"));
            requireDecisionTimeCoherence(manifest, status);
            return new J7VerifiedEnvelope(
                    exportId,
                    canonicalEventId,
                    status,
                    dataSha256,
                    sourceSetSha256,
                    Sha256.hex(bytes),
                    bytes.length,
                    root);
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (JacksonException | IllegalArgumentException | DateTimeException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
    }

    private void requireSourceAndDataCoherence(ArrayNode sources, ObjectNode data) {
        List<String> components = List.of(
                "EVENT_STATE",
                "EVENT_DETAILS",
                "EVENT_STATISTICS",
                "EVENT_INCIDENTS",
                "EVENT_LINEUPS");
        List<String> dataSlots = List.of(
                "eventState",
                "eventDetails",
                "statistics",
                "incidents",
                "lineups");
        for (int index = 0; index < components.size(); index++) {
            ObjectNode source = requiredObject(sources.get(index));
            if (!components.get(index).equals(source.required("component").stringValue())) {
                throw incoherentSource();
            }
            String availability = source.required("availability").stringValue();
            ObjectNode slot = requiredObject(data, dataSlots.get(index));
            if (!availability.equals(slot.required("availability").stringValue())) {
                throw incoherentSource();
            }
            requireSourceReferenceCoherence(source, availability);
            if (index >= 2) {
                JsonNode sourceCompleteness = source.required("completeness");
                JsonNode dataCompleteness = slot.required("completeness");
                if (!sourceCompleteness.equals(dataCompleteness)) {
                    throw incoherentSource();
                }
                if (sourceCompleteness instanceof ObjectNode completeness) {
                    requireCompletenessCoherence(completeness);
                }
                requireFamilyPayloadCoherence(index, availability, slot);
            }
        }
    }

    private static void requireSourceReferenceCoherence(
            ObjectNode source,
            String availability) {
        if ("MISSING".equals(availability)) {
            return;
        }
        String kind = source.required("sourceKind").stringValue();
        String reference = source.required("sourceReference").stringValue();
        String rawState = source.required("rawPayloadState").stringValue();
        if ("PROVIDER_SNAPSHOT".equals(kind)) {
            long snapshotId = source.required("snapshotId").longValue();
            if (!reference.equals("snapshot:" + snapshotId)
                    || "NOT_APPLICABLE".equals(rawState)) {
                throw incoherentSource();
            }
            return;
        }
        if ("SYNTHETIC_FIXTURE".equals(kind)) {
            String fixtureId = source.required("fixtureId").stringValue();
            if (!reference.equals(fixtureId) || !"NOT_APPLICABLE".equals(rawState)) {
                throw incoherentSource();
            }
            return;
        }
        throw incoherentSource();
    }

    private static void requireCompletenessCoherence(ObjectNode completeness) {
        String status = completeness.required("status").stringValue();
        int score = completeness.required("scorePercent").intValue();
        int present = completeness.required("presentSignals").intValue();
        int expected = completeness.required("expectedSignals").intValue();
        int missing = completeness.required("missingPaths").size();
        boolean valid = switch (status) {
            case "COMPLETE" -> expected > 0
                    && present == expected
                    && score == 100
                    && missing == 0;
            case "PARTIAL" -> expected > 0
                    && present < expected
                    && score == Math.floorDiv(present * 100, expected)
                    && missing == expected - present;
            case "EMPTY_VALID" -> expected == 0
                    && present == 0
                    && score == 100
                    && missing == 0;
            case "UNAVAILABLE" -> expected == 0
                    && present == 0
                    && score == 0
                    && missing == 0;
            default -> false;
        };
        if (!valid) {
            throw incoherentSource();
        }
    }

    private static void requireFamilyPayloadCoherence(
            int sourceIndex,
            String availability,
            ObjectNode slot) {
        String payloadName = switch (sourceIndex) {
            case 2 -> "metrics";
            case 3 -> "incidents";
            case 4 -> "lineups";
            default -> throw incoherentSource();
        };
        JsonNode payload = slot.required(payloadName);
        if ("MISSING".equals(availability) || "UNAVAILABLE".equals(availability)) {
            if (!payload.isNull()) {
                throw incoherentSource();
            }
            return;
        }
        if ("EMPTY_VALID".equals(availability)) {
            if (sourceIndex < 4) {
                if (!payload.isArray() || !payload.isEmpty()) {
                    throw incoherentSource();
                }
                return;
            }
            ObjectNode lineups = requiredObject(payload);
            ObjectNode home = requiredObject(lineups, "home");
            ObjectNode away = requiredObject(lineups, "away");
            if (lineups.required("confirmed").booleanValue()
                    || !home.required("formation").isNull()
                    || !away.required("formation").isNull()
                    || !home.required("players").isEmpty()
                    || !away.required("players").isEmpty()) {
                throw incoherentSource();
            }
            return;
        }
        if ("PRESENT".equals(availability)) {
            if (sourceIndex < 4) {
                if (!payload.isArray() || payload.isEmpty()) {
                    throw incoherentSource();
                }
                return;
            }
            ObjectNode lineups = requiredObject(payload);
            ObjectNode home = requiredObject(lineups, "home");
            ObjectNode away = requiredObject(lineups, "away");
            boolean emptyPlaceholder = !lineups.required("confirmed").booleanValue()
                    && home.required("formation").isNull()
                    && away.required("formation").isNull()
                    && home.required("players").isEmpty()
                    && away.required("players").isEmpty();
            if (emptyPlaceholder) {
                throw incoherentSource();
            }
        }
    }

    private void requireWarnings(ArrayNode sources, ArrayNode actual) {
        ArrayNode expected = mapper.createArrayNode();
        appendWarnings(expected, sources, "MISSING_COMPONENT", source ->
                "MISSING".equals(source.required("availability").stringValue()));
        appendWarnings(expected, sources, "UNAVAILABLE_COMPONENT", source ->
                "UNAVAILABLE".equals(source.required("availability").stringValue()));
        appendWarnings(expected, sources, "PARTIAL_COMPLETENESS", source -> {
            JsonNode completeness = source.required("completeness");
            return completeness instanceof ObjectNode object
                    && "PARTIAL".equals(object.required("status").stringValue());
        });
        appendWarnings(expected, sources, "SYNTHETIC_SOURCE", source ->
                !source.required("sourceKind").isNull()
                        && "SYNTHETIC_FIXTURE".equals(
                        source.required("sourceKind").stringValue()));
        List<String> kinds = sources.valueStream()
                .map(source -> source.required("sourceKind"))
                .filter(value -> !value.isNull())
                .map(JsonNode::stringValue)
                .distinct()
                .toList();
        if (kinds.size() > 1) {
            expected.add(warning("MIXED_SOURCE_KINDS", null));
        }
        appendWarnings(expected, sources, "RAW_PAYLOAD_PURGED", source ->
                !source.required("rawPayloadState").isNull()
                        && "PAYLOAD_PURGED".equals(
                        source.required("rawPayloadState").stringValue()));
        appendWarnings(expected, sources, "RAW_PAYLOAD_LEGACY_ABSENT", source ->
                !source.required("rawPayloadState").isNull()
                        && "LEGACY_ABSENT".equals(
                        source.required("rawPayloadState").stringValue()));
        if (!expected.equals(actual)) {
            throw incoherentSource();
        }
    }

    private void appendWarnings(
            ArrayNode warnings,
            ArrayNode sources,
            String code,
            java.util.function.Predicate<ObjectNode> predicate) {
        sources.valueStream()
                .map(J7ExportIntegrityGuard::requiredObject)
                .filter(predicate)
                .map(source -> warning(code, source.required("component").stringValue()))
                .forEach(warnings::add);
    }

    private ObjectNode warning(String code, String component) {
        ObjectNode warning = mapper.createObjectNode();
        warning.put("code", code);
        if (component == null) {
            warning.putNull("component");
        }
        else {
            warning.put("component", component);
        }
        return warning;
    }

    private static void requireDecisionTimeCoherence(
            ObjectNode manifest,
            J7ExportStatus status) {
        Instant generatedAt = Instant.parse(manifest.required("generatedAt").stringValue());
        JsonNode decidedAtNode = requiredObject(manifest, "validation").required("decidedAt");
        if (status.isTerminal()
                && Instant.parse(decidedAtNode.stringValue()).isBefore(generatedAt)) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
    }

    private static ArrayNode requiredArray(ObjectNode parent, String name) {
        JsonNode child = parent.required(name);
        if (child instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
    }

    private static ObjectNode requiredObject(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
    }

    private static J7ExportException incoherentSource() {
        return new J7ExportException(J7ExportError.INCOHERENT_SOURCE);
    }

    private static ObjectNode requiredObject(ObjectNode parent, String name) {
        JsonNode child = parent.required(name);
        if (child instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
    }

    private static boolean containsForbiddenKey(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream().anyMatch(entry -> {
                String canonical = entry.getKey()
                        .replace("_", "")
                        .replace("-", "")
                        .toLowerCase(Locale.ROOT);
                return FORBIDDEN_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))
                        || FORBIDDEN_KEYS.contains(canonical)
                        || containsForbiddenKey(entry.getValue());
            });
        }
        if (node.isArray()) {
            return node.valueStream().anyMatch(J7ExportIntegrityGuard::containsForbiddenKey);
        }
        return false;
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        }
        catch (CharacterCodingException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
        }
    }
}
