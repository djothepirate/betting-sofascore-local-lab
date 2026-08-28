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
                .contains("exactNavigationAdmission.compareAndSet(true, false)")
                .contains("route.resume()")
                .contains("response.status()")
                .contains("response.headerValue(\"content-type\")")
                .contains("response.body()")
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
        assertThat(handshake).isNotNegative();
        assertThat(startGate).isGreaterThan(handshake);
        assertThat(runtime).isGreaterThan(startGate);
        assertThat(ready).isGreaterThan(runtime);
        assertThat(routeHandler).isNotNegative();
        assertThat(redirectedRequest).isNotNegative();
        assertThat(exactUriCheck).isGreaterThan(redirectedRequest);
        assertThat(singleAdmissionCheck).isGreaterThan(exactUriCheck);
    }
}
