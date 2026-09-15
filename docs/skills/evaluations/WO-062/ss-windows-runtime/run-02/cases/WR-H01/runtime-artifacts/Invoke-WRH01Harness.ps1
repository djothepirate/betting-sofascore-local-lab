[CmdletBinding()]
param()

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$caseRoot = [IO.Path]::GetFullPath((Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$outputRoot = Join-Path $caseRoot 'output'
$runtimeRoot = Join-Path $outputRoot 'runtime'
$events = [Collections.Generic.List[object]]::new()
$observedFiles = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$observedRoots = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$p = $null
$nativeCode = $null
$forcedCode = $null
$outTask = $null
$errTask = $null
$stdout = ''
$stderr = ''
$primaryFailure = $null
$cleanupFailure = $null
$cleanupResult = 'NOT_OBSERVED'
$launchMs = $null
$exitMs = $null
$sourceBefore = @()
$sourceAfter = @()
$runtime = $null
$command = $null
$identity = $null
$postflight = $null
$parentTemp = $env:TEMP
$parentTmp = $env:TMP
$startUtc = [DateTime]::UtcNow.ToString('O')

function Event([string]$Name, $Data) {
    $events.Add([pscustomobject]@{event=$Name;utc=[DateTime]::UtcNow.ToString('O');elapsedMs=$clock.Elapsed.TotalMilliseconds;data=$Data})
}

function Assert-Confined([string]$Path, [string]$Parent) {
    if ($Path -match '[\x00-\x1f\x7f"]|(^|[\\/])\.\.([\\/]|$)' -or -not [IO.Path]::IsPathFullyQualified($Path)) { throw 'Unsafe absolute path.' }
    $canonical = [IO.Path]::GetFullPath($Path)
    if (-not $Path.Equals($canonical,[StringComparison]::OrdinalIgnoreCase) -or -not $canonical.StartsWith($Parent.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Canonical parent-plus-separator confinement failed.' }
    $cursor=$canonical
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if (((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point refused: $cursor" }
        }
        $cursor=[IO.Path]::GetDirectoryName($cursor)
    }
}

function Source-Proof {
    foreach ($relative in @(
        'AGENTS.md','input.json','inputs/runtime-protocol.md',
        '.agents/skills/ss-windows-runtime/SKILL.md','.agents/skills/ss-windows-runtime/agents/openai.yaml',
        'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1',
        'scripts/wo044/Capture-WO044NativeArguments.ps1','scripts/wo036/WO036-CampaignTools.psm1',
        'scripts/Tests/WO044JavaJarArgumentBoundary.Tests.ps1',
        'docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md',
        'docs/validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md',
        'docs/validation/J9-WO053-REVIEW-HARDENING-20260905.md')) {
        $path=Join-Path $caseRoot $relative
        Assert-Confined $path $caseRoot
        [pscustomobject]@{relativePath=$relative;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
    }
}

function Observe-TemporaryFiles {
    foreach ($root in @(Get-ChildItem -LiteralPath $runtimeRoot -Force)) {
        try {
            Assert-Confined $root.FullName $runtimeRoot
            if (-not $root.PSIsContainer -or $root.Name -cnotmatch '^WO044 Java JAR argument boundary [0-9a-f-]{36}$') { throw 'Unexpected runtime entry; preserved.' }
            if ($observedRoots.Add($root.FullName)) { Event 'TEMP_ROOT_OBSERVED' $root.FullName }
            foreach ($file in @(Get-ChildItem -LiteralPath $root.FullName -Force)) {
                Assert-Confined $file.FullName $root.FullName
                if ($file.PSIsContainer -or $file.Name -cnotmatch '^(synthetic application with spaces\.jar|(baseline-unquoted|qualified-01|qualified-02|qualified-no-space)\.(arguments\.json|stdout\.log|stderr\.log))$') { throw 'Unexpected capture entry; preserved.' }
                if ($observedFiles.Add($file.FullName)) { Event 'TEMP_FILE_OBSERVED' $file.FullName }
            }
        } catch {
            if (-not (Test-Path -LiteralPath $root.FullName)) { Event 'ROOT_REMOVED_DURING_POLL' $root.FullName; continue }
            throw
        }
    }
}

try {
    Assert-Confined $outputRoot $caseRoot
    Assert-Confined $PSScriptRoot $outputRoot
    Assert-Confined $runtimeRoot $outputRoot
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'execution.json')) { throw 'Harness execution evidence exists; no automatic rerun.' }
    if (-not $IsWindows -or $PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -ne 7) { throw 'Windows PowerShell 7 prerequisite missing.' }
    $entry=Get-Content -LiteralPath (Join-Path $caseRoot 'input.json') -Raw -Encoding utf8 | ConvertFrom-Json
    if ($entry.iterations -ne 2 -or $entry.wrapper_total_budget_ms -ne 60000 -or $entry.child_capture_timeout_ms -ne 10000 -or $entry.cleanup_timeout_ms -ne 5000) { throw 'Case execution bounds mismatch.' }
    $discovered=@(Get-Command pwsh.exe -CommandType Application -ErrorAction Stop)
    $pwsh=[IO.Path]::GetFullPath($discovered[0].Source)
    $self=[Diagnostics.Process]::GetCurrentProcess()
    if (-not $self.MainModule.FileName.Equals($pwsh,[StringComparison]::OrdinalIgnoreCase)) { throw 'Actual driver executable differs from resolved pwsh; no substitution.' }
    $table=[ordered]@{}
    foreach ($key in $PSVersionTable.Keys) { $table[$key]=[string]$PSVersionTable[$key] }
    $runtime=[ordered]@{PSVersionTable=$table;PSHOME=$PSHOME;actualDriverExecutable=$self.MainModule.FileName;driverPid=$PID;driverStartUtc=$self.StartTime.ToUniversalTime().ToString('O');driverFileVersion=$self.MainModule.FileVersionInfo.FileVersion;cwd=(Get-Location).Path;windows=[Environment]::OSVersion.VersionString;osArchitecture=[Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString();processArchitecture=[Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString();discoveredPwsh=@($discovered | ForEach-Object { @{path=$_.Source;version=$_.Version.ToString()} });consoleInputEncoding=[Console]::InputEncoding.WebName;consoleOutputEncoding=[Console]::OutputEncoding.WebName;powerShellOutputEncoding=$OutputEncoding.WebName;redirectedStreamDecoder='UTF-8 strict';evidenceEncoding='UTF-8 without BOM';captureJsonEncoding='UTF-8 without BOM, strict (frozen source)';parentLauncher='PowerShell exec tool -> Run-WRH01Harness.ps1 -> Invoke-WRH01Harness.ps1';cimParentObservation='Unavailable: HRESULT 0x80041003, retained in cim-observation-block.json'}
    Event 'RUNTIME_PREFLIGHT' $runtime
    $sourceBefore=@(Source-Proof)
    $pins=@{
        'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'='59996B14941700523007E1F6B0784B102D65E088569F95E882340250D43565E4'
        'scripts/wo044/Capture-WO044NativeArguments.ps1'='841428737A500FD9CCB1407D3C0822B56872F40B308568EE59AB48BAE88B7818'
        'scripts/wo036/WO036-CampaignTools.psm1'='43C7BC8EB242161729F6C18F8B0FEB67C46715CE911B27572E4BD13E5956BD74'
    }
    foreach ($source in $sourceBefore) { if ($pins.ContainsKey($source.relativePath) -and $pins[$source.relativePath] -cne $source.sha256) { throw 'Supplied executable differs from session initial fingerprint.' } }
    if (-not (Test-Path -LiteralPath $runtimeRoot)) { [void][IO.Directory]::CreateDirectory($runtimeRoot) }
    if (@(Get-ChildItem -LiteralPath $runtimeRoot -Force).Count -ne 0) { throw 'Runtime root must be empty; no deletion.' }
    Event 'RUNTIME_PREFLIGHT_EMPTY' $runtimeRoot
    $harness=Join-Path $caseRoot 'scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1'
    $arguments=@('-NoLogo','-NoProfile','-NonInteractive','-File',$harness,'-Iterations','2')
    $psi=[Diagnostics.ProcessStartInfo]::new()
    $psi.FileName=$pwsh
    foreach ($arg in $arguments) { $psi.ArgumentList.Add($arg) }
    $psi.WorkingDirectory=$outputRoot
    $psi.UseShellExecute=$false
    $psi.CreateNoWindow=$true
    $psi.RedirectStandardOutput=$true
    $psi.RedirectStandardError=$true
    $psi.StandardOutputEncoding=$utf8
    $psi.StandardErrorEncoding=$utf8
    $psi.Environment['TEMP']=$runtimeRoot
    $psi.Environment['TMP']=$runtimeRoot
    $command=[ordered]@{executable=$pwsh;arguments=$arguments;display=('"{0}" -NoLogo -NoProfile -NonInteractive -File "{1}" -Iterations 2' -f $pwsh,$harness);cwd=$outputRoot;childEnvironmentChanges=@{TEMP=$runtimeRoot;TMP=$runtimeRoot};UseShellExecute=$false;CreateNoWindow=$true;argumentSerialization='ProcessStartInfo.ArgumentList, paths passed as separate full arguments'}
    Event 'HARNESS_START_REQUEST' $command
    if ($clock.ElapsedMilliseconds -ge 5000) { throw 'Preflight launch allowance exhausted; no launch.' }
    $p=[Diagnostics.Process]::new()
    $p.StartInfo=$psi
    $launchMs=$clock.Elapsed.TotalMilliseconds
    if (-not $p.Start()) { throw 'Process start returned false.' }
    $handle=$p.Handle
    $identity=[ordered]@{pid=$p.Id;handle=$handle.ToInt64();startUtc=$p.StartTime.ToUniversalTime().ToString('O');actualExecutable=$p.MainModule.FileName;actualFileVersion=$p.MainModule.FileVersionInfo.FileVersion;instance=[guid]::NewGuid().ToString();instanceScope='Ownership record tied to retained handle; no extra harness argument'}
    $outTask=$p.StandardOutput.ReadToEndAsync()
    $errTask=$p.StandardError.ReadToEndAsync()
    Event 'HARNESS_PROCESS_CREATED' $identity
    if (-not $identity.actualExecutable.Equals($pwsh,[StringComparison]::OrdinalIgnoreCase)) { throw 'Actual child executable differs from resolved pwsh.' }
    while ($true) {
        if ($p.WaitForExit(20)) {
            $nativeCode=$p.ExitCode
            $exitMs=$clock.Elapsed.TotalMilliseconds
            Event 'NATIVE_EXIT_CODE_IMMEDIATE' $nativeCode
            break
        }
        Observe-TemporaryFiles
        if ($clock.ElapsedMilliseconds -ge 55000) { throw 'External timeout; reserve 5 seconds within 60-second total budget.' }
    }
} catch {
    $primaryFailure=[ordered]@{message=$_.Exception.Message;type=$_.Exception.GetType().FullName;errorId=$_.FullyQualifiedErrorId;scriptLine=$_.InvocationInfo.ScriptLineNumber}
    Event 'PRIMARY_FAILURE' $primaryFailure
} finally {
    $cleanupClock=[Diagnostics.Stopwatch]::StartNew()
    try {
        if ($null -ne $p -and -not $p.HasExited) {
            if ($null -eq $identity -or $p.Handle.ToInt64() -ne $identity.handle -or $p.StartTime.ToUniversalTime().ToString('O') -cne $identity.startUtc -or -not $p.MainModule.FileName.Equals($identity.actualExecutable,[StringComparison]::OrdinalIgnoreCase)) { throw 'Held process ownership not established; no termination.' }
            $p.Kill()
            if (-not $p.WaitForExit(5000)) { throw 'Owned harness did not exit within cleanup bound.' }
            $forcedCode=$p.ExitCode
            Event 'OWNED_HARNESS_FORCED_EXIT' $forcedCode
        }
        foreach ($task in @($outTask,$errTask)) {
            if ($null -ne $task -and -not $task.Wait([Math]::Max(1,5000-[int]$cleanupClock.ElapsedMilliseconds))) { throw 'Redirected output drain timeout.' }
        }
        if ($null -ne $outTask) { $stdout=$outTask.GetAwaiter().GetResult() }
        if ($null -ne $errTask) { $stderr=$errTask.GetAwaiter().GetResult() }
        Assert-Confined $runtimeRoot $outputRoot
        $remaining=@()
        if (Test-Path -LiteralPath $runtimeRoot) { $remaining=@(Get-ChildItem -LiteralPath $runtimeRoot -Force | Select-Object FullName,Attributes) }
        $postflight=[ordered]@{runtimeRoot=$runtimeRoot;remainingEntries=$remaining;remainingCount=$remaining.Count;observedRootChecks=@($observedRoots | ForEach-Object { @{path=$_;exists=(Test-Path -LiteralPath $_)} });harnessExitByHeldHandle=($null -ne $p -and $p.HasExited);harnessPid=$(if($null -ne $identity){$identity.pid}else{$null});captureProcessPostflight='Frozen harness checks its exact owned PIDs; their numeric identities/individual codes are not emitted or archived by this driver';parentTempUnchanged=($env:TEMP -ceq $parentTemp);parentTmpUnchanged=($env:TMP -ceq $parentTmp)}
        Event 'INDEPENDENT_POSTFLIGHT' $postflight
        if ($remaining.Count -ne 0) { throw 'Unexpected residue preserved; no recursive deletion.' }
        $sourceAfter=@(Source-Proof)
        if ($sourceBefore.Count -eq 0) { throw 'Source baseline absent; equality not established.' }
        if (($sourceBefore | ConvertTo-Json -Compress) -cne ($sourceAfter | ConvertTo-Json -Compress)) { throw 'Allowed source fingerprints differ.' }
        if (-not $postflight.parentTempUnchanged -or -not $postflight.parentTmpUnchanged) { throw 'Parent TEMP/TMP unexpectedly changed.' }
        if ($null -ne $forcedCode) { throw 'Forced wrapper exit: capture child exit cannot be established independently.' }
        $cleanupResult='PASS_RUNTIME_EMPTY_HARNESS_EXITED'
    } catch {
        $cleanupFailure=[ordered]@{message=$_.Exception.Message;type=$_.Exception.GetType().FullName;errorId=$_.FullyQualifiedErrorId;scriptLine=$_.InvocationInfo.ScriptLineNumber}
        $cleanupResult='FAIL_OR_NOT_ESTABLISHED'
    }
    $cleanupClock.Stop()
    Event 'CLEANUP_FINISHED' @{result=$cleanupResult;durationMs=$cleanupClock.Elapsed.TotalMilliseconds;failure=$cleanupFailure}
    if ($null -ne $p) { $p.Dispose() }
}

$pass=$null -ne $nativeCode -and $nativeCode -eq 0 -and $null -eq $primaryFailure -and $cleanupResult -eq 'PASS_RUNTIME_EMPTY_HARNESS_EXITED' -and $stdout.Contains('WO044_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED') -and $stdout.Contains('WO044_QUALIFIED_ITERATIONS=2') -and $clock.ElapsedMilliseconds -lt 60000
$driverExit=if($pass){0}else{1}
$result=[ordered]@{case='WR-H01';statuses=@('EXPERIMENTAL','LOCAL_ONLY','NOT_PRODUCTION_APPROVED','NO_CRITICAL_DEPENDENCY');startedUtc=$startUtc;endedUtc=[DateTime]::UtcNow.ToString('O');harnessExitCode=$nativeCode;forcedHarnessExitCode=$forcedCode;driverExitCode=$driverExit;primaryFailure=$primaryFailure;cleanupResult=$cleanupResult;cleanupFailure=$cleanupFailure;harnessDurationMs=$(if($null -ne $exitMs){$exitMs-$launchMs}else{$null});durationAtEvidenceMs=$clock.Elapsed.TotalMilliseconds;stopwatchFrequency=[Diagnostics.Stopwatch]::Frequency;budgets=@{totalMs=60000;externalCutoffMs=55000;captureMs=10000;cleanupMs=5000};runtime=$runtime;command=$command;identity=$identity;postflight=$postflight;sourceBefore=$sourceBefore;sourceAfter=$sourceAfter;rootsObserved=@($observedRoots);filesObserved=@($observedFiles | Sort-Object);events=$events;limitations=@('The frozen child does not print PSVersionTable: actual child executable and file version are observed; driver PSVersionTable is from the same verified executable.','Capture JSON and per-capture logs are deleted by the frozen harness. Its PASS validates each native capture exit as zero, but individual numeric process identities and timings are not in its transcript.','CIM unavailable; no independent child process inventory. Exit of outer harness is proved with its retained handle.','Only one harness invocation; previous driver failed during optional CIM preflight before launching any harness.','Current Git SHA not read: outside allowed files. Executables pinned to supplied bytes as observed before execution, with before/after SHA-256.')}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution.stdout.txt'),$stdout,$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution.stderr.txt'),$stderr,$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution.json'),($result | ConvertTo-Json -Depth 14),$utf8)
$display=if($null -ne $command){$command.display}else{'NOT_STARTED'}
$transcript=@("STARTED_UTC=$startUtc","COMMAND=$display",'--- STDOUT ---',$stdout,'--- STDERR ---',$stderr,"NATIVE_HARNESS_EXIT_CODE=$nativeCode","FORCED_HARNESS_EXIT_CODE=$forcedCode","DRIVER_EXIT_CODE=$driverExit","HARNESS_DURATION_MS=$($result.harnessDurationMs)","DURATION_AT_EVIDENCE_MS=$($result.durationAtEvidenceMs)","CLEANUP=$cleanupResult") -join "`r`n"
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution-transcript.txt'),$transcript,$utf8)
Write-Output $transcript
if ($null -ne $primaryFailure) { Write-Output ($primaryFailure | ConvertTo-Json -Compress) }
if ($null -ne $cleanupFailure) { Write-Output ($cleanupFailure | ConvertTo-Json -Compress) }
exit $driverExit
