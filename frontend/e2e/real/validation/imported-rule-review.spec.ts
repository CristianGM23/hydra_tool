import { expect, test } from '@playwright/test';
import { diskDoc, expectSavedMessage, field, forceEnglish, getExpressions, getValidations, modeSwitch, openValidationDesigner, saveFromToolbar, seedValidationMetamodel, select } from './helpers';

// Jornada «revisión de regla importada»: parte de un documento con una regla importada de forma parcial (desactivada y con su origen externo).
// La importación en sí NO es la etapa certificada y se siembra con la API real; la revisión (completar la condición, activarla, marcarla como
// completa) la hace el usuario en el formulario. Oráculo: GET real y fichero del backend.
test('revisar y completar una regla importada: se activa, queda completa y su condición canónica se guarda', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'valimport');
    const expressionId = 'expression:imported-age';
    const ruleId = 'rule:imported-age';
    const expressionResponse = await request.post(`/api/canonical-metamodels/${mm.id}/expressions`, {
        data: {
            schemaVersion: '1.0', id: mm.id, metamodelId: mm.id, annotations: {},
            expressions: [{ id: expressionId, name: 'Imported age condition', description: 'Imported from rules.evl', contextClassId: mm.personId, declaredReturnType: { kind: 'BOOLEAN' }, annotations: { 'hydra.import.requiresReview': 'true' }, root: { kind: 'LITERAL', nodeId: 'node:imported-root', valueType: { kind: 'BOOLEAN' }, value: true } }],
        },
    });
    if (!expressionResponse.ok()) throw new Error(`no se pudo sembrar las expresiones: ${expressionResponse.status()} ${await expressionResponse.text()}`);
    const validationResponse = await request.post(`/api/canonical-metamodels/${mm.id}/validations`, {
        data: {
            schemaVersion: '1.0', id: `${mm.id}-validations`, metamodelId: mm.id, expressionModelId: mm.id, annotations: {},
            ruleSets: [{ id: 'rule-set:imported', name: 'EVL import', description: '', enabled: true, order: 0, ruleIds: [ruleId], annotations: {} }],
            importedSources: [{ id: 'validation-source:rules-evl', technology: 'EVL', fileName: 'rules.evl', contentHash: '0', conversionStatus: 'PARTIAL', annotations: {} }],
            rules: [{
                id: ruleId, name: 'Imported age rule', code: 'IMPORTED_AGE', description: '', enabled: false, context: { kind: 'CLASS', classId: mm.personId }, severity: 'WARNING',
                conditionExpressionId: expressionId, messageTemplate: 'Imported: the age must be valid', triggers: ['MANUAL'], dependencies: [], quickFixIds: [],
                executionMode: 'MANUAL_ONLY', authoritativeRepresentation: 'EVL', conversionStatus: 'PARTIAL', annotations: { 'hydra.import.requiresReview': 'true' },
            }],
        },
    });
    if (!validationResponse.ok()) throw new Error(`no se pudo sembrar las validaciones: ${validationResponse.status()} ${await validationResponse.text()}`);
    const before = (await getValidations(request, mm.id)).rules[0];
    expect(before).toMatchObject({ enabled: false, conversionStatus: 'PARTIAL', authoritativeRepresentation: 'EVL' });

    await openValidationDesigner(page, mm.id);
    const item = page.locator('.validation-rule-list-item', { hasText: 'Imported age rule' });
    await expect(item).toContainText('Off');
    await item.click();
    await modeSwitch(page, 'Full form').click();
    await expect(field(page, 'Enabled')).not.toBeChecked();

    // Completar: condición real con el constructor guiado, activar y marcar como completa y propia de HYDRA.
    await page.getByRole('button', { name: /Check a field value/ }).click();
    await select(page, 'Condition 1 attribute').selectOption(mm.ageId);
    await select(page, 'Condition 1 operator').selectOption('GREATER_OR_EQUAL');
    await page.locator('.validation-guided-clause').nth(0).getByRole('spinbutton', { name: 'Expected value' }).fill('0');
    await field(page, 'Enabled').check();
    await page.locator('details.validation-advanced-section > summary').click();
    await select(page, 'Authoritative representation').selectOption('CANONICAL');
    await select(page, 'Conversion status').selectOption('FULL');

    await saveFromToolbar(page);
    await expectSavedMessage(page);

    // Oráculo.
    const after = (await getValidations(request, mm.id)).rules.find((rule: Record<string, unknown>) => rule.id === ruleId);
    expect(after).toMatchObject({ enabled: true, conversionStatus: 'FULL', authoritativeRepresentation: 'CANONICAL' });
    const expression = (await getExpressions(request, mm.id)).expressions.find((entry: Record<string, unknown>) => entry.id === expressionId);
    expect(expression.root).toMatchObject({ kind: 'BINARY', operator: 'GREATER_OR_EQUAL', left: { kind: 'ATTRIBUTE_ACCESS', attributeId: mm.ageId }, right: { kind: 'LITERAL', value: 0 } });
    const onDisk = (diskDoc(mm.id, 'validations')?.rules as Array<Record<string, unknown>>).find((rule) => rule.id === ruleId);
    expect(onDisk).toMatchObject({ enabled: true, conversionStatus: 'FULL' });

    // Recargar: la regla aparece ya activa (sin la marca «Off»).
    await page.reload();
    await openValidationDesigner(page, mm.id);
    await expect(page.locator('.validation-rule-list-item', { hasText: 'Imported age rule' })).not.toContainText('Off');
});
