[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$From,

    [Parameter(Mandatory = $true)]
    [string]$To,

    [Parameter(Mandatory = $true)]
    [string]$AsOf
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

function Convert-StrictUtcInstant {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Name
    )
    if ($Value.Length -lt 1 -or $Value.Length -gt 64) {
        throw "$Name must be a raw 1..64 character ISO-8601 UTC instant."
    }
    foreach ($character in $Value.ToCharArray()) {
        if ([Char]::IsControl($character)) {
            throw "$Name cannot contain ISO control characters."
        }
    }
    $normalized = $Value.Trim()
    if ($normalized.Length -lt 1 -or
            -not $normalized.EndsWith('Z', [StringComparison]::Ordinal)) {
        throw "$Name must be a 1..64 character ISO-8601 UTC instant ending in Z."
    }
    if ($normalized -notmatch `
            '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$') {
        throw "$Name must use the strict ISO-8601 UTC instant form accepted by Java Instant."
    }
    $parsed = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse(
            $normalized,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal -bor
                [Globalization.DateTimeStyles]::AdjustToUniversal,
            [ref]$parsed)) {
        throw "$Name must be an ISO-8601 UTC instant accepted by the J8 exporter."
    }
    return $normalized
}

$fromInstant = Convert-StrictUtcInstant -Value $From -Name 'From'
$toInstant = Convert-StrictUtcInstant -Value $To -Name 'To'
$asOfInstant = Convert-StrictUtcInstant -Value $AsOf -Name 'AsOf'

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (Get-NetTCPConnection -LocalPort 8087 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Stop the local application before running the one-shot J8 benchmark exporter.'
}
if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot '.env') -PathType Leaf)) {
    throw 'The local .env file is required.'
}

$environmentNames = @(
    'SOFASCORE_J8_EXPORT_FROM',
    'SOFASCORE_J8_EXPORT_TO',
    'SOFASCORE_J8_EXPORT_AS_OF',
    'SPRING_MAIN_WEB_APPLICATION_TYPE',
    'SPRING_TASK_SCHEDULING_ENABLED',
    'SOFASCORE_EXPORT_DIR',
    'SOFASCORE_ENABLED',
    'SOFASCORE_PLAYWRIGHT_ENABLED',
    'SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION',
    'SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN',
    'SOFASCORE_J3_QUALIFICATION_ENABLED',
    'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED',
    'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED',
    'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED',
    'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED',
    'SOFASCORE_AUTOMATIC_REFRESH_ENABLED',
    'SOFASCORE_LIVE_POLLING_ENABLED',
    'OPTIONAL_INTEGRATION_ENABLED',
    'OPTIONAL_INTEGRATION_EXECUTION_MODE',
    'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED',
    'OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS',
    'OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION',
    'OPTIONAL_INTEGRATION_SENDER_QUALIFICATION',
    'OPTIONAL_INTEGRATION_RECEIVER_ORIGIN',
    'OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION',
    'OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN',
    'OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED',
    'OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256'
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
    Set-ProcessEnvironment -Name 'SOFASCORE_J8_EXPORT_FROM' -Value $fromInstant
    Set-ProcessEnvironment -Name 'SOFASCORE_J8_EXPORT_TO' -Value $toInstant
    Set-ProcessEnvironment -Name 'SOFASCORE_J8_EXPORT_AS_OF' -Value $asOfInstant
    Set-ProcessEnvironment -Name 'SPRING_MAIN_WEB_APPLICATION_TYPE' -Value 'none'
    Set-ProcessEnvironment -Name 'SPRING_TASK_SCHEDULING_ENABLED' -Value 'false'
    Set-ProcessEnvironment -Name 'SOFASCORE_EXPORT_DIR' -Value './exports'
    Set-ProcessEnvironment -Name 'SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN' -Value ''
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_EXECUTION_MODE' -Value 'DISABLED'
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS' -Value 'NOT_EVIDENCED'
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION' -Value 'NOT_QUALIFIED'
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_SENDER_QUALIFICATION' -Value 'NOT_QUALIFIED'
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_RECEIVER_ORIGIN' -Value ''
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN' -Value ''
    Set-ProcessEnvironment -Name 'OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256' -Value ''
    foreach ($name in @(
            'SOFASCORE_ENABLED',
            'SOFASCORE_PLAYWRIGHT_ENABLED',
            'SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION',
            'SOFASCORE_J3_QUALIFICATION_ENABLED',
            'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED',
            'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED',
            'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED',
            'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED',
            'SOFASCORE_AUTOMATIC_REFRESH_ENABLED',
            'SOFASCORE_LIVE_POLLING_ENABLED',
            'OPTIONAL_INTEGRATION_ENABLED',
            'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED',
            'OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION',
            'OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED')) {
        Set-ProcessEnvironment -Name $name -Value 'false'
    }

    Push-Location $repositoryRoot
    try {
        & .\mvnw.cmd -q -DskipTests `
            '-Dspring-boot.run.main-class=com.bettingproject.sofascorelocal.cli.J8BenchmarkExportCommand' `
            spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw "The one-shot J8 benchmark export failed with exit code $LASTEXITCODE."
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
