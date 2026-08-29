[CmdletBinding()]
param(
    [ValidateSet('Preview', 'Execute')]
    [string]$Mode = 'Preview',

    [string]$BackupManifest,
    [string]$CutoffAt,
    [string]$PlanSha256,
    [string]$ConfirmationPhrase
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (Get-NetTCPConnection -LocalPort 8087 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Stop the local application before running the one-shot J6 retention command.'
}
if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot '.env') -PathType Leaf)) {
    throw 'The local .env file is required.'
}

$environmentNames = @(
    'SOFASCORE_J6_RETENTION_MODE',
    'SOFASCORE_J6_RETENTION_CUTOFF_AT',
    'SOFASCORE_J6_RETENTION_PLAN_SHA256',
    'SOFASCORE_J6_RETENTION_CONFIRMATION',
    'SOFASCORE_J6_BACKUP_MANIFEST_SHA256',
    'SOFASCORE_J6_BACKUP_CIPHER_SHA256',
    'SOFASCORE_J6_BACKUP_QUALIFIED_AT',
    'SOFASCORE_J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID',
    'SOFASCORE_J6_BACKUP_COVERAGE_RECEIVED_AT',
    'SOFASCORE_J6_BACKUP_RESTORED_AND_QUALIFIED',
    'SOFASCORE_ENABLED',
    'SOFASCORE_J3_QUALIFICATION_ENABLED',
    'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED',
    'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED',
    'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED',
    'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED'
)
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Set-ProcessEnvironment {
    param([Parameter(Mandatory = $true)][string]$Name, [AllowEmptyString()][string]$Value)
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

try {
    Set-ProcessEnvironment -Name 'SOFASCORE_J6_RETENTION_MODE' -Value $Mode.ToUpperInvariant()
    foreach ($name in @(
            'SOFASCORE_ENABLED',
            'SOFASCORE_J3_QUALIFICATION_ENABLED',
            'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED',
            'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED',
            'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED',
            'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED')) {
        Set-ProcessEnvironment -Name $name -Value 'false'
    }

    if ($Mode -eq 'Execute') {
        if ([string]::IsNullOrWhiteSpace($BackupManifest) -or
                [string]::IsNullOrWhiteSpace($CutoffAt) -or
                [string]::IsNullOrWhiteSpace($PlanSha256) -or
                [string]::IsNullOrWhiteSpace($ConfirmationPhrase)) {
            throw 'Execute requires -BackupManifest, -CutoffAt, -PlanSha256 and -ConfirmationPhrase.'
        }
        if (-not [IO.Path]::IsPathFullyQualified($BackupManifest)) {
            throw 'The backup manifest must be an absolute path outside the repository.'
        }
        $manifestPath = [IO.Path]::GetFullPath($BackupManifest)
        $repositoryPrefix = $repositoryRoot.TrimEnd('\') + '\'
        if ($manifestPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'The backup manifest must be an absolute path outside the repository.'
        }
        if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
            throw 'The backup manifest does not exist.'
        }
        $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
        if ($manifest.formatVersion -ne 1 -or
                $manifest.purpose -cne 'J6_RAW_PAYLOAD_RETENTION_BACKUP' -or
                -not $manifest.restoreQualified -or
                [string]::IsNullOrWhiteSpace($manifest.qualifiedAt)) {
            throw 'The backup manifest is not a qualified J6 restore proof.'
        }
        $cipherFileName = $manifest.encryptedBackupFileName
        if ([string]::IsNullOrWhiteSpace($cipherFileName) -or
                [IO.Path]::GetFileName($cipherFileName) -cne $cipherFileName -or
                -not $cipherFileName.EndsWith('.age', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'The qualified manifest contains an invalid encrypted backup file name.'
        }
        $qualificationFields = @(
            'flywayVersion',
            'snapshotCount',
            'occurrenceCount',
            'canonicalObservationCount',
            'detailObservationCount',
            'eventDataObservationCount',
            'purgeAuditCount',
            'coverageMaxSnapshotId',
            'coverageReceivedAt',
            'rawPayloadIntegrityFailures',
            'snapshotMetadataSha256',
            'occurrenceSha256',
            'normalizedProvenanceSha256'
        )
        if ($null -eq $manifest.source -or $null -eq $manifest.restored) {
            throw 'The qualified manifest must contain source and restored evidence.'
        }
        foreach ($field in $qualificationFields) {
            $sourceValue = $manifest.source.$field
            $restoredValue = $manifest.restored.$field
            if ($null -eq $sourceValue -or $null -eq $restoredValue -or
                    $sourceValue.ToString() -cne $restoredValue.ToString()) {
                throw "The qualified manifest source/restore evidence differs: $field"
            }
        }
        if ($manifest.source.flywayVersion.ToString() -cne '26' -or
                [long]$manifest.source.rawPayloadIntegrityFailures -ne 0) {
            throw 'The qualified manifest does not prove a valid Flyway V26 raw-payload restore.'
        }
        $cipherPath = [IO.Path]::GetFullPath((Join-Path `
            (Split-Path -Parent $manifestPath) $cipherFileName))
        if (-not (Test-Path -LiteralPath $cipherPath -PathType Leaf)) {
            throw 'The encrypted backup referenced by the manifest is missing.'
        }
        $actualCipherHash = (Get-FileHash -LiteralPath $cipherPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualCipherHash -cne $manifest.cipherSha256) {
            throw 'The encrypted backup SHA-256 no longer matches the qualified manifest.'
        }
        if ([long]$manifest.source.coverageMaxSnapshotId -lt 1) {
            throw 'The qualified backup does not cover any snapshot.'
        }
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_RETENTION_CUTOFF_AT' -Value $CutoffAt
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_RETENTION_PLAN_SHA256' -Value $PlanSha256
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_RETENTION_CONFIRMATION' -Value $ConfirmationPhrase
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_BACKUP_MANIFEST_SHA256' -Value `
            ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant())
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_BACKUP_CIPHER_SHA256' -Value $actualCipherHash
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_BACKUP_QUALIFIED_AT' -Value $manifest.qualifiedAt
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID' -Value `
            $manifest.source.coverageMaxSnapshotId.ToString()
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_BACKUP_COVERAGE_RECEIVED_AT' -Value `
            $manifest.source.coverageReceivedAt
        Set-ProcessEnvironment -Name 'SOFASCORE_J6_BACKUP_RESTORED_AND_QUALIFIED' -Value 'true'
    }

    Push-Location $repositoryRoot
    try {
        & .\mvnw.cmd -q -DskipTests `
            '-Dspring-boot.run.main-class=com.bettingproject.sofascorelocal.cli.J6RetentionCommand' `
            spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw "The one-shot J6 retention command failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
}
