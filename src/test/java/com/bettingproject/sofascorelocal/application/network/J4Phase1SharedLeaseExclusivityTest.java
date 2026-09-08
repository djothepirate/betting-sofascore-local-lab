package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV3Parser;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceResult;
import com.bettingproject.sofascorelocal.application.event.J4ParsedEventDetailsPersistenceService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J4CachedEventDetails;
import com.bettingproject.sofascorelocal.domain.provider.J4RealPhase1ExecutionClaim;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.port.EventDetailsProviderTransport;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;

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

class J4Phase1SharedLeaseExclusivityTest {

    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final UUID J4_REQUEST_ID = UUID.fromString(
            "14000000-0000-0000-0000-000000000014");
    private static final UUID J3_REQUEST_ID = UUID.fromString(
            "13000000-0000-0000-0000-000000000013");
    private static final UUID J5_REQUEST_ID = UUID.fromString(
            "15000000-0000-0000-0000-000000000015");

    @Test
    void holdsTheSharedLeaseAcrossBothFixedTargetsUntilTheJ4CampaignCloses()
            throws Exception {
        ManualProviderRequestCoordinator coordinator =
                immediateCoordinator();
        CountDownLatch firstTargetStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTarget = new CountDownLatch(1);
        CountDownLatch secondTargetStarted = new CountDownLatch(1);
        CountDownLatch releaseSecondTarget = new CountDownLatch(1);
        CountDownLatch contendersAttempting = new CountDownLatch(2);
        CountDownLatch contendersEntered = new CountDownLatch(2);
        List<String> order = new CopyOnWriteArrayList<>();

        EventDetailsProviderTransport transport = campaignTransport(
                firstTargetStarted,
                releaseFirstTarget,
                secondTargetStarted,
                releaseSecondTarget,
                order);
        J4RealEventDetailsPhase1Service service = service(coordinator, transport);
        var executor = Executors.newFixedThreadPool(3);

        try {
            var j4Result = executor.submit(() -> service.execute(new J4RealPhase1ExecutionClaim(
                    J4_REQUEST_ID,
                    URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN))));
            assertThat(firstTargetStarted.await(1, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> contend(
                    coordinator,
                    J3_REQUEST_ID,
                    "J3-enter",
                    contendersAttempting,
                    contendersEntered,
                    order));
            executor.submit(() -> contend(
                    coordinator,
                    J5_REQUEST_ID,
                    "J5-enter",
                    contendersAttempting,
                    contendersEntered,
                    order));
            assertThat(contendersAttempting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).doesNotContain("J3-enter", "J5-enter");

            releaseFirstTarget.countDown();
            assertThat(secondTargetStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly("J4-1", "J4-2");
            assertThat(contendersEntered.getCount()).isEqualTo(2);

            releaseSecondTarget.countDown();
            assertThat(j4Result.get(2, TimeUnit.SECONDS).completed()).isTrue();
            assertThat(contendersEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(order.subList(0, 3))
                    .containsExactly("J4-1", "J4-2", "J4-close");
            assertThat(order.subList(3, order.size()))
                    .containsExactlyInAnyOrder("J3-enter", "J5-enter");
        }
        finally {
            releaseFirstTarget.countDown();
            releaseSecondTarget.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void holdsTheSharedLeaseFromTheFirstCacheHitThroughTheSecondTargetMiss()
            throws Exception {
        ManualProviderRequestCoordinator coordinator =
                immediateCoordinator();
        CountDownLatch cachedTargetLookupStarted = new CountDownLatch(1);
        CountDownLatch releaseCachedTarget = new CountDownLatch(1);
        CountDownLatch providerTargetStarted = new CountDownLatch(1);
        CountDownLatch releaseProviderTarget = new CountDownLatch(1);
        CountDownLatch contendersAttempting = new CountDownLatch(2);
        CountDownLatch contendersEntered = new CountDownLatch(2);
        List<String> order = new CopyOnWriteArrayList<>();

        J4EventDetailsCache cache = cacheHitThenMiss(
                cachedTargetLookupStarted,
                releaseCachedTarget,
                order);
        EventDetailsProviderTransport transport = campaignId -> {
            order.add("J4-worker-open");
            return new EventDetailsProviderTransport.Campaign() {
                @Override
                public EventDetailsTransportResponse execute(
                        EventDetailsProviderRequest request) {
                    order.add("J4-provider");
                    providerTargetStarted.countDown();
                    await(releaseProviderTarget);
                    return response(request);
                }

                @Override
                public void close() {
                    order.add("J4-close");
                }
            };
        };
        J4RealEventDetailsPhase1Service service = service(
                coordinator, transport, cache);
        var executor = Executors.newFixedThreadPool(3);

        try {
            var j4Result = executor.submit(() -> service.execute(new J4RealPhase1ExecutionClaim(
                    J4_REQUEST_ID,
                    URI.create(EventDetailsProviderRequest.EXPECTED_ORIGIN))));
            assertThat(cachedTargetLookupStarted.await(1, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> contend(
                    coordinator,
                    J3_REQUEST_ID,
                    "J3-enter",
                    contendersAttempting,
                    contendersEntered,
                    order));
            executor.submit(() -> contend(
                    coordinator,
                    J5_REQUEST_ID,
                    "J5-enter",
                    contendersAttempting,
                    contendersEntered,
                    order));
            assertThat(contendersAttempting.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly("J4-cache-lookup");
            assertThat(contendersEntered.getCount()).isEqualTo(2);

            releaseCachedTarget.countDown();
            assertThat(providerTargetStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly(
                    "J4-cache-lookup",
                    "J4-cache-hit",
                    "J4-cache-miss",
                    "J4-worker-open",
                    "J4-provider");
            assertThat(contendersEntered.getCount()).isEqualTo(2);

            releaseProviderTarget.countDown();
            var result = j4Result.get(2, TimeUnit.SECONDS);
            assertThat(result.completed()).isTrue();
            assertThat(result.cacheHits()).isEqualTo(1);
            assertThat(result.providerCallAttempts()).isEqualTo(1);
            assertThat(contendersEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(order.subList(0, 6)).containsExactly(
                    "J4-cache-lookup",
                    "J4-cache-hit",
                    "J4-cache-miss",
                    "J4-worker-open",
                    "J4-provider",
                    "J4-close");
            assertThat(order.subList(6, order.size()))
                    .containsExactlyInAnyOrder("J3-enter", "J5-enter");
        }
        finally {
            releaseCachedTarget.countDown();
            releaseProviderTarget.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static J4RealEventDetailsPhase1Service service(
            ManualProviderRequestCoordinator coordinator,
            EventDetailsProviderTransport transport) {
        J4EventDetailsCache cache = mock(J4EventDetailsCache.class);
        when(cache.findFreshParsed(any(), any(), any(), any())).thenReturn(Optional.empty());
        return service(coordinator, transport, cache);
    }

    private static J4RealEventDetailsPhase1Service service(
            ManualProviderRequestCoordinator coordinator,
            EventDetailsProviderTransport transport,
            J4EventDetailsCache cache) {
        J4RealPhase1ControlService control = mock(J4RealPhase1ControlService.class);
        RawManualCallSnapshotStore rawStore = mock(RawManualCallSnapshotStore.class);
        J4ParsedEventDetailsPersistenceService persistence =
                mock(J4ParsedEventDetailsPersistenceService.class);
        when(control.executionMayContinue(J4_REQUEST_ID)).thenReturn(true);
        when(rawStore.save(any())).thenAnswer(invocation -> {
            RawManualCallSnapshot snapshot = invocation.getArgument(0);
            long snapshotId = snapshot.requestKey().endsWith("16386245") ? 141L : 142L;
            return new RawSnapshotPersistenceResult(
                    snapshotId,
                    RawSnapshotPersistenceOutcome.INSERTED,
                    snapshot.payload().sha256(),
                    snapshot.payload().sizeBytes(),
                    java.util.OptionalLong.of(snapshotId));
        });
        when(persistence.persistParsed(any(), any(), any(), any())).thenAnswer(invocation -> {
            EventDetailsProviderRequest request = invocation.getArgument(0);
            return new J4ParsedEventDetailsPersistenceResult(
                    CanonicalEventIdentity.sofascore(request.eventId()).value(),
                    request.eventId(),
                    request.eventId() + 1,
                    true,
                    true);
        });
        return new J4RealEventDetailsPhase1Service(
                control,
                transport,
                rawStore,
                cache,
                persistence,
                new SofascoreEndpointCatalog(),
                coordinator);
    }

    private static J4EventDetailsCache cacheHitThenMiss(
            CountDownLatch cachedTargetLookupStarted,
            CountDownLatch releaseCachedTarget,
            List<String> order) {
        J4EventDetailsCache cache = mock(J4EventDetailsCache.class);
        when(cache.findFreshParsed(any(), any(), any(), any())).thenAnswer(invocation -> {
            EventDetailsProviderRequest request = invocation.getArgument(0);
            if (request.eventId()
                    == EventDetailsProviderRequest.SAINT_ETIENNE_CLERMONT_EVENT_ID) {
                order.add("J4-cache-lookup");
                cachedTargetLookupStarted.countDown();
                await(releaseCachedTarget);
                order.add("J4-cache-hit");
                return Optional.of(cached(request));
            }
            order.add("J4-cache-miss");
            return Optional.empty();
        });
        return cache;
    }

    private static EventDetailsProviderTransport campaignTransport(
            CountDownLatch firstTargetStarted,
            CountDownLatch releaseFirstTarget,
            CountDownLatch secondTargetStarted,
            CountDownLatch releaseSecondTarget,
            List<String> order) {
        return campaignId -> new EventDetailsProviderTransport.Campaign() {
            @Override
            public EventDetailsTransportResponse execute(EventDetailsProviderRequest request) {
                if (request.eventId()
                        == EventDetailsProviderRequest.SAINT_ETIENNE_CLERMONT_EVENT_ID) {
                    order.add("J4-1");
                    firstTargetStarted.countDown();
                    await(releaseFirstTarget);
                }
                else {
                    order.add("J4-2");
                    secondTargetStarted.countDown();
                    await(releaseSecondTarget);
                }
                return response(request);
            }

            @Override
            public void close() {
                order.add("J4-close");
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

    private static EventDetailsTransportResponse response(
            EventDetailsProviderRequest request) {
        byte[] body = nominal(request.eventId()).getBytes(StandardCharsets.UTF_8);
        return new EventDetailsTransportResponse(
                request.requestKey(),
                NOW,
                NOW.plusMillis(25),
                200,
                "application/json",
                Duration.ofMillis(25),
                RawPayloadEvidence.capture(body));
    }

    private static J4CachedEventDetails cached(EventDetailsProviderRequest request) {
        RawPayloadEvidence payload = RawPayloadEvidence.capture(
                nominal(request.eventId()).getBytes(StandardCharsets.UTF_8));
        return new J4CachedEventDetails(
                140L,
                request.requestKey(),
                NOW.plusMillis(25),
                NOW,
                NOW.plusMillis(25),
                200,
                "application/json",
                Duration.ofMillis(25),
                payload,
                EventDetailsV3Parser.PARSER_VERSION);
    }

    private static String nominal(long eventId) {
        return """
                {
                  "event": {
                    "id": %d,
                    "startTimestamp": 1786793400,
                    "homeTeam": {"id": 11, "name": "Home"},
                    "awayTeam": {"id": 12, "name": "Away"},
                    "status": {"type": "notstarted", "description": "Not started"},
                    "tournament": {"id": 13, "name": "Qualification League"},
                    "season": {"id": 14, "name": "2026"},
                    "roundInfo": {"round": 1}
                  }
                }
                """.formatted(eventId);
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

    private static ManualProviderRequestCoordinator immediateCoordinator() {
        AtomicLong ticker = new AtomicLong();
        return new ManualProviderRequestCoordinator(
                ticker::get,
                Duration.ofSeconds(3),
                delay -> ticker.addAndGet(delay.toNanos()));
    }
}
