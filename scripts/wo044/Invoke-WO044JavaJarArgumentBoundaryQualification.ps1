[CmdletBinding()]
param(
    [ValidateRange(1, 20)]
    [int]$Iterations = 5
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) {
    throw 'WO-044 host qualification requires Windows.'
}

$repositoryRoot = [IO.Path]::GetFullPath(
    (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)))
$modulePath = Join-Path $repositoryRoot 'scripts\wo036\WO036-CampaignTools.psm1'
$captureScriptPath = Join-Path $PSScriptRoot 'Capture-WO044NativeArguments.ps1'
$pwshPath = Join-Path $PSHOME 'pwsh.exe'
foreach ($path in @($modulePath, $captureScriptPath, $pwshPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'WO-044 host qualification dependency is unavailable.'
    }
}

$module = Import-Module $modulePath -Force -PassThru
$runId = [guid]::NewGuid().ToString()
$temporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar)
$temporaryRoot = [IO.Path]::GetFullPath((Join-Path $temporaryBase `
    "WO044 Java JAR argument boundary $runId"))
$expectedPrefix = $temporaryBase + [IO.Path]::DirectorySeparatorChar
if (-not $temporaryRoot.StartsWith($expectedPrefix,
        [StringComparison]::OrdinalIgnoreCase) `
        -or [IO.Path]::GetFileName($temporaryRoot) -cnotmatch
            '^WO044 Java JAR argument boundary [0-9a-f-]{36}$' `
        -or (Test-Path -LiteralPath $temporaryRoot)) {
    throw 'WO-044 synthetic temporary root is not exactly confined.'
}

$createdFiles = [Collections.Generic.List[string]]::new()
$ownedProcesses = [Collections.Generic.List[object]]::new()
$qualifiedIterations = 0

function ConvertTo-WO044ControlledQuotedPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path) `
            -or $Path -match '[\x00-\x1f\x7f"]' `
            -or $Path.EndsWith([IO.Path]::DirectorySeparatorChar) `
            -or $Path.EndsWith([IO.Path]::AltDirectorySeparatorChar)) {
        throw 'WO-044 controlled harness path cannot be serialized safely.'
    }
    return ('"{0}"' -f $Path)
}

function ConvertTo-WO044JavaJarArgument {
    param([Parameter(Mandatory = $true)][string]$Path)

    return [string](& $module {
        param([string]$Candidate)
        ConvertTo-WO036JavaJarStartProcessArgument -Path $Candidate
    } $Path)
}

function Stop-WO044ExactOwnedProcess {
    param([Parameter(Mandatory = $true)][object]$Owned)

    try {
        if ($Owned.Process.HasExited) {
            return
        }
        $current = Get-Process -Id $Owned.ProcessId -ErrorAction Stop
        try {
            $currentStartedAtUtc = $current.StartTime.ToUniversalTime().ToString('O')
        }
        finally {
            $current.Dispose()
        }
        $cim = Get-CimInstance Win32_Process -Filter (
            'ProcessId={0}' -f $Owned.ProcessId) -ErrorAction Stop
        $actualExecutable = [IO.Path]::GetFullPath([string]$cim.ExecutablePath)
        if ($currentStartedAtUtc -cne $Owned.StartTimeUtc `
                -or -not $actualExecutable.Equals($pwshPath,
                    [StringComparison]::OrdinalIgnoreCase) `
                -or [string]$cim.CommandLine -notlike "*$($Owned.InstanceToken)*" `
                -or [string]$cim.CommandLine -notlike "*$captureScriptPath*") {
            throw 'WO-044 refused to terminate a process without exact ownership proof.'
        }
        Stop-Process -Id $Owned.ProcessId -Force -ErrorAction Stop
        if (-not $Owned.Process.WaitForExit(5000)) {
            throw 'WO-044 exact owned process did not terminate in time.'
        }
    }
    finally {
        $Owned.Process.Dispose()
    }
}

function Invoke-WO044ArgumentCapture {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$JarArgument
    )

    if ($Label -cnotmatch '^[a-z0-9-]+$') {
        throw 'WO-044 capture label is invalid.'
    }
    $instanceToken = [guid]::NewGuid().ToString()
    $capturePath = Join-Path $temporaryRoot "$Label.arguments.json"
    $stdoutPath = Join-Path $temporaryRoot "$Label.stdout.log"
    $stderrPath = Join-Path $temporaryRoot "$Label.stderr.log"
    foreach ($path in @($capturePath, $stdoutPath, $stderrPath)) {
        if (Test-Path -LiteralPath $path) {
            throw 'WO-044 capture target already exists.'
        }
        $createdFiles.Add($path)
    }
    $process = Start-Process -FilePath $pwshPath -ArgumentList @(
        '-NoLogo',
        '-NoProfile',
        '-NonInteractive',
        '-File', (ConvertTo-WO044ControlledQuotedPath -Path $captureScriptPath),
        (ConvertTo-WO044ControlledQuotedPath -Path $capturePath),
        "-Dwo036.instance=$instanceToken",
        '-Djdk.httpclient.disableRetryConnect=true',
        '-Djdk.httpclient.redirects.retrylimit=1',
        '-Djdk.httpclient.enableAllMethodRetry=false',
        '-jar', $JarArgument) `
        -WorkingDirectory $temporaryRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    $owned = [pscustomobject]@{
        Process = $process
        ProcessId = $process.Id
        StartTimeUtc = $process.StartTime.ToUniversalTime().ToString('O')
        InstanceToken = $instanceToken
    }
    $ownedProcesses.Add($owned)
    if (-not $process.WaitForExit(10000)) {
        throw 'WO-044 argument capture child exceeded its bounded deadline.'
    }
    if ($process.ExitCode -ne 0 `
            -or -not (Test-Path -LiteralPath $capturePath -PathType Leaf)) {
        throw 'WO-044 argument capture child failed.'
    }
    try {
        $document = Get-Content -LiteralPath $capturePath -Raw -Encoding utf8 |
            ConvertFrom-Json -Depth 4
    }
    catch {
        throw 'WO-044 argument capture output is invalid.'
    }
    if ([string]$document.schemaVersion -cne '1.0' `
            -or $null -eq $document.arguments) {
        throw 'WO-044 argument capture output has an invalid shape.'
    }
    return [pscustomobject]@{
        InstanceToken = $instanceToken
        Arguments = @($document.arguments | ForEach-Object { [string]$_ })
    }
}

function Assert-WO044ExactArguments {
    param(
        [Parameter(Mandatory = $true)][string[]]$Actual,
        [Parameter(Mandatory = $true)][string[]]$Expected
    )

    if ($Actual.Count -ne $Expected.Count) {
        throw 'WO-044 native argument count is inconsistent.'
    }
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        if ($Actual[$index] -cne $Expected[$index]) {
            throw "WO-044 native argument $index is inconsistent."
        }
    }
}

try {
    [void][IO.Directory]::CreateDirectory($temporaryRoot)
    if ((((Get-Item -LiteralPath $temporaryRoot -Force).Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0) `
            -or $temporaryRoot.IndexOf(' ', [StringComparison]::Ordinal) -lt 0) {
        throw 'WO-044 synthetic temporary root is not one direct path with spaces.'
    }

    $jarPath = Join-Path $temporaryRoot 'synthetic application with spaces.jar'
    [IO.File]::WriteAllBytes($jarPath, [byte[]](0x50, 0x4b, 0x03, 0x04))
    $createdFiles.Add($jarPath)

    $baseline = Invoke-WO044ArgumentCapture -Label 'baseline-unquoted' `
        -JarArgument $jarPath
    if ($baseline.Arguments.Count -le 6 `
            -or $baseline.Arguments -contains $jarPath) {
        throw 'WO-044 pre-fix split-argument reproduction was not observed.'
    }

    for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
        $quotedJar = ConvertTo-WO044JavaJarArgument -Path $jarPath
        $capture = Invoke-WO044ArgumentCapture `
            -Label ("qualified-{0:D2}" -f $iteration) -JarArgument $quotedJar
        $expected = @(
            "-Dwo036.instance=$($capture.InstanceToken)",
            '-Djdk.httpclient.disableRetryConnect=true',
            '-Djdk.httpclient.redirects.retrylimit=1',
            '-Djdk.httpclient.enableAllMethodRetry=false',
            '-jar',
            $jarPath
        )
        Assert-WO044ExactArguments -Actual $capture.Arguments -Expected $expected
        $qualifiedIterations++
    }

    $noSpaceJarPath = "C:\WO044-$runId\synthetic.jar"
    $noSpaceCapture = Invoke-WO044ArgumentCapture -Label 'qualified-no-space' `
        -JarArgument (ConvertTo-WO044JavaJarArgument -Path $noSpaceJarPath)
    Assert-WO044ExactArguments -Actual $noSpaceCapture.Arguments -Expected @(
        "-Dwo036.instance=$($noSpaceCapture.InstanceToken)",
        '-Djdk.httpclient.disableRetryConnect=true',
        '-Djdk.httpclient.redirects.retrylimit=1',
        '-Djdk.httpclient.enableAllMethodRetry=false',
        '-jar',
        $noSpaceJarPath
    )

    $rejectedAmbiguousValues = 0
    foreach ($candidate in @(
            'relative.jar',
            "C:\WO044-$runId\not-a-jar.txt",
            "C:\WO044-$runId\quoted`"value.jar",
            "C:\WO044-$runId\control`nvalue.jar")) {
        try {
            [void](ConvertTo-WO044JavaJarArgument -Path $candidate)
        }
        catch {
            $rejectedAmbiguousValues++
        }
    }
    if ($rejectedAmbiguousValues -ne 4) {
        throw 'WO-044 did not reject every ambiguous Java JAR path.'
    }
}
finally {
    foreach ($owned in $ownedProcesses) {
        Stop-WO044ExactOwnedProcess -Owned $owned
    }
    foreach ($file in $createdFiles) {
        if (Test-Path -LiteralPath $file) {
            Remove-Item -LiteralPath $file -Force
        }
    }
    if (Test-Path -LiteralPath $temporaryRoot -PathType Container) {
        $remaining = @(Get-ChildItem -LiteralPath $temporaryRoot -Force)
        if ($remaining.Count -ne 0) {
            throw 'WO-044 synthetic temporary root contains unexpected residue.'
        }
        Remove-Item -LiteralPath $temporaryRoot -Force
    }
}

$residualProcesses = @($ownedProcesses | Where-Object {
        $null -ne (Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue)
    })
if ($residualProcesses.Count -ne 0 `
        -or (Test-Path -LiteralPath $temporaryRoot)) {
    throw 'WO-044 host qualification cleanup is incomplete.'
}

Write-Output 'WO044_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED'
Write-Output 'WO044_PRE_FIX_SPLIT_ARGUMENT_REPRODUCED=YES'
Write-Output 'WO044_WINDOWS_PATH_WITH_SPACES_RECEIVED_AS_ONE_ARGUMENT=YES'
Write-Output "WO044_QUALIFIED_ITERATIONS=$qualifiedIterations"
Write-Output 'WO044_NO_SPACE_PATH_UNCHANGED=YES'
Write-Output 'WO044_INSTANCE_ARGUMENT=PASS'
Write-Output 'WO044_JDK_RETRY_GUARDS=3_OF_3_PASS'
Write-Output 'WO044_JAR_SWITCH_AND_PATH_ORDER=PASS'
Write-Output 'WO044_AMBIGUOUS_PATH_CASES_REJECTED=4_OF_4'
Write-Output 'WO044_RESIDUAL_PROCESS_COUNT=0'
Write-Output 'WO044_RESIDUAL_TEMP_ROOT_COUNT=0'
Write-Output 'WO044_DATABASES_STARTED=NO'
Write-Output 'WO044_PROVIDER_CALLS=0'
Write-Output 'WO044_RECEIVER_HTTP_CALLS=0'
Write-Output 'WO044_REMOTE_NETWORK_OPENED=NO'
