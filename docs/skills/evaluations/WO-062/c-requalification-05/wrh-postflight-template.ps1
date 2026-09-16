[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
$utf8 = New-Object Text.UTF8Encoding($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$artifactRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output\runtime'))
$executionPath = Join-Path $artifactRoot 'execution.json'
$postflightPath = Join-Path $artifactRoot 'postflight.json'

if (-not [IO.File]::Exists($executionPath)) { throw 'Missing execution evidence' }
$execution = Get-Content -LiteralPath $executionPath -Raw | ConvertFrom-Json
$entries = if ([IO.Directory]::Exists($runtimeRoot)) { @([IO.Directory]::GetFileSystemEntries($runtimeRoot)) } else { @() }
$postflight = [ordered]@{
    case = 'WR-H01'
    observed_at_utc = [DateTime]::UtcNow.ToString('o')
    execution_result = $execution.result
    native_exit_code = $execution.native_exit_code
    cleanup = $execution.cleanup
    runtime_root = $runtimeRoot
    runtime_entries_after_prompt_return = $entries
    runtime_empty = ($entries.Count -eq 0)
    scope = 'Separate postflight after the real driver return; it observes only this case output and runtime root.'
}
[IO.File]::WriteAllText($postflightPath, ($postflight | ConvertTo-Json -Depth 8), $utf8)
Write-Output 'WR_PHASE=POSTFLIGHT'
Write-Output ('WR_POSTFLIGHT_RESULT=' + $postflight.execution_result + '; WR_RUNTIME_EMPTY=' + $postflight.runtime_empty)
