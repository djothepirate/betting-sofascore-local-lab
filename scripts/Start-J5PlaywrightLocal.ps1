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

Push-Location $repositoryRoot
try {
    & .\mvnw.cmd '-Pprovider-playwright-runtime' '-DskipTests' clean package
    if ($LASTEXITCODE -ne 0) {
        throw 'The explicit J5 Playwright worker package failed'
    }
    $workerFinalName = @(& .\mvnw.cmd '-q' '-Pprovider-playwright-runtime' `
        '-DforceStdout' '-Dexpression=project.build.finalName' help:evaluate)
    if ($LASTEXITCODE -ne 0 -or $workerFinalName.Count -ne 1) {
        throw 'The Maven worker finalName could not be resolved exactly'
    }
    $workerJar = & (Join-Path $PSScriptRoot 'Resolve-ProviderWorkerJar.ps1') `
        -RepositoryRoot $repositoryRoot -FinalName $workerFinalName[0]
}
finally {
    Pop-Location
}

$legacyRestClassRelativePath = `
    'com\bettingproject\sofascorelocal\adapter\sofascore\transport\ProviderJ5EventDataRestTransport.class'
$legacyRestClasses = @(
    Join-Path $repositoryRoot "target\classes\$legacyRestClassRelativePath"
    Join-Path $repositoryRoot "target\provider-playwright-runtime\classes\$legacyRestClassRelativePath"
)
if (@($legacyRestClasses | Where-Object {
            Test-Path -LiteralPath $_ -PathType Leaf
        }).Count -ne 0) {
    throw 'Legacy J5 direct-transport bytecode remains after the mandatory clean build'
}
$legacyRestJarEntry = $legacyRestClassRelativePath.Replace('\', '/')
$workerArchive = [System.IO.Compression.ZipFile]::OpenRead($workerJar)
try {
    if (@($workerArchive.Entries | Where-Object {
                $_.FullName.EndsWith($legacyRestJarEntry, [System.StringComparison]::Ordinal)
            }).Count -ne 0) {
        throw 'Legacy J5 direct-transport bytecode remains in the Playwright worker artifact'
    }
}
finally {
    $workerArchive.Dispose()
}
$workerJar = (Resolve-Path -LiteralPath $workerJar).Path

$environmentNames = @(
    'PLAYWRIGHT_BROWSERS_PATH',
    'SOFASCORE_PLAYWRIGHT_ENABLED',
    'SOFASCORE_PLAYWRIGHT_WORKER_JAR',
    'SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION',
    'SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN',
    'OPTIONAL_INTEGRATION_ENABLED',
    'OPTIONAL_INTEGRATION_EXECUTION_MODE',
    'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED',
    'OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS',
    'OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION',
    'OPTIONAL_INTEGRATION_SENDER_QUALIFICATION',
    'OPTIONAL_INTEGRATION_RECEIVER_ORIGIN',
    'OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION',
    'OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN',
    'OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED',
    'OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256',
    'OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID',
    'OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'
)
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable(
        $name,
        'Process')
}

$applicationExitCode = 1
try {
    $env:PLAYWRIGHT_BROWSERS_PATH = $browserCache
    $env:SOFASCORE_PLAYWRIGHT_ENABLED = 'true'
    $env:SOFASCORE_PLAYWRIGHT_WORKER_JAR = $workerJar
    $env:SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION = 'false'
    $env:SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN = ''
    $env:OPTIONAL_INTEGRATION_ENABLED = 'false'
    $env:OPTIONAL_INTEGRATION_EXECUTION_MODE = 'DISABLED'
    $env:OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED = 'false'
    $env:OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS = 'NOT_EVIDENCED'
    $env:OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION = 'NOT_QUALIFIED'
    $env:OPTIONAL_INTEGRATION_SENDER_QUALIFICATION = 'NOT_QUALIFIED'
    $env:OPTIONAL_INTEGRATION_RECEIVER_ORIGIN = ''
    $env:OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION = 'false'
    $env:OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN = ''
    $env:OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED = 'false'
    $env:OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256 = ''
    $env:OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID = ''
    $env:OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256 = ''

    Write-Host 'J5_PLAYWRIGHT_LOCAL_LAUNCH=READY'
    Write-Host 'PLAYWRIGHT_RUNTIME_OPT_IN=TRUE'
    Write-Host 'PLAYWRIGHT_WORKER_ARTIFACT=VERIFIED_PRESENT'
    Write-Host 'PLAYWRIGHT_BROWSER_CACHE=VERIFIED_COMPLETE'
    Write-Host 'PLAYWRIGHT_IMPLICIT_BROWSER_DOWNLOAD=DISABLED'
    Write-Host 'LEGACY_J5_REST_BYTECODE=ABSENT'
    Write-Host 'PROVIDER_ACCESS_PERFORMED=NO'

    & (Join-Path $PSScriptRoot 'Start-Local.ps1')
    Write-Host 'APPLICATION_STARTING_WITH_J5_PLAYWRIGHT=YES'

    Push-Location $repositoryRoot
    try {
        & .\mvnw.cmd `
            '-Pprovider-playwright-runtime' `
            '-Dspring-boot.run.profiles=local' `
            spring-boot:run
        $applicationExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($name in $environmentNames) {
        $previousValue = $previousEnvironment[$name]
        if ($null -eq $previousValue) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        }
        else {
            [Environment]::SetEnvironmentVariable(
                $name,
                $previousValue,
                'Process')
        }
    }
}

if ($applicationExitCode -ne 0) {
    throw "The J5 Playwright local application exited with code $applicationExitCode"
}
