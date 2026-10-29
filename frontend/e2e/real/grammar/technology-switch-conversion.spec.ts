import { expect, test } from '@playwright/test';
import { apiGet, eventually, readJson, uid } from '../support';
import { grammarHttpStatus, lf, monacoText, observeDraftTraffic, openGrammarDesigner, replaceMonacoText, saveGrammar, startNewGrammar, type GrammarJson } from './helpers';

// Jornada: cambiar la tecnología de la gramática. Con el texto vacío la sustitución es directa; con texto se pasa por la vista
// previa de conversión, que no guarda nada, y solo al aplicar y guardar el fichero del backend cambia. Acción: selector de
// tecnología, diálogo y botones reales. Oráculo: GET a la API real, el fichero guardado y una llamada directa a la API de conversión.

interface PreviewJson { semanticallyEquivalent: boolean; targetTechnology: string; convertedGrammar: GrammarJson }
const dialogOf = (page: import('@playwright/test').Page) => page.getByRole('dialog');

test('sin texto la tecnología se sustituye sin diálogo; con texto pasa por la vista previa y solo aplicar y guardar cambia lo guardado', async ({ page, request }) => {
    const calls = observeDraftTraffic(page);
    await openGrammarDesigner(page);

    // em (rama vacía): sin texto, elegir Xtext sustituye directamente y no se pide ninguna conversión.
    const emptyId = uid('switch-empty');
    await startNewGrammar(page, emptyId, uid('Switch empty'));
    await replaceMonacoText(page, '');
    await page.locator('#grammar-technology').selectOption('XTEXT');
    await expect(page.locator('#grammar-technology')).toHaveValue('XTEXT');
    await expect(dialogOf(page)).toHaveCount(0);
    await eventually(() => monacoText(page), text => text.includes('xtext'));
    expect(calls.filter(call => call.kind === 'conversion')).toHaveLength(0);
    await saveGrammar(page, false);
    const emptySaved = await eventually(() => apiGet<GrammarJson>(request, `/api/grammars/${emptyId}`).catch(() => null), value => value !== null);
    expect(emptySaved).toMatchObject({ id: emptyId, technology: 'XTEXT' });

    // em (rama con texto): se pide la vista previa; cancelar deja la gramática como estaba.
    const grammarId = uid('switch-text');
    await startNewGrammar(page, grammarId, uid('Switch text'));
    const original = await eventually(() => monacoText(page), text => text.includes('entry Model'));
    await page.locator('#grammar-technology').selectOption('XTEXT');
    const dialog = dialogOf(page);
    await expect(dialog).toBeVisible({ timeout: 30_000 });
    await expect(dialog).toContainText(/Equivalent structure|Estructura equivalente/);
    expect(await grammarHttpStatus(request, grammarId)).toBeGreaterThanOrEqual(400); // la vista previa no persiste
    await dialog.locator('.grammar-conversion-footer').getByRole('button', { name: /^(Keep current grammar|Mantener la gramática actual)$/ }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('#grammar-technology')).toHaveValue('LANGIUM');
    expect(await monacoText(page)).toBe(original);

    // Elegir de nuevo y aplicar: el texto pasa a ser el que devuelve el backend real para esa misma petición.
    await page.locator('#grammar-technology').selectOption('XTEXT');
    await expect(dialog).toBeVisible({ timeout: 30_000 });
    const conversions = calls.filter(call => call.kind === 'conversion' && call.grammarId === grammarId);
    expect(conversions.length).toBeGreaterThanOrEqual(2);
    const sent = JSON.parse(conversions[conversions.length - 1].body) as { grammar: GrammarJson; targetTechnology: string };
    expect(sent.targetTechnology).toBe('XTEXT');
    const directResponse = await request.post('/api/grammars/conversion-preview', { data: sent });
    expect(directResponse.ok()).toBe(true);
    const preview = (await directResponse.json()) as PreviewJson;
    expect(preview.semanticallyEquivalent).toBe(true);
    expect(preview.convertedGrammar.technology).toBe('XTEXT');
    const convertedEntry = preview.convertedGrammar.documents.find(document => document.id === preview.convertedGrammar.entryDocumentId) ?? preview.convertedGrammar.documents[0];

    await dialog.getByRole('button', { name: /^(Apply converted grammar|Aplicar gramática convertida)$/ }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('#grammar-technology')).toHaveValue('XTEXT');
    await eventually(() => monacoText(page), text => text === lf(convertedEntry.content));
    expect(await grammarHttpStatus(request, grammarId)).toBeGreaterThanOrEqual(400); // aplicar tampoco guarda

    // Guardar: el fichero del backend queda con la tecnología y el texto convertidos.
    await saveGrammar(page, false);
    const saved = await eventually(() => apiGet<GrammarJson>(request, `/api/grammars/${grammarId}`).catch(() => null), value => value !== null);
    expect(saved?.technology).toBe('XTEXT');
    const onDisk = readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`);
    expect(onDisk.technology).toBe('XTEXT');
    expect(lf(onDisk.documents.find(document => document.id === onDisk.entryDocumentId)?.content ?? '')).toBe(lf(convertedEntry.content));
    expect(lf(onDisk.documents[0].content)).not.toBe(original);
});
