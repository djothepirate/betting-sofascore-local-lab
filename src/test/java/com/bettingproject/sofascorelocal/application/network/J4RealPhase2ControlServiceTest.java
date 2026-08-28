package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.J4EventDetailsQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase2State;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J4RealPhase2ControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final UUID FIRST_REQUEST_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000004");
    private static final UUID SECOND_REQUEST_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000005");
    private static final long EVENT_ID = 17000001L;
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(EVENT_ID);

    @Test
    void bindsConfirmationToOneEventAndAllowsASeparateManualRefreshAfterCompletion() {
        AtomicInteger sequence = new AtomicInteger();
        var control = new J4RealPhase2ControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> sequence.getAndIncrement() == 0 ? FIRST_REQUEST_ID : SECOND_REQUEST_ID,
                () -> 42,
                J4RealPhase2ControlServiceTest::available);

        var first = control.prepare(IDENTITY);
        assertThat(first.state()).isEqualTo(J4RealPhase2State.AWAITING_CONFIRMATION);
        assertThat(first.confirmationPhrase())
                .isEqualTo("CONFIRMER EVENT_DETAILS " + IDENTITY.value()
                        + " 17000001 000042");
        assertThatThrownBy(() -> control.confirmAndClaim(
                FIRST_REQUEST_ID,
                "CONFIRMER EVENT_DETAILS " + IDENTITY.value() + " 17000002 000042",
                true))
                .isInstanceOf(J4RealPhase2ControlException.class)
                .extracting(exception -> ((J4RealPhase2ControlException) exception).error())
                .isEqualTo(J4RealPhase2ControlError.CONFIRMATION_TEXT_MISMATCH);

        var firstClaim = control.confirmAndClaim(
                FIRST_REQUEST_ID,
                first.confirmationPhrase(),
                true);
        assertThat(firstClaim.eventId()).isEqualTo(EVENT_ID);
        assertThat(firstClaim.canonicalEventId()).isEqualTo(IDENTITY.value());
        control.recordEventCompleted(FIRST_REQUEST_ID, EVENT_ID);
        assertThat(control.complete(FIRST_REQUEST_ID).state())
                .isEqualTo(J4RealPhase2State.COMPLETED_LOCKED);

        var second = control.prepare(IDENTITY);
        assertThat(second.requestId()).isEqualTo(SECOND_REQUEST_ID);
        assertThat(second.eventId()).isEqualTo(EVENT_ID);
        assertThat(second.state()).isEqualTo(J4RealPhase2State.AWAITING_CONFIRMATION);
        assertThat(second.eventCompleted()).isFalse();
    }

    @Test
    void acceptsAFixedPhaseOneIdForRefreshAndStopsAnExecutingCampaign() {
        var fixedControl = availableControl();
        var fixedRefresh = fixedControl.prepare(CanonicalEventIdentity.sofascore(16386245L));
        assertThat(fixedRefresh.eventId()).isEqualTo(16386245L);
        fixedControl.stop();

        var control = availableControl();
        var prepared = control.prepare(IDENTITY);
        control.confirmAndClaim(FIRST_REQUEST_ID, prepared.confirmationPhrase(), true);
        List<UUID> signaled = new ArrayList<>();
        var stopped = control.stop(signaled::add);

        assertThat(stopped.state()).isEqualTo(J4RealPhase2State.STOPPED_LOCKED);
        assertThat(stopped.terminalCode()).isEqualTo("OPERATOR_STOP");
        assertThat(control.executionMayContinue(FIRST_REQUEST_ID)).isFalse();
        assertThat(stopped.preparationAllowed()).isFalse();
        assertThat(signaled).containsExactly(FIRST_REQUEST_ID);
        assertThatThrownBy(() -> control.prepare(IDENTITY))
                .isInstanceOf(J4RealPhase2ControlException.class)
                .extracting(exception -> ((J4RealPhase2ControlException) exception).error())
                .isEqualTo(J4RealPhase2ControlError.TERMINAL_LOCK_REQUIRES_RESTART);
    }

    @Test
    void rejectsPreparationWhileTheDedicatedOptInIsBlocked() {
        var control = new J4RealPhase2ControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> FIRST_REQUEST_ID,
                () -> 42,
                () -> J4EventDetailsQualificationSnapshot.blocked(List.of(
                        "J4_EVENT_DETAILS_PHASE_2_DISABLED")));

        assertThatThrownBy(() -> control.prepare(IDENTITY))
                .isInstanceOf(J4RealPhase2ControlException.class)
                .extracting(exception -> ((J4RealPhase2ControlException) exception).error())
                .isEqualTo(J4RealPhase2ControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
    }

    @Test
    void completesAConfirmed404AsUnavailable() {
        var control = availableControl();
        var prepared = control.prepare(IDENTITY);
        control.confirmAndClaim(FIRST_REQUEST_ID, prepared.confirmationPhrase(), true);
        control.recordEventCompleted(FIRST_REQUEST_ID, EVENT_ID);

        var completed = control.completeUnavailable(FIRST_REQUEST_ID);

        assertThat(completed.state()).isEqualTo(J4RealPhase2State.COMPLETED_LOCKED);
        assertThat(completed.terminalCode()).isEqualTo("COMPLETED_UNAVAILABLE");
        assertThat(completed.eventCompleted()).isTrue();
    }

    private static J4RealPhase2ControlService availableControl() {
        return new J4RealPhase2ControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> FIRST_REQUEST_ID,
                () -> 42,
                J4RealPhase2ControlServiceTest::available);
    }

    private static J4EventDetailsQualificationSnapshot available() {
        return J4EventDetailsQualificationSnapshot.available(
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN));
    }
}
