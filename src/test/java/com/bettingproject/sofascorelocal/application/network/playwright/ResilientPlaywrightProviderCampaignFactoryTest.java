package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResilientPlaywrightProviderCampaignFactoryTest {
    private static final Instant START = Instant.parse("2026-09-09T12:00:00Z");
    private static final Set<SofascoreEndpointType> ALL = Set.of(SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_LINEUPS);

    @ParameterizedTest @CsvSource({"J3,403", "J4,403", "J5,429"})
    void persistsRefusalBeforeApplicationSeesHeadersOrBodyAndNeverRetries(String journey, int status) {
        Harness h = new Harness();
        var headers = headers(status);
        h.delegate.behavior = admission -> {
            admission.onTransportProgress(headers);
            h.trace.add("body");
            throw new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT,
                    headers.at(PlaywrightTransportDiagnostic.Phase.READING_BODY));
        };
        UUID campaignId = UUID.randomUUID();
        var request = switch (journey) {
            case "J3" -> PlaywrightProviderRequest.scheduledEvents(LocalDate.of(2026,9,9),1);
            case "J4" -> PlaywrightProviderRequest.eventDetails(123L);
            default -> PlaywrightProviderRequest.eventStatistics(123L);
        };
        var campaign = h.factory.open(campaignId, Set.of(request.endpoint()));
        assertThatThrownBy(() -> campaign.execute(request, new PlaywrightDispatchAdmission() {
            public void check() { }
            public Permit acquireDispatchPermit() { return () -> { }; }
            public void onTransportProgress(PlaywrightTransportDiagnostic d) {
                assertThat(h.state.get().state()).isEqualTo(State.SUSPENDED);
                assertThat(h.trace).doesNotContain("body");
                h.trace.add("application-headers");
            }
        })).isInstanceOf(PlaywrightProviderException.class);
        assertThat(h.trace).containsSubsequence("reserve","get","suspend","application-headers","body");
        assertThat(h.delegate.emissions).isEqualTo(1);
        verify(h.store, times(1)).suspend(any(),eq(campaignId),eq(status),eq(headers.headersReceivedAt()),eq(headers.retryAfterNotBefore()));
        verify(h.store, never()).markDepartureFinished(any(),any());
        campaign.close();
        assertThat(h.trace).containsSubsequence("close","finish");
        verify(h.store, times(1)).markDepartureFinished(any(),any());
        assertThatThrownBy(() -> h.factory.open(UUID.randomUUID(),ALL)).hasMessage("PROVIDER_SUSPENDED");
        assertThat(h.delegate.opens).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"historical","v4","v5","manual-j5"})
    void everyOpeningIsBlockedBeforeDelegateWhenTheProviderIsSuspended(String opening) {
        Harness h = new Harness();
        h.state.set(new Snapshot(State.SUSPENDED,1,START,403,START,null,null,UUID.randomUUID(),UUID.randomUUID(),null,null));
        assertThatThrownBy(() -> switchOpen(h.factory,opening)).hasMessage("PROVIDER_SUSPENDED");
        assertThat(h.delegate.opens).isZero();
        verify(h.store,never()).tryReserveDeparture(any(),any());
    }

    @Test void reservationPrecedesEmissionAndCompletionRecordsActualReturnTime() {
        Harness h = new Harness();
        h.delegate.behavior = admission -> {
            h.clock.advance(Duration.ofSeconds(10)); h.trace.add("response"); return response();
        };
        try(var campaign=h.factory.open(UUID.randomUUID(),ALL)) { campaign.execute(PlaywrightProviderRequest.eventDetails(123)); }
        assertThat(h.trace).containsSubsequence("reserve","get","response","finish","close");
        verify(h.store).markDepartureFinished(any(),eq(START.plusSeconds(10)));
        assertThat(h.state.get().lastDepartureFinishedAt()).isEqualTo(START.plusSeconds(10));
        assertThat(h.state.get().unresolvedDispatchId()).isNull();
    }

    @Test void aNewCampaignAndFactoryStillUseTheSharedBudgetAndPostResponseFence() {
        Harness h = new Harness();
        h.delegate.behavior = admission -> { h.clock.advance(Duration.ofSeconds(10)); return response(); };
        try(var first=h.factory.open(UUID.randomUUID(),ALL)) { first.execute(PlaywrightProviderRequest.eventDetails(123)); }
        Instant nextEligible=h.state.get().lastDepartureFinishedAt().plusSeconds(2);
        AtomicInteger budgetChecks=new AtomicInteger();
        h.reservation=(id,at)-> {
            budgetChecks.incrementAndGet();
            if(at.isBefore(nextEligible)) return new DepartureDecision(false,DepartureReason.RATE_LIMITED,nextEligible,h.state.get());
            return h.allow(id,at);
        };
        var restartedFactory=h.newFactory();
        try(var second=restartedFactory.openLiveGroupedV5(UUID.randomUUID(),ALL)) {
            second.execute(PlaywrightProviderRequest.eventDetails(123));
        }
        assertThat(budgetChecks).hasValue(2);
        assertThat(h.paused).isEqualTo(Duration.ofSeconds(2));
        verify(h.store).tryReserveDeparture(any(),eq(nextEligible));
        assertThat(h.delegate.emissions).isEqualTo(2);
        // A fresh object and a fresh campaign did not replace or reset the store.
        verify(h.store,never()).rearm(anyLong(),any());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancellationNeverEmitsAndAnAlreadyReservedAttemptIsNotRefunded(boolean afterReservation) {
        Harness h=new Harness();
        var campaign=h.factory.open(UUID.randomUUID(),ALL);
        assertThatThrownBy(()->campaign.execute(PlaywrightProviderRequest.eventDetails(123),new PlaywrightDispatchAdmission(){
            public void check(){if(!afterReservation)throw new PlaywrightDispatchCancelledException();}
            public Permit acquireDispatchPermit(){throw new PlaywrightDispatchCancelledException();}
        })).isInstanceOf(PlaywrightDispatchCancelledException.class);
        assertThat(h.delegate.emissions).isZero();
        verify(h.store,times(afterReservation?1:0)).tryReserveDeparture(any(),any());
        verify(h.store,never()).markDepartureFinished(any(),any());
        campaign.close();
        verify(h.store,times(afterReservation?1:0)).markDepartureFinished(any(),any());
        assertThat(h.trace).doesNotContain("get");
    }

    @Test void failedSuspensionPublicationBlocksNewOpenAndCloseRetriesOnlyLocalPersistence() {
        Harness h=new Harness();
        AtomicInteger publications=new AtomicInteger();
        doAnswer(invocation->{
            h.trace.add("suspend");
            if(publications.incrementAndGet()==1)throw new IllegalStateException("SQL unavailable");
            return h.suspend(invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2),invocation.getArgument(3),invocation.getArgument(4));
        }).when(h.store).suspend(any(),any(),anyInt(),any(),any());
        h.delegate.behavior=admission->{admission.onTransportProgress(headers(403));throw new AssertionError("body must not be read");};
        var campaign=h.factory.open(UUID.randomUUID(),ALL);
        AtomicReference<PlaywrightTransportDiagnostic> forwarded=new AtomicReference<>();
        assertThatThrownBy(()->campaign.execute(PlaywrightProviderRequest.eventDetails(123),new PlaywrightDispatchAdmission(){
            public void check(){ }
            public Permit acquireDispatchPermit(){return ()->{ };}
            public void onTransportProgress(PlaywrightTransportDiagnostic diagnostic){forwarded.set(diagnostic);}
        })).hasMessage("SQL unavailable");
        assertThat(forwarded.get()).isEqualTo(headers(403));
        assertThatThrownBy(()->h.factory.open(UUID.randomUUID(),ALL)).hasMessage("PROVIDER_SUSPENDED");
        assertThat(h.delegate.opens).isEqualTo(1);
        campaign.close();
        assertThat(publications).hasValue(2);
        assertThat(h.delegate.emissions).isEqualTo(1);
        assertThat(h.delegate.closes).isEqualTo(1);
        assertThat(h.state.get().state()).isEqualTo(State.SUSPENDED);
        assertThat(h.trace.subList(h.trace.size()-3,h.trace.size())).containsExactly("close","suspend","finish");
    }

    @Test void anUnpublishedRefusalCannotLoseItsDurableExclusionDuringCleanupOrRestart() {
        Harness h=new Harness();
        doThrow(new IllegalStateException("SQL unavailable")).when(h.store).suspend(any(),any(),anyInt(),any(),any());
        h.delegate.behavior=admission->{admission.onTransportProgress(headers(403));throw new AssertionError("body must not be read");};
        var campaign=h.factory.open(UUID.randomUUID(),ALL);
        assertThatThrownBy(()->campaign.execute(PlaywrightProviderRequest.eventDetails(123))).hasMessage("SQL unavailable");
        UUID unresolved=h.state.get().unresolvedDispatchId();
        assertThat(unresolved).isNotNull();
        assertThatThrownBy(campaign::close).hasMessage("SQL unavailable");
        assertThat(h.delegate.closes).isEqualTo(1);
        verify(h.store,never()).markDepartureFinished(any(),any());
        assertThat(h.state.get().state()).isEqualTo(State.OPEN);
        assertThat(h.state.get().unresolvedDispatchId()).isEqualTo(unresolved);
        assertThatThrownBy(()->h.newFactory().open(UUID.randomUUID(),ALL)).hasMessage("PROVIDER_DEPARTURE_UNRESOLVED");
        assertThat(h.delegate.opens).isEqualTo(1);
        assertThat(h.delegate.emissions).isEqualTo(1);

        doAnswer(invocation->{
            h.trace.add("suspend");
            return h.suspend(invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2),invocation.getArgument(3),invocation.getArgument(4));
        }).when(h.store).suspend(any(),any(),anyInt(),any(),any());
        campaign.close();
        assertThat(h.trace.subList(h.trace.size()-3,h.trace.size())).containsExactly("close","suspend","finish");
        assertThat(h.state.get().state()).isEqualTo(State.SUSPENDED);
        assertThat(h.state.get().unresolvedDispatchId()).isNull();
        verify(h.store,times(1)).markDepartureFinished(eq(unresolved),any());
        assertThatThrownBy(()->h.newFactory().open(UUID.randomUUID(),ALL)).hasMessage("PROVIDER_SUSPENDED");
        assertThat(h.delegate.emissions).isEqualTo(1);
    }

    @Test void timeoutReservationFinishesOnlyAfterSuccessfulTransportCleanup() {
        Harness h=new Harness();
        h.delegate.behavior=admission->{throw new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT);};
        var campaign=h.factory.open(UUID.randomUUID(),ALL);
        assertThatThrownBy(()->campaign.execute(PlaywrightProviderRequest.eventDetails(123))).isInstanceOf(PlaywrightProviderException.class);
        verify(h.store,never()).markDepartureFinished(any(),any());
        h.delegate.closeFailure=new IllegalStateException("cleanup unverified");
        assertThatThrownBy(campaign::close).hasMessage("cleanup unverified");
        verify(h.store,never()).markDepartureFinished(any(),any());
        assertThatThrownBy(()->h.factory.open(UUID.randomUUID(),ALL)).hasMessage("PROVIDER_DEPARTURE_UNRESOLVED");
        h.delegate.closeFailure=null;
        campaign.close();
        verify(h.store,times(1)).markDepartureFinished(any(),any());
        assertThat(h.delegate.emissions).isEqualTo(1);
    }

    @Test void anUnacknowledgedFinishRemainsBlockingAfterTheTransportHasClosed() {
        Harness h=new Harness();
        when(h.store.markDepartureFinished(any(),any())).thenThrow(new IllegalStateException("finish acknowledgement lost"));
        var campaign=h.factory.open(UUID.randomUUID(),ALL);
        assertThatThrownBy(()->campaign.execute(PlaywrightProviderRequest.eventDetails(123))).hasMessage("finish acknowledgement lost");
        assertThatThrownBy(campaign::close).hasMessage("finish acknowledgement lost");
        assertThatThrownBy(()->h.factory.open(UUID.randomUUID(),ALL)).hasMessage("PROVIDER_DEPARTURE_UNRESOLVED");
        assertThat(h.delegate.opens).isEqualTo(1);
        assertThat(h.delegate.closes).isEqualTo(1);
        assertThat(h.delegate.emissions).isEqualTo(1);
    }

    private static PlaywrightProviderCampaign switchOpen(PlaywrightProviderCampaignFactory f,String kind) {
        UUID id=UUID.randomUUID();
        return switch(kind){case "v4"->f.openLiveGrouped(id,ALL);case "v5"->f.openLiveGroupedV5(id,ALL);
            case "manual-j5"->f.openManualJ5Grouped(id,Set.of(SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_INCIDENTS,SofascoreEndpointType.EVENT_LINEUPS));
            default->f.open(id,ALL);};
    }
    private static PlaywrightTransportDiagnostic headers(int status) {
        return new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,30_000,
                START,START.plusMillis(50),status,START.plusSeconds(60),false);
    }
    private static PlaywrightProviderResponse response() {
        return new PlaywrightProviderResponse(START,START.plusMillis(100),200,"application/json",Duration.ofMillis(100),
                RawPayloadEvidence.capture("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));
    }
    private static final class Harness {
        final List<String> trace=new ArrayList<>();
        final MutableClock clock=new MutableClock();
        Duration paused=Duration.ZERO;
        final ProviderResilienceStore store=mock(ProviderResilienceStore.class);
        final AtomicReference<Snapshot> state=new AtomicReference<>(new Snapshot(State.OPEN,0,START,null,null,null,null,null,null,null,null));
        final FakeFactory delegate=new FakeFactory(trace);
        BiFunction<UUID,Instant,DepartureDecision> reservation=this::allow;
        final ResilientPlaywrightProviderCampaignFactory factory;
        Harness(){
            when(store.snapshot()).thenAnswer(invocation->state.get());
            when(store.tryReserveDeparture(any(),any())).thenAnswer(invocation->{trace.add("reserve");return reservation.apply(invocation.getArgument(0),invocation.getArgument(1));});
            when(store.markDepartureFinished(any(),any())).thenAnswer(invocation->{
                trace.add("finish");Snapshot s=state.get();
                state.set(new Snapshot(s.state(),s.version(),s.changedAt(),s.httpStatus(),s.suspendedAt(),s.retryNotBefore(),s.lastDepartureAt(),s.evidenceId(),s.campaignId(),invocation.getArgument(1),null));return state.get();});
            when(store.suspend(any(),any(),anyInt(),any(),any())).thenAnswer(invocation->{trace.add("suspend");return suspend(invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2),invocation.getArgument(3),invocation.getArgument(4));});
            factory=newFactory();
        }
        ResilientPlaywrightProviderCampaignFactory newFactory(){return new ResilientPlaywrightProviderCampaignFactory(delegate,store,clock,d->{paused=paused.plus(d);clock.advance(d);});}
        DepartureDecision allow(UUID id,Instant at){
            Snapshot s=state.get();state.set(new Snapshot(s.state(),s.version(),s.changedAt(),s.httpStatus(),s.suspendedAt(),s.retryNotBefore(),at,s.evidenceId(),s.campaignId(),s.lastDepartureFinishedAt(),id));
            return new DepartureDecision(true,DepartureReason.ALLOWED,at,state.get());
        }
        Snapshot suspend(UUID evidence,UUID campaign,int status,Instant at,Instant retry){
            Snapshot s=state.get();state.set(new Snapshot(State.SUSPENDED,s.version()+1,at,status,at,retry,s.lastDepartureAt(),evidence,campaign,s.lastDepartureFinishedAt(),s.unresolvedDispatchId()));return state.get();
        }
    }
    private static final class MutableClock extends Clock {
        Instant now=START;
        void advance(Duration d){now=now.plus(d);}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    private static final class FakeFactory implements PlaywrightProviderCampaignFactory {
        final List<String> trace;int opens,emissions,closes;RuntimeException closeFailure;
        Function<PlaywrightDispatchAdmission,PlaywrightProviderResponse> behavior=admission->response();
        FakeFactory(List<String> trace){this.trace=trace;}
        public PlaywrightProviderCampaign open(UUID id,Set<SofascoreEndpointType> endpoints){
            opens++;
            return new PlaywrightProviderCampaign(){
                public PlaywrightProviderResponse execute(PlaywrightProviderRequest request){throw new AssertionError("unguarded dispatch");}
                public PlaywrightProviderResponse execute(PlaywrightProviderRequest request,PlaywrightDispatchAdmission admission){
                    admission.check();
                    try(var permit=admission.acquireDispatchPermit()){emissions++;trace.add("get");}
                    return behavior.apply(admission);
                }
                public PlaywrightProviderResponse executeGrouped(PlaywrightProviderRequest request,LiveProviderDispatchGroup group,PlaywrightDispatchAdmission admission){return execute(request,admission);}
                public void close(){closes++;trace.add("close");if(closeFailure!=null)throw closeFailure;}
            };
        }
        public PlaywrightProviderCampaign openLiveGrouped(UUID id,Set<SofascoreEndpointType> e){return open(id,e);}
        public PlaywrightProviderCampaign openLiveGroupedV5(UUID id,Set<SofascoreEndpointType> e){return open(id,e);}
        public PlaywrightProviderCampaign openManualJ5Grouped(UUID id,Set<SofascoreEndpointType> e){return open(id,e);}
    }
}
