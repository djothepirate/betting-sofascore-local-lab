[CmdletBinding()]
param(
    [ValidateSet(
        'NONE',
        'AFTER_PRIVATE_STATE',
        'AFTER_SERVER_KEYSTORE',
        'AFTER_SERVER_ROOT_IMPORT',
        'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP',
        'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY',
        'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED',
        'AFTER_RECEIVER_TRUSTSTORE')]
    [string]$QualificationFailurePoint = 'NONE'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$workOrder = 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign'
$provisioningWorkOrder =
    'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning'
$repositoryRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$modulePath = Join-Path $PSScriptRoot 'wo048\WO048-PkiTools.psm1'
$nativeRunnerModulePath = Join-Path $PSScriptRoot 'J6-NativeBinaryPipeline.psm1'
$nativeProcessHostPath = Join-Path $PSScriptRoot 'J6-NativeProcessHost.ps1'
$cleanupPath = Join-Path $PSScriptRoot 'Remove-WO046LocalMtlsIdentity.ps1'
$javaStoreProbePath = Join-Path $PSScriptRoot 'wo048\WO048WindowsStoreProbe.java'
$boundedJavaCapturePath = Join-Path $PSScriptRoot `
    'wo048\Invoke-WO048BoundedJavaCapture.ps1'
if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $nativeRunnerModulePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $nativeProcessHostPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $cleanupPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $javaStoreProbePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $boundedJavaCapturePath -PathType Leaf)) {
    throw 'The exact versioned WO-048 PKI-only tooling is incomplete.'
}

function Import-WO048PinnedGlobalModule {
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedPath,
        [Parameter(Mandatory = $true)][string]$ExpectedName,
        [Parameter(Mandatory = $true)][string[]]$RequiredCommands
    )

    $canonicalExpectedPath = [System.IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $ExpectedPath -ErrorAction Stop).Path)
    $existing = @(Get-Module -Name $ExpectedName -All)
    if ($existing.Count -gt 1 -or @($existing | Where-Object {
            [string]::IsNullOrWhiteSpace($_.Path) -or
            [System.IO.Path]::GetFullPath($_.Path) -cne $canonicalExpectedPath
        }).Count -ne 0) {
        throw 'WO-048 refuses a loaded module with an ambiguous or unexpected origin.'
    }
    $ownedByThisInvocation = $existing.Count -eq 0
    Import-Module -Name $canonicalExpectedPath -Global -ErrorAction Stop
    $loaded = @(Get-Module -Name $ExpectedName -All)
    if ($loaded.Count -ne 1 -or
        [System.IO.Path]::GetFullPath($loaded[0].Path) -cne
            $canonicalExpectedPath) {
        throw 'WO-048 could not establish one exact globally visible module.'
    }
    foreach ($requiredCommand in $RequiredCommands) {
        $resolved = @(Get-Command -Name ($ExpectedName + '\' + $requiredCommand) `
            -All -ErrorAction Stop)
        if ($resolved.Count -ne 1 -or $null -eq $resolved[0].Module -or
            [System.IO.Path]::GetFullPath($resolved[0].Module.Path) -cne
                $canonicalExpectedPath) {
            throw 'WO-048 could not resolve an exact pinned module command.'
        }
    }
    return $ownedByThisInvocation
}

$script:wo048PkiModuleOwnedByThisInvocation = $false
$script:wo048NativeModuleOwnedByThisInvocation = $false
try {
    $script:wo048PkiModuleOwnedByThisInvocation =
        Import-WO048PinnedGlobalModule `
            -ExpectedPath $modulePath `
            -ExpectedName 'WO048-PkiTools' `
            -RequiredCommands @(
                'Assert-WO048LocalFixedPathPrefix',
                'Get-WO048CurrentOwnerSid')
    $script:wo048NativeModuleOwnedByThisInvocation =
        Import-WO048PinnedGlobalModule `
            -ExpectedPath $nativeRunnerModulePath `
            -ExpectedName 'J6-NativeBinaryPipeline' `
            -RequiredCommands @('Invoke-J6BoundedNativeCommand')
}
catch {
    if ($script:wo048NativeModuleOwnedByThisInvocation) {
        Remove-Module -Name 'J6-NativeBinaryPipeline' -Force `
            -ErrorAction SilentlyContinue
    }
    if ($script:wo048PkiModuleOwnedByThisInvocation) {
        Remove-Module -Name 'WO048-PkiTools' -Force `
            -ErrorAction SilentlyContinue
    }
    throw 'WO-048 could not establish its exact pinned module set.'
}

$script:phase = 'PREFLIGHT'
$script:runRoot = $null
$script:runRootCreatedByThisInvocation = $false
$script:runRootPrivacyApplied = $false
$script:statePath = $null
$script:state = $null
$script:toolsLock = $null
$script:clientCertificate = $null
$script:clientCertificateRecoveryDeclared = $false
$script:clientCertificateOwnershipPersisted = $false
$script:unpersistedClientRollbackFailed = $false
$script:keytool = $null
$script:keytoolSha256 = $null
$script:javaHome = $null
$script:java = $null
$script:javaSha256 = $null
$script:javac = $null
$script:javacSha256 = $null
$script:javaStoreProbeSha256 = $null
$script:boundedJavaCaptureSha256 = $null
$script:nativeWorkingDirectory = $null
$script:nativeRunnerModuleSha256 = $null
$script:nativeProcessHostSha256 = $null
$script:pwshPath = $null
$script:pwshSha256 = $null
$script:ownerSid = $null

function Exit-WO048ToolsLock {
    if ($null -ne $script:toolsLock) {
        $script:toolsLock.Dispose()
        $script:toolsLock = $null
    }
}

function Remove-WO048OwnedPreStateRunRoot {
    if (-not $script:runRootCreatedByThisInvocation -or
        [string]::IsNullOrWhiteSpace($script:runRoot) -or
        -not (Test-Path -LiteralPath $script:runRoot -PathType Container)) {
        throw 'WO-048 refuses pre-state cleanup without invocation-local root ownership.'
    }
    Assert-WO048ImmediateGuidChild `
        -Parent $campaignBase -Child $script:runRoot -RunId $runId
    Assert-WO048NotReparsePoint -Path $script:runRoot
    $rootItems = @(Get-ChildItem -LiteralPath $script:runRoot -Force -ErrorAction Stop)
    if (-not $script:runRootPrivacyApplied) {
        if ($rootItems.Count -ne 0) {
            throw 'WO-048 refuses cleanup of a non-private pre-state root containing data.'
        }
        Remove-Item -LiteralPath $script:runRoot -Force -ErrorAction Stop
        return
    }

    Assert-WO048PrivateAcl `
        -Path $script:runRoot -OwnerSid $ownerSid.Value -RequireProtected
    $allowedRootFiles = @(
        '.wo048-pki.lock',
        '.wo048-owner.json',
        'identity.private.json.partial')
    $pkiRoot = Join-Path $script:runRoot 'pki'
    foreach ($item in $rootItems) {
        Assert-WO048NotReparsePoint -Path $item.FullName
        Assert-WO048PrivateAcl `
            -Path $item.FullName -OwnerSid $ownerSid.Value -RequireProtected
        if ($item.PSIsContainer) {
            if ($item.Name -cne 'pki') {
                throw 'WO-048 refuses an unknown pre-state directory.'
            }
        }
        elseif ($item.Name -cnotin $allowedRootFiles) {
            throw 'WO-048 refuses an unknown pre-state file.'
        }
    }
    if (Test-Path -LiteralPath $pkiRoot -PathType Container) {
        Assert-WO048ExactPath -Actual (Get-Item -LiteralPath $pkiRoot -Force).FullName `
            -Expected $pkiRoot -Failure 'The WO-048 pre-state PKI root changed identity.'
        $allowedPkiFiles = @(
            'native-java.stdout.private.txt',
            'native-java.stderr.private.txt')
        $pkiItems = @(Get-ChildItem -LiteralPath $pkiRoot -Force -ErrorAction Stop)
        foreach ($item in $pkiItems) {
            if ($item.PSIsContainer -or $item.Name -cnotin $allowedPkiFiles) {
                throw 'WO-048 refuses unknown pre-state PKI content.'
            }
            Assert-WO048NotReparsePoint -Path $item.FullName
            Assert-WO048PrivateAcl `
                -Path $item.FullName -OwnerSid $ownerSid.Value -RequireProtected
        }
    }

    foreach ($fileName in $allowedRootFiles) {
        $filePath = Join-Path $script:runRoot $fileName
        if (Test-Path -LiteralPath $filePath -PathType Leaf) {
            Remove-Item -LiteralPath $filePath -Force -ErrorAction Stop
        }
    }
    if (Test-Path -LiteralPath $pkiRoot -PathType Container) {
        foreach ($fileName in @(
                'native-java.stdout.private.txt',
                'native-java.stderr.private.txt')) {
            $filePath = Join-Path $pkiRoot $fileName
            if (Test-Path -LiteralPath $filePath -PathType Leaf) {
                Remove-Item -LiteralPath $filePath -Force -ErrorAction Stop
            }
        }
        if (@(Get-ChildItem -LiteralPath $pkiRoot -Force -ErrorAction Stop).Count -ne 0) {
            throw 'WO-048 refuses to remove a non-empty pre-state PKI root.'
        }
        Remove-Item -LiteralPath $pkiRoot -Force -ErrorAction Stop
    }
    if (@(Get-ChildItem -LiteralPath $script:runRoot -Force -ErrorAction Stop).Count -ne 0) {
        throw 'WO-048 refuses to remove a non-empty pre-state run root.'
    }
    Remove-Item -LiteralPath $script:runRoot -Force -ErrorAction Stop
}

function Write-WO048State {
    if ($null -eq $script:state -or [string]::IsNullOrWhiteSpace($script:statePath)) {
        throw 'The WO-048 private identity state has not been initialized.'
    }
    Write-WO048PrivateJsonAtomic `
        -Path $script:statePath `
        -Value $script:state `
        -OwnerSid ([System.Security.Principal.SecurityIdentifier]::new(
            [string]$script:state.ownerSid))
}

function Invoke-WO048QualificationFailurePoint {
    param([Parameter(Mandatory = $true)][string]$Point)

    if ($QualificationFailurePoint -eq $Point) {
        throw "WO-048 injected PKI-only qualification failure at $Point."
    }
}

function Invoke-WO048WithSanitizedJavaEnvironment {
    param([Parameter(Mandatory = $true)][scriptblock]$Action)

    $neutralNames = @(
        'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JDK_JAVAC_OPTIONS',
        'JAVA_OPTS',
        'CLASSPATH', 'MAVEN_OPTS', 'GRADLE_OPTS',
        'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'NO_PROXY', 'FTP_PROXY',
        'SOCKS_PROXY', 'GIT_HTTP_PROXY', 'GIT_HTTPS_PROXY',
        'SSLKEYLOGFILE', 'NSS_SSLKEYLOGFILE', 'JDK_TLS_KEYLOGGER', 'JAVAX_NET_DEBUG',
        'JDK_HTTPCLIENT_HTTPCLIENT_LOG')
    $saved = [ordered]@{}
    $saved['JAVA_HOME'] = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    $saved['WO048_EXPECTED_JAVA_HOME'] = [Environment]::GetEnvironmentVariable(
        'WO048_EXPECTED_JAVA_HOME', 'Process')
    foreach ($name in $neutralNames) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    try {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $script:javaHome, 'Process')
        [Environment]::SetEnvironmentVariable(
            'WO048_EXPECTED_JAVA_HOME', $script:javaHome, 'Process')
        foreach ($name in $neutralNames) {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        if ([Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process') -cne
                $script:javaHome -or
            [Environment]::GetEnvironmentVariable('WO048_EXPECTED_JAVA_HOME', 'Process') -cne
                $script:javaHome -or
            @($neutralNames | Where-Object {
                -not [string]::IsNullOrEmpty(
                    [Environment]::GetEnvironmentVariable($_, 'Process'))
            }).Count -ne 0) {
            throw 'WO-048 could not establish the exact sanitized Java child environment.'
        }
        return & $Action
    }
    finally {
        foreach ($entry in $saved.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
    }
}

function Invoke-WO048BoundedJavaTool {
    param(
        [Parameter(Mandatory = $true)][string]$ToolPath,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Assert-WO048NotReparsePoint -Path $ToolPath
    if ((Get-WO048FileSha256 -Path $ToolPath) -ne $ExpectedSha256) {
        throw 'An exact WO-048 Java executable changed after preflight.'
    }
    if ((Get-WO048FileSha256 -Path $nativeRunnerModulePath) -ne
            $script:nativeRunnerModuleSha256 -or
        (Get-WO048FileSha256 -Path $nativeProcessHostPath) -ne
            $script:nativeProcessHostSha256 -or
        (Get-WO048FileSha256 -Path $script:pwshPath) -ne $script:pwshSha256) {
        throw 'The exact WO-048 bounded native supervision toolchain changed after preflight.'
    }
    if ([string]::IsNullOrWhiteSpace($script:nativeWorkingDirectory) -or
        -not (Test-Path -LiteralPath $script:nativeWorkingDirectory -PathType Container)) {
        throw 'The WO-048 native working directory is unavailable.'
    }
    if ((Get-WO048FileSha256 -Path $boundedJavaCapturePath) -ne
            $script:boundedJavaCaptureSha256) {
        throw 'The exact WO-048 bounded Java capture host changed after preflight.'
    }
    $stdoutPath = Join-Path $script:nativeWorkingDirectory `
        'native-java.stdout.private.txt'
    $stderrPath = Join-Path $script:nativeWorkingDirectory `
        'native-java.stderr.private.txt'
    if ((Test-Path -LiteralPath $stdoutPath) -or (Test-Path -LiteralPath $stderrPath)) {
        throw 'A residual WO-048 bounded Java capture blocks execution.'
    }
    $argumentBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes(
        (ConvertTo-Json -InputObject ([string[]]$Arguments) -Compress))
    try {
        if ($argumentBytes.Length -gt 32768) {
            throw 'The WO-048 Java argument payload exceeds its fixed bound.'
        }
        $argumentPayloadBase64 = [Convert]::ToBase64String($argumentBytes)
    }
    finally {
        [Array]::Clear($argumentBytes, 0, $argumentBytes.Length)
    }
    try {
        $supervisorResult = Invoke-WO048WithSanitizedJavaEnvironment -Action {
            Invoke-J6BoundedNativeCommand `
                -FilePath $script:pwshPath `
                -ArgumentList @(
                    '-NoLogo', '-NoProfile', '-NonInteractive',
                    '-File', $boundedJavaCapturePath,
                    '-ToolPath', $ToolPath,
                    '-ExpectedToolSha256', $ExpectedSha256,
                    '-ExpectedJavaHome', $script:javaHome,
                    '-WorkingDirectory', $script:nativeWorkingDirectory,
                    '-ArgumentPayloadBase64', $argumentPayloadBase64,
                    '-StandardOutputPath', $stdoutPath,
                    '-StandardErrorPath', $stderrPath,
                    '-MaximumCharactersPerStream', '16384',
                    '-ChildTimeoutMilliseconds', '28000',
                    '-ChildCleanupTimeoutMilliseconds', '8000') `
                -WorkingDirectory $script:nativeWorkingDirectory `
                -TimeoutMilliseconds 30000 `
                -StartupTimeoutMilliseconds 10000 `
                -CleanupTimeoutMilliseconds 10000
        }
        if ($supervisorResult.ExitCode -ne 0 -or
            $supervisorResult.ProcessTreeCleanup -cne 'PASS' -or
            $supervisorResult.UnexpectedDescendantCleanup -isnot [bool] -or
            $supervisorResult.UnexpectedDescendantCleanup -or
            $supervisorResult.Confinement -cne 'WINDOWS_KILL_ON_JOB_CLOSE' -or
            $null -eq $supervisorResult.ActiveProcessesAfterCleanup -or
            [int]$supervisorResult.ActiveProcessesAfterCleanup -ne 0 -or
            $null -eq $supervisorResult.TargetProcessId -or
            $null -eq $supervisorResult.TargetStartedAtUtcTicks) {
            throw 'The bounded WO-048 Java capture supervisor failed closed.'
        }
        $captureLines = @($supervisorResult.StandardOutput -split '\r?\n' |
            Where-Object { $_ -ne '' })
        if ($captureLines.Count -ne 7 -or
            $captureLines[0] -cne 'WO048_BOUNDED_JAVA_CAPTURE=PASS' -or
            $captureLines[1] -cnotmatch '^WO048_CHILD_EXIT_CODE=-?[0-9]+$' -or
            $captureLines[2] -cnotmatch '^WO048_CHILD_PROCESS_ID=[1-9][0-9]*$' -or
            $captureLines[3] -cnotmatch '^WO048_CHILD_STARTED_AT_UTC_TICKS=[1-9][0-9]*$' -or
            $captureLines[4] -cnotmatch '^WO048_CHILD_STDOUT_BYTES=[0-9]{1,5}$' -or
            $captureLines[5] -cnotmatch '^WO048_CHILD_STDERR_BYTES=[0-9]{1,5}$' -or
            $captureLines[6] -cne 'WO048_CHILD_CLEANUP=PASS') {
            throw 'The bounded WO-048 Java capture evidence is not exact.'
        }
        $childExitCode = [int]$captureLines[1].Substring(
            'WO048_CHILD_EXIT_CODE='.Length)
        $childProcessId = [int]$captureLines[2].Substring(
            'WO048_CHILD_PROCESS_ID='.Length)
        $childStartedAtUtcTicks = [long]$captureLines[3].Substring(
            'WO048_CHILD_STARTED_AT_UTC_TICKS='.Length)
        $expectedStdoutLength = [long]$captureLines[4].Substring(
            'WO048_CHILD_STDOUT_BYTES='.Length)
        $expectedStderrLength = [long]$captureLines[5].Substring(
            'WO048_CHILD_STDERR_BYTES='.Length)
        foreach ($capturePath in @($stdoutPath, $stderrPath)) {
            if (-not (Test-Path -LiteralPath $capturePath -PathType Leaf)) {
                throw 'A bounded WO-048 Java private capture is missing.'
            }
            Assert-WO048NotReparsePoint -Path $capturePath
            Protect-WO048PrivateFile -Path $capturePath `
                -OwnerSid $script:ownerSid
        }
        $stdoutBytes = [IO.File]::ReadAllBytes($stdoutPath)
        $stderrBytes = [IO.File]::ReadAllBytes($stderrPath)
        try {
            if ($stdoutBytes.Length -ne $expectedStdoutLength -or
                $stderrBytes.Length -ne $expectedStderrLength -or
                $stdoutBytes.Length -gt 65536 -or $stderrBytes.Length -gt 65536) {
                throw 'A WO-048 Java capture violated its exact encoded byte bound.'
            }
            $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
            $standardOutput = $strictUtf8.GetString($stdoutBytes)
            $standardError = $strictUtf8.GetString($stderrBytes)
        }
        finally {
            [Array]::Clear($stdoutBytes, 0, $stdoutBytes.Length)
            [Array]::Clear($stderrBytes, 0, $stderrBytes.Length)
        }
        if ($childExitCode -ne 0) {
            throw 'An offline WO-048 bounded Java-tool operation returned nonzero.'
        }
        return [pscustomobject]@{
            ExitCode = $childExitCode
            StandardOutput = $standardOutput
            StandardError = $standardError
            ProcessTreeCleanup = 'PASS'
            UnexpectedDescendantCleanup = $false
            Confinement = 'WINDOWS_KILL_ON_JOB_CLOSE'
            ActiveProcessesAfterCleanup = 0
            TargetProcessId = $childProcessId
            TargetStartedAtUtcTicks = $childStartedAtUtcTicks
        }
    }
    finally {
        $argumentPayloadBase64 = $null
        foreach ($capturePath in @($stdoutPath, $stderrPath)) {
            if (Test-Path -LiteralPath $capturePath -PathType Leaf) {
                Assert-WO048NotReparsePoint -Path $capturePath
                Remove-Item -LiteralPath $capturePath -Force
            }
        }
    }
}

function Invoke-WO048Keytool {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    Invoke-WO048BoundedJavaTool `
        -ToolPath $script:keytool `
        -ExpectedSha256 $script:keytoolSha256 `
        -Arguments $Arguments | Out-Null
}

function Assert-WO048Java25RuntimeProvenance {
    $result = Invoke-WO048BoundedJavaTool `
        -ToolPath $script:java `
        -ExpectedSha256 $script:javaSha256 `
        -Arguments @(
            '-Djava.net.useSystemProxies=false',
            $javaStoreProbePath,
            '--runtime-only')
    $lines = @($result.StandardOutput -split '\r?\n' | Where-Object { $_ -ne '' })
    $expected = @(
        'WO048_JAVA_RUNTIME_PROVENANCE=PASS_JAVA_25',
        'WO048_JAVA_ENVIRONMENT_SANITIZED=YES',
        'WO048_JAVA_TLS_HANDSHAKES=0',
        'WO048_JAVA_SOCKETS_OPENED=0')
    if (@(Compare-Object -ReferenceObject $expected -DifferenceObject $lines).Count -ne 0) {
        throw 'The exact WO-048 Java 25 runtime provenance probe failed closed.'
    }
    $result = $null

    $javacResult = Invoke-WO048BoundedJavaTool `
        -ToolPath $script:javac `
        -ExpectedSha256 $script:javacSha256 `
        -Arguments @('--version')
    $javacLines = @($javacResult.StandardOutput -split '\r?\n' | Where-Object { $_ -ne '' })
    if ($javacLines.Count -ne 1 -or $javacLines[0] -notmatch '^javac 25(?:\.|\+|$)') {
        throw 'The exact WO-048 javac executable is not Java 25.'
    }
    $javacResult = $null
}

function Assert-WO048JavaWindowsStoreQualification {
    param(
        [Parameter(Mandatory = $true)][string]$ProbeClassRoot,
        [Parameter(Mandatory = $true)][string]$ProbeInputPath,
        [Parameter(Mandatory = $true)][string]$ClientSha256,
        [Parameter(Mandatory = $true)][string]$ServerSha256,
        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$OwnerSid
    )

    if ((Get-WO048FileSha256 -Path $javaStoreProbePath) -cne
            $script:javaStoreProbeSha256) {
        throw 'The exact WO-048 Java Windows-store probe changed after preflight.'
    }
    if (-not (Test-Path -LiteralPath $ProbeClassRoot)) {
        New-Item -ItemType Directory -Path $ProbeClassRoot -ErrorAction Stop | Out-Null
        Protect-WO048PrivateDirectory -Path $ProbeClassRoot -OwnerSid $OwnerSid
    }
    if (@(Get-ChildItem -LiteralPath $ProbeClassRoot -Force).Count -ne 0) {
        throw 'The WO-048 private Java probe class directory is not empty.'
    }
    Write-WO048Utf8NoBomFile `
        -Path $ProbeInputPath `
        -Content ("clientSha256=$ClientSha256`nserverSha256=$ServerSha256`n")
    Protect-WO048PrivateFile -Path $ProbeInputPath -OwnerSid $OwnerSid

    Invoke-WO048BoundedJavaTool `
        -ToolPath $script:javac `
        -ExpectedSha256 $script:javacSha256 `
        -Arguments @('-encoding', 'UTF-8', '-d', $ProbeClassRoot, $javaStoreProbePath) |
        Out-Null
    $classPath = Join-Path $ProbeClassRoot 'WO048WindowsStoreProbe.class'
    $classChildren = @(Get-ChildItem -LiteralPath $ProbeClassRoot -Force)
    if ($classChildren.Count -ne 1 -or
        -not (Test-Path -LiteralPath $classPath -PathType Leaf)) {
        throw 'The bounded WO-048 Java probe compilation produced an unexpected artifact set.'
    }
    Protect-WO048PrivateFile -Path $classPath -OwnerSid $OwnerSid

    $result = Invoke-WO048BoundedJavaTool `
        -ToolPath $script:java `
        -ExpectedSha256 $script:javaSha256 `
        -Arguments @(
            '-Djava.net.useSystemProxies=false',
            '-cp', $ProbeClassRoot,
            'WO048WindowsStoreProbe',
            '--stores', $ProbeInputPath)
    $lines = @($result.StandardOutput -split '\r?\n' | Where-Object { $_ -ne '' })
    $expected = @(
        'WO048_JAVA_STORE_PROBE=PASS',
        'WO048_JAVA_PROVIDER=SunMSCAPI',
        'WO048_JAVA_WINDOWS_MY_EXACT_MATCH_COUNT=1',
        'WO048_JAVA_WINDOWS_ROOT_EXACT_MATCH_COUNT=1',
        'WO048_JAVA_CLIENT_PRIVATE_KEY_ENCODING=NULL',
        'WO048_JAVA_TLS_HANDSHAKES=0',
        'WO048_JAVA_SOCKETS_OPENED=0')
    if (@(Compare-Object -ReferenceObject $expected -DifferenceObject $lines).Count -ne 0) {
        throw 'The bounded WO-048 Java Windows-store qualification failed closed.'
    }
    $result = $null
}

function Get-WO048ExactStoredCertificate {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('My', 'Root')][string]$StoreName,
        [Parameter(Mandatory = $true)][string]$Thumbprint,
        [Parameter(Mandatory = $true)][string]$Sha256,
        [Parameter(Mandatory = $true)][string]$Subject
    )

    if ($Thumbprint -notmatch '^[0-9A-F]{40}$' -or
        $Sha256 -notmatch '^[0-9a-f]{64}$' -or
        [string]::IsNullOrWhiteSpace($Subject)) {
        throw 'A WO-048 certificate identity is incomplete.'
    }
    $certificate = Get-Item -LiteralPath (Join-Path "Cert:\CurrentUser\$StoreName" $Thumbprint) `
        -ErrorAction SilentlyContinue
    if ($null -eq $certificate -or
        (Get-WO048CertificateSha256 -Certificate $certificate) -ne $Sha256 -or
        $certificate.Subject -cne $Subject) {
        throw 'A WO-048 certificate failed exact store identity verification.'
    }
    return $certificate
}

function Remove-WO048ExactReturnedClientCertificate {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate
    )

    $cngIdentity = Get-WO048ClientCngKeyIdentity -Certificate $Certificate
    $thumbprint = $Certificate.Thumbprint.ToUpperInvariant()
    $sha256 = Get-WO048CertificateSha256 -Certificate $Certificate
    $subject = $Certificate.Subject
    $stored = Get-WO048ExactStoredCertificate `
        -StoreName My `
        -Thumbprint $thumbprint `
        -Sha256 $sha256 `
        -Subject $subject
    $stored.Dispose()
    $Certificate.Dispose()
    Remove-Item -LiteralPath (Join-Path 'Cert:\CurrentUser\My' $thumbprint) `
        -DeleteKey -Force
    for ($attempt = 1; $attempt -le 40; $attempt++) {
        $provider = [System.Security.Cryptography.CngProvider]::new(
            [string]$cngIdentity.providerName)
        $keyExists = [System.Security.Cryptography.CngKey]::Exists(
            [string]$cngIdentity.keyName,
            $provider)
        if (-not (Test-Path -LiteralPath (Join-Path 'Cert:\CurrentUser\My' $thumbprint)) -and
            -not $keyExists -and
            -not (Test-Path -LiteralPath ([string]$cngIdentity.keyFilePath))) {
            return
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'The exact WO-048 returned client certificate or CNG container remains after cleanup.'
}

function Set-WO048ClientCertificateRecoveryDeclaration {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$Certificate,
        [Parameter(Mandatory = $true)][string]$ExpectedSubject
    )

    # This recovery declaration deliberately does not inspect the private key.
    # It is persisted immediately after New-SelfSignedCertificate returns so a
    # later CNG inspection failure cannot make the certificate undiscoverable.
    $clientRecord = Set-WO048ClientCertificateDeclaration `
        -State $script:state `
        -Certificate $Certificate `
        -ExpectedSubject $ExpectedSubject
    Write-WO048State
    $script:clientCertificateRecoveryDeclared = $true
    return $clientRecord
}

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'WO-048 PKI-only provisioning is supported only on Windows.'
}
foreach ($commandName in @(
        'New-SelfSignedCertificate',
        'Import-Certificate',
        'Export-Certificate')) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw 'A required Windows certificate command is unavailable.'
    }
}

$ownerSid = Get-WO048CurrentOwnerSid
$script:ownerSid = $ownerSid
$campaignBase = Resolve-WO048CampaignBase -Create
$roamingApplicationData = [Environment]::GetFolderPath(
    [Environment+SpecialFolder]::ApplicationData)
if ([string]::IsNullOrWhiteSpace($roamingApplicationData)) {
    throw 'WO-048 cannot resolve the current-user cryptographic roots.'
}
$canonicalRoamingRoot = Assert-WO048LocalFixedPathPrefix `
    -Path $roamingApplicationData -RequireExistingType Directory
[void](Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $canonicalRoamingRoot 'Microsoft\Crypto\Keys'))
[void](Assert-WO048LocalFixedPathPrefix `
        -Path (Join-Path $canonicalRoamingRoot ('Microsoft\Crypto\RSA\' + $ownerSid.Value)))
$runId = [guid]::NewGuid()
$moduleSha256 = Get-WO048FileSha256 -Path $modulePath
$script:nativeRunnerModuleSha256 = Get-WO048FileSha256 -Path $nativeRunnerModulePath
$script:nativeProcessHostSha256 = Get-WO048FileSha256 -Path $nativeProcessHostPath
$script:pwshPath = [System.IO.Path]::GetFullPath((Join-Path $PSHOME 'pwsh.exe'))
if (-not (Test-Path -LiteralPath $script:pwshPath -PathType Leaf)) {
    throw 'The exact PowerShell native-process host executable is unavailable.'
}
Assert-WO048NotReparsePoint -Path $script:pwshPath
$script:pwshSha256 = Get-WO048FileSha256 -Path $script:pwshPath
$javaToolchain = Resolve-WO048ExactJava25Toolchain
$script:javaHome = $javaToolchain.JavaHome
$script:java = $javaToolchain.Java
$script:javac = $javaToolchain.Javac
$script:keytool = $javaToolchain.Keytool
$script:javaSha256 = Get-WO048FileSha256 -Path $script:java
$script:javacSha256 = Get-WO048FileSha256 -Path $script:javac
$script:keytoolSha256 = Get-WO048FileSha256 -Path $script:keytool
$script:javaStoreProbeSha256 = Get-WO048FileSha256 -Path $javaStoreProbePath
$script:boundedJavaCaptureSha256 = Get-WO048FileSha256 -Path $boundedJavaCapturePath

try {
    $script:phase = 'PRIVATE_ROOT'
    $script:runRoot = New-WO048ExclusiveRunRoot `
        -CampaignBase $campaignBase -RunId $runId
    $script:runRootCreatedByThisInvocation = $true
    Assert-WO048ImmediateGuidChild `
        -Parent $campaignBase `
        -Child $script:runRoot `
        -RunId $runId
    Assert-WO048NotReparsePoint -Path $script:runRoot
    Protect-WO048PrivateDirectory -Path $script:runRoot -OwnerSid $ownerSid
    $script:runRootPrivacyApplied = $true

    $script:phase = 'PRIVATE_LAYOUT'
    $pkiRoot = Join-Path $script:runRoot 'pki'
    New-Item -ItemType Directory -Path $pkiRoot -ErrorAction Stop | Out-Null
    Assert-WO048NotReparsePoint -Path $pkiRoot
    Protect-WO048PrivateDirectory -Path $pkiRoot -OwnerSid $ownerSid
    $script:nativeWorkingDirectory = $pkiRoot
    $script:statePath = Join-Path $script:runRoot 'identity.private.json'
    $markerPath = Join-Path $script:runRoot '.wo048-owner.json'
    $toolsLockPath = Join-Path $script:runRoot '.wo048-pki.lock'
    $serverKeyStorePath = Join-Path $pkiRoot 'receiver-server.private.p12'
    $serverPublicPath = Join-Path $pkiRoot 'receiver-server.public.cer'
    $clientPublicPath = Join-Path $pkiRoot 'sender-client.public.cer'
    $receiverTrustStorePath = Join-Path $pkiRoot 'receiver-client-trust.private.p12'
    $javaProbeInputPath = Join-Path $pkiRoot 'java-store-probe-input.private.properties'
    $javaProbeClassRoot = Join-Path $pkiRoot 'java-store-probe-classes'

    Assert-WO048Java25RuntimeProvenance

    $script:phase = 'PRIVATE_LOCK'
    $script:toolsLock = [System.IO.FileStream]::new(
        $toolsLockPath,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None)
    $script:toolsLock.Flush($true)
    Protect-WO048PrivateFile -Path $toolsLockPath -OwnerSid $ownerSid

    $serverPassword = New-WO048RandomSecret
    $receiverTrustStorePassword = New-WO048RandomSecret
    $createdAt = [DateTimeOffset]::UtcNow
    $createdAtText = $createdAt.ToString('O')
    $expectedServerSubject =
        'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
    $expectedClientSubject =
        "CN=WO046 sender $($runId.ToString('D')), OU=WO-046, O=Betting Project Local Qualification"
    $stateIdentityBindingSha256 = Get-WO048StateIdentityBinding `
        -WorkOrder $workOrder `
        -ProvisioningWorkOrder $provisioningWorkOrder `
        -RunId $runId.ToString('D') `
        -OwnerSid $ownerSid.Value `
        -CreatedAtUtc $createdAtText `
        -RunRoot $script:runRoot `
        -MarkerPath $markerPath
    $lockAuthority = New-WO048FinalizationLockAuthority `
        -WorkOrder $workOrder `
        -ProvisioningWorkOrder $provisioningWorkOrder `
        -RunId $runId.ToString('D') `
        -OwnerSid $ownerSid.Value `
        -CreatedAtUtc $createdAtText `
        -RunRoot $script:runRoot `
        -MarkerPath $markerPath `
        -LockPath $toolsLockPath `
        -StateIdentityBindingSha256 $stateIdentityBindingSha256
    Assert-WO048FinalizationLockAuthority `
        -Authority $lockAuthority `
        -ExpectedWorkOrder $workOrder `
        -ExpectedProvisioningWorkOrder $provisioningWorkOrder `
        -ExpectedRunId $runId.ToString('D') `
        -ExpectedOwnerSid $ownerSid.Value `
        -ExpectedRunRoot $script:runRoot `
        -ExpectedMarkerPath $markerPath `
        -ExpectedLockPath $toolsLockPath
    Write-WO048FinalizationLockAuthorityToStream `
        -Stream $script:toolsLock `
        -Authority $lockAuthority

    $script:phase = 'PRIVATE_STATE'
    $script:state = [pscustomobject]@{
        schemaVersion = 2
        format = 'WO046_LOCAL_MTLS_IDENTITY_V2'
        workOrder = $workOrder
        provisioningWorkOrder = $provisioningWorkOrder
        runId = $runId.ToString('D')
        ownerSid = $ownerSid.Value
        stateIdentityBindingSha256 = $stateIdentityBindingSha256
        repositoryRoot = $repositoryRoot
        modulePath = $modulePath
        moduleSha256 = $moduleSha256
        nativeRunnerModulePath = $nativeRunnerModulePath
        nativeRunnerModuleSha256 = $script:nativeRunnerModuleSha256
        nativeProcessHostPath = $nativeProcessHostPath
        nativeProcessHostSha256 = $script:nativeProcessHostSha256
        nativePowerShellPath = $script:pwshPath
        nativePowerShellSha256 = $script:pwshSha256
        runRoot = $script:runRoot
        markerPath = $markerPath
        lockPath = $toolsLockPath
        createdAtUtc = $createdAtText
        maximumCertificateValidityDays = 7
        provisioningStatus = 'IN_PROGRESS'
        cleanupStatus = 'NOT_STARTED'
        receiverOrigin = 'https://127.0.0.1:8444'
        privateValuesDisplayed = $false
        databasesStarted = $false
        applicationsStarted = $false
        tlsHandshakeAttempted = $false
        socketsOpened = $false
        providerNetworkTouched = $false
        remoteNetworkTouched = $false
        keytoolPath = $script:keytool
        keytoolSha256 = $script:keytoolSha256
        javaHome = $script:javaHome
        javaPath = $script:java
        javaSha256 = $script:javaSha256
        javacPath = $script:javac
        javacSha256 = $script:javacSha256
        javaStoreProbePath = $javaStoreProbePath
        javaStoreProbeSha256 = $script:javaStoreProbeSha256
        boundedJavaCapturePath = $boundedJavaCapturePath
        boundedJavaCaptureSha256 = $script:boundedJavaCaptureSha256
        javaRuntimeQualification = 'PASS_JAVA_25_SANITIZED'
        javaStoreQualification = 'NOT_STARTED'
        ownedCertificates = @(
            [pscustomobject]@{
                role = 'receiver-server-direct-trust'
                ownershipStatus = 'NOT_CREATED'
                storeLocation = 'CurrentUser\Root'
                thumbprint = $null
                sha256 = $null
                subject = $expectedServerSubject
                cngKey = $null
            },
            [pscustomobject]@{
                role = 'sender-client'
                ownershipStatus = 'NOT_CREATED'
                storeLocation = 'CurrentUser\My'
                thumbprint = $null
                sha256 = $null
                subject = $expectedClientSubject
                cngKey = $null
            })
        pki = [pscustomobject]@{
            serverKeyStorePath = $serverKeyStorePath
            serverKeyStorePassword = $serverPassword
            serverPublicCertificatePath = $serverPublicPath
            serverCertificateSha256 = $null
            receiverTrustStorePath = $receiverTrustStorePath
            receiverTrustStorePassword = $receiverTrustStorePassword
            clientPublicCertificatePath = $clientPublicPath
            clientCertificateStore = 'CurrentUser\My'
            clientCertificateSha256 = $null
            serverTrustStore = 'CurrentUser\Root'
            javaStoreProbeInputPath = $javaProbeInputPath
            javaStoreProbeClassRoot = $javaProbeClassRoot
        }
    }
    $marker = [pscustomobject]@{
        schemaVersion = 2
        workOrder = $workOrder
        provisioningWorkOrder = $provisioningWorkOrder
        runId = $runId.ToString('D')
        ownerSid = $ownerSid.Value
        createdAtUtc = $createdAtText
        runRoot = $script:runRoot
        markerPath = $markerPath
        stateIdentityBindingSha256 = $stateIdentityBindingSha256
        provisioningStateStatus = 'IN_PROGRESS_UNBOUND'
        passStateSha256 = $null
        finalizationStatus = 'NOT_READY'
        finalCertificateRecords = $null
    }
    Write-WO048PrivateJsonAtomic -Path $markerPath -Value $marker -OwnerSid $ownerSid
    Write-WO048State
    Invoke-WO048QualificationFailurePoint -Point 'AFTER_PRIVATE_STATE'

    $script:phase = 'SERVER_KEYSTORE'
    $serverAlias = 'wo046-server-' + $runId.ToString('N')
    $previousServerPassword = [Environment]::GetEnvironmentVariable(
        'WO048_KEYTOOL_SERVER_PASSWORD', 'Process')
    try {
        $env:WO048_KEYTOOL_SERVER_PASSWORD = $serverPassword
        Invoke-WO048Keytool -Arguments @(
            '-genkeypair', '-noprompt',
            '-alias', $serverAlias,
            '-dname', 'CN=127.0.0.1,OU=WO-046,O=Betting Project Local Qualification',
            '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA',
            '-validity', '7',
            '-ext', 'BC=ca:false',
            '-ext', 'KU=digitalSignature,keyEncipherment',
            '-ext', 'EKU=serverAuth',
            '-ext', 'SAN=ip:127.0.0.1',
            '-storetype', 'PKCS12', '-keystore', $serverKeyStorePath,
            '-storepass:env', 'WO048_KEYTOOL_SERVER_PASSWORD',
            '-keypass:env', 'WO048_KEYTOOL_SERVER_PASSWORD')
        Invoke-WO048Keytool -Arguments @(
            '-exportcert', '-noprompt',
            '-alias', $serverAlias,
            '-keystore', $serverKeyStorePath,
            '-storetype', 'PKCS12',
            '-storepass:env', 'WO048_KEYTOOL_SERVER_PASSWORD',
            '-file', $serverPublicPath)
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'WO048_KEYTOOL_SERVER_PASSWORD', $previousServerPassword, 'Process')
    }
    foreach ($privateFile in @($serverKeyStorePath, $serverPublicPath)) {
        Protect-WO048PrivateFile -Path $privateFile -OwnerSid $ownerSid
    }
    Assert-WO048NoDescendantReparsePoint -Root $script:runRoot

    $serverCertificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        $serverPublicPath)
    try {
        Assert-WO048ServerCertificateProfile `
            -Certificate $serverCertificate `
            -ObservedAtUtc ([DateTimeOffset]::UtcNow)
        $serverCertificateSha256 = Get-WO048CertificateSha256 -Certificate $serverCertificate
        $serverRootRecords = @($script:state.ownedCertificates | Where-Object {
            $_.role -ceq 'receiver-server-direct-trust'
        })
        if ($serverRootRecords.Count -ne 1 -or
            $serverRootRecords[0].ownershipStatus -cne 'NOT_CREATED') {
            throw 'The WO-048 server recovery record cardinality is not exact.'
        }
        $serverRootRecord = $serverRootRecords[0]
        $serverRootRecord.ownershipStatus = 'DECLARED_BEFORE_IMPORT'
        $serverRootRecord.thumbprint = $serverCertificate.Thumbprint.ToUpperInvariant()
        $serverRootRecord.sha256 = $serverCertificateSha256
        $serverRootRecord.subject = $serverCertificate.Subject
        $script:state.pki.serverCertificateSha256 = $serverCertificateSha256
        Write-WO048State
        Invoke-WO048QualificationFailurePoint -Point 'AFTER_SERVER_KEYSTORE'

        $imported = @(Import-Certificate `
            -FilePath $serverPublicPath `
            -CertStoreLocation 'Cert:\CurrentUser\Root')
        if ($imported.Count -ne 1 -or
            $imported[0].Thumbprint.ToUpperInvariant() -ne $serverRootRecord.thumbprint -or
            (Get-WO048CertificateSha256 -Certificate $imported[0]) -ne $serverCertificateSha256 -or
            $imported[0].Subject -cne $serverRootRecord.subject) {
            throw 'The exact WO-048 receiver trust import was not established.'
        }
        $imported[0].Dispose()
        $serverRootRecord.ownershipStatus = 'OWNED'
        Write-WO048State
    }
    finally {
        $serverCertificate.Dispose()
    }
    Invoke-WO048QualificationFailurePoint -Point 'AFTER_SERVER_ROOT_IMPORT'

    $script:phase = 'CLIENT_CERTIFICATE_CREATE'
    $clientSubjectArgument =
        "CN=WO046 sender $($runId.ToString('D')),OU=WO-046,O=Betting Project Local Qualification"
    $clientValidityAnchorUtc = [DateTimeOffset]::UtcNow
    $clientNotBeforeUtc = $clientValidityAnchorUtc.AddMinutes(-1).UtcDateTime
    $clientNotAfterUtc = $clientValidityAnchorUtc.AddDays(7).AddMinutes(-1).UtcDateTime
    $script:clientCertificate = New-SelfSignedCertificate `
        -Type Custom `
        -Subject $clientSubjectArgument `
        -CertStoreLocation 'Cert:\CurrentUser\My' `
        -Provider 'Microsoft Software Key Storage Provider' `
        -KeyAlgorithm RSA `
        -KeyLength 3072 `
        -HashAlgorithm SHA256 `
        -KeyExportPolicy NonExportable `
        -KeySpec Signature `
        -KeyUsage DigitalSignature `
        -TextExtension @(
            '2.5.29.19={critical}{text}CA=false',
            '2.5.29.37={critical}{text}1.3.6.1.5.5.7.3.2') `
        -NotBefore $clientNotBeforeUtc `
        -NotAfter $clientNotAfterUtc
    if ($null -eq $script:clientCertificate) {
        throw 'The WO-048 sender client certificate was not created.'
    }
    try {
        $script:phase = 'CLIENT_RECOVERY_DECLARATION'
        $clientRecord = Set-WO048ClientCertificateRecoveryDeclaration `
            -Certificate $script:clientCertificate `
            -ExpectedSubject $expectedClientSubject
        Invoke-WO048QualificationFailurePoint `
            -Point 'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP'
        $script:phase = 'CLIENT_CNG_IDENTITY'
        $clientRecord.cngKey = Get-WO048ClientCngKeyIdentity `
            -Certificate $script:clientCertificate
        $script:phase = 'CLIENT_OWNERSHIP_STATE'
        $clientRecord.ownershipStatus = 'OWNED'
        Invoke-WO048QualificationFailurePoint `
            -Point 'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY'
        Write-WO048State
        $script:clientCertificateOwnershipPersisted = $true
    }
    catch {
        $clientFailure = $_
        if (-not $script:clientCertificateRecoveryDeclared) {
            try {
                # A write can fail after replacing the state but before its
                # final ACL check. Retry the recovery-only declaration once;
                # it is idempotent and does not repeat the fallible CNG query.
                $clientRecord = Set-WO048ClientCertificateRecoveryDeclaration `
                    -Certificate $script:clientCertificate `
                    -ExpectedSubject $expectedClientSubject
            }
            catch {
                try {
                    Remove-WO048ExactReturnedClientCertificate `
                        -Certificate $script:clientCertificate
                    $script:clientCertificate = $null
                }
                catch {
                    $script:phase = 'UNPERSISTED_CLIENT_RECOVERY_FAILED'
                    $script:unpersistedClientRollbackFailed = $true
                }
            }
        }
        throw $clientFailure
    }
    Invoke-WO048QualificationFailurePoint `
        -Point 'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED'
    $script:phase = 'CLIENT_CERTIFICATE_PROFILE'
    Assert-WO048ClientCertificateProfile `
        -Certificate $script:clientCertificate `
        -ExpectedSubject $expectedClientSubject `
        -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
        -RequirePrivateKey
    $script:phase = 'CLIENT_PRIVATE_KEY_PROFILE'
    Assert-WO048ClientPrivateKeyProfile -Certificate $script:clientCertificate
    $script:phase = 'CLIENT_PUBLIC_EXPORT'
    Export-Certificate `
        -Cert $script:clientCertificate `
        -FilePath $clientPublicPath `
        -Type CERT | Out-Null
    Protect-WO048PrivateFile -Path $clientPublicPath -OwnerSid $ownerSid

    $script:phase = 'CLIENT_PUBLIC_CERTIFICATE_PROFILE'
    $clientPublicCertificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        $clientPublicPath)
    try {
        Assert-WO048ClientCertificateProfile `
            -Certificate $clientPublicCertificate `
            -ExpectedSubject $expectedClientSubject `
            -ObservedAtUtc ([DateTimeOffset]::UtcNow)
        if ((Get-WO048CertificateSha256 -Certificate $clientPublicCertificate) -ne
            $clientRecord.sha256) {
            throw 'The WO-048 public client certificate is not byte-identical to its store identity.'
        }
    }
    finally {
        $clientPublicCertificate.Dispose()
    }

    $script:phase = 'RECEIVER_TRUSTSTORE'
    $previousTrustPassword = [Environment]::GetEnvironmentVariable(
        'WO048_KEYTOOL_TRUSTSTORE_PASSWORD', 'Process')
    try {
        $env:WO048_KEYTOOL_TRUSTSTORE_PASSWORD = $receiverTrustStorePassword
        Invoke-WO048Keytool -Arguments @(
            '-importcert', '-noprompt',
            '-alias', ('wo046-client-' + $runId.ToString('N')),
            '-file', $clientPublicPath,
            '-keystore', $receiverTrustStorePath,
            '-storetype', 'PKCS12',
            '-storepass:env', 'WO048_KEYTOOL_TRUSTSTORE_PASSWORD')
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            'WO048_KEYTOOL_TRUSTSTORE_PASSWORD', $previousTrustPassword, 'Process')
    }
    Protect-WO048PrivateFile -Path $receiverTrustStorePath -OwnerSid $ownerSid
    Assert-WO048NoDescendantReparsePoint -Root $script:runRoot

    $script:state.pki.clientCertificateSha256 = $clientRecord.sha256
    $script:phase = 'JAVA_WINDOWS_STORE_QUALIFICATION'
    Assert-WO048JavaWindowsStoreQualification `
        -ProbeClassRoot $javaProbeClassRoot `
        -ProbeInputPath $javaProbeInputPath `
        -ClientSha256 $clientRecord.sha256 `
        -ServerSha256 $serverRootRecord.sha256 `
        -OwnerSid $ownerSid
    Assert-WO048NoDescendantReparsePoint -Root $script:runRoot
    $script:state.javaStoreQualification = 'PASS_SUNMSCAPI_NO_HANDSHAKE'
    $script:state.provisioningStatus = 'PASS'
    Write-WO048State
    $passStateSha256 = Get-WO048FileSha256 -Path $script:statePath
    $marker.provisioningStateStatus = 'PASS_BOUND'
    $marker.passStateSha256 = $passStateSha256
    Write-WO048PrivateJsonAtomic -Path $markerPath -Value $marker -OwnerSid $ownerSid
    Invoke-WO048QualificationFailurePoint -Point 'AFTER_RECEIVER_TRUSTSTORE'

    $script:clientCertificate.Dispose()
    $script:clientCertificate = $null
    $privateRecordSha256 = Get-WO048FileSha256 -Path $script:statePath
    if ($privateRecordSha256 -cne $passStateSha256) {
        throw 'The WO-048 bound PASS private-state commitment changed before release.'
    }
    Exit-WO048ToolsLock

    Write-Output 'WO048_PKI_ONLY_PROVISIONING=PASS'
    Write-Output "WO048_RUN_ID=$($runId.ToString('D'))"
    Write-Output "WO048_PRIVATE_IDENTITY_RECORD_SHA256=$privateRecordSha256"
    Write-Output 'WO048_CLIENT_IDENTITY_SELECTED=YES'
    Write-Output 'WO048_RECEIVER_IDENTITY_SELECTED=YES'
    Write-Output 'WO048_CLIENT_PRIVATE_KEY_EXPORTABLE=NO'
    Write-Output 'WO048_JAVA_25_PROVENANCE=PASS'
    Write-Output 'WO048_JAVA_SUNMSCAPI_STORE_QUALIFICATION=PASS'
    Write-Output 'WO048_PRIVATE_VALUES_DISPLAYED=NO'
    Write-Output 'WO048_DATABASES_STARTED=NO'
    Write-Output 'WO048_APPLICATIONS_STARTED=NO'
    Write-Output 'WO048_TLS_HANDSHAKES=0'
    Write-Output 'WO048_SOCKETS_OPENED=0'
    Write-Output 'WO048_PROVIDER_CALLS=0'
    Write-Output 'WO048_REMOTE_NETWORK_CALLS=0'
}
catch {
    $failureException = $_.Exception
    $failureType = $failureException.GetType().Name
    if ($null -ne $script:clientCertificate) {
        if ($script:unpersistedClientRollbackFailed) {
            $script:clientCertificate.Dispose()
        }
        elseif ($script:clientCertificateRecoveryDeclared -or
            $script:clientCertificateOwnershipPersisted) {
            # The exact certificate identity is already durable. The shared
            # cleanup path must perform and prove the certificate/CNG removal.
            $script:clientCertificate.Dispose()
        }
        else {
            try {
                Remove-WO048ExactReturnedClientCertificate `
                    -Certificate $script:clientCertificate
                $script:clientCertificate = $null
            }
            catch {
                $script:unpersistedClientRollbackFailed = $true
            }
        }
        $script:clientCertificate = $null
    }
    $rollback = 'NOT_REQUIRED'
    try {
        Exit-WO048ToolsLock
        if ($script:unpersistedClientRollbackFailed) {
            $rollback = 'FAILED_UNPERSISTED_CLIENT_IDENTITY_RETAINED'
        }
        elseif (-not [string]::IsNullOrWhiteSpace($script:statePath) -and
            (Test-Path -LiteralPath $script:statePath -PathType Leaf)) {
            & $cleanupPath -RunId $runId.ToString('D') -Confirm:$false *> $null
            $rollback = 'PASS'
        }
        elseif ($script:runRootCreatedByThisInvocation -and
            -not [string]::IsNullOrWhiteSpace($script:runRoot) -and
            (Test-Path -LiteralPath $script:runRoot -PathType Container)) {
            Remove-WO048OwnedPreStateRunRoot
            $rollback = 'PASS_PRE_STATE_ROOT_ONLY'
        }
        elseif (-not [string]::IsNullOrWhiteSpace($script:runRoot) -and
            (Test-Path -LiteralPath $script:runRoot)) {
            throw 'WO-048 refuses cleanup of a run root not created by this invocation.'
        }
    }
    catch {
        Exit-WO048ToolsLock
        $rollback = 'FAILED_PRIVATE_STATE_RETAINED'
    }
    $failureHResultChain = $null
    $failureNativeCryptoCode = $null
    try {
        $failureHResults = [System.Collections.Generic.List[string]]::new()
        $failureCursor = $failureException
        for ($failureDepth = 0;
            $failureDepth -lt 4 -and $null -ne $failureCursor;
            $failureDepth++) {
            $failureHResultBytes = [System.BitConverter]::GetBytes(
                [int]$failureCursor.HResult)
            $failureHResults.Add(
                [System.BitConverter]::ToUInt32($failureHResultBytes, 0).ToString('X8'))
            $failureCursor = $failureCursor.InnerException
        }
        $failureHResultChain = ($failureHResults -join ',')
        if ($script:phase -ceq 'CLIENT_CERTIFICATE_CREATE') {
            $nativeCodeMatches = @([System.Text.RegularExpressions.Regex]::Matches(
                $failureException.Message,
                '(?i)(?<![0-9a-f])0x(8009[0-9a-f]{4}|80070005|80070020)(?![0-9a-f])'))
            $nativeCodes = @($nativeCodeMatches |
                ForEach-Object { $_.Groups[1].Value.ToUpperInvariant() } |
                Sort-Object -Unique)
            if ($nativeCodes.Count -eq 1) {
                $failureNativeCryptoCode = $nativeCodes[0]
            }
        }
    }
    catch {
        $failureHResultChain = $null
        $failureNativeCryptoCode = $null
    }
    $publicFailureMessage = if ($script:unpersistedClientRollbackFailed) {
        "WO-048 PKI-only provisioning failed closed during $script:phase ($failureType); rollback=$rollback; manual exact recovery required."
    }
    else {
        "WO-048 PKI-only provisioning failed closed during $script:phase ($failureType); rollback=$rollback."
    }
    $publicFailure = [System.InvalidOperationException]::new($publicFailureMessage)
    if ($failureHResultChain -match '^[0-9A-F]{8}(,[0-9A-F]{8}){0,3}$') {
        $publicFailure.Data['WO048FailureHResultChain'] = $failureHResultChain
    }
    if ($failureNativeCryptoCode -match '^(8009[0-9A-F]{4}|80070005|80070020)$') {
        $publicFailure.Data['WO048FailureNativeCryptoCode'] =
            $failureNativeCryptoCode
    }
    throw $publicFailure
}
finally {
    if ($null -ne $script:clientCertificate) {
        $script:clientCertificate.Dispose()
        $script:clientCertificate = $null
    }
    Exit-WO048ToolsLock
    if ($script:wo048NativeModuleOwnedByThisInvocation) {
        Remove-Module -Name 'J6-NativeBinaryPipeline' -Force `
            -ErrorAction SilentlyContinue
        $script:wo048NativeModuleOwnedByThisInvocation = $false
    }
    if ($script:wo048PkiModuleOwnedByThisInvocation) {
        Remove-Module -Name 'WO048-PkiTools' -Force `
            -ErrorAction SilentlyContinue
        $script:wo048PkiModuleOwnedByThisInvocation = $false
    }
}
