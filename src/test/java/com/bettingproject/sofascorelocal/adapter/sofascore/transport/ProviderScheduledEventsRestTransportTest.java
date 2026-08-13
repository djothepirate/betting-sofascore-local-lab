package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
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
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderScheduledEventsRestTransportTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    @Test
    void executesOnlyTheValidatedPageWithoutCredentialsOrSessionHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] body = "{\"events\":[],\"hasNextPage\":false}"
                .getBytes(StandardCharsets.UTF_8);
        ScheduledEventsProviderPageRequest request = request(3);
        server.expect(requestTo(request.targetUri()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(actual -> {
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.COOKIE)).isNull();
                    assertThat(actual.getHeaders().getFirst(HttpHeaders.PROXY_AUTHORIZATION)).isNull();
                })
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var transport = new ProviderScheduledEventsRestTransport(
                builder.build(), Clock.fixed(NOW, ZoneOffset.UTC));

        var response = transport.execute(request);

        assertThat(response.requestKey())
                .isEqualTo("SCHEDULED_EVENTS|date=2026-08-13|page=3");
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.payload().bytes()).isEqualTo(body);
        server.verify();
    }

    private static ScheduledEventsProviderPageRequest request(int page) {
        return new ScheduledEventsProviderPageRequest(
                URI.create("https://www.sofascore.com"),
                LocalDate.parse("2026-08-13"),
                page);
    }
}
