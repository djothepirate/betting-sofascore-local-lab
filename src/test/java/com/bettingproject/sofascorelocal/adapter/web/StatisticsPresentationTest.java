package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsPresentationTest {
    @Test
    void groupsOnlyObservedPeriodsWithoutSummingOrInventingMissingMetrics() {
        var source = new EventStatistics(900001L, List.of(
                metric("1ST", "Tirs", "shots", "3", "1"),
                metric("ALL", "Tirs", "shots", "4", "2"),
                metric("ALL", "Passes", "passes", "82", "75"),
                metric("EXTRA", "Tirs", "shots", "1", null)));
        var view = StatisticsPresentation.from(source);
        assertThat(view.defaultPeriod()).isEqualTo("ALL");
        assertThat(view.periods()).extracting(StatisticsPresentation.Period::code)
                .containsExactly("1ST", "ALL", "EXTRA");
        assertThat(view.periods().get(1).groups()).extracting(StatisticsPresentation.Group::name)
                .containsExactly("Tirs", "Passes");
        assertThat(view.periods().get(1).groups().getFirst().metrics().getFirst().home().text()).isEqualTo("4");
        assertThat(source.metrics().getFirst().homeValue()).contains("3");
    }

    @Test
    void selectsFirstObservedPeriodWhenAllIsAbsentAndKeepsEmptyCollectionExplicit() {
        var first = StatisticsPresentation.from(new EventStatistics(900001L,
                List.of(metric("2ND", "Tirs", "shots", "0", "1"))));
        assertThat(first.defaultPeriod()).isEqualTo("2ND");
        assertThat(StatisticsPresentation.from(new EventStatistics(900001L, List.of())).periods()).isEmpty();
    }

    @Test
    void absenceIsDistinctFromObservedZeroAndUndefinedRatio() {
        var missing = StatisticsPresentation.value(Optional.empty(), false);
        var zero = StatisticsPresentation.value(Optional.of("0"), false);
        var undefined = StatisticsPresentation.value(Optional.of("0/0 (0%)"), false);
        assertThat(missing.state()).isEqualTo("MISSING");
        assertThat(missing.note()).isEqualTo("Valeur absente");
        assertThat(zero.state()).isEqualTo("VALUE");
        assertThat(zero.text()).isEqualTo("0");
        assertThat(undefined.state()).isEqualTo("UNDEFINED");
        assertThat(undefined.percentage()).isNull();
        assertThat(undefined.text()).isEqualTo("0/0 (0%)");
        assertThat(undefined.note()).contains("taux non défini");
    }

    @ParameterizedTest
    @CsvSource({"4/9 (44%),44.444444", "3/4 (75%),75", "0/9 (0%),0", "9/9,100",
            "2147483648/4294967296,50", " 4 / 9 ,44.444444"})
    void ratiosPreserveSourceAndCalculateBoundedPercentage(String raw, String expected) {
        var value = StatisticsPresentation.value(Optional.of(raw), false);
        assertThat(value.text()).isEqualTo(raw);
        assertThat(value.state()).isEqualTo("VALUE");
        assertThat(value.ratio()).isTrue();
        assertThat(value.percentage()).isEqualByComparingTo(expected);
        assertThat(value.note()).contains("calculés");
    }

    @ParameterizedTest
    @CsvSource({"4/0", "-1/9", "1/-9", "10/9", "4/9 (90%)", "4/9 (101%)", "a/b", "1.5/2"})
    void invalidRatiosKeepSourceButCannotCreateAGraphic(String raw) {
        var value = StatisticsPresentation.value(Optional.of(raw), false);
        assertThat(value.text()).isEqualTo(raw);
        assertThat(value.state()).isEqualTo("INVALID");
        assertThat(value.percentage()).isNull();
        assertThat(value.note()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"0%,100%,0", "100%,0%,100", "63%,37%,63", "50%,50%,50"})
    void possessionUsesExactObservedPercentagesIncludingZero(String home, String away, String expected) {
        var row = row(metric("ALL", "Match overview", "ballPossession", home, away));
        assertThat(row.possessionHome()).isEqualByComparingTo(expected);
        assertThat(row.home().text()).isEqualTo(home);
        assertThat(row.away().text()).isEqualTo(away);
        assertThat(row.note()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0%,0%", "61%,40%", "-1%,101%", "101%,0%", "unknown,50%", ",50%"})
    void invalidOrIncompletePossessionDoesNotNormalizeItsSumToOneHundred(String home, String away) {
        var row = row(metric("ALL", "Match overview", "ballPossession", home, away));
        assertThat(row.possessionHome()).isNull();
        assertThat(row.note()).isNotBlank();
    }

    @Test
    void preservesUnrecognizedSourceLabelsAndValuesForEscapedRendering() {
        var row = row(metric("<period>", "<group>", "unknown", "<img src=x onerror=alert(1)>", "0"));
        assertThat(row.home().text()).isEqualTo("<img src=x onerror=alert(1)>");
        assertThat(row.possessionHome()).isNull();
        assertThat(row.home().percentage()).isNull();
    }

    private static StatisticsPresentation.Metric row(EventStatisticMetric metric) {
        return StatisticsPresentation.from(new EventStatistics(900001L, List.of(metric)))
                .periods().getFirst().groups().getFirst().metrics().getFirst();
    }

    private static EventStatisticMetric metric(String period, String group, String code, String home, String away) {
        return new EventStatisticMetric(period, group, code, code, Optional.ofNullable(home), Optional.ofNullable(away));
    }
}
