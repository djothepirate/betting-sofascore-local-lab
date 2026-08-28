[CmdletBinding()]
param(
    [switch]$WithIntegrationTests
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
if ($WithIntegrationTests) {
    & (Join-Path $PSScriptRoot 'Preflight-Local.ps1')
}
else {
    & (Join-Path $PSScriptRoot 'Preflight-Local.ps1') -SkipDocker -SkipEnvironmentFile
}

$sourceRoot = Join-Path $repositoryRoot 'src\main'
$sourceFiles = Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
    Where-Object { $_.Extension -in @('.java', '.yml', '.yaml', '.properties') }
$forbiddenPatterns = @(
    'https?://[^\s"'']*sofascore',
    'api\.sofascore',
    'flaresolverr',
    'localhost:8191',
    '\bProxySelector\b',
    '\borg\.openqa\.selenium\b',
    '\bcom\.microsoft\.playwright\b',
    '\bWebClient\s*\.(?:builder|create)\b'
)
$approvedProviderOriginDeclarations = @(
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\domain\provider\ScheduledEventsProviderPageRequest.java'),
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\domain\provider\EventDetailsProviderRequest.java'),
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\domain\provider\TournamentScheduledEventsProviderRequest.java')
)
$approvedProviderOriginLine =
    'public static final String EXPECTED_ORIGIN = "https://www.sofascore.com";'

$violations = @()
foreach ($pattern in $forbiddenPatterns) {
    $matches = $sourceFiles |
        Select-String -Pattern $pattern -CaseSensitive:$false
    if ($pattern -eq $forbiddenPatterns[0]) {
        $matches = $matches | Where-Object {
            -not ($_.Path -in $approvedProviderOriginDeclarations -and
                $_.Line.Trim() -ceq $approvedProviderOriginLine)
        }
    }
    if ($matches) {
        $violations += $matches
    }
}

$approvedRestClientConstructions = @(
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\LoopbackScheduledEventsRestTransport.java'),
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderJ5EventDataRestTransport.java')
)
$restClientConstructions = $sourceFiles |
    Select-String -Pattern '\bRestClient\s*\.(?:builder|create)\b' -CaseSensitive:$false |
    Where-Object { $_.Path -notin $approvedRestClientConstructions }
if ($restClientConstructions) {
    $violations += $restClientConstructions
}

$removedJ3RestTransports = @(
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderScheduledEventsRestTransport.java'),
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderTournamentScheduledEventsRestTransport.java')
)
foreach ($removedTransport in $removedJ3RestTransports) {
    if (Test-Path -LiteralPath $removedTransport) {
        throw "Retired J3 RestClient transport is present: $removedTransport"
    }
}

$requiredJ3PlaywrightTransports = @(
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderScheduledEventsPlaywrightTransport.java'),
    (Join-Path $sourceRoot `
        'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderTournamentScheduledEventsPlaywrightTransport.java')
)
foreach ($requiredTransport in $requiredJ3PlaywrightTransports) {
    if (-not (Test-Path -LiteralPath $requiredTransport)) {
        throw "Required J3 Playwright transport is absent: $requiredTransport"
    }
}

$removedJ4RestTransport = Join-Path $sourceRoot `
    'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderEventDetailsRestTransport.java'
if (Test-Path -LiteralPath $removedJ4RestTransport) {
    throw "Retired J4 RestClient transport is present: $removedJ4RestTransport"
}

$requiredJ4PlaywrightTransport = Join-Path $sourceRoot `
    'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderEventDetailsPlaywrightTransport.java'
if (-not (Test-Path -LiteralPath $requiredJ4PlaywrightTransport)) {
    throw "Required J4 Playwright transport is absent: $requiredJ4PlaywrightTransport"
}

$playwrightSourceRoot = Join-Path $repositoryRoot 'src\provider-playwright'
$playwrightSourceFiles = Get-ChildItem -LiteralPath $playwrightSourceRoot -Recurse -File |
    Where-Object { $_.Extension -eq '.java' }
$forbiddenPlaywrightPatterns = @(
    'flaresolverr',
    'localhost:8191',
    '\.setProxy\s*\(',
    '--proxy',
    '\.setUserAgent\s*\(',
    '\bstorageState\b',
    '\brecordHar',
    '\brecordVideo',
    '\.screenshot\s*\(',
    '\bconnectOverCDP\b',
    '\bpage\.content\s*\(',
    '\bresponse\.text\s*\('
)
foreach ($pattern in $forbiddenPlaywrightPatterns) {
    $matches = $playwrightSourceFiles |
        Select-String -Pattern $pattern -CaseSensitive:$false
    if ($matches) {
        $violations += $matches
    }
}
if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error ($_.ToString()) }
    throw 'J3/J4 source guardrail scan failed'
}

Push-Location $repositoryRoot
try {
    & .\mvnw.cmd clean verify
    if ($LASTEXITCODE -ne 0) {
        throw 'Standard Maven verification failed'
    }

    if ($WithIntegrationTests) {
        & .\mvnw.cmd -Pintegration-tests verify
        if ($LASTEXITCODE -ne 0) {
            throw 'Integration tests failed'
        }
    }
}
finally {
    Pop-Location
}

Write-Host 'VERIFY_RESULT=PASS'
Write-Host "INTEGRATION_TESTS_EXECUTED=$WithIntegrationTests"
Write-Host 'SOFASCORE_NETWORK_CALLS_EXECUTED=NO'
