package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.MissingLineupPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LineupsPresentationTest {
    @Test
    void unavailablePlayersUseTheirOwnCountryAndKeepAnExplicitFallback() {
        var unavailable = new MissingLineupPlayer(31, "Absent synthétique", Optional.of(42), Optional.of("D"),
                Optional.of("missing"), Optional.of(1), Optional.of("Knee Injury"), Optional.of(5), Optional.empty(),
                Optional.of(new com.bettingproject.sofascorelocal.domain.event.ProviderCountry(Optional.of("Argentina"), Optional.of("AR"))));
        var home = new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(), Optional.of(List.of(unavailable,
                missing(32, "Back Injury"))));
        var view = LineupsPresentation.from(new EventLineups(900001, true, home,
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of())), "Équipe française", "Autre équipe");
        assertThat(view.teams().getFirst().missingPlayers()).satisfies(players -> {
            assertThat(players.getFirst().country().label()).isEqualTo("Argentina");
            assertThat(players.getFirst().country().flagPath()).isEqualTo("/images/flags/4x3/ar.svg");
            assertThat(players.getLast().country().label()).isEqualTo("Pays non renseigné");
            assertThat(players.getLast().country().flagPath()).isEmpty();
            assertThat(players.getFirst().description()).isEqualTo("Blessure au genou");
        });
    }

    @Test
    void unavailablePlayerLabelsTranslateKnownSupplierDescriptionsWithoutChangingTheirSourceValues() {
        var rawDescriptions = List.of("red_card_suspension", "Shoulder Injury", "Meniscus Injury", "Hernia",
                "Ligament Injury", "Heart Problems", "Knock Injury", "Groin Injury", "Strain Injury");
        var home = new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(), Optional.of(List.of(
                missing(101, rawDescriptions.get(0)), missing(102, rawDescriptions.get(1)),
                missing(103, rawDescriptions.get(2)), missing(104, rawDescriptions.get(3)),
                missing(105, rawDescriptions.get(4)), missing(106, rawDescriptions.get(5)),
                missing(107, rawDescriptions.get(6)), missing(108, rawDescriptions.get(7)),
                missing(109, rawDescriptions.get(8)))));

        var displayed = LineupsPresentation.from(new EventLineups(900001, true, home,
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()))).teams().getFirst().missingPlayers();

        assertThat(displayed).extracting(LineupsPresentation.MissingPlayer::description).containsExactly(
                "Suspension après carton rouge", "Blessure à l’épaule", "Blessure au ménisque", "Hernie",
                "Blessure aux ligaments", "Problèmes cardiaques", "Coup", "Blessure à l’aine",
                "Blessure à l’entraînement");
        assertThat(displayed).allSatisfy(player -> assertThat(player.type()).isEqualTo("Indisponible"));
        assertThat(home.missingPlayers().orElseThrow()).extracting(MissingLineupPlayer::description)
                .containsExactlyElementsOf(rawDescriptions.stream().map(Optional::of).toList());
    }

    @Test
    void achievementsUseExactPositiveCountsAndCountryComesFromThePlayer() {
        var statistics = new PlayerMatchStatistics(Map.of("goals", new java.math.BigDecimal("2"),
                "goalAssist", new java.math.BigDecimal("3")), Map.of());
        var source = new EventLineupPlayer(1, "Joueur", Optional.of(9), Optional.of("F"), true,
                Optional.of(true), Optional.of(statistics), Optional.of(new com.bettingproject.sofascorelocal.domain.event.ProviderCountry(
                        Optional.of("France"), Optional.of("FR"))));
        var player = players(LineupsPresentation.from(lineups(true, null, List.of(source), List.of())).teams().getFirst()).findFirst().orElseThrow();
        assertThat(player.hasStatistics()).isTrue();
        assertThat(player.captain()).isTrue();
        assertThat(player.country().flagPath()).isEqualTo("/images/flags/4x3/fr.svg");
        assertThat(player.country().label()).isEqualTo("France");
        assertThat(player.achievements()).extracting(LineupsPresentation.Achievement::label)
                .containsExactly("2 buts", "3 passes décisives");
        assertThat(player.achievements()).extracting(value -> value.repeats().size()).containsExactly(2, 3);
    }

    @Test
    void emptyStatisticsDoNotOfferDetailsWhileZeroAndRatingVersionsAreDisplayable() {
        var empty = new PlayerStatisticsPresentation.View(List.of(), List.of());
        assertThat(new LineupsPresentation.Player("p", "Joueur", "9", "Attaquant", "Titulaire", false, null).hasStatistics()).isFalse();
        assertThat(new LineupsPresentation.Player("p", "Joueur", "9", "Attaquant", "Titulaire", false, empty).hasStatistics()).isFalse();
        for (var stats : List.of(new PlayerMatchStatistics(Map.of("goals", java.math.BigDecimal.ZERO), Map.of()),
                new PlayerMatchStatistics(Map.of(), Map.of("original", java.math.BigDecimal.ZERO)))) {
            assertThat(new LineupsPresentation.Player("p", "Joueur", "9", "Attaquant", "Titulaire", false,
                    PlayerStatisticsPresentation.from(stats)).hasStatistics()).isTrue();
        }
    }

    @Test
    void malformedCountsAreNotRoundedIntoAchievementsAndHugeCountsHaveBoundedIcons() {
        for (String value : List.of("0", "-1", "1.5", "99999999999999999999999999999999999999")) {
            var stats = new PlayerMatchStatistics(Map.of("goals", new java.math.BigDecimal(value)), Map.of());
            var source = new EventLineupPlayer(1, "Joueur", Optional.empty(), Optional.empty(), true,
                    Optional.empty(), Optional.of(stats));
            var player = players(LineupsPresentation.from(lineups(true, null, List.of(source), List.of())).teams().getFirst()).findFirst().orElseThrow();
            if (value.length() > 10) {
                assertThat(player.achievements()).singleElement().satisfies(badge -> {
                    assertThat(badge.count()).isEqualTo(value); assertThat(badge.repeats()).hasSize(1);
                    assertThat(badge.compact()).isTrue();
                });
            } else assertThat(player.achievements()).isEmpty();
            assertThat(player.country().flagPath()).isEmpty();
            assertThat(player.country().label()).isEqualTo("Pays non renseigné");
        }
    }

    @Test
    void partitionsEveryPlayerByObservedRoleAndPositionWithoutLosingOrderInsideGroups() {
        List<EventLineupPlayer> home = List.of(
                player(1, "Defender first", 12, "D", true, true),
                player(2, "Forward", 9, "F", true, false),
                player(3, "Defender second", 2, "D", true),
                player(4, "Keeper", 1, "G", true),
                player(5, "Midfielder", 8, "M", true),
                player(6, "Reserve keeper", 30, "G", false, true),
                player(7, "Other position", 20, "D/M", true),
                player(8, "Unknown position", null, null, true),
                player(9, "Reserve forward", 10, "F", false));
        List<EventLineupPlayer> away = List.of(player(10, "Away defender", 4, "D", true));
        EventLineups source = lineups(true, "4-3-3", home, away);

        var view = LineupsPresentation.from(source, "Home club", "Away club");

        assertThat(view.confirmed()).isTrue();
        assertThat(view.confirmationLabel()).isEqualTo("Confirmée");
        assertThat(view.teams()).extracting(LineupsPresentation.Team::side).containsExactly("HOME", "AWAY");
        assertThat(view.teams()).extracting(LineupsPresentation.Team::name).containsExactly("Home club", "Away club");
        var team = view.teams().getFirst();
        assertThat(team.sideLabel()).isEqualTo("Domicile");
        assertThat(team.formation()).isEqualTo("4-3-3");
        assertThat(team.starterCount()).isEqualTo(7);
        assertThat(team.substituteCount()).isEqualTo(2);
        assertThat(team.starterGroups()).extracting(LineupsPresentation.Group::key)
                .containsExactly("G", "D", "M", "F", "UNKNOWN");
        assertThat(team.starterGroups()).extracting(LineupsPresentation.Group::label)
                .containsExactly("Gardiens", "Défenseurs", "Milieux", "Attaquants", "Poste non renseigné ou autre");
        assertThat(team.starterGroups().subList(0, 4).stream().map(group -> group.players().getFirst().positionLabel()))
                .containsExactly("Gardien", "Défenseur", "Milieu", "Attaquant");
        assertThat(team.starterGroups().get(1).players()).extracting(LineupsPresentation.Player::key)
                .containsExactly("HOME:1", "HOME:3");
        assertThat(team.starterGroups().get(1).players()).extracting(LineupsPresentation.Player::shirtNumber)
                .containsExactly("12", "2");
        assertThat(team.starterGroups().getLast().players()).extracting(LineupsPresentation.Player::positionLabel)
                .containsExactly("D/M", "Poste non renseigné");
        assertThat(team.starterGroups().stream().flatMap(group -> group.players().stream()))
                .allSatisfy(player -> assertThat(player.roleLabel()).isEqualTo("Titulaire"));
        assertThat(team.substitutes()).extracting(LineupsPresentation.Player::key).containsExactly("HOME:6", "HOME:9");
        assertThat(team.substitutes()).allSatisfy(player -> assertThat(player.roleLabel()).isEqualTo("Remplaçant"));
        assertThat(players(team).filter(LineupsPresentation.Player::captain).map(LineupsPresentation.Player::key))
                .as("captain follows the explicit flag independently of position or starter role")
                .containsExactly("HOME:1", "HOME:6");
        assertThat(players(view.teams().getLast())).noneMatch(LineupsPresentation.Player::captain);
        assertThat(players(team).map(LineupsPresentation.Player::key))
                .containsExactlyInAnyOrderElementsOf(home.stream().map(player -> "HOME:" + player.providerPlayerId()).toList());
        assertThat(view.teams().getLast().starterGroups()).extracting(LineupsPresentation.Group::key).containsExactly("D");
        assertThat(players(view.teams().getLast()).map(LineupsPresentation.Player::key)).containsExactly("AWAY:10");
        assertThat(source.home().players()).containsExactlyElementsOf(home);
        assertThat(source.home().formation()).contains("4-3-3");
    }

    @Test
    void incompleteAndEmptyRostersRemainExplicitWithoutPlayersInferredFromFormation() {
        EventLineups source = lineups(false, null,
                List.of(player(1, "No optional fields", null, null, true)), List.of());
        var view = LineupsPresentation.from(source);
        assertThat(view.confirmed()).isFalse();
        assertThat(view.confirmationLabel()).isEqualTo("Provisoire");
        assertThat(view.teams()).extracting(LineupsPresentation.Team::name).containsExactly("Domicile", "Extérieur");
        assertThat(view.teams()).extracting(LineupsPresentation.Team::formation)
                .containsExactly("Formation non renseignée", "Formation non renseignée");
        assertThat(view.teams().getFirst().starterGroups()).singleElement().satisfies(group -> {
            assertThat(group.key()).isEqualTo("UNKNOWN");
            assertThat(group.players()).singleElement().satisfies(player -> {
                assertThat(player.shirtNumber()).isEqualTo("—");
                assertThat(player.positionLabel()).isEqualTo("Poste non renseigné");
                assertThat(player.name()).isEqualTo("No optional fields");
                assertThat(player.captain()).isFalse();
            });
        });
        assertThat(view.teams().getLast()).satisfies(team -> {
            assertThat(team.starterCount()).isZero();assertThat(team.substituteCount()).isZero();
            assertThat(team.starterGroups()).isEmpty();assertThat(team.substitutes()).isEmpty();
        });
        var empty = LineupsPresentation.from(lineups(true, "4-4-2", List.of(), List.of()), "", " ");
        assertThat(empty.teams()).extracting(LineupsPresentation.Team::name).containsExactly("Domicile", "Extérieur");
        assertThat(empty.teams()).allSatisfy(team -> {
            assertThat(team.starterGroups()).isEmpty();assertThat(team.substitutes()).isEmpty();
        });
        assertThat(empty.teams().getFirst().formation()).isEqualTo("4-4-2");
    }

    @Test
    void sourceTextIsNotHtmlEncodedAndUnrecognizedPositionsAreNotReinterpreted() {
        EventLineups source = lineups(false, "<formation>",
                List.of(player(1, "<img src=x onerror=alert(1)>", 99, "<position>", true),
                        player(2, "Lowercase", 13, "g", true)), List.of());
        var team = LineupsPresentation.from(source, "<Home & club>", "Away").teams().getFirst();
        assertThat(team.name()).isEqualTo("<Home & club>");
        assertThat(team.formation()).isEqualTo("<formation>");
        assertThat(team.starterGroups()).singleElement().satisfies(group -> {
            assertThat(group.key()).isEqualTo("UNKNOWN");
            assertThat(group.players()).extracting(LineupsPresentation.Player::name)
                    .containsExactly("<img src=x onerror=alert(1)>", "Lowercase");
            assertThat(group.players()).extracting(LineupsPresentation.Player::positionLabel)
                    .containsExactly("<position>", "g");
        });
    }

    @Test
    void identityUsesProviderIdAndSideRatherThanNameShirtNumberOrRole() {
        List<EventLineupPlayer> sameNames = List.of(player(10, "Same name", 8, "M", true, true),
                player(11, "Same name", 8, "M", true));
        var before = LineupsPresentation.from(lineups(false, null, sameNames,
                List.of(player(10, "Same name", 8, "M", true))));
        assertThat(players(before.teams().getFirst()).map(LineupsPresentation.Player::key))
                .containsExactly("HOME:10", "HOME:11");
        assertThat(players(before.teams().getLast()).map(LineupsPresentation.Player::key)).containsExactly("AWAY:10");
        assertThat(players(before.teams().getFirst()).filter(LineupsPresentation.Player::captain)
                .map(LineupsPresentation.Player::key)).containsExactly("HOME:10");
        var after = LineupsPresentation.from(lineups(true, null,
                List.of(player(10, "New display name", 22, "F", false, false)), List.of()));
        assertThat(after.teams().getFirst().substitutes()).singleElement()
                .satisfies(player -> {
                    assertThat(player.key()).isEqualTo("HOME:10");
                    assertThat(player.captain()).isFalse();
                });
        assertThat(after.teams().getFirst().starterGroups()).isEmpty();
    }

    private static Stream<LineupsPresentation.Player> players(LineupsPresentation.Team team) {
        return Stream.concat(team.starterGroups().stream().flatMap(group -> group.players().stream()), team.substitutes().stream());
    }

    @Test
    void distinguishesAbsentAndEmptyStatisticsAndKeepsMissingPlayersOutsideRosterCounts() {
        var missing = List.of(
                missing(20, "Achilles Tendon Injury"), missing(21, "Sprained Knee Injury"),
                missing(22, "Dislocated Shoulder"), missing(23, "<unknown description>"), missing(20, "Achilles Tendon Injury"));
        var home = new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(
                player(1, "No statistics", 1, "G", true),
                new EventLineupPlayer(2, "Empty statistics", Optional.empty(), Optional.empty(), false,
                        Optional.empty(), Optional.of(new PlayerMatchStatistics(Map.of(), Map.of())))), Optional.of(missing));
        var away = new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of(), Optional.of(List.of()));
        var view = LineupsPresentation.from(new EventLineups(900001, false, home, away));
        var team = view.teams().getFirst();
        assertThat(team.starterCount()).isOne();
        assertThat(team.substituteCount()).isOne();
        assertThat(team.starterGroups().getFirst().players().getFirst().statistics()).isNull();
        assertThat(team.substitutes().getFirst().statistics().groups()).isEmpty();
        assertThat(team.missingPlayers()).extracting(LineupsPresentation.MissingPlayer::description)
                .containsExactly("Blessure au tendon d’Achille", "Entorse du genou", "Luxation de l’épaule", "<unknown description>", "Blessure au tendon d’Achille");
        assertThat(team.missingPlayers()).extracting(LineupsPresentation.MissingPlayer::key)
                .containsExactly("HOME:20", "HOME:21", "HOME:22", "HOME:23", "HOME:20|duplicate:2");
        assertThat(team.missingPlayers()).allSatisfy(player -> {
            assertThat(player.expectedReturn()).isEqualTo("20/09/2026 à 02:00 +02:00");
            assertThat(player.reason()).isEqualTo("99");
            assertThat(player.externalType()).isEqualTo("5");
        });
        assertThat(view.teams().getLast().missingPlayers()).isEmpty();
        assertThat(LineupsPresentation.from(lineups(false, null, List.of(), List.of())).teams().getFirst().missingPlayers()).isNull();
        assertThat(home.missingPlayers().orElseThrow().getFirst().description()).contains("Achilles Tendon Injury");
    }

    private static MissingLineupPlayer missing(long id, String description) {
        return new MissingLineupPlayer(id, "Missing " + id, Optional.empty(), Optional.empty(), Optional.of("missing"),
                Optional.of(99), Optional.of(description), Optional.of(5),
                Optional.of(OffsetDateTime.parse("2026-09-20T02:00:00+02:00")));
    }

    private static EventLineups lineups(boolean confirmed, String formation, List<EventLineupPlayer> home, List<EventLineupPlayer> away) {
        return new EventLineups(900001, confirmed, new TeamLineup(LineupSide.HOME, Optional.ofNullable(formation), home),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), away));
    }

    private static EventLineupPlayer player(long id, String name, Integer shirt, String position, boolean starter) {
        return new EventLineupPlayer(id, name, Optional.ofNullable(shirt), Optional.ofNullable(position), starter);
    }

    private static EventLineupPlayer player(long id, String name, Integer shirt, String position,
                                            boolean starter, Boolean captain) {
        return new EventLineupPlayer(id, name, Optional.ofNullable(shirt), Optional.ofNullable(position),
                starter, Optional.ofNullable(captain));
    }
}
