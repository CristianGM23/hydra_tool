import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { eventually, readJson, uid, workDir } from '../support';
import { analyzeViaApi, grammarDraft, langiumSource, monacoText, openGrammarDesigner, replaceMonacoText, seedGrammar, type GrammarJson } from './helpers';

// Jornada: corregir un diagnóstico desde el panel de problemas. Acción: el usuario elige el diagnóstico, ve las correcciones del
// servidor y las locales del navegador y pulsa una. Oráculo: el texto que queda en el editor, un análisis directo al backend real
// con ese texto, y el almacenamiento, que no cambia (la corrección edita el borrador, no guarda).

const REPLACE_LOCAL = /Replace “item” with “Item”|Reemplazar “item”/;

test('un diagnóstico con corrección ofrece arreglos del servidor y locales y aplicar uno edita el borrador sin guardarlo', async ({ page, request }) => {
    const grammarId = uid('fixes');
    const valid = langiumSource('Fixes', 'entry Model: items+=Item*;', "Item: 'item' name=ID;");
    const broken = langiumSource('Fixes', 'entry Model: items+=item*;', "Item: 'item' name=ID;");
    await seedGrammar(request, grammarDraft(grammarId, uid('Fix language'), valid));
    const storedFile = path.join(workDir, 'data', 'grammars', `${grammarId}.grammar.json`);
    const before = await request.get(`/api/grammars/${grammarId}`);
    const beforeEtag = before.headers()['etag'] ?? '';
    const beforeBody = (await before.json()) as GrammarJson;
    const beforeMtime = fs.statSync(storedFile).mtimeMs;

    await openGrammarDesigner(page, grammarId);
    // El usuario escribe una referencia con mayúsculas erróneas.
    await replaceMonacoText(page, broken);

    // ty: hay correcciones del servidor (acción del analizador) y locales del navegador para el mismo problema.
    const localFix = page.locator('.grammar-quick-fix', { hasText: REPLACE_LOCAL });
    const serverFix = page.locator('.grammar-quick-fix', { hasText: /"Item"|“Item”/ }).filter({ hasNotText: /Replace|Reemplazar/ });
    await expect(localFix.first()).toBeVisible({ timeout: 20_000 });
    await expect(serverFix.first()).toBeVisible({ timeout: 20_000 });
    // Oráculo: el backend real reconoce el mismo problema sobre ese texto (control negativo del arreglo posterior).
    const control = await analyzeViaApi(request, beforeBody, broken);
    expect(control.diagnostics.some(item => /REFERENCE_CASE_MISMATCH/.test(item.code))).toBe(true);

    // sel: elegir el diagnóstico que lleva corrección lleva el cursor a su línea.
    const problem = page.locator('.grammar-problem').filter({ has: localFix }).first();
    await problem.locator('header button').first().click();
    await expect(page.locator('.grammar-editor-status')).toContainText(/Line 2,|Línea 2,/);

    // ap: aplicar la corrección local edita el texto del borrador.
    await localFix.first().click();
    await eventually(() => monacoText(page), text => text === valid, 8_000);
    await expect(page.locator('.grammar-quick-fix', { hasText: REPLACE_LOCAL })).toHaveCount(0, { timeout: 20_000 });

    // El backend real, con el texto resultante, ya no devuelve el error; y el borrador sigue sin guardar.
    const after = await analyzeViaApi(request, beforeBody, await monacoText(page));
    expect(after.diagnostics.filter(item => /REFERENCE_(CASE_MISMATCH|NOT_FOUND)/.test(item.code))).toHaveLength(0);
    const stored = await request.get(`/api/grammars/${grammarId}`);
    expect(stored.headers()['etag'] ?? '').toBe(beforeEtag);
    expect(await stored.json()).toEqual(beforeBody);
    expect(fs.statSync(storedFile).mtimeMs).toBe(beforeMtime);
    expect(readJson<GrammarJson>(`data/grammars/${grammarId}.grammar.json`).documents[0].content).toBe(valid);
});
