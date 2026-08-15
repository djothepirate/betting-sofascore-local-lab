package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J4EventDetailsQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1State;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J4RealPhase1ControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000004");

    @Test
    void requiresExactConfirmationThenLocksAfterTheTwoAuthorizedEvents() {
        var control = availableControl();

        var prepared = control.prepare();

        assertThat(prepared.state()).isEqualTo(J4RealPhase1State.AWAITING_CONFIRMATION);
        assertThat(prepared.confirmationPhrase())
                .isEqualTo("CONFIRMER EVENT_DETAILS 16386245 16421052 000042");
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID,
                "wrong",
                true))
                .isInstanceOf(J4RealPhase1ControlException.class)
                .extracting(exception -> ((J4RealPhase1ControlException) exception).error())
                .isEqualTo(J4RealPhase1ControlError.CONFIRMATION_TEXT_MISMATCH);

        var claim = control.confirmAndClaim(
                REQUEST_ID,
                prepared.confirmationPhrase(),
                true);
        assertThat(claim.providerOrigin().toString())
                .isEqualTo(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        assertThat(control.executionMayContinue(REQUEST_ID)).isTrue();

        control.recordEventCompleted(REQUEST_ID, 16386245L);
        control.recordEventCompleted(REQUEST_ID, 16421052L);
        var completed = control.complete(REQUEST_ID);

        assertThat(completed.state()).isEqualTo(J4RealPhase1State.COMPLETED_LOCKED);
        assertThat(completed.locked()).isTrue();
        assertThat(completed.completedEvents()).isEqualTo(2);
        assertThat(completed.terminalCode()).isEqualTo("COMPLETED");
        assertThat(control.executionMayContinue(REQUEST_ID)).isFalse();
    }

    @Test
    void rejectsPreparationWhileConfigurationIsBlocked() {
        var control = new J4RealPhase1ControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> REQUEST_ID,
                () -> 42,
                () -> J4EventDetailsQualificationSnapshot.blocked(List.of(
                        "J4_EVENT_DETAILS_QUALIFICATION_DISABLED")));

        assertThatThrownBy(control::prepare)
                .isInstanceOf(J4RealPhase1ControlException.class)
                .extracting(exception -> ((J4RealPhase1ControlException) exception).error())
                .isEqualTo(J4RealPhase1ControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        assertThat(control.snapshot().state()).isEqualTo(J4RealPhase1State.LOCKED);
    }

    @Test
    void globalStopImmediatelyMakesAnExecutingCampaignTerminal() {
        var control = availableControl();
        var prepared = control.prepare();
        control.confirmAndClaim(REQUEST_ID, prepared.confirmationPhrase(), true);

        var stopped = control.stop();

        assertThat(stopped.state()).isEqualTo(J4RealPhase1State.STOPPED_LOCKED);
        assertThat(stopped.terminalCode()).isEqualTo("OPERATOR_STOP");
        assertThat(control.executionMayContinue(REQUEST_ID)).isFalse();
    }

    private static J4RealPhase1ControlService availableControl() {
        return new J4RealPhase1ControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> REQUEST_ID,
                () -> 42,
                () -> J4EventDetailsQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)));
    }
}
