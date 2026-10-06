#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding='utf-8')

def add(name: str, ok: bool) -> None:
    checks.append((name, bool(ok)))
    print(('PASS' if ok else 'FAIL') + ' - ' + name)

policy = text('frontend/src/hooks/hydraOverwritePolicy.ts')
hook = text('frontend/src/hooks/useHydraOverwriteConfirmation.tsx')
dialogs = text('frontend/src/hooks/useHydraDialogs.tsx')
modal = text('frontend/src/design-system/HydraModal.tsx')
validation = text('frontend/src/validation-designer/ValidationDesigner.tsx')
validation_identity = text('frontend/src/validation-designer/validationOverwriteIdentity.ts')
validation_identity_test = text('frontend/src/validation-designer/validationOverwriteIdentity.test.ts')
policy_test = text('frontend/src/hooks/hydraOverwritePolicy.test.ts')
dialog_test = text('frontend/src/hooks/useHydraDialogs.test.tsx')
metamodel = text('frontend/src/metamodel-designer/MetamodelDesigner.tsx')
editor = text('frontend/src/editor-definition/EditorDefinitionDesigner.tsx')
events = text('frontend/src/event-designer/EventDesigner.tsx')
flows = text('frontend/src/event-designer/flow/EventFlowDesigner.tsx')
gamification = text('frontend/src/gamification-designer/GamificationDesigner.tsx')
integration = text('frontend/src/integration-designer/IntegrationDesigner.tsx')
spanish = text('frontend/src/i18n/currentSpanishCatalogPart16.ts')

add('overwrite conflicts are based on persisted entities instead of container ETags', 'findHydraOverwriteConflicts' in policy and 'changedHydraPersistedIdentities' in policy)
add('unchanged siblings in compound documents are filtered using content signatures', 'contentSignature' in policy and 'persistedById' in policy)
add('conflict lookup is indexed by scope/id/name instead of nested full scans', 'const byId = new Map' in policy and 'const byName = new Map' in policy and 'indexKey' in policy)
add('validation can opt into semantic condition collisions', "includeSemanticSignature: true" in validation and "entityKind: 'VALIDATION_RULE'" in validation_identity)
add('validation semantic signature includes context and condition tree', 'context: rule.context' in validation_identity and 'root: expression.root' in validation_identity)
add('validation semantic signature ignores generated AST node IDs', "key === 'nodeId'" in validation_identity)
add('validation overwrite identity is per rule rather than ValidationModel document', 'validationModel.rules.map' in validation_identity and 'stableId: rule.id' in validation_identity and 'uniqueName: rule.name' in validation_identity)
add('quick fixes and operation bindings use entity-level overwrite identities too', "entityKind: 'QUICK_FIX'" in validation_identity and "entityKind: 'OPERATION_BINDING'" in validation_identity)
add('empty persisted validation documents do not create an artificial conflict', 'setPersistedValidationIdentities(validationOverwriteIdentities' in validation and 'no matching element exists in persistence' in policy_test)
add('successful validation save refreshes the persisted entity baseline', 'setPersistedValidationIdentities(validationOverwriteIdentities(nextValidationModel' in validation)
add('validation regression covers semantic equality independent of AST IDs', 'equivalent conditions as the same semantic signature' in validation_identity_test)
add('event layer compares event types and bindings instead of the event document id', 'eventOverwriteIdentities' in events and 'persistedEventIdentities' in events)
add('EventFlow compares individual flows instead of the container model id', 'eventFlowOverwriteIdentities' in flows and 'persistedFlowIdentities' in flows)
add('gamification compares individual definitions and rules', 'gamificationOverwriteIdentities' in gamification and 'persistedGamificationIdentities' in gamification)
add('metamodel overwrite check uses the actual metamodel catalog', 'persisted: metamodels.map' in metamodel and "entityKind: 'METAMODEL'" in metamodel)
add('editor overwrite check uses the actual editor catalog', 'persisted: availableEditors.map' in editor and "entityKind: 'EDITOR_DEFINITION'" in editor)
add('integration overwrite check uses the actual profile catalog', 'persisted: summaries.map' in integration and "entityKind: 'INTEGRATION_PROFILE'" in integration)
add('new entities with different id/name do not conflict with unchanged persisted siblings', 'does not treat unchanged siblings in a persisted container as overwrite candidates' in policy_test)
add('validation-only semantic collisions are explicitly regression tested', 'can use semantic signatures only for layers that explicitly enable them' in policy_test)
add('different entity kinds cannot collide only because they share an id/name', 'keeps different entity kinds independent inside a compound layer' in policy_test)
add('overwrite dialog description is rendered once', '>{null}</HydraModal>' in dialogs and '<p>{decision.description}</p>' not in dialogs)
add('modal omits an empty content region for description-only decisions', 'children !== null && children !== undefined' in modal)
add('dialog regression asserts one visible copy of the explanation', "getAllByText('One explanation only')" in dialog_test and 'toHaveLength(1)' in dialog_test)
add('overwrite dialog explains the concrete matching criterion', 'same stable ID' in hook and 'same unique name' in hook and 'same validation condition' in hook)
add('new overwrite explanations are localized in Spanish', all(fragment in spanish for fragment in [
    'Elemento existente', 'Coincidencia', 'mismo ID estable', 'mismo nombre único', 'misma condición de validación'
]))

failed = [name for name, ok in checks if not ok]
print(f'V43295_ENTITY_OVERWRITE_CONFLICTS={len(checks)-len(failed)}/{len(checks)}')
if failed:
    raise SystemExit(1)
