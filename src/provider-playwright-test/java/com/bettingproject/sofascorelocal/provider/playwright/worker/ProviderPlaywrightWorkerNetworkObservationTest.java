package com.bettingproject.sofascorelocal.provider.playwright.worker;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderPlaywrightWorkerNetworkObservationTest {

    private static final String EXACT_URI =
            "https://www.sofascore.com/api/v1/event/16691018/statistics";
    private static final String MAIN_FRAME_ID = "main-frame";
    private static final String REQUEST_ID = "request-1";
    private static final Instant STARTED_AT = Instant.parse("2026-08-31T09:00:00.123Z");
    private static final Clock CLOCK = Clock.fixed(STARTED_AT, ZoneOffset.UTC);

    @Test
    void capturesOnlyTheExactTopLevelDocumentAndCorrelatesItsResponse() {
        ProviderMainDocumentNetworkObservation observation = observation();

        observation.onRequestWillBeSent(requestEvent(
                "https://www.sofascore.com/favicon.ico",
                "GET",
                MAIN_FRAME_ID,
                "Image",
                "unrelated"));
        observation.onRequestWillBeSent(requestEvent(
                EXACT_URI,
                "GET",
                "child-frame",
                "Document",
                "subframe"));
        observation.onRequestWillBeSent(exactRequestEvent());
        observation.onResponseReceived(exactResponseEvent());

        assertThat(observation.requireNetworkStartedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void rejectsASecondMatchingMainDocumentObservation() {
        ProviderMainDocumentNetworkObservation observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());
        observation.onRequestWillBeSent(requestEvent(
                EXACT_URI,
                "GET",
                MAIN_FRAME_ID,
                "Document",
                "request-2"));
        observation.onResponseReceived(exactResponseEvent());

        assertIncomplete(observation);
    }

    @Test
    void rejectsARedirectedMainDocumentObservation() {
        ProviderMainDocumentNetworkObservation observation = observation();
        JsonObject redirected = exactRequestEvent();
        redirected.add("redirectResponse", new JsonObject());

        observation.onRequestWillBeSent(redirected);
        observation.onResponseReceived(exactResponseEvent());

        assertIncomplete(observation);
    }

    @Test
    void rejectsMissingNonFiniteOrNegativeCdpTimes() {
        for (String field : new String[] {"timestamp", "wallTime"}) {
            JsonObject missing = exactRequestEvent();
            missing.remove(field);
            assertRejectedRequestEvent(missing);

            for (double value : new double[] {
                    Double.NaN, Double.POSITIVE_INFINITY, -1.0d}) {
                JsonObject invalid = exactRequestEvent();
                invalid.addProperty(field, value);
                assertRejectedRequestEvent(invalid);
            }

            JsonObject wrongType = exactRequestEvent();
            wrongType.addProperty(field, "not-a-cdp-time");
            assertRejectedRequestEvent(wrongType);
        }
    }

    @Test
    void rejectsAMatchingRequestWithoutACorrelatedResponse() {
        ProviderMainDocumentNetworkObservation observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());

        assertIncomplete(observation);
    }

    @Test
    void rejectsAResponseWithAnIncoherentDocumentIdentity() {
        ProviderMainDocumentNetworkObservation observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());
        observation.onResponseReceived(responseEvent(
                REQUEST_ID,
                "https://www.sofascore.com/api/v1/event/16691018/incidents",
                MAIN_FRAME_ID,
                "Document"));

        assertIncomplete(observation);
    }

    @Test
    void rejectsAnExplicitRequestServedFromCache() {
        ProviderMainDocumentNetworkObservation observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());
        JsonObject cached = new JsonObject();
        cached.addProperty("requestId", REQUEST_ID);
        observation.onRequestServedFromCache(cached);
        observation.onResponseReceived(exactResponseEvent());

        assertIncomplete(observation);
    }

    @Test
    void rejectsEveryCdpResponseCacheOrWorkerFlag() {
        for (String flag : new String[] {
                "fromDiskCache", "fromServiceWorker", "fromPrefetchCache"}) {
            ProviderMainDocumentNetworkObservation observation = observation();
            observation.onRequestWillBeSent(exactRequestEvent());
            JsonObject responseEvent = exactResponseEvent();
            responseEvent.getAsJsonObject("response").addProperty(flag, true);
            observation.onResponseReceived(responseEvent);

            assertIncomplete(observation);
        }
    }

    @Test
    void rejectsAPlaywrightServiceWorkerResponseEvenWhenCdpFlagsAreClear() {
        ProviderMainDocumentNetworkObservation observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());
        observation.onResponseReceived(exactResponseEvent());
        observation.rejectNonNetworkResponse();

        assertIncomplete(observation);
    }

    @Test
    void extractsOnlyAConcreteMainFrameIdentity() {
        JsonObject response = new JsonObject();
        JsonObject frameTree = new JsonObject();
        JsonObject frame = new JsonObject();
        frame.addProperty("id", MAIN_FRAME_ID);
        frameTree.add("frame", frame);
        response.add("frameTree", frameTree);

        assertThat(ProviderMainDocumentNetworkObservation.requireMainFrameId(response))
                .isEqualTo(MAIN_FRAME_ID);
        assertThatThrownBy(() ->
                ProviderMainDocumentNetworkObservation.requireMainFrameId(new JsonObject()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(EXACT_URI);
    }

    private static ProviderMainDocumentNetworkObservation observation() {
        return new ProviderMainDocumentNetworkObservation(EXACT_URI, MAIN_FRAME_ID, CLOCK);
    }

    @Test
    void terminalCompletionRequiresTheExactRequestAndARealNetworkResponse() {
        var observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());
        observation.onResponseReceived(exactResponseEvent());
        JsonObject unrelated = terminalEvent(); unrelated.addProperty("requestId", "other");
        observation.onLoadingFinished(unrelated);
        assertThat(observation.terminalObserved()).isFalse();
        observation.onLoadingFinished(terminalEvent());
        assertThat(observation.terminalProofIfObserved()).isEqualTo(
                new ProviderMainDocumentNetworkObservation.TerminalProof(STARTED_AT, 1));
        observation.onRequestServedFromCache(terminalEvent());
        assertThat(observation.terminalProofIfObserved()).isNull();
    }

    @Test
    void committedResponseWithoutTerminalNeverProvesEndEvenAfterCancellationWasRequested() {
        var observation = observation();
        observation.onRequestWillBeSent(exactRequestEvent());
        observation.onResponseReceived(exactResponseEvent());
        assertThat(observation.terminalProofIfObserved()).isNull();
        observation.beginCancellation();
        assertThat(observation.terminalObserved()).isFalse();
        assertThat(observation.terminalProofIfObserved()).isNull();
        observation.onLoadingFinished(terminalEvent());
        assertThat(observation.terminalProofIfObserved().reason()).isEqualTo(1);
    }

    @Test
    void abortedProofRequiresExplicitCancellationAndCorrelatedCanceledDocument() {
        for (boolean cancelRequested : new boolean[]{false, true}) {
            var observation = observation();
            observation.onRequestWillBeSent(exactRequestEvent());
            if (cancelRequested) observation.beginCancellation();
            observation.onLoadingFailed(terminalEvent());
            assertThat(observation.terminalObserved()).isTrue();
            if (cancelRequested) assertThat(observation.terminalProofIfObserved()).isEqualTo(
                    new ProviderMainDocumentNetworkObservation.TerminalProof(STARTED_AT, 2));
            else assertThat(observation.terminalProofIfObserved()).isNull();
        }
        for (String invalid : new String[]{"type", "canceled", "timestamp"}) {
            var observation = observation(); observation.onRequestWillBeSent(exactRequestEvent());
            observation.beginCancellation();
            JsonObject event = terminalEvent(); event.remove(invalid);
            observation.onLoadingFailed(event);
            assertThat(observation.terminalProofIfObserved()).isNull();
        }
    }

    @Test
    void rejectsTerminalDuplicatesAndRegressingOrMissingTimes() {
        for (double timestamp : new double[]{-1, 123_456.0, Double.NaN}) {
            var observation = observation(); observation.onRequestWillBeSent(exactRequestEvent());
            observation.onResponseReceived(exactResponseEvent());
            JsonObject event = terminalEvent(); event.addProperty("timestamp", timestamp);
            observation.onLoadingFinished(event);
            assertThat(observation.terminalProofIfObserved()).isNull();
        }
        var observation = observation(); observation.onRequestWillBeSent(exactRequestEvent());
        observation.onResponseReceived(exactResponseEvent());
        observation.onLoadingFinished(terminalEvent()); observation.onLoadingFinished(terminalEvent());
        assertThat(observation.terminalProofIfObserved()).isNull();
    }

    private static JsonObject terminalEvent() {
        JsonObject event = new JsonObject();
        event.addProperty("requestId", REQUEST_ID); event.addProperty("timestamp", 123_457.0);
        event.addProperty("type", "Document"); event.addProperty("canceled", true);
        return event;
    }

    private static JsonObject exactRequestEvent() {
        return requestEvent(EXACT_URI, "GET", MAIN_FRAME_ID, "Document", REQUEST_ID);
    }

    private static JsonObject requestEvent(
            String uri,
            String method,
            String frameId,
            String resourceType,
            String requestId) {
        JsonObject request = new JsonObject();
        request.addProperty("url", uri);
        request.addProperty("method", method);
        JsonObject event = new JsonObject();
        event.addProperty("requestId", requestId);
        event.addProperty("frameId", frameId);
        event.addProperty("type", resourceType);
        event.addProperty("timestamp", 123_456.789d);
        event.addProperty("wallTime", 1_788_173_200.123d);
        event.add("request", request);
        return event;
    }

    private static JsonObject exactResponseEvent() {
        return responseEvent(REQUEST_ID, EXACT_URI, MAIN_FRAME_ID, "Document");
    }

    private static JsonObject responseEvent(
            String requestId,
            String uri,
            String frameId,
            String resourceType) {
        JsonObject response = new JsonObject();
        response.addProperty("url", uri);
        response.addProperty("fromDiskCache", false);
        response.addProperty("fromServiceWorker", false);
        response.addProperty("fromPrefetchCache", false);
        JsonObject event = new JsonObject();
        event.addProperty("requestId", requestId);
        event.addProperty("frameId", frameId);
        event.addProperty("type", resourceType);
        event.add("response", response);
        return event;
    }

    private static void assertIncomplete(
            ProviderMainDocumentNetworkObservation observation) {
        assertThatThrownBy(observation::requireNetworkStartedAt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(EXACT_URI);
    }

    private static void assertRejectedRequestEvent(JsonObject event) {
        ProviderMainDocumentNetworkObservation observation = observation();
        observation.onRequestWillBeSent(event);
        observation.onResponseReceived(exactResponseEvent());
        assertIncomplete(observation);
    }
}
