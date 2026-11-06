import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { eventually, exists, openApp, readJson, uid } from '../support';
import { listSavedMetamodels, metamodelDocument, seedMetamodel } from './helpers';

// Jornada: campo «Optional metamodel» del diseñador de gramáticas: elegir un metamodelo guardado, importar uno desde un fichero
// del navegador y quitar el vínculo. Acción: formulario real (selectores, subida de fichero, botón Guardar). Oráculo: GET a la API
// real de gramáticas y de metamodelos y los ficheros que escribe el backend.

interface GrammarJson { id: string; name: string; authoringMode: string; metamodelId: string }

async function openGrammarDesigner(page: Page): Promise<void> {
    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    await openApp(page, '/#/grammars');
    await page.reload();
    await expect(page.getByRole('button', { name: /^New grammar$|^Nueva gramática$/i })).toBeVisible();
    // El diseñador carga de forma asíncrona la última gramática guardada; hay que dejar que termine antes de empezar un borrador.
    await page.waitForLoadState('networkidle');
}

async function saveGrammar(page: Page, alreadySaved: boolean): Promise<void> {
    await page.getByRole('button', { name: /^Save grammar$|^Guardar gramática$/i }).click();
    if (alreadySaved) await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click();
    // El aviso «Grammar saved.» queda en una pestaña inferior que no siempre está a la vista: la confirmación es el oráculo del backend.
}

/** Espera (sin lanzar mientras la gramática aún no existe) a que el backend devuelva la gramática con la condición dada. */
async function grammarWhen(request: APIRequestContext, id: string, accept: (grammar: GrammarJson) => boolean): Promise<GrammarJson> {
    const found = await eventually(async () => {
        const response = await request.get(`/api/grammars/${id}`);
        return response.ok() ? await response.json() as GrammarJson : null;
    }, value => value !== null && accept(value), 20_000);
    return found as GrammarJson;
}

const authoringMode = (page: Page) => page.locator('#grammar-authoring-mode');
const metamodelSelect = (page: Page) => page.locator('#grammar-metamodel');

test('una gramática se vincula a un metamodelo guardado, a otro importado desde fichero y se desvincula conservando los metamodelos', async ({ page, request }) => {
    const seededId = uid('people');
    const seededName = uid('People seeded');
    await seedMetamodel(request, seededId, seededName);

    const grammarId = uid('lang');
    const grammarName = uid('Linked language');
    await openGrammarDesigner(page);
    await page.getByRole('button', { name: /^New grammar$|^Nueva gramática$/i }).click();
    await page.locator('#grammar-id').fill(grammarId);
    await page.locator('#grammar-name').fill(grammarName);
    await expect(page.locator('#grammar-id')).toHaveValue(grammarId);
    await expect(metamodelSelect(page)).toHaveValue(''); // sin vínculo: «primero la gramática»
    await expect(authoringMode(page)).toHaveValue('GRAMMAR_FIRST');

    // c: el usuario puede elegir uno existente o importar un fichero o una carpeta.
    await expect(metamodelSelect(page).locator(`option[value="${seededId}"]`)).toHaveCount(1);
    await page.locator('.grammar-import-menu summary').click();
    await expect(page.getByRole('button', { name: /Import metamodel file|Importar fichero de metamodelo/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Import metamodel folder|Importar carpeta de metamodelo/i })).toBeVisible();
    await page.locator('.grammar-import-menu summary').click();

    // ex: elegir de la lista un metamodelo guardado y pasar al modo vinculado; guardar.
    await authoringMode(page).selectOption('LINKED');
    await metamodelSelect(page).selectOption(seededId);
    await saveGrammar(page, false);
    const linked = await grammarWhen(request, grammarId, value => value.metamodelId === seededId);
    expect(linked).toMatchObject({ id: grammarId, authoringMode: 'LINKED', metamodelId: seededId });
    expect(exists(`data/grammars/${grammarId}.grammar.json`)).toBe(true);
    expect(readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`)).toMatchObject({ authoringMode: 'LINKED', metamodelId: seededId });

    // im: importar un metamodelo externo desde un fichero del navegador; queda guardado y vinculado.
    const importedSourceId = uid('imported');
    const importedName = uid('Imported people');
    const importedDocument = JSON.stringify(metamodelDocument(importedSourceId, importedName));
    await page.locator('.grammar-metamodel-actions input[type="file"]').first().setInputFiles({ name: `${importedSourceId}.metamodel.json`, mimeType: 'application/json', buffer: Buffer.from(importedDocument, 'utf8') });
    const importedId = await eventually(() => metamodelSelect(page).inputValue(), value => value !== '' && value !== seededId, 30_000);
    const storedList = await listSavedMetamodels(request);
    expect(storedList.find(item => item.id === importedId)).toMatchObject({ name: importedName });
    expect(exists(`data/metamodels/${importedId}.metamodel.json`)).toBe(true);
    await saveGrammar(page, true);
    const relinked = await grammarWhen(request, grammarId, value => value.metamodelId === importedId);
    expect(relinked).toMatchObject({ authoringMode: 'LINKED', metamodelId: importedId });

    // cl: quitar el metamodelo devuelve el modo a «primero la gramática»; los metamodelos no se borran.
    await metamodelSelect(page).selectOption('');
    await expect(authoringMode(page)).toHaveValue('GRAMMAR_FIRST');
    await saveGrammar(page, true);
    const unlinked = await grammarWhen(request, grammarId, value => value.metamodelId === '');
    expect(unlinked).toMatchObject({ id: grammarId, authoringMode: 'GRAMMAR_FIRST', metamodelId: '' });
    expect(readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`)).toMatchObject({ authoringMode: 'GRAMMAR_FIRST', metamodelId: '' });
    const remaining = (await listSavedMetamodels(request)).map(item => item.id);
    expect(remaining).toContain(seededId);
    expect(remaining).toContain(importedId);
});
