$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $repositoryRoot 'scripts\wo048\WO048-PkiTools.psm1'
$nativeRunnerModulePath = Join-Path $repositoryRoot 'scripts\J6-NativeBinaryPipeline.psm1'
$initializePath = Join-Path $repositoryRoot 'scripts\Initialize-WO046LocalMtlsIdentity.ps1'
$cleanupPath = Join-Path $repositoryRoot 'scripts\Remove-WO046LocalMtlsIdentity.ps1'
$qualificationPath = Join-Path $repositoryRoot `
    'scripts\Invoke-WO048LocalMtlsIdentityQualification.ps1'
$javaProbePath = Join-Path $repositoryRoot 'scripts\wo048\WO048WindowsStoreProbe.java'
$boundedCapturePath = Join-Path $repositoryRoot `
    'scripts\wo048\Invoke-WO048BoundedJavaCapture.ps1'
$testPath = $MyInvocation.MyCommand.Path

$moduleSource = Get-Content -LiteralPath $modulePath -Raw
$initializeSource = Get-Content -LiteralPath $initializePath -Raw
$cleanupSource = Get-Content -LiteralPath $cleanupPath -Raw
$qualificationSource = Get-Content -LiteralPath $qualificationPath -Raw
$javaProbeSource = Get-Content -LiteralPath $javaProbePath -Raw
$boundedCaptureSource = Get-Content -LiteralPath $boundedCapturePath -Raw
$testSource = Get-Content -LiteralPath $testPath -Raw
$allRuntimeSource = $moduleSource + $initializeSource + $cleanupSource +
    $qualificationSource + $javaProbeSource + $boundedCaptureSource
$testTokens = $null
$testParseErrors = $null
$testAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $testPath,
    [ref]$testTokens,
    [ref]$testParseErrors)
$testCommandNames = @($testAst.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.CommandAst]
}, $true) | ForEach-Object { $_.GetCommandName() } | Where-Object { $_ })
$testDynamicInvocations = @($testAst.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and
        $node.InvocationOperator -in @(
            [System.Management.Automation.Language.TokenKind]::Ampersand,
            [System.Management.Automation.Language.TokenKind]::Dot)
}, $true) | ForEach-Object { $_.Extent.Text })

Import-Module -Name $modulePath -Force

function New-WO048SyntheticCertificate {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('Client', 'Server')][string]$Role,
        [switch]$InvalidCa,
        [switch]$InvalidSan,
        [switch]$ExtraSan,
        [switch]$FutureNotBefore,
        [switch]$OmitEku,
        [switch]$OmitKeyUsage,
        [switch]$WrongKeyUsage
    )

    $rsa = [System.Security.Cryptography.RSA]::Create(3072)
    try {
        $subject = if ($Role -eq 'Client') {
            'CN=WO046 sender 11111111-1111-4111-8111-111111111111, OU=WO-046, O=Betting Project Local Qualification'
        }
        else {
            'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
        }
        $request = [System.Security.Cryptography.X509Certificates.CertificateRequest]::new(
            $subject,
            $rsa,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $request.CertificateExtensions.Add(
            [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new(
                [bool]$InvalidCa,
                $false,
                0,
                $true))
        if (-not $OmitKeyUsage) {
            $keyUsage = if ($WrongKeyUsage) {
                [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment
            }
            elseif ($Role -eq 'Client') {
                [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature
            }
            else {
                [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor
                    [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment
            }
            $request.CertificateExtensions.Add(
                [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
                    $keyUsage,
                    $true))
        }
        if (-not $OmitEku) {
            $ekuOids = [System.Security.Cryptography.OidCollection]::new()
            $ekuOid = if ($Role -eq 'Client') {
                '1.3.6.1.5.5.7.3.2'
            }
            else {
                '1.3.6.1.5.5.7.3.1'
            }
            [void]$ekuOids.Add([System.Security.Cryptography.Oid]::new($ekuOid))
            $request.CertificateExtensions.Add(
                [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new(
                    $ekuOids,
                    ($Role -eq 'Client')))
        }
        if ($Role -eq 'Server' -or $InvalidSan) {
            $sanBuilder = [System.Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
            $sanBuilder.AddIpAddress($(if ($InvalidSan) {
                [System.Net.IPAddress]::Parse('127.0.0.2')
            }
            else {
                [System.Net.IPAddress]::Loopback
            }))
            $request.CertificateExtensions.Add($sanBuilder.Build())
        }
        if ($Role -eq 'Server' -and $ExtraSan) {
            $sanBuilder = [System.Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
            $sanBuilder.AddIpAddress([System.Net.IPAddress]::Loopback)
            $sanBuilder.AddUri([uri]'urn:wo048:unexpected')
            $request.CertificateExtensions.RemoveAt($request.CertificateExtensions.Count - 1)
            $request.CertificateExtensions.Add($sanBuilder.Build())
        }
        $anchor = [DateTimeOffset]::UtcNow
        $notBefore = if ($FutureNotBefore) {
            $anchor.AddMinutes(5)
        }
        else {
            $anchor.AddMinutes(-1)
        }
        return $request.CreateSelfSigned(
            $notBefore,
            $notBefore.AddDays(7))
    }
    finally {
        $rsa.Dispose()
    }
}

function New-WO048SyntheticRecordSet {
    param(
        [string]$RunId = '11111111-1111-4111-8111-111111111111',
        [string]$ServerThumbprint = ('A' * 40),
        [string]$ClientThumbprint = ('B' * 40),
        [string]$ServerSha256 = ('a' * 64),
        [string]$ClientSha256 = ('b' * 64),
        [string]$Status = 'OWNED'
    )

    return @(
        [pscustomobject]@{
            role = 'receiver-server-direct-trust'
            ownershipStatus = $Status
            storeLocation = 'CurrentUser\Root'
            thumbprint = $ServerThumbprint
            sha256 = $ServerSha256
            subject = 'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
            cngKey = $null
        },
        [pscustomobject]@{
            role = 'sender-client'
            ownershipStatus = $Status
            storeLocation = 'CurrentUser\My'
            thumbprint = $ClientThumbprint
            sha256 = $ClientSha256
            subject = "CN=WO046 sender $RunId, OU=WO-046, O=Betting Project Local Qualification"
            cngKey = $(if ($Status -eq 'OWNED') { [pscustomobject]@{ exact = $true } } else { $null })
        })
}

function New-WO048SyntheticPrivateState {
    param(
        [string]$RunId = '11111111-1111-4111-8111-111111111111',
        [int]$SchemaVersion = 2,
        [object[]]$Records = $(New-WO048SyntheticRecordSet -RunId $RunId)
    )

    return [pscustomobject]@{
        schemaVersion = $SchemaVersion
        format = 'WO046_LOCAL_MTLS_IDENTITY_V2'
        workOrder = 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign'
        provisioningWorkOrder =
            'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning'
        runId = $RunId
        ownerSid = 'S-1-5-21-111-222-333-1001'
        provisioningStatus = 'PASS'
        cleanupStatus = 'NOT_STARTED'
        receiverOrigin = 'https://127.0.0.1:8444'
        maximumCertificateValidityDays = 7
        javaRuntimeQualification = 'PASS_JAVA_25_SANITIZED'
        javaStoreQualification = 'PASS_SUNMSCAPI_NO_HANDSHAKE'
        privateValuesDisplayed = $false
        databasesStarted = $false
        applicationsStarted = $false
        tlsHandshakeAttempted = $false
        socketsOpened = $false
        providerNetworkTouched = $false
        remoteNetworkTouched = $false
        ownedCertificates = $Records
    }
}

function New-WO048SyntheticFinalizationAuthorities {
    param(
        [Parameter(Mandatory = $true)][string]$RunRoot,
        [string]$RunId = '11111111-1111-4111-8111-111111111111',
        [string]$OwnerSid = 'S-1-5-21-111-222-333-1001',
        [bool]$Ready = $true
    )

    $workOrder = 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign'
    $provisioningWorkOrder =
        'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning'
    $markerPath = Join-Path $RunRoot '.wo048-owner.json'
    $lockPath = Join-Path $RunRoot '.wo048-pki.lock'
    $createdAtUtc = '2026-09-04T12:00:00.0000000+00:00'
    $binding = Get-WO048StateIdentityBinding `
        -WorkOrder $workOrder `
        -ProvisioningWorkOrder $provisioningWorkOrder `
        -RunId $RunId `
        -OwnerSid $OwnerSid `
        -CreatedAtUtc $createdAtUtc `
        -RunRoot $RunRoot `
        -MarkerPath $markerPath
    $records = New-WO048SyntheticRecordSet -RunId $RunId
    $lock = New-WO048FinalizationLockAuthority `
        -WorkOrder $workOrder `
        -ProvisioningWorkOrder $provisioningWorkOrder `
        -RunId $RunId `
        -OwnerSid $OwnerSid `
        -CreatedAtUtc $createdAtUtc `
        -RunRoot $RunRoot `
        -MarkerPath $markerPath `
        -LockPath $lockPath `
        -StateIdentityBindingSha256 $binding
    if ($Ready) {
        $lock.finalizationStatus = 'READY_AFTER_RESIDUAL_PROOF'
        $lock.finalCertificateRecords = $records
    }
    $marker = [pscustomobject][ordered]@{
        schemaVersion = 2
        workOrder = $workOrder
        provisioningWorkOrder = $provisioningWorkOrder
        runId = $RunId
        ownerSid = $OwnerSid
        createdAtUtc = $createdAtUtc
        runRoot = [IO.Path]::GetFullPath($RunRoot)
        markerPath = [IO.Path]::GetFullPath($markerPath)
        stateIdentityBindingSha256 = $binding
        provisioningStateStatus = 'CLEANUP_TRANSITION'
        passStateSha256 = ('c' * 64)
        finalizationStatus = 'READY_AFTER_RESIDUAL_PROOF'
        finalCertificateRecords = $records
    }
    return [pscustomobject]@{
        WorkOrder = $workOrder
        ProvisioningWorkOrder = $provisioningWorkOrder
        RunId = $RunId
        OwnerSid = $OwnerSid
        RunRoot = [IO.Path]::GetFullPath($RunRoot)
        MarkerPath = [IO.Path]::GetFullPath($markerPath)
        LockPath = [IO.Path]::GetFullPath($lockPath)
        Lock = $lock
        Marker = $marker
    }
}

function Test-WO048ActionThrows {
    param([Parameter(Mandatory = $true)][scriptblock]$Action)

    try {
        & $Action *> $null
        return $false
    }
    catch {
        return $true
    }
}

function Get-WO048FunctionDefinitionSource {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string[]]$Names
    )

    $tokens = $null
    $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        $Path,
        [ref]$tokens,
        [ref]$errors)
    if ($errors.Count -ne 0) {
        throw 'The WO-048 executable helper source did not parse.'
    }
    $definitions = [System.Collections.Generic.List[string]]::new()
    foreach ($name in $Names) {
        $matches = @($ast.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
                $node.Name -ceq $name
        }, $true))
        if ($matches.Count -ne 1) {
            throw 'The WO-048 executable helper source is not exact.'
        }
        $definitions.Add($matches[0].Extent.Text)
    }
    return ($definitions -join "`n`n")
}

Describe 'WO-048 PKI-only script surface' {
    It 'parses every dedicated script without PowerShell syntax errors' {
        foreach ($path in @(
                $modulePath, $initializePath, $cleanupPath, $qualificationPath,
                $boundedCapturePath)) {
            $tokens = $null
            $parseErrors = $null
            [System.Management.Automation.Language.Parser]::ParseFile(
                $path,
                [ref]$tokens,
                [ref]$parseErrors) | Out-Null
            $parseErrors.Count | Should Be 0
        }
    }

    It 'resolves pinned module commands from real nested helpers under pwsh File scope' {
        $scopeRoot = Join-Path $TestDrive 'empty-module-scope-root'
        [void](New-Item -ItemType Directory -Path $scopeRoot)
        $pwshPath = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
        Import-Module -Name $nativeRunnerModulePath -Force -ErrorAction Stop
        try {
            $result = Invoke-J6BoundedNativeCommand `
                -FilePath $pwshPath `
                -ArgumentList @(
                    '-NoLogo', '-NoProfile', '-NonInteractive',
                    '-File', $qualificationPath,
                    '-ModuleCommandScopeQualificationOnly',
                    '-ModuleCommandScopeQualificationRoot', $scopeRoot) `
                -WorkingDirectory $repositoryRoot `
                -TimeoutMilliseconds 30000 `
                -StartupTimeoutMilliseconds 5000 `
                -CleanupTimeoutMilliseconds 5000
            $result.ExitCode | Should Be 0
            $result.StandardOutput.Trim() |
                Should Be 'WO048_NESTED_MODULE_COMMAND_RESOLUTION=PASS'
            $result.ProcessTreeCleanup | Should Be 'PASS'
            $result.UnexpectedDescendantCleanup | Should Be $false

            $fakeModuleRoot = Join-Path $TestDrive 'unexpected-module-origin'
            [void](New-Item -ItemType Directory -Path $fakeModuleRoot)
            $fakeModulePath = Join-Path $fakeModuleRoot 'WO048-PkiTools.psm1'
            [IO.File]::WriteAllText(
                $fakeModulePath,
                "function Get-WO048UnexpectedOrigin { 'unexpected' }`n" +
                    "Export-ModuleMember -Function 'Get-WO048UnexpectedOrigin'`n",
                [Text.UTF8Encoding]::new($false, $true))
            $escapedFake = $fakeModulePath.Replace("'", "''")
            $escapedQualification = $qualificationPath.Replace("'", "''")
            $escapedScopeRoot = $scopeRoot.Replace("'", "''")
            $command = "Import-Module -Name '$escapedFake' -Global -Force " +
                "-ErrorAction Stop; & '$escapedQualification' " +
                "-ModuleCommandScopeQualificationOnly " +
                "-ModuleCommandScopeQualificationRoot '$escapedScopeRoot'"
            $wrongOrigin = Invoke-J6BoundedNativeCommand `
                -FilePath $pwshPath `
                -ArgumentList @(
                    '-NoLogo', '-NoProfile', '-NonInteractive',
                    '-Command', $command) `
                -WorkingDirectory $repositoryRoot `
                -TimeoutMilliseconds 30000 `
                -StartupTimeoutMilliseconds 5000 `
                -CleanupTimeoutMilliseconds 5000
            ($wrongOrigin.ExitCode -ne 0) | Should Be $true
            $wrongOrigin.ProcessTreeCleanup | Should Be 'PASS'
            $wrongOrigin.UnexpectedDescendantCleanup | Should Be $false
        }
        finally {
            Remove-Module -Name 'J6-NativeBinaryPipeline' -Force `
                -ErrorAction SilentlyContinue
        }
    }

    It 'does not execute the real provisioner from its offline Pester suite' {
        $testParseErrors.Count | Should Be 0
        ($testCommandNames -contains $initializePath) | Should Be $false
        ($testCommandNames -contains 'New-SelfSignedCertificate') | Should Be $false
        ($testCommandNames -contains 'Import-Certificate') | Should Be $false
        ($testCommandNames -contains 'Export-Certificate') | Should Be $false
        @($testDynamicInvocations | Where-Object {
            $_ -match '(?i)(initializePath|cleanupPath|qualificationPath|Initialize-WO046|Remove-WO046|Invoke-WO048LocalMtlsIdentityQualification)'
        }).Count | Should Be 0
    }

    It 'contains no Docker database application or outbound-network operation' {
        $allRuntimeSource | Should Not Match '(?i)(?:^|[\s''"])(docker(?:\.exe)?|psql(?:\.exe)?)(?:[\s''"]|$)'
        $allRuntimeSource | Should Not Match '(?i)spring-boot:run|Start-Process'
        $allRuntimeSource | Should Not Match '(?i)Invoke-WebRequest|Invoke-RestMethod'
        $allRuntimeSource | Should Not Match `
            '(?i)\b(?:TcpClient|TcpListener|UdpClient)\b|(?:System\.Net\.Http|java\.net\.http)\.HttpClient'
        $allRuntimeSource | Should Not Match '(?i)api\.sofascore\.com|51\.255\.167\.32|0\.0\.0\.0'
        $qualificationSource | Should Match 'Get-NetTCPConnection -State Listen'
        ([regex]::Matches($allRuntimeSource, 'Get-NetTCPConnection').Count) | Should Be 1
    }

    It 'uses a new GUID child under one fixed LocalApplicationData campaign base' {
        $moduleSource | Should Match 'LocalApplicationData'
        $moduleSource | Should Match "'WO-SS-20260904-046'"
        $qualificationSource | Should Match `
            'SofaScoreLocalLab\\qualifications\\WO-SS-20260904-046'
        $allRuntimeSource | Should Not Match `
            'SofaScoreLocalLab\\qualifications\\WO-SS-20260904-048'
        $moduleSource | Should Match 'Assert-WO048ImmediateGuidChild'
        $moduleSource | Should Match 'refuses UNC, provider and non-drive paths'
        $initializeSource | Should Match '\$runId = \[guid\]::NewGuid\(\)'
        $initializeSource | Should Not Match '(?m)^\s*param\([^)]*RunId'
    }

    It 'protects the private root and every state file for the exact owner SID' {
        $moduleSource | Should Match 'SetAccessRuleProtection\(\$true, \$false\)'
        $moduleSource | Should Match 'FileSystemRights\]::FullControl'
        $moduleSource | Should Match 'grants access outside its exact owner'
        $moduleSource | Should Match 'Assert-WO048NoDescendantReparsePoint'
        $initializeSource | Should Match 'Protect-WO048PrivateDirectory'
        $initializeSource | Should Match 'Write-WO048PrivateJsonAtomic'
        $cleanupSource | Should Match 'Assert-WO048PrivateAcl.*RequireProtected'
    }

    It 'keeps fingerprints secrets and private paths in one protected external record' {
        $initializeSource | Should Match "format = 'WO046_LOCAL_MTLS_IDENTITY_V2'"
        $initializeSource | Should Match 'identity\.private\.json'
        $initializeSource | Should Match 'serverKeyStorePassword = \$serverPassword'
        $initializeSource | Should Match 'clientCertificateSha256 = \$null'
        $initializeSource | Should Match 'WO048_PRIVATE_IDENTITY_RECORD_SHA256='
        $initializeSource | Should Not Match 'Write-Output.*(?:thumbprint|clientCertificateSha256|serverCertificateSha256|serverKeyStorePath|clientPublicCertificatePath|Password=)'
        $cleanupSource | Should Not Match 'Write-Output.*(?:thumbprint|clientCertificateSha256|serverCertificateSha256|\.p12|\.cer)'
        $qualificationSource | Should Match 'WO048_PRIVATE_CERTIFICATE_FINGERPRINT_DISCLOSED=NO'
        $qualificationSource | Should Match 'WO048_PRIVATE_PATH_DISCLOSED=NO'
    }
}

Describe 'WO-048 exact certificate profiles and ownership ordering' {
    It 'creates the exact loopback receiver server certificate and stores its private key only in PKCS12' {
        $initializeSource | Should Match "'-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA'"
        $initializeSource | Should Match "'-validity', '7'"
        $initializeSource | Should Match "'-ext', 'BC=ca:false'"
        $initializeSource | Should Match "'-ext', 'KU=digitalSignature,keyEncipherment'"
        $initializeSource | Should Match "'-ext', 'EKU=serverAuth'"
        $initializeSource | Should Match "'-ext', 'SAN=ip:127\.0\.0\.1'"
        $initializeSource | Should Match "receiver-server\.private\.p12"
        $initializeSource | Should Match "receiver-server\.public\.cer"
        $initializeSource | Should Match "Cert:\\CurrentUser\\Root"
    }

    It 'uses the previously qualified bounded native runner for every keytool process' {
        $initializeSource | Should Match 'J6-NativeBinaryPipeline\.psm1'
        $initializeSource | Should Match 'Invoke-J6BoundedNativeCommand'
        $initializeSource | Should Match 'TimeoutMilliseconds 30000'
        $initializeSource | Should Match 'StartupTimeoutMilliseconds 10000'
        $initializeSource | Should Match 'CleanupTimeoutMilliseconds 10000'
        $initializeSource | Should Match "Confinement -cne 'WINDOWS_KILL_ON_JOB_CLOSE'"
        $initializeSource | Should Match 'ActiveProcessesAfterCleanup'
        $initializeSource | Should Match 'UnexpectedDescendantCleanup'
        $initializeSource | Should Match `
            'UnexpectedDescendantCleanup -isnot \[bool\]'
        $initializeSource | Should Match 'MaximumCharactersPerStream.*16384'
        $initializeSource | Should Match 'stdoutBytes\.Length -gt 65536'
        $boundedCaptureSource | Should Match 'ReadBoundedAsync'
        $boundedCaptureSource | Should Match 'value\.Length > maximumCharacters - read'
        $boundedCaptureSource | Should Match 'process\.Kill\(true\)'
        $boundedCaptureSource | Should Match 'FileMode\]::CreateNew'
        $initializeSource | Should Match 'TargetProcessId'
        $initializeSource | Should Match 'TargetStartedAtUtcTicks'
        $initializeSource | Should Not Match '& \$script:keytool'
        $cleanupSource | Should Not Match 'J6-NativeBinaryPipeline|J6-NativeProcessHost'
        $cleanupSource | Should Not Match 'nativeRunnerModuleSha256|nativeProcessHostSha256'
        $cleanupSource | Should Not Match 'javaPath|javacPath|keytoolPath|javaHome'
    }

    It 'pins Java 25 exclusively under exact JAVA_HOME and sanitizes each child' {
        $moduleSource | Should Match "GetEnvironmentVariable\('JAVA_HOME', 'Process'\)"
        $moduleSource | Should Match 'Join-Path \$javaHome ''bin'''
        $moduleSource | Should Match "@\('java\.exe', 'javac\.exe', 'keytool\.exe'\)"
        $initializeSource | Should Not Match "Get-Command 'keytool\.exe'"
        $moduleSource | Should Match 'FileVersionInfo\]::GetVersionInfo'
        $moduleSource | Should Match '\$version -notmatch ''\^25'
        $moduleSource | Should Match 'Assert-WO048LocalFixedPathPrefix'
        foreach ($name in @(
                'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
                'JDK_JAVAC_OPTIONS',
                'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'NO_PROXY')) {
            $initializeSource | Should Match $name
            $javaProbeSource | Should Match $name
        }
        $initializeSource | Should Match 'Invoke-WO048WithSanitizedJavaEnvironment'
        $initializeSource | Should Match 'WO048_EXPECTED_JAVA_HOME'
        $initializeSource | Should Match 'Get-WO048FileSha256.*ToolPath'
    }

    It 'creates an exact non-exportable CurrentUser My CNG client identity' {
        $initializeSource | Should Match "Cert:\\CurrentUser\\My"
        $initializeSource | Should Match "Provider 'Microsoft Software Key Storage Provider'"
        $initializeSource | Should Match 'KeyLength 3072'
        $initializeSource | Should Match 'HashAlgorithm SHA256'
        $initializeSource | Should Match 'KeyExportPolicy NonExportable'
        $initializeSource | Should Match 'KeyUsage DigitalSignature'
        $initializeSource | Should Match '2\.5\.29\.19=\{critical\}\{text\}CA=false'
        $initializeSource | Should Match '2\.5\.29\.37=\{critical\}\{text\}1\.3\.6\.1\.5\.5\.7\.3\.2'
        $moduleSource | Should Match 'RSACng'
        $moduleSource | Should Match 'CngExportPolicies\]::None'
    }

    It 'exposes only a bounded hexadecimal HRESULT chain with a sanitized failure' {
        $initializeSource | Should Match '\$failureDepth -lt 4'
        $initializeSource | Should Match 'BitConverter\]::ToUInt32'
        $initializeSource | Should Match "ToString\('X8'\)"
        $initializeSource | Should Match "\^\[0-9A-F\]\{8\}\(,\[0-9A-F\]\{8\}\)\{0,3\}\$"
        $initializeSource | Should Match "Data\['WO048FailureHResultChain'\]"
        $initializeSource | Should Not Match 'Data\[''WO048FailureMessage''\]'
        $initializeSource | Should Not Match 'Data\[''WO048FailurePath''\]'
    }

    It 'allowlists one native crypto code without exposing the cmdlet message' {
        $initializeSource | Should Match "\$script:phase -ceq 'CLIENT_CERTIFICATE_CREATE'"
        $initializeSource | Should Match '0x\(8009\[0-9a-f\]\{4\}\|80070005\|80070020\)'
        $initializeSource | Should Match '\$nativeCodes.Count -eq 1'
        $initializeSource | Should Match "\^\(8009\[0-9A-F\]\{4\}\|80070005\|80070020\)\$"
        $initializeSource | Should Match "Data\['WO048FailureNativeCryptoCode'\]"
        $initializeSource | Should Not Match "Data\['WO048FailureNativeMessage'\]"

        $pattern = '(?i)(?<![0-9a-f])0x(8009[0-9a-f]{4}|80070005|80070020)(?![0-9a-f])'
        $one = @([regex]::Matches('private text 0x8009000f private text', $pattern))
        $one.Count | Should Be 1
        $one[0].Groups[1].Value.ToUpperInvariant() | Should Be '8009000F'
        @([regex]::Matches('private text 0xDEADBEEF private text', $pattern)).Count |
            Should Be 0
        @([regex]::Matches('0x8009000F and 0x80070005', $pattern)).Count |
            Should Be 2
    }

    It 'performs rollback before any non-blocking public failure classification' {
        $caughtFailure = $initializeSource.IndexOf('$failureException = $_.Exception')
        $rollback = $initializeSource.IndexOf('& $cleanupPath', $caughtFailure)
        $classification = $initializeSource.IndexOf(
            '$failureHResultChain = $null', $rollback)
        $classificationGuard = $initializeSource.IndexOf('try {', $classification)
        $classificationFallback = $initializeSource.IndexOf(
            '$failureNativeCryptoCode = $null',
            $initializeSource.IndexOf('catch {', $classificationGuard))
        $publicFailure = $initializeSource.IndexOf(
            '$publicFailure = [System.InvalidOperationException]::new',
            $classificationFallback)

        $caughtFailure | Should BeGreaterThan -1
        $rollback | Should BeGreaterThan $caughtFailure
        $classification | Should BeGreaterThan $rollback
        $classificationGuard | Should BeGreaterThan $classification
        $classificationFallback | Should BeGreaterThan $classificationGuard
        $publicFailure | Should BeGreaterThan $classificationFallback
    }

    It 'persists exact recovery authority before server import and before client failure checks' {
        $serverRecord = $initializeSource.IndexOf("role = 'receiver-server-direct-trust'")
        $serverState = $initializeSource.IndexOf('Write-WO048State', $serverRecord)
        $serverImport = $initializeSource.IndexOf('Import-Certificate', $serverRecord)
        $clientCreate = $initializeSource.IndexOf(
            '$script:clientCertificate = New-SelfSignedCertificate')
        $clientCreatePhase = $initializeSource.LastIndexOf(
            "`$script:phase = 'CLIENT_CERTIFICATE_CREATE'", $clientCreate)
        $declarationFunction = $initializeSource.IndexOf(
            'function Set-WO048ClientCertificateRecoveryDeclaration')
        $declarationTransition = $initializeSource.IndexOf(
            'Set-WO048ClientCertificateDeclaration', $declarationFunction)
        $declarationStateWrite = $initializeSource.IndexOf(
            'Write-WO048State', $declarationTransition)
        $declarationFlag = $initializeSource.IndexOf(
            '$script:clientCertificateRecoveryDeclared = $true', $declarationStateWrite)
        $clientDeclaration = $initializeSource.IndexOf(
            'Set-WO048ClientCertificateRecoveryDeclaration', $clientCreate)
        $clientDeclarationPhase = $initializeSource.LastIndexOf(
            "`$script:phase = 'CLIENT_RECOVERY_DECLARATION'", $clientDeclaration)
        $clientBeforeOwnershipFailure = $initializeSource.IndexOf(
            "'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP'", $clientDeclaration)
        $clientCng = $initializeSource.IndexOf(
            'Get-WO048ClientCngKeyIdentity', $clientBeforeOwnershipFailure)
        $clientCngPhase = $initializeSource.LastIndexOf(
            "`$script:phase = 'CLIENT_CNG_IDENTITY'", $clientCng)
        $clientRecord = $initializeSource.IndexOf(
            '$clientRecord.ownershipStatus = ''OWNED''', $clientCng)
        $clientOwnershipPhase = $initializeSource.LastIndexOf(
            "`$script:phase = 'CLIENT_OWNERSHIP_STATE'", $clientRecord)
        $clientInMemoryFailure = $initializeSource.IndexOf(
            "'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY'", $clientRecord)
        $clientOwnedState = $initializeSource.IndexOf(
            'Write-WO048State', $clientInMemoryFailure)
        $clientPersistedFailure = $initializeSource.IndexOf(
            "'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED'", $clientOwnedState)
        $clientProfilePhase = $initializeSource.IndexOf(
            "`$script:phase = 'CLIENT_CERTIFICATE_PROFILE'", $clientPersistedFailure)
        $clientProfile = $initializeSource.IndexOf(
            'Assert-WO048ClientCertificateProfile', $clientProfilePhase)
        $clientPrivateKeyPhase = $initializeSource.IndexOf(
            "`$script:phase = 'CLIENT_PRIVATE_KEY_PROFILE'", $clientProfile)
        $clientPrivateKeyProfile = $initializeSource.IndexOf(
            'Assert-WO048ClientPrivateKeyProfile', $clientPrivateKeyPhase)
        $clientExportPhase = $initializeSource.IndexOf(
            "`$script:phase = 'CLIENT_PUBLIC_EXPORT'", $clientPrivateKeyProfile)
        $clientExport = $initializeSource.IndexOf('Export-Certificate', $clientExportPhase)
        $clientPublicProfilePhase = $initializeSource.IndexOf(
            "`$script:phase = 'CLIENT_PUBLIC_CERTIFICATE_PROFILE'", $clientExport)
        $clientPublicProfile = $initializeSource.IndexOf(
            'Assert-WO048ClientCertificateProfile', $clientPublicProfilePhase)

        $serverRecord | Should BeGreaterThan -1
        $serverState | Should BeGreaterThan $serverRecord
        $serverImport | Should BeGreaterThan $serverState
        $clientCreate | Should BeGreaterThan -1
        $clientCreatePhase | Should BeGreaterThan -1
        $clientCreate | Should BeGreaterThan $clientCreatePhase
        $declarationFunction | Should BeGreaterThan -1
        $declarationTransition | Should BeGreaterThan $declarationFunction
        $declarationStateWrite | Should BeGreaterThan $declarationTransition
        $declarationFlag | Should BeGreaterThan $declarationStateWrite
        $clientDeclaration | Should BeGreaterThan $clientCreate
        $clientDeclarationPhase | Should BeGreaterThan $clientCreate
        $clientDeclaration | Should BeGreaterThan $clientDeclarationPhase
        $clientBeforeOwnershipFailure | Should BeGreaterThan $clientDeclaration
        $clientCng | Should BeGreaterThan $clientBeforeOwnershipFailure
        $clientCngPhase | Should BeGreaterThan $clientBeforeOwnershipFailure
        $clientCng | Should BeGreaterThan $clientCngPhase
        $clientRecord | Should BeGreaterThan $clientCng
        $clientOwnershipPhase | Should BeGreaterThan $clientCng
        $clientRecord | Should BeGreaterThan $clientOwnershipPhase
        $clientInMemoryFailure | Should BeGreaterThan $clientRecord
        $clientOwnedState | Should BeGreaterThan $clientInMemoryFailure
        $clientPersistedFailure | Should BeGreaterThan $clientOwnedState
        $clientProfilePhase | Should BeGreaterThan $clientPersistedFailure
        $clientProfile | Should BeGreaterThan $clientProfilePhase
        $clientPrivateKeyPhase | Should BeGreaterThan $clientProfile
        $clientPrivateKeyProfile | Should BeGreaterThan $clientPrivateKeyPhase
        $clientExportPhase | Should BeGreaterThan $clientPrivateKeyProfile
        $clientExport | Should BeGreaterThan $clientExportPhase
        $clientPublicProfilePhase | Should BeGreaterThan $clientExport
        $clientPublicProfile | Should BeGreaterThan $clientPublicProfilePhase
    }

    It 'retains a certificate-only recovery declaration when CNG inspection cannot complete' {
        $moduleSource | Should Match "ownershipStatus = 'CERTIFICATE_DECLARED'"
        $initializeSource | Should Match 'does not inspect the private key'
        $cleanupSource | Should Match "ownershipStatus -eq 'CERTIFICATE_DECLARED'"
        $cleanupSource | Should Match `
            'Persist the exact CNG authority before any destructive'
        $cleanupSource.IndexOf('$record.cngKey = Get-WO048ClientCngKeyIdentity') |
            Should BeLessThan $cleanupSource.IndexOf(
                'Remove-Item',
                $cleanupSource.IndexOf('$record.cngKey = Get-WO048ClientCngKeyIdentity'))
        $cleanupSource | Should Match `
            'cannot prove a certificate-only client declaration has no orphaned CNG key'
        $initializeSource | Should Match `
            'FAILED_UNPERSISTED_CLIENT_IDENTITY_RETAINED'
    }

    It 'builds a receiver truststore from the public client certificate only' {
        $initializeSource | Should Match 'Export-Certificate[\s\S]+sender-client\.public\.cer'
        $initializeSource | Should Match "'-importcert', '-noprompt'"
        $initializeSource | Should Match "receiver-client-trust\.private\.p12"
        $initializeSource | Should Match "'-storetype', 'PKCS12'"
        $initializeSource | Should Not Match '(?i)Export-PfxCertificate'
    }
}

Describe 'WO-048 fail-closed lifecycle' {
    It 'covers seven bounded failure points plus one retained nominal identity' {
        foreach ($point in @(
                'AFTER_PRIVATE_STATE',
                'AFTER_SERVER_KEYSTORE',
                'AFTER_SERVER_ROOT_IMPORT',
                'AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP',
                'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY',
                'AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED',
                'AFTER_RECEIVER_TRUSTSTORE')) {
            $initializeSource | Should Match $point
            $qualificationSource | Should Match $point
        }
        $qualificationSource | Should Match 'WO048_FAILURE_POINTS=7_OF_7_PASS'
        $qualificationSource | Should Match 'WO048_LOCAL_MTLS_IDENTITY_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED'
        ([regex]::Matches(
            $qualificationSource,
            [regex]::Escape('& $cleanupPath')).Count) | Should Be 1
        $qualificationSource.IndexOf('& $cleanupPath') |
            Should BeGreaterThan $qualificationSource.IndexOf(
                "catch {`r`n    try {",
                $qualificationSource.IndexOf('$beforeNominal'))
    }

    It 'removes the exact client private key only after three-factor certificate matching' {
        $cleanupSource | Should Match 'storeLocation -cne \$expectedStore'
        $cleanupSource | Should Match 'thumbprint.*\^\[0-9A-F\]\{40\}\$'
        $cleanupSource | Should Match 'sha256.*\^\[0-9a-f\]\{64\}\$'
        $cleanupSource | Should Match 'Get-WO048CertificateSha256.*Record\.sha256'
        $cleanupSource | Should Match 'certificate\.Subject -cne \$Record\.subject'
        $cleanupSource | Should Match 'Remove-Item[\s\S]+-DeleteKey[\s\S]+-Force'
    }

    It 'binds immutable state identity without a false nonce-authentication claim' {
        $moduleSource | Should Match 'Get-WO048StateIdentityBinding'
        $initializeSource | Should Match 'stateIdentityBindingSha256'
        $cleanupSource | Should Match 'marker\.stateIdentityBindingSha256'
        $initializeSource | Should Match "provisioningStateStatus = 'PASS_BOUND'"
        $cleanupSource | Should Match 'marker\.passStateSha256 -cne \$loadedStateSha256'
        $cleanupSource | Should Match 'does not bind the immutable private-state identity'
        $allRuntimeSource | Should Not Match 'ownershipNonce|ownershipSha256|authenticate the private identity'
        $initializeSource | Should Match 'moduleSha256 = \$moduleSha256'
        $cleanupSource | Should Not Match 'state\.moduleSha256|Get-WO048FileSha256 -Path \$pin\[1\]'
        $exclusiveLockIndex = $cleanupSource.IndexOf('FileShare]::None')
        $exclusiveLockIndex | Should BeGreaterThan -1
        $exclusiveLockIndex | Should BeLessThan $cleanupSource.IndexOf(
            'Read-WO048FinalizationLockJournalFromStream',
            $exclusiveLockIndex)
    }

    It 'allows only the exact PKI artifacts and makes final cleanup resumable' {
        foreach ($name in @(
                'java-store-probe-input.private.properties',
                'java-store-probe-classes',
                'native-java.stderr.private.txt',
                'native-java.stdout.private.txt',
                'receiver-client-trust.private.p12',
                'receiver-server.private.p12',
                'receiver-server.public.cer',
                'sender-client.public.cer')) {
            $cleanupSource | Should Match ([regex]::Escape($name))
        }
        $cleanupSource | Should Match 'contains an unexpected child'
        $cleanupSource | Should Match 'READY_AFTER_RESIDUAL_PROOF'
        $cleanupSource | Should Match 'FINALIZING_METADATA'
        $cleanupSource | Should Match 'PASS_ALREADY_ABSENT'
        $moduleSource | Should Match 'WO048_FINALIZATION_LOCK_AUTHORITY_V1'
        $initializeSource | Should Match 'Write-WO048FinalizationLockAuthorityToStream'
        $cleanupSource | Should Match 'FINAL_MARKER_AND_LOCK'
        $cleanupSource | Should Match 'FINAL_MARKER_ONLY'
        $cleanupSource | Should Match 'FINAL_LOCK_ONLY'
        $cleanupSource | Should Match 'FINAL_EMPTY_ROOT'
        $cleanupSource.IndexOf('Remove-Item -LiteralPath $statePath -Force') |
            Should BeGreaterThan $cleanupSource.IndexOf('READY_AFTER_RESIDUAL_PROOF')
        $cleanupSource.LastIndexOf('Remove-Item -LiteralPath $lockPath -Force') |
            Should BeLessThan $cleanupSource.LastIndexOf(
                'Remove-Item -LiteralPath $markerPath -Force')
        $cleanupSource.LastIndexOf('Remove-Item -LiteralPath $markerPath -Force') |
            Should BeLessThan $cleanupSource.LastIndexOf(
                'Remove-Item -LiteralPath $runRoot -Force')
        $cleanupSource | Should Match `
            'WO048_FINALIZATION_AUTHORITY_PRESERVED_THROUGH_LOCK_DELETE=YES'
        $cleanupSource | Should Not Match 'Remove-Item -LiteralPath \$runRoot -Recurse'
    }

    It 'requires exactly two recovery records and proves the CNG container absent' {
        $initializeSource | Should Match 'ownedCertificates = @\('
        $moduleSource | Should Match '\$Records\.Count -ne 2'
        $cleanupSource | Should Match 'RequireOwned'
        $moduleSource | Should Match 'keyName = \$keyName'
        $moduleSource | Should Match 'uniqueName = \$uniqueName'
        $moduleSource | Should Match 'keyFilePath = \$keyFilePath'
        $cleanupSource | Should Match 'CngKey\]::Exists'
        $cleanupSource | Should Match 'Get-WO048OwnedClientPrivateKeyResidualCount'
        $qualificationSource | Should Match '\$newPrivateKeyFiles\.Count -ne 1'
        $cleanupSource.IndexOf('Get-WO048OwnedClientPrivateKeyResidualCount') |
            Should BeLessThan $cleanupSource.IndexOf(
                "Write-Output 'WO048_CLEANUP_CLIENT_PRIVATE_KEY_RESIDUAL_COUNT=0'")
    }

    It 'requires a canonical lowercase UUID-D before cleanup can resolve a run root' {
        $cleanupSource | Should Match '\$_ -cnotmatch ''\^\[0-9a-f\]'
        $cleanupSource | Should Match 'requires a canonical lowercase UUID-D RunId'
        $moduleSource | Should Match 'Assert-WO048CanonicalLowercaseRunId'
        $cleanupSource.IndexOf('$RunId -cne $canonicalRunId') |
            Should BeLessThan $cleanupSource.IndexOf('$ownerSid = Get-WO048CurrentOwnerSid')
    }

    It 'retains explicit zero-operation lifecycle evidence' {
        foreach ($source in @($initializeSource, $cleanupSource, $qualificationSource)) {
            $source | Should Match 'DATABASES_(?:STARTED|TOUCHED)=NO'
            $source | Should Match 'APPLICATIONS_STARTED=NO'
            $source | Should Match 'TLS_HANDSHAKES=0'
            $source | Should Match 'SOCKETS_OPENED=0'
            $source | Should Match 'PROVIDER_CALLS=0'
            $source | Should Match 'REMOTE_NETWORK_CALLS=0'
        }
    }
}

Describe 'WO-048 executable destructive-boundary guards' {
    It 'rejects invalid and non-canonical RunIds without normalization' {
        (Test-WO048ActionThrows {
            Assert-WO048CanonicalLowercaseRunId -RunId 'not-a-guid'
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048CanonicalLowercaseRunId `
                -RunId '11111111-1111-4111-8111-11111111111A'
        }) | Should Be $true
        (Assert-WO048CanonicalLowercaseRunId `
            -RunId '11111111-1111-4111-8111-111111111111') |
            Should Be '11111111-1111-4111-8111-111111111111'
    }

    It 'preserves a colliding run root and its sentinel byte-for-byte' {
        $campaignBase = Join-Path $TestDrive 'collision-campaign'
        New-Item -ItemType Directory -Path $campaignBase | Out-Null
        $runId = [guid]'11111111-1111-4111-8111-111111111111'
        $runRoot = Join-Path $campaignBase $runId.ToString('D')
        New-Item -ItemType Directory -Path $runRoot | Out-Null
        $sentinelPath = Join-Path $runRoot 'do-not-delete.sentinel'
        [IO.File]::WriteAllBytes($sentinelPath, [byte[]](1, 7, 3, 9))
        $before = [Convert]::ToBase64String([IO.File]::ReadAllBytes($sentinelPath))

        (Test-WO048ActionThrows {
            New-WO048ExclusiveRunRoot -CampaignBase $campaignBase -RunId $runId
        }) | Should Be $true

        (Test-Path -LiteralPath $runRoot -PathType Container) | Should Be $true
        (Test-Path -LiteralPath $sentinelPath -PathType Leaf) | Should Be $true
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($sentinelPath)) |
            Should Be $before
    }

    It 'classifies every finalization state marker and lock recovery window' {
        $cases = @(
            [pscustomobject]@{
                State = $true; Marker = $true; Lock = $true
                Children = @('identity.private.json', '.wo048-owner.json',
                    '.wo048-pki.lock', 'pki')
                Expected = 'ACTIVE_STATE_MARKER_LOCK'
            },
            [pscustomobject]@{
                State = $false; Marker = $true; Lock = $true
                Children = @('.wo048-owner.json', '.wo048-pki.lock')
                Expected = 'FINAL_MARKER_AND_LOCK'
            },
            [pscustomobject]@{
                State = $false; Marker = $true; Lock = $false
                Children = @('.wo048-owner.json')
                Expected = 'FINAL_MARKER_ONLY'
            },
            [pscustomobject]@{
                State = $false; Marker = $false; Lock = $true
                Children = @('.wo048-pki.lock')
                Expected = 'FINAL_LOCK_ONLY'
            },
            [pscustomobject]@{
                State = $false; Marker = $false; Lock = $false
                Children = @()
                Expected = 'FINAL_EMPTY_ROOT'
            })
        foreach ($case in $cases) {
            Get-WO048FinalizationRecoveryPhase `
                -StateExists $case.State `
                -MarkerExists $case.Marker `
                -LockExists $case.Lock `
                -ChildNames $case.Children | Should Be $case.Expected
        }
        foreach ($invalid in @(
                [pscustomobject]@{
                    State = $true; Marker = $false; Lock = $true
                    Children = @('identity.private.json', '.wo048-pki.lock')
                },
                [pscustomobject]@{
                    State = $true; Marker = $true; Lock = $false
                    Children = @('identity.private.json', '.wo048-owner.json')
                },
                [pscustomobject]@{
                    State = $false; Marker = $false; Lock = $true
                    Children = @('.wo048-pki.lock', 'not-owned.sentinel')
                },
                [pscustomobject]@{
                    State = $false; Marker = $true; Lock = $false
                    Children = @()
                })) {
            (Test-WO048ActionThrows {
                Get-WO048FinalizationRecoveryPhase `
                    -StateExists $invalid.State `
                    -MarkerExists $invalid.Marker `
                    -LockExists $invalid.Lock `
                    -ChildNames $invalid.Children
            }) | Should Be $true
        }
    }

    It 'keeps exact durable lock authority through lock-only recovery and refuses non-owned roots' {
        $runRoot = Join-Path $TestDrive '11111111-1111-4111-8111-111111111111'
        New-Item -ItemType Directory -Path $runRoot | Out-Null
        $initialAuthorities = New-WO048SyntheticFinalizationAuthorities `
            -RunRoot $runRoot `
            -Ready $false
        $authorities = New-WO048SyntheticFinalizationAuthorities -RunRoot $runRoot
        Assert-WO048FinalizationLockAuthority `
            -Authority $authorities.Lock `
            -ExpectedWorkOrder $authorities.WorkOrder `
            -ExpectedProvisioningWorkOrder $authorities.ProvisioningWorkOrder `
            -ExpectedRunId $authorities.RunId `
            -ExpectedOwnerSid $authorities.OwnerSid `
            -ExpectedRunRoot $authorities.RunRoot `
            -ExpectedMarkerPath $authorities.MarkerPath `
            -ExpectedLockPath $authorities.LockPath `
            -RequireFinalizationReady
        Assert-WO048FinalizationMarkerAuthority `
            -Marker $authorities.Marker `
            -ExpectedWorkOrder $authorities.WorkOrder `
            -ExpectedProvisioningWorkOrder $authorities.ProvisioningWorkOrder `
            -ExpectedRunId $authorities.RunId `
            -ExpectedOwnerSid $authorities.OwnerSid `
            -ExpectedRunRoot $authorities.RunRoot `
            -ExpectedMarkerPath $authorities.MarkerPath

        $stream = [IO.FileStream]::new(
            $authorities.LockPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None)
        try {
            Write-WO048FinalizationLockAuthorityToStream `
                -Stream $stream `
                -Authority $initialAuthorities.Lock
            (Test-WO048ActionThrows {
                Write-WO048FinalizationLockAuthorityToStream `
                    -Stream $stream `
                    -Authority $authorities.Lock `
                    -QualificationFailurePoint AFTER_PARTIAL_FRAME
            }) | Should Be $true
            $interruptedJournal = Read-WO048FinalizationLockJournalFromStream `
                -Stream $stream
            $interruptedJournal.Authorities.Count | Should Be 1
            $interruptedJournal.Latest.finalizationStatus | Should Be 'NOT_READY'
            $interruptedJournal.HasTrailingFragment | Should Be $true
            Write-WO048FinalizationLockAuthorityToStream `
                -Stream $stream `
                -Authority $authorities.Lock
            $recoveredJournal = Read-WO048FinalizationLockJournalFromStream `
                -Stream $stream
            $recoveredJournal.Authorities.Count | Should Be 2
            $recoveredJournal.Latest.finalizationStatus |
                Should Be 'READY_AFTER_RESIDUAL_PROOF'
            $recoveredJournal.HasTrailingFragment | Should Be $false
        }
        finally {
            $stream.Dispose()
        }
        $readStream = [IO.FileStream]::new(
            $authorities.LockPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::None)
        try {
            $loadedJournal = Read-WO048FinalizationLockJournalFromStream `
                -Stream $readStream
            $loaded = $loadedJournal.Latest
        }
        finally {
            $readStream.Dispose()
        }
        Assert-WO048FinalizationLockAuthority `
            -Authority $loaded `
            -ExpectedWorkOrder $authorities.WorkOrder `
            -ExpectedProvisioningWorkOrder $authorities.ProvisioningWorkOrder `
            -ExpectedRunId $authorities.RunId `
            -ExpectedOwnerSid $authorities.OwnerSid `
            -ExpectedRunRoot $authorities.RunRoot `
            -ExpectedMarkerPath $authorities.MarkerPath `
            -ExpectedLockPath $authorities.LockPath `
            -RequireFinalizationReady

        $sentinelPath = Join-Path $runRoot 'non-owned.sentinel'
        [IO.File]::WriteAllBytes($sentinelPath, [byte[]](9, 2, 6, 5))
        $invalid = $loaded | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $invalid.ownerSid = 'S-1-5-21-999-888-777-1001'
        $destructiveAction = [pscustomobject]@{ Called = $false }
        (Test-WO048ActionThrows {
            Assert-WO048FinalizationLockAuthority `
                -Authority $invalid `
                -ExpectedWorkOrder $authorities.WorkOrder `
                -ExpectedProvisioningWorkOrder $authorities.ProvisioningWorkOrder `
                -ExpectedRunId $authorities.RunId `
                -ExpectedOwnerSid $authorities.OwnerSid `
                -ExpectedRunRoot $authorities.RunRoot `
                -ExpectedMarkerPath $authorities.MarkerPath `
                -ExpectedLockPath $authorities.LockPath `
                -RequireFinalizationReady
            $destructiveAction.Called = $true
            Remove-Item -LiteralPath $sentinelPath -Force
        }) | Should Be $true
        $destructiveAction.Called | Should Be $false
        (Test-Path -LiteralPath $sentinelPath -PathType Leaf) | Should Be $true
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($sentinelPath)) |
            Should Be 'CQIGBQ=='

        $notReady = New-WO048SyntheticFinalizationAuthorities `
            -RunRoot (Join-Path $TestDrive '22222222-2222-4222-8222-222222222222') `
            -RunId '22222222-2222-4222-8222-222222222222' `
            -Ready $false
        (Test-WO048ActionThrows {
            Assert-WO048FinalizationLockAuthority `
                -Authority $notReady.Lock `
                -ExpectedWorkOrder $notReady.WorkOrder `
                -ExpectedProvisioningWorkOrder $notReady.ProvisioningWorkOrder `
                -ExpectedRunId $notReady.RunId `
                -ExpectedOwnerSid $notReady.OwnerSid `
                -ExpectedRunRoot $notReady.RunRoot `
                -ExpectedMarkerPath $notReady.MarkerPath `
                -ExpectedLockPath $notReady.LockPath `
                -RequireFinalizationReady
        }) | Should Be $true
    }

    It 'refuses ambiguous rollback deltas instead of selecting a non-owned run' {
        $one = '11111111-1111-4111-8111-111111111111'
        $other = '22222222-2222-4222-8222-222222222222'
        (Test-WO048ActionThrows {
            Get-WO048ExactAddedCanonicalRunId -Before @() -After @()
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Get-WO048ExactAddedCanonicalRunId -Before @() -After @($one, $other)
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Get-WO048ExactAddedCanonicalRunId -Before @($one) -After @($other)
        }) | Should Be $true
        (Get-WO048ExactAddedCanonicalRunId -Before @() -After @($one)) |
            Should Be $one
    }

    It 'rolls back one exact private run when the later complete snapshot fails' {
        $campaign = Join-Path $TestDrive 'guarded-post-provisioning'
        New-Item -ItemType Directory -Path $campaign | Out-Null
        $existingRunId = '22222222-2222-4222-8222-222222222222'
        $newRunId = '11111111-1111-4111-8111-111111111111'
        $existingRoot = Join-Path $campaign $existingRunId
        $newRoot = Join-Path $campaign $newRunId
        New-Item -ItemType Directory -Path $existingRoot | Out-Null
        $existingSentinel = Join-Path $existingRoot 'unrelated.sentinel'
        [IO.File]::WriteAllBytes($existingSentinel, [byte[]](4, 8, 1, 6))
        $cleanupTargets = [System.Collections.Generic.List[string]]::new()
        $caught = $null
        try {
            Invoke-WO048GuardedPostProvisioningPhase `
                -BeforeRunRoots @($existingRunId) `
                -ProvisioningAction {
                    New-Item -ItemType Directory -Path $newRoot | Out-Null
                    [IO.File]::WriteAllText(
                        (Join-Path $newRoot 'owned.marker'),
                        $newRunId,
                        [Text.UTF8Encoding]::new($false, $true))
                    'synthetic provisioner output'
                } `
                -RunRootSnapshotAction {
                    @(Get-ChildItem -LiteralPath $campaign -Directory -Force |
                        Select-Object -ExpandProperty Name | Sort-Object)
                } `
                -RollbackAuthorityAction {
                    param([string]$RunId)
                    if ($RunId -cne $newRunId -or
                        [IO.File]::ReadAllText((Join-Path $newRoot 'owned.marker')) -cne
                            $newRunId) {
                        throw 'synthetic rollback authority mismatch'
                    }
                } `
                -QualificationAction {
                    throw 'synthetic complete store/CNG/listener snapshot failure'
                } `
                -CleanupAction {
                    param([string]$RunId)
                    $cleanupTargets.Add($RunId)
                    Remove-Item -LiteralPath (Join-Path $newRoot 'owned.marker') -Force
                    Remove-Item -LiteralPath $newRoot -Force
                } `
                -RunRootAbsentAction {
                    param([string]$RunId)
                    return -not (Test-Path -LiteralPath (Join-Path $campaign $RunId))
                } | Out-Null
        }
        catch {
            $caught = $_
        }
        ($null -ne $caught) | Should Be $true
        $caught.Exception.Data['WO048ExactRollback'] | Should Be 'PASS'
        @($cleanupTargets).Count | Should Be 1
        $cleanupTargets[0] | Should Be $newRunId
        (Test-Path -LiteralPath $newRoot) | Should Be $false
        (Test-Path -LiteralPath $existingSentinel -PathType Leaf) | Should Be $true
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($existingSentinel)) |
            Should Be 'BAgBBg=='
    }

    It 'never targets a client identity whose ownership authority was not persisted' {
        $runId = '11111111-1111-4111-8111-111111111111'
        $cleanupState = [pscustomobject]@{ Called = $false }
        $caught = $null
        try {
            Invoke-WO048GuardedPostProvisioningPhase `
                -BeforeRunRoots @() `
                -ProvisioningAction { 'synthetic pre-persistence output' } `
                -RunRootSnapshotAction { @($runId) } `
                -RollbackAuthorityAction {
                    throw 'synthetic ownership not persisted'
                } `
                -QualificationAction { throw 'must not execute' } `
                -CleanupAction { $cleanupState.Called = $true } `
                -RunRootAbsentAction { $false } | Out-Null
        }
        catch {
            $caught = $_
        }
        ($null -ne $caught) | Should Be $true
        $cleanupState.Called | Should Be $false
        $caught.Exception.Message | Should Match 'no unambiguous owned rollback authority'
        $null -eq $caught.Exception.InnerException | Should Be $true
    }

    It 'removes private exception canaries recursively at the public rollback boundary' {
        $runId = '11111111-1111-4111-8111-111111111111'
        $privatePathCanary = 'C:\Users\private-owner\WO046\identity.private.json'
        $fingerprintCanary = 'ABCDEF0123456789ABCDEF0123456789ABCDEF01'
        $caught = $null
        try {
            Invoke-WO048GuardedPostProvisioningPhase `
                -BeforeRunRoots @() `
                -ProvisioningAction { 'synthetic output' } `
                -RunRootSnapshotAction { @($runId) } `
                -RollbackAuthorityAction { } `
                -QualificationAction {
                    $inner = [Exception]::new($fingerprintCanary)
                    $privateFailure = [InvalidOperationException]::new(
                        $privatePathCanary,
                        $inner)
                    $privateFailure.Data['privateFingerprint'] = $fingerprintCanary
                    throw $privateFailure
                } `
                -CleanupAction { } `
                -RunRootAbsentAction { $true } | Out-Null
        }
        catch {
            $caught = $_
        }
        ($null -ne $caught) | Should Be $true
        $caught.Exception.Data['WO048ExactRollback'] | Should Be 'PASS'
        $null -eq $caught.Exception.InnerException | Should Be $true
        $publicExceptionGraph = @(
            $caught.ToString(),
            $caught.Exception.Message,
            $caught.Exception.ToString(),
            (($caught.Exception.Data.Keys | ForEach-Object {
                [string]$_ + '=' + [string]$caught.Exception.Data[$_]
            }) -join "`n")) -join "`n"
        $publicExceptionGraph | Should Not Match ([regex]::Escape($privatePathCanary))
        $publicExceptionGraph | Should Not Match ([regex]::Escape($fingerprintCanary))
    }

    It 'sanitizes the public cleanup exception graph including target object and inner failures' {
        $privatePathCanary =
            'C:\Users\private-owner\WO046\11111111-1111-4111-8111-111111111111\identity.private.json'
        $fingerprintCanary = 'ABCDEF0123456789ABCDEF0123456789ABCDEF01'
        $caught = $null
        try {
            Invoke-WO048SanitizedCleanupFailureBoundary -Action {
                $inner = [InvalidOperationException]::new($privatePathCanary)
                $privateFailure = [InvalidOperationException]::new(
                    $fingerprintCanary,
                    $inner)
                $privateFailure.Data['privatePath'] = $privatePathCanary
                $privateRecord = [Management.Automation.ErrorRecord]::new(
                    $privateFailure,
                    'WO048_PRIVATE_FAILURE',
                    [Management.Automation.ErrorCategory]::InvalidData,
                    [pscustomobject]@{
                        Path = $privatePathCanary
                        Thumbprint = $fingerprintCanary
                    })
                throw $privateRecord
            }
        }
        catch {
            $caught = $_
        }
        ($null -ne $caught) | Should Be $true
        $caught.Exception.Message | Should Be 'WO048_PKI_CLEANUP_FAILED_CLOSED'
        $null -eq $caught.Exception.InnerException | Should Be $true
        $caught.Exception.Data.Count | Should Be 0
        $publicGraph = @(
            $caught.ToString(),
            $caught.Exception.ToString(),
            [string]$caught.TargetObject,
            (($caught.Exception.Data.Keys | ForEach-Object {
                [string]$_ + '=' + [string]$caught.Exception.Data[$_]
            }) -join "`n")) -join "`n"
        $publicGraph | Should Not Match ([regex]::Escape($privatePathCanary))
        $publicGraph | Should Not Match ([regex]::Escape($fingerprintCanary))
        $cleanupSource | Should Match 'WO048_PKI_CLEANUP_FAILED_CLOSED'
        $cleanupSource | Should Match 'throw \$publicFailure'
    }

    It 'rejects UNC roots before access and every existing local reparse prefix' {
        (Test-WO048ActionThrows {
            Assert-WO048LocalFixedPathPrefix `
                -Path '\\127.0.0.1\wo048-must-not-be-contacted\private'
        }) | Should Be $true

        $realRoot = Join-Path $TestDrive 'reparse-real'
        $linkRoot = Join-Path $TestDrive 'reparse-link'
        New-Item -ItemType Directory -Path $realRoot | Out-Null
        New-Item -ItemType Junction -Path $linkRoot -Target $realRoot | Out-Null
        (Test-WO048ActionThrows {
            Assert-WO048LocalFixedPathPrefix -Path (Join-Path $linkRoot 'child')
        }) | Should Be $true
    }

    It 'rejects relative UNC and reparse JAVA_HOME before selecting Java tools' {
        $savedJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
        $realJavaHome = [IO.Path]::GetFullPath($savedJavaHome)
        $javaHomeJunction = Join-Path $TestDrive 'java-home-junction'
        New-Item -ItemType Junction -Path $javaHomeJunction -Target $realJavaHome | Out-Null
        try {
            foreach ($invalidJavaHome in @(
                    'relative\jdk-25',
                    '\\127.0.0.1\wo048-must-not-be-contacted\jdk-25',
                    $javaHomeJunction)) {
                [Environment]::SetEnvironmentVariable(
                    'JAVA_HOME', $invalidJavaHome, 'Process')
                (Test-WO048ActionThrows {
                    Resolve-WO048ExactJava25Toolchain
                }) | Should Be $true
            }
            [Environment]::SetEnvironmentVariable(
                'JAVA_HOME', $realJavaHome, 'Process')
            $resolved = Resolve-WO048ExactJava25Toolchain
            $resolved.JavaHome | Should Be $realJavaHome
            $resolved.Java | Should Be (Join-Path $realJavaHome 'bin\java.exe')
            $resolved.Javac | Should Be (Join-Path $realJavaHome 'bin\javac.exe')
            $resolved.Keytool | Should Be (Join-Path $realJavaHome 'bin\keytool.exe')
        }
        finally {
            [Environment]::SetEnvironmentVariable(
                'JAVA_HOME', $savedJavaHome, 'Process')
        }
    }

    It 'rejects absent corrupt incomplete and unknown-version private states' {
        $absent = Join-Path $TestDrive 'absent.private.json'
        (Test-WO048ActionThrows {
            Read-WO048StrictUtf8JsonObject -Path $absent
        }) | Should Be $true
        $corrupt = Join-Path $TestDrive 'corrupt.private.json'
        [IO.File]::WriteAllText(
            $corrupt,
            '{not-json',
            [Text.UTF8Encoding]::new($false, $true))
        (Test-WO048ActionThrows {
            Read-WO048StrictUtf8JsonObject -Path $corrupt
        }) | Should Be $true

        $incomplete = [pscustomobject]@{ schemaVersion = 2 }
        (Test-WO048ActionThrows {
            Assert-WO048PrivateStateShape `
                -State $incomplete `
                -ExpectedWorkOrder 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign' `
                -ExpectedProvisioningWorkOrder `
                    'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning' `
                -ExpectedRunId '11111111-1111-4111-8111-111111111111' `
                -ExpectedOwnerSid 'S-1-5-21-111-222-333-1001'
        }) | Should Be $true
        $unknown = New-WO048SyntheticPrivateState -SchemaVersion 99
        (Test-WO048ActionThrows {
            Assert-WO048PrivateStateShape `
                -State $unknown `
                -ExpectedWorkOrder 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign' `
                -ExpectedProvisioningWorkOrder `
                    'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning' `
                -ExpectedRunId '11111111-1111-4111-8111-111111111111' `
                -ExpectedOwnerSid 'S-1-5-21-111-222-333-1001'
        }) | Should Be $true
    }

    It 'keeps cleanup authority valid when historical Java and J6 pins are unavailable' {
        $runId = '11111111-1111-4111-8111-111111111111'
        $runRoot = Join-Path $TestDrive $runId
        $markerPath = Join-Path $runRoot '.wo048-owner.json'
        $lockPath = Join-Path $runRoot '.wo048-pki.lock'
        $state = New-WO048SyntheticPrivateState -RunId $runId
        foreach ($entry in ([ordered]@{
                runRoot = $runRoot
                markerPath = $markerPath
                lockPath = $lockPath
                modulePath = 'Z:\historical\missing\WO048-PkiTools.psm1'
                moduleSha256 = ('1' * 64)
                nativeRunnerModulePath = 'Z:\historical\missing\J6.psm1'
                nativeRunnerModuleSha256 = ('2' * 64)
                nativeProcessHostPath = 'Z:\historical\missing\J6Host.ps1'
                nativeProcessHostSha256 = ('3' * 64)
                nativePowerShellPath = 'Z:\historical\missing\pwsh.exe'
                nativePowerShellSha256 = ('4' * 64)
                javaHome = 'Z:\historical\missing\jdk-25'
                javaPath = 'Z:\historical\missing\jdk-25\bin\java.exe'
                javaSha256 = ('5' * 64)
                javacPath = 'Z:\historical\missing\jdk-25\bin\javac.exe'
                javacSha256 = ('6' * 64)
                keytoolPath = 'Z:\historical\missing\jdk-25\bin\keytool.exe'
                keytoolSha256 = ('7' * 64)
            }).GetEnumerator()) {
            Add-Member -InputObject $state -NotePropertyName $entry.Key `
                -NotePropertyValue $entry.Value
        }
        (Test-WO048ActionThrows {
            Assert-WO048CleanupRecoveryAuthorityShape `
                -State $state `
                -ExpectedWorkOrder 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign' `
                -ExpectedProvisioningWorkOrder `
                    'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning' `
                -ExpectedRunId $runId `
                -ExpectedOwnerSid 'S-1-5-21-111-222-333-1001' `
                -ExpectedRunRoot $runRoot `
                -ExpectedMarkerPath $markerPath `
                -ExpectedLockPath $lockPath
        }) | Should Be $false
        (Test-WO048ActionThrows {
            Assert-WO048CleanupRecoveryAuthorityShape `
                -State $state `
                -ExpectedWorkOrder 'WO-SS-20260904-046-j9-j7-real-local-e2e-campaign' `
                -ExpectedProvisioningWorkOrder `
                    'WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning' `
                -ExpectedRunId $runId `
                -ExpectedOwnerSid 'S-1-5-21-111-222-333-1001' `
                -ExpectedRunRoot (Join-Path $TestDrive 'different-run-root') `
                -ExpectedMarkerPath $markerPath `
                -ExpectedLockPath $lockPath
        }) | Should Be $true
        $cleanupSource | Should Not Match `
            'state\.(?:moduleSha256|nativeRunnerModuleSha256|nativeProcessHostSha256|nativePowerShellSha256|javaSha256|javacSha256|keytoolSha256)'
    }

    It 'rejects divergent owners ACL entries and inheritance' {
        $owner = 'S-1-5-21-111-222-333-1001'
        $validRules = @([pscustomobject]@{
            IdentitySid = $owner
            AccessControlType = 'Allow'
            FileSystemRights = [int][Security.AccessControl.FileSystemRights]::FullControl
            InheritanceFlags = [int][Security.AccessControl.InheritanceFlags]::None
            PropagationFlags = [int][Security.AccessControl.PropagationFlags]::None
            IsInherited = $false
        })
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid 'S-1-5-21-999-888-777-1001' `
                -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $true `
                -Rules $validRules -PathKind File -RequireProtected
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $false `
                -Rules $validRules -PathKind File -RequireProtected
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $true `
                -Rules @([pscustomobject]@{
                    IdentitySid = 'S-1-1-0'
                    AccessControlType = 'Allow'
                    FileSystemRights = [int][Security.AccessControl.FileSystemRights]::FullControl
                    InheritanceFlags = [int][Security.AccessControl.InheritanceFlags]::None
                    PropagationFlags = [int][Security.AccessControl.PropagationFlags]::None
                    IsInherited = $false
                }) -PathKind File -RequireProtected
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $true `
                -Rules @() -PathKind File -RequireProtected
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $true `
                -Rules @($validRules + $validRules) -PathKind File -RequireProtected
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $true `
                -Rules $validRules -PathKind File -RequireProtected
        }) | Should Be $false
        $directoryRule = [pscustomobject]@{
            IdentitySid = $owner
            AccessControlType = 'Allow'
            FileSystemRights = [int][Security.AccessControl.FileSystemRights]::FullControl
            InheritanceFlags = [int](
                [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
                [Security.AccessControl.InheritanceFlags]::ObjectInherit)
            PropagationFlags = [int][Security.AccessControl.PropagationFlags]::None
            IsInherited = $false
        }
        (Test-WO048ActionThrows {
            Assert-WO048PrivateAclContract `
                -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                -AreAccessRulesProtected $true `
                -Rules @($directoryRule) -PathKind Directory -RequireProtected
        }) | Should Be $false
        foreach ($property in @(
                'FileSystemRights', 'InheritanceFlags', 'PropagationFlags', 'IsInherited')) {
            $invalid = $validRules[0].PSObject.Copy()
            if ($property -eq 'FileSystemRights') {
                $invalid.$property = [int][Security.AccessControl.FileSystemRights]::Read
            }
            elseif ($property -eq 'InheritanceFlags') {
                $invalid.$property = [int][Security.AccessControl.InheritanceFlags]::ObjectInherit
            }
            elseif ($property -eq 'PropagationFlags') {
                $invalid.$property = [int][Security.AccessControl.PropagationFlags]::InheritOnly
            }
            else {
                $invalid.$property = $true
            }
            (Test-WO048ActionThrows {
                Assert-WO048PrivateAclContract `
                    -ActualOwnerSid $owner -ExpectedOwnerSid $owner `
                    -AreAccessRulesProtected $true -Rules @($invalid) `
                    -PathKind File -RequireProtected
            }) | Should Be $true
        }
    }

    It 'rejects PKCS12 cardinality and malformed or divergent fingerprints' {
        $certificate = New-WO048SyntheticCertificate -Role Server
        try {
            $sha256 = Get-WO048CertificateSha256 -Certificate $certificate
            $none = [Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
            (Test-WO048ActionThrows {
                Assert-WO048ExactCertificateCollection `
                    -Certificates $none -ExpectedSha256 $sha256 `
                    -ExpectedPrivateKey $true
            }) | Should Be $true
            $one = [Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
            [void]$one.Add($certificate)
            (Test-WO048ActionThrows {
                Assert-WO048ExactCertificateCollection `
                    -Certificates $one -ExpectedSha256 'not-a-sha256' `
                    -ExpectedPrivateKey $true
            }) | Should Be $true
            (Test-WO048ActionThrows {
                Assert-WO048ExactCertificateCollection `
                    -Certificates $one -ExpectedSha256 ('0' * 64) `
                    -ExpectedPrivateKey $true
            }) | Should Be $true
            (Test-WO048ActionThrows {
                Assert-WO048ExactCertificateCollection `
                    -Certificates $one -ExpectedSha256 $sha256 `
                    -ExpectedPrivateKey $true
            }) | Should Be $false
            $many = [Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
            [void]$many.Add($certificate)
            [void]$many.Add($certificate)
            (Test-WO048ActionThrows {
                Assert-WO048ExactCertificateCollection `
                    -Certificates $many -ExpectedSha256 $sha256 `
                    -ExpectedPrivateKey $true
            }) | Should Be $true
        }
        finally {
            $certificate.Dispose()
        }

        $duplicate = New-WO048SyntheticRecordSet `
            -ClientThumbprint ('A' * 40) -ClientSha256 ('a' * 64)
        (Test-WO048ActionThrows {
            Assert-WO048CertificateRecordSetShape `
                -Records $duplicate `
                -CanonicalRunId '11111111-1111-4111-8111-111111111111' `
                -RequireOwned
        }) | Should Be $true
    }

    It 'rejects case-divergent certificate recovery roles properties and statuses' {
        $runId = '11111111-1111-4111-8111-111111111111'

        $uppercaseRole = @(New-WO048SyntheticRecordSet -RunId $runId)
        $uppercaseRole[0].role = 'RECEIVER-SERVER-DIRECT-TRUST'
        (Test-WO048ActionThrows {
            Assert-WO048CertificateRecordSetShape `
                -Records $uppercaseRole -CanonicalRunId $runId -RequireOwned
        }) | Should Be $true

        $uppercaseStatus = @(New-WO048SyntheticRecordSet -RunId $runId)
        $uppercaseStatus[1].ownershipStatus = 'owned'
        (Test-WO048ActionThrows {
            Assert-WO048CertificateRecordSetShape `
                -Records $uppercaseStatus -CanonicalRunId $runId -RequireOwned
        }) | Should Be $true

        $valid = @(New-WO048SyntheticRecordSet -RunId $runId)
        $caseDivergentProperty = [pscustomobject][ordered]@{
            Role = 'sender-client'
            ownershipStatus = 'OWNED'
            storeLocation = 'CurrentUser\My'
            thumbprint = ('B' * 40)
            sha256 = ('b' * 64)
            subject = "CN=WO046 sender $runId, OU=WO-046, O=Betting Project Local Qualification"
            cngKey = [pscustomobject]@{ exact = $true }
        }
        (Test-WO048ActionThrows {
            Assert-WO048CertificateRecordSetShape `
                -Records @($valid[0], $caseDivergentProperty) `
                -CanonicalRunId $runId -RequireOwned
        }) | Should Be $true
    }

    It 'rejects missing duplicated unexpected and mismatched provisioning output' {
        $runId = '11111111-1111-4111-8111-111111111111'
        $hash = 'a' * 64
        $valid = @(
            'WO048_PKI_ONLY_PROVISIONING=PASS',
            "WO048_RUN_ID=$runId",
            "WO048_PRIVATE_IDENTITY_RECORD_SHA256=$hash",
            'WO048_CLIENT_IDENTITY_SELECTED=YES',
            'WO048_RECEIVER_IDENTITY_SELECTED=YES',
            'WO048_CLIENT_PRIVATE_KEY_EXPORTABLE=NO',
            'WO048_JAVA_25_PROVENANCE=PASS',
            'WO048_JAVA_SUNMSCAPI_STORE_QUALIFICATION=PASS',
            'WO048_PRIVATE_VALUES_DISPLAYED=NO',
            'WO048_DATABASES_STARTED=NO',
            'WO048_APPLICATIONS_STARTED=NO',
            'WO048_TLS_HANDSHAKES=0',
            'WO048_SOCKETS_OPENED=0',
            'WO048_PROVIDER_CALLS=0',
            'WO048_REMOTE_NETWORK_CALLS=0')
        (Assert-WO048ProvisioningPublicOutput -Lines $valid -ExpectedRunId $runId) |
            Should Be $hash
        (Test-WO048ActionThrows {
            Assert-WO048ProvisioningPublicOutput `
                -Lines @($valid | Select-Object -Skip 1) -ExpectedRunId $runId
        }) | Should Be $true
        (Test-WO048ActionThrows {
            Assert-WO048ProvisioningPublicOutput `
                -Lines @($valid + $valid[0]) -ExpectedRunId $runId
        }) | Should Be $true
        $unexpected = @($valid)
        $unexpected[3] = 'WO048_UNEXPECTED=YES'
        (Test-WO048ActionThrows {
            Assert-WO048ProvisioningPublicOutput `
                -Lines $unexpected -ExpectedRunId $runId
        }) | Should Be $true
        $wrongRun = @($valid)
        $wrongRun[1] = 'WO048_RUN_ID=22222222-2222-4222-8222-222222222222'
        (Test-WO048ActionThrows {
            Assert-WO048ProvisioningPublicOutput `
                -Lines $wrongRun -ExpectedRunId $runId
        }) | Should Be $true
    }
}

Describe 'WO-048 Java 25 Windows-store proof' {
    It 'uses SunMSCAPI stores and proves one exact non-encodable client key without TLS' {
        $javaProbeSource | Should Match 'KeyStore\.getInstance\("Windows-MY", PROVIDER\)'
        $javaProbeSource | Should Match 'KeyStore\.getInstance\("Windows-ROOT", PROVIDER\)'
        $javaProbeSource | Should Match 'Security\.getProvider\(PROVIDER\)'
        $javaProbeSource | Should Match 'clientAliases\.size\(\) == 1'
        $javaProbeSource | Should Match 'serverAliases\.size\(\) == 1'
        $javaProbeSource | Should Match 'key\.getEncoded\(\) == null'
        $javaProbeSource | Should Match 'List\.of\(CLIENT_AUTH\)'
        $javaProbeSource | Should Match 'List\.of\(SERVER_AUTH\)'
        $javaProbeSource | Should Not Match 'SSLContext|SSLSocket|SocketChannel|java\.net\.Socket'
        $initializeSource | Should Match 'Assert-WO048JavaWindowsStoreQualification'
        $initializeSource | Should Match 'Invoke-WO048BoundedJavaTool'
        $initializeSource.IndexOf('Assert-WO048JavaWindowsStoreQualification') |
            Should BeLessThan $initializeSource.IndexOf("provisioningStatus = 'PASS'",
                $initializeSource.IndexOf('Assert-WO048JavaWindowsStoreQualification'))
    }

    It 'keeps expected fingerprints in a protected private input and emits only safe markers' {
        $initializeSource | Should Match 'java-store-probe-input\.private\.properties'
        $initializeSource | Should Match 'Protect-WO048PrivateFile -Path \$ProbeInputPath'
        $javaProbeSource | Should Not Match 'println\([^\r\n]*(?:clientSha256|serverSha256|expectedSha256)'
        $javaProbeSource | Should Match 'WO048_JAVA_STORE_PROBE=PASS'
        $qualificationSource | Should Match 'WO048_JAVA_SUNMSCAPI_STORE_QUALIFICATION=PASS_NO_HANDSHAKE'
    }
}

Describe 'WO-048 executable offline helpers' {
    It 'computes deterministic lowercase SHA-256 without touching a certificate store' {
        Get-WO048Sha256Text -Value 'abc' | Should Be `
            'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
    }

    It 'writes and replaces one owner-only UTF-8 private JSON record atomically' {
        $ownerSid = Get-WO048CurrentOwnerSid
        $privateRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('D'))
        New-Item -ItemType Directory -Path $privateRoot | Out-Null
        Protect-WO048PrivateDirectory -Path $privateRoot -OwnerSid $ownerSid
        $path = Join-Path $privateRoot 'identity.private.json'
        Write-WO048PrivateJsonAtomic `
            -Path $path `
            -Value ([pscustomobject]@{ schemaVersion = 1; state = 'FIRST' }) `
            -OwnerSid $ownerSid
        Write-WO048PrivateJsonAtomic `
            -Path $path `
            -Value ([pscustomobject]@{ schemaVersion = 1; state = 'SECOND' }) `
            -OwnerSid $ownerSid
        $bytes = [System.IO.File]::ReadAllBytes($path)
        ($bytes.Length -gt 3) | Should Be $true
        (($bytes[0] -eq 0xEF) -and ($bytes[1] -eq 0xBB) -and ($bytes[2] -eq 0xBF)) |
            Should Be $false
        (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json).state | Should Be 'SECOND'
        Assert-WO048PrivateAcl -Path $path -OwnerSid $ownerSid.Value -RequireProtected
        (Test-Path -LiteralPath "$path.partial") | Should Be $false
    }

    It 'persists exact certificate recovery authority before a synthetic CNG inspection failure' {
        $ownerSid = Get-WO048CurrentOwnerSid
        $privateRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('D'))
        New-Item -ItemType Directory -Path $privateRoot | Out-Null
        Protect-WO048PrivateDirectory -Path $privateRoot -OwnerSid $ownerSid
        $path = Join-Path $privateRoot 'identity.private.json'
        $state = [pscustomobject]@{
            ownedCertificates = @(
                [pscustomobject]@{
                    role = 'receiver-server-direct-trust'
                    ownershipStatus = 'NOT_CREATED'
                    storeLocation = 'CurrentUser\Root'
                    thumbprint = $null
                    sha256 = $null
                    subject = 'CN=127.0.0.1, OU=WO-046, O=Betting Project Local Qualification'
                    cngKey = $null
                },
                [pscustomobject]@{
                    role = 'sender-client'
                    ownershipStatus = 'NOT_CREATED'
                    storeLocation = 'CurrentUser\My'
                    thumbprint = $null
                    sha256 = $null
                    subject = 'CN=WO046 sender 11111111-1111-4111-8111-111111111111, OU=WO-046, O=Betting Project Local Qualification'
                    cngKey = $null
                })
        }
        $certificate = New-WO048SyntheticCertificate -Role Client
        try {
            Set-WO048ClientCertificateDeclaration `
                -State $state `
                -Certificate $certificate `
                -ExpectedSubject $certificate.Subject | Out-Null
            Write-WO048PrivateJsonAtomic `
                -Path $path `
                -Value $state `
                -OwnerSid $ownerSid
            try {
                throw 'synthetic CNG inspection failure'
            }
            catch {
                $_.Exception.Message | Should Be 'synthetic CNG inspection failure'
            }
            $persisted = Get-Content -LiteralPath $path -Raw -Encoding UTF8 |
                ConvertFrom-Json
            $client = @($persisted.ownedCertificates | Where-Object {
                $_.role -ceq 'sender-client'
            })
            $client.Count | Should Be 1
            $client[0].ownershipStatus | Should Be 'CERTIFICATE_DECLARED'
            $client[0].thumbprint | Should Match '^[0-9A-F]{40}$'
            $client[0].sha256 | Should Be (Get-WO048CertificateSha256 -Certificate $certificate)
            $client[0].subject | Should Be $certificate.Subject
            $null -eq $client[0].cngKey | Should Be $true
        }
        finally {
            $certificate.Dispose()
        }
    }

    It 'rejects client identities with SAN CA missing EKU wrong KU or no private key' {
        $subject =
            'CN=WO046 sender 11111111-1111-4111-8111-111111111111, OU=WO-046, O=Betting Project Local Qualification'
        $valid = New-WO048SyntheticCertificate -Role Client
        $invalidSan = New-WO048SyntheticCertificate -Role Client -InvalidSan
        $invalidCa = New-WO048SyntheticCertificate -Role Client -InvalidCa
        $missingEku = New-WO048SyntheticCertificate -Role Client -OmitEku
        $missingKeyUsage = New-WO048SyntheticCertificate -Role Client -OmitKeyUsage
        $wrongKeyUsage = New-WO048SyntheticCertificate -Role Client -WrongKeyUsage
        $publicOnly = [Security.Cryptography.X509Certificates.X509Certificate2]::new(
            $valid.Export(
                [Security.Cryptography.X509Certificates.X509ContentType]::Cert))
        try {
            Assert-WO048ClientCertificateProfile `
                -Certificate $valid `
                -ExpectedSubject $subject `
                -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
                -RequirePrivateKey
            foreach ($invalid in @(
                    $invalidSan, $invalidCa, $missingEku,
                    $missingKeyUsage, $wrongKeyUsage)) {
                (Test-WO048ActionThrows {
                    Assert-WO048ClientCertificateProfile `
                        -Certificate $invalid `
                        -ExpectedSubject $subject `
                        -ObservedAtUtc ([DateTimeOffset]::UtcNow)
                }) | Should Be $true
            }
            (Test-WO048ActionThrows {
                Assert-WO048ClientCertificateProfile `
                    -Certificate $publicOnly `
                    -ExpectedSubject $subject `
                    -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
                    -RequirePrivateKey
            }) | Should Be $true
        }
        finally {
            $valid.Dispose()
            $invalidSan.Dispose()
            $invalidCa.Dispose()
            $missingEku.Dispose()
            $missingKeyUsage.Dispose()
            $wrongKeyUsage.Dispose()
            $publicOnly.Dispose()
        }
    }

    It 'rejects exportable or otherwise divergent client private-key metadata' {
        $valid = [ordered]@{
            KeyImplementation = 'System.Security.Cryptography.RSACng'
            ProviderName = 'Microsoft Software Key Storage Provider'
            AlgorithmGroup = 'RSA'
            KeySize = 3072
            ExportPolicy = [int][Security.Cryptography.CngExportPolicies]::None
            KeyUsage = [int][Security.Cryptography.CngKeyUsages]::Signing
            IsEphemeral = $false
        }
        (Test-WO048ActionThrows {
            Assert-WO048ClientPrivateKeyMetadata @valid
        }) | Should Be $false
        foreach ($case in @(
                @{ Name = 'ExportPolicy'; Value = [int][Security.Cryptography.CngExportPolicies]::AllowExport },
                @{ Name = 'KeyUsage'; Value = [int][Security.Cryptography.CngKeyUsages]::Decryption },
                @{ Name = 'KeySize'; Value = 2048 },
                @{ Name = 'IsEphemeral'; Value = $true },
                @{ Name = 'ProviderName'; Value = 'Unexpected Provider' })) {
            $invalid = [ordered]@{}
            foreach ($entry in $valid.GetEnumerator()) {
                $invalid[$entry.Key] = $entry.Value
            }
            $invalid[$case.Name] = $case.Value
            (Test-WO048ActionThrows {
                Assert-WO048ClientPrivateKeyMetadata @invalid
            }) | Should Be $true
        }
    }

    It 'accepts exact loopback server SAN and rejects a different loopback-range IP' {
        $valid = New-WO048SyntheticCertificate -Role Server
        $invalidSan = New-WO048SyntheticCertificate -Role Server -InvalidSan
        $extraSan = New-WO048SyntheticCertificate -Role Server -ExtraSan
        try {
            Assert-WO048ServerCertificateProfile `
                -Certificate $valid `
                -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
                -AllowPrivateKey
            $invalidSanRejected = $false
            try {
                Assert-WO048ServerCertificateProfile `
                    -Certificate $invalidSan `
                    -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
                    -AllowPrivateKey
            }
            catch {
                $invalidSanRejected = $true
            }
            $invalidSanRejected | Should Be $true
            $extraSanRejected = $false
            try {
                Assert-WO048ServerCertificateProfile `
                    -Certificate $extraSan `
                    -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
                    -AllowPrivateKey
            }
            catch {
                $extraSanRejected = $true
            }
            $extraSanRejected | Should Be $true
        }
        finally {
            $valid.Dispose()
            $invalidSan.Dispose()
            $extraSan.Dispose()
        }
    }

    It 'enforces an exact seven-day maximum and raw SAN equality' {
        $moduleSource | Should Match '\(\$notAfter - \$notBefore\) -gt \[TimeSpan\]::FromDays\(7\)'
        $moduleSource | Should Not Match '\[TimeSpan\]::FromDays\(8\)'
        $moduleSource | Should Match 'expectedSanBuilder\.AddIpAddress\(\[System\.Net\.IPAddress\]::Loopback\)'
        $moduleSource | Should Match 'ToBase64String\(\$parsedSan\.RawData\)'
        $moduleSource | Should Match 'ToBase64String\(\$expectedSan\.RawData\)'
        $moduleSource | Should Match '\$notBefore -gt \$ObservedAtUtc'
        $moduleSource | Should Not Match 'ObservedAtUtc\.AddMinutes'
        $initializeSource | Should Match '\$clientValidityAnchorUtc = \[DateTimeOffset\]::UtcNow'
        $initializeSource | Should Match '\$clientNotBeforeUtc = \$clientValidityAnchorUtc\.AddMinutes'
        $initializeSource | Should Match '\$clientNotAfterUtc = \$clientValidityAnchorUtc\.AddDays'
    }

    It 'rejects a certificate that is not valid at the exact inspection instant' {
        $future = New-WO048SyntheticCertificate -Role Client -FutureNotBefore
        try {
            $rejected = $false
            try {
                Assert-WO048ClientCertificateProfile `
                    -Certificate $future `
                    -ExpectedSubject $future.Subject `
                    -ObservedAtUtc ([DateTimeOffset]::UtcNow) `
                    -RequirePrivateKey
            }
            catch {
                $rejected = $true
            }
            $rejected | Should Be $true
        }
        finally {
            $future.Dispose()
        }
    }

    It 'proves native timeout cleanup offline without touching certificate stores' {
        Import-Module -Name $nativeRunnerModulePath -Force
        $caught = $null
        try {
            Invoke-J6BoundedNativeCommand `
                -FilePath ([System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName) `
                -ArgumentList @(
                    '-NoLogo',
                    '-NoProfile',
                    '-NonInteractive',
                    '-Command',
                    'Start-Sleep -Seconds 5') `
                -WorkingDirectory $repositoryRoot `
                -TimeoutMilliseconds 250 `
                -StartupTimeoutMilliseconds 5000 `
                -CleanupTimeoutMilliseconds 5000 | Out-Null
        }
        catch {
            $caught = $_
        }
        finally {
            Remove-Module -Name 'J6-NativeBinaryPipeline' -Force -ErrorAction SilentlyContinue
        }
        ($null -ne $caught) | Should Be $true
        ($caught.Exception -is [TimeoutException]) | Should Be $true
        $caught.Exception.Data['J6ProcessTreeCleanup'] | Should Be 'PASS'
        $caught.Exception.Data['J6Confinement'] | Should Be 'WINDOWS_KILL_ON_JOB_CLOSE'
        [int]$caught.Exception.Data['J6ActiveProcessesAfterCleanup'] | Should Be 0
        ([int]$caught.Exception.Data['J6TargetProcessId'] -gt 0) | Should Be $true
        ([long]$caught.Exception.Data['J6TargetStartedAtUtcTicks'] -gt 0) | Should Be $true
    }

    It 'executes the real first Java probe with null state and restores injected Java options' {
        $functionSource = Get-WO048FunctionDefinitionSource `
            -Path $initializePath `
            -Names @(
                'Invoke-WO048WithSanitizedJavaEnvironment',
                'Invoke-WO048BoundedJavaTool',
                'Assert-WO048Java25RuntimeProvenance')
        $javaHome = [IO.Path]::GetFullPath(
            [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process'))
        $config = [pscustomobject]@{
            ModulePath = $modulePath
            NativeRunnerModulePath = $nativeRunnerModulePath
            NativeProcessHostPath = Join-Path $repositoryRoot `
                'scripts\J6-NativeProcessHost.ps1'
            JavaStoreProbePath = $javaProbePath
            BoundedJavaCapturePath = $boundedCapturePath
            NativeWorkingDirectory = Join-Path $TestDrive `
                ('first-java-probe-' + [guid]::NewGuid().ToString('D'))
            JavaHome = $javaHome
            JavaPath = Join-Path $javaHome 'bin\java.exe'
            JavacPath = Join-Path $javaHome 'bin\javac.exe'
            PowerShellPath = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
            OwnerSid = (Get-WO048CurrentOwnerSid).Value
        }
        New-Item -ItemType Directory -Path $config.NativeWorkingDirectory | Out-Null
        Protect-WO048PrivateDirectory `
            -Path $config.NativeWorkingDirectory -OwnerSid $config.OwnerSid
        $injected = [ordered]@{
            JAVA_TOOL_OPTIONS = '-Dwo048.injected=true'
            _JAVA_OPTIONS = '-Dwo048.injected=true'
            JDK_JAVA_OPTIONS = '--wo048-invalid-if-inherited'
            JDK_JAVAC_OPTIONS = '--wo048-invalid-if-inherited'
            HTTPS_PROXY = 'http://127.0.0.1:1'
        }
        $saved = [ordered]@{}
        foreach ($entry in $injected.GetEnumerator()) {
            $saved[$entry.Key] = [Environment]::GetEnvironmentVariable(
                $entry.Key, 'Process')
        }
        $moduleName = 'WO048FirstJavaProbe' + [guid]::NewGuid().ToString('N')
        try {
            foreach ($entry in $injected.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable(
                    $entry.Key, $entry.Value, 'Process')
            }
            $probeModule = New-Module -Name $moduleName -AsCustomObject `
                -ArgumentList @($config, $functionSource) -ScriptBlock {
                    param($Config, $FunctionSource)

                    Import-Module -Name $Config.ModulePath -Force -ErrorAction Stop
                    Import-Module -Name $Config.NativeRunnerModulePath -Force `
                        -ErrorAction Stop
                    . ([scriptblock]::Create($FunctionSource))

                    $nativeRunnerModulePath = $Config.NativeRunnerModulePath
                    $nativeProcessHostPath = $Config.NativeProcessHostPath
                    $javaStoreProbePath = $Config.JavaStoreProbePath
                    $boundedJavaCapturePath = $Config.BoundedJavaCapturePath
                    $script:nativeWorkingDirectory = $Config.NativeWorkingDirectory
                    $script:javaHome = $Config.JavaHome
                    $script:java = $Config.JavaPath
                    $script:javac = $Config.JavacPath
                    $script:pwshPath = $Config.PowerShellPath
                    $script:ownerSid = $Config.OwnerSid
                    $script:state = $null
                    $script:javaSha256 = Get-WO048FileSha256 -Path $script:java
                    $script:javacSha256 = Get-WO048FileSha256 -Path $script:javac
                    $script:pwshSha256 = Get-WO048FileSha256 -Path $script:pwshPath
                    $script:nativeRunnerModuleSha256 = Get-WO048FileSha256 `
                        -Path $nativeRunnerModulePath
                    $script:nativeProcessHostSha256 = Get-WO048FileSha256 `
                        -Path $nativeProcessHostPath
                    $script:boundedJavaCaptureSha256 = Get-WO048FileSha256 `
                        -Path $boundedJavaCapturePath

                    function Invoke-WO048ActualFirstJavaProbe {
                        Assert-WO048Java25RuntimeProvenance
                        return [pscustomobject]@{
                            StateStillNull = ($null -eq $script:state)
                            CaptureResidualCount = @(Get-ChildItem `
                                -LiteralPath $script:nativeWorkingDirectory `
                                -Force).Count
                        }
                    }
                    Export-ModuleMember -Function Invoke-WO048ActualFirstJavaProbe
                }
            $result = $probeModule.'Invoke-WO048ActualFirstJavaProbe'()
            $result.StateStillNull | Should Be $true
            $result.CaptureResidualCount | Should Be 0
            foreach ($entry in $injected.GetEnumerator()) {
                [Environment]::GetEnvironmentVariable($entry.Key, 'Process') |
                    Should Be $entry.Value
            }
        }
        finally {
            Remove-Module -Name $moduleName -Force -ErrorAction SilentlyContinue
            foreach ($entry in $saved.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable(
                    $entry.Key, $entry.Value, 'Process')
            }
        }
    }


    It 'bounds Java stdout and fails closed on a synthetic output flood' {
        $javaHome = [IO.Path]::GetFullPath(
            [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process'))
        $java = Join-Path $javaHome 'bin\java.exe'
        $javaSha256 = (Get-FileHash -LiteralPath $java -Algorithm SHA256).Hash.ToLowerInvariant()
        $captureRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('D'))
        New-Item -ItemType Directory -Path $captureRoot | Out-Null
        $stdoutPath = Join-Path $captureRoot 'native-java.stdout.private.txt'
        $stderrPath = Join-Path $captureRoot 'native-java.stderr.private.txt'
        $runtimeArgumentJson = ConvertTo-Json -InputObject ([string[]]@(
                '-Djava.net.useSystemProxies=false',
                $javaProbePath,
                '--runtime-only')) -Compress
        $runtimePayload = [Convert]::ToBase64String(
            [Text.UTF8Encoding]::new($false, $true).GetBytes($runtimeArgumentJson))
        $argumentJson = ConvertTo-Json -InputObject ([string[]]@(
                '-Djava.net.useSystemProxies=false',
                $javaProbePath,
                '--qualification-output-flood')) -Compress
        $payload = [Convert]::ToBase64String(
            [Text.UTF8Encoding]::new($false, $true).GetBytes($argumentJson))
        $neutralNames = @(
            'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
            'JDK_JAVAC_OPTIONS', 'JAVA_OPTS',
            'CLASSPATH', 'MAVEN_OPTS', 'GRADLE_OPTS',
            'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'NO_PROXY', 'FTP_PROXY',
            'SOCKS_PROXY', 'GIT_HTTP_PROXY', 'GIT_HTTPS_PROXY',
            'SSLKEYLOGFILE', 'NSS_SSLKEYLOGFILE', 'JDK_TLS_KEYLOGGER',
            'JAVAX_NET_DEBUG', 'JDK_HTTPCLIENT_HTTPCLIENT_LOG')
        $saved = [ordered]@{
            JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
            WO048_EXPECTED_JAVA_HOME = [Environment]::GetEnvironmentVariable(
                'WO048_EXPECTED_JAVA_HOME', 'Process')
        }
        foreach ($name in $neutralNames) {
            $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        try {
            [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'Process')
            [Environment]::SetEnvironmentVariable(
                'WO048_EXPECTED_JAVA_HOME', $javaHome, 'Process')
            foreach ($name in $neutralNames) {
                [Environment]::SetEnvironmentVariable($name, $null, 'Process')
            }
            $runtimeCaptureOutput = @(& $boundedCapturePath `
                -ToolPath $java `
                -ExpectedToolSha256 $javaSha256 `
                -ExpectedJavaHome $javaHome `
                -WorkingDirectory $captureRoot `
                -ArgumentPayloadBase64 $runtimePayload `
                -StandardOutputPath $stdoutPath `
                -StandardErrorPath $stderrPath `
                -MaximumCharactersPerStream 4096 `
                -ChildTimeoutMilliseconds 10000 `
                -ChildCleanupTimeoutMilliseconds 5000)
            $runtimeCaptureExitCode = $LASTEXITCODE
            $runtimeOutput = Get-Content -LiteralPath $stdoutPath -Raw -Encoding UTF8
            $runtimeError = Get-Content -LiteralPath $stderrPath -Raw -Encoding UTF8
            Remove-Item -LiteralPath $stdoutPath -Force
            Remove-Item -LiteralPath $stderrPath -Force
            $captureOutput = @(& $boundedCapturePath `
                -ToolPath $java `
                -ExpectedToolSha256 $javaSha256 `
                -ExpectedJavaHome $javaHome `
                -WorkingDirectory $captureRoot `
                -ArgumentPayloadBase64 $payload `
                -StandardOutputPath $stdoutPath `
                -StandardErrorPath $stderrPath `
                -MaximumCharactersPerStream 4096 `
                -ChildTimeoutMilliseconds 10000 `
                -ChildCleanupTimeoutMilliseconds 5000)
            $captureExitCode = $LASTEXITCODE
        }
        finally {
            foreach ($entry in $saved.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
            }
        }
        $runtimeCaptureExitCode | Should Be 0
        $runtimeCaptureOutput[0] | Should Be 'WO048_BOUNDED_JAVA_CAPTURE=PASS'
        $runtimeCaptureOutput[-1] | Should Be 'WO048_CHILD_CLEANUP=PASS'
        $runtimeOutput | Should Match 'WO048_JAVA_RUNTIME_PROVENANCE=PASS_JAVA_25'
        $runtimeError | Should BeNullOrEmpty
        $captureExitCode | Should Be 90
        $captureOutput | Should Be @(
            'WO048_BOUNDED_JAVA_CAPTURE=FAIL',
            'WO048_BOUNDED_JAVA_CAPTURE_FAILURE=OUTPUT_LIMIT')
        (Test-Path -LiteralPath $stdoutPath) | Should Be $false
        (Test-Path -LiteralPath $stderrPath) | Should Be $false
    }
}

Remove-Module -Name 'WO048-PkiTools' -Force -ErrorAction SilentlyContinue
