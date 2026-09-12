package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.EndpointEnvelope;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.GroupedAdmissionProfile;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_DETAILS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_INCIDENTS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_LINEUPS;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.EVENT_STATISTICS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Offline V10 admission coverage.  The V10 configuration deliberately adapts to
 * the frozen V9 scheduler-profile contract; this test starts no campaign or provider work.
 */
class LiveAdmissionPolicyV10Test {
    private static final String V10_QUALIFICATION_SHA256 = "a".repeat(64);
    private static final Map<SofascoreEndpointType, EndpointEnvelope> NOMINAL_ENVELOPES = Map.of(
            EVENT_DETAILS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(500)),
            EVENT_INCIDENTS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(400)),
            EVENT_STATISTICS, new EndpointEnvelope(Duration.ofMillis(350), Duration.ofMillis(400)),
            EVENT_LINEUPS, new EndpointEnvelope(Duration.ofMillis(300), Duration.ofMillis(450)));

    @Test
    void v10ConfigBuildsTheFrozenV9SchedulerProfileAndAdmitsEightButNotNine() {
        var properties = v10Properties();
        var profile = properties.groupedAdmissionProfileV10();
        var storage = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        var policy = new LiveAdmissionPolicyV10(properties, storage);

        // GroupedAdmissionProfile remains the immutable V9 scheduler contract.
        // V10 config can therefore be consumed only through its V10 admission path.
        assertThat(profile.policyVersion()).isEqualTo("live-v9");
        assertThat(profile.qualificationSha256()).isEqualTo(V10_QUALIFICATION_SHA256);
        assertThat(profile.endpointEnvelopes()).containsExactlyInAnyOrderEntriesOf(NOMINAL_ENVELOPES);
        assertThat(V10GroupedScheduleProfile.asV9SchedulerProfile(profile)).isSameAs(profile);
        assertThatIllegalArgumentException().isThrownBy(
                        () -> new GroupedAdmissionProfile(profile.endpointEnvelopes(), V10_QUALIFICATION_SHA256, "live-v10"))
                .withMessage("unknown grouped live policy");

        assertThat(LiveAdmissionPolicyV10.V10_MAXIMUM_SELECTION_SIZE).isEqualTo(8);
        assertThat(LiveAdmissionPolicyV10.estimatedLiveCallsPerMinute(8)).isEqualTo(32.0d);
        assertThat(LiveAdmissionPolicyV10.V10_MAXIMUM_CALLS_PER_MINUTE).isEqualTo(35);
        assertThat(LiveAdmissionPolicyV10.V10_MAXIMUM_CALLS_PER_HOUR).isEqualTo(2_100);
        assertThat(GroupedLiveAdmissionSimulationV10.MAXIMUM_DEPARTURES_PER_MINUTE).isEqualTo(35);
        assertThat(GroupedLiveAdmissionSimulationV10.MAXIMUM_DEPARTURES_PER_HOUR).isEqualTo(2_100);
        assertThat(ProviderResilienceData.DepartureProfile.LIVE_V10.maximumDeparturesPerMinute()).isEqualTo(35);
        assertThat(ProviderResilienceData.DepartureProfile.LIVE_V10.maximumDeparturesPerHour()).isEqualTo(2_100);

        assertThat(LiveAdmissionPolicyV10.qualifiedCapacity(profile)).isEqualTo(8);
        assertThatCode(() -> policy.admit(8, profile)).doesNotThrowAnyException();
        assertThatIllegalArgumentException().isThrownBy(() -> policy.admit(9, profile))
                .withMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
    }

    @Test
    void eightTargetsWithTheNominalV10EnvelopesRemainInsideTheStrictMinuteReservation() {
        var profile = v10Properties().groupedAdmissionProfileV10();
        var reservation = V10GroupedScheduleProfile.strictGroupReservation(profile);

        assertThat(reservation).isEqualTo(Duration.ofMillis(6_000));
        assertThat(reservation.multipliedBy(8)).isEqualTo(Duration.ofSeconds(48));
        assertThat(reservation.multipliedBy(8)).isLessThanOrEqualTo(Duration.ofMinutes(1));
        assertThat(GroupedLiveAdmissionSimulationV10.hasStrictMinuteDepartureBudget(8, profile)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV10.fits(8, profile)).isTrue();
        assertThat(GroupedLiveAdmissionSimulationV10.hasStrictMinuteDepartureBudget(9, profile)).isFalse();
        assertThat(GroupedLiveAdmissionSimulationV10.fits(9, profile)).isFalse();
    }

    private static LiveCampaignProperties v10Properties() {
        var properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(8);
        properties.getGroupedV10().setQualificationSha256(V10_QUALIFICATION_SHA256);
        NOMINAL_ENVELOPES.forEach((endpoint, envelope) -> {
            var budget = properties.getGroupedV10().getEndpoints().get(endpoint);
            budget.setRequestEnvelope(envelope.requestEnvelope());
            budget.setProcessingEnvelope(envelope.processingEnvelope());
        });
        return properties;
    }
}
