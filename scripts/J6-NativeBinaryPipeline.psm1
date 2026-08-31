Set-StrictMode -Version 3.0

if ($IsWindows -and -not ('J6ProcessTreeSnapshot' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;

public static class J6ProcessTreeSnapshot
{
    private const uint TH32CS_SNAPPROCESS = 0x00000002;
    private static readonly IntPtr InvalidHandleValue = new IntPtr(-1);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct PROCESSENTRY32
    {
        public uint dwSize;
        public uint cntUsage;
        public uint th32ProcessID;
        public IntPtr th32DefaultHeapID;
        public uint th32ModuleID;
        public uint cntThreads;
        public uint th32ParentProcessID;
        public int pcPriClassBase;
        public uint dwFlags;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 260)]
        public string szExeFile;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr CreateToolhelp32Snapshot(uint flags, uint processId);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool Process32FirstW(IntPtr snapshot, ref PROCESSENTRY32 entry);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool Process32NextW(IntPtr snapshot, ref PROCESSENTRY32 entry);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr handle);

    public static int[] GetDescendantProcessIds(int rootProcessId)
    {
        IntPtr snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (snapshot == InvalidHandleValue)
        {
            throw new InvalidOperationException(
                "Could not take the owned process-tree snapshot. Win32=" + Marshal.GetLastWin32Error());
        }

        try
        {
            var childrenByParent = new Dictionary<int, List<int>>();
            var entry = new PROCESSENTRY32 { dwSize = (uint)Marshal.SizeOf<PROCESSENTRY32>() };
            if (Process32FirstW(snapshot, ref entry))
            {
                do
                {
                    int parent = unchecked((int)entry.th32ParentProcessID);
                    int process = unchecked((int)entry.th32ProcessID);
                    if (!childrenByParent.TryGetValue(parent, out List<int> children))
                    {
                        children = new List<int>();
                        childrenByParent[parent] = children;
                    }
                    children.Add(process);
                    entry.dwSize = (uint)Marshal.SizeOf<PROCESSENTRY32>();
                }
                while (Process32NextW(snapshot, ref entry));
            }

            var descendants = new List<int>();
            var pending = new Queue<int>();
            var visited = new HashSet<int> { rootProcessId };
            pending.Enqueue(rootProcessId);
            while (pending.Count > 0)
            {
                int parent = pending.Dequeue();
                if (!childrenByParent.TryGetValue(parent, out List<int> children))
                {
                    continue;
                }
                foreach (int child in children)
                {
                    if (visited.Add(child))
                    {
                        descendants.Add(child);
                        pending.Enqueue(child);
                    }
                }
            }
            return descendants.ToArray();
        }
        finally
        {
            CloseHandle(snapshot);
        }
    }
}

public sealed class J6ConsoleCancellationRegistration : IDisposable
{
    private readonly CancellationTokenSource source = new CancellationTokenSource();
    private readonly ConsoleCancelEventHandler handler;
    private bool disposed;

    public J6ConsoleCancellationRegistration()
    {
        handler = OnCancelKeyPress;
        Console.CancelKeyPress += handler;
    }

    public CancellationToken Token => source.Token;

    public void CancelAfterForQualification(int milliseconds)
    {
        source.CancelAfter(milliseconds);
    }

    private void OnCancelKeyPress(object sender, ConsoleCancelEventArgs eventArgs)
    {
        eventArgs.Cancel = true;
        source.Cancel();
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }
        disposed = true;
        Console.CancelKeyPress -= handler;
        source.Dispose();
    }
}

public sealed class J6NativeConfinementCleanupException : InvalidOperationException
{
    public J6NativeConfinementCleanupException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}

public sealed class J6WindowsKillOnCloseJob : IDisposable
{
    private const int JobObjectBasicAccountingInformation = 1;
    private const int JobObjectExtendedLimitInformation = 9;
    private const uint JobObjectLimitKillOnJobClose = 0x00002000;
    private IntPtr handle;
    private bool disposed;

    [StructLayout(LayoutKind.Sequential)]
    private struct IO_COUNTERS
    {
        public ulong ReadOperationCount;
        public ulong WriteOperationCount;
        public ulong OtherOperationCount;
        public ulong ReadTransferCount;
        public ulong WriteTransferCount;
        public ulong OtherTransferCount;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_BASIC_LIMIT_INFORMATION
    {
        public long PerProcessUserTimeLimit;
        public long PerJobUserTimeLimit;
        public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize;
        public UIntPtr MaximumWorkingSetSize;
        public uint ActiveProcessLimit;
        public UIntPtr Affinity;
        public uint PriorityClass;
        public uint SchedulingClass;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
    {
        public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
        public IO_COUNTERS IoInfo;
        public UIntPtr ProcessMemoryLimit;
        public UIntPtr JobMemoryLimit;
        public UIntPtr PeakProcessMemoryUsed;
        public UIntPtr PeakJobMemoryUsed;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_BASIC_ACCOUNTING_INFORMATION
    {
        public long TotalUserTime;
        public long TotalKernelTime;
        public long ThisPeriodTotalUserTime;
        public long ThisPeriodTotalKernelTime;
        public uint TotalPageFaultCount;
        public uint TotalProcesses;
        public uint ActiveProcesses;
        public uint TotalTerminatedProcesses;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateJobObject(IntPtr securityAttributes, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetInformationJobObject(
        IntPtr job,
        int informationClass,
        ref JOBOBJECT_EXTENDED_LIMIT_INFORMATION information,
        uint informationLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool QueryInformationJobObject(
        IntPtr job,
        int informationClass,
        ref JOBOBJECT_BASIC_ACCOUNTING_INFORMATION information,
        uint informationLength,
        IntPtr returnLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool IsProcessInJob(IntPtr process, IntPtr job, out bool result);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool TerminateJobObject(IntPtr job, uint exitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr handle);

    public J6WindowsKillOnCloseJob()
    {
        handle = CreateJobObject(IntPtr.Zero, null);
        if (handle == IntPtr.Zero)
        {
            throw Error("Could not create the native confinement job");
        }

        var limits = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
        limits.BasicLimitInformation.LimitFlags = JobObjectLimitKillOnJobClose;
        if (!SetInformationJobObject(
                handle,
                JobObjectExtendedLimitInformation,
                ref limits,
                (uint)Marshal.SizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>()))
        {
            int error = Marshal.GetLastWin32Error();
            CloseHandle(handle);
            handle = IntPtr.Zero;
            throw new InvalidOperationException(
                "Could not configure the native confinement job. Win32=" + error);
        }
    }

    public void AssignProcess(IntPtr processHandle)
    {
        EnsureOpen();
        if (!AssignProcessToJobObject(handle, processHandle))
        {
            throw Error("Could not assign the gated native host to its confinement job");
        }
        bool assigned;
        if (!IsProcessInJob(processHandle, handle, out assigned) || !assigned)
        {
            throw Error("Could not verify native host confinement");
        }
    }

    public int GetActiveProcessCount()
    {
        EnsureOpen();
        var accounting = new JOBOBJECT_BASIC_ACCOUNTING_INFORMATION();
        if (!QueryInformationJobObject(
                handle,
                JobObjectBasicAccountingInformation,
                ref accounting,
                (uint)Marshal.SizeOf<JOBOBJECT_BASIC_ACCOUNTING_INFORMATION>(),
                IntPtr.Zero))
        {
            throw Error("Could not verify the native confinement job membership");
        }
        return checked((int)accounting.ActiveProcesses);
    }

    public void Terminate()
    {
        EnsureOpen();
        if (!TerminateJobObject(handle, 197))
        {
            int error = Marshal.GetLastWin32Error();
            if (error != 5)
            {
                throw new InvalidOperationException(
                    "Could not terminate the native confinement job. Win32=" + error);
            }
            throw new InvalidOperationException(
                "Native confinement job termination was not verifiable. Win32=" + error);
        }
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }
        if (handle != IntPtr.Zero)
        {
            if (!CloseHandle(handle))
            {
                throw Error("Could not close the native confinement job handle");
            }
            handle = IntPtr.Zero;
        }
        disposed = true;
        GC.SuppressFinalize(this);
    }

    ~J6WindowsKillOnCloseJob()
    {
        if (handle != IntPtr.Zero)
        {
            CloseHandle(handle);
            handle = IntPtr.Zero;
        }
    }

    private void EnsureOpen()
    {
        if (disposed || handle == IntPtr.Zero)
        {
            throw new ObjectDisposedException(nameof(J6WindowsKillOnCloseJob));
        }
    }

    private static InvalidOperationException Error(string operation)
    {
        return new InvalidOperationException(
            operation + ". Win32=" + Marshal.GetLastWin32Error());
    }
}
'@
}

function New-J6NativeProcessStartInfo {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [switch]$RedirectStandardOutput,
        [switch]$RedirectStandardInput,
        [switch]$RedirectStandardError
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $false
    $startInfo.RedirectStandardOutput = $RedirectStandardOutput.IsPresent
    $startInfo.RedirectStandardInput = $RedirectStandardInput.IsPresent
    $startInfo.RedirectStandardError = $RedirectStandardError.IsPresent
    $startInfo.WorkingDirectory = $WorkingDirectory
    foreach ($argument in $ArgumentList) {
        [void]$startInfo.ArgumentList.Add($argument)
    }
    return $startInfo
}

function Start-J6ConfinedNativeProcess {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [switch]$RedirectStandardOutput,
        [switch]$RedirectStandardInput,
        [switch]$RedirectStandardError
    )

    if (-not $IsWindows) {
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = New-J6NativeProcessStartInfo `
            -FilePath $FilePath `
            -ArgumentList $ArgumentList `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput:$RedirectStandardOutput.IsPresent `
            -RedirectStandardInput:$RedirectStandardInput.IsPresent `
            -RedirectStandardError:$RedirectStandardError.IsPresent
        if (-not $process.Start()) {
            $process.Dispose()
            throw 'The native process did not start.'
        }
        return [pscustomobject]@{
            Process = $process
            Identity = New-J6OwnedProcessIdentity -Process $process
            Job = $null
            Gate = $null
            Confinement = 'PROCESS_TREE_FALLBACK'
        }
    }

    $hostPath = Join-Path $PSScriptRoot 'J6-NativeProcessHost.ps1'
    $pwshPath = Join-Path $PSHOME 'pwsh.exe'
    if (-not (Test-Path -LiteralPath $hostPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $pwshPath -PathType Leaf)) {
        throw 'The gated Windows native-process host is unavailable.'
    }

    $argumentJson = ConvertTo-Json -InputObject ([string[]]$ArgumentList) -Compress
    $argumentPayload = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes($argumentJson))
    $gateName = 'Local\J6NativeGate_' + [Guid]::NewGuid().ToString('N')
    $createdNew = $false
    $gate = [Threading.EventWaitHandle]::new(
        $false,
        [Threading.EventResetMode]::ManualReset,
        $gateName,
        [ref]$createdNew)
    if (-not $createdNew) {
        $gate.Dispose()
        throw 'A unique native-process gate could not be created.'
    }

    $job = $null
    $process = $null
    try {
        $job = [J6WindowsKillOnCloseJob]::new()
        $hostArguments = @(
            '-NoLogo', '-NoProfile', '-NonInteractive',
            '-File', $hostPath,
            '-GateName', $gateName,
            '-TargetFilePath', $FilePath,
            '-ArgumentPayloadBase64', $argumentPayload)
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = New-J6NativeProcessStartInfo `
            -FilePath $pwshPath `
            -ArgumentList $hostArguments `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput:$RedirectStandardOutput.IsPresent `
            -RedirectStandardInput:$RedirectStandardInput.IsPresent `
            -RedirectStandardError:$RedirectStandardError.IsPresent
        if (-not $process.Start()) {
            throw 'The gated native-process host did not start.'
        }

        # The host script cannot create the target before this assignment and
        # signal. Every subsequently created target/descendant inherits the
        # kill-on-close job membership.
        $identity = New-J6OwnedProcessIdentity -Process $process
        $job.AssignProcess($process.Handle)
        if ($job.GetActiveProcessCount() -ne 1) {
            throw 'The pre-release native confinement membership is ambiguous.'
        }
        if (-not $gate.Set()) {
            throw 'The confined native-process gate could not be released.'
        }

        return [pscustomobject]@{
            Process = $process
            Identity = $identity
            Job = $job
            Gate = $gate
            Confinement = 'WINDOWS_KILL_ON_JOB_CLOSE'
        }
    }
    catch {
        $startFailure = $_.Exception
        $cleanupFailures = [Collections.Generic.List[string]]::new()
        $startCleanupDeadline = [DateTime]::UtcNow.AddSeconds(5)
        if ($null -ne $job) {
            try {
                $job.Terminate()
                while ($job.GetActiveProcessCount() -ne 0 -and
                    [DateTime]::UtcNow -lt $startCleanupDeadline) {
                    Start-Sleep -Milliseconds 25
                }
                if ($job.GetActiveProcessCount() -ne 0) {
                    $cleanupFailures.Add('JOB_NOT_EMPTY')
                }
            }
            catch {
                $cleanupFailures.Add('JOB_TERMINATION_UNVERIFIABLE')
            }
        }
        if ($null -ne $process) {
            try {
                if (-not $process.HasExited) {
                    $process.Kill($true)
                    $remainingMilliseconds = [Math]::Max(
                        0,
                        [Math]::Floor(
                            ($startCleanupDeadline - [DateTime]::UtcNow).TotalMilliseconds))
                    if (-not $process.WaitForExit([int]$remainingMilliseconds)) {
                        $cleanupFailures.Add('HOST_WAIT_TIMEOUT')
                    }
                }
                if (-not $process.HasExited) {
                    $cleanupFailures.Add('HOST_STILL_ACTIVE')
                }
            }
            catch {
                $cleanupFailures.Add('HOST_TERMINATION_UNVERIFIABLE')
            }
        }
        if ($null -ne $job) {
            try {
                $job.Dispose()
            }
            catch {
                $cleanupFailures.Add('JOB_HANDLE_CLOSE_UNVERIFIABLE')
            }
        }
        if ($null -ne $process) {
            try { $process.Dispose() } catch { }
        }
        try { $gate.Dispose() } catch {
            $cleanupFailures.Add('GATE_CLOSE_UNVERIFIABLE')
        }
        if ($cleanupFailures.Count -ne 0) {
            throw [J6NativeConfinementCleanupException]::new(
                'The native confinement start cleanup could not be confirmed.',
                $startFailure)
        }
        throw $startFailure
    }
}

function New-J6OwnedProcessIdentity {
    param([Parameter(Mandatory = $true)][Diagnostics.Process]$Process)

    return [pscustomobject]@{
        ProcessId = $Process.Id
        StartedAtUtcTicks = $Process.StartTime.ToUniversalTime().Ticks
        ExitedAtUtcTicks = $null
    }
}

function Get-J6IdentityProcess {
    param([Parameter(Mandatory = $true)]$Identity)

    $candidate = $null
    try {
        $candidate = [Diagnostics.Process]::GetProcessById([int]$Identity.ProcessId)
        if ($candidate.StartTime.ToUniversalTime().Ticks -ne [long]$Identity.StartedAtUtcTicks) {
            $candidate.Dispose()
            $candidate = $null
            return $null
        }
        return $candidate
    }
    catch [ArgumentException] {
        if ($null -ne $candidate) {
            $candidate.Dispose()
        }
        return $null
    }
    catch {
        if ($null -ne $candidate) {
            $candidate.Dispose()
        }
        throw [InvalidOperationException]::new(
            "The exact process identity for PID $($Identity.ProcessId) could not be verified.",
            $_.Exception)
    }
}

function Update-J6OwnedDescendantIdentities {
    param(
        $RootIdentity,
        [Parameter(Mandatory = $true)]
        [Collections.Generic.Dictionary[string, object]]$OwnedDescendants
    )

    if ($null -eq $RootIdentity -or -not $IsWindows) {
        return
    }

    try {
    $exactRoot = Get-J6IdentityProcess -Identity $RootIdentity
    $rootWasExactAndAliveAtSnapshot = $false
    $exitCutoffTicks = if ($null -eq $RootIdentity.ExitedAtUtcTicks) {
        $null
    }
    else {
        [long]$RootIdentity.ExitedAtUtcTicks
    }
    $descendantIds = @()
    if ($null -ne $exactRoot) {
        try {
            if ($exactRoot.HasExited) {
                $exitTime = $exactRoot.ExitTime
                if ($null -eq $exitTime) {
                    return
                }
                $exitCutoffTicks = $exitTime.ToUniversalTime().Ticks
                $RootIdentity.ExitedAtUtcTicks = $exitCutoffTicks
            }
            else {
                $rootWasExactAndAliveAtSnapshot = $true
            }
            $descendantIds = @([J6ProcessTreeSnapshot]::GetDescendantProcessIds(
                    [int]$RootIdentity.ProcessId))
            if ($rootWasExactAndAliveAtSnapshot -and $exactRoot.HasExited) {
                $exitTime = $exactRoot.ExitTime
                if ($null -ne $exitTime) {
                    $exitCutoffTicks = $exitTime.ToUniversalTime().Ticks
                    $RootIdentity.ExitedAtUtcTicks = $exitCutoffTicks
                }
            }
        }
        finally {
            $exactRoot.Dispose()
        }
    }
    elseif ($null -ne $exitCutoffTicks) {
        # The original root is gone. A reused PID may now be present in the
        # snapshot, so only pre-exit descendants of the verified old root may
        # still be claimed.
        $descendantIds = @([J6ProcessTreeSnapshot]::GetDescendantProcessIds(
                [int]$RootIdentity.ProcessId))
    }
    else {
        # Never discover from a bare PID after the exact root identity vanished.
        return
    }

    foreach ($descendantId in $descendantIds) {
        $descendant = $null
        try {
            $descendant = [Diagnostics.Process]::GetProcessById($descendantId)
            $startedAtTicks = $descendant.StartTime.ToUniversalTime().Ticks
            $withinOwnedLifetime = $startedAtTicks -ge [long]$RootIdentity.StartedAtUtcTicks -and
                ($rootWasExactAndAliveAtSnapshot -or
                    ($null -ne $exitCutoffTicks -and $startedAtTicks -le $exitCutoffTicks))
            if ($withinOwnedLifetime) {
                $key = "$descendantId`:$startedAtTicks"
                if (-not $OwnedDescendants.ContainsKey($key)) {
                    $OwnedDescendants.Add($key, [pscustomobject]@{
                            ProcessId = $descendantId
                            StartedAtUtcTicks = $startedAtTicks
                        })
                }
            }
        }
        catch [ArgumentException] {
            # The descendant exited between the snapshot and identity capture.
        }
        catch {
            throw [InvalidOperationException]::new(
                "Descendant identity $descendantId could not be verified.",
                $_.Exception)
        }
        finally {
            if ($null -ne $descendant) {
                $descendant.Dispose()
            }
        }
    }
    }
    catch {
        throw [InvalidOperationException]::new(
            "Owned descendant identity update failed at $($_.InvocationInfo.PositionMessage)",
            $_.Exception)
    }
}

function Update-J6OwnedRootExitEvidence {
    param(
        [Diagnostics.Process]$Process,
        $RootIdentity
    )

    if ($null -eq $Process -or $null -eq $RootIdentity -or
        $null -ne $RootIdentity.ExitedAtUtcTicks) {
        return
    }
    try {
        if ($Process.Id -eq [int]$RootIdentity.ProcessId -and
            $Process.StartTime.ToUniversalTime().Ticks -eq [long]$RootIdentity.StartedAtUtcTicks -and
            $Process.HasExited) {
            $exitTime = $Process.ExitTime
            if ($null -ne $exitTime) {
                $RootIdentity.ExitedAtUtcTicks = $exitTime.ToUniversalTime().Ticks
            }
        }
    }
    catch [ArgumentException] {
        # The exact process object cannot provide additional exit evidence.
    }
    catch [InvalidOperationException] {
        # The exact process object cannot provide additional exit evidence.
    }
    catch [ComponentModel.Win32Exception] {
        # The exact process object cannot provide additional exit evidence.
    }
    catch [UnauthorizedAccessException] {
        # The exact process object cannot provide additional exit evidence.
    }
}

function Stop-J6OwnedProcessTree {
    param(
        [Diagnostics.Process]$Process,
        $RootIdentity,
        [Parameter(Mandatory = $true)]
        [Collections.Generic.Dictionary[string, object]]$OwnedDescendants,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $killRequested = $false
    $descendantKillRequested = $false
    $unconfirmedDescendantsWithoutRootIdentity = $false
    $rootAliveBeforeCleanup = $false
    if ($null -ne $Process) {
        try {
            $rootAliveBeforeCleanup = -not $Process.HasExited
        }
        catch {
            $unconfirmedDescendantsWithoutRootIdentity = $true
        }
    }
    Update-J6OwnedRootExitEvidence -Process $Process -RootIdentity $RootIdentity
    Update-J6OwnedDescendantIdentities `
        -RootIdentity $RootIdentity `
        -OwnedDescendants $OwnedDescendants

    foreach ($identity in @($OwnedDescendants.Values)) {
        $descendant = Get-J6IdentityProcess -Identity $identity
        if ($null -eq $descendant) {
            continue
        }
        try {
            $descendantKillRequested = $true
            $descendant.Kill($true)
        }
        catch [InvalidOperationException] {
            # The exact descendant exited before the kill request.
        }
        catch {
            # The bounded identity verification below is authoritative.
        }
        finally {
            $descendant.Dispose()
        }
    }

    if ($null -ne $Process -and $null -ne $RootIdentity) {
        $exactRoot = Get-J6IdentityProcess -Identity $RootIdentity
        if ($null -ne $exactRoot) {
            try {
                if (-not $exactRoot.HasExited) {
                    $killRequested = $true
                    $exactRoot.Kill($true)
                }
            }
            catch [InvalidOperationException] {
                # The exact process exited before the kill request.
            }
            catch {
                # The bounded identity verification below is authoritative.
            }
            finally {
                $exactRoot.Dispose()
            }
        }
    }
    elseif ($null -ne $Process) {
        try {
            if (-not $Process.HasExited) {
                $killRequested = $true
                $Process.Kill($true)
            }
            else {
                $unconfirmedDescendantsWithoutRootIdentity = $true
            }
        }
        catch [InvalidOperationException] {
            $unconfirmedDescendantsWithoutRootIdentity = $true
        }
    }
    Update-J6OwnedRootExitEvidence -Process $Process -RootIdentity $RootIdentity

    $deadline = [DateTime]::UtcNow.AddMilliseconds($CleanupTimeoutMilliseconds)
    $rootExited = $true
    $descendantsExited = -not $unconfirmedDescendantsWithoutRootIdentity
    do {
        Update-J6OwnedRootExitEvidence -Process $Process -RootIdentity $RootIdentity
        Update-J6OwnedDescendantIdentities `
            -RootIdentity $RootIdentity `
            -OwnedDescendants $OwnedDescendants
        $loopRootProbe = if ($null -eq $RootIdentity) {
            $Process
        }
        else {
            Get-J6IdentityProcess -Identity $RootIdentity
        }
        $rootExited = if ($null -eq $loopRootProbe) {
            $true
        }
        else {
            try { $loopRootProbe.HasExited } catch [InvalidOperationException] { $true }
        }
        if ($null -ne $loopRootProbe -and $loopRootProbe -ne $Process) {
            $loopRootProbe.Dispose()
        }
        $descendantsExited = -not $unconfirmedDescendantsWithoutRootIdentity
        foreach ($identity in @($OwnedDescendants.Values)) {
            $descendant = Get-J6IdentityProcess -Identity $identity
            if ($null -eq $descendant) {
                continue
            }
            $descendantsExited = $false
            try {
                $descendantKillRequested = $true
                $descendant.Kill($true)
            }
            catch [InvalidOperationException] {
                # The descendant exited during bounded verification.
            }
            catch {
                # A surviving exact identity yields cleanup failure below.
            }
            finally {
                $descendant.Dispose()
            }
        }
        if (-not $rootExited -or -not $descendantsExited) {
            Start-Sleep -Milliseconds 25
        }
    } while ((-not $rootExited -or -not $descendantsExited) -and
        [DateTime]::UtcNow -lt $deadline)

    Update-J6OwnedRootExitEvidence -Process $Process -RootIdentity $RootIdentity
    $rootProbe = if ($null -eq $RootIdentity) {
        $Process
    }
    else {
        Get-J6IdentityProcess -Identity $RootIdentity
    }
    $rootExited = if ($null -eq $rootProbe) {
        $true
    }
    else {
        try { $rootProbe.HasExited } catch [InvalidOperationException] { $true }
    }
    if ($null -ne $rootProbe -and $rootProbe -ne $Process) {
        $rootProbe.Dispose()
    }
    $descendantsExited = -not $unconfirmedDescendantsWithoutRootIdentity
    foreach ($identity in @($OwnedDescendants.Values)) {
        $probe = Get-J6IdentityProcess -Identity $identity
        if ($null -ne $probe) {
            $descendantsExited = $false
            $probe.Dispose()
        }
    }

    return [pscustomobject]@{
        KillRequested = $killRequested
        DescendantKillRequested = $descendantKillRequested
        Exited = $rootExited
        DescendantsExited = $descendantsExited
        RootAliveBeforeCleanup = $rootAliveBeforeCleanup
        ObservedDescendantPids = @($OwnedDescendants.Values | ForEach-Object { $_.ProcessId })
    }
}

function Stop-J6ConfinedProcess {
    param(
        [Diagnostics.Process]$Process,
        $RootIdentity,
        [Parameter(Mandatory = $true)]
        [Collections.Generic.Dictionary[string, object]]$OwnedDescendants,
        $Job,
        [Parameter(Mandatory = $true)][DateTime]$CleanupDeadlineUtc,
        [switch]$AllowNaturalExitUntilDeadline
    )

    $remainingMilliseconds = [int][Math]::Max(
        1,
        [Math]::Floor(($CleanupDeadlineUtc - [DateTime]::UtcNow).TotalMilliseconds))
    if (-not $IsWindows -or $null -eq $Job) {
        return Stop-J6OwnedProcessTree `
            -Process $Process `
            -RootIdentity $RootIdentity `
            -OwnedDescendants $OwnedDescendants `
            -CleanupTimeoutMilliseconds $remainingMilliseconds
    }

    $activeBeforeCleanup = $null
    $activeAfterCleanup = $null
    $killRequested = $false
    $descendantKillRequested = $false
    $unverifiable = $false
    $rootStillAlive = $false
    try {
        if ($null -ne $Process) {
            $rootStillAlive = -not $Process.HasExited
        }
        $activeBeforeCleanup = $Job.GetActiveProcessCount()
        $activeAtTermination = $activeBeforeCleanup
        if ($AllowNaturalExitUntilDeadline -and $activeAtTermination -gt 0) {
            $naturalGraceMilliseconds = [int][Math]::Min(
                250,
                [Math]::Max(
                    0,
                    [Math]::Floor(
                        ($CleanupDeadlineUtc - [DateTime]::UtcNow).TotalMilliseconds) - 100))
            $naturalExitDeadline = [DateTime]::UtcNow.AddMilliseconds(
                $naturalGraceMilliseconds)
            while ($activeAtTermination -gt 0 -and
                [DateTime]::UtcNow -lt $naturalExitDeadline) {
                Start-Sleep -Milliseconds 25
                $activeAtTermination = $Job.GetActiveProcessCount()
            }
        }
        if ($activeAtTermination -gt 0) {
            $killRequested = $true
            $descendantKillRequested = $activeAtTermination -gt $(if ($rootStillAlive) { 1 } else { 0 })
            $Job.Terminate()
        }

        do {
            $activeAfterCleanup = $Job.GetActiveProcessCount()
            if ($activeAfterCleanup -eq 0) {
                break
            }
            Start-Sleep -Milliseconds 25
        } while ([DateTime]::UtcNow -lt $CleanupDeadlineUtc)
    }
    catch {
        $unverifiable = $true
    }

    if ($null -eq $activeAfterCleanup) {
        $unverifiable = $true
    }
    $empty = -not $unverifiable -and $activeAfterCleanup -eq 0
    return [pscustomobject]@{
        KillRequested = $killRequested
        DescendantKillRequested = $descendantKillRequested
        Exited = $empty
        DescendantsExited = $empty
        RootAliveBeforeCleanup = $rootStillAlive
        ObservedDescendantPids = @()
        ActiveProcessesBeforeCleanup = $activeBeforeCleanup
        ActiveProcessesAfterCleanup = $activeAfterCleanup
        Unverifiable = $unverifiable
        Confinement = 'WINDOWS_KILL_ON_JOB_CLOSE'
    }
}

function Get-J6NativeExitCode {
    param([Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return $null
    }
    try {
        if (-not $Process.HasExited) {
            return $null
        }
        return $Process.ExitCode
    }
    catch [InvalidOperationException] {
        return $null
    }
}

function Get-J6NativeProcessStatus {
    param(
        [Diagnostics.Process]$Process,
        [bool]$KillRequested,
        [bool]$TimedOut,
        [bool]$Cancelled
    )

    if ($null -eq $Process) {
        return 'NOT_STARTED'
    }
    if ($KillRequested) {
        if ($TimedOut) {
            return 'TIMEOUT'
        }
        if ($Cancelled) {
            return 'CANCELLED'
        }
        return 'SUPERVISOR_CANCELLED'
    }
    try {
        if (-not $Process.HasExited) {
            return 'RESIDUAL'
        }
        if ($Process.ExitCode -eq 0) {
            return 'EXIT_0'
        }
        return 'EXIT_NONZERO'
    }
    catch [InvalidOperationException] {
        return 'NOT_STARTED'
    }
}

function New-J6ConsoleCancellationRegistration {
    return [J6ConsoleCancellationRegistration]::new()
}

function Invoke-J6BoundedNativeCommand {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [ValidateRange(1, 300000)][int]$TimeoutMilliseconds,
        [ValidateRange(100, 30000)][int]$CleanupTimeoutMilliseconds = 5000,
        [Parameter(DontShow = $true)]
        [switch]$QualificationInjectSupervisionFailureAfterStart,
        [Parameter(DontShow = $true)]
        [string]$QualificationSupervisionReadyPath,
        [Parameter(DontShow = $true)]
        [switch]$QualificationWaitForProcessExitBeforeSupervisionFailure
    )

    if (-not [IO.Path]::IsPathFullyQualified($FilePath) -or
        -not (Test-Path -LiteralPath $FilePath -PathType Leaf)) {
        throw 'The bounded native command executable must be an existing absolute path.'
    }
    if (-not (Test-Path -LiteralPath $WorkingDirectory -PathType Container)) {
        throw 'The bounded native command working directory does not exist.'
    }
    if ($QualificationInjectSupervisionFailureAfterStart -and
        [Environment]::GetEnvironmentVariable(
            'J6_WO024_LOOPBACK_FAULT_INJECTION',
            [EnvironmentVariableTarget]::Process) -ne 'AUTHORIZED') {
        throw 'WO-024 native supervision fault injection is not authorized.'
    }
    if ($QualificationWaitForProcessExitBeforeSupervisionFailure -and
        -not $QualificationInjectSupervisionFailureAfterStart) {
        throw 'WO-024 root-exit fault injection requires post-start supervision injection.'
    }

    $process = $null
    $job = $null
    $gate = $null
    $identity = $null
    $descendants = [Collections.Generic.Dictionary[string, object]]::new()
    $stdoutTask = $null
    $stderrTask = $null
    $timedOut = $false
    $primaryFailure = $null
    $cleanupFailure = $null
    $cleanup = $null
    try {
        $confined = Start-J6ConfinedNativeProcess `
            -FilePath $FilePath `
            -ArgumentList $ArgumentList `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput `
            -RedirectStandardError
        $process = $confined.Process
        $identity = $confined.Identity
        $job = $confined.Job
        $gate = $confined.Gate
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if ($QualificationInjectSupervisionFailureAfterStart) {
            if (-not [string]::IsNullOrWhiteSpace($QualificationSupervisionReadyPath)) {
                $qualificationReadyDeadline = [DateTime]::UtcNow.AddSeconds(5)
                while (-not (Test-Path -LiteralPath $QualificationSupervisionReadyPath -PathType Leaf) -and
                    [DateTime]::UtcNow -lt $qualificationReadyDeadline) {
                    Start-Sleep -Milliseconds 25
                }
                if (-not (Test-Path -LiteralPath $QualificationSupervisionReadyPath -PathType Leaf)) {
                    throw 'WO-024 post-start supervision fault injection did not reach its ready marker.'
                }
            }
            if ($QualificationWaitForProcessExitBeforeSupervisionFailure) {
                $qualificationExitDeadline = [DateTime]::UtcNow.AddSeconds(5)
                while (-not $process.HasExited -and
                    [DateTime]::UtcNow -lt $qualificationExitDeadline) {
                    Start-Sleep -Milliseconds 25
                }
                if (-not $process.HasExited) {
                    throw 'WO-024 post-start supervision fault injection did not observe root exit.'
                }
            }
            throw 'WO-024 injected post-start native supervision failure.'
        }
        $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMilliseconds)
        while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
            if ($null -eq $job) {
                Update-J6OwnedDescendantIdentities `
                    -RootIdentity $identity `
                    -OwnedDescendants $descendants
            }
            Start-Sleep -Milliseconds 25
        }
        if (-not $process.HasExited) {
            $timedOut = $true
        }
    }
    catch {
        $primaryFailure = $_.Exception
    }
    finally {
        $cleanupDeadline = [DateTime]::UtcNow.AddMilliseconds($CleanupTimeoutMilliseconds)
        if ($null -ne $process) {
            try {
                $cleanup = Stop-J6ConfinedProcess `
                    -Process $process `
                    -RootIdentity $identity `
                    -OwnedDescendants $descendants `
                    -Job $job `
                    -CleanupDeadlineUtc $cleanupDeadline `
                    -AllowNaturalExitUntilDeadline:($null -eq $primaryFailure -and -not $timedOut)
                if (-not $cleanup.Exited -or -not $cleanup.DescendantsExited) {
                    throw 'The bounded native command process tree cleanup could not be confirmed.'
                }
            }
            catch {
                $cleanupFailure = $_.Exception
                try {
                    if (-not $process.HasExited) {
                        $process.Kill($true)
                        $fallbackRemaining = [int][Math]::Max(
                            0,
                            [Math]::Floor(
                                ($cleanupDeadline - [DateTime]::UtcNow).TotalMilliseconds))
                        if ($fallbackRemaining -gt 0) {
                            [void]$process.WaitForExit($fallbackRemaining)
                        }
                    }
                }
                catch {
                    # The fail-closed cleanup error below remains authoritative.
                }
            }
            foreach ($readerTask in @($stdoutTask, $stderrTask)) {
                if ($null -eq $readerTask) {
                    continue
                }
                try {
                    $readerRemaining = [int][Math]::Max(
                        1,
                        [Math]::Floor(($cleanupDeadline - [DateTime]::UtcNow).TotalMilliseconds))
                    if (-not $readerTask.Wait($readerRemaining) -and
                        $null -eq $cleanupFailure) {
                        $cleanupFailure = [InvalidOperationException]::new(
                            'A bounded native command output reader did not settle.')
                    }
                }
                catch {
                    if ($null -eq $cleanupFailure) {
                        $cleanupFailure = $_.Exception
                    }
                }
            }
        }
        if ($null -ne $job) {
            try {
                $job.Dispose()
            }
            catch {
                if ($null -eq $cleanupFailure) {
                    $cleanupFailure = $_.Exception
                }
            }
        }
        if ($null -ne $gate) {
            try {
                $gate.Dispose()
            }
            catch {
                if ($null -eq $cleanupFailure) {
                    $cleanupFailure = $_.Exception
                }
            }
        }
    }

    if ($null -ne $cleanupFailure) {
        if ($null -ne $process) {
            $process.Dispose()
        }
        throw [InvalidOperationException]::new(
            'The bounded native command cleanup could not be confirmed.',
            $cleanupFailure)
    }
    if ($null -ne $primaryFailure) {
        if ($null -ne $process) {
            $process.Dispose()
        }
        throw $primaryFailure
    }
    if ($timedOut) {
        $process.Dispose()
        throw 'The bounded native command exceeded its deadline.'
    }

    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $standardOutput = $stdoutTask.GetAwaiter().GetResult()
    $process.Dispose()
    return [pscustomobject]@{
        ExitCode = $exitCode
        StandardOutput = $standardOutput
        ProcessTreeCleanup = 'PASS'
        UnexpectedDescendantCleanup = $cleanup.DescendantKillRequested
    }
}

function Invoke-J6NativeBinaryPipeline {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('BACKUP_ENCRYPTION', 'RESTORE_DECRYPTION', 'SYNTHETIC_QUALIFICATION')]
        [string]$Phase,

        [Parameter(Mandatory = $true)][string]$ProducerFilePath,
        [Parameter(Mandatory = $true)][string[]]$ProducerArgumentList,
        [Parameter(Mandatory = $true)][string]$ConsumerFilePath,
        [Parameter(Mandatory = $true)][string[]]$ConsumerArgumentList,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,

        [ValidateRange(1, 3600)]
        [int]$TimeoutSeconds = 300,

        [ValidateRange(100, 30000)]
        [int]$CleanupTimeoutMilliseconds = 5000,

        [Threading.CancellationToken]$CancellationToken = [Threading.CancellationToken]::None
    )

    if (-not [IO.Path]::IsPathFullyQualified($ProducerFilePath) -or
        -not [IO.Path]::IsPathFullyQualified($ConsumerFilePath)) {
        throw 'Native pipeline executables must use absolute paths.'
    }
    if (-not (Test-Path -LiteralPath $ProducerFilePath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $ConsumerFilePath -PathType Leaf)) {
        throw 'A native pipeline executable does not exist.'
    }
    if (-not (Test-Path -LiteralPath $WorkingDirectory -PathType Container)) {
        throw 'The native pipeline working directory does not exist.'
    }

    $producer = $null
    $consumer = $null
    $producerJob = $null
    $consumerJob = $null
    $producerGate = $null
    $consumerGate = $null
    $producerConfinement = 'NOT_STARTED'
    $consumerConfinement = 'NOT_STARTED'
    $producerIdentity = $null
    $consumerIdentity = $null
    $producerDescendants = [Collections.Generic.Dictionary[string, object]]::new()
    $consumerDescendants = [Collections.Generic.Dictionary[string, object]]::new()
    $producerPid = $null
    $consumerPid = $null
    $producerStartedAt = $null
    $consumerStartedAt = $null
    $producerCleanup = [pscustomobject]@{
        KillRequested = $false
        DescendantKillRequested = $false
        Exited = $true
        DescendantsExited = $true
        RootAliveBeforeCleanup = $false
        ObservedDescendantPids = @()
    }
    $consumerCleanup = [pscustomobject]@{
        KillRequested = $false
        DescendantKillRequested = $false
        Exited = $true
        DescendantsExited = $true
        RootAliveBeforeCleanup = $false
        ObservedDescendantPids = @()
    }
    $copyCancellation = [Threading.CancellationTokenSource]::new()
    $copyTask = $null
    $copyReachedEof = $false
    $pipelineResult = 'NOT_STARTED'
    $timedOut = $false
    $cancelled = $false
    $failureKind = $null
    $failureMessage = $null
    $failurePosition = $null
    $startCleanupUnconfirmed = $false
    $ownedResourceDisposeUnconfirmed = $false
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()

    try {
        if ($CancellationToken.IsCancellationRequested) {
            $cancelled = $true
            $pipelineResult = 'CANCELLED'
            throw [OperationCanceledException]::new('Native pipeline cancelled before start.')
        }

        $consumerConfined = Start-J6ConfinedNativeProcess `
            -FilePath $ConsumerFilePath `
            -ArgumentList $ConsumerArgumentList `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardInput
        $consumer = $consumerConfined.Process
        $consumerIdentity = $consumerConfined.Identity
        $consumerJob = $consumerConfined.Job
        $consumerGate = $consumerConfined.Gate
        $consumerConfinement = $consumerConfined.Confinement
        $consumerPid = $consumer.Id
        $consumerStartedAt = $consumer.StartTime.ToUniversalTime().ToString('o')

        $producerConfined = Start-J6ConfinedNativeProcess `
            -FilePath $ProducerFilePath `
            -ArgumentList $ProducerArgumentList `
            -WorkingDirectory $WorkingDirectory `
            -RedirectStandardOutput
        $producer = $producerConfined.Process
        $producerIdentity = $producerConfined.Identity
        $producerJob = $producerConfined.Job
        $producerGate = $producerConfined.Gate
        $producerConfinement = $producerConfined.Confinement
        $producerPid = $producer.Id
        $producerStartedAt = $producer.StartTime.ToUniversalTime().ToString('o')

        $copyTask = $producer.StandardOutput.BaseStream.CopyToAsync(
            $consumer.StandardInput.BaseStream,
            $copyCancellation.Token)
        $pipelineResult = 'RUNNING'

        while ($pipelineResult -eq 'RUNNING') {
            if ($null -eq $producerJob) {
                Update-J6OwnedDescendantIdentities `
                    -RootIdentity $producerIdentity `
                    -OwnedDescendants $producerDescendants
            }
            if ($null -eq $consumerJob) {
                Update-J6OwnedDescendantIdentities `
                    -RootIdentity $consumerIdentity `
                    -OwnedDescendants $consumerDescendants
            }
            if ($CancellationToken.IsCancellationRequested) {
                $cancelled = $true
                $pipelineResult = 'CANCELLED'
                break
            }
            if ($stopwatch.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
                $timedOut = $true
                $pipelineResult = 'TIMEOUT'
                break
            }

            $producerExited = $producer.HasExited
            $consumerExited = $consumer.HasExited

            if ($consumerExited -and -not $producerExited) {
                if ($consumer.ExitCode -eq 0) {
                    $pipelineResult = 'CONSUMER_EARLY_EXIT'
                }
                else {
                    $pipelineResult = 'CONSUMER_FAILED'
                }
                break
            }
            if ($copyTask.IsFaulted) {
                $pipelineResult = 'COPY_FAILED'
                break
            }
            if ($copyTask.IsCanceled) {
                $pipelineResult = 'COPY_CANCELLED'
                break
            }

            if ($producerExited -and $copyTask.IsCompleted) {
                if ($copyTask.Status -eq [Threading.Tasks.TaskStatus]::RanToCompletion) {
                    $producer.WaitForExit()
                    [void]$copyTask.GetAwaiter().GetResult()
                    $copyReachedEof = $true
                }
                else {
                    $pipelineResult = 'COPY_FAILED'
                    break
                }

                try {
                    $consumer.StandardInput.BaseStream.Close()
                }
                catch [InvalidOperationException] {
                    # A consumer that already exited has already closed its input.
                }

                if ($producer.ExitCode -ne 0) {
                    $pipelineResult = 'PRODUCER_FAILED'
                    break
                }

                while (-not $consumer.HasExited) {
                    if ($null -eq $consumerJob) {
                        Update-J6OwnedDescendantIdentities `
                            -RootIdentity $consumerIdentity `
                            -OwnedDescendants $consumerDescendants
                    }
                    if ($CancellationToken.IsCancellationRequested) {
                        $cancelled = $true
                        $pipelineResult = 'CANCELLED'
                        break
                    }
                    if ($stopwatch.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
                        $timedOut = $true
                        $pipelineResult = 'TIMEOUT'
                        break
                    }
                    Start-Sleep -Milliseconds 25
                }
                if ($pipelineResult -ne 'RUNNING') {
                    break
                }
                $consumer.WaitForExit()
                if ($consumer.ExitCode -ne 0) {
                    $pipelineResult = 'CONSUMER_FAILED'
                }
                else {
                    $pipelineResult = 'SUCCESS'
                }
                break
            }

            Start-Sleep -Milliseconds 25
        }
    }
    catch [OperationCanceledException] {
        $cancelled = $true
        if ($pipelineResult -eq 'NOT_STARTED' -or $pipelineResult -eq 'RUNNING') {
            $pipelineResult = 'CANCELLED'
        }
        $failureKind = $_.Exception.GetType().Name
        $failureMessage = $_.Exception.Message
        $failurePosition = $_.InvocationInfo.PositionMessage
    }
    catch {
        if ($_.Exception -is [J6NativeConfinementCleanupException]) {
            $startCleanupUnconfirmed = $true
        }
        if ($pipelineResult -eq 'NOT_STARTED' -or $pipelineResult -eq 'RUNNING') {
            $pipelineResult = 'START_OR_SUPERVISION_FAILED'
        }
        $failureKind = $_.Exception.GetType().Name
        $failureMessage = $_.Exception.Message
        $failurePosition = $_.InvocationInfo.PositionMessage
    }
    finally {
        if ($pipelineResult -ne 'SUCCESS') {
            try {
                $copyCancellation.Cancel()
            }
            catch [ObjectDisposedException] {
                # The cancellation source is owned by this invocation and is already closing.
            }
        }

        $cleanupDeadline = [DateTime]::UtcNow.AddMilliseconds($CleanupTimeoutMilliseconds)
        try {
            $consumerCleanup = Stop-J6ConfinedProcess `
                -Process $consumer `
                -RootIdentity $consumerIdentity `
                -OwnedDescendants $consumerDescendants `
                -Job $consumerJob `
                -CleanupDeadlineUtc $cleanupDeadline `
                -AllowNaturalExitUntilDeadline:($pipelineResult -eq 'SUCCESS')
        }
        catch {
            $consumerCleanup = [pscustomobject]@{
                KillRequested = $false
                DescendantKillRequested = $false
                Exited = $false
                DescendantsExited = $false
                RootAliveBeforeCleanup = $false
                ObservedDescendantPids = @()
                Unverifiable = $true
                Confinement = $consumerConfinement
            }
        }
        try {
            $producerCleanup = Stop-J6ConfinedProcess `
                -Process $producer `
                -RootIdentity $producerIdentity `
                -OwnedDescendants $producerDescendants `
                -Job $producerJob `
                -CleanupDeadlineUtc $cleanupDeadline `
                -AllowNaturalExitUntilDeadline:($pipelineResult -eq 'SUCCESS')
        }
        catch {
            $producerCleanup = [pscustomobject]@{
                KillRequested = $false
                DescendantKillRequested = $false
                Exited = $false
                DescendantsExited = $false
                RootAliveBeforeCleanup = $false
                ObservedDescendantPids = @()
                Unverifiable = $true
                Confinement = $producerConfinement
            }
        }

        foreach ($ownedResource in @(
                $consumerJob, $producerJob,
                $consumerGate, $producerGate)) {
            if ($null -ne $ownedResource) {
                try {
                    $ownedResource.Dispose()
                }
                catch {
                    $ownedResourceDisposeUnconfirmed = $true
                }
            }
        }

        if ($null -ne $copyTask -and -not $copyTask.IsCompleted) {
            try {
                $copyRemaining = [int][Math]::Max(
                    1,
                    [Math]::Floor(($cleanupDeadline - [DateTime]::UtcNow).TotalMilliseconds))
                [void]$copyTask.Wait($copyRemaining)
            }
            catch {
                # The copy status is reported independently below.
            }
        }
        $stopwatch.Stop()
    }

    $producerExitCode = Get-J6NativeExitCode -Process $producer
    $consumerExitCode = Get-J6NativeExitCode -Process $consumer
    $copySettled = $null -eq $copyTask -or $copyTask.IsCompleted
    $localCleanupPassed = $producerCleanup.Exited -and
        $producerCleanup.DescendantsExited -and
        $consumerCleanup.Exited -and
        $consumerCleanup.DescendantsExited -and
        $copySettled -and
        -not $startCleanupUnconfirmed -and
        -not $ownedResourceDisposeUnconfirmed
    if (-not $localCleanupPassed) {
        $pipelineResult = 'CLEANUP_UNCONFIRMED'
    }
    elseif ($pipelineResult -eq 'SUCCESS' -and
        ($producerCleanup.KillRequested -or $consumerCleanup.KillRequested -or
            $producerCleanup.DescendantKillRequested -or
            $consumerCleanup.DescendantKillRequested)) {
        $pipelineResult = 'INCOMPLETE_SUCCESS_REJECTED'
    }
    if ($pipelineResult -eq 'SUCCESS' -and
        (-not $copyReachedEof -or $producerExitCode -ne 0 -or $consumerExitCode -ne 0)) {
        $pipelineResult = 'INCOMPLETE_SUCCESS_REJECTED'
    }

    $copyStatus = if ($copyReachedEof) {
        'COMPLETED_TO_EOF'
    }
    elseif ($timedOut) {
        'TIMEOUT'
    }
    elseif ($cancelled) {
        'CANCELLED'
    }
    elseif ($null -ne $copyTask -and $copyTask.IsFaulted) {
        'FAILED'
    }
    elseif ($null -ne $copyTask -and $copyTask.IsCanceled) {
        'CANCELLED'
    }
    else {
        'NOT_COMPLETED'
    }

    $result = [pscustomobject]@{
        Phase = $Phase
        PipelineResult = $pipelineResult
        ProducerStatus = Get-J6NativeProcessStatus `
            -Process $producer `
            -KillRequested $producerCleanup.KillRequested `
            -TimedOut $timedOut `
            -Cancelled $cancelled
        ProducerExitCode = $producerExitCode
        ProducerPid = $producerPid
        ProducerStartedAtUtc = $producerStartedAt
        ProducerConfinement = $producerConfinement
        ProducerRootAliveAtCleanupStart = $producerCleanup.RootAliveBeforeCleanup
        ConsumerStatus = Get-J6NativeProcessStatus `
            -Process $consumer `
            -KillRequested $consumerCleanup.KillRequested `
            -TimedOut $timedOut `
            -Cancelled $cancelled
        ConsumerExitCode = $consumerExitCode
        ConsumerPid = $consumerPid
        ConsumerStartedAtUtc = $consumerStartedAt
        ConsumerConfinement = $consumerConfinement
        ConsumerRootAliveAtCleanupStart = $consumerCleanup.RootAliveBeforeCleanup
        ProducerObservedDescendantPids = @($producerCleanup.ObservedDescendantPids)
        ConsumerObservedDescendantPids = @($consumerCleanup.ObservedDescendantPids)
        CopyStatus = $copyStatus
        LocalProcessTreeCleanup = if ($localCleanupPassed) { 'PASS' } else { 'FAIL' }
        TimedOut = $timedOut
        Cancelled = $cancelled
        DurationMilliseconds = $stopwatch.ElapsedMilliseconds
        FailureKind = $failureKind
        FailureMessage = $failureMessage
        FailurePosition = $failurePosition
    }

    if ($null -ne $producer) {
        $producer.Dispose()
    }
    if ($null -ne $consumer) {
        $consumer.Dispose()
    }
    $copyCancellation.Dispose()

    return $result
}

Export-ModuleMember -Function @(
    'Invoke-J6NativeBinaryPipeline',
    'Invoke-J6BoundedNativeCommand',
    'New-J6ConsoleCancellationRegistration'
)
