package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryContract;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFailure;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BettingProjectJ7DeliveryHttpTransportTest {

    private static final URI ORIGIN = URI.create("https://127.0.0.1:49152");
    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final String FILE_SHA256 =
            "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a";
    private static final String DATA_SHA256 = "b".repeat(64);

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void performsExactlyOneBoundedPostWithTheContractIdentityHeaders() {
        HttpClient client = mock(HttpClient.class);
        stubResponse(
                client,
                201,
                J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE,
                null,
                "{\"status\":\"IMPORTED\"}".getBytes(StandardCharsets.UTF_8));
        var transport = transport(client);

        var result = transport.execute(request());

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.contentType())
                .isEqualTo(J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE);
        assertThat(result.receivedAt()).isEqualTo(NOW);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(1)).sendAsync(captor.capture(), anyByteArrayHandler());
        HttpRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.uri()).isEqualTo(ORIGIN.resolve(
                BettingProjectJ7DeliveryHttpTransport.IMPORT_PATH));
        assertThat(sent.timeout()).contains(Duration.ofSeconds(10));
        assertThat(sent.headers().firstValue("Content-Type"))
                .contains(J7DeliveryContract.REQUEST_MEDIA_TYPE);
        assertThat(sent.headers().firstValue("Accept"))
                .contains(J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE);
        assertThat(sent.headers().firstValue("Idempotency-Key"))
                .contains("j7:" + EXPORT_ID + ":sha256:" + FILE_SHA256);
        assertThat(sent.headers().firstValue(
                BettingProjectJ7DeliveryHttpTransport.PROTOCOL_HEADER)).contains("1.0");
        assertThat(sent.headers().firstValue(
                BettingProjectJ7DeliveryHttpTransport.EXPORT_ID_HEADER))
                .contains(EXPORT_ID.toString());
        assertThat(sent.headers().firstValue(
                BettingProjectJ7DeliveryHttpTransport.FILE_SHA256_HEADER))
                .contains(FILE_SHA256);
        assertThat(sent.headers().firstValue(
                BettingProjectJ7DeliveryHttpTransport.DATA_SHA256_HEADER))
                .contains(DATA_SHA256);
        HttpRequest.BodyPublisher publisher = sent.bodyPublisher().orElseThrow();
        assertThat(publisher.contentLength()).isEqualTo(2L);
        assertThat(collect(publisher).bytes())
                .containsExactly((byte) '{', (byte) '}');
    }

    @Test
    void boundsTheWholeExchangeWhenTheAcknowledgementNeverReachesEof() {
        HttpClient client = mock(HttpClient.class);
        AtomicReference<CompletableFuture<HttpResponse<byte[]>>> exchange =
                new AtomicReference<>();
        when(client.sendAsync(any(), anyByteArrayHandler())).thenAnswer(invocation -> {
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            HttpResponse.BodySubscriber<byte[]> subscriber = handler.apply(responseInfo(
                    201,
                    headers(J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE, null)));
            subscriber.onSubscribe(new TestSubscription());
            subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {'{'})));
            CompletableFuture<HttpResponse<byte[]>> pending = subscriber.getBody()
                    .toCompletableFuture()
                    .thenApply(body -> response(
                            201,
                            headers(J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE, null),
                            body));
            exchange.set(pending);
            return pending;
        });

        assertThatThrownBy(() -> transport(client, Duration.ofMillis(50)).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.TIMEOUT);
        assertThat(exchange.get()).isCancelled();
        verify(client, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    @Test
    void neverRetriesTimeoutTlsIoOrInterruption() {
        assertFailure(new HttpTimeoutException("timeout"), J7DeliveryTransportFailure.TIMEOUT);
        assertFailure(new IOException("outer", new SSLException("tls")),
                J7DeliveryTransportFailure.TLS_FAILURE);
        assertFailure(new IOException("io"), J7DeliveryTransportFailure.IO_FAILURE);

        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(client.sendAsync(any(), anyByteArrayHandler())).thenReturn(pending);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> transport(client).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.INTERRUPTED);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(pending).isCancelled();
        verify(client, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    @Test
    void refusesASecondBodySubscriptionWithoutPublishingAnyReplay() {
        HttpRequest.BodyPublisher publisher =
                BettingProjectJ7DeliveryHttpTransport.oneShotBodyPublisher(
                        new byte[] {'{', '}'});

        RecordingSubscriber first = collect(publisher);
        RecordingSubscriber replay = collect(publisher);

        assertThat(first.bytes()).containsExactly((byte) '{', (byte) '}');
        assertThat(first.failure()).isNull();
        assertThat(replay.bytes()).isEmpty();
        assertThat(replay.failure())
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(failure -> ((J7DeliveryTransportException) failure).failure())
                .isEqualTo(J7DeliveryTransportFailure.AUTOMATIC_REPLAY_BLOCKED);
    }

    @Test
    void refusesAmbientAutomaticPostRetryBeforeOpeningAnExchange() {
        for (Map.Entry<String, String> unsafe : Map.of(
                JdkHttpClientNoAutomaticRetryPolicy.DISABLE_RETRY_CONNECT, "false",
                JdkHttpClientNoAutomaticRetryPolicy.REDIRECTS_RETRY_LIMIT, "2",
                JdkHttpClientNoAutomaticRetryPolicy.ENABLE_ALL_METHOD_RETRY, "true"
        ).entrySet()) {
            String previousValue = System.getProperty(unsafe.getKey());
            System.setProperty(unsafe.getKey(), unsafe.getValue());
            try {
                HttpClient client = mock(HttpClient.class);

                assertThatThrownBy(() -> transport(client).execute(request()))
                        .isInstanceOf(J7DeliveryTransportException.class)
                        .extracting(exception ->
                                ((J7DeliveryTransportException) exception).failure())
                        .isEqualTo(J7DeliveryTransportFailure.AUTOMATIC_REPLAY_BLOCKED);
                verifyNoInteractions(client);
            }
            finally {
                restoreProperty(unsafe.getKey(), previousValue);
            }
        }
    }

    @Test
    void preservesAReplayRefusalRaisedByASecondHttpClientSubscription() {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(), anyByteArrayHandler())).thenAnswer(invocation -> {
            HttpRequest sent = invocation.getArgument(0);
            HttpRequest.BodyPublisher publisher = sent.bodyPublisher().orElseThrow();
            RecordingSubscriber first = collect(publisher);
            RecordingSubscriber replay = collect(publisher);
            assertThat(first.bytes()).containsExactly((byte) '{', (byte) '}');
            return CompletableFuture.failedFuture(replay.failure());
        });

        assertThatThrownBy(() -> transport(client).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.AUTOMATIC_REPLAY_BLOCKED);
        verify(client, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    @Test
    void refusesAnOversizedAcknowledgementWithoutASecondCall() {
        HttpClient client = mock(HttpClient.class);
        stubResponse(
                client,
                200,
                J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE,
                null,
                new byte[16 * 1024 + 1]);

        assertThatThrownBy(() -> transport(client).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.ACKNOWLEDGEMENT_TOO_LARGE);
        verify(client, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    @Test
    void translatesHostileResponseMetadataToATransportFailure() {
        HttpClient client = mock(HttpClient.class);
        stubResponse(
                client,
                201,
                "a".repeat(129),
                null,
                new byte[] {'{', '}'});

        assertThatThrownBy(() -> transport(client).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.INVALID_RESPONSE_METADATA);
        verify(client, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    @Test
    void rejectsEncodedAcknowledgementsAndOversizedErrorBodies() {
        HttpClient encodedClient = mock(HttpClient.class);
        stubResponse(
                encodedClient,
                200,
                J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE,
                "gzip",
                new byte[] {1, 2, 3});

        assertThatThrownBy(() -> transport(encodedClient).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.UNSUPPORTED_ACKNOWLEDGEMENT_ENCODING);
        verify(encodedClient, times(1)).sendAsync(any(), anyByteArrayHandler());

        HttpClient rejectedClient = mock(HttpClient.class);
        stubResponse(
                rejectedClient,
                409,
                "application/problem+json",
                "gzip",
                new byte[16 * 1024 + 1]);

        assertThatThrownBy(() -> transport(rejectedClient).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(J7DeliveryTransportFailure.ACKNOWLEDGEMENT_TOO_LARGE);
        verify(rejectedClient, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    @Test
    void acceptsOnlyAnExactHttpsIpv4LoopbackOriginAndBoundedTimeout() {
        for (String invalid : List.of(
                "http://127.0.0.1:49152",
                "https://localhost:49152",
                "https://127.0.0.1:49152/path",
                "https://user@127.0.0.1:49152",
                "https://127.0.0.1:49152?query=1")) {
            assertThatThrownBy(() -> BettingProjectJ7DeliveryHttpTransport
                    .forTest(mock(HttpClient.class), URI.create(invalid),
                            Duration.ofSeconds(10), Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> BettingProjectJ7DeliveryHttpTransport
                .forTest(mock(HttpClient.class), ORIGIN,
                        Duration.ofSeconds(11), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTheMaximumTcpPortAndRejectsTheFirstOutOfRangePortBeforeClientUse() {
        HttpClient client = mock(HttpClient.class);

        assertThat(BettingProjectJ7DeliveryHttpTransport.forTest(
                client,
                URI.create("https://127.0.0.1:65535"),
                Duration.ofSeconds(10),
                Clock.systemUTC())).isNotNull();

        assertThatThrownBy(() -> BettingProjectJ7DeliveryHttpTransport.forTest(
                client,
                URI.create("https://127.0.0.1:65536"),
                Duration.ofSeconds(10),
                Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("origin must be an exact HTTPS IPv4 loopback origin");
        verifyNoInteractions(client);
    }

    @Test
    void validatesEveryConstructionArgumentBeforeBuildingAnOwnedHttpClient()
            throws Exception {
        SSLContext sslContext = SSLContext.getDefault();

        assertThatThrownBy(() -> BettingProjectJ7DeliveryHttpTransport.forSyntheticLoopback(
                ORIGIN,
                sslContext,
                Duration.ofSeconds(1),
                Duration.ofSeconds(11),
                Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestTimeout");
        assertThatThrownBy(() -> BettingProjectJ7DeliveryHttpTransport.forSyntheticLoopback(
                ORIGIN,
                sslContext,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock");
    }

    @Test
    void forcesAndProvesOwnedClientTerminationWhenGracefulCleanupIsInterrupted()
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.awaitTermination(any(Duration.class)))
                .thenThrow(new InterruptedException("synthetic interruption"))
                .thenReturn(true);
        when(client.isTerminated()).thenReturn(false, true);
        BettingProjectJ7DeliveryHttpTransport transport = ownedTransport(client);

        assertThatThrownBy(transport::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interrupted after forced termination");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(transport.isTerminated()).isTrue();
        verify(client).shutdown();
        verify(client).shutdownNow();
        verify(client, times(2)).awaitTermination(any(Duration.class));
    }

    @Test
    void failsClosedWhenAnOwnedClientDoesNotTerminateAfterForcedCleanup()
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.awaitTermination(any(Duration.class))).thenReturn(false);
        when(client.isTerminated()).thenReturn(false);
        BettingProjectJ7DeliveryHttpTransport transport = ownedTransport(client);

        assertThatThrownBy(transport::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delivery HTTP client cleanup did not terminate");

        assertThat(transport.isTerminated()).isFalse();
        verify(client).shutdown();
        verify(client).shutdownNow();
        verify(client, times(2)).awaitTermination(any(Duration.class));
    }

    @Test
    void aSecondCloseRetriesCleanupAfterAProvenNonTermination()
            throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.awaitTermination(any(Duration.class)))
                .thenReturn(false, false, true);
        when(client.isTerminated()).thenReturn(false, false, false, true);
        BettingProjectJ7DeliveryHttpTransport transport = ownedTransport(client);

        assertThatThrownBy(transport::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delivery HTTP client cleanup did not terminate");
        assertThat(transport.isTerminated()).isFalse();

        transport.close();

        assertThat(transport.isTerminated()).isTrue();
        verify(client, times(2)).shutdown();
        verify(client).shutdownNow();
        verify(client, times(3)).awaitTermination(any(Duration.class));
    }

    private static void assertFailure(
            Throwable failure,
            J7DeliveryTransportFailure expected) {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(), anyByteArrayHandler()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> transport(client).execute(request()))
                .isInstanceOf(J7DeliveryTransportException.class)
                .hasMessage(expected.name())
                .extracting(exception -> ((J7DeliveryTransportException) exception).failure())
                .isEqualTo(expected);
        verify(client, times(1)).sendAsync(any(), anyByteArrayHandler());
    }

    private static BettingProjectJ7DeliveryHttpTransport transport(HttpClient client) {
        return transport(client, Duration.ofSeconds(10));
    }

    private static BettingProjectJ7DeliveryHttpTransport transport(
            HttpClient client,
            Duration timeout) {
        return BettingProjectJ7DeliveryHttpTransport.forTest(
                client,
                ORIGIN,
                timeout,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BettingProjectJ7DeliveryHttpTransport ownedTransport(HttpClient client) {
        return BettingProjectJ7DeliveryHttpTransport.forOwnedClientTest(
                client,
                ORIGIN,
                Duration.ofSeconds(10),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static J7DeliveryTransportRequest request() {
        return new J7DeliveryTransportRequest(
                new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256),
                DATA_SHA256,
                new byte[] {'{', '}'});
    }

    private static void stubResponse(
            HttpClient client,
            int status,
            String contentType,
            String contentEncoding,
            byte[] body) {
        HttpHeaders responseHeaders = headers(contentType, contentEncoding);
        when(client.sendAsync(any(), anyByteArrayHandler())).thenAnswer(invocation -> {
            HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            try {
                HttpResponse.BodySubscriber<byte[]> subscriber = handler.apply(
                        responseInfo(status, responseHeaders));
                TestSubscription subscription = new TestSubscription();
                subscriber.onSubscribe(subscription);
                if (!subscription.cancelled()) {
                    subscriber.onNext(List.of(ByteBuffer.wrap(body)));
                }
                if (!subscription.cancelled()) {
                    subscriber.onComplete();
                }
                return subscriber.getBody().toCompletableFuture()
                        .thenApply(captured -> response(
                                status, responseHeaders, captured));
            }
            catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        });
    }

    private static HttpResponse.ResponseInfo responseInfo(
            int status,
            HttpHeaders headers) {
        return new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(
            int status,
            HttpHeaders headers,
            byte[] body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(headers);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static HttpHeaders headers(
            String contentType,
            String contentEncoding) {
        Map<String, List<String>> values = new java.util.LinkedHashMap<>();
        values.put("Content-Type", List.of(contentType));
        if (contentEncoding != null) {
            values.put("Content-Encoding", List.of(contentEncoding));
        }
        return HttpHeaders.of(values, (name, value) -> true);
    }

    private static RecordingSubscriber collect(HttpRequest.BodyPublisher publisher) {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        publisher.subscribe(subscriber);
        return subscriber;
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        }
        else {
            System.setProperty(name, previousValue);
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<byte[]> anyByteArrayHandler() {
        return (HttpResponse.BodyHandler<byte[]>) any(HttpResponse.BodyHandler.class);
    }

    private static final class TestSubscription implements Flow.Subscription {
        private boolean cancelled;

        @Override
        public void request(long count) {
            // Test data is driven synchronously by the fixture.
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        private boolean cancelled() {
            return cancelled;
        }
    }

    private static final class RecordingSubscriber
            implements Flow.Subscriber<ByteBuffer> {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] chunk = new byte[item.remaining()];
            item.get(chunk);
            bytes.writeBytes(chunk);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
        }

        @Override
        public void onComplete() {
            // Completion is evidenced by the lack of a failure and the complete body.
        }

        private byte[] bytes() {
            return bytes.toByteArray();
        }

        private Throwable failure() {
            return failure;
        }
    }
}
