[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$StatePath,
    [Parameter(Mandatory = $true)]
    [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
    [string]$Component,
    [guid]$ExportId = [guid]::Empty
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WO036-CampaignTools.psm1') -Force
Start-WO036Component @PSBoundParameters
