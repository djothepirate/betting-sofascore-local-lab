[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$FixturePath,
    [Parameter(Mandatory = $true)][string]$ArtifactPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2
$clock = [Diagnostics.Stopwatch]::StartNew()
$utf8 = New-Object Text.UTF8Encoding($false, $true)
$events = New-Object Collections.ArrayList

function Add-Event([string]$Name, $Data) {
    [void]$events.Add([ordered]@{
        event = $Name
        utc = [DateTime]::UtcNow.ToString('o')
        monotonic_ms = $clock.Elapsed.TotalMilliseconds
        data = $Data
    })
}

function Get-NetSha256([byte[]]$Bytes) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $algorithm.Dispose()
    }
}

function Get-NetFileSha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

$result = [ordered]@{
    case = 'WR-N01'
    phase = 'C5_DEPENDENCY_PREFLIGHT_ONLY'
    java_started = $false
    status = 'NOT_COMPLETED'
    events = $events
}

try {
    $self = [Diagnostics.Process]::GetCurrentProcess()
    $psTable = [ordered]@{}
    foreach ($key in $PSVersionTable.Keys) { $psTable[$key] = [string]$PSVersionTable[$key] }

    # This event deliberately precedes Get-Command and every SHA calculation.
    Add-Event 'runtime_registered' ([ordered]@{
        ps_version_table = $psTable
        pshome = $PSHOME
        executable = $self.MainModule.FileName
        conductor_pid = $PID
        cwd = (Get-Location).Path
        windows = [Environment]::OSVersion.VersionString
        is_64bit_os = [Environment]::Is64BitOperatingSystem
        is_64bit_process = [Environment]::Is64BitProcess
        clr = [Environment]::Version.ToString()
        output_encoding = $OutputEncoding.WebName
        console_encoding = [Console]::OutputEncoding.WebName
        artifact_encoding = 'UTF-8 no BOM'
    })

    if ($PSVersionTable.PSEdition -ne 'Desktop' -or $PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -ne 1 -or [Environment]::OSVersion.Platform -ne 'Win32NT') {
        throw 'Required Windows PowerShell 5.1 Desktop not observed'
    }

    $getFileHash = Get-Command Get-FileHash -ErrorAction SilentlyContinue
    $shaType = [type]::GetType('System.Security.Cryptography.SHA256, mscorlib', $false)
    if ($null -eq $shaType) { $shaType = [Security.Cryptography.SHA256] }
    $processStartInfo = New-Object Diagnostics.ProcessStartInfo
    Add-Event 'dependencies_checked' ([ordered]@{
        runtime_registered_before_dependency_check = $true
        get_file_hash_available = ($null -ne $getFileHash)
        get_file_hash_source = if ($null -ne $getFileHash) { [string]$getFileHash.Source } else { $null }
        dotnet_sha256_type = if ($null -ne $shaType) { [string]$shaType.FullName } else { $null }
        file_open_read_available = ($null -ne ([IO.File].GetMethod('OpenRead', [type[]]@([string]))))
        process_start_info_available = ($null -ne $processStartInfo)
        powershell_module_path_changed = $false
        module_imported = $false
        java_started = $false
    })
    if ($null -eq $shaType) { throw 'Direct .NET SHA-256 is unavailable' }
    if ($null -eq ([IO.File].GetMethod('OpenRead', [type[]]@([string])))) { throw 'File.OpenRead is unavailable' }

    $vector = Get-NetSha256 ([Text.Encoding]::ASCII.GetBytes('abc'))
    if ($vector -cne 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad') { throw 'Direct .NET SHA-256 self-test failed' }
    Add-Event 'net_sha256_self_tested' @{ vector = 'ascii:abc'; sha256 = $vector; passed = $true }

    $fixture = [IO.Path]::GetFullPath($FixturePath)
    if (-not [IO.File]::Exists($fixture)) { throw "Fixture not found: $fixture" }
    $fixtureHash = Get-NetFileSha256 $fixture
    Add-Event 'fixture_hashed_after_runtime' @{ path = $fixture; sha256 = $fixtureHash; bytes = (Get-Item -LiteralPath $fixture).Length; algorithm = '.NET SHA256' }

    $result.status = 'PASS'
    $result.fixture_sha256 = $fixtureHash
}
catch {
    Add-Event 'preflight_error' @{ stage = 'dependency_preflight'; type = $_.Exception.GetType().FullName; message = $_.Exception.Message; id = $_.FullyQualifiedErrorId; line = $_.InvocationInfo.ScriptLineNumber; stack = $_.ScriptStackTrace }
    $result.status = 'BLOCKED'
    $result.error = $_.Exception.Message
}
finally {
    $result.elapsed_ms = $clock.Elapsed.TotalMilliseconds
    $parent = Split-Path -Parent $ArtifactPath
    if (-not [IO.Directory]::Exists($parent)) { [void][IO.Directory]::CreateDirectory($parent) }
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($ArtifactPath), ($result | ConvertTo-Json -Depth 12), $utf8)
}

if ($result.status -eq 'PASS') { exit 0 }
exit 1
