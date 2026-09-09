package com.bettingproject.sofascorelocal.domain.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LiveGroupedDataTest {
    private static Map<SofascoreEndpointType,EndpointEnvelope> envelopes() {
        Map<SofascoreEndpointType,EndpointEnvelope> result=new EnumMap<>(SofascoreEndpointType.class);
        for(SofascoreEndpointType endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_LINEUPS))
            result.put(endpoint,new EndpointEnvelope(Duration.ofMillis(500),Duration.ofMillis(100)));
        return result;
    }
    @Test void qualifiedGroupedProfileCopiesAllFourEnvelopesAndRetainsExactPolicy() {
        var source=envelopes();var profile=new GroupedAdmissionProfile(source,"a".repeat(64));source.clear();
        assertThat(profile.endpointEnvelopes()).hasSize(4);
        assertThatThrownBy(()->profile.endpointEnvelopes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(profile.envelope(SofascoreEndpointType.EVENT_DETAILS).exchangeEnvelope()).isEqualTo(Duration.ofMillis(600));
        assertThat(profile.criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(profile.lineupInterval()).isEqualTo(Duration.ofSeconds(300));
        assertThat(profile.intraGroupDelay()).isEqualTo(Duration.ZERO);
        assertThat(profile.interGroupDelay()).isEqualTo(Duration.ofSeconds(3));
        assertThat(profile.maximumUtilization()).isEqualTo(0.9);
    }
    @Test void previousAdmissionProofDoesNotGrantGroupedQualification() {
        assertThat(new AdmissionProfile(Duration.ofSeconds(1),Duration.ofSeconds(1),"a".repeat(64)).groupedProfile()).isNull();
        assertThatThrownBy(()->new GroupedAdmissionProfile(envelopes(),"")).isInstanceOf(IllegalArgumentException.class);
        var incomplete=envelopes();incomplete.remove(SofascoreEndpointType.EVENT_LINEUPS);
        assertThatThrownBy(()->new GroupedAdmissionProfile(incomplete,"a".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void policyVersionIsExplicitAndHistoricalConstructorCannotGrantNewerTiming() {
        var historical=new GroupedAdmissionProfile(envelopes(),"a".repeat(64));
        var v5=new GroupedAdmissionProfile(envelopes(),"a".repeat(64),"live-v5");
        assertThat(historical.policyVersion()).isEqualTo("live-v4");
        assertThat(v5).isNotEqualTo(historical);
        assertThat(v5.criticalInterval()).isEqualTo(Duration.ofSeconds(100));
        assertThat(v5.lineupInterval()).isEqualTo(Duration.ofSeconds(300));
        assertThat(v5.interGroupDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(v5.intraGroupDelay()).isZero();
        var v6 = new GroupedAdmissionProfile(envelopes(),"a".repeat(64),"live-v6");
        assertThat(v6).isNotEqualTo(v5);
        assertThat(v6.criticalInterval()).isEqualTo(v5.criticalInterval());
        assertThat(v6.minimumRequestStartInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(v5.minimumRequestStartInterval()).isZero();
        var v7 = new GroupedAdmissionProfile(envelopes(),"a".repeat(64),"live-v7");
        assertThat(v7).isNotEqualTo(v6);
        assertThat(v7.criticalInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(v7.lineupInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(v7.minimumRequestStartInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(v7.interGroupDelay()).isEqualTo(Duration.ofSeconds(1));
    }
    @Test void groupedCostsAndFamilySchedulesRejectInvalidBounds() {
        assertThatThrownBy(()->new EndpointEnvelope(Duration.ZERO,Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EndpointEnvelope(Duration.ofSeconds(11),Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,null,0,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,null,60,-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
