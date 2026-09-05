[CmdletBinding()]
param(
    [string]$TestRoot = (Join-Path ([System.IO.Path]::GetTempPath()) ('lab-skills-test-' + [guid]::NewGuid().ToString('N')))
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$installer = Join-Path $PSScriptRoot 'Install-LocalLabSkills.ps1'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

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
$result = & $installer -Destination $nominal
Assert-True ($result -like '*FILES=10; COPIED=10;*') 'Nominal installation did not copy ten files.'
Assert-True (@(Get-ChildItem -LiteralPath $nominal -Recurse -File).Count -eq 10) 'Unexpected installed file count.'
Write-Output 'PASS nominal installation and post-copy hashes'

$before = Get-FileState $nominal
$result = & $installer -Destination $nominal
Assert-True ($result -like '*COPIED=0;*') 'The second installation should not copy files.'
Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Idempotent installation changed existing bytes or timestamps.'
$alternatePrefix = $nominal.ToUpperInvariant()
if (Test-Path -LiteralPath $alternatePrefix -PathType Container) {
    $result = & $installer -Destination $alternatePrefix -VerifyOnly
    Assert-True ($result -like '*COPIED=0; VERIFY_ONLY=True') 'Windows destination prefix casing caused a false conflict.'
    Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Alternate destination casing changed files.'
    Write-Output 'PASS Windows destination prefix case variation'
}
else {
    Write-Output 'NOT_APPLICABLE destination prefix case variation on a case-sensitive filesystem'
}
Write-Output 'PASS idempotence without rewriting files'

$result = & $installer -Destination $nominal -VerifyOnly
Assert-True ($result -like '*VERIFY_ONLY=True') 'Verification mode did not succeed.'
Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Verification mode changed installed files.'
$missing = Join-Path $TestRoot 'missing'
Assert-Refused { & $installer -Destination $missing -VerifyOnly } '*not installed*'
Assert-True (-not (Test-Path -LiteralPath $missing)) 'Verification of missing installation created a directory.'
Write-Output 'PASS verification-only existing and missing destinations'

# Put a conflict on the last manifest entry: earlier absent skills must not be copied.
$conflict = Join-Path $TestRoot 'conflict'
$conflictFile = Join-Path $conflict 'ss-review-closeout/agents/openai.yaml'
New-Item -ItemType Directory -Path (Split-Path -Parent $conflictFile) -Force | Out-Null
[System.IO.File]::WriteAllText($conflictFile, 'local edited skill', $utf8)
$before = Get-FileState $conflict
Assert-Refused { & $installer -Destination $conflict } '*Local skill differs*'
Assert-True (@(Compare-Object $before (Get-FileState $conflict)).Count -eq 0) 'A conflict caused a partial installation or replacement.'
Write-Output 'PASS late local conflict refused before any copy'

$caseVariant = Join-Path $TestRoot 'case-variant'
New-Item -ItemType Directory -Path (Join-Path $caseVariant 'ss-work-order') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs/skills/local-lab/ss-work-order/SKILL.md') -Destination (Join-Path $caseVariant 'ss-work-order/skill.md')
$before = Get-FileState $caseVariant
Assert-Refused { & $installer -Destination $caseVariant } '*unapproved file*'
Assert-True (@(Compare-Object $before (Get-FileState $caseVariant)).Count -eq 0) 'A case-only local variant was accepted or caused a partial installation.'
Write-Output 'PASS case-only local file variant refused before any copy'

$extra = Join-Path $nominal 'ss-work-order/local-notes.txt'
[System.IO.File]::WriteAllText($extra, 'local additional file', $utf8)
$before = Get-FileState $nominal
Assert-Refused { & $installer -Destination $nominal } '*unapproved file*'
Assert-True (@(Compare-Object $before (Get-FileState $nominal)).Count -eq 0) 'Local additional file was not preserved.'
Write-Output 'PASS additional local file preserved and reported'

# A disposable source tree proves all source hashes are checked before the first copy.
$badRepository = Join-Path $TestRoot 'bad-source'
New-Item -ItemType Directory -Path (Join-Path $badRepository 'scripts'),
    (Join-Path $badRepository 'docs/skills/evaluations/SKL-002') -Force | Out-Null
Copy-Item -LiteralPath $installer -Destination (Join-Path $badRepository 'scripts')
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs/skills/local-lab') -Destination (Join-Path $badRepository 'docs/skills') -Recurse
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs/skills/evaluations/SKL-002/installation-manifest.json') -Destination (Join-Path $badRepository 'docs/skills/evaluations/SKL-002')
$badSource = Join-Path $badRepository 'docs/skills/local-lab/ss-review-closeout/agents/openai.yaml'
[System.IO.File]::AppendAllText($badSource, 'modified', $utf8)
$badDestination = Join-Path $TestRoot 'must-remain-absent'
Assert-Refused { & (Join-Path $badRepository 'scripts/Install-LocalLabSkills.ps1') -Destination $badDestination } '*source hash or size mismatch*'
Assert-True (-not (Test-Path -LiteralPath $badDestination)) 'Bad source validation created a destination.'
Write-Output 'PASS source hash mismatch refused before any copy'
Write-Output 'LOCAL_LAB_SKILLS_INSTALLER_TESTS=PASS; CASES=7; USER_INSTALLATION_TOUCHED=NO'
