package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryAcknowledgementParser;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryContract;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransport;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFailure;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportRequest;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportResponse;
import com.bettingproject.sofascorelocal.port.OwnedJ7DeliveryTransport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.net.CookieHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One-shot HTTPS transport for the exact loopback receiver. The transport itself is not a Spring
 * bean; WO-035 creates one owned instance only after all manual runtime gates have passed.
 */
public final class BettingProjectJ7DeliveryHttpTransport
        implements J7DeliveryTransport, OwnedJ7DeliveryTransport {

    public static final String IMPORT_PATH =
            J7DeliveryContract.RELATIVE_IMPORT_PATH;
    public static final String PROTOCOL_HEADER = J7DeliveryContract.PROTOCOL_HEADER;
    public static final String EXPORT_ID_HEADER = J7DeliveryContract.EXPORT_ID_HEADER;
    public static final String FILE_SHA256_HEADER = J7DeliveryContract.FILE_SHA256_HEADER;
    public static final String DATA_SHA256_HEADER = J7DeliveryContract.DATA_SHA256_HEADER;

    private static final Duration MAXIMUM_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CLIENT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(1);
    private static final int MAXIMUM_CONTENT_TYPE_LENGTH = 128;
    private static final int MAXIMUM_TCP_PORT = 65_535;
    private final HttpClient httpClient;
    private final URI endpointUri;
    private final Duration requestTimeout;
    private final Clock clock;
    private final boolean ownsHttpClient;
    private final AtomicBoolean executed = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private BettingProjectJ7DeliveryHttpTransport(
            HttpClient httpClient,
            URI endpointUri,
            Duration requestTimeout,
            Clock clock,
            boolean ownsHttpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpointUri = Objects.requireNonNull(endpointUri, "endpointUri");
        this.requestTimeout = requireBoundedTimeout(requestTimeout, "requestTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ownsHttpClient = ownsHttpClient;
    }

    public static BettingProjectJ7DeliveryHttpTransport forSyntheticLoopback(
            URI origin,
            SSLContext sslContext,
            Duration connectTimeout,
            Duration requestTimeout,
            Clock clock) {
        URI exactOrigin = requireExactHttpsIpv4LoopbackOrigin(origin);
        SSLContext exactSslContext = Objects.requireNonNull(sslContext, "sslContext");
        Duration exactConnectTimeout = requireBoundedTimeout(
                connectTimeout, "connectTimeout");
        Duration exactRequestTimeout = requireBoundedTimeout(
                requestTimeout, "requestTimeout");
        Clock exactClock = Objects.requireNonNull(clock, "clock");
        URI endpointUri = exactOrigin.resolve(IMPORT_PATH);
        JdkHttpClientNoAutomaticRetryPolicy.requireSatisfiedFromStartupAndNow();
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        sslParameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(exactConnectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(HttpClient.Builder.NO_PROXY)
                .cookieHandler(new RejectingCookieHandler())
                .sslContext(exactSslContext)
                .sslParameters(sslParameters)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return new BettingProjectJ7DeliveryHttpTransport(
                httpClient,
                endpointUri,
                exactRequestTimeout,
                exactClock,
                true);
    }

    public static BettingProjectJ7DeliveryHttpTransport forLocalReceiver(
            URI origin,
            SSLContext sslContext,
            Duration connectTimeout,
            Duration requestTimeout,
            Clock clock) {
        return forSyntheticLoopback(
                origin, sslContext, connectTimeout, requestTimeout, clock);
    }

    static BettingProjectJ7DeliveryHttpTransport forTest(
            HttpClient httpClient,
            URI origin,
            Duration requestTimeout,
            Clock clock) {
        return new BettingProjectJ7DeliveryHttpTransport(
                httpClient,
                requireExactHttpsIpv4LoopbackOrigin(origin).resolve(IMPORT_PATH),
                requestTimeout,
                clock,
                false);
    }

    static BettingProjectJ7DeliveryHttpTransport forOwnedClientTest(
            HttpClient httpClient,
            URI origin,
            Duration requestTimeout,
            Clock clock) {
        return new BettingProjectJ7DeliveryHttpTransport(
                httpClient,
                requireExactHttpsIpv4LoopbackOrigin(origin).resolve(IMPORT_PATH),
                requestTimeout,
                clock,
                true);
    }

    @Override
    public J7DeliveryTransportResponse execute(J7DeliveryTransportRequest request) {
        Objects.requireNonNull(request, "request");
        if (!executed.compareAndSet(false, true)) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.AUTOMATIC_REPLAY_BLOCKED);
        }
        if (closed.get()) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.IO_FAILURE);
        }
        JdkHttpClientNoAutomaticRetryPolicy.requireSatisfiedFromStartupAndNow();
        HttpRequest httpRequest = HttpRequest.newBuilder(endpointUri)
                .timeout(requestTimeout)
                .header("Content-Type", J7DeliveryContract.REQUEST_MEDIA_TYPE)
                .header("Accept", J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE)
                .header(J7DeliveryContract.IDEMPOTENCY_HEADER,
                        request.identity().idempotencyKey())
                .header(J7DeliveryContract.PROTOCOL_HEADER,
                        J7DeliveryContract.PROTOCOL_VERSION)
                .header(J7DeliveryContract.EXPORT_ID_HEADER,
                        request.identity().exportId().toString())
                .header(J7DeliveryContract.FILE_SHA256_HEADER,
                        request.identity().fileSha256())
                .header(J7DeliveryContract.DATA_SHA256_HEADER,
                        request.dataSha256())
                .POST(oneShotBodyPublisher(request.content()))
                .build();
        CompletableFuture<HttpResponse<byte[]>> exchange = null;
        long startedAtNanos = System.nanoTime();
        try {
            exchange = httpClient.sendAsync(httpRequest, boundedBodyHandler());
            long elapsedNanos = System.nanoTime() - startedAtNanos;
            long remainingNanos = requestTimeout.toNanos() - elapsedNanos;
            if (remainingNanos <= 0) {
                throw new TimeoutException("delivery deadline elapsed");
            }
            HttpResponse<byte[]> response = exchange.get(remainingNanos, TimeUnit.NANOSECONDS);
            return toTransportResponse(response);
        }
        catch (J7DeliveryTransportException exception) {
            throw exception;
        }
        catch (TimeoutException exception) {
            cancel(exchange);
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.TIMEOUT,
                    exception);
        }
        catch (InterruptedException exception) {
            cancel(exchange);
            Thread.currentThread().interrupt();
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.INTERRUPTED,
                    exception);
        }
        catch (ExecutionException exception) {
            J7DeliveryTransportException transportException =
                    findTransportException(exception);
            if (transportException != null) {
                throw transportException;
            }
            throw new J7DeliveryTransportException(classify(exception), exception);
        }
    }

    @Override
    public synchronized void close() {
        closed.set(true);
        if (!ownsHttpClient || httpClient.isTerminated()) {
            return;
        }
        httpClient.shutdown();
        InterruptedException interruption = null;
        boolean terminated = false;
        try {
            terminated = httpClient.awaitTermination(CLIENT_SHUTDOWN_TIMEOUT);
        }
        catch (InterruptedException exception) {
            interruption = exception;
        }
        if (!terminated) {
            httpClient.shutdownNow();
            TerminationWait forcedWait = awaitTerminationPreservingInterrupt(
                    httpClient,
                    CLIENT_SHUTDOWN_TIMEOUT);
            terminated = forcedWait.terminated();
            if (interruption == null) {
                interruption = forcedWait.interruption();
            }
            else if (forcedWait.interruption() != null) {
                interruption.addSuppressed(forcedWait.interruption());
            }
        }
        if (interruption != null) {
            Thread.currentThread().interrupt();
        }
        if (!terminated) {
            IllegalStateException failure = new IllegalStateException(
                    "delivery HTTP client cleanup did not terminate");
            if (interruption != null) {
                failure.addSuppressed(interruption);
            }
            throw failure;
        }
        if (interruption != null) {
            throw new IllegalStateException(
                    "delivery HTTP client cleanup was interrupted after forced termination",
                    interruption);
        }
    }

    public boolean isTerminated() {
        return !ownsHttpClient || httpClient.isTerminated();
    }

    private static TerminationWait awaitTerminationPreservingInterrupt(
            HttpClient client,
            Duration timeout) {
        long remainingNanos = timeout.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        InterruptedException interruption = null;
        while (remainingNanos > 0) {
            try {
                return new TerminationWait(
                        client.awaitTermination(Duration.ofNanos(remainingNanos)),
                        interruption);
            }
            catch (InterruptedException exception) {
                if (interruption == null) {
                    interruption = exception;
                }
                else {
                    interruption.addSuppressed(exception);
                }
                remainingNanos = deadline - System.nanoTime();
            }
        }
        return new TerminationWait(client.isTerminated(), interruption);
    }

    private J7DeliveryTransportResponse toTransportResponse(
            HttpResponse<byte[]> response) {
        Objects.requireNonNull(response, "response");
        ResponseMetadata metadata = requireValidMetadata(
                response.statusCode(), response.headers());
        byte[] boundedBody = response.body();
        if (boundedBody == null) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.INVALID_RESPONSE_METADATA);
        }
        if (metadata.positiveAcknowledgementCandidate()
                && !hasSupportedAcknowledgementEncoding(response.headers())) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.UNSUPPORTED_ACKNOWLEDGEMENT_ENCODING);
        }
        if (boundedBody.length > J7DeliveryAcknowledgementParser.MAXIMUM_BYTES) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.ACKNOWLEDGEMENT_TOO_LARGE);
        }
        try {
            return new J7DeliveryTransportResponse(
                    metadata.status(),
                    metadata.contentType(),
                    metadata.positiveAcknowledgementCandidate()
                            ? boundedBody
                            : new byte[0],
                    clock.instant());
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.INVALID_RESPONSE_METADATA,
                    exception);
        }
    }

    private static HttpResponse.BodyHandler<byte[]> boundedBodyHandler() {
        return responseInfo -> {
            ResponseMetadata metadata = requireValidMetadata(
                    responseInfo.statusCode(), responseInfo.headers());
            if (metadata.positiveAcknowledgementCandidate()
                    && !hasSupportedAcknowledgementEncoding(responseInfo.headers())) {
                throw new J7DeliveryTransportException(
                        J7DeliveryTransportFailure.UNSUPPORTED_ACKNOWLEDGEMENT_ENCODING);
            }
            return new BoundedBodySubscriber(
                    metadata.positiveAcknowledgementCandidate());
        };
    }

    private static ResponseMetadata requireValidMetadata(
            int status,
            HttpHeaders headers) {
        if (status < 100 || status > 599 || headers == null) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.INVALID_RESPONSE_METADATA);
        }
        List<String> contentTypes = headers.allValues("Content-Type");
        if (contentTypes.size() > 1) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.INVALID_RESPONSE_METADATA);
        }
        String contentType = contentTypes.isEmpty() ? "" : contentTypes.getFirst();
        if (contentType == null || contentType.length() > MAXIMUM_CONTENT_TYPE_LENGTH) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.INVALID_RESPONSE_METADATA);
        }
        return new ResponseMetadata(
                status,
                contentType,
                status >= 200 && status <= 299);
    }

    private static boolean hasSupportedAcknowledgementEncoding(HttpHeaders headers) {
        List<String> values = headers.allValues("Content-Encoding");
        return values.isEmpty()
                || values.size() == 1 && "identity".equalsIgnoreCase(values.getFirst().trim());
    }

    static HttpRequest.BodyPublisher oneShotBodyPublisher(byte[] content) {
        byte[] immutableContent = Objects.requireNonNull(content, "content").clone();
        HttpRequest.BodyPublisher delegate =
                HttpRequest.BodyPublishers.ofByteArray(immutableContent);
        AtomicBoolean subscribed = new AtomicBoolean();
        return new HttpRequest.BodyPublisher() {
            @Override
            public long contentLength() {
                return delegate.contentLength();
            }

            @Override
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                Objects.requireNonNull(subscriber, "subscriber");
                if (!subscribed.compareAndSet(false, true)) {
                    subscriber.onSubscribe(EmptySubscription.INSTANCE);
                    subscriber.onError(new J7DeliveryTransportException(
                            J7DeliveryTransportFailure.AUTOMATIC_REPLAY_BLOCKED));
                    return;
                }
                delegate.subscribe(subscriber);
            }
        };
    }

    private static void cancel(CompletableFuture<?> exchange) {
        if (exchange != null) {
            exchange.cancel(true);
        }
    }

    private static J7DeliveryTransportException findTransportException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof J7DeliveryTransportException transportException) {
                return transportException;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return null;
            }
            current = cause;
        }
        return null;
    }

    static J7DeliveryTransportFailure classify(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current != null) {
            if (current instanceof HttpTimeoutException) {
                return J7DeliveryTransportFailure.TIMEOUT;
            }
            if (current instanceof SSLException) {
                return J7DeliveryTransportFailure.TLS_FAILURE;
            }
            current = current.getCause();
        }
        return J7DeliveryTransportFailure.IO_FAILURE;
    }

    private record ResponseMetadata(
            int status,
            String contentType,
            boolean positiveAcknowledgementCandidate) {
    }

    private record TerminationWait(
            boolean terminated,
            InterruptedException interruption) {
    }

    private enum EmptySubscription implements Flow.Subscription {
        INSTANCE;

        @Override
        public void request(long count) {
            // There is deliberately no content after a replay is refused.
        }

        @Override
        public void cancel() {
            // There is deliberately no underlying subscription to cancel.
        }
    }

    private static final class BoundedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {

        private final boolean captureBody;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream captured;
        private final AtomicReference<Flow.Subscription> subscription =
                new AtomicReference<>();
        private final AtomicBoolean terminated = new AtomicBoolean();
        private long receivedBytes;

        private BoundedBodySubscriber(boolean captureBody) {
            this.captureBody = captureBody;
            this.captured = new ByteArrayOutputStream(captureBody
                    ? J7DeliveryAcknowledgementParser.MAXIMUM_BYTES
                    : 0);
        }

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            Objects.requireNonNull(newSubscription, "newSubscription");
            if (!subscription.compareAndSet(null, newSubscription)) {
                newSubscription.cancel();
                return;
            }
            newSubscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (terminated.get()) {
                return;
            }
            for (ByteBuffer source : Objects.requireNonNull(buffers, "buffers")) {
                ByteBuffer buffer = Objects.requireNonNull(source, "buffer").duplicate();
                int chunkLength = buffer.remaining();
                if (receivedBytes
                        > J7DeliveryAcknowledgementParser.MAXIMUM_BYTES - chunkLength) {
                    terminateOversized();
                    return;
                }
                receivedBytes += chunkLength;
                if (captureBody) {
                    byte[] chunk = new byte[chunkLength];
                    buffer.get(chunk);
                    captured.writeBytes(chunk);
                }
            }
            Flow.Subscription current = subscription.get();
            if (!terminated.get() && current != null) {
                current.request(1);
            }
        }

        @Override
        public void onError(Throwable failure) {
            if (terminated.compareAndSet(false, true)) {
                body.completeExceptionally(Objects.requireNonNull(failure, "failure"));
            }
        }

        @Override
        public void onComplete() {
            if (terminated.compareAndSet(false, true)) {
                body.complete(captureBody ? captured.toByteArray() : new byte[0]);
            }
        }

        private void terminateOversized() {
            if (terminated.compareAndSet(false, true)) {
                Flow.Subscription current = subscription.get();
                if (current != null) {
                    current.cancel();
                }
                body.completeExceptionally(new J7DeliveryTransportException(
                        J7DeliveryTransportFailure.ACKNOWLEDGEMENT_TOO_LARGE));
            }
        }
    }

    static URI requireExactHttpsIpv4LoopbackOrigin(URI origin) {
        Objects.requireNonNull(origin, "origin");
        if (!"https".equals(origin.getScheme())
                || !"127.0.0.1".equals(origin.getHost())
                || origin.getPort() < 1
                || origin.getPort() > MAXIMUM_TCP_PORT
                || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())) {
            throw new IllegalArgumentException(
                    "origin must be an exact HTTPS IPv4 loopback origin");
        }
        return origin;
    }

    private static Duration requireBoundedTimeout(Duration timeout, String name) {
        Objects.requireNonNull(timeout, name);
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAXIMUM_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    name + " must be positive and no greater than ten seconds");
        }
        return timeout;
    }

    private static final class RejectingCookieHandler extends CookieHandler {
        @Override
        public java.util.Map<String, java.util.List<String>> get(
                URI uri,
                java.util.Map<String, java.util.List<String>> requestHeaders) {
            return java.util.Map.of();
        }

        @Override
        public void put(
                URI uri,
                java.util.Map<String, java.util.List<String>> responseHeaders) {
            // Deliberately ignore every cookie response.
        }
    }
}
