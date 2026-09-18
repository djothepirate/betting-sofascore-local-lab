$ErrorActionPreference = 'Stop'
$encoding = New-Object Text.UTF8Encoding($false)
$psExe = (Get-Command -Name powershell.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$scriptFile = Join-Path $PSScriptRoot 'Invoke-WRN01.ps1'
$outerProcess = [Diagnostics.Process]::GetCurrentProcess()
$outer = [ordered]@{
 startedUtc = [DateTime]::UtcNow.ToString('o')
 launcherPid = $PID
 launcherCreationUtc = $outerProcess.StartTime.ToUniversalTime().ToString('o')
 launcherExecutable = $outerProcess.MainModule.FileName
 launcherPSEdition = $PSVersionTable.PSEdition
 launcherPSVersion = $PSVersionTable.PSVersion.ToString()
 workingDirectory = (Get-Location).Path
 conductorExecutable = $psExe
 command = '"' + $psExe + '" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $scriptFile + '"'
 environmentPolicy = 'inherited; parent environment unmodified; Java environment isolation inside conductor'
}
$transcriptPath = Join-Path $PSScriptRoot 'conductor-transcript.txt'
Start-Transcript -LiteralPath $transcriptPath -NoClobber | Out-Null
$watch = [Diagnostics.Stopwatch]::StartNew()
& $psExe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $scriptFile
$childExitCode = $LASTEXITCODE
$outer.nativeExitCaptureUtc = [DateTime]::UtcNow.ToString('o')
$outer.nativeExitCode = $childExitCode
$outer.elapsedMs = $watch.Elapsed.TotalMilliseconds
$outer.captureRule = '$LASTEXITCODE copied as the first statement after native invocation'
Stop-Transcript | Out-Null
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'outer-result.json'), (ConvertTo-Json -InputObject $outer -Depth 8), $encoding)
Write-Output ('WR_CONDUCTOR_RETURNED code=' + $childExitCode + ' elapsedMs=' + $outer.elapsedMs)
exit $childExitCode