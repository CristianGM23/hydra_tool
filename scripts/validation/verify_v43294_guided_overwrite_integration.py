#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding='utf-8')

def add(name: str, ok: bool) -> None:
    checks.append((name, bool(ok)))
    print(('PASS' if ok else 'FAIL') + ' - ' + name)

workflow = text('frontend/src/design-system/HydraGuidedWorkflow.tsx')
workflow_model = text('frontend/src/design-system/HydraGuidedWorkflowModel.ts')
workflow_types = text('frontend/src/design-system/HydraGuidedWorkflowTypes.ts')
workflow_tests = text('frontend/src/design-system/HydraGuidedWorkflow.test.tsx')
overwrite = text('frontend/src/hooks/hydraOverwritePolicy.ts')
overwrite_tests = text('frontend/src/hooks/hydraOverwritePolicy.test.ts')
root_policy = text('frontend/src/integration-designer/generatedRootPath.ts')
integration = text('frontend/src/integration-designer/IntegrationDesigner.tsx')
integration_css = text('frontend/src/integration-designer/integration-designer.css')
injection_policy = text('src/main/java/com/example/editor/integration/InjectionPolicy.java')
serialization = text('src/test/java/com/example/editor/validation/contract/CanonicalActionValidationSerializationTests.java')

add('guided checks block by default', "return check.blocking !== false" in workflow_model)
add('optional steps cannot bypass blocking errors', 'return guidedBlockingChecks(step).length === 0' in workflow_model and '`optional` ya no permite saltarse errores' in workflow_model)
add('previous confirmed steps are revalidated before advancing', 'firstInvalidConfirmedStep' in workflow and 'previousStepsReady' in workflow and 'canAdvance = currentReady && previousStepsReady' in workflow)
add('guided finalization requires explicit boolean success', 'onFinish?: () => boolean | Promise<boolean>' in workflow and 'if (result === true) setConfirmedStepIds' in workflow)
add('guided regression covers failed final persistence', 'does not mark the final step complete when the finish action reports failure' in workflow_tests)
add('guided regression covers invalid optional step', 'never lets optional steps bypass an actual blocking error' in workflow_tests)
add('guided regression covers a previously valid step becoming invalid', 'already confirmed previous step becomes invalid' in workflow_tests)
add('informational checks must opt out explicitly', 'blocking?: boolean' in workflow_types and 'explicitly non-blocking informational checks' in workflow_tests)

# All current guided finish callbacks are statically typed by HydraGuidedWorkflow; additionally reject the old silent-success idiom.
all_tsx = '\n'.join(p.read_text(encoding='utf-8') for p in (ROOT / 'frontend/src').rglob('*.tsx'))
add('no guided workflow still discards final result with void', 'onFinish={() => void ' not in all_tsx and 'onFinish={() => { if (!busy) void ' not in all_tsx)

core_layers = {
    'metamodel': 'frontend/src/metamodel-designer/MetamodelDesigner.tsx',
    'model editor': 'frontend/src/editor-definition/EditorDefinitionDesigner.tsx',
    'validation and quick fixes': 'frontend/src/validation-designer/ValidationDesigner.tsx',
    'events': 'frontend/src/event-designer/EventDesigner.tsx',
    'EventFlow': 'frontend/src/event-designer/flow/EventFlowDesigner.tsx',
    'gamification': 'frontend/src/gamification-designer/GamificationDesigner.tsx',
    'integrations': 'frontend/src/integration-designer/IntegrationDesigner.tsx',
}
for label, path in core_layers.items():
    source = text(path)
    add(f'{label} uses the common overwrite confirmation policy', 'useHydraOverwriteConfirmation' in source and 'confirmOverwrite' in source and 'overwriteDialogs' in source)
    add(f'{label} persistence reports boolean success', 'Promise<boolean>' in source and 'return false;' in source and 'return true;' in source)

add('overwrite identity comparison preserves identifier case', 'toLocaleLowerCase' not in overwrite and 'toLowerCase' not in overwrite)
add('overwrite policy recognizes stable ID or declared unique name', 'currentId === persistedId' in overwrite and 'currentName === persistedName' in overwrite)
add('overwrite policy does not invent a conflict when no persisted baseline exists', 'if (persisted === null) return false' in overwrite)
add('overwrite tests cover no persisted element, same id, same unique name and case-sensitive identity', all(fragment in overwrite_tests for fragment in [
    'no matching element exists in persistence', 'same stable ID', 'same unique name', 'backend can distinguish'
]))

add('generated output root is validated in the frontend', 'validateGeneratedRootPath' in root_policy and 'TRAVERSAL' in root_policy and 'ABSOLUTE' in root_policy)
add('generated output root rejects Windows device names and trailing-dot/space segments', 'WINDOWS_RESERVED_SEGMENT' in root_policy and "segment.endsWith('.')" in root_policy and "segment.endsWith(' ')" in root_policy)
add('generated output root is independently revalidated by the backend', 'normalizeGeneratedRoot' in injection_policy and 'windowsReserved' in injection_policy and 'project-relative path' in injection_policy)
add('integration generated-root picker stores only a project-relative path', 'pickProjectPath' in integration and 'toProjectRelativeGeneratedRoot' in integration and 'generatedRootControl' in integration)
add('integration callback identities cannot reload and erase an in-memory preset', 'onErrorRef' in integration and 'onMessageRef' in integration and '[metamodelId, persisted]' in integration)
add('portable runtime preset remains a single explicit action', 'applyPortableRuntimePreset' in integration and 'Apply portable runtime preset' in integration)
add('integration method cards use responsive wrapping instead of clipped text', 'grid-template-columns:repeat(auto-fit' in integration_css and 'overflow-wrap:' in integration_css)
add('validation/action JSON contract retains empty structural collections', 'serializesCanonicalEmptyCollectionsInsteadOfDroppingTheUiContract' in serialization and 'serializedActions.has("actions")' in serialization and 'serializedValidations.path("rules").isEmpty()' in serialization)

failed = [name for name, ok in checks if not ok]
print(f'V43294_GUIDED_OVERWRITE_INTEGRATION={len(checks)-len(failed)}/{len(checks)}')
if failed:
    raise SystemExit(1)
