[CmdletBinding()]
param([string]$BrowserCachePath = '', [string]$DockerExecutablePath = '',
    [ValidateSet('live-v4', 'live-v5', 'live-v6', 'live-v7', 'live-v8')][string]$PolicyVersion = 'live-v4')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$v8 = $PolicyVersion -eq 'live-v8'
$v7 = $PolicyVersion -eq 'live-v7'
$v6 = $PolicyVersion -eq 'live-v6'
# The existing UI publication fixture uses this flag only to select the 100-second nominal.
$v5 = $PolicyVersion -in @('live-v5', 'live-v6')
$qualifiedMatches = if ($v8) { 10 } elseif ($v7) { 3 } elseif ($v6) { 7 } elseif ($v5) { 20 } else { 10 }
$criticalSeconds = if ($v8 -or -not $v5) { 60 } else { 100 }
$gapSeconds = if ($v8) { 0.5 } elseif ($v5 -or $v7) { 1 } else { 3 }
$gapMillis = if ($v8) { 500 } elseif ($v5 -or $v7) { 1000 } else { 3000 }
$requiresPressureEvidence = $v6 -or $v7 -or $v8
$minimumPostCompletionSeconds = if ($v8) { 0.5 } else { 2.0 }
$maximumDeparturesPerMinute = if ($v8) { 45 } else { 25 }
$maximumDeparturesPerHour = if ($v8) { 2756 } else { 1000 }
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
        "-Dwo058.grouped.v8=$($v8.ToString().ToLowerInvariant())" "-Dwo058.grouped.v7=$($v7.ToString().ToLowerInvariant())" "-Dwo058.grouped.v5=$($v5.ToString().ToLowerInvariant())" "-Dwo058.grouped.v6=$($v6.ToString().ToLowerInvariant())" `
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
            -or $report.interGroupDelayMillis -ne $gapMillis `
            -or $report.warmupSeconds -ne 300 -or $report.productionDockerDfProbePerRequest -ne $true `
            -or $report.steadyElapsedSeconds -lt 1800 -or $report.realProviderCalls -ne 0 `
            -or $report.operatorDatabaseUsed -ne $false) {
        throw 'The report does not prove the required sustained loopback scenario'
    }
    if ($requiresPressureEvidence -and ($report.productionPersistentResilience -ne $true `
            -or $report.productionTransportDiagnosticPersistence -ne $true `
            -or $report.minimumPostCompletionDelaySeconds -ne $minimumPostCompletionSeconds `
            -or $report.minimumPostCompletionDelayMillis -ne ($minimumPostCompletionSeconds * 1000) `
            -or $report.maximumDeparturesPer60Seconds -ne $maximumDeparturesPerMinute -or $report.maximumDeparturesPerHour -ne $maximumDeparturesPerHour `
            -or $report.observedMinimumPostCompletionDelaySeconds -lt ($minimumPostCompletionSeconds - 0.001) `
            -or $report.observedMaximumDeparturesPer60Seconds -gt $maximumDeparturesPerMinute `
            -or $report.observedMaximumDeparturesPerHour -gt $maximumDeparturesPerHour `
            -or $report.observedMaximumWireArrivalsPer60Seconds -gt $maximumDeparturesPerMinute `
            -or $report.observedMaximumWireArrivalsPerHour -gt $maximumDeparturesPerHour `
            -or $report.durableDepartures -ne $report.requests `
            -or $report.durableDepartureCompletions -ne $report.requests `
            -or $report.durableCompleteTransportDiagnostics -ne $report.requests)) {
        throw 'The report does not prove the real persistent protected wrapper and its bounded departures'
    }
    if ($v8) {
        if ($report.normalPathDepartureCadenceSeconds -ne 60 -or $report.normalPathDepartureTimestamp -ne 'requestedNanos' `
                -or $report.normalPathDepartureCadenceScope -ne 'per-event-family') {
            throw 'The V8 report does not identify the strict 60-second normal-path departure cadence'
        }
        # The builder reads only the newly generated loopback report.  It emits
        # reviewable bytes under .tmp; it never updates application configuration
        # or a tracked evidence file on the operator's behalf.
        $v8EvidenceDirectory = Join-Path $repositoryRoot '.tmp/wo058-v8-verification'
        if (Test-Path -LiteralPath $v8EvidenceDirectory) {
            throw 'V8 verification directory already exists; preserve or review it before a new evidence generation'
        }
        New-Item -ItemType Directory -Path $v8EvidenceDirectory | Out-Null
        $v8ClasspathFile = Join-Path $repositoryRoot '.tmp/wo058-v8-builder-classpath.txt'
        $v8BuilderClasses = Join-Path $repositoryRoot '.tmp/wo058-v8-builder-classes'
        $v8BuilderSource = Join-Path $repositoryRoot 'docs/validation/v8-profile-builder/V8MeasuredProfile.java'
        $mainClasses = Join-Path $repositoryRoot 'target/classes'
        if (Test-Path -LiteralPath $v8BuilderClasses) {
            $expectedBuilderClasses = [IO.Path]::GetFullPath($v8BuilderClasses)
            $resolvedBuilderClasses = (Resolve-Path -LiteralPath $v8BuilderClasses).Path
            $temporaryRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot '.tmp')).TrimEnd('\')
            if (-not $resolvedBuilderClasses.Equals(
                    $expectedBuilderClasses,
                    [StringComparison]::OrdinalIgnoreCase) -or
                -not $expectedBuilderClasses.StartsWith(
                    $temporaryRoot + '\',
                    [StringComparison]::OrdinalIgnoreCase) -or
                (((Get-Item -LiteralPath $v8BuilderClasses -Force).Attributes -band
                    [IO.FileAttributes]::ReparsePoint) -ne 0)) {
                throw 'The V8 evidence builder classes directory is not the owned temporary directory.'
            }
            Remove-Item -LiteralPath $resolvedBuilderClasses -Recurse -Force
        }
        New-Item -ItemType Directory -Path $v8BuilderClasses | Out-Null
        & .\mvnw.cmd '-q' 'dependency:build-classpath' "-Dmdep.outputFile=$v8ClasspathFile" '-Dmdep.pathSeparator=;'
        if ($LASTEXITCODE -ne 0) { throw 'Unable to create the local classpath for the V8 evidence builder' }
        $v8Classpath = (Get-Content -LiteralPath $v8ClasspathFile -Raw).Trim()
        & javac '--release' '25' '-cp' "$mainClasses;$v8Classpath" '-d' $v8BuilderClasses $v8BuilderSource
        if ($LASTEXITCODE -ne 0) { throw 'V8 evidence builder compilation failed' }
        $v8OutputDirectory = Join-Path $v8EvidenceDirectory 'generated'
        & java '-cp' "$v8BuilderClasses;$mainClasses;$v8Classpath" `
            'com.bettingproject.sofascorelocal.application.live.V8MeasuredProfile' $reportFile.FullName $v8OutputDirectory
        if ($LASTEXITCODE -ne 0) { throw 'V8 loopback evidence is not admissible for ten matches' }
        $v8Native = Get-Item -LiteralPath (Join-Path $v8OutputDirectory 'WO058-GROUPED-LIVE-V8-NATIVE-20260910.json')
        $v8Profile = Get-Item -LiteralPath (Join-Path $v8OutputDirectory 'WO058-GROUPED-LIVE-V8-PROFILE-20260910.json')
        $v8ProfileDocument = Get-Content -LiteralPath $v8Profile.FullName -Raw | ConvertFrom-Json
        $v8NativeHash = (Get-FileHash -LiteralPath $v8Native.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($v8ProfileDocument.status -ne 'QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE' `
                -or $v8ProfileDocument.policyVersion -ne 'live-v8' -or $v8ProfileDocument.qualifiedCapacity -ne 10 `
                -or $v8ProfileDocument.nativeEvidence.sha256 -ne $v8NativeHash `
                -or $v8ProfileDocument.admission.productionSchedulerScenariosExecutedForThisProfile -ne $true) {
            throw 'V8 generated profile does not bind the measured local report to the production V8 replay'
        }
        "V8_NATIVE_SHA256=$v8NativeHash"
        "V8_PROFILE_SHA256=$((Get-FileHash -LiteralPath $v8Profile.FullName -Algorithm SHA256).Hash.ToLowerInvariant())"
        'V8_TRACKED_EVIDENCE_UPDATED=NO'
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
