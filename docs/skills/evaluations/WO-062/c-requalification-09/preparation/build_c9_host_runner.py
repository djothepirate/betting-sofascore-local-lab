"""Derive the C9 host conductor from the frozen C5 host conductor.

This authoring step creates no model session and runs no PowerShell, Java, Maven, Docker,
browser or network command.  It is intentionally a narrow transformation: C9 has new
authorization/context checks and replaces C5's presence-only runtime-root test with the
validated conservative collector.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re


C5_RUNNER_SHA256 = "b83b6432eb9c053cd2735b6e10c0baf846a60b692a3c459adc4a659f417e43d2"
CANDIDATE_SHA256 = "b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6"


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise RuntimeError(f"{label}: source anchor is not unique")
    return text.replace(old, new, 1)


def replace_function(text: str, name: str, body: str, next_name: str) -> str:
    pattern = rf"def {re.escape(name)}\(\):.*?(?=def {re.escape(next_name)}\()"
    replacement = body.rstrip() + "\n\n"
    result, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise RuntimeError(f"could not replace {name}")
    return result


def c9_assertion() -> str:
    return '''def assert_prelaunch_contract():
    require(CLI.is_file() and not CLI.is_symlink(), "Configured Codex CLI is absent")
    auth = read_json(AUTHORIZATION)
    candidate = auth.get("candidate")
    require(isinstance(candidate, dict), "C9 authorization lacks candidate")
    require(
        auth.get("skill") == SKILL
        and auth.get("run_id") == RUN_ID
        and auth.get("physical_context_label") == PHYSICAL
        and candidate.get("version") == CANDIDATE_VERSION
        and candidate.get("sha256") == CANDIDATE_SHA
        and tuple(auth.get("cases", [])) == CASES
        and auth.get("max_model_sessions") == 2
        and auth.get("automatic_retry") is False
        and auth.get("host_owned_contexts_required") is True
        and auth.get("repaired_copies_required") is True
        and auth.get("postfreeze_review_required") is True
        and auth.get("human_validation") is False
        and auth.get("personal_installation") is False,
        "C9 authorization boundary changed",
    )
    preflight = read_json(PREFLIGHT)
    require(
        preflight.get("skill") == SKILL
        and preflight.get("run_id") == RUN_ID
        and preflight.get("physical_context_label") == PHYSICAL
        and preflight.get("candidate_version") == CANDIDATE_VERSION
        and preflight.get("candidate_sha256") == CANDIDATE_SHA
        and tuple(item.get("case_id") for item in preflight.get("cases", [])) == CASES
        and preflight.get("model_session_started") is False
        and preflight.get("runtime_probe_executed") is False
        and preflight.get("java_started") is False
        and preflight.get("oracle_supplied") is False
        and preflight.get("prior_answers_supplied") is False,
        "C9 context preflight changed",
    )
    provenance = read_json(RUNTIME / "repair-provenance.json")
    require(
        provenance.get("historical_c5_context_modified") is False
        and len(provenance.get("copies", [])) == 3
        and provenance.get("repair_validation_sha256")
        and provenance.get("repair_manifest_sha256"),
        "C9 repaired-copy provenance changed",
    )
    runner_manifest = read_json(RUNTIME / "runner-manifest.json")
    require(
        runner_manifest.get("c5_runner_sha256") == "''' + C5_RUNNER_SHA256 + '''"
        and runner_manifest.get("candidate_sha256") == CANDIDATE_SHA
        and runner_manifest.get("collector_integrated") is True
        and runner_manifest.get("model_session_started") is False,
        "C9 host conductor provenance changed",
    )
    expected_top = {"WR-H01", "WR-N01", "preflight.json", "repair-provenance.json", "runner-manifest.json", "c9_host_runner.py"}
    require({item.name for item in RUNTIME.iterdir()} == expected_top, "C9 physical context is not pristine")
    require(not FROZEN.exists(), "Frozen C9 evidence already exists: automatic retry is forbidden")
    for case_id in CASES:
        output = RUNTIME / case_id / "output"
        require(output.is_dir() and not output.is_symlink() and not any(output.iterdir()), "Output is nonempty: " + case_id)
    return preflight'''


def collect_helper() -> str:
    return '''def collect_runtime_root(output):
    import importlib.util
    collector_path = ROOT / "docs" / "skills" / "evaluations" / "WO-062" / "c-windows-operational-repair-06" / "c5_runtime_root_collector.py"
    require(collector_path.is_file() and not collector_path.is_symlink(), "C9 runtime-root collector is absent")
    spec = importlib.util.spec_from_file_location("wo062_c9_runtime_root_collector", collector_path)
    require(spec is not None and spec.loader is not None, "C9 runtime-root collector cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    record = module.inspect_owned_runtime_root(output)
    require(record.get("delete_attempted") is False, "Collector must remain observational")
    return record

'''


def transform(source: str) -> str:
    text = source
    text = replace_once(text, "ROOT = pathlib.Path(__file__).resolve().parent.parent", "ROOT = pathlib.Path(__file__).resolve().parents[4]", "C9 root")
    text = replace_once(text, 'RUN_ID = "run-05"', 'RUN_ID = "run-06"', "C9 run id")
    text = replace_once(text, 'PHYSICAL = "run-05-host-formal-20260916"', 'PHYSICAL = "run-06-host-formal-20260916"', "C9 physical label")
    text = replace_once(text, 'SOURCE_RUNTIME = ROOT / ".tmp" / "wo062-evaluations" / SKILL / RUN_ID', 'SOURCE_RUNTIME = ROOT / ".tmp" / "wo062-evaluations" / SKILL / "run-05-host-formal-20260916"', "C9 source provenance")
    text = replace_once(text, 'AUTHORIZATION = ROOT / "docs" / "skills" / "evaluations" / "WO-062" / "c-requalification-05" / "authorization.json"', 'AUTHORIZATION = ROOT / "docs" / "skills" / "evaluations" / "WO-062" / "c-requalification-09" / "authorization.json"', "C9 authorization")
    text = text.replace('"wo062-c5-', '"wo062-c9-')
    text = text.replace('C5 Windows', 'C9 Windows').replace('C5 host', 'C9 host').replace('C5 source', 'C9 source')
    text = replace_function(text, "assert_prelaunch_contract", c9_assertion(), "attest_host_parent")
    text = replace_once(text, "def reading_audit(case_id, commands, case):", collect_helper() + "def reading_audit(case_id, commands, case):", "C9 collector integration")
    old_root_check = '''    runtime_residue = (output / "runtime").exists()
    if runtime_residue:
        errors.append({"stage": "postflight", "error": "RuntimeRootResidue"})
'''
    new_root_check = '''    runtime_root = collect_runtime_root(output)
    if runtime_root["status"] != "PASS":
        errors.append({"stage": "postflight", "error": "OwnedRuntimeRootResidue", "collector": runtime_root})
    write_new_json(output / "runtime-root-collector.json", runtime_root)
'''
    text = replace_once(text, old_root_check, new_root_check, "C9 collector call")
    text = replace_once(text, '"postflight-integrity.json", "reading-audit.json", "response.md", "runtime",', '"postflight-integrity.json", "reading-audit.json", "response.md", "runtime", "runtime-root-collector.json",', "C9 output allowlist")
    text = replace_once(text, '"runtime_root_present": runtime_residue,', '"runtime_root": runtime_root,', "C9 postflight record")
    text = replace_once(text, 'description="Draft-only strict C9 Windows host-parent conductor"', 'description="Strict C9 Windows host-parent conductor"', "C9 description")
    return text


def build(root: pathlib.Path, destination: pathlib.Path) -> dict[str, object]:
    source = root / ".tmp/c5_host_formal_windows_runner_draft.py"
    collector = root / "docs/skills/evaluations/WO-062/c-windows-operational-repair-06/c5_runtime_root_collector.py"
    if not source.is_file() or sha256(source) != C5_RUNNER_SHA256:
        raise RuntimeError("the frozen C5 conductor source is missing or differs")
    if not collector.is_file() or destination.exists():
        raise RuntimeError("collector is absent or C9 conductor destination already exists")
    transformed = transform(source.read_text(encoding="utf-8-sig"))
    destination.write_text(transformed, encoding="utf-8", newline="\n")
    manifest = {
        "schema": "wo062-c9-host-runner-build-v1",
        "c5_runner": str(source.relative_to(root).as_posix()),
        "c5_runner_sha256": C5_RUNNER_SHA256,
        "runner": destination.name,
        "runner_sha256": sha256(destination),
        "candidate_sha256": CANDIDATE_SHA256,
        "collector": str(collector.relative_to(root).as_posix()),
        "collector_sha256": sha256(collector),
        "collector_integrated": True,
        "model_session_started": False,
        "runtime_probe_executed": False,
        "historical_c5_evidence_modified": False,
    }
    manifest_path = destination.parent / "runner-manifest.json"
    if manifest_path.exists():
        raise RuntimeError("runner manifest already exists")
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
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
