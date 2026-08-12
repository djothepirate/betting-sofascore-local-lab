package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.ScheduledEventsTransport;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * HTTP transport restricted to the exact IPv4 loopback origin and the simulated J3 route.
 * It is deliberately not a Spring bean and cannot reach a provider endpoint.
 */
public final class LoopbackScheduledEventsRestTransport implements ScheduledEventsTransport {

    public static final Duration MAXIMUM_TIMEOUT = Duration.ofSeconds(10);

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final RestClient restClient;
    private final Clock clock;

    private LoopbackScheduledEventsRestTransport(RestClient restClient, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static LoopbackScheduledEventsRestTransport create(
            Duration connectTimeout,
            Duration readTimeout,
            Clock clock) {
        requireBoundedTimeout(connectTimeout, "connectTimeout");
        requireBoundedTimeout(readTimeout, "readTimeout");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new LoopbackScheduledEventsRestTransport(restClient, clock);
    }

    static LoopbackScheduledEventsRestTransport forSimulatedServer(
            RestClient restClient,
            Clock clock) {
        return new LoopbackScheduledEventsRestTransport(restClient, clock);
    }

    @Override
    public ScheduledEventsTransportResponse execute(ScheduledEventsTransportRequest request) {
        Objects.requireNonNull(request, "request");
        Instant requestedAt = clock.instant();
        try {
            return restClient.get()
                    .uri(request.targetUri())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((httpRequest, httpResponse) -> {
                        Instant receivedAt = clock.instant();
                        byte[] rawPayload = readBounded(httpResponse.getBody());
                        RawPayloadEvidence evidence = captureSafely(rawPayload);
                        MediaType responseType = httpResponse.getHeaders().getContentType();
                        String contentType = responseType == null
                                ? FALLBACK_CONTENT_TYPE
                                : responseType.toString();
                        return new ScheduledEventsTransportResponse(
                                request.requestKey(),
                                requestedAt,
                                receivedAt,
                                httpResponse.getStatusCode().value(),
                                contentType,
                                Duration.between(requestedAt, receivedAt),
                                evidence);
                    });
        }
        catch (ScheduledEventsTransportException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.IO_FAILURE);
        }
    }

    private static byte[] readBounded(java.io.InputStream input) {
        try {
            byte[] payload = input.readNBytes(RawPayloadEvidence.MAXIMUM_BYTES + 1);
            if (payload.length > RawPayloadEvidence.MAXIMUM_BYTES) {
                throw new ScheduledEventsTransportException(
                        ScheduledEventsTransportFailure.PAYLOAD_TOO_LARGE);
            }
            return payload;
        }
        catch (IOException exception) {
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.IO_FAILURE);
        }
    }

    private static RawPayloadEvidence captureSafely(byte[] rawPayload) {
        try {
            return RawPayloadEvidence.capture(rawPayload);
        }
        catch (IllegalArgumentException exception) {
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.SENSITIVE_CONTENT_REJECTED);
        }
    }

    private static void requireBoundedTimeout(Duration timeout, String name) {
        Objects.requireNonNull(timeout, name);
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAXIMUM_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    name + " must be positive and no greater than ten seconds");
        }
    }
}
