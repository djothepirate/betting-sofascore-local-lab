package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LineupsPresentationTest {
    @Test
    void partitionsEveryPlayerByObservedRoleAndPositionWithoutLosingOrderInsideGroups() {
        List<EventLineupPlayer> home = List.of(
                player(1, "Defender first", 12, "D", true),
                player(2, "Forward", 9, "F", true),
                player(3, "Defender second", 2, "D", true),
                player(4, "Keeper", 1, "G", true),
                player(5, "Midfielder", 8, "M", true),
                player(6, "Reserve keeper", 30, "G", false),
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
        List<EventLineupPlayer> sameNames = List.of(player(10, "Same name", 8, "M", true),
                player(11, "Same name", 8, "M", true));
        var before = LineupsPresentation.from(lineups(false, null, sameNames,
                List.of(player(10, "Same name", 8, "M", true))));
        assertThat(players(before.teams().getFirst()).map(LineupsPresentation.Player::key))
                .containsExactly("HOME:10", "HOME:11");
        assertThat(players(before.teams().getLast()).map(LineupsPresentation.Player::key)).containsExactly("AWAY:10");
        var after = LineupsPresentation.from(lineups(true, null,
                List.of(player(10, "New display name", 22, "F", false)), List.of()));
        assertThat(after.teams().getFirst().substitutes()).singleElement()
                .satisfies(player -> assertThat(player.key()).isEqualTo("HOME:10"));
        assertThat(after.teams().getFirst().starterGroups()).isEmpty();
    }

    private static Stream<LineupsPresentation.Player> players(LineupsPresentation.Team team) {
        return Stream.concat(team.starterGroups().stream().flatMap(group -> group.players().stream()), team.substitutes().stream());
    }

    private static EventLineups lineups(boolean confirmed, String formation, List<EventLineupPlayer> home, List<EventLineupPlayer> away) {
        return new EventLineups(900001, confirmed, new TeamLineup(LineupSide.HOME, Optional.ofNullable(formation), home),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), away));
    }

    private static EventLineupPlayer player(long id, String name, Integer shirt, String position, boolean starter) {
        return new EventLineupPlayer(id, name, Optional.ofNullable(shirt), Optional.ofNullable(position), starter);
    }
}
