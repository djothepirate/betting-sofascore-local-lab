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

# Observe the marker before the producer resumes, then retain it on interruption.
$observed = [Collections.Generic.List[string]]::new()
$interruption = $null
try {
    & {
        $good
        if ($observed.Count -ne 1) { throw 'DIAGNOSTIC_RELAY_BUFFERED' }
        'SYNTHETIC_SECRET'
        throw 'DIAGNOSTIC_SYNTHETIC_INTERRUPTION'
    } | Write-J6CtrlBreakNativeEvidence 6>&1 | ForEach-Object { $observed.Add($_.ToString()) }
} catch { $interruption = $_.Exception.Message }
if ($interruption -cne 'DIAGNOSTIC_SYNTHETIC_INTERRUPTION' -or $observed.Count -ne 1 -or $observed[0] -cne $good) {
    throw 'DIAGNOSTIC_INTERRUPTED_RELAY_FAILED'
}
if ($source.Contains('$ctrlBreakLauncherOutput') -or -not $source.Contains('$ctrlBreakConsumerIdentityPath 2>&1 | Write-J6CtrlBreakNativeEvidence')) {
    throw 'DIAGNOSTIC_LAUNCHER_STREAMING_WIRING_FAILED'
}
'J6_DIAGNOSTIC_INTERRUPTED_RELAY=PASS'

# Compile the actual diagnostic method only: no Win32 process or socket call.
$method = [regex]::Match($source, '(?s)    private static void WriteDiagnostic\(string marker\).*?(?=    public static int Main)')
if (-not $method.Success -or $source.Contains('Console.WriteLine("J6_CTRL_BREAK_NATIVE_')) { throw 'DIAGNOSTIC_SAFE_WRITER_WIRING_FAILED' }
$probe = @'
using System;
using System.IO;
using System.Text;
public static class J6DiagnosticWriterContract {
    private sealed class FaultWriter : TextWriter {
        public override Encoding Encoding { get { return Encoding.UTF8; } }
        public override void WriteLine(string value) { throw new IOException("SYNTHETIC_SECRET"); }
    }
    public static bool Run() {
        TextWriter saved = Console.Out;
        bool cleanupReached = false;
        try {
            Console.SetOut(new FaultWriter());
            try { throw new InvalidOperationException("PRIMARY_SENTINEL"); }
            finally {
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_PHASE=CLEANUP");
                cleanupReached = true;
                WriteDiagnostic("J6_CTRL_BREAK_NATIVE_CLEANUP=PASS");
            }
        } catch (InvalidOperationException ex) {
            return cleanupReached && ex.Message == "PRIMARY_SENTINEL";
        } finally { Console.SetOut(saved); }
    }
METHOD
}
'@
Add-Type -TypeDefinition ($probe.Replace('METHOD', $method.Value))
if (-not [J6DiagnosticWriterContract]::Run()) { throw 'DIAGNOSTIC_OUTPUT_FAILURE_ISOLATION_FAILED' }
'J6_DIAGNOSTIC_OUTPUT_FAILURE_ISOLATION=PASS'
