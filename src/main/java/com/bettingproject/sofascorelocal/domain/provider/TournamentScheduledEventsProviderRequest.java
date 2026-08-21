package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Closed exact-host request for the tournament event-discovery endpoint.
 * Every URI component is derived from validated server-side values.
 */
public record TournamentScheduledEventsProviderRequest(
        URI providerOrigin,
        LocalDate date,
        long uniqueTournamentId) {

    public static final String EXPECTED_ORIGIN = "https://www.sofascore.com";

    public TournamentScheduledEventsProviderRequest {
        providerOrigin = Objects.requireNonNull(providerOrigin, "providerOrigin");
        date = Objects.requireNonNull(date, "date");
        ScheduledEventsProviderPageRequest.parseExactProviderOrigin(providerOrigin.toString());
        if (uniqueTournamentId < 1) {
            throw new IllegalArgumentException("uniqueTournamentId must be positive");
        }
    }

    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS;
    }

    public String requestKey() {
        return endpointType().name()
                + "|date=" + date
                + "|uniqueTournamentId=" + uniqueTournamentId;
    }

    public URI targetUri() {
        try {
            return new URI(
                    "https",
                    null,
                    "www.sofascore.com",
                    -1,
                    "/api/v1/unique-tournament/" + uniqueTournamentId
                            + "/scheduled-events/" + date,
                    null,
                    null);
        }
        catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "validated tournament scheduled-events request is not a valid URI",
                    exception);
        }
    }
}
