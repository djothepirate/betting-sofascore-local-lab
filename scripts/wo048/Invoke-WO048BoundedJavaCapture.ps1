[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ToolPath,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')][string]$ExpectedToolSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedJavaHome,
    [Parameter(Mandatory = $true)][string]$WorkingDirectory,
    [Parameter(Mandatory = $true)]
    [ValidateLength(4, 45000)]
    [ValidatePattern('^[A-Za-z0-9+/]+={0,2}$')]
    [string]$ArgumentPayloadBase64,
    [Parameter(Mandatory = $true)][string]$StandardOutputPath,
    [Parameter(Mandatory = $true)][string]$StandardErrorPath,
    [ValidateRange(1024, 32768)][int]$MaximumCharactersPerStream = 16384,
    [ValidateRange(1000, 30000)][int]$ChildTimeoutMilliseconds = 28000,
    [ValidateRange(1000, 10000)][int]$ChildCleanupTimeoutMilliseconds = 8000
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0
$validatedStandardOutputPath = $null
$validatedStandardErrorPath = $null

function Write-WO048CaptureFailure {
    param([Parameter(Mandatory = $true)][string]$Code)

    Write-Output 'WO048_BOUNDED_JAVA_CAPTURE=FAIL'
    Write-Output "WO048_BOUNDED_JAVA_CAPTURE_FAILURE=$Code"
}

try {
    if (-not $IsWindows) {
        throw 'PLATFORM'
    }
    $javaHome = [IO.Path]::GetFullPath($ExpectedJavaHome)
    $binPath = [IO.Path]::GetFullPath((Join-Path $javaHome 'bin'))
    $tool = [IO.Path]::GetFullPath($ToolPath)
    $working = [IO.Path]::GetFullPath($WorkingDirectory)
    if (-not (Test-Path -LiteralPath $javaHome -PathType Container) -or
        -not (Test-Path -LiteralPath $binPath -PathType Container) -or
        -not (Test-Path -LiteralPath $tool -PathType Leaf) -or
        -not (Test-Path -LiteralPath $working -PathType Container) -or
        -not (Split-Path -Parent $tool).Equals(
            $binPath, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $tool) -cnotin @('java.exe', 'javac.exe', 'keytool.exe')) {
        throw 'TOOL_PATH'
    }
    foreach ($path in @($javaHome, $binPath, $tool, $working)) {
        if (((Get-Item -LiteralPath $path -Force).Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'REPARSE_POINT'
        }
    }
    $actualToolSha256 = (Get-FileHash -LiteralPath $tool -Algorithm SHA256).Hash.ToLowerInvariant()
    $fileVersion = [Diagnostics.FileVersionInfo]::GetVersionInfo($tool)
    if ($actualToolSha256 -cne $ExpectedToolSha256 -or
        $fileVersion.ProductVersion -notmatch '^25(?:\.|\+|$)' -or
        $fileVersion.OriginalFilename -cne (Split-Path -Leaf $tool)) {
        throw 'TOOL_PROVENANCE'
    }
    if ([Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process') -cne $javaHome -or
        [Environment]::GetEnvironmentVariable('WO048_EXPECTED_JAVA_HOME', 'Process') -cne
            $javaHome) {
        throw 'JAVA_HOME'
    }
    $neutralEnvironmentNames = @(
        'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JDK_JAVAC_OPTIONS',
        'JAVA_OPTS',
        'CLASSPATH', 'MAVEN_OPTS', 'GRADLE_OPTS',
        'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'NO_PROXY', 'FTP_PROXY',
        'SOCKS_PROXY', 'GIT_HTTP_PROXY', 'GIT_HTTPS_PROXY',
        'SSLKEYLOGFILE', 'NSS_SSLKEYLOGFILE', 'JDK_TLS_KEYLOGGER', 'JAVAX_NET_DEBUG',
        'JDK_HTTPCLIENT_HTTPCLIENT_LOG')
    if (@($neutralEnvironmentNames | Where-Object {
            -not [string]::IsNullOrEmpty(
                [Environment]::GetEnvironmentVariable($_, 'Process'))
        }).Count -ne 0) {
        throw 'ENVIRONMENT'
    }

    $stdout = [IO.Path]::GetFullPath($StandardOutputPath)
    $stderr = [IO.Path]::GetFullPath($StandardErrorPath)
    if (-not (Split-Path -Parent $stdout).Equals(
            $working, [StringComparison]::OrdinalIgnoreCase) -or
        -not (Split-Path -Parent $stderr).Equals(
            $working, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $stdout) -cne 'native-java.stdout.private.txt' -or
        (Split-Path -Leaf $stderr) -cne 'native-java.stderr.private.txt' -or
        (Test-Path -LiteralPath $stdout) -or (Test-Path -LiteralPath $stderr)) {
        throw 'CAPTURE_PATH'
    }
    $validatedStandardOutputPath = $stdout
    $validatedStandardErrorPath = $stderr
    $argumentBytes = [Convert]::FromBase64String($ArgumentPayloadBase64)
    if ($argumentBytes.Length -gt 32768) {
        throw 'ARGUMENT_PAYLOAD'
    }
    try {
        $argumentJson = [Text.UTF8Encoding]::new($false, $true).GetString($argumentBytes)
    }
    finally {
        [Array]::Clear($argumentBytes, 0, $argumentBytes.Length)
    }
    $arguments = [string[]](ConvertFrom-Json -InputObject $argumentJson)
    if ($arguments.Count -gt 64 -or @($arguments | Where-Object {
            $_ -isnot [string] -or ([string]$_).Length -gt 4096 -or
            ([string]$_).IndexOf([char]0) -ge 0
        }).Count -ne 0) {
        throw 'ARGUMENT_SHAPE'
    }

    if (-not ('WO048BoundedTextProcess' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

public sealed class WO048BoundedTextProcessResult
{
    public int ExitCode { get; set; }
    public int ProcessId { get; set; }
    public long StartedAtUtcTicks { get; set; }
    public string StandardOutput { get; set; }
    public string StandardError { get; set; }
    public bool TimedOut { get; set; }
    public bool OutputLimitExceeded { get; set; }
    public bool CleanupConfirmed { get; set; }
}

public static class WO048BoundedTextProcess
{
    private static async Task<string> ReadBoundedAsync(StreamReader reader, int maximumCharacters)
    {
        char[] buffer = new char[2048];
        StringBuilder value = new StringBuilder(Math.Min(maximumCharacters, 4096));
        while (true)
        {
            int read = await reader.ReadAsync(buffer, 0, buffer.Length).ConfigureAwait(false);
            if (read == 0)
            {
                return value.ToString();
            }
            if (value.Length > maximumCharacters - read)
            {
                throw new InvalidDataException("OUTPUT_LIMIT");
            }
            value.Append(buffer, 0, read);
        }
    }

    public static WO048BoundedTextProcessResult Run(
        string filePath,
        string workingDirectory,
        string[] arguments,
        string[] neutralEnvironmentNames,
        int maximumCharacters,
        int timeoutMilliseconds,
        int cleanupTimeoutMilliseconds)
    {
        ProcessStartInfo startInfo = new ProcessStartInfo();
        startInfo.FileName = filePath;
        startInfo.WorkingDirectory = workingDirectory;
        startInfo.UseShellExecute = false;
        startInfo.CreateNoWindow = true;
        startInfo.RedirectStandardInput = false;
        startInfo.RedirectStandardOutput = true;
        startInfo.RedirectStandardError = true;
        startInfo.StandardOutputEncoding = new UTF8Encoding(false, true);
        startInfo.StandardErrorEncoding = new UTF8Encoding(false, true);
        foreach (string argument in arguments)
        {
            startInfo.ArgumentList.Add(argument);
        }
        foreach (string name in neutralEnvironmentNames)
        {
            startInfo.Environment.Remove(name);
        }

        using (Process process = new Process())
        {
            process.StartInfo = startInfo;
            if (!process.Start())
            {
                throw new InvalidOperationException("START_FAILED");
            }
            int processId = process.Id;
            long startedAtUtcTicks = process.StartTime.ToUniversalTime().Ticks;
            Task<string> stdout = ReadBoundedAsync(process.StandardOutput, maximumCharacters);
            Task<string> stderr = ReadBoundedAsync(process.StandardError, maximumCharacters);
            Stopwatch timer = Stopwatch.StartNew();
            bool outputLimitExceeded = false;
            bool timedOut = false;
            while (!process.HasExited)
            {
                if (stdout.IsFaulted || stderr.IsFaulted)
                {
                    outputLimitExceeded = true;
                    break;
                }
                if (timer.ElapsedMilliseconds >= timeoutMilliseconds)
                {
                    timedOut = true;
                    break;
                }
                Thread.Sleep(10);
            }
            if (outputLimitExceeded || timedOut)
            {
                try { process.Kill(true); } catch (InvalidOperationException) { }
            }
            bool cleanupConfirmed = process.WaitForExit(cleanupTimeoutMilliseconds);
            if (!cleanupConfirmed)
            {
                try { process.Kill(true); } catch (InvalidOperationException) { }
                cleanupConfirmed = process.WaitForExit(cleanupTimeoutMilliseconds);
            }
            string standardOutput = String.Empty;
            string standardError = String.Empty;
            if (cleanupConfirmed && !outputLimitExceeded)
            {
                try
                {
                    if (!Task.WaitAll(new Task[] { stdout, stderr }, cleanupTimeoutMilliseconds))
                    {
                        cleanupConfirmed = false;
                    }
                    else
                    {
                        standardOutput = stdout.GetAwaiter().GetResult();
                        standardError = stderr.GetAwaiter().GetResult();
                    }
                }
                catch (AggregateException aggregate)
                {
                    aggregate.Handle(error => error is InvalidDataException ||
                        error is DecoderFallbackException);
                    outputLimitExceeded = true;
                }
                catch (InvalidDataException)
                {
                    outputLimitExceeded = true;
                }
                catch (DecoderFallbackException)
                {
                    outputLimitExceeded = true;
                }
            }
            return new WO048BoundedTextProcessResult {
                ExitCode = cleanupConfirmed && process.HasExited ? process.ExitCode : Int32.MinValue,
                ProcessId = processId,
                StartedAtUtcTicks = startedAtUtcTicks,
                StandardOutput = standardOutput,
                StandardError = standardError,
                TimedOut = timedOut,
                OutputLimitExceeded = outputLimitExceeded,
                CleanupConfirmed = cleanupConfirmed
            };
        }
    }
}
'@
    }

    # The actual child environment is inherited from this already sanitized
    # process; the native runner below does not permit shell expansion.
    $result = [WO048BoundedTextProcess]::Run(
        $tool,
        $working,
        [string[]]$arguments,
        [string[]]$neutralEnvironmentNames,
        $MaximumCharactersPerStream,
        $ChildTimeoutMilliseconds,
        $ChildCleanupTimeoutMilliseconds)
    if (-not $result.CleanupConfirmed) {
        throw 'CHILD_CLEANUP'
    }
    if ($result.TimedOut) {
        throw 'CHILD_TIMEOUT'
    }
    if ($result.OutputLimitExceeded) {
        throw 'OUTPUT_LIMIT'
    }
    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    foreach ($capture in @(
            @($stdout, $result.StandardOutput),
            @($stderr, $result.StandardError))) {
        $captureBytes = $utf8.GetBytes([string]$capture[1])
        try {
            $captureStream = [IO.FileStream]::new(
                [string]$capture[0],
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None)
            try {
                $captureStream.Write($captureBytes, 0, $captureBytes.Length)
                $captureStream.Flush($true)
            }
            finally {
                $captureStream.Dispose()
            }
        }
        finally {
            [Array]::Clear($captureBytes, 0, $captureBytes.Length)
        }
    }
    $stdoutLength = (Get-Item -LiteralPath $stdout -Force).Length
    $stderrLength = (Get-Item -LiteralPath $stderr -Force).Length
    if ($stdoutLength -gt 65536 -or $stderrLength -gt 65536) {
        throw 'ENCODED_OUTPUT_LIMIT'
    }
    Write-Output 'WO048_BOUNDED_JAVA_CAPTURE=PASS'
    Write-Output "WO048_CHILD_EXIT_CODE=$($result.ExitCode)"
    Write-Output "WO048_CHILD_PROCESS_ID=$($result.ProcessId)"
    Write-Output "WO048_CHILD_STARTED_AT_UTC_TICKS=$($result.StartedAtUtcTicks)"
    Write-Output "WO048_CHILD_STDOUT_BYTES=$stdoutLength"
    Write-Output "WO048_CHILD_STDERR_BYTES=$stderrLength"
    Write-Output 'WO048_CHILD_CLEANUP=PASS'
    exit 0
}
catch {
    foreach ($path in @($validatedStandardOutputPath, $validatedStandardErrorPath)) {
        if (-not [string]::IsNullOrWhiteSpace($path) -and
            (Test-Path -LiteralPath $path -PathType Leaf)) {
            Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
        }
    }
    $failureCode = if ($_.Exception.Message -cmatch '^[A-Z_]+$') {
        $_.Exception.Message
    }
    else {
        'UNCLASSIFIED'
    }
    Write-WO048CaptureFailure -Code $failureCode
    exit 90
}
