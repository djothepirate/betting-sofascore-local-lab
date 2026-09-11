package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.adapter.sofascore.live.LivePayloadNormalizer;
import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
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
 * V9's committed upper-bound evidence and its offline production-scheduler replay.
 *
 * <p>This standard unit test reads only versioned local files and class resources.
 * It does not start a browser, Docker, PostgreSQL, a campaign, or a provider transport.</p>
 */
class GroupedLiveAdmissionPolicyV9EvidenceTest {
    private static final String PROFILE_NAME = "WO058-GROUPED-LIVE-V9-SUSPENDED-20260911.json";
    private static final String PROFILE_SHA256 = "353b6e7c248a73ddbaab0c55a28c9b5e27dae8eaa5594434964b7df265c7d1a2";
    private static final String V8_PROFILE_NAME = "WO058-GROUPED-LIVE-V8-PROFILE-20260910.json";
    private static final String V8_NATIVE_NAME = "WO058-GROUPED-LIVE-V8-NATIVE-20260910.json";
    private static final String V8_PROFILE_SHA256 = "4255f5327681398013dc9a0ab0f2250c7168558a399d164e299bb54b8b856de3";
    private static final String V8_NATIVE_SHA256 = "f5b70709dcc51d9b40223fde1175d3c507190244562355e675689cb06e9a7fc0";
    private static final int QUALIFIED_MATCHES = 10;
    private static final Map<SofascoreEndpointType, EndpointEnvelope> QUALIFIED_ENVELOPES = Map.of(
            EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(500)),
            EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)),
            EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)),
            EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(450)));
    private static final Map<String, String> PRODUCTION_CLASS_SHA256 = Map.of(
            LiveAdmissionPolicy.class.getName(), "3bca981a21b14ec4c4b809e4861e9b2f01c6c5a1adfbe4268f99cb794dbf661d",
            GroupedLiveAdmissionSimulationV9.class.getName(), "fba1dcbff3996e97c6b24c2fc771380842991388595a5ed139525772928abb53",
            LiveSchedule.class.getName(), "38af7cc3e909d88c1f1705487ae505f8f0af6ecd9818630a942af7b81d39acce",
            GroupedLiveScheduleV9.class.getName(), "a72e74c09a8c966d36758b038caadc2c9d53ea026b9d6648d0ec94fa83c2a7f8",
            GroupedAdmissionProfile.class.getName(), "f803973f03feeef102c351e8da8e36e2dfefd12c5cfbd39b17b13cad242bb43e",
            LiveJ4ControlFacts.class.getName(), "e6efdbdb5214d63e4e84f3adee19c957aeae34322553110ed68e9e086c32f086",
            LivePayloadNormalizer.class.getName(), "e83ae7649194194326af43130449baa13a6a576dea3d2e899850db617dab25f0");

    @Test
    void committedV9EvidenceBindsASeparateTenMatchProfileWithoutEnablingLiveTransport() throws Exception {
        Path profilePath = Path.of("docs/validation", PROFILE_NAME).toAbsolutePath().normalize();
        assertThat(profilePath).as("the V9 profile is mandatory before a V9 preparation").isRegularFile();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        assertThat(Sha256.hex(profileBytes)).isEqualTo(PROFILE_SHA256);
        assertThat(PROFILE_SHA256).isNotEqualTo(V8_PROFILE_SHA256);

        JsonNode profileDocument = JsonMapper.builder().build().readTree(profileBytes);
        assertThat(profileDocument.path("schema").asString()).isEqualTo("wo058-grouped-live-v9-capacity-evidence-v2");
        assertThat(profileDocument.path("status").asString()).isEqualTo("QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE");
        assertThat(profileDocument.path("policyVersion").asString()).isEqualTo("live-v9");
        assertThat(integer(profileDocument.path("flywayVersion"))).isEqualTo(52);
        assertThat(integer(profileDocument.path("qualifiedCapacity"))).isEqualTo(QUALIFIED_MATCHES);
        assertThat(profileDocument.path("qualificationMethod").asString())
                .isEqualTo("V8_LOCAL_LOOPBACK_UPPER_BOUND_AND_V9_OFFLINE_PRODUCTION_SCHEDULER_REPLAY_WITH_SUSPENDED_J4_ONLY");
        assertThat(array(profileDocument.path("labStatus"))).containsExactly(
                "EXPERIMENTAL", "LOCAL_ONLY", "NOT_PRODUCTION_APPROVED", "NO_CRITICAL_DEPENDENCY");

        JsonNode sourceUpperBound = profileDocument.path("sourceUpperBound");
        assertThat(sourceUpperBound.path("v8Profile").path("path").asString()).isEqualTo(V8_PROFILE_NAME);
        assertThat(sourceUpperBound.path("v8Profile").path("sha256").asString()).isEqualTo(V8_PROFILE_SHA256);
        assertThat(sourceUpperBound.path("v8NativeEvidence").path("path").asString()).isEqualTo(V8_NATIVE_NAME);
        assertThat(sourceUpperBound.path("v8NativeEvidence").path("sha256").asString()).isEqualTo(V8_NATIVE_SHA256);
        assertThat(sourceUpperBound.path("assertion").asString()).contains("V9 schedules no more than that upper bound")
                .contains("suspended status suppresses all J5 families");
        assertThat(Sha256.hex(Files.readAllBytes(profilePath.getParent().resolve(V8_PROFILE_NAME)))).isEqualTo(V8_PROFILE_SHA256);
        assertThat(Sha256.hex(Files.readAllBytes(profilePath.getParent().resolve(V8_NATIVE_NAME)))).isEqualTo(V8_NATIVE_SHA256);

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
        assertThat(integer(cadence.path("maximumDeparturesPer60Seconds"))).isEqualTo(45);
        assertThat(integer(cadence.path("maximumDeparturesPerHour"))).isEqualTo(2_756);
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
        assertThat(integer(admission.path("weightedMinuteMillisAtQualifiedCapacity"))).isEqualTo(60_000);
        assertThat(integer(admission.path("availableMinuteMillis"))).isEqualTo(60_000);
        assertThat(integer(admission.path("hourlyCallsPerMatch"))).isEqualTo(248);
        assertThat(integer(admission.path("hourlyHeadroomCallsAtQualifiedCapacity"))).isEqualTo(2_480);

        JsonNode controls = profileDocument.path("v9SchedulingControls");
        assertBoolean(controls.path("finalResultOnlyStopsBeforeJ5"), true);
        assertBoolean(controls.path("statisticsAndIncidentsSuppressedBeforeEligibleStatus"), true);
        assertBoolean(controls.path("lineupsSuppressedWhenPlayerStatisticsExplicitlyFalse"), true);
        assertThat(integer(controls.path("halftimeQuietPeriodMinutes"))).isEqualTo(15);
        assertThat(integer(controls.path("terminalStatistics404RecheckMaximum"))).isEqualTo(1);
        assertThat(integer(controls.path("suspendedJ4OnlyRecheckSeconds"))).isEqualTo(60);
        assertBoolean(controls.path("suspendedSuppressesAllJ5Families"), true);
        assertBoolean(controls.path("suspendedStatusReasonProjected"), true);

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
        assertThat(LiveSchedule.v9StrictGroupReservation(profile)).isEqualTo(Duration.ofMillis(6_000));
        assertThat(GroupedLiveAdmissionSimulationV9.SCENARIOS).isEqualTo(16);
        assertThat(GroupedLiveAdmissionSimulationV9.POST_EXCHANGE_FENCE).isEqualTo(Duration.ofMillis(500));
        assertThat(GroupedLiveAdmissionSimulationV9.MAXIMUM_DEPARTURES_PER_MINUTE).isEqualTo(45);
        assertThat(GroupedLiveAdmissionSimulationV9.MAXIMUM_DEPARTURES_PER_HOUR).isEqualTo(2_756);
        assertThat(GroupedLiveAdmissionSimulationV9.hasStrictMinuteDepartureBudget(QUALIFIED_MATCHES, profile)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV9.fits(QUALIFIED_MATCHES, profile)).isTrue();
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV9(profile)).isEqualTo(QUALIFIED_MATCHES);
        assertThat(GroupedLiveAdmissionSimulationV9.hasStrictMinuteDepartureBudget(QUALIFIED_MATCHES + 1, profile)).isFalse();
        assertThat(GroupedLiveAdmissionSimulationV9.fits(QUALIFIED_MATCHES + 1, profile)).isFalse();

        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(QUALIFIED_MATCHES);
        properties.getGroupedV9().setQualificationSha256(PROFILE_SHA256);
        QUALIFIED_ENVELOPES.forEach((endpoint, envelope) -> {
            properties.getGroupedV9().getEndpoints().get(endpoint).setRequestEnvelope(envelope.requestEnvelope());
            properties.getGroupedV9().getEndpoints().get(endpoint).setProcessingEnvelope(envelope.processingEnvelope());
        });
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.groupedAdmissionProfileV9()).isEqualTo(profile);

        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatCode(() -> policy.admitV9(QUALIFIED_MATCHES, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admitV9(QUALIFIED_MATCHES + 1, profile))
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
