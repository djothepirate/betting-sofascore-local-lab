package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.LocalDate;
import java.util.Objects;

public record PlaywrightProviderRequest(
        SofascoreEndpointType endpoint,
        LocalDate date,
        int page,
        long uniqueTournamentId) {

    public PlaywrightProviderRequest {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(date, "date");
        switch (endpoint) {
            case SCHEDULED_EVENTS -> {
                if (page < 1
                        || page > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE
                        || uniqueTournamentId != 0) {
                    throw new IllegalArgumentException("invalid scheduled-events request");
                }
            }
            case TOURNAMENT_SCHEDULED_EVENTS -> {
                if (page != 0 || uniqueTournamentId < 1) {
                    throw new IllegalArgumentException(
                            "invalid tournament scheduled-events request");
                }
            }
            default -> throw new IllegalArgumentException(
                    "endpoint is not implemented by the J3 Playwright worker");
        }
    }

    public static PlaywrightProviderRequest scheduledEvents(LocalDate date, int page) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.SCHEDULED_EVENTS, date, page, 0);
    }

    public static PlaywrightProviderRequest tournamentScheduledEvents(
            LocalDate date,
            long uniqueTournamentId) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
                date,
                0,
                uniqueTournamentId);
    }
}
