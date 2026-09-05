[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$StatePath,
    [Parameter(Mandatory = $true)][guid]$ExportId
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WO036-CampaignTools.psm1') -Force
Copy-WO036LocalLabDatabase @PSBoundParameters
