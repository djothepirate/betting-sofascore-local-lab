package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.*;
import com.bettingproject.sofascorelocal.adapter.sofascore.transport.ScheduledEventsTransportException;
import com.bettingproject.sofascorelocal.domain.benchmark.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.Order;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.port.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** All J3 entry points share this bounded execution path; no legacy stop/intent mutations. */
@Service
public class J3CollectionExecutor {
    /** Implementations own cleanup; the dispatch callback runs only after the provider guard and delay. */
    public interface ProviderAccess extends AutoCloseable {
        ScheduledEventsTransportResponse execute(ScheduledEventsProviderPageRequest request,Runnable beforeDispatch);
        @Override void close();
    }
    public record Result(Proof proof,boolean safeToResumeLive) { }
    private final RawManualCallSnapshotStore raw;private final J3ScheduledEventsPageCache cache;
    private final J3CollectionStore collections;private final J3CatalogProjector projector;
    private final J3CollectionCompletionService completion;private final J8BenchmarkAuditService benchmark;
    private final Duration cacheTtl;private final Clock clock;
    @Autowired public J3CollectionExecutor(RawManualCallSnapshotStore raw,J3ScheduledEventsPageCache cache,
            J3CollectionStore collections,J3CatalogProjector projector,J3CollectionCompletionService completion,
            J8BenchmarkAuditService benchmark,SofascoreEndpointCatalog catalog) {
        this(raw,cache,collections,projector,completion,benchmark,catalog.get(SofascoreEndpointType.SCHEDULED_EVENTS).cacheTtl(),Clock.systemUTC());
    }
    J3CollectionExecutor(RawManualCallSnapshotStore raw,J3ScheduledEventsPageCache cache,J3CollectionStore collections,
            J3CatalogProjector projector,J3CollectionCompletionService completion,J8BenchmarkAuditService benchmark,Duration ttl,Clock clock) {
        this.raw=raw;this.cache=cache;this.collections=collections;this.projector=projector;this.completion=completion;
        this.benchmark=benchmark;this.cacheTtl=ttl;this.clock=clock;
    }
    public Result execute(Order order,URI origin,List<RawPayloadEvidence> imported,ProviderAccess provider,Supplier<String> cancellation) {
        boolean local=order.trigger()==Trigger.MANUAL_IMPORT;
        if(local) {
            imported=J3LocalBatchValidator.validate(order.date(),imported);
            if(provider!=null || !J3LocalBatchValidator.fingerprint(imported).equals(order.inputSha256()))
                throw new IllegalArgumentException("J3_IMPORT_IDENTITY_CONFLICT");
        } else if(provider==null || imported!=null)throw new IllegalArgumentException("J3_PROVIDER_ACCESS_REQUIRED");
        Instant started=clock.instant();
        var circuit=J3NetworkCircuit.forAcceptedCollection(started);
        var processor=new J3ScheduledEventsOutcomeProcessor(raw,new ScheduledEventsV1Parser(),circuit);
        var pages=new ArrayList<J3MinimizedPageEvidence>();
        J8BenchmarkAuditService.Session audit=null;J8BenchmarkAuditService.Unit pending=null;
        State state=State.FAILED;String code="PROCESSING_FAILURE";boolean fatal=false;
        List<Entry> entries=List.of();
        try {
            collections.begin(order.id(),order.date(),order.trigger(),started);
            audit=benchmark.start(order.id(),J8BenchmarkCampaignType.J3_SCHEDULED_EVENTS,
                    local?J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT:J8BenchmarkExecutionMode.GUARDED_PROVIDER,35,Optional.of(order.date()));
            for(int number=1;number<=35;number++) {
                String cancelled=cancellation.get();
                if(cancelled!=null) {code=cancelled;state=State.CANCELLED;break;}
                if(!clock.instant().isBefore(order.deadline())) {code="ADMISSION_DEADLINE_EXPIRED";state=State.CANCELLED;break;}
                String key="SCHEDULED_EVENTS|date="+order.date()+"|page="+number;
                pending=audit.declare(number,SofascoreEndpointType.SCHEDULED_EVENTS,key,Optional.empty(),OptionalLong.empty());
                audit.reach(pending);final var session=audit;final var unit=pending;
                J3MinimizedPageEvidence page;
                if(local) {
                    Instant at=clock.instant();
                    var response=new ScheduledEventsTransportResponse(key,at,at,200,"application/json",Duration.ZERO,imported.get(number-1));
                    var processed=processor.processImportedResponse(response,p->session.captureSnapshot(unit,p,ScheduledEventsV1Parser.PARSER_VERSION));
                    page=evidence(number,response,processed,true);
                } else {
                    var request=new ScheduledEventsProviderPageRequest(origin,order.date(),number);
                    var cached=cache.findFreshParsed(request,clock.instant(),cacheTtl,ScheduledEventsV1Parser.PARSER_VERSION);
                    if(cached.isPresent()) {
                        var hit=cached.orElseThrow();var parsed=new ScheduledEventsV1Parser().parseTransportResponse(hit.asTransportResponse());
                        if(parsed.status()!=ScheduledEventsParseStatus.PARSED || parsed.page().orElseThrow().payloadShape()!=ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST)
                            throw new IllegalStateException("CACHE_REPARSE_INCOMPATIBLE");
                        audit.captureSnapshot(unit,new RawSnapshotPersistenceResult(hit.snapshotId(),RawSnapshotPersistenceOutcome.CACHE_HIT,
                                hit.payload().sha256(),hit.payload().sizeBytes(),OptionalLong.empty()),ScheduledEventsV1Parser.PARSER_VERSION);
                        page=J3MinimizedPageEvidence.cached(number,hit,parsed.page().orElseThrow().hasNextPage(),clock.instant());
                    } else {
                        final boolean[] dispatched={false};Instant attemptedAt=clock.instant();
                        try {
                            var response=provider.execute(request,()->{
                                String stopped=cancellation.get();if(stopped!=null)throw new Cancelled(stopped);
                                // Includes the maximum bounded worker startup, exchange, delay and cleanup margin.
                                if(!clock.instant().plusSeconds(130).isBefore(order.deadline()))throw new Cancelled("ADMISSION_DEADLINE_EXPIRED");
                                session.startAttempt(unit);dispatched[0]=true;
                            });
                            if(!dispatched[0])throw new IllegalStateException("J3_DISPATCH_PROOF_MISSING");
                            audit.captureResponse(unit,response.httpStatus(),response.latency().toMillis());
                            var processed=processor.processResponse(response,p->session.captureSnapshot(unit,p,ScheduledEventsV1Parser.PARSER_VERSION));
                            page=evidence(number,response,processed,false);
                            if(page.schemaStatus()==RawSnapshotSchemaStatus.PARSED)
                                cache.recordParsed(request,response,processed.persistence().orElseThrow(),ScheduledEventsV1Parser.PARSER_VERSION);
                        } catch(ScheduledEventsTransportException failure) {
                            code=processor.processFailure(failure,clock.instant()).circuit().reason().name();
                            page=J3MinimizedPageEvidence.failedBeforeSnapshot(number,attemptedAt,code,dispatched[0]);
                            pages.add(page);collections.appendPage(order.id(),page);audit.resolveFailure(unit,code);pending=null;fatal=true;break;
                        }
                    }
                }
                pages.add(page);collections.appendPage(order.id(),page);
                if(page.schemaStatus()!=RawSnapshotSchemaStatus.PARSED) {
                    code=page.terminalCode();audit.resolveFailure(unit,code);pending=null;
                    fatal=page.httpStatus()==403 || page.httpStatus()==429;break;
                }
                audit.resolve(unit,local?J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT
                        :page.resolutionSource()==J3PageResolutionSource.CACHE?J8BenchmarkResolutionSource.CACHE:J8BenchmarkResolutionSource.PROVIDER,
                        J8BenchmarkOutcomeType.PARSED,Optional.of(RawSnapshotSchemaStatus.PARSED),0,Optional.empty(),OptionalInt.empty(),Optional.empty());
                pending=null;
                if(!page.hasNextPage()) {
                    var projection=projector.project(order.date(),pages);
                    if(projection.catalog().available()) {state=State.COMPLETED;code="NONE";entries=projection.entries();}
                    else {
                        code=projection.catalog().status().name();
                        fatal=!"SNAPSHOT_PARSE_INCOMPATIBLE".equals(code);
                    }
                    break;
                }
                if(number==35)code="PAGINATION_LIMIT_REACHED";
            }
        } catch(Cancelled stopped) {code=stopped.getMessage();state=State.CANCELLED;}
        catch(RuntimeException failure) {code="EVIDENCE_PROCESSING_FAILURE";fatal=true;}
        finally {
            if(provider!=null)try {provider.close();}
            catch(RuntimeException cleanup) {code="PROVIDER_CLEANUP_UNVERIFIED";state=State.FAILED;entries=List.of();fatal=true;}
        }
        if(state!=State.COMPLETED)entries=List.of();
        if(audit!=null && pending!=null) {
            try {audit.resolveFailureIfPending(pending,code);}
            catch(RuntimeException failure) {fatal=true;code="AUDIT_PUBLICATION_FAILURE";state=State.FAILED;audit=null;}
        }
        var proof=new Proof(order.id(),order.date(),order.trigger(),started,clock.instant(),state,code,pages);
        try {completion.publish(proof,entries,order.owner(),audit);}
        catch(RuntimeException failure) {
            // A publication failure cannot turn a coherent payload into a success. The previous pointer survives.
            var committed=completion.committedProof(order.id());
            if(committed.isPresent()) return new Result(committed.orElseThrow(),!fatal);
            var failed=new Proof(order.id(),order.date(),order.trigger(),started,clock.instant(),State.FAILED,"EVIDENCE_PUBLICATION_FAILURE",pages);
            completion.publish(failed,List.of(),order.owner(),null);
            return new Result(failed,false);
        }
        return new Result(proof,!fatal);
    }
    private static J3MinimizedPageEvidence evidence(int number,ScheduledEventsTransportResponse response,J3ScheduledEventsOutcome outcome,boolean local) {
        var saved=outcome.persistence().orElseThrow();Boolean next=outcome.hasNextPage().orElse(null);
        String reason=next==null?outcome.circuit().reason().name():null;
        RawSnapshotSchemaStatus schema=next!=null?RawSnapshotSchemaStatus.PARSED:response.httpStatus()==404?RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE
                :"SCHEMA_INCOMPATIBLE".equals(reason)?RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE
                :"UNEXPECTED_CONTENT".equals(reason)?RawSnapshotSchemaStatus.UNEXPECTED_CONTENT:RawSnapshotSchemaStatus.TRANSPORT_ERROR;
        return (local?J3MinimizedPageEvidence.imported(number,response,saved,schema,next,reason)
                :J3MinimizedPageEvidence.recorded(number,response,saved,schema,next,reason)).withOccurrence(saved.occurrenceId().orElseThrow());
    }
    private static final class Cancelled extends RuntimeException {Cancelled(String code){super(code);}}
}
