[CmdletBinding()]
param(
    [string]$BrowserCachePath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$browserCache = $BrowserCachePath
if ([string]::IsNullOrWhiteSpace($browserCache)) {
    $browserCache = Join-Path $repositoryRoot '.tmp\provider-playwright-browsers'
}
if (-not (Test-Path -LiteralPath $browserCache -PathType Container)) {
    throw 'The dedicated Playwright browser cache is absent; run Install-J3PlaywrightRuntime.ps1 explicitly first'
}
$browserCacheItem = Get-Item -LiteralPath $browserCache -Force
$browserCacheTarget = $browserCacheItem.ResolveLinkTarget($true)
$browserCache = if ($null -eq $browserCacheTarget) {
    $browserCacheItem.FullName
}
else {
    $browserCacheTarget.FullName
}
$chromiumInstallations = @(Get-ChildItem -LiteralPath $browserCache -Directory |
    Where-Object {
        $_.Name -like 'chromium-*' -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'INSTALLATION_COMPLETE') -PathType Leaf) -and
        @(Get-ChildItem -LiteralPath $_.FullName -Recurse -File -Filter 'chrome.exe').Count -gt 0
    })
$headlessShellInstallations = @(Get-ChildItem -LiteralPath $browserCache -Directory |
    Where-Object {
        $_.Name -like 'chromium_headless_shell-*' -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'INSTALLATION_COMPLETE') -PathType Leaf) -and
        @(Get-ChildItem -LiteralPath $_.FullName -Recurse -File -Filter 'chrome-headless-shell.exe').Count -gt 0
    })
if ($chromiumInstallations.Count -eq 0 -or $headlessShellInstallations.Count -eq 0) {
    throw 'The dedicated Playwright cache has no complete Chromium installation; run Install-J3PlaywrightRuntime.ps1 explicitly first'
}
$previousBrowserCache = [Environment]::GetEnvironmentVariable(
    'PLAYWRIGHT_BROWSERS_PATH',
    'Process')

$sensitiveReportPatterns = @(
    'local-only-value',
    '(?im)^\s*(?:authorization|proxy-authorization)\s*:\s*(?:bearer|basic)\s+\S+',
    '(?im)^\s*(?:set-cookie|cookie)\s*:\s*\S+',
    '(?i)["''](?:access[_-]?token|refresh[_-]?token|api[_-]?key|password|session[_-]?id|cookie|secret)["'']\s*:\s*["''][^"''\r\n]+["'']',
    '\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b',
    '-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----'
)
$sensitiveNamePattern =
    '(?i)(authorization|proxy[_-]?authorization|cookie|set[_-]?cookie|token|secret|password|passwd|api[_-]?key|session[_-]?id)'

function Test-J5SensitiveReportContent {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content,
        [Parameter(Mandatory = $true)]
        [bool]$IsXml
    )

    $textsToScan = @(
        $Content,
        [System.Net.WebUtility]::HtmlDecode($Content)
    )
    if ($IsXml) {
        [xml]$document = $Content
        $textsToScan += $document.DocumentElement.InnerText
        foreach ($attribute in @($document.SelectNodes('//@*'))) {
            if ($attribute.LocalName -match $sensitiveNamePattern -and
                -not [string]::IsNullOrWhiteSpace($attribute.Value)) {
                return $true
            }
        }
        foreach ($property in @($document.SelectNodes('//property'))) {
            $propertyName = $property.GetAttribute('name')
            $propertyValue = $property.GetAttribute('value')
            if ($propertyName -match $sensitiveNamePattern -and
                -not [string]::IsNullOrWhiteSpace($propertyValue)) {
                return $true
            }
        }
    }
    foreach ($textToScan in $textsToScan) {
        foreach ($pattern in $sensitiveReportPatterns) {
            if ($textToScan -match $pattern) {
                return $true
            }
        }
    }
    return $false
}

$encodedTokenCanary =
    '<testsuite><testcase><system-out>{&quot;access_token&quot;:&quot;fixture-value&quot;}</system-out></testcase></testsuite>'
$propertyTokenCanary =
    '<testsuite><properties><property name="env.ACCESS_TOKEN" value="fixture-value"/></properties></testsuite>'
$authorizationCanary =
    '<testsuite><testcase><system-out>Authorization: Bearer fixture-credential-value</system-out></testcase></testsuite>'
$cookieCanary =
    '<testsuite><testcase><system-out>Cookie: session=fixture-value</system-out></testcase></testsuite>'
$passwordCanary =
    '<testsuite><testcase><system-out>{&quot;password&quot;:&quot;fixture-value&quot;}</system-out></testcase></testsuite>'
$jwtCanary =
    '<testsuite><testcase><system-out>eyJabcdefghijk.abcdefghijk.abcdefghijk</system-out></testcase></testsuite>'
$privateKeyCanary =
    '<testsuite><testcase><system-out>-----BEGIN PRIVATE KEY-----</system-out></testcase></testsuite>'
$safeReportCanary =
    '<testsuite><properties><property name="java.version" value="25"/></properties></testsuite>'
foreach ($sensitiveScannerCanary in @(
        $encodedTokenCanary,
        $propertyTokenCanary,
        $authorizationCanary,
        $cookieCanary,
        $passwordCanary,
        $jwtCanary,
        $privateKeyCanary)) {
    if (-not (Test-J5SensitiveReportContent `
                -Content $sensitiveScannerCanary `
                -IsXml $true)) {
        throw 'The J5 Maven report sensitive-data scanner contract is not satisfied'
    }
}
if (Test-J5SensitiveReportContent -Content $safeReportCanary -IsXml $true) {
    throw 'The J5 Maven report sensitive-data scanner contract is not satisfied'
}

Push-Location $repositoryRoot
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    & .\mvnw.cmd `
        '-Pprovider-playwright-runtime' `
        '-DskipTests=false' `
        '-Dtest=ProviderPlaywrightWorkerProtocolTest,ProviderPlaywrightWorkerSecurityContractTest,ProviderPlaywrightWorkerNetworkObservationTest' `
        clean `
        test
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J5 Playwright worker protocol, security and network-observation tests failed'
    }

    & .\mvnw.cmd `
        '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
        -DskipTests `
        package
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J5 Playwright worker package failed'
    }

    & .\mvnw.cmd `
        '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
        '-DskipTests=false' `
        '-DskipITs=false' `
        "-Dprovider.playwright.browser-cache=$browserCache" `
        'failsafe:integration-test@provider-playwright-loopback-qualification' `
        'failsafe:verify@provider-playwright-loopback-qualification'
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J5 Playwright loopback qualification failed'
    }

    $protocolReportPath = Join-Path $repositoryRoot `
        'target\surefire-reports\TEST-com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerProtocolTest.xml'
    $securityReportPath = Join-Path $repositoryRoot `
        'target\surefire-reports\TEST-com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerSecurityContractTest.xml'
    $networkObservationReportPath = Join-Path $repositoryRoot `
        'target\surefire-reports\TEST-com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerNetworkObservationTest.xml'
    $qualificationReportPath = Join-Path $repositoryRoot `
        'target\failsafe-reports\TEST-com.bettingproject.sofascorelocal.application.network.playwright.ProviderPlaywrightLocalQualificationIT.xml'
    $expectedXmlSuites = @(
        [pscustomobject]@{ Path = $protocolReportPath; Tests = 11; Exact = $true },
        [pscustomobject]@{ Path = $securityReportPath; Tests = 1; Exact = $true },
        [pscustomobject]@{ Path = $networkObservationReportPath; Tests = 10; Exact = $true },
        [pscustomobject]@{ Path = $qualificationReportPath; Tests = 14; Exact = $true }
    )
    $expectedXmlReports = @($expectedXmlSuites | ForEach-Object { $_.Path })
    foreach ($expectedSuite in $expectedXmlSuites) {
        $reportPath = $expectedSuite.Path
        if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
            throw "The expected J5 Playwright Maven test report is absent: $reportPath"
        }
        [xml]$report = Get-Content -LiteralPath $reportPath -Raw
        $suite = $report.testsuite
        $tests = [int]$suite.tests
        $failures = [int]$suite.failures
        $errors = [int]$suite.errors
        $skipped = [int]$suite.skipped
        $testCountMismatch = if ($expectedSuite.Exact) {
            $tests -ne $expectedSuite.Tests
        }
        else {
            $tests -lt $expectedSuite.Tests
        }
        if ($testCountMismatch -or $failures -ne 0 -or
            $errors -ne 0 -or $skipped -ne 0) {
            throw "The expected J5 Playwright Maven test report does not have the exact successful suite shape: $reportPath"
        }
    }

    # The closed loopback TCP-port range regression was added in 154349a.
    [xml]$protocolReport = Get-Content -LiteralPath $protocolReportPath -Raw
    $closedPortRangeCases = @($protocolReport.testsuite.testcase |
        Where-Object { $_.name -eq 'acceptsOnlyTheClosedLoopbackOriginTcpPortRange' })
    if ($closedPortRangeCases.Count -ne 1 -or
        $null -ne $closedPortRangeCases[0].SelectSingleNode('failure') -or
        $null -ne $closedPortRangeCases[0].SelectSingleNode('error') -or
        $null -ne $closedPortRangeCases[0].SelectSingleNode('skipped')) {
        throw 'The worker protocol qualification requires its successful closed loopback TCP-port range regression'
    }

    [xml]$qualificationReport = Get-Content -LiteralPath $qualificationReportPath -Raw
    foreach ($requiredTestCase in @(
            'routesJ5StatisticsIncidentsAndLineupsThroughOneWorkerAndPreservesA404',
            'createsANewWorkerAndContextForTheNextExplicitCampaign',
            'stopsJ5DuringMinimumDelayFenceWithinEveryBoundWithoutStartingNextRequest',
            'rejectsASensitiveCanaryWithoutWritingItToWorkerStreamsOrRuntimeFiles')) {
        $matchingTestCases = @($qualificationReport.testsuite.testcase |
                Where-Object { $_.name -eq $requiredTestCase })
        if ($matchingTestCases.Count -ne 1) {
            throw "The J5 Playwright qualification must contain exactly one successful test case named $requiredTestCase"
        }
        $testCase = $matchingTestCases[0]
        if ($null -ne $testCase.SelectSingleNode('failure') -or
            $null -ne $testCase.SelectSingleNode('error') -or
            $null -ne $testCase.SelectSingleNode('skipped')) {
            throw "The required J5 Playwright qualification test case did not succeed: $requiredTestCase"
        }
    }

    $reportFiles = @($expectedXmlReports)
    foreach ($reportRoot in @(
            (Join-Path $repositoryRoot 'target\surefire-reports'),
            (Join-Path $repositoryRoot 'target\failsafe-reports'))) {
        if (Test-Path -LiteralPath $reportRoot -PathType Container) {
            $reportFiles += Get-ChildItem -LiteralPath $reportRoot -File |
                Where-Object { $_.Extension -in @('.xml', '.txt') } |
                ForEach-Object { $_.FullName }
        }
    }
    $reportFiles = @($reportFiles | Sort-Object -Unique)
    $sensitiveReportFiles = @()
    foreach ($reportFile in $reportFiles) {
        $reportContent = Get-Content -LiteralPath $reportFile -Raw -ErrorAction Stop
        $isXml = [System.IO.Path]::GetExtension($reportFile) -ieq '.xml'
        if (Test-J5SensitiveReportContent -Content $reportContent -IsXml $isXml) {
            $sensitiveReportFiles += $reportFile
        }
    }
    if ($sensitiveReportFiles.Count -ne 0) {
        throw 'Sensitive data was found in a J5 Playwright Maven test report'
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

Write-Host 'J5_PLAYWRIGHT_LOOPBACK_QUALIFICATION=PASS'
Write-Host 'WORKER_PROTOCOL_AND_SECURITY_TESTS=PASS'
Write-Host 'WORKER_NETWORK_OBSERVATION_TESTS=PASS'
Write-Host 'J5_EVENT_DATA_ROUTE_TESTS=PASS'
# These delay assertions qualify supervisor.open, not the grouped manual-J5 authority.
Write-Host 'J5_GAP_MEASUREMENT_SCOPE=LEGACY_GENERIC_SUPERVISOR_OPEN'
Write-Host 'GROUPED_MANUAL_J5_QUALIFICATION=SEPARATE_MANUAL_J5_GROUPED_QUALIFICATION_IT'
Write-Host 'MINIMUM_PERSISTED_NETWORK_START_GAP_MS=PASS_GE_3000'
Write-Host 'MINIMUM_LOOPBACK_SERVER_ARRIVAL_GAP_NS=PASS_GE_3000000000'
Write-Host 'J5_REQUESTED_AT_GAPS=PASS_GE_3000_MS'
Write-Host 'J5_LOOPBACK_ARRIVAL_GAPS=PASS_GE_3000000000_NS'
Write-Host 'CROSS_WORKER_REQUESTED_AT_GAP=PASS_GE_3000_MS'
Write-Host 'CROSS_WORKER_LOOPBACK_ARRIVAL_GAP=PASS_GE_3000000000_NS'
Write-Host 'STOP_DURING_DELAY_NEW_REQUEST_COUNT=0'
Write-Host 'MAVEN_REPORT_SENSITIVE_SCANNER=PASS_SCANNER_PARITY_ENCODED_TEXT_ATTRIBUTES_PROPERTIES'
Write-Host 'RUNTIME_FILE_CANARY_SCAN=PASS_ISOLATED_WRITABLE_ROOTS_PER_RUN_RANDOM_CANARY'
Write-Host 'SENSITIVE_DATA_IN_TEST_REPORTS=NO'
Write-Host 'ORIGIN=http://127.0.0.1:<ephemeral>'
Write-Host 'PROVIDER_ACCESS_PERFORMED=NO'
