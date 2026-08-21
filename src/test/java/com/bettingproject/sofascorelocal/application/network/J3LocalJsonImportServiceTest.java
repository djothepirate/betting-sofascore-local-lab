package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3LocalJsonImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-21");
    private static final UUID REQUEST_ID = UUID.fromString(
            "9fca983e-a9af-4d6a-b0ba-9f9b8f36b7d2");
    private static final URI ORIGIN = URI.create("https://www.sofascore.com");

    @Test
    void importsACompleteJ3PageSequenceWithoutTransportOrCache() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        J3LocalJsonImportService service = service(
                control, store, evidenceService, clock);
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        var result = service.importPages(REQUEST_ID, List.of(
                RawPayloadEvidence.capture(withHasNextPage(providerShape, true)),
                RawPayloadEvidence.capture(withHasNextPage(providerShape, false))));

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(2);
        assertThat(result.providerRequests()).isZero();
        assertThat(result.cacheHits()).isZero();
        assertThat(result.localJsonImports()).isEqualTo(2);
        assertThat(store.saved).hasSize(2);
        assertThat(store.saved)
                .extracting(RawManualCallSnapshot::acquisitionMode)
                .containsOnly(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(store.saved)
                .extracting(RawManualCallSnapshot::requestKey)
                .containsExactly(
                        "SCHEDULED_EVENTS|date=2026-08-21|page=1",
                        "SCHEDULED_EVENTS|date=2026-08-21|page=2");
        assertThat(store.classifiedStatuses)
                .containsExactly(
                        RawSnapshotSchemaStatus.PARSED,
                        RawSnapshotSchemaStatus.PARSED);
        assertThat(control.snapshot().globalStopActive()).isTrue();
        assertThat(control.snapshot().circuitState()).isEqualTo(J3CircuitState.LOCKED);
        assertThat(control.snapshot().circuitReason())
                .isEqualTo(J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK);
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.COMPLETED);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("J3_MINIMIZED_EVIDENCE_VERSION=5")
                .contains("PROVIDER_PAGES_REQUESTED=NONE")
                .contains("CACHE_HIT_PAGES=NONE")
                .contains("LOCAL_JSON_IMPORT_PAGES=1,2")
                .contains("PROVIDER_REQUEST_COUNT=0")
                .contains("CACHE_HIT_COUNT=0")
                .contains("LOCAL_JSON_IMPORT_COUNT=2")
                .contains("PAGE_1_RESOLUTION_SOURCE=LOCAL_JSON_IMPORT")
                .contains("PAGE_1_PROVIDER_REQUEST_EXECUTED=NO")
                .contains("PAGE_1_LOCAL_JSON_IMPORT_EXECUTED=YES")
                .contains("PAGE_2_HAS_NEXT_PAGE=false");
    }

    @Test
    void rejectsAnIncompleteSequenceBeforeClaimOrPersistence() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        J3LocalJsonImportService service = service(
                control, store, evidenceService, clock);
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        assertThatThrownBy(() -> service.importPages(REQUEST_ID, List.of(
                RawPayloadEvidence.capture(withHasNextPage(providerShape, true)))))
                .isInstanceOf(J3LocalJsonImportException.class)
                .extracting("error")
                .isEqualTo(J3LocalJsonImportError.PAGINATION_SEQUENCE_INVALID);

        assertThat(store.saved).isEmpty();
        assertThat(evidenceService.latestDocument()).isEmpty();
        assertThat(control.snapshot().globalStopActive()).isFalse();
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);
    }

    @Test
    void rejectsAnEventsPayloadBeforeClaimBecauseJ3RequiresScheduledTournaments()
            throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3LocalJsonImportService service = service(
                control,
                store,
                new J3ManualCollectionEvidenceService(),
                clock);
        byte[] eventsShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/nominal.json"));

        assertThatThrownBy(() -> service.importPages(REQUEST_ID, List.of(
                RawPayloadEvidence.capture(eventsShape))))
                .isInstanceOf(J3LocalJsonImportException.class)
                .extracting("error")
                .isEqualTo(J3LocalJsonImportError.PAYLOAD_SHAPE_INCOMPATIBLE);

        assertThat(store.saved).isEmpty();
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);
    }

    private static J3LocalJsonImportService service(
            J3ManualCallControlService control,
            RecordingStore store,
            J3ManualCollectionEvidenceService evidenceService,
            Clock clock) {
        return new J3LocalJsonImportService(
                control,
                new J3ScheduledEventsOutcomeProcessor(
                        store,
                        new ScheduledEventsV1Parser(),
                        control.circuit()),
                new ScheduledEventsV1Parser(),
                new J3SingleCallGuard(),
                evidenceService,
                clock,
                Duration.ofMinutes(10));
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
        return control;
    }

    private static byte[] withHasNextPage(byte[] source, boolean value) {
        String json = new String(source, StandardCharsets.UTF_8);
        return json.replace(
                        "\"hasNextPage\": true",
                        "\"hasNextPage\": " + value)
                .getBytes(StandardCharsets.UTF_8);
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
}
