import { expect, test } from '@playwright/test';
import { diskDoc, expectSavedMessage, field, finishButton, forceEnglish, getExpressions, nextStep, openValidationDesigner, seedValidationMetamodel, select, waitForRule } from './helpers';

// Jornada «asistente guiado de regla»: el usuario recorre los seis pasos (identidad, contexto, diagnóstico y momentos, condición guiada con Y/O/NO,
// quick fixes y revisión) y guarda. Oráculo: GET real de validaciones/expresiones y ficheros del backend; el resumen de revisión coincide con lo guardado.
test('el asistente guiado construye la regla paso a paso y lo guardado coincide con el resumen de revisión', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'valguided');
    await openValidationDesigner(page, mm.id);
    await page.getByRole('button', { name: '+ Rule', exact: true }).click();

    // id · 1 Identidad
    await field(page, 'Name').fill('Cliente mayor de edad');
    await field(page, 'Stable problem code').fill('client_adult');
    await select(page, 'Severity').selectOption('ERROR');
    await expect(field(page, 'Enabled')).toBeChecked();
    await nextStep(page).click();

    // ctx · 2 Contexto
    await select(page, 'Execution mode').selectOption('INCREMENTAL');
    await select(page, 'Context').selectOption('ATTRIBUTE');
    await select(page, 'Context class').selectOption(mm.personId);
    await select(page, 'Target feature').selectOption(mm.ageId);
    await nextStep(page).click();

    // dg · 3 Diagnóstico y momentos
    await field(page, 'Message shown when the rule fails').fill('The customer must be an adult or the company must have people.');
    await page.getByLabel('When an attribute changes').check();
    await nextStep(page).click();

    // cn · 4 Condición guiada: age >= 18  O  NO(número de Person >= 1)
    await page.getByRole('button', { name: /Check a field value/ }).click();
    await select(page, 'Condition 1 attribute').selectOption(mm.ageId);
    await select(page, 'Condition 1 operator').selectOption('GREATER_OR_EQUAL');
    await page.locator('.validation-guided-clause').nth(0).getByRole('spinbutton', { name: 'Expected value' }).fill('18');
    await page.getByRole('button', { name: '+ Complete model', exact: true }).click();
    await select(page, 'Condition 2 operator').selectOption('GREATER_OR_EQUAL');
    await page.locator('.validation-guided-clause').nth(1).getByRole('spinbutton', { name: 'Expected value' }).fill('1');
    await page.locator('.validation-guided-clause').nth(1).getByRole('checkbox', { name: 'NOT this condition' }).check();
    await select(page, 'Condition combination').selectOption('OR');
    await expect(page.locator('.validation-guided-summary')).toContainText('greater than or equal to 18');
    await expect(page.locator('.validation-guided-summary')).toContainText('NOT');
    await nextStep(page).click();

    // 5 Quick fixes (opcional) y rv · 6 Revisión
    await nextStep(page).click();
    const review = page.locator('.hydra-guided-review-summary');
    await expect(review).toContainText('Cliente mayor de edad');
    await expect(review).toContainText('CLIENT_ADULT');
    await expect(review).toContainText('The customer must be an adult');
    const summary = await review.locator('dd').allTextContents();
    await finishButton(page, 'Save validation').click();
    await expectSavedMessage(page);

    // Oráculo: lo guardado coincide con lo escrito y con el resumen mostrado.
    const rule = await waitForRule(request, mm.id, 'CLIENT_ADULT');
    expect(rule).toMatchObject({ name: 'Cliente mayor de edad', enabled: true, severity: 'ERROR', executionMode: 'INCREMENTAL', context: { kind: 'ATTRIBUTE', classId: mm.personId, featureId: mm.ageId }, messageTemplate: 'The customer must be an adult or the company must have people.' });
    expect(rule.triggers).toContain('ON_ATTRIBUTE_CHANGE');
    expect(summary).toEqual([rule.severity, rule.context.kind, rule.executionMode, String(rule.quickFixIds.length)]);

    const expressions = await getExpressions(request, mm.id);
    const expression = (expressions.expressions as Array<Record<string, any>>).find((item) => item.id === rule.conditionExpressionId); // eslint-disable-line @typescript-eslint/no-explicit-any
    const tree = expression?.root;
    expect(tree).toMatchObject({ kind: 'BINARY', operator: 'OR' });
    expect(tree.left).toMatchObject({ kind: 'BINARY', operator: 'GREATER_OR_EQUAL', left: { kind: 'ATTRIBUTE_ACCESS', attributeId: mm.ageId }, right: { kind: 'LITERAL', value: 18 } });
    expect(tree.right).toMatchObject({ kind: 'UNARY', operator: 'NOT', operand: { kind: 'BINARY', operator: 'GREATER_OR_EQUAL', left: { kind: 'COLLECTION', operator: 'COUNT', source: { kind: 'CLASS_INSTANCES', classId: mm.personId } }, right: { kind: 'LITERAL', value: 1 } } });
    const diskRule = (diskDoc(mm.id, 'validations')?.rules as Array<Record<string, unknown>>).find((item) => item.code === 'CLIENT_ADULT');
    expect(diskRule?.name).toBe('Cliente mayor de edad');

    // Recargar: la regla sigue en la lista del diseñador.
    await page.reload();
    await openValidationDesigner(page, mm.id);
    await expect(page.locator('.validation-rule-list-item', { hasText: 'Cliente mayor de edad' })).toBeVisible();
});
