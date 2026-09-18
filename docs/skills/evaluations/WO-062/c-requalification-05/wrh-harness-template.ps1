[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = New-Object Text.UTF8Encoding($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$artifactRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$outputRoot = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output'))
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $outputRoot 'runtime'))
$harness = [IO.Path]::GetFullPath((Join-Path $caseRoot 'scripts\wo044\Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'))
$capture = [IO.Path]::GetFullPath((Join-Path $caseRoot 'scripts\wo044\Capture-WO044NativeArguments.ps1'))
$module = [IO.Path]::GetFullPath((Join-Path $caseRoot 'scripts\wo036\WO036-CampaignTools.psm1'))
$events = New-Object Collections.ArrayList
$child = $null
$handle = $null
$scriptExit = 1
$nativeExit = $null
$forcedExit = $null
$result = 'NOT_RUN'
$cleanup = 'NOT_OBSERVED'

function Add-Event([string]$Name, $Data) {
    [void]$events.Add([ordered]@{
        event = $Name
        utc = [DateTime]::UtcNow.ToString('o')
        monotonic_ms = $clock.Elapsed.TotalMilliseconds
        data = $Data
    })
}

function Assert-Contained([string]$Path, [string]$Base) {
    if ($Path -match '[\x00-\x1f\x7f"]' -or $Path -match '(^|[\\/])\.\.([\\/]|$)') { throw 'Unsafe path syntax' }
    $full = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetFullPath($Base).TrimEnd('\\')
    if (-not $full.StartsWith($root + '\\', [StringComparison]::OrdinalIgnoreCase)) { throw "Path outside case: $full" }
    $cursor = $full
    while ($cursor) {
        if ([IO.Directory]::Exists($cursor) -or [IO.File]::Exists($cursor)) {
            if (([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
    return $full
}

function Remaining {
    return [Math]::Max(0, [int][Math]::Floor(60000 - $clock.Elapsed.TotalMilliseconds))
}

try {
    foreach ($path in @($artifactRoot, $outputRoot, $harness, $capture, $module)) { Assert-Contained $path $caseRoot | Out-Null }
    foreach ($path in @($harness, $capture, $module)) { if (-not [IO.File]::Exists($path)) { throw "Frozen runtime dependency absent: $path" } }
    if (-not $IsWindows -or $PSVersionTable.PSVersion.Major -ne 7) { throw 'Windows PowerShell 7 required; no runtime substitution' }
    if ([IO.Directory]::Exists($runtimeRoot)) {
        if (@([IO.Directory]::GetFileSystemEntries($runtimeRoot)).Count -ne 0) { throw 'Runtime root must be empty for the one C5 attempt' }
    } else {
        [void][IO.Directory]::CreateDirectory($runtimeRoot)
    }
    $self = [Diagnostics.Process]::GetCurrentProcess()
    $pwsh = @(Get-Command pwsh.exe -CommandType Application -ErrorAction Stop)[0].Source
    if (-not [string]::Equals($self.MainModule.FileName, $pwsh, [StringComparison]::OrdinalIgnoreCase)) { throw 'Driver image differs from resolved PowerShell 7' }
    $runtime = [ordered]@{}
    foreach ($entry in $PSVersionTable.GetEnumerator()) { $runtime[$entry.Key] = [string]$entry.Value }
    Add-Event 'runtime_observed' @{ ps_version_table=$runtime; executable=$self.MainModule.FileName; conductor_pid=$PID; cwd=(Get-Location).Path; runtime_root=$runtimeRoot; source_dependencies=@('scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1','scripts/wo044/Capture-WO044NativeArguments.ps1','scripts/wo036/WO036-CampaignTools.psm1'); module_access='runtime import by unchanged harness only'; evidence_encoding='UTF-8 no BOM' }

    $args = @('-NoLogo', '-NoProfile', '-NonInteractive', '-File', $harness, '-Iterations', '2')
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $pwsh
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WorkingDirectory = $runtimeRoot
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = $utf8
    $info.StandardErrorEncoding = $utf8
    foreach ($argument in $args) { [void]$info.ArgumentList.Add($argument) }
    $info.Environment['TEMP'] = $runtimeRoot
    $info.Environment['TMP'] = $runtimeRoot
    Add-Event 'execution_prepared' @{ file_name=$pwsh; arguments=$args; cwd=$runtimeRoot; child_environment='TEMP and TMP only in the child copy'; source_module_is_runtime_only=$true }
    if ((Remaining) -lt 55000) { throw 'Preflight leaves insufficient C5 harness budget' }

    $child = New-Object Diagnostics.Process
    $child.StartInfo = $info
    if (-not $child.Start()) { throw 'Process.Start returned false' }
    $handle = $child.Handle
    $createdUtc = $child.StartTime.ToUniversalTime().ToString('o')
    $image = $child.MainModule.FileName
    Add-Event 'process_started' @{ pid=$child.Id; handle=$handle.ToInt64(); creation_utc=$createdUtc; image=$image; requested_image=$pwsh; conductor_pid=$PID; command=(('"' + $pwsh + '" ') + ($args -join ' ')); execution_started_after_process_start=$true }
    Write-Output 'WR_PHASE=EXECUTION'
    $stdout = $child.StandardOutput.ReadToEndAsync()
    $stderr = $child.StandardError.ReadToEndAsync()
    while (-not $child.WaitForExit([Math]::Min(25, [Math]::Max(1, (Remaining))))) {
        if ((Remaining) -le 5000) { throw 'Harnais did not return before its reserved cleanup time' }
    }
    $nativeExit = $child.ExitCode
    if (-not [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout, $stderr), [Math]::Min(5000, (Remaining)))) { throw 'Harnais stream drain deadline expired' }
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'harness.stdout.txt'), $stdout.Result, $utf8)
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'harness.stderr.txt'), $stderr.Result, $utf8)
    Add-Event 'harness_result_available' @{ native_exit_code=$nativeExit; elapsed_ms=$clock.Elapsed.TotalMilliseconds; stdout='harness.stdout.txt'; stderr='harness.stderr.txt'; retained_handle=$handle.ToInt64(); exited=$child.HasExited }
    if ($nativeExit -ne 0) { throw "Harnais native exit code: $nativeExit" }
    $result = 'NATIVE_EXIT_ZERO'
    $scriptExit = 0
}
catch {
    Add-Event 'driver_error' @{ type=$_.Exception.GetType().FullName; message=$_.Exception.Message; id=$_.FullyQualifiedErrorId; line=$_.InvocationInfo.ScriptLineNumber; stack=$_.ScriptStackTrace }
    if ($result -eq 'NOT_RUN') { $result='BLOCKED_OR_DRIVER_ERROR' }
    $scriptExit = 1
}
finally {
    try {
        if ($null -ne $child) {
            if (-not $child.HasExited) {
                $identity = ($child.Handle -eq $handle -and [string]::Equals($child.MainModule.FileName, $child.StartInfo.FileName, [StringComparison]::OrdinalIgnoreCase))
                if (-not $identity) { throw 'Exact outer process identity unavailable; forced stop blocked' }
                Add-Event 'ownership_verified_before_kill' @{ pid=$child.Id; handle=$handle.ToInt64(); image=$child.MainModule.FileName; creation_utc=$child.StartTime.ToUniversalTime().ToString('o') }
                $child.Kill()
                if (-not $child.WaitForExit([Math]::Min(5000, (Remaining)))) { throw 'Exact outer process did not exit during cleanup' }
                $forcedExit = $child.ExitCode
                $cleanup = 'EXACT_OUTER_PROCESS_STOPPED'
            } else {
                $cleanup = 'OUTER_PROCESS_EXIT_CONFIRMED_BY_RETAINED_HANDLE'
            }
            $child.Dispose()
        } else {
            $cleanup = 'NO_CHILD_CREATED'
        }
    }
    catch {
        $cleanup = 'INCOMPLETE'
        $scriptExit = 2
        Add-Event 'cleanup_error' @{ type=$_.Exception.GetType().FullName; message=$_.Exception.Message; id=$_.FullyQualifiedErrorId; line=$_.InvocationInfo.ScriptLineNumber; stack=$_.ScriptStackTrace }
    }
    $entries = if ([IO.Directory]::Exists($runtimeRoot)) { @([IO.Directory]::GetFileSystemEntries($runtimeRoot)) } else { @() }
    Add-Event 'driver_return' @{ result=$result; native_exit_code=$nativeExit; forced_exit_code=$forcedExit; cleanup=$cleanup; runtime_entries_after_return=$entries; elapsed_ms=$clock.Elapsed.TotalMilliseconds; budget_ms=60000; within_budget=($clock.Elapsed.TotalMilliseconds -le 60000) }
    $report = [ordered]@{ case='WR-H01'; result=$result; native_exit_code=$nativeExit; forced_exit_code=$forcedExit; cleanup=$cleanup; events=@($events); elapsed_ms=$clock.Elapsed.TotalMilliseconds; budget_ms=60000 }
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'execution.json'), ($report | ConvertTo-Json -Depth 12), $utf8)
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'evidence-available.txt'), ('WR_EVIDENCE_AVAILABLE UTC=' + [DateTime]::UtcNow.ToString('o')), $utf8)
    Write-Output 'WR_PHASE=PROOFS'
    Write-Output ('WR_DRIVER_RESULT=' + $result + '; WR_NATIVE_EXIT_CODE=' + $nativeExit + '; WR_CLEANUP=' + $cleanup)
}

exit $scriptExit
