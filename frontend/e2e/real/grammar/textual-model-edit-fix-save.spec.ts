import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { apiGet, eventually, readJson, uid } from '../support';
import { compileViaApi, grammarDraft, langiumSource, lf, monacoText, replaceMonacoText, seedGrammar } from './helpers';

// Jornada: editor de modelos en texto sobre una gramática compilada y activa. Acción: abrir el editor (se rellena el ejemplo),
// «New session», escribir en Monaco, introducir un token inválido, corregir a mano, volver a romper, pulsar la corrección sugerida
// del panel y «Promote valid model». Oráculo: GET a la sesión que guarda el backend real y su fichero JSON: texto, revisión,
// diagnósticos y modelo semántico válido/promovido. La definición del editor y la gramática compilada se siembran por la API real.

interface SessionJson {
    id: string; editorDefinitionId: string; draftRevision: number; lastParsedRevision: number; draftValid?: boolean;
    documents: Array<{ id: string; content: string }>; diagnostics: Array<{ code: string; severity: string; metadata?: Record<string, string> }>;
    lastValidSemanticModel: { elements: Array<{ id: string }> } | null; lastCommittedSemanticModel: { elements: Array<{ id: string }> } | null;
    lastCommittedRevision: number; metadata: Record<string, string>;
}

const GRAMMAR_RULES = ['entry Model: nodes+=Node*;', "Node: 'node' name=ID;"];
const content = (value: SessionJson): string => lf(value.documents[0]?.content ?? '');
const ids = (model: SessionJson['lastValidSemanticModel']): string[] => (model?.elements ?? []).map(element => element.id);

async function seedTextualEditor(request: APIRequestContext): Promise<{ grammarId: string; definitionId: string }> {
    const grammarId = uid('txg');
    const grammar = grammarDraft(grammarId, uid('Textual language'), langiumSource('Stable', ...GRAMMAR_RULES));
    await seedGrammar(request, grammar);
    const snapshot = await compileViaApi(request, grammar);
    if (snapshot.status !== 'SUCCESS') throw new Error('la gramática sembrada no compila: ' + JSON.stringify(snapshot).slice(0, 400));
    // El editor textual abre la primera definición de la lista (orden por id): un prefijo decreciente hace que la más reciente sea la primera.
    const definitionId = `0-${(9_000_000_000_000 - Date.now()).toString()}-${uid('txe')}`;
    const response = await request.post('/api/textual-editor-definitions', {
        data: {
            schemaVersion: '1.0', id: definitionId, name: `Textual editor ${definitionId}`, grammarId, compilationSnapshotId: '', syntaxStyleDefinitionId: '',
            fileExtensions: ['.ex1'], entryRule: 'Model', runtimeMode: 'AUTO', features: ['DIAGNOSTICS', 'QUICK_FIXES', 'OUTLINE'],
            validationModelIds: [], actionModelIds: [], eventDefinitionIds: [], eventFlowIds: [], gamificationDefinitionIds: [], settings: {}
        }
    });
    if (!response.ok()) throw new Error(`no se pudo sembrar la definición del editor: ${response.status()} ${await response.text()}`);
    return { grammarId, definitionId };
}

async function openTextualEditor(page: Page): Promise<void> {
    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    await page.goto('/#/textual-models');
    await page.reload();
    await expect(page.locator('.textual-model-editor')).toBeVisible();
}

test.setTimeout(180_000); // compila una gramática y encadena varias sesiones y análisis del servidor

test('se rellena el ejemplo, se escribe, se corrige a mano y con la sugerencia del panel, y solo un borrador válido se promueve', async ({ page, request }) => {
    const { definitionId } = await seedTextualEditor(request);
    // La interfaz adopta la sesión de la ÚLTIMA PETICIÓN INICIADA (sessionSerial en TextualModelEditor descarta las respuestas antiguas),
    // así que los ids se guardan en orden de inicio (evento request) y no de llegada: con dos POST casi simultáneos (la sesión inicial y
    // «New session») la respuesta de la petición más antigua puede llegar después (traza 2026-10-25: 317 ms frente a 64 ms) y la prueba
    // consultaba una sesión que la interfaz ya había descartado.
    const sessionIds: Promise<string | undefined>[] = [];
    page.on('request', sessionRequest => {
        if (sessionRequest.method() !== 'POST' || !/\/api\/textual-model-sessions$/.test(sessionRequest.url())) return;
        sessionIds.push(sessionRequest.response()
            .then(response => (response?.ok() ? response.json() : undefined))
            .then((body?: { id?: string }) => body?.id)
            .catch(() => undefined));
    });
    const session = async (): Promise<SessionJson> => apiGet<SessionJson>(request, `/api/textual-model-sessions/${await sessionIds[sessionIds.length - 1]}`);
    const settled = (predicate: (value: SessionJson) => boolean) => eventually(session, predicate, 30_000);
    const status = page.locator('.textual-runtime-status');
    const problems = page.locator('.textual-problems');

    await openTextualEditor(page);
    await expect(page.locator('.textual-definition select').first()).toHaveValue(definitionId, { timeout: 30_000 });

    // ex: con el texto vacío se rellena un ejemplo derivado de la gramática activa.
    await eventually(() => monacoText(page), text => text === 'node name1', 30_000);
    await expect(status).toContainText(/Compiled grammar version: (?!No compiled version)\S+/, { timeout: 30_000 });
    await page.getByRole('button', { name: /^New session$|^Nueva sesión$/ }).click();
    await expect(page.locator('.textual-message')).toContainText(/Textual session started|The current draft does not comply/, { timeout: 30_000 });
    const started = await settled(value => content(value) === 'node name1' && ids(value.lastValidSemanticModel).length > 0);
    expect(started.editorDefinitionId).toBe(definitionId);
    expect(ids(started.lastValidSemanticModel)).toEqual(['name1']);

    // wr: el texto escrito llega a la sesión del backend y da su modelo semántico.
    const validText = 'node alpha\nnode beta';
    await replaceMonacoText(page, validText);
    const written = await settled(value => content(value) === validText && value.metadata['draftValid'] === 'true');
    expect(ids(written.lastValidSemanticModel)).toEqual(['alpha', 'beta']);
    expect(written.draftRevision).toBeGreaterThan(started.draftRevision);

    // Rechazo: un token inválido da diagnóstico, el último modelo válido se conserva y no se puede promover.
    const promote = page.getByRole('button', { name: /^Promote valid model$|^Promover modelo válido$/ });
    await replaceMonacoText(page, 'node alpha\nnode 123');
    const broken = await settled(value => content(value) === 'node alpha\nnode 123' && value.metadata['draftValid'] === 'false');
    expect(broken.diagnostics.some(item => item.severity === 'ERROR')).toBe(true);
    expect(ids(broken.lastValidSemanticModel)).toEqual(['alpha', 'beta']);
    expect(broken.lastCommittedSemanticModel).toBeNull();
    await expect(problems.locator('article.error').first()).toBeVisible();
    await expect(promote).toBeDisabled();

    // m: corregir a mano; el diagnóstico desaparece y el backend acepta el borrador.
    await replaceMonacoText(page, 'node alpha\nnode manual');
    const manual = await settled(value => content(value) === 'node alpha\nnode manual' && value.metadata['draftValid'] === 'true');
    expect(manual.diagnostics).toHaveLength(0);
    expect(ids(manual.lastValidSemanticModel)).toEqual(['alpha', 'manual']);
    await expect(problems).toContainText(/No blocking textual problems|Sin problemas/);

    // ap: volver a romper y pulsar la corrección sugerida del panel repara el texto.
    await replaceMonacoText(page, 'node alpha\nnode 456');
    await settled(value => content(value) === 'node alpha\nnode 456' && value.metadata['draftValid'] === 'false');
    const quickFix = problems.locator('.textual-problem-guidance button').first();
    await expect(quickFix).toBeVisible({ timeout: 20_000 });
    await quickFix.click();
    const repairedText = await eventually(() => monacoText(page), text => /^node alpha\nnode [A-Za-z_]\w*$/.test(text), 10_000);
    expect(repairedText).not.toBe('node alpha\nnode 456');
    const repaired = await settled(value => content(value) === repairedText && value.metadata['draftValid'] === 'true');
    expect(repaired.diagnostics).toHaveLength(0);
    const repairedName = repairedText.split('\n')[1].replace('node ', '');
    expect(ids(repaired.lastValidSemanticModel)).toEqual(['alpha', repairedName]);

    // Promover: la sesión servida lleva `draftValid` verdadero, la pantalla muestra «validated», habilita el botón y la promoción
    // deja el modelo del texto final como modelo comprometido, también en el fichero de la sesión.
    expect(repaired.draftValid).toBe(true);
    expect(repaired.lastCommittedSemanticModel).toBeNull();
    await expect(status).toContainText(/Draft status: validated/, { timeout: 20_000 });
    await expect(promote).toBeEnabled();
    await promote.click();
    await expect(page.locator('.textual-message')).toContainText(/Valid textual model promoted/, { timeout: 30_000 });
    const promoted = await settled(value => ids(value.lastCommittedSemanticModel).length > 0);
    expect(ids(promoted.lastCommittedSemanticModel)).toEqual(['alpha', repairedName]);
    expect(ids(readJson<SessionJson>(`data/textual-model-sessions/${promoted.id}.textual-session.json`).lastCommittedSemanticModel)).toEqual(['alpha', repairedName]);
});
