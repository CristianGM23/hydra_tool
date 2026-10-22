import { expect, test } from '@playwright/test';
import { eventually, uid } from '../support';
import {
    checkbox, clickSave, combo, findTransformationByName, loadTransformationDoc, openDesigner, readTransformationFromDisk,
    runtimeMessage, savedSelector, seedMetamodel, SAVED_MESSAGE, treeClass,
} from './helpers';

// Jornada guiada de una transformación modelo a modelo bidireccional: el usuario escribe nombre, elige tipo, direccionalidad,
// política de cambios simultáneos, metamodelos y crea la correspondencia de ida y vuelta; el backend real la guarda y se relee.
test('el asistente guiado construye y guarda una transformación M2M bidireccional con sus políticas de sincronización', async ({ page, request }) => {
    const srcId = uid('e2e-tsrc');
    const tgtId = uid('e2e-ttgt');
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: `${srcId}-person`, name: 'Person', attrs: [{ id: `${srcId}-person-name`, name: 'name' }] }] });
    await seedMetamodel(request, { id: tgtId, name: tgtId, classes: [{ id: `${tgtId}-contact`, name: 'Contact', attrs: [{ id: `${tgtId}-contact-name`, name: 'name' }] }] });
    const name = uid('E2E Sync');

    await openDesigner(page);

    // Etapa nm: el nombre escrito por el usuario.
    await page.locator('#transformation-name').fill(name);

    // Etapas ty / isM: el tipo condiciona si se pregunta la direccionalidad.
    const directionality = combo(page, 'Directionality', 'Direccionalidad');
    await expect(directionality).toBeVisible();
    await combo(page, 'Transformation type', 'Tipo de transformación').selectOption('M2T');
    await expect(directionality).toHaveCount(0);
    await combo(page, 'Transformation type', 'Tipo de transformación').selectOption('M2M');
    await expect(directionality).toBeVisible();

    // Etapas di / isB: solo la opción bidireccional pide las opciones de sincronización.
    const conflict = combo(page, 'Concurrent changes', 'Cambios concurrentes');
    await expect(conflict).toHaveCount(0);
    await directionality.selectOption('BIDIRECTIONAL');
    await expect(conflict).toBeVisible();

    // Etapa cn: política de conflicto y las dos casillas (valores distintos de los de fábrica).
    await conflict.selectOption('FORWARD_WINS');
    const propagate = checkbox(page, 'Propagate deletions', 'Propagar eliminaciones');
    const requireTrace = checkbox(page, 'Require previous trace for updates', 'Exigir traza previa para actualizaciones');
    await expect(propagate).not.toBeChecked();
    await expect(requireTrace).toBeChecked();
    await propagate.check();
    await requireTrace.uncheck();

    // Etapa mm: metamodelos reales de origen y destino.
    await combo(page, 'Source metamodel', 'Metamodelo origen').selectOption(srcId);
    await combo(page, 'Target metamodel', 'Metamodelo destino').selectOption(tgtId);

    // Etapa wk: espacio de mapeo del tipo (correspondencia de ida y de vuelta).
    await page.getByRole('button', { name: /^(Next step|Paso siguiente)$/ }).click();
    await treeClass(page, 0, 'Person').click();
    await treeClass(page, 1, 'Contact').click();
    await page.getByRole('button', { name: /Create rule from selected classes|Crear regla con las clases seleccionadas/ }).click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);
    await combo(page, 'Rule direction', 'Dirección de la regla').selectOption('REVERSE');
    await treeClass(page, 0, 'Contact').click();
    await treeClass(page, 1, 'Person').click();
    await page.getByRole('button', { name: /Create backward rule|Crear regla de vuelta/ }).click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);

    // Guardado por el usuario y comprobación en el backend real.
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(SAVED_MESSAGE);
    const summary = await eventually(() => findTransformationByName(request, name), (value) => value !== undefined);
    const id = summary!.id;

    const viaApi = await loadTransformationDoc(request, id);
    const onDisk = readTransformationFromDisk(id);
    for (const doc of [viaApi, onDisk]) {
        expect(doc.name).toBe(name);
        expect(doc.kind).toBe('M2M');
        expect(doc.directionality).toBe('BIDIRECTIONAL');
        expect(doc.tracePolicy.enabled).toBe(true);
        expect(doc.synchronizationPolicy).toEqual({ conflictPolicy: 'FORWARD_WINS', propagateDeletions: true, requireTraceForUpdates: false });
        expect(doc.sources[0].metamodelId).toBe(srcId);
        expect(doc.targets[0].metamodelId).toBe(tgtId);
        expect(doc.rules).toHaveLength(2);
        const forward = doc.rules.find((rule: any) => rule.direction === 'FORWARD');
        const reverse = doc.rules.find((rule: any) => rule.direction === 'REVERSE');
        expect(forward.sourceTypeId).toBe(`${srcId}-person`);
        expect(forward.targets[0].typeId).toBe(`${tgtId}-contact`);
        expect(reverse.sourceTypeId).toBe(`${tgtId}-contact`);
        expect(reverse.targets[0].typeId).toBe(`${srcId}-person`);
        expect(reverse.correspondenceId).toBe(forward.correspondenceId);
        expect(forward.correspondenceId).not.toBe('');
    }

    // Recarga: la interfaz recupera lo que el backend guardó.
    await page.reload();
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(id);
    await expect(page.locator('#transformation-name')).toHaveValue(name);
    await expect(combo(page, 'Directionality', 'Direccionalidad')).toHaveValue('BIDIRECTIONAL');
    await expect(combo(page, 'Concurrent changes', 'Cambios concurrentes')).toHaveValue('FORWARD_WINS');
    await expect(checkbox(page, 'Propagate deletions', 'Propagar eliminaciones')).toBeChecked();
    await expect(checkbox(page, 'Require previous trace for updates', 'Exigir traza previa para actualizaciones')).not.toBeChecked();
});

// Rama de rechazo: una bidireccional sin regla de vuelta no se puede guardar desde el asistente.
test('el asistente no guarda una bidireccional con solo la regla de ida', async ({ page, request }) => {
    const srcId = uid('e2e-tsrc');
    const tgtId = uid('e2e-ttgt');
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: `${srcId}-person`, name: 'Person', attrs: [{ id: `${srcId}-person-name`, name: 'name' }] }] });
    await seedMetamodel(request, { id: tgtId, name: tgtId, classes: [{ id: `${tgtId}-contact`, name: 'Contact', attrs: [{ id: `${tgtId}-contact-name`, name: 'name' }] }] });
    const name = uid('E2E Half');

    await openDesigner(page);
    await page.locator('#transformation-name').fill(name);
    await combo(page, 'Directionality', 'Direccionalidad').selectOption('BIDIRECTIONAL');
    await combo(page, 'Source metamodel', 'Metamodelo origen').selectOption(srcId);
    await combo(page, 'Target metamodel', 'Metamodelo destino').selectOption(tgtId);
    await page.getByRole('button', { name: /^(Next step|Paso siguiente)$/ }).click();
    await treeClass(page, 0, 'Person').click();
    await treeClass(page, 1, 'Contact').click();
    await page.getByRole('button', { name: /Create rule from selected classes|Crear regla con las clases seleccionadas/ }).click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);

    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(/Resolve the blocking|Resuelve los diagnósticos bloqueantes/);
    expect(await findTransformationByName(request, name)).toBeUndefined();
});
