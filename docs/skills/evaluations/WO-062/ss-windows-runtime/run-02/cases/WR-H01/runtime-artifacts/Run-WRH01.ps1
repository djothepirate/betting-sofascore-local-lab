Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$outerWatch = [Diagnostics.Stopwatch]::StartNew()
$outerUtc = [DateTime]::UtcNow.ToString('O')
$driverPath = Join-Path $PSScriptRoot 'Invoke-WRH01.ps1'
& $driverPath
$driverScriptExitCode = $LASTEXITCODE
$outerWatch.Stop()
$outer = [ordered]@{
    command = '& ''output/runtime-artifacts/Run-WRH01.ps1''; caller copies $LASTEXITCODE immediately and exits with that code'
    driverInvocation = ('& ''{0}''' -f $driverPath)
    driverScriptExitCode = $driverScriptExitCode
    codeMeaning = 'Exit statement of driver script, captured immediately; harness native Process.ExitCode is separately recorded in run.json'
    startUtc = $outerUtc
    returnUtc = [DateTime]::UtcNow.ToString('O')
    durationMs = $outerWatch.Elapsed.TotalMilliseconds
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'outer-return.json'),($outer | ConvertTo-Json),[Text.UTF8Encoding]::new($false,$true))
Write-Output ('WR_DRIVER_RETURN_CODE={0}' -f $driverScriptExitCode)
Write-Output ('WR_OUTER_DURATION_MS={0}' -f $outer.durationMs)
exit $driverScriptExitCode
