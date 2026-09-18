"""Conservative C5 postflight classification for a case output/runtime root.

This module only observes the owned runtime root.  It never deletes a path, terminates a
process, or classifies an empty parent directory as a runtime residue.
"""

from __future__ import annotations

import json
import pathlib
import sys
from typing import Any


def inspect_owned_runtime_root(output: pathlib.Path) -> dict[str, Any]:
    """Return a deterministic postflight record for ``output/runtime``.

    ``status=PASS`` means that the parent is absent or a regular empty directory.  Every
    child entry remains a retained residue and produces ``status=FAIL``.  A symlink or an
    unexpected file-shaped root is also unsafe and fails closed.
    """

    case_output = output.resolve()
    runtime_root = output / "runtime"
    record: dict[str, Any] = {
        "schema": "wo062-c5-owned-runtime-root-v2",
        "output": str(case_output),
        "runtime_root": str(runtime_root),
        "runtime_root_present": runtime_root.exists(),
        "runtime_root_empty": None,
        "runtime_residue_entries": [],
        "unsafe_paths": [],
        "status": "PASS",
        "delete_attempted": False,
    }

    if runtime_root.is_symlink():
        record["unsafe_paths"].append("runtime")
    elif runtime_root.exists() and not runtime_root.is_dir():
        record["unsafe_paths"].append("runtime-not-directory")
    elif runtime_root.is_dir():
        safe_root = runtime_root.resolve()
        for item in sorted(runtime_root.iterdir(), key=lambda entry: entry.name.lower()):
            relative = item.relative_to(runtime_root).as_posix()
            if item.is_symlink() or not item.resolve().is_relative_to(safe_root):
                record["unsafe_paths"].append(relative)
            else:
                record["runtime_residue_entries"].append(relative)
        record["runtime_root_empty"] = not record["runtime_residue_entries"] and not record["unsafe_paths"]
    else:
        record["runtime_root_empty"] = True

    if record["runtime_residue_entries"] or record["unsafe_paths"]:
        record["status"] = "FAIL"
    return record


def _self_test() -> int:
    import tempfile

    with tempfile.TemporaryDirectory(prefix="wo062-c5-runtime-root-") as temporary:
        output = pathlib.Path(temporary) / "output"
        (output / "runtime").mkdir(parents=True)
        empty = inspect_owned_runtime_root(output)
        if empty["status"] != "PASS" or empty["runtime_root_empty"] is not True:
            raise RuntimeError("empty runtime parent must pass")

        (output / "runtime" / "owned-guid").mkdir()
        residue = inspect_owned_runtime_root(output)
        if residue["status"] != "FAIL" or residue["runtime_residue_entries"] != ["owned-guid"]:
            raise RuntimeError("owned child residue must fail")

        print(json.dumps({"empty_parent": empty, "owned_child": residue}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    if sys.argv[1:] != ["--self-test"]:
        raise SystemExit("usage: c5_runtime_root_collector.py --self-test")
    raise SystemExit(_self_test())
