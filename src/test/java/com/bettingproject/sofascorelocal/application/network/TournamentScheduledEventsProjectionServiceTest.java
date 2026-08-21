package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TournamentScheduledEventsProjectionServiceTest {

    private final TournamentScheduledEventsProjectionService service =
            new TournamentScheduledEventsProjectionService();

    @Test
    void filtersByPhaseAndTheHalfOpenEuropeParisDayThenVerifiesTheCount() {
        var selected = option(Map.of(7200, 2));
        var first = candidate(501, 119_880, "2026-08-18T19:00:00Z", 7, "finished");
        var atLocalStart = candidate(502, 119_880, "2026-08-17T22:00:00Z", 7, "notstarted");
        var atNextLocalStart = candidate(
                503, 119_880, "2026-08-18T22:00:00Z", 7, "notstarted");
        var anotherPhase = candidate(504, 998, "2026-08-18T18:00:00Z", 7, "finished");

        var result = service.project(
                LocalDate.of(2026, 8, 18),
                selected,
                List.of(first, atLocalStart, atNextLocalStart, anotherPhase));

        assertThat(result.events()).extracting(ScheduledEvent::providerEventId)
                .containsExactly(501L, 502L);
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
        assertThat(result.expectedCount()).hasValue(2);
        assertThat(result.excludedOutsideDateCount()).isEqualTo(1);
        assertThat(result.excludedOtherTournamentCount()).isEqualTo(1);
        assertThat(result.fromInclusive()).isEqualTo(Instant.parse("2026-08-17T22:00:00Z"));
        assertThat(result.toExclusive()).isEqualTo(Instant.parse("2026-08-18T22:00:00Z"));
    }

    @Test
    void treatsAnAbsentOffsetCountAsUnknownAndADstDayAsAmbiguous() {
        var noCount = service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of()),
                List.of(candidate(501, 119_880, "2026-08-18T19:00:00Z", 7, "finished")));
        var dstDay = service.project(
                LocalDate.of(2026, 3, 29),
                option(Map.of(3600, 1, 7200, 1)),
                List.of(candidate(502, 119_880, "2026-03-29T12:00:00Z", 7, "finished")));

        assertThat(noCount.countStatus())
                .isEqualTo(TournamentEventCountStatus.COUNT_NOT_VERIFIABLE);
        assertThat(noCount.expectedCount()).isEmpty();
        assertThat(dstDay.countStatus())
                .isEqualTo(TournamentEventCountStatus.COUNT_NOT_VERIFIABLE_DST_TRANSITION);
        assertThat(dstDay.expectedCount()).isEmpty();
    }

    @Test
    void exposesCountMismatchWithoutAuthorizingNormalization() {
        var result = service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of(7200, 2)),
                List.of(candidate(501, 119_880, "2026-08-18T19:00:00Z", 7, "finished")));

        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_MISMATCH);
        assertThat(result.expectedCount()).hasValue(2);
        assertThat(result.actualCount()).isEqualTo(1);
        assertThat(result.normalizationAllowed()).isFalse();
    }

    @Test
    void deduplicatesAnExactEventButRejectsAConflictingDuplicate() {
        var original = candidate(501, 119_880, "2026-08-18T19:00:00Z", 7, "finished");
        var exact = service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of(7200, 1)),
                List.of(original, original));

        assertThat(exact.events()).hasSize(1);
        assertThat(exact.exactDuplicateCount()).isEqualTo(1);

        var conflict = candidate(501, 119_880, "2026-08-18T19:00:00Z", 7, "notstarted");
        assertThatThrownBy(() -> service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of(7200, 1)),
                List.of(original, conflict)))
                .isInstanceOf(TournamentScheduledEventsProjectionException.class)
                .extracting(exception -> ((TournamentScheduledEventsProjectionException) exception)
                        .error())
                .isEqualTo(TournamentScheduledEventsProjectionError.CONFLICTING_EVENT_DUPLICATE);
    }

    @Test
    void rejectsAnyEventFromAnotherUniqueTournament() {
        assertThatThrownBy(() -> service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of()),
                List.of(candidate(501, 119_880, "2026-08-18T19:00:00Z", 8, "finished"))))
                .isInstanceOf(TournamentScheduledEventsProjectionException.class)
                .extracting(exception -> ((TournamentScheduledEventsProjectionException) exception)
                        .error())
                .isEqualTo(TournamentScheduledEventsProjectionError.UNIQUE_TOURNAMENT_ID_MISMATCH);
    }

    @Test
    void acceptsLocalizedTournamentNamesWhenBothNumericIdentitiesMatch() {
        var localized = candidate(
                501,
                119_880,
                "2026-08-18T19:00:00Z",
                7,
                "finished",
                "Ligue des champions, barrages",
                "Ligue des champions");

        var result = service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of(7200, 1)),
                List.of(localized));

        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
        assertThat(result.events())
                .singleElement()
                .satisfies(event -> assertThat(event.tournament().orElseThrow().name())
                        .isEqualTo("Ligue des champions, barrages"));
    }

    @Test
    void deduplicatesLocalizedTournamentMetadataWhenNumericIdentitiesMatch() {
        var providerEnglish = candidate(
                501,
                119_880,
                "2026-08-18T19:00:00Z",
                7,
                "finished",
                "Champions League, Playoff Round",
                "Champions League");
        var providerFrench = candidate(
                501,
                119_880,
                "2026-08-18T19:00:00Z",
                7,
                "finished",
                "Ligue des champions, barrages",
                "Ligue des champions");

        var result = service.project(
                LocalDate.of(2026, 8, 18),
                option(Map.of(7200, 1)),
                List.of(providerEnglish, providerFrench));

        assertThat(result.events()).hasSize(1);
        assertThat(result.exactDuplicateCount()).isEqualTo(1);
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
    }

    private static J3TournamentCatalogOption option(Map<Integer, Integer> counts) {
        return new J3TournamentCatalogOption(
                119_880,
                "UEFA Champions League, Playoff Round",
                "Europe",
                7,
                "UEFA Champions League",
                counts,
                List.of(41L));
    }

    private static TournamentScheduledEventCandidate candidate(
            long eventId,
            long tournamentId,
            String startsAt,
            long uniqueTournamentId,
            String status) {
        return candidate(
                eventId,
                tournamentId,
                startsAt,
                uniqueTournamentId,
                status,
                tournamentId == 119_880
                        ? "UEFA Champions League, Playoff Round"
                        : "Another phase",
                "UEFA Champions League");
    }

    private static TournamentScheduledEventCandidate candidate(
            long eventId,
            long tournamentId,
            String startsAt,
            long uniqueTournamentId,
            String status,
            String tournamentName,
            String uniqueTournamentName) {
        ScheduledTournament tournament = new ScheduledTournament(tournamentId, tournamentName);
        return new TournamentScheduledEventCandidate(
                new ScheduledEvent(
                        eventId,
                        Instant.parse(startsAt),
                        new ScheduledTeam(10, "Home"),
                        new ScheduledTeam(20, "Away"),
                        new ScheduledEventStatus(status, Optional.empty()),
                        Optional.of(tournament)),
                new ScheduledTournament(uniqueTournamentId, uniqueTournamentName));
    }
}
