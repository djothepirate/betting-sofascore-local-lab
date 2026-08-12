package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class LoopbackScheduledEventsRestTransportTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void executesOneExactGetAgainstTheSimulatedServerAndKeepsRawBytes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] rawPayload = "{\"events\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(
                        "http://127.0.0.1:18087/simulated/scheduled-events?date=2026-08-12"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(rawPayload, MediaType.APPLICATION_JSON));
        var transport = LoopbackScheduledEventsRestTransport.forSimulatedServer(
                builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = transport.execute(request());

        assertThat(response.requestKey()).isEqualTo("SCHEDULED_EVENTS|date=2026-08-12");
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.requestedAt()).isEqualTo(NOW);
        assertThat(response.receivedAt()).isEqualTo(NOW);
        assertThat(response.latency()).isZero();
        assertThat(response.payload().bytes()).isEqualTo(rawPayload);
        server.verify();
    }

    @Test
    void rejectsOversizedAndSensitiveResponsesWithoutExposingTheirValues() {
        RestClient.Builder oversizedBuilder = RestClient.builder();
        MockRestServiceServer oversizedServer =
                MockRestServiceServer.bindTo(oversizedBuilder).build();
        oversizedServer.expect(requestTo(request().targetUri()))
                .andRespond(withSuccess(
                        new byte[RawPayloadEvidence.MAXIMUM_BYTES + 1],
                        MediaType.APPLICATION_JSON));
        var oversizedTransport = LoopbackScheduledEventsRestTransport.forSimulatedServer(
                oversizedBuilder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> oversizedTransport.execute(request()))
                .isInstanceOf(ScheduledEventsTransportException.class)
                .extracting("failure")
                .isEqualTo(ScheduledEventsTransportFailure.PAYLOAD_TOO_LARGE);
        oversizedServer.verify();

        RestClient.Builder sensitiveBuilder = RestClient.builder();
        MockRestServiceServer sensitiveServer =
                MockRestServiceServer.bindTo(sensitiveBuilder).build();
        String forbiddenValue = "do-not-store-this-value";
        sensitiveServer.expect(requestTo(request().targetUri()))
                .andRespond(withSuccess(
                        ("{\"access_token\":\"" + forbiddenValue + "\"}")
                                .getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON));
        var sensitiveTransport = LoopbackScheduledEventsRestTransport.forSimulatedServer(
                sensitiveBuilder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> sensitiveTransport.execute(request()))
                .isInstanceOf(ScheduledEventsTransportException.class)
                .hasMessageNotContaining(forbiddenValue)
                .extracting("failure")
                .isEqualTo(ScheduledEventsTransportFailure.SENSITIVE_CONTENT_REJECTED);
        sensitiveServer.verify();
    }

    @Test
    void returnsAnErrorStatusAndBodyOnceWithoutAutomaticRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] rawPayload = "{\"error\":\"rate limited\"}"
                .getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(request().targetUri()))
                .andRespond(withTooManyRequests().body(rawPayload));
        var transport = LoopbackScheduledEventsRestTransport.forSimulatedServer(
                builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = transport.execute(request());

        assertThat(response.httpStatus()).isEqualTo(429);
        assertThat(response.payload().bytes()).isEqualTo(rawPayload);
        server.verify();
    }

    @Test
    void requiresPositiveTimeoutsNoGreaterThanTenSeconds() {
        assertThatThrownBy(() -> LoopbackScheduledEventsRestTransport.create(
                Duration.ZERO,
                Duration.ofSeconds(1),
                Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");

        assertThatThrownBy(() -> LoopbackScheduledEventsRestTransport.create(
                Duration.ofSeconds(1),
                Duration.ofSeconds(11),
                Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readTimeout");
    }

    private static ScheduledEventsTransportRequest request() {
        return new ScheduledEventsTransportRequest(
                URI.create("http://127.0.0.1:18087"),
                LocalDate.parse("2026-08-12"));
    }
}
