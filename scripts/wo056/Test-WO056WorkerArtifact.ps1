[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resolver = Join-Path $repositoryRoot 'scripts\Resolve-ProviderWorkerJar.ps1'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('wo056 worker artifact ' + [Guid]::NewGuid().ToString('N'))
$expectedFixture = [IO.Path]::GetFullPath($fixture)
try {
    New-Item -ItemType Directory -Path (Join-Path $fixture 'target') | Out-Null
    $staleName = 'betting-sofascore-local-lab-0.1.0-SNAPSHOT-provider-playwright-worker.jar'
    [IO.File]::WriteAllText((Join-Path $fixture "target\$staleName"), 'synthetic stale jar')
    $finalName = 'betting-sofascore-local-lab-0.1.0-rc.1-SNAPSHOT'
    $caught = $false
    try { & $resolver -RepositoryRoot $fixture -FinalName $finalName | Out-Null }
    catch { $caught = $true }
    if (-not $caught) { throw 'WO056_STALE_WORKER_ACCEPTED' }
    $expectedJar = Join-Path $fixture "target\${finalName}-provider-playwright-worker.jar"
    [IO.File]::WriteAllText($expectedJar, 'synthetic current jar')
    $actual = & $resolver -RepositoryRoot $fixture -FinalName $finalName
    if ($actual -cne $expectedJar) { throw 'WO056_EXACT_WORKER_PATH_LOST' }
    foreach ($invalid in @('../outside', '..\outside', 'C:\outside', '[INFO] diagnostic',
            'artifact*', 'artifact.', "artifact`nother")) {
        $caught = $false
        try { & $resolver -RepositoryRoot $fixture -FinalName $invalid | Out-Null }
        catch { $caught = $true }
        if (-not $caught) { throw 'WO056_INVALID_FINALNAME_ACCEPTED' }
    }
    foreach ($launcher in @('Start-J3PlaywrightLocal.ps1', 'Start-J4PlaywrightLocal.ps1',
            'Start-J5PlaywrightLocal.ps1')) {
        $source = Get-Content -Raw (Join-Path $repositoryRoot "scripts\$launcher")
        if ($source -notmatch 'Dexpression=project.build.finalName' -or
            $source -notmatch 'Resolve-ProviderWorkerJar.ps1' -or
            $source -match '0[.]1[.]0-SNAPSHOT-provider-playwright-worker') {
            throw 'WO056_LAUNCHER_NOT_USING_EXACT_MAVEN_ARTIFACT'
        }
    }
    'WO056_WORKER_ARTIFACT=PASS_OFFLINE'
}
finally {
    if (Test-Path -LiteralPath $fixture) {
        $resolvedFixture = (Resolve-Path -LiteralPath $fixture).Path
        if ($resolvedFixture -cne $expectedFixture -or
            -not [IO.Path]::GetFileName($resolvedFixture).StartsWith('wo056 worker artifact ')) {
            throw 'WO056_UNEXPECTED_CLEANUP_PATH'
        }
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
