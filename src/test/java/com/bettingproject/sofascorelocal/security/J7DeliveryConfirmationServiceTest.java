package com.bettingproject.sofascorelocal.security;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationAction;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationReceipt;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryConfirmationRequest;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryConfirmationServiceTest {

    private static final String SESSION_KEY = "session-wo035-a";
    private static final UUID EVENT_ID = UUID.fromString(
            "60000000-0000-4000-8000-000000000035");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000035");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void preparesTheExactDeliveryTextAndAnOpaqueFiveMinuteRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);

        J7DeliveryConfirmationRequest request = service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1));

        assertThat(request.requestId()).satisfies(requestId -> {
            assertThat(requestId.version()).isEqualTo(4);
            assertThat(requestId.variant()).isEqualTo(2);
        });
        assertThat(request.confirmationText()).isEqualTo(
                "LIVRER J7 " + EXPORT_ID + " SHA256 " + FILE_SHA256);
        assertThat(request.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(request.toString())
                .doesNotContain(request.requestId().toString())
                .doesNotContain(request.confirmationText())
                .doesNotContain(EXPORT_ID.toString())
                .doesNotContain(FILE_SHA256);
    }

    @Test
    void bindsDeliveryAndReconciliationToOneExactPositiveAttempt() {
        J7DeliveryConfirmationService service = service(new MutableClock(NOW));

        J7DeliveryConfirmationRequest request = service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.RECONCILIATION,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(3));

        assertThat(request.confirmationText()).isEqualTo(
                "RECONCILIER J7 " + EXPORT_ID
                        + " TENTATIVE 3 SHA256 " + FILE_SHA256);
        assertPrepareError(
                () -> service.prepare(
                        SESSION_KEY,
                        J7DeliveryConfirmationAction.RECONCILIATION,
                        EVENT_ID,
                        EXPORT_ID,
                        FILE_SHA256,
                        OptionalInt.empty()),
                J7DeliveryConfirmationError.INVALID_ACTION_CONTEXT);
        assertPrepareError(
                () -> service.prepare(
                        SESSION_KEY,
                        J7DeliveryConfirmationAction.DELIVERY,
                        EVENT_ID,
                        EXPORT_ID,
                        FILE_SHA256,
                        OptionalInt.empty()),
                J7DeliveryConfirmationError.INVALID_ACTION_CONTEXT);
    }

    @Test
    void consumesTheExactAcknowledgedRequestOnlyOnce() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);

        consume(service, request, true, clock);

        assertInvalidConsumption(() -> consume(service, request, true, clock));
    }

    @Test
    void issuedRuntimeCapabilityIsIdentityBoundAndConsumedOnlyOnce() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);
        J7DeliveryConfirmationReceipt issued = consume(service, request, true, clock);
        J7DeliveryConfirmationReceipt forgedCopy = new J7DeliveryConfirmationReceipt(
                issued.action(),
                issued.canonicalEventId(),
                issued.exportId(),
                issued.fileSha256(),
                issued.attemptNumber(),
                issued.providerOwnerGoReference());

        assertRuntimeCapabilityRejected(() ->
                service.consumeRuntimeCapability(forgedCopy, NOW));
        service.consumeRuntimeCapability(issued, NOW);
        assertRuntimeCapabilityRejected(() ->
                service.consumeRuntimeCapability(issued, NOW));
    }

    @Test
    void issuedRuntimeCapabilityExpiresAtTheOriginalConfirmationBoundary() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);
        J7DeliveryConfirmationReceipt issued = consume(service, request, true, clock);

        assertRuntimeCapabilityRejected(() ->
                service.consumeRuntimeCapability(issued, request.expiresAt()));
    }

    @Test
    void bindsTheServerSelectedOwnerGoWithoutExposingItInTheRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7ProviderDerivedOwnerGo.Reference reference =
                new J7ProviderDerivedOwnerGo.Reference(
                        UUID.fromString("45000000-0000-4000-8000-000000000045"),
                        "e".repeat(64));
        J7DeliveryConfirmationRequest request = service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                Optional.of(reference));

        assertThat(request.toString())
                .doesNotContain(reference.goId().toString())
                .doesNotContain(reference.ownerDecisionBlockSha256());

        J7DeliveryConfirmationReceipt receipt = service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                request.requestId(),
                request.confirmationText(),
                true,
                clock);

        assertThat(receipt.providerOwnerGoReference()).contains(reference);
        assertThat(receipt.action()).isEqualTo(J7DeliveryConfirmationAction.DELIVERY);
        assertThat(receipt.canonicalEventId()).isEqualTo(EVENT_ID);
        assertThat(receipt.exportId()).isEqualTo(EXPORT_ID);
        assertThat(receipt.fileSha256()).isEqualTo(FILE_SHA256);
        assertThat(receipt.attemptNumber()).isEqualTo(1);
        assertThat(receipt.toString())
                .doesNotContain(reference.goId().toString())
                .doesNotContain(reference.ownerDecisionBlockSha256())
                .doesNotContain(EVENT_ID.toString())
                .doesNotContain(EXPORT_ID.toString())
                .doesNotContain(FILE_SHA256);
    }

    @Test
    void replacingPreparationCannotSubstituteTheEarlierServerSelectedOwnerGo() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7ProviderDerivedOwnerGo.Reference firstReference =
                new J7ProviderDerivedOwnerGo.Reference(
                        UUID.fromString("45000000-0000-4000-8000-000000000045"),
                        "e".repeat(64));
        J7ProviderDerivedOwnerGo.Reference secondReference =
                new J7ProviderDerivedOwnerGo.Reference(
                        UUID.fromString("45000000-0000-4000-8000-000000000046"),
                        "f".repeat(64));
        J7DeliveryConfirmationRequest first = service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                Optional.of(firstReference));
        J7DeliveryConfirmationRequest second = service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                Optional.of(secondReference));

        assertInvalidConsumption(() -> service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                first.requestId(),
                first.confirmationText(),
                true,
                clock));
        assertInvalidConsumption(() -> service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                second.requestId(),
                second.confirmationText(),
                true,
                clock));
        J7DeliveryConfirmationRequest fresh = service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                Optional.of(secondReference));
        assertThat(service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                fresh.requestId(),
                fresh.confirmationText(),
                true,
                clock).providerOwnerGoReference()).contains(secondReference);
    }

    @Test
    void aChangedDeliveryAttemptOrdinalBurnsTheStaleRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);

        assertInvalidConsumption(() -> service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(2),
                request.requestId(),
                request.confirmationText(),
                true,
                clock));
        assertInvalidConsumption(() -> consume(service, request, true, clock));
    }

    @Test
    void anUnacknowledgedOrWrongSubmissionBurnsTheRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest unacknowledged = delivery(service);

        assertInvalidConsumption(() -> consume(
                service, unacknowledged, false, clock));
        assertInvalidConsumption(() -> consume(
                service, unacknowledged, true, clock));

        J7DeliveryConfirmationRequest wrongText = delivery(service);
        assertInvalidConsumption(() -> service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                wrongText.requestId(),
                wrongText.confirmationText() + " modifié",
                true,
                clock));
        assertInvalidConsumption(() -> consume(service, wrongText, true, clock));
    }

    @Test
    void wrongSessionRequestOrIdentityBurnsOnlyThatSessionsCurrentRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest source = delivery(service);
        J7DeliveryConfirmationRequest other = service.prepare(
                "session-wo035-b",
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1));

        assertInvalidConsumption(() -> service.consume(
                "session-wo035-b",
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                source.requestId(),
                source.confirmationText(),
                true,
                clock));
        assertInvalidConsumption(() -> service.consume(
                "session-wo035-b",
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                other.requestId(),
                other.confirmationText(),
                true,
                clock));

        UUID anotherEvent = UUID.fromString(
                "60000000-0000-4000-8000-000000000036");
        assertInvalidConsumption(() -> service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                anotherEvent,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                source.requestId(),
                source.confirmationText(),
                true,
                clock));
        assertInvalidConsumption(() -> consume(service, source, true, clock));
    }

    @Test
    void rejectsAtTheExactExpiryBoundaryAndBurnsTheRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);
        clock.set(request.expiresAt());

        assertInvalidConsumption(() -> consume(service, request, true, clock));
        assertInvalidConsumption(() -> consume(service, request, true, clock));
    }

    @Test
    void aMissingClockFailsClosedAndBurnsTheRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);

        assertInvalidConsumption(() -> consume(service, request, true, null));
        assertInvalidConsumption(() -> consume(service, request, true, clock));
    }

    @Test
    void aNewPreparationReplacesTheSameSessionsEarlierRequest() {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest first = delivery(service);
        J7DeliveryConfirmationRequest second = delivery(service);

        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertInvalidConsumption(() -> consume(service, first, true, clock));
        assertInvalidConsumption(() -> consume(service, second, true, clock));

        J7DeliveryConfirmationRequest third = delivery(service);
        consume(service, third, true, clock);
    }

    @Test
    void concurrentConsumersProduceExactlyOneSuccessfulConsumption()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        J7DeliveryConfirmationService service = service(clock);
        J7DeliveryConfirmationRequest request = delivery(service);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        consume(service, request, true, clock);
                        successes.incrementAndGet();
                    }
                    catch (J7DeliveryConfirmationException exception) {
                        if (exception.error()
                                == J7DeliveryConfirmationError
                                .INVALID_OR_EXPIRED_CONFIRMATION) {
                            rejections.incrementAndGet();
                        }
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successes).hasValue(1);
        assertThat(rejections).hasValue(1);
    }

    @Test
    void validatesSessionIdentityAndTtlWithoutLeakingInputs() {
        J7DeliveryConfirmationService service = service(new MutableClock(NOW));

        assertPrepareError(
                () -> service.prepare(
                        " ",
                        J7DeliveryConfirmationAction.DELIVERY,
                        EVENT_ID,
                        EXPORT_ID,
                        FILE_SHA256,
                        OptionalInt.of(1)),
                J7DeliveryConfirmationError.INVALID_SESSION_KEY);
        assertPrepareError(
                () -> service.prepare(
                        SESSION_KEY,
                        J7DeliveryConfirmationAction.DELIVERY,
                        new UUID(0, 0),
                        EXPORT_ID,
                        FILE_SHA256,
                        OptionalInt.of(1)),
                J7DeliveryConfirmationError.INVALID_DELIVERY_IDENTITY);
        assertPrepareError(
                () -> service.prepare(
                        SESSION_KEY,
                        J7DeliveryConfirmationAction.DELIVERY,
                        EVENT_ID,
                        EXPORT_ID,
                        "not-a-hash",
                        OptionalInt.of(1)),
                J7DeliveryConfirmationError.INVALID_DELIVERY_IDENTITY);
        assertPrepareError(
                () -> service.prepare(
                        SESSION_KEY,
                        null,
                        EVENT_ID,
                        EXPORT_ID,
                        FILE_SHA256,
                        OptionalInt.of(1)),
                J7DeliveryConfirmationError.INVALID_ACTION_CONTEXT);

        assertThatThrownBy(() -> new J7DeliveryConfirmationService(
                new SequenceSecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl must be between one second and ten minutes");
        assertThatThrownBy(() -> new J7DeliveryConfirmationService(
                new SequenceSecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(11)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl must be between one second and ten minutes");
    }

    private static J7DeliveryConfirmationRequest delivery(
            J7DeliveryConfirmationService service) {
        return service.prepare(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1));
    }

    private static void assertRuntimeCapabilityRejected(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(J7DeliveryException.class)
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(J7DeliveryError.INVALID_CONFIRMATION);
    }

    private static J7DeliveryConfirmationReceipt consume(
            J7DeliveryConfirmationService service,
            J7DeliveryConfirmationRequest request,
            boolean acknowledged,
            Clock clock) {
        return service.consume(
                SESSION_KEY,
                J7DeliveryConfirmationAction.DELIVERY,
                EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                OptionalInt.of(1),
                request.requestId(),
                request.confirmationText(),
                acknowledged,
                clock);
    }

    private static J7DeliveryConfirmationService service(Clock clock) {
        return new J7DeliveryConfirmationService(
                new SequenceSecureRandom(), clock, Duration.ofMinutes(5));
    }

    private static void assertPrepareError(
            Runnable action,
            J7DeliveryConfirmationError expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J7DeliveryConfirmationException.class)
                .hasMessage(expected.name())
                .extracting(exception ->
                        ((J7DeliveryConfirmationException) exception).error())
                .isEqualTo(expected);
    }

    private static void assertInvalidConsumption(Runnable action) {
        assertPrepareError(
                action,
                J7DeliveryConfirmationError.INVALID_OR_EXPIRED_CONFIRMATION);
    }

    private static final class SequenceSecureRandom extends SecureRandom {

        private final AtomicLong value = new AtomicLong(1);

        @Override
        public long nextLong() {
            return value.getAndIncrement();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("only UTC is supported in this test");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
