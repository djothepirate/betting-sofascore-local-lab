package com.bettingproject.sofascorelocal.provider.playwright.worker;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderPlaywrightWorkerProtocolTest {

    @Test
    void readsTheTwoExactGetShapesWithoutStartingChromium() throws Exception {
        ProviderPlaywrightWorkerProtocol.GetCommand scheduled = read(
                "SCHEDULED_EVENTS", "2026-08-27", 25, 0, 10_000);
        ProviderPlaywrightWorkerProtocol.GetCommand tournament = read(
                "TOURNAMENT_SCHEDULED_EVENTS", "2026-08-27", 0, 17, 10_000);

        assertThat(scheduled).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 25, 0, 10_000));
        assertThat(tournament).isEqualTo(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.TOURNAMENT_SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 0, 17, 10_000));
    }

    @Test
    void failsClosedForEveryScalarOutsideTheAllowlist() {
        assertFailure("EVENT_DETAILS", "2026-08-27", 0, 0, 1_000, "INVALID_ENDPOINT");
        assertFailure("SCHEDULED_EVENTS", "2026-8-27", 1, 0, 1_000, "INVALID_DATE");
        assertFailure("SCHEDULED_EVENTS", "2026-08-27", 26, 0, 1_000, "INVALID_PAGE");
        assertFailure("SCHEDULED_EVENTS", "2026-08-27", 1, 4, 1_000, "INVALID_TOURNAMENT_ID");
        assertFailure("TOURNAMENT_SCHEDULED_EVENTS", "2026-08-27", 1, 4, 1_000, "INVALID_PAGE");
        assertFailure("TOURNAMENT_SCHEDULED_EVENTS", "2026-08-27", 0, 0, 1_000,
                "INVALID_TOURNAMENT_ID");
        assertFailure("SCHEDULED_EVENTS", "2026-08-27", 1, 0, 0, "INVALID_TIMEOUT");
    }

    @Test
    void reconstructsOnlyTheProductionOrExplicitLoopbackUris() {
        Map<String, String> production = baseEnvironment();
        ProviderPlaywrightWorkerConfiguration productionConfiguration =
                ProviderPlaywrightWorkerConfiguration.fromEnvironment(production);
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 2, 0, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/sport/football/"
                        + "scheduled-tournaments/2026-08-27/page/2");
        assertThat(productionConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.TOURNAMENT_SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 0, 42, 5_000)).toASCIIString())
                .isEqualTo("https://www.sofascore.com/api/v1/unique-tournament/42/"
                        + "scheduled-events/2026-08-27");

        Map<String, String> loopback = baseEnvironment();
        loopback.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_QUALIFICATION, "true");
        loopback.put(ProviderPlaywrightWorkerConfiguration.LOOPBACK_ORIGIN, "http://127.0.0.1:8081");
        ProviderPlaywrightWorkerConfiguration loopbackConfiguration =
                ProviderPlaywrightWorkerConfiguration.fromEnvironment(loopback);
        assertThat(loopbackConfiguration.uriFor(new ProviderPlaywrightWorkerProtocol.GetCommand(
                ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS,
                LocalDate.of(2026, 8, 27), 1, 0, 5_000)).toASCIIString())
                .startsWith("http://127.0.0.1:8081/api/v1/");
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

    private static ProviderPlaywrightWorkerProtocol.GetCommand read(
            String endpoint, String date, int page, long tournamentId, int timeoutMillis) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(endpoint);
            output.writeUTF(date);
            output.writeInt(page);
            output.writeLong(tournamentId);
            output.writeInt(timeoutMillis);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return ProviderPlaywrightWorkerProtocol.readGetCommand(input);
        }
    }

    private static void assertFailure(
            String endpoint,
            String date,
            int page,
            long tournamentId,
            int timeoutMillis,
            String expectedCode) {
        assertThatThrownBy(() -> read(endpoint, date, page, tournamentId, timeoutMillis))
                .isInstanceOf(ProviderPlaywrightWorkerProtocol.ProtocolValidationException.class)
                .hasMessage(expectedCode);
    }

    private static Map<String, String> baseEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(ProviderPlaywrightWorkerConfiguration.IPC_PORT, "49152");
        environment.put(ProviderPlaywrightWorkerConfiguration.IPC_TOKEN,
                "0123456789abcdef0123456789abcdef");
        return environment;
    }
}
