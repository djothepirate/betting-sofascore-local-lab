package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LineupIncidentOverlayTest {

    @Test
    void keepsDirectLineupGoalsAndAssistsButEnrichesCardsAndSubstitutionsFromIncidents() {
        var lineups = new EventLineups(900001L, true,
                new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(
                        player(100L, "Home scorer", Optional.empty()),
                        player(101L, "Home assistant", Optional.empty()),
                        player(102L, "Outgoing player", Optional.of(new PlayerMatchStatistics(
                                Map.of("rating", new BigDecimal("6.7")), Map.of()))),
                        player(103L, "Incoming player", Optional.of(new PlayerMatchStatistics(
                                Map.of("minutesPlayed", BigDecimal.ZERO), Map.of()))),
                        player(104L, "Rescinded card", Optional.empty()),
                        player(105L, "Partial substitution", Optional.empty()),
                        player(106L, "Missing side", Optional.empty()),
                        player(200L, "Existing lineup statistics", Optional.of(new PlayerMatchStatistics(
                                Map.of("goals", BigDecimal.ONE), Map.of()))),
                        player(201L, "Existing lineup assister", Optional.of(new PlayerMatchStatistics(
                                Map.of("goalAssist", BigDecimal.ONE), Map.of()))))),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of(
                        player(100L, "Away player with same provider id", Optional.of(new PlayerMatchStatistics(
                                Map.of("rating", new BigDecimal("6.4")), Map.of()))))));
        var incidents = new EventIncidents(900001L, List.of(
                incident(0, "goal", 37, Optional.of(true), Optional.of(100L), Optional.of("Home scorer"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("regular"),
                        Optional.of(101L), Optional.of("Home assistant"), Optional.empty()),
                incident(1, "card", 42, Optional.of(true), Optional.of(100L), Optional.of("Home scorer"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("yellow"),
                        Optional.empty(), Optional.empty(), Optional.empty()),
                incident(2, "card", 43, Optional.of(false), Optional.of(100L), Optional.of("Away player with same provider id"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("red"),
                        Optional.empty(), Optional.empty(), Optional.empty()),
                incident(3, "substitution", 71, Optional.of(true), Optional.empty(), Optional.empty(),
                        Optional.of(103L), Optional.of("Incoming player"), Optional.of(102L), Optional.of("Outgoing player"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                incident(4, "substitution", 73, Optional.of(true), Optional.empty(), Optional.empty(),
                        Optional.of(105L), Optional.of("Partial substitution"), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                incident(5, "card", 74, Optional.of(true), Optional.of(104L), Optional.of("Rescinded card"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("yellow"),
                        Optional.empty(), Optional.empty(), Optional.of(true)),
                incident(6, "goal", 75, Optional.empty(), Optional.of(106L), Optional.of("Missing side"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("regular"),
                        Optional.empty(), Optional.empty(), Optional.empty()),
                incident(7, "goal", 76, Optional.of(true), Optional.of(200L), Optional.of("Existing lineup statistics"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("regular"),
                        Optional.of(201L), Optional.of("Existing lineup assister"), Optional.empty()),
                incident(8, "card", 77, Optional.of(true), Optional.of(200L), Optional.of("Existing lineup statistics"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("yellow"),
                        Optional.empty(), Optional.empty(), Optional.empty())));

        var view = LineupsPresentation.from(lineups, "Home", "Away", LineupCountryOverlay.empty(),
                LineupIncidentOverlay.from(incidents), true);

        assertThat(player(view, "HOME:100").incidentDecorations()).extracting(LineupIncidentOverlay.Decoration::key)
                .containsExactly("goal", "yellow-card");
        assertThat(player(view, "HOME:101").incidentDecorations()).singleElement().satisfies(decoration -> {
            assertThat(decoration.key()).isEqualTo("assist");
            assertThat(decoration.label()).isEqualTo("Passe décisive");
            assertThat(decoration.minuteLabel()).isEqualTo("37");
            assertThat(decoration.source()).isEqualTo("EVENT_INCIDENTS");
        });
        assertThat(player(view, "AWAY:100").incidentDecorations()).singleElement()
                .extracting(LineupIncidentOverlay.Decoration::key).isEqualTo("red-card");
        assertThat(player(view, "AWAY:100").statistics()).isNotNull();
        assertThat(player(view, "HOME:102").incidentDecorations()).singleElement().satisfies(decoration -> {
            assertThat(decoration.key()).isEqualTo("substitution-out");
            assertThat(decoration.label()).isEqualTo("Sortie observée");
            assertThat(decoration.minuteLabel()).isEqualTo("71");
        });
        assertThat(player(view, "HOME:102").statistics()).isNotNull();
        assertThat(player(view, "HOME:103").incidentDecorations()).singleElement().satisfies(decoration -> {
            assertThat(decoration.key()).isEqualTo("substitution-in");
            assertThat(decoration.label()).isEqualTo("Entrée observée");
            assertThat(decoration.minuteLabel()).isEqualTo("71");
        });
        assertThat(player(view, "HOME:103").statistics()).isNotNull();
        assertThat(player(view, "HOME:104").incidentDecorations()).isEmpty();
        assertThat(player(view, "HOME:105").incidentDecorations()).isEmpty();
        assertThat(player(view, "HOME:106").incidentDecorations()).isEmpty();
        assertThat(player(view, "HOME:200")).satisfies(player -> {
            assertThat(player.statistics()).isNotNull();
            assertThat(player.achievements()).extracting(LineupsPresentation.Achievement::label).containsExactly("1 but");
            assertThat(player.incidentDecorations()).extracting(LineupIncidentOverlay.Decoration::key)
                    .containsExactly("yellow-card");
            assertThat(player.detailsAllowed()).isTrue();
        });
        assertThat(player(view, "HOME:201")).satisfies(player -> {
            assertThat(player.statistics()).isNotNull();
            assertThat(player.achievements()).extracting(LineupsPresentation.Achievement::label)
                    .containsExactly("1 passe décisive");
            assertThat(player.incidentDecorations()).isEmpty();
        });
    }

    @Test
    void cardDetailsFollowTheJ4CapabilityRatherThanThePresenceOfLineupMetrics() {
        var withMetrics = player(300L, "With metrics", Optional.of(new PlayerMatchStatistics(
                Map.of("goals", BigDecimal.ZERO), Map.of())));
        var withoutMetrics = player(301L, "Without metrics", Optional.empty());
        var lineups = new EventLineups(900001L, false,
                new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(withMetrics, withoutMetrics)),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()));

        var staticView = LineupsPresentation.from(lineups, "Home", "Away", LineupCountryOverlay.empty(),
                LineupIncidentOverlay.empty(), false);
        var allowedView = LineupsPresentation.from(lineups, "Home", "Away", LineupCountryOverlay.empty(),
                LineupIncidentOverlay.empty(), true);

        assertThat(player(staticView, "HOME:300").hasStatistics()).isTrue();
        assertThat(player(staticView, "HOME:300").detailsAllowed()).isFalse();
        assertThat(player(staticView, "HOME:301").hasStatistics()).isFalse();
        assertThat(player(staticView, "HOME:301").detailsAllowed()).isFalse();
        assertThat(player(allowedView, "HOME:300").detailsAllowed()).isTrue();
        assertThat(player(allowedView, "HOME:301").detailsAllowed()).isTrue();
    }

    private static EventLineupPlayer player(long id, String name, Optional<PlayerMatchStatistics> statistics) {
        return new EventLineupPlayer(id, name, Optional.empty(), Optional.of("F"), true,
                Optional.empty(), statistics);
    }

    private static EventIncident incident(int sequence, String type, int minute, Optional<Boolean> home,
                                          Optional<Long> playerId, Optional<String> playerName,
                                          Optional<Long> playerInId, Optional<String> playerInName,
                                          Optional<Long> playerOutId, Optional<String> playerOutName,
                                          Optional<String> incidentClass, Optional<Long> assistId,
                                          Optional<String> assistName, Optional<Boolean> rescinded) {
        return new EventIncident(sequence, type, minute, Optional.empty(), home, Optional.empty(), playerId,
                playerName, playerInId, playerInName, playerOutId, playerOutName, Optional.empty(), Optional.empty(),
                incidentClass, Optional.empty(), Optional.empty(), Optional.empty(), assistId, assistName,
                Optional.empty(), Optional.empty(), Optional.empty(), rescinded, Optional.empty(), Optional.empty());
    }

    private static LineupsPresentation.Player player(LineupsPresentation.View view, String key) {
        return view.teams().stream()
                .flatMap(team -> Stream.concat(team.starterGroups().stream().flatMap(group -> group.players().stream()),
                        team.substitutes().stream()))
                .filter(player -> player.key().equals(key)).findFirst().orElseThrow();
    }
}
