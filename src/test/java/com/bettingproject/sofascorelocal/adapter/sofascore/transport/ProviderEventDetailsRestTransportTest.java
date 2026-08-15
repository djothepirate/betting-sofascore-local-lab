package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderEventDetailsRestTransportTest {

    @Test
    void sendsOnlyTheValidatedEventWithoutCredentialsOrSessionHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] body = "{\"event\":{\"id\":16386245}}"
                .getBytes(StandardCharsets.UTF_8);
        var request = new EventDetailsProviderRequest(
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                16386245L);
        server.expect(requestTo(request.targetUri()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(actual -> {
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.COOKIE)).isNull();
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.PROXY_AUTHORIZATION)).isNull();
                })
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var transport = new ProviderEventDetailsRestTransport(
                builder.build(),
                Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC));

        var response = transport.execute(request);

        assertThat(response.requestKey()).isEqualTo("EVENT_DETAILS|eventId=16386245");
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.payload().bytes()).isEqualTo(body);
        server.verify();
    }
}
