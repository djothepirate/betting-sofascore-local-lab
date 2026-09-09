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
import java.util.Map;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exact committed V7 evidence plus production scheduler replay; no browser, database or network. */
class GroupedLiveAdmissionPolicyV7EvidenceTest {
    private static final long SECOND = 1_000_000_000L;
    private static final int MEASURED_MATCHES = 3;
    private static final List<SofascoreEndpointType> FAMILIES =
            List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
    private static final Map<SofascoreEndpointType, EndpointEnvelope> EXPECTED_ENVELOPES = Map.of(
            EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)),
            EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(550)),
            EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(500)),
            EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(500)));

    @Test
    void committedV7EvidenceBindsTheThreeMatchProfileWithoutEnablingTransport() throws Exception {
        Path profilePath = Path.of("docs/validation/WO058-GROUPED-LIVE-V7-PROFILE-20260909.json")
                .toAbsolutePath().normalize();
        assertThat(profilePath).as("the v7 profile is required; a v6 campaign never authorizes it").isRegularFile();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        var mapper = JsonMapper.builder().build();
        JsonNode profileDocument = mapper.readTree(profileBytes);

        assertThat(profileDocument.path("schema").asString()).isEqualTo("wo058-grouped-live-capacity-evidence-v2");
        assertThat(profileDocument.path("status").asString()).isEqualTo("QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE");
        assertThat(profileDocument.path("policyVersion").asString()).isEqualTo("live-v7");
        assertThat(integer(profileDocument.path("flywayVersion"))).isEqualTo(47);
        assertThat(integer(profileDocument.path("qualifiedCapacity"))).isEqualTo(MEASURED_MATCHES);
        assertThat(profileDocument.path("envelopeScope").asString()).isEqualTo("STEADY_64_KIB_ONLY");
        assertBoolean(profileDocument.path("initialWave").path("separatelyMeasured"), true);
        assertBoolean(profileDocument.path("initialWave").path("coveredBySteadyEnvelopes"), false);
        assertThat(integer(profileDocument.path("initialWave").path("pairs"))).isEqualTo(12);
        assertThat(profileDocument.path("admission").path("profileAppliedAutomatically").asBoolean()).isFalse();

        JsonNode cadence = profileDocument.path("cadence");
        assertThat(integer(cadence.path("criticalSeconds"))).isEqualTo(60);
        assertThat(integer(cadence.path("lineupSeconds"))).isEqualTo(60);
        assertThat(integer(cadence.path("interGroupDelayMillis"))).isEqualTo(1_000);
        assertThat(integer(cadence.path("minimumPostCompletionDelayMillis"))).isEqualTo(2_000);
        assertThat(integer(cadence.path("maximumDeparturesPer60Seconds"))).isEqualTo(25);
        assertThat(integer(cadence.path("maximumDeparturesPerHour"))).isEqualTo(1_000);

        Path nativePath = profilePath.getParent().resolve(profileDocument.path("nativeEvidence").path("path").asString()).normalize();
        assertThat(nativePath).as("the native sustained loopback evidence is mandatory").isRegularFile();
        byte[] nativeBytes = Files.readAllBytes(nativePath);
        assertThat(Sha256.hex(nativeBytes)).isEqualTo(profileDocument.path("nativeEvidence").path("sha256").asString());
        Path builderPath = Path.of("docs/validation/v7-profile-builder/V7MeasuredProfile.java").toAbsolutePath().normalize();
        assertThat(builderPath).as("the profile builder source remains auditable").isRegularFile();
        assertThat(Sha256.hex(Files.readAllBytes(builderPath))).isEqualTo(profileDocument.path("profileBuilderSourceSha256").asString());

        JsonNode nativeDocument = mapper.readTree(nativeBytes);
        assertThat(nativeDocument.path("status").asString()).isEqualTo("PASSED");
        assertThat(nativeDocument.path("scope").asString()).isEqualTo("EXPERIMENTAL LOCAL_ONLY SYNTHETIC_LOOPBACK");
        assertThat(nativeDocument.path("policyVersion").asString()).isEqualTo("live-v7");
        assertThat(integer(nativeDocument.path("matches"))).isEqualTo(MEASURED_MATCHES);
        assertThat(integer(nativeDocument.path("criticalIntervalSeconds"))).isEqualTo(60);
        assertThat(integer(nativeDocument.path("lineupsIntervalSeconds"))).isEqualTo(60);
        assertThat(integer(nativeDocument.path("effectiveRequestTimeoutMillis"))).isEqualTo(30_000);
        assertThat(decimal(nativeDocument.path("steadyElapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(1_800));
        assertThat(decimal(nativeDocument.path("elapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(2_100));
        for (String flag : List.of("sustainedQualification", "productionDockerDfProbePerRequest",
                "productionPersistentResilience", "productionTransportDiagnosticPersistence", "workerAndChildrenClosed")) {
            assertBoolean(nativeDocument.path(flag), true);
        }
        assertBoolean(nativeDocument.path("operatorDatabaseUsed"), false);
        for (String count : List.of("realProviderCalls", "offScopeRequests", "missedCycles")) {
            assertThat(integer(nativeDocument.path(count))).as(count).isZero();
        }

        List<Sample> samples = new ArrayList<>();
        for (JsonNode value : nativeDocument.path("samples")) {
            samples.add(new Sample(integer(value.path("providerEventId")),
                    SofascoreEndpointType.valueOf(value.path("endpoint").asString()),
                    integer(value.path("bodyBytes")), integer(value.path("receivedNanos")),
                    integer(value.path("requestNanos")), integer(value.path("processingNanos"))));
        }
        assertThat(samples).hasSize(420);
        List<Sample> steady = samples.stream().filter(sample -> sample.receivedNanos() >= 300 * SECOND).toList();
        assertThat(steady).hasSize(360).allSatisfy(sample -> assertThat(sample.bodyBytes()).isEqualTo(65_536));
        assertThat(samples.stream().filter(sample -> sample.bodyBytes() == 5_242_880).toList())
                .hasSize(12)
                .allSatisfy(sample -> assertThat(sample.receivedNanos()).isLessThan(300 * SECOND));

        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        JsonNode declared = profileDocument.path("endpointEnvelopes");
        for (SofascoreEndpointType endpoint : FAMILIES) {
            List<Sample> family = steady.stream().filter(sample -> sample.endpoint() == endpoint).toList();
            assertThat(family).hasSize(90);
            EndpointEnvelope expected = EXPECTED_ENVELOPES.get(endpoint);
            long requestMillis = integer(declared.path(endpoint.name()).path("requestMillis"));
            long processingMillis = integer(declared.path(endpoint.name()).path("processingMillis"));
            assertThat(requestMillis).isEqualTo(expected.requestEnvelope().toMillis());
            assertThat(processingMillis).isEqualTo(expected.processingEnvelope().toMillis());
            assertThat(requestMillis * 1_000_000L).isGreaterThanOrEqualTo(family.stream().mapToLong(Sample::requestNanos).max().orElseThrow());
            assertThat(processingMillis * 1_000_000L).isGreaterThanOrEqualTo(family.stream().mapToLong(Sample::processingNanos).max().orElseThrow());
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis)));
        }

        var profile = new GroupedAdmissionProfile(envelopes, Sha256.hex(profileBytes), "live-v7");
        assertThat(profile.qualificationSha256()).isEqualTo("7b339ee1664abb744639164aa2e7d69388de22ac49db51edadb3b9b33f619335");
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV7(profile)).isEqualTo(MEASURED_MATCHES);
        assertThat(GroupedLiveAdmissionSimulationV7.fits(MEASURED_MATCHES, profile)).isTrue();

        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(MEASURED_MATCHES);
        properties.getGroupedV7().setQualificationSha256(profile.qualificationSha256());
        EXPECTED_ENVELOPES.forEach((endpoint, envelope) -> {
            properties.getGroupedV7().getEndpoints().get(endpoint).setRequestEnvelope(envelope.requestEnvelope());
            properties.getGroupedV7().getEndpoints().get(endpoint).setProcessingEnvelope(envelope.processingEnvelope());
        });
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.groupedAdmissionProfileV7()).isEqualTo(profile);
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatCode(() -> policy.admitV7(MEASURED_MATCHES, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admitV7(MEASURED_MATCHES + 1, profile))
                .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
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

    private record Sample(long eventId, SofascoreEndpointType endpoint, long bodyBytes,
                          long receivedNanos, long requestNanos, long processingNanos) { }
}
