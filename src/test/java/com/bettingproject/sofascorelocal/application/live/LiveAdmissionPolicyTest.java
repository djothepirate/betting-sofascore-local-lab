package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Capacity proofs use the full response envelope, never an observed average response size. */
class LiveAdmissionPolicyTest {
    private static final long GIB = 1024L * 1024 * 1024;
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Test
    void theConservativeProfileAcceptsOneMatchAndRefusesTwoOrTen() {
        LiveCampaignProperties properties = new LiveCampaignProperties();
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);

        assertThatCode(() -> policy.admit(1)).doesNotThrowAnyException();
        for (int matches : new int[] {2, 10}) {
            assertThatThrownBy(() -> policy.admit(matches)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        }
    }

    @Test
    void aQualificationReferenceDoesNotOverrideAnUnschedulableEnvelope() {
        LiveCampaignProperties properties = qualifiedTwo();
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);

        assertThatThrownBy(() -> policy.admit(2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        properties.setDuration(Duration.ofSeconds(10));
        assertThatThrownBy(() -> policy.admit(2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    @Test
    void aSimulatedQualifiedTwoMatchProfileMustFitAllFourFamiliesAndTheFence() {
        LiveCampaignProperties properties = qualifiedTwo();
        properties.setRequestEnvelope(Duration.ofMillis(3500));
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        // 8 exchanges * (3.5 s request + 1 s processing + 3 s fence) = exactly 60 s.
        assertThatCode(() -> policy.admit(2)).doesNotThrowAnyException();
        properties.setRequestEnvelope(Duration.ofMillis(3501));
        assertThatThrownBy(() -> policy.admit(2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
    }

    @Test
    void aFasterEnvelopeCannotBeSelectedWithoutItsQualificationReference() {
        LiveCampaignProperties properties = new LiveCampaignProperties();
        properties.setRequestEnvelope(Duration.ofSeconds(3));
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        assertThatThrownBy(() -> policy.admit(1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_CAPACITY_QUALIFICATION_REQUIRED");
    }

    @Test
    void theRawVolumeEnvelopeReservesFiveMiBForEveryPossibleCall() {
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(new LiveCampaignProperties(), () -> Long.MAX_VALUE);
        assertThat(policy.maximumBytes(1)).isEqualTo(1000L * FIVE_MIB);
        assertThat(policy.maximumBytes(2)).isEqualTo(2000L * FIVE_MIB);
        assertThat(policy.maximumBytes(3)).isEqualTo(3000L * FIVE_MIB);
    }

    @Test
    void oneByteBelowTheRemainingEnvelopeAndFixedDiskReserveRefusesAdmission() {
        LiveCampaignProperties properties = new LiveCampaignProperties();
        long required = 2 * 1000L * FIVE_MIB + GIB;
        AtomicLong available = new AtomicLong(required - 1);
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(properties, available::get);
        assertThatThrownBy(() -> policy.admit(1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
        available.set(required);
        assertThatCode(() -> policy.admit(1)).doesNotThrowAnyException();

        available.set(GIB);
        assertThatCode(() -> policy.requireStorage(0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireStorage(1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
        available.set(GIB - 1);
        assertThatThrownBy(() -> policy.requireStorage(0)).isInstanceOf(IllegalStateException.class)
                .hasMessage("LIVE_STORAGE_CAPACITY_REFUSED");
    }

    @Test
    void theFourHourBoundaryIsAcceptedButZeroNegativeAndLongerDurationsAreRejected() {
        LiveCampaignProperties properties = new LiveCampaignProperties();
        LiveAdmissionPolicy policy = new LiveAdmissionPolicy(properties, () -> Long.MAX_VALUE);
        properties.setDuration(Duration.ofHours(4));
        assertThatCode(() -> policy.admit(1)).doesNotThrowAnyException();
        for (Duration invalid : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1), Duration.ofHours(4).plusSeconds(1)}) {
            properties.setDuration(invalid);
            assertThatThrownBy(() -> policy.admit(1)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("LIVE_POLICY_INVALID");
        }
    }

    private static LiveCampaignProperties qualifiedTwo() {
        LiveCampaignProperties properties = new LiveCampaignProperties();
        properties.setQualifiedMatchCapacity(2);
        properties.setQualificationSha256("a".repeat(64));
        return properties;
    }
}
