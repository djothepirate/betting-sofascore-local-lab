[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$AgePath,

    [string]$DockerPath,

    [ValidateRange(30, 3600)]
    [int]$PipelineTimeoutSeconds = 300,

    [ValidateRange(100, 30000)]
    [int]$PipelineCleanupTimeoutMilliseconds = 5000,

    [ValidateRange(10000, 60000)]
    [int]$PostgresCleanupTimeoutMilliseconds = 10000,

    [Parameter(DontShow = $true)]
    [switch]$QualificationInjectCleanupFailureAfterSuccessfulCleanup
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($QualificationInjectCleanupFailureAfterSuccessfulCleanup -and
    $env:J6_WO024_LOOPBACK_FAULT_INJECTION -cne 'AUTHORIZED') {
    throw 'The WO-024 loopback cleanup fault injection is not authorized in this process.'
}

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required to preserve native binary pipelines.'
}
$pipelineModulePath = Join-Path $PSScriptRoot 'J6-NativeBinaryPipeline.psm1'
if (-not (Test-Path -LiteralPath $pipelineModulePath -PathType Leaf)) {
    throw 'The J6 native binary pipeline module is required.'
}
Import-Module -Name $pipelineModulePath -Force

function Assert-J6DockerExecutableIdentity {
    param([Parameter(Mandatory = $true)][string]$ExecutablePath)

    $canonicalPath = [IO.Path]::GetFullPath($ExecutablePath)
    $item = Get-Item -LiteralPath $canonicalPath -Force
    if ($item.Name -cne 'docker.exe' -or
        ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        $item.VersionInfo.CompanyName -cne 'Docker Inc' -or
        $item.VersionInfo.ProductName -cne 'Docker Client') {
        throw 'The Docker executable identity is not the qualified Docker Client.'
    }
    $signature = Get-AuthenticodeSignature -LiteralPath $canonicalPath
    if ($signature.Status -ne [Management.Automation.SignatureStatus]::Valid -or
        $null -eq $signature.SignerCertificate -or
        $signature.SignerCertificate.Subject -cnotmatch
            '(^|, )O=Docker Inc(,|$)') {
        throw 'The Docker executable Authenticode identity is not valid for Docker Inc.'
    }
}

if (-not [IO.Path]::IsPathFullyQualified($Destination)) {
    throw 'The encrypted backup destination must be absolute.'
}

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$destinationPath = [IO.Path]::GetFullPath($Destination)
$repositoryPrefix = $repositoryRoot.TrimEnd('\') + '\'
if ($destinationPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The encrypted backup destination must be outside the repository.'
}
if (-not $destinationPath.EndsWith('.age', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The encrypted backup destination must use the .age extension.'
}
$destinationDirectory = Split-Path -Parent $destinationPath
if (-not (Test-Path -LiteralPath $destinationDirectory -PathType Container)) {
    throw 'The encrypted backup destination directory must already exist.'
}
$manifestPath = $destinationPath + '.manifest.json'
if ((Test-Path -LiteralPath $destinationPath) -or (Test-Path -LiteralPath $manifestPath)) {
    throw 'The destination or its manifest already exists; choose a new backup name.'
}

if (Get-NetTCPConnection -LocalPort 8087 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Stop the local application before creating and qualifying the J6 backup.'
}
$dockerExecutable = $null
if (-not [string]::IsNullOrWhiteSpace($DockerPath)) {
    if (-not [IO.Path]::IsPathFullyQualified($DockerPath)) {
        throw 'The supplied Docker executable path must be absolute.'
    }
    $dockerExecutable = [IO.Path]::GetFullPath($DockerPath)
    if (-not (Test-Path -LiteralPath $dockerExecutable -PathType Leaf)) {
        throw 'The supplied Docker executable does not exist.'
    }
}
else {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -eq $dockerCommand) {
        throw 'Docker is required. Supply -DockerPath when it is not available on PATH.'
    }
    $dockerExecutable = [IO.Path]::GetFullPath($dockerCommand.Source)
}
Assert-J6DockerExecutableIdentity -ExecutablePath $dockerExecutable
Write-Host 'J6_DOCKER_EXECUTABLE_IDENTITY=AUTHENTICODE_DOCKER_INC'
if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot '.env') -PathType Leaf)) {
    throw 'The local .env file is required by Docker Compose.'
}

$ageExecutable = $null
if (-not [string]::IsNullOrWhiteSpace($AgePath)) {
    $ageExecutable = [IO.Path]::GetFullPath($AgePath)
    if (-not (Test-Path -LiteralPath $ageExecutable -PathType Leaf)) {
        throw 'The supplied age executable does not exist.'
    }
}
else {
    $ageCommand = Get-Command age -ErrorAction SilentlyContinue
    if ($null -eq $ageCommand) {
        throw 'age is required. Supply -AgePath when it is not available on PATH.'
    }
    $ageExecutable = $ageCommand.Source
}

function Invoke-PrimaryScalar {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $value = & $dockerExecutable compose --env-file .env exec -T postgres sh -c `
        'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-align --tuples-only --quiet --set=ON_ERROR_STOP=1 --command "$1"' `
        sh $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'A primary database verification query failed.'
    }
    return (($value | ForEach-Object { $_.ToString() }) -join "`n").TrimEnd()
}

function Invoke-RestoreScalar {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Sql
    )
    $value = & $dockerExecutable compose --env-file .env exec -T postgres sh -c `
        'psql --username "$POSTGRES_USER" --dbname "$1" --no-align --tuples-only --quiet --set=ON_ERROR_STOP=1 --command "$2"' `
        sh $Database $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'A restored database verification query failed.'
    }
    return (($value | ForEach-Object { $_.ToString() }) -join "`n").TrimEnd()
}

function Assert-J6OwnedPostgresApplicationName {
    param([Parameter(Mandatory = $true)][string]$ApplicationName)
    if ($ApplicationName -cnotmatch '^j6_(backup|restore)_[a-f0-9]{32}$') {
        throw 'The owned PostgreSQL application name is unsafe.'
    }
}

function ConvertTo-J6SanitizedInnerException {
    param(
        [Parameter(Mandatory = $true)][Exception]$Exception,
        [ValidateRange(0, 8)][int]$Depth = 0
    )

    if ($Depth -ge 8) {
        $depthLimited = [InvalidOperationException]::new(
            'J6_SANITIZED_INNER_CAUSE=CAUSE_DEPTH_LIMIT')
        $depthLimited.Data['J6SanitizedCauseCategory'] = 'CAUSE_DEPTH_LIMIT'
        return $depthLimited
    }

    if ($Exception -is [AggregateException]) {
        $sanitizedCauses = [Collections.Generic.List[Exception]]::new()
        foreach ($inner in @($Exception.InnerExceptions)) {
            $sanitizedCauses.Add((ConvertTo-J6SanitizedInnerException `
                        -Exception $inner `
                        -Depth ($Depth + 1)))
        }
        $sanitizedAggregate = [AggregateException]::new(
            'J6_SANITIZED_INNER_CAUSE=AGGREGATE_FAILURE',
            [Exception[]]$sanitizedCauses.ToArray())
        $sanitizedAggregate.Data['J6SanitizedCauseCategory'] = `
            'AGGREGATE_FAILURE'
        return $sanitizedAggregate
    }

    $cleanupClassification = [string]$Exception.Data[
        'J6CleanupClassification']
    $category = if ($cleanupClassification -cmatch
        '^([A-Z0-9]+_?)+$') {
        'CLEANUP_' + $cleanupClassification
    }
    elseif ($Exception -is [TimeoutException]) {
        'TIMEOUT_EXCEPTION'
    }
    elseif ($Exception -is [OperationCanceledException]) {
        'OPERATION_CANCELLED'
    }
    elseif ($Exception -is [UnauthorizedAccessException]) {
        'ACCESS_DENIED'
    }
    elseif ($Exception -is [ComponentModel.Win32Exception]) {
        'NATIVE_PROCESS_FAILURE'
    }
    elseif ($Exception -is [IO.IOException]) {
        'IO_FAILURE'
    }
    elseif ($Exception -is [InvalidOperationException]) {
        'INVALID_OPERATION'
    }
    else {
        'OTHER_FAILURE'
    }

    $sanitizedNestedCause = $null
    if ($null -ne $Exception.InnerException) {
        $sanitizedNestedCause = ConvertTo-J6SanitizedInnerException `
            -Exception $Exception.InnerException `
            -Depth ($Depth + 1)
    }
    $sanitized = if ($null -eq $sanitizedNestedCause) {
        [InvalidOperationException]::new(
            "J6_SANITIZED_INNER_CAUSE=$category")
    }
    else {
        [InvalidOperationException]::new(
            "J6_SANITIZED_INNER_CAUSE=$category",
            $sanitizedNestedCause)
    }
    $sanitized.Data['J6SanitizedCauseCategory'] = $category
    return $sanitized
}

function New-J6SanitizedCleanupException {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'POSTGRES_DOCKER_COMMAND_FAILED',
            'POSTGRES_DOCKER_COMMAND_NONZERO_EXIT',
            'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED',
            'POSTGRES_SQL_COMMAND_NONZERO_EXIT',
            'POSTGRES_OBSERVATION_TIMEOUT',
            'POSTGRES_SCALAR_OUTPUT_INVALID',
            'POSTGRES_SESSION_REMAINING',
            'POSTGRES_SESSION_STABILITY_NOT_PROVEN',
            'POSTGRES_SESSION_CLEANUP_UNCONFIRMED',
            'TEMPORARY_RESTORE_DATABASE_CLEANUP_UNCONFIRMED',
            'FILE_CLEANUP_UNCONFIRMED',
            'LOCAL_RESOURCE_CLEANUP_UNCONFIRMED',
            'PRIMARY_OPERATION_FAILED_DURING_CLEANUP',
            'QUALIFICATION_INJECTED_CLEANUP_FAILURE')]
        [string]$Classification,
        [AllowNull()][Exception]$InnerException
    )

    $message = "J6_CLEANUP_FAILURE=$Classification"
    $sanitizedInnerException = $null
    if ($null -ne $InnerException) {
        $sanitizedInnerException = ConvertTo-J6SanitizedInnerException `
            -Exception $InnerException
    }
    $exception = if ($null -eq $sanitizedInnerException) {
        [InvalidOperationException]::new($message)
    }
    else {
        [InvalidOperationException]::new($message, $sanitizedInnerException)
    }
    $exception.Data['J6CleanupClassification'] = $Classification
    return $exception
}

function Get-J6SanitizedCleanupClassification {
    param(
        [Parameter(Mandatory = $true)][Exception]$Exception,
        [Parameter(Mandatory = $true)][string]$Fallback
    )

    $classification = [string]$Exception.Data['J6CleanupClassification']
    if ($classification -in @(
            'POSTGRES_DOCKER_COMMAND_FAILED',
            'POSTGRES_DOCKER_COMMAND_NONZERO_EXIT',
            'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED',
            'POSTGRES_SQL_COMMAND_NONZERO_EXIT',
            'POSTGRES_OBSERVATION_TIMEOUT',
            'POSTGRES_SCALAR_OUTPUT_INVALID',
            'POSTGRES_SESSION_REMAINING',
            'POSTGRES_SESSION_STABILITY_NOT_PROVEN',
            'POSTGRES_SESSION_CLEANUP_UNCONFIRMED',
            'TEMPORARY_RESTORE_DATABASE_CLEANUP_UNCONFIRMED',
            'FILE_CLEANUP_UNCONFIRMED',
            'LOCAL_RESOURCE_CLEANUP_UNCONFIRMED',
            'PRIMARY_OPERATION_FAILED_DURING_CLEANUP',
            'QUALIFICATION_INJECTED_CLEANUP_FAILURE')) {
        return $classification
    }
    return $Fallback
}

function ConvertFrom-J6StrictNonNegativeInt64Scalar {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value -cnotmatch '\A(0|[1-9][0-9]*)(?:\r?\n)?\z') {
        throw (New-J6SanitizedCleanupException `
                -Classification 'POSTGRES_SCALAR_OUTPUT_INVALID')
    }

    $canonicalValue = $Matches[1]
    [long]$parsedValue = 0
    if (-not [long]::TryParse(
            $canonicalValue,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsedValue)) {
        throw (New-J6SanitizedCleanupException `
                -Classification 'POSTGRES_SCALAR_OUTPUT_INVALID')
    }
    return $parsedValue
}

function ConvertFrom-J6StrictTerminationEvidenceScalar {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value -cnotmatch
        '\A(?<targeted>0|[1-9][0-9]*),(?<successful>0|[1-9][0-9]*)(?:\r?\n)?\z') {
        throw (New-J6SanitizedCleanupException `
                -Classification 'POSTGRES_SCALAR_OUTPUT_INVALID')
    }
    $targetedText = [string]$Matches.targeted
    $successfulText = [string]$Matches.successful
    $targeted = ConvertFrom-J6StrictNonNegativeInt64Scalar `
        -Value $targetedText
    $successful = ConvertFrom-J6StrictNonNegativeInt64Scalar `
        -Value $successfulText
    if ($successful -gt $targeted) {
        throw (New-J6SanitizedCleanupException `
                -Classification 'POSTGRES_SESSION_CLEANUP_UNCONFIRMED')
    }
    return [pscustomobject]@{
        TargetedSessionCount = $targeted
        SuccessfulTerminationSignals = $successful
    }
}

function Merge-J6PostgresTerminationEvidence {
    param(
        [Parameter(Mandatory = $true)][long]$TargetedSessionAttempts,
        [Parameter(Mandatory = $true)][long]$SuccessfulTerminationSignals,
        [Parameter(Mandatory = $true)][long]$TargetedSessionCountNow,
        [Parameter(Mandatory = $true)][long]$SuccessfulSignalsNow
    )

    if ($TargetedSessionAttempts -lt 0 -or
        $SuccessfulTerminationSignals -lt 0 -or
        $TargetedSessionCountNow -lt 0 -or
        $SuccessfulSignalsNow -lt 0 -or
        $SuccessfulSignalsNow -gt $TargetedSessionCountNow -or
        $TargetedSessionAttempts -gt
            ([long]::MaxValue - $TargetedSessionCountNow) -or
        $SuccessfulTerminationSignals -gt
            ([long]::MaxValue - $SuccessfulSignalsNow)) {
        throw (New-J6SanitizedCleanupException `
                -Classification 'POSTGRES_SESSION_CLEANUP_UNCONFIRMED')
    }

    return [pscustomobject]@{
        TargetedSessionAttempts =
            $TargetedSessionAttempts + $TargetedSessionCountNow
        SuccessfulTerminationSignals =
            $SuccessfulTerminationSignals + $SuccessfulSignalsNow
    }
}

function Resolve-J6PostgresCleanupTimeoutClassification {
    param(
        [Parameter(Mandatory = $true)][long]$LastSessionCount,
        [Parameter(Mandatory = $true)]
        [bool]$LastObservationIsFreshAfterTermination
    )

    if ($LastObservationIsFreshAfterTermination -and
        $LastSessionCount -gt 0) {
        return 'POSTGRES_SESSION_REMAINING'
    }
    return 'POSTGRES_OBSERVATION_TIMEOUT'
}

function Assert-J6OperatorNotCancelled {
    if ($operatorCancellation.Token.IsCancellationRequested) {
        throw [OperationCanceledException]::new('The J6 backup/restore was cancelled by the operator.')
    }
}

function Get-J6RemainingCleanupMilliseconds {
    param([Parameter(Mandatory = $true)][DateTime]$DeadlineUtc)
    $remaining = [int][Math]::Floor(($DeadlineUtc - [DateTime]::UtcNow).TotalMilliseconds)
    if ($remaining -lt 1) {
        throw 'The bounded J6 cleanup deadline expired.'
    }
    return $remaining
}

function Invoke-J6BoundedDockerCleanupCommand {
    param(
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )
    try {
        $remaining = Get-J6RemainingCleanupMilliseconds -DeadlineUtc $DeadlineUtc
        $result = Invoke-J6BoundedNativeCommand `
            -FilePath $dockerExecutable `
            -ArgumentList $ArgumentList `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds $remaining `
            -CleanupTimeoutMilliseconds $PipelineCleanupTimeoutMilliseconds `
            -OverallCommandDeadlineUtc $DeadlineUtc
    }
    catch {
        $nativeFailure = $_.Exception
        $classification = if (
            $nativeFailure.Data['J6ProcessTreeCleanup'] -eq 'UNCONFIRMED' -or
            $nativeFailure.Message -eq `
                'The bounded native command cleanup could not be confirmed.' -or
            $nativeFailure.Message -eq `
                'The native confinement start cleanup could not be confirmed.') {
            'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED'
        }
        elseif ($nativeFailure -is [TimeoutException] -or
            $nativeFailure.Message -eq 'The bounded J6 cleanup deadline expired.' -or
            $nativeFailure.Message -eq `
                'The confined native target did not complete its startup handshake.' -or
            $nativeFailure.Message -eq `
                'The confined native target startup evidence was not received in time.') {
            'POSTGRES_OBSERVATION_TIMEOUT'
        }
        else {
            'POSTGRES_DOCKER_COMMAND_FAILED'
        }
        throw (New-J6SanitizedCleanupException `
                -Classification $classification `
                -InnerException $nativeFailure)
    }
    if ($result.ProcessTreeCleanup -ne 'PASS' -or
        $result.UnexpectedDescendantCleanup) {
        $failure = New-J6SanitizedCleanupException `
            -Classification 'POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED'
        $failure.Data['J6NativeExitCode'] = [int]$result.ExitCode
        throw $failure
    }
    if ($result.ExitCode -ne 0) {
        $failure = New-J6SanitizedCleanupException `
            -Classification 'POSTGRES_DOCKER_COMMAND_NONZERO_EXIT'
        $failure.Data['J6NativeExitCode'] = [int]$result.ExitCode
        throw $failure
    }
    return [string]$result.StandardOutput
}

function Invoke-J6BoundedPrimaryCleanupScalar {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )
    try {
        return Invoke-J6BoundedDockerCleanupCommand `
            -ArgumentList @(
                'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
                'sh', '-c',
                'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-align --tuples-only --quiet --set=ON_ERROR_STOP=1 --command "$1"; status=$?; if [ "$status" -ne 0 ]; then exit 86; fi',
                'sh', $Sql) `
            -DeadlineUtc $DeadlineUtc
    }
    catch {
        $classification = Get-J6SanitizedCleanupClassification `
            -Exception $_.Exception `
            -Fallback 'POSTGRES_DOCKER_COMMAND_FAILED'
        if ($classification -eq 'POSTGRES_DOCKER_COMMAND_NONZERO_EXIT' -and
            [int]$_.Exception.Data['J6NativeExitCode'] -eq 86) {
            throw (New-J6SanitizedCleanupException `
                    -Classification 'POSTGRES_SQL_COMMAND_NONZERO_EXIT' `
                    -InnerException $_.Exception)
        }
        throw
    }
}

function Invoke-J6BoundedPrimaryCleanupCount {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )

    $value = Invoke-J6BoundedPrimaryCleanupScalar `
        -Sql $Sql `
        -DeadlineUtc $DeadlineUtc
    return ConvertFrom-J6StrictNonNegativeInt64Scalar -Value $value
}

function Invoke-J6BoundedPrimaryCleanupTerminationEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )

    $value = Invoke-J6BoundedPrimaryCleanupScalar `
        -Sql $Sql `
        -DeadlineUtc $DeadlineUtc
    return ConvertFrom-J6StrictTerminationEvidenceScalar -Value $value
}

function Get-J6OwnedPostgresSessionCount {
    param(
        [Parameter(Mandatory = $true)][string]$ApplicationName,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )
    Assert-J6OwnedPostgresApplicationName -ApplicationName $ApplicationName
    return Invoke-J6BoundedPrimaryCleanupCount `
        -Sql "select count(*) from pg_stat_activity where application_name = '$ApplicationName' and pid <> pg_backend_pid()" `
        -DeadlineUtc $DeadlineUtc
}

function Confirm-J6OwnedPostgresSessionCleanup {
    param([Parameter(Mandatory = $true)][string]$ApplicationName)
    Assert-J6OwnedPostgresApplicationName -ApplicationName $ApplicationName

    if ($confirmedPostgresCleanupProofs.ContainsKey($ApplicationName)) {
        Write-Host 'J6_POSTGRES_SESSION_CLEANUP_IDEMPOTENT_REUSE=PASS'
        return $confirmedPostgresCleanupProofs[$ApplicationName]
    }

    $startedAt = [DateTime]::UtcNow
    $deadline = $startedAt.AddMilliseconds($PostgresCleanupTimeoutMilliseconds)
    $minimumObservationMilliseconds = [Math]::Min(
        2500,
        [Math]::Floor($PostgresCleanupTimeoutMilliseconds / 2))
    $stabilizationNotBefore = $startedAt.AddMilliseconds(
        $minimumObservationMilliseconds)
    $terminateSql = @"
with exact_targets as materialized (
    select pid
    from pg_stat_activity
    where application_name = '$ApplicationName'
      and pid <> pg_backend_pid()
), terminated as materialized (
    select pg_terminate_backend(pid) as was_terminated
    from exact_targets
)
select
    (select count(*) from exact_targets)::text || ',' ||
    (select count(*) from terminated where was_terminated)::text
"@
    [long]$targetedSessionAttempts = 0
    [long]$successfulTerminationSignals = 0
    [long]$lastSessionCount = 0
    $lastObservationIsFreshAfterTermination = $false
    $awaitingFreshObservationAfterZeroSignal = $false
    $consecutiveZeros = 0

    $initialSessionCount = Get-J6OwnedPostgresSessionCount `
        -ApplicationName $ApplicationName `
        -DeadlineUtc $deadline
    $lastSessionCount = $initialSessionCount
    $lastObservationIsFreshAfterTermination = $true
    if ($initialSessionCount -gt 0) {
        $terminationNow = Invoke-J6BoundedPrimaryCleanupTerminationEvidence `
            -Sql $terminateSql `
            -DeadlineUtc $deadline
        $terminationEvidence = Merge-J6PostgresTerminationEvidence `
            -TargetedSessionAttempts $targetedSessionAttempts `
            -SuccessfulTerminationSignals $successfulTerminationSignals `
            -TargetedSessionCountNow $terminationNow.TargetedSessionCount `
            -SuccessfulSignalsNow `
                $terminationNow.SuccessfulTerminationSignals
        $targetedSessionAttempts = $terminationEvidence.TargetedSessionAttempts
        $successfulTerminationSignals = `
            $terminationEvidence.SuccessfulTerminationSignals
        $lastObservationIsFreshAfterTermination = $false
        $awaitingFreshObservationAfterZeroSignal =
            $terminationNow.TargetedSessionCount -gt 0 -and
            $terminationNow.SuccessfulTerminationSignals -eq 0
    }

    while ([DateTime]::UtcNow -lt $stabilizationNotBefore -and
        [DateTime]::UtcNow -lt $deadline) {
        $sleepMilliseconds = [int][Math]::Min(
            100,
            [Math]::Max(
                1,
                [Math]::Floor(
                    ($stabilizationNotBefore - [DateTime]::UtcNow).TotalMilliseconds)))
        Start-Sleep -Milliseconds $sleepMilliseconds
    }

    while ([DateTime]::UtcNow -lt $deadline -and $consecutiveZeros -lt 3) {
        $sessionCount = Get-J6OwnedPostgresSessionCount `
                -ApplicationName $ApplicationName `
                -DeadlineUtc $deadline
        $lastSessionCount = $sessionCount
        $lastObservationIsFreshAfterTermination = $true
        if ($sessionCount -ne 0) {
            if ($awaitingFreshObservationAfterZeroSignal) {
                $remainingFailure = New-J6SanitizedCleanupException `
                    -Classification 'POSTGRES_SESSION_REMAINING'
                $remainingFailure.Data['J6TargetedSessionAttempts'] =
                    $targetedSessionAttempts
                $remainingFailure.Data['J6SuccessfulTerminationSignals'] =
                    $successfulTerminationSignals
                $remainingFailure.Data['J6RemainingSessions'] = $sessionCount
                $remainingFailure.Data['J6StableZeroObservations'] =
                    $consecutiveZeros
                throw $remainingFailure
            }
            $terminationNow =
                Invoke-J6BoundedPrimaryCleanupTerminationEvidence `
                -Sql $terminateSql `
                -DeadlineUtc $deadline
            $terminationEvidence = Merge-J6PostgresTerminationEvidence `
                -TargetedSessionAttempts $targetedSessionAttempts `
                -SuccessfulTerminationSignals $successfulTerminationSignals `
                -TargetedSessionCountNow `
                    $terminationNow.TargetedSessionCount `
                -SuccessfulSignalsNow `
                    $terminationNow.SuccessfulTerminationSignals
            $targetedSessionAttempts = `
                $terminationEvidence.TargetedSessionAttempts
            $successfulTerminationSignals = `
                $terminationEvidence.SuccessfulTerminationSignals
            $lastObservationIsFreshAfterTermination = $false
            $awaitingFreshObservationAfterZeroSignal =
                $terminationNow.TargetedSessionCount -gt 0 -and
                $terminationNow.SuccessfulTerminationSignals -eq 0
            $consecutiveZeros = 0
        }
        else {
            $awaitingFreshObservationAfterZeroSignal = $false
            $consecutiveZeros++
        }
        if ($consecutiveZeros -lt 3) {
            Start-Sleep -Milliseconds 100
        }
    }
    if ($consecutiveZeros -eq 3) {
        $proof = [pscustomobject]@{
            TargetedSessionAttempts = $targetedSessionAttempts
            SuccessfulTerminationSignals = $successfulTerminationSignals
            RemainingSessions = 0L
            StableZeroObservations = 3
            ConfirmedAtUtc = [DateTime]::UtcNow.ToString('o')
            Classification = 'PASS'
        }
        $confirmedPostgresCleanupProofs.Add($ApplicationName, $proof)
        Write-Host (
            'J6_POSTGRES_SESSION_TARGETED_ATTEMPT_COUNT=' +
            $targetedSessionAttempts)
        Write-Host (
            'J6_POSTGRES_SESSION_SUCCESSFUL_TERMINATION_SIGNAL_COUNT=' +
            $successfulTerminationSignals)
        Write-Host 'J6_POSTGRES_SESSION_REMAINING_COUNT=0'
        Write-Host 'J6_POSTGRES_SESSION_STABLE_ZERO_OBSERVATIONS=3'
        return $proof
    }

    $failureClassification = `
        Resolve-J6PostgresCleanupTimeoutClassification `
            -LastSessionCount $lastSessionCount `
            -LastObservationIsFreshAfterTermination `
                $lastObservationIsFreshAfterTermination
    $failure = New-J6SanitizedCleanupException `
        -Classification $failureClassification
    $failure.Data['J6TargetedSessionAttempts'] = $targetedSessionAttempts
    $failure.Data['J6SuccessfulTerminationSignals'] = `
        $successfulTerminationSignals
    $failure.Data['J6RemainingSessions'] = if (
        $lastObservationIsFreshAfterTermination) {
        $lastSessionCount
    }
    else {
        'UNCONFIRMED_AFTER_TERMINATION'
    }
    $failure.Data['J6StableZeroObservations'] = $consecutiveZeros
    throw $failure
}

function Write-J6NativePipelineEvidence {
    param([Parameter(Mandatory = $true)]$Result)
    $producerExit = if ($null -eq $Result.ProducerExitCode) {
        'NOT_AVAILABLE'
    }
    else {
        $Result.ProducerExitCode
    }
    $consumerExit = if ($null -eq $Result.ConsumerExitCode) {
        'NOT_AVAILABLE'
    }
    else {
        $Result.ConsumerExitCode
    }
    Write-Host "J6_NATIVE_PIPELINE_PHASE=$($Result.Phase)"
    Write-Host "J6_NATIVE_PIPELINE_RESULT=$($Result.PipelineResult)"
    Write-Host "J6_NATIVE_PIPELINE_PRODUCER_STATUS=$($Result.ProducerStatus)"
    Write-Host "J6_NATIVE_PIPELINE_PRODUCER_EXIT_CODE=$producerExit"
    Write-Host "J6_NATIVE_PIPELINE_CONSUMER_STATUS=$($Result.ConsumerStatus)"
    Write-Host "J6_NATIVE_PIPELINE_CONSUMER_EXIT_CODE=$consumerExit"
    Write-Host "J6_NATIVE_PIPELINE_COPY_STATUS=$($Result.CopyStatus)"
    Write-Host "J6_NATIVE_PIPELINE_LOCAL_CLEANUP=$($Result.LocalProcessTreeCleanup)"
}

function Complete-J6NativePipeline {
    param(
        [Parameter(Mandatory = $true)]$Result,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )
    Write-J6NativePipelineEvidence -Result $Result
    if ($Result.PipelineResult -ne 'SUCCESS' -or
        $Result.ProducerExitCode -ne 0 -or
        $Result.ConsumerExitCode -ne 0 -or
        $Result.CopyStatus -ne 'COMPLETED_TO_EOF' -or
        $Result.LocalProcessTreeCleanup -ne 'PASS') {
        throw "$FailureMessage Native result: $($Result.PipelineResult)."
    }
}

function Get-TextSha256 {
    param([AllowEmptyString()][string]$Value)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $digest = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($digest).ToLowerInvariant()
}

$flywaySql = @'
select coalesce((
    select version
    from flyway_schema_history
    where success = true
    order by installed_rank desc
    limit 1
), 'NONE')
'@
$snapshotFingerprintSql = @'
select coalesce(string_agg(
    concat_ws('|', id, logical_endpoint, received_at, payload_size_bytes,
        payload_sha256,
        case when payload_raw is null then 'ABSENT'
             else encode(sha256(payload_raw), 'hex') end,
        schema_status, payload_purged_at), E'\n' order by id), '')
from provider_snapshot
'@
$occurrenceFingerprintSql = @'
select coalesce(string_agg(
    concat_ws('|', id, snapshot_id, requested_at, received_at,
        http_status, parser_version, persistence_outcome), E'\n' order by id), '')
from provider_snapshot_occurrence
'@
$normalizedFingerprintSql = @'
select coalesce(string_agg(value, E'\n' order by value), '')
from (
    select concat_ws('|', 'STATE', observation.id, observation.source_snapshot_id,
        observation.source_reference, observation.source_payload_sha256,
        observation.parser_version, observation.normalized_sha256) as value
    from canonical_event_observation observation
    union all
    select concat_ws('|', 'DETAILS', observation.id, observation.source_snapshot_id,
        observation.source_reference, observation.source_payload_sha256,
        observation.parser_version, observation.normalized_sha256) as value
    from event_detail_observation observation
    union all
    select concat_ws('|', observation.endpoint_type, observation.id,
        observation.source_snapshot_id, observation.source_reference,
        observation.source_payload_sha256, observation.parser_version,
        observation.normalized_sha256) as value
    from j5_event_data_observation observation
    union all
    select 'LINEUP_SIDE|' || to_jsonb(side)::text as value
    from j5_event_lineup_side side
    union all
    select 'LINEUP_PLAYER|' || to_jsonb(player)::text as value
    from j5_event_lineup_player player
) normalized
'@
$j8BenchmarkFingerprintSql = @'
select coalesce(string_agg(value, E'\n' order by value), '')
from (
    select 'CAMPAIGN|' || to_jsonb(campaign)::text as value
    from j8_benchmark_campaign campaign
    union all
    select 'UNIT|' || to_jsonb(unit)::text as value
    from j8_benchmark_unit unit
    union all
    select 'ATTEMPT|' || to_jsonb(attempt)::text as value
    from j8_provider_call_attempt attempt
    union all
    select 'UNIT_RESULT|' || to_jsonb(unit_result)::text as value
    from j8_benchmark_unit_result unit_result
    union all
    select 'CAMPAIGN_RESULT|' || to_jsonb(campaign_result)::text as value
    from j8_benchmark_campaign_result campaign_result
) j8_evidence
'@
$j7DeliveryLedgerFingerprintSql = @'
select coalesce(string_agg(value, E'\n' order by value), '')
from (
    select 'J7_DELIVERY|' || to_jsonb(delivery)::text as value
    from j7_delivery delivery
    union all
    select 'J7_DELIVERY_ATTEMPT|' || to_jsonb(attempt)::text as value
    from j7_delivery_attempt attempt
    union all
    select 'J7_DELIVERY_ATTEMPT_RESULT|' || to_jsonb(attempt_result)::text as value
    from j7_delivery_attempt_result attempt_result
    union all
    select 'J7_PROVIDER_OWNER_GO_GRANT|' || to_jsonb(owner_go)::text as value
    from j7_provider_delivery_owner_go_grant owner_go
    union all
    select 'J7_PROVIDER_OWNER_GO_REVOCATION|' || to_jsonb(revocation)::text as value
    from j7_provider_delivery_owner_go_revocation revocation
    union all
    select 'J7_PROVIDER_OWNER_GO_CONSUMPTION|' || to_jsonb(consumption)::text as value
    from j7_provider_delivery_owner_go_consumption consumption
) j7_delivery_ledger
'@
$liveLedgerFingerprintSql = @'
select coalesce(string_agg(value, E'\n' order by value), '')
from (
    select 'LIVE_CAMPAIGN|' || to_jsonb(t)::text as value from live_campaign t
    union all select 'LIVE_EVENT|' || to_jsonb(t)::text from live_event t
    union all select 'LIVE_CALL|' || to_jsonb(t)::text from live_call t
    union all select 'LIVE_DISPATCH|' || to_jsonb(t)::text from live_call_dispatch t
    union all select 'LIVE_RECEIPT|' || to_jsonb(t)::text from live_call_receipt t
    union all select 'LIVE_RESULT|' || to_jsonb(t)::text from live_call_result t
    union all select 'LIVE_TRANSITION|' || to_jsonb(t)::text from live_transition t
    union all select 'LIVE_GROUPED_POLICY|' || to_jsonb(t)::text from live_grouped_policy t
    union all select 'LIVE_CALL_GROUP|' || to_jsonb(t)::text from live_call_group t
    union all select 'LIVE_FAMILY_SCHEDULE|' || to_jsonb(t)::text from live_family_schedule t
    union all select 'LIVE_FAMILY_SCHEDULE_REVISION|' || to_jsonb(t)::text from live_family_schedule_revision t
    union all select 'PROVIDER_GUARD|' || to_jsonb(t)::text from provider_campaign_guard t
    union all select 'PROVIDER_RESILIENCE_STATE|' || to_jsonb(t)::text from provider_resilience_state t
    union all select 'PROVIDER_DEPARTURE_RESERVATION|' || to_jsonb(t)::text from provider_departure_reservation t
    union all select 'PROVIDER_DEPARTURE_COMPLETION|' || to_jsonb(t)::text from provider_departure_completion t
    union all select 'PROVIDER_RESILIENCE_EVENT|' || to_jsonb(t)::text from provider_resilience_event t
    union all select 'LIVE_ATTEMPT_TRANSPORT_DIAGNOSTIC|' || to_jsonb(t)::text from live_attempt_transport_diagnostic t
    union all select 'LIVE_CAMPAIGN_DIAGNOSTIC|' || to_jsonb(t)::text from live_campaign_diagnostic t
) live_evidence
'@

Push-Location $repositoryRoot
$operatorCancellation = New-J6ConsoleCancellationRegistration
$partialPath = Join-Path $destinationDirectory `
    ([IO.Path]::GetFileName($destinationPath) + '.partial-' + [Guid]::NewGuid().ToString('N'))
$manifestStagingPath = $manifestPath + '.partial-' + [Guid]::NewGuid().ToString('N')
$restoreDatabase = 'sofascore_j6_restore_' + [Guid]::NewGuid().ToString('N')
$restoreDatabaseCreated = $false
$restoreDatabaseCleanupArmed = $false
$backupApplicationName = $null
$restoreApplicationName = $null
$qualificationDataReady = $false
$destinationOwned = $false
$manifestOwned = $false
$publicationComplete = $false
$manifest = $null
$operationFailure = $null
$terminalFailure = $null
$confirmedPostgresCleanupProofs = `
    [Collections.Generic.Dictionary[string, object]]::new(
        [StringComparer]::Ordinal)
Write-Host "J6_NATIVE_PROCESS_CLEANUP_TIMEOUT_MILLISECONDS=$PipelineCleanupTimeoutMilliseconds"
Write-Host "J6_POSTGRES_CLEANUP_TIMEOUT_MILLISECONDS=$PostgresCleanupTimeoutMilliseconds"
try {
    try {
    Assert-J6OperatorNotCancelled
    & $dockerExecutable compose --env-file .env config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw 'compose.yaml validation failed.'
    }
    $connectorState = Invoke-PrimaryScalar -Sql `
        "select case when not network_enabled and circuit_state = 'LOCKED' then 'SAFE' else 'UNSAFE' end from connector_control where singleton_id = 1"
    if ($connectorState -ne 'SAFE') {
        throw 'The persisted connector control must be disabled and LOCKED before backup.'
    }

    $sourceFlywayVersion = Invoke-PrimaryScalar -Sql $flywaySql
    if ($sourceFlywayVersion -cne '44') {
        throw 'Flyway V44 must be applied before the J6 backup/restore qualification.'
    }
    $providerGuardState = Invoke-PrimaryScalar -Sql 'select state from provider_campaign_guard where singleton_id=1'
    $activeLiveCount = [long](Invoke-PrimaryScalar -Sql "select count(*) from live_campaign where state in ('RUNNING','CLEANUP_REQUIRED')")
    if ($providerGuardState -cne 'FREE' -or $activeLiveCount -ne 0) {
        throw 'Stop provider campaigns and verify cleanup before J6 backup; the durable provider guard must be FREE.'
    }
    $coverageReceivedSql = @'
select coalesce(
    to_char(max(received_at) at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    '1970-01-01T00:00:00Z')
from provider_snapshot
'@
    $source = [ordered]@{
        flywayVersion = $sourceFlywayVersion
        snapshotCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from provider_snapshot')
        occurrenceCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from provider_snapshot_occurrence')
        canonicalObservationCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from canonical_event_observation')
        detailObservationCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from event_detail_observation')
        eventDataObservationCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j5_event_data_observation')
        purgeAuditCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j6_raw_payload_purge_audit')
        j8CampaignCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j8_benchmark_campaign')
        j8UnitCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j8_benchmark_unit')
        j8ProviderAttemptCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j8_provider_call_attempt')
        j8UnitResultCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j8_benchmark_unit_result')
        j8CampaignResultCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j8_benchmark_campaign_result')
        j7DeliveryCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j7_delivery')
        j7DeliveryAttemptCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j7_delivery_attempt')
        j7DeliveryAttemptResultCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j7_delivery_attempt_result')
        j7ProviderOwnerGoGrantCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j7_provider_delivery_owner_go_grant')
        j7ProviderOwnerGoRevocationCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j7_provider_delivery_owner_go_revocation')
        j7ProviderOwnerGoConsumptionCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from j7_provider_delivery_owner_go_consumption')
        liveCampaignCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_campaign')
        liveEventCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_event')
        liveCallCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_call')
        liveDispatchCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_call_dispatch')
        liveReceiptCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_call_receipt')
        liveResultCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_call_result')
        liveTransitionCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_transition')
        providerResilienceStateCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from provider_resilience_state')
        providerDepartureReservationCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from provider_departure_reservation')
        providerDepartureCompletionCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from provider_departure_completion')
        providerResilienceEventCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from provider_resilience_event')
        liveAttemptTransportDiagnosticCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_attempt_transport_diagnostic')
        liveCampaignDiagnosticCount = [long](Invoke-PrimaryScalar -Sql 'select count(*) from live_campaign_diagnostic')
        providerGuardState = $providerGuardState
        activeLiveCount = $activeLiveCount
        coverageMaxSnapshotId = [long](Invoke-PrimaryScalar -Sql 'select coalesce(max(id), 0) from provider_snapshot')
        coverageReceivedAt = Invoke-PrimaryScalar -Sql $coverageReceivedSql
        rawPayloadIntegrityFailures = [long](Invoke-PrimaryScalar -Sql "select count(*) from provider_snapshot where payload_raw is not null and encode(sha256(payload_raw), 'hex') <> payload_sha256")
        snapshotMetadataSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $snapshotFingerprintSql)
        occurrenceSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $occurrenceFingerprintSql)
        normalizedProvenanceSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $normalizedFingerprintSql)
        j8BenchmarkSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $j8BenchmarkFingerprintSql)
        j7DeliveryLedgerSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $j7DeliveryLedgerFingerprintSql)
        liveLedgerSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $liveLedgerFingerprintSql)
    }
    if ($source.rawPayloadIntegrityFailures -ne 0) {
        throw 'At least one retained payload does not match its persisted SHA-256.'
    }
    if ($source.providerResilienceStateCount -ne 1) {
        throw 'The durable provider resilience singleton must be present before backup qualification.'
    }

    Write-Host 'J6_BACKUP_ENCRYPTION=INTERACTIVE_PASSPHRASE_REQUIRED'
    Assert-J6OperatorNotCancelled
    $backupApplicationName = 'j6_backup_' + [Guid]::NewGuid().ToString('N')
    Assert-J6OwnedPostgresApplicationName -ApplicationName $backupApplicationName
    $backupPipeline = Invoke-J6NativeBinaryPipeline `
        -Phase BACKUP_ENCRYPTION `
        -ProducerFilePath $dockerExecutable `
        -ProducerArgumentList @(
            'compose', '--env-file', '.env', 'exec', '-T',
            '-e', "PGAPPNAME=$backupApplicationName",
            'postgres', 'sh', '-c',
            'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --no-owner --no-privileges') `
        -ConsumerFilePath $ageExecutable `
        -ConsumerArgumentList @('-p', '-o', $partialPath) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds $PipelineTimeoutSeconds `
        -CleanupTimeoutMilliseconds $PipelineCleanupTimeoutMilliseconds `
        -CancellationToken $operatorCancellation.Token
    Complete-J6NativePipeline `
        -Result $backupPipeline `
        -FailureMessage 'Encrypted pg_dump creation failed.'
    [void](Confirm-J6OwnedPostgresSessionCleanup `
        -ApplicationName $backupApplicationName)
    Write-Host 'J6_NATIVE_PIPELINE_REMOTE_SESSION_CLEANUP=PASS'
    Assert-J6OperatorNotCancelled
    if (-not (Test-Path -LiteralPath $partialPath -PathType Leaf) -or
        (Get-Item -LiteralPath $partialPath).Length -le 0) {
        throw 'Encrypted pg_dump creation did not produce a non-empty partial archive.'
    }
    $cipherSha256 = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant()

    $manifest = [ordered]@{
        formatVersion = 1
        purpose = 'J6_RAW_PAYLOAD_RETENTION_BACKUP'
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
        encryptedBackupFileName = [IO.Path]::GetFileName($destinationPath)
        cipherSha256 = $cipherSha256
        source = $source
        restoreQualified = $false
        qualifiedAt = $null
    }

    if ($restoreDatabase -cnotmatch '^sofascore_j6_restore_[a-f0-9]{32}$') {
        throw 'Generated restore database name is unsafe.'
    }
    $ownershipDeadline = [DateTime]::UtcNow.AddMilliseconds($PostgresCleanupTimeoutMilliseconds)
    $existingRestoreDatabaseCount = Invoke-J6BoundedPrimaryCleanupCount `
        -Sql "select count(*) from pg_database where datname = '$restoreDatabase'" `
        -DeadlineUtc $ownershipDeadline
    if ($existingRestoreDatabaseCount -ne 0) {
        throw 'The generated temporary restore database name is not unowned.'
    }
    $restoreDatabaseCleanupArmed = $true
    $createDeadline = [DateTime]::UtcNow.AddSeconds($PipelineTimeoutSeconds)
    $createResult = Invoke-J6BoundedDockerCleanupCommand `
        -ArgumentList @(
            'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
            'sh', '-c', 'createdb --username "$POSTGRES_USER" "$1"',
            'sh', $restoreDatabase) `
        -DeadlineUtc $createDeadline
    if ($createResult.Length -gt 0) {
        throw 'Temporary restore database creation produced unexpected output.'
    }
    $createdDatabaseCount = Invoke-J6BoundedPrimaryCleanupCount `
        -Sql "select count(*) from pg_database where datname = '$restoreDatabase'" `
        -DeadlineUtc ([DateTime]::UtcNow.AddMilliseconds(
                $PostgresCleanupTimeoutMilliseconds))
    if ($createdDatabaseCount -ne 1) {
        throw 'Temporary restore database creation failed.'
    }
    $restoreDatabaseCreated = $true

    Write-Host 'J6_RESTORE_DECRYPTION=INTERACTIVE_PASSPHRASE_REQUIRED'
    Assert-J6OperatorNotCancelled
    $restoreApplicationName = 'j6_restore_' + [Guid]::NewGuid().ToString('N')
    Assert-J6OwnedPostgresApplicationName -ApplicationName $restoreApplicationName
    $restorePipeline = Invoke-J6NativeBinaryPipeline `
        -Phase RESTORE_DECRYPTION `
        -ProducerFilePath $ageExecutable `
        -ProducerArgumentList @('-d', $partialPath) `
        -ConsumerFilePath $dockerExecutable `
        -ConsumerArgumentList @(
            'compose', '--env-file', '.env', 'exec', '-T',
            '-e', "PGAPPNAME=$restoreApplicationName",
            'postgres', 'sh', '-c',
            'exec pg_restore --username "$POSTGRES_USER" --dbname "$1" --no-owner --no-privileges --exit-on-error',
            'sh', $restoreDatabase) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds $PipelineTimeoutSeconds `
        -CleanupTimeoutMilliseconds $PipelineCleanupTimeoutMilliseconds `
        -CancellationToken $operatorCancellation.Token
    Complete-J6NativePipeline `
        -Result $restorePipeline `
        -FailureMessage 'Encrypted backup restore failed.'
    [void](Confirm-J6OwnedPostgresSessionCleanup `
        -ApplicationName $restoreApplicationName)
    Write-Host 'J6_NATIVE_PIPELINE_REMOTE_SESSION_CLEANUP=PASS'
    Assert-J6OperatorNotCancelled

    $restored = [ordered]@{
        flywayVersion = Invoke-RestoreScalar -Database $restoreDatabase -Sql $flywaySql
        snapshotCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_snapshot')
        occurrenceCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_snapshot_occurrence')
        canonicalObservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from canonical_event_observation')
        detailObservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from event_detail_observation')
        eventDataObservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j5_event_data_observation')
        purgeAuditCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j6_raw_payload_purge_audit')
        j8CampaignCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j8_benchmark_campaign')
        j8UnitCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j8_benchmark_unit')
        j8ProviderAttemptCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j8_provider_call_attempt')
        j8UnitResultCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j8_benchmark_unit_result')
        j8CampaignResultCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j8_benchmark_campaign_result')
        j7DeliveryCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j7_delivery')
        j7DeliveryAttemptCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j7_delivery_attempt')
        j7DeliveryAttemptResultCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j7_delivery_attempt_result')
        j7ProviderOwnerGoGrantCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j7_provider_delivery_owner_go_grant')
        j7ProviderOwnerGoRevocationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j7_provider_delivery_owner_go_revocation')
        j7ProviderOwnerGoConsumptionCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j7_provider_delivery_owner_go_consumption')
        liveCampaignCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_campaign')
        liveEventCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_event')
        liveCallCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_call')
        liveDispatchCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_call_dispatch')
        liveReceiptCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_call_receipt')
        liveResultCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_call_result')
        liveTransitionCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_transition')
        providerResilienceStateCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_resilience_state')
        providerDepartureReservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_departure_reservation')
        providerDepartureCompletionCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_departure_completion')
        providerResilienceEventCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_resilience_event')
        liveAttemptTransportDiagnosticCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_attempt_transport_diagnostic')
        liveCampaignDiagnosticCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from live_campaign_diagnostic')
        providerGuardState = Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select state from provider_campaign_guard where singleton_id=1'
        activeLiveCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql "select count(*) from live_campaign where state in ('RUNNING','CLEANUP_REQUIRED')")
        coverageMaxSnapshotId = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select coalesce(max(id), 0) from provider_snapshot')
        coverageReceivedAt = Invoke-RestoreScalar -Database $restoreDatabase -Sql $coverageReceivedSql
        rawPayloadIntegrityFailures = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql "select count(*) from provider_snapshot where payload_raw is not null and encode(sha256(payload_raw), 'hex') <> payload_sha256")
        snapshotMetadataSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $snapshotFingerprintSql)
        occurrenceSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $occurrenceFingerprintSql)
        normalizedProvenanceSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $normalizedFingerprintSql)
        j8BenchmarkSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $j8BenchmarkFingerprintSql)
        j7DeliveryLedgerSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $j7DeliveryLedgerFingerprintSql)
        liveLedgerSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $liveLedgerFingerprintSql)
    }
    foreach ($key in $source.Keys) {
        if ($source[$key].ToString() -cne $restored[$key].ToString()) {
            throw "Restore qualification mismatch: $key"
        }
    }
    $qualificationDataReady = $true
    }
    catch {
        $operationFailure = $_.Exception
    }
    finally {
        $cleanupFailures = [Collections.Generic.List[Exception]]::new()
        $cleanupClassifications = [Collections.Generic.List[string]]::new()
        foreach ($applicationName in @($backupApplicationName, $restoreApplicationName)) {
            if ([string]::IsNullOrWhiteSpace($applicationName)) {
                continue
            }
            try {
                [void](Confirm-J6OwnedPostgresSessionCleanup `
                    -ApplicationName $applicationName)
            }
            catch {
                $cleanupFailure = $_.Exception
                $classification = Get-J6SanitizedCleanupClassification `
                    -Exception $cleanupFailure `
                    -Fallback 'POSTGRES_SESSION_CLEANUP_UNCONFIRMED'
                if ($classification -eq 'POSTGRES_SESSION_CLEANUP_UNCONFIRMED') {
                    $cleanupFailure = New-J6SanitizedCleanupException `
                        -Classification $classification `
                        -InnerException $cleanupFailure
                }
                $cleanupFailures.Add($cleanupFailure)
                $cleanupClassifications.Add($classification)
            }
        }

        if ($restoreDatabaseCleanupArmed) {
            try {
                if ($restoreDatabase -cnotmatch '^sofascore_j6_restore_[a-f0-9]{32}$') {
                    throw 'The armed temporary restore database name is unsafe.'
                }
                $dropDeadline = [DateTime]::UtcNow.AddMilliseconds(
                    $PostgresCleanupTimeoutMilliseconds)
                [void](Invoke-J6BoundedDockerCleanupCommand `
                    -ArgumentList @(
                        'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
                        'sh', '-c',
                        'dropdb --username "$POSTGRES_USER" --force --if-exists "$1"',
                        'sh', $restoreDatabase) `
                    -DeadlineUtc $dropDeadline)
                $remainingDatabaseCount = Invoke-J6BoundedPrimaryCleanupCount `
                    -Sql "select count(*) from pg_database where datname = '$restoreDatabase'" `
                    -DeadlineUtc $dropDeadline
                if ($remainingDatabaseCount -ne 0) {
                    throw 'The owned temporary restore database still exists after cleanup.'
                }
                $restoreDatabaseCleanupArmed = $false
                if ($restoreDatabaseCreated) {
                    Write-Host 'J6_TEMPORARY_RESTORE_DATABASE_CLEANUP=PASS'
                }
            }
            catch {
                $cleanupFailure = New-J6SanitizedCleanupException `
                    -Classification 'TEMPORARY_RESTORE_DATABASE_CLEANUP_UNCONFIRMED' `
                    -InnerException $_.Exception
                $cleanupFailures.Add($cleanupFailure)
                $cleanupClassifications.Add(
                    'TEMPORARY_RESTORE_DATABASE_CLEANUP_UNCONFIRMED')
            }
        }
        if ($QualificationInjectCleanupFailureAfterSuccessfulCleanup -and
            $cleanupFailures.Count -eq 0) {
            $cleanupFailure = New-J6SanitizedCleanupException `
                -Classification 'QUALIFICATION_INJECTED_CLEANUP_FAILURE'
            $cleanupFailures.Add($cleanupFailure)
            $cleanupClassifications.Add('QUALIFICATION_INJECTED_CLEANUP_FAILURE')
        }
        if ($cleanupFailures.Count -ne 0) {
            if ($null -ne $operationFailure) {
                $primaryFailure = New-J6SanitizedCleanupException `
                    -Classification 'PRIMARY_OPERATION_FAILED_DURING_CLEANUP' `
                    -InnerException $operationFailure
                $cleanupFailures.Insert(0, $primaryFailure)
                $cleanupClassifications.Add(
                    'PRIMARY_OPERATION_FAILED_DURING_CLEANUP')
            }
            $failureClasses = @($cleanupClassifications | Sort-Object -Unique)
            $aggregateFailure = [AggregateException]::new(
                'J6_CLEANUP_CAUSES_PRESERVED',
                [Exception[]]$cleanupFailures.ToArray())
            throw [InvalidOperationException]::new(
                ('J6_FAIL_CLOSED_CLEANUP=FAILED;' +
                    'J6_CLEANUP_FAILURE_CLASSES=' + ($failureClasses -join ',')),
                $aggregateFailure)
        }
    }

    if ($null -ne $operationFailure) {
        throw $operationFailure
    }

    if (-not $qualificationDataReady) {
        throw 'The J6 backup cannot be published without complete restore evidence.'
    }
    Assert-J6OperatorNotCancelled
    $manifest.restoreQualified = $true
    $manifest.qualifiedAt = (Get-Date).ToUniversalTime().ToString('o')
    $manifest['restored'] = $restored
    Move-Item -LiteralPath $partialPath -Destination $destinationPath
    $destinationOwned = $true
    $manifest | ConvertTo-Json -Depth 6 |
        Set-Content -LiteralPath $manifestStagingPath -Encoding utf8NoBOM
    Move-Item -LiteralPath $manifestStagingPath -Destination $manifestPath
    $manifestOwned = $true
    $publicationComplete = $true

    $manifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host 'J6_BACKUP_RESULT=QUALIFIED'
    Write-Host "J6_BACKUP_CIPHER_SHA256=$cipherSha256"
    Write-Host "J6_BACKUP_MANIFEST_SHA256=$manifestSha256"
    Write-Host "J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=$($source.coverageMaxSnapshotId)"
    Write-Host "J6_BACKUP_COVERAGE_RECEIVED_AT=$($source.coverageReceivedAt)"
}
catch {
    $terminalFailure = $_.Exception
}
finally {
    $fileCleanupFailures = [Collections.Generic.List[Exception]]::new()
    $fileCleanupClassifications = [Collections.Generic.List[string]]::new()
    if (Test-Path -LiteralPath $partialPath) {
        try {
            Remove-Item -LiteralPath $partialPath -Force
        }
        catch {
            $fileCleanupFailures.Add((New-J6SanitizedCleanupException `
                        -Classification 'FILE_CLEANUP_UNCONFIRMED' `
                        -InnerException $_.Exception))
            $fileCleanupClassifications.Add('FILE_CLEANUP_UNCONFIRMED')
        }
    }
    if (Test-Path -LiteralPath $manifestStagingPath) {
        try {
            Remove-Item -LiteralPath $manifestStagingPath -Force
        }
        catch {
            $fileCleanupFailures.Add((New-J6SanitizedCleanupException `
                        -Classification 'FILE_CLEANUP_UNCONFIRMED' `
                        -InnerException $_.Exception))
            $fileCleanupClassifications.Add('FILE_CLEANUP_UNCONFIRMED')
        }
    }
    if (-not $publicationComplete -and $manifestOwned -and
        (Test-Path -LiteralPath $manifestPath)) {
        try {
            Remove-Item -LiteralPath $manifestPath -Force
        }
        catch {
            $fileCleanupFailures.Add((New-J6SanitizedCleanupException `
                        -Classification 'FILE_CLEANUP_UNCONFIRMED' `
                        -InnerException $_.Exception))
            $fileCleanupClassifications.Add('FILE_CLEANUP_UNCONFIRMED')
        }
    }
    if (-not $publicationComplete -and $destinationOwned -and
        (Test-Path -LiteralPath $destinationPath)) {
        try {
            Remove-Item -LiteralPath $destinationPath -Force
        }
        catch {
            $fileCleanupFailures.Add((New-J6SanitizedCleanupException `
                        -Classification 'FILE_CLEANUP_UNCONFIRMED' `
                        -InnerException $_.Exception))
            $fileCleanupClassifications.Add('FILE_CLEANUP_UNCONFIRMED')
        }
    }
    try {
        Pop-Location
    }
    catch {
        $fileCleanupFailures.Add((New-J6SanitizedCleanupException `
                    -Classification 'LOCAL_RESOURCE_CLEANUP_UNCONFIRMED' `
                    -InnerException $_.Exception))
        $fileCleanupClassifications.Add(
            'LOCAL_RESOURCE_CLEANUP_UNCONFIRMED')
    }
    try {
        $operatorCancellation.Dispose()
    }
    catch {
        $fileCleanupFailures.Add((New-J6SanitizedCleanupException `
                    -Classification 'LOCAL_RESOURCE_CLEANUP_UNCONFIRMED' `
                    -InnerException $_.Exception))
        $fileCleanupClassifications.Add(
            'LOCAL_RESOURCE_CLEANUP_UNCONFIRMED')
    }
    if ($fileCleanupFailures.Count -ne 0) {
        if ($null -ne $terminalFailure) {
            $fileCleanupFailures.Insert(0, (
                    New-J6SanitizedCleanupException `
                        -Classification 'PRIMARY_OPERATION_FAILED_DURING_CLEANUP' `
                        -InnerException $terminalFailure))
            $fileCleanupClassifications.Add(
                'PRIMARY_OPERATION_FAILED_DURING_CLEANUP')
        }
        $failureClasses = @(
            $fileCleanupClassifications | Sort-Object -Unique)
        $terminalFailure = [InvalidOperationException]::new(
            ('J6_FAIL_CLOSED_FILE_CLEANUP=FAILED;' +
                'J6_CLEANUP_FAILURE_CLASSES=' +
                ($failureClasses -join ',')),
            [AggregateException]::new(
                'J6_FILE_CLEANUP_CAUSES_PRESERVED',
                [Exception[]]$fileCleanupFailures.ToArray()))
    }
}
if ($null -ne $terminalFailure) {
    throw $terminalFailure
}
