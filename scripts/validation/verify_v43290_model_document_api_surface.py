#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

def check(name: str, ok: bool) -> None:
    checks.append((name, bool(ok)))

def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")

contract = text("src/test/java/com/example/editor/architecture/CoreArchitectureContractTests.java")
service = text("src/main/java/com/example/editor/model/ModelDocumentService.java")
controller = text("src/main/java/com/example/editor/model/ModelDocumentController.java")
model_tests = text("src/test/java/com/example/editor/model/ModelDocumentTests.java")
v43288 = text("scripts/validation/verify_v43288_selection_codegen_sql.py")

check(
    "reviewed public API includes bulk delete",
    '"deleteElements(String,List)"' in contract,
)
check(
    "bulk delete remains a public model service operation",
    "public ModelDocumentResponse deleteElements(String modelId, List<String> elementIds)" in service,
)
check(
    "REST controller delegates bulk delete to the reviewed service operation",
    "modelDocumentService.deleteElements(modelId, request.elementIds())" in controller,
)
check(
    "bulk delete functional tests remain present",
    "deletesSeveralContainedElementsInOneAtomicMutation" in model_tests,
)
check(
    "V4.32.88 selection gate still requires bulk delete",
    "deleteElements(String modelId, List<String> elementIds)" in v43288,
)

for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
print(f"V43290_MODEL_DOCUMENT_API_SURFACE={sum(ok for _, ok in checks)}/{len(checks)}")
raise SystemExit(0 if all(ok for _, ok in checks) else 1)
