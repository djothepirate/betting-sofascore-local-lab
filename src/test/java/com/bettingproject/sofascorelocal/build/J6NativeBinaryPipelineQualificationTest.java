package com.bettingproject.sofascorelocal.build;

import org.junit.jupiter.api.Test;
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
                        "dropdb --username \"$POSTGRES_USER\" --force --if-exists",
                        "$manifestStagingPath",
                        "$QualificationInjectCleanupFailureAfterSuccessfulCleanup")
                .doesNotContain(
                        "'pg_dump --username \"$POSTGRES_USER\" --dbname \"$POSTGRES_DB\" --format=custom --no-owner --no-privileges' |",
                        "& $ageExecutable -d $destinationPath |");
        assertThat(module)
                .contains(
                        "CopyToAsync",
                        "Kill($true)",
                        "J6ProcessTreeSnapshot",
                        "J6WindowsKillOnCloseJob",
                        "J6-NativeProcessHost.ps1",
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
                        "J6_CTRL_BREAK_NEW_CONSOLE_REQUESTED=NO")
                .doesNotContain(
                        "Start-Process",
                        "CREATE_NEW_CONSOLE",
                        "AttachConsole",
                        "FreeConsole");
        assertThat(nativeHost)
                .contains(
                        "SetConsoleCtrlHandler",
                        "if (-not $controlGuard.Installed)",
                        "$gate.WaitOne",
                        "$target.Start()")
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
        Path pwsh = Path.of(System.getenv("ProgramFiles"), "PowerShell", "7", "pwsh.exe");
        Path qualification = repository.resolve(
                "scripts/Invoke-J6BackupRestoreLoopbackQualification.ps1");

        assertThat(pwsh)
                .as("PowerShell 7 is mandatory for the Windows J6 qualification")
                .isRegularFile();
        assertThat(qualification).isRegularFile();

        Process process = new ProcessBuilder(
                pwsh.toString(),
                "-NoLogo",
                "-NoProfile",
                "-File",
                qualification.toString())
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

        boolean finished = process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        String evidence = output.get(5, TimeUnit.SECONDS);

        assertThat(finished)
                .as("bounded J6 synthetic qualification output: %s", evidence)
                .isTrue();
        assertThat(process.exitValue())
                .as("J6 synthetic qualification output: %s", evidence)
                .isZero();
        assertThat(evidence)
                .contains(
                        "J6_PIPELINE_BINARY_NOMINAL=PASS",
                        "J6_PIPELINE_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED",
                        "J6_PIPELINE_EXITED_ROOT_DESCENDANT_CLEANUP=PASS",
                        "J6_PIPELINE_PID_REUSE_OWNERSHIP_FENCE=PASS",
                        "J6_PIPELINE_EARLY_PRODUCER_FAILURE=PASS_FAIL_CLOSED",
                        "J6_PIPELINE_RESTORE_SYMMETRY=PASS",
                        "J6_PIPELINE_PREMATURE_SUCCESS_TRUNCATION=PASS_FAIL_CLOSED",
                        "J6_PIPELINE_TIMEOUT=PASS_FAIL_CLOSED",
                        "J6_BOUNDED_NATIVE_COMMAND_TIMEOUT_CLEANUP=PASS_FAIL_CLOSED",
                        "J6_BOUNDED_NATIVE_COMMAND_POST_START_EXCEPTION_CLEANUP=PASS_FAIL_CLOSED",
                        "J6_BOUNDED_NATIVE_COMMAND_EXITED_ROOT_DESCENDANT_JOB_CLEANUP=PASS",
                        "J6_PIPELINE_COOPERATIVE_CANCELLATION_CLEANUP=PASS",
                        "J6_CONSOLE_RESIDUAL_BEFORE_FALLBACK_CLEANUP=0",
                        "J6_CTRL_BREAK_NEW_CONSOLE_REQUESTED=NO",
                        "J6_CTRL_BREAK_WRAPPER_ROOTS_ALIVE_BEFORE_SUPERVISOR_CLEANUP=PASS",
                        "J6_CONSOLE_CTRL_BREAK_EVENT=PASS",
                        "J6_CONSOLE_CANCEL_KEYPRESS_WIRING=PASS",
                        "J6_PIPELINE_INTERRUPTION_CLEANUP=PASS",
                        "J6_PIPELINE_RESIDUAL_OWNED_PROCESS_COUNT=0",
                        "J6_PIPELINE_HUMAN_INCORRECT_PASSPHRASE_REQUIRED=NO",
                        "J6_BACKUP_RESTORE_LOOPBACK_QUALIFICATION=PASS")
                .doesNotContain("SYNTHETIC_SECRET", "PROVIDER_ACCESS_PERFORMED=YES");
    }
}
