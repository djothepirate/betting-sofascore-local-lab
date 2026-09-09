package com.bettingproject.sofascorelocal.provider.playwright.worker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Correlates one exact top-level document request with its CDP response without
 * exposing the provider URI outside the isolated worker.
 */
final class ProviderMainDocumentNetworkObservation {

    private static final String DOCUMENT_RESOURCE_TYPE = "Document";

    private final String exactUri;
    private final String mainFrameId;
    private final Clock clock;

    private String requestId;
    private Instant requestedAt;
    private boolean responseObserved;
    private boolean cachedOrSynthetic;
    private boolean invalid;

    ProviderMainDocumentNetworkObservation(String exactUri, String mainFrameId) {
        this(exactUri, mainFrameId, Clock.systemUTC());
    }

    ProviderMainDocumentNetworkObservation(
            String exactUri,
            String mainFrameId,
            Clock clock) {
        this.exactUri = requireNonBlank(exactUri, "exactUri");
        this.mainFrameId = requireNonBlank(mainFrameId, "mainFrameId");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized void onRequestWillBeSent(JsonObject event) {
        JsonObject request = object(event, "request");
        String requestUri = string(request, "url");
        String method = string(request, "method");
        String frameId = string(event, "frameId");
        String resourceType = string(event, "type");

        if (!exactUri.equals(requestUri)
                || !"GET".equals(method)
                || !mainFrameId.equals(frameId)
                || !DOCUMENT_RESOURCE_TYPE.equals(resourceType)) {
            return;
        }
        if (nonNull(event, "redirectResponse") || requestId != null) {
            invalid = true;
            return;
        }
        if (!finiteNonNegativeNumber(event, "timestamp")
                || !finiteNonNegativeNumber(event, "wallTime")) {
            invalid = true;
            return;
        }

        String observedRequestId = string(event, "requestId");
        Instant observedAt;
        try {
            observedAt = clock.instant();
        }
        catch (RuntimeException exception) {
            invalid = true;
            return;
        }
        if (observedRequestId == null || observedRequestId.isBlank() || observedAt == null) {
            invalid = true;
            return;
        }
        requestId = observedRequestId;
        requestedAt = observedAt;
    }

    synchronized void onRequestServedFromCache(JsonObject event) {
        if (matchesObservedRequest(event)) {
            cachedOrSynthetic = true;
        }
    }

    synchronized void onResponseReceived(JsonObject event) {
        if (!matchesObservedRequest(event)) {
            return;
        }
        if (responseObserved) {
            invalid = true;
            return;
        }
        responseObserved = true;

        JsonObject response = object(event, "response");
        if (!mainFrameId.equals(string(event, "frameId"))
                || !DOCUMENT_RESOURCE_TYPE.equals(string(event, "type"))
                || response == null
                || !exactUri.equals(string(response, "url"))) {
            invalid = true;
            return;
        }
        if (booleanFlag(response, "fromDiskCache")
                || booleanFlag(response, "fromServiceWorker")
                || booleanFlag(response, "fromPrefetchCache")) {
            cachedOrSynthetic = true;
        }
    }

    synchronized void rejectNonNetworkResponse() {
        cachedOrSynthetic = true;
    }

    synchronized Instant requireNetworkStartedAt() {
        if (invalid
                || cachedOrSynthetic
                || requestId == null
                || requestedAt == null
                || !responseObserved) {
            throw new IllegalStateException(
                    "exact provider document network observation is incomplete");
        }
        return requestedAt;
    }

    synchronized Instant requestStartedAtIfObserved() {
        return invalid || cachedOrSynthetic ? null : requestedAt;
    }

    synchronized Instant requireRequestStartedAt(String networkId) {
        if (invalid || cachedOrSynthetic || requestedAt == null || requestId == null || !requestId.equals(networkId))
            throw new IllegalStateException("exact network request correlation unavailable");
        return requestedAt;
    }

    static String requireMainFrameId(JsonObject frameTreeResponse) {
        JsonObject frameTree = object(frameTreeResponse, "frameTree");
        JsonObject frame = object(frameTree, "frame");
        String frameId = string(frame, "id");
        if (frameId == null || frameId.isBlank()) {
            throw new IllegalStateException("main document frame identity is unavailable");
        }
        return frameId;
    }

    private boolean matchesObservedRequest(JsonObject event) {
        return requestId != null && requestId.equals(string(event, "requestId"));
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return null;
        }
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return null;
        }
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }

    private static boolean booleanFlag(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return false;
        }
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isBoolean() && primitive.getAsBoolean();
    }

    private static boolean finiteNonNegativeNumber(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return false;
        }
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return false;
        }
        try {
            double number = primitive.getAsDouble();
            return Double.isFinite(number) && number >= 0.0d;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean nonNull(JsonObject parent, String name) {
        if (parent == null || name == null) {
            return false;
        }
        JsonElement value = parent.get(name);
        return value != null && !value.isJsonNull();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
