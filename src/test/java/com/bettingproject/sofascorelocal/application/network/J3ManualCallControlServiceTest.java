package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3ManualCallControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-12");
    private static final UUID REQUEST_ID = UUID.fromString(
            "d476ba08-abaa-451a-b4cc-cf077f3d6833");

    @Test
    void startsWithTheGlobalStopAndEveryProviderBarrierActive() {
        var service = service(new MutableClock(NOW));

        var snapshot = service.snapshot();

        assertThat(snapshot.globalStopActive()).isTrue();
        assertThat(snapshot.circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(snapshot.circuitReason()).isEqualTo(J3CircuitReason.STARTUP_LOCK);
        assertThat(snapshot.operatorActivated()).isFalse();
        assertThat(snapshot.intent()).isNull();
        assertThat(snapshot.providerTransportAvailable()).isFalse();
        assertThat(snapshot.providerBlockers()).containsExactly(
                "REAL_ENDPOINT_URI_ABSENT",
                "REAL_CALL_NOT_AUTHORIZED",
                "CONNECTOR_GATE_LOCKED",
                "CATALOG_NOT_CALLABLE",
                "LIVE_PROFILE_BLOCKED");
    }

    @Test
    void requiresRearmActivationPreparationAndExactAcknowledgedPhrase() {
        var service = service(new MutableClock(NOW));

        assertRejected(
                service::activateByOperator,
                J3ManualCallControlError.GLOBAL_STOP_ACTIVE);

        var rearmed = service.rearmAfterGlobalStop();
        assertThat(rearmed.globalStopActive()).isFalse();
        assertThat(rearmed.circuitState()).isEqualTo(J3CircuitState.LOCKED);

        var activated = service.activateByOperator();
        assertThat(activated.operatorActivated()).isTrue();

        var prepared = service.prepare(DATE);
        assertThat(prepared.intent().requestId()).isEqualTo(REQUEST_ID);
        assertThat(prepared.intent().requestKey())
                .isEqualTo("SCHEDULED_EVENTS|date=2026-08-12");
        assertThat(prepared.intent().state())
                .isEqualTo(J3ManualCallIntentState.AWAITING_CONFIRMATION);
        assertThat(prepared.intent().confirmationPhrase())
                .isEqualTo("CONFIRMER SCHEDULED_EVENTS 2026-08-12 000042");
        assertThat(prepared.intent().expiresAt())
                .isEqualTo(NOW.plus(J3ManualCallControlService.CONFIRMATION_TTL));

        assertRejected(
                () -> service.confirm(
                        REQUEST_ID,
                        prepared.intent().confirmationPhrase(),
                        false),
                J3ManualCallControlError.ACKNOWLEDGEMENT_REQUIRED);
        assertRejected(
                () -> service.confirm(REQUEST_ID, "phrase incorrecte", true),
                J3ManualCallControlError.CONFIRMATION_TEXT_MISMATCH);
        assertRejected(
                () -> service.confirm(
                        REQUEST_ID,
                        prepared.intent().confirmationPhrase() + " ",
                        true),
                J3ManualCallControlError.CONFIRMATION_TEXT_MISMATCH);

        var confirmed = service.confirm(
                REQUEST_ID,
                prepared.intent().confirmationPhrase(),
                true);

        assertThat(confirmed.intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_BLOCKED);
        assertThat(confirmed.intent().confirmationPhrase()).isNull();
        assertThat(confirmed.intent().confirmedAt()).isEqualTo(NOW);
        assertThat(confirmed.providerTransportAvailable()).isFalse();
        assertRejected(
                () -> service.confirm(REQUEST_ID, "unused", true),
                J3ManualCallControlError.INTENT_ALREADY_CONFIRMED);
    }

    @Test
    void expiresAnUnconfirmedIntentAndNeverKeepsItsPhrase() {
        MutableClock clock = new MutableClock(NOW);
        var service = service(clock);
        service.rearmAfterGlobalStop();
        service.activateByOperator();
        var prepared = service.prepare(DATE);

        clock.advance(J3ManualCallControlService.CONFIRMATION_TTL);
        var expired = service.snapshot();

        assertThat(expired.intent().state()).isEqualTo(J3ManualCallIntentState.EXPIRED);
        assertThat(expired.intent().confirmationPhrase()).isNull();
        assertRejected(
                () -> service.confirm(
                        REQUEST_ID,
                        prepared.intent().confirmationPhrase(),
                        true),
                J3ManualCallControlError.CONFIRMATION_EXPIRED);
    }

    @Test
    void globalStopLocksTheCircuitAndCancelsAnActiveIntent() {
        var service = service(new MutableClock(NOW));
        service.rearmAfterGlobalStop();
        service.activateByOperator();
        service.prepare(DATE);

        var stopped = service.stopGlobally();

        assertThat(stopped.globalStopActive()).isTrue();
        assertThat(stopped.circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(stopped.circuitReason()).isEqualTo(J3CircuitReason.OPERATOR_STOP);
        assertThat(stopped.intent().state())
                .isEqualTo(J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP);
        assertThat(stopped.intent().confirmationPhrase()).isNull();
        assertRejected(
                () -> service.confirm(REQUEST_ID, "unused", true),
                J3ManualCallControlError.GLOBAL_STOP_ACTIVE);
    }

    private static J3ManualCallControlService service(Clock clock) {
        return new J3ManualCallControlService(clock, () -> REQUEST_ID, () -> 42);
    }

    private static void assertRejected(
            Runnable action,
            J3ManualCallControlError expectedError) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        J3ManualCallControlException.class,
                        exception -> assertThat(exception.error()).isEqualTo(expectedError));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock is UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
