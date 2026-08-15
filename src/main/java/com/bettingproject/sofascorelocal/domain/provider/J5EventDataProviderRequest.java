package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exact-host, exact-endpoint request for one guarded J5 campaign. */
public record J5EventDataProviderRequest(
        URI providerOrigin,
        long eventId,
        SofascoreEndpointType endpointType) {

    public static final long MAXIMUM_EVENT_ID = 999_999_999L;
    public static final Set<SofascoreEndpointType> ALLOWED_ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);
    private static final Map<SofascoreEndpointType, String> PATH_SUFFIXES = Map.of(
            SofascoreEndpointType.EVENT_STATISTICS, "statistics",
            SofascoreEndpointType.EVENT_INCIDENTS, "incidents",
            SofascoreEndpointType.EVENT_LINEUPS, "lineups");

    public J5EventDataProviderRequest {
        providerOrigin = Objects.requireNonNull(providerOrigin, "providerOrigin");
        EventDetailsProviderRequest.parseExactProviderOrigin(providerOrigin.toString());
        requireEventId(eventId);
        endpointType = Objects.requireNonNull(endpointType, "endpointType");
        if (!ALLOWED_ENDPOINTS.contains(endpointType)) {
            throw new IllegalArgumentException("J5 request endpoint is not authorized");
        }
    }

    public static long requireEventId(long eventId) {
        if (eventId < 1 || eventId > MAXIMUM_EVENT_ID) {
            throw new IllegalArgumentException(
                    "J5 eventId must be between 1 and " + MAXIMUM_EVENT_ID);
        }
        return eventId;
    }

    public String requestKey() {
        return endpointType.name() + "|eventId=" + eventId;
    }

    public URI targetUri() {
        try {
            return new URI(
                    "https",
                    null,
                    "www.sofascore.com",
                    -1,
                    "/api/v1/event/" + eventId + "/" + PATH_SUFFIXES.get(endpointType),
                    null,
                    null);
        }
        catch (URISyntaxException exception) {
            throw new IllegalStateException("authorized J5 request is not a valid URI", exception);
        }
    }
}
