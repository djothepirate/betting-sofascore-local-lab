$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $scriptsRoot
$scriptPath = Join-Path $scriptsRoot 'Show-LiveGroupedV10LauncherConfiguration.ps1'
$profilePath = Join-Path $repositoryRoot 'docs\validation\WO058-GROUPED-LIVE-V10-PROFILE-20260912.json'

Describe 'WO-058 V10 launcher configuration display' {
    It 'has a parseable display-only script with no launcher, environment, process or network mutation commands' {
        $tokens = $null
        $errors = $null
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
        $errors.Count | Should Be 0

        $commands = $ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.CommandAst] }, $true) |
            ForEach-Object { $_.GetCommandName() }
        $forbiddenCommands = @(
            'Add-Content', 'Invoke-RestMethod', 'Invoke-WebRequest', 'mvnw.cmd', 'New-Item',
            'Out-File', 'Remove-Item', 'Set-Content', 'Set-Item', 'Set-Variable', 'Start-Process'
        )
        foreach ($command in $commands) {
            ($forbiddenCommands -contains $command) | Should Be $false
        }

        $source = Get-Content -LiteralPath $scriptPath -Raw
        $source | Should Not Match 'SetEnvironmentVariable'
        $source | Should Not Match 'EnvironmentVariableTarget'
        $source | Should Not Match 'SOFASCORE_LIVE_ENABLED.*='
    }

    It 'renders exactly the nine reviewed V10 variables, the independent qualified binding and the requested local guards' {
        $expectedSha256 = (Get-FileHash -LiteralPath $profilePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $before = [Environment]::GetEnvironmentVariable('SOFASCORE_LIVE_GROUPED_V10_QUALIFICATION_SHA256', 'Process')

        $output = @(& $scriptPath -OutputFormat Eclipse)
        $mapEntries = @($output | Where-Object { $_ -like '<mapEntry *' })

        $mapEntries.Count | Should Be 9
        ($mapEntries -contains ('<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_QUALIFICATION_SHA256" value="{0}"/>' -f $expectedSha256)) | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_J4_REQUEST_ENVELOPE" value="300ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_J4_PROCESSING_ENVELOPE" value="500ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_INCIDENTS_REQUEST_ENVELOPE" value="300ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_INCIDENTS_PROCESSING_ENVELOPE" value="400ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_STATISTICS_REQUEST_ENVELOPE" value="350ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_STATISTICS_PROCESSING_ENVELOPE" value="400ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_LINEUPS_REQUEST_ENVELOPE" value="300ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V10_LINEUPS_PROCESSING_ENVELOPE" value="450ms"/>') | Should Be $true
        ($output -contains 'V10_QUALIFICATION_PROVENANCE=INDEPENDENT_V10_COMPILED_CLASS_BINDING') | Should Be $true
        ($output -contains 'V10_PROFILE_STATUS=QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE') | Should Be $true
        ($output -contains 'V10_COMPILED_CLASS_HASH_BINDING=QUALIFIED') | Should Be $true
        ($output -contains 'V10_MAXIMUM_SELECTED_MATCHES=8') | Should Be $true
        ($output -contains 'V10_NOMINAL_ENDPOINT_CALLS_PER_MINUTE=32') | Should Be $true
        ($output -contains 'V10_DURABLE_MAXIMUM_DEPARTURES_PER_60_SECONDS=35') | Should Be $true
        ($output -contains 'V10_DURABLE_MAXIMUM_DEPARTURES_PER_HOUR=2100') | Should Be $true
        ($output -contains 'V10_LAUNCHER_CONFIGURATION=REVIEW_REQUIRED') | Should Be $true
        ($output -contains 'V10_LIVE_OPT_IN_CHANGED=NO') | Should Be $true
        ($output -contains 'V10_PROVIDER_CALLS_EXECUTED=NO') | Should Be $true
        [Environment]::GetEnvironmentVariable('SOFASCORE_LIVE_GROUPED_V10_QUALIFICATION_SHA256', 'Process') | Should Be $before
    }

    It 'renders only the reviewed V10 compiled class SHA-256 binding' {
        $profile = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json

        $profile.status | Should Be 'QUALIFIED_LOCAL_REPLAY_WITH_STATED_SCOPE'
        $profile.qualifiedCapacity | Should Be 8
        $profile.candidateConfiguration.maximumSelectedMatches | Should Be 8
        $profile.productionClassHashBinding.status | Should Be 'QUALIFIED_V10_COMPILED_CLASS_BINDING'
        @($profile.productionClassHashBinding.requiredClasses) | Should Be @(
            'com.bettingproject.sofascorelocal.application.live.LiveAdmissionPolicyV10',
            'com.bettingproject.sofascorelocal.application.live.GroupedLiveAdmissionSimulationV10',
            'com.bettingproject.sofascorelocal.application.live.V10GroupedScheduleProfile',
            'com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData$DepartureProfile'
        )
        @($profile.productionClassSha256.PSObject.Properties.Name) | Should Be @(
            'com.bettingproject.sofascorelocal.application.live.LiveAdmissionPolicyV10',
            'com.bettingproject.sofascorelocal.application.live.GroupedLiveAdmissionSimulationV10',
            'com.bettingproject.sofascorelocal.application.live.V10GroupedScheduleProfile',
            'com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData$DepartureProfile'
        )
        foreach ($property in $profile.productionClassSha256.PSObject.Properties) {
            $property.Value | Should Match '^[0-9a-f]{64}$'
        }
    }
}
