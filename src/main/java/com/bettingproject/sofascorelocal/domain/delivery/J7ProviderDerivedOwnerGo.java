package com.bettingproject.sofascorelocal.domain.delivery;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Exact, value-safe representation of one owner authorization for one provider-derived J7 POST.
 *
 * <p>The grant contains only hashes and correlation metadata. It never contains export bytes,
 * certificate material, credentials or an acknowledgement body. Persistence is authoritative for
 * registration, revocation, consumption and window evaluation.</p>
 */
public final class J7ProviderDerivedOwnerGo {

    public static final Duration MAXIMUM_WINDOW = Duration.ofMinutes(60);
    public static final String FORMAT_V1 = "J7_PROVIDER_DERIVED_OWNER_GO_V1";
    public static final String FORMAT_V2 = "J7_PROVIDER_DERIVED_OWNER_GO_V2";
    public static final String EXPECTED_WORK_ORDER_PREFIX = "WO-SS-20260904-046-";
    public static final String EXPECTED_SCHEMA_ID =
            "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1";
    public static final String EXPECTED_SCHEMA_VERSION = "1.0.0";
    public static final URI EXPECTED_RECEIVER_ORIGIN = URI.create("https://127.0.0.1:8444");
    public static final String EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE =
            "ADR-SS-003-optional-integration-topology.md";
    public static final String EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT =
            "e1ec9936467dd570f7ed00c51227c8e7d5a35945";
    public static final String EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256 =
            "ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f";
    public static final String EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS =
            "ADR_ACCEPTED_NO_EXECUTION_AUTHORITY";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SAFE_REFERENCE = Pattern.compile(
            "docs/validation/[A-Za-z0-9][A-Za-z0-9._/-]{0,495}");
    private static final Pattern WORK_ORDER = Pattern.compile(
            "WO-SS-20260904-046-[a-z0-9-]{1,160}");
    private static final UUID NIL_UUID = new UUID(0, 0);
    private static final DateTimeFormatter OWNER_GO_INSTANT =
            new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    private J7ProviderDerivedOwnerGo() {
    }

    public record Reference(UUID goId, String ownerDecisionBlockSha256) {

        public Reference {
            goId = requireUuid(goId, "goId");
            ownerDecisionBlockSha256 = requireSha(ownerDecisionBlockSha256,
                    "ownerDecisionBlockSha256");
        }

        @Override
        public String toString() {
            return "Reference[goId=" + goId + ",ownerDecisionBlockSha256=REDACTED]";
        }
    }

    public record Grant(
            UUID goId,
            String ownerDecisionBlockSha256,
            String workOrder,
            String campaignManifestReference,
            String campaignManifestSha256,
            String localLabCommit,
            String receiverCommit,
            String officialPermissionEvidenceReference,
            String officialPermissionEvidenceSha256,
            String officialPermissionStatus,
            String receiverQualification,
            String senderQualification,
            String executionActor,
            UUID canonicalEventId,
            long providerEventId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            long fileSizeBytes,
            String schemaId,
            String schemaVersion,
            URI receiverOrigin,
            String clientCertificateSha256,
            int expectedAttemptNumber,
            int maximumDirectImportCalls,
            Instant validFrom,
            Instant validUntil,
            String ownerDecision,
            String goUse,
            String payloadClass,
            String validationStatus,
            boolean providerDerivedRealPostAuthorized,
            boolean providerNetworkAuthorized,
            boolean remoteReceiverNetworkAuthorized,
            boolean vpsDeploymentAuthorized,
            boolean productionAuthorized,
            boolean automaticRetryAuthorized,
            String format,
            String providerPermissionAuditReference,
            String providerPermissionAuditSha256,
            String providerPermissionAuditStatus,
            String j7TransferGovernanceBasisReference,
            String j7TransferGovernanceBasisCommit,
            String j7TransferGovernanceBasisSha256,
            String j7TransferGovernanceBasisStatus) {

        /**
         * Strict legacy V1 constructor. Its validation and canonical bytes intentionally retain
         * the WO-045 contract; V2 metadata cannot be smuggled through this path.
         */
        public Grant(
                UUID goId,
                String ownerDecisionBlockSha256,
                String workOrder,
                String campaignManifestReference,
                String campaignManifestSha256,
                String localLabCommit,
                String receiverCommit,
                String officialPermissionEvidenceReference,
                String officialPermissionEvidenceSha256,
                String officialPermissionStatus,
                String receiverQualification,
                String senderQualification,
                String executionActor,
                UUID canonicalEventId,
                long providerEventId,
                UUID exportId,
                String fileSha256,
                String dataSha256,
                long fileSizeBytes,
                String schemaId,
                String schemaVersion,
                URI receiverOrigin,
                String clientCertificateSha256,
                int expectedAttemptNumber,
                int maximumDirectImportCalls,
                Instant validFrom,
                Instant validUntil,
                String ownerDecision,
                String goUse,
                String payloadClass,
                String validationStatus,
                boolean providerDerivedRealPostAuthorized,
                boolean providerNetworkAuthorized,
                boolean remoteReceiverNetworkAuthorized,
                boolean vpsDeploymentAuthorized,
                boolean productionAuthorized,
                boolean automaticRetryAuthorized) {
            this(
                    goId, ownerDecisionBlockSha256, workOrder,
                    campaignManifestReference, campaignManifestSha256,
                    localLabCommit, receiverCommit,
                    officialPermissionEvidenceReference,
                    officialPermissionEvidenceSha256,
                    officialPermissionStatus, receiverQualification, senderQualification,
                    executionActor, canonicalEventId, providerEventId, exportId,
                    fileSha256, dataSha256, fileSizeBytes, schemaId, schemaVersion,
                    receiverOrigin, clientCertificateSha256,
                    expectedAttemptNumber, maximumDirectImportCalls,
                    validFrom, validUntil, ownerDecision, goUse, payloadClass,
                    validationStatus, providerDerivedRealPostAuthorized,
                    providerNetworkAuthorized, remoteReceiverNetworkAuthorized,
                    vpsDeploymentAuthorized, productionAuthorized, automaticRetryAuthorized,
                    FORMAT_V1, null, null, null, null, null, null, null);
        }

        /** Creates a format-discriminated V2 grant with separate audit and governance facts. */
        public static Grant v2(
                UUID goId,
                String ownerDecisionBlockSha256,
                String workOrder,
                String campaignManifestReference,
                String campaignManifestSha256,
                String localLabCommit,
                String receiverCommit,
                String providerPermissionAuditReference,
                String providerPermissionAuditSha256,
                String providerPermissionAuditStatus,
                String j7TransferGovernanceBasisReference,
                String j7TransferGovernanceBasisCommit,
                String j7TransferGovernanceBasisSha256,
                String j7TransferGovernanceBasisStatus,
                String receiverQualification,
                String senderQualification,
                String executionActor,
                UUID canonicalEventId,
                long providerEventId,
                UUID exportId,
                String fileSha256,
                String dataSha256,
                long fileSizeBytes,
                String schemaId,
                String schemaVersion,
                URI receiverOrigin,
                String clientCertificateSha256,
                int expectedAttemptNumber,
                int maximumDirectImportCalls,
                Instant validFrom,
                Instant validUntil,
                String ownerDecision,
                String goUse,
                String payloadClass,
                String validationStatus,
                boolean providerDerivedRealPostAuthorized,
                boolean providerNetworkAuthorized,
                boolean remoteReceiverNetworkAuthorized,
                boolean vpsDeploymentAuthorized,
                boolean productionAuthorized,
                boolean automaticRetryAuthorized) {
            return new Grant(
                    goId, ownerDecisionBlockSha256, workOrder,
                    campaignManifestReference, campaignManifestSha256,
                    localLabCommit, receiverCommit,
                    null, null, null,
                    receiverQualification, senderQualification,
                    executionActor, canonicalEventId, providerEventId, exportId,
                    fileSha256, dataSha256, fileSizeBytes, schemaId, schemaVersion,
                    receiverOrigin, clientCertificateSha256,
                    expectedAttemptNumber, maximumDirectImportCalls,
                    validFrom, validUntil, ownerDecision, goUse, payloadClass,
                    validationStatus, providerDerivedRealPostAuthorized,
                    providerNetworkAuthorized, remoteReceiverNetworkAuthorized,
                    vpsDeploymentAuthorized, productionAuthorized, automaticRetryAuthorized,
                    FORMAT_V2,
                    providerPermissionAuditReference,
                    providerPermissionAuditSha256,
                    providerPermissionAuditStatus,
                    j7TransferGovernanceBasisReference,
                    j7TransferGovernanceBasisCommit,
                    j7TransferGovernanceBasisSha256,
                    j7TransferGovernanceBasisStatus);
        }

        public Grant {
            goId = requireUuid(goId, "goId");
            ownerDecisionBlockSha256 = requireSha(ownerDecisionBlockSha256,
                    "ownerDecisionBlockSha256");
            workOrder = requireWorkOrder(workOrder);
            campaignManifestReference = requireSafeReference(
                    campaignManifestReference, "campaignManifestReference");
            campaignManifestSha256 = requireSha(
                    campaignManifestSha256, "campaignManifestSha256");
            localLabCommit = requireCommit(localLabCommit, "localLabCommit");
            receiverCommit = requireCommit(receiverCommit, "receiverCommit");
            format = requireFormat(format);
            if (FORMAT_V1.equals(format)) {
                officialPermissionEvidenceReference = requireSafeReference(
                        officialPermissionEvidenceReference,
                        "officialPermissionEvidenceReference");
                officialPermissionEvidenceSha256 = requireSha(
                        officialPermissionEvidenceSha256,
                        "officialPermissionEvidenceSha256");
                officialPermissionStatus = requireExact(
                        officialPermissionStatus,
                        "EVIDENCED_COMPATIBLE",
                        "officialPermissionStatus");
                requireAbsentV2(
                        providerPermissionAuditReference,
                        providerPermissionAuditSha256,
                        providerPermissionAuditStatus,
                        j7TransferGovernanceBasisReference,
                        j7TransferGovernanceBasisCommit,
                        j7TransferGovernanceBasisSha256,
                        j7TransferGovernanceBasisStatus);
            }
            else {
                requireAbsentV1(
                        officialPermissionEvidenceReference,
                        officialPermissionEvidenceSha256,
                        officialPermissionStatus);
                providerPermissionAuditReference = requireSafeReference(
                        providerPermissionAuditReference,
                        "providerPermissionAuditReference");
                providerPermissionAuditSha256 = requireSha(
                        providerPermissionAuditSha256,
                        "providerPermissionAuditSha256");
                providerPermissionAuditStatus = requireV2PermissionAuditStatus(
                        providerPermissionAuditStatus);
                j7TransferGovernanceBasisReference = requireExact(
                        j7TransferGovernanceBasisReference,
                        EXPECTED_TRANSFER_GOVERNANCE_BASIS_REFERENCE,
                        "j7TransferGovernanceBasisReference");
                j7TransferGovernanceBasisCommit = requireExact(
                        j7TransferGovernanceBasisCommit,
                        EXPECTED_TRANSFER_GOVERNANCE_BASIS_COMMIT,
                        "j7TransferGovernanceBasisCommit");
                j7TransferGovernanceBasisSha256 = requireExact(
                        j7TransferGovernanceBasisSha256,
                        EXPECTED_TRANSFER_GOVERNANCE_BASIS_SHA256,
                        "j7TransferGovernanceBasisSha256");
                j7TransferGovernanceBasisStatus = requireExact(
                        j7TransferGovernanceBasisStatus,
                        EXPECTED_TRANSFER_GOVERNANCE_BASIS_STATUS,
                        "j7TransferGovernanceBasisStatus");
            }
            receiverQualification = requireExact(
                    receiverQualification, "PASS", "receiverQualification");
            senderQualification = requireExact(
                    senderQualification, "PASS", "senderQualification");
            executionActor = requireExact(executionActor, "CODEX_LOCAL_UI", "executionActor");
            canonicalEventId = requireUuid(canonicalEventId, "canonicalEventId");
            if (providerEventId < 1) {
                throw new IllegalArgumentException("providerEventId must be positive");
            }
            exportId = requireUuid(exportId, "exportId");
            fileSha256 = requireSha(fileSha256, "fileSha256");
            dataSha256 = requireSha(dataSha256, "dataSha256");
            if (fileSizeBytes < 1 || fileSizeBytes > 5L * 1024L * 1024L) {
                throw new IllegalArgumentException("fileSizeBytes is outside the J7 boundary");
            }
            schemaId = requireExact(schemaId, EXPECTED_SCHEMA_ID, "schemaId");
            schemaVersion = requireExact(schemaVersion, EXPECTED_SCHEMA_VERSION, "schemaVersion");
            receiverOrigin = Objects.requireNonNull(receiverOrigin, "receiverOrigin");
            if (!EXPECTED_RECEIVER_ORIGIN.equals(receiverOrigin)
                    || receiverOrigin.getRawUserInfo() != null
                    || receiverOrigin.getRawPath() != null && !receiverOrigin.getRawPath().isEmpty()
                    || receiverOrigin.getRawQuery() != null
                    || receiverOrigin.getRawFragment() != null) {
                throw new IllegalArgumentException("receiverOrigin is outside the loopback boundary");
            }
            clientCertificateSha256 = requireSha(
                    clientCertificateSha256, "clientCertificateSha256");
            if (expectedAttemptNumber != 1 || maximumDirectImportCalls != 1) {
                throw new IllegalArgumentException("the owner go authorizes exactly one first attempt");
            }
            validFrom = Objects.requireNonNull(validFrom, "validFrom");
            validUntil = Objects.requireNonNull(validUntil, "validUntil");
            if (!validFrom.equals(validFrom.truncatedTo(ChronoUnit.MICROS))
                    || !validUntil.equals(validUntil.truncatedTo(ChronoUnit.MICROS))) {
                throw new IllegalArgumentException(
                        "the owner go window must use PostgreSQL microsecond precision");
            }
            if (!validUntil.isAfter(validFrom)
                    || Duration.between(validFrom, validUntil).compareTo(MAXIMUM_WINDOW) > 0) {
                throw new IllegalArgumentException(
                        "the owner go window must be positive and at most 60 minutes");
            }
            ownerDecision = requireExact(ownerDecision, "GRANT", "ownerDecision");
            goUse = requireExact(goUse, "ONE_TIME", "goUse");
            payloadClass = requireExact(payloadClass, "PROVIDER_DERIVED", "payloadClass");
            validationStatus = requireExact(
                    validationStatus, "HUMAN_VALIDATED", "validationStatus");
            if (!providerDerivedRealPostAuthorized
                    || providerNetworkAuthorized
                    || remoteReceiverNetworkAuthorized
                    || vpsDeploymentAuthorized
                    || productionAuthorized
                    || automaticRetryAuthorized) {
                throw new IllegalArgumentException("owner go authorization flags are inconsistent");
            }
        }

        public Reference reference() {
            return new Reference(goId, ownerDecisionBlockSha256);
        }

        /** Returns the exact provider-permission audit fact carried by either format. */
        public String permissionAuditStatus() {
            return FORMAT_V1.equals(format)
                    ? officialPermissionStatus
                    : providerPermissionAuditStatus;
        }

        public String canonicalDecisionBlock() {
            if (FORMAT_V1.equals(format)) {
                return canonicalV1DecisionBlock();
            }
            if (FORMAT_V2.equals(format)) {
                return canonicalV2DecisionBlock();
            }
            throw new IllegalStateException("owner-go format was not validated");
        }

        private String canonicalV1DecisionBlock() {
            return String.join("\n",
                    "FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V1",
                    "GO_ID=" + goId,
                    "WORK_ORDER=" + workOrder,
                    "CAMPAIGN_MANIFEST_REFERENCE=" + campaignManifestReference,
                    "CAMPAIGN_MANIFEST_SHA256=" + campaignManifestSha256,
                    "LOCAL_LAB_COMMIT=" + localLabCommit,
                    "RECEIVER_COMMIT=" + receiverCommit,
                    "OFFICIAL_PERMISSION_EVIDENCE_REFERENCE="
                            + officialPermissionEvidenceReference,
                    "OFFICIAL_PERMISSION_EVIDENCE_SHA256="
                            + officialPermissionEvidenceSha256,
                    "OFFICIAL_PERMISSION_STATUS=" + officialPermissionStatus,
                    "RECEIVER_QUALIFICATION=" + receiverQualification,
                    "SENDER_QUALIFICATION=" + senderQualification,
                    "EXECUTION_ACTOR=" + executionActor,
                    "CANONICAL_EVENT_ID=" + canonicalEventId,
                    "PROVIDER_EVENT_ID=" + providerEventId,
                    "EXPORT_ID=" + exportId,
                    "FILE_SHA256=" + fileSha256,
                    "DATA_SHA256=" + dataSha256,
                    "FILE_SIZE_BYTES=" + fileSizeBytes,
                    "SCHEMA_ID=" + schemaId,
                    "SCHEMA_VERSION=" + schemaVersion,
                    "RECEIVER_ORIGIN=" + receiverOrigin.toASCIIString(),
                    "CLIENT_CERTIFICATE_SHA256=" + clientCertificateSha256,
                    "EXPECTED_ATTEMPT_NUMBER=" + expectedAttemptNumber,
                    "MAXIMUM_DIRECT_IMPORT_CALLS=" + maximumDirectImportCalls,
                    "VALID_FROM=" + OWNER_GO_INSTANT.format(validFrom),
                    "VALID_UNTIL=" + OWNER_GO_INSTANT.format(validUntil),
                    "OWNER_DECISION=" + ownerDecision,
                    "GO_USE=" + goUse,
                    "PAYLOAD_CLASS=" + payloadClass,
                    "VALIDATION_STATUS=" + validationStatus,
                    "PROVIDER_DERIVED_REAL_POST_AUTHORIZED="
                            + yesNo(providerDerivedRealPostAuthorized),
                    "PROVIDER_NETWORK_AUTHORIZED=" + yesNo(providerNetworkAuthorized),
                    "REMOTE_RECEIVER_NETWORK_AUTHORIZED="
                            + yesNo(remoteReceiverNetworkAuthorized),
                    "VPS_DEPLOYMENT_AUTHORIZED=" + yesNo(vpsDeploymentAuthorized),
                    "PRODUCTION_AUTHORIZED=" + yesNo(productionAuthorized),
                    "AUTOMATIC_RETRY_AUTHORIZED=" + yesNo(automaticRetryAuthorized)) + "\n";
        }

        private String canonicalV2DecisionBlock() {
            return String.join("\n",
                    "FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2",
                    "GO_ID=" + goId,
                    "WORK_ORDER=" + workOrder,
                    "CAMPAIGN_MANIFEST_REFERENCE=" + campaignManifestReference,
                    "CAMPAIGN_MANIFEST_SHA256=" + campaignManifestSha256,
                    "LOCAL_LAB_COMMIT=" + localLabCommit,
                    "RECEIVER_COMMIT=" + receiverCommit,
                    "PROVIDER_PERMISSION_AUDIT_REFERENCE="
                            + providerPermissionAuditReference,
                    "PROVIDER_PERMISSION_AUDIT_SHA256="
                            + providerPermissionAuditSha256,
                    "PROVIDER_PERMISSION_AUDIT_STATUS=" + providerPermissionAuditStatus,
                    "J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE="
                            + j7TransferGovernanceBasisReference,
                    "J7_TRANSFER_GOVERNANCE_BASIS_COMMIT="
                            + j7TransferGovernanceBasisCommit,
                    "J7_TRANSFER_GOVERNANCE_BASIS_SHA256="
                            + j7TransferGovernanceBasisSha256,
                    "J7_TRANSFER_GOVERNANCE_BASIS_STATUS="
                            + j7TransferGovernanceBasisStatus,
                    "RECEIVER_QUALIFICATION=" + receiverQualification,
                    "SENDER_QUALIFICATION=" + senderQualification,
                    "EXECUTION_ACTOR=" + executionActor,
                    "CANONICAL_EVENT_ID=" + canonicalEventId,
                    "PROVIDER_EVENT_ID=" + providerEventId,
                    "EXPORT_ID=" + exportId,
                    "FILE_SHA256=" + fileSha256,
                    "DATA_SHA256=" + dataSha256,
                    "FILE_SIZE_BYTES=" + fileSizeBytes,
                    "SCHEMA_ID=" + schemaId,
                    "SCHEMA_VERSION=" + schemaVersion,
                    "RECEIVER_ORIGIN=" + receiverOrigin.toASCIIString(),
                    "CLIENT_CERTIFICATE_SHA256=" + clientCertificateSha256,
                    "EXPECTED_ATTEMPT_NUMBER=" + expectedAttemptNumber,
                    "MAXIMUM_DIRECT_IMPORT_CALLS=" + maximumDirectImportCalls,
                    "VALID_FROM=" + OWNER_GO_INSTANT.format(validFrom),
                    "VALID_UNTIL=" + OWNER_GO_INSTANT.format(validUntil),
                    "OWNER_DECISION=" + ownerDecision,
                    "GO_USE=" + goUse,
                    "PAYLOAD_CLASS=" + payloadClass,
                    "VALIDATION_STATUS=" + validationStatus,
                    "PROVIDER_DERIVED_REAL_POST_AUTHORIZED="
                            + yesNo(providerDerivedRealPostAuthorized),
                    "PROVIDER_NETWORK_AUTHORIZED=" + yesNo(providerNetworkAuthorized),
                    "REMOTE_RECEIVER_NETWORK_AUTHORIZED="
                            + yesNo(remoteReceiverNetworkAuthorized),
                    "VPS_DEPLOYMENT_AUTHORIZED=" + yesNo(vpsDeploymentAuthorized),
                    "PRODUCTION_AUTHORIZED=" + yesNo(productionAuthorized),
                    "AUTOMATIC_RETRY_AUTHORIZED=" + yesNo(automaticRetryAuthorized)) + "\n";
        }

        public String computedOwnerDecisionBlockSha256() {
            try {
                return java.util.HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(
                                canonicalDecisionBlock().getBytes(StandardCharsets.UTF_8)));
            }
            catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        public boolean hasValidOwnerDecisionBlockSha256() {
            return MessageDigest.isEqual(
                    ownerDecisionBlockSha256.getBytes(StandardCharsets.US_ASCII),
                    computedOwnerDecisionBlockSha256().getBytes(StandardCharsets.US_ASCII));
        }

        public Grant withOwnerDecisionBlockSha256(String value) {
            return new Grant(
                    goId, value, workOrder,
                    campaignManifestReference, campaignManifestSha256,
                    localLabCommit, receiverCommit,
                    officialPermissionEvidenceReference,
                    officialPermissionEvidenceSha256,
                    officialPermissionStatus, receiverQualification, senderQualification,
                    executionActor, canonicalEventId, providerEventId, exportId,
                    fileSha256, dataSha256, fileSizeBytes, schemaId, schemaVersion,
                    receiverOrigin, clientCertificateSha256,
                    expectedAttemptNumber, maximumDirectImportCalls,
                    validFrom, validUntil, ownerDecision, goUse, payloadClass,
                    validationStatus, providerDerivedRealPostAuthorized,
                    providerNetworkAuthorized, remoteReceiverNetworkAuthorized,
                    vpsDeploymentAuthorized, productionAuthorized, automaticRetryAuthorized,
                    format,
                    providerPermissionAuditReference,
                    providerPermissionAuditSha256,
                    providerPermissionAuditStatus,
                    j7TransferGovernanceBasisReference,
                    j7TransferGovernanceBasisCommit,
                    j7TransferGovernanceBasisSha256,
                    j7TransferGovernanceBasisStatus);
        }

        @Override
        public String toString() {
            return "Grant[goId=" + goId
                    + ",workOrder=" + workOrder
                    + ",exportId=" + exportId
                    + ",canonicalEventId=" + canonicalEventId
                    + ",providerEventId=" + providerEventId
                    + ",hashes=REDACTED]";
        }
    }

    public record Claim(Grant grant, String idempotencyKey, Instant requestedAt) {

        private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
                "j7:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}:sha256:[0-9a-f]{64}");

        public Claim {
            grant = Objects.requireNonNull(grant, "grant");
            if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
                throw new IllegalArgumentException("idempotencyKey does not match J7 delivery v1");
            }
            String expected = "j7:" + grant.exportId() + ":sha256:" + grant.fileSha256();
            if (!expected.equals(idempotencyKey)) {
                throw new IllegalArgumentException("idempotencyKey does not match the owner go");
            }
            requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        }

        @Override
        public String toString() {
            return "Claim[goId=" + grant.goId()
                    + ",exportId=" + grant.exportId()
                    + ",requestedAt=" + requestedAt
                    + ",idempotencyKey=REDACTED]";
        }
    }

    public enum Status {
        AVAILABLE,
        NOT_YET_VALID,
        EXPIRED,
        REVOKED,
        CONSUMED
    }

    public record Snapshot(
            Grant grant,
            Status status,
            Instant registeredAt,
            Instant observedAt,
            Optional<String> revocationDecisionBlockSha256,
            Optional<Instant> revokedAt,
            Optional<Instant> consumedAt,
            Optional<UUID> deliveryId,
            OptionalInt attemptNumber) {

        public Snapshot {
            grant = Objects.requireNonNull(grant, "grant");
            status = Objects.requireNonNull(status, "status");
            registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
            revocationDecisionBlockSha256 = Objects.requireNonNull(
                    revocationDecisionBlockSha256, "revocationDecisionBlockSha256");
            revokedAt = Objects.requireNonNull(revokedAt, "revokedAt");
            consumedAt = Objects.requireNonNull(consumedAt, "consumedAt");
            deliveryId = Objects.requireNonNull(deliveryId, "deliveryId");
            attemptNumber = Objects.requireNonNull(attemptNumber, "attemptNumber");
            revocationDecisionBlockSha256.ifPresent(value -> requireSha(
                    value, "revocationDecisionBlockSha256"));
            deliveryId.ifPresent(value -> requireUuid(value, "deliveryId"));
            if (attemptNumber.isPresent() && attemptNumber.getAsInt() < 1) {
                throw new IllegalArgumentException("attemptNumber must be positive");
            }
            boolean revoked = revocationDecisionBlockSha256.isPresent() && revokedAt.isPresent();
            boolean consumed = consumedAt.isPresent()
                    && deliveryId.isPresent()
                    && attemptNumber.isPresent();
            if (revocationDecisionBlockSha256.isPresent() != revokedAt.isPresent()
                    || consumedAt.isPresent() != deliveryId.isPresent()
                    || consumedAt.isPresent() != attemptNumber.isPresent()
                    || status == Status.REVOKED && (!revoked || consumed)
                    || status == Status.CONSUMED && (!consumed || revoked)
                    || status != Status.REVOKED && revoked
                    || status != Status.CONSUMED && consumed) {
                throw new IllegalArgumentException("owner go lifecycle snapshot is inconsistent");
            }
        }

        @Override
        public String toString() {
            return "Snapshot[goId=" + grant.goId()
                    + ",status=" + status
                    + ",observedAt=" + observedAt
                    + ",hashes=REDACTED]";
        }
    }

    private static String requireSha(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
        return value;
    }

    private static String requireFormat(String value) {
        if (!FORMAT_V1.equals(value) && !FORMAT_V2.equals(value)) {
            throw new IllegalArgumentException("format is outside the exact owner-go contract");
        }
        return value;
    }

    private static String requireV2PermissionAuditStatus(String value) {
        if (!"NOT_EVIDENCED".equals(value) && !"EVIDENCED_COMPATIBLE".equals(value)) {
            throw new IllegalArgumentException(
                    "providerPermissionAuditStatus is outside the exact owner-go V2 contract");
        }
        return value;
    }

    private static void requireAbsentV1(
            String reference,
            String sha256,
            String status) {
        if (reference != null || sha256 != null || status != null) {
            throw new IllegalArgumentException(
                    "legacy official-permission fields are forbidden in owner-go V2");
        }
    }

    private static void requireAbsentV2(
            String auditReference,
            String auditSha256,
            String auditStatus,
            String governanceReference,
            String governanceCommit,
            String governanceSha256,
            String governanceStatus) {
        if (auditReference != null
                || auditSha256 != null
                || auditStatus != null
                || governanceReference != null
                || governanceCommit != null
                || governanceSha256 != null
                || governanceStatus != null) {
            throw new IllegalArgumentException(
                    "owner-go V2 fields are forbidden in strict legacy V1");
        }
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String requireCommit(String value, String name) {
        if (value == null || !COMMIT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a full lower-case Git commit");
        }
        return value;
    }

    private static String requireSafeReference(String value, String name) {
        if (value == null
                || !SAFE_REFERENCE.matcher(value).matches()
                || value.contains("..")
                || value.contains("\\")
                || value.contains("://")) {
            throw new IllegalArgumentException(
                    name + " must be a confined docs/validation reference");
        }
        return value;
    }

    private static String requireWorkOrder(String value) {
        if (value == null || !WORK_ORDER.matcher(value).matches()) {
            throw new IllegalArgumentException("workOrder is not canonical");
        }
        return value;
    }

    private static String requireExact(String value, String expected, String name) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(name + " is outside the exact owner-go contract");
        }
        return value;
    }

    private static UUID requireUuid(UUID value, String name) {
        if (value == null
                || NIL_UUID.equals(value)
                || value.variant() != 2
                || value.version() < 1
                || value.version() > 5) {
            throw new IllegalArgumentException(name + " must be a non-nil RFC 4122 UUID");
        }
        return value;
    }
}
