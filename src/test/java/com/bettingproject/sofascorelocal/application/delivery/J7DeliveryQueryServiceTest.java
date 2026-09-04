package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J7DeliveryQueryServiceTest {

    private static final UUID EVENT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID DELIVERY_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final String FILE_SHA256 = "a".repeat(64);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");

    private J7DeliveryLedgerStore ledgerStore;
    private J7DeliveryPolicy policy;
    private J7DeliveryQueryService service;

    @BeforeEach
    void setUp() {
        ledgerStore = mock(J7DeliveryLedgerStore.class);
        policy = mock(J7DeliveryPolicy.class);
        service = new J7DeliveryQueryService(ledgerStore, policy);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.SYNTHETIC_ONLY))
                .thenReturn(List.of());
    }

    @Test
    void nonValidatedExportIsBlockedWithoutReadingTheDeliveryLedger() {
        J7ExportPreview preview = preview(J7ExportStatus.COHERENCE_CHECKED);

        J7DeliveryView view = service.view(preview);

        assertThat(view.policyBlockers())
                .containsExactly("EXPORT_NOT_HUMAN_VALIDATED");
        assertThat(view.preparationAllowed()).isFalse();
        assertThat(view.reconciliationAvailable()).isFalse();
        assertThat(view.unknownOutcomeReconciliationRequired()).isFalse();
        assertThat(view.state()).isEqualTo("NOT_ATTEMPTED");
        assertThat(view.attemptCount()).isZero();
        verify(ledgerStore, never()).find(EXPORT_ID, FILE_SHA256);
    }

    @Test
    void validatedSyntheticExportWithoutLedgerIsEligibleForPreparation() {
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256)).thenReturn(Optional.empty());

        J7DeliveryView view = service.view(preview(J7ExportStatus.HUMAN_VALIDATED));

        assertThat(view.policyBlockers()).isEmpty();
        assertThat(view.preparationAllowed()).isTrue();
        assertThat(view.reconciliationAvailable()).isFalse();
        assertThat(view.unknownOutcomeReconciliationRequired()).isFalse();
        assertThat(view.state()).isEqualTo("NOT_ATTEMPTED");
    }

    @Test
    void inFlightAttemptRequiresTheDedicatedStaleReconciliationPath() {
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.of(snapshot(
                        J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT, 1)));

        J7DeliveryView view = service.view(preview(J7ExportStatus.HUMAN_VALIDATED));

        assertThat(view.preparationAllowed()).isFalse();
        assertThat(view.reconciliationAvailable()).isTrue();
        assertThat(view.unknownOutcomeReconciliationRequired()).isFalse();
        assertThat(view.state()).isEqualTo("IN_FLIGHT");
        assertThat(view.attemptCount()).isEqualTo(1);
    }

    @Test
    void unknownOutcomeIsVisiblySeparatedFromOrdinaryPreparation() {
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.of(snapshot(
                        J7DeliveryLedgerStore.DeliveryState
                                .UNKNOWN_RECONCILIATION_REQUIRED,
                        2)));

        J7DeliveryView view = service.view(preview(J7ExportStatus.HUMAN_VALIDATED));

        assertThat(view.preparationAllowed()).isTrue();
        assertThat(view.reconciliationAvailable()).isFalse();
        assertThat(view.unknownOutcomeReconciliationRequired()).isTrue();
        assertThat(view.state()).isEqualTo("UNKNOWN_RECONCILIATION_REQUIRED");
        assertThat(view.attemptCount()).isEqualTo(2);
    }

    @Test
    void terminalDeliveryAndRuntimeBlockersBothPreventPreparation() {
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.SYNTHETIC_ONLY))
                .thenReturn(List.of("SENDER_NOT_QUALIFIED"));
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.of(snapshot(
                        J7DeliveryLedgerStore.DeliveryState.DELIVERED, 1)));

        J7DeliveryView view = service.view(preview(J7ExportStatus.HUMAN_VALIDATED));

        assertThat(view.policyBlockers()).containsExactly("SENDER_NOT_QUALIFIED");
        assertThat(view.preparationAllowed()).isFalse();
        assertThat(view.reconciliationAvailable()).isFalse();
        assertThat(view.unknownOutcomeReconciliationRequired()).isFalse();
    }

    private static J7ExportPreview preview(J7ExportStatus status) {
        Optional<Instant> decidedAt = status == J7ExportStatus.COHERENCE_CHECKED
                ? Optional.empty()
                : Optional.of(NOW);
        J7ExportManifest manifest = new J7ExportManifest(
                1,
                EXPORT_ID,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                NOW,
                DATA_SHA256,
                "c".repeat(64),
                "d".repeat(64),
                FILE_SHA256,
                1_024,
                "j7-synthetic.json",
                "[]",
                List.of(),
                "[]",
                status,
                decidedAt,
                Optional.empty());
        return new J7ExportPreview(
                manifest,
                "{}",
                "VALIDER",
                "REJETER",
                J7DeliveryPayloadClass.SYNTHETIC_ONLY);
    }

    private static J7DeliveryLedgerStore.DeliverySnapshot snapshot(
            J7DeliveryLedgerStore.DeliveryState state,
            int attempts) {
        return new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                1_024,
                new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256).idempotencyKey(),
                "1.0",
                state,
                attempts,
                NOW,
                NOW);
    }
}
