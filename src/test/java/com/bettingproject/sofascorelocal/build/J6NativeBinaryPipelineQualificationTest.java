package com.bettingproject.sofascorelocal.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class J6NativeBinaryPipelineQualificationTest {

    private static final Duration OFFLINE_TIMEOUT = Duration.ofSeconds(180);
    private static final Duration DOCKER_TIMEOUT = Duration.ofMinutes(10);

    private record QualificationResult(boolean finished, int exitCode, String evidence) {
    }

    @Test
    void retentionAcceptsOnlyAV44QualifiedManifestWithoutRunningNativeTools()
            throws IOException {
        String retention = Files.readString(
                Path.of("").toAbsolutePath().normalize()
                        .resolve("scripts/Invoke-J6Retention.ps1"),
                StandardCharsets.UTF_8);

        assertThat(retention)
                .contains(
                        "$manifest.source.flywayVersion.ToString() -cne '44'",
                        "valid Flyway V44 raw-payload, J8, J7 and quiescent live ledger restore",
                        "'providerResilienceStateCount'", "'providerDepartureReservationCount'",
                        "'providerDepartureCompletionCount'", "'providerResilienceEventCount'",
                        "'liveAttemptTransportDiagnosticCount'", "'liveCampaignDiagnosticCount'",
                        "[long]$manifest.source.providerResilienceStateCount -ne 1",
                        "'SOFASCORE_LIVE_ENABLED'", "'providerGuardState'", "'liveLedgerSha256'")
                .doesNotContain(
                        "$manifest.source.flywayVersion.ToString() -cne '41'",
                        "$manifest.source.flywayVersion.ToString() -cne '40'",
                        "$manifest.source.flywayVersion.ToString() -cne '31'",
                        "valid Flyway V31 raw-payload, J8 evidence and metadata-only J7 delivery and owner-go restore");
    }

    @Test
    void backupRestoreScriptUsesTheFailClosedSupervisorForBothBinaryPipelines() throws IOException {
        Path repository = Path.of("").toAbsolutePath().normalize();
        String script = Files.readString(
                repository.resolve("scripts/Backup-Restore-J6.ps1"),
                StandardCharsets.UTF_8);
        String module = Files.readString(
                repository.resolve("scripts/J6-NativeBinaryPipeline.psm1"),
                StandardCharsets.UTF_8);
        String qualification = Files.readString(
                repository.resolve("scripts/Invoke-J6BackupRestoreLoopbackQualification.ps1"),
                StandardCharsets.UTF_8);
        String nativeHost = Files.readString(
                repository.resolve("scripts/J6-NativeProcessHost.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script)
                .contains(
                        "Import-Module -Name $pipelineModulePath -Force",
                        "-Phase BACKUP_ENCRYPTION",
                        "-Phase RESTORE_DECRYPTION",
                        "PGAPPNAME=$backupApplicationName",
                        "PGAPPNAME=$restoreApplicationName",
                        "Confirm-J6OwnedPostgresSessionCleanup",
                        "New-J6ConsoleCancellationRegistration",
                        "-CancellationToken $operatorCancellation.Token",
                        "Invoke-J6BoundedDockerCleanupCommand",
                        "[int]$PipelineCleanupTimeoutMilliseconds = 5000",
                        "[int]$PostgresCleanupTimeoutMilliseconds = 10000",
                        "[string]$DockerPath",
                        "J6_NATIVE_PROCESS_CLEANUP_TIMEOUT_MILLISECONDS=",
                        "J6_POSTGRES_CLEANUP_TIMEOUT_MILLISECONDS=",
                        "--set=ON_ERROR_STOP=1",
                        "ConvertFrom-J6StrictNonNegativeInt64Scalar",
                        "ConvertFrom-J6StrictTerminationEvidenceScalar",
                        "ConvertTo-J6SanitizedInnerException",
                        "Merge-J6PostgresTerminationEvidence",
                        "Resolve-J6PostgresCleanupTimeoutClassification",
                        "Globalization.NumberStyles]::None",
                        "POSTGRES_OBSERVATION_TIMEOUT",
                        "POSTGRES_SESSION_REMAINING",
                        "POSTGRES_SCALAR_OUTPUT_INVALID",
                        "POSTGRES_SQL_COMMAND_NONZERO_EXIT",
                        "then exit 86; fi",
                        "TargetedSessionCountNow",
                        "J6_POSTGRES_SESSION_TARGETED_ATTEMPT_COUNT=",
                        "J6_POSTGRES_SESSION_SUCCESSFUL_TERMINATION_SIGNAL_COUNT=",
                        "UNCONFIRMED_AFTER_TERMINATION",
                        "J6_CLEANUP_CAUSES_PRESERVED",
                        "J6_FILE_CLEANUP_CAUSES_PRESERVED",
                        "J6_DOCKER_EXECUTABLE_IDENTITY=AUTHENTICODE_DOCKER_INC",
                        "Get-AuthenticodeSignature",
                        "AggregateException",
                        "if ($sourceFlywayVersion -cne '44')",
                        "Flyway V44 must be applied before the J6 backup/restore qualification.",
                        "$liveLedgerFingerprintSql", "provider_campaign_guard", "$providerGuardState -cne 'FREE'",
                        "J6_POSTGRES_SESSION_CLEANUP_IDEMPOTENT_REUSE=PASS",
                        "dropdb --username \"$POSTGRES_USER\" --force --if-exists",
                        "$manifestStagingPath",
                        "$QualificationInjectCleanupFailureAfterSuccessfulCleanup")
                .doesNotContain(
                        "if ($sourceFlywayVersion -cne '41')",
                        "if ($sourceFlywayVersion -cne '31')",
                        "Flyway V31 must be applied before the J6 backup/restore qualification.",
                        "'pg_dump --username \"$POSTGRES_USER\" --dbname \"$POSTGRES_DB\" --format=custom --no-owner --no-privileges' |",
                        "& $ageExecutable -d $destinationPath |");
        // Each new durable table is counted on both sides and included in the ordered full-row hash.
        for (String table : java.util.List.of("provider_resilience_state", "provider_departure_reservation",
                "provider_departure_completion", "provider_resilience_event", "live_attempt_transport_diagnostic",
                "live_campaign_diagnostic")) {
            assertThat(script).contains(
                    "Invoke-PrimaryScalar -Sql 'select count(*) from " + table + "'",
                    "Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from " + table + "'",
                    "'" + table.toUpperCase(java.util.Locale.ROOT) + "|' || to_jsonb(t)::text from " + table + " t");
        }
        assertThat(module)
                .contains(
                        "CopyToAsync",
                        "Kill($true)",
                        "J6ProcessTreeSnapshot",
                        "J6WindowsKillOnCloseJob",
                        "J6-NativeProcessHost.ps1",
                        "CurrentUserOnly",
                        "J6_NATIVE_TARGET_START_V1",
                        "StartupTimeoutMilliseconds = 10000",
                        "OverallCommandDeadlineUtc",
                        "TargetStartedAtUtcTicks",
                        "J6TargetStartedAtUtcTicks",
                        "J6ActiveProcessesAfterCleanup",
                        "ProducerTargetStartedAtUtcTicks",
                        "ConsumerTargetStartedAtUtcTicks",
                        "ProducerActiveProcessesAfterCleanup",
                        "ConsumerActiveProcessesAfterCleanup",
                        "QualificationStartupHandshakeDelayMilliseconds",
                        "WINDOWS_KILL_ON_JOB_CLOSE",
                        "Console.CancelKeyPress",
                        "Invoke-J6BoundedNativeCommand",
                        "CONSUMER_EARLY_EXIT",
                        "PRODUCER_FAILED",
                        "CLEANUP_UNCONFIRMED",
                        "COMPLETED_TO_EOF");
        assertThat(qualification)
                .contains(
                        "CREATE_NEW_PROCESS_GROUP",
                        "J6_CTRL_BREAK_NEW_CONSOLE_REQUESTED=NO",
                        "J6_RUNTIME_POSTGRES_STRICT_SCALAR_PARSER=PASS",
                        "J6_RUNTIME_POSTGRES_STRICT_TERMINATION_EVIDENCE_PARSER=PASS",
                        "J6_RUNTIME_SANITIZED_INNER_CAUSE=PASS",
                        "J6_RUNTIME_POSTGRES_TERMINATION_EVIDENCE_COUNTERS=PASS",
                        "J6_RUNTIME_POSTGRES_STALE_COUNT_CLASSIFICATION=PASS",
                        "J6_RUNTIME_POSTGRES_STATE_MACHINE_COUNTEREXAMPLES=PASS",
                        "J6_RUNTIME_POSTGRES_IDEMPOTENT_CONFIRMATION=PASS",
                        "J6_RUNTIME_POSTGRES_FAILURE_CLASSIFICATION=PASS",
                        "J6_RUNTIME_PSQL_ON_ERROR_STOP_STATIC_CONTRACT=PASS",
                        "AMBIGUOUS_CROSS_API_GHOST_VISIBILITY",
                        "J6_PROCESS_IDENTITY_STILL_ACTIVE",
                        "J6_PROCESS_GHOST_STATE_TRANSIENT_RECOVERY=",
                        "Invoke-J6BoundedTasklistObservation",
                        "J6_PROCESS_IDENTITY_FINAL_BATCH=PASS",
                        "J6_TASKLIST_OBSERVER_BOUNDED_JOB_CLEANUP=PASS",
                        "LOOPBACK_LISTENER_OBSERVATION_FAILED",
                        "LOOPBACK_APPLICATION_LISTENER_RESIDUAL",
                        "PID_REUSED_NOT_OWNED",
                        "J6_SYNTHETIC_TEMP_ROOT_OWNER_V1",
                        "J6_SYNTHETIC_TEMP_ROOT_POST_DELETE_ABSENCE_UNCONFIRMED",
                        "J6_SYNTHETIC_TEMP_ROOT_DELETE_FAILURE_INJECTED",
                        "J6_SYNTHETIC_TEMP_ROOT_PARENT_AND_GLOB_GATES=PASS",
                        "J6_SYNTHETIC_TEMP_ROOT_REPARSE_GATE=PASS",
                        "J6_SYNTHETIC_TEMP_ROOT_COMBINED_FAILURE_SANITIZED=PASS",
                        "J6_PID_ONLY_TERMINATION_USED=NO")
                .doesNotContain(
                        "Start-Process",
                        "CREATE_NEW_CONSOLE",
                        "AttachConsole",
                        "FreeConsole",
                        "Test-J6ProcessAbsent",
                        "Test-J6ProcessIdentityAbsent",
                        "consumer-failure-child.pid",
                        "exited-root-child.pid",
                        "post-start-failure.pid",
                        "-PipelineCleanupTimeoutMilliseconds 10000",
                        "taskkill",
                        "Stop-Process");
        assertThat(nativeHost)
                .contains(
                        "SetConsoleCtrlHandler",
                        "if (-not $controlGuard.Installed)",
                        "$gate.WaitOne",
                        "$target.Start()",
                        "NamedPipeClientStream",
                        "J6_NATIVE_TARGET_START_V1",
                        "StartupNonce",
                        "QualificationStartupHandshakeDelayMilliseconds")
                .doesNotContain("Start-Process", "CREATE_NEW_CONSOLE");

        int cleanupGate = script.indexOf("if ($cleanupFailures.Count -ne 0)");
        int favorableManifestPromotion = script.indexOf("$manifest.restoreQualified = $true");
        int favorableOutput = script.indexOf("J6_BACKUP_RESULT=QUALIFIED");
        assertThat(cleanupGate).isGreaterThanOrEqualTo(0);
        assertThat(favorableManifestPromotion)
                .as("restoreQualified must only be promoted after the fail-closed cleanup gate")
                .isGreaterThan(cleanupGate);
        assertThat(favorableOutput)
                .as("the favorable result must only be emitted after the cleanup gate")
                .isGreaterThan(cleanupGate);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize();
        QualificationResult result = runQualification(repository, false, OFFLINE_TIMEOUT);
        assertQualificationSucceeded(result, offlineMarkers());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @EnabledIfSystemProperty(named = "j6.docker.qualification", matches = "true")
    void dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize();
        assertThat(repository.resolve(".env"))
                .as("The explicitly enabled Docker qualification requires the ignored local .env")
                .isRegularFile();

        for (int run = 1; run <= 3; run++) {
            QualificationResult result = runQualification(repository, true, DOCKER_TIMEOUT);
            assertQualificationSucceeded(result, dockerMarkers());
        }
    }

    private static QualificationResult runQualification(
            Path repository,
            boolean withDocker,
            Duration timeout) throws Exception {
        Path pwsh = Path.of(System.getenv("ProgramFiles"), "PowerShell", "7", "pwsh.exe");
        Path qualification = repository.resolve(
                "scripts/Invoke-J6BackupRestoreLoopbackQualification.ps1");
        assertThat(pwsh)
                .as("PowerShell 7 is mandatory for the Windows J6 qualification")
                .isRegularFile();
        assertThat(qualification).isRegularFile();

        ProcessBuilder builder = new ProcessBuilder(
                pwsh.toString(),
                "-NoLogo",
                "-NoProfile",
                "-File",
                qualification.toString());
        if (withDocker) {
            Path docker = Path.of(
                    System.getenv("LOCALAPPDATA"),
                    "Programs",
                    "DockerDesktop",
                    "resources",
                    "bin",
                    "docker.exe");
            assertThat(docker)
                    .as("The Docker qualification requires the explicit local Docker CLI")
                    .isRegularFile();
            builder.command().add("-WithDocker");
            builder.command().add("-DockerPath");
            builder.command().add(docker.toString());
        }
        Process process = builder
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read J6 qualification output", exception);
            }
        });

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String evidence = output.get(10, TimeUnit.SECONDS);
        int exitCode = finished ? process.exitValue() : Integer.MIN_VALUE;
        return new QualificationResult(finished, exitCode, evidence);
    }

    private static void assertQualificationSucceeded(
            QualificationResult result,
            String[] requiredMarkers) {
        assertThat(result.finished())
                .as("The bounded J6 qualification exceeded its deadline; markers=%s",
                        sanitizedMarkerTrace(result.evidence()))
                .isTrue();
        assertThat(result.exitCode())
                .as("The bounded J6 qualification returned a non-zero exit code; markers=%s",
                        sanitizedMarkerTrace(result.evidence()))
                .isZero();
        for (String marker : requiredMarkers) {
            assertThat(result.evidence().contains(marker))
                    .as("Missing sanitized J6 qualification marker: %s", marker)
                    .isTrue();
        }
        for (String forbidden : new String[]{
                "SYNTHETIC_SECRET",
                "SYNTHETIC_RAW_",
                "SYNTHETIC_INNER_CAUSE_MUST_NOT_ESCAPE",
                "SYNTHETIC_PRIMARY_DETAIL_MUST_NOT_ESCAPE",
                "SYNTHETIC_TEMP_DETAIL_MUST_NOT_ESCAPE",
                "PROVIDER_ACCESS_PERFORMED=YES",
                "storageState",
                "HAR"}) {
            assertThat(result.evidence().contains(forbidden))
                    .as("Forbidden material appeared in sanitized J6 qualification output: %s", forbidden)
                    .isFalse();
        }
    }

    private static String sanitizedMarkerTrace(String evidence) {
        return evidence.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("J6_") || line.startsWith("PROVIDER_"))
                .limit(160)
                .reduce((left, right) -> left + "|" + right)
                .orElse("NONE");
    }

    private static String[] offlineMarkers() {
        return new String[]{
                "J6_POSTGRES_STRICT_SCALAR_PARSER=PASS",
                "J6_RUNTIME_POSTGRES_STRICT_SCALAR_PARSER=PASS",
                "J6_RUNTIME_POSTGRES_STRICT_TERMINATION_EVIDENCE_PARSER=PASS",
                "J6_RUNTIME_SANITIZED_INNER_CAUSE=PASS",
                "J6_RUNTIME_POSTGRES_EXACT_OWNERSHIP_NAME=PASS",
                "J6_RUNTIME_POSTGRES_TERMINATION_EVIDENCE_COUNTERS=PASS",
                "J6_RUNTIME_POSTGRES_STALE_COUNT_CLASSIFICATION=PASS",
                "J6_RUNTIME_POSTGRES_STATE_MACHINE_COUNTEREXAMPLES=PASS",
                "J6_RUNTIME_POSTGRES_IDEMPOTENT_CONFIRMATION=PASS",
                "J6_RUNTIME_POSTGRES_FAILURE_CLASSIFICATION=PASS",
                "J6_RUNTIME_PSQL_ON_ERROR_STOP_STATIC_CONTRACT=PASS",
                "J6_PROCESS_MULTI_API_CLASSIFIER=PASS",
                "J6_PROCESS_GHOST_STATE_CLASSIFICATION=AMBIGUOUS_CROSS_API_GHOST_VISIBILITY",
                "J6_SYNTHETIC_TEMP_ROOT_NEGATIVE_GATES=PASS",
                "J6_SYNTHETIC_TEMP_ROOT_PARENT_AND_GLOB_GATES=PASS",
                "J6_SYNTHETIC_TEMP_ROOT_REPARSE_GATE=PASS",
                "J6_SYNTHETIC_TEMP_ROOT_COMBINED_FAILURE_SANITIZED=PASS",
                "J6_SYNTHETIC_TEMP_ROOT_DELETE_FAILURE_FAIL_CLOSED=PASS",
                "J6_SYNTHETIC_TEMP_ROOT_SIBLING_SENTINEL_PRESERVED=PASS",
                "J6_PIPELINE_BINARY_NOMINAL=PASS",
                "J6_PIPELINE_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED",
                "J6_PIPELINE_EXITED_ROOT_DESCENDANT_CLEANUP=PASS",
                "J6_PIPELINE_PID_REUSE_OWNERSHIP_FENCE=PASS",
                "J6_PIPELINE_EARLY_PRODUCER_FAILURE=PASS_FAIL_CLOSED",
                "J6_PIPELINE_RESTORE_SYMMETRY=PASS",
                "J6_PIPELINE_PREMATURE_SUCCESS_TRUNCATION=PASS_FAIL_CLOSED",
                "J6_PIPELINE_TIMEOUT=PASS_FAIL_CLOSED",
                "J6_BOUNDED_NATIVE_COMMAND_TARGET_HANDSHAKE=PASS",
                "J6_BOUNDED_NATIVE_COMMAND_JOB_ACTIVE_PROCESS_COUNT_AFTER_CLEANUP=0",
                "J6_BOUNDED_NATIVE_COMMAND_TIMEOUT_CLEANUP=PASS_FAIL_CLOSED",
                "J6_BOUNDED_NATIVE_COMMAND_STARTUP_TIMEOUT=PASS_FAIL_CLOSED",
                "J6_BOUNDED_NATIVE_COMMAND_STARTUP_TIMEOUT_JOB_ACTIVE_PROCESS_COUNT=0",
                "J6_BOUNDED_NATIVE_COMMAND_POST_START_EXCEPTION_CLEANUP=PASS_FAIL_CLOSED",
                "J6_BOUNDED_NATIVE_COMMAND_EXITED_ROOT_DESCENDANT_JOB_CLEANUP=PASS",
                "J6_PIPELINE_COOPERATIVE_CANCELLATION_CLEANUP=PASS",
                "J6_CTRL_BREAK_NEW_CONSOLE_REQUESTED=NO",
                "J6_CONSOLE_CTRL_BREAK_EVENT=PASS",
                "J6_PROCESS_SECONDARY_SNAPSHOT=PASS_AFTER_",
                "J6_PROCESS_IDENTITY_FINAL_BATCH=PASS",
                "J6_TASKLIST_OBSERVER_BOUNDED_JOB_CLEANUP=PASS",
                "J6_PIPELINE_OWNED_IDENTITIES_INACTIVE_MULTI_API=PASS",
                "J6_PID_ONLY_TERMINATION_USED=NO",
                "J6_PIPELINE_RESIDUAL_OWNED_PROCESS_COUNT=0",
                "J6_LOOPBACK_APPLICATION_LISTENER_RESIDUAL_COUNT=0",
                "PROVIDER_ACCESS_PERFORMED=NO",
                "J6_BACKUP_RESTORE_LOOPBACK_QUALIFICATION=PASS",
                "J6_SYNTHETIC_TEMP_ROOT_CLEANUP=PASS"};
    }

    private static String[] dockerMarkers() {
        String[] offline = offlineMarkers();
        String[] docker = new String[]{
                "J6_BACKUP_RUNTIME_EFFECTIVE_DEFAULTS_5000_10000=PASS",
                "J6_DOCKER_EXECUTABLE_IDENTITY=AUTHENTICODE_DOCKER_INC",
                "J6_PIPELINE_DOCKER_PG_DUMP_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED",
                "J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION=PASS_ABSTRACT_NO_SECRET",
                "J6_PIPELINE_DOCKER_PG_RESTORE_NOMINAL=PASS",
                "J6_TEMPORARY_RESTORE_DATABASE_CLEANUP=PASS",
                "J6_PARTIAL_FILE_CLEANUP=PASS",
                "J6_DECRYPTOR_FAILURE_REJECTS_QUALIFICATION=PASS_FAIL_CLOSED",
                "J6_TEMPORARY_RESTORE_DATABASE_RESIDUAL_COUNT=0",
                "J6_CLEANUP_FAILURE_REJECTS_QUALIFICATION=PASS_FAIL_CLOSED",
                "J6_POSTGRES_OWNED_SESSION_RESIDUAL_COUNT=0",
                "J6_BACKUP_PARTIAL_ARTIFACT_RESIDUAL_COUNT=0"};
        String[] combined = new String[offline.length + docker.length];
        System.arraycopy(offline, 0, combined, 0, offline.length);
        System.arraycopy(docker, 0, combined, offline.length, docker.length);
        return combined;
    }
}
