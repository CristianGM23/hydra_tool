import { expect, test } from '@playwright/test';
import { apiGet, eventually, readJson, uid } from '../support';
import { grammarHttpStatus, lf, monacoText, openGrammarDesigner, replaceMonacoText, saveGrammar, startNewGrammar, type GrammarJson } from './helpers';

// Jornada: varios documentos en una gramática. Acción: «+ File», escribir en el editor Monaco del documento, quitar un documento
// sobrante con la «×» y «Save grammar». Oráculo: GET a la API real y el fichero guardado: lista de documentos sin el eliminado,
// con la extensión de la tecnología y el texto tecleado literal.

const explorer = (page: import('@playwright/test').Page) => page.locator('.grammar-source-explorer');
const removeButtons = (page: import('@playwright/test').Page) => explorer(page).getByRole('button', { name: /^Remove file$|^Quitar fichero$/ });

test('se añade un documento con la extensión de la tecnología, se escribe en su editor, se quita uno sobrante y se guarda lo escrito', async ({ page, request }) => {
    const grammarId = uid('docs');
    await openGrammarDesigner(page);
    await startNewGrammar(page, grammarId, uid('Docs language'));
    const mainText = await eventually(() => monacoText(page), text => text.includes('entry Model'));
    // Solo hay el documento de entrada y no se puede quitar.
    await expect(explorer(page).getByRole('button', { name: /main\.langium/ })).toBeVisible();
    await expect(removeButtons(page)).toHaveCount(0);

    // ad: «+ File» crea un documento con la extensión de la tecnología.
    await explorer(page).getByRole('button', { name: /^\+ File$|^\+ Fichero$/ }).click();
    await expect(explorer(page).getByRole('button', { name: /source-2\.langium/ })).toBeVisible();
    await expect(removeButtons(page)).toHaveCount(1);

    // wr: se escribe en el editor Monaco del documento nuevo.
    const extraText = "Extra: 'extra' name=ID;";
    await eventually(() => monacoText(page), text => text === '');
    await replaceMonacoText(page, extraText);

    // rm: se añade otro documento, sobra y se quita; el de entrada y el escrito permanecen.
    await explorer(page).getByRole('button', { name: /^\+ File$|^\+ Fichero$/ }).click();
    const surplus = explorer(page).locator(':scope > div').filter({ hasText: 'source-3.langium' });
    await expect(surplus).toHaveCount(1);
    await expect(removeButtons(page)).toHaveCount(2);
    await surplus.getByRole('button', { name: /^Remove file$|^Quitar fichero$/ }).click();
    await expect(explorer(page).getByRole('button', { name: /source-3\.langium/ })).toHaveCount(0);
    await expect(removeButtons(page)).toHaveCount(1);
    await eventually(() => monacoText(page), text => text === mainText); // al quitar vuelve el documento de entrada

    // Aún no hay nada guardado; tras «Save grammar» el backend tiene exactamente los documentos que quedan en pantalla.
    expect(await grammarHttpStatus(request, grammarId)).toBeGreaterThanOrEqual(400);
    await saveGrammar(page, false);
    const saved = await eventually(() => apiGet<GrammarJson>(request, `/api/grammars/${grammarId}`).catch(() => null), value => value !== null);
    const onDisk = readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`);
    for (const stored of [saved as GrammarJson, onDisk]) {
        expect(stored.documents.map(document => document.id)).toEqual(['main', 'source-2']);
        const extra = stored.documents[1];
        expect(extra).toMatchObject({ id: 'source-2', relativePath: 'source-2.langium', languageId: 'langium', entryPoint: false });
        expect(lf(stored.documents[0].content)).toBe(mainText);
        expect(lf(stored.documents[1].content)).toBe(extraText);
    }

    // El documento escrito se recupera al volver a abrir la gramática guardada.
    await openGrammarDesigner(page, grammarId);
    await explorer(page).getByRole('button', { name: /source-2\.langium/ }).click();
    await eventually(() => monacoText(page), text => text === extraText);
});
