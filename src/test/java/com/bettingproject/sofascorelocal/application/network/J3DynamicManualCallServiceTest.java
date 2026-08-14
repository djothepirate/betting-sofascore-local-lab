package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportFailure;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.ScheduledEventsProviderPageTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3DynamicManualCallServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-13");
    private static final UUID REQUEST_ID = UUID.fromString(
            "3ccfd0a0-7825-4bfa-977b-358be086b1e2");
    private static final URI ORIGIN = URI.create("https://www.sofascore.com");

    @Test
    void followsHasNextPageUntilFalseWithThreeSecondsBetweenStarts() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        List<Instant> starts = new ArrayList<>();
        List<Duration> waits = new ArrayList<>();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            starts.add(clock.instant());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    200,
                    withHasNextPage(terminalBody, request.page() < 5));
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(control, transport, store, evidenceService, clock, duration -> {
            waits.add(duration);
            clock.advance(duration);
        });

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(pages).containsExactly(1, 2, 3, 4, 5);
        assertThat(starts).hasSize(5);
        for (int index = 1; index < starts.size(); index++) {
            assertThat(Duration.between(starts.get(index - 1), starts.get(index)))
                    .isGreaterThanOrEqualTo(Duration.ofSeconds(3));
        }
        assertThat(waits).hasSize(4);
        assertThat(store.saved).hasSize(5);
        assertThat(store.classifiedStatuses)
                .containsOnly(RawSnapshotSchemaStatus.PARSED);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.COMPLETED);
        assertThat(control.snapshot().intent().completedPages()).isEqualTo(5);
        assertThat(control.snapshot().globalStopActive()).isTrue();
        assertThat(control.snapshot().circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        var evidence = evidenceService.latestDocument().orElseThrow();
        assertThat(evidence.evidence().pageAttempts()).hasSize(5);
        assertThat(evidence.reportText())
                .contains("PAGES_ATTEMPTED=1,2,3,4,5")
                .contains("PAGINATION_MODE=HAS_NEXT_PAGE")
                .contains("MAXIMUM_PAGE_LIMIT=25")
                .contains("PAGE_1_HAS_NEXT_PAGE=true")
                .contains("PAGE_5_HAS_NEXT_PAGE=false")
                .contains("FINAL_GLOBAL_STOP=ACTIVE")
                .contains("FINAL_CIRCUIT_REASON=MANUAL_COLLECTION_TERMINAL_LOCK")
                .contains("RAW_PAYLOAD_INCLUDED=NO")
                .doesNotContain("https://www.sofascore.com")
                .doesNotContain("\"events\"");

        assertThatThrownBy(() -> service.execute(REQUEST_ID))
                .isInstanceOfSatisfying(
                        J3ManualCallControlException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(J3ManualCallControlError.EXECUTION_ALREADY_STARTED));
    }

    @Test
    void stopsNormallyAfterPageOneWhenItIsTheLastAvailablePage()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] body = Files.readAllBytes(Path.of("fixtures/scheduled-events/nominal.json"));
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(25));
            return response(request, requestedAt, clock.instant(), 200, body);
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(control, transport, store, evidenceService, clock, clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(1);
        assertThat(pages).containsExactly(1);
        assertThat(store.saved).hasSize(1);
        assertThat(control.snapshot().intent().firstPage()).isEqualTo(1);
        assertThat(control.snapshot().intent().completedPages()).isEqualTo(1);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("J3_MINIMIZED_EVIDENCE_VERSION=3")
                .contains("PROVIDER_FIRST_PAGE=1")
                .contains("PAGES_ATTEMPTED=1")
                .contains("PAGES_COMPLETED_COUNT=1")
                .contains("LAST_COMPLETED_PAGE=1")
                .contains("PAGE_1_HAS_NEXT_PAGE=false")
                .doesNotContain("PAGE_2_REQUESTED_AT");
    }

    @Test
    void persistsTheFailingPageAndStopsBeforePageThreeWithoutRetry() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        byte[] validBody = withHasNextPage(terminalBody, true);
        byte[] forbiddenBody = "{\"error\":\"forbidden\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(
                    request,
                    requestedAt,
                    clock.instant(),
                    request.page() == 2 ? 403 : 200,
                    request.page() == 2 ? forbiddenBody : validBody);
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(
                control,
                transport,
                store,
                evidenceService,
                clock,
                clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.completedPages()).isEqualTo(1);
        assertThat(result.failedPage()).isEqualTo(2);
        assertThat(result.terminalCode()).isEqualTo("HTTP_FORBIDDEN");
        assertThat(pages).containsExactly(1, 2);
        assertThat(store.saved).hasSize(2);
        assertThat(store.saved.get(1).schemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.TRANSPORT_ERROR);
        assertThat(control.snapshot().circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.FAILED);
        var evidence = evidenceService.latestDocument().orElseThrow();
        assertThat(evidence.reportText())
                .contains("TERMINAL_CODE=HTTP_FORBIDDEN")
                .contains("PAGE_2_SCHEMA_STATUS=TRANSPORT_ERROR")
                .contains("PAGE_2_TERMINAL_CODE=HTTP_FORBIDDEN")
                .contains("PAGE_2_SNAPSHOT_RECORDED=YES")
                .doesNotContain("forbidden");
    }

    @Test
    void recordsOnlyMinimizedAttemptMetadataWhenTransportFailsBeforeSnapshot() {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        ScheduledEventsProviderPageTransport transport = request -> {
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.TIMEOUT);
        };
        J3ManualCallControlService control = readyControl(clock);
        var service = service(
                control,
                transport,
                store,
                evidenceService,
                clock,
                clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.failedPage()).isEqualTo(1);
        assertThat(result.terminalCode()).isEqualTo("TIMEOUT");
        assertThat(store.saved).isEmpty();
        assertThat(control.snapshot().globalStopActive()).isTrue();
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("PAGES_ATTEMPTED=1")
                .contains("PAGE_1_SNAPSHOT_RECORDED=NO")
                .contains("PAGE_1_HTTP_STATUS=NONE")
                .contains("PAGE_1_PAYLOAD_SHA256=NONE")
                .contains("PAGE_1_TERMINAL_CODE=TIMEOUT");
    }

    @Test
    void stopsBeforePageTwentySixWhenProviderStillAnnouncesAnotherPage()
            throws Exception {
        MutableClock clock = new MutableClock(NOW);
        RecordingStore store = new RecordingStore();
        List<Integer> pages = new ArrayList<>();
        byte[] terminalBody = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));
        byte[] continuingBody = withHasNextPage(terminalBody, true);
        ScheduledEventsProviderPageTransport transport = request -> {
            pages.add(request.page());
            Instant requestedAt = clock.instant();
            clock.advance(Duration.ofMillis(10));
            return response(request, requestedAt, clock.instant(), 200, continuingBody);
        };
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        var service = service(
                control, transport, store, evidenceService, clock, clock::advance);

        var result = service.execute(REQUEST_ID);

        assertThat(result.completed()).isFalse();
        assertThat(result.completedPages()).isEqualTo(25);
        assertThat(result.failedPage()).isEqualTo(26);
        assertThat(result.terminalCode()).isEqualTo("PAGINATION_LIMIT_REACHED");
        assertThat(pages).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 25).boxed().toList());
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("MAXIMUM_PAGE_LIMIT=25")
                .contains("FAILED_PAGE=26")
                .contains("PAGE_25_HAS_NEXT_PAGE=true")
                .doesNotContain("PAGE_26_REQUESTED_AT");
    }

    private static J3DynamicManualCallService service(
            J3ManualCallControlService control,
            ScheduledEventsProviderPageTransport transport,
            RecordingStore store,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock,
            J3DynamicManualCallService.InterPageDelay delay) {
        return new J3DynamicManualCallService(
                control,
                transport,
                new J3ScheduledEventsOutcomeProcessor(
                        store,
                        new ScheduledEventsV1Parser(),
                        control.circuit()),
                new J3SingleCallGuard(),
                evidenceService,
                clock,
                Duration.ofSeconds(3),
                delay);
    }

    private static J3ManualCallControlService readyControl(Clock clock) {
        J3ManualCallControlService control = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> J3ProviderQualificationSnapshot.available(ORIGIN));
        control.rearmAfterGlobalStop();
        control.activateByOperator();
        var prepared = control.prepare(DATE);
        control.confirm(
                REQUEST_ID,
                prepared.intent().confirmationPhrase(),
                true);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);
        return control;
    }

    private static byte[] withHasNextPage(byte[] source, boolean value) {
        String json = new String(source, java.nio.charset.StandardCharsets.UTF_8);
        return json.replace(
                        "\"hasNextPage\": false",
                        "\"hasNextPage\": " + value)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static ScheduledEventsTransportResponse response(
            ScheduledEventsProviderPageRequest request,
            Instant requestedAt,
            Instant receivedAt,
            int status,
            byte[] body) {
        return new ScheduledEventsTransportResponse(
                request.requestKey(),
                requestedAt,
                receivedAt,
                status,
                "application/json",
                Duration.between(requestedAt, receivedAt),
                RawPayloadEvidence.capture(body));
    }

    private static final class RecordingStore implements RawManualCallSnapshotStore {

        private final List<RawManualCallSnapshot> saved = new ArrayList<>();
        private final List<RawSnapshotSchemaStatus> classifiedStatuses = new ArrayList<>();

        @Override
        public RawSnapshotPersistenceResult save(RawManualCallSnapshot snapshot) {
            saved.add(snapshot);
            return new RawSnapshotPersistenceResult(
                    saved.size(),
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        }

        @Override
        public void classify(
                long snapshotId,
                RawSnapshotSchemaStatus schemaStatus,
                String errorCode) {
            classifiedStatuses.add(schemaStatus);
        }
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
