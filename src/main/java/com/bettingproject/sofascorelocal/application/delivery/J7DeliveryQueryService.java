package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public final class J7DeliveryQueryService {

    private final J7DeliveryLedgerStore ledgerStore;
    private final J7DeliveryPolicy policy;

    public J7DeliveryQueryService(
            J7DeliveryLedgerStore ledgerStore,
            J7DeliveryPolicy policy) {
        this.ledgerStore = Objects.requireNonNull(ledgerStore, "ledgerStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public J7DeliveryView view(J7ExportPreview preview) {
        return preparation(preview).view();
    }

    /**
     * Builds the public view and, only when eligible, selects the exact durable provider go.
     */
    public J7DeliveryPreparation preparation(J7ExportPreview preview) {
        Objects.requireNonNull(preview, "preview");
        var manifest = preview.manifest();
        var payloadClass = preview.deliveryPayloadClass();
        List<String> blockers = new ArrayList<>();
        if (manifest.status() != J7ExportStatus.HUMAN_VALIDATED) {
            blockers.add("EXPORT_NOT_HUMAN_VALIDATED");
        }
        blockers.addAll(policy.runtimeBlockers(payloadClass));
        Optional<J7DeliveryLedgerStore.DeliverySnapshot> ledger =
                manifest.status() == J7ExportStatus.HUMAN_VALIDATED
                        ? ledgerStore.find(
                                manifest.exportId(),
                                manifest.currentContentSha256())
                        : Optional.empty();
        boolean claimable = ledger.map(snapshot -> !snapshot.state().forbidsAnotherClaim())
                .orElse(true);
        Optional<J7ProviderDerivedOwnerGo.Reference> availableProviderGo = Optional.empty();
        if (manifest.status() == J7ExportStatus.HUMAN_VALIDATED
                && blockers.isEmpty()
                && claimable
                && payloadClass == J7DeliveryPayloadClass.PROVIDER_DERIVED
                && firstAttemptAvailable(ledger)) {
            J7ProviderDerivedOwnerGo.Reference configuredReference =
                    policy.providerOwnerGoReference();
            Optional<J7ProviderDerivedOwnerGo.Snapshot> snapshot =
                    ledgerStore.findProviderDerivedOwnerGo(configuredReference);
            if (snapshot.isEmpty()) {
                blockers.add("PROVIDER_OWNER_GO_NOT_REGISTERED");
            }
            else if (snapshot.orElseThrow().status()
                    != J7ProviderDerivedOwnerGo.Status.AVAILABLE) {
                blockers.add("PROVIDER_OWNER_GO_"
                        + snapshot.orElseThrow().status().name());
            }
            else if (!matchesManifest(
                    snapshot.orElseThrow().grant(),
                    configuredReference,
                    manifest)) {
                blockers.add("PROVIDER_OWNER_GO_IDENTITY_MISMATCH");
            }
            else {
                try {
                    policy.requireProviderOwnerGoGrantConfiguration(
                            snapshot.orElseThrow().grant());
                    availableProviderGo = Optional.of(configuredReference);
                }
                catch (J7DeliveryException exception) {
                    blockers.add("PROVIDER_OWNER_GO_CONFIGURATION_MISMATCH");
                }
            }
        }
        else if (manifest.status() == J7ExportStatus.HUMAN_VALIDATED
                && blockers.isEmpty()
                && claimable
                && payloadClass == J7DeliveryPayloadClass.PROVIDER_DERIVED) {
            blockers.add("PROVIDER_OWNER_GO_ATTEMPT_NOT_FIRST");
        }
        boolean preparationAllowed = blockers.isEmpty() && claimable;
        boolean reconciliationAvailable = ledger
                .map(snapshot -> snapshot.state()
                        == J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT)
                .orElse(false);
        J7DeliveryView view = new J7DeliveryView(
                payloadClass,
                blockers,
                ledger,
                preparationAllowed,
                reconciliationAvailable);
        return new J7DeliveryPreparation(view, availableProviderGo);
    }

    private static boolean firstAttemptAvailable(
            Optional<J7DeliveryLedgerStore.DeliverySnapshot> ledger) {
        return ledger.isEmpty()
                || ledger.filter(snapshot -> snapshot.state()
                                == J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED)
                        .filter(snapshot -> snapshot.attemptCount() == 0)
                        .isPresent();
    }

    private static boolean matchesManifest(
            J7ProviderDerivedOwnerGo.Grant grant,
            J7ProviderDerivedOwnerGo.Reference configuredReference,
            J7ExportManifest manifest) {
        return grant.reference().equals(configuredReference)
                && grant.canonicalEventId().equals(manifest.canonicalEventId())
                && grant.exportId().equals(manifest.exportId())
                && grant.fileSha256().equals(manifest.currentContentSha256())
                && grant.dataSha256().equals(manifest.dataSha256())
                && grant.fileSizeBytes() == manifest.currentSizeBytes()
                && grant.schemaId().equals(manifest.schemaId())
                && grant.schemaVersion().equals(manifest.schemaVersion())
                && grant.expectedAttemptNumber() == 1;
    }
}
