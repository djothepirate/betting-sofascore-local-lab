package com.bettingproject.sofascorelocal.application.benchmark;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class J8BenchmarkWindowTest {

    @Test
    void acceptsOnlyTwoAbsentParametersForAllHistory() {
        assertThat(J8BenchmarkWindow.parse(null, null))
                .isEqualTo(J8BenchmarkWindow.allAvailable());

        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse("", ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse("  ", "\t"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse(null, ""));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse("", null));
    }

    @Test
    void parsesAndTrimsAStrictUtcHalfOpenWindow() {
        J8BenchmarkWindow window = J8BenchmarkWindow.parse(
                " 2026-08-01T00:00:00Z ",
                "2026-09-01T00:00:00Z");

        assertThat(window.fromInclusive())
                .contains(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(window.toExclusive())
                .contains(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void rejectsOffsetsLengthsControlCharactersAndReversedBounds() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse(
                        "2026-08-01T02:00:00+02:00",
                        "2026-09-01T02:00:00+02:00"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse("x".repeat(65), "2026-09-01T00:00:00Z"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse(
                        " ".repeat(30) + "2026-08-01T00:00:00Z" + " ".repeat(20),
                        "2026-09-01T00:00:00Z"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse(
                        "2026-08-01T00:00:00Z\n",
                        "2026-09-01T00:00:00Z"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse(
                        "2026-09-01T00:00:00Z",
                        "2026-08-01T00:00:00Z"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                J8BenchmarkWindow.parse(
                        "2026-08-01T00:00:00Z",
                        "2026-08-01T00:00:00Z"));
    }

    @Test
    void boundsAValidRequestedWindowAtAsOfAndRepresentsAnEmptyIntersection() {
        Instant asOf = Instant.parse("2026-08-29T12:00:00Z");
        J8BenchmarkWindow overlapping = J8BenchmarkWindow.between(
                Instant.parse("2026-08-29T10:00:00Z"),
                Instant.parse("2026-08-29T13:00:00Z"));
        J8BenchmarkWindow futureOnly = J8BenchmarkWindow.between(
                Instant.parse("2026-08-29T12:30:00Z"),
                Instant.parse("2026-08-29T13:00:00Z"));

        assertThat(overlapping.boundedAt(asOf)).isEqualTo(
                J8BenchmarkWindow.between(
                        Instant.parse("2026-08-29T10:00:00Z"), asOf));
        assertThat(futureOnly.boundedAt(asOf)).satisfies(window -> {
            assertThat(window.fromInclusive()).contains(asOf);
            assertThat(window.toExclusive()).contains(asOf);
            assertThat(window.empty()).isTrue();
        });
        assertThat(J8BenchmarkWindow.allAvailable().boundedAt(asOf))
                .isEqualTo(J8BenchmarkWindow.allAvailable());
    }
}
