// Ayudantes de los specs E2E REALES del área gamification (navegador real + backend real, sin simulaciones de la API).
// Siembra solo con la API real (metamodelo, catálogo, eventos); las acciones de cada etapa las hace el usuario en la interfaz.
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { exists, openApp, readJson } from '../support';

/** Expresiones bilingües (la interfaz arranca en inglés; el español se cubre con los textos del catálogo de traducción). */
export const RE = {
    guided: /Guided|Guiado/,
    form: /Form|Formulario/,
    diagram: /Diagram|Diagrama/,
    source: /Source|Origen/,
    ledger: /Runtime & ledger|Ejecución y ledger/,
    quality: /^(Quality|Calidad)$/,
    changes: /^(Changes|Cambios)$/,
    check: /^(Check|Comprobar)$/,
    save: /^(Save gamification|Guardar gamificación)$/,
    next: /Next step|Paso siguiente/,
    refresh: /^(Refresh|Actualizar)$/,
    apply: /Apply source|Aplicar origen/,
    connect: /Connect elements|Conectar elementos/,
    removeRelation: /Remove relation|Eliminar relación/,
    overwrite: /Overwrite saved element|Sobrescribir elemento guardado/,
    saved: /Gamification definitions, rules and diagram saved|Definiciones de gamificación, reglas y diagrama guardados/,
    valid: /Gamification valid|Gamificación válida/,
    applied: /Gamification source applied|Fuente de gamificación aplicada/,
};

export const ALERT = '[data-hydra-error-context="gamification designer"]';
export const STATUS = '.hydra-independent-workbench .status-message';
export const EVIDENCE_EVENT = 'core.validation.violation-resolved';

export type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

export function escapeRegExp(text: string): string { return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

/** Metamodelo mínimo real (schema 2.1) sembrado por la API canónica. */
export async function seedMetamodel(request: APIRequestContext, id: string): Promise<void> {
    const response = await request.post('/api/canonical-metamodels', {
        data: {
            schemaVersion: '2.1', id, name: id,
            classes: [{ id: 'class:Item', name: 'Item', kind: 'CLASS', attributes: [], operations: [], annotations: {} }],
            enums: [], dataTypes: [], relations: [], annotations: {},
        },
    });
    if (!response.ok()) throw new Error(`POST metamodelo ${id} -> ${response.status()} ${await response.text()}`);
}

export interface RuleSeed { id: string; name?: string; eventTypeId?: string; pointsTypeId?: string; points?: number; repeatPolicy?: string; effectKind?: string }

export function buildModels(metamodelId: string, options: { points?: Array<{ id: string; name: string; minimumBalance?: number }>; rules?: RuleSeed[]; definitionAnnotations?: Record<string, string>; bindingAnnotations?: Record<string, string> } = {}): { definitions: Json; bindings: Json } {
    const definitionId = `${metamodelId}-gamification`;
    return {
        definitions: {
            schemaVersion: '1.0', id: definitionId, metamodelId, name: 'E2E gamification',
            pointTypes: (options.points ?? []).map((point) => ({ id: point.id, name: point.name, description: '', minimumBalance: point.minimumBalance ?? 0 })),
            levels: [], achievements: [], challenges: [], rewards: [], annotations: options.definitionAnnotations ?? {},
        },
        bindings: {
            schemaVersion: '1.0', id: `${definitionId}-bindings`, metamodelId, definitionModelId: definitionId,
            rules: (options.rules ?? []).map((rule) => ({
                id: rule.id, name: rule.name ?? rule.id, eventTypeId: rule.eventTypeId ?? EVIDENCE_EVENT, conditionPath: '', operator: 'EQUALS',
                pointsTypeId: rule.pointsTypeId ?? '', points: rule.points ?? 0, achievementId: '', challengeId: '', rewardId: '',
                repeatPolicy: rule.repeatPolicy ?? 'ONCE_PER_EVENT', cooldownSeconds: 0, maxAwardsPerActor: 0, maxAwardsPerDay: 0,
                effectKind: rule.effectKind ?? 'AWARD', enabled: true,
            })),
            annotations: options.bindingAnnotations ?? {},
        },
    };
}

export async function seedGamification(request: APIRequestContext, metamodelId: string, models: { definitions: Json; bindings: Json }): Promise<void> {
    const response = await request.post(`/api/canonical-metamodels/${metamodelId}/gamification`, { data: models });
    if (!response.ok()) throw new Error(`POST gamification ${metamodelId} -> ${response.status()} ${await response.text()}`);
}

/** Concede un evento verificado real al motor de gamificación (API real). */
export async function postEvent(request: APIRequestContext, metamodelId: string, event: { id: string; actorId: string; eventTypeId?: string }): Promise<Json> {
    const response = await request.post(`/api/canonical-metamodels/${metamodelId}/gamification/runtime/events`, {
        data: {
            id: event.id, eventTypeId: event.eventTypeId ?? EVIDENCE_EVENT, eventTypeVersion: '1.0', occurredAt: new Date().toISOString(),
            source: { component: 'e2e', module: 'gamification', instanceId: '', version: '1.0' },
            context: { kind: 'GAMIFICATION', workspaceId: '', projectId: '', metamodelId, modelId: '', editorId: '', sessionId: '' },
            actor: { id: event.actorId, kind: 'USER', displayName: event.actorId },
            transactionId: `transaction:${event.id}`, correlationId: `correlation:${event.id}`, payload: {}, metadata: {},
        },
    });
    if (!response.ok()) throw new Error(`POST evento ${event.id} -> ${response.status()} ${await response.text()}`);
    return (await response.json()) as Json;
}

export async function getRuntime(request: APIRequestContext, metamodelId: string): Promise<Json> {
    const response = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification/runtime`);
    if (!response.ok()) throw new Error(`GET runtime ${metamodelId} -> ${response.status()}`);
    return (await response.json()) as Json;
}

export async function getFeedback(request: APIRequestContext, metamodelId: string, actorId = ''): Promise<Json[]> {
    const response = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification/runtime/feedback?actorId=${encodeURIComponent(actorId)}&limit=100`);
    if (!response.ok()) throw new Error(`GET feedback ${metamodelId} -> ${response.status()}`);
    return (await response.json()) as Json[];
}

export async function getGamification(request: APIRequestContext, metamodelId: string): Promise<Json> {
    const response = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification`);
    if (!response.ok()) throw new Error(`GET gamification ${metamodelId} -> ${response.status()}`);
    return (await response.json()) as Json;
}

/** Rutas relativas (dentro del almacenamiento del backend real) de los ficheros que el backend escribe para un metamodelo. */
export const files = (metamodelId: string) => ({
    definitions: `data/gamification/${metamodelId}.gamification-definitions.json`,
    bindings: `data/gamification/${metamodelId}.gamification-bindings.json`,
    ledger: `data/gamification/${metamodelId}.gamification-ledger.json`,
    diagram: `data/gamification/${metamodelId}.gamification-diagram.json`,
});
export const catalogExists = (metamodelId: string): boolean => exists(files(metamodelId).definitions) || exists(files(metamodelId).bindings);
export const readDefinitions = (metamodelId: string): Json => readJson<Json>(files(metamodelId).definitions);
export const readBindings = (metamodelId: string): Json => readJson<Json>(files(metamodelId).bindings);
/** Los JSON omiten las cadenas vacías: se lee con valor por defecto. */
export const text = (value: unknown): string => (typeof value === 'string' ? value : '');

/** Registra las peticiones de escritura (no GET) que el navegador envía al catálogo de gamificación, sin interceptarlas. */
export function watchWrites(page: Page): { saves: () => string[]; checks: () => string[] } {
    const log: string[] = [];
    page.on('request', (request) => {
        if (request.method() === 'GET') return;
        const path = new URL(request.url()).pathname;
        if (/\/gamification(\/|$)/.test(path)) log.push(`${request.method()} ${path}`);
    });
    return {
        saves: () => log.filter((entry) => !/\/check$/.test(entry)),
        checks: () => log.filter((entry) => /\/check$/.test(entry)),
    };
}

export async function openGamification(page: Page, metamodelId: string): Promise<void> {
    const loaded = page.waitForResponse((response) => response.request().method() === 'GET'
        && response.url().includes(`/canonical-metamodels/${encodeURIComponent(metamodelId)}/gamification/runtime/feedback`));
    // Carga completa aunque ya estemos en la misma ruta de hash (un goto solo de hash no recarga la aplicación ni vuelve a pedir el catálogo).
    await page.goto('about:blank');
    await openApp(page, '/#/gamification');
    await page.getByRole('combobox', { name: /Active (metamodel|semantic context)|Metamodelo activo|Contexto sem[aá]ntico activo/i }).first().selectOption(metamodelId);
    await loaded;
    await expect(page.locator('.gamification-designer')).toBeVisible();
}

export function tab(page: Page, name: RegExp): Locator { return page.locator('.event-subtabs').getByRole('button', { name }); }

/** Abre un desplegable de comandos del diseñador (Calidad / Cambios) y pulsa un comando. */
export async function runCommand(page: Page, group: RegExp, command: RegExp): Promise<void> {
    await page.locator('.gamification-header').getByRole('button', { name: group }).click();
    await page.getByRole('group', { name: group }).getByRole('button', { name: command }).click();
}

export async function expectSaveDisabled(page: Page, disabled: boolean): Promise<void> {
    await page.locator('.gamification-header').getByRole('button', { name: RE.changes }).click();
    const save = page.getByRole('group', { name: RE.changes }).getByRole('button', { name: RE.save });
    if (disabled) await expect(save).toBeDisabled(); else await expect(save).toBeEnabled();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('group', { name: RE.changes })).toHaveCount(0);
}

/** Pulsa «Guardar gamificación» y espera a que el diseñador complete el guardado (confirmando la sobrescritura si se pide). */
export async function saveGamification(page: Page, trigger: () => Promise<void> = () => runCommand(page, RE.changes, RE.save)): Promise<void> {
    const diagramSaved = page.waitForResponse((response) => response.request().method() === 'PUT' && /\/gamification\/diagram$/.test(new URL(response.url()).pathname));
    await trigger();
    const confirm = page.getByRole('button', { name: RE.overwrite });
    const outcome = await Promise.race([
        diagramSaved.then(() => 'saved' as const),
        confirm.waitFor({ state: 'visible', timeout: 20_000 }).then(() => 'confirm' as const).catch(() => 'none' as const),
    ]);
    if (outcome === 'confirm') await confirm.click();
    const response = await diagramSaved;
    expect(response.ok()).toBeTruthy();
    await expect(page.locator(STATUS)).toContainText(RE.saved);
}

export async function dismissAlert(page: Page): Promise<void> {
    await page.locator(`${ALERT} .hydra-window-close`).click();
    await expect(page.locator(ALERT)).toHaveCount(0);
}

/** Nodo del lienzo identificado por el nombre visible del elemento. */
export function node(page: Page, name: string): Locator {
    return page.locator('.react-flow__node').filter({ has: page.locator('strong', { hasText: new RegExp(`^${escapeRegExp(name)}$`) }) });
}

/** Selecciona un nodo del lienzo (clic de usuario sobre el nodo). */
export async function selectNode(page: Page, name: string): Promise<void> {
    await node(page, name).dispatchEvent('click');
    await expect(page.locator('.gamification-visual-inspector')).toContainText(name);
}

/** Añade un elemento desde la paleta del lienzo y fija su nombre e identificador en el inspector. */
export async function addFromPalette(page: Page, kind: 'rule' | 'point' | 'level' | 'achievement' | 'challenge' | 'reward', name: string, id: string): Promise<void> {
    await page.locator(`.gamification-palette-item.kind-${kind}`).click();
    const prefix = `gamification-${kind}`;
    await page.locator(`#${prefix}-name`).fill(name);
    await page.locator(`#${prefix}-id`).fill(id);
    await expect(node(page, name)).toHaveCount(1);
}

/** Reparte los nodos del lienzo (Lienzo > Auto layout) y los encaja en la vista para que ningún puerto quede tapado. */
export async function arrangeCanvas(page: Page): Promise<void> {
    await page.locator('.hydra-diagram-toolbox').getByRole('button', { name: /^(Canvas|Lienzo)$/ }).click();
    await page.getByRole('group', { name: /^(Canvas|Lienzo)$/ }).getByRole('button', { name: /^(Auto layout|Distribuci[oó]n autom[aá]tica|Auto)/ }).click();
    await page.locator('.react-flow__controls-fitview').click();
    await new Promise((resolve) => setTimeout(resolve, 400));
}

/** Conecta arrastrando el ratón desde el puerto de salida del origen hasta el puerto de entrada del destino (herramienta de conexión activa). */
export async function connectByDrag(page: Page, sourceName: string, targetName: string): Promise<void> {
    const tool = page.locator('.gamification-palette-item.kind-connection');
    if ((await tool.getAttribute('aria-pressed')) !== 'true') await tool.click();
    await expect(tool).toHaveAttribute('aria-pressed', 'true');
    // Encaja todo el diagrama en la vista (el lienzo es estrecho entre paleta e inspector) para que ambos puertos queden visibles y sin tapar.
    await page.locator('.react-flow__controls-fitview').click();
    await new Promise((resolve) => setTimeout(resolve, 300));
    const canvas = await page.locator('.gamification-visual-canvas').boundingBox();
    const from = await node(page, sourceName).locator('.react-flow__handle[data-handleid="right-source"]').boundingBox();
    const to = await node(page, targetName).locator('.react-flow__handle[data-handleid="left-target"]').boundingBox();
    if (from === null || to === null) throw new Error('los puertos no tienen geometría visible');
    const start = { x: from.x + from.width / 2, y: from.y + from.height / 2 };
    const end = { x: to.x + to.width / 2, y: to.y + to.height / 2 };
    if (canvas === null) throw new Error('el lienzo no tiene geometría visible');
    for (const point of [start, end]) {
        if (point.x < canvas.x || point.x > canvas.x + canvas.width || point.y < canvas.y || point.y > canvas.y + canvas.height) throw new Error(`un puerto queda fuera del lienzo visible (${Math.round(point.x)},${Math.round(point.y)})`);
    }
    await page.mouse.move(start.x, start.y);
    await page.mouse.down();
    await page.mouse.move(start.x + 8, start.y - 8, { steps: 4 });
    await page.mouse.move(end.x, end.y, { steps: 18 });
    await page.mouse.up();
    // Al soltar, la herramienta vuelve a «seleccionar» tanto si la conexión se acepta como si se rechaza.
    await expect(tool).toHaveAttribute('aria-pressed', 'false');
}

/** Conecta dos elementos con la herramienta de conexión del lienzo: clic en el puerto de salida y luego en el puerto de entrada. */
export async function connect(page: Page, sourceName: string, targetName: string): Promise<void> {
    const tool = page.locator('.gamification-palette-item.kind-connection');
    if ((await tool.getAttribute('aria-pressed')) !== 'true') await tool.click();
    await expect(tool).toHaveAttribute('aria-pressed', 'true');
    await page.getByLabel(`Source connection point for ${sourceName}`, { exact: true }).dispatchEvent('click');
    await expect(node(page, sourceName).locator('.gamification-node-card')).toHaveClass(/connection-source-selected/);
    await page.getByLabel(`Target connection point for ${targetName}`, { exact: true }).dispatchEvent('click');
    await expect(tool).toHaveAttribute('aria-pressed', 'false');
}
