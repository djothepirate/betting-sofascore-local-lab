package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record PlaywrightProviderRequest(
        SofascoreEndpointType endpoint,
        LocalDate date,
        int page,
        long uniqueTournamentId,
        long eventId,
        Optional<PlaywrightProviderEntityTag> ifNoneMatch) {

    public PlaywrightProviderRequest(
            SofascoreEndpointType endpoint,
            LocalDate date,
            int page,
            long uniqueTournamentId,
            long eventId) {
        this(endpoint, date, page, uniqueTournamentId, eventId, Optional.empty());
    }

    public PlaywrightProviderRequest {
        Objects.requireNonNull(endpoint, "endpoint");
        ifNoneMatch = Objects.requireNonNull(ifNoneMatch, "ifNoneMatch");
        if (ifNoneMatch.isPresent() && !isEventEndpoint(endpoint)) {
            throw new IllegalArgumentException(
                    "conditional validator is only permitted for event endpoints");
        }
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
            case EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS -> {
                if (date != null
                        || page != 0
                        || uniqueTournamentId != 0
                        || eventId < 1
                        || eventId > J5EventDataProviderRequest.MAXIMUM_EVENT_ID) {
                    throw new IllegalArgumentException("invalid J5 event-data request");
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

    public static PlaywrightProviderRequest eventDetails(
            long eventId,
            PlaywrightProviderEntityTag ifNoneMatch) {
        return eventDetails(eventId).withIfNoneMatch(ifNoneMatch);
    }

    public static PlaywrightProviderRequest eventStatistics(long eventId) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_STATISTICS, null, 0, 0, eventId);
    }

    public static PlaywrightProviderRequest eventStatistics(
            long eventId,
            PlaywrightProviderEntityTag ifNoneMatch) {
        return eventStatistics(eventId).withIfNoneMatch(ifNoneMatch);
    }

    public static PlaywrightProviderRequest eventIncidents(long eventId) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_INCIDENTS, null, 0, 0, eventId);
    }

    public static PlaywrightProviderRequest eventIncidents(
            long eventId,
            PlaywrightProviderEntityTag ifNoneMatch) {
        return eventIncidents(eventId).withIfNoneMatch(ifNoneMatch);
    }

    public static PlaywrightProviderRequest eventLineups(long eventId) {
        return new PlaywrightProviderRequest(
                SofascoreEndpointType.EVENT_LINEUPS, null, 0, 0, eventId);
    }

    public static PlaywrightProviderRequest eventLineups(
            long eventId,
            PlaywrightProviderEntityTag ifNoneMatch) {
        return eventLineups(eventId).withIfNoneMatch(ifNoneMatch);
    }

    public PlaywrightProviderRequest withIfNoneMatch(PlaywrightProviderEntityTag entityTag) {
        return new PlaywrightProviderRequest(
                endpoint,
                date,
                page,
                uniqueTournamentId,
                eventId,
                Optional.of(Objects.requireNonNull(entityTag, "entityTag")));
    }

    private static boolean isEventEndpoint(SofascoreEndpointType endpoint) {
        return endpoint == SofascoreEndpointType.EVENT_DETAILS
                || endpoint == SofascoreEndpointType.EVENT_STATISTICS
                || endpoint == SofascoreEndpointType.EVENT_INCIDENTS
                || endpoint == SofascoreEndpointType.EVENT_LINEUPS;
    }
}
