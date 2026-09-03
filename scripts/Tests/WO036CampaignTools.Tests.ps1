$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$toolsRoot = Join-Path $repositoryRoot 'scripts\wo036'
$modulePath = Join-Path $toolsRoot 'WO036-CampaignTools.psm1'
$moduleText = Get-Content -LiteralPath $modulePath -Raw
$wrapperNames = @(
    'Register-WO036ExecutableArtifacts',
    'Start-WO036Component',
    'Stop-WO036Component',
    'Copy-WO036LocalLabDatabase',
    'Export-WO036PreCallFreeze',
    'Test-WO036FrozenTooling',
    'Invoke-WO036CollisionProbe',
    'Export-WO036RedactedEvidence',
    'Test-WO036PrivateLogRedaction'
)

Import-Module $modulePath -Force

Describe 'WO-036 campaign module and wrapper surface' {
    It 'parses the module and every wrapper without syntax errors' {
        $paths = @($modulePath) + @($wrapperNames | ForEach-Object {
            Join-Path $toolsRoot "$_.ps1"
        })
        foreach ($path in $paths) {
            (Test-Path -LiteralPath $path -PathType Leaf) | Should Be $true
            $tokens = $null
            $errors = $null
            [void][Management.Automation.Language.Parser]::ParseFile(
                $path, [ref]$tokens, [ref]$errors)
            $errors.Count | Should Be 0
        }
    }

    It 'exports the exact operator commands including frozen-tooling validation' {
        $actual = @((Get-Command -Module WO036-CampaignTools).Name | Sort-Object)
        $expected = @($wrapperNames + 'Read-WO036PrivateState' | Sort-Object)
        (Compare-Object $expected $actual) | Should BeNullOrEmpty
    }

    It 'imports the shared module and calls the matching command in every wrapper' {
        foreach ($name in $wrapperNames) {
            $text = Get-Content -LiteralPath (Join-Path $toolsRoot "$name.ps1") -Raw
            $text | Should Match "Import-Module .*WO036-CampaignTools\.psm1"
            $text | Should Match ([regex]::Escape($name) + ' (?:@PSBoundParameters|-StatePath)')
        }
    }
}

Describe 'WO-036 fail-closed runtime invariants' {
    It 'uses all three qualified JVM HttpClient retry guards' {
        $moduleText | Should Match '-Djdk\.httpclient\.disableRetryConnect=true'
        $moduleText | Should Match '-Djdk\.httpclient\.redirects\.retrylimit=1'
        $moduleText | Should Match '-Djdk\.httpclient\.enableAllMethodRetry=false'
        ([regex]::Matches($moduleText, '\$script:JdkHttpClientRetryGuards\[[0-2]\]').Count) |
            Should Be 3
    }

    It 'neutralizes inherited configuration, proxy, Java debug and TLS key logging' {
        foreach ($name in @(
                'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_LOCATION',
                'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS',
                'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'SSLKEYLOGFILE',
                'NSS_SSLKEYLOGFILE', 'JAVAX_NET_DEBUG',
                'JDK_HTTPCLIENT_HTTPCLIENT_LOG')) {
            $moduleText | Should Match ([regex]::Escape("'$name'"))
        }
        $moduleText | Should Match ([regex]::Escape("Join-Path `$workingDirectory '.env'"))
        $moduleText | Should Match ([regex]::Escape('-WorkingDirectory $workingDirectory'))
    }

    It 'loads only the packaged Spring configuration from the private runtime directory' {
        $moduleText | Should Match (
            "'SPRING_CONFIG_LOCATION'\s*=\s*'classpath:/'")
        $moduleText | Should Not Match (
            "'SPRING_CONFIG_LOCATION'\s*=\s*''")
        $moduleText | Should Not Match (
            "'SPRING_CONFIG_LOCATION'\s*=\s*'(?:optional:)?file:")
    }

    It 'uses the exact pre-created shared lock without an OpenOrCreate fallback' {
        $moduleText | Should Match "'\.wo036-tools\.lock'"
        $moduleText | Should Match '\[IO\.FileMode\]::Open'
        $moduleText | Should Match '\[IO\.FileShare\]::None'
        $moduleText | Should Not Match '\[IO\.FileMode\]::OpenOrCreate'
    }

    It 'uses only the exact pinned private Docker endpoint and freezes its hash' {
        $moduleText | Should Match 'docker-endpoint\.private\.txt'
        $moduleText | Should Match 'Read-WO036PinnedDockerEndpoint'
        $moduleText | Should Match 'dockerEndpointSha256'
        $moduleText | Should Match '\^npipe:////\[\.\]/pipe/\[A-Za-z0-9\._-\]\+\$'
        $moduleText | Should Not Match "GetEnvironmentVariable\('DOCKER_CONTEXT'\)"
        $moduleText | Should Not Match "GetEnvironmentVariable\('DOCKER_HOST'\)"
        $moduleText | Should Not Match 'context inspect --format'
    }

    It 'requires separately qualified clean-build JAR hashes at registration' {
        $moduleText | Should Match 'ExpectedLocalLabJarSha256'
        $moduleText | Should Match 'ExpectedReceiverJarSha256'
        $moduleText | Should Match 'separately qualified clean builds'
        $moduleText | Should Match 'registered-tooling\.json'
        $moduleText | Should Match 'javaSha256'
    }

    It 'pins the Local Lab sender provenance to the qualified WO-038 implementation' {
        $moduleText | Should Match ([regex]::Escape(
            "`$script:ExpectedLocalLabSenderCommit = '3a0c297a5151c572417b4f2f12bb5c3ed216172f'"))
        $moduleText | Should Not Match 'f5a27887b7db43576eb608d564c245c8cca3a602'
    }

    It 'reads active redirected logs through a stable bounded shared snapshot' {
        $moduleText | Should Match 'function Read-WO036StableActiveLogBytes'
        $moduleText | Should Match '\[IO\.FileShare\]::ReadWrite'
        $moduleText | Should Match '\$stream\.Length -ne \$lengthBefore'
        $moduleText | Should Match 'stable bounded snapshot'
        $moduleText | Should Not Match '\[IO\.File\]::ReadAllBytes\(\$file\.FullName\)'
    }

    It 'rehashes every frozen tool and allows only the exact manifest commit afterward' {
        $moduleText | Should Match 'Get-WO036ToolingFileProof'
        $moduleText | Should Match 'Assert-WO036RegisteredTooling'
        $moduleText | Should Match 'J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R4-20260903\.md'
        $moduleText | Should Match 'merge-base.*--is-ancestor'
        $moduleText | Should Match 'tooling changed after its one-shot registration'
    }

    It 'serializes all public mutating and evidence commands under the shared lock' {
        ([regex]::Matches($moduleText,
            'Invoke-WO036LockedOperation -StatePath \$StatePath').Count) | Should Be 9
    }

    It 'normalizes process timestamps without requiring a recent PowerShell JSON option' {
        $moduleText | Should Match 'function ConvertTo-WO036UtcDateTime'
        ([regex]::Matches($moduleText,
            'ConvertTo-WO036UtcDateTime').Count) | Should Be 3
        $moduleText | Should Not Match 'ConvertFrom-Json[^\r\n]*-DateKind'
    }
}

Describe 'WO-036 Preparation A B sequence and one-shot clone' {
    It 'models distinct Preparation, A and B components with no concurrent Local Lab process' {
        $moduleText | Should Match "ValidateSet\('Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB'\)"
        $moduleText | Should Match 'Local Lab campaign instances cannot run concurrently'
        $moduleText | Should Match 'preparation instance is only available before the clone'
        $moduleText | Should Match 'Local Lab B requires the exact durable 201 proof from A'
    }

    It 'evaluates the preparation clone gate as a Boolean expression' {
        $moduleText | Should Match (
            'if \(\(Test-Path -LiteralPath \$clonePath\)\s*`\s*' +
            '-or @\(\$state\.Config\.ownedProcesses')
        $moduleText | Should Not Match (
            'if \(Test-Path -LiteralPath \$clonePath\s*`\s*' +
            '-or @\(\$state\.Config\.ownedProcesses')
    }

    It 'requires a graceful preparation stop before a one-shot clone claim' {
        $moduleText | Should Match 'Assert-WO036GracefulStopResult -State \$state -Component LocalLabPreparation'
        $moduleText | Should Match "'database-clone\.json'"
        $moduleText | Should Match 'CLAIMED_UNKNOWN_CONSUMED'
        $moduleText | Should Match 'Write-WO036PrivateJson -Path \$claimPath -Value \$claim -CreateNew'
    }

    It 'proves B absent and creates it once without DROP or malformed backslash quotes' {
        $moduleText | Should Match "'targetCount'.*pg_database"
        $moduleText | Should Match '\[int\]\$preflight\.targetCount -ne 0'
        $moduleText | Should Match 'create database "\$target" with template "\$source";'
        $moduleText | Should Not Match '(?i)drop\s+database'
        $moduleText | Should Not Match 'create database \\"\$target'
    }

    It 'binds the clone to one HUMAN_VALIDATED five-source synthetic artifact and zero deliveries' {
        $moduleText | Should Match 'humanValidatedCandidateCount'
        $moduleText | Should Match 'jsonb_array_length\(source_observations\) = 5'
        $moduleText | Should Match "sourceKind' = 'SYNTHETIC_FIXTURE'"
        $moduleText | Should Match '\[int\]\$corpus\.deliveryCount -ne 0'
        $moduleText | Should Match '\[int\]\$corpus\.attemptCount -ne 0'
        $moduleText | Should Match '\[int\]\$corpus\.resultCount -ne 0'
    }
}

Describe 'WO-036 exact three-call proof model' {
    It 'names and caps import-route calls separately from shutdown control calls' {
        $moduleText | Should Match '\$script:MaximumImportRouteCalls = 3'
        $moduleText | Should Match 'WO036_MAXIMUM_IMPORT_ROUTE_CALLS'
        $moduleText | Should Match 'shutdownControlCallsBeforeEvidence = 3'
        $moduleText | Should Not Match 'WO036_DIRECT_ATTEMPTS'
    }

    It 'proves receiver global counts at freeze, after 201, after duplicate and after collision' {
        foreach ($field in @(
                'globalReceiptCount', 'globalPayloadCount',
                'globalAuditTotalCount', 'globalOutboxJ7Count')) {
            $moduleText | Should Match ([regex]::Escape($field))
        }
        $moduleText | Should Match '\$receiverProof\.globalAuditTotalCount -ne 1'
        $moduleText | Should Match '\$receiver\.globalAuditTotalCount -ne 2'
        $moduleText | Should Match '\$after\.globalAuditTotalCount -ne 3'
    }

    It 'correlates the 409 audit to exact export, mutant hashes, key and reason' {
        $moduleText | Should Match 'request_idempotency_key'
        $moduleText | Should Match 'observed_file_sha256'
        $moduleText | Should Match 'observed_data_sha256'
        $moduleText | Should Match "reason_code = 'EXPORT_ID_DIVERGENCE'"
        $moduleText | Should Match 'idempotencyKeySha256'
        $moduleText | Should Match 'FULL_J7_ENVELOPE_METADATA_ONLY_MUTATION'
    }

    It 'writes the collision request as exact bounded HTTP bytes over pinned loopback TLS' {
        $moduleText | Should Match 'function New-WO036CollisionHttpRequest'
        $moduleText | Should Match 'Content-Type: \$script:RequestMediaType'
        $moduleText | Should Match 'Host: 127\.0\.0\.1:8444'
        $moduleText | Should Match 'Connection: close'
        $moduleText | Should Match 'function Invoke-WO036ExactLoopbackHttpsRequest'
        $moduleText | Should Match '\[Net\.Security\.SslStream\]::new'
        $moduleText | Should Match "TargetHost = '127\.0\.0\.1'"
        $moduleText | Should Match 'ExpectedServerCertificateSha256'
        $moduleText | Should Not Match 'MediaTypeHeaderValue'
    }

    It 'bounds response evidence to a status and safe problem code before classification' {
        $moduleText | Should Match 'function Read-WO036BoundedHttpResponse'
        $moduleText | Should Match 'function Get-WO036SafeProblemCode'
        $moduleText | Should Match "safeProblemCode.*J7_IMPORT_CONFLICT"
        $moduleText | Should Match "claim\['httpStatus'\]"
        $moduleText | Should Match "claim\['safeProblemCode'\]"
        $moduleText | Should Match "(?s)claim\['httpStatus'\].*Update-WO036PrivateJsonAtomic.*Get-WO036SafeProblemCode"
        $moduleText | Should Match 'response exceeds the bounded wire envelope'
        $moduleText | Should Not Match "claim\['responseBody'\]"
        $moduleText | Should Not Match "claim\['rawResponse'\]"
    }

    It 'phase-gates PRE_COLLISION and FINAL against the one-shot collision claim' {
        $moduleText | Should Match 'PRE_COLLISION evidence is unavailable after a collision claim'
        $moduleText | Should Match "collision\.status -ceq 'PASS_CONSUMED'"
        $moduleText | Should Match 'collision\.importRouteCallOrdinal -eq 3'
        $moduleText | Should Match 'collision\.httpStatus -eq 409'
    }

    It 'requires three graceful Local Lab stops and the exact live receiver listener' {
        $moduleText | Should Match '\$gracefulStopCount -eq 3'
        $moduleText | Should Match '\$forcedStopCount -eq 0'
        $moduleText | Should Match 'STOPPED_EXACT_FORCED'
        $moduleText | Should Match 'Test-WO036ExactLoopbackListener -Port 8444'
        $moduleText | Should Match 'Get-NetTCPConnection -State Listen -LocalPort 8087'
    }

    It 'requires the exact six pre-call logs and eight completed Local Lab logs' {
        $moduleText | Should Match 'Assert-WO036ExactPrivateLogSet -Proof \$logProof'
        $moduleText | Should Match 'ExpectedNames \$script:PreCallLogNames'
        $moduleText | Should Match 'ExpectedNames \$script:CompletedLocalLabLogNames'
        $moduleText | Should Match 'fileNames = @\(\$logProof\.FileNames\)'
    }

    It 'contains no provider, VPS or non-loopback campaign target' {
        $moduleText | Should Not Match '(?i)api\.sofascore\.com'
        $moduleText | Should Not Match '51\.255\.167\.32'
        $moduleText | Should Not Match '0\.0\.0\.0'
        $moduleText | Should Match 'https://127\.0\.0\.1:8444'
    }
}

Describe 'WO-036 offline behavioral primitives' {
    InModuleScope WO036-CampaignTools {
        It 'normalizes a deserialized ISO start time under a French culture' {
            $previousCulture = [Globalization.CultureInfo]::CurrentCulture
            try {
                [Globalization.CultureInfo]::CurrentCulture =
                    [Globalization.CultureInfo]::GetCultureInfo('fr-FR')
                $parsed = '{"StartTimeUtc":"2026-09-03T06:43:08.7271579Z"}' |
                    ConvertFrom-Json -Depth 4
                $actual = ConvertTo-WO036UtcDateTime `
                    -Value $parsed.StartTimeUtc -Description 'test start time'
                $actual.Kind | Should Be ([DateTimeKind]::Utc)
                $actual.ToString('O') | Should Be '2026-09-03T06:43:08.7271579Z'
                $localizedRejected = $false
                try {
                    ConvertTo-WO036UtcDateTime -Value '03/09/2026 06:43:08' `
                        -Description 'test start time' | Out-Null
                }
                catch {
                    $localizedRejected = $true
                }
                $localizedRejected | Should Be $true
            }
            finally {
                [Globalization.CultureInfo]::CurrentCulture = $previousCulture
            }
        }

        It 'returns the packaged Spring configuration location for every component' {
            $state = [pscustomobject]@{
                ReceiverKeyStore = 'receiver.p12'
                ReceiverTrustStore = 'receiver-trust.p12'
                ExportDirectory = 'exports'
                Config = [pscustomobject]@{
                    receiver = [pscustomobject]@{
                        clientCertificateSha256 = ('a' * 64)
                        keyStorePassword = 'test-only'
                        trustStorePassword = 'test-only'
                        databaseName = 'receiver'
                        databaseUser = 'receiver'
                        databasePassword = 'test-only'
                    }
                    localLab = [pscustomobject]@{
                        sourceDatabase = 'local_lab_a'
                        targetDatabase = 'local_lab_b'
                        databaseUser = 'local_lab'
                        databasePassword = 'test-only'
                        clientCertificateSha256 = ('b' * 64)
                    }
                }
            }

            foreach ($component in @(
                    'Receiver', 'LocalLabPreparation', 'LocalLabA', 'LocalLabB')) {
                $environment = Get-WO036ComponentEnvironment `
                    -State $state -Component $component
                $environment['SPRING_CONFIG_LOCATION'] |
                    Should Be 'classpath:/'
            }
        }

        It 'wires both qualified JAR hashes through the registration core' {
            $core = Get-Command Register-WO036ExecutableArtifactsCore
            $core.Parameters.ContainsKey('ExpectedLocalLabJarSha256') | Should Be $true
            $core.Parameters.ContainsKey('ExpectedReceiverJarSha256') | Should Be $true
            $provenance = Get-Command Assert-WO036GitArtifactProvenance
            $provenance.Parameters.ContainsKey('ExpectedLocalLabJarSha256') | Should Be $false
            $provenance.Parameters.ContainsKey('ExpectedReceiverJarSha256') | Should Be $false
        }

        It 'accepts only one exact UTF-8 local npipe line and pins its hash' {
            $root = Join-Path $TestDrive 'docker-endpoint'
            [void](New-Item -ItemType Directory -Path $root)
            $endpointPath = Join-Path $root 'docker-endpoint.private.txt'
            $encoding = [Text.UTF8Encoding]::new($false, $true)
            [IO.File]::WriteAllText(
                $endpointPath,
                "npipe:////./pipe/dockerDesktopLinuxEngine`n",
                $encoding)
            Mock Assert-WO036NoReparsePathChain { }
            Mock Assert-WO036PrivateAcl { }
            $proof = Read-WO036PinnedDockerEndpoint -PrivateRoot $root
            $proof.Endpoint | Should Be 'npipe:////./pipe/dockerDesktopLinuxEngine'
            $proof.Sha256 | Should Match '^[0-9a-f]{64}$'
            $arguments = Get-WO036DockerArguments -State ([pscustomobject]@{
                PrivateRoot = $root
                DockerEndpoint = $proof.Endpoint
                DockerEndpointPath = $proof.Path
                DockerEndpointSha256 = $proof.Sha256
            })
            $arguments.Count | Should Be 2
            $arguments[0] | Should Be '--host'
            $arguments[1] | Should Be $proof.Endpoint

            [IO.File]::WriteAllText(
                $endpointPath,
                "npipe:////./pipe/otherEngine`n",
                $encoding)
            $changedFailed = $false
            try {
                [void](Get-WO036DockerArguments -State ([pscustomobject]@{
                    PrivateRoot = $root
                    DockerEndpoint = $proof.Endpoint
                    DockerEndpointPath = $proof.Path
                    DockerEndpointSha256 = $proof.Sha256
                }))
            }
            catch {
                $changedFailed = $true
            }
            $changedFailed | Should Be $true

            [IO.File]::WriteAllText(
                $endpointPath,
                "npipe:////./pipe/otherEngine`r`n",
                $encoding)
            $crlfFailed = $false
            try {
                [void](Read-WO036PinnedDockerEndpoint -PrivateRoot $root)
            }
            catch {
                $crlfFailed = $true
            }
            $crlfFailed | Should Be $true
        }

        It 'rejects a missing or substituted private log before a phase gate' {
            $proof = [pscustomobject]@{
                FileCount = 2
                FileNames = @('a.log', 'b.log')
            }
            { Assert-WO036ExactPrivateLogSet -Proof $proof `
                    -ExpectedNames @('a.log', 'b.log') -Gate 'test' } |
                Should Not Throw
            $substitutionFailed = $false
            try {
                Assert-WO036ExactPrivateLogSet -Proof $proof `
                    -ExpectedNames @('a.log', 'c.log') -Gate 'test'
            }
            catch {
                $substitutionFailed = $true
            }
            $substitutionFailed | Should Be $true
            $missingFailed = $false
            try {
                Assert-WO036ExactPrivateLogSet -Proof $proof `
                    -ExpectedNames @('a.log') -Gate 'test'
            }
            catch {
                $missingFailed = $true
            }
            $missingFailed | Should Be $true
        }

        It 'reads a flushed active log while the writer remains open' {
            $root = Join-Path $TestDrive 'shared-active-log'
            $logs = Join-Path $root 'logs'
            [void](New-Item -ItemType Directory -Path $logs)
            $logPath = Join-Path $logs 'locallaba.stderr.log'
            $safeBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes(
                "WO036_TEST_LOG=SAFE`n")
            $writer = [IO.FileStream]::new(
                $logPath,
                [IO.FileMode]::Create,
                [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::ReadWrite)
            try {
                $writer.Write($safeBytes, 0, $safeBytes.Length)
                $writer.Flush($true)
                Mock Assert-WO036NoReparsePathChain { }
                Mock Assert-WO036PrivateAcl { }
                $state = [pscustomobject]@{
                    PrivateRoot = $root
                    Config = [pscustomobject]@{
                        receiver = [pscustomobject]@{
                            databasePassword = ''
                            keyStorePassword = ''
                            trustStorePassword = ''
                        }
                        localLab = [pscustomobject]@{
                            databasePassword = ''
                            clientCertificateSha256 = ''
                        }
                    }
                }
                $proof = Get-WO036PrivateLogRedactionProof -State $state
                $proof.FileCount | Should Be 1
                $proof.SizeBytes | Should Be $safeBytes.Length
                $proof.ForbiddenOccurrenceCount | Should Be 0
            }
            finally {
                $writer.Dispose()
                [Array]::Clear($safeBytes, 0, $safeBytes.Length)
            }
        }

        It 'fails closed when an active log writer denies read sharing' {
            $logPath = Join-Path $TestDrive 'exclusive-active.log'
            [IO.File]::WriteAllText($logPath, "WO036_TEST_LOG=SAFE`n")
            $writer = [IO.FileStream]::new(
                $logPath,
                [IO.FileMode]::Open,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None)
            try {
                $failedClosed = $false
                try {
                    [void](Read-WO036StableActiveLogBytes -Path $logPath `
                        -MaximumBytes 1024)
                }
                catch {
                    $failedClosed = $_.Exception.Message -eq
                        'WO-036 active private log could not be read as one stable bounded snapshot.'
                }
                $failedClosed | Should Be $true
            }
            finally {
                $writer.Dispose()
            }
        }

        It 'reads logs created by the exact Windows Start-Process redirection mechanism' {
            if (-not $IsWindows) {
                return
            }
            $stdout = Join-Path $TestDrive 'redirected.stdout.log'
            $stderr = Join-Path $TestDrive 'redirected.stderr.log'
            $command = "[Console]::Out.WriteLine('WO036_STDOUT=SAFE');" +
                "[Console]::Error.WriteLine('WO036_STDERR=SAFE');" +
                'Start-Sleep -Seconds 15'
            $encoded = [Convert]::ToBase64String(
                [Text.Encoding]::Unicode.GetBytes($command))
            $process = Start-Process -FilePath (Get-Process -Id $PID).Path `
                -ArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive',
                    '-EncodedCommand', $encoded) `
                -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput $stdout -RedirectStandardError $stderr
            try {
                $deadline = [DateTime]::UtcNow.AddSeconds(3)
                do {
                    Start-Sleep -Milliseconds 50
                } while (((-not (Test-Path -LiteralPath $stdout)) `
                        -or (Get-Item -LiteralPath $stdout).Length -eq 0 `
                        -or (-not (Test-Path -LiteralPath $stderr)) `
                        -or (Get-Item -LiteralPath $stderr).Length -eq 0) `
                    -and [DateTime]::UtcNow -lt $deadline)
                $process.HasExited | Should Be $false
                $stdoutBytes = Read-WO036StableActiveLogBytes -Path $stdout `
                    -MaximumBytes 1024
                $stderrBytes = Read-WO036StableActiveLogBytes -Path $stderr `
                    -MaximumBytes 1024
                try {
                    $process.HasExited | Should Be $false
                    $stdoutBytes.Length | Should BeGreaterThan 0
                    $stderrBytes.Length | Should BeGreaterThan 0
                }
                finally {
                    [Array]::Clear($stdoutBytes, 0, $stdoutBytes.Length)
                    [Array]::Clear($stderrBytes, 0, $stderrBytes.Length)
                }
            }
            finally {
                try {
                    if (-not $process.HasExited) {
                        Stop-Process -InputObject $process -Force
                    }
                    Wait-Process -InputObject $process -Timeout 10 `
                        -ErrorAction SilentlyContinue
                }
                finally {
                    $process.Dispose()
                }
            }
        }

        It 'enforces FileShare.None on the pre-created lock' {
            $root = Join-Path $TestDrive 'private'
            [void](New-Item -ItemType Directory -Path $root)
            $statePath = Join-Path $root 'state.private.json'
            $lockPath = Join-Path $root '.wo036-tools.lock'
            [IO.File]::WriteAllText($statePath, '{}')
            [IO.File]::WriteAllText($lockPath, '')
            Mock Assert-WO036PrivateAcl { }
            Mock Assert-WO036NoReparsePathChain { }
            $first = Enter-WO036ToolsLock -StatePath $statePath
            try {
                $secondOpenFailed = $false
                try {
                    $second = Enter-WO036ToolsLock -StatePath $statePath
                    $second.Dispose()
                }
                catch {
                    $secondOpenFailed = $true
                }
                $secondOpenFailed | Should Be $true
            }
            finally {
                $first.Dispose()
            }
        }

        It 'refuses reuse of a CreateNew private one-shot claim' {
            $path = Join-Path $TestDrive 'claim.json'
            Mock Assert-WO036PrivateAcl { }
            Write-WO036PrivateJson -Path $path -Value @{ status = 'CLAIMED' } -CreateNew
            $secondClaimFailed = $false
            try {
                Write-WO036PrivateJson -Path $path `
                    -Value @{ status = 'SECOND' } -CreateNew
            }
            catch {
                $secondClaimFailed = $true
            }
            $secondClaimFailed | Should Be $true
        }

        It 'mutates only generator metadata while preserving data hash and byte length' {
            $id = [guid]::NewGuid()
            $data = '{"events":[]}'
            $encoding = [Text.UTF8Encoding]::new($false, $true)
            $dataHash = [Convert]::ToHexString(
                [Security.Cryptography.SHA256]::HashData(
                    $encoding.GetBytes($data))).ToLowerInvariant()
            $json = '{"manifest":{"exportId":"' + $id +
                '","dataSha256":"' + $dataHash +
                '","validation":{"status":"HUMAN_VALIDATED"},' +
                '"generatorVersion":"1.0.0"},"data":' + $data + "}`n"
            $original = $encoding.GetBytes($json)
            $mutation = New-WO036CollisionMutation -OriginalBytes $original
            try {
                $mutation.ExportId | Should Be $id
                $mutation.DataSha256 | Should Be $dataHash
                $mutation.Bytes.Length | Should Be $original.Length
                ([Convert]::ToHexString($mutation.Bytes) -eq
                    [Convert]::ToHexString($original)) | Should Be $false
            }
            finally {
                [Array]::Clear($mutation.Bytes, 0, $mutation.Bytes.Length)
                [Array]::Clear($original, 0, $original.Length)
            }
        }

        It 'builds one exact request header block and preserves the body byte for byte' {
            $encoding = [Text.UTF8Encoding]::new($false, $true)
            $body = $encoding.GetBytes('{"manifest":{},"data":{}}' + "`n")
            $fileHash = Get-WO036Sha256Hex -Bytes $body
            $dataHash = 'b' * 64
            $exportId = [guid]::NewGuid()
            $request = New-WO036CollisionHttpRequest -Body $body `
                -ExportId $exportId -FileSha256 $fileHash -DataSha256 $dataHash
            try {
                $header = [Text.Encoding]::ASCII.GetString(
                    $request.Bytes, 0, $request.HeaderSizeBytes)
                $header | Should Match '^POST /api/imports/sofascore/j7-canonical-events HTTP/1\.1\r\n'
                ([regex]::Matches($header,
                    'Content-Type: application/vnd\.betting-project\.j7-canonical-event\+json;version=1\.0\r\n').Count) |
                    Should Be 1
                $header.Contains(
                    'application/vnd.betting-project.j7-canonical-event+json; version=1.0') |
                    Should Be $false
                ([regex]::Matches($header, 'Content-Length: ' + $body.Length + '\r\n').Count) |
                    Should Be 1
                $header.EndsWith("Connection: close`r`n`r`n") | Should Be $true
                $request.HeaderSha256 | Should Match '^[0-9a-f]{64}$'
                $request.RequestSizeBytes | Should Be (
                    $request.HeaderSizeBytes + $body.Length)
                $actualBody = New-Object byte[] $body.Length
                [Buffer]::BlockCopy(
                    $request.Bytes, $request.HeaderSizeBytes,
                    $actualBody, 0, $actualBody.Length)
                try {
                    [Convert]::ToHexString($actualBody) |
                        Should Be ([Convert]::ToHexString($body))
                }
                finally {
                    [Array]::Clear($actualBody, 0, $actualBody.Length)
                }
            }
            finally {
                [Array]::Clear($request.Bytes, 0, $request.Bytes.Length)
                [Array]::Clear($body, 0, $body.Length)
            }
        }

        It 'rejects a request whose declared file hash is not the exact body hash' {
            $body = [Text.Encoding]::UTF8.GetBytes('{"synthetic":true}')
            try {
                $failedClosed = $false
                try {
                    [void](New-WO036CollisionHttpRequest -Body $body `
                        -ExportId ([guid]::NewGuid()) -FileSha256 ('a' * 64) `
                        -DataSha256 ('b' * 64))
                }
                catch {
                    $failedClosed = $_.Exception.Message -eq
                        'WO-036 collision request identity is invalid.'
                }
                $failedClosed | Should Be $true
            }
            finally {
                [Array]::Clear($body, 0, $body.Length)
            }
        }

        It 'parses one bounded fixed-length problem detail and returns only its safe code' {
            $bodyText = '{"type":"urn:test","title":"Conflict","status":409,' +
                '"detail":"safe","instance":"/test","code":"J7_IMPORT_CONFLICT"}'
            $body = [Text.UTF8Encoding]::new($false, $true).GetBytes($bodyText)
            $head = "HTTP/1.1 409 Conflict`r`n" +
                "Content-Type: application/problem+json`r`n" +
                "Content-Length: $($body.Length)`r`n" +
                "Connection: close`r`n`r`n"
            $headBytes = [Text.Encoding]::ASCII.GetBytes($head)
            $wire = New-Object byte[] ($headBytes.Length + $body.Length)
            [Buffer]::BlockCopy($headBytes, 0, $wire, 0, $headBytes.Length)
            [Buffer]::BlockCopy($body, 0, $wire, $headBytes.Length, $body.Length)
            $stream = [IO.MemoryStream]::new($wire, $false)
            $response = $null
            try {
                $response = Read-WO036BoundedHttpResponse -Stream $stream
                $response.StatusCode | Should Be 409
                (Get-WO036SafeProblemCode -Response $response) |
                    Should Be 'J7_IMPORT_CONFLICT'
                $response.PSObject.Properties.Name.Count | Should Be 3
                ($response.PSObject.Properties.Name -contains 'StatusCode') |
                    Should Be $true
                ($response.PSObject.Properties.Name -contains 'Headers') |
                    Should Be $true
                ($response.PSObject.Properties.Name -contains 'BodyBytes') |
                    Should Be $true
            }
            finally {
                if ($null -ne $response) {
                    [Array]::Clear($response.BodyBytes, 0, $response.BodyBytes.Length)
                }
                $stream.Dispose()
                [Array]::Clear($wire, 0, $wire.Length)
                [Array]::Clear($headBytes, 0, $headBytes.Length)
                [Array]::Clear($body, 0, $body.Length)
            }
        }

        It 'accepts an HTTP 1.1 status without the deprecated reason phrase' {
            $bodyText = '{"status":409,"code":"J7_IMPORT_CONFLICT"}'
            $body = [Text.UTF8Encoding]::new($false, $true).GetBytes($bodyText)
            $head = "HTTP/1.1 409`r`n" +
                "Content-Type: application/problem+json`r`n" +
                "Content-Length: $($body.Length)`r`n" +
                "Connection: close`r`n`r`n"
            $headBytes = [Text.Encoding]::ASCII.GetBytes($head)
            $wire = New-Object byte[] ($headBytes.Length + $body.Length)
            [Buffer]::BlockCopy($headBytes, 0, $wire, 0, $headBytes.Length)
            [Buffer]::BlockCopy($body, 0, $wire, $headBytes.Length, $body.Length)
            $stream = [IO.MemoryStream]::new($wire, $false)
            $response = $null
            try {
                $response = Read-WO036BoundedHttpResponse -Stream $stream
                $response.StatusCode | Should Be 409
                (Get-WO036SafeProblemCode -Response $response) |
                    Should Be 'J7_IMPORT_CONFLICT'
            }
            finally {
                if ($null -ne $response) {
                    [Array]::Clear($response.BodyBytes, 0, $response.BodyBytes.Length)
                }
                $stream.Dispose()
                [Array]::Clear($wire, 0, $wire.Length)
                [Array]::Clear($headBytes, 0, $headBytes.Length)
                [Array]::Clear($body, 0, $body.Length)
            }
        }

        It 'fails closed on a blank HTTP reason phrase separator' {
            $wire = [Text.Encoding]::ASCII.GetBytes(
                "HTTP/1.1 409 `r`nContent-Length: 0`r`nConnection: close`r`n`r`n")
            $stream = [IO.MemoryStream]::new($wire, $false)
            try {
                $failedClosed = $false
                try {
                    [void](Read-WO036BoundedHttpResponse -Stream $stream)
                }
                catch {
                    $failedClosed = $_.Exception.Message -eq
                        'WO-036 response status line is invalid.'
                }
                $failedClosed | Should Be $true
            }
            finally {
                $stream.Dispose()
                [Array]::Clear($wire, 0, $wire.Length)
            }
        }

        It 'parses a bounded chunked problem detail without retaining framing bytes' {
            $bodyText = '{"status":400,"code":"INVALID_CONTENT_TYPE"}'
            $body = [Text.UTF8Encoding]::new($false, $true).GetBytes($bodyText)
            $chunkSize = $body.Length.ToString('x', [Globalization.CultureInfo]::InvariantCulture)
            $wireText = "HTTP/1.1 400 Bad Request`r`n" +
                "Content-Type: application/problem+json`r`n" +
                "Transfer-Encoding: chunked`r`nConnection: close`r`n`r`n" +
                "$chunkSize`r`n$bodyText`r`n0`r`n`r`n"
            $wire = [Text.UTF8Encoding]::new($false, $true).GetBytes($wireText)
            $stream = [IO.MemoryStream]::new($wire, $false)
            $response = $null
            try {
                $response = Read-WO036BoundedHttpResponse -Stream $stream
                $response.StatusCode | Should Be 400
                (Get-WO036SafeProblemCode -Response $response) |
                    Should Be 'INVALID_CONTENT_TYPE'
                $response.BodyBytes.Length | Should Be $body.Length
            }
            finally {
                if ($null -ne $response) {
                    [Array]::Clear($response.BodyBytes, 0, $response.BodyBytes.Length)
                }
                $stream.Dispose()
                [Array]::Clear($wire, 0, $wire.Length)
                [Array]::Clear($body, 0, $body.Length)
            }
        }

        It 'fails closed on duplicate response headers and unsafe problem codes' {
            $duplicateText = "HTTP/1.1 409 Conflict`r`n" +
                "Content-Type: application/problem+json`r`n" +
                "Content-Type: application/problem+json`r`n" +
                "Content-Length: 0`r`nConnection: close`r`n`r`n"
            $duplicateBytes = [Text.Encoding]::ASCII.GetBytes($duplicateText)
            $duplicateStream = [IO.MemoryStream]::new($duplicateBytes, $false)
            try {
                $duplicateFailed = $false
                try {
                    [void](Read-WO036BoundedHttpResponse -Stream $duplicateStream)
                }
                catch {
                    $duplicateFailed = $true
                }
                $duplicateFailed | Should Be $true
            }
            finally {
                $duplicateStream.Dispose()
                [Array]::Clear($duplicateBytes, 0, $duplicateBytes.Length)
            }

            $unsafeBytes = [Text.Encoding]::UTF8.GetBytes(
                '{"status":409,"code":"unsafe value"}')
            $unsafeResponse = [pscustomobject]@{
                StatusCode = 409
                Headers = [Collections.Generic.Dictionary[string, string]]::new(
                    [StringComparer]::OrdinalIgnoreCase)
                BodyBytes = $unsafeBytes
            }
            $unsafeResponse.Headers.Add('Content-Type', 'application/problem+json')
            try {
                $unsafeFailed = $false
                try {
                    [void](Get-WO036SafeProblemCode -Response $unsafeResponse)
                }
                catch {
                    $unsafeFailed = $true
                }
                $unsafeFailed | Should Be $true
            }
            finally {
                [Array]::Clear($unsafeBytes, 0, $unsafeBytes.Length)
            }

            $duplicateJsonBytes = [Text.Encoding]::UTF8.GetBytes(
                '{"status":409,"code":"J7_IMPORT_CONFLICT","code":"J7_IMPORT_CONFLICT"}')
            $duplicateJsonResponse = [pscustomobject]@{
                StatusCode = 409
                Headers = [Collections.Generic.Dictionary[string, string]]::new(
                    [StringComparer]::OrdinalIgnoreCase)
                BodyBytes = $duplicateJsonBytes
            }
            $duplicateJsonResponse.Headers.Add(
                'Content-Type',
                'application/problem+json')
            try {
                $duplicateJsonFailed = $false
                try {
                    [void](Get-WO036SafeProblemCode -Response $duplicateJsonResponse)
                }
                catch {
                    $duplicateJsonFailed = $true
                }
                $duplicateJsonFailed | Should Be $true
            }
            finally {
                [Array]::Clear($duplicateJsonBytes, 0, $duplicateJsonBytes.Length)
            }
        }

        It 'builds an exact redacted collision-audit correlation proof with one mocked query' {
            Mock Invoke-WO036Psql {
                '{"divergenceCount":1,"correlationCount":1,"reasonCount":1}'
            }
            $state = [pscustomobject]@{
                Config = [pscustomobject]@{
                    receiver = [pscustomobject]@{ databaseName = 'betting_wo036' }
                }
            }
            $id = [guid]::NewGuid()
            $fileHash = 'a' * 64
            $dataHash = 'b' * 64
            $proof = Get-WO036CollisionAuditProof -State $state -ExportId $id `
                -MutatedFileSha256 $fileHash -DataSha256 $dataHash
            $proof.correlationCount | Should Be 1
            $proof.reasonCode | Should Be 'EXPORT_ID_DIVERGENCE'
            $proof.idempotencyKeySha256 | Should Match '^[0-9a-f]{64}$'
            Assert-MockCalled Invoke-WO036Psql 1 -Exactly -Scope It
        }
    }
}
