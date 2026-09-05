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
    throw 'The dedicated Playwright browser cache is absent; no installation is performed by WO-037.'
}
$browserCacheItem = Get-Item -LiteralPath $browserCache -Force
$browserCacheTarget = $browserCacheItem.ResolveLinkTarget($true)
$browserCache = if ($null -eq $browserCacheTarget) {
    $browserCacheItem.FullName
}
else {
    $browserCacheTarget.FullName
}
$headlessShellInstallations = @(Get-ChildItem -LiteralPath $browserCache -Directory |
    Where-Object {
        $_.Name -like 'chromium_headless_shell-*' -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'INSTALLATION_COMPLETE') -PathType Leaf) -and
        @(Get-ChildItem -LiteralPath $_.FullName -Recurse -File `
            -Filter 'chrome-headless-shell.exe').Count -gt 0
    })
if ($headlessShellInstallations.Count -ne 1) {
    throw 'WO-037 requires one exact complete Chromium headless-shell installation in the selected cache.'
}

function Assert-PortFree {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port `
        -ErrorAction SilentlyContinue)
    if ($listeners.Count -ne 0) {
        throw "WO-037 requires loopback port $Port to be free."
    }
}

Assert-PortFree -Port 8087
Assert-PortFree -Port 8444

$pesterPath = Join-Path $repositoryRoot `
    'scripts\Tests\WO037BrowserOriginQualification.Tests.ps1'
$pesterResult = Invoke-Pester -Path $pesterPath -PassThru
if ($pesterResult.FailedCount -ne 0 -or $pesterResult.SkippedCount -ne 0) {
    throw 'The WO-037 static qualification guardrails failed.'
}

$previousBrowserCache = [Environment]::GetEnvironmentVariable(
    'PLAYWRIGHT_BROWSERS_PATH',
    'Process')
$reportPath = Join-Path $repositoryRoot `
    'target\failsafe-reports\TEST-com.bettingproject.sofascorelocal.qualification.J7BrowserOriginLoopbackQualificationIT.xml'
if (Test-Path -LiteralPath $reportPath -PathType Leaf) {
    Remove-Item -LiteralPath $reportPath -Force
}
$qualificationFailure = $null
$postconditionFailure = $null

Push-Location $repositoryRoot
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    & .\mvnw.cmd `
        '-Pj7-browser-origin-loopback-qualification' `
        "-Dj7.browser.cache=$browserCache" `
        -DskipTests `
        clean `
        test-compile
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit WO-037 browser qualification test compilation failed.'
    }

    & .\mvnw.cmd `
        '-Pj7-browser-origin-loopback-qualification' `
        "-Dj7.browser.cache=$browserCache" `
        '-DskipTests=false' `
        '-DskipITs=false' `
        'failsafe:integration-test@j7-browser-origin-loopback-qualification' `
        'failsafe:verify@j7-browser-origin-loopback-qualification'
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit WO-037 native Chromium loopback qualification failed.'
    }

    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
        throw 'The expected WO-037 Failsafe XML report is absent.'
    }
    [xml]$report = Get-Content -LiteralPath $reportPath -Raw
    $suite = $report.testsuite
    if ([int]$suite.tests -ne 1 -or [int]$suite.failures -ne 0 `
            -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
        throw 'The WO-037 browser qualification report is not one successful non-skipped test.'
    }
}
catch {
    $qualificationFailure = $_
}
finally {
    try {
        Pop-Location
        if ($null -eq $previousBrowserCache) {
            Remove-Item Env:PLAYWRIGHT_BROWSERS_PATH -ErrorAction SilentlyContinue
        }
        else {
            $env:PLAYWRIGHT_BROWSERS_PATH = $previousBrowserCache
        }
    }
    finally {
        try {
            Assert-PortFree -Port 8087
            Assert-PortFree -Port 8444
        }
        catch {
            $postconditionFailure = $_
        }
    }
}

if ($null -ne $postconditionFailure) {
    if ($null -ne $qualificationFailure) {
        throw 'The WO-037 qualification failed and a forbidden loopback listener remained after cleanup.'
    }
    throw $postconditionFailure
}
if ($null -ne $qualificationFailure) {
    throw $qualificationFailure
}

Write-Output 'WO037_BROWSER_ORIGIN_LOOPBACK_QUALIFICATION=PASS'
Write-Output 'REAL_CHROMIUM_CONTEXT=FRESH_NON_PERSISTENT'
Write-Output 'J7_PREVIEW_REFERRER_POLICY=SAME_ORIGIN'
Write-Output 'EXACT_LOOPBACK_ORIGIN_PREPARE=HTTP_200'
Write-Output 'OPAQUE_ORIGIN_NULL_PREPARE=HTTP_403'
Write-Output 'DELIVERY_EXECUTE_CALLS=0'
Write-Output 'RECONCILIATION_CALLS=0'
Write-Output 'DELIVERY_CLAIMS=0'
Write-Output 'RECEIVER_TRAFFIC=0'
Write-Output 'PROVIDER_TRAFFIC=0'
Write-Output 'NON_LOOPBACK_BROWSER_TRAFFIC=0'
Write-Output 'BROWSER_DOWNLOADS=0'
Write-Output 'FORBIDDEN_BROWSER_ARTIFACTS=0'
Write-Output 'LOOPBACK_LISTENER_RESIDUALS=0'
