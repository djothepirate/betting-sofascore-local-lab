package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.MissingLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.math.BigDecimal;

/** Display-only roster grouping. Rendering escapes text; no pitch position is inferred from formation. */
public final class LineupsPresentation {
    private static final List<String> GROUP_ORDER = List.of("G", "D", "M", "F", "UNKNOWN");

    private LineupsPresentation() { }

    public static View from(EventLineups lineups) {
        return from(lineups, null, null, LineupCountryOverlay.empty());
    }

    public static View from(EventLineups lineups, String homeName, String awayName) {
        return from(lineups, homeName, awayName, LineupCountryOverlay.empty());
    }

    public static View from(EventLineups lineups, String homeName, String awayName,
                            LineupCountryOverlay countryOverlay) {
        LineupCountryOverlay overlay = countryOverlay == null
                ? LineupCountryOverlay.empty() : countryOverlay;
        return new View(lineups.confirmed(), lineups.confirmed() ? "Confirmée" : "Provisoire",
                List.of(team(lineups.home(), homeName, overlay), team(lineups.away(), awayName, overlay)));
    }

    private static Team team(TeamLineup lineup, String name, LineupCountryOverlay overlay) {
        String side = lineup.side().name();
        String sideLabel = lineup.side() == LineupSide.HOME ? "Domicile" : "Extérieur";
        var starters = new LinkedHashMap<String, List<Player>>();
        var substitutes = new ArrayList<Player>();
        int starterCount = 0;
        for (EventLineupPlayer source : lineup.players()) {
            Player player = player(lineup.side(), source, overlay);
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
                .map(values -> missingPlayers(lineup.side(), values, overlay)).orElse(null));
    }

    private static Player player(LineupSide side, EventLineupPlayer source, LineupCountryOverlay overlay) {
        return new Player(side.name() + ":" + source.providerPlayerId(), source.name(),
                source.shirtNumber().map(String::valueOf).orElse("—"),
                source.position().map(LineupsPresentation::positionLabel).orElse("Poste non renseigné"),
                source.starter() ? "Titulaire" : "Remplaçant", source.captain().orElse(false),
                source.statistics().map(PlayerStatisticsPresentation::from).orElse(null),
                CountryPresentation.of(preferredCountry(source.country(),
                        overlay.rosterCountry(side, source.providerPlayerId()))), achievements(source));
    }

    private static List<Achievement> achievements(EventLineupPlayer player) {
        var result = new ArrayList<Achievement>();
        player.statistics().ifPresent(statistics -> {
            achievement(result, "goals", statistics.values().get("goals"), "but", "buts");
            achievement(result, "assists", statistics.values().get("goalAssist"), "passe décisive", "passes décisives");
        });
        return List.copyOf(result);
    }

    private static void achievement(List<Achievement> result, String key, BigDecimal value, String singular, String plural) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) return;
        String count = value.toBigIntegerExact().toString();
        int repeats = value.compareTo(BigDecimal.valueOf(5)) <= 0 ? value.intValueExact() : 1;
        result.add(new Achievement(key, count, count + " " + (value.compareTo(BigDecimal.ONE) == 0 ? singular : plural),
                java.util.stream.IntStream.range(0, repeats).boxed().toList(), repeats == 1 && !count.equals("1")));
    }

    private static List<MissingPlayer> missingPlayers(LineupSide side, List<MissingLineupPlayer> values,
                                                      LineupCountryOverlay overlay) {
        Map<Long, Integer> occurrences = new LinkedHashMap<>();
        return values.stream().map(value -> {
            int occurrence = occurrences.merge(value.providerPlayerId(), 1, Integer::sum);
            String key = side.name() + ":" + value.providerPlayerId()
                    + (occurrence == 1 ? "" : "|duplicate:" + occurrence);
            return missingPlayer(key, value, side, overlay);
        }).toList();
    }

    private static MissingPlayer missingPlayer(String key, MissingLineupPlayer source, LineupSide side,
                                               LineupCountryOverlay overlay) {
        return new MissingPlayer(key, source.name(),
                source.shirtNumber().map(String::valueOf).orElse("—"),
                source.position().map(LineupsPresentation::positionLabel).orElse("Poste non renseigné"),
                source.description().map(LineupsPresentation::missingDescriptionLabel).orElse("Motif non renseigné"),
                source.expectedEndDate().map(value -> value.format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm XXX", Locale.FRANCE))).orElse("—"),
                source.type().map(LineupsPresentation::missingTypeLabel).orElse("—"), source.reason().map(String::valueOf).orElse("—"),
                source.externalType().map(String::valueOf).orElse("—"), CountryPresentation.of(preferredCountry(
                        source.country(), overlay.missingPlayerCountry(side, source.providerPlayerId()))));
    }

    private static Optional<ProviderCountry> preferredCountry(
            Optional<ProviderCountry> normalized,
            Optional<ProviderCountry> overlay) {
        return normalized.isPresent() ? normalized : overlay;
    }

    private static String missingDescriptionLabel(String value) {
        return switch (value) {
            case "Achilles Tendon Injury" -> "Blessure au tendon d’Achille";
            case "Ankle Injury" -> "Blessure à la cheville";
            case "Back Injury" -> "Blessure au dos";
            case "Broken Ankle", "Broken ankle" -> "Fracture de la cheville";
            case "Calf Injury" -> "Blessure au mollet";
            case "Cruciate Ligament Injury" -> "Blessure au ligament croisé";
            case "Dislocated Shoulder" -> "Luxation de l’épaule";
            case "Groin Injury" -> "Blessure à l’aine";
            case "Hamstring Injury" -> "Blessure aux ischio-jambiers";
            case "Heart Problems" -> "Problèmes cardiaques";
            case "Hernia" -> "Hernie";
            case "Hip Injury" -> "Blessure à la hanche";
            case "Knee Injury" -> "Blessure au genou";
            case "Knock Injury" -> "Coup";
            case "Leg Injury" -> "Blessure à la jambe";
            case "Ligament Injury" -> "Blessure aux ligaments";
            case "Meniscus Injury" -> "Blessure au ménisque";
            case "Muscle Injury" -> "Blessure musculaire";
            case "Neck Injury" -> "Blessure au cou";
            case "Shoulder Injury" -> "Blessure à l’épaule";
            case "Strain Injury" -> "Blessure à l’entraînement";
            case "Sprained Knee Injury" -> "Entorse du genou";
            case "Thigh Injury" -> "Blessure à la cuisse";
            case "Toe Injury" -> "Blessure à un orteil";
            case "Unknown" -> "Motif inconnu";
            case "yellow_or_red_card_suspension" -> "Suspension liée aux cartons";
            case "red_card_suspension" -> "Suspension après carton rouge";
            default -> value;
        };
    }

    private static String missingTypeLabel(String value) {
        return "missing".equals(value) ? "Indisponible" : value;
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
                         boolean captain, PlayerStatisticsPresentation.View statistics,
                         CountryPresentation.View country, List<Achievement> achievements) {
        public Player(String key, String name, String shirtNumber, String positionLabel, String roleLabel,
                      boolean captain, PlayerStatisticsPresentation.View statistics) {
            this(key, name, shirtNumber, positionLabel, roleLabel, captain, statistics,
                    CountryPresentation.of(Optional.empty()), List.of());
        }
        public boolean hasStatistics() {
            return statistics != null && (statistics.groups().stream().anyMatch(group -> !group.metrics().isEmpty())
                    || !statistics.ratingVersions().isEmpty());
        }
        public Player(String key, String name, String shirtNumber, String positionLabel, String roleLabel) {
            this(key, name, shirtNumber, positionLabel, roleLabel, false, null);
        }
        public Player(String key, String name, String shirtNumber, String positionLabel, String roleLabel, boolean captain) {
            this(key, name, shirtNumber, positionLabel, roleLabel, captain, null);
        }
    }
    public record Achievement(String key, String count, String label, List<Integer> repeats, boolean compact) { }
    public record MissingPlayer(String key, String name, String shirtNumber, String positionLabel,
                                String description, String expectedReturn, String type, String reason, String externalType,
                                CountryPresentation.View country) {
        public MissingPlayer(String key, String name, String shirtNumber, String positionLabel,
                             String description, String expectedReturn, String type, String reason, String externalType) {
            this(key, name, shirtNumber, positionLabel, description, expectedReturn, type, reason, externalType,
                    CountryPresentation.of(Optional.empty()));
        }
    }
}
