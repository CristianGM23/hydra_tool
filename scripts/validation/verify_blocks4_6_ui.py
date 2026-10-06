from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
FRONTEND = ROOT / "frontend" / "src"
DESIGNER = FRONTEND / "validation-designer"

checks: list[tuple[str, bool]] = []

def check(name: str, condition: bool) -> None:
    checks.append((name, condition))

required_files = [
    "ValidationDesigner.tsx",
    "ValidationRuleManager.tsx",
    "ValidationRuleEditor.tsx",
    "ExpressionBuilder.tsx",
    "QuickFixDesigner.tsx",
    "validationDesignerFactory.ts",
    "validationDesignerSelectors.ts",
    "validationDesignerValidation.ts",
    "validation-designer.css",
]
for name in required_files:
    check(f"file:{name}", (DESIGNER / name).is_file())

all_source = "\n".join(path.read_text(encoding="utf-8") for path in DESIGNER.glob("*.ts*") if not path.name.endswith(".test.ts") and not path.name.endswith(".test.tsx"))
metamodel_designer = (FRONTEND / "metamodel-designer" / "MetamodelDesigner.tsx").read_text(encoding="utf-8")
validation_designer = (DESIGNER / "ValidationDesigner.tsx").read_text(encoding="utf-8")
expression_builder = (DESIGNER / "ExpressionBuilder.tsx").read_text(encoding="utf-8")
quick_fix_designer = (DESIGNER / "QuickFixDesigner.tsx").read_text(encoding="utf-8")
rule_manager = (DESIGNER / "ValidationRuleManager.tsx").read_text(encoding="utf-8")
preflight = (DESIGNER / "validationDesignerValidation.ts").read_text(encoding="utf-8")
http_source = (FRONTEND / "validation" / "http.ts").read_text(encoding="utf-8")

check("metamodel:validation-tab", "LazyValidationDesigner" in metamodel_designer and "designerMode === 'VALIDATIONS'" in metamodel_designer)
check("metamodel:canonical-dirty-guard", "savedMetamodelSnapshot === draftSnapshot" in metamodel_designer and "const draftSnapshot = useMemo(() => JSON.stringify(draft)" in metamodel_designer)
check("rules:create-duplicate-delete", all(token in validation_designer + rule_manager for token in ["createRule", "duplicateRule", "deleteRule"]))
check("rules:groups-and-filters", all(token in rule_manager for token in ["addRuleSet", "Search validations", "Filter by severity", "Add rule to group"]))
save_body = validation_designer[validation_designer.index("async function saveAll"):validation_designer.index("    return (", validation_designer.index("async function saveAll"))]
check("rules:backend-save-order", save_body.find("createExpressionModel") < save_body.find("createActionModel") < save_body.find("createValidationModel"))
check("rules:etag-aware-replacement", all(token in validation_designer for token in ["expressionEtag", "actionEtag", "validationEtag", "replaceExpressionModel", "replaceActionModel", "replaceValidationModel"]))
check("rules:missing-documents-supported", "isMissingValidationResource" in validation_designer and "ValidationApiError" in http_source)
check("rules:auto-dependencies", "inferExpressionDependencies" in validation_designer)
check("rules:diagnostics-view", "DiagnosticsPanel" in validation_designer and "Definition diagnostics" in validation_designer)
check("rules:explicit-backend-check", all(token in validation_designer for token in ["checkExpressionModel", "checkActionModel", "checkValidationModel", "Check definitions"]))
check("rules:partial-save-retry", all(token in validation_designer for token in ["setExpressionDirty(false)", "setActionDirty(false)", "setValidationDirty(false)"]))
check("rules:unsaved-metamodel-blocked", "Save the metamodel before designing validations" in validation_designer)
check("rules:nested-diagnostics", all(token in validation_designer for token in ["expression?.expressions", "action?.actions", "validation?.rules"]))

expression_kinds = ["LITERAL", "SELF", "MODEL", "CLASS_INSTANCES", "ATTRIBUTE_ACCESS", "RELATION_NAVIGATION", "UNARY", "BINARY", "COLLECTION", "CONDITIONAL", "FUNCTION"]
check("expressions:all-node-kinds", all(kind in expression_builder for kind in expression_kinds))
check("expressions:logical-groups", all(operator in expression_builder for operator in ["AND", "OR", "NOT"]))
check("expressions:comparisons", all(operator in expression_builder for operator in ["EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_OR_EQUAL", "LESS_THAN", "LESS_OR_EQUAL"]))
check("expressions:collection-operations", all(operator in expression_builder for operator in ["EXISTS", "FOR_ALL", "NONE", "IS_UNIQUE", "SELECT", "COUNT", "SUM", "MIN", "MAX"]))
check("expressions:no-external-runtime-import", not re.search(r"from\s+['\"].*(ecore|ocl|evl|sirius|xtext)", all_source, re.IGNORECASE))

operation_kinds = ["SET_ATTRIBUTE", "CLEAR_ATTRIBUTE", "COPY_ATTRIBUTE", "ADD_RELATION", "REMOVE_RELATION", "CREATE_ELEMENT", "DELETE_ELEMENT", "TRANSFORM_TEXT", "EXECUTE_ACTION"]
check("quickfix:all-operation-kinds", all(kind in quick_fix_designer for kind in operation_kinds))
check("quickfix:transactions-and-undo", all(token in quick_fix_designer for token in ["ATOMIC", "BEST_EFFORT", "REQUIRED", "OPTIONAL", "NOT_SUPPORTED"]))
check("quickfix:parameters", all(token in quick_fix_designer for token in ["Quick-fix parameters", "+ Parameter", "Parameter type"]))
check("quickfix:preconditions", "preconditionExpressionId" in quick_fix_designer)
check("quickfix:rule-association-cleanup", "remainingActionIds" in validation_designer and "quickFixIds.filter" in validation_designer)

check("preflight:empty-action-blocked", "needs at least one operation" in preflight)
check("preflight:required-references", all(token in preflight for token in ["must select an attribute", "must select a relation", "must select the class to create"]))
check("preflight:value-source", "fixed value or an expression" in preflight)
check("quickfix:containment-selector-cleanup", "container: undefined" in quick_fix_designer and "container: operation.container" in quick_fix_designer)
check("preflight:stable-id-policy", "isStableElementId" in preflight and "stableIdentifierPolicy" in preflight)

expected_tests = [
    "validationDesignerFactory.test.ts",
    "validationDesignerValidation.test.ts",
    "ExpressionBuilder.test.tsx",
    "ValidationRuleManager.test.tsx",
    "QuickFixDesigner.test.tsx",
    "ValidationDesigner.test.tsx",
]
check("tests:all-specific-files", all((DESIGNER / name).is_file() for name in expected_tests))
check("styles:responsive", "@media (max-width: 1100px)" in (DESIGNER / "validation-designer.css").read_text(encoding="utf-8"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {name}")
print(f"\nBLOCKS 4-6 UI AUDIT: {len(checks) - len(failed)}/{len(checks)} checks passed")
if failed:
    print("Failed checks:")
    for name in failed:
        print(f"- {name}")
    sys.exit(1)
