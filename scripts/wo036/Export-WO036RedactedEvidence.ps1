[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$StatePath,
    [Parameter(Mandatory = $true)][guid]$ExportId,
    [Parameter(Mandatory = $true)]
    [ValidateSet('PRE_COLLISION', 'FINAL')][string]$Phase,
    [Parameter(Mandatory = $true)][string]$OutputPath
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WO036-CampaignTools.psm1') -Force
Export-WO036RedactedEvidence @PSBoundParameters
