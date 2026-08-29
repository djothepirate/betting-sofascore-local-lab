package com.bettingproject.sofascorelocal.application.benchmark;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class J8BenchmarkReportTest {

    @Test
    void permitsResponseOnlyEvidenceWithoutInventingAnAttemptCount() {
        J8BenchmarkReport base = J8BenchmarkReportTestFixture.notMeasured();
        J8BenchmarkReport.EvidenceScope scope = new J8BenchmarkReport.EvidenceScope(
                J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY,
                0,
                0,
                3,
                3,
                0,
                0,
                0,
                0,
                Optional.of(Instant.parse("2026-08-29T09:00:00Z")),
                Optional.of(Instant.parse("2026-08-29T09:10:00Z")));

        J8BenchmarkReport report = new J8BenchmarkReport(
                base.generatedAt(), base.asOf(), base.effectiveWindow(),
                J8MeasurementState.PARTIAL, base.populationHash(), scope,
                base.pageAndFamilyAvailability(), base.callSummary(),
                base.endpointMetrics(), base.completenessBreakdowns(),
                base.lateChangeMetrics(), base.parserStability(),
                base.dossierEfficiency(), base.decisionDimensions(), base.limitations());

        assertThat(report.evidenceScope().coverage())
                .isEqualTo(J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY);
        assertThat(report.evidenceScope().persistedResponseCount()).isEqualTo(3);
        assertThat(report.evidenceScope().directAttemptCount()).isZero();
    }

    @Test
    void stillRejectsImpossibleCountsForAFullAttemptLedger() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new J8BenchmarkReport.EvidenceScope(
                        J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                        1, 1, 2, 0, 0, 0, 0, 0,
                        Optional.of(Instant.parse("2026-08-29T09:00:00Z")),
                        Optional.of(Instant.parse("2026-08-29T09:10:00Z"))));
    }

    @Test
    void publishesEndpointAvailabilityWithItsOwnAttemptDenominator() {
        J8BenchmarkReport.EndpointMetrics endpoint =
                J8BenchmarkReportTestFixture.measured("Competition")
                        .endpointMetrics().getFirst();

        assertThat(endpoint.endpointUnavailableRate()).satisfies(rate -> {
            assertThat(rate.numerator()).isEqualTo(endpoint.endpointUnavailableCount());
            assertThat(rate.denominator()).isEqualTo(endpoint.directAttemptCount());
            assertThat(rate.percent()).contains(new java.math.BigDecimal("0.00"));
        });
    }

    @Test
    void representsUnknownHistoricalParsingIndependentlyFromResponseEvidence() {
        J8BenchmarkReport report =
                J8BenchmarkReportTestFixture.responseOnlyWithoutParsingProof();
        J8BenchmarkReport.EndpointMetrics endpoint = report.endpointMetrics().getFirst();

        assertThat(endpoint.responseEvidenceCount()).isEqualTo(1);
        assertThat(endpoint.deduplicatedResponseCount()).isEqualTo(1);
        assertThat(endpoint.latency().sampleCount()).isEqualTo(1);
        assertThat(endpoint.parsingMeasurementState())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
        assertThat(endpoint.parsedCount()).isZero();
        assertThat(endpoint.parserVersions()).isEmpty();
        assertThat(report.parserStability().state())
                .isEqualTo(J8MeasurementState.NOT_MEASURED);
    }
}
