[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$StatePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$workOrder = 'WO-SS-20260902-036-j9-j7-local-e2e-qualification'
$repositoryRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$expectedComposePath = [System.IO.Path]::GetFullPath(
    (Join-Path $repositoryRoot 'compose.wo036.yml'))
$errors = [System.Collections.Generic.List[string]]::new()
$script:dockerExecutable = $null
$script:toolsLock = $null

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

function Assert-ExactPath {
    param(
        [Parameter(Mandatory = $true)][string]$Actual,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Failure
    )

    $canonicalActual = [System.IO.Path]::GetFullPath($Actual).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    $canonicalExpected = [System.IO.Path]::GetFullPath($Expected).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar)
    if (-not $canonicalActual.Equals(
        $canonicalExpected,
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw $Failure
    }
}

function Assert-NotReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Path)

    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'A WO-036 cleanup path must not be a reparse point.'
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

function Assert-PrivateAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerSid,
        [switch]$RequireProtected
    )

    $acl = Get-Acl -LiteralPath $Path
    $actualOwnerSid = ([System.Security.Principal.NTAccount]$acl.Owner).Translate(
        [System.Security.Principal.SecurityIdentifier]).Value
    if ($actualOwnerSid -ne $OwnerSid -or
        ($RequireProtected -and -not $acl.AreAccessRulesProtected)) {
        throw 'A WO-036 private cleanup path has an invalid owner or inheritance state.'
    }
    foreach ($rule in $acl.Access) {
        $ruleSid = $rule.IdentityReference.Translate(
            [System.Security.Principal.SecurityIdentifier]).Value
        if ($ruleSid -ne $OwnerSid -or
            $rule.AccessControlType -ne
                [System.Security.AccessControl.AccessControlType]::Allow) {
            throw 'A WO-036 private cleanup path grants access outside its exact owner.'
        }
    }
}

function Write-PrivateStateAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$OwnerSid
    )

    $partialPath = "$Path.cleanup.partial"
    if (Test-Path -LiteralPath $partialPath) {
        throw 'A residual WO-036 cleanup state partial blocks mutation.'
    }
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes(
        (($State | ConvertTo-Json -Depth 12) + [Environment]::NewLine))
    try {
        $stream = [System.IO.FileStream]::new(
            $partialPath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None)
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
        Assert-NotReparsePoint -Path $partialPath
        Assert-PrivateAcl -Path $partialPath -OwnerSid $OwnerSid
        Move-Item -LiteralPath $partialPath -Destination $Path -Force
        Assert-NotReparsePoint -Path $Path
        Assert-PrivateAcl -Path $Path -OwnerSid $OwnerSid
    }
    catch {
        if (Test-Path -LiteralPath $partialPath -PathType Leaf) {
            Remove-Item -LiteralPath $partialPath -Force
        }
        throw 'The WO-036 cleanup state transition could not be persisted atomically.'
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Get-ExactOwnedProcessResidualCount {
    param([Parameter(Mandatory = $true)]$State)

    $count = 0
    foreach ($ownedProcess in @($State.OwnedProcesses)) {
        $processId = [int]$ownedProcess.Pid
        $live = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -eq $live) {
            continue
        }
        try {
            $cimProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" `
                -ErrorAction Stop
        }
        catch {
            throw 'WO-036 exact owned-process CIM postcheck failed.'
        }
        if ($null -eq $cimProcess) {
            if ($null -eq (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
                continue
            }
            throw 'WO-036 exact owned-process CIM postcheck returned no live identity.'
        }
        if ([string]::IsNullOrWhiteSpace([string]$cimProcess.ExecutablePath)) {
            throw 'WO-036 exact owned-process executable postcheck is unavailable.'
        }
        $expectedStartTime = [DateTimeOffset]::Parse(
            $ownedProcess.StartTimeUtc,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind).UtcDateTime
        $commandLine = if ($null -eq $cimProcess.CommandLine) {
            ''
        }
        else {
            [string]$cimProcess.CommandLine
        }
        if (-not ([System.IO.Path]::GetFullPath([string]$cimProcess.ExecutablePath).Equals(
                [System.IO.Path]::GetFullPath([string]$ownedProcess.ExecutablePath),
                [System.StringComparison]::OrdinalIgnoreCase)) -or
            (Get-Sha256Text -Value $commandLine) -ne $ownedProcess.CommandLineSha256 -or
            [Math]::Abs((
                $live.StartTime.ToUniversalTime() - $expectedStartTime).TotalSeconds) -gt 1) {
            throw 'WO-036 exact owned-process identity changed during the zero-residue postcheck.'
        }
        $count++
    }
    return $count
}

function Get-ListenerResidualProof {
    $ports = @(8087, 8444, 5432, 5433)
    try {
        $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
    }
    catch {
        throw 'WO-036 listener cleanup postconditions could not be enumerated.'
    }
    $proof = [ordered]@{}
    foreach ($port in $ports) {
        $proof[[string]$port] = @($listeners | Where-Object {
            [int]$_.LocalPort -eq $port
        }).Count
    }
    return [pscustomobject]$proof
}

function Get-OwnedCertificateResidualCount {
    param([Parameter(Mandatory = $true)]$State)

    $count = 0
    foreach ($ownedCertificate in @($State.OwnedCertificates)) {
        $storeName = if ($ownedCertificate.StoreLocation -eq 'CurrentUser\Root') {
            'Root'
        }
        elseif ($ownedCertificate.StoreLocation -eq 'CurrentUser\My') {
            'My'
        }
        else {
            throw 'An owned certificate has an unexpected WO-036 store during postcheck.'
        }
        if (Test-CurrentUserCertificateExists -StoreName $storeName `
            -Thumbprint $ownedCertificate.Thumbprint) {
            $count++
        }
    }
    return $count
}

function Assert-TrustedDockerExecutablePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $localApplicationData = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::LocalApplicationData)
    $programFiles = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::ProgramFiles)
    $trustedRoots = @(
        (Join-Path $localApplicationData 'Programs\DockerDesktop\resources\bin'),
        (Join-Path $programFiles 'Docker\Docker\resources\bin')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $canonicalPath = [System.IO.Path]::GetFullPath($Path)
    if ([System.IO.Path]::GetFileName($canonicalPath) -ne 'docker.exe') {
        throw 'The recorded Docker executable does not have the exact expected filename.'
    }
    foreach ($trustedRoot in $trustedRoots) {
        $canonicalTrustedRoot = [System.IO.Path]::GetFullPath($trustedRoot).TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar,
            [System.IO.Path]::AltDirectorySeparatorChar)
        if ($canonicalPath.StartsWith(
            $canonicalTrustedRoot + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase)) {
            return
        }
    }
    throw 'The recorded Docker executable is outside approved Docker Desktop installation roots.'
}

function Get-PinnedDockerArguments {
    param([Parameter(Mandatory = $true)]$State)

    $endpointPath = Join-Path $State.RunRoot 'docker-endpoint.private.txt'
    $resolvedEndpointPath = (Resolve-Path -LiteralPath $endpointPath).Path
    Assert-ExactPath -Actual $resolvedEndpointPath -Expected $endpointPath `
        -Failure 'The pinned WO-036 Docker endpoint path is not exact.'
    Assert-NotReparsePoint -Path $resolvedEndpointPath
    Assert-PrivateAcl -Path $resolvedEndpointPath -OwnerSid $State.OwnerSid -RequireProtected
    $bytes = [System.IO.File]::ReadAllBytes($resolvedEndpointPath)
    try {
        if ($bytes.Length -lt 20 -or $bytes.Length -gt 256 -or
            ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and
                $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)) {
            throw 'The pinned WO-036 Docker endpoint file has an invalid byte shape.'
        }
        $text = [System.Text.UTF8Encoding]::new($false, $true).GetString($bytes)
        if ($text -cnotmatch '\A(npipe:////[.]/pipe/[A-Za-z0-9._-]+)\n\z') {
            throw 'The pinned WO-036 Docker endpoint file is not exact LF-terminated text.'
        }
        return @('--host', $Matches[1])
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-ExpectedLabels {
    param(
        [Parameter(Mandatory = $true)]$Labels,
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$ExpectedRole
    )

    if ($Labels.'com.bettingproject.work-order' -ne $workOrder -or
        $Labels.'com.bettingproject.wo036.run-id' -ne $State.RunId -or
        $Labels.'com.bettingproject.wo036.ownership-sha256' -ne $State.OwnershipSha256 -or
        $Labels.'com.bettingproject.wo036.role' -ne $ExpectedRole) {
        throw 'Exact WO-036 ownership labels could not be proven.'
    }
}

function Stop-ExactOwnedProcesses {
    param([Parameter(Mandatory = $true)]$State)

    foreach ($ownedProcess in @($State.OwnedProcesses)) {
        if ($ownedProcess.Pid -notmatch '^\d+$' -or [int64]$ownedProcess.Pid -le 0 -or
            $ownedProcess.Role -notin @('local-lab', 'betting-project', 'local-lab-launcher', 'betting-project-launcher') -or
            $ownedProcess.CommandLineSha256 -notmatch '^[0-9a-f]{64}$' -or
            [string]::IsNullOrWhiteSpace($ownedProcess.ExecutablePath) -or
            [string]::IsNullOrWhiteSpace($ownedProcess.StartTimeUtc)) {
            throw 'An owned-process record is incomplete; PID-only termination is forbidden.'
        }
        $processId = [int]$ownedProcess.Pid
        $cimProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
        if ($null -eq $cimProcess) {
            continue
        }
        Assert-ExactPath `
            -Actual $cimProcess.ExecutablePath `
            -Expected $ownedProcess.ExecutablePath `
            -Failure 'An owned process executable no longer matches its recorded identity.'
        $commandLine = if ($null -eq $cimProcess.CommandLine) { '' } else { $cimProcess.CommandLine }
        if ((Get-Sha256Text -Value $commandLine) -ne $ownedProcess.CommandLineSha256) {
            throw 'An owned process command line no longer matches its recorded identity.'
        }
        $actualStartTime = (Get-Process -Id $processId).StartTime.ToUniversalTime()
        $expectedStartTime = [DateTimeOffset]::Parse(
            $ownedProcess.StartTimeUtc,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind).UtcDateTime
        if ([Math]::Abs(($actualStartTime - $expectedStartTime).TotalSeconds) -gt 1) {
            throw 'An owned process start time no longer matches its recorded identity.'
        }
        if ($PSCmdlet.ShouldProcess("owned process role $($ownedProcess.Role)", 'Stop exact process')) {
            Stop-Process -Id $processId
            try {
                Wait-Process -Id $processId -Timeout 10 -ErrorAction Stop
            }
            catch {
                $freshProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
                if ($null -ne $freshProcess) {
                    Assert-ExactPath `
                        -Actual $freshProcess.ExecutablePath `
                        -Expected $ownedProcess.ExecutablePath `
                        -Failure 'Process identity changed before bounded forced termination.'
                    $freshCommand = if ($null -eq $freshProcess.CommandLine) { '' } else { $freshProcess.CommandLine }
                    if ((Get-Sha256Text -Value $freshCommand) -ne $ownedProcess.CommandLineSha256) {
                        throw 'Process command line changed before bounded forced termination.'
                    }
                    $freshStartTime = (Get-Process -Id $processId -ErrorAction Stop).StartTime.ToUniversalTime()
                    if ([Math]::Abs(($freshStartTime - $expectedStartTime).TotalSeconds) -gt 1) {
                        throw 'Process start time changed before bounded forced termination.'
                    }
                    Stop-Process -Id $processId -Force
                    Wait-Process -Id $processId -Timeout 10 -ErrorAction Stop
                }
            }
        }
    }
}

function Remove-ExactOwnedCertificates {
    param([Parameter(Mandatory = $true)]$State)

    foreach ($ownedCertificate in @($State.OwnedCertificates)) {
        if ($ownedCertificate.Role -eq 'receiver-server-trust-root') {
            $expectedStore = 'CurrentUser\Root'
            $providerStore = 'Cert:\CurrentUser\Root'
        }
        elseif ($ownedCertificate.Role -eq 'sender-client') {
            $expectedStore = 'CurrentUser\My'
            $providerStore = 'Cert:\CurrentUser\My'
        }
        else {
            throw 'An owned certificate has an unexpected WO-036 role.'
        }
        if ($ownedCertificate.StoreLocation -ne $expectedStore -or
            $ownedCertificate.Thumbprint -notmatch '^[0-9A-F]{40}$' -or
            $ownedCertificate.Sha256 -notmatch '^[0-9a-f]{64}$' -or
            [string]::IsNullOrWhiteSpace($ownedCertificate.Subject)) {
            throw 'An owned certificate record is incomplete.'
        }
        $certificatePath = Join-Path $providerStore $ownedCertificate.Thumbprint
        $certificate = Get-Item -LiteralPath $certificatePath -ErrorAction SilentlyContinue
        if ($null -eq $certificate) {
            continue
        }
        if ((Get-CertificateSha256 -Certificate $certificate) -ne $ownedCertificate.Sha256 -or
            $certificate.Subject -ne $ownedCertificate.Subject) {
            throw 'A certificate-store entry failed exact WO-036 ownership verification.'
        }
        if ($PSCmdlet.ShouldProcess(
            "owned certificate role $($ownedCertificate.Role)",
            'Remove exact certificate')) {
            if ($ownedCertificate.StoreLocation -eq 'CurrentUser\Root') {
                $certutilPath = Join-Path ([Environment]::SystemDirectory) 'certutil.exe'
                if (-not (Test-Path -LiteralPath $certutilPath -PathType Leaf)) {
                    throw 'certutil.exe is unavailable for exact CurrentUser Root cleanup.'
                }
                & $certutilPath -user -delstore Root $ownedCertificate.Thumbprint *> $null
                if ($LASTEXITCODE -ne 0) {
                    throw 'Exact CurrentUser Root certificate cleanup failed.'
                }
                Assert-CurrentUserCertificateRemoved `
                    -StoreName Root `
                    -Thumbprint $ownedCertificate.Thumbprint
            }
            else {
                Remove-Item -LiteralPath $certificatePath -Force
                Assert-CurrentUserCertificateRemoved `
                    -StoreName My `
                    -Thumbprint $ownedCertificate.Thumbprint
            }
        }
    }
}

function Remove-ExactOwnedDockerResources {
    param([Parameter(Mandatory = $true)]$State)

    if ([string]::IsNullOrWhiteSpace($State.dockerExecutable) -or
        $State.dockerExecutableSha256 -notmatch '^[0-9a-f]{64}$' -or
        -not (Test-Path -LiteralPath $State.dockerExecutable -PathType Leaf)) {
        throw 'The exact Docker executable is unavailable for the zero-residue proof.'
    }
    $script:dockerExecutable = (Get-Item -LiteralPath $State.dockerExecutable -Force).FullName
    Assert-TrustedDockerExecutablePath -Path $script:dockerExecutable
    Assert-ExactPath -Actual $script:dockerExecutable -Expected $State.dockerExecutable `
        -Failure 'The recorded Docker executable path changed.'
    Assert-NotReparsePoint -Path $script:dockerExecutable
    $actualDockerSha256 = (Get-FileHash -LiteralPath $script:dockerExecutable -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualDockerSha256 -ne $State.dockerExecutableSha256) {
        throw 'The recorded Docker executable hash changed.'
    }
    $dockerArguments = @(Get-PinnedDockerArguments -State $State)
    $containerIds = @(& $script:dockerExecutable @dockerArguments ps --all --quiet `
        --filter "label=com.docker.compose.project=$($State.ComposeProjectName)" |
        ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inventory containers for the exact WO-036 Compose project.'
    }
    foreach ($containerId in $containerIds) {
        $labelJson = (& $script:dockerExecutable @dockerArguments inspect --format '{{json .Config.Labels}}' $containerId | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($labelJson)) {
            throw 'Unable to inspect a candidate WO-036 container.'
        }
        $labels = $labelJson | ConvertFrom-Json
        if ($labels.'com.docker.compose.project' -ne $State.ComposeProjectName -or
            $labels.'com.bettingproject.wo036.role' -notin @('local-lab-postgres', 'betting-project-postgres')) {
            throw 'A candidate container failed exact Compose-project ownership verification.'
        }
        Assert-ExpectedLabels -Labels $labels -State $State `
            -ExpectedRole $labels.'com.bettingproject.wo036.role'
    }

    if ($containerIds.Count -gt 0 -and $PSCmdlet.ShouldProcess(
        "Compose project $($State.ComposeProjectName)",
        'Stop and remove exact owned containers without removing volumes')) {
        $composeArguments = @(
            'compose',
            '--project-name', $State.ComposeProjectName,
            '--file', $State.ComposePath,
            '--env-file', $State.EnvironmentPath
        )
        & $script:dockerExecutable @dockerArguments @composeArguments down --remove-orphans *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'Stopping exact WO-036 containers failed.'
        }
    }

    $remainingContainers = @(& $script:dockerExecutable @dockerArguments ps --all --quiet `
        --filter "label=com.docker.compose.project=$($State.ComposeProjectName)" |
        ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0 -or $remainingContainers.Count -ne 0) {
        throw 'Owned WO-036 containers remain after the bounded cleanup.'
    }

    $expectedVolumes = @(
        "$($State.ComposeProjectName)_local_lab_postgres_data",
        "$($State.ComposeProjectName)_betting_project_postgres_data"
    )
    if (@($State.OwnedVolumes).Count -ne 2 -or
        @(Compare-Object -ReferenceObject $expectedVolumes -DifferenceObject @($State.OwnedVolumes)).Count -ne 0) {
        throw 'The private state does not identify the two exact WO-036 volumes.'
    }
    $volumeRoles = @{
        $expectedVolumes[0] = 'local-lab-postgres-data'
        $expectedVolumes[1] = 'betting-project-postgres-data'
    }
    foreach ($volumeName in $expectedVolumes) {
        $matches = @(& $script:dockerExecutable @dockerArguments volume ls --quiet --filter "name=^$volumeName`$" |
            ForEach-Object { $_.Trim() } | Where-Object { $_ -eq $volumeName })
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inventory an exact WO-036 volume.'
        }
        if ($matches.Count -eq 0) {
            continue
        }
        if ($matches.Count -ne 1) {
            throw 'An exact WO-036 volume name resolved ambiguously.'
        }
        $labelJson = (& $script:dockerExecutable @dockerArguments volume inspect --format '{{json .Labels}}' $volumeName | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($labelJson)) {
            throw 'Unable to inspect an exact WO-036 volume.'
        }
        $labels = $labelJson | ConvertFrom-Json
        if ($labels.'com.docker.compose.project' -ne $State.ComposeProjectName) {
            throw 'A volume failed exact Compose-project ownership verification.'
        }
        Assert-ExpectedLabels -Labels $labels -State $State -ExpectedRole $volumeRoles[$volumeName]
        if ($PSCmdlet.ShouldProcess("owned volume $volumeName", 'Remove exact campaign volume')) {
            & $script:dockerExecutable @dockerArguments volume rm $volumeName *> $null
            if ($LASTEXITCODE -ne 0) {
                throw 'Removing an exact WO-036 volume failed.'
            }
        }
    }

    $remainingVolumeCount = 0
    foreach ($volumeName in $expectedVolumes) {
        $matches = @(& $script:dockerExecutable @dockerArguments volume ls --quiet `
            --filter "name=^$volumeName`$" |
            ForEach-Object { $_.Trim() } | Where-Object { $_ -eq $volumeName })
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to prove exact WO-036 volume cleanup postconditions.'
        }
        $remainingVolumeCount += $matches.Count
    }
    if ($remainingVolumeCount -ne 0) {
        throw 'Owned WO-036 volumes remain after the bounded cleanup.'
    }
    return [pscustomobject]@{
        ContainerResidualCount = $remainingContainers.Count
        VolumeResidualCount = $remainingVolumeCount
    }
}

if (-not $IsWindows) {
    throw 'WO-036 infrastructure cleanup is supported only on Windows.'
}
if ($WhatIfPreference) {
    throw 'WO-036 cleanup refuses WhatIf because its fail-closed state transition must be durable.'
}

# Validate the caller-supplied topology without parsing mutable state. The shared
# lock is acquired before state content is trusted or changed.
$resolvedStatePath = (Resolve-Path -LiteralPath $StatePath).Path
if ([System.IO.Path]::GetFileName($resolvedStatePath) -cne 'state.private.json') {
    throw 'The cleanup state filename is not the exact WO-036 private-state filename.'
}
$currentOwnerSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$localApplicationData = [Environment]::GetFolderPath(
    [Environment+SpecialFolder]::LocalApplicationData)
$campaignBase = [System.IO.Path]::GetFullPath(
    (Join-Path $localApplicationData 'SofaScoreLocalLab\qualifications\WO-SS-20260902-036'))
$candidateRunRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $resolvedStatePath))
$candidateRunId = [guid]::Empty
if (-not [guid]::TryParseExact(
        [System.IO.Path]::GetFileName($candidateRunRoot),
        'D',
        [ref]$candidateRunId)) {
    throw 'The cleanup state parent is not an exact GUID run root.'
}
$expectedRunRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $campaignBase $candidateRunId.ToString('D')))
Assert-ExactPath -Actual $candidateRunRoot -Expected $expectedRunRoot `
    -Failure 'The cleanup state is outside the exact WO-036 run root.'
Assert-NotReparsePoint -Path $campaignBase
Assert-NotReparsePoint -Path $expectedRunRoot
Assert-NotReparsePoint -Path $resolvedStatePath
Assert-PrivateAcl -Path $expectedRunRoot -OwnerSid $currentOwnerSid -RequireProtected
Assert-PrivateAcl -Path $resolvedStatePath -OwnerSid $currentOwnerSid

$toolsLockPath = Join-Path $expectedRunRoot '.wo036-tools.lock'
$resolvedToolsLockPath = (Resolve-Path -LiteralPath $toolsLockPath).Path
Assert-ExactPath -Actual $resolvedToolsLockPath -Expected $toolsLockPath `
    -Failure 'The WO-036 shared tools lock path is not exact.'
Assert-NotReparsePoint -Path $resolvedToolsLockPath
Assert-PrivateAcl -Path $resolvedToolsLockPath -OwnerSid $currentOwnerSid
try {
    $script:toolsLock = [System.IO.FileStream]::new(
        $resolvedToolsLockPath,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None)
}
catch {
    throw 'WO-036 shared tools lock is unavailable; concurrent cleanup is refused.'
}

$state = $null
$runIdForOutput = $candidateRunId.ToString('D')
$rootRemoved = $false
try {
    # Revalidate every trusted path under the lock, then persist a one-way
    # transition which prevents any campaign operation from racing cleanup.
    $resolvedStatePath = (Resolve-Path -LiteralPath $StatePath).Path
    Assert-ExactPath -Actual (Split-Path -Parent $resolvedStatePath) -Expected $expectedRunRoot `
        -Failure 'The private state moved after shared-lock acquisition.'
    Assert-NotReparsePoint -Path $resolvedStatePath
    Assert-NoDescendantReparsePoint -Root $expectedRunRoot
    $state = Get-Content -LiteralPath $resolvedStatePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.SchemaVersion -ne 1 -or $state.WorkOrder -ne $workOrder -or
        $state.RunId -notmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' -or
        $state.OwnerSid -notmatch '^S-1-' -or
        $state.OwnershipSha256 -notmatch '^[0-9a-f]{64}$' -or
        $state.ComposeProjectName -notmatch '^wo036[0-9a-f]{32}$') {
        throw 'The cleanup state does not match the strict WO-036 schema.'
    }
    if ($state.dockerResourcesMayExist -isnot [bool] -or
        $state.databasesStarted -isnot [bool] -or
        $state.databasesHealthy -isnot [bool] -or
        $state.cleanupStatus -notin @('NOT_STARTED', 'IN_PROGRESS')) {
        throw 'The cleanup state has invalid infrastructure lifecycle fields.'
    }
    if ($state.OwnerSid -ne $currentOwnerSid -or $state.RunId -ne $runIdForOutput) {
        throw 'The cleanup state belongs to a different WO-036 run or Windows user.'
    }
    Assert-ExactPath -Actual $state.RunRoot -Expected $expectedRunRoot `
        -Failure 'The cleanup root is not the exact GUID child of the WO-036 campaign base.'
    Assert-ExactPath -Actual $state.MarkerPath -Expected (Join-Path $expectedRunRoot '.wo036-owner.json') `
        -Failure 'The cleanup marker path is not exact.'
    Assert-ExactPath -Actual $state.EnvironmentPath -Expected (Join-Path $expectedRunRoot 'campaign.private.env') `
        -Failure 'The cleanup environment path is not exact.'
    Assert-ExactPath -Actual $state.ComposePath -Expected $expectedComposePath `
        -Failure 'The cleanup compose path is not the versioned WO-036 compose file.'
    Assert-ExactPath -Actual $state.RepositoryRoot -Expected $repositoryRoot `
        -Failure 'The cleanup repository root does not match the running script.'
    Assert-NotReparsePoint -Path $state.MarkerPath
    if (Test-Path -LiteralPath $state.EnvironmentPath -PathType Leaf) {
        Assert-NotReparsePoint -Path $state.EnvironmentPath
    }

    $marker = Get-Content -LiteralPath $state.MarkerPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($marker.SchemaVersion -ne 1 -or $marker.WorkOrder -ne $workOrder -or
        $marker.RunId -ne $state.RunId -or $marker.OwnerSid -ne $state.OwnerSid -or
        $marker.OwnershipSha256 -ne $state.OwnershipSha256 -or
        $marker.ComposeProjectName -ne $state.ComposeProjectName) {
        throw 'The private ownership marker does not match the cleanup state.'
    }

    $cleanupResumed = $state.cleanupStatus -eq 'IN_PROGRESS'
    if (-not $cleanupResumed) {
        $state.cleanupStatus = 'IN_PROGRESS'
        Write-PrivateStateAtomic -Path $resolvedStatePath -State $state -OwnerSid $currentOwnerSid
    }

    try {
        Stop-ExactOwnedProcesses -State $state
    }
    catch {
        $errors.Add('OWNED_PROCESS_CLEANUP_FAILED')
    }
    $dockerProof = $null
    try {
        $dockerProof = Remove-ExactOwnedDockerResources -State $state
    }
    catch {
        $errors.Add('OWNED_DOCKER_CLEANUP_FAILED')
    }
    try {
        Remove-ExactOwnedCertificates -State $state
    }
    catch {
        $errors.Add('OWNED_CERTIFICATE_CLEANUP_FAILED')
    }
    if ($errors.Count -ne 0) {
        throw ('WO-036 cleanup failed closed: ' + (($errors | Sort-Object -Unique) -join ','))
    }

    $ownedProcessResidualCount = Get-ExactOwnedProcessResidualCount -State $state
    $listenerProof = Get-ListenerResidualProof
    $ownedCertificateResidualCount = Get-OwnedCertificateResidualCount -State $state
    $ownedContainerResidualCount = [int]$dockerProof.ContainerResidualCount
    $ownedVolumeResidualCount = [int]$dockerProof.VolumeResidualCount

    Write-Output "WO036_CLEANUP_RESUMED=$(if ($cleanupResumed) { 'YES' } else { 'NO' })"
    Write-Output "WO036_CLEANUP_OWNED_PROCESS_RESIDUAL_COUNT=$ownedProcessResidualCount"
    Write-Output "WO036_CLEANUP_CONTAINER_RESIDUAL_COUNT=$ownedContainerResidualCount"
    Write-Output "WO036_CLEANUP_VOLUME_RESIDUAL_COUNT=$ownedVolumeResidualCount"
    Write-Output "WO036_CLEANUP_CERTIFICATE_RESIDUAL_COUNT=$ownedCertificateResidualCount"
    Write-Output "WO036_CLEANUP_LISTENER_8087_COUNT=$($listenerProof.'8087')"
    Write-Output "WO036_CLEANUP_LISTENER_8444_COUNT=$($listenerProof.'8444')"
    Write-Output "WO036_CLEANUP_LISTENER_5432_COUNT=$($listenerProof.'5432')"
    Write-Output "WO036_CLEANUP_LISTENER_5433_COUNT=$($listenerProof.'5433')"

    $residualTotal = $ownedProcessResidualCount + $ownedContainerResidualCount +
        $ownedVolumeResidualCount + $ownedCertificateResidualCount +
        [int]$listenerProof.'8087' + [int]$listenerProof.'8444' +
        [int]$listenerProof.'5432' + [int]$listenerProof.'5433'
    if ($residualTotal -ne 0) {
        throw 'WO-036 cleanup postconditions found a residual owned resource or listener.'
    }
    Write-Output 'WO036_CLEANUP_ZERO_RESOURCE_PRE_ROOT=PASS'

    if (-not $PSCmdlet.ShouldProcess(
            'exact owned WO-036 private run root',
            'Remove its validated descendants, shared lock last, and then the empty root')) {
        throw 'WO-036 cleanup confirmation was declined after zero-residue proof.'
    }

    $freshRunRoot = (Resolve-Path -LiteralPath $expectedRunRoot).Path
    Assert-ExactPath -Actual $freshRunRoot -Expected $expectedRunRoot `
        -Failure 'The private run root changed before recursive removal.'
    Assert-NotReparsePoint -Path $freshRunRoot
    Assert-NoDescendantReparsePoint -Root $freshRunRoot
    Assert-ExactPath -Actual (Resolve-Path -LiteralPath $state.MarkerPath).Path `
        -Expected (Join-Path $freshRunRoot '.wo036-owner.json') `
        -Failure 'The ownership marker changed before private-root removal.'
    Assert-ExactPath -Actual (Resolve-Path -LiteralPath $resolvedStatePath).Path `
        -Expected (Join-Path $freshRunRoot 'state.private.json') `
        -Failure 'The private state changed before private-root removal.'
    Assert-ExactPath -Actual (Resolve-Path -LiteralPath $resolvedToolsLockPath).Path `
        -Expected (Join-Path $freshRunRoot '.wo036-tools.lock') `
        -Failure 'The shared tools lock changed before private-root removal.'

    # Keep both the durable state and the shared lock until every other exact
    # descendant has been removed. State is then removed, the lock is released
    # and deleted last, and finally the already-empty GUID root is removed.
    foreach ($child in @(Get-ChildItem -LiteralPath $freshRunRoot -Force)) {
        if ($child.Name -in @('.wo036-tools.lock', 'state.private.json')) {
            continue
        }
        if (($child.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'A descendant became a reparse point before exact cleanup.'
        }
        Remove-Item -LiteralPath $child.FullName -Recurse -Force
    }
    $remainingBeforeState = @(Get-ChildItem -LiteralPath $freshRunRoot -Force |
        Select-Object -ExpandProperty Name)
    if (@(Compare-Object -ReferenceObject @('.wo036-tools.lock', 'state.private.json') `
            -DifferenceObject $remainingBeforeState).Count -ne 0) {
        throw 'Unexpected private-root descendants remain before final lock cleanup.'
    }
    Remove-Item -LiteralPath $resolvedStatePath -Force
    $remainingBeforeLock = @(Get-ChildItem -LiteralPath $freshRunRoot -Force |
        Select-Object -ExpandProperty Name)
    if ($remainingBeforeLock.Count -ne 1 -or $remainingBeforeLock[0] -cne '.wo036-tools.lock') {
        throw 'The shared tools lock is not the final private-root file.'
    }

    $script:toolsLock.Dispose()
    $script:toolsLock = $null
    Assert-NotReparsePoint -Path $resolvedToolsLockPath
    Remove-Item -LiteralPath $resolvedToolsLockPath -Force
    if (@(Get-ChildItem -LiteralPath $freshRunRoot -Force).Count -ne 0) {
        throw 'The WO-036 run root is not empty after deleting the shared lock last.'
    }
    Remove-Item -LiteralPath $freshRunRoot -Force
    if (Test-Path -LiteralPath $freshRunRoot) {
        throw 'The exact WO-036 private run root remains after cleanup.'
    }
    $rootRemoved = $true
}
finally {
    if ($null -ne $script:toolsLock) {
        $script:toolsLock.Dispose()
        $script:toolsLock = $null
    }
}

if (-not $rootRemoved) {
    throw 'WO-036 cleanup did not remove the exact private run root.'
}
Write-Output 'WO036_CLEANUP_ATTESTATION=PASS'
Write-Output 'WO036_INFRASTRUCTURE_CLEANUP=PASS'
Write-Output "WO036_RUN_ID=$runIdForOutput"
Write-Output 'WO036_SHARED_TOOLS_LOCK_DELETED_LAST=YES'
Write-Output 'WO036_PRIMARY_DATABASE_TOUCHED=NO'
Write-Output 'WO036_PROVIDER_NETWORK_TOUCHED=NO'
