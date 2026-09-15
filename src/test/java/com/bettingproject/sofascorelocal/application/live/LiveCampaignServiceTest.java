package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightDispatchAdmission;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderException;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderFailure;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup;
import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveCampaignStore;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Owner-thread wiring tests using only in-process transport and persistence fakes. */
class LiveCampaignServiceTest {
    private static final long A = 17000001L;
    private static final long B = 17000002L;

    @ParameterizedTest
    @ValueSource(strings={"RESUME","OPERATOR_STOP","CONTEXT_CLOSE_FAILURE","PUBLICATION_FAILURE","REFUSAL"})
    void v11J3WaitsForInFlightJ4AndOnlyResumesAfterVerifiedCleanup(String scenario) throws Exception {
        try (Harness h=new Harness(false,"live-v11",1,true,Duration.ofMinutes(15))) {
            var pauses=mock(com.bettingproject.sofascorelocal.port.J3LivePauseStore.class);
            h.service.configureJ3Pauses(pauses);
            var phases=new CopyOnWriteArrayList<String>();
            doAnswer(i->{phases.add("REQUESTED");return null;}).when(pauses).request(any(),any(),any(),any());
            doAnswer(i->{phases.add(i.getArgument(3));return null;})
                    .when(pauses).transition(any(),any(),any(),any(),any(),any(),any());
            var scope=mock(PlaywrightProviderCampaign.class);
            when(h.campaign.openJ3SubOperation(any())).thenReturn(scope);
            AtomicReference<Thread> liveOwner=new AtomicReference<>();
            AtomicReference<Thread> j3Owner=new AtomicReference<>();
            h.reply=request->{
                liveOwner.compareAndSet(null,Thread.currentThread());
                return response("{\"event\":{\"id\":17000001,\"startTimestamp\":1788796800,"
                        +"\"homeTeam\":{\"id\":1,\"name\":\"H\"},\"awayTeam\":{\"id\":2,\"name\":\"A\"},"
                        +"\"status\":{\"type\":\"inprogress\"}}}",200);
            };
            h.holdAfterDispatch.set(true);
            h.launch();
            assertThat(h.getInFlight.await(3,TimeUnit.SECONDS)).isTrue();
            Instant now=Instant.now();UUID runId=UUID.randomUUID();
            var date=java.time.LocalDate.of(2026,9,13);
            var order=new com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.Order(
                    runId,"SCHEDULED|test",UUID.randomUUID(),1,date,
                    com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger.SCHEDULED,
                    now,now,com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.OrderState.RUNNING,
                    now,now.plusSeconds(1200),null,null,null,null);
            when(scope.execute(any(),any())).thenAnswer(i->{
                assertThat(phases.getLast()).isEqualTo("J3_ACTIVE");
                assertThat(h.dispatched).hasSize(1);
                var admission=(PlaywrightDispatchAdmission)i.getArgument(1);
                admission.check();try(var permit=admission.acquireDispatchPermit()) { }
                return response("{\"events\":[],\"hasNextPage\":false}",200);
            });
            if(scenario.equals("CONTEXT_CLOSE_FAILURE"))
                org.mockito.Mockito.doThrow(new IllegalStateException("cleanup failed")).when(scope).close();
            AtomicBoolean closed=new AtomicBoolean();
            if(!scenario.equals("CONTEXT_CLOSE_FAILURE")) doAnswer(i->{closed.set(true);return null;}).when(scope).close();
            var future=h.service.submitJ3(order,(access,cancellation)->{
                j3Owner.set(Thread.currentThread());
                access.execute(new com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest(
                        java.net.URI.create("https://www.sofascore.com"),date,1),()->{});
                if(scenario.equals("OPERATOR_STOP")) h.service.stop(h.manifest.campaignId(),null);
                access.close();
                assertThat(closed).isTrue();
                if(scenario.equals("PUBLICATION_FAILURE")) throw new IllegalStateException("publication failed");
                var proof=new com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Proof(
                        runId,date,order.trigger(),now,Instant.now(),
                        com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.State.FAILED,
                        "SCHEMA_INVALID",List.of());
                return new com.bettingproject.sofascorelocal.application.network.J3CollectionExecutor.Result(
                        proof,!scenario.equals("REFUSAL"));
            }).orElseThrow();
            assertThat(phases).containsExactly("REQUESTED");
            assertThat(future).isNotDone();
            verify(h.campaign,never()).openJ3SubOperation(any());
            h.releaseGet.countDown();
            if(scenario.equals("CONTEXT_CLOSE_FAILURE") || scenario.equals("PUBLICATION_FAILURE"))
                assertThatThrownBy(()->future.get(4,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
            else future.get(4,TimeUnit.SECONDS);
            assertThat(j3Owner.get()).isSameAs(liveOwner.get()).isNotSameAs(Thread.currentThread());
            verify(h.factory,times(1)).openLiveGroupedV11(h.manifest.campaignId(),LiveProviderSession.ENDPOINTS);
            verify(h.coordinator,times(1)).acquireLiveCampaign(h.manifest.campaignId());
            if(scenario.equals("RESUME")) {
                assertThat(phases).containsExactly("REQUESTED","QUIESCENT","J3_ACTIVE","CLEANED","RESUMING","RESUMED");
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(65);
                while(h.dispatched.size()<2 && System.nanoTime()<until) Thread.sleep(20);
                assertThat(h.dispatched).hasSizeGreaterThanOrEqualTo(2);
                assertThat(h.dispatched.get(1).endpoint()).isEqualTo(EVENT_DETAILS);
                assertThat(h.attemptRequests.get(1).groupId()).isNotEqualTo(h.attemptRequests.getFirst().groupId());
                assertThat(h.attemptRequests.get(1).kind()).isEqualTo("J4_J3_RESUME_RECHECK");
                verify(h.campaign,never()).close();
            } else {
                h.awaitFinished();
                assertThat(phases).contains("STOPPED").doesNotContain("RESUMED");
                assertThat(h.dispatched).hasSize(1);
            }
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=SofascoreEndpointType.class,
            names={"EVENT_INCIDENTS","EVENT_STATISTICS","EVENT_LINEUPS"})
    void everyInFlightJ5FinishesPublicationBeforeJ3AndNoOtherFamilyStarts(SofascoreEndpointType family) throws Exception {
        try (Harness h=new Harness(false,"live-v11",1,true,Duration.ofMinutes(15))) {
            var pauses=mock(com.bettingproject.sofascorelocal.port.J3LivePauseStore.class);
            h.service.configureJ3Pauses(pauses);
            var phases=new CopyOnWriteArrayList<String>();
            doAnswer(i->{phases.add("REQUESTED");return null;}).when(pauses).request(any(),any(),any(),any());
            doAnswer(i->{phases.add(i.getArgument(3));return null;})
                    .when(pauses).transition(any(),any(),any(),any(),any(),any(),any());
            h.reply=request->request.endpoint()==EVENT_DETAILS
                    ? response("{\"event\":{\"id\":17000001,\"startTimestamp\":1788796800,"
                        +"\"homeTeam\":{\"id\":1,\"name\":\"H\"},\"awayTeam\":{\"id\":2,\"name\":\"A\"},"
                        +"\"status\":{\"type\":\"inprogress\"}}}",200)
                    : normalFinishedReply(request);
            h.transportProgress=dispatch->{
                if(h.dispatched.getLast().endpoint()==family) h.holdAfterDispatch.set(true);
            };
            h.launch();
            assertThat(h.getInFlight.await(8,TimeUnit.SECONDS)).isTrue();
            int departures=h.dispatched.size();
            assertThat(h.dispatched.getLast().endpoint()).isEqualTo(family);
            Instant now=Instant.now();UUID run=UUID.randomUUID();
            var date=java.time.LocalDate.of(2026,9,13);
            var order=new com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.Order(
                    run,"SCHEDULED|j5-pause",UUID.randomUUID(),1,date,
                    com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger.SCHEDULED,
                    now,now,com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.OrderState.RUNNING,
                    now,now.plusSeconds(1200),null,null,null,null);
            var future=h.service.submitJ3(order,(access,cancel)->{
                assertThat(h.publications).hasSize(departures);
                assertThat(h.dispatched).hasSize(departures);
                assertThat(phases).containsExactly("REQUESTED","QUIESCENT");
                access.close(); // Fully cached J3 needs no child context, but still waits for live quiescence.
                return new com.bettingproject.sofascorelocal.application.network.J3CollectionExecutor.Result(
                        new com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Proof(
                                run,date,order.trigger(),now,Instant.now(),
                                com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.State.FAILED,
                                "SCHEMA_INVALID",List.of()),true);
            }).orElseThrow();
            assertThat(phases).containsExactly("REQUESTED");
            assertThat(future).isNotDone();
            assertThat(h.publications).hasSize(departures-1);
            h.releaseGet.countDown();
            future.get(4,TimeUnit.SECONDS);
            assertThat(phases).containsExactly("REQUESTED","QUIESCENT","CLEANED","RESUMING","RESUMED");
            assertThat(h.dispatched).hasSize(departures);
            verify(h.factory,times(1)).openLiveGroupedV11(h.manifest.campaignId(),LiveProviderSession.ENDPOINTS);
            verify(h.coordinator,times(1)).acquireLiveCampaign(h.manifest.campaignId());
            verify(h.campaign,never()).openJ3SubOperation(any());
        }
    }

    @Test
    void emptyAndDuplicateSelectionsAreRejectedBeforeAdmissionOrProviderWork() throws Exception {
        try (Harness h = new Harness()) {
            assertThatThrownBy(() -> h.service.prepare(List.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_SELECTION_INVALID");
            assertThatThrownBy(() -> h.service.prepare(List.of(id(A), id(A))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_SELECTION_INVALID");
            verifyNoInteractions(h.admission, h.events, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void aSyntheticFixtureCannotAuthorizeALiveProviderCampaign() throws Exception {
        try (Harness h = new Harness()) {
            var source = EventSourceTrace.syntheticFixture("live-test-only", "b".repeat(64),
                    "event-details-v2", Instant.now());
            var selected = observation(A, source);
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(selected));
            assertThatThrownBy(() -> h.service.prepare(List.of(id(A))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_PROVIDER_PROVENANCE_REQUIRED");
            verify(h.store, never()).prepare(any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void anUncallableProviderIdIsRejectedWhileStillPreparing() throws Exception {
        try (Harness h = new Harness()) {
            long outOfRange = 1_000_000_000L;
            var source = EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", Instant.now());
            var selected = observation(outOfRange, source);
            when(h.events.findLatestByCanonicalId(id(outOfRange)))
                    .thenReturn(Optional.of(selected));
            assertThatThrownBy(() -> h.service.prepare(List.of(id(outOfRange))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_EVENT_ID_OUT_OF_RANGE");
            verify(h.store, never()).prepare(any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void preparationFreezesTheSelectedObservationAndSnapshotWithoutOpeningATransport() throws Exception {
        try (Harness h = new Harness()) {
            var source = EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", Instant.now());
            var selected = observation(A, source);
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(selected));
            when(h.admission.maximumBytesV5(1)).thenReturn(15_728_640_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));
            assertThat(h.properties.getGroupedV11().getQualificationSha256()).isEqualTo("1".repeat(64));
            assertThat(ReflectionTestUtils.getField(h.service, "properties")).isSameAs(h.properties);

            Manifest prepared = h.service.prepare(List.of(id(A)));

            assertThat(prepared.targets()).containsExactly(new Target(id(A), A, 17, 23));
            assertThat(prepared.maximumBytes()).isEqualTo(15_728_640_000L);
            assertThat(prepared.manifestSha256()).matches("[0-9a-f]{64}");
            assertThat(prepared.policyVersion()).isEqualTo("live-v11");
            assertThat(prepared.maximumCallsPerEvent()).isEqualTo(2500);
            assertThat(prepared.maximumCalls()).isEqualTo(20000);
            assertThat(prepared.cycleInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(prepared.admissionProfile().groupedProfile().qualificationSha256()).isEqualTo("1".repeat(64));
            assertThat(prepared.admissionProfile().groupedProfile().policyVersion()).isEqualTo("live-v9");
            assertThat(prepared.admissionProfile().qualificationSha256()).isEqualTo("a".repeat(64));
            assertThat(Duration.between(prepared.preparedAt(), prepared.expiresAt())).isEqualTo(Duration.ofMinutes(5));
            verify(h.admission).requireStorage(eq(15_728_640_000L));
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void aLocallyFinishedMatchIsExcludedWithoutAdmissionEvenWhenAllProviderSwitchesAreDisabled() throws Exception {
        try (Harness h = new Harness()) {
            var finished = providerObservation(A, "finished");
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(finished));
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            var preparation = h.service.prepareSelection(List.of(id(A)));

            assertThat(preparation.manifest()).isNull();
            assertThat(preparation.excludedFinished()).containsExactly(finished);
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void anEntireHistoricalDayIsExcludedBeforeTheOneMatchCapacityAndStorageChecks() throws Exception {
        try (Harness h = new Harness()) {
            h.properties.setQualifiedMatchCapacity(1);
            var finished = LongStream.range(A, A + 15).mapToObj(providerId -> {
                var event = providerObservation(providerId, "finished");
                when(h.events.findLatestByCanonicalId(id(providerId))).thenReturn(Optional.of(event));
                return event;
            }).toList();

            var preparation = h.service.prepareSelection(finished.stream().map(event -> event.identity().value()).toList());

            assertThat(preparation.manifest()).isNull();
            assertThat(preparation.excludedFinished()).containsExactlyElementsOf(finished);
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void mixedSelectionAdmitsAndFreezesOnlyTheUnfinishedMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.properties.setQualifiedMatchCapacity(1);
            var finished = providerObservation(A, "finished");
            var active = providerObservation(B, "inprogress");
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(finished));
            when(h.events.findLatestByCanonicalId(id(B))).thenReturn(Optional.of(active));
            when(h.admission.maximumBytesV5(1)).thenReturn(15_728_640_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var preparation = h.service.prepareSelection(List.of(id(A), id(B)));

            assertThat(preparation.excludedFinished()).containsExactly(finished);
            assertThat(preparation.manifest().targets()).containsExactly(new Target(id(B), B, 17, 23));
            assertThat(preparation.manifest().maximumBytes()).isEqualTo(15_728_640_000L);
            assertThat(preparation.manifest().qualifiedMatchCapacity()).isEqualTo(1);
            verify(h.admission).requireStorage(eq(15_728_640_000L));
            verify(h.admission, never()).requireStorage(eq(2L * 15_728_640_000L));
            verify(h.store).prepare(preparation.manifest());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void anEntirelyPostponedOrFinishedSelectionCreatesNoCampaignOrProviderWork(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            var postponed = providerObservation(A, "postponed");
            var other = providerObservation(B, otherStatus);
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(postponed));
            when(h.events.findLatestByCanonicalId(id(B))).thenReturn(Optional.of(other));
            h.properties.setQualifiedMatchCapacity(1);
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            var preparation = h.service.prepareSelection(List.of(id(A), id(B)));

            assertThat(preparation.manifest()).isNull();
            assertThat(preparation.excludedPostponed()).containsExactlyElementsOf(
                    "postponed".equals(otherStatus) ? List.of(postponed, other) : List.of(postponed));
            assertThat(preparation.excludedFinished()).containsExactlyElementsOf(
                    "finished".equals(otherStatus) ? List.of(other) : List.of());
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void mixedSelectionExcludesFinishedAndPostponedBeforeComputingTheManifestCapacityAndCadence() throws Exception {
        try (Harness h = new Harness()) {
            var finished = providerObservation(A, "finished");
            var postponed = providerObservation(B, "postponed");
            long eligible = B + 1;
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(finished));
            when(h.events.findLatestByCanonicalId(id(B))).thenReturn(Optional.of(postponed));
            h.observe(eligible, "notstarted");
            h.properties.setQualifiedMatchCapacity(1);
            when(h.admission.maximumBytesV5(1)).thenReturn(15_728_640_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var preparation = h.service.prepareSelection(List.of(id(A), id(eligible), id(B)));

            assertThat(preparation.excludedFinished()).containsExactly(finished);
            assertThat(preparation.excludedPostponed()).containsExactly(postponed);
            assertThat(preparation.manifest().targets()).containsExactly(new Target(id(eligible), eligible, 17, 23));
            assertThat(preparation.manifest().qualifiedMatchCapacity()).isEqualTo(1);
            assertThat(preparation.manifest().cycleInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(preparation.manifest().maximumBytes()).isEqualTo(15_728_640_000L);
            verify(h.admission).requireStorage(eq(15_728_640_000L));
            verify(h.admission, never()).requireStorage(eq(3L * 15_728_640_000L));
            verify(h.store).prepare(preparation.manifest());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void theManifestOnlyApiExplainsThatAPostponedOrMixedExcludedSelectionIsIneligible(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "postponed");
            h.observe(B, otherStatus);

            assertThatThrownBy(() -> h.service.prepare(List.of(id(A), id(B))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_ALL_EVENTS_INELIGIBLE");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void canceledRemainsAdmissibleToPreparationWithoutExtendingThePostponedRule() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "canceled");
            when(h.admission.maximumBytesV5(1)).thenReturn(15_728_640_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var preparation = h.service.prepareSelection(List.of(id(A)));

            assertThat(preparation.excludedFinished()).isEmpty();
            assertThat(preparation.excludedPostponed()).isEmpty();
            assertThat(preparation.manifest().targets()).containsExactly(new Target(id(A), A, 17, 23));
            verify(h.admission).requireStorage(eq(15_728_640_000L));
            verify(h.store).prepare(preparation.manifest());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void aForgedEventInAMixedSelectionIsRejectedBeforeAdmissionAndPersistence() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            UUID missing = id(B + 1);
            when(h.events.findLatestByCanonicalId(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> h.service.prepareSelection(List.of(id(A), id(B), missing)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_EVENT_NOT_FOUND");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void theRawSelectionLimitStillRejectsMoreThanOneHundredEventsBeforeLookingThemUp() throws Exception {
        try (Harness h = new Harness()) {
            var selected = LongStream.range(A, A + 101).mapToObj(LiveCampaignServiceTest::id).toList();
            assertThatThrownBy(() -> h.service.prepareSelection(selected))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_SELECTION_INVALID");
            verifyNoInteractions(h.events, h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void theManifestOnlyPreparationApiDoesNotReturnAnEmptyCampaignForFinishedMatches() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            assertThatThrownBy(() -> h.service.prepare(List.of(id(A))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_ALL_EVENTS_FINISHED");
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void matchesFinishedSincePreparationPreventLaunchBeforeOptInAdmissionAndOwnership() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            h.observe(B, "finished");
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_ALL_EVENTS_FINISHED");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).launch(any(), any(), any(), any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void postponedLatestObservationsPreventAnEntirelyExcludedLaunchBeforeOptInOrOwnership(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "postponed");
            h.observe(B, otherStatus);
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_ALL_EVENTS_INELIGIBLE");

            assertThat(h.campaignState).hasValue("PREPARED");
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).launch(any(), any(), any(), any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3"})
    void aMatchPostponedSincePreparationIsSkippedWithoutRewritingTheManifest(String policy) throws Exception {
        try (Harness h = new Harness(false, policy)) {
            h.observe(A, "postponed");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_POSTPONED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.receipts).hasSize(4);
            assertThat(h.reservations.get()).isEqualTo(4);
            assertThat(h.service.state(h.manifest.campaignId()).manifest()).isEqualTo(h.manifest);
            verify(h.admission).admit(1, h.manifest.cycleInterval());
            verify(h.store, never()).prepare(any());
            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.campaign).close();
        }
    }

    @Test
    void aMatchFinishedSincePreparationIsSkippedWhileTheOtherMatchCompletes() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.receipts).hasSize(4);
            assertThat(h.reservations.get()).isEqualTo(4);
            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.campaign).close();
        }
    }

    @Test
    void completionDuringOwnerLaunchIsRecheckedBeforeTheBrowserCanOpen() throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> {
                h.observe(A, "finished");
                h.observe(B, "finished");
            };

            h.launch();
            h.awaitFinished();

            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.dispatched).isEmpty();
            assertThat(h.reservations.get()).isZero();
            assertThat(h.receipts).isEmpty();
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void postponementDuringOwnerLaunchIsRecheckedBeforeAnyBrowserOrReservation(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> {
                h.observe(A, "postponed");
                h.observe(B, otherStatus);
            };

            h.launch();
            h.awaitFinished();

            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_POSTPONED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("postponed".equals(otherStatus)
                    ? "STOPPED_ALREADY_POSTPONED" : "STOPPED_ALREADY_FINISHED");
            assertThat(h.campaignState).hasValue("COMPLETED");
            assertThat(h.dispatched).isEmpty();
            assertThat(h.reservations.get()).isZero();
            assertThat(h.receipts).isEmpty();
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @Test
    void postponementOfOneMatchDuringOwnerLaunchDoesNotDispatchThatMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> h.observe(A, "postponed");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_POSTPONED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.reservations.get()).isEqualTo(4);
        }
    }

    @Test
    void aPostponedJ4ResponseIsPreservedAndStopsOnlyItsMatchWithoutJ5OrFinalCollection() throws Exception {
        try (Harness h = new Harness(false, "live-v3")) {
            h.reply = request -> request.eventId() == A ? response("""
                    {"event":{"id":%d,"startTimestamp":1788796800,
                      "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                      "status":{"type":"postponed"}}}
                    """.formatted(A), 200) : normalFinishedReply(request);

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(A, B, B, B, B);
            assertThat(h.dispatched.getFirst().endpoint()).isEqualTo(EVENT_DETAILS);
            assertThat(h.receipts).hasSize(5);
            assertThat(h.receipts.getFirst().parserVersion()).isEqualTo("event-details-v4");
            assertThat(h.reservations.get()).isEqualTo(5);
            assertThat(h.publications).anySatisfy(publication -> {
                assertThat(publication.sportStatus()).isEqualTo("postponed");
                assertThat(publication.outcome()).isEqualTo("PARSED");
                assertThat(publication.successful()).isTrue();
                assertThat(publication.nextEventState()).isEqualTo("STOPPED_POSTPONED");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_POSTPONED");
            assertThat(h.finalCompleteness.get(id(A))).isFalse();
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.campaignState).hasValue("COMPLETED");
            verify(h.campaign).close();
        }
    }

    @Test
    void completionOfOneMatchDuringOwnerLaunchCannotDispatchThatMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> h.observe(A, "finished");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.reservations.get()).isEqualTo(4);
        }
    }

    @Test
    void aMissingLocalObservationCannotAuthorizeLaunchingAnOlderManifest() throws Exception {
        try (Harness h = new Harness()) {
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.empty());

            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_EVENT_NOT_FOUND");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).launch(any(), any(), any(), any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void anExpiredManifestCannotAcquireTheProviderOrOpenATransport() throws Exception {
        try (Harness h = new Harness(true)) {
            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_MANIFEST_EXPIRED");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @Test
    void changingThePreparedEnvelopeRequiresANewManifestBeforeAnyProviderAcquisition() throws Exception {
        try (Harness h = new Harness()) {
            h.properties.setProcessingEnvelope(Duration.ofSeconds(2));
            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_PREPARED_POLICY_CHANGED");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3", "live-v4"})
    void historicalTimeoutsAboveTenSecondsAreRejectedBeforeProviderAcquisition(String policy) throws Exception {
        try (Harness h = new Harness(false, policy)) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(11));
            assertThatThrownBy(h::launch).isInstanceOf(IllegalStateException.class)
                    .hasMessage("LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @Test
    void v7InitialNotstartedGroupPublishesAllThreeFamiliesUnderExplicitPrematchAuthority() throws Exception {
        try (Harness h = new Harness(false, "live-v7", 1, true)) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(30));
            long kickoff = Instant.now().plusSeconds(7200).getEpochSecond();
            h.reply = request -> {
                if (request.endpoint() == EVENT_DETAILS) return response("""
                    {"event":{"id":%d,"startTimestamp":%d,"homeTeam":{"id":1,"name":"Home"},
                    "awayTeam":{"id":2,"name":"Away"},"status":{"type":"notstarted"}}}
                    """.formatted(request.eventId(), kickoff), 200);
                if (request.endpoint() == EVENT_LINEUPS) h.service.stop(h.manifest.campaignId(), null);
                return response("unavailable", 404);
            };
            h.launch(); h.awaitFinished();
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                    .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
            assertThat(h.attemptRequests).extracting(AttemptRequest::kind)
                    .containsExactly("J4_INITIAL", "J5_PREMATCH_INITIAL", "J5_PREMATCH_INITIAL", "J5_PREMATCH_INITIAL");
            assertThat(h.groupDispatches).extracting(LiveProviderDispatchGroup::phase)
                    .containsExactly(LiveProviderDispatchGroup.Phase.CHECK, LiveProviderDispatchGroup.Phase.PREMATCH,
                            LiveProviderDispatchGroup.Phase.PREMATCH, LiveProviderDispatchGroup.Phase.PREMATCH);
            assertThat(h.groupDispatches.stream().map(LiveProviderDispatchGroup::groupId).distinct()).hasSize(1);
            verify(h.admission).admitV7(1, h.manifest.admissionProfile().groupedProfile());
            verify(h.factory).openLiveGroupedV7(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.factory, never()).openLiveGroupedV6(any(), any());
        }
    }

    @Test
    void liveV5AcceptsTwentySecondsAndKeepsThePreparedAdmissionProfile() throws Exception {
        try (Harness h = new Harness(false, "live-v5", 1)) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(20));
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            h.awaitFinished();

            verify(h.admission).admitV5(1, h.manifest.admissionProfile().groupedProfile());
            verify(h.factory).openLiveGroupedV5(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            assertThat(h.dispatched).hasSize(4);
            assertThat(h.receipts).hasSize(4);
            assertThat(h.campaignState.get()).isEqualTo("COMPLETED");
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 20_001})
    void liveV5RejectsUnboundedTimeoutsBeforeProviderAcquisition(long millis) throws Exception {
        try (Harness h = new Harness(false, "live-v5", 1)) {
            h.playwright.setRequestTimeout(Duration.ofMillis(millis));
            assertThatThrownBy(h::launch).isInstanceOf(IllegalStateException.class)
                    .hasMessage("LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @Test
    void theHardDeadlineIsRecheckedAfterSqlAdmissionAndBeforeProviderDispatch() throws Exception {
        try (Harness h = new Harness()) {
            h.expireDuringDispatchAuthorization.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatchAuthorizations).hasSize(1);
            assertThat(h.dispatched).isEmpty();
            assertThat(h.receipts).isEmpty();
            assertThat(h.publications).singleElement().satisfies(publication ->
                    assertThat(publication.code()).isEqualTo("DISPATCH_CANCELLED"));
        }
    }

    @Test
    void launchingTheSameManifestTwiceWhileItIsRunningOpensExactlyOneTransport() throws Exception {
        try (Harness h = new Harness()) {
            h.holdBeforeDispatch.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            assertThat(h.waitingAtFence.await(3, TimeUnit.SECONDS)).isTrue();

            h.launch();
            h.releaseFence.countDown();
            h.awaitFinished();

            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.coordinator, times(1)).acquireLiveCampaign(h.manifest.campaignId());
            verify(h.store, times(1)).launch(any(), any(), any(), any());
            assertThat(h.dispatched).hasSize(8);
        }
    }

    @Test
    void recoveryLeavesAConfirmedActiveOwnerAloneAndNeverStartsPlaywright() throws Exception {
        try (Harness h = new Harness()) {
            ProcessHandle current = ProcessHandle.current();
            Instant started = current.info().startInstant().orElseThrow();
            Owner owner = new Owner(h.ownership.instanceId(), current.pid(), started);
            when(h.guard.snapshot()).thenReturn(new Guard("OWNED", h.manifest.campaignId(), owner, 1, Instant.now()));

            h.service.markProvenOrphanWithoutRestart();

            verify(h.store, never()).interruptOrphan(any(), any(), any());
            verify(h.guard, never()).requireCleanup(any(), any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void recoveryDoesNotTreatAnInaccessibleProcessStartTimeAsProofOfDeath() throws Exception {
        try (Harness h = new Harness(); var processes = mockStatic(ProcessHandle.class)) {
            ProcessHandle process = mock(ProcessHandle.class);
            ProcessHandle.Info info = mock(ProcessHandle.Info.class);
            Owner owner = new Owner(h.ownership.instanceId(), 1234, Instant.now().minusSeconds(60));
            when(h.guard.snapshot()).thenReturn(new Guard("OWNED", h.manifest.campaignId(), owner, 1, Instant.now()));
            processes.when(() -> ProcessHandle.of(1234)).thenReturn(Optional.of(process));
            when(process.isAlive()).thenReturn(true);
            when(process.info()).thenReturn(info);
            when(info.startInstant()).thenReturn(Optional.empty());

            h.service.markProvenOrphanWithoutRestart();

            verify(h.store, never()).interruptOrphan(any(), any(), any());
            verify(h.guard, never()).requireCleanup(any(), any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void recoveryMarksAnAbsentManualOwnerForExplicitCleanupWithoutInventingALiveCampaign() throws Exception {
        try (Harness h = new Harness(); var processes = mockStatic(ProcessHandle.class)) {
            UUID manualCampaign = UUID.randomUUID();
            Owner owner = new Owner(UUID.randomUUID(), 1234, Instant.now().minusSeconds(60));
            Guard manual = new Guard("OWNED", manualCampaign, owner, 1, Instant.now());
            when(h.guard.snapshot()).thenReturn(manual);
            when(h.store.find(manualCampaign)).thenReturn(Optional.empty());
            processes.when(() -> ProcessHandle.of(1234)).thenReturn(Optional.empty());

            h.service.markProvenOrphanWithoutRestart();

            verify(h.store).find(manualCampaign);
            verify(h.store, never()).interruptOrphan(any(), any(), any());
            verify(h.guard).requireCleanup(eq(manual.ownership()), any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recoveryMarksAnAbsentPreLaunchOwnerForExplicitCleanupWithoutInterruptingItsPreparation(boolean cancelled)
            throws Exception {
        try (Harness h = new Harness(); var processes = mockStatic(ProcessHandle.class)) {
            Owner owner = new Owner(h.ownership.instanceId(), 1234, Instant.now().minusSeconds(60));
            Guard preLaunch = new Guard("OWNED", h.manifest.campaignId(), owner, h.ownership.generation(), Instant.now());
            when(h.guard.snapshot()).thenReturn(preLaunch);
            when(h.store.find(h.manifest.campaignId()))
                    .thenReturn(Optional.of(unlaunchedPreparation(h.manifest, cancelled)));
            processes.when(() -> ProcessHandle.of(1234)).thenReturn(Optional.empty());

            h.service.markProvenOrphanWithoutRestart();

            verify(h.store).find(h.manifest.campaignId());
            verify(h.store, never()).interruptOrphan(any(), any(), any());
            verify(h.store, never()).completePreLaunchOrphanCleanup(any(), any());
            verify(h.guard).requireCleanup(eq(preLaunch.ownership()), any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void anIsolatedSchemaStopsItsMatchButTheOtherMatchReachesAllFinalFamilies() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = request -> request.eventId() == A
                    ? response("{\"event\":{\"id\":" + A + ",\"status\":null}}", 200)
                    : normalFinishedReply(request);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId)
                    .containsExactly(A, B, B, B, B);
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                    .containsExactly(EVENT_DETAILS, EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_SCHEMA_INCOMPATIBLE");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.publications).anySatisfy(publication -> {
                assertThat(publication.outcome()).isEqualTo("SCHEMA_INCOMPATIBLE");
                assertThat(publication.scope()).isEqualTo("EVENT");
            });
            assertThat(h.receipts).hasSize(5);
            verify(h.campaign).close();
        }
    }

    @Test
    void identityAndSchemaMismatchHasGlobalPriorityAndNeverReachesTheSecondMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = ignored -> response("{\"event\":{\"id\":19000000,\"status\":null}}", 200);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
                assertThat(publication.code()).isEqualTo("EVENT_ID_MISMATCH");
            });
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            assertThat(h.receipts).hasSize(1);
            verify(h.campaign).close();
        }
    }

    @Test
    void stopWhileWaitingAtTheDispatchFenceCancelsOnlyThatMatchBeforeAnyGet() throws Exception {
        try (Harness h = new Harness()) {
            h.holdBeforeDispatch.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            assertThat(h.waitingAtFence.await(3, TimeUnit.SECONDS)).isTrue();
            h.service.stop(h.manifest.campaignId(), id(A));
            h.releaseFence.countDown();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.publications).anySatisfy(publication -> {
                assertThat(publication.outcome()).isEqualTo("NOT_DISPATCHED");
                assertThat(publication.code()).isEqualTo("DISPATCH_CANCELLED");
            });
            assertThat(h.dispatchAuthorizations).hasSize(4);
            assertThat(h.receipts).hasSize(4);
            verify(h.supervisor, never()).stopCampaign(any(), any());
        }
    }

    @Test
    void publicationFailureKeepsTheRawReceiptAndStopsAllFurtherProviderWork() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.failFirstPublication.set(true);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
                assertThat(publication.code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            verify(h.campaign).close();
        }
    }

    @Test
    void aFailedLastFinalPublicationRevokesCompletenessButKeepsTheEarlierFinishedProof() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = request -> switch (request.endpoint()) {
                case EVENT_DETAILS -> normalFinishedReply(request);
                case EVENT_STATISTICS -> response("{\"statistics\":[]}", 200);
                case EVENT_INCIDENTS -> response("{\"incidents\":[]}", 200);
                case EVENT_LINEUPS -> response("{\"confirmed\":false}", 200);
                default -> throw new IllegalArgumentException("unexpected test endpoint");
            };
            h.failFinalLineupsPublication.set(true);
            h.launch();
            h.awaitFinished();

            assertThat(h.failedFinalPublication.get()).satisfies(publication -> {
                assertThat(publication.successful()).isTrue();
                assertThat(publication.nextEventState()).isEqualTo("FINISHED_CONFIRMED");
            });
            assertThat(h.dispatched).hasSize(5);
            assertThat(h.dispatched.getLast().eventId()).isEqualTo(A);
            assertThat(h.dispatched.getLast().endpoint()).isEqualTo(EVENT_LINEUPS);
            assertThat(h.receipts).hasSize(5);
            assertThat(h.publications).anySatisfy(publication ->
                    assertThat(publication.sportStatus()).isEqualTo("finished"));
            assertThat(h.publications.getLast().code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
            assertThat(h.publications.getLast().scope()).isEqualTo("CAMPAIGN");
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            assertThat(h.finalCompleteness.get(id(A))).isFalse();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            verify(h.campaign).close();
        }
    }

    @Test
    void failingToPublishAnIsolatedSchemaCannotLeaveAFalseDurableSchemaStop() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = ignored -> response("{\"event\":{\"id\":" + A + ",\"status\":null}}", 200);
            h.failFirstPublication.set(true);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
        }
    }

    @Test
    void aGlobalStopAfterDispatchPreservesTheResponseWithoutSchedulingAnotherMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.holdAfterDispatch.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            assertThat(h.getInFlight.await(3, TimeUnit.SECONDS)).isTrue();
            h.service.stop(h.manifest.campaignId(), null);
            h.releaseGet.countDown();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_OPERATOR");
            verify(h.supervisor).stopCampaign(eq(h.manifest.campaignId()), eq(LiveProviderSession.ENDPOINTS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3"})
    void launchRespectsThePreparedPolicyForPrematchLineupsAndPersistsUnavailableEvidence(String policy) throws Exception {
        try (Harness h = new Harness(false, policy)) {
            AtomicInteger details = new AtomicInteger(), lineups = new AtomicInteger();
            h.reply = request -> {
                if (request.endpoint() == EVENT_DETAILS) {
                    if (details.incrementAndGet() == 2 && !"live-v3".equals(policy))
                        h.service.stop(h.manifest.campaignId(), null);
                    return response("""
                            {"event":{"id":%d,"startTimestamp":1788796800,
                            "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                            "status":{"type":"notstarted"}}}
                            """.formatted(request.eventId()), 200);
                }
                assertThat(request.endpoint()).isEqualTo(EVENT_LINEUPS);
                if (lineups.incrementAndGet() == 2) h.service.stop(h.manifest.campaignId(), null);
                return response("unavailable", 404);
            };
            h.launch();
            h.awaitFinished();
            assertThat(details).hasValue(2);
            assertThat(lineups).hasValue("live-v3".equals(policy) ? 2 : 0);
            assertThat(h.dispatched).noneSatisfy(request ->
                    assertThat(request.endpoint()).isIn(EVENT_STATISTICS, EVENT_INCIDENTS));
            assertThat(h.receipts).hasSize("live-v3".equals(policy) ? 4 : 2);
            assertThat(h.publications.stream().filter(p -> "HTTP_404".equals(p.code())).count())
                    .isEqualTo("live-v3".equals(policy) ? 2 : 0);
            assertThat(h.eventStates.values()).containsOnly("STOPPED_OPERATOR");
            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
        }
    }

    @Test
    void aV9QualificationCannotAuthorizeEightNewV10GroupedMatches() throws Exception {
        try(Harness h=new Harness()) {
            h.properties.setQualifiedMatchCapacity(8);
            h.properties.getGroupedV11().setQualificationSha256("");
            List<UUID> selected=LongStream.range(A,A+8).mapToObj(providerId->{h.observe(providerId,"inprogress");return id(providerId);}).toList();
            assertThatThrownBy(()->h.service.prepare(selected)).hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
            verify(h.store,never()).prepare(any());
            verifyNoInteractions(h.factory,h.coordinator,h.admission);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v4", "live-v5", "live-v6"})
    void groupedLaunchRoutesEachEndpointThroughOneOrderedAuditableGroupAndPublishesFamilySchedules(String policy) throws Exception {
        try(Harness h=new Harness(false,policy,1)) {
            h.reply=LiveCampaignServiceTest::normalFinishedReply;
            h.launch();h.awaitFinished();
            if ("live-v6".equals(policy)) verify(h.factory).openLiveGroupedV6(h.manifest.campaignId(),LiveProviderSession.ENDPOINTS);
            else if ("live-v5".equals(policy)) verify(h.factory).openLiveGroupedV5(h.manifest.campaignId(),LiveProviderSession.ENDPOINTS);
            else verify(h.factory).openLiveGrouped(h.manifest.campaignId(),LiveProviderSession.ENDPOINTS);
            verify(h.factory,never()).open(any(),any());
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                    .containsExactly(EVENT_DETAILS,EVENT_INCIDENTS,EVENT_STATISTICS,EVENT_LINEUPS);
            assertThat(h.groupDispatches).hasSize(4);
            assertThat(h.attemptRequests).extracting(AttemptRequest::groupId).doesNotContainNull().containsOnly(h.attemptRequests.getFirst().groupId());
            assertThat(h.attemptRequests).extracting(AttemptRequest::groupOrdinal).containsExactly(0,1,2,3);
            assertThat(h.dispatchAuthorizations).hasSize(4);
            assertThat(h.receipts).hasSize(4);
            assertThat(h.publications).hasSize(4);
            verify(h.store,org.mockito.Mockito.atLeastOnce()).updateFamilySchedule(eq(h.ownership),eq(id(A)),any(),any());
            verify(h.campaign,never()).execute(any(),any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v4", "live-v5", "live-v6"})
    void groupedPostponementPublishesJ4AndCancelsTheRestOfTheGroup(String policy) throws Exception {
        try(Harness h=new Harness(false,policy,1)) {
            h.reply=request->response("""
                {"event":{"id":%d,"startTimestamp":1788796800,"homeTeam":{"id":1,"name":"Home"},
                "awayTeam":{"id":2,"name":"Away"},"status":{"type":"postponed"}}}
                """.formatted(A),200);
            h.launch();h.awaitFinished();
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint).containsExactly(EVENT_DETAILS);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_POSTPONED");
            assertThat(h.publications).singleElement().satisfies(result->assertThat(result.sportStatus()).isEqualTo("postponed"));
            assertThat(h.finalCompleteness.get(id(A))).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v4", "live-v5", "live-v6"})
    void operatorStopBetweenGroupedResponsesPreventsEveryRemainingReservation(String policy) throws Exception {
        try(Harness h=new Harness(false,policy,1)) {
            h.reply=request->{h.service.stop(h.manifest.campaignId(),id(A));return normalFinishedReply(request);};
            h.launch();h.awaitFinished();
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint).containsExactly(EVENT_DETAILS);
            assertThat(h.reservations).hasValue(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v4", "live-v5", "live-v6"})
    void aGroupedOrdinaryBudgetRefusalUsesTheProtectedFinalReserveWithoutAnOrdinaryDispatch(String policy) throws Exception {
        try (Harness h = new Harness(false, policy, 1)) {
            when(h.store.dispatchBudget(eq(h.ownership), any())).thenReturn(new DispatchBudget(2992, 0, 20, "COLLECTING"));
            h.refuseReservation = request -> !request.finalCycle();
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch(); h.awaitFinished();
            assertThat(h.attemptRequests).hasSize(4).allMatch(AttemptRequest::finalCycle);
            assertThat(h.attemptRequests).extracting(AttemptRequest::kind)
                    .containsExactly("J4_FINAL_CHECK", "J5_FINAL", "J5_FINAL", "J5_FINAL");
            assertThat(h.attemptRequests).extracting(AttemptRequest::groupSequence).containsOnly(1L);
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                    .containsExactly(EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
            assertThat(h.eventStates.get(id(A))).isEqualTo("FINISHED_CONFIRMED");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v4", "live-v5", "live-v6"})
    void aGroupedFinalReservationRefusalStopsWithoutRetryOrProviderWork(String policy) throws Exception {
        try (Harness h = new Harness(false, policy, 1)) {
            h.refuseReservation = request -> true;
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch(); h.awaitFinished();
            verify(h.store, times(2)).reserveAttempt(any());
            assertThat(h.reservations).hasValue(0);
            assertThat(h.dispatched).isEmpty();
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_LIMIT");
        }
    }

    @Test
    void aFrozenV4PreparationUsesOnlyItsOwnProfileEvenWithoutACurrentV7Qualification() throws Exception {
        try (Harness h = new Harness(false, "live-v4", 1)) {
            h.properties.getGroupedV7().setQualificationSha256("");
            // New preparations use V10. A frozen V4 launch remains governed by its own profile.
            assertThat(h.service.selectionMaximum()).isEqualTo(2);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch(); h.awaitFinished();
            verify(h.admission).admitV4(eq(1), eq(h.manifest.admissionProfile().groupedProfile()));
            verify(h.admission, never()).admitV5(anyInt(), any());
            verify(h.admission, never()).admitV8(anyInt(), any());
            verify(h.factory).openLiveGrouped(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.factory, never()).openLiveGroupedV5(any(), any());
            assertThat(h.manifest.policyVersion()).isEqualTo("live-v4");
        }
    }

    @Test
    void changingOnlyTheV5ProfileRefusesAFrozenV5PreparationBeforeOpeningTransport() throws Exception {
        try (Harness h = new Harness(false, "live-v5", 1)) {
            h.properties.getGroupedV5().setQualificationSha256("e".repeat(64));
            assertThatThrownBy(h::launch).hasMessage("LIVE_PREPARED_POLICY_CHANGED");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
        }
    }

    @Test
    void aDurableProviderSuspensionRejectsV6BeforeOpeningOrAcquiringAnyProviderSession() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            Instant refused=Instant.now().minusSeconds(10);
            when(h.resilience.snapshot()).thenReturn(new ProviderResilienceData.Snapshot(ProviderResilienceData.State.SUSPENDED,
                    3,refused,403,refused,null,null,UUID.randomUUID(),UUID.randomUUID(),null,null));
            assertThatThrownBy(h::launch).hasMessage("PROVIDER_SUSPENDED");
            verifyNoInteractions(h.factory,h.coordinator,h.campaign);
            verify(h.store,never()).launch(any(),any(),any(),any());
            verify(h.store,never()).reserveAttempt(any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void anUnresolvedDepartureFromAPreviousProcessRejectsV6BeforeTransportCanOpen() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            Instant reserved=Instant.now().minusSeconds(86400);
            when(h.resilience.snapshot()).thenReturn(new ProviderResilienceData.Snapshot(ProviderResilienceData.State.OPEN,
                    0,reserved,null,null,null,reserved,null,null,null,UUID.randomUUID()));
            assertThatThrownBy(h::launch).hasMessage("PROVIDER_DEPARTURE_UNRESOLVED");
            verifyNoInteractions(h.factory,h.coordinator,h.campaign);
            verify(h.store,never()).launch(any(),any(),any(),any());
        }
    }

    @Test
    void v6AcceptsThirtySecondsButRejectsARequestTimeoutBeyondItsOwnCeiling() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(30));
            h.reply=LiveCampaignServiceTest::normalFinishedReply;
            h.launch();h.awaitFinished();
            verify(h.admission).admitV6(1,h.manifest.admissionProfile().groupedProfile());
            assertThat(h.dispatched).hasSize(4);
        }
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(31));
            assertThatThrownBy(h::launch).hasMessage("LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY");
            verifyNoInteractions(h.factory,h.coordinator,h.campaign);
        }
    }

    @Test
    void v8UsesItsIsolatedDepartureProfileWhileKeepingTheSixtySecondManifestCadence() throws Exception {
        try (Harness h = new Harness(false, "live-v8", 1, true)) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(30));
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch(); h.awaitFinished();

            verify(h.admission).admitV8(1, h.manifest.admissionProfile().groupedProfile());
            verify(h.factory).openLiveGroupedV8(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.resilience, times(4)).departureDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V8), any());
            assertThat(h.manifest.cycleInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(h.dispatched).hasSize(4);
        }
    }

    @Test
    void v8LaunchRechecksSharedPressureOnlyAfterItOwnsTheCampaignLease() throws Exception {
        try (Harness h = new Harness(false, "live-v8", 1, true)) {
            ProviderResilienceData.Snapshot open=h.resilience.snapshot();
            AtomicBoolean sharedTrafficReachedTheLeaseBoundary = new AtomicBoolean();
            when(h.lease.ownership()).thenAnswer(invocation -> {
                // Model a completed shared departure between the HTTP-thread
                // preparation checks and durable live-lease acquisition.
                sharedTrafficReachedTheLeaseBoundary.set(true);
                return h.ownership;
            });
            when(h.resilience.departureCapacityDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V8),eq(4),any()))
                    .thenAnswer(invocation -> new ProviderResilienceData.DepartureDecision(
                            !sharedTrafficReachedTheLeaseBoundary.get(),
                            sharedTrafficReachedTheLeaseBoundary.get()
                                    ? ProviderResilienceData.DepartureReason.RATE_LIMITED
                                    : ProviderResilienceData.DepartureReason.ALLOWED,
                            Instant.now().plusSeconds(30),open));

            assertThatThrownBy(h::launch).hasMessage("LIVE_V8_FRESHNESS_CAPACITY_UNAVAILABLE");
            assertThat(h.finished.await(4, TimeUnit.SECONDS)).isTrue();

            InOrder admissionOrder=inOrder(h.coordinator,h.lease,h.resilience);
            admissionOrder.verify(h.coordinator).acquireLiveCampaign(h.manifest.campaignId());
            admissionOrder.verify(h.lease).ownership();
            admissionOrder.verify(h.resilience).departureCapacityDecision(
                    eq(ProviderResilienceData.DepartureProfile.LIVE_V8),eq(4),any());
            verify(h.store,never()).launch(any(),any(),any(),any());
            verify(h.factory,never()).openLiveGroupedV8(any(),any());
            verifyNoInteractions(h.campaign);
            verify(h.lease).close();
            assertThat(h.campaignState).hasValue("PREPARED");
        }
    }

    @Test
    void v8RephasesItsPendingFamiliesFromTheReturnedWorkerRequestedAt() throws Exception {
        try (Harness h = new Harness(false, "live-v8", 1, true)) {
            AtomicReference<Instant> j4RequestedAt = new AtomicReference<>();
            h.reply = request -> {
                // Bind the worker-side timestamp to the persisted J4 due time,
                // rather than the wall clock at response construction.  V8
                // accepts at most its declared 500 ms emission head-start
                // bound; a scheduling or GC delay in this test must not turn
                // an otherwise valid fixture into a cadence recheck.
                Instant requestedAt = request.endpoint() == EVENT_DETAILS
                        ? h.attemptRequests.getLast().dueAt().plus(LiveSchedule.v8RequestEmissionHeadStart())
                        : Instant.now();
                if (request.endpoint() != EVENT_DETAILS) return response("unavailable", 404, requestedAt);
                j4RequestedAt.set(requestedAt);
                return response("""
                        {"event":{"id":%d,"startTimestamp":1788796800,
                        "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                        "status":{"type":"inprogress"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                        """.formatted(request.eventId()), 200, requestedAt);
            };

            h.launch();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                Instant requestedAt = j4RequestedAt.get();
                LiveSessionSchedule schedule = h.activeSchedule();
                if (requestedAt != null && schedule != null && schedule.states().getFirst().nextDueAt()
                        .equals(requestedAt.plusMillis(1_100))) break;
                Thread.sleep(10);
            }

            assertThat(j4RequestedAt).hasValueSatisfying(requestedAt ->
                    assertThat(h.activeSchedule().states().getFirst().nextDueAt())
                            .isEqualTo(requestedAt.plusMillis(1_100)));
            h.service.stop(h.manifest.campaignId(), null);
            h.awaitFinished();
        }
    }

    @Test
    void v8RateLimitedInPlayFamilyPublishesPressureRecheckBeforeItsFreshJ4() throws Exception {
        try (Harness h = new Harness(false, "live-v8", 1, true)) {
            AtomicInteger departures = new AtomicInteger();
            AtomicReference<Instant> notBefore = new AtomicReference<>();
            ProviderResilienceData.Snapshot open = h.resilience.snapshot();
            when(h.resilience.departureDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V8), any()))
                    .thenAnswer(invocation -> {
                        Instant now = invocation.getArgument(1);
                        if (departures.incrementAndGet() == 2) {
                            Instant nextAllowed = now.plusSeconds(1);
                            notBefore.set(nextAllowed);
                            return new ProviderResilienceData.DepartureDecision(false,
                                    ProviderResilienceData.DepartureReason.RATE_LIMITED, nextAllowed, open);
                        }
                        return new ProviderResilienceData.DepartureDecision(true,
                                ProviderResilienceData.DepartureReason.ALLOWED, now, open);
                    });
            AtomicInteger j4Responses = new AtomicInteger();
            CountDownLatch pressureRecheckDispatched = new CountDownLatch(1);
            CountDownLatch releasePressureRecheck = new CountDownLatch(1);
            h.reply = request -> {
                if (request.endpoint() != EVENT_DETAILS) return response("unavailable", 404);
                if (j4Responses.incrementAndGet() > 1) {
                    pressureRecheckDispatched.countDown();
                    try {
                        if (!releasePressureRecheck.await(3, TimeUnit.SECONDS))
                            throw new IllegalStateException("pressure recheck was not released");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("pressure recheck interrupted", interrupted);
                    }
                    return normalFinishedReply(request);
                }
                return response("""
                        {"event":{"id":%d,"startTimestamp":1788796800,
                        "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                        "status":{"type":"inprogress"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                        """.formatted(request.eventId()), 200);
            };

            h.launch();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline && !"WAITING_PRESSURE_RECHECK".equals(h.eventStates.get(id(A))))
                Thread.sleep(10);

            assertThat(h.eventStates.get(id(A))).isEqualTo("WAITING_PRESSURE_RECHECK");
            assertThat(notBefore).hasValueSatisfying(nextAllowed -> {
                LiveSessionSchedule schedule = h.activeSchedule();
                assertThat(schedule.states().getFirst()).satisfies(state -> {
                    assertThat(state.state()).isEqualTo("WAITING_PRESSURE_RECHECK");
                    assertThat(state.nextDueAt()).isEqualTo(nextAllowed);
                    assertThat(state.missedCycles()).isEqualTo(1);
                });
                assertThat(schedule.familySchedules(id(A))).extracting(FamilySchedule::missedCycles)
                        .containsExactly(0L, 1L, 1L, 1L);
            });

            try {
                assertThat(pressureRecheckDispatched.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(h.attemptRequests).extracting(AttemptRequest::kind)
                        .containsExactly("J4_INITIAL", "J4_PRESSURE_RECHECK");
                // Attempt records retain the repository's existing microsecond precision;
                // the in-memory schedule above has already asserted the exact release instant.
                assertThat(h.attemptRequests.get(1).dueAt()).isEqualTo(notBefore.get()
                        .truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                        .containsExactly(EVENT_DETAILS, EVENT_DETAILS);
                h.service.stop(h.manifest.campaignId(), null);
            } finally {
                releasePressureRecheck.countDown();
            }
            h.awaitFinished();
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
        }
    }

    @Test
    void v8PostExchangeFenceKeepsThePendingFamilyCollectingWithoutAPressureRecheck() throws Exception {
        try (Harness h = new Harness(false, "live-v8", 1, true)) {
            AtomicInteger decisions = new AtomicInteger();
            AtomicReference<Instant> fenceRelease = new AtomicReference<>();
            ProviderResilienceData.Snapshot open = h.resilience.snapshot();
            when(h.resilience.departureDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V8), any()))
                    .thenAnswer(invocation -> {
                        Instant now = invocation.getArgument(1);
                        if (decisions.incrementAndGet() == 2) {
                            Instant notBefore = now.plusSeconds(1);
                            fenceRelease.set(notBefore);
                            return new ProviderResilienceData.DepartureDecision(false,
                                    ProviderResilienceData.DepartureReason.POST_EXCHANGE_FENCE, notBefore, open);
                        }
                        return new ProviderResilienceData.DepartureDecision(true,
                                ProviderResilienceData.DepartureReason.ALLOWED, now, open);
                    });
            h.reply = request -> request.endpoint() == EVENT_DETAILS
                    ? response("""
                            {"event":{"id":%d,"startTimestamp":1788796800,
                            "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                            "status":{"type":"inprogress"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                            """.formatted(request.eventId()), 200)
                    : response("unavailable", 404);

            h.launch();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                Instant notBefore = fenceRelease.get();
                LiveSessionSchedule schedule = h.activeSchedule();
                if (notBefore != null && schedule != null && schedule.states().getFirst().state().equals("COLLECTING")
                        && schedule.states().getFirst().missedCycles() == 0
                        && notBefore.equals(schedule.states().getFirst().nextDueAt())) break;
                Thread.sleep(10);
            }

            assertThat(fenceRelease).hasValueSatisfying(notBefore -> {
                LiveSessionSchedule schedule = h.activeSchedule();
                assertThat(schedule.states().getFirst()).satisfies(state -> {
                    assertThat(state.state()).isEqualTo("COLLECTING");
                    assertThat(state.missedCycles()).isZero();
                    assertThat(state.nextDueAt()).isEqualTo(notBefore);
                });
                assertThat(schedule.familySchedules(id(A))).extracting(FamilySchedule::missedCycles)
                        .containsExactly(0L, 0L, 0L, 0L);
            });
            assertThat(h.attemptRequests).extracting(AttemptRequest::kind).containsExactly("J4_INITIAL");
            h.service.stop(h.manifest.campaignId(), null);
            h.awaitFinished();
        }
    }

    @Test
    void aKnown403RemainsTheDurableFirstCauseWhenReadingTheBodyTimesOut() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            Instant requested=Instant.now();
            var headers=new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,
                    30000,requested,requested.plusMillis(1),403,null,false);
            h.playwright.setRequestTimeout(Duration.ofSeconds(30));
            h.transportProgress=dispatch->dispatch.onTransportProgress(headers);
            h.reply=request->{throw new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT,
                    headers.at(PlaywrightTransportDiagnostic.Phase.READING_BODY));};
            h.launch();h.awaitFinished();
            assertThat(h.receipts).isEmpty();
            verify(h.store,never()).saveReceipt(any(),any(),any());
            verifyNoInteractions(h.processor);
            assertThat(h.publications).singleElement().satisfies(result->assertThat(result.code()).isEqualTo("PLAYWRIGHT_TIMEOUT"));
            assertThat(h.savedDiagnostics.get(LiveDiagnosticStore.Kind.FIRST_FAILURE)).satisfies(cause->{
                assertThat(cause.code()).isEqualTo("PROVIDER_HTTP_403");
                assertThat(cause.phase()).isEqualTo(LiveCampaignDiagnostic.Phase.TRANSPORT);
                assertThat(cause.transport().httpStatus()).isEqualTo(403);
                assertThat(cause.transport().responseComplete()).isFalse();
                assertThat(cause.attemptId()).isEqualTo(h.attemptRequests.getFirst().attemptId());
            });
            assertThat(h.savedTransport.get(h.attemptRequests.getFirst().attemptId()).phase())
                    .isEqualTo(PlaywrightTransportDiagnostic.Phase.READING_BODY);
            LiveCampaignService restarted=h.newService();
            try {
                var status=restarted.runtimeStatus(h.manifest.campaignId()).orElseThrow();
                assertThat(status.state()).isEqualTo("STOPPED_ERROR");
                assertThat(status.collectionStopped()).isTrue();
                assertThat(status.cleanupPending()).isFalse();
                assertThat(status.firstFailure().code()).isEqualTo("PROVIDER_HTTP_403");
                assertThat(status.cleanupFailure()).isNull();
            } finally {restarted.shutdown();}
            verify(h.factory,times(1)).openLiveGroupedV6(h.manifest.campaignId(),LiveProviderSession.ENDPOINTS);
        }
    }

    @Test
    void aProvenIsolatedTimeoutIsPublishedWithoutReceiptOrStopAndWaitsForANewGroup() throws Exception {
        try (Harness h = new Harness(false, "live-v6", 1, true, Duration.ofMinutes(15))) {
            h.reply = request -> { throw endedTimeout(); };
            h.launch();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (h.publications.isEmpty() && System.nanoTime() < until) Thread.sleep(10);
            assertThat(h.publications).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo("PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED");
                assertThat(p.scope()).isEqualTo("NONE");
                assertThat(p.successful()).isFalse();
            });
            assertThat(h.savedTransport.values()).singleElement().satisfies(d -> assertThat(d.contextReusable()).isTrue());
            assertThat(h.receipts).isEmpty();
            verifyNoInteractions(h.processor);
            assertThat(h.savedDiagnostics).isEmpty();
            assertThat(h.service.runtimeStatus(h.manifest.campaignId())).isEmpty();
            assertThat(h.campaignState.get()).isEqualTo("RUNNING");
            Thread.sleep(150);
            assertThat(h.dispatched).hasSize(1);
            h.service.stop(h.manifest.campaignId(), null);
            h.awaitFinished();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_OPERATOR");
            assertThat(h.savedDiagnostics).isEmpty();
        }
    }

    @Test
    void aProvenFinalTimeoutDoesNotRetryOrClaimFinalCompleteness() throws Exception {
        try (Harness h = new Harness(false, "live-v6", 1, true)) {
            h.reply = request -> {
                if (request.endpoint() != EVENT_DETAILS) throw endedTimeout();
                return normalFinishedReply(request);
            };
            h.launch(); h.awaitFinished();
            assertThat(h.dispatched).hasSize(2);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.publications.getLast().code()).isEqualTo("PLAYWRIGHT_TIMEOUT_FINAL");
            assertThat(h.publications.getLast().scope()).isEqualTo("EVENT");
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.finalCompleteness.get(id(A))).isFalse();
            assertThat(h.savedDiagnostics).isEmpty();
        }
    }

    @Test
    void failedDurablePublicationOfARecoverableTimeoutPreventsFurtherDispatch() throws Exception {
        try (Harness h = new Harness(false, "live-v6", 1, true, Duration.ofMinutes(15))) {
            h.reply = request -> { throw endedTimeout(); };
            h.failFirstPublication.set(true);
            h.launch(); h.awaitFinished();
            assertThat(h.dispatched).hasSize(1);
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.receipts).isEmpty();
            assertThat(h.savedDiagnostics.get(LiveDiagnosticStore.Kind.FIRST_FAILURE).phase())
                    .isEqualTo(LiveCampaignDiagnostic.Phase.RESULT_PUBLICATION);
        }
    }

    @Test
    void evenATerminalTimeoutCannotChangeTheHistoricalV5StopPolicy() throws Exception {
        try (Harness h = new Harness(false, "live-v5", 1, true)) {
            h.reply = request -> { throw endedTimeout(); };
            h.launch(); h.awaitFinished();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            assertThat(h.publications.getFirst().code()).isEqualTo("PLAYWRIGHT_TIMEOUT");
        }
    }

    private static PlaywrightProviderException endedTimeout() {
        Instant requested = Instant.now().minusSeconds(30);
        return new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT,
                new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.REQUEST_SENT,
                        30000, requested, null, null, null, false, requested.plusSeconds(30),
                        PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED, true));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void operatorStopBeforeOrDuringTimeoutPublicationWinsWithoutRetry(boolean global, boolean beforeFailure) throws Exception {
        try (Harness h = new Harness(false, "live-v6", 1, true, Duration.ofMinutes(15))) {
            Runnable stop = () -> h.service.stop(h.manifest.campaignId(), global ? null : id(A));
            if (!beforeFailure) {
                doAnswer(invocation -> {
                    PlaywrightTransportDiagnostic proof = invocation.getArgument(3);
                    h.savedTransport.put(invocation.getArgument(1), proof);
                    if (proof.contextReusable()) stop.run();
                    return null;
                }).when(h.diagnostics).recordTransport(any(), any(), any(), any());
            }
            h.reply = request -> {
                if (beforeFailure) stop.run();
                throw endedTimeout();
            };
            h.launch(); h.awaitFinished();
            assertThat(h.dispatched).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo("PLAYWRIGHT_TIMEOUT_ABANDONED");
                assertThat(p.nextEventState()).isEqualTo("STOPPED_OPERATOR");
            });
            assertThat(h.savedDiagnostics).isEmpty();
            assertThat(h.campaignState.get()).isEqualTo(global ? "STOPPED_OPERATOR" : "COMPLETED");
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stoppingOneMatchOnProvenTimeoutStillAllowsTheOtherMatchToComplete(boolean beforeFailure) throws Exception {
        try (Harness h = new Harness(false, "live-v6", 2, true, Duration.ofMinutes(15))) {
            AtomicBoolean stopped = new AtomicBoolean();
            Runnable stopFirst = () -> {
                if (!stopped.compareAndSet(false, true)) return;
                h.service.stop(h.manifest.campaignId(), id(A));
                // The second match's nominal phase is 50 seconds later. Advance only the
                // harness session's monotone origin, retaining the real next/reserve/dispatch path.
                AtomicReference<?> active = (AtomicReference<?>) ReflectionTestUtils.getField(h.service, "active");
                Object session = active.get();
                long origin = (long) ReflectionTestUtils.getField(session, "monotonicOrigin");
                ReflectionTestUtils.setField(session, "monotonicOrigin", origin - TimeUnit.SECONDS.toNanos(51));
            };
            if (!beforeFailure) {
                doAnswer(invocation -> {
                    PlaywrightTransportDiagnostic proof = invocation.getArgument(3);
                    h.savedTransport.put(invocation.getArgument(1), proof);
                    if (proof.contextReusable()) stopFirst.run();
                    return null;
                }).when(h.diagnostics).recordTransport(any(), any(), any(), any());
            }
            h.reply = request -> {
                if (request.eventId() == A) {
                    if (beforeFailure) stopFirst.run();
                    throw endedTimeout();
                }
                return normalFinishedReply(request);
            };
            h.launch(); h.awaitFinished();
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(A, B, B, B, B);
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                    .containsExactly(EVENT_DETAILS, EVENT_DETAILS, EVENT_INCIDENTS, EVENT_STATISTICS, EVENT_LINEUPS);
            assertThat(h.attemptRequests).filteredOn(attempt -> attempt.canonicalEventId().equals(id(A))).hasSize(1);
            assertThat(h.publications.getFirst()).satisfies(p -> {
                assertThat(p.code()).isEqualTo("PLAYWRIGHT_TIMEOUT_ABANDONED");
                assertThat(p.scope()).isEqualTo("EVENT");
                assertThat(p.nextEventState()).isEqualTo("STOPPED_OPERATOR");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.campaignState.get()).isEqualTo("COMPLETED");
            assertThat(h.receipts).hasSize(4);
            assertThat(h.savedDiagnostics).isEmpty();
            verify(h.supervisor, never()).stopCampaign(any(), any());
        }
    }

    @Test
    void aCleanupFailureIsPersistedSeparatelyAndDoesNotReplaceTheTransportFailure() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            h.reply=request->{throw new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT);};
            AtomicBoolean failClose=new AtomicBoolean(true);
            doAnswer(invocation->{if(failClose.get())throw new IllegalStateException("synthetic close failure");return null;})
                    .when(h.campaign).close();
            try {
                h.launch();h.awaitCleanupPending();
                assertThat(h.savedDiagnostics.get(LiveDiagnosticStore.Kind.FIRST_FAILURE).code()).isEqualTo("PLAYWRIGHT_TIMEOUT");
                assertThat(h.savedDiagnostics.get(LiveDiagnosticStore.Kind.CLEANUP_FAILURE)).satisfies(cleanup->{
                    assertThat(cleanup.phase()).isEqualTo(LiveCampaignDiagnostic.Phase.CLEANUP_TRANSPORT_CLOSE);
                    assertThat(cleanup.code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
                });
                verify(h.lease,never()).close();
            } finally {
                failClose.set(false);h.service.stop(h.manifest.campaignId(),null);h.awaitFinished();
            }
            LiveCampaignService restarted=h.newService();
            try {
                var status=restarted.runtimeStatus(h.manifest.campaignId()).orElseThrow();
                assertThat(status.firstFailure().code()).isEqualTo("PLAYWRIGHT_TIMEOUT");
                assertThat(status.cleanupFailure().phase()).isEqualTo(LiveCampaignDiagnostic.Phase.CLEANUP_TRANSPORT_CLOSE);
                assertThat(status.cleanupPending()).isFalse();
            } finally {restarted.shutdown();}
            assertThat(h.dispatched).hasSize(1);
        }
    }

    @Test
    void aDiagnosticPersistenceFailureKeepsProviderOwnershipUntilTheSameEvidenceCanBeSavedDuringExplicitCleanup() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            h.reply=request->{throw new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT);};
            AtomicBoolean failPersistence=new AtomicBoolean(true);
            doAnswer(invocation->{
                LiveDiagnosticStore.Kind kind=invocation.getArgument(1);LiveCampaignDiagnostic diagnostic=invocation.getArgument(2);
                if(kind==LiveDiagnosticStore.Kind.FIRST_FAILURE && failPersistence.get())
                    throw new IllegalStateException("synthetic SQL unavailable");
                if(kind==LiveDiagnosticStore.Kind.FIRST_FAILURE) h.savedDiagnostics.putIfAbsent(kind,diagnostic);
                else h.savedDiagnostics.put(kind,diagnostic);
                return null;
            }).when(h.diagnostics).recordFailure(any(),any(),any());
            LiveCampaignDiagnostic first;
            try {
                h.launch();h.awaitCleanupPending();
                first=h.service.runtimeStatus(h.manifest.campaignId()).orElseThrow().firstFailure();
                assertThat(first.code()).isEqualTo("PLAYWRIGHT_TIMEOUT");
                assertThat(h.service.runtimeStatus(h.manifest.campaignId()).orElseThrow().cleanupFailure().phase())
                        .isEqualTo(LiveCampaignDiagnostic.Phase.CLEANUP_DIAGNOSTIC_PUBLICATION);
                assertThat(h.savedDiagnostics).doesNotContainKey(LiveDiagnosticStore.Kind.FIRST_FAILURE);
                verify(h.lease,never()).close();
                verify(h.guard,org.mockito.Mockito.atLeastOnce()).requireCleanup(eq(h.ownership),any());
            } finally {
                failPersistence.set(false);h.service.stop(h.manifest.campaignId(),null);h.awaitFinished();
            }
            assertThat(h.savedDiagnostics.get(LiveDiagnosticStore.Kind.FIRST_FAILURE)).isEqualTo(first);
            assertThat(h.dispatched).hasSize(1);
            verify(h.lease).close();
        }
    }

    @Test
    void aFreshServiceReadsOnlyDurableFailureAndCleanupEvidenceWithoutOpeningProviderWork() throws Exception {
        try(Harness h=new Harness(false,"live-v6",1,true)) {
            h.campaignState.set("STOPPED_ERROR");
            var first=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.TRANSPORT,"PLAYWRIGHT_IPC_TIMEOUT",Instant.now());
            var cleanup=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_EXCLUSION,"RUNTIME_OR_STORAGE_FAILURE",Instant.now());
            h.savedDiagnostics.put(LiveDiagnosticStore.Kind.FIRST_FAILURE,first);
            h.savedDiagnostics.put(LiveDiagnosticStore.Kind.CLEANUP_FAILURE,cleanup);
            when(h.guard.snapshot()).thenReturn(new Guard("CLEANUP_REQUIRED",h.manifest.campaignId(),
                    new Owner(h.ownership.instanceId(),1234,Instant.now().minusSeconds(30)),h.ownership.generation(),Instant.now()));
            var status=h.service.runtimeStatus(h.manifest.campaignId()).orElseThrow();
            assertThat(status.firstFailure()).isEqualTo(first);
            assertThat(status.cleanupFailure()).isEqualTo(cleanup);
            assertThat(status.cleanupPending()).isTrue();
            assertThat(status.cleanupInProgress()).isFalse();
            verifyNoInteractions(h.factory,h.coordinator,h.supervisor,h.campaign,h.processor,h.resilience);
        }
    }

    private static UUID id(long providerId) { return CanonicalEventIdentity.sofascore(providerId).value(); }

    private static CanonicalEventObservationView observation(long providerId, EventSourceTrace source) {
        var event = mock(CanonicalEventObservationView.class);
        when(event.observationId()).thenReturn(17L);
        when(event.identity()).thenReturn(CanonicalEventIdentity.sofascore(providerId));
        when(event.source()).thenReturn(source);
        when(event.status()).thenReturn(new ScheduledEventStatus("notstarted", Optional.empty()));
        return event;
    }

    private static CanonicalEventObservationView providerObservation(long providerId, String status) {
        var event = observation(providerId,
                EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", Instant.now()));
        when(event.status()).thenReturn(new ScheduledEventStatus(status, Optional.empty()));
        return event;
    }

    private static CampaignView unlaunchedPreparation(Manifest manifest, boolean cancelled) {
        String state = cancelled ? "STOPPED_OPERATOR" : "PREPARED";
        String reason = cancelled ? "PREPARATION_CANCELLED" : null;
        List<EventView> events = manifest.targets().stream()
                .map(target -> new EventView(target, state, reason, 0, 0, null, 0, false, List.of()))
                .toList();
        return new CampaignView(manifest, state, reason, null, null, 0, 0, 1,
                null, events, List.of(), List.of());
    }

    @Test
    void uncorrelatedV9NotModifiedResponseStopsSafelyWithoutSavingOrNormalizingABody() throws Exception {
        try (Harness h = new Harness(false, "live-v9", 1, true)) {
            h.reply = request -> response("", 304);

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).singleElement().satisfies(request -> {
                assertThat(request.endpoint()).isEqualTo(EVENT_DETAILS);
                assertThat(request.ifNoneMatch()).isEmpty();
            });
            assertThat(h.receipts).isEmpty();
            verify(h.store, never()).saveReceipt(any(), any(), any());
            verifyNoInteractions(h.processor);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.outcome()).isEqualTo("FAILED");
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
                assertThat(publication.code()).isEqualTo("CONDITIONAL_RESPONSE_UNVERIFIABLE");
                assertThat(publication.successful()).isFalse();
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_CONDITIONAL_RESPONSE_UNVERIFIABLE");
        }
    }

    private static PlaywrightProviderResponse normalFinishedReply(PlaywrightProviderRequest request) {
        if (request.endpoint() != EVENT_DETAILS) return response("unavailable", 404);
        return response("""
                {"event":{"id":%d,"startTimestamp":1788796800,
                "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                "status":{"type":"finished"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                """.formatted(request.eventId()), 200);
    }

    private static PlaywrightProviderResponse response(String body, int status) {
        Instant now = Instant.now();
        return response(body, status, now);
    }

    private static PlaywrightProviderResponse response(String body, int status, Instant requestedAt) {
        return new PlaywrightProviderResponse(requestedAt, requestedAt, status, "application/json", Duration.ZERO,
                RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void cancellingPreparationIsLocalEvenWhenLiveOptInAndQualificationAreDisabled() throws Exception {
        try (Harness h = new Harness()) {
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);
            h.properties.getGroupedV7().setQualificationSha256("");
            h.service.cancelPreparation(h.manifest.campaignId(), h.manifest.manifestSha256());
            verify(h.store).cancelPreparation(eq(h.manifest.campaignId()), eq(h.manifest.manifestSha256()), any(Instant.class));
            verifyNoMoreInteractions(h.store);
            verifyNoInteractions(h.factory, h.campaign, h.admission, h.coordinator, h.guard, h.processor);
        }
    }

    private static final class Harness implements AutoCloseable {
        final LiveCampaignStore store = mock(LiveCampaignStore.class);
        final PlaywrightProviderCampaign campaign = mock(PlaywrightProviderCampaign.class);
        final PlaywrightProviderSupervisor supervisor = mock(PlaywrightProviderSupervisor.class);
        final LiveResponseProcessor processor = mock(LiveResponseProcessor.class);
        final LiveAdmissionPolicy admission = mock(LiveAdmissionPolicy.class);
        final CanonicalEventStore events = mock(CanonicalEventStore.class);
        final ManualProviderRequestCoordinator coordinator = mock(ManualProviderRequestCoordinator.class);
        final ProviderCampaignGuardStore guard = mock(ProviderCampaignGuardStore.class);
        final PlaywrightProviderCampaignFactory factory = mock(PlaywrightProviderCampaignFactory.class);
        final ManualProviderRequestCoordinator.CampaignLease lease = mock(ManualProviderRequestCoordinator.CampaignLease.class);
        final LiveDiagnosticStore diagnostics = mock(LiveDiagnosticStore.class);
        final ProviderResilienceStore resilience = mock(ProviderResilienceStore.class);
        final Map<LiveDiagnosticStore.Kind,LiveCampaignDiagnostic> savedDiagnostics = new ConcurrentHashMap<>();
        final Map<UUID,PlaywrightTransportDiagnostic> savedTransport = new ConcurrentHashMap<>();
        final Clock clock = mock(Clock.class);
        final boolean durable;
        final LiveCampaignProperties properties = new LiveCampaignProperties();
        final ProviderPlaywrightProperties playwright = new ProviderPlaywrightProperties();
        final SofascoreProperties provider = new SofascoreProperties();
        final LiveCampaignService service;
        final Manifest manifest;
        final Ownership ownership;
        final AtomicReference<String> campaignState = new AtomicReference<>("PREPARED");
        final AtomicInteger reservations = new AtomicInteger();
        final Map<UUID, String> eventStates = new ConcurrentHashMap<>();
        final Map<UUID, Boolean> finalCompleteness = new ConcurrentHashMap<>();
        final List<PlaywrightProviderRequest> dispatched = new CopyOnWriteArrayList<>();
        final List<LiveProviderDispatchGroup> groupDispatches = new CopyOnWriteArrayList<>();
        final List<AttemptRequest> attemptRequests = new CopyOnWriteArrayList<>();
        final List<UUID> dispatchAuthorizations = new CopyOnWriteArrayList<>();
        final List<RawManualCallSnapshot> receipts = new CopyOnWriteArrayList<>();
        final List<Publication> publications = new CopyOnWriteArrayList<>();
        final AtomicBoolean failFirstPublication = new AtomicBoolean();
        final AtomicBoolean failFinalLineupsPublication = new AtomicBoolean();
        final AtomicReference<Publication> failedFinalPublication = new AtomicReference<>();
        final AtomicBoolean holdBeforeDispatch = new AtomicBoolean();
        final AtomicBoolean holdAfterDispatch = new AtomicBoolean();
        final AtomicBoolean expireDuringDispatchAuthorization = new AtomicBoolean();
        final AtomicLong clockOffsetSeconds = new AtomicLong();
        final CountDownLatch waitingAtFence = new CountDownLatch(1), releaseFence = new CountDownLatch(1);
        final CountDownLatch getInFlight = new CountDownLatch(1), releaseGet = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        volatile Function<PlaywrightProviderRequest, PlaywrightProviderResponse> reply;
        volatile java.util.function.Predicate<AttemptRequest> refuseReservation = request -> false;
        volatile Runnable afterOwnerLaunch = () -> {};
        volatile java.util.function.Consumer<PlaywrightDispatchAdmission> transportProgress = dispatch -> {};
        boolean launched;

        Harness() { this(false); }
        Harness(boolean expired) {
            this(expired, "live-v1");
        }
        Harness(boolean expired, String policy) {
            this(expired,policy,2);
        }
        Harness(boolean expired, String policy, int targetCount) {
            this(expired,policy,targetCount,false);
        }
        Harness(boolean expired, String policy, int targetCount, boolean durable) {
            this(expired, policy, targetCount, durable, Duration.ofMinutes(5));
        }
        Harness(boolean expired, String policy, int targetCount, boolean durable, Duration duration) {
            this.durable=durable;
            Instant now = Instant.now().minusSeconds(expired ? 600 : 0);
            properties.setEnabled(true);
            properties.setDuration(duration);
            properties.setQualifiedMatchCapacity(2);
            properties.setRequestEnvelope(Duration.ofSeconds(3));
            properties.setQualificationSha256("a".repeat(64));
            properties.getGrouped().setQualificationSha256("c".repeat(64));
            properties.getGrouped().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            properties.getGroupedV5().setQualificationSha256("d".repeat(64));
            properties.getGroupedV5().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            properties.getGroupedV6().setQualificationSha256("e".repeat(64));
            properties.getGroupedV6().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            properties.getGroupedV7().setQualificationSha256("f".repeat(64));
            properties.getGroupedV7().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            properties.getGroupedV8().setQualificationSha256("8".repeat(64));
            properties.getGroupedV8().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            properties.getGroupedV9().setQualificationSha256("9".repeat(64));
            properties.getGroupedV9().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            properties.getGroupedV10().setQualificationSha256("0".repeat(64));
            properties.getGroupedV10().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            boolean v7 = "live-v7".equals(policy);
            boolean v5 = "live-v5".equals(policy);
            boolean v6 = "live-v6".equals(policy);
            boolean v8 = "live-v8".equals(policy);
            boolean v9 = "live-v9".equals(policy);
            boolean v10 = "live-v10".equals(policy);
            properties.getGroupedV11().setQualificationSha256("1".repeat(64));
            properties.getGroupedV11().getEndpoints().values().forEach(budget->{
                budget.setRequestEnvelope(Duration.ofMillis(500));budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
            boolean v11 = "live-v11".equals(policy);
            manifest = new Manifest(UUID.randomUUID(), "a".repeat(64), policy, now, now.plusSeconds(300),
                    duration, v5 || v6 || v7 || v8 || v9 || v10 || v11 ? 2500 : 1000, v5 || v6 || v7 || v8 || v9 || v10 || v11 ? 20000 : 3000, 20_000_000, 2,
                    targetCount==1?List.of(new Target(id(A),A,1,1)):List.of(new Target(id(A), A, 1, 1), new Target(id(B), B, 2, 2)),
                    new AdmissionProfile(properties.getRequestEnvelope(), properties.getProcessingEnvelope(),
                            properties.getQualificationSha256(), v11 ? properties.groupedAdmissionProfileV11() : v10 ? properties.groupedAdmissionProfileV10() : v9 ? properties.groupedAdmissionProfileV9() : v8 ? properties.groupedAdmissionProfileV8() : v7 ? properties.groupedAdmissionProfileV7() : v6 ? properties.groupedAdmissionProfileV6()
                                    : v5 ? properties.groupedAdmissionProfileV5()
                                    : "live-v4".equals(policy)?properties.groupedAdmissionProfile():null), Duration.ofSeconds(v5 || v6 ? 100 : 60));
            ownership = new Ownership(manifest.campaignId(), UUID.randomUUID(), 1);
            eventStates.put(id(A), "WAITING_START"); eventStates.put(id(B), "WAITING_START");
            provider.setEnabled(true);
            observe(A, "notstarted");
            observe(B, "notstarted");
            playwright.setEnabled(true);
            when(coordinator.acquireLiveCampaign(manifest.campaignId())).thenReturn(lease);
            when(lease.ownership()).thenReturn(ownership);
            doAnswer(invocation -> { finished.countDown(); return null; }).when(lease).close();
            when(guard.isOwned(ownership)).thenReturn(true);
            when(factory.open(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGrouped(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV5(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV6(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV7(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV8(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV9(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV10(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(factory.openLiveGroupedV11(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(supervisor.activeCampaignId()).thenReturn(Optional.empty());
            when(store.find(manifest.campaignId())).thenAnswer(invocation -> Optional.of(view()));
            when(store.dispatchBudget(eq(ownership),any())).thenAnswer(invocation -> {
                CampaignView current=view(); UUID eventId=invocation.getArgument(1);
                EventView event=current.events().stream().filter(value -> value.target().canonicalEventId().equals(eventId)).findFirst().orElseThrow();
                return new DispatchBudget(current.reservedCalls(),current.receivedBytes(),event.reservedCalls(),event.state());
            });
            when(store.launch(eq(manifest.campaignId()), eq(manifest.manifestSha256()), eq(ownership), any()))
                    .thenAnswer(invocation -> {
                        campaignState.set("RUNNING"); Instant start = invocation.getArgument(3);
                        afterOwnerLaunch.run();
                        return new Launch(ownership, start, start.plus(manifest.duration()), true);
                    });
            when(store.reserveAttempt(any())).thenAnswer(invocation -> {
                AttemptRequest request = invocation.getArgument(0);
                if (refuseReservation.test(request)) return Optional.empty();
                reservations.incrementAndGet();attemptRequests.add(request);
                long providerId = request.canonicalEventId().equals(id(A)) ? A : B;
                return Optional.of(new ReservedAttempt(request.attemptId(), request.canonicalEventId(), providerId,
                        request.endpoint(), request.cycleNumber(), request.kind(), request.dueAt(), request.reservedAt(), request.finalCycle(),
                        request.groupId(),request.groupSequence(),request.groupOrdinal()));
            });
            doAnswer(invocation -> {
                dispatchAuthorizations.add(invocation.getArgument(1));
                if (expireDuringDispatchAuthorization.compareAndSet(true, false))
                    clockOffsetSeconds.set(manifest.duration().toSeconds() + 1);
                return null;
            })
                    .when(store).recordDispatch(any(), any(), any());
            when(store.saveReceipt(any(), any(), any())).thenAnswer(invocation -> {
                RawManualCallSnapshot raw = invocation.getArgument(2); receipts.add(raw);
                return new RawSnapshotPersistenceResult(receipts.size(), RawSnapshotPersistenceOutcome.INSERTED,
                        raw.payload().sha256(), raw.payload().sizeBytes(), OptionalLong.of(receipts.size()));
            });
            when(store.publishResult(any(), any(), any(), any())).thenAnswer(invocation -> {
                Publication publication = invocation.getArgument(2);
                if (failFirstPublication.compareAndSet(true, false)) throw new IllegalStateException("publication transaction failed");
                if (publication.successful() && "FINISHED_CONFIRMED".equals(publication.nextEventState())
                        && dispatched.getLast().endpoint() == EVENT_LINEUPS
                        && failFinalLineupsPublication.compareAndSet(true, false)) {
                    failedFinalPublication.set(publication);
                    throw new IllegalStateException("final publication transaction failed");
                }
                Supplier<NormalizedReferences> callback = invocation.getArgument(3);
                NormalizedReferences normalized = callback.get(); publications.add(publication);
                return new Result(invocation.getArgument(1), publication, normalized);
            });
            doAnswer(invocation -> {
                UUID eventId = invocation.getArgument(1); String state = invocation.getArgument(2);
                if (eventId == null) campaignState.set(state); else eventStates.put(eventId, state);
                return null;
            }).when(store).transition(any(), any(), any(), any(), any(), any());
            doAnswer(invocation -> {
                finalCompleteness.put(invocation.getArgument(1), invocation.getArgument(4));
                return null;
            }).when(store).updateScheduleMetrics(any(), any(), any(), anyLong(), anyBoolean(), any());
            LiveResponseProcessor pure = new LiveResponseProcessor(events, mock(EventDetailsStore.class),
                    mock(J5EventDataStore.class), mock(RawManualCallSnapshotStore.class));
            when(processor.process(any(), any(), any(), any())).thenAnswer(invocation -> pure.process(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
            when(processor.persistProcessed(any())).thenReturn(NormalizedReferences.none());
            when(campaign.execute(any(), any())).thenAnswer(invocation -> executeInMemory(invocation.getArgument(0),invocation.getArgument(1)));
            when(campaign.executeGrouped(any(),any(),any())).thenAnswer(invocation->{
                groupDispatches.add(invocation.getArgument(1));return executeInMemory(invocation.getArgument(0),invocation.getArgument(2));
            });
            when(clock.instant()).thenAnswer(invocation -> Instant.now().plusSeconds(clockOffsetSeconds.get()));
            var open=new ProviderResilienceData.Snapshot(ProviderResilienceData.State.OPEN,0,now,null,null,null,null,null,null,null,null);
            when(resilience.snapshot()).thenReturn(open);
            when(resilience.departureDecision(any())).thenAnswer(invocation->new ProviderResilienceData.DepartureDecision(
                    true,ProviderResilienceData.DepartureReason.ALLOWED,invocation.getArgument(0),open));
            when(resilience.departureDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V8), any())).thenAnswer(invocation->
                    new ProviderResilienceData.DepartureDecision(true,ProviderResilienceData.DepartureReason.ALLOWED,
                            invocation.getArgument(1),open));
            when(resilience.departureCapacityDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V8),
                    eq(manifest.targets().size()*4), any())).thenAnswer(invocation ->
                    new ProviderResilienceData.DepartureDecision(true,ProviderResilienceData.DepartureReason.ALLOWED,
                            invocation.getArgument(2),open));
            when(resilience.departureDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V10), any())).thenAnswer(invocation->
                    new ProviderResilienceData.DepartureDecision(true,ProviderResilienceData.DepartureReason.ALLOWED,
                            invocation.getArgument(1),open));
            when(resilience.departureCapacityDecision(eq(ProviderResilienceData.DepartureProfile.LIVE_V10),
                    eq(manifest.targets().size()*4), any())).thenAnswer(invocation ->
                    new ProviderResilienceData.DepartureDecision(true,ProviderResilienceData.DepartureReason.ALLOWED,
                            invocation.getArgument(2),open));
            doAnswer(invocation->{
                UUID attempt=invocation.getArgument(1);PlaywrightTransportDiagnostic diagnostic=invocation.getArgument(3);
                savedTransport.put(attempt,diagnostic);return null;
            }).when(diagnostics).recordTransport(any(),any(),any(),any());
            doAnswer(invocation->{
                LiveDiagnosticStore.Kind kind=invocation.getArgument(1);LiveCampaignDiagnostic diagnostic=invocation.getArgument(2);
                if(kind==LiveDiagnosticStore.Kind.FIRST_FAILURE) savedDiagnostics.putIfAbsent(kind,diagnostic);
                else savedDiagnostics.put(kind,diagnostic);
                return null;
            }).when(diagnostics).recordFailure(any(),any(),any());
            when(diagnostics.find(any(),any())).thenAnswer(invocation->Optional.ofNullable(savedDiagnostics.get(invocation.getArgument(1))));
            service = newService();
        }
        private PlaywrightProviderResponse executeInMemory(PlaywrightProviderRequest request,PlaywrightDispatchAdmission dispatch) throws InterruptedException {
                if (holdBeforeDispatch.compareAndSet(true, false)) {
                    waitingAtFence.countDown();
                    if (!releaseFence.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test fence timed out");
                }
                dispatch.check();
                try (var permit = dispatch.acquireDispatchPermit()) { dispatched.add(request); }
                transportProgress.accept(dispatch);
                if (holdAfterDispatch.compareAndSet(true, false)) {
                    getInFlight.countDown();
                    if (!releaseGet.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test get timed out");
                }
                return reply.apply(request);
        }

        void observe(long providerId, String status) {
            var event = providerObservation(providerId, status);
            when(events.findLatestByCanonicalId(id(providerId))).thenReturn(Optional.of(event));
        }
        LiveCampaignService newService() {
            return new LiveCampaignService(provider,playwright,properties,admission,store,events,coordinator,guard,factory,
                    supervisor,processor,clock,new LiveOrphanProcessProbe(),durable?diagnostics:null,durable?resilience:null);
        }

        CampaignView view() {
            String state = campaignState.get();
            boolean unlaunched = "PREPARED".equals(state);
            List<EventView> events = manifest.targets().stream().map(target -> new EventView(target,
                    unlaunched ? "PREPARED" : eventStates.get(target.canonicalEventId()), null, 0, 0, null, List.of())).toList();
            return new CampaignView(manifest, state, null, unlaunched ? null : manifest.preparedAt(),
                    unlaunched ? null : manifest.preparedAt().plus(manifest.duration()), reservations.get(), 0, publications.size(),
                    unlaunched ? null : ownership, events, List.of(), List.of());
        }

        void launch() { service.launch(manifest.campaignId(), manifest.manifestSha256()); launched = true; }
        void awaitFinished() throws InterruptedException { assertThat(finished.await(4, TimeUnit.SECONDS)).isTrue(); }
        LiveSessionSchedule activeSchedule() {
            AtomicReference<?> active = (AtomicReference<?>) ReflectionTestUtils.getField(service, "active");
            Object session = active.get();
            return session == null ? null : (LiveSessionSchedule) ReflectionTestUtils.getField(session, "schedule");
        }
        void awaitCleanupPending() throws InterruptedException {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            while(System.nanoTime()<until) {
                var runtime=service.runtimeStatus(manifest.campaignId());
                if(runtime.isPresent() && runtime.orElseThrow().cleanupPending() && !runtime.orElseThrow().cleanupInProgress()) return;
                Thread.sleep(10);
            }
            assertThat(service.runtimeStatus(manifest.campaignId()).orElseThrow().cleanupPending()).isTrue();
        }
        @Override public void close() throws InterruptedException {
            releaseFence.countDown(); releaseGet.countDown(); service.shutdown();
            if (launched) assertThat(finished.await(4, TimeUnit.SECONDS)).isTrue();
        }
    }
}
