import { expect, test } from '@playwright/test';
import { apiGet, eventually, readJson, uid } from '../support';
import { compileViaApi, grammarDraft, langiumSource, openGrammarDesigner, saveGrammar, seedGrammar, type GrammarJson } from './helpers';

// Jornada: personalizar el aspecto del editor textual de una gramática. Acción: paneles reales «Editor appearance», «Contextual
// information» y «Editor preview» (selectores, campos de color y de texto) y «Save grammar». Oráculo: GET a la API real y el
// fichero guardado (ajustes de aspecto con los valores elegidos y claves ajenas intactas), el pintado en el editor tras recargar
// y dos compilaciones por la API real (antes y después) que dan el mismo proyecto generado.

const PREFIX = 'hydra.textual.';

test('fuentes, colores, palabras clave, estilos por regla y ayudas se guardan con la gramática, se pintan al recargar y no alteran la compilación', async ({ page, request }) => {
    const grammarId = uid('look');
    const source = langiumSource('Look', 'entry Model: nodes+=Node* edges+=Edge*;', "Node: 'node' name=ID;", "Edge: 'edge' label=ID;");
    await seedGrammar(request, grammarDraft(grammarId, uid('Look language'), source, { 'hydra.custom.keep': 'intacta' }));
    const seeded = await apiGet<GrammarJson>(request, `/api/grammars/${grammarId}`);
    expect(Object.keys(seeded.settings).filter(key => key.startsWith(PREFIX))).toHaveLength(0);
    const compiledBefore = await compileViaApi(request, seeded);
    expect(compiledBefore.status).toBe('SUCCESS');

    await openGrammarDesigner(page, grammarId);

    // f: familia, tamaño, interlineado y colores del editor.
    await page.getByRole('button', { name: /^Editor appearance$|^Aspecto del editor$/ }).click();
    await page.locator('#grammar-textual-font-family').selectOption({ label: 'Consolas' });
    await page.locator('#grammar-textual-font-size').fill('18');
    await page.locator('#grammar-textual-line-height').fill('27');
    await page.locator('#grammar-textual-foreground').fill('#112233');
    await page.locator('#grammar-textual-background').fill('#fffbe6');

    // k: palabras clave propias.
    await page.locator('#grammar-textual-keywords').fill('node, zebra');

    // ro: estilo general del rol de palabras reservadas y estilo propio de la regla «Node».
    await page.locator('#grammar-style-role').selectOption('KEYWORD');
    await page.locator('#grammar-style-color').fill('#00aa00');
    await page.locator('#grammar-style-italic').check({ force: true });
    await page.locator('#grammar-style-scope').selectOption('RULE');
    await page.locator('#grammar-style-symbol').selectOption('Node');
    await page.locator('#grammar-style-color').fill('#cc00cc');

    // h: ayuda emergente de «Node».
    await page.getByRole('button', { name: /^Contextual information$|^Información contextual$/ }).click();
    await page.locator('#grammar-hover-symbol').selectOption('Node');
    await page.locator('#grammar-hover-title').fill('Nodo del grafo');
    await page.locator('#grammar-hover-description').fill('Un nodo con nombre dentro del modelo.');

    // Antes de guardar, el backend no sabe nada de los cambios.
    const unsaved = await apiGet<GrammarJson>(request, `/api/grammars/${grammarId}`);
    expect(Object.keys(unsaved.settings).filter(key => key.startsWith(PREFIX))).toHaveLength(0);

    // sv: guardar con la gramática (ya estaba guardada: pide confirmar la sobrescritura).
    await saveGrammar(page, true);
    const saved = await eventually(() => apiGet<GrammarJson>(request, `/api/grammars/${grammarId}`), value => `${PREFIX}fontSize` in value.settings);

    // r: el aspecto guardado es lo que devuelve la API y el fichero, con las claves ajenas intactas.
    for (const settings of [saved.settings, readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`).settings]) {
        expect(settings[`${PREFIX}fontFamily`]).toBe('Consolas, "Courier New", monospace');
        expect(settings[`${PREFIX}fontSize`]).toBe('18');
        expect(settings[`${PREFIX}lineHeight`]).toBe('27');
        expect(settings[`${PREFIX}foreground`]).toBe('#112233');
        expect(settings[`${PREFIX}background`]).toBe('#fffbe6');
        expect(settings[`${PREFIX}keywords`].split(',')).toEqual(expect.arrayContaining(['node', 'zebra']));
        expect(settings[`${PREFIX}role.KEYWORD`]).toBe('#00aa00|1|1');
        expect(settings[`${PREFIX}symbol.RULE.Node`]).toBe('#cc00cc|1|0');
        expect(settings[`${PREFIX}hover.Node.title`]).toBe('Nodo del grafo');
        expect(settings[`${PREFIX}hover.Node.description`]).toBe('Un nodo con nombre dentro del modelo.');
        expect(settings['hydra.custom.keep']).toBe('intacta');
    }
    expect(saved.documents[0].content).toBe(source);

    // Recargar: la pantalla muestra lo guardado y el editor de vista previa pinta con ese aspecto.
    await openGrammarDesigner(page, grammarId);
    await page.getByRole('button', { name: /^Editor appearance$|^Aspecto del editor$/ }).click();
    await expect(page.locator('#grammar-textual-font-size')).toHaveValue('18');
    await expect(page.locator('#grammar-textual-foreground')).toHaveValue('#112233');
    await expect(page.locator('#grammar-textual-keywords')).toHaveValue(/zebra/);
    await page.getByRole('button', { name: /^Editor preview$|^Vista previa del editor$/ }).click();
    const preview = page.locator('.grammar-preview-editor');
    await expect(preview.locator('.monaco-editor-background').first()).toHaveCSS('background-color', 'rgb(255, 251, 230)');
    await expect(preview.locator('.view-lines .hydra-textual-style-cc00cc-b-n').first()).toBeVisible(); // «node»: estilo propio de la regla
    await expect(preview.locator('.view-lines .hydra-textual-style-00aa00-b-i').first()).toBeVisible(); // «edge»: estilo del rol de palabra reservada
    await preview.locator('.view-lines .hydra-textual-style-cc00cc-b-n').first().hover();
    await expect(page.locator('.monaco-hover:not(.hidden)').filter({ hasText: 'Nodo del grafo' }).first()).toBeVisible({ timeout: 15_000 });

    // La personalización no cambia lo que se genera: misma huella del texto y mismos ficheros del proyecto generado.
    // La pantalla ya analizó borradores de esta gramática con revisiones altas: se compila con una revisión posterior a todas ellas.
    const compiledAfter = await compileViaApi(request, saved, 1_000_000);
    expect(compiledAfter.status, JSON.stringify((compiledAfter as unknown as { diagnostics?: unknown }).diagnostics ?? []).slice(0, 600)).toBe('SUCCESS');
    expect(compiledAfter.sourceHash).toBe(compiledBefore.sourceHash);
    expect(Object.keys(compiledAfter.generatedProject.files).sort()).toEqual(Object.keys(compiledBefore.generatedProject.files).sort());
});
