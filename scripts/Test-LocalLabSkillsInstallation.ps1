[CmdletBinding()]
param(
    [string]$TestRoot = (Join-Path ([System.IO.Path]::GetTempPath()) ('lab-skills-test-' + [guid]::NewGuid().ToString('N'))),
    [ValidateSet('Lot1', 'ProviderBenchmark')]
    [string]$Package = 'Lot1'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$installer = Join-Path $PSScriptRoot 'Install-LocalLabSkills.ps1'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$firstSkill = 'ss-work-order'
$lastSkill = 'ss-review-closeout'
$fileCount = 10
$manifestRelative = 'docs/skills/evaluations/SKL-002/installation-manifest.json'
if ($Package -eq 'ProviderBenchmark') {
    $firstSkill = $lastSkill = 'ss-provider-benchmark'
    $fileCount = 2
    $manifestRelative = 'docs/skills/evaluations/WO-062/ss-provider-benchmark/installation-manifest.json'
}

if (Test-Path -LiteralPath $TestRoot) {
    throw 'Use a fresh, non-existing test directory.'
}
New-Item -ItemType Directory -Path $TestRoot | Out-Null

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Assert-Refused {
    param([scriptblock]$Action, [string]$Expected)
    $refused = $false
    try { & $Action | Out-Null }
    catch {
        if ($_.Exception.Message -notlike $Expected) { throw }
        $refused = $true
    }
    Assert-True $refused 'The operation should have been refused.'
}
function Get-FileState {
    param([string]$Root)
    return @(Get-ChildItem -LiteralPath $Root -Recurse -File | Sort-Object FullName | ForEach-Object {
        "$($_.FullName)|$($_.LastWriteTimeUtc.Ticks)|$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)"
    })
}

$nominal = Join-Path $TestRoot 'nominal with spaces'
$result = & $installer -Package $Package -Destination $nominal
Assert-True ($result -like "*FILES=$fileCount; COPIED=$fileCount;*") 'Nominal installation did not copy the approved files.'
Assert-True (@(Get-ChildItem -LiteralPath $nominal -Recurse -File).Count -eq $fileCount) 'Unexpected installed file count.'
Write-Output 'PASS nominal installation and post-copy hashes'

$before = Get-FileState $nominal
$result = & $installer -Package $Package -Destination $nominal
Assert-True ($result -like '*COPIED=0;*') 'The second installation should not copy files.'
Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Idempotent installation changed existing bytes or timestamps.'
$alternatePrefix = $nominal.ToUpperInvariant()
if (Test-Path -LiteralPath $alternatePrefix -PathType Container) {
    $result = & $installer -Package $Package -Destination $alternatePrefix -VerifyOnly
    Assert-True ($result -like '*COPIED=0; VERIFY_ONLY=True') 'Windows destination prefix casing caused a false conflict.'
    Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Alternate destination casing changed files.'
    Write-Output 'PASS Windows destination prefix case variation'
}
else {
    Write-Output 'NOT_APPLICABLE destination prefix case variation on a case-sensitive filesystem'
}
Write-Output 'PASS idempotence without rewriting files'

$result = & $installer -Package $Package -Destination $nominal -VerifyOnly
Assert-True ($result -like '*VERIFY_ONLY=True') 'Verification mode did not succeed.'
Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Verification mode changed installed files.'
$missing = Join-Path $TestRoot 'missing'
Assert-Refused { & $installer -Package $Package -Destination $missing -VerifyOnly } '*not installed*'
Assert-True (-not (Test-Path -LiteralPath $missing)) 'Verification of missing installation created a directory.'
Write-Output 'PASS verification-only existing and missing destinations'

# Put a conflict on the last manifest entry: earlier absent skills must not be copied.
$conflict = Join-Path $TestRoot 'conflict'
$conflictFile = Join-Path $conflict "$lastSkill/agents/openai.yaml"
New-Item -ItemType Directory -Path (Split-Path -Parent $conflictFile) -Force | Out-Null
[System.IO.File]::WriteAllText($conflictFile, 'local edited skill', $utf8)
$before = Get-FileState $conflict
Assert-Refused { & $installer -Package $Package -Destination $conflict } '*Local skill differs*'
Assert-True (@(Compare-Object $before (Get-FileState $conflict)).Count -eq 0) 'A conflict caused a partial installation or replacement.'
Write-Output 'PASS late local conflict refused before any copy'

$caseVariant = Join-Path $TestRoot 'case-variant'
New-Item -ItemType Directory -Path (Join-Path $caseVariant $firstSkill) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repositoryRoot "docs/skills/local-lab/$firstSkill/SKILL.md") -Destination (Join-Path $caseVariant "$firstSkill/skill.md")
$before = Get-FileState $caseVariant
Assert-Refused { & $installer -Package $Package -Destination $caseVariant } '*unapproved file*'
Assert-True (@(Compare-Object $before (Get-FileState $caseVariant)).Count -eq 0) 'A case-only local variant was accepted or caused a partial installation.'
Write-Output 'PASS case-only local file variant refused before any copy'

$extra = Join-Path $nominal "$firstSkill/local-notes.txt"
[System.IO.File]::WriteAllText($extra, 'local additional file', $utf8)
$before = Get-FileState $nominal
Assert-Refused { & $installer -Package $Package -Destination $nominal } '*unapproved file*'
Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Local additional file was not preserved.'
Write-Output 'PASS additional local file preserved and reported'

# A disposable source tree proves all source hashes are checked before the first copy.
$badRepository = Join-Path $TestRoot 'bad-source'
New-Item -ItemType Directory -Path (Join-Path $badRepository 'scripts'),
    (Split-Path -Parent (Join-Path $badRepository $manifestRelative)) -Force | Out-Null
Copy-Item -LiteralPath $installer -Destination (Join-Path $badRepository 'scripts')
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs/skills/local-lab') -Destination (Join-Path $badRepository 'docs/skills') -Recurse
Copy-Item -LiteralPath (Join-Path $repositoryRoot $manifestRelative) -Destination (Split-Path -Parent (Join-Path $badRepository $manifestRelative))
$badSource = Join-Path $badRepository "docs/skills/local-lab/$lastSkill/agents/openai.yaml"
[System.IO.File]::AppendAllText($badSource, 'modified', $utf8)
$badDestination = Join-Path $TestRoot 'must-remain-absent'
Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Package $Package -Destination $badDestination } '*source hash or size mismatch*'
Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'Bad source validation created a destination.'
Write-Output 'PASS source hash mismatch refused before any copy'
$caseCount = 7
if ($Package -eq 'ProviderBenchmark') {
    $coexist = Join-Path $TestRoot 'lot1 already installed'
    & $installer -Destination $coexist | Out-Null
    $before = Get-FileState $coexist
    $result = & $installer -Package ProviderBenchmark -Destination $coexist
    Assert-True ($result -like '*FILES=2; COPIED=2;*') 'Adding provider benchmark did not copy exactly two files.'
    $afterLot1 = @(Get-FileState $coexist | Where-Object { $_ -notlike '*ss-provider-benchmark*' })
    Assert-True (@(Compare-Object $before $afterLot1).Count -eq 0) 'Adding provider benchmark changed lot 1.'
    Assert-True (@(Get-ChildItem -LiteralPath $coexist -Recurse -File).Count -eq 12) 'Coexistence should contain twelve files.'
    & $installer -Destination $coexist -VerifyOnly | Out-Null
    & $installer -Package ProviderBenchmark -Destination $coexist -VerifyOnly | Out-Null
    Write-Output 'PASS coexistence with lot 1 without rewriting its files'

    $partial = Join-Path $TestRoot 'partial installation'
    $partialFile = Join-Path $partial 'ss-provider-benchmark/SKILL.md'
    New-Item -ItemType Directory -Path (Split-Path -Parent $partialFile) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs/skills/local-lab/ss-provider-benchmark/SKILL.md') -Destination $partialFile
    $before = Get-FileState $partial
    $result = & $installer -Package ProviderBenchmark -Destination $partial
    Assert-True ($result -like '*FILES=2; COPIED=1;*') 'Resuming should copy only the missing approved file.'
    $afterExisting = @(Get-FileState $partial | Where-Object { $_ -like '*SKILL.md|*' })
    Assert-True (@(Compare-Object $before $afterExisting).Count -eq 0) 'Resuming changed the existing approved file.'
    Write-Output 'PASS resuming a partial installation without rewriting the existing file'

    $badManifestPath = Join-Path $badRepository $manifestRelative
    $badManifest = Get-Content -LiteralPath $badManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $badManifest.owner_validated = $false
    [System.IO.File]::WriteAllText($badManifestPath, ($badManifest | ConvertTo-Json -Depth 10), $utf8)
    Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Package ProviderBenchmark -Destination $badDestination } '*lacks approval*'
    Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'Missing approval created a destination.'
    Write-Output 'PASS unapproved package refused before any copy'

    $badManifest.owner_validated = $true
    $badManifest.files[1].path = 'docs/skills/local-lab/ss-football-quality/agents/openai.yaml'
    [System.IO.File]::WriteAllText($badManifestPath, ($badManifest | ConvertTo-Json -Depth 10), $utf8)
    Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Package ProviderBenchmark -Destination $badDestination } '*exactly the approved skill paths*'
    Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'An out-of-scope manifest created a destination.'
    Write-Output 'PASS manifest path outside the selected package refused'
    $caseCount += 4
}
Write-Output "LOCAL_LAB_SKILLS_INSTALLER_TESTS=PASS; PACKAGE=$Package; CASES=$caseCount; USER_INSTALLATION_TOUCHED=NO"
