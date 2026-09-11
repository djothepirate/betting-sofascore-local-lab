package com.bettingproject.sofascorelocal.provider.playwright.worker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone, silent Playwright process. It is launched and terminated only by the parent supervisor. */
public final class ProviderPlaywrightWorkerMain {

    private static final int IPC_CONNECT_TIMEOUT_MILLIS = 5_000;

    private ProviderPlaywrightWorkerMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 0) {
            System.exit(64);
        }
        int exitCode = run(System.getenv());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(Map<String, String> environment) {
        ProviderPlaywrightWorkerConfiguration configuration;
        try {
            configuration = ProviderPlaywrightWorkerConfiguration.fromEnvironment(environment);
        }
        catch (RuntimeException exception) {
            return 64;
        }

        try (Socket socket = connect(configuration.ipcPort());
             DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            ProviderPlaywrightWorkerProtocol.writeHandshake(output, configuration.token());
            try {
                ProviderPlaywrightWorkerProtocol.requireStart(input);
            }
            catch (ProviderPlaywrightWorkerProtocol.ProtocolValidationException exception) {
                ProviderPlaywrightWorkerProtocol.writeFailure(output, exception.failureCode());
                return 65;
            }
            try (WorkerRuntime runtime = WorkerRuntime.open()) {
                ProviderPlaywrightWorkerProtocol.writeReady(output);
                return commandLoop(configuration, runtime, input, output);
            }
            catch (RuntimeStartException exception) {
                ProviderPlaywrightWorkerProtocol.writeFailure(
                        output,
                        ProviderPlaywrightWorkerProtocol.FailureCode.RUNTIME_START_FAILED);
                return 70;
            }
        }
        catch (IOException exception) {
            return 71;
        }
        catch (RuntimeException exception) {
            return 72;
        }
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port),
                    IPC_CONNECT_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(false);
            return socket;
        }
        catch (IOException exception) {
            try {
                socket.close();
            }
            catch (IOException ignored) {
                // Nothing else owns this failed socket.
            }
            throw exception;
        }
    }

    private static int commandLoop(
            ProviderPlaywrightWorkerConfiguration configuration,
            WorkerRuntime runtime,
            DataInputStream input,
            DataOutputStream output) throws IOException {
        while (true) {
            int command;
            try {
                command = input.readUnsignedByte();
            }
            catch (EOFException exception) {
                return 0;
            }

            if (command == ProviderPlaywrightWorkerProtocol.CLOSE) {
                runtime.close();
                ProviderPlaywrightWorkerProtocol.writeClosed(output);
                ProviderPlaywrightWorkerProtocol.awaitParentTermination(input);
                return 0;
            }
            boolean liveV6 = command == ProviderPlaywrightWorkerProtocol.GET_LIVE_V6;
            boolean liveV9 = command == ProviderPlaywrightWorkerProtocol.GET_LIVE_V9;
            boolean boundedLive = liveV6 || liveV9;
            if (command != ProviderPlaywrightWorkerProtocol.GET && !boundedLive) {
                ProviderPlaywrightWorkerProtocol.writeFailure(
                        output, ProviderPlaywrightWorkerProtocol.FailureCode.PROTOCOL_ERROR);
                return 65;
            }

            ProviderPlaywrightWorkerProtocol.GetCommand request;
            try {
                request = liveV9
                        ? ProviderPlaywrightWorkerProtocol.readLiveV9GetCommand(input)
                        : ProviderPlaywrightWorkerProtocol.readGetCommand(input);
            }
            catch (ProviderPlaywrightWorkerProtocol.ProtocolValidationException exception) {
                ProviderPlaywrightWorkerProtocol.writeFailure(output, exception.failureCode());
                return 65;
            }
            if (boundedLive && (request.timeoutMillis() > 30_000
                    || request.endpoint() == ProviderPlaywrightWorkerProtocol.Endpoint.SCHEDULED_EVENTS
                    || request.endpoint() == ProviderPlaywrightWorkerProtocol.Endpoint.TOURNAMENT_SCHEDULED_EVENTS)) {
                ProviderPlaywrightWorkerProtocol.writeFailure(output,
                        ProviderPlaywrightWorkerProtocol.FailureCode.PROTOCOL_ERROR);
                return 65;
            }

            ExecutionResult result = runtime.execute(
                    configuration.uriFor(request).toASCIIString(),
                    request.timeoutMillis(),
                    boundedLive,
                    liveV9 && request.ifNoneMatch() != null,
                    liveV9 ? request.ifNoneMatch() : null,
                    frame -> {
                        try { ProviderPlaywrightWorkerProtocol.writeProgress(output, frame); }
                        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    });
            if (result.timeoutEnded() != null) {
                ProviderPlaywrightWorkerProtocol.writeTimeoutEnded(output, result.timeoutEnded());
                continue;
            }
            if (result.failure() != null) {
                ProviderPlaywrightWorkerProtocol.writeFailure(output, result.failure());
                return 66;
            }
            if (liveV9) {
                ProviderPlaywrightWorkerProtocol.writeLiveV9Response(
                        output, result.response(), result.entityTag());
            }
            else {
                ProviderPlaywrightWorkerProtocol.writeResponse(output, result.response());
            }
        }
    }

    private record ExecutionResult(
            ProviderPlaywrightWorkerProtocol.ResponseFrame response,
            ProviderPlaywrightWorkerProtocol.EntityTag entityTag,
            ProviderPlaywrightWorkerProtocol.FailureCode failure,
            ProviderPlaywrightWorkerProtocol.TimeoutEndedFrame timeoutEnded) {

        static ExecutionResult success(
                ProviderPlaywrightWorkerProtocol.ResponseFrame response,
                ProviderPlaywrightWorkerProtocol.EntityTag entityTag) {
            return new ExecutionResult(response, entityTag, null, null);
        }

        static ExecutionResult failure(ProviderPlaywrightWorkerProtocol.FailureCode failure) {
            return new ExecutionResult(null, null, failure, null);
        }

        static ExecutionResult timeoutEnded(ProviderMainDocumentNetworkObservation.TerminalProof proof) {
            return new ExecutionResult(null, null, null, new ProviderPlaywrightWorkerProtocol.TimeoutEndedFrame(
                    proof.endedAt().toEpochMilli(), proof.reason()));
        }
    }

    /**
     * The CDP response-stage evidence needed when Chromium maps a valid conditional 304 document
     * navigation to {@code net::ERR_ABORTED}. It is held for one exchange only.
     */
    private record NotModifiedResponse(
            long requestedEpochMillis,
            long receivedEpochMillis,
            String contentType,
            ProviderPlaywrightWorkerProtocol.EntityTag entityTag) {
    }

    private static final class WorkerRuntime implements AutoCloseable {

        private final Playwright playwright;
        private final Browser browser;
        private final BrowserContext context;
        private final AtomicReference<String> exactAllowedUri = new AtomicReference<>();
        private final AtomicReference<ProviderPlaywrightWorkerProtocol.EntityTag> exactIfNoneMatch =
                new AtomicReference<>();
        private final AtomicReference<ProviderPlaywrightWorkerProtocol.FailureCode> routeFailure =
                new AtomicReference<>();
        private final AtomicBoolean exactNavigationAdmission = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private WorkerRuntime(Playwright playwright, Browser browser, BrowserContext context) {
            this.playwright = playwright;
            this.browser = browser;
            this.context = context;
            context.route("**/*", this::handleRoute);
            context.routeWebSocket("**/*", route -> {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                route.close();
            });
        }

        static WorkerRuntime open() throws RuntimeStartException {
            Playwright playwright = null;
            Browser browser = null;
            BrowserContext context = null;
            try {
                playwright = Playwright.create();
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                context = browser.newContext(new Browser.NewContextOptions()
                        .setAcceptDownloads(false)
                        .setJavaScriptEnabled(false)
                        .setServiceWorkers(ServiceWorkerPolicy.BLOCK));
                context.clearCookies();
                return new WorkerRuntime(playwright, browser, context);
            }
            catch (RuntimeException exception) {
                closeQuietly(context, browser, playwright);
                throw new RuntimeStartException(exception);
            }
        }

        ExecutionResult execute(
                String exactUri,
                int timeoutMillis,
                boolean boundedLive,
                boolean acceptsNotModifiedResponse,
                ProviderPlaywrightWorkerProtocol.EntityTag ifNoneMatch,
                java.util.function.Consumer<ProviderPlaywrightWorkerProtocol.ProgressFrame> observer) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            if (closed.get()) {
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
            }
            exactAllowedUri.set(exactUri);
            exactIfNoneMatch.set(ifNoneMatch);
            routeFailure.set(null);
            exactNavigationAdmission.set(true);
            RequestProgress progress = new RequestProgress(timeoutMillis, observer);
            progress.navigation();
            context.clearCookies();
            Page page = context.newPage();
            page.onPopup(popup -> {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                popup.close();
            });
            page.onDownload(download -> {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                download.cancel();
            });
            CDPSession networkObserver = null;
            CDPSession responseGuard = null;
            ProviderMainDocumentNetworkObservation networkObservation = null;
            AtomicBoolean cleanupVerified = new AtomicBoolean();
            AtomicReference<NotModifiedResponse> notModifiedResponse = new AtomicReference<>();
            try {
                networkObserver = context.newCDPSession(page);
                String mainFrameId = ProviderMainDocumentNetworkObservation.requireMainFrameId(
                        networkObserver.send("Page.getFrameTree"));
                networkObservation = new ProviderMainDocumentNetworkObservation(exactUri, mainFrameId);
                ProviderMainDocumentNetworkObservation observation = networkObservation;
                networkObserver.on(
                        "Network.requestWillBeSent",
                        event -> {
                            observation.onRequestWillBeSent(event);
                            Instant started = observation.requestStartedAtIfObserved();
                            if (started != null) progress.sent(started);
                        });
                networkObserver.on(
                        "Network.requestServedFromCache",
                        observation::onRequestServedFromCache);
                networkObserver.on(
                        "Network.responseReceived",
                        observation::onResponseReceived);
                networkObserver.on("Network.loadingFinished", observation::onLoadingFinished);
                networkObserver.on("Network.loadingFailed", observation::onLoadingFailed);
                networkObserver.send("Network.enable");
                JsonObject cacheDisabled = new JsonObject();
                cacheDisabled.addProperty("cacheDisabled", true);
                networkObserver.send("Network.setCacheDisabled", cacheDisabled);

                responseGuard = context.newCDPSession(page);
                CDPSession activeResponseGuard = responseGuard;
                responseGuard.on("Fetch.requestPaused", event ->
                        handlePausedResponse(activeResponseGuard, event, observation, progress,
                                acceptsNotModifiedResponse, notModifiedResponse));
                responseGuard.send("Fetch.enable", responseStageOnly());
                Response response;
                try {
                    response = page.navigate(exactUri, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.COMMIT)
                            .setTimeout(boundedLive ? remainingMillis(deadline) : (double) timeoutMillis));
                }
                catch (PlaywrightException exception) {
                    NotModifiedResponse captured = notModifiedResponse.get();
                    if (captured != null && routeFailure.get() == null) {
                        return completeNotModifiedExchange(
                                page, captured, progress, cleanupVerified, deadline);
                    }
                    throw exception;
                }
                ProviderPlaywrightWorkerProtocol.FailureCode failure = routeFailure.get();
                if (failure != null) {
                    return ExecutionResult.failure(failure);
                }
                NotModifiedResponse captured = notModifiedResponse.get();
                if (captured != null && (response == null || !exactUri.equals(response.url())
                        || response.status() == 304)) {
                    return completeNotModifiedExchange(
                            page, captured, progress, cleanupVerified, deadline);
                }
                if (response == null || !exactUri.equals(response.url())) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                }
                if (isBlockedRedirect(response.status(), acceptsNotModifiedResponse)) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.REDIRECT_BLOCKED);
                }
                if (response.fromServiceWorker()) {
                    networkObservation.rejectNonNetworkResponse();
                }
                long requestedEpochMillis;
                try {
                    requestedEpochMillis = networkObservation
                            .requireNetworkStartedAt()
                            .toEpochMilli();
                }
                catch (RuntimeException exception) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
                }
                String contentType = response.headerValue("content-type");
                if (contentType == null) {
                    contentType = "";
                }
                try {
                    ProviderPlaywrightWorkerProtocol.validateContentType(contentType);
                }
                catch (IllegalArgumentException exception) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.CONTENT_TYPE_TOO_LONG);
                }
                Long declaredLength = declaredContentLength(response.headerValue("content-length"));
                if (declaredLength != null
                        && declaredLength > ProviderPlaywrightWorkerProtocol.MAX_BODY_BYTES) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.PAYLOAD_TOO_LARGE);
                }
                byte[] body;
                long receivedEpochMillis;
                ProviderPlaywrightWorkerProtocol.EntityTag entityTag = responseEntityTag(
                        response.headerValue("etag"));
                progress.readingBody();
                if (boundedLive) {
                    page.waitForCondition(observation::terminalObserved,
                            new Page.WaitForConditionOptions().setTimeout(remainingMillis(deadline)));
                    var terminal = observation.terminalProofIfObserved();
                    if (terminal == null || terminal.reason() != 1)
                        return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.RESPONSE_READ_FAILED);
                    remainingMillis(deadline); // A response that finishes after the deadline remains abandoned.
                }
                try {
                    if (response.status() == 304) {
                        body = new byte[0];
                    }
                    else {
                        body = response.body();
                    }
                    receivedEpochMillis = Instant.now().toEpochMilli();
                }
                catch (PlaywrightException exception) {
                    if (boundedLive && isTimeout(exception)) return completeTimedOutExchange(
                            page, networkObserver, networkObservation, progress, cleanupVerified, deadline);
                    return ExecutionResult.failure(
                            isTimeout(exception)
                                    ? ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT
                                    : ProviderPlaywrightWorkerProtocol.FailureCode.RESPONSE_READ_FAILED);
                }
                if (body.length > ProviderPlaywrightWorkerProtocol.MAX_BODY_BYTES) {
                    Arrays.fill(body, (byte) 0);
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.PAYLOAD_TOO_LARGE);
                }
                failure = routeFailure.get();
                if (failure != null) {
                    Arrays.fill(body, (byte) 0);
                    return ExecutionResult.failure(failure);
                }
                try {
                    if (boundedLive) {
                        remainingMillis(deadline);
                        verifyPageCleanup(page, deadline, cleanupVerified);
                    }
                    if (receivedEpochMillis < requestedEpochMillis) {
                        return ExecutionResult.failure(
                                ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
                    }
                    return ExecutionResult.success(new ProviderPlaywrightWorkerProtocol.ResponseFrame(
                            requestedEpochMillis,
                            receivedEpochMillis,
                            response.status(),
                            contentType,
                            body), entityTag);
                }
                finally {
                    Arrays.fill(body, (byte) 0);
                }
            }
            catch (TimeoutError exception) {
                if (boundedLive) return completeTimedOutExchange(page, networkObserver, networkObservation,
                        progress, cleanupVerified, deadline);
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
            }
            catch (PlaywrightException exception) {
                ProviderPlaywrightWorkerProtocol.FailureCode failure = routeFailure.get();
                if (failure == null && isTimeout(exception)) {
                    if (boundedLive) return completeTimedOutExchange(page, networkObserver, networkObservation,
                            progress, cleanupVerified, deadline);
                    failure = ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT;
                }
                return ExecutionResult.failure(failure == null
                        ? ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE
                        : failure);
            }
            finally {
                exactAllowedUri.set(null);
                exactIfNoneMatch.set(null);
                exactNavigationAdmission.set(false);
                if (!cleanupVerified.get()) {
                    try {
                        page.close();
                    }
                    catch (PlaywrightException ignored) {
                        // Closing the campaign can terminate Chromium before this request returns.
                    }
                    disableAndDetach(responseGuard, "Fetch.disable");
                    disableAndDetach(networkObserver, "Network.disable");
                    context.clearCookies();
                }
            }
        }

        private static double remainingMillis(long deadline) {
            long nanos = deadline - System.nanoTime();
            if (nanos <= 0) throw new TimeoutError("configured exchange deadline expired");
            return Math.max(1.0, nanos / 1_000_000.0);
        }

        private ExecutionResult completeTimedOutExchange(Page page, CDPSession session,
                ProviderMainDocumentNetworkObservation observation, RequestProgress progress,
                AtomicBoolean cleanupVerified, long requestDeadline) {
            try {
                if (System.nanoTime() < requestDeadline)
                    return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
                long cancellationDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
                if (observation == null || session == null || observation.requestStartedAtIfObserved() == null)
                    return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
                if (!observation.terminalObserved()) {
                    if (progress.headers < 0) {
                        observation.beginCancellation();
                        session.send("Page.stopLoading");
                    }
                    // After COMMIT, stopLoading does not reliably emit a correlated terminal
                    // event in Chromium. The already abandoned body is never read: only a
                    // natural terminal arriving within the same two-second grace can qualify.
                    page.waitForCondition(observation::terminalObserved,
                            new Page.WaitForConditionOptions().setTimeout(remainingMillis(cancellationDeadline)));
                }
                var proof = observation.terminalProofIfObserved();
                if (proof == null || routeFailure.get() != null || progress.status == 403 || progress.status == 429)
                    return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
                // Revoke routing before closing; local checks make no additional provider request.
                verifyPageCleanup(page, cancellationDeadline, cleanupVerified);
                return ExecutionResult.timeoutEnded(proof);
            }
            catch (RuntimeException ignored) {
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
            }
        }

        private ExecutionResult completeNotModifiedExchange(
                Page page,
                NotModifiedResponse response,
                RequestProgress progress,
                AtomicBoolean cleanupVerified,
                long deadline) {
            try {
                // Chromium treats a conditional document 304 as a canceled navigation. The exact
                // response-stage CDP event already proved the response and 304 cannot carry a body.
                progress.readingBody();
                remainingMillis(deadline);
                verifyPageCleanup(page, deadline, cleanupVerified);
                if (response.receivedEpochMillis() < response.requestedEpochMillis()) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
                }
                return ExecutionResult.success(new ProviderPlaywrightWorkerProtocol.ResponseFrame(
                        response.requestedEpochMillis(),
                        response.receivedEpochMillis(),
                        304,
                        response.contentType(),
                        new byte[0]), response.entityTag());
            }
            catch (TimeoutError exception) {
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
            }
            catch (PlaywrightException exception) {
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
            }
        }

        private void verifyPageCleanup(Page page, long deadline, AtomicBoolean cleanupVerified) {
            exactAllowedUri.set(null);
            exactIfNoneMatch.set(null);
            exactNavigationAdmission.set(false);
            page.close();
            context.clearCookies();
            if (!page.isClosed() || !context.pages().isEmpty() || !context.cookies().isEmpty()
                    || !browser.isConnected() || closed.get() || routeFailure.get() != null)
                throw new PlaywrightException("exact page cleanup could not be verified");
            remainingMillis(deadline);
            cleanupVerified.set(true);
        }

        private static void disableAndDetach(CDPSession session, String disableCommand) {
            if (session == null) {
                return;
            }
            try {
                session.send(disableCommand);
            }
            catch (PlaywrightException ignored) {
                // Detachment is still attempted below.
            }
            try {
                session.detach();
            }
            catch (PlaywrightException ignored) {
                // Closing the exact page also closes its attached CDP session.
            }
        }

        private void handlePausedResponse(CDPSession session, JsonObject event,
                ProviderMainDocumentNetworkObservation networkObservation, RequestProgress progress,
                boolean acceptsNotModifiedResponse,
                AtomicReference<NotModifiedResponse> notModifiedResponse) {
            String requestId = null;
            try {
                requestId = event.get("requestId").getAsString();
                JsonObject request = event.getAsJsonObject("request");
                String expectedUri = exactAllowedUri.get();
                String requestUri = request.get("url").getAsString();
                String requestMethod = request.get("method").getAsString();
                int responseStatus = event.get("responseStatusCode").getAsInt();
                JsonObject command = new JsonObject();
                command.addProperty("requestId", requestId);
                if (expectedUri == null
                        || !expectedUri.equals(requestUri)
                        || !"GET".equals(requestMethod)
                        || !"Document".equals(event.get("resourceType").getAsString())) {
                    routeFailure.compareAndSet(null,
                            ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                    command.addProperty("errorReason", "Aborted");
                    session.send("Fetch.failRequest", command);
                    return;
                }
                Instant started = networkObservation.requireRequestStartedAt(event.get("networkId").getAsString());
                progress.headers(started, responseStatus, event.getAsJsonArray("responseHeaders"));
                if (isBlockedRedirect(responseStatus, acceptsNotModifiedResponse)) {
                    routeFailure.compareAndSet(null,
                            ProviderPlaywrightWorkerProtocol.FailureCode.REDIRECT_BLOCKED);
                    command.addProperty("errorReason", "Aborted");
                    session.send("Fetch.failRequest", command);
                    return;
                }
                if (responseStatus == 304) {
                    NotModifiedResponse captured;
                    try {
                        captured = captureNotModifiedResponse(started, event.getAsJsonArray("responseHeaders"));
                    }
                    catch (IllegalArgumentException exception) {
                        routeFailure.compareAndSet(null,
                                ProviderPlaywrightWorkerProtocol.FailureCode.CONTENT_TYPE_TOO_LONG);
                        command.addProperty("errorReason", "Aborted");
                        session.send("Fetch.failRequest", command);
                        return;
                    }
                    if (!notModifiedResponse.compareAndSet(null, captured)) {
                        routeFailure.compareAndSet(null,
                                ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                        command.addProperty("errorReason", "Aborted");
                        session.send("Fetch.failRequest", command);
                        return;
                    }
                }
                session.send("Fetch.continueResponse", command);
            }
            catch (RuntimeException exception) {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
                if (requestId != null) {
                    JsonObject command = new JsonObject();
                    command.addProperty("requestId", requestId);
                    command.addProperty("errorReason", "Aborted");
                    try {
                        session.send("Fetch.failRequest", command);
                    }
                    catch (RuntimeException ignored) {
                        // The failed request or its exact Chromium session can already be closed.
                    }
                }
            }
        }

        private static final class RequestProgress {
            private final int timeout;
            private final java.util.function.Consumer<ProviderPlaywrightWorkerProtocol.ProgressFrame> observer;
            private int stage = -1;
            private long requested = -1, headers = -1, retryAfter = -1;
            private int status;
            RequestProgress(int timeout, java.util.function.Consumer<ProviderPlaywrightWorkerProtocol.ProgressFrame> observer) {
                this.timeout = timeout; this.observer = observer;
            }
            void navigation() { emit(0); }
            void sent(Instant at) {
                if (stage >= 1) return;
                requested = at.toEpochMilli(); emit(1);
            }
            void headers(Instant started, int status, JsonArray responseHeaders) {
                if (stage >= 2) throw new IllegalStateException("duplicate response headers");
                sent(started);
                this.status = status;
                Instant received = Instant.now();
                headers = received.toEpochMilli();
                String retry = null; int count = 0;
                if (responseHeaders != null) for (var value : responseHeaders) {
                    JsonObject header = value.getAsJsonObject();
                    if ("retry-after".equalsIgnoreCase(header.get("name").getAsString())) {
                        count++; retry = header.get("value").getAsString();
                    }
                }
                retryAfter = count == 1 ? ProviderRetryAfter.deadline(retry, received) : -1;
                emit(2);
            }
            void readingBody() { emit(3); }
            private void emit(int next) {
                if (next != stage + 1) throw new IllegalStateException("invalid progress sequence");
                observer.accept(new ProviderPlaywrightWorkerProtocol.ProgressFrame(next, timeout,
                        requested, headers, status, retryAfter));
                stage = next;
            }
        }

        private static JsonObject responseStageOnly() {
            JsonObject pattern = new JsonObject();
            pattern.addProperty("urlPattern", "*");
            pattern.addProperty("requestStage", "Response");
            JsonArray patterns = new JsonArray();
            patterns.add(pattern);
            JsonObject command = new JsonObject();
            command.add("patterns", patterns);
            return command;
        }

        private void handleRoute(Route route) {
            String expectedUri = exactAllowedUri.get();
            if (route.request().redirectedFrom() != null) {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.REDIRECT_BLOCKED);
                route.abort();
                return;
            }
            if (expectedUri == null
                    || !expectedUri.equals(route.request().url())
                    || !"GET".equals(route.request().method())
                    || !route.request().isNavigationRequest()
                    || !exactNavigationAdmission.compareAndSet(true, false)) {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                route.abort();
                return;
            }
            Map<String, String> requestHeaders = route.request().allHeaders();
            if (containsSensitiveRequestHeader(requestHeaders)
                    || containsIfNoneMatch(requestHeaders)) {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.SENSITIVE_REQUEST_BLOCKED);
                route.abort();
                return;
            }

            ProviderPlaywrightWorkerProtocol.EntityTag entityTag = exactIfNoneMatch.get();
            try {
                if (entityTag == null) {
                    route.resume();
                }
                else {
                    Map<String, String> resumedHeaders = new LinkedHashMap<>(requestHeaders);
                    resumedHeaders.put("If-None-Match", entityTag.value());
                    route.resume(new Route.ResumeOptions().setHeaders(resumedHeaders));
                }
            }
            catch (TimeoutError exception) {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
                abortQuietly(route);
            }
            catch (PlaywrightException exception) {
                routeFailure.compareAndSet(null, isTimeout(exception)
                        ? ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT
                        : ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
                abortQuietly(route);
            }
            finally {
                exactIfNoneMatch.compareAndSet(entityTag, null);
            }
        }

        private static boolean containsSensitiveRequestHeader(Map<String, String> headers) {
            return headers.keySet().stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.equals("authorization")
                            || name.equals("cookie")
                            || name.equals("proxy-authorization"));
        }

        private static boolean isBlockedRedirect(int status, boolean acceptsNotModifiedResponse) {
            return status >= 300 && status < 400
                    && !(status == 304 && acceptsNotModifiedResponse);
        }

        private static boolean containsIfNoneMatch(Map<String, String> headers) {
            return headers.keySet().stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.equals("if-none-match"));
        }

        private static NotModifiedResponse captureNotModifiedResponse(
                Instant requestedAt, JsonArray responseHeaders) {
            String contentType = responseHeaderValue(responseHeaders, "content-type");
            if (contentType == null) {
                contentType = "";
            }
            ProviderPlaywrightWorkerProtocol.validateContentType(contentType);
            Long declaredLength = declaredContentLength(
                    responseHeaderValue(responseHeaders, "content-length"));
            if (declaredLength != null
                    && declaredLength > ProviderPlaywrightWorkerProtocol.MAX_BODY_BYTES) {
                throw new IllegalArgumentException("invalid V9 304 content length");
            }
            return new NotModifiedResponse(
                    requestedAt.toEpochMilli(),
                    Instant.now().toEpochMilli(),
                    contentType,
                    responseEntityTag(responseHeaderValue(responseHeaders, "etag")));
        }

        private static String responseHeaderValue(JsonArray responseHeaders, String expectedName) {
            if (responseHeaders == null) {
                return null;
            }
            String result = null;
            for (var value : responseHeaders) {
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException("invalid response header");
                }
                JsonObject header = value.getAsJsonObject();
                if (!header.has("name") || !header.has("value")
                        || !header.get("name").isJsonPrimitive()
                        || !header.get("value").isJsonPrimitive()) {
                    throw new IllegalArgumentException("invalid response header");
                }
                String name = header.get("name").getAsString();
                if (expectedName.equalsIgnoreCase(name)) {
                    if (result != null) {
                        return null;
                    }
                    result = header.get("value").getAsString();
                }
            }
            return result;
        }

        private static ProviderPlaywrightWorkerProtocol.EntityTag responseEntityTag(String header) {
            if (header == null) {
                return null;
            }
            try {
                return new ProviderPlaywrightWorkerProtocol.EntityTag(header);
            }
            catch (IllegalArgumentException ignored) {
                // Entity tags are optional response metadata. An untrusted malformed value is dropped.
                return null;
            }
        }

        private static Long declaredContentLength(String header) {
            if (header == null || header.isBlank()) {
                return null;
            }
            try {
                long parsed = Long.parseLong(header.trim());
                return parsed < 0 ? null : parsed;
            }
            catch (NumberFormatException exception) {
                return null;
            }
        }

        private static boolean isTimeout(PlaywrightException exception) {
            String message = exception.getMessage();
            return message != null && message.toLowerCase(Locale.ROOT).contains("timeout");
        }

        private static void abortQuietly(Route route) {
            try {
                route.abort();
            }
            catch (PlaywrightException ignored) {
                // A failed or timed-out route can already be disposed by the driver.
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            closeQuietly(context, browser, playwright);
        }

        private static void closeQuietly(BrowserContext context, Browser browser, Playwright playwright) {
            try {
                if (context != null) {
                    context.clearCookies();
                    context.close();
                }
            }
            catch (RuntimeException ignored) {
                // External termination is owned by the parent supervisor.
            }
            try {
                if (browser != null) {
                    browser.close();
                }
            }
            catch (RuntimeException ignored) {
                // External termination is owned by the parent supervisor.
            }
            try {
                if (playwright != null) {
                    playwright.close();
                }
            }
            catch (RuntimeException ignored) {
                // Nothing must escape or be logged from this isolated process.
            }
        }
    }

    private static final class RuntimeStartException extends Exception {

        RuntimeStartException(Throwable cause) {
            super(ProviderPlaywrightWorkerProtocol.FailureCode.RUNTIME_START_FAILED.name(), cause);
        }
    }
}
