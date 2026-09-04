[CmdletBinding()]
param(
    [ValidateRange(5, 30)]
    [int]$CleanupObservationSeconds = 15,

    [switch]$ModuleCommandScopeQualificationOnly,

    [string]$ModuleCommandScopeQualificationRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'WO-048 host qualification is supported only on Windows.'
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$initializePath = Join-Path $PSScriptRoot 'Initialize-WO046LocalMtlsIdentity.ps1'
$cleanupPath = Join-Path $PSScriptRoot 'Remove-WO046LocalMtlsIdentity.ps1'
$modulePath = Join-Path $PSScriptRoot 'wo048\WO048-PkiTools.psm1'
foreach ($path in @($initializePath, $cleanupPath, $modulePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'WO-048 host qualification requires the exact versioned PKI-only tooling.'
    }
}

function Import-WO048PinnedGlobalModule {
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedPath,
        [Parameter(Mandatory = $true)][string]$ExpectedName,
        [Parameter(Mandatory = $true)][string[]]$RequiredCommands
    )

    $canonicalExpectedPath = [System.IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $ExpectedPath -ErrorAction Stop).Path)
    $existing = @(Get-Module -Name $ExpectedName -All)
    if ($existing.Count -gt 1 -or @($existing | Where-Object {
            [string]::IsNullOrWhiteSpace($_.Path) -or
            [System.IO.Path]::GetFullPath($_.Path) -cne $canonicalExpectedPath
        }).Count -ne 0) {
        throw 'WO-048 refuses a loaded module with an ambiguous or unexpected origin.'
    }
    $ownedByThisInvocation = $existing.Count -eq 0
    Import-Module -Name $canonicalExpectedPath -Global -ErrorAction Stop
    $loaded = @(Get-Module -Name $ExpectedName -All)
    if ($loaded.Count -ne 1 -or
        [System.IO.Path]::GetFullPath($loaded[0].Path) -cne
            $canonicalExpectedPath) {
        throw 'WO-048 could not establish one exact globally visible module.'
    }
    foreach ($requiredCommand in $RequiredCommands) {
        $resolved = @(Get-Command -Name ($ExpectedName + '\' + $requiredCommand) `
            -All -ErrorAction Stop)
        if ($resolved.Count -ne 1 -or $null -eq $resolved[0].Module -or
            [System.IO.Path]::GetFullPath($resolved[0].Module.Path) -cne
                $canonicalExpectedPath) {
            throw 'WO-048 could not resolve an exact pinned module command.'
        }
    }
    return $ownedByThisInvocation
}

$script:wo048PkiModuleOwnedByThisInvocation =
    Import-WO048PinnedGlobalModule `
        -ExpectedPath $modulePath `
        -ExpectedName 'WO048-PkiTools' `
        -RequiredCommands @(
            'Assert-WO048LocalFixedPathPrefix',
            'Get-WO048CurrentOwnerSid')

$ownerSid = Get-WO048CurrentOwnerSid
if ($ModuleCommandScopeQualificationOnly) {
    if ([string]::IsNullOrWhiteSpace($ModuleCommandScopeQualificationRoot)) {
        throw 'WO-048 module-scope qualification requires one explicit local root.'
    }
    $scopeOnlyRoot = Assert-WO048LocalFixedPathPrefix `
        -Path $ModuleCommandScopeQualificationRoot `
        -RequireExistingType Directory
    $canonicalLocalApplicationData = $scopeOnlyRoot
    $canonicalRoamingApplicationData = $scopeOnlyRoot
    $campaignBase = $scopeOnlyRoot
    $cngKeyRoot = $scopeOnlyRoot
    $legacyRsaRoot = $scopeOnlyRoot
}
else {
    $localApplicationData = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::LocalApplicationData)
    $roamingApplicationData = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::ApplicationData)
    if ([string]::IsNullOrWhiteSpace($localApplicationData) -or
        [string]::IsNullOrWhiteSpace($roamingApplicationData)) {
        throw 'WO-048 host qualification cannot resolve exact current-user roots.'
    }
    $canonicalLocalApplicationData = Assert-WO048LocalFixedPathPrefix `
        -Path $localApplicationData -RequireExistingType Directory
    $canonicalRoamingApplicationData = Assert-WO048LocalFixedPathPrefix `
        -Path $roamingApplicationData -RequireExistingType Directory
    $campaignBase = Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $canonicalLocalApplicationData `
            'SofaScoreLocalLab\qualifications\WO-SS-20260904-046')
    $cngKeyRoot = Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $canonicalRoamingApplicationData 'Microsoft\Crypto\Keys')
    $legacyRsaRoot = Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $canonicalRoamingApplicationData `
            ('Microsoft\Crypto\RSA\' + $ownerSid.Value))
}
$clientSubjectPattern =
    '^CN=WO046 sender [0-9a-fA-F-]{36}, OU=WO-046, O=Betting Project Local Qualification$'
$serverSubject = 'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'

function Get-WO048FileNameSnapshot {
    param([Parameter(Mandatory = $true)][string[]]$Roots)

    $names = [System.Collections.Generic.List[string]]::new()
    foreach ($root in $Roots) {
        $safeRoot = Assert-WO048LocalFixedPathPrefix -Path $root
        if (-not (Test-Path -LiteralPath $safeRoot -PathType Container)) {
            continue
        }
        Assert-WO048NoDescendantReparsePoint -Root $safeRoot
        $canonicalRoot = (Get-Item -LiteralPath $safeRoot -Force).FullName.TrimEnd(
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

function Get-WO048CertificateSnapshot {
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

function Get-WO048RunRootSnapshot {
    $safeCampaignBase = Assert-WO048LocalFixedPathPrefix -Path $campaignBase
    if (-not (Test-Path -LiteralPath $safeCampaignBase -PathType Container)) {
        return @()
    }
    $guidLikeRoots = @(Get-ChildItem -LiteralPath $safeCampaignBase -Directory -Force |
        Where-Object { $_.Name -match '^[0-9a-fA-F-]{36}$' })
    if (@($guidLikeRoots | Where-Object {
            $_.Name -cnotmatch
                '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        }).Count -ne 0) {
        throw 'WO-048 refuses a non-canonical GUID-like private run root.'
    }
    return @($guidLikeRoots | ForEach-Object { $_.Name } | Sort-Object -Unique)
}

function Get-WO048ListenerSnapshot {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
    return @(foreach ($port in @(8087, 8444, 5432, 5433)) {
        '{0}={1}' -f $port, @($listeners | Where-Object {
            [int]$_.LocalPort -eq $port
        }).Count
    })
}

function Get-WO048HostSnapshot {
    return [pscustomobject]@{
        ClientCertificates = @(Get-WO048CertificateSnapshot -StoreName My -Predicate {
            $_.Subject -match $clientSubjectPattern
        })
        ServerTrustCertificates = @(Get-WO048CertificateSnapshot -StoreName Root -Predicate {
            $_.Subject -ceq $serverSubject
        })
        UserPrivateKeyFiles = @(Get-WO048FileNameSnapshot -Roots @($cngKeyRoot, $legacyRsaRoot))
        RunRoots = @(Get-WO048RunRootSnapshot)
        ListenerCounts = @(Get-WO048ListenerSnapshot)
    }
}

function Test-WO048ExactSequenceEqual {
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

function Test-WO048HostSnapshotEqual {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual
    )

    return (Test-WO048ExactSequenceEqual `
            $Expected.ClientCertificates $Actual.ClientCertificates) -and
        (Test-WO048ExactSequenceEqual `
            $Expected.ServerTrustCertificates $Actual.ServerTrustCertificates) -and
        (Test-WO048ExactSequenceEqual `
            $Expected.UserPrivateKeyFiles $Actual.UserPrivateKeyFiles) -and
        (Test-WO048ExactSequenceEqual $Expected.RunRoots $Actual.RunRoots) -and
        (Test-WO048ExactSequenceEqual $Expected.ListenerCounts $Actual.ListenerCounts)
}

function Wait-WO048HostSnapshotRestored {
    param([Parameter(Mandatory = $true)]$Expected)

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($CleanupObservationSeconds)
    do {
        $actual = Get-WO048HostSnapshot
        if (Test-WO048HostSnapshotEqual -Expected $Expected -Actual $actual) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw 'WO-048 detected a certificate, private-key, private-root or listener delta after rollback.'
}

function Get-WO048ExactAddedValue {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Before,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$After,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $added = @($After | Where-Object { $_ -notin $Before })
    $removed = @($Before | Where-Object { $_ -notin $After })
    if ($added.Count -ne 1 -or $removed.Count -ne 0) {
        throw "WO-048 could not identify one exact new $Description."
    }
    return [string]$added[0]
}

function Assert-WO048ExactRollbackAuthority {
    param([Parameter(Mandatory = $true)][string]$RunId)

    [void](Assert-WO048CanonicalLowercaseRunId -RunId $RunId)
    $runGuid = [guid]::ParseExact($RunId, 'D')
    $runRoot = Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $campaignBase $RunId) -RequireExistingType Directory
    Assert-WO048ImmediateGuidChild -Parent $campaignBase -Child $runRoot -RunId $runGuid
    Assert-WO048NoDescendantReparsePoint -Root $runRoot
    Assert-WO048PrivateAcl -Path $runRoot -OwnerSid $ownerSid.Value -RequireProtected
    $statePath = Join-Path $runRoot 'identity.private.json'
    $markerPath = Join-Path $runRoot '.wo048-owner.json'
    foreach ($privatePath in @($statePath, $markerPath)) {
        if (-not (Test-Path -LiteralPath $privatePath -PathType Leaf)) {
            throw 'WO-048 cannot prove complete rollback authority for the derived run.'
        }
        Assert-WO048NotReparsePoint -Path $privatePath
        Assert-WO048PrivateAcl `
            -Path $privatePath -OwnerSid $ownerSid.Value -RequireProtected
    }
    $state = Read-WO048StrictUtf8JsonObject -Path $statePath
    $marker = Read-WO048StrictUtf8JsonObject -Path $markerPath
    Assert-WO048PrivateStateShape `
        -State $state `
        -ExpectedWorkOrder 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign' `
        -ExpectedProvisioningWorkOrder `
            'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning' `
        -ExpectedRunId $RunId `
        -ExpectedOwnerSid $ownerSid.Value
    $binding = Get-WO048StateIdentityBinding `
        -WorkOrder $state.workOrder `
        -ProvisioningWorkOrder $state.provisioningWorkOrder `
        -RunId $state.runId `
        -OwnerSid $state.ownerSid `
        -CreatedAtUtc $state.createdAtUtc `
        -RunRoot $state.runRoot `
        -MarkerPath $state.markerPath
    if ($state.stateIdentityBindingSha256 -cne $binding -or
        $marker.stateIdentityBindingSha256 -cne $binding -or
        $marker.runId -cne $RunId -or
        $marker.ownerSid -cne $ownerSid.Value) {
        throw 'WO-048 refuses rollback without an exact state/marker identity binding.'
    }
}

function Invoke-WO048InjectedFailureCase {
    param([Parameter(Mandatory = $true)][string]$FailurePoint)

    $before = Get-WO048HostSnapshot
    $captured = [System.Collections.Generic.List[object]]::new()
    $caught = $null
    try {
        & $initializePath -QualificationFailurePoint $FailurePoint *>&1 |
            ForEach-Object { $captured.Add($_) }
    }
    catch {
        $caught = $_
    }
    $expectedInjectedPhase = 'INJECTED_' + $FailurePoint
    $expectedInjectedMessage =
        "WO-048 PKI-only provisioning failed closed during $expectedInjectedPhase (RuntimeException); rollback=PASS."
    if ($null -eq $caught -or
        $caught.Exception.Message -cne $expectedInjectedMessage) {
        throw 'A WO-048 injected failure did not reach its exact requested point with fail-closed rollback.'
    }
    $capturedText = (@($captured | ForEach-Object { $_.ToString() }) -join "`n")
    if ($capturedText -match '(?i)(thumbprint|certificate.*sha256|private\\|\.p12|\.cer)') {
        throw 'A WO-048 injected failure disclosed private certificate metadata.'
    }
    Wait-WO048HostSnapshotRestored -Expected $before
    Write-Output "WO048_FAILURE_POINT_${FailurePoint}=PASS"
    Write-Output "WO048_FAILURE_POINT_${FailurePoint}_RESIDUE=0"
}

function Get-WO048OutputValue {
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Pattern
    )

    $prefix = "$Key="
    $matches = @($Lines | Where-Object {
        $_.StartsWith($prefix, [System.StringComparison]::Ordinal)
    })
    if ($matches.Count -ne 1) {
        throw 'WO-048 nominal output does not contain one exact required marker.'
    }
    $value = $matches[0].Substring($prefix.Length)
    if ($value -notmatch $Pattern) {
        throw 'WO-048 nominal output contains an invalid marker value.'
    }
    return $value
}

function Get-WO048ExactStoreCertificate {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('My', 'Root')][string]$StoreName,
        [Parameter(Mandatory = $true)]$Record
    )

    $certificate = Get-Item `
        -LiteralPath (Join-Path "Cert:\CurrentUser\$StoreName" $Record.thumbprint) `
        -ErrorAction Stop
    if ((Get-WO048CertificateSha256 -Certificate $certificate) -ne $Record.sha256 -or
        $certificate.Subject -cne $Record.subject) {
        $certificate.Dispose()
        throw 'A retained WO-048 certificate failed exact identity verification.'
    }
    return $certificate
}

function Assert-WO048NominalPrivateRecord {
    param(
        [Parameter(Mandatory = $true)][string]$RunId,
        [Parameter(Mandatory = $true)][string]$ExpectedRecordSha256
    )

    $runGuid = [guid]::ParseExact($RunId, 'D')
    $runRoot = [System.IO.Path]::GetFullPath((Join-Path $campaignBase $runGuid.ToString('D')))
    Assert-WO048ImmediateGuidChild -Parent $campaignBase -Child $runRoot -RunId $runGuid
    Assert-WO048NoDescendantReparsePoint -Root $runRoot
    Assert-WO048PrivateAcl -Path $runRoot -OwnerSid $ownerSid.Value -RequireProtected
    $statePath = Join-Path $runRoot 'identity.private.json'
    if ((Get-WO048FileSha256 -Path $statePath) -cne $ExpectedRecordSha256) {
        throw 'The retained WO-048 private identity record commitment does not match.'
    }
    $state = Read-WO048StrictUtf8JsonObject -Path $statePath
    Assert-WO048PrivateStateShape `
        -State $state `
        -ExpectedWorkOrder 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign' `
        -ExpectedProvisioningWorkOrder `
            'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning' `
        -ExpectedRunId $RunId `
        -ExpectedOwnerSid $ownerSid.Value
    if ($state.schemaVersion -ne 2 -or
        $state.format -cne 'WO046_LOCAL_MTLS_IDENTITY_V2' -or
        $state.runId -cne $RunId -or
        $state.ownerSid -cne $ownerSid.Value -or
        $state.provisioningStatus -cne 'PASS' -or
        $state.cleanupStatus -cne 'NOT_STARTED' -or
        $state.javaRuntimeQualification -cne 'PASS_JAVA_25_SANITIZED' -or
        $state.javaStoreQualification -cne 'PASS_SUNMSCAPI_NO_HANDSHAKE' -or
        $state.receiverOrigin -cne 'https://127.0.0.1:8444' -or
        $state.privateValuesDisplayed -or $state.databasesStarted -or
        $state.applicationsStarted -or $state.tlsHandshakeAttempted -or
        $state.socketsOpened -or $state.providerNetworkTouched -or
        $state.remoteNetworkTouched -or
        @($state.ownedCertificates).Count -ne 2) {
        throw 'The retained WO-048 private identity record is not exact and offline.'
    }
    $markerPath = Join-Path $runRoot '.wo048-owner.json'
    $marker = Read-WO048StrictUtf8JsonObject -Path $markerPath
    $binding = Get-WO048StateIdentityBinding `
        -WorkOrder $state.workOrder `
        -ProvisioningWorkOrder $state.provisioningWorkOrder `
        -RunId $state.runId `
        -OwnerSid $state.ownerSid `
        -CreatedAtUtc $state.createdAtUtc `
        -RunRoot $state.runRoot `
        -MarkerPath $state.markerPath
    if ($marker.schemaVersion -ne 2 -or
        $marker.stateIdentityBindingSha256 -cne $binding -or
        $state.stateIdentityBindingSha256 -cne $binding -or
        $marker.provisioningStateStatus -cne 'PASS_BOUND' -or
        $marker.passStateSha256 -cne $ExpectedRecordSha256 -or
        $marker.finalizationStatus -cne 'NOT_READY') {
        throw 'The retained WO-048 marker does not bind its immutable state identity.'
    }

    $clientRecord = @($state.ownedCertificates | Where-Object {
        $_.role -ceq 'sender-client'
    })
    $serverRecord = @($state.ownedCertificates | Where-Object {
        $_.role -ceq 'receiver-server-direct-trust'
    })
    if ($clientRecord.Count -ne 1 -or $serverRecord.Count -ne 1) {
        throw 'The retained WO-048 identity does not contain both exact certificate roles.'
    }
    if ($clientRecord[0].ownershipStatus -cne 'OWNED' -or
        $serverRecord[0].ownershipStatus -cne 'OWNED') {
        throw 'The retained WO-048 PASS identity does not own both exact certificate roles.'
    }
    $observedAt = [DateTimeOffset]::UtcNow
    $expectedClientSubject =
        "CN=WO046 sender $RunId, OU=WO-046, O=Betting Project Local Qualification"

    $clientCertificate = Get-WO048ExactStoreCertificate -StoreName My -Record $clientRecord[0]
    try {
        Assert-WO048ClientCertificateProfile `
            -Certificate $clientCertificate `
            -ExpectedSubject $expectedClientSubject `
            -ObservedAtUtc $observedAt `
            -RequirePrivateKey
        Assert-WO048ClientPrivateKeyProfile -Certificate $clientCertificate
        $actualCng = Get-WO048ClientCngKeyIdentity -Certificate $clientCertificate
        foreach ($property in @(
                'providerName', 'keyName', 'uniqueName', 'keyFilePath', 'keyFileLength',
                'keyFileCreationTimeUtc', 'algorithmGroup', 'keySize', 'exportPolicy',
                'keyUsage', 'isEphemeral')) {
            if ([string]$actualCng.$property -cne
                [string]$clientRecord[0].cngKey.$property) {
                throw 'The retained WO-048 CNG identity differs from its recovery record.'
            }
        }
    }
    finally {
        $clientCertificate.Dispose()
    }
    $serverTrustCertificate = Get-WO048ExactStoreCertificate `
        -StoreName Root `
        -Record $serverRecord[0]
    try {
        Assert-WO048ServerCertificateProfile `
            -Certificate $serverTrustCertificate `
            -ObservedAtUtc $observedAt
    }
    finally {
        $serverTrustCertificate.Dispose()
    }

    $clientPublic = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        [string]$state.pki.clientPublicCertificatePath)
    $serverPublic = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        [string]$state.pki.serverPublicCertificatePath)
    try {
        Assert-WO048ClientCertificateProfile `
            -Certificate $clientPublic `
            -ExpectedSubject $expectedClientSubject `
            -ObservedAtUtc $observedAt
        Assert-WO048ServerCertificateProfile `
            -Certificate $serverPublic `
            -ObservedAtUtc $observedAt
        if ((Get-WO048CertificateSha256 -Certificate $clientPublic) -cne
                $clientRecord[0].sha256 -or
            (Get-WO048CertificateSha256 -Certificate $serverPublic) -cne
                $serverRecord[0].sha256) {
            throw 'A WO-048 public certificate differs from its exact selected store identity.'
        }
    }
    finally {
        $clientPublic.Dispose()
        $serverPublic.Dispose()
    }

    $ephemeral = [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet
    $receiverKeyStore = [System.Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
    $receiverTrustStore = [System.Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
    try {
        $receiverKeyStore.Import(
            [string]$state.pki.serverKeyStorePath,
            [string]$state.pki.serverKeyStorePassword,
            $ephemeral)
        $receiverTrustStore.Import(
            [string]$state.pki.receiverTrustStorePath,
            [string]$state.pki.receiverTrustStorePassword,
            $ephemeral)
        Assert-WO048ExactCertificateCollection `
            -Certificates $receiverKeyStore `
            -ExpectedSha256 $serverRecord[0].sha256 `
            -ExpectedPrivateKey $true
        Assert-WO048ExactCertificateCollection `
            -Certificates $receiverTrustStore `
            -ExpectedSha256 $clientRecord[0].sha256 `
            -ExpectedPrivateKey $false
    }
    finally {
        foreach ($certificate in @($receiverKeyStore)) {
            $certificate.Dispose()
        }
        foreach ($certificate in @($receiverTrustStore)) {
            $certificate.Dispose()
        }
    }
}

if ($ModuleCommandScopeQualificationOnly) {
    try {
        if ([string]::IsNullOrWhiteSpace($ModuleCommandScopeQualificationRoot)) {
            throw 'WO-048 module-scope qualification requires one explicit local root.'
        }
        $scopeRoot = Assert-WO048LocalFixedPathPrefix `
            -Path $ModuleCommandScopeQualificationRoot `
            -RequireExistingType Directory
        $scopeSnapshot = @(Get-WO048FileNameSnapshot -Roots @($scopeRoot))
        if ($scopeSnapshot.Count -ne 0) {
            throw 'WO-048 module-scope qualification root must be empty.'
        }
        Write-Output 'WO048_NESTED_MODULE_COMMAND_RESOLUTION=PASS'
    }
    finally {
        if ($script:wo048PkiModuleOwnedByThisInvocation) {
            Remove-Module -Name 'WO048-PkiTools' -Force `
                -ErrorAction SilentlyContinue
            $script:wo048PkiModuleOwnedByThisInvocation = $false
        }
    }
    return
}

$failurePoints = @(
    'AFTER_PRIVATE_STATE',
    'AFTER_SERVER_KEYSTORE',
    'AFTER_SERVER_ROOT_IMPORT',
    'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP',
    'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY',
    'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED',
    'AFTER_RECEIVER_TRUSTSTORE')
foreach ($failurePoint in $failurePoints) {
    Invoke-WO048InjectedFailureCase -FailurePoint $failurePoint
}

$beforeNominal = Get-WO048HostSnapshot
$runId = $null
$recordSha256 = $null
try {
    $guardedResult = Invoke-WO048GuardedPostProvisioningPhase `
        -BeforeRunRoots $beforeNominal.RunRoots `
        -ProvisioningAction {
            @(& $initializePath *>&1 | ForEach-Object { $_.ToString() })
        } `
        -RunRootSnapshotAction { @(Get-WO048RunRootSnapshot) } `
        -RollbackAuthorityAction {
            param([string]$RunId)
            Assert-WO048ExactRollbackAuthority -RunId $RunId
        } `
        -QualificationAction {
            param([string]$RunId, [string[]]$PublicOutput)

            # The private run-root delta and exact rollback authority have
            # already been captured. Store/CNG/listener inspection may fail
            # from here without losing the ability to invoke exact cleanup.
            $afterNominal = Get-WO048HostSnapshot
            $privateRecordSha256 = Assert-WO048ProvisioningPublicOutput `
                -Lines $PublicOutput -ExpectedRunId $RunId
            if (($PublicOutput -join "`n") -match
                '(?i)(thumbprint|certificate_sha256=|\\pki\\|\.p12|\.cer|storepassword)') {
                throw 'WO-048 nominal provisioning disclosed private certificate metadata.'
            }
            $addedClient = Get-WO048ExactAddedValue `
                -Before $beforeNominal.ClientCertificates `
                -After $afterNominal.ClientCertificates `
                -Description 'client certificate'
            $addedServer = Get-WO048ExactAddedValue `
                -Before $beforeNominal.ServerTrustCertificates `
                -After $afterNominal.ServerTrustCertificates `
                -Description 'server trust certificate'
            $addedRun = Get-WO048ExactAddedValue `
                -Before $beforeNominal.RunRoots `
                -After $afterNominal.RunRoots `
                -Description 'private run root'
            if ($addedClient -notmatch '^[0-9A-F]{40}$' -or
                $addedServer -notmatch '^[0-9A-F]{40}$' -or
                $addedRun -cne $RunId -or
                -not (Test-WO048ExactSequenceEqual `
                    $beforeNominal.ListenerCounts $afterNominal.ListenerCounts)) {
                throw 'WO-048 nominal provisioning changed an unauthorized host boundary.'
            }
            $newPrivateKeyFiles = @($afterNominal.UserPrivateKeyFiles | Where-Object {
                $_ -notin $beforeNominal.UserPrivateKeyFiles
            })
            $removedPrivateKeyFiles = @($beforeNominal.UserPrivateKeyFiles | Where-Object {
                $_ -notin $afterNominal.UserPrivateKeyFiles
            })
            if ($newPrivateKeyFiles.Count -ne 1 -or
                $removedPrivateKeyFiles.Count -ne 0) {
                throw 'WO-048 did not retain one bounded non-exportable client private-key identity.'
            }
            Assert-WO048NominalPrivateRecord `
                -RunId $RunId `
                -ExpectedRecordSha256 $privateRecordSha256
            return [pscustomobject]@{
                RecordSha256 = $privateRecordSha256
            }
        } `
        -CleanupAction {
            param([string]$RunId)
            & $cleanupPath -RunId $RunId -Confirm:$false *> $null
        } `
        -RunRootAbsentAction {
            param([string]$RunId)
            return (@(Get-WO048RunRootSnapshot) -cnotcontains $RunId)
        }
    $runId = $guardedResult.RunId
    $recordSha256 = $guardedResult.QualificationResult.RecordSha256
}
finally {
    if ($script:wo048PkiModuleOwnedByThisInvocation) {
        Remove-Module -Name 'WO048-PkiTools' -Force `
            -ErrorAction SilentlyContinue
        $script:wo048PkiModuleOwnedByThisInvocation = $false
    }
}

Write-Output 'WO048_LOCAL_MTLS_IDENTITY_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED'
Write-Output 'WO048_FAILURE_POINTS=7_OF_7_PASS'
Write-Output "WO048_SELECTED_RUN_ID=$runId"
Write-Output "WO048_PRIVATE_IDENTITY_RECORD_SHA256=$recordSha256"
Write-Output 'WO048_SELECTED_CLIENT_IDENTITY_COUNT=1'
Write-Output 'WO048_SELECTED_RECEIVER_IDENTITY_COUNT=1'
Write-Output 'WO048_CLIENT_PRIVATE_KEY_PROFILE=CNG_NON_EXPORTABLE'
Write-Output 'WO048_JAVA_25_PROVENANCE=PASS'
Write-Output 'WO048_JAVA_SUNMSCAPI_STORE_QUALIFICATION=PASS_NO_HANDSHAKE'
Write-Output 'WO048_PRIVATE_CERTIFICATE_FINGERPRINT_DISCLOSED=NO'
Write-Output 'WO048_PRIVATE_PATH_DISCLOSED=NO'
Write-Output 'WO048_DATABASES_TOUCHED=NO'
Write-Output 'WO048_APPLICATIONS_STARTED=NO'
Write-Output 'WO048_TLS_HANDSHAKES=0'
Write-Output 'WO048_SOCKETS_OPENED=0'
Write-Output 'WO048_PROVIDER_CALLS=0'
Write-Output 'WO048_REMOTE_NETWORK_CALLS=0'
