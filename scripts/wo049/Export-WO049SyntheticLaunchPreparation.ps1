[CmdletBinding()]
param()

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) 'wo046/WO046-LaunchPreparation.psm1'
Import-Module $modulePath -Force
# All values below are fixtures. This script accepts no private input and performs no write.
$root = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetPathRoot($PSScriptRoot)) 'WO049 synthetic fixture'))
$environment = New-WO046LocalLabLaunchEnvironment `
    -GoId '00000000-0000-4000-8000-000000000001' `
    -OwnerGoDocumentSha256 ('b' * 64) -ClientCertificateSha256 ('a' * 64) `
    -DatabaseName 'wo049_synthetic' -DatabaseUser 'wo049_fixture' `
    -DatabasePassword 'WO049_ONLY_SYNTHETIC_NOT_A_SECRET' -ExportDirectory (Join-Path $root 'exports')
Assert-WO046LocalLabLaunchEnvironment $environment
$startInfo = New-WO046LocalLabStartInfo -JavaPath (Join-Path $root 'jdk/bin/java.exe') `
    -JarPath (Join-Path $root 'application with spaces.jar') -WorkingDirectory $root `
    -InstanceId '00000000-0000-4000-8000-000000000002' -Environment $environment `
    -HostEnvironment @{ SystemRoot = (Join-Path $root 'Windows') }
$stop = New-WO046TechnicalStopBytes -GoId '00000000-0000-4000-8000-000000000001' `
    -OwnerGoDocumentSha256 ('b' * 64) -StoppedAtUtc ([DateTimeOffset]'2030-01-02T03:04:05.123456Z')
$stopResult = Test-WO046TechnicalStopBytes -Bytes $stop `
    -ExpectedGoId '00000000-0000-4000-8000-000000000001' -ExpectedOwnerGoDocumentSha256 ('b' * 64)
$result = [ordered]@{
    fixtureOnly = $true
    environment = $environment
    argumentList = @($startInfo.ArgumentList)
    stopDocumentBase64 = [Convert]::ToBase64String($stop)
    stopSha256 = $stopResult.Sha256
    stopByteCount = $stopResult.ByteCount
    processStarted = $false
    filesWritten = 0
    connectionsOpened = 0
}
$result | ConvertTo-Json -Depth 5 -Compress
