package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData;
import com.bettingproject.sofascorelocal.port.J3CollectionStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only views never invoke acquisition, cache refresh or browser transport. */
@Controller
@RequestMapping("/j3/collections")
public class J3CollectionController {
    private final J3CollectionStore store;
    private com.bettingproject.sofascorelocal.port.J3AutomationStore orders;
    private com.bettingproject.sofascorelocal.port.J3LivePauseStore pauses;
    @org.springframework.beans.factory.annotation.Autowired
    void configureEvidence(com.bettingproject.sofascorelocal.port.J3AutomationStore orders,
                           com.bettingproject.sofascorelocal.port.J3LivePauseStore pauses) {
        this.orders=orders;this.pauses=pauses;
    }
    public J3CollectionController(J3CollectionStore store) {this.store=store;}
    @GetMapping(value="/{id}/evidence",produces="text/plain;charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> evidence(@PathVariable UUID id,@RequestParam LocalDate date) {
        var collection=store.find(id).filter(it->it.date().equals(date))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var proof=collection.proof();
        StringBuilder text=new StringBuilder("J3_MINIMIZED_EVIDENCE_VERSION=7\nEXPERIMENTAL\nLOCAL_ONLY\nNOT_PRODUCTION_APPROVED\nNO_CRITICAL_DEPENDENCY\n");
        text.append("collectionId=").append(id).append("\ncollectionDate=").append(date)
                .append("\ntrigger=").append(proof.trigger()).append("\nstate=").append(proof.state())
                .append("\nstartedAt=").append(proof.startedAt()).append("\nfinishedAt=").append(proof.finishedAt())
                .append("\nterminalCode=").append(proof.terminalCode()).append("\n");
        text.append("parserVersion=scheduled-events-v1\nmaximumPages=35\npageCount=").append(proof.pages().size())
                .append("\nproviderRequests=").append(proof.pages().stream().filter(p->p.providerRequestExecuted()).count())
                .append("\ncacheHits=").append(proof.pages().stream().filter(p->p.resolutionSource()==com.bettingproject.sofascorelocal.domain.provider.J3PageResolutionSource.CACHE).count())
                .append("\nlocalImportPages=").append(proof.pages().stream().filter(p->p.resolutionSource()==com.bettingproject.sofascorelocal.domain.provider.J3PageResolutionSource.LOCAL_JSON_IMPORT).count())
                .append("\n");
        if(orders!=null) orders.find(id).ifPresent(order->text.append("occurrenceKey=").append(order.occurrenceKey())
                .append("\nruleId=").append(order.ruleId()).append("\nruleRevision=").append(order.ruleRevision())
                .append("\ndueAt=").append(order.dueAt()).append("\nadmittedAt=").append(order.admittedAt())
                .append("\ndeadline=").append(order.deadline()).append("\n"));
        if(pauses!=null) pauses.forRun(id).ifPresent(pause->text.append("liveCampaignId=").append(pause.campaignId())
                .append("\nliveGuardGeneration=").append(pause.generation()).append("\nlivePausePhase=").append(pause.phase())
                .append("\nlivePausedAt=").append(pause.requestedAt()).append("\nlivePauseChangedAt=").append(pause.changedAt()).append("\n"));
        for(var page:proof.pages()) text.append("page=").append(page.page()).append(" source=").append(page.resolutionSource())
                .append(" snapshotId=").append(page.snapshotId()).append(" occurrenceId=").append(page.occurrenceId())
                .append(" sha256=").append(page.payloadSha256()).append(" bytes=").append(page.payloadSizeBytes())
                .append(" hasNextPage=").append(page.hasNextPage()).append(" schemaStatus=").append(page.schemaStatus())
                .append(" httpStatus=").append(page.httpStatus()).append(" persistence=").append(page.persistenceOutcome())
                .append(" requestedAt=").append(page.requestedAt()).append(" receivedAt=").append(page.receivedAt())
                .append(" resolvedAt=").append(page.resolvedAt()).append(" cacheStoredAt=").append(page.cacheStoredAt())
                .append(" historicalCacheTimestampAbsent=").append(page.historicalCacheTimestampAbsent())
                .append(" latencyMillis=").append(page.latencyMillis()).append(" code=").append(page.terminalCode()).append("\n");
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Content-Disposition","attachment; filename=\"j3-"+date+"-"+id+".txt\"").body(text.toString());
    }
    @GetMapping public String latest(@RequestParam(required=false) LocalDate date,Model model) {
        LocalDate selected=date==null?LocalDate.now(J3AutomationData.ZONE):date;
        var collection=store.latest(selected);
        if(collection.isPresent())return "redirect:/j3/collections/"+collection.orElseThrow().id()+"?date="+selected;
        model.addAttribute("selectedDate",selected);model.addAttribute("availableDates",store.dates(3660));
        model.addAttribute("historicalContentUnavailable",store.hasSuccess(selected));
        return "j3-collections";
    }
    @GetMapping("/{id}") public String page(@PathVariable UUID id,@RequestParam LocalDate date,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="25") int size,Model model) {
        try {
            var result=store.page(id,date,page,size).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
            model.addAttribute("catalogPage",result);model.addAttribute("selectedDate",date);
            var dates=store.dates(3660);
            model.addAttribute("availableDates",dates);
            dates.stream().filter(saved->saved.date().equals(date) && saved.runId()!=null && !saved.runId().equals(id))
                    .findFirst().ifPresent(saved->model.addAttribute("newerCollectionId",saved.runId()));
            return "j3-collections";
        } catch(IllegalArgumentException invalid) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage());}
    }
}
