package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.OrderState;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.Owner;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Publish proof, catalogue, latest-success pointer, J8 terminal and order in one transaction. */
@Service
public class J3CollectionCompletionService {
    private final J3CollectionStore collections;private final J3AutomationStore orders;
    public J3CollectionCompletionService(J3CollectionStore collections,J3AutomationStore orders) {this.collections=collections;this.orders=orders;}
    public Optional<Proof> committedProof(UUID runId) {
        var order=orders.find(runId);
        return collections.find(runId).filter(c->order.isPresent()
                && order.orElseThrow().state().name().equals(c.proof().state().name())
                && Objects.equals(order.orElseThrow().reason(),c.proof().terminalCode())).map(c->c.proof());
    }
    @Transactional public void interrupt(UUID runId,Owner owner,java.time.Instant now,String reason) {
        collections.interrupt(runId,now,reason);
        orders.finish(runId,owner,OrderState.INTERRUPTED,reason,now);
    }
    @Transactional public void publish(Proof proof,List<Entry> entries,Owner owner,J8BenchmarkAuditService.Session audit) {
        collections.publish(proof,entries);
        if(audit!=null) audit.finishWithCollectionPublication(proof.successful()?J8BenchmarkCampaignTerminalState.COMPLETED
                :proof.state()==State.CANCELLED?J8BenchmarkCampaignTerminalState.CANCELLED:J8BenchmarkCampaignTerminalState.FAILED,
                proof.successful()?Optional.empty():Optional.of(proof.terminalCode()));
        orders.finish(proof.runId(),owner,OrderState.valueOf(proof.state().name()),proof.terminalCode(),proof.finishedAt());
    }
}
