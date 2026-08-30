package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.application.history.J6HistoryQueryService;
import com.bettingproject.sofascorelocal.application.history.J6HistoryClassifier;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.history.J6ExactTransitionClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J8BenchmarkReadStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ,
        propagation = Propagation.REQUIRES_NEW)
public class J8BenchmarkService {

    private static final Set<SofascoreEndpointType> J5_FAMILIES = Set.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    private final J8BenchmarkReadStore readStore;
    private final J6HistoryQueryService historyQueryService;
    private final J6HistoryClassifier historyClassifier;
    private final Clock clock;

    @Autowired
    public J8BenchmarkService(
            J8BenchmarkReadStore readStore,
            J6HistoryQueryService historyQueryService,
            J6HistoryClassifier historyClassifier) {
        this(readStore, historyQueryService, historyClassifier, Clock.systemUTC());
    }

    J8BenchmarkService(
            J8BenchmarkReadStore readStore,
            J6HistoryQueryService historyQueryService,
            J6HistoryClassifier historyClassifier,
            Clock clock) {
        this.readStore = Objects.requireNonNull(readStore, "readStore");
        this.historyQueryService = Objects.requireNonNull(
                historyQueryService, "historyQueryService");
        this.historyClassifier = Objects.requireNonNull(
                historyClassifier, "historyClassifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public J8BenchmarkReport load(J8BenchmarkWindow window) {
        return load(window, clock.instant());
    }

    public J8BenchmarkReport load(J8BenchmarkWindow window, Instant asOf) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(asOf, "asOf");
        J8BenchmarkWindow effectiveWindow = window.boundedAt(asOf);

        try {
            if (effectiveWindow.empty()) {
                return aggregate(
                        effectiveWindow,
                        asOf,
                        new J8BenchmarkReadEvidence(
                                List.of(), List.of(), List.of(), 0, 0),
                        new J8DirectObservationCohorts(List.of()),
                        List.of());
            }
            J8BenchmarkReadEvidence evidence = Objects.requireNonNull(
                    readStore.readEvidence(effectiveWindow, asOf), "read evidence");
            J8DirectObservationCohorts cohorts = Objects.requireNonNull(
                    readStore.readDirectObservationCohorts(effectiveWindow, asOf),
                    "direct observation cohorts");
            List<J8LateChangeEvidence> lateChanges = List.copyOf(
                    Objects.requireNonNull(
                            readStore.readLateChanges(effectiveWindow, asOf),
                            "late changes"));
            return aggregate(effectiveWindow, asOf, evidence, cohorts, lateChanges);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("local J8 evidence is incoherent", exception);
        }
    }

    private J8BenchmarkReport aggregate(
            J8BenchmarkWindow window,
            Instant asOf,
            J8BenchmarkReadEvidence evidence,
            J8DirectObservationCohorts cohorts,
            List<J8LateChangeEvidence> lateChanges) {
        validateReferences(evidence, cohorts);
        J8BenchmarkReport.EvidenceCoverage coverage = coverage(evidence);
        List<J8BenchmarkReadEvidence.LegacyResponseEvidence> legacyStratum =
                selectedLegacyResponses(coverage, evidence.legacyResponses());
        List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responseOnlyStratum =
                legacyResponses(evidence.legacyResponses(), false);
        List<J8BenchmarkReadEvidence.LegacyResponseEvidence> baselineStratum =
                legacyResponses(evidence.legacyResponses(), true);
        List<J8BenchmarkReadEvidence.UnitEvidence> ledgerUnits = evidence.units().stream()
                .filter(unit -> unit.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .toList();
        List<J8BenchmarkReadEvidence.UnitEvidence> directUnits = evidence.units().stream()
                .filter(unit -> unit.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .filter(unit -> unit.attemptId().isPresent())
                .sorted(Comparator
                        .comparing((J8BenchmarkReadEvidence.UnitEvidence unit) ->
                                unit.attemptStartedAt().orElseThrow())
                        .thenComparingLong(J8BenchmarkReadEvidence.UnitEvidence::unitId))
                .toList();

        List<J8BenchmarkReport.EvidenceStratumMetrics> evidenceStrata = List.of(
                fullAttemptLedgerStratum(evidence, ledgerUnits, directUnits, cohorts),
                historicalStratum(
                        J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY,
                        responseOnlyStratum,
                        cohorts),
                historicalStratum(
                        J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE,
                        baselineStratum,
                        cohorts));
        J8BenchmarkReport.EvidenceStratumMetrics selectedStratum = evidenceStrata.stream()
                .filter(stratum -> stratum.coverage() == coverage)
                .findFirst()
                .orElseThrow();
        J8BenchmarkReport.CallSummary callSummary = selectedStratum.callSummary();
        List<J8BenchmarkReport.EndpointMetrics> endpoints =
                selectedStratum.endpointMetrics();
        J8BenchmarkReport.ParserStability parserStability =
                selectedStratum.parserStability();
        J8BenchmarkReport.PageAndFamilyAvailability availability =
                selectedStratum.pageAndFamilyAvailability();
        List<J8BenchmarkReport.CompletenessBreakdown> completeness =
                selectedStratum.completenessBreakdowns();
        J8BenchmarkReport.DossierEfficiency dossierEfficiency = dossierEfficiency(
                coverage, evidence, directUnits, cohorts);
        J8BenchmarkReport.LateChangeMetrics lateChangeMetrics = lateChangeMetrics(lateChanges);
        J8MeasurementState reportState = reportState(
                evidence,
                directUnits,
                legacyStratum,
                cohorts,
                lateChanges,
                callSummary.state(),
                endpoints,
                completeness,
                parserStability,
                dossierEfficiency);
        J8BenchmarkReport.EvidenceScope scope = evidenceScope(
                coverage, evidence, directUnits, legacyStratum, lateChanges);
        List<J8BenchmarkReport.DecisionAssessment> decisions = decisions(
                coverage,
                callSummary,
                endpoints,
                completeness,
                lateChangeMetrics,
                parserStability,
                dossierEfficiency);
        List<J8BenchmarkReport.Limitation> limitations = limitations(
                coverage, evidence, reportState);
        return new J8BenchmarkReport(
                asOf,
                asOf,
                window,
                reportState,
                populationHash(
                        window, asOf, evidence, cohorts, lateChanges),
                scope,
                evidenceStrata,
                availability,
                callSummary,
                endpoints,
                completeness,
                lateChangeMetrics,
                parserStability,
                dossierEfficiency,
                decisions,
                limitations);
    }

    private static J8BenchmarkReport.EvidenceStratumMetrics fullAttemptLedgerStratum(
            J8BenchmarkReadEvidence evidence,
            List<J8BenchmarkReadEvidence.UnitEvidence> ledgerUnits,
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits,
            J8DirectObservationCohorts cohorts) {
        J8BenchmarkReport.CallSummary calls = callSummary(
                J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                evidence,
                directUnits);
        long providerCampaigns = evidence.campaigns().stream()
                .filter(campaign -> campaign.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .count();
        long evidenceCount = providerCampaigns + ledgerUnits.size();
        J8MeasurementState state = evidenceCount == 0
                ? J8MeasurementState.NOT_MEASURED
                : calls.state();
        return new J8BenchmarkReport.EvidenceStratumMetrics(
                J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                state,
                evidenceCount,
                availability(ledgerUnits),
                calls,
                endpointMetrics(directUnits),
                completeness(
                        J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER,
                        cohorts),
                parserStability(directUnits));
    }

    private static J8BenchmarkReport.EvidenceStratumMetrics historicalStratum(
            J8BenchmarkReport.EvidenceCoverage coverage,
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses,
            J8DirectObservationCohorts cohorts) {
        if (coverage == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER) {
            throw new IllegalArgumentException("historical evidence needs a historical stratum");
        }
        return new J8BenchmarkReport.EvidenceStratumMetrics(
                coverage,
                responses.isEmpty()
                        ? J8MeasurementState.NOT_MEASURED
                        : J8MeasurementState.PARTIAL,
                responses.size(),
                legacyAvailability(responses),
                unmeasuredCalls(),
                legacyEndpointMetrics(responses),
                completeness(coverage, cohorts),
                legacyParserStability(responses));
    }

    private static J8BenchmarkReport.CallSummary unmeasuredCalls() {
        return new J8BenchmarkReport.CallSummary(
                J8MeasurementState.NOT_MEASURED,
                0, 0, 0, 0, 0, 0, 0,
                rate(0, 0), rate(0, 0), rate(0, 0), rate(0, 0), rate(0, 0));
    }

    private static void validateReferences(
            J8BenchmarkReadEvidence evidence,
            J8DirectObservationCohorts cohorts) {
        Set<java.util.UUID> campaignIds = evidence.campaigns().stream()
                .map(J8BenchmarkReadEvidence.CampaignEvidence::campaignId)
                .collect(Collectors.toUnmodifiableSet());
        if (campaignIds.size() != evidence.campaigns().size()) {
            throw new IllegalStateException("duplicate campaign evidence");
        }
        Set<Long> unitIds = new java.util.HashSet<>();
        for (J8BenchmarkReadEvidence.UnitEvidence unit : evidence.units()) {
            if (!campaignIds.contains(unit.campaignId()) || !unitIds.add(unit.unitId())) {
                throw new IllegalStateException("unit evidence is not uniquely correlated");
            }
            if (unit.attemptId().isPresent()
                    && unit.resolutionSource().isPresent()
                    && unit.resolutionSource().orElseThrow()
                            != J8BenchmarkResolutionSource.PROVIDER) {
                throw new IllegalStateException(
                        "a direct attempt cannot use a non-provider resolution");
            }
        }
        Map<Long, J8DirectObservationCohorts.ObservationCohort> cohortsByUnit =
                new LinkedHashMap<>();
        for (J8DirectObservationCohorts.ObservationCohort cohort
                : cohorts.observations()) {
            if (cohortsByUnit.putIfAbsent(cohort.unitId(), cohort) != null) {
                throw new IllegalStateException(
                        "a J8 unit has duplicate normalized-observation cohorts");
            }
        }
        evidence.units().stream()
                .filter(unit -> unit.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .filter(unit -> unit.outcomeType().orElse(null)
                        == J8BenchmarkOutcomeType.PARSED)
                .filter(unit -> unit.campaignType()
                        == J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE1
                        || unit.campaignType()
                                == J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2
                        || unit.campaignType() == J8BenchmarkCampaignType.J5_EVENT_DATA)
                .forEach(unit -> {
                    J8DirectObservationCohorts.ObservationCohort cohort =
                            cohortsByUnit.get(unit.unitId());
                    if (cohort == null
                            || cohort.endpoint() != unit.endpoint()
                            || cohort.outcomeType().orElse(null)
                                    != J8BenchmarkOutcomeType.PARSED
                            || cohort.schemaStatus().orElse(null)
                                    != RawSnapshotSchemaStatus.PARSED
                            || !cohort.normalizedObservationPresent()
                            || cohort.currentEvidence()
                                    .correlatedObservationId().isEmpty()) {
                        throw new IllegalStateException(
                                "a parsed J4/J5 result lacks its normalized observation proof");
                    }
                });
    }

    private static J8BenchmarkReport.EvidenceCoverage coverage(
            J8BenchmarkReadEvidence evidence) {
        if (evidence.campaigns().stream().anyMatch(campaign ->
                campaign.executionMode() == J8BenchmarkExecutionMode.GUARDED_PROVIDER)) {
            return J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER;
        }
        boolean prospectiveResponse = evidence.legacyResponses().stream()
                .anyMatch(response -> response.persistenceOutcome()
                        != J6SnapshotOccurrenceOutcome.BASELINE);
        return prospectiveResponse
                ? J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY
                : J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE;
    }

    private static List<J8BenchmarkReadEvidence.LegacyResponseEvidence>
            selectedLegacyResponses(
                    J8BenchmarkReport.EvidenceCoverage coverage,
                    List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses) {
        if (coverage == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER) {
            return List.of();
        }
        return legacyResponses(
                responses,
                coverage == J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE);
    }

    private static List<J8BenchmarkReadEvidence.LegacyResponseEvidence> legacyResponses(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses,
            boolean baseline) {
        return responses.stream()
                .filter(response -> (response.persistenceOutcome()
                        == J6SnapshotOccurrenceOutcome.BASELINE) == baseline)
                .toList();
    }

    private static J8BenchmarkReport.EvidenceScope evidenceScope(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8BenchmarkReadEvidence evidence,
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits,
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> legacyStratum,
            List<J8LateChangeEvidence> lateChanges) {
        long campaignCount = evidence.campaigns().stream()
                .filter(campaign -> campaign.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .count();
        long persistedResponses = coverage
                == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                ? directUnits.stream().filter(
                        J8BenchmarkReadEvidence.UnitEvidence::responseReceived).count()
                : legacyStratum.size();
        long responseOnly = evidence.legacyResponses().stream()
                .filter(response -> response.persistenceOutcome()
                        != J6SnapshotOccurrenceOutcome.BASELINE)
                .count();
        long baselines = evidence.legacyResponses().stream()
                .filter(response -> response.persistenceOutcome()
                        == J6SnapshotOccurrenceOutcome.BASELINE)
                .count();
        long manualImports = evidence.manualImportOccurrenceCount();
        long cacheHits = evidence.units().stream()
                .filter(unit -> unit.resolutionSource().orElse(null)
                        == J8BenchmarkResolutionSource.CACHE)
                .count();
        List<Instant> measuredAt = new ArrayList<>();
        evidence.campaigns().forEach(campaign -> measuredAt.add(campaign.startedAt()));
        directUnits.forEach(unit -> measuredAt.add(unit.attemptStartedAt().orElseThrow()));
        evidence.legacyResponses().forEach(response -> measuredAt.add(response.requestedAt()));
        lateChanges.forEach(change -> measuredAt.add(change.receivedAt()));
        Optional<Instant> first = measuredAt.stream().min(Comparator.naturalOrder());
        Optional<Instant> last = measuredAt.stream().max(Comparator.naturalOrder());
        return new J8BenchmarkReport.EvidenceScope(
                coverage,
                campaignCount,
                directUnits.size(),
                persistedResponses,
                responseOnly,
                baselines,
                manualImports,
                cacheHits,
                evidence.excludedSyntheticObservationCount(),
                first,
                last);
    }

    private static J8BenchmarkReport.CallSummary callSummary(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8BenchmarkReadEvidence evidence,
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits) {
        if (coverage != J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER) {
            return unmeasuredCalls();
        }
        long attempts = directUnits.size();
        long responses = count(directUnits,
                J8BenchmarkReadEvidence.UnitEvidence::responseReceived);
        long parsed = outcomeCount(directUnits, J8BenchmarkOutcomeType.PARSED);
        long refusals = count(directUnits, unit -> isRefusal(unit.httpStatus()));
        long unavailable = outcomeCount(
                directUnits, J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE);
        long incomplete = count(directUnits, unit -> unit.outcomeType().isEmpty());
        long errors = count(directUnits, J8BenchmarkService::isOperationalError);
        long parsingEligible = count(directUnits, J8BenchmarkService::isParsingEligible);
        boolean hasProviderCampaign = evidence.campaigns().stream()
                .anyMatch(campaign -> campaign.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER);
        J8MeasurementState state = attempts == 0
                ? !hasProviderCampaign
                        ? J8MeasurementState.NOT_MEASURED
                        : J8MeasurementState.PARTIAL
                : directUnits.stream().allMatch(unit -> unit.outcomeType().isPresent())
                        ? J8MeasurementState.MEASURED
                        : J8MeasurementState.PARTIAL;
        return new J8BenchmarkReport.CallSummary(
                state,
                attempts,
                responses,
                parsed,
                refusals,
                unavailable,
                incomplete,
                errors,
                rate(responses, attempts),
                rate(parsed, parsingEligible),
                rate(refusals, attempts),
                rate(unavailable, attempts),
                rate(errors, attempts));
    }

    private static List<J8BenchmarkReport.EndpointMetrics> endpointMetrics(
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits) {
        Map<SofascoreEndpointType, List<J8BenchmarkReadEvidence.UnitEvidence>> groups =
                directUnits.stream().collect(Collectors.groupingBy(
                        J8BenchmarkReadEvidence.UnitEvidence::endpoint,
                        () -> new EnumMap<>(SofascoreEndpointType.class),
                        Collectors.toList()));
        List<J8BenchmarkReport.EndpointMetrics> result = new ArrayList<>();
        for (SofascoreEndpointType endpoint : SofascoreEndpointType.values()) {
            List<J8BenchmarkReadEvidence.UnitEvidence> units = groups.get(endpoint);
            if (units == null || units.isEmpty()) {
                continue;
            }
            long parsed = outcomeCount(units, J8BenchmarkOutcomeType.PARSED);
            long unavailable = outcomeCount(units, J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE);
            long refused = count(units, unit -> isRefusal(unit.httpStatus()));
            long incomplete = count(units, unit -> unit.outcomeType().isEmpty());
            long incompatible = outcomeCount(units, J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE);
            long unexpected = outcomeCount(units, J8BenchmarkOutcomeType.UNEXPECTED_CONTENT);
            long transport = outcomeCount(units, J8BenchmarkOutcomeType.TRANSPORT_FAILURE);
            long other = count(units, unit -> unit.outcomeType().map(outcome -> switch (outcome) {
                case HTTP_ERROR, PERSISTENCE_FAILURE, PROCESSING_FAILURE,
                        OPERATOR_STOP, NOT_REACHED_AFTER_TERMINAL_FAILURE -> true;
                default -> false;
            }).orElse(false));
            long eligible = count(units, J8BenchmarkService::isParsingEligible);
            long allErrors = count(units, J8BenchmarkService::isOperationalError);
            long responses = count(
                    units, J8BenchmarkReadEvidence.UnitEvidence::responseReceived);
            List<Long> latencies = units.stream()
                    .filter(J8BenchmarkReadEvidence.UnitEvidence::responseReceived)
                    .filter(unit -> unit.latencyMillis().isPresent())
                    .map(unit -> unit.latencyMillis().orElseThrow())
                    .sorted()
                    .toList();
            List<String> versions = units.stream()
                    .filter(J8BenchmarkService::isParsingEligible)
                    .flatMap(unit -> unit.parserVersion().stream())
                    .distinct()
                    .sorted()
                    .toList();
            J8MeasurementState endpointState = units.stream()
                    .allMatch(unit -> unit.outcomeType().isPresent())
                            ? J8MeasurementState.MEASURED
                            : J8MeasurementState.PARTIAL;
            J8MeasurementState parsingState = eligible == 0
                    ? J8MeasurementState.NOT_MEASURED
                    : J8MeasurementState.MEASURED;
            result.add(new J8BenchmarkReport.EndpointMetrics(
                    endpoint,
                    endpointState,
                    endpointState,
                    parsingState,
                    parsingState,
                    units.size(),
                    responses,
                    parsed,
                    unavailable,
                    refused,
                    incomplete,
                    incompatible,
                    unexpected,
                    transport,
                    other,
                    count(units,
                            J8BenchmarkReadEvidence.UnitEvidence::deduplicatedResponse),
                    rate(unavailable, units.size()),
                    rate(parsed, eligible),
                    rate(allErrors, units.size()),
                    latency(latencies),
                    versions));
        }
        return List.copyOf(result);
    }

    private static List<J8BenchmarkReport.EndpointMetrics> legacyEndpointMetrics(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses) {
        Map<SofascoreEndpointType, List<J8BenchmarkReadEvidence.LegacyResponseEvidence>> groups =
                responses.stream().collect(Collectors.groupingBy(
                        J8BenchmarkReadEvidence.LegacyResponseEvidence::endpoint,
                        () -> new EnumMap<>(SofascoreEndpointType.class),
                        Collectors.toList()));
        List<J8BenchmarkReport.EndpointMetrics> result = new ArrayList<>();
        for (SofascoreEndpointType endpoint : SofascoreEndpointType.values()) {
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> rows = groups.get(endpoint);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            long parsed = legacySchemaCount(rows, RawSnapshotSchemaStatus.PARSED);
            long unavailable = count(rows, row -> isHttpStatus(row.httpStatus(), 404)
                    || row.schemaStatus().orElse(null)
                            == RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
            long refused = count(rows, row -> isRefusal(row.httpStatus()));
            long incompatible = legacySchemaCount(
                    rows, RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
            long unexpected = legacySchemaCount(
                    rows, RawSnapshotSchemaStatus.UNEXPECTED_CONTENT);
            long transport = legacySchemaCount(rows, RawSnapshotSchemaStatus.TRANSPORT_ERROR);
            long other = count(rows, J8BenchmarkService::isLegacyOtherError);
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> eligible = rows.stream()
                    .filter(J8BenchmarkService::isLegacyParsingEligible)
                    .toList();
            List<Long> latencies = rows.stream()
                    .flatMapToLong(row -> row.latencyMillis().stream())
                    .sorted()
                    .boxed()
                    .toList();
            List<String> versions = eligible.stream()
                    .flatMap(row -> row.parserVersion().stream())
                    .distinct()
                    .sorted()
                    .toList();
            result.add(new J8BenchmarkReport.EndpointMetrics(
                    endpoint,
                    J8MeasurementState.PARTIAL,
                    J8MeasurementState.NOT_MEASURED,
                    eligible.isEmpty()
                            ? J8MeasurementState.NOT_MEASURED
                            : J8MeasurementState.PARTIAL,
                    J8MeasurementState.NOT_MEASURED,
                    0,
                    rows.size(),
                    parsed,
                    unavailable,
                    refused,
                    0,
                    incompatible,
                    unexpected,
                    transport,
                    other,
                    count(rows, row -> row.persistenceOutcome()
                            == J6SnapshotOccurrenceOutcome.DEDUPLICATED),
                    rate(0, 0),
                    rate(0, 0),
                    rate(0, 0),
                    latency(latencies),
                    versions));
        }
        return List.copyOf(result);
    }

    private static J8BenchmarkReport.PageAndFamilyAvailability availability(
            List<J8BenchmarkReadEvidence.UnitEvidence> ledgerUnits) {
        List<J8BenchmarkReadEvidence.UnitEvidence> declaredPages = ledgerUnits.stream()
                .filter(unit -> unit.endpoint() == SofascoreEndpointType.SCHEDULED_EVENTS)
                .toList();
        OptionalLong minimumPage = declaredPages.stream()
                .mapToLong(J8BenchmarkReadEvidence.UnitEvidence::unitOrdinal).min();
        OptionalLong maximumPage = declaredPages.stream()
                .mapToLong(J8BenchmarkReadEvidence.UnitEvidence::unitOrdinal).max();
        List<SofascoreEndpointType> available = ledgerUnits.stream()
                .filter(unit -> unit.outcomeType().orElse(null) == J8BenchmarkOutcomeType.PARSED)
                .map(J8BenchmarkReadEvidence.UnitEvidence::endpoint)
                .distinct()
                .sorted()
                .toList();
        List<SofascoreEndpointType> unavailable = ledgerUnits.stream()
                .filter(unit -> unit.outcomeType().orElse(null)
                        == J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE)
                .map(J8BenchmarkReadEvidence.UnitEvidence::endpoint)
                .filter(Predicate.not(available::contains))
                .distinct()
                .sorted()
                .toList();
        J8MeasurementState state = ledgerUnits.isEmpty()
                ? J8MeasurementState.NOT_MEASURED
                : ledgerUnits.stream().allMatch(unit ->
                        unit.outcomeType().filter(outcome ->
                                outcome == J8BenchmarkOutcomeType.PARSED
                                        || outcome
                                                == J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE)
                                .isPresent())
                                ? J8MeasurementState.MEASURED
                                : J8MeasurementState.PARTIAL;
        return new J8BenchmarkReport.PageAndFamilyAvailability(
                state,
                declaredPages.size(),
                minimumPage,
                maximumPage,
                available,
                unavailable);
    }

    private static J8BenchmarkReport.PageAndFamilyAvailability legacyAvailability(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses) {
        if (responses.isEmpty()) {
            return new J8BenchmarkReport.PageAndFamilyAvailability(
                    J8MeasurementState.NOT_MEASURED,
                    0,
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    List.of(),
                    List.of());
        }
        List<SofascoreEndpointType> available = responses.stream()
                .filter(row -> row.schemaStatus().orElse(null)
                        == RawSnapshotSchemaStatus.PARSED)
                .map(J8BenchmarkReadEvidence.LegacyResponseEvidence::endpoint)
                .distinct()
                .sorted()
                .toList();
        List<SofascoreEndpointType> unavailable = responses.stream()
                .filter(row -> isHttpStatus(row.httpStatus(), 404)
                        || row.schemaStatus().orElse(null)
                                == RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE)
                .map(J8BenchmarkReadEvidence.LegacyResponseEvidence::endpoint)
                .filter(Predicate.not(available::contains))
                .distinct()
                .sorted()
                .toList();
        return new J8BenchmarkReport.PageAndFamilyAvailability(
                J8MeasurementState.PARTIAL,
                0,
                OptionalLong.empty(),
                OptionalLong.empty(),
                available,
                unavailable);
    }

    private static List<J8BenchmarkReport.CompletenessBreakdown> completeness(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8DirectObservationCohorts cohorts) {
        List<CompletenessEvidenceRow> eligible = coverage
                == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                ? cohorts.observations().stream()
                        .filter(J8DirectObservationCohorts.ObservationCohort::providerAttempted)
                        .map(row -> new CompletenessEvidenceRow(
                                row.unitId(),
                                row.endpoint(),
                                row.completenessStatus(),
                                row.presentSignals(),
                                row.expectedSignals(),
                                row.normalizedObservationPresent(),
                                row.competition(),
                                row.season(),
                                row.eventStatus()))
                        .toList()
                : cohorts.historicalObservations().stream()
                        .filter(row -> (row.persistenceOutcome()
                                == J6SnapshotOccurrenceOutcome.BASELINE)
                                == (coverage
                                        == J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE))
                        .map(row -> new CompletenessEvidenceRow(
                                row.occurrenceId(),
                                row.endpoint(),
                                row.completenessStatus(),
                                row.presentSignals(),
                                row.expectedSignals(),
                                row.normalizedObservationPresent(),
                                row.competition(),
                                row.season(),
                                row.eventStatus()))
                        .toList();
        Map<CompletenessKey, List<CompletenessEvidenceRow>> groups =
                new LinkedHashMap<>();
        eligible.stream()
                .filter(row -> J5_FAMILIES.contains(row.endpoint()))
                .filter(row -> row.completenessStatus().isPresent())
                .filter(row -> row.completenessStatus().orElseThrow()
                                == J5CompletenessStatus.UNAVAILABLE
                        || row.normalizedObservationPresent())
                .sorted(Comparator
                        .comparing(CompletenessEvidenceRow::endpoint)
                        .thenComparing(row -> row.competition().orElse(""))
                        .thenComparing(row -> row.season().orElse(""))
                        .thenComparing(row -> row.eventStatus().orElse(""))
                        .thenComparingLong(CompletenessEvidenceRow::evidenceId))
                .forEach(row -> groups.computeIfAbsent(new CompletenessKey(
                        row.endpoint(),
                        explicitDimension(row.competition()),
                        explicitDimension(row.season()),
                        explicitDimension(row.eventStatus())),
                        ignored -> new ArrayList<>()).add(row));
        List<J8BenchmarkReport.CompletenessBreakdown> result = new ArrayList<>();
        for (Map.Entry<CompletenessKey, List<CompletenessEvidenceRow>> entry
                : groups.entrySet()) {
            CompletenessKey key = entry.getKey();
            List<CompletenessEvidenceRow> rows = entry.getValue();
            long complete = completenessCount(rows, J5CompletenessStatus.COMPLETE);
            long partial = completenessCount(rows, J5CompletenessStatus.PARTIAL);
            long empty = completenessCount(rows, J5CompletenessStatus.EMPTY_VALID);
            long unavailable = completenessCount(rows, J5CompletenessStatus.UNAVAILABLE);
            List<CompletenessEvidenceRow> signalRows = rows.stream()
                    .filter(row -> row.expectedSignals().isPresent()
                            && row.expectedSignals().orElseThrow() > 0)
                    .filter(row -> row.presentSignals().isPresent())
                    .toList();
            long expectedSignals = signalRows.stream()
                    .mapToLong(row -> row.expectedSignals().orElseThrow())
                    .sum();
            long presentSignals = signalRows.stream()
                    .mapToLong(row -> row.presentSignals().orElseThrow())
                    .sum();
            Optional<BigDecimal> weightedSignalCoverage = expectedSignals == 0
                    ? Optional.empty()
                    : Optional.of(BigDecimal.valueOf(presentSignals)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(expectedSignals), 2,
                                    RoundingMode.HALF_UP));
            J8MeasurementState state = key.competition().orElseThrow().equals("UNKNOWN")
                    || key.season().orElseThrow().equals("UNKNOWN")
                    || key.eventStatus().orElseThrow().equals("UNKNOWN")
                    || rows.stream().anyMatch(row -> row.expectedSignals().isEmpty()
                            || row.presentSignals().isEmpty()
                            || (row.expectedSignals().orElseThrow() == 0
                                    && row.presentSignals().orElseThrow() > 0))
                    ? J8MeasurementState.PARTIAL
                    : J8MeasurementState.MEASURED;
            result.add(new J8BenchmarkReport.CompletenessBreakdown(
                    key.endpoint(),
                    key.competition(),
                    key.season(),
                    key.eventStatus(),
                    rows.size(),
                    complete,
                    partial,
                    empty,
                    unavailable,
                    weightedSignalCoverage,
                    state));
        }
        return List.copyOf(result);
    }

    private static J8BenchmarkReport.ParserStability parserStability(
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits) {
        List<J8BenchmarkReadEvidence.UnitEvidence> eligible = directUnits.stream()
                .filter(J8BenchmarkService::isParsingEligible)
                .toList();
        long parsed = outcomeCount(eligible, J8BenchmarkOutcomeType.PARSED);
        long incompatible = outcomeCount(eligible, J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE);
        long unexpected = outcomeCount(eligible, J8BenchmarkOutcomeType.UNEXPECTED_CONTENT);
        Map<ParserKey, List<J8BenchmarkReadEvidence.UnitEvidence>> groups = eligible.stream()
                .filter(unit -> unit.parserVersion().isPresent())
                .collect(Collectors.groupingBy(
                        unit -> new ParserKey(
                                unit.endpoint(), unit.parserVersion().orElseThrow()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<J8BenchmarkReport.ParserVersionMetrics> versions = groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<J8BenchmarkReadEvidence.UnitEvidence> units = entry.getValue();
                    Optional<Instant> first = units.stream()
                            .flatMap(unit -> unit.attemptStartedAt().stream())
                            .min(Comparator.naturalOrder());
                    Optional<Instant> last = units.stream()
                            .flatMap(unit -> unit.attemptStartedAt().stream())
                            .max(Comparator.naturalOrder());
                    return new J8BenchmarkReport.ParserVersionMetrics(
                            entry.getKey().endpoint(),
                            entry.getKey().parserVersion(),
                            units.size(),
                            outcomeCount(units, J8BenchmarkOutcomeType.PARSED),
                            outcomeCount(units, J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE),
                            first,
                            last);
                })
                .toList();
        long transitions = parserVersionTransitions(directUnits);
        ParserBreakTimeline breakTimeline = parserBreakTimeline(directUnits);
        J8MeasurementState state = eligible.isEmpty()
                ? J8MeasurementState.NOT_MEASURED
                : eligible.stream().allMatch(unit -> unit.parserVersion().isPresent())
                        ? J8MeasurementState.MEASURED
                        : J8MeasurementState.PARTIAL;
        return new J8BenchmarkReport.ParserStability(
                state,
                state,
                eligible.size(),
                parsed,
                incompatible,
                unexpected,
                versions.size(),
                transitions,
                rate(parsed, eligible.size()),
                breakTimeline.firstBreakAt(),
                breakTimeline.lastBreakAt(),
                breakTimeline.firstRecoveryAt(),
                versions);
    }

    private static J8BenchmarkReport.ParserStability legacyParserStability(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses) {
        List<J8BenchmarkReadEvidence.LegacyResponseEvidence> eligible = responses.stream()
                .filter(J8BenchmarkService::isLegacyParsingEligible)
                .sorted(Comparator
                        .comparing(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                        .thenComparingLong(
                                J8BenchmarkReadEvidence.LegacyResponseEvidence::occurrenceId))
                .toList();
        long parsed = legacySchemaCount(eligible, RawSnapshotSchemaStatus.PARSED);
        long incompatible = legacySchemaCount(
                eligible, RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
        long unexpected = legacySchemaCount(
                eligible, RawSnapshotSchemaStatus.UNEXPECTED_CONTENT);
        Map<ParserKey, List<J8BenchmarkReadEvidence.LegacyResponseEvidence>> groups =
                eligible.stream()
                        .filter(row -> row.parserVersion().isPresent())
                        .collect(Collectors.groupingBy(
                                row -> new ParserKey(
                                        row.endpoint(), row.parserVersion().orElseThrow()),
                                LinkedHashMap::new,
                                Collectors.toList()));
        List<J8BenchmarkReport.ParserVersionMetrics> versions = groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<J8BenchmarkReadEvidence.LegacyResponseEvidence> rows =
                            entry.getValue();
                    return new J8BenchmarkReport.ParserVersionMetrics(
                            entry.getKey().endpoint(),
                            entry.getKey().parserVersion(),
                            rows.size(),
                            legacySchemaCount(rows, RawSnapshotSchemaStatus.PARSED),
                            legacySchemaCount(
                                    rows, RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                            rows.stream()
                                    .map(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                                    .min(Comparator.naturalOrder()),
                            rows.stream()
                                    .map(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                                    .max(Comparator.naturalOrder()));
                })
                .toList();
        ParserBreakTimeline breakTimeline = legacyParserBreakTimeline(eligible);
        return new J8BenchmarkReport.ParserStability(
                eligible.isEmpty()
                        ? J8MeasurementState.NOT_MEASURED
                        : J8MeasurementState.PARTIAL,
                J8MeasurementState.NOT_MEASURED,
                eligible.size(),
                parsed,
                incompatible,
                unexpected,
                versions.size(),
                legacyParserVersionTransitions(eligible),
                rate(parsed, eligible.size()),
                breakTimeline.firstBreakAt(),
                breakTimeline.lastBreakAt(),
                breakTimeline.firstRecoveryAt(),
                versions);
    }

    private static long parserVersionTransitions(
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits) {
        long transitions = 0;
        Map<SofascoreEndpointType, List<J8BenchmarkReadEvidence.UnitEvidence>> groups =
                directUnits.stream()
                        .filter(J8BenchmarkService::isParsingEligible)
                        .filter(unit -> unit.parserVersion().isPresent())
                        .collect(Collectors.groupingBy(
                                J8BenchmarkReadEvidence.UnitEvidence::endpoint,
                                () -> new EnumMap<>(SofascoreEndpointType.class),
                                Collectors.toList()));
        for (List<J8BenchmarkReadEvidence.UnitEvidence> units : groups.values()) {
            String previous = null;
            for (J8BenchmarkReadEvidence.UnitEvidence unit : units.stream()
                    .sorted(Comparator
                            .comparing((J8BenchmarkReadEvidence.UnitEvidence value) ->
                                    value.attemptStartedAt().orElseThrow())
                            .thenComparingLong(J8BenchmarkReadEvidence.UnitEvidence::unitId))
                    .toList()) {
                String current = unit.parserVersion().orElseThrow();
                if (previous != null && !previous.equals(current)) {
                    transitions++;
                }
                previous = current;
            }
        }
        return transitions;
    }

    private static ParserBreakTimeline parserBreakTimeline(
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits) {
        List<J8BenchmarkReadEvidence.UnitEvidence> ordered = directUnits.stream()
                .filter(J8BenchmarkService::isParsingEligible)
                .sorted(Comparator
                        .comparing((J8BenchmarkReadEvidence.UnitEvidence unit) ->
                                unit.attemptStartedAt().orElseThrow())
                        .thenComparingLong(J8BenchmarkReadEvidence.UnitEvidence::unitId))
                .toList();
        int lastBreakIndex = -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (isParserBreak(ordered.get(index).outcomeType().orElseThrow())) {
                lastBreakIndex = index;
            }
        }
        if (lastBreakIndex < 0) {
            return ParserBreakTimeline.none();
        }
        Optional<Instant> first = ordered.stream()
                .filter(unit -> isParserBreak(unit.outcomeType().orElseThrow()))
                .map(unit -> unit.attemptStartedAt().orElseThrow())
                .min(Comparator.naturalOrder());
        Optional<Instant> last = Optional.of(
                ordered.get(lastBreakIndex).attemptStartedAt().orElseThrow());
        SofascoreEndpointType brokenEndpoint = ordered.get(lastBreakIndex).endpoint();
        Optional<Instant> recovery = ordered.subList(lastBreakIndex + 1, ordered.size())
                .stream()
                .filter(unit -> unit.endpoint() == brokenEndpoint)
                .filter(unit -> unit.outcomeType().orElseThrow()
                        == J8BenchmarkOutcomeType.PARSED)
                .map(unit -> unit.attemptStartedAt().orElseThrow())
                .findFirst();
        return new ParserBreakTimeline(first, last, recovery);
    }

    private static long legacyParserVersionTransitions(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses) {
        long transitions = 0;
        Map<SofascoreEndpointType,
                List<J8BenchmarkReadEvidence.LegacyResponseEvidence>> groups =
                responses.stream()
                        .filter(J8BenchmarkService::isLegacyParsingEligible)
                        .filter(row -> row.parserVersion().isPresent())
                        .collect(Collectors.groupingBy(
                                J8BenchmarkReadEvidence.LegacyResponseEvidence::endpoint,
                                () -> new EnumMap<>(SofascoreEndpointType.class),
                                Collectors.toList()));
        for (List<J8BenchmarkReadEvidence.LegacyResponseEvidence> rows : groups.values()) {
            String previous = null;
            for (J8BenchmarkReadEvidence.LegacyResponseEvidence row : rows.stream()
                    .sorted(Comparator
                            .comparing(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                            .thenComparingLong(
                                    J8BenchmarkReadEvidence.LegacyResponseEvidence::occurrenceId))
                    .toList()) {
                String current = row.parserVersion().orElseThrow();
                if (previous != null && !previous.equals(current)) {
                    transitions++;
                }
                previous = current;
            }
        }
        return transitions;
    }

    private static ParserBreakTimeline legacyParserBreakTimeline(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses) {
        List<J8BenchmarkReadEvidence.LegacyResponseEvidence> ordered = responses.stream()
                .filter(J8BenchmarkService::isLegacyParsingEligible)
                .sorted(Comparator
                        .comparing(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                        .thenComparingLong(
                                J8BenchmarkReadEvidence.LegacyResponseEvidence::occurrenceId))
                .toList();
        int lastBreakIndex = -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (isParserBreak(ordered.get(index).schemaStatus().orElseThrow())) {
                lastBreakIndex = index;
            }
        }
        if (lastBreakIndex < 0) {
            return ParserBreakTimeline.none();
        }
        Optional<Instant> first = ordered.stream()
                .filter(row -> isParserBreak(row.schemaStatus().orElseThrow()))
                .map(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                .min(Comparator.naturalOrder());
        Optional<Instant> last = Optional.of(ordered.get(lastBreakIndex).requestedAt());
        SofascoreEndpointType brokenEndpoint = ordered.get(lastBreakIndex).endpoint();
        Optional<Instant> recovery = ordered.subList(lastBreakIndex + 1, ordered.size())
                .stream()
                .filter(row -> row.endpoint() == brokenEndpoint)
                .filter(row -> row.schemaStatus().orElseThrow()
                        == RawSnapshotSchemaStatus.PARSED)
                .map(J8BenchmarkReadEvidence.LegacyResponseEvidence::requestedAt)
                .findFirst();
        return new ParserBreakTimeline(first, last, recovery);
    }

    private static J8BenchmarkReport.DossierEfficiency dossierEfficiency(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8BenchmarkReadEvidence evidence,
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits,
            J8DirectObservationCohorts cohorts) {
        long discoveryOverhead = count(directUnits, unit ->
                unit.campaignType() == J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS
                        || unit.campaignType()
                                == J8BenchmarkCampaignType.J3_TOURNAMENT_DISCOVERY);
        long marginalCalls = count(directUnits, unit ->
                unit.campaignType() == J8BenchmarkCampaignType.J4_EVENT_DETAILS_PHASE2
                        || unit.campaignType() == J8BenchmarkCampaignType.J5_EVENT_DATA);
        if (coverage != J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER) {
            return new J8BenchmarkReport.DossierEfficiency(
                    J8MeasurementState.NOT_MEASURED,
                    0, 0, 0, 0, 0, 0,
                    rate(0, 0), rate(0, 0), Optional.empty(), Optional.empty());
        }
        Map<Long, List<J8DirectObservationCohorts.ObservationCohort>> byEvent =
                cohorts.observations().stream()
                        .filter(J8DirectObservationCohorts.ObservationCohort::declaredInWindow)
                        .collect(Collectors.groupingBy(
                        J8DirectObservationCohorts.ObservationCohort::providerEventId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        long targeted = byEvent.size();
        long exploitable = byEvent.values().stream()
                .filter(J8BenchmarkService::isExploitable).count();
        long strict = byEvent.values().stream()
                .filter(J8BenchmarkService::isStrictlyComplete).count();
        boolean pending = cohorts.observations().stream()
                .filter(J8DirectObservationCohorts.ObservationCohort::declaredInWindow)
                .anyMatch(row -> row.outcomeType().isEmpty());
        J8MeasurementState state = pending || targeted == 0 || exploitable == 0
                ? J8MeasurementState.PARTIAL
                : J8MeasurementState.MEASURED;
        return new J8BenchmarkReport.DossierEfficiency(
                state,
                targeted,
                exploitable,
                strict,
                discoveryOverhead,
                marginalCalls,
                directUnits.size(),
                rate(exploitable, targeted),
                rate(strict, targeted),
                divide(marginalCalls, exploitable),
                divide(directUnits.size(), exploitable));
    }

    private static boolean isExploitable(
            List<J8DirectObservationCohorts.ObservationCohort> rows) {
        return rows.stream().anyMatch(J8BenchmarkService::hasAllCurrentComponents);
    }

    private static boolean isStrictlyComplete(
            List<J8DirectObservationCohorts.ObservationCohort> rows) {
        return rows.stream().anyMatch(row -> hasAllCurrentComponents(row)
                && row.currentStatisticsCompleteness().orElse(null)
                        == J5CompletenessStatus.COMPLETE
                && row.currentIncidentsCompleteness().orElse(null)
                        == J5CompletenessStatus.COMPLETE
                && row.currentLineupsCompleteness().orElse(null)
                        == J5CompletenessStatus.COMPLETE);
    }

    private static boolean hasAllCurrentComponents(
            J8DirectObservationCohorts.ObservationCohort row) {
        J8DirectObservationCohorts.DirectComponentState available =
                J8DirectObservationCohorts.DirectComponentState.AVAILABLE;
        return row.currentCanonicalState() == available
                && row.currentEventDetails() == available
                && row.currentStatistics() == available
                && row.currentIncidents() == available
                && row.currentLineups() == available
                && isAvailableCompleteness(row.currentStatisticsCompleteness())
                && isAvailableCompleteness(row.currentIncidentsCompleteness())
                && isAvailableCompleteness(row.currentLineupsCompleteness());
    }

    private static boolean isAvailableCompleteness(
            Optional<J5CompletenessStatus> completeness) {
        return completeness.filter(status ->
                status == J5CompletenessStatus.COMPLETE
                        || status == J5CompletenessStatus.PARTIAL
                        || status == J5CompletenessStatus.EMPTY_VALID).isPresent();
    }

    private J8BenchmarkReport.LateChangeMetrics lateChangeMetrics(
            List<J8LateChangeEvidence> changes) {
        if (changes.isEmpty()) {
            return new J8BenchmarkReport.LateChangeMetrics(
                    J8MeasurementState.NOT_MEASURED,
                    0, 0, 0, 0,
                    J8BenchmarkReport.LatencyDistribution.notMeasured());
        }
        Map<HistoryTargetKey, J8LateChangeEvidence> targets = new LinkedHashMap<>();
        for (J8LateChangeEvidence change : changes) {
            HistoryTargetKey key = new HistoryTargetKey(
                    change.canonicalEventId(), change.stream(), change.observationId());
            if (targets.putIfAbsent(key, change) != null) {
                throw new IllegalStateException("duplicate direct J6 late-change target");
            }
        }
        long enrichments = 0;
        long corrections = 0;
        List<Long> lateChangeDelays = new ArrayList<>();
        for (J8LateChangeEvidence change : changes) {
            boolean terminalBeforeCurrent = change.previousDirectStateStatus()
                    .map(historyClassifier::isTerminalStatus)
                    .orElse(false);
            J6ExactTransitionClassification comparison =
                    historyQueryService.classifyExactTransition(
                            change.canonicalEventId(),
                            change.stream(),
                            change.previousObservationId(),
                            change.observationId(),
                            terminalBeforeCurrent)
                    .orElseThrow(() -> new IllegalStateException(
                            "a bounded direct late-change pair has no J6 projection"));
            if (comparison.previousObservationId() != change.previousObservationId()
                    || comparison.observationId() != change.observationId()) {
                throw new IllegalStateException(
                        "the J6 comparison differs from the bounded direct proof");
            }
            J6HistoryClassification classification = comparison.classification();
            if (classification == J6HistoryClassification.LATE_ENRICHMENT) {
                enrichments++;
                lateChangeDelays.add(lateDelay(change, terminalBeforeCurrent));
            }
            else if (classification == J6HistoryClassification.LATE_CORRECTION) {
                corrections++;
                lateChangeDelays.add(lateDelay(change, terminalBeforeCurrent));
            }
        }
        long analyzedVersionCount = changes.size();
        long eventCount = changes.stream()
                .map(J8LateChangeEvidence::canonicalEventId)
                .distinct()
                .count();
        return new J8BenchmarkReport.LateChangeMetrics(
                analyzedVersionCount == 0
                        ? J8MeasurementState.NOT_MEASURED
                        : J8MeasurementState.MEASURED,
                eventCount,
                analyzedVersionCount,
                enrichments,
                corrections,
                latency(lateChangeDelays));
    }

    private static long lateDelay(
            J8LateChangeEvidence change,
            boolean terminalBeforeCurrent) {
        if (!terminalBeforeCurrent) {
            throw new IllegalStateException(
                    "J6 classified a late change without a terminal-state proof");
        }
        Instant terminalAt = change.previousDirectStateAt().orElseThrow(() ->
                new IllegalStateException(
                        "J6 classified a late change without terminal-time proof"));
        return Duration.between(terminalAt, change.receivedAt()).toMillis();
    }

    private static List<J8BenchmarkReport.DecisionAssessment> decisions(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8BenchmarkReport.CallSummary calls,
            List<J8BenchmarkReport.EndpointMetrics> endpoints,
            List<J8BenchmarkReport.CompletenessBreakdown> completeness,
            J8BenchmarkReport.LateChangeMetrics lateChanges,
            J8BenchmarkReport.ParserStability stability,
            J8BenchmarkReport.DossierEfficiency efficiency) {
        J8MeasurementState completenessState = completeness.isEmpty()
                ? J8MeasurementState.NOT_MEASURED
                : completeness.stream().anyMatch(value ->
                        value.state() == J8MeasurementState.PARTIAL)
                        ? J8MeasurementState.PARTIAL
                        : J8MeasurementState.MEASURED;
        J8MeasurementState accessibilityState = calls.state()
                == J8MeasurementState.NOT_MEASURED && !endpoints.isEmpty()
                        ? J8MeasurementState.PARTIAL
                        : calls.state();
        J8MeasurementState riskState = calls.state() == J8MeasurementState.NOT_MEASURED
                ? endpoints.isEmpty()
                        ? J8MeasurementState.NOT_MEASURED
                        : J8MeasurementState.PARTIAL
                : J8MeasurementState.PARTIAL;
        boolean transportLatencyMeasured = endpoints.stream()
                .anyMatch(endpoint -> endpoint.latency().sampleCount() > 0);
        boolean lateChangeDelayMeasured = lateChanges.lateChangeDelay().sampleCount() > 0;
        J8MeasurementState freshnessState = transportLatencyMeasured
                || lateChangeDelayMeasured
                        ? J8MeasurementState.PARTIAL
                        : J8MeasurementState.NOT_MEASURED;
        return List.of(
                decision(J8BenchmarkReport.DecisionDimension.ACCESSIBILITY,
                        accessibilityState,
                        coverage == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                                ? "DIRECT_CALL_LEDGER"
                                : "HISTORICAL_RESPONSE_STRATUM",
                        coverage == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                                ? "Succès, refus, 404 et erreurs sont bornés aux campagnes locales observées."
                                : "Seules les réponses persistées de la strate historique sélectionnée sont mesurées; les tentatives restent inconnues."),
                decision(J8BenchmarkReport.DecisionDimension.COMPLETENESS, completenessState,
                        "DIRECT_J5_COMPLETENESS",
                        "La complétude est ventilée seulement quand une observation J5 directe est corrélée."),
                decision(J8BenchmarkReport.DecisionDimension.ACCURACY,
                        J8MeasurementState.NOT_MEASURED,
                        "CONTROL_SOURCE_ABSENT",
                        "Aucune source de contrôle externe n'est intégrée à ce laboratoire."),
                decision(J8BenchmarkReport.DecisionDimension.FRESHNESS, freshnessState,
                        "RECEIPT_TIME_ONLY",
                        "Les latences transport et les éventuels délais tardifs utilisent les heures locales de requête/réception; le retard heure source vers réception reste NOT_MEASURED."),
                decision(J8BenchmarkReport.DecisionDimension.STABILITY, stability.state(),
                        "PARSER_OUTCOME_LEDGER",
                        "La stabilité décrit résultats de parsing et transitions, sans test de charge."),
                decision(J8BenchmarkReport.DecisionDimension.EFFICIENCY, efficiency.state(),
                        "DIRECT_DOSSIER_DENOMINATOR",
                        "Le dénominateur contient uniquement les événements ciblés par J4/J5 fournisseur."),
                decision(J8BenchmarkReport.DecisionDimension.MAINTAINABILITY,
                        J8MeasurementState.NOT_MEASURED,
                        "ADAPTATION_TIME_ABSENT",
                        "Aucun temps d'adaptation ou de restauration offline n'est chronométré."),
                decision(J8BenchmarkReport.DecisionDimension.RISK, riskState,
                        "LOCAL_FAILURE_EVIDENCE_ONLY",
                        "Seuls les refus et échecs locaux observés sont mesurés; aucune prédiction externe."),
                decision(J8BenchmarkReport.DecisionDimension.ANALYTICAL_VALUE,
                        J8MeasurementState.NOT_MEASURED,
                        "EXTERNAL_COMPARISON_ABSENT",
                        "Aucune donnée externe n'autorise une comparaison de valeur analytique."));
    }

    private static J8BenchmarkReport.DecisionAssessment decision(
            J8BenchmarkReport.DecisionDimension dimension,
            J8MeasurementState state,
            String code,
            String explanation) {
        return new J8BenchmarkReport.DecisionAssessment(
                dimension, state, code, explanation);
    }

    private static List<J8BenchmarkReport.Limitation> limitations(
            J8BenchmarkReport.EvidenceCoverage coverage,
            J8BenchmarkReadEvidence evidence,
            J8MeasurementState state) {
        List<J8BenchmarkReport.Limitation> result = new ArrayList<>();
        result.add(new J8BenchmarkReport.Limitation(
                "NO_EXTERNAL_CONTROL_SOURCE",
                "Exactitude, identité, horaire, statut, score et correction ne sont pas comparés à une source externe."));
        result.add(new J8BenchmarkReport.Limitation(
                "SOURCE_EVENT_TIME_ABSENT",
                "Les preuves ne portent pas une heure source homogène; le retard source reste NOT_MEASURED."));
        result.add(new J8BenchmarkReport.Limitation(
                "BOUNDED_MANUAL_CAMPAIGNS",
                "Latences et erreurs proviennent de campagnes manuelles bornées; ce rapport n'est pas un test de charge."));
        result.add(new J8BenchmarkReport.Limitation(
                "MAINTENANCE_TIME_ABSENT",
                "Les temps d'adaptation de parseur et de restauration offline ne sont pas instrumentés."));
        result.add(new J8BenchmarkReport.Limitation(
                "ANALYTICAL_COMPARATOR_ABSENT",
                "La valeur analytique relative reste NOT_MEASURED sans fournisseur comparateur."));
        result.add(new J8BenchmarkReport.Limitation(
                "DIRECT_RECEIPT_TIME_LATE_CHANGES",
                "Les cinq flux J6 sont classifiés, mais uniquement pour les observations DIRECT_LOCAL_ENDPOINT et selon leur heure de réception locale."));
        if (coverage != J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER) {
            result.add(new J8BenchmarkReport.Limitation(
                    "ATTEMPT_LEDGER_ABSENT",
                    "Les tentatives exactes et l'efficacité des dossiers restent NOT_MEASURED hors ledger J8."));
        }
        if (coverage == J8BenchmarkReport.EvidenceCoverage.RESPONSE_ONLY) {
            result.add(new J8BenchmarkReport.Limitation(
                    "RESPONSE_ONLY_STRATUM",
                    "Seules les occurrences directes non-BASELINE alimentent les métriques historiques; les baselines sont exclues sans addition."));
        }
        else if (coverage == J8BenchmarkReport.EvidenceCoverage.LEGACY_BASELINE) {
            result.add(new J8BenchmarkReport.Limitation(
                    "LEGACY_BASELINE_STRATUM",
                    "Faute de réponse prospective ou de ledger J8, seules les baselines directes alimentent la strate historique."));
        }
        if (evidence.campaigns().stream()
                .filter(campaign -> campaign.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .anyMatch(campaign -> campaign.finishedAt().isEmpty())) {
            result.add(new J8BenchmarkReport.Limitation(
                    "UNFINISHED_CAMPAIGN",
                    "Au moins une campagne sélectionnée est inachevée; les agrégats concernés sont partiels."));
        }
        if (evidence.campaigns().stream()
                .filter(campaign -> campaign.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .anyMatch(campaign -> campaign.terminalState().isPresent()
                        && campaign.terminalState().orElseThrow()
                                != J8BenchmarkCampaignTerminalState.COMPLETED)) {
            result.add(new J8BenchmarkReport.Limitation(
                    "NON_COMPLETED_CAMPAIGN",
                    "Au moins une campagne fournisseur sélectionnée est FAILED ou CANCELLED; le rapport global reste partiel."));
        }
        if (evidence.units().stream()
                .filter(unit -> unit.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .anyMatch(unit -> unit.resolutionSource().isPresent()
                        && unit.resolutionSource().orElseThrow()
                                != J8BenchmarkResolutionSource.PROVIDER)) {
            result.add(new J8BenchmarkReport.Limitation(
                    "NON_DIRECT_UNIT_RESOLUTION",
                    "Au moins une unité fournisseur est résolue par cache ou blocage sans appel direct; le rapport global reste partiel."));
        }
        if (evidence.units().stream()
                .filter(unit -> unit.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .anyMatch(unit -> unit.attemptId().isPresent()
                        && unit.outcomeType().isEmpty())) {
            result.add(new J8BenchmarkReport.Limitation(
                    "INCOMPLETE_ATTEMPT_EVIDENCE",
                    "Au moins une tentative directe ne possède pas encore de résultat terminal INCOMPLETE_ATTEMPT."));
        }
        if (state == J8MeasurementState.NOT_MEASURED) {
            result.add(new J8BenchmarkReport.Limitation(
                    "NO_ELIGIBLE_EVIDENCE",
                    "Aucune preuve locale éligible n'existe dans la fenêtre demandée."));
        }
        else if (coverage == J8BenchmarkReport.EvidenceCoverage.FULL_ATTEMPT_LEDGER
                && state != J8MeasurementState.MEASURED) {
            result.add(new J8BenchmarkReport.Limitation(
                    "MANDATORY_DIMENSIONS_INCOMPLETE",
                    "Le ledger existe, mais complétude J5, stabilité, latence ou coût par dossier ne possède pas encore un dénominateur exploitable complet."));
        }
        return List.copyOf(result);
    }

    private static J8MeasurementState reportState(
            J8BenchmarkReadEvidence evidence,
            List<J8BenchmarkReadEvidence.UnitEvidence> directUnits,
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> legacyStratum,
            J8DirectObservationCohorts cohorts,
            List<J8LateChangeEvidence> lateChanges,
            J8MeasurementState callState,
            List<J8BenchmarkReport.EndpointMetrics> endpoints,
            List<J8BenchmarkReport.CompletenessBreakdown> completeness,
            J8BenchmarkReport.ParserStability parserStability,
            J8BenchmarkReport.DossierEfficiency dossierEfficiency) {
        boolean hasProviderCampaign = evidence.campaigns().stream().anyMatch(campaign ->
                campaign.executionMode() == J8BenchmarkExecutionMode.GUARDED_PROVIDER);
        boolean anyEvidence = hasProviderCampaign
                || !directUnits.isEmpty()
                || !legacyStratum.isEmpty()
                || !cohorts.observations().isEmpty()
                || !cohorts.historicalObservations().isEmpty()
                || !lateChanges.isEmpty();
        if (!anyEvidence) {
            return J8MeasurementState.NOT_MEASURED;
        }
        List<J8BenchmarkReadEvidence.CampaignEvidence> providerCampaigns =
                evidence.campaigns().stream()
                        .filter(campaign -> campaign.executionMode()
                                == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                        .toList();
        boolean allProviderCampaignsCompleted = !providerCampaigns.isEmpty()
                && providerCampaigns.stream().allMatch(campaign ->
                        campaign.terminalState().orElse(null)
                                == J8BenchmarkCampaignTerminalState.COMPLETED);
        boolean allProviderUnitsResolvedDirectly = evidence.units().stream()
                .filter(unit -> unit.executionMode()
                        == J8BenchmarkExecutionMode.GUARDED_PROVIDER)
                .allMatch(unit -> unit.attemptId().isPresent()
                        && unit.outcomeType().isPresent()
                        && unit.resolutionSource().orElse(null)
                                == J8BenchmarkResolutionSource.PROVIDER);
        boolean completenessMeasured = !completeness.isEmpty()
                && completeness.stream().anyMatch(row ->
                        row.weightedSignalCoveragePercent().isPresent());
        boolean parserStabilityMeasured = parserStability.state()
                != J8MeasurementState.NOT_MEASURED;
        boolean dossierCostsMeasured = dossierEfficiency.state()
                != J8MeasurementState.NOT_MEASURED
                && dossierEfficiency.exploitableEventCount() > 0
                && dossierEfficiency.marginalCallsPerExploitableDossier().isPresent()
                && dossierEfficiency.effectiveCallsPerExploitableDossier().isPresent();
        boolean latencyMeasured = endpoints.stream()
                .anyMatch(endpoint -> endpoint.latency().sampleCount() > 0)
                && endpoints.stream()
                        .filter(endpoint -> endpoint.responseEvidenceCount() > 0)
                        .allMatch(endpoint -> endpoint.latency().sampleCount() > 0);
        return callState == J8MeasurementState.MEASURED
                && allProviderCampaignsCompleted
                && allProviderUnitsResolvedDirectly
                && completenessMeasured
                && parserStabilityMeasured
                && dossierCostsMeasured
                && latencyMeasured
                ? J8MeasurementState.MEASURED
                : J8MeasurementState.PARTIAL;
    }

    private static String populationHash(
            J8BenchmarkWindow window,
            Instant asOf,
            J8BenchmarkReadEvidence evidence,
            J8DirectObservationCohorts cohorts,
            List<J8LateChangeEvidence> lateChanges) {
        TreeSet<String> lines = new TreeSet<>();
        lines.add("j8-benchmark-v1");
        lines.add("window|from|" + window.fromInclusive()
                .map(Instant::toString).orElse("ALL_AVAILABLE"));
        lines.add("window|to|" + window.toExclusive()
                .map(Instant::toString).orElse("ALL_AVAILABLE"));
        lines.add("asOf|" + asOf);
        lines.add("manual-import-occurrences|" + evidence.manualImportOccurrenceCount());
        lines.add("synthetic-observations|" + evidence.excludedSyntheticObservationCount());
        evidence.campaigns().forEach(campaign -> {
            lines.add("campaign|" + campaign.campaignId() + '|'
                    + campaign.startedAt());
            if (campaign.terminalState().isPresent()) {
                lines.add("campaign-result|" + campaign.campaignId() + '|'
                        + campaign.finishedAt().orElseThrow() + '|'
                        + campaign.terminalState().orElseThrow().name() + '|'
                        + campaign.completedUnits().orElseThrow());
            }
        });
        evidence.units().forEach(unit -> lines.add("unit|" + unit.unitId() + '|'
                + unit.campaignId() + '|' + unit.endpoint().name() + '|'
                + unit.attemptId().stream().mapToObj(Long::toString).findFirst().orElse("-")
                + '|' + unit.outcomeType().map(Enum::name).orElse("-")
                + '|' + unit.resolutionSource().map(Enum::name).orElse("-")
                + '|' + optionalLong(unit.snapshotId())
                + '|' + optionalLong(unit.snapshotOccurrenceId())));
        evidence.legacyResponses().forEach(response -> lines.add("legacy|"
                + response.occurrenceId() + '|' + response.endpoint().name() + '|'
                + response.persistenceOutcome().name() + '|'
                + response.snapshotId() + '|'
                + response.normalizedObservationIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",", "[", "]"))));
        cohorts.observations().forEach(row -> lines.add("cohort|" + row.unitId() + '|'
                + row.providerEventId() + '|' + row.endpoint().name() + '|'
                + row.currentCanonicalState().name() + '|'
                + row.currentEventDetails().name() + '|'
                + row.currentStatistics().name() + '|'
                + row.currentIncidents().name() + '|'
                + row.currentLineups().name() + '|'
                + currentEvidenceFingerprint(row.currentEvidence())));
        cohorts.historicalObservations().forEach(row -> lines.add(
                "historical-cohort|" + row.occurrenceId() + '|'
                        + row.snapshotId() + '|'
                        + optionalLong(row.normalizedObservationId()) + '|'
                        + optionalLong(row.stateObservationId()) + '|'
                        + optionalLong(row.detailObservationId()) + '|'
                        + row.persistenceOutcome().name() + '|'
                        + row.endpoint().name()));
        lateChanges.forEach(change -> lines.add("late|" + change.canonicalEventId() + '|'
                + change.stream().name() + '|'
                + change.observationId() + '|'
                + change.previousObservationId() + '|'
                + change.sourceSnapshotId() + '|'
                + change.receivedAt() + '|'
                + optionalLong(change.previousDirectStateObservationId()) + '|'
                + change.previousDirectStateStatus().orElse("-") + '|'
                + change.previousDirectStateAt()
                        .map(Instant::toString).orElse("-")));
        return Sha256.hex(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private static String currentEvidenceFingerprint(
            J8DirectObservationCohorts.CurrentDirectEvidence evidence) {
        return optionalLong(evidence.correlatedObservationId()) + '|'
                + optionalLong(evidence.canonicalObservationId()) + '|'
                + componentFingerprint(evidence.eventDetails()) + '|'
                + componentFingerprint(evidence.statistics()) + '|'
                + componentFingerprint(evidence.incidents()) + '|'
                + componentFingerprint(evidence.lineups());
    }

    private static String componentFingerprint(
            J8DirectObservationCohorts.ComponentEvidence evidence) {
        return optionalLong(evidence.snapshotOccurrenceId()) + ','
                + optionalLong(evidence.snapshotId()) + ','
                + optionalLong(evidence.normalizedObservationId());
    }

    private static String optionalLong(OptionalLong value) {
        return value.isPresent() ? Long.toString(value.orElseThrow()) : "-";
    }

    private static boolean isParsingEligible(J8BenchmarkReadEvidence.UnitEvidence unit) {
        return unit.outcomeType().filter(outcome -> outcome == J8BenchmarkOutcomeType.PARSED
                || outcome == J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE
                || outcome == J8BenchmarkOutcomeType.UNEXPECTED_CONTENT).isPresent();
    }

    private static boolean isOperationalError(
            J8BenchmarkReadEvidence.UnitEvidence unit) {
        return unit.outcomeType().map(outcome -> switch (outcome) {
            case PARSED, ENDPOINT_UNAVAILABLE -> false;
            default -> true;
        }).orElse(true);
    }

    private static boolean isParserBreak(J8BenchmarkOutcomeType outcome) {
        return outcome == J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE
                || outcome == J8BenchmarkOutcomeType.UNEXPECTED_CONTENT;
    }

    private static boolean isParserBreak(RawSnapshotSchemaStatus status) {
        return status == RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE
                || status == RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
    }

    private static boolean isLegacyParsingEligible(
            J8BenchmarkReadEvidence.LegacyResponseEvidence response) {
        return response.schemaStatus().filter(status ->
                status == RawSnapshotSchemaStatus.PARSED
                        || status == RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE
                        || status == RawSnapshotSchemaStatus.UNEXPECTED_CONTENT).isPresent();
    }

    private static boolean isLegacyOtherError(
            J8BenchmarkReadEvidence.LegacyResponseEvidence response) {
        if (response.schemaStatus().orElse(null) == RawSnapshotSchemaStatus.RAW_ONLY) {
            return true;
        }
        return response.httpStatus().isPresent()
                && response.httpStatus().orElseThrow() >= 400
                && !isHttpStatus(response.httpStatus(), 404)
                && !isRefusal(response.httpStatus());
    }

    private static boolean isRefusal(OptionalInt status) {
        return status.isPresent()
                && (status.orElseThrow() == 401
                        || status.orElseThrow() == 403
                        || status.orElseThrow() == 429);
    }

    private static boolean isHttpStatus(OptionalInt status, int expected) {
        return status.isPresent() && status.orElseThrow() == expected;
    }

    private static long legacySchemaCount(
            List<J8BenchmarkReadEvidence.LegacyResponseEvidence> responses,
            RawSnapshotSchemaStatus status) {
        return count(responses, response -> response.schemaStatus().orElse(null) == status);
    }

    private static Optional<String> explicitDimension(Optional<String> value) {
        return Optional.of(value.orElse("UNKNOWN"));
    }

    private static long outcomeCount(
            List<J8BenchmarkReadEvidence.UnitEvidence> units,
            J8BenchmarkOutcomeType outcome) {
        return count(units, unit -> unit.outcomeType().orElse(null) == outcome);
    }

    private static long completenessCount(
            List<CompletenessEvidenceRow> rows,
            J5CompletenessStatus status) {
        return rows.stream()
                .filter(row -> row.completenessStatus().orElse(null) == status)
                .count();
    }

    private static <T> long count(List<T> values, Predicate<T> predicate) {
        return values.stream().filter(predicate).count();
    }

    private static J8BenchmarkReport.Rate rate(long numerator, long denominator) {
        Optional<BigDecimal> percent = denominator == 0
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(numerator)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP));
        return new J8BenchmarkReport.Rate(numerator, denominator, percent);
    }

    private static Optional<BigDecimal> divide(long numerator, long denominator) {
        return denominator == 0
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(numerator)
                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP));
    }

    private static J8BenchmarkReport.LatencyDistribution latency(List<Long> samples) {
        if (samples.isEmpty()) {
            return J8BenchmarkReport.LatencyDistribution.notMeasured();
        }
        List<Long> sorted = samples.stream().sorted().toList();
        return new J8BenchmarkReport.LatencyDistribution(
                sorted.size(),
                OptionalLong.of(sorted.getFirst()),
                OptionalLong.of(percentile(sorted, 50)),
                OptionalLong.of(percentile(sorted, 95)),
                OptionalLong.of(sorted.getLast()));
    }

    private static long percentile(List<Long> sorted, int percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile / 100.0d * sorted.size()) - 1);
        return sorted.get(index);
    }

    private record CompletenessKey(
            SofascoreEndpointType endpoint,
            Optional<String> competition,
            Optional<String> season,
            Optional<String> eventStatus) {
    }

    private record CompletenessEvidenceRow(
            long evidenceId,
            SofascoreEndpointType endpoint,
            Optional<J5CompletenessStatus> completenessStatus,
            OptionalInt presentSignals,
            OptionalInt expectedSignals,
            boolean normalizedObservationPresent,
            Optional<String> competition,
            Optional<String> season,
            Optional<String> eventStatus) {
    }

    private record ParserKey(
            SofascoreEndpointType endpoint,
            String parserVersion) implements Comparable<ParserKey> {

        @Override
        public int compareTo(ParserKey other) {
            int endpointComparison = endpoint.compareTo(other.endpoint);
            return endpointComparison != 0
                    ? endpointComparison
                    : parserVersion.compareTo(other.parserVersion);
        }
    }

    private record HistoryTargetKey(
            java.util.UUID canonicalEventId,
            J6HistoryStream stream,
            long observationId) {
    }

    private record ParserBreakTimeline(
            Optional<Instant> firstBreakAt,
            Optional<Instant> lastBreakAt,
            Optional<Instant> firstRecoveryAt) {

        private ParserBreakTimeline {
            firstBreakAt = Objects.requireNonNull(firstBreakAt, "firstBreakAt");
            lastBreakAt = Objects.requireNonNull(lastBreakAt, "lastBreakAt");
            firstRecoveryAt = Objects.requireNonNull(firstRecoveryAt, "firstRecoveryAt");
        }

        private static ParserBreakTimeline none() {
            return new ParserBreakTimeline(
                    Optional.empty(), Optional.empty(), Optional.empty());
        }
    }
}
