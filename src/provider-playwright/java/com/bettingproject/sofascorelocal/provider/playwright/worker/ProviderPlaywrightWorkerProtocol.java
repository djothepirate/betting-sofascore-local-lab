package com.bettingproject.sofascorelocal.provider.playwright.worker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Binary, sequential protocol used by the isolated Playwright worker.
 *
 * <p>The worker opens the loopback connection and first writes {@code int MAGIC},
 * {@code int VERSION}, then {@code writeUTF(token)}. The parent must then write {@code byte START}
 * before the worker is allowed to create Playwright or Chromium. The worker acknowledges a
 * successful runtime creation with {@code byte READY}. The parent subsequently writes one command
 * at a time. A GET command starts with {@code byte GET} and {@code writeUTF(endpoint)}.
 * The endpoint-specific arguments are an ISO date and page for scheduled events, an ISO date and
 * tournament identifier for tournament events, or one event identifier for event details. The
 * final field is always {@code int timeoutMillis}. A CLOSE command contains only
 * {@code byte CLOSE}. No URI crosses the IPC boundary.</p>
 *
 * <p>A successful request response is {@code byte RESPONSE}, two epoch-millisecond timestamps,
 * {@code int HTTP status}, {@code writeUTF(content-type)}, {@code int bodyLength}, and the exact
 * body bytes. A closed failure is {@code byte FAILURE}, {@code writeUTF(failureCode)}. A normal
 * close is acknowledged with {@code byte CLOSED}. After that acknowledgement the worker remains
 * alive and quiescent until the parent closes the channel or terminates the worker. Commands are
 * strictly sequential, so a request identifier is deliberately absent.</p>
 */
public final class ProviderPlaywrightWorkerProtocol {

    public static final int MAGIC = 0x53335057;
    public static final int VERSION = 4;

    public static final byte GET = 1;
    public static final byte CLOSE = 2;
    public static final byte START = 3;
    public static final byte RESPONSE = 10;
    public static final byte FAILURE = 11;
    public static final byte CLOSED = 12;
    public static final byte READY = 13;

    public static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    public static final int MAX_CONTENT_TYPE_BYTES = 160;
    public static final int MAX_TOKEN_BYTES = 512;
    public static final int MIN_TOKEN_BYTES = 32;
    public static final int MAX_TIMEOUT_MILLIS = 60_000;
    public static final long MAX_EVENT_ID = 999_999_999L;

    private ProviderPlaywrightWorkerProtocol() {
    }

    public enum Endpoint {
        SCHEDULED_EVENTS,
        TOURNAMENT_SCHEDULED_EVENTS,
        EVENT_DETAILS
    }

    public enum FailureCode {
        INVALID_CONFIGURATION,
        RUNTIME_START_FAILED,
        IPC_CONNECT_FAILED,
        PROTOCOL_ERROR,
        INVALID_ENDPOINT,
        INVALID_DATE,
        INVALID_PAGE,
        INVALID_TOURNAMENT_ID,
        INVALID_EVENT_ID,
        INVALID_TIMEOUT,
        SENSITIVE_REQUEST_BLOCKED,
        UNEXPECTED_ROUTE,
        REDIRECT_BLOCKED,
        TIMEOUT,
        PAYLOAD_TOO_LARGE,
        CONTENT_TYPE_TOO_LONG,
        RESPONSE_READ_FAILED,
        PLAYWRIGHT_FAILURE
    }

    public record GetCommand(
            Endpoint endpoint,
            LocalDate date,
            int page,
            long tournamentId,
            long eventId,
            int timeoutMillis) {

        public GetCommand {
            Objects.requireNonNull(endpoint, "endpoint");
            if (timeoutMillis < 1 || timeoutMillis > MAX_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException(FailureCode.INVALID_TIMEOUT.name());
            }
            switch (endpoint) {
                case SCHEDULED_EVENTS -> {
                    requireDate(date);
                    if (page < 1 || page > 25) {
                        throw new IllegalArgumentException(FailureCode.INVALID_PAGE.name());
                    }
                    if (tournamentId != 0) {
                        throw new IllegalArgumentException(FailureCode.INVALID_TOURNAMENT_ID.name());
                    }
                    if (eventId != 0) {
                        throw new IllegalArgumentException(FailureCode.INVALID_EVENT_ID.name());
                    }
                }
                case TOURNAMENT_SCHEDULED_EVENTS -> {
                    requireDate(date);
                    if (page != 0) {
                        throw new IllegalArgumentException(FailureCode.INVALID_PAGE.name());
                    }
                    if (tournamentId < 1) {
                        throw new IllegalArgumentException(FailureCode.INVALID_TOURNAMENT_ID.name());
                    }
                    if (eventId != 0) {
                        throw new IllegalArgumentException(FailureCode.INVALID_EVENT_ID.name());
                    }
                }
                case EVENT_DETAILS -> {
                    if (date != null) {
                        throw new IllegalArgumentException(FailureCode.INVALID_DATE.name());
                    }
                    if (page != 0) {
                        throw new IllegalArgumentException(FailureCode.INVALID_PAGE.name());
                    }
                    if (tournamentId != 0) {
                        throw new IllegalArgumentException(FailureCode.INVALID_TOURNAMENT_ID.name());
                    }
                    if (eventId < 1 || eventId > MAX_EVENT_ID) {
                        throw new IllegalArgumentException(FailureCode.INVALID_EVENT_ID.name());
                    }
                }
            }
        }

        private static void requireDate(LocalDate date) {
            if (date == null) {
                throw new IllegalArgumentException(FailureCode.INVALID_DATE.name());
            }
        }
    }

    public record ResponseFrame(
            long requestedEpochMillis,
            long receivedEpochMillis,
            int status,
            String contentType,
            byte[] body) {

        public ResponseFrame {
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(body, "body");
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("status");
            }
            if (receivedEpochMillis < requestedEpochMillis) {
                throw new IllegalArgumentException("timestamps");
            }
            validateContentType(contentType);
            if (body.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException(FailureCode.PAYLOAD_TOO_LARGE.name());
            }
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        void clearBody() {
            java.util.Arrays.fill(body, (byte) 0);
        }
    }

    public static GetCommand readGetCommand(DataInputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        Endpoint endpoint = readEndpoint(input.readUTF());
        try {
            return switch (endpoint) {
                case SCHEDULED_EVENTS -> new GetCommand(
                        endpoint, readDate(input.readUTF()), input.readInt(), 0, 0,
                        input.readInt());
                case TOURNAMENT_SCHEDULED_EVENTS -> new GetCommand(
                        endpoint, readDate(input.readUTF()), 0, input.readLong(), 0,
                        input.readInt());
                case EVENT_DETAILS -> new GetCommand(
                        endpoint, null, 0, 0, input.readLong(), input.readInt());
            };
        }
        catch (IllegalArgumentException exception) {
            throw new ProtocolValidationException(readFailureCode(exception), exception);
        }
    }

    public static void writeHandshake(DataOutputStream output, String token) throws IOException {
        Objects.requireNonNull(output, "output");
        validateToken(token);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeUTF(token);
        output.flush();
    }

    public static void requireStart(DataInputStream input)
            throws IOException, ProtocolValidationException {
        Objects.requireNonNull(input, "input");
        if (input.readUnsignedByte() != START) {
            throw new ProtocolValidationException(FailureCode.PROTOCOL_ERROR);
        }
    }

    public static void writeReady(DataOutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        output.writeByte(READY);
        output.flush();
    }

    public static void writeResponse(DataOutputStream output, ResponseFrame response) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(response, "response");
        byte[] body = response.body();
        try {
            output.writeByte(RESPONSE);
            output.writeLong(response.requestedEpochMillis());
            output.writeLong(response.receivedEpochMillis());
            output.writeInt(response.status());
            output.writeUTF(response.contentType());
            output.writeInt(body.length);
            output.write(body);
            output.flush();
        }
        finally {
            java.util.Arrays.fill(body, (byte) 0);
            response.clearBody();
        }
    }

    public static void writeFailure(DataOutputStream output, FailureCode code) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(code, "code");
        output.writeByte(FAILURE);
        output.writeUTF(code.name());
        output.flush();
    }

    public static void writeClosed(DataOutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        output.writeByte(CLOSED);
        output.flush();
    }

    public static void awaitParentTermination(DataInputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        if (input.read() != -1) {
            throw new IOException("unexpected data after CLOSED");
        }
    }

    public static void validateToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException(FailureCode.INVALID_CONFIGURATION.name());
        }
        int byteCount = token.getBytes(StandardCharsets.UTF_8).length;
        if (byteCount < MIN_TOKEN_BYTES || byteCount > MAX_TOKEN_BYTES
                || token.codePoints().anyMatch(codePoint -> codePoint < 0x21 || codePoint > 0x7e)) {
            throw new IllegalArgumentException(FailureCode.INVALID_CONFIGURATION.name());
        }
    }

    public static void validateContentType(String contentType) {
        int byteCount = contentType.getBytes(StandardCharsets.UTF_8).length;
        if (byteCount > MAX_CONTENT_TYPE_BYTES
                || contentType.indexOf('\r') >= 0
                || contentType.indexOf('\n') >= 0
                || contentType.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(FailureCode.CONTENT_TYPE_TOO_LONG.name());
        }
    }

    private static Endpoint readEndpoint(String value) throws ProtocolValidationException {
        if (value == null || value.length() > 40) {
            throw new ProtocolValidationException(FailureCode.INVALID_ENDPOINT);
        }
        try {
            return Endpoint.valueOf(value);
        }
        catch (IllegalArgumentException exception) {
            throw new ProtocolValidationException(FailureCode.INVALID_ENDPOINT, exception);
        }
    }

    private static LocalDate readDate(String value) throws ProtocolValidationException {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new ProtocolValidationException(FailureCode.INVALID_DATE);
        }
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (!parsed.toString().equals(value)) {
                throw new ProtocolValidationException(FailureCode.INVALID_DATE);
            }
            return parsed;
        }
        catch (DateTimeParseException exception) {
            throw new ProtocolValidationException(FailureCode.INVALID_DATE, exception);
        }
    }

    private static FailureCode readFailureCode(IllegalArgumentException exception) {
        try {
            return FailureCode.valueOf(exception.getMessage());
        }
        catch (RuntimeException ignored) {
            return FailureCode.PROTOCOL_ERROR;
        }
    }

    public static final class ProtocolValidationException extends IOException {

        private final FailureCode failureCode;

        ProtocolValidationException(FailureCode failureCode) {
            super(failureCode.name());
            this.failureCode = failureCode;
        }

        ProtocolValidationException(FailureCode failureCode, Throwable cause) {
            super(failureCode.name(), cause);
            this.failureCode = failureCode;
        }

        public FailureCode failureCode() {
            return failureCode;
        }
    }
}
