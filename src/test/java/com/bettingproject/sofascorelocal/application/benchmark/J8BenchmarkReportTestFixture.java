package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public final class J8BenchmarkReportTestFixture {

    public static final Instant AS_OF = Instant.parse("2026-08-29T10:15:30Z");

    private J8BenchmarkReportTestFixture() {
    }

    public static J8BenchmarkReport notMeasured() {
        return new J8BenchmarkReport(
                AS_OF,
                AS_OF,
                J8BenchmarkWindow.allAvailable(),
                J8MeasurementState.NOT_MEASURED,
                "0".repeat(64),
                new J8BenchmarkReport.EvidenceScope(
                        J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        Optional.empty(), Optional.empty()),
                new J8BenchmarkReport.PageAndFamilyAvailability(
                        J8MeasurementState.NOT_MEASURED,
                        0,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(),
                        List.of()),
                emptyCalls(),
                List.of(),
                List.of(),
                new J8BenchmarkReport.LateChangeMetrics(
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0,
                        J8BenchmarkReport.LatencyDistribution.notMeasured()),
                new J8BenchmarkReport.ParserStability(
                        J8MeasurementState.NOT_MEASURED,
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0, 0, 0,
                        rate(0, 0, null),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of()),
                new J8BenchmarkReport.DossierEfficiency(
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0, 0, 0,
                        rate(0, 0, null),
                        rate(0, 0, null),
                        Optional.empty(),
                        Optional.empty()),
                decisions(J8MeasurementState.NOT_MEASURED),
                List.of(new J8BenchmarkReport.Limitation(
                        "NO_ELIGIBLE_EVIDENCE",
                        "Aucune preuve locale éligible.")));
    }

    public static J8BenchmarkReport measured(String competition) {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-29T00:00:00Z");
        return new J8BenchmarkReport(
                AS_OF,
                AS_OF,
                J8BenchmarkWindow.between(from, to),
                J8MeasurementState.MEASURED,
                "a".repeat(64),
                new J8BenchmarkReport.EvidenceScope(
                        J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                        1, 2, 2, 0, 0, 0, 0, 0,
                        Optional.of(from.plusSeconds(60)),
                        Optional.of(from.plusSeconds(120))),
                new J8BenchmarkReport.PageAndFamilyAvailability(
                        J8MeasurementState.PARTIAL,
                        0,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(SofascoreEndpointType.EVENT_DETAILS),
                        List.of()),
                new J8BenchmarkReport.CallSummary(
                        J8MeasurementState.MEASURED,
                        2, 2, 1, 1, 0, 0, 1,
                        rate(2, 2, "100.00"),
                        rate(1, 1, "100.00"),
                        rate(1, 2, "50.00"),
                        rate(0, 2, "0.00"),
                        rate(1, 2, "50.00")),
                List.of(new J8BenchmarkReport.EndpointMetrics(
                        SofascoreEndpointType.EVENT_DETAILS,
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        2, 2, 1, 0, 1, 0, 0, 0, 0, 0, 0,
                        rate(0, 2, "0.00"),
                        rate(1, 1, "100.00"),
                        rate(1, 2, "50.00"),
                        new J8BenchmarkReport.LatencyDistribution(
                                2,
                                OptionalLong.of(100),
                                OptionalLong.of(100),
                                OptionalLong.of(200),
                                OptionalLong.of(200)),
                        List.of("event-details-v2"))),
                List.of(new J8BenchmarkReport.CompletenessBreakdown(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        Optional.of(competition),
                        Optional.of("2026/27"),
                        Optional.of("finished"),
                        1, 1, 0, 0, 0,
                        Optional.of(new BigDecimal("100.00")),
                        J8MeasurementState.MEASURED)),
                new J8BenchmarkReport.LateChangeMetrics(
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0,
                        J8BenchmarkReport.LatencyDistribution.notMeasured()),
                new J8BenchmarkReport.ParserStability(
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        1, 1, 0, 0, 1, 0,
                        rate(1, 1, "100.00"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(new J8BenchmarkReport.ParserVersionMetrics(
                                SofascoreEndpointType.EVENT_DETAILS,
                                "event-details-v2",
                                1, 1, 0,
                                Optional.of(from.plusSeconds(60)),
                                Optional.of(from.plusSeconds(60))))),
                new J8BenchmarkReport.DossierEfficiency(
                        J8MeasurementState.MEASURED,
                        1, 1, 0, 0, 2, 2,
                        rate(1, 1, "100.00"),
                        rate(0, 1, "0.00"),
                        Optional.of(new BigDecimal("2.00")),
                        Optional.of(new BigDecimal("2.00"))),
                decisions(J8MeasurementState.MEASURED),
                List.of(new J8BenchmarkReport.Limitation(
                        "CONTROL_SOURCE_ABSENT",
                        "Aucune comparaison externe.")));
    }

    public static J8BenchmarkReport partial() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-29T00:00:00Z");
        J8BenchmarkReport.Rate oneOfOne = rate(1, 1, "100.00");
        J8BenchmarkReport.Rate zeroOfOne = rate(0, 1, "0.00");
        return new J8BenchmarkReport(
                AS_OF,
                AS_OF,
                J8BenchmarkWindow.between(from, to),
                J8MeasurementState.PARTIAL,
                "b".repeat(64),
                new J8BenchmarkReport.EvidenceScope(
                        J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                        1, 1, 1, 0, 0, 0, 0, 0,
                        Optional.of(from.plusSeconds(60)),
                        Optional.of(from.plusSeconds(60))),
                new J8BenchmarkReport.PageAndFamilyAvailability(
                        J8MeasurementState.MEASURED,
                        1,
                        OptionalLong.of(1),
                        OptionalLong.of(1),
                        List.of(SofascoreEndpointType.SCHEDULED_EVENTS),
                        List.of()),
                new J8BenchmarkReport.CallSummary(
                        J8MeasurementState.MEASURED,
                        1, 1, 1, 0, 0, 0, 0,
                        oneOfOne, oneOfOne, zeroOfOne, zeroOfOne, zeroOfOne),
                List.of(new J8BenchmarkReport.EndpointMetrics(
                        SofascoreEndpointType.SCHEDULED_EVENTS,
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                        zeroOfOne,
                        oneOfOne,
                        zeroOfOne,
                        new J8BenchmarkReport.LatencyDistribution(
                                1,
                                OptionalLong.of(100),
                                OptionalLong.of(100),
                                OptionalLong.of(100),
                                OptionalLong.of(100)),
                        List.of("scheduled-events-v2"))),
                List.of(),
                new J8BenchmarkReport.LateChangeMetrics(
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0,
                        J8BenchmarkReport.LatencyDistribution.notMeasured()),
                new J8BenchmarkReport.ParserStability(
                        J8MeasurementState.MEASURED,
                        J8MeasurementState.MEASURED,
                        1, 1, 0, 0, 1, 0,
                        oneOfOne,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(new J8BenchmarkReport.ParserVersionMetrics(
                                SofascoreEndpointType.SCHEDULED_EVENTS,
                                "scheduled-events-v2",
                                1, 1, 0,
                                Optional.of(from.plusSeconds(60)),
                                Optional.of(from.plusSeconds(60))))),
                new J8BenchmarkReport.DossierEfficiency(
                        J8MeasurementState.PARTIAL,
                        0, 0, 0, 1, 0, 1,
                        rate(0, 0, null),
                        rate(0, 0, null),
                        Optional.empty(),
                        Optional.empty()),
                decisions(J8MeasurementState.PARTIAL),
                List.of(new J8BenchmarkReport.Limitation(
                        "DOSSIER_DENOMINATOR_ABSENT",
                        "Aucun dossier J4/J5 n'est ciblé dans cette fenêtre.")));
    }

    public static J8BenchmarkReport responseOnlyWithoutParsingProof() {
        Instant observedAt = AS_OF.minusSeconds(60);
        J8BenchmarkReport.Rate notMeasuredRate = rate(0, 0, null);
        J8BenchmarkReport.PageAndFamilyAvailability availability =
                new J8BenchmarkReport.PageAndFamilyAvailability(
                        J8MeasurementState.NOT_MEASURED,
                        0,
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        List.of(),
                        List.of());
        J8BenchmarkReport.ParserStability parsingNotMeasured =
                new J8BenchmarkReport.ParserStability(
                        J8MeasurementState.NOT_MEASURED,
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0, 0, 0,
                        notMeasuredRate,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());

        return new J8BenchmarkReport(
                AS_OF,
                AS_OF,
                J8BenchmarkWindow.allAvailable(),
                J8MeasurementState.PARTIAL,
                "c".repeat(64),
                new J8BenchmarkReport.EvidenceScope(
                        J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY,
                        0, 0, 1, 1, 0, 0, 0, 0,
                        Optional.of(observedAt), Optional.of(observedAt)),
                availability,
                emptyCalls(),
                List.of(new J8BenchmarkReport.EndpointMetrics(
                        SofascoreEndpointType.EVENT_DETAILS,
                        J8MeasurementState.PARTIAL,
                        J8MeasurementState.NOT_MEASURED,
                        J8MeasurementState.NOT_MEASURED,
                        J8MeasurementState.NOT_MEASURED,
                        0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1,
                        notMeasuredRate,
                        notMeasuredRate,
                        notMeasuredRate,
                        new J8BenchmarkReport.LatencyDistribution(
                                1,
                                OptionalLong.of(180),
                                OptionalLong.of(180),
                                OptionalLong.of(180),
                                OptionalLong.of(180)),
                        List.of())),
                List.of(),
                new J8BenchmarkReport.LateChangeMetrics(
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0,
                        J8BenchmarkReport.LatencyDistribution.notMeasured()),
                parsingNotMeasured,
                new J8BenchmarkReport.DossierEfficiency(
                        J8MeasurementState.NOT_MEASURED,
                        0, 0, 0, 0, 0, 0,
                        notMeasuredRate,
                        notMeasuredRate,
                        Optional.empty(),
                        Optional.empty()),
                decisions(J8MeasurementState.PARTIAL),
                List.of(new J8BenchmarkReport.Limitation(
                        "HISTORICAL_PARSING_NOT_PROVEN",
                        "La réponse dédupliquée ne possède pas de preuve normalisée immuable corrélée.")));
    }

    public static J8BenchmarkReport withEffectiveWindow(
            J8BenchmarkReport report,
            J8BenchmarkWindow effectiveWindow) {
        return new J8BenchmarkReport(
                report.generatedAt(),
                report.asOf(),
                effectiveWindow,
                report.state(),
                report.populationHash(),
                report.evidenceScope(),
                report.evidenceStrata(),
                report.pageAndFamilyAvailability(),
                report.callSummary(),
                report.endpointMetrics(),
                report.completenessBreakdowns(),
                report.lateChangeMetrics(),
                report.parserStability(),
                report.dossierEfficiency(),
                report.decisionDimensions(),
                report.limitations());
    }

    private static J8BenchmarkReport.CallSummary emptyCalls() {
        J8BenchmarkReport.Rate empty = rate(0, 0, null);
        return new J8BenchmarkReport.CallSummary(
                J8MeasurementState.NOT_MEASURED,
                0, 0, 0, 0, 0, 0, 0,
                empty, empty, empty, empty, empty);
    }

    private static J8BenchmarkReport.Rate rate(
            long numerator,
            long denominator,
            String percent) {
        return new J8BenchmarkReport.Rate(
                numerator,
                denominator,
                percent == null
                        ? Optional.empty()
                        : Optional.of(new BigDecimal(percent)));
    }

    private static List<J8BenchmarkReport.DecisionAssessment> decisions(
            J8MeasurementState state) {
        return Arrays.stream(J8BenchmarkReport.DecisionDimension.values())
                .map(dimension -> new J8BenchmarkReport.DecisionAssessment(
                        dimension,
                        state,
                        "TEST_" + dimension.name(),
                        "Preuve locale bornée pour le test."))
                .toList();
    }
}
