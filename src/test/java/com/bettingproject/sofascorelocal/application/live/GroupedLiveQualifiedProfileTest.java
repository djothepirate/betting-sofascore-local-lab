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
import java.util.EnumMap;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Replays admission from committed evidence only; starts no browser, database or provider transport. */
class GroupedLiveQualifiedProfileTest {
    @Test
    void measuredSteadyEnvelopesRemainBoundToNativeEvidenceAndAdmitTenButNotEleven() throws Exception {
        Path profilePath = Path.of("docs/validation/WO058-GROUPED-LIVE-V4-PROFILE-20260908.json")
                .toAbsolutePath().normalize();
        byte[] profileBytes = Files.readAllBytes(profilePath);
        var mapper = JsonMapper.builder().build();
        JsonNode document = mapper.readTree(profileBytes);
        assertThat(document.path("policyVersion").asString()).isEqualTo("live-v4");
        assertThat(document.path("qualifiedCapacity").asInt()).isEqualTo(10);
        assertThat(document.path("envelopeScope").asString()).isEqualTo("STEADY_64_KIB_ONLY");
        assertThat(document.path("initialWave").path("separatelyMeasured").asBoolean()).isTrue();
        assertThat(document.path("initialWave").path("coveredBySteadyEnvelopes").isBoolean()).isTrue();
        assertThat(document.path("initialWave").path("coveredBySteadyEnvelopes").asBoolean()).isFalse();

        JsonNode reference = document.path("nativeEvidence");
        Path nativePath = profilePath.getParent().resolve(reference.path("path").asString()).normalize();
        assertThat(nativePath.getParent()).isEqualTo(profilePath.getParent());
        byte[] nativeBytes = Files.readAllBytes(nativePath);
        assertThat(Sha256.hex(nativeBytes)).as("exact native evidence bytes")
                .isEqualTo(reference.path("sha256").asString());
        JsonNode evidence = mapper.readTree(nativeBytes);
        assertThat(evidence.path("status").asString()).isEqualTo("PASSED");
        assertThat(evidence.path("policyVersion").asString()).isEqualTo("live-v4");
        assertThat(evidence.path("sustainedQualification").asBoolean()).isTrue();
        assertThat(decimal(evidence.path("steadyElapsedSeconds"))).isGreaterThanOrEqualTo(BigDecimal.valueOf(1800));
        for (String zeroCount : new String[] {"realProviderCalls", "missedCycles", "offScopeRequests"})
            assertThat(evidence.path(zeroCount).isIntegralNumber()).as("%s is recorded", zeroCount).isTrue();
        assertThat(evidence.path("realProviderCalls").asInt()).isZero();
        assertThat(evidence.path("operatorDatabaseUsed").isBoolean()).isTrue();
        assertThat(evidence.path("operatorDatabaseUsed").asBoolean()).isFalse();
        assertThat(evidence.path("missedCycles").asInt()).isZero();
        assertThat(evidence.path("offScopeRequests").asInt()).isZero();
        assertThat(evidence.path("matches").asInt()).isEqualTo(10);
        assertThat(evidence.path("bodyBytesPerResponse").asLong()).isEqualTo(65_536);
        assertThat(evidence.path("initialFirstResponsePerFamilyBytes").asLong()).isEqualTo(5_242_880);

        JsonNode declared = document.path("endpointEnvelopes");
        JsonNode measured = evidence.path("steadyMetrics");
        assertThat(declared.size()).isEqualTo(4);
        assertThat(measured.size()).isEqualTo(4);
        var envelopes = new EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : new SofascoreEndpointType[] {EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS}) {
            JsonNode budget = declared.path(endpoint.name());
            JsonNode family = measured.path(endpoint.name());
            long requestMillis = budget.path("requestMillis").asLong();
            long processingMillis = budget.path("processingMillis").asLong();
            coversMeasuredMaximum(endpoint, "request", requestMillis, family.path("requestSeconds"));
            coversMeasuredMaximum(endpoint, "processing including SQL", processingMillis,
                    family.path("processingIncludingSqlSeconds"));
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(requestMillis), Duration.ofMillis(processingMillis)));
        }

        // The production replay checks startup, kickoff and finalization independently
        // of the weighted steady bound. The evidence does not claim that these constant
        // envelopes also bound the separately measured initial 5 MiB response costs.
        var profile = new GroupedAdmissionProfile(envelopes, Sha256.hex(profileBytes));
        assertThat(LiveAdmissionPolicy.qualifiedCapacityV4(profile)).isEqualTo(10);
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(11);
        var policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatCode(() -> policy.admitV4(10, profile)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.admitV4(11, profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    private static void coversMeasuredMaximum(SofascoreEndpointType endpoint, String cost,
                                              long envelopeMillis, JsonNode measured) {
        assertThat(measured.path("count").asLong()).as("%s %s measurement count", endpoint, cost).isPositive();
        BigDecimal maximumMillis = decimal(measured.path("maximum")).movePointRight(3);
        assertThat(maximumMillis).as("%s %s maximum is measured", endpoint, cost).isPositive();
        assertThat(BigDecimal.valueOf(envelopeMillis)).as("%s %s envelope covers steady maximum", endpoint, cost)
                .isGreaterThanOrEqualTo(maximumMillis);
    }

    private static BigDecimal decimal(JsonNode value) {
        assertThat(value.isNumber()).as("numeric evidence value").isTrue();
        return value.decimalValue();
    }
}
