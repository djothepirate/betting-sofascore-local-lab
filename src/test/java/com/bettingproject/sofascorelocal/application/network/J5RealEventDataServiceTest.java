package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV3Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportFailure;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataProviderTransport;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J5RealEventDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T14:00:00Z");
    private static final long EVENT_ID = 16391135L;
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(EVENT_ID);
    private static final UUID REQUEST_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000007");

    private J5RealControlService control;
    private J5EventDataProviderTransport transport;
    private RawManualCallSnapshotStore rawStore;
    private CanonicalEventStore canonicalStore;
    private J5EventDataStore dataStore;
    private List<String> operations;
    private List<J5EventDataObservation> observations;
    private List<Duration> pauses;
    private J5RealEventDataService service;

    @BeforeEach
    void setUp() {
        control = mock(J5RealControlService.class);
        transport = mock(J5EventDataProviderTransport.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        canonicalStore = mock(CanonicalEventStore.class);
        dataStore = mock(J5EventDataStore.class);
        CanonicalEventObservationView view = mock(CanonicalEventObservationView.class);
        when(view.identity()).thenReturn(IDENTITY);
        when(canonicalStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(view));
        when(control.executionMayContinue(any())).thenReturn(true);

        operations = new ArrayList<>();
        observations = new ArrayList<>();
        pauses = new ArrayList<>();
        AtomicLong snapshotIds = new AtomicLong(100L);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            operations.add("raw:" + snapshot.endpointType());
            return new RawSnapshotPersistenceResult(
                    snapshotIds.incrementAndGet(),
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        });
        doAnswer(invocation -> {
            operations.add("classify:" + invocation.getArgument(1));
            return null;
        }).when(rawStore).classify(anyLong(), any(), any());
        AtomicLong observationIds = new AtomicLong(200L);
        when(dataStore.save(any())).thenAnswer(invocation -> {
            J5EventDataObservation observation = invocation.getArgument(0);
            observations.add(observation);
            operations.add("normalized:" + observation.data().endpointType());
            return new J5EventDataPersistenceResult(
                    observationIds.incrementAndGet(),
                    IDENTITY.value(),
                    observation.data().endpointType(),
                    true);
        });

        AtomicReference<Instant> time = new AtomicReference<>(NOW);
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return time.get();
            }
        };
        service = new J5RealEventDataService(
                control,
                transport,
                rawStore,
                canonicalStore,
                dataStore,
                new EventStatisticsV2Parser(),
                new EventIncidentsV3Parser(),
                new EventLineupsV2Parser(),
                clock,
                Duration.ofSeconds(3),
                delay -> {
                    pauses.add(delay);
                    time.set(time.get().plus(delay));
                });
    }

    @Test
    void persistsAStatistics404AsUnavailableThenContinuesTheOrderedCampaign()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            if (request.endpointType() == SofascoreEndpointType.EVENT_STATISTICS) {
                return response(request, 404, "{\"error\":\"statistics unavailable\"}");
            }
            return response(request, 200, fixtureFor(request.endpointType()));
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::completenessStatus)
                .containsExactly(
                        J5CompletenessStatus.UNAVAILABLE,
                        J5CompletenessStatus.COMPLETE,
                        J5CompletenessStatus.COMPLETE);
        assertThat(result.endpoints().getFirst().completenessLabel())
                .isEqualTo("UNAVAILABLE · N/A");
        assertThat(observations.getFirst().data()).isInstanceOfSatisfying(
                EventStatistics.class,
                statistics -> assertThat(statistics.metrics()).isEmpty());
        assertThat(observations.getFirst().completeness().status())
                .isEqualTo(J5CompletenessStatus.UNAVAILABLE);
        assertThat(operations).containsExactly(
                "transport:EVENT_STATISTICS",
                "raw:EVENT_STATISTICS",
                "normalized:EVENT_STATISTICS",
                "classify:ENDPOINT_UNAVAILABLE",
                "transport:EVENT_INCIDENTS",
                "raw:EVENT_INCIDENTS",
                "normalized:EVENT_INCIDENTS",
                "classify:PARSED",
                "transport:EVENT_LINEUPS",
                "raw:EVENT_LINEUPS",
                "normalized:EVENT_LINEUPS",
                "classify:PARSED");
        verify(rawStore).classify(
                101L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);
        verify(transport, times(3)).execute(any());
        verify(control, times(3)).recordEndpointCompleted(any(), any());
        verify(control).complete(REQUEST_ID);
        assertThat(pauses).containsExactly(
                Duration.ofSeconds(3),
                Duration.ofSeconds(3));
    }

    @Test
    void executesExactlyThreeOrderedCallsAndPersistsRawBeforeEachNormalization()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            return response(request, 200, fixtureFor(request.endpointType()));
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints()).extracting(J5RealEndpointResult::endpointType)
                .containsExactlyElementsOf(J5RealControlService.ORDERED_ENDPOINTS);
        assertThat(pauses).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
        assertThat(operations).containsExactly(
                "transport:EVENT_STATISTICS",
                "raw:EVENT_STATISTICS",
                "normalized:EVENT_STATISTICS",
                "classify:PARSED",
                "transport:EVENT_INCIDENTS",
                "raw:EVENT_INCIDENTS",
                "normalized:EVENT_INCIDENTS",
                "classify:PARSED",
                "transport:EVENT_LINEUPS",
                "raw:EVENT_LINEUPS",
                "normalized:EVENT_LINEUPS",
                "classify:PARSED");
        verify(transport, times(3)).execute(any());
        verify(control, times(3)).recordEndpointCompleted(any(), any());
        verify(control).complete(REQUEST_ID);
    }

    @Test
    void stopsAtTheFirstHttpIncidentWithoutRetryOrLaterEndpoint() throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            return response(request, 429, "{\"error\":\"rate-limited\"}");
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("HTTP_429");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.endpoints()).isEmpty();
        verify(transport, times(1)).execute(any());
        verify(dataStore, never()).save(any());
        verify(control).fail(REQUEST_ID, "HTTP_429");
    }

    @Test
    void retainsTheFirstFamilyButDoesNotNormalizeAnIncompatibleSecondFamily()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? "{}"
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("SCHEMA_INCOMPATIBLE");
        assertThat(result.providerCallAttempts()).isEqualTo(2);
        assertThat(result.endpoints()).singleElement()
                .extracting(J5RealEndpointResult::endpointType)
                .isEqualTo(SofascoreEndpointType.EVENT_STATISTICS);
        verify(transport, times(2)).execute(any());
        verify(dataStore, times(1)).save(any());
        verify(control).fail(REQUEST_ID, "SCHEMA_INCOMPATIBLE");
    }

    @Test
    void rejectsAnIdentityMissingFromTheLocalCanonicalStoreBeforeTransport() {
        when(canonicalStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.empty());

        var result = service.execute(claim());

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("EVENT_ID_MISMATCH");
        assertThat(result.providerCallAttempts()).isZero();
        verify(transport, never()).execute(any());
        verify(rawStore, never()).save(any());
        verify(dataStore, never()).save(any());
        verify(control).fail(REQUEST_ID, "EVENT_ID_MISMATCH");
    }

    @Test
    void stopsAfterOneTimedOutAttemptWithoutRetryOrRawFabrication() {
        when(transport.execute(any())).thenThrow(new J5EventDataTransportException(
                J5EventDataTransportFailure.TIMEOUT));

        var result = service.execute(claim());

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("TRANSPORT_TIMEOUT");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.endpoints()).isEmpty();
        verify(transport, times(1)).execute(any());
        verify(rawStore, never()).save(any());
        verify(dataStore, never()).save(any());
        verify(control).fail(REQUEST_ID, "TRANSPORT_TIMEOUT");
    }

    private static J5RealExecutionClaim claim() {
        return new J5RealExecutionClaim(
                REQUEST_ID,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                IDENTITY.value(),
                EVENT_ID);
    }

    private static J5EventDataTransportResponse response(
            J5EventDataProviderRequest request,
            int status,
            String body) {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new J5EventDataTransportResponse(
                request.endpointType(),
                request.requestKey(),
                NOW,
                NOW.plusMillis(100),
                status,
                "application/json; charset=utf-8",
                Duration.ofMillis(100),
                payload);
    }

    private String fixtureFor(SofascoreEndpointType endpoint) throws IOException {
        String name = switch (endpoint) {
            case EVENT_STATISTICS -> "statistics-nominal.json";
            case EVENT_INCIDENTS -> "incidents-provider-period-markers.json";
            case EVENT_LINEUPS -> "lineups-nominal.json";
            default -> throw new IllegalArgumentException("unsupported endpoint");
        };
        try (var input = getClass().getResourceAsStream("/fixtures/provider-j5/" + name)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
