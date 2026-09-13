package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.security.Sha256;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Committed V10 local-capacity evidence and its offline admission replay.
 *
 * <p>The test reads only the versioned evidence file and compiled class resources.
 * It starts no browser, Docker, PostgreSQL, campaign, or provider transport. The V10
 * adapter intentionally reuses the immutable V9 J4/J5 scheduler, whose own source and
 * class hashes remain bound by {@link GroupedLiveAdmissionPolicyV9EvidenceTest}.</p>
 */
class GroupedLiveAdmissionPolicyV10EvidenceTest {
    private static final String PROFILE_NAME = "WO058-GROUPED-LIVE-V10-PROFILE-20260912.json";
    private static final String PROFILE_SHA256 = "849b7453eaae058f8b9ce6d70565fb45d21ba4787d60d0b1d2133e1a15947366";
    private static final int QUALIFIED_MATCHES = 8;
    private static final int NOMINAL_DEPARTURES_PER_MINUTE = 32;
    private static final Map<SofascoreEndpointType, EndpointEnvelope> QUALIFIED_ENVELOPES = Map.of(
            EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(500)),
            EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)),
            EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)),
            EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(450)));
    private static final List<String> V10_PRODUCTION_CLASSES = List.of(
            LiveAdmissionPolicyV10.class.getName(),
            GroupedLiveAdmissionSimulationV10.class.getName(),
            V10GroupedScheduleProfile.class.getName(),
            ProviderResilienceData.DepartureProfile.class.getName());
    private static final Map<String, String> PRODUCTION_CLASS_SHA256 = Map.of(
            LiveAdmissionPolicyV10.class.getName(), "ea3e6517916e055904b50afc2d1ae92a2fa217efbe47e896f8e8b2792d5eea7f",
            GroupedLiveAdmissionSimulationV10.class.getName(), "a8038e642ef8248558a85ae93c0ccea14c04bd9935ff7fef0066967863fb2d8f",
            V10GroupedScheduleProfile.class.getName(), "99b5c95a1a75e126c487a9924e91fac6de7f8a1022875eeaf8093262ab527433",
            ProviderResilienceData.DepartureProfile.class.getName(), "f3d353dbb3558fc3566fad20fc06f0acc420d93cb5770582f578ce8920b4526d");

    @Test
    void committedV10EvidenceBindsTheEightMatchLocalProfileWithoutEnablingTransport() throws Exception {
        Path profilePath = Path.of("docs/validation", PROFILE_NAME).toAbsolutePath().normalize();
        assertThat(profilePath).as("the V10 profile is mandatory before a V10 preparation").isRegularFile();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        assertThat(Sha256.hex(profileBytes)).isEqualTo(PROFILE_SHA256);

        JsonNode profileDocument = JsonMapper.builder().build().readTree(profileBytes);
        assertThat(profileDocument.path("schema").asString()).isEqualTo("wo058-grouped-live-v10-capacity-evidence-v2");
        assertThat(profileDocument.path("status").asString()).isEqualTo("QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE");
        assertThat(profileDocument.path("policyVersion").asString()).isEqualTo("live-v10");
        assertThat(integer(profileDocument.path("flywayVersion"))).isEqualTo(54);
        assertThat(integer(profileDocument.path("qualifiedCapacity"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(array(profileDocument.path("labStatus"))).containsExactly(
                "EXPERIMENTAL", "LOCAL_ONLY", "NOT_PRODUCTION_APPROVED", "NO_CRITICAL_DEPENDENCY");

        JsonNode candidate = profileDocument.path("candidateConfiguration");
        assertThat(integer(candidate.path("maximumSelectedMatches"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(integer(candidate.path("maximumEndpointsPerMatchPerMinute"))).isEqualTo(4);
        assertThat(integer(candidate.path("nominalEndpointsPerMinute"))).isEqualTo(NOMINAL_DEPARTURES_PER_MINUTE);
        assertThat(integer(candidate.path("maximumDurableDeparturesPer60Seconds"))).isEqualTo(35);
        assertThat(integer(candidate.path("maximumDurableDeparturesPerHour"))).isEqualTo(2_100);
        assertThat(integer(candidate.path("perMinuteHeadroomAtNominalCapacity"))).isEqualTo(3);
        assertThat(integer(candidate.path("hourlyCallsPerMatchIncludingInitialAndFinalReservation"))).isEqualTo(248);
        assertThat(integer(candidate.path("hourlyCallsAtMaximumSelection"))).isEqualTo(1_984);
        assertThat(integer(candidate.path("hourlyHeadroomAtMaximumSelection"))).isEqualTo(116);

        JsonNode qualification = profileDocument.path("qualification");
        assertBoolean(qualification.path("independentV10BindingRequired"), true);
        assertThat(qualification.path("method").asString()).isEqualTo(
                "V10_OFFLINE_ADMISSION_AND_V9_SCHEDULER_REPLAY_WITH_LOCAL_DURABLE_35_PER_60S_AND_2100_PER_HOUR_GUARDS");
        assertThat(qualification.path("assertion").asString())
                .contains("eight-match capacity").contains("35 per rolling 60 seconds").contains("2100 per rolling hour");
        assertBoolean(qualification.path("profileAppliedAutomatically"), false);
        assertBoolean(qualification.path("localReplayOnly"), true);

        JsonNode v9Reference = profileDocument.path("historicalV9SchedulerReference");
        assertThat(v9Reference.path("policyVersion").asString()).isEqualTo("live-v9");
        assertThat(v9Reference.path("evidenceTest").asString()).isEqualTo("GroupedLiveAdmissionPolicyV9EvidenceTest");
        assertThat(v9Reference.path("assertion").asString())
                .contains("immutable V9 scheduler contract").contains("not copied into this V10 binding");

        for (SofascoreEndpointType endpoint : List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS)) {
            EndpointEnvelope expected = QUALIFIED_ENVELOPES.get(endpoint);
            JsonNode envelope = profileDocument.path("endpointEnvelopes").path(endpoint.name());
            assertThat(integer(envelope.path("requestMillis"))).isEqualTo(expected.requestEnvelope().toMillis());
            assertThat(integer(envelope.path("processingMillis"))).isEqualTo(expected.processingEnvelope().toMillis());
        }

        JsonNode cadence = profileDocument.path("cadence");
        assertThat(integer(cadence.path("criticalSeconds"))).isEqualTo(60);
        assertThat(integer(cadence.path("lineupSeconds"))).isEqualTo(60);
        assertThat(integer(cadence.path("intraGroupDelayMillis"))).isZero();
        assertThat(integer(cadence.path("interGroupDelayMillis"))).isEqualTo(500);
        assertThat(integer(cadence.path("interGroupSlotReserveMillis"))).isEqualTo(1_000);
        assertThat(integer(cadence.path("requestEmissionHeadStartMillis"))).isEqualTo(500);
        assertThat(integer(cadence.path("strictGroupPhaseReservationMillis"))).isEqualTo(6_000);
        assertThat(integer(cadence.path("maximumEndpointsPerGroup"))).isEqualTo(4);
        assertThat(decimal(cadence.path("maximumTemporalUtilization"))).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(integer(cadence.path("minimumPostCompletionDelayMillis"))).isEqualTo(500);
        assertThat(integer(cadence.path("nominalEndpointsPerMinuteAtMaximumSelection")))
                .isEqualTo(NOMINAL_DEPARTURES_PER_MINUTE);
        assertThat(integer(cadence.path("maximumDeparturesPer60Seconds"))).isEqualTo(35);
        assertThat(integer(cadence.path("maximumDeparturesPerHour"))).isEqualTo(2_100);
        assertThat(cadence.path("strictNormalPathDepartureTimestamp").asString()).isEqualTo("requestedNanos");
        assertThat(cadence.path("strictNormalPathDepartureCadenceScope").asString()).isEqualTo("per-event-family");
        assertThat(integer(cadence.path("strictNormalPathMaximumDepartureIntervalSeconds"))).isEqualTo(60);

        JsonNode admission = profileDocument.path("admission");
        assertThat(integer(admission.path("calculatedCapacity"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(integer(admission.path("productionSchedulerScenarioCount"))).isEqualTo(16);
        assertBoolean(admission.path("productionSchedulerScenariosExecutedForThisProfile"), true);
        assertBoolean(admission.path("profileAppliedAutomatically"), false);
        assertBoolean(admission.path("localReplayOnly"), true);
        assertThat(integer(admission.path("weightedMinuteMillisPerMatch"))).isEqualTo(6_000);
        assertThat(integer(admission.path("weightedMinuteMillisAtQualifiedCapacity"))).isEqualTo(48_000);
        assertThat(integer(admission.path("availableMinuteMillis"))).isEqualTo(60_000);
        assertThat(integer(admission.path("hourlyCallsPerMatch"))).isEqualTo(248);
        assertThat(integer(admission.path("hourlyCallsAtQualifiedCapacity"))).isEqualTo(1_984);
        assertThat(integer(admission.path("hourlyHeadroomCallsAtQualifiedCapacity"))).isEqualTo(116);

        JsonNode controls = profileDocument.path("v10SchedulingControls");
        assertBoolean(controls.path("finalResultOnlyStopsBeforeJ5"), true);
        assertBoolean(controls.path("statisticsAndIncidentsSuppressedBeforeEligibleStatus"), true);
        assertBoolean(controls.path("lineupsSuppressedWhenPlayerStatisticsExplicitlyFalse"), true);
        assertThat(integer(controls.path("halftimeQuietPeriodMinutes"))).isEqualTo(15);
        assertThat(integer(controls.path("terminalStatistics404RecheckMaximum"))).isEqualTo(1);
        assertThat(integer(controls.path("suspendedJ4OnlyRecheckSeconds"))).isEqualTo(60);
        assertBoolean(controls.path("suspendedSuppressesAllJ5Families"), true);
        assertBoolean(controls.path("suspendedStatusReasonProjected"), true);
        assertBoolean(controls.path("conditionalNotModifiedUsesLocalCacheOnly"), true);

        JsonNode binding = profileDocument.path("productionClassHashBinding");
        assertThat(binding.path("status").asString()).isEqualTo("QUALIFIED_V10_COMPILED_CLASS_BINDING");
        assertThat(array(binding.path("requiredClasses"))).containsExactlyElementsOf(V10_PRODUCTION_CLASSES);
        assertThat(binding.path("completionCriteria").asString())
                .contains("independent V10 evidence test").contains("immutable V9 scheduler");
        assertThat(profileDocument.path("productionClassSha256")).hasSize(PRODUCTION_CLASS_SHA256.size());
        for (Map.Entry<String, String> entry : PRODUCTION_CLASS_SHA256.entrySet()) {
            assertThat(profileDocument.path("productionClassSha256").path(entry.getKey()).asString())
                    .as("recorded production class hash for %s", entry.getKey()).isEqualTo(entry.getValue());
            assertThat(classSha256(Class.forName(entry.getKey())))
                    .as("loaded production class hash for %s", entry.getKey()).isEqualTo(entry.getValue());
        }

        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        envelopes.putAll(QUALIFIED_ENVELOPES);
        var profile = new GroupedAdmissionProfile(envelopes, PROFILE_SHA256, "live-v9");
        assertThat(V10GroupedScheduleProfile.asV9SchedulerProfile(profile)).isSameAs(profile);
        assertThat(V10GroupedScheduleProfile.strictGroupReservation(profile)).isEqualTo(Duration.ofMillis(6_000));
        assertThat(V10GroupedScheduleProfile.strictGroupReservation(profile).multipliedBy(QUALIFIED_MATCHES))
                .isEqualTo(Duration.ofMillis(48_000));
        assertThat(GroupedLiveAdmissionSimulationV10.MAXIMUM_DEPARTURES_PER_MINUTE).isEqualTo(35);
        assertThat(GroupedLiveAdmissionSimulationV10.MAXIMUM_DEPARTURES_PER_HOUR).isEqualTo(2_100);
        assertThat(GroupedLiveAdmissionSimulationV10.hasStrictMinuteDepartureBudget(QUALIFIED_MATCHES, profile)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV10.fits(QUALIFIED_MATCHES, profile)).isTrue();
        assertThat(LiveAdmissionPolicyV10.qualifiedCapacity(profile)).isEqualTo(QUALIFIED_MATCHES);
        assertThat(LiveAdmissionPolicyV10.estimatedLiveCallsPerMinute(QUALIFIED_MATCHES))
                .isEqualTo((double) NOMINAL_DEPARTURES_PER_MINUTE);
        assertThat(GroupedLiveAdmissionSimulationV10.hasStrictMinuteDepartureBudget(QUALIFIED_MATCHES + 1, profile))
                .isFalse();
        assertThat(GroupedLiveAdmissionSimulationV10.fits(QUALIFIED_MATCHES + 1, profile)).isFalse();

        assertThat(ProviderResilienceData.DepartureProfile.LIVE_V10.persistenceValue()).isEqualTo("live-v10");
        assertThat(ProviderResilienceData.DepartureProfile.LIVE_V10.minimumDepartureInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(ProviderResilienceData.DepartureProfile.LIVE_V10.maximumDeparturesPerMinute()).isEqualTo(35);
        assertThat(ProviderResilienceData.DepartureProfile.LIVE_V10.maximumDeparturesPerHour()).isEqualTo(2_100);

        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(QUALIFIED_MATCHES);
        properties.getGroupedV10().setQualificationSha256(PROFILE_SHA256);
        QUALIFIED_ENVELOPES.forEach((endpoint, envelope) -> {
            properties.getGroupedV10().getEndpoints().get(endpoint).setRequestEnvelope(envelope.requestEnvelope());
            properties.getGroupedV10().getEndpoints().get(endpoint).setProcessingEnvelope(envelope.processingEnvelope());
        });
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.groupedAdmissionProfileV10()).isEqualTo(profile);

        var policy = new LiveAdmissionPolicyV10(properties, new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE));
        assertThatCode(() -> policy.admit(QUALIFIED_MATCHES, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admit(QUALIFIED_MATCHES + 1, profile))
                .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }

    private static List<String> array(JsonNode values) {
        assertThat(values.isArray()).as("the committed evidence has an array").isTrue();
        var result = new java.util.ArrayList<String>();
        values.forEach(value -> result.add(value.asString()));
        return result;
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

    private static String classSha256(Class<?> type) throws Exception {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            assertThat(stream).as("class resource for %s", type.getName()).isNotNull();
            return Sha256.hex(stream.readAllBytes());
        }
    }
}
