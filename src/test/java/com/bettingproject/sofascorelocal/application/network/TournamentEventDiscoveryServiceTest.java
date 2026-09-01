package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents.TournamentScheduledEventsParseEvidence;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportFailure;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalEventPersistenceService;
import com.bettingproject.sofascorelocal.application.event.TournamentCanonicalizationResult;
import com.bettingproject.sofascorelocal.application.event.TournamentDiscoveredEventView;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.CachedTournamentScheduledEventsResponse;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryLocalImportClaim;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoverySource;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoveryState;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsCache;
import com.bettingproject.sofascorelocal.port.TournamentScheduledEventsProviderTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentEventDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");
    private static final UUID REQUEST_ID =
            UUID.fromString("d9498338-4d8f-4127-9c16-276af31464be");

    private TournamentEventDiscoveryControlService control;
    private TournamentScheduledEventsProviderTransport transport;
    private TournamentScheduledEventsProviderTransport.Campaign providerCampaign;
    private TournamentScheduledEventsCache cache;
    private RawManualCallSnapshotStore rawStore;
    private TournamentCanonicalEventPersistenceService persistence;
    private TournamentEventDiscoveryService service;

    @BeforeEach
    void setUp() {
        control = mock(TournamentEventDiscoveryControlService.class);
        transport = mock(TournamentScheduledEventsProviderTransport.class);
        providerCampaign = mock(TournamentScheduledEventsProviderTransport.Campaign.class);
        cache = mock(TournamentScheduledEventsCache.class);
        rawStore = mock(RawManualCallSnapshotStore.class);
        persistence = mock(TournamentCanonicalEventPersistenceService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = serviceWith(
                new TournamentScheduledEventsV1Parser(),
                new TournamentScheduledEventsProjectionService(),
                clock);
        when(control.executionMayContinue(REQUEST_ID)).thenReturn(true);
        when(cache.findFreshParsed(
                any(), any(), eq(TournamentEventDiscoveryService.CACHE_TTL),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION)))
                .thenReturn(Optional.empty());
        when(transport.openCampaign(any())).thenReturn(providerCampaign);
        when(control.executeWhileActive(eq(REQUEST_ID), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(control.executeAndComplete(eq(REQUEST_ID), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    }

    @Test
    void persistsRawBeforeClassificationCacheAndCanonicalizationForOneProviderGet() {
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(11L);
        when(evidenceStore.startProviderAttempt(any())).thenReturn(12L);
        service = auditedService(evidenceStore, Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        var raw = persistenceResult(81, payload);
        when(transport.openCampaign(REQUEST_ID)).thenReturn(providerCampaign);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(raw);
        when(persistence.persist(any(), eq(81L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isTrue();
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.snapshotId()).isEqualTo(81);
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
        assertThat(result.events()).hasSize(1);
        verify(transport).openCampaign(REQUEST_ID);
        verify(providerCampaign).execute(any());
        verify(providerCampaign).close();
        InOrder auditOrder = inOrder(evidenceStore, providerCampaign);
        auditOrder.verify(evidenceStore).startCampaign(any());
        auditOrder.verify(evidenceStore).declareUnit(any());
        auditOrder.verify(evidenceStore).startProviderAttempt(any());
        auditOrder.verify(providerCampaign).execute(any());
        auditOrder.verify(providerCampaign).close();
        auditOrder.verify(evidenceStore).recordUnitResult(any());
        auditOrder.verify(evidenceStore).finishCampaign(any());
        ArgumentCaptor<J8BenchmarkUnitResult> auditResult =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(auditResult.capture());
        assertThat(auditResult.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.PROVIDER);
        assertThat(auditResult.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PARSED);
        ArgumentCaptor<J8BenchmarkCampaignResult> campaignResult =
                ArgumentCaptor.forClass(J8BenchmarkCampaignResult.class);
        verify(evidenceStore).finishCampaign(campaignResult.capture());
        assertThat(campaignResult.getValue().terminalState())
                .isEqualTo(J8BenchmarkCampaignTerminalState.COMPLETED);
        InOrder order = inOrder(rawStore, cache, persistence, control);
        order.verify(rawStore).save(any());
        order.verify(rawStore).classify(81, RawSnapshotSchemaStatus.PARSED, null);
        order.verify(cache).recordParsed(
                any(), eq(response), eq(raw),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION));
        order.verify(control).executeAndComplete(eq(REQUEST_ID), any());
        order.verify(persistence).persist(any(), eq(81L), eq(payload.sha256()), eq(NOW));
    }

    @Test
    void preservesRawResponseWhenCampaignCleanupFailsAfterProviderGet() {
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(21L);
        when(evidenceStore.startProviderAttempt(any())).thenReturn(22L);
        service = auditedService(evidenceStore, Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        var raw = persistenceResult(87, payload);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(raw);
        doThrow(new ScheduledEventsTransportException(
                ScheduledEventsTransportFailure.IO_FAILURE))
                .when(providerCampaign).close();

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("TRANSPORT_IO_FAILURE");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.snapshotId()).isEqualTo(87);
        assertThat(result.payloadSha256()).isEqualTo(payload.sha256());
        assertThat(result.payloadSizeBytes()).isEqualTo(payload.sizeBytes());
        InOrder order = inOrder(providerCampaign, rawStore);
        order.verify(providerCampaign).execute(any());
        order.verify(rawStore).save(any());
        order.verify(providerCampaign).close();
        verify(rawStore, never()).classify(anyLong(), any(), any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
        verify(evidenceStore).startProviderAttempt(any());
        ArgumentCaptor<J8BenchmarkUnitResult> auditResult =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(auditResult.capture());
        assertThat(auditResult.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PROCESSING_FAILURE);
        ArgumentCaptor<J8BenchmarkCampaignResult> campaignResult =
                ArgumentCaptor.forClass(J8BenchmarkCampaignResult.class);
        verify(evidenceStore).finishCampaign(campaignResult.capture());
        assertThat(campaignResult.getValue().terminalState())
                .isEqualTo(J8BenchmarkCampaignTerminalState.FAILED);
    }

    @Test
    void importsAResponseBodyLocallyWithDistinctProvenanceAndNoProviderOrCacheCall() {
        RawPayloadEvidence payload = payload(7);
        var raw = persistenceResult(90, payload);
        when(rawStore.save(any())).thenReturn(raw);
        when(persistence.persist(any(), eq(90L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());

        var result = service.importLocalJson(localImportClaim(Map.of(7200, 1)), payload);

        assertThat(result.completed()).isTrue();
        assertThat(result.source()).isEqualTo(
                TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
        assertThat(result.providerCallAttempts()).isZero();
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.snapshotId()).isEqualTo(90);
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_VERIFIED);
        assertThat(result.events()).hasSize(1);

        ArgumentCaptor<RawManualCallSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(RawManualCallSnapshot.class);
        verify(rawStore).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().acquisitionMode())
                .isEqualTo(RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT);
        assertThat(snapshotCaptor.getValue().requestKey())
                .isEqualTo(
                        "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7");
        assertThat(snapshotCaptor.getValue().httpStatus()).isEqualTo(200);
        assertThat(snapshotCaptor.getValue().latency()).isZero();
        assertThat(snapshotCaptor.getValue().payload()).isEqualTo(payload);
        verify(rawStore).classify(90, RawSnapshotSchemaStatus.PARSED, null);
        verify(transport, never()).openCampaign(any());
        verify(cache, never()).findFreshParsed(any(), any(), any(), any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence).persist(any(), eq(90L), eq(payload.sha256()), eq(NOW));
    }

    @Test
    void importsTournamentJsonWhenPlaywrightDefaultsAreDisabledAndWorkerJarIsEmpty() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog());
        SofascoreProperties sofascore = new SofascoreProperties();
        sofascore.setEnabled(true);
        sofascore.setJ3QualificationEnabled(true);
        sofascore.setTournamentEventDiscoveryEnabled(true);
        sofascore.setBaseUrl(EventDetailsProviderRequest.EXPECTED_ORIGIN);
        sofascore.setAllowedEndpoints(Set.of(
                SofascoreEndpointType.SCHEDULED_EVENTS,
                SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS));
        TournamentEventDiscoveryQualificationPolicy policy =
                new TournamentEventDiscoveryQualificationPolicy(
                        sofascore, new ProviderPlaywrightProperties());
        TournamentEventDiscoveryControlService realControl =
                new TournamentEventDiscoveryControlService(
                        clock,
                        () -> REQUEST_ID,
                        () -> 42,
                        policy::snapshot,
                        policy::localImportSnapshot,
                        catalogService);
        var prepared = realControl.prepare(119_880);
        assertThat(prepared.providerTransportAvailable()).isFalse();
        assertThat(prepared.localImportAvailable()).isTrue();
        var localClaim = realControl.confirmAndClaimLocalImport(
                REQUEST_ID, prepared.confirmationPhrase(), true);
        RawPayloadEvidence payload = payload(7);
        when(rawStore.save(any())).thenReturn(persistenceResult(92, payload));
        when(persistence.persist(any(), eq(92L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());
        TournamentEventDiscoveryService localService =
                new TournamentEventDiscoveryService(
                        realControl,
                        transport,
                        cache,
                        rawStore,
                        new TournamentScheduledEventsProjectionService(),
                        persistence,
                        new TournamentScheduledEventsV1Parser(),
                        immediateCoordinator(),
                        clock);

        var result = localService.importLocalJson(localClaim, payload);

        assertThat(result.completed()).isTrue();
        assertThat(result.source())
                .isEqualTo(TournamentEventDiscoverySource.LOCAL_JSON_IMPORT);
        assertThat(result.providerCallAttempts()).isZero();
        assertThat(result.cacheHit()).isFalse();
        verify(transport, never()).openCampaign(any());
        verify(cache, never()).findFreshParsed(any(), any(), any(), any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
    }

    @Test
    void reparsesAFreshCacheHitWithoutExecutingOrPersistingTransport() {
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(31L);
        service = auditedService(evidenceStore, Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        var cached = new CachedTournamentScheduledEventsResponse(
                82,
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7",
                NOW,
                NOW.minusMillis(25),
                NOW,
                200,
                "application/json",
                Duration.ofMillis(25),
                payload,
                TournamentScheduledEventsV1Parser.PARSER_VERSION);
        when(cache.findFreshParsed(
                any(), eq(NOW), eq(TournamentEventDiscoveryService.CACHE_TTL),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION)))
                .thenReturn(Optional.of(cached));
        when(persistence.persist(any(), eq(82L), eq(payload.sha256()), eq(NOW)))
                .thenReturn(canonicalization());

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isTrue();
        assertThat(result.cacheHit()).isTrue();
        assertThat(result.providerCallAttempts()).isZero();
        verify(transport, never()).openCampaign(any());
        verify(rawStore, never()).save(any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(evidenceStore, never()).startProviderAttempt(any());
        ArgumentCaptor<J8BenchmarkUnitResult> auditResult =
                ArgumentCaptor.forClass(J8BenchmarkUnitResult.class);
        verify(evidenceStore).recordUnitResult(auditResult.capture());
        assertThat(auditResult.getValue().resolutionSource())
                .isEqualTo(J8BenchmarkResolutionSource.CACHE);
        assertThat(auditResult.getValue().outcomeType())
                .isEqualTo(J8BenchmarkOutcomeType.PARSED);
        assertThat(auditResult.getValue().attemptId()).isEmpty();
        verify(evidenceStore).finishCampaign(any());
    }

    @Test
    void blocksTournamentTransportWhenAttemptAuditPersistenceFails() {
        J8BenchmarkEvidenceStore evidenceStore = mock(J8BenchmarkEvidenceStore.class);
        when(evidenceStore.declareUnit(any())).thenReturn(41L);
        when(evidenceStore.startProviderAttempt(any()))
                .thenThrow(new IllegalStateException("attempt audit unavailable"));
        service = auditedService(evidenceStore, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.execute(claim(Map.of(7200, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt audit unavailable");

        InOrder order = inOrder(evidenceStore, providerCampaign);
        order.verify(evidenceStore).declareUnit(any());
        order.verify(evidenceStore).startProviderAttempt(any());
        order.verify(providerCampaign).close();
        verify(providerCampaign, never()).execute(any());
        verify(rawStore, never()).save(any());
        verify(evidenceStore, never()).recordUnitResult(any());
        verify(evidenceStore, never()).finishCampaign(any());
        verify(control).fail(REQUEST_ID, "BENCHMARK_AUDIT_FAILURE");
    }

    @Test
    void refusesCanonicalizationOnCountMismatchButKeepsTheParsedSnapshotCached() {
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        var raw = persistenceResult(83, payload);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(raw);

        var result = service.execute(claim(Map.of(7200, 2)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("EVENT_COUNT_MISMATCH");
        assertThat(result.countStatus()).isEqualTo(TournamentEventCountStatus.COUNT_MISMATCH);
        assertThat(result.expectedCount()).isEqualTo(2);
        assertThat(result.actualCount()).isEqualTo(1);
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
        verify(cache).recordParsed(
                any(), eq(response), eq(raw),
                eq(TournamentScheduledEventsV1Parser.PARSER_VERSION));
        verify(control).fail(REQUEST_ID, "EVENT_COUNT_MISMATCH");
    }

    @Test
    void rejectsAUniqueTournamentContradictionBeforeCacheOrCanonicalWrites() {
        RawPayloadEvidence payload = payload(8);
        var response = response(200, payload);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(84, payload));

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("UNIQUE_TOURNAMENT_ID_MISMATCH");
        verify(rawStore).classify(
                84,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void classifiesAParserContractBreakWithTheCanonicalRawErrorCode() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"events\":{}}".getBytes(StandardCharsets.UTF_8));
        var response = response(200, payload);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(87, payload));

        var result = service.execute(claim(Map.of()));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("SCHEMA_INCOMPATIBLE");
        verify(rawStore).classify(
                87,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void keepsParserFailureDetailedForControlButCanonicalForRawClassification() {
        TournamentScheduledEventsV1Parser failingParser =
                mock(TournamentScheduledEventsV1Parser.class);
        when(failingParser.parse(
                any(byte[].class),
                any(String.class),
                any(TournamentScheduledEventsParseEvidence.class)))
                .thenThrow(new IllegalStateException("synthetic parser failure"));
        service = serviceWith(
                failingParser,
                new TournamentScheduledEventsProjectionService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        when(providerCampaign.execute(any())).thenReturn(response(200, payload));
        when(rawStore.save(any())).thenReturn(persistenceResult(88, payload));

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("PARSER_FAILURE");
        verify(rawStore).classify(
                88,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void keepsProjectionFailureDetailedForControlButCanonicalForRawClassification() {
        TournamentScheduledEventsProjectionService failingProjection =
                mock(TournamentScheduledEventsProjectionService.class);
        when(failingProjection.project(
                any(LocalDate.class),
                any(J3TournamentCatalogOption.class),
                anyList()))
                .thenThrow(new IllegalStateException("synthetic projection failure"));
        service = serviceWith(
                new TournamentScheduledEventsV1Parser(),
                failingProjection,
                Clock.fixed(NOW, ZoneOffset.UTC));
        RawPayloadEvidence payload = payload(7);
        when(providerCampaign.execute(any())).thenReturn(response(200, payload));
        when(rawStore.save(any())).thenReturn(persistenceResult(89, payload));

        var result = service.execute(claim(Map.of(7200, 1)));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("PROJECTION_FAILURE");
        verify(rawStore).classify(
                89,
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                "SCHEMA_INCOMPATIBLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void recordsAndClassifiesANonSuccessResponseWithoutRetry() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"error\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8));
        var response = response(403, payload);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(85, payload));

        var result = service.execute(claim(Map.of()));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("HTTP_403");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        verify(providerCampaign).execute(any());
        verify(rawStore).classify(85, RawSnapshotSchemaStatus.TRANSPORT_ERROR, "HTTP_403");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void classifiesA404AsEndpointUnavailableWithoutParsingOrRetry() {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                "{\"error\":\"not-found\"}".getBytes(StandardCharsets.UTF_8));
        var response = response(404, payload);
        when(providerCampaign.execute(any())).thenReturn(response);
        when(rawStore.save(any())).thenReturn(persistenceResult(91, payload));

        var result = service.execute(claim(Map.of()));

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("ENDPOINT_UNAVAILABLE");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(result.snapshotId()).isEqualTo(91);
        verify(providerCampaign).execute(any());
        verify(rawStore).classify(
                91,
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE,
                "ENDPOINT_UNAVAILABLE");
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
        verify(control).fail(REQUEST_ID, "ENDPOINT_UNAVAILABLE");
    }

    @Test
    void operatorStopDuringTransportKeepsRawButPreventsCacheAndCanonicalWrites() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog());
        var realControl = new TournamentEventDiscoveryControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> TournamentEventDiscoveryQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)),
                catalogService);
        var prepared = realControl.prepare(119_880);
        var realClaim = realControl.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true);
        RawPayloadEvidence payload = payload(7);
        var response = response(200, payload);
        when(providerCampaign.execute(any())).thenAnswer(invocation -> {
            realControl.stop();
            return response;
        });
        when(rawStore.save(any())).thenReturn(persistenceResult(86, payload));
        var realService = new TournamentEventDiscoveryService(
                realControl,
                transport,
                cache,
                rawStore,
                new TournamentScheduledEventsProjectionService(),
                persistence,
                new TournamentScheduledEventsV1Parser(),
                immediateCoordinator(),
                clock);

        var result = realService.execute(realClaim);

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("OPERATOR_STOP");
        assertThat(result.snapshotId()).isEqualTo(86);
        assertThat(realControl.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
        verify(rawStore).save(any());
        verify(rawStore, never()).classify(anyLong(), any(), any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    @Test
    void operatorStopThatTerminatesTransportIsNotReclassifiedAsATransportFailure() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        J3TournamentCatalogService catalogService = mock(J3TournamentCatalogService.class);
        when(catalogService.latest()).thenReturn(catalog());
        var realControl = new TournamentEventDiscoveryControlService(
                clock,
                () -> REQUEST_ID,
                () -> 42,
                () -> TournamentEventDiscoveryQualificationSnapshot.available(
                        URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN)),
                catalogService);
        var prepared = realControl.prepare(119_880);
        var realClaim = realControl.confirmAndClaim(
                REQUEST_ID, prepared.confirmationPhrase(), true);
        when(providerCampaign.execute(any())).thenAnswer(invocation -> {
            realControl.stop();
            throw new ScheduledEventsTransportException(
                    ScheduledEventsTransportFailure.OPERATOR_STOP);
        });
        var realService = new TournamentEventDiscoveryService(
                realControl,
                transport,
                cache,
                rawStore,
                new TournamentScheduledEventsProjectionService(),
                persistence,
                new TournamentScheduledEventsV1Parser(),
                immediateCoordinator(),
                clock);

        var result = realService.execute(realClaim);

        assertThat(result.completed()).isFalse();
        assertThat(result.terminalCode()).isEqualTo("OPERATOR_STOP");
        assertThat(result.providerCallAttempts()).isEqualTo(1);
        assertThat(realControl.snapshot().state())
                .isEqualTo(TournamentEventDiscoveryState.STOPPED_LOCKED);
        verify(rawStore, never()).save(any());
        verify(cache, never()).recordParsed(any(), any(), any(), any());
        verify(persistence, never()).persist(any(), anyLong(), any(), any());
    }

    private static TournamentEventDiscoveryExecutionClaim claim(
            Map<Integer, Integer> counts) {
        return new TournamentEventDiscoveryExecutionClaim(
                REQUEST_ID,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                LocalDate.of(2026, 8, 18),
                new J3TournamentCatalogOption(
                        119_880,
                        "UEFA Champions League, Playoff Round",
                        "Europe",
                        7,
                        "UEFA Champions League",
                        counts,
                        List.of(41L)));
    }

    private static TournamentEventDiscoveryLocalImportClaim localImportClaim(
            Map<Integer, Integer> counts) {
        TournamentEventDiscoveryExecutionClaim providerClaim = claim(counts);
        return new TournamentEventDiscoveryLocalImportClaim(
                providerClaim.requestId(),
                providerClaim.providerOrigin(),
                providerClaim.collectionDate(),
                providerClaim.selection());
    }

    private static J3TournamentCatalog catalog() {
        return J3TournamentCatalog.available(
                LocalDate.of(2026, 8, 18),
                List.of(41L),
                List.of(claim(Map.of(7200, 1)).selection()),
                0);
    }

    private static ManualProviderRequestCoordinator immediateCoordinator() {
        AtomicLong ticker = new AtomicLong();
        return new ManualProviderRequestCoordinator(
                ticker::get,
                Duration.ofSeconds(3),
                delay -> ticker.addAndGet(delay.toNanos()));
    }

    private static TournamentScheduledEventsTransportResponse response(
            int status,
            RawPayloadEvidence payload) {
        return new TournamentScheduledEventsTransportResponse(
                "TOURNAMENT_SCHEDULED_EVENTS|date=2026-08-18|uniqueTournamentId=7",
                NOW.minusMillis(25),
                NOW,
                status,
                "application/json",
                Duration.ofMillis(25),
                payload);
    }

    private static RawSnapshotPersistenceResult persistenceResult(
            long snapshotId,
            RawPayloadEvidence payload) {
        return new RawSnapshotPersistenceResult(
                snapshotId,
                RawSnapshotPersistenceOutcome.INSERTED,
                payload.sha256(),
                payload.sizeBytes(),
                java.util.OptionalLong.of(snapshotId + 1000L));
    }

    private static TournamentCanonicalizationResult canonicalization() {
        return new TournamentCanonicalizationResult(
                1,
                0,
                List.of(new TournamentDiscoveredEventView(
                        CanonicalEventIdentity.sofascore(501).value(),
                        501,
                        Instant.parse("2026-08-18T19:00:00Z"),
                        "Home",
                        "Away",
                        "finished")));
    }

    private static RawPayloadEvidence payload(long uniqueTournamentId) {
        String json = """
                {
                  "events": [
                    {
                      "id": 501,
                      "startTimestamp": 1787079600,
                      "homeTeam": {"id": 10, "name": "Home"},
                      "awayTeam": {"id": 20, "name": "Away"},
                      "status": {"type": "finished"},
                      "tournament": {
                        "id": 119880,
                        "name": "UEFA Champions League, Playoff Round",
                        "uniqueTournament": {
                          "id": %d,
                          "name": "UEFA Champions League"
                        }
                      }
                    }
                  ]
                }
                """.formatted(uniqueTournamentId);
        return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8));
    }

    private TournamentEventDiscoveryService serviceWith(
            TournamentScheduledEventsV1Parser selectedParser,
            TournamentScheduledEventsProjectionService selectedProjection,
            Clock selectedClock) {
        return new TournamentEventDiscoveryService(
                control,
                transport,
                cache,
                rawStore,
                selectedProjection,
                persistence,
                selectedParser,
                immediateCoordinator(),
                selectedClock);
    }

    private TournamentEventDiscoveryService auditedService(
            J8BenchmarkEvidenceStore evidenceStore,
            Clock selectedClock) {
        return new TournamentEventDiscoveryService(
                control,
                transport,
                cache,
                rawStore,
                new TournamentScheduledEventsProjectionService(),
                persistence,
                new TournamentScheduledEventsV1Parser(),
                immediateCoordinator(),
                selectedClock,
                new J8BenchmarkAuditService(evidenceStore, selectedClock));
    }
}
