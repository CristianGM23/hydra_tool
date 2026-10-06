#!/usr/bin/env python3
"""Independent audit for Block 3: canonical ActionModel 1.0 and quick fixes."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "src/main/java/com/example/editor/validation"
PACKAGE = MAIN / "action"
SCHEMA = ROOT / "src/main/resources/schemas/validation/action-model-1.0.schema.json"
EXAMPLE = ROOT / "src/test/resources/validation/contracts/examples/library.actions.json"
PROPERTIES = ROOT / "src/main/resources/application.properties"
FRONT = ROOT / "frontend/src/validation"
TESTS = ROOT / "src/test/java/com/example/editor/validation/action"
LIFECYCLE = ROOT / "src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java"
IMPACT = ROOT / "src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java"

checks: list[tuple[str, bool, str]] = []
def add(name: str, ok: bool, detail: str = "") -> None:
    checks.append((name, ok, detail))

required = [
    PACKAGE / "ActionModel.java",
    PACKAGE / "ActionDefinition.java",
    PACKAGE / "ActionOperation.java",
    PACKAGE / "ActionModelChecker.java",
    PACKAGE / "ActionModelApplicationService.java",
    PACKAGE / "persistence/JsonActionModelRepository.java",
    PACKAGE / "web/CanonicalActionController.java",
]
add("required_components", all(path.is_file() for path in required), ", ".join(path.name for path in required if not path.is_file()))

java_files = list(PACKAGE.rglob("*.java"))
java_text = "\n".join(path.read_text(encoding="utf-8") for path in java_files)
add("technology_independent", not re.search(r"^import\s+(org\.eclipse\.emf|com\.example\.editor\.emf|com\.example\.editor\.metamodel\.adapter\.ecore|com\.example\.editor\.metamodel\.projection)", java_text, re.MULTILINE), "ActionModel must not import external modeling runtimes")

add("schema_and_example", SCHEMA.is_file() and EXAMPLE.is_file(), "ActionModel schema and example")
if SCHEMA.is_file() and EXAMPLE.is_file():
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    example = json.loads(EXAMPLE.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    errors = list(Draft202012Validator(schema).iter_errors(example))
    add("example_validates", not errors, errors[0].message if errors else "library.actions.json")
    add("canonical_identity", example.get("id") == "library-actions" and example.get("metamodelId") == "library", "document id is independent from metamodel id")
else:
    add("example_validates", False, "missing input")
    add("canonical_identity", False, "missing input")

operation_types = [
    "SetAttributeOperation", "ClearAttributeOperation", "CopyAttributeOperation",
    "AddRelationOperation", "RemoveRelationOperation", "CreateElementOperation",
    "DeleteElementOperation", "TransformTextOperation", "ExecuteActionOperation",
]
add("declarative_operations", all((PACKAGE / f"{name}.java").is_file() for name in operation_types), f"{sum((PACKAGE / f'{name}.java').is_file() for name in operation_types)}/{len(operation_types)}")

action_operation = (PACKAGE / "ActionOperation.java").read_text(encoding="utf-8") if (PACKAGE / "ActionOperation.java").is_file() else ""
add("closed_polymorphic_contract", "sealed interface ActionOperation" in action_operation and all(name in action_operation for name in operation_types), "sealed operation hierarchy")

checker = (PACKAGE / "ActionModelChecker.java").read_text(encoding="utf-8") if (PACKAGE / "ActionModelChecker.java").is_file() else ""
for token in ["UNKNOWN_ATTRIBUTE", "UNKNOWN_RELATION", "UNKNOWN_CREATED_ELEMENT", "DIRECT_ACTION_RECURSION", "ACTION_EXECUTION_CYCLE", "ATOMIC_ACTION_REQUIRES_UNDO", "PRECONDITION_NOT_BOOLEAN"]:
    add(f"checker_{token.lower()}", token in checker, token)

controller = (PACKAGE / "web/CanonicalActionController.java").read_text(encoding="utf-8") if (PACKAGE / "web/CanonicalActionController.java").is_file() else ""
for token in ["/actions", "/check", "HttpHeaders.IF_MATCH", "HttpHeaders.ETAG"]:
    add(f"api_{token.lower().replace('/', '_')}", token in controller, token)

store = (MAIN / "persistence/AtomicJsonDocumentStore.java").read_text(encoding="utf-8") if (MAIN / "persistence/AtomicJsonDocumentStore.java").is_file() else ""
add("atomic_etag_storage", "ATOMIC_MOVE" in store and "SHA-256" in store and "PRECONDITION_FAILED" in store, "shared atomic persistence")

props = PROPERTIES.read_text(encoding="utf-8") if PROPERTIES.is_file() else ""
add("configuration", "dsl.validation.actions.enabled=true" in props and ".actions.json" in props and "dsl.validation.actions.use-etag=true" in props, "application.properties")
add("frontend_contract", (FRONT / "actionApi.ts").is_file() and (FRONT / "actionApi.test.ts").is_file() and "ActionModel" in (FRONT / "types.ts").read_text(encoding="utf-8"), "TypeScript API and types")
add("specific_tests", (TESTS / "ActionModelCheckerTests.java").is_file() and (TESTS / "persistence/JsonActionModelRepositoryTests.java").is_file() and (TESTS / "web/CanonicalActionControllerTests.java").is_file(), "checker, repository and controller")

lifecycle = LIFECYCLE.read_text(encoding="utf-8") if LIFECYCLE.is_file() else ""
impact = IMPACT.read_text(encoding="utf-8") if IMPACT.is_file() else ""
add("lifecycle_cleanup", "actionModelRepository.deleteIfExists" in lifecycle and "actionFile" in impact and "actionExists" in impact, "metamodel deletion includes actions")

failed = False
for name, ok, detail in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {name}" + (f": {detail}" if detail else ""))
    failed |= not ok
print(f"\nResult: {sum(ok for _, ok, _ in checks)}/{len(checks)} checks passed")
sys.exit(1 if failed else 0)
