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
    '\bProxySelector\b',
    '\borg\.openqa\.selenium\b',
    '\bcom\.microsoft\.playwright\b',
    '\bWebClient\s*\.(?:builder|create)\b'
)

$violations = @()
foreach ($pattern in $forbiddenPatterns) {
    $matches = $sourceFiles |
        Select-String -Pattern $pattern -CaseSensitive:$false
    if ($matches) {
        $violations += $matches
    }
}

$approvedRestClientConstruction = Join-Path $sourceRoot `
    'java\com\bettingproject\sofascorelocal\adapter\sofascore\transport\LoopbackScheduledEventsRestTransport.java'
$restClientConstructions = $sourceFiles |
    Select-String -Pattern '\bRestClient\s*\.(?:builder|create)\b' -CaseSensitive:$false |
    Where-Object { $_.Path -ne $approvedRestClientConstruction }
if ($restClientConstructions) {
    $violations += $restClientConstructions
}
if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error ($_.ToString()) }
    throw 'J3 source guardrail scan failed'
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
