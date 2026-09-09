package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.MissingLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display-only roster grouping. Rendering escapes text; no pitch position is inferred from formation. */
public final class LineupsPresentation {
    private static final List<String> GROUP_ORDER = List.of("G", "D", "M", "F", "UNKNOWN");

    private LineupsPresentation() { }

    public static View from(EventLineups lineups) {
        return from(lineups, null, null);
    }

    public static View from(EventLineups lineups, String homeName, String awayName) {
        return new View(lineups.confirmed(), lineups.confirmed() ? "Confirmée" : "Provisoire",
                List.of(team(lineups.home(), homeName), team(lineups.away(), awayName)));
    }

    private static Team team(TeamLineup lineup, String name) {
        String side = lineup.side().name();
        String sideLabel = lineup.side() == LineupSide.HOME ? "Domicile" : "Extérieur";
        var starters = new LinkedHashMap<String, List<Player>>();
        var substitutes = new ArrayList<Player>();
        int starterCount = 0;
        for (EventLineupPlayer source : lineup.players()) {
            Player player = player(side, source);
            if (source.starter()) {
                starters.computeIfAbsent(groupKey(source), ignored -> new ArrayList<>()).add(player);
                starterCount++;
            } else {
                substitutes.add(player);
            }
        }
        List<Group> groups = GROUP_ORDER.stream().filter(starters::containsKey)
                .map(key -> new Group(key, groupLabel(key), List.copyOf(starters.get(key)))).toList();
        return new Team(side, name == null || name.isBlank() ? sideLabel : name, sideLabel,
                lineup.formation().orElse("Formation non renseignée"), starterCount, substitutes.size(),
                groups, List.copyOf(substitutes), lineup.missingPlayers()
                .map(values -> missingPlayers(side, values)).orElse(null));
    }

    private static Player player(String side, EventLineupPlayer source) {
        return new Player(side + ":" + source.providerPlayerId(), source.name(),
                source.shirtNumber().map(String::valueOf).orElse("—"),
                source.position().map(LineupsPresentation::positionLabel).orElse("Poste non renseigné"),
                source.starter() ? "Titulaire" : "Remplaçant", source.captain().orElse(false),
                source.statistics().map(PlayerStatisticsPresentation::from).orElse(null));
    }

    private static List<MissingPlayer> missingPlayers(String side, List<MissingLineupPlayer> values) {
        Map<Long, Integer> occurrences = new LinkedHashMap<>();
        return values.stream().map(value -> {
            int occurrence = occurrences.merge(value.providerPlayerId(), 1, Integer::sum);
            String key = side + ":" + value.providerPlayerId() + (occurrence == 1 ? "" : "|duplicate:" + occurrence);
            return missingPlayer(key, value);
        }).toList();
    }

    private static MissingPlayer missingPlayer(String key, MissingLineupPlayer source) {
        return new MissingPlayer(key, source.name(),
                source.shirtNumber().map(String::valueOf).orElse("—"),
                source.position().map(LineupsPresentation::positionLabel).orElse("Poste non renseigné"),
                source.description().map(value -> switch (value) {
                    case "Achilles Tendon Injury" -> "Blessure au tendon d’Achille";
                    case "Sprained Knee Injury" -> "Entorse du genou";
                    case "Cruciate Ligament Injury" -> "Blessure au ligament croisé";
                    case "Dislocated Shoulder" -> "Luxation de l’épaule";
                    case "Hip Injury" -> "Blessure à la hanche";
                    case "Thigh Injury" -> "Blessure à la cuisse";
                    case "Toe Injury" -> "Blessure à un orteil";
                    case "Muscle Injury" -> "Blessure musculaire";
                    case "Unknown" -> "Motif inconnu";
                    case "yellow_or_red_card_suspension" -> "Suspension liée aux cartons";
                    default -> value;
                }).orElse("Motif non renseigné"),
                source.expectedEndDate().map(value -> value.format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm XXX", Locale.FRANCE))).orElse("—"),
                source.type().orElse("—"), source.reason().map(String::valueOf).orElse("—"),
                source.externalType().map(String::valueOf).orElse("—"));
    }

    private static String groupKey(EventLineupPlayer player) {
        return player.position().filter(value -> switch (value) {
            case "G", "D", "M", "F" -> true;
            default -> false;
        }).orElse("UNKNOWN");
    }

    private static String groupLabel(String key) {
        return switch (key) {
            case "G" -> "Gardiens";
            case "D" -> "Défenseurs";
            case "M" -> "Milieux";
            case "F" -> "Attaquants";
            default -> "Poste non renseigné ou autre";
        };
    }

    private static String positionLabel(String position) {
        return switch (position) {
            case "G" -> "Gardien";
            case "D" -> "Défenseur";
            case "M" -> "Milieu";
            case "F" -> "Attaquant";
            default -> position;
        };
    }

    public record View(boolean confirmed, String confirmationLabel, List<Team> teams) { }
    public record Team(String side, String name, String sideLabel, String formation,
                       int starterCount, int substituteCount, List<Group> starterGroups, List<Player> substitutes,
                       List<MissingPlayer> missingPlayers) {
        public Team(String side, String name, String sideLabel, String formation, int starterCount,
                    int substituteCount, List<Group> starterGroups, List<Player> substitutes) {
            this(side, name, sideLabel, formation, starterCount, substituteCount, starterGroups, substitutes, null);
        }
    }
    public record Group(String key, String label, List<Player> players) { }
    public record Player(String key, String name, String shirtNumber, String positionLabel, String roleLabel,
                         boolean captain, PlayerStatisticsPresentation.View statistics) {
        public Player(String key, String name, String shirtNumber, String positionLabel, String roleLabel) {
            this(key, name, shirtNumber, positionLabel, roleLabel, false, null);
        }
        public Player(String key, String name, String shirtNumber, String positionLabel, String roleLabel, boolean captain) {
            this(key, name, shirtNumber, positionLabel, roleLabel, captain, null);
        }
    }
    public record MissingPlayer(String key, String name, String shirtNumber, String positionLabel,
                                String description, String expectedReturn, String type, String reason, String externalType) { }
}
