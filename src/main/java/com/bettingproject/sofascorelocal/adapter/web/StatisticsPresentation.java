package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Display-only interpretation of persisted strings; never alters normalized evidence. */
public final class StatisticsPresentation {
    private static final Pattern PERCENT = Pattern.compile("([+-]?\\d+(?:[.,]\\d+)?)\\s*%");
    private static final Pattern RATIO = Pattern.compile(
            "([+-]?\\d+)\\s*/\\s*([+-]?\\d+)(?:\\s*\\(([+-]?\\d+(?:[.,]\\d+)?)\\s*%\\))?");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private StatisticsPresentation() { }

    public static View from(EventStatistics statistics) {
        var grouped = new LinkedHashMap<String, LinkedHashMap<String, java.util.ArrayList<Metric>>>();
        for (EventStatisticMetric metric : statistics.metrics()) {
            grouped.computeIfAbsent(metric.period(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(metric.groupName(), ignored -> new java.util.ArrayList<>()).add(metric(metric));
        }
        List<Period> periods = grouped.entrySet().stream().map(period -> new Period(period.getKey(),
                periodLabel(period.getKey()), period.getValue().entrySet().stream()
                .map(group -> new Group(group.getKey(), List.copyOf(group.getValue()))).toList())).toList();
        String selected = grouped.containsKey("ALL") ? "ALL" : periods.isEmpty() ? "" : periods.getFirst().code();
        return new View(periods, selected);
    }

    private static String periodLabel(String code) {
        return switch (code) {
            case "ALL" -> "ALL · Ensemble du match";
            case "1ST" -> "1ST · Première période";
            case "2ND" -> "2ND · Seconde période";
            default -> code;
        };
    }

    private static String groupLabel(String source) {
        return switch (source) {
            case "Match overview" -> "Vue d’ensemble du match";
            case "Shots" -> "Tirs";
            case "Attack" -> "Attaque";
            case "Passes" -> "Passes";
            case "Duels" -> "Duels";
            case "Defending" -> "Défense";
            case "Goalkeeping" -> "Gardiens";
            default -> source;
        };
    }

    private static String metricLabel(String source) {
        // Match only known labels: a new provider name remains visible verbatim for review.
        return switch (source) {
            case "Ball possession" -> "Possession du ballon";
            case "Expected goals" -> "Buts attendus (xG)";
            case "Big chances" -> "Grosses occasions";
            case "Total shots" -> "Total des tirs";
            case "Goalkeeper saves" -> "Arrêts du gardien";
            case "Corner kicks" -> "Corners";
            case "Fouls" -> "Fautes";
            case "Passes" -> "Passes";
            case "Tackles" -> "Tacles";
            case "Free kicks" -> "Coups francs";
            case "Yellow cards" -> "Cartons jaunes";
            case "Red cards" -> "Cartons rouges";
            case "Average rating" -> "Note moyenne";
            case "Shots on target" -> "Tirs cadrés";
            case "Expected goals on target" -> "Buts attendus sur tirs cadrés (xGOT)";
            case "Shots off target" -> "Tirs non cadrés";
            case "Blocked shots" -> "Tirs contrés";
            case "Shots inside box" -> "Tirs dans la surface";
            case "Shots outside box" -> "Tirs hors de la surface";
            case "Hit woodwork" -> "Montants touchés";
            case "Big chances scored" -> "Grosses occasions converties";
            case "Big chances missed" -> "Grosses occasions manquées";
            case "Through balls" -> "Passes en profondeur";
            case "Touches in penalty area" -> "Touches de balle dans la surface";
            case "Fouled in final third" -> "Fautes subies dans le tiers offensif";
            case "Offsides" -> "Hors-jeu";
            case "Accurate passes" -> "Passes réussies";
            case "Throw-ins" -> "Touches";
            case "Final third entries" -> "Entrées dans le tiers offensif";
            case "Final third phase" -> "Phase dans le tiers offensif";
            case "Long balls" -> "Longs ballons";
            case "Crosses" -> "Centres";
            case "Duels" -> "Duels";
            case "Ground duels" -> "Duels au sol";
            case "Aerial duels" -> "Duels aériens";
            case "Dribbles" -> "Dribbles";
            case "Dispossessed" -> "Ballons perdus";
            case "Tackles won" -> "Tacles gagnés";
            case "Total tackles" -> "Total des tacles";
            case "Interceptions" -> "Interceptions";
            case "Recoveries" -> "Récupérations";
            case "Clearances" -> "Dégagements";
            case "Errors lead to a shot" -> "Erreurs menant à un tir";
            case "Errors lead to a goal" -> "Erreurs menant à un but";
            case "Total saves" -> "Total des arrêts";
            case "Goals prevented" -> "Buts évités";
            case "Big saves" -> "Arrêts décisifs";
            case "High claims" -> "Ballons aériens captés";
            case "Punches" -> "Dégagements des poings";
            case "Goal kicks" -> "Coups de pied de but";
            case "Penalty saves" -> "Penalties arrêtés";
            default -> source;
        };
    }

    private static Metric metric(EventStatisticMetric metric) {
        boolean possession = "ballPossession".equals(metric.metricCode());
        Value home = value(metric.homeValue(), possession);
        Value away = value(metric.awayValue(), possession);
        BigDecimal possessionHome = null;
        String note = "";
        if (possession) {
            if (home.percentage() != null && away.percentage() != null) {
                if (home.percentage().add(away.percentage()).compareTo(HUNDRED) == 0) {
                    possessionHome = home.percentage();
                } else {
                    note = "Possession incohérente : le total des valeurs observées diffère de 100 %.";
                }
            } else {
                note = "Comparaison de possession indisponible : deux pourcentages valides sont nécessaires.";
            }
        }
        return new Metric(metric.metricCode(), metricLabel(metric.metricName()), home, away, possessionHome, note);
    }

    static Value value(Optional<String> source, boolean possession) {
        if (source.isEmpty()) return new Value("—", "Valeur absente", "MISSING", null, false);
        String text = source.orElseThrow();
        if (possession) {
            var percent = PERCENT.matcher(text);
            if (!percent.matches()) return new Value(text, "Pourcentage de possession invalide", "INVALID", null, false);
            BigDecimal amount = decimal(percent.group(1));
            return validPercent(amount) ? new Value(text, "", "VALUE", amount, false)
                    : new Value(text, "Pourcentage hors de l’intervalle 0–100", "INVALID", null, false);
        }
        if (!text.contains("/")) return new Value(text, "", "VALUE", null, false);
        var ratio = RATIO.matcher(text);
        if (!ratio.matches()) return new Value(text, "Ratio non interprétable", "INVALID", null, true);
        BigDecimal numerator = decimal(ratio.group(1));
        BigDecimal denominator = decimal(ratio.group(2));
        if (numerator.signum() < 0 || denominator.signum() < 0 || numerator.compareTo(denominator) > 0) {
            return new Value(text, "Ratio invalide : attendu 0 ≤ X ≤ Y", "INVALID", null, true);
        }
        if (denominator.signum() == 0) {
            return new Value(text, "Aucune tentative (0/0) · taux non défini", "UNDEFINED", null, true);
        }
        BigDecimal percentage = numerator.multiply(HUNDRED).divide(denominator, 6, RoundingMode.HALF_UP);
        String note = numerator.toPlainString() + " sur " + denominator.toPlainString() + " · "
                + percentage.setScale(0, RoundingMode.HALF_UP).toPlainString() + " % calculés";
        if (ratio.group(3) != null) {
            BigDecimal declared = decimal(ratio.group(3));
            if (!validPercent(declared) || declared.subtract(percentage).abs().compareTo(new BigDecimal("0.5")) > 0) {
                return new Value(text, "Pourcentage fourni incohérent avec X/Y", "INVALID", null, true);
            }
        }
        return new Value(text, note, "VALUE", percentage, true);
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value.replace(',', '.')); }
    private static boolean validPercent(BigDecimal value) {
        return value.signum() >= 0 && value.compareTo(HUNDRED) <= 0;
    }

    public record View(List<Period> periods, String defaultPeriod) { }
    public record Period(String code, String label, List<Group> groups) { }
    public record Group(String name, String label, List<Metric> metrics) {
        public Group(String name, List<Metric> metrics) { this(name, groupLabel(name), metrics); }
    }
    public record Metric(String code, String label, Value home, Value away,
                         BigDecimal possessionHome, String note) { }
    public record Value(String text, String note, String state, BigDecimal percentage, boolean ratio) { }
}
