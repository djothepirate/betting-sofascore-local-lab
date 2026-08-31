[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$AgePath,

    [ValidateRange(30, 3600)]
    [int]$PipelineTimeoutSeconds = 300,

    [ValidateRange(100, 30000)]
    [int]$PipelineCleanupTimeoutMilliseconds = 5000,

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
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $dockerCommand) {
    throw 'Docker is required.'
}
$dockerExecutable = [IO.Path]::GetFullPath($dockerCommand.Source)
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
        'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-align --tuples-only --quiet --command "$1"' `
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
        'psql --username "$POSTGRES_USER" --dbname "$1" --no-align --tuples-only --quiet --command "$2"' `
        sh $Database $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'A restored database verification query failed.'
    }
    return (($value | ForEach-Object { $_.ToString() }) -join "`n").TrimEnd()
}

function Assert-J6OwnedPostgresApplicationName {
    param([Parameter(Mandatory = $true)][string]$ApplicationName)
    if ($ApplicationName -notmatch '^j6_(backup|restore)_[a-f0-9]{32}$') {
        throw 'The owned PostgreSQL application name is unsafe.'
    }
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
    $remaining = Get-J6RemainingCleanupMilliseconds -DeadlineUtc $DeadlineUtc
    if ($remaining -lt 200) {
        throw 'The bounded Docker cleanup command has no safe execution/cleanup budget remaining.'
    }
    $nativeCleanupBudget = [int][Math]::Min(
        $PipelineCleanupTimeoutMilliseconds,
        [Math]::Max(100, [Math]::Floor($remaining / 5)))
    $nativeExecutionBudget = $remaining - $nativeCleanupBudget
    $result = Invoke-J6BoundedNativeCommand `
        -FilePath $dockerExecutable `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $repositoryRoot `
        -TimeoutMilliseconds $nativeExecutionBudget `
        -CleanupTimeoutMilliseconds $nativeCleanupBudget
    if ($result.ExitCode -ne 0 -or
        $result.ProcessTreeCleanup -ne 'PASS' -or
        $result.UnexpectedDescendantCleanup) {
        throw 'A bounded Docker cleanup command did not complete cleanly.'
    }
    return $result.StandardOutput.TrimEnd()
}

function Invoke-J6BoundedPrimaryCleanupScalar {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )
    return Invoke-J6BoundedDockerCleanupCommand `
        -ArgumentList @(
            'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
            'sh', '-c',
            'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-align --tuples-only --quiet --command "$1"',
            'sh', $Sql) `
        -DeadlineUtc $DeadlineUtc
}

function Get-J6OwnedPostgresSessionCount {
    param(
        [Parameter(Mandatory = $true)][string]$ApplicationName,
        [Parameter(Mandatory = $true)][DateTime]$DeadlineUtc
    )
    Assert-J6OwnedPostgresApplicationName -ApplicationName $ApplicationName
    return [long](Invoke-J6BoundedPrimaryCleanupScalar `
        -Sql "select count(*) from pg_stat_activity where application_name = '$ApplicationName' and pid <> pg_backend_pid()" `
        -DeadlineUtc $DeadlineUtc)
}

function Confirm-J6OwnedPostgresSessionCleanup {
    param([Parameter(Mandatory = $true)][string]$ApplicationName)
    Assert-J6OwnedPostgresApplicationName -ApplicationName $ApplicationName

    $startedAt = [DateTime]::UtcNow
    $deadline = $startedAt.AddMilliseconds($PipelineCleanupTimeoutMilliseconds)
    $minimumObservationMilliseconds = [Math]::Min(
        2500,
        [Math]::Floor($PipelineCleanupTimeoutMilliseconds / 2))
    $stabilizationNotBefore = $startedAt.AddMilliseconds(
        $minimumObservationMilliseconds)
    $terminateSql = @"
select count(*)
from (
    select pg_terminate_backend(pid)
    from pg_stat_activity
    where application_name = '$ApplicationName'
      and pid <> pg_backend_pid()
) terminated
"@
    $consecutiveZeros = 0
    while ([DateTime]::UtcNow -lt $deadline -and $consecutiveZeros -lt 3) {
        $sessionCount = Get-J6OwnedPostgresSessionCount `
                -ApplicationName $ApplicationName `
                -DeadlineUtc $deadline
        if ($sessionCount -ne 0) {
            [void](Invoke-J6BoundedPrimaryCleanupScalar `
                -Sql $terminateSql `
                -DeadlineUtc $deadline)
            $consecutiveZeros = 0
        }
        else {
            if ([DateTime]::UtcNow -ge $stabilizationNotBefore) {
                $consecutiveZeros++
            }
            else {
                $consecutiveZeros = 0
            }
        }
        if ($consecutiveZeros -lt 3) {
            Start-Sleep -Milliseconds 100
        }
    }
    if ($consecutiveZeros -eq 3) {
        return
    }

    throw 'An exactly owned PostgreSQL backup/restore session survived bounded cleanup.'
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
    if ($sourceFlywayVersion -cne '28') {
        throw 'Flyway V28 must be applied before the J6 backup/restore qualification.'
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
        coverageMaxSnapshotId = [long](Invoke-PrimaryScalar -Sql 'select coalesce(max(id), 0) from provider_snapshot')
        coverageReceivedAt = Invoke-PrimaryScalar -Sql $coverageReceivedSql
        rawPayloadIntegrityFailures = [long](Invoke-PrimaryScalar -Sql "select count(*) from provider_snapshot where payload_raw is not null and encode(sha256(payload_raw), 'hex') <> payload_sha256")
        snapshotMetadataSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $snapshotFingerprintSql)
        occurrenceSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $occurrenceFingerprintSql)
        normalizedProvenanceSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $normalizedFingerprintSql)
        j8BenchmarkSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $j8BenchmarkFingerprintSql)
    }
    if ($source.rawPayloadIntegrityFailures -ne 0) {
        throw 'At least one retained payload does not match its persisted SHA-256.'
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
    Confirm-J6OwnedPostgresSessionCleanup -ApplicationName $backupApplicationName
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

    if ($restoreDatabase -notmatch '^sofascore_j6_restore_[a-f0-9]{32}$') {
        throw 'Generated restore database name is unsafe.'
    }
    $ownershipDeadline = [DateTime]::UtcNow.AddMilliseconds($PipelineCleanupTimeoutMilliseconds)
    $existingRestoreDatabaseCount = [long](Invoke-J6BoundedPrimaryCleanupScalar `
        -Sql "select count(*) from pg_database where datname = '$restoreDatabase'" `
        -DeadlineUtc $ownershipDeadline)
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
    $createdDatabaseCount = [long](Invoke-J6BoundedPrimaryCleanupScalar `
        -Sql "select count(*) from pg_database where datname = '$restoreDatabase'" `
        -DeadlineUtc ([DateTime]::UtcNow.AddMilliseconds($PipelineCleanupTimeoutMilliseconds)))
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
    Confirm-J6OwnedPostgresSessionCleanup -ApplicationName $restoreApplicationName
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
        coverageMaxSnapshotId = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select coalesce(max(id), 0) from provider_snapshot')
        coverageReceivedAt = Invoke-RestoreScalar -Database $restoreDatabase -Sql $coverageReceivedSql
        rawPayloadIntegrityFailures = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql "select count(*) from provider_snapshot where payload_raw is not null and encode(sha256(payload_raw), 'hex') <> payload_sha256")
        snapshotMetadataSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $snapshotFingerprintSql)
        occurrenceSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $occurrenceFingerprintSql)
        normalizedProvenanceSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $normalizedFingerprintSql)
        j8BenchmarkSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $j8BenchmarkFingerprintSql)
    }
    foreach ($key in $source.Keys) {
        if ($source[$key].ToString() -cne $restored[$key].ToString()) {
            throw "Restore qualification mismatch: $key"
        }
    }
    $qualificationDataReady = $true
    }
    finally {
        $cleanupFailures = [Collections.Generic.List[string]]::new()
        foreach ($applicationName in @($backupApplicationName, $restoreApplicationName)) {
            if ([string]::IsNullOrWhiteSpace($applicationName)) {
                continue
            }
            try {
                Confirm-J6OwnedPostgresSessionCleanup -ApplicationName $applicationName
            }
            catch {
                $cleanupFailures.Add(
                    "The exactly owned PostgreSQL session cleanup failed for $applicationName.")
            }
        }

        if ($restoreDatabaseCleanupArmed) {
            try {
                if ($restoreDatabase -notmatch '^sofascore_j6_restore_[a-f0-9]{32}$') {
                    throw 'The armed temporary restore database name is unsafe.'
                }
                $dropDeadline = [DateTime]::UtcNow.AddMilliseconds(
                    $PipelineCleanupTimeoutMilliseconds)
                [void](Invoke-J6BoundedDockerCleanupCommand `
                    -ArgumentList @(
                        'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
                        'sh', '-c',
                        'dropdb --username "$POSTGRES_USER" --force --if-exists "$1"',
                        'sh', $restoreDatabase) `
                    -DeadlineUtc $dropDeadline)
                $remainingDatabaseCount = [long](Invoke-J6BoundedPrimaryCleanupScalar `
                    -Sql "select count(*) from pg_database where datname = '$restoreDatabase'" `
                    -DeadlineUtc ([DateTime]::UtcNow.AddMilliseconds(
                            $PipelineCleanupTimeoutMilliseconds)))
                if ($remainingDatabaseCount -ne 0) {
                    throw 'The owned temporary restore database still exists after cleanup.'
                }
                $restoreDatabaseCleanupArmed = $false
                if ($restoreDatabaseCreated) {
                    Write-Host 'J6_TEMPORARY_RESTORE_DATABASE_CLEANUP=PASS'
                }
            }
            catch {
                $cleanupFailures.Add(
                    'The exactly owned temporary restore database cleanup could not be confirmed.')
            }
        }
        if ($QualificationInjectCleanupFailureAfterSuccessfulCleanup -and
            $cleanupFailures.Count -eq 0) {
            $cleanupFailures.Add(
                'WO-024 loopback fault injection rejected qualification after successful cleanup.')
        }
        if ($cleanupFailures.Count -ne 0) {
            throw ('J6 fail-closed cleanup failed: ' + ($cleanupFailures -join ' '))
        }
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
finally {
    $fileCleanupFailures = [Collections.Generic.List[string]]::new()
    if (Test-Path -LiteralPath $partialPath) {
        try {
            Remove-Item -LiteralPath $partialPath -Force
        }
        catch {
            $fileCleanupFailures.Add('The partial encrypted backup could not be removed.')
        }
    }
    if (Test-Path -LiteralPath $manifestStagingPath) {
        try {
            Remove-Item -LiteralPath $manifestStagingPath -Force
        }
        catch {
            $fileCleanupFailures.Add('The partial backup manifest could not be removed.')
        }
    }
    if (-not $publicationComplete -and $manifestOwned -and
        (Test-Path -LiteralPath $manifestPath)) {
        try {
            Remove-Item -LiteralPath $manifestPath -Force
        }
        catch {
            $fileCleanupFailures.Add('The unqualified backup manifest could not be removed.')
        }
    }
    if (-not $publicationComplete -and $destinationOwned -and
        (Test-Path -LiteralPath $destinationPath)) {
        try {
            Remove-Item -LiteralPath $destinationPath -Force
        }
        catch {
            $fileCleanupFailures.Add('The unqualified encrypted backup could not be removed.')
        }
    }
    Pop-Location
    $operatorCancellation.Dispose()
    if ($fileCleanupFailures.Count -ne 0) {
        throw ('J6 fail-closed file cleanup failed: ' + ($fileCleanupFailures -join ' '))
    }
}
