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
    private static final String PROFILE_NAME = "WO058-GROUPED-LIVE-V8-PROFILE-20260910.json";
    private static final String NATIVE_NAME = "WO058-GROUPED-LIVE-V8-NATIVE-20260910.json";

    private V8MeasuredProfile() { }

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
        List<JsonNode> steady = samples.stream().filter(s -> number(s, "requestedNanos") >= 300 * SECOND).toList();
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        var declaredEnvelopes = new LinkedHashMap<String, Object>();
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
            var envelope = new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis));
            envelopes.put(endpoint, envelope);
            declaredEnvelopes.put(endpoint.name(), Map.of(
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
                    && number(s, "requestedNanos") < 300 * SECOND).toList();
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
        long weightedMillis = 4 * profile.minimumRequestStartInterval().toMillis();
        for (SofascoreEndpointType family : FAMILIES)
            weightedMillis = Math.addExact(weightedMillis, profile.envelope(family).exchangeEnvelope().toMillis());
        require(weightedMillis * MATCHES <= 54_000, "V8_NINETY_PERCENT_TEMPORAL_BOUND_FAILED");

        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("schema", "wo058-grouped-live-capacity-evidence-v2");
        evidence.put("status", replayPassed ? "QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE" : "MEASURED_PROFILE_NOT_ADMISSIBLE");
        evidence.put("labStatus", List.of("EXPERIMENTAL", "LOCAL_ONLY", "NOT_PRODUCTION_APPROVED", "NO_CRITICAL_DEPENDENCY"));
        evidence.put("completedAt", nativeDoc.path("finishedAt").asString());
        evidence.put("policyVersion", "live-v8");
        evidence.put("flywayVersion", 50);
        evidence.put("qualifiedCapacity", capacity);
        evidence.put("envelopeScope", "STEADY_64_KIB_ONLY");
        evidence.put("nativeEvidence", Map.of("path", NATIVE_NAME, "sha256", nativeHash));
        evidence.put("endpointEnvelopes", declaredEnvelopes);
        evidence.put("initialWave", Map.of("separatelyMeasured", true, "coveredBySteadyEnvelopes", false,
                "firstResponseBytesPerPair", 5_242_880, "pairs", 40));
        var cadence = new LinkedHashMap<String, Object>();
        cadence.put("criticalSeconds", 60);
        cadence.put("lineupSeconds", 60);
        cadence.put("intraGroupDelayMillis", 0);
        cadence.put("interGroupDelayMillis", 500);
        cadence.put("maximumEndpointsPerGroup", 4);
        cadence.put("maximumUtilization", new BigDecimal("0.9"));
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
        admission.put("rounding", "each separate observed steady maximum, rounded upward to the next 50 ms boundary; no historical profile floor");
        admission.put("weightedMinuteMillisPerMatch", weightedMillis);
        admission.put("weightedMinuteMillisAtQualifiedCapacity", weightedMillis * capacity);
        admission.put("availableMinuteMillis", 54_000);
        admission.put("uncappedMeanBoundCapacity", 54_000 / weightedMillis);
        admission.put("hourlyCallsPerMatch", HOURLY_CALLS_PER_EVENT);
        admission.put("hourlyHeadroomCallsAtQualifiedCapacity", capacity * HOURLY_CALLS_PER_EVENT);
        admission.put("calculation", "min(10, floor((2756*9/10)/248), floor(54000/(2000+J4+incidents+statistics+lineups))) followed by production v8 scheduler replay; all endpoint costs are request plus processing milliseconds");
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
                && decimal(nativeDoc.path("elapsedSeconds")).compareTo(BigDecimal.valueOf(2100)) >= 0, "RUN_TOO_SHORT");
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
        require(number(nativeDoc, "effectiveRequestTimeoutMillis") == 30_000, "OBSERVED_TIMEOUT_MISMATCH");
        List<JsonNode> samples = array(nativeDoc, "samples");
        require(!samples.isEmpty(), "SAMPLES_EMPTY");
        for (String key : List.of("requests", "durableAttempts", "durableDepartures",
                "durableDepartureCompletions", "durableCompleteTransportDiagnostics"))
            require(number(nativeDoc, key) == samples.size(), "DURABLE_COUNT_MISMATCH_" + key);
        require(number(nativeDoc, "receivedBytes") == samples.stream().mapToLong(s -> number(s, "bodyBytes")).sum(),
                "BYTE_COUNT_MISMATCH");
        require(samples.stream().filter(s -> number(s, "bodyBytes") == 5_242_880).count() == 40,
                "INITIAL_WAVE_MISMATCH");
        require(samples.stream().filter(s -> number(s, "bodyBytes") == 5_242_880)
                .allMatch(s -> number(s, "requestedNanos") < 300 * SECOND), "INITIAL_WAVE_OUTSIDE_WARMUP");
        require(samples.stream().filter(s -> number(s, "requestedNanos") >= 300 * SECOND)
                .allMatch(s -> number(s, "bodyBytes") == 65_536), "STEADY_SCOPE_MISMATCH");
    }

    private static List<JsonNode> array(JsonNode node, String key) {
        require(node.path(key).isArray(), "ARRAY_REQUIRED_" + key);
        List<JsonNode> result = new ArrayList<>();
        node.path(key).forEach(result::add);
        return result;
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
