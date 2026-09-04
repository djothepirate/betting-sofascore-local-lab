[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({
        if ($_ -cnotmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$') {
            throw 'WO-048 cleanup requires a canonical lowercase UUID-D RunId.'
        }
        $true
    })]
    [string]$RunId
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$workOrder = 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign'
$provisioningWorkOrder =
    'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning'
$modulePath = Join-Path $PSScriptRoot 'wo048\WO048-PkiTools.psm1'
if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf)) {
    throw 'The exact versioned WO-048 PKI-only module is unavailable.'
}
Import-Module -Name $modulePath -Force -ErrorAction Stop

$script:toolsLock = $null

function Exit-WO048CleanupLock {
    if ($null -ne $script:toolsLock) {
        $script:toolsLock.Dispose()
        $script:toolsLock = $null
    }
}

function Test-WO048CurrentUserCertificateExists {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('My', 'Root')][string]$StoreName,
        [Parameter(Mandatory = $true)][string]$Thumbprint
    )

    $store = [System.Security.Cryptography.X509Certificates.X509Store]::new(
        $StoreName,
        [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser)
    try {
        $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadOnly)
        return $store.Certificates.Find(
            [System.Security.Cryptography.X509Certificates.X509FindType]::FindByThumbprint,
            $Thumbprint,
            $false).Count -ne 0
    }
    finally {
        $store.Dispose()
    }
}

function Assert-WO048CurrentUserCertificateRemoved {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('My', 'Root')][string]$StoreName,
        [Parameter(Mandatory = $true)][string]$Thumbprint
    )

    for ($attempt = 1; $attempt -le 40; $attempt++) {
        if (-not (Test-WO048CurrentUserCertificateExists `
                -StoreName $StoreName `
                -Thumbprint $Thumbprint)) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'An exact WO-048 CurrentUser certificate remains after bounded cleanup.'
}

function Get-WO048ExactCertificateFromProvider {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('My', 'Root')][string]$StoreName,
        [Parameter(Mandatory = $true)]$Record
    )

    $certificatePath = Join-Path "Cert:\CurrentUser\$StoreName" $Record.thumbprint
    $certificate = Get-Item -LiteralPath $certificatePath -ErrorAction SilentlyContinue
    if ($null -eq $certificate) {
        return $null
    }
    if ((Get-WO048CertificateSha256 -Certificate $certificate) -ne $Record.sha256 -or
        $certificate.Subject -cne $Record.subject) {
        $certificate.Dispose()
        throw 'A certificate-store entry failed exact WO-048 ownership verification.'
    }
    return $certificate
}

function Remove-WO048ExactRootCertificate {
    param([Parameter(Mandatory = $true)]$Record)

    $store = [System.Security.Cryptography.X509Certificates.X509Store]::new(
        'Root',
        [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser)
    try {
        $store.Open(
            [System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite -bor
            [System.Security.Cryptography.X509Certificates.OpenFlags]::OpenExistingOnly)
        $matches = @($store.Certificates.Find(
            [System.Security.Cryptography.X509Certificates.X509FindType]::FindByThumbprint,
            $Record.thumbprint,
            $false))
        if ($matches.Count -eq 0) {
            return
        }
        if ($matches.Count -ne 1 -or
            (Get-WO048CertificateSha256 -Certificate $matches[0]) -ne $Record.sha256 -or
            $matches[0].Subject -cne $Record.subject) {
            throw 'The CurrentUser Root entry failed exact WO-048 ownership verification.'
        }
        $store.Remove($matches[0])
    }
    finally {
        $store.Dispose()
    }
    Assert-WO048CurrentUserCertificateRemoved `
        -StoreName Root `
        -Thumbprint $Record.thumbprint
}

function Assert-WO048ExactClientCngRecord {
    param([Parameter(Mandatory = $true)]$Record)

    $cng = $Record.cngKey
    $roamingApplicationData = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::ApplicationData)
    if ([string]::IsNullOrWhiteSpace($roamingApplicationData)) {
        throw 'WO-048 cannot resolve the current-user CNG recovery root.'
    }
    $canonicalRoamingRoot = Assert-WO048LocalFixedPathPrefix `
        -Path $roamingApplicationData -RequireExistingType Directory
    $expectedKeyRoot = Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $canonicalRoamingRoot 'Microsoft\Crypto\Keys')
    if ($null -eq $cng -or
        $cng.providerName -cne 'Microsoft Software Key Storage Provider' -or
        [string]::IsNullOrWhiteSpace([string]$cng.keyName) -or
        $cng.uniqueName -notmatch '^[A-Za-z0-9._-]+$' -or
        $cng.algorithmGroup -cne 'RSA' -or
        [int]$cng.keySize -ne 3072 -or
        $cng.exportPolicy -cne 'None' -or
        $cng.keyUsage -cne 'Signing' -or
        $cng.isEphemeral -isnot [bool] -or $cng.isEphemeral -or
        [long]$cng.keyFileLength -le 0 -or
        [string]::IsNullOrWhiteSpace([string]$cng.keyFileCreationTimeUtc)) {
        throw 'The WO-048 client CNG recovery identity is incomplete.'
    }
    $expectedKeyPath = [System.IO.Path]::GetFullPath((Join-Path $expectedKeyRoot `
        ([string]$cng.uniqueName)))
    Assert-WO048ExactPath -Actual ([string]$cng.keyFilePath) -Expected $expectedKeyPath `
        -Failure 'The WO-048 client CNG key-file identity escaped its exact user root.'
    [void](Assert-WO048LocalFixedPathPrefix -Path $expectedKeyPath)
}

function Test-WO048ExactClientCngKeyExists {
    param([Parameter(Mandatory = $true)]$Record)

    Assert-WO048ExactClientCngRecord -Record $Record
    $provider = [System.Security.Cryptography.CngProvider]::new(
        [string]$Record.cngKey.providerName)
    for ($attempt = 1; $attempt -le 40; $attempt++) {
        $containerExists = [System.Security.Cryptography.CngKey]::Exists(
            [string]$Record.cngKey.keyName,
            $provider)
        $fileExists = Test-Path -LiteralPath ([string]$Record.cngKey.keyFilePath) -PathType Leaf
        if ($containerExists -eq $fileExists) {
            return $containerExists
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'The WO-048 client CNG container APIs report a persistent inconsistent residual state.'
}

function Assert-WO048LiveClientCngIdentity {
    param(
        [Parameter(Mandatory = $true)]$Record,
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $actual = Get-WO048ClientCngKeyIdentity -Certificate $Certificate
    foreach ($property in @(
            'providerName', 'keyName', 'uniqueName', 'keyFilePath', 'keyFileLength',
            'keyFileCreationTimeUtc', 'algorithmGroup', 'keySize', 'exportPolicy',
            'keyUsage', 'isEphemeral')) {
        if ([string]$actual.$property -cne [string]$Record.cngKey.$property) {
            throw 'The live WO-048 client CNG identity differs from its recovery authority.'
        }
    }
}

function Remove-WO048ExactOrphanedClientCngKey {
    param([Parameter(Mandatory = $true)]$Record)

    if (-not (Test-WO048ExactClientCngKeyExists -Record $Record)) {
        return
    }
    $provider = [System.Security.Cryptography.CngProvider]::new(
        [string]$Record.cngKey.providerName)
    $key = [System.Security.Cryptography.CngKey]::Open(
        [string]$Record.cngKey.keyName,
        $provider)
    try {
        if ($key.UniqueName -cne [string]$Record.cngKey.uniqueName -or
            $key.Provider.Provider -cne [string]$Record.cngKey.providerName -or
            $key.AlgorithmGroup.AlgorithmGroup -cne 'RSA' -or
            $key.KeySize -ne 3072 -or
            $key.ExportPolicy -ne [System.Security.Cryptography.CngExportPolicies]::None -or
            $key.KeyUsage -ne [System.Security.Cryptography.CngKeyUsages]::Signing -or
            $key.IsEphemeral) {
            throw 'An orphaned WO-048 CNG key failed exact identity verification.'
        }
        $key.Delete()
    }
    finally {
        $key.Dispose()
    }
    for ($attempt = 1; $attempt -le 40; $attempt++) {
        $containerExists = [System.Security.Cryptography.CngKey]::Exists(
            [string]$Record.cngKey.keyName,
            $provider)
        $fileExists = Test-Path -LiteralPath ([string]$Record.cngKey.keyFilePath) -PathType Leaf
        if (-not $containerExists -and -not $fileExists) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'The exact WO-048 client CNG key remains after bounded deletion.'
}

function Remove-WO048ExactOwnedCertificates {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    foreach ($record in @($State.ownedCertificates)) {
        if ($record.role -eq 'receiver-server-direct-trust') {
            $expectedStore = 'CurrentUser\Root'
            $storeName = 'Root'
            $expectedSubject =
                'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
        }
        elseif ($record.role -eq 'sender-client') {
            $expectedStore = 'CurrentUser\My'
            $storeName = 'My'
            $expectedSubject =
                "CN=WO046 sender $($State.runId), OU=WO-046, O=Betting Project Local Qualification"
        }
        else {
            throw 'A WO-048 certificate record has an unexpected role.'
        }
        if ($record.storeLocation -cne $expectedStore -or
            $record.subject -cne $expectedSubject -or
            $record.ownershipStatus -notin @(
                'NOT_CREATED', 'DECLARED_BEFORE_IMPORT',
                'CERTIFICATE_DECLARED', 'OWNED')) {
            throw 'A WO-048 owned-certificate record is incomplete.'
        }
        if ($record.ownershipStatus -eq 'NOT_CREATED') {
            if ($null -ne $record.thumbprint -or $null -ne $record.sha256 -or
                $null -ne $record.cngKey) {
                throw 'A WO-048 uncreated certificate record contains ownership material.'
            }
            continue
        }
        if ($record.thumbprint -notmatch '^[0-9A-F]{40}$' -or
            $record.sha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'A WO-048 declared certificate record lacks an exact identity.'
        }
        if ($record.ownershipStatus -eq 'CERTIFICATE_DECLARED' -and
            ($storeName -ne 'My' -or $null -ne $record.cngKey)) {
            throw 'A WO-048 certificate-only recovery declaration is not exact.'
        }
        if ($storeName -eq 'My' -and
            $record.ownershipStatus -ne 'CERTIFICATE_DECLARED') {
            Assert-WO048ExactClientCngRecord -Record $record
        }
        $certificate = Get-WO048ExactCertificateFromProvider `
            -StoreName $storeName `
            -Record $record
        if ($null -ne $certificate) {
            if ($storeName -eq 'My') {
                if ($record.ownershipStatus -eq 'CERTIFICATE_DECLARED') {
                    # Persist the exact CNG authority before any destructive
                    # action. If the inspection or write fails, the certificate
                    # and partial declaration remain for a later exact retry.
                    $record.cngKey = Get-WO048ClientCngKeyIdentity `
                        -Certificate $certificate
                    $record.ownershipStatus = 'OWNED'
                    Write-WO048PrivateJsonAtomic `
                        -Path $StatePath `
                        -Value $State `
                        -OwnerSid $OwnerSid
                }
                Assert-WO048LiveClientCngIdentity -Record $record -Certificate $certificate
            }
            $certificate.Dispose()
            if (-not $PSCmdlet.ShouldProcess(
                    "exact owned WO-048 certificate role $($record.role)",
                    'Remove certificate and, for the client role, its private key')) {
                throw 'WO-048 certificate cleanup confirmation was declined.'
            }
            if ($storeName -eq 'Root') {
                Remove-WO048ExactRootCertificate -Record $record
            }
            else {
                Remove-Item `
                    -LiteralPath (Join-Path 'Cert:\CurrentUser\My' $record.thumbprint) `
                    -DeleteKey `
                    -Force
                Assert-WO048CurrentUserCertificateRemoved `
                    -StoreName My `
                    -Thumbprint $record.thumbprint
            }
        }
        elseif ($storeName -eq 'My' -and
            $record.ownershipStatus -eq 'CERTIFICATE_DECLARED') {
            throw 'WO-048 cannot prove a certificate-only client declaration has no orphaned CNG key.'
        }
        if ($storeName -eq 'My') {
            Remove-WO048ExactOrphanedClientCngKey -Record $record
        }
    }
}

function Get-WO048OwnedClientPrivateKeyResidualCount {
    param([Parameter(Mandatory = $true)]$State)

    $clientRecords = @($State.ownedCertificates | Where-Object {
        $_.role -ceq 'sender-client'
    })
    if ($clientRecords.Count -ne 1) {
        throw 'The WO-048 client recovery record cardinality is not exact.'
    }
    if ($clientRecords[0].ownershipStatus -eq 'NOT_CREATED') {
        return 0
    }
    return [int](Test-WO048ExactClientCngKeyExists -Record $clientRecords[0])
}

function Get-WO048OwnedCertificateResidualCount {
    param([Parameter(Mandatory = $true)]$State)

    $count = 0
    foreach ($record in @($State.ownedCertificates)) {
        if ($record.ownershipStatus -eq 'NOT_CREATED') {
            continue
        }
        $storeName = if ($record.role -eq 'receiver-server-direct-trust') {
            'Root'
        }
        elseif ($record.role -eq 'sender-client') {
            'My'
        }
        else {
            throw 'A WO-048 residual certificate has an unexpected role.'
        }
        $certificate = Get-WO048ExactCertificateFromProvider `
            -StoreName $storeName `
            -Record $record
        if ($null -ne $certificate) {
            $count++
            $certificate.Dispose()
        }
    }
    return $count
}

function Assert-WO048PrivateTreeShape {
    param(
        [Parameter(Mandatory = $true)][string]$RunRoot,
        [Parameter(Mandatory = $true)]$State
    )

    $expectedRootNames = @('.wo048-owner.json', '.wo048-pki.lock', 'identity.private.json')
    if (Test-Path -LiteralPath (Join-Path $RunRoot 'pki') -PathType Container) {
        $expectedRootNames += 'pki'
    }
    elseif ($State.cleanupStatus -notin @(
            'REMOVING_PRIVATE_FILES', 'PRIVATE_FILES_REMOVED', 'FINALIZING_METADATA')) {
        throw 'The WO-048 PKI directory is missing before its cleanup phase.'
    }
    $actualRootNames = @(Get-ChildItem -LiteralPath $RunRoot -Force |
        Select-Object -ExpandProperty Name |
        Sort-Object)
    if (@(Compare-Object `
            -ReferenceObject @($expectedRootNames | Sort-Object) `
            -DifferenceObject $actualRootNames).Count -ne 0) {
        throw 'The WO-048 private root contains an unexpected or missing direct child.'
    }

    $pkiRoot = Join-Path $RunRoot 'pki'
    $allowedPkiNames = @(
        'java-store-probe-classes',
        'java-store-probe-input.private.properties',
        'native-java.stderr.private.txt',
        'native-java.stdout.private.txt',
        'receiver-client-trust.private.p12',
        'receiver-server.private.p12',
        'receiver-server.public.cer',
        'sender-client.public.cer')
    $actualPkiNames = if (Test-Path -LiteralPath $pkiRoot -PathType Container) {
        @(Get-ChildItem -LiteralPath $pkiRoot -Force | Select-Object -ExpandProperty Name)
    }
    else {
        @()
    }
    foreach ($name in $actualPkiNames) {
        if ($name -notin $allowedPkiNames) {
            throw 'The WO-048 PKI directory contains an unexpected child.'
        }
    }

    $expectedPathMap = @{
        serverKeyStorePath = Join-Path $pkiRoot 'receiver-server.private.p12'
        serverPublicCertificatePath = Join-Path $pkiRoot 'receiver-server.public.cer'
        receiverTrustStorePath = Join-Path $pkiRoot 'receiver-client-trust.private.p12'
        clientPublicCertificatePath = Join-Path $pkiRoot 'sender-client.public.cer'
        javaStoreProbeInputPath = Join-Path $pkiRoot 'java-store-probe-input.private.properties'
        javaStoreProbeClassRoot = Join-Path $pkiRoot 'java-store-probe-classes'
    }
    foreach ($property in $expectedPathMap.Keys) {
        Assert-WO048ExactPath `
            -Actual ([string]$State.pki.$property) `
            -Expected $expectedPathMap[$property] `
            -Failure 'A WO-048 PKI path does not match its exact owned location.'
    }
    $probeClassRoot = Join-Path $pkiRoot 'java-store-probe-classes'
    if (Test-Path -LiteralPath $probeClassRoot -PathType Container) {
        $probeChildren = @(Get-ChildItem -LiteralPath $probeClassRoot -Force)
        if ($probeChildren.Count -gt 1 -or
            @($probeChildren | Where-Object {
                $_.Name -cne 'WO048WindowsStoreProbe.class' -or $_.PSIsContainer
            }).Count -ne 0) {
            throw 'The WO-048 Java probe directory contains an unexpected child.'
        }
    }
}

function Assert-WO048ExactCertificateRecordSet {
    param(
        [Parameter(Mandatory = $true)][object[]]$Records,
        [Parameter(Mandatory = $true)][string]$CanonicalRunId,
        [switch]$RequireOwned
    )

    Assert-WO048CertificateRecordSetShape `
        -Records $Records -CanonicalRunId $CanonicalRunId -RequireOwned:$RequireOwned
    foreach ($record in $Records) {
        if ($record.ownershipStatus -ceq 'NOT_CREATED') {
            continue
        }
        if ($record.role -ceq 'sender-client') {
            if ($record.ownershipStatus -cne 'CERTIFICATE_DECLARED') {
                Assert-WO048ExactClientCngRecord -Record $record
            }
        }
    }
}

try {
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'WO-048 exact PKI cleanup is supported only on Windows.'
}
if ($WhatIfPreference) {
    throw 'WO-048 cleanup refuses WhatIf because its fail-closed transition must be durable.'
}

$parsedRunId = [guid]::ParseExact($RunId, 'D')
$canonicalRunId = $parsedRunId.ToString('D')
[void](Assert-WO048CanonicalLowercaseRunId -RunId $RunId)
$ownerSid = Get-WO048CurrentOwnerSid
$campaignBase = Resolve-WO048CampaignBase
$runRoot = [System.IO.Path]::GetFullPath((Join-Path $campaignBase $canonicalRunId))
if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) {
    Write-Output 'WO048_PKI_CLEANUP=PASS_ALREADY_ABSENT'
    Write-Output "WO048_RUN_ID=$canonicalRunId"
    return
}
Assert-WO048ImmediateGuidChild -Parent $campaignBase -Child $runRoot -RunId $parsedRunId
Assert-WO048NoDescendantReparsePoint -Root $runRoot
Assert-WO048PrivateAcl -Path $runRoot -OwnerSid $ownerSid.Value -RequireProtected

$statePath = Join-Path $runRoot 'identity.private.json'
$markerPath = Join-Path $runRoot '.wo048-owner.json'
$lockPath = Join-Path $runRoot '.wo048-pki.lock'
if (-not (Test-Path -LiteralPath $lockPath -PathType Leaf)) {
    $stateExistsWithoutLock = Test-Path -LiteralPath $statePath -PathType Leaf
    $markerExistsWithoutLock = Test-Path -LiteralPath $markerPath -PathType Leaf
    $rootChildrenWithoutLock = @(Get-ChildItem -LiteralPath $runRoot -Force |
        Select-Object -ExpandProperty Name)
    $recoveryPhaseWithoutLock = Get-WO048FinalizationRecoveryPhase `
        -StateExists $stateExistsWithoutLock `
        -MarkerExists $markerExistsWithoutLock `
        -LockExists $false `
        -ChildNames $rootChildrenWithoutLock
    if ($recoveryPhaseWithoutLock -eq 'FINAL_MARKER_ONLY') {
        Assert-WO048NotReparsePoint -Path $markerPath
        Assert-WO048PrivateAcl -Path $markerPath -OwnerSid $ownerSid.Value `
            -RequireProtected
        $finalMarker = Read-WO048StrictUtf8JsonObject -Path $markerPath
        Assert-WO048FinalizationMarkerAuthority `
            -Marker $finalMarker `
            -ExpectedWorkOrder $workOrder `
            -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
            -ExpectedRunId $canonicalRunId `
            -ExpectedOwnerSid $ownerSid.Value `
            -ExpectedRunRoot $runRoot `
            -ExpectedMarkerPath $markerPath
        $finalRecords = @($finalMarker.finalCertificateRecords)
        Assert-WO048ExactCertificateRecordSet `
            -Records $finalRecords `
            -CanonicalRunId $canonicalRunId
        $finalRecoveryState = [pscustomobject]@{ ownedCertificates = $finalRecords }
        if ((Get-WO048OwnedCertificateResidualCount -State $finalRecoveryState) -ne 0 -or
            (Get-WO048OwnedClientPrivateKeyResidualCount -State $finalRecoveryState) -ne 0) {
            throw 'WO-048 marker-only recovery detected a residual owned identity.'
        }
        if (-not $PSCmdlet.ShouldProcess(
                'exact finalized WO-048 marker-only GUID root',
                'Delete final marker and empty exact run root')) {
            throw 'WO-048 marker-only recovery confirmation was declined.'
        }
        Remove-Item -LiteralPath $markerPath -Force
        if (@(Get-ChildItem -LiteralPath $runRoot -Force).Count -ne 0) {
            throw 'The WO-048 marker-only root is not empty after marker removal.'
        }
        Remove-Item -LiteralPath $runRoot -Force
        if (Test-Path -LiteralPath $runRoot) {
            throw 'The interrupted exact WO-048 marker-only root remains after recovery.'
        }
        Write-Output 'WO048_PKI_CLEANUP=PASS_RESUMED_MARKER_ONLY'
        Write-Output "WO048_RUN_ID=$canonicalRunId"
        Write-Output 'WO048_PRIVATE_ROOT_RESIDUAL_COUNT=0'
        Remove-Module -Name 'WO048-PkiTools' -Force -ErrorAction SilentlyContinue
        return
    }
    if ($recoveryPhaseWithoutLock -cne 'FINAL_EMPTY_ROOT') {
        throw 'The WO-048 private root has no recoverable finalization authority.'
    }
    if (-not $PSCmdlet.ShouldProcess(
            'exact empty finalized WO-048 GUID root', 'Complete interrupted root deletion')) {
        throw 'WO-048 empty-root recovery confirmation was declined.'
    }
    Remove-Item -LiteralPath $runRoot -Force
    if (Test-Path -LiteralPath $runRoot) {
        throw 'The interrupted exact WO-048 empty root remains after recovery.'
    }
    Write-Output 'WO048_PKI_CLEANUP=PASS_RESUMED_EMPTY_ROOT'
    Write-Output "WO048_RUN_ID=$canonicalRunId"
    Write-Output 'WO048_PRIVATE_ROOT_RESIDUAL_COUNT=0'
    Remove-Module -Name 'WO048-PkiTools' -Force -ErrorAction SilentlyContinue
    return
}
Assert-WO048NotReparsePoint -Path $lockPath
Assert-WO048PrivateAcl -Path $lockPath -OwnerSid $ownerSid.Value -RequireProtected
try {
    $script:toolsLock = [System.IO.FileStream]::new(
        $lockPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None)
}
catch {
    throw 'The WO-048 PKI lock is unavailable; concurrent cleanup is refused.'
}

$rootRemoved = $false
try {
    $lockJournal = Read-WO048FinalizationLockJournalFromStream `
        -Stream $script:toolsLock
    foreach ($authorityFrame in @($lockJournal.Authorities)) {
        Assert-WO048FinalizationLockAuthority `
            -Authority $authorityFrame `
            -ExpectedWorkOrder $workOrder `
            -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
            -ExpectedRunId $canonicalRunId `
            -ExpectedOwnerSid $ownerSid.Value `
            -ExpectedRunRoot $runRoot `
            -ExpectedMarkerPath $markerPath `
            -ExpectedLockPath $lockPath
    }
    if ($lockJournal.Authorities[0].finalizationStatus -cne 'NOT_READY' -or
        ($lockJournal.Authorities.Count -eq 2 -and
            $lockJournal.Authorities[1].finalizationStatus -cne
                'READY_AFTER_RESIDUAL_PROOF') -or
        @($lockJournal.Authorities | Select-Object -ExpandProperty `
            stateIdentityBindingSha256 -Unique).Count -ne 1) {
        throw 'The WO-048 finalization lock journal transition sequence is invalid.'
    }
    $lockAuthority = $lockJournal.Latest
    $stateExists = Test-Path -LiteralPath $statePath -PathType Leaf
    $markerExists = Test-Path -LiteralPath $markerPath -PathType Leaf
    $state = $null
    $marker = $null
    $rootChildren = @(Get-ChildItem -LiteralPath $runRoot -Force |
        Select-Object -ExpandProperty Name)
    $recoveryPhase = Get-WO048FinalizationRecoveryPhase `
        -StateExists $stateExists `
        -MarkerExists $markerExists `
        -LockExists $true `
        -ChildNames $rootChildren
    if ($stateExists) {
        if (-not $markerExists) {
            throw 'WO-048 refuses a state record whose immutable binding marker is missing.'
        }
        foreach ($privatePath in @($statePath, $markerPath)) {
            Assert-WO048NotReparsePoint -Path $privatePath
            Assert-WO048PrivateAcl -Path $privatePath -OwnerSid $ownerSid.Value -RequireProtected
        }
        $loadedStateSha256 = Get-WO048FileSha256 -Path $statePath
        $state = Read-WO048StrictUtf8JsonObject -Path $statePath
        $marker = Read-WO048StrictUtf8JsonObject -Path $markerPath
        Assert-WO048CleanupRecoveryAuthorityShape `
            -State $state `
            -ExpectedWorkOrder $workOrder `
            -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
            -ExpectedRunId $canonicalRunId `
            -ExpectedOwnerSid $ownerSid.Value `
            -ExpectedRunRoot $runRoot `
            -ExpectedMarkerPath $markerPath `
            -ExpectedLockPath $lockPath
        $binding = Get-WO048StateIdentityBinding `
            -WorkOrder $state.workOrder `
            -ProvisioningWorkOrder $state.provisioningWorkOrder `
            -RunId $state.runId `
            -OwnerSid $state.ownerSid `
            -CreatedAtUtc $state.createdAtUtc `
            -RunRoot $state.runRoot `
            -MarkerPath $state.markerPath
        if ($marker.schemaVersion -ne 2 -or
            $marker.workOrder -cne $state.workOrder -or
            $marker.provisioningWorkOrder -cne $state.provisioningWorkOrder -or
            $marker.runId -cne $state.runId -or $marker.ownerSid -cne $state.ownerSid -or
            $marker.createdAtUtc -cne $state.createdAtUtc -or
            $marker.runRoot -cne $state.runRoot -or
            $marker.markerPath -cne $state.markerPath -or
            $state.stateIdentityBindingSha256 -cne $binding -or
            $marker.stateIdentityBindingSha256 -cne $binding -or
            $lockAuthority.stateIdentityBindingSha256 -cne $binding) {
            throw 'The WO-048 marker does not bind the immutable private-state identity.'
        }
        if ($marker.provisioningStateStatus -notin @(
                'IN_PROGRESS_UNBOUND', 'PASS_BOUND', 'CLEANUP_TRANSITION') -or
            ($marker.provisioningStateStatus -eq 'PASS_BOUND' -and
                ($marker.passStateSha256 -notmatch '^[0-9a-f]{64}$' -or
                    $marker.passStateSha256 -cne $loadedStateSha256)) -or
            ($state.provisioningStatus -eq 'PASS' -and
                $marker.provisioningStateStatus -eq 'IN_PROGRESS_UNBOUND' -and
                $marker.passStateSha256 -ne $null)) {
            throw 'The WO-048 marker PASS-state commitment is invalid.'
        }
        foreach ($partialPath in @("$statePath.partial", "$markerPath.partial")) {
            if (Test-Path -LiteralPath $partialPath -PathType Leaf) {
                Assert-WO048NotReparsePoint -Path $partialPath
                Assert-WO048PrivateAcl -Path $partialPath -OwnerSid $ownerSid.Value `
                    -RequireProtected
                Remove-Item -LiteralPath $partialPath -Force
            }
        }
        Assert-WO048PrivateTreeShape -RunRoot $runRoot -State $state

        $records = @($state.ownedCertificates)
        Assert-WO048ExactCertificateRecordSet -Records $records `
            -CanonicalRunId $canonicalRunId -RequireOwned:($state.provisioningStatus -eq 'PASS')
        if ($marker.provisioningStateStatus -ne 'CLEANUP_TRANSITION') {
            $marker.provisioningStateStatus = 'CLEANUP_TRANSITION'
            Write-WO048PrivateJsonAtomic -Path $markerPath -Value $marker -OwnerSid $ownerSid
        }
        if ($state.cleanupStatus -in @('NOT_STARTED', 'REMOVING_CERTIFICATES')) {
            $state.cleanupStatus = 'REMOVING_CERTIFICATES'
            Write-WO048PrivateJsonAtomic -Path $statePath -Value $state -OwnerSid $ownerSid
            Remove-WO048ExactOwnedCertificates `
                -State $state `
                -StatePath $statePath `
                -OwnerSid $ownerSid
        }
        $certificateResidualCount = Get-WO048OwnedCertificateResidualCount -State $state
        $privateKeyResidualCount = Get-WO048OwnedClientPrivateKeyResidualCount -State $state
        if ($certificateResidualCount -ne 0 -or $privateKeyResidualCount -ne 0) {
            throw 'An exact WO-048 certificate or CNG private-key container remains after cleanup.'
        }
        Write-Output 'WO048_CLEANUP_CERTIFICATE_RESIDUAL_COUNT=0'
        Write-Output 'WO048_CLEANUP_CLIENT_PRIVATE_KEY_RESIDUAL_COUNT=0'
        if ($state.cleanupStatus -notin @(
                'REMOVING_PRIVATE_FILES', 'PRIVATE_FILES_REMOVED', 'FINALIZING_METADATA')) {
            $state.cleanupStatus = 'CERTIFICATES_REMOVED'
            Write-WO048PrivateJsonAtomic -Path $statePath -Value $state -OwnerSid $ownerSid
            if (-not $PSCmdlet.ShouldProcess(
                    'exact owned WO-048 private run root',
                    'Remove allowlisted private artifacts and finalize the exact run root')) {
                throw 'WO-048 private-root cleanup confirmation was declined.'
            }
            $state.cleanupStatus = 'REMOVING_PRIVATE_FILES'
            Write-WO048PrivateJsonAtomic -Path $statePath -Value $state -OwnerSid $ownerSid
        }
        $pkiRoot = Join-Path $runRoot 'pki'
        if (Test-Path -LiteralPath $pkiRoot -PathType Container) {
            Assert-WO048PrivateTreeShape -RunRoot $runRoot -State $state
            $classRoot = Join-Path $pkiRoot 'java-store-probe-classes'
            $classFile = Join-Path $classRoot 'WO048WindowsStoreProbe.class'
            if (Test-Path -LiteralPath $classFile -PathType Leaf) {
                Assert-WO048NotReparsePoint -Path $classFile
                Remove-Item -LiteralPath $classFile -Force
            }
            if (Test-Path -LiteralPath $classRoot -PathType Container) {
                if (@(Get-ChildItem -LiteralPath $classRoot -Force).Count -ne 0) {
                    throw 'The exact WO-048 Java probe directory is not empty.'
                }
                Remove-Item -LiteralPath $classRoot -Force
            }
            foreach ($fileName in @(
                    'java-store-probe-input.private.properties',
                    'native-java.stderr.private.txt',
                    'native-java.stdout.private.txt',
                    'receiver-client-trust.private.p12', 'receiver-server.private.p12',
                    'receiver-server.public.cer', 'sender-client.public.cer')) {
                $path = Join-Path $pkiRoot $fileName
                if (Test-Path -LiteralPath $path -PathType Leaf) {
                    Assert-WO048NotReparsePoint -Path $path
                    Remove-Item -LiteralPath $path -Force
                }
            }
            if (@(Get-ChildItem -LiteralPath $pkiRoot -Force).Count -ne 0) {
                throw 'The exact WO-048 PKI directory is not empty after allowlisted cleanup.'
            }
            Remove-Item -LiteralPath $pkiRoot -Force
        }
        $state.cleanupStatus = 'PRIVATE_FILES_REMOVED'
        Write-WO048PrivateJsonAtomic -Path $statePath -Value $state -OwnerSid $ownerSid
        $lockAuthority.finalizationStatus = 'READY_AFTER_RESIDUAL_PROOF'
        $lockAuthority.finalCertificateRecords = $records
        Assert-WO048FinalizationLockAuthority `
            -Authority $lockAuthority `
            -ExpectedWorkOrder $workOrder `
            -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
            -ExpectedRunId $canonicalRunId `
            -ExpectedOwnerSid $ownerSid.Value `
            -ExpectedRunRoot $runRoot `
            -ExpectedMarkerPath $markerPath `
            -ExpectedLockPath $lockPath `
            -RequireFinalizationReady
        Write-WO048FinalizationLockAuthorityToStream `
            -Stream $script:toolsLock `
            -Authority $lockAuthority
        $marker.finalizationStatus = 'READY_AFTER_RESIDUAL_PROOF'
        $marker.finalCertificateRecords = $records
        Write-WO048PrivateJsonAtomic -Path $markerPath -Value $marker -OwnerSid $ownerSid
        $state.cleanupStatus = 'FINALIZING_METADATA'
        Write-WO048PrivateJsonAtomic -Path $statePath -Value $state -OwnerSid $ownerSid
        Remove-Item -LiteralPath $statePath -Force
        $stateExists = $false
    }

    if ($markerExists -or (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        if ($null -eq $marker) {
            Assert-WO048NotReparsePoint -Path $markerPath
            Assert-WO048PrivateAcl -Path $markerPath -OwnerSid $ownerSid.Value -RequireProtected
            $marker = Read-WO048StrictUtf8JsonObject -Path $markerPath
        }
        Assert-WO048FinalizationMarkerAuthority `
            -Marker $marker `
            -ExpectedWorkOrder $workOrder `
            -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
            -ExpectedRunId $canonicalRunId `
            -ExpectedOwnerSid $ownerSid.Value `
            -ExpectedRunRoot $runRoot `
            -ExpectedMarkerPath $markerPath
        if ($marker.stateIdentityBindingSha256 -cne
            $lockAuthority.stateIdentityBindingSha256) {
            throw 'The WO-048 marker and finalization lock identity bindings diverged.'
        }
        $finalRecords = @($marker.finalCertificateRecords)
        Assert-WO048ExactCertificateRecordSet -Records $finalRecords `
            -CanonicalRunId $canonicalRunId
        $recoveryState = [pscustomobject]@{ ownedCertificates = $finalRecords }
        if ((Get-WO048OwnedCertificateResidualCount -State $recoveryState) -ne 0 -or
            (Get-WO048OwnedClientPrivateKeyResidualCount -State $recoveryState) -ne 0 -or
            (Test-Path -LiteralPath (Join-Path $runRoot 'pki'))) {
            throw 'WO-048 finalization recovery detected a residual owned artifact.'
        }
    }
    else {
        $finalRecords = @($lockAuthority.finalCertificateRecords)
        Assert-WO048ExactCertificateRecordSet -Records $finalRecords `
            -CanonicalRunId $canonicalRunId
        $recoveryState = [pscustomobject]@{ ownedCertificates = $finalRecords }
        if ((Get-WO048OwnedCertificateResidualCount -State $recoveryState) -ne 0 -or
            (Get-WO048OwnedClientPrivateKeyResidualCount -State $recoveryState) -ne 0 -or
            (Test-Path -LiteralPath (Join-Path $runRoot 'pki'))) {
            throw 'WO-048 lock-only recovery detected a residual owned artifact.'
        }
    }

    Assert-WO048FinalizationLockAuthority `
        -Authority $lockAuthority `
        -ExpectedWorkOrder $workOrder `
        -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
        -ExpectedRunId $canonicalRunId `
        -ExpectedOwnerSid $ownerSid.Value `
        -ExpectedRunRoot $runRoot `
        -ExpectedMarkerPath $markerPath `
        -ExpectedLockPath $lockPath `
        -RequireFinalizationReady
    if ($null -ne $marker -and
        (($marker.finalCertificateRecords | ConvertTo-Json -Depth 12 -Compress) -cne
            ($lockAuthority.finalCertificateRecords | ConvertTo-Json -Depth 12 -Compress))) {
        throw 'The WO-048 marker and finalization lock recovery records diverged.'
    }

    $remainingBeforeLock = @(Get-ChildItem -LiteralPath $runRoot -Force |
        Select-Object -ExpandProperty Name)
    $finalPhaseWithLock = Get-WO048FinalizationRecoveryPhase `
        -StateExists $false `
        -MarkerExists (Test-Path -LiteralPath $markerPath -PathType Leaf) `
        -LockExists $true `
        -ChildNames $remainingBeforeLock
    if ($finalPhaseWithLock -notin @('FINAL_MARKER_AND_LOCK', 'FINAL_LOCK_ONLY')) {
        throw 'The WO-048 finalization topology is not recoverable before lock deletion.'
    }
    if (-not $PSCmdlet.ShouldProcess(
            'exact finalized WO-048 private run root',
            'Delete finalization lock, marker if present, and empty GUID root')) {
        throw 'WO-048 final root cleanup confirmation was declined.'
    }
    Exit-WO048CleanupLock
    Assert-WO048NotReparsePoint -Path $lockPath
    Remove-Item -LiteralPath $lockPath -Force
    $markerStillExists = Test-Path -LiteralPath $markerPath -PathType Leaf
    $afterLockChildren = @(Get-ChildItem -LiteralPath $runRoot -Force |
        Select-Object -ExpandProperty Name)
    $phaseAfterLock = Get-WO048FinalizationRecoveryPhase `
        -StateExists $false `
        -MarkerExists $markerStillExists `
        -LockExists $false `
        -ChildNames $afterLockChildren
    if ($phaseAfterLock -eq 'FINAL_MARKER_ONLY') {
        Remove-Item -LiteralPath $markerPath -Force
    }
    elseif ($phaseAfterLock -cne 'FINAL_EMPTY_ROOT') {
        throw 'The WO-048 finalization topology changed after lock deletion.'
    }
    $emptyPhase = Get-WO048FinalizationRecoveryPhase `
        -StateExists $false `
        -MarkerExists $false `
        -LockExists $false `
        -ChildNames @(Get-ChildItem -LiteralPath $runRoot -Force |
            Select-Object -ExpandProperty Name)
    if ($emptyPhase -cne 'FINAL_EMPTY_ROOT') {
        throw 'The WO-048 private run root is not empty after final authority deletion.'
    }
    Remove-Item -LiteralPath $runRoot -Force
    if (Test-Path -LiteralPath $runRoot) {
        throw 'The exact WO-048 private run root remains after cleanup.'
    }
    $rootRemoved = $true
}
finally {
    Exit-WO048CleanupLock
    Remove-Module -Name 'WO048-PkiTools' -Force -ErrorAction SilentlyContinue
}

if (-not $rootRemoved) {
    throw 'WO-048 cleanup did not remove its exact private run root.'
}
Write-Output 'WO048_PKI_CLEANUP=PASS'
Write-Output "WO048_RUN_ID=$canonicalRunId"
Write-Output 'WO048_FINALIZATION_AUTHORITY_PRESERVED_THROUGH_LOCK_DELETE=YES'
Write-Output 'WO048_PRIVATE_ROOT_RESIDUAL_COUNT=0'
Write-Output 'WO048_DATABASES_TOUCHED=NO'
Write-Output 'WO048_APPLICATIONS_STARTED=NO'
Write-Output 'WO048_TLS_HANDSHAKES=0'
Write-Output 'WO048_SOCKETS_OPENED=0'
Write-Output 'WO048_PROVIDER_CALLS=0'
Write-Output 'WO048_REMOTE_NETWORK_CALLS=0'
}
catch {
    $publicFailure = [InvalidOperationException]::new(
        'WO048_PKI_CLEANUP_FAILED_CLOSED')
    throw $publicFailure
}
