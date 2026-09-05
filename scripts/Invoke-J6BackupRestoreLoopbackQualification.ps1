[CmdletBinding()]
param(
    [switch]$WithDocker,
    [string]$DockerPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the J6 native pipeline qualification.'
}

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$modulePath = Join-Path $PSScriptRoot 'J6-NativeBinaryPipeline.psm1'
Import-Module -Name $modulePath -Force

function Assert-J6Qualification {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message,
        [string]$FailureMarker
    )
    if (-not $Condition) {
        if ($FailureMarker) {
            throw (New-J6QualificationSanitizedException -Classification $FailureMarker)
        }
        throw $Message
    }
}

function ConvertFrom-J6QualificationCanonicalCount {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Value
    )

    if ($Value -notmatch '\A(0|[1-9][0-9]*)(?:\r?\n)?\z') {
        throw [IO.InvalidDataException]::new(
            'J6_QUALIFICATION_POSTGRES_SCALAR_OUTPUT_INVALID')
    }

    $canonicalDigits = $Value.TrimEnd("`r", "`n")
    $parsed = 0L
    if (-not [long]::TryParse(
            $canonicalDigits,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsed)) {
        throw [IO.InvalidDataException]::new(
            'J6_QUALIFICATION_POSTGRES_SCALAR_OUTPUT_INVALID')
    }
    return $parsed
}

function Assert-J6QualificationDockerExecutableIdentity {
    param([Parameter(Mandatory = $true)][string]$ExecutablePath)

    $canonicalPath = [IO.Path]::GetFullPath($ExecutablePath)
    $item = Get-Item -LiteralPath $canonicalPath -Force
    if ($item.Name -cne 'docker.exe' -or
        ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        $item.VersionInfo.CompanyName -cne 'Docker Inc' -or
        $item.VersionInfo.ProductName -cne 'Docker Client') {
        throw 'The qualification Docker executable identity is invalid.'
    }
    $signature = Get-AuthenticodeSignature -LiteralPath $canonicalPath
    if ($signature.Status -ne [Management.Automation.SignatureStatus]::Valid -or
        $null -eq $signature.SignerCertificate -or
        $signature.SignerCertificate.Subject -cnotmatch
            '(^|, )O=Docker Inc(,|$)') {
        throw 'The qualification Docker Authenticode identity is invalid.'
    }
}

function Assert-J6QualificationCanonicalCountParser {
    foreach ($accepted in @(
            '0',
            "0`n",
            "42`r`n",
            '9223372036854775807')) {
        [void](ConvertFrom-J6QualificationCanonicalCount -Value $accepted)
    }

    foreach ($rejected in @(
            '',
            "`n",
            ' 0',
            '0 ',
            '+1',
            '-1',
            '1.0',
            "0`n0`n",
            "warning`n0`n",
            '9223372036854775808')) {
        $wasRejected = $false
        try {
            [void](ConvertFrom-J6QualificationCanonicalCount -Value $rejected)
        }
        catch [IO.InvalidDataException] {
            $wasRejected = $true
        }
        Assert-J6Qualification $wasRejected `
            'The strict qualification scalar parser accepted a non-canonical value.'
    }
    Write-Host 'J6_POSTGRES_STRICT_SCALAR_PARSER=PASS'
}

function Assert-J6RuntimePostgresCleanupContracts {
    $backupScriptPath = Join-Path $PSScriptRoot 'Backup-Restore-J6.ps1'
    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile(
        $backupScriptPath,
        [ref]$tokens,
        [ref]$parseErrors)
    Assert-J6Qualification ($parseErrors.Count -eq 0) `
        'The J6 backup script could not be parsed for isolated runtime qualification.'

    $requiredFunctionNames = @(
        'ConvertTo-J6SanitizedInnerException',
        'New-J6SanitizedCleanupException',
        'Get-J6SanitizedCleanupClassification',
        'ConvertFrom-J6StrictNonNegativeInt64Scalar',
        'ConvertFrom-J6StrictTerminationEvidenceScalar',
        'Assert-J6OwnedPostgresApplicationName',
        'Merge-J6PostgresTerminationEvidence',
        'Resolve-J6PostgresCleanupTimeoutClassification',
        'Get-J6RemainingCleanupMilliseconds',
        'Invoke-J6BoundedDockerCleanupCommand',
        'Invoke-J6BoundedPrimaryCleanupScalar',
        'Confirm-J6OwnedPostgresSessionCleanup')
    $functionDefinitions = @($ast.FindAll({
                param($node)
                $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
                    $node.Name -in $requiredFunctionNames
            }, $true))
    Assert-J6Qualification (
        $functionDefinitions.Count -eq $requiredFunctionNames.Count) `
        'The exact runtime PostgreSQL cleanup helpers could not be isolated.'
    $runtimeModuleText = ($functionDefinitions | Sort-Object {
            [array]::IndexOf($requiredFunctionNames, $_.Name)
        } | ForEach-Object {
            $_.Extent.Text
        }) -join "`n`n"
    $runtimeModule = New-Module -ScriptBlock ([ScriptBlock]::Create(
            "Set-StrictMode -Version 3.0`n$runtimeModuleText"))
    try {
        $runtimeResult = & $runtimeModule {
            $accepted = @(
                '0',
                "0`n",
                "42`r`n",
                '9223372036854775807')
            $rejected = @(
                '',
                "`n",
                ' 0',
                '0 ',
                '+1',
                '-1',
                '1.0',
                "0`n0`n",
                "warning`n0`n",
                '9223372036854775808')
            foreach ($value in $accepted) {
                [void](ConvertFrom-J6StrictNonNegativeInt64Scalar -Value $value)
            }
            $rejectedCount = 0
            foreach ($value in $rejected) {
                try {
                    [void](ConvertFrom-J6StrictNonNegativeInt64Scalar -Value $value)
                }
                catch {
                    if ($_.Exception.Message -eq `
                            'J6_CLEANUP_FAILURE=POSTGRES_SCALAR_OUTPUT_INVALID' -and
                        $_.Exception.Data['J6CleanupClassification'] -eq `
                            'POSTGRES_SCALAR_OUTPUT_INVALID' -and
                        ($value.Length -eq 0 -or
                            -not $_.Exception.Message.Contains($value))) {
                        $rejectedCount++
                    }
                }
            }
            $terminationEvidenceScalar =
                ConvertFrom-J6StrictTerminationEvidenceScalar `
                    -Value "2,1`r`n"
            $rejectedTerminationEvidenceCount = 0
            foreach ($value in @(
                    '',
                    '1',
                    '1,2',
                    '01,0',
                    '1, 0',
                    '1,0,0',
                    "1,0`n0,0`n")) {
                try {
                    [void](ConvertFrom-J6StrictTerminationEvidenceScalar `
                            -Value $value)
                }
                catch {
                    $rejectedTerminationEvidenceCount++
                }
            }
            $inner = [InvalidOperationException]::new(
                'SYNTHETIC_INNER_CAUSE_MUST_NOT_ESCAPE')
            $outer = New-J6SanitizedCleanupException `
                -Classification POSTGRES_DOCKER_COMMAND_FAILED `
                -InnerException $inner

            Assert-J6OwnedPostgresApplicationName `
                -ApplicationName `
                    'j6_backup_0123456789abcdef0123456789abcdef'
            $uppercaseApplicationNameRejected = $false
            try {
                Assert-J6OwnedPostgresApplicationName `
                    -ApplicationName `
                        'J6_BACKUP_0123456789ABCDEF0123456789ABCDEF'
            }
            catch {
                $uppercaseApplicationNameRejected = $true
            }

            $mergedEvidence = Merge-J6PostgresTerminationEvidence `
                -TargetedSessionAttempts 0 `
                -SuccessfulTerminationSignals 0 `
                -TargetedSessionCountNow 1 `
                -SuccessfulSignalsNow 1
            $mergedEvidence = Merge-J6PostgresTerminationEvidence `
                -TargetedSessionAttempts `
                    $mergedEvidence.TargetedSessionAttempts `
                -SuccessfulTerminationSignals `
                    $mergedEvidence.SuccessfulTerminationSignals `
                -TargetedSessionCountNow 1 `
                -SuccessfulSignalsNow 1
            $invalidTerminationEvidenceRejected = $false
            try {
                [void](Merge-J6PostgresTerminationEvidence `
                        -TargetedSessionAttempts 0 `
                        -SuccessfulTerminationSignals 0 `
                        -TargetedSessionCountNow 1 `
                        -SuccessfulSignalsNow 2)
            }
            catch {
                $invalidTerminationEvidenceRejected =
                    $_.Exception.Data['J6CleanupClassification'] -eq
                        'POSTGRES_SESSION_CLEANUP_UNCONFIRMED'
            }

            $script:dockerExecutable = 'synthetic-docker.exe'
            $script:repositoryRoot = 'C:\synthetic-j6-qualification'
            $script:PipelineCleanupTimeoutMilliseconds = 5000
            $script:nativeCleanupMode = 'SUCCESS'
            function script:Invoke-J6BoundedNativeCommand {
                param(
                    [string]$FilePath,
                    [string[]]$ArgumentList,
                    [string]$WorkingDirectory,
                    [int]$TimeoutMilliseconds,
                    [int]$CleanupTimeoutMilliseconds,
                    [DateTime]$OverallCommandDeadlineUtc
                )
                switch ($script:nativeCleanupMode) {
                    'TIMEOUT' {
                        throw [TimeoutException]::new(
                            'SYNTHETIC_RAW_TIMEOUT_DETAIL_MUST_NOT_ESCAPE')
                    }
                    'PROCESS_THROW' {
                        $failure = [InvalidOperationException]::new(
                            'SYNTHETIC_RAW_PROCESS_DETAIL_MUST_NOT_ESCAPE')
                        $failure.Data['J6ProcessTreeCleanup'] = 'UNCONFIRMED'
                        throw $failure
                    }
                    'COMMAND_THROW' {
                        throw [InvalidOperationException]::new(
                            'SYNTHETIC_RAW_COMMAND_DETAIL_MUST_NOT_ESCAPE')
                    }
                    'PROCESS_RESULT' {
                        return [pscustomobject]@{
                            ProcessTreeCleanup = 'UNCONFIRMED'
                            UnexpectedDescendantCleanup = $true
                            ExitCode = 0
                            StandardOutput = ''
                        }
                    }
                    'NONZERO' {
                        return [pscustomobject]@{
                            ProcessTreeCleanup = 'PASS'
                            UnexpectedDescendantCleanup = $false
                            ExitCode = 7
                            StandardOutput = ''
                        }
                    }
                    'SQL_NONZERO' {
                        return [pscustomobject]@{
                            ProcessTreeCleanup = 'PASS'
                            UnexpectedDescendantCleanup = $false
                            ExitCode = 86
                            StandardOutput = ''
                        }
                    }
                    default {
                        return [pscustomobject]@{
                            ProcessTreeCleanup = 'PASS'
                            UnexpectedDescendantCleanup = $false
                            ExitCode = 0
                            StandardOutput = "0`n"
                        }
                    }
                }
            }

            $observedCleanupClasses =
                [Collections.Generic.List[string]]::new()
            $rawCauseEscaped = $false
            foreach ($case in @(
                    @('TIMEOUT', 'POSTGRES_OBSERVATION_TIMEOUT', $false),
                    @('PROCESS_THROW',
                        'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED', $false),
                    @('COMMAND_THROW', 'POSTGRES_DOCKER_COMMAND_FAILED', $false),
                    @('PROCESS_RESULT',
                        'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED', $false),
                    @('NONZERO', 'POSTGRES_DOCKER_COMMAND_NONZERO_EXIT', $false),
                    @('SQL_NONZERO', 'POSTGRES_SQL_COMMAND_NONZERO_EXIT', $true))) {
                $script:nativeCleanupMode = [string]$case[0]
                try {
                    if ([bool]$case[2]) {
                        [void](Invoke-J6BoundedPrimaryCleanupScalar `
                                -Sql 'select synthetic_failure' `
                                -DeadlineUtc ([DateTime]::UtcNow.AddSeconds(2)))
                    }
                    else {
                        [void](Invoke-J6BoundedDockerCleanupCommand `
                                -ArgumentList @('synthetic') `
                                -DeadlineUtc ([DateTime]::UtcNow.AddSeconds(2)))
                    }
                }
                catch {
                    $classification = [string]$_.Exception.Data[
                        'J6CleanupClassification']
                    $observedCleanupClasses.Add($classification)
                    if ($_.Exception.ToString().Contains(
                            'SYNTHETIC_RAW_')) {
                        $rawCauseEscaped = $true
                    }
                }
            }

            $script:confirmedPostgresCleanupProofs =
                [Collections.Generic.Dictionary[string, object]]::new(
                    [StringComparer]::Ordinal)
            $script:mockSessionCounts = $null
            $script:mockTerminationTargetCounts = $null
            $script:mockTerminationSignals = $null
            $script:mockDefaultSessionCount = 0L
            $script:mockDefaultTerminationTargetCount = 0L
            $script:mockDefaultTerminationSignal = 0L
            $script:mockCountDelayMilliseconds = 0
            $script:mockTerminationDelayMilliseconds = 0
            $script:mockCountInvocationCount = 0
            $script:mockTerminationInvocationCount = 0
            function script:Set-J6PostgresCleanupMock {
                param(
                    [long[]]$SessionCounts,
                    [long[]]$TerminationTargetCounts,
                    [long[]]$TerminationSignals,
                    [long]$DefaultSessionCount = 0,
                    [long]$DefaultTerminationTargetCount = 0,
                    [long]$DefaultTerminationSignal = 0,
                    [int]$CountDelayMilliseconds = 0,
                    [int]$TerminationDelayMilliseconds = 0
                )
                $script:mockSessionCounts =
                    [Collections.Generic.Queue[long]]::new()
                foreach ($count in @($SessionCounts)) {
                    $script:mockSessionCounts.Enqueue($count)
                }
                $script:mockTerminationTargetCounts =
                    [Collections.Generic.Queue[long]]::new()
                foreach ($count in @($TerminationTargetCounts)) {
                    $script:mockTerminationTargetCounts.Enqueue($count)
                }
                $script:mockTerminationSignals =
                    [Collections.Generic.Queue[long]]::new()
                foreach ($signal in @($TerminationSignals)) {
                    $script:mockTerminationSignals.Enqueue($signal)
                }
                $script:mockDefaultSessionCount = $DefaultSessionCount
                $script:mockDefaultTerminationTargetCount =
                    $DefaultTerminationTargetCount
                $script:mockDefaultTerminationSignal =
                    $DefaultTerminationSignal
                $script:mockCountDelayMilliseconds = $CountDelayMilliseconds
                $script:mockTerminationDelayMilliseconds =
                    $TerminationDelayMilliseconds
                $script:mockCountInvocationCount = 0
                $script:mockTerminationInvocationCount = 0
            }
            function script:Get-J6OwnedPostgresSessionCount {
                param([string]$ApplicationName, [DateTime]$DeadlineUtc)
                $script:mockCountInvocationCount++
                if ($script:mockCountDelayMilliseconds -gt 0) {
                    Start-Sleep -Milliseconds `
                        $script:mockCountDelayMilliseconds
                }
                if ($script:mockSessionCounts.Count -ne 0) {
                    return $script:mockSessionCounts.Dequeue()
                }
                return $script:mockDefaultSessionCount
            }
            function script:Invoke-J6BoundedPrimaryCleanupTerminationEvidence {
                param([string]$Sql, [DateTime]$DeadlineUtc)
                $script:mockTerminationInvocationCount++
                if ($script:mockTerminationDelayMilliseconds -gt 0) {
                    Start-Sleep -Milliseconds `
                        $script:mockTerminationDelayMilliseconds
                }
                $targeted = if (
                    $script:mockTerminationTargetCounts.Count -ne 0) {
                    $script:mockTerminationTargetCounts.Dequeue()
                }
                else {
                    $script:mockDefaultTerminationTargetCount
                }
                $successful = if ($script:mockTerminationSignals.Count -ne 0) {
                    $script:mockTerminationSignals.Dequeue()
                }
                else {
                    $script:mockDefaultTerminationSignal
                }
                return [pscustomobject]@{
                    TargetedSessionCount = $targeted
                    SuccessfulTerminationSignals = $successful
                }
            }

            $script:PostgresCleanupTimeoutMilliseconds = 250
            Set-J6PostgresCleanupMock `
                -SessionCounts @(1) `
                -TerminationTargetCounts @(1) `
                -TerminationSignals @(1) `
                -DefaultSessionCount 0 `
                -TerminationDelayMilliseconds 300
            $staleRemainingClass = $null
            $staleRemainingEvidence = $null
            try {
                [void](Confirm-J6OwnedPostgresSessionCleanup `
                        -ApplicationName `
                            'j6_backup_11111111111111111111111111111111')
            }
            catch {
                $staleRemainingClass = [string]$_.Exception.Data[
                    'J6CleanupClassification']
                $staleRemainingEvidence = [string]$_.Exception.Data[
                    'J6RemainingSessions']
            }

            $script:PostgresCleanupTimeoutMilliseconds = 1000
            Set-J6PostgresCleanupMock `
                -SessionCounts @(1, 0, 0, 0) `
                -TerminationTargetCounts @(1) `
                -TerminationSignals @(0)
            $naturalExitProof = Confirm-J6OwnedPostgresSessionCleanup `
                -ApplicationName `
                    'j6_backup_22222222222222222222222222222222'

            $script:PostgresCleanupTimeoutMilliseconds = 1400
            Set-J6PostgresCleanupMock `
                -SessionCounts @(1, 1, 0, 0, 0) `
                -TerminationTargetCounts @(1, 1) `
                -TerminationSignals @(1, 1)
            $counterProof = Confirm-J6OwnedPostgresSessionCleanup `
                -ApplicationName `
                    'j6_backup_33333333333333333333333333333333'
            $countCallsBeforeCache = $script:mockCountInvocationCount
            $terminationCallsBeforeCache =
                $script:mockTerminationInvocationCount
            $cachedCounterProof = Confirm-J6OwnedPostgresSessionCleanup `
                -ApplicationName `
                    'j6_backup_33333333333333333333333333333333'
            $countCallsAfterCache = $script:mockCountInvocationCount
            $terminationCallsAfterCache =
                $script:mockTerminationInvocationCount

            $script:PostgresCleanupTimeoutMilliseconds = 1400
            Set-J6PostgresCleanupMock `
                -SessionCounts @(0, 0, 0, 0) `
                -TerminationTargetCounts @() `
                -TerminationSignals @() `
                -CountDelayMilliseconds 100
            $absentSlowProof = Confirm-J6OwnedPostgresSessionCleanup `
                -ApplicationName `
                    'j6_restore_44444444444444444444444444444444'

            $script:PostgresCleanupTimeoutMilliseconds = 600
            Set-J6PostgresCleanupMock `
                -SessionCounts @(1) `
                -TerminationTargetCounts @(1) `
                -TerminationSignals @(0) `
                -DefaultSessionCount 1 `
                -DefaultTerminationTargetCount 1 `
                -DefaultTerminationSignal 0
            $persistentRefusalClass = $null
            $persistentRefusalRemaining = $null
            try {
                [void](Confirm-J6OwnedPostgresSessionCleanup `
                        -ApplicationName `
                            'j6_restore_55555555555555555555555555555555')
            }
            catch {
                $persistentRefusalClass = [string]$_.Exception.Data[
                    'J6CleanupClassification']
                $persistentRefusalRemaining = [long]$_.Exception.Data[
                    'J6RemainingSessions']
            }

            [pscustomobject]@{
                AcceptedCount = $accepted.Count
                RejectedCount = $rejectedCount
                TerminationEvidenceTargeted =
                    $terminationEvidenceScalar.TargetedSessionCount
                TerminationEvidenceSuccessful =
                    $terminationEvidenceScalar.SuccessfulTerminationSignals
                RejectedTerminationEvidenceCount =
                    $rejectedTerminationEvidenceCount
                InnerCauseSanitizedCopy =
                    -not [object]::ReferenceEquals($inner, $outer.InnerException)
                InnerCauseCategory = ([string]$outer.InnerException.Data[
                        'J6SanitizedCauseCategory'])
                FullExceptionSanitized =
                    -not $outer.ToString().Contains(
                        'SYNTHETIC_INNER_CAUSE_MUST_NOT_ESCAPE')
                UppercaseApplicationNameRejected =
                    $uppercaseApplicationNameRejected
                MergedTargetedSessionAttempts =
                    $mergedEvidence.TargetedSessionAttempts
                MergedSuccessfulTerminationSignals =
                    $mergedEvidence.SuccessfulTerminationSignals
                InvalidTerminationEvidenceRejected =
                    $invalidTerminationEvidenceRejected
                StaleCountClassification = (
                    Resolve-J6PostgresCleanupTimeoutClassification `
                        -LastSessionCount 1 `
                        -LastObservationIsFreshAfterTermination $false)
                FreshCountClassification = (
                    Resolve-J6PostgresCleanupTimeoutClassification `
                        -LastSessionCount 1 `
                        -LastObservationIsFreshAfterTermination $true)
                ObservedCleanupClasses = (
                    [string[]]$observedCleanupClasses.ToArray())
                RawCauseEscaped = $rawCauseEscaped
                StaleRemainingClass = $staleRemainingClass
                StaleRemainingEvidence = $staleRemainingEvidence
                NaturalExitProof = $naturalExitProof
                CounterProof = $counterProof
                CachedCounterProof = $cachedCounterProof
                CountCallsBeforeCache = $countCallsBeforeCache
                CountCallsAfterCache = $countCallsAfterCache
                TerminationCallsBeforeCache = $terminationCallsBeforeCache
                TerminationCallsAfterCache = $terminationCallsAfterCache
                AbsentSlowProof = $absentSlowProof
                PersistentRefusalClass = $persistentRefusalClass
                PersistentRefusalRemaining = $persistentRefusalRemaining
            }
        }
    }
    finally {
        Remove-Module -ModuleInfo $runtimeModule -Force
    }
    Assert-J6Qualification (
        $runtimeResult.AcceptedCount -eq 4 -and
        $runtimeResult.RejectedCount -eq 10) `
        'The exact runtime PostgreSQL scalar parser did not enforce its canonical contract.'
    Assert-J6Qualification (
        $runtimeResult.TerminationEvidenceTargeted -eq 2 -and
        $runtimeResult.TerminationEvidenceSuccessful -eq 1 -and
        $runtimeResult.RejectedTerminationEvidenceCount -eq 7) `
        'The exact runtime PostgreSQL termination evidence parser was not strict.'
    Assert-J6Qualification (
        $runtimeResult.InnerCauseSanitizedCopy -and
        $runtimeResult.InnerCauseCategory -eq 'INVALID_OPERATION' -and
        $runtimeResult.FullExceptionSanitized) `
        'The exact runtime cleanup exception did not preserve a sanitized inner cause.'
    Assert-J6Qualification $runtimeResult.UppercaseApplicationNameRejected `
        'The exact PostgreSQL ownership name accepted a non-canonical case variant.'
    Assert-J6Qualification (
        $runtimeResult.MergedTargetedSessionAttempts -eq 2 -and
        $runtimeResult.MergedSuccessfulTerminationSignals -eq 2 -and
        $runtimeResult.InvalidTerminationEvidenceRejected) `
        'The PostgreSQL termination evidence counters are not cumulative and coherent.'
    Assert-J6Qualification (
        $runtimeResult.StaleCountClassification -eq
            'POSTGRES_OBSERVATION_TIMEOUT' -and
        $runtimeResult.FreshCountClassification -eq
            'POSTGRES_SESSION_REMAINING') `
        'The PostgreSQL timeout classifier accepted a stale remaining-session count.'
    $expectedCleanupClasses = @(
        'POSTGRES_OBSERVATION_TIMEOUT',
        'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED',
        'POSTGRES_DOCKER_COMMAND_FAILED',
        'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED',
        'POSTGRES_DOCKER_COMMAND_NONZERO_EXIT',
        'POSTGRES_SQL_COMMAND_NONZERO_EXIT')
    Assert-J6Qualification (
        -not $runtimeResult.RawCauseEscaped -and
        [string]::Join(',', $runtimeResult.ObservedCleanupClasses) -ceq
            [string]::Join(',', $expectedCleanupClasses)) `
        'The bounded Docker/PostgreSQL runtime failure classes are not distinct and sanitized.'
    Assert-J6Qualification (
        $runtimeResult.StaleRemainingClass -eq
            'POSTGRES_OBSERVATION_TIMEOUT' -and
        $runtimeResult.StaleRemainingEvidence -ceq
            'UNCONFIRMED_AFTER_TERMINATION') `
        'A pre-termination PostgreSQL count was reported as a fresh remaining session.'
    Assert-J6Qualification (
        $runtimeResult.NaturalExitProof.Classification -eq 'PASS' -and
        $runtimeResult.NaturalExitProof.TargetedSessionAttempts -eq 1 -and
        $runtimeResult.NaturalExitProof.SuccessfulTerminationSignals -eq 0 -and
        $runtimeResult.NaturalExitProof.StableZeroObservations -eq 3) `
        'A naturally terminated owned PostgreSQL session was not proven absent.'
    Assert-J6Qualification (
        $runtimeResult.CounterProof.Classification -eq 'PASS' -and
        $runtimeResult.CounterProof.TargetedSessionAttempts -eq 2 -and
        $runtimeResult.CounterProof.SuccessfulTerminationSignals -eq 2 -and
        $runtimeResult.CounterProof.StableZeroObservations -eq 3) `
        'Repeated PostgreSQL termination evidence became internally contradictory.'
    Assert-J6Qualification (
        [object]::ReferenceEquals(
            $runtimeResult.CounterProof,
            $runtimeResult.CachedCounterProof) -and
        $runtimeResult.CountCallsBeforeCache -eq
            $runtimeResult.CountCallsAfterCache -and
        $runtimeResult.TerminationCallsBeforeCache -eq
            $runtimeResult.TerminationCallsAfterCache) `
        'The second exact PostgreSQL cleanup confirmation reopened runtime activity.'
    Assert-J6Qualification (
        $runtimeResult.AbsentSlowProof.Classification -eq 'PASS' -and
        $runtimeResult.AbsentSlowProof.TargetedSessionAttempts -eq 0 -and
        $runtimeResult.AbsentSlowProof.StableZeroObservations -eq 3) `
        'An already-absent session with bounded slow observations did not pass.'
    Assert-J6Qualification (
        $runtimeResult.PersistentRefusalClass -eq
            'POSTGRES_SESSION_REMAINING' -and
        $runtimeResult.PersistentRefusalRemaining -eq 1) `
        'A persistent or refused PostgreSQL cleanup did not fail closed.'

    $psqlLiterals = @($ast.FindAll({
                param($node)
                $node -is [Management.Automation.Language.StringConstantExpressionAst] -and
                    $node.Value -match 'psql\s+--username'
            }, $true))
    Assert-J6Qualification ($psqlLiterals.Count -ge 3) `
        'The runtime psql command paths were not all discoverable.'
    Assert-J6Qualification (
        @($psqlLiterals | Where-Object {
                $_.Value -notmatch '--set=ON_ERROR_STOP=1'
            }).Count -eq 0) `
        'A runtime psql path does not fail closed on SQL errors.'

    Write-Host 'J6_RUNTIME_POSTGRES_STRICT_SCALAR_PARSER=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_STRICT_TERMINATION_EVIDENCE_PARSER=PASS'
    Write-Host 'J6_RUNTIME_SANITIZED_INNER_CAUSE=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_EXACT_OWNERSHIP_NAME=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_TERMINATION_EVIDENCE_COUNTERS=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_STALE_COUNT_CLASSIFICATION=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_STATE_MACHINE_COUNTEREXAMPLES=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_IDEMPOTENT_CONFIRMATION=PASS'
    Write-Host 'J6_RUNTIME_POSTGRES_FAILURE_CLASSIFICATION=PASS'
    Write-Host 'J6_RUNTIME_PSQL_ON_ERROR_STOP_STATIC_CONTRACT=PASS'
}

function Add-J6OwnedPipelineProcessEvidence {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [Collections.Generic.List[object]]$Collection,
        [Parameter(Mandatory = $true)]$Result
    )
    foreach ($pair in @(
            @($Result.ProducerPid, $Result.ProducerStartedAtUtc),
            @($Result.ConsumerPid, $Result.ConsumerStartedAtUtc))) {
        if ($null -eq $pair[0] -or [string]::IsNullOrWhiteSpace([string]$pair[1])) {
            continue
        }
        $Collection.Add([pscustomobject]@{
                ProcessId = [int]$pair[0]
                StartedAtUtcTicks = [DateTime]::Parse(
                    [string]$pair[1],
                    [Globalization.CultureInfo]::InvariantCulture,
                    [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime().Ticks
            })
    }

    foreach ($pair in @(
            @($Result.ProducerTargetPid, $Result.ProducerTargetStartedAtUtcTicks),
            @($Result.ConsumerTargetPid, $Result.ConsumerTargetStartedAtUtcTicks))) {
        if ($null -eq $pair[0] -and $null -eq $pair[1]) {
            continue
        }
        Assert-J6Qualification (
            $null -ne $pair[0] -and $null -ne $pair[1]) `
            'A native target identity was only partially reported.'
        $Collection.Add([pscustomobject]@{
                ProcessId = [int]$pair[0]
                StartedAtUtcTicks = [long]$pair[1]
            })
    }

    if ($IsWindows) {
        foreach ($root in @('Producer', 'Consumer')) {
            $confinementProperty = $root + 'Confinement'
            $activeProperty = $root + 'ActiveProcessesAfterCleanup'
            if ($Result.$confinementProperty -eq 'WINDOWS_KILL_ON_JOB_CLOSE') {
                Assert-J6Qualification (
                    $null -ne $Result.$activeProperty -and
                    [int]$Result.$activeProperty -eq 0) `
                    'A qualified native pipeline Job Object was not empty after cleanup.'
            }
        }
    }
}

function Resolve-J6ProcessIdentityObservation {
    param(
        [Parameter(Mandatory = $true)][string]$DotNet,
        [Parameter(Mandatory = $true)][string]$Toolhelp,
        [Parameter(Mandatory = $true)][string]$Cim,
        [Parameter(Mandatory = $true)][string]$Tasklist
    )

    if ($DotNet -eq 'EXACT_ACTIVE') {
        if ($Toolhelp -eq 'VISIBLE' -and
            $Cim -eq 'VISIBLE' -and
            $Tasklist -eq 'VISIBLE') {
            return 'ACTIVE_EXACT'
        }
        return 'UNVERIFIABLE'
    }
    if ($DotNet -eq 'PID_REUSED') {
        return 'PID_REUSED_NOT_OWNED'
    }

    $secondaryViews = @($Toolhelp, $Cim, $Tasklist)
    if ($secondaryViews -contains 'VISIBLE') {
        return 'AMBIGUOUS_CROSS_API_GHOST_VISIBILITY'
    }
    if ($DotNet -eq 'ABSENT' -and
        @($secondaryViews | Where-Object { $_ -ne 'ABSENT' }).Count -eq 0) {
        return 'ABSENT_ALL_VIEWS'
    }
    return 'UNVERIFIABLE'
}

function Invoke-J6BoundedTasklistObservation {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,
        [ValidateRange(100, 10000)]
        [int]$TimeoutMilliseconds = 5000
    )

    $tasklistPath = Join-Path $env:WINDIR 'System32\tasklist.exe'
    if (-not (Test-Path -LiteralPath $tasklistPath -PathType Leaf)) {
        throw (New-J6QualificationSanitizedException `
                -Classification 'TASKLIST_OBSERVER_EXECUTABLE_UNAVAILABLE')
    }

    try {
        $result = Invoke-J6BoundedNativeCommand `
            -FilePath $tasklistPath `
            -ArgumentList $ArgumentList `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds $TimeoutMilliseconds `
            -StartupTimeoutMilliseconds 5000 `
            -CleanupTimeoutMilliseconds 5000
    }
    catch {
        throw (New-J6QualificationSanitizedException `
                -Classification 'TASKLIST_OBSERVER_EXECUTION_FAILED' `
                -InnerException $_.Exception)
    }

    if ($result.ExitCode -ne 0) {
        throw (New-J6QualificationSanitizedException `
                -Classification 'TASKLIST_OBSERVER_NONZERO_EXIT')
    }
    if ($result.ProcessTreeCleanup -cne 'PASS' -or
        $result.Confinement -cne 'WINDOWS_KILL_ON_JOB_CLOSE' -or
        $null -eq $result.ActiveProcessesAfterCleanup -or
        [int]$result.ActiveProcessesAfterCleanup -ne 0 -or
        $null -eq $result.TargetProcessId -or
        $null -eq $result.TargetStartedAtUtcTicks) {
        throw (New-J6QualificationSanitizedException `
                -Classification 'TASKLIST_OBSERVER_CLEANUP_UNCONFIRMED')
    }

    return [string]$result.StandardOutput
}

function New-J6SecondaryProcessObservationSnapshot {
    param(
        [ValidateRange(1, 5)][int]$MaximumAttempts = 3,
        [ValidateRange(10, 1000)][int]$RetryDelayMilliseconds = 100
    )

    $snapshot = $null
    for ($attempt = 1; $attempt -le $MaximumAttempts; $attempt++) {
        $cimProcessIds = [Collections.Generic.HashSet[int]]::new()
        $cimState = 'AVAILABLE'
        try {
            foreach ($entry in @(Get-CimInstance `
                    -ClassName Win32_Process `
                    -Property ProcessId `
                    -OperationTimeoutSec 5 `
                    -ErrorAction Stop)) {
                [void]$cimProcessIds.Add([int]$entry.ProcessId)
            }
        }
        catch {
            $cimState = 'ERROR'
        }

        $tasklistProcessIds = [Collections.Generic.HashSet[int]]::new()
        $tasklistState = 'ERROR'
        try {
            $tasklistOutput = Invoke-J6BoundedTasklistObservation `
                -ArgumentList @('/FO', 'CSV', '/NH')
            $tasklistState = 'AVAILABLE'
            foreach ($line in @($tasklistOutput -split '\r?\n')) {
                if ($line -match '^"[^"]+","(?<pid>[0-9]+)",') {
                    [void]$tasklistProcessIds.Add([int]$Matches.pid)
                }
            }
        }
        catch {
            $tasklistState = 'ERROR'
        }

        $snapshot = [pscustomobject]@{
            CimState = $cimState
            CimProcessIds = $cimProcessIds
            TasklistState = $tasklistState
            TasklistProcessIds = $tasklistProcessIds
            AttemptCount = $attempt
        }
        if ($cimState -eq 'AVAILABLE' -and
            $tasklistState -eq 'AVAILABLE') {
            return $snapshot
        }
        if ($attempt -lt $MaximumAttempts) {
            Start-Sleep -Milliseconds $RetryDelayMilliseconds
        }
    }

    return $snapshot
}

function Get-J6ProcessIdentityObservation {
    param(
        [Parameter(Mandatory = $true)]$Identity,
        $SecondarySnapshot
    )

    $processId = [int]$Identity.ProcessId
    $startedAtUtcTicks = [long]$Identity.StartedAtUtcTicks
    $dotNet = 'ERROR'
    $process = $null
    try {
        $process = [Diagnostics.Process]::GetProcessById($processId)
        if ($process.StartTime.ToUniversalTime().Ticks -eq $startedAtUtcTicks -and
            -not $process.HasExited) {
            $dotNet = 'EXACT_ACTIVE'
        }
        else {
            $dotNet = 'PID_REUSED'
        }
    }
    catch [ArgumentException] {
        $dotNet = 'ABSENT'
    }
    catch [InvalidOperationException] {
        $dotNet = 'UNOPENABLE'
    }
    catch [ComponentModel.Win32Exception] {
        $dotNet = 'UNOPENABLE'
    }
    catch [UnauthorizedAccessException] {
        $dotNet = 'UNOPENABLE'
    }
    finally {
        if ($null -ne $process) {
            $process.Dispose()
        }
    }

    $toolhelp = 'ERROR'
    try {
        $toolhelp = if ([J6ProcessTreeSnapshot]::ContainsProcessId($processId)) {
            'VISIBLE'
        }
        else {
            'ABSENT'
        }
    }
    catch {
        $toolhelp = 'ERROR'
    }

    if ($null -ne $SecondarySnapshot) {
        $cim = if ($SecondarySnapshot.CimState -ne 'AVAILABLE') {
            'ERROR'
        }
        elseif ($SecondarySnapshot.CimProcessIds.Contains($processId)) {
            'VISIBLE'
        }
        else {
            'ABSENT'
        }
        $tasklist = if ($SecondarySnapshot.TasklistState -ne 'AVAILABLE') {
            'ERROR'
        }
        elseif ($SecondarySnapshot.TasklistProcessIds.Contains($processId)) {
            'VISIBLE'
        }
        else {
            'ABSENT'
        }
    }
    else {
        $cim = 'ERROR'
        try {
            $cimMatches = @(Get-CimInstance `
                -ClassName Win32_Process `
                -Filter "ProcessId = $processId" `
                -OperationTimeoutSec 3 `
                -ErrorAction Stop)
            $cim = if ($cimMatches.Count -eq 0) { 'ABSENT' } else { 'VISIBLE' }
        }
        catch {
            $cim = 'ERROR'
        }

        $tasklist = 'ERROR'
        try {
            $tasklistText = Invoke-J6BoundedTasklistObservation `
                -ArgumentList @('/FI', "PID eq $processId", '/FO', 'CSV', '/NH')
            $quotedPid = [regex]::Escape(('"{0}"' -f $processId))
            $tasklist = if ($tasklistText -match (',' + $quotedPid + ',')) {
                'VISIBLE'
            }
            else {
                'ABSENT'
            }
        }
        catch {
            $tasklist = 'ERROR'
        }
    }

    return [pscustomobject]@{
        DotNet = $dotNet
        Toolhelp = $toolhelp
        Cim = $cim
        Tasklist = $tasklist
        Classification = Resolve-J6ProcessIdentityObservation `
            -DotNet $dotNet `
            -Toolhelp $toolhelp `
            -Cim $cim `
            -Tasklist $tasklist
    }
}

function Assert-J6OwnedProcessIdentityGone {
    param(
        [Parameter(Mandatory = $true)]$Identity,
        $SecondarySnapshot,
        [switch]$Quiet,
        [ValidateRange(250, 10000)]
        [int]$GhostReobservationTimeoutMilliseconds = 5000,
        [ValidateRange(1, 5)]
        [int]$MaximumGhostReobservations = 3,
        [ValidateRange(10, 1000)]
        [int]$ObservationIntervalMilliseconds = 50
    )

    $writeObservation = {
        param($Observed)
        if (-not $Quiet) {
            Write-Host (
                'J6_PROCESS_IDENTITY_OBSERVATION=' +
                "DOTNET_$($Observed.DotNet)," +
                "TOOLHELP_$($Observed.Toolhelp)," +
                "CIM_$($Observed.Cim)," +
                "TASKLIST_$($Observed.Tasklist)," +
                "CLASS_$($Observed.Classification)")
        }
    }

    $observation = Get-J6ProcessIdentityObservation `
        -Identity $Identity `
        -SecondarySnapshot $SecondarySnapshot
    & $writeObservation $observation
    if ($observation.Classification -in @(
            'ABSENT_ALL_VIEWS',
            'PID_REUSED_NOT_OWNED')) {
        return $observation
    }
    if ($observation.Classification -eq 'ACTIVE_EXACT') {
        throw [InvalidOperationException]::new(
            'J6_PROCESS_IDENTITY_STILL_ACTIVE')
    }

    $secondaryStates = @(
        $observation.Toolhelp,
        $observation.Cim,
        $observation.Tasklist)
    $isTransientCrossApiGhostCandidate =
        $observation.Classification -eq
            'AMBIGUOUS_CROSS_API_GHOST_VISIBILITY' -and
        $observation.DotNet -eq 'ABSENT' -and
        $secondaryStates -contains 'VISIBLE' -and
        $secondaryStates -notcontains 'ERROR'
    if (-not $isTransientCrossApiGhostCandidate) {
        throw [InvalidOperationException]::new(
            'J6_PROCESS_IDENTITY_ABSENCE_UNCONFIRMED_' +
            [string]$observation.Classification)
    }

    # A batch CIM/tasklist snapshot can precede the exact .NET and Toolhelp
    # views by enough time for a short-lived, already-owned process to exit in
    # between. Any secondary view may therefore retain a transient PID ghost.
    # Only this precise state is retryable: .NET already proves absence, at
    # least one secondary view is visible, and no view errored. Each retry
    # rebuilds both secondary snapshots and re-runs the exact identity check.
    # The retry count and wall-clock window are both bounded. ACTIVE_EXACT,
    # inaccessible/error states and every other contradiction remain immediate
    # fail-closed outcomes. Re-observation is read-only and never authorizes
    # termination by PID.
    $deadline = [DateTime]::UtcNow.AddMilliseconds(
        $GhostReobservationTimeoutMilliseconds)
    $observationCount = 1
    for ($reobservationAttempt = 1;
        $reobservationAttempt -le $MaximumGhostReobservations -and
        [DateTime]::UtcNow -lt $deadline;
        $reobservationAttempt++) {
        Start-Sleep -Milliseconds $ObservationIntervalMilliseconds
        $freshSecondarySnapshot = New-J6SecondaryProcessObservationSnapshot `
            -MaximumAttempts 1
        if ($freshSecondarySnapshot.CimState -ne 'AVAILABLE' -or
            $freshSecondarySnapshot.TasklistState -ne 'AVAILABLE') {
            throw [InvalidOperationException]::new(
                'J6_PROCESS_IDENTITY_ABSENCE_UNCONFIRMED_' +
                'SECONDARY_VIEW_UNAVAILABLE')
        }
        $observation = Get-J6ProcessIdentityObservation `
            -Identity $Identity `
            -SecondarySnapshot $freshSecondarySnapshot
        $observationCount++
        & $writeObservation $observation
        if ($observation.Classification -in @(
                'ABSENT_ALL_VIEWS',
                'PID_REUSED_NOT_OWNED')) {
            if (-not $Quiet) {
                Write-Host (
                    'J6_PROCESS_GHOST_STATE_TRANSIENT_RECOVERY=' +
                    "PASS_AFTER_$observationCount" + '_OBSERVATIONS')
            }
            return $observation
        }
        if ($observation.Classification -eq 'ACTIVE_EXACT') {
            throw [InvalidOperationException]::new(
                'J6_PROCESS_IDENTITY_STILL_ACTIVE')
        }
        $secondaryStates = @(
            $observation.Toolhelp,
            $observation.Cim,
            $observation.Tasklist)
        if ($observation.Classification -ne
                'AMBIGUOUS_CROSS_API_GHOST_VISIBILITY' -or
            $observation.DotNet -ne 'ABSENT' -or
            $secondaryStates -notcontains 'VISIBLE' -or
            $secondaryStates -contains 'ERROR') {
            throw [InvalidOperationException]::new(
                'J6_PROCESS_IDENTITY_ABSENCE_UNCONFIRMED_' +
                [string]$observation.Classification)
        }
    }

    throw [InvalidOperationException]::new(
        'J6_PROCESS_IDENTITY_ABSENCE_UNCONFIRMED_' +
        [string]$observation.Classification)
}

function Assert-J6ProcessObservationClassifier {
    $active = Resolve-J6ProcessIdentityObservation `
        -DotNet EXACT_ACTIVE `
        -Toolhelp VISIBLE `
        -Cim VISIBLE `
        -Tasklist VISIBLE
    Assert-J6Qualification ($active -eq 'ACTIVE_EXACT') `
        'The multi-API process classifier did not recognize exact activity.'
    $activeContradiction = Resolve-J6ProcessIdentityObservation `
        -DotNet EXACT_ACTIVE `
        -Toolhelp ABSENT `
        -Cim VISIBLE `
        -Tasklist VISIBLE
    Assert-J6Qualification ($activeContradiction -eq 'UNVERIFIABLE') `
        'The multi-API process classifier accepted a contradictory active view.'
    $absent = Resolve-J6ProcessIdentityObservation `
        -DotNet ABSENT `
        -Toolhelp ABSENT `
        -Cim ABSENT `
        -Tasklist ABSENT
    Assert-J6Qualification ($absent -eq 'ABSENT_ALL_VIEWS') `
        'The multi-API process classifier did not recognize unanimous absence.'
    $reused = Resolve-J6ProcessIdentityObservation `
        -DotNet PID_REUSED `
        -Toolhelp VISIBLE `
        -Cim VISIBLE `
        -Tasklist VISIBLE
    Assert-J6Qualification ($reused -eq 'PID_REUSED_NOT_OWNED') `
        'The multi-API process classifier treated PID reuse as owned activity.'
    $ghost = Resolve-J6ProcessIdentityObservation `
        -DotNet UNOPENABLE `
        -Toolhelp VISIBLE `
        -Cim VISIBLE `
        -Tasklist VISIBLE
    Assert-J6Qualification (
        $ghost -eq 'AMBIGUOUS_CROSS_API_GHOST_VISIBILITY') `
        'The multi-API process classifier treated ghost visibility as owned activity or absence.'
    $secondaryError = Resolve-J6ProcessIdentityObservation `
        -DotNet ABSENT `
        -Toolhelp ABSENT `
        -Cim ERROR `
        -Tasklist ABSENT
    Assert-J6Qualification ($secondaryError -eq 'UNVERIFIABLE') `
        'The multi-API process classifier ignored a required observation error.'
    Write-Host 'J6_PROCESS_MULTI_API_CLASSIFIER=PASS'
    Write-Host 'J6_PROCESS_GHOST_STATE_CLASSIFICATION=AMBIGUOUS_CROSS_API_GHOST_VISIBILITY'
}

function New-J6QualificationSanitizedException {
    param(
        [Parameter(Mandatory = $true)][string]$Classification,
        [AllowNull()][Exception]$InnerException
    )

    $sanitizedInner = $null
    if ($null -ne $InnerException) {
        $causeCategory = if ($InnerException -is [UnauthorizedAccessException]) {
            'ACCESS_DENIED'
        }
        elseif ($InnerException -is [IO.IOException]) {
            'IO_FAILURE'
        }
        elseif ($InnerException -is [InvalidOperationException]) {
            'INVALID_OPERATION'
        }
        else {
            'OTHER_FAILURE'
        }
        $sanitizedInner = [InvalidOperationException]::new(
            "J6_QUALIFICATION_SANITIZED_INNER_CAUSE=$causeCategory")
        $sanitizedInner.Data['J6SanitizedCauseCategory'] = $causeCategory
    }
    $message = "J6_QUALIFICATION_FAILURE=$Classification"
    $failure = if ($null -eq $sanitizedInner) {
        [InvalidOperationException]::new($message)
    }
    else {
        [InvalidOperationException]::new($message, $sanitizedInner)
    }
    $failure.Data['J6QualificationClassification'] = $Classification
    return $failure
}

function New-J6CombinedQualificationTempCleanupFailure {
    param(
        [Parameter(Mandatory = $true)][Exception]$PrimaryFailure,
        [Parameter(Mandatory = $true)][Exception]$TempCleanupFailure
    )

    $sanitizedPrimary = New-J6QualificationSanitizedException `
        -Classification 'PRIMARY_QUALIFICATION_FAILED_DURING_TEMP_CLEANUP' `
        -InnerException $PrimaryFailure
    $sanitizedCleanup = if (
        $TempCleanupFailure.Data['J6QualificationClassification'] -eq
            'TEMP_ROOT_CLEANUP_FAILED') {
        $TempCleanupFailure
    }
    else {
        New-J6QualificationSanitizedException `
            -Classification 'TEMP_ROOT_CLEANUP_FAILED' `
            -InnerException $TempCleanupFailure
    }
    $aggregate = [AggregateException]::new(
        'J6_QUALIFICATION_TEMP_CLEANUP_CAUSES_PRESERVED',
        [Exception[]]@($sanitizedPrimary, $sanitizedCleanup))
    $combined = [InvalidOperationException]::new(
        ('J6_QUALIFICATION_FAIL_CLOSED=FAILED;' +
            'J6_QUALIFICATION_FAILURE_CLASSES=' +
            'PRIMARY_QUALIFICATION_FAILED_DURING_TEMP_CLEANUP,' +
            'TEMP_ROOT_CLEANUP_FAILED'),
        $aggregate)
    $combined.Data['J6QualificationClassification'] =
        'PRIMARY_AND_TEMP_ROOT_CLEANUP_FAILED'
    return $combined
}

function New-J6OwnedQualificationTempRoot {
    $canonicalParent = [IO.Path]::GetFullPath(
        [IO.Path]::GetTempPath()).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar)
    $ownerNonce = [Guid]::NewGuid().ToString('N')
    $leafName = 'j6-native-pipeline-' + $ownerNonce
    $candidate = [IO.Path]::GetFullPath(
        [IO.Path]::Combine($canonicalParent, $leafName))
    $candidateParent = [IO.Path]::GetFullPath(
        [IO.Path]::GetDirectoryName($candidate)).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar)
    if (-not $candidateParent.Equals(
            $canonicalParent,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_CANONICAL_CONFINEMENT_FAILED'
    }

    if (Test-Path -LiteralPath $candidate) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_ALREADY_EXISTS'
    }

    $directoryCreated = $false
    try {
        $directory = [IO.Directory]::CreateDirectory($candidate)
        $directoryCreated = $true
        if (($directory.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'J6_SYNTHETIC_TEMP_ROOT_REPARSE_POINT_REJECTED'
        }
        $directory = $null

        $markerPath = Join-Path $candidate '.j6-qualification-owner.json'
        $marker = [ordered]@{
            protocol = 'J6_SYNTHETIC_TEMP_ROOT_OWNER_V1'
            ownerNonce = $ownerNonce
            canonicalPath = $candidate
        }
        $markerJson = $marker | ConvertTo-Json -Compress
        $markerStream = $null
        try {
            $markerStream = [IO.FileStream]::new(
                $markerPath,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None)
            $markerBytes = [Text.UTF8Encoding]::new($false).GetBytes($markerJson)
            $markerStream.Write($markerBytes, 0, $markerBytes.Length)
            $markerStream.Flush($true)
        }
        finally {
            if ($null -ne $markerStream) {
                $markerStream.Dispose()
            }
        }

        return [pscustomobject]@{
            CanonicalParent = $canonicalParent
            CanonicalPath = $candidate
            LeafName = $leafName
            OwnerNonce = $ownerNonce
            MarkerPath = $markerPath
        }
    }
    catch {
        $creationFailure = $_.Exception
        $creationCleanupFailure = $null
        if ($directoryCreated -and
            (Test-Path -LiteralPath $candidate -PathType Container)) {
            try {
                $candidateItem = Get-Item -LiteralPath $candidate -Force
                if (($candidateItem.Attributes -band
                        [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    throw 'J6_SYNTHETIC_TEMP_ROOT_CREATION_CLEANUP_REPARSE_REJECTED'
                }
                $children = @(Get-ChildItem -LiteralPath $candidate -Force)
                foreach ($child in $children) {
                    if ($child.Name -cne '.j6-qualification-owner.json' -or
                        $child.PSIsContainer -or
                        ($child.Attributes -band
                            [IO.FileAttributes]::ReparsePoint) -ne 0) {
                        throw 'J6_SYNTHETIC_TEMP_ROOT_CREATION_CLEANUP_OWNERSHIP_UNCONFIRMED'
                    }
                }
                foreach ($child in $children) {
                    Remove-Item -LiteralPath $child.FullName -Force
                }
                [IO.Directory]::Delete($candidate, $false)
                if (Test-Path -LiteralPath $candidate) {
                    throw 'J6_SYNTHETIC_TEMP_ROOT_CREATION_CLEANUP_ABSENCE_UNCONFIRMED'
                }
            }
            catch {
                $creationCleanupFailure = $_.Exception
            }
        }
        if ($null -ne $creationCleanupFailure) {
            throw [InvalidOperationException]::new(
                'J6_SYNTHETIC_TEMP_ROOT_CREATION_CLEANUP_FAILED',
                [AggregateException]::new(
                    'J6_SYNTHETIC_TEMP_ROOT_CREATION_CAUSES_PRESERVED',
                    [Exception[]]@($creationFailure, $creationCleanupFailure)))
        }
        throw $creationFailure
    }
}

function Remove-J6OwnedQualificationTempRoot {
    param(
        [Parameter(Mandatory = $true)]$Ownership,
        [Parameter(DontShow = $true)]
        [switch]$QualificationInjectDeleteFailure
    )

    if ($QualificationInjectDeleteFailure -and
        [Environment]::GetEnvironmentVariable(
            'J6_WO025_LOOPBACK_FAULT_INJECTION',
            [EnvironmentVariableTarget]::Process) -ne 'AUTHORIZED') {
        throw 'J6_SYNTHETIC_TEMP_ROOT_DELETE_FAULT_INJECTION_NOT_AUTHORIZED'
    }

    $canonicalPath = [IO.Path]::GetFullPath([string]$Ownership.CanonicalPath)
    $canonicalParent = [IO.Path]::GetFullPath(
        [IO.Path]::GetDirectoryName($canonicalPath)).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar)
    $expectedParent = [IO.Path]::GetFullPath(
        [IO.Path]::GetTempPath()).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar)
    $declaredParent = [IO.Path]::GetFullPath(
        [string]$Ownership.CanonicalParent).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar)
    $actualLeafName = [IO.Path]::GetFileName($canonicalPath)
    if (-not $canonicalParent.Equals(
            $expectedParent,
            [StringComparison]::OrdinalIgnoreCase) -or
        -not $declaredParent.Equals(
            $expectedParent,
            [StringComparison]::OrdinalIgnoreCase) -or
        $actualLeafName -cnotmatch '^j6-native-pipeline-[0-9a-f]{32}$' -or
        $actualLeafName -cne ('j6-native-pipeline-' + [string]$Ownership.OwnerNonce) -or
        -not $actualLeafName.Equals(
            [string]$Ownership.LeafName,
            [StringComparison]::Ordinal)) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_CANONICAL_CONFINEMENT_FAILED'
    }
    if (-not (Test-Path -LiteralPath $canonicalPath -PathType Container)) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_OWNED_DIRECTORY_MISSING'
    }

    $rootItem = Get-Item -LiteralPath $canonicalPath -Force
    if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_REPARSE_POINT_REJECTED'
    }
    $markerPath = Join-Path $canonicalPath '.j6-qualification-owner.json'
    if (-not $markerPath.Equals(
            [string]$Ownership.MarkerPath,
            [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_OWNERSHIP_MARKER_MISSING'
    }
    $markerItem = Get-Item -LiteralPath $markerPath -Force
    if (($markerItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_OWNERSHIP_MARKER_INVALID'
    }
    try {
        $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    }
    catch {
        throw (New-J6QualificationSanitizedException `
                -Classification 'TEMP_ROOT_OWNERSHIP_MARKER_INVALID' `
                -InnerException $_.Exception)
    }
    if ($marker.protocol -cne 'J6_SYNTHETIC_TEMP_ROOT_OWNER_V1' -or
        $marker.ownerNonce -cne [string]$Ownership.OwnerNonce -or
        -not ([string]$marker.canonicalPath).Equals(
            $canonicalPath,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_OWNERSHIP_MARKER_INVALID'
    }

    $directoriesToInspect = [Collections.Generic.Stack[string]]::new()
    $directoriesToInspect.Push($canonicalPath)
    while ($directoriesToInspect.Count -ne 0) {
        $directoryToInspect = $directoriesToInspect.Pop()
        foreach ($child in @(Get-ChildItem `
                -LiteralPath $directoryToInspect `
                -Force)) {
            if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'J6_SYNTHETIC_TEMP_ROOT_DESCENDANT_REPARSE_POINT_REJECTED'
            }
            if ($child.PSIsContainer) {
                $directoriesToInspect.Push($child.FullName)
            }
        }
    }

    if ($QualificationInjectDeleteFailure) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_DELETE_FAILURE_INJECTED'
    }

    try {
        Remove-Item -LiteralPath $canonicalPath -Recurse -Force
    }
    catch {
        throw (New-J6QualificationSanitizedException `
                -Classification 'TEMP_ROOT_CLEANUP_FAILED' `
                -InnerException $_.Exception)
    }
    if (Test-Path -LiteralPath $canonicalPath) {
        throw 'J6_SYNTHETIC_TEMP_ROOT_POST_DELETE_ABSENCE_UNCONFIRMED'
    }
    Write-Host 'J6_SYNTHETIC_TEMP_ROOT_CLEANUP=PASS'
}

function ConvertTo-J6SingleQuotedLiteral {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function New-J6SyntheticAgeExecutable {
    param([Parameter(Mandatory = $true)][string]$DestinationPath)

    $source = @'
using System;
using System.IO;

public static class J6SyntheticAge
{
    private static void Transform(Stream input, Stream output, bool stopEarly)
    {
        byte[] buffer = new byte[8192];
        while (true)
        {
            int read = input.Read(buffer, 0, buffer.Length);
            if (read == 0)
            {
                break;
            }
            for (int index = 0; index < read; index++)
            {
                buffer[index] ^= 0xA5;
            }
            output.Write(buffer, 0, read);
            output.Flush();
            if (stopEarly)
            {
                break;
            }
        }
    }

    public static int Main(string[] args)
    {
        string mode = Environment.GetEnvironmentVariable("J6_SYNTHETIC_AGE_MODE") ?? "NOMINAL";
        if (args.Length == 3 && args[0] == "-p" && args[1] == "-o")
        {
            if (mode != "FAIL_ENCRYPT")
            {
                string marker = Environment.GetEnvironmentVariable(
                    "J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER");
                if (!String.IsNullOrWhiteSpace(marker))
                {
                    File.WriteAllText(
                        marker,
                        "PASSPHRASE_INVOCATION_PATH=SIMULATED_NO_SECRET");
                }
            }
            using (Stream input = Console.OpenStandardInput())
            using (FileStream output = new FileStream(
                args[2], FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                Transform(input, output, mode == "FAIL_ENCRYPT");
            }
            return mode == "FAIL_ENCRYPT" ? 17 : 0;
        }
        if (args.Length == 2 && args[0] == "-d")
        {
            if (mode == "FAIL_DECRYPT")
            {
                return 18;
            }
            using (FileStream input = File.OpenRead(args[1]))
            using (Stream output = Console.OpenStandardOutput())
            {
                Transform(input, output, false);
            }
            return 0;
        }
        return 64;
    }
}
'@
    $compilerPath = Join-Path $env:WINDIR `
        'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path -LiteralPath $compilerPath -PathType Leaf)) {
        $compilerPath = Join-Path $env:WINDIR `
            'Microsoft.NET\Framework\v4.0.30319\csc.exe'
    }
    Assert-J6Qualification (Test-Path -LiteralPath $compilerPath -PathType Leaf) `
        'The local Windows C# compiler is required for the loopback double.'
    $sourcePath = [IO.Path]::ChangeExtension($DestinationPath, '.cs')
    [IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
    & $compilerPath /nologo /target:exe "/out:$DestinationPath" $sourcePath
    Assert-J6Qualification ($LASTEXITCODE -eq 0) `
        'The synthetic age executable compilation failed.'
    Assert-J6Qualification (Test-Path -LiteralPath $DestinationPath -PathType Leaf) `
        'The synthetic age executable was not created.'
}

function Write-J6CtrlBreakNativeEvidence {
    [CmdletBinding()]
    param([Parameter(ValueFromPipeline)][AllowEmptyCollection()][AllowNull()][object[]]$Lines)
    begin {
    # Exact finite vocabulary: never forward arbitrary native output.
    $allowed = @(
        'J6_CTRL_BREAK_NATIVE_PHASE=PROCESS_CREATE',
        'J6_CTRL_BREAK_NATIVE_PHASE=READINESS_WAIT',
        'J6_CTRL_BREAK_NATIVE_PHASE=SIGNAL_SEND',
        'J6_CTRL_BREAK_NATIVE_PHASE=CHILD_WAIT',
        'J6_CTRL_BREAK_NATIVE_PHASE=CLEANUP',
        'J6_CTRL_BREAK_NATIVE_CLEANUP=PASS',
        'J6_CTRL_BREAK_NATIVE_CLEANUP=FAIL',
        'J6_CTRL_BREAK_NATIVE_SIGNAL=SENT',
        'J6_CTRL_BREAK_NATIVE_FAILURE=ARGUMENT_COUNT',
        'J6_CTRL_BREAK_NATIVE_FAILURE=PROCESS_CREATE',
        'J6_CTRL_BREAK_NATIVE_FAILURE=HANDLER_INSTALL',
        'J6_CTRL_BREAK_NATIVE_FAILURE=EARLY_EXIT',
        'J6_CTRL_BREAK_NATIVE_FAILURE=READINESS_TIMEOUT',
        'J6_CTRL_BREAK_NATIVE_FAILURE=SIGNAL_SEND',
        'J6_CTRL_BREAK_NATIVE_FAILURE=CHILD_TIMEOUT',
        'J6_CTRL_BREAK_NATIVE_FAILURE=CHILD_WAIT',
        'J6_CTRL_BREAK_NATIVE_FAILURE=EXIT_CODE_READ'
    )
    }
    process {
    foreach ($line in $Lines) {
        if ($line -is [string] -and $line -cin $allowed) { Write-Host $line }
    }
    }
}

function New-J6CtrlBreakLauncherExecutable {
    param([Parameter(Mandatory = $true)][string]$DestinationPath)

    $source = @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class J6CtrlBreakLauncher
{
    private const uint CREATE_NEW_PROCESS_GROUP = 0x00000200;
    private const uint CTRL_BREAK_EVENT = 1;
    private const uint WAIT_OBJECT_0 = 0;
    private const uint WAIT_TIMEOUT = 258;
    private const uint STILL_ACTIVE = 259;
    private delegate bool ConsoleCtrlHandler(uint ctrlType);
    private static readonly ConsoleCtrlHandler IgnoreLauncherControlEvent =
        delegate(uint ctrlType) { return true; };

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO
    {
        public uint cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public uint dwX;
        public uint dwY;
        public uint dwXSize;
        public uint dwYSize;
        public uint dwXCountChars;
        public uint dwYCountChars;
        public uint dwFillAttribute;
        public uint dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION
    {
        public IntPtr hProcess;
        public IntPtr hThread;
        public uint dwProcessId;
        public uint dwThreadId;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateProcessW(
        string applicationName,
        StringBuilder commandLine,
        IntPtr processAttributes,
        IntPtr threadAttributes,
        bool inheritHandles,
        uint creationFlags,
        IntPtr environment,
        string currentDirectory,
        ref STARTUPINFO startupInfo,
        out PROCESS_INFORMATION processInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GenerateConsoleCtrlEvent(uint ctrlEvent, uint processGroupId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetConsoleCtrlHandler(ConsoleCtrlHandler handlerRoutine, bool add);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetExitCodeProcess(IntPtr process, out uint exitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool TerminateProcess(IntPtr process, uint exitCode);

    [DllImport("kernel32.dll")]
    private static extern bool CloseHandle(IntPtr handle);

    private static string Quote(string value)
    {
        if (value.Length > 0 && value.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) < 0)
        {
            return value;
        }
        var quoted = new StringBuilder("\"");
        int backslashes = 0;
        foreach (char current in value)
        {
            if (current == '\\')
            {
                backslashes++;
            }
            else if (current == '"')
            {
                quoted.Append('\\', backslashes * 2 + 1);
                quoted.Append('"');
                backslashes = 0;
            }
            else
            {
                quoted.Append('\\', backslashes);
                quoted.Append(current);
                backslashes = 0;
            }
        }
        quoted.Append('\\', backslashes * 2);
        quoted.Append('"');
        return quoted.ToString();
    }

    private static void WriteDiagnostic(string marker)
    {
        // Observation must never prevent exact-handle cleanup or replace its error.
        try { Console.WriteLine(marker); }
        catch (IOException) { }
        catch (ObjectDisposedException) { }
        catch (InvalidOperationException) { }
    }

    public static int Main(string[] args)
    {
        if (args.Length != 8)
        {
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=ARGUMENT_COUNT");
            return 64;
        }
        string commandLine = Quote(args[0]) +
            " -NoLogo -NoProfile -NonInteractive -File " + Quote(args[1]) +
            " -ModulePath " + Quote(args[2]) +
            " -ReadyPath " + Quote(args[3]) +
            " -ResultPath " + Quote(args[4]) +
            " -ProducerIdentityPath " + Quote(args[6]) +
            " -ConsumerIdentityPath " + Quote(args[7]);
        var startup = new STARTUPINFO
        {
            cb = (uint)Marshal.SizeOf(typeof(STARTUPINFO))
        };
        PROCESS_INFORMATION process;
        WriteDiagnostic("J6_CTRL_BREAK_NATIVE_PHASE=PROCESS_CREATE");
        if (!CreateProcessW(
                args[0],
                new StringBuilder(commandLine),
                IntPtr.Zero,
                IntPtr.Zero,
                false,
                CREATE_NEW_PROCESS_GROUP,
                IntPtr.Zero,
                Directory.GetCurrentDirectory(),
                ref startup,
                out process))
        {
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=PROCESS_CREATE");
            return 66;
        }
        bool handlerInstalled = false;
        try
        {
            if (!SetConsoleCtrlHandler(IgnoreLauncherControlEvent, true))
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=HANDLER_INSTALL");
                return 75;
            }
            handlerInstalled = true;
            File.WriteAllText(args[5], process.dwProcessId.ToString());
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_PHASE=READINESS_WAIT");
            DateTime readyDeadline = DateTime.UtcNow.AddSeconds(10);
            while (!File.Exists(args[3]) && DateTime.UtcNow < readyDeadline)
            {
                uint earlyCode;
                if (GetExitCodeProcess(process.hProcess, out earlyCode) && earlyCode != STILL_ACTIVE)
                {
                    WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=EARLY_EXIT");
                    return 67;
                }
                Thread.Sleep(25);
            }
            if (!File.Exists(args[3]))
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=READINESS_TIMEOUT");
                return 68;
            }
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_PHASE=SIGNAL_SEND");
            if (!GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, process.dwProcessId))
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=SIGNAL_SEND");
                return 69;
            }
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_PHASE=CHILD_WAIT");
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_SIGNAL=SENT");
            uint wait = WaitForSingleObject(process.hProcess, 10000);
            if (wait == WAIT_TIMEOUT)
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=CHILD_TIMEOUT");
                return 70;
            }
            if (wait != WAIT_OBJECT_0)
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=CHILD_WAIT");
                return 71;
            }
            uint exitCode;
            if (!GetExitCodeProcess(process.hProcess, out exitCode))
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_FAILURE=EXIT_CODE_READ");
                return 72;
            }
            File.WriteAllText(
                args[5] + ".exact-exit",
                process.dwProcessId.ToString() + ":" + exitCode.ToString());
            return unchecked((int)exitCode);
        }
        finally
        {
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_PHASE=CLEANUP");
            bool cleanupVerified = true;
            uint exitCode;
            if (!GetExitCodeProcess(process.hProcess, out exitCode))
            {
                cleanupVerified = false;
            }
            else if (exitCode == STILL_ACTIVE)
            {
                TerminateProcess(process.hProcess, 73);
                uint terminationWait = WaitForSingleObject(process.hProcess, 3000);
                uint terminatedCode;
                cleanupVerified = terminationWait == WAIT_OBJECT_0 &&
                    GetExitCodeProcess(process.hProcess, out terminatedCode) &&
                    terminatedCode != STILL_ACTIVE;
            }
            if (handlerInstalled)
            {
                SetConsoleCtrlHandler(IgnoreLauncherControlEvent, false);
            }
            bool threadClosed = CloseHandle(process.hThread);
            bool processClosed = CloseHandle(process.hProcess);
            if (!cleanupVerified || !threadClosed || !processClosed)
            {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_CLEANUP=FAIL");
                throw new InvalidOperationException(
                    "The exact CTRL_BREAK harness cleanup could not be confirmed.");
            }
            WriteDiagnostic("J6_CTRL_BREAK_NATIVE_CLEANUP=PASS");
        }
    }
}
'@
    $compilerPath = Join-Path $env:WINDIR `
        'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path -LiteralPath $compilerPath -PathType Leaf)) {
        $compilerPath = Join-Path $env:WINDIR `
            'Microsoft.NET\Framework\v4.0.30319\csc.exe'
    }
    Assert-J6Qualification (Test-Path -LiteralPath $compilerPath -PathType Leaf) `
        'The local Windows C# compiler is required for CTRL_BREAK qualification.' -FailureMarker 'CTRL_BREAK_COMPILATION_GATE_1'
    $sourcePath = [IO.Path]::ChangeExtension($DestinationPath, '.cs')
    [IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
    $script:j6CtrlBreakStage = 'COMPILATION_EXECUTION'
    $compilerOutput = @(& $compilerPath /nologo /target:exe "/out:$DestinationPath" $sourcePath 2>&1)
    $compilerExit = $LASTEXITCODE
    Write-Host ("J6_CTRL_BREAK_COMPILER_EXIT=" + [int]$compilerExit)
    Assert-J6Qualification ($compilerExit -eq 0) `
        'The CTRL_BREAK launcher compilation failed.' -FailureMarker 'CTRL_BREAK_COMPILATION_GATE_2'
    Assert-J6Qualification (Test-Path -LiteralPath $DestinationPath -PathType Leaf) `
        'The CTRL_BREAK launcher was not created.' -FailureMarker 'CTRL_BREAK_COMPILATION_GATE_3'
}

function Invoke-J6QualificationDockerScalar {
    param(
        [Parameter(Mandatory = $true)][string]$DockerExecutable,
        [Parameter(Mandatory = $true)][string]$Sql
    )
    $result = Invoke-J6BoundedNativeCommand `
        -FilePath $DockerExecutable `
        -ArgumentList @(
            'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
            'sh', '-c',
            'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-align --tuples-only --quiet --set=ON_ERROR_STOP=1 --command "$1"',
            'sh', $Sql) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutMilliseconds 30000 `
        -CleanupTimeoutMilliseconds 5000
    if ($result.ExitCode -ne 0 -or
        $result.ProcessTreeCleanup -ne 'PASS' -or
        $result.UnexpectedDescendantCleanup) {
        throw (
            'A Docker loopback qualification query failed. ' +
            "ExitCode=$($result.ExitCode); " +
            "ProcessTreeCleanup=$($result.ProcessTreeCleanup); " +
            "UnexpectedDescendantCleanup=$($result.UnexpectedDescendantCleanup)")
    }
    return [string]$result.StandardOutput
}

function Assert-J6BackupRuntimeEffectiveDefaults {
    param([Parameter(Mandatory = $true)][string]$CapturedText)

    Assert-J6Qualification (
        $CapturedText.Contains(
            'J6_NATIVE_PROCESS_CLEANUP_TIMEOUT_MILLISECONDS=5000') -and
        $CapturedText.Contains(
            'J6_POSTGRES_CLEANUP_TIMEOUT_MILLISECONDS=10000')) `
        'A backup/restore loopback path did not exercise the effective 5000/10000 defaults.'
}

$pwshPath = (Get-Process -Id $PID).Path
Assert-J6QualificationCanonicalCountParser
Assert-J6RuntimePostgresCleanupContracts
Assert-J6ProcessObservationClassifier
$qualificationRootOwnership = New-J6OwnedQualificationTempRoot
$resolvedQualificationRoot = [string]$qualificationRootOwnership.CanonicalPath

$commonArguments = @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command')
$ownedProcesses = [Collections.Generic.List[object]]::new()
$qualificationFailure = $null
$tempRootCleanupFailure = $null

try {
    $outsideRootRejected = $false
    $outsideNonce = [Guid]::NewGuid().ToString('N')
    $outsideLeaf = 'j6-native-pipeline-' + $outsideNonce
    $outsideOwnership = [pscustomobject]@{
        CanonicalParent = [IO.Path]::GetTempPath()
        CanonicalPath = Join-Path $resolvedQualificationRoot $outsideLeaf
        LeafName = $outsideLeaf
        OwnerNonce = $outsideNonce
        MarkerPath = Join-Path `
            (Join-Path $resolvedQualificationRoot $outsideLeaf) `
            '.j6-qualification-owner.json'
    }
    try {
        Remove-J6OwnedQualificationTempRoot -Ownership $outsideOwnership
    }
    catch {
        $outsideRootRejected = $_.Exception.Message -eq `
            'J6_SYNTHETIC_TEMP_ROOT_CANONICAL_CONFINEMENT_FAILED'
    }
    Assert-J6Qualification $outsideRootRejected `
        'The exact temp cleanup accepted a path outside the operating-system temp root.'

    $wrongOwnerRejected = $false
    $wrongOwner = [pscustomobject]@{
        CanonicalParent = $qualificationRootOwnership.CanonicalParent
        CanonicalPath = $qualificationRootOwnership.CanonicalPath
        LeafName = $qualificationRootOwnership.LeafName
        OwnerNonce = '00000000000000000000000000000000'
        MarkerPath = $qualificationRootOwnership.MarkerPath
    }
    try {
        Remove-J6OwnedQualificationTempRoot -Ownership $wrongOwner
    }
    catch {
        $wrongOwnerRejected = $_.Exception.Message -eq `
            'J6_SYNTHETIC_TEMP_ROOT_CANONICAL_CONFINEMENT_FAILED'
    }
    Assert-J6Qualification $wrongOwnerRejected `
        'The exact temp cleanup accepted a mismatched ownership nonce.'

    $tempParentRejected = $false
    $tempParentOwnership = [pscustomobject]@{
        CanonicalParent = [IO.Path]::GetDirectoryName(
            $qualificationRootOwnership.CanonicalParent)
        CanonicalPath = $qualificationRootOwnership.CanonicalParent
        LeafName = [IO.Path]::GetFileName(
            $qualificationRootOwnership.CanonicalParent)
        OwnerNonce = $qualificationRootOwnership.OwnerNonce
        MarkerPath = Join-Path `
            $qualificationRootOwnership.CanonicalParent `
            '.j6-qualification-owner.json'
    }
    try {
        Remove-J6OwnedQualificationTempRoot -Ownership $tempParentOwnership
    }
    catch {
        $tempParentRejected = $_.Exception.Message -eq
            'J6_SYNTHETIC_TEMP_ROOT_CANONICAL_CONFINEMENT_FAILED'
    }
    Assert-J6Qualification $tempParentRejected `
        'The exact temp cleanup accepted the operating-system temp parent itself.'

    $globPathRejected = $false
    $globNonce = [Guid]::NewGuid().ToString('N')
    $globLeaf = 'j6-native-pipeline-' + $globNonce + '*'
    $globOwnership = [pscustomobject]@{
        CanonicalParent = $qualificationRootOwnership.CanonicalParent
        CanonicalPath = Join-Path `
            $qualificationRootOwnership.CanonicalParent `
            $globLeaf
        LeafName = $globLeaf
        OwnerNonce = $globNonce
        MarkerPath = Join-Path `
            (Join-Path `
                $qualificationRootOwnership.CanonicalParent `
                $globLeaf) `
            '.j6-qualification-owner.json'
    }
    try {
        Remove-J6OwnedQualificationTempRoot -Ownership $globOwnership
    }
    catch {
        $globPathRejected = $_.Exception.Message -eq
            'J6_SYNTHETIC_TEMP_ROOT_CANONICAL_CONFINEMENT_FAILED'
    }
    Assert-J6Qualification $globPathRejected `
        'The exact temp cleanup accepted a wildcard-bearing path.'

    $combinedFailure = New-J6CombinedQualificationTempCleanupFailure `
        -PrimaryFailure ([InvalidOperationException]::new(
                'SYNTHETIC_PRIMARY_DETAIL_MUST_NOT_ESCAPE')) `
        -TempCleanupFailure ([IO.IOException]::new(
                'SYNTHETIC_TEMP_DETAIL_MUST_NOT_ESCAPE'))
    Assert-J6Qualification (
        $combinedFailure.InnerException -is [AggregateException] -and
        $combinedFailure.InnerException.InnerExceptions.Count -eq 2 -and
        -not $combinedFailure.ToString().Contains(
            'SYNTHETIC_PRIMARY_DETAIL_MUST_NOT_ESCAPE') -and
        -not $combinedFailure.ToString().Contains(
            'SYNTHETIC_TEMP_DETAIL_MUST_NOT_ESCAPE')) `
        'The combined qualification/temp cleanup failure did not preserve sanitized causes.'

    $reparseTargetPath = Join-Path `
        $resolvedQualificationRoot `
        'reparse-target'
    $reparseJunctionPath = Join-Path `
        $resolvedQualificationRoot `
        'reparse-junction'
    $reparseRejected = $false
    try {
        [void][IO.Directory]::CreateDirectory($reparseTargetPath)
        [void](New-Item `
                -ItemType Junction `
                -Path $reparseJunctionPath `
                -Target $reparseTargetPath)
        try {
            Remove-J6OwnedQualificationTempRoot `
                -Ownership $qualificationRootOwnership
        }
        catch {
            $reparseRejected = $_.Exception.Message -eq
                'J6_SYNTHETIC_TEMP_ROOT_DESCENDANT_REPARSE_POINT_REJECTED'
        }
        Assert-J6Qualification $reparseRejected `
            'The exact temp cleanup accepted a descendant reparse point.'
    }
    finally {
        if (Test-Path -LiteralPath $reparseJunctionPath) {
            [IO.Directory]::Delete($reparseJunctionPath, $false)
        }
        if (Test-Path -LiteralPath $reparseTargetPath -PathType Container) {
            [IO.Directory]::Delete($reparseTargetPath, $false)
        }
    }
    Write-Host 'J6_SYNTHETIC_TEMP_ROOT_NEGATIVE_GATES=PASS'
    Write-Host 'J6_SYNTHETIC_TEMP_ROOT_PARENT_AND_GLOB_GATES=PASS'
    Write-Host 'J6_SYNTHETIC_TEMP_ROOT_REPARSE_GATE=PASS'
    Write-Host 'J6_SYNTHETIC_TEMP_ROOT_COMBINED_FAILURE_SANITIZED=PASS'

    $deleteFailureOwnership = $null
    $sentinelPath = Join-Path `
        ([IO.Path]::GetTempPath()) `
        ('j6-qualification-sentinel-' + [Guid]::NewGuid().ToString('N'))
    $previousDeleteFaultInjection = [Environment]::GetEnvironmentVariable(
        'J6_WO025_LOOPBACK_FAULT_INJECTION',
        [EnvironmentVariableTarget]::Process)
    try {
        [IO.File]::WriteAllText(
            $sentinelPath,
            'J6_SIBLING_SENTINEL',
            [Text.UTF8Encoding]::new($false))
        $deleteFailureOwnership = New-J6OwnedQualificationTempRoot
        [Environment]::SetEnvironmentVariable(
            'J6_WO025_LOOPBACK_FAULT_INJECTION',
            'AUTHORIZED',
            [EnvironmentVariableTarget]::Process)
        $deleteFailureObserved = $false
        try {
            Remove-J6OwnedQualificationTempRoot `
                -Ownership $deleteFailureOwnership `
                -QualificationInjectDeleteFailure
        }
        catch {
            $deleteFailureObserved = $_.Exception.Message -eq `
                'J6_SYNTHETIC_TEMP_ROOT_DELETE_FAILURE_INJECTED'
        }
        Assert-J6Qualification $deleteFailureObserved `
            'The exact temp cleanup did not fail closed on injected delete failure.'
        Assert-J6Qualification (
            Test-Path `
                -LiteralPath $deleteFailureOwnership.CanonicalPath `
                -PathType Container) `
            'The injected delete failure lost the still-owned exact temp root.'
        Remove-J6OwnedQualificationTempRoot -Ownership $deleteFailureOwnership
        $deleteFailureOwnership = $null
        Assert-J6Qualification (
            Test-Path -LiteralPath $sentinelPath -PathType Leaf) `
            'Exact temp-root cleanup removed an unrelated sibling sentinel.'
        Write-Host 'J6_SYNTHETIC_TEMP_ROOT_DELETE_FAILURE_FAIL_CLOSED=PASS'
        Write-Host 'J6_SYNTHETIC_TEMP_ROOT_SIBLING_SENTINEL_PRESERVED=PASS'
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'J6_WO025_LOOPBACK_FAULT_INJECTION',
            $previousDeleteFaultInjection,
            [EnvironmentVariableTarget]::Process)
        if ($null -ne $deleteFailureOwnership -and
            (Test-Path `
                -LiteralPath $deleteFailureOwnership.CanonicalPath `
                -PathType Container)) {
            Remove-J6OwnedQualificationTempRoot `
                -Ownership $deleteFailureOwnership
        }
        if (Test-Path -LiteralPath $sentinelPath -PathType Leaf) {
            Remove-Item -LiteralPath $sentinelPath -Force
        }
    }

    $nominalInput = Join-Path $resolvedQualificationRoot 'nominal-input.bin'
    $nominalOutput = Join-Path $resolvedQualificationRoot 'nominal-output.bin'
    $expectedBytes = [byte[]]::new(65536)
    for ($index = 0; $index -lt $expectedBytes.Length; $index++) {
        $expectedBytes[$index] = [byte]($index % 256)
    }
    [IO.File]::WriteAllBytes($nominalInput, $expectedBytes)
    $nominalProducerTemplate = @'
$sourceStream = [IO.File]::OpenRead(__INPUT_PATH__)
$targetStream = [Console]::OpenStandardOutput()
try {
    $sourceStream.CopyTo($targetStream)
    $targetStream.Flush()
}
finally {
    $sourceStream.Dispose()
}
exit 0
'@
    $nominalProducer = $nominalProducerTemplate.Replace(
        '__INPUT_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $nominalInput))
    $fileConsumerTemplate = @'
$sourceStream = [Console]::OpenStandardInput()
$targetStream = [IO.File]::Open(
    __OUTPUT_PATH__,
    [IO.FileMode]::CreateNew,
    [IO.FileAccess]::Write,
    [IO.FileShare]::None)
try {
    $sourceStream.CopyTo($targetStream)
}
finally {
    $targetStream.Dispose()
}
exit 0
'@
    $fileConsumer = $fileConsumerTemplate.Replace(
        '__OUTPUT_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $nominalOutput))
    $nominalResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($nominalProducer)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($fileConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $nominalResult

    $expectedHash = (Get-FileHash -LiteralPath $nominalInput -Algorithm SHA256).Hash.ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $nominalOutput -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host "J6_PIPELINE_BINARY_EXPECTED_SHA256=$expectedHash"
    Write-Host "J6_PIPELINE_BINARY_ACTUAL_SHA256=$actualHash"
    Write-Host "J6_PIPELINE_BINARY_ACTUAL_LENGTH=$((Get-Item -LiteralPath $nominalOutput).Length)"
    Assert-J6Qualification ($nominalResult.PipelineResult -eq 'SUCCESS') `
        "The nominal synthetic binary pipeline did not succeed: $($nominalResult | ConvertTo-Json -Compress)"
    Assert-J6Qualification ($nominalResult.CopyStatus -eq 'COMPLETED_TO_EOF') `
        'The nominal synthetic copy did not reach EOF.'
    Assert-J6Qualification ($nominalResult.ProducerExitCode -eq 0 -and
        $nominalResult.ConsumerExitCode -eq 0) `
        'The nominal native exit codes were not both zero.'
    Assert-J6Qualification ($nominalResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The nominal synthetic process cleanup was not confirmed.'
    Assert-J6Qualification ($expectedHash -ceq $actualHash) `
        'The synthetic binary payload was modified in transit.'
    Assert-J6Qualification ((Get-Item -LiteralPath $nominalOutput).Length -eq 65536) `
        'The synthetic binary payload length changed in transit.'
    Write-Host 'J6_PIPELINE_BINARY_NOMINAL=PASS'

    $childIdentityPath = Join-Path $resolvedQualificationRoot `
        'consumer-failure-child.identity.json'
    $slowProducerWithChildTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 60')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
$identity = [ordered]@{
    ProcessId = $child.Id
    StartedAtUtcTicks = $child.StartTime.ToUniversalTime().Ticks
}
[IO.File]::WriteAllText(
    __CHILD_IDENTITY_PATH__,
    ($identity | ConvertTo-Json -Compress),
    [Text.UTF8Encoding]::new($false))
$child.Dispose()
$output = [Console]::OpenStandardOutput()
$chunk = [byte[]]::new(1024)
while ($true) {
    $output.Write($chunk, 0, $chunk.Length)
    $output.Flush()
    Start-Sleep -Milliseconds 20
}
'@
    $slowProducerWithChild = $slowProducerWithChildTemplate.Replace(
        '__CHILD_IDENTITY_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $childIdentityPath))
    $earlyFailingConsumerTemplate = @'
$deadline = [DateTime]::UtcNow.AddSeconds(5)
while (-not (Test-Path -LiteralPath __CHILD_IDENTITY_PATH__ -PathType Leaf) -and
    [DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Milliseconds 25
}
if (-not (Test-Path -LiteralPath __CHILD_IDENTITY_PATH__ -PathType Leaf)) {
    exit 19
}
exit 17
'@
    $earlyFailingConsumer = $earlyFailingConsumerTemplate.Replace(
        '__CHILD_IDENTITY_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $childIdentityPath))
    $consumerFailureResult = Invoke-J6NativeBinaryPipeline `
        -Phase BACKUP_ENCRYPTION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($slowProducerWithChild)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($earlyFailingConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $consumerFailureResult
    Assert-J6Qualification ($consumerFailureResult.PipelineResult -eq 'CONSUMER_FAILED') `
        'An early non-zero consumer exit was not classified as a consumer failure.'
    Assert-J6Qualification ($consumerFailureResult.ConsumerExitCode -eq 17) `
        'The early consumer exit code was not observed independently.'
    Assert-J6Qualification ($consumerFailureResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The early consumer failure did not clean both process roots.'
    Assert-J6Qualification (
        Test-Path -LiteralPath $childIdentityPath -PathType Leaf) `
        'The synthetic producer child identity evidence was not created.'
    $childIdentity = Get-Content `
        -LiteralPath $childIdentityPath `
        -Raw | ConvertFrom-Json
    [void](Assert-J6OwnedProcessIdentityGone -Identity $childIdentity)
    $ownedProcesses.Add($childIdentity)
    Write-Host 'J6_PIPELINE_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED'

    $exitedRootChildIdentityPath = Join-Path $resolvedQualificationRoot `
        'exited-root-child.identity.json'
    $producerExitsLeavingChildTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 60')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
$identity = [ordered]@{
    ProcessId = $child.Id
    StartedAtUtcTicks = $child.StartTime.ToUniversalTime().Ticks
}
[IO.File]::WriteAllText(
    __CHILD_IDENTITY_PATH__,
    ($identity | ConvertTo-Json -Compress),
    [Text.UTF8Encoding]::new($false))
$child.Dispose()
exit 29
'@
    $producerExitsLeavingChild = $producerExitsLeavingChildTemplate.Replace(
        '__CHILD_IDENTITY_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $exitedRootChildIdentityPath))
    $rootExitConsumer = @'
$sourceStream = [Console]::OpenStandardInput()
$buffer = [byte[]]::new(1024)
while ($sourceStream.Read($buffer, 0, $buffer.Length) -gt 0) {}
Start-Sleep -Seconds 60
'@
    $rootExitResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($producerExitsLeavingChild)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($rootExitConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $rootExitResult
    Assert-J6Qualification (
        Test-Path -LiteralPath $exitedRootChildIdentityPath -PathType Leaf) `
        'The exited-root descendant identity evidence was not created.'
    $exitedRootChildIdentity = Get-Content `
        -LiteralPath $exitedRootChildIdentityPath `
        -Raw | ConvertFrom-Json
    Assert-J6Qualification ($rootExitResult.PipelineResult -eq 'TIMEOUT' -and
        $rootExitResult.ProducerExitCode -eq 29) `
        "An exited producer with a pipe-holding descendant was not rejected: $($rootExitResult | ConvertTo-Json -Compress)"
    Assert-J6Qualification ($rootExitResult.ProducerRootAliveAtCleanupStart -eq $false) `
        'The producer wrapper had not exited before descendant cleanup began.'
    Assert-J6Qualification ($rootExitResult.ProducerConfinement -eq 'WINDOWS_KILL_ON_JOB_CLOSE') `
        'The exited-root descendant was not enclosed by the Windows confinement job.'
    [void](Assert-J6OwnedProcessIdentityGone `
        -Identity $exitedRootChildIdentity)
    $ownedProcesses.Add($exitedRootChildIdentity)
    Write-Host 'J6_PIPELINE_EXITED_ROOT_DESCENDANT_CLEANUP=PASS'

    $pidReuseChildPath = Join-Path $resolvedQualificationRoot `
        'pid-reuse-child.identity.json'
    $pidReuseParentTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
$identity = [ordered]@{
    ProcessId = $child.Id
    StartedAtUtcTicks = $child.StartTime.ToUniversalTime().Ticks
}
[IO.File]::WriteAllText(
    __CHILD_IDENTITY_PATH__,
    ($identity | ConvertTo-Json -Compress),
    [Text.UTF8Encoding]::new($false))
$child.Dispose()
Start-Sleep -Seconds 30
'@
    $pidReuseParentCommand = $pidReuseParentTemplate.Replace(
        '__CHILD_IDENTITY_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $pidReuseChildPath))
    $pidReuseParentStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $pidReuseParentStartInfo.FileName = $pwshPath
    $pidReuseParentStartInfo.UseShellExecute = $false
    $pidReuseParentStartInfo.CreateNoWindow = $true
    $pidReuseParentStartInfo.WorkingDirectory = $repositoryRoot
    foreach ($argument in ($commonArguments + @($pidReuseParentCommand))) {
        [void]$pidReuseParentStartInfo.ArgumentList.Add($argument)
    }
    $pidReuseParent = [Diagnostics.Process]::new()
    $pidReuseParent.StartInfo = $pidReuseParentStartInfo
    $pidReuseChildPid = $null
    try {
        Assert-J6Qualification $pidReuseParent.Start() `
            'The PID-reuse ownership-fence parent did not start.'
        $pidReuseReadyDeadline = [DateTime]::UtcNow.AddSeconds(5)
        while (-not (Test-Path -LiteralPath $pidReuseChildPath -PathType Leaf) -and
            [DateTime]::UtcNow -lt $pidReuseReadyDeadline) {
            Start-Sleep -Milliseconds 25
        }
        Assert-J6Qualification (Test-Path -LiteralPath $pidReuseChildPath -PathType Leaf) `
            'The PID-reuse ownership-fence child did not start.'
        $pidReuseChildIdentity = Get-Content `
            -LiteralPath $pidReuseChildPath `
            -Raw | ConvertFrom-Json
        $pidReuseChildPid = [int]$pidReuseChildIdentity.ProcessId
        $reusedPidStartedAt = $pidReuseParent.StartTime.ToUniversalTime()
        $oldRootStartedAtTicks = $reusedPidStartedAt.AddSeconds(-10).Ticks
        $oldRootExitedAtTicks = $reusedPidStartedAt.AddSeconds(-5).Ticks
        $pipelineModule = Get-Module | Where-Object {
            $_.Path -eq [IO.Path]::GetFullPath($modulePath)
        } | Select-Object -First 1
        Assert-J6Qualification ($null -ne $pipelineModule) `
            'The native pipeline module scope could not be located.'
        $incorrectClaims = & $pipelineModule {
            param($ReusedProcessId, $OldStartedAtTicks, $OldExitedAtTicks)
            $claimed = [Collections.Generic.Dictionary[string, object]]::new()
            $oldIdentity = [pscustomobject]@{
                ProcessId = [int]$ReusedProcessId
                StartedAtUtcTicks = [long]$OldStartedAtTicks
                ExitedAtUtcTicks = [long]$OldExitedAtTicks
            }
            Update-J6OwnedDescendantIdentities `
                -RootIdentity $oldIdentity `
                -OwnedDescendants $claimed
            return $claimed.Count
        } $pidReuseParent.Id $oldRootStartedAtTicks $oldRootExitedAtTicks
        Assert-J6Qualification ([int]$incorrectClaims -eq 0) `
            'A descendant of a process reusing an old PID was incorrectly claimed as owned.'
    }
    finally {
        try {
            if ($pidReuseParent.Id -gt 0 -and -not $pidReuseParent.HasExited) {
                $pidReuseParent.Kill($true)
                [void]$pidReuseParent.WaitForExit(3000)
            }
        }
        catch [InvalidOperationException] {
            # The synthetic parent already exited.
        }
        $pidReuseParent.Dispose()
    }
    [void](Assert-J6OwnedProcessIdentityGone `
        -Identity $pidReuseChildIdentity)
    $ownedProcesses.Add($pidReuseChildIdentity)
    Write-Host 'J6_PIPELINE_PID_REUSE_OWNERSHIP_FENCE=PASS'

    $producerFailure = 'exit 23'
    $consumerWaitingAfterEof = @'
$sourceStream = [Console]::OpenStandardInput()
$buffer = [byte[]]::new(1024)
while ($sourceStream.Read($buffer, 0, $buffer.Length) -gt 0) {}
Start-Sleep -Seconds 30
'@
    $producerFailureResult = Invoke-J6NativeBinaryPipeline `
        -Phase RESTORE_DECRYPTION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($producerFailure)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($consumerWaitingAfterEof)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $producerFailureResult
    Assert-J6Qualification ($producerFailureResult.PipelineResult -eq 'PRODUCER_FAILED') `
        'An early non-zero producer exit was not classified as a producer failure.'
    Assert-J6Qualification ($producerFailureResult.ProducerExitCode -eq 23) `
        'The early producer exit code was not observed independently.'
    Assert-J6Qualification ($producerFailureResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The early producer failure did not clean both process roots.'
    Write-Host 'J6_PIPELINE_EARLY_PRODUCER_FAILURE=PASS_FAIL_CLOSED'
    Write-Host 'J6_PIPELINE_RESTORE_SYMMETRY=PASS'

    $slowProducer = @'
$output = [Console]::OpenStandardOutput()
$chunk = [byte[]]::new(1024)
while ($true) {
    $output.Write($chunk, 0, $chunk.Length)
    $output.Flush()
    Start-Sleep -Milliseconds 20
}
'@
    $earlySuccessfulConsumer = 'Start-Sleep -Milliseconds 200; exit 0'
    $truncationResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($slowProducer)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($earlySuccessfulConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $truncationResult
    Assert-J6Qualification ($truncationResult.PipelineResult -eq 'CONSUMER_EARLY_EXIT') `
        'A premature zero consumer exit was not rejected.'
    Assert-J6Qualification ($truncationResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The premature zero consumer exit left an owned process root.'
    Write-Host 'J6_PIPELINE_PREMATURE_SUCCESS_TRUNCATION=PASS_FAIL_CLOSED'

    $blockingProducer = 'Start-Sleep -Seconds 30'
    $blockingConsumer = @'
$sourceStream = [Console]::OpenStandardInput()
$buffer = [byte[]]::new(16)
while ($sourceStream.Read($buffer, 0, $buffer.Length) -gt 0) {}
'@
    $timeoutResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($blockingProducer)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($blockingConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 1
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $timeoutResult
    Assert-J6Qualification ($timeoutResult.PipelineResult -eq 'TIMEOUT') `
        'The bounded synthetic timeout was not classified as TIMEOUT.'
    Assert-J6Qualification ($timeoutResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The bounded synthetic timeout left an owned process root.'
    Assert-J6Qualification ($timeoutResult.DurationMilliseconds -lt 8000) `
        'The bounded synthetic timeout exceeded the cleanup envelope.'
    Write-Host 'J6_PIPELINE_TIMEOUT=PASS_FAIL_CLOSED'

    $boundedCommand = 'Start-Sleep -Seconds 30'
    $boundedCommandTimedOut = $false
    $boundedCommandIdentity = $null
    $boundedCommandStartupDurationMilliseconds = $null
    $boundedCommandJobActiveProcessesAfterCleanup = $null
    try {
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @($boundedCommand)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 1500 `
            -CleanupTimeoutMilliseconds 3000)
    }
    catch {
        $boundedCommandTimedOut = $_.Exception.Message -eq `
            'The bounded native command exceeded its deadline.'
        if ($_.Exception.Data.Contains('J6TargetProcessId') -and
            $_.Exception.Data.Contains('J6TargetStartedAtUtcTicks')) {
            $boundedCommandIdentity = [pscustomobject]@{
                ProcessId = [int]$_.Exception.Data['J6TargetProcessId']
                StartedAtUtcTicks = [long]$_.Exception.Data['J6TargetStartedAtUtcTicks']
            }
        }
        $boundedCommandStartupDurationMilliseconds = `
            $_.Exception.Data['J6StartupDurationMilliseconds']
        $boundedCommandJobActiveProcessesAfterCleanup = `
            $_.Exception.Data['J6ActiveProcessesAfterCleanup']
        Assert-J6Qualification (
            $_.Exception.Data['J6ProcessTreeCleanup'] -eq 'PASS') `
            'The bounded native command timeout did not prove process-tree cleanup.'
        Assert-J6Qualification (
            $_.Exception.Data['J6Confinement'] -eq 'WINDOWS_KILL_ON_JOB_CLOSE') `
            'The bounded native command timeout was not confined by the Windows Job Object.'
    }
    Assert-J6Qualification $boundedCommandTimedOut `
        'The bounded native command helper did not fail closed on timeout.'
    Assert-J6Qualification ($null -ne $boundedCommandIdentity) `
        'The bounded native command target handshake identity was not preserved.'
    Assert-J6Qualification (
        $null -ne $boundedCommandStartupDurationMilliseconds -and
        [long]$boundedCommandStartupDurationMilliseconds -ge 0) `
        'The bounded native command startup duration was not preserved.'
    Assert-J6Qualification (
        $null -ne $boundedCommandJobActiveProcessesAfterCleanup -and
        [int]$boundedCommandJobActiveProcessesAfterCleanup -eq 0) `
        'The bounded native command Job Object was not empty after timeout cleanup.'
    [void](Assert-J6OwnedProcessIdentityGone `
        -Identity $boundedCommandIdentity)
    $ownedProcesses.Add($boundedCommandIdentity)
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_TARGET_HANDSHAKE=PASS'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_JOB_ACTIVE_PROCESS_COUNT_AFTER_CLEANUP=0'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_TIMEOUT_CLEANUP=PASS_FAIL_CLOSED'

    $startupTimeoutObserved = $false
    $startupTimeoutCleanup = $null
    $startupTimeoutJobActiveProcesses = $null
    $previousWo025FaultInjection = [Environment]::GetEnvironmentVariable(
        'J6_WO025_LOOPBACK_FAULT_INJECTION',
        [EnvironmentVariableTarget]::Process)
    try {
        [Environment]::SetEnvironmentVariable(
            'J6_WO025_LOOPBACK_FAULT_INJECTION',
            'AUTHORIZED',
            [EnvironmentVariableTarget]::Process)
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @('Start-Sleep -Seconds 30')) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 5000 `
            -CleanupTimeoutMilliseconds 3000 `
            -StartupTimeoutMilliseconds 500 `
            -QualificationStartupHandshakeDelayMilliseconds 1500)
    }
    catch {
        $startupTimeoutObserved = $_.Exception.Message -eq `
            'The confined native target did not complete its startup handshake.'
        $startupTimeoutCleanup = $_.Exception.Data['J6ProcessTreeCleanup']
        $startupTimeoutJobActiveProcesses = `
            $_.Exception.Data['J6ActiveProcessesAfterCleanup']
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'J6_WO025_LOOPBACK_FAULT_INJECTION',
            $previousWo025FaultInjection,
            [EnvironmentVariableTarget]::Process)
    }
    Assert-J6Qualification $startupTimeoutObserved `
        'The deterministic native startup timeout was not observed.'
    Assert-J6Qualification ($startupTimeoutCleanup -eq 'PASS') `
        'The deterministic native startup timeout did not prove fail-closed cleanup.'
    Assert-J6Qualification (
        $null -ne $startupTimeoutJobActiveProcesses -and
        [int]$startupTimeoutJobActiveProcesses -eq 0) `
        'The deterministic native startup timeout left an active Job Object member.'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_STARTUP_TIMEOUT=PASS_FAIL_CLOSED'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_STARTUP_TIMEOUT_JOB_ACTIVE_PROCESS_COUNT=0'

    $postStartFailureObserved = $false
    $postStartFailureIdentity = $null
    $postStartFailureJobActiveProcesses = $null
    $previousOfflineFaultInjection = [Environment]::GetEnvironmentVariable(
        'J6_WO024_LOOPBACK_FAULT_INJECTION',
        [EnvironmentVariableTarget]::Process)
    try {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            'AUTHORIZED',
            [EnvironmentVariableTarget]::Process)
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @('Start-Sleep -Seconds 30')) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 5000 `
            -CleanupTimeoutMilliseconds 3000 `
            -QualificationInjectSupervisionFailureAfterStart)
    }
    catch {
        $postStartFailureObserved = $_.Exception.Message -eq `
            'WO-024 injected post-start native supervision failure.'
        if ($_.Exception.Data.Contains('J6TargetProcessId') -and
            $_.Exception.Data.Contains('J6TargetStartedAtUtcTicks')) {
            $postStartFailureIdentity = [pscustomobject]@{
                ProcessId = [int]$_.Exception.Data['J6TargetProcessId']
                StartedAtUtcTicks = [long]$_.Exception.Data['J6TargetStartedAtUtcTicks']
            }
        }
        $postStartFailureJobActiveProcesses = `
            $_.Exception.Data['J6ActiveProcessesAfterCleanup']
        Assert-J6Qualification (
            $_.Exception.Data['J6ProcessTreeCleanup'] -eq 'PASS') `
            'The injected post-start failure did not prove process-tree cleanup.'
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            $previousOfflineFaultInjection,
            [EnvironmentVariableTarget]::Process)
    }
    Assert-J6Qualification $postStartFailureObserved `
        'The injected post-start supervision failure was not observed.'
    Assert-J6Qualification ($null -ne $postStartFailureIdentity) `
        'The injected post-start failure did not preserve its exact target identity.'
    Assert-J6Qualification (
        $null -ne $postStartFailureJobActiveProcesses -and
        [int]$postStartFailureJobActiveProcesses -eq 0) `
        'The injected post-start failure left an active Job Object member.'
    [void](Assert-J6OwnedProcessIdentityGone `
        -Identity $postStartFailureIdentity)
    $ownedProcesses.Add($postStartFailureIdentity)
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_POST_START_EXCEPTION_CLEANUP=PASS_FAIL_CLOSED'

    $identityGapEvidencePath = Join-Path $resolvedQualificationRoot 'identity-gap-child.json'
    $identityGapRootTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
$identity = [ordered]@{
    ProcessId = $child.Id
    StartedAtUtcTicks = $child.StartTime.ToUniversalTime().Ticks
}
[IO.File]::WriteAllText(
    __EVIDENCE_PATH__,
    ($identity | ConvertTo-Json -Compress),
    [Text.UTF8Encoding]::new($false))
$child.Dispose()
exit 0
'@
    $identityGapRootCommand = $identityGapRootTemplate.Replace(
        '__EVIDENCE_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $identityGapEvidencePath))
    $identityGapFailureObserved = $false
    $previousIdentityGapFaultInjection = [Environment]::GetEnvironmentVariable(
        'J6_WO024_LOOPBACK_FAULT_INJECTION',
        [EnvironmentVariableTarget]::Process)
    $identityGapChildIdentity = $null
    try {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            'AUTHORIZED',
            [EnvironmentVariableTarget]::Process)
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @($identityGapRootCommand)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 5000 `
            -CleanupTimeoutMilliseconds 3000 `
            -QualificationInjectSupervisionFailureAfterStart `
            -QualificationSupervisionReadyPath $identityGapEvidencePath `
            -QualificationWaitForProcessExitBeforeSupervisionFailure)
    }
    catch {
        $identityGapFailureObserved = $_.Exception.Message -eq `
            'WO-024 injected post-start native supervision failure.'
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            $previousIdentityGapFaultInjection,
            [EnvironmentVariableTarget]::Process)
    }
    Assert-J6Qualification $identityGapFailureObserved `
        'The injected exited-root supervision failure was not preserved.'
    Assert-J6Qualification (Test-Path -LiteralPath $identityGapEvidencePath -PathType Leaf) `
        'The root-exit identity-gap child evidence was not created.'
    $identityGapChildIdentity = Get-Content `
        -LiteralPath $identityGapEvidencePath `
        -Raw | ConvertFrom-Json
    [void](Assert-J6OwnedProcessIdentityGone `
        -Identity $identityGapChildIdentity)
    $ownedProcesses.Add($identityGapChildIdentity)
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_EXITED_ROOT_DESCENDANT_JOB_CLEANUP=PASS'

    $cancellation = New-J6ConsoleCancellationRegistration
    try {
        $cancellation.CancelAfterForQualification(250)
        $cancelledResult = Invoke-J6NativeBinaryPipeline `
            -Phase SYNTHETIC_QUALIFICATION `
            -ProducerFilePath $pwshPath `
            -ProducerArgumentList ($commonArguments + @($blockingProducer)) `
            -ConsumerFilePath $pwshPath `
            -ConsumerArgumentList ($commonArguments + @($blockingConsumer)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutSeconds 10 `
            -CancellationToken $cancellation.Token
    }
    finally {
        $cancellation.Dispose()
    }
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $cancelledResult
    Assert-J6Qualification ($cancelledResult.PipelineResult -eq 'CANCELLED') `
        'The cooperative interruption was not classified as CANCELLED.'
    Assert-J6Qualification ($cancelledResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The cooperative interruption left an owned process root.'
    Write-Host 'J6_PIPELINE_COOPERATIVE_CANCELLATION_CLEANUP=PASS'

    & (Join-Path $PSScriptRoot 'Test-J6CtrlBreakDiagnosticContract.ps1')
    $script:j6CtrlBreakStage = 'COMPILATION_PREPARE'
    Write-Host 'J6_CTRL_BREAK_COMPILATION=START'
    $ctrlBreakLauncherPath = Join-Path $resolvedQualificationRoot 'j6-ctrl-break-launcher.exe'
    New-J6CtrlBreakLauncherExecutable -DestinationPath $ctrlBreakLauncherPath
    Write-Host 'J6_CTRL_BREAK_COMPILATION=PASS'
    $script:j6CtrlBreakStage = 'CHILD_PREPARE'
    $ctrlBreakHarnessPath = Join-Path $resolvedQualificationRoot 'j6-ctrl-break-harness.ps1'
    $ctrlBreakReadyPath = Join-Path $resolvedQualificationRoot 'ctrl-break.ready'
    $ctrlBreakResultPath = Join-Path $resolvedQualificationRoot 'ctrl-break-result.json'
    $ctrlBreakHarnessPidPath = Join-Path $resolvedQualificationRoot 'ctrl-break-harness.pid'
    $ctrlBreakExactHarnessExitPath = $ctrlBreakHarnessPidPath + '.exact-exit'
    $ctrlBreakProducerIdentityPath = Join-Path $resolvedQualificationRoot `
        'ctrl-break-producer.identity.json'
    $ctrlBreakConsumerIdentityPath = Join-Path $resolvedQualificationRoot `
        'ctrl-break-consumer.identity.json'
    $ctrlBreakHarness = @'
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ModulePath,
    [Parameter(Mandatory = $true)][string]$ReadyPath,
    [Parameter(Mandatory = $true)][string]$ResultPath,
    [Parameter(Mandatory = $true)][string]$ProducerIdentityPath,
    [Parameter(Mandatory = $true)][string]$ConsumerIdentityPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0
Import-Module -Name $ModulePath -Force
function ConvertTo-QuotedLiteral([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}
$pwshPath = (Get-Process -Id $PID).Path
$workingDirectory = Split-Path -Parent $ModulePath
$ignoreControlSource = 'using System; public static class J6IgnoreConsoleControl { private static readonly ConsoleCancelEventHandler Handler = delegate(object sender, ConsoleCancelEventArgs args) { args.Cancel = true; }; public static void Install() { Console.CancelKeyPress += Handler; } }'
$ignoreControlPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ignoreControlSource))
$ignoreControlPrefix = "Add-Type -TypeDefinition ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$ignoreControlPayload'))); [J6IgnoreConsoleControl]::Install(); "
$producerCommand = $ignoreControlPrefix + '$self = [Diagnostics.Process]::GetCurrentProcess(); try { $identity = [ordered]@{ ProcessId = $PID; StartedAtUtcTicks = $self.StartTime.ToUniversalTime().Ticks }; [IO.File]::WriteAllText(__PRODUCER_IDENTITY_PATH__, ($identity | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false)) } finally { $self.Dispose() }; $consumerDeadline = [DateTime]::UtcNow.AddSeconds(5); while (-not (Test-Path -LiteralPath __CONSUMER_IDENTITY_PATH__ -PathType Leaf) -and [DateTime]::UtcNow -lt $consumerDeadline) { Start-Sleep -Milliseconds 25 }; if (-not (Test-Path -LiteralPath __CONSUMER_IDENTITY_PATH__ -PathType Leaf)) { exit 98 }; [IO.File]::WriteAllText(__READY_PATH__, ''READY''); $target = [Console]::OpenStandardOutput(); $buffer = [byte[]]::new(1024); while ($true) { $target.Write($buffer, 0, $buffer.Length); $target.Flush(); Start-Sleep -Milliseconds 20 }'.Replace(
    '__PRODUCER_IDENTITY_PATH__', (ConvertTo-QuotedLiteral $ProducerIdentityPath)).Replace(
    '__CONSUMER_IDENTITY_PATH__', (ConvertTo-QuotedLiteral $ConsumerIdentityPath)).Replace(
    '__READY_PATH__', (ConvertTo-QuotedLiteral $ReadyPath))
$consumerCommand = $ignoreControlPrefix + '$self = [Diagnostics.Process]::GetCurrentProcess(); try { $identity = [ordered]@{ ProcessId = $PID; StartedAtUtcTicks = $self.StartTime.ToUniversalTime().Ticks }; [IO.File]::WriteAllText(__CONSUMER_IDENTITY_PATH__, ($identity | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false)) } finally { $self.Dispose() }; $source = [Console]::OpenStandardInput(); $buffer = [byte[]]::new(1024); while ($source.Read($buffer, 0, $buffer.Length) -gt 0) {}'.Replace(
    '__CONSUMER_IDENTITY_PATH__', (ConvertTo-QuotedLiteral $ConsumerIdentityPath))
$registration = New-J6ConsoleCancellationRegistration
$exitCode = 0
try {
    $result = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', $producerCommand) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', $consumerCommand) `
        -WorkingDirectory $workingDirectory `
        -TimeoutSeconds 10 `
        -CleanupTimeoutMilliseconds 5000 `
        -CancellationToken $registration.Token
    $evidence = [ordered]@{
        TokenCancelled = $registration.Token.IsCancellationRequested
        PipelineResult = $result.PipelineResult
        LocalProcessTreeCleanup = $result.LocalProcessTreeCleanup
        ProducerPid = $result.ProducerPid
        ProducerStartedAtUtc = $result.ProducerStartedAtUtc
        ProducerTargetPid = $result.ProducerTargetPid
        ProducerTargetStartedAtUtcTicks = $result.ProducerTargetStartedAtUtcTicks
        ProducerActiveProcessesAfterCleanup = $result.ProducerActiveProcessesAfterCleanup
        ProducerConfinement = $result.ProducerConfinement
        ConsumerPid = $result.ConsumerPid
        ConsumerStartedAtUtc = $result.ConsumerStartedAtUtc
        ConsumerTargetPid = $result.ConsumerTargetPid
        ConsumerTargetStartedAtUtcTicks = $result.ConsumerTargetStartedAtUtcTicks
        ConsumerActiveProcessesAfterCleanup = $result.ConsumerActiveProcessesAfterCleanup
        ConsumerConfinement = $result.ConsumerConfinement
        ProducerRootAliveAtCleanupStart = $result.ProducerRootAliveAtCleanupStart
        ConsumerRootAliveAtCleanupStart = $result.ConsumerRootAliveAtCleanupStart
    }
    [IO.File]::WriteAllText(
        $ResultPath,
        ($evidence | ConvertTo-Json -Compress),
        [Text.UTF8Encoding]::new($false))
    if (-not $evidence.TokenCancelled -or
        $evidence.PipelineResult -ne 'CANCELLED' -or
        $evidence.LocalProcessTreeCleanup -ne 'PASS' -or
        -not $evidence.ProducerRootAliveAtCleanupStart -or
        -not $evidence.ConsumerRootAliveAtCleanupStart) {
        $exitCode = 90
    }
}
catch {
    $exitCode = 91
}
finally {
    $registration.Dispose()
}
exit $exitCode
'@
    [IO.File]::WriteAllText(
        $ctrlBreakHarnessPath,
        $ctrlBreakHarness,
        [Text.UTF8Encoding]::new($false))
    $ctrlBreakLauncherExitCode = $null
    $ctrlBreakProducerIdentity = $null
    $ctrlBreakConsumerIdentity = $null
    try {
        $script:j6CtrlBreakStage = 'LAUNCHER_EXECUTION'
        & $ctrlBreakLauncherPath `
            $pwshPath `
            $ctrlBreakHarnessPath `
            $modulePath `
            $ctrlBreakReadyPath `
            $ctrlBreakResultPath `
            $ctrlBreakHarnessPidPath `
            $ctrlBreakProducerIdentityPath `
            $ctrlBreakConsumerIdentityPath 2>&1 | Write-J6CtrlBreakNativeEvidence
        $ctrlBreakLauncherExitCode = $LASTEXITCODE
        Write-Host ("J6_CTRL_BREAK_LAUNCHER_EXIT=" + [int]$ctrlBreakLauncherExitCode)
        $script:j6CtrlBreakStage = 'CHILD_ASSERTIONS'
        Assert-J6Qualification ($ctrlBreakLauncherExitCode -eq 0) `
            "The real CTRL_BREAK launcher failed with exit code $ctrlBreakLauncherExitCode." -FailureMarker 'CTRL_BREAK_CHILD_GATE_01'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakReadyPath -PathType Leaf) `
            'The real CTRL_BREAK harness never reached its ready state.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_02'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakResultPath -PathType Leaf) `
            'The real CTRL_BREAK harness did not produce bounded result evidence.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_03'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakHarnessPidPath -PathType Leaf) `
            'The real CTRL_BREAK harness PID was not captured by its exact-handle launcher.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_04'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakExactHarnessExitPath -PathType Leaf) `
            'The CTRL_BREAK launcher did not confirm exact-handle harness termination.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_05'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakProducerIdentityPath -PathType Leaf) `
            'The CTRL_BREAK producer identity was not captured.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_06'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakConsumerIdentityPath -PathType Leaf) `
            'The CTRL_BREAK consumer identity was not captured.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_07'

        $ctrlBreakHarnessPid = [int]([IO.File]::ReadAllText($ctrlBreakHarnessPidPath))
        $ctrlBreakExactHarnessExit = [IO.File]::ReadAllText(
            $ctrlBreakExactHarnessExitPath).Trim()
        Assert-J6Qualification (
            $ctrlBreakExactHarnessExit -eq ('{0}:0' -f $ctrlBreakHarnessPid)) `
            'The exact CTRL_BREAK harness handle did not terminate with the qualified result.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_08'

        $ctrlBreakProducerIdentity = Get-Content `
            -LiteralPath $ctrlBreakProducerIdentityPath `
            -Raw | ConvertFrom-Json
        $ctrlBreakConsumerIdentity = Get-Content `
            -LiteralPath $ctrlBreakConsumerIdentityPath `
            -Raw | ConvertFrom-Json
        $ownedProcesses.Add($ctrlBreakProducerIdentity)
        $ownedProcesses.Add($ctrlBreakConsumerIdentity)
        $ctrlBreakResidualBeforeFallbackCleanup = @(
            $ctrlBreakProducerIdentity,
            $ctrlBreakConsumerIdentity | Where-Object {
                (Get-J6ProcessIdentityObservation -Identity $_).Classification -ne `
                    'ABSENT_ALL_VIEWS'
            }).Count
        Assert-J6Qualification ($ctrlBreakResidualBeforeFallbackCleanup -eq 0) `
            'A CTRL_BREAK-owned exact process identity survived before fallback teardown.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_09'

        $ctrlBreakResult = Get-Content -LiteralPath $ctrlBreakResultPath -Raw | ConvertFrom-Json
        Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $ctrlBreakResult
        Assert-J6Qualification ($ctrlBreakResult.TokenCancelled -eq $true) `
            'The real CTRL_BREAK event did not cancel the registered token.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_10'
        Assert-J6Qualification ($ctrlBreakResult.PipelineResult -eq 'CANCELLED') `
            'The real CTRL_BREAK event was not classified as CANCELLED.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_11'
        Assert-J6Qualification ($ctrlBreakResult.LocalProcessTreeCleanup -eq 'PASS') `
            'The real CTRL_BREAK event left an owned process root.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_12'
        Assert-J6Qualification ($ctrlBreakResult.ProducerRootAliveAtCleanupStart -eq $true) `
            'The CTRL_BREAK signal terminated the producer wrapper before supervisor cleanup.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_13'
        Assert-J6Qualification ($ctrlBreakResult.ConsumerRootAliveAtCleanupStart -eq $true) `
            'The CTRL_BREAK signal terminated the consumer wrapper before supervisor cleanup.' -FailureMarker 'CTRL_BREAK_CHILD_GATE_14'
        Write-Host 'J6_CTRL_BREAK_WRAPPER_ROOTS_ALIVE_BEFORE_SUPERVISOR_CLEANUP=PASS'
        Write-Host 'J6_CONSOLE_RESIDUAL_BEFORE_FALLBACK_CLEANUP=0'
        Write-Host 'J6_CTRL_BREAK_NEW_CONSOLE_REQUESTED=NO'
        Write-Host 'J6_CONSOLE_CTRL_BREAK_EVENT=PASS'
        Write-Host 'J6_CONSOLE_CANCEL_KEYPRESS_WIRING=PASS'
        Write-Host 'J6_PIPELINE_INTERRUPTION_CLEANUP=PASS'
    }
    finally {
        Write-Host 'J6_CTRL_BREAK_FALLBACK_CLEANUP=START'
        try {
        if ($null -eq $ctrlBreakProducerIdentity -and
            (Test-Path -LiteralPath $ctrlBreakProducerIdentityPath -PathType Leaf)) {
            try {
                $ctrlBreakProducerIdentity = Get-Content `
                    -LiteralPath $ctrlBreakProducerIdentityPath `
                    -Raw | ConvertFrom-Json
            }
            catch {
                # Preserve the primary failure. Malformed evidence cannot
                # authorize a PID-only fallback.
            }
        }
        if ($null -eq $ctrlBreakConsumerIdentity -and
            (Test-Path -LiteralPath $ctrlBreakConsumerIdentityPath -PathType Leaf)) {
            try {
                $ctrlBreakConsumerIdentity = Get-Content `
                    -LiteralPath $ctrlBreakConsumerIdentityPath `
                    -Raw | ConvertFrom-Json
            }
            catch {
                # Preserve the primary failure. Malformed evidence cannot
                # authorize a PID-only fallback.
            }
        }
        foreach ($identity in @(
                $ctrlBreakProducerIdentity,
                $ctrlBreakConsumerIdentity)) {
            if ($null -eq $identity) {
                continue
            }
            $residual = Get-Process `
                -Id ([int]$identity.ProcessId) `
                -ErrorAction SilentlyContinue
            if ($null -eq $residual) {
                continue
            }
            try {
                if ($residual.StartTime.ToUniversalTime().Ticks -eq
                    [long]$identity.StartedAtUtcTicks) {
                    $residual.Kill($true)
                    [void]$residual.WaitForExit(3000)
                }
            }
            catch [InvalidOperationException] {
                # The exact synthetic identity exited during fallback teardown.
            }
            finally {
                $residual.Dispose()
            }
        }
        Write-Host 'J6_CTRL_BREAK_FALLBACK_CLEANUP=COMPLETED'
        }
        catch {
            Write-Host 'J6_CTRL_BREAK_FALLBACK_CLEANUP=FAIL'
            throw
        }
    }

    $script:j6CtrlBreakStage = 'COMPLETE'

    if ($WithDocker) {
        if (Get-NetTCPConnection -LocalPort 8087 -State Listen -ErrorAction SilentlyContinue) {
            throw 'Stop the local application before the Docker loopback qualification.'
        }
        if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot '.env') -PathType Leaf)) {
            throw 'The local .env file is required for the Docker loopback qualification.'
        }
        $dockerExecutable = $null
        if (-not [string]::IsNullOrWhiteSpace($DockerPath)) {
            if (-not [IO.Path]::IsPathFullyQualified($DockerPath)) {
                throw 'The Docker qualification path must be absolute.'
            }
            $dockerExecutable = [IO.Path]::GetFullPath($DockerPath)
            if (-not (Test-Path -LiteralPath $dockerExecutable -PathType Leaf)) {
                throw 'The supplied Docker qualification executable does not exist.'
            }
        }
        else {
            $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
            if ($null -eq $dockerCommand) {
                throw 'Docker is required for the Docker loopback qualification.'
            }
            $dockerExecutable = [IO.Path]::GetFullPath($dockerCommand.Source)
        }
        Assert-J6QualificationDockerExecutableIdentity `
            -ExecutablePath $dockerExecutable
        Write-Host 'J6_DOCKER_EXECUTABLE_IDENTITY=AUTHENTICODE_DOCKER_INC'
        Push-Location $repositoryRoot
        try {
            & $dockerExecutable compose --env-file .env config --quiet
            if ($LASTEXITCODE -ne 0) {
                throw 'Docker Compose configuration failed during loopback qualification.'
            }
            $connectorState = (Invoke-J6QualificationDockerScalar `
                -DockerExecutable $dockerExecutable `
                -Sql "select case when not network_enabled and circuit_state = 'LOCKED' then 'SAFE' else 'UNSAFE' end from connector_control where singleton_id = 1").TrimEnd("`r", "`n")
            Assert-J6Qualification ($connectorState -eq 'SAFE') `
                'The persisted connector was not disabled and LOCKED.'

            $applicationName = 'j6_backup_' + [Guid]::NewGuid().ToString('N')
            $dockerFailureResult = $null
            $dockerEarlyFailingConsumer = 'Start-Sleep -Milliseconds 250; exit 17'
            try {
                $dockerFailureResult = Invoke-J6NativeBinaryPipeline `
                    -Phase BACKUP_ENCRYPTION `
                    -ProducerFilePath $dockerExecutable `
                    -ProducerArgumentList @(
                        'compose', '--env-file', '.env', 'exec', '-T',
                        '-e', "PGAPPNAME=$applicationName",
                        'postgres', 'sh', '-c',
                        'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --no-owner --no-privileges') `
                    -ConsumerFilePath $pwshPath `
                    -ConsumerArgumentList ($commonArguments + @($dockerEarlyFailingConsumer)) `
                    -WorkingDirectory $repositoryRoot `
                    -TimeoutSeconds 15
                Add-J6OwnedPipelineProcessEvidence `
                    -Collection $ownedProcesses `
                    -Result $dockerFailureResult
                Assert-J6Qualification ($dockerFailureResult.PipelineResult -eq 'CONSUMER_FAILED') `
                    'The Docker pg_dump producer did not fail closed after its fake consumer exited.'
                Assert-J6Qualification ($dockerFailureResult.LocalProcessTreeCleanup -eq 'PASS') `
                    'The Docker pg_dump producer root cleanup was not confirmed.'

                $deadline = [DateTime]::UtcNow.AddSeconds(5)
                $consecutiveAbsentSamples = 0
                do {
                    $sessionCount = ConvertFrom-J6QualificationCanonicalCount -Value `
                        (Invoke-J6QualificationDockerScalar `
                            -DockerExecutable $dockerExecutable `
                            -Sql "select count(*) from pg_stat_activity where application_name = '$applicationName' and pid <> pg_backend_pid()")
                    if ($sessionCount -eq 0) {
                        $consecutiveAbsentSamples++
                    }
                    else {
                        $consecutiveAbsentSamples = 0
                    }
                    if ($consecutiveAbsentSamples -ge 3) {
                        break
                    }
                    Start-Sleep -Milliseconds 50
                } while ([DateTime]::UtcNow -lt $deadline)
                Assert-J6Qualification ($consecutiveAbsentSamples -ge 3) `
                    'An exactly owned Docker pg_dump session survived bounded local tree cleanup.'
                Write-Host 'J6_PIPELINE_DOCKER_PG_DUMP_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED'
                Write-Host 'J6_PIPELINE_REMOTE_OWNED_SESSION_COUNT=0'
            }
            finally {
                $remaining = ConvertFrom-J6QualificationCanonicalCount -Value `
                    (Invoke-J6QualificationDockerScalar `
                        -DockerExecutable $dockerExecutable `
                        -Sql "select count(*) from pg_stat_activity where application_name = '$applicationName' and pid <> pg_backend_pid()")
                if ($remaining -ne 0) {
                    [void](Invoke-J6QualificationDockerScalar `
                        -DockerExecutable $dockerExecutable `
                        -Sql @"
select count(*)
from (
    select pg_terminate_backend(pid)
    from pg_stat_activity
    where application_name = '$applicationName'
      and pid <> pg_backend_pid()
) terminated
"@)
                }
            }

            $syntheticAgePath = Join-Path $resolvedQualificationRoot 'j6-synthetic-age.exe'
            New-J6SyntheticAgeExecutable -DestinationPath $syntheticAgePath
            $backupScript = Join-Path $repositoryRoot 'scripts\Backup-Restore-J6.ps1'
            $previousSyntheticMode = [Environment]::GetEnvironmentVariable(
                'J6_SYNTHETIC_AGE_MODE',
                [EnvironmentVariableTarget]::Process)
            $previousSyntheticMarker = [Environment]::GetEnvironmentVariable(
                'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER',
                [EnvironmentVariableTarget]::Process)
            $previousFaultInjection = [Environment]::GetEnvironmentVariable(
                'J6_WO024_LOOPBACK_FAULT_INJECTION',
                [EnvironmentVariableTarget]::Process)
            try {
                $nominalDestination = Join-Path $resolvedQualificationRoot 'nominal-loopback.age'
                $nominalMarker = Join-Path $resolvedQualificationRoot `
                    'nominal-passphrase-invocation.marker'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'NOMINAL',
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER',
                    $nominalMarker,
                    [EnvironmentVariableTarget]::Process)
                $nominalBackupOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $nominalDestination `
                    -AgePath $syntheticAgePath `
                    -DockerPath $dockerExecutable `
                    -PipelineTimeoutSeconds 120 *>&1)
                $nominalBackupExitCode = $LASTEXITCODE
                $nominalBackupText = ($nominalBackupOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6BackupRuntimeEffectiveDefaults `
                    -CapturedText $nominalBackupText
                Assert-J6Qualification ($nominalBackupExitCode -eq 0) `
                    'The synthetic passphrase-invocation loopback backup/restore did not complete.'
                Assert-J6Qualification ($nominalBackupText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The nominal loopback backup did not publish a qualified result after cleanup.'
                Assert-J6Qualification (
                    $nominalBackupText.Contains(
                        'J6_POSTGRES_SESSION_CLEANUP_IDEMPOTENT_REUSE=PASS') -and
                    $nominalBackupText.Contains(
                        'J6_POSTGRES_SESSION_REMAINING_COUNT=0') -and
                    $nominalBackupText.Contains(
                        'J6_POSTGRES_SESSION_STABLE_ZERO_OBSERVATIONS=3')) `
                    'The nominal loopback path did not prove exact idempotent PostgreSQL cleanup.'
                Assert-J6Qualification (Test-Path -LiteralPath $nominalDestination -PathType Leaf) `
                    'The nominal loopback encrypted backup was not published.'
                Assert-J6Qualification (Test-Path -LiteralPath ($nominalDestination + '.manifest.json') -PathType Leaf) `
                    'The nominal loopback manifest was not published.'
                $nominalManifest = Get-Content `
                    -LiteralPath ($nominalDestination + '.manifest.json') `
                    -Raw | ConvertFrom-Json
                Assert-J6Qualification ($nominalManifest.restoreQualified -eq $true) `
                    'The nominal loopback manifest was not restore-qualified.'
                Assert-J6Qualification (Test-Path -LiteralPath $nominalMarker -PathType Leaf) `
                    'The synthetic age -p invocation path was not exercised.'
                Assert-J6Qualification (
                    [IO.File]::ReadAllText($nominalMarker) -ceq
                        'PASSPHRASE_INVOCATION_PATH=SIMULATED_NO_SECRET') `
                    'The synthetic age marker did not prove the abstract no-secret invocation path.'
                Write-Host 'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION=PASS_ABSTRACT_NO_SECRET'
                Write-Host 'J6_PIPELINE_DOCKER_PG_RESTORE_NOMINAL=PASS'
                Write-Host 'J6_TEMPORARY_RESTORE_DATABASE_CLEANUP=PASS'

                $encryptFailureDestination = Join-Path $resolvedQualificationRoot 'encrypt-failure.age'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'FAIL_ENCRYPT',
                    [EnvironmentVariableTarget]::Process)
                $encryptFailureOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $encryptFailureDestination `
                    -AgePath $syntheticAgePath `
                    -DockerPath $dockerExecutable `
                    -PipelineTimeoutSeconds 120 *>&1)
                $encryptFailureExitCode = $LASTEXITCODE
                $encryptFailureText = ($encryptFailureOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6BackupRuntimeEffectiveDefaults `
                    -CapturedText $encryptFailureText
                Assert-J6Qualification ($encryptFailureExitCode -ne 0) `
                    'The synthetic encryptor failure did not fail the backup script.'
                Assert-J6Qualification (-not $encryptFailureText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The synthetic encryptor failure emitted a favorable qualification.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath $encryptFailureDestination)) `
                    'The synthetic encryptor failure left a final backup.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath ($encryptFailureDestination + '.manifest.json'))) `
                    'The synthetic encryptor failure left a manifest.'
                $encryptFailurePartials = @(Get-ChildItem `
                    -LiteralPath $resolvedQualificationRoot `
                    -Filter 'encrypt-failure.age.partial-*' `
                    -File)
                Assert-J6Qualification ($encryptFailurePartials.Count -eq 0) `
                    'The synthetic encryptor failure left a partial backup.'
                Write-Host 'J6_PARTIAL_FILE_CLEANUP=PASS'

                $decryptFailureDestination = Join-Path $resolvedQualificationRoot 'decrypt-failure.age'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'FAIL_DECRYPT',
                    [EnvironmentVariableTarget]::Process)
                $decryptFailureOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $decryptFailureDestination `
                    -AgePath $syntheticAgePath `
                    -DockerPath $dockerExecutable `
                    -PipelineTimeoutSeconds 120 *>&1)
                $decryptFailureExitCode = $LASTEXITCODE
                $decryptFailureText = ($decryptFailureOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6BackupRuntimeEffectiveDefaults `
                    -CapturedText $decryptFailureText
                Assert-J6Qualification ($decryptFailureExitCode -ne 0) `
                    'The synthetic decryptor failure did not fail the backup script.'
                Assert-J6Qualification (-not $decryptFailureText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The synthetic decryptor failure emitted a favorable qualification.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath $decryptFailureDestination)) `
                    'The synthetic decryptor failure left a final backup.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath ($decryptFailureDestination + '.manifest.json'))) `
                    'The synthetic decryptor failure left a manifest.'
                $decryptFailurePartials = @(Get-ChildItem `
                    -LiteralPath $resolvedQualificationRoot `
                    -Filter 'decrypt-failure.age.partial-*' `
                    -File)
                Assert-J6Qualification ($decryptFailurePartials.Count -eq 0) `
                    'The synthetic decryptor failure left a staged backup.'
                $temporaryDatabaseCount = ConvertFrom-J6QualificationCanonicalCount -Value `
                    (Invoke-J6QualificationDockerScalar `
                        -DockerExecutable $dockerExecutable `
                        -Sql "select count(*) from pg_database where datname like 'sofascore_j6_restore_%'")
                Assert-J6Qualification ($temporaryDatabaseCount -eq 0) `
                    'A temporary restore database survived the synthetic restore failure.'
                Write-Host 'J6_DECRYPTOR_FAILURE_REJECTS_QUALIFICATION=PASS_FAIL_CLOSED'
                Write-Host 'J6_TEMPORARY_RESTORE_DATABASE_RESIDUAL_COUNT=0'

                $cleanupFailureDestination = Join-Path $resolvedQualificationRoot 'cleanup-failure.age'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'NOMINAL',
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_WO024_LOOPBACK_FAULT_INJECTION',
                    'AUTHORIZED',
                    [EnvironmentVariableTarget]::Process)
                $cleanupFailureOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $cleanupFailureDestination `
                    -AgePath $syntheticAgePath `
                    -DockerPath $dockerExecutable `
                    -PipelineTimeoutSeconds 120 `
                    -QualificationInjectCleanupFailureAfterSuccessfulCleanup *>&1)
                $cleanupFailureExitCode = $LASTEXITCODE
                $cleanupFailureText = ($cleanupFailureOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6BackupRuntimeEffectiveDefaults `
                    -CapturedText $cleanupFailureText
                Assert-J6Qualification ($cleanupFailureExitCode -ne 0) `
                    'The injected cleanup failure did not fail the backup script.'
                Assert-J6Qualification (
                    $cleanupFailureText.Contains(
                        'J6_CLEANUP_FAILURE_CLASSES=QUALIFICATION_INJECTED_CLEANUP_FAILURE')) `
                    'The injected cleanup failure did not retain its sanitized classification.'
                Assert-J6Qualification (-not $cleanupFailureText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The injected cleanup failure emitted a favorable qualification.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath $cleanupFailureDestination)) `
                    'The injected cleanup failure left a final backup.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath ($cleanupFailureDestination + '.manifest.json'))) `
                    'The injected cleanup failure left a favorable manifest.'
                $cleanupFailureDatabaseCount = ConvertFrom-J6QualificationCanonicalCount -Value `
                    (Invoke-J6QualificationDockerScalar `
                        -DockerExecutable $dockerExecutable `
                        -Sql "select count(*) from pg_database where datname like 'sofascore_j6_restore_%'")
                Assert-J6Qualification ($cleanupFailureDatabaseCount -eq 0) `
                    'The injected cleanup failure test left a temporary restore database.'
                $ownedSessionResidualCount =
                    ConvertFrom-J6QualificationCanonicalCount -Value `
                        (Invoke-J6QualificationDockerScalar `
                            -DockerExecutable $dockerExecutable `
                            -Sql "select count(*) from pg_stat_activity where application_name ~ '^j6_(backup|restore)_[a-f0-9]{32}$' and pid <> pg_backend_pid()")
                Assert-J6Qualification ($ownedSessionResidualCount -eq 0) `
                    'An exactly tagged J6 PostgreSQL session survived Docker qualification.'
                $partialArtifacts = @(Get-ChildItem `
                    -LiteralPath $resolvedQualificationRoot `
                    -Recurse `
                    -File | Where-Object {
                        $_.Name -cmatch '\.partial-[a-f0-9]{32}$'
                    })
                Assert-J6Qualification ($partialArtifacts.Count -eq 0) `
                    'A J6 partial artifact survived Docker qualification.'
                Write-Host 'J6_CLEANUP_FAILURE_REJECTS_QUALIFICATION=PASS_FAIL_CLOSED'
                Write-Host 'J6_POSTGRES_OWNED_SESSION_RESIDUAL_COUNT=0'
                Write-Host 'J6_BACKUP_PARTIAL_ARTIFACT_RESIDUAL_COUNT=0'
                Write-Host 'J6_BACKUP_RUNTIME_EFFECTIVE_DEFAULTS_5000_10000=PASS'
            }
            finally {
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    $previousSyntheticMode,
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER',
                    $previousSyntheticMarker,
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_WO024_LOOPBACK_FAULT_INJECTION',
                    $previousFaultInjection,
                    [EnvironmentVariableTarget]::Process)
            }
        }
        finally {
            Pop-Location
        }
    }

    $uniqueOwnedProcesses = [Collections.Generic.Dictionary[string, object]]::new()
    foreach ($ownedProcess in $ownedProcesses) {
        $identityKey = '{0}:{1}' -f `
            [int]$ownedProcess.ProcessId,
            [long]$ownedProcess.StartedAtUtcTicks
        if (-not $uniqueOwnedProcesses.ContainsKey($identityKey)) {
            $uniqueOwnedProcesses.Add($identityKey, $ownedProcess)
        }
    }
    $secondarySnapshot = New-J6SecondaryProcessObservationSnapshot
    Assert-J6Qualification (
        $secondarySnapshot.CimState -eq 'AVAILABLE' -and
        $secondarySnapshot.TasklistState -eq 'AVAILABLE') `
        'The bounded final CIM/tasklist process snapshot remained unavailable.'
    Write-Host (
        'J6_PROCESS_SECONDARY_SNAPSHOT=' +
        "PASS_AFTER_$($secondarySnapshot.AttemptCount)_ATTEMPT")
    $finalIdentityOrdinal = 0
    foreach ($ownedProcess in $uniqueOwnedProcesses.Values) {
        $finalIdentityOrdinal++
        try {
            [void](Assert-J6OwnedProcessIdentityGone `
                -Identity $ownedProcess `
                -SecondarySnapshot $secondarySnapshot)
        }
        catch {
            $identityFailureToken = if (
                $_.Exception.Message -cmatch '^J6_[A-Z0-9_]+$') {
                $_.Exception.Message
            }
            else {
                'SANITIZED_' + $_.Exception.GetType().Name.ToUpperInvariant()
            }
            Write-Host (
                'J6_PROCESS_FINAL_IDENTITY_FAILURE=' +
                "ORDINAL_$finalIdentityOrdinal," +
                "TOKEN_$identityFailureToken")
            throw
        }
    }
    Write-Host 'J6_PROCESS_IDENTITY_FINAL_BATCH=PASS'
    Write-Host 'J6_TASKLIST_OBSERVER_BOUNDED_JOB_CLEANUP=PASS'
    try {
        $loopbackListeners = @(
            [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().
                GetActiveTcpListeners() |
                Where-Object { $_.Port -eq 8087 })
    }
    catch {
        throw (New-J6QualificationSanitizedException `
                -Classification 'LOOPBACK_LISTENER_OBSERVATION_FAILED' `
                -InnerException $_.Exception)
    }
    if ($loopbackListeners.Count -ne 0) {
        throw (New-J6QualificationSanitizedException `
                -Classification 'LOOPBACK_APPLICATION_LISTENER_RESIDUAL')
    }
    Write-Host "J6_PIPELINE_UNIQUE_OWNED_IDENTITY_COUNT=$($uniqueOwnedProcesses.Count)"
    Write-Host 'J6_PIPELINE_OWNED_IDENTITIES_INACTIVE_MULTI_API=PASS'
    Write-Host 'J6_PID_ONLY_TERMINATION_USED=NO'
    Write-Host 'J6_PIPELINE_RESIDUAL_OWNED_PROCESS_COUNT=0'
    Write-Host 'J6_LOOPBACK_APPLICATION_LISTENER_RESIDUAL_COUNT=0'
    Write-Host 'J6_PIPELINE_HUMAN_INCORRECT_PASSPHRASE_REQUIRED=NO'
    Write-Host 'PROVIDER_ACCESS_PERFORMED=NO'
    Write-Host 'J6_BACKUP_RESTORE_LOOPBACK_QUALIFICATION=PASS'
}
catch {
    $qualificationFailure = $_.Exception
    $terminalClassification = [string]$qualificationFailure.Data[
        'J6QualificationClassification']
    if ([string]::IsNullOrWhiteSpace($terminalClassification)) {
        $terminalClassification = [string]$qualificationFailure.Data[
            'J6CleanupClassification']
    }
    if ([string]::IsNullOrWhiteSpace($terminalClassification) -and
        $qualificationFailure.Message -cmatch
            '^(?<classification>J6_[A-Z0-9_=;,]+)$') {
        $terminalClassification = $Matches.classification
    }
    if ([string]::IsNullOrWhiteSpace($terminalClassification)) {
        $terminalClassification = if (
            $qualificationFailure -is [TimeoutException]) {
            'TIMEOUT_EXCEPTION'
        }
        elseif ($qualificationFailure -is [UnauthorizedAccessException]) {
            'ACCESS_DENIED'
        }
        elseif ($qualificationFailure -is [IO.IOException]) {
            'IO_FAILURE'
        }
        else {
            'UNCLASSIFIED_FAIL_CLOSED'
        }
    }
    if (Get-Variable j6CtrlBreakStage -Scope Script -ErrorAction SilentlyContinue) {
        Write-Host ('J6_CTRL_BREAK_LAST_STAGE=' + $script:j6CtrlBreakStage)
    }
    Write-Host (
        'J6_QUALIFICATION_PRIMARY_FAILURE_CLASS=' +
        $terminalClassification)
}
finally {
    try {
        Remove-J6OwnedQualificationTempRoot `
            -Ownership $qualificationRootOwnership
    }
    catch {
        $tempRootCleanupFailure = $_.Exception
    }
}
if ($null -ne $qualificationFailure -and
    $null -ne $tempRootCleanupFailure) {
    throw (New-J6CombinedQualificationTempCleanupFailure `
            -PrimaryFailure $qualificationFailure `
            -TempCleanupFailure $tempRootCleanupFailure)
}
if ($null -ne $tempRootCleanupFailure) {
    if ($tempRootCleanupFailure.Data['J6QualificationClassification'] -eq
        'TEMP_ROOT_CLEANUP_FAILED') {
        throw $tempRootCleanupFailure
    }
    throw (New-J6QualificationSanitizedException `
            -Classification 'TEMP_ROOT_CLEANUP_FAILED' `
            -InnerException $tempRootCleanupFailure)
}
if ($null -ne $qualificationFailure) {
    throw $qualificationFailure
}
