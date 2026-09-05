package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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
    private static final J7ProviderDerivedOwnerGo.Reference OWNER_GO_REFERENCE =
            new J7ProviderDerivedOwnerGo.Reference(
                    UUID.fromString("45000000-0000-4000-8000-000000000045"),
                    "e".repeat(64));

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

    @Test
    void evidencedIncompatibleProviderBlockerIsVisibleAndPreventsOwnerGoLookup() {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of("OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE"));

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers())
                .containsExactly("OFFICIAL_PERMISSION_EVIDENCED_INCOMPATIBLE");
        assertThat(preparation.view().preparationAllowed()).isFalse();
        assertThat(preparation.providerOwnerGoReference()).isEmpty();
        verify(policy, never()).providerOwnerGoReference();
        verify(ledgerStore, never()).findProviderDerivedOwnerGo(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onlyTheExactConfiguredAvailableOwnerGoIsBoundToPreparation() {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(policy.providerOwnerGoReference()).thenReturn(OWNER_GO_REFERENCE);
        when(ledgerStore.findProviderDerivedOwnerGo(OWNER_GO_REFERENCE))
                .thenReturn(Optional.of(ownerGoSnapshot(
                        J7ProviderDerivedOwnerGo.Status.AVAILABLE)));
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256)).thenReturn(Optional.empty());

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers()).isEmpty();
        assertThat(preparation.view().preparationAllowed()).isTrue();
        assertThat(preparation.providerOwnerGoReference())
                .contains(OWNER_GO_REFERENCE);
        assertThat(preparation.toString())
                .doesNotContain(OWNER_GO_REFERENCE.goId().toString())
                .doesNotContain(OWNER_GO_REFERENCE.ownerDecisionBlockSha256());
        verify(ledgerStore).findProviderDerivedOwnerGo(OWNER_GO_REFERENCE);
        verify(policy).requireProviderOwnerGoGrantConfiguration(
                ownerGoSnapshot(J7ProviderDerivedOwnerGo.Status.AVAILABLE).grant());
    }

    @Test
    void certificateMismatchInAvailableGoBlocksPreparationBeforeConfirmation() {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        J7ProviderDerivedOwnerGo.Snapshot snapshot = ownerGoSnapshot(
                J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(policy.providerOwnerGoReference()).thenReturn(OWNER_GO_REFERENCE);
        when(ledgerStore.findProviderDerivedOwnerGo(OWNER_GO_REFERENCE))
                .thenReturn(Optional.of(snapshot));
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256)).thenReturn(Optional.empty());
        doThrow(new J7DeliveryException(J7DeliveryError.PROVIDER_OWNER_GO_MISMATCH))
                .when(policy).requireProviderOwnerGoGrantConfiguration(snapshot.grant());

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers())
                .containsExactly("PROVIDER_OWNER_GO_CONFIGURATION_MISMATCH");
        assertThat(preparation.view().preparationAllowed()).isFalse();
        assertThat(preparation.providerOwnerGoReference()).isEmpty();
    }

    @Test
    void anUnregisteredConfiguredOwnerGoFailsClosedWithoutSelectingAnother() {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(policy.providerOwnerGoReference()).thenReturn(OWNER_GO_REFERENCE);
        when(ledgerStore.findProviderDerivedOwnerGo(OWNER_GO_REFERENCE))
                .thenReturn(Optional.empty());

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers())
                .containsExactly("PROVIDER_OWNER_GO_NOT_REGISTERED");
        assertThat(preparation.view().preparationAllowed()).isFalse();
        assertThat(preparation.providerOwnerGoReference()).isEmpty();
        verify(ledgerStore).findProviderDerivedOwnerGo(OWNER_GO_REFERENCE);
    }

    @Test
    void anAvailableGoForAnotherExportCannotBeSubstituted() {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(policy.providerOwnerGoReference()).thenReturn(OWNER_GO_REFERENCE);
        J7ProviderDerivedOwnerGo.Snapshot exact = ownerGoSnapshot(
                J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        J7ProviderDerivedOwnerGo.Grant grant = exact.grant();
        J7ProviderDerivedOwnerGo.Grant anotherExport = new J7ProviderDerivedOwnerGo.Grant(
                grant.goId(),
                grant.ownerDecisionBlockSha256(),
                grant.workOrder(),
                grant.campaignManifestReference(),
                grant.campaignManifestSha256(),
                grant.localLabCommit(),
                grant.receiverCommit(),
                grant.officialPermissionEvidenceReference(),
                grant.officialPermissionEvidenceSha256(),
                grant.officialPermissionStatus(),
                grant.receiverQualification(),
                grant.senderQualification(),
                grant.executionActor(),
                grant.canonicalEventId(),
                grant.providerEventId(),
                UUID.fromString("20000000-0000-4000-8000-000000000003"),
                "f".repeat(64),
                grant.dataSha256(),
                grant.fileSizeBytes(),
                grant.schemaId(),
                grant.schemaVersion(),
                grant.receiverOrigin(),
                grant.clientCertificateSha256(),
                grant.expectedAttemptNumber(),
                grant.maximumDirectImportCalls(),
                grant.validFrom(),
                grant.validUntil(),
                grant.ownerDecision(),
                grant.goUse(),
                grant.payloadClass(),
                grant.validationStatus(),
                grant.providerDerivedRealPostAuthorized(),
                grant.providerNetworkAuthorized(),
                grant.remoteReceiverNetworkAuthorized(),
                grant.vpsDeploymentAuthorized(),
                grant.productionAuthorized(),
                grant.automaticRetryAuthorized());
        when(ledgerStore.findProviderDerivedOwnerGo(OWNER_GO_REFERENCE))
                .thenReturn(Optional.of(new J7ProviderDerivedOwnerGo.Snapshot(
                        anotherExport,
                        J7ProviderDerivedOwnerGo.Status.AVAILABLE,
                        exact.registeredAt(),
                        exact.observedAt(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        OptionalInt.empty())));

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers())
                .containsExactly("PROVIDER_OWNER_GO_IDENTITY_MISMATCH");
        assertThat(preparation.view().preparationAllowed()).isFalse();
        assertThat(preparation.providerOwnerGoReference()).isEmpty();
    }

    @Test
    void aNonFirstProviderAttemptSkipsOwnerGoLookup() {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256)).thenReturn(Optional.of(
                snapshot(
                        J7DeliveryLedgerStore.DeliveryState
                                .UNKNOWN_RECONCILIATION_REQUIRED,
                        1)));

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers())
                .containsExactly("PROVIDER_OWNER_GO_ATTEMPT_NOT_FIRST");
        assertThat(preparation.view().preparationAllowed()).isFalse();
        verify(policy, never()).providerOwnerGoReference();
        verify(ledgerStore, never()).findProviderDerivedOwnerGo(
                org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @EnumSource(value = J7DeliveryLedgerStore.DeliveryState.class, names = {
            "IN_FLIGHT", "DELIVERED", "DUPLICATE_CONFIRMED", "REJECTED_TERMINAL"
    })
    void aNonClaimableProviderDeliverySkipsOwnerGoLookup(
            J7DeliveryLedgerStore.DeliveryState state) {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(ledgerStore.find(EXPORT_ID, FILE_SHA256))
                .thenReturn(Optional.of(snapshot(state, 1)));

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().preparationAllowed()).isFalse();
        assertThat(preparation.providerOwnerGoReference()).isEmpty();
        verify(policy, never()).providerOwnerGoReference();
        verify(ledgerStore, never()).findProviderDerivedOwnerGo(
                org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @EnumSource(value = J7ProviderDerivedOwnerGo.Status.class, names = {
            "NOT_YET_VALID", "EXPIRED", "REVOKED", "CONSUMED"
    })
    void anUnavailableExactOwnerGoFailsClosed(
            J7ProviderDerivedOwnerGo.Status status) {
        J7ExportPreview providerPreview = preview(
                J7ExportStatus.HUMAN_VALIDATED,
                J7DeliveryPayloadClass.PROVIDER_DERIVED);
        when(policy.runtimeBlockers(J7DeliveryPayloadClass.PROVIDER_DERIVED))
                .thenReturn(List.of());
        when(policy.providerOwnerGoReference()).thenReturn(OWNER_GO_REFERENCE);
        when(ledgerStore.findProviderDerivedOwnerGo(OWNER_GO_REFERENCE))
                .thenReturn(Optional.of(ownerGoSnapshot(status)));

        J7DeliveryPreparation preparation = service.preparation(providerPreview);

        assertThat(preparation.view().policyBlockers())
                .containsExactly("PROVIDER_OWNER_GO_" + status.name());
        assertThat(preparation.view().preparationAllowed()).isFalse();
        assertThat(preparation.providerOwnerGoReference()).isEmpty();
    }

    private static J7ExportPreview preview(J7ExportStatus status) {
        return preview(status, J7DeliveryPayloadClass.SYNTHETIC_ONLY);
    }

    private static J7ExportPreview preview(
            J7ExportStatus status,
            J7DeliveryPayloadClass payloadClass) {
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
                payloadClass);
    }

    private static J7ProviderDerivedOwnerGo.Snapshot ownerGoSnapshot(
            J7ProviderDerivedOwnerGo.Status status) {
        J7ProviderDerivedOwnerGo.Grant grant = new J7ProviderDerivedOwnerGo.Grant(
                OWNER_GO_REFERENCE.goId(),
                OWNER_GO_REFERENCE.ownerDecisionBlockSha256(),
                "WO-SS-20260904-046-provider-derived-real-j7-delivery",
                "docs/validation/WO046-manifest.md",
                "1".repeat(64),
                "2".repeat(40),
                "3".repeat(40),
                "docs/validation/WO046-official-permission-response.md",
                "4".repeat(64),
                "EVIDENCED_COMPATIBLE",
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                EVENT_ID,
                16_310_945L,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                1_024,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_ID,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_VERSION,
                URI.create("https://127.0.0.1:8444"),
                "5".repeat(64),
                1,
                1,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                "GRANT",
                "ONE_TIME",
                "PROVIDER_DERIVED",
                "HUMAN_VALIDATED",
                true,
                false,
                false,
                false,
                false,
                false);
        boolean revoked = status == J7ProviderDerivedOwnerGo.Status.REVOKED;
        boolean consumed = status == J7ProviderDerivedOwnerGo.Status.CONSUMED;
        return new J7ProviderDerivedOwnerGo.Snapshot(
                grant,
                status,
                NOW.minusSeconds(120),
                NOW,
                revoked ? Optional.of("6".repeat(64)) : Optional.empty(),
                revoked ? Optional.of(NOW.minusSeconds(30)) : Optional.empty(),
                consumed ? Optional.of(NOW.minusSeconds(30)) : Optional.empty(),
                consumed ? Optional.of(DELIVERY_ID) : Optional.empty(),
                consumed ? OptionalInt.of(1) : OptionalInt.empty());
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
