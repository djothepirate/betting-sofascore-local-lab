[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$StatePath)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WO036-CampaignTools.psm1') -Force
Test-WO036PrivateLogRedaction @PSBoundParameters
