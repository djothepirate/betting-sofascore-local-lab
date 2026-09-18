[CmdletBinding()]
param(
    [switch]$PreflightOnly
)

# C5 draft only. It is designed to be copied to a fresh WR-N01
# output/runtime-artifacts directory by the C5 session; it is not a reusable runtime tool.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2

$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = New-Object Text.UTF8Encoding($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$artifactRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$outputRoot = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output'))
$runtimeBase = [IO.Path]::GetFullPath((Join-Path $outputRoot 'runtime'))
$runId = [guid]::NewGuid().ToString()
$root = Join-Path $runtimeBase ('WR native probe ' + $runId)
$tempRoot = Join-Path $root 'temp'
$fixture = Join-Path $caseRoot 'inputs\fixtures\NativeProbe.java'
$phaseName = if ($PreflightOnly) { 'preflight' } else { 'full' }
$reportPath = Join-Path $artifactRoot ($phaseName + '-observations.json')
$readyPath = Join-Path $artifactRoot ($phaseName + '-evidence-available.json')

$events = New-Object Collections.ArrayList
$owned = New-Object Collections.ArrayList
$createdDirs = New-Object Collections.ArrayList
$environmentBefore = @{}
foreach ($key in @('TEMP', 'TMP', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
    $environmentBefore[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}

$stage = 'runtime_registration'
$mainResult = 'NOT_COMPLETED'
$cleanupResult = 'NOT_COMPLETED'
$scriptExit = 1
$javaPath = $null
$fixtureSha256Before = $null
$fixtureSha256After = $null

function Write-Event([string]$name, $data) {
    [void]$events.Add([ordered]@{
        event = $name
        utc = [DateTime]::UtcNow.ToString('o')
        monotonic_ms = $clock.Elapsed.TotalMilliseconds
        data = $data
    })
}

function Remaining {
    return [Math]::Max(0, [int][Math]::Floor(45000 - $clock.Elapsed.TotalMilliseconds))
}

function Assert-Safe([string]$path, [string]$base) {
    if ($path -match '[\x00-\x1f"]' -or $path -match '(^|[\\/])\.\.([\\/]|$)') {
        throw 'Unsafe path syntax'
    }
    $full = [IO.Path]::GetFullPath($path)
    $canonicalBase = [IO.Path]::GetFullPath($base).TrimEnd('\')
    if (-not $full.StartsWith($canonicalBase + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path outside bound: $full"
    }
    $cursor = $full
    while ($cursor) {
        if ([IO.Directory]::Exists($cursor) -or [IO.File]::Exists($cursor)) {
            if (([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Reparse point: $cursor"
            }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
    return $full
}

function Get-DotNetSha256([string]$path) {
    $stream = $null
    $algorithm = $null
    try {
        $stream = [IO.File]::OpenRead($path)
        $algorithm = [Security.Cryptography.SHA256]::Create()
        return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    } finally {
        if ($null -ne $algorithm) { $algorithm.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Test-DotNetSha256 {
    $algorithm = $null
    try {
        $bytes = [Text.Encoding]::ASCII.GetBytes('abc')
        $algorithm = [Security.Cryptography.SHA256]::Create()
        $actual = ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace('-', '').ToUpperInvariant()
        $expected = 'BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD'
        return [ordered]@{
            provider = 'System.Security.Cryptography.SHA256'
            vector = 'ASCII abc'
            expected = $expected
            actual = $actual
            passed = ($actual -ceq $expected)
        }
    } finally {
        if ($null -ne $algorithm) { $algorithm.Dispose() }
    }
}

function Test-ConductorDependencies([bool]$driverImageObserved) {
    # This function deliberately runs in the launched powershell.exe process. It
    # neither invokes Java nor changes PSModulePath. Get-FileHash is only observed.
    $getFileHash = @(Get-Command -Name Get-FileHash -ErrorAction SilentlyContinue)[0]
    $getFileHashObservation = [ordered]@{
        available = ($null -ne $getFileHash)
        invoked = $false
        required = $false
        command_type = if ($null -ne $getFileHash) { [string]$getFileHash.CommandType } else { $null }
        module = if ($null -ne $getFileHash) { [string]$getFileHash.ModuleName } else { $null }
    }

    $sha256 = $null
    $sha256Error = $null
    try {
        $sha256 = Test-DotNetSha256
    } catch {
        $sha256Error = $_.Exception.Message
    }

    $processApiError = $null
    try {
        $probe = New-Object Diagnostics.ProcessStartInfo
        $processApi = [ordered]@{
            process_type = ([Diagnostics.Process]::GetCurrentProcess()).GetType().FullName
            start_info_type = $probe.GetType().FullName
            redirect_supported = ($null -ne $probe.PSObject.Properties['RedirectStandardOutput'])
            main_module_observed = $true
        }
    } catch {
        $processApi = $null
        $processApiError = $_.Exception.Message
    }

    $utf8Error = $null
    try {
        $sampleBytes = $utf8.GetBytes('café équipe')
        $utf8Observation = [ordered]@{
            web_name = $utf8.WebName
            strict_encoder = $true
            sample_byte_count = $sampleBytes.Length
        }
    } catch {
        $utf8Observation = $null
        $utf8Error = $_.Exception.Message
    }

    $ps51Desktop = (
        $PSVersionTable.PSEdition -eq 'Desktop' -and
        $PSVersionTable.PSVersion.Major -eq 5 -and
        $PSVersionTable.PSVersion.Minor -eq 1 -and
        [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
    )

    $dependency = [ordered]@{
        phase = 'before_fixture_fingerprint_and_java'
        runtime_is_windows_powershell_5_1_desktop = $ps51Desktop
        dotnet_sha256 = $sha256
        dotnet_sha256_error = $sha256Error
        get_file_hash_observation = $getFileHashObservation
        process_api = $processApi
        process_api_error = $processApiError
        current_process_image_observed = $driverImageObserved
        utf8 = $utf8Observation
        utf8_error = $utf8Error
        java_discovery_or_launch = 'NOT_PERFORMED'
        psmodulepath_mutation = 'NOT_PERFORMED'
    }
    $dependency.passed = (
        $ps51Desktop -and
        $null -ne $sha256 -and $sha256.passed -and
        $null -ne $processApi -and
        $driverImageObserved -and
        $null -ne $utf8Observation
    )
    Write-Event 'conductor_dependencies_checked' $dependency
    if (-not $dependency.passed) {
        throw 'Required conductor dependency unavailable in this Windows PowerShell 5.1 process'
    }
    return $dependency
}

function File-Provenance([string]$path) {
    $item = Get-Item -LiteralPath $path
    $version = $item.VersionInfo
    return [ordered]@{
        path = $item.FullName
        bytes = $item.Length
        sha256 = Get-DotNetSha256 $path
        sha256_provider = 'System.Security.Cryptography.SHA256'
        file_version = $version.FileVersion
        product_version = $version.ProductVersion
        original_filename = $version.OriginalFilename
        last_write_utc = $item.LastWriteTimeUtc.ToString('o')
    }
}

function Quote-Arg([string]$value) {
    if ($value -match '[\x00-\x1f"]' -or $value.EndsWith('\')) {
        throw 'Unsupported argument'
    }
    return '"' + $value + '"'
}

function Start-Java([string]$mode, [string[]]$nativeArgs, [string]$identity) {
    $psi = New-Object Diagnostics.ProcessStartInfo
    $psi.FileName = $javaPath
    $psi.Arguments = (($nativeArgs | ForEach-Object { Quote-Arg $_ }) -join ' ')
    $psi.WorkingDirectory = $root
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = $utf8
    $psi.StandardErrorEncoding = $utf8
    $psi.EnvironmentVariables['TEMP'] = $tempRoot
    $psi.EnvironmentVariables['TMP'] = $tempRoot
    foreach ($key in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
        $psi.EnvironmentVariables.Remove($key)
    }

    $p = New-Object Diagnostics.Process
    $p.StartInfo = $psi
    $r = [pscustomobject]@{
        mode = $mode
        identity = $identity
        process = $p
        handle = $null
        pid_value = $null
        start_utc = $null
        image = $null
        image_observation_error = $null
        arguments = $psi.Arguments
        started_ms = $clock.Elapsed.TotalMilliseconds
        ready_ms = $null
        ready_utc = $null
        exit_code = $null
        exit_ms = $null
        forced_code = $null
        killed = $false
        lines = (New-Object Collections.ArrayList)
        line_task = $null
        err_task = $null
        stderr = ''
        identity_ok = $false
        confirmed_exit = $false
        streams_finalized = $false
    }

    if (-not $p.Start()) {
        $p.Dispose()
        throw 'Process.Start returned false'
    }
    [void]$owned.Add($r)
    $r.handle = $p.Handle
    $r.pid_value = $p.Id
    $r.start_utc = $p.StartTime.ToUniversalTime().ToString('o')
    $r.line_task = $p.StandardOutput.ReadLineAsync()
    $r.err_task = $p.StandardError.ReadToEndAsync()
    try {
        $r.image = $p.MainModule.FileName
    } catch {
        $r.image_observation_error = $_.Exception.Message
        if ($mode -ne 'version') {
            throw "Required image observation failed for $mode"
        }
    }
    Write-Event 'process_started' @{
        mode = $mode
        uuid = $identity
        pid = $r.pid_value
        handle = $r.handle.ToInt64()
        creation_utc = $r.start_utc
        image = $r.image
        image_observation_error = $r.image_observation_error
        requested_image = $javaPath
        conductor_pid = $PID
        command = ((Quote-Arg $javaPath) + ' ' + $psi.Arguments)
        cwd = $root
        temp = $tempRoot
        user_home = $root
        stdout_encoding = 'UTF-8 strict'
        stderr_encoding = 'UTF-8 strict'
        environment = 'Child copy only: TEMP/TMP confined; four Java injection variables removed'
        creation_relation = 'Conductor Process.Start; not measured by JVM'
    }
    return $r
}

function Drain($r) {
    while ($null -ne $r.line_task -and $r.line_task.IsCompleted) {
        $line = $r.line_task.GetAwaiter().GetResult()
        if ($null -eq $line) {
            $r.line_task = $null
            break
        }
        [void]$r.lines.Add($line)
        if ($line -ceq 'WR_PROBE_READY' -and $null -eq $r.ready_ms) {
            $r.ready_ms = $clock.Elapsed.TotalMilliseconds
            $r.ready_utc = [DateTime]::UtcNow.ToString('o')
            Write-Event 'ready_observed' @{
                mode = $r.mode
                utc = $r.ready_utc
                since_launch_ms = ($r.ready_ms - $r.started_ms)
            }
        }
        $r.line_task = $r.process.StandardOutput.ReadLineAsync()
    }
}

function Observe-Exit($r) {
    if ($r.process.WaitForExit(0)) {
        $code = $r.process.ExitCode
        if (-not $r.confirmed_exit) {
            $r.confirmed_exit = $true
            $r.exit_ms = $clock.Elapsed.TotalMilliseconds
            if ($r.killed) {
                $r.forced_code = $code
            } else {
                $r.exit_code = $code
            }
            Write-Event 'exit_code_copied_immediately' @{
                mode = $r.mode
                code = $code
                after_forced_stop = $r.killed
                duration_ms = ($r.exit_ms - $r.started_ms)
            }
        }
        return $true
    }
    return $false
}

function Finalize-Streams($r) {
    if ($r.streams_finalized) {
        return
    }
    if ($null -eq $r.err_task) {
        throw "Stream tasks were not retained for $($r.mode)"
    }
    $streamDeadline = $clock.Elapsed.TotalMilliseconds + [Math]::Min(1000, (Remaining))
    while (($null -ne $r.line_task -or -not $r.err_task.IsCompleted) -and $clock.Elapsed.TotalMilliseconds -lt $streamDeadline) {
        Drain $r
        [Threading.Thread]::Sleep(1)
    }
    Drain $r
    if ($null -ne $r.line_task -or -not $r.err_task.IsCompleted) {
        throw "Stream drain deadline expired for $($r.mode)"
    }
    $r.stderr = $r.err_task.GetAwaiter().GetResult()
    $r.streams_finalized = $true
}

function Assert-ProbeIdentity($r) {
    $expectedText = 'WR_PROBE_TEXT=caf' + [char]0x00e9 + ' ' + [char]0x00e9 + 'quipe'
    $r.identity_ok = (
        $r.lines.Contains('WR_PROBE_RUN=' + $r.identity) -and
        $r.lines.Contains('WR_PROBE_PID=' + $r.pid_value) -and
        $r.lines.Contains($expectedText) -and
        [string]::Equals($r.image, $javaPath, [StringComparison]::OrdinalIgnoreCase) -and
        $r.process.StartInfo.Arguments -ceq $r.arguments -and
        $r.process.Handle -eq $r.handle
    )
    if (-not $r.identity_ok) {
        throw "Fixture identity or UTF-8 text mismatch for $($r.mode)"
    }
}

function Stop-Owned($r) {
    if (Observe-Exit $r) {
        return
    }
    Drain $r
    Assert-ProbeIdentity $r
    if ($r.process.Handle -ne $r.handle) {
        throw 'Live handle mismatch'
    }
    if ($r.process.Id -ne $r.pid_value) {
        throw 'Live PID mismatch'
    }
    if (-not [string]::Equals($r.process.MainModule.FileName, $javaPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Live image mismatch'
    }
    if ($r.process.StartTime.ToUniversalTime().ToString('o') -cne $r.start_utc) {
        throw 'Creation time mismatch'
    }
    Write-Event 'ownership_verified_before_kill' @{
        mode = $r.mode
        pid = $r.pid_value
        uuid = $r.identity
        image = $r.image
        handle = $r.handle.ToInt64()
        creation_utc = $r.start_utc
        command_source = 'Retained ProcessStartInfo'
    }
    $stopClock = [Diagnostics.Stopwatch]::StartNew()
    $r.process.Kill()
    $r.killed = $true
    if ($r.process.WaitForExit([Math]::Min(5000, (Remaining)))) {
        $code = $r.process.ExitCode
        $r.forced_code = $code
        $r.confirmed_exit = $true
        $r.exit_ms = $clock.Elapsed.TotalMilliseconds
        Write-Event 'forced_exit_code_copied_immediately' @{
            mode = $r.mode
            code = $code
            cleanup_wait_ms = $stopClock.Elapsed.TotalMilliseconds
            duration_ms = ($r.exit_ms - $r.started_ms)
        }
    } else {
        throw 'Owned process did not exit within cleanup deadline'
    }
}

try {
    Assert-Safe $artifactRoot $outputRoot | Out-Null
    Assert-Safe $root $runtimeBase | Out-Null
    if ([IO.File]::Exists($reportPath) -or [IO.File]::Exists($readyPath)) {
        throw "Evidence already exists for $phaseName; no retry or overwrite is permitted"
    }

    $self = [Diagnostics.Process]::GetCurrentProcess()
    $psTable = [ordered]@{}
    foreach ($key in $PSVersionTable.Keys) {
        $psTable[$key] = [string]$PSVersionTable[$key]
    }
    $selfImage = $null
    $selfImageError = $null
    try {
        $selfImage = $self.MainModule.FileName
    } catch {
        $selfImageError = $_.Exception.Message
    }
    Write-Event 'runtime_registered' @{
        ps_version_table = $psTable
        pshome = $PSHOME
        executable = $selfImage
        executable_observation_error = $selfImageError
        conductor_pid = $PID
        cwd = (Get-Location).Path
        process_cwd = [Environment]::CurrentDirectory
        windows = [Environment]::OSVersion.VersionString
        platform = [string][Environment]::OSVersion.Platform
        is_64bit_os = [Environment]::Is64BitOperatingSystem
        is_64bit_process = [Environment]::Is64BitProcess
        clr = [Environment]::Version.ToString()
        output_encoding = $OutputEncoding.WebName
        console_encoding = [Console]::OutputEncoding.WebName
        artifact_encoding = 'UTF-8 no BOM strict'
        fixture = $fixture
        fixture_fingerprint_recorded = $false
        statuses = @('EXPERIMENTAL', 'LOCAL_ONLY', 'NOT_PRODUCTION_APPROVED', 'NO_CRITICAL_DEPENDENCY')
    }

    $stage = 'conductor_dependencies'
    Test-ConductorDependencies ($null -ne $selfImage) | Out-Null

    $stage = 'fixture_fingerprint'
    if (-not [IO.File]::Exists($fixture)) {
        throw 'Required fixture is absent'
    }
    $fixtureSha256Before = Get-DotNetSha256 $fixture
    Write-Event 'fixture_fingerprint' @{
        path = $fixture
        sha256 = $fixtureSha256Before
        provider = 'System.Security.Cryptography.SHA256'
        recorded_after_runtime_and_dependency_checks = $true
    }

    if ($PreflightOnly) {
        $mainResult = 'PREFLIGHT_VERIFIED_NO_JAVA'
        $scriptExit = 0
        Write-Event 'preflight_only_complete' @{
            java_discovery = 'NOT_PERFORMED'
            java_launch = 'NOT_PERFORMED'
            temporary_root_created = $false
        }
    } else {
        $stage = 'temporary_root'
        if ([IO.Directory]::Exists($root) -or [IO.File]::Exists($root)) {
            throw 'Temporary root already exists'
        }
        [void][IO.Directory]::CreateDirectory($runtimeBase)
        [void][IO.Directory]::CreateDirectory($root)
        [void]$createdDirs.Add($root)
        [void][IO.Directory]::CreateDirectory($tempRoot)
        [void]$createdDirs.Add($tempRoot)
        Write-Event 'temporary_root_created' @{
            root = $root
            directories = @($createdDirs)
            files_created_by_driver = @()
            canonical_separator_check = $true
            reparse_chain_check = $true
        }

        $stage = 'java_discovery'
        $candidates = @(
            Get-Command java.exe -CommandType Application -All -ErrorAction Stop |
            ForEach-Object { File-Provenance $_.Source }
        )
        Write-Event 'java_candidates' $candidates
        $direct = @(
            $candidates |
            Where-Object {
                $_.original_filename -ieq 'java.exe' -and
                ($_.file_version -match '^25([.,+\s-]|$)' -or $_.product_version -match '^25([.,+\s-]|$)')
            } |
            Sort-Object path -Unique
        )
        if ($direct.Count -ne 1) {
            throw "Unique direct Java 25 required; observed count=$($direct.Count)"
        }
        $javaPath = $direct[0].path

        $common = @(
            '-XX:-UsePerfData',
            ('-Djava.io.tmpdir=' + $tempRoot),
            ('-Duser.home=' + $root)
        )
        $stage = 'java_version'
        $version = Start-Java 'version' ($common + @('--version')) ([guid]::NewGuid().ToString())
        $versionDeadline = [Math]::Min(10000, (Remaining))
        while (-not (Observe-Exit $version)) {
            Drain $version
            if (($clock.Elapsed.TotalMilliseconds - $version.started_ms) -ge $versionDeadline) {
                throw 'Java version deadline expired'
            }
            [Threading.Thread]::Sleep(10)
        }
        Drain $version
        Finalize-Streams $version
        $versionText = (($version.lines -join [Environment]::NewLine) + [Environment]::NewLine + $version.stderr)
        if ($version.exit_code -ne 0 -or $versionText -notmatch '(?m)^(?:java|openjdk)\s+(?:version\s+)?["]?25(?:[.\-"\s]|$)') {
            throw "Effective Java 25 not verified (code=$($version.exit_code))"
        }
        Write-Event 'java_version_verified' @{
            text = $versionText
            immediate_code = $version.exit_code
            path = $javaPath
        }

        foreach ($mode in @('failure', 'sleep')) {
            $required = if ($mode -eq 'sleep') { 17500 } else { 16000 }
            if ((Remaining) -lt $required) {
                throw "Insufficient remaining global budget before $mode"
            }
            $stage = $mode
            $identity = [guid]::NewGuid().ToString()
            $r = Start-Java $mode ($common + @($fixture, $mode, $identity)) $identity
            if ($mode -eq 'failure') {
                while (-not (Observe-Exit $r)) {
                    Drain $r
                    if (($clock.Elapsed.TotalMilliseconds - $r.started_ms) -ge 10000) {
                        throw 'Failure mode deadline expired'
                    }
                    [Threading.Thread]::Sleep(10)
                }
                Drain $r
                Finalize-Streams $r
                Assert-ProbeIdentity $r
                if ($r.exit_code -ne 23) {
                    throw "Failure native code is $($r.exit_code), not 23"
                }
                Write-Event 'failure_observation' @{
                    native_code = $r.exit_code
                    identity_match = $r.identity_ok
                    stdout = @($r.lines)
                    stderr = $r.stderr
                    duration_ms = ($r.exit_ms - $r.started_ms)
                }
            } else {
                while ($null -eq $r.ready_ms) {
                    Drain $r
                    if ($null -ne $r.ready_ms) {
                        break
                    }
                    if (Observe-Exit $r) {
                        throw 'Sleep mode exited before READY'
                    }
                    if (($clock.Elapsed.TotalMilliseconds - $r.started_ms) -ge 10000) {
                        throw 'Readiness deadline expired'
                    }
                    [Threading.Thread]::Sleep(5)
                }
                Assert-ProbeIdentity $r
                while (($clock.Elapsed.TotalMilliseconds - $r.ready_ms) -lt 1500) {
                    if (Observe-Exit $r) {
                        throw 'Sleep exited before expiration'
                    }
                    $left = 1500 - ($clock.Elapsed.TotalMilliseconds - $r.ready_ms)
                    if ($left -gt 0) {
                        [Threading.Thread]::Sleep([Math]::Max(1, [Math]::Min(5, [int]$left)))
                    }
                }
                if (Observe-Exit $r) {
                    throw 'Sleep no longer alive at expiration'
                }
                Write-Event 'timeout_observed_after_ready' @{
                    mode = $mode
                    alive = $true
                    ready_utc = $r.ready_utc
                    elapsed_after_ready_ms = ($clock.Elapsed.TotalMilliseconds - $r.ready_ms)
                    deadline_ms = 1500
                    remaining_global_ms = (Remaining)
                }
                Stop-Owned $r
                Finalize-Streams $r
            }
        }
        $mainResult = 'TWO_MODES_OBSERVED'
        $scriptExit = 0
    }
} catch {
    Write-Event 'conductor_error' @{
        stage = $stage
        type = $_.Exception.GetType().FullName
        message = $_.Exception.Message
        id = $_.FullyQualifiedErrorId
        line = $_.InvocationInfo.ScriptLineNumber
        position = $_.InvocationInfo.PositionMessage
        stack = $_.ScriptStackTrace
    }
    $mainResult = 'BLOCKED_OR_INCOMPLETE'
    $scriptExit = 1
} finally {
    $cleanupResult = 'CONFIRMED'
    foreach ($r in $owned) {
        try {
            if (-not (Observe-Exit $r)) {
                Drain $r
                if ($r.mode -ne 'version') {
                    Assert-ProbeIdentity $r
                    Stop-Owned $r
                } else {
                    Write-Event 'forced_stop_blocked' @{
                        mode = $r.mode
                        reason = 'Version process lacks fixture identity; only a bounded natural-exit observation is permitted'
                    }
                    $naturalWait = [Math]::Min((Remaining), [Math]::Max(0, [int](16000 - ($clock.Elapsed.TotalMilliseconds - $r.started_ms))))
                    if (0 -lt $naturalWait -and $r.process.WaitForExit($naturalWait)) {
                        $r.exit_code = $r.process.ExitCode
                        $r.confirmed_exit = $true
                        $r.exit_ms = $clock.Elapsed.TotalMilliseconds
                        Write-Event 'natural_exit_code_copied_immediately' @{
                            mode = $r.mode
                            code = $r.exit_code
                        }
                    } else {
                        throw 'Version process exit not confirmed within remaining budget'
                    }
                }
            }
            Finalize-Streams $r
            Write-Event 'process_postflight' @{
                mode = $r.mode
                pid = $r.pid_value
                uuid = $r.identity
                creation_utc = $r.start_utc
                image = $r.image
                handle_exit_confirmed = $r.confirmed_exit
                native_code = $r.exit_code
                forced_code = $r.forced_code
                identity_match = $r.identity_ok
                stdout = @($r.lines)
                stderr = $r.stderr
            }
            if (-not $r.confirmed_exit) {
                throw 'Exit unconfirmed'
            }
            $r.process.Dispose()
        } catch {
            $cleanupResult = 'INCOMPLETE'
            $scriptExit = 2
            Write-Event 'cleanup_error' @{
                stage = $r.mode
                type = $_.Exception.GetType().FullName
                message = $_.Exception.Message
                id = $_.FullyQualifiedErrorId
                line = $_.InvocationInfo.ScriptLineNumber
                stack = $_.ScriptStackTrace
            }
        }
    }

    try {
        if ($null -ne $fixtureSha256Before) {
            $fixtureSha256After = Get-DotNetSha256 $fixture
            Write-Event 'fixture_postflight' @{
                path = $fixture
                sha256_before = $fixtureSha256Before
                sha256_after = $fixtureSha256After
                unchanged = ($fixtureSha256Before -ceq $fixtureSha256After)
            }
            if ($fixtureSha256Before -cne $fixtureSha256After) {
                throw 'Fixture changed during the conductor'
            }
        }

        if ($cleanupResult -eq 'CONFIRMED') {
            for ($i = $createdDirs.Count - 1; $i -ge 0; $i--) {
                $dir = [string]$createdDirs[$i]
                Assert-Safe $dir $runtimeBase | Out-Null
                $entries = @([IO.Directory]::GetFileSystemEntries($dir))
                Write-Event 'directory_predelete' @{ path = $dir; entries = $entries }
                if ($entries.Count -ne 0) {
                    throw "Unexpected residue preserved: $dir"
                }
                [IO.Directory]::Delete($dir, $false)
            }
        }
    } catch {
        $cleanupResult = 'INCOMPLETE'
        $scriptExit = 2
        Write-Event 'filesystem_cleanup_error' @{
            type = $_.Exception.GetType().FullName
            message = $_.Exception.Message
            id = $_.FullyQualifiedErrorId
            line = $_.InvocationInfo.ScriptLineNumber
            stack = $_.ScriptStackTrace
        }
    }

    $environmentUnchanged = $true
    foreach ($key in $environmentBefore.Keys) {
        if ([Environment]::GetEnvironmentVariable($key, 'Process') -cne $environmentBefore[$key]) {
            $environmentUnchanged = $false
        }
    }
    if (-not $environmentUnchanged) {
        $cleanupResult = 'INCOMPLETE'
        $scriptExit = 2
        Write-Event 'environment_cleanup_error' @{
            message = 'Conductor process environment differs from its initial targeted values'
            values_exposed = $false
        }
    }
    Write-Event 'temporary_root_postflight' @{
        path = $root
        absent = (-not [IO.Directory]::Exists($root))
        cleanup = $cleanupResult
        parent_targeted_environment_unchanged = $environmentUnchanged
    }
    if ($clock.Elapsed.TotalMilliseconds -gt 45000) {
        $scriptExit = 3
        Write-Event 'global_budget_exceeded' @{ budget_ms = 45000 }
    }
    Write-Event 'conductor_return' @{
        phase = $phaseName
        main_result = $mainResult
        cleanup_result = $cleanupResult
        exit_code = $scriptExit
        elapsed_ms = $clock.Elapsed.TotalMilliseconds
        budget_ms = 45000
    }
    $report = [ordered]@{
        case = 'WR-N01'
        phase = $phaseName
        session_uuid = $runId
        temporary_root = $root
        main_result = $mainResult
        cleanup_result = $cleanupResult
        conductor_exit = $scriptExit
        events = @($events)
    }
    [IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 14), $utf8)
    $ready = [ordered]@{
        event = 'evidence_available'
        utc = [DateTime]::UtcNow.ToString('o')
        elapsed_ms = $clock.Elapsed.TotalMilliseconds
        report = [IO.Path]::GetFileName($reportPath)
    }
    [IO.File]::WriteAllText($readyPath, ($ready | ConvertTo-Json), $utf8)
    Write-Output ('WR_CONDUCTOR_RESULT=' + $mainResult + '; CLEANUP=' + $cleanupResult + '; EXIT=' + $scriptExit + '; PHASE=' + $phaseName)
}

exit $scriptExit
