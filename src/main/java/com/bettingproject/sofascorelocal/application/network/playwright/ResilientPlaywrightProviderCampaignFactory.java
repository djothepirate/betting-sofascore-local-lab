package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureReason;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.DepartureProfile;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Shared J3/J4/J5 refusal and departure admission. Never opens or retries a session itself. */
@Primary @Component
public final class ResilientPlaywrightProviderCampaignFactory implements PlaywrightProviderCampaignFactory {
    private final PlaywrightProviderCampaignFactory delegate;
    private final ProviderResilienceStore store;
    private final Clock clock;
    private final Pause pause;
    // Keep a refusal blocking even if its SQL acknowledgement fails. Closing retries only local persistence.
    private final AtomicReference<Refusal> pendingRefusal = new AtomicReference<>();
    private record Refusal(UUID evidenceId, UUID campaignId, int status, Instant at, Instant retryAt) { }
    @FunctionalInterface interface Pause { void sleep(Duration duration) throws InterruptedException; }

    @Autowired
    public ResilientPlaywrightProviderCampaignFactory(ChildJvmPlaywrightProviderSupervisor delegate,
                                                      ProviderResilienceStore store) {
        this(delegate,store,Clock.systemUTC(),d -> Thread.sleep(d));
    }

    ResilientPlaywrightProviderCampaignFactory(PlaywrightProviderCampaignFactory delegate,
                                              ProviderResilienceStore store, Clock clock, Pause pause) {
        this.delegate=delegate; this.store=store; this.clock=clock; this.pause=pause;
    }

    @Override public PlaywrightProviderCampaign open(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.open(id,endpoints));
    }
    @Override public PlaywrightProviderCampaign openLiveGrouped(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.openLiveGrouped(id,endpoints));
    }
    @Override public PlaywrightProviderCampaign openLiveGroupedV5(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.openLiveGroupedV5(id,endpoints));
    }
    @Override public PlaywrightProviderCampaign openLiveGroupedV6(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.openLiveGroupedV6(id,endpoints),true,DepartureProfile.LEGACY_V1);
    }
    @Override public PlaywrightProviderCampaign openLiveGroupedV7(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.openLiveGroupedV7(id,endpoints),true,DepartureProfile.LEGACY_V1);
    }
    @Override public PlaywrightProviderCampaign openLiveGroupedV8(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.openLiveGroupedV8(id,endpoints),true,DepartureProfile.LIVE_V8);
    }
    @Override public PlaywrightProviderCampaign openManualJ5Grouped(UUID id, Set<SofascoreEndpointType> endpoints) {
        return openProtected(id,()->delegate.openManualJ5Grouped(id,endpoints));
    }

    private void requireOpen() {
        if(pendingRefusal.get()!=null || store.snapshot().state()==State.SUSPENDED)
            throw new IllegalStateException("PROVIDER_SUSPENDED");
    }

    private PlaywrightProviderCampaign openProtected(UUID campaignId, Supplier<PlaywrightProviderCampaign> opening) {
        return openProtected(campaignId, opening, false, DepartureProfile.LEGACY_V1);
    }

    private PlaywrightProviderCampaign openProtected(UUID campaignId, Supplier<PlaywrightProviderCampaign> opening,
                                                     boolean recoverTimeouts) {
        return openProtected(campaignId, opening, recoverTimeouts, DepartureProfile.LEGACY_V1);
    }

    private PlaywrightProviderCampaign openProtected(UUID campaignId, Supplier<PlaywrightProviderCampaign> opening,
                                                     boolean recoverTimeouts, DepartureProfile departureProfile) {
        requireOpen();
        if(store.snapshot().unresolvedDispatchId()!=null) throw new IllegalStateException("PROVIDER_DEPARTURE_UNRESOLVED");
        PlaywrightProviderCampaign campaign=opening.get();
        return new PlaywrightProviderCampaign() {
            private UUID unfinished;
            @Override public PlaywrightProviderResponse execute(PlaywrightProviderRequest request) {
                return execute(request,PlaywrightDispatchAdmission.UNRESTRICTED);
            }
            @Override public PlaywrightProviderResponse execute(PlaywrightProviderRequest request,
                                                                PlaywrightDispatchAdmission admission) {
                return dispatch(request,null,admission);
            }
            @Override public PlaywrightProviderResponse executeGrouped(PlaywrightProviderRequest request,
                    LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
                return dispatch(request,group,admission);
            }
            private PlaywrightProviderResponse dispatch(PlaywrightProviderRequest request,
                    LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
                requireOpen(); UUID dispatchId=UUID.randomUUID();
                AtomicReference<Refusal> refusal=new AtomicReference<>();
                var protectedAdmission=new PlaywrightDispatchAdmission() {
                    @Override public void check() { admission.check(); requireOpen(); }
                    @Override public Permit acquireDispatchPermit() {
                        // The underlying supervisor holds single-flight. No SQL lock survives this method.
                        awaitReservation(dispatchId,admission,departureProfile);
                        unfinished=dispatchId;
                        // Reservation remains charged on cancellation/failure: no duplicate emission is inferred.
                        return admission.acquireDispatchPermit();
                    }
                    @Override public void onTransportProgress(PlaywrightTransportDiagnostic diagnostic) {
                        RuntimeException refusalFailure = null;
                        try {
                            // A known refusal remains first: an incoherent worker timestamp
                            // can stop V8, but must never discard a 403/429 suspension.
                            observeRefusal(diagnostic,dispatchId,campaignId,refusal);
                            recordAuthenticatedV8Departure(dispatchId,departureProfile,
                                    diagnostic == null ? null : diagnostic.requestedAt(),clock.instant());
                        }
                        catch (RuntimeException failure) { refusalFailure = failure; }
                        // Even when the refusal transaction fails, retain the known headers in the
                        // campaign diagnosis. Neither failure authorizes continued transport.
                        try { admission.onTransportProgress(diagnostic); }
                        catch (RuntimeException diagnosticFailure) {
                            if (refusalFailure == null) throw diagnosticFailure;
                        }
                        if (refusalFailure != null) throw refusalFailure;
                    }
                };
                try {
                    var response=group==null ? campaign.execute(request,protectedAdmission)
                            : campaign.executeGrouped(request,group,protectedAdmission);
                    observeRefusal(response.diagnostic(),dispatchId,campaignId,refusal);
                    if((response.httpStatus()==403 || response.httpStatus()==429) && refusal.get()==null)
                        persistRefusal(new Refusal(dispatchId,campaignId,response.httpStatus(),response.receivedAt(),null));
                    // A completed response is an idempotent fallback for transports that
                    // cannot stream REQUEST_SENT progress.  Its constructor already proves
                    // requestedAt <= receivedAt; the store also checks the active reservation.
                    recordAuthenticatedV8Departure(dispatchId,departureProfile,response.requestedAt(),response.receivedAt());
                    finishDeparture();
                    return response;
                } catch(PlaywrightProviderException failure) {
                    observeRefusal(failure.diagnostic(),dispatchId,campaignId,refusal);
                    recordAuthenticatedV8Departure(dispatchId,departureProfile,
                            failure.diagnostic()==null?null:failure.diagnostic().requestedAt(),clock.instant());
                    // The terminal acknowledgement includes verified page/context cleanup.
                    // Persist the charge's end before the caller may defer or emit again.
                    if (recoverTimeouts && failure.recoverableTimeout()) {
                        admission.onTransportProgress(failure.diagnostic());
                        finishDeparture();
                    }
                    throw failure;
                }
            }
            @Override public void close() {
                // Close processes first. A refusal must be durable before the pressure reservation
                // is released, otherwise restarting the JVM could discard the only blocking proof.
                campaign.close();
                flushPendingRefusal();
                finishDeparture();
            }
            private void finishDeparture() {
                if(unfinished!=null) { store.markDepartureFinished(unfinished,clock.instant()); unfinished=null; }
            }
        };
    }

    private void awaitReservation(UUID dispatchId, PlaywrightDispatchAdmission admission, DepartureProfile departureProfile) {
        while(true) {
            admission.check(); requireOpen();
            // Preserve the legacy method path for pre-V8 callers. This keeps their durable
            // contract unchanged while V8 explicitly selects its faster local envelope.
            var decision=departureProfile==DepartureProfile.LEGACY_V1
                    ? store.tryReserveDeparture(dispatchId,clock.instant())
                    : store.tryReserveDeparture(dispatchId,departureProfile,clock.instant());
            if(decision.allowed()) return;
            if(decision.reason()!=DepartureReason.RATE_LIMITED && decision.reason()!=DepartureReason.POST_EXCHANGE_FENCE)
                throw new IllegalStateException(decision.reason()==DepartureReason.PROVIDER_SUSPENDED
                        ? "PROVIDER_SUSPENDED" : decision.reason()==DepartureReason.DEPARTURE_UNRESOLVED
                        ? "PROVIDER_DEPARTURE_UNRESOLVED" : "PROVIDER_CLOCK_REGRESSION");
            Instant until=decision.nextAllowedAt();
            while(clock.instant().isBefore(until)) {
                admission.check();
                if(pendingRefusal.get()!=null) throw new IllegalStateException("PROVIDER_SUSPENDED");
                Duration remaining=Duration.between(clock.instant(),until);
                if(remaining.isNegative() || remaining.isZero()) break;
                try { pause.sleep(remaining.compareTo(Duration.ofMillis(50))>0?Duration.ofMillis(50):remaining); }
                catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new PlaywrightDispatchCancelledException();
                }
            }
        }
    }

    private void observeRefusal(PlaywrightTransportDiagnostic d, UUID dispatchId, UUID campaignId,
                               AtomicReference<Refusal> observed) {
        if(d==null || d.httpStatus()==null || (d.httpStatus()!=403 && d.httpStatus()!=429)) return;
        Refusal evidence=new Refusal(dispatchId,campaignId,d.httpStatus(),
                d.headersReceivedAt()==null?clock.instant():d.headersReceivedAt(),d.retryAfterNotBefore());
        if(observed.compareAndSet(null,evidence)) persistRefusal(evidence);
    }
    /**
     * V8 alone upgrades a charged reservation to the worker-side departure
     * timestamp.  A missing request timestamp is deliberately not inferred:
     * the completion trigger retains its conservative fallback instead.
     */
    private void recordAuthenticatedV8Departure(UUID dispatchId, DepartureProfile profile,
                                                Instant requestedAt, Instant observedAt) {
        if (profile != DepartureProfile.LIVE_V8 || requestedAt == null) return;
        if (observedAt == null || requestedAt.isAfter(observedAt))
            throw new IllegalStateException("PROVIDER_REQUESTED_TIMESTAMP_INCOHERENT");
        store.recordAuthenticatedV8Departure(dispatchId,requestedAt,observedAt);
    }
    private void persistRefusal(Refusal refusal) {
        pendingRefusal.compareAndSet(null,refusal);
        store.suspend(refusal.evidenceId(),refusal.campaignId(),refusal.status(),refusal.at(),refusal.retryAt());
        pendingRefusal.compareAndSet(refusal,null);
    }
    private void flushPendingRefusal() {
        Refusal pending=pendingRefusal.get();
        if(pending!=null) persistRefusal(pending);
    }
}
