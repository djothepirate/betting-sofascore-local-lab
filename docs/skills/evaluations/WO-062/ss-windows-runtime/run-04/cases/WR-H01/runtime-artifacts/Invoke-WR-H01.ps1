[CmdletBinding()]
param()
Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$watch = [Diagnostics.Stopwatch]::StartNew()
$caseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $caseRoot 'output/runtime'))
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$evidence = [ordered]@{ case='WR-H01'; startedUtc=[DateTime]::UtcNow.ToString('O'); budgetMs=60000; stage='PREFLIGHT'; result='NOT_RUN'; nativeExitCode=$null; forcedExitCode=$null; cleanup='NOT_OBSERVED'; events=@() }
$child = $null
$retainedHandle = $null
$nativeArgs = $null
$stdoutTask = $null
$stderrTask = $null
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$seenRoots = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$hashes = @{}
$parentTemp = $env:TEMP
$parentTmp = $env:TMP
function Event([string]$Name) { $evidence.events += [ordered]@{name=$Name; utc=[DateTime]::UtcNow.ToString('O'); elapsedMs=$watch.Elapsed.TotalMilliseconds} }
function Assert-DirectPath([string]$Path) {
    $canonical = [IO.Path]::GetFullPath($Path)
    if ($Path -match '[\x00-\x1f\x7f"]|(^|[\\/])\.\.([\\/]|$)' -or $canonical -cne $Path -or -not $canonical.StartsWith($caseRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unconfined path' }
    $ancestor=$canonical
    while ($ancestor) {
        if (Test-Path -LiteralPath $ancestor) { if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse point rejected' } }
        $ancestor=[IO.Path]::GetDirectoryName($ancestor)
    }
}
function Observe-TemporaryResources {
    foreach ($root in @(Get-ChildItem -LiteralPath $runtimeRoot -Directory -Force)) {
        Assert-DirectPath $root.FullName
        if ($root.Name -cnotmatch '^WO044 Java JAR argument boundary [0-9a-f-]{36}$') { throw 'Unexpected temporary root' }
        [void]$seenRoots.Add($root.FullName)
        foreach ($item in @(Get-ChildItem -LiteralPath $root.FullName -Force -ErrorAction SilentlyContinue)) { [void]$seen.Add($item.FullName) }
    }
}
try {
    Assert-DirectPath $runtimeRoot
    Assert-DirectPath $PSScriptRoot
    if (@(Get-ChildItem -LiteralPath $runtimeRoot -Force).Count) { throw 'Runtime root must be empty' }
    if (-not $IsWindows -or $PSVersionTable.PSVersion.Major -ne 7) { throw 'Windows PowerShell 7 required; no runtime substitution' }
    $self = [Diagnostics.Process]::GetCurrentProcess()
    $pwsh = @(Get-Command pwsh.exe -CommandType Application -ErrorAction Stop)[0].Source
    if (-not $self.MainModule.FileName.Equals($pwsh,[StringComparison]::OrdinalIgnoreCase)) { throw 'Driver image differs from resolved PowerShell; effective runtime not established' }
    $versions=[ordered]@{}
    foreach ($entry in $PSVersionTable.GetEnumerator()) { $versions[$entry.Key]=[string]$entry.Value }
    $evidence.runtime=[ordered]@{psVersionTable=$versions; psHome=$PSHOME; executable=$self.MainModule.FileName; executableVersion=$self.MainModule.FileVersionInfo.FileVersion; driverPid=$PID; architecture=[Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString(); os=[Runtime.InteropServices.RuntimeInformation]::OSDescription; cwd=(Get-Location).Path; childCwd=$runtimeRoot; stdoutDecoder='UTF-8 strict'; evidenceEncoding='UTF-8 without BOM'; consoleOutputEncoding=[Console]::OutputEncoding.WebName; outputEncoding=$OutputEncoding.WebName; childVersionEvidence='Same observed executable as this running PS7 driver; frozen child script not instrumented'}
    $sourcePaths=@('scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1','scripts/wo044/Capture-WO044NativeArguments.ps1','scripts/wo036/WO036-CampaignTools.psm1')
    foreach ($relative in $sourcePaths) {
        $source=Join-Path $caseRoot $relative
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing frozen dependency: $relative" }
        $hashes[$relative]=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    }
    $scriptPath=[IO.Path]::GetFullPath((Join-Path $caseRoot $sourcePaths[0]))
    $nativeArgs=@('-NoLogo','-NoProfile','-NonInteractive','-File',$scriptPath,'-Iterations','2')
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$pwsh
    $info.UseShellExecute=$false
    $info.CreateNoWindow=$true
    $info.WorkingDirectory=$runtimeRoot
    $info.RedirectStandardOutput=$true
    $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$utf8
    $info.StandardErrorEncoding=$utf8
    foreach ($argument in $nativeArgs) { $info.ArgumentList.Add($argument) }
    $info.Environment['TEMP']=$runtimeRoot
    $info.Environment['TMP']=$runtimeRoot
    $evidence.command=[ordered]@{fileName=$pwsh; argumentList=$nativeArgs; display=('"'+$pwsh+'" -NoLogo -NoProfile -NonInteractive -File "'+$scriptPath+'" -Iterations 2'); childEnvironmentOverride=@{TEMP=$runtimeRoot; TMP=$runtimeRoot}; useShellExecute=$false; createNoWindow=$true}
    $evidence.preflightMs=$watch.Elapsed.TotalMilliseconds
    if ($watch.ElapsedMilliseconds -ge 10000) { throw 'Preflight leaves insufficient budget for frozen harness and cleanup' }
    $evidence.stage='PROCESS_CREATE'
    $child=[Diagnostics.Process]::new()
    $child.StartInfo=$info
    $launchAt=$watch.Elapsed.TotalMilliseconds
    if (-not $child.Start()) { throw 'Process.Start returned false' }
    $retainedHandle=$child.SafeHandle
    Event 'PROCESS_STARTED'
    $evidence.child=[ordered]@{pid=$child.Id; startedUtc=$child.StartTime.ToUniversalTime().ToString('O'); image=$child.MainModule.FileName; handle=$retainedHandle.DangerousGetHandle().ToInt64(); instance=[guid]::NewGuid().ToString(); instanceScope='Driver record, not an argument added to frozen harness'; creatorPid=$PID}
    $stdoutTask=$child.StandardOutput.ReadToEndAsync()
    $stderrTask=$child.StandardError.ReadToEndAsync()
    $evidence.stage='CHILD_WAIT'
    $exited=$false
    while ($watch.ElapsedMilliseconds -lt 55000) {
        $remaining=[Math]::Max(1,[Math]::Min(25,55000-$watch.ElapsedMilliseconds))
        if ($child.WaitForExit([int]$remaining)) {
            $evidence.nativeExitCode=$child.ExitCode
            $exited=$true
            Event 'NATIVE_EXIT_OBSERVED'
            break
        }
        Observe-TemporaryResources
    }
    $evidence.harnessElapsedMs=$watch.Elapsed.TotalMilliseconds-$launchAt
    if (-not $exited) { $evidence.result='TIMEOUT'; throw 'Outer wait expired; 5 seconds reserved for exact child cleanup' }
    $evidence.result=if ($evidence.nativeExitCode -eq 0) {'NATIVE_EXIT_ZERO'} else {'NATIVE_EXIT_NONZERO'}
    $evidence.stage='CAPTURE_OUTPUT'
    $streamBudget=[Math]::Max(0,60000-$watch.ElapsedMilliseconds)
    if (-not [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdoutTask,$stderrTask),[int]$streamBudget)) { throw 'Stream drain deadline exceeded' }
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'harness.stdout.txt'),$stdoutTask.Result,$utf8)
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'harness.stderr.txt'),$stderrTask.Result,$utf8)
    $evidence.stage='SOURCE_INTEGRITY'
    $evidence.sourcesUnchanged=$true
    foreach ($relative in $sourcePaths) { if ((Get-FileHash -LiteralPath (Join-Path $caseRoot $relative) -Algorithm SHA256).Hash -cne $hashes[$relative]) { $evidence.sourcesUnchanged=$false } }
    $evidence.sourceVerification='Allowlisted supplied files used in place; byte hashes compared before/after. No independent reference hash supplied.'
}
catch {
    $evidence.error=[ordered]@{stage=$evidence.stage; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; id=$_.FullyQualifiedErrorId; line=$_.InvocationInfo.ScriptLineNumber; stack=$_.ScriptStackTrace}
    if ($evidence.result -eq 'NOT_RUN') { $evidence.result='BLOCKED_OR_DRIVER_ERROR' }
}
finally {
    $evidence.stage='CLEANUP'
    if ($null -ne $child -and $null -ne $retainedHandle) {
        if (-not $child.HasExited) {
            if ($null -ne $evidence.child -and -not $retainedHandle.IsClosed -and $child.Id -eq $evidence.child.pid -and $child.StartTime.ToUniversalTime().ToString('O') -ceq $evidence.child.startedUtc -and $child.MainModule.FileName -ceq $evidence.child.image) {
                $child.Kill()
                $cleanupBudget=[Math]::Max(0,[Math]::Min(5000,60000-$watch.ElapsedMilliseconds))
                if ($child.WaitForExit([int]$cleanupBudget)) { $evidence.forcedExitCode=$child.ExitCode; $evidence.cleanup='EXACT_OUTER_PROCESS_STOPPED_DESCENDANTS_NOT_INDEPENDENTLY_ESTABLISHED' }
                else { $evidence.cleanup='EXACT_OUTER_PROCESS_EXIT_NOT_OBSERVED' }
            } else { $evidence.cleanup='OWNERSHIP_NOT_ESTABLISHED_NO_STOP' }
        } else { $evidence.cleanup='OUTER_PROCESS_EXIT_CONFIRMED_BY_RETAINED_HANDLE' }
        $evidence.outerProcessExited=$child.HasExited
        $child.Dispose()
    }
    $evidence.observedTemporaryRoots=@($seenRoots)
    $evidence.observedTemporaryFiles=@($seen)
    $evidence.temporaryInventoryScope='Names observed while running; exact child identities and file ownership managed internally by unchanged harness; raw capture files intentionally not retained'
    $evidence.runtimeEntriesAfterReturn=@(Get-ChildItem -LiteralPath $runtimeRoot -Force | Select-Object Name,FullName,Attributes)
    $evidence.parentTempTmpUnchanged=($parentTemp -ceq $env:TEMP -and $parentTmp -ceq $env:TMP)
    Event 'DRIVER_RETURN'
    $evidence.totalElapsedMs=$watch.Elapsed.TotalMilliseconds
    $evidence.withinBudget=($watch.ElapsedMilliseconds -lt 60000)
    $evidence.stage='COMPLETE'
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution.json'),($evidence | ConvertTo-Json -Depth 12),$utf8)
    $available='WR_EVIDENCE_AVAILABLE UTC='+[DateTime]::UtcNow.ToString('O')
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'evidence-available.txt'),$available,$utf8)
    Write-Output $available
    Write-Output ('WR_DRIVER_RESULT='+$evidence.result)
    Write-Output ('WR_NATIVE_EXIT_CODE='+$evidence.nativeExitCode)
    Write-Output ('WR_TOTAL_ELAPSED_MS='+$evidence.totalElapsedMs)
}
