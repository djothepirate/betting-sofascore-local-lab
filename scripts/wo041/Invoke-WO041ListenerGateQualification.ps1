[CmdletBinding()]
param(
    [ValidateRange(1, 20)]
    [int]$Iterations = 10,

    [ValidateRange(1024, 65535)]
    [int]$Port = 8087
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) {
    throw 'WO-041 host qualification requires Windows.'
}

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $repositoryRoot 'scripts\wo036\WO036-CampaignTools.psm1'
$helperPath = Join-Path $PSScriptRoot 'Open-WO041DelayedLoopbackListener.ps1'
$pwshPath = Join-Path $PSHOME 'pwsh.exe'

foreach ($path in @($modulePath, $helperPath, $pwshPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'WO-041 qualification dependency is unavailable.'
    }
}

$module = Import-Module $modulePath -Force -PassThru
$delays = @(0, 1, 25, 100, 249, 499, 501, 750, 999, 1250)
$successfulIterations = 0
$ownedChildren = [Collections.Generic.List[object]]::new()

function Test-WO041PortIsFree {
    return [bool](& $module {
        param([int]$CandidatePort)
        Test-WO036PortIsFree -Port $CandidatePort
    } $Port)
}

function Stop-WO041ExactOwnedChild {
    param([Parameter(Mandatory = $true)][object]$Owned)

    $process = Get-Process -Id $Owned.ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return }
    $startTime = $process.StartTime.ToUniversalTime().ToString('O')
    $cim = Get-CimInstance Win32_Process -Filter (
        'ProcessId={0}' -f $Owned.ProcessId) -ErrorAction Stop
    $actualExecutable = [IO.Path]::GetFullPath([string]$cim.ExecutablePath)
    if ($startTime -cne $Owned.StartTimeUtc `
            -or -not $actualExecutable.Equals($pwshPath,
                [StringComparison]::OrdinalIgnoreCase) `
            -or [string]$cim.CommandLine -notlike "*$($Owned.InstanceToken)*" `
            -or [string]$cim.CommandLine -notlike "*$helperPath*") {
        throw 'WO-041 refused to terminate a process without exact ownership proof.'
    }
    Stop-Process -Id $Owned.ProcessId -Force -ErrorAction Stop
    [void]$process.WaitForExit(5000)
}

function Start-WO041OwnedListenerChild {
    param(
        [Parameter(Mandatory = $true)][int]$DelayMilliseconds,
        [Parameter(Mandatory = $true)][int]$HoldMilliseconds
    )

    if (-not (Test-WO041PortIsFree)) {
        throw 'WO-041 loopback qualification port is not free.'
    }
    $token = [guid]::NewGuid().ToString()
    $process = Start-Process -FilePath $pwshPath -ArgumentList @(
        '-NoLogo',
        '-NoProfile',
        '-NonInteractive',
        '-File', ('"{0}"' -f $helperPath),
        '-Port', $Port,
        '-DelayMilliseconds', $DelayMilliseconds,
        '-HoldMilliseconds', $HoldMilliseconds,
        '-InstanceToken', $token) -WindowStyle Hidden -PassThru
    $owned = [pscustomobject]@{
        ProcessId = $process.Id
        StartTimeUtc = $process.StartTime.ToUniversalTime().ToString('O')
        InstanceToken = $token
        Process = $process
    }
    $ownedChildren.Add($owned)
    return $owned
}

try {
    for ($index = 0; $index -lt $Iterations; $index++) {
        $delay = $delays[$index % $delays.Count]
        $owned = Start-WO041OwnedListenerChild -DelayMilliseconds $delay `
            -HoldMilliseconds 2000
        $ready = [bool](& $module {
            param([int]$CandidatePort, [int]$ExpectedProcessId)
            Wait-WO036LoopbackListener -Port $CandidatePort `
                -ProcessId $ExpectedProcessId
        } $Port $owned.ProcessId)
        if (-not $ready) {
            throw 'WO-041 exact delayed listener was rejected.'
        }
        if (-not $owned.Process.WaitForExit(6000)) {
            throw 'WO-041 exact delayed listener child did not exit in time.'
        }
        for ($attempt = 1; $attempt -le 20 -and -not (Test-WO041PortIsFree); $attempt++) {
            Start-Sleep -Milliseconds 100
        }
        if (-not (Test-WO041PortIsFree)) {
            throw 'WO-041 delayed listener remained after its owned child exited.'
        }
        $successfulIterations++
    }

    $conflictOwner = Start-WO041OwnedListenerChild -DelayMilliseconds 0 `
        -HoldMilliseconds 3000
    for ($attempt = 1; $attempt -le 20 -and (Test-WO041PortIsFree); $attempt++) {
        Start-Sleep -Milliseconds 100
    }
    if (Test-WO041PortIsFree) {
        throw 'WO-041 conflicting listener did not become observable.'
    }
    $conflictAccepted = [bool](& $module {
        param([int]$CandidatePort, [int]$WrongProcessId)
        Wait-WO036LoopbackListener -Port $CandidatePort `
            -ProcessId $WrongProcessId
    } $Port $PID)
    if ($conflictAccepted) {
        throw 'WO-041 listener owned by another process was accepted.'
    }
    if (-not $conflictOwner.Process.WaitForExit(6000)) {
        throw 'WO-041 conflicting listener child did not exit in time.'
    }
    for ($attempt = 1; $attempt -le 20 -and -not (Test-WO041PortIsFree); $attempt++) {
        Start-Sleep -Milliseconds 100
    }
    if (-not (Test-WO041PortIsFree)) {
        throw 'WO-041 conflicting listener remained after its owned child exited.'
    }
}
finally {
    foreach ($owned in $ownedChildren) {
        Stop-WO041ExactOwnedChild -Owned $owned
    }
}

$residualListeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port `
    -ErrorAction SilentlyContinue)
$residualProcesses = @($ownedChildren | Where-Object {
        $null -ne (Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue)
    })
if ($residualListeners.Count -ne 0 -or $residualProcesses.Count -ne 0) {
    throw 'WO-041 host qualification cleanup is incomplete.'
}

Write-Output 'WO041_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED'
Write-Output "WO041_DELAYED_EXACT_LISTENER_ITERATIONS=$Iterations"
Write-Output "WO041_DELAYED_EXACT_LISTENER_SUCCESSES=$successfulIterations"
Write-Output 'WO041_CONFLICTING_OWNER_REJECTED=YES'
Write-Output 'WO041_RESIDUAL_PROCESS_COUNT=0'
Write-Output 'WO041_RESIDUAL_LISTENER_COUNT=0'
Write-Output 'WO041_NETWORK_SCOPE=127.0.0.1_ONLY'
Write-Output 'WO041_PROVIDER_CALLS=0'
Write-Output 'WO041_RECEIVER_CALLS=0'
