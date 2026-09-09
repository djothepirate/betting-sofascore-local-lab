[CmdletBinding()]
param([string]$BrowserCachePath = '', [string]$DockerExecutablePath = '',
    [ValidateSet('live-v4', 'live-v5', 'live-v6', 'live-v7')][string]$PolicyVersion = 'live-v4')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$v7 = $PolicyVersion -eq 'live-v7'
$v6 = $PolicyVersion -eq 'live-v6'
# The existing UI publication fixture uses this flag only to select the 100-second nominal.
$v5 = $PolicyVersion -in @('live-v5', 'live-v6')
$qualifiedMatches = if ($v7) { 3 } elseif ($v6) { 7 } elseif ($v5) { 20 } else { 10 }
$criticalSeconds = if ($v5) { 100 } else { 60 }
$gapSeconds = if ($v5 -or $v7) { 1 } else { 3 }
if ([string]::IsNullOrWhiteSpace($BrowserCachePath)) {
    $BrowserCachePath = Join-Path $repositoryRoot '.tmp/provider-playwright-browsers'
}
if (-not (Test-Path -LiteralPath $BrowserCachePath -PathType Container)) {
    throw 'Install the dedicated Chromium runtime explicitly before grouped qualification'
}
$browserCache = (Resolve-Path -LiteralPath $BrowserCachePath).Path
$dockerArgument = @()
if (-not [string]::IsNullOrWhiteSpace($DockerExecutablePath)) {
    if (-not (Test-Path -LiteralPath $DockerExecutablePath -PathType Leaf)) {
        throw 'DockerExecutablePath must name the installed Docker CLI'
    }
    $dockerArgument = @("-Dwo058.grouped.docker=$((Resolve-Path -LiteralPath $DockerExecutablePath).Path)")
}
$previousCache = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH', 'Process')
$previousDownload = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD', 'Process')
Push-Location $repositoryRoot
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
    & .\mvnw.cmd '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Grouped qualification package failed' }
    $qualificationStartedAt = [DateTime]::UtcNow
    & .\mvnw.cmd '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-DskipTests=false' '-DskipITs=false' `
        '-Dwo058.grouped.sustained=true' '-Dit.test=LiveGroupedCampaignLocalQualificationIT,LiveTenMatchRefreshBrowserQualificationIT' `
        "-Dwo058.grouped.v7=$($v7.ToString().ToLowerInvariant())" "-Dwo058.grouped.v5=$($v5.ToString().ToLowerInvariant())" "-Dwo058.grouped.v6=$($v6.ToString().ToLowerInvariant())" `
        "-Dwo058.ui.matches=$qualifiedMatches" `
        "-Dprovider.playwright.browser-cache=$browserCache" @dockerArgument `
        'failsafe:integration-test@provider-playwright-loopback-qualification' 'failsafe:verify@provider-playwright-loopback-qualification'
    if ($LASTEXITCODE -ne 0) { throw 'Grouped sustained qualification failed; inspect the preserved report' }
    $suite = Get-Item -LiteralPath 'target/failsafe-reports/TEST-com.bettingproject.sofascorelocal.application.network.playwright.LiveGroupedCampaignLocalQualificationIT.xml'
    if ($suite.LastWriteTimeUtc -lt $qualificationStartedAt) { throw 'Grouped qualification XML is stale' }
    [xml]$xml = Get-Content -LiteralPath $suite.FullName -Raw
    if ([int]$xml.testsuite.tests -ne 2 -or [int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) {
        throw 'Both smoke and sustained scenarios must execute successfully; skips are not qualification'
    }
    $uiSuite = Get-Item -LiteralPath 'target/failsafe-reports/TEST-com.bettingproject.sofascorelocal.adapter.web.LiveTenMatchRefreshBrowserQualificationIT.xml'
    if ($uiSuite.LastWriteTimeUtc -lt $qualificationStartedAt) { throw 'Ten-match UI qualification XML is stale' }
    [xml]$uiXml = Get-Content -LiteralPath $uiSuite.FullName -Raw
    if ([int]$uiXml.testsuite.tests -ne 1 -or [int]$uiXml.testsuite.failures -ne 0 -or [int]$uiXml.testsuite.errors -ne 0 -or [int]$uiXml.testsuite.skipped -ne 0) {
        throw 'Ten-match UI publication must be qualified without skipped tests'
    }
    $reportFile = Get-Item -LiteralPath ".tmp/wo058-$($PolicyVersion.Replace('live-', ''))-sustained-qualification.json"
    if ($reportFile.LastWriteTimeUtc -lt $qualificationStartedAt) { throw 'Grouped measured report is stale' }
    $report = Get-Content -LiteralPath $reportFile.FullName -Raw | ConvertFrom-Json
    if ($report.status -ne 'PASSED' -or $report.sustainedQualification -ne $true -or $report.matches -ne $qualifiedMatches `
            -or $report.policyVersion -ne $PolicyVersion -or $report.criticalIntervalSeconds -ne $criticalSeconds `
            -or $report.interGroupDelaySeconds -ne $gapSeconds `
            -or $report.warmupSeconds -ne 300 -or $report.productionDockerDfProbePerRequest -ne $true `
            -or $report.steadyElapsedSeconds -lt 1800 -or $report.realProviderCalls -ne 0 `
            -or $report.operatorDatabaseUsed -ne $false) {
        throw 'The report does not prove the required sustained loopback scenario'
    }
    if (($v6 -or $v7) -and ($report.productionPersistentResilience -ne $true `
            -or $report.productionTransportDiagnosticPersistence -ne $true `
            -or $report.minimumPostCompletionDelaySeconds -ne 2 `
            -or $report.maximumDeparturesPer60Seconds -ne 25 -or $report.maximumDeparturesPerHour -ne 1000 `
            -or $report.observedMinimumPostCompletionDelaySeconds -lt 1.999 `
            -or $report.observedMaximumDeparturesPer60Seconds -gt 25 `
            -or $report.observedMaximumDeparturesPerHour -gt 1000 `
            -or $report.observedMaximumWireArrivalsPer60Seconds -gt 25 `
            -or $report.observedMaximumWireArrivalsPerHour -gt 1000 `
            -or $report.durableDepartures -ne $report.requests `
            -or $report.durableDepartureCompletions -ne $report.requests `
            -or $report.durableCompleteTransportDiagnostics -ne $report.requests)) {
        throw 'The report does not prove the real persistent protected wrapper and its bounded departures'
    }
    'WO058_GROUPED_CADENCE_LOOPBACK=PASS'
    'SOFASCORE_NETWORK_CALLS_EXECUTED=NO'
    "GROUPED_REPORT_SHA256=$((Get-FileHash -LiteralPath $reportFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant())"
    'CAPACITY_CONFIGURATION=REQUIRES_REVIEW_OF_MEASURED_FAMILY_ENVELOPES'
}
finally {
    Pop-Location
    if ($null -eq $previousCache) { Remove-Item Env:PLAYWRIGHT_BROWSERS_PATH -ErrorAction SilentlyContinue }
    else { $env:PLAYWRIGHT_BROWSERS_PATH = $previousCache }
    if ($null -eq $previousDownload) { Remove-Item Env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD -ErrorAction SilentlyContinue }
    else { $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = $previousDownload }
}
