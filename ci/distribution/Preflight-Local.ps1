[CmdletBinding()]
param(
    [switch]$SkipDocker,
    [switch]$SkipEnvironmentFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$distributionRoot = Split-Path -Parent $PSScriptRoot

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Get-LocalDockerArguments {
    $dockerContextOverride = [Environment]::GetEnvironmentVariable('DOCKER_CONTEXT')
    $dockerHostOverride = [Environment]::GetEnvironmentVariable('DOCKER_HOST')
    if (-not [string]::IsNullOrWhiteSpace($dockerContextOverride)) {
        $endpointOutput = & docker context inspect --format '{{.Endpoints.docker.Host}}' $dockerContextOverride
        $endpointExitCode = $LASTEXITCODE
        if ($endpointExitCode -ne 0) {
            throw 'Unable to inspect the Docker context selected by DOCKER_CONTEXT.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    elseif (-not [string]::IsNullOrWhiteSpace($dockerHostOverride)) {
        if ($dockerHostOverride -notmatch '^npipe:////[.]/pipe/[^/]+$') {
            throw 'DOCKER_HOST must target a local Windows named pipe.'
        }
        $endpoint = $dockerHostOverride
    }
    else {
        $endpointOutput = & docker context inspect --format '{{.Endpoints.docker.Host}}'
        $endpointExitCode = $LASTEXITCODE
        if ($endpointExitCode -ne 0) {
            throw 'Unable to inspect the selected Docker context.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    if ($endpoint -notmatch '^npipe:////[.]/pipe/[^/]+$') {
        throw 'The selected Docker context must target a local Windows named pipe.'
    }
    return @('--host', $endpoint)
}

function Get-ApplicationJar {
    $jars = @(
        Get-ChildItem -LiteralPath $distributionRoot -File -Filter 'betting-sofascore-local-lab-*.jar' |
            Where-Object {
                $_.Name -notmatch '-(sources|javadoc|tests|provider-playwright-worker)[.]jar$'
            }
    )
    if ($jars.Count -ne 1) {
        throw "Exactly one bundled application JAR is required; found $($jars.Count)."
    }
    if ($jars[0].Length -le 0) {
        throw 'The bundled application JAR is empty.'
    }
    return $jars[0]
}

Assert-Command -Name 'java'
$javaOutput = (& java --version | Out-String)
$javaExitCode = $LASTEXITCODE
if ($javaExitCode -ne 0) {
    throw 'java --version failed'
}
if ($javaOutput -notmatch '(?m)^\s*(?:java|openjdk)\s+(?:version\s+)?["]?25(?:[.\-"\s]|$)') {
    throw "Java 25 LTS is required. Detected output: $($javaOutput.Trim())"
}

$applicationJar = Get-ApplicationJar
$composePath = Join-Path $distributionRoot 'compose.yaml'
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
    throw 'compose.yaml is missing from the distribution.'
}

if (-not $SkipEnvironmentFile) {
    $envPath = Join-Path $distributionRoot '.env'
    if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
        throw 'Missing .env. Run scripts\Initialize-LocalConfig.ps1 first.'
    }
}

if (-not $SkipDocker) {
    Assert-Command -Name 'docker'
    $dockerGlobalArguments = @(Get-LocalDockerArguments)
    & docker @dockerGlobalArguments info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop is not ready'
    }
    & docker @dockerGlobalArguments compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'docker compose is not available'
    }

    Push-Location $distributionRoot
    try {
        & docker @dockerGlobalArguments compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env config --quiet
        if ($LASTEXITCODE -ne 0) {
            throw 'compose.yaml validation failed'
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host 'PREFLIGHT_RESULT=PASS'
Write-Host 'JAVA_TARGET=25'
Write-Host "APPLICATION_JAR=$($applicationJar.Name)"
Write-Host "DOCKER_CHECKED=$(-not $SkipDocker)"
