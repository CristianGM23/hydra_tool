import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { apiGet, eventually, exists, listFiles, readJson, uid, workDir } from '../support';
import { analyzeDirect, folderFiles, metamodelProject, openModule } from './helpers';

// Jornada: importar un update-site NO Sirius (solo el proyecto del metamodelo con su .ecore) desde el asistente guiado. El usuario elige las opciones de
// rigor y de guardado y finaliza la importación. Oráculo: ficheros del almacén de metamodelos y del paquete de recuperación en el disco real del
// backend, y una llamada directa e independiente al análisis real para saber si el candidato es importable.
// Nota de diseño: la interfaz NO tiene un cuadro de confirmación al importar; la puerta es que «Finish import» solo se habilita con un análisis
// importable y coincidente con el candidato elegido, y el guardado es inmediato al pulsarlo.
test.use({ viewport: { width: 1700, height: 1100 } });

const METAMODELS = 'data/metamodels';
const PACKAGES = 'data/update-site-imports';
const next = (page: Page) => page.getByRole('button', { name: 'Next step', exact: true });
const finish = (page: Page) => page.getByRole('button', { name: 'Finish import', exact: true });
const packagesOf = (id: string): string[] => listFiles(PACKAGES).filter(file => file.startsWith(`${id}.`) && file.endsWith('.update-site-recovery.json'));

interface PlanOptions { strict?: boolean; overwrite?: boolean; persistPackage?: boolean }

/** Recorre el asistente hasta el paso de análisis con la carpeta del proyecto de metamodelo y las opciones elegidas (solo metamodelo, sin capas visuales). */
async function planAndAnalyze(page: Page, targetId: string, options: PlanOptions): Promise<void> {
    await openModule(page, '/#/update-site-imports');
    await page.locator('input[aria-label="Guided update site directory"]').setInputFiles(metamodelProject);
    await expect(page.locator('.update-site-file-summary.selected')).toBeVisible();
    await next(page).click();
    await page.locator('#guided-update-site-target-id').fill(targetId);
    for (const layer of ['Recover palettes', 'Recover representations', 'Recover mappings']) await page.getByRole('checkbox', { name: layer, exact: true }).uncheck();
    const set = async (name: string, wanted: boolean | undefined): Promise<void> => { if (wanted !== undefined) await page.getByRole('checkbox', { name, exact: true }).setChecked(wanted); };
    await set('Strict exact recovery', options.strict);
    await set('Overwrite existing metamodel', options.overwrite);
    await set('Persist recovery package', options.persistPackage);
    await next(page).click();
    await expect(next(page)).toBeDisabled();
    await page.getByRole('button', { name: 'Analyze repository', exact: true }).click();
    await expect(next(page)).toBeEnabled({ timeout: 120_000 });
    await next(page).click();
}

test('importar guarda el metamodelo y el paquete de recuperación; sin sobrescribir se rechaza el segundo intento y con sobrescribir se acepta', async ({ page, request }) => {
    const id = uid('e2e-upd');
    await planAndAnalyze(page, id, { persistPackage: true, overwrite: false });

    // g: «¿Importar?» — hasta pulsar el botón final, nada se ha escrito.
    await expect(page.getByRole('button', { name: 'Next step', exact: true })).toBeEnabled();
    expect(exists(`${METAMODELS}/${id}.metamodel.json`)).toBe(false);
    expect(packagesOf(id)).toHaveLength(0);
    await next(page).click();
    await expect(finish(page)).toBeEnabled();
    expect(exists(`${METAMODELS}/${id}.metamodel.json`)).toBe(false);

    // c: con «guardar paquete» marcado, el import escribe el metamodelo y el paquete de recuperación.
    await finish(page).click();
    await expect(page.locator('.status-message').filter({ hasText: 'Imported' })).toContainText(id, { timeout: 60_000 });
    const metamodel = await eventually(() => readJson<{ id: string; classes: Array<{ name: string }> }>(`${METAMODELS}/${id}.metamodel.json`), item => item.id === id);
    expect(metamodel.classes.length).toBeGreaterThan(0);
    const packages = packagesOf(id);
    expect(packages).toHaveLength(1);
    const recoveryPackage = readJson<{ metamodelId: string; candidate: { id: string } }>(`${PACKAGES}/${packages[0]}`);
    expect(recoveryPackage.metamodelId).toBe(id);
    expect((await apiGet<Array<{ id: string }>>(request, '/api/canonical-metamodels')).filter(item => item.id === id)).toHaveLength(1);
    const firstWrite = fs.statSync(path.join(workDir, METAMODELS, `${id}.metamodel.json`)).mtimeMs;

    // Sin «sobrescribir», importar de nuevo el mismo identificador se rechaza y el documento no cambia.
    await finish(page).click();
    await expect(page.locator('.hydra-actionable-error')).toBeVisible({ timeout: 60_000 });
    expect(fs.statSync(path.join(workDir, METAMODELS, `${id}.metamodel.json`)).mtimeMs).toBe(firstWrite);

    // Con «sobrescribir» marcado el mismo import se acepta.
    await page.locator('.hydra-guided-stepper').getByRole('button').nth(1).click();
    await page.getByRole('checkbox', { name: 'Overwrite existing metamodel', exact: true }).check();
    await page.locator('.hydra-guided-stepper').getByRole('button').nth(4).click();
    await expect(finish(page)).toBeVisible();
    await finish(page).click();
    await expect(page.locator('.hydra-actionable-error')).toHaveCount(0, { timeout: 60_000 });
    await expect(page.locator('.status-message').filter({ hasText: 'Imported' })).toContainText(id);
    await eventually(() => fs.statSync(path.join(workDir, METAMODELS, `${id}.metamodel.json`)).mtimeMs, value => value > firstWrite);
    expect((await apiGet<Array<{ id: string }>>(request, '/api/canonical-metamodels')).filter(item => item.id === id)).toHaveLength(1);
});

test('desmarcar «guardar paquete de recuperación» importa el metamodelo sin escribir el paquete', async ({ page }) => {
    const id = uid('e2e-nopkg');
    await planAndAnalyze(page, id, { persistPackage: false });
    await next(page).click();
    await expect(finish(page)).toBeEnabled();
    await finish(page).click();
    await expect(page.locator('.status-message').filter({ hasText: 'Imported' })).toContainText(id, { timeout: 60_000 });
    await expect(page.locator('.status-message').filter({ hasText: 'Imported' })).toContainText('not persisted');
    await eventually(() => exists(`${METAMODELS}/${id}.metamodel.json`), found => found);
    expect(packagesOf(id)).toHaveLength(0);
    expect(fs.existsSync(path.join(workDir, PACKAGES)) ? fs.readdirSync(path.join(workDir, PACKAGES)).filter(name => name.startsWith(`${id}.`)) : []).toEqual([]);
});

test('abandonar el asistente en el último paso no escribe nada y la importación solo ocurre al pulsar el botón final', async ({ page, request }) => {
    const id = uid('e2e-abandon');
    await planAndAnalyze(page, id, { persistPackage: true, overwrite: false });
    await next(page).click();
    await expect(finish(page)).toBeEnabled();

    // g (cancelar): el usuario sale sin pulsar «Finish import» (la interfaz no tiene cuadro de confirmación: abandonar equivale a cancelar).
    await openModule(page, '/#/update-site-imports');
    await expect(finish(page)).toHaveCount(0);
    expect(exists(`${METAMODELS}/${id}.metamodel.json`)).toBe(false);
    expect(packagesOf(id)).toHaveLength(0);
    expect((await apiGet<Array<{ id: string }>>(request, '/api/canonical-metamodels')).filter(item => item.id === id)).toHaveLength(0);

    // g (confirmar): repetir el recorrido y pulsar el botón final sí escribe.
    await planAndAnalyze(page, id, { persistPackage: true, overwrite: false });
    await next(page).click();
    await expect(finish(page)).toBeEnabled();
    expect(exists(`${METAMODELS}/${id}.metamodel.json`)).toBe(false);
    await finish(page).click();
    await expect(page.locator('.status-message').filter({ hasText: 'Imported' })).toContainText(id, { timeout: 60_000 });
    await eventually(() => exists(`${METAMODELS}/${id}.metamodel.json`), found => found);
    expect(packagesOf(id)).toHaveLength(1);
});

test('con el modo estricto la puerta de importación sigue a la decisión del análisis real independiente', async ({ page, request }) => {
    const id = uid('e2e-strict');
    // Oráculo independiente: ¿acepta el backend este candidato en modo estricto?
    const direct = await analyzeDirect(request, folderFiles(metamodelProject), { targetId: id, strict: true, palettes: false, representations: false, mappings: false });
    await planAndAnalyze(page, id, { strict: true, persistPackage: true });
    if (direct.importable) {
        await expect(next(page)).toBeEnabled();
        await next(page).click();
        await expect(finish(page)).toBeEnabled();
        await finish(page).click();
        await expect(page.locator('.status-message').filter({ hasText: 'Imported' })).toContainText(id, { timeout: 60_000 });
        await eventually(() => exists(`${METAMODELS}/${id}.metamodel.json`), found => found);
    } else {
        // Rechazo: el modo estricto bloquea el paso de revisión y no se escribe nada.
        await expect(next(page)).toBeDisabled();
        expect(direct.diagnostics.join(' ')).toContain('Strict mode accepts only EXACT recovery');
        expect(exists(`${METAMODELS}/${id}.metamodel.json`)).toBe(false);
        expect(packagesOf(id)).toHaveLength(0);
    }
});
