[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [string]$PrivateRoot,

    [Parameter(Mandatory = $true)]
    [string]$AttestedRendererPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$AttestedRendererSha256,

    [Parameter(Mandatory = $true)]
    [IO.FileStream]$AttestedRendererStream,

    [Parameter(Mandatory = $true)]
    [IO.FileStream]$AttestedSourceRendererStream,

    [switch]$SyntheticQualification
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the WO-034 renderer.'
}

$rendererVersion = '1.1.0'
$expectedTemplateSha256 = 'bfb9195b78aa663c2e7bb36e896b813087afb1d4e1609346bc9cb0ff21ad8356'
$expectedSyntheticInputSha256 = 'abdafb5f87ee2fe0f46b2cfef8a33daebb5f3f6b4d99f86fd49c1de70bad8114'
$expectedBootstrapSha256 = '3741fa3fdf4097f155c0d58891cca10bb9951561e996edff5f75dba78b75e731'
$ownerDeclaredProductionVpsIpv4 = '51.255.167.32'
$destinationUrl = 'https://corporate.sofascore.com/contact'
$formCategory = 'Product'
$formTopic = 'API'
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Get-WO034Sha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Test-WO034ByteArrayEqual {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Left,
        [Parameter(Mandatory = $true)][byte[]]$Right
    )

    if ($Left.Length -ne $Right.Length) {
        return $false
    }
    for ($index = 0; $index -lt $Left.Length; $index++) {
        if ($Left[$index] -ne $Right[$index]) {
            return $false
        }
    }
    return $true
}

function Read-WO034AttestedStreamBytes {
    param(
        [Parameter(Mandatory = $true)][IO.FileStream]$Stream,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Stream.SafeFileHandle.IsClosed -or $Stream.SafeFileHandle.IsInvalid -or
        -not $Stream.CanRead -or -not $Stream.CanSeek -or $Stream.CanWrite -or
        $Stream.Length -lt 1 -or $Stream.Length -gt 1048576) {
        throw "$Description is outside the attested stream byte bound."
    }
    $originalPosition = $Stream.Position
    $buffer = [IO.MemoryStream]::new()
    try {
        $Stream.Position = 0
        $Stream.CopyTo($buffer)
        return $buffer.ToArray()
    } finally {
        $Stream.Position = $originalPosition
        $buffer.Dispose()
    }
}

function Assert-WO034ConcurrentWriteOpenDenied {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $writeProbe = $null
    try {
        try {
            $writeProbe = [IO.File]::Open(
                $Path, [IO.FileMode]::Open, [IO.FileAccess]::Write,
                [IO.FileShare]::ReadWrite)
        } catch [IO.IOException] {
            $win32ErrorCode = $_.Exception.HResult -band 0xffff
            if ($win32ErrorCode -eq 32) {
                return
            }
            throw
        }
        throw "$Description did not deny a concurrent write handle."
    } finally {
        if ($null -ne $writeProbe) {
            $writeProbe.Dispose()
        }
    }
}

function Get-WO034CanonicalPrivatePath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$PrivateRoot,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not [IO.Path]::IsPathFullyQualified($Path)) {
        throw "$Description must be an absolute path."
    }
    $canonicalPath = [IO.Path]::GetFullPath($Path)
    $canonicalPrivateRoot = [IO.Path]::GetFullPath($PrivateRoot)
    $privatePrefix = $canonicalPrivateRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if ($canonicalPath -cne $canonicalPrivateRoot -and
        -not $canonicalPath.StartsWith($privatePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description must remain inside the WO-034 private root."
    }
    return $canonicalPath
}

function ConvertFrom-WO034StrictUtf8Bytes {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($Bytes.Length -ge 3 -and $Bytes[0] -eq 0xEF -and $Bytes[1] -eq 0xBB -and
        $Bytes[2] -eq 0xBF) {
        throw "$Description must be UTF-8 without BOM."
    }
    try {
        return $utf8.GetString($Bytes)
    } catch {
        throw "$Description is not strict UTF-8."
    }
}

function Assert-WO034NoReparsePath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Anchor,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $canonicalPath = [IO.Path]::GetFullPath($Path)
    $canonicalAnchor = [IO.Path]::GetFullPath($Anchor)
    $anchorPrefix = $canonicalAnchor.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if ($canonicalPath -cne $canonicalAnchor -and
        -not $canonicalPath.StartsWith($anchorPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description escapes its trusted anchor."
    }
    if (-not (Test-Path -LiteralPath $canonicalAnchor)) {
        throw "$Description trusted anchor does not exist."
    }

    $current = $canonicalAnchor
    if (((Get-Item -LiteralPath $current -Force).Attributes -band
            [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description trusted anchor must not be a reparse point."
    }
    $relativePath = [IO.Path]::GetRelativePath($canonicalAnchor, $canonicalPath)
    if ($relativePath -ceq '.') {
        return
    }
    foreach ($segment in $relativePath.Split(
            [char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar),
            [StringSplitOptions]::RemoveEmptyEntries)) {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current)) {
            throw "$Description and all its ancestors must already exist."
        }
        if (((Get-Item -LiteralPath $current -Force).Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "$Description must not traverse a reparse point."
        }
    }
}

function ConvertTo-WO034Sid {
    param([Parameter(Mandatory = $true)]$Identity)

    if ($Identity -is [Security.Principal.SecurityIdentifier]) {
        return $Identity.Value
    }
    if ($Identity -is [Security.Principal.NTAccount]) {
        return $Identity.Translate([Security.Principal.SecurityIdentifier]).Value
    }
    $identityText = [string]$Identity
    try {
        return [Security.Principal.SecurityIdentifier]::new($identityText).Value
    } catch {
        return [Security.Principal.NTAccount]::new($identityText).
            Translate([Security.Principal.SecurityIdentifier]).Value
    }
}

function Assert-WO034PrivateAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][Collections.Generic.HashSet[string]]$AllowedSids,
        [switch]$RequireProtected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $acl = Get-Acl -LiteralPath $Path
    $rawDescriptor = [Security.AccessControl.RawSecurityDescriptor]::new(
        $acl.GetSecurityDescriptorBinaryForm(), 0)
    if ($null -eq $rawDescriptor.DiscretionaryAcl) {
        throw "$Description must not have a null Windows DACL."
    }
    if ($RequireProtected -and -not $acl.AreAccessRulesProtected) {
        throw "$Description must have protected Windows ACL inheritance."
    }
    $ownerSid = ConvertTo-WO034Sid -Identity $acl.Owner
    if (-not $AllowedSids.Contains($ownerSid)) {
        throw "$Description has an owner outside the private ACL allowlist."
    }
    foreach ($rule in $acl.Access) {
        $ruleSid = ConvertTo-WO034Sid -Identity $rule.IdentityReference
        if (-not $AllowedSids.Contains($ruleSid)) {
            throw "$Description grants access outside the private ACL allowlist."
        }
    }
}

function Assert-WO034NoUntrustedWriteAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][Collections.Generic.HashSet[string]]$TrustedWriterSids,
        [switch]$RequireProtected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $acl = Get-Acl -LiteralPath $Path
    $rawDescriptor = [Security.AccessControl.RawSecurityDescriptor]::new(
        $acl.GetSecurityDescriptorBinaryForm(), 0)
    if ($null -eq $rawDescriptor.DiscretionaryAcl) {
        throw "$Description must not have a null Windows DACL."
    }
    if ($RequireProtected -and -not $acl.AreAccessRulesProtected) {
        throw "$Description must have protected Windows ACL inheritance."
    }
    $ownerSid = ConvertTo-WO034Sid -Identity $acl.Owner
    if (-not $TrustedWriterSids.Contains($ownerSid)) {
        throw "$Description has an owner outside the trusted writer allowlist."
    }
    $writeMask = [uint32]([int64](
        [Security.AccessControl.FileSystemRights]::WriteData -bor
        [Security.AccessControl.FileSystemRights]::AppendData -bor
        [Security.AccessControl.FileSystemRights]::WriteExtendedAttributes -bor
        [Security.AccessControl.FileSystemRights]::WriteAttributes -bor
        [Security.AccessControl.FileSystemRights]::Delete -bor
        [Security.AccessControl.FileSystemRights]::DeleteSubdirectoriesAndFiles -bor
        [Security.AccessControl.FileSystemRights]::ChangePermissions -bor
        [Security.AccessControl.FileSystemRights]::TakeOwnership))
    $genericWriteOrAllMask = [uint32](0x40000000 -bor 0x10000000)
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) {
            continue
        }
        $ruleSid = ConvertTo-WO034Sid -Identity $rule.IdentityReference
        $ruleMask = [uint32]([int64][int]$rule.FileSystemRights -band 0xffffffffL)
        if (-not $TrustedWriterSids.Contains($ruleSid) -and
            ((($ruleMask -band $writeMask) -ne 0) -or
                (($ruleMask -band $genericWriteOrAllMask) -ne 0))) {
            throw "$Description grants write-capable access outside the trusted writer allowlist."
        }
    }
}

function Get-WO034NormalizedOwnerValue {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [AllowEmptyString()][string]$Value
    )

    if ($Value -cne $Value.Trim()) {
        throw "Owner input $Key contains leading or trailing whitespace."
    }
    for ($index = 0; $index -lt $Value.Length; $index++) {
        $unicodeCategory = [Globalization.CharUnicodeInfo]::GetUnicodeCategory($Value, $index)
        if ($unicodeCategory -in @(
                [Globalization.UnicodeCategory]::Control,
                [Globalization.UnicodeCategory]::Format,
                [Globalization.UnicodeCategory]::LineSeparator,
                [Globalization.UnicodeCategory]::ParagraphSeparator)) {
            throw "Owner input $Key contains a forbidden invisible, control or directional character."
        }
        if ([char]::IsHighSurrogate($Value[$index])) {
            $index++
        }
    }
    if ($Value.IndexOfAny([char[]]'[]{}<>') -ge 0) {
        throw "Owner input $Key contains a reserved bracket delimiter."
    }
    $secretLikePatterns = @(
        '(?i)-----BEGIN\s+(?:RSA\s+|EC\s+|OPENSSH\s+)?PRIVATE\s+KEY-----',
        '(?i)\b(?:github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16})\b',
        ('(?i)\b(?:api[-_ ]?key|access[-_ ]?token|client[-_ ]?secret|password|passwd|' +
            'private[-_ ]?key)\s*[:=]\s*\S+'),
        '\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b',
        '(?i)\bAuthorization\s*:\s*(?:Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}',
        '(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{16,}',
        '(?i)\b(?:Cookie|Set-Cookie)\s*:\s*[^\s=;]+=[^\s;]{8,}',
        ('(?i)\b(?:sk-(?:proj|live|test)-[A-Za-z0-9_-]{10,}|' +
            'xox[baprs]-[A-Za-z0-9-]{10,}|glpat-[A-Za-z0-9_-]{10,})\b'),
        '(?i)\b(?:session(?:id)?|auth(?:entication)?[-_ ]?token)\s*[:=]\s*\S{8,}'
    )
    foreach ($secretLikePattern in $secretLikePatterns) {
        if ([regex]::IsMatch($Value, $secretLikePattern)) {
            throw "Owner input $Key contains secret-like material and cannot be rendered."
        }
    }
    if ($utf8.GetByteCount($Value) -gt 4096) {
        throw "Owner input $Key exceeds the 4096-byte bound."
    }
    return $Value.Normalize([Text.NormalizationForm]::FormC)
}

function Assert-WO034Enum {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string[]]$Allowed
    )

    if ($Allowed -cnotcontains $Value) {
        throw "Owner input $Key is outside its allowlist."
    }
}

function Get-WO034OtherDescription {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Details,
        [Parameter(Mandatory = $true)][hashtable]$Descriptions
    )

    if ($Value -ceq 'OTHER_WITH_DETAILS') {
        if ([string]::IsNullOrWhiteSpace($Details)) {
            throw "Owner input ${Key}_DETAILS is required."
        }
        return $Details
    }
    if (-not [string]::IsNullOrWhiteSpace($Details)) {
        throw "Owner input ${Key}_DETAILS must be empty unless $Key is OTHER_WITH_DETAILS."
    }
    return $Descriptions[$Value]
}

function Get-WO034DeclarationStatement {
    param(
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Details,
        [Parameter(Mandatory = $true)][string]$NoStatement,
        [Parameter(Mandatory = $true)][string]$YesPrefix
    )

    Assert-WO034Enum -Key $Key -Value $Value -Allowed @('NO', 'YES_WITH_DETAILS')
    if ($Value -ceq 'NO') {
        if (-not [string]::IsNullOrWhiteSpace($Details)) {
            throw "Owner input ${Key}_DETAILS must be empty when $Key is NO."
        }
        return $NoStatement
    }
    if ([string]::IsNullOrWhiteSpace($Details)) {
        throw "Owner input ${Key}_DETAILS is required."
    }
    return $YesPrefix + $Details
}

$repositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$privateRoot = [IO.Path]::GetFullPath($PrivateRoot)
$expectedUsersRoot = [IO.Path]::GetFullPath('C:\Users')
$bootstrapManifestPath = [IO.Path]::GetFullPath((Join-Path $privateRoot `
    'bootstrap-manifest.properties'))
if (-not (Test-Path -LiteralPath $repositoryRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $expectedUsersRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $privateRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $bootstrapManifestPath -PathType Leaf)) {
    throw 'The repository, Users root, private root and private bootstrap must already exist.'
}
Assert-WO034NoReparsePath -Path $privateRoot -Anchor $expectedUsersRoot `
    -Description 'WO-034 private root bootstrap path'
Assert-WO034NoReparsePath -Path $bootstrapManifestPath -Anchor $expectedUsersRoot `
    -Description 'WO-034 private bootstrap manifest'
$bootstrapBytes = [IO.File]::ReadAllBytes($bootstrapManifestPath)
$bootstrapSha256 = Get-WO034Sha256 -Bytes $bootstrapBytes
if ($bootstrapSha256 -cne $expectedBootstrapSha256) {
    throw 'The WO-034 private bootstrap fingerprint does not match the renderer contract.'
}
$bootstrapText = ConvertFrom-WO034StrictUtf8Bytes -Bytes $bootstrapBytes `
    -Description 'WO-034 private bootstrap manifest'
$expectedBootstrapKeys = @(
    'SCHEMA', 'OWNER_SID', 'USERS_ROOT', 'OWNER_PROFILE_ROOT', 'PRIVATE_ROOT'
)
$bootstrapValues = @{}
foreach ($bootstrapLine in $bootstrapText -split "`n") {
    if ([string]::IsNullOrEmpty($bootstrapLine)) {
        continue
    }
    $bootstrapSeparatorIndex = $bootstrapLine.IndexOf('=')
    if ($bootstrapSeparatorIndex -lt 1) {
        throw 'The WO-034 private bootstrap contains a malformed property line.'
    }
    $bootstrapKey = $bootstrapLine.Substring(0, $bootstrapSeparatorIndex)
    $bootstrapValue = $bootstrapLine.Substring($bootstrapSeparatorIndex + 1)
    if ($expectedBootstrapKeys -cnotcontains $bootstrapKey -or
        $bootstrapValues.ContainsKey($bootstrapKey)) {
        throw 'The WO-034 private bootstrap contains an unknown or duplicate key.'
    }
    $bootstrapValues[$bootstrapKey] = $bootstrapValue
}
if (@($expectedBootstrapKeys | Where-Object {
            -not $bootstrapValues.ContainsKey($_)
        }).Count -ne 0 -or $bootstrapValues.Count -ne $expectedBootstrapKeys.Count) {
    throw 'The WO-034 private bootstrap key set is incomplete.'
}
if ($bootstrapValues['SCHEMA'] -cne
    'urn:betting-project:sofascore-local-lab:wo034:private-bootstrap:v1' -or
    [IO.Path]::GetFullPath($bootstrapValues['USERS_ROOT']) -cne $expectedUsersRoot -or
    [IO.Path]::GetFullPath($bootstrapValues['PRIVATE_ROOT']) -cne $privateRoot) {
    throw 'The WO-034 private bootstrap identity or path contract is invalid.'
}
try {
    $bootstrapOwnerSid = [Security.Principal.SecurityIdentifier]::new(
        $bootstrapValues['OWNER_SID']).Value
} catch {
    throw 'The WO-034 private bootstrap owner SID is invalid.'
}
$currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if ($currentSid -cne $bootstrapOwnerSid) {
    throw 'WO-034 rendering must execute as the fingerprinted private owner.'
}
$expectedProfileRoot = [IO.Path]::GetFullPath($bootstrapValues['OWNER_PROFILE_ROOT'])
$expectedDocumentsRoot = [IO.Path]::GetFullPath((Join-Path $expectedProfileRoot 'Documents'))
$expectedPrivateRoot = [IO.Path]::GetFullPath((Join-Path $expectedDocumentsRoot `
    'SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034'))
if ($privateRoot -cne $expectedPrivateRoot) {
    throw 'PrivateRoot does not match the fingerprinted owner Documents path for WO-034.'
}
$privateRootInfo = [IO.DirectoryInfo]::new($privateRoot)
$privateAnchorInfo = $privateRootInfo.Parent
$privateContainerInfo = if ($null -eq $privateAnchorInfo) { $null } else {
    $privateAnchorInfo.Parent
}
$documentsRootInfo = if ($null -eq $privateContainerInfo) { $null } else {
    $privateContainerInfo.Parent
}
$profileRootInfo = if ($null -eq $documentsRootInfo) { $null } else {
    $documentsRootInfo.Parent
}
$usersRootInfo = if ($null -eq $profileRootInfo) { $null } else {
    $profileRootInfo.Parent
}
if ($privateRootInfo.Name -cne 'WO-SS-20260901-034' -or
    $null -eq $privateAnchorInfo -or $privateAnchorInfo.Name -cne 'permission-requests' -or
    $null -eq $privateContainerInfo -or
    $privateContainerInfo.Name -cne 'SofaScoreLocalLab-private' -or
    $null -eq $documentsRootInfo -or $documentsRootInfo.Name -cne 'Documents' -or
    $null -eq $profileRootInfo -or $profileRootInfo.FullName -cne $expectedProfileRoot -or
    $null -eq $usersRootInfo -or $usersRootInfo.FullName -cne $expectedUsersRoot) {
    throw ('PrivateRoot must be the canonical ' +
        'Documents\SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034 path.')
}
$privateAnchor = $privateAnchorInfo.FullName
$privateContainerRoot = $privateContainerInfo.FullName
$documentsRoot = $documentsRootInfo.FullName
$profileRoot = $profileRootInfo.FullName
$usersRoot = $usersRootInfo.FullName
$qualifiedRuntimeRoot = [IO.Path]::GetFullPath((Join-Path $privateRoot 'qualified-runtime'))
$rendererPath = [IO.Path]::GetFullPath($AttestedRendererPath)
$expectedRendererPath = [IO.Path]::GetFullPath((Join-Path $qualifiedRuntimeRoot `
    'Render-WO034PermissionRequest.ps1'))
$templatePath = [IO.Path]::GetFullPath((Join-Path $qualifiedRuntimeRoot `
    'J9-WO034-PERMISSION-REQUEST-PRIMARY-TEMPLATE-20260901.txt'))
$sourceRendererPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
    'scripts\Render-WO034PermissionRequest.ps1'))
$sourceTemplatePath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
    'docs\validation\J9-WO034-PERMISSION-REQUEST-PRIMARY-TEMPLATE-20260901.txt'))
$sourceSyntheticFixturePath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
    'docs\validation\J9-WO034-PERMISSION-REQUEST-SYNTHETIC-FIXTURE-20260901.properties'))

if (-not (Test-Path -LiteralPath $repositoryRoot -PathType Container)) {
    throw 'The source repository root does not exist.'
}
if (-not (Test-Path -LiteralPath $profileRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $documentsRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $privateContainerRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $privateAnchor -PathType Container)) {
    throw 'The WO-034 protected private ancestor chain must already exist.'
}
if ($rendererPath -cne $expectedRendererPath) {
    throw 'WO-034 must execute the attested protected qualified-runtime bytes.'
}
if (-not $AttestedRendererStream.CanRead -or $AttestedRendererStream.CanWrite -or
    [IO.Path]::GetFullPath($AttestedRendererStream.Name) -cne $rendererPath -or
    -not $AttestedSourceRendererStream.CanRead -or $AttestedSourceRendererStream.CanWrite -or
    [IO.Path]::GetFullPath($AttestedSourceRendererStream.Name) -cne $sourceRendererPath) {
    throw 'WO-034 requires held read locks for the runtime and versioned renderer bytes.'
}
Assert-WO034ConcurrentWriteOpenDenied -Path $rendererPath `
    -Description 'Attested runtime renderer stream'
Assert-WO034ConcurrentWriteOpenDenied -Path $sourceRendererPath `
    -Description 'Attested versioned renderer stream'
$attestedRuntimeRendererBytes = Read-WO034AttestedStreamBytes `
    -Stream $AttestedRendererStream -Description 'Attested runtime renderer'
$attestedSourceRendererBytes = Read-WO034AttestedStreamBytes `
    -Stream $AttestedSourceRendererStream -Description 'Attested versioned renderer source'
if (-not (Test-WO034ByteArrayEqual -Left $attestedRuntimeRendererBytes `
        -Right $attestedSourceRendererBytes)) {
    throw 'The two held renderer streams are not byte-identical.'
}
$rendererSha256 = Get-WO034Sha256 -Bytes $attestedRuntimeRendererBytes
if ($rendererSha256 -cne $AttestedRendererSha256.ToLowerInvariant()) {
    throw 'The held renderer stream SHA-256 does not match the launcher attestation.'
}
if (-not (Test-Path -LiteralPath $privateRoot -PathType Container)) {
    throw 'The WO-034 private root must already exist.'
}
if (-not (Test-Path -LiteralPath $qualifiedRuntimeRoot -PathType Container)) {
    throw 'The WO-034 qualified runtime root must already exist.'
}
Assert-WO034NoReparsePath -Path $privateRoot -Anchor $usersRoot `
    -Description 'WO-034 private root'
Assert-WO034NoReparsePath -Path $qualifiedRuntimeRoot -Anchor $usersRoot `
    -Description 'WO-034 qualified runtime root'
Assert-WO034NoReparsePath -Path $rendererPath -Anchor $usersRoot `
    -Description 'WO-034 qualified renderer'
Assert-WO034NoReparsePath -Path $templatePath -Anchor $usersRoot `
    -Description 'WO-034 qualified template'
Assert-WO034NoReparsePath -Path $sourceRendererPath -Anchor $repositoryRoot `
    -Description 'WO-034 versioned renderer source'
Assert-WO034NoReparsePath -Path $sourceTemplatePath -Anchor $repositoryRoot `
    -Description 'WO-034 versioned template source'
Assert-WO034NoReparsePath -Path $sourceSyntheticFixturePath -Anchor $repositoryRoot `
    -Description 'WO-034 versioned synthetic fixture source'
$repositoryPrefix = $repositoryRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$privatePrefix = $privateRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if ($privateRoot -ceq $repositoryRoot -or
    $privateRoot.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase) -or
    $repositoryRoot.StartsWith($privatePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The WO-034 private root and Git worktree must be disjoint.'
}

$allowedPrivateSids = [Collections.Generic.HashSet[string]]::new(
    [StringComparer]::OrdinalIgnoreCase)
[void]$allowedPrivateSids.Add('S-1-5-18')
[void]$allowedPrivateSids.Add('S-1-5-32-544')
[void]$allowedPrivateSids.Add($currentSid)
$trustedWriterSids = $allowedPrivateSids
Assert-WO034NoUntrustedWriteAcl -Path $usersRoot -TrustedWriterSids $trustedWriterSids `
    -RequireProtected -Description 'WO-034 Users ancestor'
Assert-WO034NoUntrustedWriteAcl -Path $profileRoot -TrustedWriterSids $trustedWriterSids `
    -RequireProtected -Description 'WO-034 user profile ancestor'
Assert-WO034NoUntrustedWriteAcl -Path $documentsRoot -TrustedWriterSids $trustedWriterSids `
    -Description 'WO-034 Documents ancestor'
Assert-WO034PrivateAcl -Path $privateContainerRoot -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 private container'
Assert-WO034PrivateAcl -Path $privateAnchor -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 private anchor'
Assert-WO034PrivateAcl -Path $privateRoot -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 private root'
Assert-WO034PrivateAcl -Path $qualifiedRuntimeRoot -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 qualified runtime root'
Assert-WO034PrivateAcl -Path $rendererPath -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 qualified renderer'
Assert-WO034PrivateAcl -Path $templatePath -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 qualified template'
Assert-WO034PrivateAcl -Path $bootstrapManifestPath -AllowedSids $allowedPrivateSids `
    -RequireProtected -Description 'WO-034 private bootstrap manifest'

$canonicalInputPath = Get-WO034CanonicalPrivatePath -Path $InputPath `
    -PrivateRoot $privateRoot -Description 'Owner input path'
$canonicalOutputDirectory = Get-WO034CanonicalPrivatePath -Path $OutputDirectory `
    -PrivateRoot $privateRoot -Description 'Output directory'

if (-not (Test-Path -LiteralPath $canonicalInputPath -PathType Leaf)) {
    throw 'The owner input file does not exist.'
}
if ([IO.Path]::GetDirectoryName($canonicalInputPath) -cne $privateRoot) {
    throw 'The owner input file must be a direct child of the WO-034 private root.'
}
if (-not (Test-Path -LiteralPath $canonicalOutputDirectory -PathType Container)) {
    throw 'The output directory must already exist.'
}
$outputParent = [IO.DirectoryInfo]::new($canonicalOutputDirectory).Parent
if ($canonicalOutputDirectory -cne $privateRoot -and
    ($null -eq $outputParent -or $outputParent.FullName -cne $privateRoot)) {
    throw 'The output directory must be the private root or one of its direct children.'
}
$canonicalOwnerInputPath = Join-Path $privateRoot 'owner-input.properties'
$renderPurpose = 'OWNER_FINAL'
if ($SyntheticQualification) {
    $renderPurpose = 'SYNTHETIC_QUALIFICATION'
    if ([IO.Path]::GetFileName($canonicalInputPath) -cne 'qualification-synthetic.properties' -or
        $canonicalOutputDirectory -ceq $privateRoot -or
        [IO.Path]::GetFileName($canonicalOutputDirectory) -cnotmatch
            '^qualification-[a-z0-9-]+-output$') {
        throw 'Synthetic qualification requires the canonical synthetic input and a qualification output child.'
    }
} elseif ($canonicalInputPath -cne $canonicalOwnerInputPath -or
    $canonicalOutputDirectory -cne $privateRoot) {
    throw 'Owner-final rendering requires the canonical owner input and private-root output.'
}
Assert-WO034NoReparsePath -Path $canonicalInputPath -Anchor $usersRoot `
    -Description 'Owner input path'
Assert-WO034NoReparsePath -Path $canonicalOutputDirectory -Anchor $usersRoot `
    -Description 'Output directory'
Assert-WO034PrivateAcl -Path $canonicalInputPath -AllowedSids $allowedPrivateSids `
    -Description 'Owner input file'
Assert-WO034PrivateAcl -Path $canonicalOutputDirectory -AllowedSids $allowedPrivateSids `
    -Description 'Output directory'

$renderLockPath = Join-Path $canonicalOutputDirectory '.wo034-render.lock'
$renderLockStream = $null
try {
    if ([IO.File]::Exists($renderLockPath) -or [IO.Directory]::Exists($renderLockPath)) {
        $existingLockItem = Get-Item -LiteralPath $renderLockPath -Force
        if ($existingLockItem.PSIsContainer -or
            (($existingLockItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw 'The existing WO-034 output lock must be a regular file.'
        }
        Assert-WO034PrivateAcl -Path $renderLockPath -AllowedSids $allowedPrivateSids `
            -Description 'Existing WO-034 output lock'
        try {
            $renderLockStream = [IO.File]::Open(
                $renderLockPath,
                [IO.FileMode]::Open,
                [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::None)
        } catch [IO.IOException] {
            throw 'Another WO-034 renderer already owns the canonical output lock.'
        }
    } else {
        try {
            $renderLockStream = [IO.File]::Open(
                $renderLockPath,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::None)
        } catch [IO.IOException] {
            throw 'Another WO-034 renderer created the canonical output lock first.'
        }
    }
    Assert-WO034PrivateAcl -Path $renderLockPath -AllowedSids $allowedPrivateSids `
        -Description 'WO-034 output lock'
    if ($renderLockStream.Length -ne 0) {
        throw 'The WO-034 output lock file must remain empty.'
    }

    $staleStagingFilesRemoved = 0
    $staleStagingCleanupFailures = 0
    $stagingNamePattern = '^(?:\.wo034-[0-9a-f]{32}|\.wo034-staging-[0-9a-f]{32}\.tmp)$'
    foreach ($stagingItem in @(Get-ChildItem -LiteralPath $canonicalOutputDirectory -Force |
            Where-Object { $_.Name -cmatch $stagingNamePattern })) {
        try {
            if ($stagingItem.PSIsContainer -or
                (($stagingItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
                throw 'Residual staging entry is not a regular file.'
            }
            Assert-WO034PrivateAcl -Path $stagingItem.FullName -AllowedSids $allowedPrivateSids `
                -Description 'Residual WO-034 staging file'
            [IO.File]::Delete($stagingItem.FullName)
            if ([IO.File]::Exists($stagingItem.FullName)) {
                throw 'Residual staging file still exists after deletion.'
            }
            $staleStagingFilesRemoved++
        } catch {
            $staleStagingCleanupFailures++
        }
    }
    if ($staleStagingCleanupFailures -ne 0) {
        throw 'One or more residual WO-034 staging files could not be safely removed.'
    }

    $canonicalPayloadNames = @(
        'outbound-message.utf8.txt',
        'outbound-form-envelope.utf8.json',
        'outbound-message.sha256.txt',
        'outbound-form-envelope.sha256.txt'
    )
    $canonicalEvidencePath = Join-Path $canonicalOutputDirectory 'render-evidence.properties'
    $incompleteCanonicalFilesRemoved = 0
    $incompleteCanonicalCleanupFailures = 0
    if (-not [IO.File]::Exists($canonicalEvidencePath)) {
        foreach ($canonicalPayloadName in $canonicalPayloadNames) {
            $canonicalPayloadPath = Join-Path $canonicalOutputDirectory $canonicalPayloadName
            try {
                if (-not [IO.File]::Exists($canonicalPayloadPath) -and
                    -not [IO.Directory]::Exists($canonicalPayloadPath)) {
                    continue
                }
                $canonicalPayloadItem = Get-Item -LiteralPath $canonicalPayloadPath -Force
                if ($canonicalPayloadItem.PSIsContainer -or
                    (($canonicalPayloadItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
                    throw 'Incomplete canonical output is not a regular file.'
                }
                Assert-WO034PrivateAcl -Path $canonicalPayloadPath `
                    -AllowedSids $allowedPrivateSids `
                    -Description 'Incomplete canonical WO-034 output'
                [IO.File]::Delete($canonicalPayloadPath)
                if ([IO.File]::Exists($canonicalPayloadPath)) {
                    throw 'Incomplete canonical output still exists after deletion.'
                }
                $incompleteCanonicalFilesRemoved++
            } catch {
                $incompleteCanonicalCleanupFailures++
            }
        }
    }
    if ($incompleteCanonicalCleanupFailures -ne 0) {
        throw 'One or more incomplete canonical WO-034 outputs could not be safely removed.'
    }

$templateBytes = [IO.File]::ReadAllBytes($templatePath)
$sourceTemplateBytes = [IO.File]::ReadAllBytes($sourceTemplatePath)
if (-not (Test-WO034ByteArrayEqual -Left $templateBytes -Right $sourceTemplateBytes)) {
    throw 'The protected template copy does not match the versioned template bytes.'
}
$templateSha256 = Get-WO034Sha256 -Bytes $templateBytes
if ($templateSha256 -cne $expectedTemplateSha256) {
    throw 'The WO-034 primary template SHA-256 does not match the renderer contract.'
}
$template = ConvertFrom-WO034StrictUtf8Bytes -Bytes $templateBytes `
    -Description 'WO-034 primary template'
if ($template.Contains("`r", [StringComparison]::Ordinal) -or -not $template.EndsWith("`n")) {
    throw 'The WO-034 primary template must use LF and end with one newline.'
}

$expectedKeys = @(
    'OUTBOUND_CONTENT_MODE',
    'OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED',
    'REQUESTOR_FULL_LEGAL_NAME',
    'REQUESTOR_ROLE',
    'ORGANIZATION_OR_PROJECT_OWNER',
    'LEGAL_ENTITY_TYPE',
    'LEGAL_ENTITY_TYPE_DETAILS',
    'COUNTRY_AND_JURISDICTION',
    'REPLY_EMAIL',
    'FORM_COMPANY_FIELD_MODE',
    'FORM_COMPANY_VALUE',
    'PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE',
    'PROJECT_WEBSITE_OR_REPOSITORY',
    'INTENDED_USE_CLASSIFICATION',
    'INTENDED_USE_CLASSIFICATION_DETAILS',
    'BETTING_RELATED_USE_DESCRIPTION',
    'END_USER_ACCESS',
    'END_USER_ACCESS_DETAILS',
    'RECEIVER_CONTROL_RELATION',
    'RECEIVER_CONTROL_RELATION_DETAILS',
    'RECEIVER_OPERATOR_IDENTITY',
    'RECEIVER_FUTURE_HOSTING_PROVIDER',
    'RECEIVER_FUTURE_HOSTING_COUNTRY',
    'RECEIVER_FUTURE_HOSTING_REGION',
    'POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION',
    'PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST',
    'MODEL_TRAINING',
    'MODEL_TRAINING_DETAILS',
    'DATA_RESALE',
    'DATA_RESALE_DETAILS',
    'PUBLIC_REDISTRIBUTION',
    'PUBLIC_REDISTRIBUTION_DETAILS',
    'DATA_USE_DECLARATIONS_SCOPE',
    'DATA_USE_DECLARATIONS_SCOPE_DETAILS',
    'REQUESTED_PERMISSION_DURATION',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_DAY',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK',
    'EXPECTED_DIRECT_ATTEMPTS_PER_DAY',
    'EXPECTED_DIRECT_ATTEMPTS_PER_MONTH',
    'LAB_ITSELF_PLACES_WAGERS',
    'LAB_ITSELF_PLACES_WAGERS_EXPLANATION'
)
$expectedKeySet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($expectedKey in $expectedKeys) {
    [void]$expectedKeySet.Add($expectedKey)
}
$inputValues = @{}
$inputBytes = [IO.File]::ReadAllBytes($canonicalInputPath)
if ($SyntheticQualification) {
    $sourceSyntheticFixtureBytes = [IO.File]::ReadAllBytes($sourceSyntheticFixturePath)
    $sourceSyntheticFixtureSha256 = Get-WO034Sha256 -Bytes $sourceSyntheticFixtureBytes
    if ($sourceSyntheticFixtureSha256 -cne $expectedSyntheticInputSha256 -or
        -not (Test-WO034ByteArrayEqual -Left $inputBytes -Right $sourceSyntheticFixtureBytes)) {
        throw 'Synthetic qualification requires the exact reviewed PII-free fixture bytes.'
    }
}
$inputText = ConvertFrom-WO034StrictUtf8Bytes -Bytes $inputBytes -Description 'Owner input file'
foreach ($rawLine in $inputText -split "`n") {
    $line = $rawLine.TrimEnd("`r")
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        continue
    }
    $separatorIndex = $line.IndexOf('=')
    if ($separatorIndex -lt 1) {
        throw 'The owner input file contains a malformed property line.'
    }
    $key = $line.Substring(0, $separatorIndex)
    $value = $line.Substring($separatorIndex + 1)
    if (-not $expectedKeySet.Contains($key)) {
        throw 'The owner input file contains an unknown key.'
    }
    if ($inputValues.ContainsKey($key)) {
        throw "The owner input file contains a duplicate key: $key."
    }
    $inputValues[$key] = Get-WO034NormalizedOwnerValue -Key $key -Value $value
}

$missingKeys = @($expectedKeys | Where-Object { -not $inputValues.ContainsKey($_) })
if ($missingKeys.Count -gt 0) {
    throw 'The owner input file omits required schema keys: ' + ($missingKeys -join ', ')
}

$unconditionalKeys = @(
    'OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED',
    'REQUESTOR_FULL_LEGAL_NAME',
    'REQUESTOR_ROLE',
    'ORGANIZATION_OR_PROJECT_OWNER',
    'LEGAL_ENTITY_TYPE',
    'COUNTRY_AND_JURISDICTION',
    'REPLY_EMAIL',
    'FORM_COMPANY_FIELD_MODE',
    'PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE',
    'INTENDED_USE_CLASSIFICATION',
    'BETTING_RELATED_USE_DESCRIPTION',
    'END_USER_ACCESS',
    'RECEIVER_CONTROL_RELATION',
    'RECEIVER_OPERATOR_IDENTITY',
    'RECEIVER_FUTURE_HOSTING_PROVIDER',
    'RECEIVER_FUTURE_HOSTING_COUNTRY',
    'RECEIVER_FUTURE_HOSTING_REGION',
    'POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION',
    'PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST',
    'MODEL_TRAINING',
    'DATA_RESALE',
    'PUBLIC_REDISTRIBUTION',
    'DATA_USE_DECLARATIONS_SCOPE',
    'REQUESTED_PERMISSION_DURATION',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_DAY',
    'EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK',
    'EXPECTED_DIRECT_ATTEMPTS_PER_DAY',
    'EXPECTED_DIRECT_ATTEMPTS_PER_MONTH',
    'LAB_ITSELF_PLACES_WAGERS',
    'LAB_ITSELF_PLACES_WAGERS_EXPLANATION'
)
$emptyKeys = @($unconditionalKeys | Where-Object {
        [string]::IsNullOrWhiteSpace($inputValues[$_])
    })
if ($emptyKeys.Count -gt 0) {
    throw 'Owner input remains incomplete for keys: ' + ($emptyKeys -join ', ')
}

if ($inputValues['OUTBOUND_CONTENT_MODE'] -cne 'PRIMARY_ONLY' -or
    $inputValues['OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED'] -cne 'YES') {
    throw 'Renderer v1 requires an explicitly confirmed PRIMARY_ONLY content mode.'
}

Assert-WO034Enum -Key 'LEGAL_ENTITY_TYPE' -Value $inputValues['LEGAL_ENTITY_TYPE'] `
    -Allowed @('INDIVIDUAL', 'ASSOCIATION', 'COMPANY', 'OTHER_WITH_DETAILS')
$legalEntityDescription = Get-WO034OtherDescription -Key 'LEGAL_ENTITY_TYPE' `
    -Value $inputValues['LEGAL_ENTITY_TYPE'] `
    -Details $inputValues['LEGAL_ENTITY_TYPE_DETAILS'] -Descriptions @{
        INDIVIDUAL = 'an individual'
        ASSOCIATION = 'an association'
        COMPANY = 'a company'
    }

Assert-WO034Enum -Key 'FORM_COMPANY_FIELD_MODE' `
    -Value $inputValues['FORM_COMPANY_FIELD_MODE'] `
    -Allowed @('OMIT', 'USE_ORGANIZATION_OR_PROJECT_OWNER', 'EXPLICIT_VALUE')
$companyValue = $null
switch ($inputValues['FORM_COMPANY_FIELD_MODE']) {
    'OMIT' {
        if (-not [string]::IsNullOrWhiteSpace($inputValues['FORM_COMPANY_VALUE'])) {
            throw 'FORM_COMPANY_VALUE must be empty when the company field is omitted.'
        }
    }
    'USE_ORGANIZATION_OR_PROJECT_OWNER' {
        if (-not [string]::IsNullOrWhiteSpace($inputValues['FORM_COMPANY_VALUE'])) {
            throw 'FORM_COMPANY_VALUE must be empty when the organization value is reused.'
        }
        $companyValue = $inputValues['ORGANIZATION_OR_PROJECT_OWNER']
    }
    'EXPLICIT_VALUE' {
        if ([string]::IsNullOrWhiteSpace($inputValues['FORM_COMPANY_VALUE'])) {
            throw 'FORM_COMPANY_VALUE is required for EXPLICIT_VALUE.'
        }
        $companyValue = $inputValues['FORM_COMPANY_VALUE']
    }
}

Assert-WO034Enum -Key 'PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE' `
    -Value $inputValues['PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE'] `
    -Allowed @('OMIT', 'INCLUDE')
$repositoryBlock = ''
if ($inputValues['PROJECT_WEBSITE_OR_REPOSITORY_DISCLOSURE'] -ceq 'INCLUDE') {
    $repositoryValue = $inputValues['PROJECT_WEBSITE_OR_REPOSITORY']
    if ([string]::IsNullOrWhiteSpace($repositoryValue)) {
        throw 'PROJECT_WEBSITE_OR_REPOSITORY is required when disclosure is INCLUDE.'
    }
    $repositoryUri = $null
    if (-not [Uri]::TryCreate($repositoryValue, [UriKind]::Absolute, [ref]$repositoryUri) -or
        $repositoryUri.Scheme -cne 'https' -or -not [string]::IsNullOrEmpty($repositoryUri.UserInfo) -or
        -not [string]::IsNullOrEmpty($repositoryUri.Query) -or
        -not [string]::IsNullOrEmpty($repositoryUri.Fragment)) {
        throw 'PROJECT_WEBSITE_OR_REPOSITORY must be an absolute HTTPS URI without user info, query or fragment.'
    }
    $repositoryBlock = "The public project website or repository is: $repositoryValue.`n`n"
} elseif (-not [string]::IsNullOrWhiteSpace($inputValues['PROJECT_WEBSITE_OR_REPOSITORY'])) {
    throw 'PROJECT_WEBSITE_OR_REPOSITORY must be empty when disclosure is OMIT.'
}

Assert-WO034Enum -Key 'INTENDED_USE_CLASSIFICATION' `
    -Value $inputValues['INTENDED_USE_CLASSIFICATION'] `
    -Allowed @('PERSONAL_RESEARCH', 'NON_COMMERCIAL', 'COMMERCIAL', 'OTHER_WITH_DETAILS')
$intendedUseDescription = Get-WO034OtherDescription -Key 'INTENDED_USE_CLASSIFICATION' `
    -Value $inputValues['INTENDED_USE_CLASSIFICATION'] `
    -Details $inputValues['INTENDED_USE_CLASSIFICATION_DETAILS'] -Descriptions @{
        PERSONAL_RESEARCH = 'personal research'
        NON_COMMERCIAL = 'non-commercial'
        COMMERCIAL = 'commercial'
    }

Assert-WO034Enum -Key 'END_USER_ACCESS' -Value $inputValues['END_USER_ACCESS'] `
    -Allowed @('INTERNAL_ONLY', 'EXTERNAL_USERS', 'OTHER_WITH_DETAILS')
$endUserAccessDescription = Get-WO034OtherDescription -Key 'END_USER_ACCESS' `
    -Value $inputValues['END_USER_ACCESS'] `
    -Details $inputValues['END_USER_ACCESS_DETAILS'] -Descriptions @{
        INTERNAL_ONLY = 'internal users only'
        EXTERNAL_USERS = 'external users'
    }

Assert-WO034Enum -Key 'RECEIVER_CONTROL_RELATION' `
    -Value $inputValues['RECEIVER_CONTROL_RELATION'] `
    -Allowed @('SAME_CONTROLLER', 'THIRD_PARTY', 'OTHER_WITH_DETAILS')
$receiverControlDescription = Get-WO034OtherDescription -Key 'RECEIVER_CONTROL_RELATION' `
    -Value $inputValues['RECEIVER_CONTROL_RELATION'] `
    -Details $inputValues['RECEIVER_CONTROL_RELATION_DETAILS'] -Descriptions @{
        SAME_CONTROLLER = 'under the same controller as the requesting project'
        THIRD_PARTY = 'controlled by a third party'
    }

Assert-WO034Enum -Key 'POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION' `
    -Value $inputValues['POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION'] `
    -Allowed @('INCLUDE', 'EXCLUDE')
$colocationListItem = ''
$hostedProviderQuestionsBlock = @(
    '8. whether the answer changes for commercial, user-facing or other hosted use; and'
    '9. whether a sandbox or test environment is available for end-to-end qualification.'
) -join "`n"
if ($inputValues['POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION'] -ceq 'INCLUDE') {
    $colocationListItem = '3. potentially, both applications running on that VPS at a later date, ' +
        'but only if hosted provider acquisition is explicitly permitted and separately approved.' + "`n"
    $hostedProviderQuestionsBlock = @(
        '8. whether provider acquisition may ever run from that VPS, under which official API ' +
            'product and restrictions, or whether it must always remain on the Windows workstation;'
        '9. whether the answer changes for commercial, user-facing or other hosted use; and'
        '10. whether a sandbox or test environment is available for end-to-end qualification.'
    ) -join "`n"
}

Assert-WO034Enum -Key 'PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST' `
    -Value $inputValues['PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST'] `
    -Allowed @('NO', 'YES')
$ipDisclosureBlock = ''
if ($inputValues['PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST'] -ceq 'YES') {
    $ipDisclosureBlock = "The owner-declared public IPv4 address for that VPS is $ownerDeclaredProductionVpsIpv4.`n`n"
}

$modelTrainingStatement = Get-WO034DeclarationStatement -Key 'MODEL_TRAINING' `
    -Value $inputValues['MODEL_TRAINING'] -Details $inputValues['MODEL_TRAINING_DETAILS'] `
    -NoStatement 'none is intended' -YesPrefix 'intended use is: '
$dataResaleStatement = Get-WO034DeclarationStatement -Key 'DATA_RESALE' `
    -Value $inputValues['DATA_RESALE'] -Details $inputValues['DATA_RESALE_DETAILS'] `
    -NoStatement 'none is intended' -YesPrefix 'intended use is: '
$publicRedistributionStatement = Get-WO034DeclarationStatement -Key 'PUBLIC_REDISTRIBUTION' `
    -Value $inputValues['PUBLIC_REDISTRIBUTION'] `
    -Details $inputValues['PUBLIC_REDISTRIBUTION_DETAILS'] `
    -NoStatement 'none is intended' -YesPrefix 'intended use is: '

Assert-WO034Enum -Key 'DATA_USE_DECLARATIONS_SCOPE' `
    -Value $inputValues['DATA_USE_DECLARATIONS_SCOPE'] `
    -Allowed @('RAW_ONLY', 'RAW_NORMALIZED_AND_DERIVED_DATA', 'OTHER_WITH_DETAILS')
$dataUseScopeDescription = Get-WO034OtherDescription -Key 'DATA_USE_DECLARATIONS_SCOPE' `
    -Value $inputValues['DATA_USE_DECLARATIONS_SCOPE'] `
    -Details $inputValues['DATA_USE_DECLARATIONS_SCOPE_DETAILS'] -Descriptions @{
        RAW_ONLY = 'raw provider responses only'
        RAW_NORMALIZED_AND_DERIVED_DATA = 'raw, normalized and derived data'
    }

Assert-WO034Enum -Key 'LAB_ITSELF_PLACES_WAGERS' `
    -Value $inputValues['LAB_ITSELF_PLACES_WAGERS'] -Allowed @('NO', 'YES')
$labWagerStatement = if ($inputValues['LAB_ITSELF_PLACES_WAGERS'] -ceq 'NO') {
    'does not place wagers. ' + $inputValues['LAB_ITSELF_PLACES_WAGERS_EXPLANATION']
} else {
    'does place wagers. ' + $inputValues['LAB_ITSELF_PLACES_WAGERS_EXPLANATION']
}

try {
    $mailAddress = [Net.Mail.MailAddress]::new($inputValues['REPLY_EMAIL'])
    if ($mailAddress.Address -cne $inputValues['REPLY_EMAIL']) {
        throw 'not canonical'
    }
} catch {
    throw 'REPLY_EMAIL must be one canonical mailbox address without a display name.'
}

$replacements = [ordered]@{
    REQUESTOR_FULL_LEGAL_NAME = $inputValues['REQUESTOR_FULL_LEGAL_NAME']
    REQUESTOR_ROLE = $inputValues['REQUESTOR_ROLE']
    ORGANIZATION_OR_PROJECT_OWNER = $inputValues['ORGANIZATION_OR_PROJECT_OWNER']
    COUNTRY_AND_JURISDICTION = $inputValues['COUNTRY_AND_JURISDICTION']
    LEGAL_ENTITY_DESCRIPTION = $legalEntityDescription
    REPOSITORY_BLOCK = $repositoryBlock
    BETTING_RELATED_USE_DESCRIPTION = $inputValues['BETTING_RELATED_USE_DESCRIPTION']
    INTENDED_USE_DESCRIPTION = $intendedUseDescription
    END_USER_ACCESS_DESCRIPTION = $endUserAccessDescription
    LAB_WAGER_STATEMENT = $labWagerStatement
    RECEIVER_CONTROL_DESCRIPTION = $receiverControlDescription
    RECEIVER_OPERATOR_IDENTITY = $inputValues['RECEIVER_OPERATOR_IDENTITY']
    RECEIVER_FUTURE_HOSTING_PROVIDER = $inputValues['RECEIVER_FUTURE_HOSTING_PROVIDER']
    RECEIVER_FUTURE_HOSTING_COUNTRY = $inputValues['RECEIVER_FUTURE_HOSTING_COUNTRY']
    RECEIVER_FUTURE_HOSTING_REGION = $inputValues['RECEIVER_FUTURE_HOSTING_REGION']
    COLOCATION_LIST_ITEM = $colocationListItem
    HOSTED_PROVIDER_QUESTIONS_BLOCK = $hostedProviderQuestionsBlock
    IP_DISCLOSURE_BLOCK = $ipDisclosureBlock
    EXPECTED_MANUAL_ACQUISITIONS_PER_DAY = $inputValues['EXPECTED_MANUAL_ACQUISITIONS_PER_DAY']
    EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK = $inputValues['EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK']
    EXPECTED_DIRECT_ATTEMPTS_PER_DAY = $inputValues['EXPECTED_DIRECT_ATTEMPTS_PER_DAY']
    EXPECTED_DIRECT_ATTEMPTS_PER_MONTH = $inputValues['EXPECTED_DIRECT_ATTEMPTS_PER_MONTH']
    DATA_USE_SCOPE_DESCRIPTION = $dataUseScopeDescription
    MODEL_TRAINING_STATEMENT = $modelTrainingStatement
    DATA_RESALE_STATEMENT = $dataResaleStatement
    PUBLIC_REDISTRIBUTION_STATEMENT = $publicRedistributionStatement
    REQUESTED_PERMISSION_DURATION = $inputValues['REQUESTED_PERMISSION_DURATION']
    REPLY_EMAIL = $inputValues['REPLY_EMAIL']
}

$message = $template
foreach ($entry in $replacements.GetEnumerator()) {
    $message = $message.Replace('{{' + $entry.Key + '}}', [string]$entry.Value,
        [StringComparison]::Ordinal)
}
$message = $message.Normalize([Text.NormalizationForm]::FormC).Replace("`r`n", "`n").Replace("`r", "`n")
$message = $message.TrimEnd("`n") + "`n"
$unresolvedTokens = [regex]::Matches($message, '\{\{[A-Z0-9_]+\}\}')
if ($unresolvedTokens.Count -ne 0) {
    throw 'The rendered message still contains template tokens.'
}
if ($message.IndexOfAny([char[]]'[]{}<>') -ge 0) {
    throw 'The rendered message still contains a reserved placeholder delimiter.'
}
if ([regex]::IsMatch($message, '(?m)[ \t]+$')) {
    throw 'The rendered message contains trailing whitespace.'
}
$messageBytes = $utf8.GetBytes($message)
if ($messageBytes.Length -gt 100000) {
    throw 'The rendered message exceeds the WO-034 100000-byte safety bound.'
}
$messageSha256 = Get-WO034Sha256 -Bytes $messageBytes

$envelopeStream = [IO.MemoryStream]::new()
$jsonOptions = [Text.Json.JsonWriterOptions]::new()
$jsonOptions.Indented = $false
$jsonOptions.SkipValidation = $false
$jsonOptions.Encoder = [Text.Encodings.Web.JavaScriptEncoder]::UnsafeRelaxedJsonEscaping
$jsonWriter = [Text.Json.Utf8JsonWriter]::new($envelopeStream, $jsonOptions)
try {
    $jsonWriter.WriteStartObject()
    $jsonWriter.WriteString('schema', 'urn:betting-project:sofascore-local-lab:wo034:permission-request-form:v1')
    $jsonWriter.WriteString('destinationUrl', $destinationUrl)
    $jsonWriter.WriteString('category', $formCategory)
    $jsonWriter.WriteString('topic', $formTopic)
    $jsonWriter.WriteString('companyMode', $inputValues['FORM_COMPANY_FIELD_MODE'])
    if ($null -eq $companyValue) {
        $jsonWriter.WriteNull('company')
    } else {
        $jsonWriter.WriteString('company', $companyValue)
    }
    $jsonWriter.WriteString('fullName', $inputValues['REQUESTOR_FULL_LEGAL_NAME'])
    $jsonWriter.WriteString('email', $inputValues['REPLY_EMAIL'])
    $jsonWriter.WriteNumber('messageByteLength', $messageBytes.Length)
    $jsonWriter.WriteString('messageSha256', $messageSha256)
    $jsonWriter.WriteString('message', $message)
    $jsonWriter.WriteStartArray('attachments')
    $jsonWriter.WriteEndArray()
    $jsonWriter.WriteStartArray('pasteLinks')
    $jsonWriter.WriteEndArray()
    $jsonWriter.WriteEndObject()
    $jsonWriter.Flush()
} finally {
    $jsonWriter.Dispose()
}
$envelopePayload = $envelopeStream.ToArray()
$envelopeStream.Dispose()
$envelopeBytes = [byte[]]::new($envelopePayload.Length + 1)
[Array]::Copy($envelopePayload, $envelopeBytes, $envelopePayload.Length)
$envelopeBytes[-1] = 0x0A
$envelopeSha256 = Get-WO034Sha256 -Bytes $envelopeBytes

$payloadOutputs = [ordered]@{
    'outbound-message.utf8.txt' = $messageBytes
    'outbound-form-envelope.utf8.json' = $envelopeBytes
    'outbound-message.sha256.txt' = $utf8.GetBytes("$messageSha256  outbound-message.utf8.txt`n")
    'outbound-form-envelope.sha256.txt' = $utf8.GetBytes("$envelopeSha256  outbound-form-envelope.utf8.json`n")
}

foreach ($name in @($payloadOutputs.Keys) + 'render-evidence.properties') {
    $finalPath = Join-Path $canonicalOutputDirectory $name
    if (Test-Path -LiteralPath $finalPath) {
        throw "Output already exists and will not be overwritten: $name."
    }
}

$temporaryPaths = @{}
$createdFinalPaths = [Collections.Generic.List[string]]::new()
$evidenceFinalPath = Join-Path $canonicalOutputDirectory 'render-evidence.properties'
try {
    foreach ($entry in $payloadOutputs.GetEnumerator()) {
        $temporaryPath = Join-Path $canonicalOutputDirectory `
            ('.wo034-staging-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        $temporaryPaths[$entry.Key] = $temporaryPath
        [IO.File]::WriteAllBytes($temporaryPath, [byte[]]$entry.Value)
    }
    foreach ($entry in $payloadOutputs.GetEnumerator()) {
        $finalPath = Join-Path $canonicalOutputDirectory $entry.Key
        [IO.File]::Move($temporaryPaths[$entry.Key], $finalPath, $false)
        $createdFinalPaths.Add($finalPath)
    }

    foreach ($entry in $payloadOutputs.GetEnumerator()) {
        $finalPath = Join-Path $canonicalOutputDirectory $entry.Key
        Assert-WO034PrivateAcl -Path $finalPath -AllowedSids $allowedPrivateSids `
            -Description "Rendered output $($entry.Key)"
        $persistedBytes = [IO.File]::ReadAllBytes($finalPath)
        if (-not (Test-WO034ByteArrayEqual -Left $persistedBytes -Right ([byte[]]$entry.Value))) {
            throw "Rendered output changed during the write postflight: $($entry.Key)."
        }
    }

    $persistedMessageBytes = [IO.File]::ReadAllBytes(
        (Join-Path $canonicalOutputDirectory 'outbound-message.utf8.txt'))
    $persistedEnvelopeBytes = [IO.File]::ReadAllBytes(
        (Join-Path $canonicalOutputDirectory 'outbound-form-envelope.utf8.json'))
    $messageSha256SecondPass = Get-WO034Sha256 -Bytes $persistedMessageBytes
    $envelopeSha256SecondPass = Get-WO034Sha256 -Bytes $persistedEnvelopeBytes
    if ($messageSha256SecondPass -cne $messageSha256) {
        throw 'The persisted message SHA-256 does not match its first-pass fingerprint.'
    }
    if ($envelopeSha256SecondPass -cne $envelopeSha256) {
        throw 'The persisted form envelope SHA-256 does not match its first-pass fingerprint.'
    }

    $envelopeReadStream = [IO.MemoryStream]::new($persistedEnvelopeBytes, $false)
    $envelopeDocument = [Text.Json.JsonDocument]::Parse($envelopeReadStream)
    try {
        $rootElement = $envelopeDocument.RootElement
        $roundTripMessage = $rootElement.GetProperty('message').GetString()
        $roundTripMessageBytes = $utf8.GetBytes($roundTripMessage)
        if (-not (Test-WO034ByteArrayEqual -Left $roundTripMessageBytes `
                -Right $persistedMessageBytes)) {
            throw 'The message extracted from the form envelope is not byte-identical.'
        }
        if ($rootElement.GetProperty('messageByteLength').GetInt32() -ne
            $persistedMessageBytes.Length) {
            throw 'The form envelope message byte length does not match the persisted message.'
        }
        if ($rootElement.GetProperty('messageSha256').GetString() -cne
            $messageSha256SecondPass) {
            throw 'The form envelope message SHA-256 does not match the persisted message.'
        }
        if ($rootElement.GetProperty('attachments').GetArrayLength() -ne 0 -or
            $rootElement.GetProperty('pasteLinks').GetArrayLength() -ne 0) {
            throw 'The canonical form envelope must not include attachments or pasted links.'
        }
    } finally {
        $envelopeDocument.Dispose()
        $envelopeReadStream.Dispose()
    }

    $evidenceBytes = $utf8.GetBytes((@(
                'WORK_ORDER=WO-SS-20260901-034-j9-permission-request-final-render'
                "RENDERER_VERSION=$rendererVersion"
                "RENDERER_SHA256=$rendererSha256"
                'RENDERER_RUNTIME_AND_SOURCE_STREAMS_REVERIFIED=YES'
                'RENDERER_RUNTIME_AND_SOURCE_CONCURRENT_WRITE_OPEN_DENIED=YES'
                "POWERSHELL_VERSION=$($PSVersionTable.PSVersion.ToString())"
                "TEMPLATE_SHA256=$templateSha256"
                "PRIVATE_BOOTSTRAP_SHA256=$bootstrapSha256"
                'PRIVATE_OWNER_EXECUTION_MATCH=YES'
                'PRIVATE_ROOT_AND_GIT_WORKTREE_DISJOINT=YES'
                "RENDER_PURPOSE=$renderPurpose"
                "STALE_STAGING_FILES_REMOVED=$staleStagingFilesRemoved"
                "INCOMPLETE_CANONICAL_FILES_REMOVED=$incompleteCanonicalFilesRemoved"
                'OUTBOUND_CONTENT_MODE=PRIMARY_ONLY'
                'UNRESOLVED_OWNER_PLACEHOLDER_COUNT=0'
                "OUTBOUND_MESSAGE_BYTE_LENGTH=$($persistedMessageBytes.Length)"
                "OUTBOUND_MESSAGE_SHA256=$messageSha256"
                "OUTBOUND_MESSAGE_SHA256_SECOND_PASS=$messageSha256SecondPass"
                'OUTBOUND_MESSAGE_SHA256_MATCH=YES'
                "OUTBOUND_FORM_ENVELOPE_BYTE_LENGTH=$($persistedEnvelopeBytes.Length)"
                "OUTBOUND_FORM_ENVELOPE_SHA256=$envelopeSha256"
                "OUTBOUND_FORM_ENVELOPE_SHA256_SECOND_PASS=$envelopeSha256SecondPass"
                'OUTBOUND_FORM_ENVELOPE_SHA256_MATCH=YES'
                'FORM_MESSAGE_ROUND_TRIP_BYTE_IDENTICAL=YES'
                "FINAL_RENDERED_STATUS=$(if ($SyntheticQualification) { 'SYNTHETIC_QUALIFICATION' } else { 'FINAL_RENDERED_NOT_SENT' })"
                'EXTERNAL_MESSAGE_SEND_AUTHORIZED=NO'
                'EXTERNAL_MESSAGE_SENT=NO'
            ) -join "`n") + "`n")
    $evidenceName = 'render-evidence.properties'
    $evidenceTemporaryPath = Join-Path $canonicalOutputDirectory `
        ('.wo034-staging-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    $temporaryPaths[$evidenceName] = $evidenceTemporaryPath
    [IO.File]::WriteAllBytes($evidenceTemporaryPath, $evidenceBytes)
    [IO.File]::Move($evidenceTemporaryPath, $evidenceFinalPath, $false)
    $createdFinalPaths.Add($evidenceFinalPath)
    Assert-WO034PrivateAcl -Path $evidenceFinalPath -AllowedSids $allowedPrivateSids `
        -Description 'Rendered output render-evidence.properties'
    $persistedEvidenceBytes = [IO.File]::ReadAllBytes($evidenceFinalPath)
    if (-not (Test-WO034ByteArrayEqual -Left $persistedEvidenceBytes -Right $evidenceBytes)) {
        throw 'The final render evidence changed during its commit-marker write.'
    }
} catch {
    $renderFailure = $_
    try {
        [IO.File]::Delete($evidenceFinalPath)
        if ([IO.File]::Exists($evidenceFinalPath)) {
            throw 'The completion marker still exists after fail-closed deletion.'
        }
    } catch {
        throw ('WO-034 fail-closed cleanup could not remove the completion marker; ' +
            'the payload bundle was left intact for explicit quarantine. Original render failure: ' +
            $renderFailure.Exception.Message)
    }
    $cleanupFailures = [Collections.Generic.List[string]]::new()
    foreach ($temporaryPath in $temporaryPaths.Values) {
        try {
            [IO.File]::Delete($temporaryPath)
            if ([IO.File]::Exists($temporaryPath)) {
                throw 'A staging file still exists after fail-closed deletion.'
            }
        } catch {
            $cleanupFailures.Add([IO.Path]::GetFileName($temporaryPath))
        }
    }
    foreach ($finalPath in @($createdFinalPaths | Where-Object {
                [IO.Path]::GetFileName($_) -cne 'render-evidence.properties'
            })) {
        try {
            [IO.File]::Delete($finalPath)
            if ([IO.File]::Exists($finalPath)) {
                throw 'A rendered payload still exists after fail-closed deletion.'
            }
        } catch {
            $cleanupFailures.Add([IO.Path]::GetFileName($finalPath))
        }
    }
    if ($cleanupFailures.Count -gt 0) {
        throw ('WO-034 fail-closed cleanup could not remove every partial output (' +
            ($cleanupFailures -join ', ') + '). Original render failure: ' +
            $renderFailure.Exception.Message)
    }
    throw $renderFailure
}

Write-Output 'WO034_RENDER_STATUS=SUCCESS'
Write-Output "WO034_RENDERER_VERSION=$rendererVersion"
Write-Output "WO034_RENDERER_SHA256=$rendererSha256"
Write-Output 'WO034_RENDERER_RUNTIME_AND_SOURCE_STREAMS_REVERIFIED=YES'
Write-Output 'WO034_RENDERER_RUNTIME_AND_SOURCE_CONCURRENT_WRITE_OPEN_DENIED=YES'
Write-Output "WO034_POWERSHELL_VERSION=$($PSVersionTable.PSVersion.ToString())"
Write-Output "WO034_TEMPLATE_SHA256=$templateSha256"
Write-Output "WO034_PRIVATE_BOOTSTRAP_SHA256=$bootstrapSha256"
Write-Output 'WO034_PRIVATE_OWNER_EXECUTION_MATCH=YES'
Write-Output 'WO034_PRIVATE_ROOT_AND_GIT_WORKTREE_DISJOINT=YES'
Write-Output "WO034_RENDER_PURPOSE=$renderPurpose"
Write-Output "WO034_STALE_STAGING_FILES_REMOVED=$staleStagingFilesRemoved"
Write-Output "WO034_INCOMPLETE_CANONICAL_FILES_REMOVED=$incompleteCanonicalFilesRemoved"
Write-Output 'WO034_UNRESOLVED_OWNER_PLACEHOLDER_COUNT=0'
Write-Output "WO034_OUTBOUND_MESSAGE_BYTE_LENGTH=$($messageBytes.Length)"
Write-Output "WO034_OUTBOUND_MESSAGE_SHA256=$messageSha256"
Write-Output "WO034_OUTBOUND_MESSAGE_SHA256_SECOND_PASS=$messageSha256SecondPass"
Write-Output 'WO034_OUTBOUND_MESSAGE_SHA256_MATCH=YES'
Write-Output "WO034_OUTBOUND_FORM_ENVELOPE_BYTE_LENGTH=$($envelopeBytes.Length)"
Write-Output "WO034_OUTBOUND_FORM_ENVELOPE_SHA256=$envelopeSha256"
Write-Output "WO034_OUTBOUND_FORM_ENVELOPE_SHA256_SECOND_PASS=$envelopeSha256SecondPass"
Write-Output 'WO034_OUTBOUND_FORM_ENVELOPE_SHA256_MATCH=YES'
Write-Output 'WO034_FORM_MESSAGE_ROUND_TRIP_BYTE_IDENTICAL=YES'
Write-Output 'WO034_EXTERNAL_MESSAGE_SEND_AUTHORIZED=NO'
Write-Output 'WO034_EXTERNAL_MESSAGE_SENT=NO'
} finally {
    if ($null -ne $renderLockStream) {
        $renderLockStream.Dispose()
    }
}
