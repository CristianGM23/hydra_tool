// Ayudantes de los specs E2E REALES del área «events» (navegador real + backend real, sin simulaciones de la API).
// Siembra solo con la API real (metamodelo, eventos); las acciones de cada etapa las hace el usuario en la interfaz.
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { exists, openApp, readJson } from '../support';

export type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

export const RE = {
    changes: /^(Changes|Cambios)$/,
    quality: /^(Quality|Calidad)$/,
    saveEvents: /^(Save events|Guardar eventos)$/,
    saveFlows: /^(Save flows|Guardar flujos)$/,
    overwrite: /Overwrite saved element|Sobrescribir elemento guardado/,
    formTab: /^(Form|Formulario)$/,
    architectureTab: /^(Event architecture|Arquitectura de eventos)$/,
    sourceTab: /^(Source|Origen)$/,
    diagramTab: /^(Diagram|Diagrama)$/,
    connect: /^(Connect|Conectar)$/,
    saved: /^(Saved|Guardado)$/,
};

export const EVENTS_ERROR = '[data-hydra-error-context="event designer"]';
export const FLOWS_ERROR = '[data-hydra-error-context="event flow designer"]';
export const STATUS = '.hydra-independent-workbench .status-message';

export const files = (metamodelId: string) => ({
    definitions: `data/events/${metamodelId}.event-definitions.json`,
    bindings: `data/events/${metamodelId}.event-bindings.json`,
    diagram: `data/events/${metamodelId}.event-architecture-diagram.json`,
    flows: `data/event-flows/${metamodelId}.event-flows.json`,
    flowDiagram: `data/event-flows/${metamodelId}.event-flow-diagram.json`,
});
export const readDefinitions = (metamodelId: string): Json => readJson<Json>(files(metamodelId).definitions);
export const readBindings = (metamodelId: string): Json => readJson<Json>(files(metamodelId).bindings);
export const readFlows = (metamodelId: string): Json => readJson<Json>(files(metamodelId).flows);
export const eventsSaved = (metamodelId: string): boolean => exists(files(metamodelId).definitions) || exists(files(metamodelId).bindings);
/** Los JSON del backend omiten cadenas vacías, listas vacías y booleanos falsos: se lee con valor por defecto. */
export const text = (value: unknown): string => (typeof value === 'string' ? value : '');
export const list = <T = Json>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);

export function escapeRegExp(value: string): string { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

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

export interface FieldSeed { id: string; name: string; type?: string; required?: boolean }
export function eventType(namespace: string, id: string, name: string, fields: FieldSeed[] = []): Json {
    return {
        id, name, namespace, description: '', category: 'DOMAIN', version: '1.0',
        fields: fields.map((field) => ({ id: field.id, name: field.name, type: field.type ?? 'STRING', required: field.required ?? false, many: false, enumTypeId: '', objectTypeId: '', description: '' })),
        allowedSources: [], allowedSubjects: [], persistencePolicy: 'TRANSIENT', abstractType: false, deprecated: false, enabled: true, annotations: {},
    };
}

/** Siembra definiciones de eventos (y bindings vacíos) con la API real. */
export async function seedEvents(request: APIRequestContext, metamodelId: string, namespace: string, types: Json[]): Promise<void> {
    const definitionsId = `${metamodelId}-events`;
    const response = await request.post(`/api/canonical-metamodels/${metamodelId}/events`, {
        data: {
            definitions: { schemaVersion: '1.0', id: definitionsId, metamodelId, namespace, eventTypes: types, annotations: {} },
            bindings: { schemaVersion: '1.0', id: `${metamodelId}-event-bindings`, metamodelId, eventDefinitionModelId: definitionsId, expressionModelId: '', bindings: [], annotations: {} },
        },
    });
    if (!response.ok()) throw new Error(`POST eventos ${metamodelId} -> ${response.status()} ${await response.text()}`);
}

/** Siembra con la API real un modelo de expresiones (una condición booleana siempre cierta por id) del metamodelo sembrado con `seedMetamodel`. */
export async function seedConditionExpressions(request: APIRequestContext, metamodelId: string, expressionIds: string[]): Promise<void> {
    const response = await request.post(`/api/canonical-metamodels/${metamodelId}/expressions`, {
        data: {
            schemaVersion: '1.0', id: metamodelId, metamodelId, annotations: {},
            expressions: expressionIds.map((id) => ({
                id, name: `Condition ${id}`, description: '', contextClassId: 'class:Item', declaredReturnType: { kind: 'BOOLEAN' }, annotations: {},
                root: { kind: 'LITERAL', nodeId: `node:${id.replace(/[^A-Za-z0-9_-]/g, '-')}`, valueType: { kind: 'BOOLEAN' }, value: true },
            })),
        },
    });
    if (!response.ok()) throw new Error(`POST expresiones ${metamodelId} -> ${response.status()} ${await response.text()}`);
}

/** Siembra definiciones y enlaces de eventos (con modelo de expresiones ya sembrado) con la API real. */
export async function seedEventsWithBindings(request: APIRequestContext, metamodelId: string, namespace: string, types: Json[], bindings: Json[], expressionModelId = ''): Promise<void> {
    const definitionsId = `${metamodelId}-events`;
    const response = await request.post(`/api/canonical-metamodels/${metamodelId}/events`, {
        data: {
            definitions: { schemaVersion: '1.0', id: definitionsId, metamodelId, namespace, eventTypes: types, annotations: {} },
            bindings: { schemaVersion: '1.0', id: `${metamodelId}-event-bindings`, metamodelId, eventDefinitionModelId: definitionsId, expressionModelId, bindings, annotations: {} },
        },
    });
    if (!response.ok()) throw new Error(`POST eventos con enlaces ${metamodelId} -> ${response.status()} ${await response.text()}`);
}

const getOf = (page: Page, suffix: string, metamodelId: string) => page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/canonical-metamodels/${metamodelId}${suffix}`);

/** Abre el diseñador de eventos del metamodelo (interfaz en inglés) y espera a que cargue lo guardado. */
export async function openEvents(page: Page, metamodelId: string): Promise<void> {
    // Las respuestas se esperan antes de elegir el metamodelo; el reinicio de página hace que el diseñador se monte después.
    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    await openApp(page, '/#/events');
    await page.reload();
    const loaded = [getOf(page, '/events', metamodelId), getOf(page, '/events/diagram', metamodelId)];
    await page.getByRole('combobox', { name: /Active (metamodel|semantic context)|Metamodelo activo|Contexto sem[aá]ntico activo/i }).first().selectOption(metamodelId);
    await Promise.all(loaded);
    await expect(page.locator('.event-designer')).toBeVisible();
}

/** Abre el diseñador de flujos de eventos y espera a que cargue lo guardado. */
export async function openFlows(page: Page, metamodelId: string): Promise<void> {
    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    await openApp(page, '/#/event-flows');
    await page.reload();
    const loaded = [getOf(page, '/event-flows/optional', metamodelId), getOf(page, '/event-flows/diagram', metamodelId), getOf(page, '/event-flows/runtime/state', metamodelId)];
    await page.getByRole('combobox', { name: /Active (metamodel|semantic context)|Metamodelo activo|Contexto sem[aá]ntico activo/i }).first().selectOption(metamodelId);
    await Promise.all(loaded);
    await expect(page.locator('.event-flow-designer')).toBeVisible();
}

export function tab(page: Page, name: RegExp): Locator { return page.locator('.event-subtabs').getByRole('button', { name }); }

/** Abre un desplegable de comandos (Cambios / Calidad) de la barra del diseñador y pulsa un comando. */
export async function runCommand(page: Page, header: string, group: RegExp, command: RegExp): Promise<void> {
    await page.locator(header).getByRole('button', { name: group }).click();
    await page.getByRole('group', { name: group }).getByRole('button', { name: command }).click();
}

export async function expectSaveEnabled(page: Page, header: string, saveLabel: RegExp, enabled: boolean): Promise<void> {
    await page.locator(header).getByRole('button', { name: RE.changes }).click();
    const save = page.getByRole('group', { name: RE.changes }).getByRole('button', { name: saveLabel });
    if (enabled) await expect(save).toBeEnabled(); else await expect(save).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('group', { name: RE.changes })).toHaveCount(0);
}

/** Pulsa «Guardar eventos» y espera a que el diseñador termine de guardar (confirmando la sobrescritura si se pide). */
export async function saveEvents(page: Page): Promise<void> {
    const diagramSaved = page.waitForResponse((response) => response.request().method() === 'PUT' && /\/events\/diagram$/.test(new URL(response.url()).pathname));
    await runCommand(page, '.event-designer-header', RE.changes, RE.saveEvents);
    const confirm = page.getByRole('button', { name: RE.overwrite });
    const outcome = await Promise.race([
        diagramSaved.then(() => 'saved' as const),
        confirm.waitFor({ state: 'visible', timeout: 20_000 }).then(() => 'confirm' as const).catch(() => 'none' as const),
    ]);
    if (outcome === 'confirm') await confirm.click();
    const response = await diagramSaved;
    expect(response.ok()).toBeTruthy();
    await expect(page.locator('.event-dirty-state')).toHaveText(RE.saved);
    await expect(page.locator(STATUS)).toContainText(/Event definitions, bindings and architecture diagram saved/);
}

/** Pulsa «Guardar eventos» esperando un rechazo: devuelve el estado HTTP de la escritura de contratos si llegó al servidor, o 0 si lo frenó el diseñador. */
export async function saveEventsExpectingRejection(page: Page): Promise<number> {
    let status = 0;
    const onResponse = (response: import('@playwright/test').Response): void => {
        const path = new URL(response.url()).pathname;
        if (['POST', 'PUT'].includes(response.request().method()) && /\/events$/.test(path)) status = response.status();
    };
    page.on('response', onResponse);
    try {
        await runCommand(page, '.event-designer-header', RE.changes, RE.saveEvents);
        const confirm = page.getByRole('button', { name: RE.overwrite });
        if (await confirm.isVisible().catch(() => false)) await confirm.click();
        await expect(page.locator(EVENTS_ERROR)).toBeVisible();
    } finally { page.off('response', onResponse); }
    return status;
}

export async function saveFlows(page: Page): Promise<void> {
    const diagramSaved = page.waitForResponse((response) => response.request().method() === 'PUT' && /\/event-flows\/diagram$/.test(new URL(response.url()).pathname));
    await runCommand(page, '.event-flow-toolbar', RE.changes, RE.saveFlows);
    const confirm = page.getByRole('button', { name: RE.overwrite });
    const outcome = await Promise.race([
        diagramSaved.then(() => 'saved' as const),
        confirm.waitFor({ state: 'visible', timeout: 20_000 }).then(() => 'confirm' as const).catch(() => 'none' as const),
    ]);
    if (outcome === 'confirm') await confirm.click();
    const response = await diagramSaved;
    expect(response.ok()).toBeTruthy();
    await expect(page.locator(STATUS)).toContainText(/Event flows and diagram saved/);
}

export async function saveFlowsExpectingRejection(page: Page): Promise<number> {
    let status = 0;
    const onResponse = (response: import('@playwright/test').Response): void => {
        const path = new URL(response.url()).pathname;
        if (['POST', 'PUT'].includes(response.request().method()) && /\/event-flows$/.test(path)) status = response.status();
    };
    page.on('response', onResponse);
    try {
        await runCommand(page, '.event-flow-toolbar', RE.changes, RE.saveFlows);
        await expect(page.locator(FLOWS_ERROR)).toBeVisible();
    } finally { page.off('response', onResponse); }
    return status;
}

export async function dismissError(page: Page, context: string): Promise<void> {
    await page.locator(`${context} .hydra-window-close`).click();
    await expect(page.locator(context)).toHaveCount(0);
}

// --- Lienzo de arquitectura de eventos -------------------------------------------------------------------------------

export function eventNode(page: Page, label: string): Locator {
    return page.locator('.react-flow__node').filter({ has: page.locator('.event-architecture-node strong', { hasText: new RegExp(`^${escapeRegExp(label)}$`) }) });
}

/** Coloca un evento del catálogo en el lienzo: busca por nombre en la paleta y pulsa su botón. */
export async function placeFromPalette(page: Page, label: string): Promise<void> {
    const search = page.getByRole('textbox', { name: /Search event palette|Buscar en la paleta de eventos/ });
    await search.fill(label);
    const item = page.locator('.event-architecture-catalog-item').filter({ has: page.locator('strong', { hasText: new RegExp(`^${escapeRegExp(label)}$`) }) });
    await expect(item).toHaveCount(1);
    await item.click();
    await expect(eventNode(page, label)).toHaveCount(1);
    await clearField(search);
}

/**
 * Vacía un campo escrito por el usuario. No se usa `fill('')`: con valor vacío Playwright pulsa «Supr», y dentro del
 * lienzo de eventos esa tecla borra la selección del diagrama (atajo del editor).
 */
export async function clearField(field: Locator): Promise<void> {
    await field.evaluate((element) => {
        const input = element as HTMLInputElement | HTMLTextAreaElement;
        const prototype = input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(prototype, 'value')?.set?.call(input, '');
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await expect(field).toHaveValue('');
}

/** Reparte los nodos del lienzo (Lienzo > Auto layout) y los encaja en la vista para que ningún puerto quede tapado. */
export async function arrangeCanvas(page: Page): Promise<void> {
    await page.locator('.hydra-diagram-toolbox').getByRole('button', { name: /^(Canvas|Lienzo)$/ }).click();
    await page.getByRole('group', { name: /^(Canvas|Lienzo)$/ }).getByRole('button', { name: /^(Auto layout|Distribuci[oó]n autom[aá]tica|Auto)/ }).click();
    await page.locator('.react-flow__controls-fitview').click();
    await new Promise((resolve) => setTimeout(resolve, 400));
}

export function connectTool(page: Page): Locator { return page.locator('.hydra-diagram-pointer-modes').getByRole('button', { name: RE.connect }); }

export async function enterConnectMode(page: Page): Promise<void> {
    const tool = connectTool(page);
    if ((await tool.getAttribute('aria-pressed')) !== 'true') await tool.click();
    await expect(tool).toHaveAttribute('aria-pressed', 'true');
}

/** Conexión por dos clics: puerto de salida del origen y puerto de entrada del destino (con el modo Conectar activo). */
export async function connectByClicks(page: Page, source: string, target: string): Promise<void> {
    await enterConnectMode(page);
    await page.getByLabel(`Source connection point for ${source}`, { exact: true }).dispatchEvent('click');
    await expect(eventNode(page, source).locator('.event-architecture-node')).toHaveClass(/connection-source-selected/);
    await page.getByLabel(`Target connection point for ${target}`, { exact: true }).dispatchEvent('click');
    await expect(connectTool(page)).toHaveAttribute('aria-pressed', 'false');
}

/** Conexión arrastrando el ratón desde el puerto de salida del origen hasta el puerto de entrada del destino. */
export async function connectByDrag(page: Page, source: string, target: string): Promise<void> {
    await enterConnectMode(page);
    const from = await eventNode(page, source).locator('.react-flow__handle[data-handleid="right-source"]').boundingBox();
    const to = await eventNode(page, target).locator('.react-flow__handle[data-handleid="left-target"]').boundingBox();
    if (from === null || to === null) throw new Error('los puertos no tienen geometría visible');
    const start = { x: from.x + from.width / 2, y: from.y + from.height / 2 };
    const end = { x: to.x + to.width / 2, y: to.y + to.height / 2 };
    await page.mouse.move(start.x, start.y);
    await page.mouse.down();
    await page.mouse.move(start.x + 8, start.y - 8, { steps: 4 });
    await page.mouse.move(end.x, end.y, { steps: 18 });
    await page.mouse.up();
}

export const inspector = (page: Page): Locator => page.locator('.event-architecture-inspector');
