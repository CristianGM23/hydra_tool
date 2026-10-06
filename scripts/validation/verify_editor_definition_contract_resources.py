#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = {
    "schemas/editor/editor-definition-model-1.0.schema.json": ("EditorDefinitionModel 1.0", "representations", "palette"),
    "examples/editor/people-basic-editor.json": ("people-basic-editor", "tool:create-person", "representation:main"),
}

def main() -> int:
    passed = 0
    for relative, tokens in REQUIRED.items():
        source = ROOT / "src/main/resources" / relative
        if not source.is_file():
            print(f"FAIL missing source resource {relative}")
            return 1
        try:
            json.loads(source.read_text(encoding="utf-8"))
        except Exception as exc:
            print(f"FAIL invalid JSON {relative}: {exc}")
            return 1
        text = source.read_text(encoding="utf-8")
        missing = [token for token in tokens if token not in text]
        if missing:
            print(f"FAIL semantic markers missing from {relative}: {missing}")
            return 1
        print(f"PASS source contract resource {relative}")
        passed += 1

    pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
    required_pom_tokens = [
        "hydra-copy-editor-contract-resources",
        "<filtering>false</filtering>",
        "schemas/editor/editor-definition-model-1.0.schema.json",
        "examples/editor/people-basic-editor.json",
        "${project.build.outputDirectory}",
    ]
    missing = [token for token in required_pom_tokens if token not in pom]
    if missing:
        print(f"FAIL Maven classpath copy contract incomplete: {missing}")
        return 1
    print("PASS Maven classpath copy contract is explicit and non-filtered")
    passed += 1
    print(f"EDITOR_DEFINITION_CONTRACT_RESOURCES = {passed}/{passed} PASS")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
