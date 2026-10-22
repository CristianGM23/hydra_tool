import { expect, test } from '@playwright/test';
import { apiGet, eventually, exists, readJson, uid } from '../support';
import { grammarHttpStatus, lf, monacoText, openGrammarDesigner, saveGrammar, seedMetamodel, startNewGrammar, type GrammarJson } from './helpers';

// Jornada: identidad de una gramática nueva. Acción: «New grammar», nombre, tecnología (con su diálogo de conversión), modo de
// trabajo con metamodelo, guardar y volver a elegirla de la lista de guardadas. Oráculo: GET a la API real de gramáticas y el
// fichero que escribe el backend; antes de guardar no existe nada guardado.

test('una gramática nueva parte de Langium por defecto, cambia a Xtext y a modo vinculado, se guarda y se recupera de la lista', async ({ page, request }) => {
    const metamodelId = uid('people');
    await seedMetamodel(request, metamodelId, uid('People'));
    const grammarId = uid('identity');
    const grammarName = uid('Identity language');

    await openGrammarDesigner(page);
    // n: «New grammar» arranca de una gramática Langium por defecto; todavía no hay nada guardado.
    await startNewGrammar(page, grammarId, grammarName);
    await expect(page.locator('#grammar-technology')).toHaveValue('LANGIUM');
    await expect(page.locator('#grammar-authoring-mode')).toHaveValue('GRAMMAR_FIRST');
    const initialText = await eventually(() => monacoText(page), text => text.includes('entry Model'));
    expect(initialText).toMatch(/^grammar Main/);
    expect(await grammarHttpStatus(request, grammarId)).toBeGreaterThanOrEqual(400);
    expect(exists(`data/grammars/${grammarId}.grammar.json`)).toBe(false);

    // t: elegir Xtext con texto escrito abre el diálogo de conversión; al aplicarlo cambia la tecnología.
    await page.locator('#grammar-technology').selectOption('XTEXT');
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible({ timeout: 30_000 });
    await expect(dialog.locator('#grammar-conversion-title')).toContainText(/Safe grammar conversion|Conversión segura/);
    await dialog.getByRole('button', { name: /^(Apply converted grammar|Aplicar gramática convertida)$/ }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('#grammar-technology')).toHaveValue('XTEXT');
    const convertedText = await eventually(() => monacoText(page), text => text !== initialText && text !== '');
    expect(await grammarHttpStatus(request, grammarId)).toBeGreaterThanOrEqual(400); // aplicar no guarda

    // t: modo de trabajo «vinculado» con un metamodelo guardado.
    await page.locator('#grammar-authoring-mode').selectOption('LINKED');
    await page.locator('#grammar-metamodel').selectOption(metamodelId);
    await expect(page.locator('#grammar-authoring-mode')).toHaveValue('LINKED');
    await saveGrammar(page, false);

    const saved = await eventually(() => apiGet<GrammarJson>(request, `/api/grammars/${grammarId}`).catch(() => null), value => value !== null);
    expect(saved).toMatchObject({ id: grammarId, name: grammarName, technology: 'XTEXT', authoringMode: 'LINKED', metamodelId });
    expect(saved?.entryRule).not.toBe('');
    const onDisk = readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`);
    expect(onDisk).toMatchObject({ id: grammarId, name: grammarName, technology: 'XTEXT', authoringMode: 'LINKED', metamodelId });
    expect(lf(onDisk.documents[0].content)).toBe(convertedText);

    // n: volver a elegir la gramática guardada de la lista restaura identidad, tecnología, modo y texto.
    await page.getByRole('button', { name: /^New grammar$|^Nueva gramática$/i }).first().click();
    await expect(page.locator('#grammar-id')).toHaveValue(/^main-language/);
    await page.locator('.grammar-saved-selector select').selectOption(grammarId);
    await expect(page.locator('#grammar-id')).toHaveValue(grammarId);
    await expect(page.locator('#grammar-name')).toHaveValue(grammarName);
    await expect(page.locator('#grammar-technology')).toHaveValue('XTEXT');
    await expect(page.locator('#grammar-authoring-mode')).toHaveValue('LINKED');
    await expect(page.locator('#grammar-metamodel')).toHaveValue(metamodelId);
    await eventually(() => monacoText(page), text => text === lf(onDisk.documents[0].content));
});
