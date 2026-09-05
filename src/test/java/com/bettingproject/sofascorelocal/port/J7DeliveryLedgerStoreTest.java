package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryLedgerStoreTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "27000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000027");
    private static final String FILE_SHA = "a".repeat(64);
    private static final String DATA_SHA = "b".repeat(64);
    private static final String KEY = "j7:" + EXPORT_ID + ":sha256:" + FILE_SHA;
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void exposesExactlyTheSixAdrStatesAndOnlyUnknownAsARepeatableResult() {
        assertThat(J7DeliveryLedgerStore.DeliveryState.values())
                .containsExactly(
                        J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED,
                        J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                        J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                        J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED,
                        J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL,
                        J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED
                .forbidsAnotherClaim()).isFalse();
        assertThat(J7DeliveryLedgerStore.DeliveryState.DELIVERED.forbidsAnotherClaim()).isTrue();
        assertThat(J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED
                .forbidsAnotherClaim()).isTrue();
        assertThat(J7DeliveryLedgerStore.DeliveryState.REJECTED_TERMINAL
                .forbidsAnotherClaim()).isTrue();
    }

    @Test
    void validatesClaimAndSnapshotMetadataWithoutAcceptingPayloadOrDiagnostics() {
        var receipt = new J7DeliveryLedgerStore.ClaimReceipt(
                DELIVERY_ID,
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                KEY,
                STARTED_AT);
        var snapshot = new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                KEY,
                "1.0",
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                1,
                STARTED_AT,
                STARTED_AT);
        var notAttempted = new J7DeliveryLedgerStore.DeliverySnapshot(
                UUID.fromString("27000000-0000-4000-8000-000000000002"),
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                KEY,
                "1.0",
                J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED,
                0,
                STARTED_AT,
                STARTED_AT);

        assertThat(receipt.idempotencyKey()).isEqualTo(KEY);
        assertThat(notAttempted.attemptCount()).isZero();
        assertThat(snapshot)
                .extracting(
                        J7DeliveryLedgerStore.DeliverySnapshot::exportId,
                        J7DeliveryLedgerStore.DeliverySnapshot::fileSha256,
                        J7DeliveryLedgerStore.DeliverySnapshot::dataSha256,
                        J7DeliveryLedgerStore.DeliverySnapshot::protocolVersion,
                        J7DeliveryLedgerStore.DeliverySnapshot::attemptCount)
                .containsExactly(EXPORT_ID, FILE_SHA, DATA_SHA, "1.0", 1);
        assertThat(J7DeliveryLedgerStore.DeliverySnapshot.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("payload", "body", "diagnostic", "privateKey", "certificate");
    }

    @Test
    void rejectsMalformedIdentityAndKeepsLedgerErrorsValueFree() {
        String sensitive = "do-not-leak-this-value";

        assertThatThrownBy(() -> new J7DeliveryLedgerStore.ClaimReceipt(
                DELIVERY_ID,
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                sensitive,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(sensitive);

        var exception = new J7DeliveryLedgerStore.LedgerException(
                J7DeliveryLedgerStore.LedgerFailure.STORAGE_UNAVAILABLE);
        assertThat(exception.getMessage())
                .isEqualTo("J7_DELIVERY_LEDGER_STORAGE_UNAVAILABLE")
                .doesNotContain(sensitive);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void rejectsNonCanonicalSnapshotCorrelationAndSubMicrosecondClaimTimes() {
        assertThatThrownBy(() -> new J7DeliveryLedgerStore.ClaimReceipt(
                DELIVERY_ID,
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                KEY,
                STARTED_AT.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");

        assertThatThrownBy(() -> new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                "j7:" + EXPORT_ID + ":sha256:" + "c".repeat(64),
                "1.0",
                J7DeliveryLedgerStore.DeliveryState.NOT_ATTEMPTED,
                0,
                STARTED_AT,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match exportId and fileSha256");

        assertThatThrownBy(() -> new J7DeliveryLedgerStore.ClaimReceipt(
                new UUID(0, 0),
                1,
                J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                KEY,
                STARTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-nil RFC 4122 UUID");
    }

    @Test
    void validatesOneExactProviderDerivedOwnerGoWithoutSecretsOrPayloads() {
        J7ProviderDerivedOwnerGo.Grant grant = validGrant();
        var claim = new J7ProviderDerivedOwnerGo.Claim(grant, KEY, STARTED_AT);
        var snapshot = new J7ProviderDerivedOwnerGo.Snapshot(
                grant,
                J7ProviderDerivedOwnerGo.Status.AVAILABLE,
                STARTED_AT.minusSeconds(1),
                STARTED_AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty());

        assertThat(grant.reference()).isEqualTo(new J7ProviderDerivedOwnerGo.Reference(
                grant.goId(), grant.ownerDecisionBlockSha256()));
        assertThat(claim.idempotencyKey()).isEqualTo(KEY);
        assertThat(snapshot.status()).isEqualTo(J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        assertThat(grant.toString())
                .doesNotContain(grant.ownerDecisionBlockSha256())
                .doesNotContain(grant.clientCertificateSha256());
        assertThat(grant.reference().toString())
                .doesNotContain(grant.ownerDecisionBlockSha256());
        assertThat(claim.toString()).doesNotContain(KEY, FILE_SHA);
        assertThat(snapshot.toString()).doesNotContain(FILE_SHA, DATA_SHA);
        assertThat(J7ProviderDerivedOwnerGo.Grant.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("payload", "body", "privateKey", "certificate");
    }

    @Test
    void confinesOwnerGoReferencesAndRequiresTheThreeQualifiedGates() {
        assertThatThrownBy(() -> grantWithReferences(
                "../manifest.md", "docs/validation/permission.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confined docs/validation reference");
        assertThatThrownBy(() -> grantWithReferences(
                "docs/validation/manifest.md", "https://example.test/evidence"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confined docs/validation reference");
        assertThatThrownBy(() -> new J7ProviderDerivedOwnerGo.Grant(
                validGrant().goId(),
                validGrant().ownerDecisionBlockSha256(),
                validGrant().workOrder(),
                validGrant().campaignManifestReference(),
                validGrant().campaignManifestSha256(),
                validGrant().localLabCommit(),
                validGrant().receiverCommit(),
                validGrant().officialPermissionEvidenceReference(),
                validGrant().officialPermissionEvidenceSha256(),
                "NOT_EVIDENCED",
                "PASS",
                "PASS",
                validGrant().executionActor(),
                validGrant().canonicalEventId(),
                validGrant().providerEventId(),
                validGrant().exportId(),
                validGrant().fileSha256(),
                validGrant().dataSha256(),
                validGrant().fileSizeBytes(),
                validGrant().schemaId(),
                validGrant().schemaVersion(),
                validGrant().receiverOrigin(),
                validGrant().clientCertificateSha256(),
                1,
                1,
                validGrant().validFrom(),
                validGrant().validUntil(),
                "GRANT",
                "ONE_TIME",
                "PROVIDER_DERIVED",
                "HUMAN_VALIDATED",
                true,
                false,
                false,
                false,
                false,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("officialPermissionStatus");
    }

    private static J7ProviderDerivedOwnerGo.Grant validGrant() {
        return grantWithReferences(
                "docs/validation/J9-WO046-PROVIDER-DERIVED-DELIVERY-MANIFEST.md",
                "docs/validation/J9-OFFICIAL-PERMISSION-EVIDENCE.md");
    }

    private static J7ProviderDerivedOwnerGo.Grant grantWithReferences(
            String manifestReference,
            String permissionReference) {
        J7ProviderDerivedOwnerGo.Grant draft = new J7ProviderDerivedOwnerGo.Grant(
                UUID.fromString("45000000-0000-4000-8000-000000000001"),
                "1".repeat(64),
                "WO-SS-20260904-046-provider-derived-real-delivery",
                manifestReference,
                "2".repeat(64),
                "3".repeat(40),
                "4".repeat(40),
                permissionReference,
                "5".repeat(64),
                "EVIDENCED_COMPATIBLE",
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                UUID.fromString("45000000-0000-4000-8000-000000000002"),
                16_310_945L,
                EXPORT_ID,
                FILE_SHA,
                DATA_SHA,
                1024,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_ID,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_VERSION,
                URI.create("https://127.0.0.1:8444"),
                "6".repeat(64),
                1,
                1,
                STARTED_AT.minusSeconds(60),
                STARTED_AT.plusSeconds(60),
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
        return draft.withOwnerDecisionBlockSha256(
                draft.computedOwnerDecisionBlockSha256());
    }
}
