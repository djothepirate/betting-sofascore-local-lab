[CmdletBinding()]
param(
    [switch]$StartDatabases,
    [string]$DockerExecutablePath = '',
    [ValidateSet(
        'NONE',
        'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP',
        'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY',
        'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED',
        'BEFORE_CLIENT_CERTIFICATE_EXPORT')]
    [string]$QualificationFailurePoint = 'NONE'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$workOrder = 'WO-SS-20260902-036-j9-j7-local-e2e-qualification'
$repositoryRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$composePath = Join-Path $repositoryRoot 'compose.wo036.yml'
$cleanupPath = Join-Path $PSScriptRoot 'Remove-J7LocalE2eInfrastructure.ps1'
$script:state = $null
$script:statePath = $null
$script:runRoot = $null
$script:serverRootCertificateThumbprint = $null
$script:clientCertificateThumbprint = $null
$script:clientCertificate = $null
$script:preRegistrationClientCleanupStatus = 'NOT_REQUIRED'
$script:phase = 'PREFLIGHT'
$script:resolvedDockerExecutable = $null
$script:resolvedDockerEndpoint = $null
$script:toolsLock = $null

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function ConvertTo-LowerHex {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return [Convert]::ToHexString($Bytes).ToLowerInvariant()
}

function Get-Sha256Text {
    param([Parameter(Mandatory = $true)][string]$Value)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-LowerHex -Bytes $sha256.ComputeHash(
            [System.Text.Encoding]::UTF8.GetBytes($Value))
    }
    finally {
        $sha256.Dispose()
    }
}

function Get-CertificateSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-LowerHex -Bytes $sha256.ComputeHash($Certificate.RawData)
    }
    finally {
        $sha256.Dispose()
    }
}

function Test-CurrentUserCertificateExists {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('Root', 'My')][string]$StoreName,
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

function Assert-CurrentUserCertificateRemoved {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('Root', 'My')][string]$StoreName,
        [Parameter(Mandatory = $true)][string]$Thumbprint
    )

    for ($attempt = 1; $attempt -le 40; $attempt++) {
        if (-not (Test-CurrentUserCertificateExists -StoreName $StoreName -Thumbprint $Thumbprint)) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'An exact WO-036 CurrentUser certificate remains after bounded cleanup.'
}

function New-RandomUrlSafeValue {
    param([ValidateRange(16, 128)][int]$ByteCount = 32)

    $bytes = New-Object byte[] $ByteCount
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Write-PrivateState {
    if ($null -eq $script:state -or [string]::IsNullOrWhiteSpace($script:statePath)) {
        throw 'Private state has not been initialized.'
    }
    $temporaryPath = "$($script:statePath).partial"
    $json = $script:state | ConvertTo-Json -Depth 12
    Write-Utf8NoBomFile -Path $temporaryPath -Content ($json + [Environment]::NewLine)
    Move-Item -LiteralPath $temporaryPath -Destination $script:statePath -Force
}

function Assert-PrivateAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerSid
    )

    $acl = Get-Acl -LiteralPath $Path
    $actualOwnerSid = ([System.Security.Principal.NTAccount]$acl.Owner).Translate(
        [System.Security.Principal.SecurityIdentifier]).Value
    if ($actualOwnerSid -ne $OwnerSid) {
        throw 'The private campaign root owner does not match the current Windows user.'
    }
    if (-not $acl.AreAccessRulesProtected) {
        throw 'The private campaign root still inherits access rules.'
    }
    foreach ($rule in $acl.Access) {
        $ruleSid = $rule.IdentityReference.Translate(
            [System.Security.Principal.SecurityIdentifier]).Value
        if ($ruleSid -ne $OwnerSid -or
            $rule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) {
            throw 'The private campaign root grants access outside the current Windows user.'
        }
    }
}

function Protect-PrivateDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    $acl = [System.Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($OwnerSid)
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    $propagation = [System.Security.AccessControl.PropagationFlags]::None
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $OwnerSid,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        $inheritance,
        $propagation,
        [System.Security.AccessControl.AccessControlType]::Allow)
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
    Assert-PrivateAcl -Path $Path -OwnerSid $OwnerSid.Value
}

function Protect-PrivateFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    $acl = [System.Security.AccessControl.FileSecurity]::new()
    $acl.SetOwner($OwnerSid)
    $acl.SetAccessRuleProtection($true, $false)
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $OwnerSid,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        [System.Security.AccessControl.AccessControlType]::Allow)
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
    Assert-PrivateAcl -Path $Path -OwnerSid $OwnerSid.Value
}

function Exit-PrivateToolsLock {
    if ($null -ne $script:toolsLock) {
        $script:toolsLock.Dispose()
        $script:toolsLock = $null
    }
}

function Invoke-WO043QualificationFailurePoint {
    param([Parameter(Mandatory = $true)][string]$Point)

    if ($script:QualificationFailurePoint -eq $Point) {
        throw "WO-043 injected client-certificate rollback qualification failure at $Point."
    }
}

function Remove-ExactReturnedClientCertificate {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $thumbprint = $Certificate.Thumbprint.ToUpperInvariant()
    $sha256 = Get-CertificateSha256 -Certificate $Certificate
    $subject = $Certificate.Subject
    if ($thumbprint -notmatch '^[0-9A-F]{40}$' -or
        $sha256 -notmatch '^[0-9a-f]{64}$' -or
        [string]::IsNullOrWhiteSpace($subject)) {
        throw 'The returned WO-036 client certificate identity is incomplete.'
    }

    $certificatePath = Join-Path 'Cert:\CurrentUser\My' $thumbprint
    $storedCertificate = Get-Item -LiteralPath $certificatePath -ErrorAction SilentlyContinue
    if ($null -eq $storedCertificate -or
        (Get-CertificateSha256 -Certificate $storedCertificate) -ne $sha256 -or
        $storedCertificate.Subject -ne $subject) {
        throw 'The returned WO-036 client certificate failed exact store ownership verification.'
    }

    # Release the CNG key handle before asking the certificate provider to
    # remove both the exact store entry and its exact private key container.
    $Certificate.Dispose()
    Remove-Item -LiteralPath $certificatePath -DeleteKey -Force
    Assert-CurrentUserCertificateRemoved -StoreName My -Thumbprint $thumbprint
}

function Assert-NotReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Path)

    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'A WO-036 private path must not be a reparse point.'
    }
}

function Assert-NoDescendantReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Root)

    foreach ($item in Get-ChildItem -LiteralPath $Root -Force -Recurse -ErrorAction Stop) {
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'The WO-036 private run root contains a descendant reparse point.'
        }
    }
}

function Resolve-TrustedDockerExecutable {
    param([string]$Override)

    $localApplicationData = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::LocalApplicationData)
    $programFiles = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::ProgramFiles)
    $trustedRoots = @(
        (Join-Path $localApplicationData 'Programs\DockerDesktop\resources\bin'),
        (Join-Path $programFiles 'Docker\Docker\resources\bin')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Override)) {
        $candidates.Add($Override)
    }
    else {
        $command = Get-Command docker.exe -ErrorAction SilentlyContinue
        if ($null -ne $command -and -not [string]::IsNullOrWhiteSpace($command.Source)) {
            $candidates.Add($command.Source)
        }
        foreach ($trustedRoot in $trustedRoots) {
            $candidates.Add((Join-Path $trustedRoot 'docker.exe'))
        }
    }
    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            continue
        }
        $resolved = (Get-Item -LiteralPath $candidate -Force).FullName
        if ([System.IO.Path]::GetFileName($resolved) -ne 'docker.exe') {
            continue
        }
        if (((Get-Item -LiteralPath $resolved -Force).Attributes -band
            [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            continue
        }
        foreach ($trustedRoot in $trustedRoots) {
            $canonicalTrustedRoot = [System.IO.Path]::GetFullPath($trustedRoot).TrimEnd(
                [System.IO.Path]::DirectorySeparatorChar,
                [System.IO.Path]::AltDirectorySeparatorChar)
            $canonicalCandidate = [System.IO.Path]::GetFullPath($resolved)
            if ($canonicalCandidate.StartsWith(
                $canonicalTrustedRoot + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
                return $canonicalCandidate
            }
        }
    }
    throw 'docker.exe was not found under an approved local Docker Desktop installation root.'
}

function Assert-ImmediateGuidChild {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Child,
        [Parameter(Mandatory = $true)][guid]$RunId
    )

    $canonicalParent = [System.IO.Path]::GetFullPath($Parent).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    $canonicalChild = [System.IO.Path]::GetFullPath($Child).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    $expectedChild = Join-Path $canonicalParent $RunId.ToString('D')
    if (-not $canonicalChild.Equals(
        $expectedChild,
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'The WO-036 run root is not the exact GUID child of the campaign base.'
    }
}

function Resolve-LocalDockerEndpoint {
    $dockerContextOverride = [Environment]::GetEnvironmentVariable('DOCKER_CONTEXT')
    $dockerHostOverride = [Environment]::GetEnvironmentVariable('DOCKER_HOST')
    if (-not [string]::IsNullOrWhiteSpace($dockerContextOverride)) {
        $endpointOutput = & $script:resolvedDockerExecutable context inspect --format '{{.Endpoints.docker.Host}}' $dockerContextOverride
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inspect the Docker context selected by DOCKER_CONTEXT.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    elseif (-not [string]::IsNullOrWhiteSpace($dockerHostOverride)) {
        if ($dockerHostOverride -cnotmatch '^npipe:////[.]/pipe/[A-Za-z0-9._-]+$') {
            throw 'DOCKER_HOST must target a local Windows named pipe.'
        }
        $endpoint = $dockerHostOverride
    }
    else {
        $endpointOutput = & $script:resolvedDockerExecutable context inspect --format '{{.Endpoints.docker.Host}}'
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inspect the selected Docker context.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    if ($endpoint -cnotmatch '^npipe:////[.]/pipe/[A-Za-z0-9._-]+$') {
        throw 'The selected Docker context must target a local Windows named pipe.'
    }
    return $endpoint
}

function Get-PinnedDockerArguments {
    if ($script:resolvedDockerEndpoint -cnotmatch
        '^npipe:////[.]/pipe/[A-Za-z0-9._-]+$') {
        throw 'The pinned WO-036 Docker endpoint is unavailable.'
    }
    return @('--host', $script:resolvedDockerEndpoint)
}

function Assert-LoopbackDatabasePortsFree {
    $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    $conflicts = @($listeners | Where-Object { $_.Port -in @(5432, 5433) })
    if ($conflicts.Count -ne 0) {
        throw 'A required WO-036 database port is already in use; no campaign database was started.'
    }
}

function Assert-DockerLabels {
    param(
        [Parameter(Mandatory = $true)][string[]]$DockerArguments,
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$ExpectedRole
    )

    $labelJson = (& $script:resolvedDockerExecutable @DockerArguments inspect --format '{{json .Config.Labels}}' $ContainerId | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($labelJson)) {
        throw 'Unable to inspect an owned WO-036 database container.'
    }
    $labels = $labelJson | ConvertFrom-Json
    if ($labels.'com.bettingproject.work-order' -ne $workOrder -or
        $labels.'com.bettingproject.wo036.run-id' -ne $script:state.RunId -or
        $labels.'com.bettingproject.wo036.ownership-sha256' -ne $script:state.OwnershipSha256 -or
        $labels.'com.bettingproject.wo036.role' -ne $ExpectedRole -or
        $labels.'com.docker.compose.project' -ne $script:state.ComposeProjectName) {
        throw 'A database container failed the exact WO-036 ownership-label check.'
    }
}

function Start-OwnedDatabases {
    param([Parameter(Mandatory = $true)][string]$EnvironmentPath)

    Assert-LoopbackDatabasePortsFree
    $dockerArguments = @(Get-PinnedDockerArguments)
    & $script:resolvedDockerExecutable @dockerArguments info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop is not ready.'
    }
    $composeArguments = @(
        'compose',
        '--project-name', $script:state.ComposeProjectName,
        '--file', $composePath,
        '--env-file', $EnvironmentPath
    )
    & $script:resolvedDockerExecutable @dockerArguments @composeArguments config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw 'The isolated WO-036 Compose model is invalid.'
    }
    $script:state.dockerResourcesMayExist = $true
    Write-PrivateState
    & $script:resolvedDockerExecutable @dockerArguments @composeArguments up --detach local-lab-postgres betting-project-postgres
    if ($LASTEXITCODE -ne 0) {
        throw 'The isolated WO-036 PostgreSQL services failed to start.'
    }

    $ownedContainers = @()
    foreach ($service in @(
        [pscustomobject]@{ Name = 'local-lab-postgres'; Role = 'local-lab-postgres'; Port = '5432/tcp'; Binding = '127.0.0.1:5432' },
        [pscustomobject]@{ Name = 'betting-project-postgres'; Role = 'betting-project-postgres'; Port = '5432/tcp'; Binding = '127.0.0.1:5433' }
    )) {
        $containerId = (& $script:resolvedDockerExecutable @dockerArguments @composeArguments ps --quiet $service.Name | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $containerId -notmatch '^[0-9a-f]{12,64}$') {
            throw 'Compose did not return one exact WO-036 database container.'
        }
        Assert-DockerLabels -DockerArguments $dockerArguments -ContainerId $containerId -ExpectedRole $service.Role
        $restartPolicy = (& $script:resolvedDockerExecutable @dockerArguments inspect --format '{{.HostConfig.RestartPolicy.Name}}' $containerId | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $restartPolicy -ne 'no') {
            throw 'An owned WO-036 database container has a non-disabled restart policy.'
        }
        $binding = (& $script:resolvedDockerExecutable @dockerArguments port $containerId $service.Port | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $binding -ne $service.Binding) {
            throw 'An owned WO-036 database container is not bound to its exact loopback port.'
        }
        $ownedContainers += [pscustomobject]@{
            Service = $service.Name
            Role = $service.Role
            ContainerId = $containerId
        }
    }

    foreach ($container in $ownedContainers) {
        $healthy = $false
        for ($attempt = 1; $attempt -le 40; $attempt++) {
            $health = (& $script:resolvedDockerExecutable @dockerArguments inspect --format '{{.State.Health.Status}}' $container.ContainerId | Out-String).Trim()
            if ($LASTEXITCODE -ne 0) {
                throw 'Unable to read an owned WO-036 database health status.'
            }
            if ($health -eq 'healthy') {
                $healthy = $true
                break
            }
            if ($health -eq 'unhealthy') {
                throw 'An owned WO-036 database became unhealthy.'
            }
            Start-Sleep -Seconds 2
        }
        if (-not $healthy) {
            throw 'An owned WO-036 database did not become healthy within the bounded wait.'
        }
    }

    $script:state.OwnedContainers = @($ownedContainers)
    foreach ($container in $ownedContainers) {
        if ($container.Role -eq 'local-lab-postgres') {
            $script:state.localLab.databaseContainer = $container.ContainerId
        }
        elseif ($container.Role -eq 'betting-project-postgres') {
            $script:state.receiver.databaseContainer = $container.ContainerId
        }
    }
    $script:state.DatabasesStarted = $true
    $script:state.DatabasesHealthy = $true
    Write-PrivateState
}

if (-not $IsWindows) {
    throw 'WO-036 infrastructure initialization is supported only on Windows.'
}
if ($StartDatabases -and $QualificationFailurePoint -ne 'NONE') {
    throw 'WO-043 failure injection cannot be combined with database startup.'
}
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $cleanupPath -PathType Leaf)) {
    throw 'The versioned WO-036 infrastructure files are incomplete.'
}
Assert-Command -Name 'keytool.exe'
Assert-Command -Name 'New-SelfSignedCertificate'
Assert-Command -Name 'Import-Certificate'
Assert-Command -Name 'Export-Certificate'
$script:resolvedDockerExecutable = Resolve-TrustedDockerExecutable -Override $DockerExecutablePath
$script:resolvedDockerEndpoint = Resolve-LocalDockerEndpoint

$currentIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$ownerSid = $currentIdentity.User
if ($null -eq $ownerSid) {
    throw 'The current Windows user SID is unavailable.'
}
$localApplicationData = [Environment]::GetFolderPath(
    [Environment+SpecialFolder]::LocalApplicationData)
if ([string]::IsNullOrWhiteSpace($localApplicationData)) {
    throw 'The current Windows LocalApplicationData directory is unavailable.'
}

$qualificationBase = Join-Path $localApplicationData 'SofaScoreLocalLab\qualifications'
$campaignBase = Join-Path $qualificationBase 'WO-SS-20260902-036'
New-Item -ItemType Directory -Path $campaignBase -Force | Out-Null
$canonicalCampaignBase = (Get-Item -LiteralPath $campaignBase -Force).FullName
Assert-NotReparsePoint -Path $canonicalCampaignBase

$runId = [guid]::NewGuid()
try {
$script:phase = 'PRIVATE_ROOT'
$script:runRoot = Join-Path $canonicalCampaignBase $runId.ToString('D')
New-Item -ItemType Directory -Path $script:runRoot | Out-Null
$script:runRoot = (Get-Item -LiteralPath $script:runRoot -Force).FullName
Assert-ImmediateGuidChild -Parent $canonicalCampaignBase -Child $script:runRoot -RunId $runId
Assert-NotReparsePoint -Path $script:runRoot
$script:phase = 'PRIVATE_ACL'
Protect-PrivateDirectory -Path $script:runRoot -OwnerSid $ownerSid

$script:phase = 'PRIVATE_LAYOUT'
$script:statePath = Join-Path $script:runRoot 'state.private.json'
$markerPath = Join-Path $script:runRoot '.wo036-owner.json'
$toolsLockPath = Join-Path $script:runRoot '.wo036-tools.lock'
$dockerEndpointPath = Join-Path $script:runRoot 'docker-endpoint.private.txt'
$environmentPath = Join-Path $script:runRoot 'campaign.private.env'
$pkiRoot = Join-Path $script:runRoot 'pki'
$exportRoot = Join-Path $script:runRoot 'exports'
New-Item -ItemType Directory -Path $pkiRoot | Out-Null
New-Item -ItemType Directory -Path $exportRoot | Out-Null
Assert-NotReparsePoint -Path $pkiRoot
Assert-NotReparsePoint -Path $exportRoot

$script:phase = 'PRIVATE_TOOLS_LOCK'
$script:toolsLock = [System.IO.FileStream]::new(
    $toolsLockPath,
    [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::ReadWrite,
    [System.IO.FileShare]::None)
$script:toolsLock.Flush($true)
Protect-PrivateFile -Path $toolsLockPath -OwnerSid $ownerSid
Assert-NotReparsePoint -Path $toolsLockPath
Assert-PrivateAcl -Path $toolsLockPath -OwnerSid $ownerSid.Value

$script:phase = 'PRIVATE_DOCKER_ENDPOINT'
Write-Utf8NoBomFile -Path $dockerEndpointPath -Content ($script:resolvedDockerEndpoint + "`n")
Protect-PrivateFile -Path $dockerEndpointPath -OwnerSid $ownerSid
Assert-NotReparsePoint -Path $dockerEndpointPath
Assert-PrivateAcl -Path $dockerEndpointPath -OwnerSid $ownerSid.Value
if ([System.IO.File]::ReadAllText(
        $dockerEndpointPath,
        [System.Text.UTF8Encoding]::new($false, $true)) -cne
    ($script:resolvedDockerEndpoint + "`n")) {
    throw 'The pinned WO-036 Docker endpoint file is not byte-exact UTF-8 LF text.'
}

$ownershipNonce = New-RandomUrlSafeValue
$ownershipSha256 = Get-Sha256Text -Value $ownershipNonce
$composeProjectName = 'wo036' + $runId.ToString('N')
$volumeNames = @(
    "${composeProjectName}_local_lab_postgres_data",
    "${composeProjectName}_betting_project_postgres_data"
)
$createdAt = [DateTimeOffset]::UtcNow.ToString('O')
$serverStorePath = Join-Path $pkiRoot 'receiver-server.p12'
$serverPublicPath = Join-Path $pkiRoot 'receiver-server.cer'
$receiverTrustStorePath = Join-Path $pkiRoot 'receiver-client-truststore.p12'
$clientPublicPath = Join-Path $pkiRoot 'sender-client.cer'
$serverPassword = New-RandomUrlSafeValue -ByteCount 48
$trustStorePassword = New-RandomUrlSafeValue -ByteCount 48
$localLabDatabasePassword = New-RandomUrlSafeValue -ByteCount 48
$bettingProjectDatabasePassword = New-RandomUrlSafeValue -ByteCount 48
$localLabSourceDatabase = 'sofascore_local_lab_wo036_a'
$localLabTargetDatabase = 'sofascore_local_lab_wo036_b'

$script:phase = 'PRIVATE_STATE'
$script:state = [pscustomobject]@{
    schemaVersion = 1
    campaignId = 'wo036-' + $runId.ToString('D')
    workOrder = $workOrder
    runId = $runId.ToString('D')
    ownerSid = $ownerSid.Value
    ownershipSha256 = $ownershipSha256
    repositoryRoot = $repositoryRoot
    runRoot = $script:runRoot
    markerPath = $markerPath
    environmentPath = $environmentPath
    composePath = $composePath
    composeProjectName = $composeProjectName
    createdAtUtc = $createdAt
    initializationStatus = 'IN_PROGRESS'
    databasesStarted = $false
    databasesHealthy = $false
    dockerResourcesMayExist = $false
    dockerExecutable = $script:resolvedDockerExecutable
    dockerExecutableSha256 = $(if ([string]::IsNullOrWhiteSpace($script:resolvedDockerExecutable)) {
        $null
    }
    else {
        (Get-FileHash -LiteralPath $script:resolvedDockerExecutable -Algorithm SHA256).Hash.ToLowerInvariant()
    })
    javaExecutable = $null
    localLabJar = $null
    localLabJarSha256 = $null
    receiverJar = $null
    receiverJarSha256 = $null
    receiver = [pscustomobject]@{
        containerRole = 'betting-project-postgres'
        databaseContainer = $null
        databasePort = 5433
        databaseName = 'betting_wo036'
        databaseUser = 'betting_wo036'
        databasePassword = $bettingProjectDatabasePassword
        serverPort = 8444
        keyStorePath = $serverStorePath
        keyStorePassword = $serverPassword
        trustStorePath = $receiverTrustStorePath
        trustStorePassword = $trustStorePassword
        clientCertificateSha256 = $null
    }
    localLab = [pscustomobject]@{
        containerRole = 'local-lab-postgres'
        databaseContainer = $null
        databasePort = 5432
        databaseUser = 'sofascore_lab_wo036'
        databasePassword = $localLabDatabasePassword
        sourceDatabase = $localLabSourceDatabase
        targetDatabase = $localLabTargetDatabase
        serverPort = 8087
        exportDirectory = $exportRoot
        clientCertificateSha256 = $null
    }
    ownedContainers = @()
    ownedVolumes = $volumeNames
    ownedCertificates = @()
    ownedProcesses = @()
    pki = [pscustomobject]@{
        serverKeyStore = $serverStorePath
        receiverTrustStore = $receiverTrustStorePath
        serverCertificatePublic = $serverPublicPath
        clientCertificatePublic = $clientPublicPath
        clientCertificateSha256 = $null
    }
    cleanupStatus = 'NOT_STARTED'
}

$script:phase = 'PRIVATE_MARKER_OBJECT'
$marker = [pscustomobject]@{
    schemaVersion = 1
    workOrder = $workOrder
    runId = $script:state.RunId
    ownerSid = $script:state.OwnerSid
    ownershipSha256 = $script:state.OwnershipSha256
    composeProjectName = $script:state.ComposeProjectName
    createdAtUtc = $createdAt
}
$script:phase = 'PRIVATE_MARKER_WRITE'
Write-Utf8NoBomFile -Path $markerPath -Content (($marker | ConvertTo-Json -Depth 4) + [Environment]::NewLine)
$script:phase = 'PRIVATE_STATE_WRITE'
Write-PrivateState

    $script:phase = 'SERVER_CERTIFICATE'
    $serverAlias = 'wo036-server-' + $runId.ToString('N')
    $previousServerPassword = [Environment]::GetEnvironmentVariable(
        'WO036_KEYTOOL_SERVER_PASSWORD', 'Process')
    try {
        $env:WO036_KEYTOOL_SERVER_PASSWORD = $serverPassword
        & keytool.exe -genkeypair -noprompt `
            -alias $serverAlias `
            -dname "CN=127.0.0.1,OU=WO-036,O=Betting Project Local Qualification" `
            -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 7 `
            -ext 'BC=ca:false' `
            -ext 'KU=digitalSignature,keyEncipherment' `
            -ext 'EKU=serverAuth' `
            -ext 'SAN=ip:127.0.0.1' `
            -storetype PKCS12 -keystore $serverStorePath `
            -storepass:env WO036_KEYTOOL_SERVER_PASSWORD `
            -keypass:env WO036_KEYTOOL_SERVER_PASSWORD *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'keytool failed to create the WO-036 receiver server key store.'
        }
        & keytool.exe -exportcert -noprompt `
            -alias $serverAlias -keystore $serverStorePath `
            -storetype PKCS12 -storepass:env WO036_KEYTOOL_SERVER_PASSWORD `
            -file $serverPublicPath *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'keytool failed to export the WO-036 receiver server certificate.'
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'WO036_KEYTOOL_SERVER_PASSWORD', $previousServerPassword, 'Process')
    }

    $serverCertificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(
        $serverPublicPath)
    try {
        if ($serverCertificate.Subject -ne
            'CN=127.0.0.1, OU=WO-036, O=Betting Project Local Qualification' -or
            $serverCertificate.Issuer -ne $serverCertificate.Subject -or
            $serverCertificate.HasPrivateKey) {
            throw 'The exported WO-036 receiver certificate identity is invalid.'
        }
        $serverEku = @($serverCertificate.Extensions |
            Where-Object { $_ -is [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension] })
        if ($serverEku.Count -ne 1 -or
            -not ($serverEku[0].EnhancedKeyUsages.Value -contains '1.3.6.1.5.5.7.3.1')) {
            throw 'The WO-036 receiver certificate is missing serverAuth EKU.'
        }
        $serverKeyUsage = @($serverCertificate.Extensions |
            Where-Object { $_ -is [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension] })
        $requiredServerKeyUsage =
            [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor
            [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment
        if ($serverKeyUsage.Count -ne 1 -or
            ($serverKeyUsage[0].KeyUsages -band $requiredServerKeyUsage) -ne $requiredServerKeyUsage) {
            throw 'The WO-036 receiver certificate has invalid key usage.'
        }
        $serverBasicConstraints = @($serverCertificate.Extensions |
            Where-Object { $_ -is [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension] })
        if ($serverBasicConstraints.Count -ne 1 -or
            $serverBasicConstraints[0].CertificateAuthority) {
            throw 'The WO-036 receiver certificate must not be a CA certificate.'
        }
        $serverSan = @($serverCertificate.Extensions |
            Where-Object { $_.Oid.Value -eq '2.5.29.17' })
        if ($serverSan.Count -ne 1) {
            throw 'The WO-036 receiver certificate SAN is not the exact IPv4 loopback address.'
        }
        $parsedServerSan = [System.Security.Cryptography.X509Certificates.X509SubjectAlternativeNameExtension]::new(
            $serverSan[0].RawData,
            $serverSan[0].Critical)
        $serverIpAddresses = @($parsedServerSan.EnumerateIPAddresses())
        if ($serverIpAddresses.Count -ne 1 -or
            -not $serverIpAddresses[0].Equals([System.Net.IPAddress]::Loopback)) {
            throw 'The WO-036 receiver certificate SAN is not the exact IPv4 loopback address.'
        }
        $serverCertificateSha256 = Get-CertificateSha256 -Certificate $serverCertificate
        $importedServerCertificate = Import-Certificate `
            -FilePath $serverPublicPath `
            -CertStoreLocation 'Cert:\CurrentUser\Root'
        if ($null -eq $importedServerCertificate) {
            throw 'The WO-036 server certificate was not imported into CurrentUser Root.'
        }
        $serverRootCertificateThumbprint = $importedServerCertificate.Thumbprint.ToUpperInvariant()
        $script:serverRootCertificateThumbprint = $serverRootCertificateThumbprint
        $script:state.OwnedCertificates = @($script:state.OwnedCertificates) + [pscustomobject]@{
            Role = 'receiver-server-trust-root'
            StoreLocation = 'CurrentUser\Root'
            Thumbprint = $serverRootCertificateThumbprint
            Sha256 = $serverCertificateSha256
            Subject = $importedServerCertificate.Subject
        }
        Write-PrivateState
    }
    finally {
        $serverCertificate.Dispose()
    }

    $script:phase = 'CLIENT_CERTIFICATE'
    $clientSubject = "CN=WO036 sender $($runId.ToString('D')),OU=WO-036,O=Betting Project Local Qualification"
    $script:clientCertificate = New-SelfSignedCertificate `
        -Type Custom `
        -Subject $clientSubject `
        -CertStoreLocation 'Cert:\CurrentUser\My' `
        -KeyAlgorithm RSA `
        -KeyLength 3072 `
        -HashAlgorithm SHA256 `
        -KeyExportPolicy NonExportable `
        -KeySpec Signature `
        -KeyUsage DigitalSignature `
        -TextExtension @(
            '2.5.29.19={critical}{text}CA=false',
            '2.5.29.37={critical}{text}1.3.6.1.5.5.7.3.2') `
        -NotAfter ([DateTime]::UtcNow.AddDays(7))
    if ($null -eq $script:clientCertificate) {
        throw 'The WO-036 sender client certificate was not created.'
    }
    try {
        Invoke-WO043QualificationFailurePoint `
            -Point 'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP'
        $script:clientCertificateThumbprint = $script:clientCertificate.Thumbprint.ToUpperInvariant()
        $clientCertificateSha256 = Get-CertificateSha256 -Certificate $script:clientCertificate
        $script:state.OwnedCertificates = @($script:state.OwnedCertificates) + [pscustomobject]@{
            Role = 'sender-client'
            StoreLocation = 'CurrentUser\My'
            Thumbprint = $script:clientCertificateThumbprint
            Sha256 = $clientCertificateSha256
            Subject = $script:clientCertificate.Subject
        }
    }
    catch {
        $preRegistrationFailure = $_
        try {
            Remove-ExactReturnedClientCertificate -Certificate $script:clientCertificate
            $script:preRegistrationClientCleanupStatus = 'PASS'
        }
        catch {
            $script:preRegistrationClientCleanupStatus = 'FAILED'
        }
        finally {
            $script:clientCertificate.Dispose()
            $script:clientCertificate = $null
        }
        throw $preRegistrationFailure
    }
    Invoke-WO043QualificationFailurePoint -Point 'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY'
    Write-PrivateState
    Invoke-WO043QualificationFailurePoint -Point 'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED'
    if (-not $script:clientCertificate.HasPrivateKey) {
        throw 'The WO-036 sender client certificate does not own a private key.'
    }
    $clientEku = $script:clientCertificate.Extensions |
        Where-Object { $_ -is [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension] }
    if ($null -eq $clientEku -or
        -not ($clientEku.EnhancedKeyUsages.Value -contains '1.3.6.1.5.5.7.3.2')) {
        throw 'The WO-036 sender client certificate is missing clientAuth EKU.'
    }
    $clientKeyUsage = $script:clientCertificate.Extensions |
        Where-Object { $_ -is [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension] }
    if ($null -eq $clientKeyUsage -or
        ($clientKeyUsage.KeyUsages -band
            [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature) -eq 0) {
        throw 'The WO-036 sender client certificate is missing digitalSignature key usage.'
    }
    $clientPrivateKey = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey(
        $script:clientCertificate)
    try {
        if ($clientPrivateKey -isnot [System.Security.Cryptography.RSACng] -or
            $clientPrivateKey.Key.ExportPolicy -ne [System.Security.Cryptography.CngExportPolicies]::None) {
            throw 'The WO-036 sender client private key is not a non-exportable Windows CNG key.'
        }
    }
    finally {
        if ($null -ne $clientPrivateKey) {
            $clientPrivateKey.Dispose()
        }
    }
    Invoke-WO043QualificationFailurePoint -Point 'BEFORE_CLIENT_CERTIFICATE_EXPORT'
    Export-Certificate -Cert $script:clientCertificate -FilePath $clientPublicPath -Type CERT | Out-Null

    $script:phase = 'RECEIVER_TRUST_STORE'
    $previousTrustStorePassword = [Environment]::GetEnvironmentVariable(
        'WO036_KEYTOOL_TRUSTSTORE_PASSWORD', 'Process')
    try {
        $env:WO036_KEYTOOL_TRUSTSTORE_PASSWORD = $trustStorePassword
        & keytool.exe -importcert -noprompt `
            -alias ('wo036-client-' + $runId.ToString('N')) `
            -file $clientPublicPath `
            -keystore $receiverTrustStorePath `
            -storetype PKCS12 `
            -storepass:env WO036_KEYTOOL_TRUSTSTORE_PASSWORD *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'keytool failed to create the WO-036 receiver client trust store.'
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'WO036_KEYTOOL_TRUSTSTORE_PASSWORD', $previousTrustStorePassword, 'Process')
    }

    $script:state.Pki = [pscustomobject]@{
        ServerKeyStore = $serverStorePath
        ReceiverTrustStore = $receiverTrustStorePath
        ServerCertificatePublic = $serverPublicPath
        ClientCertificatePublic = $clientPublicPath
        ClientCertificateSha256 = $clientCertificateSha256
    }
    $script:state.receiver.clientCertificateSha256 = $clientCertificateSha256
    $script:state.localLab.clientCertificateSha256 = $clientCertificateSha256
    Write-PrivateState
    $script:clientCertificate.Dispose()
    $script:clientCertificate = $null

    $script:phase = 'PRIVATE_ENVIRONMENT'
    $environmentLines = @(
        "WO036_WORK_ORDER=$workOrder",
        "WO036_RUN_ID=$($script:state.RunId)",
        "WO036_OWNERSHIP_SHA256=$ownershipSha256",
        "WO036_COMPOSE_PROJECT_NAME=$composeProjectName",
        "WO036_LOCAL_LAB_POSTGRES_DB=$localLabSourceDatabase",
        'WO036_LOCAL_LAB_POSTGRES_USER=sofascore_lab_wo036',
        "WO036_LOCAL_LAB_POSTGRES_PASSWORD=$localLabDatabasePassword",
        "WO036_LOCAL_LAB_TARGET_POSTGRES_DB=$localLabTargetDatabase",
        'WO036_BETTING_PROJECT_POSTGRES_DB=betting_wo036',
        'WO036_BETTING_PROJECT_POSTGRES_USER=betting_wo036',
        "WO036_BETTING_PROJECT_POSTGRES_PASSWORD=$bettingProjectDatabasePassword",
        "POSTGRES_DB=$localLabSourceDatabase",
        'POSTGRES_USER=sofascore_lab_wo036',
        "POSTGRES_PASSWORD=$localLabDatabasePassword",
        'POSTGRES_PORT=5432',
        'BETTING_DB_NAME=betting_wo036',
        'BETTING_DB_USER=betting_wo036',
        "BETTING_DB_PASSWORD=$bettingProjectDatabasePassword",
        'BETTING_DB_PORT=5433',
        'BETTING_DB_URL=jdbc:postgresql://127.0.0.1:5433/betting_wo036',
        'BETTING_OPERATOR_ID=wo036-codex-local-ui',
        'BETTING_J7_RECEIVER_ENABLED=true',
        'BETTING_J7_RETENTION_DAYS=30',
        "BETTING_J7_CLIENT_CERTIFICATE_SHA256_ALLOWLIST=$clientCertificateSha256",
        'BETTING_CONTROL_API_PORT=8444',
        ('BETTING_J7_KEY_STORE=' + $serverStorePath.Replace('\', '/')),
        "BETTING_J7_KEY_STORE_PASSWORD=$serverPassword",
        'BETTING_J7_KEY_STORE_TYPE=PKCS12',
        ('BETTING_J7_TRUST_STORE=' + $receiverTrustStorePath.Replace('\', '/')),
        "BETTING_J7_TRUST_STORE_PASSWORD=$trustStorePassword",
        'BETTING_J7_TRUST_STORE_TYPE=PKCS12',
        'OPTIONAL_INTEGRATION_ENABLED=true',
        'OPTIONAL_INTEGRATION_EXECUTION_MODE=SYNTHETIC_LOOPBACK',
        'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED=false',
        'OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED',
        'OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION=PASS',
        'OPTIONAL_INTEGRATION_SENDER_QUALIFICATION=PASS',
        'OPTIONAL_INTEGRATION_RECEIVER_ORIGIN=https://127.0.0.1:8444',
        'OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION=true',
        'OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN=https://127.0.0.1:8444',
        'OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED=false',
        "OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256=$clientCertificateSha256",
        'SOFASCORE_ENABLED=false',
        'SOFASCORE_PLAYWRIGHT_ENABLED=false',
        'SOFASCORE_J3_QUALIFICATION_ENABLED=false',
        'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false',
        'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false',
        'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false',
        'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=false',
        'SOFASCORE_BASE_URL=',
        'SOFASCORE_ALLOWED_ENDPOINTS=',
        ('SOFASCORE_EXPORT_DIR=' + $exportRoot.Replace('\', '/'))
    )
    Write-Utf8NoBomFile -Path $environmentPath -Content (
        ($environmentLines -join [Environment]::NewLine) + [Environment]::NewLine)
    Assert-PrivateAcl -Path $script:runRoot -OwnerSid $ownerSid.Value

    $script:state.InitializationStatus = 'PREPARED'
    Write-PrivateState
    if ($StartDatabases) {
        $script:phase = 'DATABASE_START'
        Start-OwnedDatabases -EnvironmentPath $environmentPath
    }
    $script:state.InitializationStatus = 'PASS'
    Write-PrivateState

    Write-Output 'WO036_INFRASTRUCTURE_INITIALIZATION=PASS'
    Write-Output "WO036_RUN_ID=$($script:state.RunId)"
    Write-Output "WO036_PRIVATE_STATE=$script:statePath"
    Write-Output "WO036_DATABASES_STARTED=$(if ($script:state.DatabasesStarted) { 'YES' } else { 'NO' })"
    Write-Output 'WO036_SECRETS_DISPLAYED=NO'
    Write-Output 'WO036_PROVIDER_NETWORK_OPENED=NO'
    Exit-PrivateToolsLock
}
catch {
    $failureType = $_.Exception.GetType().Name
    $failureLine = $_.InvocationInfo.ScriptLineNumber
    $failureParameter = if ($_.Exception -is [System.Management.Automation.ParameterBindingException]) {
        $_.Exception.ParameterName
    }
    else {
        ''
    }
    if ($null -ne $script:clientCertificate) {
        $script:clientCertificate.Dispose()
        $script:clientCertificate = $null
    }
    $rollback = 'NOT_ATTEMPTED'
    try {
        if (-not [string]::IsNullOrWhiteSpace($script:statePath) -and
            (Test-Path -LiteralPath $script:statePath -PathType Leaf)) {
            if ($null -ne $script:toolsLock -and $null -ne $script:state) {
                $script:state.cleanupStatus = 'IN_PROGRESS'
                Write-PrivateState
            }
            Exit-PrivateToolsLock
            & $cleanupPath -StatePath $script:statePath -Confirm:$false
            $rollback = 'PASS'
        }
        elseif (-not [string]::IsNullOrWhiteSpace($script:runRoot) -and
            (Test-Path -LiteralPath $script:runRoot -PathType Container)) {
            $candidateRoot = (Get-Item -LiteralPath $script:runRoot -Force).FullName
            Assert-ImmediateGuidChild `
                -Parent $canonicalCampaignBase `
                -Child $candidateRoot `
                -RunId $runId
            Assert-NotReparsePoint -Path $candidateRoot
            Assert-NoDescendantReparsePoint -Root $candidateRoot
            Exit-PrivateToolsLock
            Remove-Item -LiteralPath $candidateRoot -Recurse -Force
            $rollback = 'PASS_PRE_STATE_ROOT_ONLY'
        }
        else {
            Exit-PrivateToolsLock
        }
    }
    catch {
        Exit-PrivateToolsLock
        $rollback = 'FAILED_PRIVATE_STATE_RETAINED'
    }
    if ($null -ne $script:state) {
        foreach ($ownedCertificate in @($script:state.OwnedCertificates)) {
            try {
                $certificateStore = if ($ownedCertificate.StoreLocation -eq 'CurrentUser\Root') {
                    'Cert:\CurrentUser\Root'
                }
                elseif ($ownedCertificate.StoreLocation -eq 'CurrentUser\My') {
                    'Cert:\CurrentUser\My'
                }
                else {
                    $null
                }
                if ($null -ne $certificateStore -and
                    $ownedCertificate.Thumbprint -match '^[0-9A-F]{40}$' -and
                    $ownedCertificate.Sha256 -match '^[0-9a-f]{64}$') {
                    $certificatePath = Join-Path $certificateStore $ownedCertificate.Thumbprint
                    $certificate = Get-Item -LiteralPath $certificatePath -ErrorAction SilentlyContinue
                    if ($null -ne $certificate -and
                        (Get-CertificateSha256 -Certificate $certificate) -eq $ownedCertificate.Sha256 -and
                        $certificate.Subject -eq $ownedCertificate.Subject) {
                        if ($ownedCertificate.StoreLocation -eq 'CurrentUser\Root') {
                            $certutilPath = Join-Path ([Environment]::SystemDirectory) 'certutil.exe'
                            if (-not (Test-Path -LiteralPath $certutilPath -PathType Leaf)) {
                                throw 'certutil.exe is unavailable for exact CurrentUser Root rollback.'
                            }
                            & $certutilPath -user -delstore Root $ownedCertificate.Thumbprint *> $null
                            if ($LASTEXITCODE -ne 0) {
                                throw 'Exact CurrentUser Root rollback failed.'
                            }
                            Assert-CurrentUserCertificateRemoved `
                                -StoreName Root `
                                -Thumbprint $ownedCertificate.Thumbprint
                        }
                        else {
                            Remove-Item -LiteralPath $certificatePath -DeleteKey -Force
                            Assert-CurrentUserCertificateRemoved `
                                -StoreName My `
                                -Thumbprint $ownedCertificate.Thumbprint
                        }
                    }
                }
            }
            catch {
                $rollback = 'FAILED_PRIVATE_CERTIFICATE_RETAINED'
            }
        }
    }
    if ($script:preRegistrationClientCleanupStatus -eq 'FAILED') {
        $rollback = 'FAILED_PRIVATE_CERTIFICATE_RETAINED'
    }
    throw "WO-036 initialization failed closed during $script:phase ($failureType, line=$failureLine, parameter=$failureParameter); rollback=$rollback."
}
