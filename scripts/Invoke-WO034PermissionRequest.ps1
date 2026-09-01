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

    [switch]$SyntheticQualification
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the WO-034 launcher.'
}

$expectedRendererSha256 = 'b722709fd0d8988718fcfefb8506803daba4cd241bb3dc00a88874dd708f28b6'
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Get-WO034LauncherSha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Test-WO034LauncherBytesEqual {
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

function Read-WO034LockedBytes {
    param(
        [Parameter(Mandatory = $true)][IO.FileStream]$Stream,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not $Stream.CanRead -or $Stream.Length -lt 1 -or $Stream.Length -gt 1048576) {
        throw "$Description is outside the launcher byte bound."
    }
    $Stream.Position = 0
    $buffer = [IO.MemoryStream]::new()
    try {
        $Stream.CopyTo($buffer)
        return $buffer.ToArray()
    } finally {
        $buffer.Dispose()
    }
}

function Assert-WO034LauncherNoReparsePath {
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
        throw "$Description escapes its launcher anchor."
    }
    $current = $canonicalAnchor
    if (-not (Test-Path -LiteralPath $current)) {
        throw "$Description launcher anchor does not exist."
    }
    if (((Get-Item -LiteralPath $current -Force).Attributes -band
            [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Description launcher anchor must not be a reparse point."
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
            throw "$Description and all its launcher ancestors must exist."
        }
        if (((Get-Item -LiteralPath $current -Force).Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "$Description must not traverse a reparse point."
        }
    }
}

$repositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$privateRoot = [IO.Path]::GetFullPath($PrivateRoot)
$qualifiedRuntimeRoot = [IO.Path]::GetFullPath((Join-Path $privateRoot 'qualified-runtime'))
$launcherPath = [IO.Path]::GetFullPath($PSCommandPath)
$expectedLauncherPath = [IO.Path]::GetFullPath((Join-Path $qualifiedRuntimeRoot `
    'Invoke-WO034PermissionRequest.ps1'))
$runtimeRendererPath = [IO.Path]::GetFullPath((Join-Path $qualifiedRuntimeRoot `
    'Render-WO034PermissionRequest.ps1'))
$sourceRendererPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
    'scripts\Render-WO034PermissionRequest.ps1'))

if ($launcherPath -cne $expectedLauncherPath) {
    throw 'WO-034 must launch from its qualified-runtime copy.'
}
if (-not (Test-Path -LiteralPath $repositoryRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $privateRoot -PathType Container) -or
    -not (Test-Path -LiteralPath $runtimeRendererPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $sourceRendererPath -PathType Leaf)) {
    throw 'The WO-034 launcher source and runtime paths must already exist.'
}
Assert-WO034LauncherNoReparsePath -Path $launcherPath -Anchor 'C:\Users' `
    -Description 'WO-034 launcher'
Assert-WO034LauncherNoReparsePath -Path $runtimeRendererPath -Anchor 'C:\Users' `
    -Description 'WO-034 runtime renderer'
Assert-WO034LauncherNoReparsePath -Path $sourceRendererPath -Anchor $repositoryRoot `
    -Description 'WO-034 versioned renderer source'

$runtimeRendererStream = $null
$sourceRendererStream = $null
try {
    try {
        $runtimeRendererStream = [IO.File]::Open(
            $runtimeRendererPath, [IO.FileMode]::Open, [IO.FileAccess]::Read,
            [IO.FileShare]::Read)
        $sourceRendererStream = [IO.File]::Open(
            $sourceRendererPath, [IO.FileMode]::Open, [IO.FileAccess]::Read,
            [IO.FileShare]::Read)
    } catch [IO.IOException] {
        throw 'WO-034 could not acquire immutable read locks for both renderer copies.'
    }

    $runtimeRendererBytes = Read-WO034LockedBytes -Stream $runtimeRendererStream `
        -Description 'WO-034 runtime renderer'
    $sourceRendererBytes = Read-WO034LockedBytes -Stream $sourceRendererStream `
        -Description 'WO-034 versioned renderer source'
    if (-not (Test-WO034LauncherBytesEqual -Left $runtimeRendererBytes `
            -Right $sourceRendererBytes)) {
        throw 'The locked runtime renderer does not match the locked versioned source bytes.'
    }
    $rendererSha256 = Get-WO034LauncherSha256 -Bytes $runtimeRendererBytes
    if ($rendererSha256 -cne $expectedRendererSha256) {
        throw 'The locked WO-034 renderer fingerprint is outside the launcher contract.'
    }
    if ($runtimeRendererBytes.Length -ge 3 -and
        $runtimeRendererBytes[0] -eq 0xEF -and $runtimeRendererBytes[1] -eq 0xBB -and
        $runtimeRendererBytes[2] -eq 0xBF) {
        throw 'The locked WO-034 renderer must be UTF-8 without BOM.'
    }
    try {
        $rendererText = $utf8.GetString($runtimeRendererBytes)
    } catch {
        throw 'The locked WO-034 renderer is not strict UTF-8.'
    }
    $rendererScript = [ScriptBlock]::Create($rendererText)
    & $rendererScript -InputPath $InputPath -OutputDirectory $OutputDirectory `
        -RepositoryRoot $repositoryRoot -PrivateRoot $privateRoot `
        -AttestedRendererPath $runtimeRendererPath `
        -AttestedRendererSha256 $rendererSha256 `
        -AttestedRendererStream $runtimeRendererStream `
        -AttestedSourceRendererStream $sourceRendererStream `
        -SyntheticQualification:$SyntheticQualification
} finally {
    if ($null -ne $sourceRendererStream) {
        $sourceRendererStream.Dispose()
    }
    if ($null -ne $runtimeRendererStream) {
        $runtimeRendererStream.Dispose()
    }
}
