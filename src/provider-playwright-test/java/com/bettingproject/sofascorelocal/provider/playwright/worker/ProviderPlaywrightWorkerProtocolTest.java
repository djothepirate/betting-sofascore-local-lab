package com.bettingproject.sofascorelocal.provider.playwright.worker;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderPlaywrightWorkerProtocolTest {

    @Test
    void readsTheSixExactGetShapesWithoutStartingChromium() throws Exception {
        ProviderPlaywrightWorkerProtocol.GetCommand scheduled = readScheduled(
                "SCHEDULED_EVENTS", "2026-08-27", 25, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand tournament = readTournament(
                "TOURNAMENT_SCHEDULED_EVENTS", "2026-08-27", 17, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand eventDetails = readEvent(
                "EVENT_DETAILS", 16_386_245L, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand eventStatistics = readEvent(
                "EVENT_STATISTICS", 16_386_245L, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand eventIncidents = readEvent(
                "EVENT_INCIDENTS", 16_386_245L, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand eventLineups = readEvent(
                "EVENT_LINEUPS", 16_386_245L, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand maximumEventDetails = readEvent(
                "EVENT_DETAILS", ProviderPlaywrightWorkerProtocol.MAX_EVENT_ID, 10_000);

        assertThat(scheduled).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 25, 0, 0, 10_000));
        assertThat(tournament).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.TOURNAMENT_SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 0, 17, 0, 10_000));
        assertThat(eventDetails).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_DETAILS,
                null, 0, 0, 16_386_245L, 10_000));
        assertThat(eventStatistics).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_STATISTICS,
                null, 0, 0, 16_386_245L, 10_000));
        assertThat(eventIncidents).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_INCIDENTS,
                null, 0, 0, 16_386_245L, 10_000));
        assertThat(eventLineups).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_LINEUPS,
                null, 0, 0, 16_386_245L, 10_000));
        assertThat(maximumEventDetails.eventId())
                .isEqualTo(ProviderPlaywrightWorkerProtocol.MAX_EVENT_ID);
    }

    @Test
    void identifiesTheExplicitV9ConditionalTransportProtocol() {
        assertThat(ProviderPlaywrightWorkerProtocol.VERSION).isEqualTo(9);
        assertThat(ProviderPlaywrightWorkerProtocol.GET_LIVE_V9).isNotEqualTo(
                ProviderPlaywrightWorkerProtocol.GET);
        assertThat(ProviderPlaywrightWorkerProtocol.RESPONSE_V9).isNotEqualTo(
                ProviderPlaywrightWorkerProtocol.RESPONSE);
    }

    @Test
    void readsTheV9ConditionalShapeOnlyForTheFourEventEndpoints() throws Exception {
        ProviderPlaywrightWorkerProtocol.EntityTag validator =
                new ProviderPlaywrightWorkerProtocol.EntityTag("W/\"opaque-v9\"");
        for (ProviderPlaywrightWorkerProtocol.Endpoint endpoint : List.of(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_DETAILS,
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_STATISTICS,
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_INCIDENTS,
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_LINEUPS)) {
            ProviderPlaywrightWorkerProtocol.GetCommand command = readV9Event(
                    endpoint.name(), 16_386_245L, validator.value(), 10_000);
            assertThat(command).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                    endpoint, null, 0, 0, 16_386_245L, 10_000, validator));
            assertThat(command.ifNoneMatch().toString()).doesNotContain("opaque-v9");
        }
        assertThat(readV9Event("EVENT_DETAILS", 16_386_245L, null, 10_000).ifNoneMatch())
                .isNull();
        assertThatThrownBy(() -> readV9Event(
                "EVENT_DETAILS", 16_386_245L, "W/\"bad\rvalue\"", 10_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_VALIDATOR");
        assertThatThrownBy(() -> readV9Event(
                "EVENT_DETAILS",
                16_386_245L,
                "x".repeat(ProviderPlaywrightWorkerProtocol.MAX_ENTITY_TAG_BYTES + 1),
                10_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_VALIDATOR");
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27),
                1,
                0,
                0,
                10_000,
                validator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_VALIDATOR");
    }

    @Test
    void timeoutCompletionFrameContainsOnlyBoundedEndTimeAndReason() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ProviderPlaywrightWorkerProtocol.writeTimeoutEnded(new DataOutputStream(bytes),
                new ProviderPlaywrightWorkerProtocol.TimeoutEndedFrame(1_788_173_200_123L, 2));
        var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertThat(input.readUnsignedByte()).isEqualTo(ProviderPlaywrightWorkerProtocol.TIMEOUT_ENDED);
        assertThat(input.readLong()).isEqualTo(1_788_173_200_123L);
        assertThat(input.readUnsignedByte()).isEqualTo(2);
        assertThat(input.read()).isEqualTo(-1);
        for (int reason : new int[]{-1, 0, 3, 255}) assertThatThrownBy(() ->
                new ProviderPlaywrightWorkerProtocol.TimeoutEndedFrame(1_788_173_200_123L, reason))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.TimeoutEndedFrame(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsClosedForEveryScalarOutsideTheAllowlist() {
        assertThatThrownBy(() -> readUnknownEndpoint("TOURNAMENT_STANDINGS"))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_ENDPOINT");
        assertThatThrownBy(() -> readScheduled(
                "SCHEDULED_EVENTS", "2026-8-27", 1, 1_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_DATE");
        assertThatThrownBy(() -> readScheduled(
                "SCHEDULED_EVENTS", "2026-08-27", 26, 1_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_PAGE");
        assertThatThrownBy(() -> readTournament(
                "TOURNAMENT_SCHEDULED_EVENTS", "2026-08-27", 0, 1_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_TOURNAMENT_ID");
        assertThatThrownBy(() -> readEvent("EVENT_DETAILS", 0, 1_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_EVENT_ID");
        assertThatThrownBy(() -> readEvent(
                "EVENT_LINEUPS", ProviderPlaywrightWorkerProtocol.MAX_EVENT_ID + 1, 1_000))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_EVENT_ID");
        assertThatThrownBy(() -> readScheduled(
                "SCHEDULED_EVENTS", "2026-08-27", 1, 0))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("INVALID_TIMEOUT");
    }

    @Test
    void rejectsCrossEndpointArgumentsBeforeAnyNavigation() {
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 1, 4, 0, 1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_TOURNAMENT_ID");
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.TOURNAMENT_SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 0, 4, 12, 1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_EVENT_ID");
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_DETAILS,
                LocalDate.of(2026, 8, 27), 0, 0, 12, 1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_DATE");
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_STATISTICS,
                null, 1, 0, 12, 1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_PAGE");
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_INCIDENTS,
                null, 0, 42, 12, 1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_TOURNAMENT_ID");
    }

    @Test
    void reconstructsOnlyTheProductionOrExplicitLoopbackUris() {
        Map<String, String> production = baseEnvironment();
        ProviderPlaywrightWorkerConfiguration productionConfiguration =
                ProviderPlaywrightWorkerConfiguration.fromEnvironment(production);
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 2, 0, 0, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/sport/football/"
                        + "scheduled-tournaments/2026-08-27/page/2");
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.TOURNAMENT_SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 0, 42, 0, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/unique-tournament/42/"
                        + "scheduled-events/2026-08-27");
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_DETAILS,
                null, 0, 0, 16_386_245L, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16386245");
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_STATISTICS,
                null, 0, 0, 16_386_245L, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16386245/statistics");
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_INCIDENTS,
                null, 0, 0, 16_386_245L, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16386245/incidents");
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_LINEUPS,
                null, 0, 0, 16_386_245L, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/event/16386245/lineups");

        Map<String, String> loopback = baseEnvironment();
        loopback.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_QUALIFICATION, "true");
        loopback.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_ORIGIN, "http://127.0.0.1:8081");
        ProviderPlaywrightWorkerConfiguration loopbackConfiguration =
                ProviderPlaywrightWorkerConfiguration.fromEnvironment(loopback);
        assertThat(loopbackConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_DETAILS,
                null, 0, 0, 16_421_052L, 5_000)).toASCIIString())
                .isEqualTo("http://127.0.0.1:8081/api/v1/event/16421052");
    }

    @Test
    void refusesAnOriginOverrideWithoutTheExplicitQualificationSwitch() {
        Map<String, String> environment = baseEnvironment();
        environment.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_ORIGIN,
                "http://127.0.0.1:8081");
        assertThatThrownBy(() -> ProviderPlaywrightWorkerConfiguration.fromEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CONFIGURATION");

        environment.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_QUALIFICATION, "true");
        environment.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_ORIGIN,
                "https://www.sofascore.com");
        assertThatThrownBy(() -> ProviderPlaywrightWorkerConfiguration.fromEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CONFIGURATION");
    }

    @Test
    void acceptsOnlyTheClosedLoopbackOriginTcpPortRange() {
        assertLoopbackOriginRejected("http://127.0.0.1");
        assertLoopbackOriginRejected("http://127.0.0.1:0");

        ProviderPlaywrightWorkerConfiguration lowest = loopbackConfiguration(
                "http://127.0.0.1:1");
        assertThat(lowest.uriFor(eventDetailsCommand()).getPort()).isEqualTo(1);

        ProviderPlaywrightWorkerConfiguration highest = loopbackConfiguration(
                "http://127.0.0.1:65535");
        assertThat(highest.uriFor(eventDetailsCommand()).getPort()).isEqualTo(65_535);

        assertLoopbackOriginRejected("http://127.0.0.1:65536");
    }

    @Test
    void writesTheBoundedResponseFrameExactly() throws Exception {
        byte[] body = new byte[]{0, 1, 2, (byte) 0xff};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            ProviderPlaywrightWorkerProtocol.writeResponse(output,
                    new ProviderPlaywrightWorkerProtocol.ResponseFrame(
                            100, 110, 404, "application/json; charset=utf-8", body));
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readUnsignedByte()).isEqualTo(ProviderPlaywrightWorkerProtocol.RESPONSE);
            assertThat(input.readLong()).isEqualTo(100);
            assertThat(input.readLong()).isEqualTo(110);
            assertThat(input.readInt()).isEqualTo(404);
            assertThat(input.readUTF()).isEqualTo("application/json; charset=utf-8");
            int length = input.readInt();
            assertThat(length).isEqualTo(body.length);
            assertThat(input.readNBytes(length)).containsExactly(body);
            assertThat(input.available()).isZero();
        }
    }

    @Test
    void writesTheV9ResponseWithAnOpaqueEntityTagAndAcceptsAnEmpty304Body() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProviderPlaywrightWorkerProtocol.EntityTag tag =
                new ProviderPlaywrightWorkerProtocol.EntityTag("W/\"response-v9\"");
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            ProviderPlaywrightWorkerProtocol.writeLiveV9Response(output,
                    new ProviderPlaywrightWorkerProtocol.ResponseFrame(100, 110, 304, "", new byte[0]),
                    tag);
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readUnsignedByte()).isEqualTo(ProviderPlaywrightWorkerProtocol.RESPONSE_V9);
            assertThat(input.readLong()).isEqualTo(100);
            assertThat(input.readLong()).isEqualTo(110);
            assertThat(input.readInt()).isEqualTo(304);
            assertThat(input.readUTF()).isEmpty();
            assertThat(input.readBoolean()).isTrue();
            assertThat(input.readUTF()).isEqualTo(tag.value());
            assertThat(input.readInt()).isZero();
            assertThat(input.available()).isZero();
        }
        assertThatThrownBy(() -> ProviderPlaywrightWorkerProtocol.writeLiveV9Response(
                new DataOutputStream(new ByteArrayOutputStream()),
                new ProviderPlaywrightWorkerProtocol.ResponseFrame(100, 110, 304, "", new byte[]{1}),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid V9 304 body");
    }

    @Test
    void partialHeadersFrameContainsOnlyBoundedNumbersAndNoResponseBody() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            ProviderPlaywrightWorkerProtocol.writeProgress(output,
                    new ProviderPlaywrightWorkerProtocol.ProgressFrame(2, 30_000, 100, 110, 429, 60_110));
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readUnsignedByte()).isEqualTo(ProviderPlaywrightWorkerProtocol.PROGRESS);
            assertThat(input.readUnsignedByte()).isEqualTo(2);
            assertThat(input.readInt()).isEqualTo(30_000);
            assertThat(input.readLong()).isEqualTo(100);
            assertThat(input.readLong()).isEqualTo(110);
            assertThat(input.readInt()).isEqualTo(429);
            assertThat(input.readLong()).isEqualTo(60_110);
            assertThat(input.available()).isZero();
        }
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.ProgressFrame(1, 30_000, 100, -1, 403, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderPlaywrightWorkerProtocol.ProgressFrame(2, 30_000, 100, 90, 403, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAnExplicitStartFrameAndWritesTheReadyAcknowledgement() throws Exception {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(new byte[]{ProviderPlaywrightWorkerProtocol.START}))) {
            ProviderPlaywrightWorkerProtocol.requireStart(input);
            assertThat(input.available()).isZero();
        }

        ByteArrayOutputStream readyBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(readyBytes)) {
            ProviderPlaywrightWorkerProtocol.writeReady(output);
        }
        assertThat(readyBytes.toByteArray())
                .containsExactly(ProviderPlaywrightWorkerProtocol.READY);
    }

    @Test
    void closedWorkerWaitsForParentEofAndRejectsFurtherCommands() throws Exception {
        ByteArrayOutputStream closedBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(closedBytes)) {
            ProviderPlaywrightWorkerProtocol.writeClosed(output);
        }
        assertThat(closedBytes.toByteArray())
                .containsExactly(ProviderPlaywrightWorkerProtocol.CLOSED);

        try (DataInputStream eof = new DataInputStream(new ByteArrayInputStream(new byte[0]))) {
            ProviderPlaywrightWorkerProtocol.awaitParentTermination(eof);
        }

        assertThatThrownBy(() -> {
            try (DataInputStream command = new DataInputStream(
                    new ByteArrayInputStream(new byte[]{ProviderPlaywrightWorkerProtocol.GET}))) {
                ProviderPlaywrightWorkerProtocol.awaitParentTermination(command);
            }
        }).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void refusesEveryCommandUntilTheExplicitStartFrame() {
        assertThatThrownBy(() -> {
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(new byte[]{ProviderPlaywrightWorkerProtocol.GET}))) {
                ProviderPlaywrightWorkerProtocol.requireStart(input);
            }
        })
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage("PROTOCOL_ERROR");
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand readScheduled(
            String endpoint, String date, int page, int timeoutMillis) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(endpoint);
            output.writeUTF(date);
            output.writeInt(page);
            output.writeInt(timeoutMillis);
        }
        return read(bytes);
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand readTournament(
            String endpoint, String date, long tournamentId, int timeoutMillis) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(endpoint);
            output.writeUTF(date);
            output.writeLong(tournamentId);
            output.writeInt(timeoutMillis);
        }
        return read(bytes);
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand readEvent(
            String endpoint, long eventId, int timeoutMillis) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(endpoint);
            output.writeLong(eventId);
            output.writeInt(timeoutMillis);
        }
        return read(bytes);
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand readV9Event(
            String endpoint,
            long eventId,
            String validator,
            int timeoutMillis) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(endpoint);
            output.writeLong(eventId);
            output.writeBoolean(validator != null);
            if (validator != null) {
                output.writeUTF(validator);
            }
            output.writeInt(timeoutMillis);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ProviderPlaywrightWorkerProtocol.GetCommand command =
                    ProviderPlaywrightWorkerProtocol.readLiveV9GetCommand(input);
            assertThat(input.available()).isZero();
            return command;
        }
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand readUnknownEndpoint(String endpoint)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(endpoint);
        }
        return read(bytes);
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand read(ByteArrayOutputStream bytes)
            throws Exception {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            ProviderPlaywrightWorkerProtocol.GetCommand command =
                    ProviderPlaywrightWorkerProtocol.readGetCommand(input);
            assertThat(input.available()).isZero();
            return command;
        }
    }

    private static Map<String, String> baseEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(ProviderPlaywrightWorkerConfiguration.IPC_PORT, "49152");
        environment.put(ProviderPlaywrightWorkerConfiguration.IPC_TOKEN,
                "0123456789abcdef0123456789abcdef");
        return environment;
    }

    private static ProviderPlaywrightWorkerConfiguration loopbackConfiguration(String origin) {
        Map<String, String> environment = baseEnvironment();
        environment.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_QUALIFICATION, "true");
        environment.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_ORIGIN, origin);
        return ProviderPlaywrightWorkerConfiguration.fromEnvironment(environment);
    }

    private static void assertLoopbackOriginRejected(String origin) {
        assertThatThrownBy(() -> loopbackConfiguration(origin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CONFIGURATION");
    }

    private static ProviderPlaywrightWorkerProtocol.GetCommand eventDetailsCommand() {
        return new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.EVENT_DETAILS,
                null, 0, 0, 16_386_245L, 5_000);
    }
}
