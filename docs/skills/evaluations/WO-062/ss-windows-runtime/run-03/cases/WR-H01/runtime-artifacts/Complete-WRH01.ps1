Set-StrictMode -Version 3
$ErrorActionPreference = 'Stop'
$watch = [Diagnostics.Stopwatch]::StartNew()
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $root 'output'
$runtime = Join-Path $output 'runtime'
if (-not $runtime.StartsWith($output + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Postflight boundary invalid' }
$cursor = $runtime
while ($cursor) {
    if (Test-Path -LiteralPath $cursor) {
        if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Postflight reparse point refused' }
    }
    $cursor = Split-Path -Parent $cursor
}
$entries = @(Get-ChildItem -LiteralPath $runtime -Force)
$postflight = [ordered]@{
    utc = [DateTime]::UtcNow.ToString('O')
    runtimeRoot = $runtime
    runtimeRootExists = Test-Path -LiteralPath $runtime -PathType Container
    immediateEntryCount = $entries.Count
    entries = @($entries | ForEach-Object { $_.FullName })
    cleanupDeletionPerformed = $false
    ownedNativeProcessCount = 0
    processEvidence = 'Driver result: native process creation was not reached; no child PID/handle recorded. No third-party process queried or stopped by postflight.'
    temporaryRootCreated = $false
    conclusion = if ($entries.Count -eq 0) { 'RUNTIME_EMPTY_NO_OWNED_TEMPORARY_RESOURCE' } else { 'UNEXPECTED_RESIDUE_NOT_DELETED' }
    elapsedMs = $watch.Elapsed.TotalMilliseconds
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'postflight.json'), ($postflight | ConvertTo-Json -Depth 6), $utf8)
$context = [ordered]@{
    driverToolEvent = '2c1d94'; driverToolExitCode = 0; driverToolWallSeconds = 0.4056694
    driverCommand = "& '.\output\runtime-artifacts\Invoke-WRH01.ps1'"
    driverOutput = 'WRH01_PRIMARY=BLOCKED; NATIVE_EXIT=; ELAPSED_MS=194,609'
    promptToolEvent = 'cb2997'; promptToolExitCode = 0
    promptUtc = '2026-09-15T22:44:24.4114165Z'; promptMarker = 'WR_PROMPT_RETURNED'
    runtimeObservationToolEvent = '9107a2'
    observedPSVersion = '7.6.6'; observedPSEdition = 'Core'; observedWindows = 'Microsoft Windows 10.0.26200'
    resolvedPwsh = 'C:\Program Files\PowerShell\7\pwsh.exe'; resolvedFileVersion = '7.6.6.0'
    requestedHarnessLaunchCount = 0
    nativeExitCode = $null
    limitation = 'Access denied during driver preflight. Error invocation, stack and failing line were not persisted. Runtime details assembled after the failing preflight operation were not saved. This does not establish that PowerShell 7 is unavailable or that the frozen harness is faulty.'
    missingEvidence = @('EXECUTION_START marker (native launch not reached)', 'native stdout/stderr', 'native process identity', 'native duration and exit code', 'current argument captures', 'effective child PSVersionTable/PSHOME/architecture', 'per-child codes and timing')
    retry = 'None; finalization follows wrh-flow-addendum.md'
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution-context.json'), ($context | ConvertTo-Json -Depth 6), $utf8)
foreach ($stage in @('POSTFLIGHT_COMPLETE','CONCLUSION_READY')) {
    [IO.File]::AppendAllText((Join-Path $PSScriptRoot 'stages.txt'), ('{0} finalization_elapsed_ms={1:F3} {2}' -f [DateTime]::UtcNow.ToString('O'), $watch.Elapsed.TotalMilliseconds, $stage) + "`n", $utf8)
}
Write-Output ('WR_POSTFLIGHT={0}; ENTRY_COUNT={1}; ELAPSED_MS={2:F3}' -f $postflight.conclusion, $postflight.immediateEntryCount, $postflight.elapsedMs)
