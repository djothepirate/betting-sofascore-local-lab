"""Run only the C9 host-conductor prelaunch guards and write an immutable receipt.

The check imports the generated conductor, asks it to verify authorization, pristine inputs
and host identity, then exits before its `--execute` entry point.  It launches no model and
no runtime probe.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import runpy
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runner", required=True, type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    options = parser.parse_args()
    if options.receipt.exists():
        raise RuntimeError(f"prelaunch receipt already exists: {options.receipt}")
    namespace = runpy.run_path(str(options.runner.resolve()))
    preflight = namespace["assert_prelaunch_contract"]()
    host = namespace["attest_host_parent"]()
    receipt = {
        "schema": "wo062-c9-host-prelaunch-receipt-v1",
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "status": "PASS",
        "run_id": preflight["run_id"],
        "physical_context_label": preflight["physical_context_label"],
        "candidate_sha256": preflight["candidate_sha256"],
        "preflight_sha256": sha256(options.runner.parent / "preflight.json"),
        "runner_sha256": sha256(options.runner),
        "host": host,
        "model_session_started": False,
        "runtime_probe_executed": False,
        "java_started": False,
        "network_used": False,
        "historical_c5_evidence_modified": False,
    }
    options.receipt.parent.mkdir(parents=True, exist_ok=True)
    with options.receipt.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(receipt, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
