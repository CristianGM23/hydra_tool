#!/usr/bin/env python3
"""Independent audit for Block 2: canonical ValidationModel 1.0."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "src/main/java/com/example/editor/validation"
PACKAGE = MAIN / "validation"
SCHEMA = ROOT / "src/main/resources/schemas/validation/validation-model-1.0.schema.json"
EXAMPLE = ROOT / "src/test/resources/validation/contracts/examples/library.validations.json"
PROPERTIES = ROOT / "src/main/resources/application.properties"
FRONT = ROOT / "frontend/src/validation"
TESTS = ROOT / "src/test/java/com/example/editor/validation/validation"
LIFECYCLE = ROOT / "src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java"
IMPACT = ROOT / "src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java"

checks: list[tuple[str, bool, str]] = []

def add(name: str, ok: bool, detail: str = "") -> None:
    checks.append((name, ok, detail))

required = [
    PACKAGE / "ValidationModel.java",
    PACKAGE / "ValidationRule.java",
    PACKAGE / "ValidationModelChecker.java",
    PACKAGE / "ValidationModelApplicationService.java",
    PACKAGE / "persistence/JsonValidationModelRepository.java",
    PACKAGE / "web/CanonicalValidationController.java",
]
add("required_components", all(path.is_file() for path in required), ", ".join(path.name for path in required if not path.is_file()))

java_files = list(PACKAGE.rglob("*.java"))
java_text = "\n".join(path.read_text(encoding="utf-8") for path in java_files)
add("technology_independent", not re.search(r"^import\s+(org\.eclipse\.emf|com\.example\.editor\.emf|com\.example\.editor\.metamodel\.adapter\.ecore|com\.example\.editor\.metamodel\.projection)", java_text, re.MULTILINE), "ValidationModel must not import external modeling runtimes")

add("schema_and_example", SCHEMA.is_file() and EXAMPLE.is_file(), "ValidationModel schema and example")
if SCHEMA.is_file() and EXAMPLE.is_file():
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    example = json.loads(EXAMPLE.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    errors = list(Draft202012Validator(schema).iter_errors(example))
    add("example_validates", not errors, errors[0].message if errors else "library.validations.json")
    add("canonical_identity", example.get("id") == "library-validations" and example.get("metamodelId") == "library", "document id is independent from metamodel id")
else:
    add("example_validates", False, "missing input")
    add("canonical_identity", False, "missing input")

checker = (PACKAGE / "ValidationModelChecker.java").read_text(encoding="utf-8") if (PACKAGE / "ValidationModelChecker.java").is_file() else ""
for token in ["DUPLICATE_RULE_ID", "CONDITION_NOT_BOOLEAN", "CONTEXT_FEATURE_OWNER_MISMATCH", "MISSING_DECLARED_DEPENDENCY", "UNKNOWN_QUICK_FIX", "ACTION_MODEL_REQUIRED"]:
    add(f"checker_{token.lower()}", token in checker, token)
add("inheritance_aware_context", "featureBelongsToClass" in checker and "isSameOrSubtype" in (MAIN / "support/CanonicalMetamodelIndex.java").read_text(encoding="utf-8"), "inherited features are accepted")

controller = (PACKAGE / "web/CanonicalValidationController.java").read_text(encoding="utf-8") if (PACKAGE / "web/CanonicalValidationController.java").is_file() else ""
for token in ["/validations", "/check", "HttpHeaders.IF_MATCH", "HttpHeaders.ETAG"]:
    add(f"api_{token.lower().replace('/', '_')}", token in controller, token)

props = PROPERTIES.read_text(encoding="utf-8") if PROPERTIES.is_file() else ""
add("configuration", "dsl.validation.rules.enabled=true" in props and ".validations.json" in props and "dsl.validation.rules.use-etag=true" in props, "application.properties")
add("frontend_contract", (FRONT / "validationApi.ts").is_file() and (FRONT / "validationApi.test.ts").is_file() and "ValidationModel" in (FRONT / "types.ts").read_text(encoding="utf-8"), "TypeScript API and types")
add("specific_tests", (TESTS / "ValidationModelCheckerTests.java").is_file() and (TESTS / "persistence/JsonValidationModelRepositoryTests.java").is_file() and (TESTS / "web/CanonicalValidationControllerTests.java").is_file(), "checker, repository and controller")

lifecycle = LIFECYCLE.read_text(encoding="utf-8") if LIFECYCLE.is_file() else ""
impact = IMPACT.read_text(encoding="utf-8") if IMPACT.is_file() else ""
add("lifecycle_cleanup", "validationModelRepository.deleteIfExists" in lifecycle and "validationFile" in impact and "validationExists" in impact, "metamodel deletion includes validations")

failed = False
for name, ok, detail in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {name}" + (f": {detail}" if detail else ""))
    failed |= not ok
print(f"\nResult: {sum(ok for _, ok, _ in checks)}/{len(checks)} checks passed")
sys.exit(1 if failed else 0)
