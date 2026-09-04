[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the WO-034 orchestrator tests.'
}

$wrapperPath = Join-Path $PSScriptRoot 'Invoke-WO034PermissionRequestWithPreflight.ps1'
. $wrapperPath -InputPath 'not-used-by-dot-source' -OutputDirectory 'not-used-by-dot-source' `
    -RepositoryRoot 'not-used-by-dot-source' -PrivateRoot 'not-used-by-dot-source'

$utf8 = [Text.UTF8Encoding]::new($false, $true)
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('wo034-orchestrator-tests-' + [Guid]::NewGuid().ToString('N'))
$inputPath = Join-Path $temporaryRoot 'owner-input.properties'
$originalBytes = $utf8.GetBytes("SYNTHETIC_LOCK_TEST=owner-controlled-value`n")
$syntheticInputSha256 = Get-WO034OrchestratorSha256 -Bytes $originalBytes

function Assert-WO034OrchestratorTest {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not $Condition) {
        throw "WO-034 orchestrator test assertion failed: $Description."
    }
}

function Assert-WO034OrchestratorInputUnchanged {
    param([Parameter(Mandatory = $true)][string]$Path)

    $persistedBytes = [IO.File]::ReadAllBytes($Path)
    Assert-WO034OrchestratorTest `
        -Condition (Test-WO034OrchestratorBytesEqual -Left $persistedBytes `
            -Right $originalBytes) -Description 'synthetic input bytes unchanged'
}

[IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
try {
    [IO.File]::WriteAllBytes($inputPath, $originalBytes)

    $writeDenied = Invoke-WO034WithOwnerInputReadLock -LockedInputPath $inputPath `
        -Action {
        $attemptedWriteStream = $null
        try {
            $attemptedWriteStream = [IO.File]::Open(
                $inputPath, [IO.FileMode]::Open, [IO.FileAccess]::Write,
                [IO.FileShare]::ReadWrite)
            return $false
        } catch [IO.IOException] {
            return $true
        } finally {
            if ($null -ne $attemptedWriteStream) {
                $attemptedWriteStream.Dispose()
            }
        }
    }
    Assert-WO034OrchestratorTest -Condition $writeDenied `
        -Description 'concurrent write denied by owner-input lock'
    Assert-WO034OrchestratorInputUnchanged -Path $inputPath

    $deleteDenied = Invoke-WO034WithOwnerInputReadLock -LockedInputPath $inputPath `
        -Action {
        try {
            [IO.File]::Delete($inputPath)
            return $false
        } catch [IO.IOException] {
            return $true
        }
    }
    Assert-WO034OrchestratorTest -Condition $deleteDenied `
        -Description 'concurrent delete denied by owner-input lock'
    Assert-WO034OrchestratorTest -Condition ([IO.File]::Exists($inputPath)) `
        -Description 'synthetic input remains after denied delete'
    Assert-WO034OrchestratorInputUnchanged -Path $inputPath

    $replacementPath = Join-Path $temporaryRoot 'replacement.properties'
    [IO.File]::WriteAllBytes($replacementPath,
        $utf8.GetBytes("SYNTHETIC_LOCK_TEST=replacement-value`n"))
    $replacementDenied = Invoke-WO034WithOwnerInputReadLock `
        -LockedInputPath $inputPath -Action {
        try {
            [IO.File]::Move($replacementPath, $inputPath, $true)
            return $false
        } catch [Management.Automation.MethodInvocationException] {
            if ($_.Exception.InnerException -is [IO.IOException] -or
                $_.Exception.InnerException -is [UnauthorizedAccessException]) {
                return $true
            }
            throw
        }
    }
    Assert-WO034OrchestratorTest -Condition $replacementDenied `
        -Description 'concurrent replacement denied by owner-input lock'
    Assert-WO034OrchestratorTest -Condition ([IO.File]::Exists($replacementPath)) `
        -Description 'replacement source remains after denied replacement'
    Assert-WO034OrchestratorInputUnchanged -Path $inputPath

    $sequentialReadEvidence = @(Invoke-WO034WithOwnerInputReadLock `
            -LockedInputPath $inputPath -Action {
            foreach ($phase in @('PREFLIGHT', 'RENDER')) {
                $readStream = [IO.File]::Open(
                    $inputPath, [IO.FileMode]::Open, [IO.FileAccess]::Read,
                    [IO.FileShare]::Read)
                try {
                    $phaseBytes = Read-WO034OrchestratorLockedBytes -Stream $readStream `
                        -Description "WO-034 synthetic $phase read"
                    if (-not (Test-WO034OrchestratorBytesEqual -Left $phaseBytes `
                            -Right $originalBytes)) {
                        throw 'A synthetic phase did not observe the held owner-input bytes.'
                    }
                    Write-Output "WO034_SYNTHETIC_$($phase)_READ=PASS"
                } finally {
                    $readStream.Dispose()
                }
            }
        })
    Assert-WO034OrchestratorTest -Condition ($sequentialReadEvidence.Count -eq 2) `
        -Description 'both synthetic phases reread under one owner-input lock'
    $capturedSyntheticEvidence = (@(
            "WRITE_DENIED=$writeDenied",
            "DELETE_DENIED=$deleteDenied",
            "REPLACEMENT_DENIED=$replacementDenied") +
        $sequentialReadEvidence) -join "`n"
    Assert-WO034OrchestratorTest `
        -Condition (-not $capturedSyntheticEvidence.Contains(
            $syntheticInputSha256, [StringComparison]::Ordinal)) `
        -Description 'synthetic owner-input SHA-256 absent from captured evidence'
    Assert-WO034OrchestratorInputUnchanged -Path $inputPath

    $validFixturePath = Join-Path $temporaryRoot 'valid-preflight.properties'
    $versionedFixturePath = Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) `
        'docs\validation\J9-WO034-PERMISSION-REQUEST-PREFLIGHT-SYNTHETIC-FIXTURE-20260902.properties'
    $validFixtureBytes = [IO.File]::ReadAllBytes($versionedFixturePath)
    $validFixtureSha256 = Get-WO034OrchestratorSha256 -Bytes $validFixtureBytes
    [IO.File]::WriteAllBytes($validFixturePath, $validFixtureBytes)
    $realPreflightOutput = Invoke-WO034WithOwnerInputReadLock `
        -LockedInputPath $validFixturePath -Action {
        $preflightResult = Invoke-WO034OrchestratorChildProcess `
            -ScriptPath (Join-Path $PSScriptRoot 'Test-WO034PermissionRequestInput.ps1') `
            -Arguments @('-InputPath', $validFixturePath) -TimeoutSeconds 60 `
            -Description 'WO-034 synthetic locked preflight'
        if ($preflightResult.ExitCode -ne 0 -or
            -not [string]::IsNullOrEmpty($preflightResult.StandardError)) {
            throw 'The real synthetic preflight child failed under the owner-input read lock.'
        }
        return $preflightResult.StandardOutput
    }
    Assert-WO034OrchestratorTest -Condition ($realPreflightOutput.Contains(
            'WO034_INPUT_PREFLIGHT_STATUS=PASS', [StringComparison]::Ordinal)) `
        -Description 'real preflight child reads valid fixture under owner-input lock'
    Assert-WO034OrchestratorTest -Condition (-not $realPreflightOutput.Contains(
            $validFixtureSha256, [StringComparison]::Ordinal)) `
        -Description 'valid fixture SHA-256 absent from real preflight output'
    $persistedValidFixtureBytes = [IO.File]::ReadAllBytes($validFixturePath)
    Assert-WO034OrchestratorTest -Condition (
        Test-WO034OrchestratorBytesEqual -Left $persistedValidFixtureBytes `
            -Right $validFixtureBytes) -Description 'valid fixture unchanged after real preflight'

    Write-Output 'WO034_ORCHESTRATOR_TEST_STATUS=PASS'
    Write-Output 'WO034_ORCHESTRATOR_LOCK_SCENARIO_COUNT=5'
    Write-Output 'WO034_ORCHESTRATOR_CONCURRENT_WRITE_DENIED=YES'
    Write-Output 'WO034_ORCHESTRATOR_CONCURRENT_DELETE_DENIED=YES'
    Write-Output 'WO034_ORCHESTRATOR_CONCURRENT_REPLACEMENT_DENIED=YES'
    Write-Output 'WO034_ORCHESTRATOR_SEQUENTIAL_PREFLIGHT_RENDER_READ_ALLOWED=YES'
    Write-Output 'WO034_ORCHESTRATOR_REAL_PREFLIGHT_CHILD_READ_ALLOWED=YES'
    Write-Output 'WO034_ORCHESTRATOR_OWNER_INPUT_HASH_EMITTED=NO'
    Write-Output 'WO034_ORCHESTRATOR_RENDERER_EXECUTION_COUNT=0'
} finally {
    $canonicalTemporaryRoot = [IO.Path]::GetFullPath($temporaryRoot)
    $canonicalSystemTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).
        TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $canonicalTemporaryRoot.StartsWith(
            $canonicalSystemTemporaryRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($canonicalTemporaryRoot) -cnotmatch
            '^wo034-orchestrator-tests-[0-9a-f]{32}$') {
        throw 'The WO-034 orchestrator test temporary root failed confinement validation.'
    }
    if (Test-Path -LiteralPath $canonicalTemporaryRoot) {
        Remove-Item -LiteralPath $canonicalTemporaryRoot -Recurse -Force
    }
}
