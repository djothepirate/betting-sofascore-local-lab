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
            if (command != ProviderPlaywrightWorkerProtocol.GET) {
                ProviderPlaywrightWorkerProtocol.writeFailure(
                        output, ProviderPlaywrightWorkerProtocol.FailureCode.PROTOCOL_ERROR);
                return 65;
            }

            ProviderPlaywrightWorkerProtocol.GetCommand request;
            try {
                request = ProviderPlaywrightWorkerProtocol.readGetCommand(input);
            }
            catch (ProviderPlaywrightWorkerProtocol.ProtocolValidationException exception) {
                ProviderPlaywrightWorkerProtocol.writeFailure(output, exception.failureCode());
                return 65;
            }

            ExecutionResult result = runtime.execute(
                    configuration.uriFor(request).toASCIIString(), request.timeoutMillis());
            if (result.failure() != null) {
                ProviderPlaywrightWorkerProtocol.writeFailure(output, result.failure());
                return 66;
            }
            ProviderPlaywrightWorkerProtocol.writeResponse(output, result.response());
        }
    }

    private record ExecutionResult(
            ProviderPlaywrightWorkerProtocol.ResponseFrame response,
            ProviderPlaywrightWorkerProtocol.FailureCode failure) {

        static ExecutionResult success(ProviderPlaywrightWorkerProtocol.ResponseFrame response) {
            return new ExecutionResult(response, null);
        }

        static ExecutionResult failure(ProviderPlaywrightWorkerProtocol.FailureCode failure) {
            return new ExecutionResult(null, failure);
        }
    }

    private static final class WorkerRuntime implements AutoCloseable {

        private final Playwright playwright;
        private final Browser browser;
        private final BrowserContext context;
        private final AtomicReference<String> exactAllowedUri = new AtomicReference<>();
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

        ExecutionResult execute(String exactUri, int timeoutMillis) {
            if (closed.get()) {
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
            }
            exactAllowedUri.set(exactUri);
            routeFailure.set(null);
            exactNavigationAdmission.set(true);
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
            try {
                networkObserver = context.newCDPSession(page);
                String mainFrameId = ProviderMainDocumentNetworkObservation.requireMainFrameId(
                        networkObserver.send("Page.getFrameTree"));
                ProviderMainDocumentNetworkObservation networkObservation =
                        new ProviderMainDocumentNetworkObservation(exactUri, mainFrameId);
                networkObserver.on(
                        "Network.requestWillBeSent",
                        networkObservation::onRequestWillBeSent);
                networkObserver.on(
                        "Network.requestServedFromCache",
                        networkObservation::onRequestServedFromCache);
                networkObserver.on(
                        "Network.responseReceived",
                        networkObservation::onResponseReceived);
                networkObserver.send("Network.enable");

                responseGuard = context.newCDPSession(page);
                CDPSession activeResponseGuard = responseGuard;
                responseGuard.on("Fetch.requestPaused", event ->
                        handlePausedResponse(activeResponseGuard, event));
                responseGuard.send("Fetch.enable", responseStageOnly());
                Response response = page.navigate(exactUri, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.COMMIT)
                        .setTimeout((double) timeoutMillis));
                ProviderPlaywrightWorkerProtocol.FailureCode failure = routeFailure.get();
                if (failure != null) {
                    return ExecutionResult.failure(failure);
                }
                if (response == null || !exactUri.equals(response.url())) {
                    return ExecutionResult.failure(
                            ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                }
                if (response.status() >= 300 && response.status() < 400) {
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
                try {
                    body = response.body();
                }
                catch (PlaywrightException exception) {
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
                    long receivedEpochMillis = Instant.now().toEpochMilli();
                    if (receivedEpochMillis < requestedEpochMillis) {
                        return ExecutionResult.failure(
                                ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE);
                    }
                    return ExecutionResult.success(new ProviderPlaywrightWorkerProtocol.ResponseFrame(
                            requestedEpochMillis,
                            receivedEpochMillis,
                            response.status(),
                            contentType,
                            body));
                }
                finally {
                    Arrays.fill(body, (byte) 0);
                }
            }
            catch (TimeoutError exception) {
                return ExecutionResult.failure(ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT);
            }
            catch (PlaywrightException exception) {
                ProviderPlaywrightWorkerProtocol.FailureCode failure = routeFailure.get();
                if (failure == null && isTimeout(exception)) {
                    failure = ProviderPlaywrightWorkerProtocol.FailureCode.TIMEOUT;
                }
                return ExecutionResult.failure(failure == null
                        ? ProviderPlaywrightWorkerProtocol.FailureCode.PLAYWRIGHT_FAILURE
                        : failure);
            }
            finally {
                exactAllowedUri.set(null);
                exactNavigationAdmission.set(false);
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

        private void handlePausedResponse(CDPSession session, JsonObject event) {
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
                        || !"GET".equals(requestMethod)) {
                    routeFailure.compareAndSet(null,
                            ProviderPlaywrightWorkerProtocol.FailureCode.UNEXPECTED_ROUTE);
                    command.addProperty("errorReason", "Aborted");
                    session.send("Fetch.failRequest", command);
                    return;
                }
                if (responseStatus >= 300 && responseStatus < 400) {
                    routeFailure.compareAndSet(null,
                            ProviderPlaywrightWorkerProtocol.FailureCode.REDIRECT_BLOCKED);
                    command.addProperty("errorReason", "Aborted");
                    session.send("Fetch.failRequest", command);
                    return;
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
            if (containsSensitiveRequestHeader(route.request().allHeaders())) {
                routeFailure.compareAndSet(null,
                        ProviderPlaywrightWorkerProtocol.FailureCode.SENSITIVE_REQUEST_BLOCKED);
                route.abort();
                return;
            }

            try {
                route.resume();
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
        }

        private static boolean containsSensitiveRequestHeader(Map<String, String> headers) {
            return headers.keySet().stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.equals("authorization")
                            || name.equals("cookie")
                            || name.equals("proxy-authorization"));
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
