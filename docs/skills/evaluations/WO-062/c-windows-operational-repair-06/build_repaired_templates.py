"""Build revised operational copies without changing the C5 historical sources.

The builder intentionally has no model, Codex CLI, Java, PowerShell or network action.  It
only performs exact byte-derived textual transformations into a destination that must not
already exist.  The resulting manifest makes a future fresh context reproducible while
keeping the sources used by run-05 untouched.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys
from typing import Final


SOURCE_DIRECTORY: Final = pathlib.PurePosixPath(
    "docs/skills/evaluations/WO-062/c-requalification-05"
)
TARGETS: Final = (
    "wrh-harness-template.ps1",
    "wrh-postflight-template.ps1",
    "wrn-conductor-template.ps1",
)


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one source anchor, found {count}")
    return text.replace(old, new, 1)


def repair_wrh_harness(text: str) -> str:
    text = replace_once(
        text,
        "param()",
        "param(\n    [switch]$PreflightOnly\n)",
        "WR-H01 preflight parameter",
    )
    text = replace_once(
        text,
        """    $root = [IO.Path]::GetFullPath($Base).TrimEnd('\\\\')
    if (-not $full.StartsWith($root + '\\\\', [StringComparison]::OrdinalIgnoreCase)) { throw \"Path outside case: $full\" }
""",
        """    $root = [IO.Path]::GetFullPath($Base).TrimEnd([char[]]@('\\', '/'))
    $separator = [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($root + $separator, [StringComparison]::OrdinalIgnoreCase)) { throw \"Path outside case: $full\" }
""",
        "WR-H01 containment separator",
    )
    text = replace_once(
        text,
        """    if ((Remaining) -lt 55000) { throw 'Preflight leaves insufficient C5 harness budget' }

    $child = New-Object Diagnostics.Process
    $child.StartInfo = $info
    if (-not $child.Start()) { throw 'Process.Start returned false' }
    $handle = $child.Handle
    $createdUtc = $child.StartTime.ToUniversalTime().ToString('o')
    $image = $child.MainModule.FileName
    Add-Event 'process_started' @{ pid=$child.Id; handle=$handle.ToInt64(); creation_utc=$createdUtc; image=$image; requested_image=$pwsh; conductor_pid=$PID; command=(('\"' + $pwsh + '\" ') + ($args -join ' ')); execution_started_after_process_start=$true }
    Write-Output 'WR_PHASE=EXECUTION'
    $stdout = $child.StandardOutput.ReadToEndAsync()
    $stderr = $child.StandardError.ReadToEndAsync()
    while (-not $child.WaitForExit([Math]::Min(25, [Math]::Max(1, (Remaining))))) {
        if ((Remaining) -le 5000) { throw 'Harnais did not return before its reserved cleanup time' }
    }
    $nativeExit = $child.ExitCode
    if (-not [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout, $stderr), [Math]::Min(5000, (Remaining)))) { throw 'Harnais stream drain deadline expired' }
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'harness.stdout.txt'), $stdout.Result, $utf8)
    [IO.File]::WriteAllText((Join-Path $artifactRoot 'harness.stderr.txt'), $stderr.Result, $utf8)
    Add-Event 'harness_result_available' @{ native_exit_code=$nativeExit; elapsed_ms=$clock.Elapsed.TotalMilliseconds; stdout='harness.stdout.txt'; stderr='harness.stderr.txt'; retained_handle=$handle.ToInt64(); exited=$child.HasExited }
    if ($nativeExit -ne 0) { throw \"Harnais native exit code: $nativeExit\" }
    $result = 'NATIVE_EXIT_ZERO'
    $scriptExit = 0
""",
        """    if ($PreflightOnly) {
        Add-Event 'preflight_complete' @{ scope='Containment and empty runtime root only; no WO-044 child started'; runtime_root=$runtimeRoot }
        $result = 'PREFLIGHT_OK_NO_HARNESS'
        $scriptExit = 0
    } else {
        if ((Remaining) -lt 55000) { throw 'Preflight leaves insufficient C5 harness budget' }

        $child = New-Object Diagnostics.Process
        $child.StartInfo = $info
        if (-not $child.Start()) { throw 'Process.Start returned false' }
        $handle = $child.Handle
        $createdUtc = $child.StartTime.ToUniversalTime().ToString('o')
        $image = $child.MainModule.FileName
        Add-Event 'process_started' @{ pid=$child.Id; handle=$handle.ToInt64(); creation_utc=$createdUtc; image=$image; requested_image=$pwsh; conductor_pid=$PID; command=(('\"' + $pwsh + '\" ') + ($args -join ' ')); execution_started_after_process_start=$true }
        Write-Output 'WR_PHASE=EXECUTION'
        $stdout = $child.StandardOutput.ReadToEndAsync()
        $stderr = $child.StandardError.ReadToEndAsync()
        while (-not $child.WaitForExit([Math]::Min(25, [Math]::Max(1, (Remaining))))) {
            if ((Remaining) -le 5000) { throw 'Harnais did not return before its reserved cleanup time' }
        }
        $nativeExit = $child.ExitCode
        if (-not [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout, $stderr), [Math]::Min(5000, (Remaining)))) { throw 'Harnais stream drain deadline expired' }
        [IO.File]::WriteAllText((Join-Path $artifactRoot 'harness.stdout.txt'), $stdout.Result, $utf8)
        [IO.File]::WriteAllText((Join-Path $artifactRoot 'harness.stderr.txt'), $stderr.Result, $utf8)
        Add-Event 'harness_result_available' @{ native_exit_code=$nativeExit; elapsed_ms=$clock.Elapsed.TotalMilliseconds; stdout='harness.stdout.txt'; stderr='harness.stderr.txt'; retained_handle=$handle.ToInt64(); exited=$child.HasExited }
        if ($nativeExit -ne 0) { throw \"Harnais native exit code: $nativeExit\" }
        $result = 'NATIVE_EXIT_ZERO'
        $scriptExit = 0
    }
""",
        "WR-H01 no-child preflight branch",
    )
    return text


def repair_wrh_postflight(text: str) -> str:
    return replace_once(
        text,
        "$entries = if ([IO.Directory]::Exists($runtimeRoot)) { @([IO.Directory]::GetFileSystemEntries($runtimeRoot)) } else { @() }",
        """$entries = @()
if ([IO.Directory]::Exists($runtimeRoot)) {
    $entries = @([IO.Directory]::GetFileSystemEntries($runtimeRoot))
}""",
        "WR-H01 postflight empty array",
    )


def repair_wrn_conductor(text: str) -> str:
    text = replace_once(
        text,
        """        image = $null
        image_observation_error = $null
        arguments = $psi.Arguments
""",
        """        image = $null
        image_observation_error = $null
        image_observed = $false
        image_observed_before_stream_read = $false
        arguments = $psi.Arguments
""",
        "WR-N01 image record fields",
    )
    text = replace_once(
        text,
        """    $r.start_utc = $p.StartTime.ToUniversalTime().ToString('o')
    $r.line_task = $p.StandardOutput.ReadLineAsync()
    $r.err_task = $p.StandardError.ReadToEndAsync()
    try {
        $r.image = $p.MainModule.FileName
    } catch {
        $r.image_observation_error = $_.Exception.Message
        if ($mode -ne 'version') {
            throw \"Required image observation failed for $mode\"
        }
    }
""",
        """    $r.start_utc = $p.StartTime.ToUniversalTime().ToString('o')
    try {
        $observedImage = [string]$p.MainModule.FileName
        if ([string]::IsNullOrWhiteSpace($observedImage)) {
            throw 'Process main-module image is empty'
        }
        $r.image = [IO.Path]::GetFullPath($observedImage)
        if (-not [string]::Equals($r.image, $javaPath, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Process main-module image differs from direct Java'
        }
        $r.image_observed = $true
        $r.image_observed_before_stream_read = $true
    } catch {
        $r.image_observation_error = $_.Exception.Message
        if ($mode -ne 'version') {
            throw \"Required image observation failed for ${mode}: $($r.image_observation_error)\"
        }
    }
    $r.line_task = $p.StandardOutput.ReadLineAsync()
    $r.err_task = $p.StandardError.ReadToEndAsync()
""",
        "WR-N01 image before streams",
    )
    text = replace_once(
        text,
        """        image = $r.image
        image_observation_error = $r.image_observation_error
        requested_image = $javaPath
""",
        """        image = $r.image
        image_observation_error = $r.image_observation_error
        image_observed = $r.image_observed
        image_observed_before_stream_read = $r.image_observed_before_stream_read
        requested_image = $javaPath
""",
        "WR-N01 image event fields",
    )
    text = replace_once(
        text,
        """        $r.lines.Contains($expectedText) -and
        [string]::Equals($r.image, $javaPath, [StringComparison]::OrdinalIgnoreCase) -and
""",
        """        $r.lines.Contains($expectedText) -and
        $r.image_observed -and
        $r.image_observed_before_stream_read -and
        [string]::Equals($r.image, $javaPath, [StringComparison]::OrdinalIgnoreCase) -and
""",
        "WR-N01 identity requires prior image",
    )
    return text


def build(root: pathlib.Path, destination: pathlib.Path) -> dict[str, object]:
    source_root = root / pathlib.Path(*SOURCE_DIRECTORY.parts)
    if destination.exists():
        raise RuntimeError(f"destination must not exist: {destination}")
    destination.mkdir(parents=True, exist_ok=False)
    transforms = {
        "wrh-harness-template.ps1": repair_wrh_harness,
        "wrh-postflight-template.ps1": repair_wrh_postflight,
        "wrn-conductor-template.ps1": repair_wrn_conductor,
    }
    files = []
    for name in TARGETS:
        source = source_root / name
        if not source.is_file():
            raise RuntimeError(f"missing C5 source: {source}")
        original = source.read_text(encoding="utf-8-sig")
        repaired = transforms[name](original)
        output = destination / name
        output.write_text(repaired, encoding="utf-8", newline="\n")
        files.append(
            {
                "source": str(source.relative_to(root).as_posix()),
                "source_sha256": sha256(source),
                "output": output.name,
                "output_sha256": sha256(output),
            }
        )
    manifest = {
        "schema": "wo062-windows-operational-repair-build-v1",
        "source_c5_directory": str(SOURCE_DIRECTORY),
        "destination": str(destination),
        "files": files,
        "model_session_started": False,
        "qualification_execution_started": False,
    }
    (destination / "revision-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=pathlib.Path)
    parser.add_argument("--destination", required=True, type=pathlib.Path)
    options = parser.parse_args()
    manifest = build(options.root.resolve(), options.destination.resolve())
    print(json.dumps(manifest, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
