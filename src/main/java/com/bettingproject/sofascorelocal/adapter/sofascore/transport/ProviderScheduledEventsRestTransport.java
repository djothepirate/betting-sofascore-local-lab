package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;
import org.springframework.http.HttpHeaders;
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
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Exact-host J3 qualification transport. It sends no cookie, authorization, account or session data.
 * Redirects and proxies are disabled and only a validated page request can reach this adapter.
 */
@Component
public class ProviderScheduledEventsRestTransport
        implements ScheduledEventsProviderPageTransport {

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";
    private static final int MAXIMUM_RETRY_AFTER_LENGTH = 128;
    private static final Pattern DELTA_SECONDS = Pattern.compile("[0-9]{1,10}");

    private final RestClient restClient;
    private final Clock clock;

    public ProviderScheduledEventsRestTransport(SofascoreProperties properties) {
        this(createRestClient(
                properties.getConnectTimeout(),
                properties.getReadTimeout()), Clock.systemUTC());
    }

    ProviderScheduledEventsRestTransport(RestClient restClient, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ScheduledEventsTransportResponse execute(
            ScheduledEventsProviderPageRequest request) {
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
                        int status = httpResponse.getStatusCode().value();
                        return new ScheduledEventsTransportResponse(
                                request.requestKey(),
                                requestedAt,
                                receivedAt,
                                status,
                                contentType,
                                Duration.between(requestedAt, receivedAt),
                                evidence,
                                retryNotBefore(status, httpResponse.getHeaders(), receivedAt));
                    });
        }
        catch (ScheduledEventsTransportException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw new ScheduledEventsTransportException(classifyFailure(exception));
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
                throw new ScheduledEventsTransportException(
                        ScheduledEventsTransportFailure.PAYLOAD_TOO_LARGE);
            }
            return payload;
        }
        catch (IOException exception) {
            throw new ScheduledEventsTransportException(classifyFailure(exception));
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

    private static ScheduledEventsTransportFailure classifyFailure(Throwable throwable) {
        Throwable current = Objects.requireNonNull(throwable, "throwable");
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return ScheduledEventsTransportFailure.TIMEOUT;
            }
            current = current.getCause();
        }
        return ScheduledEventsTransportFailure.IO_FAILURE;
    }

    private static Instant retryNotBefore(
            int httpStatus,
            HttpHeaders headers,
            Instant receivedAt) {
        if (httpStatus != 429) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank() || value.length() > MAXIMUM_RETRY_AFTER_LENGTH) {
            return null;
        }
        String normalized = value.trim();
        try {
            Instant boundary = DELTA_SECONDS.matcher(normalized).matches()
                    ? receivedAt.plusSeconds(Long.parseLong(normalized))
                    : ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant();
            return boundary.isAfter(receivedAt) ? boundary : null;
        }
        catch (ArithmeticException | DateTimeException exception) {
            return null;
        }
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
