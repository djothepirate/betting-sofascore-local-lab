package com.bettingproject.sofascorelocal.adapter.persistence.delivery;

import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
                started_at
            ) values (
                :attemptId,
                :deliveryDatabaseId,
                :attemptNumber,
                :startedAt
            )
            returning id
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
                    .addValue("startedAt", utc(startedAt));
            requireIdentifier(
                    jdbcTemplate.queryForObject(
                            INSERT_ATTEMPT_SQL, attemptParameters, Long.class),
                    "attempt insert");

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
        catch (LedgerException | IllegalArgumentException exception) {
            throw exception;
        }
        catch (DataAccessException | ArithmeticException exception) {
            throw new LedgerException(LedgerFailure.STORAGE_UNAVAILABLE);
        }
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
        if (acknowledgementReceivedAt
                .filter(receivedAt -> receivedAt.isBefore(attempt.startedAt())
                        || receivedAt.isAfter(completedAt))
                .isPresent()) {
            throw new LedgerException(LedgerFailure.INVALID_COMPLETION);
        }

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
                && acknowledgementReceivedAt.isPresent();
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
