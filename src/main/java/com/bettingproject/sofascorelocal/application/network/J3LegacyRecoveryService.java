package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.port.J3CollectionStore;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

/** Offline reconstruction uses only one terminal J8 campaign and its exact ordered sources. */
@Service
public class J3LegacyRecoveryService {
    private final J3CollectionStore store;
    private final RawSnapshotInspectionStore snapshots;
    private final J3CatalogProjector projector;
    public J3LegacyRecoveryService(J3CollectionStore store,RawSnapshotInspectionStore snapshots,J3CatalogProjector projector) {
        this.store=store; this.snapshots=snapshots; this.projector=projector;
    }
    public synchronized int recover() {
        int recovered=0;
        for (;;) {
            var batch=store.legacyCandidates(100);
            if (batch.isEmpty()) return recovered;
            for(var legacy:batch) { recover(legacy); recovered++; }
        }
    }
    private void recover(Legacy legacy) {
        var pages=new ArrayList<J3MinimizedPageEvidence>();
        try {
            if ((legacy.maximumPages()!=25 && legacy.maximumPages()!=35) || legacy.completedPages()<1
                    || legacy.completedPages()>legacy.maximumPages() || legacy.pages().size()!=legacy.completedPages())
                throw new IllegalArgumentException("J3_LEGACY_PAGE_COUNT");
            var parser=new ScheduledEventsV1Parser();
            for(var page:legacy.pages()) {
                if (page.page()!=pages.size()+1 || page.snapshotId()==null || !"PARSED".equals(page.outcome())
                        || !ScheduledEventsV1Parser.PARSER_VERSION.equals(page.parserVersion())
                        || !page.requestKey().equals("SCHEDULED_EVENTS|date="+legacy.date()+"|page="+page.page()))
                    throw new IllegalArgumentException("J3_LEGACY_PAGE_UNPROVEN");
                var source=(page.occurrenceId()==null ? snapshots.findById(page.snapshotId())
                        : snapshots.findOccurrence(page.snapshotId(),page.occurrenceId()))
                        .orElseThrow(()->new IllegalArgumentException("J3_LEGACY_RAW_UNAVAILABLE"));
                var s=source.summary();
                var payload=RawPayloadEvidence.capture(source.payloadRaw());
                if (page.requestedAt()==null || page.receivedAt()==null || page.latencyMillis()==null || page.resolvedAt()==null)
                    throw new IllegalArgumentException("J3_LEGACY_TIME_UNPROVEN");
                var response=new ScheduledEventsTransportResponse(page.requestKey(),page.requestedAt(),page.receivedAt(),
                        s.httpStatus(),s.contentType(),Duration.ofMillis(page.latencyMillis()),payload);
                var parsed=parser.parseTransportResponse(response);
                if (parsed.status()!=ScheduledEventsParseStatus.PARSED) throw new IllegalArgumentException("J3_LEGACY_PARSE_FAILED");
                var mode="MANUAL_LOCAL_JSON_IMPORT".equals(page.source()) ? J3PageResolutionSource.LOCAL_JSON_IMPORT
                        : J3PageResolutionSource.valueOf(page.source());
                var outcome=mode==J3PageResolutionSource.CACHE ? RawSnapshotPersistenceOutcome.CACHE_HIT
                        : RawSnapshotPersistenceOutcome.valueOf(page.persistenceOutcome());
                // J8 did not record cache insertion time. Preserve that absence explicitly.
                pages.add(new J3MinimizedPageEvidence(page.page(),mode,mode==J3PageResolutionSource.PROVIDER,
                        page.resolvedAt(),null,
                        page.requestedAt(),page.receivedAt(),s.httpStatus(),page.latencyMillis(),s.snapshotId(),outcome,
                        Math.toIntExact(s.payloadSizeBytes()),s.payloadSha256(),s.schemaStatus(),
                        parsed.page().orElseThrow().hasNextPage(),null,page.occurrenceId(),
                        mode==J3PageResolutionSource.CACHE));
            }
            var projected=projector.project(legacy.date(),pages);
            if (!projected.catalog().available()) throw new IllegalArgumentException(projected.catalog().status().name());
            var proof=new Proof(legacy.campaignId(),legacy.date(),Trigger.LEGACY,legacy.startedAt(),legacy.finishedAt(),
                    State.COMPLETED,"NONE",pages);
            store.begin(proof.runId(),proof.date(),proof.trigger(),proof.startedAt());
            store.publish(proof,projected.entries());
            store.recordRecovery(legacy,"RECOVERED","NONE",Instant.now());
        } catch (IllegalArgumentException failure) {
            String code=failure.getMessage();
            if (code==null || !code.matches("[A-Z0-9_]{1,96}")) code="J3_LEGACY_PROOF_INVALID";
            store.recordRecovery(legacy,"SUCCESS_PROVEN_CONTENT_UNAVAILABLE",code,Instant.now());
        }
    }
}
