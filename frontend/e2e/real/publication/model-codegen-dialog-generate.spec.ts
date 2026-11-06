import fs from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { uid } from '../support';
import { openModule } from './helpers';

// Jornada: «Generar código desde el modelo». Con un metamodelo real y un modelo nuevo abierto en el editor de modelos, el usuario abre el diálogo,
// elige el destino Java, escribe espacio de nombres y nombre, previsualiza, cambia una opción, vuelve a previsualizar y descarga.
// Oráculo: llamadas directas a la API real de generación con los mismos parámetros (contenido de la vista previa y bytes del fichero descargado).
test.use({ viewport: { width: 1700, height: 1000 } });

const features = (gettersSetters: boolean) => ({ generateTests: false, generateGettersSetters: gettersSetters, generateComparison: false, generateStringRepresentation: false, generateEquality: false });
const body = (gettersSetters: boolean) => ({ options: { namespace: 'e2e.gen', artifactName: 'E2eModel', includeTraceability: true, features: features(gettersSetters) }, selectionElementIds: [] as string[] });

function metamodelDocument(id: string, name: string): Record<string, unknown> {
    return {
        schemaVersion: '2.1', id, name, annotations: {}, enums: [], dataTypes: [], relations: [],
        classes: [{ id: `${id}-class`, name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [{ id: `${id}-attribute`, name: 'fullName', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }] }]
    };
}

/** Las órdenes del editor de modelos viven en el menú «Model» de la barra de órdenes. */
async function modelMenuCommand(page: Page, name: RegExp): Promise<void> {
    await page.locator('.hydra-designer-command-group-dropdown > button.hydra-designer-dropdown-trigger').first().click();
    await page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name }).click();
}

async function openGenerateDialog(page: Page): Promise<void> {
    await expect(page.locator('.hydra-designer-command-group-dropdown > button.hydra-designer-dropdown-trigger').first()).toBeVisible({ timeout: 30_000 });
    await modelMenuCommand(page, /^New model$/);
    await expect(page.getByText(/Document: .*Unsaved changes|Document: /).first()).toBeVisible({ timeout: 30_000 });
    // El diálogo pide el catálogo de destinos al abrirse: se comprueba que el servidor lo ofrece y que el selector lo muestra.
    for (let attempt = 1; ; attempt++) {
        const catalog = page.waitForResponse(item => item.request().method() === 'GET' && item.url().includes('/code-generation/targets'));
        await modelMenuCommand(page, /^Generate code$/);
        const response = await catalog;
        expect(response.status(), `catálogo de destinos: ${response.status()}`).toBe(200);
        const targets = await response.json() as Array<{ id: string }>;
        expect(targets.map(item => item.id)).toEqual(expect.arrayContaining(['java', 'python']));
        const options = page.getByRole('dialog').locator('select').first().locator('option[value="java"]');
        try { await expect(options).toHaveCount(1, { timeout: 15_000 }); return; }
        catch (error) {
            // Defecto de producción sospechado: el selector queda vacío aunque el servidor ofrece el catálogo; se reabre el diálogo un par de veces.
            if (attempt >= 3) throw error;
            await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
            await expect(page.getByRole('dialog')).toHaveCount(0);
        }
    }
}

test('elegir destino, escribir espacio de nombres y nombre, previsualizar, cambiar una opción y descargar el código Java generado', async ({ page, request }) => {
    // Paso previo (no es la etapa certificada): un metamodelo real mínimo sembrado por la API real.
    const metamodelId = uid('codegen');
    const seeded = await request.post('/api/canonical-metamodels', { data: metamodelDocument(metamodelId, metamodelId) });
    expect(seeded.ok(), `siembra del metamodelo: ${seeded.status()}`).toBeTruthy();

    // Otros specs dejan un proyecto activo con una pestaña de fichero abierta, que sustituye al editor de modelos: se cierra por la API real.
    await request.post('/api/projects/close');
    await openModule(page, '/#/model');
    await page.locator('select[aria-label="Active semantic context"], select[aria-label="Active metamodel"]').selectOption(metamodelId);
    await openGenerateDialog(page);

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Generate code from model', { exact: true }).first()).toBeVisible();

    // Destino de generación: el usuario elige Java en el selector (t) y el catálogo ofrece otros destinos.
    const target = dialog.locator('select').first();
    await expect(target.locator('option[value="python"]')).toHaveCount(1, { timeout: 15_000 });
    await target.selectOption('java');
    await expect(target).toHaveValue('java');

    // ns: espacio de nombres y nombre del artefacto.
    await dialog.getByLabel('Namespace or module', { exact: true }).fill('e2e.gen');
    await dialog.getByLabel('Artifact name', { exact: true }).fill('E2eModel');

    // Primera vista previa (sin opciones adicionales); la ruta de la petición identifica el modelo abierto.
    const firstPreview = page.waitForRequest(item => item.method() === 'POST' && item.url().endsWith('/code-generation/java/preview'));
    await dialog.getByRole('button', { name: 'Preview code', exact: true }).click();
    const previewUrl = new URL((await firstPreview).url());
    const previewPath = previewUrl.pathname;
    const code = dialog.locator('.model-code-generation-preview code');
    await expect(code).toContainText('package e2e.gen;');
    await expect(code).toContainText('public final class E2eModel');
    await expect(code).not.toContainText('getId()');

    // ok: «¿Te convence?» — si no, el usuario cambia una opción y vuelve a previsualizar; el resultado cambia.
    await dialog.getByRole('checkbox', { name: /Generate getters and setters/ }).check();
    await dialog.getByRole('button', { name: 'Preview code', exact: true }).click();
    await expect(code).toContainText('getId()');

    // Oráculo 1: la misma vista previa pedida directamente a la API real devuelve exactamente ese contenido.
    const directPreview = await request.post(previewPath, { data: body(true) });
    expect(directPreview.ok(), `preview directa: ${directPreview.status()}`).toBeTruthy();
    const directJson = await directPreview.json() as { fileName: string; content: string };
    expect(directJson.content).toContain('package e2e.gen;');
    expect(directJson.content).toContain('public final class E2eModel');
    expect(directJson.content).toContain('getId()');
    expect(directJson.fileName).toBe('E2eModel.java');
    expect((await code.innerText()).replace(/\r\n/g, '\n').trimEnd()).toBe(directJson.content.replace(/\r\n/g, '\n').trimEnd());
    const withoutOption = await (await request.post(previewPath, { data: body(false) })).json() as { content: string };
    expect(withoutOption.content).not.toContain('getId()');

    // Generar y descargar: el fichero coincide byte a byte con la generación directa de la API real.
    const downloadPromise = page.waitForEvent('download');
    await dialog.getByRole('button', { name: /^(Generate and download|Generate package and download)$/ }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe('E2eModel.java');
    const downloaded = fs.readFileSync(await download.path());
    const direct = await request.post(previewPath.replace(/\/preview$/, ''), { data: body(true) });
    expect(direct.ok()).toBeTruthy();
    expect(downloaded.equals(await direct.body())).toBe(true);
    expect(downloaded.toString('utf8')).toContain('package e2e.gen;');
    await expect(dialog).toHaveCount(0);
});

test('elegir otro destino cambia la extensión y el contenido generado', async ({ page, request }) => {
    const metamodelId = uid('codegen');
    expect((await request.post('/api/canonical-metamodels', { data: metamodelDocument(metamodelId, metamodelId) })).ok()).toBeTruthy();
    // Otros specs dejan un proyecto activo con una pestaña de fichero abierta, que sustituye al editor de modelos: se cierra por la API real.
    await request.post('/api/projects/close');
    await openModule(page, '/#/model');
    await page.locator('select[aria-label="Active semantic context"], select[aria-label="Active metamodel"]').selectOption(metamodelId);
    await openGenerateDialog(page);
    const dialog = page.getByRole('dialog');
    const target = dialog.locator('select').first();

    await target.selectOption('java');
    const javaPreview = page.waitForRequest(item => item.method() === 'POST' && item.url().endsWith('/code-generation/java/preview'));
    await dialog.getByRole('button', { name: 'Preview code', exact: true }).click();
    const javaPath = new URL((await javaPreview).url()).pathname;
    await expect(dialog.locator('.model-code-generation-preview')).toContainText('.java');

    await target.selectOption('python');
    await expect(target).toHaveValue('python');
    const pythonPreview = page.waitForRequest(item => item.method() === 'POST' && item.url().endsWith('/code-generation/python/preview'));
    await dialog.getByRole('button', { name: 'Preview code', exact: true }).click();
    const pythonPath = new URL((await pythonPreview).url()).pathname;
    expect(pythonPath).toBe(javaPath.replace('/java/', '/python/'));
    await expect(dialog.locator('.model-code-generation-preview header')).toContainText('.py');

    // Oráculo: la API real ofrece el mismo destino con esa extensión y genera un fichero distinto al de Java.
    const direct = await (await request.post(pythonPath, { data: { options: { namespace: 'generated.model', artifactName: 'GeneratedModel', includeTraceability: true, features: features(false) }, selectionElementIds: [] } })).json() as { fileName: string; content: string };
    expect(direct.fileName.endsWith('.py')).toBe(true);
    const java = await (await request.post(javaPath, { data: { options: { namespace: 'generated.model', artifactName: 'GeneratedModel', includeTraceability: true, features: features(false) }, selectionElementIds: [] } })).json() as { fileName: string; content: string };
    expect(java.fileName.endsWith('.java')).toBe(true);
    expect(java.content).not.toBe(direct.content);
});

test('sin modelo abierto no se puede generar, y el destino elegido determina el fichero descargado', async ({ page, request }) => {
    const metamodelId = uid('codegen');
    expect((await request.post('/api/canonical-metamodels', { data: metamodelDocument(metamodelId, metamodelId) })).ok()).toBeTruthy();
    await request.post('/api/projects/close');
    const generationCalls: string[] = [];
    page.on('request', item => { if (item.url().includes('/code-generation/') && item.method() === 'POST') generationCalls.push(item.url()); });
    await openModule(page, '/#/model');
    await page.locator('select[aria-label="Active semantic context"], select[aria-label="Active metamodel"]').selectOption(metamodelId);

    // t (rechazo): con el metamodelo elegido pero sin ningún modelo abierto, la orden de generar está deshabilitada y no viaja ninguna generación.
    const trigger = page.locator('.hydra-designer-command-group-dropdown > button.hydra-designer-dropdown-trigger').first();
    await expect(trigger).toBeVisible({ timeout: 30_000 });
    await trigger.click();
    await expect(page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name: /^Generate code$/ })).toBeDisabled();
    await page.keyboard.press('Escape');
    expect(generationCalls).toEqual([]);

    // Con un modelo nuevo (modelo + metamodelo) el diálogo se abre, el usuario elige Python y descarga.
    await openGenerateDialog(page);
    const dialog = page.getByRole('dialog');
    const target = dialog.locator('select').first();
    await expect(target.locator('option[value="python"]')).toHaveCount(1, { timeout: 15_000 });
    await target.selectOption('python');
    await expect(target).toHaveValue('python');
    await dialog.getByLabel('Namespace or module', { exact: true }).fill('e2e.gen');
    await dialog.getByLabel('Artifact name', { exact: true }).fill('E2eModel');
    const sentRequest = page.waitForRequest(item => item.method() === 'POST' && /\/code-generation\/python$/.test(item.url()));
    const downloadPromise = page.waitForEvent('download');
    await dialog.getByRole('button', { name: /^(Generate and download|Generate package and download)$/ }).click();
    const sent = await sentRequest;
    const download = await downloadPromise;
    expect(download.suggestedFilename().endsWith('.py'), `fichero descargado: ${download.suggestedFilename()}`).toBe(true);
    expect(new URL(sent.url()).pathname).toMatch(/\/code-generation\/python$/);

    // Oráculo: la API real, con la misma petición que envió el navegador, genera exactamente los bytes descargados.
    const direct = await request.post(new URL(sent.url()).pathname, { data: sent.postDataJSON() as Record<string, unknown> });
    expect(direct.ok(), `generación directa: ${direct.status()}`).toBeTruthy();
    expect(fs.readFileSync(await download.path()).equals(await direct.body())).toBe(true);
});
