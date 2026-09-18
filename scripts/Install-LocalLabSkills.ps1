[CmdletBinding()]
param(
    [string]$Destination = (Join-Path $env:USERPROFILE '.agents/skills'),
    [switch]$VerifyOnly,
    [ValidateSet('Lot1', 'ProviderBenchmark', 'FootballQualityCiSecurity', 'WindowsRuntime', 'JavaModule')]
    [string]$Package = 'Lot1'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

# Each package has an exact approved allowlist. Never replace a local variant.
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $repositoryRoot 'docs/skills/evaluations/SKL-002/installation-manifest.json'
$skillNames = @('ss-work-order', 'ss-verify', 'ss-postgres-change',
    'ss-data-contract-replay', 'ss-review-closeout')
if ($Package -eq 'ProviderBenchmark') {
    $manifestPath = Join-Path $repositoryRoot 'docs/skills/evaluations/WO-062/ss-provider-benchmark/installation-manifest.json'
    $skillNames = @('ss-provider-benchmark')
}
elseif ($Package -eq 'FootballQualityCiSecurity') {
    $manifestPath = Join-Path $repositoryRoot 'docs/skills/evaluations/WO-062/football-quality-ci-security/installation-manifest.json'
    $skillNames = @('ss-football-quality', 'ss-ci-security')
}
elseif ($Package -eq 'WindowsRuntime') {
    $manifestPath = Join-Path $repositoryRoot 'docs/skills/evaluations/WO-062/ss-windows-runtime/installation-manifest.json'
    $skillNames = @('ss-windows-runtime')
}
elseif ($Package -eq 'JavaModule') {
    $manifestPath = Join-Path $repositoryRoot 'docs/skills/evaluations/WO-062/ss-java-module/installation-manifest.json'
    $skillNames = @('ss-java-module')
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedCandidateVersion = if ($Package -eq 'JavaModule') { '0.1.0-candidate.5' } else { '0.1.0-candidate.1' }
if ($Package -ne 'Lot1' -and
    ($manifest.owner_validated -isnot [bool] -or -not $manifest.owner_validated -or
     $manifest.personal_installation_authorized -isnot [bool] -or -not $manifest.personal_installation_authorized -or
     $manifest.candidate_version -cne $expectedCandidateVersion)) {
    throw 'Selected package lacks approval for the exact candidate version.'
}
$expectedPaths = @($skillNames | ForEach-Object {
    "docs/skills/local-lab/$_/SKILL.md"
    "docs/skills/local-lab/$_/agents/openai.yaml"
})
if ($manifest.schema_version -ne 1 -or @($manifest.files).Count -ne $expectedPaths.Count) {
    throw 'Invalid approved skill manifest.'
}
$actualPaths = @($manifest.files | ForEach-Object { $_.path })
if (@(Compare-Object $expectedPaths $actualPaths -CaseSensitive).Count -ne 0 -or
    @($actualPaths | Select-Object -Unique).Count -ne $expectedPaths.Count) {
    throw 'The manifest must contain exactly the approved skill paths for the selected package.'
}

function Assert-NoLinkedAncestor {
    param([string]$Path)
    $current = [System.IO.Path]::GetFullPath($Path)
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Linked source or installation paths are not supported.'
            }
        }
        $current = Split-Path -Parent $current
    }
}

function Test-ApprovedFile {
    param([string]$Path, $Entry)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    $item = Get-Item -LiteralPath $Path -Force
    return ($item.Length -eq $Entry.bytes -and
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $Entry.sha256)
}

$destinationRoot = [System.IO.Path]::GetFullPath($Destination)
Assert-NoLinkedAncestor -Path $destinationRoot
if ((Test-Path -LiteralPath $destinationRoot) -and
    -not (Test-Path -LiteralPath $destinationRoot -PathType Container)) {
    throw 'The installation destination is not a directory.'
}
$entries = @($manifest.files | ForEach-Object {
    $source = Join-Path $repositoryRoot $_.path
    Assert-NoLinkedAncestor -Path $source
    if (-not (Test-ApprovedFile -Path $source -Entry $_)) {
        throw "Approved source hash or size mismatch: $($_.path)"
    }
    [pscustomobject]@{
        Source = $source
        Target = [System.IO.Path]::GetFullPath((Join-Path $destinationRoot $_.path.Substring('docs/skills/local-lab/'.Length)))
        Evidence = $_
    }
})

# Finish all source and destination checks before creating any file or directory.
foreach ($name in $skillNames) {
    $skillDirectory = Join-Path $destinationRoot $name
    Assert-NoLinkedAncestor -Path $skillDirectory
    if (Test-Path -LiteralPath $skillDirectory) {
        if (-not (Test-Path -LiteralPath $skillDirectory -PathType Container)) {
            throw "Local skill path is not a directory: $name"
        }
        $allowedRelativePaths = @($entries | Where-Object {
            $_.Evidence.path.StartsWith("docs/skills/local-lab/$name/", [StringComparison]::Ordinal)
        } | ForEach-Object { $_.Evidence.path.Substring("docs/skills/local-lab/$name/".Length) })
        $skillRootPath = (Get-Item -LiteralPath $skillDirectory -Force).FullName
        $children = @(Get-ChildItem -LiteralPath $skillRootPath -Force)
        foreach ($child in $children) {
            Assert-NoLinkedAncestor -Path $child.FullName
        }
        foreach ($child in @(Get-ChildItem -LiteralPath $skillRootPath -Recurse -Force)) {
            Assert-NoLinkedAncestor -Path $child.FullName
            # The file spelling is exact; the absolute Windows prefix may differ in case.
            $relativePath = $child.FullName.Substring($skillRootPath.Length + 1).Replace('\', '/')
            if (-not $child.PSIsContainer -and $relativePath -cnotin $allowedRelativePaths) {
                throw "Local skill contains an unapproved file: $name"
            }
        }
    }
}
foreach ($entry in $entries) {
    Assert-NoLinkedAncestor -Path $entry.Target
    if (Test-Path -LiteralPath $entry.Target) {
        if (-not (Test-ApprovedFile -Path $entry.Target -Entry $entry.Evidence)) {
            throw "Local skill differs; preserve and reconcile it before installing: $($entry.Evidence.path)"
        }
    }
    elseif ($VerifyOnly) {
        throw "Approved skill file is not installed: $($entry.Evidence.path)"
    }
}

$copied = 0
if (-not $VerifyOnly) {
    foreach ($entry in $entries) {
        if (-not (Test-Path -LiteralPath $entry.Target)) {
            New-Item -ItemType Directory -Path (Split-Path -Parent $entry.Target) -Force | Out-Null
            # overwrite=false also prevents replacement if a file appears after preflight.
            [System.IO.File]::Copy($entry.Source, $entry.Target, $false)
            $copied++
        }
    }
}
foreach ($entry in $entries) {
    if (-not (Test-ApprovedFile -Path $entry.Target -Entry $entry.Evidence)) {
        throw 'Post-installation hash verification failed.'
    }
}
Write-Output "LOCAL_LAB_SKILLS=PASS; FILES=$($entries.Count); COPIED=$copied; VERIFY_ONLY=$([bool]$VerifyOnly)"
