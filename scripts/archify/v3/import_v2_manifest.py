#!/usr/bin/env python3
"""One-off helper: derive the V3 manifest skeleton from the FROZEN V2 manifest (never edits V2).
Each V2 task becomes a V3 maven task with focused selectors (the test classes found in its inputs). V2 used `mvnw.cmd -B test` without
-Dtest for every task (35 full-suite runs); V3 groups pending selectors into ONE focused Maven invocation. V2 tasks are marked
importedFromV2: they are adopted from the immutable baseline evidence (`adopt`), not re-executed.
Usage: python scripts/archify/v3/import_v2_manifest.py [--v2 <path>] [--out <path>]   (refuses to overwrite an existing V3 manifest unless --merge)"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
V2 = ROOT / "target" / "archify-certificacion-final" / "MANIFEST_CERTIFICACION_V2.json"
OUT = ROOT / "docs" / "architecture" / "archify" / "knowledge" / "tests" / "v3" / "MANIFEST_CERTIFICACION_V3.json"


def fqn(path: str) -> str:
    return re.sub(r"^src/test/java/|\.java$", "", path).replace("/", ".")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--v2", default=str(V2))
    ap.add_argument("--out", default=str(OUT))
    ap.add_argument("--merge", action="store_true")
    a = ap.parse_args()
    v2 = json.loads(Path(a.v2).read_text(encoding="utf-8-sig"))
    out = Path(a.out)
    cur = json.loads(out.read_text(encoding="utf-8-sig")) if out.exists() else None
    if cur and not a.merge:
        print(f"{out} exists; use --merge to add missing imports", file=sys.stderr)
        return 2
    m = cur or {"schemaVersion": 3, "status": "ACTIVE", "description": "Manifest V3: focused, grouped, resumable certification tasks (V2 is frozen and only a historical baseline).",
                "baselineManifest": "target/archify-certificacion-final/MANIFEST_CERTIFICACION_V2.json", "tasks": []}
    have = {t["id"] for t in m["tasks"]}
    added = 0
    for t in v2["tasks"]:
        tid = "v2." + t["id"].lower().replace("_", "-")
        if tid in have:
            continue
        tests = [i for i in t.get("inputs", []) if i.startswith("src/test/java/") and i.endswith(".java")]
        if not tests:
            print("no test input for", t["id"], file=sys.stderr)
            continue
        m["tasks"].append({"id": tid, "kind": "maven", "selectors": [fqn(i) for i in tests], "inputs": t.get("inputs", []), "scope": t.get("scope", []),
                           "oracle": t.get("oracle", ""), "expected": t.get("expectedOutcome", ""), "importedFromV2": True, "v2Id": t["id"], "priority": 9,
                           "quarantinable": False, "status": "ACTIVE"})
        added += 1
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(m, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{added} V2 task(s) imported -> {out} (total {len(m['tasks'])})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
