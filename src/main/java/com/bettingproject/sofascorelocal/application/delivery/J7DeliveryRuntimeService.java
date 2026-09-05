package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7DeliveryCandidate;
import com.bettingproject.sofascorelocal.application.export.J7ValidatedExportArtifact;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;
import com.bettingproject.sofascorelocal.port.OwnedJ7DeliveryTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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
    private final J7DeliveryConfirmationCapabilityVerifier confirmationCapabilityVerifier;

    @Autowired
    public J7DeliveryRuntimeService(
            J7CanonicalExportService exportService,
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryTransportFactory transportFactory,
            J7DeliveryPolicy policy,
            J7DeliveryExecutionGate executionGate,
            J7DeliveryConfirmationCapabilityVerifier confirmationCapabilityVerifier) {
        this(
                exportService,
                ledgerStore,
                transportFactory,
                new J7DeliveryAcknowledgementParser(),
                policy,
                Clock.systemUTC(),
                executionGate,
                confirmationCapabilityVerifier);
    }

    J7DeliveryRuntimeService(
            J7CanonicalExportService exportService,
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryTransportFactory transportFactory,
            J7DeliveryAcknowledgementParser acknowledgementParser,
            J7DeliveryPolicy policy,
            Clock clock,
            J7DeliveryExecutionGate executionGate,
            J7DeliveryConfirmationCapabilityVerifier confirmationCapabilityVerifier) {
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
        this.confirmationCapabilityVerifier = Objects.requireNonNull(
                confirmationCapabilityVerifier, "confirmationCapabilityVerifier");
    }

    public J7DeliveryExecutionResult deliver(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation,
            int expectedAttemptNumber) {
        try (J7DeliveryExecutionGate.Lease lease = executionGate.acquire()) {
            if (expectedAttemptNumber < 1) {
                throw new J7DeliveryException(J7DeliveryError.INVALID_CONFIRMATION);
            }
            J7DeliveryCandidate candidate = exportService.deliveryCandidate(
                    canonicalEventId, exportId);
            if (candidate.payloadClass() == J7DeliveryPayloadClass.PROVIDER_DERIVED) {
                throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_REQUIRED);
            }
            return deliverExclusively(
                    new J7DeliveryConfirmationReceipt(
                            J7DeliveryConfirmationAction.DELIVERY,
                            canonicalEventId,
                            exportId,
                            candidate.fileSha256(),
                            expectedAttemptNumber,
                            Optional.empty()),
                    confirmation,
                    lease,
                    candidate);
        }
    }

    /**
     * Executes one delivery from the trusted receipt produced by the session-bound confirmation
     * service. Provider-derived receipts must carry the exact configured owner-go reference.
     */
    public J7DeliveryExecutionResult deliver(
            J7DeliveryConfirmationReceipt receipt,
            String confirmation) {
        Objects.requireNonNull(receipt, "receipt");
        try (J7DeliveryExecutionGate.Lease lease = executionGate.acquire()) {
            confirmationCapabilityVerifier.consumeRuntimeCapability(
                    receipt, clock.instant());
            return deliverExclusively(receipt, confirmation, lease, null);
        }
    }

    private J7DeliveryExecutionResult deliverExclusively(
            J7DeliveryConfirmationReceipt receipt,
            String confirmation,
            J7DeliveryExecutionGate.Lease lease,
            J7DeliveryCandidate preloadedCandidate) {
        if (receipt.action() != J7DeliveryConfirmationAction.DELIVERY) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_CONFIRMATION);
        }
        J7DeliveryCandidate candidate = preloadedCandidate == null
                ? exportService.deliveryCandidate(
                receipt.canonicalEventId(), receipt.exportId())
                : preloadedCandidate;
        requireReceiptMatchesCandidate(receipt, candidate);
        J7DeliveryPayloadClass payloadClass = candidate.payloadClass();
        J7DeliveryTransportFactory.Configuration configuration =
                policy.runtimeTransportConfiguration(payloadClass);
        J7DeliveryIdentity identity = new J7DeliveryIdentity(
                candidate.exportId(), candidate.fileSha256());
        J7OptionalDeliveryService.requireExactConfirmation(
                confirmation,
                J7OptionalDeliveryService.confirmationFor(identity));
        J7ValidatedExportArtifact artifact = exportService.loadHumanValidatedForDelivery(
                receipt.canonicalEventId(), receipt.exportId());
        requireCandidateStillMatches(candidate, artifact);

        if (payloadClass == J7DeliveryPayloadClass.PROVIDER_DERIVED) {
            return deliverProviderDerived(
                    receipt, artifact, identity, configuration, lease);
        }
        if (receipt.providerOwnerGoReference().isPresent()) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_NOT_ALLOWED);
        }
        return openAndExecuteSynthetic(
                artifact,
                confirmation,
                payloadClass,
                receipt.attemptNumber(),
                configuration,
                lease);
    }

    private J7DeliveryExecutionResult deliverProviderDerived(
            J7DeliveryConfirmationReceipt receipt,
            J7ValidatedExportArtifact artifact,
            J7DeliveryIdentity identity,
            J7DeliveryTransportFactory.Configuration configuration,
            J7DeliveryExecutionGate.Lease lease) {
        J7ProviderDerivedOwnerGo.Reference configuredReference =
                policy.providerOwnerGoReference();
        if (!receipt.providerOwnerGoReference()
                .filter(configuredReference::equals)
                .isPresent()) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_MISMATCH);
        }
        J7ProviderDerivedOwnerGo.Snapshot snapshot = ledgerStore
                .findProviderDerivedOwnerGo(configuredReference)
                .orElseThrow(() -> new J7DeliveryException(
                        J7DeliveryError.PROVIDER_OWNER_GO_NOT_AVAILABLE));
        if (snapshot.status() != J7ProviderDerivedOwnerGo.Status.AVAILABLE) {
            throw new J7DeliveryException(
                    J7DeliveryError.PROVIDER_OWNER_GO_NOT_AVAILABLE);
        }
        J7ProviderDerivedOwnerGo.Grant grant = snapshot.grant();
        policy.requireProviderOwnerGoGrantConfiguration(grant);
        requireGrantMatches(
                grant, configuredReference, receipt, artifact, configuration);

        J7DeliveryLedgerStore.ClaimReceipt claim = ledgerStore.claimProviderDerived(
                new J7ProviderDerivedOwnerGo.Claim(
                        grant, identity.idempotencyKey(), now()));

        OwnedJ7DeliveryTransport transport;
        try {
            transport = Objects.requireNonNull(
                    transportFactory.open(configuration),
                    "transportFactory.open(configuration)");
        }
        catch (RuntimeException exception) {
            return completePostClaimConfigurationFailure(claim, exception, lease);
        }
        return executeWithOwnedTransport(
                artifact, claim, transport, lease);
    }

    private J7DeliveryExecutionResult openAndExecuteSynthetic(
            J7ValidatedExportArtifact artifact,
            String confirmation,
            J7DeliveryPayloadClass payloadClass,
            int expectedAttemptNumber,
            J7DeliveryTransportFactory.Configuration configuration,
            J7DeliveryExecutionGate.Lease lease) {

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

    private J7DeliveryExecutionResult executeWithOwnedTransport(
            J7ValidatedExportArtifact artifact,
            J7DeliveryLedgerStore.ClaimReceipt claim,
            OwnedJ7DeliveryTransport transport,
            J7DeliveryExecutionGate.Lease lease) {
        RuntimeException primaryFailure = null;
        try {
            J7OptionalDeliveryService executor = new J7OptionalDeliveryService(
                    exportService,
                    ledgerStore,
                    transport,
                    acknowledgementParser,
                    policy,
                    clock);
            return executor.executeAlreadyClaimed(artifact, claim);
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

    private J7DeliveryExecutionResult completePostClaimConfigurationFailure(
            J7DeliveryLedgerStore.ClaimReceipt claim,
            RuntimeException configurationFailure,
            J7DeliveryExecutionGate.Lease lease) {
        try {
            J7DeliveryLedgerStore.DeliverySnapshot snapshot = ledgerStore.complete(
                    claim.deliveryId(),
                    claim.attemptNumber(),
                    J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                    OptionalInt.empty(),
                    "TRANSPORT_CONFIGURATION_FAILED",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    now());
            return new J7DeliveryExecutionResult(
                    snapshot.deliveryId(),
                    claim.attemptNumber(),
                    J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                    OptionalInt.empty(),
                    "TRANSPORT_CONFIGURATION_FAILED");
        }
        catch (RuntimeException completionFailure) {
            lease.poison();
            J7DeliveryException failure = new J7DeliveryException(
                    J7DeliveryError.TRANSPORT_CONFIGURATION_FAILED);
            failure.addSuppressed(configurationFailure);
            failure.addSuppressed(completionFailure);
            throw failure;
        }
    }

    private static void requireReceiptMatchesCandidate(
            J7DeliveryConfirmationReceipt receipt,
            J7DeliveryCandidate candidate) {
        if (!receipt.exportId().equals(candidate.exportId())
                || !receipt.canonicalEventId().equals(candidate.canonicalEventId())
                || !receipt.fileSha256().equals(candidate.fileSha256())) {
            throw new J7DeliveryException(J7DeliveryError.PAYLOAD_IDENTITY_CHANGED);
        }
    }

    private static void requireGrantMatches(
            J7ProviderDerivedOwnerGo.Grant grant,
            J7ProviderDerivedOwnerGo.Reference reference,
            J7DeliveryConfirmationReceipt receipt,
            J7ValidatedExportArtifact artifact,
            J7DeliveryTransportFactory.Configuration configuration) {
        if (!grant.reference().equals(reference)
                || !grant.canonicalEventId().equals(artifact.canonicalEventId())
                || !grant.exportId().equals(artifact.exportId())
                || !grant.fileSha256().equals(artifact.fileSha256())
                || !grant.dataSha256().equals(artifact.dataSha256())
                || grant.fileSizeBytes() != artifact.sizeBytes()
                || !grant.schemaId().equals(artifact.schemaId())
                || !grant.schemaVersion().equals(artifact.schemaVersion())
                || !grant.receiverOrigin().equals(configuration.receiverOrigin())
                || !grant.clientCertificateSha256()
                .equals(configuration.clientCertificateSha256())
                || grant.expectedAttemptNumber() != receipt.attemptNumber()) {
            throw new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_MISMATCH);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
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
