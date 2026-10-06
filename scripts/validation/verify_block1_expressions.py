#!/usr/bin/env python3
"""Auditoría independiente del Bloque 1: ExpressionModel canónico."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "src/main/java/com/example/editor/validation"
SEMANTIC = ROOT / "src/main/java/com/example/editor/semantic/expression"
FRONT = ROOT / "frontend/src/validation"
SCHEMA = ROOT / "src/main/resources/schemas/validation/expression-model-1.0.schema.json"
PROPERTIES = ROOT / "src/main/resources/application.properties"

checks: list[tuple[str, bool, str]] = []

def add(name: str, condition: bool, detail: str = "") -> None:
    checks.append((name, condition, detail))

java_files = list(MAIN.rglob("*.java"))
java_text = "\n".join(path.read_text(encoding="utf-8") for path in java_files)

add("validation_sources_exist", len(java_files) >= 40, f"{len(java_files)} Java files")
add("no_emf_dependency", not re.search(r"^import\s+org\.eclipse\.emf\.", java_text, re.MULTILINE), "validation packages must be Ecore-free")
add("no_eobject_or_eclass", not re.search(r"^import\s+.*\b(EObject|EClass|EAttribute|EReference)\b", java_text, re.MULTILINE), "canonical validation contracts cannot import EMF model types")
add("expression_schema_exists", SCHEMA.is_file(), str(SCHEMA))
if SCHEMA.is_file():
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    kinds = schema.get("$defs", {}).get("node", {}).get("oneOf", [])
    add("polymorphic_schema", len(kinds) >= 10, f"{len(kinds)} expression node variants")
else:
    add("polymorphic_schema", False, "schema missing")

required = [
    SEMANTIC / "ExpressionTypeChecker.java",
    SEMANTIC / "ExpressionDependencyAnalyzer.java",
    SEMANTIC / "ExpressionEvaluator.java",
    MAIN / "expression/persistence/JsonExpressionModelRepository.java",
    MAIN / "expression/web/CanonicalExpressionController.java",
    MAIN / "model/CanonicalModelDocument.java",
]
add("required_services", all(path.is_file() for path in required), ", ".join(path.name for path in required if not path.is_file()))
add("frontend_contract", (FRONT / "types.ts").is_file() and (FRONT / "api.ts").is_file(), str(FRONT))

props = PROPERTIES.read_text(encoding="utf-8") if PROPERTIES.is_file() else ""
add("configuration_registered", "dsl.validation.expressions.enabled=true" in props and ".expressions.json" in props, "application.properties")

controller = (MAIN / "expression/web/CanonicalExpressionController.java").read_text(encoding="utf-8")
for operation in ["/expressions", "/check", "/evaluate"]:
    add(f"endpoint_{operation}", operation in controller, operation)

repo = (MAIN / "expression/persistence/JsonExpressionModelRepository.java").read_text(encoding="utf-8")
add("atomic_persistence", "ATOMIC_MOVE" in repo and ".tmp" in repo, "temporary file + atomic move")
add("etag", "SHA-256" in repo and "verifyEtag" in repo, "optimistic concurrency")
add("stable_locking", "locks.values().remove" not in repo and "lock.unlock()" in repo, "one lock identity per document")
repository_tests = (ROOT / "src/test/java/com/example/editor/validation/expression/persistence/JsonExpressionModelRepositoryTests.java")
add("concurrency_test", repository_tests.is_file() and "concurrentReplacementsWithTheSameEtagAllowExactlyOneWriter" in repository_tests.read_text(encoding="utf-8"), "three writers with one ETag")

lifecycle = (ROOT / "src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java")
impact = (ROOT / "src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java")
add("lifecycle_cleanup", lifecycle.is_file() and "expressionModelRepository" in lifecycle.read_text(encoding="utf-8") and impact.is_file() and "expressionFile" in impact.read_text(encoding="utf-8"), "metamodel deletion includes expressions")

failed = False
for name, ok, detail in checks:
    status = "OK" if ok else "FAIL"
    print(f"[{status}] {name}" + (f": {detail}" if detail else ""))
    failed |= not ok

print(f"\nResult: {sum(ok for _, ok, _ in checks)}/{len(checks)} checks passed")
sys.exit(1 if failed else 0)
