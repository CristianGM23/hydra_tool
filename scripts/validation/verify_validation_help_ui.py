from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
DESIGNER = ROOT / "frontend" / "src" / "validation-designer"

checks: list[tuple[str, bool, str]] = []

def add(name: str, condition: bool, detail: str = "") -> None:
    checks.append((name, condition, detail))

help_source = (DESIGNER / "ValidationFieldHelp.tsx").read_text(encoding="utf-8")
rule_source = (DESIGNER / "ValidationRuleEditor.tsx").read_text(encoding="utf-8")
manager_source = (DESIGNER / "ValidationRuleManager.tsx").read_text(encoding="utf-8")
expression_source = (DESIGNER / "ExpressionBuilder.tsx").read_text(encoding="utf-8")
quick_fix_source = (DESIGNER / "QuickFixDesigner.tsx").read_text(encoding="utf-8")
css = (DESIGNER / "validation-designer.css").read_text(encoding="utf-8")
tooltip_source = (ROOT / "frontend" / "src" / "design-system" / "HydraTooltip.tsx").read_text(encoding="utf-8")

add("reusable_help_component", all(token in help_source for token in ["FieldLabel", "HelpedField", "HydraTooltip", "HydraFormField"]) and 'role="tooltip"' in tooltip_source)
add("focus_associated_control", "document.getElementById(targetId)?.focus()" in help_source)
add("keyboard_escape_tooltip", "event.key === 'Escape'" in tooltip_source and "setOpenState(false)" in tooltip_source)
add("purpose_and_example", all(token in help_source for token in ["help", "example", "Example"]))
add("accessible_information_button", "Information about" in help_source and all(token in tooltip_source for token in ["aria-controls", "aria-expanded", "aria-label"]))
add("rule_process_guide", all(token in help_source for token in ["How to create a validation rule", "Identify the rule", "Build the condition", "Save and check"]))
add("quick_fix_process_guide", all(token in help_source for token in ["How to create a quick fix", "Add and order operations", "Associate it with a rule"]))
add("accessible_process_dialog", all(token in help_source for token in ["aria-modal=\"true\"", "role=\"dialog\"", "Close guide"]))
add("rule_guide_always_available", "ProcessGuideButton kind=\"RULE\"" in manager_source)
add("quick_fix_guide_always_available", "ProcessGuideButton kind=\"QUICK_FIX\"" in quick_fix_source)

rule_fields = [
    "Name", "Stable problem code", "Severity", "Group", "Execution mode", "Context",
    "Context class", "Target feature", "Description", "Message shown when the rule fails",
    "When should it run?", "Available quick fixes", "Authoritative representation", "Conversion status"
]
add("all_rule_fields_have_help", all(f'label="{field}"' in rule_source for field in rule_fields), f"{sum(f'label=\"{field}\"' in rule_source for field in rule_fields)}/{len(rule_fields)}")
add("expression_fields_have_help", all(f'label="{field}"' in expression_source for field in ["Expression name", "Expression element", "Type", "Value", "Class", "Attribute", "Relation", "Operator", "Collection operation", "Function name"]))

quick_fix_fields = ["Name", "Precondition", "Transaction", "Undo support", "Description"]
quick_fix_core_help = all(f'label="{field}"' in quick_fix_source for field in quick_fix_fields)
parameter_help = all(token in quick_fix_source for token in ['<h4>Quick-fix parameters</h4>', 'label="Parameter name"', 'label="Parameter type"'])
operation_help = all(token in quick_fix_source for token in ['<h4>Operations</h4>', 'label="Operation type"'])
add("all_quick_fix_top_fields_have_help", quick_fix_core_help and parameter_help and operation_help, f"core={sum(f'label=\"{field}\"' in quick_fix_source for field in quick_fix_fields)}/{len(quick_fix_fields)}, parameters={parameter_help}, operations={operation_help}")
add("operation_fields_have_help", "label = 'Value source'" in quick_fix_source and all(f'label="{field}"' in quick_fix_source for field in ["Fixed value", "Expression", "Parameter", "Relation", "Class", "Result name", "Containment relation", "Transformation", "Action", "Command operation"]))
add("selector_fields_have_help", all(token in quick_fix_source for token in ["Selected parameter", "Created element name", "Element ID"]))
add("parameter_fields_have_help", all(token in quick_fix_source for token in ["Parameter name", "Parameter type", "parameter-required-field"]))

add("required_legend_rule", "RequiredFieldsLegend" in rule_source)
add("required_legend_quick_fix", "RequiredFieldsLegend" in quick_fix_source)
add("rule_required_html", all(token in rule_source for token in ["required value={currentRule.name}", "required value={currentRule.code}", "required rows={2} value={currentRule.messageTemplate}"]))
add("quick_fix_required_html", all(token in quick_fix_source for token in ["required value={action.name}", "required value={action.transactionPolicy}", "required value={action.undoPolicy}"]))
add("required_operation_warning", "Add at least one operation before this quick fix can be saved as executable." in quick_fix_source)
add("global_context_no_false_class_field", "structuralContextKinds" in rule_source and "hasStructuralContext" in rule_source)

add("tooltip_styles", all(token in css for token in [".validation-field-info-button", ".validation-field-tooltip", ".validation-required-marker"]))
add("guide_styles", all(token in css for token in [".validation-guide-backdrop", ".validation-guide-dialog", ".validation-process-info-button"]))
add("responsive_help_styles", "@media (max-width: 800px)" in css)
add("manager_actions_below_titles", all(token in css for token in [
    ".validation-rule-manager .validation-panel-heading,",
    ".quick-fix-list .validation-panel-heading",
    "flex-direction: column;",
    ".validation-rule-manager .validation-panel-heading-actions,"
]))
add("label_marker_info_horizontal", all(token in css for token in [
    ".validation-field-label-row > label {",
    "flex-direction: row !important;",
    ".validation-required-marker {",
    "flex-wrap: nowrap;"
]))
add("larger_field_labels", "font-size: 16px;" in css and ".validation-rule-fields label," in css)
add("larger_tooltip_copy", all(token in css for token in [
    ".validation-field-tooltip {",
    "width: min(470px",
    ".validation-field-tooltip strong {",
    ".validation-field-tooltip code {"
]))
add("expanded_rule_tooltips", all(token in rule_source for token in [
    "does not produce diagnostics",
    "enable or disable the contained rules as a set",
    "defines what Current element means inside the condition",
    "including inherited members when they are compatible",
    "not executed automatically"
]))

for name in ["ValidationFieldHelp.test.tsx", "ValidationRuleEditor.help.test.tsx", "QuickFixDesigner.help.test.tsx"]:
    add(f"test:{name}", (DESIGNER / name).is_file())

failed = False
for name, ok, detail in checks:
    print(f"[{'OK' if ok else 'FAIL'}] {name}" + (f": {detail}" if detail else ""))
    failed |= not ok
print(f"\nVALIDATION HELP UI AUDIT: {sum(ok for _, ok, _ in checks)}/{len(checks)} checks passed")
sys.exit(1 if failed else 0)
