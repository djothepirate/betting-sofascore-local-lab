package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7DeliveryCandidate;
import com.bettingproject.sofascorelocal.application.export.J7ValidatedExportArtifact;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;
import com.bettingproject.sofascorelocal.port.OwnedJ7DeliveryTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring-composed, manually invoked J7 sender. It opens the certificate store and HTTP client only
 * after persisted provenance, policy and the exact operator phrase have been verified. The J7
 * bytes are then independently reverified before the ledger claim.
 */
@Service
public final class J7DeliveryRuntimeService {

    private final J7CanonicalExportService exportService;
    private final J7DeliveryLedgerStore ledgerStore;
    private final J7DeliveryTransportFactory transportFactory;
    private final J7DeliveryAcknowledgementParser acknowledgementParser;
    private final J7DeliveryPolicy policy;
    private final Clock clock;
    private final J7DeliveryExecutionGate executionGate;

    @Autowired
    public J7DeliveryRuntimeService(
            J7CanonicalExportService exportService,
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryTransportFactory transportFactory,
            J7DeliveryPolicy policy,
            J7DeliveryExecutionGate executionGate) {
        this(
                exportService,
                ledgerStore,
                transportFactory,
                new J7DeliveryAcknowledgementParser(),
                policy,
                Clock.systemUTC(),
                executionGate);
    }

    J7DeliveryRuntimeService(
            J7CanonicalExportService exportService,
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryTransportFactory transportFactory,
            J7DeliveryAcknowledgementParser acknowledgementParser,
            J7DeliveryPolicy policy,
            Clock clock,
            J7DeliveryExecutionGate executionGate) {
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.ledgerStore = Objects.requireNonNull(ledgerStore, "ledgerStore");
        this.transportFactory = Objects.requireNonNull(
                transportFactory, "transportFactory");
        this.acknowledgementParser = Objects.requireNonNull(
                acknowledgementParser, "acknowledgementParser");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executionGate = Objects.requireNonNull(
                executionGate, "executionGate");
    }

    public J7DeliveryExecutionResult deliver(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation,
            int expectedAttemptNumber) {
        try (J7DeliveryExecutionGate.Lease lease = executionGate.acquire()) {
            return deliverExclusively(
                    canonicalEventId,
                    exportId,
                    confirmation,
                    expectedAttemptNumber,
                    lease);
        }
    }

    private J7DeliveryExecutionResult deliverExclusively(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation,
            int expectedAttemptNumber,
            J7DeliveryExecutionGate.Lease lease) {
        if (expectedAttemptNumber < 1) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_CONFIRMATION);
        }
        J7DeliveryCandidate candidate = exportService.deliveryCandidate(
                canonicalEventId, exportId);
        J7DeliveryPayloadClass payloadClass = candidate.payloadClass();
        J7DeliveryTransportFactory.Configuration configuration =
                policy.runtimeTransportConfiguration(payloadClass);
        J7DeliveryIdentity identity = new J7DeliveryIdentity(
                candidate.exportId(), candidate.fileSha256());
        J7OptionalDeliveryService.requireExactConfirmation(
                confirmation,
                J7OptionalDeliveryService.confirmationFor(identity));
        J7ValidatedExportArtifact artifact = exportService.loadHumanValidatedForDelivery(
                canonicalEventId, exportId);
        requireCandidateStillMatches(candidate, artifact);

        OwnedJ7DeliveryTransport transport;
        try {
            transport = Objects.requireNonNull(
                    transportFactory.open(configuration),
                    "transportFactory.open(configuration)");
        }
        catch (RuntimeException exception) {
            throw new J7DeliveryException(
                    J7DeliveryError.TRANSPORT_CONFIGURATION_FAILED);
        }

        RuntimeException primaryFailure = null;
        try {
            J7OptionalDeliveryService executor = new J7OptionalDeliveryService(
                    exportService,
                    ledgerStore,
                    transport,
                    acknowledgementParser,
                    policy,
                    clock);
            return executor.deliverVerifiedArtifact(
                    artifact,
                    confirmation,
                    payloadClass,
                    expectedAttemptNumber);
        }
        catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        }
        finally {
            try {
                transport.close();
            }
            catch (RuntimeException exception) {
                lease.poison();
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(exception);
                }
                else {
                    throw new J7DeliveryException(
                            J7DeliveryError.TRANSPORT_CLEANUP_FAILED);
                }
            }
        }
    }

    private static void requireCandidateStillMatches(
            J7DeliveryCandidate candidate,
            J7ValidatedExportArtifact artifact) {
        if (!candidate.exportId().equals(artifact.exportId())
                || !candidate.canonicalEventId().equals(artifact.canonicalEventId())
                || !candidate.fileSha256().equals(artifact.fileSha256())
                || candidate.payloadClass() != artifact.payloadClass()) {
            throw new J7DeliveryException(J7DeliveryError.PAYLOAD_IDENTITY_CHANGED);
        }
    }
}
