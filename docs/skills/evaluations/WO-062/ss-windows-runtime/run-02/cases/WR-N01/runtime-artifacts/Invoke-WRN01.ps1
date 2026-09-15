[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$globalClock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = New-Object Text.UTF8Encoding($false, $true)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$artifacts = [IO.Path]::GetFullPath($PSScriptRoot)
$runtimeBase = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output/runtime'))
$sessionId = [Guid]::NewGuid().ToString()
$runRoot = Join-Path $runtimeBase ('WR native probe ' + $sessionId)
$javaTemp = Join-Path $runRoot 'temp'
$fixture = Join-Path $caseRoot 'inputs/fixtures/NativeProbe.java'
$events = Join-Path $artifacts 'events.jsonl.txt'
$reportPath = Join-Path $artifacts 'result.json'
$owned = New-Object 'System.Collections.Generic.List[object]'
$createdDirs = New-Object 'System.Collections.Generic.List[string]'
$observedProcesses = New-Object 'System.Collections.Generic.List[object]'
$problems = New-Object 'System.Collections.Generic.List[string]'
$parentEnvironment = @{}
$environmentNames = @('TEMP','TMP','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH','PSModulePath')
foreach ($name in $environmentNames) { $parentEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$result = [ordered]@{
 schema = 'WR-N01-session-1'; sessionId = $sessionId
 status = 'RUNNING'; startedUtc = [DateTime]::UtcNow.ToString('o')
 invariants = @('EXPERIMENTAL','LOCAL_ONLY','NOT_PRODUCTION_APPROVED','NO_CRITICAL_DEPENDENCY')
 caseRoot = $caseRoot; temporaryRoot = $runRoot; temporaryDirectory = $javaTemp
 budgetsMs = @{ version = 5000; failure = 10000; readiness = 10000; afterReady = 1500; cleanup = 5000; global = 45000 }
 processes = $observedProcesses; errors = $problems
 sourceCommit = 'NOT_OBSERVED_GIT_OUTSIDE_READ_ALLOWLIST'
}
function Assert-SafePath([string]$Path, [string]$Base) {
 if ($Path -match '[\x00-\x1f\x7f"]' -or $Path -match '(^|[\\/])\.\.([\\/]|$)') { throw "Unsafe path: $Path" }
 if (-not [IO.Path]::IsPathRooted($Path)) { throw "Not absolute: $Path" }
 $full = [IO.Path]::GetFullPath($Path)
 if (-not $full.StartsWith($Base.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Outside allowed base: $full" }
 $current = $full
 while ($current) {
  if ([IO.Directory]::Exists($current) -or [IO.File]::Exists($current)) {
   if (([IO.File]::GetAttributes($current) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point refused: $current" }
  }
  $current = [IO.Path]::GetDirectoryName($current)
 }
 return $full
}
function Emit([string]$Kind, $Data) {
 $event = [ordered]@{ utc = [DateTime]::UtcNow.ToString('o'); monotonicMs = $globalClock.Elapsed.TotalMilliseconds; kind = $Kind; data = $Data }
 $json = ConvertTo-Json -InputObject $event -Depth 12 -Compress
 [IO.File]::AppendAllText($events, $json + "`r`n", $utf8)
 Write-Host $json
}
function Hash([string]$Path) {
 $stream = [IO.File]::OpenRead($Path)
 $sha = [Security.Cryptography.SHA256]::Create()
 try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
 finally { $sha.Dispose(); $stream.Dispose() }
}
function Quote([string]$Value) {
 if ($Value -match '[\x00-\x1f\x7f"]' -or $Value.EndsWith('\')) { throw 'Argument cannot be represented by restricted quote helper' }
 return '"' + $Value + '"'
}
function Remaining([int]$Cap, [int]$Reserve = 0) {
 $left = [Math]::Floor(45000 - $globalClock.Elapsed.TotalMilliseconds - $Reserve)
 if ($left -le 0) { throw 'Global monotonic budget exhausted' }
 return [int][Math]::Min($Cap, $left)
}
function Start-Java([string]$Mode, [string]$Id, [string[]]$Arguments) {
 $psi = New-Object Diagnostics.ProcessStartInfo
 $psi.FileName = $script:javaExe
 $psi.Arguments = (($Arguments | ForEach-Object { Quote $_ }) -join ' ')
 $psi.WorkingDirectory = $runRoot
 $psi.UseShellExecute = $false
 $psi.CreateNoWindow = $true
 $psi.RedirectStandardOutput = $true
 $psi.RedirectStandardError = $true
 $psi.StandardOutputEncoding = $utf8
 $psi.StandardErrorEncoding = $utf8
 $psi.EnvironmentVariables['TEMP'] = $javaTemp
 $psi.EnvironmentVariables['TMP'] = $javaTemp
 foreach ($name in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) { $psi.EnvironmentVariables.Remove($name) }
 $p = New-Object Diagnostics.Process
 $p.StartInfo = $psi
 $watch = [Diagnostics.Stopwatch]::StartNew()
 $record = [ordered]@{
  mode = $Mode; runId = $Id; executable = $psi.FileName; arguments = $psi.Arguments
  command = (Quote $psi.FileName) + ' ' + $psi.Arguments; workingDirectory = $psi.WorkingDirectory
  targetedEnvironment = @{ TEMP = $javaTemp; TMP = $javaTemp; removedNames = @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH'); PSModulePath = 'inherited unchanged' }
  useShellExecute = $false; createNoWindow = $true; stdoutEncoding = 'UTF-8 strict'; stderrEncoding = 'UTF-8 strict'
  startedUtc = [DateTime]::UtcNow.ToString('o'); startGlobalMs = $globalClock.Elapsed.TotalMilliseconds
  outcome = 'STARTING'; nativeExitCode = $null; forcedExitCode = $null; exitCaptureSource = $null
  readyObservedMs = $null; stdout = ''; stderr = ''; ownedExitConfirmed = $false
 }
 $observedProcesses.Add($record)
 Emit 'COMMAND' $record
 if (-not $p.Start()) { $p.Dispose(); throw "Process start failed: $Mode" }
 $item = @{ process = $p; record = $record; clock = $watch; handle = $null; startTicks = $null; executable = $null; readTask = $null; stderrTask = $null; lines = (New-Object 'System.Collections.Generic.List[string]'); eof = $false }
 $owned.Add($item)
 $item.handle = $p.Handle
 $record.pid = $p.Id
 $record.handle = $item.handle.ToInt64()
 $created = $p.StartTime.ToUniversalTime()
 $item.startTicks = $created.Ticks
 $record.creationUtc = $created.ToString('o')
 $record.creationTicks = $item.startTicks
 try { $item.executable = $p.MainModule.FileName } catch { $record.executableObservationError = $_.Exception.Message }
 $record.observedExecutable = $item.executable
 $item.stderrTask = $p.StandardError.ReadToEndAsync()
 $item.readTask = $p.StandardOutput.ReadLineAsync()
 Emit 'PROCESS_OWNED' @{ mode = $Mode; runId = $Id; pid = $p.Id; handle = $record.handle; creationUtc = $record.creationUtc; executable = $item.executable }
 return $item
}
function Pump($Item) {
 while (-not $Item.eof -and $Item.readTask.IsCompleted) {
  $line = $Item.readTask.GetAwaiter().GetResult()
  if ($null -eq $line) { $Item.eof = $true; break }
  $Item.lines.Add($line)
  if ($line -ceq 'WR_PROBE_READY' -and $null -eq $Item.record.readyObservedMs) {
   $Item.record.readyObservedMs = $Item.clock.Elapsed.TotalMilliseconds
   $Item.record.readyGlobalMs = $globalClock.Elapsed.TotalMilliseconds
   $Item.record.readyUtc = [DateTime]::UtcNow.ToString('o')
  }
  Emit 'STDOUT_LINE' @{ mode = $Item.record.mode; pid = $Item.record.pid; processElapsedMs = $Item.clock.Elapsed.TotalMilliseconds; line = $line }
  $Item.readTask = $Item.process.StandardOutput.ReadLineAsync()
 }
}
function Capture-Exit($Item, [bool]$Forced) {
 # Caller has observed exit. No native command or cleanup occurs before this copy.
 $code = $Item.process.ExitCode
 $Item.record.exitCaptureGlobalMs = $globalClock.Elapsed.TotalMilliseconds
 $Item.record.exitCaptureUtc = [DateTime]::UtcNow.ToString('o')
 $Item.record.exitCaptureSource = 'Process.ExitCode immediately after observed exit'
 if ($Forced) { $Item.record.forcedExitCode = $code } else { $Item.record.nativeExitCode = $code }
 $Item.record.exitElapsedMs = $Item.clock.Elapsed.TotalMilliseconds
 $Item.record.ownedExitConfirmed = $true
 Emit 'EXIT_CAPTURED' @{ mode = $Item.record.mode; pid = $Item.record.pid; forced = $Forced; code = $code; elapsedMs = $Item.record.exitElapsedMs }
}
function Drain($Item) {
 $drainClock = [Diagnostics.Stopwatch]::StartNew()
 while (-not $Item.eof -and $drainClock.ElapsedMilliseconds -lt 1000) { Pump $Item; if (-not $Item.eof) { Start-Sleep -Milliseconds 5 } }
 if (-not $Item.eof -or -not $Item.stderrTask.Wait((Remaining 1000))) { throw "Stream drain incomplete: $($Item.record.mode)" }
 $Item.record.stdout = ($Item.lines -join "`n") + "`n"
 $Item.record.stderr = $Item.stderrTask.GetAwaiter().GetResult()
 [IO.File]::WriteAllText((Join-Path $artifacts ($Item.record.mode + '.stdout.txt')), $Item.record.stdout, $utf8)
 [IO.File]::WriteAllText((Join-Path $artifacts ($Item.record.mode + '.stderr.txt')), $Item.record.stderr, $utf8)
}
function Stop-Owned($Item) {
 $cleanupClock = [Diagnostics.Stopwatch]::StartNew()
 $p = $Item.process
 if ($p.HasExited) { Capture-Exit $Item $false; $Item.record.cleanup = 'already exited'; return }
 if ($p.Handle -ne $Item.handle -or $p.Id -ne $Item.record.pid -or $p.StartTime.ToUniversalTime().Ticks -ne $Item.startTicks) { throw 'Process ownership mismatch; termination refused' }
 $actualExe = $p.MainModule.FileName
 if (-not [string]::Equals($actualExe, $script:javaExe, [StringComparison]::OrdinalIgnoreCase)) { throw 'Executable ownership mismatch; termination refused' }
 Emit 'KILL_OWNED' @{ pid = $p.Id; handle = $Item.handle.ToInt64(); runId = $Item.record.runId; creationUtc = $Item.record.creationUtc; executable = $actualExe }
 $p.Kill()
 if (-not $p.WaitForExit((Remaining 5000))) { $Item.record.cleanup = 'exit not confirmed within bound'; throw 'Owned process did not exit within cleanup bound' }
 Capture-Exit $Item $true
 $Item.record.cleanupElapsedMs = $cleanupClock.Elapsed.TotalMilliseconds
 $Item.record.cleanup = 'owned process exited after Kill()'
}
function Assert-IdentityOutput($Item) {
 if (-not $Item.lines.Contains('WR_PROBE_RUN=' + $Item.record.runId)) { throw 'Fixture UUID mismatch' }
 if (-not $Item.lines.Contains('WR_PROBE_PID=' + $Item.record.pid)) { throw 'Fixture PID mismatch' }
 $expectedText = 'WR_PROBE_TEXT=caf' + [char]0x00e9 + ' ' + [char]0x00e9 + 'quipe'
 if (-not $Item.lines.Contains($expectedText)) { throw 'UTF-8 content mismatch' }
 $Item.record.fixtureIdentityVerified = $true
 $Item.record.utf8TextVerified = $true
}
$exitCode = 1
try {
 [void](Assert-SafePath $artifacts $caseRoot)
 [void](Assert-SafePath $runRoot $runtimeBase)
 [void](Assert-SafePath $javaTemp $runRoot)
 if ([IO.File]::Exists($events) -or [IO.File]::Exists($reportPath)) { throw 'Evidence already exists; no overwrite or retry permitted' }
 $psProcess = [Diagnostics.Process]::GetCurrentProcess()
 $psTable = @{}; foreach ($key in $PSVersionTable.Keys) { $psTable[$key] = [string]$PSVersionTable[$key] }
 $result.runtime = [ordered]@{
  PSVersionTable = $psTable; PSHOME = $PSHOME; executable = $psProcess.MainModule.FileName
  pid = $PID; creationUtc = $psProcess.StartTime.ToUniversalTime().ToString('o')
  executableFileVersion = $psProcess.MainModule.FileVersionInfo.FileVersion
  windows = [Environment]::OSVersion.VersionString; platform = [string][Environment]::OSVersion.Platform
  is64BitOS = [Environment]::Is64BitOperatingSystem; is64BitProcess = [Environment]::Is64BitProcess
  processArchitecture = $env:PROCESSOR_ARCHITECTURE; clr = [Environment]::Version.ToString()
  currentDirectory = (Get-Location).Path; processCurrentDirectory = [Environment]::CurrentDirectory
  consoleOutputEncoding = [Console]::OutputEncoding.WebName; evidenceEncoding = 'UTF-8 without BOM'; scriptEncoding = 'UTF-8 without BOM, ASCII source'
  stopwatchFrequency = [Diagnostics.Stopwatch]::Frequency; stopwatchHighResolution = [Diagnostics.Stopwatch]::IsHighResolution
 }
 Emit 'RUNTIME' $result.runtime
 if ($PSVersionTable.PSEdition -ne 'Desktop' -or $PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -ne 1 -or [Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'Required Windows PowerShell 5.1 Desktop runtime absent' }
 $result.fixture = @{ path = $fixture; sha256Before = (Hash $fixture) }
 $result.conductorSha256 = Hash $PSCommandPath
 # Get-Command resolves an application; no JAVA_HOME assumption and no runtime substitution.
 $javaCommand = Get-Command -Name java.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1
 $script:javaExe = [IO.Path]::GetFullPath($javaCommand.Source)
 $result.java = @{ resolvedExecutable = $script:javaExe; fileVersion = [Diagnostics.FileVersionInfo]::GetVersionInfo($script:javaExe).FileVersion }
 if ([IO.Directory]::Exists($runRoot) -or [IO.File]::Exists($runRoot)) { throw 'Fresh temporary root already exists' }
 [void][IO.Directory]::CreateDirectory($runtimeBase)
 [void][IO.Directory]::CreateDirectory($runRoot); $createdDirs.Add($runRoot)
 [void][IO.Directory]::CreateDirectory($javaTemp); $createdDirs.Add($javaTemp)
 $result.ownedDirectories = @($createdDirs.ToArray())
 $result.ownedFiles = @()
 Emit 'TEMP_ROOT_CREATED' @{ root = $runRoot; temp = $javaTemp; checks = 'absolute; fresh; canonical prefix plus separator; no reparse points; contains spaces' }
 $common = @('-XX:-UsePerfData', ('-Djava.io.tmpdir=' + $javaTemp), ('-Duser.home=' + $runRoot))
 $version = Start-Java 'version' ([Guid]::NewGuid().ToString()) ($common + @('--version'))
 if ($version.process.WaitForExit((Remaining 5000 5000))) { Capture-Exit $version $false } else { $version.record.outcome = 'VERSION_TIMEOUT'; throw 'Java version preflight exceeded 5000 ms' }
 Drain $version
 if ($version.record.nativeExitCode -ne 0 -or $version.record.stdout -notmatch '(?m)^\s*(?:java|openjdk)\s+(?:version\s+)?["]?25(?:[.\-"\s]|$)') { $version.record.outcome = 'PREREQUISITE_FAILED'; throw 'Local Java effective version is not 25 or version command failed' }
 $version.record.outcome = 'JAVA_25_OBSERVED'
 $result.java.versionOutput = $version.record.stdout
 Emit 'PREFLIGHT_COMPLETE' @{ elapsedMs = $globalClock.Elapsed.TotalMilliseconds; javaNativeCode = $version.record.nativeExitCode }
 if ((45000 - $globalClock.Elapsed.TotalMilliseconds) -lt 16000) { throw 'Insufficient global budget for failure plus cleanup' }
 $failureId = [Guid]::NewGuid().ToString()
 $failure = Start-Java 'failure' $failureId ($common + @($fixture, 'failure', $failureId))
 if ($failure.process.WaitForExit((Remaining 10000 5000))) { Capture-Exit $failure $false; $failure.record.outcome = 'NATIVE_EXIT' } else { $failure.record.outcome = 'FAILURE_DEADLINE_EXPIRED'; throw 'Failure mode did not exit in its bound' }
 Drain $failure
 Assert-IdentityOutput $failure
 if ($null -eq $failure.record.readyObservedMs) { throw 'Failure READY not observed' }
 if ((45000 - $globalClock.Elapsed.TotalMilliseconds) -lt 17500) { throw 'Insufficient global budget for readiness plus expiry and cleanup' }
 $sleepId = [Guid]::NewGuid().ToString()
 $sleeper = Start-Java 'sleep' $sleepId ($common + @($fixture, 'sleep', $sleepId))
 while ($null -eq $sleeper.record.readyObservedMs -and $sleeper.clock.Elapsed.TotalMilliseconds -lt 10000) {
  Pump $sleeper
  if ($null -ne $sleeper.record.readyObservedMs) { break }
  if ($sleeper.process.HasExited) { Capture-Exit $sleeper $false; $sleeper.record.outcome = 'EXIT_BEFORE_READY'; throw 'Sleep exited before READY' }
  [void](Remaining 1 5000)
  Start-Sleep -Milliseconds 5
 }
 if ($null -eq $sleeper.record.readyObservedMs -or $sleeper.record.readyObservedMs -ge 10000) { $sleeper.record.outcome = 'READINESS_TIMEOUT'; throw 'Sleep READY not observed within 10000 ms' }
 Assert-IdentityOutput $sleeper
 $sinceReady = $sleeper.clock.Elapsed.TotalMilliseconds - $sleeper.record.readyObservedMs
 $waitMs = [int][Math]::Max(0, [Math]::Ceiling(1500 - $sinceReady))
 $sleeper.record.waitAfterReadyRequestedMs = $waitMs
 if ($sleeper.process.WaitForExit($waitMs)) {
  Capture-Exit $sleeper $false
  $sleeper.record.outcome = 'EXIT_BEFORE_TIMEOUT'
  throw 'Sleep exited before the expected timeout observation'
 }
 $sleeper.record.timeoutAfterReadyObservedMs = $sleeper.clock.Elapsed.TotalMilliseconds - $sleeper.record.readyObservedMs
 $sleeper.record.timeoutGlobalMs = $globalClock.Elapsed.TotalMilliseconds
 $sleeper.record.timeoutUtc = [DateTime]::UtcNow.ToString('o')
 $sleeper.record.outcome = 'TIMEOUT_AFTER_READY'
 Emit 'TIMEOUT_OBSERVED' @{ pid = $sleeper.record.pid; readyMs = $sleeper.record.readyObservedMs; afterReadyMs = $sleeper.record.timeoutAfterReadyObservedMs; stillRunning = (-not $sleeper.process.HasExited) }
 Stop-Owned $sleeper
 Drain $sleeper
 $result.status = 'OBSERVATIONS_COMPLETE'
 $exitCode = 0
}
catch {
 $problems.Add($_.Exception.Message)
 $result.status = 'BLOCKED_OR_INCOMPLETE'
 $result.errorLocation = $_.ScriptStackTrace
 Emit 'PRIMARY_ERROR' @{ message = $_.Exception.Message; stack = $_.ScriptStackTrace }
}
finally {
 $postClock = [Diagnostics.Stopwatch]::StartNew()
 foreach ($item in $owned) {
  try {
   if (-not $item.process.HasExited) { Stop-Owned $item }
   elseif (-not $item.record.ownedExitConfirmed) { Capture-Exit $item $false }
   if (-not $item.process.HasExited) { throw 'Owned handle remains alive' }
   $item.record.finalHandleExitConfirmed = $true
   Emit 'HANDLE_POSTFLIGHT' @{ pid = $item.record.pid; runId = $item.record.runId; exited = $item.process.HasExited; creationUtc = $item.record.creationUtc }
   if ($null -ne $item.readTask -and $null -ne $item.stderrTask) { Drain $item }
   $item.process.Dispose()
   $item.record.handleReleased = $true
  } catch { $problems.Add('Process cleanup: ' + $_.Exception.Message); $exitCode = 2 }
 }
 $inventory = New-Object 'System.Collections.Generic.List[string]'
 try {
  foreach ($dir in $createdDirs) {
   [void](Assert-SafePath $dir $runtimeBase)
   if ([IO.Directory]::Exists($dir)) {
    foreach ($entry in [IO.Directory]::GetFileSystemEntries($dir)) {
     [void](Assert-SafePath $entry $runRoot)
     $inventory.Add($entry)
     if (-not $createdDirs.Contains($entry)) { throw "Unexpected residue retained, removal refused: $entry" }
    }
   }
  }
  $result.inventoryBeforeCleanup = @($inventory.ToArray())
  if (@($owned | Where-Object { -not $_.record.ownedExitConfirmed }).Count -gt 0) { throw 'Directory cleanup refused while process exit unconfirmed' }
  for ($i = $createdDirs.Count - 1; $i -ge 0; $i--) {
   $dir = $createdDirs[$i]
   [void](Assert-SafePath $dir $runtimeBase)
   if ([IO.Directory]::Exists($dir)) {
    if ([IO.Directory]::GetFileSystemEntries($dir).Length -ne 0) { throw "Directory not empty: $dir" }
    [IO.Directory]::Delete($dir, $false)
    Emit 'OWNED_EMPTY_DIRECTORY_DELETED' @{ path = $dir }
   }
  }
 } catch { $problems.Add('Filesystem cleanup: ' + $_.Exception.Message); $exitCode = 2 }
 $unchanged = $true
 foreach ($name in $environmentNames) { if ([Environment]::GetEnvironmentVariable($name, 'Process') -cne $parentEnvironment[$name]) { $unchanged = $false } }
 $result.postflight = @{
  rootAbsent = (-not [IO.Directory]::Exists($runRoot) -and -not [IO.File]::Exists($runRoot))
  ownedProcessCount = $owned.Count
  allOwnedHandlesExited = (@($owned | Where-Object { -not $_.record.ownedExitConfirmed }).Count -eq 0)
  parentTargetedEnvironmentUnchanged = $unchanged
  cleanupElapsedMs = $postClock.Elapsed.TotalMilliseconds
  observedUtc = [DateTime]::UtcNow.ToString('o')
 }
 if ($result.Contains('fixture')) { $result.fixture.sha256After = Hash $fixture; $result.fixture.unchanged = $result.fixture.sha256Before -ceq $result.fixture.sha256After }
 if (-not $result.postflight.rootAbsent -or -not $unchanged) { $exitCode = 2 }
 $result.totalElapsedMs = $globalClock.Elapsed.TotalMilliseconds
 $result.withinGlobalBudget = $result.totalElapsedMs -le 45000
 if (-not $result.withinGlobalBudget) { $problems.Add('Global 45000 ms budget exceeded'); $exitCode = 2 }
 $result.conductorExitCode = $exitCode
 $result.finishedUtc = [DateTime]::UtcNow.ToString('o')
 if ($exitCode -eq 2) { $result.status = 'OBSERVATION_OR_CLEANUP_FAILURE' }
 Emit 'POSTFLIGHT' $result.postflight
 [IO.File]::WriteAllText($reportPath, (ConvertTo-Json -InputObject $result -Depth 15), $utf8)
 Emit 'CONDUCTOR_FINISHED' @{ exitCode = $exitCode; elapsedMs = $result.totalElapsedMs; status = $result.status }
}
exit $exitCode