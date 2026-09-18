"""Build pristine C9 host-owned Windows contexts from the validated repair copies.

The program performs no model, PowerShell, Java, Maven, Docker, browser or network action.
It requires a non-existent destination, reconstructs only the explicitly declared C5 inputs,
substitutes the C9 protocol and repaired operational templates, and emits a new C9 preflight.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import pathlib
import shutil
from datetime import datetime, timezone


SKILL = "ss-windows-runtime"
RUN_ID = "run-06"
CASES = ("WR-H01", "WR-N01")
CANDIDATE_SHA = "b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6"
OLD_TO_NEW = {
    "inputs/c5/windows-session-protocol.md": "inputs/c9/windows-session-protocol.md",
    "inputs/c5/wrh-module-extract.md": "inputs/c9/wrh-module-extract.md",
    "inputs/c5/wrh-harness-template.ps1": "inputs/c9/wrh-harness-template.ps1",
    "inputs/c5/wrh-postflight-template.ps1": "inputs/c9/wrh-postflight-template.ps1",
    "inputs/c5/wrn-conductor-template.ps1": "inputs/c9/wrn-conductor-template.ps1",
}


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def record(path: pathlib.Path, relative: str, role: str) -> dict[str, object]:
    if not path.is_file() or path.is_symlink():
        raise RuntimeError(f"expected regular input: {path}")
    return {"path": relative.replace("\\", "/"), "bytes": path.stat().st_size, "sha256": sha256(path), "access_role": role}


def copy_bytes(source: pathlib.Path, destination: pathlib.Path) -> None:
    if not source.is_file() or source.is_symlink() or destination.exists():
        raise RuntimeError(f"unsafe copy: {source} -> {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with source.open("rb") as incoming, destination.open("xb") as outgoing:
        shutil.copyfileobj(incoming, outgoing, 1024 * 1024)
    if source.stat().st_size != destination.stat().st_size or sha256(source) != sha256(destination):
        raise RuntimeError(f"copy differs: {destination}")


def write_text_new(path: pathlib.Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(text)


def write_json_new(path: pathlib.Path, value: object) -> None:
    write_text_new(path, json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def relative_path(value: str) -> pathlib.Path:
    pure = pathlib.PurePosixPath(value)
    if pure.is_absolute() or ".." in pure.parts:
        raise RuntimeError(f"unsafe relative path: {value}")
    return pathlib.Path(*pure.parts)


def load_repair_builder(root: pathlib.Path):
    repair = root / "docs/skills/evaluations/WO-062/c-windows-operational-repair-06/build_repaired_templates.py"
    spec = importlib.util.spec_from_file_location("wo062_repair_builder", repair)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load repair builder")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def request_for_c9(source: pathlib.Path) -> str:
    text = source.read_text(encoding="utf-8-sig")
    text = text.replace("inputs/c5/", "inputs/c9/")
    text = text.replace("C5", "C9")
    text = text.replace("harnais inchangé", "harnais réparé fourni")
    text = text.replace("gabarit opérationnel", "gabarit opérationnel réparé")
    return text


def prompt_for_c9(source: pathlib.Path) -> str:
    return source.read_text(encoding="utf-8-sig")


def build(root: pathlib.Path, destination: pathlib.Path) -> dict[str, object]:
    source_runtime = root / ".tmp/wo062-evaluations/ss-windows-runtime/run-05-host-formal-20260916"
    source_preflight_path = source_runtime / "preflight.json"
    protocol = root / "docs/skills/evaluations/WO-062/c-requalification-09/windows-session-protocol.md"
    repair_validation = root / "docs/skills/evaluations/WO-062/c-windows-operational-repair-06/validation.json"
    repair_builder_path = root / "docs/skills/evaluations/WO-062/c-windows-operational-repair-06/build_repaired_templates.py"
    if destination.exists():
        raise RuntimeError(f"C9 destination must not exist: {destination}")
    if not source_preflight_path.is_file() or not protocol.is_file() or not repair_validation.is_file():
        raise RuntimeError("C9 source, protocol or repair validation is absent")

    source_preflight = json.loads(source_preflight_path.read_text(encoding="utf-8-sig"))
    if source_preflight.get("skill") != SKILL or source_preflight.get("candidate_sha256") != CANDIDATE_SHA:
        raise RuntimeError("C5 source candidate does not match C9 authorization")
    source_cases = source_preflight.get("cases")
    if not isinstance(source_cases, list) or tuple(item.get("case_id") for item in source_cases) != CASES:
        raise RuntimeError("C5 source case order differs")

    stage = destination.parent / (destination.name + "-repair-stage")
    if stage.exists():
        raise RuntimeError(f"repair stage must not exist: {stage}")
    repair_builder = load_repair_builder(root)
    repair_manifest = repair_builder.build(root, stage)
    repaired = {item["output"]: stage / item["output"] for item in repair_manifest["files"]}
    if set(repaired) != {"wrh-harness-template.ps1", "wrh-postflight-template.ps1", "wrn-conductor-template.ps1"}:
        raise RuntimeError("repair copy set differs")

    destination.mkdir(parents=True, exist_ok=False)
    cases: list[dict[str, object]] = []
    for source_case in source_cases:
        case_id = str(source_case["case_id"])
        source_case_dir = source_runtime / case_id
        case_dir = destination / case_id
        case_dir.mkdir(parents=True, exist_ok=False)
        records: list[dict[str, object]] = []
        for item in source_case["input_files"]:
            old_path = str(item["path"])
            new_path = OLD_TO_NEW.get(old_path, old_path)
            destination_file = case_dir / relative_path(new_path)
            if old_path == "inputs/c5/windows-session-protocol.md":
                copy_bytes(protocol, destination_file)
            elif old_path == "inputs/c5/wrh-module-extract.md":
                copy_bytes(root / "docs/skills/evaluations/WO-062/c-requalification-05/wrh-module-extract.md", destination_file)
            elif old_path == "inputs/c5/wrh-harness-template.ps1":
                copy_bytes(repaired["wrh-harness-template.ps1"], destination_file)
            elif old_path == "inputs/c5/wrh-postflight-template.ps1":
                copy_bytes(repaired["wrh-postflight-template.ps1"], destination_file)
            elif old_path == "inputs/c5/wrn-conductor-template.ps1":
                copy_bytes(repaired["wrn-conductor-template.ps1"], destination_file)
            else:
                copy_bytes(source_case_dir / relative_path(old_path), destination_file)
            role = str(item["access_role"])
            if old_path in OLD_TO_NEW and role == "operational_template":
                role = "operational_template_repaired"
            records.append(record(destination_file, new_path, role))
        request = request_for_c9(source_case_dir / "request.txt")
        prompt = prompt_for_c9(source_case_dir / "case-prompt.txt")
        write_text_new(case_dir / "request.txt", request)
        write_text_new(case_dir / "case-prompt.txt", prompt)
        (case_dir / "output").mkdir(exist_ok=False)
        case = {
            "case_id": case_id,
            "kind": source_case["kind"],
            "execution_mode": "bounded_local_runtime",
            "input_files": records,
            "prompt_sha256": sha256(case_dir / "case-prompt.txt"),
            "request_sha256": sha256(case_dir / "request.txt"),
            "oracle_supplied": False,
            "design_history_supplied": False,
            "candidate_body_manually_pasted": False,
            "runtime_dependency_only": source_case["runtime_dependency_only"],
            "readable_input_paths": [OLD_TO_NEW.get(path, path) for path in source_case["readable_input_paths"]],
            "operational_templates": [OLD_TO_NEW.get(path, path) for path in source_case["operational_templates"]],
        }
        cases.append(case)

    provenance = {
        "schema": "wo062-c9-repaired-copy-provenance-v1",
        "created_at_utc": now(),
        "source_c5_physical_context": str(source_runtime.relative_to(root).as_posix()),
        "source_c5_preflight_sha256": sha256(source_preflight_path),
        "repair_validation_sha256": sha256(repair_validation),
        "repair_builder_sha256": sha256(repair_builder_path),
        "repair_stage": str(stage.relative_to(root).as_posix()),
        "repair_manifest_sha256": sha256(stage / "revision-manifest.json"),
        "copies": repair_manifest["files"],
        "historical_c5_context_modified": False,
    }
    write_json_new(destination / "repair-provenance.json", provenance)
    preflight = {
        "schema": "wo062-c9-host-context-preflight-v1",
        "prepared_at_utc": now(),
        "skill": SKILL,
        "run_id": RUN_ID,
        "physical_context_label": destination.name,
        "candidate_version": "0.1.0-candidate.1",
        "candidate_sha256": CANDIDATE_SHA,
        "source_c5_preflight_sha256": sha256(source_preflight_path),
        "repair_provenance_sha256": sha256(destination / "repair-provenance.json"),
        "case_count": len(cases),
        "cases": cases,
        "model_session_started": False,
        "runtime_probe_executed": False,
        "java_started": False,
        "network_used": False,
        "oracle_supplied": False,
        "prior_answers_supplied": False,
    }
    write_json_new(destination / "preflight.json", preflight)
    return preflight


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, type=pathlib.Path)
    parser.add_argument("--destination", required=True, type=pathlib.Path)
    options = parser.parse_args()
    result = build(options.root.resolve(), options.destination.resolve())
    print(json.dumps({"run_id": result["run_id"], "case_count": result["case_count"], "destination": str(options.destination)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
