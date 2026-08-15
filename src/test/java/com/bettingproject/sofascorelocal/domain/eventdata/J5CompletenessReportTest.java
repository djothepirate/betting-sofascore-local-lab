package com.bettingproject.sofascorelocal.domain.eventdata;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J5CompletenessReportTest {

    @Test
    void calculatesAConservativeIntegerScoreForPartialData() {
        var report = J5CompletenessReport.measured(
                3,
                7,
                List.of(
                        "$.confirmed=true",
                        "$.home.formation",
                        "$.home.players[0].position",
                        "$.away.players"));

        assertThat(report.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
        assertThat(report.scorePercent()).isEqualTo(42);
    }

    @Test
    void rejectsACompleteStatusThatStillListsMissingPaths() {
        assertThatThrownBy(() -> new J5CompletenessReport(
                J5CompletenessStatus.COMPLETE,
                100,
                2,
                2,
                List.of("$.unexpected")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPartialStatusWithAnOptimisticScore() {
        assertThatThrownBy(() -> new J5CompletenessReport(
                J5CompletenessStatus.PARTIAL,
                80,
                1,
                2,
                List.of("$.away")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPartialStatusThatDoesNotDocumentEveryMissingSignal() {
        assertThatThrownBy(() -> new J5CompletenessReport(
                J5CompletenessStatus.PARTIAL,
                33,
                1,
                3,
                List.of("$.away")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distinguishesProviderUnavailabilityFromAValidEmptyList() {
        var unavailable = J5CompletenessReport.unavailable();
        var emptyValid = J5CompletenessReport.emptyValid();

        assertThat(unavailable.status()).isEqualTo(J5CompletenessStatus.UNAVAILABLE);
        assertThat(unavailable.scorePercent()).isZero();
        assertThat(emptyValid.status()).isEqualTo(J5CompletenessStatus.EMPTY_VALID);
        assertThat(emptyValid.scorePercent()).isEqualTo(100);
    }

    @Test
    void rejectsUnavailableDataWithACompletenessPercentage() {
        assertThatThrownBy(() -> new J5CompletenessReport(
                J5CompletenessStatus.UNAVAILABLE,
                100,
                0,
                0,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
