import { expect, test } from '@playwright/test';
import { eventually, uid } from '../support';
import {
    clickSave, combo, findTransformationByName, loadTransformationDoc, openDesigner, readTransformationFromDisk, runtimeMessage,
    savedSelector, seedMetamodel, SAVED_MESSAGE, switchToAdvanced, treeClass, BLOCKED_MESSAGE,
} from './helpers';

// Definir direccionalidad y políticas en el modo avanzado: la elección llega persistida al backend real, el trazado queda
// forzado y la puerta de guardado rechaza una bidireccional sin regla de vuelta.
test('la direccionalidad bidireccional y la política de conflictos elegidas en el diseñador quedan persistidas y la puerta rechaza la definición incompleta', async ({ page, request }) => {
    const srcId = uid('e2e-psrc');
    const tgtId = uid('e2e-ptgt');
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: `${srcId}-person`, name: 'Person', attrs: [{ id: `${srcId}-person-name`, name: 'name' }] }] });
    await seedMetamodel(request, { id: tgtId, name: tgtId, classes: [{ id: `${tgtId}-contact`, name: 'Contact', attrs: [{ id: `${tgtId}-contact-name`, name: 'name' }] }] });
    const name = uid('E2E Policies');

    await openDesigner(page);
    await switchToAdvanced(page);
    await page.locator('#transformation-name').fill(name);
    await combo(page, 'Directionality', 'Direccionalidad').selectOption('BIDIRECTIONAL');
    await combo(page, 'Concurrent changes', 'Cambios concurrentes').selectOption('REVERSE_WINS');
    await combo(page, 'Source metamodel', 'Metamodelo origen').selectOption(srcId);
    await combo(page, 'Target metamodel', 'Metamodelo destino').selectOption(tgtId);

    // Rechazo: sin regla de ida y vuelta la puerta de guardado bloquea y no se escribe nada.
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(BLOCKED_MESSAGE);
    expect(await findTransformationByName(request, name)).toBeUndefined();

    // El usuario completa el par de reglas y guarda.
    await treeClass(page, 0, 'Person').click();
    await treeClass(page, 1, 'Contact').click();
    await page.getByRole('button', { name: /Create rule from selected classes|Crear regla con las clases seleccionadas/ }).click();
    await combo(page, 'Rule direction', 'Dirección de la regla').selectOption('REVERSE');
    await treeClass(page, 0, 'Contact').click();
    await treeClass(page, 1, 'Person').click();
    await page.getByRole('button', { name: /Create backward rule|Crear regla de vuelta/ }).click();
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(SAVED_MESSAGE);

    const summary = await eventually(() => findTransformationByName(request, name), (value) => value !== undefined);
    const id = summary!.id;
    const persisted = await loadTransformationDoc(request, id);
    const onDisk = readTransformationFromDisk(id);
    for (const doc of [persisted, onDisk]) {
        expect(doc.directionality).toBe('BIDIRECTIONAL');
        expect(doc.synchronizationPolicy.conflictPolicy).toBe('REVERSE_WINS');
        expect(doc.tracePolicy.enabled).toBe(true);
        expect(doc.rules.map((rule) => rule.direction).sort()).toEqual(['FORWARD', 'REVERSE']);
    }

    // Oráculo de la puerta, independiente del navegador: la misma definición sin reglas es rechazada por el comprobador real.
    const response = await request.post('/api/transformations/check', { data: { ...persisted, rules: [] } });
    expect(response.ok()).toBeTruthy();
    const report = await response.json();
    expect(report.diagnostics.map((item: { code: string }) => item.code)).toContain('BIDIRECTIONAL_RULE_PAIR_REQUIRED');

    // Recarga de la página: la elección persistida vuelve a la interfaz.
    await page.reload();
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(id);
    await expect(page.locator('#transformation-name')).toHaveValue(name);
    await expect(combo(page, 'Directionality', 'Direccionalidad')).toHaveValue('BIDIRECTIONAL');
    await expect(combo(page, 'Concurrent changes', 'Cambios concurrentes')).toHaveValue('REVERSE_WINS');
});
