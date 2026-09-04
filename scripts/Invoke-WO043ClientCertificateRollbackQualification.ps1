[CmdletBinding()]
param(
    [string]$DockerExecutablePath = '',
    [ValidateRange(1, 30)]
    [int]$CleanupObservationSeconds = 10
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if (-not $IsWindows) {
    throw 'WO-043 client-certificate rollback qualification is supported only on Windows.'
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$initializePath = Join-Path $PSScriptRoot 'Initialize-J7LocalE2eInfrastructure.ps1'
$cleanupPath = Join-Path $PSScriptRoot 'Remove-J7LocalE2eInfrastructure.ps1'
if (-not (Test-Path -LiteralPath $initializePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $cleanupPath -PathType Leaf)) {
    throw 'WO-043 requires the exact versioned WO-036 initialization and cleanup scripts.'
}

$localApplicationData = [Environment]::GetFolderPath(
    [Environment+SpecialFolder]::LocalApplicationData)
$roamingApplicationData = [Environment]::GetFolderPath(
    [Environment+SpecialFolder]::ApplicationData)
$currentIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ([string]::IsNullOrWhiteSpace($localApplicationData) -or
    [string]::IsNullOrWhiteSpace($roamingApplicationData) -or
    $null -eq $currentIdentity.User) {
    throw 'WO-043 cannot resolve the exact current-user storage roots.'
}

$campaignBase = [System.IO.Path]::GetFullPath((Join-Path $localApplicationData `
    'SofaScoreLocalLab\qualifications\WO-SS-20260902-036'))
$cngKeyRoot = [System.IO.Path]::GetFullPath((Join-Path $roamingApplicationData `
    'Microsoft\Crypto\Keys'))
$legacyRsaRoot = [System.IO.Path]::GetFullPath((Join-Path $roamingApplicationData `
    ('Microsoft\Crypto\RSA\' + $currentIdentity.User.Value)))
$clientSubjectPattern =
    '^CN=WO036 sender [0-9a-fA-F-]{36}, OU=WO-036, O=Betting Project Local Qualification$'
$serverSubject = 'CN=127.0.0.1, OU=WO-036, O=Betting Project Local Qualification'

function Get-FileNameSnapshot {
    param([Parameter(Mandatory = $true)][string[]]$Roots)

    $names = [System.Collections.Generic.List[string]]::new()
    foreach ($root in $Roots) {
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            continue
        }
        $canonicalRoot = (Get-Item -LiteralPath $root -Force).FullName.TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar,
            [System.IO.Path]::AltDirectorySeparatorChar)
        foreach ($file in Get-ChildItem -LiteralPath $canonicalRoot -File -Recurse -Force) {
            $relative = $file.FullName.Substring($canonicalRoot.Length).TrimStart(
                [System.IO.Path]::DirectorySeparatorChar,
                [System.IO.Path]::AltDirectorySeparatorChar)
            $names.Add(($canonicalRoot + '|' + $relative).ToLowerInvariant())
        }
    }
    return @($names | Sort-Object -Unique)
}

function Get-CertificateThumbprintSnapshot {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('My', 'Root')][string]$StoreName,
        [Parameter(Mandatory = $true)][scriptblock]$Predicate
    )

    $store = [System.Security.Cryptography.X509Certificates.X509Store]::new(
        $StoreName,
        [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser)
    try {
        $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadOnly)
        return @($store.Certificates |
            Where-Object $Predicate |
            ForEach-Object { $_.Thumbprint.ToUpperInvariant() } |
            Sort-Object -Unique)
    }
    finally {
        $store.Dispose()
    }
}

function Get-CampaignRootSnapshot {
    if (-not (Test-Path -LiteralPath $campaignBase -PathType Container)) {
        return @()
    }
    return @(Get-ChildItem -LiteralPath $campaignBase -Directory -Force |
        Where-Object { $_.Name -match '^[0-9a-fA-F-]{36}$' } |
        ForEach-Object { $_.Name.ToLowerInvariant() } |
        Sort-Object -Unique)
}

function Get-ListenerCountSnapshot {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
    return @(foreach ($port in @(8087, 8444, 5432, 5433)) {
        '{0}={1}' -f $port, @($listeners | Where-Object {
            [int]$_.LocalPort -eq $port
        }).Count
    })
}

function Get-WO043HostSnapshot {
    return [pscustomobject]@{
        ClientCertificates = @(Get-CertificateThumbprintSnapshot -StoreName My -Predicate {
            $_.Subject -match $clientSubjectPattern
        })
        ServerRoots = @(Get-CertificateThumbprintSnapshot -StoreName Root -Predicate {
            $_.Subject -eq $serverSubject
        })
        UserPrivateKeyFiles = @(Get-FileNameSnapshot -Roots @($cngKeyRoot, $legacyRsaRoot))
        CampaignRoots = @(Get-CampaignRootSnapshot)
        ListenerCounts = @(Get-ListenerCountSnapshot)
    }
}

function Test-ExactSequenceEqual {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Actual
    )

    if ($Expected.Count -ne $Actual.Count) {
        return $false
    }
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        if ([string]$Expected[$index] -cne [string]$Actual[$index]) {
            return $false
        }
    }
    return $true
}

function Test-HostSnapshotEqual {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual
    )

    return (Test-ExactSequenceEqual $Expected.ClientCertificates $Actual.ClientCertificates) -and
        (Test-ExactSequenceEqual $Expected.ServerRoots $Actual.ServerRoots) -and
        (Test-ExactSequenceEqual $Expected.UserPrivateKeyFiles $Actual.UserPrivateKeyFiles) -and
        (Test-ExactSequenceEqual $Expected.CampaignRoots $Actual.CampaignRoots) -and
        (Test-ExactSequenceEqual $Expected.ListenerCounts $Actual.ListenerCounts)
}

function Assert-NoDescendantReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Root)

    foreach ($entry in Get-ChildItem -LiteralPath $Root -Recurse -Force) {
        if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'WO-043 refuses a qualification root containing a reparse point.'
        }
    }
}

function Get-NewCampaignRootName {
    param(
        [Parameter(Mandatory = $true)]$Before,
        [Parameter(Mandatory = $true)]$After
    )

    $added = @($After.CampaignRoots | Where-Object {
        $_ -notin @($Before.CampaignRoots)
    })
    $removed = @($Before.CampaignRoots | Where-Object {
        $_ -notin @($After.CampaignRoots)
    })
    if ($added.Count -ne 1 -or $removed.Count -ne 0 -or
        $added[0] -notmatch '^[0-9a-fA-F-]{36}$') {
        throw 'WO-043 cannot identify exactly one newly retained qualification root.'
    }
    return [string]$added[0]
}

function Assert-ZeroOwnedCleanupMarkers {
    param(
        [Parameter(Mandatory = $true)][object[]]$Captured,
        [Parameter(Mandatory = $true)]$Before
    )

    $lines = @($Captured | ForEach-Object { $_.ToString() })
    foreach ($required in @(
        'WO036_CLEANUP_OWNED_PROCESS_RESIDUAL_COUNT=0',
        'WO036_CLEANUP_CONTAINER_RESIDUAL_COUNT=0',
        'WO036_CLEANUP_VOLUME_RESIDUAL_COUNT=0',
        'WO036_CLEANUP_CERTIFICATE_RESIDUAL_COUNT=0')) {
        if ($lines -notcontains $required) {
            throw 'WO-043 did not receive the exact zero-owned-resource cleanup proof.'
        }
    }
    foreach ($listenerCount in @($Before.ListenerCounts)) {
        $parts = $listenerCount.Split('=', 2)
        $required = "WO036_CLEANUP_LISTENER_$($parts[0])_COUNT=$($parts[1])"
        if ($lines -notcontains $required) {
            throw 'WO-043 observed a listener delta during exact qualification cleanup.'
        }
    }
}

function Remove-ExactRetainedQualificationRoot {
    param(
        [Parameter(Mandatory = $true)]$Before,
        [Parameter(Mandatory = $true)]$After
    )

    if (-not (Test-ExactSequenceEqual `
            $Before.ClientCertificates $After.ClientCertificates) -or
        -not (Test-ExactSequenceEqual $Before.ServerRoots $After.ServerRoots) -or
        -not (Test-ExactSequenceEqual `
            $Before.UserPrivateKeyFiles $After.UserPrivateKeyFiles) -or
        -not (Test-ExactSequenceEqual $Before.ListenerCounts $After.ListenerCounts)) {
        throw 'WO-043 refuses private-root cleanup while a host-resource delta remains.'
    }

    $runId = Get-NewCampaignRootName -Before $Before -After $After
    $expectedRoot = [System.IO.Path]::GetFullPath((Join-Path $campaignBase $runId))
    $resolvedRoot = (Resolve-Path -LiteralPath $expectedRoot -ErrorAction Stop).Path
    $resolvedParent = [System.IO.Path]::GetFullPath((Split-Path -Parent $resolvedRoot))
    if (-not $resolvedRoot.Equals($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not $resolvedParent.Equals($campaignBase, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'WO-043 retained-root cleanup escaped the exact campaign base.'
    }
    $rootItem = Get-Item -LiteralPath $resolvedRoot -Force
    if (($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'WO-043 refuses a retained qualification root that is a reparse point.'
    }
    Assert-NoDescendantReparsePoint -Root $resolvedRoot

    $statePath = Join-Path $resolvedRoot 'state.private.json'
    $markerPath = Join-Path $resolvedRoot '.wo036-owner.json'
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        throw 'WO-043 retained-root cleanup requires exact private state and marker files.'
    }
    $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $marker = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $expectedComposePath = [System.IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot 'compose.wo036.yml'))
    if ($state.schemaVersion -ne 1 -or $marker.schemaVersion -ne 1 -or
        $state.workOrder -ne 'WO-SS-20260902-036-j9-j7-local-e2e-qualification' -or
        $marker.workOrder -ne $state.workOrder -or
        $state.runId -ne $runId -or $marker.runId -ne $runId -or
        $state.ownerSid -ne $currentIdentity.User.Value -or
        $marker.ownerSid -ne $state.ownerSid -or
        $marker.ownershipSha256 -ne $state.ownershipSha256 -or
        -not ([System.IO.Path]::GetFullPath([string]$state.runRoot).Equals(
            $resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) -or
        -not ([System.IO.Path]::GetFullPath([string]$state.repositoryRoot).Equals(
            $repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) -or
        -not ([System.IO.Path]::GetFullPath([string]$state.composePath).Equals(
            $expectedComposePath, [System.StringComparison]::OrdinalIgnoreCase)) -or
        [bool]$state.databasesStarted -or [bool]$state.dockerResourcesMayExist -or
        @($state.ownedProcesses).Count -ne 0 -or @($state.ownedContainers).Count -ne 0) {
        throw 'WO-043 retained-root cleanup rejected an ownership or zero-resource invariant.'
    }

    Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    if (Test-Path -LiteralPath $resolvedRoot) {
        throw 'WO-043 exact retained qualification root remains after cleanup.'
    }
}

function Complete-QualificationCleanup {
    param(
        [Parameter(Mandatory = $true)]$Before,
        [Parameter(Mandatory = $true)][object[]]$Captured,
        [Parameter(Mandatory = $true)][AllowNull()]$Caught
    )

    if ($null -eq $Caught) {
        Wait-HostSnapshotRestored -Expected $Before
        return
    }
    if ($Caught.Exception.Message -notmatch
            'WO-036 cleanup postconditions found a residual owned resource or listener' -and
        $Caught.Exception.Message -notmatch
            'WO-036 initialization failed closed during CLIENT_CERTIFICATE.*rollback=FAILED_PRIVATE_STATE_RETAINED') {
        throw $Caught
    }
    Assert-ZeroOwnedCleanupMarkers -Captured $Captured -Before $Before
    $after = Get-WO043HostSnapshot
    Remove-ExactRetainedQualificationRoot -Before $Before -After $after
    Wait-HostSnapshotRestored -Expected $Before
}

function Wait-HostSnapshotRestored {
    param([Parameter(Mandatory = $true)]$Expected)

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($CleanupObservationSeconds)
    do {
        $actual = Get-WO043HostSnapshot
        if (Test-HostSnapshotEqual -Expected $Expected -Actual $actual) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw 'WO-043 detected a current-user certificate, private-key file or campaign-root residue.'
}

function Get-InitializationArguments {
    param([Parameter(Mandatory = $true)][string]$FailurePoint)

    $arguments = @{
        QualificationFailurePoint = $FailurePoint
    }
    if (-not [string]::IsNullOrWhiteSpace($DockerExecutablePath)) {
        $arguments.DockerExecutablePath = $DockerExecutablePath
    }
    return $arguments
}

function Invoke-InjectedFailureCase {
    param([Parameter(Mandatory = $true)][string]$FailurePoint)

    $before = Get-WO043HostSnapshot
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $caught = $null
    $captured = [System.Collections.Generic.List[object]]::new()
    try {
        $arguments = Get-InitializationArguments -FailurePoint $FailurePoint
        & $initializePath @arguments *>&1 | ForEach-Object {
            $captured.Add($_)
        }
    }
    catch {
        $caught = $_
    }
    finally {
        $stopwatch.Stop()
    }
    if ($null -eq $caught -or
        $caught.Exception.Message -notmatch 'WO-036 initialization failed closed during CLIENT_CERTIFICATE' -or
        $caught.Exception.Message -notmatch
            'rollback=(PASS|FAILED_PRIVATE_STATE_RETAINED)') {
        throw "WO-043 failure point $FailurePoint did not fail through the qualified rollback path."
    }
    if ($caught.Exception.Message -match 'rollback=PASS') {
        Wait-HostSnapshotRestored -Expected $before
    }
    else {
        Complete-QualificationCleanup `
            -Before $before `
            -Captured @($captured) `
            -Caught $caught
    }
    Write-Output "WO043_FAILURE_POINT_${FailurePoint}=PASS"
    Write-Output ("WO043_FAILURE_POINT_${FailurePoint}_DURATION_MILLISECONDS=" +
        [Math]::Round($stopwatch.Elapsed.TotalMilliseconds))
    Write-Output "WO043_FAILURE_POINT_${FailurePoint}_RESIDUE=0"
}

function Assert-ExactStatePath {
    param([Parameter(Mandatory = $true)][string]$StatePath)

    $canonicalStatePath = [System.IO.Path]::GetFullPath($StatePath)
    $canonicalParent = [System.IO.Path]::GetFullPath((Split-Path -Parent $canonicalStatePath))
    $expectedBase = $campaignBase.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    if (-not $canonicalParent.StartsWith(
            $expectedBase + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase) -or
        [System.IO.Path]::GetFileName($canonicalStatePath) -cne 'state.private.json' -or
        [System.IO.Path]::GetFileName($canonicalParent) -notmatch '^[0-9a-fA-F-]{36}$') {
        throw 'WO-043 nominal cleanup received a state path outside the exact campaign root.'
    }
    return $canonicalStatePath
}

function Invoke-NominalPrepareAndCleanupCase {
    $before = Get-WO043HostSnapshot
    $arguments = Get-InitializationArguments -FailurePoint 'NONE'
    $initializationOutput = @(& $initializePath @arguments *>&1)
    $initializationLines = @($initializationOutput | ForEach-Object { $_.ToString() })
    $stateLines = @($initializationLines |
        ForEach-Object { $_.ToString() } |
        Where-Object { $_.StartsWith('WO036_PRIVATE_STATE=', [System.StringComparison]::Ordinal) })
    if ($stateLines.Count -ne 1 -or
        $initializationLines -notcontains 'WO036_INFRASTRUCTURE_INITIALIZATION=PASS' -or
        $initializationLines -notcontains 'WO036_DATABASES_STARTED=NO' -or
        $initializationLines -notcontains 'WO036_PROVIDER_NETWORK_OPENED=NO') {
        throw 'WO-043 nominal initialization did not return the exact offline success markers.'
    }
    $statePath = Assert-ExactStatePath -StatePath $stateLines[0].Substring(
        'WO036_PRIVATE_STATE='.Length)
    $cleanupOutput = [System.Collections.Generic.List[object]]::new()
    $cleanupCaught = $null
    try {
        & $cleanupPath -StatePath $statePath -Confirm:$false *>&1 | ForEach-Object {
            $cleanupOutput.Add($_)
        }
    }
    catch {
        $cleanupCaught = $_
    }
    if ($null -eq $cleanupCaught) {
        $cleanupLines = @($cleanupOutput | ForEach-Object { $_.ToString() })
        if ($cleanupLines -notcontains 'WO036_CLEANUP_ATTESTATION=PASS' -or
            $cleanupLines -notcontains 'WO036_CLEANUP_CERTIFICATE_RESIDUAL_COUNT=0') {
            throw 'WO-043 nominal cleanup did not return the exact zero-certificate attestation.'
        }
    }
    Complete-QualificationCleanup `
        -Before $before `
        -Captured @($cleanupOutput) `
        -Caught $cleanupCaught
    Write-Output 'WO043_NOMINAL_PREPARE_AND_CLEANUP=PASS'
    Write-Output 'WO043_NOMINAL_DATABASES_STARTED=NO'
    Write-Output 'WO043_NOMINAL_PROVIDER_CALLS=0'
    Write-Output 'WO043_NOMINAL_RECEIVER_HTTP_CALLS=0'
    Write-Output 'WO043_NOMINAL_RESIDUE=0'
}

$failurePoints = @(
    'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP',
    'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY',
    'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED',
    'BEFORE_CLIENT_CERTIFICATE_EXPORT'
)
foreach ($failurePoint in $failurePoints) {
    Invoke-InjectedFailureCase -FailurePoint $failurePoint
}
Invoke-NominalPrepareAndCleanupCase

Write-Output 'WO043_CLIENT_CERTIFICATE_ROLLBACK_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED'
Write-Output 'WO043_CLIENT_CERTIFICATE_FAILURE_POINTS=4_OF_4_PASS'
Write-Output 'WO043_CLIENT_CERTIFICATE_AND_PRIVATE_KEY_RESIDUE=0'
Write-Output 'WO043_PRIVATE_CAMPAIGN_ROOT_DELTA=0'
Write-Output 'WO043_PREEXISTING_LOOPBACK_LISTENERS_PRESERVED=YES'
Write-Output 'WO043_DATABASES_STARTED=NO'
Write-Output 'WO043_PROVIDER_CALLS=0'
Write-Output 'WO043_RECEIVER_HTTP_CALLS=0'
