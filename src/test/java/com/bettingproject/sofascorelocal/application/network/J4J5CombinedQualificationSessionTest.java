package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1State;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2State;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlState;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class J4J5CombinedQualificationSessionTest {

    private static final long EVENT_ID = 16_251_993L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T11:05:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void aGlobalJ4StopDoesNotPreventPreparingJ5InTheSameProcess() throws Exception {
        SofascoreProperties properties = combinedProperties();
        var playwright = ProviderPlaywrightPolicyTestSupport.configured(
                temporaryDirectory, "combined-j4-worker.jar");
        var j4PhaseOnePolicy = new J4EventDetailsQualificationPolicy(properties, playwright);
        var j4Policy = new J4EventDetailsPhase2QualificationPolicy(properties, playwright);
        var j5Policy = new J5RealQualificationPolicy(properties);
        var j4PhaseOneControl = new J4RealPhase1ControlService(
                CLOCK,
                () -> UUID.fromString("30000000-0000-0000-0000-000000000001"),
                () -> 112233,
                j4PhaseOnePolicy::snapshot);
        var j4Control = new J4RealPhase2ControlService(
                CLOCK,
                () -> UUID.fromString("40000000-0000-0000-0000-000000000001"),
                () -> 123456,
                j4Policy::snapshot);
        var j5Control = new J5RealControlService(
                CLOCK,
                () -> UUID.fromString("50000000-0000-0000-0000-000000000001"),
                () -> 654321,
                j5Policy::snapshot);

        assertThat(j4Policy.snapshot().available()).isTrue();
        assertThat(j5Policy.snapshot().available()).isTrue();
        assertThat(j4PhaseOneControl.stop().state())
                .isEqualTo(J4RealPhase1State.STOPPED_LOCKED);
        assertThat(j4Control.stop().state()).isEqualTo(J4RealPhase2State.STOPPED_LOCKED);

        var j5Prepared = j5Control.prepare(
                CanonicalEventIdentity.sofascore(EVENT_ID).value(), EVENT_ID);

        assertThat(j5Prepared.state()).isEqualTo(J5RealControlState.AWAITING_CONFIRMATION);
        assertThat(j5Prepared.eventId()).isEqualTo(EVENT_ID);
        assertThat(j4PhaseOneControl.snapshot().state())
                .isEqualTo(J4RealPhase1State.STOPPED_LOCKED);
        assertThat(j4Control.snapshot().state()).isEqualTo(J4RealPhase2State.STOPPED_LOCKED);
    }

    private static SofascoreProperties combinedProperties() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ4EventDetailsQualificationEnabled(true);
        properties.setJ4EventDetailsPhase2Enabled(true);
        properties.setJ5EventDataQualificationEnabled(true);
        properties.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        properties.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.EVENT_DETAILS,
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_LINEUPS));
        return properties;
    }
}
