param([Parameter(Mandatory=$true)][string]$OutputRoot)
$ErrorActionPreference = 'Stop'
$watch = [Diagnostics.Stopwatch]::StartNew()
if ($PSVersionTable.PSEdition -ne 'Desktop' -or $PSVersionTable.PSVersion.Major -ne 5) { throw 'Windows PowerShell 5.1 Desktop required' }
$output = [IO.Path]::GetFullPath($OutputRoot)
$allowed = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../../..'))
if (-not $output.StartsWith($allowed.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Output outside worktree' }
if (-not (Test-Path -LiteralPath $output -PathType Container)) { throw 'Output directory must exist' }
$probeIdentity = [Guid]::NewGuid().ToString()
$temporary = Join-Path $output ('ownership probe ' + $probeIdentity)
if (Test-Path -LiteralPath $temporary) { throw 'Fresh temporary root required' }
$ancestor = Get-Item -LiteralPath $output
while ($null -ne $ancestor) { if (($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse path refused' }; $ancestor=$ancestor.Parent }
$candidates = @(Get-Command java.exe -All -CommandType Application | ForEach-Object {
    $item=Get-Item -LiteralPath $_.Source
    [pscustomobject]@{path=$_.Source; originalFilename=$item.VersionInfo.OriginalFilename; version=$item.VersionInfo.FileVersion; bytes=$item.Length; sha256=(Get-FileHash -LiteralPath $_.Source -Algorithm SHA256).Hash.ToLowerInvariant()}
})
$direct = @($candidates | Where-Object { $_.originalFilename -ceq 'java.exe' -and $_.version -like '25.*' })
if ($direct.Count -ne 1) { throw 'Unambiguous discovered direct Java 25 executable required' }
$javaPath=[IO.Path]::GetFullPath($direct[0].path)
$ancestor=Get-Item -LiteralPath (Split-Path -Path $javaPath -Parent)
while ($null -ne $ancestor) { if (($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Direct executable path must not traverse a reparse point' }; $ancestor=$ancestor.Parent }
$fixture=Join-Path $PSScriptRoot 'OwnershipProbe.java'
$fixtureHash=(Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
$process=$null;$processStarted=$false;$processExited=$false;$reader=$null;$stderrTask=$null;$lines=[Collections.Generic.List[string]]::new();$result='BLOCKED';$errorText=$null;$identity=$null;$exitCode=$null;$cleanup='NOT_STARTED';$releaseSent=$false
New-Item -ItemType Directory -Path $temporary | Out-Null
$temp=Join-Path $temporary 'temp';New-Item -ItemType Directory -Path $temp | Out-Null
function Quote-OwnArgument([string]$value) { if ($value -match '["\r\n\x00]') { throw 'Ambiguous argument' }; return '"'+$value+'"' }
try {
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$javaPath;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.WorkingDirectory=$temporary;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false,$true);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false,$true)
    $info.Arguments=(@('-XX:-UsePerfData',('-Djava.io.tmpdir='+$temp),('-Duser.home='+$temporary),$fixture,$probeIdentity) | ForEach-Object { Quote-OwnArgument $_ }) -join ' '
    $info.EnvironmentVariables['TEMP']=$temp;$info.EnvironmentVariables['TMP']=$temp
    foreach ($name in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) { $info.EnvironmentVariables.Remove($name) }
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    if (-not $process.Start()) { throw 'Process start failed' }
    $processStarted=$true
    $retainedHandle=$process.Handle;$childId=$process.Id;$childStart=$process.StartTime.ToUniversalTime().ToString('o')
    $stderrTask=$process.StandardError.ReadToEndAsync()
    $readDeadline=$watch.ElapsedMilliseconds+5000
    while ($watch.ElapsedMilliseconds -lt $readDeadline) {
        $reader=$process.StandardOutput.ReadLineAsync()
        $remaining=[Math]::Max(1,$readDeadline-$watch.ElapsedMilliseconds)
        if (-not $reader.Wait([int]$remaining)) { throw 'Ownership readiness deadline reached' }
        $line=$reader.Result;if ($null -eq $line) { throw 'Output ended before readiness' };$lines.Add($line)
        if ($line -ceq 'OWNER_READY') { break }
    }
    if (-not $lines.Contains('OWNER_READY')) { throw 'Readiness absent' }
    $values=@{};foreach ($line in $lines) { $pair=$line -split '=',2;if ($pair.Length -eq 2) { $values[$pair[0]]=$pair[1] } }
    $observedImage=$process.MainModule.FileName
    $identity=[ordered]@{conductorPid=$PID;requestedExecutable=$javaPath;observedImage=$observedImage;retainedHandle=$retainedHandle.ToInt64();retainedPid=$childId;retainedStartUtc=$childStart;fixturePid=$values['OWNER_PID'];fixtureParentPid=$values['OWNER_PARENT_PID'];fixtureCommand=$values['OWNER_COMMAND'];fixtureStartUtc=$values['OWNER_STARTED'];uuid=$values['OWNER_UUID'];javaVersion=$values['OWNER_JAVA_VERSION'];aliveDuringCheck=(-not $process.HasExited)}
    if ($values['OWNER_UUID'] -cne $probeIdentity -or [int]$values['OWNER_PID'] -ne $childId -or [int]$values['OWNER_PARENT_PID'] -ne $PID -or -not $observedImage.Equals($javaPath,[StringComparison]::OrdinalIgnoreCase) -or -not $values['OWNER_COMMAND'].Equals($javaPath,[StringComparison]::OrdinalIgnoreCase) -or -not $identity.aliveDuringCheck) { throw 'Ownership mismatch' }
    if ($values['OWNER_JAVA_VERSION'] -notmatch '^25[.+]') { throw 'Actual Java 25 required' }
    $process.StandardInput.WriteLine('RELEASE');$process.StandardInput.Flush();$releaseSent=$true
    if (-not $process.WaitForExit(5000)) { throw 'Natural release did not exit within deadline' }
    $exitCode=$process.ExitCode
    $tail=$process.StandardOutput.ReadToEnd();if ($tail) { $lines.Add($tail.TrimEnd()) }
    if ($exitCode -ne 0 -or -not $lines.Contains('OWNER_RELEASED')) { throw 'Natural release evidence incomplete' }
    $result='PASS_OWNERSHIP_ONLY'
} catch { $errorText=$_.Exception.Message }
finally {
    if ($processStarted) {
        if (-not $process.HasExited) {
            if (-not $releaseSent) { try { $process.StandardInput.WriteLine('RELEASE');$process.StandardInput.Flush();$releaseSent=$true } catch {} }
            $remaining=[Math]::Max(1,20000-$watch.ElapsedMilliseconds)
            [void]$process.WaitForExit([int][Math]::Min(10000,$remaining))
        }
        if ($process.HasExited) { $processExited=$true;if ($null -eq $exitCode) { $exitCode=$process.ExitCode };$process.Dispose() }
        else { $result='BLOCKED';$errorText='Owned process did not return; no global stop attempted' }
    }
    try {
        if ($processStarted -and -not $processExited) { throw 'Process exit not established' }
        $entries=@(Get-ChildItem -LiteralPath $temporary -Recurse -Force)
        $unexpected=@($entries | Where-Object { -not $_.PSIsContainer -or $_.FullName -ne $temp })
        if ($unexpected.Count -ne 0) { throw 'Unexpected residue retained' }
        [IO.Directory]::Delete($temp,$false);[IO.Directory]::Delete($temporary,$false);$cleanup='PASS_EMPTY_ROOT_REMOVED'
    } catch { $cleanup='BLOCKED: '+$_.Exception.Message;$result='BLOCKED' }
}
$stderr=if ($stderrTask -and $stderrTask.IsCompleted) { $stderrTask.Result } else { $null }
$record=[ordered]@{schema='wo062-java-ownership-preflight-v1';observedAtUtc=[DateTime]::UtcNow.ToString('o');result=$result;error=$errorText;scope='Ownership prerequisite only. No failure/sleep modes, no forced termination, no model call.';powerShellVersion=$PSVersionTable.PSVersion.ToString();edition=$PSVersionTable.PSEdition;discovery=$candidates;fixtureSha256=$fixtureHash;identity=$identity;stdout=@($lines);stderr=$stderr;exitCode=$exitCode;releaseSent=$releaseSent;cleanup=$cleanup;temporaryRoot=$temporary;rootAbsent=(-not (Test-Path -LiteralPath $temporary));durationMs=$watch.Elapsed.TotalMilliseconds;processesStopped=0;oldWRN01ResidueTouched=$false}
[IO.File]::WriteAllText((Join-Path $output 'ownership-proof.json'),($record|ConvertTo-Json -Depth 7)+"`n",[Text.UTF8Encoding]::new($false))
$record|ConvertTo-Json -Depth 7
if ($result -ne 'PASS_OWNERSHIP_ONLY') { exit 2 }
