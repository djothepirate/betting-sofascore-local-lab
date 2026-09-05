[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$names = @('OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID',
    'OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_OWNER_GO_DOCUMENT_SHA256')
$scripts = @('Start-Local.ps1', 'Start-J3PlaywrightLocal.ps1', 'Start-J4PlaywrightLocal.ps1',
    'Start-J5PlaywrightLocal.ps1', 'Export-J8Benchmark.ps1', 'Invoke-J6Retention.ps1')
$results = [Collections.Generic.List[object]]::new()

foreach ($file in $scripts) {
    $path = Join-Path (Join-Path $root 'scripts') $file
    $tokens = $null; $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
    if ($errors.Count) { throw 'WO050_SCRIPT_PARSE_FAILED' }
    $assignment = $ast.Find({ param($node)
        $node -is [Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left.Extent.Text -in @('$environmentNames', '$optionalIntegrationEnvironment')
    }, $true)
    if ($null -eq $assignment) { throw 'WO050_ENVIRONMENT_LIST_NOT_FOUND' }
    # Select the original try/finally that restores the saved process environment.
    $boundary = $ast.Find({ param($node)
        $node -is [Management.Automation.Language.TryStatementAst] -and
        $null -ne $node.Finally -and $node.Finally.Extent.Text.Contains('SetEnvironmentVariable')
    }, $true)
    if ($null -eq $boundary -or $boundary.Extent.StartOffset -le $assignment.Extent.StartOffset) {
        throw 'WO050_RESTORATION_BOUNDARY_NOT_FOUND'
    }
    $start = $assignment.Extent.StartOffset
    $source = $ast.Extent.Text.Substring($start, $boundary.Extent.EndOffset - $start)
    # Only business call-operator commands are replaced; all environment assignments,
    # snapshot loops, exception flow and finally blocks remain the source's exact text.
    $calls = @($boundary.FindAll({ param($node)
        $node -is [Management.Automation.Language.CommandAst] -and
        $node.InvocationOperator -eq [Management.Automation.Language.TokenKind]::Ampersand
    }, $true))
    if ($calls.Count -lt 1 -or $calls.Count -gt 2) { throw 'WO050_UNEXPECTED_BUSINESS_CALL_COUNT' }
    foreach ($call in ($calls | Sort-Object { $_.Extent.StartOffset } -Descending)) {
        $original = $call.Extent.Text
        if ($original.StartsWith('& .\mvnw.cmd') -and $original.Contains('spring-boot:run')) {
            $replacement = 'Invoke-WO050BusinessDouble'
        }
        elseif ($original -eq "& (Join-Path `$PSScriptRoot 'Start-Local.ps1')") {
            $replacement = 'Invoke-WO050BootstrapDouble'
        }
        else { throw 'WO050_UNEXPECTED_BUSINESS_CALL' }
        $offset = $call.Extent.StartOffset - $start
        $source = $source.Remove($offset, $call.Extent.EndOffset - $call.Extent.StartOffset).Insert($offset, $replacement)
    }
    # Fail closed if the extracted boundary ever introduces another command capable
    # of escaping the fixture. Do not execute preflight, Docker, Maven or filesystem I/O.
    if ($file -eq 'Invoke-J6Retention.ps1') {
        $previewAst = [Management.Automation.Language.Parser]::ParseInput($source, [ref]$tokens, [ref]$errors)
        $executeBranch = $previewAst.Find({ param($node)
            $node -is [Management.Automation.Language.IfStatementAst] -and
            $node.Clauses[0].Item1.Extent.Text -eq "`$Mode -eq 'Execute'"
        }, $true)
        if ($null -eq $executeBranch) { throw 'WO050_RETENTION_EXECUTE_BRANCH_NOT_FOUND' }
        $source = $source.Remove($executeBranch.Extent.StartOffset,
            $executeBranch.Extent.EndOffset - $executeBranch.Extent.StartOffset).Insert(
            $executeBranch.Extent.StartOffset, "if (`$Mode -ne 'Preview') { throw 'WO050_PREVIEW_ONLY' }")
    }
    $safeAst = [Management.Automation.Language.Parser]::ParseInput($source, [ref]$tokens, [ref]$errors)
    if ($errors.Count) { throw 'WO050_EXTRACTED_PARSE_FAILED' }
    $allowed = @('Set-StrictMode', 'Set-ProcessEnvironment', 'Write-Host', 'Push-Location',
        'Pop-Location', 'Remove-Item', 'Invoke-WO050BusinessDouble', 'Invoke-WO050BootstrapDouble',
        'IsNullOrWhiteSpace')
    foreach ($command in $safeAst.FindAll({ param($node)
        $node -is [Management.Automation.Language.CommandAst]
    }, $true)) {
        if ($command.GetCommandName() -notin $allowed) { throw 'WO050_UNEXPECTED_COMMAND' }
    }
    foreach ($mask in 0..3) {
        foreach ($outcome in @('success', 'native-failure', 'exception')) {
            & {
                param($source, $file, $mask, $outcome, $names, $results)
                $originalEnvironment = @{}
                foreach ($entry in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) {
                    $originalEnvironment[$entry.Key] = $entry.Value
                }
                $repositoryRoot = $root
                $browserCache = 'WO050_SYNTHETIC_BROWSER_CACHE_NOT_USED'
                $workerJar = 'WO050_SYNTHETIC_WORKER_NOT_USED.jar'
                $fromInstant = '2030-01-01T00:00:00Z'; $toInstant = '2030-01-02T00:00:00Z'
                $asOfInstant = '2030-01-03T00:00:00Z'; $Mode = 'Preview'
                $script:wo050Capture = $null; $script:wo050Calls = 0
                $applicationExitCode = 1
                function Push-Location { param($Path) }
                function Pop-Location { }
                function Write-Host { param($Object) }
                function Invoke-WO050BootstrapDouble { }
                function Invoke-WO050BusinessDouble {
                    $script:wo050Calls++
                    $script:wo050Capture = @{}
                    foreach ($name in @('OPTIONAL_INTEGRATION_ENABLED', 'OPTIONAL_INTEGRATION_EXECUTION_MODE',
                        'OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED') + $names) {
                        $script:wo050Capture[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
                    }
                    if ($outcome -eq 'exception') { throw 'WO050_INJECTED_BUSINESS_FAILURE' }
                    $global:LASTEXITCODE = $(if ($outcome -eq 'native-failure') { 19 } else { 0 })
                }
                try {
                    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
                    if ($mask -band 1) { [Environment]::SetEnvironmentVariable($names[0], '00000000-0000-4000-8000-000000000050', 'Process') }
                    if ($mask -band 2) { [Environment]::SetEnvironmentVariable($names[1], ('b' * 64), 'Process') }
                    $before = @{}
                    foreach ($name in $names) { $before[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
                    $caught = $false
                    try { & ([scriptblock]::Create($source)) }
                    catch {
                        $caught = $true
                        if ($outcome -eq 'success') { throw 'WO050_UNEXPECTED_SUCCESS_PATH_FAILURE' }
                        if ($outcome -eq 'exception' -and $_.Exception.Message -ne 'WO050_INJECTED_BUSINESS_FAILURE') {
                            throw 'WO050_UNEXPECTED_EXCEPTION'
                        }
                    }
                    if ($script:wo050Calls -ne 1) { throw 'WO050_BUSINESS_DOUBLE_NOT_CALLED_EXACTLY_ONCE' }
                    if ($outcome -eq 'exception' -and -not $caught) { throw 'WO050_EXCEPTION_WAS_SWALLOWED' }
                    foreach ($name in $names) {
                        if (-not [string]::IsNullOrEmpty($script:wo050Capture[$name])) { throw "WO050_AMBIENT_GO_LEAK:${file}:$mask" }
                        if ([Environment]::GetEnvironmentVariable($name, 'Process') -cne $before[$name]) {
                            throw 'WO050_PARENT_ENVIRONMENT_NOT_RESTORED'
                        }
                    }
                    if ($script:wo050Capture['OPTIONAL_INTEGRATION_ENABLED'] -cne 'false' -or
                        $script:wo050Capture['OPTIONAL_INTEGRATION_EXECUTION_MODE'] -cne 'DISABLED' -or
                        $script:wo050Capture['OPTIONAL_INTEGRATION_REMOTE_DELIVERY_AUTHORIZED'] -cne 'false') {
                        throw 'WO050_INTEGRATION_NOT_DISABLED'
                    }
                    $results.Add([ordered]@{script=$file; mask=$mask; outcome=$outcome;
                        restored=$true; environment=$script:wo050Capture})
                }
                finally {
                    foreach ($key in @([Environment]::GetEnvironmentVariables('Process').Keys)) {
                        if (-not $originalEnvironment.ContainsKey($key)) { [Environment]::SetEnvironmentVariable($key, $null, 'Process') }
                    }
                    foreach ($key in $originalEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $originalEnvironment[$key], 'Process') }
                }
            } $source $file $mask $outcome $names $results
        }
    }
}
ConvertTo-Json -InputObject @($results.ToArray()) -Depth 5 -Compress
