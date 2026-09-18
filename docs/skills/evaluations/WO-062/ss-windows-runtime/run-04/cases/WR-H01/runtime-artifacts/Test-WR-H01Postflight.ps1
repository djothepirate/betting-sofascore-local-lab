Set-StrictMode -Version 3.0
$ErrorActionPreference='Stop'
$watch=[Diagnostics.Stopwatch]::StartNew()
$caseRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime=[IO.Path]::GetFullPath((Join-Path $caseRoot 'output/runtime'))
if (-not $runtime.StartsWith($caseRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unconfined postflight path' }
$ancestor=$runtime
while ($ancestor) {
    if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Postflight reparse point' }
    $ancestor=[IO.Path]::GetDirectoryName($ancestor)
}
$execution=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'execution.json') -Raw -Encoding utf8 | ConvertFrom-Json
$exactState='NO_OUTER_IDENTITY_RECORDED'
if ($null -ne $execution.child) {
    $current=Get-Process -Id $execution.child.pid -ErrorAction SilentlyContinue
    if ($null -eq $current) { $exactState='PID_ABSENT' }
    else {
        try {
            $same=$current.StartTime.ToUniversalTime().ToString('O') -ceq $execution.child.startedUtc
            $exactState=if ($same) {'EXACT_INSTANCE_STILL_PRESENT'} else {'PID_REUSED_DIFFERENT_INSTANCE_UNTOUCHED'}
        } finally { $current.Dispose() }
    }
}
$remaining=@(Get-ChildItem -LiteralPath $runtime -Force | Select-Object Name,FullName,Attributes)
$result=[ordered]@{
    marker='WR_POSTFLIGHT'; utc=[DateTime]::UtcNow.ToString('O')
    command="& './output/runtime-artifacts/Test-WR-H01Postflight.ps1'"
    runtimeRoot=$runtime; runtimeEntryCount=$remaining.Count; runtimeEntries=$remaining
    outerPid=$execution.child.pid; outerIdentityStatus=$exactState
    harnessDescendantsEvidence='Frozen harness stdout reports exact owned process residual count; no independent descendant identity log exported by that harness'
    runtimeBaseRetainedEmpty=($remaining.Count -eq 0)
    cleanupPerformedBy='Frozen WO044 harness; postflight performs no deletion or termination'
    elapsedMs=$watch.Elapsed.TotalMilliseconds
}
$json=$result | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'postflight.json'),$json,[Text.UTF8Encoding]::new($false,$true))
Write-Output $json
