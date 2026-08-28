package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.LocalDate;
import java.util.Objects;

public record PlaywrightProviderRequest(
        SofascoreEndpointType endpoint,
        LocalDate date,
        int page,
        long uniqueTournamentId,
        long eventId) {

    public PlaywrightProviderRequest {
        Objects.requireNonNull(endpoint, "endpoint");
        switch (endpoint) {
            case SCHEDULED_EVENTS -> {
                if (date == null
                        || page < 1
                        || page > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE
                        || uniqueTournamentId != 0
                        || eventId != 0) {
                    throw new IllegalArgumentException("invalid scheduled-events request");
                }
            }
            case TOURNAMENT_SCHEDULED_EVENTS -> {
                if (date == null || page != 0 || uniqueTournamentId < 1 || eventId != 0) {
                    throw new IllegalArgumentException(
                            "invalid tournament scheduled-events request");
                }
            }
            case EVENT_DETAILS -> {
                if (date != null
                        || page != 0
                        || uniqueTournamentId != 0
                        || eventId < 1
                        || eventId > EventDetailsProviderRequest.MAXIMUM_PARAMETERIZED_EVENT_ID) {
                    throw new IllegalArgumentException("invalid event-details request");
                }
            }
            default -> throw new IllegalArgumentException(
                    "endpoint is not implemented by the Playwright worker");
        }
    }

    public static PlaywrightProviderRequest scheduledEvents(LocalDate date, int page) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.SCHEDULED_EVENTS, date, page, 0, 0);
    }

    public static PlaywrightProviderRequest tournamentScheduledEvents(
            LocalDate date,
            long uniqueTournamentId) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                date,
                0,
                uniqueTournamentId,
                0);
    }

    public static PlaywrightProviderRequest eventDetails(long eventId) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_DETAILS, null, 0, 0, eventId);
    }
}
