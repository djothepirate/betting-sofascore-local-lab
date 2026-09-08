package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportException;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.J5EventDataTransportFailure;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ProviderJ5EventDataPlaywrightTransport;
import com.bettingproject.sofascorelocal.application.live.LiveProviderSession;
import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit native loopback proof through BOTH the manual coordinator and J5 production adapter. */
class ManualJ5GroupedQualificationIT {
    private static final long EVENT = 17_000_001L;
    private static final List<SofascoreEndpointType> J5 = List.of(EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS);

    @Test @Timeout(100)
    void twoManualTripletsKeepTheirOuterFencesAndJ4AndLiveTransitions() throws Exception {
        try (var fixture = new LiveProviderSessionQualificationIT.Fixture()) {
            var supervisor = fixture.supervisor();
            var coordinator = new ManualProviderRequestCoordinator(new SofascoreProperties());
            var adapter = new ProviderJ5EventDataPlaywrightTransport(supervisor);
            List<ProcessHandle> owned = new ArrayList<>();
            Instant previousReceived;
            UUID j4Id = UUID.randomUUID();
            try (var lease = coordinator.acquireCampaign(j4Id);
                 var j4 = supervisor.open(j4Id, Set.of(EVENT_DETAILS))) {
                lease.beginRequest();
                var first = j4.execute(PlaywrightProviderRequest.eventDetails(EVENT));
                lease.beginRequest();
                var second = j4.execute(PlaywrightProviderRequest.eventDetails(EVENT + 1));
                assertOuterFence(first.receivedAt(), second.requestedAt());
                previousReceived = second.receivedAt();
                owned.addAll(ownedProcesses(fixture.worker.get()));
            }
            awaitCleanup(supervisor, owned);

            var innerGaps = new ArrayList<Long>();
            var statuses = new ArrayList<Integer>();
            for (int groupIndex = 0; groupIndex < 2; groupIndex++) {
                UUID id = UUID.randomUUID();
                // Same event deliberately repeated in a NEW claim: this must not continue the old group.
                try (var lease = coordinator.acquireManualJ5Campaign(id, EVENT);
                     var manual = adapter.openCampaign(id)) {
                    for (int index = 0; index < J5.size(); index++) {
                        fixture.responseDelayMillis.set(List.of(80L, 160L, 60L).get(index));
                        var request = request(J5.get(index));
                        lease.beginManualJ5Request(request);
                        J5EventDataTransportResponse response = manual.execute(request,
                                () -> lease.checkManualJ5Request(request));
                        statuses.add(response.httpStatus());
                        if (index == 0) assertOuterFence(previousReceived, response.requestedAt());
                        else {
                            Duration gap = Duration.between(previousReceived, response.requestedAt());
                            assertThat(gap).as("same-event manual J5 continuation has no three-second pause")
                                    .isGreaterThanOrEqualTo(Duration.ZERO).isLessThan(Duration.ofSeconds(3));
                            innerGaps.add(gap.toNanos());
                        }
                        previousReceived = response.receivedAt();
                    }
                    owned.addAll(ownedProcesses(fixture.worker.get()));
                }
                awaitCleanup(supervisor, owned);
            }
            assertThat(statuses).containsExactly(200, 404, 200, 200, 200, 200);

            UUID liveId = UUID.randomUUID();
            try (var lease = coordinator.acquireLiveCampaign(liveId);
                 var live = supervisor.openLiveGrouped(liveId, LiveProviderSession.ENDPOINTS)) {
                lease.beginRequest();
                var response = live.executeGrouped(PlaywrightProviderRequest.eventDetails(EVENT),
                        new LiveProviderDispatchGroup(liveId, UUID.randomUUID(), EVENT,
                                LiveProviderDispatchGroup.Phase.CHECK), PlaywrightDispatchAdmission.UNRESTRICTED);
                assertOuterFence(previousReceived, response.requestedAt());
                previousReceived = response.receivedAt();
                owned.addAll(ownedProcesses(fixture.worker.get()));
            }
            awaitCleanup(supervisor, owned);
            UUID finalJ4Id = UUID.randomUUID();
            try (var lease = coordinator.acquireCampaign(finalJ4Id);
                 var j4 = supervisor.open(finalJ4Id, Set.of(EVENT_DETAILS))) {
                lease.beginRequest();
                var response = j4.execute(PlaywrightProviderRequest.eventDetails(EVENT));
                assertOuterFence(previousReceived, response.requestedAt());
                owned.addAll(ownedProcesses(fixture.worker.get()));
            }
            awaitCleanup(supervisor, owned);
            assertThat(fixture.arrivals).hasSize(10);
            assertThat(fixture.offScope.get()).isZero();
            fixture.assertNoArtifacts();
            System.out.println("WO058_MANUAL_J5_GROUPS=2;RECEIPTS=10;INNER_GAPS_NS=" + innerGaps
                    + ";J4_AND_GROUP_TRANSITIONS=PASS;INCIDENT_404_CONTINUED=PASS;REAL_PROVIDER_CALLS=0;CLEANUP=PASS");
        }
    }

    @Test @Timeout(35)
    void stoppedClaimRejectsTheNextJ5AtTheFinalGuardWithoutAGet() throws Exception {
        try (var fixture = new LiveProviderSessionQualificationIT.Fixture()) {
            var supervisor = fixture.supervisor();
            var coordinator = new ManualProviderRequestCoordinator(new SofascoreProperties());
            var adapter = new ProviderJ5EventDataPlaywrightTransport(supervisor);
            UUID id = UUID.randomUUID();
            var stopped = new AtomicBoolean();
            List<ProcessHandle> owned;
            try (var lease = coordinator.acquireManualJ5Campaign(id, EVENT);
                 var manual = adapter.openCampaign(id)) {
                var first = request(EVENT_STATISTICS);
                lease.beginManualJ5Request(first);
                manual.execute(first, () -> lease.checkManualJ5Request(first));
                var next = request(EVENT_INCIDENTS);
                lease.beginManualJ5Request(next);
                stopped.set(true);
                assertThatThrownBy(() -> manual.execute(next, () -> {
                    lease.checkManualJ5Request(next);
                    if (stopped.get()) throw new J5EventDataTransportException(J5EventDataTransportFailure.OPERATOR_STOP);
                })).isInstanceOf(J5EventDataTransportException.class);
                assertThat(fixture.arrivals).hasSize(1);
                owned = ownedProcesses(fixture.worker.get());
            }
            awaitCleanup(supervisor, owned);
            assertThat(fixture.arrivals).hasSize(1);
            assertThat(fixture.offScope.get()).isZero();
            fixture.assertNoArtifacts();
            System.out.println("WO058_MANUAL_J5_STOP=PASS;RECEIPTS=1;CONTINUATION_DEPARTURES=0;REAL_PROVIDER_CALLS=0;CLEANUP=PASS");
        }
    }

    private static J5EventDataProviderRequest request(SofascoreEndpointType endpoint) {
        return new J5EventDataProviderRequest(URI.create("https://www.sofascore.com"), EVENT, endpoint);
    }

    private static void assertOuterFence(Instant received, Instant requested) {
        assertThat(Duration.between(received, requested))
                .as("three seconds after the previous response between groups and other manual/live requests")
                .isGreaterThanOrEqualTo(Duration.ofSeconds(3));
    }

    private static List<ProcessHandle> ownedProcesses(Process worker) {
        var handles = new ArrayList<>(worker.descendants().toList());
        handles.add(worker.toHandle());
        return List.copyOf(handles);
    }

    private static void awaitCleanup(ChildJvmPlaywrightProviderSupervisor supervisor, List<ProcessHandle> owned)
            throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((supervisor.activeCampaignId().isPresent() || owned.stream().anyMatch(ProcessHandle::isAlive))
                && System.nanoTime() < until) Thread.sleep(20);
        assertThat(supervisor.activeCampaignId()).isEmpty();
        assertThat(owned).noneMatch(ProcessHandle::isAlive);
    }
}
