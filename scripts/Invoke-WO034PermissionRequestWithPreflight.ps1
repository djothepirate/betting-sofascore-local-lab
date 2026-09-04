[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [string]$PrivateRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the WO-034 orchestrator.'
}

$expectedPreflightSha256 = '36cdc6e766a61a451570a934d4b51b97d6c62aab605e76aeb1d3170aac33d647'
$expectedLauncherSha256 = '2398de4e1a9179f6bd79913876c64256de014c48ed0615a0c3709851f3e37094'

function Get-WO034OrchestratorSha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    return [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Test-WO034OrchestratorBytesEqual {
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

function Read-WO034OrchestratorLockedBytes {
    param(
        [Parameter(Mandatory = $true)][IO.FileStream]$Stream,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not $Stream.CanRead -or $Stream.CanWrite -or
        $Stream.Length -lt 1 -or $Stream.Length -gt 1048576) {
        throw "$Description is outside the orchestrator byte contract."
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

function Open-WO034OrchestratorReadLock {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    try {
        return [IO.File]::Open(
            [IO.Path]::GetFullPath($Path), [IO.FileMode]::Open,
            [IO.FileAccess]::Read, [IO.FileShare]::Read)
    } catch [IO.IOException] {
        throw "WO-034 could not acquire the required $Description read lock."
    }
}

function Invoke-WO034OrchestratorChildProcess {
    param(
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][ValidateRange(1, 600)]
        [int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = [Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = (Get-Command pwsh -ErrorAction Stop).Source
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-File',
            $ScriptPath) + $Arguments) {
        [void]$process.StartInfo.ArgumentList.Add($argument)
    }
    try {
        if (-not $process.Start()) {
            throw "$Description did not start."
        }
        $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try {
                $process.Kill($true)
            } catch {
                # The fail-closed timeout result is authoritative even if the child already exited.
            }
            if (-not $process.WaitForExit(10000)) {
                throw "$Description termination could not be confirmed after its timeout."
            }
            [void]$standardOutputTask.GetAwaiter().GetResult()
            [void]$standardErrorTask.GetAwaiter().GetResult()
            throw "$Description exceeded its bounded timeout."
        }
        $standardOutput = $standardOutputTask.GetAwaiter().GetResult()
        $standardError = $standardErrorTask.GetAwaiter().GetResult()
        if ($standardOutput.Length -gt 65536 -or $standardError.Length -gt 65536) {
            throw "$Description exceeded its bounded diagnostic output."
        }
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            StandardOutput = $standardOutput
            StandardError = $standardError
        }
    } finally {
        $process.Dispose()
    }
}

function ConvertFrom-WO034OrchestratorStatusOutput {
    param(
        [Parameter(Mandatory = $true)][string]$StandardOutput,
        [Parameter(Mandatory = $true)][string]$Description
    )

    $values = @{}
    foreach ($line in $StandardOutput -split "`r?`n") {
        if ([string]::IsNullOrEmpty($line)) {
            continue
        }
        if ($line -cnotmatch '^[A-Z][A-Z0-9_]*=[A-Za-z0-9,._:/-]*$') {
            throw "$Description emitted a value outside the expurgated status contract."
        }
        $separatorIndex = $line.IndexOf('=')
        $key = $line.Substring(0, $separatorIndex)
        if ($values.ContainsKey($key)) {
            throw "$Description emitted a duplicate status key."
        }
        $values[$key] = $line.Substring($separatorIndex + 1)
    }
    return $values
}

function Assert-WO034OrchestratorExpectedStatuses {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Values,
        [Parameter(Mandatory = $true)][hashtable]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    foreach ($entry in $Expected.GetEnumerator()) {
        if (-not $Values.ContainsKey($entry.Key) -or
            $Values[$entry.Key] -cne $entry.Value) {
            throw "$Description failed its expurgated status contract."
        }
    }
}

function Invoke-WO034WithOwnerInputReadLock {
    param(
        [Parameter(Mandatory = $true)][string]$LockedInputPath,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    $inputStream = Open-WO034OrchestratorReadLock -Path $LockedInputPath `
        -Description 'owner input'
    $actionResult = $null
    $actionFailure = $null
    try {
        $beforeBytes = Read-WO034OrchestratorLockedBytes -Stream $inputStream `
            -Description 'WO-034 owner input'
        $beforeSha256 = Get-WO034OrchestratorSha256 -Bytes $beforeBytes
        try {
            $actionResult = & $Action
        } catch {
            $actionFailure = $_
        }
        $afterBytes = Read-WO034OrchestratorLockedBytes -Stream $inputStream `
            -Description 'WO-034 owner input'
        $afterSha256 = Get-WO034OrchestratorSha256 -Bytes $afterBytes
        if ($beforeSha256 -cne $afterSha256 -or
            -not (Test-WO034OrchestratorBytesEqual -Left $beforeBytes -Right $afterBytes)) {
            throw 'The locked WO-034 owner input changed during orchestration.'
        }
        if ($null -ne $actionFailure) {
            throw $actionFailure
        }
        return $actionResult
    } finally {
        $inputStream.Dispose()
    }
}

function Invoke-WO034PermissionRequestOrchestration {
    param(
        [Parameter(Mandatory = $true)][string]$OwnerInputPath,
        [Parameter(Mandatory = $true)][string]$RenderedOutputDirectory,
        [Parameter(Mandatory = $true)][string]$SourceRepositoryRoot,
        [Parameter(Mandatory = $true)][string]$ProtectedPrivateRoot
    )

    $repositoryRoot = [IO.Path]::GetFullPath($SourceRepositoryRoot)
    $privateRoot = [IO.Path]::GetFullPath($ProtectedPrivateRoot)
    $inputPath = [IO.Path]::GetFullPath($OwnerInputPath)
    $outputDirectory = [IO.Path]::GetFullPath($RenderedOutputDirectory)
    $expectedInputPath = [IO.Path]::GetFullPath((Join-Path $privateRoot `
        'owner-input.properties'))
    if ($inputPath -cne $expectedInputPath -or $outputDirectory -cne $privateRoot) {
        throw 'The orchestrator accepts only the canonical WO-034 owner-final paths.'
    }
    if (-not (Test-Path -LiteralPath $repositoryRoot -PathType Container) -or
        -not (Test-Path -LiteralPath $privateRoot -PathType Container) -or
        -not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
        throw 'The WO-034 orchestrator roots and owner input must already exist.'
    }

    $preflightPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
        'scripts\Test-WO034PermissionRequestInput.ps1'))
    $sourceLauncherPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
        'scripts\Invoke-WO034PermissionRequest.ps1'))
    $runtimeLauncherPath = [IO.Path]::GetFullPath((Join-Path $privateRoot `
        'qualified-runtime\Invoke-WO034PermissionRequest.ps1'))

    $preflightStream = $null
    $sourceLauncherStream = $null
    $runtimeLauncherStream = $null
    try {
        $preflightStream = Open-WO034OrchestratorReadLock -Path $preflightPath `
            -Description 'versioned preflight'
        $sourceLauncherStream = Open-WO034OrchestratorReadLock -Path $sourceLauncherPath `
            -Description 'versioned launcher'
        $runtimeLauncherStream = Open-WO034OrchestratorReadLock -Path $runtimeLauncherPath `
            -Description 'qualified launcher'

        $preflightBytes = Read-WO034OrchestratorLockedBytes -Stream $preflightStream `
            -Description 'WO-034 versioned preflight'
        $sourceLauncherBytes = Read-WO034OrchestratorLockedBytes `
            -Stream $sourceLauncherStream -Description 'WO-034 versioned launcher'
        $runtimeLauncherBytes = Read-WO034OrchestratorLockedBytes `
            -Stream $runtimeLauncherStream -Description 'WO-034 qualified launcher'
        if ((Get-WO034OrchestratorSha256 -Bytes $preflightBytes) -cne
            $expectedPreflightSha256) {
            throw 'The locked WO-034 preflight fingerprint is outside the orchestrator contract.'
        }
        if ((Get-WO034OrchestratorSha256 -Bytes $sourceLauncherBytes) -cne
            $expectedLauncherSha256 -or
            (Get-WO034OrchestratorSha256 -Bytes $runtimeLauncherBytes) -cne
            $expectedLauncherSha256 -or
            -not (Test-WO034OrchestratorBytesEqual -Left $sourceLauncherBytes `
                -Right $runtimeLauncherBytes)) {
            throw 'The locked WO-034 launcher copies are outside the orchestrator contract.'
        }

        $renderOutput = Invoke-WO034WithOwnerInputReadLock -LockedInputPath $inputPath `
            -Action {
            $preflightResult = Invoke-WO034OrchestratorChildProcess `
                -ScriptPath $preflightPath -Arguments @('-InputPath', $inputPath) `
                -TimeoutSeconds 60 -Description 'WO-034 preflight'
            if ($preflightResult.ExitCode -ne 0 -or
                -not [string]::IsNullOrEmpty($preflightResult.StandardError)) {
                throw 'The WO-034 preflight rejected the owner input.'
            }
            $preflightValues = ConvertFrom-WO034OrchestratorStatusOutput `
                -StandardOutput $preflightResult.StandardOutput `
                -Description 'WO-034 preflight'
            Assert-WO034OrchestratorExpectedStatuses -Values $preflightValues -Expected @{
                WO034_INPUT_PREFLIGHT_STATUS = 'PASS'
                FROZEN_LAUNCHER_SHA256_MATCH = 'YES'
                FROZEN_RENDERER_SHA256_MATCH = 'YES'
                FROZEN_TEMPLATE_SHA256_MATCH = 'YES'
                INPUT_READABLE = 'YES'
                INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE = 'YES'
                EXPECTED_KEY_COUNT = '41'
                PRESENT_EXPECTED_KEY_COUNT = '41'
                MISSING_KEY_COUNT = '0'
                UNKNOWN_KEY_COUNT = '0'
                DUPLICATE_KEY_COUNT = '0'
                MALFORMED_LINE_COUNT = '0'
                UNCONDITIONAL_COMPLETE_COUNT = '30/30'
                EMPTY_UNCONDITIONAL_KEY_COUNT = '0'
                FORMAT_ERROR_COUNT = '0'
                ENUM_ERROR_COUNT = '0'
                CONDITIONAL_ERROR_COUNT = '0'
                SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS = 'YES'
                OWNER_INPUT_VALUES_EMITTED = 'NO'
                RENDERER_EXECUTED = 'NO'
                EXTERNAL_MESSAGE_SEND_AUTHORIZED = 'NO'
                PROVIDER_NETWORK_AUTHORIZED = 'NO'
            } -Description 'WO-034 preflight'

            $launcherResult = Invoke-WO034OrchestratorChildProcess `
                -ScriptPath $runtimeLauncherPath -Arguments @(
                    '-InputPath', $inputPath,
                    '-OutputDirectory', $outputDirectory,
                    '-RepositoryRoot', $repositoryRoot,
                    '-PrivateRoot', $privateRoot) `
                -TimeoutSeconds 300 -Description 'WO-034 qualified launcher'
            if ($launcherResult.ExitCode -ne 0 -or
                -not [string]::IsNullOrEmpty($launcherResult.StandardError)) {
                throw 'The WO-034 qualified launcher failed without exposing private diagnostics.'
            }
            $launcherValues = ConvertFrom-WO034OrchestratorStatusOutput `
                -StandardOutput $launcherResult.StandardOutput `
                -Description 'WO-034 qualified launcher'
            Assert-WO034OrchestratorExpectedStatuses -Values $launcherValues -Expected @{
                WO034_RENDER_STATUS = 'SUCCESS'
                WO034_RENDERER_SHA256 = 'b722709fd0d8988718fcfefb8506803daba4cd241bb3dc00a88874dd708f28b6'
                WO034_RENDERER_RUNTIME_AND_SOURCE_STREAMS_REVERIFIED = 'YES'
                WO034_RENDERER_RUNTIME_AND_SOURCE_CONCURRENT_WRITE_OPEN_DENIED = 'YES'
                WO034_TEMPLATE_SHA256 = 'bfb9195b78aa663c2e7bb36e896b813087afb1d4e1609346bc9cb0ff21ad8356'
                WO034_PRIVATE_OWNER_EXECUTION_MATCH = 'YES'
                WO034_PRIVATE_ROOT_AND_GIT_WORKTREE_DISJOINT = 'YES'
                WO034_RENDER_PURPOSE = 'OWNER_FINAL'
                WO034_UNRESOLVED_OWNER_PLACEHOLDER_COUNT = '0'
                WO034_OUTBOUND_MESSAGE_SHA256_MATCH = 'YES'
                WO034_OUTBOUND_FORM_ENVELOPE_SHA256_MATCH = 'YES'
                WO034_FORM_MESSAGE_ROUND_TRIP_BYTE_IDENTICAL = 'YES'
                WO034_EXTERNAL_MESSAGE_SEND_AUTHORIZED = 'NO'
                WO034_EXTERNAL_MESSAGE_SENT = 'NO'
            } -Description 'WO-034 qualified launcher'
            return @($launcherResult.StandardOutput -split "`r?`n" |
                Where-Object { -not [string]::IsNullOrEmpty($_) })
        }

        Write-Output 'WO034_ORCHESTRATION_STATUS=SUCCESS'
        Write-Output 'WO034_PREFLIGHT_ATTESTED_AND_PASSED=YES'
        Write-Output 'WO034_FROZEN_LAUNCHER_SOURCE_AND_RUNTIME_ATTESTED=YES'
        Write-Output 'WO034_OWNER_INPUT_READ_LOCK_HELD_ACROSS_PREFLIGHT_AND_RENDER=YES'
        Write-Output 'WO034_OWNER_INPUT_BYTES_UNCHANGED=YES'
        Write-Output 'WO034_RENDERER_EXECUTED=YES'
        foreach ($line in $renderOutput) {
            Write-Output $line
        }
    } finally {
        if ($null -ne $runtimeLauncherStream) {
            $runtimeLauncherStream.Dispose()
        }
        if ($null -ne $sourceLauncherStream) {
            $sourceLauncherStream.Dispose()
        }
        if ($null -ne $preflightStream) {
            $preflightStream.Dispose()
        }
    }
}

if ($MyInvocation.InvocationName -cne '.') {
    Invoke-WO034PermissionRequestOrchestration -OwnerInputPath $InputPath `
        -RenderedOutputDirectory $OutputDirectory -SourceRepositoryRoot $RepositoryRoot `
        -ProtectedPrivateRoot $PrivateRoot
}
