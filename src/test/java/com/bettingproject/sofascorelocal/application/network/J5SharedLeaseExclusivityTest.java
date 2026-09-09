package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV17Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV3Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
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
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataProviderTransport;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class J5SharedLeaseExclusivityTest {

    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final long EVENT_ID = 16_391_135L;
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(EVENT_ID);
    private static final UUID J5_REQUEST_ID = UUID.fromString(
            "15000000-0000-0000-0000-000000000015");
    private static final UUID J3_REQUEST_ID = UUID.fromString(
            "13000000-0000-0000-0000-000000000013");
    private static final UUID J4_REQUEST_ID = UUID.fromString(
            "14000000-0000-0000-0000-000000000014");

    @Test
    void holdsTheSharedLeaseAcrossAllThreeTargetsUntilTheJ5CampaignCloses()
            throws Exception {
        ManualProviderRequestCoordinator coordinator =
                immediateCoordinator();
        CountDownLatch statisticsStarted = new CountDownLatch(1);
        CountDownLatch releaseStatistics = new CountDownLatch(1);
        CountDownLatch incidentsStarted = new CountDownLatch(1);
        CountDownLatch releaseIncidents = new CountDownLatch(1);
        CountDownLatch lineupsStarted = new CountDownLatch(1);
        CountDownLatch releaseLineups = new CountDownLatch(1);
        CountDownLatch contendersAttempting = new CountDownLatch(2);
        CountDownLatch contendersEntered = new CountDownLatch(2);
        List<String> order = new CopyOnWriteArrayList<>();

        J5EventDataProviderTransport transport = campaignTransport(
                fixture("statistics-nominal.json"),
                fixture("lineups-nominal.json"),
                statisticsStarted,
                releaseStatistics,
                incidentsStarted,
                releaseIncidents,
                lineupsStarted,
                releaseLineups,
                order);
        J5RealEventDataService service = service(coordinator, transport);
        var executor = Executors.newFixedThreadPool(3);

        try {
            var j5Result = executor.submit(() -> service.execute(claim()));
            assertThat(statisticsStarted.await(1, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> contend(
                    coordinator,
                    J3_REQUEST_ID,
                    "J3-enter",
                    contendersAttempting,
                    contendersEntered,
                    order));
            executor.submit(() -> contend(
                    coordinator,
                    J4_REQUEST_ID,
                    "J4-enter",
                    contendersAttempting,
                    contendersEntered,
                    order));
            assertThat(contendersAttempting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly("J5-statistics");
            assertThat(contendersEntered.getCount()).isEqualTo(2);

            releaseStatistics.countDown();
            assertThat(incidentsStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly("J5-statistics", "J5-incidents-404");
            assertThat(contendersEntered.getCount()).isEqualTo(2);

            releaseIncidents.countDown();
            assertThat(lineupsStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly(
                    "J5-statistics", "J5-incidents-404", "J5-lineups");
            assertThat(contendersEntered.getCount()).isEqualTo(2);

            releaseLineups.countDown();
            var result = j5Result.get(2, TimeUnit.SECONDS);
            assertThat(result.completed()).isTrue();
            assertThat(result.providerCallAttempts()).isEqualTo(3);
            assertThat(result.endpoints())
                    .extracting(J5RealEndpointResult::completenessStatus)
                    .containsExactly(
                            J5CompletenessStatus.COMPLETE,
                            J5CompletenessStatus.UNAVAILABLE,
                            J5CompletenessStatus.COMPLETE);
            assertThat(contendersEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(order.subList(0, 4)).containsExactly(
                    "J5-statistics",
                    "J5-incidents-404",
                    "J5-lineups",
                    "J5-close");
            assertThat(order.subList(4, order.size()))
                    .containsExactlyInAnyOrder("J3-enter", "J4-enter");
        }
        finally {
            releaseStatistics.countDown();
            releaseIncidents.countDown();
            releaseLineups.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static J5RealEventDataService service(
            ManualProviderRequestCoordinator coordinator,
            J5EventDataProviderTransport transport) {
        J5RealControlService control = mock(J5RealControlService.class);
        RawManualCallSnapshotStore rawStore = mock(RawManualCallSnapshotStore.class);
        CanonicalEventStore canonicalStore = mock(CanonicalEventStore.class);
        J5EventDataStore dataStore = mock(J5EventDataStore.class);
        PlaywrightProviderSupervisor providerSupervisor =
                mock(PlaywrightProviderSupervisor.class);
        CanonicalEventObservationView view = mock(CanonicalEventObservationView.class);

        when(control.executionMayContinue(J5_REQUEST_ID)).thenReturn(true);
        when(view.identity()).thenReturn(IDENTITY);
        when(canonicalStore.findLatestByCanonicalId(IDENTITY.value()))
                .thenReturn(Optional.of(view));
        AtomicLong snapshotIds = new AtomicLong(100L);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            long snapshotId = snapshotIds.incrementAndGet();
            return new RawSnapshotPersistenceResult(
                    snapshotId,
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(snapshotId));
        });
        AtomicLong observationIds = new AtomicLong(200L);
        when(dataStore.save(any())).thenAnswer(invocation -> {
            J5EventDataObservation observation = invocation.getArgument(0);
            return new J5EventDataPersistenceResult(
                    observationIds.incrementAndGet(),
                    IDENTITY.value(),
                    observation.data().endpointType(),
                    true);
        });
        return new J5RealEventDataService(
                control,
                transport,
                rawStore,
                canonicalStore,
                dataStore,
                new EventStatisticsV2Parser(),
                new EventIncidentsV17Parser(),
                new EventLineupsV3Parser(),
                coordinator,
                providerSupervisor);
    }

    private static J5EventDataProviderTransport campaignTransport(
            String statisticsBody,
            String lineupsBody,
            CountDownLatch statisticsStarted,
            CountDownLatch releaseStatistics,
            CountDownLatch incidentsStarted,
            CountDownLatch releaseIncidents,
            CountDownLatch lineupsStarted,
            CountDownLatch releaseLineups,
            List<String> order) {
        return campaignId -> new J5EventDataProviderTransport.Campaign() {
            @Override
            public J5EventDataTransportResponse execute(
                    J5EventDataProviderRequest request) {
                return switch (request.endpointType()) {
                    case EVENT_STATISTICS -> {
                        order.add("J5-statistics");
                        statisticsStarted.countDown();
                        await(releaseStatistics);
                        yield response(request, 200, statisticsBody);
                    }
                    case EVENT_INCIDENTS -> {
                        order.add("J5-incidents-404");
                        incidentsStarted.countDown();
                        await(releaseIncidents);
                        yield response(request, 404, "{\"error\":\"incidents unavailable\"}");
                    }
                    case EVENT_LINEUPS -> {
                        order.add("J5-lineups");
                        lineupsStarted.countDown();
                        await(releaseLineups);
                        yield response(request, 200, lineupsBody);
                    }
                    default -> throw new IllegalArgumentException("unsupported endpoint");
                };
            }

            @Override
            public void close() {
                order.add("J5-close");
            }
        };
    }

    private static void contend(
            ManualProviderRequestCoordinator coordinator,
            UUID campaignId,
            String marker,
            CountDownLatch attempting,
            CountDownLatch entered,
            List<String> order) {
        attempting.countDown();
        try (var lease = coordinator.acquireCampaign(campaignId)) {
            lease.beginRequest();
            order.add(marker);
            entered.countDown();
        }
    }

    private static ManualProviderRequestCoordinator immediateCoordinator() {
        AtomicLong ticker = new AtomicLong();
        return new ManualProviderRequestCoordinator(
                ticker::get,
                Duration.ofSeconds(3),
                delay -> ticker.addAndGet(delay.toNanos()));
    }

    private static J5RealExecutionClaim claim() {
        return new J5RealExecutionClaim(
                J5_REQUEST_ID,
                URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN),
                IDENTITY.value(),
                EVENT_ID);
    }

    private static J5EventDataTransportResponse response(
            J5EventDataProviderRequest request,
            int status,
            String body) {
        return new J5EventDataTransportResponse(
                request.endpointType(),
                request.requestKey(),
                NOW,
                NOW.plusMillis(25),
                status,
                "application/json; charset=utf-8",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String fixture(String name) throws IOException {
        try (var input = J5SharedLeaseExclusivityTest.class.getResourceAsStream(
                "/fixtures/provider-j5/" + name)) {
            return new String(
                    java.util.Objects.requireNonNull(input).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test synchronization timed out");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test synchronization interrupted", exception);
        }
    }
}
