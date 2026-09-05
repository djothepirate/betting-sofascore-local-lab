[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$tokens = $null
$errors = $null
$path = Join-Path $PSScriptRoot 'Invoke-J6BackupRestoreLoopbackQualification.ps1'
$ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'DIAGNOSTIC_CONTRACT_PARSE_FAILED' }
foreach ($name in @('Write-J6CtrlBreakNativeEvidence','Assert-J6Qualification','New-J6QualificationSanitizedException')) {
    $function = $ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]}, $true) | Where-Object Name -eq $name
    if (@($function).Count -ne 1) { throw 'DIAGNOSTIC_FUNCTION_AMBIGUOUS' }
    . ([scriptblock]::Create($function.Extent.Text))
}
$good = 'J6_CTRL_BREAK_NATIVE_SIGNAL=SENT'
$output = @(& { Write-J6CtrlBreakNativeEvidence -Lines @($good,' J6_CTRL_BREAK_NATIVE_SIGNAL=SENT','J6_CTRL_BREAK_NATIVE_SIGNAL=SENT SECRET','J6_CTRL_BREAK_NATIVE_FAILURE=UNKNOWN','C:\private\SYNTHETIC_SECRET',[InvalidOperationException]::new('SYNTHETIC_SECRET')) } 6>&1)
if ($output.Count -ne 1 -or $output[0].ToString() -cne $good) { throw 'DIAGNOSTIC_ALLOWLIST_FAILED' }
$source = [IO.File]::ReadAllText($path)
$markers = @([regex]::Matches($source,"-FailureMarker '([^']+)'") | ForEach-Object { $_.Groups[1].Value })
if ($markers.Count -ne 17 -or @($markers | Sort-Object -Unique).Count -ne 17) { throw 'DIAGNOSTIC_GATE_COVERAGE_FAILED' }
foreach ($match in [regex]::Matches($source, 'J6_CTRL_BREAK_[A-Z_]+=[A-Z_]+')) {
    if ($match.Value.Contains('HAR')) { throw 'DIAGNOSTIC_FORBIDDEN_VOCABULARY' }
}
foreach ($marker in $markers) {
    $caught = $null
    try { Assert-J6Qualification -Condition $false -Message 'SYNTHETIC_SECRET' -FailureMarker $marker } catch { $caught=$_.Exception }
    if ($null -eq $caught -or $caught.Data['J6QualificationClassification'] -cne $marker -or $caught.ToString().Contains('SYNTHETIC_SECRET')) { throw 'DIAGNOSTIC_FAILURE_SANITIZATION_FAILED' }
    Assert-J6Qualification -Condition $true -Message 'SYNTHETIC_SECRET' -FailureMarker $marker
}
'J6_DIAGNOSTIC_NATIVE_ALLOWLIST=PASS'
'J6_DIAGNOSTIC_FAILURE_GATES=17'
'J6_DIAGNOSTIC_FAILURE_SANITIZATION=PASS'
