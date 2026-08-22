package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessingResult;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class J5OfflineBatchTransactionalImporter {

    private final J5OfflineBatchPlanService planService;
    private final J5OfflineBatchPolicy policy;
    private final J5LocalJsonImportProcessor processor;

    public J5OfflineBatchTransactionalImporter(
            J5OfflineBatchPlanService planService,
            J5OfflineBatchPolicy policy,
            J5LocalJsonImportProcessor processor) {
        this.planService = Objects.requireNonNull(planService, "planService");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.processor = Objects.requireNonNull(processor, "processor");
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public J5OfflineBatchResult importAtomically(
            J5OfflineBatchExecutionClaim claim,
            List<J5OfflineBatchPreparedEvent> preparedEvents,
            long totalBytes) {
        Objects.requireNonNull(claim, "claim");
        List<J5OfflineBatchPreparedEvent> prepared = List.copyOf(
                Objects.requireNonNull(preparedEvents, "preparedEvents"));
        J5OfflineBatchPlan plan = claim.plan();
        if (prepared.size() != plan.events().size()) {
            throw new IllegalArgumentException("prepared event count does not match the plan");
        }
        for (int index = 0; index < prepared.size(); index++) {
            if (!prepared.get(index).event().equals(plan.events().get(index))) {
                throw new IllegalArgumentException("prepared events are out of plan order");
            }
        }

        policy.requireAvailable();
        planService.requireCurrent(plan);
        List<J5OfflineBatchEventResult> eventResults = new ArrayList<>();
        int imports = 0;
        for (J5OfflineBatchPreparedEvent preparedEvent : prepared) {
            J5LocalJsonImportProcessingResult processed = processor.execute(
                    preparedEvent.processingPlan(), () -> true);
            imports += processed.localJsonImports();
            J5OfflineBatchPlanEvent event = preparedEvent.event();
            eventResults.add(new J5OfflineBatchEventResult(
                    event.canonicalEventId(),
                    event.providerEventId(),
                    event.homeTeamName(),
                    event.awayTeamName(),
                    processed.endpoints()));
        }
        return new J5OfflineBatchResult(
                claim.requestId(),
                true,
                "COMPLETED",
                plan.events().size(),
                imports,
                totalBytes,
                eventResults);
    }
}
