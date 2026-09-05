[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$StatePath,
    [Parameter(Mandatory = $true)][string]$JavaExecutable,
    [Parameter(Mandatory = $true)][string]$LocalLabJar,
    [Parameter(Mandatory = $true)][string]$ExpectedLocalLabJarSha256,
    [Parameter(Mandatory = $true)][string]$ReceiverRepositoryRoot,
    [Parameter(Mandatory = $true)][string]$ReceiverJar,
    [Parameter(Mandatory = $true)][string]$ExpectedReceiverJarSha256
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WO036-CampaignTools.psm1') -Force
Register-WO036ExecutableArtifacts @PSBoundParameters
