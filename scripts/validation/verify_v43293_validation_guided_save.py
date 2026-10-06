from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def contains(path: str, needle: str) -> bool:
    return needle in (ROOT / path).read_text(encoding='utf-8')

def add(name: str, ok: bool) -> None:
    checks.append((name, ok))
    print(('PASS' if ok else 'FAIL') + ' - ' + name)

factory = 'frontend/src/validation-designer/validationDesignerFactory.ts'
policy = 'frontend/src/validation/stableIdentifierPolicy.ts'
preflight = 'frontend/src/validation-designer/validationDesignerValidation.ts'
rule = 'frontend/src/validation-designer/ValidationRuleEditor.tsx'
workflow = 'frontend/src/design-system/HydraGuidedWorkflow.tsx'
designer = 'frontend/src/validation-designer/ValidationDesigner.tsx'
catalog = 'frontend/src/i18n/guidedSpanishCatalogB.ts'
translations = 'frontend/src/i18n/translationCatalog.ts'

add('frontend document-ID policy matches backend stable document contract', contains(policy, "^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$"))
add('action model no longer derives a colon document ID', contains(factory, "derivedValidationDocumentId(metamodelId, 'actions')") and not contains(factory, '`${metamodelId}:actions`'))
add('validation model no longer derives a colon document ID', contains(factory, "derivedValidationDocumentId(metamodelId, 'validations')") and not contains(factory, '`${metamodelId}:validations`'))
add('derived layer document IDs remain bounded to 120 characters', contains(policy, 'DOCUMENT_ID_MAX_LENGTH = 120'))
add('save preflight validates all layer-level document identifiers', contains(preflight, 'validationDocumentError(expressions, actions, validations)') and contains(preflight, 'isStableDocumentId(actions.id)') and contains(preflight, 'isStableDocumentId(validations.id)'))
add('guided identity validates the full stable problem-code contract', contains(rule, 'complete:stableCodeValid'))
add('guided condition validates the expression structure before advancing', contains(rule, "id:'expression-valid'") and contains(rule, 'findExpressionBlockingError(currentExpression)'))
add('guided quick-fix step rejects invalid quick-fix definitions', contains(rule, "findQuickFixBlockingError(action) === ''"))
add('guided review includes a global save-readiness check', contains(rule, "id:'all-ready'") and contains(rule, 'complete:allDefinitionsReady'))
add('failed finish operations are not marked complete', contains(workflow, 'if (result === true) setConfirmedStepIds'))
add('validation save reports success/failure back to the guided workflow', contains(designer, 'async function saveAll(): Promise<boolean>') and contains(designer, 'return false;') and contains(designer, 'return true;'))
add('step 5 Spanish detail is catalogued', contains(catalog, 'No hay ninguna reparación asociada; es un estado válido.'))
add('step 6 Spanish identity detail is catalogued', contains(catalog, 'El nombre, el código y el mensaje de fallo son obligatorios.'))
add('step 6 Spanish runtime detail is catalogued', contains(catalog, 'El runtime dispone de la información necesaria'))
add('dynamic quick-fix count is translated in Spanish', contains(translations, 'repair option\\(s\\) linked'))
add('factory regression asserts backend-safe action and validation document IDs', contains('frontend/src/validation-designer/validationDesignerFactory.test.ts', "expect(actions.id).toBe('shop-actions')") and contains('frontend/src/validation-designer/validationDesignerFactory.test.ts', "expect(validations.id).toBe('shop-validations')"))
add('guided-workflow regression covers a failed final save', contains('frontend/src/design-system/HydraGuidedWorkflow.test.tsx', 'does not mark the final step complete when the finish action reports failure'))
add('Spanish validation regression covers steps 5 and 6', contains('frontend/src/validation-designer/ValidationRuleEditor.help.test.tsx', 'shows the step 5 and step 6 checklist details completely in Spanish'))

failed = [name for name, ok in checks if not ok]
print(f'V43293_VALIDATION_GUIDED_SAVE={len(checks)-len(failed)}/{len(checks)}')
if failed:
    raise SystemExit(1)
