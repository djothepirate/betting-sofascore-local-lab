package com.bettingproject.sofascorelocal.domain.eventdetails;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDetailsDisplayScoreTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-08T09:00:00Z");

    @Test
    void historicalConstructorLeavesNewFieldsAbsentAndKeepsTheScheduledEventContract() {
        var historical = historical();
        assertThat(historical.isAwarded()).isEmpty();
        assertThat(historical.homeDisplayScore()).isEmpty();
        assertThat(historical.awayDisplayScore()).isEmpty();
        assertThat(extended(Optional.of(true), Optional.of(0), Optional.of(999)).asScheduledEvent())
                .isEqualTo(historical.asScheduledEvent());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsOutOfRangeScoresOnEitherSide(int score) {
        assertThatThrownBy(() -> extended(Optional.empty(), Optional.of(score), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("homeDisplayScore");
        assertThatThrownBy(() -> extended(Optional.empty(), Optional.empty(), Optional.of(score)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("awayDisplayScore");
    }

    @Test
    void optionalContainersCannotBeNull() {
        assertThatThrownBy(() -> extended(null, Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class).hasMessage("isAwarded");
        assertThatThrownBy(() -> extended(Optional.empty(), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class).hasMessage("homeDisplayScore");
        assertThatThrownBy(() -> extended(Optional.empty(), Optional.empty(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("awayDisplayScore");
    }

    @ParameterizedTest
    @ValueSource(strings = {"event-details-v1", "event-details-v2"})
    void preservesTheHistoricalHashExactlyAndDisallowsUnhashedNewFields(String version) {
        // Golden bytes from the unchanged V1 detail format, including venue/city/season/round.
        assertThat(hash(historical(), version)).isEqualTo(
                "2906e105626414d6aa35fdaa9dd255a0090eff417775fa1e12c94ce1477bae33");
        for (EventDetails details : List.of(
                extended(Optional.of(false), Optional.empty(), Optional.empty()),
                extended(Optional.empty(), Optional.of(0), Optional.empty()),
                extended(Optional.empty(), Optional.empty(), Optional.of(0)))) {
            assertThatThrownBy(() -> hash(details, version))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("event-details-v3 provenance");
        }
    }

    @Test
    void versionedHashSeparatesAbsenceFalseZeroAndBothSidesAndIsStableAcrossSources() {
        List<EventDetails> variants = List.of(
                historical(),
                extended(Optional.of(false), Optional.empty(), Optional.empty()),
                extended(Optional.of(true), Optional.empty(), Optional.empty()),
                extended(Optional.empty(), Optional.of(0), Optional.empty()),
                extended(Optional.empty(), Optional.empty(), Optional.of(0)),
                extended(Optional.empty(), Optional.of(0), Optional.of(0)),
                extended(Optional.empty(), Optional.of(1), Optional.of(0)),
                extended(Optional.empty(), Optional.of(0), Optional.of(1)));
        var hashes = variants.stream().map(details -> hash(details, "event-details-v3")).toList();
        assertThat(hashes).doesNotHaveDuplicates();
        assertThat(hashes.getFirst()).isNotEqualTo(hash(historical(), "event-details-v2"));
        EventDetails scored = variants.getLast();
        var laterSource = EventSourceTrace.providerSnapshot(399, "b".repeat(64),
                "event-details-v3", RECEIVED_AT.plusSeconds(90));
        assertThat(EventDetailObservation.from(CanonicalEventIdentity.sofascore(38001), scored, laterSource)
                .normalizedSha256()).isEqualTo(hashes.getLast());
    }

    private static String hash(EventDetails details, String version) {
        return EventDetailObservation.from(CanonicalEventIdentity.sofascore(38001), details,
                EventSourceTrace.providerSnapshot(381, "a".repeat(64), version, RECEIVED_AT))
                .normalizedSha256();
    }

    private static EventDetails historical() {
        return new EventDetails(38001, Instant.ofEpochSecond(1786793400),
                new ScheduledTeam(1, "Home"), new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("finished", Optional.of("Ended")),
                Optional.of(new ScheduledTournament(3, "League")),
                Optional.of(new EventVenue(4, "Venue", Optional.of("City"))),
                Optional.of(new EventSeason(5, "Season")), Optional.of("1"));
    }

    private static EventDetails extended(Optional<Boolean> awarded, Optional<Integer> home, Optional<Integer> away) {
        EventDetails details = historical();
        return new EventDetails(details.providerEventId(), details.startsAt(), details.homeTeam(), details.awayTeam(),
                details.status(), details.tournament(), details.venue(), details.season(), details.round(),
                awarded, home, away);
    }
}
