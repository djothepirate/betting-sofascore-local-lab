package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV13Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportFailure;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
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
        doAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            operations.add("raw:" + snapshot.endpointType());
            return new RawSnapshotPersistenceResult(
                    snapshotIds.incrementAndGet(),
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        }).when(rawStore).save(any());
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
                new EventIncidentsV13Parser(),
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
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    var cardWithoutReason = incidents.incidents().get(1);
                    assertThat(cardWithoutReason.incidentType()).isEqualTo("card");
                    assertThat(cardWithoutReason.reason()).isEmpty();
                    assertThat(cardWithoutReason.motifLabel()).isEqualTo("—");
                });
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
    void continuesToLineupsAfterReparsingDeduplicatedHistoricalIncidentEvidence()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            if (request.endpointType() == SofascoreEndpointType.EVENT_STATISTICS) {
                return response(request, 404, "{\"error\":\"statistics unavailable\"}");
            }
            return response(request, 200, fixtureFor(request.endpointType()));
        });
        doAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            operations.add("raw:" + snapshot.endpointType());
            long snapshotId = switch (snapshot.endpointType()) {
                case EVENT_STATISTICS -> 30L;
                case EVENT_INCIDENTS -> 32L;
                case EVENT_LINEUPS -> 35L;
                default -> throw new IllegalArgumentException("unsupported J5 endpoint");
            };
            RawSnapshotPersistenceOutcome outcome =
                    snapshot.endpointType() == SofascoreEndpointType.EVENT_LINEUPS
                            ? RawSnapshotPersistenceOutcome.INSERTED
                            : RawSnapshotPersistenceOutcome.DEDUPLICATED;
            return new RawSnapshotPersistenceResult(
                    snapshotId,
                    outcome,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes());
        }).when(rawStore).save(any());
        doAnswer(invocation -> {
            long snapshotId = invocation.getArgument(0);
            if (snapshotId == 30L || snapshotId == 32L) {
                throw new IllegalStateException(
                        "historical raw classification must not be rewritten");
            }
            operations.add("classify:" + invocation.getArgument(1));
            return null;
        }).when(rawStore).classify(anyLong(), any(), any());

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::snapshotId)
                .containsExactly(30L, 32L, 35L);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents.class,
                incidents -> {
                    var substitution = incidents.incidents().get(2);
                    assertThat(substitution.playerInName())
                            .contains("Synthetic Incoming Player");
                    assertThat(substitution.playerOutName())
                            .contains("Synthetic Outgoing Player");
                });
        assertThat(operations).containsExactly(
                "transport:EVENT_STATISTICS",
                "raw:EVENT_STATISTICS",
                "normalized:EVENT_STATISTICS",
                "transport:EVENT_INCIDENTS",
                "raw:EVENT_INCIDENTS",
                "normalized:EVENT_INCIDENTS",
                "transport:EVENT_LINEUPS",
                "raw:EVENT_LINEUPS",
                "normalized:EVENT_LINEUPS",
                "classify:PARSED");
        verify(rawStore, never()).classify(
                30L, RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE, null);
        verify(rawStore, never()).classify(
                32L, RawSnapshotSchemaStatus.PARSED, null);
        verify(rawStore).classify(35L, RawSnapshotSchemaStatus.PARSED, null);
        verify(transport, times(3)).execute(any());
        verify(control, times(3)).recordEndpointCompleted(any(), any());
        verify(control).complete(REQUEST_ID);
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
        assertThat(result.localJsonImports()).isZero();
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
    void continuesToLineupsAfterNormalizingAProviderBenchIncident()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? """
                            {"incidents":[{
                              "incidentType":"card",
                              "incidentClass":"yellow",
                              "time":-5,
                              "benchTime":58,
                              "reversedPeriodTime":6,
                              "isHome":false,
                              "reason":"Argument",
                              "player":{"id":1053241,"name":"Provider Player"}
                            }]}
                            """
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(result.endpoints().get(1).warningCount()).isPositive();
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    var incident = incidents.incidents().getFirst();
                    assertThat(incident.minute()).contains(58);
                    assertThat(incident.incidentClass()).contains("yellow");
                    assertThat(incident.reason()).contains("Argument");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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
    void continuesToLineupsAfterNormalizingAnInjuryClassSubstitution()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? """
                            {"incidents":[{
                              "incidentType":"substitution",
                              "incidentClass":"injury",
                              "time":46,
                              "isHome":false,
                              "injury":true,
                              "playerIn":{"id":864921,"name":"Jack Grealish"},
                              "playerOut":{"id":851005,"name":"Jérémy Doku"}
                            }]}
                            """
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    var substitution = incidents.incidents().getFirst();
                    assertThat(substitution.incidentClass()).contains("injury");
                    assertThat(substitution.injury()).contains(true);
                    assertThat(substitution.playerInName()).contains("Jack Grealish");
                    assertThat(substitution.playerOutName()).contains("Jérémy Doku");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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
    void continuesToLineupsAfterNormalizingTheShootoutSentinelAndBothWoodworkFamilies()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? v8IncidentFixture()
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    assertThat(incidents.incidents()).hasSize(3);
                    assertThat(incidents.incidents().getFirst().periodText()).contains("PEN");
                    assertThat(incidents.incidents().getFirst().minute()).contains(146);
                    assertThat(incidents.incidents().get(1).incidentType())
                            .isEqualTo("penaltyShootout");
                    assertThat(incidents.incidents().get(1).reason()).contains("woodwork");
                    assertThat(incidents.incidents().get(2).incidentType())
                            .isEqualTo("inGamePenalty");
                    assertThat(incidents.incidents().get(2).reason()).contains("woodwork");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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
    void continuesToLineupsAfterAnUnavailableStatisticsFamilyAndAnUnminutedTerminalShootout()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            if (request.endpointType() == SofascoreEndpointType.EVENT_STATISTICS) {
                return response(request, 404, "{\"error\":\"statistics unavailable\"}");
            }
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? v12UnminutedShootoutFixture()
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::completenessStatus)
                .containsExactly(
                        J5CompletenessStatus.UNAVAILABLE,
                        J5CompletenessStatus.PARTIAL,
                        J5CompletenessStatus.COMPLETE);
        assertThat(result.endpoints().get(1).warningCount()).isGreaterThanOrEqualTo(3);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    assertThat(incidents.incidents()).hasSize(4);
                    var marker = incidents.incidents().getFirst();
                    var scored = incidents.incidents().get(1);
                    var missed = incidents.incidents().get(2);
                    assertThat(marker.periodText()).contains("PEN");
                    assertThat(marker.minute()).isEmpty();
                    assertThat(marker.minuteLabel()).isEqualTo("—");
                    assertThat(scored.minute()).isEmpty();
                    assertThat(scored.shootoutSequence()).contains(2);
                    assertThat(missed.minute()).isEmpty();
                    assertThat(missed.incidentClass()).contains("missed");
                    assertThat(missed.reason()).isEmpty();
                    assertThat(missed.description()).isEmpty();
                    assertThat(missed.motifLabel()).isEqualTo("—");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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
        verify(transport, times(3)).execute(any());
        verify(control, times(3)).recordEndpointCompleted(any(), any());
        verify(control).complete(REQUEST_ID);
        assertThat(pauses).containsExactly(
                Duration.ofSeconds(3),
                Duration.ofSeconds(3));
    }

    @Test
    void continuesToLineupsAfterNormalizingTheObservedRegularGoalOrigin()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? """
                            {"incidents":[{
                              "incidentType":"goal","incidentClass":"regular",
                              "from":"regular","time":16,"isHome":false,
                              "player":{"id":914501,"name":"Goal Scorer"},
                              "homeScore":0,"awayScore":1
                            }]}
                            """
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(result.endpoints().get(1).warningCount()).isEqualTo(1);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    var goal = incidents.incidents().getFirst();
                    assertThat(goal.incidentClass()).contains("regular");
                    assertThat(goal.goalOrigin()).isEmpty();
                    assertThat(goal.playerName()).contains("Goal Scorer");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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
    void continuesToLineupsAfterAcceptingTheObservedOffTheBallCardReason()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? """
                            {"incidents":[{
                              "player":{"id":877400,"name":"Facundo Mallo"},
                              "playerName":"Facundo Mallo",
                              "reason":"Off the ball foul","rescinded":false,
                              "id":125440215,"time":77,"isHome":false,
                              "incidentClass":"yellow","incidentType":"card",
                              "reversedPeriodTime":14
                            }]}
                            """
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    var card = incidents.incidents().getFirst();
                    assertThat(card.playerName()).contains("Facundo Mallo");
                    assertThat(card.incidentClass()).contains("yellow");
                    assertThat(card.reason()).contains("Off the ball foul");
                    assertThat(card.motifLabel()).isEqualTo("Off the ball foul");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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
    void continuesToLineupsAfterAcceptingTheObservedLeavingFieldCardReason()
            throws Exception {
        when(transport.execute(any())).thenAnswer(invocation -> {
            J5EventDataProviderRequest request = invocation.getArgument(0);
            operations.add("transport:" + request.endpointType());
            String body = request.endpointType() == SofascoreEndpointType.EVENT_INCIDENTS
                    ? """
                            {"incidents":[{
                              "player":{"id":817886,"name":"Yongjing Cao"},
                              "playerName":"Yongjing Cao",
                              "reason":"Leaving field","rescinded":false,
                              "id":125444628,"time":74,"isHome":false,
                              "incidentClass":"yellow","incidentType":"card",
                              "reversedPeriodTime":17
                            }]}
                            """
                    : fixtureFor(request.endpointType());
            return response(request, 200, body);
        });

        var result = service.execute(claim());

        assertThat(result.completed()).isTrue();
        assertThat(result.terminalCode()).isEqualTo("COMPLETED");
        assertThat(result.providerCallAttempts()).isEqualTo(3);
        assertThat(result.endpoints())
                .extracting(J5RealEndpointResult::endpointType)
                .containsExactly(
                        SofascoreEndpointType.EVENT_STATISTICS,
                        SofascoreEndpointType.EVENT_INCIDENTS,
                        SofascoreEndpointType.EVENT_LINEUPS);
        assertThat(observations.get(1).source().parserVersion())
                .isEqualTo(EventIncidentsV13Parser.PARSER_VERSION);
        assertThat(observations.get(1).data()).isInstanceOfSatisfying(
                EventIncidents.class,
                incidents -> {
                    var card = incidents.incidents().getFirst();
                    assertThat(card.playerName()).contains("Yongjing Cao");
                    assertThat(card.incidentClass()).contains("yellow");
                    assertThat(card.reason()).contains("Leaving field");
                    assertThat(card.motifLabel()).isEqualTo("Leaving field");
                });
        assertThat(observations.get(2).data()).isInstanceOf(EventLineups.class);
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

    private static String v8IncidentFixture() {
        return """
                {"incidents":[
                  {
                    "incidentType":"period",
                    "text":"PEN",
                    "period":"penalties",
                    "time":999,
                    "addedTime":999,
                    "isLive":false,
                    "homeScore":3,
                    "awayScore":2
                  },
                  {
                    "incidentType":"penaltyShootout",
                    "incidentClass":"missed",
                    "isHome":false,
                    "player":{"id":301,"name":"Shootout Taker"},
                    "homeScore":3,
                    "awayScore":2,
                    "reason":"woodwork",
                    "description":"Woodwork",
                    "sequence":9,
                    "footballPassingNetworkAction":[{"time":146}]
                  },
                  {
                    "incidentType":"inGamePenalty",
                    "incidentClass":"missed",
                    "time":58,
                    "isHome":true,
                    "player":{"id":302,"name":"Penalty Taker"},
                    "reason":"woodwork",
                    "description":"Woodwork"
                  }
                ]}
                """;
    }

    private static String v12UnminutedShootoutFixture() {
        return """
                {"incidents":[
                  {
                    "text":"PEN","homeScore":2,"awayScore":1,"isLive":false,
                    "period":"penalties","time":999,"addedTime":999,
                    "incidentType":"period"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"scored",
                    "isHome":true,"player":{"id":302,"name":"Scoring Taker"},
                    "homeScore":2,"awayScore":1,"sequence":2,
                    "description":"Scored","reason":"scored"
                  },
                  {
                    "incidentType":"penaltyShootout","incidentClass":"missed",
                    "isHome":false,"player":{"id":301,"name":"Missing Taker"},
                    "homeScore":1,"awayScore":1,"sequence":1
                  },
                  {
                    "text":"FT","homeScore":1,"awayScore":1,"isLive":false,
                    "time":90,"addedTime":999,"incidentType":"period"
                  }
                ]}
                """;
    }
}
