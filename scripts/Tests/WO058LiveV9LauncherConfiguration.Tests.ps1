$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $scriptsRoot
$scriptPath = Join-Path $scriptsRoot 'Show-LiveGroupedV9LauncherConfiguration.ps1'
$profilePath = Join-Path $repositoryRoot 'docs\validation\WO058-GROUPED-LIVE-V9-SUSPENDED-20260911.json'

Describe 'WO-058 V9 launcher configuration display' {
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

    It 'renders exactly the nine reviewed V9 variables without changing the process environment' {
        $expectedSha256 = (Get-FileHash -LiteralPath $profilePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $before = [Environment]::GetEnvironmentVariable('SOFASCORE_LIVE_GROUPED_V9_QUALIFICATION_SHA256', 'Process')

        $output = @(& $scriptPath -OutputFormat Eclipse)
        $mapEntries = @($output | Where-Object { $_ -like '<mapEntry *' })

        $mapEntries.Count | Should Be 9
        ($mapEntries -contains ('<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_QUALIFICATION_SHA256" value="{0}"/>' -f $expectedSha256)) | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_J4_REQUEST_ENVELOPE" value="300ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_J4_PROCESSING_ENVELOPE" value="500ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_INCIDENTS_REQUEST_ENVELOPE" value="300ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_INCIDENTS_PROCESSING_ENVELOPE" value="400ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_STATISTICS_REQUEST_ENVELOPE" value="350ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_STATISTICS_PROCESSING_ENVELOPE" value="400ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_LINEUPS_REQUEST_ENVELOPE" value="300ms"/>') | Should Be $true
        ($mapEntries -contains '<mapEntry key="SOFASCORE_LIVE_GROUPED_V9_LINEUPS_PROCESSING_ENVELOPE" value="450ms"/>') | Should Be $true
        ($output -contains 'V9_LAUNCHER_CONFIGURATION=REVIEW_REQUIRED') | Should Be $true
        ($output -contains 'V9_LIVE_OPT_IN_CHANGED=NO') | Should Be $true
        ($output -contains 'V9_PROVIDER_CALLS_EXECUTED=NO') | Should Be $true
        [Environment]::GetEnvironmentVariable('SOFASCORE_LIVE_GROUPED_V9_QUALIFICATION_SHA256', 'Process') | Should Be $before
    }
}
