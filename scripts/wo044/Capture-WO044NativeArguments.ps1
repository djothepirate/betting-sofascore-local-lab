Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

if ($args.Count -lt 2) {
    throw 'WO-044 argument capture requires an output path and native arguments.'
}

$outputPath = [string]$args[0]
if (-not [IO.Path]::IsPathFullyQualified($outputPath)) {
    throw 'WO-044 argument capture output path must be absolute.'
}
$outputPath = [IO.Path]::GetFullPath($outputPath)
$outputParent = Split-Path -Parent $outputPath
if (-not (Test-Path -LiteralPath $outputParent -PathType Container) `
        -or (Test-Path -LiteralPath $outputPath)) {
    throw 'WO-044 argument capture output boundary is invalid.'
}

$captured = @($args | Select-Object -Skip 1 | ForEach-Object { [string]$_ })
$document = [ordered]@{
    schemaVersion = '1.0'
    arguments = $captured
}
$json = $document | ConvertTo-Json -Depth 3 -Compress
[IO.File]::WriteAllText(
    $outputPath,
    $json,
    [Text.UTF8Encoding]::new($false, $true))
