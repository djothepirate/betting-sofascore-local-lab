[CmdletBinding()]
param()

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$watch = [Diagnostics.Stopwatch]::StartNew()
$startedUtc = [DateTime]::UtcNow.ToString('O')
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$outputRoot = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output'))
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $outputRoot 'runtime'))
$events = [Collections.Generic.List[object]]::new()
$owned = [Collections.Generic.List[object]]::new()
$seenFiles = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$seenRoots = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$process = $null
$stdoutTask = $null
$stderrTask = $null
$harnessExit = $null
$forcedExit = $null
$primary = 'NOT_STARTED'
$cleanup = 'NOT_OBSERVED'
$failure = $null
$cleanupFailure = $null
$stdout = ''
$stderr = ''
$launchAtMs = $null
$exitAtMs = $null
$command = $null
$observations = $null
$sourceBefore = @()
$sourceAfter = @()
$parentTemp = $env:TEMP
$parentTmp = $env:TMP
$runIdentity = [guid]::NewGuid().ToString()
$entry = Get-Content -LiteralPath (Join-Path $caseRoot 'input.json') -Raw -Encoding utf8 | ConvertFrom-Json

function Add-Event([string]$Name, $Data) {
    $events.Add([pscustomobject]@{ event = $Name; utc = [DateTime]::UtcNow.ToString('O'); elapsedMs = $watch.Elapsed.TotalMilliseconds; data = $Data })
}

function Assert-Path([string]$Path, [string]$Parent) {
    if ($Path -match '[\x00-\x1f\x7f"]' -or $Path -match '(^|[\\/])\.\.([\\/]|$)' -or -not [IO.Path]::IsPathFullyQualified($Path)) { throw 'Unsafe absolute path.' }
    $canonical = [IO.Path]::GetFullPath($Path)
    if (-not $canonical.Equals($Path, [StringComparison]::OrdinalIgnoreCase)) { throw 'Non-canonical path.' }
    if ($Parent -and -not $canonical.StartsWith($Parent.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside exact parent plus separator.' }
    $cursor = $canonical
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point refused: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}

function Get-SourceProof {
    foreach ($relative in @(
        'AGENTS.md', 'input.json', 'inputs/runtime-protocol.md',
        '.agents/skills/ss-windows-runtime/SKILL.md', '.agents/skills/ss-windows-runtime/agents/openai.yaml',
        'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1',
        'scripts/wo044/Capture-WO044NativeArguments.ps1', 'scripts/wo036/WO036-CampaignTools.psm1',
        'scripts/Tests/WO044JavaJarArgumentBoundary.Tests.ps1',
        'docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md',
        'docs/validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md',
        'docs/validation/J9-WO053-REVIEW-HARDENING-20260905.md')) {
        $path = Join-Path $caseRoot $relative
        Assert-Path $path $caseRoot
        [pscustomobject]@{ relativePath = $relative; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
    }
}

function Observe-Files {
    Assert-Path $runtimeRoot $outputRoot
    foreach ($root in @(Get-ChildItem -LiteralPath $runtimeRoot -Force)) {
        Assert-Path $root.FullName $runtimeRoot
        if (-not $root.PSIsContainer -or $root.Name -cnotmatch '^WO044 Java JAR argument boundary [0-9a-f-]{36}$') { throw 'Unexpected runtime entry; preserved.' }
        if ($seenRoots.Add($root.FullName)) { Add-Event 'TEMP_ROOT_OBSERVED' $root.FullName }
        try { $entries = @(Get-ChildItem -LiteralPath $root.FullName -Force) }
        catch {
            if (-not (Test-Path -LiteralPath $root.FullName)) { Add-Event 'TEMP_ROOT_REMOVED_DURING_OBSERVATION' $root.FullName; continue }
            throw
        }
        foreach ($file in $entries) {
            Assert-Path $file.FullName $root.FullName
            if ($file.PSIsContainer -or $file.Name -cnotmatch '^(synthetic application with spaces\.jar|(baseline-unquoted|qualified-01|qualified-02|qualified-no-space)\.(arguments\.json|stdout\.log|stderr\.log))$') { throw 'Unexpected temporary entry; preserved.' }
            if ($seenFiles.Add($file.FullName)) { Add-Event 'TEMP_FILE_OBSERVED' $file.FullName }
        }
    }
}

function Observe-CaptureChildren {
    foreach ($candidate in @(Get-CimInstance Win32_Process -Filter ('ParentProcessId={0}' -f $process.Id))) {
        if (@($owned | Where-Object { $_.pid -eq [int]$candidate.ProcessId }).Count -gt 0) { continue }
        $line = [string]$candidate.CommandLine
        if (-not $line) { Add-Event 'CAPTURE_CIM_IDENTITY_UNAVAILABLE' ([int]$candidate.ProcessId); continue }
        if (-not ([string]$candidate.ExecutablePath).Equals($pwshPath, [StringComparison]::OrdinalIgnoreCase) -or -not $line.Contains($capturePath) -or $line -notmatch '-Dwo036.instance=([0-9a-f-]{36})') { throw 'Unexpected descendant; no termination authorized.' }
        $token = $Matches[1]
        $child = $null
        try {
            $child = [Diagnostics.Process]::GetProcessById([int]$candidate.ProcessId)
            $handle = $child.Handle
            $childStart = $child.StartTime.ToUniversalTime().ToString('O')
            $record = [pscustomobject]@{ process = $child; pid = $child.Id; handle = $handle.ToInt64(); startTimeUtc = $childStart; executable = [string]$candidate.ExecutablePath; instance = $token; commandLine = $line; observedAtMs = $watch.Elapsed.TotalMilliseconds; exitCode = $null; exitObservedAtMs = $null; forced = $false }
            $owned.Add($record)
            Add-Event 'CAPTURE_PROCESS_OBSERVED' ([ordered]@{pid=$record.pid;startTimeUtc=$childStart;executable=$record.executable;instance=$token;commandLine=$line;handle=$record.handle})
        } catch {
            if ($null -ne $child) { $child.Dispose() }
            Add-Event 'CAPTURE_OBSERVATION_RACE' $_.Exception.Message
        }
    }
    foreach ($record in $owned) {
        if ($null -eq $record.exitCode -and $record.process.WaitForExit(0)) {
            $record.exitCode = $record.process.ExitCode
            $record.exitObservedAtMs = $watch.Elapsed.TotalMilliseconds
            Add-Event 'CAPTURE_EXIT_OBSERVED' ([ordered]@{pid=$record.pid;exitCode=$record.exitCode})
        }
    }
}

function Stop-Exact($Record, [int]$WaitMs) {
    if ($Record.process.HasExited) { return }
    $current = Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $Record.pid)
    if ($null -eq $current -or -not ([string]$current.ExecutablePath).Equals($Record.executable, [StringComparison]::OrdinalIgnoreCase) -or [string]$current.CommandLine -cne $Record.commandLine -or $Record.process.StartTime.ToUniversalTime().ToString('O') -cne $Record.startTimeUtc) { throw 'Exact process ownership revalidation failed; no kill.' }
    $Record.process.Kill()
    if (-not $Record.process.WaitForExit($WaitMs)) { throw 'Exact process cleanup deadline expired.' }
    $codeAfterKill = $Record.process.ExitCode
    Add-Event 'EXACT_PROCESS_FORCED_EXIT' ([ordered]@{pid=$Record.pid;exitCodeAfterKill=$codeAfterKill})
}

try {
    Assert-Path $outputRoot $caseRoot
    Assert-Path $PSScriptRoot $outputRoot
    Assert-Path $runtimeRoot $outputRoot
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'run.json')) { throw 'Evidence already exists; automatic rerun refused.' }
    if (-not $IsWindows -or $PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -ne 7) { throw 'Required Windows PowerShell 7 runtime unavailable.' }
    if ($entry.iterations -ne 2 -or $entry.wrapper_total_budget_ms -ne 60000 -or $entry.child_capture_timeout_ms -ne 10000 -or $entry.cleanup_timeout_ms -ne 5000) { throw 'Case contract differs from this bounded driver.' }
    $resolvedCommands = @(Get-Command pwsh.exe -CommandType Application -ErrorAction Stop)
    $pwshPath = [IO.Path]::GetFullPath($resolvedCommands[0].Source)
    $currentProcess = [Diagnostics.Process]::GetCurrentProcess()
    $currentExecutable = $currentProcess.MainModule.FileName
    if (-not $currentExecutable.Equals($pwshPath, [StringComparison]::OrdinalIgnoreCase)) { throw 'Driver actual executable differs from resolved PowerShell; no substitution.' }
    $parent = Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $PID)
    $psTable = [ordered]@{}
    foreach ($key in $PSVersionTable.Keys) { $psTable[$key] = [string]$PSVersionTable[$key] }
    $observations = [ordered]@{
        PSVersionTable=$psTable; PSHOME=$PSHOME; actualExecutable=$currentExecutable; executableVersion=$currentProcess.MainModule.FileVersionInfo.FileVersion;
        driverPid=$PID; driverParentPid=$parent.ParentProcessId; driverStartTimeUtc=$currentProcess.StartTime.ToUniversalTime().ToString('O');
        cwd=(Get-Location).Path; OS=[Environment]::OSVersion.VersionString; osArchitecture=[Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString(); processArchitecture=[Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString();
        discoveredPwsh=@($resolvedCommands | ForEach-Object { [ordered]@{path=$_.Source;version=$_.Version.ToString()} });
        consoleInputEncoding=[Console]::InputEncoding.WebName; consoleOutputEncoding=[Console]::OutputEncoding.WebName; powerShellOutputEncoding=$OutputEncoding.WebName;
        streamDecoder='UTF-8 strict'; evidenceEncoding='UTF-8 without BOM'; captureEncoding='UTF-8 without BOM, strict (frozen capture script)';
        runtimeVersionEvidence='PSVersionTable observed in driver; same executable required and actual child MainModule/FileVersion checked. Frozen harness does not print its own PSVersionTable.'
    }
    Add-Event 'PREFLIGHT_RUNTIME' $observations
    $sourceBefore = @(Get-SourceProof)
    $pinned = @{
        'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'='59996B14941700523007E1F6B0784B102D65E088569F95E882340250D43565E4'
        'scripts/wo044/Capture-WO044NativeArguments.ps1'='841428737A500FD9CCB1407D3C0822B56872F40B308568EE59AB48BAE88B7818'
        'scripts/wo036/WO036-CampaignTools.psm1'='43C7BC8EB242161729F6C18F8B0FEB67C46715CE911B27572E4BD13E5956BD74'
    }
    foreach ($source in $sourceBefore) { if ($pinned.ContainsKey($source.relativePath) -and $pinned[$source.relativePath] -cne $source.sha256) { throw 'Frozen executable hash differs from initial session observation.' } }
    if (-not (Test-Path -LiteralPath $runtimeRoot)) { [void][IO.Directory]::CreateDirectory($runtimeRoot) }
    if (@(Get-ChildItem -LiteralPath $runtimeRoot -Force).Count -ne 0) { throw 'Runtime base is not empty; nothing removed.' }
    Add-Event 'RUNTIME_PREFLIGHT_EMPTY' $runtimeRoot
    $harnessPath = Join-Path $caseRoot 'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'
    $capturePath = Join-Path $caseRoot 'scripts/wo044/Capture-WO044NativeArguments.ps1'
    $arguments = @('-NoLogo','-NoProfile','-NonInteractive','-File',$harnessPath,'-Iterations','2')
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $pwshPath
    foreach ($argument in $arguments) { $psi.ArgumentList.Add($argument) }
    $psi.WorkingDirectory = $outputRoot
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = $utf8
    $psi.StandardErrorEncoding = $utf8
    $psi.Environment['TEMP'] = $runtimeRoot
    $psi.Environment['TMP'] = $runtimeRoot
    $command = [ordered]@{executable=$pwshPath;arguments=$arguments;display=('"{0}" -NoLogo -NoProfile -NonInteractive -File "{1}" -Iterations 2' -f $pwshPath,$harnessPath);workingDirectory=$outputRoot;childEnvironmentOverrides=@{TEMP=$runtimeRoot;TMP=$runtimeRoot};useShellExecute=$false;createNoWindow=$true}
    Add-Event 'HARNESS_START_REQUEST' $command
    if ($watch.ElapsedMilliseconds -ge 5000) { throw 'Preflight consumed launch allowance; no child started.' }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $psi
    $launchAtMs = $watch.Elapsed.TotalMilliseconds
    if (-not $process.Start()) { throw 'Harness process start returned false.' }
    $processHandle = $process.Handle
    $processStart = $process.StartTime.ToUniversalTime().ToString('O')
    $actualChildExecutable = $process.MainModule.FileName
    $actualChildVersion = $process.MainModule.FileVersionInfo.FileVersion
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $native = Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $process.Id)
    $harnessRecord = [pscustomobject]@{process=$process;pid=$process.Id;handle=$processHandle.ToInt64();startTimeUtc=$processStart;executable=$actualChildExecutable;instance=$runIdentity;commandLine=[string]$native.CommandLine}
    Add-Event 'HARNESS_PROCESS_CREATED' ([ordered]@{pid=$process.Id;handle=$processHandle.ToInt64();startTimeUtc=$processStart;actualExecutable=$actualChildExecutable;actualFileVersion=$actualChildVersion;instance=$runIdentity;instanceScope='driver ownership record, not an injected harness argument';commandLine=$native.CommandLine})
    if (-not $actualChildExecutable.Equals($pwshPath,[StringComparison]::OrdinalIgnoreCase)) { throw 'Actual harness executable mismatch.' }
    while ($true) {
        if ($process.WaitForExit(20)) {
            $harnessExit = $process.ExitCode
            $exitAtMs = $watch.Elapsed.TotalMilliseconds
            Add-Event 'HARNESS_EXIT_CODE_IMMEDIATE' $harnessExit
            break
        }
        Observe-Files
        Observe-CaptureChildren
        if ($watch.ElapsedMilliseconds -ge 55000) { $primary='EXTERNAL_DEADLINE'; throw 'Global driver deadline approaching; 5 seconds reserved for cleanup within 60 seconds.' }
    }
    $primary = if ($harnessExit -eq 0) { 'HARNESS_EXIT_ZERO' } else { 'HARNESS_EXIT_NONZERO' }
} catch {
    $failure = $_.Exception.Message
    if ($primary -eq 'NOT_STARTED') { $primary = if ($null -eq $process) { 'PREFLIGHT_BLOCKED' } else { 'DRIVER_OR_HARNESS_FAILURE' } }
    Add-Event 'PRIMARY_FAILURE' $failure
} finally {
    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        foreach ($record in $owned) {
            if (-not $record.process.HasExited) {
                if ($null -eq $harnessExit) { $record.forced=$true; Stop-Exact $record ([Math]::Max(1,5000-[int]$cleanupWatch.ElapsedMilliseconds)) }
                else { throw 'A tracked capture is still alive after harness exit.' }
            }
            if ($null -eq $record.exitCode -and $record.process.WaitForExit(0)) {
                $record.exitCode = $record.process.ExitCode
                $record.exitObservedAtMs = $watch.Elapsed.TotalMilliseconds
            }
        }
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Exact $harnessRecord ([Math]::Max(1,5000-[int]$cleanupWatch.ElapsedMilliseconds))
            $forcedExit = $process.ExitCode
        }
        if ($null -ne $process -and -not $process.HasExited) { throw 'Harness exit not established.' }
        if ($null -ne $stdoutTask) {
            if (-not $stdoutTask.Wait([Math]::Max(1,5000-[int]$cleanupWatch.ElapsedMilliseconds))) { throw 'Stdout drain deadline.' }
            $stdout = $stdoutTask.GetAwaiter().GetResult()
        }
        if ($null -ne $stderrTask) {
            if (-not $stderrTask.Wait([Math]::Max(1,5000-[int]$cleanupWatch.ElapsedMilliseconds))) { throw 'Stderr drain deadline.' }
            $stderr = $stderrTask.GetAwaiter().GetResult()
        }
        $remaining = @()
        if (Test-Path -LiteralPath $runtimeRoot) {
            Assert-Path $runtimeRoot $outputRoot
            $remaining = @(Get-ChildItem -LiteralPath $runtimeRoot -Force | Select-Object FullName,Attributes)
        }
        $exactExits = @($owned | ForEach-Object { [ordered]@{pid=$_.pid;handle=$_.handle;startTimeUtc=$_.startTimeUtc;instance=$_.instance;executable=$_.executable;hasExited=$_.process.HasExited;exitCode=$_.exitCode;exitObservedAtMs=$_.exitObservedAtMs;forced=$_.forced} })
        $rootChecks = @($seenRoots | ForEach-Object { [ordered]@{path=$_;exists=(Test-Path -LiteralPath $_)} })
        Add-Event 'INDEPENDENT_POSTFLIGHT' ([ordered]@{runtimeRoot=$runtimeRoot;remainingEntries=$remaining;remainingCount=$remaining.Count;observedRoots=$rootChecks;harnessHasExited=($null -ne $process -and $process.HasExited);captureProcesses=$exactExits})
        if ($remaining.Count -ne 0) { throw 'Runtime residue preserved for diagnosis; no recursive cleanup.' }
        $sourceAfter = @(Get-SourceProof)
        if (($sourceBefore | ConvertTo-Json -Compress) -cne ($sourceAfter | ConvertTo-Json -Compress)) { throw 'Allowed source hashes changed.' }
        if ($env:TEMP -cne $parentTemp -or $env:TMP -cne $parentTmp) { throw 'Parent TEMP/TMP changed.' }
        $cleanup = 'PASS_NO_OWNED_RESIDUE_OBSERVED'
    } catch {
        $cleanup='FAIL_OR_NOT_ESTABLISHED'
        $cleanupFailure=$_.Exception.Message
        Add-Event 'CLEANUP_FAILURE' $cleanupFailure
    }
    $cleanupWatch.Stop()
    Add-Event 'CLEANUP_FINISHED' ([ordered]@{result=$cleanup;durationMs=$cleanupWatch.Elapsed.TotalMilliseconds;failure=$cleanupFailure})
    foreach ($record in $owned) { $record.process.Dispose() }
    if ($null -ne $process) { $process.Dispose() }
}

$passed = $harnessExit -eq 0 -and $null -ne $harnessExit -and $null -eq $failure -and $cleanup -eq 'PASS_NO_OWNED_RESIDUE_OBSERVED' -and $stdout.Contains('WO044_QUALIFIED_ITERATIONS=2') -and $stdout.Contains('WO044_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED') -and $watch.ElapsedMilliseconds -lt 60000
$driverCode = if ($passed) { 0 } else { 1 }
$result = [ordered]@{
    case='WR-H01';statuses=@('EXPERIMENTAL','LOCAL_ONLY','NOT_PRODUCTION_APPROVED','NO_CRITICAL_DEPENDENCY');runIdentity=$runIdentity;startedUtc=$startedUtc;endedUtc=[DateTime]::UtcNow.ToString('O');
    primary=$primary;harnessExitCode=$harnessExit;forcedHarnessExitCode=$forcedExit;driverExitCode=$driverCode;failure=$failure;cleanup=$cleanup;cleanupFailure=$cleanupFailure;
    durationAtEvidenceMs=$watch.Elapsed.TotalMilliseconds;harnessLaunchToExitObservedMs=$(if($null -ne $exitAtMs){$exitAtMs-$launchAtMs}else{$null});stopwatchFrequency=[Diagnostics.Stopwatch]::Frequency;
    budgets=@{globalMs=60000;externalCutoffMs=55000;captureWaitMs=10000;cleanupMs=5000};runtime=$observations;command=$command;sourceBefore=$sourceBefore;sourceAfter=$sourceAfter;
    provenance=@{declaredPolicySourceCommit=$entry._policy_source_commit;currentCheckoutSha='NOT_READ_SCOPE_RESTRICTED';pinOrigin='SHA-256 observed from the supplied frozen files before execution; no separate signed manifest supplied'};
    filesObserved=@($seenFiles | Sort-Object);rootsObserved=@($seenRoots | Sort-Object);captureObservationCount=$owned.Count;events=$events;
    observationLimits=@('Frozen harness owns and deletes its raw argument JSON and redirection logs. They are not preserved by this driver.','Capture process codes recorded on first observed exit with held handles; observation times are not exact OS exit times.','Polling metadata may affect scheduling. No source instrumentation or extra native probe.','Independent postflight covers output/runtime and held process handles; it is not an inventory of unrelated processes.')
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'harness.stdout.txt'),$stdout,$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'harness.stderr.txt'),$stderr,$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'run.json'),($result | ConvertTo-Json -Depth 14),$utf8)
$commandDisplay = if ($null -ne $command) { $command.display } else { 'NOT_STARTED' }
$transcript = @("WR_H01_STARTED_UTC=$startedUtc", "COMMAND=$commandDisplay", "WORKING_DIRECTORY=$outputRoot", "CHILD_TEMP=$runtimeRoot", "CHILD_TMP=$runtimeRoot", '--- HARNESS STDOUT ---', $stdout, '--- HARNESS STDERR ---', $stderr, "HARNESS_EXIT_CODE=$harnessExit", "FORCED_HARNESS_EXIT_CODE=$forcedExit", "PRIMARY=$primary", "CLEANUP=$cleanup", "DRIVER_EXIT_CODE=$driverCode", "DURATION_AT_EVIDENCE_MS=$($result.durationAtEvidenceMs)", "HARNESS_DURATION_MS=$($result.harnessLaunchToExitObservedMs)") -join "`r`n"
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'transcript.txt'),$transcript,$utf8)
Write-Output $transcript
exit $driverCode
