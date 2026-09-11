package com.bettingproject.sofascorelocal.provider.playwright.worker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPlaywrightWorkerSecurityContractTest {

    @Test
    void workerSourceKeepsTheNonPersistentSilentBrowserContract() throws Exception {
        String source = Files.readString(Path.of(
                "src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/"
                        + "playwright/worker/ProviderPlaywrightWorkerMain.java"));

        assertThat(source)
                .contains("browser.newContext(")
                .contains(".setAcceptDownloads(false)")
                .contains(".setJavaScriptEnabled(false)")
                .contains(".setServiceWorkers(ServiceWorkerPolicy.BLOCK)")
                .contains("context.clearCookies()")
                .contains("exactIfNoneMatch.set(null)")
                .contains("liveV9 && request.ifNoneMatch() != null")
                .contains("exactNavigationAdmission.compareAndSet(true, false)")
                .contains("route.resume()")
                .contains("new Route.ResumeOptions().setHeaders(resumedHeaders)")
                .contains("response.status()")
                .contains("status == 304 && acceptsNotModifiedResponse")
                .contains("captureNotModifiedResponse")
                .contains("completeNotModifiedExchange")
                .contains("response.headerValue(\"content-type\")")
                .contains("response.headerValue(\"etag\")")
                .contains("response.body()")
                .contains("networkObserver.send(\"Page.getFrameTree\")")
                .contains("\"Network.requestWillBeSent\"")
                .contains("\"Network.requestServedFromCache\"")
                .contains("\"Network.responseReceived\"")
                .contains("networkObserver.send(\"Network.enable\")")
                .contains("Network.setCacheDisabled")
                .contains(".requireNetworkStartedAt()")
                .contains("response.fromServiceWorker()")
                .contains("responseGuard.send(\"Fetch.enable\", responseStageOnly())")
                .contains("session.send(\"Fetch.failRequest\", command)")
                .contains("session.send(\"Fetch.continueResponse\", command)")
                .doesNotContain(
                        "APIResponse",
                        "route.fetch(",
                        "route.fulfill(",
                        "Fetch.getResponseBody",
                        "page.content(",
                        "response.text(",
                        "launchPersistentContext",
                        "setStorageState",
                        "storageState()",
                        "recordHar",
                        "recordVideo",
                        ".tracing(",
                        ".screenshot(",
                        "setDownloadsPath",
                        "setProxy",
                        "setUserAgent",
                        "addCookies",
                        "long requestedEpochMillis = Instant.now().toEpochMilli()",
                        "System.out",
                        "System.err",
                        "FlareSolverr");

        int handshake = source.indexOf("writeHandshake(output, configuration.token())");
        int startGate = source.indexOf("requireStart(input)");
        int runtime = source.indexOf("WorkerRuntime.open()");
        int ready = source.indexOf("writeReady(output)");
        int routeHandler = source.indexOf("private void handleRoute(Route route)");
        int redirectedRequest = source.indexOf(
                "route.request().redirectedFrom() != null", routeHandler);
        int exactUriCheck = source.indexOf("expectedUri == null", routeHandler);
        int singleAdmissionCheck = source.indexOf(
                "exactNavigationAdmission.compareAndSet(true, false)", routeHandler);
        int networkEnable = source.indexOf("networkObserver.send(\"Network.enable\")");
        int fetchEnable = source.indexOf(
                "responseGuard.send(\"Fetch.enable\", responseStageOnly())");
        int navigation = source.indexOf("page.navigate(exactUri");
        assertThat(handshake).isNotNegative();
        assertThat(startGate).isGreaterThan(handshake);
        assertThat(runtime).isGreaterThan(startGate);
        assertThat(ready).isGreaterThan(runtime);
        assertThat(routeHandler).isNotNegative();
        assertThat(redirectedRequest).isNotNegative();
        assertThat(exactUriCheck).isGreaterThan(redirectedRequest);
        assertThat(singleAdmissionCheck).isGreaterThan(exactUriCheck);
        assertThat(networkEnable).isNotNegative();
        assertThat(fetchEnable).isGreaterThan(networkEnable);
        assertThat(navigation).isGreaterThan(fetchEnable);
    }
}
