Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

$script:WorkOrder = 'WO-SS-20260902-036-j9-j7-local-e2e-qualification'
$script:StateSchemaVersion = 1
$script:ReceiverOrigin = 'https://127.0.0.1:8444'
$script:ReceiverPath = '/api/imports/sofascore/j7-canonical-events'
$script:RequestMediaType =
    'application/vnd.betting-project.j7-canonical-event+json;version=1.0'
$script:AckMediaType = 'application/vnd.betting-project.j7-delivery-ack+json;version=1.0'
$script:ExpectedLocalLabSenderCommit = 'f5a27887b7db43576eb608d564c245c8cca3a602'
$script:ExpectedReceiverHeadCommit = 'b6a093ab4d3358f23a59b65b68a3eb720494bcba'
$script:ExpectedReceiverImplementationCommit = '3920a58c122cbee0fb379781abcd53d3eaa0f70d'
$script:ExpectedLocalLabBranch = 'codex/j9-wo036-j7-local-e2e'
$script:ExpectedReceiverBranch = 'codex/int-001-j7-receiver'
$script:AllowedPostFreezeManifestPath =
    'docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-20260903.md'
$script:MaximumImportRouteCalls = 3
$script:DockerEndpointFileName = 'docker-endpoint.private.txt'
$script:PreCallLogNames = @(
    'receiver.stdout.log', 'receiver.stderr.log',
    'locallabpreparation.stdout.log', 'locallabpreparation.stderr.log',
    'locallaba.stdout.log', 'locallaba.stderr.log')
$script:CompletedLocalLabLogNames = @(
    $script:PreCallLogNames +
    @('locallabb.stdout.log', 'locallabb.stderr.log'))
$script:ToolingRelativePaths = @(
    'compose.wo036.yml',
    'scripts/Initialize-J7LocalE2eInfrastructure.ps1',
    'scripts/Remove-J7LocalE2eInfrastructure.ps1',
    'scripts/wo036/WO036-CampaignTools.psm1',
    'scripts/wo036/Register-WO036ExecutableArtifacts.ps1',
    'scripts/wo036/Start-WO036Component.ps1',
    'scripts/wo036/Stop-WO036Component.ps1',
    'scripts/wo036/Copy-WO036LocalLabDatabase.ps1',
    'scripts/wo036/Export-WO036PreCallFreeze.ps1',
    'scripts/wo036/Test-WO036FrozenTooling.ps1',
    'scripts/wo036/Invoke-WO036CollisionProbe.ps1',
    'scripts/wo036/Export-WO036RedactedEvidence.ps1',
    'scripts/wo036/Test-WO036PrivateLogRedaction.ps1'
)
$script:JdkHttpClientRetryGuards = @(
    '-Djdk.httpclient.disableRetryConnect=true',
    '-Djdk.httpclient.redirects.retrylimit=1',
    '-Djdk.httpclient.enableAllMethodRetry=false'
)
$script:RepositoryRoot = [IO.Path]::GetFullPath(
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false, $true)

function ConvertTo-WO036UtcDateTime {
    param(
        [Parameter(Mandatory = $true)][object]$Value,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Value -is [DateTimeOffset]) {
        return $Value.UtcDateTime
    }
    if ($Value -is [DateTime]) {
        if ($Value.Kind -eq [DateTimeKind]::Unspecified) {
            throw "WO-036 $Description has no UTC or offset identity."
        }
        return $Value.ToUniversalTime()
    }
    if ($Value -is [string]) {
        try {
            return [DateTimeOffset]::ParseExact(
                $Value,
                'O',
                [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::RoundtripKind).UtcDateTime
        }
        catch {
            throw "WO-036 $Description is not one round-trip timestamp."
        }
    }
    throw "WO-036 $Description has an invalid timestamp type."
}

function Test-WO036PathWithin {
    param(
        [Parameter(Mandatory = $true)][string]$Candidate,
        [Parameter(Mandatory = $true)][string]$Root
    )

    $candidatePath = [IO.Path]::GetFullPath($Candidate)
    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar)
    $prefix = $rootPath + [IO.Path]::DirectorySeparatorChar
    return $candidatePath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-WO036NoReparsePathChain {
    param(
        [Parameter(Mandatory = $true)][string]$Candidate,
        [Parameter(Mandatory = $true)][string]$Root
    )

    $candidatePath = [IO.Path]::GetFullPath($Candidate)
    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar)
    if (-not $candidatePath.Equals($rootPath,
                [StringComparison]::OrdinalIgnoreCase) `
            -and -not (Test-WO036PathWithin -Candidate $candidatePath -Root $rootPath)) {
        throw 'WO-036 path chain escapes its exact root.'
    }
    $current = $candidatePath
    if (-not (Test-Path -LiteralPath $current)) {
        $current = Split-Path -Parent $current
    }
    while ($true) {
        $item = Get-Item -LiteralPath $current -Force -ErrorAction Stop
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'WO-036 path chain contains a reparse point.'
        }
        $normalizedCurrent = [IO.Path]::GetFullPath($current).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar)
        if ($normalizedCurrent.Equals(
                $rootPath, [StringComparison]::OrdinalIgnoreCase)) {
            break
        }
        $parent = Split-Path -Parent $current
        if ([string]::IsNullOrEmpty($parent) -or $parent -eq $current) {
            throw 'WO-036 path chain root was not reached.'
        }
        $current = $parent
    }
}

function Assert-WO036ExactProperties {
    param(
        [Parameter(Mandatory = $true)][object]$Value,
        [Parameter(Mandatory = $true)][string[]]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($null -eq $Value) {
        throw "WO-036 private state is missing $Description."
    }
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $wanted = @($Expected | Sort-Object)
    if ($actual.Count -ne $wanted.Count `
            -or (Compare-Object -ReferenceObject $wanted -DifferenceObject $actual)) {
        throw "WO-036 private state has an invalid $Description shape."
    }
}

function Assert-WO036NoDuplicateJsonProperties {
    param([Parameter(Mandatory = $true)][System.Text.Json.JsonElement]$Element)

    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [Collections.Generic.HashSet[string]]::new(
            [StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) {
                throw 'WO-036 private state contains a duplicate JSON key.'
            }
            Assert-WO036NoDuplicateJsonProperties -Element $property.Value
        }
    }
    elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $Element.EnumerateArray()) {
            Assert-WO036NoDuplicateJsonProperties -Element $item
        }
    }
}

function Assert-WO036PrivateAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$RequireProtected
    )

    $unsafeSids = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::OrdinalIgnoreCase)
    @(
        'S-1-1-0',       # Everyone
        'S-1-5-11',      # Authenticated Users
        'S-1-5-32-545',  # BUILTIN\Users
        'S-1-5-32-546'   # BUILTIN\Guests
    ) | ForEach-Object { [void]$unsafeSids.Add($_) }

    try {
        $acl = Get-Acl -LiteralPath $Path
        $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        $ownerSid = ([Security.Principal.NTAccount]$acl.Owner).Translate(
            [Security.Principal.SecurityIdentifier]).Value
        if ($ownerSid -ne $currentSid `
                -or ($RequireProtected -and -not $acl.AreAccessRulesProtected)) {
            throw 'ownership or inheritance mismatch'
        }
        foreach ($rule in $acl.Access) {
            if ($rule.AccessControlType -ne
                    [Security.AccessControl.AccessControlType]::Allow) {
                continue
            }
            $sid = $rule.IdentityReference.Translate(
                [Security.Principal.SecurityIdentifier]).Value
            if ($unsafeSids.Contains($sid)) {
                throw 'unsafe principal'
            }
            if ($sid -ne $currentSid) {
                throw 'non-owner principal'
            }
        }
    }
    catch {
        throw 'WO-036 private state ACL is not restricted to privileged principals.'
    }
}

function Resolve-WO036ExistingPath {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][ValidateSet('Leaf', 'Container')]
        [string]$PathType,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not [IO.Path]::IsPathFullyQualified($Value) `
            -or -not (Test-Path -LiteralPath $Value -PathType $PathType)) {
        throw "WO-036 $Description is not an existing absolute $PathType path."
    }
    try {
        $resolved = (Resolve-Path -LiteralPath $Value).Path
        $item = Get-Item -LiteralPath $resolved -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'reparse point'
        }
        return [IO.Path]::GetFullPath($resolved)
    }
    catch {
        throw "WO-036 $Description cannot be resolved safely."
    }
}

function Assert-WO036SecretValue {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ([string]::IsNullOrWhiteSpace($Value) `
            -or $Value.Length -lt 16 `
            -or $Value.Length -gt 256 `
            -or $Value -match '[\x00-\x1f\x7f]') {
        throw "WO-036 $Description is missing or malformed."
    }
}

function Get-WO036Sha256Hex {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Get-WO036FileSha256Hex {
    param([Parameter(Mandatory = $true)][string]$Path)

    try {
        $stream = [IO.File]::OpenRead($Path)
        try {
            return [Convert]::ToHexString(
                [Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()
        }
        finally {
            $stream.Dispose()
        }
    }
    catch {
        throw 'WO-036 file hashing failed.'
    }
}

function Read-WO036PinnedDockerEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$PrivateRoot,
        [string]$ExpectedSha256 = ''
    )

    $expectedPath = [IO.Path]::GetFullPath((Join-Path `
        $PrivateRoot $script:DockerEndpointFileName))
    $endpointPath = Resolve-WO036ExistingPath -Value $expectedPath `
        -PathType Leaf -Description 'pinned private Docker endpoint'
    if (-not $endpointPath.Equals($expectedPath,
                [StringComparison]::OrdinalIgnoreCase)) {
        throw 'WO-036 pinned Docker endpoint path is not exact.'
    }
    Assert-WO036NoReparsePathChain -Candidate $endpointPath -Root $PrivateRoot
    Assert-WO036PrivateAcl -Path $endpointPath -RequireProtected
    $bytes = [IO.File]::ReadAllBytes($endpointPath)
    try {
        if ($bytes.Length -lt 20 -or $bytes.Length -gt 256 `
                -or $bytes[0] -eq 0xEF `
                -or $bytes[$bytes.Length - 1] -ne 0x0A) {
            throw 'invalid endpoint byte envelope'
        }
        $text = $script:Utf8NoBom.GetString($bytes)
        if ($text.Contains("`r") `
                -or ([regex]::Matches($text, "`n")).Count -ne 1) {
            throw 'invalid endpoint line envelope'
        }
        $endpoint = $text.Substring(0, $text.Length - 1)
        if ($endpoint -cnotmatch '^npipe:////[.]/pipe/[A-Za-z0-9._-]+$') {
            throw 'invalid endpoint value'
        }
        $sha256 = Get-WO036Sha256Hex -Bytes $bytes
        if (-not [string]::IsNullOrEmpty($ExpectedSha256) `
                -and ($ExpectedSha256 -notmatch '^[0-9a-f]{64}$' `
                    -or $sha256 -cne $ExpectedSha256)) {
            throw 'endpoint hash mismatch'
        }
        return [pscustomobject][ordered]@{
            Path = $endpointPath
            Endpoint = $endpoint
            Sha256 = $sha256
        }
    }
    catch {
        throw 'WO-036 pinned Docker endpoint is not exact private UTF-8 evidence.'
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Read-WO036PrivateState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [switch]$AllowUnregisteredExecutables,
        [switch]$AllowInfrastructureNotReady
    )

    $resolvedState = Resolve-WO036ExistingPath -Value $StatePath -PathType Leaf `
        -Description 'state file'
    $privateRoot = Split-Path -Parent $resolvedState
    if (Test-WO036PathWithin -Candidate $resolvedState -Root $script:RepositoryRoot) {
        throw 'WO-036 private state must stay outside the repository.'
    }
    Assert-WO036PrivateAcl -Path $privateRoot -RequireProtected
    Assert-WO036PrivateAcl -Path $resolvedState
    Assert-WO036NoReparsePathChain -Candidate $resolvedState -Root $privateRoot

    try {
        $bytes = [IO.File]::ReadAllBytes($resolvedState)
        if ($bytes.Length -lt 2 -or $bytes.Length -gt 65536) {
            throw 'invalid length'
        }
        $text = $script:Utf8NoBom.GetString($bytes)
        $document = [System.Text.Json.JsonDocument]::Parse($text)
        try {
            Assert-WO036NoDuplicateJsonProperties -Element $document.RootElement
        }
        finally {
            $document.Dispose()
        }
        $state = $text | ConvertFrom-Json -Depth 8
    }
    catch {
        throw 'WO-036 private state is not strict UTF-8 JSON.'
    }
    finally {
        if ($null -ne $bytes) {
            [Array]::Clear($bytes, 0, $bytes.Length)
        }
    }

    Assert-WO036ExactProperties -Value $state -Description 'root' -Expected @(
        'schemaVersion', 'campaignId', 'workOrder', 'runId', 'ownerSid',
        'ownershipSha256', 'repositoryRoot', 'runRoot', 'markerPath',
        'environmentPath', 'composePath', 'composeProjectName', 'createdAtUtc',
        'initializationStatus', 'databasesStarted', 'databasesHealthy',
        'dockerResourcesMayExist', 'dockerExecutable', 'dockerExecutableSha256',
        'javaExecutable', 'localLabJar', 'localLabJarSha256', 'receiverJar',
        'receiverJarSha256', 'receiver', 'localLab', 'ownedContainers',
        'ownedVolumes', 'ownedCertificates', 'ownedProcesses', 'pki',
        'cleanupStatus')
    Assert-WO036ExactProperties -Value $state.receiver -Description 'receiver' -Expected @(
        'databaseContainer', 'databasePort', 'databaseName', 'databaseUser',
        'databasePassword', 'containerRole', 'serverPort', 'keyStorePath',
        'keyStorePassword', 'trustStorePath', 'trustStorePassword',
        'clientCertificateSha256')
    Assert-WO036ExactProperties -Value $state.localLab -Description 'localLab' -Expected @(
        'databaseContainer', 'databasePort', 'databaseUser', 'databasePassword',
        'containerRole', 'sourceDatabase', 'targetDatabase', 'serverPort',
        'exportDirectory', 'clientCertificateSha256')

    Assert-WO036ExactProperties -Value $state.pki -Description 'pki' -Expected @(
        'serverKeyStore', 'receiverTrustStore', 'serverCertificatePublic',
        'clientCertificatePublic', 'clientCertificateSha256')

    if ([int]$state.schemaVersion -ne $script:StateSchemaVersion `
            -or $state.workOrder -ne $script:WorkOrder `
            -or $state.runId -notmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' `
            -or $state.campaignId -ne "wo036-$($state.runId)" `
            -or $state.ownerSid -ne
                [Security.Principal.WindowsIdentity]::GetCurrent().User.Value `
            -or $state.ownershipSha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'WO-036 private state identity is invalid.'
    }

    $resolvedRepository = Resolve-WO036ExistingPath -Value ([string]$state.repositoryRoot) `
        -PathType Container -Description 'repository root'
    $resolvedRunRoot = Resolve-WO036ExistingPath -Value ([string]$state.runRoot) `
        -PathType Container -Description 'private run root'
    $approvedQualificationRoot = [IO.Path]::GetFullPath((Join-Path `
        ([Environment]::GetFolderPath(
            [Environment+SpecialFolder]::LocalApplicationData)) `
        'SofaScoreLocalLab\qualifications\WO-SS-20260902-036'))
    $expectedRunRoot = [IO.Path]::GetFullPath((Join-Path `
        $approvedQualificationRoot ([string]$state.runId)))
    if (-not $resolvedRepository.Equals($script:RepositoryRoot,
                [StringComparison]::OrdinalIgnoreCase) `
            -or -not $resolvedRunRoot.Equals($privateRoot,
                [StringComparison]::OrdinalIgnoreCase) `
            -or -not $resolvedRunRoot.Equals($expectedRunRoot,
                [StringComparison]::OrdinalIgnoreCase) `
            -or [IO.Path]::GetFileName($resolvedState) -cne 'state.private.json' `
            -or $state.composeProjectName -notmatch '^wo036[0-9a-f]{32}$' `
            -or $state.cleanupStatus -ne 'NOT_STARTED') {
        throw 'WO-036 private state topology identity is invalid.'
    }
    Assert-WO036NoReparsePathChain -Candidate $resolvedRunRoot `
        -Root $approvedQualificationRoot
    $dockerEndpointProof = Read-WO036PinnedDockerEndpoint `
        -PrivateRoot $resolvedRunRoot
    if (-not $AllowInfrastructureNotReady `
            -and ($state.initializationStatus -ne 'PASS' `
                -or $state.databasesStarted -ne $true `
                -or $state.databasesHealthy -ne $true)) {
        throw 'WO-036 private infrastructure is not ready.'
    }

    $dockerPath = Resolve-WO036ExistingPath -Value ([string]$state.dockerExecutable) `
        -PathType Leaf -Description 'Docker executable'
    if ($state.dockerExecutableSha256 -notmatch '^[0-9a-f]{64}$' `
            -or (Get-WO036FileSha256Hex -Path $dockerPath) -cne
                [string]$state.dockerExecutableSha256) {
        throw 'WO-036 Docker executable identity is invalid.'
    }

    $executableValues = @(
        $state.javaExecutable,
        $state.localLabJar,
        $state.localLabJarSha256,
        $state.receiverJar,
        $state.receiverJarSha256)
    $unregistered = @($executableValues | Where-Object { $null -eq $_ }).Count -eq 5
    if ($unregistered -and -not $AllowUnregisteredExecutables) {
        throw 'WO-036 executable artifacts have not been registered.'
    }
    if (-not $unregistered -and @($executableValues | Where-Object { $null -eq $_ }).Count -ne 0) {
        throw 'WO-036 executable artifact registration is incomplete.'
    }
    $javaPath = $null
    $localLabJar = $null
    $receiverJar = $null
    if (-not $unregistered) {
        $javaPath = Resolve-WO036ExistingPath -Value ([string]$state.javaExecutable) `
            -PathType Leaf -Description 'Java executable'
        $localLabJar = Resolve-WO036ExistingPath -Value ([string]$state.localLabJar) `
            -PathType Leaf -Description 'Local Lab executable JAR'
        $receiverJar = Resolve-WO036ExistingPath -Value ([string]$state.receiverJar) `
            -PathType Leaf -Description 'receiver executable JAR'
        if ($state.localLabJarSha256 -notmatch '^[0-9a-f]{64}$' `
                -or $state.receiverJarSha256 -notmatch '^[0-9a-f]{64}$' `
                -or (Get-WO036FileSha256Hex -Path $localLabJar) -cne
                    [string]$state.localLabJarSha256 `
                -or (Get-WO036FileSha256Hex -Path $receiverJar) -cne
                    [string]$state.receiverJarSha256) {
            throw 'WO-036 executable JAR identity is invalid.'
        }
    }

    foreach ($database in @($state.receiver, $state.localLab)) {
        if ((-not $AllowInfrastructureNotReady `
                    -and $database.databaseContainer -notmatch '^[0-9a-f]{12,64}$') `
                -or $database.databaseUser -notmatch '^[a-z][a-z0-9_]{0,30}$' `
                -or $database.containerRole -notmatch
                    '^(betting-project-postgres|local-lab-postgres)$') {
            throw 'WO-036 database ownership identity is invalid.'
        }
        Assert-WO036SecretValue -Value ([string]$database.databasePassword) `
            -Description 'database credential'
    }
    if ([int]$state.receiver.databasePort -ne 5433 `
            -or [int]$state.receiver.serverPort -ne 8444 `
            -or $state.receiver.databaseName -cne 'betting_wo036' `
            -or [int]$state.localLab.databasePort -ne 5432 `
            -or [int]$state.localLab.serverPort -ne 8087 `
            -or $state.localLab.sourceDatabase -cne 'sofascore_local_lab_wo036_a' `
            -or $state.localLab.targetDatabase -cne 'sofascore_local_lab_wo036_b' `
            -or $state.localLab.sourceDatabase -eq $state.localLab.targetDatabase) {
        throw 'WO-036 database or listener topology is invalid.'
    }

    $keyStore = Resolve-WO036ExistingPath -Value ([string]$state.receiver.keyStorePath) `
        -PathType Leaf -Description 'receiver key store'
    $trustStore = Resolve-WO036ExistingPath -Value ([string]$state.receiver.trustStorePath) `
        -PathType Leaf -Description 'receiver trust store'
    $exportDirectory = Resolve-WO036ExistingPath `
        -Value ([string]$state.localLab.exportDirectory) -PathType Container `
        -Description 'shared export directory'
    $markerPath = Resolve-WO036ExistingPath -Value ([string]$state.markerPath) `
        -PathType Leaf -Description 'ownership marker'
    $environmentPath = Resolve-WO036ExistingPath `
        -Value ([string]$state.environmentPath) -PathType Leaf `
        -Description 'private campaign environment'
    $composePath = Resolve-WO036ExistingPath -Value ([string]$state.composePath) `
        -PathType Leaf -Description 'WO-036 compose definition'
    $serverPublic = Resolve-WO036ExistingPath `
        -Value ([string]$state.pki.serverCertificatePublic) -PathType Leaf `
        -Description 'receiver public certificate'
    $clientPublic = Resolve-WO036ExistingPath `
        -Value ([string]$state.pki.clientCertificatePublic) -PathType Leaf `
        -Description 'sender public certificate'
    foreach ($privatePath in @(
            $keyStore, $trustStore, $exportDirectory, $markerPath,
            $environmentPath, $serverPublic, $clientPublic)) {
        if (-not (Test-WO036PathWithin -Candidate $privatePath -Root $privateRoot)) {
            throw 'WO-036 private runtime artifacts must remain under the private state root.'
        }
        Assert-WO036NoReparsePathChain -Candidate $privatePath -Root $privateRoot
        Assert-WO036PrivateAcl -Path $privatePath
    }
    Assert-WO036SecretValue -Value ([string]$state.receiver.keyStorePassword) `
        -Description 'receiver key-store credential'
    Assert-WO036SecretValue -Value ([string]$state.receiver.trustStorePassword) `
        -Description 'receiver trust-store credential'

    $expectedCompose = [IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot `
        'compose.wo036.yml'))
    if (-not $composePath.Equals($expectedCompose,
                [StringComparison]::OrdinalIgnoreCase) `
            -or [IO.Path]::GetFileName($markerPath) -cne '.wo036-owner.json' `
            -or [IO.Path]::GetFileName($environmentPath) -cne
                'campaign.private.env' `
            -or -not [IO.Path]::GetFullPath([string]$state.pki.serverKeyStore).Equals(
                $keyStore, [StringComparison]::OrdinalIgnoreCase) `
            -or -not [IO.Path]::GetFullPath([string]$state.pki.receiverTrustStore).Equals(
                $trustStore, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'WO-036 state artifact topology is inconsistent.'
    }
    Assert-WO036PrivateAcl -Path $markerPath
    Assert-WO036PrivateAcl -Path $environmentPath
    $markerBytes = [IO.File]::ReadAllBytes($markerPath)
    try {
        $marker = $script:Utf8NoBom.GetString($markerBytes) | ConvertFrom-Json -Depth 4
        Assert-WO036ExactProperties -Value $marker -Description 'ownership marker' `
            -Expected @('schemaVersion', 'workOrder', 'runId', 'ownerSid',
                'ownershipSha256', 'composeProjectName', 'createdAtUtc')
        if ([int]$marker.schemaVersion -ne 1 `
                -or $marker.workOrder -cne $state.workOrder `
                -or $marker.runId -cne $state.runId `
                -or $marker.ownerSid -cne $state.ownerSid `
                -or $marker.ownershipSha256 -cne $state.ownershipSha256 `
                -or $marker.composeProjectName -cne $state.composeProjectName `
                -or $marker.createdAtUtc -cne $state.createdAtUtc) {
            throw 'marker mismatch'
        }
    }
    catch {
        throw 'WO-036 ownership marker is invalid or inconsistent.'
    }
    finally {
        [Array]::Clear($markerBytes, 0, $markerBytes.Length)
    }

    $certificateSha256 = [string]$state.localLab.clientCertificateSha256
    if ($certificateSha256 -notmatch '^[0-9a-f]{64}$' `
            -or $certificateSha256 -cne
                [string]$state.receiver.clientCertificateSha256 `
            -or $certificateSha256 -cne
                [string]$state.pki.clientCertificateSha256) {
        throw 'WO-036 mTLS certificate identity is invalid.'
    }

    return [pscustomobject]@{
        Config = $state
        StatePath = $resolvedState
        PrivateRoot = [IO.Path]::GetFullPath($privateRoot)
        JavaPath = $javaPath
        LocalLabJar = $localLabJar
        ReceiverJar = $receiverJar
        ReceiverKeyStore = $keyStore
        ReceiverTrustStore = $trustStore
        ExportDirectory = $exportDirectory
        DockerPath = $dockerPath
        DockerEndpoint = [string]$dockerEndpointProof.Endpoint
        DockerEndpointPath = [string]$dockerEndpointProof.Path
        DockerEndpointSha256 = [string]$dockerEndpointProof.Sha256
    }
}

function Write-WO036StateConfig {
    param([Parameter(Mandatory = $true)][object]$State)

    $temporaryPath = "$($State.StatePath).tools.partial"
    if (Test-Path -LiteralPath $temporaryPath) {
        throw 'WO-036 private-state update has a residual partial file.'
    }
    try {
        Write-WO036PrivateJson -Path $temporaryPath -Value $State.Config -CreateNew
        Move-Item -LiteralPath $temporaryPath -Destination $State.StatePath -Force
        Assert-WO036PrivateAcl -Path $State.StatePath
    }
    catch {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
        throw 'WO-036 private-state atomic update failed.'
    }
}

function Invoke-WO036GitScalar {
    param(
        [Parameter(Mandatory = $true)][string]$Repository,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowEmpty
    )

    try {
        $gitCommands = @(Get-Command git.exe -CommandType Application -ErrorAction Stop)
        if ($gitCommands.Count -lt 1) {
            throw 'Git executable not found'
        }
        $gitPath = Resolve-WO036ExistingPath -Value $gitCommands[0].Source `
            -PathType Leaf -Description 'Git executable'
        $value = (& $gitPath -c "safe.directory=$Repository" -C $Repository `
            @Arguments 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or (-not $AllowEmpty `
                -and [string]::IsNullOrWhiteSpace($value)) -or $value.Length -gt 4096) {
            throw 'Git command failed'
        }
        return $value
    }
    catch {
        throw 'WO-036 Git provenance could not be proven.'
    }
}

function Assert-WO036GitArtifactProvenance {
    param(
        [Parameter(Mandatory = $true)][string]$LocalLabJar,
        [Parameter(Mandatory = $true)][string]$ReceiverRepositoryRoot,
        [Parameter(Mandatory = $true)][string]$ReceiverJar
    )

    $receiverRoot = Resolve-WO036ExistingPath -Value $ReceiverRepositoryRoot `
        -PathType Container -Description 'receiver repository root'
    $expectedLocalJar = [IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot `
        'target\betting-sofascore-local-lab-0.1.0-SNAPSHOT.jar'))
    $expectedReceiverJar = [IO.Path]::GetFullPath((Join-Path $receiverRoot `
        'target\betting-project-0.1.0-SNAPSHOT.jar'))
    if (-not $LocalLabJar.Equals($expectedLocalJar,
                [StringComparison]::OrdinalIgnoreCase) `
            -or -not $ReceiverJar.Equals($expectedReceiverJar,
                [StringComparison]::OrdinalIgnoreCase)) {
        throw 'WO-036 executable JAR does not belong to an exact expected build target.'
    }

    $localTop = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('rev-parse', '--show-toplevel')
    $localBranch = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('branch', '--show-current')
    $localStatus = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('status', '--porcelain=v1', '--untracked-files=all') -AllowEmpty
    if (-not [IO.Path]::GetFullPath($localTop).Equals($script:RepositoryRoot,
                [StringComparison]::OrdinalIgnoreCase) `
            -or $localBranch -cne $script:ExpectedLocalLabBranch `
            -or -not [string]::IsNullOrEmpty($localStatus)) {
        throw 'WO-036 Local Lab worktree provenance or cleanliness is invalid.'
    }
    [void](Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('merge-base', '--is-ancestor',
            $script:ExpectedLocalLabSenderCommit, 'HEAD') -AllowEmpty)

    $receiverTop = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('rev-parse', '--show-toplevel')
    $receiverHead = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('rev-parse', 'HEAD')
    $receiverBranch = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('branch', '--show-current')
    $receiverStatus = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('status', '--porcelain=v1', '--untracked-files=all') -AllowEmpty
    if (-not [IO.Path]::GetFullPath($receiverTop).Equals($receiverRoot,
                [StringComparison]::OrdinalIgnoreCase) `
            -or $receiverHead -cne $script:ExpectedReceiverHeadCommit `
            -or $receiverBranch -cne $script:ExpectedReceiverBranch `
            -or -not [string]::IsNullOrEmpty($receiverStatus)) {
        throw 'WO-036 receiver worktree provenance or cleanliness is invalid.'
    }
    [void](Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('merge-base', '--is-ancestor',
            $script:ExpectedReceiverImplementationCommit, 'HEAD') -AllowEmpty)
}

function Get-WO036ToolingFileProof {
    $proof = @()
    foreach ($relativePath in $script:ToolingRelativePaths) {
        $absolutePath = Resolve-WO036ExistingPath `
            -Value ([IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $relativePath))) `
            -PathType Leaf -Description 'qualified WO-036 tooling file'
        if (-not (Test-WO036PathWithin -Candidate $absolutePath `
                -Root $script:RepositoryRoot)) {
            throw 'WO-036 tooling file escapes the Local Lab worktree.'
        }
        Assert-WO036NoReparsePathChain -Candidate $absolutePath `
            -Root $script:RepositoryRoot
        $proof += [pscustomobject][ordered]@{
            relativePath = $relativePath.Replace('\', '/')
            sha256 = Get-WO036FileSha256Hex -Path $absolutePath
        }
    }
    return @($proof)
}

function Assert-WO036RegisteredTooling {
    param([Parameter(Mandatory = $true)][object]$State)

    $registration = Read-WO036PrivateToolJson -State $State `
        -FileName 'registered-tooling.json'
    Assert-WO036ExactProperties -Value $registration `
        -Description 'registered tooling manifest' -Expected @(
            'schemaVersion', 'workOrder', 'registeredAtUtc', 'status',
            'localLabBranch', 'localLabHead', 'receiverBranch', 'receiverHead',
            'receiverRepositoryRoot', 'javaSha256', 'localLabJarSha256',
            'receiverJarSha256', 'dockerEndpointSha256', 'toolingFiles')
    if ($registration.schemaVersion -cne '1.0' `
            -or $registration.workOrder -cne $script:WorkOrder `
            -or $registration.status -cne 'FROZEN_ONE_SHOT' `
            -or $registration.localLabBranch -cne $script:ExpectedLocalLabBranch `
            -or $registration.receiverBranch -cne $script:ExpectedReceiverBranch `
            -or $registration.receiverHead -cne $script:ExpectedReceiverHeadCommit `
            -or $registration.localLabHead -notmatch '^[0-9a-f]{40}$' `
            -or $registration.javaSha256 -notmatch '^[0-9a-f]{64}$' `
            -or $registration.localLabJarSha256 -cne
                [string]$State.Config.localLabJarSha256 `
            -or $registration.receiverJarSha256 -cne
                [string]$State.Config.receiverJarSha256 `
            -or $registration.dockerEndpointSha256 -cne
                [string]$State.DockerEndpointSha256 `
            -or (Get-WO036FileSha256Hex -Path $State.JavaPath) -cne
                [string]$registration.javaSha256) {
        throw 'WO-036 registered executable/tooling identity is invalid.'
    }

    $expectedTooling = @(Get-WO036ToolingFileProof)
    $registeredTooling = @($registration.toolingFiles)
    if ($registeredTooling.Count -ne $expectedTooling.Count) {
        throw 'WO-036 registered tooling file set is incomplete.'
    }
    for ($index = 0; $index -lt $expectedTooling.Count; $index++) {
        Assert-WO036ExactProperties -Value $registeredTooling[$index] `
            -Description 'registered tooling file' -Expected @('relativePath', 'sha256')
        if ($registeredTooling[$index].relativePath -cne
                $expectedTooling[$index].relativePath `
                -or $registeredTooling[$index].sha256 -cne
                    $expectedTooling[$index].sha256) {
            throw 'WO-036 tooling changed after its one-shot registration.'
        }
    }

    $localBranch = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('branch', '--show-current')
    $localHead = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('rev-parse', 'HEAD')
    $localStatus = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('status', '--porcelain=v1', '--untracked-files=all') -AllowEmpty
    if ($localBranch -cne $script:ExpectedLocalLabBranch `
            -or -not [string]::IsNullOrEmpty($localStatus)) {
        throw 'WO-036 Local Lab worktree changed after tooling registration.'
    }
    [void](Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('merge-base', '--is-ancestor',
            [string]$registration.localLabHead, $localHead) -AllowEmpty)
    $changed = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
        -Arguments @('diff', '--name-only',
            "$($registration.localLabHead)..$localHead") -AllowEmpty
    $changedPaths = @($changed -split '\r?\n' | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_)
    })
    if (@($changedPaths | Where-Object {
            $_ -cne $script:AllowedPostFreezeManifestPath
        }).Count -ne 0 -or $changedPaths.Count -gt 1) {
        throw 'WO-036 post-registration history is outside the one manifest allowance.'
    }

    $receiverRoot = Resolve-WO036ExistingPath `
        -Value ([string]$registration.receiverRepositoryRoot) `
        -PathType Container -Description 'registered receiver repository root'
    $receiverBranch = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('branch', '--show-current')
    $receiverHead = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('rev-parse', 'HEAD')
    $receiverStatus = Invoke-WO036GitScalar -Repository $receiverRoot `
        -Arguments @('status', '--porcelain=v1', '--untracked-files=all') -AllowEmpty
    if ($receiverBranch -cne $script:ExpectedReceiverBranch `
            -or $receiverHead -cne [string]$registration.receiverHead `
            -or -not [string]::IsNullOrEmpty($receiverStatus)) {
        throw 'WO-036 receiver worktree changed after tooling registration.'
    }

    $registrationPath = Join-Path (Get-WO036PrivateToolsStateDirectory -State $State) `
        'registered-tooling.json'
    return [pscustomobject][ordered]@{
        localLabRegisteredHead = [string]$registration.localLabHead
        localLabCurrentHead = $localHead
        receiverHead = $receiverHead
        javaSha256 = [string]$registration.javaSha256
        localLabJarSha256 = [string]$registration.localLabJarSha256
        receiverJarSha256 = [string]$registration.receiverJarSha256
        dockerEndpointSha256 = [string]$registration.dockerEndpointSha256
        toolingFiles = $expectedTooling
        registrationManifestSha256 = Get-WO036FileSha256Hex -Path $registrationPath
        allowedPostFreezeManifestPath = $script:AllowedPostFreezeManifestPath
    }
}

function Register-WO036ExecutableArtifactsCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][string]$JavaExecutable,
        [Parameter(Mandatory = $true)][string]$LocalLabJar,
        [Parameter(Mandatory = $true)][string]$ExpectedLocalLabJarSha256,
        [Parameter(Mandatory = $true)][string]$ReceiverRepositoryRoot,
        [Parameter(Mandatory = $true)][string]$ReceiverJar,
        [Parameter(Mandatory = $true)][string]$ExpectedReceiverJarSha256
    )

    $state = Read-WO036PrivateState -StatePath $StatePath `
        -AllowUnregisteredExecutables -AllowInfrastructureNotReady
    if ($null -ne $state.Config.javaExecutable `
            -or $null -ne $state.Config.localLabJar `
            -or $null -ne $state.Config.receiverJar) {
        throw 'WO-036 executable artifacts have already been registered.'
    }
    $java = Resolve-WO036ExistingPath -Value $JavaExecutable -PathType Leaf `
        -Description 'Java executable'
    $labJar = Resolve-WO036ExistingPath -Value $LocalLabJar -PathType Leaf `
        -Description 'Local Lab executable JAR'
    $receiver = Resolve-WO036ExistingPath -Value $ReceiverJar -PathType Leaf `
        -Description 'receiver executable JAR'
    if (-not (Test-WO036PathWithin -Candidate $labJar -Root $script:RepositoryRoot) `
            -or [IO.Path]::GetExtension($labJar) -cne '.jar' `
            -or [IO.Path]::GetExtension($receiver) -cne '.jar') {
        throw 'WO-036 executable JAR location is invalid.'
    }
    Assert-WO036GitArtifactProvenance -LocalLabJar $labJar `
        -ReceiverRepositoryRoot $ReceiverRepositoryRoot -ReceiverJar $receiver
    $actualLocalLabJarSha256 = Get-WO036FileSha256Hex -Path $labJar
    $actualReceiverJarSha256 = Get-WO036FileSha256Hex -Path $receiver
    if ($ExpectedLocalLabJarSha256 -notmatch '^[0-9a-f]{64}$' `
            -or $ExpectedReceiverJarSha256 -notmatch '^[0-9a-f]{64}$' `
            -or $actualLocalLabJarSha256 -cne $ExpectedLocalLabJarSha256 `
            -or $actualReceiverJarSha256 -cne $ExpectedReceiverJarSha256) {
        throw 'WO-036 JAR hashes do not match the separately qualified clean builds.'
    }
    try {
        $javaVersion = (& $java --version 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0 `
                -or $javaVersion -notmatch '(?m)^\s*(?:java|openjdk)\s+(?:version\s+)?[\"]?25(?:[.\-\"\s]|$)') {
            throw 'not Java 25'
        }
    }
    catch {
        throw 'WO-036 requires the exact Java 25 executable.'
    }
    $state.Config.javaExecutable = $java
    $state.Config.localLabJar = $labJar
    $state.Config.localLabJarSha256 = $actualLocalLabJarSha256
    $state.Config.receiverJar = $receiver
    $state.Config.receiverJarSha256 = $actualReceiverJarSha256
    Write-WO036StateConfig -State $state
    $state = Read-WO036PrivateState -StatePath $StatePath
    $receiverRoot = Resolve-WO036ExistingPath -Value $ReceiverRepositoryRoot `
        -PathType Container -Description 'receiver repository root'
    $registration = [ordered]@{
        schemaVersion = '1.0'
        workOrder = $script:WorkOrder
        registeredAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
        status = 'FROZEN_ONE_SHOT'
        localLabBranch = $script:ExpectedLocalLabBranch
        localLabHead = Invoke-WO036GitScalar -Repository $script:RepositoryRoot `
            -Arguments @('rev-parse', 'HEAD')
        receiverBranch = $script:ExpectedReceiverBranch
        receiverHead = Invoke-WO036GitScalar -Repository $receiverRoot `
            -Arguments @('rev-parse', 'HEAD')
        receiverRepositoryRoot = $receiverRoot
        javaSha256 = Get-WO036FileSha256Hex -Path $java
        localLabJarSha256 = [string]$state.Config.localLabJarSha256
        receiverJarSha256 = [string]$state.Config.receiverJarSha256
        dockerEndpointSha256 = [string]$state.DockerEndpointSha256
        toolingFiles = @(Get-WO036ToolingFileProof)
    }
    Write-WO036PrivateJson -Path (Join-Path `
        (Get-WO036PrivateToolsStateDirectory -State $state) `
        'registered-tooling.json') -Value $registration -CreateNew
    Write-Output 'WO036_EXECUTABLE_ARTIFACT_REGISTRATION=PASS'
    Write-Output 'WO036_JAVA_TARGET=25'
}

function Get-WO036PrivateToolsStateDirectory {
    param([Parameter(Mandatory = $true)][object]$State)
    $directory = Join-Path $State.PrivateRoot 'state'
    [void][IO.Directory]::CreateDirectory($directory)
    Assert-WO036NoReparsePathChain -Candidate $directory -Root $State.PrivateRoot
    Assert-WO036PrivateAcl -Path $directory
    return $directory
}

function Write-WO036PrivateJson {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object]$Value,
        [switch]$CreateNew
    )

    $json = ($Value | ConvertTo-Json -Depth 8 -Compress) + "`n"
    $bytes = $script:Utf8NoBom.GetBytes($json)
    $mode = if ($CreateNew) { [IO.FileMode]::CreateNew } else { [IO.FileMode]::Create }
    try {
        $stream = [IO.FileStream]::new(
            $Path, $mode, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
    if (-not (Test-WO036PathWithin -Candidate $Path -Root $script:RepositoryRoot)) {
        Assert-WO036PrivateAcl -Path $Path
    }
}

function Update-WO036PrivateJsonAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object]$Value
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'WO-036 atomic private JSON update requires one existing file.'
    }
    $partial = "$Path.partial"
    if (Test-Path -LiteralPath $partial) {
        throw 'WO-036 atomic private JSON update found a residual partial file.'
    }
    try {
        Write-WO036PrivateJson -Path $partial -Value $Value -CreateNew
        Move-Item -LiteralPath $partial -Destination $Path -Force
        Assert-WO036PrivateAcl -Path $Path
    }
    catch {
        if (Test-Path -LiteralPath $partial) {
            Remove-Item -LiteralPath $partial -Force
        }
        throw 'WO-036 atomic private JSON update failed.'
    }
}

function Get-WO036OwnedProcess {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][string]$Component,
        [switch]$AllowMissing
    )

    $records = @($State.Config.ownedProcesses | Where-Object {
        $_.Component -eq $Component
    })
    if ($records.Count -eq 0) {
        if ($AllowMissing) { return $null }
        throw "WO-036 $Component process ownership record is missing."
    }
    if ($records.Count -ne 1) {
        throw "WO-036 $Component process ownership is ambiguous."
    }
    try {
        $record = $records[0]
        $expectedJar = if ($Component -eq 'Receiver') {
            $State.ReceiverJar
        }
        else {
            $State.LocalLabJar
        }
        if ($record.component -ne $Component `
                -or $record.instanceToken -notmatch
                    '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' `
                -or [int]$record.Pid -lt 1 `
                -or $record.Role -ne $(if ($Component -eq 'Receiver') {
                    'betting-project'
                }
                else { 'local-lab' }) `
                -or $record.CommandLineSha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'invalid record'
        }
        $process = Get-CimInstance Win32_Process -Filter (
            "ProcessId={0}" -f [int]$record.Pid) -ErrorAction SilentlyContinue
        if ($null -eq $process) {
            return $null
        }
        $executable = [IO.Path]::GetFullPath([string]$process.ExecutablePath)
        $recordedExecutable = [IO.Path]::GetFullPath([string]$record.ExecutablePath)
        $live = Get-Process -Id ([int]$record.Pid) -ErrorAction Stop
        $recordedStart = ConvertTo-WO036UtcDateTime `
            -Value $record.StartTimeUtc -Description "$Component process start time"
        $marker = "-Dwo036.instance=$($record.instanceToken)"
        if (-not $executable.Equals($State.JavaPath,
                    [StringComparison]::OrdinalIgnoreCase) `
                -or -not $recordedExecutable.Equals($State.JavaPath,
                    [StringComparison]::OrdinalIgnoreCase) `
                -or [Math]::Abs((
                    $live.StartTime.ToUniversalTime() - $recordedStart).TotalSeconds) -gt 1 `
                -or [string]$process.CommandLine -notlike "*$marker*" `
                -or [string]$process.CommandLine -notlike "*$expectedJar*" `
                -or (Get-WO036Sha256Hex -Bytes (
                    $script:Utf8NoBom.GetBytes([string]$process.CommandLine))) -cne
                    [string]$record.CommandLineSha256) {
            throw 'identity mismatch'
        }
        return $process
    }
    catch {
        throw "WO-036 $Component process identity cannot be proven."
    }
}

function Assert-WO036ContainerOwnership {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][object]$Database
    )

    $dockerArguments = @(Get-WO036DockerArguments -State $State)
    try {
        $inspection = (& $State.DockerPath @dockerArguments inspect --format `
            '{{.State.Running}}|{{json .Config.Labels}}' `
            ([string]$Database.databaseContainer) 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $inspection -notmatch '^(true|false)\|(\{.*\})$') {
            throw 'inspect failed'
        }
        if ($Matches[1] -ne 'true') {
            throw 'not running'
        }
        $labels = $Matches[2] | ConvertFrom-Json
        if ($labels.'com.bettingproject.work-order' -ne $script:WorkOrder `
                -or $labels.'com.bettingproject.wo036.run-id' -ne
                    $State.Config.runId `
                -or $labels.'com.bettingproject.wo036.ownership-sha256' -ne
                    $State.Config.ownershipSha256 `
                -or $labels.'com.bettingproject.wo036.role' -ne
                    $Database.containerRole `
                -or $labels.'com.docker.compose.project' -ne
                    $State.Config.composeProjectName) {
            throw 'label mismatch'
        }
    }
    catch {
        throw 'WO-036 database container ownership cannot be proven.'
    }
}

function Get-WO036DockerArguments {
    param([Parameter(Mandatory = $true)][object]$State)

    try {
        $proof = Read-WO036PinnedDockerEndpoint -PrivateRoot $State.PrivateRoot `
            -ExpectedSha256 ([string]$State.DockerEndpointSha256)
        if ($proof.Endpoint -cne [string]$State.DockerEndpoint `
                -or -not $proof.Path.Equals([string]$State.DockerEndpointPath,
                    [StringComparison]::OrdinalIgnoreCase)) {
            throw 'endpoint identity changed'
        }
        return @('--host', [string]$proof.Endpoint)
    }
    catch {
        throw 'WO-036 Docker endpoint is not one local Windows named pipe.'
    }
}

function Invoke-WO036Psql {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][object]$Database,
        [Parameter(Mandatory = $true)][string]$DatabaseName,
        [Parameter(Mandatory = $true)][string]$Sql
    )

    Assert-WO036ContainerOwnership -State $State -Database $Database
    $dockerArguments = @(Get-WO036DockerArguments -State $State)
    try {
        $output = (& $State.DockerPath @dockerArguments exec `
            ([string]$Database.databaseContainer) `
            psql -X --no-psqlrc --quiet --tuples-only --no-align `
            --set ON_ERROR_STOP=1 -U ([string]$Database.databaseUser) `
            -d $DatabaseName -c $Sql 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw 'psql failed'
        }
        return $output
    }
    catch {
        throw 'WO-036 bounded PostgreSQL query failed.'
    }
}

function ConvertFrom-WO036PsqlJson {
    param([Parameter(Mandatory = $true)][string]$Value)
    try {
        if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Length -gt 32768) {
            throw 'invalid result'
        }
        return $Value | ConvertFrom-Json -Depth 8
    }
    catch {
        throw 'WO-036 PostgreSQL proof was not one bounded JSON value.'
    }
}

function Get-WO036DatabaseProof {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)]
        [ValidateSet('LocalLabA', 'LocalLabB', 'Receiver')][string]$Role,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )

    $id = $ExportId.ToString()
    if ($Role -eq 'Receiver') {
        $database = $State.Config.receiver
        $sql = @"
with receipt as (
    select * from j7_import_receipt where export_id = '$id'::uuid
), payload as (
    select payload.* from j7_import_payload payload
    join receipt on receipt.id = payload.import_id
), audit as (
    select audit.* from j7_import_audit audit
    join receipt on receipt.id = audit.import_id
), outbox as (
    select outbox.* from outbox_message outbox
    join receipt on outbox.aggregate_type = 'j7_import_receipt'
                and outbox.aggregate_id = receipt.id
                and outbox.destination = 'J7_IMPORT_ACCEPTED'
)
select jsonb_build_object(
    'globalReceiptCount', (select count(*) from j7_import_receipt),
    'globalPayloadCount', (select count(*) from j7_import_payload),
    'globalAuditTotalCount', (select count(*) from j7_import_audit),
    'globalOutboxJ7Count', (select count(*) from outbox_message
        where destination = 'J7_IMPORT_ACCEPTED'),
    'receiptCount', (select count(*) from receipt),
    'payloadCount', (select count(*) from payload),
    'auditImportedCount', (select count(*) from audit where event_type = 'IMPORTED'),
    'auditDuplicateCount', (select count(*) from audit where event_type = 'DUPLICATE'),
    'auditDivergenceCount', (select count(*) from audit where event_type = 'DIVERGENCE_REJECTED'),
    'auditTotalCount', (select count(*) from audit),
    'outboxCount', (select count(*) from outbox),
    'outboxPendingCount', (select count(*) from outbox where status = 'PENDING'),
    'outboxAttemptCount', coalesce((select max(attempt_count) from outbox), 0),
    'payloadSizeBytes', coalesce((select max(payload_size_bytes) from receipt), 0),
    'fileSha256', coalesce((select max(file_sha256) from receipt), ''),
    'dataSha256', coalesce((select max(data_sha256) from receipt), ''),
    'payloadComputedSha256', coalesce((select max(encode(sha256(payload), 'hex')) from payload), ''),
    'payloadLengthBytes', coalesce((select max(octet_length(payload)) from payload), 0),
    'schemaCount', (select count(*) from receipt
        where schema_id = 'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1'
          and schema_version = '1.0.0'
          and validation_status = 'HUMAN_VALIDATED'),
    'retentionCount', (select count(*) from receipt
        where payload_expires_at = received_at + interval '30 days')
)::text;
"@
        return ConvertFrom-WO036PsqlJson -Value (Invoke-WO036Psql -State $State `
            -Database $database -DatabaseName ([string]$database.databaseName) -Sql $sql)
    }

    $database = $State.Config.localLab
    $databaseName = if ($Role -eq 'LocalLabA') {
        [string]$database.sourceDatabase
    }
    else {
        [string]$database.targetDatabase
    }
    $sql = @"
with manifest as (
    select * from export_manifest where export_uuid = '$id'::uuid
), delivery as (
    select delivery.* from j7_delivery delivery
    join manifest on manifest.id = delivery.export_manifest_id
), attempt as (
    select attempt.* from j7_delivery_attempt attempt
    join delivery on delivery.id = attempt.delivery_id
), result as (
    select result.* from j7_delivery_attempt_result result
    join attempt on attempt.id = result.attempt_id
)
select jsonb_build_object(
    'manifestCount', (select count(*) from manifest),
    'humanValidatedCount', (select count(*) from manifest
        where validation_status = 'HUMAN_VALIDATED'),
    'syntheticOnlyCount', (select count(*) from manifest
        where jsonb_array_length(source_observations) = 5
          and exists (select 1 from jsonb_array_elements(source_observations) source
                      where source->>'sourceKind' = 'SYNTHETIC_FIXTURE')
          and not exists (select 1 from jsonb_array_elements(source_observations) source
                          where (source->>'availability' = 'MISSING'
                                 and source->'sourceKind' <> 'null'::jsonb)
                             or (source->>'availability' <> 'MISSING'
                                 and source->>'sourceKind' <> 'SYNTHETIC_FIXTURE'))),
    'fileSizeBytes', coalesce((select max(content_size_bytes) from manifest), 0),
    'fileSha256', coalesce((select max(content_sha256) from manifest), ''),
    'dataSha256', coalesce((select max(data_sha256) from manifest), ''),
    'deliveryCount', (select count(*) from delivery),
    'deliveryState', coalesce((select max(current_state) from delivery), ''),
    'attemptCount', (select count(*) from attempt),
    'resultCount', (select count(*) from result),
    'httpStatus', coalesce((select max(http_status) from result), 0),
    'safeResultCode', coalesce((select max(safe_result_code) from result), '')
)::text;
"@
    return ConvertFrom-WO036PsqlJson -Value (Invoke-WO036Psql -State $State `
        -Database $database -DatabaseName $databaseName -Sql $sql)
}

function Get-WO036ExportPath {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )
    $sql = "select export_path from export_manifest where export_uuid = '$ExportId'::uuid and validation_status = 'HUMAN_VALIDATED';"
    $relative = Invoke-WO036Psql -State $State -Database $State.Config.localLab `
        -DatabaseName ([string]$State.Config.localLab.sourceDatabase) -Sql $sql
    if ($relative -notmatch
            '^j7-[0-9a-f-]{36}-[0-9a-f-]{36}\.validated\.json$' `
            -or $relative -notlike "*-$ExportId.validated.json") {
        throw 'WO-036 validated export path is not canonical.'
    }
    $candidate = [IO.Path]::GetFullPath((Join-Path $State.ExportDirectory $relative))
    if (-not (Test-WO036PathWithin -Candidate $candidate -Root $State.ExportDirectory) `
            -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw 'WO-036 validated export file is missing or not confined.'
    }
    Assert-WO036NoReparsePathChain -Candidate $candidate -Root $State.ExportDirectory
    Assert-WO036PrivateAcl -Path $State.ExportDirectory
    Assert-WO036PrivateAcl -Path $candidate
    return $candidate
}

function Get-WO036ClientCertificate {
    param([Parameter(Mandatory = $true)][string]$CertificateSha256)

    $store = [Security.Cryptography.X509Certificates.X509Store]::new(
        [Security.Cryptography.X509Certificates.StoreName]::My,
        [Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser)
    try {
        $store.Open([Security.Cryptography.X509Certificates.OpenFlags]::ReadOnly)
        $matches = @($store.Certificates | Where-Object {
            $_.HasPrivateKey -and
            (Get-WO036Sha256Hex -Bytes $_.RawData) -ceq $CertificateSha256
        })
        if ($matches.Count -ne 1) {
            throw 'not exactly one certificate'
        }
        return [Security.Cryptography.X509Certificates.X509Certificate2]::new(
            $matches[0])
    }
    catch {
        throw 'WO-036 client certificate identity cannot be resolved exactly.'
    }
    finally {
        $store.Close()
        $store.Dispose()
    }
}

function New-WO036HttpClient {
    param([string]$ClientCertificateSha256)

    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    $handler.UseProxy = $false
    $handler.AutomaticDecompression = [Net.DecompressionMethods]::None
    $handler.SslProtocols =
        [Security.Authentication.SslProtocols]::Tls12 -bor
        [Security.Authentication.SslProtocols]::Tls13
    if (-not [string]::IsNullOrEmpty($ClientCertificateSha256)) {
        $certificate = Get-WO036ClientCertificate `
            -CertificateSha256 $ClientCertificateSha256
        [void]$handler.ClientCertificates.Add($certificate)
    }
    $client = [Net.Http.HttpClient]::new($handler, $true)
    $client.Timeout = [TimeSpan]::FromSeconds(10)
    return $client
}

function Test-WO036PortIsFree {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    return -not ($listeners | Where-Object { $_.Port -eq $Port })
}

function Get-WO036CollisionAuditProof {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][guid]$ExportId,
        [Parameter(Mandatory = $true)][string]$MutatedFileSha256,
        [Parameter(Mandatory = $true)][string]$DataSha256
    )

    if ($MutatedFileSha256 -notmatch '^[0-9a-f]{64}$' `
            -or $DataSha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'WO-036 collision audit identity is malformed.'
    }
    $id = $ExportId.ToString()
    $idempotencyKey = "j7:$id`:sha256:$MutatedFileSha256"
    $sql = @"
with receipt as (
    select id from j7_import_receipt where export_id = '$id'::uuid
), divergence as (
    select audit.* from j7_import_audit audit
    join receipt on receipt.id = audit.import_id
    where audit.event_type = 'DIVERGENCE_REJECTED'
)
select jsonb_build_object(
    'divergenceCount', (select count(*) from divergence),
    'correlationCount', (select count(*) from divergence
        where request_idempotency_key = '$idempotencyKey'
          and observed_file_sha256 = '$MutatedFileSha256'
          and observed_data_sha256 = '$DataSha256'
          and reason_code = 'EXPORT_ID_DIVERGENCE'),
    'reasonCount', (select count(*) from divergence
        where reason_code = 'EXPORT_ID_DIVERGENCE')
)::text;
"@
    $proof = ConvertFrom-WO036PsqlJson -Value (Invoke-WO036Psql -State $State `
        -Database $State.Config.receiver `
        -DatabaseName ([string]$State.Config.receiver.databaseName) -Sql $sql)
    return [pscustomobject][ordered]@{
        divergenceCount = [int]$proof.divergenceCount
        correlationCount = [int]$proof.correlationCount
        reasonCount = [int]$proof.reasonCount
        reasonCode = 'EXPORT_ID_DIVERGENCE'
        idempotencyKeySha256 = Get-WO036Sha256Hex -Bytes (
            $script:Utf8NoBom.GetBytes($idempotencyKey))
    }
}

function Test-WO036ExactLoopbackListener {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][int]$ProcessId
    )

    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port `
        -ErrorAction SilentlyContinue)
    return $listeners.Count -eq 1 -and
        [string]$listeners[0].LocalAddress -ceq '127.0.0.1' -and
        [int]$listeners[0].LocalPort -eq $Port -and
        [int]$listeners[0].OwningProcess -eq $ProcessId
}

function Wait-WO036LoopbackListener {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][int]$ProcessId
    )
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
            return $false
        }
        if (Test-WO036ExactLoopbackListener -Port $Port -ProcessId $ProcessId) {
            return $true
        }
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port `
            -ErrorAction SilentlyContinue)
        if ($listeners.Count -gt 0) { return $false }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-WO036ComponentEnvironment {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)]
        [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
        [string]$Component
    )

    $common = @{
        'JAVA_TOOL_OPTIONS' = ''
        '_JAVA_OPTIONS' = ''
        'JDK_JAVA_OPTIONS' = ''
        'HTTP_PROXY' = ''
        'HTTPS_PROXY' = ''
        'ALL_PROXY' = ''
        'NO_PROXY' = '127.0.0.1,localhost'
        'SSLKEYLOGFILE' = ''
        'NSS_SSLKEYLOGFILE' = ''
        'JDK_TLS_KEYLOGGER' = ''
        'JAVAX_NET_DEBUG' = ''
        'JDK_HTTPCLIENT_HTTPCLIENT_LOG' = ''
        'SPRING_APPLICATION_JSON' = ''
        'SPRING_CONFIG_IMPORT' = ''
        'SPRING_CONFIG_LOCATION' = 'classpath:/'
        'SPRING_CONFIG_ADDITIONAL_LOCATION' = ''
    }
    if ($Component -eq 'Receiver') {
        $receiver = $State.Config.receiver
        $common['SPRING_PROFILES_ACTIVE'] = 'control-api'
        $common['BETTING_J7_RECEIVER_ENABLED'] = 'true'
        $common['BETTING_CONTROL_API_PORT'] = '8444'
        $common['BETTING_J7_RETENTION_DAYS'] = '30'
        $common['BETTING_J7_CLIENT_CERTIFICATE_SHA256_ALLOWLIST'] =
            [string]$receiver.clientCertificateSha256
        $common['BETTING_J7_KEY_STORE'] = $State.ReceiverKeyStore
        $common['BETTING_J7_KEY_STORE_PASSWORD'] = [string]$receiver.keyStorePassword
        $common['BETTING_J7_KEY_STORE_TYPE'] = 'PKCS12'
        $common['BETTING_J7_TRUST_STORE'] = $State.ReceiverTrustStore
        $common['BETTING_J7_TRUST_STORE_PASSWORD'] = [string]$receiver.trustStorePassword
        $common['BETTING_J7_TRUST_STORE_TYPE'] = 'PKCS12'
        $common['BETTING_DB_URL'] = "jdbc:postgresql://127.0.0.1:5433/$($receiver.databaseName)"
        $common['BETTING_DB_USER'] = [string]$receiver.databaseUser
        $common['BETTING_DB_PASSWORD'] = [string]$receiver.databasePassword
        $common['BETTING_OPERATOR_ID'] = 'wo036-synthetic-campaign'
        $common['MANAGEMENT_ENDPOINT_SHUTDOWN_ENABLED'] = 'true'
        $common['MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE'] = 'health,info,flyway,shutdown'
        return $common
    }

    $lab = $State.Config.localLab
    $database = if ($Component -in @('LocalLabPreparation', 'LocalLabA')) {
        [string]$lab.sourceDatabase
    }
    else {
        [string]$lab.targetDatabase
    }
    $common['SPRING_PROFILES_ACTIVE'] = 'local'
    $common['SERVER_ADDRESS'] = '127.0.0.1'
    $common['SERVER_PORT'] = '8087'
    $common['POSTGRES_PORT'] = '5432'
    $common['POSTGRES_DB'] = $database
    $common['POSTGRES_USER'] = [string]$lab.databaseUser
    $common['POSTGRES_PASSWORD'] = [string]$lab.databasePassword
    $common['SOFASCORE_EXPORT_DIR'] = $State.ExportDirectory
    $common['SOFASCORE_ENABLED'] = 'false'
    $common['SOFASCORE_PLAYWRIGHT_ENABLED'] = 'false'
    $common['SOFASCORE_J3_QUALIFICATION_ENABLED'] = 'false'
    $common['SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED'] = 'false'
    $common['SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED'] = 'false'
    $common['SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED'] = 'false'
    $common['SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED'] = 'false'
    $common['SOFASCORE_BASE_URL'] = ''
    $common['SOFASCORE_ALLOWED_ENDPOINTS'] = ''
    $common['OPTIONAL_INTEGRATION_ENABLED'] = 'true'
    $common['OPTIONAL_INTEGRATION_EXECUTION_MODE'] = 'SYNTHETIC_LOOPBACK'
    $common['OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED'] = 'false'
    $common['OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS'] = 'NOT_EVIDENCED'
    $common['OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION'] = 'PASS'
    $common['OPTIONAL_INTEGRATION_SENDER_QUALIFICATION'] = 'PASS'
    $common['OPTIONAL_INTEGRATION_RECEIVER_ORIGIN'] = $script:ReceiverOrigin
    $common['OPTIONAL_INTEGRATION_LOOPBACK_QUALIFICATION'] = 'true'
    $common['OPTIONAL_INTEGRATION_LOOPBACK_ORIGIN'] = $script:ReceiverOrigin
    $common['OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED'] = 'false'
    $common['OPTIONAL_INTEGRATION_MTLS_REQUIRED'] = 'true'
    $common['OPTIONAL_INTEGRATION_MTLS_KEY_STORE_TYPE'] = 'Windows-MY'
    $common['OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256'] =
        [string]$lab.clientCertificateSha256
    $common['MANAGEMENT_ENDPOINT_SHUTDOWN_ENABLED'] = 'true'
    $common['MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE'] = 'health,info,metrics,shutdown'
    return $common
}

function Get-WO036SensitiveProcessEnvironmentNames {
    $pattern = '^(?:SPRING_|SERVER_|MANAGEMENT_|SOFASCORE_|OPTIONAL_INTEGRATION_|BETTING_|POSTGRES_|JNDI_|FLYWAY_|LOGGING_|DEBUG$|TRACE$|HTTP_PROXY$|HTTPS_PROXY$|ALL_PROXY$|NO_PROXY$|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|SSLKEYLOGFILE$|NSS_SSLKEYLOGFILE$|JDK_TLS_KEYLOGGER$|JAVAX_NET_DEBUG$|JDK_HTTPCLIENT_HTTPCLIENT_LOG$)'
    return @([Environment]::GetEnvironmentVariables('Process').Keys |
        Where-Object { [string]$_ -match $pattern } |
        ForEach-Object { [string]$_ })
}

function Read-WO036StableActiveLogBytes {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][ValidateRange(0, 52428800)]
        [long]$MaximumBytes
    )

    $stream = $null
    $bytes = $null
    try {
        $stream = [IO.FileStream]::new(
            $Path,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::ReadWrite,
            65536,
            [IO.FileOptions]::SequentialScan)
        $lengthBefore = $stream.Length
        if ($lengthBefore -lt 0 `
                -or $lengthBefore -gt $MaximumBytes `
                -or $lengthBefore -gt [int]::MaxValue) {
            throw 'log length outside bounded scan envelope'
        }
        $bytes = [byte[]]::new([int]$lengthBefore)
        $offset = 0
        while ($offset -lt $bytes.Length) {
            $read = $stream.Read($bytes, $offset, $bytes.Length - $offset)
            if ($read -le 0) {
                throw 'log reached EOF before its frozen length'
            }
            $offset += $read
        }
        if ($stream.Length -ne $lengthBefore) {
            throw 'log changed during bounded scan'
        }
        $result = $bytes
        $bytes = $null
        return ,$result
    }
    catch {
        if ($null -ne $bytes) {
            [Array]::Clear($bytes, 0, $bytes.Length)
        }
        throw 'WO-036 active private log could not be read as one stable bounded snapshot.'
    }
    finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
    }
}

function Get-WO036PrivateLogRedactionProof {
    param([Parameter(Mandatory = $true)][object]$State)

    $logsDirectory = Join-Path $State.PrivateRoot 'logs'
    if (-not (Test-Path -LiteralPath $logsDirectory -PathType Container)) {
        return [pscustomobject]@{
            FileCount = 0
            FileNames = @()
            SizeBytes = 0L
            ForbiddenOccurrenceCount = 0
        }
    }
    Assert-WO036NoReparsePathChain -Candidate $logsDirectory -Root $State.PrivateRoot
    Assert-WO036PrivateAcl -Path $logsDirectory
    $allowedNames = @($script:CompletedLocalLabLogNames)
    $entries = @(Get-ChildItem -LiteralPath $logsDirectory -Force)
    $files = @($entries | Where-Object { -not $_.PSIsContainer })
    if (@($entries | Where-Object { $_.PSIsContainer }).Count -ne 0 `
            -or @($files | Where-Object { $_.Name -cnotin $allowedNames }).Count -ne 0 `
            -or @($files | Where-Object {
                ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
            }).Count -ne 0) {
        throw 'WO-036 private logs contain an unexpected artifact.'
    }
    $metadataSize = [long]($files | Measure-Object -Property Length -Sum).Sum
    if ($metadataSize -gt 104857600 `
            -or @($files | Where-Object { $_.Length -gt 52428800 }).Count -ne 0) {
        throw 'WO-036 private logs exceed the bounded scan envelope.'
    }
    $literalSecrets = @(
        [string]$State.Config.receiver.databasePassword,
        [string]$State.Config.localLab.databasePassword,
        [string]$State.Config.receiver.keyStorePassword,
        [string]$State.Config.receiver.trustStorePassword,
        [string]$State.Config.localLab.clientCertificateSha256) |
        Where-Object { -not [string]::IsNullOrEmpty($_) } |
        Select-Object -Unique
    $forbidden = 0
    $size = 0L
    foreach ($file in $files) {
        Assert-WO036NoReparsePathChain -Candidate $file.FullName -Root $State.PrivateRoot
        Assert-WO036PrivateAcl -Path $file.FullName
        $bytes = Read-WO036StableActiveLogBytes -Path $file.FullName `
            -MaximumBytes 52428800
        try {
            $size += $bytes.Length
            if ($size -gt 104857600) {
                throw 'WO-036 private logs exceed the bounded scan envelope.'
            }
            $text = $script:Utf8NoBom.GetString($bytes)
            foreach ($literal in $literalSecrets) {
                $offset = 0
                while (($offset = $text.IndexOf(
                        $literal, $offset, [StringComparison]::Ordinal)) -ge 0) {
                    $forbidden++
                    $offset += $literal.Length
                }
            }
            foreach ($pattern in @(
                '(?i)\bremoteImportId\b',
                '(?is)\{\s*"manifest"\s*:',
                '(?i)(?:^|\s)(?:set-)?cookie\s*:',
                '(?i)(?:^|\s)authorization\s*:',
                '(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{8,}',
                '(?i)\b(?:access|refresh|session)[_-]?token\s*[=:]',
                '(?i)^CLIENT_RANDOM\s+[0-9a-f]{64}\s+[0-9a-f]+$')) {
                $forbidden += [regex]::Matches($text, $pattern,
                    [Text.RegularExpressions.RegexOptions]::Multiline).Count
            }
        }
        finally {
            [Array]::Clear($bytes, 0, $bytes.Length)
        }
    }
    return [pscustomobject]@{
        FileCount = $files.Count
        FileNames = @($files.Name | Sort-Object)
        SizeBytes = $size
        ForbiddenOccurrenceCount = $forbidden
    }
}

function Assert-WO036ExactPrivateLogSet {
    param(
        [Parameter(Mandatory = $true)][object]$Proof,
        [Parameter(Mandatory = $true)][string[]]$ExpectedNames,
        [Parameter(Mandatory = $true)][string]$Gate
    )

    $expected = @($ExpectedNames | Sort-Object)
    $actual = @($Proof.FileNames | Sort-Object)
    if ([int]$Proof.FileCount -ne $expected.Count `
            -or $actual.Count -ne $expected.Count `
            -or $null -ne (Compare-Object -ReferenceObject $expected `
                -DifferenceObject $actual -CaseSensitive)) {
        throw "WO-036 $Gate private log set is incomplete or unexpected."
    }
}

function Test-WO036PrivateLogRedactionCore {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$StatePath)

    $state = Read-WO036PrivateState -StatePath $StatePath
    [void](Assert-WO036RegisteredTooling -State $state)
    $proof = Get-WO036PrivateLogRedactionProof -State $state
    Write-Output "WO036_PRIVATE_LOG_FILE_COUNT=$($proof.FileCount)"
    Write-Output "WO036_PRIVATE_LOG_SIZE_BYTES=$($proof.SizeBytes)"
    Write-Output "WO036_PRIVATE_LOG_FORBIDDEN_OCCURRENCES=$($proof.ForbiddenOccurrenceCount)"
    if ($proof.ForbiddenOccurrenceCount -ne 0) {
        throw 'WO-036 private logs contain forbidden sensitive evidence.'
    }
    Write-Output 'WO036_PRIVATE_LOG_REDACTION=PASS'
}

function Get-WO036StopResultSet {
    param([Parameter(Mandatory = $true)][object]$State)

    $path = Join-Path (Get-WO036PrivateToolsStateDirectory -State $State) `
        'process-stop-results.json'
    if (-not (Test-Path -LiteralPath $path)) {
        return [pscustomobject][ordered]@{
            schemaVersion = '1.0'
            workOrder = $script:WorkOrder
            results = @()
        }
    }
    $set = Read-WO036PrivateToolJson -State $State `
        -FileName 'process-stop-results.json'
    Assert-WO036ExactProperties -Value $set -Description 'process stop result set' `
        -Expected @('schemaVersion', 'workOrder', 'results')
    if ($set.schemaVersion -cne '1.0' -or $set.workOrder -cne $script:WorkOrder) {
        throw 'WO-036 process stop result set identity is invalid.'
    }
    foreach ($result in @($set.results)) {
        Assert-WO036ExactProperties -Value $result -Description 'process stop result' `
            -Expected @('component', 'status', 'recordedAtUtc', 'pid',
                'commandLineSha256')
        if ($result.component -notin @(
                'Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB') `
                -or $result.status -notin @(
                    'STOPPED_GRACEFULLY', 'STOPPED_EXACT_FORCED',
                    'STOPPED_UNCONFIRMED', 'PROCESS_MISSING_UNPROVEN') `
                -or [int]$result.pid -lt 1 `
                -or $result.commandLineSha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'WO-036 process stop result entry is invalid.'
        }
    }
    $duplicates = @($set.results | Group-Object component | Where-Object Count -ne 1)
    if ($duplicates.Count -ne 0) {
        throw 'WO-036 process stop result set contains duplicate components.'
    }
    return $set
}

function Add-WO036StopResult {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][string]$Component,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][int]$ProcessId,
        [Parameter(Mandatory = $true)][string]$CommandLineSha256
    )

    $set = Get-WO036StopResultSet -State $State
    if (@($set.results | Where-Object { $_.component -eq $Component }).Count -ne 0) {
        throw "WO-036 $Component stop result has already been recorded."
    }
    $set.results = @($set.results) + [pscustomobject][ordered]@{
        component = $Component
        status = $Status
        recordedAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
        pid = $ProcessId
        commandLineSha256 = $CommandLineSha256
    }
    $path = Join-Path (Get-WO036PrivateToolsStateDirectory -State $State) `
        'process-stop-results.json'
    if (Test-Path -LiteralPath $path) {
        $partial = "$path.partial"
        if (Test-Path -LiteralPath $partial) {
            throw 'WO-036 process stop result has a residual partial file.'
        }
        Write-WO036PrivateJson -Path $partial -Value $set -CreateNew
        Move-Item -LiteralPath $partial -Destination $path -Force
        Assert-WO036PrivateAcl -Path $path
    }
    else {
        Write-WO036PrivateJson -Path $path -Value $set -CreateNew
    }
}

function Assert-WO036GracefulStopResult {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][string]$Component
    )
    $set = Get-WO036StopResultSet -State $State
    $matches = @($set.results | Where-Object { $_.component -eq $Component })
    if ($matches.Count -ne 1 -or $matches[0].status -cne 'STOPPED_GRACEFULLY') {
        throw "WO-036 $Component was not proven stopped gracefully."
    }
}

function Stop-WO036ExactStartedProcess {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][int]$ProcessId,
        [Parameter(Mandatory = $true)][DateTime]$StartTimeUtc,
        [Parameter(Mandatory = $true)][string]$InstanceToken,
        [Parameter(Mandatory = $true)][string]$Jar,
        [string]$CommandLineSha256
    )

    $cim = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $ProcessId) `
        -ErrorAction SilentlyContinue
    if ($null -eq $cim) {
        return
    }
    try {
        $live = Get-Process -Id $ProcessId -ErrorAction Stop
        $executable = [IO.Path]::GetFullPath([string]$cim.ExecutablePath)
        if (-not $executable.Equals($State.JavaPath,
                    [StringComparison]::OrdinalIgnoreCase) `
                -or [Math]::Abs((
                    $live.StartTime.ToUniversalTime() - $StartTimeUtc).TotalSeconds) -gt 1) {
            throw 'base identity mismatch'
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$cim.CommandLine)) {
            $marker = "-Dwo036.instance=$InstanceToken"
            $actualCommandHash = Get-WO036Sha256Hex -Bytes (
                $script:Utf8NoBom.GetBytes([string]$cim.CommandLine))
            if ([string]$cim.CommandLine -notlike "*$marker*" `
                    -or [string]$cim.CommandLine -notlike "*$Jar*" `
                    -or (-not [string]::IsNullOrEmpty($CommandLineSha256) `
                        -and $actualCommandHash -cne $CommandLineSha256)) {
                throw 'command identity mismatch'
            }
        }
        elseif (-not [string]::IsNullOrEmpty($CommandLineSha256)) {
            throw 'recorded command line became unavailable'
        }
        Stop-Process -InputObject $live -Force -ErrorAction Stop
        Wait-Process -InputObject $live -Timeout 10 -ErrorAction Stop
    }
    catch {
        throw 'WO-036 exact post-start rollback could not prove and stop its process.'
    }
}

function Assert-WO036ExactSensitiveProcessEnvironment {
    param([Parameter(Mandatory = $true)][hashtable]$Expected)

    foreach ($name in Get-WO036SensitiveProcessEnvironmentNames) {
        if (-not $Expected.ContainsKey($name) `
                -or [Environment]::GetEnvironmentVariable($name, 'Process') -cne
                    [string]$Expected[$name]) {
            throw 'WO-036 child environment contains an unexpected sensitive override.'
        }
    }
    foreach ($entry in $Expected.GetEnumerator()) {
        if ($entry.Key -notmatch
                '^(?:SPRING_|SERVER_|MANAGEMENT_|SOFASCORE_|OPTIONAL_INTEGRATION_|BETTING_|POSTGRES_|JNDI_|FLYWAY_|LOGGING_|DEBUG$|TRACE$|HTTP_PROXY$|HTTPS_PROXY$|ALL_PROXY$|NO_PROXY$|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|SSLKEYLOGFILE$|NSS_SSLKEYLOGFILE$|JDK_TLS_KEYLOGGER$|JAVAX_NET_DEBUG$|JDK_HTTPCLIENT_HTTPCLIENT_LOG$)') {
            continue
        }
        $actual = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
        if ([string]::IsNullOrEmpty([string]$entry.Value)) {
            if (-not [string]::IsNullOrEmpty($actual)) {
                throw 'WO-036 child environment failed to neutralize a sensitive override.'
            }
        }
        elseif ($actual -cne [string]$entry.Value) {
            throw 'WO-036 child environment does not match its exact approved override.'
        }
    }
}

function Start-WO036ComponentCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)]
        [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
        [string]$Component,
        [guid]$ExportId = [guid]::Empty
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    [void](Assert-WO036RegisteredTooling -State $state)
    if (@($state.Config.ownedProcesses | Where-Object {
            $_.Component -eq $Component
        }).Count -ne 0) {
        throw "WO-036 $Component has already consumed its one process start."
    }
    if ($Component -ne 'Receiver') {
        $otherComponents = @('LocalLabPreparation', 'LocalLabA', 'LocalLabB') |
            Where-Object { $_ -ne $Component }
        foreach ($other in $otherComponents) {
            if ($null -ne (Get-WO036OwnedProcess -State $state -Component $other `
                    -AllowMissing)) {
                throw 'WO-036 Local Lab campaign instances cannot run concurrently.'
            }
        }
        $clonePath = Join-Path (Get-WO036PrivateToolsStateDirectory -State $state) `
            'database-clone.json'
        if ($Component -eq 'LocalLabPreparation') {
            if ((Test-Path -LiteralPath $clonePath) `
                    -or @($state.Config.ownedProcesses | Where-Object {
                        $_.Component -in @('LocalLabA', 'LocalLabB')
                    }).Count -ne 0) {
                throw 'WO-036 preparation instance is only available before the clone.'
            }
        }
        else {
            if (@($state.Config.ownedProcesses | Where-Object {
                    $_.Component -eq 'LocalLabPreparation'
                }).Count -ne 1 `
                    -or -not (Test-Path -LiteralPath $clonePath -PathType Leaf) `
                    -or (Read-WO036PrivateToolJson -State $state `
                        -FileName 'database-clone.json').status -cne 'PASS_CONSUMED') {
                throw 'WO-036 delivery instances require the completed preparation and clone.'
            }
            Assert-WO036GracefulStopResult -State $state `
                -Component LocalLabPreparation
        }
        if ($Component -eq 'LocalLabA' `
                -and @($state.Config.ownedProcesses | Where-Object {
                    $_.Component -eq 'LocalLabB'
                }).Count -ne 0) {
            throw 'WO-036 Local Lab A cannot start after B has been claimed.'
        }
        if ($Component -in @('LocalLabA', 'LocalLabB')) {
            $receiverProcess = Get-WO036OwnedProcess -State $state `
                -Component Receiver
            if (-not (Test-WO036ExactLoopbackListener -Port 8444 `
                    -ProcessId ([int]$receiverProcess.ProcessId))) {
                throw 'WO-036 delivery instance requires the exact active receiver listener.'
            }
        }
        if ($Component -eq 'LocalLabB' `
                -and @($state.Config.ownedProcesses | Where-Object {
                    $_.Component -eq 'LocalLabA'
                }).Count -ne 1) {
            throw 'WO-036 Local Lab B requires the one-shot A instance first.'
        }
        if ($Component -eq 'LocalLabB') {
            if ($ExportId -eq [guid]::Empty) {
                throw 'WO-036 Local Lab B requires the frozen synthetic export identity.'
            }
            $freeze = Read-WO036PrivateToolJson -State $state `
                -FileName 'pre-call-freeze.json'
            $aProof = Get-WO036DatabaseProof -State $state -Role LocalLabA `
                -ExportId $ExportId
            $receiverProof = Get-WO036DatabaseProof -State $state -Role Receiver `
                -ExportId $ExportId
            Assert-WO036GracefulStopResult -State $state -Component LocalLabA
            if ($freeze.result -cne 'PASS' `
                    -or [guid]$freeze.artifact.exportId -ne $ExportId `
                    -or [int]$aProof.manifestCount -ne 1 `
                    -or [int]$aProof.humanValidatedCount -ne 1 `
                    -or [int]$aProof.syntheticOnlyCount -ne 1 `
                    -or [int]$aProof.deliveryCount -ne 1 `
                    -or $aProof.deliveryState -cne 'DELIVERED' `
                    -or [int]$aProof.attemptCount -ne 1 `
                    -or [int]$aProof.resultCount -ne 1 `
                    -or [int]$aProof.httpStatus -ne 201 `
                    -or $aProof.safeResultCode -cne 'HTTP_201_IMPORTED' `
                    -or [int]$receiverProof.receiptCount -ne 1 `
                    -or [int]$receiverProof.payloadCount -ne 1 `
                    -or [int]$receiverProof.outboxCount -ne 1 `
                    -or [int]$receiverProof.auditImportedCount -ne 1 `
                    -or [int]$receiverProof.auditDuplicateCount -ne 0 `
                    -or [int]$receiverProof.auditDivergenceCount -ne 0 `
                    -or [int]$receiverProof.auditTotalCount -ne 1 `
                    -or [int]$receiverProof.globalReceiptCount -ne 1 `
                    -or [int]$receiverProof.globalPayloadCount -ne 1 `
                    -or [int]$receiverProof.globalOutboxJ7Count -ne 1 `
                    -or [int]$receiverProof.globalAuditTotalCount -ne 1 `
                    -or $aProof.fileSha256 -cne $freeze.artifact.fileSha256 `
                    -or $aProof.dataSha256 -cne $freeze.artifact.dataSha256 `
                    -or $receiverProof.fileSha256 -cne $freeze.artifact.fileSha256 `
                    -or $receiverProof.dataSha256 -cne $freeze.artifact.dataSha256) {
                throw 'WO-036 Local Lab B requires the exact durable 201 proof from A.'
            }
        }
        elseif ($ExportId -ne [guid]::Empty) {
            throw 'WO-036 export identity is only accepted for the Local Lab B start gate.'
        }
    }
    else {
        $clonePath = Join-Path (Get-WO036PrivateToolsStateDirectory -State $state) `
            'database-clone.json'
        if (@($state.Config.ownedProcesses | Where-Object {
                $_.Component -eq 'LocalLabPreparation'
            }).Count -ne 1 `
                -or $null -ne (Get-WO036OwnedProcess -State $state `
                    -Component LocalLabPreparation -AllowMissing) `
                -or @($state.Config.ownedProcesses | Where-Object {
                    $_.Component -in @('LocalLabA', 'LocalLabB')
                }).Count -ne 0 `
                -or -not (Test-Path -LiteralPath $clonePath -PathType Leaf) `
                -or (Read-WO036PrivateToolJson -State $state `
                    -FileName 'database-clone.json').status -cne 'PASS_CONSUMED') {
                throw 'WO-036 receiver start requires the immutable cloned corpus before A and B.'
        }
        Assert-WO036GracefulStopResult -State $state `
            -Component LocalLabPreparation
        if ($ExportId -ne [guid]::Empty) {
            throw 'WO-036 receiver start does not accept an export argument.'
        }
    }
    $database = if ($Component -eq 'Receiver') {
        $state.Config.receiver
    }
    else {
        $state.Config.localLab
    }
    Assert-WO036ContainerOwnership -State $state -Database $database
    $port = if ($Component -eq 'Receiver') { 8444 } else { 8087 }
    if (-not (Test-WO036PortIsFree -Port $port)) {
        throw "WO-036 $Component listener port is already occupied."
    }

    $instance = [guid]::NewGuid().ToString()
    $logsDirectory = Join-Path $state.PrivateRoot 'logs'
    [void][IO.Directory]::CreateDirectory($logsDirectory)
    Assert-WO036NoReparsePathChain -Candidate $logsDirectory -Root $state.PrivateRoot
    Assert-WO036PrivateAcl -Path $logsDirectory
    $stdout = Join-Path $logsDirectory ("{0}.stdout.log" -f $Component.ToLowerInvariant())
    $stderr = Join-Path $logsDirectory ("{0}.stderr.log" -f $Component.ToLowerInvariant())
    $jar = if ($Component -eq 'Receiver') { $state.ReceiverJar } else { $state.LocalLabJar }
    $workingDirectory = Join-Path $state.PrivateRoot (
        "runtime-{0}" -f $Component.ToLowerInvariant())
    [void][IO.Directory]::CreateDirectory($workingDirectory)
    Assert-WO036NoReparsePathChain -Candidate $workingDirectory -Root $state.PrivateRoot
    Assert-WO036PrivateAcl -Path $workingDirectory
    if ((((Get-Item -LiteralPath $workingDirectory -Force).Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0) `
            -or (Test-Path -LiteralPath (Join-Path $workingDirectory '.env'))) {
        throw 'WO-036 private runtime working directory is unsafe.'
    }
    $environment = Get-WO036ComponentEnvironment -State $state -Component $Component
    $previous = @{}
    $process = $null
    try {
        foreach ($name in Get-WO036SensitiveProcessEnvironmentNames) {
            if (-not $previous.ContainsKey($name)) {
                $previous[$name] = [Environment]::GetEnvironmentVariable(
                    $name, 'Process')
            }
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        foreach ($entry in $environment.GetEnumerator()) {
            if (-not $previous.ContainsKey($entry.Key)) {
                $previous[$entry.Key] = [Environment]::GetEnvironmentVariable(
                    $entry.Key, 'Process')
            }
            [Environment]::SetEnvironmentVariable(
                $entry.Key, [string]$entry.Value, 'Process')
        }
        Assert-WO036ExactSensitiveProcessEnvironment -Expected $environment
        try {
            $process = Start-Process -FilePath $state.JavaPath -ArgumentList @(
                "-Dwo036.instance=$instance",
                $script:JdkHttpClientRetryGuards[0],
                $script:JdkHttpClientRetryGuards[1],
                $script:JdkHttpClientRetryGuards[2],
                '-jar', $jar) `
                -WorkingDirectory $workingDirectory -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        }
        catch {
            throw 'WO-036 Java component could not be started.'
        }
    }
    finally {
        foreach ($entry in $previous.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable(
                $entry.Key, $entry.Value, 'Process')
        }
    }

    $startedAtUtc = [DateTime]::MinValue
    $commandLineSha256 = ''
    try {
        $startedAtUtc = $process.StartTime.ToUniversalTime()
        foreach ($logPath in @($stdout, $stderr)) {
            $resolvedLog = Resolve-WO036ExistingPath -Value $logPath -PathType Leaf `
                -Description 'component private log'
            Assert-WO036NoReparsePathChain -Candidate $resolvedLog `
                -Root $state.PrivateRoot
            Assert-WO036PrivateAcl -Path $resolvedLog
        }
        $cimProcess = $null
        for ($attempt = 1; $attempt -le 20; $attempt++) {
            $cimProcess = Get-CimInstance Win32_Process -Filter `
                ("ProcessId={0}" -f $process.Id) -ErrorAction SilentlyContinue
            if ($null -ne $cimProcess -and -not [string]::IsNullOrWhiteSpace(
                    [string]$cimProcess.CommandLine)) { break }
            Start-Sleep -Milliseconds 100
        }
        if ($null -eq $cimProcess -or [string]::IsNullOrWhiteSpace(
                [string]$cimProcess.CommandLine)) {
            throw "WO-036 $Component exact process identity was not observable."
        }
        $commandLineBytes = $script:Utf8NoBom.GetBytes(
            [string]$cimProcess.CommandLine)
        try {
            $commandLineSha256 = Get-WO036Sha256Hex -Bytes $commandLineBytes
        }
        finally {
            [Array]::Clear($commandLineBytes, 0, $commandLineBytes.Length)
        }
        $actualExecutable = [IO.Path]::GetFullPath(
            [string]$cimProcess.ExecutablePath)
        if (-not $actualExecutable.Equals($state.JavaPath,
                    [StringComparison]::OrdinalIgnoreCase) `
                -or [string]$cimProcess.CommandLine -notlike
                    "*-Dwo036.instance=$instance*" `
                -or [string]$cimProcess.CommandLine -notlike "*$jar*") {
            throw "WO-036 $Component exact process identity is inconsistent."
        }
        $record = [pscustomobject]@{
            Role = if ($Component -eq 'Receiver') { 'betting-project' } else { 'local-lab' }
            Component = $Component
            InstanceToken = $instance
            Pid = $process.Id
            ExecutablePath = $actualExecutable
            CommandLineSha256 = $commandLineSha256
            StartTimeUtc = $startedAtUtc.ToString('O')
        }
        $state.Config.ownedProcesses = @($state.Config.ownedProcesses) + $record
        Write-WO036StateConfig -State $state
        if (-not (Wait-WO036LoopbackListener -Port $port -ProcessId $process.Id)) {
            throw "WO-036 $Component failed to expose one exact loopback listener."
        }
    }
    catch {
        $cleanupFailed = $false
        try {
            Stop-WO036ExactStartedProcess -State $state -ProcessId $process.Id `
                -StartTimeUtc $startedAtUtc -InstanceToken $instance -Jar $jar `
                -CommandLineSha256 $commandLineSha256
        }
        catch {
            $cleanupFailed = $true
        }
        if ($cleanupFailed) {
            throw "WO-036 $Component post-start failure could not be cleaned exactly."
        }
        throw
    }
    Write-Output "WO036_COMPONENT=$Component"
    Write-Output 'WO036_COMPONENT_START=PASS'
    Write-Output "WO036_LISTENER=127.0.0.1:$port"
}

function Stop-WO036ComponentCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)]
        [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
        [string]$Component
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    [void](Assert-WO036RegisteredTooling -State $state)
    $existingStops = Get-WO036StopResultSet -State $state
    if (@($existingStops.results | Where-Object {
            $_.component -eq $Component
        }).Count -ne 0) {
        throw "WO-036 $Component stop is one-shot and was already recorded."
    }
    $records = @($state.Config.ownedProcesses | Where-Object {
        $_.Component -eq $Component
    })
    if ($records.Count -ne 1) {
        throw "WO-036 $Component has no exact one-shot process ownership record."
    }
    $record = $records[0]
    $process = Get-WO036OwnedProcess -State $state -Component $Component -AllowMissing
    if ($null -eq $process) {
        Add-WO036StopResult -State $state -Component $Component `
            -Status 'PROCESS_MISSING_UNPROVEN' -ProcessId ([int]$record.Pid) `
            -CommandLineSha256 ([string]$record.CommandLineSha256)
        throw "WO-036 $Component disappeared before an exact stop could be proven."
    }

    $gracefulResponseAccepted = $false
    $forced = $false
    $client = $null
    $request = $null
    $response = $null
    $port = if ($Component -eq 'Receiver') { 8444 } else { 8087 }
    if (Test-WO036ExactLoopbackListener -Port $port `
            -ProcessId ([int]$process.ProcessId)) {
        try {
            $certificate = if ($Component -eq 'Receiver') {
                [string]$state.Config.receiver.clientCertificateSha256
            }
            else { '' }
            $client = New-WO036HttpClient -ClientCertificateSha256 $certificate
            $origin = if ($Component -eq 'Receiver') {
                $script:ReceiverOrigin
            }
            else { 'http://127.0.0.1:8087' }
            $request = [Net.Http.HttpRequestMessage]::new(
                [Net.Http.HttpMethod]::Post, "$origin/actuator/shutdown")
            $response = $client.Send(
                $request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead)
            $gracefulResponseAccepted = [int]$response.StatusCode -ge 200 `
                -and [int]$response.StatusCode -lt 300
        }
        catch {
            $gracefulResponseAccepted = $false
        }
        finally {
            if ($null -ne $response) { $response.Dispose() }
            if ($null -ne $request) { $request.Dispose() }
            if ($null -ne $client) { $client.Dispose() }
        }
    }

    for ($attempt = 1; $attempt -le 40; $attempt++) {
        if ($null -eq (Get-Process -Id ([int]$process.ProcessId) `
                -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 500
    }
    if ($null -ne (Get-Process -Id ([int]$process.ProcessId) `
            -ErrorAction SilentlyContinue)) {
        [void](Get-WO036OwnedProcess -State $state -Component $Component)
        Stop-WO036ExactStartedProcess -State $state `
            -ProcessId ([int]$record.Pid) `
            -StartTimeUtc (ConvertTo-WO036UtcDateTime `
                -Value $record.StartTimeUtc `
                -Description "$Component process start time") `
            -InstanceToken ([string]$record.InstanceToken) `
            -Jar $(if ($Component -eq 'Receiver') {
                $state.ReceiverJar
            }
            else { $state.LocalLabJar }) `
            -CommandLineSha256 ([string]$record.CommandLineSha256)
        $forced = $true
    }
    for ($attempt = 1; $attempt -le 20; $attempt++) {
        if ($null -eq (Get-Process -Id ([int]$process.ProcessId) `
                -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 250
    }
    if ($null -ne (Get-Process -Id ([int]$process.ProcessId) `
            -ErrorAction SilentlyContinue)) {
        throw "WO-036 $Component exact process cleanup failed."
    }
    $status = if ($forced) {
        'STOPPED_EXACT_FORCED'
    }
    elseif ($gracefulResponseAccepted) {
        'STOPPED_GRACEFULLY'
    }
    else {
        'STOPPED_UNCONFIRMED'
    }
    Add-WO036StopResult -State $state -Component $Component -Status $status `
        -ProcessId ([int]$record.Pid) `
        -CommandLineSha256 ([string]$record.CommandLineSha256)
    Write-Output "WO036_COMPONENT=$Component"
    Write-Output "WO036_COMPONENT_STOP=$status"
    if ($status -cne 'STOPPED_GRACEFULLY') {
        throw "WO-036 $Component stop was not graceful; campaign PASS is blocked."
    }
}

function Copy-WO036LocalLabDatabaseCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    [void](Assert-WO036RegisteredTooling -State $state)
    if (@($state.Config.ownedProcesses | Where-Object {
            $_.Component -eq 'LocalLabPreparation'
        }).Count -ne 1) {
        throw 'WO-036 database clone requires the consumed preparation instance.'
    }
    Assert-WO036GracefulStopResult -State $state -Component LocalLabPreparation
    foreach ($component in @('LocalLabPreparation', 'LocalLabA', 'LocalLabB')) {
        if ($null -ne (Get-WO036OwnedProcess -State $state -Component $component `
                -AllowMissing)) {
            throw 'WO-036 database clone requires every Local Lab instance to be stopped.'
        }
    }
    if (@($state.Config.ownedProcesses | Where-Object {
            $_.Component -in @('LocalLabA', 'LocalLabB')
        }).Count -ne 0) {
        throw 'WO-036 database clone must precede both delivery instances.'
    }
    $database = $state.Config.localLab
    $source = [string]$database.sourceDatabase
    $target = [string]$database.targetDatabase
    $preflightSql = @"
select jsonb_build_object(
    'sourceCount', (select count(*) from pg_database where datname = '$source'),
    'targetCount', (select count(*) from pg_database where datname = '$target'),
    'sourceSessions', (select count(*) from pg_stat_activity where datname = '$source'),
    'targetSessions', (select count(*) from pg_stat_activity where datname = '$target'),
    'primaryTouchedGuard', (select count(*) from pg_database
        where datname in ('postgres', 'sofascore_local_lab')),
    'ownedDatabaseSetCount', (select count(*) from pg_database
        where datname in ('postgres', 'template0', 'template1', '$source')),
    'unexpectedDatabaseCount', (select count(*) from pg_database
        where datname not in ('postgres', 'template0', 'template1', '$source'))
)::text;
"@
    $preflight = ConvertFrom-WO036PsqlJson -Value (Invoke-WO036Psql -State $state `
        -Database $database -DatabaseName 'postgres' -Sql $preflightSql)
    if ([int]$preflight.sourceCount -ne 1 `
            -or [int]$preflight.targetCount -ne 0 `
            -or [int]$preflight.sourceSessions -ne 0 `
            -or [int]$preflight.targetSessions -ne 0 `
            -or [int]$preflight.primaryTouchedGuard -ne 1 `
            -or [int]$preflight.ownedDatabaseSetCount -ne 4 `
            -or [int]$preflight.unexpectedDatabaseCount -ne 0) {
        throw 'WO-036 database clone preconditions are not satisfied.'
    }

    $candidateSql = @"
select jsonb_build_object(
    'humanValidatedCandidateCount', (select count(*) from export_manifest
        where validation_status = 'HUMAN_VALIDATED'),
    'selectedCandidateCount', (select count(*) from export_manifest
        where export_uuid = '$ExportId'::uuid
          and validation_status = 'HUMAN_VALIDATED')
)::text;
"@
    $candidate = ConvertFrom-WO036PsqlJson -Value (Invoke-WO036Psql `
        -State $state -Database $database -DatabaseName $source `
        -Sql $candidateSql)
    $corpus = Get-WO036DatabaseProof -State $state -Role LocalLabA `
        -ExportId $ExportId
    $exportPath = Get-WO036ExportPath -State $state -ExportId $ExportId
    $exportBytes = [IO.File]::ReadAllBytes($exportPath)
    try {
        $fileSha256 = Get-WO036Sha256Hex -Bytes $exportBytes
        if ([int]$candidate.humanValidatedCandidateCount -ne 1 `
                -or [int]$candidate.selectedCandidateCount -ne 1 `
                -or [int]$corpus.manifestCount -ne 1 `
                -or [int]$corpus.humanValidatedCount -ne 1 `
                -or [int]$corpus.syntheticOnlyCount -ne 1 `
                -or [int]$corpus.deliveryCount -ne 0 `
                -or [int]$corpus.attemptCount -ne 0 `
                -or [int]$corpus.resultCount -ne 0 `
                -or $corpus.fileSha256 -cne $fileSha256 `
                -or [long]$corpus.fileSizeBytes -ne $exportBytes.Length `
                -or $corpus.dataSha256 -notmatch '^[0-9a-f]{64}$') {
            throw 'WO-036 clone corpus is not one exact synthetic validated export.'
        }
    }
    finally {
        [Array]::Clear($exportBytes, 0, $exportBytes.Length)
    }

    $claimPath = Join-Path (Get-WO036PrivateToolsStateDirectory -State $state) `
        'database-clone.json'
    $claim = [ordered]@{
        schemaVersion = '1.0'
        workOrder = $script:WorkOrder
        claimedAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
        status = 'CLAIMED_UNKNOWN_CONSUMED'
        exportId = $ExportId.ToString()
        sizeBytes = [long]$corpus.fileSizeBytes
        fileSha256 = $fileSha256
        dataSha256 = [string]$corpus.dataSha256
    }
    Write-WO036PrivateJson -Path $claimPath -Value $claim -CreateNew
    $cloneSql = @"
create database "$target" with template "$source";
"@
    try {
        [void](Invoke-WO036Psql -State $state -Database $database `
            -DatabaseName 'postgres' -Sql $cloneSql)
        $proofSql = @"
select jsonb_build_object(
    'sourceCount', (select count(*) from pg_database where datname = '$source'),
    'targetCount', (select count(*) from pg_database where datname = '$target'),
    'sourceSessions', (select count(*) from pg_stat_activity where datname = '$source'),
    'targetSessions', (select count(*) from pg_stat_activity where datname = '$target'),
    'primaryTouchedGuard', (select count(*) from pg_database
        where datname in ('postgres', 'sofascore_local_lab')),
    'ownedDatabaseSetCount', (select count(*) from pg_database
        where datname in ('postgres', 'template0', 'template1', '$source', '$target')),
    'unexpectedDatabaseCount', (select count(*) from pg_database
        where datname not in ('postgres', 'template0', 'template1', '$source', '$target'))
)::text;
"@
        $proof = ConvertFrom-WO036PsqlJson -Value (Invoke-WO036Psql -State $state `
            -Database $database -DatabaseName 'postgres' -Sql $proofSql)
        if ([int]$proof.sourceCount -ne 1 `
                -or [int]$proof.targetCount -ne 1 `
                -or [int]$proof.sourceSessions -ne 0 `
                -or [int]$proof.targetSessions -ne 0 `
                -or [int]$proof.primaryTouchedGuard -ne 1 `
                -or [int]$proof.ownedDatabaseSetCount -ne 5 `
                -or [int]$proof.unexpectedDatabaseCount -ne 0) {
            throw 'WO-036 database clone postconditions are not satisfied.'
        }
        $claim.status = 'PASS_CONSUMED'
        $claim['completedAtUtc'] = [DateTimeOffset]::UtcNow.ToString('O')
        Update-WO036PrivateJsonAtomic -Path $claimPath -Value $claim
    }
    catch {
        $claim.status = 'FAILED_OR_UNKNOWN_CONSUMED'
        $claim['completedAtUtc'] = [DateTimeOffset]::UtcNow.ToString('O')
        Update-WO036PrivateJsonAtomic -Path $claimPath -Value $claim
        throw
    }
    Write-Output 'WO036_LOCAL_LAB_DATABASE_CLONE=PASS'
    Write-Output 'WO036_SOURCE_DATABASE_MUTATED=NO'
    Write-Output 'WO036_PRIMARY_DATABASE_TOUCHED=NO'
    Write-Output "WO036_CLONE_EXPORT_ID=$ExportId"
    Write-Output "WO036_CLONE_FILE_SHA256=$fileSha256"
    Write-Output "WO036_CLONE_DATA_SHA256=$($corpus.dataSha256)"
}

function Read-WO036PrivateToolJson {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][string]$FileName
    )

    if ($FileName -notmatch '^[a-z0-9-]+\.json$') {
        throw 'WO-036 private tool evidence name is invalid.'
    }
    $path = Join-Path (Get-WO036PrivateToolsStateDirectory -State $State) $FileName
    $resolved = Resolve-WO036ExistingPath -Value $path -PathType Leaf `
        -Description 'private tool evidence'
    Assert-WO036NoReparsePathChain -Candidate $resolved -Root $State.PrivateRoot
    Assert-WO036PrivateAcl -Path $resolved
    $bytes = [IO.File]::ReadAllBytes($resolved)
    try {
        if ($bytes.Length -lt 2 -or $bytes.Length -gt 65536) {
            throw 'invalid size'
        }
        $text = $script:Utf8NoBom.GetString($bytes)
        $document = [Text.Json.JsonDocument]::Parse($text)
        try {
            Assert-WO036NoDuplicateJsonProperties -Element $document.RootElement
        }
        finally {
            $document.Dispose()
        }
        return $text | ConvertFrom-Json -Depth 8
    }
    catch {
        throw 'WO-036 private tool evidence is not strict bounded JSON.'
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Export-WO036PreCallFreezeCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    $toolingProof = Assert-WO036RegisteredTooling -State $state
    $cloneClaim = Read-WO036PrivateToolJson -State $state `
        -FileName 'database-clone.json'
    if ($cloneClaim.status -cne 'PASS_CONSUMED' `
            -or [guid]$cloneClaim.exportId -ne $ExportId `
            -or $cloneClaim.fileSha256 -notmatch '^[0-9a-f]{64}$' `
            -or $cloneClaim.dataSha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'WO-036 pre-call freeze requires the successful one-shot A-to-B clone.'
    }
    $toolState = Get-WO036PrivateToolsStateDirectory -State $state
    if (Test-Path -LiteralPath (Join-Path $toolState 'collision-probe.json')) {
        throw 'WO-036 pre-call freeze is no longer available after collision claim.'
    }
    $receiverProcess = Get-WO036OwnedProcess -State $state -Component Receiver
    $localLabAProcess = Get-WO036OwnedProcess -State $state -Component LocalLabA
    if (@($state.Config.ownedProcesses | Where-Object {
            $_.Component -eq 'LocalLabB'
        }).Count -ne 0 `
            -or -not (Wait-WO036LoopbackListener -Port 8444 `
                -ProcessId ([int]$receiverProcess.ProcessId)) `
            -or -not (Wait-WO036LoopbackListener -Port 8087 `
                -ProcessId ([int]$localLabAProcess.ProcessId))) {
        throw 'WO-036 pre-call freeze requires receiver and A exact, with B never started.'
    }
    if ($null -ne (Get-WO036OwnedProcess -State $state `
            -Component LocalLabPreparation -AllowMissing)) {
        throw 'WO-036 preparation process must be stopped before pre-call freeze.'
    }
    Assert-WO036GracefulStopResult -State $state `
        -Component LocalLabPreparation
    $a = Get-WO036DatabaseProof -State $state -Role LocalLabA -ExportId $ExportId
    $b = Get-WO036DatabaseProof -State $state -Role LocalLabB -ExportId $ExportId
    $receiver = Get-WO036DatabaseProof -State $state -Role Receiver `
        -ExportId $ExportId
    if ([int]$a.manifestCount -ne 1 `
            -or [int]$a.humanValidatedCount -ne 1 `
            -or [int]$a.syntheticOnlyCount -ne 1 `
            -or [int]$a.deliveryCount -ne 0 `
            -or [int]$a.attemptCount -ne 0 `
            -or [int]$a.resultCount -ne 0 `
            -or [int]$b.manifestCount -ne 1 `
            -or [int]$b.humanValidatedCount -ne 1 `
            -or [int]$b.syntheticOnlyCount -ne 1 `
            -or [int]$b.deliveryCount -ne 0 `
            -or [int]$b.attemptCount -ne 0 `
            -or [int]$b.resultCount -ne 0 `
            -or [int]$receiver.receiptCount -ne 0 `
            -or [int]$receiver.payloadCount -ne 0 `
            -or [int]$receiver.auditTotalCount -ne 0 `
            -or [int]$receiver.outboxCount -ne 0 `
            -or [int]$receiver.globalReceiptCount -ne 0 `
            -or [int]$receiver.globalPayloadCount -ne 0 `
            -or [int]$receiver.globalAuditTotalCount -ne 0 `
            -or [int]$receiver.globalOutboxJ7Count -ne 0) {
        throw 'WO-036 pre-call databases are not in the exact zero-attempt state.'
    }
    $exportPath = Get-WO036ExportPath -State $state -ExportId $ExportId
    $exportBytes = [IO.File]::ReadAllBytes($exportPath)
    try {
        $fileSha256 = Get-WO036Sha256Hex -Bytes $exportBytes
        if ($fileSha256 -cne $a.fileSha256 `
                -or $fileSha256 -cne $b.fileSha256 `
                -or $fileSha256 -cne $cloneClaim.fileSha256 `
                -or $a.dataSha256 -cne $b.dataSha256 `
                -or $a.dataSha256 -cne $cloneClaim.dataSha256 `
                -or $exportBytes.Length -ne [long]$a.fileSizeBytes `
                -or $exportBytes.Length -ne [long]$b.fileSizeBytes `
                -or $exportBytes.Length -ne [long]$cloneClaim.sizeBytes) {
            throw 'WO-036 pre-call A/B artifact identity is not exact.'
        }
        $logProof = Get-WO036PrivateLogRedactionProof -State $state
        Assert-WO036ExactPrivateLogSet -Proof $logProof `
            -ExpectedNames $script:PreCallLogNames -Gate 'pre-call freeze'
        if ([int]$logProof.ForbiddenOccurrenceCount -ne 0) {
            throw 'WO-036 pre-call private logs are not redacted.'
        }
        $freeze = [ordered]@{
            schemaVersion = '1.0'
            workOrder = $script:WorkOrder
            frozenAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
            result = 'PASS'
            expectedSourceCommits = [ordered]@{
                localLabSenderAncestor = $script:ExpectedLocalLabSenderCommit
                receiverHead = $script:ExpectedReceiverHeadCommit
                receiverImplementationAncestor =
                    $script:ExpectedReceiverImplementationCommit
            }
            executableArtifacts = [ordered]@{
                javaSha256 = [string]$toolingProof.javaSha256
                localLabJarSha256 = [string]$state.Config.localLabJarSha256
                receiverJarSha256 = [string]$state.Config.receiverJarSha256
                dockerEndpointSha256 =
                    [string]$toolingProof.dockerEndpointSha256
            }
            tooling = [ordered]@{
                localLabRegisteredHead =
                    [string]$toolingProof.localLabRegisteredHead
                localLabCurrentHead = [string]$toolingProof.localLabCurrentHead
                receiverHead = [string]$toolingProof.receiverHead
                registrationManifestSha256 =
                    [string]$toolingProof.registrationManifestSha256
                dockerEndpointSha256 =
                    [string]$toolingProof.dockerEndpointSha256
                allowedPostFreezeManifestPath =
                    [string]$toolingProof.allowedPostFreezeManifestPath
                files = @($toolingProof.toolingFiles)
            }
            topology = [ordered]@{
                localLab = '127.0.0.1:8087'
                receiver = '127.0.0.1:8444'
                localLabPostgres = '127.0.0.1:5432'
                receiverPostgres = '127.0.0.1:5433'
                mtlsClientAuth = 'NEED'
            }
            callEnvelope = [ordered]@{
                importRouteCallsBeforeFreeze = 0
                maximumImportRouteCalls = $script:MaximumImportRouteCalls
                automaticRetries = 0
                expectedStatuses = @(201, 200, 409)
            }
            artifact = [ordered]@{
                exportId = $ExportId.ToString()
                sizeBytes = $exportBytes.Length
                fileSha256 = $fileSha256
                dataSha256 = [string]$a.dataSha256
                validation = 'HUMAN_VALIDATED'
                provenance = 'SYNTHETIC_ONLY'
            }
            logScan = [ordered]@{
                files = [int]$logProof.FileCount
                fileNames = @($logProof.FileNames)
                sizeBytes = [long]$logProof.SizeBytes
                forbiddenOccurrences = 0
            }
            forbiddenScope = [ordered]@{
                providerCalls = 0
                remoteReceiverCalls = 0
                providerDerivedPayloads = 0
                vpsDeployments = 0
            }
        }
        $freezePath = Join-Path $toolState 'pre-call-freeze.json'
        Write-WO036PrivateJson -Path $freezePath -Value $freeze -CreateNew
        $freezeBytes = [IO.File]::ReadAllBytes($freezePath)
        try {
        Write-Output 'WO036_PRE_CALL_FREEZE=PASS'
            Write-Output "WO036_PRE_CALL_FREEZE_SIZE_BYTES=$($freezeBytes.Length)"
            Write-Output "WO036_PRE_CALL_FREEZE_SHA256=$(Get-WO036Sha256Hex -Bytes $freezeBytes)"
            Write-Output 'WO036_IMPORT_ROUTE_CALLS_BEFORE_FREEZE=0'
            Write-Output "WO036_MAXIMUM_IMPORT_ROUTE_CALLS=$script:MaximumImportRouteCalls"
        }
        finally {
            [Array]::Clear($freezeBytes, 0, $freezeBytes.Length)
        }
    }
    finally {
        [Array]::Clear($exportBytes, 0, $exportBytes.Length)
    }
}

function Test-WO036FrozenToolingCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    $tooling = Assert-WO036RegisteredTooling -State $state
    $freeze = Read-WO036PrivateToolJson -State $state `
        -FileName 'pre-call-freeze.json'
    $clone = Read-WO036PrivateToolJson -State $state `
        -FileName 'database-clone.json'
    if ($freeze.result -cne 'PASS' `
            -or [guid]$freeze.artifact.exportId -ne $ExportId `
            -or $clone.status -cne 'PASS_CONSUMED' `
            -or [guid]$clone.exportId -ne $ExportId `
            -or (Test-Path -LiteralPath (Join-Path `
                (Get-WO036PrivateToolsStateDirectory -State $state) `
                'collision-probe.json'))) {
        throw 'WO-036 frozen pre-call identity is invalid or already consumed.'
    }
    $receiverProcess = Get-WO036OwnedProcess -State $state -Component Receiver
    $aProcess = Get-WO036OwnedProcess -State $state -Component LocalLabA
    if (-not (Test-WO036ExactLoopbackListener -Port 8444 `
                -ProcessId ([int]$receiverProcess.ProcessId)) `
            -or -not (Test-WO036ExactLoopbackListener -Port 8087 `
                -ProcessId ([int]$aProcess.ProcessId)) `
            -or @($state.Config.ownedProcesses | Where-Object {
                $_.Component -eq 'LocalLabB'
            }).Count -ne 0) {
        throw 'WO-036 frozen pre-call listeners or process sequence changed.'
    }
    $a = Get-WO036DatabaseProof -State $state -Role LocalLabA -ExportId $ExportId
    $b = Get-WO036DatabaseProof -State $state -Role LocalLabB -ExportId $ExportId
    $receiver = Get-WO036DatabaseProof -State $state -Role Receiver `
        -ExportId $ExportId
    if ([int]$a.deliveryCount -ne 0 -or [int]$a.attemptCount -ne 0 `
            -or [int]$a.resultCount -ne 0 -or [int]$b.deliveryCount -ne 0 `
            -or [int]$b.attemptCount -ne 0 -or [int]$b.resultCount -ne 0 `
            -or [int]$receiver.globalReceiptCount -ne 0 `
            -or [int]$receiver.globalPayloadCount -ne 0 `
            -or [int]$receiver.globalAuditTotalCount -ne 0 `
            -or [int]$receiver.globalOutboxJ7Count -ne 0) {
        throw 'WO-036 frozen tooling gate detected an import-route side effect.'
    }
    $exportPath = Get-WO036ExportPath -State $state -ExportId $ExportId
    if ((Get-WO036FileSha256Hex -Path $exportPath) -cne
            [string]$freeze.artifact.fileSha256) {
        throw 'WO-036 frozen J7 bytes changed before the first POST.'
    }
    $logs = Get-WO036PrivateLogRedactionProof -State $state
    Assert-WO036ExactPrivateLogSet -Proof $logs `
        -ExpectedNames $script:PreCallLogNames -Gate 'frozen tooling'
    if ([int]$logs.ForbiddenOccurrenceCount -ne 0) {
        throw 'WO-036 private logs are not clean at the frozen tooling gate.'
    }
    Write-Output 'WO036_FROZEN_TOOLING=PASS'
    Write-Output "WO036_FROZEN_LOCAL_LAB_HEAD=$($tooling.localLabRegisteredHead)"
    Write-Output "WO036_CURRENT_LOCAL_LAB_HEAD=$($tooling.localLabCurrentHead)"
    Write-Output "WO036_RECEIVER_HEAD=$($tooling.receiverHead)"
    Write-Output "WO036_REGISTRATION_MANIFEST_SHA256=$($tooling.registrationManifestSha256)"
    Write-Output "WO036_MAXIMUM_IMPORT_ROUTE_CALLS=$script:MaximumImportRouteCalls"
}

function New-WO036CollisionMutation {
    param([Parameter(Mandatory = $true)][byte[]]$OriginalBytes)

    if ($OriginalBytes.Length -lt 2 -or $OriginalBytes.Length -gt 5242880) {
        throw 'WO-036 validated export size is outside the protocol envelope.'
    }
    $text = $script:Utf8NoBom.GetString($OriginalBytes)
    if (-not $text.EndsWith("`n", [StringComparison]::Ordinal) `
            -or $text.Contains("`r")) {
        throw 'WO-036 validated export bytes are not canonical line-oriented UTF-8.'
    }
    $pattern = '"generatorVersion":"(?<value>[A-Za-z0-9._+-]{1,64})"'
    $metadataMatches = [regex]::Matches($text, $pattern,
        [Text.RegularExpressions.RegexOptions]::CultureInvariant)
    if ($metadataMatches.Count -ne 1) {
        throw 'WO-036 collision metadata target is not unique.'
    }

    $document = [System.Text.Json.JsonDocument]::Parse($text)
    try {
        $manifest = $document.RootElement.GetProperty('manifest')
        $data = $document.RootElement.GetProperty('data')
        $exportId = [guid]::ParseExact($manifest.GetProperty('exportId').GetString(), 'D')
        $dataSha256 = $manifest.GetProperty('dataSha256').GetString()
        $status = $manifest.GetProperty('validation').GetProperty('status').GetString()
        $dataBytes = $script:Utf8NoBom.GetBytes($data.GetRawText())
        try {
            if ($status -ne 'HUMAN_VALIDATED' `
                    -or $dataSha256 -notmatch '^[0-9a-f]{64}$' `
                    -or (Get-WO036Sha256Hex -Bytes $dataBytes) -cne $dataSha256) {
                throw 'invalid validated envelope'
            }
        }
        finally {
            [Array]::Clear($dataBytes, 0, $dataBytes.Length)
        }
    }
    catch {
        throw 'WO-036 collision source is not one valid HUMAN_VALIDATED envelope.'
    }
    finally {
        $document.Dispose()
    }

    $value = $metadataMatches[0].Groups['value'].Value
    $replacementFirst = if ($value[0] -ceq 'z') { 'y' } else { 'z' }
    $replacement = $replacementFirst + $value.Substring(1)
    $valueIndex = $metadataMatches[0].Groups['value'].Index
    $mutatedText = $text.Remove($valueIndex, $value.Length).Insert(
        $valueIndex, $replacement)
    $mutatedBytes = $script:Utf8NoBom.GetBytes($mutatedText)
    if ($mutatedBytes.Length -ne $OriginalBytes.Length `
            -or [Security.Cryptography.CryptographicOperations]::FixedTimeEquals(
                $OriginalBytes, $mutatedBytes)) {
        [Array]::Clear($mutatedBytes, 0, $mutatedBytes.Length)
        throw 'WO-036 collision mutation is not an exact same-size metadata-only change.'
    }
    return [pscustomobject]@{
        Bytes = $mutatedBytes
        ExportId = $exportId
        DataSha256 = $dataSha256
        FileSha256 = Get-WO036Sha256Hex -Bytes $mutatedBytes
    }
}

function Assert-WO036TwoCallPrecondition {
    param(
        [Parameter(Mandatory = $true)][object]$State,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )
    $a = Get-WO036DatabaseProof -State $State -Role LocalLabA -ExportId $ExportId
    $b = Get-WO036DatabaseProof -State $State -Role LocalLabB -ExportId $ExportId
    $receiver = Get-WO036DatabaseProof -State $State -Role Receiver -ExportId $ExportId
    if ([int]$a.manifestCount -ne 1 -or [int]$a.humanValidatedCount -ne 1 `
            -or [int]$a.syntheticOnlyCount -ne 1 -or [int]$a.deliveryCount -ne 1 `
            -or $a.deliveryState -ne 'DELIVERED' -or [int]$a.attemptCount -ne 1 `
            -or [int]$a.resultCount -ne 1 -or [int]$a.httpStatus -ne 201 `
            -or $a.safeResultCode -cne 'HTTP_201_IMPORTED' `
            -or [int]$b.manifestCount -ne 1 -or [int]$b.humanValidatedCount -ne 1 `
            -or [int]$b.syntheticOnlyCount -ne 1 -or [int]$b.deliveryCount -ne 1 `
            -or $b.deliveryState -ne 'DUPLICATE_CONFIRMED' `
            -or [int]$b.attemptCount -ne 1 -or [int]$b.resultCount -ne 1 `
            -or [int]$b.httpStatus -ne 200 `
            -or $b.safeResultCode -cne 'HTTP_DUPLICATE_CONFIRMED' `
            -or [int]$receiver.receiptCount -ne 1 `
            -or [int]$receiver.payloadCount -ne 1 `
            -or [int]$receiver.outboxCount -ne 1 `
            -or [int]$receiver.auditImportedCount -ne 1 `
            -or [int]$receiver.auditDuplicateCount -ne 1 `
            -or [int]$receiver.auditDivergenceCount -ne 0 `
            -or [int]$receiver.auditTotalCount -ne 2 `
            -or [int]$receiver.globalReceiptCount -ne 1 `
            -or [int]$receiver.globalPayloadCount -ne 1 `
            -or [int]$receiver.globalOutboxJ7Count -ne 1 `
            -or [int]$receiver.globalAuditTotalCount -ne 2) {
        throw 'WO-036 collision probe requires the exact 201 then 200 two-call proof.'
    }
    if ($a.fileSha256 -cne $b.fileSha256 `
            -or $a.dataSha256 -cne $b.dataSha256 `
            -or $a.fileSha256 -cne $receiver.fileSha256 `
            -or $a.dataSha256 -cne $receiver.dataSha256) {
        throw 'WO-036 A/B/receiver identity is not byte-equivalent before collision.'
    }
    return [pscustomobject]@{ A = $a; B = $b; Receiver = $receiver }
}

function Invoke-WO036CollisionProbeCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    [void](Assert-WO036RegisteredTooling -State $state)
    foreach ($component in @('LocalLabPreparation', 'LocalLabA', 'LocalLabB')) {
        if ($null -ne (Get-WO036OwnedProcess -State $state -Component $component `
                -AllowMissing)) {
            throw 'WO-036 collision probe requires all Local Lab processes to be stopped.'
        }
        Assert-WO036GracefulStopResult -State $state -Component $component
    }
    $receiverProcess = Get-WO036OwnedProcess -State $state -Component Receiver
    if (-not (Test-WO036ExactLoopbackListener -Port 8444 `
            -ProcessId ([int]$receiverProcess.ProcessId))) {
        throw 'WO-036 collision probe requires the exact receiver loopback listener.'
    }
    $freeze = Read-WO036PrivateToolJson -State $state `
        -FileName 'pre-call-freeze.json'
    if ($freeze.result -cne 'PASS' -or [guid]$freeze.artifact.exportId -ne $ExportId) {
        throw 'WO-036 collision probe is not bound to the frozen synthetic export.'
    }
    $proof = Assert-WO036TwoCallPrecondition -State $state -ExportId $ExportId
    $exportPath = Get-WO036ExportPath -State $state -ExportId $ExportId
    $originalBytes = [IO.File]::ReadAllBytes($exportPath)
    $mutation = $null
    $claim = $null
    $claimCreated = $false
    $client = $null
    $request = $null
    $content = $null
    $response = $null
    $claimPath = Join-Path (Get-WO036PrivateToolsStateDirectory -State $state) `
        'collision-probe.json'
    try {
        if ((Get-WO036Sha256Hex -Bytes $originalBytes) -cne $proof.A.fileSha256 `
                -or $originalBytes.Length -ne [long]$proof.A.fileSizeBytes) {
            throw 'WO-036 validated export file no longer matches the immutable ledger.'
        }
        $mutation = New-WO036CollisionMutation -OriginalBytes $originalBytes
        if ($mutation.ExportId -ne $ExportId `
                -or $mutation.DataSha256 -cne $proof.A.dataSha256 `
                -or $mutation.FileSha256 -ceq $proof.A.fileSha256) {
            throw 'WO-036 collision mutation identity is inconsistent.'
        }
        $idempotencyKey = "j7:$ExportId`:sha256:$($mutation.FileSha256)"
        $idempotencyKeySha256 = Get-WO036Sha256Hex -Bytes (
            $script:Utf8NoBom.GetBytes($idempotencyKey))
        $claim = [ordered]@{
            schemaVersion = '1.0'
            workOrder = $script:WorkOrder
            claimedAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
            status = 'CLAIMED_UNKNOWN_CONSUMED'
            probeType = 'FULL_J7_ENVELOPE_METADATA_ONLY_MUTATION'
            exportId = $ExportId.ToString()
            importRouteCallOrdinal = 3
            maximumImportRouteCalls = $script:MaximumImportRouteCalls
            originalFileSha256 = [string]$proof.A.fileSha256
            mutatedFileSha256 = [string]$mutation.FileSha256
            dataSha256 = [string]$mutation.DataSha256
            idempotencyKeySha256 = $idempotencyKeySha256
        }
        Write-WO036PrivateJson -Path $claimPath -Value $claim -CreateNew
        $claimCreated = $true

        if (-not (Test-WO036ExactLoopbackListener -Port 8444 `
                -ProcessId ([int]$receiverProcess.ProcessId))) {
            throw 'WO-036 receiver listener identity changed after collision claim.'
        }
        $client = New-WO036HttpClient -ClientCertificateSha256 `
            ([string]$state.Config.localLab.clientCertificateSha256)
        $request = [Net.Http.HttpRequestMessage]::new(
            [Net.Http.HttpMethod]::Post,
            $script:ReceiverOrigin + $script:ReceiverPath)
        $content = [Net.Http.ByteArrayContent]::new($mutation.Bytes)
        $content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::Parse(
            $script:RequestMediaType)
        $request.Content = $content
        [void]$request.Headers.TryAddWithoutValidation('Accept', $script:AckMediaType)
        [void]$request.Headers.TryAddWithoutValidation(
            'Idempotency-Key', $idempotencyKey)
        [void]$request.Headers.TryAddWithoutValidation('X-J7-Protocol-Version', '1.0')
        [void]$request.Headers.TryAddWithoutValidation(
            'X-J7-Export-Id', $ExportId.ToString())
        [void]$request.Headers.TryAddWithoutValidation(
            'X-J7-File-SHA256', $mutation.FileSha256)
        [void]$request.Headers.TryAddWithoutValidation(
            'X-J7-Data-SHA256', $mutation.DataSha256)
        $response = $client.Send(
            $request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead)
        if ([int]$response.StatusCode -ne 409) {
            throw 'WO-036 collision probe did not receive the exact terminal conflict.'
        }
        $after = Get-WO036DatabaseProof -State $state -Role Receiver -ExportId $ExportId
        $audit = Get-WO036CollisionAuditProof -State $state -ExportId $ExportId `
            -MutatedFileSha256 $mutation.FileSha256 -DataSha256 $mutation.DataSha256
        if ([int]$after.receiptCount -ne 1 `
                -or [int]$after.payloadCount -ne 1 `
                -or [int]$after.outboxCount -ne 1 `
                -or [int]$after.auditImportedCount -ne 1 `
                -or [int]$after.auditDuplicateCount -ne 1 `
                -or [int]$after.auditDivergenceCount -ne 1 `
                -or [int]$after.auditTotalCount -ne 3 `
                -or [int]$after.globalReceiptCount -ne 1 `
                -or [int]$after.globalPayloadCount -ne 1 `
                -or [int]$after.globalOutboxJ7Count -ne 1 `
                -or [int]$after.globalAuditTotalCount -ne 3 `
                -or [int]$audit.divergenceCount -ne 1 `
                -or [int]$audit.correlationCount -ne 1 `
                -or [int]$audit.reasonCount -ne 1 `
                -or $audit.reasonCode -cne 'EXPORT_ID_DIVERGENCE' `
                -or $audit.idempotencyKeySha256 -cne $idempotencyKeySha256 `
                -or $after.fileSha256 -cne $proof.Receiver.fileSha256 `
                -or $after.payloadComputedSha256 -cne
                    $proof.Receiver.payloadComputedSha256 `
                -or -not (Test-WO036ExactLoopbackListener -Port 8444 `
                    -ProcessId ([int]$receiverProcess.ProcessId))) {
            throw 'WO-036 collision receiver postconditions are not satisfied.'
        }
        $claim.status = 'PASS_CONSUMED'
        $claim['completedAtUtc'] = [DateTimeOffset]::UtcNow.ToString('O')
        $claim['httpStatus'] = 409
        $claim['auditCorrelationCount'] = 1
        $claim['auditReasonCode'] = 'EXPORT_ID_DIVERGENCE'
        Update-WO036PrivateJsonAtomic -Path $claimPath -Value $claim
        Write-Output 'WO036_COLLISION_PROBE=PASS'
        Write-Output "WO036_IMPORT_ROUTE_CALLS=$script:MaximumImportRouteCalls"
        Write-Output "WO036_MAXIMUM_IMPORT_ROUTE_CALLS=$script:MaximumImportRouteCalls"
        Write-Output 'WO036_COLLISION_HTTP_STATUS=409'
        Write-Output 'WO036_COLLISION_PROBE_TYPE=FULL_J7_ENVELOPE_METADATA_ONLY_MUTATION'
        Write-Output 'WO036_PROBE_REUSABLE=NO'
    }
    catch {
        if ($claimCreated) {
            $claim.status = 'FAILED_OR_UNKNOWN_CONSUMED'
            $claim['completedAtUtc'] = [DateTimeOffset]::UtcNow.ToString('O')
            try {
                Update-WO036PrivateJsonAtomic -Path $claimPath -Value $claim
            }
            catch {
                throw 'WO-036 collision failure could not preserve its consumed claim.'
            }
        }
        throw
    }
    finally {
        if ($null -ne $response) { $response.Dispose() }
        if ($null -ne $request) { $request.Dispose() }
        elseif ($null -ne $content) { $content.Dispose() }
        if ($null -ne $client) { $client.Dispose() }
        if ($null -ne $mutation -and $null -ne $mutation.Bytes) {
            [Array]::Clear($mutation.Bytes, 0, $mutation.Bytes.Length)
        }
        if ($null -ne $originalBytes) {
            [Array]::Clear($originalBytes, 0, $originalBytes.Length)
        }
    }
}

function Export-WO036RedactedEvidenceCore {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId,
        [Parameter(Mandatory = $true)][ValidateSet('PRE_COLLISION', 'FINAL')]
        [string]$Phase,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $state = Read-WO036PrivateState -StatePath $StatePath
    $toolingProof = Assert-WO036RegisteredTooling -State $state
    $resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
    $outputDirectory = Split-Path -Parent $resolvedOutput
    if (-not [IO.Path]::IsPathFullyQualified($OutputPath) `
            -or [IO.Path]::GetExtension($resolvedOutput) -cne '.json' `
            -or -not (Test-WO036PathWithin -Candidate $resolvedOutput `
                -Root $state.PrivateRoot) `
            -or -not (Test-Path -LiteralPath $outputDirectory -PathType Container) `
            -or (Test-Path -LiteralPath $resolvedOutput)) {
        throw 'WO-036 evidence destination must be one new private JSON file.'
    }
    Assert-WO036NoReparsePathChain -Candidate $outputDirectory -Root $state.PrivateRoot
    Assert-WO036PrivateAcl -Path $outputDirectory

    $clone = Read-WO036PrivateToolJson -State $state -FileName 'database-clone.json'
    $freeze = Read-WO036PrivateToolJson -State $state -FileName 'pre-call-freeze.json'
    $collisionPath = Join-Path (Get-WO036PrivateToolsStateDirectory -State $state) `
        'collision-probe.json'
    $collision = $null
    if ($Phase -eq 'FINAL') {
        $collision = Read-WO036PrivateToolJson -State $state `
            -FileName 'collision-probe.json'
    }
    elseif (Test-Path -LiteralPath $collisionPath) {
        throw 'WO-036 PRE_COLLISION evidence is unavailable after a collision claim.'
    }

    $receiverProcess = Get-WO036OwnedProcess -State $state -Component Receiver
    $localLabLiveCount = 0
    foreach ($component in @('LocalLabPreparation', 'LocalLabA', 'LocalLabB')) {
        if ($null -ne (Get-WO036OwnedProcess -State $state -Component $component `
                -AllowMissing)) {
            $localLabLiveCount++
        }
    }
    $receiverListenerExact = Test-WO036ExactLoopbackListener -Port 8444 `
        -ProcessId ([int]$receiverProcess.ProcessId)
    $localLabListenerCount = @(Get-NetTCPConnection -State Listen -LocalPort 8087 `
        -ErrorAction SilentlyContinue).Count
    $stopSet = Get-WO036StopResultSet -State $state
    $expectedStoppedComponents = @('LocalLabPreparation', 'LocalLabA', 'LocalLabB')
    $gracefulStopCount = @($stopSet.results | Where-Object {
        $_.component -in $expectedStoppedComponents `
            -and $_.status -ceq 'STOPPED_GRACEFULLY'
    }).Count
    $forcedStopCount = @($stopSet.results | Where-Object {
        $_.status -ceq 'STOPPED_EXACT_FORCED'
    }).Count
    $unexpectedStopCount = @($stopSet.results | Where-Object {
        $_.component -notin $expectedStoppedComponents `
            -or $_.status -cne 'STOPPED_GRACEFULLY'
    }).Count

    $a = Get-WO036DatabaseProof -State $state -Role LocalLabA -ExportId $ExportId
    $b = Get-WO036DatabaseProof -State $state -Role LocalLabB -ExportId $ExportId
    $receiver = Get-WO036DatabaseProof -State $state -Role Receiver -ExportId $ExportId
    $logProof = Get-WO036PrivateLogRedactionProof -State $state
    Assert-WO036ExactPrivateLogSet -Proof $logProof `
        -ExpectedNames $script:CompletedLocalLabLogNames -Gate $Phase
    $exportPath = Get-WO036ExportPath -State $state -ExportId $ExportId
    $fileBytes = [IO.File]::ReadAllBytes($exportPath)
    try {
        $fileSha256 = Get-WO036Sha256Hex -Bytes $fileBytes
        $expectedDivergence = if ($Phase -eq 'FINAL') { 1 } else { 0 }
        $expectedAudits = if ($Phase -eq 'FINAL') { 3 } else { 2 }
        $importRouteCalls = $expectedAudits
        $collisionAudit = [pscustomobject][ordered]@{
            divergenceCount = 0
            correlationCount = 0
            reasonCount = 0
            reasonCode = ''
            idempotencyKeySha256 = ''
        }
        if ($Phase -eq 'FINAL' `
                -and $null -ne $collision `
                -and $collision.mutatedFileSha256 -match '^[0-9a-f]{64}$' `
                -and $collision.dataSha256 -match '^[0-9a-f]{64}$') {
            $collisionAudit = Get-WO036CollisionAuditProof -State $state `
                -ExportId $ExportId `
                -MutatedFileSha256 ([string]$collision.mutatedFileSha256) `
                -DataSha256 ([string]$collision.dataSha256)
        }
        $claimsExact = $clone.status -ceq 'PASS_CONSUMED' `
            -and [guid]$clone.exportId -eq $ExportId `
            -and $freeze.result -ceq 'PASS' `
            -and [guid]$freeze.artifact.exportId -eq $ExportId `
            -and $freeze.artifact.fileSha256 -ceq $fileSha256 `
            -and $freeze.tooling.registrationManifestSha256 -ceq
                $toolingProof.registrationManifestSha256
        $collisionClaimExact = if ($Phase -eq 'PRE_COLLISION') {
            $null -eq $collision
        }
        else {
            $null -ne $collision `
                -and $collision.status -ceq 'PASS_CONSUMED' `
                -and $collision.probeType -ceq
                    'FULL_J7_ENVELOPE_METADATA_ONLY_MUTATION' `
                -and [guid]$collision.exportId -eq $ExportId `
                -and [int]$collision.importRouteCallOrdinal -eq 3 `
                -and [int]$collision.maximumImportRouteCalls -eq
                    $script:MaximumImportRouteCalls `
                -and [int]$collision.httpStatus -eq 409 `
                -and [int]$collision.auditCorrelationCount -eq 1 `
                -and $collision.auditReasonCode -ceq 'EXPORT_ID_DIVERGENCE' `
                -and $collision.idempotencyKeySha256 -ceq
                    $collisionAudit.idempotencyKeySha256 `
                -and [int]$collisionAudit.divergenceCount -eq 1 `
                -and [int]$collisionAudit.correlationCount -eq 1 `
                -and [int]$collisionAudit.reasonCount -eq 1 `
                -and $collisionAudit.reasonCode -ceq 'EXPORT_ID_DIVERGENCE'
        }
        $assertions = [ordered]@{
            claimsPhaseExact = $claimsExact -and $collisionClaimExact
            toolingStillFrozen = $freeze.tooling.registrationManifestSha256 -ceq
                $toolingProof.registrationManifestSha256 `
                -and $toolingProof.localLabRegisteredHead -match '^[0-9a-f]{40}$' `
                -and $toolingProof.receiverHead -ceq $script:ExpectedReceiverHeadCommit
            topologyReceiverOnly = $localLabLiveCount -eq 0 `
                -and $receiverListenerExact -and $localLabListenerCount -eq 0
            localLabStopsGraceful = $gracefulStopCount -eq 3 `
                -and $forcedStopCount -eq 0 -and $unexpectedStopCount -eq 0 `
                -and @($stopSet.results).Count -eq 3
            localLabAImported = [int]$a.manifestCount -eq 1 -and
                [int]$a.humanValidatedCount -eq 1 -and
                [int]$a.syntheticOnlyCount -eq 1 -and
                $a.deliveryState -ceq 'DELIVERED' -and
                [int]$a.attemptCount -eq 1 -and [int]$a.resultCount -eq 1 -and
                [int]$a.httpStatus -eq 201 -and
                $a.safeResultCode -ceq 'HTTP_201_IMPORTED'
            localLabBDuplicate = [int]$b.manifestCount -eq 1 -and
                [int]$b.humanValidatedCount -eq 1 -and
                [int]$b.syntheticOnlyCount -eq 1 -and
                $b.deliveryState -ceq 'DUPLICATE_CONFIRMED' -and
                [int]$b.attemptCount -eq 1 -and [int]$b.resultCount -eq 1 -and
                [int]$b.httpStatus -eq 200 -and
                $b.safeResultCode -ceq 'HTTP_DUPLICATE_CONFIRMED'
            exactImportRouteCalls = [int]$receiver.auditImportedCount -eq 1 -and
                [int]$receiver.auditDuplicateCount -eq 1 -and
                [int]$receiver.auditDivergenceCount -eq $expectedDivergence -and
                [int]$receiver.auditTotalCount -eq $expectedAudits -and
                [int]$receiver.globalAuditTotalCount -eq $expectedAudits -and
                $importRouteCalls -le $script:MaximumImportRouteCalls
            receiverSingleDurableImport = [int]$receiver.receiptCount -eq 1 -and
                [int]$receiver.payloadCount -eq 1 -and
                [int]$receiver.outboxCount -eq 1 -and
                [int]$receiver.globalReceiptCount -eq 1 -and
                [int]$receiver.globalPayloadCount -eq 1 -and
                [int]$receiver.globalOutboxJ7Count -eq 1 -and
                [int]$receiver.outboxPendingCount -eq 1 -and
                [int]$receiver.outboxAttemptCount -eq 0
            receiverContractAndRetention = [int]$receiver.schemaCount -eq 1 -and
                [int]$receiver.retentionCount -eq 1
            byteIdentity = $fileSha256 -ceq $a.fileSha256 -and
                $fileSha256 -ceq $b.fileSha256 -and
                $fileSha256 -ceq $receiver.fileSha256 -and
                $fileSha256 -ceq $receiver.payloadComputedSha256 -and
                $fileBytes.Length -eq [long]$receiver.payloadLengthBytes -and
                $fileBytes.Length -eq [long]$receiver.payloadSizeBytes
            dataIdentity = $a.dataSha256 -ceq $b.dataSha256 -and
                $a.dataSha256 -ceq $receiver.dataSha256
            privateLogsRedacted = [int]$logProof.ForbiddenOccurrenceCount -eq 0 `
                -and [int]$logProof.FileCount -eq
                    $script:CompletedLocalLabLogNames.Count
        }
        $allPass = -not ($assertions.Values -contains $false)
        $evidence = [ordered]@{
            schemaVersion = '1.0'
            workOrder = $script:WorkOrder
            evidencePhase = $Phase
            generatedAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
            result = if ($allPass) { 'PASS' } else { 'FAIL' }
            scope = [ordered]@{
                syntheticOnly = $true
                loopbackOnly = $true
                providerCalls = 0
                remoteReceiverCalls = 0
                importRouteCalls = $importRouteCalls
                maximumImportRouteCalls = $script:MaximumImportRouteCalls
                shutdownControlCallsBeforeEvidence = 3
                automaticRetries = 0
                postCleanupAttestationRequired = $true
            }
            artifact = [ordered]@{
                exportId = $ExportId.ToString()
                sizeBytes = $fileBytes.Length
                fileSha256 = $fileSha256
                dataSha256 = [string]$a.dataSha256
            }
            tooling = [ordered]@{
                localLabRegisteredHead = $toolingProof.localLabRegisteredHead
                localLabCurrentHead = $toolingProof.localLabCurrentHead
                receiverHead = $toolingProof.receiverHead
                javaSha256 = $toolingProof.javaSha256
                localLabJarSha256 = $toolingProof.localLabJarSha256
                receiverJarSha256 = $toolingProof.receiverJarSha256
                dockerEndpointSha256 = $toolingProof.dockerEndpointSha256
                registrationManifestSha256 =
                    $toolingProof.registrationManifestSha256
                files = @($toolingProof.toolingFiles)
            }
            processTopology = [ordered]@{
                receiverExactLoopbackListener = $receiverListenerExact
                localLabListenerCount = $localLabListenerCount
                localLabLiveProcessCount = $localLabLiveCount
                gracefulStopCount = $gracefulStopCount
                forcedStopCount = $forcedStopCount
                cleanupAttestationPending = $true
            }
            localLabA = [ordered]@{
                state = [string]$a.deliveryState
                attempts = [int]$a.attemptCount
                results = [int]$a.resultCount
                httpStatus = [int]$a.httpStatus
                safeResultCode = [string]$a.safeResultCode
                sizeBytes = [long]$a.fileSizeBytes
                fileSha256 = [string]$a.fileSha256
                dataSha256 = [string]$a.dataSha256
            }
            localLabB = [ordered]@{
                state = [string]$b.deliveryState
                attempts = [int]$b.attemptCount
                results = [int]$b.resultCount
                httpStatus = [int]$b.httpStatus
                safeResultCode = [string]$b.safeResultCode
                sizeBytes = [long]$b.fileSizeBytes
                fileSha256 = [string]$b.fileSha256
                dataSha256 = [string]$b.dataSha256
            }
            receiver = [ordered]@{
                receipts = [int]$receiver.receiptCount
                payloads = [int]$receiver.payloadCount
                auditsImported = [int]$receiver.auditImportedCount
                auditsDuplicate = [int]$receiver.auditDuplicateCount
                auditsDivergenceRejected = [int]$receiver.auditDivergenceCount
                auditsTotal = [int]$receiver.auditTotalCount
                globalReceipts = [int]$receiver.globalReceiptCount
                globalPayloads = [int]$receiver.globalPayloadCount
                globalAuditsTotal = [int]$receiver.globalAuditTotalCount
                globalOutboxJ7 = [int]$receiver.globalOutboxJ7Count
                outboxAccepted = [int]$receiver.outboxCount
                outboxPending = [int]$receiver.outboxPendingCount
                outboxAttempts = [int]$receiver.outboxAttemptCount
                payloadSizeBytes = [long]$receiver.payloadSizeBytes
                payloadComputedSha256 = [string]$receiver.payloadComputedSha256
                fileSha256 = [string]$receiver.fileSha256
                dataSha256 = [string]$receiver.dataSha256
            }
            collision = [ordered]@{
                status = if ($Phase -eq 'FINAL') { 'PASS_CONSUMED' } else { 'NOT_CLAIMED' }
                probeType = if ($Phase -eq 'FINAL') {
                    'FULL_J7_ENVELOPE_METADATA_ONLY_MUTATION'
                }
                else { 'NOT_EXECUTED' }
                httpStatus = if ($Phase -eq 'FINAL') { 409 } else { 0 }
                auditCorrelationCount = [int]$collisionAudit.correlationCount
                auditReasonCode = [string]$collisionAudit.reasonCode
                idempotencyKeySha256 = [string]$collisionAudit.idempotencyKeySha256
            }
            privateLogScan = [ordered]@{
                files = [int]$logProof.FileCount
                fileNames = @($logProof.FileNames)
                sizeBytes = [long]$logProof.SizeBytes
                forbiddenOccurrences = [int]$logProof.ForbiddenOccurrenceCount
            }
            assertions = $assertions
        }
        Write-WO036PrivateJson -Path $resolvedOutput -Value $evidence -CreateNew
        $evidenceBytes = [IO.File]::ReadAllBytes($resolvedOutput)
        try {
            Write-Output "WO036_EVIDENCE_RESULT=$($evidence.result)"
            Write-Output "WO036_EVIDENCE_PHASE=$Phase"
            Write-Output "WO036_IMPORT_ROUTE_CALLS=$importRouteCalls"
            Write-Output "WO036_MAXIMUM_IMPORT_ROUTE_CALLS=$script:MaximumImportRouteCalls"
            Write-Output "WO036_EVIDENCE_SIZE_BYTES=$($evidenceBytes.Length)"
            Write-Output "WO036_EVIDENCE_SHA256=$(Get-WO036Sha256Hex -Bytes $evidenceBytes)"
        }
        finally {
            [Array]::Clear($evidenceBytes, 0, $evidenceBytes.Length)
        }
        if (-not $allPass) {
            throw 'WO-036 redacted evidence assertions failed.'
        }
    }
    finally {
        [Array]::Clear($fileBytes, 0, $fileBytes.Length)
    }
}

function Enter-WO036ToolsLock {
    param([Parameter(Mandatory = $true)][string]$StatePath)

    $resolvedState = Resolve-WO036ExistingPath -Value $StatePath -PathType Leaf `
        -Description 'state file for lock acquisition'
    $privateRoot = Split-Path -Parent $resolvedState
    if (Test-WO036PathWithin -Candidate $resolvedState -Root $script:RepositoryRoot) {
        throw 'WO-036 tools lock must stay outside the repository.'
    }
    Assert-WO036PrivateAcl -Path $privateRoot -RequireProtected
    Assert-WO036NoReparsePathChain -Candidate $resolvedState -Root $privateRoot
    $lockPath = Join-Path $privateRoot '.wo036-tools.lock'
    $resolvedLock = Resolve-WO036ExistingPath -Value $lockPath -PathType Leaf `
        -Description 'shared tools lock'
    Assert-WO036NoReparsePathChain -Candidate $resolvedLock -Root $privateRoot
    Assert-WO036PrivateAcl -Path $resolvedLock
    try {
        return [IO.FileStream]::new(
            $resolvedLock,
            [IO.FileMode]::Open,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None)
    }
    catch {
        throw 'WO-036 shared tools lock is unavailable; concurrent mutation is refused.'
    }
}

function Invoke-WO036LockedOperation {
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][scriptblock]$Operation
    )

    $lock = Enter-WO036ToolsLock -StatePath $StatePath
    try {
        return & $Operation
    }
    finally {
        $lock.Dispose()
    }
}

function Register-WO036ExecutableArtifacts {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][string]$JavaExecutable,
        [Parameter(Mandatory = $true)][string]$LocalLabJar,
        [Parameter(Mandatory = $true)][string]$ExpectedLocalLabJarSha256,
        [Parameter(Mandatory = $true)][string]$ReceiverRepositoryRoot,
        [Parameter(Mandatory = $true)][string]$ReceiverJar,
        [Parameter(Mandatory = $true)][string]$ExpectedReceiverJarSha256
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Register-WO036ExecutableArtifactsCore @parameters
    }
}

function Start-WO036Component {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)]
        [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
        [string]$Component,
        [guid]$ExportId = [guid]::Empty
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Start-WO036ComponentCore @parameters
    }
}

function Stop-WO036Component {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)]
        [ValidateSet('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')]
        [string]$Component
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Stop-WO036ComponentCore @parameters
    }
}

function Copy-WO036LocalLabDatabase {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Copy-WO036LocalLabDatabaseCore @parameters
    }
}

function Export-WO036PreCallFreeze {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Export-WO036PreCallFreezeCore @parameters
    }
}

function Test-WO036FrozenTooling {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Test-WO036FrozenToolingCore @parameters
    }
}

function Invoke-WO036CollisionProbe {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Invoke-WO036CollisionProbeCore @parameters
    }
}

function Export-WO036RedactedEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$StatePath,
        [Parameter(Mandatory = $true)][guid]$ExportId,
        [Parameter(Mandatory = $true)][ValidateSet('PRE_COLLISION', 'FINAL')]
        [string]$Phase,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Export-WO036RedactedEvidenceCore @parameters
    }
}

function Test-WO036PrivateLogRedaction {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$StatePath)
    $parameters = @{} + $PSBoundParameters
    Invoke-WO036LockedOperation -StatePath $StatePath -Operation {
        Test-WO036PrivateLogRedactionCore @parameters
    }
}

Export-ModuleMember -Function @(
    'Register-WO036ExecutableArtifacts',
    'Read-WO036PrivateState',
    'Start-WO036Component',
    'Stop-WO036Component',
    'Copy-WO036LocalLabDatabase',
    'Export-WO036PreCallFreeze',
    'Test-WO036FrozenTooling',
    'Invoke-WO036CollisionProbe',
    'Export-WO036RedactedEvidence',
    'Test-WO036PrivateLogRedaction'
)
