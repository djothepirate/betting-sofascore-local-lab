package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV13Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlState;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

class J5LocalJsonImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T07:00:00Z");
    private static final long EVENT_ID = 16310922L;
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(EVENT_ID);
    private static final UUID REQUEST_ID = UUID.fromString(
            "90000000-0000-0000-0000-000000000009");
    private static final String CONFIRMATION =
            "CONFIRMER J5 REAL 16310922 STATISTICS INCIDENTS LINEUPS 123456";

    private J5RealControlService control;
    private RawManualCallSnapshotStore rawStore;
    private CanonicalEventStore canonicalStore;
    private J5EventDataStore dataStore;
    private List<RawManualCallSnapshot> rawSnapshots;
    private List<J5EventDataObservation> observations;
    private J5LocalJsonImportService service;

    @BeforeEach
    void setUp() {
        control = mock(J5RealControlService.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        canonicalStore = mock(CanonicalEventStore.class);
        dataStore = mock(J5EventDataStore.class);

        when(control.snapshot()).thenReturn(pending());
        when(control.confirmAndClaim(REQUEST_ID, CONFIRMATION, true)).thenReturn(claim());
        when(control.executionMayContinue(REQUEST_ID)).thenReturn(true);

        CanonicalEventObservationView event = mock(CanonicalEventObservationView.class);
        when(event.identity()).thenReturn(IDENTITY);
        when(canonicalStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(event));

        rawSnapshots = new ArrayList<>();
        AtomicLong snapshotIds = new AtomicLong(400L);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            rawSnapshots.add(snapshot);
            return new RawSnapshotPersistenceResult(
                    snapshotIds.incrementAndGet(),
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        });

        observations = new ArrayList<>();
        AtomicLong observationIds = new AtomicLong(500L);
        when(dataStore.save(any())).thenAnswer(invocation -> {
            J5EventDataObservation observation = invocation.getArgument(0);
            observations.add(observation);
            return new J5EventDataPersistenceResult(
                    observationIds.incrementAndGet(),
                    IDENTITY.value(),
                    observation.data().endpointType(),
                    true);
        });
        doAnswer(invocation -> null)
                .when(rawStore).classify(anyLong(), any(), any());

        service = new J5LocalJsonImportService(
                control,
                rawStore,
                canonicalStore,
                dataStore,
                new EventStatisticsV2Parser(),
                new EventIncidentsV13Parser(),
                new EventLineupsV2Parser(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void importsThreeValidatedBodiesInOrderWithoutAnyProviderCall() {
        J5RealCampaignResult result = service.importCampaign(
                IDENTITY.value(),
                REQUEST_ID,
                CONFIRMATION,
                true,
                payload("{\"statistics\":[]}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}"));

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isZero();
        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactlyElementsOf(J5RealControlService.ORDERED_ENDPOINTS);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::completenessStatus)
                .containsExactly(
                        J5CompletenessStatus.EMPTY_VALID,
                        J5CompletenessStatus.EMPTY_VALID,
                        J5CompletenessStatus.EMPTY_VALID);
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::acquisitionMode)
                .containsOnly(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::endpointType)
                .containsExactlyElementsOf(J5RealControlService.ORDERED_ENDPOINTS);
        assertThat(rawSnapshots)
                .extracting(RawManualCallSnapshot::httpStatus)
                .containsOnly(200);
        assertThat(observations).hasSize(3);
        assertThat(observations)
                .allSatisfy(observation -> assertThat(observation.source().snapshotId())
                        .isPresent());
        verify(control).confirmAndClaim(REQUEST_ID, CONFIRMATION, true);
        verify(control, times(3)).recordEndpointCompleted(any(), any());
        verify(control).complete(REQUEST_ID);
    }

    @Test
    void keepsAnExact404EnvelopeAsUnavailableThenContinuesTheImport() {
        J5RealCampaignResult result = service.importCampaign(
                IDENTITY.value(),
                REQUEST_ID,
                CONFIRMATION,
                true,
                payload("{\"error\":{\"code\":404,\"reason\":\"Not Found\"}}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}"));

        assertThat(result.completed()).isTrue();
        assertThat(result.providerCallAttempts()).isZero();
        assertThat(result.localJsonImports()).isEqualTo(3);
        assertThat(result.endpoints().getFirst().completenessStatus())
                .isEqualTo(J5CompletenessStatus.UNAVAILABLE);
        assertThat(rawSnapshots).extracting(RawManualCallSnapshot::httpStatus)
                .containsExactly(404, 200, 200);
        verify(rawStore).classify(
                401L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);
        verify(control).complete(REQUEST_ID);
    }

    @Test
    void rejectsAnIncompatibleThirdBodyBeforeConsumingTheConfirmation() {
        assertThatThrownBy(() -> service.importCampaign(
                IDENTITY.value(),
                REQUEST_ID,
                CONFIRMATION,
                true,
                payload("{\"statistics\":[]}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":true}")))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(J5LocalJsonImportError.LINEUPS_PAYLOAD_INCOMPATIBLE));

        verify(control, never()).confirmAndClaim(any(), any(), any(Boolean.class));
        verifyNoInteractions(rawStore, dataStore, canonicalStore);
    }

    @Test
    void refusesA403EnvelopeInsteadOfTreatingItAsAnUnavailableFamily() {
        assertThatThrownBy(() -> service.importCampaign(
                IDENTITY.value(),
                REQUEST_ID,
                CONFIRMATION,
                true,
                payload("{\"error\":{\"code\":403,\"reason\":\"Forbidden\"}}"),
                payload("{\"incidents\":[]}"),
                payload("{\"confirmed\":false}")))
                .isInstanceOfSatisfying(
                        J5LocalJsonImportException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(J5LocalJsonImportError.STATISTICS_PAYLOAD_INCOMPATIBLE));

        verify(control, never()).confirmAndClaim(any(), any(), any(Boolean.class));
        verifyNoInteractions(rawStore, dataStore, canonicalStore);
    }

    private static J5RealControlSnapshot pending() {
        return new J5RealControlSnapshot(
                J5RealControlState.AWAITING_CONFIRMATION,
                NOW,
                REQUEST_ID,
                CONFIRMATION,
                NOW,
                NOW.plusSeconds(300),
                IDENTITY.value(),
                EVENT_ID,
                List.of(),
                null,
                true,
                List.of());
    }

    private static J5RealExecutionClaim claim() {
        return new J5RealExecutionClaim(
                REQUEST_ID,
                URI.create("https://www.sofascore.com"),
                IDENTITY.value(),
                EVENT_ID);
    }

    private static RawPayloadEvidence payload(String json) {
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }
}
