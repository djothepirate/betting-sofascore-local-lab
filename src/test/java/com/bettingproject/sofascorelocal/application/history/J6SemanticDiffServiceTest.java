package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.history.J6ChangeKind;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class J6SemanticDiffServiceTest {

    private static final long PROVIDER_EVENT_ID = 9_600_001L;
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(PROVIDER_EVENT_ID);
    private static final J6SemanticDiffService SERVICE = new J6SemanticDiffService();

    @Test
    void comparesOnlyTheJ6StateContract() {
        CanonicalEventObservationView before = state(
                1,
                "2026-08-20T18:00:00Z",
                "notstarted",
                "Home",
                "Competition A");
        CanonicalEventObservationView after = state(
                2,
                "2026-08-20T19:00:00Z",
                "inprogress",
                "Home renamed",
                "Competition B");

        var changes = SERVICE.compareState(before, after);

        assertThat(changes)
                .extracting(change -> change.field())
                .containsExactly(
                        "startsAt",
                        "homeTeam.name",
                        "status.type",
                        "tournament.name");
        assertThat(changes).allSatisfy(change ->
                assertThat(change.kind()).isEqualTo(J6ChangeKind.CHANGED));
    }

    @Test
    void preservesLegacyDetailChangesWithoutInventingAbsentAwardedFields() {
        EventDetailObservationView before = details(
                10,
                Optional.of(new EventVenue(10, "Old stadium", Optional.empty())),
                Optional.empty(),
                Optional.of("Round 1"));
        EventDetailObservationView after = details(
                11,
                Optional.of(new EventVenue(11, "New stadium", Optional.of("Paris"))),
                Optional.of(new EventSeason(44, "2026/27")),
                Optional.empty());

        var changes = SERVICE.compareDetails(before, after);

        assertThat(changes)
                .extracting(change -> change.field())
                .containsExactly(
                        "venue.providerId",
                        "venue.name",
                        "venue.city",
                        "season.providerId",
                        "season.name",
                        "round")
                .doesNotContain("startsAt", "status.type", "homeTeam.name");
    }

    @Test
    void distinguishesAbsentAwardedFieldsFromExplicitFalseAndZeroScores() {
        var before = awardedDetails(12, null, null, null);
        var after = awardedDetails(13, false, 0, 0);

        assertThat(SERVICE.compareDetails(before, after))
                .extracting(change -> change.field(), change -> change.beforeValue(),
                        change -> change.afterValue(), change -> change.kind())
                .containsExactly(
                        tuple("isAwarded", Optional.empty(), Optional.of("false"), J6ChangeKind.ADDED),
                        tuple("homeScore.display", Optional.empty(), Optional.of("0"), J6ChangeKind.ADDED),
                        tuple("awayScore.display", Optional.empty(), Optional.of("0"), J6ChangeKind.ADDED));
    }

    @Test
    void reportsAnAwardAndItsDisplayScoreCorrectionWithoutChangingTheUnchangedSide() {
        var before = awardedDetails(14, false, 0, 0);
        var after = awardedDetails(15, true, 3, 0);

        assertThat(SERVICE.compareDetails(before, after))
                .extracting(change -> change.field(), change -> change.beforeValue(),
                        change -> change.afterValue(), change -> change.kind())
                .containsExactly(
                        tuple("isAwarded", Optional.of("false"), Optional.of("true"), J6ChangeKind.CHANGED),
                        tuple("homeScore.display", Optional.of("0"), Optional.of("3"), J6ChangeKind.CHANGED));
    }

    @Test
    void reportsRemovedAwardedFieldsWithoutReplacingThemWithFalseOrZero() {
        var before = awardedDetails(16, true, 0, 3);
        var after = awardedDetails(17, null, null, null);

        assertThat(SERVICE.compareDetails(before, after))
                .extracting(change -> change.field(), change -> change.beforeValue(),
                        change -> change.afterValue(), change -> change.kind())
                .containsExactly(
                        tuple("isAwarded", Optional.of("true"), Optional.empty(), J6ChangeKind.REMOVED),
                        tuple("homeScore.display", Optional.of("0"), Optional.empty(), J6ChangeKind.REMOVED),
                        tuple("awayScore.display", Optional.of("3"), Optional.empty(), J6ChangeKind.REMOVED));
    }

    @Test
    void doesNotReportUnchangedAwardedValuesAsSemanticChanges() {
        assertThat(SERVICE.compareDetails(
                awardedDetails(18, true, 3, 0),
                awardedDetails(19, true, 3, 0))).isEmpty();
    }

    @Test
    void keysStatisticsAndLineupsByTheirStableBusinessIdentities() {
        EventStatistics oldStatistics = new EventStatistics(PROVIDER_EVENT_ID, List.of(
                metric("ALL", "Match", "possession", "Possession", "48%", "52%"),
                metric("ALL", "Match", "shots", "Shots", "8", "9")));
        EventStatistics newStatistics = new EventStatistics(PROVIDER_EVENT_ID, List.of(
                metric("ALL", "Match", "possession", "Ball possession", "51%", "49%"),
                metric("ALL", "Match", "corners", "Corners", "6", "3")));

        var statisticChanges = SERVICE.compareEventData(
                view(20, oldStatistics, "event-statistics-v1", 'a'),
                view(21, newStatistics, "event-statistics-v1", 'b'));

        assertThat(statisticChanges)
                .extracting(change -> change.field())
                .contains(
                        "statistics[ALL/Match/possession].label",
                        "statistics[ALL/Match/possession].home",
                        "statistics[ALL/Match/possession].away",
                        "statistics[ALL/Match/shots]",
                        "statistics[ALL/Match/corners]");

        EventLineups oldLineups = lineups(
                false,
                "4-4-2",
                List.of(player(100, "Alice", 9, "F", true)));
        EventLineups newLineups = lineups(
                true,
                "4-3-3",
                List.of(
                        player(100, "Alice Martin", 10, "F", true),
                        player(101, "Bob", 12, "M", false)));

        var lineupChanges = SERVICE.compareEventData(
                view(30, oldLineups, "event-lineups-v1", 'c'),
                view(31, newLineups, "event-lineups-v1", 'd'));

        assertThat(lineupChanges)
                .extracting(change -> change.field())
                .contains(
                        "lineups.confirmed",
                        "lineups[HOME].formation",
                        "lineups[HOME,playerId=100].name",
                        "lineups[HOME,playerId=100].number",
                        "lineups[HOME,playerId=101]");
        assertThat(lineupChanges.stream()
                .filter(change -> change.field().equals("lineups[HOME,playerId=101]"))
                .findFirst().orElseThrow().kind()).isEqualTo(J6ChangeKind.ADDED);
    }

    @Test
    void matchesIncidentsByProviderIdentityButDoesNotInventAmbiguousPairs() {
        EventIncidents before = new EventIncidents(PROVIDER_EVENT_ID, List.of(
                incident(0, "goal", true, 10L, 1, 0),
                incident(1, "card", false, 20L, null, null)));
        EventIncidents after = new EventIncidents(PROVIDER_EVENT_ID, List.of(
                incident(0, "card", false, 30L, null, null),
                incident(1, "goal", true, 10L, 1, 0)));

        var changes = SERVICE.compareEventData(
                view(40, before, "event-incidents-v1", 'e'),
                view(41, after, "event-incidents-v1", 'f'));

        assertThat(changes)
                .extracting(change -> change.field())
                .contains(
                        "incidents[before=0,after=1].order",
                        "incidents");
        assertThat(changes.stream()
                .filter(change -> change.field().equals("incidents"))
                .map(change -> change.kind()))
                .containsExactlyInAnyOrder(J6ChangeKind.REMOVED, J6ChangeKind.ADDED);

        EventIncidents ambiguousBefore = new EventIncidents(PROVIDER_EVENT_ID, List.of(
                incident(0, "card", true, 90L, null, null),
                incident(1, "card", true, 90L, null, null)));
        EventIncidents ambiguousAfter = new EventIncidents(PROVIDER_EVENT_ID, List.of(
                incident(0, "card", true, 90L, null, null),
                incident(1, "card", true, 90L, null, null),
                incident(2, "card", true, 90L, null, null)));

        var ambiguousChanges = SERVICE.compareEventData(
                view(42, ambiguousBefore, "event-incidents-v1", '1'),
                view(43, ambiguousAfter, "event-incidents-v1", '2'));

        assertThat(ambiguousChanges)
                .filteredOn(change -> change.field().startsWith("incidents"))
                .singleElement()
                .satisfies(change -> assertThat(change.kind()).isEqualTo(J6ChangeKind.ADDED));
    }

    @Test
    void derivesTheScoreFromTheLastStoredIncidentThatCarriesBothValues() {
        EventIncidents incidents = new EventIncidents(PROVIDER_EVENT_ID, List.of(
                incident(0, "goal", true, 10L, 1, 0),
                incident(1, "card", false, 20L, null, null),
                incident(2, "goal", false, 30L, 1, 1),
                incident(3, "period", null, null, null, null)));

        assertThat(SERVICE.score(incidents))
                .hasValueSatisfying(score -> {
                    assertThat(score.home()).isEqualTo(1);
                    assertThat(score.away()).isEqualTo(1);
                    assertThat(score.label()).isEqualTo("1–1");
                });
        assertThat(SERVICE.score(new EventIncidents(
                PROVIDER_EVENT_ID,
                List.of(incident(0, "card", true, 10L, null, null))))).isEmpty();
    }

    private static CanonicalEventObservationView state(
            long observationId,
            String startsAt,
            String status,
            String homeName,
            String tournamentName) {
        return new CanonicalEventObservationView(
                observationId,
                IDENTITY,
                Instant.parse(startsAt),
                new ScheduledTeam(1, homeName),
                new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus(status, Optional.empty()),
                Optional.of(new ScheduledTournament(50, tournamentName)),
                source("state-" + observationId, "scheduled-events-v1", 'a', observationId),
                hash('c'),
                2);
    }

    private static EventDetailObservationView details(
            long observationId,
            Optional<EventVenue> venue,
            Optional<EventSeason> season,
            Optional<String> round) {
        EventDetails details = new EventDetails(
                PROVIDER_EVENT_ID,
                Instant.parse("2026-08-20T18:00:00Z"),
                new ScheduledTeam(1, "Home"),
                new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("notstarted", Optional.empty()),
                Optional.empty(),
                venue,
                season,
                round);
        return new EventDetailObservationView(
                observationId,
                IDENTITY,
                details,
                source("details-" + observationId, "event-details-v1", 'b', observationId),
                hash('d'));
    }

    private static EventDetailObservationView awardedDetails(
            long observationId, Boolean awarded, Integer homeDisplay, Integer awayDisplay) {
        EventDetails details = new EventDetails(
                PROVIDER_EVENT_ID,
                Instant.parse("2026-08-20T18:00:00Z"),
                new ScheduledTeam(1, "Home"),
                new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("finished", Optional.empty()),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.ofNullable(awarded), Optional.ofNullable(homeDisplay), Optional.ofNullable(awayDisplay));
        var provenance = source("awarded-details-" + observationId,
                "event-details-v3", 'a', observationId);
        var observation = EventDetailObservation.from(IDENTITY, details, provenance);
        return new EventDetailObservationView(observationId, IDENTITY, details,
                provenance, observation.normalizedSha256());
    }

    private static EventStatisticMetric metric(
            String period,
            String group,
            String code,
            String label,
            String home,
            String away) {
        return new EventStatisticMetric(
                period,
                group,
                code,
                label,
                Optional.of(home),
                Optional.of(away));
    }

    private static EventLineupPlayer player(
            long id,
            String name,
            int number,
            String position,
            boolean starter) {
        return new EventLineupPlayer(
                id,
                name,
                Optional.of(number),
                Optional.of(position),
                starter);
    }

    private static EventLineups lineups(
            boolean confirmed,
            String homeFormation,
            List<EventLineupPlayer> homePlayers) {
        return new EventLineups(
                PROVIDER_EVENT_ID,
                confirmed,
                new TeamLineup(LineupSide.HOME, Optional.of(homeFormation), homePlayers),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()));
    }

    private static EventIncident incident(
            int sequence,
            String type,
            Boolean home,
            Long playerId,
            Integer homeScore,
            Integer awayScore) {
        String playerName = playerId == null ? null : "Player " + playerId;
        Optional<String> period = "period".equals(type) ? Optional.of("FT") : Optional.empty();
        return new EventIncident(
                sequence,
                type,
                10 + sequence,
                Optional.empty(),
                Optional.ofNullable(home),
                Optional.empty(),
                Optional.ofNullable(playerId),
                Optional.ofNullable(playerName),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(homeScore),
                Optional.ofNullable(awayScore),
                Optional.empty(),
                Optional.empty(),
                period,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static J5EventDataObservationView view(
            long observationId,
            com.bettingproject.sofascorelocal.domain.eventdata.J5EventData data,
            String parserVersion,
            char hashCharacter) {
        return new J5EventDataObservationView(
                observationId,
                IDENTITY,
                data,
                source("data-" + observationId, parserVersion, hashCharacter, observationId),
                J5CompletenessReport.measured(1, 1, List.of()),
                hash(hashCharacter));
    }

    private static EventSourceTrace source(
            String fixtureId,
            String parserVersion,
            char hashCharacter,
            long secondOffset) {
        return new EventSourceTrace(
                com.bettingproject.sofascorelocal.domain.event.EventSourceKind.SYNTHETIC_FIXTURE,
                OptionalLong.empty(),
                Optional.of(fixtureId),
                hash(hashCharacter),
                parserVersion,
                Instant.parse("2026-08-18T10:00:00Z").plusSeconds(secondOffset));
    }

    private static String hash(char value) {
        return Character.toString(value).repeat(64);
    }
}
