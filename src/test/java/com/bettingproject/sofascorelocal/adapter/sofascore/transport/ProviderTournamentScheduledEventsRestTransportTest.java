package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderTournamentScheduledEventsRestTransportTest {

    private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");

    @Test
    void executesOneExactGetWithoutCredentialsCookiesOrBrowserState() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] body = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);
        var request = request();
        server.expect(requestTo(request.targetUri()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(actual -> {
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.COOKIE)).isNull();
                    assertThat(actual.getHeaders().getFirst(
                            HttpHeaders.PROXY_AUTHORIZATION)).isNull();
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.USER_AGENT)).isNull();
                })
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var transport = new ProviderTournamentScheduledEventsRestTransport(
                builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = transport.execute(request);

        assertThat(response.requestKey()).isEqualTo(
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7");
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.payload().bytes()).isEqualTo(body);
        assertThat(response.retryNotBefore()).isNull();
        server.verify();
    }

    @Test
    void exposesOneRateLimitResponseAndSafeBoundaryWithoutRetrying() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(request().targetUri()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "120")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"rate limited\"}"));
        var transport = new ProviderTournamentScheduledEventsRestTransport(
                builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = transport.execute(request());

        assertThat(response.httpStatus()).isEqualTo(429);
        assertThat(response.retryNotBefore()).isEqualTo(NOW.plusSeconds(120));
        server.verify();
    }

    @Test
    void rejectsOversizedAndSensitiveBodiesWithJ3SafeClassifications() {
        RestClient.Builder oversizedBuilder = RestClient.builder();
        MockRestServiceServer oversizedServer =
                MockRestServiceServer.bindTo(oversizedBuilder).build();
        oversizedServer.expect(requestTo(request().targetUri()))
                .andRespond(withSuccess(
                        new byte[RawPayloadEvidence.MAXIMUM_BYTES + 1],
                        MediaType.APPLICATION_JSON));
        var oversizedTransport = new ProviderTournamentScheduledEventsRestTransport(
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
        String forbiddenValue = "must-never-escape-the-adapter";
        sensitiveServer.expect(requestTo(request().targetUri()))
                .andRespond(withSuccess(
                        ("{\"access_token\":\"" + forbiddenValue + "\"}")
                                .getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON));
        var sensitiveTransport = new ProviderTournamentScheduledEventsRestTransport(
                sensitiveBuilder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> sensitiveTransport.execute(request()))
                .isInstanceOf(ScheduledEventsTransportException.class)
                .hasMessageNotContaining(forbiddenValue)
                .extracting("failure")
                .isEqualTo(ScheduledEventsTransportFailure.SENSITIVE_CONTENT_REJECTED);
        sensitiveServer.verify();
    }

    private static TournamentScheduledEventsProviderRequest request() {
        return new TournamentScheduledEventsProviderRequest(
                URI.create(TournamentScheduledEventsProviderRequest.EXPECTED_ORIGIN),
                LocalDate.parse("2026-08-18"),
                7L);
    }
}
