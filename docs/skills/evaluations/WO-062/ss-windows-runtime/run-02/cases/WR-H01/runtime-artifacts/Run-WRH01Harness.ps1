Set-StrictMode -Version 3.0
$ErrorActionPreference='Stop'
$watch=[Diagnostics.Stopwatch]::StartNew()
$start=[DateTime]::UtcNow.ToString('O')
$driver=Join-Path $PSScriptRoot 'Invoke-WRH01Harness.ps1'
& $driver
$driverExit=$LASTEXITCODE
$watch.Stop()
$evidence=[ordered]@{driverInvocation=('& ''{0}''' -f $driver);driverScriptExitCode=$driverExit;codeMeaning='Driver script exit statement, copied immediately; native harness Process.ExitCode stored separately';startUtc=$start;returnUtc=[DateTime]::UtcNow.ToString('O');durationMs=$watch.Elapsed.TotalMilliseconds}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'execution-outer-return.json'),($evidence | ConvertTo-Json),[Text.UTF8Encoding]::new($false,$true))
Write-Output "WR_DRIVER_RETURN_CODE=$driverExit"
Write-Output "WR_OUTER_DURATION_MS=$($evidence.durationMs)"
exit $driverExit
