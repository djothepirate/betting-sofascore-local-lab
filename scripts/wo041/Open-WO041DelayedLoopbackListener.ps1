[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1024, 65535)]
    [int]$Port,

    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 5000)]
    [int]$DelayMilliseconds,

    [Parameter(Mandatory = $true)]
    [ValidateRange(500, 10000)]
    [int]$HoldMilliseconds,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')]
    [string]$InstanceToken
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Start-Sleep -Milliseconds $DelayMilliseconds
$listener = [Net.Sockets.TcpListener]::new(
    [Net.IPAddress]::Parse('127.0.0.1'),
    $Port)
try {
    $listener.Start(1)
    Start-Sleep -Milliseconds $HoldMilliseconds
}
finally {
    $listener.Stop()
    $listener.Dispose()
}
