#!/usr/bin/env python3
from __future__ import annotations

"""Static regression gate for actionable transformation diagnostics (R5C13 Fix49C)."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
failures: list[str] = []
passes = 0


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def check(name: str, condition: bool, detail: str = "") -> None:
    global passes
    if condition:
        passes += 1
        print(f"PASS {name}")
    else:
        failures.append(name if not detail else f"{name}: {detail}")
        print(f"FAIL {name}{': ' + detail if detail else ''}")


checker = text("src/main/java/com/example/editor/transformation/TransformationDefinitionChecker.java")
enricher = text("src/main/java/com/example/editor/transformation/TransformationDiagnosticEnricher.java")
export_enricher = text("src/main/java/com/example/editor/transformation/export/TransformationExportDiagnosticEnricher.java")
export_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in (ROOT / "src/main/java/com/example/editor/transformation/export").glob("*.java")
    if path.name != "TransformationExportDiagnosticEnricher.java"
)
translation = text("frontend/src/i18n/translationCatalog.ts")
designer = text("frontend/src/transformation-designer/TransformationDesigner.tsx")
export_panel = text("frontend/src/transformation-designer/TransformationExportPanel.tsx")
tests = text("src/test/java/com/example/editor/transformation/TransformationDefinitionCheckerTests.java")
export_tests = text("src/test/java/com/example/editor/transformation/export/TransformationLanguageAdapterTests.java")

# Every static checker code is either enriched or intentionally handled before the enricher.
checker_codes = sorted(set(re.findall(r'"([A-Z][A-Z0-9_]{3,})"', checker)))
ignore = {"ERROR", "WARNING", "INFO", "M2M", "M2T", "T2M", "FORWARD", "REVERSE", "BIDIRECTIONAL", "STRICT", "BATCH", "INCREMENTAL", "FORWARD_ONLY", "FAIL_FAST", "BEST_EFFORT", "TRACE_EQUIVALENT", "TRACE_REQUIRED"}
checker_codes = [code for code in checker_codes if code not in ignore and ("_" in code or code.endswith("REQUIRED"))]
unhandled_checker = [code for code in checker_codes if code not in enricher and code != "TRANSFORMATION_REQUIRED"]
check("every checker diagnostic has actionable enrichment", not unhandled_checker, ", ".join(unhandled_checker))

# Every export diagnostic requiring explanation must be covered by the export enricher.
export_codes = sorted(set(re.findall(r'"([A-Z][A-Z0-9_]{3,})"', export_sources)))
export_ignore = {"ERROR", "WARNING", "INFO", "EXACT", "PARTIAL", "NONE", "FORWARD", "REVERSE", "M2M", "M2T", "T2M"}
export_codes = [
    code for code in export_codes if code not in export_ignore and (
        any(marker in code for marker in ("REQUIRED", "NOT_", "UNSUPPORTED", "FAILED", "MISMATCH", "COLLISION"))
        or code.startswith("CANONICAL_")
        or code in {"RULE_TYPE_NOT_FOUND", "BINDING_FEATURE_NOT_FOUND", "DIRECT_BINDING_NOT_EXACT", "DIRECTION_NOT_SUPPORTED", "REVERSE_REQUIRES_BIDIRECTIONAL", "METAMODELS_REQUIRED"}
    )
]
unhandled_export = [code for code in export_codes if code not in export_enricher]
check("every export diagnostic has actionable enrichment", not unhandled_export, ", ".join(unhandled_export))

check("root-cause cascade suppression is explicit", all(token in enricher for token in ["UNKNOWN_SOURCE_TYPE", "UNKNOWN_TARGET_TYPE", "UNKNOWN_SOURCE_ATTRIBUTE", "UNKNOWN_TARGET_ATTRIBUTE", "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED"]))
check("duplicate diagnostics are collapsed", "Set<String> seen" in enricher and "seen.add(key)" in enricher)
check("unknown source features list real alternatives", "available IDs include" in enricher and "attributeIdList(sourceClass)" in enricher)
check("type conversion explains both actual types", "TYPE_CONVERSION_REQUIRED" in enricher and "conversion" in enricher.lower() and "source" in enricher.lower() and "target" in enricher.lower())
check("cardinality repair is explicit", "COLLECTION_REDUCTION_REQUIRED" in enricher and "collection" in enricher.lower())
check("required target features explain the missing mapping", "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED" in enricher)
check("expression failures retain evaluator detail", all(token in enricher for token in ["GUARD_", "BINDING_", "GENERATION_GUARD_"]))
check("metamodel drift diagnostic tells users what to re-check", all(token in text("src/main/java/com/example/editor/transformation/TransformationApplicationService.java") for token in ["Classes, attributes or relations", "multiplicities", "representative models"]))
check("export failures identify concrete rule or binding", all(token in export_enricher for token in ["ruleName", "bindingName", "sourceTypeId", "targetFeatureId"]))
check("canonical serialization failure is actionable", "CANONICAL_SERIALIZATION_FAILED" in export_enricher and "serializer" in export_enricher.lower())
check("Spanish fallback never overwrites rich transformation evidence", "isTransformationDiagnosticCode(code)" in translation and "if (isTransformationDiagnosticCode(code)) return input" in translation)
check("designer where-column distinguishes rules and bindings", "rule ${item.ruleId} · binding ${item.bindingId}" in designer)
check("export panel exposes why and repair separately", "How to fix it" in export_panel and "Why" in export_panel)
check("checker regression tests cover real alternatives and cascade suppression", all(token in tests for token in ["explainsUnknownBindingAttributeWithConcreteRuleClassAndAvailableFeatures", "suppressesBindingCascadeWhenTheRuleSourceTypeItselfIsUnknown", "reportsConcreteSourceAndTargetTypesWhenConversionIsRequired"]))
check("export regression tests cover contextual native failures", all(token in export_tests for token in ["exportDiagnosticNamesTheGuardedRuleAndProvidesAConcreteAlternative", "exportDiagnosticExplainsTheActualBindingAndValuePolicyInsteadOfAStaticError"]))
check("old generic Spanish transformation boilerplate is not used by the designer", "Incidencia de la operación" not in designer)

if failures:
    print(f"\n{passes} PASS / {len(failures)} FAIL")
    for failure in failures:
        print(" -", failure)
    raise SystemExit(1)
print(f"\n{passes}/{passes} PASS")
