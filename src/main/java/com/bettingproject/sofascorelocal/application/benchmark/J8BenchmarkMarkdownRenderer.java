package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class J8BenchmarkMarkdownRenderer {

    private static final String NOT_MEASURED = "NOT_MEASURED";
    private static final Pattern URI_SCHEME = Pattern.compile(
            "(?i)(?<![A-Z0-9+.-])[A-Z][A-Z0-9+.-]*:");
    private static final Pattern BARE_WWW_AUTOLINK = Pattern.compile(
            "(?i)(?<![A-Z0-9])www\\.");

    public String render(J8BenchmarkReport report) {
        Objects.requireNonNull(report, "report");
        StringBuilder output = new StringBuilder(12_288);
        output.append("<!-- J8-AUTOMATIC-BEGIN populationHash=")
                .append(report.populationHash())
                .append(" -->\n")
                .append("# J8 — Rapport de benchmark local\n\n");
        output.append("> EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · ")
                .append("NO_CRITICAL_DEPENDENCY\n\n");
        output.append("> J8 : agrégation locale — aucun appel fournisseur.\n\n");
        keyValue(output, "Generated at", report.generatedAt().toString());
        keyValue(output, "As of", report.asOf().toString());
        keyValue(output, "Effective window from", report.effectiveWindow().fromInclusive()
                .map(Object::toString).orElse("ALL_AVAILABLE"));
        keyValue(output, "Effective window to", report.effectiveWindow().toExclusive()
                .map(Object::toString).orElse(report.asOf().toString()));
        keyValue(output, "Window semantics", "[from,to) UTC; asOf is an inclusive read cutoff");
        keyValue(output, "Population SHA-256", report.populationHash());
        keyValue(output, "Measurement state", report.state().name());

        renderScope(output, report);
        renderEvidenceStrata(output, report.evidenceStrata());
        renderLateChanges(output, report.lateChangeMetrics());
        renderDossierEfficiency(output, report.dossierEfficiency());
        renderDecisionAssessments(output, report.decisionDimensions());
        renderLimitations(output, report.limitations());

        output.append("\n## Garde-fous\n\n")
                .append("- Aucun payload brut, URI, header, cookie, token ou état de session.\n")
                .append("- Aucun appel fournisseur, retry, polling, scheduler ou transfert externe.\n")
                .append("- Les valeurs non observables restent `NOT_MEASURED`; aucune comparaison ")
                .append("externe n’est inventée.\n\n")
                .append("<!-- J8-AUTOMATIC-END -->\n");
        return output.toString();
    }

    private static void renderScope(
            StringBuilder output,
            J8BenchmarkReport report) {
        output.append("\n## Portée des preuves\n\n");
        J8BenchmarkReport.EvidenceScope scope = report.evidenceScope();
        keyValue(output, "Evidence coverage", scope.coverage().name());
        output.append("\n| Campagnes | Appels directs | Réponses de la strate | RESPONSE_ONLY présentes | Baselines historiques ")
                .append("| Imports manuels exclus | Cache hits exclus | Observations synthétiques exclues |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|---:|\n")
                .append('|').append(scope.providerCampaignCount())
                .append('|').append(scope.coverage()
                        == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                                ? Long.toString(scope.directAttemptCount())
                                : NOT_MEASURED)
                .append('|').append(scope.persistedResponseCount())
                .append('|').append(scope.responseOnlyCount())
                .append('|').append(scope.legacyBaselineCount())
                .append('|').append(scope.manualImportCount())
                .append('|').append(scope.cacheHitCount())
                .append('|').append(scope.syntheticObservationCount())
                .append("|\n\n");
        keyValue(output, "First measured at", instant(scope.firstMeasuredAt()));
        keyValue(output, "Last measured at", instant(scope.lastMeasuredAt()));
    }

    private static void renderEvidenceStrata(
            StringBuilder output,
            List<J8BenchmarkReport.EvidenceStratumMetrics> strata) {
        output.append("\n## Strates de preuve non mélangées\n\n")
                .append("Chaque bloc conserve sa propre population et ses propres ")
                .append("dénominateurs. Les coûts et taux par tentative sont exacts ")
                .append("uniquement dans `FULL_ATTEMPT_LEDGER`.\n");
        for (J8BenchmarkReport.EvidenceStratumMetrics stratum : strata) {
            output.append("\n### ").append(stratum.coverage().name()).append("\n\n");
            keyValue(output, "Stratum measurement state", stratum.state().name());
            keyValue(output, "Stratum evidence population",
                    Long.toString(stratum.evidenceCount()));
            keyValue(output, "Attempt denominator",
                    stratum.coverage()
                                    == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                            ? "EXACT_LEDGER"
                            : NOT_MEASURED);
            renderAvailability(output, stratum.pageAndFamilyAvailability(),
                    "#### Disponibilité des pages et familles");
            renderCalls(output, stratum.callSummary(),
                    "#### Synthèse des appels directs");
            renderEndpoints(output, stratum.coverage(), stratum.endpointMetrics(),
                    "#### Mesures par endpoint");
            renderCompleteness(output, stratum.completenessBreakdowns(),
                    "#### Complétude normalisée");
            renderParserStability(output, stratum.parserStability(),
                    "#### Stabilité des parseurs");
        }
    }

    private static void renderAvailability(
            StringBuilder output,
            J8BenchmarkReport.PageAndFamilyAvailability availability,
            String heading) {
        output.append('\n').append(heading).append("\n\n");
        keyValue(output, "Measurement state", availability.state().name());
        keyValue(output, "Observed scheduled pages",
                availability.lowestScheduledPage().isPresent()
                        ? Long.toString(availability.observedPageCount())
                        : NOT_MEASURED);
        keyValue(output, "Scheduled page bounds", availability.lowestScheduledPage().isPresent()
                ? availability.lowestScheduledPage().orElseThrow() + ".."
                        + availability.highestScheduledPage().orElseThrow()
                : NOT_MEASURED);
        keyValue(output, "Available families", endpointNames(availability.availableFamilies()));
        keyValue(output, "Unavailable families",
                endpointNames(availability.unavailableFamilies()));
    }

    private static void renderCalls(
            StringBuilder output,
            J8BenchmarkReport.CallSummary calls,
            String heading) {
        output.append('\n').append(heading).append("\n\n");
        keyValue(output, "Measurement state", calls.state().name());
        output.append("\n| Tentatives | Réponses | Parsées | Refus 401/403/429 | 404 | INCOMPLETE_ATTEMPT | Erreurs opérationnelles hors 404 |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|\n");
        if (calls.state() == J8MeasurementState.NOT_MEASURED) {
            output.append('|').append(NOT_MEASURED)
                    .append('|').append(NOT_MEASURED)
                    .append('|').append(NOT_MEASURED)
                    .append('|').append(NOT_MEASURED)
                    .append('|').append(NOT_MEASURED)
                    .append('|').append(NOT_MEASURED)
                    .append('|').append(NOT_MEASURED).append("|\n");
        }
        else {
            output.append('|').append(calls.directAttemptCount())
                    .append('|').append(calls.responseReceivedCount())
                    .append('|').append(calls.parsedCount())
                    .append('|').append(calls.refusalCount())
                    .append('|').append(calls.endpointUnavailableCount())
                    .append('|').append(calls.incompleteAttemptCount())
                    .append('|').append(calls.errorCount()).append("|\n");
        }
        output.append('\n');
        keyValue(output, "Response rate", rate(calls.responseRate()));
        keyValue(output, "Parsing compatibility rate", rate(calls.parsingRate()));
        keyValue(output, "Refusal rate", rate(calls.refusalRate()));
        keyValue(output, "404 rate — all endpoint attempts",
                rate(calls.endpointUnavailableRate()));
        keyValue(output, "Operational error rate excluding 404", rate(calls.errorRate()));
        output.append("\nFormules : réponse = réponses reçues / tentatives directes ; ")
                .append("compatibilité = réponses parsées / réponses éligibles au parsing ; ")
                .append("refus et 404 de synthèse = catégorie / toutes les tentatives directes ; erreur opérationnelle ")
                .append("= refus + échecs HTTP/transport/persistance/traitement/parsing + arrêt ")
                .append("opérateur après tentative + tentative sans outcome, le 404 étant exclu. ")
                .append("Chaque taux conserve son numérateur et son dénominateur ; un ")
                .append("dénominateur nul produit `NOT_MEASURED`.\n");
    }

    private static void renderEndpoints(
            StringBuilder output,
            J8BenchmarkReport.EvidenceCoverage coverage,
            List<J8BenchmarkReport.EndpointMetrics> endpoints,
            String heading) {
        output.append('\n').append(heading).append("\n\n");
        if (endpoints.isEmpty()) {
            output.append("_NOT_MEASURED — aucune preuve de réponse éligible._\n");
            return;
        }
        output.append("| Endpoint | État | État parsing | Tentatives | Réponses prouvées | Parsés | 404 indisponibles | Taux 404 / tentatives endpoint | Refus | ")
                .append("INCOMPLETE_ATTEMPT | Schéma incompatible | Contenu inattendu | Transport | Autres erreurs | ")
                .append("Dédupliquées | Compatibilité | Taux d’erreur | Latence | Parseurs |\n")
                .append("|---|---|---|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|\n");
        for (J8BenchmarkReport.EndpointMetrics endpoint : endpoints) {
            output.append('|').append(endpoint.endpoint().name())
                    .append('|').append(endpoint.state().name())
                    .append('|').append(endpoint.parsingMeasurementState().name())
                    .append('|').append(endpoint.attemptMeasurementState()
                            == J8MeasurementState.NOT_MEASURED
                                    ? NOT_MEASURED
                                    : Long.toString(endpoint.directAttemptCount()))
                    .append('|').append(endpoint.responseEvidenceCount())
                    .append('|').append(measuredCount(
                            endpoint.parsingMeasurementState(), endpoint.parsedCount()))
                    .append('|').append(endpoint.endpointUnavailableCount())
                    .append('|').append(rate(endpoint.endpointUnavailableRate()))
                    .append('|').append(endpoint.refusalCount())
                    .append('|').append(measuredCount(
                            endpoint.attemptMeasurementState(),
                            endpoint.incompleteAttemptCount()))
                    .append('|').append(measuredCount(
                            endpoint.schemaBreakMeasurementState(),
                            endpoint.schemaIncompatibleCount()))
                    .append('|').append(measuredCount(
                            endpoint.schemaBreakMeasurementState(),
                            endpoint.unexpectedContentCount()))
                    .append('|').append(measuredCount(
                            endpoint.attemptMeasurementState(),
                            endpoint.transportErrorCount()))
                    .append('|').append(endpoint.otherErrorCount())
                    .append('|').append(coverage
                                    == J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE
                            ? NOT_MEASURED
                            : Long.toString(endpoint.deduplicatedResponseCount()))
                    .append('|').append(rate(endpoint.compatibilityRate()))
                    .append('|').append(rate(endpoint.errorRate()))
                    .append('|').append(latency(endpoint.latency()))
                    .append('|').append(endpoint.parserVersions().isEmpty()
                            ? NOT_MEASURED
                            : escape(String.join(", ", endpoint.parserVersions())))
                    .append("|\n");
        }
        output.append("\nLe taux de 404 par endpoint = ENDPOINT_UNAVAILABLE / tentatives directes de cet endpoint. ")
                .append("Le taux d’erreur conserve les tentatives directes comme dénominateur ; ")
                .append("il reste `NOT_MEASURED` pour RESPONSE_ONLY et LEGACY_BASELINE. ")
                .append("Dans ces strates historiques, les autres colonnes portent uniquement ")
                .append("sur les réponses persistées sélectionnées, jamais sur des tentatives inférées.\n");
    }

    private static void renderCompleteness(
            StringBuilder output,
            List<J8BenchmarkReport.CompletenessBreakdown> completeness,
            String heading) {
        output.append('\n').append(heading).append("\n\n");
        if (completeness.isEmpty()) {
            output.append("_NOT_MEASURED — aucune observation J5 directe éligible._\n");
            return;
        }
        output.append("| Endpoint | Compétition | Saison | État du match | État de mesure | ")
                .append("Observations | Complete | Partial | Empty valid | Unavailable | Couverture pondérée des signaux |\n")
                .append("|---|---|---|---|---|---:|---:|---:|---:|---:|---|\n");
        for (J8BenchmarkReport.CompletenessBreakdown value : completeness) {
            output.append('|').append(value.endpoint().name())
                    .append('|').append(text(value.competition()))
                    .append('|').append(text(value.season()))
                    .append('|').append(text(value.eventStatus()))
                    .append('|').append(value.state().name())
                    .append('|').append(value.observationCount())
                    .append('|').append(value.completeCount())
                    .append('|').append(value.partialCount())
                    .append('|').append(value.emptyValidCount())
                    .append('|').append(value.unavailableCount())
                    .append('|').append(decimal(value.weightedSignalCoveragePercent(), "%"))
                    .append("|\n");
        }
        output.append("\nFormule : couverture pondérée = Σ signaux présents / Σ signaux ")
                .append("attendus × 100, uniquement pour les observations dont le dénominateur ")
                .append("attendu est strictement positif. Les dimensions absentes sont rendues ")
                .append("littéralement `UNKNOWN`.\n");
    }

    private static void renderLateChanges(
            StringBuilder output,
            J8BenchmarkReport.LateChangeMetrics late) {
        output.append("\n## Corrections tardives J6\n\n");
        keyValue(output, "Measurement state", late.state().name());
        keyValue(output, "Analyzed events",
                measuredCount(late.state(), late.analyzedEventCount()));
        keyValue(output, "Analyzed provider versions",
                measuredCount(late.state(), late.analyzedProviderVersionCount()));
        keyValue(output, "Late enrichments",
                measuredCount(late.state(), late.lateEnrichmentCount()));
        keyValue(output, "Late corrections",
                measuredCount(late.state(), late.lateCorrectionCount()));
        keyValue(output, "Late-change delay", latency(late.lateChangeDelay()));
        output.append("\nLe délai commun aux enrichissements et corrections tardifs est mesuré ")
                .append("entre la version fournisseur terminale précédente et la version tardive ")
                .append("reçue ; ce n’est pas un retard par rapport à une ")
                .append("horloge source absente des preuves.\n");
    }

    private static void renderParserStability(
            StringBuilder output,
            J8BenchmarkReport.ParserStability stability,
            String heading) {
        output.append('\n').append(heading).append("\n\n");
        keyValue(output, "Measurement state", stability.state().name());
        keyValue(output, "Eligible responses",
                measuredCount(stability.state(), stability.eligibleResponseCount()));
        keyValue(output, "Parsed responses",
                measuredCount(stability.state(), stability.parsedCount()));
        keyValue(output, "Schema-incompatible responses",
                measuredCount(
                        stability.schemaBreakMeasurementState(),
                        stability.schemaIncompatibleCount()));
        keyValue(output, "Unexpected-content responses",
                measuredCount(
                        stability.schemaBreakMeasurementState(),
                        stability.unexpectedContentCount()));
        keyValue(output, "Parser versions",
                measuredCount(stability.state(), stability.parserVersionCount()));
        keyValue(output, "Parser version transitions",
                measuredCount(stability.state(), stability.parserVersionTransitionCount()));
        keyValue(output, "Compatibility rate", rate(stability.compatibilityRate()));
        keyValue(output, "First parser break",
                stability.schemaBreakMeasurementState() == J8MeasurementState.NOT_MEASURED
                        ? NOT_MEASURED
                        : instant(stability.firstBreakAt()));
        keyValue(output, "Last parser break",
                stability.schemaBreakMeasurementState() == J8MeasurementState.NOT_MEASURED
                        ? NOT_MEASURED
                        : instant(stability.lastBreakAt()));
        keyValue(output, "First PARSED recovery after last break",
                stability.schemaBreakMeasurementState() == J8MeasurementState.NOT_MEASURED
                        ? NOT_MEASURED
                        : instant(stability.firstRecoveryAt()));
        if (stability.versions().isEmpty()) {
            output.append("\n_NOT_MEASURED — aucune version de parseur éligible._\n");
            return;
        }
        output.append("\n| Endpoint | Parseur | Réponses | Parsées | Incompatibles | Première | Dernière |\n")
                .append("|---|---|---:|---:|---:|---|---|\n");
        for (J8BenchmarkReport.ParserVersionMetrics version : stability.versions()) {
            output.append('|').append(version.endpoint().name())
                    .append('|').append(escape(version.parserVersion()))
                    .append('|').append(version.responseCount())
                    .append('|').append(version.parsedCount())
                    .append('|').append(measuredCount(
                            stability.schemaBreakMeasurementState(),
                            version.incompatibleCount()))
                    .append('|').append(instant(version.firstObservedAt()))
                    .append('|').append(instant(version.lastObservedAt()))
                    .append("|\n");
        }
    }

    private static void renderDossierEfficiency(
            StringBuilder output,
            J8BenchmarkReport.DossierEfficiency efficiency) {
        output.append("\n## Efficacité des dossiers\n\n");
        keyValue(output, "Measurement state", efficiency.state().name());
        keyValue(output, "Targeted events",
                measuredCount(efficiency.state(), efficiency.targetedEventCount()));
        keyValue(output, "Exploitable events",
                measuredCount(efficiency.state(), efficiency.exploitableEventCount()));
        keyValue(output, "Strictly complete events",
                measuredCount(efficiency.state(), efficiency.strictlyCompleteEventCount()));
        keyValue(output, "Discovery overhead",
                measuredCount(efficiency.state(), efficiency.discoveryOverhead()));
        keyValue(output, "Marginal dossier calls",
                measuredCount(efficiency.state(), efficiency.marginalCallCount()));
        keyValue(output, "All direct calls",
                measuredCount(efficiency.state(), efficiency.effectiveCallCount()));
        keyValue(output, "Exploitable rate", rate(efficiency.exploitableRate()));
        keyValue(output, "Strictly complete rate", rate(efficiency.strictlyCompleteRate()));
        keyValue(output, "Marginal calls per exploitable dossier",
                decimal(efficiency.marginalCallsPerExploitableDossier(), ""));
        keyValue(output, "Effective calls per exploitable dossier",
                decimal(efficiency.effectiveCallsPerExploitableDossier(), ""));
        output.append("\nFormules : discovery overhead = tentatives J3 pages + découverte tournoi ; ")
                .append("appels marginaux / dossier = tentatives J4 phase 2 + J5 / dossiers ")
                .append("directs exploitables ; appels effectifs / dossier = toutes les tentatives ")
                .append("directes de la fenêtre / dossiers directs exploitables. Le dénominateur ")
                .append("des deux ratios est donc `exploitable events`. Un événement est ciblé dès ")
                .append("qu’une unité J4/J5 le déclare dans la fenêtre ; il est exploitable si, à ")
                .append("`asOf`, état canonique, détail et trois familles J5 proviennent tous de ")
                .append("preuves directes parse-compatibles, même si elles préexistent. ")
                .append("`EMPTY_VALID` est disponible ")
                .append("mais jamais strictement complet. Avec `FULL_ATTEMPT_LEDGER`, les trois ")
                .append("comptes d’appels restent exacts même sans dossier ciblé ou exploitable ; ")
                .append("seuls les ratios à dénominateur nul restent `NOT_MEASURED`.\n");
    }

    private static void renderDecisionAssessments(
            StringBuilder output,
            List<J8BenchmarkReport.DecisionAssessment> assessments) {
        output.append("\n## Dimensions de décision J9\n\n")
                .append("| Dimension | État | Preuve | Limite explicite |\n")
                .append("|---|---|---|---|\n");
        for (J8BenchmarkReport.DecisionAssessment assessment : assessments) {
            output.append('|').append(assessment.dimension().name())
                    .append('|').append(assessment.state().name())
                    .append('|').append(assessment.evidenceCode())
                    .append('|').append(escape(assessment.explanation()))
                    .append("|\n");
        }
    }

    private static void renderLimitations(
            StringBuilder output,
            List<J8BenchmarkReport.Limitation> limitations) {
        output.append("\n## Limites de mesure\n\n");
        if (limitations.isEmpty()) {
            output.append("- Aucune limite supplémentaire déclarée.\n");
            return;
        }
        for (J8BenchmarkReport.Limitation limitation : limitations) {
            output.append("- `").append(limitation.code()).append("` — ")
                    .append(escape(limitation.explanation())).append('\n');
        }
    }

    private static void keyValue(StringBuilder output, String key, String value) {
        output.append("- **").append(escape(key)).append(":** ")
                .append(escape(value)).append('\n');
    }

    private static String rate(J8BenchmarkReport.Rate rate) {
        if (rate.percent().isEmpty()) {
            return NOT_MEASURED;
        }
        return rate.numerator() + "/" + rate.denominator() + " ("
                + rate.percent().orElseThrow().toPlainString() + "%)";
    }

    private static String latency(J8BenchmarkReport.LatencyDistribution value) {
        if (value.sampleCount() == 0) {
            return NOT_MEASURED;
        }
        return "n=" + value.sampleCount()
                + ", min=" + value.minimumMs().orElseThrow() + " ms"
                + ", p50=" + value.p50Ms().orElseThrow() + " ms"
                + ", p95=" + value.p95Ms().orElseThrow() + " ms"
                + ", max=" + value.maximumMs().orElseThrow() + " ms";
    }

    private static String measuredCount(J8MeasurementState state, long value) {
        return state == J8MeasurementState.NOT_MEASURED
                ? NOT_MEASURED
                : Long.toString(value);
    }

    private static String endpointNames(List<SofascoreEndpointType> values) {
        return values.isEmpty()
                ? NOT_MEASURED
                : values.stream()
                        .map(Enum::name)
                        .sorted()
                        .reduce((left, right) -> left + ", " + right)
                        .orElse(NOT_MEASURED);
    }

    private static String instant(Optional<Instant> value) {
        return value.map(Object::toString).orElse(NOT_MEASURED);
    }

    private static String text(Optional<String> value) {
        return value.map(J8BenchmarkMarkdownRenderer::escape).orElse(NOT_MEASURED);
    }

    private static String decimal(Optional<BigDecimal> value, String suffix) {
        return value.map(number -> number.toPlainString() + suffix).orElse(NOT_MEASURED);
    }

    private static String escape(String value) {
        String safe = value.replace("\r", " ")
                .replace("\n", " ")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        safe = URI_SCHEME.matcher(safe).replaceAll(result ->
                result.group().replace(":", "&#58;"));
        safe = BARE_WWW_AUTOLINK.matcher(safe).replaceAll(result ->
                result.group().replace(".", "&#46;"));
        return safe.replace("\\", "&#92;")
                .replace("`", "&#96;")
                .replace("!", "&#33;")
                .replace("@", "&#64;")
                .replace("[", "&#91;")
                .replace("]", "&#93;")
                .replace("(", "&#40;")
                .replace(")", "&#41;")
                .replace("|", "&#124;");
    }
}
