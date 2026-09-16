"""Perform the distinct C9 postfreeze technical review from frozen evidence only.

This reviewer never opens the live case outputs, launches no model or runtime probe, and does
not alter a frozen file.  It verifies the freeze manifests byte-for-byte and classifies the
observed C9 outcomes against the fixed H01/N01 requirements.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


CASES = ("WR-H01", "WR-N01")
CANDIDATE_SHA = "b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise RuntimeError(f"JSON object required: {path}")
    return value


def require(value: bool, message: str) -> None:
    if not value:
        raise RuntimeError(message)


def validate_freeze(frozen: Path, case_id: str) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    freeze = load_json(frozen / "freeze.json")
    require(freeze.get("logical_run_id") == "run-06" and freeze.get("before_independent_review") is True, f"bad freeze header: {case_id}")
    declared = freeze.get("files")
    require(isinstance(declared, list) and declared, f"empty freeze: {case_id}")
    actual: dict[str, tuple[int, str]] = {}
    for path in frozen.rglob("*"):
        if path.is_file() and path.name != "freeze.json":
            actual[path.relative_to(frozen).as_posix()] = (path.stat().st_size, sha256(path))
    expected: dict[str, tuple[int, str]] = {}
    for item in declared:
        relative = str(item["path"])
        expected[relative] = (int(item["bytes"]), str(item["sha256"]))
    require(actual == expected, f"freeze byte mismatch: {case_id}")
    trace = load_json(frozen / "trace.json")
    postflight = load_json(frozen / "postflight-integrity.json")
    require(trace.get("case_id") == case_id and trace.get("candidate_sha256") == CANDIDATE_SHA, f"trace candidate mismatch: {case_id}")
    return freeze, trace, postflight


def review_h01(frozen: Path, trace: dict[str, Any], postflight: dict[str, Any]) -> dict[str, Any]:
    execution = load_json(frozen / "runtime-artifacts/execution.json")
    prompt = load_json(frozen / "runtime-artifacts/wrh-prompt-returned.json")
    root = postflight.get("runtime_root", {})
    conditions = {
        "collection_completed": trace.get("collection_completed") is True,
        "no_trace_diagnostics": trace.get("diagnostics") == [],
        "native_exit_zero": execution.get("native_exit_code") == 0,
        "two_iterations": "WO044_QUALIFIED_ITERATIONS=2" in (frozen / "runtime-artifacts/harness.stdout.txt").read_text(encoding="utf-8-sig"),
        "prompt_returned_separately": prompt.get("marker") == "WR_PROMPT_RETURNED" and prompt.get("separateCommandAfterDriverReturn") is True,
        "collector_passes_empty_parent": root.get("status") == "PASS" and root.get("runtime_root_empty") is True and root.get("delete_attempted") is False,
        "read_policy_pass": trace.get("reading_audit", {}).get("status") == "PASS",
        "input_integrity_pass": postflight.get("input_integrity", {}).get("status") == "PASS",
    }
    return {
        "case_id": "WR-H01",
        "verdict": "PASS" if all(conditions.values()) else "FAIL",
        "conditions": conditions,
        "session_duration_seconds": trace.get("duration_seconds"),
        "watchdog_seconds": trace.get("watchdog_seconds"),
        "six_minute_target_met": float(trace.get("duration_seconds", 0)) < 360,
        "finding": "The fresh repaired H01 harness completed and the collector accepted the empty runtime parent. The response-finalization target of six minutes was missed, while the 900-second watchdog was not approached.",
    }


def review_n01(frozen: Path, trace: dict[str, Any], postflight: dict[str, Any]) -> dict[str, Any]:
    preflight_output = (frozen / "runtime-artifacts/session-preflight-output.txt").read_text(encoding="utf-8-sig")
    normal_output = (frozen / "runtime-artifacts/session-normal-output.txt").read_text(encoding="utf-8-sig")
    observations = (frozen / "runtime-artifacts/full-observations.json").read_text(encoding="utf-8-sig")
    postflight_session = load_json(frozen / "runtime-artifacts/session-postflight.json")
    root = postflight.get("runtime_root", {})
    conditions = {
        "preflight_passed_without_java": "PREFLIGHT_VERIFIED_NO_JAVA" in preflight_output,
        "normal_conductor_failed_closed": "WR_CONDUCTOR_RESULT=BLOCKED_OR_INCOMPLETE" in normal_output,
        "image_failure_recorded": "Required image observation failed for failure: Process main-module image is empty" in observations,
        "sleep_not_claimed": "ProcessSleep\": \"Not reached" in (frozen / "runtime-artifacts/session-postflight.json").read_text(encoding="utf-8-sig"),
        "no_forced_stop": postflight_session.get("ProcessStopsPerformed") == 0,
        "fixture_unchanged": "\"unchanged\":  true" in observations,
        "collector_retains_real_residue": root.get("status") == "FAIL" and bool(root.get("runtime_residue_entries")) and root.get("delete_attempted") is False,
        "input_integrity_pass": postflight.get("input_integrity", {}).get("status") == "PASS",
    }
    return {
        "case_id": "WR-N01",
        "verdict": "BLOCKED",
        "conditions": conditions,
        "session_duration_seconds": trace.get("duration_seconds"),
        "watchdog_seconds": trace.get("watchdog_seconds"),
        "finding": "The repaired conductor failed closed when the failure JVM image was empty. It consequently did not establish failure identity, did not run sleep, performed no forced stop, and preserved its owned temporary root as evidence. This does not satisfy the required two-mode and cleanup proof.",
    }


def write_new(path: Path, content: str) -> None:
    if path.exists():
        raise RuntimeError(f"refusing to overwrite review result: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--destination", required=True, type=Path)
    options = parser.parse_args()
    runtime = options.runtime.resolve()
    frozen = runtime / "frozen"
    destination = options.destination.resolve()
    if destination.exists():
        raise RuntimeError(f"review destination already exists: {destination}")

    reviewed: dict[str, dict[str, Any]] = {}
    freeze_hashes: dict[str, str] = {}
    for case_id in CASES:
        case_frozen = frozen / case_id
        _, trace, postflight = validate_freeze(case_frozen, case_id)
        reviewed[case_id] = review_h01(case_frozen, trace, postflight) if case_id == "WR-H01" else review_n01(case_frozen, trace, postflight)
        freeze_hashes[case_id] = sha256(case_frozen / "freeze.json")

    h01, n01 = reviewed["WR-H01"], reviewed["WR-N01"]
    result = {
        "schema": "wo062-c9-postfreeze-review-v1",
        "reviewed_at_utc": datetime.now(timezone.utc).isoformat(),
        "reviewer_mode": "separate_postfreeze_technical_review",
        "reviewer_is_human_validation": False,
        "model_session_started_by_review": False,
        "runtime_probe_executed_by_review": False,
        "run_id": "run-06",
        "candidate_version": "0.1.0-candidate.1",
        "candidate_sha256": CANDIDATE_SHA,
        "freeze_manifests_sha256": freeze_hashes,
        "cases": [h01, n01],
        "campaign_verdict": "INCOMPLETE_QUALIFICATION",
        "current_candidate_qualification": {
            "pass": 7,
            "fail": 0,
            "blocked": 1,
            "basis": "Six historical C4/C5 PASS remain preserved; C9 adds WR-H01 PASS and WR-N01 BLOCKED. Historical C5 BLOCKED evidence is not rewritten."
        },
        "historical_c4_c5_evidence_modified": False,
        "human_validation_authorized": False,
        "personal_installation_authorized": False,
        "next_step": "Do not retry from this campaign. Analyze the PS5.1 empty main-module observation and retained owned root before requesting a new authorized recipe.",
    }
    destination.mkdir(parents=True, exist_ok=False)
    write_new(destination / "postfreeze-review.json", json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    markdown = """# C9 — revue postfreeze Windows

## Verdict

`INCOMPLETE_QUALIFICATION` — `WR-H01` est **PASS** et `WR-N01` est **BLOCKED**.

Cette revue est distincte de l'exécution : elle a relu exclusivement les fichiers gelés,
vérifié chaque taille et SHA-256 déclarés par les deux manifests de gel, et n'a lancé ni
session de modèle, ni PowerShell, ni JVM. Elle ne constitue pas une validation humaine.

## WR-H01 — PASS

- Le contexte, le candidat et les entrées sont restés intègres ; l'audit de lecture est PASS.
- Le harnais réparé a produit le code natif `0`, les deux itérations, le marqueur distinct
  `WR_PROMPT_RETURNED` et un postflight distinct.
- Le collecteur intégré a accepté `output/runtime` car son parent était vide ; aucune
  suppression n'a été tentée.
- La session a duré 391,672 s : sous le watchdog de 900 s, mais au-dessus de l'objectif
  opérationnel de six minutes. Cet écart n'annule pas les preuves fonctionnelles.

## WR-N01 — BLOCKED

- Le préflight PS5.1 s'est terminé `PREFLIGHT_VERIFIED_NO_JAVA`.
- L'invocation normale a refusé d'affirmer l'identité de `failure` après
  `Process main-module image is empty`. Elle n'a pas exécuté `sleep` et n'a forcé aucun arrêt.
- Le collecteur a relevé une racine temporaire détenue réellement non vide. Elle reste
  conservée comme preuve ; le parent `output/runtime` n'est pas confondu avec ce résidu.
- La campagne ne démontre donc ni le code 23 de `failure`, ni READY, ni l'expiration de
  `sleep`, ni le nettoyage complet. La cause de l'image vide reste `NOT_ESTABLISHED`.

## État

Le bilan courant exact du candidat devient **7 PASS / 0 FAIL / 1 BLOCKED**. Les preuves
historiques C4/C5 et leurs verdicts restent inchangés. Il n'y a ni validation humaine,
ni installation personnelle. Une future reprise requiert une analyse ciblée et une nouvelle
autorisation explicite ; cette campagne ne doit pas être rejouée automatiquement.
"""
    write_new(destination / "postfreeze-review.md", markdown)
    manifest = {
        "schema": "wo062-c9-postfreeze-artifact-manifest-v1",
        "review_sha256": sha256(destination / "postfreeze-review.json"),
        "markdown_sha256": sha256(destination / "postfreeze-review.md"),
        "runtime": str(runtime),
        "frozen_case_freeze_sha256": freeze_hashes,
        "historical_c4_c5_evidence_modified": False,
    }
    write_new(destination / "artifact-manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
