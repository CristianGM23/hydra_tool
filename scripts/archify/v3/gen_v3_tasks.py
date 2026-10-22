#!/usr/bin/env python3
"""Adds/refreshes one maven task per semantic fragment in knowledge/tests/v3/fragments/*.json.
Idempotent: V2-imported tasks are never touched; tasks it created earlier (generatedFromFragment) are regenerated.
Usage: python scripts/archify/v3/gen_v3_tasks.py"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
V3 = ROOT / "docs/architecture/archify/knowledge/tests/v3"
MAN = V3 / "MANIFEST_CERTIFICACION_V3.json"


def kebab(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "-", name).lower()).strip("-")


def main() -> None:
    man = json.loads(MAN.read_text(encoding="utf-8-sig"))
    keep = [t for t in man["tasks"] if not t.get("generatedFromFragment")]
    new = []
    claimed = {t["fragment"] for t in keep if t.get("fragment")}  # fragments already owned by a hand-written/remap task
    for fp in sorted((V3 / "fragments").glob("*.json")):
        if f"fragments/{fp.name}" in claimed:
            continue
        recs = json.loads(fp.read_text(encoding="utf-8-sig"))
        for r in recs:
            if r["id"].startswith("pw:e2e/real/"):
                spec = r["id"][3:]
                if spec == "e2e/real/real-backend-smoke.spec.ts" or not (ROOT / "frontend" / spec).exists():
                    continue  # the smoke task is hand-written; missing specs are reported by validate-fragment
                scope = [{"processId": p["id"], "stageId": s} for p in r.get("procs", []) if p.get("match") == "direct" for s in p.get("stages", [])]
                stem = spec[len("e2e/real/"):].replace(".spec.ts", "")
                new.append({
                    "id": "v3.e2e-" + kebab(stem.replace("/", "-"))[:70],
                    "kind": "playwright",
                    "specs": [spec],
                    "config": "playwright.real.config.ts",
                    "prepare": ["maven-classpath", "frontend-dist"],
                    "inputGlobs": ["src/main/java/**/*.java", "src/main/resources/**/*", "frontend/src/**/*.ts", "frontend/src/**/*.tsx",
                                   "frontend/e2e/real/" + stem.split("/")[0] + "/helpers.ts", "frontend/e2e/real/support.ts"],
                    "scope": scope,
                    "oracle": (r.get("scenario") or {}).get("es", "") or (r.get("behavior") or {}).get("es", ""),
                    "expected": "Playwright: todos los tests del spec PASS contra el backend real; el fragmento solo se fusiona si PASA.",
                    "fragment": f"fragments/{fp.name}",
                    "generatedFromFragment": True,
                    "priority": 6,
                    "regressionOnly": not scope,
                    "status": "ACTIVE",
                })
                continue
            if not r["id"].startswith("java:"):
                continue  # vitest: and other records are not generated here
            if r.get("type") == "OTHER" or r.get("caseCount") == 0:
                continue  # support classes ride along in a sibling record, never a task
            fqn = r["id"].split(":", 1)[1]
            test_path = "src/test/java/" + fqn.replace(".", "/") + ".java"
            if not (ROOT / test_path).exists():
                print("MISSING test file", test_path)
                continue
            inputs = [test_path] + [p for p in r.get("prod", []) if (ROOT / p).exists()]
            scope = [{"processId": p["id"], "stageId": s} for p in r.get("procs", []) if p.get("match") == "direct" for s in p.get("stages", [])]
            simple = fqn.rsplit(".", 1)[1]
            new.append({
                "id": "v3." + kebab(simple)[:70],
                "kind": "maven",
                "selectors": [fqn],
                "inputs": inputs,
                "scope": scope,
                "oracle": (r.get("scenario") or {}).get("es", "") or (r.get("behavior") or {}).get("es", ""),
                "expected": f"Surefire: {simple} sin Failures/Errors/Skipped; el fragmento solo se fusiona si PASA.",
                "fragment": f"fragments/{fp.name}",
                "generatedFromFragment": True,
                "priority": 5,
                "regressionOnly": not scope,
                "quarantinable": True,
                "status": "ACTIVE",
            })
    man["tasks"] = keep + new
    MAN.write_text(json.dumps(man, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"kept {len(keep)} imported, generated {len(new)} tasks")


if __name__ == "__main__":
    main()
