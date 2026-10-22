// Ayudantes compartidos de los specs E2E REALES del área «gramática». Sin simulaciones: la interfaz habla con el backend real;
// el oráculo sale de GET a la API real, de los ficheros que escribe el backend (data/grammars, data/textual-model-sessions...)
// y de observar (sin interceptar) las peticiones que lanza la propia interfaz.
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { eventually, openApp } from '../support';

export const LAST_GRAMMAR_KEY = 'hydra.grammar-designer.last-grammar.v1';

export interface GrammarDocumentJson { id: string; relativePath: string; languageId: string; content: string; sourceHash: string; entryPoint: boolean; generated: boolean }
export interface GrammarJson {
    schemaVersion: string; id: string; name: string; technology: string; authoringMode: string; metamodelId: string; toolchainProfileId: string;
    entryDocumentId: string; entryRule: string; documents: GrammarDocumentJson[]; settings: Record<string, string>; annotations: Record<string, string>;
    semanticBindings?: unknown; syntaxStyleDefinitionId?: string;
}
export interface DiagnosticJson { code: string; severity: string; title: string; message: string }
export interface AnalysisJson { stale: boolean; mode: string; draftRevision: number; sourceHash: string; diagnostics: DiagnosticJson[] }
export interface SnapshotJson { id: string; status: string; sourceHash: string; generatedProject: { files: Record<string, string> } }

/** Texto Langium mínimo de varias líneas (las reglas deben empezar en línea propia para el análisis local). */
export function langiumSource(grammarName: string, ...rules: string[]): string {
    return [`grammar ${grammarName}`, ...rules].join('\n');
}

/** Abre el diseñador de gramáticas con la interfaz en inglés. Si se indica una gramática, es la que recuerda el diseñador al montarse. */
export async function openGrammarDesigner(page: Page, rememberId = ''): Promise<void> {
    await page.goto('/');
    await page.evaluate(([id, key]) => {
        try {
            window.localStorage.setItem('hydra.language.v1', 'en');
            if (id !== '') window.localStorage.setItem(key, id); else window.localStorage.removeItem(key);
        } catch { /* sin almacenamiento: queda el inglés por defecto */ }
    }, [rememberId, LAST_GRAMMAR_KEY]);
    await openApp(page, '/#/grammars');
    await page.reload();
    await expect(page.getByRole('button', { name: /^New grammar$|^Nueva gramática$/i })).toBeVisible();
    // El diseñador carga de forma asíncrona la gramática recordada o la primera guardada; hay que dejar que termine.
    await page.waitForLoadState('networkidle');
    if (rememberId !== '') await expect(page.locator('#grammar-id')).toHaveValue(rememberId);
}

/** Pulsa «New grammar» y fija id y nombre únicos con los campos del formulario. */
export async function startNewGrammar(page: Page, id: string, name: string): Promise<void> {
    await page.getByRole('button', { name: /^New grammar$|^Nueva gramática$/i }).first().click();
    await expect(page.locator('#grammar-id')).toHaveValue(/^main-language/);
    await page.locator('#grammar-id').fill(id);
    await page.locator('#grammar-name').fill(name);
    await expect(page.locator('#grammar-id')).toHaveValue(id);
    await expect(page.locator('#grammar-name')).toHaveValue(name);
}

/** Guarda con el botón de la cabecera. Una gramática ya guardada pide confirmar la sobrescritura de sí misma. */
export async function saveGrammar(page: Page, alreadySaved: boolean): Promise<void> {
    await page.getByRole('button', { name: /^Save grammar$|^Guardar gramática$/i }).click();
    if (alreadySaved) await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click();
}

export const grammarToolbar = (page: Page) => page.locator('.grammar-document-toolbar');

// --- Monaco -----------------------------------------------------------------------------------------------------------

/** Normaliza los saltos de línea (Monaco en Windows usa CRLF; el texto del backend puede traer CRLF o LF). */
export function lf(text: string): string {
    return text.replace(/\r\n/g, '\n');
}

/** Texto del último modelo de Monaco creado (el del editor visible), con saltos LF; lectura del estado real del editor del navegador. */
export async function monacoText(page: Page): Promise<string> {
    const text = await page.evaluate(() => {
        const monaco = (window as unknown as { monaco?: { editor: { getModels(): Array<{ getValue(): string }> } } }).monaco;
        const models = monaco?.editor.getModels() ?? [];
        return models.length === 0 ? '' : models[models.length - 1].getValue();
    });
    return lf(text);
}

/** Da el foco al editor Monaco con un clic real y espera a que Monaco lo marque como enfocado (reintenta si el clic se pierde). */
export async function focusMonaco(page: Page): Promise<void> {
    const host = page.locator('.grammar-monaco-host').first();
    await host.scrollIntoViewIfNeeded();
    for (let attempt = 0; attempt < 4; attempt++) {
        // Primero el clic natural sobre las líneas visibles; si Monaco las cubre con otra capa, clic forzado; y como último recurso el foco programático del editor (solo para dar foco, el texto lo sigue escribiendo el teclado).
        const lines = host.locator('.view-lines').first();
        await lines.click({ position: { x: 60, y: 10 }, timeout: 3_000 }).catch(() => lines.click({ position: { x: 60, y: 10 }, force: true, timeout: 3_000 })).catch(() => undefined);
        if (await host.locator('.monaco-editor.focused').count() === 0 && attempt >= 2) await page.evaluate(() => { const editors = (window as unknown as { monaco?: { editor?: { getEditors?: () => { focus(): void }[] } } }).monaco?.editor?.getEditors?.(); editors?.[0]?.focus(); });
        if (await host.locator('.monaco-editor.focused').count() > 0) return;
        await page.waitForTimeout(200);
    }
    await expect(host.locator('.monaco-editor.focused')).toHaveCount(1);
}

/** El usuario selecciona todo el texto del editor y escribe otro (entrada de texto real; sin tocar el modelo por programa). */
export async function replaceMonacoText(page: Page, text: string): Promise<void> {
    for (let attempt = 0; ; attempt++) {
        await focusMonaco(page);
        await page.keyboard.press('Control+A');
        if (text === '') await page.keyboard.press('Delete'); else await page.keyboard.insertText(text);
        await page.keyboard.press('Escape'); // cierra una posible ayuda de completado sin aceptarla
        try {
            await eventually(() => monacoText(page), value => value === text, attempt < 2 ? 4_000 : 8_000);
            return;
        } catch (error) {
            if (attempt >= 2) throw error;
        }
    }
}

/** Código de estado de GET /api/grammars/{id}; reintenta si la conexión se corta (el backend compartido puede estar ocupado). */
export async function grammarHttpStatus(request: APIRequestContext, id: string): Promise<number> {
    for (let attempt = 0; ; attempt++) {
        try {
            return (await request.get(`/api/grammars/${id}`)).status();
        } catch (error) {
            if (attempt >= 4) throw error;
            await new Promise(resolve => setTimeout(resolve, 500));
        }
    }
}

// --- Observación (sin interceptar) del tráfico que lanza la interfaz --------------------------------------------------

export interface DraftCall { kind: 'analyze' | 'diagram' | 'conversion'; mode: string; revision: number; grammarId: string; content: string; body: string; at: number; result: unknown }

/** Registra las peticiones de análisis/proyección/conversión que la interfaz envía al backend real y sus respuestas. */
export function observeDraftTraffic(page: Page): DraftCall[] {
    const calls: DraftCall[] = [];
    const byRequest = new Map<unknown, DraftCall>();
    page.on('request', request => {
        if (request.method() !== 'POST') return;
        const url = request.url();
        const kind = url.endsWith('/api/grammars/analyze-draft') ? 'analyze' : url.endsWith('/api/grammars/class-diagram-draft') ? 'diagram' : url.endsWith('/api/grammars/conversion-preview') ? 'conversion' : null;
        if (kind === null) return;
        const body = request.postData() ?? '{}';
        const parsed = JSON.parse(body) as { grammar?: GrammarJson; analysis?: { mode?: string; draftRevision?: number } };
        const call: DraftCall = { kind, mode: parsed.analysis?.mode ?? '', revision: parsed.analysis?.draftRevision ?? -1, grammarId: parsed.grammar?.id ?? '', content: lf(parsed.grammar?.documents?.[0]?.content ?? ''), body, at: Date.now(), result: null };
        calls.push(call);
        byRequest.set(request, call);
    });
    page.on('response', response => {
        const call = byRequest.get(response.request());
        if (call === undefined) return;
        void response.json().then(json => { call.result = json; }).catch(() => { call.result = null; });
    });
    return calls;
}

/** POST directo a la API real de análisis (oráculo independiente del navegador) para un borrador dado. */
export async function analyzeViaApi(request: APIRequestContext, grammar: GrammarJson, content: string, mode: 'FAST' | 'NORMAL' | 'FULL' = 'NORMAL'): Promise<AnalysisJson> {
    // La interfaz envía sourceHash vacío en los borradores; un hash que no corresponde al texto lo rechaza el servidor.
    const documents = grammar.documents.map((document, index) => index === 0 ? { ...document, content, sourceHash: '' } : { ...document, sourceHash: '' });
    const draft = { ...grammar, documents };
    const analysis = { requestId: `e2e-oracle:${Date.now().toString(36)}:${Math.floor(Math.random() * 1e6)}`, grammarId: grammar.id, draftRevision: 1, mode, changedDocuments: documents.map(item => item.id), documents, toolchainProfileId: grammar.toolchainProfileId };
    const response = await request.post('/api/grammars/analyze-draft', { data: { grammar: draft, analysis } });
    if (!response.ok()) throw new Error(`analyze-draft -> ${response.status()} ${await response.text()}`);
    return (await response.json()) as AnalysisJson;
}

// --- Siembra por la API real (paso previo que no es la etapa certificada) --------------------------------------------

export function grammarDraft(id: string, name: string, content: string, settings: Record<string, string> = {}): GrammarJson {
    return {
        schemaVersion: '1.0', id, name, technology: 'LANGIUM', authoringMode: 'GRAMMAR_FIRST', metamodelId: '', toolchainProfileId: 'langium-4.3-browser',
        entryDocumentId: 'main', entryRule: 'Model',
        documents: [{ id: 'main', relativePath: 'main.langium', languageId: 'langium', content, sourceHash: '', entryPoint: true, generated: false }],
        semanticBindings: { schemaVersion: '1.0', bindings: [], annotations: {} }, syntaxStyleDefinitionId: '', settings, annotations: { 'hydra.entryRule.mode': 'AUTO' }
    };
}

export async function seedGrammar(request: APIRequestContext, grammar: GrammarJson): Promise<void> {
    const response = await request.post('/api/grammars', { data: grammar });
    if (!response.ok()) throw new Error(`no se pudo sembrar la gramática ${grammar.id}: ${response.status()} ${await response.text()}`);
}

/** Compila y activa una gramática guardada por la API real (la compilación en sí no es la etapa certificada). */
export async function compileViaApi(request: APIRequestContext, grammar: GrammarJson, revision = 1): Promise<SnapshotJson> {
    const response = await request.post(`/api/grammars/${grammar.id}/compile`, {
        data: { requestId: `e2e-compile:${Date.now().toString(36)}:${Math.floor(Math.random() * 1e6)}`, grammarId: grammar.id, sourceRevision: revision, documents: grammar.documents, toolchainProfileId: grammar.toolchainProfileId, activateOnSuccess: true }
    });
    if (!response.ok()) throw new Error(`compile ${grammar.id} -> ${response.status()} ${await response.text()}`);
    return (await response.json()) as SnapshotJson;
}

/** Documento de metamodelo canónico mínimo y válido (copiado del área metamodelo). */
export function metamodelDocument(id: string, name: string): Record<string, unknown> {
    return {
        schemaVersion: '2.1', id, name, annotations: {}, enums: [], dataTypes: [], relations: [],
        classes: [{ id: `${id}-class`, name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [{ id: `${id}-attribute`, name: 'fullName', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }] }]
    };
}

export async function seedMetamodel(request: APIRequestContext, id: string, name: string): Promise<void> {
    const response = await request.post('/api/canonical-metamodels', { data: metamodelDocument(id, name) });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
}
