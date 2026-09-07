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
        return new Metric(metric.metricCode(), metric.metricName(), home, away, possessionHome, note);
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
    public record Group(String name, List<Metric> metrics) { }
    public record Metric(String code, String label, Value home, Value away,
                         BigDecimal possessionHome, String note) { }
    public record Value(String text, String note, String state, BigDecimal percentage, boolean ratio) { }
}
