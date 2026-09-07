[CmdletBinding()]
param([string]$BrowserCachePath = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BrowserCachePath)) { $BrowserCachePath = Join-Path $repositoryRoot '.tmp/provider-playwright-browsers' }
if (-not (Test-Path -LiteralPath $BrowserCachePath -PathType Container)) { throw 'Install the dedicated runtime explicitly with Install-J3PlaywrightRuntime.ps1 first' }
$browserCache = (Resolve-Path -LiteralPath $BrowserCachePath).Path
$previousCache = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH','Process')
Push-Location $repositoryRoot
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    & .\mvnw.cmd '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Live qualification package failed' }
    $qualificationStartedAt = [DateTime]::UtcNow
    & .\mvnw.cmd '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests=false' '-DskipITs=false' `
        '-Dit.test=LiveProviderSessionQualificationIT,LiveCampaignBrowserQualificationIT,LiveCampaignFormBrowserQualificationIT' `
        "-Dprovider.playwright.browser-cache=$browserCache" `
        'failsafe:integration-test@provider-playwright-loopback-qualification' 'failsafe:verify@provider-playwright-loopback-qualification'
    if ($LASTEXITCODE -ne 0) { throw 'Live loopback qualification failed' }
    $reports = @(
        [pscustomobject]@{ Path = 'target/failsafe-reports/TEST-com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderSessionQualificationIT.xml'; Tests = 3 },
        [pscustomobject]@{ Path = 'target/failsafe-reports/TEST-com.bettingproject.sofascorelocal.adapter.web.LiveCampaignBrowserQualificationIT.xml'; Tests = 1 },
        [pscustomobject]@{ Path = 'target/failsafe-reports/TEST-com.bettingproject.sofascorelocal.adapter.web.LiveCampaignFormBrowserQualificationIT.xml'; Tests = 2 }
    )
    foreach ($report in $reports) {
        $item = Get-Item -LiteralPath $report.Path
        if ($item.LastWriteTimeUtc -lt $qualificationStartedAt) { throw 'Live loopback report is stale' }
        [xml]$xml = Get-Content -LiteralPath $item.FullName -Raw
        if ([int]$xml.testsuite.tests -ne $report.Tests -or [int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) { throw 'Live loopback report is not green' }
    }
    'WO058_LIVE_LOOPBACK=PASS'
    'SOFASCORE_NETWORK_CALLS_EXECUTED=NO'
}
finally {
    Pop-Location
    if ($null -eq $previousCache) { Remove-Item Env:PLAYWRIGHT_BROWSERS_PATH -ErrorAction SilentlyContinue }
    else { $env:PLAYWRIGHT_BROWSERS_PATH = $previousCache }
}
