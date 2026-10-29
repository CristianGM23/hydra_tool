import { expect, test } from '@playwright/test';
import { eventually, uid } from '../support';
import {
    BLOCKED_MESSAGE, clickSave, combo, findTransformationByName, loadTransformationDoc, openDesigner, readTransformationFromDisk,
    runtimeMessage, savedSelector, seedMetamodel, SAVED_MESSAGE, switchToAdvanced, treeClass,
} from './helpers';

// Jornada de transformación modelo a texto: el usuario elige la clase de contexto, crea la regla de generación con sus valores
// iniciales y guarda; el backend real persiste la regla y la puerta de guardado rechaza una ruta de salida insegura.
test('una regla de generación creada para la clase de contexto nace con ruta, plantilla y colisión en «fallar» y se guarda en el backend', async ({ page, request }) => {
    const srcId = uid('e2e-gsrc');
    const attrId = `${srcId}-person-name`;
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: `${srcId}-person`, name: 'Person', attrs: [{ id: attrId, name: 'name' }] }] });
    const name = uid('E2E Generation');

    await openDesigner(page);
    await switchToAdvanced(page);
    await page.locator('#transformation-name').fill(name);
    await combo(page, 'Transformation type', 'Tipo de transformación').selectOption('M2T');
    await combo(page, 'Source metamodel', 'Metamodelo origen').selectOption(srcId);

    // Rechazo: sin ninguna regla de generación la puerta no deja guardar.
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(BLOCKED_MESSAGE);
    expect(await findTransformationByName(request, name)).toBeUndefined();

    // Etapa cc: elegir la clase de contexto; sin elegirla no se puede crear la regla.
    const create = page.getByRole('button', { name: /Create generation rule|Crear regla de generación/ });
    await expect(create).toBeDisabled();
    await treeClass(page, 0, 'Person').click();
    await expect(create).toBeEnabled();
    await create.click();

    // Etapa df: valores iniciales de la regla recién creada.
    const pathInput = page.locator('input[id^="generation-path-"]');
    const templateInput = page.locator('textarea[id^="generation-template-"]');
    await expect(pathInput).toHaveValue(`generated/{{attr:${attrId}}}.txt`);
    await expect(templateInput).toHaveValue(`Person {{attr:${attrId}}}`);
    await expect(combo(page, 'Artifact collision', 'Colisión de artefactos')).toHaveValue('FAIL');

    // Rechazo: una ruta que sale del paquete es insegura y no se guarda.
    await pathInput.fill('../fuera.txt');
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(BLOCKED_MESSAGE);
    expect(await findTransformationByName(request, name)).toBeUndefined();

    // Etapa art: con la ruta válida la regla se guarda con la definición.
    // Rodeo de un defecto de producción: el backend devuelve «guard: null» en una regla M2T sin condición y la pantalla se rompe
    // al recibirlo tras guardar; con una condición definida el documento guardado se pinta bien.
    await page.getByRole('button', { name: /^(Add condition|Añadir condición)$/ }).click();
    await pathInput.fill(`generated/{{attr:${attrId}}}.txt`);
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(SAVED_MESSAGE);
    const summary = await eventually(() => findTransformationByName(request, name), (value) => value !== undefined);
    const id = summary!.id;

    const persisted = await loadTransformationDoc(request, id);
    const onDisk = readTransformationFromDisk(id);
    for (const doc of [persisted, onDisk]) {
        expect(doc.kind).toBe('M2T');
        expect(doc.generationRules).toHaveLength(1);
        const rule = doc.generationRules[0];
        expect(rule.contextTypeId).toBe(`${srcId}-person`);
        expect(rule.outputPathTemplate).toBe(`generated/{{attr:${attrId}}}.txt`);
        expect(rule.contentTemplate).toBe(`Person {{attr:${attrId}}}`);
        expect(rule.overwritePolicy).toBe('FAIL');
        expect(rule.enabled).toBe(true);
        expect(rule.guard).toBeTruthy();
    }

    // El comprobador real acepta la definición guardada (sin errores).
    const checked = await request.post('/api/transformations/check', { data: persisted });
    expect(checked.ok()).toBeTruthy();
    const report = await checked.json();
    expect(report.diagnostics.filter((item: { severity: string }) => item.severity === 'ERROR')).toEqual([]);

    // Recarga: la regla guardada reaparece en la interfaz.
    await page.reload();
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(id);
    await switchToAdvanced(page);
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);
    await expect(page.locator('.transformation-rule-card').first()).toContainText(`generated/{{attr:${attrId}}}.txt`);
    await expect(page.locator('input[id^="generation-path-"]')).toHaveValue(`generated/{{attr:${attrId}}}.txt`);
});
