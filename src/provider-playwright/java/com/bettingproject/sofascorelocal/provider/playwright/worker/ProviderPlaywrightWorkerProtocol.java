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
 * body bytes. Up to four ordered {@code byte PROGRESS} frames may precede a terminal frame,
 * with a bounded numeric stage, configured timeout, observed network/header timestamps,
 * HTTP status and a validated Retry-After deadline. Unknown timestamps use -1 and unknown
 * status uses 0. No raw header crosses IPC. Progress does not complete a response or extend
 * its deadline. Version 7 adds GET_LIVE_V6 with the same GET payload and a maximum 30-second
 * exchange deadline. Only this command may emit TIMEOUT_ENDED: one end timestamp and a bounded
 * FINISHED/ABORTED code, after exact network completion and verified page/context cleanup.
 * This frame carries no body, abandons the exchange, and leaves the existing worker awaiting
 * a new group. Cancellation and cleanup have a separate two-second bound. A closed failure
 * is {@code byte FAILURE}, {@code writeUTF(failureCode)}. A normal
 * close is acknowledged with {@code byte CLOSED}. After that acknowledgement the worker remains
 * alive and quiescent until the parent closes the channel or terminates the worker. Commands are
 * strictly sequential, so a request identifier is deliberately absent. Version 9 adds the
 * explicitly named {@code GET_LIVE_V9} command for the four event endpoints only. Its payload
 * keeps the endpoint fields and adds a bounded, opaque optional {@code If-None-Match} value
 * before the final timeout. Its terminal {@code RESPONSE_V9} frame keeps historical
 * {@code RESPONSE} frames unchanged, and carries an optional bounded response entity tag.
 * A V9 {@code 304} must carry an empty body.</p>
 */
public final class ProviderPlaywrightWorkerProtocol {

    public static final int MAGIC = 0x53335057;
    public static final int VERSION = 10;

    public static final byte GET = 1;
    public static final byte CLOSE = 2;
    public static final byte START = 3;
    public static final byte BEGIN_J3 = 6;
    public static final byte START_WITH_J3_PAUSE = 7;
    public static final byte GET_J3 = 8;
    public static final byte END_J3 = 9;
    public static final byte J3_READY = 17;
    public static final byte J3_CLOSED = 18;
    public static final byte GET_LIVE_V6 = 4;
    public static final byte GET_LIVE_V9 = 5;
    public static final byte RESPONSE = 10;
    public static final byte FAILURE = 11;
    public static final byte CLOSED = 12;
    public static final byte READY = 13;
    public static final byte PROGRESS = 14;
    public static final byte TIMEOUT_ENDED = 15;
    public static final byte RESPONSE_V9 = 16;

    public static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    public static final int MAX_CONTENT_TYPE_BYTES = 160;
    public static final int MAX_TOKEN_BYTES = 512;
    public static final int MIN_TOKEN_BYTES = 32;
    public static final int MAX_ENTITY_TAG_BYTES = 512;
    public static final int MAX_TIMEOUT_MILLIS = 60_000;
    public static final long MAX_EVENT_ID = 999_999_999L;
    /** Kept in sync with the parent J3 manual-collection bound. */
    public static final int MAXIMUM_SCHEDULED_EVENTS_PAGE = 35;

    private ProviderPlaywrightWorkerProtocol() {
    }

    public enum Endpoint {
        SCHEDULED_EVENTS,
        TOURNAMENT_SCHEDULED_EVENTS,
        EVENT_DETAILS,
        EVENT_STATISTICS,
        EVENT_INCIDENTS,
        EVENT_LINEUPS
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
        INVALID_VALIDATOR,
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
            int timeoutMillis,
            EntityTag ifNoneMatch) {

        public GetCommand(
                Endpoint endpoint,
                LocalDate date,
                int page,
                long tournamentId,
                long eventId,
                int timeoutMillis) {
            this(endpoint, date, page, tournamentId, eventId, timeoutMillis, null);
        }

        public GetCommand {
            Objects.requireNonNull(endpoint, "endpoint");
            if (timeoutMillis < 1 || timeoutMillis > MAX_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException(FailureCode.INVALID_TIMEOUT.name());
            }
            switch (endpoint) {
                case SCHEDULED_EVENTS -> {
                    requireDate(date);
                    if (page < 1 || page > MAXIMUM_SCHEDULED_EVENTS_PAGE) {
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
                case EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS -> {
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
            if (ifNoneMatch != null && !isEventEndpoint(endpoint)) {
                throw new IllegalArgumentException(FailureCode.INVALID_VALIDATOR.name());
            }
        }

        private static void requireDate(LocalDate date) {
            if (date == null) {
                throw new IllegalArgumentException(FailureCode.INVALID_DATE.name());
            }
        }
    }

    /** Opaque, bounded header value whose diagnostic representation never reveals the value. */
    public record EntityTag(String value) {

        public EntityTag {
            if (value == null || value.isEmpty() || value.length() > MAX_ENTITY_TAG_BYTES
                    || value.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) {
                throw new IllegalArgumentException(FailureCode.INVALID_VALIDATOR.name());
            }
        }

        @Override
        public String toString() {
            return "EntityTag[redacted]";
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

    /** Stages: 0 navigation, 1 request sent, 2 headers, 3 reading body. Unknown time=-1, status=0. */
    public record ProgressFrame(int stage, int timeoutMillis, long requestedEpochMillis,
            long headersEpochMillis, int status, long retryAfterEpochMillis) {
        public ProgressFrame {
            if (stage < 0 || stage > 3 || timeoutMillis < 1 || timeoutMillis > MAX_TIMEOUT_MILLIS
                    || status != 0 && (status < 100 || status > 599)
                    || stage == 0 && requestedEpochMillis != -1
                    || stage > 0 && requestedEpochMillis < 1
                    || stage < 2 && (headersEpochMillis != -1 || status != 0 || retryAfterEpochMillis != -1)
                    || stage >= 2 && (headersEpochMillis < requestedEpochMillis || status == 0)
                    || retryAfterEpochMillis != -1 && retryAfterEpochMillis < headersEpochMillis)
                throw new IllegalArgumentException("invalid bounded progress");
            for (long value : new long[]{requestedEpochMillis, headersEpochMillis, retryAfterEpochMillis})
                if (value != -1 && (value < 1 || value > 253_402_300_799_999L))
                    throw new IllegalArgumentException("invalid progress timestamp");
        }
    }

    public static void writeProgress(DataOutputStream output, ProgressFrame frame) throws IOException {
        Objects.requireNonNull(output); Objects.requireNonNull(frame);
        output.writeByte(PROGRESS);
        output.writeByte(frame.stage());
        output.writeInt(frame.timeoutMillis());
        output.writeLong(frame.requestedEpochMillis());
        output.writeLong(frame.headersEpochMillis());
        output.writeInt(frame.status());
        output.writeLong(frame.retryAfterEpochMillis());
        output.flush();
    }

    /** Emitted only after correlated network completion and verified cleanup of the exact page. */
    public record TimeoutEndedFrame(long endedEpochMillis, int endReason) {
        public TimeoutEndedFrame {
            if (endedEpochMillis < 1 || endedEpochMillis > 253_402_300_799_999L
                    || endReason < 1 || endReason > 2)
                throw new IllegalArgumentException("invalid bounded exchange end");
        }
    }

    public static void writeTimeoutEnded(DataOutputStream output, TimeoutEndedFrame frame) throws IOException {
        output.writeByte(TIMEOUT_ENDED);
        output.writeLong(frame.endedEpochMillis());
        output.writeByte(frame.endReason()); // 1=FINISHED, 2=ABORTED; context reuse is certified by this frame.
        output.flush();
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
            case EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS -> new GetCommand(
                    endpoint, null, 0, 0, input.readLong(), input.readInt());
            };
        }
        catch (IllegalArgumentException exception) {
            throw new ProtocolValidationException(readFailureCode(exception), exception);
        }
    }

    /** Reads the V9-only event request shape without changing the historical GET wire shape. */
    public static GetCommand readLiveV9GetCommand(DataInputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        Endpoint endpoint = readEndpoint(input.readUTF());
        if (!isEventEndpoint(endpoint)) {
            throw new ProtocolValidationException(FailureCode.INVALID_ENDPOINT);
        }
        try {
            long eventId = input.readLong();
            int validatorPresent = input.readUnsignedByte();
            EntityTag ifNoneMatch;
            if (validatorPresent == 0) {
                ifNoneMatch = null;
            }
            else if (validatorPresent == 1) {
                ifNoneMatch = new EntityTag(input.readUTF());
            }
            else {
                throw new IllegalArgumentException(FailureCode.INVALID_VALIDATOR.name());
            }
            return new GetCommand(endpoint, null, 0, 0, eventId, input.readInt(), ifNoneMatch);
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

    public static boolean requireStart(DataInputStream input)
            throws IOException, ProtocolValidationException {
        Objects.requireNonNull(input, "input");
        int command=input.readUnsignedByte();
        if (command != START && command != START_WITH_J3_PAUSE) {
            throw new ProtocolValidationException(FailureCode.PROTOCOL_ERROR);
        }
        return command==START_WITH_J3_PAUSE;
    }

    public record J3Scope(java.util.UUID runId,LocalDate date,java.time.Instant deadline) { }

    public static J3Scope readJ3Scope(DataInputStream input,java.time.Instant now) throws IOException,ProtocolValidationException {
        try {
            java.util.UUID id=readScopeId(input);
            String text=input.readUTF();LocalDate date=LocalDate.parse(text);
            if(text.length()!=10 || !date.toString().equals(text))throw new IllegalArgumentException();
            java.time.Instant deadline=java.time.Instant.ofEpochMilli(input.readLong());
            if(!deadline.isAfter(now) || deadline.isAfter(now.plusSeconds(1200)))throw new IllegalArgumentException();
            return new J3Scope(id,date,deadline);
        } catch(IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new ProtocolValidationException(FailureCode.PROTOCOL_ERROR);
        }
    }
    public static java.util.UUID readScopeId(DataInputStream input) throws IOException,ProtocolValidationException {
        try {
            String value=input.readUTF();java.util.UUID id=java.util.UUID.fromString(value);
            if(!id.toString().equals(value))throw new IllegalArgumentException();return id;
        } catch(IllegalArgumentException invalid) {throw new ProtocolValidationException(FailureCode.PROTOCOL_ERROR);}
    }
    public static void writeScopeAcknowledgement(DataOutputStream output,byte frame,java.util.UUID id) throws IOException {
        if(frame!=J3_READY && frame!=J3_CLOSED)throw new IllegalArgumentException("Invalid scope acknowledgement");
        output.writeByte(frame);output.writeUTF(id.toString());output.flush();
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

    /** Writes the V9-only terminal response without changing the historical RESPONSE frame. */
    public static void writeLiveV9Response(
            DataOutputStream output,
            ResponseFrame response,
            EntityTag entityTag) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(response, "response");
        byte[] body = response.body();
        try {
            if (response.status() == 304 && body.length != 0) {
                throw new IllegalArgumentException("invalid V9 304 body");
            }
            output.writeByte(RESPONSE_V9);
            output.writeLong(response.requestedEpochMillis());
            output.writeLong(response.receivedEpochMillis());
            output.writeInt(response.status());
            output.writeUTF(response.contentType());
            output.writeBoolean(entityTag != null);
            if (entityTag != null) {
                output.writeUTF(entityTag.value());
            }
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

    private static boolean isEventEndpoint(Endpoint endpoint) {
        return endpoint == Endpoint.EVENT_DETAILS
                || endpoint == Endpoint.EVENT_STATISTICS
                || endpoint == Endpoint.EVENT_INCIDENTS
                || endpoint == Endpoint.EVENT_LINEUPS;
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
