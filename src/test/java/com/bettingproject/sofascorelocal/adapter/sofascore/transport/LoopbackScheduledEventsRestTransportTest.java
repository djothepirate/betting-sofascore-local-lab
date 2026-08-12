package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
    void keepsValidRetryAfterBoundariesAndIgnoresUnsafeValues() {
        assertRetryAfter("120", NOW.plusSeconds(120));
        assertRetryAfter("Wed, 12 Aug 2026 12:03:00 GMT", NOW.plusSeconds(180));
        assertRetryAfter("0", null);
        assertRetryAfter("not-a-date", null);
        assertRetryAfter("9".repeat(129), null);
    }

    @Test
    void classifiesTimeoutsFromTransportOrBodyReadingWithoutLeakingDetails() {
        String sensitiveDiagnostic = "do-not-log-this-timeout-diagnostic";
        assertThat(LoopbackScheduledEventsRestTransport.classifyFailure(
                new HttpTimeoutException(sensitiveDiagnostic)))
                .isEqualTo(ScheduledEventsTransportFailure.TIMEOUT);
        assertThat(LoopbackScheduledEventsRestTransport.classifyFailure(
                new java.io.IOException(
                        "bounded read failed",
                        new SocketTimeoutException(sensitiveDiagnostic))))
                .isEqualTo(ScheduledEventsTransportFailure.TIMEOUT);
        assertThat(new ScheduledEventsTransportException(
                ScheduledEventsTransportFailure.TIMEOUT))
                .hasMessageNotContaining(sensitiveDiagnostic);
        assertThat(LoopbackScheduledEventsRestTransport.classifyFailure(
                new java.io.IOException("ordinary simulated I/O failure")))
                .isEqualTo(ScheduledEventsTransportFailure.IO_FAILURE);
    }

    @Test
    void mapsASimulatedTransportTimeoutToASafeFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String sensitiveDiagnostic = "simulated-timeout-detail-not-for-output";
        server.expect(requestTo(request().targetUri()))
                .andRespond(httpRequest -> {
                    throw new SocketTimeoutException(sensitiveDiagnostic);
                });
        var transport = LoopbackScheduledEventsRestTransport.forSimulatedServer(
                builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> transport.execute(request()))
                .isInstanceOf(ScheduledEventsTransportException.class)
                .hasMessageNotContaining(sensitiveDiagnostic)
                .extracting("failure")
                .isEqualTo(ScheduledEventsTransportFailure.TIMEOUT);
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

    private static void assertRetryAfter(String headerValue, Instant expected) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(request().targetUri()))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, headerValue)
                        .body("{\"error\":\"simulated\"}"));
        var transport = LoopbackScheduledEventsRestTransport.forSimulatedServer(
                builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(transport.execute(request()).retryNotBefore()).isEqualTo(expected);
        server.verify();
    }
}
