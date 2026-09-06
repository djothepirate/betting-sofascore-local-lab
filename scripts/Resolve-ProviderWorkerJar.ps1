[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepositoryRoot,
    [Parameter(Mandatory)][string]$FinalName
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

# FinalName comes from the same Maven profile as the package invocation. Reject
# diagnostic output and paths; never discover a worker by globbing old artifacts.
if ($FinalName -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$' -or $FinalName.EndsWith('.')) {
    throw 'The Maven finalName is not a single safe artifact filename'
}
$candidate = Join-Path $RepositoryRoot "target\${FinalName}-provider-playwright-worker.jar"
if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
    throw 'The exact classified Playwright worker jar is absent after packaging'
}
(Resolve-Path -LiteralPath $candidate).Path
