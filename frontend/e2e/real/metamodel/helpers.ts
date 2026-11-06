// Ayudantes compartidos de los specs E2E REALES del área «metamodelo». Sin simulaciones: todo lo que se afirma sale del
// almacenamiento real del backend (data/metamodels/...) o de GET a la API real.
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { apiGet, eventually, openApp, readJson } from '../support';

export interface TypeRef { kind: string; targetId: string; externalName: string }
export interface Bounds { lowerBound: number; upperBound: number | null }
export interface PersistedAttribute { id: string; name: string; type: TypeRef; multiplicity: Bounds; ordered: boolean; unique: boolean; defaultValue: string }
export interface PersistedParameter { id: string; name: string; type: TypeRef; multiplicity: Bounds }
export interface PersistedOperation { id: string; name: string; returnType: TypeRef | null; behaviorKind: string; parameters: PersistedParameter[] }
export interface PersistedClass { id: string; name: string; kind: string; attributes: PersistedAttribute[]; operations: PersistedOperation[] }
export interface PersistedEnum { id: string; name: string; literals: Array<{ id: string; name: string; value: number }> }
export interface PersistedDataType { id: string; name: string }
export interface PersistedRelation { id: string; name: string; sourceTypeId: string; targetTypeId: string; kind: string; targetMultiplicity: Bounds | null; oppositeRelationId: string; ordered: boolean; unique: boolean }
export interface PersistedMetamodel { schemaVersion: string; id: string; name: string; classes: PersistedClass[]; enums: PersistedEnum[]; dataTypes: PersistedDataType[]; relations: PersistedRelation[] }
export interface PersistedDiagram { metamodelId: string; nodes: Array<{ elementId: string }>; edges: Array<{ relationId: string; sourceHandle: string; targetHandle: string }> }
export interface MetamodelSummary { id: string; name: string; schemaVersion: string }

/** Orden fijo de los botones de la paleta (independiente del idioma). */
export const ELEMENT = { CLASS: 0, ABSTRACT: 1, INTERFACE: 2, ENUM: 3, DATA_TYPE: 4 } as const;
export const RELATION = { REFERENCE: 0, AGGREGATION: 1, CONTAINMENT: 2, DEPENDENCY: 3, INHERITANCE: 4, IMPLEMENTATION: 5 } as const;

/** Abre el diseñador de metamodelos con la interfaz en inglés (idioma guardado) y espera a que esté montado. */
export async function openMetamodelDesigner(page: Page): Promise<void> {
    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento: queda el inglés por defecto */ } });
    await openApp(page, '/#/metamodel');
    await page.reload();
    await expect(page.locator('.metamodel-visual-designer')).toBeVisible();
    await expect(page.locator('.metamodel-structure-mode-strip')).toBeVisible();
}

/** Pasa del asistente guiado al editor visual/manual (segundo botón del selector de modo). */
export async function switchToVisualMode(page: Page): Promise<void> {
    await page.locator('.metamodel-structure-mode-strip').getByRole('button').nth(1).click();
    await expect(page.locator('.metamodel-palette')).toBeVisible();
    await expect(page.locator('.metamodel-property-panel')).toBeVisible();
}

export function propertyPanel(page: Page): Locator { return page.locator('.metamodel-property-panel'); }
export function field(page: Page, label: string): Locator { return propertyPanel(page).getByLabel(label, { exact: true }); }

export function elementButton(page: Page, index: number): Locator {
    return page.locator('.metamodel-palette-section').nth(0).locator('.metamodel-palette-button').nth(index);
}
export function relationButton(page: Page, index: number): Locator {
    return page.locator('.metamodel-palette-section').nth(1).locator('.metamodel-palette-button').nth(index);
}

/** Selecciona una clase de relación sin desactivarla si ya estaba activa (pulsar dos veces la apaga). */
export async function chooseRelationKind(page: Page, index: number): Promise<void> {
    const button = relationButton(page, index);
    const active = await button.evaluate(element => element.classList.contains('selected'));
    if (!active) await button.click();
    await expect(button).toHaveClass(/selected/);
}

/** Nodo del lienzo por el nombre que muestra su cabecera (clase, enumerado o tipo de datos). */
export function canvasNode(page: Page, name: string): Locator {
    return page.locator('.react-flow__node').filter({ has: page.locator('header strong', { hasText: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) }) });
}

export function portOf(page: Page, nodeName: string, handleId: string): Locator {
    return canvasNode(page, nodeName).locator(`.react-flow__handle[data-handleid="${handleId}"]`);
}

/** Encaja el lienzo para que todos los nodos y puertos queden dentro de la ventana. */
export async function fitCanvas(page: Page): Promise<void> {
    await page.locator('.react-flow__controls-fitview').click();
    await eventually(async () => {
        const first = await page.locator('.react-flow__node').first().boundingBox();
        await new Promise(resolve => setTimeout(resolve, 120));
        const second = await page.locator('.react-flow__node').first().boundingBox();
        return JSON.stringify(first) === JSON.stringify(second);
    }, stable => stable, 5_000);
}

export async function selectCanvasNode(page: Page, name: string): Promise<void> {
    await canvasNode(page, name).locator('header').click();
}

/** Conecta con dos clics: puerto azul de origen y después puerto verde de destino. */
export async function connectByClicks(page: Page, sourceNode: string, sourceHandle: string, targetNode: string, targetHandle: string): Promise<void> {
    await portOf(page, sourceNode, sourceHandle).click();
    await portOf(page, targetNode, targetHandle).click();
}

/** Conecta arrastrando el ratón desde el puerto de origen hasta el de destino. */
export async function connectByDrag(page: Page, sourceNode: string, sourceHandle: string, targetNode: string, targetHandle: string): Promise<void> {
    const from = await portOf(page, sourceNode, sourceHandle).boundingBox();
    const to = await portOf(page, targetNode, targetHandle).boundingBox();
    if (from === null || to === null) throw new Error('los puertos no tienen geometría visible');
    const start = { x: from.x + from.width / 2, y: from.y + from.height / 2 };
    const end = { x: to.x + to.width / 2, y: to.y + to.height / 2 };
    await page.mouse.move(start.x, start.y);
    await page.mouse.move(start.x + 1, start.y + 1, { steps: 2 }); // el puntero debe estar sobre el puerto antes de pulsar
    await page.mouse.down();
    await page.mouse.move(start.x + 12, start.y - 12, { steps: 6 });
    await page.mouse.move(end.x - 6, end.y - 6, { steps: 25 });
    await page.mouse.move(end.x, end.y, { steps: 6 }); // varios eventos sobre el puerto de destino antes de soltar
    await page.mouse.move(end.x + 0.5, end.y + 0.5, { steps: 2 });
    await page.mouse.up();
}

/** Aviso de error accionable del diseñador (role=alert); se acota a su clase para no contar otras regiones en vivo. */
export function errorNotice(page: Page): Locator { return page.locator('.hydra-actionable-error'); }

export async function dismissError(page: Page): Promise<void> {
    await page.getByRole('button', { name: /Close error|Cerrar error/i }).first().click();
    await expect(errorNotice(page)).toHaveCount(0);
}

/**
 * Las órdenes de la barra del diseñador viven dentro de desplegables («Metamodel», «Interchange», «Layout», «Changes»):
 * hay que abrir el grupo antes de pulsar su botón. El desplegable se cierra solo al pulsar una orden.
 */
export async function openToolbarMenu(page: Page, group: 'Metamodel' | 'Interchange' | 'Layout' | 'Changes'): Promise<void> {
    await page.locator('.hydra-designer-command-bar').getByRole('button', { name: group, exact: true }).click();
}

/** Abre un grupo de la barra y pulsa una orden suya localizada por selector. */
export async function clickToolbarCommand(page: Page, group: 'Metamodel' | 'Interchange' | 'Layout' | 'Changes', selector: string): Promise<void> {
    await openToolbarMenu(page, group);
    await page.locator(selector).click();
}

/** Guarda desde la barra de órdenes. Un metamodelo ya guardado pide confirmar la sobrescritura de sí mismo. */
export async function saveFromToolbar(page: Page, savedName: string, alreadySaved: boolean): Promise<void> {
    await clickToolbarCommand(page, 'Changes', '.metamodel-save-button');
    if (alreadySaved) await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click();
    await expect(page.locator('.metamodel-success-message')).toContainText(`Saved ${savedName}`);
}

/** Intenta guardar y comprueba que la interfaz muestra el rechazo (sin esperar éxito). */
export async function saveExpectingRejection(page: Page, alreadySaved: boolean): Promise<void> {
    await clickToolbarCommand(page, 'Changes', '.metamodel-save-button');
    if (alreadySaved) await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click();
    await expect(errorNotice(page).first()).toBeVisible();
}

// --- Oráculo: almacenamiento real y API real del backend ---------------------------------------------------------

export async function listSavedMetamodels(request: APIRequestContext): Promise<MetamodelSummary[]> {
    return apiGet<MetamodelSummary[]>(request, '/api/canonical-metamodels');
}

export async function findSavedByName(request: APIRequestContext, name: string): Promise<MetamodelSummary> {
    const found = await eventually(async () => (await listSavedMetamodels(request)).find(item => item.name === name) ?? null, value => value !== null, 20_000);
    return found as MetamodelSummary;
}

export async function expectNotSaved(request: APIRequestContext, name: string): Promise<void> {
    expect((await listSavedMetamodels(request)).filter(item => item.name === name)).toHaveLength(0);
}

export function readPersistedMetamodel(id: string): PersistedMetamodel {
    return readJson<PersistedMetamodel>(`data/metamodels/${id}.metamodel.json`);
}

export function readPersistedDiagram(id: string): PersistedDiagram {
    return readJson<PersistedDiagram>(`data/metamodels/${id}.metamodel-diagram.json`);
}

export function classNamed(model: PersistedMetamodel, name: string): PersistedClass {
    const found = model.classes.find(item => item.name === name);
    if (found === undefined) throw new Error(`no hay clase ${name} en el metamodelo guardado: ${model.classes.map(item => item.name).join(', ')}`);
    return found;
}

/** Documento de metamodelo canónico mínimo y válido: una clase «Person» con un atributo de texto. */
export function metamodelDocument(id: string, name: string): Record<string, unknown> {
    return {
        schemaVersion: '2.1', id, name, annotations: {}, enums: [], dataTypes: [], relations: [],
        classes: [{ id: `${id}-class`, name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [{ id: `${id}-attribute`, name: 'fullName', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }] }]
    };
}

/** Siembra un metamodelo mínimo mediante la API real (paso previo que no es la etapa certificada). */
export async function seedMetamodel(request: APIRequestContext, id: string, name: string): Promise<void> {
    const response = await request.post('/api/canonical-metamodels', { data: metamodelDocument(id, name) });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
}
