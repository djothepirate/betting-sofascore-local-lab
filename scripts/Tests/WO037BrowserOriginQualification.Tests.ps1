$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pomPath = Join-Path $repositoryRoot 'pom.xml'
$scriptPath = Join-Path $repositoryRoot `
    'scripts\Invoke-J7BrowserOriginLoopbackQualification.ps1'
$testPath = Join-Path $repositoryRoot `
    'src\j7-browser-origin-qualification-test\java\com\bettingproject\sofascorelocal\qualification\J7BrowserOriginLoopbackQualificationIT.java'

Describe 'WO-037 explicit native-browser qualification surface' {
    BeforeAll {
        $pom = Get-Content -LiteralPath $pomPath -Raw
        $launcher = Get-Content -LiteralPath $scriptPath -Raw
        $qualification = Get-Content -LiteralPath $testPath -Raw
    }

    It 'parses the explicit launcher without executing it' {
        $errors = $null
        [void][System.Management.Automation.Language.Parser]::ParseFile(
            $scriptPath,
            [ref]$null,
            [ref]$errors)
        @($errors).Count | Should Be 0
    }

    It 'keeps Playwright and the browser test source behind one opt-in Maven profile' {
        $pom | Should Match '<id>j7-browser-origin-loopback-qualification</id>'
        $pom | Should Match '<scope>test</scope>'
        $pom | Should Match 'src/j7-browser-origin-qualification-test/java'
        $pom | Should Match 'J7BrowserOriginLoopbackQualificationIT\.java'
        $launcher | Should Match "'-Pj7-browser-origin-loopback-qualification'"
        $launcher | Should Match '(?s)-DskipTests\s*`\s*clean\s*`\s*test-compile'
        $launcher | Should Match 'failsafe:integration-test@j7-browser-origin-loopback-qualification'
    }

    It 'allows browser traffic only to the exact Local Lab loopback origin' {
        $qualification | Should Match 'http://127\.0\.0\.1:8087'
        $qualification | Should Match 'context\.route\("\*\*/\*"'
        $qualification | Should Match 'nonLoopbackRequests\.incrementAndGet\(\)'
        $qualification | Should Match 'route\.abort\(\)'
        $qualification | Should Not Match 'https://'
        $qualification | Should Not Match 'sofascore\.com'
        $qualification | Should Not Match '127\.0\.0\.1:8444'
    }

    It 'uses a fresh non-persistent context with downloads and service workers disabled' {
        $qualification | Should Match 'browser\.newContext\('
        $qualification | Should Match 'setAcceptDownloads\(false\)'
        $qualification | Should Match 'setServiceWorkers\(ServiceWorkerPolicy\.BLOCK\)'
        $qualification | Should Not Match '(?i)recordHar|recordVideo|storageState|tracing\(|screenshot\('
    }

    It 'captures the browser process tree before work and again on every context exit' {
        ([regex]::Matches(
                $qualification,
                'browserProcesses\.addAll\(captureDescendants\(\)\)')).Count |
            Should Be 2
        $qualification | Should Match '(?s)finally\s*\{\s*browserProcesses\.addAll\(captureDescendants\(\)\)'
        $qualification | Should Match '(?s)finally\s*\{.*assertOwnedProcessesExited\(browserProcesses\)'
    }

    It 'qualifies only preview and prepare and never invokes execution or reconciliation' {
        $qualification | Should Match 'PREVIEW_PATH'
        $qualification | Should Match 'PREPARE_PATH'
        $qualification | Should Match 'Origin'
        $qualification | Should Match 'List\.of\("null"\)'
        $qualification | Should Match 'deniedResponse\.status\(\)\)\.isEqualTo\(403\)'
        $qualification | Should Match 'verify\(runtimeService, never\(\)\)\.deliver'
        $qualification | Should Match 'verify\(ledgerStore, never\(\)\)\.claim'
        $qualification | Should Match 'deliveryExecuteRequestCount\(\)\)\.isZero\(\)'
        $qualification | Should Match 'reconciliationRequestCount\(\)\)\.isZero\(\)'
    }

    It 'requires both application and receiver ports to be absent before and after the run' {
        ([regex]::Matches($launcher, 'Assert-PortFree -Port 8087')).Count |
            Should Be 2
        ([regex]::Matches($launcher, 'Assert-PortFree -Port 8444')).Count |
            Should Be 2
        $launcher | Should Match '(?s)finally\s*\{.*Assert-PortFree -Port 8087.*Assert-PortFree -Port 8444'
    }

    It 'invalidates the exact prior Failsafe report before the new browser run' {
        $launcher | Should Match 'TEST-com\.bettingproject\.sofascorelocal\.qualification\.J7BrowserOriginLoopbackQualificationIT\.xml'
        $launcher | Should Match '(?s)Test-Path -LiteralPath \$reportPath.*Remove-Item -LiteralPath \$reportPath -Force'
    }
}
