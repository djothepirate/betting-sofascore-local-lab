[CmdletBinding()]
param(
    [ValidateSet('Eclipse', 'PowerShell', 'Table')]
    [string]$OutputFormat = 'Eclipse'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$profilePath = Join-Path $repositoryRoot 'docs\validation\WO058-GROUPED-LIVE-V9-SUSPENDED-20260911.json'
Require (Test-Path -LiteralPath $profilePath -PathType Leaf) 'V9 profile evidence is missing.'

$expectedProfilePath = [System.IO.Path]::GetFullPath($profilePath)
$resolvedProfilePath = (Resolve-Path -LiteralPath $profilePath).Path
Require ($resolvedProfilePath.Equals($expectedProfilePath, [System.StringComparison]::OrdinalIgnoreCase)) `
    'V9 profile evidence must be the versioned repository file.'

$profileBytes = [System.IO.File]::ReadAllBytes($resolvedProfilePath)
$profile = [System.Text.Encoding]::UTF8.GetString($profileBytes) | ConvertFrom-Json
$profileSha256 = (Get-FileHash -LiteralPath $resolvedProfilePath -Algorithm SHA256).Hash.ToLowerInvariant()

Require ($profileSha256 -match '^[0-9a-f]{64}$') 'V9 profile SHA-256 is invalid.'
Require ($profile.policyVersion -eq 'live-v9') 'V9 profile policy is invalid.'
Require ($profile.status -eq 'QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE') 'V9 profile status requires review.'
Require ($profile.qualifiedCapacity -eq 10) 'V9 profile capacity is invalid.'
Require ($profile.admission.profileAppliedAutomatically -eq $false) 'V9 profile must not be automatically applied.'
Require ($profile.admission.localReplayOnly -eq $true) 'V9 profile must remain local replay evidence.'
Require ($profile.sourceUpperBound.v8Profile.sha256 -ne $profileSha256) `
    'V9 profile SHA must remain distinct from V8 evidence provenance.'

$expectedEnvelopes = @{
    EVENT_DETAILS = @{ RequestMillis = 300; ProcessingMillis = 500 }
    EVENT_INCIDENTS = @{ RequestMillis = 300; ProcessingMillis = 400 }
    EVENT_STATISTICS = @{ RequestMillis = 350; ProcessingMillis = 400 }
    EVENT_LINEUPS = @{ RequestMillis = 300; ProcessingMillis = 450 }
}

foreach ($endpoint in $expectedEnvelopes.Keys) {
    $envelope = $profile.endpointEnvelopes.$endpoint
    Require ($null -ne $envelope) "V9 profile is missing the $endpoint envelope."
    Require ($envelope.requestMillis -eq $expectedEnvelopes[$endpoint].RequestMillis) `
        "V9 profile request envelope differs for $endpoint."
    Require ($envelope.processingMillis -eq $expectedEnvelopes[$endpoint].ProcessingMillis) `
        "V9 profile processing envelope differs for $endpoint."
}

# This utility is deliberately display-only. It neither writes the Eclipse launch
# configuration nor sets process/user/machine environment variables.
$settings = @(
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_QUALIFICATION_SHA256'; Value = $profileSha256 }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_J4_REQUEST_ENVELOPE'; Value = '300ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_J4_PROCESSING_ENVELOPE'; Value = '500ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_INCIDENTS_REQUEST_ENVELOPE'; Value = '300ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_INCIDENTS_PROCESSING_ENVELOPE'; Value = '400ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_STATISTICS_REQUEST_ENVELOPE'; Value = '350ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_STATISTICS_PROCESSING_ENVELOPE'; Value = '400ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_LINEUPS_REQUEST_ENVELOPE'; Value = '300ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V9_LINEUPS_PROCESSING_ENVELOPE'; Value = '450ms' }
)

switch ($OutputFormat) {
    'Eclipse' {
        foreach ($setting in $settings) {
            '<mapEntry key="{0}" value="{1}"/>' -f $setting.Name, $setting.Value
        }
    }
    'PowerShell' {
        foreach ($setting in $settings) {
            '$env:{0} = ''{1}''' -f $setting.Name, $setting.Value
        }
    }
    'Table' {
        $settings | Format-Table -AutoSize
    }
}

'V9_LAUNCHER_CONFIGURATION=REVIEW_REQUIRED'
'V9_LIVE_OPT_IN_CHANGED=NO'
'V9_PROVIDER_CALLS_EXECUTED=NO'
