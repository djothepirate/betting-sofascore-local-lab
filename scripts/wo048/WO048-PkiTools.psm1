Set-StrictMode -Version 3.0

$script:Wo048CampaignName = 'WO-SS-20260904-046'
$script:Wo048ClientAuthOid = '1.3.6.1.5.5.7.3.2'
$script:Wo048ServerAuthOid = '1.3.6.1.5.5.7.3.1'
$script:Wo048Sha256WithRsaOid = '1.2.840.113549.1.1.11'

function ConvertTo-WO048LowerHex {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return (($Bytes | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Get-WO048Sha256Text {
    param([Parameter(Mandatory = $true)][string]$Value)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-WO048LowerHex -Bytes $sha256.ComputeHash(
            [System.Text.Encoding]::UTF8.GetBytes($Value))
    }
    finally {
        $sha256.Dispose()
    }
}

function Get-WO048StateIdentityBinding {
    param(
        [Parameter(Mandatory = $true)][string]$WorkOrder,
        [Parameter(Mandatory = $true)][string]$ProvisioningWorkOrder,
        [Parameter(Mandatory = $true)][string]$RunId,
        [Parameter(Mandatory = $true)][string]$OwnerSid,
        [Parameter(Mandatory = $true)][string]$CreatedAtUtc,
        [Parameter(Mandatory = $true)][string]$RunRoot,
        [Parameter(Mandatory = $true)][string]$MarkerPath
    )

    $binding = @(
        'WO046_LOCAL_MTLS_IDENTITY_V2',
        $WorkOrder,
        $ProvisioningWorkOrder,
        $RunId,
        $OwnerSid,
        $CreatedAtUtc,
        [System.IO.Path]::GetFullPath($RunRoot),
        [System.IO.Path]::GetFullPath($MarkerPath)) -join "`n"
    return Get-WO048Sha256Text -Value $binding
}

function New-WO048FinalizationLockAuthority {
    param(
        [Parameter(Mandatory = $true)][string]$WorkOrder,
        [Parameter(Mandatory = $true)][string]$ProvisioningWorkOrder,
        [Parameter(Mandatory = $true)][string]$RunId,
        [Parameter(Mandatory = $true)][string]$OwnerSid,
        [Parameter(Mandatory = $true)][string]$CreatedAtUtc,
        [Parameter(Mandatory = $true)][string]$RunRoot,
        [Parameter(Mandatory = $true)][string]$MarkerPath,
        [Parameter(Mandatory = $true)][string]$LockPath,
        [Parameter(Mandatory = $true)][string]$StateIdentityBindingSha256
    )

    return [pscustomobject][ordered]@{
        schemaVersion = 1
        format = 'WO048_FINALIZATION_LOCK_AUTHORITY_V1'
        workOrder = $WorkOrder
        provisioningWorkOrder = $ProvisioningWorkOrder
        runId = $RunId
        ownerSid = $OwnerSid
        createdAtUtc = $CreatedAtUtc
        runRoot = [System.IO.Path]::GetFullPath($RunRoot)
        markerPath = [System.IO.Path]::GetFullPath($MarkerPath)
        lockPath = [System.IO.Path]::GetFullPath($LockPath)
        stateIdentityBindingSha256 = $StateIdentityBindingSha256
        finalizationStatus = 'NOT_READY'
        finalCertificateRecords = $null
    }
}

function Read-WO048FinalizationLockJournalFromStream {
    param(
        [Parameter(Mandatory = $true)][System.IO.FileStream]$Stream,
        [ValidateRange(1, 65536)][int]$MaximumBytes = 65536
    )

    if (-not $Stream.CanRead -or -not $Stream.CanSeek -or
        $Stream.Length -le 0 -or $Stream.Length -gt $MaximumBytes -or
        $Stream.Length -gt [int]::MaxValue) {
        throw 'The WO-048 finalization lock journal has an invalid bounded shape.'
    }
    $journalBytes = New-Object byte[] ([int]$Stream.Length)
    try {
        $Stream.Position = 0
        $offset = 0
        while ($offset -lt $journalBytes.Length) {
            $read = $Stream.Read(
                $journalBytes, $offset, $journalBytes.Length - $offset)
            if ($read -le 0) {
                throw 'The WO-048 finalization lock journal ended before its bounded length.'
            }
            $offset += $read
        }
        if ($journalBytes.Length -ge 3 -and $journalBytes[0] -eq 0xEF -and
            $journalBytes[1] -eq 0xBB -and $journalBytes[2] -eq 0xBF) {
            throw 'The WO-048 finalization lock journal must be UTF-8 without BOM.'
        }
        $text = [Text.UTF8Encoding]::new($false, $true).GetString($journalBytes)
        if ($text.Contains("`r")) {
            throw 'The WO-048 finalization lock journal contains a non-canonical line ending.'
        }
        $segments = @($text.Split([char]"`n"))
        $hasTrailingFragment = -not $text.EndsWith(
            "`n", [StringComparison]::Ordinal)
        $completeFrameCount = if ($hasTrailingFragment) {
            $segments.Count - 1
        }
        else {
            $segments.Count - 1
        }
        if ($completeFrameCount -lt 1 -or $completeFrameCount -gt 2) {
            throw 'The WO-048 finalization lock journal has no exact bounded complete frame set.'
        }
        $authorities = [System.Collections.Generic.List[object]]::new()
        for ($index = 0; $index -lt $completeFrameCount; $index++) {
            $frame = $segments[$index]
            if ($frame -cnotmatch '^WO048LOCK1\|([1-9][0-9]{0,4})\|([0-9a-f]{64})\|([A-Za-z0-9+/]+={0,2})$') {
                throw 'A complete WO-048 finalization lock journal frame is invalid.'
            }
            $declaredLength = [int]$Matches[1]
            $declaredSha256 = $Matches[2]
            $payloadBytes = $null
            try {
                $payloadBytes = [Convert]::FromBase64String($Matches[3])
            }
            catch {
                throw 'A complete WO-048 finalization lock journal payload is invalid.'
            }
            try {
                if ($payloadBytes.Length -ne $declaredLength -or
                    $payloadBytes.Length -gt 16384) {
                    throw 'A WO-048 finalization lock payload length is invalid.'
                }
                $sha256 = [Security.Cryptography.SHA256]::Create()
                try {
                    $actualSha256 = ConvertTo-WO048LowerHex `
                        -Bytes $sha256.ComputeHash($payloadBytes)
                }
                finally {
                    $sha256.Dispose()
                }
                if ($actualSha256 -cne $declaredSha256) {
                    throw 'A WO-048 finalization lock payload hash is invalid.'
                }
                $payloadJson = [Text.UTF8Encoding]::new(
                    $false, $true).GetString($payloadBytes)
                $authority = $payloadJson |
                    ConvertFrom-Json -DateKind String -ErrorAction Stop
                if ($authority -isnot [pscustomobject]) {
                    throw 'A WO-048 finalization lock payload is not one object.'
                }
                $authorities.Add($authority)
            }
            finally {
                if ($null -ne $payloadBytes) {
                    [Array]::Clear($payloadBytes, 0, $payloadBytes.Length)
                }
            }
        }
        $trailingFragment = if ($hasTrailingFragment) {
            $segments[-1]
        }
        else {
            ''
        }
        if ($hasTrailingFragment -and
            -not ('WO048LOCK1|'.StartsWith(
                    $trailingFragment, [StringComparison]::Ordinal) -or
                $trailingFragment.StartsWith(
                    'WO048LOCK1|', [StringComparison]::Ordinal))) {
            throw 'The WO-048 finalization lock trailing fragment is not an interrupted frame.'
        }
        $validText = (($segments[0..($completeFrameCount - 1)] -join "`n") + "`n")
        $validLength = [Text.UTF8Encoding]::new(
            $false, $true).GetByteCount($validText)
        return [pscustomobject]@{
            Authorities = @($authorities)
            Latest = $authorities[$authorities.Count - 1]
            ValidLength = [long]$validLength
            HasTrailingFragment = $hasTrailingFragment
        }
    }
    finally {
        [Array]::Clear($journalBytes, 0, $journalBytes.Length)
    }
}

function Write-WO048FinalizationLockAuthorityToStream {
    param(
        [Parameter(Mandatory = $true)][System.IO.FileStream]$Stream,
        [Parameter(Mandatory = $true)]$Authority,
        [ValidateSet('NONE', 'AFTER_PARTIAL_FRAME')]
        [string]$QualificationFailurePoint = 'NONE'
    )

    if (-not $Stream.CanRead -or -not $Stream.CanWrite -or -not $Stream.CanSeek) {
        throw 'The WO-048 finalization lock stream is not readable writable and seekable.'
    }
    $payloadBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes(
        ($Authority | ConvertTo-Json -Depth 16 -Compress))
    $frameBytes = $null
    try {
        if ($payloadBytes.Length -lt 1 -or $payloadBytes.Length -gt 16384) {
            throw 'The WO-048 finalization lock authority exceeded its bounded private format.'
        }
        $sha256 = [Security.Cryptography.SHA256]::Create()
        try {
            $payloadSha256 = ConvertTo-WO048LowerHex `
                -Bytes $sha256.ComputeHash($payloadBytes)
        }
        finally {
            $sha256.Dispose()
        }
        $frameText = 'WO048LOCK1|' + $payloadBytes.Length + '|' +
            $payloadSha256 + '|' + [Convert]::ToBase64String($payloadBytes) + "`n"
        $frameBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($frameText)

        $journal = $null
        if ($Stream.Length -gt 0) {
            $journal = Read-WO048FinalizationLockJournalFromStream -Stream $Stream
            $latest = $journal.Latest
            foreach ($property in @(
                    'workOrder', 'provisioningWorkOrder', 'runId', 'ownerSid',
                    'createdAtUtc', 'runRoot', 'markerPath', 'lockPath',
                    'stateIdentityBindingSha256')) {
                if ([string]$latest.$property -cne [string]$Authority.$property) {
                    throw 'The WO-048 finalization lock transition changed immutable authority.'
                }
            }
            if ($journal.HasTrailingFragment) {
                $Stream.SetLength($journal.ValidLength)
                $Stream.Flush($true)
            }
            $latestJson = $latest | ConvertTo-Json -Depth 16 -Compress
            $authorityJson = $Authority | ConvertTo-Json -Depth 16 -Compress
            if ($latest.finalizationStatus -ceq $Authority.finalizationStatus) {
                if ($latestJson -cne $authorityJson) {
                    throw 'The WO-048 finalization lock repeated transition diverged.'
                }
                return
            }
            if ($journal.Authorities.Count -ne 1 -or
                $latest.finalizationStatus -cne 'NOT_READY' -or
                $Authority.finalizationStatus -cne 'READY_AFTER_RESIDUAL_PROOF') {
                throw 'The WO-048 finalization lock transition order is invalid.'
            }
        }
        elseif ($Authority.finalizationStatus -cne 'NOT_READY') {
            throw 'The first WO-048 finalization lock frame must be NOT_READY.'
        }
        if ($Stream.Length + $frameBytes.Length -gt 65536) {
            throw 'The WO-048 finalization lock journal would exceed its exact bound.'
        }
        $Stream.Position = $Stream.Length
        if ($QualificationFailurePoint -ceq 'AFTER_PARTIAL_FRAME') {
            $partialLength = [Math]::Max(1, [Math]::Floor($frameBytes.Length / 2))
            $Stream.Write($frameBytes, 0, $partialLength)
            $Stream.Flush($true)
            throw 'WO048_QUALIFICATION_INJECTED_PARTIAL_LOCK_FRAME'
        }
        $Stream.Write($frameBytes, 0, $frameBytes.Length)
        $Stream.Flush($true)
        if ($Stream.Length -lt $frameBytes.Length) {
            throw 'The WO-048 finalization lock authority was not durably appended.'
        }
    }
    finally {
        [Array]::Clear($payloadBytes, 0, $payloadBytes.Length)
        if ($null -ne $frameBytes) {
            [Array]::Clear($frameBytes, 0, $frameBytes.Length)
        }
    }
}

function Assert-WO048FinalizationLockAuthority {
    param(
        [Parameter(Mandatory = $true)]$Authority,
        [Parameter(Mandatory = $true)][string]$ExpectedWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedProvisioningWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedRunId,
        [Parameter(Mandatory = $true)][string]$ExpectedOwnerSid,
        [Parameter(Mandatory = $true)][string]$ExpectedRunRoot,
        [Parameter(Mandatory = $true)][string]$ExpectedMarkerPath,
        [Parameter(Mandatory = $true)][string]$ExpectedLockPath,
        [switch]$RequireFinalizationReady
    )

    $expectedProperties = @(
        'schemaVersion', 'format', 'workOrder', 'provisioningWorkOrder',
        'runId', 'ownerSid', 'createdAtUtc', 'runRoot', 'markerPath',
        'lockPath', 'stateIdentityBindingSha256', 'finalizationStatus',
        'finalCertificateRecords')
    $actualProperties = @($Authority.PSObject.Properties |
        ForEach-Object { $_.Name })
    if ($actualProperties.Count -ne $expectedProperties.Count -or
        @($actualProperties | Where-Object { $_ -cnotin $expectedProperties }).Count -ne 0) {
        throw 'The WO-048 finalization lock authority property set is not exact.'
    }
    $parsedCreatedAt = [DateTimeOffset]::MinValue
    if ($Authority.schemaVersion -ne 1 -or
        $Authority.format -cne 'WO048_FINALIZATION_LOCK_AUTHORITY_V1' -or
        $Authority.workOrder -cne $ExpectedWorkOrder -or
        $Authority.provisioningWorkOrder -cne $ExpectedProvisioningWorkOrder -or
        $Authority.runId -cne $ExpectedRunId -or
        $Authority.ownerSid -cne $ExpectedOwnerSid -or
        $Authority.stateIdentityBindingSha256 -cnotmatch '^[0-9a-f]{64}$' -or
        $Authority.finalizationStatus -notin @(
            'NOT_READY', 'READY_AFTER_RESIDUAL_PROOF') -or
        ($Authority.finalizationStatus -ceq 'NOT_READY' -and
            $null -ne $Authority.finalCertificateRecords) -or
        ($RequireFinalizationReady -and
            $Authority.finalizationStatus -cne 'READY_AFTER_RESIDUAL_PROOF') -or
        -not [DateTimeOffset]::TryParseExact(
            [string]$Authority.createdAtUtc,
            'O',
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$parsedCreatedAt)) {
        throw 'The WO-048 finalization lock authority identity is not exact.'
    }
    Assert-WO048ExactPath -Actual ([string]$Authority.runRoot) -Expected $ExpectedRunRoot `
        -Failure 'The WO-048 finalization lock authority run root changed.'
    Assert-WO048ExactPath -Actual ([string]$Authority.markerPath) -Expected $ExpectedMarkerPath `
        -Failure 'The WO-048 finalization lock authority marker path changed.'
    Assert-WO048ExactPath -Actual ([string]$Authority.lockPath) -Expected $ExpectedLockPath `
        -Failure 'The WO-048 finalization lock authority lock path changed.'
    $expectedBinding = Get-WO048StateIdentityBinding `
        -WorkOrder ([string]$Authority.workOrder) `
        -ProvisioningWorkOrder ([string]$Authority.provisioningWorkOrder) `
        -RunId ([string]$Authority.runId) `
        -OwnerSid ([string]$Authority.ownerSid) `
        -CreatedAtUtc ([string]$Authority.createdAtUtc) `
        -RunRoot ([string]$Authority.runRoot) `
        -MarkerPath ([string]$Authority.markerPath)
    if ($Authority.stateIdentityBindingSha256 -cne $expectedBinding) {
        throw 'The WO-048 finalization lock authority binding is invalid.'
    }
    if ($Authority.finalizationStatus -ceq 'READY_AFTER_RESIDUAL_PROOF') {
        Assert-WO048CertificateRecordSetShape `
            -Records @($Authority.finalCertificateRecords) `
            -CanonicalRunId $ExpectedRunId
    }
}

function Assert-WO048FinalizationMarkerAuthority {
    param(
        [Parameter(Mandatory = $true)]$Marker,
        [Parameter(Mandatory = $true)][string]$ExpectedWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedProvisioningWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedRunId,
        [Parameter(Mandatory = $true)][string]$ExpectedOwnerSid,
        [Parameter(Mandatory = $true)][string]$ExpectedRunRoot,
        [Parameter(Mandatory = $true)][string]$ExpectedMarkerPath
    )

    $expectedProperties = @(
        'schemaVersion', 'workOrder', 'provisioningWorkOrder', 'runId',
        'ownerSid', 'createdAtUtc', 'runRoot', 'markerPath',
        'stateIdentityBindingSha256', 'provisioningStateStatus',
        'passStateSha256', 'finalizationStatus', 'finalCertificateRecords')
    $actualProperties = @($Marker.PSObject.Properties |
        ForEach-Object { $_.Name })
    $parsedCreatedAt = [DateTimeOffset]::MinValue
    if ($actualProperties.Count -ne $expectedProperties.Count -or
        @($actualProperties | Where-Object { $_ -cnotin $expectedProperties }).Count -ne 0 -or
        $Marker.schemaVersion -ne 2 -or $Marker.workOrder -cne $ExpectedWorkOrder -or
        $Marker.provisioningWorkOrder -cne $ExpectedProvisioningWorkOrder -or
        $Marker.runId -cne $ExpectedRunId -or $Marker.ownerSid -cne $ExpectedOwnerSid -or
        $Marker.provisioningStateStatus -cne 'CLEANUP_TRANSITION' -or
        $Marker.finalizationStatus -cne 'READY_AFTER_RESIDUAL_PROOF' -or
        $Marker.stateIdentityBindingSha256 -cnotmatch '^[0-9a-f]{64}$' -or
        ($null -ne $Marker.passStateSha256 -and
            $Marker.passStateSha256 -cnotmatch '^[0-9a-f]{64}$') -or
        -not [DateTimeOffset]::TryParseExact(
            [string]$Marker.createdAtUtc,
            'O',
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$parsedCreatedAt)) {
        throw 'The WO-048 finalization marker authority is not exact.'
    }
    Assert-WO048ExactPath -Actual ([string]$Marker.runRoot) -Expected $ExpectedRunRoot `
        -Failure 'The WO-048 finalization marker run root changed.'
    Assert-WO048ExactPath -Actual ([string]$Marker.markerPath) -Expected $ExpectedMarkerPath `
        -Failure 'The WO-048 finalization marker path changed.'
    $expectedBinding = Get-WO048StateIdentityBinding `
        -WorkOrder ([string]$Marker.workOrder) `
        -ProvisioningWorkOrder ([string]$Marker.provisioningWorkOrder) `
        -RunId ([string]$Marker.runId) `
        -OwnerSid ([string]$Marker.ownerSid) `
        -CreatedAtUtc ([string]$Marker.createdAtUtc) `
        -RunRoot ([string]$Marker.runRoot) `
        -MarkerPath ([string]$Marker.markerPath)
    if ($Marker.stateIdentityBindingSha256 -cne $expectedBinding) {
        throw 'The WO-048 finalization marker immutable binding is invalid.'
    }
    Assert-WO048CertificateRecordSetShape `
        -Records @($Marker.finalCertificateRecords) `
        -CanonicalRunId $ExpectedRunId
}

function Get-WO048FinalizationRecoveryPhase {
    param(
        [Parameter(Mandatory = $true)][bool]$StateExists,
        [Parameter(Mandatory = $true)][bool]$MarkerExists,
        [Parameter(Mandatory = $true)][bool]$LockExists,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$ChildNames
    )

    if ($StateExists) {
        if (-not $MarkerExists -or -not $LockExists) {
            throw 'An active WO-048 state is missing its marker or finalization lock.'
        }
        return 'ACTIVE_STATE_MARKER_LOCK'
    }

    $expectedChildren = [System.Collections.Generic.List[string]]::new()
    if ($MarkerExists) {
        $expectedChildren.Add('.wo048-owner.json')
    }
    if ($LockExists) {
        $expectedChildren.Add('.wo048-pki.lock')
    }
    if ($ChildNames.Count -ne $expectedChildren.Count -or
        @($ChildNames | Where-Object { $_ -cnotin $expectedChildren }).Count -ne 0 -or
        @($expectedChildren | Where-Object { $_ -cnotin $ChildNames }).Count -ne 0) {
        throw 'The WO-048 finalization root contains an unexpected or missing child.'
    }
    if ($MarkerExists -and $LockExists) {
        return 'FINAL_MARKER_AND_LOCK'
    }
    if ($MarkerExists) {
        return 'FINAL_MARKER_ONLY'
    }
    if ($LockExists) {
        return 'FINAL_LOCK_ONLY'
    }
    return 'FINAL_EMPTY_ROOT'
}

function Invoke-WO048SanitizedCleanupFailureBoundary {
    param([Parameter(Mandatory = $true)][scriptblock]$Action)

    try {
        & $Action
    }
    catch {
        $publicFailure = [InvalidOperationException]::new(
            'WO048_PKI_CLEANUP_FAILED_CLOSED')
        throw $publicFailure
    }
}

function Get-WO048FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'The WO-048 hash target is not an existing file.'
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-WO048CertificateSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-WO048LowerHex -Bytes $sha256.ComputeHash($Certificate.RawData)
    }
    finally {
        $sha256.Dispose()
    }
}

function New-WO048RandomSecret {
    param([ValidateRange(32, 128)][int]$ByteCount = 48)

    $bytes = New-Object byte[] $ByteCount
    try {
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Write-WO048Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Content
    )

    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        [System.Text.UTF8Encoding]::new($false, $true))
}

function Assert-WO048NotReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Path)

    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'WO-048 refuses a path that is a reparse point.'
    }
}

function Assert-WO048NoDescendantReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Root)

    Assert-WO048NotReparsePoint -Path $Root
    foreach ($item in Get-ChildItem -LiteralPath $Root -Force -Recurse -ErrorAction Stop) {
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'WO-048 refuses a private root containing a descendant reparse point.'
        }
    }
}

function Assert-WO048ExactPath {
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

function Assert-WO048LocalFixedPathPrefix {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateSet('None', 'Directory', 'File')]
        [string]$RequireExistingType = 'None'
    )

    if ([string]::IsNullOrWhiteSpace($Path) -or $Path -ne $Path.Trim() -or
        -not [System.IO.Path]::IsPathFullyQualified($Path)) {
        throw 'WO-048 requires an exact fully qualified local path.'
    }
    $canonicalPath = [System.IO.Path]::GetFullPath($Path)
    if ($canonicalPath.StartsWith('\\', [System.StringComparison]::Ordinal) -or
        $canonicalPath -notmatch '^[A-Za-z]:\\') {
        throw 'WO-048 refuses UNC, provider and non-drive paths before filesystem access.'
    }
    $driveRoot = [System.IO.Path]::GetPathRoot($canonicalPath)
    if ($driveRoot -notmatch '^[A-Za-z]:\\$') {
        throw 'WO-048 cannot prove an exact local drive root.'
    }
    $drive = [System.IO.DriveInfo]::new($driveRoot)
    if ($drive.DriveType -ne [System.IO.DriveType]::Fixed) {
        throw 'WO-048 refuses non-fixed storage for private PKI material.'
    }
    if (-not (Test-Path -LiteralPath $driveRoot -PathType Container)) {
        throw 'The exact WO-048 local drive root is unavailable.'
    }
    Assert-WO048NotReparsePoint -Path $driveRoot

    $relative = $canonicalPath.Substring($driveRoot.Length)
    $current = $driveRoot
    foreach ($segment in $relative.Split(
            [char[]]@(
                [System.IO.Path]::DirectorySeparatorChar,
                [System.IO.Path]::AltDirectorySeparatorChar),
            [System.StringSplitOptions]::RemoveEmptyEntries)) {
        $candidate = [System.IO.Path]::GetFullPath((Join-Path $current $segment))
        Assert-WO048ExactPath -Actual (Split-Path -Parent $candidate) -Expected $current `
            -Failure 'A WO-048 path component escaped its exact local parent.'
        if (-not (Test-Path -LiteralPath $candidate)) {
            break
        }
        Assert-WO048NotReparsePoint -Path $candidate
        $current = (Get-Item -LiteralPath $candidate -Force -ErrorAction Stop).FullName
    }

    if ($RequireExistingType -eq 'Directory' -and
        -not (Test-Path -LiteralPath $canonicalPath -PathType Container)) {
        throw 'The required WO-048 local directory is unavailable.'
    }
    if ($RequireExistingType -eq 'File' -and
        -not (Test-Path -LiteralPath $canonicalPath -PathType Leaf)) {
        throw 'The required WO-048 local file is unavailable.'
    }
    return $canonicalPath
}

function Resolve-WO048ExactJava25Toolchain {
    $javaHomeValue = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    if ([string]::IsNullOrWhiteSpace($javaHomeValue) -or
        $javaHomeValue -cne $javaHomeValue.Trim() -or
        -not [System.IO.Path]::IsPathFullyQualified($javaHomeValue) -or
        $javaHomeValue.StartsWith('\\', [System.StringComparison]::Ordinal)) {
        throw 'WO-048 requires one exact fully qualified local JAVA_HOME for Java 25.'
    }
    $javaHome = Assert-WO048LocalFixedPathPrefix `
        -Path $javaHomeValue -RequireExistingType Directory
    $binPath = Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $javaHome 'bin') -RequireExistingType Directory
    $tools = [ordered]@{}
    foreach ($name in @('java.exe', 'javac.exe', 'keytool.exe')) {
        $toolPath = Assert-WO048LocalFixedPathPrefix `
            -Path (Join-Path $binPath $name) -RequireExistingType File
        Assert-WO048ExactPath -Actual (Split-Path -Parent $toolPath) -Expected $binPath `
            -Failure 'A WO-048 Java tool escaped the exact JAVA_HOME bin directory.'
        Assert-WO048ExactPath -Actual (Resolve-Path -LiteralPath $toolPath).Path `
            -Expected $toolPath -Failure 'WO-048 refuses a redirected Java launcher shim.'
        $fileVersion = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($toolPath)
        $version = $fileVersion.ProductVersion
        if ([string]::IsNullOrWhiteSpace($version) -or
            $version -notmatch '^25(?:\.|\+|$)' -or
            $fileVersion.OriginalFilename -cne $name) {
            throw 'A WO-048 Java tool does not carry exact Java 25 launcher provenance.'
        }
        $tools[$name] = $toolPath
    }
    return [pscustomobject]@{
        JavaHome = $javaHome
        Java = $tools['java.exe']
        Javac = $tools['javac.exe']
        Keytool = $tools['keytool.exe']
    }
}

function New-WO048ExclusiveRunRoot {
    param(
        [Parameter(Mandatory = $true)][string]$CampaignBase,
        [Parameter(Mandatory = $true)][guid]$RunId
    )

    $canonicalBase = Assert-WO048LocalFixedPathPrefix `
        -Path $CampaignBase -RequireExistingType Directory
    $runRoot = [System.IO.Path]::GetFullPath(
        (Join-Path $canonicalBase $RunId.ToString('D')))
    Assert-WO048ImmediateGuidChild -Parent $canonicalBase -Child $runRoot -RunId $RunId
    if (Test-Path -LiteralPath $runRoot) {
        throw 'A WO-048 run-root collision was detected before creation.'
    }
    try {
        New-Item -ItemType Directory -Path $runRoot -ErrorAction Stop | Out-Null
    }
    catch {
        throw 'The exclusive WO-048 run root could not be created.'
    }
    if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) {
        throw 'The exclusive WO-048 run root is not a directory.'
    }
    Assert-WO048NotReparsePoint -Path $runRoot
    if (@(Get-ChildItem -LiteralPath $runRoot -Force -ErrorAction Stop).Count -ne 0) {
        throw 'The new WO-048 run root was not empty at ownership handoff.'
    }
    return (Get-Item -LiteralPath $runRoot -Force -ErrorAction Stop).FullName
}

function Assert-WO048CanonicalLowercaseRunId {
    param([Parameter(Mandatory = $true)][string]$RunId)

    if ($RunId -cnotmatch
        '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$') {
        throw 'WO-048 requires one canonical lowercase UUID-D RunId.'
    }
    $parsed = [guid]::ParseExact($RunId, 'D')
    if ($parsed.ToString('D') -cne $RunId) {
        throw 'WO-048 refuses silent RunId normalization.'
    }
    return $RunId
}

function Get-WO048ExactAddedCanonicalRunId {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Before,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$After
    )

    $added = @($After | Where-Object { $_ -cnotin $Before })
    $removed = @($Before | Where-Object { $_ -cnotin $After })
    if ($added.Count -ne 1 -or $removed.Count -ne 0) {
        throw 'WO-048 cannot derive one exact new run root for bounded recovery.'
    }
    return Assert-WO048CanonicalLowercaseRunId -RunId $added[0]
}

function Invoke-WO048GuardedPostProvisioningPhase {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$BeforeRunRoots,
        [Parameter(Mandatory = $true)][scriptblock]$ProvisioningAction,
        [Parameter(Mandatory = $true)][scriptblock]$RunRootSnapshotAction,
        [Parameter(Mandatory = $true)][scriptblock]$RollbackAuthorityAction,
        [Parameter(Mandatory = $true)][scriptblock]$QualificationAction,
        [Parameter(Mandatory = $true)][scriptblock]$CleanupAction,
        [Parameter(Mandatory = $true)][scriptblock]$RunRootAbsentAction
    )

    $publicOutput = @()
    $runId = $null
    $rollbackAuthorityEstablished = $false
    try {
        $publicOutput = @(& $ProvisioningAction)
        $afterRunRoots = @(& $RunRootSnapshotAction)
        $runId = Get-WO048ExactAddedCanonicalRunId `
            -Before $BeforeRunRoots -After $afterRunRoots
        & $RollbackAuthorityAction -RunId $runId
        $rollbackAuthorityEstablished = $true
        $qualificationResult = & $QualificationAction `
            -RunId $runId -PublicOutput ([string[]]$publicOutput)
        return [pscustomobject]@{
            RunId = $runId
            PublicOutput = [string[]]$publicOutput
            QualificationResult = $qualificationResult
        }
    }
    catch {
        if (-not $rollbackAuthorityEstablished) {
            try {
                $currentRunRoots = @(& $RunRootSnapshotAction)
            }
            catch {
                throw [System.InvalidOperationException]::new(
                    'WO-048 failed before a private run-root recovery delta could be inspected.')
            }
            $added = @($currentRunRoots | Where-Object { $_ -cnotin $BeforeRunRoots })
            $removed = @($BeforeRunRoots | Where-Object { $_ -cnotin $currentRunRoots })
            if ($added.Count -eq 0 -and $removed.Count -eq 0 -and
                $currentRunRoots.Count -eq $BeforeRunRoots.Count) {
                $alreadyRestored = [System.InvalidOperationException]::new(
                    'WO-048 provisioning failed after restoring its private run-root boundary.')
                $alreadyRestored.Data['WO048ExactRollback'] = 'NOT_REQUIRED_ALREADY_ABSENT'
                throw $alreadyRestored
            }
            try {
                $runId = Get-WO048ExactAddedCanonicalRunId `
                    -Before $BeforeRunRoots -After $currentRunRoots
                & $RollbackAuthorityAction -RunId $runId
                $rollbackAuthorityEstablished = $true
            }
            catch {
                throw [System.InvalidOperationException]::new(
                    'WO-048 failed with no unambiguous owned rollback authority; artifacts are retained.')
            }
        }
        try {
            & $CleanupAction -RunId $runId
            if (-not [bool](& $RunRootAbsentAction -RunId $runId)) {
                throw 'The exact WO-048 run root remains after rollback.'
            }
        }
        catch {
            $retained = [System.InvalidOperationException]::new(
                'WO-048 exact rollback did not complete; private recovery authority is retained.')
            $retained.Data['WO048ExactRollback'] = 'FAILED_RETAINED'
            throw $retained
        }
        $rolledBack = [System.InvalidOperationException]::new(
            'WO-048 post-provisioning qualification failed after exact rollback.')
        $rolledBack.Data['WO048ExactRollback'] = 'PASS'
        $rolledBack.Data['WO048RolledBackRunId'] = $runId
        throw $rolledBack
    }
}

function Assert-WO048ProvisioningPublicOutput {
    param(
        [Parameter(Mandatory = $true)][string[]]$Lines,
        [Parameter(Mandatory = $true)][string]$ExpectedRunId
    )

    [void](Assert-WO048CanonicalLowercaseRunId -RunId $ExpectedRunId)
    $fixedMarkers = @(
        'WO048_PKI_ONLY_PROVISIONING=PASS',
        'WO048_CLIENT_IDENTITY_SELECTED=YES',
        'WO048_RECEIVER_IDENTITY_SELECTED=YES',
        'WO048_CLIENT_PRIVATE_KEY_EXPORTABLE=NO',
        'WO048_JAVA_25_PROVENANCE=PASS',
        'WO048_JAVA_SUNMSCAPI_STORE_QUALIFICATION=PASS',
        'WO048_PRIVATE_VALUES_DISPLAYED=NO',
        'WO048_DATABASES_STARTED=NO',
        'WO048_APPLICATIONS_STARTED=NO',
        'WO048_TLS_HANDSHAKES=0',
        'WO048_SOCKETS_OPENED=0',
        'WO048_PROVIDER_CALLS=0',
        'WO048_REMOTE_NETWORK_CALLS=0')
    if ($Lines.Count -ne ($fixedMarkers.Count + 2) -or
        @($Lines | Where-Object { $_.Length -gt 512 }).Count -ne 0) {
        throw 'WO-048 provisioning output has unexpected cardinality or length.'
    }
    foreach ($marker in $fixedMarkers) {
        if (@($Lines | Where-Object { $_ -ceq $marker }).Count -ne 1) {
            throw 'WO-048 provisioning output is missing one exact unique marker.'
        }
    }
    $runLines = @($Lines | Where-Object { $_.StartsWith(
        'WO048_RUN_ID=', [System.StringComparison]::Ordinal) })
    $hashLines = @($Lines | Where-Object { $_.StartsWith(
        'WO048_PRIVATE_IDENTITY_RECORD_SHA256=', [System.StringComparison]::Ordinal) })
    if ($runLines.Count -ne 1 -or $hashLines.Count -ne 1 -or
        $runLines[0] -cne "WO048_RUN_ID=$ExpectedRunId") {
        throw 'WO-048 provisioning output does not bind one exact derived RunId.'
    }
    $recordSha256 = $hashLines[0].Substring(
        'WO048_PRIVATE_IDENTITY_RECORD_SHA256='.Length)
    if ($recordSha256 -cnotmatch '^[0-9a-f]{64}$') {
        throw 'WO-048 provisioning output contains an invalid state commitment.'
    }
    $allowed = @($fixedMarkers) + @($runLines[0], $hashLines[0])
    if (@($Lines | Where-Object { $_ -cnotin $allowed }).Count -ne 0) {
        throw 'WO-048 provisioning output contains an unexpected line.'
    }
    return $recordSha256
}

function Assert-WO048PrivateAclContract {
    param(
        [Parameter(Mandatory = $true)][string]$ActualOwnerSid,
        [Parameter(Mandatory = $true)][string]$ExpectedOwnerSid,
        [Parameter(Mandatory = $true)][bool]$AreAccessRulesProtected,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Rules,
        [Parameter(Mandatory = $true)][ValidateSet('File', 'Directory')]
        [string]$PathKind,
        [switch]$RequireProtected
    )

    if ($ActualOwnerSid -cne $ExpectedOwnerSid -or
        ($RequireProtected -and -not $AreAccessRulesProtected)) {
        throw 'A WO-048 private path has an invalid owner or inheritance state.'
    }
    if ($Rules.Count -ne 1) {
        throw 'A WO-048 private path must have exactly one explicit owner access rule.'
    }
    $expectedInheritance = if ($PathKind -eq 'Directory') {
        [int]([System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
            [System.Security.AccessControl.InheritanceFlags]::ObjectInherit)
    }
    else {
        [int][System.Security.AccessControl.InheritanceFlags]::None
    }
    foreach ($rule in $Rules) {
        if ([string]$rule.IdentitySid -cne $ExpectedOwnerSid -or
            [string]$rule.AccessControlType -cne 'Allow' -or
            [int]$rule.FileSystemRights -ne
                [int][System.Security.AccessControl.FileSystemRights]::FullControl -or
            [int]$rule.InheritanceFlags -ne $expectedInheritance -or
            [int]$rule.PropagationFlags -ne
                [int][System.Security.AccessControl.PropagationFlags]::None -or
            [bool]$rule.IsInherited) {
            throw 'A WO-048 private path grants access outside its exact owner.'
        }
    }
}

function Read-WO048StrictUtf8JsonObject {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateRange(1, 1048576)][int]$MaximumBytes = 1048576
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'The required WO-048 private JSON state is absent.'
    }
    Assert-WO048NotReparsePoint -Path $Path
    $file = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($file.Length -le 0 -or $file.Length -gt $MaximumBytes) {
        throw 'The WO-048 private JSON state has an invalid bounded size.'
    }
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    try {
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and
            $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            throw 'The WO-048 private JSON state must be UTF-8 without BOM.'
        }
        $json = [System.Text.UTF8Encoding]::new($false, $true).GetString($bytes)
        $value = $json | ConvertFrom-Json -DateKind String -ErrorAction Stop
        if ($value -isnot [pscustomobject]) {
            throw 'The WO-048 private JSON state root must be one object.'
        }
        return $value
    }
    catch {
        throw 'The WO-048 private JSON state is corrupt or not strict UTF-8 JSON.'
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Read-WO048StrictUtf8JsonObjectFromStream {
    param(
        [Parameter(Mandatory = $true)][System.IO.FileStream]$Stream,
        [ValidateRange(1, 1048576)][int]$MaximumBytes = 1048576
    )

    if (-not $Stream.CanRead -or -not $Stream.CanSeek -or
        $Stream.Length -le 0 -or $Stream.Length -gt $MaximumBytes -or
        $Stream.Length -gt [int]::MaxValue) {
        throw 'The WO-048 private JSON stream has an invalid bounded shape.'
    }
    $bytes = New-Object byte[] ([int]$Stream.Length)
    try {
        $Stream.Position = 0
        $offset = 0
        while ($offset -lt $bytes.Length) {
            $read = $Stream.Read($bytes, $offset, $bytes.Length - $offset)
            if ($read -le 0) {
                throw 'The WO-048 private JSON stream ended before its bounded length.'
            }
            $offset += $read
        }
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and
            $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            throw 'The WO-048 private JSON stream must be UTF-8 without BOM.'
        }
        $json = [System.Text.UTF8Encoding]::new($false, $true).GetString($bytes)
        $value = $json | ConvertFrom-Json -DateKind String -ErrorAction Stop
        if ($value -isnot [pscustomobject]) {
            throw 'The WO-048 private JSON stream root must be one object.'
        }
        return $value
    }
    catch {
        throw 'The WO-048 private JSON stream is corrupt or not strict UTF-8 JSON.'
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-WO048CertificateRecordSetShape {
    param(
        [Parameter(Mandatory = $true)][object[]]$Records,
        [Parameter(Mandatory = $true)][string]$CanonicalRunId,
        [switch]$RequireOwned
    )

    [void](Assert-WO048CanonicalLowercaseRunId -RunId $CanonicalRunId)
    if ($Records.Count -ne 2 -or
        @($Records | Where-Object {
            $_.role -ceq 'receiver-server-direct-trust'
        }).Count -ne 1 -or
        @($Records | Where-Object {
            $_.role -ceq 'sender-client'
        }).Count -ne 1) {
        throw 'The WO-048 certificate recovery set must contain exactly both roles.'
    }
    $ownedFingerprints = [System.Collections.Generic.List[string]]::new()
    $ownedHashes = [System.Collections.Generic.List[string]]::new()
    foreach ($record in $Records) {
        $expectedRecordProperties = @(
            'role', 'ownershipStatus', 'storeLocation', 'thumbprint',
            'sha256', 'subject', 'cngKey')
        $actualRecordProperties = @($record.PSObject.Properties |
            ForEach-Object { $_.Name })
        if ($actualRecordProperties.Count -ne $expectedRecordProperties.Count -or
            @($actualRecordProperties | Where-Object {
                $_ -cnotin $expectedRecordProperties
            }).Count -ne 0) {
            throw 'A WO-048 certificate recovery record property set is not exact.'
        }
        $isClient = $record.role -ceq 'sender-client'
        $expectedStore = if ($isClient) { 'CurrentUser\My' } else { 'CurrentUser\Root' }
        $expectedSubject = if ($isClient) {
            "CN=WO046 sender $CanonicalRunId, OU=WO-046, O=Betting Project Local Qualification"
        }
        else {
            'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
        }
        $allowedStatus = if ($isClient) {
            @('NOT_CREATED', 'CERTIFICATE_DECLARED', 'OWNED')
        }
        else {
            @('NOT_CREATED', 'DECLARED_BEFORE_IMPORT', 'OWNED')
        }
        if ($record.storeLocation -cne $expectedStore -or
            $record.subject -cne $expectedSubject -or
            $allowedStatus -cnotcontains $record.ownershipStatus -or
            ($RequireOwned -and $record.ownershipStatus -cne 'OWNED')) {
            throw 'A WO-048 certificate recovery record violates its exact role contract.'
        }
        if ($record.ownershipStatus -ceq 'NOT_CREATED') {
            if ($null -ne $record.thumbprint -or $null -ne $record.sha256 -or
                $null -ne $record.cngKey) {
                throw 'A WO-048 NOT_CREATED record contains ownership material.'
            }
            continue
        }
        if ([string]$record.thumbprint -cnotmatch '^[0-9A-F]{40}$' -or
            [string]$record.sha256 -cnotmatch '^[0-9a-f]{64}$') {
            throw 'A WO-048 owned certificate identity is incomplete.'
        }
        $ownedFingerprints.Add([string]$record.thumbprint)
        $ownedHashes.Add([string]$record.sha256)
        if (($isClient -and $record.ownershipStatus -ceq 'CERTIFICATE_DECLARED') -or
            -not $isClient) {
            if ($null -ne $record.cngKey) {
                throw 'A WO-048 non-CNG recovery role unexpectedly owns CNG material.'
            }
        }
    }
    if (@($ownedFingerprints | Sort-Object -Unique).Count -ne $ownedFingerprints.Count -or
        @($ownedHashes | Sort-Object -Unique).Count -ne $ownedHashes.Count) {
        throw 'WO-048 refuses non-unique certificate fingerprints or hashes.'
    }
}

function Assert-WO048PrivateStateShape {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$ExpectedWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedProvisioningWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedRunId,
        [Parameter(Mandatory = $true)][string]$ExpectedOwnerSid
    )

    if ($State.schemaVersion -ne 2 -or
        $State.format -cne 'WO046_LOCAL_MTLS_IDENTITY_V2' -or
        $State.workOrder -cne $ExpectedWorkOrder -or
        $State.provisioningWorkOrder -cne $ExpectedProvisioningWorkOrder -or
        $State.runId -cne $ExpectedRunId -or $State.ownerSid -cne $ExpectedOwnerSid -or
        @('IN_PROGRESS', 'PASS') -cnotcontains $State.provisioningStatus -or
        @('NOT_STARTED', 'REMOVING_CERTIFICATES',
            'CERTIFICATES_REMOVED', 'REMOVING_PRIVATE_FILES',
            'PRIVATE_FILES_REMOVED', 'FINALIZING_METADATA') -cnotcontains
                $State.cleanupStatus -or
        $State.receiverOrigin -cne 'https://127.0.0.1:8444' -or
        $State.maximumCertificateValidityDays -ne 7 -or
        $State.javaRuntimeQualification -cne 'PASS_JAVA_25_SANITIZED' -or
        @('NOT_STARTED', 'PASS_SUNMSCAPI_NO_HANDSHAKE') -cnotcontains
            $State.javaStoreQualification -or
        ($State.provisioningStatus -eq 'PASS' -and
            $State.javaStoreQualification -cne 'PASS_SUNMSCAPI_NO_HANDSHAKE') -or
        $State.privateValuesDisplayed -isnot [bool] -or $State.privateValuesDisplayed -or
        $State.databasesStarted -isnot [bool] -or $State.databasesStarted -or
        $State.applicationsStarted -isnot [bool] -or $State.applicationsStarted -or
        $State.tlsHandshakeAttempted -isnot [bool] -or $State.tlsHandshakeAttempted -or
        $State.socketsOpened -isnot [bool] -or $State.socketsOpened -or
        $State.providerNetworkTouched -isnot [bool] -or $State.providerNetworkTouched -or
        $State.remoteNetworkTouched -isnot [bool] -or $State.remoteNetworkTouched) {
        throw 'The WO-048 private identity record violates its strict lifecycle schema.'
    }
    Assert-WO048CertificateRecordSetShape `
        -Records @($State.ownedCertificates) `
        -CanonicalRunId $ExpectedRunId `
        -RequireOwned:($State.provisioningStatus -ceq 'PASS')
}

function Assert-WO048CleanupRecoveryAuthorityShape {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$ExpectedWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedProvisioningWorkOrder,
        [Parameter(Mandatory = $true)][string]$ExpectedRunId,
        [Parameter(Mandatory = $true)][string]$ExpectedOwnerSid,
        [Parameter(Mandatory = $true)][string]$ExpectedRunRoot,
        [Parameter(Mandatory = $true)][string]$ExpectedMarkerPath,
        [Parameter(Mandatory = $true)][string]$ExpectedLockPath
    )

    Assert-WO048PrivateStateShape `
        -State $State `
        -ExpectedWorkOrder $ExpectedWorkOrder `
        -ExpectedProvisioningWorkOrder $ExpectedProvisioningWorkOrder `
        -ExpectedRunId $ExpectedRunId `
        -ExpectedOwnerSid $ExpectedOwnerSid
    Assert-WO048ExactPath -Actual ([string]$State.runRoot) -Expected $ExpectedRunRoot `
        -Failure 'The WO-048 cleanup authority run root changed.'
    Assert-WO048ExactPath -Actual ([string]$State.markerPath) -Expected $ExpectedMarkerPath `
        -Failure 'The WO-048 cleanup authority marker path changed.'
    Assert-WO048ExactPath -Actual ([string]$State.lockPath) -Expected $ExpectedLockPath `
        -Failure 'The WO-048 cleanup authority lock path changed.'
}

function Assert-WO048ClientPrivateKeyMetadata {
    param(
        [Parameter(Mandatory = $true)][string]$KeyImplementation,
        [Parameter(Mandatory = $true)][string]$ProviderName,
        [Parameter(Mandatory = $true)][string]$AlgorithmGroup,
        [Parameter(Mandatory = $true)][int]$KeySize,
        [Parameter(Mandatory = $true)][int]$ExportPolicy,
        [Parameter(Mandatory = $true)][int]$KeyUsage,
        [Parameter(Mandatory = $true)][bool]$IsEphemeral
    )

    if ($KeyImplementation -cne 'System.Security.Cryptography.RSACng' -or
        $ProviderName -cne 'Microsoft Software Key Storage Provider' -or
        $AlgorithmGroup -cne 'RSA' -or $KeySize -ne 3072 -or
        $ExportPolicy -ne [int][System.Security.Cryptography.CngExportPolicies]::None -or
        $KeyUsage -ne [int][System.Security.Cryptography.CngKeyUsages]::Signing -or
        $IsEphemeral) {
        throw 'The WO-048 client private-key metadata is not exact non-exportable CNG signing material.'
    }
}

function Assert-WO048ExactCertificateCollection {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2Collection]$Certificates,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [Parameter(Mandatory = $true)][bool]$ExpectedPrivateKey
    )

    if ($ExpectedSha256 -cnotmatch '^[0-9a-f]{64}$' -or
        $Certificates.Count -ne 1 -or
        $Certificates[0].HasPrivateKey -ne $ExpectedPrivateKey -or
        (Get-WO048CertificateSha256 -Certificate $Certificates[0]) -cne $ExpectedSha256) {
        throw 'A WO-048 PKCS12 collection does not contain its one exact certificate role.'
    }
}

function Assert-WO048ImmediateGuidChild {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Child,
        [Parameter(Mandatory = $true)][guid]$RunId
    )

    $expected = [System.IO.Path]::GetFullPath(
        (Join-Path $Parent $RunId.ToString('D')))
    Assert-WO048ExactPath -Actual $Child -Expected $expected `
        -Failure 'The WO-048 private root is not the exact GUID child of its campaign base.'
    Assert-WO048ExactPath -Actual (Split-Path -Parent $Child) -Expected $Parent `
        -Failure 'The WO-048 private root escaped its exact campaign base.'
}

function Get-WO048CurrentOwnerSid {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw 'WO-048 PKI operations are supported only on Windows.'
    }
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    if ($null -eq $identity -or $null -eq $identity.User) {
        throw 'WO-048 cannot resolve the current Windows user SID.'
    }
    return $identity.User
}

function Resolve-WO048CampaignBase {
    param([switch]$Create)

    $localApplicationData = [Environment]::GetFolderPath(
        [Environment+SpecialFolder]::LocalApplicationData)
    if ([string]::IsNullOrWhiteSpace($localApplicationData)) {
        throw 'WO-048 cannot resolve LocalApplicationData.'
    }
    $canonicalLocalRoot = Assert-WO048LocalFixedPathPrefix `
        -Path $localApplicationData -RequireExistingType Directory

    $current = $canonicalLocalRoot
    foreach ($segment in @(
            'SofaScoreLocalLab',
            'qualifications',
            $script:Wo048CampaignName)) {
        $candidate = [System.IO.Path]::GetFullPath((Join-Path $current $segment))
        Assert-WO048ExactPath -Actual (Split-Path -Parent $candidate) -Expected $current `
            -Failure 'WO-048 campaign-base construction escaped its exact parent.'
        if (-not (Test-Path -LiteralPath $candidate)) {
            if (-not $Create) {
                throw 'The WO-048 campaign base does not exist.'
            }
            New-Item -ItemType Directory -Path $candidate -ErrorAction Stop | Out-Null
        }
        if (-not (Test-Path -LiteralPath $candidate -PathType Container)) {
            throw 'A WO-048 campaign-base component is not a directory.'
        }
        Assert-WO048NotReparsePoint -Path $candidate
        $current = (Get-Item -LiteralPath $candidate -Force).FullName
    }
    return [System.IO.Path]::GetFullPath($current)
}

function Assert-WO048PrivateAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerSid,
        [switch]$RequireProtected
    )

    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    $acl = Get-Acl -LiteralPath $item.FullName -ErrorAction Stop
    $actualOwnerSid = ([System.Security.Principal.NTAccount]$acl.Owner).Translate(
        [System.Security.Principal.SecurityIdentifier]).Value
    $rules = @(foreach ($rule in $acl.Access) {
        [pscustomobject]@{
            IdentitySid = $rule.IdentityReference.Translate(
                [System.Security.Principal.SecurityIdentifier]).Value
            AccessControlType = $rule.AccessControlType.ToString()
            FileSystemRights = [int]$rule.FileSystemRights
            InheritanceFlags = [int]$rule.InheritanceFlags
            PropagationFlags = [int]$rule.PropagationFlags
            IsInherited = [bool]$rule.IsInherited
        }
    })
    Assert-WO048PrivateAclContract `
        -ActualOwnerSid $actualOwnerSid `
        -ExpectedOwnerSid $OwnerSid `
        -AreAccessRulesProtected $acl.AreAccessRulesProtected `
        -Rules $rules `
        -PathKind $(if ($item.PSIsContainer) { 'Directory' } else { 'File' }) `
        -RequireProtected:$RequireProtected
}

function Protect-WO048PrivateDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    Assert-WO048NotReparsePoint -Path $Path
    $acl = [System.Security.AccessControl.DirectorySecurity]::new()
    $acl.SetOwner($OwnerSid)
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $OwnerSid,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        $inheritance,
        [System.Security.AccessControl.PropagationFlags]::None,
        [System.Security.AccessControl.AccessControlType]::Allow)
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl -ErrorAction Stop
    Assert-WO048PrivateAcl -Path $Path -OwnerSid $OwnerSid.Value -RequireProtected
}

function Protect-WO048PrivateFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    Assert-WO048NotReparsePoint -Path $Path
    $acl = [System.Security.AccessControl.FileSecurity]::new()
    $acl.SetOwner($OwnerSid)
    $acl.SetAccessRuleProtection($true, $false)
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $OwnerSid,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        [System.Security.AccessControl.AccessControlType]::Allow)
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl -ErrorAction Stop
    Assert-WO048PrivateAcl -Path $Path -OwnerSid $OwnerSid.Value -RequireProtected
}

function Write-WO048PrivateJsonAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    $parent = Split-Path -Parent ([System.IO.Path]::GetFullPath($Path))
    Assert-WO048NotReparsePoint -Path $parent
    Assert-WO048PrivateAcl -Path $parent -OwnerSid $OwnerSid.Value -RequireProtected
    $partialPath = "$Path.partial"
    if (Test-Path -LiteralPath $partialPath) {
        throw 'A residual WO-048 private-state partial blocks mutation.'
    }
    $bytes = [System.Text.UTF8Encoding]::new($false, $true).GetBytes(
        (($Value | ConvertTo-Json -Depth 14) + [Environment]::NewLine))
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
        Protect-WO048PrivateFile -Path $partialPath -OwnerSid $OwnerSid
        if (Test-Path -LiteralPath $Path -PathType Leaf) {
            Assert-WO048NotReparsePoint -Path $Path
            [System.IO.File]::Move($partialPath, $Path, $true)
        }
        else {
            Move-Item -LiteralPath $partialPath -Destination $Path -ErrorAction Stop
        }
        Assert-WO048NotReparsePoint -Path $Path
        Assert-WO048PrivateAcl -Path $Path -OwnerSid $OwnerSid.Value -RequireProtected
    }
    catch {
        if (Test-Path -LiteralPath $partialPath -PathType Leaf) {
            Remove-Item -LiteralPath $partialPath -Force
        }
        throw 'The WO-048 private-state transition could not be persisted atomically.'
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Get-WO048SingleExtension {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate,
        [Parameter(Mandatory = $true)][string]$Oid,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $extensions = @($Certificate.Extensions | Where-Object { $_.Oid.Value -eq $Oid })
    if ($extensions.Count -ne 1) {
        throw "The WO-048 $Description extension is not unique."
    }
    return $extensions[0]
}

function Assert-WO048Rsa3072Sha256Signature {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    if ($Certificate.SignatureAlgorithm.Value -ne $script:Wo048Sha256WithRsaOid) {
        throw 'The WO-048 certificate signature algorithm is not SHA256withRSA.'
    }
    $rsa = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPublicKey(
        $Certificate)
    try {
        if ($null -eq $rsa -or $rsa.KeySize -ne 3072) {
            throw 'The WO-048 certificate public key is not RSA 3072.'
        }
    }
    finally {
        if ($null -ne $rsa) {
            $rsa.Dispose()
        }
    }
}

function Assert-WO048CertificateValidity {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate,
        [Parameter(Mandatory = $true)][DateTimeOffset]$ObservedAtUtc
    )

    $notBefore = [DateTimeOffset]::new($Certificate.NotBefore.ToUniversalTime())
    $notAfter = [DateTimeOffset]::new($Certificate.NotAfter.ToUniversalTime())
    if ($notBefore -gt $ObservedAtUtc -or
        $notAfter -le $ObservedAtUtc -or
        ($notAfter - $notBefore) -gt [TimeSpan]::FromDays(7)) {
        throw 'The WO-048 certificate validity is not current and bounded to seven days.'
    }
}

function Assert-WO048ClientCertificateProfile {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate,
        [Parameter(Mandatory = $true)][string]$ExpectedSubject,
        [Parameter(Mandatory = $true)][DateTimeOffset]$ObservedAtUtc,
        [switch]$RequirePrivateKey
    )

    if ($Certificate.Subject -cne $ExpectedSubject -or
        $Certificate.Issuer -cne $Certificate.Subject) {
        throw 'The WO-048 client certificate subject or self-issuer is not exact.'
    }
    if ($RequirePrivateKey -and -not $Certificate.HasPrivateKey) {
        throw 'The WO-048 client certificate has no private key.'
    }
    Assert-WO048Rsa3072Sha256Signature -Certificate $Certificate
    Assert-WO048CertificateValidity -Certificate $Certificate -ObservedAtUtc $ObservedAtUtc

    $basic = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.19' `
        -Description 'client basic-constraints'
    $parsedBasic = if ($basic -is
        [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]) {
        $basic
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new(
            $basic.RawData, $basic.Critical)
    }
    if (-not $parsedBasic.Critical -or $parsedBasic.CertificateAuthority) {
        throw 'The WO-048 client certificate is not critical CA=false.'
    }

    $keyUsage = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.15' `
        -Description 'client key-usage'
    $parsedKeyUsage = if ($keyUsage -is
        [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]) {
        $keyUsage
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            $keyUsage.RawData, $keyUsage.Critical)
    }
    if ($parsedKeyUsage.KeyUsages -ne
        [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature) {
        throw 'The WO-048 client certificate key usage is not exact digitalSignature.'
    }

    $eku = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.37' `
        -Description 'client enhanced-key-usage'
    $parsedEku = if ($eku -is
        [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]) {
        $eku
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new(
            $eku.RawData, $eku.Critical)
    }
    $ekuOids = @($parsedEku.EnhancedKeyUsages | ForEach-Object { $_.Value })
    if (-not $parsedEku.Critical -or $ekuOids.Count -ne 1 -or
        $ekuOids[0] -ne $script:Wo048ClientAuthOid) {
        throw 'The WO-048 client certificate EKU is not exact critical clientAuth.'
    }
    if (@($Certificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.17' }).Count -ne 0) {
        throw 'The WO-048 client certificate must not contain a subject alternative name.'
    }
}

function Assert-WO048ClientPrivateKeyProfile {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $privateKey = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey(
        $Certificate)
    try {
        if ($privateKey -isnot [System.Security.Cryptography.RSACng]) {
            throw 'The WO-048 client private key is not Windows CNG RSA.'
        }
        Assert-WO048ClientPrivateKeyMetadata `
            -KeyImplementation $privateKey.GetType().FullName `
            -ProviderName $privateKey.Key.Provider.Provider `
            -AlgorithmGroup $privateKey.Key.AlgorithmGroup.AlgorithmGroup `
            -KeySize $privateKey.KeySize `
            -ExportPolicy ([int]$privateKey.Key.ExportPolicy) `
            -KeyUsage ([int]$privateKey.Key.KeyUsage) `
            -IsEphemeral $privateKey.Key.IsEphemeral
    }
    finally {
        if ($null -ne $privateKey) {
            $privateKey.Dispose()
        }
    }
}

function Get-WO048ClientCngKeyIdentity {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $privateKey = [System.Security.Cryptography.X509Certificates.RSACertificateExtensions]::GetRSAPrivateKey(
        $Certificate)
    try {
        if ($privateKey -isnot [System.Security.Cryptography.RSACng]) {
            throw 'The WO-048 client private key is not backed by Windows CNG.'
        }
        $key = $privateKey.Key
        $providerName = $key.Provider.Provider
        $keyName = $key.KeyName
        $uniqueName = $key.UniqueName
        if ($providerName -cne 'Microsoft Software Key Storage Provider' -or
            [string]::IsNullOrWhiteSpace($keyName) -or
            [string]::IsNullOrWhiteSpace($uniqueName) -or
            $uniqueName -notmatch '^[A-Za-z0-9._-]+$' -or
            $key.IsEphemeral -or
            $key.AlgorithmGroup.AlgorithmGroup -cne 'RSA' -or
            $key.KeySize -ne 3072 -or
            $key.ExportPolicy -ne [System.Security.Cryptography.CngExportPolicies]::None -or
            $key.KeyUsage -ne [System.Security.Cryptography.CngKeyUsages]::Signing) {
            throw 'The WO-048 CNG key-container identity is not exact and non-exportable.'
        }

        $roamingApplicationData = [Environment]::GetFolderPath(
            [Environment+SpecialFolder]::ApplicationData)
        if ([string]::IsNullOrWhiteSpace($roamingApplicationData)) {
            throw 'WO-048 cannot resolve the current-user CNG key root.'
        }
        $canonicalRoamingRoot = Assert-WO048LocalFixedPathPrefix `
            -Path $roamingApplicationData -RequireExistingType Directory
        $keyRoot = Assert-WO048LocalFixedPathPrefix `
            -Path (Join-Path $canonicalRoamingRoot 'Microsoft\Crypto\Keys') `
            -RequireExistingType Directory
        $keyFilePath = [System.IO.Path]::GetFullPath((Join-Path $keyRoot $uniqueName))
        Assert-WO048ExactPath -Actual (Split-Path -Parent $keyFilePath) -Expected $keyRoot `
            -Failure 'The WO-048 CNG key file escaped its exact current-user key root.'
        $keyFilePath = Assert-WO048LocalFixedPathPrefix `
            -Path $keyFilePath -RequireExistingType File
        $keyFile = Get-Item -LiteralPath $keyFilePath -Force -ErrorAction Stop
        return [pscustomobject]@{
            providerName = $providerName
            keyName = $keyName
            uniqueName = $uniqueName
            keyFilePath = $keyFilePath
            keyFileLength = [long]$keyFile.Length
            keyFileCreationTimeUtc = $keyFile.CreationTimeUtc.ToString('O')
            algorithmGroup = 'RSA'
            keySize = 3072
            exportPolicy = 'None'
            keyUsage = 'Signing'
            isEphemeral = $false
        }
    }
    finally {
        if ($null -ne $privateKey) {
            $privateKey.Dispose()
        }
    }
}

function Set-WO048ClientCertificateDeclaration {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate,
        [Parameter(Mandatory = $true)][string]$ExpectedSubject
    )

    # Keep this transition independent of private-key inspection. It creates
    # enough exact authority to find the certificate again if the subsequent
    # CNG query fails, while deliberately leaving cngKey unset.
    $thumbprint = $Certificate.Thumbprint.ToUpperInvariant()
    $sha256 = Get-WO048CertificateSha256 -Certificate $Certificate
    $subject = $Certificate.Subject
    if ($thumbprint -notmatch '^[0-9A-F]{40}$' -or
        $sha256 -notmatch '^[0-9a-f]{64}$' -or
        $subject -cne $ExpectedSubject) {
        throw 'The WO-048 client certificate recovery declaration is not exact.'
    }
    $clientRecords = @($State.ownedCertificates | Where-Object {
        $_.role -ceq 'sender-client'
    })
    if ($clientRecords.Count -ne 1 -or
        $clientRecords[0].storeLocation -cne 'CurrentUser\My' -or
        $clientRecords[0].ownershipStatus -notin @(
            'NOT_CREATED', 'CERTIFICATE_DECLARED')) {
        throw 'The WO-048 client recovery record cardinality or state is not exact.'
    }
    $clientRecord = $clientRecords[0]
    if ($clientRecord.ownershipStatus -eq 'NOT_CREATED') {
        if ($null -ne $clientRecord.thumbprint -or $null -ne $clientRecord.sha256 -or
            $null -ne $clientRecord.cngKey -or
            $clientRecord.subject -cne $ExpectedSubject) {
            throw 'The WO-048 uncreated client recovery slot contains unexpected material.'
        }
        $clientRecord.ownershipStatus = 'CERTIFICATE_DECLARED'
        $clientRecord.thumbprint = $thumbprint
        $clientRecord.sha256 = $sha256
        $clientRecord.subject = $subject
        $clientRecord.cngKey = $null
    }
    elseif ($clientRecord.thumbprint -cne $thumbprint -or
        $clientRecord.sha256 -cne $sha256 -or
        $clientRecord.subject -cne $subject -or
        $null -ne $clientRecord.cngKey) {
        throw 'The existing WO-048 client recovery declaration changed identity.'
    }
    return $clientRecord
}

function Assert-WO048ServerCertificateProfile {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate,
        [Parameter(Mandatory = $true)][DateTimeOffset]$ObservedAtUtc,
        [switch]$AllowPrivateKey
    )

    $expectedSubject = 'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
    if ($Certificate.Subject -cne $expectedSubject -or
        $Certificate.Issuer -cne $Certificate.Subject) {
        throw 'The WO-048 receiver certificate subject or self-issuer is not exact.'
    }
    if (-not $AllowPrivateKey -and $Certificate.HasPrivateKey) {
        throw 'The WO-048 public receiver certificate unexpectedly contains a private key.'
    }
    Assert-WO048Rsa3072Sha256Signature -Certificate $Certificate
    Assert-WO048CertificateValidity -Certificate $Certificate -ObservedAtUtc $ObservedAtUtc

    $basic = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.19' `
        -Description 'server basic-constraints'
    $parsedBasic = if ($basic -is
        [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]) {
        $basic
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new(
            $basic.RawData, $basic.Critical)
    }
    if ($parsedBasic.CertificateAuthority) {
        throw 'The WO-048 receiver certificate must be CA=false.'
    }

    $keyUsage = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.15' `
        -Description 'server key-usage'
    $parsedKeyUsage = if ($keyUsage -is
        [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]) {
        $keyUsage
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            $keyUsage.RawData, $keyUsage.Critical)
    }
    $expectedUsage =
        [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor
        [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment
    if ($parsedKeyUsage.KeyUsages -ne $expectedUsage) {
        throw 'The WO-048 receiver certificate key usage is not exact.'
    }

    $eku = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.37' `
        -Description 'server enhanced-key-usage'
    $parsedEku = if ($eku -is
        [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]) {
        $eku
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new(
            $eku.RawData, $eku.Critical)
    }
    $ekuOids = @($parsedEku.EnhancedKeyUsages | ForEach-Object { $_.Value })
    if ($ekuOids.Count -ne 1 -or $ekuOids[0] -ne $script:Wo048ServerAuthOid) {
        throw 'The WO-048 receiver certificate EKU is not exact serverAuth.'
    }

    $san = Get-WO048SingleExtension -Certificate $Certificate -Oid '2.5.29.17' `
        -Description 'server subject-alternative-name'
    $parsedSan = if ($san -is
        [System.Security.Cryptography.X509Certificates.X509SubjectAlternativeNameExtension]) {
        $san
    }
    else {
        [System.Security.Cryptography.X509Certificates.X509SubjectAlternativeNameExtension]::new(
            $san.RawData, $san.Critical)
    }
    $expectedSanBuilder =
        [System.Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
    $expectedSanBuilder.AddIpAddress([System.Net.IPAddress]::Loopback)
    $expectedSan = $expectedSanBuilder.Build()
    if ([Convert]::ToBase64String($parsedSan.RawData) -cne
        [Convert]::ToBase64String($expectedSan.RawData)) {
        throw 'The WO-048 receiver certificate SAN is not exact IP 127.0.0.1 only.'
    }
}

Export-ModuleMember -Function @(
    'ConvertTo-WO048LowerHex',
    'Get-WO048Sha256Text',
    'Get-WO048StateIdentityBinding',
    'New-WO048FinalizationLockAuthority',
    'Read-WO048FinalizationLockJournalFromStream',
    'Write-WO048FinalizationLockAuthorityToStream',
    'Assert-WO048FinalizationLockAuthority',
    'Assert-WO048FinalizationMarkerAuthority',
    'Get-WO048FinalizationRecoveryPhase',
    'Invoke-WO048SanitizedCleanupFailureBoundary',
    'Get-WO048FileSha256',
    'Get-WO048CertificateSha256',
    'New-WO048RandomSecret',
    'Write-WO048Utf8NoBomFile',
    'Write-WO048PrivateJsonAtomic',
    'Assert-WO048NotReparsePoint',
    'Assert-WO048NoDescendantReparsePoint',
    'Assert-WO048ExactPath',
    'Assert-WO048LocalFixedPathPrefix',
    'Resolve-WO048ExactJava25Toolchain',
    'Assert-WO048ImmediateGuidChild',
    'New-WO048ExclusiveRunRoot',
    'Assert-WO048CanonicalLowercaseRunId',
    'Get-WO048ExactAddedCanonicalRunId',
    'Invoke-WO048GuardedPostProvisioningPhase',
    'Assert-WO048ProvisioningPublicOutput',
    'Assert-WO048PrivateAclContract',
    'Read-WO048StrictUtf8JsonObject',
    'Read-WO048StrictUtf8JsonObjectFromStream',
    'Assert-WO048CertificateRecordSetShape',
    'Assert-WO048PrivateStateShape',
    'Assert-WO048CleanupRecoveryAuthorityShape',
    'Assert-WO048ExactCertificateCollection',
    'Get-WO048CurrentOwnerSid',
    'Resolve-WO048CampaignBase',
    'Assert-WO048PrivateAcl',
    'Protect-WO048PrivateDirectory',
    'Protect-WO048PrivateFile',
    'Assert-WO048ClientCertificateProfile',
    'Assert-WO048ClientPrivateKeyProfile',
    'Assert-WO048ClientPrivateKeyMetadata',
    'Get-WO048ClientCngKeyIdentity',
    'Set-WO048ClientCertificateDeclaration',
    'Assert-WO048ServerCertificateProfile'
)
