"""Bounded non-model verification for the C5 operational repair.

This is deliberately not an evaluator.  It creates disposable local cases, executes no Codex
session, and emits no candidate verdict.  Its narrow Java source-file probe exists only to
verify that the repaired PS5.1 conductor captures a fast owned process image before asserting
fixture identity.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
from typing import Any


def digest(path: pathlib.Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def execute(argv: list[str], cwd: pathlib.Path, timeout: int) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        argv,
        cwd=cwd,
        text=True,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        check=False,
    )


def get_program(candidates: tuple[str, ...]) -> str:
    for candidate in candidates:
        if pathlib.Path(candidate).is_file() or shutil.which(candidate):
            return candidate
    raise RuntimeError("Required executable unavailable: " + ", ".join(candidates))


def load_module(path: pathlib.Path):
    spec = importlib.util.spec_from_file_location("wo062_runtime_root_collector", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load runtime-root collector")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def prepare_h_case(case: pathlib.Path, repaired: pathlib.Path, source_root: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path]:
    artifacts = case / "output" / "runtime-artifacts"
    artifacts.mkdir(parents=True)
    harness = artifacts / "Invoke-WRH01.ps1"
    postflight = artifacts / "Invoke-WRH01Postflight.ps1"
    shutil.copyfile(repaired / "wrh-harness-template.ps1", harness)
    shutil.copyfile(repaired / "wrh-postflight-template.ps1", postflight)
    for relative in (
        pathlib.PurePosixPath("scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1"),
        pathlib.PurePosixPath("scripts/wo044/Capture-WO044NativeArguments.ps1"),
        pathlib.PurePosixPath("scripts/wo036/WO036-CampaignTools.psm1"),
    ):
        destination = case / pathlib.Path(*relative.parts)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text("# test placeholder\n", encoding="utf-8", newline="\n")
    return harness, postflight


def run(root: pathlib.Path) -> dict[str, Any]:
    repair = root / "docs" / "skills" / "evaluations" / "WO-062" / "c-windows-operational-repair-06"
    c5 = root / "docs" / "skills" / "evaluations" / "WO-062" / "c-requalification-05"
    source_fixture = root / "docs" / "skills" / "evaluations" / "WO-062" / "ss-windows-runtime" / "fixtures" / "NativeProbe.java"
    work_parent = root / ".tmp"
    require(source_fixture.is_file(), "NativeProbe fixture is missing")
    before = {name: digest(c5 / name) for name in (
        "wrh-harness-template.ps1",
        "wrh-postflight-template.ps1",
        "wrn-conductor-template.ps1",
    )}
    pwsh = get_program(("C:/Program Files/PowerShell/7/pwsh.exe", "pwsh.exe"))
    powershell = get_program(("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe", "powershell.exe"))
    builder = repair / "build_repaired_templates.py"
    collector = load_module(repair / "c5_runtime_root_collector.py")

    with tempfile.TemporaryDirectory(prefix="wo062-operational-repair-", dir=work_parent) as temporary:
        temp_root = pathlib.Path(temporary)
        repaired = temp_root / "repaired"
        build = execute(
            [sys.executable, str(builder), "--root", str(root), "--destination", str(repaired)],
            root,
            30,
        )
        require(build.returncode == 0, "template builder failed: " + build.stderr)
        build_manifest = json.loads(build.stdout)

        h_case = temp_root / "wrh-pass"
        h_script, h_postflight = prepare_h_case(h_case, repaired, root)
        h_preflight = execute(
            [pwsh, "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(h_script), "-PreflightOnly"],
            h_case,
            30,
        )
        require(h_preflight.returncode == 0, "WR-H01 repaired preflight failed: " + h_preflight.stderr + h_preflight.stdout)
        h_execution = json.loads((h_script.parent / "execution.json").read_text(encoding="utf-8-sig"))
        require(h_execution["result"] == "PREFLIGHT_OK_NO_HARNESS", "WR-H01 preflight started or claimed the harness")
        require(h_execution["cleanup"] == "NO_CHILD_CREATED", "WR-H01 preflight created a child")

        runtime_root = h_case / "output" / "runtime"
        require(runtime_root.is_dir() and not any(runtime_root.iterdir()), "WR-H01 preflight did not produce an empty owned runtime root")
        runtime_root.rmdir()
        h_post = execute(
            [pwsh, "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(h_postflight)],
            h_case,
            30,
        )
        require(h_post.returncode == 0, "WR-H01 postflight failed when runtime root was absent: " + h_post.stderr + h_post.stdout)
        h_postflight_json = json.loads((h_script.parent / "postflight.json").read_text(encoding="utf-8-sig"))
        require(h_postflight_json["runtime_empty"] is True, "WR-H01 absent runtime root was not classified empty")

        h_reject = temp_root / "wrh-reject"
        reject_script, _ = prepare_h_case(h_reject, repaired, root)
        outside = temp_root / "outside-artifacts"
        outside.mkdir()
        source = reject_script.read_text(encoding="utf-8-sig")
        source = source.replace(
            "$artifactRoot = [IO.Path]::GetFullPath($PSScriptRoot)",
            "$artifactRoot = [IO.Path]::GetFullPath((Join-Path $caseRoot '..\\outside-artifacts'))",
            1,
        )
        reject_script.write_text(source, encoding="utf-8", newline="\n")
        h_outside = execute(
            [pwsh, "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(reject_script), "-PreflightOnly"],
            h_reject,
            30,
        )
        require(h_outside.returncode != 0, "WR-H01 containment guard accepted an outside artifact root")
        outside_execution = outside / "execution.json"
        require(outside_execution.is_file(), "WR-H01 outside rejection did not retain execution evidence")
        outside_report = json.loads(outside_execution.read_text(encoding="utf-8-sig"))
        outside_error = next(item for item in outside_report["events"] if item["event"] == "driver_error")
        require("Path outside case" in outside_error["data"]["message"], "WR-H01 outside rejection lacks the expected guard evidence")

        n_case = temp_root / "wrn"
        n_artifacts = n_case / "output" / "runtime-artifacts"
        n_artifacts.mkdir(parents=True)
        shutil.copyfile(repaired / "wrn-conductor-template.ps1", n_artifacts / "Invoke-WRN01.ps1")
        fixture = n_case / "inputs" / "fixtures" / "NativeProbe.java"
        fixture.parent.mkdir(parents=True)
        shutil.copyfile(source_fixture, fixture)
        n_run = execute(
            [powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(n_artifacts / "Invoke-WRN01.ps1")],
            n_case,
            60,
        )
        require(n_run.returncode == 0, "WR-N01 repaired conductor failed: " + n_run.stderr + n_run.stdout)
        n_report = json.loads((n_artifacts / "full-observations.json").read_text(encoding="utf-8-sig"))
        require(n_report["main_result"] == "TWO_MODES_OBSERVED", "WR-N01 did not complete both modes")
        events = n_report["events"]
        failure_start = next(item for item in events if item["event"] == "process_started" and item["data"]["mode"] == "failure")
        failure_observation = next(item for item in events if item["event"] == "failure_observation")
        sleep_timeout = next(item for item in events if item["event"] == "timeout_observed_after_ready" and item["data"]["mode"] == "sleep")
        sleep_stop = next(item for item in events if item["event"] == "ownership_verified_before_kill" and item["data"]["mode"] == "sleep")
        require(failure_start["data"]["image_observed"] is True, "failure image was not captured")
        require(failure_start["data"]["image_observed_before_stream_read"] is True, "failure image was not captured before stream reads")
        require(failure_start["data"]["image"], "failure image is empty")
        require(failure_observation["data"]["native_code"] == 23 and failure_observation["data"]["identity_match"] is True, "failure proof incomplete")
        require(sleep_timeout["data"]["elapsed_after_ready_ms"] >= 1500, "sleep timeout shorter than 1500ms")
        require(sleep_stop["data"]["image"], "sleep owned-process image absent")
        n_postflight = collector.inspect_owned_runtime_root(n_case / "output")
        require(n_postflight["status"] == "PASS" and n_postflight["runtime_root_empty"] is True, "empty runtime parent is not accepted")
        owned_residue = n_case / "output" / "runtime" / "owned-residue"
        owned_residue.mkdir()
        residue_postflight = collector.inspect_owned_runtime_root(n_case / "output")
        require(residue_postflight["status"] == "FAIL", "owned runtime child residue is not rejected")
        require(residue_postflight["runtime_residue_entries"] == ["owned-residue"], "wrong owned residue evidence")

        after = {name: digest(c5 / name) for name in before}
        require(before == after, "C5 source template changed during tests")
        return {
            "schema": "wo062-windows-operational-repair-test-v1",
            "status": "PASS",
            "model_session_started": False,
            "qualification_execution_started": False,
            "wrh": {
                "preflight_only": True,
                "preflight_exit": h_preflight.returncode,
                "postflight_with_absent_runtime_exit": h_post.returncode,
                "outside_path_rejected": True,
            },
            "wrn": {
                "powershell": powershell,
                "conductor_exit": n_run.returncode,
                "main_result": n_report["main_result"],
                "failure_native_code": failure_observation["data"]["native_code"],
                "failure_image_observed_before_stream_read": failure_start["data"]["image_observed_before_stream_read"],
                "sleep_timeout_ms": sleep_timeout["data"]["elapsed_after_ready_ms"],
            },
            "collector": {
                "empty_parent_status": n_postflight["status"],
                "owned_residue_status": residue_postflight["status"],
                "owned_residue_entries": residue_postflight["runtime_residue_entries"],
            },
            "builder": build_manifest,
            "c5_sources_unchanged": True,
            "temporary_root_cleaned": True,
        }


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: test_operational_repair.py <worktree-root>")
    result = run(pathlib.Path(sys.argv[1]).resolve())
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
