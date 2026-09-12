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
$profilePath = Join-Path $repositoryRoot 'docs\validation\WO058-GROUPED-LIVE-V10-PROFILE-20260912.json'
Require (Test-Path -LiteralPath $profilePath -PathType Leaf) 'V10 profile evidence is missing.'

$expectedProfilePath = [System.IO.Path]::GetFullPath($profilePath)
$resolvedProfilePath = (Resolve-Path -LiteralPath $profilePath).Path
Require ($resolvedProfilePath.Equals($expectedProfilePath, [System.StringComparison]::OrdinalIgnoreCase)) `
    'V10 profile evidence must be the versioned repository file.'

$profileBytes = [System.IO.File]::ReadAllBytes($resolvedProfilePath)
$profile = [System.Text.Encoding]::UTF8.GetString($profileBytes) | ConvertFrom-Json
$profileSha256 = (Get-FileHash -LiteralPath $resolvedProfilePath -Algorithm SHA256).Hash.ToLowerInvariant()

Require ($profileSha256 -match '^[0-9a-f]{64}$') 'V10 profile SHA-256 is invalid.'
Require ($profile.policyVersion -eq 'live-v10') 'V10 profile policy is invalid.'
Require ($profile.status -eq 'QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE') `
    'V10 profile must carry the committed local replay qualification state.'
Require ($profile.qualification.independentV10BindingRequired -eq $true) `
    'V10 profile must require an independent V10 compiled-class binding.'
Require ($profile.qualifiedCapacity -eq 8) 'V10 qualified capacity is invalid.'
Require ($profile.candidateConfiguration.maximumSelectedMatches -eq 8) 'V10 selection cap is invalid.'
Require ($profile.candidateConfiguration.nominalEndpointsPerMinute -eq 32) 'V10 nominal minute cadence is invalid.'
Require ($profile.candidateConfiguration.maximumDurableDeparturesPer60Seconds -eq 35) `
    'V10 durable sixty-second cap is invalid.'
Require ($profile.candidateConfiguration.maximumDurableDeparturesPerHour -eq 2100) `
    'V10 durable hourly cap is invalid.'
Require ($profile.productionClassHashBinding.status -eq 'QUALIFIED_V10_COMPILED_CLASS_BINDING') `
    'V10 class-hash binding must be qualified before launcher review.'
$requiredClasses = @(
    'com.bettingproject.sofascorelocal.application.live.LiveAdmissionPolicyV10',
    'com.bettingproject.sofascorelocal.application.live.GroupedLiveAdmissionSimulationV10',
    'com.bettingproject.sofascorelocal.application.live.V10GroupedScheduleProfile',
    'com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData$DepartureProfile'
)
Require (@($profile.productionClassHashBinding.requiredClasses).Count -eq $requiredClasses.Count) `
    'V10 class-hash binding must name exactly the reviewed V10 classes.'
foreach ($className in $requiredClasses) {
    Require (@($profile.productionClassHashBinding.requiredClasses) -contains $className) `
        "V10 class-hash binding is missing $className."
    $hashProperty = $profile.productionClassSha256.PSObject.Properties[$className]
    Require ($null -ne $hashProperty -and $hashProperty.Value -match '^[0-9a-f]{64}$') `
        "V10 class-hash binding is invalid for $className."
}
Require (@($profile.productionClassSha256.PSObject.Properties).Count -eq $requiredClasses.Count) `
    'V10 class-hash binding must not include unrelated historical scheduler classes.'

$expectedEnvelopes = @{
    EVENT_DETAILS = @{ RequestMillis = 300; ProcessingMillis = 500 }
    EVENT_INCIDENTS = @{ RequestMillis = 300; ProcessingMillis = 400 }
    EVENT_STATISTICS = @{ RequestMillis = 350; ProcessingMillis = 400 }
    EVENT_LINEUPS = @{ RequestMillis = 300; ProcessingMillis = 450 }
}

foreach ($endpoint in $expectedEnvelopes.Keys) {
    $envelope = $profile.endpointEnvelopes.$endpoint
    Require ($null -ne $envelope) "V10 profile is missing the $endpoint envelope."
    Require ($envelope.requestMillis -eq $expectedEnvelopes[$endpoint].RequestMillis) `
        "V10 profile request envelope differs for $endpoint."
    Require ($envelope.processingMillis -eq $expectedEnvelopes[$endpoint].ProcessingMillis) `
        "V10 profile processing envelope differs for $endpoint."
}

# This utility is deliberately display-only. It neither writes the Eclipse launch
# configuration nor sets process/user/machine environment variables. The capacity
# and rate rows below are review metadata; only the nine envelope-related entries
# map to the local V10 configuration keys.
$settings = @(
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_QUALIFICATION_SHA256'; Value = $profileSha256 }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_J4_REQUEST_ENVELOPE'; Value = '300ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_J4_PROCESSING_ENVELOPE'; Value = '500ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_INCIDENTS_REQUEST_ENVELOPE'; Value = '300ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_INCIDENTS_PROCESSING_ENVELOPE'; Value = '400ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_STATISTICS_REQUEST_ENVELOPE'; Value = '350ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_STATISTICS_PROCESSING_ENVELOPE'; Value = '400ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_LINEUPS_REQUEST_ENVELOPE'; Value = '300ms' }
    [pscustomobject]@{ Name = 'SOFASCORE_LIVE_GROUPED_V10_LINEUPS_PROCESSING_ENVELOPE'; Value = '450ms' }
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

'V10_QUALIFICATION_PROVENANCE=INDEPENDENT_V10_COMPILED_CLASS_BINDING'
'V10_PROFILE_STATUS=QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE'
'V10_COMPILED_CLASS_HASH_BINDING=QUALIFIED'
'V10_MAXIMUM_SELECTED_MATCHES=8'
'V10_NOMINAL_ENDPOINT_CALLS_PER_MINUTE=32'
'V10_DURABLE_MAXIMUM_DEPARTURES_PER_60_SECONDS=35'
'V10_DURABLE_MAXIMUM_DEPARTURES_PER_HOUR=2100'
'V10_LAUNCHER_CONFIGURATION=REVIEW_REQUIRED'
'V10_LIVE_OPT_IN_CHANGED=NO'
'V10_PROVIDER_CALLS_EXECUTED=NO'
