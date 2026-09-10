package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Disposable V8 loopback-evidence builder. It never starts Spring, a browser, a
 * store or a network client; it only verifies one already-completed local report.
 */
public final class V8MeasuredProfile {
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLIS = 1_000_000L;
    private static final long ROUNDING = 50 * MILLIS;
    private static final int MATCHES = 10;
    private static final int HOURLY_CALLS_PER_EVENT = 248;
    private static final int HOURLY_NINETY_PERCENT_BUDGET = 2_480;
    private static final List<SofascoreEndpointType> FAMILIES = List.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS);
    private static final String COLD_START_LANE = "INITIAL_COLD_START_STRESS";
    private static final String STEADY_LANE = "V8_STEADY";
    private static final long INTER_GROUP_SLOT_RESERVE_MILLIS = LiveSchedule.v8InterGroupSlotReserve().toMillis();
    private static final long REQUEST_EMISSION_HEAD_START_MILLIS = LiveSchedule.v8RequestEmissionHeadStart().toMillis();
    /**
     * The V8 production profile is an explicit, qualified upper-bound contract.
     * A new local measurement may prove that a bound is too small, but it must
     * never silently lower a bound just because one individual run was faster.
     */
    private static final Map<SofascoreEndpointType, QualifiedEnvelope> QUALIFIED_V8_ENVELOPES = Map.of(
            SofascoreEndpointType.EVENT_DETAILS, new QualifiedEnvelope(300, 500),
            SofascoreEndpointType.EVENT_INCIDENTS, new QualifiedEnvelope(300, 400),
            SofascoreEndpointType.EVENT_STATISTICS, new QualifiedEnvelope(350, 400),
            SofascoreEndpointType.EVENT_LINEUPS, new QualifiedEnvelope(300, 450));
    private static final long QUALIFIED_STRICT_GROUP_RESERVATION_MILLIS = qualifiedStrictGroupReservationMillis();
    private static final String PROFILE_NAME = "WO058-GROUPED-LIVE-V8-PROFILE-20260910.json";
    private static final String NATIVE_NAME = "WO058-GROUPED-LIVE-V8-NATIVE-20260910.json";

    private V8MeasuredProfile() { }

    private record QualifiedEnvelope(long requestMillis, long processingMillis) {
        private EndpointEnvelope toEndpointEnvelope() {
            return new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("expected native-input and fresh ignored-output-directory");
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path expectedInput = repository.resolve(".tmp/wo058-v8-sustained-qualification.json");
        require(input.equals(expectedInput) && Files.isRegularFile(input), "SUSTAINED_NATIVE_INPUT_REQUIRED");
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path allowed = repository.resolve(".tmp/wo058-v8-verification").toRealPath();
        require(output.getParent() != null && Files.isDirectory(output.getParent())
                && output.getParent().toRealPath().equals(allowed) && !Files.exists(output), "FRESH_IGNORED_OUTPUT_REQUIRED");

        byte[] nativeBytes = Files.readAllBytes(input);
        String nativeHash = sha256(nativeBytes);
        var mapper = JsonMapper.builder().build();
        JsonNode nativeDoc = mapper.readTree(nativeBytes);
        validateNative(nativeDoc);

        List<JsonNode> samples = array(nativeDoc, "samples");
        // V8's steady boundary and cadence claim use the actual departure
        // timestamp. A response may arrive late without making a second request
        // admissible, so receipt/commit timestamps remain observations only.
        List<JsonNode> steady = samples.stream().filter(s -> STEADY_LANE.equals(s.path("executionLane").asString())
                && number(s, "laneRequestedNanos") >= 300 * SECOND).toList();
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        var declaredEnvelopes = new LinkedHashMap<String, Object>();
        var observedRoundedEnvelopes = new LinkedHashMap<String, Object>();
        var measuredFamilies = new LinkedHashMap<String, Object>();
        for (SofascoreEndpointType endpoint : FAMILIES) {
            List<JsonNode> family = steady.stream()
                    .filter(s -> endpoint.name().equals(s.path("endpoint").asString())).toList();
            require(!family.isEmpty(), "FAMILY_MISSING_" + endpoint);
            long requestMaximum = maximum(family, "requestNanos");
            long processingMaximum = maximum(family, "processingNanos");
            long requestMillis = roundUp(requestMaximum);
            long processingMillis = roundUp(processingMaximum);
            require(requestMillis <= 10_000 && processingMillis <= 60_000, "ENVELOPE_EXCEEDS_DOMAIN_BOUND");
            QualifiedEnvelope qualified = qualifiedEnvelope(endpoint);
            require(requestMillis <= qualified.requestMillis() && processingMillis <= qualified.processingMillis(),
                    "STEADY_ENVELOPE_EXCEEDS_QUALIFIED_CANDIDATE_" + endpoint);
            var envelope = qualified.toEndpointEnvelope();
            envelopes.put(endpoint, envelope);
            declaredEnvelopes.put(endpoint.name(), Map.of(
                    "requestMillis", qualified.requestMillis(), "processingMillis", qualified.processingMillis()));
            observedRoundedEnvelopes.put(endpoint.name(), Map.of(
                    "requestMillis", requestMillis, "processingMillis", processingMillis));

            var metric = new LinkedHashMap<String, Object>();
            metric.put("steadySamples", family.size());
            metric.put("maximumRequestNanos", requestMaximum);
            metric.put("maximumProcessingNanos", processingMaximum);
            metric.put("requestSeconds", summary(family, "requestNanos"));
            metric.put("processingSeconds", summary(family, "processingNanos"));
            metric.put("httpSeconds", summary(family, "httpNanos"));
            metric.put("authorizationDelaySeconds", summary(family, "authorizationDelayNanos"));
            metric.put("limiterSleepSeconds", summary(family, "limiterSleepNanos"));
            metric.put("resilienceSqlSeconds", summary(family, "resilienceSqlNanos"));
            List<JsonNode> initial = samples.stream().filter(s -> endpoint.name().equals(s.path("endpoint").asString())
                    && COLD_START_LANE.equals(s.path("executionLane").asString())).toList();
            metric.put("initialRequestSeconds", summary(initial, "requestNanos"));
            metric.put("initialProcessingSeconds", summary(initial, "processingNanos"));
            measuredFamilies.put(endpoint.name(), metric);
            for (long eventId = 17_000_001; eventId < 17_000_001 + MATCHES; eventId++) {
                long selectedEventId = eventId;
                List<JsonNode> pair = family.stream().filter(s -> number(s, "providerEventId") == selectedEventId).toList();
                require(pair.size() >= 28, "STEADY_PAIR_TOO_SHORT_" + endpoint);
                checkStrictNormalPathDepartureIntervals(pair);
            }
        }

        long minimumGap = Long.MAX_VALUE;
        for (int i = 1; i < samples.size(); i++) {
            JsonNode previous = samples.get(i - 1);
            JsonNode current = samples.get(i);
            long gap = number(current, "requestedNanos") - number(previous, "resilienceFinishedNanos");
            require(gap >= 500 * MILLIS - MILLIS, "POST_COMPLETION_FENCE_FAILED");
            require(number(current, "serverArrivalNanos") > number(previous, "serverArrivalNanos"), "WIRE_ORDER_FAILED");
            minimumGap = Math.min(minimumGap, gap);
        }
        int minutePeak = rollingPeak(samples, 60 * SECOND, s -> number(s, "requestedNanos"));
        int hourPeak = rollingPeak(samples, 3600 * SECOND, s -> number(s, "requestedNanos"));
        int wireMinutePeak = rollingPeak(samples, 60 * SECOND, s -> number(s, "serverArrivalNanos"));
        int wireHourPeak = rollingPeak(samples, 3600 * SECOND, s -> number(s, "serverArrivalNanos"));
        require(minutePeak <= 45 && wireMinutePeak <= 45 && hourPeak <= 2_756 && wireHourPeak <= 2_756,
                "ROLLING_WINDOW_FAILED");

        var profile = new GroupedAdmissionProfile(envelopes, nativeHash, "live-v8");
        int capacity = LiveAdmissionPolicy.qualifiedCapacityV8(profile);
        boolean replayPassed = capacity == MATCHES && GroupedLiveAdmissionSimulationV8.fits(capacity, profile);
        require(capacity >= 0 && capacity <= MATCHES, "CALCULATED_CAPACITY_OUT_OF_SCOPE");
        long groupReservationMillis = LiveSchedule.v8StrictGroupReservation(profile).toMillis();
        require(groupReservationMillis * MATCHES <= 60_000, "V8_STRICT_PHASE_RESERVATION_BOUND_FAILED");
        require(number(nativeDoc.path("strictScheduler"), "groupPhaseReservationMillis") == groupReservationMillis,
                "STRICT_SCHEDULER_PHASE_RESERVATION_REQUIRED");

        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("schema", "wo058-grouped-live-capacity-evidence-v4");
        evidence.put("status", replayPassed ? "QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE" : "MEASURED_PROFILE_NOT_ADMISSIBLE");
        evidence.put("labStatus", List.of("EXPERIMENTAL", "LOCAL_ONLY", "NOT_PRODUCTION_APPROVED", "NO_CRITICAL_DEPENDENCY"));
        evidence.put("completedAt", nativeDoc.path("finishedAt").asString());
        evidence.put("policyVersion", "live-v8");
        evidence.put("flywayVersion", 51);
        evidence.put("qualifiedCapacity", capacity);
        evidence.put("envelopeScope", "STEADY_64_KIB_ONLY");
        evidence.put("nativeEvidence", Map.of("path", NATIVE_NAME, "sha256", nativeHash));
        evidence.put("endpointEnvelopes", declaredEnvelopes);
        evidence.put("observedRoundedSteadyEnvelopes", observedRoundedEnvelopes);
        evidence.put("envelopeBinding", "immutable qualified V8 upper bounds; each observed steady maximum is rounded upward to 50 ms and must remain within its corresponding bound; the profile is never automatically reduced");
        evidence.put("initialWave", Map.of("separatelyMeasured", true, "coveredBySteadyEnvelopes", false,
                "executionLane", COLD_START_LANE, "firstResponseBytesPerPair", 5_242_880, "pairs", 40,
                "postCompletionFenceMillis", number(nativeDoc.path("initialWave"), "postCompletionFenceMillis"),
                "strictLaneClearanceMillis", number(nativeDoc, "coldStartToStrictLaneClearanceMillis"),
                "strictLaneClearanceBasis", nativeDoc.path("initialWave").path("strictLaneClearanceBasis").asString(),
                "departureToStrictLaneClearanceMillis", number(nativeDoc, "coldStartDepartureToStrictLaneClearanceMillis"),
                "partOfSteadyV8Scheduler", false));
        var cadence = new LinkedHashMap<String, Object>();
        cadence.put("criticalSeconds", 60);
        cadence.put("lineupSeconds", 60);
        cadence.put("intraGroupDelayMillis", 0);
        cadence.put("interGroupDelayMillis", 500);
        cadence.put("interGroupSlotReserveMillis", INTER_GROUP_SLOT_RESERVE_MILLIS);
        cadence.put("requestEmissionHeadStartMillis", REQUEST_EMISSION_HEAD_START_MILLIS);
        cadence.put("strictGroupPhaseReservationMillis", groupReservationMillis);
        cadence.put("maximumEndpointsPerGroup", 4);
        cadence.put("maximumTemporalUtilization", BigDecimal.valueOf(groupReservationMillis * capacity)
                .divide(BigDecimal.valueOf(60_000), 3, java.math.RoundingMode.HALF_UP));
        cadence.put("minimumPostCompletionDelayMillis", 500);
        cadence.put("maximumDeparturesPer60Seconds", 45);
        cadence.put("maximumDeparturesPerHour", 2_756);
        cadence.put("strictNormalPathDepartureTimestamp", "requestedNanos");
        cadence.put("strictNormalPathDepartureCadenceScope", "per-event-family");
        cadence.put("strictNormalPathMaximumDepartureIntervalSeconds", 60);
        cadence.put("receiptAndCommitCadence", "observed as response-latency evidence; no hard provider receipt SLA claimed");
        evidence.put("cadence", cadence);
        var admission = new LinkedHashMap<String, Object>();
        admission.put("calculatedCapacity", capacity);
        admission.put("productionSchedulerScenarioCount", GroupedLiveAdmissionSimulationV8.SCENARIOS);
        admission.put("productionSchedulerScenariosExecutedForThisProfile", replayPassed);
        admission.put("profileAppliedAutomatically", false);
        admission.put("rounding", "each separate observed steady maximum is rounded upward to the next 50 ms boundary and must not exceed its immutable qualified V8 bound; no automatic profile reduction");
        admission.put("weightedMinuteMillisPerMatch", groupReservationMillis);
        admission.put("weightedMinuteMillisAtQualifiedCapacity", groupReservationMillis * capacity);
        admission.put("availableMinuteMillis", 60_000);
        admission.put("uncappedMeanBoundCapacity", 60_000 / groupReservationMillis);
        admission.put("hourlyCallsPerMatch", HOURLY_CALLS_PER_EVENT);
        admission.put("hourlyHeadroomCallsAtQualifiedCapacity", capacity * HOURLY_CALLS_PER_EVENT);
        admission.put("calculation", "immutable qualified V8 bounds feed min(10, floor((2756*9/10)/248), floor(60000/(sum(exchangeEnvelope + 500 ms terminal fence) + 1000 ms static scheduling reserve))); all rounded observed steady maxima must remain within those bounds before the production v8 scheduler replay; actual normal J4 REQUEST_SENT may be at most 500 ms after its head-start due time");
        evidence.put("admission", admission);
        evidence.put("tenMatchHourlyBoundary", Map.of(
                "ordinaryCallsPerHourPerMatch", 240, "initialCallsPerMatch", 4, "finalCallsPerMatch", 4,
                "totalCallsPerHourPerMatch", HOURLY_CALLS_PER_EVENT, "callsForTenMatches", MATCHES * HOURLY_CALLS_PER_EVENT,
                "ninetyPercentOfMaximumHourlyDepartures", HOURLY_NINETY_PERCENT_BUDGET,
                "remainingCallsAtIntegerNinetyPercentBoundary", HOURLY_NINETY_PERCENT_BUDGET - MATCHES * HOURLY_CALLS_PER_EVENT));
        evidence.put("budgets", Map.of("maximumCallsPerEvent", 2_500, "maximumCallsPerCampaign", 20_000,
                "maximumDurationMinutes", 240, "maximumRawBytes", 15_728_640_000L, "reservedFinalCallsPerActiveEvent", 4));
        var measurement = new LinkedHashMap<String, Object>();
        measurement.put("totalCalls", samples.size());
        measurement.put("steadyCalls", steady.size());
        measurement.put("coldStartCalls", samples.stream().filter(s -> COLD_START_LANE.equals(s.path("executionLane").asString())).count());
        measurement.put("pairsChecked", 40);
        measurement.put("effectiveRequestTimeoutMillis", number(nativeDoc, "effectiveRequestTimeoutMillis"));
        measurement.put("minimumPostCompletionDelaySeconds", seconds(minimumGap));
        measurement.put("maximumDeparturesPer60Seconds", minutePeak);
        measurement.put("maximumWireArrivalsPer60Seconds", wireMinutePeak);
        measurement.put("strictNormalPathDepartureTimestamp", "requestedNanos");
        measurement.put("strictNormalPathDepartureCadenceScope", "per-event-family");
        measurement.put("strictNormalPathMaximumDepartureIntervalSeconds", 60);
        measurement.put("endpointMetrics", measuredFamilies);
        evidence.put("measurement", measurement);
        var runtime = new LinkedHashMap<String, String>();
        for (Class<?> type : List.of(LiveAdmissionPolicy.class, GroupedLiveAdmissionSimulationV8.class,
                LiveSchedule.class, GroupedLiveScheduleV8.class, GroupedAdmissionProfile.class)) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var stream = type.getResourceAsStream(resource)) {
                require(stream != null, "RUNTIME_CLASS_EVIDENCE_MISSING");
                runtime.put(type.getName(), sha256(stream.readAllBytes()));
            }
        }
        evidence.put("productionClassSha256", runtime);
        Path builderSource = repository.resolve("docs/validation/v8-profile-builder/V8MeasuredProfile.java");
        evidence.put("profileBuilderSourceSha256", sha256(Files.readAllBytes(builderSource)));
        evidence.put("limitations", List.of(
                "Synthetic local loopback only; does not establish provider acceptance or an IP/quota threshold",
                "Steady 64 KiB response corpus, including LINEUPS V4 and J4 V4; initial 5 MiB responses are measured separately",
                "The 35-minute run does not saturate a full hourly window; the 2,480-call integer 90% hourly boundary is replayed by the production scheduler",
                "Nominal cadence remains subordinate to the shared persistent budget and previous campaigns",
                "Operator selection limit is independent and is neither read nor modified by this utility"));
        byte[] profileBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence);
        Files.createDirectory(output);
        Files.write(output.resolve("v8-admission-analysis.json"), profileBytes, StandardOpenOption.CREATE_NEW);
        if (replayPassed) {
            Files.write(output.resolve(NATIVE_NAME), nativeBytes, StandardOpenOption.CREATE_NEW);
            Files.write(output.resolve(PROFILE_NAME), profileBytes, StandardOpenOption.CREATE_NEW);
        }
        System.out.println("V8_MEASURED_CAPACITY=" + capacity);
        System.out.println("V8_PRODUCTION_SCENARIOS=" + (replayPassed ? GroupedLiveAdmissionSimulationV8.SCENARIOS : 0));
        System.out.println("V8_NATIVE_SHA256=" + nativeHash);
        System.out.println("V8_PROFILE_SHA256=" + sha256(profileBytes));
        System.out.println("V8_OPERATOR_CONFIGURATION_CHANGED=NO");
        if (!replayPassed) System.exit(2);
    }

    private static void validateNative(JsonNode nativeDoc) {
        require("PASSED".equals(nativeDoc.path("status").asString()), "NATIVE_NOT_PASSED");
        require("live-v8".equals(nativeDoc.path("policyVersion").asString()), "V8_REQUIRED");
        require("EXPERIMENTAL LOCAL_ONLY SYNTHETIC_LOOPBACK".equals(nativeDoc.path("scope").asString()), "SCOPE_MISMATCH");
        require(nativeDoc.path("loopbackTransportOnly").isBoolean()
                && nativeDoc.path("loopbackTransportOnly").asBoolean(), "LOOPBACK_TRANSPORT_REQUIRED");
        require(nativeDoc.path("loopbackOrigin").asString().matches("http://127\\.0\\.0\\.1:[1-9][0-9]*"),
                "LOOPBACK_ORIGIN_REQUIRED");
        for (String key : List.of("sustainedQualification", "productionPersistentResilience",
                "productionTransportDiagnosticPersistence", "productionDockerDfProbePerRequest", "workerAndChildrenClosed"))
            flag(nativeDoc, key, true);
        flag(nativeDoc, "operatorDatabaseUsed", false);
        for (String key : List.of("realProviderCalls", "offScopeRequests", "missedCycles"))
            require(number(nativeDoc, key) == 0, key);
        require(number(nativeDoc, "matches") == MATCHES && number(nativeDoc, "warmupSeconds") == 300
                && number(nativeDoc, "requiredSteadySeconds") == 1800, "MEASURED_RUN_SHAPE_MISMATCH");
        require(decimal(nativeDoc.path("steadyElapsedSeconds")).compareTo(BigDecimal.valueOf(1800)) >= 0
                && decimal(nativeDoc.path("strictLaneElapsedSeconds")).compareTo(BigDecimal.valueOf(2100)) >= 0,
                "RUN_TOO_SHORT");
        require(number(nativeDoc, "criticalIntervalSeconds") == 60 && number(nativeDoc, "lineupsIntervalSeconds") == 60
                && number(nativeDoc, "interGroupDelayMillis") == 500
                && number(nativeDoc, "minimumPostCompletionDelayMillis") == 500
                && number(nativeDoc, "maximumDeparturesPer60Seconds") == 45
                && number(nativeDoc, "maximumDeparturesPerHour") == 2_756, "POLICY_LIMIT_MISMATCH");
        require(decimal(nativeDoc.path("interGroupDelaySeconds")).compareTo(new BigDecimal("0.5")) == 0
                && decimal(nativeDoc.path("minimumPostCompletionDelaySeconds")).compareTo(new BigDecimal("0.5")) == 0,
                "POLICY_SECONDS_MISMATCH");
        require(number(nativeDoc, "normalPathDepartureCadenceSeconds") == 60
                && "requestedNanos".equals(nativeDoc.path("normalPathDepartureTimestamp").asString())
                && "per-event-family".equals(nativeDoc.path("normalPathDepartureCadenceScope").asString()),
                "STRICT_NORMAL_PATH_DEPARTURE_CADENCE_REQUIRED");
        validateQualifiedCandidateEnvelopes(nativeDoc);
        JsonNode strictScheduler = nativeDoc.path("strictScheduler");
        require(number(strictScheduler, "interGroupSlotReserveMillis") == INTER_GROUP_SLOT_RESERVE_MILLIS
                && number(strictScheduler, "requestEmissionHeadStartMillis") == REQUEST_EMISSION_HEAD_START_MILLIS
                && number(strictScheduler, "groupPhaseReservationMillis") == QUALIFIED_STRICT_GROUP_RESERVATION_MILLIS
                && "WAITING_CADENCE_RECHECK".equals(strictScheduler.path("normalJ4EmissionOverrunState").asString()),
                "STRICT_SCHEDULER_RESERVATION_REQUIRED");
        require(number(nativeDoc, "effectiveRequestTimeoutMillis") == 30_000, "OBSERVED_TIMEOUT_MISMATCH");
        JsonNode initialWave = nativeDoc.path("initialWave");
        require(COLD_START_LANE.equals(initialWave.path("executionLane").asString())
                && number(initialWave, "pairs") == 40
                && number(initialWave, "firstResponseBytesPerPair") == 5_242_880
                && number(initialWave, "postCompletionFenceMillis") == 500
                && number(initialWave, "strictLaneClearanceMillis") >= 60_001
                && "LAST_COLD_COMPLETION".equals(initialWave.path("strictLaneClearanceBasis").asString())
                && initialWave.path("partOfSteadyV8Scheduler").isBoolean()
                && !initialWave.path("partOfSteadyV8Scheduler").asBoolean(), "INITIAL_WAVE_LANE_REQUIRED");
        flag(nativeDoc, "strictLaneStartedAfterColdDrain", true);
        require(number(nativeDoc, "coldStartToStrictLaneClearanceMillis") >= 60_001
                && number(nativeDoc, "coldStartDepartureToStrictLaneClearanceMillis") >= 60_001,
                "COLD_START_DRAIN_REQUIRED");
        List<JsonNode> samples = array(nativeDoc, "samples");
        require(!samples.isEmpty(), "SAMPLES_EMPTY");
        for (String key : List.of("requests", "durableAttempts", "durableDepartures",
                "durableDepartureCompletions", "durableCompleteTransportDiagnostics"))
            require(number(nativeDoc, key) == samples.size(), "DURABLE_COUNT_MISMATCH_" + key);
        require(number(nativeDoc, "receivedBytes") == samples.stream().mapToLong(s -> number(s, "bodyBytes")).sum(),
                "BYTE_COUNT_MISMATCH");
        List<JsonNode> cold = samples.stream().filter(s -> COLD_START_LANE.equals(s.path("executionLane").asString())).toList();
        List<JsonNode> strict = samples.stream().filter(s -> STEADY_LANE.equals(s.path("executionLane").asString())).toList();
        // The cold lane is intentionally outside the strict V8 scheduler and
        // may itself take longer than five minutes under local transport or
        // persistence stress.  Its explicit lane, cardinality and 5 MiB body
        // evidence identify it; the strict warm-up begins only afterwards.
        require(cold.size() == 40 && number(nativeDoc, "coldStartSamples") == 40
                && cold.stream().allMatch(s -> number(s, "bodyBytes") == 5_242_880), "INITIAL_WAVE_MISMATCH");
        require(samples.stream().filter(s -> number(s, "bodyBytes") == 5_242_880)
                .allMatch(s -> COLD_START_LANE.equals(s.path("executionLane").asString())), "INITIAL_WAVE_LANE_LEAK");
        require(!strict.isEmpty() && number(nativeDoc, "strictV8SteadySamples") == strict.size()
                && strict.stream().allMatch(s -> number(s, "bodyBytes") == 65_536), "STEADY_SCOPE_MISMATCH");
        require(strict.stream().filter(s -> number(s, "laneRequestedNanos") >= 300 * SECOND)
                .allMatch(s -> number(s, "bodyBytes") == 65_536), "POST_WARMUP_STEADY_SCOPE_MISMATCH");
    }

    private static void validateQualifiedCandidateEnvelopes(JsonNode nativeDoc) {
        JsonNode candidates = nativeDoc.path("candidateEndpointEnvelopes");
        require(candidates.isObject(), "QUALIFIED_CANDIDATE_ENVELOPES_REQUIRED");
        for (SofascoreEndpointType endpoint : FAMILIES) {
            JsonNode candidate = candidates.path(endpoint.name());
            require(candidate.isObject(), "QUALIFIED_CANDIDATE_ENVELOPE_REQUIRED_" + endpoint);
            QualifiedEnvelope qualified = qualifiedEnvelope(endpoint);
            require(number(candidate, "requestMillis") == qualified.requestMillis()
                    && number(candidate, "processingMillis") == qualified.processingMillis(),
                    "QUALIFIED_CANDIDATE_ENVELOPE_MISMATCH_" + endpoint);
        }
    }

    private static List<JsonNode> array(JsonNode node, String key) {
        require(node.path(key).isArray(), "ARRAY_REQUIRED_" + key);
        List<JsonNode> result = new ArrayList<>();
        node.path(key).forEach(result::add);
        return result;
    }

    private static QualifiedEnvelope qualifiedEnvelope(SofascoreEndpointType endpoint) {
        QualifiedEnvelope envelope = QUALIFIED_V8_ENVELOPES.get(endpoint);
        require(envelope != null, "QUALIFIED_CANDIDATE_ENVELOPE_REQUIRED_" + endpoint);
        return envelope;
    }

    private static long qualifiedStrictGroupReservationMillis() {
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (SofascoreEndpointType endpoint : FAMILIES)
            envelopes.put(endpoint, qualifiedEnvelope(endpoint).toEndpointEnvelope());
        var profile = new GroupedAdmissionProfile(envelopes, "0".repeat(64), "live-v8");
        return LiveSchedule.v8StrictGroupReservation(profile).toMillis();
    }
    private static void checkStrictNormalPathDepartureIntervals(List<JsonNode> pair) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < pair.size(); i++)
            intervals.add(number(pair.get(i), "requestedNanos") - number(pair.get(i - 1), "requestedNanos"));
        require(intervals.stream().allMatch(v -> v > 0), "NON_POSITIVE_INTERVAL");
        require(intervals.stream().allMatch(v -> v <= 60 * SECOND),
                "STRICT_NORMAL_PATH_DEPARTURE_CADENCE_FAILED");
    }
    private static long roundUp(long nanos) {
        require(nanos > 0, "POSITIVE_MEASURED_COST_REQUIRED");
        return Math.multiplyExact(Math.floorDiv(Math.addExact(nanos, ROUNDING - 1), ROUNDING), 50);
    }
    private static long maximum(List<JsonNode> samples, String key) {
        return samples.stream().mapToLong(sample -> number(sample, key)).max().orElseThrow();
    }
    private static Map<String, Object> summary(List<JsonNode> samples, String key) {
        List<Long> values = samples.stream().map(sample -> number(sample, key)).toList();
        return Map.of("count", values.size(), "p95", seconds(percentile(values, .95)), "maximum", seconds(percentile(values, 1)));
    }
    private static long percentile(List<Long> values, double p) {
        require(!values.isEmpty(), "MEASURED_VALUES_EMPTY");
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * p) - 1);
    }
    private static int rollingPeak(List<JsonNode> samples, long window, ToLongFunction<JsonNode> time) {
        int start = 0, maximum = 0;
        for (int end = 0; end < samples.size(); end++) {
            long lower = time.applyAsLong(samples.get(end)) - window;
            while (start <= end && time.applyAsLong(samples.get(start)) <= lower) start++;
            maximum = Math.max(maximum, end - start + 1);
        }
        return maximum;
    }
    private static long number(JsonNode node, String key) {
        JsonNode value = node.path(key);
        require(value.isIntegralNumber(), "INTEGER_REQUIRED_" + key);
        return value.longValue();
    }
    private static BigDecimal decimal(JsonNode value) {
        require(value.isNumber(), "NUMBER_REQUIRED");
        return value.decimalValue();
    }
    private static BigDecimal seconds(long nanos) {
        return BigDecimal.valueOf(nanos).movePointLeft(9);
    }
    private static void flag(JsonNode node, String key, boolean expected) {
        JsonNode value = node.path(key);
        require(value.isBoolean() && value.asBoolean() == expected, "FLAG_MISMATCH_" + key);
    }
    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalStateException(code);
    }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
