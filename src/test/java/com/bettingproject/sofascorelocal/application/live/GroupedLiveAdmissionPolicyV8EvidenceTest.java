package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exact committed V8 evidence plus the production scheduler replay.
 *
 * <p>This is deliberately standard JUnit: it reads only versioned JSON and class
 * resources and does not start a browser, Docker, PostgreSQL or any network transport.</p>
 */
class GroupedLiveAdmissionPolicyV8EvidenceTest {
    private static final String PROFILE_NAME = "WO058-GROUPED-LIVE-V8-PROFILE-20260910.json";
    private static final String NATIVE_NAME = "WO058-GROUPED-LIVE-V8-NATIVE-20260910.json";
    private static final String PROFILE_SHA256 = "c25d65be2a42969c561eadc631eb3499ee21519990e7b44e790a21410efcc1d6";
    private static final String NATIVE_SHA256 = "f5b70709dcc51d9b40223fde1175d3c507190244562355e675689cb06e9a7fc0";
    private static final String PROFILE_BUILDER_SHA256 = "3bcefc3b3f6f49582e03a9ed8bc852d5870832666e1c1e3a86635cb4e522f31f";
    private static final int QUALIFIED_MATCHES = 10;
    private static final long SECOND = 1_000_000_000L;
    private static final long STEADY_WINDOW_START_NANOS = 300 * SECOND;
    private static final List<SofascoreEndpointType> FAMILIES = List.of(
            EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private static final Map<SofascoreEndpointType, EndpointEnvelope> QUALIFIED_ENVELOPES = Map.of(
            EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(500)),
            EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)),
            EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)),
            EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(450)));
    private static final Map<String, String> PRODUCTION_CLASS_SHA256 = Map.of(
            LiveAdmissionPolicy.class.getName(), "c15204a9ede37fa4cf655e5c8c625ddf18e594102d142acbd4ef0c801bfeff1c",
            GroupedLiveAdmissionSimulationV8.class.getName(), "7de9201dceb845360be42cb8101cb17de0bcd3bcf3a5a8abffc3da9426234f16",
            LiveSchedule.class.getName(), "f8eec1a5c87ef6a6912d0308b7d8ddbeca4c97e484fdec06a01622b9f34b3757",
            GroupedLiveScheduleV8.class.getName(), "e3e65b16295329bc61b27af2311911b29d57b732dec6af043c9bf302b4dc2e9e",
            GroupedAdmissionProfile.class.getName(), "15089f45b98f2279dce689afdd48785dc74f4915859e4fd832ac4e4005450651");

    @Test
    void committedV8EvidenceBindsTheImmutableTenMatchProfileWithoutEnablingTransport() throws Exception {
        Path profilePath = Path.of("docs/validation", PROFILE_NAME).toAbsolutePath().normalize();
        assertThat(profilePath).as("the V8 profile is mandatory before a ten-match preparation").isRegularFile();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        assertThat(Sha256.hex(profileBytes)).isEqualTo(PROFILE_SHA256);

        var mapper = JsonMapper.builder().build();
        JsonNode profileDocument = mapper.readTree(profileBytes);
        assertThat(profileDocument.path("schema").asString()).isEqualTo("wo058-grouped-live-capacity-evidence-v4");
        assertThat(profileDocument.path("status").asString()).isEqualTo("QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE");
        assertThat(profileDocument.path("policyVersion").asString()).isEqualTo("live-v8");
        assertThat(integer(profileDocument.path("flywayVersion"))).isEqualTo(51);
        assertThat(integer(profileDocument.path("qualifiedCapacity"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(profileDocument.path("envelopeScope").asString()).isEqualTo("STEADY_64_KIB_ONLY");
        assertThat(profileDocument.path("envelopeBinding").asString()).isEqualTo(
                "immutable qualified V8 upper bounds; each observed steady maximum is rounded upward to 50 ms "
                        + "and must remain within its corresponding bound; the profile is never automatically reduced");
        assertThat(profileDocument.path("labStatus")).hasSize(4);
        assertThat(profileDocument.path("labStatus").get(0).asString()).isEqualTo("EXPERIMENTAL");
        assertThat(profileDocument.path("labStatus").get(1).asString()).isEqualTo("LOCAL_ONLY");
        assertThat(profileDocument.path("labStatus").get(2).asString()).isEqualTo("NOT_PRODUCTION_APPROVED");
        assertThat(profileDocument.path("labStatus").get(3).asString()).isEqualTo("NO_CRITICAL_DEPENDENCY");

        JsonNode profileInitialWave = profileDocument.path("initialWave");
        assertBoolean(profileInitialWave.path("separatelyMeasured"), true);
        assertBoolean(profileInitialWave.path("coveredBySteadyEnvelopes"), false);
        assertBoolean(profileInitialWave.path("partOfSteadyV8Scheduler"), false);
        assertThat(profileInitialWave.path("executionLane").asString()).isEqualTo("INITIAL_COLD_START_STRESS");
        assertThat(integer(profileInitialWave.path("pairs"))).isEqualTo(40);
        assertThat(integer(profileInitialWave.path("firstResponseBytesPerPair"))).isEqualTo(5_242_880);
        assertThat(integer(profileInitialWave.path("postCompletionFenceMillis"))).isEqualTo(500);
        assertThat(integer(profileInitialWave.path("strictLaneClearanceMillis"))).isGreaterThanOrEqualTo(60_001);
        assertThat(integer(profileInitialWave.path("departureToStrictLaneClearanceMillis"))).isGreaterThanOrEqualTo(60_001);

        JsonNode cadence = profileDocument.path("cadence");
        assertThat(integer(cadence.path("criticalSeconds"))).isEqualTo(60);
        assertThat(integer(cadence.path("lineupSeconds"))).isEqualTo(60);
        assertThat(integer(cadence.path("intraGroupDelayMillis"))).isZero();
        assertThat(integer(cadence.path("interGroupDelayMillis"))).isEqualTo(500);
        assertThat(integer(cadence.path("interGroupSlotReserveMillis"))).isEqualTo(1_000);
        assertThat(integer(cadence.path("requestEmissionHeadStartMillis"))).isEqualTo(500);
        assertThat(integer(cadence.path("strictGroupPhaseReservationMillis"))).isEqualTo(6_000);
        assertThat(integer(cadence.path("minimumPostCompletionDelayMillis"))).isEqualTo(500);
        assertThat(integer(cadence.path("maximumDeparturesPer60Seconds"))).isEqualTo(45);
        assertThat(integer(cadence.path("maximumDeparturesPerHour"))).isEqualTo(2_756);
        assertThat(decimal(cadence.path("maximumTemporalUtilization"))).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(cadence.path("strictNormalPathDepartureTimestamp").asString()).isEqualTo("requestedNanos");
        assertThat(cadence.path("strictNormalPathDepartureCadenceScope").asString()).isEqualTo("per-event-family");
        assertThat(integer(cadence.path("strictNormalPathMaximumDepartureIntervalSeconds"))).isEqualTo(60);

        JsonNode admission = profileDocument.path("admission");
        assertThat(integer(admission.path("calculatedCapacity"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(integer(admission.path("productionSchedulerScenarioCount"))).isEqualTo(16);
        assertBoolean(admission.path("productionSchedulerScenariosExecutedForThisProfile"), true);
        assertBoolean(admission.path("profileAppliedAutomatically"), false);
        assertThat(integer(admission.path("weightedMinuteMillisPerMatch"))).isEqualTo(6_000);
        assertThat(integer(admission.path("weightedMinuteMillisAtQualifiedCapacity"))).isEqualTo(60_000);
        assertThat(integer(admission.path("availableMinuteMillis"))).isEqualTo(60_000);
        assertThat(integer(admission.path("uncappedMeanBoundCapacity"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(integer(admission.path("hourlyCallsPerMatch"))).isEqualTo(248);
        assertThat(integer(admission.path("hourlyHeadroomCallsAtQualifiedCapacity"))).isEqualTo(2_480);

        assertThat(integer(profileDocument.path("tenMatchHourlyBoundary").path("callsForTenMatches"))).isEqualTo(2_480);
        assertThat(integer(profileDocument.path("tenMatchHourlyBoundary").path("ninetyPercentOfMaximumHourlyDepartures")))
                .isEqualTo(2_480);
        assertThat(integer(profileDocument.path("tenMatchHourlyBoundary").path("remainingCallsAtIntegerNinetyPercentBoundary")))
                .isZero();

        Path nativePath = profilePath.getParent().resolve(profileDocument.path("nativeEvidence").path("path").asString()).normalize();
        assertThat(nativePath).isEqualTo(profilePath.getParent().resolve(NATIVE_NAME));
        assertThat(nativePath).as("the native sustained loopback evidence is mandatory").isRegularFile();
        byte[] nativeBytes = Files.readAllBytes(nativePath);
        assertThat(Sha256.hex(nativeBytes)).isEqualTo(NATIVE_SHA256);
        assertThat(profileDocument.path("nativeEvidence").path("sha256").asString()).isEqualTo(NATIVE_SHA256);

        Path builderPath = Path.of("docs/validation/v8-profile-builder/V8MeasuredProfile.java").toAbsolutePath().normalize();
        assertThat(builderPath).as("the V8 profile builder source remains auditable").isRegularFile();
        assertThat(Sha256.hex(Files.readAllBytes(builderPath))).isEqualTo(PROFILE_BUILDER_SHA256);
        assertThat(profileDocument.path("profileBuilderSourceSha256").asString()).isEqualTo(PROFILE_BUILDER_SHA256);

        assertThat(profileDocument.path("productionClassSha256")).hasSize(PRODUCTION_CLASS_SHA256.size());
        for (Map.Entry<String, String> entry : PRODUCTION_CLASS_SHA256.entrySet()) {
            assertThat(profileDocument.path("productionClassSha256").path(entry.getKey()).asString())
                    .as("recorded production class hash for %s", entry.getKey())
                    .isEqualTo(entry.getValue());
            assertThat(classSha256(Class.forName(entry.getKey())))
                    .as("loaded production class hash for %s", entry.getKey())
                    .isEqualTo(entry.getValue());
        }

        JsonNode nativeDocument = mapper.readTree(nativeBytes);
        assertThat(nativeDocument.path("status").asString()).isEqualTo("PASSED");
        assertThat(nativeDocument.path("scope").asString()).isEqualTo("EXPERIMENTAL LOCAL_ONLY SYNTHETIC_LOOPBACK");
        assertThat(nativeDocument.path("policyVersion").asString()).isEqualTo("live-v8");
        assertBoolean(nativeDocument.path("loopbackTransportOnly"), true);
        assertBoolean(nativeDocument.path("operatorDatabaseUsed"), false);
        assertThat(nativeDocument.path("loopbackOrigin").asString()).matches("http://127\\.0\\.0\\.1:[1-9][0-9]*");
        assertThat(integer(nativeDocument.path("matches"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(integer(nativeDocument.path("criticalIntervalSeconds"))).isEqualTo(60);
        assertThat(integer(nativeDocument.path("lineupsIntervalSeconds"))).isEqualTo(60);
        assertThat(integer(nativeDocument.path("effectiveRequestTimeoutMillis"))).isEqualTo(30_000);
        assertThat(integer(nativeDocument.path("warmupSeconds"))).isEqualTo(300);
        assertThat(integer(nativeDocument.path("requiredSteadySeconds"))).isEqualTo(1_800);
        assertThat(decimal(nativeDocument.path("steadyElapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(1_800));
        assertThat(decimal(nativeDocument.path("strictLaneElapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(2_100));
        for (String flag : List.of("sustainedQualification", "productionDockerDfProbePerRequest",
                "productionPersistentResilience", "productionTransportDiagnosticPersistence", "workerAndChildrenClosed",
                "strictLaneStartedAfterColdDrain")) {
            assertBoolean(nativeDocument.path(flag), true);
        }
        for (String count : List.of("realProviderCalls", "offScopeRequests", "missedCycles", "pressureDeferrals")) {
            assertThat(integer(nativeDocument.path(count))).as(count).isZero();
        }
        assertThat(array(nativeDocument, "postExchangeFenceWaitEvents")).isEmpty();
        assertThat(array(nativeDocument, "pressureDeferralEvents")).isEmpty();

        JsonNode nativeStrictScheduler = nativeDocument.path("strictScheduler");
        assertThat(integer(nativeStrictScheduler.path("interGroupSlotReserveMillis"))).isEqualTo(1_000);
        assertThat(integer(nativeStrictScheduler.path("requestEmissionHeadStartMillis"))).isEqualTo(500);
        assertThat(integer(nativeStrictScheduler.path("groupPhaseReservationMillis"))).isEqualTo(6_000);
        assertThat(nativeStrictScheduler.path("normalJ4EmissionOverrunState").asString())
                .isEqualTo("WAITING_CADENCE_RECHECK");

        for (SofascoreEndpointType endpoint : FAMILIES) {
            assertEnvelope(profileDocument.path("endpointEnvelopes").path(endpoint.name()), QUALIFIED_ENVELOPES.get(endpoint));
            assertEnvelope(nativeDocument.path("candidateEndpointEnvelopes").path(endpoint.name()),
                    QUALIFIED_ENVELOPES.get(endpoint));
        }

        List<JsonNode> samples = array(nativeDocument, "samples");
        assertThat(samples).hasSize((int) integer(nativeDocument.path("requests")));
        for (String count : List.of("durableAttempts", "durableDepartures", "durableDepartureCompletions",
                "durableCompleteTransportDiagnostics")) {
            assertThat(integer(nativeDocument.path(count))).as(count).isEqualTo(samples.size());
        }
        assertThat(integer(nativeDocument.path("receivedBytes")))
                .isEqualTo(samples.stream().mapToLong(sample -> integer(sample.path("bodyBytes"))).sum());

        List<JsonNode> cold = samples.stream()
                .filter(sample -> "INITIAL_COLD_START_STRESS".equals(sample.path("executionLane").asString()))
                .toList();
        assertThat(cold).hasSize((int) integer(nativeDocument.path("coldStartSamples")));
        assertThat(cold).hasSize(40)
                .allSatisfy(sample -> assertThat(integer(sample.path("bodyBytes"))).isEqualTo(5_242_880));

        List<JsonNode> strict = samples.stream()
                .filter(sample -> "V8_STEADY".equals(sample.path("executionLane").asString()))
                .toList();
        assertThat(strict).hasSize((int) integer(nativeDocument.path("strictV8SteadySamples")));
        assertThat(strict).isNotEmpty()
                .allSatisfy(sample -> assertThat(integer(sample.path("bodyBytes"))).isEqualTo(65_536));

        List<JsonNode> steadyWindow = strict.stream()
                .filter(sample -> integer(sample.path("laneRequestedNanos")) >= STEADY_WINDOW_START_NANOS)
                .toList();
        assertThat(steadyWindow).hasSize((int) integer(profileDocument.path("measurement").path("steadyCalls")));
        assertThat(steadyWindow).isNotEmpty()
                .allSatisfy(sample -> {
                    assertThat(integer(sample.path("laneRequestedNanos"))).isGreaterThanOrEqualTo(STEADY_WINDOW_START_NANOS);
                    assertThat(integer(sample.path("bodyBytes"))).isEqualTo(65_536);
                });
        assertThat(strict).anySatisfy(sample ->
                assertThat(integer(sample.path("laneRequestedNanos"))).isLessThan(STEADY_WINDOW_START_NANOS));
        assertThat(samples.stream()
                .filter(sample -> integer(sample.path("laneRequestedNanos")) >= STEADY_WINDOW_START_NANOS)
                .allMatch(sample -> "V8_STEADY".equals(sample.path("executionLane").asString()))).isTrue();

        JsonNode endpointMetrics = profileDocument.path("measurement").path("endpointMetrics");
        for (SofascoreEndpointType endpoint : FAMILIES) {
            List<JsonNode> family = steadyWindow.stream()
                    .filter(sample -> endpoint.name().equals(sample.path("endpoint").asString()))
                    .toList();
            JsonNode metrics = endpointMetrics.path(endpoint.name());
            assertThat(family).hasSize((int) integer(metrics.path("steadySamples")));

            long maximumRequestNanos = family.stream()
                    .mapToLong(sample -> integer(sample.path("requestNanos"))).max().orElseThrow();
            long maximumProcessingNanos = family.stream()
                    .mapToLong(sample -> integer(sample.path("processingNanos"))).max().orElseThrow();
            assertThat(maximumRequestNanos).isEqualTo(integer(metrics.path("maximumRequestNanos")));
            assertThat(maximumProcessingNanos).isEqualTo(integer(metrics.path("maximumProcessingNanos")));

            JsonNode observed = profileDocument.path("observedRoundedSteadyEnvelopes").path(endpoint.name());
            long observedRequestMillis = integer(observed.path("requestMillis"));
            long observedProcessingMillis = integer(observed.path("processingMillis"));
            assertThat(observedRequestMillis).isEqualTo(roundUpToFiftyMillis(maximumRequestNanos));
            assertThat(observedProcessingMillis).isEqualTo(roundUpToFiftyMillis(maximumProcessingNanos));
            assertThat(observedRequestMillis % 50).isZero();
            assertThat(observedProcessingMillis % 50).isZero();

            EndpointEnvelope qualified = QUALIFIED_ENVELOPES.get(endpoint);
            assertThat(observedRequestMillis).isLessThanOrEqualTo(qualified.requestEnvelope().toMillis());
            assertThat(observedProcessingMillis).isLessThanOrEqualTo(qualified.processingEnvelope().toMillis());
        }

        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        envelopes.putAll(QUALIFIED_ENVELOPES);
        var profile = new GroupedAdmissionProfile(envelopes, PROFILE_SHA256, "live-v8");
        assertThat(LiveSchedule.v8InterGroupSlotReserve()).isEqualTo(Duration.ofSeconds(1));
        assertThat(LiveSchedule.v8RequestEmissionHeadStart()).isEqualTo(Duration.ofMillis(500));
        assertThat(LiveSchedule.v8StrictGroupReservation(profile)).isEqualTo(Duration.ofMillis(6_000));
        assertThat(GroupedLiveAdmissionSimulationV8.SCENARIOS).isEqualTo(16);
        assertThat(GroupedLiveAdmissionSimulationV8.POST_EXCHANGE_FENCE).isEqualTo(Duration.ofMillis(500));
        assertThat(GroupedLiveAdmissionSimulationV8.MAXIMUM_DEPARTURES_PER_MINUTE).isEqualTo(45);
        assertThat(GroupedLiveAdmissionSimulationV8.MAXIMUM_DEPARTURES_PER_HOUR).isEqualTo(2_756);
        assertThat(GroupedLiveAdmissionSimulationV8.hasStrictMinuteDepartureBudget(QUALIFIED_MATCHES, profile)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV8.fits(QUALIFIED_MATCHES, profile)).isTrue();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV8(profile)).isEqualTo(QUALIFIED_MATCHES);
        assertThat(GroupedLiveAdmissionSimulationV8.hasStrictMinuteDepartureBudget(QUALIFIED_MATCHES + 1, profile)).isFalse();
        assertThat(GroupedLiveAdmissionSimulationV8.fits(QUALIFIED_MATCHES + 1, profile)).isFalse();

        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(QUALIFIED_MATCHES);
        properties.getGroupedV8().setQualificationSha256(PROFILE_SHA256);
        QUALIFIED_ENVELOPES.forEach((endpoint, envelope) -> {
            properties.getGroupedV8().getEndpoints().get(endpoint).setRequestEnvelope(envelope.requestEnvelope());
            properties.getGroupedV8().getEndpoints().get(endpoint).setProcessingEnvelope(envelope.processingEnvelope());
        });
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.groupedAdmissionProfileV8()).isEqualTo(profile);

        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatCode(() -> policy.admitV8(QUALIFIED_MATCHES, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admitV8(QUALIFIED_MATCHES + 1, profile))
                .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }

    private static List<JsonNode> array(JsonNode node, String field) {
        JsonNode values = node.path(field);
        assertThat(values.isArray()).as("%s is an array in the committed evidence", field).isTrue();
        List<JsonNode> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private static void assertEnvelope(JsonNode document, EndpointEnvelope expected) {
        assertThat(integer(document.path("requestMillis"))).isEqualTo(expected.requestEnvelope().toMillis());
        assertThat(integer(document.path("processingMillis"))).isEqualTo(expected.processingEnvelope().toMillis());
    }

    private static long integer(JsonNode value) {
        assertThat(value.isIntegralNumber()).as("integer evidence value is recorded").isTrue();
        return value.longValue();
    }

    private static BigDecimal decimal(JsonNode value) {
        assertThat(value.isNumber()).as("numeric evidence value is recorded").isTrue();
        return value.decimalValue();
    }

    private static void assertBoolean(JsonNode value, boolean expected) {
        assertThat(value.isBoolean()).as("boolean evidence value is recorded").isTrue();
        assertThat(value.asBoolean()).isEqualTo(expected);
    }

    private static long roundUpToFiftyMillis(long nanos) {
        assertThat(nanos).isPositive();
        return Math.multiplyExact(Math.floorDiv(Math.addExact(nanos, 50_000_000L - 1), 50_000_000L), 50);
    }

    private static String classSha256(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            assertThat(stream).as("class resource for %s", type.getName()).isNotNull();
            return Sha256.hex(stream.readAllBytes());
        }
    }
}
