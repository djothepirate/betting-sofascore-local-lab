[CmdletBinding()]
param([ValidateSet('Eclipse','PowerShell','Table')][string]$OutputFormat='Eclipse')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$wo060Root=Split-Path -Parent $PSScriptRoot
$wo060ProfilePath=Join-Path $wo060Root 'docs/validation/WO060-LIVE-V11-PROFILE-20260914.json'
$wo060Profile=Get-Content -LiteralPath $wo060ProfilePath -Raw -Encoding utf8 | ConvertFrom-Json
if($wo060Profile.policyVersion -cne 'live-v11' -or $wo060Profile.status -cne 'QUALIFIED_LOCAL_REPLAY_AND_NATIVE_LOOPBACK' -or
        $wo060Profile.qualifiedCapacity -ne 8 -or $wo060Profile.workerProtocolVersion -ne 10 -or $wo060Profile.flywayVersion -ne 57) {
    throw 'The versioned WO-060 V11 qualification is required.'
}
$wo060Hash=(Get-FileHash -LiteralPath $wo060ProfilePath -Algorithm SHA256).Hash.ToLowerInvariant()
$wo060Rows=@(
    [pscustomobject]@{Name='SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY';Value='8'}
    [pscustomobject]@{Name='SOFASCORE_LIVE_GROUPED_V11_QUALIFICATION_SHA256';Value=$wo060Hash}
)
$wo060Families=[ordered]@{EVENT_DETAILS='J4';EVENT_INCIDENTS='INCIDENTS';EVENT_STATISTICS='STATISTICS';EVENT_LINEUPS='LINEUPS'}
foreach($wo060Family in $wo060Families.Keys) {
    $wo060Envelope=$wo060Profile.endpointEnvelopes.$wo060Family
    if($wo060Envelope.requestMillis -le 0 -or $wo060Envelope.processingMillis -lt 0) {throw 'Invalid V11 envelope.'}
    $wo060Prefix='SOFASCORE_LIVE_GROUPED_V11_'+$wo060Families[$wo060Family]
    $wo060Rows += [pscustomobject]@{Name=$wo060Prefix+'_REQUEST_ENVELOPE';Value=([string]$wo060Envelope.requestMillis+'ms')}
    $wo060Rows += [pscustomobject]@{Name=$wo060Prefix+'_PROCESSING_ENVELOPE';Value=([string]$wo060Envelope.processingMillis+'ms')}
}
# Read-only display: no environment, Eclipse launcher, database or provider mutation.
switch($OutputFormat) {
    'Eclipse' {foreach($wo060Row in $wo060Rows) {'<mapEntry key="{0}" value="{1}"/>' -f $wo060Row.Name,$wo060Row.Value}}
    'PowerShell' {foreach($wo060Row in $wo060Rows) {'$env:{0} = ''{1}''' -f $wo060Row.Name,$wo060Row.Value}}
    'Table' {$wo060Rows | Format-Table -AutoSize}
}
'V11_QUALIFICATION_SCOPE=LOCAL_REPLAY_AND_NATIVE_CONTEXT_LOOPBACK'
'V11_REAL_PROVIDER_CALLS=0'
'V11_CONFIGURATION_CHANGED=NO'
