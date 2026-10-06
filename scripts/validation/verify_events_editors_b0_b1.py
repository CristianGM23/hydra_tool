from __future__ import annotations

import json
from pathlib import Path
import sys

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def exists(path: str) -> bool:
    return (ROOT / path).is_file()


def check(name: str, condition: bool) -> None:
    checks.append((name, bool(condition)))


def contains(path: str, *values: str) -> bool:
    content = text(path)
    return all(value in content for value in values)

# B0 — contratos, persistencia y bus de eventos.
event_files = [
    "src/main/java/com/example/editor/event/CanonicalEvent.java",
    "src/main/java/com/example/editor/event/EventDefinitionModel.java",
    "src/main/java/com/example/editor/event/EventBindingModel.java",
    "src/main/java/com/example/editor/event/EventModelChecker.java",
    "src/main/java/com/example/editor/event/ReservedEventCatalog.java",
    "src/main/java/com/example/editor/event/bus/CanonicalEventBus.java",
    "src/main/java/com/example/editor/event/bus/InMemoryCanonicalEventBus.java",
    "src/main/java/com/example/editor/event/bus/EventPublicationBuffer.java",
    "src/main/java/com/example/editor/event/EventModelApplicationService.java",
    "src/main/java/com/example/editor/event/web/CanonicalEventModelController.java",
]
for path in event_files:
    check(f"B0 file exists: {path}", exists(path))

check("CanonicalEvent is immutable record", contains(event_files[0], "record CanonicalEvent", "occurredAt", "correlationId", "causationId", "immutableObjectMap"))
check("CanonicalEvent uses shared stable IDs", contains(event_files[0], "StableIdentifierPolicy.requireElementId"))
check("Event definitions are separate documents", contains(event_files[1], "record EventDefinitionModel", "eventTypes"))
check("Event bindings are separate documents", contains(event_files[2], "record EventBindingModel", "eventDefinitionModelId", "expressionModelId"))
check("Reserved events cannot be redefined", contains(event_files[3], "RESERVED_EVENT_TYPE", "ReservedEventCatalog.isReserved"))
check("Checker resolves source and target event types", contains(event_files[3], "UNKNOWN_SOURCE_EVENT", "UNKNOWN_TARGET_EVENT"))
check("Checker validates required mappings", contains(event_files[3], "REQUIRED_FIELD_NOT_MAPPED", "DUPLICATE_TARGET_MAPPING"))
check("Bus supports subscriptions", contains(event_files[5], "subscribe", "EventSubscription"))
check("Bus continues after subscriber failures", contains(event_files[6], "List<RuntimeException> failures", "EventDeliveryException"))
check("Publication buffer separates commit and rollback", contains(event_files[7], "commit()", "rollback()", "EventPublicationException"))
check("Event service compensates failed creation", contains(event_files[8], "definitionRepository.deleteIfExists"))
check("Event service compensates failed replacement", contains(event_files[8], "previousDefinitions", "rollbackFailure"))
check("Event service compensates failed deletion", contains(event_files[8], "previousBindings", "bindingRepository.create"))
check("Event API uses both ETags", contains(event_files[9], "If-Match-Definitions", "If-Match-Bindings"))
check("Event properties use an independent directory", contains("src/main/resources/application.properties", "dsl.events.directory=./data/events"))
event_frontend = text("frontend/src/event-designer/EventDesigner.tsx") + text("frontend/src/event-designer/EventDesignerParts.tsx") + text("frontend/src/event-designer/eventStructureModel.ts")
check("Event designer exposes types and bindings", all(token in event_frontend for token in ("Custom event types", "Selected binding", "payloadMappings")))
check("Event designer states optional gamification", contains("frontend/src/event-designer/EventDesigner.tsx", "independent from optional gamification"))
check("Event frontend preflight checks required mappings", contains("frontend/src/event-designer/eventDesignerValidation.ts", "must map required field"))
check("Event frontend sends two ETags", contains("frontend/src/event-designer/api.ts", "If-Match-Definitions", "If-Match-Bindings"))

# JSON schemas and examples.
event_definition_schema = json.loads(text("src/main/resources/schemas/event/event-definition-model-1.0.schema.json"))
event_binding_schema = json.loads(text("src/main/resources/schemas/event/event-binding-model-1.0.schema.json"))
canonical_event_schema = json.loads(text("src/main/resources/schemas/event/canonical-event-1.0.schema.json"))
event_example = json.loads(text("src/main/resources/examples/event/people-events.json"))
check("Event definition schema is Draft 2020-12", event_definition_schema.get("$schema", "").endswith("2020-12/schema"))
check("Event binding schema describes bindings", "binding" in event_binding_schema.get("$defs", {}))
check("Canonical event schema describes actor and subject", {"actor", "subject"}.issubset(canonical_event_schema.get("$defs", {})))
check("Event definition example validates", not list(Draft202012Validator(event_definition_schema).iter_errors(event_example["definitions"])))
check("Event binding example validates", not list(Draft202012Validator(event_binding_schema).iter_errors(event_example["bindings"])))

# B1 — definiciones de editores de modelos.
editor_files = [
    "src/main/java/com/example/editor/definition/EditorDefinitionModel.java",
    "src/main/java/com/example/editor/definition/RepresentationDefinition.java",
    "src/main/java/com/example/editor/definition/ToolPaletteDefinition.java",
    "src/main/java/com/example/editor/definition/EditorTool.java",
    "src/main/java/com/example/editor/definition/FormDefinition.java",
    "src/main/java/com/example/editor/definition/EditorCapabilityManifest.java",
    "src/main/java/com/example/editor/definition/EditorDefinitionChecker.java",
    "src/main/java/com/example/editor/definition/EditorDefinitionApplicationService.java",
    "src/main/java/com/example/editor/definition/persistence/JsonEditorDefinitionRepository.java",
    "src/main/java/com/example/editor/definition/web/CanonicalEditorDefinitionController.java",
]
for path in editor_files:
    check(f"B1 file exists: {path}", exists(path))

check("Editor document owns representations and palette", contains(editor_files[0], "representations", "palette", "forms", "capabilityManifest"))
check("Representations have their own tool IDs", contains(editor_files[1], "toolIds", "nodeMappings", "edgeMappings"))
check("Palette is stored per editor", contains(editor_files[2], "groups", "tools"))
check("Tools are a closed discriminated hierarchy", contains(editor_files[3], "sealed interface EditorTool", "NodeCreationTool", "EdgeCreationTool", "OperationEditorTool"))
check("Forms are separate from metamodel structure", contains(editor_files[4], "targetClassId", "sections"))
check("Capability manifest supports optional modules", contains(editor_files[5], "Set<EditorCapability>"))
check("Checker validates editor representations", contains(editor_files[6], "NO_REPRESENTATION", "DUPLICATE_REPRESENTATION"))
check("Checker validates node and edge mappings", contains(editor_files[6], "UNKNOWN_NODE_CLASS", "EDGE_ENDPOINT_MISMATCH"))
check("Checker validates palette groups and tools", contains(editor_files[6], "UNKNOWN_GROUP_TOOL", "UNKNOWN_REPRESENTATION_TOOL"))
check("Checker validates forms", contains(editor_files[6], "UNKNOWN_FORM_CLASS", "UNKNOWN_FORM_FEATURE"))
check("Checker validates operations and actions by ID", contains(editor_files[6], "UNKNOWN_TOOL_OPERATION", "UNKNOWN_TOOL_ACTION"))
check("Editor service filters several editors by metamodel", contains(editor_files[7], "repository.listIds()", "metamodelId"))
check("Editor repository supports lifecycle cleanup", contains(editor_files[8], "deleteIfExists", "fileName", "exists"))
check("Editor API is nested under the owning metamodel", contains(editor_files[9], "/api/canonical-metamodels/{metamodelId}/editors"))
check("Editor properties use independent directory", contains("src/main/resources/application.properties", "dsl.editors.directory=./data/editors"))
check("Frontend can create another editor for the same metamodel", contains("frontend/src/editor-definition/editorDefinitionFactory.ts", "createAdditionalEditorDefinition"))
check("Frontend can create several representations", contains("frontend/src/editor-definition/EditorDefinitionDesigner.tsx", "+ Representation", "deleteSelectedRepresentation"))
check("Frontend palette adds only concrete classes", contains("frontend/src/editor-definition/EditorDefinitionDesigner.tsx", "metaClass.kind === 'CLASS'"))
palette_source = text("frontend/src/metamodel-designer/MetamodelPalette.tsx")
check("Metamodel palette remains canonical and fixed", all(token in palette_source for token in ("'CLASS'", "'ABSTRACT'", "'INTERFACE'", 'onAddEnum', 'onAddDataType', 'kind="REFERENCE"', 'kind="AGGREGATION"', 'kind="CONTAINMENT"', 'kind="DEPENDENCY"', 'kind="INHERITANCE"', 'kind="IMPLEMENTATION"')))
check("Metamodel designer includes separate event and editor spaces", contains("frontend/src/metamodel-designer/MetamodelDesigner.tsx", "EVENTS", "EDITORS", "LazyEventDesigner", "LazyEditorDefinitionDesigner"))

editor_schema = json.loads(text("src/main/resources/schemas/editor/editor-definition-model-1.0.schema.json"))
editor_example = json.loads(text("src/main/resources/examples/editor/people-basic-editor.json"))
check("Editor schema is Draft 2020-12", editor_schema.get("$schema", "").endswith("2020-12/schema"))
check("Editor schema describes discriminated tools", "tool" in editor_schema.get("$defs", {}) and "nodeTool" in editor_schema.get("$defs", {}))
check("Editor example validates", not list(Draft202012Validator(editor_schema).iter_errors(editor_example)))
check("Editor example contains editor palette", editor_example["palette"]["tools"][0]["kind"] == "CREATE_NODE")

# Cross-module lifecycle and separation.
check("Lifecycle reports event definitions", contains("src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java", "eventDefinitionFile", "eventBindingFile"))
check("Lifecycle reports editor definitions", contains("src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java", "editorDefinitionFiles"))
check("Lifecycle deletes associated event documents", contains("src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java", "eventBindingRepository.deleteIfExists", "eventDefinitionRepository.deleteIfExists"))
check("Lifecycle deletes all associated editors", contains("src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java", "deleteNamedFiles(editorProperties.getDirectory()", "findAssociatedEditorDefinitions"))
check("Delete dialog shows new related files", contains("frontend/src/metamodel-designer/MetamodelLibraryDialog.tsx", "eventDefinitionFile", "editorDefinitionFiles"))

check("Metamodel palette uses the shared control system", "HydraButton" in palette_source and "<button" not in palette_source)

failed = [name for name, result in checks if not result]
for index, (name, result) in enumerate(checks, 1):
    print(f"[{index:02d}] {'OK' if result else 'FAIL'} - {name}")
print(f"\nResult: {len(checks) - len(failed)}/{len(checks)} checks passed")
if failed:
    print("Failed checks:")
    for name in failed:
        print(f"- {name}")
    sys.exit(1)
