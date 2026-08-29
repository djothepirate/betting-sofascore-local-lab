[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$browserCache = Join-Path $repositoryRoot '.tmp\provider-playwright-browsers'
New-Item -ItemType Directory -Path $browserCache -Force | Out-Null
$browserCache = (Resolve-Path -LiteralPath $browserCache).Path
$previousBrowserCache = [Environment]::GetEnvironmentVariable(
    'PLAYWRIGHT_BROWSERS_PATH',
    'Process')

Push-Location $repositoryRoot
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    & .\mvnw.cmd `
        -Pprovider-playwright-runtime `
        '-Dexec.mainClass=com.microsoft.playwright.CLI' `
        '-Dexec.args=install chromium' `
        org.codehaus.mojo:exec-maven-plugin:3.5.1:java
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit Playwright Chromium installation failed'
    }
}
finally {
    Pop-Location
    if ($null -eq $previousBrowserCache) {
        Remove-Item Env:PLAYWRIGHT_BROWSERS_PATH -ErrorAction SilentlyContinue
    }
    else {
        $env:PLAYWRIGHT_BROWSERS_PATH = $previousBrowserCache
    }
}

Write-Host 'PLAYWRIGHT_RUNTIME_INSTALL=PASS'
Write-Host "PLAYWRIGHT_BROWSERS_PATH=$browserCache"
Write-Host 'PROVIDER_ACCESS_PERFORMED=NO'
