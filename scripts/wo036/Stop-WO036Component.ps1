[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$StatePath,
    [Parameter(Mandatory = $true)]
    [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
    [string]$Component
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WO036-CampaignTools.psm1') -Force
Stop-WO036Component @PSBoundParameters
