package com.bettingproject.sofascorelocal.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class J7ProviderOwnerGoV32MigrationIT {

    private static final String V1_FORMAT = "J7_PROVIDER_DERIVED_OWNER_GO_V1";
    private static final String V2_FORMAT = "J7_PROVIDER_DERIVED_OWNER_GO_V2";
    private static final String GOVERNANCE_REFERENCE =
            "ADR-SS-003-optional-integration-topology.md";
    private static final String GOVERNANCE_COMMIT =
            "e1ec9936467dd570f7ed00c51227c8e7d5a35945";
    private static final String GOVERNANCE_SHA256 =
            "ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f";
    private static final String GOVERNANCE_STATUS =
            "ADR_ACCEPTED_NO_EXECUTION_AUTHORITY";
    private static final DateTimeFormatter OWNER_GO_INSTANT =
            new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("sofascore_local_lab")
            .withUsername("sofascore_lab")
            .withPassword("integration-test-only");

    @Test
    void upgradesV31ToV32WithoutRewritingLegacyRowsHashesOrTheV1Function() {
        DriverManagerDataSource dataSource = isolatedDatabase("v32_upgrade");
        Flyway flywayV31 = flyway(dataSource, "31");

        assertThat(flywayV31.migrate().migrationsExecuted).isEqualTo(31);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        ExportEvidence export = insertProviderValidatedExport(jdbc, 32_001);
        Map<String, Object> legacy = v1Grant(export, 32_001);
        insertV31Grant(namedJdbc, legacy);

        Map<String, Object> v31History = jdbc.queryForMap("""
                select installed_rank, version, description, type, script, checksum, success
                from flyway_schema_history
                where version = '31'
                """);
        String v1FunctionBefore = functionDefinition(
                jdbc, "j7_provider_owner_go_canonical_block");
        String canonicalBefore = jdbc.queryForObject("""
                select j7_provider_owner_go_canonical_block(owner_go)
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, String.class, legacy.get("goId"));
        String legacyJsonBefore = jdbc.queryForObject("""
                select to_jsonb(owner_go)::text
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, String.class, legacy.get("goId"));
        Map<String, Object> physicalIdentityBefore = jdbc.queryForMap("""
                select ctid::text as ctid, xmin::text as xmin
                from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, legacy.get("goId"));
        Long relationFileNodeBefore = jdbc.queryForObject("""
                select pg_relation_filenode(
                    'j7_provider_delivery_owner_go_grant'::regclass)
                """, Long.class);
        String storedHashBefore = jdbc.queryForObject("""
                select owner_decision_block_sha256
                from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, String.class, legacy.get("goId"));

        Flyway flywayV32 = flyway(dataSource, "32");
        assertThat(flywayV32.migrate().migrationsExecuted).isOne();
        assertThat(flywayV32.info().current().getVersion().getVersion()).isEqualTo("32");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version is not null",
                Long.class)).isEqualTo(32L);
        assertThat(jdbc.queryForMap("""
                select installed_rank, version, description, type, script, checksum, success
                from flyway_schema_history
                where version = '31'
                """)).containsExactlyInAnyOrderEntriesOf(v31History);

        assertThat(functionDefinition(jdbc, "j7_provider_owner_go_canonical_block"))
                .isEqualTo(v1FunctionBefore);
        assertThat(jdbc.queryForObject("""
                select j7_provider_owner_go_canonical_block(owner_go)
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, String.class, legacy.get("goId"))).isEqualTo(canonicalBefore);
        assertThat(jdbc.queryForObject("""
                select j7_provider_owner_go_canonical_block_for_format(owner_go)
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, String.class, legacy.get("goId"))).isEqualTo(canonicalBefore);
        assertThat(jdbc.queryForObject("""
                select owner_decision_block_sha256
                from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, String.class, legacy.get("goId"))).isEqualTo(storedHashBefore);
        assertThat(jdbc.queryForObject("""
                select pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
                    j7_provider_owner_go_canonical_block_for_format(owner_go), 'UTF8')), 'hex')
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, String.class, legacy.get("goId"))).isEqualTo(storedHashBefore);

        String legacyJsonAfter = jdbc.queryForObject("""
                select (to_jsonb(owner_go)
                    - 'owner_go_format'
                    - 'provider_permission_audit_reference'
                    - 'provider_permission_audit_sha256'
                    - 'provider_permission_audit_status'
                    - 'j7_transfer_governance_basis_reference'
                    - 'j7_transfer_governance_basis_commit'
                    - 'j7_transfer_governance_basis_sha256'
                    - 'j7_transfer_governance_basis_status')::text
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """, String.class, legacy.get("goId"));
        assertThat(legacyJsonAfter).isEqualTo(legacyJsonBefore);
        assertThat(jdbc.queryForMap("""
                select ctid::text as ctid, xmin::text as xmin
                from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, legacy.get("goId")))
                .containsExactlyInAnyOrderEntriesOf(physicalIdentityBefore);
        assertThat(jdbc.queryForObject("""
                select pg_relation_filenode(
                    'j7_provider_delivery_owner_go_grant'::regclass)
                """, Long.class)).isEqualTo(relationFileNodeBefore);
        assertThat(jdbc.queryForMap("""
                select owner_go_format,
                       provider_permission_audit_reference,
                       provider_permission_audit_sha256,
                       provider_permission_audit_status,
                       j7_transfer_governance_basis_reference,
                       j7_transfer_governance_basis_commit,
                       j7_transfer_governance_basis_sha256,
                       j7_transfer_governance_basis_status
                from j7_provider_delivery_owner_go_grant
                where go_uuid = ?
                """, legacy.get("goId")))
                .containsEntry("owner_go_format", V1_FORMAT)
                .containsEntry("provider_permission_audit_reference", null)
                .containsEntry("provider_permission_audit_sha256", null)
                .containsEntry("provider_permission_audit_status", null)
                .containsEntry("j7_transfer_governance_basis_reference", null)
                .containsEntry("j7_transfer_governance_basis_commit", null)
                .containsEntry("j7_transfer_governance_basis_sha256", null)
                .containsEntry("j7_transfer_governance_basis_status", null);
        assertThat(jdbc.queryForObject("""
                select column_default
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'j7_provider_delivery_owner_go_grant'
                  and column_name = 'owner_go_format'
                """, String.class)).isNull();
    }

    @Test
    void freshV32StrictlyDispatchesV1AndV2AndRejectsEveryGovernanceDivergence() {
        DriverManagerDataSource dataSource = isolatedDatabase("v32_fresh");
        Flyway flyway = flyway(dataSource, "32");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(32);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        ExportEvidence export = insertProviderValidatedExport(jdbc, 32_101);

        Map<String, Object> v1 = v1Grant(export, 32_101);
        insertV32Grant(namedJdbc, v1);
        assertExactCanonicalDispatch(jdbc, v1);

        Map<String, Object> notEvidenced = v2Grant(
                export, 32_102, "NOT_EVIDENCED");
        insertV32Grant(namedJdbc, notEvidenced);
        assertExactCanonicalDispatch(jdbc, notEvidenced);

        Map<String, Object> compatible = v2Grant(
                export, 32_103, "EVIDENCED_COMPATIBLE");
        insertV32Grant(namedJdbc, compatible);
        assertExactCanonicalDispatch(jdbc, compatible);

        assertThat(jdbc.queryForList("""
                select owner_go_format, provider_permission_audit_status,
                       j7_transfer_governance_basis_reference,
                       j7_transfer_governance_basis_commit,
                       j7_transfer_governance_basis_sha256,
                       j7_transfer_governance_basis_status
                from j7_provider_delivery_owner_go_grant
                where owner_go_format = 'J7_PROVIDER_DERIVED_OWNER_GO_V2'
                order by provider_permission_audit_status desc
                """))
                .allSatisfy(row -> assertThat(row)
                        .containsEntry("j7_transfer_governance_basis_reference",
                                GOVERNANCE_REFERENCE)
                        .containsEntry("j7_transfer_governance_basis_commit",
                                GOVERNANCE_COMMIT)
                        .containsEntry("j7_transfer_governance_basis_sha256",
                                GOVERNANCE_SHA256)
                        .containsEntry("j7_transfer_governance_basis_status",
                                GOVERNANCE_STATUS));

        assertRejected(namedJdbc, v1Grant(export, 32_110), parameters ->
                parameters.put("officialPermissionStatus", "NOT_EVIDENCED"));
        assertRejected(namedJdbc, v1Grant(export, 32_111), parameters ->
                parameters.put("providerPermissionAuditStatus", "NOT_EVIDENCED"));
        assertRejected(namedJdbc, v2Grant(export, 32_112, "NOT_EVIDENCED"), parameters ->
                parameters.put("officialPermissionStatus", "EVIDENCED_COMPATIBLE"));
        assertRejected(namedJdbc, v2Grant(export, 32_113, "EVIDENCED_INCOMPATIBLE"),
                parameters -> { });
        assertRejected(namedJdbc, v2Grant(export, 32_114, "UNKNOWN"),
                parameters -> { });
        assertRejected(namedJdbc, v2Grant(export, 32_115, "NOT_EVIDENCED"), parameters ->
                parameters.put("providerPermissionAuditReference", "docs/validation/../bad.md"));
        assertRejected(namedJdbc, v2Grant(export, 32_116, "NOT_EVIDENCED"), parameters ->
                parameters.put("providerPermissionAuditSha256", "A".repeat(64)));
        assertRejected(namedJdbc, v2Grant(export, 32_124, "NOT_EVIDENCED"), parameters ->
                parameters.put("governanceReference", "docs/validation/another-adr.md"));
        assertRejected(namedJdbc, v2Grant(export, 32_125, "NOT_EVIDENCED"), parameters ->
                parameters.put("governanceCommit", "f".repeat(40)));
        assertRejected(namedJdbc, v2Grant(export, 32_126, "NOT_EVIDENCED"), parameters ->
                parameters.put("governanceSha256", "f".repeat(64)));
        assertRejected(namedJdbc, v2Grant(export, 32_127, "NOT_EVIDENCED"), parameters ->
                parameters.put("governanceStatus", "ADR_ACCEPTED_EXECUTION_AUTHORITY"));
        assertRejected(namedJdbc, v2Grant(export, 32_128, "NOT_EVIDENCED"), parameters ->
                parameters.put("providerDerivedRealPostAuthorized", false));

        jdbc.execute("""
                alter table j7_provider_delivery_owner_go_grant
                disable trigger j7_provider_owner_go_grant_insert_guard
                """);
        try {
            assertRejected(namedJdbc, v2Grant(export, 32_130, "NOT_EVIDENCED"), parameters ->
                    parameters.put("providerPermissionAuditReference", null));
            assertRejected(namedJdbc, v2Grant(export, 32_131, "NOT_EVIDENCED"), parameters ->
                    parameters.put("providerPermissionAuditSha256", null));
            assertRejected(namedJdbc, v2Grant(export, 32_132, "NOT_EVIDENCED"), parameters ->
                    parameters.put("providerPermissionAuditStatus", null));
            assertRejected(namedJdbc, v2Grant(export, 32_133, "NOT_EVIDENCED"), parameters ->
                    parameters.put("governanceReference", null));
            assertRejected(namedJdbc, v2Grant(export, 32_134, "NOT_EVIDENCED"), parameters ->
                    parameters.put("governanceCommit", null));
            assertRejected(namedJdbc, v2Grant(export, 32_135, "NOT_EVIDENCED"), parameters ->
                    parameters.put("governanceSha256", null));
            assertRejected(namedJdbc, v2Grant(export, 32_136, "NOT_EVIDENCED"), parameters ->
                    parameters.put("governanceStatus", null));
        }
        finally {
            jdbc.execute("""
                    alter table j7_provider_delivery_owner_go_grant
                    enable trigger j7_provider_owner_go_grant_insert_guard
                    """);
        }

        Map<String, Object> unknownFormat = v2Grant(export, 32_129, "NOT_EVIDENCED");
        unknownFormat.put("format", "J7_PROVIDER_DERIVED_OWNER_GO_V3");
        unknownFormat.put("ownerDecisionBlockSha256", "f".repeat(64));
        assertThatThrownBy(() -> insertV32Grant(namedJdbc, unknownFormat))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("provider-derived owner-go format is unsupported");
        assertThatThrownBy(() -> insertV31Grant(
                namedJdbc, v1Grant(export, 32_137)))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("provider-derived owner-go format is unsupported");

        assertHostileSearchPathCannotShadowV2OrItsDispatcher(
                dataSource, jdbc, namedJdbc, export);

        assertThat(jdbc.queryForObject(
                "select count(*) from j7_provider_delivery_owner_go_grant",
                Long.class)).isEqualTo(4L);
    }

    private static void assertHostileSearchPathCannotShadowV2OrItsDispatcher(
            DriverManagerDataSource dataSource,
            JdbcTemplate jdbc,
            NamedParameterJdbcTemplate namedJdbc,
            ExportEvidence export) {
        assertThat(functionConfiguration(
                jdbc, "j7_provider_owner_go_canonical_block_v2"))
                .isEqualTo("search_path=pg_catalog");
        assertThat(functionConfiguration(
                jdbc, "j7_provider_owner_go_canonical_block_for_format"))
                .isEqualTo("search_path=pg_catalog, public");
        assertThat(functionConfiguration(
                jdbc, "guard_j7_provider_owner_go_grant_insert"))
                .isEqualTo("search_path=pg_catalog, public");

        String hostileSchema = "v32_hostile_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
        assertThat(hostileSchema).matches("[a-z0-9_]+");
        jdbc.execute("create schema " + hostileSchema);
        try {
            jdbc.execute("""
                    create function %s.j7_provider_owner_go_canonical_block_v2(
                        owner_go public.j7_provider_delivery_owner_go_grant)
                    returns text
                    language sql
                    immutable
                    as $hostile$
                        select 'HOSTILE_V2_CANONICAL_BLOCK'::text
                    $hostile$
                    """.formatted(hostileSchema));
            jdbc.execute("""
                    create function %s.j7_provider_owner_go_canonical_block_for_format(
                        owner_go public.j7_provider_delivery_owner_go_grant)
                    returns text
                    language sql
                    immutable
                    as $hostile$
                        select 'HOSTILE_DISPATCHED_CANONICAL_BLOCK'::text
                    $hostile$
                    """.formatted(hostileSchema));

            TransactionTemplate transaction = new TransactionTemplate(
                    new JdbcTransactionManager(dataSource));
            Map<String, Object> valid = v2Grant(export, 32_140, "NOT_EVIDENCED");
            Map<String, Object> hostileHash = new HashMap<>(valid);
            hostileHash.put("ownerDecisionBlockSha256",
                    sha256("HOSTILE_DISPATCHED_CANONICAL_BLOCK"));
            assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                jdbc.execute("set local search_path = "
                        + hostileSchema + ", public, pg_catalog");
                insertV32Grant(namedJdbc, hostileHash);
            })).isInstanceOf(DataAccessException.class);

            transaction.executeWithoutResult(ignored -> {
                jdbc.execute("set local search_path = "
                        + hostileSchema + ", public, pg_catalog");
                insertV32Grant(namedJdbc, valid);
            });
            assertExactCanonicalDispatch(jdbc, valid);
        }
        finally {
            jdbc.execute("drop schema " + hostileSchema + " cascade");
        }
    }

    private static DriverManagerDataSource isolatedDatabase(String prefix) {
        String database = prefix + "_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
        assertThat(database).matches("[a-z0-9_]+");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("create database " + database);

        String sourceUrl = POSTGRES.getJdbcUrl();
        int queryIndex = sourceUrl.indexOf('?');
        String query = queryIndex < 0 ? "" : sourceUrl.substring(queryIndex);
        String path = queryIndex < 0 ? sourceUrl : sourceUrl.substring(0, queryIndex);
        String databaseUrl = path.substring(0, path.lastIndexOf('/') + 1) + database + query;
        return new DriverManagerDataSource(
                databaseUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target))
                .load();
    }

    private static ExportEvidence insertProviderValidatedExport(
            JdbcTemplate jdbc,
            long sequence) {
        UUID canonicalEventId = UUID.nameUUIDFromBytes(
                ("v32-event-" + sequence).getBytes(StandardCharsets.UTF_8));
        UUID exportId = UUID.nameUUIDFromBytes(
                ("v32-export-" + sequence).getBytes(StandardCharsets.UTF_8));
        long providerEventId = sequence;
        String dataSha256 = sha256("data-" + sequence);
        String sourceSetSha256 = sha256("sources-" + sequence);
        String candidateSha256 = sha256("candidate-" + sequence);
        String fileSha256 = sha256("file-" + sequence);
        jdbc.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, providerEventId);
        Long snapshotId = jdbc.queryForObject("""
                insert into provider_snapshot (
                    provider, logical_endpoint, request_key,
                    requested_at, received_at, http_status, content_type,
                    latency_ms, payload_sha256, parser_version, schema_status
                ) values (
                    'SOFASCORE', 'EVENT_DETAILS', ?,
                    '2026-09-04T08:00:00Z', '2026-09-04T08:00:01Z',
                    200, 'application/json', 1000, ?, 'v32-test', 'PARSED'
                ) returning id
                """, Long.class, "v32-owner-go:" + sequence,
                sha256("snapshot-" + sequence));
        assertThat(snapshotId).isNotNull();
        String sources = """
                [
                  {"component":"EVENT_STATE","availability":"PRESENT","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":%d},
                  {"component":"EVENT_DETAILS","availability":"PRESENT","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":%d},
                  {"component":"EVENT_STATISTICS","availability":"UNAVAILABLE","sourceKind":"PROVIDER_SNAPSHOT","snapshotId":%d},
                  {"component":"EVENT_INCIDENTS","availability":"MISSING","sourceKind":null,"snapshotId":null},
                  {"component":"EVENT_LINEUPS","availability":"MISSING","sourceKind":null,"snapshotId":null}
                ]
                """.formatted(snapshotId, snapshotId, snapshotId);
        String candidatePath = "j7-" + canonicalEventId + "-" + exportId
                + ".candidate.json";
        jdbc.update("""
                insert into export_manifest (
                    export_kind, export_uuid, canonical_event_id, schema_id,
                    schema_version, generated_at, data_sha256, source_set_sha256,
                    candidate_content_sha256, content_size_bytes, source_observations,
                    export_path, content_sha256, validation_status,
                    source_snapshot_ids, warnings
                ) values (
                    'J7_CANONICAL_EVENT', ?, ?,
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1',
                    '1.0.0', '2026-09-04T08:00:00Z', ?, ?, ?, 1024,
                    cast(? as jsonb), ?, ?, 'COHERENCE_CHECKED',
                    array[?]::bigint[], '[]'::jsonb
                )
                """, exportId, canonicalEventId, dataSha256, sourceSetSha256,
                candidateSha256, sources, candidatePath, candidateSha256, snapshotId);

        String validatedPath = "j7-" + canonicalEventId + "-" + exportId
                + ".validated.json";
        Instant validatedAt = Instant.parse("2026-09-04T09:00:00Z");
        assertThat(jdbc.update("""
                update export_manifest
                set decision_intent_status = 'HUMAN_VALIDATED',
                    decision_intent_at = ?,
                    decision_intent_reason = null,
                    decision_intent_path = ?,
                    decision_intent_content_sha256 = ?,
                    decision_intent_content_size_bytes = 2048
                where export_uuid = ?
                  and validation_status = 'COHERENCE_CHECKED'
                """, Timestamp.from(validatedAt), validatedPath,
                fileSha256, exportId)).isOne();
        assertThat(jdbc.update("""
                update export_manifest
                set export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = 2048,
                    validation_status = 'HUMAN_VALIDATED',
                    decided_at = ?,
                    decision_reason = null
                where export_uuid = ?
                """, validatedPath, fileSha256,
                Timestamp.from(validatedAt), exportId)).isOne();
        Long manifestId = jdbc.queryForObject(
                "select id from export_manifest where export_uuid = ?",
                Long.class, exportId);
        assertThat(manifestId).isNotNull();
        return new ExportEvidence(
                manifestId, canonicalEventId, providerEventId,
                exportId, fileSha256, dataSha256);
    }

    private static Map<String, Object> v1Grant(ExportEvidence export, long sequence) {
        Map<String, Object> parameters = commonGrant(export, sequence);
        parameters.put("format", V1_FORMAT);
        parameters.put("officialPermissionEvidenceReference",
                "docs/validation/J9-WO046-PERMISSION-" + sequence + ".md");
        parameters.put("officialPermissionEvidenceSha256",
                sha256("legacy-permission-" + sequence));
        parameters.put("officialPermissionStatus", "EVIDENCED_COMPATIBLE");
        parameters.put("providerPermissionAuditReference", null);
        parameters.put("providerPermissionAuditSha256", null);
        parameters.put("providerPermissionAuditStatus", null);
        parameters.put("governanceReference", null);
        parameters.put("governanceCommit", null);
        parameters.put("governanceSha256", null);
        parameters.put("governanceStatus", null);
        refreshCanonicalHash(parameters);
        return parameters;
    }

    private static Map<String, Object> v2Grant(
            ExportEvidence export,
            long sequence,
            String auditStatus) {
        Map<String, Object> parameters = commonGrant(export, sequence);
        parameters.put("format", V2_FORMAT);
        parameters.put("officialPermissionEvidenceReference", null);
        parameters.put("officialPermissionEvidenceSha256", null);
        parameters.put("officialPermissionStatus", null);
        parameters.put("providerPermissionAuditReference",
                "docs/validation/J9-WO046-PERMISSION-AUDIT-" + sequence + ".md");
        parameters.put("providerPermissionAuditSha256",
                sha256("permission-audit-" + sequence));
        parameters.put("providerPermissionAuditStatus", auditStatus);
        parameters.put("governanceReference", GOVERNANCE_REFERENCE);
        parameters.put("governanceCommit", GOVERNANCE_COMMIT);
        parameters.put("governanceSha256", GOVERNANCE_SHA256);
        parameters.put("governanceStatus", GOVERNANCE_STATUS);
        refreshCanonicalHash(parameters);
        return parameters;
    }

    private static Map<String, Object> commonGrant(ExportEvidence export, long sequence) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("goId", UUID.nameUUIDFromBytes(
                ("v32-go-" + sequence).getBytes(StandardCharsets.UTF_8)));
        parameters.put("workOrder", "WO-SS-20260904-046-v32-migration-test");
        parameters.put("campaignManifestReference",
                "docs/validation/J9-WO046-V32-MANIFEST-" + sequence + ".md");
        parameters.put("campaignManifestSha256", sha256("manifest-" + sequence));
        parameters.put("localLabCommit", sha256("local-" + sequence).substring(0, 40));
        parameters.put("receiverCommit", sha256("receiver-" + sequence).substring(0, 40));
        parameters.put("receiverQualification", "PASS");
        parameters.put("senderQualification", "PASS");
        parameters.put("executionActor", "CODEX_LOCAL_UI");
        parameters.put("exportManifestId", export.manifestId());
        parameters.put("canonicalEventId", export.canonicalEventId());
        parameters.put("providerEventId", export.providerEventId());
        parameters.put("exportId", export.exportId());
        parameters.put("fileSha256", export.fileSha256());
        parameters.put("dataSha256", export.dataSha256());
        parameters.put("fileSizeBytes", 2048L);
        parameters.put("schemaId",
                "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1");
        parameters.put("schemaVersion", "1.0.0");
        parameters.put("receiverOrigin", "https://127.0.0.1:8444");
        parameters.put("clientCertificateSha256", sha256("certificate-" + sequence));
        parameters.put("expectedAttemptNumber", 1);
        parameters.put("maximumDirectImportCalls", 1);
        parameters.put("validFrom", OffsetDateTime.ofInstant(
                Instant.parse("2026-09-04T10:00:00Z"), ZoneOffset.UTC));
        parameters.put("validUntil", OffsetDateTime.ofInstant(
                Instant.parse("2026-09-04T10:30:00Z"), ZoneOffset.UTC));
        parameters.put("ownerDecision", "GRANT");
        parameters.put("goUse", "ONE_TIME");
        parameters.put("payloadClass", "PROVIDER_DERIVED");
        parameters.put("validationStatus", "HUMAN_VALIDATED");
        parameters.put("providerDerivedRealPostAuthorized", true);
        parameters.put("providerNetworkAuthorized", false);
        parameters.put("remoteReceiverNetworkAuthorized", false);
        parameters.put("vpsDeploymentAuthorized", false);
        parameters.put("productionAuthorized", false);
        parameters.put("automaticRetryAuthorized", false);
        return parameters;
    }

    private static void insertV31Grant(
            NamedParameterJdbcTemplate jdbc,
            Map<String, Object> parameters) {
        jdbc.update("""
                insert into j7_provider_delivery_owner_go_grant (
                    go_uuid, owner_decision_block_sha256, work_order,
                    campaign_manifest_reference, campaign_manifest_sha256,
                    local_lab_commit, receiver_commit,
                    official_permission_evidence_reference,
                    official_permission_evidence_sha256,
                    official_permission_status, receiver_qualification,
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
                    :officialPermissionEvidenceReference,
                    :officialPermissionEvidenceSha256,
                    :officialPermissionStatus, :receiverQualification,
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
                """, new MapSqlParameterSource(parameters));
    }

    private static void insertV32Grant(
            NamedParameterJdbcTemplate jdbc,
            Map<String, Object> parameters) {
        jdbc.update("""
                insert into j7_provider_delivery_owner_go_grant (
                    go_uuid, owner_decision_block_sha256, work_order,
                    campaign_manifest_reference, campaign_manifest_sha256,
                    local_lab_commit, receiver_commit, owner_go_format,
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
                    receiver_qualification, sender_qualification,
                    execution_actor, export_manifest_id,
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
                    :localLabCommit, :receiverCommit, :format,
                    :officialPermissionEvidenceReference,
                    :officialPermissionEvidenceSha256,
                    :officialPermissionStatus,
                    :providerPermissionAuditReference,
                    :providerPermissionAuditSha256,
                    :providerPermissionAuditStatus,
                    :governanceReference, :governanceCommit,
                    :governanceSha256, :governanceStatus,
                    :receiverQualification, :senderQualification,
                    :executionActor, :exportManifestId,
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
                """, new MapSqlParameterSource(parameters));
    }

    private static void assertExactCanonicalDispatch(
            JdbcTemplate jdbc,
            Map<String, Object> parameters) {
        String function = V1_FORMAT.equals(parameters.get("format"))
                ? "j7_provider_owner_go_canonical_block"
                : "j7_provider_owner_go_canonical_block_v2";
        Map<String, Object> evidence = jdbc.queryForMap("""
                select %s(owner_go) as versioned_block,
                       j7_provider_owner_go_canonical_block_for_format(owner_go)
                           as dispatched_block,
                       owner_go.owner_decision_block_sha256 as stored_hash,
                       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
                           j7_provider_owner_go_canonical_block_for_format(owner_go),
                           'UTF8')), 'hex') as computed_hash
                from j7_provider_delivery_owner_go_grant owner_go
                where owner_go.go_uuid = ?
                """.formatted(function), parameters.get("goId"));
        String expected = canonicalBlock(parameters);
        assertThat(evidence)
                .containsEntry("versioned_block", expected)
                .containsEntry("dispatched_block", expected)
                .containsEntry("stored_hash", parameters.get("ownerDecisionBlockSha256"))
                .containsEntry("computed_hash", parameters.get("ownerDecisionBlockSha256"));
        assertThat(expected).doesNotContain("\r").endsWith("\n");
    }

    private static void assertRejected(
            NamedParameterJdbcTemplate jdbc,
            Map<String, Object> source,
            ParameterMutation mutation) {
        Map<String, Object> parameters = new HashMap<>(source);
        mutation.apply(parameters);
        refreshCanonicalHash(parameters);
        assertThatThrownBy(() -> insertV32Grant(jdbc, parameters))
                .isInstanceOf(DataAccessException.class);
    }

    private static void refreshCanonicalHash(Map<String, Object> parameters) {
        parameters.put("ownerDecisionBlockSha256", sha256(canonicalBlock(parameters)));
    }

    private static String canonicalBlock(Map<String, Object> parameters) {
        String format = (String) parameters.get("format");
        List<String> lines = new ArrayList<>();
        lines.add("FORMAT=" + format);
        lines.add("GO_ID=" + parameters.get("goId"));
        lines.add("WORK_ORDER=" + parameters.get("workOrder"));
        lines.add("CAMPAIGN_MANIFEST_REFERENCE="
                + parameters.get("campaignManifestReference"));
        lines.add("CAMPAIGN_MANIFEST_SHA256=" + parameters.get("campaignManifestSha256"));
        lines.add("LOCAL_LAB_COMMIT=" + parameters.get("localLabCommit"));
        lines.add("RECEIVER_COMMIT=" + parameters.get("receiverCommit"));
        if (V1_FORMAT.equals(format)) {
            lines.add("OFFICIAL_PERMISSION_EVIDENCE_REFERENCE="
                    + parameters.get("officialPermissionEvidenceReference"));
            lines.add("OFFICIAL_PERMISSION_EVIDENCE_SHA256="
                    + parameters.get("officialPermissionEvidenceSha256"));
            lines.add("OFFICIAL_PERMISSION_STATUS="
                    + parameters.get("officialPermissionStatus"));
        }
        else if (V2_FORMAT.equals(format)) {
            lines.add("PROVIDER_PERMISSION_AUDIT_REFERENCE="
                    + parameters.get("providerPermissionAuditReference"));
            lines.add("PROVIDER_PERMISSION_AUDIT_SHA256="
                    + parameters.get("providerPermissionAuditSha256"));
            lines.add("PROVIDER_PERMISSION_AUDIT_STATUS="
                    + parameters.get("providerPermissionAuditStatus"));
            lines.add("J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE="
                    + parameters.get("governanceReference"));
            lines.add("J7_TRANSFER_GOVERNANCE_BASIS_COMMIT="
                    + parameters.get("governanceCommit"));
            lines.add("J7_TRANSFER_GOVERNANCE_BASIS_SHA256="
                    + parameters.get("governanceSha256"));
            lines.add("J7_TRANSFER_GOVERNANCE_BASIS_STATUS="
                    + parameters.get("governanceStatus"));
        }
        else {
            throw new IllegalArgumentException("unsupported test format");
        }
        lines.add("RECEIVER_QUALIFICATION=" + parameters.get("receiverQualification"));
        lines.add("SENDER_QUALIFICATION=" + parameters.get("senderQualification"));
        lines.add("EXECUTION_ACTOR=" + parameters.get("executionActor"));
        lines.add("CANONICAL_EVENT_ID=" + parameters.get("canonicalEventId"));
        lines.add("PROVIDER_EVENT_ID=" + parameters.get("providerEventId"));
        lines.add("EXPORT_ID=" + parameters.get("exportId"));
        lines.add("FILE_SHA256=" + parameters.get("fileSha256"));
        lines.add("DATA_SHA256=" + parameters.get("dataSha256"));
        lines.add("FILE_SIZE_BYTES=" + parameters.get("fileSizeBytes"));
        lines.add("SCHEMA_ID=" + parameters.get("schemaId"));
        lines.add("SCHEMA_VERSION=" + parameters.get("schemaVersion"));
        lines.add("RECEIVER_ORIGIN=" + parameters.get("receiverOrigin"));
        lines.add("CLIENT_CERTIFICATE_SHA256="
                + parameters.get("clientCertificateSha256"));
        lines.add("EXPECTED_ATTEMPT_NUMBER=" + parameters.get("expectedAttemptNumber"));
        lines.add("MAXIMUM_DIRECT_IMPORT_CALLS="
                + parameters.get("maximumDirectImportCalls"));
        lines.add("VALID_FROM=" + formatInstant(parameters.get("validFrom")));
        lines.add("VALID_UNTIL=" + formatInstant(parameters.get("validUntil")));
        lines.add("OWNER_DECISION=" + parameters.get("ownerDecision"));
        lines.add("GO_USE=" + parameters.get("goUse"));
        lines.add("PAYLOAD_CLASS=" + parameters.get("payloadClass"));
        lines.add("VALIDATION_STATUS=" + parameters.get("validationStatus"));
        lines.add("PROVIDER_DERIVED_REAL_POST_AUTHORIZED="
                + yesNo(parameters.get("providerDerivedRealPostAuthorized")));
        lines.add("PROVIDER_NETWORK_AUTHORIZED="
                + yesNo(parameters.get("providerNetworkAuthorized")));
        lines.add("REMOTE_RECEIVER_NETWORK_AUTHORIZED="
                + yesNo(parameters.get("remoteReceiverNetworkAuthorized")));
        lines.add("VPS_DEPLOYMENT_AUTHORIZED="
                + yesNo(parameters.get("vpsDeploymentAuthorized")));
        lines.add("PRODUCTION_AUTHORIZED="
                + yesNo(parameters.get("productionAuthorized")));
        lines.add("AUTOMATIC_RETRY_AUTHORIZED="
                + yesNo(parameters.get("automaticRetryAuthorized")));
        return String.join("\n", lines) + "\n";
    }

    private static String formatInstant(Object value) {
        return OWNER_GO_INSTANT.format(((OffsetDateTime) value).toInstant());
    }

    private static String yesNo(Object value) {
        return Boolean.TRUE.equals(value) ? "YES" : "NO";
    }

    private static String functionDefinition(JdbcTemplate jdbc, String function) {
        return jdbc.queryForObject("""
                select pg_get_functiondef(routine.oid)
                from pg_proc routine
                join pg_namespace namespace on namespace.oid = routine.pronamespace
                where namespace.nspname = current_schema()
                  and routine.proname = ?
                """, String.class, function);
    }

    private static String functionConfiguration(JdbcTemplate jdbc, String function) {
        return jdbc.queryForObject("""
                select pg_catalog.array_to_string(routine.proconfig, ',')
                from pg_proc routine
                join pg_namespace namespace on namespace.oid = routine.pronamespace
                where namespace.nspname = current_schema()
                  and routine.proname = ?
                """, String.class, function);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    private interface ParameterMutation {

        void apply(Map<String, Object> parameters);
    }

    private record ExportEvidence(
            long manifestId,
            UUID canonicalEventId,
            long providerEventId,
            UUID exportId,
            String fileSha256,
            String dataSha256) {
    }
}
