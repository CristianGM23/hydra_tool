import { expect, test } from '@playwright/test';
import { diskDoc, expectSavedMessage, field, finishButton, forceEnglish, getActions, modeSwitch, nextStep, openValidationDesigner, saveFromToolbar, seedValidationMetamodel, select } from './helpers';

// Jornada «diseñador de quick fixes»: parámetros (el botón «+ Parámetro» crea uno de texto obligatorio; se renombra y se cambia el tipo) y asistente
// con su paso de revisión. Oráculo: GET real de las acciones guardadas y fichero JSON del backend.
const addQuickFix = (page: import('@playwright/test').Page) => page.getByRole('button', { name: '+ Quick fix', exact: true });

test('«+ Parámetro» crea un parámetro de texto obligatorio, se renombra y cambia de tipo, y se guarda y recarga', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'qfparams');
    await openValidationDesigner(page, mm.id, 'quick-fixes');
    await addQuickFix(page).click();
    await field(page, 'Name').fill('Reset age');

    // pr · 3 Parámetros: valores por defecto del parámetro nuevo.
    await page.locator('details.validation-advanced-section > summary').click();
    await page.getByRole('button', { name: '+ Parameter', exact: true }).click();
    const names = page.getByLabel('Parameter name', { exact: true });
    await expect(names.nth(0)).toHaveValue('parameter1');
    await expect(select(page, 'Parameter type').nth(0)).toHaveValue('STRING');
    await expect(page.getByLabel('Required', { exact: true }).nth(0)).toBeChecked();
    // Renombrar y cambiar el tipo del primero; el segundo se deja tal como se crea.
    await names.nth(0).fill('replacementAge');
    await select(page, 'Parameter type').nth(0).selectOption('INTEGER');
    await page.getByRole('button', { name: '+ Parameter', exact: true }).click();
    await expect(names.nth(1)).toHaveValue('parameter2');

    // Un quick fix necesita al menos una operación para guardarse.
    await select(page, 'Operation type').selectOption('CLEAR_ATTRIBUTE');
    await saveFromToolbar(page);
    await expectSavedMessage(page);

    const saved = (await getActions(request, mm.id)).actions.find((action: Record<string, unknown>) => action.name === 'Reset age');
    expect(saved, 'el quick fix debe estar guardado').toBeDefined();
    expect(saved.parameters).toEqual([
        expect.objectContaining({ name: 'replacementAge', type: 'INTEGER', required: true }),
        expect.objectContaining({ name: 'parameter2', type: 'STRING', required: true }),
    ]);
    expect(saved.operations).toHaveLength(1);
    const onDisk = (diskDoc(mm.id, 'actions')?.actions as Array<Record<string, any>>).find((action) => action.name === 'Reset age'); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(onDisk?.parameters.map((parameter: Record<string, unknown>) => parameter.name)).toEqual(['replacementAge', 'parameter2']);

    // Recargar: los parámetros se reabren con lo guardado.
    await page.reload();
    await openValidationDesigner(page, mm.id, 'quick-fixes');
    await page.locator('.quick-fix-list-item', { hasText: 'Reset age' }).click();
    await page.locator('details.validation-advanced-section > summary').click();
    await expect(page.getByLabel('Parameter name', { exact: true }).nth(0)).toHaveValue('replacementAge');
    await expect(select(page, 'Parameter type').nth(0)).toHaveValue('INTEGER');
});

test('el asistente de quick fix termina en una revisión cuyo resumen coincide con la acción guardada', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'qfguided');
    await openValidationDesigner(page, mm.id, 'quick-fixes');
    await addQuickFix(page).click();
    await modeSwitch(page, 'Guided').click();

    await field(page, 'Name').fill('Reset age to zero');
    await field(page, 'Description').fill('Clears the age when it is negative.');
    await nextStep(page).click();
    await select(page, 'Transaction').selectOption('BEST_EFFORT');
    await select(page, 'Undo support').selectOption('OPTIONAL');
    await nextStep(page).click();
    await nextStep(page).click(); // parámetros: ninguno
    await select(page, 'Operation type').selectOption('CLEAR_ATTRIBUTE');
    await nextStep(page).click();

    // rv · 5 Revisión
    const review = page.locator('.hydra-guided-review-summary');
    await expect(review).toContainText('Reset age to zero');
    const summary = await review.locator('dd').allTextContents();
    expect(summary).toEqual(['Always', 'BEST_EFFORT', 'OPTIONAL', '1']);
    await finishButton(page, 'Save quick fix').click();
    await expectSavedMessage(page);

    const saved = (await getActions(request, mm.id)).actions.find((action: Record<string, unknown>) => action.name === 'Reset age to zero');
    expect(saved).toBeDefined();
    expect([saved.preconditionExpressionId ?? 'Always', saved.transactionPolicy, saved.undoPolicy, String(saved.operations.length)]).toEqual(summary);
    expect(saved.description).toBe('Clears the age when it is negative.');
    expect(diskDoc(mm.id, 'actions')).toBeDefined();
});
