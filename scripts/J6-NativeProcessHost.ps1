[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GateName,

    [Parameter(Mandatory = $true)]
    [string]$TargetFilePath,

    [Parameter(Mandatory = $true)]
    [string]$ArgumentPayloadBase64,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^J6NativeStartup_[0-9a-f]{32}$')]
    [string]$StartupPipeName,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{32}$')]
    [string]$StartupNonce,

    [Parameter(Mandatory = $true)]
    [ValidateRange(100, 30000)]
    [int]$StartupTimeoutMilliseconds,

    [Parameter(DontShow = $true)]
    [ValidateRange(0, 30000)]
    [int]$QualificationStartupHandshakeDelayMilliseconds = 0
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($IsWindows -and -not ('J6NativeHostControlGuard' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public sealed class J6NativeHostControlGuard : IDisposable
{
    private delegate bool HandlerRoutine(uint controlType);
    private readonly HandlerRoutine handler;
    private bool installed;

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetConsoleCtrlHandler(
        HandlerRoutine handlerRoutine,
        bool add);

    public J6NativeHostControlGuard()
    {
        handler = HandleControl;
        installed = SetConsoleCtrlHandler(handler, true);
    }

    public bool Installed => installed;

    private static bool HandleControl(uint controlType)
    {
        // CTRL_C_EVENT and CTRL_BREAK_EVENT are routed through the parent
        // supervisor's cancellation token. Other console lifecycle events
        // retain their native behavior.
        return controlType == 0 || controlType == 1;
    }

    public void Dispose()
    {
        if (!installed)
        {
            return;
        }
        SetConsoleCtrlHandler(handler, false);
        installed = false;
        GC.KeepAlive(handler);
    }
}
'@
}

# This host is deliberately unable to start the target before the parent has
# assigned this process to its kill-on-close Job Object. Standard handles are
# inherited by the target so binary bytes never pass through PowerShell's
# object/text pipeline.
$gate = $null
$target = $null
$controlGuard = $null
$startupPipe = $null
$startupWriter = $null
try {
    if ($IsWindows) {
        # The target gate remains closed while this guard is installed. The
        # wrapper therefore cannot be terminated directly by CTRL_BREAK before
        # the parent supervisor observes and owns the cancellation.
        $controlGuard = [J6NativeHostControlGuard]::new()
        if (-not $controlGuard.Installed) {
            exit 127
        }
    }
    $gate = [Threading.EventWaitHandle]::OpenExisting($GateName)
    if (-not $gate.WaitOne([TimeSpan]::FromSeconds(30))) {
        exit 124
    }

    $argumentJson = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($ArgumentPayloadBase64))
    $decodedArguments = @(ConvertFrom-Json -InputObject $argumentJson)

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $TargetFilePath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $false
    $startInfo.RedirectStandardInput = $false
    $startInfo.RedirectStandardOutput = $false
    $startInfo.RedirectStandardError = $false
    foreach ($argument in $decodedArguments) {
        [void]$startInfo.ArgumentList.Add([string]$argument)
    }

    $target = [Diagnostics.Process]::new()
    $target.StartInfo = $startInfo
    if (-not $target.Start()) {
        exit 125
    }

    if ($QualificationStartupHandshakeDelayMilliseconds -gt 0) {
        if ([Environment]::GetEnvironmentVariable(
                'J6_WO025_LOOPBACK_FAULT_INJECTION',
                [EnvironmentVariableTarget]::Process) -ne 'AUTHORIZED') {
            exit 124
        }
        Start-Sleep -Milliseconds $QualificationStartupHandshakeDelayMilliseconds
    }

    # Publish the exact target identity over an in-memory, nonce-bound channel.
    # The parent starts its execution deadline only after validating this
    # message. No command argument, path, output byte, or persistent artifact is
    # included in the startup protocol.
    $startupEvidence = [ordered]@{
        Protocol = 'J6_NATIVE_TARGET_START_V1'
        Nonce = $StartupNonce
        ProcessId = $target.Id
        StartedAtUtcTicks = $target.StartTime.ToUniversalTime().Ticks
    }
    $startupPipe = [IO.Pipes.NamedPipeClientStream]::new(
        '.',
        $StartupPipeName,
        [IO.Pipes.PipeDirection]::Out,
        [IO.Pipes.PipeOptions]::Asynchronous)
    $startupPipe.Connect($StartupTimeoutMilliseconds)
    $startupWriter = [IO.StreamWriter]::new(
        $startupPipe,
        [Text.UTF8Encoding]::new($false),
        1024,
        $true)
    $startupWriter.WriteLine((ConvertTo-Json -InputObject $startupEvidence -Compress))
    $startupWriter.Flush()
    $startupWriter.Dispose()
    $startupWriter = $null
    $startupPipe.Dispose()
    $startupPipe = $null

    $target.WaitForExit()
    exit $target.ExitCode
}
catch {
    # Do not print argument payloads or exception details from this transport
    # boundary. The supervisor reports the bounded native exit independently.
    exit 126
}
finally {
    if ($null -ne $startupWriter) {
        $startupWriter.Dispose()
    }
    if ($null -ne $startupPipe) {
        $startupPipe.Dispose()
    }
    if ($null -ne $target) {
        $target.Dispose()
    }
    if ($null -ne $gate) {
        $gate.Dispose()
    }
    if ($null -ne $controlGuard) {
        $controlGuard.Dispose()
    }
}
