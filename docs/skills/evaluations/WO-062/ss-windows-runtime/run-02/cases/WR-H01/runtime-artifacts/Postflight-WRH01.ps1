Set-StrictMode -Version 3.0
$ErrorActionPreference='Stop'
$watch=[Diagnostics.Stopwatch]::StartNew()
$root=[IO.Path]::GetFullPath((Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$runtime=[IO.Path]::GetFullPath((Join-Path $root 'output/runtime'))
$run=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'execution.json') -Raw -Encoding utf8 | ConvertFrom-Json
$prefix=[IO.Path]::GetFullPath((Join-Path $root 'output'))+'\'
if (-not $runtime.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'Postflight boundary failed.' }
$cursor=$runtime
while ($cursor) {
    if (Test-Path -LiteralPath $cursor) {
        if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Postflight reparse point refused.' }
    }
    $cursor=[IO.Path]::GetDirectoryName($cursor)
}
$entries=@(Get-ChildItem -LiteralPath $runtime -Force | Select-Object FullName,Attributes)
$ownedIdentityStatus='NOT_FOUND'
$current=Get-Process -Id $run.identity.pid -ErrorAction SilentlyContinue
if ($null -ne $current) {
    try {
        $started=$current.StartTime.ToUniversalTime().ToString('O')
        $ownedIdentityStatus=if($started -ceq $run.identity.startUtc){'EXACT_IDENTITY_PRESENT'}else{'PID_REUSED_DIFFERENT_START_TIME'}
    } finally { $current.Dispose() }
}
$sources=@($run.sourceBefore | ForEach-Object {
    $path=Join-Path $root $_.relativePath
    $hash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    [pscustomobject]@{relativePath=$_.relativePath;before=$_.sha256;after=$hash;identical=($hash -ceq $_.sha256)}
})
$roots=@($run.rootsObserved | ForEach-Object { [pscustomobject]@{path=$_;exists=(Test-Path -LiteralPath $_)} })
$watch.Stop()
$result=[ordered]@{utc=[DateTime]::UtcNow.ToString('O');durationMs=$watch.Elapsed.TotalMilliseconds;runtimeRoot=$runtime;rootChainWithoutReparse=$true;runtimeEntries=$entries;runtimeEntryCount=$entries.Count;temporaryRoots=$roots;harnessPid=$run.identity.pid;harnessIdentityStatus=$ownedIdentityStatus;sources=$sources;allSourcesIdentical=(@($sources | Where-Object {-not $_.identical}).Count -eq 0);captureProcesses='Internal frozen-harness postflight marker; no external CIM inventory available';deletedByThisPostflight=@()}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'independent-postflight.json'),($result | ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false,$true))
$result | ConvertTo-Json -Depth 6
if ($entries.Count -ne 0 -or $ownedIdentityStatus -eq 'EXACT_IDENTITY_PRESENT' -or -not $result.allSourcesIdentical -or @($roots | Where-Object exists).Count -ne 0) { exit 1 }
exit 0
