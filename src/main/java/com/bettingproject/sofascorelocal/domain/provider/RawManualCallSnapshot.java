package com.bettingproject.sofascorelocal.domain.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record RawManualCallSnapshot(
        SofascoreEndpointType endpointType,
        String requestKey,
        Instant requestedAt,
        Instant receivedAt,
        int httpStatus,
        String contentType,
        Duration latency,
        RawPayloadEvidence payload,
        String parserVersion,
        RawSnapshotSchemaStatus schemaStatus,
        String errorCode) {

    private static final int MAXIMUM_REQUEST_KEY_LENGTH = 512;
    private static final int MAXIMUM_CONTENT_TYPE_LENGTH = 160;
    private static final int MAXIMUM_PARSER_VERSION_LENGTH = 32;
    private static final int MAXIMUM_ERROR_CODE_LENGTH = 96;
    private static final Pattern REQUEST_KEY_PATTERN = Pattern.compile(
            "^[A-Z_]+(?:\\|[A-Za-z][A-Za-z0-9]*=[A-Za-z0-9._-]+)*$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]+$");

    public RawManualCallSnapshot {
        Objects.requireNonNull(endpointType, "endpointType");
        requestKey = requireText(requestKey, "requestKey", MAXIMUM_REQUEST_KEY_LENGTH);
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        contentType = requireText(contentType, "contentType", MAXIMUM_CONTENT_TYPE_LENGTH);
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(payload, "payload");
        parserVersion = requireText(
                parserVersion, "parserVersion", MAXIMUM_PARSER_VERSION_LENGTH);
        Objects.requireNonNull(schemaStatus, "schemaStatus");

        if (!REQUEST_KEY_PATTERN.matcher(requestKey).matches()
                || (!requestKey.equals(endpointType.name())
                && !requestKey.startsWith(endpointType.name() + "|"))) {
            throw new IllegalArgumentException(
                    "requestKey must be a canonical key for the logical endpoint");
        }
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be between 100 and 599");
        }
        if (receivedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("receivedAt cannot precede requestedAt");
        }
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency cannot be negative");
        }
        try {
            latency.toMillis();
        }
        catch (ArithmeticException exception) {
            throw new IllegalArgumentException("latency is too large", exception);
        }
        if (!VERSION_PATTERN.matcher(parserVersion).matches()) {
            throw new IllegalArgumentException("parserVersion contains unsafe characters");
        }

        if (errorCode != null) {
            errorCode = requireText(errorCode, "errorCode", MAXIMUM_ERROR_CODE_LENGTH);
            if (!ERROR_CODE_PATTERN.matcher(errorCode).matches()) {
                throw new IllegalArgumentException("errorCode contains unsafe characters");
            }
        }
    }

    public long latencyMillis() {
        return latency.toMillis();
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must contain between 1 and " + maximumLength + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " cannot contain control characters");
        }
        return normalized;
    }
}
