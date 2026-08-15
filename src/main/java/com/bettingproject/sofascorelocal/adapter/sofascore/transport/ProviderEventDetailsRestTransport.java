package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Exact-host J4 EVENT_DETAILS transport. Redirects and proxies are disabled and
 * the request type can only be created through an explicit J4 qualification scope.
 */
@Component
public class ProviderEventDetailsRestTransport implements EventDetailsProviderTransport {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public ProviderEventDetailsRestTransport(SofascoreProperties properties) {
        this(createRestClient(
                properties.getConnectTimeout(),
                properties.getReadTimeout()), Clock.systemUTC());
    }

    ProviderEventDetailsRestTransport(RestClient restClient, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EventDetailsTransportResponse execute(EventDetailsProviderRequest request) {
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
                        return new EventDetailsTransportResponse(
                                request.requestKey(),
                                requestedAt,
                                receivedAt,
                                httpResponse.getStatusCode().value(),
                                contentType,
                                Duration.between(requestedAt, receivedAt),
                                evidence);
                    });
        }
        catch (EventDetailsTransportException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new EventDetailsTransportException(classifyFailure(exception));
        }
    }

    private static RestClient createRestClient(
            Duration connectTimeout,
            Duration readTimeout) {
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
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private static byte[] readBounded(java.io.InputStream input) {
        try {
            byte[] payload = input.readNBytes(RawPayloadEvidence.MAXIMUM_BYTES + 1);
            if (payload.length > RawPayloadEvidence.MAXIMUM_BYTES) {
                throw new EventDetailsTransportException(
                        EventDetailsTransportFailure.PAYLOAD_TOO_LARGE);
            }
            return payload;
        }
        catch (IOException exception) {
            throw new EventDetailsTransportException(classifyFailure(exception));
        }
    }

    private static RawPayloadEvidence captureSafely(byte[] rawPayload) {
        try {
            return RawPayloadEvidence.capture(rawPayload);
        }
        catch (IllegalArgumentException exception) {
            throw new EventDetailsTransportException(
                    EventDetailsTransportFailure.SENSITIVE_CONTENT_REJECTED);
        }
    }

    private static EventDetailsTransportFailure classifyFailure(Throwable throwable) {
        Throwable current = Objects.requireNonNull(throwable, "throwable");
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return EventDetailsTransportFailure.TIMEOUT;
            }
            current = current.getCause();
        }
        return EventDetailsTransportFailure.IO_FAILURE;
    }

    private static void requireBoundedTimeout(Duration timeout, String name) {
        Objects.requireNonNull(timeout, name);
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException(
                    name + " must be positive and no greater than ten seconds");
        }
    }
}
