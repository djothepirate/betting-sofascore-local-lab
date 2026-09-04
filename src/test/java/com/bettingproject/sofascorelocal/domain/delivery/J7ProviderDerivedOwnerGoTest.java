package com.bettingproject.sofascorelocal.domain.delivery;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7ProviderDerivedOwnerGoTest {

    private static final UUID GO_ID = UUID.fromString(
            "45000000-0000-4000-8000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString(
            "45000000-0000-4000-8000-000000000002");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000027");
    private static final Instant VALID_FROM = Instant.parse("2026-09-01T09:59:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2026-09-01T10:01:00Z");

    @Test
    void legacyV1ConstructorAndCanonicalBlockRemainStrictAndByteIdentical() {
        J7ProviderDerivedOwnerGo.Grant draft = v1Draft("EVIDENCED_COMPATIBLE");

        assertThat(draft.format()).isEqualTo(J7ProviderDerivedOwnerGo.FORMAT_V1);
        assertThat(draft.permissionAuditStatus()).isEqualTo("EVIDENCED_COMPATIBLE");
        assertThat(draft.providerPermissionAuditReference()).isNull();
        assertThat(draft.j7TransferGovernanceBasisReference()).isNull();
        assertThat(draft.canonicalDecisionBlock()).isEqualTo(v1CanonicalBlock());
        assertThat(draft.computedOwnerDecisionBlockSha256())
                .isEqualTo("ef400eb26605b7ab447150d9b3db083094f25cde0a69cf3c5a46b943ce3c23ee");
        assertThat(draft.withOwnerDecisionBlockSha256(
                draft.computedOwnerDecisionBlockSha256())
                .hasValidOwnerDecisionBlockSha256()).isTrue();

        assertThatThrownBy(() -> v1Draft("NOT_EVIDENCED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("officialPermissionStatus");
    }

    @Test
    void v2CanonicalBlockSeparatesProviderAuditFromTransferGovernance() {
        J7ProviderDerivedOwnerGo.Grant draft = v2Draft(
                "NOT_EVIDENCED",
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS);

        assertThat(draft.format()).isEqualTo(J7ProviderDerivedOwnerGo.FORMAT_V2);
        assertThat(draft.permissionAuditStatus()).isEqualTo("NOT_EVIDENCED");
        assertThat(draft.officialPermissionEvidenceReference()).isNull();
        assertThat(draft.officialPermissionStatus()).isNull();
        assertThat(draft.canonicalDecisionBlock())
                .isEqualTo(v2CanonicalBlock())
                .doesNotContain(
                        "OFFICIAL_PERMISSION_EVIDENCE_",
                        "OFFICIAL_PERMISSION_STATUS=");
        assertThat(draft.computedOwnerDecisionBlockSha256())
                .isEqualTo("09e71b91da149f38dcd671568c88d3f2522049b37e1aaf222b468538cc588414");

        J7ProviderDerivedOwnerGo.Grant exact = draft.withOwnerDecisionBlockSha256(
                draft.computedOwnerDecisionBlockSha256());
        assertThat(exact.hasValidOwnerDecisionBlockSha256()).isTrue();
        assertThat(exact.format()).isEqualTo(J7ProviderDerivedOwnerGo.FORMAT_V2);
        assertThat(exact.j7TransferGovernanceBasisCommit())
                .isEqualTo("e1ec9936467dd570f7ed00c51227c8e7d5a35945");
    }

    @Test
    void v2AllowsOnlyKnownNonVetoAuditStatuses() {
        assertThat(v2Draft(
                "EVIDENCED_COMPATIBLE",
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS)
                .permissionAuditStatus()).isEqualTo("EVIDENCED_COMPATIBLE");

        for (String refused : new String[]{"EVIDENCED_INCOMPATIBLE", "UNKNOWN", ""}) {
            assertThatThrownBy(() -> v2Draft(
                    refused,
                    J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                    J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                    J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                    J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerPermissionAuditStatus");
        }
        assertThatThrownBy(() -> v2Draft(
                null,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerPermissionAuditStatus");
    }

    @Test
    void v2RequiresTheExactAcceptedNoExecutionAuthorityGovernanceBasis() {
        assertGovernanceRefused(
                "docs/validation/not-the-adr.md",
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS,
                "j7TransferGovernanceBasisReference");
        assertGovernanceRefused(
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                "f".repeat(40),
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS,
                "j7TransferGovernanceBasisCommit");
        assertGovernanceRefused(
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                "f".repeat(64),
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS,
                "j7TransferGovernanceBasisSha256");
        assertGovernanceRefused(
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                J7ProviderDerivedOwnerGo.EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                "ADR_ACCEPTED_AND_EXECUTION_AUTHORIZED",
                "j7TransferGovernanceBasisStatus");
    }

    private static void assertGovernanceRefused(
            String reference,
            String commit,
            String sha256,
            String status,
            String expectedField) {
        assertThatThrownBy(() -> v2Draft(
                "NOT_EVIDENCED", reference, commit, sha256, status))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedField);
    }

    private static J7ProviderDerivedOwnerGo.Grant v1Draft(String permissionStatus) {
        return new J7ProviderDerivedOwnerGo.Grant(
                GO_ID,
                "1".repeat(64),
                "WO-SS-20260904-046-provider-derived-real-delivery",
                "docs/validation/J9-WO046-PROVIDER-DERIVED-DELIVERY-MANIFEST.md",
                "2".repeat(64),
                "3".repeat(40),
                "4".repeat(40),
                "docs/validation/J9-OFFICIAL-PERMISSION-EVIDENCE.md",
                "5".repeat(64),
                permissionStatus,
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                EVENT_ID,
                16_310_945L,
                EXPORT_ID,
                "a".repeat(64),
                "b".repeat(64),
                1_024,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_ID,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_VERSION,
                URI.create("https://127.0.0.1:8444"),
                "6".repeat(64),
                1,
                1,
                VALID_FROM,
                VALID_UNTIL,
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
    }

    private static J7ProviderDerivedOwnerGo.Grant v2Draft(
            String auditStatus,
            String governanceReference,
            String governanceCommit,
            String governanceSha256,
            String governanceStatus) {
        return J7ProviderDerivedOwnerGo.Grant.v2(
                GO_ID,
                "1".repeat(64),
                "WO-SS-20260904-046-provider-derived-real-delivery",
                "docs/validation/J9-WO046-PROVIDER-DERIVED-DELIVERY-MANIFEST.md",
                "2".repeat(64),
                "3".repeat(40),
                "4".repeat(40),
                "docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md",
                "707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473",
                auditStatus,
                governanceReference,
                governanceCommit,
                governanceSha256,
                governanceStatus,
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                EVENT_ID,
                16_310_945L,
                EXPORT_ID,
                "a".repeat(64),
                "b".repeat(64),
                1_024,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_ID,
                J7ProviderDerivedOwnerGo.EXPECTED_SCHEMA_VERSION,
                URI.create("https://127.0.0.1:8444"),
                "6".repeat(64),
                1,
                1,
                VALID_FROM,
                VALID_UNTIL,
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
    }

    private static String v1CanonicalBlock() {
        return """
                FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V1
                GO_ID=45000000-0000-4000-8000-000000000001
                WORK_ORDER=WO-SS-20260904-046-provider-derived-real-delivery
                CAMPAIGN_MANIFEST_REFERENCE=docs/validation/J9-WO046-PROVIDER-DERIVED-DELIVERY-MANIFEST.md
                CAMPAIGN_MANIFEST_SHA256=2222222222222222222222222222222222222222222222222222222222222222
                LOCAL_LAB_COMMIT=3333333333333333333333333333333333333333
                RECEIVER_COMMIT=4444444444444444444444444444444444444444
                OFFICIAL_PERMISSION_EVIDENCE_REFERENCE=docs/validation/J9-OFFICIAL-PERMISSION-EVIDENCE.md
                OFFICIAL_PERMISSION_EVIDENCE_SHA256=5555555555555555555555555555555555555555555555555555555555555555
                OFFICIAL_PERMISSION_STATUS=EVIDENCED_COMPATIBLE
                RECEIVER_QUALIFICATION=PASS
                SENDER_QUALIFICATION=PASS
                EXECUTION_ACTOR=CODEX_LOCAL_UI
                CANONICAL_EVENT_ID=45000000-0000-4000-8000-000000000002
                PROVIDER_EVENT_ID=16310945
                EXPORT_ID=70000000-0000-4000-8000-000000000027
                FILE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                DATA_SHA256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                FILE_SIZE_BYTES=1024
                SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
                SCHEMA_VERSION=1.0.0
                RECEIVER_ORIGIN=https://127.0.0.1:8444
                CLIENT_CERTIFICATE_SHA256=6666666666666666666666666666666666666666666666666666666666666666
                EXPECTED_ATTEMPT_NUMBER=1
                MAXIMUM_DIRECT_IMPORT_CALLS=1
                VALID_FROM=2026-09-01T09:59:00.000000Z
                VALID_UNTIL=2026-09-01T10:01:00.000000Z
                OWNER_DECISION=GRANT
                GO_USE=ONE_TIME
                PAYLOAD_CLASS=PROVIDER_DERIVED
                VALIDATION_STATUS=HUMAN_VALIDATED
                PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES
                PROVIDER_NETWORK_AUTHORIZED=NO
                REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
                VPS_DEPLOYMENT_AUTHORIZED=NO
                PRODUCTION_AUTHORIZED=NO
                AUTOMATIC_RETRY_AUTHORIZED=NO
                """;
    }

    private static String v2CanonicalBlock() {
        return """
                FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
                GO_ID=45000000-0000-4000-8000-000000000001
                WORK_ORDER=WO-SS-20260904-046-provider-derived-real-delivery
                CAMPAIGN_MANIFEST_REFERENCE=docs/validation/J9-WO046-PROVIDER-DERIVED-DELIVERY-MANIFEST.md
                CAMPAIGN_MANIFEST_SHA256=2222222222222222222222222222222222222222222222222222222222222222
                LOCAL_LAB_COMMIT=3333333333333333333333333333333333333333
                RECEIVER_COMMIT=4444444444444444444444444444444444444444
                PROVIDER_PERMISSION_AUDIT_REFERENCE=docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md
                PROVIDER_PERMISSION_AUDIT_SHA256=707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473
                PROVIDER_PERMISSION_AUDIT_STATUS=NOT_EVIDENCED
                J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE=ADR-SS-003-optional-integration-topology.md
                J7_TRANSFER_GOVERNANCE_BASIS_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
                J7_TRANSFER_GOVERNANCE_BASIS_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
                J7_TRANSFER_GOVERNANCE_BASIS_STATUS=ADR_ACCEPTED_NO_EXECUTION_AUTHORITY
                RECEIVER_QUALIFICATION=PASS
                SENDER_QUALIFICATION=PASS
                EXECUTION_ACTOR=CODEX_LOCAL_UI
                CANONICAL_EVENT_ID=45000000-0000-4000-8000-000000000002
                PROVIDER_EVENT_ID=16310945
                EXPORT_ID=70000000-0000-4000-8000-000000000027
                FILE_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                DATA_SHA256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                FILE_SIZE_BYTES=1024
                SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
                SCHEMA_VERSION=1.0.0
                RECEIVER_ORIGIN=https://127.0.0.1:8444
                CLIENT_CERTIFICATE_SHA256=6666666666666666666666666666666666666666666666666666666666666666
                EXPECTED_ATTEMPT_NUMBER=1
                MAXIMUM_DIRECT_IMPORT_CALLS=1
                VALID_FROM=2026-09-01T09:59:00.000000Z
                VALID_UNTIL=2026-09-01T10:01:00.000000Z
                OWNER_DECISION=GRANT
                GO_USE=ONE_TIME
                PAYLOAD_CLASS=PROVIDER_DERIVED
                VALIDATION_STATUS=HUMAN_VALIDATED
                PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES
                PROVIDER_NETWORK_AUTHORIZED=NO
                REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
                VPS_DEPLOYMENT_AUTHORIZED=NO
                PRODUCTION_AUTHORIZED=NO
                AUTOMATIC_RETRY_AUTHORIZED=NO
                """;
    }
}
