package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.security.Sha256;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Validate the whole import before creating an order or writing any raw page. */
public final class J3LocalBatchValidator {
    public static final long MAXIMUM_TOTAL_BYTES=25L*1024*1024;
    private J3LocalBatchValidator() { }
    public static List<RawPayloadEvidence> validate(LocalDate date,List<RawPayloadEvidence> values) {
        if(values==null || values.isEmpty())throw new J3LocalJsonImportException(J3LocalJsonImportError.EMPTY_BATCH);
        if(values.size()>35)throw new J3LocalJsonImportException(J3LocalJsonImportError.TOO_MANY_PAGES);
        var pages=List.copyOf(values);long bytes=0;var parser=new ScheduledEventsV1Parser();
        for(int i=0;i<pages.size();i++) {
            var page=pages.get(i);bytes+=page.sizeBytes();
            if(bytes>MAXIMUM_TOTAL_BYTES)throw new J3LocalJsonImportException(J3LocalJsonImportError.TOTAL_SIZE_EXCEEDED);
            var parsed=parser.parseTransportResponse(new ScheduledEventsTransportResponse("SCHEDULED_EVENTS|date="+date+"|page="+(i+1),
                    Instant.EPOCH,Instant.EPOCH,200,"application/json",Duration.ZERO,page));
            if(parsed.status()!=ScheduledEventsParseStatus.PARSED)
                throw new J3LocalJsonImportException(parsed.status()==ScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE
                        ?J3LocalJsonImportError.SCHEMA_INCOMPATIBLE:J3LocalJsonImportError.UNEXPECTED_CONTENT);
            var result=parsed.page().orElseThrow();
            if(result.payloadShape()!=ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST)
                throw new J3LocalJsonImportException(J3LocalJsonImportError.PAYLOAD_SHAPE_INCOMPATIBLE);
            if(result.hasNextPage()!=(i<pages.size()-1))
                throw new J3LocalJsonImportException(J3LocalJsonImportError.PAGINATION_SEQUENCE_INVALID);
        }
        return pages;
    }
    public static String fingerprint(List<RawPayloadEvidence> pages) {
        String value=String.join("\n",pages.stream().map(p->p.sizeBytes()+":"+p.sha256()).toList());
        return Sha256.hex(value.getBytes(StandardCharsets.UTF_8));
    }
}
