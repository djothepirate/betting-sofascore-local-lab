$ErrorActionPreference = 'Stop'
$woRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$evaluationRoot = Join-Path $woRoot '.tmp/wo062-evaluations'
$windowsRoot = Join-Path $evaluationRoot 'ss-windows-runtime/run-02'
$caseRoots = @('WR-H01', 'WR-N01' | ForEach-Object { (Resolve-Path -LiteralPath (Join-Path $windowsRoot $_)).Path })
$matching = @(Get-CimInstance Win32_Process -Filter "Name = 'codex.exe'" | Where-Object {
    $command = $_.CommandLine
    $command -and $command.Contains('--ephemeral') -and @($caseRoots | Where-Object { $command.Contains($_) }).Count -gt 0
} | Select-Object ProcessId, ParentProcessId, CreationDate, ExecutablePath, CommandLine)
$observedProcesses = @(32700,34004,26528,36948 | ForEach-Object {
    $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
    [pscustomobject]@{ pid = $_; present = [bool]$process; action = 'read-only; no stop'; limitation = 'Point-in-time PID observation does not establish original identity or handle ownership.' }
})
$temporaryState = @($caseRoots | ForEach-Object {
    $runtimePath = Join-Path $_ 'output/runtime'
    $items = @(Get-ChildItem -LiteralPath $runtimePath -Recurse -Force | Select-Object FullName, Attributes, Length, CreationTimeUtc, LastWriteTimeUtc)
    [pscustomobject]@{ caseRoot = $_; runtimeRoot = $runtimePath; entries = $items; fileContentRead = $false; deleted = $false }
})
$frozen = @($caseRoots | ForEach-Object {
    $eventPath = Join-Path $_ 'output/events.jsonl'
    $caseId = Split-Path $_ -Leaf
    $trace = Get-Content -LiteralPath (Join-Path $windowsRoot "frozen/$caseId/trace.json") -Raw | ConvertFrom-Json
    $item = Get-Item -LiteralPath $eventPath
    $hash = (Get-FileHash -LiteralPath $eventPath -Algorithm SHA256).Hash.ToLowerInvariant()
    [pscustomobject]@{ caseId = $caseId; eventsSha256 = $hash; matchesFrozenTrace = ($hash -ceq $trace.raw_events_sha256); bytes = $item.Length; lastWriteUtc = $item.LastWriteTimeUtc.ToString('o') }
})
$record = [ordered]@{
    schema = 'wo062-post-run02-host-observation-v1'
    observedAtUtc = [DateTime]::UtcNow.ToString('o')
    scope = 'Read-only host observation after both model sessions ended. Separate from frozen evaluated evidence; not a skill qualification or replacement for native cleanup proof.'
    matchingCodexProcesses = $matching
    processObservations = $observedProcesses
    temporaryState = $temporaryState
    frozenEvents = $frozen
    modelCalls = 0
    processesStopped = 0
    filesDeleted = 0
    persistentConfigurationChanged = $false
}
$destination = Join-Path $PSScriptRoot 'c-post-run02-host-observation.json'
[IO.File]::WriteAllText($destination, ($record | ConvertTo-Json -Depth 8) + "`n", [Text.UTF8Encoding]::new($false))
[pscustomobject]@{ Saved = $destination; MatchingCodexProcesses = $matching.Count; FrozenEventsUnchanged = (@($frozen | Where-Object { -not $_.matchesFrozenTrace }).Count -eq 0); RuntimeEntryCounts = @($temporaryState | ForEach-Object { $_.entries.Count }) } | ConvertTo-Json -Depth 3
