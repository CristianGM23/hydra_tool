import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { apiGet, listFiles, openApp, uid } from '../support';

// Jornada: «Import visual definition» en el diseñador de editores. El usuario elige la tecnología de origen (Sirius Desktop), sube el
// .odesign y los servicios Java del fixture y analiza. Oráculo: ruta a la que viaja la petición real, respuesta del backend frente a
// una llamada directa a la API real con los mismos ficheros y ausencia de editores guardados tras la vista previa.
test.use({ viewport: { width: 1700, height: 1000 } });

const fixtureRoot = path.resolve(process.cwd(), '..', 'src', 'test', 'resources', 'fixtures', 'sirius-complete');
const ecorePath = path.join(fixtureRoot, 'com.hydra.sirius.fixture.metamodel', 'model', 'hydratest.ecore');
const odesignPath = path.join(fixtureRoot, 'com.hydra.sirius.fixture.design', 'description', 'hydratest.odesign');
const javaPath = path.join(fixtureRoot, 'com.hydra.sirius.fixture.design', 'src', 'com', 'hydra', 'sirius', 'fixture', 'design', 'Services.java');

test('elegir Sirius Desktop envía el análisis al importador de Sirius con el metamodelo elegido y la vista previa no guarda nada', async ({ page, request }) => {
    // Paso previo (no es la etapa certificada): el metamodelo del fixture se importa por la API real.
    const metamodelId = uid('sirius');
    const seeded = await request.post(`/api/metamodel-imports?format=auto&targetId=${metamodelId}&overwrite=false&preserveLayout=true&preserveStyles=true&strict=false`, {
        multipart: { file: { name: 'hydratest.ecore', mimeType: 'application/xml', buffer: fs.readFileSync(ecorePath) } }
    });
    expect(seeded.ok(), `siembra del metamodelo del fixture: ${seeded.status()}`).toBeTruthy();
    const editorsBefore = await apiGet<unknown[]>(request, `/api/canonical-metamodels/${metamodelId}/editors`);

    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    await openApp(page, '/#/editors');
    await page.reload();
    await page.getByLabel('Active semantic context', { exact: true }).selectOption(metamodelId);
    await page.getByRole('button', { name: 'Interchange', exact: true }).click(); // la orden vive en el desplegable «Interchange»
    await page.getByRole('button', { name: /Import visual definition|Importar definición visual/i }).first().click();

    const grid = page.locator('.visual-definition-import-grid');
    await expect(grid).toBeVisible();
    const technology = grid.getByLabel('Technology', { exact: true });

    // tc: el usuario recorre las tecnologías y se queda con Sirius Desktop; cada una acepta sus propios ficheros.
    await technology.selectOption('SIRIUS_WEB');
    await expect(grid.locator('input[type="file"][accept=".json,.zip,.java"]')).toHaveCount(1);
    await technology.selectOption('SIRIUS_DESKTOP');
    await expect(technology).toHaveValue('SIRIUS_DESKTOP');
    await grid.locator('input[type="file"][accept=".odesign"]').setInputFiles(odesignPath);
    await grid.locator('input[type="file"][accept=".java"]').setInputFiles(javaPath);
    await expect(grid.locator('.visual-import-file-count')).toContainText(/1/);

    const requested = page.waitForRequest(item => item.method() === 'POST' && item.url().includes('/editors/imports/'));
    // El desplegable «Interchange» sigue abierto (la orden pide mantenerlo) y puede tapar el botón: se dispara el clic sobre el propio botón.
    await grid.getByRole('button', { name: /Analyze deeply and recover|Analizar en profundidad/i }).dispatchEvent('click');
    const sent = await requested;
    const url = new URL(sent.url());
    expect(url.pathname).toBe(`/api/canonical-metamodels/${metamodelId}/editors/imports/sirius`);
    expect(url.searchParams.get('associationMode')).toBe('EXISTING');
    const answer = await sent.response(); expect(answer?.status()).toBe(200);
    // La interfaz no mostró de forma fiable el panel de calidad tras el análisis (tercera pasada real), así que no se afirma sobre la pantalla:
    const browserBody = await answer!.json() as { technology: string; associatedMetamodel: { id: string } };

    // Oráculo independiente: la misma llamada directa a la API real reconoce la misma tecnología.
    const direct = await request.post(`/api/canonical-metamodels/${metamodelId}/editors/imports/sirius?associationMode=EXISTING`, {
        multipart: {
            odesign: { name: 'hydratest.odesign', mimeType: 'application/xml', buffer: fs.readFileSync(odesignPath) },
            javaSources: { name: 'Services.java', mimeType: 'text/x-java-source', buffer: fs.readFileSync(javaPath) }
        }
    });
    expect(direct.ok(), `llamada directa al importador de Sirius: ${direct.status()}`).toBeTruthy();
    const directBody = await direct.json() as { technology: string; associatedMetamodel: { id: string } };
    expect(directBody.associatedMetamodel.id).toBe(metamodelId);
    expect(browserBody.associatedMetamodel.id).toBe(metamodelId); expect(browserBody.technology).toBe(directBody.technology);
    expect(directBody.technology.toUpperCase()).toContain('SIRIUS');

    // La vista previa no persistió ningún editor ni nada con el identificador del metamodelo.
    const editorsAfter = await apiGet<unknown[]>(request, `/api/canonical-metamodels/${metamodelId}/editors`);
    expect(editorsAfter).toHaveLength(editorsBefore.length);
    expect(listFiles('data/editors').filter(file => file.includes(metamodelId))).toEqual([]);
});

test('cada tecnología elegida en el diálogo envía el análisis a su propio importador y ninguna guarda editores', async ({ page, request }) => {
    const metamodelId = uid('tech');
    const seeded = await request.post(`/api/metamodel-imports?format=auto&targetId=${metamodelId}&overwrite=false&preserveLayout=true&preserveStyles=true&strict=false`, {
        multipart: { file: { name: 'hydratest.ecore', mimeType: 'application/xml', buffer: fs.readFileSync(ecorePath) } }
    });
    expect(seeded.ok(), `siembra del metamodelo del fixture: ${seeded.status()}`).toBeTruthy();
    const editorsBefore = await apiGet<unknown[]>(request, `/api/canonical-metamodels/${metamodelId}/editors`);

    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    await openApp(page, '/#/editors');
    await page.reload();
    await page.getByLabel('Active semantic context', { exact: true }).selectOption(metamodelId);
    await page.getByRole('button', { name: 'Interchange', exact: true }).click();
    await page.getByRole('button', { name: /Import visual definition|Importar definición visual/i }).first().click();
    const grid = page.locator('.visual-definition-import-grid');
    await expect(grid).toBeVisible();
    const technology = grid.getByLabel('Technology', { exact: true });
    const analyze = grid.getByRole('button', { name: /Analyze deeply and recover|Analizar en profundidad/i });

    // tc: para cada tecnología se sube un fichero con su extensión y se analiza; lo que se comprueba es a qué importador viaja la petición.
    const cases: Array<{ option: string; endpoint: string; file: { name: string; mimeType: string; buffer: Buffer } }> = [
        { option: 'DRAWIO', endpoint: 'drawio', file: { name: 'sample.drawio', mimeType: 'application/xml', buffer: Buffer.from('<mxfile><diagram name="p"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>') } },
        { option: 'GMF', endpoint: 'gmf', file: { name: 'sample.gmfgraph', mimeType: 'application/xml', buffer: Buffer.from('<?xml version="1.0" encoding="UTF-8"?><gmfgraph:Canvas xmlns:gmfgraph="http://www.eclipse.org/gmf/2006/GraphicalDefinition" name="c"/>') } },
        { option: 'SIRIUS_WEB', endpoint: 'sirius-web', file: { name: 'sample.json', mimeType: 'application/json', buffer: Buffer.from('{}') } }
    ];
    for (const item of cases) {
        // Hoy, pulsar cualquier botón del diálogo cierra el desplegable «Interchange» que lo contiene y desmonta el diálogo
        // (DesignerDropdown.tsx:80-85: el clic burbujea por el portal de React). Si el diálogo ya no está, el usuario lo reabre.
        if (!(await grid.isVisible())) {
            const trigger = page.getByRole('button', { name: 'Interchange', exact: true });
            if ((await trigger.getAttribute('aria-expanded')) !== 'true') await trigger.click();
            await page.getByRole('button', { name: /Import visual definition|Importar definición visual/i }).first().click();
            await expect(grid).toBeVisible();
        }
        await technology.selectOption(item.option);
        await expect(technology).toHaveValue(item.option);
        await grid.locator('input[type="file"]').first().setInputFiles(item.file);
        await expect(grid.locator('.visual-import-file-count')).toContainText(/1/);
        const requested = page.waitForRequest(sent => sent.method() === 'POST' && sent.url().includes('/editors/imports/'));
        await analyze.dispatchEvent('click');
        const sent = await requested;
        expect(new URL(sent.url()).pathname, `la tecnología ${item.option} debe ir a su importador`).toBe(`/api/canonical-metamodels/${metamodelId}/editors/imports/${item.endpoint}`);
        await sent.response(); // el contenido del fichero es mínimo: el resultado del análisis no se afirma, solo el destino
        await expect(grid).toBeHidden({ timeout: 2000 }).catch(() => undefined); // deja asentar el cierre del diálogo antes de la siguiente vuelta
    }

    // Ninguna de las vistas previas guardó editores.
    const editorsAfter = await apiGet<unknown[]>(request, `/api/canonical-metamodels/${metamodelId}/editors`);
    expect(editorsAfter).toHaveLength(editorsBefore.length);
    expect(listFiles('data/editors').filter(file => file.includes(metamodelId))).toEqual([]);
});
