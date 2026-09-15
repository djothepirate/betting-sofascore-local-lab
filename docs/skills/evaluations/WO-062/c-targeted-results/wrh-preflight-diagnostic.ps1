$ErrorActionPreference = 'Stop'
$records = [Collections.Generic.List[object]]::new()
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'wo062-evaluations/ss-windows-runtime/run-03/WR-H01/output/runtime'))
$checks = [ordered]@{
    'path_ancestors' = {
        $cursor = $root
        $seen = @()
        while ($cursor) {
            if (Test-Path -LiteralPath $cursor) { $item = Get-Item -LiteralPath $cursor -Force; $seen += @{path=$cursor; reparse=(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)} }
            $cursor = Split-Path -Parent $cursor
        }
        $seen
    }
    'runtime_directory_read' = { @{entries=@(Get-ChildItem -LiteralPath $root -Force).Count} }
    'self_image' = { $p=[Diagnostics.Process]::GetCurrentProcess(); @{pid=$PID; image=$p.MainModule.FileName; psVersion=[string]$PSVersionTable.PSVersion} }
    'self_cim_parent' = { $p=Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $PID); @{pid=$p.ProcessId; parentPid=$p.ParentProcessId} }
}
foreach ($step in $checks.Keys) {
    $timer=[Diagnostics.Stopwatch]::StartNew()
    try { $value=& $checks[$step]; $records.Add(@{step=$step;status='PASS';value=$value;durationMs=$timer.Elapsed.TotalMilliseconds}) }
    catch { $records.Add(@{step=$step;status='BLOCKED';durationMs=$timer.Elapsed.TotalMilliseconds;message=$_.Exception.Message;exceptionType=$_.Exception.GetType().FullName;fullyQualifiedErrorId=$_.FullyQualifiedErrorId;position=$_.InvocationInfo.PositionMessage;scriptStackTrace=$_.ScriptStackTrace}) }
}
$result=@{schema='wo062-wrh-readonly-preflight-diagnostic-v1';atUtc=[DateTime]::UtcNow.ToString('O');scope='Read-only diagnostic in the parent task sandbox, not the evaluated session; no harness, JVM, deletion, ACL change or retry.';records=$records}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'wrh-preflight-diagnostic.json'),($result|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$records | Select-Object step,status,durationMs | ConvertTo-Json
