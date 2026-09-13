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

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Committed evidence and fake-clock replay only: no browser, PostgreSQL or provider calls. */
class GroupedLiveAdmissionPolicyV5EvidenceTest {
    private static final long SECOND = 1_000_000_000L;
    private static final List<SofascoreEndpointType> FAMILIES =
            List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);

    @Test
    void measuredV5SamplesBindTheProfileAndQualifyTwentyMatchesAcrossAllTwentyFourScenarios() throws Exception {
        Path profilePath = Path.of("docs/validation/WO058-GROUPED-LIVE-V5-PROFILE-20260908.json")
                .toAbsolutePath().normalize();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        var mapper = JsonMapper.builder().build();
        JsonNode document = mapper.readTree(profileBytes);
        assertThat(document.path("schema").asString()).isEqualTo("wo058-grouped-live-capacity-evidence-v1");
        assertThat(document.path("status").asString()).isEqualTo("QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE");
        assertThat(document.path("policyVersion").asString()).isEqualTo("live-v5");
        assertThat(integer(document.path("flywayVersion"))).isEqualTo(40);
        assertThat(integer(document.path("qualifiedCapacity"))).isEqualTo(20);
        assertThat(document.path("envelopeScope").asString()).isEqualTo("STEADY_64_KIB_ONLY");
        assertBoolean(document.path("initialWave").path("separatelyMeasured"), true);
        assertBoolean(document.path("initialWave").path("coveredBySteadyEnvelopes"), false);
        assertBoolean(document.path("admission").path("profileAppliedAutomatically"), false);

        JsonNode cadence = document.path("cadence");
        assertThat(integer(cadence.path("criticalSeconds"))).isEqualTo(100);
        assertThat(integer(cadence.path("lineupSeconds"))).isEqualTo(300);
        assertThat(integer(cadence.path("intraGroupDelayMillis"))).isZero();
        assertThat(integer(cadence.path("interGroupDelayMillis"))).isEqualTo(1000);
        assertThat(integer(cadence.path("maximumEndpointsPerGroup"))).isEqualTo(4);
        assertThat(decimal(cadence.path("maximumUtilization"))).isEqualByComparingTo("0.9");

        // Faster observations in the 100-second run must not lower the costs retained
        // from the archived 75-second candidate; preserve each component separately.
        JsonNode floorReference = document.path("preservedCostFloorEvidence");
        assertBoolean(floorReference.path("costsMayOnlyIncrease"), true);
        String floorRelativePath = floorReference.path("path").asString();
        assertThat(floorRelativePath).isEqualTo("WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json");
        Path floorPath = profilePath.getParent().resolve(floorRelativePath).normalize();
        assertThat(floorPath.getParent()).isEqualTo(profilePath.getParent());
        byte[] floorBytes = Files.readAllBytes(floorPath);
        assertThat(floorReference.path("sha256").asString()).matches("[0-9a-f]{64}");
        assertThat(Sha256.hex(floorBytes)).as("exact archived candidate cost-floor evidence bytes")
                .isEqualTo(floorReference.path("sha256").asString());
        JsonNode floorEvidence = mapper.readTree(floorBytes);
        assertThat(floorEvidence.path("schema").asString()).isEqualTo("wo058-grouped-live-capacity-evidence-v1");
        assertThat(floorEvidence.path("policyVersion").asString()).isEqualTo("live-v5");
        assertThat(integer(floorEvidence.path("cadence").path("criticalSeconds"))).isEqualTo(75);
        assertThat(floorEvidence.path("envelopeScope").asString()).isEqualTo("STEADY_64_KIB_ONLY");
        assertBoolean(floorEvidence.path("initialWave").path("separatelyMeasured"), true);
        assertBoolean(floorEvidence.path("initialWave").path("coveredBySteadyEnvelopes"), false);
        JsonNode retainedFloors = floorEvidence.path("endpointEnvelopes");
        assertThat(retainedFloors.isObject()).isTrue();
        assertThat(retainedFloors.size()).isEqualTo(4);

        JsonNode reference = document.path("nativeEvidence");
        String relativePath = reference.path("path").asString();
        assertThat(relativePath).isNotBlank();
        assertThat(Path.of(relativePath).isAbsolute()).isFalse();
        Path nativePath = profilePath.getParent().resolve(relativePath).normalize();
        assertThat(nativePath.getParent()).isEqualTo(profilePath.getParent());
        byte[] nativeBytes = Files.readAllBytes(nativePath);
        assertThat(reference.path("sha256").asString()).matches("[0-9a-f]{64}");
        assertThat(Sha256.hex(nativeBytes)).as("exact native evidence bytes")
                .isEqualTo(reference.path("sha256").asString());
        JsonNode nativeEvidence = mapper.readTree(nativeBytes);
        assertThat(nativeEvidence.path("status").asString()).isEqualTo("PASSED");
        assertThat(nativeEvidence.path("policyVersion").asString()).isEqualTo("live-v5");
        assertThat(integer(nativeEvidence.path("matches"))).isEqualTo(20);
        assertThat(integer(nativeEvidence.path("criticalIntervalSeconds"))).isEqualTo(100);
        assertThat(integer(nativeEvidence.path("lineupsIntervalSeconds"))).isEqualTo(300);
        assertThat(integer(nativeEvidence.path("interGroupDelaySeconds"))).isEqualTo(1);
        assertThat(integer(nativeEvidence.path("warmupSeconds"))).isEqualTo(300);
        assertThat(integer(nativeEvidence.path("requiredSteadySeconds"))).isEqualTo(1800);
        assertThat(decimal(nativeEvidence.path("steadyElapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(1800));
        assertThat(decimal(nativeEvidence.path("elapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(2100));
        for (String flag : List.of("sustainedQualification", "productionDockerDfProbePerRequest", "workerAndChildrenClosed"))
            assertBoolean(nativeEvidence.path(flag), true);
        assertBoolean(nativeEvidence.path("operatorDatabaseUsed"), false);
        for (String count : List.of("realProviderCalls", "offScopeRequests", "missedCycles"))
            assertThat(integer(nativeEvidence.path(count))).as(count).isZero();
        assertThat(integer(nativeEvidence.path("bodyBytesPerResponse"))).isEqualTo(65_536);
        assertThat(integer(nativeEvidence.path("initialFirstResponsePerFamilyBytes"))).isEqualTo(5_242_880);
        assertThat(integer(nativeEvidence.path("immutableObservations"))).isPositive();
        assertThat(integer(nativeEvidence.path("familyScheduleRevisions"))).isPositive();

        // Recompute from individual native samples, rather than trusting a copied profile summary.
        assertThat(nativeEvidence.path("samples").isArray()).isTrue();
        List<Sample> samples = new ArrayList<>();
        for (JsonNode value : nativeEvidence.path("samples")) samples.add(sample(value));
        assertThat(samples).isNotEmpty();
        assertThat(integer(nativeEvidence.path("requests"))).isEqualTo(samples.size());
        assertThat(integer(nativeEvidence.path("durableAttempts"))).isEqualTo(samples.size());
        assertThat(samples).allSatisfy(sample -> {
            assertThat(sample.eventId()).isBetween(17_000_001L, 17_000_020L);
            assertThat(FAMILIES).contains(sample.endpoint());
            assertThat(sample.groupId()).isNotBlank();
            assertThat(sample.requestNanos()).isPositive();
            assertThat(sample.processingNanos()).isPositive();
        });
        List<Sample> steady = samples.stream().filter(sample -> sample.receivedNanos() >= 300 * SECOND).toList();
        assertThat(steady).allSatisfy(sample -> assertThat(sample.bodyBytes()).isEqualTo(65_536));
        assertThat(samples.stream().filter(sample -> sample.bodyBytes() == 5_242_880).toList())
                .hasSize(80).allSatisfy(sample -> assertThat(sample.receivedNanos()).isLessThan(300 * SECOND));

        JsonNode declared = document.path("endpointEnvelopes");
        JsonNode reportedMetrics = nativeEvidence.path("steadyMetrics");
        assertThat(declared.size()).isEqualTo(4);
        assertThat(reportedMetrics.size()).isEqualTo(4);
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (SofascoreEndpointType endpoint : FAMILIES) {
            List<Sample> family = steady.stream().filter(sample -> sample.endpoint() == endpoint).toList();
            JsonNode budget = declared.path(endpoint.name());
            long requestMillis = integer(budget.path("requestMillis"));
            long processingMillis = integer(budget.path("processingMillis"));
            JsonNode retainedFloor = retainedFloors.path(endpoint.name());
            long requestFloorMillis = integer(retainedFloor.path("requestMillis"));
            long processingFloorMillis = integer(retainedFloor.path("processingMillis"));
            assertThat(requestFloorMillis).isPositive();
            assertThat(processingFloorMillis).isPositive();
            assertThat(requestMillis).as("%s request envelope preserves the 75-second candidate floor", endpoint)
                    .isGreaterThanOrEqualTo(requestFloorMillis);
            assertThat(processingMillis).as("%s processing envelope preserves the 75-second candidate floor", endpoint)
                    .isGreaterThanOrEqualTo(processingFloorMillis);
            long maximumRequest = family.stream().mapToLong(Sample::requestNanos).max().orElseThrow();
            long maximumProcessing = family.stream().mapToLong(Sample::processingNanos).max().orElseThrow();
            assertThat(Math.multiplyExact(requestMillis, 1_000_000L)).as("%s request envelope covers measured steady maximum", endpoint)
                    .isGreaterThanOrEqualTo(maximumRequest);
            assertThat(Math.multiplyExact(processingMillis, 1_000_000L)).as("%s processing envelope covers measured steady maximum including SQL", endpoint)
                    .isGreaterThanOrEqualTo(maximumProcessing);
            assertReportedMaximum(reportedMetrics.path(endpoint.name()).path("requestSeconds"), family.size(), maximumRequest);
            assertReportedMaximum(reportedMetrics.path(endpoint.name()).path("processingIncludingSqlSeconds"), family.size(), maximumProcessing);
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis)));

            for (long eventId = 17_000_001; eventId <= 17_000_020; eventId++) {
                long selectedId = eventId;
                List<Sample> pair = family.stream().filter(sample -> sample.eventId() == selectedId).toList();
                assertThat(pair).as("steady samples for %s/%s", eventId, endpoint)
                        .hasSizeGreaterThanOrEqualTo(endpoint == EVENT_LINEUPS ? 5 : 17);
                List<Long> receiptIntervals = new ArrayList<>(), publicationIntervals = new ArrayList<>();
                for (int index = 1; index < pair.size(); index++) {
                    Sample previous = pair.get(index - 1), current = pair.get(index);
                    assertThat(current.dueNanos() - previous.dueNanos()).as("nominal cadence for %s/%s", eventId, endpoint)
                            .isEqualTo((endpoint == EVENT_LINEUPS ? 300 : 100) * SECOND);
                    receiptIntervals.add(current.receivedNanos() - previous.receivedNanos());
                    publicationIntervals.add(current.committedNanos() - previous.committedNanos());
                }
                if (endpoint == EVENT_LINEUPS) {
                    assertThat(percentile(pair.stream().map(sample -> Math.max(0, sample.receivedNanos() - sample.dueNanos())).toList(), .95))
                            .as("lineup nominal lateness for %s", eventId).isLessThanOrEqualTo(15 * SECOND);
                    long eventIndex = eventId - 17_000_001, phaseNanos = 100 * SECOND * eventIndex / 20;
                    assertThat(pair).allSatisfy(sample -> assertThat((sample.dueNanos() - phaseNanos) / (100 * SECOND) % 3)
                            .as("three-round lineup phase for %s", selectedId).isEqualTo(eventIndex % 3));
                } else {
                    assertCriticalIntervals(receiptIntervals, eventId, endpoint, "receipt");
                    assertCriticalIntervals(publicationIntervals, eventId, endpoint, "publication");
                }
            }
        }
        for (int index = 1; index < samples.size(); index++) {
            Sample previous = samples.get(index - 1), current = samples.get(index);
            if (!previous.groupId().equals(current.groupId()))
                assertThat(current.serverArrivalNanos() - previous.responseCompleteNanos())
                        .as("native inter-group fence; at most 1 ms clock quantization tolerance").isGreaterThanOrEqualTo(SECOND - 1_000_000);
        }
        List<Long> debt = steady.stream().filter(sample -> sample.endpoint() != EVENT_LINEUPS)
                .map(sample -> Math.max(0, sample.committedNanos() - sample.dueNanos())).toList();
        int quarter = debt.size() / 4;
        assertThat(percentile(debt, .95)).isLessThanOrEqualTo(15 * SECOND);
        assertThat(percentile(debt.subList(debt.size() - quarter, debt.size()), .95) - percentile(debt.subList(0, quarter), .95))
                .as("steady publication debt must not grow").isLessThanOrEqualTo(5 * SECOND);

        // fits() executes three lineup phases x both kickoff modes x both cost modes x
        // both finalization modes on the production scheduler, using these measured envelopes.
        var profile = new GroupedAdmissionProfile(envelopes, Sha256.hex(profileBytes), "live-v5");
        assertThat(integer(document.path("admission").path("productionSchedulerScenarioCount"))).isEqualTo(24);
        assertThat(GroupedLiveAdmissionSimulation.fits(20, profile)).isTrue();
        assertThat(integer(document.path("admission").path("calculatedCapacity"))).isEqualTo(20);
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV5(profile)).isEqualTo(20);
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(100);
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatCode(() -> policy.admitV5(20, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admitV5(21, profile)).hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }

    private static Sample sample(JsonNode value) {
        return new Sample(integer(value.path("providerEventId")), SofascoreEndpointType.valueOf(value.path("endpoint").asString()),
                value.path("groupId").asString(), integer(value.path("dueNanos")), integer(value.path("receivedNanos")),
                integer(value.path("committedNanos")), integer(value.path("requestNanos")), integer(value.path("processingNanos")),
                integer(value.path("serverArrivalNanos")), integer(value.path("responseCompleteNanos")), integer(value.path("bodyBytes")));
    }

    private static void assertReportedMaximum(JsonNode reported, int count, long maximumNanos) {
        assertThat(integer(reported.path("count"))).isEqualTo(count);
        // The native summary serializes double seconds; exact envelope coverage above uses integer nanoseconds.
        assertThat(decimal(reported.path("maximum")).subtract(BigDecimal.valueOf(maximumNanos).movePointLeft(9)).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.000000001"));
    }

    private static void assertCriticalIntervals(List<Long> intervals, long eventId, SofascoreEndpointType endpoint, String stage) {
        assertThat(intervals).isNotEmpty().allSatisfy(interval -> assertThat(interval).isPositive());
        assertThat(percentile(intervals, .95)).as("%s interval P95 for %s/%s", stage, eventId, endpoint)
                .isLessThanOrEqualTo(105 * SECOND);
        assertThat(percentile(intervals, 1)).as("%s maximum interval for %s/%s", stage, eventId, endpoint)
                .isLessThanOrEqualTo(115 * SECOND);
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

    private record Sample(long eventId, SofascoreEndpointType endpoint, String groupId, long dueNanos,
                          long receivedNanos, long committedNanos, long requestNanos, long processingNanos,
                          long serverArrivalNanos, long responseCompleteNanos, long bodyBytes) { }
}
