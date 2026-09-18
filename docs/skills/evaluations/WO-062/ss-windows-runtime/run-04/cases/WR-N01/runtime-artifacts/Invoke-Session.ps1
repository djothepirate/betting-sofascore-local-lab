$ErrorActionPreference='Stop'
$utf8=New-Object Text.UTF8Encoding($false)
$powerShellPath=(Get-Command powershell.exe -CommandType Application -ErrorAction Stop).Source
$driver=Join-Path $PSScriptRoot 'Invoke-WRN01.ps1'
$timer=[Diagnostics.Stopwatch]::StartNew()
$psi=New-Object Diagnostics.ProcessStartInfo
$psi.FileName=$powerShellPath
$psi.Arguments='-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+$driver+'"'
$psi.WorkingDirectory=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$psi.UseShellExecute=$false
$psi.CreateNoWindow=$true
$psi.RedirectStandardOutput=$true
$psi.RedirectStandardError=$true
$psi.StandardOutputEncoding=$utf8
$psi.StandardErrorEncoding=$utf8
$p=New-Object Diagnostics.Process
$p.StartInfo=$psi
if (-not $p.Start()) { throw 'Conductor did not start' }
$handle=$p.Handle
$identity=[ordered]@{event='conductor_started';utc=[DateTime]::UtcNow.ToString('o');pid=$p.Id;handle=$handle.ToInt64();
 creation_utc=$p.StartTime.ToUniversalTime().ToString('o');image=$p.MainModule.FileName;
 command=('"'+$powerShellPath+'" '+$psi.Arguments);cwd=$psi.WorkingDirectory}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'launch.json'),($identity|ConvertTo-Json),$utf8)
$outTask=$p.StandardOutput.ReadToEndAsync()
$errTask=$p.StandardError.ReadToEndAsync()
if (-not $p.WaitForExit(45000)) { throw 'External 45s conductor deadline exceeded; no broad process termination authorized' }
$nativeCode=$p.ExitCode
$elapsed=$timer.Elapsed.TotalMilliseconds
$return=[ordered]@{event='conductor_return_observed';utc=[DateTime]::UtcNow.ToString('o');native_code_immediate=$nativeCode;monotonic_ms=$elapsed;handle_exit_confirmed=$true}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'return.json'),($return|ConvertTo-Json),$utf8)
if (-not $outTask.Wait(1000) -or -not $errTask.Wait(1000)) { throw 'Conductor output not drained within 1s' }
$outText=$outTask.GetAwaiter().GetResult()
$errText=$errTask.GetAwaiter().GetResult()
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'conductor-stdout.txt'),$outText,$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'conductor-stderr.txt'),$errText,$utf8)
$p.Dispose()
Write-Output $outText
Write-Output $errText
Write-Output ('CONDUCTOR_NATIVE_CODE='+$nativeCode+'; ELAPSED_MS='+$elapsed)
exit $nativeCode