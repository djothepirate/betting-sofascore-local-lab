package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

public record J8BenchmarkReport(
        Instant generatedAt,
        Instant asOf,
        J8BenchmarkWindow effectiveWindow,
        J8MeasurementState state,
        String populationHash,
        EvidenceScope evidenceScope,
        List<EvidenceStratumMetrics> evidenceStrata,
        PageAndFamilyAvailability pageAndFamilyAvailability,
        CallSummary callSummary,
        List<EndpointMetrics> endpointMetrics,
        List<CompletenessBreakdown> completenessBreakdowns,
        LateChangeMetrics lateChangeMetrics,
        ParserStability parserStability,
        DossierEfficiency dossierEfficiency,
        List<DecisionAssessment> decisionDimensions,
        List<Limitation> limitations) {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern EVIDENCE_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,96}");

    public J8BenchmarkReport {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        asOf = Objects.requireNonNull(asOf, "asOf");
        if (generatedAt.isBefore(asOf)) {
            throw new IllegalArgumentException("generatedAt cannot precede asOf");
        }
        effectiveWindow = Objects.requireNonNull(effectiveWindow, "effectiveWindow");
        if (effectiveWindow.toExclusive().isPresent()
                && effectiveWindow.toExclusive().orElseThrow().isAfter(asOf)) {
            throw new IllegalArgumentException("effective window cannot extend after asOf");
        }
        state = Objects.requireNonNull(state, "state");
        populationHash = requireSha256(populationHash, "populationHash");
        evidenceScope = Objects.requireNonNull(evidenceScope, "evidenceScope");
        evidenceStrata = List.copyOf(Objects.requireNonNull(
                evidenceStrata, "evidenceStrata"));
        if (evidenceStrata.stream().map(EvidenceStratumMetrics::coverage)
                .distinct().count() != evidenceStrata.size()) {
            throw new IllegalArgumentException("benchmark evidence strata must be distinct");
        }
        pageAndFamilyAvailability = Objects.requireNonNull(
                pageAndFamilyAvailability, "pageAndFamilyAvailability");
        callSummary = Objects.requireNonNull(callSummary, "callSummary");
        endpointMetrics = List.copyOf(Objects.requireNonNull(
                endpointMetrics, "endpointMetrics"));
        completenessBreakdowns = List.copyOf(Objects.requireNonNull(
                completenessBreakdowns, "completenessBreakdowns"));
        lateChangeMetrics = Objects.requireNonNull(
                lateChangeMetrics, "lateChangeMetrics");
        parserStability = Objects.requireNonNull(parserStability, "parserStability");
        dossierEfficiency = Objects.requireNonNull(
                dossierEfficiency, "dossierEfficiency");
        decisionDimensions = List.copyOf(Objects.requireNonNull(
                decisionDimensions, "decisionDimensions"));
        limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations"));
        if (decisionDimensions.size() > DecisionDimension.values().length) {
            throw new IllegalArgumentException("too many decision assessments");
        }
        if (limitations.size() > 50) {
            throw new IllegalArgumentException("too many benchmark limitations");
        }
    }

    /**
     * Compatibility constructor for callers building a single-stratum report. The J8
     * aggregator uses the canonical constructor and always publishes all three strata.
     */
    public J8BenchmarkReport(
            Instant generatedAt,
            Instant asOf,
            J8BenchmarkWindow effectiveWindow,
            J8MeasurementState state,
            String populationHash,
            EvidenceScope evidenceScope,
            PageAndFamilyAvailability pageAndFamilyAvailability,
            CallSummary callSummary,
            List<EndpointMetrics> endpointMetrics,
            List<CompletenessBreakdown> completenessBreakdowns,
            LateChangeMetrics lateChangeMetrics,
            ParserStability parserStability,
            DossierEfficiency dossierEfficiency,
            List<DecisionAssessment> decisionDimensions,
            List<Limitation> limitations) {
        this(
                generatedAt,
                asOf,
                effectiveWindow,
                state,
                populationHash,
                evidenceScope,
                List.of(new EvidenceStratumMetrics(
                        evidenceScope.coverage(),
                        state,
                        evidenceScope.coverage() == EvidenceCoverage.FULL_ATTEMPT_LEDGER
                                ? evidenceScope.directAttemptCount()
                                : evidenceScope.persistedResponseCount(),
                        pageAndFamilyAvailability,
                        callSummary,
                        endpointMetrics,
                        completenessBreakdowns,
                        parserStability)),
                pageAndFamilyAvailability,
                callSummary,
                endpointMetrics,
                completenessBreakdowns,
                lateChangeMetrics,
                parserStability,
                dossierEfficiency,
                decisionDimensions,
                limitations);
    }

    public record EvidenceScope(
            EvidenceCoverage coverage,
            long providerCampaignCount,
            long directAttemptCount,
            long persistedResponseCount,
            long responseOnlyCount,
            long legacyBaselineCount,
            long manualImportCount,
            long cacheHitCount,
            long syntheticObservationCount,
            Optional<Instant> firstMeasuredAt,
            Optional<Instant> lastMeasuredAt) {

        public EvidenceScope {
            coverage = Objects.requireNonNull(coverage, "coverage");
            requireNonNegative(
                    providerCampaignCount,
                    directAttemptCount,
                    persistedResponseCount,
                    responseOnlyCount,
                    legacyBaselineCount,
                    manualImportCount,
                    cacheHitCount,
                    syntheticObservationCount);
            if (coverage == EvidenceCoverage.FULL_ATTEMPT_LEDGER
                    && persistedResponseCount > directAttemptCount) {
                throw new IllegalArgumentException(
                        "persisted responses cannot exceed direct attempts");
            }
            firstMeasuredAt = Objects.requireNonNull(firstMeasuredAt, "firstMeasuredAt");
            lastMeasuredAt = Objects.requireNonNull(lastMeasuredAt, "lastMeasuredAt");
            if (firstMeasuredAt.isPresent() != lastMeasuredAt.isPresent()) {
                throw new IllegalArgumentException(
                        "measurement coverage bounds must both be present or absent");
            }
            if (firstMeasuredAt.isPresent()
                    && firstMeasuredAt.orElseThrow().isAfter(lastMeasuredAt.orElseThrow())) {
                throw new IllegalArgumentException(
                        "measurement coverage bounds are reversed");
            }
        }
    }

    /**
     * Metrics whose denominators all belong to one evidence level. Keeping this
     * boundary explicit prevents FULL, RESPONSE_ONLY and baseline populations from
     * ever being added into a single exact rate.
     */
    public record EvidenceStratumMetrics(
            EvidenceCoverage coverage,
            J8MeasurementState state,
            long evidenceCount,
            PageAndFamilyAvailability pageAndFamilyAvailability,
            CallSummary callSummary,
            List<EndpointMetrics> endpointMetrics,
            List<CompletenessBreakdown> completenessBreakdowns,
            ParserStability parserStability) {

        public EvidenceStratumMetrics {
            coverage = Objects.requireNonNull(coverage, "coverage");
            state = Objects.requireNonNull(state, "state");
            requireNonNegative(evidenceCount);
            pageAndFamilyAvailability = Objects.requireNonNull(
                    pageAndFamilyAvailability, "pageAndFamilyAvailability");
            callSummary = Objects.requireNonNull(callSummary, "callSummary");
            endpointMetrics = List.copyOf(Objects.requireNonNull(
                    endpointMetrics, "endpointMetrics"));
            completenessBreakdowns = List.copyOf(Objects.requireNonNull(
                    completenessBreakdowns, "completenessBreakdowns"));
            parserStability = Objects.requireNonNull(parserStability, "parserStability");
            if (evidenceCount == 0 && state != J8MeasurementState.NOT_MEASURED) {
                throw new IllegalArgumentException(
                        "an empty evidence stratum must remain NOT_MEASURED");
            }
        }
    }

    public record PageAndFamilyAvailability(
            J8MeasurementState state,
            long observedPageCount,
            OptionalLong lowestScheduledPage,
            OptionalLong highestScheduledPage,
            List<SofascoreEndpointType> availableFamilies,
            List<SofascoreEndpointType> unavailableFamilies) {

        public PageAndFamilyAvailability {
            state = Objects.requireNonNull(state, "state");
            requireNonNegative(observedPageCount);
            lowestScheduledPage = Objects.requireNonNull(
                    lowestScheduledPage, "lowestScheduledPage");
            highestScheduledPage = Objects.requireNonNull(
                    highestScheduledPage, "highestScheduledPage");
            if (lowestScheduledPage.isPresent() != highestScheduledPage.isPresent()) {
                throw new IllegalArgumentException(
                        "scheduled page bounds must both be present or absent");
            }
            if (lowestScheduledPage.isPresent()) {
                long first = lowestScheduledPage.orElseThrow();
                long last = highestScheduledPage.orElseThrow();
                if (first < 1 || first > last) {
                    throw new IllegalArgumentException("scheduled page bounds are invalid");
                }
            }
            availableFamilies = List.copyOf(Objects.requireNonNull(
                    availableFamilies, "availableFamilies"));
            unavailableFamilies = List.copyOf(Objects.requireNonNull(
                    unavailableFamilies, "unavailableFamilies"));
            if (availableFamilies.stream().distinct().count() != availableFamilies.size()
                    || unavailableFamilies.stream().distinct().count()
                            != unavailableFamilies.size()
                    || availableFamilies.stream().anyMatch(unavailableFamilies::contains)) {
                throw new IllegalArgumentException(
                        "availability family sets must be distinct and disjoint");
            }
        }
    }

    public record CallSummary(
            J8MeasurementState state,
            long directAttemptCount,
            long responseReceivedCount,
            long parsedCount,
            long refusalCount,
            long endpointUnavailableCount,
            long incompleteAttemptCount,
            long errorCount,
            Rate responseRate,
            Rate parsingRate,
            Rate refusalRate,
            Rate endpointUnavailableRate,
            Rate errorRate) {

        public CallSummary {
            state = Objects.requireNonNull(state, "state");
            requireNonNegative(
                    directAttemptCount,
                    responseReceivedCount,
                    parsedCount,
                    refusalCount,
                    endpointUnavailableCount,
                    incompleteAttemptCount,
                    errorCount);
            if (responseReceivedCount > directAttemptCount
                    || parsedCount > responseReceivedCount
                    || refusalCount > directAttemptCount
                    || endpointUnavailableCount > directAttemptCount
                    || incompleteAttemptCount > directAttemptCount
                    || errorCount > directAttemptCount) {
                throw new IllegalArgumentException("call summary populations are inconsistent");
            }
            responseRate = Objects.requireNonNull(responseRate, "responseRate");
            parsingRate = Objects.requireNonNull(parsingRate, "parsingRate");
            refusalRate = Objects.requireNonNull(refusalRate, "refusalRate");
            endpointUnavailableRate = Objects.requireNonNull(
                    endpointUnavailableRate, "endpointUnavailableRate");
            errorRate = Objects.requireNonNull(errorRate, "errorRate");
        }
    }

    public record EndpointMetrics(
            SofascoreEndpointType endpoint,
            J8MeasurementState state,
            J8MeasurementState attemptMeasurementState,
            J8MeasurementState parsingMeasurementState,
            J8MeasurementState schemaBreakMeasurementState,
            long directAttemptCount,
            long responseEvidenceCount,
            long parsedCount,
            long endpointUnavailableCount,
            long refusalCount,
            long incompleteAttemptCount,
            long schemaIncompatibleCount,
            long unexpectedContentCount,
            long transportErrorCount,
            long otherErrorCount,
            long deduplicatedResponseCount,
            Rate endpointUnavailableRate,
            Rate compatibilityRate,
            Rate errorRate,
            LatencyDistribution latency,
            List<String> parserVersions) {

        public EndpointMetrics {
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            state = Objects.requireNonNull(state, "state");
            attemptMeasurementState = Objects.requireNonNull(
                    attemptMeasurementState, "attemptMeasurementState");
            parsingMeasurementState = Objects.requireNonNull(
                    parsingMeasurementState, "parsingMeasurementState");
            schemaBreakMeasurementState = Objects.requireNonNull(
                    schemaBreakMeasurementState, "schemaBreakMeasurementState");
            requireNonNegative(
                    directAttemptCount,
                    responseEvidenceCount,
                    parsedCount,
                    endpointUnavailableCount,
                    refusalCount,
                    incompleteAttemptCount,
                    schemaIncompatibleCount,
                    unexpectedContentCount,
                    transportErrorCount,
                    otherErrorCount,
                    deduplicatedResponseCount);
            endpointUnavailableRate = Objects.requireNonNull(
                    endpointUnavailableRate, "endpointUnavailableRate");
            compatibilityRate = Objects.requireNonNull(
                    compatibilityRate, "compatibilityRate");
            errorRate = Objects.requireNonNull(errorRate, "errorRate");
            if (attemptMeasurementState == J8MeasurementState.NOT_MEASURED
                    && (directAttemptCount != 0
                            || incompleteAttemptCount != 0
                            || endpointUnavailableRate.denominator() != 0
                            || errorRate.denominator() != 0)) {
                throw new IllegalArgumentException(
                        "unmeasured endpoint attempts cannot expose an attempt denominator");
            }
            if (attemptMeasurementState != J8MeasurementState.NOT_MEASURED
                    && (responseEvidenceCount > directAttemptCount
                            || incompleteAttemptCount > directAttemptCount
                            || endpointUnavailableRate.numerator()
                                    != endpointUnavailableCount
                            || endpointUnavailableRate.denominator()
                                    != directAttemptCount)) {
                throw new IllegalArgumentException(
                        "endpoint response and incomplete populations exceed attempts");
            }
            if (parsingMeasurementState == J8MeasurementState.NOT_MEASURED
                    && (parsedCount != 0 || compatibilityRate.denominator() != 0)) {
                throw new IllegalArgumentException(
                        "unmeasured endpoint parsing cannot expose parsing values");
            }
            latency = Objects.requireNonNull(latency, "latency");
            parserVersions = List.copyOf(Objects.requireNonNull(
                    parserVersions, "parserVersions"));
            if (parsingMeasurementState == J8MeasurementState.NOT_MEASURED
                    && !parserVersions.isEmpty()) {
                throw new IllegalArgumentException(
                        "unmeasured endpoint parsing cannot expose parser versions");
            }
            if (parserVersions.stream().anyMatch(value -> value == null
                    || value.isBlank()
                    || value.length() > 32
                    || value.chars().anyMatch(Character::isISOControl))) {
                throw new IllegalArgumentException("parser versions must be bounded safe values");
            }
        }
    }

    public record CompletenessBreakdown(
            SofascoreEndpointType endpoint,
            Optional<String> competition,
            Optional<String> season,
            Optional<String> eventStatus,
            long observationCount,
            long completeCount,
            long partialCount,
            long emptyValidCount,
            long unavailableCount,
            Optional<BigDecimal> weightedSignalCoveragePercent,
            J8MeasurementState state) {

        public CompletenessBreakdown {
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            competition = boundedOptional(competition, "competition", 200);
            season = boundedOptional(season, "season", 100);
            eventStatus = boundedOptional(eventStatus, "eventStatus", 64);
            requireNonNegative(
                    observationCount,
                    completeCount,
                    partialCount,
                    emptyValidCount,
                    unavailableCount);
            if (completeCount + partialCount + emptyValidCount + unavailableCount
                    > observationCount) {
                throw new IllegalArgumentException(
                        "completeness categories exceed their observation population");
            }
            weightedSignalCoveragePercent = percentageOptional(
                    weightedSignalCoveragePercent, "weightedSignalCoveragePercent");
            state = Objects.requireNonNull(state, "state");
        }
    }

    public record LateChangeMetrics(
            J8MeasurementState state,
            long analyzedEventCount,
            long analyzedProviderVersionCount,
            long lateEnrichmentCount,
            long lateCorrectionCount,
            LatencyDistribution lateChangeDelay) {

        public LateChangeMetrics {
            state = Objects.requireNonNull(state, "state");
            requireNonNegative(
                    analyzedEventCount,
                    analyzedProviderVersionCount,
                    lateEnrichmentCount,
                    lateCorrectionCount);
            lateChangeDelay = Objects.requireNonNull(lateChangeDelay, "lateChangeDelay");
        }
    }

    public record ParserStability(
            J8MeasurementState state,
            J8MeasurementState schemaBreakMeasurementState,
            long eligibleResponseCount,
            long parsedCount,
            long schemaIncompatibleCount,
            long unexpectedContentCount,
            long parserVersionCount,
            long parserVersionTransitionCount,
            Rate compatibilityRate,
            Optional<Instant> firstBreakAt,
            Optional<Instant> lastBreakAt,
            Optional<Instant> firstRecoveryAt,
            List<ParserVersionMetrics> versions) {

        public ParserStability {
            state = Objects.requireNonNull(state, "state");
            schemaBreakMeasurementState = Objects.requireNonNull(
                    schemaBreakMeasurementState, "schemaBreakMeasurementState");
            requireNonNegative(
                    eligibleResponseCount,
                    parsedCount,
                    schemaIncompatibleCount,
                    unexpectedContentCount,
                    parserVersionCount,
                    parserVersionTransitionCount);
            if (parsedCount + schemaIncompatibleCount + unexpectedContentCount
                    != eligibleResponseCount) {
                throw new IllegalArgumentException(
                        "parser stability populations are inconsistent");
            }
            compatibilityRate = Objects.requireNonNull(
                    compatibilityRate, "compatibilityRate");
            firstBreakAt = Objects.requireNonNull(firstBreakAt, "firstBreakAt");
            lastBreakAt = Objects.requireNonNull(lastBreakAt, "lastBreakAt");
            firstRecoveryAt = Objects.requireNonNull(firstRecoveryAt, "firstRecoveryAt");
            long breakCount = schemaIncompatibleCount + unexpectedContentCount;
            if ((breakCount > 0) != firstBreakAt.isPresent()
                    || firstBreakAt.isPresent() != lastBreakAt.isPresent()) {
                throw new IllegalArgumentException(
                        "parser break bounds must match observed break outcomes");
            }
            if (firstBreakAt.isPresent()
                    && firstBreakAt.orElseThrow().isAfter(lastBreakAt.orElseThrow())) {
                throw new IllegalArgumentException("parser break bounds are reversed");
            }
            if (firstRecoveryAt.isPresent()
                    && (lastBreakAt.isEmpty()
                            || firstRecoveryAt.orElseThrow()
                                    .isBefore(lastBreakAt.orElseThrow()))) {
                throw new IllegalArgumentException(
                        "parser recovery cannot precede the last break");
            }
            versions = List.copyOf(Objects.requireNonNull(versions, "versions"));
            if (parserVersionCount != versions.size()) {
                throw new IllegalArgumentException(
                        "parser version count must match version rows");
            }
        }
    }

    public record ParserVersionMetrics(
            SofascoreEndpointType endpoint,
            String parserVersion,
            long responseCount,
            long parsedCount,
            long incompatibleCount,
            Optional<Instant> firstObservedAt,
            Optional<Instant> lastObservedAt) {

        public ParserVersionMetrics {
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            parserVersion = requireBoundedText(parserVersion, "parserVersion", 32);
            requireNonNegative(responseCount, parsedCount, incompatibleCount);
            if (parsedCount + incompatibleCount > responseCount) {
                throw new IllegalArgumentException(
                        "parser version populations are inconsistent");
            }
            firstObservedAt = Objects.requireNonNull(firstObservedAt, "firstObservedAt");
            lastObservedAt = Objects.requireNonNull(lastObservedAt, "lastObservedAt");
            if (firstObservedAt.isPresent() != lastObservedAt.isPresent()) {
                throw new IllegalArgumentException(
                        "parser observation bounds must both be present or absent");
            }
            if (firstObservedAt.isPresent()
                    && firstObservedAt.orElseThrow().isAfter(lastObservedAt.orElseThrow())) {
                throw new IllegalArgumentException("parser observation bounds are reversed");
            }
        }
    }

    public record DossierEfficiency(
            J8MeasurementState state,
            long targetedEventCount,
            long exploitableEventCount,
            long strictlyCompleteEventCount,
            long discoveryOverhead,
            long marginalCallCount,
            long effectiveCallCount,
            Rate exploitableRate,
            Rate strictlyCompleteRate,
            Optional<BigDecimal> marginalCallsPerExploitableDossier,
            Optional<BigDecimal> effectiveCallsPerExploitableDossier) {

        public DossierEfficiency {
            state = Objects.requireNonNull(state, "state");
            requireNonNegative(
                    targetedEventCount,
                    exploitableEventCount,
                    strictlyCompleteEventCount,
                    discoveryOverhead,
                    marginalCallCount,
                    effectiveCallCount);
            if (exploitableEventCount > targetedEventCount
                    || strictlyCompleteEventCount > exploitableEventCount) {
                throw new IllegalArgumentException(
                        "dossier efficiency populations are inconsistent");
            }
            exploitableRate = Objects.requireNonNull(exploitableRate, "exploitableRate");
            strictlyCompleteRate = Objects.requireNonNull(
                    strictlyCompleteRate, "strictlyCompleteRate");
            marginalCallsPerExploitableDossier = requireNonNegativeDecimal(
                    marginalCallsPerExploitableDossier,
                    "marginalCallsPerExploitableDossier");
            effectiveCallsPerExploitableDossier = requireNonNegativeDecimal(
                    effectiveCallsPerExploitableDossier,
                    "effectiveCallsPerExploitableDossier");
            if (exploitableEventCount == 0
                    && (marginalCallsPerExploitableDossier.isPresent()
                            || effectiveCallsPerExploitableDossier.isPresent())) {
                throw new IllegalArgumentException(
                        "calls per exploitable dossier require a non-zero denominator");
            }
        }

        private static Optional<BigDecimal> requireNonNegativeDecimal(
                Optional<BigDecimal> value,
                String name) {
            Objects.requireNonNull(value, name);
            value.ifPresent(number -> {
                if (number.signum() < 0) {
                    throw new IllegalArgumentException(
                            name + " cannot be negative");
                }
            });
            return value;
        }
    }

    public record Rate(
            long numerator,
            long denominator,
            Optional<BigDecimal> percent) {

        public Rate {
            requireNonNegative(numerator, denominator);
            if (numerator > denominator) {
                throw new IllegalArgumentException("rate numerator exceeds denominator");
            }
            percent = percentageOptional(percent, "percent");
            if ((denominator == 0) != percent.isEmpty()) {
                throw new IllegalArgumentException(
                        "a rate percent is present if and only if its denominator is non-zero");
            }
        }
    }

    public record LatencyDistribution(
            long sampleCount,
            OptionalLong minimumMs,
            OptionalLong p50Ms,
            OptionalLong p95Ms,
            OptionalLong maximumMs) {

        public LatencyDistribution {
            requireNonNegative(sampleCount);
            minimumMs = Objects.requireNonNull(minimumMs, "minimumMs");
            p50Ms = Objects.requireNonNull(p50Ms, "p50Ms");
            p95Ms = Objects.requireNonNull(p95Ms, "p95Ms");
            maximumMs = Objects.requireNonNull(maximumMs, "maximumMs");
            boolean valuesPresent = minimumMs.isPresent()
                    && p50Ms.isPresent()
                    && p95Ms.isPresent()
                    && maximumMs.isPresent();
            if ((sampleCount > 0) != valuesPresent) {
                throw new IllegalArgumentException(
                        "latency values are present if and only if samples exist");
            }
            if (valuesPresent) {
                long minimum = minimumMs.orElseThrow();
                long p50 = p50Ms.orElseThrow();
                long p95 = p95Ms.orElseThrow();
                long maximum = maximumMs.orElseThrow();
                requireNonNegative(minimum, p50, p95, maximum);
                if (minimum > p50 || p50 > p95 || p95 > maximum) {
                    throw new IllegalArgumentException(
                            "latency distribution must be monotonically ordered");
                }
            }
        }

        public static LatencyDistribution notMeasured() {
            return new LatencyDistribution(
                    0,
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty());
        }
    }

    public record DecisionAssessment(
            DecisionDimension dimension,
            J8MeasurementState state,
            String evidenceCode,
            String explanation) {

        public DecisionAssessment {
            dimension = Objects.requireNonNull(dimension, "dimension");
            state = Objects.requireNonNull(state, "state");
            evidenceCode = Objects.requireNonNull(evidenceCode, "evidenceCode").trim();
            if (!EVIDENCE_CODE_PATTERN.matcher(evidenceCode).matches()) {
                throw new IllegalArgumentException("evidenceCode must be a bounded safe code");
            }
            explanation = requireBoundedText(explanation, "explanation", 500);
        }
    }

    public record Limitation(String code, String explanation) {

        public Limitation {
            code = Objects.requireNonNull(code, "code").trim();
            if (!EVIDENCE_CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException("limitation code must be a bounded safe code");
            }
            explanation = requireBoundedText(explanation, "explanation", 500);
        }
    }

    public enum EvidenceCoverage {
        FULL_ATTEMPT_LEDGER,
        RESPONSE_ONLY,
        LEGACY_BASELINE
    }

    public enum DecisionDimension {
        ACCESSIBILITY,
        COMPLETENESS,
        ACCURACY,
        FRESHNESS,
        STABILITY,
        EFFICIENCY,
        MAINTAINABILITY,
        RISK,
        ANALYTICAL_VALUE
    }

    private static Optional<BigDecimal> percentageOptional(
            Optional<BigDecimal> value,
            String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(number -> {
            if (number.compareTo(BigDecimal.ZERO) < 0
                    || number.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(name + " must be between 0 and 100");
            }
        });
        return value;
    }

    private static Optional<String> boundedOptional(
            Optional<String> value,
            String name,
            int maximumLength) {
        Objects.requireNonNull(value, name);
        return value.map(text -> requireBoundedText(text, name, maximumLength));
    }

    private static String requireBoundedText(
            String value,
            String name,
            int maximumLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must contain between 1 and " + maximumLength + " characters");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " cannot contain control characters");
        }
        return normalized;
    }

    private static String requireSha256(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
        return value;
    }

    private static void requireNonNegative(long... values) {
        for (long value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("benchmark counts cannot be negative");
            }
        }
    }
}
