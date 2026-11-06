// Ayudantes de los specs E2E REALES del área «validation» (navegador real + backend real). Sin simulaciones de la API.
// Se siembra solo el metamodelo (y, en la jornada de reglas importadas, el documento de partida) con la API real; la acción de cada etapa la hace el usuario en la interfaz.
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { eventually, exists, listFiles, openApp, readJson, uid } from '../support';

export type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

export interface SeededMetamodel { id: string; personId: string; companyId: string; ageId: string; nameId: string; relationId: string }

const attribute = (id: string, name: string, type: string) => ({
    id, name, type: { kind: 'PRIMITIVE', targetId: type, externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 },
    ordered: true, unique: true, defaultValue: '', annotations: {},
});

/** Metamodelo real con Person(age, fullName), Company y la relación Company --employees--> Person. Person es la primera clase. */
export async function seedValidationMetamodel(request: APIRequestContext, label: string): Promise<SeededMetamodel> {
    const id = uid(label);
    const seeded: SeededMetamodel = { id, personId: `${id}-person`, companyId: `${id}-company`, ageId: `${id}-person-age`, nameId: `${id}-person-name`, relationId: `${id}-employees` };
    const response = await request.post('/api/canonical-metamodels', {
        data: {
            schemaVersion: '2.1', id, name: id, annotations: {}, enums: [], dataTypes: [],
            classes: [
                { id: seeded.personId, name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [attribute(seeded.ageId, 'age', 'INTEGER'), attribute(seeded.nameId, 'fullName', 'STRING')] },
                { id: seeded.companyId, name: 'Company', kind: 'CLASS', annotations: {}, operations: [], attributes: [] },
            ],
            relations: [{
                id: seeded.relationId, name: 'employees', sourceTypeId: seeded.companyId, targetTypeId: seeded.personId, kind: 'REFERENCE',
                sourceMultiplicity: { lowerBound: 0, upperBound: 1 }, targetMultiplicity: { lowerBound: 0, upperBound: null },
                oppositeRelationId: '', ordered: false, unique: true, annotations: {},
            }],
        },
    });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
    return seeded;
}

/** Fija el idioma de la interfaz en inglés (solo preferencia de idioma, sin tocar la API). */
export async function forceEnglish(page: Page): Promise<void> {
    await page.addInitScript(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
}

export const META_COMBO = /Active (metamodel|semantic context)|Metamodelo activo|Contexto sem[aá]ntico activo/i;
export const ALERT = '[data-hydra-error-context="validation designer"]';
export const STATUS = '.hydra-independent-workbench .status-message';

/**
 * El selector de contexto solo lista los metamodelos del proyecto activo del servidor, y otros specs dejan uno abierto:
 * se cierra por la API real (estado de partida, no es la etapa certificada) para que el metamodelo sembrado sea elegible.
 */
export async function closeActiveProject(page: Page): Promise<void> {
    await page.request.post('/api/projects/close').catch(() => undefined);
}

/** Abre «Validations» o «Quick fixes» y elige el metamodelo en el combobox de contexto. */
export async function openValidationDesigner(page: Page, metamodelId: string, route: 'validations' | 'quick-fixes' = 'validations'): Promise<void> {
    await closeActiveProject(page);
    await openApp(page, `/#/${route}`);
    await page.reload();
    await expect(page.getByRole('combobox', { name: META_COMBO }).first().locator(`option[value="${metamodelId}"]`)).toHaveCount(1);
    await page.getByRole('combobox', { name: META_COMBO }).first().selectOption(metamodelId);
    await expect(page.locator('.validation-designer')).toBeVisible();
    await expect(page.locator(STATUS)).toContainText('Validation definitions loaded');
}

/** Abre el módulo sin elegir metamodelo (para ver el estado de bloqueo). */
export async function openWithoutContext(page: Page, route: 'validations' | 'quick-fixes' = 'validations'): Promise<void> {
    await closeActiveProject(page);
    await openApp(page, `/#/${route}`);
}

export const confirmOverwriteIfAsked = async (page: Page): Promise<void> => {
    await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click({ timeout: 3_000 }).catch(() => undefined);
};

/** Pulsa «Save validations» de la barra (guarda expresiones, acciones y validaciones). */
export async function saveFromToolbar(page: Page): Promise<void> {
    await page.locator('.validation-save-button').click();
    await confirmOverwriteIfAsked(page);
}

export async function expectSavedMessage(page: Page): Promise<void> {
    await expect(page.locator(STATUS)).toContainText(/Validations saved\./);
}

// --- Campos del editor de reglas / quick fixes ---------------------------------------------------------------------
export const field = (page: Page, label: string): Locator => page.getByLabel(label, { exact: true });
export const select = (page: Page, label: string): Locator => page.getByRole('combobox', { name: label, exact: true });
export const nextStep = (page: Page): Locator => page.locator('.hydra-guided-workflow-actions').getByRole('button', { name: 'Next step' });
export const finishButton = (page: Page, label: string): Locator => page.locator('.hydra-guided-workflow-actions').getByRole('button', { name: label });
export const modeSwitch = (page: Page, label: string): Locator => page.locator('.hydra-creation-mode-switch').getByRole('button', { name: label, exact: true });

// --- Oráculo: API real y disco del backend -----------------------------------------------------------------------
const base = (id: string): string => `/api/canonical-metamodels/${encodeURIComponent(id)}`;
export async function getJson(request: APIRequestContext, url: string): Promise<{ status: number; body: Json | null }> {
    const response = await request.get(url);
    return { status: response.status(), body: response.ok() ? (await response.json()) as Json : null };
}
export const getValidations = async (request: APIRequestContext, id: string): Promise<Json> => (await getJson(request, `${base(id)}/validations`)).body ?? {};
export const getExpressions = async (request: APIRequestContext, id: string): Promise<Json> => (await getJson(request, `${base(id)}/expressions`)).body ?? {};
export const getActions = async (request: APIRequestContext, id: string): Promise<Json> => (await getJson(request, `${base(id)}/actions`)).body ?? {};
export const validationsStatus = async (request: APIRequestContext, id: string): Promise<number> => (await getJson(request, `${base(id)}/validations`)).status;

type Kind = 'validations' | 'expressions' | 'actions';
/** Nombre relativo del fichero que el backend escribió para este metamodelo (o undefined si no existe). */
export function diskFile(id: string, kind: Kind): string | undefined {
    return listFiles('data/validations').find((name) => name.includes(id) && name.endsWith(`.${kind}.json`));
}
export const diskDoc = (id: string, kind: Kind): Json | undefined => {
    const file = diskFile(id, kind);
    return file === undefined ? undefined : readJson<Json>(`data/validations/${file}`);
};
export const anyDiskDocument = (id: string): boolean => (['validations', 'expressions', 'actions'] as const).some((kind) => diskFile(id, kind) !== undefined) || exists(`data/validations/${id}.validations.json`);

/** Espera a que la API devuelva una regla con ese código y la devuelve. */
export async function waitForRule(request: APIRequestContext, id: string, code: string): Promise<Json> {
    const model = await eventually(() => getValidations(request, id), (value) => (value.rules ?? []).some((rule: Json) => rule.code === code), 20_000);
    return (model.rules as Json[]).find((rule) => rule.code === code) as Json;
}

/** Recorre un árbol de expresión y devuelve el conjunto de tipos de nodo que contiene. */
export function nodeKinds(node: Json | undefined | null, into: Set<string> = new Set()): Set<string> {
    if (node === undefined || node === null || typeof node !== 'object') return into;
    if (typeof node.kind === 'string' && typeof node.nodeId === 'string') into.add(node.kind);
    for (const key of ['target', 'operand', 'left', 'right', 'source', 'expression', 'condition', 'whenTrue', 'whenFalse']) nodeKinds(node[key], into);
    for (const item of (node.arguments ?? []) as Json[]) nodeKinds(item, into);
    return into;
}

// --- Árbol de expresión (modo avanzado): navegación por la estructura de nodos -------------------------------------
export const advancedRoot = (page: Page): Locator => page.locator('.validation-expression-advanced-workspace > .validation-expression-node');
export const kindSelect = (node: Locator): Locator => node.locator(':scope > .validation-expression-node-header select');
export const nodeSelect = (node: Locator, idSuffix: string): Locator => node.locator(`:scope > [data-hydra-field-id$="${idSuffix}"] select`);
export const literalType = (node: Locator): Locator => node.locator(':scope > .validation-expression-inline-fields [data-hydra-field-id$="-literal-type"] select');
export const literalValueInput = (node: Locator): Locator => node.locator(':scope > .validation-expression-inline-fields [data-hydra-field-id$="-literal-value"] input');
/** Hijo directo (Target, Operand, Collection...) por posición. */
export const directChild = (node: Locator, index: number): Locator => node.locator(':scope > .validation-expression-child').nth(index).locator(':scope > .validation-expression-child-body > .validation-expression-node');
/** Hijo de una rama binaria (0 = Left, 1 = Right). */
export const branchChild = (node: Locator, index: number): Locator => node.locator(':scope > .validation-expression-branches > .validation-expression-child').nth(index).locator(':scope > .validation-expression-child-body > .validation-expression-node');

export async function useAdvancedCondition(page: Page): Promise<void> {
    await page.getByRole('group', { name: 'Condition builder mode' }).getByRole('button', { name: 'Advanced', exact: true }).click();
    await expect(advancedRoot(page)).toBeVisible();
}
