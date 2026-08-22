package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessingException;
import com.bettingproject.sofascorelocal.application.network.J5LocalJsonImportProcessor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class J5OfflineBatchImportService {

    private final J5OfflineBatchControlService controlService;
    private final J5OfflineBatchPolicy policy;
    private final J5OfflineBatchPlanService planService;
    private final J5OfflineBatchUploadService uploadService;
    private final J5LocalJsonImportProcessor processor;
    private final J5OfflineBatchTransactionalImporter transactionalImporter;

    public J5OfflineBatchImportService(
            J5OfflineBatchControlService controlService,
            J5OfflineBatchPolicy policy,
            J5OfflineBatchPlanService planService,
            J5OfflineBatchUploadService uploadService,
            J5LocalJsonImportProcessor processor,
            J5OfflineBatchTransactionalImporter transactionalImporter) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.planService = Objects.requireNonNull(planService, "planService");
        this.uploadService = Objects.requireNonNull(uploadService, "uploadService");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.transactionalImporter = Objects.requireNonNull(
                transactionalImporter, "transactionalImporter");
    }

    public synchronized J5OfflineBatchResult execute(
            UUID requestId,
            String confirmationText,
            boolean acknowledged,
            List<J5OfflineBatchUpload> uploads) {
        J5OfflineBatchPlan pending = controlService.requirePending(requestId);
        policy.requireAvailable();
        J5OfflineBatchUploadSet uploadSet = uploadService.validate(pending, uploads);
        planService.requireCurrent(pending);

        List<J5OfflineBatchPreparedEvent> prepared = new ArrayList<>();
        try {
            for (J5OfflineBatchEventPayloads payloads : uploadSet.events()) {
                prepared.add(new J5OfflineBatchPreparedEvent(
                        payloads.event(),
                        processor.prepare(
                                payloads.event().canonicalEventId(),
                                payloads.event().providerEventId(),
                                payloads.statistics(),
                                payloads.incidents(),
                                payloads.lineups())));
            }
        }
        catch (J5LocalJsonImportProcessingException exception) {
            throw rejected(mapProcessingError(exception), exception);
        }

        planService.requireCurrent(pending);
        J5OfflineBatchExecutionClaim claim = controlService.confirmAndClaim(
                requestId, confirmationText, acknowledged);
        try {
            J5OfflineBatchResult result = transactionalImporter.importAtomically(
                    claim, prepared, uploadSet.totalBytes());
            controlService.complete(requestId, result);
            return result;
        }
        catch (J5OfflineBatchException exception) {
            failControl(requestId, exception.error(), uploadSet.totalBytes());
            throw exception;
        }
        catch (J5LocalJsonImportProcessingException exception) {
            J5OfflineBatchError error = mapProcessingError(exception);
            failControl(requestId, error, uploadSet.totalBytes());
            throw rejected(error, exception);
        }
        catch (DataAccessException exception) {
            failControl(requestId, J5OfflineBatchError.STORAGE_UNAVAILABLE,
                    uploadSet.totalBytes());
            throw rejected(J5OfflineBatchError.STORAGE_UNAVAILABLE, exception);
        }
        catch (RuntimeException exception) {
            failControl(requestId, J5OfflineBatchError.LOCAL_EXECUTION_FAILURE,
                    uploadSet.totalBytes());
            throw rejected(J5OfflineBatchError.LOCAL_EXECUTION_FAILURE, exception);
        }
    }

    private void failControl(
            UUID requestId,
            J5OfflineBatchError error,
            long totalBytes) {
        if (controlService.executionMayContinue(requestId)) {
            controlService.fail(requestId, error, totalBytes);
        }
    }

    private static J5OfflineBatchError mapProcessingError(
            J5LocalJsonImportProcessingException exception) {
        return switch (exception.code()) {
            case "STATISTICS_PAYLOAD_INCOMPATIBLE" ->
                    J5OfflineBatchError.STATISTICS_PAYLOAD_INCOMPATIBLE;
            case "INCIDENTS_PAYLOAD_INCOMPATIBLE" ->
                    J5OfflineBatchError.INCIDENTS_PAYLOAD_INCOMPATIBLE;
            case "LINEUPS_PAYLOAD_INCOMPATIBLE" ->
                    J5OfflineBatchError.LINEUPS_PAYLOAD_INCOMPATIBLE;
            case "TOTAL_PAYLOAD_TOO_LARGE" -> J5OfflineBatchError.BATCH_TOO_LARGE;
            case "EVENT_ID_MISMATCH" -> J5OfflineBatchError.PLAN_CHANGED;
            case "CANONICAL_EVENT_LOOKUP_ERROR" ->
                    exception.getCause() instanceof DataAccessException
                            ? J5OfflineBatchError.STORAGE_UNAVAILABLE
                            : J5OfflineBatchError.LOCAL_EXECUTION_FAILURE;
            case "OPERATOR_STOP" -> J5OfflineBatchError.OPERATOR_STOP;
            case "RAW_PERSISTENCE_ERROR",
                    "NORMALIZATION_PERSISTENCE_ERROR",
                    "RAW_CLASSIFICATION_ERROR" -> J5OfflineBatchError.STORAGE_UNAVAILABLE;
            default -> J5OfflineBatchError.LOCAL_EXECUTION_FAILURE;
        };
    }

    private static J5OfflineBatchException rejected(
            J5OfflineBatchError error,
            Throwable cause) {
        return new J5OfflineBatchException(error, cause);
    }

    private static J5OfflineBatchException rejected(J5OfflineBatchError error) {
        return new J5OfflineBatchException(error);
    }
}
