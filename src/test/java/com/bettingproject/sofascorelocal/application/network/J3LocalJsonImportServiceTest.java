package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
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
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .contains("J3_MINIMIZED_EVIDENCE_VERSION=6")
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
    void importsTwentySevenContiguousPagesWhenTheLastPageEndsPagination()
            throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        J3LocalJsonImportService service = service(control, store, evidenceService, clock);
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        var result = service.importPages(REQUEST_ID, pageSequence(providerShape, 27));

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(27);
        assertThat(result.localJsonImports()).isEqualTo(27);
        assertThat(store.saved).hasSize(27);
        assertThat(evidenceService.latestDocument().orElseThrow().reportText())
                .contains("LOCAL_JSON_IMPORT_PAGES=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27")
                .contains("PAGE_27_HAS_NEXT_PAGE=false");
    }

    @Test
    void importsTheFullThirtyFivePageLocalBound() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3LocalJsonImportService service = service(
                control, store, new J3ManualCollectionEvidenceService(), clock);
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        var result = service.importPages(REQUEST_ID, pageSequence(providerShape, 35));

        assertThat(result.completed()).isTrue();
        assertThat(result.completedPages()).isEqualTo(35);
        assertThat(result.localJsonImports()).isEqualTo(35);
        assertThat(store.saved).hasSize(35);
        assertThat(store.saved.getLast().requestKey())
                .isEqualTo("SCHEDULED_EVENTS|date=2026-08-21|page=35");
    }

    @Test
    void rejectsThirtySixLocalPagesBeforeClaimOrPersistence() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RecordingStore store = new RecordingStore();
        J3ManualCallControlService control = readyControl(clock);
        J3ManualCollectionEvidenceService evidenceService =
                new J3ManualCollectionEvidenceService();
        J3LocalJsonImportService service = service(control, store, evidenceService, clock);
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        assertThatThrownBy(() -> service.importPages(REQUEST_ID, pageSequence(providerShape, 36)))
                .isInstanceOf(J3LocalJsonImportException.class)
                .extracting("error")
                .isEqualTo(J3LocalJsonImportError.TOO_MANY_PAGES);

        assertThat(store.saved).isEmpty();
        assertThat(evidenceService.latestDocument()).isEmpty();
        assertThat(control.snapshot().globalStopActive()).isFalse();
        assertThat(control.snapshot().intent().state())
                .isEqualTo(J3ManualCallIntentState.CONFIRMED_READY);
    }

    @Test
    void importsLocallyWhenPlaywrightDefaultsAreDisabledAndWorkerJarIsEmpty()
            throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SofascoreProperties sofascore = new SofascoreProperties();
        sofascore.setEnabled(true);
        sofascore.setJ3QualificationEnabled(true);
        sofascore.setBaseUrl(ORIGIN.toString());
        sofascore.setAllowedEndpoints(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        ProviderPlaywrightProperties playwrightDefaults =
                new ProviderPlaywrightProperties();
        J3ProviderQualificationPolicy policy = new J3ProviderQualificationPolicy(
                sofascore, playwrightDefaults);
        J3ManualCallControlService control = new J3ManualCallControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                policy::snapshot,
                policy::localImportSnapshot);
        control.rearmAfterGlobalStop();
        control.activateByOperator();
        var prepared = control.prepare(DATE);
        control.confirm(
                REQUEST_ID,
                prepared.intent().confirmationPhrase(),
                true);
        RecordingStore store = new RecordingStore();
        J3LocalJsonImportService service = service(
                control,
                store,
                new J3ManualCollectionEvidenceService(),
                clock);
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        assertThat(control.snapshot().providerTransportAvailable()).isFalse();
        assertThat(control.snapshot().localImportAvailable()).isTrue();
        assertThat(control.snapshot().providerBlockers()).contains(
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");

        var result = service.importPages(REQUEST_ID, List.of(
                RawPayloadEvidence.capture(withHasNextPage(providerShape, false))));

        assertThat(result.completed()).isTrue();
        assertThat(result.providerRequests()).isZero();
        assertThat(result.cacheHits()).isZero();
        assertThat(result.localJsonImports()).isEqualTo(1);
        assertThat(store.saved).singleElement()
                .extracting(RawManualCallSnapshot::acquisitionMode)
                .isEqualTo(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
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

    @Test
    void auditsAReachedLocalImportPersistenceFailureWithoutInventingAnAttempt()
            throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        J3ManualCallControlService control = readyControl(clock);
        RawManualCallSnapshotStore failingStore = mock(RawManualCallSnapshotStore.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(failingStore).save(any());
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(11L);
        J3LocalJsonImportService service = new J3LocalJsonImportService(
                control,
                new J3ScheduledEventsOutcomeProcessor(
                        failingStore,
                        new ScheduledEventsV1Parser(),
                        control.circuit()),
                new ScheduledEventsV1Parser(),
                new J3SingleCallGuard(),
                new J3ManualCollectionEvidenceService(),
                clock,
                Duration.ofMinutes(10),
                new J8BenchmarkAuditService(evidenceStore, clock));
        byte[] providerShape = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));

        var result = service.importPages(REQUEST_ID, List.of(
                RawPayloadEvidence.capture(withHasNextPage(providerShape, false))));

        assertThat(result.completed()).isFalse();
        assertThat(result.providerRequests()).isZero();
        assertThat(result.localJsonImports()).isZero();
        ArgumentCaptor<J8BenchmarkUnitResult> resultCaptor =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(resultCaptor.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PERSISTENCE_FAILURE);
        verify(evidenceStore, never()).startProviderAttempt(any());
        verify(evidenceStore).finishCampaign(any());
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

    private static List<RawPayloadEvidence> pageSequence(byte[] providerShape, int pageCount) {
        List<RawPayloadEvidence> pages = new ArrayList<>(pageCount);
        for (int page = 1; page <= pageCount; page++) {
            pages.add(RawPayloadEvidence.capture(withHasNextPage(
                    providerShape, page < pageCount)));
        }
        return List.copyOf(pages);
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
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(saved.size()));
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
