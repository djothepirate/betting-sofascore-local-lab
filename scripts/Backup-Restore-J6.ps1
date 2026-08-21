[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$AgePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required to preserve native binary pipelines.'
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
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is required.'
}
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
    $value = & docker compose --env-file .env exec -T postgres sh -c `
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
    $value = & docker compose --env-file .env exec -T postgres sh -c `
        'psql --username "$POSTGRES_USER" --dbname "$1" --no-align --tuples-only --quiet --command "$2"' `
        sh $Database $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'A restored database verification query failed.'
    }
    return (($value | ForEach-Object { $_.ToString() }) -join "`n").TrimEnd()
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

Push-Location $repositoryRoot
$partialPath = Join-Path $destinationDirectory `
    ([IO.Path]::GetFileName($destinationPath) + '.partial-' + [Guid]::NewGuid().ToString('N'))
$restoreDatabase = 'sofascore_j6_restore_' + `
    (Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss') + '_' + `
    [Guid]::NewGuid().ToString('N').Substring(0, 8)
$manifest = $null
try {
    & docker compose --env-file .env config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw 'compose.yaml validation failed.'
    }
    $connectorState = Invoke-PrimaryScalar -Sql `
        "select case when not network_enabled and circuit_state = 'LOCKED' then 'SAFE' else 'UNSAFE' end from connector_control where singleton_id = 1"
    if ($connectorState -ne 'SAFE') {
        throw 'The persisted connector control must be disabled and LOCKED before backup.'
    }

    $sourceFlywayVersion = Invoke-PrimaryScalar -Sql $flywaySql
    if ($sourceFlywayVersion -cne '25') {
        throw 'Flyway V25 must be applied before the J6 backup/restore qualification.'
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
        coverageMaxSnapshotId = [long](Invoke-PrimaryScalar -Sql 'select coalesce(max(id), 0) from provider_snapshot')
        coverageReceivedAt = Invoke-PrimaryScalar -Sql $coverageReceivedSql
        rawPayloadIntegrityFailures = [long](Invoke-PrimaryScalar -Sql "select count(*) from provider_snapshot where payload_raw is not null and encode(sha256(payload_raw), 'hex') <> payload_sha256")
        snapshotMetadataSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $snapshotFingerprintSql)
        occurrenceSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $occurrenceFingerprintSql)
        normalizedProvenanceSha256 = Get-TextSha256 (Invoke-PrimaryScalar -Sql $normalizedFingerprintSql)
    }
    if ($source.rawPayloadIntegrityFailures -ne 0) {
        throw 'At least one retained payload does not match its persisted SHA-256.'
    }

    Write-Host 'J6_BACKUP_ENCRYPTION=INTERACTIVE_PASSPHRASE_REQUIRED'
    & docker compose --env-file .env exec -T postgres sh -c `
        'pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --no-owner --no-privileges' |
        & $ageExecutable -p -o $partialPath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $partialPath -PathType Leaf)) {
        throw 'Encrypted pg_dump creation failed.'
    }
    Move-Item -LiteralPath $partialPath -Destination $destinationPath
    $cipherSha256 = (Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash.ToLowerInvariant()

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
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

    if ($restoreDatabase -notmatch '^sofascore_j6_restore_[0-9]{14}_[a-f0-9]{8}$') {
        throw 'Generated restore database name is unsafe.'
    }
    & docker compose --env-file .env exec -T postgres sh -c `
        'createdb --username "$POSTGRES_USER" "$1"' sh $restoreDatabase
    if ($LASTEXITCODE -ne 0) {
        throw 'Temporary restore database creation failed.'
    }

    Write-Host 'J6_RESTORE_DECRYPTION=INTERACTIVE_PASSPHRASE_REQUIRED'
    & $ageExecutable -d $destinationPath |
        & docker compose --env-file .env exec -T postgres sh -c `
            'pg_restore --username "$POSTGRES_USER" --dbname "$1" --no-owner --no-privileges --exit-on-error' `
            sh $restoreDatabase
    if ($LASTEXITCODE -ne 0) {
        throw 'Encrypted backup restore failed.'
    }

    $restored = [ordered]@{
        flywayVersion = Invoke-RestoreScalar -Database $restoreDatabase -Sql $flywaySql
        snapshotCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_snapshot')
        occurrenceCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from provider_snapshot_occurrence')
        canonicalObservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from canonical_event_observation')
        detailObservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from event_detail_observation')
        eventDataObservationCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j5_event_data_observation')
        purgeAuditCount = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select count(*) from j6_raw_payload_purge_audit')
        coverageMaxSnapshotId = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql 'select coalesce(max(id), 0) from provider_snapshot')
        coverageReceivedAt = Invoke-RestoreScalar -Database $restoreDatabase -Sql $coverageReceivedSql
        rawPayloadIntegrityFailures = [long](Invoke-RestoreScalar -Database $restoreDatabase -Sql "select count(*) from provider_snapshot where payload_raw is not null and encode(sha256(payload_raw), 'hex') <> payload_sha256")
        snapshotMetadataSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $snapshotFingerprintSql)
        occurrenceSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $occurrenceFingerprintSql)
        normalizedProvenanceSha256 = Get-TextSha256 (Invoke-RestoreScalar -Database $restoreDatabase -Sql $normalizedFingerprintSql)
    }
    foreach ($key in $source.Keys) {
        if ($source[$key].ToString() -cne $restored[$key].ToString()) {
            throw "Restore qualification mismatch: $key"
        }
    }
    $manifest.restoreQualified = $true
    $manifest.qualifiedAt = (Get-Date).ToUniversalTime().ToString('o')
    $manifest['restored'] = $restored
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM

    $manifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host 'J6_BACKUP_RESULT=QUALIFIED'
    Write-Host "J6_BACKUP_CIPHER_SHA256=$cipherSha256"
    Write-Host "J6_BACKUP_MANIFEST_SHA256=$manifestSha256"
    Write-Host "J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=$($source.coverageMaxSnapshotId)"
    Write-Host "J6_BACKUP_COVERAGE_RECEIVED_AT=$($source.coverageReceivedAt)"
}
finally {
    if (Test-Path -LiteralPath $partialPath) {
        Remove-Item -LiteralPath $partialPath -Force
    }
    if ($restoreDatabase -match '^sofascore_j6_restore_[0-9]{14}_[a-f0-9]{8}$') {
        & docker compose --env-file .env exec -T postgres sh -c `
            'dropdb --username "$POSTGRES_USER" --if-exists "$1"' sh $restoreDatabase *> $null
    }
    Pop-Location
}
