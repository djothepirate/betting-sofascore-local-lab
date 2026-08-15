package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlState;
import com.bettingproject.sofascorelocal.domain.provider.J5RealQualificationSnapshot;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J5RealControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T14:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000006");
    private static final long EVENT_ID = 16391135L;
    private static final UUID CANONICAL_ID = CanonicalEventIdentity.sofascore(EVENT_ID).value();

    @Test
    void bindsOneConfirmationToOneCanonicalIdentityAndLocksAfterThreeOrderedEndpoints() {
        J5RealControlService control = availableControl();

        var prepared = control.prepare(CANONICAL_ID, EVENT_ID);
        assertThat(prepared.state()).isEqualTo(J5RealControlState.AWAITING_CONFIRMATION);
        assertThat(prepared.confirmationPhrase()).isEqualTo(
                "CONFIRMER J5 REAL 16391135 STATISTICS INCIDENTS LINEUPS 000042");
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), false))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.ACKNOWLEDGEMENT_REQUIRED);

        var claim = control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true);
        assertThat(claim.canonicalEventId()).isEqualTo(CANONICAL_ID);
        J5RealControlService.ORDERED_ENDPOINTS.forEach(
                endpoint -> control.recordEndpointCompleted(REQUEST_ID, endpoint));
        var terminal = control.complete(REQUEST_ID);

        assertThat(terminal.state()).isEqualTo(J5RealControlState.COMPLETED_LOCKED);
        assertThat(terminal.completedEndpoints())
                .containsExactlyElementsOf(J5RealControlService.ORDERED_ENDPOINTS);
        assertThatThrownBy(() -> control.prepare(CANONICAL_ID, EVENT_ID))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.TERMINAL_LOCK_REQUIRES_RESTART);
    }

    @Test
    void rejectsIdentityMismatchAndOutOfOrderCompletion() {
        J5RealControlService control = availableControl();
        assertThatThrownBy(() -> control.prepare(
                CanonicalEventIdentity.sofascore(EVENT_ID + 1).value(), EVENT_ID))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.EVENT_ID_MISMATCH);

        var prepared = control.prepare(CANONICAL_ID, EVENT_ID);
        control.confirmAndClaim(REQUEST_ID, prepared.confirmationPhrase(), true);
        assertThatThrownBy(() -> control.recordEndpointCompleted(
                REQUEST_ID, J5RealControlService.ORDERED_ENDPOINTS.get(1)))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.ENDPOINT_ORDER_INVALID);
    }

    @Test
    void preparationRemainsPurelyBlockedWhenQualificationIsUnavailable() {
        var control = new J5RealControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> REQUEST_ID,
                () -> 42,
                () -> J5RealQualificationSnapshot.blocked(List.of(
                        "J5_EVENT_DATA_QUALIFICATION_DISABLED")));

        assertThat(control.snapshot().preparationAllowed()).isFalse();
        assertThatThrownBy(() -> control.prepare(CANONICAL_ID, EVENT_ID))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
    }

    @Test
    void expiresAtTheFiveMinuteBoundaryAndKeepsTheProcessTerminallyLocked() {
        MutableClock clock = new MutableClock(NOW);
        J5RealControlService control = new J5RealControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> J5RealQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)));
        var prepared = control.prepare(CANONICAL_ID, EVENT_ID);

        clock.advance(J5RealControlService.CONFIRMATION_TTL);

        assertThat(control.snapshot().state())
                .isEqualTo(J5RealControlState.EXPIRED_LOCKED);
        assertThat(control.snapshot().confirmationPhrase()).isNull();
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.CONFIRMATION_EXPIRED);
        assertThatThrownBy(() -> control.prepare(CANONICAL_ID, EVENT_ID))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.TERMINAL_LOCK_REQUIRES_RESTART);
    }

    @Test
    void rejectsWrongRequestAndPhraseThenHonorsTheGlobalStop() {
        J5RealControlService control = availableControl();
        var prepared = control.prepare(CANONICAL_ID, EVENT_ID);

        assertThatThrownBy(() -> control.confirmAndClaim(
                UUID.fromString("60000000-0000-0000-0000-000000000099"),
                prepared.confirmationPhrase(),
                true))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.REQUEST_ID_MISMATCH);
        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, "not the exact phrase", true))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.CONFIRMATION_TEXT_MISMATCH);

        assertThat(control.stop().state()).isEqualTo(J5RealControlState.STOPPED_LOCKED);
        assertThatThrownBy(() -> control.prepare(CANONICAL_ID, EVENT_ID))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.TERMINAL_LOCK_REQUIRES_RESTART);
    }

    @Test
    void locksWithoutAClaimWhenTheConfigurationIsRevokedAfterPreparation() {
        AtomicReference<J5RealQualificationSnapshot> qualification = new AtomicReference<>(
                J5RealQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)));
        J5RealControlService control = new J5RealControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> REQUEST_ID,
                () -> 42,
                qualification::get);
        var prepared = control.prepare(CANONICAL_ID, EVENT_ID);
        qualification.set(J5RealQualificationSnapshot.blocked(
                List.of("J5_EVENT_DATA_QUALIFICATION_DISABLED")));

        assertThatThrownBy(() -> control.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true))
                .isInstanceOf(J5RealControlException.class)
                .extracting(exception -> ((J5RealControlException) exception).error())
                .isEqualTo(J5RealControlError.PROVIDER_TRANSPORT_UNAVAILABLE);
        assertThat(control.snapshot().state()).isEqualTo(J5RealControlState.FAILED_LOCKED);
        assertThat(control.snapshot().terminalCode())
                .isEqualTo("PROVIDER_TRANSPORT_UNAVAILABLE");
    }

    private static J5RealControlService availableControl() {
        return new J5RealControlService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> REQUEST_ID,
                () -> 42,
                () -> J5RealQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)));
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
