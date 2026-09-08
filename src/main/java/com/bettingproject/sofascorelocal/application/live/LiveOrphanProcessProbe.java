package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Owner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Read-only Windows absence proof for an explicitly requested orphan cleanup; never opens Playwright. */
@Component
public final class LiveOrphanProcessProbe {
    private static final String OWNER_ACTIVE = "LIVE_CLEANUP_OWNER_ACTIVE";
    private static final String PROCESS_ACTIVE = "LIVE_CLEANUP_PROCESS_ACTIVE";
    private static final String UNVERIFIED = "LIVE_CLEANUP_PROCESS_UNVERIFIED";
    private static final int MAXIMUM_STDOUT_BYTES = 64;
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    /* Command lines stay inside the local PowerShell helper. Only these three codes
       can cross its stdout boundary. Unknown JVMs not proved older than the former
       owner remain blocked: the former supervisor's process inventory was lost. */
    static final String CLASSIFIER = """
        $ErrorActionPreference = 'Stop'
        Set-StrictMode -Version Latest
        function Test-LiveMavenLauncher {
          param([string]$Command)
          # Accept only balanced ordinary Windows argument quoting. Unusual quoting or
          # argument files stay ambiguous; never infer the main class from a substring.
          if ($Command -notmatch '^\\s*(?:(?:[^"\\s]+|"[^"]*")+\\s*)+$') { return $false }
          $arguments = @([regex]::Matches($Command, '(?:[^"\\s]+|"[^"]*")+') | ForEach-Object { $_.Value.Replace('"', '') })
          for ($index = 1; $index -lt $arguments.Count; $index++) {
            $argument = $arguments[$index]
            if ($argument.StartsWith('@') -or $argument -in @('-jar', '-m', '--module', '--')) { return $false }
            if ($argument -in @('-cp', '-classpath', '--class-path', '-p', '--module-path', '--upgrade-module-path',
                '--add-modules', '--limit-modules', '--add-exports', '--add-opens', '--add-reads', '--patch-module')) {
              $index++
              if ($index -ge $arguments.Count) { return $false }
              continue
            }
            if ($argument.StartsWith('-')) { continue }
            if ($argument -ne 'org.codehaus.plexus.classworlds.launcher.launcher') { return $false }
            for ($goal = $index + 1; $goal -lt $arguments.Count; $goal++) {
              if ($arguments[$goal] -in @('-D', '--define', '-f', '--file', '-s', '--settings', '-gs', '--global-settings',
                  '-t', '--toolchains', '-gt', '--global-toolchains', '-P', '--activate-profiles', '-pl', '--projects',
                  '-rf', '--resume-from', '-T', '--threads', '-l', '--log-file', '-b', '--builder')) {
                $goal++
                continue
              }
              if ($arguments[$goal] -ceq 'spring-boot:run') { return $true }
            }
            return $false
          }
          return $false
        }
        function Test-LiveProcessInventory {
          param([object[]]$Processes, [string]$WorkerJar, [long]$CurrentProcessId, [DateTimeOffset]$CurrentStartedAt,
                [string]$CurrentAncestors = '', [DateTimeOffset]$FormerOwnerStartedAt = [DateTimeOffset]::MinValue)
          $ancestors = @{}
          if (-not [string]::IsNullOrEmpty($CurrentAncestors)) {
            foreach ($identity in $CurrentAncestors.Split(';')) {
              if ($identity -notmatch '^([1-9][0-9]*):([0-9]+)$') { return 'UNVERIFIED' }
              $ancestorId = [long]$Matches[1]
              if ($ancestors.ContainsKey($ancestorId)) { return 'UNVERIFIED' }
              $ancestors[$ancestorId] = [long]$Matches[2]
            }
          }
          $unverified = $false
          $active = $false
          $currentSeen = $false
          foreach ($process in $Processes) {
            $id = [long]$process.ProcessId
            if ($id -le 0) { $unverified = $true; continue }
            $name = ([string]$process.Name).ToLowerInvariant()
            if ($name -notmatch '^(javaw?|node|chrome|chrome-headless-shell|headless_shell|chromium)\\.exe$') {
              $unverified = $true; continue
            }
            if ($null -eq $process.CreationDate) { $unverified = $true; continue }
            $created = [DateTimeOffset]$process.CreationDate
            if ($id -eq $CurrentProcessId) {
              # JDK ProcessHandle.Info.startInstant uses Instant.ofEpochMilli. This exemption
              # concerns the still-running caller only, whose PID cannot meanwhile be reused.
              $actual = $created.ToUnixTimeMilliseconds()
              $expected = $CurrentStartedAt.ToUnixTimeMilliseconds()
              if ($actual -eq $expected) { $currentSeen = $true } else { $unverified = $true }
              continue
            }
            $path = [string]$process.ExecutablePath
            $command = [string]$process.CommandLine
            if ([string]::IsNullOrWhiteSpace($path) -or [string]::IsNullOrWhiteSpace($command)) {
              $unverified = $true; continue
            }
            $path = $path.Replace('/', '\\').ToLowerInvariant()
            $command = $command.Replace('/', '\\').ToLowerInvariant()
            if ($name -match '^javaw?\\.exe$') {
              if ($command.Contains($WorkerJar.Replace('/', '\\').ToLowerInvariant()) -or
                  $command.Contains('provider-playwright-worker') -or
                  $command.Contains('com.bettingproject.sofascorelocal.provider.playwright.worker.')) {
                $active = $true
              } elseif ($ancestors.ContainsKey($id) -and
                  $ancestors[$id] -eq $created.ToUnixTimeMilliseconds() -and
                  (Test-LiveMavenLauncher -Command $command)) {
                # Only a proven ancestor running the exact Maven Spring Boot launch goal.
                continue
              } elseif ($created.ToUnixTimeMilliseconds() -lt $FormerOwnerStartedAt.ToUnixTimeMilliseconds()) {
                # The former application's worker is always created fresh. A readable,
                # unmarked JVM strictly older than its owner cannot be that session's worker.
                # Equality at Java's millisecond precision remains ambiguous and is rejected.
                continue
              } else { $unverified = $true }
            } elseif ($name -eq 'node.exe') {
              if ($path.Contains('playwright') -or $command.Contains('playwright') -or
                  $command.Contains('run-driver') -or $command.Contains('\\driver\\package\\cli.js')) {
                $active = $true
              }
            } else {
              if ($name -ne 'chrome.exe' -or $path.Contains('playwright') -or
                  $path.Contains('\\chromium-') -or $path.Contains('\\chromium_headless_shell-') -or
                  $command.Contains('playwright') -or $command.Contains('--remote-debugging-pipe') -or
                  $command.Contains('--headless') -or $command.Contains('--enable-automation')) {
                $active = $true
              }
            }
          }
          if ($active) { return 'ACTIVE' }
          if ($unverified -or -not $currentSeen) { return 'UNVERIFIED' }
          return 'ABSENT'
        }
        """;

    static final String PROBE_SCRIPT = CLASSIFIER + """
        try {
          $filter = "Name='java.exe' OR Name='javaw.exe' OR Name='node.exe' OR Name='chrome.exe' OR Name='chrome-headless-shell.exe' OR Name='headless_shell.exe' OR Name='chromium.exe'"
          $candidates = @(Get-CimInstance -ClassName Win32_Process -Filter $filter -OperationTimeoutSec 5 -ErrorAction Stop)
          $result = Test-LiveProcessInventory -Processes $candidates -WorkerJar $env:LAB_CLEANUP_WORKER_JAR -CurrentProcessId ([long]$env:LAB_CLEANUP_CURRENT_PID) -CurrentStartedAt ([DateTimeOffset]::Parse($env:LAB_CLEANUP_CURRENT_START)) -CurrentAncestors $env:LAB_CLEANUP_CURRENT_ANCESTORS -FormerOwnerStartedAt ([DateTimeOffset]::Parse($env:LAB_CLEANUP_FORMER_OWNER_START))
          [Console]::Out.Write($result)
        } catch { [Console]::Out.Write('UNVERIFIED') }
        """;

    private final String operatingSystem;
    private final OwnerLookup owners;
    private final PowerShellRunner runner;
    private final long currentPid;
    private final Instant currentStartedAt;
    private final AncestorsLookup ancestors;

    public LiveOrphanProcessProbe() {
        this(System.getProperty("os.name", ""), pid -> ProcessHandle.of(pid).map(process ->
                        new ObservedProcess(process.isAlive(), process.info().startInstant().orElse(null))),
                LiveOrphanProcessProbe::runPowerShell, ProcessHandle.current().pid(),
                ProcessHandle.current().info().startInstant().orElse(null), LiveOrphanProcessProbe::currentAncestors);
    }

    LiveOrphanProcessProbe(String operatingSystem, OwnerLookup owners, PowerShellRunner runner,
                           long currentPid, Instant currentStartedAt) {
        this(operatingSystem, owners, runner, currentPid, currentStartedAt, List::of);
    }

    LiveOrphanProcessProbe(String operatingSystem, OwnerLookup owners, PowerShellRunner runner,
                           long currentPid, Instant currentStartedAt, AncestorsLookup ancestors) {
        this.operatingSystem = operatingSystem;
        this.owners = owners;
        this.runner = runner;
        this.currentPid = currentPid;
        this.currentStartedAt = currentStartedAt;
        this.ancestors = ancestors;
    }

    /** Caller must retain provider exclusion through this proof and the SQL cleanup transaction. */
    public void requireAbsent(Owner formerOwner, Path configuredWorkerJar) {
        if (Thread.currentThread().isInterrupted()) throw unverified();
        requireFormerOwnerAbsent(formerOwner);
        if (!operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")
                || currentPid <= 0 || currentStartedAt == null || configuredWorkerJar == null) throw unverified();
        final Path workerJar;
        try {
            Path absolute = configuredWorkerJar.toAbsolutePath().normalize();
            // This recovery is local. Do not probe UNC/network shares for an ambiguous configured artifact.
            if (absolute.toString().startsWith("\\\\")) throw unverified();
            workerJar = absolute.toRealPath();
            if (!Files.isRegularFile(workerJar)
                    || !workerJar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) throw unverified();
        } catch (IOException | SecurityException exception) { throw unverified(); }
        ShellResult result;
        try {
            result = runner.run(PROBE_SCRIPT, Map.of(
                    "LAB_CLEANUP_WORKER_JAR", workerJar.toString(),
                    "LAB_CLEANUP_CURRENT_PID", Long.toString(currentPid),
                    "LAB_CLEANUP_CURRENT_START", currentStartedAt.truncatedTo(ChronoUnit.MICROS).toString(),
                    "LAB_CLEANUP_FORMER_OWNER_START", formerOwner.processStartedAt().truncatedTo(ChronoUnit.MICROS).toString(),
                    "LAB_CLEANUP_CURRENT_ANCESTORS", encodeAncestors(ancestors.find())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unverified();
        } catch (Exception exception) { throw unverified(); }
        if (result == null || result.timedOut() || result.exitCode() != 0 || result.output() == null
                || result.output().length() > MAXIMUM_STDOUT_BYTES) throw unverified();
        switch (result.output()) {
            case "ABSENT" -> { }
            case "ACTIVE" -> throw new IllegalStateException(PROCESS_ACTIVE);
            default -> throw unverified();
        }
    }

    private void requireFormerOwnerAbsent(Owner formerOwner) {
        if (formerOwner == null) throw unverified();
        final Optional<ObservedProcess> observed;
        try { observed = owners.find(formerOwner.processId()); }
        catch (RuntimeException exception) { throw unverified(); }
        if (observed == null) throw unverified();
        if (observed.isEmpty() || !observed.orElseThrow().alive()) return;
        Instant started = observed.orElseThrow().startedAt();
        if (started == null) throw unverified();
        if (started.truncatedTo(ChronoUnit.MICROS).equals(formerOwner.processStartedAt().truncatedTo(ChronoUnit.MICROS)))
            throw new IllegalStateException(OWNER_ACTIVE);
    }

    private static List<ProcessIdentity> currentAncestors() {
        List<ProcessIdentity> result = new ArrayList<>();
        var seen = new HashSet<Long>();
        try {
            Optional<ProcessHandle> parent = ProcessHandle.current().parent();
            while (parent.isPresent()) {
                ProcessHandle process = parent.orElseThrow();
                Instant started = process.info().startInstant().orElse(null);
                // Stop at an inaccessible link. The previously proved prefix remains valid;
                // neither this link nor any ancestor beyond it receives an exemption.
                if (result.size() >= 32 || !process.isAlive() || started == null || !seen.add(process.pid())) break;
                result.add(new ProcessIdentity(process.pid(), started));
                parent = process.parent();
            }
        } catch (RuntimeException inaccessible) { /* Keep only the already proved prefix. */ }
        return List.copyOf(result);
    }

    private static String encodeAncestors(List<ProcessIdentity> identities) {
        if (identities == null || identities.size() > 32) throw unverified();
        var seen = new HashSet<Long>();
        List<String> encoded = new ArrayList<>();
        for (ProcessIdentity identity : identities) {
            if (identity == null || identity.processId() < 1 || identity.startedAt() == null
                    || identity.startedAt().isBefore(Instant.EPOCH) || !seen.add(identity.processId())) throw unverified();
            encoded.add(identity.processId() + ":" + identity.startedAt().toEpochMilli());
        }
        return String.join(";", encoded);
    }

    static ShellResult runPowerShell(String script, Map<String, String> parameters) throws Exception {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) throw unverified();
        Path executable = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toRealPath();
        if (!Files.isRegularFile(executable)) throw unverified();
        var builder = new ProcessBuilder(executable.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                "-EncodedCommand", Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)));
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        var environment = builder.environment();
        environment.clear();
        for (String key : new String[]{"SystemRoot", "WINDIR", "TEMP", "TMP", "USERPROFILE", "LOCALAPPDATA"}) {
            String value = System.getenv(key);
            if (value != null) environment.put(key, value);
        }
        environment.put("PATH", Path.of(systemRoot, "System32").toString());
        environment.putAll(parameters);
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        Process helper = builder.start();
        CompletableFuture<byte[]> output = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (var input = helper.getInputStream()) {
                byte[] bytes = input.readNBytes(MAXIMUM_STDOUT_BYTES + 1);
                if (bytes.length > MAXIMUM_STDOUT_BYTES) output.completeExceptionally(new IOException("probe output bound"));
                else output.complete(bytes);
            } catch (IOException exception) { output.completeExceptionally(exception); }
        });
        try {
            helper.getOutputStream().close();
            if (!helper.waitFor(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                return new ShellResult(-1, "", true);
            byte[] bytes = output.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return new ShellResult(helper.exitValue(), new String(bytes, StandardCharsets.UTF_8), false);
        } finally {
            // Only our own bounded read-only helper is cancelled. Never signal any inspected process.
            if (helper.isAlive()) helper.destroyForcibly();
        }
    }

    private static IllegalStateException unverified() { return new IllegalStateException(UNVERIFIED); }
    @FunctionalInterface interface OwnerLookup { Optional<ObservedProcess> find(long pid); }
    @FunctionalInterface interface AncestorsLookup { List<ProcessIdentity> find(); }
    @FunctionalInterface interface PowerShellRunner { ShellResult run(String script, Map<String, String> parameters) throws Exception; }
    record ObservedProcess(boolean alive, Instant startedAt) { }
    record ProcessIdentity(long processId, Instant startedAt) { }
    record ShellResult(int exitCode, String output, boolean timedOut) { }
}
