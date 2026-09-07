package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV17Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class J5LocalJsonImportProcessorTest {

    private static final Instant NOW = Instant.parse("2026-08-21T07:00:00Z");
    private static final long EVENT_ID = 16310922L;
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(EVENT_ID);

    private RawManualCallSnapshotStore rawStore;
    private CanonicalEventStore canonicalStore;
    private J5EventDataStore dataStore;
    private List<RawManualCallSnapshot> rawSnapshots;
    private List<J5EventDataObservation> observations;
    private J5LocalJsonImportProcessor processor;

    @BeforeEach
    void setUp() {
        rawStore = mock(RawManualCallSnapshotStore.class);
        canonicalStore = mock(CanonicalEventStore.class);
        dataStore = mock(J5EventDataStore.class);

        CanonicalEventObservationView event = mock(CanonicalEventObservationView.class);
        when(event.identity()).thenReturn(IDENTITY);
        when(canonicalStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(event));

        rawSnapshots = new ArrayList<>();
        AtomicLong snapshotIds = new AtomicLong(400L);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            rawSnapshots.add(snapshot);
            long snapshotId = snapshotIds.incrementAndGet();
            return new RawSnapshotPersistenceResult(
                    snapshotId,
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(snapshotId));
        });
        doAnswer(invocation -> null)
                .when(rawStore).classify(anyLong(), any(), any());

        observations = new ArrayList<>();
        AtomicLong observationIds = new AtomicLong(500L);
        when(dataStore.save(any())).thenAnswer(invocation -> {
            J5EventDataObservation observation = invocation.getArgument(0);
            observations.add(observation);
            return new J5EventDataPersistenceResult(
                    observationIds.incrementAndGet(),
                    observation.identity().value(),
                    observation.data().endpointType(),
                    true);
        });

        processor = new J5LocalJsonImportProcessor(
                rawStore,
                canonicalStore,
                dataStore,
                new EventStatisticsV2Parser(),
                new EventIncidentsV17Parser(),
                new EventLineupsV2Parser(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void preparesAllBodiesAndCurrentIdentityWithoutWriting() {
        J5LocalJsonImportProcessingPlan prepared = prepareValid();

        assertThat(prepared.canonicalEventId()).isEqualTo(IDENTITY.value());
        assertThat(prepared.eventId()).isEqualTo(EVENT_ID);
        verify(canonicalStore).findLatestByCanonicalId(IDENTITY.value());
        verifyNoInteractions(rawStore, dataStore);
    }

    @Test
    void executesInOrderUsingOnlyManualLocalJsonPersistence() {
        J5LocalJsonImportProcessingResult result = processor.execute(
                prepareValid(), () -> true);

        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::acquisitionMode)
                .containsOnly(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(observations).hasSize(3);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        verify(canonicalStore, times(2)).findLatestByCanonicalId(IDENTITY.value());
    }

    @Test
    void importsProfessionalHandballWithV17AndPreservesItsRawEvidence() {
        RawPayloadEvidence incidentsPayload = payload("""
                {"incidents":[{"incidentType":"card","incidentClass":"red",
                  "reason":"Professional handball","time":64,"isHome":false,
                  "rescinded":false,"player":{"name":"Synthetic player"}}]}
                """);
        J5LocalJsonImportProcessingPlan prepared = processor.prepare(
                IDENTITY.value(), EVENT_ID, payload("{\"statistics\":[]}"),
                incidentsPayload, payload("{\"confirmed\":false}"));

        var result = processor.execute(prepared, () -> true);

        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(observations.get(1).source().parserVersion()).isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(EventIncidents.class,
                incidents -> assertThat(incidents.incidents().getFirst().reason()).contains("Professional handball"));
        assertThat(rawSnapshots.get(1).parserVersion()).isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        assertThat(rawSnapshots.get(1).payload().sha256()).isEqualTo(incidentsPayload.sha256());
        assertThat(rawSnapshots).extracting(RawManualCallSnapshot::acquisitionMode)
                .containsOnly(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
    }

    @Test
    void acceptsEmptyShootoutActionArraysThroughTheZeroNetworkImportPath()
            throws IOException {
        J5LocalJsonImportProcessingPlan prepared = processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"statistics\":[]}"),
                payload(fixture("incidents-terminal-shootout-empty-actions.json")),
                payload("{\"confirmed\":false}"));

        J5LocalJsonImportProcessingResult result = processor.execute(prepared, () -> true);

        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(result.endpoints().get(1).completenessStatus())
                .isEqualTo(J5CompletenessStatus.PARTIAL);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    assertThat(incidents.incidents().getFirst().minute()).isEmpty();
                    assertThat(incidents.incidents().get(1).minute()).isEmpty();
                    assertThat(incidents.incidents().get(2).minute()).isEmpty();
                });
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::acquisitionMode)
                .containsOnly(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
    }

    @Test
    void acceptsTheLiveExtraTimeMarkerThroughTheZeroNetworkImportPath() {
        J5LocalJsonImportProcessingPlan prepared = processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"statistics\":[]}"),
                payload("""
                        {"incidents":[{
                          "incidentType":"period","text":"Extra time","isLive":true,
                          "time":120,"addedTime":999,"periodTimeSeconds":900,
                          "homeScore":1,"awayScore":1
                        }]}
                        """),
                payload("{\"confirmed\":false}"));

        J5LocalJsonImportProcessingResult result = processor.execute(prepared, () -> true);

        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(result.endpoints().get(1).endpointType())
                .isEqualTo(SofascoreEndpointType.EVENT_INCIDENTS);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV17Parser.PARSER_VERSION);
        assertThat(observations.get(1).data())
                .isInstanceOfSatisfying(
                        com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents.class,
                        incidents -> assertThat(incidents.incidents().getFirst().periodText())
                                .contains("Extra time"));
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::acquisitionMode)
                .containsOnly(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
    }

    @Test
    void reclassifiesEveryDeduplicatedSnapshotAfterNormalization() {
        AtomicLong snapshotIds = new AtomicLong(900L);
        doAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            long snapshotId = snapshotIds.incrementAndGet();
            return new RawSnapshotPersistenceResult(
                    snapshotId,
                    RawSnapshotPersistenceOutcome.DEDUPLICATED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(snapshotId + 1000L));
        }).when(rawStore).save(any());

        J5LocalJsonImportProcessingResult result = processor.execute(
                prepareValid(), () -> true);

        assertThat(result.localJsonImports()).isEqualTo(3);
        verify(rawStore).classify(901L, RawSnapshotSchemaStatus.PARSED, null);
        verify(rawStore).classify(902L, RawSnapshotSchemaStatus.PARSED, null);
        verify(rawStore).classify(903L, RawSnapshotSchemaStatus.PARSED, null);
    }

    @Test
    void failsClosedWhenADeduplicatedSnapshotCannotBeReclassified() {
        doAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            return new RawSnapshotPersistenceResult(
                    950L,
                    RawSnapshotPersistenceOutcome.DEDUPLICATED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(951L));
        }).when(rawStore).save(any());
        doAnswer(invocation -> {
            throw new IllegalStateException("classification unavailable");
        }).when(rawStore).classify(anyLong(), any(), any());

        assertThatThrownBy(() -> processor.execute(prepareValid(), () -> true))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo("RAW_CLASSIFICATION_ERROR");
                            assertThat(exception.localJsonImports()).isEqualTo(1);
                            assertThat(exception.endpoints()).isEmpty();
                        });

        verify(dataStore, times(1)).save(any());
    }

    @Test
    void acceptsOnlyTheClosed404EnvelopeAsUnavailable() {
        J5LocalJsonImportProcessingPlan prepared = processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"error\":{\"code\":404,\"reason\":\"Not Found\"}}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}"));

        J5LocalJsonImportProcessingResult result = processor.execute(prepared, () -> true);

        assertThat(result.endpoints().getFirst().completenessStatus())
                .isEqualTo(J5CompletenessStatus.UNAVAILABLE);
        assertThat(rawSnapshots).extracting(RawManualCallSnapshot::httpStatus)
                .containsExactly(404, 200, 200);
        verify(rawStore).classify(
                401L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);

        assertThatThrownBy(() -> processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"error\":{\"code\":404,\"detail\":\"Not Found\"}}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}")))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("STATISTICS_PAYLOAD_INCOMPATIBLE"));

        assertThatThrownBy(() -> processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"error\":{\"code\":4294967700}}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}")))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("STATISTICS_PAYLOAD_INCOMPATIBLE"));
    }

    @Test
    void persistsTheCanonicalOperator404DeclarationAsUnavailableLocalJson() {
        J5LocalJsonImportProcessingPlan prepared = processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                J5LocalUnavailableEvidence.declared404(),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}"));

        J5LocalJsonImportProcessingResult result = processor.execute(prepared, () -> true);

        assertThat(result.endpoints().getFirst().completenessStatus())
                .isEqualTo(J5CompletenessStatus.UNAVAILABLE);
        assertThat(rawSnapshots.getFirst().httpStatus()).isEqualTo(404);
        assertThat(rawSnapshots.getFirst().acquisitionMode())
                .isEqualTo(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(new String(
                rawSnapshots.getFirst().payload().bytes(),
                StandardCharsets.UTF_8))
                .contains(J5LocalUnavailableEvidence.OPERATOR_MARKER);
        verify(rawStore).classify(
                401L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);
    }

    @Test
    void executesTheFullPositiveLongDomainWithoutUsingTheNetworkResponseContract() {
        CanonicalEventIdentity maximumIdentity =
                CanonicalEventIdentity.sofascore(Long.MAX_VALUE);
        CanonicalEventObservationView event = mock(CanonicalEventObservationView.class);
        when(event.identity()).thenReturn(maximumIdentity);
        when(canonicalStore.findLatestByCanonicalId(maximumIdentity.value()))
                .thenReturn(Optional.of(event));

        J5LocalJsonImportProcessingPlan prepared = processor.prepare(
                maximumIdentity.value(),
                Long.MAX_VALUE,
                payload("{\"statistics\":[]}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}"));
        J5LocalJsonImportProcessingResult result = processor.execute(
                prepared, () -> true);

        assertThat(prepared.eventId()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::requestKey)
                .containsExactly(
                        "EVENT_STATISTICS|eventId=" + Long.MAX_VALUE,
                        "EVENT_INCIDENTS|eventId=" + Long.MAX_VALUE,
                        "EVENT_LINEUPS|eventId=" + Long.MAX_VALUE);
        assertThat(observations)
                .extracting(observation -> observation.identity().providerEventId())
                .containsOnly(Long.MAX_VALUE);
    }

    @Test
    void rejectsAnIncompatibleBodyBeforeAnyWriteOrIdentityLookup() {
        assertThatThrownBy(() -> processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"statistics\":[]}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":true}")))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo("LINEUPS_PAYLOAD_INCOMPATIBLE");
                            assertThat(exception.localJsonImports()).isZero();
                            assertThat(exception.endpoints()).isEmpty();
                        });

        verifyNoInteractions(rawStore, dataStore, canonicalStore);
    }

    @Test
    void revalidatesTheCanonicalIdentityBeforeTheFirstWrite() {
        J5LocalJsonImportProcessingPlan prepared = prepareValid();
        when(canonicalStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.execute(prepared, () -> true))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo("EVENT_ID_MISMATCH");
                            assertThat(exception.localJsonImports()).isZero();
                            assertThat(exception.endpoints()).isEmpty();
                        });

        verifyNoInteractions(rawStore, dataStore);
    }

    @Test
    void reportsTheImportCountAndCompletedEndpointsWhenPersistenceFails() {
        J5LocalJsonImportProcessingPlan prepared = prepareValid();
        AtomicInteger saves = new AtomicInteger();
        List<SofascoreEndpointType> reachedEndpoints = new ArrayList<>();
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 2) {
                throw new IllegalStateException("database unavailable");
            }
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            return new RawSnapshotPersistenceResult(
                    700L,
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(701L));
        }).when(rawStore).save(any());

        assertThatThrownBy(() -> processor.execute(
                prepared,
                () -> true,
                reachedEndpoints::add,
                ignored -> { },
                (endpoint, raw) -> { }))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo("RAW_PERSISTENCE_ERROR");
                            assertThat(exception.localJsonImports()).isEqualTo(1);
                            assertThat(exception.endpoints())
                                    .extracting(J5RealEndpointResult::endpointType)
                                    .containsExactly(SofascoreEndpointType.EVENT_STATISTICS);
                        });
        assertThat(reachedEndpoints).containsExactly(
                SofascoreEndpointType.EVENT_STATISTICS,
                SofascoreEndpointType.EVENT_INCIDENTS);
    }

    @Test
    void stopsBetweenEndpointsWithStructuredPartialResults() {
        J5LocalJsonImportProcessingPlan prepared = prepareValid();
        AtomicInteger checks = new AtomicInteger();

        assertThatThrownBy(() -> processor.execute(
                prepared, () -> checks.incrementAndGet() <= 2))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo("OPERATOR_STOP");
                            assertThat(exception.localJsonImports()).isEqualTo(1);
                            assertThat(exception.endpoints())
                                    .extracting(J5RealEndpointResult::endpointType)
                                    .containsExactly(SofascoreEndpointType.EVENT_STATISTICS);
                        });

        verify(rawStore, times(1)).save(any());
        verify(dataStore, times(1)).save(any());
    }

    @Test
    void rejectsACombinedPayloadAboveTheExplicitFifteenMibLimit() {
        RawPayloadEvidence oversized = mock(RawPayloadEvidence.class);
        when(oversized.sizeBytes()).thenReturn(RawPayloadEvidence.MAXIMUM_BYTES + 1);

        assertThatThrownBy(() -> processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                oversized,
                oversized,
                oversized))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportProcessingException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("TOTAL_PAYLOAD_TOO_LARGE"));

        verify(oversized, never()).bytes();
        verifyNoInteractions(rawStore, dataStore, canonicalStore);
    }

    private J5LocalJsonImportProcessingPlan prepareValid() {
        return processor.prepare(
                IDENTITY.value(),
                EVENT_ID,
                payload("{\"statistics\":[]}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}"));
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private String fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/provider-j5/" + name)) {
            return new String(
                    java.util.Objects.requireNonNull(input).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }
}
