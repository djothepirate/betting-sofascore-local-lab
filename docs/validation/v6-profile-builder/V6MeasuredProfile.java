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
import java.util.*;
import java.util.function.ToLongFunction;

/** Disposable offline profile builder. Does not start Spring, a store, a browser or a network client. */
public final class V6MeasuredProfile {
    private static final long SECOND = 1_000_000_000L, MILLIS = 1_000_000L, ROUNDING = 50 * MILLIS;
    private static final List<SofascoreEndpointType> FAMILIES = List.of(SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS);
    private static final String PROFILE_NAME = "WO058-GROUPED-LIVE-V6-PROFILE-20260909.json";
    private static final String NATIVE_NAME = "WO058-GROUPED-LIVE-V6-NATIVE-20260909.json";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("expected native-input and ignored-output-directory");
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path expectedInput = repository.resolve(".tmp/wo058-v6-sustained-qualification.json");
        require(input.equals(expectedInput) && Files.isRegularFile(input), "SUSTAINED_NATIVE_INPUT_REQUIRED");
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path allowed = repository.resolve(".tmp/resilience-verification").toRealPath();
        require(output.getParent() != null && Files.isDirectory(output.getParent())
                && output.getParent().toRealPath().equals(allowed) && !Files.exists(output), "FRESH_IGNORED_OUTPUT_REQUIRED");
        byte[] nativeBytes = Files.readAllBytes(input);
        String nativeHash = sha256(nativeBytes);
        var mapper = JsonMapper.builder().build();
        JsonNode nativeDoc = mapper.readTree(nativeBytes);
        require("PASSED".equals(nativeDoc.path("status").asString()), "NATIVE_NOT_PASSED");
        require("live-v6".equals(nativeDoc.path("policyVersion").asString()), "V6_REQUIRED");
        require("EXPERIMENTAL LOCAL_ONLY SYNTHETIC_LOOPBACK".equals(nativeDoc.path("scope").asString()), "SCOPE_MISMATCH");
        for (String key : List.of("sustainedQualification", "productionPersistentResilience",
                "productionTransportDiagnosticPersistence", "productionDockerDfProbePerRequest", "workerAndChildrenClosed"))
            flag(nativeDoc, key, true);
        flag(nativeDoc, "operatorDatabaseUsed", false);
        for (String key : List.of("realProviderCalls", "offScopeRequests", "missedCycles")) require(number(nativeDoc, key) == 0, key);
        require(number(nativeDoc, "matches") == 7 && number(nativeDoc, "warmupSeconds") == 300
                && number(nativeDoc, "requiredSteadySeconds") == 1800, "MEASURED_RUN_SHAPE_MISMATCH");
        require(decimal(nativeDoc.path("steadyElapsedSeconds")).compareTo(BigDecimal.valueOf(1800)) >= 0
                && decimal(nativeDoc.path("elapsedSeconds")).compareTo(BigDecimal.valueOf(2100)) >= 0, "RUN_TOO_SHORT");
        require(number(nativeDoc, "criticalIntervalSeconds") == 100 && number(nativeDoc, "lineupsIntervalSeconds") == 300
                && number(nativeDoc, "interGroupDelaySeconds") == 1 && number(nativeDoc, "minimumPostCompletionDelaySeconds") == 2
                && number(nativeDoc, "maximumDeparturesPer60Seconds") == 25 && number(nativeDoc, "maximumDeparturesPerHour") == 1000,
                "POLICY_LIMIT_MISMATCH");
        require(number(nativeDoc, "effectiveRequestTimeoutMillis") == 30000, "OBSERVED_TIMEOUT_MISMATCH");
        require(nativeDoc.path("samples").isArray(), "SAMPLES_REQUIRED");
        List<JsonNode> samples = new ArrayList<>();
        nativeDoc.path("samples").forEach(samples::add);
        require(!samples.isEmpty(), "SAMPLES_EMPTY");
        for (String key : List.of("requests", "durableAttempts", "durableDepartures", "durableDepartureCompletions", "durableCompleteTransportDiagnostics"))
            require(number(nativeDoc, key) == samples.size(), "DURABLE_COUNT_MISMATCH_" + key);
        require(number(nativeDoc, "receivedBytes") == samples.stream().mapToLong(s -> number(s, "bodyBytes")).sum(), "BYTE_COUNT_MISMATCH");
        List<JsonNode> steady = samples.stream().filter(s -> number(s, "receivedNanos") >= 300 * SECOND).toList();
        require(!steady.isEmpty() && steady.stream().allMatch(s -> number(s, "bodyBytes") == 65536), "STEADY_SCOPE_MISMATCH");
        require(samples.stream().filter(s -> number(s, "bodyBytes") == 5242880).count() == 28, "INITIAL_WAVE_MISMATCH");
        require(samples.stream().filter(s -> number(s, "bodyBytes") == 5242880).allMatch(s -> number(s, "receivedNanos") < 300 * SECOND),
                "INITIAL_WAVE_OUTSIDE_WARMUP");

        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        var declaredEnvelopes = new LinkedHashMap<String, Object>();
        var measuredFamilies = new LinkedHashMap<String, Object>();
        for (SofascoreEndpointType endpoint : FAMILIES) {
            List<JsonNode> family = steady.stream().filter(s -> endpoint.name().equals(s.path("endpoint").asString())).toList();
            require(!family.isEmpty(), "FAMILY_MISSING");
            long requestMaximum = maximum(family, "requestNanos"), processingMaximum = maximum(family, "processingNanos");
            long requestMillis = roundUp(requestMaximum), processingMillis = roundUp(processingMaximum);
            require(requestMillis <= 10000 && processingMillis <= 60000, "ENVELOPE_EXCEEDS_DOMAIN_BOUND");
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis)));
            declaredEnvelopes.put(endpoint.name(), Map.of("requestMillis", requestMillis, "processingMillis", processingMillis));
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("steadySamples", family.size());
            metric.put("maximumRequestNanos", requestMaximum); metric.put("maximumProcessingNanos", processingMaximum);
            metric.put("requestSeconds", summary(family, "requestNanos"));
            metric.put("processingSeconds", summary(family, "processingNanos"));
            metric.put("httpSeconds", summary(family, "httpNanos"));
            metric.put("authorizationDelaySeconds", summary(family, "authorizationDelayNanos"));
            metric.put("limiterSleepSeconds", summary(family, "limiterSleepNanos"));
            metric.put("resilienceSqlSeconds", summary(family, "resilienceSqlNanos"));
            List<JsonNode> initial = samples.stream().filter(s -> endpoint.name().equals(s.path("endpoint").asString())
                    && number(s, "receivedNanos") < 300 * SECOND).toList();
            metric.put("initialRequestSeconds", summary(initial, "requestNanos"));
            metric.put("initialProcessingSeconds", summary(initial, "processingNanos"));
            measuredFamilies.put(endpoint.name(), metric);
            for (long id = 17_000_001; id <= 17_000_007; id++) {
                long eventId = id;
                var pair = family.stream().filter(s -> number(s, "providerEventId") == eventId).toList();
                require(pair.size() >= (endpoint == SofascoreEndpointType.EVENT_LINEUPS ? 5 : 17), "STEADY_PAIR_TOO_SHORT");
                checkIntervals(pair, endpoint, "receivedNanos");
                checkIntervals(pair, endpoint, "committedNanos");
            }
        }
        long minimumGap = Long.MAX_VALUE;
        for (int i = 1; i < samples.size(); i++) {
            JsonNode previous = samples.get(i - 1), current = samples.get(i);
            long gap = number(current, "requestedNanos") - number(previous, "resilienceFinishedNanos");
            require(gap >= 2 * SECOND - MILLIS, "POST_COMPLETION_FENCE_FAILED");
            require(number(current, "serverArrivalNanos") > number(previous, "serverArrivalNanos"), "WIRE_ORDER_FAILED");
            minimumGap = Math.min(minimumGap, gap);
        }
        int minutePeak = rollingPeak(samples, 60 * SECOND, s -> number(s, "requestedNanos"));
        int hourPeak = rollingPeak(samples, 3600 * SECOND, s -> number(s, "requestedNanos"));
        int wireMinutePeak = rollingPeak(samples, 60 * SECOND, s -> number(s, "serverArrivalNanos"));
        int wireHourPeak = rollingPeak(samples, 3600 * SECOND, s -> number(s, "serverArrivalNanos"));
        require(minutePeak <= 25 && wireMinutePeak <= 25 && hourPeak <= 1000 && wireHourPeak <= 1000, "ROLLING_WINDOW_FAILED");

        // The digest here binds actual native bytes, not a placeholder qualification.
        // These production methods execute the exact scheduler's phase/transition replays.
        var admissionProfile = new GroupedAdmissionProfile(envelopes, nativeHash, "live-v6");
        int capacity = LiveAdmissionPolicy.qualifiedCapacityV6(admissionProfile);
        boolean replayPassed = capacity > 0 && GroupedLiveAdmissionSimulation.fits(capacity, admissionProfile);
        require(capacity >= 0 && capacity <= 7, "CALCULATED_CAPACITY_OUT_OF_SCOPE");
        if (capacity > 0) require(replayPassed, "PRODUCTION_REPLAY_FAILED");
        long weightedMillis = 20000;
        for (SofascoreEndpointType family : FAMILIES)
            weightedMillis = Math.addExact(weightedMillis, Math.multiplyExact(envelopes.get(family).exchangeEnvelope().toMillis(),
                    family == SofascoreEndpointType.EVENT_LINEUPS ? 1 : 3));
        var profile = new LinkedHashMap<String, Object>();
        profile.put("schema", "wo058-grouped-live-capacity-evidence-v2");
        profile.put("status", capacity > 0 ? "QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE" : "MEASURED_PROFILE_NOT_ADMISSIBLE");
        profile.put("labStatus", List.of("EXPERIMENTAL", "LOCAL_ONLY", "NOT_PRODUCTION_APPROVED", "NO_CRITICAL_DEPENDENCY"));
        profile.put("completedAt", nativeDoc.path("finishedAt").asString());
        profile.put("policyVersion", "live-v6"); profile.put("flywayVersion", 44);
        profile.put("qualifiedCapacity", capacity); profile.put("envelopeScope", "STEADY_64_KIB_ONLY");
        profile.put("nativeEvidence", Map.of("path", NATIVE_NAME, "sha256", nativeHash));
        profile.put("endpointEnvelopes", declaredEnvelopes);
        profile.put("initialWave", Map.of("separatelyMeasured", true, "coveredBySteadyEnvelopes", false,
                "firstResponseBytesPerPair", 5242880, "pairs", 28));
        var cadence = new LinkedHashMap<String, Object>();
        cadence.put("criticalSeconds", 100); cadence.put("lineupSeconds", 300); cadence.put("intraGroupDelayMillis", 0);
        cadence.put("interGroupDelayMillis", 1000); cadence.put("maximumEndpointsPerGroup", 4);
        cadence.put("maximumUtilization", new BigDecimal("0.9")); cadence.put("minimumPostCompletionDelayMillis", 2000);
        cadence.put("maximumDeparturesPer60Seconds", 25); cadence.put("maximumDeparturesPerHour", 1000);
        profile.put("cadence", cadence);
        var admission = new LinkedHashMap<String, Object>();
        admission.put("calculatedCapacity", capacity); admission.put("productionSchedulerScenarioCount", 24);
        admission.put("productionSchedulerScenariosExecutedForThisProfile", replayPassed);
        admission.put("profileAppliedAutomatically", false);
        admission.put("rounding", "each separate observed steady maximum, rounded upward to the next 50 ms boundary; no v5 floor");
        admission.put("weightedFiveMinuteMillisPerMatch", weightedMillis);
        admission.put("weightedFiveMinuteMillisAtQualifiedCapacity", weightedMillis * capacity);
        admission.put("availableFiveMinuteMillis", 270000);
        admission.put("uncappedMeanBoundCapacity", 270000 / weightedMillis);
        admission.put("hourlyHeadroomCallsAtQualifiedCapacity", capacity * 128);
        admission.put("calculation", "min(7, floor(900/128), floor(270000/(20000+3*(J4+incidents+statistics)+lineups))) followed by production v6 scheduler replay; all endpoint costs are request plus processing milliseconds");
        profile.put("admission", admission);
        profile.put("budgets", Map.of("maximumCallsPerEvent", 2500, "maximumCallsPerCampaign", 20000,
                "maximumDurationMinutes", 240, "maximumRawBytes", 15728640000L, "reservedFinalCallsPerActiveEvent", 4));
        profile.put("measurement", Map.of("totalCalls", samples.size(), "steadyCalls", steady.size(), "pairsChecked", 28,
                "effectiveRequestTimeoutMillis", number(nativeDoc, "effectiveRequestTimeoutMillis"),
                "minimumPostCompletionDelaySeconds", seconds(minimumGap), "maximumDeparturesPer60Seconds", minutePeak,
                "maximumWireArrivalsPer60Seconds", wireMinutePeak, "endpointMetrics", measuredFamilies));
        var runtime = new LinkedHashMap<String, String>();
        for (Class<?> type : List.of(LiveAdmissionPolicy.class, GroupedLiveAdmissionSimulation.class,
                GroupedLiveScheduleV4.class, LiveSchedule.class, GroupedAdmissionProfile.class)) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var stream = type.getResourceAsStream(resource)) {
                require(stream != null, "RUNTIME_CLASS_EVIDENCE_MISSING");
                runtime.put(type.getName(), sha256(stream.readAllBytes()));
            }
        }
        profile.put("productionClassSha256", runtime);
        profile.put("profileBuilderSourceSha256", sha256(Files.readAllBytes(allowed.resolve("V6MeasuredProfile.java"))));
        profile.put("limitations", List.of("Synthetic local loopback only; does not establish provider acceptance or an IP/quota threshold",
                "Steady 64 KiB response corpus, including LINEUPS V3; initial 5 MiB responses measured separately",
                "The 35-minute run does not saturate a full hourly window; rolling-hour invariants have separate PostgreSQL replay evidence",
                "Nominal cadence remains subordinate to the shared persistent budget and previous campaigns",
                "Operator selection limit is independent and is neither read nor modified by this utility"));
        byte[] profileBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(profile);
        Files.createDirectory(output);
        Files.write(output.resolve("v6-admission-analysis.json"), profileBytes, StandardOpenOption.CREATE_NEW);
        if (capacity > 0) {
            Files.write(output.resolve(NATIVE_NAME), nativeBytes, StandardOpenOption.CREATE_NEW);
            Files.write(output.resolve(PROFILE_NAME), profileBytes, StandardOpenOption.CREATE_NEW);
        }
        System.out.println("V6_MEASURED_CAPACITY=" + capacity);
        System.out.println("V6_PRODUCTION_SCENARIOS=" + (replayPassed ? 24 : 0));
        System.out.println("V6_NATIVE_SHA256=" + nativeHash);
        System.out.println("V6_PROFILE_SHA256=" + sha256(profileBytes));
        System.out.println("V6_OPERATOR_CONFIGURATION_CHANGED=NO");
        if (capacity == 0) System.exit(2);
    }

    private static void checkIntervals(List<JsonNode> pair, SofascoreEndpointType endpoint, String field) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < pair.size(); i++) intervals.add(number(pair.get(i), field) - number(pair.get(i - 1), field));
        require(intervals.stream().allMatch(v -> v > 0), "NON_POSITIVE_INTERVAL");
        long p95 = endpoint == SofascoreEndpointType.EVENT_LINEUPS ? 315 : 105;
        long maximum = endpoint == SofascoreEndpointType.EVENT_LINEUPS ? 330 : 115;
        require(percentile(intervals, .95) <= p95 * SECOND && percentile(intervals, 1) <= maximum * SECOND, "MEASURED_CADENCE_FAILED");
    }
    private static long roundUp(long nanos) {
        require(nanos > 0, "POSITIVE_MEASURED_COST_REQUIRED");
        return Math.multiplyExact(Math.floorDiv(Math.addExact(nanos, ROUNDING - 1), ROUNDING), 50);
    }
    private static long maximum(List<JsonNode> samples, String key) { return samples.stream().mapToLong(s -> number(s, key)).max().orElseThrow(); }
    private static Map<String, Object> summary(List<JsonNode> samples, String key) {
        var values = samples.stream().map(s -> number(s, key)).toList();
        return Map.of("count", values.size(), "p95", seconds(percentile(values, .95)), "maximum", seconds(percentile(values, 1)));
    }
    private static long percentile(List<Long> values, double p) {
        require(!values.isEmpty(), "MEASURED_VALUES_EMPTY");
        var sorted = values.stream().sorted().toList();
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
    private static long number(JsonNode node, String key) { JsonNode value = node.path(key); require(value.isIntegralNumber(), "INTEGER_REQUIRED_" + key); return value.longValue(); }
    private static BigDecimal decimal(JsonNode value) { require(value.isNumber(), "NUMBER_REQUIRED"); return value.decimalValue(); }
    private static BigDecimal seconds(long nanos) { return BigDecimal.valueOf(nanos).movePointLeft(9); }
    private static void flag(JsonNode node, String key, boolean expected) { JsonNode value = node.path(key); require(value.isBoolean() && value.asBoolean() == expected, "FLAG_MISMATCH_" + key); }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
