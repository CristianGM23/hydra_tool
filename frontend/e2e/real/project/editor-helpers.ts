// Ayudantes de los specs E2E REALES del diseñador de definiciones de editor (área «project»). Sin simulaciones de la API:
// el metamodelo de partida se siembra con la API real y todo lo afirmado sale de GET a la API real o del disco del backend.
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { apiGet, listFiles, openApp, readJson, uid } from '../support';

export interface SeededMetamodel { id: string; name: string; personId: string; teamId: string; relationId: string }

const attribute = (id: string, name: string) => ({
    id, name, type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 },
    ordered: true, unique: true, defaultValue: '', annotations: {},
});

/** Siembra un metamodelo con dos clases concretas (Person, Team) y la relación Team --members--> Person. */
export async function seedEditorMetamodel(request: APIRequestContext, label: string): Promise<SeededMetamodel> {
    const id = uid(label);
    const personId = `${id}-person`;
    const teamId = `${id}-team`;
    const relationId = `${id}-members`;
    const response = await request.post('/api/canonical-metamodels', {
        data: {
            schemaVersion: '2.1', id, name: id, annotations: {}, enums: [], dataTypes: [],
            classes: [
                { id: personId, name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [attribute(`${id}-person-name`, 'fullName')] },
                { id: teamId, name: 'Team', kind: 'CLASS', annotations: {}, operations: [], attributes: [attribute(`${id}-team-title`, 'title')] },
            ],
            relations: [{
                id: relationId, name: 'members', sourceTypeId: teamId, targetTypeId: personId, kind: 'REFERENCE',
                sourceMultiplicity: { lowerBound: 0, upperBound: 1 }, targetMultiplicity: { lowerBound: 0, upperBound: null },
                oppositeRelationId: '', ordered: false, unique: true, annotations: {},
            }],
        },
    });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
    return { id, name: id, personId, teamId, relationId };
}

/** Metamodelo sin ninguna clase concreta (solo una clase abstracta): sirve para el rechazo de «+ Form». */
export async function seedAbstractOnlyMetamodel(request: APIRequestContext, label: string): Promise<string> {
    const id = uid(label);
    const response = await request.post('/api/canonical-metamodels', {
        data: { schemaVersion: '2.1', id, name: id, annotations: {}, enums: [], dataTypes: [], relations: [], classes: [{ id: `${id}-shape`, name: 'Shape', kind: 'ABSTRACT_CLASS', annotations: {}, operations: [], attributes: [] }] },
    });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
    return id;
}

/** Abre «Model editor definitions» y elige el metamodelo sembrado en el selector de contexto de la barra superior. */
export async function openEditors(page: Page, metamodelId: string): Promise<void> {
    await openApp(page, '/#/editors');
    const selector = page.locator('select[aria-label="Active semantic context"], select[aria-label="Active metamodel"]');
    await expect(selector).toBeVisible();
    await selector.selectOption(metamodelId);
    await expect(page.locator('.editor-definition-designer')).toBeVisible();
    await expect(page.locator('.editor-definition-tree')).toBeVisible();
}

/** Ejecuta una orden de la barra del diseñador (grupos «Changes» o «Generation», que son desplegables). */
export async function runDesignerCommand(page: Page, group: 'Changes' | 'Generation', item: RegExp): Promise<void> {
    await page.getByRole('button', { name: group, exact: true }).click();
    await page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name: item }).click();
}

/** Guarda un editor que ya existe en el backend: HYDRA pide confirmar la sobrescritura de sí mismo. */
export async function saveExistingEditor(page: Page): Promise<void> {
    await runDesignerCommand(page, 'Changes', SAVE_EDITOR);
    await page.getByRole('button', { name: 'Overwrite saved element' }).click();
}

export const SAVE_EDITOR =/^Save editor$/;
export const COMPILE_RUNTIME = /^Compile runtime$/;
export const SAVED_MESSAGE = 'Model editor definition saved';

export const editorRoute = (metamodelId: string, editorId: string): string => `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/editors/${encodeURIComponent(editorId)}`;

export interface EditorDoc {
    id: string; name: string; metamodelId: string;
    representations: Array<{
        id: string; name: string; kind: string; rootClassIds: string[];
        nodeMappings: Array<{ id: string; targetClassId: string; representationName: string; shape: string; conditionalStyles: Array<{ id: string; name: string; conditionExpressionId: string; styles: Record<string, string> }> }>;
        edgeMappings: Array<{ id: string; relationId: string; sourceClassId: string; targetClassId: string }>;
        toolIds: string[];
    }>;
    palette: { groups: Array<{ id: string; name: string; toolIds: string[] }>; tools: Array<{ id: string; kind: string; label: string; representationIds: string[]; targetClassId?: string; relationId?: string; edgeMappingId?: string; validationMode?: string }> };
    forms: Array<{ id: string; name: string; targetClassId: string }>;
}

export const loadEditorDoc = (request: APIRequestContext, metamodelId: string, editorId: string): Promise<EditorDoc> => apiGet<EditorDoc>(request, editorRoute(metamodelId, editorId));

/** Documento del editor que el backend dejó en disco (si el almacén lo nombra por su id); undefined si no se encuentra. */
export function editorOnDisk(editorId: string): EditorDoc | undefined {
    const file = listFiles('data/editors').find(name => name.endsWith('.editor.json') && name.includes(editorId));
    return file === undefined ? undefined : readJson<EditorDoc>(`data/editors/${file}`);
}

/**
 * Precondición común (no es la etapa certificada): crea y guarda por la interfaz un editor mínimo con un mapeo de nodo
 * para Person y el nombre indicado. Devuelve su id (el diseñador usa «<metamodelo>-editor» para el primero).
 */
export async function createAndSaveMinimalEditor(page: Page, request: APIRequestContext, metamodel: SeededMetamodel, editorName: string): Promise<string> {
    const editorId = `${metamodel.id}-editor`;
    await openEditors(page, metamodel.id);
    await page.locator('.editor-definition-designer').getByRole('button', { name: 'All details', exact: true }).click();
    // El botón «+» del árbol queda tapado por la barra «+ Representation/+ Form» con el viewport por defecto (el clic real se intercepta): se dispara su clic directamente.
    await page.getByRole('button', { name: 'Add Person node mapping' }).dispatchEvent('click');
    await expect(page.locator('#node-mapping-class')).toHaveValue(metamodel.personId);
    await page.locator('.editor-document-disclosure').getByRole('button', { name: 'Show', exact: true }).click();
    await page.locator('#editor-definition-name').fill(editorName);
    await runDesignerCommand(page, 'Changes', SAVE_EDITOR);
    await expect(page.locator('.status-message')).toContainText(SAVED_MESSAGE);
    const saved = await loadEditorDoc(request, metamodel.id, editorId);
    expect(saved.name).toBe(editorName);
    return editorId;
}
