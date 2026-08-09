[CmdletBinding()]
param(
    [switch]$SkipDocker,
    [switch]$SkipEnvironmentFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
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

if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot 'mvnw.cmd') -PathType Leaf)) {
    throw 'mvnw.cmd is missing'
}
if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -PathType Leaf)) {
    throw 'pom.xml is missing'
}

if (-not $SkipEnvironmentFile) {
    $envPath = Join-Path $repositoryRoot '.env'
    if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
        throw 'Missing .env. Run scripts\Initialize-LocalConfig.ps1 first.'
    }
}

if (-not $SkipDocker) {
    Assert-Command -Name 'docker'
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop is not ready'
    }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'docker compose is not available'
    }

    Push-Location $repositoryRoot
    try {
        & docker compose --env-file .env config --quiet
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
Write-Host "DOCKER_CHECKED=$(-not $SkipDocker)"
