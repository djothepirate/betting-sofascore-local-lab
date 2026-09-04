package com.bettingproject.sofascorelocal.adapter.persistence.delivery;

import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryReceivedAtPolicy;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PostgreSQL delivery ledger adapter. Every claim and completion is a short local transaction;
 * this component never performs transport and never stores an export or acknowledgement body.
 */
@Repository
public class JdbcJ7DeliveryLedgerStore implements J7DeliveryLedgerStore {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_RESULT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,95}");
    private static final UUID NIL_UUID = new UUID(0, 0);

    private static final String SELECT_DELIVERY_COLUMNS = """
            delivery.id,
            delivery.delivery_uuid,
            delivery.export_manifest_id,
            delivery.export_uuid,
            delivery.file_sha256,
            delivery.data_sha256,
            delivery.file_size_bytes,
            delivery.idempotency_key,
            delivery.protocol_version,
            delivery.current_state,
            delivery.created_at,
            delivery.state_changed_at,
            (
                select count(*)
                from j7_delivery_attempt attempt
                where attempt.delivery_id = delivery.id
            ) as attempt_count
            """;

    private static final String FIND_BY_EXPORT_SQL = """
            select %s
            from j7_delivery delivery
            where delivery.export_uuid = :exportId
            """.formatted(SELECT_DELIVERY_COLUMNS);

    private static final String FIND_BY_EXPORT_FOR_UPDATE_SQL = FIND_BY_EXPORT_SQL + " for update";

    private static final String FIND_BY_DELIVERY_FOR_UPDATE_SQL = """
            select %s
            from j7_delivery delivery
            where delivery.delivery_uuid = :deliveryId
            for update
            """.formatted(SELECT_DELIVERY_COLUMNS);

    private static final String FIND_EXACT_MANIFEST_SQL = """
            select id,
                   export_uuid,
                   content_sha256,
                   data_sha256,
                   content_size_bytes,
                   validation_status,
                   export_kind
            from export_manifest
            where export_uuid = :exportId
            for key share
            """;

    private static final String INSERT_DELIVERY_SQL = """
            insert into j7_delivery (
                delivery_uuid,
                export_manifest_id,
                export_uuid,
                file_sha256,
                data_sha256,
                file_size_bytes,
                idempotency_key,
                protocol_version,
                current_state,
                created_at,
                state_changed_at
            ) values (
                :deliveryId,
                :exportManifestId,
                :exportId,
                :fileSha256,
                :dataSha256,
                :fileSizeBytes,
                :idempotencyKey,
                '1.0',
                'NOT_ATTEMPTED',
                :createdAt,
                :createdAt
            )
            returning id
            """;

    private static final String INSERT_ATTEMPT_SQL = """
            insert into j7_delivery_attempt (
                attempt_uuid,
                delivery_id,
                attempt_number,
                started_at,
                payload_class
            ) values (
                :attemptId,
                :deliveryDatabaseId,
                :attemptNumber,
                :startedAt,
                :payloadClass
            )
            returning id
            """;

    private static final String SELECT_GRANT_COLUMNS = """
            owner_go.id,
            owner_go.go_uuid,
            owner_go.owner_decision_block_sha256,
            owner_go.work_order,
            owner_go.campaign_manifest_reference,
            owner_go.campaign_manifest_sha256,
            owner_go.local_lab_commit,
            owner_go.receiver_commit,
            owner_go.owner_go_format,
            owner_go.official_permission_evidence_reference,
            owner_go.official_permission_evidence_sha256,
            owner_go.official_permission_status,
            owner_go.provider_permission_audit_reference,
            owner_go.provider_permission_audit_sha256,
            owner_go.provider_permission_audit_status,
            owner_go.j7_transfer_governance_basis_reference,
            owner_go.j7_transfer_governance_basis_commit,
            owner_go.j7_transfer_governance_basis_sha256,
            owner_go.j7_transfer_governance_basis_status,
            owner_go.receiver_qualification,
            owner_go.sender_qualification,
            owner_go.execution_actor,
            owner_go.canonical_event_id,
            owner_go.provider_event_id,
            owner_go.export_uuid,
            owner_go.file_sha256,
            owner_go.data_sha256,
            owner_go.file_size_bytes,
            owner_go.schema_id,
            owner_go.schema_version,
            owner_go.receiver_origin,
            owner_go.client_certificate_sha256,
            owner_go.expected_attempt_number,
            owner_go.maximum_direct_import_calls,
            owner_go.valid_from,
            owner_go.valid_until,
            owner_go.owner_decision,
            owner_go.go_use,
            owner_go.payload_class,
            owner_go.validation_status,
            owner_go.provider_derived_real_post_authorized,
            owner_go.provider_network_authorized,
            owner_go.remote_receiver_network_authorized,
            owner_go.vps_deployment_authorized,
            owner_go.production_authorized,
            owner_go.automatic_retry_authorized,
            owner_go.registered_at,
            clock_timestamp() as observed_at,
            revocation.revocation_decision_block_sha256,
            revocation.revoked_at,
            consumption.consumed_at,
            delivery.delivery_uuid as consumed_delivery_uuid,
            attempt.attempt_number as consumed_attempt_number
            """;

    private static final String FIND_GRANT_SQL = """
            select %s
            from j7_provider_delivery_owner_go_grant owner_go
            left join j7_provider_delivery_owner_go_revocation revocation
              on revocation.grant_id = owner_go.id
            left join j7_provider_delivery_owner_go_consumption consumption
              on consumption.grant_id = owner_go.id
            left join j7_delivery_attempt attempt
              on attempt.id = consumption.attempt_id
            left join j7_delivery delivery
              on delivery.id = attempt.delivery_id
            where owner_go.go_uuid = :goId
            """.formatted(SELECT_GRANT_COLUMNS);

    private static final String FIND_GRANT_FOR_UPDATE_SQL =
            FIND_GRANT_SQL + " for update of owner_go";

    private static final String INSERT_GRANT_SQL = """
            insert into j7_provider_delivery_owner_go_grant (
                go_uuid, owner_decision_block_sha256, work_order,
                campaign_manifest_reference, campaign_manifest_sha256,
                local_lab_commit, receiver_commit,
                owner_go_format,
                official_permission_evidence_reference,
                official_permission_evidence_sha256,
                official_permission_status,
                provider_permission_audit_reference,
                provider_permission_audit_sha256,
                provider_permission_audit_status,
                j7_transfer_governance_basis_reference,
                j7_transfer_governance_basis_commit,
                j7_transfer_governance_basis_sha256,
                j7_transfer_governance_basis_status,
                receiver_qualification,
                sender_qualification, execution_actor, export_manifest_id,
                canonical_event_id, provider_event_id, export_uuid,
                file_sha256, data_sha256, file_size_bytes,
                schema_id, schema_version, receiver_origin,
                client_certificate_sha256, expected_attempt_number,
                maximum_direct_import_calls, valid_from, valid_until,
                owner_decision, go_use, payload_class, validation_status,
                provider_derived_real_post_authorized,
                provider_network_authorized, remote_receiver_network_authorized,
                vps_deployment_authorized, production_authorized,
                automatic_retry_authorized
            ) values (
                :goId, :ownerDecisionBlockSha256, :workOrder,
                :campaignManifestReference, :campaignManifestSha256,
                :localLabCommit, :receiverCommit,
                :ownerGoFormat,
                :officialPermissionEvidenceReference,
                :officialPermissionEvidenceSha256,
                :officialPermissionStatus,
                :providerPermissionAuditReference,
                :providerPermissionAuditSha256,
                :providerPermissionAuditStatus,
                :j7TransferGovernanceBasisReference,
                :j7TransferGovernanceBasisCommit,
                :j7TransferGovernanceBasisSha256,
                :j7TransferGovernanceBasisStatus,
                :receiverQualification,
                :senderQualification, :executionActor, :exportManifestId,
                :canonicalEventId, :providerEventId, :exportId,
                :fileSha256, :dataSha256, :fileSizeBytes,
                :schemaId, :schemaVersion, :receiverOrigin,
                :clientCertificateSha256, :expectedAttemptNumber,
                :maximumDirectImportCalls, :validFrom, :validUntil,
                :ownerDecision, :goUse, :payloadClass, :validationStatus,
                :providerDerivedRealPostAuthorized,
                :providerNetworkAuthorized, :remoteReceiverNetworkAuthorized,
                :vpsDeploymentAuthorized, :productionAuthorized,
                :automaticRetryAuthorized
            )
            """;

    private static final String INSERT_REVOCATION_SQL = """
            insert into j7_provider_delivery_owner_go_revocation (
                grant_id, revocation_decision_block_sha256, revoked_at
            ) values (:grantDatabaseId, :revocationDecisionBlockSha256, default)
            """;

    private static final String INSERT_CONSUMPTION_SQL = """
            insert into j7_provider_delivery_owner_go_consumption (
                grant_id, attempt_id, consumed_at
            ) values (:grantDatabaseId, :attemptDatabaseId, default)
            """;

    private static final String CLAIM_DELIVERY_SQL = """
            update j7_delivery
            set current_state = 'IN_FLIGHT',
                state_changed_at = :startedAt
            where id = :deliveryDatabaseId
              and current_state = :expectedState
            """;

    private static final String INSERT_RESULT_SQL = """
            insert into j7_delivery_attempt_result (
                attempt_id,
                terminal_state,
                http_status,
                safe_result_code,
                acknowledgement_sha256,
                remote_import_id,
                acknowledgement_received_at,
                completed_at
            ) values (
                :attemptDatabaseId,
                :terminalState,
                :httpStatus,
                :safeResultCode,
                :acknowledgementSha256,
                :remoteImportId,
                :acknowledgementReceivedAt,
                :completedAt
            )
            """;

    private static final String COMPLETE_DELIVERY_SQL = """
            update j7_delivery
            set current_state = :terminalState,
                state_changed_at = :completedAt
            where id = :deliveryDatabaseId
              and current_state = 'IN_FLIGHT'
            """;

    private static final String FIND_ATTEMPT_SQL = """
            select attempt.id, attempt.started_at, attempt.created_at,
                   result.attempt_id as result_attempt_id
            from j7_delivery_attempt attempt
            left join j7_delivery_attempt_result result on result.attempt_id = attempt.id
            where attempt.delivery_id = :deliveryDatabaseId
              and attempt.attempt_number = :attemptNumber
            """;

    private static final String FIND_GLOBAL_IN_FLIGHT_SQL = """
            select delivery_uuid
            from j7_delivery
            where current_state = 'IN_FLIGHT'
            limit 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<DeliveryRow> deliveryRowMapper = this::mapDelivery;

    public JdbcJ7DeliveryLedgerStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimReceipt claim(
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            int expectedAttemptNumber,
            Instant startedAt) {
        requireClaim(
                exportId,
                fileSha256,
                dataSha256,
                idempotencyKey,
                expectedAttemptNumber,
                startedAt);
        try {
            acquireGlobalClaimLock();
            return claimLocked(
                    exportId,
                    fileSha256,
                    dataSha256,
                    idempotencyKey,
                    expectedAttemptNumber,
                    startedAt,
                    "SYNTHETIC_ONLY",
                    Optional.empty());
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException | ArithmeticException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public J7ProviderDerivedOwnerGo.Snapshot registerProviderDerivedOwnerGo(
            J7ProviderDerivedOwnerGo.Grant grant) {
        Objects.requireNonNull(grant, "grant");
        requireCanonicalGrantHash(grant);
        try {
            acquireGlobalClaimLock();
            Optional<ProviderGoRow> existing = findGrantForUpdate(grant.goId());
            if (existing.isPresent()) {
                ProviderGoRow row = existing.orElseThrow();
                requireSameGrant(row.grant(), grant);
                return row.snapshot();
            }
            ManifestRow manifest = findManifest(grant.exportId())
                    .orElseThrow(() -> new LedgerException(
                            LedgerFailure.EXPORT_NOT_ELIGIBLE));
            MapSqlParameterSource parameters = grantParameters(grant)
                    .addValue("exportManifestId", manifest.databaseId());
            requireOne(jdbcTemplate.update(INSERT_GRANT_SQL, parameters), "owner-go grant insert");
            ProviderGoRow inserted = findGrantForUpdate(grant.goId())
                    .orElseThrow(() -> new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE));
            requireSameGrant(inserted.grant(), grant);
            return inserted.snapshot();
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public J7ProviderDerivedOwnerGo.Snapshot revokeProviderDerivedOwnerGo(
            J7ProviderDerivedOwnerGo.Reference reference,
            String revocationDecisionBlockSha256) {
        Objects.requireNonNull(reference, "reference");
        requireSha256(revocationDecisionBlockSha256, "revocationDecisionBlockSha256");
        try {
            acquireGlobalClaimLock();
            ProviderGoRow existing = findGrantForUpdate(reference.goId())
                    .orElseThrow(() -> new LedgerException(
                            LedgerFailure.OWNER_GO_NOT_REGISTERED));
            requireSameReference(existing.grant(), reference);
            if (existing.status() == J7ProviderDerivedOwnerGo.Status.CONSUMED) {
                throw new LedgerException(LedgerFailure.OWNER_GO_CONSUMED);
            }
            if (existing.status() == J7ProviderDerivedOwnerGo.Status.REVOKED) {
                if (existing.revocationDecisionBlockSha256()
                        .filter(revocationDecisionBlockSha256::equals).isEmpty()) {
                    throw new LedgerException(LedgerFailure.OWNER_GO_REVOCATION_CONFLICT);
                }
                return existing.snapshot();
            }
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("grantDatabaseId", existing.databaseId())
                    .addValue("revocationDecisionBlockSha256", revocationDecisionBlockSha256);
            requireOne(jdbcTemplate.update(INSERT_REVOCATION_SQL, parameters),
                    "owner-go revocation insert");
            return findGrantForUpdate(reference.goId())
                    .map(ProviderGoRow::snapshot)
                    .orElseThrow(() -> new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE));
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<J7ProviderDerivedOwnerGo.Snapshot> findProviderDerivedOwnerGo(
            J7ProviderDerivedOwnerGo.Reference reference) {
        Objects.requireNonNull(reference, "reference");
        try {
            Optional<ProviderGoRow> row = findGrant(reference.goId());
            row.ifPresent(value -> requireSameReference(value.grant(), reference));
            return row.map(ProviderGoRow::snapshot);
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimReceipt claimProviderDerived(J7ProviderDerivedOwnerGo.Claim claim) {
        Objects.requireNonNull(claim, "claim");
        requireCanonicalGrantHash(claim.grant());
        try {
            acquireGlobalClaimLock();
            ProviderGoRow persisted = findGrantForUpdate(claim.grant().goId())
                    .orElseThrow(() -> new LedgerException(
                            LedgerFailure.OWNER_GO_NOT_REGISTERED));
            requireSameGrant(persisted.grant(), claim.grant());
            Instant databaseNow = databaseClock();
            J7ProviderDerivedOwnerGo.Status currentStatus = persisted.statusAt(databaseNow);
            switch (currentStatus) {
                case NOT_YET_VALID -> throw new LedgerException(
                        LedgerFailure.OWNER_GO_NOT_YET_VALID);
                case EXPIRED -> throw new LedgerException(LedgerFailure.OWNER_GO_EXPIRED);
                case REVOKED -> throw new LedgerException(LedgerFailure.OWNER_GO_REVOKED);
                case CONSUMED -> throw new LedgerException(LedgerFailure.OWNER_GO_CONSUMED);
                case AVAILABLE -> {
                    // Continue under the same transaction and global advisory lock.
                }
            }
            return claimLocked(
                    claim.grant().exportId(),
                    claim.grant().fileSha256(),
                    claim.grant().dataSha256(),
                    claim.idempotencyKey(),
                    claim.grant().expectedAttemptNumber(),
                    databaseNow,
                    "PROVIDER_DERIVED",
                    Optional.of(persisted.databaseId()));
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException | ArithmeticException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    private ClaimReceipt claimLocked(
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            int expectedAttemptNumber,
            Instant startedAt,
            String payloadClass,
            Optional<Long> providerGrantDatabaseId) {
        Optional<DeliveryRow> existing = findDeliveryForUpdate(exportId);
        Optional<UUID> globallyInFlight = findGloballyInFlight();
        if (globallyInFlight.isPresent()) {
            LedgerFailure failure = existing
                    .filter(row -> row.deliveryId().equals(globallyInFlight.orElseThrow()))
                    .isPresent()
                    ? LedgerFailure.DELIVERY_ALREADY_IN_FLIGHT
                    : LedgerFailure.ANOTHER_DELIVERY_IN_FLIGHT;
            throw new LedgerException(failure);
        }

        DeliveryRow delivery = existing.orElseGet(() -> createDelivery(
                exportId, fileSha256, dataSha256, idempotencyKey, startedAt));
        requireSameIdentity(delivery, fileSha256, dataSha256, idempotencyKey);
        if (delivery.state().forbidsAnotherClaim()) {
            throw new LedgerException(delivery.state() == DeliveryState.IN_FLIGHT
                    ? LedgerFailure.DELIVERY_ALREADY_IN_FLIGHT
                    : LedgerFailure.DELIVERY_TERMINAL);
        }

        int attemptNumber = Math.addExact(delivery.attemptCount(), 1);
        if (attemptNumber != expectedAttemptNumber) {
            throw new LedgerException(LedgerFailure.ATTEMPT_ORDINAL_MISMATCH);
        }
        MapSqlParameterSource attemptParameters = new MapSqlParameterSource()
                .addValue("attemptId", UUID.randomUUID())
                .addValue("deliveryDatabaseId", delivery.databaseId())
                .addValue("attemptNumber", attemptNumber)
                .addValue("startedAt", utc(startedAt))
                .addValue("payloadClass", payloadClass);
        long attemptDatabaseId = requireIdentifier(
                jdbcTemplate.queryForObject(INSERT_ATTEMPT_SQL, attemptParameters, Long.class),
                "attempt insert");

        if (providerGrantDatabaseId.isPresent()) {
            MapSqlParameterSource consumptionParameters = new MapSqlParameterSource()
                    .addValue("grantDatabaseId", providerGrantDatabaseId.orElseThrow())
                    .addValue("attemptDatabaseId", attemptDatabaseId);
            requireOne(jdbcTemplate.update(INSERT_CONSUMPTION_SQL, consumptionParameters),
                    "owner-go consumption insert");
        }

        MapSqlParameterSource claimParameters = new MapSqlParameterSource()
                .addValue("deliveryDatabaseId", delivery.databaseId())
                .addValue("expectedState", delivery.state().name())
                .addValue("startedAt", utc(startedAt));
        requireOne(jdbcTemplate.update(CLAIM_DELIVERY_SQL, claimParameters), "delivery claim");
        return new ClaimReceipt(
                delivery.deliveryId(),
                attemptNumber,
                DeliveryState.IN_FLIGHT,
                delivery.idempotencyKey(),
                startedAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliverySnapshot complete(
            UUID deliveryId,
            int attemptNumber,
            DeliveryState terminalState,
            OptionalInt httpStatus,
            String safeResultCode,
            Optional<String> acknowledgementSha256,
            Optional<UUID> remoteImportId,
            Optional<Instant> acknowledgementReceivedAt,
            Instant completedAt) {
        requireCompletion(
                deliveryId,
                attemptNumber,
                terminalState,
                httpStatus,
                safeResultCode,
                acknowledgementSha256,
                remoteImportId,
                acknowledgementReceivedAt,
                completedAt);
        try {
            acquireGlobalClaimLock();
            return completeLocked(
                    deliveryId,
                    attemptNumber,
                    terminalState,
                    httpStatus,
                    safeResultCode,
                    acknowledgementSha256,
                    remoteImportId,
                    acknowledgementReceivedAt,
                    completedAt);
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliverySnapshot reconcileStaleInFlightAsUnknown(
            UUID deliveryId,
            int attemptNumber,
            Instant expectedStartedAt) {
        requireExportId(deliveryId);
        if (attemptNumber < 1) {
            throw new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE);
        }
        Objects.requireNonNull(expectedStartedAt, "expectedStartedAt");
        try {
            acquireGlobalClaimLock();
            DeliveryRow delivery = findDeliveryByIdForUpdate(deliveryId)
                    .orElseThrow(() -> new LedgerException(
                            LedgerFailure.ATTEMPT_NOT_ACTIVE));
            if (delivery.state() != DeliveryState.IN_FLIGHT
                    || delivery.attemptCount() != attemptNumber) {
                throw new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE);
            }
            AttemptRow attempt = findAttempt(delivery.databaseId(), attemptNumber)
                    .orElseThrow(() -> new LedgerException(
                            LedgerFailure.ATTEMPT_NOT_ACTIVE));
            if (attempt.resultPresent()
                    || !attempt.startedAt().equals(expectedStartedAt)) {
                throw new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE);
            }
            Instant reconciledAt = databaseClock();
            Instant earliestReconciliation = attempt.createdAt().plus(
                    MINIMUM_STALE_IN_FLIGHT_AGE);
            if (reconciledAt.isBefore(earliestReconciliation)) {
                throw new LedgerException(LedgerFailure.ATTEMPT_NOT_STALE);
            }
            return completeLocked(
                    deliveryId,
                    attemptNumber,
                    DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED,
                    OptionalInt.empty(),
                    "OPERATOR_RECONCILED_STALE_IN_FLIGHT",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    reconciledAt);
        }
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException | ArithmeticException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    private DeliverySnapshot completeLocked(
            UUID deliveryId,
            int attemptNumber,
            DeliveryState terminalState,
            OptionalInt httpStatus,
            String safeResultCode,
            Optional<String> acknowledgementSha256,
            Optional<UUID> remoteImportId,
            Optional<Instant> acknowledgementReceivedAt,
            Instant completedAt) {
        DeliveryRow delivery = findDeliveryByIdForUpdate(deliveryId)
                .orElseThrow(() -> new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE));
        if (delivery.state() != DeliveryState.IN_FLIGHT
                || delivery.attemptCount() != attemptNumber) {
            throw new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE);
        }

        AttemptRow attempt = findAttempt(delivery.databaseId(), attemptNumber)
                .orElseThrow(() -> new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE));
        if (attempt.resultPresent() || completedAt.isBefore(attempt.startedAt())) {
            throw new LedgerException(LedgerFailure.ATTEMPT_NOT_ACTIVE);
        }

        // acknowledgementReceivedAt is receiver-authored metadata. Only startedAt and
        // completedAt share the Local Lab clock and therefore have a meaningful ordering.
        MapSqlParameterSource resultParameters = new MapSqlParameterSource()
                .addValue("attemptDatabaseId", attempt.databaseId())
                .addValue("terminalState", terminalState.name())
                .addValue(
                        "httpStatus",
                        httpStatus.isPresent() ? httpStatus.getAsInt() : null,
                        Types.INTEGER)
                .addValue("safeResultCode", safeResultCode)
                .addValue(
                        "acknowledgementSha256",
                        acknowledgementSha256.orElse(null),
                        Types.VARCHAR)
                .addValue(
                        "remoteImportId",
                        remoteImportId.orElse(null),
                        Types.OTHER)
                .addValue(
                        "acknowledgementReceivedAt",
                        acknowledgementReceivedAt.map(JdbcJ7DeliveryLedgerStore::utc)
                                .orElse(null),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("completedAt", utc(completedAt));
        requireOne(
                jdbcTemplate.update(INSERT_RESULT_SQL, resultParameters),
                "attempt result insert");

        MapSqlParameterSource completionParameters = new MapSqlParameterSource()
                .addValue("deliveryDatabaseId", delivery.databaseId())
                .addValue("terminalState", terminalState.name())
                .addValue("completedAt", utc(completedAt));
        requireOne(
                jdbcTemplate.update(COMPLETE_DELIVERY_SQL, completionParameters),
                "delivery completion");
        return findDeliveryForUpdate(delivery.exportId())
                .map(DeliveryRow::snapshot)
                .orElseThrow(() -> new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliverySnapshot> find(UUID exportId, String fileSha256) {
        requireExportId(exportId);
        requireSha256(fileSha256, "fileSha256");
        try {
            return single(jdbcTemplate.query(
                    FIND_BY_EXPORT_SQL,
                    new MapSqlParameterSource("exportId", exportId),
                    deliveryRowMapper))
                    .filter(row -> row.fileSha256().equals(fileSha256))
                    .map(DeliveryRow::snapshot);
        }
        catch (DataAccessException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    private Optional<ProviderGoRow> findGrant(UUID goId) {
        return single(jdbcTemplate.query(
                FIND_GRANT_SQL,
                new MapSqlParameterSource("goId", goId),
                this::mapProviderGo));
    }

    private Optional<ProviderGoRow> findGrantForUpdate(UUID goId) {
        return single(jdbcTemplate.query(
                FIND_GRANT_FOR_UPDATE_SQL,
                new MapSqlParameterSource("goId", goId),
                this::mapProviderGo));
    }

    private ProviderGoRow mapProviderGo(ResultSet resultSet, int rowNumber)
            throws SQLException {
        J7ProviderDerivedOwnerGo.Grant grant = mapGrant(resultSet);
        return new ProviderGoRow(
                resultSet.getLong("id"),
                grant,
                resultSet.getObject("registered_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("observed_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(resultSet.getString(
                        "revocation_decision_block_sha256")),
                optionalInstant(resultSet, "revoked_at"),
                optionalInstant(resultSet, "consumed_at"),
                Optional.ofNullable(resultSet.getObject(
                        "consumed_delivery_uuid", UUID.class)),
                optionalInt(resultSet, "consumed_attempt_number"));
    }

    private static J7ProviderDerivedOwnerGo.Grant mapGrant(ResultSet resultSet)
            throws SQLException {
        String format = resultSet.getString("owner_go_format");
        if (J7ProviderDerivedOwnerGo.FORMAT_V1.equals(format)) {
            return new J7ProviderDerivedOwnerGo.Grant(
                    resultSet.getObject("go_uuid", UUID.class),
                    resultSet.getString("owner_decision_block_sha256"),
                    resultSet.getString("work_order"),
                    resultSet.getString("campaign_manifest_reference"),
                    resultSet.getString("campaign_manifest_sha256"),
                    resultSet.getString("local_lab_commit"),
                    resultSet.getString("receiver_commit"),
                    resultSet.getString("official_permission_evidence_reference"),
                    resultSet.getString("official_permission_evidence_sha256"),
                    resultSet.getString("official_permission_status"),
                    resultSet.getString("receiver_qualification"),
                    resultSet.getString("sender_qualification"),
                    resultSet.getString("execution_actor"),
                    resultSet.getObject("canonical_event_id", UUID.class),
                    resultSet.getLong("provider_event_id"),
                    resultSet.getObject("export_uuid", UUID.class),
                    resultSet.getString("file_sha256"),
                    resultSet.getString("data_sha256"),
                    resultSet.getLong("file_size_bytes"),
                    resultSet.getString("schema_id"),
                    resultSet.getString("schema_version"),
                    URI.create(resultSet.getString("receiver_origin")),
                    resultSet.getString("client_certificate_sha256"),
                    resultSet.getInt("expected_attempt_number"),
                    resultSet.getInt("maximum_direct_import_calls"),
                    resultSet.getObject("valid_from", OffsetDateTime.class).toInstant(),
                    resultSet.getObject("valid_until", OffsetDateTime.class).toInstant(),
                    resultSet.getString("owner_decision"),
                    resultSet.getString("go_use"),
                    resultSet.getString("payload_class"),
                    resultSet.getString("validation_status"),
                    resultSet.getBoolean("provider_derived_real_post_authorized"),
                    resultSet.getBoolean("provider_network_authorized"),
                    resultSet.getBoolean("remote_receiver_network_authorized"),
                    resultSet.getBoolean("vps_deployment_authorized"),
                    resultSet.getBoolean("production_authorized"),
                    resultSet.getBoolean("automatic_retry_authorized"));
        }
        if (J7ProviderDerivedOwnerGo.FORMAT_V2.equals(format)) {
            return J7ProviderDerivedOwnerGo.Grant.v2(
                    resultSet.getObject("go_uuid", UUID.class),
                    resultSet.getString("owner_decision_block_sha256"),
                    resultSet.getString("work_order"),
                    resultSet.getString("campaign_manifest_reference"),
                    resultSet.getString("campaign_manifest_sha256"),
                    resultSet.getString("local_lab_commit"),
                    resultSet.getString("receiver_commit"),
                    resultSet.getString("provider_permission_audit_reference"),
                    resultSet.getString("provider_permission_audit_sha256"),
                    resultSet.getString("provider_permission_audit_status"),
                    resultSet.getString("j7_transfer_governance_basis_reference"),
                    resultSet.getString("j7_transfer_governance_basis_commit"),
                    resultSet.getString("j7_transfer_governance_basis_sha256"),
                    resultSet.getString("j7_transfer_governance_basis_status"),
                    resultSet.getString("receiver_qualification"),
                    resultSet.getString("sender_qualification"),
                    resultSet.getString("execution_actor"),
                    resultSet.getObject("canonical_event_id", UUID.class),
                    resultSet.getLong("provider_event_id"),
                    resultSet.getObject("export_uuid", UUID.class),
                    resultSet.getString("file_sha256"),
                    resultSet.getString("data_sha256"),
                    resultSet.getLong("file_size_bytes"),
                    resultSet.getString("schema_id"),
                    resultSet.getString("schema_version"),
                    URI.create(resultSet.getString("receiver_origin")),
                    resultSet.getString("client_certificate_sha256"),
                    resultSet.getInt("expected_attempt_number"),
                    resultSet.getInt("maximum_direct_import_calls"),
                    resultSet.getObject("valid_from", OffsetDateTime.class).toInstant(),
                    resultSet.getObject("valid_until", OffsetDateTime.class).toInstant(),
                    resultSet.getString("owner_decision"),
                    resultSet.getString("go_use"),
                    resultSet.getString("payload_class"),
                    resultSet.getString("validation_status"),
                    resultSet.getBoolean("provider_derived_real_post_authorized"),
                    resultSet.getBoolean("provider_network_authorized"),
                    resultSet.getBoolean("remote_receiver_network_authorized"),
                    resultSet.getBoolean("vps_deployment_authorized"),
                    resultSet.getBoolean("production_authorized"),
                    resultSet.getBoolean("automatic_retry_authorized"));
        }
        throw new LedgerException(LedgerFailure.OWNER_GO_FORMAT_INVALID);
    }

    private static Optional<Instant> optionalInstant(ResultSet resultSet, String column)
            throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static OptionalInt optionalInt(ResultSet resultSet, String column)
            throws SQLException {
        Integer value = resultSet.getObject(column, Integer.class);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static MapSqlParameterSource grantParameters(
            J7ProviderDerivedOwnerGo.Grant grant) {
        return new MapSqlParameterSource()
                .addValue("goId", grant.goId())
                .addValue("ownerDecisionBlockSha256", grant.ownerDecisionBlockSha256())
                .addValue("workOrder", grant.workOrder())
                .addValue("campaignManifestReference", grant.campaignManifestReference())
                .addValue("campaignManifestSha256", grant.campaignManifestSha256())
                .addValue("localLabCommit", grant.localLabCommit())
                .addValue("receiverCommit", grant.receiverCommit())
                .addValue("ownerGoFormat", grant.format())
                .addValue("officialPermissionEvidenceReference",
                        grant.officialPermissionEvidenceReference(), Types.VARCHAR)
                .addValue("officialPermissionEvidenceSha256",
                        grant.officialPermissionEvidenceSha256(), Types.VARCHAR)
                .addValue("officialPermissionStatus",
                        grant.officialPermissionStatus(), Types.VARCHAR)
                .addValue("providerPermissionAuditReference",
                        grant.providerPermissionAuditReference(), Types.VARCHAR)
                .addValue("providerPermissionAuditSha256",
                        grant.providerPermissionAuditSha256(), Types.VARCHAR)
                .addValue("providerPermissionAuditStatus",
                        grant.providerPermissionAuditStatus(), Types.VARCHAR)
                .addValue("j7TransferGovernanceBasisReference",
                        grant.j7TransferGovernanceBasisReference(), Types.VARCHAR)
                .addValue("j7TransferGovernanceBasisCommit",
                        grant.j7TransferGovernanceBasisCommit(), Types.VARCHAR)
                .addValue("j7TransferGovernanceBasisSha256",
                        grant.j7TransferGovernanceBasisSha256(), Types.VARCHAR)
                .addValue("j7TransferGovernanceBasisStatus",
                        grant.j7TransferGovernanceBasisStatus(), Types.VARCHAR)
                .addValue("receiverQualification", grant.receiverQualification())
                .addValue("senderQualification", grant.senderQualification())
                .addValue("executionActor", grant.executionActor())
                .addValue("canonicalEventId", grant.canonicalEventId())
                .addValue("providerEventId", grant.providerEventId())
                .addValue("exportId", grant.exportId())
                .addValue("fileSha256", grant.fileSha256())
                .addValue("dataSha256", grant.dataSha256())
                .addValue("fileSizeBytes", grant.fileSizeBytes())
                .addValue("schemaId", grant.schemaId())
                .addValue("schemaVersion", grant.schemaVersion())
                .addValue("receiverOrigin", grant.receiverOrigin().toASCIIString())
                .addValue("clientCertificateSha256", grant.clientCertificateSha256())
                .addValue("expectedAttemptNumber", grant.expectedAttemptNumber())
                .addValue("maximumDirectImportCalls", grant.maximumDirectImportCalls())
                .addValue("validFrom", utc(grant.validFrom()))
                .addValue("validUntil", utc(grant.validUntil()))
                .addValue("ownerDecision", grant.ownerDecision())
                .addValue("goUse", grant.goUse())
                .addValue("payloadClass", grant.payloadClass())
                .addValue("validationStatus", grant.validationStatus())
                .addValue("providerDerivedRealPostAuthorized",
                        grant.providerDerivedRealPostAuthorized())
                .addValue("providerNetworkAuthorized", grant.providerNetworkAuthorized())
                .addValue("remoteReceiverNetworkAuthorized",
                        grant.remoteReceiverNetworkAuthorized())
                .addValue("vpsDeploymentAuthorized", grant.vpsDeploymentAuthorized())
                .addValue("productionAuthorized", grant.productionAuthorized())
                .addValue("automaticRetryAuthorized", grant.automaticRetryAuthorized());
    }

    private DeliveryRow createDelivery(
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            Instant startedAt) {
        ManifestRow manifest = findManifest(exportId)
                .orElseThrow(() -> new LedgerException(LedgerFailure.EXPORT_NOT_ELIGIBLE));
        if (!"J7_CANONICAL_EVENT".equals(manifest.exportKind())
                || !"HUMAN_VALIDATED".equals(manifest.validationStatus())) {
            throw new LedgerException(LedgerFailure.EXPORT_NOT_ELIGIBLE);
        }
        if (!manifest.fileSha256().equals(fileSha256)
                || !manifest.dataSha256().equals(dataSha256)) {
            throw new LedgerException(LedgerFailure.IDENTITY_MISMATCH);
        }

        UUID deliveryId = UUID.randomUUID();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("deliveryId", deliveryId)
                .addValue("exportManifestId", manifest.databaseId())
                .addValue("exportId", exportId)
                .addValue("fileSha256", fileSha256)
                .addValue("dataSha256", dataSha256)
                .addValue("fileSizeBytes", manifest.fileSizeBytes())
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("createdAt", utc(startedAt));
        long databaseId = requireIdentifier(
                jdbcTemplate.queryForObject(INSERT_DELIVERY_SQL, parameters, Long.class),
                "delivery insert");
        return new DeliveryRow(
                databaseId,
                deliveryId,
                manifest.databaseId(),
                exportId,
                fileSha256,
                dataSha256,
                manifest.fileSizeBytes(),
                idempotencyKey,
                "1.0",
                DeliveryState.NOT_ATTEMPTED,
                0,
                startedAt,
                startedAt);
    }

    private Optional<ManifestRow> findManifest(UUID exportId) {
        return single(jdbcTemplate.query(
                FIND_EXACT_MANIFEST_SQL,
                new MapSqlParameterSource("exportId", exportId),
                (resultSet, rowNumber) -> new ManifestRow(
                        resultSet.getLong("id"),
                        resultSet.getObject("export_uuid", UUID.class),
                        resultSet.getString("content_sha256"),
                        resultSet.getString("data_sha256"),
                        resultSet.getLong("content_size_bytes"),
                        resultSet.getString("validation_status"),
                        resultSet.getString("export_kind"))));
    }

    private Optional<DeliveryRow> findDeliveryForUpdate(UUID exportId) {
        return single(jdbcTemplate.query(
                FIND_BY_EXPORT_FOR_UPDATE_SQL,
                new MapSqlParameterSource("exportId", exportId),
                deliveryRowMapper));
    }

    private Optional<DeliveryRow> findDeliveryByIdForUpdate(UUID deliveryId) {
        return single(jdbcTemplate.query(
                FIND_BY_DELIVERY_FOR_UPDATE_SQL,
                new MapSqlParameterSource("deliveryId", deliveryId),
                deliveryRowMapper));
    }

    private Optional<AttemptRow> findAttempt(long deliveryDatabaseId, int attemptNumber) {
        return single(jdbcTemplate.query(
                FIND_ATTEMPT_SQL,
                new MapSqlParameterSource()
                        .addValue("deliveryDatabaseId", deliveryDatabaseId)
                        .addValue("attemptNumber", attemptNumber),
                (resultSet, rowNumber) -> new AttemptRow(
                        resultSet.getLong("id"),
                        resultSet.getObject("started_at", OffsetDateTime.class).toInstant(),
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                        resultSet.getObject("result_attempt_id") != null)));
    }

    private Instant databaseClock() {
        OffsetDateTime value = jdbcTemplate.getJdbcTemplate().queryForObject(
                "select clock_timestamp()", OffsetDateTime.class);
        if (value == null) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
        return value.toInstant();
    }

    private Optional<UUID> findGloballyInFlight() {
        return single(jdbcTemplate.query(
                FIND_GLOBAL_IN_FLIGHT_SQL,
                new MapSqlParameterSource(),
                (resultSet, rowNumber) -> resultSet.getObject("delivery_uuid", UUID.class)));
    }

    private void acquireGlobalClaimLock() {
        jdbcTemplate.getJdbcTemplate().query(
                "select pg_advisory_xact_lock(hashtextextended('J7_DELIVERY_GLOBAL', 2700))",
                resultSet -> {
                    if (!resultSet.next()) {
                        throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
                    }
                    return Boolean.TRUE;
                });
    }

    private DeliveryRow mapDelivery(ResultSet resultSet, int rowNumber) throws SQLException {
        long attemptCount = resultSet.getLong("attempt_count");
        if (attemptCount < 0 || attemptCount > Integer.MAX_VALUE) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
        return new DeliveryRow(
                resultSet.getLong("id"),
                resultSet.getObject("delivery_uuid", UUID.class),
                resultSet.getLong("export_manifest_id"),
                resultSet.getObject("export_uuid", UUID.class),
                resultSet.getString("file_sha256"),
                resultSet.getString("data_sha256"),
                resultSet.getLong("file_size_bytes"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("protocol_version"),
                DeliveryState.valueOf(resultSet.getString("current_state")),
                (int) attemptCount,
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("state_changed_at", OffsetDateTime.class).toInstant());
    }

    private static void requireClaim(
            UUID exportId,
            String fileSha256,
            String dataSha256,
            String idempotencyKey,
            int expectedAttemptNumber,
            Instant startedAt) {
        requireExportId(exportId);
        requireSha256(fileSha256, "fileSha256");
        requireSha256(dataSha256, "dataSha256");
        Objects.requireNonNull(startedAt, "startedAt");
        if (!startedAt.equals(startedAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException("startedAt must use PostgreSQL microsecond precision");
        }
        String expectedIdempotencyKey = "j7:" + exportId + ":sha256:" + fileSha256;
        if (!expectedIdempotencyKey.equals(idempotencyKey)) {
            throw new LedgerException(LedgerFailure.IDENTITY_MISMATCH);
        }
        if (expectedAttemptNumber < 1) {
            throw new LedgerException(LedgerFailure.ATTEMPT_ORDINAL_MISMATCH);
        }
    }

    private static void requireCompletion(
            UUID deliveryId,
            int attemptNumber,
            DeliveryState terminalState,
            OptionalInt httpStatus,
            String safeResultCode,
            Optional<String> acknowledgementSha256,
            Optional<UUID> remoteImportId,
            Optional<Instant> acknowledgementReceivedAt,
            Instant completedAt) {
        requireExportId(deliveryId);
        if (attemptNumber < 1
                || terminalState == null
                || !terminalState.isTerminalResult()
                || safeResultCode == null
                || !SAFE_RESULT_CODE.matcher(safeResultCode).matches()) {
            throw new LedgerException(LedgerFailure.INVALID_COMPLETION);
        }
        Objects.requireNonNull(httpStatus, "httpStatus");
        acknowledgementSha256 = Objects.requireNonNull(
                acknowledgementSha256, "acknowledgementSha256");
        remoteImportId = Objects.requireNonNull(remoteImportId, "remoteImportId");
        acknowledgementReceivedAt = Objects.requireNonNull(
                acknowledgementReceivedAt, "acknowledgementReceivedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (httpStatus.isPresent()
                && (httpStatus.getAsInt() < 100 || httpStatus.getAsInt() > 599)) {
            throw new LedgerException(LedgerFailure.INVALID_COMPLETION);
        }

        boolean acknowledgementComplete = acknowledgementSha256
                        .filter(value -> SHA_256.matcher(value).matches())
                        .isPresent()
                && remoteImportId.filter(value -> !NIL_UUID.equals(value)).isPresent()
                && acknowledgementReceivedAt
                        .filter(J7DeliveryReceivedAtPolicy::isExactlyPersistable)
                        .isPresent();
        boolean acknowledgementAbsent = acknowledgementSha256.isEmpty()
                && remoteImportId.isEmpty()
                && acknowledgementReceivedAt.isEmpty();
        boolean validShape = switch (terminalState) {
            case DELIVERED -> acknowledgementComplete
                    && httpStatus.isPresent()
                    && httpStatus.getAsInt() == 201;
            case DUPLICATE_CONFIRMED -> acknowledgementComplete
                    && httpStatus.isPresent()
                    && httpStatus.getAsInt() == 200;
            case REJECTED_TERMINAL -> acknowledgementAbsent
                    && httpStatus.isPresent()
                    && httpStatus.getAsInt() >= 400
                    && httpStatus.getAsInt() <= 499;
            case UNKNOWN_RECONCILIATION_REQUIRED -> acknowledgementAbsent
                    && (httpStatus.isEmpty()
                    || httpStatus.getAsInt() < 400
                    || httpStatus.getAsInt() > 499);
            case NOT_ATTEMPTED, IN_FLIGHT -> false;
        };
        if (!validShape) {
            throw new LedgerException(LedgerFailure.INVALID_COMPLETION);
        }
    }

    private static void requireSameIdentity(
            DeliveryRow row,
            String fileSha256,
            String dataSha256,
            String idempotencyKey) {
        if (!row.fileSha256().equals(fileSha256)
                || !row.dataSha256().equals(dataSha256)
                || !row.idempotencyKey().equals(idempotencyKey)) {
            throw new LedgerException(LedgerFailure.IDENTITY_MISMATCH);
        }
    }

    private static void requireSameGrant(
            J7ProviderDerivedOwnerGo.Grant persisted,
            J7ProviderDerivedOwnerGo.Grant supplied) {
        if (!persisted.equals(supplied)) {
            throw new LedgerException(LedgerFailure.OWNER_GO_IDENTITY_MISMATCH);
        }
    }

    private static void requireCanonicalGrantHash(J7ProviderDerivedOwnerGo.Grant grant) {
        if (!grant.hasValidOwnerDecisionBlockSha256()) {
            throw new LedgerException(LedgerFailure.OWNER_GO_IDENTITY_MISMATCH);
        }
    }

    private static void requireSameReference(
            J7ProviderDerivedOwnerGo.Grant persisted,
            J7ProviderDerivedOwnerGo.Reference supplied) {
        if (!persisted.reference().equals(supplied)) {
            throw new LedgerException(LedgerFailure.OWNER_GO_IDENTITY_MISMATCH);
        }
    }

    private static void requireExportId(UUID value) {
        if (value == null
                || NIL_UUID.equals(value)
                || value.variant() != 2
                || value.version() < 1
                || value.version() > 5) {
            throw new IllegalArgumentException("identifier must be a non-nil UUID");
        }
    }

    private static void requireSha256(String value, String name) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lower-case SHA-256");
        }
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static long requireIdentifier(Long identifier, String operation) {
        if (identifier == null || identifier < 1) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
        return identifier;
    }

    private static void requireOne(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
    }

    private static <T> Optional<T> single(List<T> values) {
        if (values.size() > 1) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
        return values.stream().findFirst();
    }

    private record ManifestRow(
            long databaseId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            long fileSizeBytes,
            String validationStatus,
            String exportKind) {
    }

    private record AttemptRow(
            long databaseId,
            Instant startedAt,
            Instant createdAt,
            boolean resultPresent) {
    }

    private record ProviderGoRow(
            long databaseId,
            J7ProviderDerivedOwnerGo.Grant grant,
            Instant registeredAt,
            Instant observedAt,
            Optional<String> revocationDecisionBlockSha256,
            Optional<Instant> revokedAt,
            Optional<Instant> consumedAt,
            Optional<UUID> deliveryId,
            OptionalInt attemptNumber) {

        private ProviderGoRow {
            if (databaseId < 1) {
                throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
            }
        }

        private J7ProviderDerivedOwnerGo.Status status() {
            return statusAt(observedAt);
        }

        private J7ProviderDerivedOwnerGo.Status statusAt(Instant instant) {
            if (consumedAt.isPresent()) {
                return J7ProviderDerivedOwnerGo.Status.CONSUMED;
            }
            if (revokedAt.isPresent()) {
                return J7ProviderDerivedOwnerGo.Status.REVOKED;
            }
            if (instant.isBefore(grant.validFrom())) {
                return J7ProviderDerivedOwnerGo.Status.NOT_YET_VALID;
            }
            if (!instant.isBefore(grant.validUntil())) {
                return J7ProviderDerivedOwnerGo.Status.EXPIRED;
            }
            return J7ProviderDerivedOwnerGo.Status.AVAILABLE;
        }

        private J7ProviderDerivedOwnerGo.Snapshot snapshot() {
            return new J7ProviderDerivedOwnerGo.Snapshot(
                    grant,
                    status(),
                    registeredAt,
                    observedAt,
                    revocationDecisionBlockSha256,
                    revokedAt,
                    consumedAt,
                    deliveryId,
                    attemptNumber);
        }
    }

    private record DeliveryRow(
            long databaseId,
            UUID deliveryId,
            long exportManifestId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            long fileSizeBytes,
            String idempotencyKey,
            String protocolVersion,
            DeliveryState state,
            int attemptCount,
            Instant createdAt,
            Instant stateChangedAt) {

        private DeliverySnapshot snapshot() {
            return new DeliverySnapshot(
                    deliveryId,
                    exportId,
                    fileSha256,
                    dataSha256,
                    fileSizeBytes,
                    idempotencyKey,
                    protocolVersion,
                    state,
                    attemptCount,
                    createdAt,
                    stateChangedAt);
        }
    }
}
