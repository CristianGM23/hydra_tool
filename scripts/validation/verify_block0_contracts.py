#!/usr/bin/env python3
"""Valida los contratos arquitectónicos del Bloque 0 sin requerir Maven."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / "src/main/resources/schemas/validation"
EXAMPLES = ROOT / "src/test/resources/validation/contracts/examples"
CONTRACT_PACKAGE = ROOT / "src/main/java/com/example/editor/validation/contract"

PAIRS = {
    "library.model.json": "canonical-model-document-1.0.schema.json",
    "library.expressions.json": "expression-model-1.0.schema.json",
    "library.validations.json": "validation-model-1.0.schema.json",
    "library.actions.json": "action-model-1.0.schema.json",
    "library.validation-report.json": "validation-report-1.0.schema.json",
    "library.executable-plan.json": "executable-validation-plan-1.0.schema.json",
}

FORBIDDEN_IMPORTS = (
    "org.eclipse.emf",
    "com.example.editor.emf",
    "com.example.editor.metamodel.adapter.ecore",
    "com.example.editor.metamodel.projection",
)


def check(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def main() -> int:
    errors: list[str] = []
    results: list[str] = []

    for example_name, schema_name in PAIRS.items():
        schema_path = SCHEMAS / schema_name
        example_path = EXAMPLES / example_name
        check(schema_path.is_file(), f"Missing schema: {schema_path}", errors)
        check(example_path.is_file(), f"Missing example: {example_path}", errors)
        if not schema_path.is_file() or not example_path.is_file():
            continue

        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        example = json.loads(example_path.read_text(encoding="utf-8"))
        check(schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Wrong draft: {schema_name}", errors)
        check(schema.get("type") == "object", f"Root must be object: {schema_name}", errors)
        check(schema.get("additionalProperties") is False, f"Root must be closed: {schema_name}", errors)
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        validation_errors = sorted(validator.iter_errors(example), key=lambda item: list(item.path))
        if validation_errors:
            for validation_error in validation_errors:
                errors.append(f"{example_name} at {list(validation_error.path)}: {validation_error.message}")
        else:
            results.append(f"Schema example valid: {example_name}")

    java_files = sorted(CONTRACT_PACKAGE.glob("*.java"))
    check(bool(java_files), "No Java contract files found", errors)
    for java_file in java_files:
        source = java_file.read_text(encoding="utf-8")
        for forbidden in FORBIDDEN_IMPORTS:
            check(f"import {forbidden}" not in source, f"Forbidden dependency in {java_file.name}: {forbidden}", errors)
    if java_files:
        results.append(f"Technology-independent Java contracts: {len(java_files)}")

    metamodel_example = json.loads((EXAMPLES / "library.metamodel.json").read_text(encoding="utf-8"))
    metamodel_ids = {item["id"] for item in metamodel_example["classes"]}
    metamodel_ids.update(attribute["id"] for item in metamodel_example["classes"] for attribute in item["attributes"])
    metamodel_ids.update(item["id"] for item in metamodel_example["relations"])

    model_example = json.loads((EXAMPLES / "library.model.json").read_text(encoding="utf-8"))
    relation = model_example["relations"][0]
    check("relationId" in relation, "Canonical relation must use relationId", errors)
    for element in model_example["elements"]:
        check(element["classId"] in metamodel_ids, f"Unknown classId in model: {element['classId']}", errors)
        for attribute_id in element["attributes"]:
            check(attribute_id in metamodel_ids, f"Unknown attributeId in model: {attribute_id}", errors)
    check(relation["relationId"] in metamodel_ids, f"Unknown relationId in model: {relation['relationId']}", errors)
    check("referenceName" not in relation, "Canonical relation must not use editable referenceName", errors)

    validation_example = json.loads((EXAMPLES / "library.validations.json").read_text(encoding="utf-8"))
    check(validation_example["rules"][0]["authoritativeRepresentation"] == "CANONICAL", "Created example rule must be canonical", errors)
    results.append("Stable cross-document references checked")

    print("BLOCK 0 CONTRACT VERIFICATION")
    print("=" * 31)
    for result in results:
        print(f"[OK] {result}")
    if errors:
        for error in errors:
            print(f"[ERROR] {error}")
        return 1
    print(f"[OK] All {len(results)} verification groups passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
