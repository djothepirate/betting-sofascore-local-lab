package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.PlayerMatchStatistics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerStatisticsPresentationTest {
    @Test
    void rendersOnlyObservedValuesWithIndependentRatingsAndDisplayOnlyRounding() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("rating", new BigDecimal("6.25"));
        values.put("goals", BigDecimal.ZERO);
        values.put("expectedGoals", new BigDecimal("0.125"));
        values.put("totalBallCarriesDistance", new BigDecimal("123.456"));
        values.put("futureMetric", new BigDecimal("2.345"));
        values.put("expectedAssists", new BigDecimal("0.001"));
        values.put("goalsPrevented", new BigDecimal("-0.001"));
        var source = new PlayerMatchStatistics(values, Map.of("alternative", new BigDecimal("9.1")));
        var view = PlayerStatisticsPresentation.from(source);
        var metrics = view.groups().stream().flatMap(group -> group.metrics().stream()).toList();
        assertThat(metrics).extracting(PlayerStatisticsPresentation.Metric::key)
                .containsExactlyInAnyOrderElementsOf(values.keySet());
        assertThat(metrics).filteredOn(metric -> metric.key().equals("rating")).singleElement()
                .satisfies(metric -> assertThat(metric.value()).isEqualTo("6,25"));
        assertThat(metrics).filteredOn(metric -> metric.key().equals("expectedGoals")).singleElement()
                .satisfies(metric -> assertThat(metric.value()).isEqualTo("0,13"));
        assertThat(metrics).filteredOn(metric -> metric.key().equals("expectedAssists")).singleElement()
                .satisfies(metric -> assertThat(metric.value()).isEqualTo("< 0,01"));
        assertThat(metrics).filteredOn(metric -> metric.key().equals("goalsPrevented")).singleElement()
                .satisfies(metric -> assertThat(metric.value()).isEqualTo("> -0,01"));
        assertThat(metrics).filteredOn(metric -> metric.key().equals("totalBallCarriesDistance")).singleElement()
                .satisfies(metric -> {
                    assertThat(metric.value()).isEqualTo("123,46");
                    assertThat(metric.label()).contains("unité non précisée");
                });
        assertThat(metrics).filteredOn(metric -> metric.key().equals("futureMetric")).singleElement()
                .satisfies(metric -> assertThat(metric.label()).isEqualTo("futureMetric"));
        assertThat(view.ratingVersions()).containsExactly(
                new PlayerStatisticsPresentation.Metric("alternative", "Alternative", "9,1"));
        assertThat(view.groups()).extracting(PlayerStatisticsPresentation.Group::label)
                .containsExactly("Vue d’ensemble", "Attaque", "Gardien", "Conservation et progression", "Autres données");
        assertThat(source.values().get("expectedGoals")).isEqualByComparingTo("0.125");
        assertThat(source.values()).doesNotContainKey("minutesPlayed");
    }

    @Test
    void emptyStatisticsRemainEmptyAndAlternativeRatingNeverCreatesARating() {
        var empty = PlayerStatisticsPresentation.from(new PlayerMatchStatistics(Map.of(), Map.of()));
        assertThat(empty.groups()).isEmpty();
        assertThat(empty.ratingVersions()).isEmpty();
        var alternativeOnly = PlayerStatisticsPresentation.from(new PlayerMatchStatistics(Map.of(),
                Map.of("alternative", new BigDecimal("8.9"))));
        assertThat(alternativeOnly.groups()).isEmpty();
        assertThat(alternativeOnly.ratingVersions()).singleElement()
                .satisfies(metric -> assertThat(metric.value()).isEqualTo("8,9"));
    }

    @Test
    void groupsNewlyObservedKeeperAttackAndDefenceMetricsWithFrenchLabels() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("accurateKeeperSweeper", BigDecimal.ONE);
        values.put("totalKeeperSweeper", new BigDecimal("2"));
        values.put("hitWoodwork", BigDecimal.ONE);
        values.put("errorLeadToAShot", new BigDecimal("3"));
        values.put("errorLeadToAGoal", new BigDecimal("4"));
        values.put("clearanceOffLine", new BigDecimal("5"));

        var metrics = PlayerStatisticsPresentation.from(new PlayerMatchStatistics(values, Map.of()))
                .groups().stream().flatMap(group -> group.metrics().stream()).toList();

        assertThat(metrics).containsExactlyInAnyOrder(
                new PlayerStatisticsPresentation.Metric("accurateKeeperSweeper", "Sorties du gardien (réussies)", "1"),
                new PlayerStatisticsPresentation.Metric("totalKeeperSweeper", "Sorties du gardien (total)", "2"),
                new PlayerStatisticsPresentation.Metric("hitWoodwork", "Tir sur un montant (poteau ou barre transversale)", "1"),
                new PlayerStatisticsPresentation.Metric("errorLeadToAShot", "Erreur menant à un tir", "3"),
                new PlayerStatisticsPresentation.Metric("errorLeadToAGoal", "Erreur provoquant un but", "4"),
                new PlayerStatisticsPresentation.Metric("clearanceOffLine", "Sauvetages sur la ligne", "5"));
        assertThat(PlayerStatisticsPresentation.from(new PlayerMatchStatistics(values, Map.of())).groups())
                .extracting(PlayerStatisticsPresentation.Group::label)
                .containsExactly("Attaque", "Défense", "Gardien");
        assertThat(values).containsEntry("clearanceOffLine", new BigDecimal("5"));
    }
}
