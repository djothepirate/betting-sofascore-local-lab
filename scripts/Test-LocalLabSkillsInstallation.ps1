[CmdletBinding()]
param(
    [string]$TestRoot = (Join-Path ([System.IO.Path]::GetTempPath()) ('lab-skills-test-' + [guid]::NewGuid().ToString('N'))),
    [ValidateSet('Lot1', 'ProviderBenchmark', 'FootballQualityCiSecurity', 'WindowsRuntime', 'JavaModule')]
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
elseif ($Package -eq 'FootballQualityCiSecurity') {
    $firstSkill = 'ss-football-quality'
    $lastSkill = 'ss-ci-security'
    $fileCount = 4
    $manifestRelative = 'docs/skills/evaluations/WO-062/football-quality-ci-security/installation-manifest.json'
}
elseif ($Package -eq 'WindowsRuntime') {
    $firstSkill = $lastSkill = 'ss-windows-runtime'
    $fileCount = 2
    $manifestRelative = 'docs/skills/evaluations/WO-062/ss-windows-runtime/installation-manifest.json'
}
elseif ($Package -eq 'JavaModule') {
    $firstSkill = $lastSkill = 'ss-java-module'
    $fileCount = 2
    $manifestRelative = 'docs/skills/evaluations/WO-062/ss-java-module/installation-manifest.json'
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
# Subsequent manifest checks must exercise manifest guards, not reuse the intentionally corrupted source.
Copy-Item -LiteralPath (Join-Path $repositoryRoot "docs/skills/local-lab/$lastSkill/agents/openai.yaml") -Destination $badSource -Force
$caseCount = 7
if ($Package -ne 'Lot1') {
    $coexist = Join-Path $TestRoot 'lot1 already installed'
    & $installer -Destination $coexist | Out-Null
    $priorCount = 10
    if ($Package -in @('FootballQualityCiSecurity', 'WindowsRuntime', 'JavaModule')) {
        & $installer -Package ProviderBenchmark -Destination $coexist | Out-Null
        $priorCount += 2
    }
    if ($Package -eq 'WindowsRuntime') {
        & $installer -Package FootballQualityCiSecurity -Destination $coexist | Out-Null
        $priorCount += 4
    }
    $before = Get-FileState $coexist
    $result = & $installer -Package $Package -Destination $coexist
    Assert-True ($result -like "*FILES=$fileCount; COPIED=$fileCount;*") 'Adding the package did not copy exactly its approved files.'
    $afterPrior = @(Get-FileState $coexist | Where-Object {
        -not $_.StartsWith(((Join-Path $coexist $firstSkill) + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase) -and
        -not $_.StartsWith(((Join-Path $coexist $lastSkill) + [IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)
    })
    Assert-True (@(Compare-Object $before $afterPrior).Count -eq 0) 'Adding the package changed previously installed files.'
    Assert-True (@(Get-ChildItem -LiteralPath $coexist -Recurse -File).Count -eq ($priorCount + $fileCount)) 'Unexpected coexistence file count.'
    & $installer -Destination $coexist -VerifyOnly | Out-Null
    if ($Package -in @('FootballQualityCiSecurity', 'WindowsRuntime', 'JavaModule')) {
        & $installer -Package ProviderBenchmark -Destination $coexist -VerifyOnly | Out-Null
    }
    if ($Package -eq 'WindowsRuntime') {
        & $installer -Package FootballQualityCiSecurity -Destination $coexist -VerifyOnly | Out-Null
    }
    & $installer -Package $Package -Destination $coexist -VerifyOnly | Out-Null
    Write-Output 'PASS coexistence without rewriting previously installed files'

    $partial = Join-Path $TestRoot 'partial installation'
    $partialFile = Join-Path $partial "$firstSkill/SKILL.md"
    New-Item -ItemType Directory -Path (Split-Path -Parent $partialFile) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot "docs/skills/local-lab/$firstSkill/SKILL.md") -Destination $partialFile
    $before = Get-FileState $partial
    $result = & $installer -Package $Package -Destination $partial
    Assert-True ($result -like "*FILES=$fileCount; COPIED=$($fileCount - 1);*") 'Resuming should copy only missing approved files.'
    $afterExisting = @(Get-FileState $partial | Where-Object { $_ -like "*$firstSkill*SKILL.md|*" })
    Assert-True (@(Compare-Object $before $afterExisting).Count -eq 0) 'Resuming changed the existing approved file.'
    Write-Output 'PASS resuming a partial installation without rewriting the existing file'

    $badManifestPath = Join-Path $badRepository $manifestRelative
    $badManifest = Get-Content -LiteralPath $badManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $badManifest.owner_validated = $false
    [System.IO.File]::WriteAllText($badManifestPath, ($badManifest | ConvertTo-Json -Depth 10), $utf8)
    Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Package $Package -Destination $badDestination } '*lacks approval*'
    Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'Missing approval created a destination.'
    Write-Output 'PASS unapproved package refused before any copy'

    $badManifest.owner_validated = $true
    $foreignSkill = if ($lastSkill -eq 'ss-java-module') { 'ss-windows-runtime' } else { 'ss-java-module' }
    $badManifest.files[$fileCount - 1].path = "docs/skills/local-lab/$foreignSkill/agents/openai.yaml"
    [System.IO.File]::WriteAllText($badManifestPath, ($badManifest | ConvertTo-Json -Depth 10), $utf8)
    Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Package $Package -Destination $badDestination } '*exactly the approved skill paths*'
    Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'An out-of-scope manifest created a destination.'
    Write-Output 'PASS manifest path outside the selected package refused'
    $caseCount += 4
    if ($Package -in @('FootballQualityCiSecurity', 'WindowsRuntime', 'JavaModule')) {
        foreach ($variant in @('personal-approval-missing', 'approval-not-boolean', 'different-version')) {
            $badManifest = Get-Content -LiteralPath (Join-Path $repositoryRoot $manifestRelative) -Raw -Encoding UTF8 | ConvertFrom-Json
            switch ($variant) {
                'personal-approval-missing' { $badManifest.personal_installation_authorized = $false }
                'approval-not-boolean' { $badManifest.owner_validated = 'true' }
                'different-version' { $badManifest.candidate_version = '0.1.0-candidate.2' }
            }
            [System.IO.File]::WriteAllText($badManifestPath, ($badManifest | ConvertTo-Json -Depth 10), $utf8)
            Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Package $Package -Destination $badDestination } '*lacks approval*'
            Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'An invalid approval created a destination.'
            Write-Output "PASS $variant refused before any copy"
            $caseCount++
        }
    }
}
Write-Output "LOCAL_LAB_SKILLS_INSTALLER_TESTS=PASS; PACKAGE=$Package; CASES=$caseCount; USER_INSTALLATION_TOUCHED=NO"
