package com.bettingproject.sofascorelocal.domain.scheduledevents;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TournamentScheduledEventCandidateTest {

    @Test
    void exposesThePhaseTournamentWithoutConflatingTheUniqueTournament() {
        ScheduledTournament phase = new ScheduledTournament(119880L, "Playoff Round");
        ScheduledTournament uniqueTournament = new ScheduledTournament(7L, "Champions League");
        TournamentScheduledEventCandidate candidate = new TournamentScheduledEventCandidate(
                event(Optional.of(phase)),
                uniqueTournament);

        assertThat(candidate.tournament()).isSameAs(phase);
        assertThat(candidate.uniqueTournament()).isSameAs(uniqueTournament);
    }

    @Test
    void refusesAnEventThatDoesNotCarryItsPhaseTournament() {
        ScheduledTournament uniqueTournament = new ScheduledTournament(7L, "Champions League");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TournamentScheduledEventCandidate(
                        event(Optional.empty()),
                        uniqueTournament))
                .withMessageContaining("phase tournament");
    }

    private static ScheduledEvent event(Optional<ScheduledTournament> tournament) {
        return new ScheduledEvent(
                16707704L,
                Instant.parse("2026-08-18T19:00:00Z"),
                new ScheduledTeam(11L, "Home"),
                new ScheduledTeam(12L, "Away"),
                new ScheduledEventStatus("notstarted", Optional.empty()),
                tournament);
    }
}
