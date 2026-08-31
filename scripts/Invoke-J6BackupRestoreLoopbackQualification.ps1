[CmdletBinding()]
param(
    [switch]$WithDocker
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

if ($PSVersionTable.PSVersion -lt [version]'7.4') {
    throw 'PowerShell 7.4 or newer is required for the J6 native pipeline qualification.'
}

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$modulePath = Join-Path $PSScriptRoot 'J6-NativeBinaryPipeline.psm1'
Import-Module -Name $modulePath -Force

function Assert-J6Qualification {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Test-J6ProcessAbsent {
    param([object]$ProcessId)
    if ($null -eq $ProcessId) {
        return $true
    }
    $process = $null
    try {
        $process = [Diagnostics.Process]::GetProcessById([int]$ProcessId)
        return $process.HasExited
    }
    catch [ArgumentException] {
        return $true
    }
    catch {
        throw [InvalidOperationException]::new(
            "Qualification could not verify process identity $($Identity.ProcessId).",
            $_.Exception)
    }
    finally {
        if ($null -ne $process) {
            $process.Dispose()
        }
    }
}

function Add-J6OwnedPipelineProcessEvidence {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [Collections.Generic.List[object]]$Collection,
        [Parameter(Mandatory = $true)]$Result
    )
    foreach ($pair in @(
            @($Result.ProducerPid, $Result.ProducerStartedAtUtc),
            @($Result.ConsumerPid, $Result.ConsumerStartedAtUtc))) {
        if ($null -eq $pair[0] -or [string]::IsNullOrWhiteSpace([string]$pair[1])) {
            continue
        }
        $Collection.Add([pscustomobject]@{
                ProcessId = [int]$pair[0]
                StartedAtUtcTicks = [DateTime]::Parse(
                    [string]$pair[1],
                    [Globalization.CultureInfo]::InvariantCulture,
                    [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime().Ticks
            })
    }
}

function Test-J6ProcessIdentityAbsent {
    param([Parameter(Mandatory = $true)]$Identity)
    $process = $null
    try {
        $process = [Diagnostics.Process]::GetProcessById([int]$Identity.ProcessId)
        $sameIdentity = $process.StartTime.ToUniversalTime().Ticks -eq `
            [long]$Identity.StartedAtUtcTicks
        return -not $sameIdentity
    }
    catch [ArgumentException] {
        return $true
    }
    catch [InvalidOperationException] {
        return $true
    }
    finally {
        if ($null -ne $process) {
            $process.Dispose()
        }
    }
}

function ConvertTo-J6SingleQuotedLiteral {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function New-J6SyntheticAgeExecutable {
    param([Parameter(Mandatory = $true)][string]$DestinationPath)

    $source = @'
using System;
using System.IO;

public static class J6SyntheticAge
{
    private static void Transform(Stream input, Stream output, bool stopEarly)
    {
        byte[] buffer = new byte[8192];
        while (true)
        {
            int read = input.Read(buffer, 0, buffer.Length);
            if (read == 0)
            {
                break;
            }
            for (int index = 0; index < read; index++)
            {
                buffer[index] ^= 0xA5;
            }
            output.Write(buffer, 0, read);
            output.Flush();
            if (stopEarly)
            {
                break;
            }
        }
    }

    public static int Main(string[] args)
    {
        string mode = Environment.GetEnvironmentVariable("J6_SYNTHETIC_AGE_MODE") ?? "NOMINAL";
        if (args.Length == 3 && args[0] == "-p" && args[1] == "-o")
        {
            if (mode != "FAIL_ENCRYPT")
            {
                string marker = Environment.GetEnvironmentVariable(
                    "J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER");
                if (!String.IsNullOrWhiteSpace(marker))
                {
                    File.WriteAllText(
                        marker,
                        "PASSPHRASE_INVOCATION_PATH=SIMULATED_NO_SECRET");
                }
            }
            using (Stream input = Console.OpenStandardInput())
            using (FileStream output = new FileStream(
                args[2], FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                Transform(input, output, mode == "FAIL_ENCRYPT");
            }
            return mode == "FAIL_ENCRYPT" ? 17 : 0;
        }
        if (args.Length == 2 && args[0] == "-d")
        {
            if (mode == "FAIL_DECRYPT")
            {
                return 18;
            }
            using (FileStream input = File.OpenRead(args[1]))
            using (Stream output = Console.OpenStandardOutput())
            {
                Transform(input, output, false);
            }
            return 0;
        }
        return 64;
    }
}
'@
    $compilerPath = Join-Path $env:WINDIR `
        'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path -LiteralPath $compilerPath -PathType Leaf)) {
        $compilerPath = Join-Path $env:WINDIR `
            'Microsoft.NET\Framework\v4.0.30319\csc.exe'
    }
    Assert-J6Qualification (Test-Path -LiteralPath $compilerPath -PathType Leaf) `
        'The local Windows C# compiler is required for the loopback double.'
    $sourcePath = [IO.Path]::ChangeExtension($DestinationPath, '.cs')
    [IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
    & $compilerPath /nologo /target:exe "/out:$DestinationPath" $sourcePath
    Assert-J6Qualification ($LASTEXITCODE -eq 0) `
        'The synthetic age executable compilation failed.'
    Assert-J6Qualification (Test-Path -LiteralPath $DestinationPath -PathType Leaf) `
        'The synthetic age executable was not created.'
}

function New-J6CtrlBreakLauncherExecutable {
    param([Parameter(Mandatory = $true)][string]$DestinationPath)

    $source = @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class J6CtrlBreakLauncher
{
    private const uint CREATE_NEW_PROCESS_GROUP = 0x00000200;
    private const uint CTRL_BREAK_EVENT = 1;
    private const uint WAIT_OBJECT_0 = 0;
    private const uint WAIT_TIMEOUT = 258;
    private const uint STILL_ACTIVE = 259;
    private delegate bool ConsoleCtrlHandler(uint ctrlType);
    private static readonly ConsoleCtrlHandler IgnoreLauncherControlEvent =
        delegate(uint ctrlType) { return true; };

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct STARTUPINFO
    {
        public uint cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public uint dwX;
        public uint dwY;
        public uint dwXSize;
        public uint dwYSize;
        public uint dwXCountChars;
        public uint dwYCountChars;
        public uint dwFillAttribute;
        public uint dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PROCESS_INFORMATION
    {
        public IntPtr hProcess;
        public IntPtr hThread;
        public uint dwProcessId;
        public uint dwThreadId;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateProcessW(
        string applicationName,
        StringBuilder commandLine,
        IntPtr processAttributes,
        IntPtr threadAttributes,
        bool inheritHandles,
        uint creationFlags,
        IntPtr environment,
        string currentDirectory,
        ref STARTUPINFO startupInfo,
        out PROCESS_INFORMATION processInformation);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GenerateConsoleCtrlEvent(uint ctrlEvent, uint processGroupId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetConsoleCtrlHandler(ConsoleCtrlHandler handlerRoutine, bool add);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetExitCodeProcess(IntPtr process, out uint exitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool TerminateProcess(IntPtr process, uint exitCode);

    [DllImport("kernel32.dll")]
    private static extern bool CloseHandle(IntPtr handle);

    private static string Quote(string value)
    {
        if (value.Length > 0 && value.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) < 0)
        {
            return value;
        }
        var quoted = new StringBuilder("\"");
        int backslashes = 0;
        foreach (char current in value)
        {
            if (current == '\\')
            {
                backslashes++;
            }
            else if (current == '"')
            {
                quoted.Append('\\', backslashes * 2 + 1);
                quoted.Append('"');
                backslashes = 0;
            }
            else
            {
                quoted.Append('\\', backslashes);
                quoted.Append(current);
                backslashes = 0;
            }
        }
        quoted.Append('\\', backslashes * 2);
        quoted.Append('"');
        return quoted.ToString();
    }

    public static int Main(string[] args)
    {
        if (args.Length != 8)
        {
            return 64;
        }
        string commandLine = Quote(args[0]) +
            " -NoLogo -NoProfile -NonInteractive -File " + Quote(args[1]) +
            " -ModulePath " + Quote(args[2]) +
            " -ReadyPath " + Quote(args[3]) +
            " -ResultPath " + Quote(args[4]) +
            " -ProducerIdentityPath " + Quote(args[6]) +
            " -ConsumerIdentityPath " + Quote(args[7]);
        var startup = new STARTUPINFO
        {
            cb = (uint)Marshal.SizeOf(typeof(STARTUPINFO))
        };
        PROCESS_INFORMATION process;
        if (!CreateProcessW(
                args[0],
                new StringBuilder(commandLine),
                IntPtr.Zero,
                IntPtr.Zero,
                false,
                CREATE_NEW_PROCESS_GROUP,
                IntPtr.Zero,
                Directory.GetCurrentDirectory(),
                ref startup,
                out process))
        {
            return 66;
        }
        bool handlerInstalled = false;
        try
        {
            if (!SetConsoleCtrlHandler(IgnoreLauncherControlEvent, true))
            {
                return 75;
            }
            handlerInstalled = true;
            File.WriteAllText(args[5], process.dwProcessId.ToString());
            DateTime readyDeadline = DateTime.UtcNow.AddSeconds(10);
            while (!File.Exists(args[3]) && DateTime.UtcNow < readyDeadline)
            {
                uint earlyCode;
                if (GetExitCodeProcess(process.hProcess, out earlyCode) && earlyCode != STILL_ACTIVE)
                {
                    return 67;
                }
                Thread.Sleep(25);
            }
            if (!File.Exists(args[3]))
            {
                return 68;
            }
            if (!GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, process.dwProcessId))
            {
                return 69;
            }
            uint wait = WaitForSingleObject(process.hProcess, 10000);
            if (wait == WAIT_TIMEOUT)
            {
                return 70;
            }
            if (wait != WAIT_OBJECT_0)
            {
                return 71;
            }
            uint exitCode;
            if (!GetExitCodeProcess(process.hProcess, out exitCode))
            {
                return 72;
            }
            File.WriteAllText(
                args[5] + ".exact-exit",
                process.dwProcessId.ToString() + ":" + exitCode.ToString());
            return unchecked((int)exitCode);
        }
        finally
        {
            bool cleanupVerified = true;
            uint exitCode;
            if (!GetExitCodeProcess(process.hProcess, out exitCode))
            {
                cleanupVerified = false;
            }
            else if (exitCode == STILL_ACTIVE)
            {
                TerminateProcess(process.hProcess, 73);
                uint terminationWait = WaitForSingleObject(process.hProcess, 3000);
                uint terminatedCode;
                cleanupVerified = terminationWait == WAIT_OBJECT_0 &&
                    GetExitCodeProcess(process.hProcess, out terminatedCode) &&
                    terminatedCode != STILL_ACTIVE;
            }
            if (handlerInstalled)
            {
                SetConsoleCtrlHandler(IgnoreLauncherControlEvent, false);
            }
            bool threadClosed = CloseHandle(process.hThread);
            bool processClosed = CloseHandle(process.hProcess);
            if (!cleanupVerified || !threadClosed || !processClosed)
            {
                throw new InvalidOperationException(
                    "The exact CTRL_BREAK harness cleanup could not be confirmed.");
            }
        }
    }
}
'@
    $compilerPath = Join-Path $env:WINDIR `
        'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (-not (Test-Path -LiteralPath $compilerPath -PathType Leaf)) {
        $compilerPath = Join-Path $env:WINDIR `
            'Microsoft.NET\Framework\v4.0.30319\csc.exe'
    }
    Assert-J6Qualification (Test-Path -LiteralPath $compilerPath -PathType Leaf) `
        'The local Windows C# compiler is required for CTRL_BREAK qualification.'
    $sourcePath = [IO.Path]::ChangeExtension($DestinationPath, '.cs')
    [IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
    & $compilerPath /nologo /target:exe "/out:$DestinationPath" $sourcePath
    Assert-J6Qualification ($LASTEXITCODE -eq 0) `
        'The CTRL_BREAK launcher compilation failed.'
    Assert-J6Qualification (Test-Path -LiteralPath $DestinationPath -PathType Leaf) `
        'The CTRL_BREAK launcher was not created.'
}

function Invoke-J6QualificationDockerScalar {
    param(
        [Parameter(Mandatory = $true)][string]$DockerExecutable,
        [Parameter(Mandatory = $true)][string]$Sql
    )
    $result = Invoke-J6BoundedNativeCommand `
        -FilePath $DockerExecutable `
        -ArgumentList @(
            'compose', '--env-file', '.env', 'exec', '-T', 'postgres',
            'sh', '-c',
            'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-align --tuples-only --quiet --command "$1"',
            'sh', $Sql) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutMilliseconds 30000 `
        -CleanupTimeoutMilliseconds 5000
    if ($result.ExitCode -ne 0 -or
        $result.ProcessTreeCleanup -ne 'PASS' -or
        $result.UnexpectedDescendantCleanup) {
        throw (
            'A Docker loopback qualification query failed. ' +
            "ExitCode=$($result.ExitCode); " +
            "ProcessTreeCleanup=$($result.ProcessTreeCleanup); " +
            "UnexpectedDescendantCleanup=$($result.UnexpectedDescendantCleanup)")
    }
    return $result.StandardOutput.TrimEnd()
}

$pwshPath = (Get-Process -Id $PID).Path
$qualificationRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('j6-native-pipeline-' + [Guid]::NewGuid().ToString('N'))
$expectedPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$resolvedQualificationRoot = [IO.Path]::GetFullPath($qualificationRoot)
if (-not $resolvedQualificationRoot.StartsWith(
        $expectedPrefix,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The synthetic qualification directory is outside the operating-system temp directory.'
}

$commonArguments = @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command')
$ownedProcesses = [Collections.Generic.List[object]]::new()

try {
    [void](New-Item -ItemType Directory -Path $resolvedQualificationRoot)

    $nominalInput = Join-Path $resolvedQualificationRoot 'nominal-input.bin'
    $nominalOutput = Join-Path $resolvedQualificationRoot 'nominal-output.bin'
    $expectedBytes = [byte[]]::new(65536)
    for ($index = 0; $index -lt $expectedBytes.Length; $index++) {
        $expectedBytes[$index] = [byte]($index % 256)
    }
    [IO.File]::WriteAllBytes($nominalInput, $expectedBytes)
    $nominalProducerTemplate = @'
$sourceStream = [IO.File]::OpenRead(__INPUT_PATH__)
$targetStream = [Console]::OpenStandardOutput()
try {
    $sourceStream.CopyTo($targetStream)
    $targetStream.Flush()
}
finally {
    $sourceStream.Dispose()
}
exit 0
'@
    $nominalProducer = $nominalProducerTemplate.Replace(
        '__INPUT_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $nominalInput))
    $fileConsumerTemplate = @'
$sourceStream = [Console]::OpenStandardInput()
$targetStream = [IO.File]::Open(
    __OUTPUT_PATH__,
    [IO.FileMode]::CreateNew,
    [IO.FileAccess]::Write,
    [IO.FileShare]::None)
try {
    $sourceStream.CopyTo($targetStream)
}
finally {
    $targetStream.Dispose()
}
exit 0
'@
    $fileConsumer = $fileConsumerTemplate.Replace(
        '__OUTPUT_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $nominalOutput))
    $nominalResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($nominalProducer)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($fileConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $nominalResult

    $expectedHash = (Get-FileHash -LiteralPath $nominalInput -Algorithm SHA256).Hash.ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $nominalOutput -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host "J6_PIPELINE_BINARY_EXPECTED_SHA256=$expectedHash"
    Write-Host "J6_PIPELINE_BINARY_ACTUAL_SHA256=$actualHash"
    Write-Host "J6_PIPELINE_BINARY_ACTUAL_LENGTH=$((Get-Item -LiteralPath $nominalOutput).Length)"
    Assert-J6Qualification ($nominalResult.PipelineResult -eq 'SUCCESS') `
        "The nominal synthetic binary pipeline did not succeed: $($nominalResult | ConvertTo-Json -Compress)"
    Assert-J6Qualification ($nominalResult.CopyStatus -eq 'COMPLETED_TO_EOF') `
        'The nominal synthetic copy did not reach EOF.'
    Assert-J6Qualification ($nominalResult.ProducerExitCode -eq 0 -and
        $nominalResult.ConsumerExitCode -eq 0) `
        'The nominal native exit codes were not both zero.'
    Assert-J6Qualification ($nominalResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The nominal synthetic process cleanup was not confirmed.'
    Assert-J6Qualification ($expectedHash -ceq $actualHash) `
        'The synthetic binary payload was modified in transit.'
    Assert-J6Qualification ((Get-Item -LiteralPath $nominalOutput).Length -eq 65536) `
        'The synthetic binary payload length changed in transit.'
    Write-Host 'J6_PIPELINE_BINARY_NOMINAL=PASS'

    $childPidPath = Join-Path $resolvedQualificationRoot 'consumer-failure-child.pid'
    $slowProducerWithChildTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 60')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
[IO.File]::WriteAllText(__CHILD_PID_PATH__, $child.Id.ToString())
$child.Dispose()
$output = [Console]::OpenStandardOutput()
$chunk = [byte[]]::new(1024)
while ($true) {
    $output.Write($chunk, 0, $chunk.Length)
    $output.Flush()
    Start-Sleep -Milliseconds 20
}
'@
    $slowProducerWithChild = $slowProducerWithChildTemplate.Replace(
        '__CHILD_PID_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $childPidPath))
    $earlyFailingConsumerTemplate = @'
$deadline = [DateTime]::UtcNow.AddSeconds(5)
while (-not (Test-Path -LiteralPath __CHILD_PID_PATH__ -PathType Leaf) -and
    [DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Milliseconds 25
}
if (-not (Test-Path -LiteralPath __CHILD_PID_PATH__ -PathType Leaf)) {
    exit 19
}
exit 17
'@
    $earlyFailingConsumer = $earlyFailingConsumerTemplate.Replace(
        '__CHILD_PID_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $childPidPath))
    $consumerFailureResult = Invoke-J6NativeBinaryPipeline `
        -Phase BACKUP_ENCRYPTION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($slowProducerWithChild)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($earlyFailingConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $consumerFailureResult
    Assert-J6Qualification ($consumerFailureResult.PipelineResult -eq 'CONSUMER_FAILED') `
        'An early non-zero consumer exit was not classified as a consumer failure.'
    Assert-J6Qualification ($consumerFailureResult.ConsumerExitCode -eq 17) `
        'The early consumer exit code was not observed independently.'
    Assert-J6Qualification ($consumerFailureResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The early consumer failure did not clean both process roots.'
    Assert-J6Qualification (Test-Path -LiteralPath $childPidPath -PathType Leaf) `
        'The synthetic producer child PID evidence was not created.'
    $childPid = [int]([IO.File]::ReadAllText($childPidPath))
    Assert-J6Qualification (Test-J6ProcessAbsent $childPid) `
        'The producer descendant survived the early consumer failure.'
    Write-Host 'J6_PIPELINE_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED'

    $exitedRootChildPidPath = Join-Path $resolvedQualificationRoot 'exited-root-child.pid'
    $producerExitsLeavingChildTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 60')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
[IO.File]::WriteAllText(__CHILD_PID_PATH__, $child.Id.ToString())
$child.Dispose()
exit 29
'@
    $producerExitsLeavingChild = $producerExitsLeavingChildTemplate.Replace(
        '__CHILD_PID_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $exitedRootChildPidPath))
    $rootExitConsumer = @'
$sourceStream = [Console]::OpenStandardInput()
$buffer = [byte[]]::new(1024)
while ($sourceStream.Read($buffer, 0, $buffer.Length) -gt 0) {}
Start-Sleep -Seconds 60
'@
    $rootExitResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($producerExitsLeavingChild)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($rootExitConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $rootExitResult
    Assert-J6Qualification (Test-Path -LiteralPath $exitedRootChildPidPath -PathType Leaf) `
        'The exited-root descendant PID evidence was not created.'
    $exitedRootChildPid = [int]([IO.File]::ReadAllText($exitedRootChildPidPath))
    Assert-J6Qualification ($rootExitResult.PipelineResult -eq 'TIMEOUT' -and
        $rootExitResult.ProducerExitCode -eq 29) `
        "An exited producer with a pipe-holding descendant was not rejected: $($rootExitResult | ConvertTo-Json -Compress)"
    Assert-J6Qualification ($rootExitResult.ProducerRootAliveAtCleanupStart -eq $false) `
        'The producer wrapper had not exited before descendant cleanup began.'
    Assert-J6Qualification ($rootExitResult.ProducerConfinement -eq 'WINDOWS_KILL_ON_JOB_CLOSE') `
        'The exited-root descendant was not enclosed by the Windows confinement job.'
    Assert-J6Qualification (Test-J6ProcessAbsent $exitedRootChildPid) `
        'The descendant of an already exited producer root survived cleanup.'
    Write-Host 'J6_PIPELINE_EXITED_ROOT_DESCENDANT_CLEANUP=PASS'

    $pidReuseChildPath = Join-Path $resolvedQualificationRoot 'pid-reuse-child.pid'
    $pidReuseParentTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
[IO.File]::WriteAllText(__CHILD_PID_PATH__, $child.Id.ToString())
$child.Dispose()
Start-Sleep -Seconds 30
'@
    $pidReuseParentCommand = $pidReuseParentTemplate.Replace(
        '__CHILD_PID_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $pidReuseChildPath))
    $pidReuseParentStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $pidReuseParentStartInfo.FileName = $pwshPath
    $pidReuseParentStartInfo.UseShellExecute = $false
    $pidReuseParentStartInfo.CreateNoWindow = $true
    $pidReuseParentStartInfo.WorkingDirectory = $repositoryRoot
    foreach ($argument in ($commonArguments + @($pidReuseParentCommand))) {
        [void]$pidReuseParentStartInfo.ArgumentList.Add($argument)
    }
    $pidReuseParent = [Diagnostics.Process]::new()
    $pidReuseParent.StartInfo = $pidReuseParentStartInfo
    $pidReuseChildPid = $null
    try {
        Assert-J6Qualification $pidReuseParent.Start() `
            'The PID-reuse ownership-fence parent did not start.'
        $pidReuseReadyDeadline = [DateTime]::UtcNow.AddSeconds(5)
        while (-not (Test-Path -LiteralPath $pidReuseChildPath -PathType Leaf) -and
            [DateTime]::UtcNow -lt $pidReuseReadyDeadline) {
            Start-Sleep -Milliseconds 25
        }
        Assert-J6Qualification (Test-Path -LiteralPath $pidReuseChildPath -PathType Leaf) `
            'The PID-reuse ownership-fence child did not start.'
        $pidReuseChildPid = [int]([IO.File]::ReadAllText($pidReuseChildPath))
        $reusedPidStartedAt = $pidReuseParent.StartTime.ToUniversalTime()
        $oldRootStartedAtTicks = $reusedPidStartedAt.AddSeconds(-10).Ticks
        $oldRootExitedAtTicks = $reusedPidStartedAt.AddSeconds(-5).Ticks
        $pipelineModule = Get-Module | Where-Object {
            $_.Path -eq [IO.Path]::GetFullPath($modulePath)
        } | Select-Object -First 1
        Assert-J6Qualification ($null -ne $pipelineModule) `
            'The native pipeline module scope could not be located.'
        $incorrectClaims = & $pipelineModule {
            param($ReusedProcessId, $OldStartedAtTicks, $OldExitedAtTicks)
            $claimed = [Collections.Generic.Dictionary[string, object]]::new()
            $oldIdentity = [pscustomobject]@{
                ProcessId = [int]$ReusedProcessId
                StartedAtUtcTicks = [long]$OldStartedAtTicks
                ExitedAtUtcTicks = [long]$OldExitedAtTicks
            }
            Update-J6OwnedDescendantIdentities `
                -RootIdentity $oldIdentity `
                -OwnedDescendants $claimed
            return $claimed.Count
        } $pidReuseParent.Id $oldRootStartedAtTicks $oldRootExitedAtTicks
        Assert-J6Qualification ([int]$incorrectClaims -eq 0) `
            'A descendant of a process reusing an old PID was incorrectly claimed as owned.'
    }
    finally {
        try {
            if ($pidReuseParent.Id -gt 0 -and -not $pidReuseParent.HasExited) {
                $pidReuseParent.Kill($true)
                [void]$pidReuseParent.WaitForExit(3000)
            }
        }
        catch [InvalidOperationException] {
            # The synthetic parent already exited.
        }
        $pidReuseParent.Dispose()
    }
    Assert-J6Qualification (Test-J6ProcessAbsent $pidReuseChildPid) `
        'The synthetic PID-reuse counter-proof child survived local teardown.'
    Write-Host 'J6_PIPELINE_PID_REUSE_OWNERSHIP_FENCE=PASS'

    $producerFailure = 'exit 23'
    $consumerWaitingAfterEof = @'
$sourceStream = [Console]::OpenStandardInput()
$buffer = [byte[]]::new(1024)
while ($sourceStream.Read($buffer, 0, $buffer.Length) -gt 0) {}
Start-Sleep -Seconds 30
'@
    $producerFailureResult = Invoke-J6NativeBinaryPipeline `
        -Phase RESTORE_DECRYPTION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($producerFailure)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($consumerWaitingAfterEof)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $producerFailureResult
    Assert-J6Qualification ($producerFailureResult.PipelineResult -eq 'PRODUCER_FAILED') `
        'An early non-zero producer exit was not classified as a producer failure.'
    Assert-J6Qualification ($producerFailureResult.ProducerExitCode -eq 23) `
        'The early producer exit code was not observed independently.'
    Assert-J6Qualification ($producerFailureResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The early producer failure did not clean both process roots.'
    Write-Host 'J6_PIPELINE_EARLY_PRODUCER_FAILURE=PASS_FAIL_CLOSED'
    Write-Host 'J6_PIPELINE_RESTORE_SYMMETRY=PASS'

    $slowProducer = @'
$output = [Console]::OpenStandardOutput()
$chunk = [byte[]]::new(1024)
while ($true) {
    $output.Write($chunk, 0, $chunk.Length)
    $output.Flush()
    Start-Sleep -Milliseconds 20
}
'@
    $earlySuccessfulConsumer = 'Start-Sleep -Milliseconds 200; exit 0'
    $truncationResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($slowProducer)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($earlySuccessfulConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 10
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $truncationResult
    Assert-J6Qualification ($truncationResult.PipelineResult -eq 'CONSUMER_EARLY_EXIT') `
        'A premature zero consumer exit was not rejected.'
    Assert-J6Qualification ($truncationResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The premature zero consumer exit left an owned process root.'
    Write-Host 'J6_PIPELINE_PREMATURE_SUCCESS_TRUNCATION=PASS_FAIL_CLOSED'

    $blockingProducer = 'Start-Sleep -Seconds 30'
    $blockingConsumer = @'
$sourceStream = [Console]::OpenStandardInput()
$buffer = [byte[]]::new(16)
while ($sourceStream.Read($buffer, 0, $buffer.Length) -gt 0) {}
'@
    $timeoutResult = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList ($commonArguments + @($blockingProducer)) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList ($commonArguments + @($blockingConsumer)) `
        -WorkingDirectory $repositoryRoot `
        -TimeoutSeconds 1
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $timeoutResult
    Assert-J6Qualification ($timeoutResult.PipelineResult -eq 'TIMEOUT') `
        'The bounded synthetic timeout was not classified as TIMEOUT.'
    Assert-J6Qualification ($timeoutResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The bounded synthetic timeout left an owned process root.'
    Assert-J6Qualification ($timeoutResult.DurationMilliseconds -lt 8000) `
        'The bounded synthetic timeout exceeded the cleanup envelope.'
    Write-Host 'J6_PIPELINE_TIMEOUT=PASS_FAIL_CLOSED'

    $boundedCommandPidPath = Join-Path $resolvedQualificationRoot 'bounded-command.pid'
    $boundedCommandTemplate = @'
[IO.File]::WriteAllText(__PID_PATH__, $PID.ToString())
Start-Sleep -Seconds 30
'@
    $boundedCommand = $boundedCommandTemplate.Replace(
        '__PID_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $boundedCommandPidPath))
    $boundedCommandTimedOut = $false
    try {
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @($boundedCommand)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 1500 `
            -CleanupTimeoutMilliseconds 3000)
    }
    catch {
        $boundedCommandTimedOut = $_.Exception.Message -eq `
            'The bounded native command exceeded its deadline.'
    }
    Assert-J6Qualification $boundedCommandTimedOut `
        'The bounded native command helper did not fail closed on timeout.'
    Assert-J6Qualification (Test-Path -LiteralPath $boundedCommandPidPath -PathType Leaf) `
        'The bounded native command PID evidence was not created.'
    $boundedCommandPid = [int]([IO.File]::ReadAllText($boundedCommandPidPath))
    Assert-J6Qualification (Test-J6ProcessAbsent $boundedCommandPid) `
        'The bounded native command survived its timeout cleanup.'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_TIMEOUT_CLEANUP=PASS_FAIL_CLOSED'

    $postStartFailurePidPath = Join-Path $resolvedQualificationRoot 'post-start-failure.pid'
    $postStartFailureTemplate = @'
[IO.File]::WriteAllText(__PID_PATH__, $PID.ToString())
Start-Sleep -Seconds 30
'@
    $postStartFailureCommand = $postStartFailureTemplate.Replace(
        '__PID_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $postStartFailurePidPath))
    $postStartFailureObserved = $false
    $previousOfflineFaultInjection = [Environment]::GetEnvironmentVariable(
        'J6_WO024_LOOPBACK_FAULT_INJECTION',
        [EnvironmentVariableTarget]::Process)
    try {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            'AUTHORIZED',
            [EnvironmentVariableTarget]::Process)
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @($postStartFailureCommand)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 5000 `
            -CleanupTimeoutMilliseconds 3000 `
            -QualificationInjectSupervisionFailureAfterStart `
            -QualificationSupervisionReadyPath $postStartFailurePidPath)
    }
    catch {
        $postStartFailureObserved = $_.Exception.Message -eq `
            'WO-024 injected post-start native supervision failure.'
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            $previousOfflineFaultInjection,
            [EnvironmentVariableTarget]::Process)
    }
    Assert-J6Qualification $postStartFailureObserved `
        'The injected post-start supervision failure was not observed.'
    Assert-J6Qualification (Test-Path -LiteralPath $postStartFailurePidPath -PathType Leaf) `
        'The injected post-start supervision process did not publish its PID.'
    $postStartFailurePid = [int]([IO.File]::ReadAllText($postStartFailurePidPath))
    Assert-J6Qualification (Test-J6ProcessAbsent $postStartFailurePid) `
        'A native process survived a post-start supervision exception.'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_POST_START_EXCEPTION_CLEANUP=PASS_FAIL_CLOSED'

    $identityGapEvidencePath = Join-Path $resolvedQualificationRoot 'identity-gap-child.json'
    $identityGapRootTemplate = @'
$childStartInfo = [Diagnostics.ProcessStartInfo]::new()
$childStartInfo.FileName = $PSHOME + '\pwsh.exe'
$childStartInfo.UseShellExecute = $false
$childStartInfo.CreateNoWindow = $true
foreach ($argument in @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30')) {
    [void]$childStartInfo.ArgumentList.Add($argument)
}
$child = [Diagnostics.Process]::new()
$child.StartInfo = $childStartInfo
if (-not $child.Start()) { exit 97 }
$identity = [ordered]@{
    ProcessId = $child.Id
    StartedAtUtcTicks = $child.StartTime.ToUniversalTime().Ticks
}
[IO.File]::WriteAllText(
    __EVIDENCE_PATH__,
    ($identity | ConvertTo-Json -Compress),
    [Text.UTF8Encoding]::new($false))
$child.Dispose()
exit 0
'@
    $identityGapRootCommand = $identityGapRootTemplate.Replace(
        '__EVIDENCE_PATH__',
        (ConvertTo-J6SingleQuotedLiteral $identityGapEvidencePath))
    $identityGapFailureObserved = $false
    $previousIdentityGapFaultInjection = [Environment]::GetEnvironmentVariable(
        'J6_WO024_LOOPBACK_FAULT_INJECTION',
        [EnvironmentVariableTarget]::Process)
    $identityGapChildIdentity = $null
    try {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            'AUTHORIZED',
            [EnvironmentVariableTarget]::Process)
        [void](Invoke-J6BoundedNativeCommand `
            -FilePath $pwshPath `
            -ArgumentList ($commonArguments + @($identityGapRootCommand)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutMilliseconds 5000 `
            -CleanupTimeoutMilliseconds 3000 `
            -QualificationInjectSupervisionFailureAfterStart `
            -QualificationSupervisionReadyPath $identityGapEvidencePath `
            -QualificationWaitForProcessExitBeforeSupervisionFailure)
    }
    catch {
        $identityGapFailureObserved = $_.Exception.Message -eq `
            'WO-024 injected post-start native supervision failure.'
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            $previousIdentityGapFaultInjection,
            [EnvironmentVariableTarget]::Process)
    }
    Assert-J6Qualification $identityGapFailureObserved `
        'The injected exited-root supervision failure was not preserved.'
    Assert-J6Qualification (Test-Path -LiteralPath $identityGapEvidencePath -PathType Leaf) `
        'The root-exit identity-gap child evidence was not created.'
    $identityGapChildIdentity = Get-Content `
        -LiteralPath $identityGapEvidencePath `
        -Raw | ConvertFrom-Json
    Assert-J6Qualification (Test-J6ProcessIdentityAbsent $identityGapChildIdentity) `
        'The kill-on-close job did not remove the child of an already exited host.'
    Write-Host 'J6_BOUNDED_NATIVE_COMMAND_EXITED_ROOT_DESCENDANT_JOB_CLEANUP=PASS'

    $cancellation = New-J6ConsoleCancellationRegistration
    try {
        $cancellation.CancelAfterForQualification(250)
        $cancelledResult = Invoke-J6NativeBinaryPipeline `
            -Phase SYNTHETIC_QUALIFICATION `
            -ProducerFilePath $pwshPath `
            -ProducerArgumentList ($commonArguments + @($blockingProducer)) `
            -ConsumerFilePath $pwshPath `
            -ConsumerArgumentList ($commonArguments + @($blockingConsumer)) `
            -WorkingDirectory $repositoryRoot `
            -TimeoutSeconds 10 `
            -CancellationToken $cancellation.Token
    }
    finally {
        $cancellation.Dispose()
    }
    Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $cancelledResult
    Assert-J6Qualification ($cancelledResult.PipelineResult -eq 'CANCELLED') `
        'The cooperative interruption was not classified as CANCELLED.'
    Assert-J6Qualification ($cancelledResult.LocalProcessTreeCleanup -eq 'PASS') `
        'The cooperative interruption left an owned process root.'
    Write-Host 'J6_PIPELINE_COOPERATIVE_CANCELLATION_CLEANUP=PASS'

    $ctrlBreakLauncherPath = Join-Path $resolvedQualificationRoot 'j6-ctrl-break-launcher.exe'
    New-J6CtrlBreakLauncherExecutable -DestinationPath $ctrlBreakLauncherPath
    $ctrlBreakHarnessPath = Join-Path $resolvedQualificationRoot 'j6-ctrl-break-harness.ps1'
    $ctrlBreakReadyPath = Join-Path $resolvedQualificationRoot 'ctrl-break.ready'
    $ctrlBreakResultPath = Join-Path $resolvedQualificationRoot 'ctrl-break-result.json'
    $ctrlBreakHarnessPidPath = Join-Path $resolvedQualificationRoot 'ctrl-break-harness.pid'
    $ctrlBreakExactHarnessExitPath = $ctrlBreakHarnessPidPath + '.exact-exit'
    $ctrlBreakProducerIdentityPath = Join-Path $resolvedQualificationRoot `
        'ctrl-break-producer.identity.json'
    $ctrlBreakConsumerIdentityPath = Join-Path $resolvedQualificationRoot `
        'ctrl-break-consumer.identity.json'
    $ctrlBreakHarness = @'
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ModulePath,
    [Parameter(Mandatory = $true)][string]$ReadyPath,
    [Parameter(Mandatory = $true)][string]$ResultPath,
    [Parameter(Mandatory = $true)][string]$ProducerIdentityPath,
    [Parameter(Mandatory = $true)][string]$ConsumerIdentityPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0
Import-Module -Name $ModulePath -Force
function ConvertTo-QuotedLiteral([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}
$pwshPath = (Get-Process -Id $PID).Path
$workingDirectory = Split-Path -Parent $ModulePath
$ignoreControlSource = 'using System; public static class J6IgnoreConsoleControl { private static readonly ConsoleCancelEventHandler Handler = delegate(object sender, ConsoleCancelEventArgs args) { args.Cancel = true; }; public static void Install() { Console.CancelKeyPress += Handler; } }'
$ignoreControlPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ignoreControlSource))
$ignoreControlPrefix = "Add-Type -TypeDefinition ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$ignoreControlPayload'))); [J6IgnoreConsoleControl]::Install(); "
$producerCommand = $ignoreControlPrefix + '$self = [Diagnostics.Process]::GetCurrentProcess(); try { $identity = [ordered]@{ ProcessId = $PID; StartedAtUtcTicks = $self.StartTime.ToUniversalTime().Ticks }; [IO.File]::WriteAllText(__PRODUCER_IDENTITY_PATH__, ($identity | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false)) } finally { $self.Dispose() }; $consumerDeadline = [DateTime]::UtcNow.AddSeconds(5); while (-not (Test-Path -LiteralPath __CONSUMER_IDENTITY_PATH__ -PathType Leaf) -and [DateTime]::UtcNow -lt $consumerDeadline) { Start-Sleep -Milliseconds 25 }; if (-not (Test-Path -LiteralPath __CONSUMER_IDENTITY_PATH__ -PathType Leaf)) { exit 98 }; [IO.File]::WriteAllText(__READY_PATH__, ''READY''); $target = [Console]::OpenStandardOutput(); $buffer = [byte[]]::new(1024); while ($true) { $target.Write($buffer, 0, $buffer.Length); $target.Flush(); Start-Sleep -Milliseconds 20 }'.Replace(
    '__PRODUCER_IDENTITY_PATH__', (ConvertTo-QuotedLiteral $ProducerIdentityPath)).Replace(
    '__CONSUMER_IDENTITY_PATH__', (ConvertTo-QuotedLiteral $ConsumerIdentityPath)).Replace(
    '__READY_PATH__', (ConvertTo-QuotedLiteral $ReadyPath))
$consumerCommand = $ignoreControlPrefix + '$self = [Diagnostics.Process]::GetCurrentProcess(); try { $identity = [ordered]@{ ProcessId = $PID; StartedAtUtcTicks = $self.StartTime.ToUniversalTime().Ticks }; [IO.File]::WriteAllText(__CONSUMER_IDENTITY_PATH__, ($identity | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false)) } finally { $self.Dispose() }; $source = [Console]::OpenStandardInput(); $buffer = [byte[]]::new(1024); while ($source.Read($buffer, 0, $buffer.Length) -gt 0) {}'.Replace(
    '__CONSUMER_IDENTITY_PATH__', (ConvertTo-QuotedLiteral $ConsumerIdentityPath))
$registration = New-J6ConsoleCancellationRegistration
$exitCode = 0
try {
    $result = Invoke-J6NativeBinaryPipeline `
        -Phase SYNTHETIC_QUALIFICATION `
        -ProducerFilePath $pwshPath `
        -ProducerArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', $producerCommand) `
        -ConsumerFilePath $pwshPath `
        -ConsumerArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-Command', $consumerCommand) `
        -WorkingDirectory $workingDirectory `
        -TimeoutSeconds 10 `
        -CleanupTimeoutMilliseconds 5000 `
        -CancellationToken $registration.Token
    $evidence = [ordered]@{
        TokenCancelled = $registration.Token.IsCancellationRequested
        PipelineResult = $result.PipelineResult
        LocalProcessTreeCleanup = $result.LocalProcessTreeCleanup
        ProducerPid = $result.ProducerPid
        ProducerStartedAtUtc = $result.ProducerStartedAtUtc
        ConsumerPid = $result.ConsumerPid
        ConsumerStartedAtUtc = $result.ConsumerStartedAtUtc
        ProducerRootAliveAtCleanupStart = $result.ProducerRootAliveAtCleanupStart
        ConsumerRootAliveAtCleanupStart = $result.ConsumerRootAliveAtCleanupStart
    }
    [IO.File]::WriteAllText(
        $ResultPath,
        ($evidence | ConvertTo-Json -Compress),
        [Text.UTF8Encoding]::new($false))
    if (-not $evidence.TokenCancelled -or
        $evidence.PipelineResult -ne 'CANCELLED' -or
        $evidence.LocalProcessTreeCleanup -ne 'PASS' -or
        -not $evidence.ProducerRootAliveAtCleanupStart -or
        -not $evidence.ConsumerRootAliveAtCleanupStart) {
        $exitCode = 90
    }
}
catch {
    $exitCode = 91
}
finally {
    $registration.Dispose()
}
exit $exitCode
'@
    [IO.File]::WriteAllText(
        $ctrlBreakHarnessPath,
        $ctrlBreakHarness,
        [Text.UTF8Encoding]::new($false))
    $ctrlBreakLauncherOutput = $null
    $ctrlBreakLauncherExitCode = $null
    $ctrlBreakProducerIdentity = $null
    $ctrlBreakConsumerIdentity = $null
    try {
        $ctrlBreakLauncherOutput = @(& $ctrlBreakLauncherPath `
            $pwshPath `
            $ctrlBreakHarnessPath `
            $modulePath `
            $ctrlBreakReadyPath `
            $ctrlBreakResultPath `
            $ctrlBreakHarnessPidPath `
            $ctrlBreakProducerIdentityPath `
            $ctrlBreakConsumerIdentityPath 2>&1)
        $ctrlBreakLauncherExitCode = $LASTEXITCODE
        Assert-J6Qualification ($ctrlBreakLauncherExitCode -eq 0) `
            "The real CTRL_BREAK launcher failed with exit code $ctrlBreakLauncherExitCode."
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakReadyPath -PathType Leaf) `
            'The real CTRL_BREAK harness never reached its ready state.'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakResultPath -PathType Leaf) `
            'The real CTRL_BREAK harness did not produce bounded result evidence.'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakHarnessPidPath -PathType Leaf) `
            'The real CTRL_BREAK harness PID was not captured by its exact-handle launcher.'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakExactHarnessExitPath -PathType Leaf) `
            'The CTRL_BREAK launcher did not confirm exact-handle harness termination.'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakProducerIdentityPath -PathType Leaf) `
            'The CTRL_BREAK producer identity was not captured.'
        Assert-J6Qualification (Test-Path -LiteralPath $ctrlBreakConsumerIdentityPath -PathType Leaf) `
            'The CTRL_BREAK consumer identity was not captured.'

        $ctrlBreakHarnessPid = [int]([IO.File]::ReadAllText($ctrlBreakHarnessPidPath))
        $ctrlBreakExactHarnessExit = [IO.File]::ReadAllText(
            $ctrlBreakExactHarnessExitPath).Trim()
        Assert-J6Qualification (
            $ctrlBreakExactHarnessExit -eq ('{0}:0' -f $ctrlBreakHarnessPid)) `
            'The exact CTRL_BREAK harness handle did not terminate with the qualified result.'

        $ctrlBreakProducerIdentity = Get-Content `
            -LiteralPath $ctrlBreakProducerIdentityPath `
            -Raw | ConvertFrom-Json
        $ctrlBreakConsumerIdentity = Get-Content `
            -LiteralPath $ctrlBreakConsumerIdentityPath `
            -Raw | ConvertFrom-Json
        $ownedProcesses.Add($ctrlBreakProducerIdentity)
        $ownedProcesses.Add($ctrlBreakConsumerIdentity)
        $ctrlBreakResidualBeforeFallbackCleanup = @(
            $ctrlBreakProducerIdentity,
            $ctrlBreakConsumerIdentity | Where-Object {
                -not (Test-J6ProcessIdentityAbsent $_)
            }).Count
        Assert-J6Qualification ($ctrlBreakResidualBeforeFallbackCleanup -eq 0) `
            'A CTRL_BREAK-owned exact process identity survived before fallback teardown.'

        $ctrlBreakResult = Get-Content -LiteralPath $ctrlBreakResultPath -Raw | ConvertFrom-Json
        Add-J6OwnedPipelineProcessEvidence -Collection $ownedProcesses -Result $ctrlBreakResult
        Assert-J6Qualification ($ctrlBreakResult.TokenCancelled -eq $true) `
            'The real CTRL_BREAK event did not cancel the registered token.'
        Assert-J6Qualification ($ctrlBreakResult.PipelineResult -eq 'CANCELLED') `
            'The real CTRL_BREAK event was not classified as CANCELLED.'
        Assert-J6Qualification ($ctrlBreakResult.LocalProcessTreeCleanup -eq 'PASS') `
            'The real CTRL_BREAK event left an owned process root.'
        Assert-J6Qualification ($ctrlBreakResult.ProducerRootAliveAtCleanupStart -eq $true) `
            'The CTRL_BREAK signal terminated the producer wrapper before supervisor cleanup.'
        Assert-J6Qualification ($ctrlBreakResult.ConsumerRootAliveAtCleanupStart -eq $true) `
            'The CTRL_BREAK signal terminated the consumer wrapper before supervisor cleanup.'
        Write-Host 'J6_CTRL_BREAK_WRAPPER_ROOTS_ALIVE_BEFORE_SUPERVISOR_CLEANUP=PASS'
        Write-Host 'J6_CONSOLE_RESIDUAL_BEFORE_FALLBACK_CLEANUP=0'
        Write-Host 'J6_CTRL_BREAK_NEW_CONSOLE_REQUESTED=NO'
        Write-Host 'J6_CONSOLE_CTRL_BREAK_EVENT=PASS'
        Write-Host 'J6_CONSOLE_CANCEL_KEYPRESS_WIRING=PASS'
        Write-Host 'J6_PIPELINE_INTERRUPTION_CLEANUP=PASS'
    }
    finally {
        if ($null -eq $ctrlBreakProducerIdentity -and
            (Test-Path -LiteralPath $ctrlBreakProducerIdentityPath -PathType Leaf)) {
            try {
                $ctrlBreakProducerIdentity = Get-Content `
                    -LiteralPath $ctrlBreakProducerIdentityPath `
                    -Raw | ConvertFrom-Json
            }
            catch {
                # Preserve the primary failure. Malformed evidence cannot
                # authorize a PID-only fallback.
            }
        }
        if ($null -eq $ctrlBreakConsumerIdentity -and
            (Test-Path -LiteralPath $ctrlBreakConsumerIdentityPath -PathType Leaf)) {
            try {
                $ctrlBreakConsumerIdentity = Get-Content `
                    -LiteralPath $ctrlBreakConsumerIdentityPath `
                    -Raw | ConvertFrom-Json
            }
            catch {
                # Preserve the primary failure. Malformed evidence cannot
                # authorize a PID-only fallback.
            }
        }
        foreach ($identity in @(
                $ctrlBreakProducerIdentity,
                $ctrlBreakConsumerIdentity)) {
            if ($null -eq $identity) {
                continue
            }
            $residual = Get-Process `
                -Id ([int]$identity.ProcessId) `
                -ErrorAction SilentlyContinue
            if ($null -eq $residual) {
                continue
            }
            try {
                if ($residual.StartTime.ToUniversalTime().Ticks -eq
                    [long]$identity.StartedAtUtcTicks) {
                    $residual.Kill($true)
                    [void]$residual.WaitForExit(3000)
                }
            }
            catch [InvalidOperationException] {
                # The exact synthetic identity exited during fallback teardown.
            }
            finally {
                $residual.Dispose()
            }
        }
    }

    if ($WithDocker) {
        if (Get-NetTCPConnection -LocalPort 8087 -State Listen -ErrorAction SilentlyContinue) {
            throw 'Stop the local application before the Docker loopback qualification.'
        }
        if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot '.env') -PathType Leaf)) {
            throw 'The local .env file is required for the Docker loopback qualification.'
        }
        $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
        if ($null -eq $dockerCommand) {
            throw 'Docker is required for the Docker loopback qualification.'
        }
        $dockerExecutable = [IO.Path]::GetFullPath($dockerCommand.Source)
        Push-Location $repositoryRoot
        try {
            & $dockerExecutable compose --env-file .env config --quiet
            if ($LASTEXITCODE -ne 0) {
                throw 'Docker Compose configuration failed during loopback qualification.'
            }
            $connectorState = Invoke-J6QualificationDockerScalar `
                -DockerExecutable $dockerExecutable `
                -Sql "select case when not network_enabled and circuit_state = 'LOCKED' then 'SAFE' else 'UNSAFE' end from connector_control where singleton_id = 1"
            Assert-J6Qualification ($connectorState -eq 'SAFE') `
                'The persisted connector was not disabled and LOCKED.'

            $applicationName = 'j6_backup_' + [Guid]::NewGuid().ToString('N')
            $dockerFailureResult = $null
            $dockerEarlyFailingConsumer = 'Start-Sleep -Milliseconds 250; exit 17'
            try {
                $dockerFailureResult = Invoke-J6NativeBinaryPipeline `
                    -Phase BACKUP_ENCRYPTION `
                    -ProducerFilePath $dockerExecutable `
                    -ProducerArgumentList @(
                        'compose', '--env-file', '.env', 'exec', '-T',
                        '-e', "PGAPPNAME=$applicationName",
                        'postgres', 'sh', '-c',
                        'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --no-owner --no-privileges') `
                    -ConsumerFilePath $pwshPath `
                    -ConsumerArgumentList ($commonArguments + @($dockerEarlyFailingConsumer)) `
                    -WorkingDirectory $repositoryRoot `
                    -TimeoutSeconds 15
                Add-J6OwnedPipelineProcessEvidence `
                    -Collection $ownedProcesses `
                    -Result $dockerFailureResult
                Assert-J6Qualification ($dockerFailureResult.PipelineResult -eq 'CONSUMER_FAILED') `
                    'The Docker pg_dump producer did not fail closed after its fake consumer exited.'
                Assert-J6Qualification ($dockerFailureResult.LocalProcessTreeCleanup -eq 'PASS') `
                    'The Docker pg_dump producer root cleanup was not confirmed.'

                $deadline = [DateTime]::UtcNow.AddSeconds(5)
                $consecutiveAbsentSamples = 0
                do {
                    $sessionCount = [long](Invoke-J6QualificationDockerScalar `
                        -DockerExecutable $dockerExecutable `
                        -Sql "select count(*) from pg_stat_activity where application_name = '$applicationName' and pid <> pg_backend_pid()")
                    if ($sessionCount -eq 0) {
                        $consecutiveAbsentSamples++
                    }
                    else {
                        $consecutiveAbsentSamples = 0
                    }
                    if ($consecutiveAbsentSamples -ge 3) {
                        break
                    }
                    Start-Sleep -Milliseconds 50
                } while ([DateTime]::UtcNow -lt $deadline)
                Assert-J6Qualification ($consecutiveAbsentSamples -ge 3) `
                    'An exactly owned Docker pg_dump session survived bounded local tree cleanup.'
                Write-Host 'J6_PIPELINE_DOCKER_PG_DUMP_EARLY_CONSUMER_FAILURE=PASS_FAIL_CLOSED'
                Write-Host 'J6_PIPELINE_REMOTE_OWNED_SESSION_COUNT=0'
            }
            finally {
                $remaining = [long](Invoke-J6QualificationDockerScalar `
                    -DockerExecutable $dockerExecutable `
                    -Sql "select count(*) from pg_stat_activity where application_name = '$applicationName' and pid <> pg_backend_pid()")
                if ($remaining -ne 0) {
                    [void](Invoke-J6QualificationDockerScalar `
                        -DockerExecutable $dockerExecutable `
                        -Sql @"
select count(*)
from (
    select pg_terminate_backend(pid)
    from pg_stat_activity
    where application_name = '$applicationName'
      and pid <> pg_backend_pid()
) terminated
"@)
                }
            }

            $syntheticAgePath = Join-Path $resolvedQualificationRoot 'j6-synthetic-age.exe'
            New-J6SyntheticAgeExecutable -DestinationPath $syntheticAgePath
            $backupScript = Join-Path $repositoryRoot 'scripts\Backup-Restore-J6.ps1'
            $previousSyntheticMode = [Environment]::GetEnvironmentVariable(
                'J6_SYNTHETIC_AGE_MODE',
                [EnvironmentVariableTarget]::Process)
            $previousSyntheticMarker = [Environment]::GetEnvironmentVariable(
                'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER',
                [EnvironmentVariableTarget]::Process)
            $previousFaultInjection = [Environment]::GetEnvironmentVariable(
                'J6_WO024_LOOPBACK_FAULT_INJECTION',
                [EnvironmentVariableTarget]::Process)
            try {
                $nominalDestination = Join-Path $resolvedQualificationRoot 'nominal-loopback.age'
                $nominalMarker = Join-Path $resolvedQualificationRoot `
                    'nominal-passphrase-invocation.marker'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'NOMINAL',
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER',
                    $nominalMarker,
                    [EnvironmentVariableTarget]::Process)
                $nominalBackupOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $nominalDestination `
                    -AgePath $syntheticAgePath `
                    -PipelineTimeoutSeconds 120 `
                    -PipelineCleanupTimeoutMilliseconds 10000 *>&1)
                $nominalBackupExitCode = $LASTEXITCODE
                $nominalBackupText = ($nominalBackupOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6Qualification ($nominalBackupExitCode -eq 0) `
                    'The synthetic passphrase-invocation loopback backup/restore did not complete.'
                Assert-J6Qualification ($nominalBackupText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The nominal loopback backup did not publish a qualified result after cleanup.'
                Assert-J6Qualification (Test-Path -LiteralPath $nominalDestination -PathType Leaf) `
                    'The nominal loopback encrypted backup was not published.'
                Assert-J6Qualification (Test-Path -LiteralPath ($nominalDestination + '.manifest.json') -PathType Leaf) `
                    'The nominal loopback manifest was not published.'
                $nominalManifest = Get-Content `
                    -LiteralPath ($nominalDestination + '.manifest.json') `
                    -Raw | ConvertFrom-Json
                Assert-J6Qualification ($nominalManifest.restoreQualified -eq $true) `
                    'The nominal loopback manifest was not restore-qualified.'
                Assert-J6Qualification (Test-Path -LiteralPath $nominalMarker -PathType Leaf) `
                    'The synthetic age -p invocation path was not exercised.'
                Assert-J6Qualification (
                    [IO.File]::ReadAllText($nominalMarker) -ceq
                        'PASSPHRASE_INVOCATION_PATH=SIMULATED_NO_SECRET') `
                    'The synthetic age marker did not prove the abstract no-secret invocation path.'
                Write-Host 'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION=PASS_ABSTRACT_NO_SECRET'
                Write-Host 'J6_PIPELINE_DOCKER_PG_RESTORE_NOMINAL=PASS'
                Write-Host 'J6_TEMPORARY_RESTORE_DATABASE_CLEANUP=PASS'

                $encryptFailureDestination = Join-Path $resolvedQualificationRoot 'encrypt-failure.age'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'FAIL_ENCRYPT',
                    [EnvironmentVariableTarget]::Process)
                $encryptFailureOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $encryptFailureDestination `
                    -AgePath $syntheticAgePath `
                    -PipelineTimeoutSeconds 120 `
                    -PipelineCleanupTimeoutMilliseconds 10000 *>&1)
                $encryptFailureExitCode = $LASTEXITCODE
                $encryptFailureText = ($encryptFailureOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6Qualification ($encryptFailureExitCode -ne 0) `
                    'The synthetic encryptor failure did not fail the backup script.'
                Assert-J6Qualification (-not $encryptFailureText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The synthetic encryptor failure emitted a favorable qualification.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath $encryptFailureDestination)) `
                    'The synthetic encryptor failure left a final backup.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath ($encryptFailureDestination + '.manifest.json'))) `
                    'The synthetic encryptor failure left a manifest.'
                $encryptFailurePartials = @(Get-ChildItem `
                    -LiteralPath $resolvedQualificationRoot `
                    -Filter 'encrypt-failure.age.partial-*' `
                    -File)
                Assert-J6Qualification ($encryptFailurePartials.Count -eq 0) `
                    'The synthetic encryptor failure left a partial backup.'
                Write-Host 'J6_PARTIAL_FILE_CLEANUP=PASS'

                $decryptFailureDestination = Join-Path $resolvedQualificationRoot 'decrypt-failure.age'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'FAIL_DECRYPT',
                    [EnvironmentVariableTarget]::Process)
                $decryptFailureOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $decryptFailureDestination `
                    -AgePath $syntheticAgePath `
                    -PipelineTimeoutSeconds 120 `
                    -PipelineCleanupTimeoutMilliseconds 10000 *>&1)
                $decryptFailureExitCode = $LASTEXITCODE
                $decryptFailureText = ($decryptFailureOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6Qualification ($decryptFailureExitCode -ne 0) `
                    'The synthetic decryptor failure did not fail the backup script.'
                Assert-J6Qualification (-not $decryptFailureText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The synthetic decryptor failure emitted a favorable qualification.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath $decryptFailureDestination)) `
                    'The synthetic decryptor failure left a final backup.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath ($decryptFailureDestination + '.manifest.json'))) `
                    'The synthetic decryptor failure left a manifest.'
                $decryptFailurePartials = @(Get-ChildItem `
                    -LiteralPath $resolvedQualificationRoot `
                    -Filter 'decrypt-failure.age.partial-*' `
                    -File)
                Assert-J6Qualification ($decryptFailurePartials.Count -eq 0) `
                    'The synthetic decryptor failure left a staged backup.'
                $temporaryDatabaseCount = [long](Invoke-J6QualificationDockerScalar `
                    -DockerExecutable $dockerExecutable `
                    -Sql "select count(*) from pg_database where datname like 'sofascore_j6_restore_%'")
                Assert-J6Qualification ($temporaryDatabaseCount -eq 0) `
                    'A temporary restore database survived the synthetic restore failure.'
                Write-Host 'J6_DECRYPTOR_FAILURE_REJECTS_QUALIFICATION=PASS_FAIL_CLOSED'
                Write-Host 'J6_TEMPORARY_RESTORE_DATABASE_RESIDUAL_COUNT=0'

                $cleanupFailureDestination = Join-Path $resolvedQualificationRoot 'cleanup-failure.age'
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    'NOMINAL',
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_WO024_LOOPBACK_FAULT_INJECTION',
                    'AUTHORIZED',
                    [EnvironmentVariableTarget]::Process)
                $cleanupFailureOutput = @(& $pwshPath `
                    -NoLogo -NoProfile -File $backupScript `
                    -Destination $cleanupFailureDestination `
                    -AgePath $syntheticAgePath `
                    -PipelineTimeoutSeconds 120 `
                    -PipelineCleanupTimeoutMilliseconds 10000 `
                    -QualificationInjectCleanupFailureAfterSuccessfulCleanup *>&1)
                $cleanupFailureExitCode = $LASTEXITCODE
                $cleanupFailureText = ($cleanupFailureOutput | ForEach-Object { $_.ToString() }) -join "`n"
                Assert-J6Qualification ($cleanupFailureExitCode -ne 0) `
                    'The injected cleanup failure did not fail the backup script.'
                Assert-J6Qualification (-not $cleanupFailureText.Contains('J6_BACKUP_RESULT=QUALIFIED')) `
                    'The injected cleanup failure emitted a favorable qualification.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath $cleanupFailureDestination)) `
                    'The injected cleanup failure left a final backup.'
                Assert-J6Qualification (-not (Test-Path -LiteralPath ($cleanupFailureDestination + '.manifest.json'))) `
                    'The injected cleanup failure left a favorable manifest.'
                $cleanupFailureDatabaseCount = [long](Invoke-J6QualificationDockerScalar `
                    -DockerExecutable $dockerExecutable `
                    -Sql "select count(*) from pg_database where datname like 'sofascore_j6_restore_%'")
                Assert-J6Qualification ($cleanupFailureDatabaseCount -eq 0) `
                    'The injected cleanup failure test left a temporary restore database.'
                Write-Host 'J6_CLEANUP_FAILURE_REJECTS_QUALIFICATION=PASS_FAIL_CLOSED'
            }
            finally {
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_MODE',
                    $previousSyntheticMode,
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_SYNTHETIC_AGE_PASSPHRASE_INVOCATION_MARKER',
                    $previousSyntheticMarker,
                    [EnvironmentVariableTarget]::Process)
                [Environment]::SetEnvironmentVariable(
                    'J6_WO024_LOOPBACK_FAULT_INJECTION',
                    $previousFaultInjection,
                    [EnvironmentVariableTarget]::Process)
            }
        }
        finally {
            Pop-Location
        }
    }

    foreach ($ownedProcess in $ownedProcesses) {
        Assert-J6Qualification (Test-J6ProcessIdentityAbsent $ownedProcess) `
            "An owned synthetic process identity remains after qualification: $($ownedProcess.ProcessId)"
    }
    Write-Host 'J6_PIPELINE_RESIDUAL_OWNED_PROCESS_COUNT=0'
    Write-Host 'J6_PIPELINE_HUMAN_INCORRECT_PASSPHRASE_REQUIRED=NO'
    Write-Host 'J6_BACKUP_RESTORE_LOOPBACK_QUALIFICATION=PASS'
}
finally {
    if (Test-Path -LiteralPath $resolvedQualificationRoot -PathType Container) {
        $resolvedTempRoot = [IO.Path]::GetFullPath($resolvedQualificationRoot)
        if (-not $resolvedTempRoot.StartsWith(
                $expectedPrefix,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a qualification directory outside the temp root.'
        }
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
}
