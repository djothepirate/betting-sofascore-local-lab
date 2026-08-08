[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repositoryRoot '.env'
$exportsPath = Join-Path $repositoryRoot 'exports'

if ((Test-Path -LiteralPath $envPath) -and -not $Force) {
    throw ".env already exists. Use -Force only when you intentionally rotate the local database password."
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
