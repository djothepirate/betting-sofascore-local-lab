[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repositoryRoot '.env'
$exportsPath = Join-Path $repositoryRoot 'exports'
$postgresVolumeName = 'betting-sofascore-local-lab-postgres-data'

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

if ((Test-Path -LiteralPath $envPath) -and -not $Force) {
    throw '.env already exists. Keep it to reuse the credentials of the persistent PostgreSQL volume.'
}

Assert-Command -Name 'docker'
$dockerGlobalArguments = @(Get-LocalDockerArguments)
& docker @dockerGlobalArguments info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Desktop is not ready.'
}

$volumeOutput = & docker @dockerGlobalArguments volume ls --quiet --filter "name=^$postgresVolumeName`$"
$volumeQueryExitCode = $LASTEXITCODE
if ($volumeQueryExitCode -ne 0) {
    throw "Unable to determine whether persistent PostgreSQL volume '$postgresVolumeName' exists."
}
$volumeNames = @($volumeOutput | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($volumeNames -contains $postgresVolumeName) {
    throw "Persistent PostgreSQL volume '$postgresVolumeName' already exists. Reuse the .env from the previous extraction, or explicitly remove its data with scripts\Stop-Local.ps1 -RemoveData before initializing new credentials."
}

$bytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $generator.GetBytes($bytes)
}
finally {
    $generator.Dispose()
}

$password = [Convert]::ToBase64String($bytes)
$password = $password.TrimEnd('=').Replace('+', 'A').Replace('/', 'B')

$content = @(
    'POSTGRES_DB=sofascore_local_lab'
    'POSTGRES_USER=sofascore_lab'
    "POSTGRES_PASSWORD=$password"
    'POSTGRES_PORT=5432'
)

Set-Content -LiteralPath $envPath -Value $content -Encoding ASCII
New-Item -ItemType Directory -Path $exportsPath -Force | Out-Null

Write-Host 'LOCAL_CONFIG_RESULT=CREATED'
Write-Host "ENV_FILE=$envPath"
Write-Host 'POSTGRES_PASSWORD_DISPLAYED=NO'
