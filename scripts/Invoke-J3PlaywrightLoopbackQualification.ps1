[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$browserCache = Join-Path $repositoryRoot '.tmp\provider-playwright-browsers'
if (-not (Test-Path -LiteralPath $browserCache -PathType Container)) {
    throw 'The dedicated Playwright browser cache is absent; run Install-J3PlaywrightRuntime.ps1 explicitly first'
}
$browserCache = (Resolve-Path -LiteralPath $browserCache).Path
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

Push-Location $repositoryRoot
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    & .\mvnw.cmd `
        '-Pprovider-playwright-runtime' `
        '-DskipTests=false' `
        '-Dtest=ProviderPlaywrightWorkerProtocolTest,ProviderPlaywrightWorkerSecurityContractTest' `
        clean `
        test
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J3 Playwright worker protocol and security tests failed'
    }

    & .\mvnw.cmd `
        '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
        -DskipTests `
        package
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J3 Playwright worker package failed'
    }

    & .\mvnw.cmd `
        '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
        '-DskipTests=false' `
        '-DskipITs=false' `
        "-Dprovider.playwright.browser-cache=$browserCache" `
        'failsafe:integration-test@provider-playwright-loopback-qualification' `
        'failsafe:verify@provider-playwright-loopback-qualification'
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J3 Playwright loopback qualification failed'
    }

    $expectedXmlReports = @(
        (Join-Path $repositoryRoot `
            'target\surefire-reports\TEST-com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerProtocolTest.xml'),
        (Join-Path $repositoryRoot `
            'target\surefire-reports\TEST-com.bettingproject.sofascorelocal.provider.playwright.worker.ProviderPlaywrightWorkerSecurityContractTest.xml'),
        (Join-Path $repositoryRoot `
            'target\failsafe-reports\TEST-com.bettingproject.sofascorelocal.application.network.playwright.ProviderPlaywrightLocalQualificationIT.xml')
    )
    foreach ($reportPath in $expectedXmlReports) {
        if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
            throw "The expected J3 Playwright Maven test report is absent: $reportPath"
        }
        [xml]$report = Get-Content -LiteralPath $reportPath -Raw
        $suite = $report.testsuite
        $tests = [int]$suite.tests
        $failures = [int]$suite.failures
        $errors = [int]$suite.errors
        $skipped = [int]$suite.skipped
        if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
            throw "The expected J3 Playwright Maven test report is not a successful non-skipped suite: $reportPath"
        }
    }

    $reportFiles = @($expectedXmlReports)
    foreach ($reportRoot in @(
            (Join-Path $repositoryRoot 'target\surefire-reports'),
            (Join-Path $repositoryRoot 'target\failsafe-reports'))) {
        if (Test-Path -LiteralPath $reportRoot -PathType Container) {
            $reportFiles += Get-ChildItem -LiteralPath $reportRoot -File |
                Where-Object { $_.Extension -in @('.xml', '.txt') }
        }
    }
    $reportFiles = $reportFiles | Sort-Object -Unique
    $sensitiveFindings = $reportFiles | Select-String -Pattern @(
        'local-only-value',
        '(?i)(authorization|proxy-authorization|cookie|set-cookie)\s*[:=]\s*\S+',
        '(?i)["'']?(access|refresh|id)_?token["'']?\s*[:=]\s*["'']?[^<\s]+'
    )
    if (@($sensitiveFindings).Count -ne 0) {
        throw 'Sensitive data was found in a J3 Playwright Maven test report'
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

Write-Host 'J3_PLAYWRIGHT_LOOPBACK_QUALIFICATION=PASS'
Write-Host 'WORKER_PROTOCOL_AND_SECURITY_TESTS=PASS'
Write-Host 'SENSITIVE_DATA_IN_TEST_REPORTS=NO'
Write-Host 'ORIGIN=http://127.0.0.1:<ephemeral>'
Write-Host 'PROVIDER_ACCESS_PERFORMED=NO'
