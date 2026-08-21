package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.Objects;

/**
 * One fully structured event returned by the unique-tournament scheduled-events
 * endpoint.
 *
 * <p>The phase tournament is kept on {@link #event()} while the enclosing unique
 * tournament is kept separately. This preserves both provider identities without
 * widening the historical {@link ScheduledEvent} model.</p>
 */
public record TournamentScheduledEventCandidate(
        ScheduledEvent event,
        ScheduledTournament uniqueTournament) {

    public TournamentScheduledEventCandidate {
        event = Objects.requireNonNull(event, "event");
        uniqueTournament = Objects.requireNonNull(uniqueTournament, "uniqueTournament");
        if (event.tournament().isEmpty()) {
            throw new IllegalArgumentException(
                    "A tournament scheduled-event candidate requires a phase tournament");
        }
    }

    public ScheduledTournament tournament() {
        return event.tournament().orElseThrow();
    }
}
