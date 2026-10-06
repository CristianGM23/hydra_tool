#!/usr/bin/env python3
"""Audita el patrón de formularios unificado de HYDRA sin depender de Node."""
from __future__ import annotations
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "frontend" / "src"
checks: list[tuple[str, bool]] = []

def require(name: str, condition: bool) -> None:
    checks.append((name, condition))

shared = (SRC / "designer-ui" / "DesignerFieldHelp.tsx").read_text(encoding="utf-8")
shared_accessibility = shared + (SRC / "design-system" / "HydraTooltip.tsx").read_text(encoding="utf-8") + (SRC / "design-system" / "HydraModal.tsx").read_text(encoding="utf-8")
shared_css = (SRC / "designer-ui" / "designer-ui.css").read_text(encoding="utf-8")
for guide in ("EVENT", "EVENT_BINDING", "EVENT_FLOW", "GAMIFICATION", "EDITOR", "REPRESENTATION", "INTEGRATION"):
    require(f"guide {guide}", f"{guide}: {{" in shared)
require("shared helped field", "export function DesignerHelpedField" in shared)
require("shared helped checkbox", "export function DesignerHelpedCheckbox" in shared)
require("shared required legend", "export function DesignerRequiredFieldsLegend" in shared)
require("field tooltip role", 'role="tooltip"' in shared_accessibility and 'HydraTooltip' in shared)
require("guide dialog role", 'role="dialog"' in shared_accessibility and 'HydraModal' in shared)
require("guide close escape", "event.key === 'Escape'" in shared_accessibility)
for selector in (
    ".hydra-designer-master-detail", ".hydra-designer-sidebar", ".hydra-designer-list-item",
    ".hydra-designer-form", ".hydra-helped-field", ".hydra-field-tooltip", ".hydra-guide-dialog"
):
    require(f"shared CSS {selector}", selector in shared_css)

def bundle_text(relative_dir: str) -> str:
    directory = SRC / relative_dir
    return "\n".join(path.read_text(encoding="utf-8") for path in sorted(directory.rglob("*.tsx")))

modules = {
    "events": ("event-designer", ["EVENT", "EVENT_BINDING"], ["Stable event ID", "Binding ID", "Field ID"]),
    "event flows": ("event-designer/flow", ["EVENT_FLOW"], ["Flow ID", "Node ID", "Transition ID"]),
    "gamification": ("gamification-designer", ["GAMIFICATION"], ["Point type ID", "Level ID", "Achievement ID", "Challenge ID", "Reward ID", "Rule ID"]),
    "model editors": ("editor-definition", ["EDITOR", "REPRESENTATION"], ["Editor ID", "Representation ID", "Node mapping ID", "Edge mapping ID", "Tool ID", "Form ID"]),
    "integrations": ("integration-designer", ["INTEGRATION"], ["Profile ID", "Mapping ID", "Document ID"]),
}
for module_name, (relative, guides, identifiers) in modules.items():
    text = bundle_text(relative)
    require(f"{module_name}: master-detail", "hydra-designer-master-detail" in text)
    require(f"{module_name}: sidebar", "hydra-designer-sidebar" in text)
    require(f"{module_name}: helped fields", "DesignerHelpedField" in text)
    require(f"{module_name}: helped checkboxes", "DesignerHelpedCheckbox" in text)
    require(f"{module_name}: required legend", "DesignerRequiredFieldsLegend" in text)
    require(f"{module_name}: search", re.search(r"Search .*", text, re.IGNORECASE) is not None)
    for guide in guides:
        require(f"{module_name}: {guide} guide", f'kind="{guide}"' in text)
    for identifier in identifiers:
        require(f"{module_name}: {identifier}", f'label="{identifier}"' in text)

# Cobertura de campos canónicos especialmente sensibles.
event_text = bundle_text("event-designer")
for label in ("Namespace", "Category", "Version", "Persistence", "Allowed sources", "Allowed subjects", "Source event", "Target event", "Error policy"):
    require(f"event canonical field {label}", f'label="{label}"' in event_text)
flow_text = bundle_text("event-designer/flow")
for label in ("Trigger event types", "Retry attempts", "Retry delay (ms)", "Backoff multiplier", "Timeout (ms)", "Timeout transition", "Configuration JSON"):
    require(f"flow canonical field {label}", f'label="{label}"' in flow_text)
gamification_text = bundle_text("gamification-designer")
for label in ("Verified event type", "Repeat policy", "Cooldown seconds", "Max per actor", "Max per day", "Payload JSON"):
    require(f"gamification canonical field {label}", f'label="{label}"' in gamification_text)
editor_text = bundle_text("editor-definition")
for label in ("View type", "Root classes", "Target class", "Relationship", "Semantic feature", "Visible when", "Enabled when"):
    require(f"editor canonical field {label}", f'label="{label}"' in editor_text)
integration_text = bundle_text("integration-designer")
for label in ("Integration technology", "Delivery mode", "Build system", "Generated folder (relative)", "Integration method", "Document type", "Locator strategy"):
    require(f"integration canonical field {label}", f'label="{label}"' in integration_text)

layout_test = (SRC / "project-explorer" / "MetamodelWorkbenchLayout.test.ts").read_text(encoding="utf-8")
require("layout test reads CSS from disk", "readFileSync" in layout_test and "?raw" not in layout_test)

failed = [name for name, ok in checks if not ok]
for index, (name, ok) in enumerate(checks, start=1):
    print(f"[{index:03d}] {'OK' if ok else 'FAIL'} {name}")
print(f"\nResultado: {len(checks) - len(failed)}/{len(checks)} comprobaciones correctas")
if failed:
    print("Faltan:")
    for name in failed:
        print(f" - {name}")
    sys.exit(1)
