package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ToLongFunction;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Exact committed v6 evidence plus fake-clock replay; no browser, database or network. */
class GroupedLiveAdmissionPolicyV6EvidenceTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long MILLIS = 1_000_000L;
    private static final int MEASURED_MATCHES = 7;
    private static final List<SofascoreEndpointType> FAMILIES =
            List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);

    @Test
    void measuredV6SamplesIndependentlyBindCapacityAndDurableDepartureProtection() throws Exception {
        Path profilePath = Path.of("docs/validation/WO058-GROUPED-LIVE-V6-PROFILE-20260909.json")
                .toAbsolutePath().normalize();
        assertThat(profilePath).as("a v6 profile must exist; no v5 evidence or test envelope is substituted")
                .isRegularFile();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        var mapper = JsonMapper.builder().build();
        JsonNode document = mapper.readTree(profileBytes);
        assertThat(document.path("schema").asString()).isEqualTo("wo058-grouped-live-capacity-evidence-v2");
        assertThat(document.path("status").asString()).isEqualTo("QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE");
        assertThat(document.path("policyVersion").asString()).isEqualTo("live-v6");
        assertThat(integer(document.path("flywayVersion"))).isEqualTo(44);
        int declaredCapacity = Math.toIntExact(integer(document.path("qualifiedCapacity")));
        assertThat(declaredCapacity).isBetween(1, MEASURED_MATCHES);
        assertThat(document.path("envelopeScope").asString()).isEqualTo("STEADY_64_KIB_ONLY");
        assertBoolean(document.path("initialWave").path("separatelyMeasured"), true);
        assertBoolean(document.path("initialWave").path("coveredBySteadyEnvelopes"), false);
        assertThat(document.has("preservedCostFloorEvidence"))
                .as("this v6 qualification does not inherit a v5 evidence floor").isFalse();

        JsonNode cadence = document.path("cadence");
        assertThat(integer(cadence.path("criticalSeconds"))).isEqualTo(100);
        assertThat(integer(cadence.path("lineupSeconds"))).isEqualTo(300);
        assertThat(integer(cadence.path("intraGroupDelayMillis"))).isZero();
        assertThat(integer(cadence.path("interGroupDelayMillis"))).isEqualTo(1000);
        assertThat(integer(cadence.path("maximumEndpointsPerGroup"))).isEqualTo(4);
        assertThat(decimal(cadence.path("maximumUtilization"))).isEqualByComparingTo("0.9");
        assertThat(integer(cadence.path("minimumPostCompletionDelayMillis"))).isEqualTo(2000);
        assertThat(integer(cadence.path("maximumDeparturesPer60Seconds"))).isEqualTo(25);
        assertThat(integer(cadence.path("maximumDeparturesPerHour"))).isEqualTo(1000);

        JsonNode reference = document.path("nativeEvidence");
        String relativePath = reference.path("path").asString();
        assertThat(relativePath).isEqualTo("WO058-GROUPED-LIVE-V6-NATIVE-20260909.json");
        Path nativePath = profilePath.getParent().resolve(relativePath).normalize();
        assertThat(nativePath.getParent()).isEqualTo(profilePath.getParent());
        assertThat(nativePath).as("the exact sustained native v6 evidence is mandatory").isRegularFile();
        byte[] nativeBytes = Files.readAllBytes(nativePath);
        assertThat(reference.path("sha256").asString()).matches("[0-9a-f]{64}");
        assertThat(Sha256.hex(nativeBytes)).isEqualTo(reference.path("sha256").asString());
        JsonNode nativeEvidence = mapper.readTree(nativeBytes);
        assertThat(nativeEvidence.path("status").asString()).isEqualTo("PASSED");
        assertThat(nativeEvidence.path("policyVersion").asString()).isEqualTo("live-v6");
        assertThat(nativeEvidence.path("scope").asString()).isEqualTo("EXPERIMENTAL LOCAL_ONLY SYNTHETIC_LOOPBACK");
        assertThat(integer(nativeEvidence.path("matches"))).isEqualTo(MEASURED_MATCHES);
        assertThat(integer(nativeEvidence.path("criticalIntervalSeconds"))).isEqualTo(100);
        assertThat(integer(nativeEvidence.path("lineupsIntervalSeconds"))).isEqualTo(300);
        assertThat(integer(nativeEvidence.path("interGroupDelaySeconds"))).isEqualTo(1);
        assertThat(integer(nativeEvidence.path("minimumPostCompletionDelaySeconds"))).isEqualTo(2);
        assertThat(integer(nativeEvidence.path("maximumDeparturesPer60Seconds"))).isEqualTo(25);
        assertThat(integer(nativeEvidence.path("maximumDeparturesPerHour"))).isEqualTo(1000);
        assertThat(integer(nativeEvidence.path("effectiveRequestTimeoutMillis")))
                .as("the v6 run uses the launcher's 30-second timeout; it does not establish slow-provider latency")
                .isEqualTo(30_000);
        assertThat(integer(nativeEvidence.path("warmupSeconds"))).isEqualTo(300);
        assertThat(integer(nativeEvidence.path("requiredSteadySeconds"))).isEqualTo(1800);
        assertThat(decimal(nativeEvidence.path("steadyElapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(1800));
        assertThat(decimal(nativeEvidence.path("elapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(2100));
        for (String flag : List.of("sustainedQualification", "productionDockerDfProbePerRequest",
                "productionPersistentResilience", "productionTransportDiagnosticPersistence", "workerAndChildrenClosed"))
            assertBoolean(nativeEvidence.path(flag), true);
        assertBoolean(nativeEvidence.path("operatorDatabaseUsed"), false);
        for (String count : List.of("realProviderCalls", "offScopeRequests", "missedCycles"))
            assertThat(integer(nativeEvidence.path(count))).as(count).isZero();
        assertThat(integer(nativeEvidence.path("bodyBytesPerResponse"))).isEqualTo(65_536);
        assertThat(integer(nativeEvidence.path("initialFirstResponsePerFamilyBytes"))).isEqualTo(5_242_880);
        assertThat(integer(nativeEvidence.path("immutableObservations"))).isPositive();
        assertThat(integer(nativeEvidence.path("familyScheduleRevisions"))).isPositive();
        assertThat(integer(nativeEvidence.path("pressureDeferrals"))).isPositive();
        JsonNode lineup = nativeEvidence.path("lineupV3Fixture");
        assertThat(integer(lineup.path("captains"))).isEqualTo(2);
        assertThat(integer(lineup.path("playersWithStatistics"))).isEqualTo(44);
        assertThat(integer(lineup.path("statisticsPerPlayer"))).isEqualTo(20);
        assertThat(integer(lineup.path("ratingVersionsPerPlayer"))).isEqualTo(2);
        assertThat(integer(lineup.path("missingPlayers"))).isEqualTo(4);

        assertThat(nativeEvidence.path("samples").isArray()).isTrue();
        List<Sample> samples = new ArrayList<>();
        for (JsonNode value : nativeEvidence.path("samples")) samples.add(sample(value));
        assertThat(samples).isNotEmpty();
        for (String count : List.of("requests", "durableAttempts", "durableDepartures",
                "durableDepartureCompletions", "durableCompleteTransportDiagnostics"))
            assertThat(integer(nativeEvidence.path(count))).as(count).isEqualTo(samples.size());
        assertThat(integer(nativeEvidence.path("receivedBytes")))
                .isEqualTo(samples.stream().mapToLong(Sample::bodyBytes).sum());
        assertThat(samples).allSatisfy(sample -> {
            assertThat(sample.eventId()).isBetween(17_000_001L, 17_000_000L + MEASURED_MATCHES);
            assertThat(FAMILIES).contains(sample.endpoint());
            assertThat(sample.groupId()).isNotBlank();
            assertThat(sample.round()).isNotNegative();
            assertThat(sample.requestNanos()).isPositive();
            assertThat(sample.processingNanos()).isPositive();
            assertThat(sample.resilienceSqlNanos()).isPositive();
            assertThat(sample.authorizationDelayNanos()).isNotNegative();
            assertThat(sample.pressureElapsedNanos()).isNotNegative();
            assertThat(sample.limiterSleepNanos()).isBetween(0L, sample.authorizationDelayNanos());
            assertThat(sample.httpNanos()).isNotNegative();
            assertThat(sample.receivedNanos()).isGreaterThanOrEqualTo(sample.requestedNanos());
            assertThat(sample.resilienceFinishedNanos()).isGreaterThanOrEqualTo(sample.receivedNanos());
            // Worker wall-clock timestamps are quantized to milliseconds.
            assertThat(sample.requestNanos() + MILLIS).isGreaterThanOrEqualTo(sample.httpNanos());
            assertThat(sample.committedNanos() + MILLIS).isGreaterThanOrEqualTo(sample.resilienceFinishedNanos());
        });

        List<Sample> steady = samples.stream().filter(sample -> sample.receivedNanos() >= 300 * SECOND).toList();
        assertThat(steady).isNotEmpty().allSatisfy(sample -> assertThat(sample.bodyBytes()).isEqualTo(65_536));
        assertThat(samples.stream().filter(sample -> sample.bodyBytes() == 5_242_880).toList())
                .hasSize(4 * MEASURED_MATCHES)
                .allSatisfy(sample -> assertThat(sample.receivedNanos()).isLessThan(300 * SECOND));
        for (long eventId = 17_000_001; eventId <= 17_000_000 + MEASURED_MATCHES; eventId++) {
            long id = eventId;
            for (SofascoreEndpointType endpoint : FAMILIES) {
                var pair = samples.stream().filter(sample -> sample.eventId() == id && sample.endpoint() == endpoint).toList();
                assertThat(pair).isNotEmpty();
                assertThat(pair.getFirst().bodyBytes()).isEqualTo(5_242_880);
                assertThat(pair.subList(1, pair.size())).allSatisfy(sample -> assertThat(sample.bodyBytes()).isEqualTo(65_536));
            }
        }

        JsonNode declared = document.path("endpointEnvelopes");
        JsonNode metrics = nativeEvidence.path("steadyMetrics");
        assertThat(declared.isObject()).isTrue();
        assertThat(declared.size()).isEqualTo(4);
        assertThat(metrics.size()).isEqualTo(4);
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (SofascoreEndpointType endpoint : FAMILIES) {
            var family = steady.stream().filter(sample -> sample.endpoint() == endpoint).toList();
            assertThat(family).isNotEmpty();
            long requestMillis = integer(declared.path(endpoint.name()).path("requestMillis"));
            long processingMillis = integer(declared.path(endpoint.name()).path("processingMillis"));
            assertThat(requestMillis).isPositive();
            assertThat(processingMillis).isPositive();
            long maximumRequest = family.stream().mapToLong(Sample::requestNanos).max().orElseThrow();
            long maximumProcessing = family.stream().mapToLong(Sample::processingNanos).max().orElseThrow();
            assertThat(Math.multiplyExact(requestMillis, MILLIS)).as("%s measured request including callbacks/completion SQL", endpoint)
                    .isGreaterThanOrEqualTo(maximumRequest);
            assertThat(Math.multiplyExact(processingMillis, MILLIS)).as("%s measured processing including authorization SQL", endpoint)
                    .isGreaterThanOrEqualTo(maximumProcessing);
            assertReportedMaximum(metrics.path(endpoint.name()).path("requestSeconds"), family.size(), maximumRequest);
            assertReportedMaximum(metrics.path(endpoint.name()).path("processingIncludingSqlSeconds"), family.size(), maximumProcessing);
            assertReportedMaximum(metrics.path(endpoint.name()).path("resilienceSqlSeconds"), family.size(),
                    family.stream().mapToLong(Sample::resilienceSqlNanos).max().orElseThrow());
            assertReportedMaximum(metrics.path(endpoint.name()).path("authorizationDelaySeconds"), family.size(),
                    family.stream().mapToLong(Sample::authorizationDelayNanos).max().orElseThrow());
            assertReportedMaximum(metrics.path(endpoint.name()).path("schedulerDeferralElapsedSeconds"), family.size(),
                    family.stream().mapToLong(Sample::pressureElapsedNanos).max().orElseThrow());
            assertReportedMaximum(metrics.path(endpoint.name()).path("limiterSleepSeconds"), family.size(),
                    family.stream().mapToLong(Sample::limiterSleepNanos).max().orElseThrow());
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis)));
            for (long eventId = 17_000_001; eventId <= 17_000_000 + MEASURED_MATCHES; eventId++) {
                long id = eventId;
                var pair = family.stream().filter(sample -> sample.eventId() == id).toList();
                assertThat(pair).as("steady samples for %s/%s", id, endpoint)
                        .hasSizeGreaterThanOrEqualTo(endpoint == EVENT_LINEUPS ? 5 : 17);
                var receiptIntervals = new ArrayList<Long>();
                var publicationIntervals = new ArrayList<Long>();
                for (int index = 1; index < pair.size(); index++) {
                    Sample previous = pair.get(index - 1), current = pair.get(index);
                    receiptIntervals.add(current.receivedNanos() - previous.receivedNanos());
                    publicationIntervals.add(current.committedNanos() - previous.committedNanos());
                    if (endpoint == EVENT_LINEUPS)
                        assertThat(current.dueNanos() - previous.dueNanos()).isGreaterThanOrEqualTo(300 * SECOND);
                }
                // Shared holds can rephase v6; v5 absolute phase congruences are not inherited.
                assertIntervals(receiptIntervals, endpoint, "receipt");
                assertIntervals(publicationIntervals, endpoint, "publication");
                if (endpoint == EVENT_LINEUPS)
                    assertThat(percentile(pair.stream().map(sample -> Math.max(0, sample.receivedNanos() - sample.dueNanos())).toList(), .95))
                            .isLessThanOrEqualTo(15 * SECOND);
            }
        }

        long minimumPostCompletion = Long.MAX_VALUE;
        for (int index = 1; index < samples.size(); index++) {
            Sample previous = samples.get(index - 1), current = samples.get(index);
            assertThat(current.requestedNanos()).isGreaterThan(previous.requestedNanos());
            assertThat(current.serverArrivalNanos()).isGreaterThan(previous.serverArrivalNanos());
            long gap = current.requestedNanos() - previous.resilienceFinishedNanos();
            minimumPostCompletion = Math.min(minimumPostCompletion, gap);
            assertThat(gap).as("post-completion fence, including initialization; at most 1 ms quantization")
                    .isGreaterThanOrEqualTo(2 * SECOND - MILLIS);
            if (!previous.groupId().equals(current.groupId()))
                assertThat(current.serverArrivalNanos() - previous.responseCompleteNanos())
                        .isGreaterThanOrEqualTo(SECOND - MILLIS);
        }
        assertSeconds(nativeEvidence.path("observedMinimumPostCompletionDelaySeconds"), minimumPostCompletion);
        int minutePeak = rollingPeak(samples, 60 * SECOND, Sample::requestedNanos);
        int hourPeak = rollingPeak(samples, 3600 * SECOND, Sample::requestedNanos);
        int wireMinutePeak = rollingPeak(samples, 60 * SECOND, Sample::serverArrivalNanos);
        int wireHourPeak = rollingPeak(samples, 3600 * SECOND, Sample::serverArrivalNanos);
        assertThat(minutePeak).isLessThanOrEqualTo(25);
        assertThat(hourPeak).isLessThanOrEqualTo(1000);
        assertThat(wireMinutePeak).isLessThanOrEqualTo(25);
        assertThat(wireHourPeak).isLessThanOrEqualTo(1000);
        assertThat(integer(nativeEvidence.path("observedMaximumDeparturesPer60Seconds"))).isEqualTo(minutePeak);
        assertThat(integer(nativeEvidence.path("observedMaximumDeparturesPerHour"))).isEqualTo(hourPeak);
        assertThat(integer(nativeEvidence.path("observedMaximumWireArrivalsPer60Seconds"))).isEqualTo(wireMinutePeak);
        assertThat(integer(nativeEvidence.path("observedMaximumWireArrivalsPerHour"))).isEqualTo(wireHourPeak);
        List<Long> debt = steady.stream().filter(sample -> sample.endpoint() != EVENT_LINEUPS)
                .map(sample -> Math.max(0, sample.committedNanos() - sample.dueNanos())).toList();
        int quarter = debt.size() / 4;
        assertThat(quarter).isPositive();
        assertThat(percentile(debt, .95)).isLessThanOrEqualTo(15 * SECOND);
        assertThat(percentile(debt.subList(debt.size() - quarter, debt.size()), .95)
                - percentile(debt.subList(0, quarter), .95)).isLessThanOrEqualTo(5 * SECOND);

        // Exact v6 measured envelopes drive all 24 production-scheduler scenarios.
        var profile = new GroupedAdmissionProfile(envelopes, Sha256.hex(profileBytes), "live-v6");
        assertThat(integer(document.path("admission").path("productionSchedulerScenarioCount"))).isEqualTo(24);
        assertBoolean(document.path("admission").path("productionSchedulerScenariosExecutedForThisProfile"), true);
        assertBoolean(document.path("admission").path("profileAppliedAutomatically"), false);
        int calculatedCapacity = LiveAdmissionPolicy.qualifiedCapacityV6(profile);
        assertThat(calculatedCapacity).isBetween(1, MEASURED_MATCHES).isEqualTo(declaredCapacity);
        assertThat(integer(document.path("admission").path("calculatedCapacity"))).isEqualTo(calculatedCapacity);
        assertThat(GroupedLiveAdmissionSimulation.fits(calculatedCapacity, profile)).isTrue();
        assertThat(calculatedCapacity * 128).as("initial/final headroom inside 90%% of the local hourly ceiling")
                .isLessThanOrEqualTo(900);
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(100);
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatCode(() -> policy.admitV6(calculatedCapacity, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admitV6(calculatedCapacity + 1, profile))
                .hasMessage(calculatedCapacity < MEASURED_MATCHES
                        ? "LIVE_CAPACITY_REFUSED_REDUCE_SELECTION"
                        : "LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }

    private static Sample sample(JsonNode value) {
        return new Sample(integer(value.path("providerEventId")), SofascoreEndpointType.valueOf(value.path("endpoint").asString()),
                integer(value.path("round")), value.path("groupId").asString(), integer(value.path("dueNanos")),
                integer(value.path("receivedNanos")), integer(value.path("committedNanos")), integer(value.path("requestNanos")),
                integer(value.path("processingNanos")), integer(value.path("serverArrivalNanos")), integer(value.path("responseCompleteNanos")),
                integer(value.path("bodyBytes")), integer(value.path("requestedNanos")), integer(value.path("httpNanos")),
                integer(value.path("authorizationDelayNanos")), integer(value.path("pressureElapsedNanos")),
                integer(value.path("limiterSleepNanos")), integer(value.path("resilienceSqlNanos")), integer(value.path("resilienceFinishedNanos")));
    }

    private static int rollingPeak(List<Sample> samples, long windowNanos, ToLongFunction<Sample> timestamp) {
        int start = 0, maximum = 0;
        for (int end = 0; end < samples.size(); end++) {
            long lower = timestamp.applyAsLong(samples.get(end)) - windowNanos;
            while (start <= end && timestamp.applyAsLong(samples.get(start)) <= lower) start++;
            maximum = Math.max(maximum, end - start + 1);
        }
        return maximum;
    }

    private static void assertReportedMaximum(JsonNode reported, int count, long maximumNanos) {
        assertThat(integer(reported.path("count"))).isEqualTo(count);
        assertSeconds(reported.path("maximum"), maximumNanos);
    }

    private static void assertSeconds(JsonNode reported, long nanos) {
        assertThat(decimal(reported).subtract(BigDecimal.valueOf(nanos).movePointLeft(9)).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.000000001"));
    }

    private static void assertIntervals(List<Long> intervals, SofascoreEndpointType endpoint, String stage) {
        assertThat(intervals).as("%s %s intervals", endpoint, stage).isNotEmpty()
                .allSatisfy(interval -> assertThat(interval).isPositive());
        assertThat(percentile(intervals, .95)).isLessThanOrEqualTo((endpoint == EVENT_LINEUPS ? 315 : 105) * SECOND);
        assertThat(percentile(intervals, 1)).isLessThanOrEqualTo((endpoint == EVENT_LINEUPS ? 330 : 115) * SECOND);
    }

    private static long percentile(List<Long> values, double fraction) {
        assertThat(values).isNotEmpty();
        var sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * fraction) - 1);
    }

    private static void assertBoolean(JsonNode value, boolean expected) {
        assertThat(value.isBoolean()).as("boolean evidence value is recorded").isTrue();
        assertThat(value.asBoolean()).isEqualTo(expected);
    }

    private static long integer(JsonNode value) {
        assertThat(value.isIntegralNumber()).as("integer evidence value is recorded").isTrue();
        return value.asLong();
    }

    private static BigDecimal decimal(JsonNode value) {
        assertThat(value.isNumber()).as("numeric evidence value is recorded").isTrue();
        return value.decimalValue();
    }

    private record Sample(long eventId, SofascoreEndpointType endpoint, long round, String groupId,
                          long dueNanos, long receivedNanos, long committedNanos, long requestNanos,
                          long processingNanos, long serverArrivalNanos, long responseCompleteNanos, long bodyBytes,
                          long requestedNanos, long httpNanos, long authorizationDelayNanos, long pressureElapsedNanos,
                          long limiterSleepNanos, long resilienceSqlNanos, long resilienceFinishedNanos) { }
}
