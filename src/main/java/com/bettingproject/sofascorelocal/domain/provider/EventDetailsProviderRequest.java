package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase-1 J4 request whose allow-list is part of the compiled contract.
 */
public record EventDetailsProviderRequest(URI providerOrigin, long eventId) {

    public static final String EXPECTED_ORIGIN = "https://www.sofascore.com";
    public static final long SAINT_ETIENNE_CLERMONT_EVENT_ID = 16386245L;
    public static final long SEVILLA_RAYO_EVENT_ID = 16421052L;
    public static final List<Long> PHASE_1_EVENT_IDS = List.of(
            SAINT_ETIENNE_CLERMONT_EVENT_ID,
            SEVILLA_RAYO_EVENT_ID);
    private static final Set<Long> AUTHORIZED_EVENT_IDS = Set.copyOf(PHASE_1_EVENT_IDS);

    public EventDetailsProviderRequest {
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        requireExactProviderOrigin(providerOrigin);
        if (!AUTHORIZED_EVENT_IDS.contains(eventId)) {
            throw new IllegalArgumentException(
                    "eventId is not authorized by J4 real qualification phase 1");
        }
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
