package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        Objects.requireNonNull(preview, "preview");
        var manifest = preview.manifest();
        var payloadClass = preview.deliveryPayloadClass();
        List<String> blockers = new ArrayList<>();
        if (manifest.status() != J7ExportStatus.HUMAN_VALIDATED) {
            blockers.add("EXPORT_NOT_HUMAN_VALIDATED");
        }
        blockers.addAll(policy.runtimeBlockers(payloadClass));
        var ledger = manifest.status() == J7ExportStatus.HUMAN_VALIDATED
                ? ledgerStore.find(manifest.exportId(), manifest.currentContentSha256())
                : java.util.Optional.<J7DeliveryLedgerStore.DeliverySnapshot>empty();
        boolean claimable = ledger.map(snapshot -> !snapshot.state().forbidsAnotherClaim())
                .orElse(true);
        boolean preparationAllowed = blockers.isEmpty() && claimable;
        boolean reconciliationAvailable = ledger
                .map(snapshot -> snapshot.state()
                        == J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT)
                .orElse(false);
        return new J7DeliveryView(
                payloadClass,
                blockers,
                ledger,
                preparationAllowed,
                reconciliationAvailable);
    }
}
