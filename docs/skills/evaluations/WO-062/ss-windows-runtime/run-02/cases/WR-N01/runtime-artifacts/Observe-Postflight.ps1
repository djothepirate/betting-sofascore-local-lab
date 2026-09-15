$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = New-Object Text.UTF8Encoding($false, $true)
[Console]::OutputEncoding = $utf8
$r = ConvertFrom-Json ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'result.json'), $utf8))
$basePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../runtime'))
$root = [IO.Path]::GetFullPath($r.temporaryRoot)
if (-not $root.StartsWith($basePath.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Postflight root outside runtime' }
$check = $root
while ($check) {
 if ([IO.Directory]::Exists($check) -or [IO.File]::Exists($check)) {
  if (([IO.File]::GetAttributes($check) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Postflight reparse point refused' }
 }
 $check = [IO.Path]::GetDirectoryName($check)
}
$processChecks = @()
# These PIDs were observed in this run, never inferred from historical inputs.
$records = @($r.processes | ForEach-Object { @{ pid = [int]$_.pid; origin = 'retained Process handle'; startTicks = [long]$_.creationTicks } })
$match = [regex]::Match(($r.processes | Where-Object { $_.mode -eq 'failure' }).stdout, '(?m)^WR_PROBE_PID=(\d+)')
if ($match.Success) { $records += @{ pid = [int]$match.Groups[1].Value; origin = 'fixture output only, no original handle'; startTicks = $null } }
foreach ($record in $records) {
 $checkResult = [ordered]@{ pid = $record.pid; origin = $record.origin; observedUtc = [DateTime]::UtcNow.ToString('o'); processFoundNow = $false; action = 'read-only; no stop' }
 try {
  $p = [Diagnostics.Process]::GetProcessById($record.pid)
  try {
   $checkResult.processFoundNow = $true
   $checkResult.currentCreationUtc = $p.StartTime.ToUniversalTime().ToString('o')
   $checkResult.sameCreationTime = ($null -ne $record.startTicks -and $p.StartTime.ToUniversalTime().Ticks -eq $record.startTicks)
  } finally { $p.Dispose() }
 } catch [ArgumentException] { $checkResult.observation = 'PID absent at observation time' }
 $processChecks += $checkResult
}
$inventory = @()
foreach ($dir in @($root, [string]$r.temporaryDirectory)) {
 if (-not $dir.StartsWith($basePath.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Inventory path escaped runtime' }
 if ([IO.Directory]::Exists($dir)) {
  if (([IO.File]::GetAttributes($dir) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Inventory reparse point refused' }
  foreach ($entry in [IO.Directory]::GetFileSystemEntries($dir)) {
   $attributes = [IO.File]::GetAttributes($entry)
   $metadata = [ordered]@{ path = $entry; attributes = $attributes.ToString(); contentRead = $false }
   if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Unexpected reparse entry refused' }
   if (($attributes -band [IO.FileAttributes]::Directory) -eq 0) {
    $fileInfo = New-Object IO.FileInfo($entry)
    $metadata.length = $fileInfo.Length
    $metadata.createdUtc = $fileInfo.CreationTimeUtc.ToString('o')
    $metadata.lastWriteUtc = $fileInfo.LastWriteTimeUtc.ToString('o')
   }
   $inventory += $metadata
  }
 }
}
$post = [ordered]@{
 observedUtc = [DateTime]::UtcNow.ToString('o'); PSVersion = $PSVersionTable.PSVersion.ToString(); PSEdition = $PSVersionTable.PSEdition
 temporaryRoot = $root; rootAbsent = (-not [IO.Directory]::Exists($root)); processes = $processChecks
 inventory = $inventory; filesReadInTemporaryRoot = 0; filesDeleted = 0; processesStopped = 0
 cleanupStatus = 'UNEXPECTED_RESIDUE_RETAINED_PER_PROTOCOL'; elapsedMs = $clock.Elapsed.TotalMilliseconds
 limitation = 'The PID emitted by the fixture had no retained original handle or creation time; point-in-time absence does not replace that missing ownership proof.'
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'independent-postflight.json'), (ConvertTo-Json -InputObject $post -Depth 8), $utf8)
Write-Output (ConvertTo-Json -InputObject $post -Depth 8)