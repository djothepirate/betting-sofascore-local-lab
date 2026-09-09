package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only labels and display rounding. No absent metric is replaced with zero. */
public final class PlayerStatisticsPresentation {
    private static final List<String> GROUP_ORDER = List.of("Vue d’ensemble", "Attaque", "Passes",
            "Duels et discipline", "Défense", "Gardien", "Conservation et progression", "Indices fournisseur", "Autres données");
    private PlayerStatisticsPresentation() { }

    public static View from(PlayerMatchStatistics source) {
        Map<String, List<Metric>> groups = new LinkedHashMap<>();
        source.values().forEach((key, value) -> {
            Label label = label(key);
            groups.computeIfAbsent(label.group(), ignored -> new ArrayList<>())
                    .add(new Metric(key, label.text(), format(value)));
        });
        return new View(GROUP_ORDER.stream().filter(groups::containsKey).map(key ->
                new Group(key, key, List.copyOf(groups.get(key)))).toList(),
                source.ratingVersions().entrySet().stream().map(entry ->
                        new Metric(entry.getKey(), switch (entry.getKey()) {
                            case "original" -> "Originale";
                            case "alternative" -> "Alternative";
                            default -> entry.getKey();
                        }, format(entry.getValue()))).toList());
    }

    private static String format(BigDecimal value) {
        if (value.signum() != 0 && value.abs().compareTo(new BigDecimal("0.01")) < 0)
            return value.signum() > 0 ? "< 0,01" : "> -0,01";
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static Label label(String key) {
        return switch (key) {
            case "rating" -> new Label("Vue d’ensemble", "Note fournisseur");
            case "minutesPlayed" -> new Label("Vue d’ensemble", "Minutes jouées");
            case "touches" -> new Label("Vue d’ensemble", "Touches de balle");
            case "goals" -> new Label("Attaque", "Buts");
            case "goalAssist" -> new Label("Attaque", "Passes décisives");
            case "totalShots" -> new Label("Attaque", "Total des tirs");
            case "onTargetScoringAttempt" -> new Label("Attaque", "Tirs cadrés");
            case "shotOffTarget" -> new Label("Attaque", "Tirs non cadrés");
            case "blockedScoringAttempt" -> new Label("Attaque", "Tirs contrés");
            case "expectedGoals" -> new Label("Attaque", "Buts attendus (xG)");
            case "expectedGoalsOnTarget" -> new Label("Attaque", "Buts attendus sur tirs cadrés (xGOT)");
            case "expectedAssists" -> new Label("Attaque", "Passes décisives attendues (xA)");
            case "bigChanceCreated" -> new Label("Attaque", "Grosses occasions créées");
            case "keyPass" -> new Label("Attaque", "Passes clés");
            case "totalOffside" -> new Label("Attaque", "Hors-jeu");
            case "totalPass" -> new Label("Passes", "Passes tentées");
            case "accuratePass" -> new Label("Passes", "Passes réussies");
            case "totalLongBalls" -> new Label("Passes", "Longs ballons tentés");
            case "accurateLongBalls" -> new Label("Passes", "Longs ballons réussis");
            case "totalCross" -> new Label("Passes", "Centres tentés");
            case "accurateCross" -> new Label("Passes", "Centres réussis");
            case "totalOwnHalfPasses" -> new Label("Passes", "Passes dans sa moitié de terrain");
            case "accurateOwnHalfPasses" -> new Label("Passes", "Passes réussies dans sa moitié de terrain");
            case "totalOppositionHalfPasses" -> new Label("Passes", "Passes dans la moitié adverse");
            case "accurateOppositionHalfPasses" -> new Label("Passes", "Passes réussies dans la moitié adverse");
            case "duelWon" -> new Label("Duels et discipline", "Duels gagnés");
            case "duelLost" -> new Label("Duels et discipline", "Duels perdus");
            case "aerialWon" -> new Label("Duels et discipline", "Duels aériens gagnés");
            case "aerialLost" -> new Label("Duels et discipline", "Duels aériens perdus");
            case "totalContest" -> new Label("Duels et discipline", "Dribbles tentés");
            case "wonContest" -> new Label("Duels et discipline", "Dribbles réussis");
            case "challengeLost" -> new Label("Duels et discipline", "Duels défensifs perdus");
            case "fouls" -> new Label("Duels et discipline", "Fautes commises");
            case "wasFouled" -> new Label("Duels et discipline", "Fautes subies");
            case "dispossessed" -> new Label("Conservation et progression", "Dépossessions");
            case "possessionLostCtrl" -> new Label("Conservation et progression", "Pertes de possession");
            case "unsuccessfulTouch" -> new Label("Conservation et progression", "Contrôles manqués");
            case "ballCarriesCount" -> new Label("Conservation et progression", "Conduites de balle");
            case "progressiveBallCarriesCount" -> new Label("Conservation et progression", "Conduites progressives");
            case "totalBallCarriesDistance" -> new Label("Conservation et progression", "Distance de conduite (unité non précisée)");
            case "totalProgressiveBallCarriesDistance" -> new Label("Conservation et progression", "Distance de conduite progressive (unité non précisée)");
            case "bestBallCarryProgression" -> new Label("Conservation et progression", "Meilleure progression en conduite (unité non précisée)");
            case "totalProgression" -> new Label("Conservation et progression", "Progression totale (unité non précisée)");
            case "totalTackle" -> new Label("Défense", "Tacles");
            case "wonTackle" -> new Label("Défense", "Tacles gagnés");
            case "interceptionWon" -> new Label("Défense", "Interceptions");
            case "ballRecovery" -> new Label("Défense", "Récupérations");
            case "totalClearance" -> new Label("Défense", "Dégagements");
            case "outfielderBlock" -> new Label("Défense", "Tirs bloqués par le joueur");
            case "saves" -> new Label("Gardien", "Arrêts");
            case "punches" -> new Label("Gardien", "Dégagements des poings");
            case "goodHighClaim" -> new Label("Gardien", "Ballons aériens captés");
            case "goalsPrevented" -> new Label("Gardien", "Buts évités");
            case "keeperSaveValue" -> new Label("Indices fournisseur", "Valeur des arrêts");
            case "defensiveValueNormalized" -> new Label("Indices fournisseur", "Valeur défensive normalisée");
            case "dribbleValueNormalized" -> new Label("Indices fournisseur", "Valeur des dribbles normalisée");
            case "goalkeeperValueNormalized" -> new Label("Indices fournisseur", "Valeur du gardien normalisée");
            case "passValueNormalized" -> new Label("Indices fournisseur", "Valeur des passes normalisée");
            case "shotValueNormalized" -> new Label("Indices fournisseur", "Valeur des tirs normalisée");
            default -> new Label("Autres données", key);
        };
    }

    private record Label(String group, String text) { }
    public record View(List<Group> groups, List<Metric> ratingVersions) { }
    public record Group(String key, String label, List<Metric> metrics) { }
    public record Metric(String key, String label, String value) { }
}
