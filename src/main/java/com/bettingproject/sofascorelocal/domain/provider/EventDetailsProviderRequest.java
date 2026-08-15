package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exact-host J4 request. Construction is restricted to one of the two explicit
 * qualification scopes so a phase-1 caller cannot accidentally create a
 * parameterized request.
 */
public final class EventDetailsProviderRequest {

    public static final String EXPECTED_ORIGIN = "https://www.sofascore.com";
    public static final long SAINT_ETIENNE_CLERMONT_EVENT_ID = 16386245L;
    public static final long SEVILLA_RAYO_EVENT_ID = 16421052L;
    public static final List<Long> PHASE_1_EVENT_IDS = List.of(
            SAINT_ETIENNE_CLERMONT_EVENT_ID,
            SEVILLA_RAYO_EVENT_ID);
    public static final long MAXIMUM_PARAMETERIZED_EVENT_ID = 999_999_999L;
    private static final Set<Long> PHASE_1_AUTHORIZED_EVENT_IDS = Set.copyOf(PHASE_1_EVENT_IDS);

    private final URI providerOrigin;
    private final long eventId;

    private EventDetailsProviderRequest(URI providerOrigin, long eventId) {
        this.providerOrigin = Objects.requireNonNull(providerOrigin, "providerOrigin");
        requireExactProviderOrigin(providerOrigin);
        this.eventId = eventId;
    }

    public static EventDetailsProviderRequest phase1(URI providerOrigin, long eventId) {
        if (!PHASE_1_AUTHORIZED_EVENT_IDS.contains(eventId)) {
            throw new IllegalArgumentException(
                    "eventId is not authorized by J4 real qualification phase 1");
        }
        return new EventDetailsProviderRequest(providerOrigin, eventId);
    }

    public static EventDetailsProviderRequest phase2(URI providerOrigin, long eventId) {
        requirePhase2EventId(eventId);
        return new EventDetailsProviderRequest(providerOrigin, eventId);
    }

    public static long requirePhase2EventId(long eventId) {
        if (eventId < 1 || eventId > MAXIMUM_PARAMETERIZED_EVENT_ID) {
            throw new IllegalArgumentException(
                    "phase-2 eventId must be between 1 and " + MAXIMUM_PARAMETERIZED_EVENT_ID);
        }
        return eventId;
    }

    public URI providerOrigin() {
        return providerOrigin;
    }

    public long eventId() {
        return eventId;
    }

    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.EVENT_DETAILS;
    }

    public String requestKey() {
        return endpointType().name() + "|eventId=" + eventId;
    }

    public URI targetUri() {
        try {
            return new URI(
                    "https",
                    null,
                    "www.sofascore.com",
                    -1,
                    "/api/v1/event/" + eventId,
                    null,
                    null);
        }
        catch (URISyntaxException exception) {
            throw new IllegalStateException("authorized event request is not a valid URI", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventDetailsProviderRequest that)) {
            return false;
        }
        return eventId == that.eventId && providerOrigin.equals(that.providerOrigin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerOrigin, eventId);
    }

    @Override
    public String toString() {
        return "EventDetailsProviderRequest[providerOrigin=" + providerOrigin
                + ", eventId=" + eventId + "]";
    }

    public static URI parseExactProviderOrigin(String value) {
        Objects.requireNonNull(value, "value");
        URI origin;
        try {
            origin = URI.create(value.trim());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("provider origin is not a valid URI", exception);
        }
        requireExactProviderOrigin(origin);
        return origin;
    }

    private static void requireExactProviderOrigin(URI origin) {
        boolean rootPath = origin.getRawPath() == null
                || origin.getRawPath().isEmpty()
                || origin.getRawPath().equals("/");
        if (!origin.isAbsolute()
                || !"https".equals(origin.getScheme())
                || !"www.sofascore.com".equals(origin.getHost())
                || origin.getPort() != -1
                || origin.getRawUserInfo() != null
                || !rootPath
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "providerOrigin must be exactly " + EXPECTED_ORIGIN);
        }
    }
}
