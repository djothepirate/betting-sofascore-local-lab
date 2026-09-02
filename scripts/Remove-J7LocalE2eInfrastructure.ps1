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

function Get-LocalDockerArguments {
    $dockerContextOverride = [Environment]::GetEnvironmentVariable('DOCKER_CONTEXT')
    $dockerHostOverride = [Environment]::GetEnvironmentVariable('DOCKER_HOST')
    if (-not [string]::IsNullOrWhiteSpace($dockerContextOverride)) {
        $endpointOutput = & $script:dockerExecutable context inspect --format '{{.Endpoints.docker.Host}}' $dockerContextOverride
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inspect the Docker context selected by DOCKER_CONTEXT.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    elseif (-not [string]::IsNullOrWhiteSpace($dockerHostOverride)) {
        if ($dockerHostOverride -notmatch '^npipe:////[.]/pipe/[^/]+$') {
            throw 'DOCKER_HOST must target a local Windows named pipe.'
        }
        $endpoint = $dockerHostOverride
    }
    else {
        $endpointOutput = & $script:dockerExecutable context inspect --format '{{.Endpoints.docker.Host}}'
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inspect the selected Docker context.'
        }
        $endpoint = ($endpointOutput | Out-String).Trim()
    }
    if ($endpoint -notmatch '^npipe:////[.]/pipe/[^/]+$') {
        throw 'The selected Docker context must target a local Windows named pipe.'
    }
    return @('--host', $endpoint)
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

    if (-not $State.dockerResourcesMayExist) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($State.dockerExecutable) -or
        $State.dockerExecutableSha256 -notmatch '^[0-9a-f]{64}$' -or
        -not (Test-Path -LiteralPath $State.dockerExecutable -PathType Leaf)) {
        throw 'The exact Docker executable is unavailable while owned resources may exist.'
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
    $dockerArguments = @(Get-LocalDockerArguments)
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
        & $script:dockerExecutable @dockerArguments @composeArguments down --remove-orphans
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
}

if (-not $IsWindows) {
    throw 'WO-036 infrastructure cleanup is supported only on Windows.'
}
$resolvedStatePath = (Resolve-Path -LiteralPath $StatePath).Path
if ([System.IO.Path]::GetFileName($resolvedStatePath) -ne 'state.private.json') {
    throw 'The cleanup state filename is not the exact WO-036 private-state filename.'
}
$state = Get-Content -LiteralPath $resolvedStatePath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($state.SchemaVersion -ne 1 -or $state.WorkOrder -ne $workOrder -or
    $state.RunId -notmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' -or
    $state.OwnerSid -notmatch '^S-1-' -or
    $state.OwnershipSha256 -notmatch '^[0-9a-f]{64}$' -or
    $state.ComposeProjectName -notmatch '^wo036[0-9a-f]{32}$') {
    throw 'The cleanup state does not match the strict WO-036 schema.'
}
if ($state.dockerResourcesMayExist -isnot [bool] -or
    $state.databasesStarted -isnot [bool] -or
    $state.databasesHealthy -isnot [bool]) {
    throw 'The cleanup state has non-boolean infrastructure lifecycle fields.'
}

$currentOwnerSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if ($state.OwnerSid -ne $currentOwnerSid) {
    throw 'The cleanup state belongs to a different Windows user.'
}
$localApplicationData = [Environment]::GetFolderPath(
    [Environment+SpecialFolder]::LocalApplicationData)
$campaignBase = [System.IO.Path]::GetFullPath(
    (Join-Path $localApplicationData 'SofaScoreLocalLab\qualifications\WO-SS-20260902-036'))
$runId = [guid]::ParseExact($state.RunId, 'D')
$expectedRunRoot = Join-Path $campaignBase $runId.ToString('D')
Assert-ExactPath -Actual $state.RunRoot -Expected $expectedRunRoot `
    -Failure 'The cleanup root is not the exact GUID child of the WO-036 campaign base.'
Assert-ExactPath -Actual (Split-Path -Parent $resolvedStatePath) -Expected $expectedRunRoot `
    -Failure 'The private state is not inside its exact WO-036 run root.'
Assert-ExactPath -Actual $state.MarkerPath -Expected (Join-Path $expectedRunRoot '.wo036-owner.json') `
    -Failure 'The cleanup marker path is not exact.'
Assert-ExactPath -Actual $state.EnvironmentPath -Expected (Join-Path $expectedRunRoot 'campaign.private.env') `
    -Failure 'The cleanup environment path is not exact.'
Assert-ExactPath -Actual $state.ComposePath -Expected $expectedComposePath `
    -Failure 'The cleanup compose path is not the versioned WO-036 compose file.'
Assert-ExactPath -Actual $state.RepositoryRoot -Expected $repositoryRoot `
    -Failure 'The cleanup repository root does not match the running script.'
Assert-NotReparsePoint -Path $campaignBase
Assert-NotReparsePoint -Path $expectedRunRoot
Assert-NotReparsePoint -Path $resolvedStatePath
Assert-NotReparsePoint -Path $state.MarkerPath
if (Test-Path -LiteralPath $state.EnvironmentPath -PathType Leaf) {
    Assert-NotReparsePoint -Path $state.EnvironmentPath
}
Assert-NoDescendantReparsePoint -Root $expectedRunRoot

$rootAcl = Get-Acl -LiteralPath $expectedRunRoot
$rootOwnerSid = ([System.Security.Principal.NTAccount]$rootAcl.Owner).Translate(
    [System.Security.Principal.SecurityIdentifier]).Value
if ($rootOwnerSid -ne $currentOwnerSid -or -not $rootAcl.AreAccessRulesProtected) {
    throw 'The private run root ownership or ACL protection no longer matches.'
}
foreach ($rule in $rootAcl.Access) {
    $ruleSid = $rule.IdentityReference.Translate(
        [System.Security.Principal.SecurityIdentifier]).Value
    if ($ruleSid -ne $currentOwnerSid -or
        $rule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) {
        throw 'The private run root grants access outside its exact owner.'
    }
}

$marker = Get-Content -LiteralPath $state.MarkerPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($marker.SchemaVersion -ne 1 -or $marker.WorkOrder -ne $workOrder -or
    $marker.RunId -ne $state.RunId -or $marker.OwnerSid -ne $state.OwnerSid -or
    $marker.OwnershipSha256 -ne $state.OwnershipSha256 -or
    $marker.ComposeProjectName -ne $state.ComposeProjectName) {
    throw 'The private ownership marker does not match the cleanup state.'
}

try {
    Stop-ExactOwnedProcesses -State $state
}
catch {
    $errors.Add('OWNED_PROCESS_CLEANUP_FAILED')
}
try {
    Remove-ExactOwnedDockerResources -State $state
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

if ($PSCmdlet.ShouldProcess('exact owned WO-036 private run root', 'Remove recursively')) {
    $freshRunRoot = (Resolve-Path -LiteralPath $expectedRunRoot).Path
    Assert-ExactPath -Actual $freshRunRoot -Expected $expectedRunRoot `
        -Failure 'The private run root changed before recursive removal.'
    Assert-NotReparsePoint -Path $freshRunRoot
    Assert-NoDescendantReparsePoint -Root $freshRunRoot
    Remove-Item -LiteralPath $freshRunRoot -Recurse -Force
}

Write-Output 'WO036_INFRASTRUCTURE_CLEANUP=PASS'
Write-Output "WO036_RUN_ID=$($state.RunId)"
Write-Output 'WO036_PRIMARY_DATABASE_TOUCHED=NO'
Write-Output 'WO036_PROVIDER_NETWORK_TOUCHED=NO'
