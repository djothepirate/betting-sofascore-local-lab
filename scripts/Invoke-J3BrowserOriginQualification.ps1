[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BrowserCachePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$browserCache = (Resolve-Path -LiteralPath $BrowserCachePath).Path
if (-not (Test-Path -LiteralPath $browserCache -PathType Container)) {
    throw 'Select an existing dedicated Playwright browser cache. No browser is installed by this script.'
}
$javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
$filterClass = Join-Path $repositoryRoot 'target/classes/com/bettingproject/sofascorelocal/config/SecurityHeadersFilter.class'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf) -or
    -not (Test-Path -LiteralPath $filterClass -PathType Leaf)) {
    throw 'Run mvnw.cmd clean verify with Java 25 before this explicit browser qualification.'
}

$previousBrowserCache = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH', 'Process')
$previousSkipDownload = [Environment]::GetEnvironmentVariable('PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD', 'Process')
Push-Location $repositoryRoot
try {
    New-Item -ItemType Directory -Path '.tmp' -Force | Out-Null
    # This existing opt-in profile is used only to resolve browser test dependencies.
    # No J7 test, application, database, worker or provider is started by this Maven goal.
    & .\mvnw.cmd '-Pj7-browser-origin-loopback-qualification' 'dependency:build-classpath' '-Dmdep.outputFile=.tmp/wo060-browser-classpath.txt' *> '.tmp/wo060-j3-browser-dependencies.log'
    $dependencyExit = $LASTEXITCODE
    if ($dependencyExit -ne 0) { throw "Browser dependency classpath failed with exit code $dependencyExit" }

    $dependencies = [System.IO.File]::ReadAllText((Join-Path $repositoryRoot '.tmp/wo060-browser-classpath.txt')).Trim()
    $qualificationClasspath = 'target/classes;target/test-classes;' + $dependencies
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
    & $javaExecutable --class-path $qualificationClasspath 'scripts/qualification/J3BrowserOriginCheck.java' *> '.tmp/wo060-j3-browser-origin.log'
    $browserExit = $LASTEXITCODE
    if ($browserExit -ne 0) { throw "J3 browser origin qualification failed with exit code $browserExit; inspect .tmp/wo060-j3-browser-origin.log" }
    $result = Get-Content -LiteralPath '.tmp/wo060-j3-browser-origin.log' -Tail 1
    if ($result -cne 'WO060_J3_BROWSER_ORIGIN=PASS;CASES=32;PROVIDER_CALLS=0;LAB_REQUESTS=0') {
        throw 'The 32-case native Chromium qualification result is absent'
    }
    Write-Output $result
}
finally {
    [Environment]::SetEnvironmentVariable('PLAYWRIGHT_BROWSERS_PATH', $previousBrowserCache, 'Process')
    [Environment]::SetEnvironmentVariable('PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD', $previousSkipDownload, 'Process')
    Pop-Location
}
