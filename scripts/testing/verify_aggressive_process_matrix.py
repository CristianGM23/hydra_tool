#!/usr/bin/env python3
"""Static gate for HYDRA's aggressive process coverage matrix.

This gate deliberately avoids Node/Vitest dependencies so release validation can
prove that every module exposed by the application shell has an aggressive
acceptance scenario before the heavier campaign runs.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "frontend/src/application-shell/moduleRegistry.ts"
CATALOG = ROOT / "frontend/src/aggressive-testing/aggressiveProcessCatalog.ts"

module_ids = re.findall(r"\{\s*id:\s*'([A-Z][A-Z0-9_]*)'", REGISTRY.read_text(encoding="utf-8"))
catalog_text = CATALOG.read_text(encoding="utf-8")
scenario_ids = re.findall(r"moduleId:\s*'([A-Z][A-Z0-9_]*)'", catalog_text)

errors: list[str] = []
if not module_ids:
    errors.append("No HYDRA modules were discovered in moduleRegistry.ts")
if not scenario_ids:
    errors.append("No aggressive scenarios were discovered in aggressiveProcessCatalog.ts")

if len(module_ids) != len(set(module_ids)):
    errors.append("moduleRegistry.ts contains duplicate module IDs")
if len(scenario_ids) != len(set(scenario_ids)):
    errors.append("aggressiveProcessCatalog.ts contains duplicate module IDs")

missing = sorted(set(module_ids) - set(scenario_ids))
extra = sorted(set(scenario_ids) - set(module_ids))
if missing:
    errors.append(f"Modules without aggressive scenario: {', '.join(missing)}")
if extra:
    errors.append(f"Aggressive scenarios without registered module: {', '.join(extra)}")

scenario_pattern = re.compile(
    r"\{\s*moduleId:'(?P<module>[A-Z][A-Z0-9_]*)',\s*process:'(?P<process>[^']*)',\s*"
    r"proof:'(?P<proof>[A-Z_]+)',\s*finalEvidence:'(?P<evidence>[^']*)',\s*attacks:\[(?P<attacks>[^\]]*)\]\s*\}"
)
allowed_proofs = {"ARTIFACT", "MUTATION", "ROUND_TRIP", "RECOVERY", "SECURITY", "INVARIANT"}
parsed = list(scenario_pattern.finditer(catalog_text))
if len(parsed) != len(scenario_ids):
    errors.append(f"Only {len(parsed)}/{len(scenario_ids)} aggressive scenarios match the canonical scenario shape")

for match in parsed:
    module = match.group("module")
    process = match.group("process").strip()
    proof = match.group("proof")
    evidence = match.group("evidence").strip()
    attacks = re.findall(r"'([^']+)'", match.group("attacks"))
    if len(process) < 9:
        errors.append(f"{module}: process description is too short")
    if proof not in allowed_proofs:
        errors.append(f"{module}: unsupported proof kind {proof}")
    if len(evidence) < 25:
        errors.append(f"{module}: final evidence is too weak/short")
    if len(attacks) < 3:
        errors.append(f"{module}: requires at least three adversarial attacks")

if errors:
    print("[HYDRA] Aggressive process matrix: FAIL")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print(f"[HYDRA] Aggressive process matrix: PASS ({len(module_ids)}/{len(module_ids)} modules)")
print("[HYDRA] Proof kinds: " + ", ".join(sorted({m.group('proof') for m in parsed})))
