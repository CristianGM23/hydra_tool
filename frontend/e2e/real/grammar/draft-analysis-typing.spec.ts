import { expect, test } from '@playwright/test';
import { eventually, exists, uid } from '../support';
import { analyzeViaApi, grammarHttpStatus, grammarToolbar, observeDraftTraffic, openGrammarDesigner, replaceMonacoText, startNewGrammar, focusMonaco, type AnalysisJson, type GrammarJson } from './helpers';

// Jornada: el diseñador analiza el borrador mientras el usuario escribe. Acción: teclado real en el editor Monaco. Oráculo: las
// peticiones que la propia interfaz envía al backend real (observadas sin interceptar, con su respuesta), contrastadas con una
// llamada directa a la API real con el mismo borrador; y el almacenamiento, que no debe cambiar (el borrador no se guarda).

const codes = (analysis: AnalysisJson): string[] => analysis.diagnostics.map(item => `${item.severity}:${item.code}`).sort();

test('el análisis rápido y la proyección de clases se piden tras una pausa y no mientras se teclea', async ({ page, request }) => {
    const calls = observeDraftTraffic(page);
    const grammarId = uid('draft');
    await openGrammarDesigner(page);
    await startNewGrammar(page, grammarId, uid('Draft language'));

    // fast: se escribe una regla que referencia una regla inexistente y el análisis rápido la señala.
    const broken = ['grammar Draft', 'entry Model: items+=Item*;', "Item: 'item' name=Missing;"].join('\n');
    await replaceMonacoText(page, broken);
    const fast = await eventually(
        () => calls.filter(call => call.kind === 'analyze' && call.grammarId === grammarId && call.mode === 'FAST' && call.content === broken),
        found => found.length > 0 && found[found.length - 1].result !== null);
    const fastCall = fast[fast.length - 1];
    const fastResult = fastCall.result as AnalysisJson;
    expect(fastResult.stale).toBe(false);
    expect(fastResult.draftRevision).toBe(fastCall.revision);
    await expect(page.locator('.grammar-problem').first()).toBeVisible();
    // Oráculo: la misma petición enviada directamente al backend real da los mismos diagnósticos que recibió la interfaz.
    const sentGrammar = (JSON.parse(fastCall.body) as { grammar: GrammarJson }).grammar;
    const direct = await analyzeViaApi(request, sentGrammar, broken, 'FAST');
    expect(direct.diagnostics.length).toBeGreaterThan(0);
    expect(codes(direct)).toEqual(codes(fastResult));

    // pn: tras la pausa se pide la proyección de clases del borrador y la pantalla muestra su recuento.
    const diagram = await eventually(
        () => calls.filter(call => call.kind === 'diagram' && call.grammarId === grammarId && call.content === broken),
        found => found.length > 0 && found[found.length - 1].result !== null);
    expect(diagram[diagram.length - 1].mode).toBe('NORMAL');
    expect(diagram[diagram.length - 1].revision).toBe(fastCall.revision);
    expect(diagram[diagram.length - 1].at).toBeGreaterThanOrEqual(fastCall.at);
    await expect(page.locator('.grammar-workbench-view-tabs > span')).toHaveText(/\d+ (classifiers|clasificadores)/);
    await eventually(() => calls.filter(call => call.kind === 'analyze' && call.grammarId === grammarId && call.content === broken && call.mode === 'FULL' && call.result !== null).length, count => count > 0, 30_000);
    await expect(grammarToolbar(page)).toContainText(/Full analysis|Análisis completo/);

    // Rechazo: mientras se teclea no se pide ni análisis ni diagrama; la pausa posterior sí los pide con la revisión nueva.
    const before = calls.length;
    const previousRevision = Math.max(...calls.filter(call => call.grammarId === grammarId).map(call => call.revision));
    await focusMonaco(page);
    await page.keyboard.press('Control+End');
    const typed = ' // typing';
    await page.keyboard.type(typed, { delay: 40 });
    expect(calls.length).toBe(before);
    const afterPause = await eventually(
        () => calls.filter(call => call.kind === 'analyze' && call.grammarId === grammarId && call.mode === 'FAST' && call.content.endsWith(typed)),
        found => found.length > 0);
    expect(afterPause[0].revision).toBeGreaterThanOrEqual(previousRevision + typed.length);
    const diagramAfter = await eventually(
        () => calls.filter(call => call.kind === 'diagram' && call.grammarId === grammarId && call.content.endsWith(typed)),
        found => found.length > 0);
    expect(diagramAfter[0].revision).toBe(afterPause[0].revision);
    expect(diagramAfter[0].at).toBeGreaterThanOrEqual(afterPause[0].at);

    // Nada de esto guarda la gramática.
    expect(exists(`data/grammars/${grammarId}.grammar.json`)).toBe(false);
    expect(await grammarHttpStatus(request, grammarId)).toBeGreaterThanOrEqual(400);
});
