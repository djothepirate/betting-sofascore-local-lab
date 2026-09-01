[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('bp local distribution ' + [Guid]::NewGuid().ToString('N'))
$scriptsDirectory = Join-Path $fixtureRoot 'scripts'
$binDirectory = Join-Path $fixtureRoot 'bin'
$argumentsFile = Join-Path $fixtureRoot 'java-arguments.txt'
$workingDirectoryFile = Join-Path $fixtureRoot 'java-working-directory.txt'
$environmentFile = Join-Path $fixtureRoot 'java-environment.txt'
$dockerArgumentsFile = Join-Path $fixtureRoot 'docker-arguments.txt'
$originalPath = $env:PATH
$hadArgumentsFileEnvironment = Test-Path Env:DISTRIBUTION_JAVA_ARGS_FILE
$originalArgumentsFileEnvironment = $env:DISTRIBUTION_JAVA_ARGS_FILE
$hadWorkingDirectoryEnvironment = Test-Path Env:DISTRIBUTION_JAVA_CWD_FILE
$originalWorkingDirectoryEnvironment = $env:DISTRIBUTION_JAVA_CWD_FILE
$hadExitCodeEnvironment = Test-Path Env:DISTRIBUTION_JAVA_EXIT_CODE
$originalExitCodeEnvironment = $env:DISTRIBUTION_JAVA_EXIT_CODE
$hadEnvironmentFileEnvironment = Test-Path Env:DISTRIBUTION_JAVA_ENV_FILE
$originalEnvironmentFileEnvironment = $env:DISTRIBUTION_JAVA_ENV_FILE
$hadDockerEndpointEnvironment = Test-Path Env:DISTRIBUTION_DOCKER_ENDPOINT
$originalDockerEndpointEnvironment = $env:DISTRIBUTION_DOCKER_ENDPOINT
$hadDockerHostEnvironment = Test-Path Env:DOCKER_HOST
$originalDockerHostEnvironment = $env:DOCKER_HOST
$hadDockerContextEnvironment = Test-Path Env:DOCKER_CONTEXT
$originalDockerContextEnvironment = $env:DOCKER_CONTEXT
$hadDockerArgumentsEnvironment = Test-Path Env:DISTRIBUTION_DOCKER_ARGS_FILE
$originalDockerArgumentsEnvironment = $env:DISTRIBUTION_DOCKER_ARGS_FILE
$poisonedEnvironment = [ordered]@{
    'SPRING_APPLICATION_JSON' = '{"server":{"address":"0.0.0.0"},"sofascore":{"enabled":true}}'
    'SPRING_CONFIG_IMPORT' = 'optional:https://example.invalid/config'
    'SPRING_PROFILES_INCLUDE' = 'sofascore-live-test'
    'SPRING_DATASOURCE_URL' = 'jdbc:postgresql://example.invalid/forbidden'
    'SPRING_FLYWAY_URL' = 'jdbc:postgresql://example.invalid/forbidden'
    'SERVER_ADDRESS' = '0.0.0.0'
    'MANAGEMENT_SERVER_ADDRESS' = '0.0.0.0'
    'SOFASCORE_ENABLED' = 'true'
    'SOFASCORE_PLAYWRIGHT_ENABLED' = 'true'
    'SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION' = 'true'
    'SOFASCORE_J3_QUALIFICATION_ENABLED' = 'true'
    'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED' = 'true'
    'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED' = 'true'
    'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED' = 'true'
    'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED' = 'true'
    'SOFASCORE_AUTOMATIC_REFRESH_ENABLED' = 'true'
    'SOFASCORE_LIVE_POLLING_ENABLED' = 'true'
    'SOFASCORE_BASE_URL' = 'https://example.invalid/forbidden'
    'OPTIONAL_INTEGRATION_ENABLED' = 'true'
    'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED' = 'true'
    'OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION' = 'true'
    '_JAVA_OPTIONS' = '-Djdk.httpclient.disableRetryConnect=false -Dspring.profiles.include=sofascore-live-test'
    'JAVA_TOOL_OPTIONS' = '-Dspring.profiles.include=sofascore-live-test'
    'JDK_JAVA_OPTIONS' = '-Dspring.profiles.include=sofascore-live-test'
}
$originalPoisonedEnvironment = @{}
$missingPoisonedEnvironment = @()

try {
    New-Item -ItemType Directory -Path $scriptsDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $binDirectory -Force | Out-Null

    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'ci\distribution\Preflight-Local.ps1') -Destination $scriptsDirectory
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'ci\distribution\Start-Local.ps1') -Destination $scriptsDirectory
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'ci\distribution\Stop-Local.ps1') -Destination $scriptsDirectory

    $jarPath = Join-Path $fixtureRoot 'betting-sofascore-local-lab-1.2.3.jar'
    Set-Content -LiteralPath $jarPath -Value 'fixture-jar' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixtureRoot '.env') -Value 'POSTGRES_DB=fixture' -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'compose.yaml') -Value 'services: {}' -Encoding ASCII

    $fakeJava = @'
@echo off
if "%~1"=="--version" (
  echo openjdk 25 2025-09-16
  exit /b 0
)
> "%DISTRIBUTION_JAVA_ARGS_FILE%" echo %*
> "%DISTRIBUTION_JAVA_CWD_FILE%" echo %CD%
set > "%DISTRIBUTION_JAVA_ENV_FILE%"
if not "%DISTRIBUTION_JAVA_EXIT_CODE%"=="" exit /b %DISTRIBUTION_JAVA_EXIT_CODE%
exit /b 0
'@
    Set-Content -LiteralPath (Join-Path $binDirectory 'java.cmd') -Value $fakeJava -Encoding ASCII

    $fakeDocker = @'
@echo off
>> "%DISTRIBUTION_DOCKER_ARGS_FILE%" echo %*
if "%~1"=="--host" (
  shift
  shift
)
if "%~1"=="inspect" (
  echo healthy
  exit /b 0
)
if "%~1"=="context" (
  if not "%DISTRIBUTION_DOCKER_ENDPOINT%"=="" (
    echo %DISTRIBUTION_DOCKER_ENDPOINT%
  ) else (
    echo npipe:////./pipe/docker_engine
  )
  exit /b 0
)
if "%~1"=="compose" (
  for %%A in (%*) do (
    if "%%~A"=="ps" (
      echo fixture-postgres
      exit /b 0
    )
  )
)
exit /b 0
'@
    Set-Content -LiteralPath (Join-Path $binDirectory 'docker.cmd') -Value $fakeDocker -Encoding ASCII

    $env:PATH = "$binDirectory$([IO.Path]::PathSeparator)$originalPath"
    $env:DISTRIBUTION_JAVA_ARGS_FILE = $argumentsFile
    $env:DISTRIBUTION_JAVA_CWD_FILE = $workingDirectoryFile
    $env:DISTRIBUTION_JAVA_ENV_FILE = $environmentFile
    $env:DISTRIBUTION_DOCKER_ARGS_FILE = $dockerArgumentsFile
    Remove-Item Env:DOCKER_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:DOCKER_CONTEXT -ErrorAction SilentlyContinue
    Remove-Item Env:DISTRIBUTION_DOCKER_ENDPOINT -ErrorAction SilentlyContinue

    $preflight = Join-Path $scriptsDirectory 'Preflight-Local.ps1'
    & $preflight -SkipDocker -SkipEnvironmentFile

    $hiddenJar = Join-Path $fixtureRoot 'application-fixture.bin'
    Move-Item -LiteralPath $jarPath -Destination $hiddenJar
    $missingJarRejected = $false
    try {
        & $preflight -SkipDocker -SkipEnvironmentFile
    }
    catch {
        if ($_.Exception.Message -notmatch 'Exactly one bundled application JAR') {
            throw
        }
        $missingJarRejected = $true
    }
    if (-not $missingJarRejected) {
        throw 'Preflight accepted a distribution without an application JAR.'
    }

    $auxiliaryJar = Join-Path $fixtureRoot 'betting-sofascore-local-lab-1.2.3-sources.jar'
    Set-Content -LiteralPath $auxiliaryJar -Value 'source-fixture-jar' -Encoding ASCII
    $auxiliaryJarRejected = $false
    try {
        & $preflight -SkipDocker -SkipEnvironmentFile
    }
    catch {
        if ($_.Exception.Message -notmatch 'Exactly one bundled application JAR') {
            throw
        }
        $auxiliaryJarRejected = $true
    }
    if (-not $auxiliaryJarRejected) {
        throw 'Preflight treated an auxiliary JAR as the application.'
    }
    Remove-Item -LiteralPath $auxiliaryJar -Force
    Move-Item -LiteralPath $hiddenJar -Destination $jarPath

    $extraJar = Join-Path $fixtureRoot 'betting-sofascore-local-lab-extra.jar'
    Set-Content -LiteralPath $extraJar -Value 'extra-fixture-jar' -Encoding ASCII
    $multipleJarsRejected = $false
    try {
        & $preflight -SkipDocker -SkipEnvironmentFile
    }
    catch {
        if ($_.Exception.Message -notmatch 'Exactly one bundled application JAR') {
            throw
        }
        $multipleJarsRejected = $true
    }
    if (-not $multipleJarsRejected) {
        throw 'Preflight accepted multiple bundled application JARs.'
    }
    Remove-Item -LiteralPath $extraJar -Force

    $env:DOCKER_HOST = 'tcp://example.invalid:2375'
    $remoteDockerHostRejected = $false
    try {
        & $preflight
    }
    catch {
        if ($_.Exception.Message -notmatch 'DOCKER_HOST must target a local Windows named pipe') {
            throw
        }
        $remoteDockerHostRejected = $true
    }
    if (-not $remoteDockerHostRejected) {
        throw 'Preflight accepted a remote DOCKER_HOST.'
    }
    Remove-Item Env:DOCKER_HOST

    $env:DISTRIBUTION_DOCKER_ENDPOINT = 'ssh://example.invalid'
    $remoteDockerContextRejected = $false
    try {
        & $preflight
    }
    catch {
        if ($_.Exception.Message -notmatch 'selected Docker context must target a local Windows named pipe') {
            throw
        }
        $remoteDockerContextRejected = $true
    }
    if (-not $remoteDockerContextRejected) {
        throw 'Preflight accepted a remote Docker context.'
    }
    Remove-Item Env:DISTRIBUTION_DOCKER_ENDPOINT

    $env:DOCKER_HOST = 'npipe:////./pipe/docker_engine'
    $env:DOCKER_CONTEXT = 'remote-fixture'
    $env:DISTRIBUTION_DOCKER_ENDPOINT = 'ssh://example.invalid'
    $contextPriorityRejected = $false
    try {
        & $preflight
    }
    catch {
        if ($_.Exception.Message -notmatch 'selected Docker context must target a local Windows named pipe') {
            throw
        }
        $contextPriorityRejected = $true
    }
    if (-not $contextPriorityRejected) {
        throw 'DOCKER_CONTEXT did not take precedence over DOCKER_HOST in the local guard.'
    }
    Remove-Item Env:DOCKER_HOST
    Remove-Item Env:DOCKER_CONTEXT
    Remove-Item Env:DISTRIBUTION_DOCKER_ENDPOINT

    $env:DOCKER_HOST = 'tcp://example.invalid:2375'
    $remoteStopRejected = $false
    try {
        & (Join-Path $scriptsDirectory 'Stop-Local.ps1')
    }
    catch {
        if ($_.Exception.Message -notmatch 'DOCKER_HOST must target a local Windows named pipe') {
            throw
        }
        $remoteStopRejected = $true
    }
    if (-not $remoteStopRejected) {
        throw 'Stop-Local accepted a remote DOCKER_HOST.'
    }
    Remove-Item Env:DOCKER_HOST

    foreach ($environmentName in $poisonedEnvironment.Keys) {
        $environmentPath = "Env:$environmentName"
        $existingEnvironment = Get-Item -LiteralPath $environmentPath -ErrorAction SilentlyContinue
        if ($null -eq $existingEnvironment) {
            $missingPoisonedEnvironment += $environmentName
        }
        else {
            $originalPoisonedEnvironment[$environmentName] = $existingEnvironment.Value
        }
        Set-Item -LiteralPath $environmentPath -Value $poisonedEnvironment[$environmentName]
    }

    & (Join-Path $scriptsDirectory 'Start-Local.ps1') -RunApplication
    if (-not (Test-Path -LiteralPath $argumentsFile -PathType Leaf)) {
        throw 'The distribution launcher did not invoke the bundled JAR.'
    }
    if (-not (Test-Path -LiteralPath $workingDirectoryFile -PathType Leaf)) {
        throw 'The distribution launcher did not record its Java working directory.'
    }
    if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
        throw 'The distribution launcher did not record its Java environment.'
    }

    $actualArguments = (Get-Content -LiteralPath $argumentsFile -Raw).Trim()
    $requiredArguments = @(
        '-Djdk.httpclient.disableRetryConnect=true'
        '-Djdk.httpclient.redirects.retrylimit=1'
        '-Djdk.httpclient.enableAllMethodRetry=false'
        '-jar'
        $jarPath
        '--spring.profiles.active=local'
        '--server.address=127.0.0.1'
        '--server.port=8087'
        '--sofascore.enabled=false'
        '--sofascore.playwright.enabled=false'
        '--sofascore.playwright.loopback-qualification=false'
        '--sofascore.playwright.loopback-origin='
        '--sofascore.j3-qualification-enabled=false'
        '--sofascore.j4-event-details-qualification-enabled=false'
        '--sofascore.j4-event-details-phase2-enabled=false'
        '--sofascore.j5-event-data-qualification-enabled=false'
        '--sofascore.tournament-event-discovery-enabled=false'
        '--sofascore.automatic-refresh-enabled=false'
        '--sofascore.live-polling-enabled=false'
        '--optional-integration.enabled=false'
        '--optional-integration.remote-delivery-authorized=false'
        '--optional-integration.official-permission-status=NOT_EVIDENCED'
        '--optional-integration.loopback-qualification=false'
        '--optional-integration.loopback-origin='
        '--optional-integration.automatic-retry-enabled=false'
    )
    $previousIndex = -1
    foreach ($requiredArgument in $requiredArguments) {
        $argumentIndex = $actualArguments.IndexOf(
            $requiredArgument,
            $previousIndex + 1,
            [StringComparison]::Ordinal
        )
        if ($argumentIndex -lt 0) {
            throw "Missing or out-of-order Java argument: $requiredArgument"
        }
        $previousIndex = $argumentIndex
    }

    $actualWorkingDirectory = (Get-Content -LiteralPath $workingDirectoryFile -Raw).Trim()
    if ([IO.Path]::GetFullPath($actualWorkingDirectory) -ine [IO.Path]::GetFullPath($fixtureRoot)) {
        throw "The bundled JAR was not launched from the distribution root: $actualWorkingDirectory"
    }

    $unsafeChildEnvironment = @(
        Get-Content -LiteralPath $environmentFile | Where-Object {
            $_ -match '^(_JAVA_OPTIONS=|JAVA_TOOL_OPTIONS=|JDK_JAVA_OPTIONS=|SPRING_APPLICATION_JSON=|SPRING_(CONFIG|PROFILES|DATASOURCE|FLYWAY)_|SERVER_|MANAGEMENT_SERVER_|SOFASCORE_|OPTIONAL_INTEGRATION_)'
        }
    )
    if ($unsafeChildEnvironment.Count -ne 0) {
        throw "Unsafe ambient configuration reached Java: $($unsafeChildEnvironment -join ', ')"
    }

    & (Join-Path $scriptsDirectory 'Stop-Local.ps1')
    & (Join-Path $scriptsDirectory 'Stop-Local.ps1') -RemoveData

    $dockerInvocations = @(Get-Content -LiteralPath $dockerArgumentsFile)
    $requiredDockerInvocations = @(
        '--host npipe:////./pipe/docker_engine compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env config --quiet'
        '--host npipe:////./pipe/docker_engine compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env up -d postgres'
        '--host npipe:////./pipe/docker_engine compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env ps -q postgres'
        '--host npipe:////./pipe/docker_engine compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env down'
        '--host npipe:////./pipe/docker_engine compose --project-name betting-sofascore-local-lab --file compose.yaml --env-file .env down --volumes'
    )
    foreach ($requiredDockerInvocation in $requiredDockerInvocations) {
        if ($dockerInvocations -notcontains $requiredDockerInvocation) {
            throw "Missing exact Docker invocation: $requiredDockerInvocation"
        }
    }

    $env:DISTRIBUTION_JAVA_EXIT_CODE = '7'
    $nonZeroExitRejected = $false
    try {
        & (Join-Path $scriptsDirectory 'Start-Local.ps1') -RunApplication
    }
    catch {
        if ($_.Exception.Message -notmatch 'exited with code 7') {
            throw
        }
        $nonZeroExitRejected = $true
    }
    if (-not $nonZeroExitRejected) {
        throw 'The distribution launcher ignored a non-zero Java exit code.'
    }
    Remove-Item Env:DISTRIBUTION_JAVA_EXIT_CODE

    $launcherPaths = @(
        (Join-Path $scriptsDirectory 'Preflight-Local.ps1')
        (Join-Path $scriptsDirectory 'Start-Local.ps1')
        (Join-Path $scriptsDirectory 'Stop-Local.ps1')
    )
    $launcherSources = Get-Content -LiteralPath $launcherPaths -Raw
    if (($launcherSources -join "`n") -match 'mvnw(?:[.]cmd)?|pom[.]xml') {
        throw 'The distribution launchers still depend on Maven source files.'
    }

    Write-Host 'DISTRIBUTION_LAUNCHERS=PASS_DIRECT_JAR'
}
finally {
    $env:PATH = $originalPath
    if ($hadArgumentsFileEnvironment) {
        $env:DISTRIBUTION_JAVA_ARGS_FILE = $originalArgumentsFileEnvironment
    }
    else {
        Remove-Item Env:DISTRIBUTION_JAVA_ARGS_FILE -ErrorAction SilentlyContinue
    }
    if ($hadWorkingDirectoryEnvironment) {
        $env:DISTRIBUTION_JAVA_CWD_FILE = $originalWorkingDirectoryEnvironment
    }
    else {
        Remove-Item Env:DISTRIBUTION_JAVA_CWD_FILE -ErrorAction SilentlyContinue
    }
    if ($hadExitCodeEnvironment) {
        $env:DISTRIBUTION_JAVA_EXIT_CODE = $originalExitCodeEnvironment
    }
    else {
        Remove-Item Env:DISTRIBUTION_JAVA_EXIT_CODE -ErrorAction SilentlyContinue
    }
    if ($hadEnvironmentFileEnvironment) {
        $env:DISTRIBUTION_JAVA_ENV_FILE = $originalEnvironmentFileEnvironment
    }
    else {
        Remove-Item Env:DISTRIBUTION_JAVA_ENV_FILE -ErrorAction SilentlyContinue
    }
    if ($hadDockerEndpointEnvironment) {
        $env:DISTRIBUTION_DOCKER_ENDPOINT = $originalDockerEndpointEnvironment
    }
    else {
        Remove-Item Env:DISTRIBUTION_DOCKER_ENDPOINT -ErrorAction SilentlyContinue
    }
    if ($hadDockerHostEnvironment) {
        $env:DOCKER_HOST = $originalDockerHostEnvironment
    }
    else {
        Remove-Item Env:DOCKER_HOST -ErrorAction SilentlyContinue
    }
    if ($hadDockerContextEnvironment) {
        $env:DOCKER_CONTEXT = $originalDockerContextEnvironment
    }
    else {
        Remove-Item Env:DOCKER_CONTEXT -ErrorAction SilentlyContinue
    }
    if ($hadDockerArgumentsEnvironment) {
        $env:DISTRIBUTION_DOCKER_ARGS_FILE = $originalDockerArgumentsEnvironment
    }
    else {
        Remove-Item Env:DISTRIBUTION_DOCKER_ARGS_FILE -ErrorAction SilentlyContinue
    }
    foreach ($environmentName in $poisonedEnvironment.Keys) {
        if ($originalPoisonedEnvironment.ContainsKey($environmentName)) {
            Set-Item -LiteralPath "Env:$environmentName" -Value $originalPoisonedEnvironment[$environmentName]
        }
        elseif ($missingPoisonedEnvironment -contains $environmentName) {
            Remove-Item -LiteralPath "Env:$environmentName" -ErrorAction SilentlyContinue
        }
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
