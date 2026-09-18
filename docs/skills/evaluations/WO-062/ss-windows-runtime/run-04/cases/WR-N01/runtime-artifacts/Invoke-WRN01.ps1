$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = New-Object Text.UTF8Encoding($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$artifactRoot = $PSScriptRoot
$runId = [guid]::NewGuid().ToString()
$runtimeBase = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output\runtime'))
$root = Join-Path $runtimeBase ("WR native probe " + $runId)
$tempRoot = Join-Path $root 'temp'
$fixture = Join-Path $caseRoot 'inputs\fixtures\NativeProbe.java'
$events = New-Object Collections.ArrayList
$owned = New-Object Collections.ArrayList
$createdDirs = New-Object Collections.ArrayList
$stage = 'preflight'
$mainResult = 'NOT_COMPLETED'
$cleanupResult = 'NOT_COMPLETED'
$scriptExit = 1
$javaPath = $null
function Event([string]$name, $data) {
    [void]$events.Add([ordered]@{event=$name; utc=[DateTime]::UtcNow.ToString('o'); monotonic_ms=$clock.Elapsed.TotalMilliseconds; data=$data})
}
function Remaining { [Math]::Max(0, [int][Math]::Floor(45000 - $clock.Elapsed.TotalMilliseconds)) }
function Assert-Safe([string]$path, [string]$base) {
    if ($path -match '[\x00-\x1f"]' -or $path -match '(^|[\\/])\.\.([\\/]|$)') { throw 'Unsafe path syntax' }
    $full = [IO.Path]::GetFullPath($path)
    if (-not $full.StartsWith($base.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Path outside bound: $full" }
    $cursor = $full
    while ($cursor) {
        if ([IO.Directory]::Exists($cursor) -or [IO.File]::Exists($cursor)) {
            if (([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}
function Quote-Arg([string]$value) {
    if ($value -match '[\x00-\x1f"]' -or $value.EndsWith('\')) { throw 'Unsupported argument' }
    return '"' + $value + '"'
}
function File-Provenance([string]$path) {
    $item = Get-Item -LiteralPath $path
    $version = $item.VersionInfo
    [ordered]@{path=$item.FullName; bytes=$item.Length; sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash;
      file_version=$version.FileVersion; product_version=$version.ProductVersion; original_filename=$version.OriginalFilename;
      last_write_utc=$item.LastWriteTimeUtc.ToString('o')}
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
    foreach ($key in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) { $psi.EnvironmentVariables.Remove($key) }
    $p = New-Object Diagnostics.Process
    $p.StartInfo = $psi
    $r = [pscustomobject]@{ mode=$mode; identity=$identity; process=$p; handle=$null; pid_value=$null;
        start_utc=$null; image=$null; arguments=$psi.Arguments; started_ms=$clock.Elapsed.TotalMilliseconds;
        ready_ms=$null; ready_utc=$null; exit_code=$null; exit_ms=$null; forced_code=$null; killed=$false;
        lines=(New-Object Collections.ArrayList); line_task=$null; err_task=$null; stderr=''; identity_ok=$false; confirmed_exit=$false }
    if (-not $p.Start()) { throw 'Process.Start returned false' }
    [void]$owned.Add($r)
    $r.handle = $p.Handle
    $r.pid_value = $p.Id
    $r.start_utc = $p.StartTime.ToUniversalTime().ToString('o')
    $r.line_task = $p.StandardOutput.ReadLineAsync()
    $r.err_task = $p.StandardError.ReadToEndAsync()
    if ($mode -eq 'version') {
        try { $r.image = $p.MainModule.FileName } catch { $r.image = 'UNAVAILABLE_AFTER_FAST_VERSION_EXIT' }
    } else { $r.image = $p.MainModule.FileName }
    Event 'process_started' @{mode=$mode; uuid=$identity; pid=$r.pid_value; handle=$r.handle.ToInt64(); creation_utc=$r.start_utc;
        image=$r.image; requested_image=$javaPath; conductor_pid=$PID; command=((Quote-Arg $javaPath)+' '+$psi.Arguments);
        cwd=$root; temp=$tempRoot; user_home=$root; stdout_encoding='UTF-8 strict'; stderr_encoding='UTF-8 strict';
        environment='Child copy only: TEMP/TMP confined; four Java injection variables removed'; creation_relation='Conductor Process.Start; not measured by JVM'}
    return $r
}
function Drain($r) {
    while ($null -ne $r.line_task -and $r.line_task.IsCompleted) {
        $line = $r.line_task.GetAwaiter().GetResult()
        if ($null -eq $line) { $r.line_task=$null; break }
        [void]$r.lines.Add($line)
        if ($line -ceq 'WR_PROBE_READY' -and $null -eq $r.ready_ms) {
            $r.ready_ms=$clock.Elapsed.TotalMilliseconds
            $r.ready_utc=[DateTime]::UtcNow.ToString('o')
            Event 'ready_observed' @{mode=$r.mode; utc=$r.ready_utc; since_launch_ms=($r.ready_ms-$r.started_ms)}
        }
        $r.line_task=$r.process.StandardOutput.ReadLineAsync()
    }
}
function Observe-Exit($r) {
    if ($r.process.WaitForExit(0)) {
        $code = $r.process.ExitCode
        if (-not $r.confirmed_exit) {
            $r.confirmed_exit=$true
            $r.exit_ms=$clock.Elapsed.TotalMilliseconds
            if ($r.killed) { $r.forced_code=$code } else { $r.exit_code=$code }
            Event 'exit_code_copied_immediately' @{mode=$r.mode; code=$code; after_forced_stop=$r.killed; duration_ms=($r.exit_ms-$r.started_ms)}
        }
        return $true
    }
    return $false
}
function Identity-Matches($r) {
    $r.identity_ok = ($r.lines.Contains('WR_PROBE_RUN='+$r.identity) -and
      $r.lines.Contains('WR_PROBE_PID='+$r.pid_value) -and
      [string]::Equals($r.image,$javaPath,[StringComparison]::OrdinalIgnoreCase) -and
      $r.process.StartInfo.Arguments -ceq $r.arguments -and $r.process.Handle -eq $r.handle)
    return $r.identity_ok
}
function Stop-Owned($r) {
    if (Observe-Exit $r) { return }
    if (-not (Identity-Matches $r)) { throw 'Identity absent/divergent: forced stop blocked' }
    if (-not [string]::Equals($r.process.MainModule.FileName,$javaPath,[StringComparison]::OrdinalIgnoreCase)) { throw 'Live image mismatch' }
    if ($r.process.StartTime.ToUniversalTime().ToString('o') -cne $r.start_utc) { throw 'Creation time mismatch' }
    Event 'ownership_verified_before_kill' @{mode=$r.mode; pid=$r.pid_value; uuid=$r.identity; image=$r.image; handle=$r.handle.ToInt64(); command_source='Retained ProcessStartInfo'}
    $stopClock=[Diagnostics.Stopwatch]::StartNew()
    $r.process.Kill()
    $r.killed=$true
    if ($r.process.WaitForExit([Math]::Min(5000,(Remaining)))) {
        $code=$r.process.ExitCode
        $r.forced_code=$code
        $r.confirmed_exit=$true
        $r.exit_ms=$clock.Elapsed.TotalMilliseconds
        Event 'forced_exit_code_copied_immediately' @{mode=$r.mode; code=$code; cleanup_wait_ms=$stopClock.Elapsed.TotalMilliseconds; duration_ms=($r.exit_ms-$r.started_ms)}
    } else { throw 'Owned process did not exit within cleanup deadline' }
}
try {
    $self=[Diagnostics.Process]::GetCurrentProcess()
    $psTable=[ordered]@{}
    foreach ($key in $PSVersionTable.Keys) { $psTable[$key]=[string]$PSVersionTable[$key] }
    Event 'runtime' @{ps_version_table=$psTable; pshome=$PSHOME; executable=$self.MainModule.FileName; conductor_pid=$PID;
      cwd=(Get-Location).Path; windows=[Environment]::OSVersion.VersionString; is_64bit_os=[Environment]::Is64BitOperatingSystem;
      is_64bit_process=[Environment]::Is64BitProcess; clr=[Environment]::Version.ToString();
      output_encoding=$OutputEncoding.WebName; console_encoding=[Console]::OutputEncoding.WebName;
      artifact_encoding='UTF-8 no BOM'; fixture=$fixture; fixture_sha256=(Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash;
      statuses=@('EXPERIMENTAL','LOCAL_ONLY','NOT_PRODUCTION_APPROVED','NO_CRITICAL_DEPENDENCY')}
    if ($PSVersionTable.PSEdition -ne 'Desktop' -or $PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -ne 1 -or [Environment]::OSVersion.Platform -ne 'Win32NT') { throw 'Required Windows PowerShell 5.1 Desktop not observed' }
    Assert-Safe $root $runtimeBase
    Assert-Safe $artifactRoot (Join-Path $caseRoot 'output')
    if ([IO.Directory]::Exists($root) -or [IO.File]::Exists($root)) { throw 'Temporary root already exists' }
    [void][IO.Directory]::CreateDirectory($runtimeBase)
    [void][IO.Directory]::CreateDirectory($root)
    [void]$createdDirs.Add($root)
    [void][IO.Directory]::CreateDirectory($tempRoot)
    [void]$createdDirs.Add($tempRoot)
    Event 'temporary_root_created' @{root=$root; directories=@($createdDirs); files_created_by_driver=@(); canonical_separator_check=$true; reparse_chain_check=$true}
    $candidates=@(Get-Command java.exe -CommandType Application -All -ErrorAction Stop | ForEach-Object { File-Provenance $_.Source })
    Event 'java_candidates' $candidates
    $direct=@($candidates | Where-Object { $_.original_filename -ieq 'java.exe' -and ($_.file_version -match '^25([.,+\s-]|$)' -or $_.product_version -match '^25([.,+\s-]|$)') } | Sort-Object path -Unique)
    if ($direct.Count -ne 1) { throw "Unique direct Java 25 required; observed count=$($direct.Count)" }
    $javaPath=$direct[0].path
    $stage='java-version'
    $common=@('-XX:-UsePerfData',('-Djava.io.tmpdir='+$tempRoot),('-Duser.home='+$root))
    $version=Start-Java 'version' ($common+@('--version')) ([guid]::NewGuid().ToString())
    $deadline=[Math]::Min(10000,(Remaining))
    while (-not (Observe-Exit $version)) {
        Drain $version
        if (($clock.Elapsed.TotalMilliseconds-$version.started_ms) -ge $deadline) { throw 'Java version deadline expired' }
        [Threading.Thread]::Sleep(10)
    }
    Drain $version
    $versionText=($version.lines -join [Environment]::NewLine)
    if ($version.exit_code -ne 0 -or $versionText -notmatch '(?m)^(?:java|openjdk)\s+(?:version\s+)?["]?25(?:[.\-"\s]|$)') { throw "Effective Java 25 not verified (code=$($version.exit_code))" }
    Event 'java_version_verified' @{text=$versionText; immediate_code=$version.exit_code; path=$javaPath}
    foreach ($mode in @('failure','sleep')) {
        $required=16000
        if ($mode -eq 'sleep') { $required=17500 }
        if ((Remaining) -lt $required) { throw "Insufficient remaining global budget before $mode" }
        $stage=$mode
        $identity=[guid]::NewGuid().ToString()
        $r=Start-Java $mode ($common+@($fixture,$mode,$identity)) $identity
        if ($mode -eq 'failure') {
            while (-not (Observe-Exit $r)) {
                Drain $r
                if (($clock.Elapsed.TotalMilliseconds-$r.started_ms) -ge 10000) { throw 'Failure mode deadline expired' }
                [Threading.Thread]::Sleep(10)
            }
            Drain $r
            if (-not (Identity-Matches $r)) { throw 'Failure mode identity mismatch' }
            Event 'failure_observation' @{native_code=$r.exit_code; identity_match=$r.identity_ok; stdout=@($r.lines); duration_ms=($r.exit_ms-$r.started_ms)}
        } else {
            while ($null -eq $r.ready_ms) {
                Drain $r
                if ($null -ne $r.ready_ms) { break }
                if (Observe-Exit $r) { throw 'Sleep mode exited before READY' }
                if (($clock.Elapsed.TotalMilliseconds-$r.started_ms) -ge 10000) { throw 'Readiness deadline expired' }
                [Threading.Thread]::Sleep(5)
            }
            if (-not (Identity-Matches $r)) { throw 'Sleep mode identity mismatch' }
            while (($clock.Elapsed.TotalMilliseconds-$r.ready_ms) -lt 1500) {
                if (Observe-Exit $r) { throw 'Sleep exited before expiration' }
                $left=1500-($clock.Elapsed.TotalMilliseconds-$r.ready_ms)
                if ($left -gt 0) { [Threading.Thread]::Sleep([Math]::Max(1,[Math]::Min(5,[int]$left))) }
            }
            if (Observe-Exit $r) { throw 'Sleep no longer alive at expiration' }
            Event 'timeout_observed_after_ready' @{mode=$mode; alive=$true; ready_utc=$r.ready_utc; elapsed_after_ready_ms=($clock.Elapsed.TotalMilliseconds-$r.ready_ms); deadline_ms=1500; remaining_global_ms=(Remaining)}
            Stop-Owned $r
        }
    }
    $mainResult='TWO_MODES_OBSERVED'
    $scriptExit=0
} catch {
    Event 'conductor_error' @{stage=$stage; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; id=$_.FullyQualifiedErrorId; line=$_.InvocationInfo.ScriptLineNumber; position=$_.InvocationInfo.PositionMessage; stack=$_.ScriptStackTrace}
    $mainResult='BLOCKED_OR_INCOMPLETE'
    $scriptExit=1
} finally {
    $cleanupResult='CONFIRMED'
    foreach ($r in $owned) {
        try {
            if (-not (Observe-Exit $r)) {
                Drain $r
                if ($r.mode -ne 'version' -and (Identity-Matches $r)) { Stop-Owned $r }
                else {
                    Event 'forced_stop_blocked' @{mode=$r.mode; reason='Missing probe identity; bounded natural-exit observation only'}
                    $naturalWait=[Math]::Min((Remaining),[Math]::Max(0,[int](16000-($clock.Elapsed.TotalMilliseconds-$r.started_ms))))
                    if ($r.process.WaitForExit($naturalWait)) {
                        $code=$r.process.ExitCode
                        $r.exit_code=$code
                        $r.confirmed_exit=$true
                        Event 'natural_exit_copied_immediately' @{mode=$r.mode; code=$code}
                    } else { throw 'Natural exit not confirmed within remaining budget' }
                }
            }
            $streamDeadline=$clock.Elapsed.TotalMilliseconds+[Math]::Min(1000,(Remaining))
            while (($null -ne $r.line_task -or -not $r.err_task.IsCompleted) -and $clock.Elapsed.TotalMilliseconds -lt $streamDeadline) { Drain $r; [Threading.Thread]::Sleep(1) }
            Drain $r
            if ($r.err_task.IsCompleted) { $r.stderr=$r.err_task.GetAwaiter().GetResult() } else { throw 'stderr drain deadline expired' }
            Event 'process_postflight' @{mode=$r.mode; pid=$r.pid_value; uuid=$r.identity; creation_utc=$r.start_utc; image=$r.image;
                handle_exit_confirmed=$r.confirmed_exit; native_code=$r.exit_code; forced_code=$r.forced_code; identity_match=$r.identity_ok;
                stdout=@($r.lines); stderr=$r.stderr}
            if (-not $r.confirmed_exit) { throw 'Exit unconfirmed' }
            $r.process.Dispose()
        } catch {
            $cleanupResult='INCOMPLETE'
            $scriptExit=2
            Event 'cleanup_error' @{stage=$r.mode; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; id=$_.FullyQualifiedErrorId; line=$_.InvocationInfo.ScriptLineNumber; stack=$_.ScriptStackTrace}
        }
    }
    try {
        if ($cleanupResult -eq 'CONFIRMED') {
            for ($i=$createdDirs.Count-1; $i -ge 0; $i--) {
                $dir=[string]$createdDirs[$i]
                Assert-Safe $dir $runtimeBase
                $entries=@([IO.Directory]::GetFileSystemEntries($dir))
                Event 'directory_predelete' @{path=$dir; entries=$entries}
                if ($entries.Count -ne 0) { throw "Unexpected residue preserved: $dir" }
                [IO.Directory]::Delete($dir,$false)
            }
        }
    } catch {
        $cleanupResult='INCOMPLETE'
        $scriptExit=2
        Event 'filesystem_cleanup_error' @{type=$_.Exception.GetType().FullName; message=$_.Exception.Message; id=$_.FullyQualifiedErrorId; line=$_.InvocationInfo.ScriptLineNumber; stack=$_.ScriptStackTrace}
    }
    Event 'temporary_root_postflight' @{path=$root; absent=(-not [IO.Directory]::Exists($root)); cleanup=$cleanupResult}
    if ($clock.Elapsed.TotalMilliseconds -gt 45000) { $scriptExit=3; Event 'global_budget_exceeded' @{budget_ms=45000} }
    Event 'conductor_return' @{main_result=$mainResult; cleanup_result=$cleanupResult; exit_code=$scriptExit; elapsed_ms=$clock.Elapsed.TotalMilliseconds; budget_ms=45000}
    $report=[ordered]@{case='WR-N01'; session_uuid=$runId; temporary_root=$root; main_result=$mainResult; cleanup_result=$cleanupResult; conductor_exit=$scriptExit; events=@($events)}
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'observations.json'),($report|ConvertTo-Json -Depth 14),$utf8)
    $ready=[ordered]@{event='evidence_available';utc=[DateTime]::UtcNow.ToString('o'); elapsed_ms=$clock.Elapsed.TotalMilliseconds; report='observations.json'}
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'evidence-available.json'),($ready|ConvertTo-Json),$utf8)
    Write-Output ('WR_CONDUCTOR_RESULT='+$mainResult+'; CLEANUP='+$cleanupResult+'; EXIT='+$scriptExit)
}
exit $scriptExit