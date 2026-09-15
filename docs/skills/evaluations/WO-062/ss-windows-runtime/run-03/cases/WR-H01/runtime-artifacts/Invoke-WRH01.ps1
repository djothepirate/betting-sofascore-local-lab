[CmdletBinding()]
param()
Set-StrictMode -Version 3
$ErrorActionPreference = 'Stop'
$timer = [Diagnostics.Stopwatch]::StartNew()
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$outRoot = Join-Path $caseRoot 'output'
$runtimeRoot = Join-Path $outRoot 'runtime'
$journal = Join-Path $PSScriptRoot 'stages.txt'
function Mark([string]$Stage) {
    [IO.File]::AppendAllText($journal, ('{0} elapsed_ms={1:F3} {2}' -f [DateTime]::UtcNow.ToString('O'), $timer.Elapsed.TotalMilliseconds, $Stage) + "`n", $utf8)
}
function CheckPath([string]$Path, [string]$Root) {
    if ($Path -match '[\x00-\x1f\x7f"]|(^|[\\/])\.\.([\\/]|$)') { throw 'Unsafe path syntax' }
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($Root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside allowed root' }
    $cursor = $full
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse point refused' }
        }
        $cursor = Split-Path -Parent $cursor
    }
    return $full
}
$result = [ordered]@{
    case = 'WR-H01'; startedUtc = [DateTime]::UtcNow.ToString('O')
    statuses = @('EXPERIMENTAL','LOCAL_ONLY','NOT_PRODUCTION_APPROVED','NO_CRITICAL_DEPENDENCY')
    budgetMs = 60000; childTimeoutMs = 10000; cleanupTimeoutMs = 5000
    primary = 'NOT_STARTED'; nativeExitCode = $null; forcedExitCode = $null
    cleanup = 'NOT_NEEDED'; error = $null; observationErrors = @()
    caseRoot = $caseRoot; runtimeRoot = $runtimeRoot
}
$process = $null
$started = $false
$rootsSeen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$filesSeen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$captureProcesses = [Collections.Generic.List[object]]::new()
$captureIds = [Collections.Generic.HashSet[int]]::new()
try {
    [void](CheckPath $runtimeRoot $outRoot)
    [void](CheckPath $PSScriptRoot $outRoot)
    if (@(Get-ChildItem -LiteralPath $runtimeRoot -Force).Count -ne 0) { throw 'Runtime root must be empty; no removal performed' }
    if (-not $IsWindows -or $PSVersionTable.PSVersion.Major -ne 7) { throw 'Required Windows PowerShell 7 unavailable in driver' }
    $pwsh = (Get-Command pwsh.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $self = [Diagnostics.Process]::GetCurrentProcess()
    $selfExe = $self.MainModule.FileName
    if (-not $pwsh.Equals($selfExe, [StringComparison]::OrdinalIgnoreCase)) { throw 'Resolved pwsh differs from observed driver runtime; no substitution' }
    $versions = [ordered]@{}
    foreach ($key in $PSVersionTable.Keys) { $versions[$key] = [string]$PSVersionTable[$key] }
    $selfCim = Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $PID)
    $result.runtime = [ordered]@{
        PSVersionTable = $versions; PSHOME = $PSHOME; executable = $selfExe
        executableProductVersion = $self.MainModule.FileVersionInfo.ProductVersion
        windows = [Runtime.InteropServices.RuntimeInformation]::OSDescription
        osArchitecture = [string][Runtime.InteropServices.RuntimeInformation]::OSArchitecture
        processArchitecture = [string][Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture
        driverPid = $PID; parentPid = $selfCim.ParentProcessId
        driverWorkingDirectory = (Get-Location).Path
        consoleOutputEncoding = [Console]::OutputEncoding.WebName
        pipelineOutputEncoding = $OutputEncoding.WebName
        redirectedStreamDecoder = 'UTF-8 strict'; evidenceEncoding = 'UTF-8 without BOM'
        childVersionEvidence = 'Exact same executable; child MainModule product version observed; frozen harness does not emit PSVersionTable'
    }
    $scriptPath = Join-Path $caseRoot 'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'
    $capturePath = Join-Path $caseRoot 'scripts/wo044/Capture-WO044NativeArguments.ps1'
    foreach ($path in @($scriptPath, $capturePath, (Join-Path $caseRoot 'scripts/wo036/WO036-CampaignTools.psm1'))) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Frozen dependency missing: $path" }
    }
    $argsVector = @('-NoLogo','-NoProfile','-NonInteractive','-File',$scriptPath,'-Iterations','2')
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $pwsh
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = $utf8
    $psi.StandardErrorEncoding = $utf8
    $psi.WorkingDirectory = $outRoot
    $psi.Environment['TEMP'] = $runtimeRoot
    $psi.Environment['TMP'] = $runtimeRoot
    foreach ($arg in $argsVector) { $psi.ArgumentList.Add($arg) }
    $result.command = [ordered]@{ executable = $pwsh; arguments = $argsVector; workingDirectory = $outRoot; childEnvironmentOverrides = @{TEMP=$runtimeRoot;TMP=$runtimeRoot} }
    $result.commandDisplay = '"' + $pwsh + '" -NoLogo -NoProfile -NonInteractive -File "' + $scriptPath + '" -Iterations 2'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $psi
    Mark 'EXECUTION_START'
    $nativeTimer = [Diagnostics.Stopwatch]::StartNew()
    if (-not $process.Start()) { throw 'Process.Start returned false' }
    $started = $true
    $ownedHandle = $process.Handle
    $result.process = [ordered]@{
        pid = $process.Id; startUtc = $process.StartTime.ToUniversalTime().ToString('O')
        executable = $process.MainModule.FileName; handle = $ownedHandle.ToInt64()
        productVersion = $process.MainModule.FileVersionInfo.ProductVersion
        identity = 'Retained Process object and OS handle, PID, start time, executable and exact arguments'
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $result.primary = 'RUNNING'
    while (-not $process.WaitForExit(40)) {
        if ($timer.ElapsedMilliseconds -ge 55000) { $result.primary = 'WRAPPER_TIMEOUT'; break }
        foreach ($dir in @(Get-ChildItem -LiteralPath $runtimeRoot -Directory -Force)) {
            [void](CheckPath $dir.FullName $runtimeRoot)
            if ($dir.Name -cnotmatch '^WO044 Java JAR argument boundary [0-9a-f-]{36}$') { throw 'Unexpected temporary directory' }
            [void]$rootsSeen.Add($dir.FullName)
            foreach ($file in @(Get-ChildItem -LiteralPath $dir.FullName -File -Force -ErrorAction SilentlyContinue)) { [void]$filesSeen.Add($file.FullName) }
        }
        try {
            foreach ($child in @(Get-CimInstance Win32_Process -Filter ('ParentProcessId={0}' -f $process.Id))) {
                if ($captureIds.Contains([int]$child.ProcessId)) { continue }
                if ([string]$child.ExecutablePath -ine $pwsh -or [string]$child.CommandLine -notlike "*$capturePath*" -or [string]$child.CommandLine -notmatch '-Dwo036.instance=([0-9a-f-]{36})') { continue }
                $token = $Matches[1]
                $childProcess = Get-Process -Id $child.ProcessId -ErrorAction Stop
                $childHandle = $childProcess.Handle
                $childStart = $childProcess.StartTime.ToUniversalTime()
                if ([Math]::Abs(($childStart - $child.CreationDate.ToUniversalTime()).TotalMilliseconds) -gt 20) { $childProcess.Dispose(); continue }
                $record = [ordered]@{ pid=$childProcess.Id; startUtc=$childStart.ToString('O'); executable=[string]$child.ExecutablePath; instanceToken=$token; commandLine=[string]$child.CommandLine; handle=$childHandle.ToInt64(); process=$childProcess }
                $captureProcesses.Add($record)
                [void]$captureIds.Add($childProcess.Id)
            }
        } catch { $result.observationErrors += $_.Exception.Message }
    }
    if ($result.primary -eq 'RUNNING') {
        $result.nativeExitCode = $process.ExitCode
        $result.nativeDurationMs = $nativeTimer.Elapsed.TotalMilliseconds
        $result.primary = if ($result.nativeExitCode -eq 0) { 'EXIT_ZERO' } else { 'EXIT_NONZERO' }
    }
} catch {
    $result.error = $_.Exception.Message
    if ($result.primary -eq 'NOT_STARTED') { $result.primary = 'BLOCKED' } elseif ($result.primary -eq 'RUNNING') { $result.primary = 'OBSERVATION_FAILURE' }
} finally {
    $cleanupTimer = [Diagnostics.Stopwatch]::StartNew()
    if ($started) {
        if (-not $process.HasExited) {
            $process.Kill()
            if ($process.WaitForExit(5000)) { $result.forcedExitCode = $process.ExitCode; $result.cleanup = 'OWNED_WRAPPER_STOPPED' } else { $result.cleanup = 'WRAPPER_EXIT_NOT_PROVED' }
        } else { $result.cleanup = 'WRAPPER_EXIT_CONFIRMED_BY_HANDLE' }
        if ($process.HasExited) {
            if ($null -eq $result.nativeExitCode -and $null -eq $result.forcedExitCode) { $result.nativeExitCode = $process.ExitCode }
            [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'harness.stdout.txt'), $stdoutTask.GetAwaiter().GetResult(), $utf8)
            [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'harness.stderr.txt'), $stderrTask.GetAwaiter().GetResult(), $utf8)
        }
        $result.wrapperExited = $process.HasExited
    }
    $captures = @()
    foreach ($owned in $captureProcesses) {
        $cp = $owned.process
        if (-not $cp.HasExited) { $cp.Kill(); [void]$cp.WaitForExit(5000); $owned.forcedStop = $true }
        $owned.exitedByHandle = $cp.HasExited
        $owned.exitCodeObservedAtPostflight = if ($cp.HasExited) { $cp.ExitCode } else { $null }
        $owned.Remove('process')
        $captures += $owned
        $cp.Dispose()
    }
    $result.observedCaptureProcesses = $captures
    $result.observedTemporaryRoots = @($rootsSeen)
    $result.observedTemporaryFiles = @($filesSeen)
    $result.captureEvidenceLimit = 'Frozen harness captures then deletes argv JSON and logs; auxiliary process/file sampling may be incomplete. Child exit codes sampled after harness return are not immediate per-child measurements.'
    $result.cleanupDurationMs = $cleanupTimer.Elapsed.TotalMilliseconds
    $result.totalDurationMs = $timer.Elapsed.TotalMilliseconds
    $result.endedUtc = [DateTime]::UtcNow.ToString('O')
    Mark 'EXECUTION_END'
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'result.json'), ($result | ConvertTo-Json -Depth 12), $utf8)
    Mark 'EVIDENCE_READY'
    if ($null -ne $process) { $process.Dispose() }
}
Write-Output ('WRH01_PRIMARY={0}; NATIVE_EXIT={1}; ELAPSED_MS={2:F3}' -f $result.primary, $result.nativeExitCode, $result.totalDurationMs)
