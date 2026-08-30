package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class J8BenchmarkMarkdownRendererTest {

    private final J8BenchmarkMarkdownRenderer renderer =
            new J8BenchmarkMarkdownRenderer();

    @Test
    void rendersAStableReportWithAsOfFormulasAndBoundedLimitations() {
        J8BenchmarkReport report = J8BenchmarkReportTestFixture.measured(
                "Competition | <script>alert(1)</script>");

        String first = renderer.render(report);
        String second = renderer.render(report);

        assertThat(second).isEqualTo(first);
        assertThat(first)
                .startsWith("<!-- J8-AUTOMATIC-BEGIN populationHash="
                        + "a".repeat(64) + " -->\n")
                .endsWith("<!-- J8-AUTOMATIC-END -->\n")
                .contains("INCOMPLETE_ATTEMPT")
                .contains("**As of:** 2026-08-29T10:15:30Z")
                .contains("Evidence coverage:** FULL_ATTEMPT_LEDGER")
                .contains("discovery overhead = tentatives J3")
                .contains("couverture pondérée = Σ signaux présents / Σ signaux")
                .contains("**Unexpected-content responses:** 0")
                .contains("**First parser break:** NOT_MEASURED")
                .contains("erreur opérationnelle")
                .contains("404 rate — all endpoint attempts")
                .contains("Taux 404 / tentatives endpoint")
                .contains("0/2 &#40;0.00%&#41;")
                .contains("dénominateur nul produit `NOT_MEASURED`")
                .contains("CONTROL_SOURCE_ABSENT")
                .contains("Competition &#124; &lt;script&gt;alert&#40;1&#41;&lt;/script&gt;")
                .doesNotContain("<script>")
                .doesNotContain("\r\n");
    }

    @Test
    void neutralizesImagesLinksUrisCodeAndTableSyntaxInEvidenceLabels() {
        String malicious = "![image](file:///secret.txt) [link](https://example.invalid/path) "
                + "ftp://host.invalid mailto:user@example.invalid javascript:alert(1) "
                + "www.example.invalid `code` | cell";

        String markdown = renderer.render(
                J8BenchmarkReportTestFixture.measured(malicious));

        assertThat(markdown)
                .contains("&#33;&#91;image&#93;&#40;file&#58;///secret.txt&#41; "
                        + "&#91;link&#93;&#40;https&#58;//example.invalid/path&#41; "
                        + "ftp&#58;//host.invalid mailto&#58;user&#64;example.invalid "
                        + "javascript&#58;alert&#40;1&#41; www&#46;example.invalid "
                        + "&#96;code&#96; &#124; cell")
                .doesNotContain(
                        "![image]", "](file://", "](https://", "ftp://", "mailto:",
                        "javascript:", "www.example.invalid", "`code` | cell");
    }

    @Test
    void rendersUnknownMeasurementsAsNotMeasuredInsteadOfRatiosAtZero() {
        String markdown = renderer.render(J8BenchmarkReportTestFixture.notMeasured());

        assertThat(markdown)
                .contains("**Measurement state:** NOT_MEASURED")
                .contains("**Response rate:** NOT_MEASURED")
                .contains("_NOT_MEASURED — aucune preuve de réponse éligible._")
                .doesNotContain(
                        "0%", "0 %", "0.00%", "0.00 %", "0 ms", "0/0", "n=0",
                        "NaN", "Infinity");
    }

    @Test
    void keepsFullLedgerCallCountsExactWhenNoDossierDenominatorExists() {
        String markdown = renderer.render(J8BenchmarkReportTestFixture.partial());

        assertThat(markdown)
                .contains("**Measurement state:** PARTIAL")
                .contains("**Targeted events:** 0")
                .contains("**Exploitable events:** 0")
                .contains("**Strictly complete events:** 0")
                .contains("**Discovery overhead:** 1")
                .contains("**Marginal dossier calls:** 0")
                .contains("**All direct calls:** 1")
                .contains("**Exploitable rate:** NOT_MEASURED")
                .contains("**Strictly complete rate:** NOT_MEASURED")
                .contains("**Marginal calls per exploitable dossier:** NOT_MEASURED")
                .contains("**Effective calls per exploitable dossier:** NOT_MEASURED")
                .contains("les trois comptes d’appels restent exacts");
    }

    @Test
    void keepsUnreconstructibleHistoricalCountsUnmeasuredInBothStrata() {
        J8BenchmarkReport base = J8BenchmarkReportTestFixture.notMeasured();
        J8BenchmarkReport.EndpointMetrics endpoint = historicalEndpoint();
        J8BenchmarkReport.EvidenceStratumMetrics responseOnly = historicalStratum(
                J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY,
                endpoint,
                base);
        J8BenchmarkReport.EvidenceStratumMetrics baseline = historicalStratum(
                J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE,
                endpoint,
                base);
        J8BenchmarkReport report = new J8BenchmarkReport(
                base.generatedAt(),
                base.asOf(),
                base.effectiveWindow(),
                J8MeasurementState.PARTIAL,
                base.populationHash(),
                base.evidenceScope(),
                List.of(responseOnly, baseline),
                base.pageAndFamilyAvailability(),
                base.callSummary(),
                base.endpointMetrics(),
                base.completenessBreakdowns(),
                base.lateChangeMetrics(),
                base.parserStability(),
                base.dossierEfficiency(),
                base.decisionDimensions(),
                base.limitations());

        String markdown = renderer.render(report);

        assertThat(markdown)
                .contains("### RESPONSE_ONLY", "### LEGACY_BASELINE")
                .contains("|EVENT_DETAILS|PARTIAL|PARTIAL|NOT_MEASURED|1|1|0|"
                        + "NOT_MEASURED|0|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|"
                        + "NOT_MEASURED|0|0|NOT_MEASURED|NOT_MEASURED|")
                .contains("|EVENT_DETAILS|PARTIAL|PARTIAL|NOT_MEASURED|1|1|0|"
                        + "NOT_MEASURED|0|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|"
                        + "NOT_MEASURED|0|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|")
                .doesNotContain("|EVENT_DETAILS|PARTIAL|PARTIAL|NOT_MEASURED|1|1|0|0|");
    }

    @Test
    void keepsDeduplicatedResponseAndLatencyButLeavesParsingUnmeasured() {
        String markdown = renderer.render(
                J8BenchmarkReportTestFixture.responseOnlyWithoutParsingProof());

        assertThat(markdown)
                .contains("### RESPONSE_ONLY")
                .contains("Taux 404 / tentatives endpoint")
                .contains("|EVENT_DETAILS|PARTIAL|NOT_MEASURED|NOT_MEASURED|1|"
                        + "NOT_MEASURED|0|NOT_MEASURED|")
                .contains("|0|1|NOT_MEASURED|NOT_MEASURED|n=1, min=180 ms, "
                        + "p50=180 ms, p95=180 ms, max=180 ms|NOT_MEASURED|")
                .contains("**Eligible responses:** NOT_MEASURED")
                .contains("**Parsed responses:** NOT_MEASURED")
                .contains("**Parser versions:** NOT_MEASURED")
                .contains("**Parser version transitions:** NOT_MEASURED")
                .contains("_NOT_MEASURED — aucune version de parseur éligible._")
                .doesNotContain(
                        "**Eligible responses:** 0",
                        "**Parsed responses:** 0",
                        "**Parser versions:** 0",
                        "**Parser version transitions:** 0");
    }

    @Test
    void rendersAFutureOnlyRequestAsAnExplicitEmptyEffectiveWindow() {
        Instant asOf = J8BenchmarkReportTestFixture.AS_OF;
        J8BenchmarkWindow effectiveWindow = J8BenchmarkWindow.between(
                asOf.plusSeconds(60), asOf.plusSeconds(120)).boundedAt(asOf);
        J8BenchmarkReport report = J8BenchmarkReportTestFixture.withEffectiveWindow(
                J8BenchmarkReportTestFixture.notMeasured(), effectiveWindow);

        String markdown = renderer.render(report);

        assertThat(markdown)
                .contains("**Effective window from:** 2026-08-29T10:15:30Z")
                .contains("**Effective window to:** 2026-08-29T10:15:30Z")
                .contains("**Measurement state:** NOT_MEASURED")
                .contains("**Response rate:** NOT_MEASURED")
                .doesNotContain(
                        "0%", "0.00%", "0/0", "0 ms", "NaN", "Infinity");
    }

    private static J8BenchmarkReport.EvidenceStratumMetrics historicalStratum(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8BenchmarkReport.EndpointMetrics endpoint,
            J8BenchmarkReport base) {
        return new J8BenchmarkReport.EvidenceStratumMetrics(
                coverage,
                J8MeasurementState.PARTIAL,
                1,
                base.pageAndFamilyAvailability(),
                base.callSummary(),
                List.of(endpoint),
                List.of(),
                new J8BenchmarkReport.ParserStability(
                        J8MeasurementState.PARTIAL,
                        J8MeasurementState.NOT_MEASURED,
                        1, 1, 0, 0, 1, 0,
                        new J8BenchmarkReport.Rate(0, 0, Optional.empty()),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        List.of(new J8BenchmarkReport.ParserVersionMetrics(
                                SofascoreEndpointType.EVENT_DETAILS,
                                "event-details-v2",
                                1, 1, 0,
                                Optional.of(base.asOf()),
                                Optional.of(base.asOf())))));
    }

    private static J8BenchmarkReport.EndpointMetrics historicalEndpoint() {
        return new J8BenchmarkReport.EndpointMetrics(
                SofascoreEndpointType.EVENT_DETAILS,
                J8MeasurementState.PARTIAL,
                J8MeasurementState.NOT_MEASURED,
                J8MeasurementState.PARTIAL,
                J8MeasurementState.NOT_MEASURED,
                0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                new J8BenchmarkReport.Rate(0, 0, Optional.empty()),
                new J8BenchmarkReport.Rate(0, 0, Optional.empty()),
                new J8BenchmarkReport.Rate(0, 0, Optional.empty()),
                new J8BenchmarkReport.LatencyDistribution(
                        1,
                        OptionalLong.of(100),
                        OptionalLong.of(100),
                        OptionalLong.of(100),
                        OptionalLong.of(100)),
                List.of("event-details-v2"));
    }
}
