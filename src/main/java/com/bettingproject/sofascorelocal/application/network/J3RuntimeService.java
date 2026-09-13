package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService;
import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One durable scheduler identity and one serial consumer, active only in the local application. */
@Service
public class J3RuntimeService {
    private final J3AutomationStore orders;
    private final J3LegacyRecoveryService recovery;
    private final J3CollectionCompletionService completion;
    private final J3CollectionExecutor engine;
    private final J3ProviderQualificationPolicy qualification;
    private final ProviderCampaignGuardStore guard;
    private final ProviderResilienceStore resilience;
    private final ManualProviderRequestCoordinator coordinator;
    private final PlaywrightProviderCampaignFactory factory;
    private final PlaywrightProviderSupervisor supervisor;
    private final LiveCampaignService live;
    private final Environment environment;
    private final boolean executionEnabled;
    private final Owner owner=new Owner(UUID.randomUUID(),ProcessHandle.current().pid(),
            ProcessHandle.current().info().startInstant().orElseThrow().truncatedTo(ChronoUnit.MICROS));
    private final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("j3-clock").factory());
    private final ExecutorService consumer=Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("j3-collection-owner").factory());
    private final Map<UUID,List<RawPayloadEvidence>> imports=new HashMap<>();
    private final AtomicBoolean consuming=new AtomicBoolean();
    private final Object cleanupMonitor=new Object();
    private volatile boolean ready,stopping,cleanupRequested;
    private volatile String runtimeReason="STARTING";
    private volatile Standalone pendingCleanup;
    private volatile Order reconciliation;
    private volatile UUID ownedStandaloneId;
    private Instant previousTick;
    private long previousNanos;

    public J3RuntimeService(J3AutomationStore orders,J3LegacyRecoveryService recovery,
            J3CollectionCompletionService completion,J3CollectionExecutor engine,
            J3ProviderQualificationPolicy qualification,ProviderCampaignGuardStore guard,
            ProviderResilienceStore resilience,ManualProviderRequestCoordinator coordinator,
            PlaywrightProviderCampaignFactory factory,PlaywrightProviderSupervisor supervisor,
            LiveCampaignService live,Environment environment,
            @Value("${sofascore.j3.runtime-enabled:true}") boolean executionEnabled) {
        this.orders=orders;this.recovery=recovery;this.completion=completion;this.engine=engine;
        this.qualification=qualification;this.guard=guard;this.resilience=resilience;this.coordinator=coordinator;
        this.factory=factory;this.supervisor=supervisor;this.live=live;this.environment=environment;this.executionEnabled=executionEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        // Maintenance CLIs also publish ApplicationReadyEvent with the local profile.
        // Only the running loopback web application owns the daily scheduler.
        if (!(event.getApplicationContext() instanceof org.springframework.web.context.WebApplicationContext web)
                || web.getServletContext() == null
                || !"127.0.0.1".equals(environment.getProperty("server.address"))) {
            runtimeReason="J3_WEB_APPLICATION_REQUIRED";return;
        }
        start();
    }

    public void start() {
        if(!executionEnabled || !environment.acceptsProfiles(Profiles.of("local"))) {
            runtimeReason="J3_RUNTIME_DISABLED";return;
        }
        // Offline reconstruction precedes the first daily success decision. No transport is opened here.
        if(!orders.lead(owner,J3RuntimeService::ownerMayBeAlive)) {runtimeReason="J3_OTHER_PROCESS_OWNER";return;}
        try {
            for(Order interrupted:orders.interrupted(owner)) {
                if(ownerMayBeAlive(interrupted.owner())) continue;
                completion.interrupt(interrupted.id(),interrupted.owner(),Instant.now(),"OWNER_PROCESS_ABSENT");
            }
            recovery.recover();
            ready=true;runtimeReason=null;
            ticker.scheduleWithFixedDelay(this::tickSafely,0,500,TimeUnit.MILLISECONDS);
        } catch(RuntimeException failure) {
            runtimeReason="J3_STARTUP_RECONCILIATION_FAILED";
            // Keep leadership: startup failure does not authorize a different process to collect.
        }
    }

    static boolean ownerMayBeAlive(Owner owner) {
        var process=ProcessHandle.of(owner.pid());
        if(process.isEmpty() || !process.orElseThrow().isAlive()) return false;
        var started=process.orElseThrow().info().startInstant();
        return started.isEmpty() || started.orElseThrow().truncatedTo(ChronoUnit.MICROS)
                .equals(owner.processStartedAt().truncatedTo(ChronoUnit.MICROS));
    }

    private void tickSafely() {
        if(stopping || !ready) return;
        Instant now=Instant.now();long nanos=System.nanoTime();
        boolean continuous=previousTick!=null && nanos-previousNanos<TimeUnit.SECONDS.toNanos(3)
                && Math.abs(Duration.between(previousTick,now).toMillis()
                    -TimeUnit.NANOSECONDS.toMillis(nanos-previousNanos))<2000;
        try {
            String unavailable=providerUnavailableReason();
            orders.tick(owner,previousTick==null?now:previousTick,now,continuous,unavailable);
            if(pendingCleanup==null && reconciliation==null) runtimeReason=null;
            wakeConsumer();
        } catch(RuntimeException unavailable) {runtimeReason="J3_PERSISTENCE_UNAVAILABLE";}
        previousTick=now;previousNanos=nanos;
    }

    public String providerUnavailableReason() {
        var access=qualification.snapshot();
        if(!access.available()) return access.blockers().getFirst();
        var state=resilience.snapshot();
        if(state.state()==ProviderResilienceData.State.SUSPENDED) return "PROVIDER_SUSPENDED";
        var ownership=guard.snapshot();
        if(ownership!=null && "CLEANUP_REQUIRED".equals(ownership.state())) return "PROVIDER_CLEANUP_REQUIRED";
        boolean currentOwner=ownership!=null && "OWNED".equals(ownership.state()) && ownership.owner()!=null
                && ownership.owner().instanceId().equals(coordinator.instanceOwner().instanceId());
        if(state.unresolvedDispatchId()!=null && !currentOwner) return "PROVIDER_DEPARTURE_UNRESOLVED";
        if(ownership!=null && "OWNED".equals(ownership.state()) && !currentOwner) return "PROVIDER_OTHER_PROCESS_OWNER";
        String liveReason=live.j3AvailabilityReason();
        if(liveReason!=null) return liveReason;
        return null;
    }
    public String runtimeReason() {return runtimeReason;}
    public boolean cleanupPending() {return pendingCleanup!=null;}
    public List<Order> orders() {return orders.recent(200);}
    public Settings settings() {return orders.settings();}

    public synchronized Order manual(UUID id,LocalDate date,List<RawPayloadEvidence> pages) {
        requireReady();
        Trigger trigger=pages==null?Trigger.MANUAL_PROVIDER:Trigger.MANUAL_IMPORT;
        String hash=null;
        if(pages!=null) {
            pages=J3LocalBatchValidator.validate(date,pages);hash=J3LocalBatchValidator.fingerprint(pages);
            if(!imports.containsKey(id) && imports.size()>=8) throw new IllegalStateException("J3_IMPORT_QUEUE_FULL");
        } else {
            String reason=providerUnavailableReason();if(reason!=null) throw new IllegalStateException(reason);
        }
        if(pages!=null) imports.put(id,pages);
        try {
            Order order=orders.manual(id,date,trigger,hash,owner,Instant.now());
            if(order.terminal()) imports.remove(id);
            wakeConsumer();return order;
        } catch(RuntimeException failure) {imports.remove(id);throw failure;}
    }
    public Settings configure(long revision,boolean enabled,Mode mode,LocalTime time) {
        return orders.configure(revision,enabled,mode,mode==Mode.DAILY_AT?time:null,Instant.now());
    }
    public Order schedule(UUID rule,int revision,LocalDate date,LocalDateTime at,ZoneOffset offset) {
        return orders.schedule(rule,revision,date,
                com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.resolveOneShot(at,offset),Instant.now());
    }
    public void cancel(UUID id) {orders.cancel(id,Instant.now());synchronized(this) {imports.remove(id);}}
    public void retryCleanup() {
        synchronized(cleanupMonitor) {
            if(pendingCleanup==null) throw new IllegalStateException("J3_NO_CLEANUP_PENDING");
            cleanupRequested=true;cleanupMonitor.notifyAll();
        }
    }
    private void requireReady() {
        if(!ready || stopping) throw new IllegalStateException(runtimeReason==null?"J3_RUNTIME_UNAVAILABLE":runtimeReason);
    }

    private void wakeConsumer() {
        if(consuming.compareAndSet(false,true)) consumer.execute(()->{
            try {
                if(reconciliation!=null) {
                    Order unresolved=reconciliation;
                    if(!orders.find(unresolved.id()).orElseThrow().terminal())
                        completion.interrupt(unresolved.id(),unresolved.owner(),Instant.now(),"EXECUTION_INTERRUPTED");
                    reconciliation=null;
                }
                while(!stopping) {
                    Optional<Order> claimed=orders.claim(owner,Instant.now());
                    if(claimed.isEmpty()) break;
                    execute(claimed.orElseThrow());
                }
                synchronized(this) {
                    imports.keySet().removeIf(id->orders.find(id).map(Order::terminal).orElse(true));
                }
            } catch(RuntimeException failure) {runtimeReason="J3_EXECUTION_RECONCILIATION_REQUIRED";}
            finally {consuming.set(false);}
        });
    }

    private void execute(Order order) {
        reconciliation=order;
        Standalone standalone=null;
        var runtimeStop=new java.util.concurrent.atomic.AtomicReference<String>();
        Thread watchdog=watch(order,runtimeStop);
        Supplier<String> cancelled=()->runtimeStop.get()!=null?runtimeStop.get():stopping?"APPLICATION_STOPPED":
                order.automatic() && !orders.settings().enabled()?"AUTOMATION_DISABLED":null;
        try {
            if(order.trigger()==Trigger.MANUAL_IMPORT) {
                List<RawPayloadEvidence> pages;
                synchronized(this) {pages=imports.get(order.id());}
                if(pages==null) throw new IllegalStateException("J3_IMPORT_BYTES_UNAVAILABLE");
                engine.execute(order,null,pages,null,cancelled);
            } else {
                var access=qualification.snapshot();
                if(!access.available()) throw new IllegalStateException(access.blockers().getFirst());
                var resource=reserveNetwork(order,access.providerOrigin(),cancelled);
                standalone=resource.standalone();
                if(resource.live()!=null) {
                    // Work continues on the live owner, which retains its thread-affine lease.
                    resource.live().get();
                } else {
                    engine.execute(order,access.providerOrigin(),null,standalone,cancelled);
                }
            }
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();runtimeReason="J3_INTERRUPTED";
            if(!orders.find(order.id()).orElseThrow().terminal())
                completion.interrupt(order.id(),order.owner(),Instant.now(),"APPLICATION_STOPPED");
        } catch(RuntimeException | ExecutionException failure) {
            // Read after an uncertain commit; never retry provider work for the same order.
            if(!orders.find(order.id()).orElseThrow().terminal())
                completion.interrupt(order.id(),order.owner(),Instant.now(),"EXECUTION_INTERRUPTED");
        } finally {
            watchdog.interrupt();
            synchronized(this) {imports.remove(order.id());}
            if(standalone!=null) finishStandalone(standalone);
        }
        reconciliation=null;
    }

    private record Resource(Standalone standalone,CompletableFuture<J3CollectionExecutor.Result> live) { }
    private Resource reserveNetwork(Order order,java.net.URI origin,Supplier<String> cancelled) throws InterruptedException {
        for(;;) {
            if(cancelled.get()!=null || !Instant.now().plusSeconds(130).isBefore(order.deadline()))
                throw new PlaywrightDispatchCancelledException();
            try {
                var handoff=live.submitJ3(order,(scope,liveCancellation)->engine.execute(order,origin,null,scope,
                        ()->{String stopped=cancelled.get();return stopped!=null?stopped:liveCancellation.get();}));
                if(handoff.isPresent()) return new Resource(null,handoff.orElseThrow());
                // Atomic lease acquisition settles the race with a simultaneous live launch.
                var lease=coordinator.tryAcquireJ3Campaign(order.id());
                var standalone=new Standalone(order,cancelled);standalone.lease=lease;
                ownedStandaloneId=order.id();return new Resource(standalone,null);
            } catch(ManualProviderRequestCoordinator.CoordinationException busy) {
                Thread.sleep(100);
            } catch(IllegalStateException unavailable) {
                if(!"J3_LIVE_UNAVAILABLE".equals(unavailable.getMessage())) throw unavailable;
                Thread.sleep(100);
            }
        }
    }

    private Thread watch(Order order,java.util.concurrent.atomic.AtomicReference<String> stopped) {
        Instant wall=Instant.now();long start=System.nanoTime();
        long remaining=Math.max(0,Duration.between(wall,order.deadline()).toNanos());
        return Thread.ofPlatform().daemon(true).name("j3-deadline-watch").start(()->{
            long before=start;Instant previous=wall;
            while(!Thread.currentThread().isInterrupted()) {
                try {Thread.sleep(200);} catch(InterruptedException done) {Thread.currentThread().interrupt();return;}
                long now=System.nanoTime();Instant current=Instant.now();
                long elapsed=TimeUnit.NANOSECONDS.toMillis(now-before);
                if(elapsed>3000 || Math.abs(Duration.between(previous,current).toMillis()-elapsed)>2000
                        || now-start>=remaining || !current.isBefore(order.deadline())) {
                    stopped.set(now-start>=remaining?"ADMISSION_DEADLINE_EXPIRED":"APPLICATION_INTERRUPTED");
                    if(order.id().equals(ownedStandaloneId))
                        supervisor.stopCampaign(order.id(),Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
                    return;
                }
                before=now;previous=current;
            }
        });
    }

    private void finishStandalone(Standalone access) {
        try {access.release();ownedStandaloneId=null;return;}
        catch(RuntimeException failure) {pendingCleanup=access;runtimeReason="PROVIDER_CLEANUP_REQUIRED";}
        // Only this original owner can release its lease, after explicit local cleanup.
        while(!stopping) {
            synchronized(cleanupMonitor) {
                while(!cleanupRequested && !stopping) {
                    try {cleanupMonitor.wait();}
                    catch(InterruptedException interrupted) {Thread.currentThread().interrupt();return;}
                }
                cleanupRequested=false;
            }
            if(stopping) return;
            try {access.release();pendingCleanup=null;ownedStandaloneId=null;runtimeReason=null;return;}
            catch(RuntimeException failure) {runtimeReason="PROVIDER_CLEANUP_REQUIRED";}
        }
    }

    private final class Standalone implements J3CollectionExecutor.ProviderAccess {
        final Order order;final Supplier<String> cancelled;
        ManualProviderRequestCoordinator.CampaignLease lease;
        PlaywrightProviderCampaign campaign;
        boolean closed,releaseAttempted;
        Standalone(Order order,Supplier<String> cancelled) {this.order=order;this.cancelled=cancelled;}

        @Override public ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request,Runnable dispatch) {
            check();
            String unavailable=providerUnavailableReason();
            if(unavailable!=null) throw new IllegalStateException(unavailable);
            if(lease==null) {
                // No SQL transaction spans this bounded wait.
                while(lease==null) {
                    check();
                    try {lease=coordinator.tryAcquireJ3Campaign(order.id());}
                    catch(ManualProviderRequestCoordinator.CoordinationException busy) {
                        if(!Instant.now().plusSeconds(130).isBefore(order.deadline())) throw busy;
                        try {Thread.sleep(100);} catch(InterruptedException interrupted) {
                            Thread.currentThread().interrupt();throw new PlaywrightDispatchCancelledException();
                        }
                    }
                }
            }
            if(campaign==null) campaign=factory.open(order.id(),Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
            lease.beginRequest();
            var response=campaign.execute(PlaywrightProviderRequest.scheduledEvents(request.date(),request.page()),
                    new PlaywrightDispatchAdmission() {
                        public void check() {Standalone.this.check();}
                        public Permit acquireDispatchPermit() {check();dispatch.run();return ()->{};}
                    });
            return new ScheduledEventsTransportResponse(request.requestKey(),response.requestedAt(),response.receivedAt(),
                    response.httpStatus(),response.contentType().isBlank()?"application/octet-stream":response.contentType(),
                    response.latency(),response.payload());
        }
        private void check() {
            if(closed || cancelled.get()!=null || !Instant.now().isBefore(order.deadline()))
                throw new PlaywrightDispatchCancelledException();
            if(lease!=null && !guard.isOwned(lease.ownership())) throw new PlaywrightDispatchCancelledException();
        }
        @Override public void close() {
            if(closed) return;
            if(campaign!=null) campaign.close();
            if(supervisor.activeCampaignId().filter(order.id()::equals).isPresent())
                throw new IllegalStateException("PROVIDER_CLEANUP_UNVERIFIED");
            closed=true;
        }
        void release() {
            if(lease==null) return;
            try {
                close();
                if(releaseAttempted) lease.retryCloseAfterVerifiedCleanup();
                else {releaseAttempted=true;lease.close();}
            } catch(RuntimeException failure) {
                supervisor.stopCampaign(order.id(),Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
                guard.requireCleanup(lease.ownership(),Instant.now());throw failure;
            }
        }
    }

    @PreDestroy public void stop() {
        stopping=true;ticker.shutdownNow();
        synchronized(cleanupMonitor) {cleanupMonitor.notifyAll();}
        consumer.shutdown();
        if(ownedStandaloneId!=null) supervisor.stopCampaign(ownedStandaloneId,Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        if(pendingCleanup!=null) supervisor.stopCampaign(pendingCleanup.order.id(),Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        // Parent identity/guard reconciliation after restart never reopens a browser.
        try {orders.release(owner);} catch(RuntimeException ignored) { }
    }
}
