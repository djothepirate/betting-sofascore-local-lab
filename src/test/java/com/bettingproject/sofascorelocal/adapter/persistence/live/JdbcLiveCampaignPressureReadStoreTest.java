package com.bettingproject.sofascorelocal.adapter.persistence.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcLiveCampaignPressureReadStoreTest {
    private static final Instant T0 = Instant.parse("2030-09-09T12:00:00Z");

    @Test
    void keepsHistoricalAbsenceDistinctAndRetainsEveryLiveFamily() {
        var pressure = JdbcLiveCampaignPressureReadStore.summarize(List.of());

        assertThat(pressure.observedDepartures()).isZero();
        assertThat(pressure.firstObservedDepartureAt()).isNull();
        assertThat(pressure.lastObservedDepartureAt()).isNull();
        assertThat(pressure.oneMinutePeak().observedDepartures()).isZero();
        assertThat(pressure.oneMinutePeak().windowEndAt()).isNull();
        assertThat(pressure.fiveMinutePeak().observedDepartures()).isZero();
        assertThat(pressure.families()).allSatisfy(family -> assertThat(family.observedDepartures()).isZero());
    }

    @Test
    void calculatesStrictlyExclusiveRollingWindowBoundariesFromObservedStartsOnly() {
        var pressure = JdbcLiveCampaignPressureReadStore.summarize(List.of(
                new JdbcLiveCampaignPressureReadStore.ObservedDeparture(SofascoreEndpointType.EVENT_DETAILS, T0.plusSeconds(10)),
                new JdbcLiveCampaignPressureReadStore.ObservedDeparture(SofascoreEndpointType.EVENT_INCIDENTS, T0.plusSeconds(20)),
                new JdbcLiveCampaignPressureReadStore.ObservedDeparture(SofascoreEndpointType.EVENT_STATISTICS, T0.plusSeconds(30)),
                new JdbcLiveCampaignPressureReadStore.ObservedDeparture(SofascoreEndpointType.EVENT_LINEUPS, T0.plusSeconds(69)),
                new JdbcLiveCampaignPressureReadStore.ObservedDeparture(SofascoreEndpointType.EVENT_DETAILS, T0.plusSeconds(70))));

        assertThat(pressure.observedDepartures()).isEqualTo(5);
        assertThat(pressure.firstObservedDepartureAt()).isEqualTo(T0.plusSeconds(10));
        assertThat(pressure.lastObservedDepartureAt()).isEqualTo(T0.plusSeconds(70));
        // At T0+70, the T0+10 observation is exactly sixty seconds old and has left (t - 60 s, t].
        assertThat(pressure.oneMinutePeak().observedDepartures()).isEqualTo(4);
        assertThat(pressure.oneMinutePeak().windowEndAt()).isEqualTo(T0.plusSeconds(69));
        assertThat(pressure.fiveMinutePeak().observedDepartures()).isEqualTo(5);
        assertThat(pressure.families()).extracting(family -> family.endpoint().name(), family -> family.observedDepartures())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("EVENT_DETAILS", 2),
                        org.assertj.core.groups.Tuple.tuple("EVENT_STATISTICS", 1),
                        org.assertj.core.groups.Tuple.tuple("EVENT_INCIDENTS", 1),
                        org.assertj.core.groups.Tuple.tuple("EVENT_LINEUPS", 1));
    }
}
