[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$BrowserCachePath)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$browserCache = (Resolve-Path -LiteralPath $BrowserCachePath).Path
if (-not (Test-Path -LiteralPath $browserCache -PathType Container)) {
    throw 'Select an existing dedicated Playwright browser cache. No browser is installed by this script.'
}
$javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
$policyClass = Join-Path $repositoryRoot 'target/classes/com/bettingproject/sofascorelocal/domain/scheduledevents/J3DatePolicy.class'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf) -or -not (Test-Path -LiteralPath $policyClass -PathType Leaf)) {
    throw 'Run mvnw.cmd clean verify with Java 25 before this explicit browser qualification.'
}
$previousBrowserCache = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH', 'Process')
$previousSkipDownload = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD', 'Process')
Push-Location $repositoryRoot
try {
    New-Item -ItemType Directory -Path '.tmp' -Force | Out-Null
    # Resolves browser test dependencies only; no application, database, provider or J7 test is started.
    & .\mvnw.cmd '-Pj7-browser-origin-loopback-qualification' 'dependency:build-classpath' '-Dmdep.outputFile=.tmp/wo060-date-browser-classpath.txt' *> '.tmp/wo060-j3-date-browser-dependencies.log'
    $dependencyExit = $LASTEXITCODE
    if ($dependencyExit -ne 0) { throw "Browser dependency classpath failed with exit code $dependencyExit" }
    $dependencies = [System.IO.File]::ReadAllText((Join-Path $repositoryRoot '.tmp/wo060-date-browser-classpath.txt')).Trim()
    $qualificationClasspath = 'target/classes;target/test-classes;' + $dependencies
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
    & $javaExecutable --class-path $qualificationClasspath 'scripts/qualification/J3DateFormCheck.java' *> '.tmp/wo060-j3-date-browser.log'
    $browserExit = $LASTEXITCODE
    if ($browserExit -ne 0) { throw "J3 date form qualification failed with exit code $browserExit; inspect .tmp/wo060-j3-date-browser.log" }
    $result = Get-Content -LiteralPath '.tmp/wo060-j3-date-browser.log' -Tail 1
    if ($result -cne 'WO060_J3_DATE_FORMS=PASS;CASES=22;PROVIDER_CALLS=0;LAB_REQUESTS=0') {
        throw 'The 22-case native Chromium qualification result is absent'
    }
    Write-Output $result
}
finally {
    [Environment]::SetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH', $previousBrowserCache, 'Process')
    [Environment]::SetEnvironmentVariable('PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD', $previousSkipDownload, 'Process')
    Pop-Location
}
