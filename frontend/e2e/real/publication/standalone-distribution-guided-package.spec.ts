import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { exists, readJson, uid, workDir } from '../support';
import { openModule, sha256 } from './helpers';

// Jornada: «Centro de distribución». El usuario prepara una distribución independiente: carpeta del editor, nombre y versión, destinos, opciones avanzadas,
// generación con validación y empaquetado en ZIP. Oráculo: ficheros que escribió el backend en la raíz de distribución, el ZIP descargado y su SHA-256
// frente a una petición independiente a la API real de empaquetado.
test.use({ viewport: { width: 1600, height: 1000 } });

const EDITORS_ROOT = 'data/standalone-editors';

/** Paso previo (no es la etapa certificada): carpeta de un editor ya generado, con su punto de entrada web. */
function seedEditor(label: string): string {
    const dir = uid(label);
    const folder = path.join(workDir, EDITORS_ROOT, dir);
    fs.mkdirSync(folder, { recursive: true });
    fs.writeFileSync(path.join(folder, 'index.html'), '<!doctype html><html><head><meta charset="utf-8"><title>E2E</title></head><body>editor</body></html>');
    return dir;
}

const profile = (targets: string[], offline: boolean, associations: string[], channel: string) => ({ productName: 'E2E Editor', version: '2.3.4', targets, offlineEnabled: offline, fileAssociations: associations, updateChannel: channel });

test('el modo guiado recorre origen, identidad, destinos y opciones, escribe la distribución y empaqueta un ZIP verificable', async ({ page, request }) => {
    const dir = seedEditor('e2e-editor');
    await openModule(page, '/#/distribution');

    // m: se abre en modo guiado (pasos) y existe el modo completo con todos los campos juntos.
    await expect(page.getByRole('heading', { name: 'Guided distribution' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Full workspace', exact: true })).toBeVisible();

    // so: carpeta del editor.
    await page.locator('#distribution-directory').fill(dir);
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // idn + ad: identidad, preajuste de asociaciones y canal.
    await page.locator('#distribution-product').fill('E2E Editor');
    await page.locator('#distribution-version').fill('2.3.4');
    await page.locator('#distribution-associations-preset').selectOption('HYDRA');
    await page.locator('#distribution-channel-preset').selectOption('beta');
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // tg: por defecto están marcados web (PWA) y Tauri para Windows.
    await expect(page.getByRole('checkbox', { name: 'Progressive Web App', exact: true })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Tauri for Windows', exact: true })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Tauri for macOS', exact: true })).not.toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Tauri for Linux', exact: true })).not.toBeChecked();
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // Generar y validar.
    await page.getByRole('button', { name: 'Materialize and validate', exact: true }).click();
    await expect(page.locator('.distribution-guided-result.valid')).toContainText('Distribution valid');

    // Oráculo 1: lo que el backend escribió en disco refleja lo que el usuario eligió.
    const base = `${EDITORS_ROOT}/${dir}`;
    const descriptor = readJson<{ productName: string; version: string; targets: string[]; offlineEnabled: boolean; fileAssociations: string[]; updateChannel: string }>(`${base}/distribution/hydra-distribution.json`);
    expect(descriptor).toMatchObject({ productName: 'E2E Editor', version: '2.3.4', targets: ['PWA', 'TAURI_WINDOWS'], offlineEnabled: true, fileAssociations: ['.hydra'], updateChannel: 'beta' });
    const manifest = readJson<{ name: string; display: string }>(`${base}/public/manifest.webmanifest`);
    expect(manifest).toMatchObject({ name: 'E2E Editor', display: 'standalone' });
    const tauri = readJson<{ productName: string; version: string; bundle: { fileAssociations: Array<{ ext: string[] }> } }>(`${base}/src-tauri/tauri.conf.json`);
    expect(tauri).toMatchObject({ productName: 'E2E Editor', version: '2.3.4' });
    expect(tauri.bundle.fileAssociations).toEqual([{ ext: ['hydra'] }]);
    expect(fs.readFileSync(path.join(workDir, base, 'public', 'service-worker.js'), 'utf8')).toContain('const SHELL');

    // Empaquetar: el último paso descarga el ZIP y muestra su SHA-256.
    await page.getByRole('button', { name: 'Next step', exact: true }).click();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Package ZIP', exact: true }).click();
    const download = await downloadPromise;
    const zip = fs.readFileSync(await download.path());
    expect(download.suggestedFilename()).toMatch(/\.zip$/);
    const shown = (await page.locator('.hydra-guided-review-summary code').innerText()).replace(/^SHA-256\s*/, '').trim();
    expect(shown).toBe(sha256(zip));

    // Oráculo 2: una petición independiente a la API real devuelve el mismo hash en su cabecera y el mismo contenido.
    const direct = await request.post('/api/standalone-distributions/package', { data: { editorDirectory: dir, profile: profile(['PWA', 'TAURI_WINDOWS'], true, ['.hydra'], 'beta') } });
    expect(direct.ok(), `package directo: ${direct.status()}`).toBeTruthy();
    expect(direct.headers()['x-hydra-distribution-sha256']).toBe(shown);
    expect((await direct.body()).equals(zip)).toBe(true);
    for (const entry of ['index.html', 'distribution/hydra-distribution.json', 'public/manifest.webmanifest', 'src-tauri/tauri.conf.json']) {
        expect(zip.includes(Buffer.from(entry)), `el ZIP contiene ${entry}`).toBe(true);
    }
});

test('el modo completo con solo web y sin conexión escribe únicamente los artefactos de esos destinos', async ({ page, request }) => {
    const dir = seedEditor('e2e-pwa');
    await openModule(page, '/#/distribution');
    await page.getByRole('button', { name: 'Full workspace', exact: true }).click();
    await page.locator('#distribution-directory').fill(dir);
    await page.locator('#distribution-product').fill('E2E Editor');
    await page.locator('#distribution-version').fill('2.3.4');
    await page.getByRole('checkbox', { name: 'Tauri for Windows', exact: true }).uncheck();
    await page.getByRole('checkbox', { name: 'Offline application shell', exact: true }).uncheck();

    // Vista previa de artefactos: coincide con el plan de la API real.
    await page.getByRole('button', { name: 'Preview artifacts', exact: true }).click();
    const planned = page.locator('aside section').filter({ hasText: 'Planned artifacts' }).locator('li code');
    await expect(planned.first()).toBeVisible();
    const shownPaths = (await planned.allInnerTexts()).sort();
    const plan = await request.post('/api/standalone-distributions/plan', { data: { editorDirectory: dir, profile: profile(['PWA'], false, ['.hydra', '.xmi'], 'stable') } });
    expect(plan.ok()).toBeTruthy();
    expect(shownPaths).toEqual((await plan.json() as Array<{ path: string }>).map(item => item.path).sort());
    expect(shownPaths.some(item => item.startsWith('src-tauri/'))).toBe(false);

    await page.getByRole('button', { name: 'Materialize and validate', exact: true }).click();
    await expect(page.locator('aside section.valid')).toContainText('Distribution valid');

    const base = `${EDITORS_ROOT}/${dir}`;
    expect(exists(`${base}/public/manifest.webmanifest`)).toBe(true);
    expect(exists(`${base}/src-tauri/tauri.conf.json`)).toBe(false);
    expect(exists(`${base}/src-tauri/Cargo.toml`)).toBe(false);
    expect(fs.readFileSync(path.join(workDir, base, 'public', 'service-worker.js'), 'utf8')).not.toContain('const SHELL');
    expect(readJson<{ targets: string[]; offlineEnabled: boolean }>(`${base}/distribution/hydra-distribution.json`)).toMatchObject({ targets: ['PWA'], offlineEnabled: false });
});

test('una carpeta sin punto de entrada web queda bloqueada y no se puede empaquetar', async ({ page, request }) => {
    const dir = uid('e2e-empty');
    fs.mkdirSync(path.join(workDir, EDITORS_ROOT, dir), { recursive: true });
    await openModule(page, '/#/distribution');
    await page.getByRole('button', { name: 'Full workspace', exact: true }).click();
    await page.locator('#distribution-directory').fill(dir);
    await page.getByRole('button', { name: 'Materialize and validate', exact: true }).click();
    await expect(page.locator('aside section.invalid')).toContainText('Distribution blocked');
    await expect(page.getByRole('button', { name: 'Package ZIP', exact: true })).toBeDisabled();

    const direct = await request.post('/api/standalone-distributions/validate', { data: { editorDirectory: dir, profile: { productName: 'HYDRA Generated Editor', version: '1.0.0', targets: ['PWA', 'TAURI_WINDOWS'], offlineEnabled: true, fileAssociations: ['.hydra', '.xmi'], updateChannel: 'stable' } } });
    const report = await direct.json() as { valid: boolean; diagnostics: Array<{ code: string; severity: string }> };
    expect(report.valid).toBe(false);
    expect(report.diagnostics.some(item => item.code === 'APP_ENTRY' && item.severity === 'ERROR')).toBe(true);
});
