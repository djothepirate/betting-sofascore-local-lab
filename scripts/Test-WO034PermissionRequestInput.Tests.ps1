[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the WO-034 preflight tests.'
}

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$preflightPath = Join-Path $PSScriptRoot 'Test-WO034PermissionRequestInput.ps1'
$fixturePath = Join-Path $repositoryRoot `
    'docs\validation\J9-WO034-PERMISSION-REQUEST-PREFLIGHT-SYNTHETIC-FIXTURE-20260902.properties'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('wo034-preflight-tests-' + [Guid]::NewGuid().ToString('N'))
$knownOwnerInputKeys = [Collections.Generic.HashSet[string]]::new(
    [StringComparer]::Ordinal)
foreach ($schemaLine in ($utf8.GetString([IO.File]::ReadAllBytes($fixturePath)) -split "`n")) {
    $schemaSeparatorIndex = $schemaLine.IndexOf('=')
    if ($schemaSeparatorIndex -gt 0 -and -not $schemaLine.StartsWith('#')) {
        [void]$knownOwnerInputKeys.Add($schemaLine.Substring(0, $schemaSeparatorIndex))
    }
}

function Assert-WO034TestEqual {
    param(
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Actual -cne $Expected) {
        throw "WO-034 preflight test assertion failed: $Description."
    }
}

function Invoke-WO034PreflightTestProcess {
    param([Parameter(Mandatory = $true)][string]$Path)

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = [Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = (Get-Command pwsh -ErrorAction Stop).Source
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-File',
            $preflightPath, '-InputPath', $Path)) {
        [void]$process.StartInfo.ArgumentList.Add($argument)
    }
    try {
        if (-not $process.Start()) {
            throw 'The WO-034 preflight test child process did not start.'
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(30000)) {
            try {
                $process.Kill($true)
            } catch {
                # A concurrent natural exit is accepted only after the bounded confirmation below.
            }
            if (-not $process.WaitForExit(10000)) {
                throw 'The WO-034 preflight test child process did not terminate.'
            }
            [void]$stdoutTask.GetAwaiter().GetResult()
            [void]$stderrTask.GetAwaiter().GetResult()
            throw 'The WO-034 preflight test child process exceeded its timeout.'
        }
        return [ordered]@{
            ExitCode = $process.ExitCode
            StandardOutput = $stdoutTask.GetAwaiter().GetResult()
            StandardError = $stderrTask.GetAwaiter().GetResult()
            Values = @{}
        }
    } finally {
        $process.Dispose()
    }
}

function Invoke-WO034PreflightTestCase {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Content
    )

    $path = Join-Path $temporaryRoot ($Name + '.properties')
    [IO.File]::WriteAllBytes($path, $utf8.GetBytes($Content))
    $result = Invoke-WO034PreflightTestProcess -Path $path
    foreach ($line in $result.StandardOutput -split "`r?`n") {
        $separatorIndex = $line.IndexOf('=')
        if ($separatorIndex -gt 0) {
            $result.Values[$line.Substring(0, $separatorIndex)] =
                $line.Substring($separatorIndex + 1)
        }
    }
    Assert-WO034NoOwnerControlledValueLeak -Result $result -Content $Content `
        -CaseName $Name
    return $result
}

function Assert-WO034NoOwnerControlledValueLeak {
    param(
        [Parameter(Mandatory = $true)]$Result,
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][string]$CaseName
    )

    $combined = $Result.StandardOutput + $Result.StandardError
    $combinedNfc = $combined.Normalize([Text.NormalizationForm]::FormC)
    $candidateValues = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    foreach ($rawLine in $Content -split "`n") {
        $line = $rawLine.TrimEnd("`r")
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line.StartsWith('#')) {
            $commentValue = $line.Substring(1).TrimStart()
            if (-not [string]::IsNullOrEmpty($commentValue)) {
                [void]$candidateValues.Add($commentValue)
            }
            continue
        }
        $separatorIndex = $line.IndexOf('=')
        $candidate = if ($separatorIndex -ge 0) {
            $key = $line.Substring(0, $separatorIndex)
            if (-not $knownOwnerInputKeys.Contains($key)) {
                [void]$candidateValues.Add($key)
            }
            $line.Substring($separatorIndex + 1)
        } else {
            $line
        }
        if (-not [string]::IsNullOrEmpty($candidate) -and
            $candidate -cnotin @('YES', 'NO')) {
            [void]$candidateValues.Add($candidate)
        }
    }
    foreach ($ownerControlledValue in $candidateValues) {
        $ownerControlledValueNfc = $ownerControlledValue.Normalize(
            [Text.NormalizationForm]::FormC)
        if ($combined.Contains($ownerControlledValue, [StringComparison]::Ordinal) -or
            $combinedNfc.Contains($ownerControlledValueNfc, [StringComparison]::Ordinal)) {
            throw "The WO-034 preflight emitted an owner-controlled value in case $CaseName."
        }
    }
}

[IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
try {
    $fixture = $utf8.GetString([IO.File]::ReadAllBytes($fixturePath))

    $valid = Invoke-WO034PreflightTestCase -Name 'valid' -Content $fixture
    Assert-WO034TestEqual $valid.ExitCode 0 'valid fixture exit code'
    Assert-WO034TestEqual $valid.Values['WO034_INPUT_PREFLIGHT_STATUS'] 'PASS' `
        'valid fixture status'
    Assert-WO034TestEqual $valid.Values['FROZEN_LAUNCHER_SHA256_MATCH'] 'YES' `
        'frozen launcher hash'
    Assert-WO034TestEqual $valid.Values['FROZEN_RENDERER_SHA256_MATCH'] 'YES' `
        'frozen renderer hash'
    Assert-WO034TestEqual $valid.Values['FROZEN_TEMPLATE_SHA256_MATCH'] 'YES' `
        'frozen template hash'
    Assert-WO034TestEqual $valid.Values['INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE'] 'YES' `
        'strict input encoding and line contract'
    Assert-WO034TestEqual $valid.Values['EXPECTED_KEY_COUNT'] '41' 'exact schema count'
    Assert-WO034TestEqual $valid.Values['UNCONDITIONAL_COMPLETE_COUNT'] '30/30' `
        'unconditional completeness'
    Assert-WO034TestEqual $valid.Values['EMPTY_UNCONDITIONAL_KEY_COUNT'] '0' `
        'empty unconditional count'
    Assert-WO034TestEqual $valid.Values['FORMAT_ERROR_COUNT'] '0' 'format error count'
    Assert-WO034TestEqual $valid.Values['ENUM_ERROR_COUNT'] '0' 'enum error count'
    Assert-WO034TestEqual $valid.Values['CONDITIONAL_ERROR_COUNT'] '0' `
        'conditional error count'
    Assert-WO034TestEqual `
        $valid.Values['SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS'] 'YES' `
        'semantic coverage'
    Assert-WO034TestEqual $valid.Values['RENDERER_EXECUTED'] 'NO' 'renderer isolation'

    $comment = Invoke-WO034PreflightTestCase -Name 'comment' -Content `
        $fixture.Replace('SYNTHETIC PII-FREE PREFLIGHT INPUT. NOT AN OUTBOUND REQUEST.',
            'SYNTHETIC OWNER COMMENT SENTINEL FOR LEAK QUALIFICATION.')
    Assert-WO034TestEqual $comment.ExitCode 0 'comment fixture exit code'
    Assert-WO034TestEqual $comment.Values['WO034_INPUT_PREFLIGHT_STATUS'] 'PASS' `
        'comment fixture status'

    $nfdOwnerRole = 'Synthetic project r' + 'e' + [char]0x0301 + 'viewer'
    $nfdOwnerValue = Invoke-WO034PreflightTestCase -Name 'nfd-owner-value' -Content `
        $fixture.Replace('REQUESTOR_ROLE=Synthetic project reviewer',
            "REQUESTOR_ROLE=$nfdOwnerRole")
    Assert-WO034TestEqual $nfdOwnerValue.ExitCode 0 'NFD owner value fixture exit code'
    Assert-WO034TestEqual $nfdOwnerValue.Values['WO034_INPUT_PREFLIGHT_STATUS'] 'PASS' `
        'NFD owner value fixture status'

    $empty = Invoke-WO034PreflightTestCase -Name 'empty' -Content `
        $fixture.Replace('REQUESTOR_ROLE=Synthetic project reviewer', 'REQUESTOR_ROLE=')
    Assert-WO034TestEqual $empty.ExitCode 1 'empty fixture exit code'
    Assert-WO034TestEqual $empty.Values['EMPTY_UNCONDITIONAL_KEY_COUNT'] '1' `
        'empty fixture count'
    Assert-WO034TestEqual $empty.Values['EMPTY_UNCONDITIONAL_KEYS'] 'REQUESTOR_ROLE' `
        'empty fixture diagnostic key'

    $emptyEnum = Invoke-WO034PreflightTestCase -Name 'empty-enum' -Content `
        $fixture.Replace('END_USER_ACCESS=OTHER_WITH_DETAILS', 'END_USER_ACCESS=')
    Assert-WO034TestEqual $emptyEnum.ExitCode 1 'empty enum fixture exit code'
    Assert-WO034TestEqual $emptyEnum.Values['EMPTY_UNCONDITIONAL_KEY_COUNT'] '1' `
        'empty enum fixture count'
    Assert-WO034TestEqual $emptyEnum.Values['ENUM_ERROR_COUNT'] '0' `
        'empty enum is not an invalid non-empty enum'
    Assert-WO034TestEqual $emptyEnum.Values['INVALID_ENUM_KEYS'] '' `
        'empty enum diagnostic separation'

    $emptyFixedContentMode = Invoke-WO034PreflightTestCase -Name 'empty-fixed-content-mode' `
        -Content $fixture.Replace('OUTBOUND_CONTENT_MODE=PRIMARY_ONLY',
            'OUTBOUND_CONTENT_MODE=')
    Assert-WO034TestEqual $emptyFixedContentMode.ExitCode 1 `
        'empty fixed content mode exit code'
    Assert-WO034TestEqual $emptyFixedContentMode.Values['UNCONDITIONAL_COMPLETE_COUNT'] `
        '30/30' 'fixed content mode does not alter owner-field completeness'
    Assert-WO034TestEqual $emptyFixedContentMode.Values['ENUM_ERROR_COUNT'] '1' `
        'empty fixed content mode enum error'
    Assert-WO034TestEqual $emptyFixedContentMode.Values['INVALID_ENUM_KEYS'] `
        'OUTBOUND_CONTENT_MODE' 'empty fixed content mode diagnostic key'

    $missing = Invoke-WO034PreflightTestCase -Name 'missing' -Content `
        $fixture.Replace("REQUESTOR_ROLE=Synthetic project reviewer`n", '')
    Assert-WO034TestEqual $missing.ExitCode 1 'missing fixture exit code'
    Assert-WO034TestEqual $missing.Values['MISSING_KEY_COUNT'] '1' 'missing fixture count'
    Assert-WO034TestEqual $missing.Values['UNCONDITIONAL_COMPLETE_COUNT'] '29/30' `
        'missing fixture completeness'

    $unknown = Invoke-WO034PreflightTestCase -Name 'unknown' -Content `
        ($fixture + "UNKNOWN_SYNTHETIC_KEY=unknown synthetic owner value`n")
    Assert-WO034TestEqual $unknown.ExitCode 1 'unknown fixture exit code'
    Assert-WO034TestEqual $unknown.Values['UNKNOWN_KEY_COUNT'] '1' 'unknown fixture count'

    $duplicate = Invoke-WO034PreflightTestCase -Name 'duplicate' -Content `
        ($fixture + "REQUESTOR_ROLE=another synthetic role`n")
    Assert-WO034TestEqual $duplicate.ExitCode 1 'duplicate fixture exit code'
    Assert-WO034TestEqual $duplicate.Values['DUPLICATE_KEY_COUNT'] '1' `
        'duplicate fixture count'
    Assert-WO034TestEqual $duplicate.Values['DUPLICATE_EXPECTED_KEYS'] 'REQUESTOR_ROLE' `
        'duplicate fixture diagnostic key'

    $malformed = Invoke-WO034PreflightTestCase -Name 'malformed' -Content `
        ($fixture + "MALFORMED_SYNTHETIC_LINE`n")
    Assert-WO034TestEqual $malformed.ExitCode 1 'malformed fixture exit code'
    Assert-WO034TestEqual $malformed.Values['MALFORMED_LINE_COUNT'] '1' `
        'malformed fixture count'

    $invalidEnum = Invoke-WO034PreflightTestCase -Name 'enum' -Content `
        $fixture.Replace('END_USER_ACCESS=OTHER_WITH_DETAILS',
            'END_USER_ACCESS=INVALID_SYNTHETIC_ENUM_VALUE')
    Assert-WO034TestEqual $invalidEnum.ExitCode 1 'enum fixture exit code'
    Assert-WO034TestEqual $invalidEnum.Values['ENUM_ERROR_COUNT'] '1' 'enum fixture count'
    Assert-WO034TestEqual $invalidEnum.Values['INVALID_ENUM_KEYS'] 'END_USER_ACCESS' `
        'enum fixture diagnostic key'

    $invalidCondition = Invoke-WO034PreflightTestCase -Name 'condition' -Content `
        $fixture.Replace('MODEL_TRAINING=NO', 'MODEL_TRAINING=YES_WITH_DETAILS')
    Assert-WO034TestEqual $invalidCondition.ExitCode 1 'conditional fixture exit code'
    Assert-WO034TestEqual $invalidCondition.Values['CONDITIONAL_ERROR_COUNT'] '1' `
        'conditional fixture count'
    Assert-WO034TestEqual $invalidCondition.Values['INVALID_CONDITIONAL_KEYS'] `
        'MODEL_TRAINING_DETAILS' 'conditional fixture diagnostic key'

    $invalidEmail = Invoke-WO034PreflightTestCase -Name 'email' -Content `
        $fixture.Replace('REPLY_EMAIL=synthetic-preflight@example.invalid',
            'REPLY_EMAIL=not a mailbox')
    Assert-WO034TestEqual $invalidEmail.ExitCode 1 'email fixture exit code'
    Assert-WO034TestEqual $invalidEmail.Values['FORMAT_ERROR_COUNT'] '1' `
        'email fixture format count'
    Assert-WO034TestEqual $invalidEmail.Values['INVALID_FORMAT_KEYS'] 'REPLY_EMAIL' `
        'email fixture diagnostic key'

    $invalidRepository = Invoke-WO034PreflightTestCase -Name 'repository' -Content `
        $fixture.Replace('https://example.invalid/synthetic-project',
            'https://example.invalid/synthetic-project?unexpected=true')
    Assert-WO034TestEqual $invalidRepository.ExitCode 1 'repository fixture exit code'
    Assert-WO034TestEqual $invalidRepository.Values['FORMAT_ERROR_COUNT'] '1' `
        'repository fixture format count'

    $semanticFailure = Invoke-WO034PreflightTestCase -Name 'semantic' -Content `
        $fixture.Replace(
            'Betting Project will receive an immutable, minimized, normalized and human-reviewed J7 export as a durable input for future versioned enrichment and betting-analysis processing; it will not call SofaScore or trigger an acquisition.',
            'Synthetic football-event analysis for renderer qualification only.')
    Assert-WO034TestEqual $semanticFailure.ExitCode 1 'semantic fixture exit code'
    Assert-WO034TestEqual `
        $semanticFailure.Values['SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS'] 'NO' `
        'semantic fixture coverage'
    Assert-WO034TestEqual $semanticFailure.Values['INVALID_SEMANTIC_KEYS'] `
        'BETTING_RELATED_USE_DESCRIPTION' 'semantic fixture diagnostic key'

    $semanticPartial = Invoke-WO034PreflightTestCase -Name 'semantic-partial' -Content `
        $fixture.Replace(
            'Betting Project will receive an immutable, minimized, normalized and human-reviewed J7 export as a durable input for future versioned enrichment and betting-analysis processing; it will not call SofaScore or trigger an acquisition.',
            'Betting Project will retain a durable J7 input for future versioned analysis; it will not call SofaScore or trigger an acquisition.')
    Assert-WO034TestEqual $semanticPartial.ExitCode 1 `
        'partial semantic fixture exit code'
    Assert-WO034TestEqual `
        $semanticPartial.Values['SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS'] `
        'NO' 'partial semantic fixture coverage'

    $semanticContradictory = Invoke-WO034PreflightTestCase `
        -Name 'semantic-contradictory' -Content $fixture.Replace(
            'Betting Project will receive an immutable, minimized, normalized and human-reviewed J7 export as a durable input for future versioned enrichment and betting-analysis processing; it will not call SofaScore or trigger an acquisition.',
            'Betting Project will receive an immutable, minimized, normalized and human-reviewed J7 export as a durable input for future versioned enrichment and betting-analysis processing; it will call SofaScore and trigger an acquisition; it will not call SofaScore or trigger an acquisition.')
    Assert-WO034TestEqual $semanticContradictory.ExitCode 1 `
        'contradictory semantic fixture exit code'
    Assert-WO034TestEqual `
        $semanticContradictory.Values['SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS'] `
        'NO' 'contradictory semantic fixture coverage'

    $reserved = Invoke-WO034PreflightTestCase -Name 'reserved' -Content `
        $fixture.Replace('REQUESTOR_ROLE=Synthetic project reviewer',
            'REQUESTOR_ROLE=Synthetic project reviewer <reserved>')
    Assert-WO034TestEqual $reserved.ExitCode 1 'reserved fixture exit code'
    Assert-WO034TestEqual $reserved.Values['FORMAT_ERROR_COUNT'] '1' `
        'reserved fixture format count'

    $secretLike = Invoke-WO034PreflightTestCase -Name 'secret-like' -Content `
        $fixture.Replace('REQUESTOR_ROLE=Synthetic project reviewer',
            'REQUESTOR_ROLE=api_key=synthetic-secret-like-value')
    Assert-WO034TestEqual $secretLike.ExitCode 1 'secret-like fixture exit code'
    Assert-WO034TestEqual $secretLike.Values['FORMAT_ERROR_COUNT'] '1' `
        'secret-like fixture format count'

    $crlf = Invoke-WO034PreflightTestCase -Name 'crlf' -Content `
        $fixture.Replace("`n", "`r`n")
    Assert-WO034TestEqual $crlf.ExitCode 1 'CRLF fixture exit code'
    Assert-WO034TestEqual $crlf.Values['INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE'] 'NO' `
        'CRLF fixture line contract'

    $missingFinalNewline = Invoke-WO034PreflightTestCase -Name 'missing-final-newline' `
        -Content $fixture.TrimEnd("`n")
    Assert-WO034TestEqual $missingFinalNewline.ExitCode 1 `
        'missing final newline fixture exit code'
    Assert-WO034TestEqual `
        $missingFinalNewline.Values['INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE'] 'NO' `
        'missing final newline fixture line contract'

    $bomPath = Join-Path $temporaryRoot 'bom.properties'
    $bomUtf8 = [Text.UTF8Encoding]::new($true, $true)
    [IO.File]::WriteAllBytes($bomPath, $bomUtf8.GetPreamble() + $utf8.GetBytes($fixture))
    $bomResult = Invoke-WO034PreflightTestProcess -Path $bomPath
    Assert-WO034TestEqual $bomResult.ExitCode 1 'BOM fixture exit code'
    if (-not $bomResult.StandardOutput.Contains(
            'INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE=NO',
            [StringComparison]::Ordinal)) {
        throw 'WO-034 preflight test assertion failed: BOM fixture encoding contract.'
    }
    Assert-WO034NoOwnerControlledValueLeak -Result $bomResult -Content $fixture `
        -CaseName 'bom'

    Write-Output 'WO034_PREFLIGHT_TEST_STATUS=PASS'
    Write-Output 'WO034_PREFLIGHT_TEST_CASE_COUNT=22'
    Write-Output 'WO034_PREFLIGHT_TEST_OWNER_VALUE_LEAK_COUNT=0'
    Write-Output 'WO034_PREFLIGHT_TEST_RENDERER_EXECUTION_COUNT=0'
} finally {
    $canonicalTemporaryRoot = [IO.Path]::GetFullPath($temporaryRoot)
    $canonicalSystemTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).
        TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $canonicalTemporaryRoot.StartsWith(
            $canonicalSystemTemporaryRoot, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($canonicalTemporaryRoot) -cnotmatch
            '^wo034-preflight-tests-[0-9a-f]{32}$') {
        throw 'The WO-034 preflight test temporary root failed confinement validation.'
    }
    if (Test-Path -LiteralPath $canonicalTemporaryRoot) {
        Remove-Item -LiteralPath $canonicalTemporaryRoot -Recurse -Force
    }
}
