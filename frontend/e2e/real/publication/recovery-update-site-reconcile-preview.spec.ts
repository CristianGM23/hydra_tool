import { expect, test } from '@playwright/test';
import { analyzeDirect, folderFiles, fixtureRoot, openModule, requestDirect, storeSnapshot, type Analysis } from './helpers';

// Jornada: espacio de trabajo avanzado de la recuperación. Tras analizar el fixture sirius-complete el usuario decide qué evidencia conservar en cada
// conflicto, construye la vista previa reconciliada, elige una sola representación y previsualiza la materialización. Ninguna de las dos acciones guarda.
// Oráculo: llamadas directas e independientes a /reconcile y /materialize de la API real con las mismas decisiones, y el disco de los almacenes.
test.use({ viewport: { width: 1700, height: 1100 } });

interface Reconciled { preview: null | { metamodel: { classes: unknown[] }; statistics: { classes: number; relations: number } }; applicable: boolean }
interface Materialized { persisted: boolean; rollbackAvailable: boolean; preview: { applicable: boolean; selectedTools: number; selectedRepresentations: number; selectedMappings: number } }

test('elegir evidencia por conflicto y construir la vista previa reconciliada, y previsualizar una sola representación sin guardar nada', async ({ page, request }) => {
    const files = folderFiles(fixtureRoot);
    const before = storeSnapshot();
    await openModule(page, '/#/update-site-imports');
    await page.getByRole('button', { name: 'Advanced workspace', exact: true }).click();
    await page.locator('input[aria-label="Update site directory"]').setInputFiles(fixtureRoot);
    await expect(page.locator('.update-site-file-summary.selected')).toContainText(`${files.length} file(s) selected`);

    const analyzed = page.waitForResponse(item => item.request().method() === 'POST' && item.url().includes('/api/update-site-imports/analyze'));
    await page.getByRole('button', { name: 'Analyze repository', exact: true }).click();
    const analysis = await (await analyzed).json() as Analysis;
    await expect(page.locator('.update-site-control-readiness')).toContainText(`${analysis.candidates.length} recovery candidate(s).`, { timeout: 120_000 });

    // d: un desplegable por conflicto; el usuario conserva una evidencia distinta de la recomendada cuando hay alternativa.
    await page.getByRole('tab', { name: 'Reconciliation', exact: true }).click();
    const conflicts = page.locator('.update-site-conflict');
    await expect(conflicts).toHaveCount(analysis.reconciliation.conflicts.length);
    const decisions: Record<string, string> = {};
    for (const [index, conflict] of analysis.reconciliation.conflicts.entries()) {
        const alternative = Object.keys(conflict.candidateSignatures).find(id => id !== conflict.recommendedCandidateId) ?? conflict.recommendedCandidateId;
        await conflicts.nth(index).locator('select').selectOption(alternative);
        decisions[conflict.elementKey] = alternative;
    }
    const primary = analysis.reconciliation.recommendedCandidateId || analysis.selectedCandidateId;

    const reconcileResponse = page.waitForResponse(item => item.request().method() === 'POST' && item.url().includes('/api/update-site-imports/reconcile'));
    await page.getByRole('button', { name: 'Build reconciled preview', exact: true }).click();
    const reconcileReply = await reconcileResponse;
    const browserReconciled = await reconcileReply.json() as Reconciled;
    // d: las decisiones que el usuario eligió en los desplegables viajan tal cual en la petición real (campo JSON del formulario multipart).
    const sentBody = reconcileReply.request().postData() ?? '';
    for (const [elementKey, candidateId] of Object.entries(decisions)) {
        expect(sentBody, `decisión de ${elementKey}`).toContain(JSON.stringify(elementKey).slice(1, -1));
        expect(sentBody).toContain(candidateId);
    }

    // Oráculo independiente: la misma reconciliación (mismas decisiones) pedida directamente a la API real; lo que devolvió al navegador y lo que
    // muestra la pantalla coinciden con ella.
    const reconciled = await requestDirect<Reconciled>(request, '/api/update-site-imports/reconcile', files, { targetId: '', primaryCandidateId: primary, conflictCandidateIds: decisions });
    expect(reconciled.applicable).toBe(true);
    expect(reconciled.preview).not.toBeNull();
    expect(browserReconciled.applicable).toBe(reconciled.applicable);
    expect(browserReconciled.preview?.statistics).toEqual(reconciled.preview!.statistics);
    await page.getByRole('tab', { name: 'Overview', exact: true }).click();
    await expect(page.getByText(`${reconciled.preview!.statistics.classes} classes · ${reconciled.preview!.statistics.relations} relations`)).toBeVisible();

    // f: seleccionar una sola representación y previsualizar la materialización.
    expect(analysis.recoveredLayers.representations.length).toBeGreaterThan(0);
    await page.getByRole('tab', { name: 'Materialization', exact: true }).click();
    await page.locator('.update-site-selection-presets').getByRole('button', { name: 'Clear', exact: true }).click();
    await expect(page.locator('.update-site-selection-total')).toContainText('0 selected');
    await page.locator('[data-group="REPRESENTATIONS"] input[type="checkbox"]').first().check();
    await expect(page.locator('.update-site-selection-total')).toContainText('1 selected');

    const materializeResponse = page.waitForResponse(item => item.request().method() === 'POST' && item.url().includes('/api/update-site-imports/materialize'));
    await page.getByRole('button', { name: 'Preview materialization', exact: true }).click();
    const browserMaterialized = await (await materializeResponse).json() as Materialized;
    expect(browserMaterialized.persisted).toBe(false);

    // Misma selección pedida directamente: una sola representación, sin herramientas ni mapeos, sin guardar.
    const materializeBody = { targetId: '', candidateId: analysis.selectedCandidateId, editorId: '', editorName: '', strict: false, persist: false,
        selection: { paletteGroupIds: [], paletteToolIds: [], representationIds: [analysis.recoveredLayers.representations[0].id], mappingIds: [], semanticElementIds: [], explicit: true } };
    const direct = await requestDirect<Materialized>(request, '/api/update-site-imports/materialize', files, materializeBody);
    expect(browserMaterialized.preview).toMatchObject({ applicable: direct.preview.applicable, selectedTools: direct.preview.selectedTools, selectedRepresentations: 1, selectedMappings: 0 });
    expect(direct.persisted).toBe(false);
    expect(direct.rollbackAvailable).toBe(false);
    expect(direct.preview.selectedRepresentations).toBe(1);
    expect(direct.preview.selectedMappings).toBe(0);
    const result = page.locator('.update-site-materialization-result');
    await expect(result).toHaveClass(direct.preview.applicable ? /applicable/ : /blocked/);
    await expect(result).toContainText(`${direct.preview.selectedTools} tools · ${direct.preview.selectedRepresentations} representations · ${direct.preview.selectedMappings} mappings`);

    // Nunca guarda: sin mensaje de importación, sin reversión posible y sin cambios en ningún almacén del disco.
    await expect(page.getByRole('button', { name: 'Rollback materialization', exact: true })).toHaveCount(0);
    await expect(page.getByText('Imported project')).toHaveCount(0);
    expect(storeSnapshot()).toEqual(before);

    // La reconciliación de referencia también es de solo lectura.
    const reference = await analyzeDirect(request, files);
    expect(reference.candidates.length).toBe(analysis.candidates.length);
    expect(storeSnapshot()).toEqual(before);
});
