package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3ManualCallControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-12");
    private static final LocalDate QUALIFICATION_DATE = LocalDate.parse("2026-08-13");
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
                .isEqualTo(
                        "SCHEDULED_EVENTS|date=2026-08-12|pagination=has-next-page|max=25");
        assertThat(prepared.intent().state())
                .isEqualTo(J3ManualCallIntentState.AWAITING_CONFIRMATION);
        assertThat(prepared.intent().confirmationPhrase())
                .isEqualTo(
                        "CONFIRMER SCHEDULED_EVENTS 2026-08-12 PAGINATION DYNAMIQUE MAX 25 000042");
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

    @Test
    void signalsTheExactCampaignBeforeApplyingTheGlobalBusinessLock() {
        J3ManualCallControlService service = executingService();

        var stopped = service.stopGlobally(requestId -> {
            assertThat(requestId).isEqualTo(REQUEST_ID);
            var beforeLock = service.snapshot();
            assertThat(beforeLock.globalStopActive()).isFalse();
            assertThat(beforeLock.circuitState()).isEqualTo(J3CircuitState.CLOSED);
            assertThat(beforeLock.intent().state())
                    .isEqualTo(J3ManualCallIntentState.EXECUTING);
        });

        assertThat(stopped.globalStopActive()).isTrue();
        assertThat(stopped.circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(stopped.intent().state())
                .isEqualTo(J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP);
    }

    @Test
    void appliesTheGlobalBusinessLockEvenWhenTheCampaignSignalFails() {
        J3ManualCallControlService service = executingService();

        assertThatThrownBy(() -> service.stopGlobally(requestId -> {
            assertThat(requestId).isEqualTo(REQUEST_ID);
            throw new IllegalStateException("signal failed");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("signal failed");

        var stopped = service.snapshot();
        assertThat(stopped.globalStopActive()).isTrue();
        assertThat(stopped.circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(stopped.circuitReason()).isEqualTo(J3CircuitReason.OPERATOR_STOP);
        assertThat(stopped.intent().state())
                .isEqualTo(J3ManualCallIntentState.CANCELLED_BY_GLOBAL_STOP);
    }

    @Test
    void exposesAReadyIntentOnlyForTheExactProviderOptIn() {
        MutableClock clock = new MutableClock(NOW);
        var service = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> J3ProviderQualificationSnapshot.available(
                        URI.create("https://www.sofascore.com")));
        service.rearmAfterGlobalStop();
        service.activateByOperator();
        var prepared = service.prepare(QUALIFICATION_DATE);

        var confirmed = service.confirm(
                REQUEST_ID,
                prepared.intent().confirmationPhrase(),
                true);

        assertThat(confirmed.providerTransportAvailable()).isTrue();
        assertThat(confirmed.providerBlockers()).isEmpty();
        assertThat(confirmed.intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);

        var claim = service.claimExecution(REQUEST_ID);

        assertThat(claim.date()).isEqualTo(QUALIFICATION_DATE);
        assertThat(claim.providerOrigin()).hasToString("https://www.sofascore.com");
        assertThat(claim.firstPage()).isEqualTo(1);
        assertThat(service.snapshot().providerTransportAvailable()).isTrue();
        assertThat(service.snapshot().providerBlockers()).isEmpty();
    }

    @Test
    void rejectsAProviderClaimBeforeExecutingWithoutConsumingTheLocalImportClaim() {
        MutableClock clock = new MutableClock(NOW);
        SofascoreProperties sofascore = new SofascoreProperties();
        sofascore.setEnabled(true);
        sofascore.setJ3QualificationEnabled(true);
        sofascore.setBaseUrl("https://www.sofascore.com");
        sofascore.setAllowedEndpoints(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        J3ProviderQualificationPolicy policy = new J3ProviderQualificationPolicy(
                sofascore, new ProviderPlaywrightProperties());
        var service = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                policy::snapshot,
                policy::localImportSnapshot);
        service.rearmAfterGlobalStop();
        service.activateByOperator();
        var prepared = service.prepare(QUALIFICATION_DATE);
        service.confirm(REQUEST_ID, prepared.intent().confirmationPhrase(), true);

        assertRejected(
                () -> service.claimExecution(REQUEST_ID),
                J3ManualCallControlError.PROVIDER_TRANSPORT_UNAVAILABLE);

        assertThat(service.executionMayContinue(REQUEST_ID)).isFalse();
        assertThat(service.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);
        assertThat(service.snapshot().providerTransportAvailable()).isFalse();
        assertThat(service.snapshot().localImportAvailable()).isTrue();

        var localClaim = service.claimLocalImportExecution(REQUEST_ID);

        assertThat(localClaim.requestId()).isEqualTo(REQUEST_ID);
        assertThat(localClaim.date()).isEqualTo(QUALIFICATION_DATE);
        assertThat(service.executionMayContinue(REQUEST_ID)).isTrue();
    }

    @Test
    void alwaysStartsAtPageOneAndAllowsAnyExplicitIsoDate() {
        MutableClock clock = new MutableClock(NOW);
        var service = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> J3ProviderQualificationSnapshot.available(
                        URI.create("https://www.sofascore.com"),
                        3));
        service.rearmAfterGlobalStop();
        service.activateByOperator();

        var prepared = service.prepare(QUALIFICATION_DATE);

        assertThat(prepared.intent().firstPage()).isEqualTo(1);
        assertThat(prepared.intent().completedPages()).isZero();
        assertThat(prepared.intent().requestKey())
                .isEqualTo(
                        "SCHEDULED_EVENTS|date=2026-08-13|pagination=has-next-page|max=25");
        assertThat(prepared.intent().confirmationPhrase())
                .isEqualTo(
                        "CONFIRMER SCHEDULED_EVENTS 2026-08-13 PAGINATION DYNAMIQUE MAX 25 000042");

        service.confirm(REQUEST_ID, prepared.intent().confirmationPhrase(), true);
        var claim = service.claimExecution(REQUEST_ID);

        assertThat(claim.firstPage()).isEqualTo(1);
        assertRejected(
                () -> service.recordPageCompleted(REQUEST_ID, 2),
                J3ManualCallControlError.PAGE_SEQUENCE_INVALID);
        service.recordPageCompleted(REQUEST_ID, 1);
        service.recordPageCompleted(REQUEST_ID, 2);
        service.completeExecution(REQUEST_ID);

        assertThat(service.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.COMPLETED);
        assertThat(service.snapshot().intent().completedPages()).isEqualTo(2);
    }

    @Test
    void terminalCollectionCanBeRearmedForANewExplicitSequence() {
        MutableClock clock = new MutableClock(NOW);
        var service = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> J3ProviderQualificationSnapshot.available(
                        URI.create("https://www.sofascore.com")));
        service.rearmAfterGlobalStop();
        service.activateByOperator();
        var prepared = service.prepare(QUALIFICATION_DATE);
        service.confirm(REQUEST_ID, prepared.intent().confirmationPhrase(), true);
        service.claimExecution(REQUEST_ID);
        service.recordPageCompleted(REQUEST_ID, 1);
        service.completeExecution(REQUEST_ID);

        var locked = service.lockAfterCollection(REQUEST_ID);

        assertThat(locked.globalStopActive()).isTrue();
        assertThat(locked.circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);

        var rearmed = service.rearmAfterGlobalStop();
        assertThat(rearmed.intent()).isNull();
        service.activateByOperator();
        var secondPrepared = service.prepare(QUALIFICATION_DATE.plusDays(1));

        assertThat(secondPrepared.intent().date()).isEqualTo(QUALIFICATION_DATE.plusDays(1));
        assertThat(secondPrepared.intent().state())
                .isEqualTo(J3ManualCallIntentState.AWAITING_CONFIRMATION);
    }

    private static J3ManualCallControlService service(Clock clock) {
        return new J3ManualCallControlService(clock, () -> REQUEST_ID, () -> 42);
    }

    private static J3ManualCallControlService executingService() {
        J3ManualCallControlService service = new J3ManualCallControlService(
                new MutableClock(NOW),
                () -> REQUEST_ID,
                () -> 42,
                () -> J3ProviderQualificationSnapshot.available(
                        URI.create("https://www.sofascore.com")));
        service.rearmAfterGlobalStop();
        service.activateByOperator();
        var prepared = service.prepare(QUALIFICATION_DATE);
        service.confirm(REQUEST_ID, prepared.intent().confirmationPhrase(), true);
        service.claimExecution(REQUEST_ID);
        return service;
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
