// Ayudantes de los specs E2E REALES del área de transformaciones (navegador real + backend real). Sin simulaciones de la API.
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { apiGet, listFiles, openApp, readJson } from '../support';

export interface SeedAttribute { id: string; name: string; type?: string }
export interface SeedClass { id: string; name: string; attrs: SeedAttribute[] }

/** Siembra un metamodelo canónico por la API real (paso previo, no es la etapa certificada). */
export async function seedMetamodel(request: APIRequestContext, spec: { id: string; name: string; classes: SeedClass[] }): Promise<void> {
    const body = {
        schemaVersion: '2.1', id: spec.id, name: spec.name, enums: [], dataTypes: [], relations: [], annotations: {},
        classes: spec.classes.map((c) => ({
            id: c.id, name: c.name, kind: 'CLASS', annotations: {}, operations: [],
            attributes: c.attrs.map((a) => ({
                id: a.id, name: a.name, type: { kind: 'PRIMITIVE', targetId: a.type ?? 'STRING', externalName: '' },
                multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {},
            })),
        })),
    };
    const response = await request.post('/api/canonical-metamodels', { data: body });
    if (!response.ok()) throw new Error(`POST /api/canonical-metamodels -> ${response.status()} ${await response.text()}`);
}

interface TransformationSummaryDto { id: string; name: string }

/** Campos del documento de transformación guardado (API real y disco) que comprueban los specs. */
export interface TransformationBindingDoc {
    kind: string; targetFeatureId: string; sourceFeatureId?: string; targetAlias?: string; expression?: unknown; valuePolicy?: Record<string, unknown>;
}
export interface TransformationRuleDoc {
    direction: string; sourceTypeId: string; correspondenceId: string; targets: { typeId: string; alias: string }[];
    bindings: TransformationBindingDoc[]; guard?: { declaredReturnType: { kind: string } } | null;
}
export interface TransformationGenerationRuleDoc {
    contextTypeId: string; outputPathTemplate: string; contentTemplate: string; overwritePolicy: string; enabled: boolean; guard?: unknown;
}
export interface TransformationDoc {
    name: string; kind: string; directionality: string; sources: { metamodelId: string }[]; targets: { metamodelId: string }[];
    rules: TransformationRuleDoc[]; generationRules: TransformationGenerationRuleDoc[];
    synchronizationPolicy: { conflictPolicy: string; propagateDeletions: boolean; requireTraceForUpdates: boolean };
    tracePolicy: { enabled: boolean };
}

/** Sesión de sincronización gestionada guardada por el backend real (API y disco): solo lo que comprueban los specs. */
export interface SyncModelElement { classId: string; attributes: Record<string, unknown> }
export interface SyncModel { elements: SyncModelElement[] }
export interface SyncSessionDoc { revision: number; sourceBaseline: SyncModel; targetBaseline: SyncModel }

/** Busca por nombre una transformación guardada por el backend real (oráculo por API). */
export async function findTransformationByName(request: APIRequestContext, name: string): Promise<TransformationSummaryDto | undefined> {
    const all = await apiGet<TransformationSummaryDto[]>(request, '/api/transformations');
    return all.find((item) => item.name === name);
}

/** Documento de transformación leído por la API real. */
export async function loadTransformationDoc(request: APIRequestContext, id: string): Promise<TransformationDoc> {
    const loaded = await apiGet<{ transformation: TransformationDoc }>(request, `/api/transformations/${encodeURIComponent(id)}`);
    return loaded.transformation;
}

/** Documento de transformación leído directamente del disco del backend (oráculo independiente de la API). */
export function readTransformationFromDisk(id: string): TransformationDoc {
    const file = listFiles('data/transformations').find((name) => name.includes(id));
    if (file === undefined) throw new Error('no hay fichero de transformación en disco para ' + id);
    return readJson<TransformationDoc>('data/transformations/' + file);
}

export function transformationExistsOnDisk(id: string): boolean {
    return listFiles('data/transformations').some((name) => name.includes(id));
}

export async function openDesigner(page: Page): Promise<void> {
    await openApp(page, '/#/transformations');
    await expect(page.getByRole('heading', { name: /Model transformation designer|Diseñador de transformaciones/i }).first()).toBeVisible();
}

export async function switchToAdvanced(page: Page): Promise<void> {
    await page.getByRole('button', { name: /^(Advanced \/ manual|Avanzado \/ manual)$/ }).click();
}

export function combo(page: Page, en: string, es: string): Locator {
    return page.getByRole('combobox', { name: new RegExp(`^(${en}|${es})$`) });
}

export function checkbox(page: Page, en: string, es: string): Locator {
    return page.getByRole('checkbox', { name: new RegExp(`^(${en}|${es})$`) });
}

export function savedSelector(page: Page): Locator {
    return page.getByRole('combobox', { name: /Saved transformations|Transformaciones guardadas/ });
}

/** Panel de metamodelo (0 = entrada/izquierda, 1 = salida/derecha) del espacio de mapeo, o único panel del de generación. */
export function pane(page: Page, index: number): Locator {
    return page.locator('aside.transformation-metamodel-pane').nth(index);
}

/** Nodo de clase del árbol semántico (el título del botón es «Nombre · id»). */
export function treeClass(page: Page, paneIndex: number, className: string): Locator {
    return pane(page, paneIndex).locator(`button.hydra-ui-tree-main[title^="${className} · "]`);
}

/** Menú de la barra de comandos: 0 = Calidad (Comprobar), 1 = Cambios (Guardar/Eliminar). */
async function runCommand(page: Page, menuIndex: number, action: RegExp): Promise<void> {
    await page.locator('.hydra-designer-command-group-dropdown > button.hydra-designer-dropdown-trigger').nth(menuIndex).click();
    await page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name: action }).click();
}

export async function clickSave(page: Page): Promise<void> {
    await runCommand(page, 1, /^(Save transformation|Guardar transformación)$/);
}

export async function clickCheck(page: Page): Promise<void> {
    await runCommand(page, 0, /^(Check transformation|Comprobar transformación)$/);
}

export function runtimeMessage(page: Page): Locator {
    return page.locator('.transformation-runtime-message');
}

export const SAVED_MESSAGE = /Transformation saved\.|Transformación guardada\./;
export const BLOCKED_MESSAGE = /Resolve the blocking|Resuelve los diagnósticos bloqueantes/;
