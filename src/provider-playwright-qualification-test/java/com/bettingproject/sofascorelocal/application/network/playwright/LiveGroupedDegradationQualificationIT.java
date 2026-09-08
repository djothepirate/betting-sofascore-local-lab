package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.application.live.LiveProviderSession;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup.Phase;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit Chromium/loopback transport qualification; never part of standard tests. */
class LiveGroupedDegradationQualificationIT {
    private static final long FIRST_EVENT = 17_000_001L;
    private static final long SECOND_EVENT = 17_000_002L;
    private static final long THREE_SECONDS = TimeUnit.SECONDS.toNanos(3);

    @Test @Timeout(40)
    void delayedIncident404KeepsFourDistinctReceiptsAndOnlyTheNextGroupWaits() throws Exception {
        try (var fixture = new LiveProviderSessionQualificationIT.Fixture()) {
            var supervisor = fixture.supervisor();
            UUID campaignId = UUID.randomUUID(), firstGroup = UUID.randomUUID();
            var samples = new ArrayList<Sample>();
            List<ProcessHandle> owned;
            try (var campaign = supervisor.openLiveGrouped(campaignId, LiveProviderSession.ENDPOINTS)) {
                var endpoints = List.of(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
                long[] delays = {120, 650, 180, 420};
                for (int i = 0; i < endpoints.size(); i++) {
                    samples.add(execute(fixture, campaign, campaignId, firstGroup, FIRST_EVENT,
                            endpoints.get(i), delays[i]));
                }
                samples.add(execute(fixture, campaign, campaignId, UUID.randomUUID(), SECOND_EVENT,
                        EVENT_DETAILS, 90));

                assertThat(samples).extracting(sample -> sample.response().httpStatus())
                        .containsExactly(200, 404, 200, 200, 200);
                assertThat(fixture.arrivals).hasSize(5);
                assertThat(samples).extracting(sample -> sample.response().requestedAt()).doesNotHaveDuplicates();
                assertThat(samples).extracting(sample -> sample.response().receivedAt()).doesNotHaveDuplicates();
                // The four successful responses intentionally have the same bytes. Each
                // HTTP reception must nevertheless remain a separate timestamped receipt.
                assertThat(samples.stream().filter(sample -> sample.response().httpStatus() == 200)
                        .map(sample -> sample.response().payload().sha256()).distinct()).hasSize(1);

                for (int i = 0; i < samples.size(); i++) {
                    Sample current = samples.get(i);
                    assertThat(current.response().latency()).as("injected response delay at request %s", i)
                            .isGreaterThanOrEqualTo(Duration.ofMillis(current.delayMillis() - 5));
                    if (i == 0) continue;
                    Sample previous = samples.get(i - 1);
                    assertThat(current.dispatchNanos()).as("only dispatch after the prior response is received")
                            .isGreaterThanOrEqualTo(previous.completedNanos());
                    assertThat(current.response().requestedAt()).isAfterOrEqualTo(previous.response().receivedAt());
                    assertThat(fixture.arrivals.get(i) - fixture.arrivals.get(i - 1))
                            .as("no overlapping response processing at request %s", i)
                            .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(previous.delayMillis()));
                    if (i < 4) {
                        assertThat(current.dispatchNanos() - previous.completedNanos())
                                .as("no artificial three-second pause inside the group at request %s", i)
                                .isLessThan(THREE_SECONDS);
                    }
                }
                Sample lastInGroup = samples.get(3), nextGroup = samples.get(4);
                assertThat(Duration.between(lastInGroup.response().receivedAt(), nextGroup.response().requestedAt()))
                        .as("the next group still waits three seconds after the preceding response")
                        .isGreaterThanOrEqualTo(Duration.ofSeconds(3));
                assertThat(fixture.arrivals.get(4) - fixture.arrivals.get(3))
                        .isGreaterThanOrEqualTo(THREE_SECONDS + TimeUnit.MILLISECONDS.toNanos(lastInGroup.delayMillis()));
                owned = ownedProcesses(fixture.worker.get());
            }
            awaitNativeCleanup(supervisor, owned);
            assertThat(fixture.offScope.get()).isZero();
            fixture.assertNoArtifacts();
            System.out.println("WO058_V4_DEGRADATION_CASE=DELAYED_404;RECEIPTS=5;HTTP_404=1;REAL_PROVIDER_CALLS=0;CLEANUP=PASS");
        }
    }

    @Test @Timeout(40)
    void explicitStopBetweenResponsesRejectsContinuationWithoutAnotherDeparture() throws Exception {
        try (var fixture = new LiveProviderSessionQualificationIT.Fixture()) {
            var supervisor = fixture.supervisor();
            UUID campaignId = UUID.randomUUID(), groupId = UUID.randomUUID();
            List<ProcessHandle> owned;
            AtomicInteger continuationPermits = new AtomicInteger();
            try (var campaign = supervisor.openLiveGrouped(campaignId, LiveProviderSession.ENDPOINTS)) {
                Sample first = execute(fixture, campaign, campaignId, groupId, FIRST_EVENT, EVENT_DETAILS, 250);
                assertThat(first.response().httpStatus()).isEqualTo(200);
                assertThat(fixture.arrivals).hasSize(1);
                owned = ownedProcesses(fixture.worker.get());

                var stopped = supervisor.stopCampaign(campaignId, LiveProviderSession.ENDPOINTS);
                assertThat(stopped.activeCampaignSignalled()).isTrue();
                assertThat(stopped.campaignId()).isEqualTo(campaignId);
                assertThat(stopped.acknowledgementLatency()).isLessThanOrEqualTo(Duration.ofMillis(500));
                var continuationAdmission = new PlaywrightDispatchAdmission() {
                    @Override public void check() { }
                    @Override public Permit acquireDispatchPermit() {
                        continuationPermits.incrementAndGet();
                        return () -> { };
                    }
                };
                assertThatThrownBy(() -> campaign.executeGrouped(PlaywrightProviderRequest.eventIncidents(FIRST_EVENT),
                        new LiveProviderDispatchGroup(campaignId, groupId, FIRST_EVENT, Phase.IN_PLAY),
                        continuationAdmission))
                        .isInstanceOfSatisfying(PlaywrightProviderException.class,
                                failure -> assertThat(failure.failure()).isEqualTo(PlaywrightProviderFailure.OPERATOR_STOP));
                assertThat(continuationPermits.get()).isZero();
                assertThat(fixture.arrivals).hasSize(1);
            }
            awaitNativeCleanup(supervisor, owned);
            // Assert after physical cleanup too: an asynchronously delayed departure
            // would increase this count even if the continuation returned an error.
            assertThat(fixture.arrivals).hasSize(1);
            assertThat(fixture.offScope.get()).isZero();
            fixture.assertNoArtifacts();
            System.out.println("WO058_V4_DEGRADATION_CASE=OPERATOR_STOP;RECEIPTS=1;CONTINUATION_DEPARTURES=0;REAL_PROVIDER_CALLS=0;CLEANUP=PASS");
        }
    }

    private static Sample execute(LiveProviderSessionQualificationIT.Fixture fixture,
                                  PlaywrightProviderCampaign campaign, UUID campaignId, UUID groupId,
                                  long eventId, SofascoreEndpointType endpoint, long delayMillis) {
        fixture.responseDelayMillis.set(delayMillis);
        AtomicLong dispatched = new AtomicLong();
        var response = campaign.executeGrouped(new PlaywrightProviderRequest(endpoint, null, 0, 0, eventId),
                new LiveProviderDispatchGroup(campaignId, groupId, eventId,
                        endpoint == EVENT_DETAILS ? Phase.CHECK : Phase.IN_PLAY),
                new PlaywrightDispatchAdmission() {
                    @Override public void check() { }
                    @Override public Permit acquireDispatchPermit() {
                        dispatched.set(System.nanoTime());
                        return () -> { };
                    }
                });
        return new Sample(response, dispatched.get(), System.nanoTime(), delayMillis);
    }

    private static List<ProcessHandle> ownedProcesses(Process worker) {
        assertThat(worker).isNotNull();
        var owned = new ArrayList<>(worker.descendants().toList());
        owned.add(worker.toHandle());
        return List.copyOf(owned);
    }

    private static void awaitNativeCleanup(ChildJvmPlaywrightProviderSupervisor supervisor,
                                           List<ProcessHandle> owned) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((supervisor.activeCampaignId().isPresent() || owned.stream().anyMatch(ProcessHandle::isAlive))
                && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(supervisor.activeCampaignId()).isEmpty();
        // Native handles query the OS process tree. Do not rely on the Java Process
        // exit notification, which can lag after Windows has removed the native PID.
        assertThat(owned).noneMatch(ProcessHandle::isAlive);
    }

    private record Sample(PlaywrightProviderResponse response, long dispatchNanos,
                          long completedNanos, long delayMillis) { }
}
