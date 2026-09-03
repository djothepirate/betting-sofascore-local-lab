$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composePath = Join-Path $repositoryRoot 'compose.wo036.yml'
$initializePath = Join-Path $repositoryRoot 'scripts\Initialize-J7LocalE2eInfrastructure.ps1'
$removePath = Join-Path $repositoryRoot 'scripts\Remove-J7LocalE2eInfrastructure.ps1'
$compose = Get-Content -LiteralPath $composePath -Raw
$initialize = Get-Content -LiteralPath $initializePath -Raw
$remove = Get-Content -LiteralPath $removePath -Raw

Describe 'WO-036 isolated PostgreSQL compose model' {
    It 'uses the two exact qualified PostgreSQL images' {
        $compose | Should Match '(?m)^\s+image: postgres:18\.4-alpine$'
        $compose | Should Match '(?m)^\s+image: postgres:17-alpine$'
    }

    It 'binds only the two exact loopback database ports' {
        $bindings = [regex]::Matches($compose, '(?m)^\s+- "([^"]+):5432"$')
        $bindings.Count | Should Be 2
        $bindings[0].Groups[1].Value | Should Be '127.0.0.1:5432'
        $bindings[1].Groups[1].Value | Should Be '127.0.0.1:5433'
        $compose | Should Not Match '0\.0\.0\.0'
    }

    It 'has no persistent primary name and never restarts automatically' {
        $compose | Should Not Match '(?m)^\s*container_name:'
        $compose | Should Not Match '(?m)^\s+name:'
        ([regex]::Matches($compose, '(?m)^\s+restart: "no"$').Count) | Should Be 2
        ([regex]::Matches($compose, '(?m)^\s+pull_policy: never$').Count) | Should Be 2
        ([regex]::Matches($compose, '(?m)^\s+healthcheck:$').Count) | Should Be 2
    }

    It 'requires private values and attaches exact ownership labels' {
        $compose | Should Match '\$\{WO036_LOCAL_LAB_POSTGRES_PASSWORD:\?'
        $compose | Should Match '\$\{WO036_BETTING_PROJECT_POSTGRES_PASSWORD:\?'
        $compose | Should Match 'com\.bettingproject\.wo036\.run-id:'
        $compose | Should Match 'com\.bettingproject\.wo036\.ownership-sha256:'
    }
}

Describe 'WO-036 initialization and cleanup scripts' {
    It 'parses both scripts without PowerShell syntax errors' {
        foreach ($path in @($initializePath, $removePath)) {
            $tokens = $null
            $parseErrors = $null
            [System.Management.Automation.Language.Parser]::ParseFile(
                $path,
                [ref]$tokens,
                [ref]$parseErrors) | Out-Null
            $parseErrors.Count | Should Be 0
        }
    }

    It 'creates private per-run state and synthetic PKI outside Git' {
        $initialize | Should Match "LocalApplicationData"
        $initialize | Should Match "WO-SS-20260902-036"
        $initialize | Should Match 'SetAccessRuleProtection\(\$true, \$false\)'
        $initialize | Should Match "SAN=ip:127\.0\.0\.1"
        $initialize | Should Match "EKU=serverAuth"
        $initialize | Should Match "1\.3\.6\.1\.5\.5\.7\.3\.2"
        $initialize | Should Match "KeyExportPolicy NonExportable"
        $initialize | Should Match "Cert:\\CurrentUser\\Root"
        $initialize | Should Match "Cert:\\CurrentUser\\My"
    }

    It 'records the shared campaign state without printing secrets or fingerprints' {
        $initialize | Should Match "campaignId ="
        $initialize | Should Match "ownershipSha256 ="
        $initialize | Should Match "receiver = \[pscustomobject\]"
        $initialize | Should Match "localLab = \[pscustomobject\]"
        $initialize | Should Match "clientCertificateSha256 ="
        $initialize | Should Match "WO036_SECRETS_DISPLAYED=NO"
        $initialize | Should Not Match "Write-(Host|Output).*DatabasePassword"
        $initialize | Should Not Match "Write-(Host|Output).*CertificateSha256"
    }

    It 'creates the shared tools lock before exposing state and cleanup acquires it exclusively' {
        $initialize | Should Match "\.wo036-tools\.lock"
        $initialize | Should Match "\[System\.IO\.FileMode\]::CreateNew"
        $initialize | Should Match "\[System\.IO\.FileShare\]::None"
        $remove | Should Match "\.wo036-tools\.lock"
        $remove | Should Match "\[System\.IO\.FileMode\]::Open"
        $remove | Should Match "\[System\.IO\.FileShare\]::None"
        $remove | Should Match "cleanupStatus = 'IN_PROGRESS'"
        $remove | Should Match "WO036_SHARED_TOOLS_LOCK_DELETED_LAST=YES"
        $initialize.IndexOf("`$script:phase = 'PRIVATE_TOOLS_LOCK'") |
            Should BeLessThan $initialize.IndexOf("`$script:phase = 'PRIVATE_STATE'")
        $initializeLockCreate = $initialize.IndexOf('$script:toolsLock = [System.IO.FileStream]::new')
        $initializePass = $initialize.IndexOf("`$script:state.InitializationStatus = 'PASS'")
        $initialize.Substring(
            $initializeLockCreate,
            $initializePass - $initializeLockCreate) |
            Should Not Match '\$script:toolsLock\.Dispose\('
        $initialize.IndexOf('    Exit-PrivateToolsLock', $initializePass) |
            Should BeGreaterThan $initialize.IndexOf(
                "Write-Output 'WO036_PROVIDER_NETWORK_OPENED=NO'", $initializePass)
        $initialize.IndexOf("`$script:state.cleanupStatus = 'IN_PROGRESS'") |
            Should BeLessThan $initialize.LastIndexOf('            Exit-PrivateToolsLock')
        $remove.IndexOf('[System.IO.FileMode]::Open') |
            Should BeLessThan $remove.IndexOf('$state = Get-Content')
        $finalLockGuardIndex = $remove.IndexOf(
            'The shared tools lock is not the final private-root file.')
        $remove.IndexOf('$script:toolsLock.Dispose()', $finalLockGuardIndex) |
            Should BeLessThan $remove.IndexOf('Remove-Item -LiteralPath $resolvedToolsLockPath -Force')
        $remove.IndexOf('Remove-Item -LiteralPath $resolvedStatePath -Force') |
            Should BeLessThan $remove.IndexOf('Remove-Item -LiteralPath $resolvedToolsLockPath -Force')
    }

    It 'pins and revalidates the exact Docker executable used by cleanup' {
        $initialize | Should Match "dockerExecutableSha256"
        $initialize | Should Match "DockerDesktop\\resources\\bin"
        $remove | Should Match "dockerExecutableSha256"
        $remove | Should Match "Assert-TrustedDockerExecutablePath"
        $remove | Should Match "The recorded Docker executable hash changed"
    }

    It 'pins one exact local Docker named-pipe endpoint for initialization and cleanup' {
        $initialize | Should Match 'docker-endpoint\.private\.txt'
        $initialize | Should Match '\^npipe:////\[\.\]/pipe/\[A-Za-z0-9\._-\]\+\$'
        $initialize | Should Match 'resolvedDockerEndpoint \+ "`n"'
        $initialize | Should Match 'Protect-PrivateFile -Path \$dockerEndpointPath'
        $remove | Should Match 'docker-endpoint\.private\.txt'
        $remove | Should Match 'Get-PinnedDockerArguments -State \$State'
        $remove | Should Not Match 'DOCKER_CONTEXT|DOCKER_HOST|context inspect'
    }

    It 'refuses PID-only cleanup and validates exact Docker ownership labels' {
        $remove | Should Match "PID-only termination is forbidden"
        $remove | Should Match "CommandLineSha256"
        $remove | Should Match "com\.bettingproject\.work-order"
        $remove | Should Match "com\.bettingproject\.wo036\.run-id"
        $remove | Should Match "com\.bettingproject\.wo036\.ownership-sha256"
        $remove | Should Match 'Process start time changed before bounded forced termination'
        $remove | Should Match 'exact owned-process CIM postcheck failed'
        $remove | Should Match 'identity changed during the zero-residue postcheck'
    }

    It 'normalizes cleanup timestamps without requiring PowerShell 7.5' {
        $remove | Should Match 'function ConvertTo-ExactUtcDateTime'
        ([regex]::Matches($remove,
            'ConvertTo-ExactUtcDateTime').Count) | Should Be 3
        $remove | Should Not Match 'ConvertFrom-Json[^\r\n]*-DateKind'
    }

    It 'never performs broad Docker or filesystem cleanup' {
        $remove | Should Not Match '(?i)down\s+(?:--volumes|-v)(?:\s|$)'
        $remove | Should Not Match '(?i)volume\s+prune'
        $remove | Should Not Match '(?i)system\s+prune'
        $remove | Should Match "Assert-ExactPath"
        $remove | Should Match "Assert-NoDescendantReparsePoint"
        $remove | Should Match 'Remove-Item -LiteralPath \$child\.FullName -Recurse -Force'
        $remove | Should Match 'Remove-Item -LiteralPath \$freshRunRoot -Force'
        $remove | Should Not Match 'Remove-Item -LiteralPath \$freshRunRoot -Recurse'
    }

    It 'attests zero process, Docker, certificate and listener residue before root deletion' {
        $remove | Should Match 'Get-ExactOwnedProcessResidualCount'
        $remove | Should Match 'Get-OwnedCertificateResidualCount'
        $remove | Should Match 'WO036_CLEANUP_CONTAINER_RESIDUAL_COUNT='
        $remove | Should Match 'WO036_CLEANUP_VOLUME_RESIDUAL_COUNT='
        foreach ($port in @(8087, 8444, 5432, 5433)) {
            $remove | Should Match "WO036_CLEANUP_LISTENER_${port}_COUNT="
        }
        $remove | Should Match 'WO036_CLEANUP_ZERO_RESOURCE_PRE_ROOT=PASS'
        $remove | Should Match 'WO036_CLEANUP_ATTESTATION=PASS'
        $remove.IndexOf('WO036_CLEANUP_ATTESTATION=PASS') |
            Should BeGreaterThan $remove.IndexOf('if (-not $rootRemoved)')
    }

    It 'contains no provider endpoint or non-loopback network target' {
        ($compose + $initialize + $remove) | Should Not Match '(?i)api\.sofascore\.com'
        ($compose + $initialize + $remove) | Should Not Match '51\.255\.167\.32'
        ($compose + $initialize + $remove) | Should Not Match '0\.0\.0\.0'
    }
}
