package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Owner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class LiveOrphanProcessProbeTest {
    private static final Instant START = Instant.parse("2026-09-08T10:00:00.123456Z");
    private static final Owner FORMER = new Owner(UUID.randomUUID(), 501, START);
    @TempDir Path directory;

    @Test
    void livingFormerOwnerComparedAtMicrosecondPrecisionNeverRunsTheInventory() throws Exception {
        var calls = new AtomicInteger();
        var probe = probe("Windows 11", Optional.of(new LiveOrphanProcessProbe.ObservedProcess(true,
                START.plusNanos(789))), (script, parameters) -> { calls.incrementAndGet(); return absent(); });
        assertCode(() -> probe.requireAbsent(FORMER, workerJar()), "LIVE_CLEANUP_OWNER_ACTIVE");
        assertThat(calls).hasValue(0);
    }

    @Test
    void reusedPidWithDifferentStartCanBeProvedAbsentButMissingIdentityCannot() throws Exception {
        var reused = probe("Windows 11", Optional.of(new LiveOrphanProcessProbe.ObservedProcess(true,
                START.plusNanos(1000))), (script, parameters) -> absent());
        reused.requireAbsent(FORMER, workerJar());
        var unknown = probe("Windows 11", Optional.of(new LiveOrphanProcessProbe.ObservedProcess(true, null)),
                (script, parameters) -> { throw new AssertionError("must not inspect after ambiguous owner"); });
        assertCode(() -> unknown.requireAbsent(FORMER, workerJar()), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
    }

    @Test
    void absenceRequiresReadableCanonicalArtifactAndSupportedPlatform() throws Exception {
        var calls = new AtomicInteger();
        LiveOrphanProcessProbe.PowerShellRunner runner = (script, parameters) -> { calls.incrementAndGet(); return absent(); };
        var windows = probe("Windows 11", Optional.empty(), runner);
        assertCode(() -> windows.requireAbsent(FORMER, null), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        assertCode(() -> windows.requireAbsent(FORMER, directory.resolve("missing.jar")), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        assertCode(() -> windows.requireAbsent(FORMER, directory), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        assertCode(() -> probe("Linux", Optional.empty(), runner).requireAbsent(FORMER, workerJar()),
                "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        assertCode(() -> probe("Darwin", Optional.empty(), runner).requireAbsent(FORMER, workerJar()),
                "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        assertThat(calls).hasValue(0);
    }

    @Test
    void inventoryReturnsOnlyRecognizedCodesAndNeverLeaksItsOutputOrException() throws Exception {
        Path jar = workerJar();
        assertCode(() -> probe("Windows 11", Optional.empty(), (s, p) ->
                new LiveOrphanProcessProbe.ShellResult(0, "ACTIVE", false)).requireAbsent(FORMER, jar),
                "LIVE_CLEANUP_PROCESS_ACTIVE");
        for (var result : java.util.List.of(
                new LiveOrphanProcessProbe.ShellResult(0, "UNVERIFIED", false),
                new LiveOrphanProcessProbe.ShellResult(0, "private-command-line-canary", false),
                new LiveOrphanProcessProbe.ShellResult(0, "ABSENT\nACTIVE", false),
                new LiveOrphanProcessProbe.ShellResult(1, "ABSENT", false),
                new LiveOrphanProcessProbe.ShellResult(0, "ABSENT", true),
                new LiveOrphanProcessProbe.ShellResult(0, "x".repeat(100), false))) {
            assertCode(() -> probe("Windows 11", Optional.empty(), (s, p) -> result).requireAbsent(FORMER, jar),
                    "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        }
        assertCode(() -> probe("Windows 11", Optional.empty(), (s, p) -> {
            throw new java.util.concurrent.TimeoutException("private-command-line-canary");
        }).requireAbsent(FORMER, jar), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
        assertCode(() -> new LiveOrphanProcessProbe("Windows 11", pid -> {
            throw new SecurityException("private-command-line-canary");
        }, (s, p) -> absent(), 42, START).requireAbsent(FORMER, jar), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
    }

    @Test
    void interruptedProofRemainsInterruptedAndDoesNotSpawnTheHelper() throws Exception {
        var probe = probe("Windows 11", Optional.empty(), (s, p) -> {
            throw new AssertionError("interrupted operation must not launch its helper");
        });
        Path jar = workerJar();
        Thread.currentThread().interrupt();
        try {
            assertCode(() -> probe.requireAbsent(FORMER, jar), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test
    void collectorUsesStaticCodeAndSeparateNonSecretParameters() throws Exception {
        Path jar = workerJar();
        var probe = probe("Windows 11", Optional.empty(), (script, parameters) -> {
            assertThat(script).isEqualTo(LiveOrphanProcessProbe.PROBE_SCRIPT)
                    .contains("Get-CimInstance -ClassName Win32_Process", "-OperationTimeoutSec 5", "-ErrorAction Stop")
                    .doesNotContain("Stop-Process", "Invoke-Expression", "Write-Host", "ConvertTo-Json");
            assertThat(parameters).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "LAB_CLEANUP_WORKER_JAR", jar.toRealPath().toString(), "LAB_CLEANUP_CURRENT_PID", "42",
                    "LAB_CLEANUP_CURRENT_START", START.toString(), "LAB_CLEANUP_CURRENT_ANCESTORS", "",
                    "LAB_CLEANUP_FORMER_OWNER_START", START.toString()));
            return absent();
        });
        probe.requireAbsent(FORMER, jar);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void currentJavaIdentityMatchesItsRealCimCreationDate() throws Exception {
        // Keep the exact production collector/classifier, narrowing only its inventory to this JVM.
        // Other JVMs (Maven/IDE) are deliberately outside this local timestamp compatibility check.
        ProcessHandle current = ProcessHandle.current();
        Instant started = current.info().startInstant().orElseThrow().truncatedTo(ChronoUnit.MICROS);
        String script = LiveOrphanProcessProbe.PROBE_SCRIPT.replace("-Filter $filter",
                "-Filter (\"ProcessId=\" + ([long]$env:LAB_CLEANUP_CURRENT_PID))");
        assertThat(script).isNotEqualTo(LiveOrphanProcessProbe.PROBE_SCRIPT);
        var result = LiveOrphanProcessProbe.runPowerShell(script, Map.of(
                "LAB_CLEANUP_WORKER_JAR", workerJar().toRealPath().toString(),
                "LAB_CLEANUP_CURRENT_PID", Long.toString(current.pid()),
                "LAB_CLEANUP_CURRENT_START", started.toString(), "LAB_CLEANUP_CURRENT_ANCESTORS", "",
                "LAB_CLEANUP_FORMER_OWNER_START", START.toString()));
        assertThat(result).isEqualTo(new LiveOrphanProcessProbe.ShellResult(0, "ABSENT", false));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void actualPowerShellExemptsOnlyProvenAncestorRunningTheMavenSpringBootGoal() throws Exception {
        String launcher = "\"C:\\jdk\\bin\\java.exe\" -classpath \"C:\\Maven Home\\boot\\classworlds.jar\" "
                + "-Dmaven.home=\"C:\\Maven Home\" org.codehaus.plexus.classworlds.launcher.Launcher";
        String ancestor = "43:" + START.toEpochMilli();
        StringBuilder script = new StringBuilder(LiveOrphanProcessProbe.CLASSIFIER);
        script.append("\ntry {\n$current = ")
                .append(process("java.exe", 42, "C:\\jdk\\bin\\java.exe", "java LabApplication", START.toString()))
                .append("\nfunction Check-Maven { param([string]$Command, [string]$Identity, [string]$Created, [string]$Expected, [int]$Number)\n")
                .append("$candidate = [pscustomobject]@{Name='java.exe';ProcessId=43;ExecutablePath='C:\\jdk\\bin\\java.exe';CommandLine=$Command;CreationDate=([DateTimeOffset]::Parse($Created)).UtcDateTime}\n")
                .append("$result = Test-LiveProcessInventory -Processes @($current,$candidate) -WorkerJar 'C:\\lab\\provider-playwright-worker.jar' -CurrentProcessId 42 -CurrentStartedAt ([DateTimeOffset]::Parse('")
                .append(START).append("')) -CurrentAncestors $Identity\n")
                .append("if ($result -ne $Expected) { [Console]::Out.Write('MAVEN_' + $Number + '_FAILED'); exit 1 }\n}\n");
        int index = 0;
        for (var sample : java.util.List.of(
                new MavenCase(launcher + " spring-boot:run", ancestor, START, "ABSENT"),
                new MavenCase(launcher + " spring-boot:run", "", START, "UNVERIFIED"),
                new MavenCase(launcher + " spring-boot:run", "44:" + START.toEpochMilli(), START, "UNVERIFIED"),
                new MavenCase(launcher + " spring-boot:run", ancestor, START.plusMillis(1), "UNVERIFIED"),
                new MavenCase("java -jar C:\\lab\\provider-playwright-worker.jar spring-boot:run", ancestor, START, "ACTIVE"),
                new MavenCase("java com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerMain spring-boot:run", ancestor, START, "ACTIVE"),
                new MavenCase("java -Dmain=org.codehaus.plexus.classworlds.launcher.Launcher Example spring-boot:run", ancestor, START, "UNVERIFIED"),
                new MavenCase(launcher + " -Dgoal=spring-boot:run verify", ancestor, START, "UNVERIFIED"),
                new MavenCase(launcher + " --define spring-boot:run verify", ancestor, START, "UNVERIFIED"),
                new MavenCase(launcher + " -f spring-boot:run verify", ancestor, START, "UNVERIFIED"),
                new MavenCase(launcher + " spring-boot:run-other", ancestor, START, "UNVERIFIED"),
                new MavenCase("java -jar renamed.jar org.codehaus.plexus.classworlds.launcher.Launcher spring-boot:run", ancestor, START, "UNVERIFIED"),
                new MavenCase("java -cp org.codehaus.plexus.classworlds.launcher.Launcher Example spring-boot:run", ancestor, START, "UNVERIFIED"),
                new MavenCase(launcher + " verify", ancestor, START, "UNVERIFIED"))) {
            script.append("Check-Maven -Command ").append(quote(sample.command()))
                    .append(" -Identity ").append(quote(sample.identity())).append(" -Created ")
                    .append(quote(sample.created().toString())).append(" -Expected ")
                    .append(quote(sample.expected())).append(" -Number ").append(index++).append("\n");
        }
        script.append("[Console]::Out.Write('PASS')\n} catch { [Console]::Out.Write('MAVEN_CLASSIFIER_FAILED'); exit 1 }\n");
        var result = LiveOrphanProcessProbe.runPowerShell(script.toString(), Map.of());
        assertThat(result).isEqualTo(new LiveOrphanProcessProbe.ShellResult(0, "PASS", false));
    }

    @Test
    void passesOnlyBoundedProvedAncestorIdentities() throws Exception {
        var calls = new AtomicInteger();
        var probe = new LiveOrphanProcessProbe("Windows 11", ignored -> Optional.empty(), (script, parameters) -> {
            assertThat(parameters.get("LAB_CLEANUP_CURRENT_ANCESTORS")).isEqualTo("43:" + START.toEpochMilli());
            calls.incrementAndGet();
            return absent();
        }, 42, START, () -> java.util.List.of(new LiveOrphanProcessProbe.ProcessIdentity(43, START)));
        probe.requireAbsent(FORMER, workerJar());
        assertThat(calls).hasValue(1);
        var unknown = new LiveOrphanProcessProbe("Windows 11", ignored -> Optional.empty(), (s, p) -> {
            throw new AssertionError("unproved ancestor identities must not reach the helper");
        }, 42, START, () -> java.util.List.of(new LiveOrphanProcessProbe.ProcessIdentity(43, null)));
        assertCode(() -> unknown.requireAbsent(FORMER, workerJar()), "LIVE_CLEANUP_PROCESS_UNVERIFIED");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void actualPowerShellExemptsOnlyReadableUnmarkedJvmsStrictlyOlderThanTheFormerOwner() throws Exception {
        String older = START.minusMillis(1).toString();
        StringBuilder script = new StringBuilder(LiveOrphanProcessProbe.CLASSIFIER);
        script.append("\ntry {\n$current = ")
                .append(process("java.exe", 42, "C:\\jdk\\bin\\java.exe", "java LabApplication", START.toString()))
                .append("\nfunction Check-Age { param($Entry, [string]$Expected, [int]$Number)\n")
                .append("$result = Test-LiveProcessInventory -Processes @($current,$Entry) -WorkerJar 'C:\\lab\\provider-playwright-worker.jar' -CurrentProcessId 42 -CurrentStartedAt ([DateTimeOffset]::Parse('")
                .append(START).append("')) -FormerOwnerStartedAt ([DateTimeOffset]::Parse('")
                .append(START).append("'))\nif ($result -ne $Expected) { [Console]::Out.Write('AGE_' + $Number + '_FAILED'); exit 1 }\n}\n");
        int index = 0;
        for (var sample : java.util.List.of(
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java EclipseHelper", older), "ABSENT"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java EclipseHelper", START.toString()), "UNVERIFIED"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java EclipseHelper", START.plusMillis(1).toString()), "UNVERIFIED"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java EclipseHelper", START.minusNanos(400_000).toString()), "UNVERIFIED"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java -jar C:\\lab\\provider-playwright-worker.jar", older), "ACTIVE"),
                new Case(process("javaw.exe", 43, "C:\\jdk\\bin\\javaw.exe", "java com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerMain", older), "ACTIVE"),
                new Case(process("node.exe", 43, "C:\\temp\\playwright-java-5\\node.exe", "node cli.js run-driver", older), "ACTIVE"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "", older), "UNVERIFIED"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java EclipseHelper", null), "UNVERIFIED"))) {
            script.append("Check-Age -Entry (").append(sample.object())
                    .append(") -Expected ").append(quote(sample.expected())).append(" -Number ").append(index++).append("\n");
        }
        script.append("[Console]::Out.Write('PASS')\n} catch { [Console]::Out.Write('AGE_CLASSIFIER_FAILED'); exit 1 }\n");
        var result = LiveOrphanProcessProbe.runPowerShell(script.toString(), Map.of());
        assertThat(result).isEqualTo(new LiveOrphanProcessProbe.ShellResult(0, "PASS", false));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void actualPowerShellClassifierRejectsSyntheticWorkersDescendantsAndInaccessibleCandidates() throws Exception {
        // The production classifier is executed on synthetic objects, never on CIM or a browser.
        String current = process("java.exe", 42, "C:\\jdk\\bin\\java.exe", "java LabApplication", START.toString());
        StringBuilder cases = new StringBuilder(LiveOrphanProcessProbe.CLASSIFIER);
        cases.append("\ntry {\n$current = ").append(current).append("\n")
                .append("function Check-Sample { param($Entry, [string]$Expected, [int]$Number)\n")
                .append("$items = @($current); if ($null -ne $Entry) { $items += $Entry }\n")
                .append("$result = Test-LiveProcessInventory -Processes $items -WorkerJar 'C:\\lab\\provider-playwright-worker.jar' -CurrentProcessId 42 -CurrentStartedAt ([DateTimeOffset]::Parse('")
                .append(START).append("'))\nif ($result -ne $Expected) { [Console]::Out.Write('CASE_' + $Number + '_FAILED'); exit 1 }\n}\n");
        int index = 0;
        for (var sample : java.util.List.of(
                new Case("", "ABSENT"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java -jar C:\\lab\\provider-playwright-worker.jar", START.toString()), "ACTIVE"),
                new Case(process("javaw.exe", 43, "C:\\jdk\\bin\\javaw.exe", "java com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerMain", START.toString()), "ACTIVE"),
                new Case(process("java.exe", 43, "C:\\jdk\\bin\\java.exe", "java -jar C:\\lab\\renamed.jar", START.toString()), "UNVERIFIED"),
                new Case(process("node.exe", 43, "C:\\temp\\playwright-java-5\\node.exe", "node package\\cli.js run-driver", START.toString()), "ACTIVE"),
                new Case(process("chrome.exe", 43, "C:\\cache\\chromium-1228\\chrome.exe", "chrome --type=renderer", START.toString()), "ACTIVE"),
                new Case(process("chrome-headless-shell.exe", 43, "C:\\cache\\shell.exe", "headless", START.toString()), "ACTIVE"),
                new Case(process("chrome.exe", 43, "C:\\Browser\\chrome.exe", "chrome --remote-debugging-pipe", START.toString()), "ACTIVE"),
                new Case(process("chrome.exe", 43, "C:\\Browser\\chrome.exe", "chrome", START.toString()), "ABSENT"),
                new Case(process("node.exe", 43, "C:\\node\\node.exe", "node local-editor.js", START.toString()), "ABSENT"),
                new Case(process("chrome.exe", 43, "C:\\Browser\\chrome.exe", "", START.toString()), "UNVERIFIED"),
                new Case(process("node.exe", 43, "", "node local-editor.js", START.toString()), "UNVERIFIED"),
                new Case(process("chrome.exe", 43, "C:\\Browser\\chrome.exe", "chrome", null), "UNVERIFIED"))) {
            cases.append("Check-Sample -Entry (").append(sample.object().isEmpty() ? "$null" : sample.object())
                    .append(") -Expected '").append(sample.expected()).append("' -Number ").append(index++).append("\n");
        }
        cases.append("$missing = Test-LiveProcessInventory -Processes @() -WorkerJar 'C:\\lab\\worker.jar' -CurrentProcessId 42 -CurrentStartedAt ([DateTimeOffset]::Parse('")
                .append(START).append("'))\nif ($missing -ne 'UNVERIFIED') { [Console]::Out.Write('MISSING_SELF_FAILED'); exit 1 }\n")
                .append("$differentStart = Test-LiveProcessInventory -Processes @(")
                .append(process("java.exe", 42, "C:\\jdk\\bin\\java.exe", "java LabApplication", START.plusMillis(1).toString()))
                .append(") -WorkerJar 'C:\\lab\\worker.jar' -CurrentProcessId 42 -CurrentStartedAt ([DateTimeOffset]::Parse('")
                .append(START).append("'))\nif ($differentStart -ne 'UNVERIFIED') { [Console]::Out.Write('CURRENT_IDENTITY_FAILED'); exit 1 }\n")
                .append("[Console]::Out.Write('PASS')\n} catch { [Console]::Out.Write('CLASSIFIER_FAILED'); exit 1 }\n");
        var result = LiveOrphanProcessProbe.runPowerShell(cases.toString(), Map.of());
        assertThat(result).isEqualTo(new LiveOrphanProcessProbe.ShellResult(0, "PASS", false));
    }

    private Path workerJar() throws Exception {
        Path jar = directory.resolve("provider-playwright-worker.jar");
        if (!Files.exists(jar)) Files.writeString(jar, "synthetic artifact, never executed");
        return jar;
    }

    private static String process(String name, int pid, String path, String command, String started) {
        return "[pscustomobject]@{Name=" + quote(name) + ";ProcessId=" + pid + ";ExecutablePath=" + quote(path)
                + ";CommandLine=" + quote(command) + ";CreationDate="
                + (started == null ? "$null" : "([DateTimeOffset]::Parse(" + quote(started) + ")).UtcDateTime") + "}";
    }
    private static String quote(String text) { return "'" + text.replace("'", "''") + "'"; }
    private static LiveOrphanProcessProbe probe(String system, Optional<LiveOrphanProcessProbe.ObservedProcess> owner,
                                               LiveOrphanProcessProbe.PowerShellRunner runner) {
        return new LiveOrphanProcessProbe(system, ignored -> owner, runner, 42, START);
    }
    private static LiveOrphanProcessProbe.ShellResult absent() { return new LiveOrphanProcessProbe.ShellResult(0, "ABSENT", false); }
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(IllegalStateException.class).hasMessage(code).hasNoCause();
    }
    private record Case(String object, String expected) { }
    private record MavenCase(String command, String identity, Instant created, String expected) { }
}
