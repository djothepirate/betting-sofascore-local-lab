$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $repositoryRoot 'scripts/wo046/WO046-LaunchPreparation.psm1'
$fixturePath = Join-Path $repositoryRoot 'scripts/wo049/Export-WO049SyntheticLaunchPreparation.ps1'
Import-Module $modulePath -Force
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetPathRoot($PSScriptRoot)) 'WO049 synthetic fixture'))
$fixtureGo = '00000000-0000-4000-8000-000000000001'
$fixtureHash = 'b' * 64
$fixtureTime = [DateTimeOffset]'2030-01-02T03:04:05.123456Z'

function New-LaunchFixture {
    $argsMap = @{
        GoId = $fixtureGo; OwnerGoDocumentSha256 = $fixtureHash
        ClientCertificateSha256 = ('a' * 64); DatabaseName = 'wo049_synthetic'
        DatabaseUser = 'wo049_fixture'; DatabasePassword = 'WO049_ONLY_SYNTHETIC_NOT_A_SECRET'
        ExportDirectory = (Join-Path $fixtureRoot 'exports')
    }
    return New-WO046LocalLabLaunchEnvironment @argsMap
}

function New-StopFixture {
    return ,(New-WO046TechnicalStopBytes -GoId $fixtureGo `
        -OwnerGoDocumentSha256 $fixtureHash -StoppedAtUtc $fixtureTime)
}

function Assert-StopFixture {
    param([byte[]]$Candidate)
    Test-WO046TechnicalStopBytes -Bytes $Candidate -ExpectedGoId $fixtureGo `
        -ExpectedOwnerGoDocumentSha256 $fixtureHash
}

Describe 'WO-049 pure launch preparation boundary' {
    It 'parses both versioned scripts and contains no execution or external I/O calls' {
        foreach ($path in @($modulePath, $fixturePath)) {
            $tokens = $null; $errors = $null
            $ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
            $errors.Count | Should Be 0
            $forbiddenCommands = @('Start-Process', 'Stop-Process', 'Invoke-WebRequest', 'Invoke-RestMethod',
                'Get-Content', 'Set-Content', 'Add-Content', 'New-Item', 'Remove-Item', 'Get-NetTCPConnection',
                'docker', 'psql', 'Import-Certificate', 'Invoke-Expression')
            $commands = $ast.FindAll({param($n) $n -is [Management.Automation.Language.CommandAst]}, $true)
            foreach ($command in $commands) {
                ($forbiddenCommands -contains $command.GetCommandName()) | Should Be $false
            }
            $members = $ast.FindAll({param($n) $n -is [Management.Automation.Language.InvokeMemberExpressionAst]}, $true)
            foreach ($member in $members) {
                (@('Start', 'Kill', 'SetEnvironmentVariable', 'WriteAllText', 'WriteAllBytes', 'Open',
                    'Connect', 'SendAsync', 'PostAsync', 'GetAsync') -contains $member.Member.Value) | Should Be $false
            }
        }
    }

    It 'prepares exact names, references, origin and provider gates' {
        $environment = New-LaunchFixture
        { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Not Throw
        $environment['OPTIONAL_INTEGRATION_EXECUTION_MODE'] | Should Be 'PROVIDER_DERIVED'
        $environment['OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID'] | Should Be $fixtureGo
        $environment['OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'] | Should Be $fixtureHash
        $environment['OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256'] | Should Be ('a' * 64)
        $environment['OPTIONAL_INTEGRATION_RECEIVER_ORIGIN'] | Should Be 'https://127.0.0.1:8444'
        $environment['SERVER_ADDRESS'] | Should Be '127.0.0.1'
        $environment['SERVER_PORT'] | Should Be '8087'
        foreach ($key in @('SOFASCORE_ENABLED', 'SOFASCORE_PLAYWRIGHT_ENABLED', 'SOFASCORE_J3_QUALIFICATION_ENABLED',
                'SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED', 'SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED',
                'SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED', 'SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED')) {
            $environment[$key] | Should Be 'false'
        }
    }

    It 'rejects removal of every required environment key' {
        $keys = @((New-LaunchFixture).Keys)
        foreach ($key in $keys) {
            $environment = New-LaunchFixture
            $environment.Remove($key)
            { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Throw 'WO046_LAUNCH_ENVIRONMENT_REFUSED'
        }
    }

    It 'rejects wrong aliases, additional overrides and casing drift' {
        foreach ($alias in @('OPTIONAL_INTEGRATION_EXECUTIONMODE', 'OPTIONALINTEGRATION_EXECUTIONMODE',
                'OPTIONAL_INTEGRATION_PROVIDEROWNERGO_GOID', 'optional_integration_enabled', 'UNEXPECTED')) {
            $environment = New-LaunchFixture
            if ($alias -ceq 'optional_integration_enabled') { $environment.Remove('OPTIONAL_INTEGRATION_ENABLED') }
            $environment[$alias] = 'true'
            { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Throw 'WO046_LAUNCH_ENVIRONMENT_REFUSED'
        }
    }

    It 'rejects changed fixed values including provider activity, timeout, retry and TLS' {
        $cases = @{
            SERVER_ADDRESS = '0.0.0.0'; SERVER_PORT = '8090'; POSTGRES_PORT = '5433'
            SOFASCORE_ENABLED = 'true'; SOFASCORE_PLAYWRIGHT_ENABLED = 'true'
            OPTIONAL_INTEGRATION_RECEIVER_ORIGIN = 'https://example.invalid:8444'
            OPTIONAL_INTEGRATION_REQUEST_TIMEOUT = '11s'; OPTIONAL_INTEGRATION_CONNECT_TIMEOUT = '0s'
            OPTIONAL_INTEGRATION_AUTOMATIC_RETRY_ENABLED = 'true'; OPTIONAL_INTEGRATION_MTLS_REQUIRED = 'false'
            OPTIONAL_INTEGRATION_MTLS_KEY_STORE_TYPE = 'PKCS12'
            OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED = 'false'
            OPTIONAL_INTEGRATION_RECEIVER_QUALIFICATION = 'NOT_QUALIFIED'
            SPRING_APPLICATION_JSON = '{}'; JAVA_TOOL_OPTIONS = '-agentlib:untrusted'
        }
        foreach ($key in $cases.Keys) {
            $environment = New-LaunchFixture; $environment[$key] = $cases[$key]
            { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Throw 'WO046_LAUNCH_ENVIRONMENT_REFUSED'
        }
    }

    It 'preserves compatible audit status but rejects evidenced incompatibility' {
        foreach ($status in @('NOT_EVIDENCED', 'EVIDENCED_COMPATIBLE')) {
            $environment = New-LaunchFixture
            $environment['OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS'] = $status
            { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Not Throw
        }
        $environment['OPTIONAL_INTEGRATION_OFFICIAL_PERMISSION_STATUS'] = 'EVIDENCED_INCOMPATIBLE'
        { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Throw 'WO046_LAUNCH_ENVIRONMENT_REFUSED'
    }

    It 'rejects malformed references, identifiers, newline injection and ambiguous paths' {
        $cases = @(
            @{key='OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID'; value='not-a-uuid'},
            @{key='OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID'; value=($fixtureGo+"`n")},
            @{key='OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'; value=($fixtureHash+"`n")},
            @{key='OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256'; value=('B'*64)},
            @{key='OPTIONAL_INTEGRATION_MTLS_CLIENT_CERTIFICATE_SHA256'; value=('a'*63)},
            @{key='POSTGRES_USER'; value="fixture`nuser"},
            @{key='POSTGRES_PASSWORD'; value="fixture`rpassword"},
            @{key='SOFASCORE_EXPORT_DIR'; value='relative/exports'},
            @{key='SOFASCORE_EXPORT_DIR'; value=(Join-Path $fixtureRoot '../outside')}
        )
        foreach ($case in $cases) {
            $environment = New-LaunchFixture; $environment[$case.key] = $case.value
            { Assert-WO046LocalLabLaunchEnvironment $environment } | Should Throw 'WO046_LAUNCH_ENVIRONMENT_REFUSED'
        }
    }

    It 'does not print bad values in validation errors' {
        $environment = New-LaunchFixture
        $environment['POSTGRES_PASSWORD'] = "WO049_SYNTHETIC_PRIVATE_SENTINEL`n"
        try { Assert-WO046LocalLabLaunchEnvironment $environment; throw 'EXPECTED_REFUSAL' }
        catch { $_.Exception.Message | Should Be 'WO046_LAUNCH_ENVIRONMENT_REFUSED' }
    }

    It 'creates independent dictionaries without mutating parent environment' {
        $before = [Environment]::GetEnvironmentVariable('OPTIONAL_INTEGRATION_ENABLED', 'Process')
        $one = New-LaunchFixture; $two = New-LaunchFixture
        $one['OPTIONAL_INTEGRATION_ENABLED'] = 'false'
        $two['OPTIONAL_INTEGRATION_ENABLED'] | Should Be 'true'
        [Environment]::GetEnvironmentVariable('OPTIONAL_INTEGRATION_ENABLED', 'Process') | Should Be $before
    }

    It 'prepares exact ArgumentList including a single unquoted JAR path containing spaces' {
        $environment = New-LaunchFixture
        $jar = Join-Path $fixtureRoot 'application with spaces.jar'
        $info = New-WO046LocalLabStartInfo -Environment $environment -JavaPath (Join-Path $fixtureRoot 'java.exe') `
            -JarPath $jar -WorkingDirectory $fixtureRoot -InstanceId $fixtureGo `
            -HostEnvironment @{SystemRoot=(Join-Path $fixtureRoot 'Windows')}
        $info.ArgumentList.Count | Should Be 6
        $info.ArgumentList[0] | Should Be ('-Dwo046.instance={0}' -f $fixtureGo)
        $info.ArgumentList[1] | Should Be '-Djdk.httpclient.disableRetryConnect=true'
        $info.ArgumentList[2] | Should Be '-Djdk.httpclient.redirects.retrylimit=1'
        $info.ArgumentList[3] | Should Be '-Djdk.httpclient.enableAllMethodRetry=false'
        $info.ArgumentList[4] | Should Be '-jar'
        $info.ArgumentList[5] | Should Be $jar
        $info.Arguments | Should Be ''
        $info.UseShellExecute | Should Be $false
        $info.CreateNoWindow | Should Be $true
        $info.RedirectStandardOutput | Should Be $true
        $info.RedirectStandardError | Should Be $true
        $info.Environment.Count | Should Be ($environment.Count + 1)
        $environment['OPTIONAL_INTEGRATION_ENABLED'] = 'false'
        $info.Environment['OPTIONAL_INTEGRATION_ENABLED'] | Should Be 'true'
    }

    It 'refuses shell executables and malformed paths before constructing launch information' {
        foreach ($invalidJarCandidate in @('relative.jar', (Join-Path $fixtureRoot 'bad.txt'),
                (Join-Path $fixtureRoot 'quoted"path.jar'), (Join-Path $fixtureRoot "control`npath.jar"))) {
            $pathRefused = $false
            try {
                $null = New-WO046LocalLabStartInfo -Environment (New-LaunchFixture) `
                    -JavaPath (Join-Path $fixtureRoot 'java.exe') -JarPath $invalidJarCandidate `
                    -WorkingDirectory $fixtureRoot -InstanceId $fixtureGo -HostEnvironment @{SystemRoot=$fixtureRoot}
            }
            catch { $pathRefused = $_.Exception.Message -ceq 'WO046_INVALID_ABSOLUTE_PATH' }
            $pathRefused | Should Be $true
        }
        { New-WO046LocalLabStartInfo -Environment (New-LaunchFixture) `
            -JavaPath (Join-Path $fixtureRoot 'cmd.exe') -JarPath (Join-Path $fixtureRoot 'app.jar') `
            -WorkingDirectory $fixtureRoot -InstanceId $fixtureGo `
            -HostEnvironment @{SystemRoot=$fixtureRoot} } | Should Throw 'WO046_INVALID_JAVA_PATH'
    }

    It 'refuses inherited injection variables and a missing SystemRoot' {
        foreach ($hostValues in @(@{}, @{SystemRoot=$fixtureRoot; JAVA_TOOL_OPTIONS='-agentlib:bad'},
                @{SystemRoot=$fixtureRoot; SPRING_APPLICATION_JSON='{}'},
                @{SystemRoot=$fixtureRoot; POSTGRES_PASSWORD='SHOULD_NOT_BE_INHERITED'})) {
            { New-WO046LocalLabStartInfo -Environment (New-LaunchFixture) `
                -JavaPath (Join-Path $fixtureRoot 'java.exe') -JarPath (Join-Path $fixtureRoot 'app.jar') `
                -WorkingDirectory $fixtureRoot -InstanceId $fixtureGo -HostEnvironment $hostValues } |
                Should Throw 'WO046_HOST_ENVIRONMENT_REFUSED'
        }
    }
}

Describe 'WO-049 strict technical stop serialization' {
    It 'produces ten nonempty ordered fields and one LF, with independent stable golden hash' {
        $bytes = New-StopFixture
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $bytes.Length | Should Be 476
        $text.Contains("`r") | Should Be $false
        $text.Split("`n").Count | Should Be 11
        $text.Split("`n")[3] | Should Be ('GO_ID={0}' -f $fixtureGo)
        $text.Split("`n")[5] | Should Be 'STOPPED_AT_UTC=2030-01-02T03:04:05.123456Z'
        $result = Assert-StopFixture $bytes
        $result.Canonical | Should Be $true
        $result.Sha256 | Should Be '058022ed74ef13e2c9c51f69bd7b87bec612d33baf5f06cf5e8756a8e810d2c1'
    }

    It 'is byte stable and culture independent' {
        $originalCulture = [Threading.Thread]::CurrentThread.CurrentCulture
        try {
            [Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]'fr-FR'
            $one = [Convert]::ToBase64String((New-StopFixture))
            [Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]'tr-TR'
            [Convert]::ToBase64String((New-StopFixture)) | Should Be $one
        }
        finally { [Threading.Thread]::CurrentThread.CurrentCulture = $originalCulture }
    }

    It 'reproduces and rejects the R1 split-line serialization' {
        $text = [Text.Encoding]::UTF8.GetString((New-StopFixture))
        $broken = $text.Replace('GO_ID=', "GO_ID=`n").Replace('STOPPED_AT_UTC=', "STOPPED_AT_UTC=`n")
        { Assert-StopFixture ([Text.Encoding]::UTF8.GetBytes($broken)) } | Should Throw 'WO046_TECHNICAL_STOP_REFUSED'
    }

    It 'rejects BOM, CRLF, missing LF, extra LF, NUL, invalid UTF8 and oversized input' {
        $bytes = New-StopFixture
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $cases = [Collections.Generic.List[byte[]]]::new()
        $cases.Add([byte[]](@(239,187,191)+$bytes))
        $cases.Add([Text.Encoding]::UTF8.GetBytes($text.Replace("`n", "`r`n")))
        $cases.Add([Text.Encoding]::UTF8.GetBytes($text.TrimEnd("`n")))
        $cases.Add([Text.Encoding]::UTF8.GetBytes($text+"`n"))
        $cases.Add([byte[]]($bytes+@(0)))
        $cases.Add([byte[]]@(255,254,253))
        $cases.Add([byte[]]::new(4097))
        foreach ($case in $cases) {
            { Assert-StopFixture $case } | Should Throw 'WO046_TECHNICAL_STOP_REFUSED'
        }
    }

    It 'rejects every field removal, duplicate, unknown field, reorder and empty reference' {
        $text = [Text.Encoding]::UTF8.GetString((New-StopFixture))
        $lines = $text.TrimEnd("`n").Split("`n")
        for ($i=0; $i -lt $lines.Count; $i++) {
            $missing = @($lines[0..($lines.Count-1)] | Where-Object {$_ -cne $lines[$i]}) -join "`n"
            { Assert-StopFixture ([Text.Encoding]::UTF8.GetBytes($missing+"`n")) } | Should Throw 'WO046_TECHNICAL_STOP_REFUSED'
        }
        foreach ($bad in @(
                ($text.Replace('ACTOR=CODEX_LOCAL_AGENT', 'ACTOR=OWNER')),
                ($text.Replace('ACTOR=CODEX_LOCAL_AGENT', 'FORMAT=CODEX_LOCAL_AGENT')),
                ($text.Replace('ACTOR=CODEX_LOCAL_AGENT', 'UNKNOWN=CODEX_LOCAL_AGENT')),
                ($text.Replace(('GO_ID={0}' -f $fixtureGo), 'GO_ID=')),
                ($text.Replace("ACTOR=CODEX_LOCAL_AGENT`nAUTHORITY=", "AUTHORITY=CODEX_LOCAL_AGENT`nACTOR=")),
                ($text+'ADDITIONAL=NO'+"`n"),
                ($text.Replace('NEW_GO_OR_RETRY_AUTHORIZED=NO', 'NEW_GO_OR_RETRY_AUTHORIZED=YES')))) {
            { Assert-StopFixture ([Text.Encoding]::UTF8.GetBytes($bad)) } | Should Throw 'WO046_TECHNICAL_STOP_REFUSED'
        }
    }

    It 'rejects mismatched expected go and decision references' {
        { Test-WO046TechnicalStopBytes -Bytes (New-StopFixture) `
            -ExpectedGoId '00000000-0000-4000-8000-000000000002' `
            -ExpectedOwnerGoDocumentSha256 $fixtureHash } | Should Throw 'WO046_TECHNICAL_STOP_REFUSED'
        { Test-WO046TechnicalStopBytes -Bytes (New-StopFixture) -ExpectedGoId $fixtureGo `
            -ExpectedOwnerGoDocumentSha256 ('c'*64) } | Should Throw 'WO046_TECHNICAL_STOP_REFUSED'
    }

    It 'rejects non-UTC timestamps and unrepresentable sub-microsecond precision' {
        foreach ($bad in @([DateTimeOffset]'2030-01-02T04:04:05.123456+01:00',
                [DateTimeOffset]'2030-01-02T03:04:05.1234567Z')) {
            { New-WO046TechnicalStopBytes -GoId $fixtureGo -OwnerGoDocumentSha256 $fixtureHash `
                -StoppedAtUtc $bad } | Should Throw 'WO046_STOP_TIMESTAMP_REQUIRES_EXACT_UTC_MICROSECONDS'
        }
    }

    It 'rejects malformed or injected reference values before rendering' {
        foreach ($bad in @('00000000-0000-0000-0000-000000000000', 'not-a-uuid', ($fixtureGo+"`n"))) {
            { New-WO046TechnicalStopBytes -GoId $bad -OwnerGoDocumentSha256 $fixtureHash `
                -StoppedAtUtc $fixtureTime } | Should Throw 'WO046_INVALID_EXACT_REFERENCE'
        }
        { New-WO046TechnicalStopBytes -GoId $fixtureGo -OwnerGoDocumentSha256 ($fixtureHash+"`n") `
            -StoppedAtUtc $fixtureTime } | Should Throw 'WO046_INVALID_EXACT_REFERENCE'
    }
}
