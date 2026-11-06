// Ayudantes de los specs E2E REALES del área «integration» (navegador real + backend real). Sin simulaciones de la API.
// Solo se siembra el metamodelo con la API real; el perfil de integración lo crea y edita el usuario en el diseñador.
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { eventually, listFiles, openApp, readJson, uid } from '../support';

export type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

export interface SeededMetamodel { id: string; personId: string }

export async function seedIntegrationMetamodel(request: APIRequestContext, label: string): Promise<SeededMetamodel> {
    const id = uid(label);
    const personId = `${id}-person`;
    const response = await request.post('/api/canonical-metamodels', {
        data: {
            schemaVersion: '2.1', id, name: id, annotations: {}, enums: [], dataTypes: [], relations: [],
            classes: [{ id: personId, name: 'Person', kind: 'CLASS', annotations: {}, operations: [], attributes: [{ id: `${id}-person-name`, name: 'fullName', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }] }],
        },
    });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
    return { id, personId };
}

export const META_COMBO = /Active (metamodel|semantic context)|Metamodelo activo|Contexto sem[aá]ntico activo/i;
export const ALERT = '[data-hydra-error-context="integration designer"]';
export const STATUS = '.hydra-independent-workbench .status-message';
export const profilesRoute = (metamodelId: string): string => `/api/semantic-contexts/${encodeURIComponent(metamodelId)}/integrations`;

/** Fija el idioma de la interfaz en inglés (solo preferencia de idioma) y abre «Integrations» con el metamodelo elegido. */
export async function openIntegrations(page: Page, metamodelId: string): Promise<void> {
    await page.addInitScript(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento */ } });
    // El selector de contexto solo lista los metamodelos del proyecto activo; se cierra el que otros specs hayan dejado abierto.
    await page.request.post('/api/projects/close').catch(() => undefined);
    await openApp(page, '/#/integrations');
    // La lista de contextos puede llegar vacía o perderse tras elegirla (recarga de la lista): se reintenta la carga completa hasta 3 veces.
    const combo = page.getByRole('combobox', { name: META_COMBO }).first();
    for (let attempt = 1; ; attempt += 1) {
        await page.reload();
        try {
            await expect(combo.locator(`option[value="${metamodelId}"]`)).toHaveCount(1, { timeout: 15_000 });
            await combo.selectOption(metamodelId);
            await expect(page.locator('.integration-designer-header')).toBeVisible({ timeout: 15_000 });
            break;
        } catch (error) {
            if (attempt >= 3) throw error;
            await page.request.post('/api/projects/close').catch(() => undefined);
        }
    }
    await expect(page.locator('.integration-dirty-state')).toBeVisible();
}

export const viewTab = (page: Page, name: 'Guided' | 'Definition' | 'Structure tree'): Locator => page.getByRole('navigation', { name: 'Integration designer views' }).getByRole('button', { name, exact: true });
/** Los comandos de la barra viven en menús desplegables («Quality», «Changes», «Generation»): se abre el grupo y se pulsa la orden. */
export async function runToolbar(page: Page, group: 'Quality' | 'Changes' | 'Generation', name: string): Promise<void> {
    await page.locator('.integration-designer-header').getByRole('button', { name: group, exact: true }).click();
    await page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name, exact: true }).click();
}
export const nextStep = (page: Page): Locator => page.locator('.hydra-guided-workflow-actions').getByRole('button', { name: 'Next step' });

/** «Save profile» de la barra: un perfil ya guardado pide confirmar la sobrescritura. */
export async function saveProfile(page: Page): Promise<void> {
    await runToolbar(page, 'Changes', 'Save profile');
    await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click({ timeout: 3_000 }).catch(() => undefined);
    await expect(page.locator(STATUS)).toContainText('Integration profile saved');
    await expect(page.locator('.integration-dirty-state')).toHaveText('Saved');
}

// --- Oráculo: API real y disco del backend -----------------------------------------------------------------------
export async function listProfiles(request: APIRequestContext, metamodelId: string): Promise<Json[]> {
    const response = await request.get(profilesRoute(metamodelId));
    return response.ok() ? (await response.json()) as Json[] : [];
}
export async function waitForProfile(request: APIRequestContext, metamodelId: string): Promise<Json> {
    const found = await eventually(async () => {
        const first = (await listProfiles(request, metamodelId))[0];
        if (first === undefined) return null;
        const loaded = await request.get(`${profilesRoute(metamodelId)}/${encodeURIComponent(first.id as string)}`);
        return loaded.ok() ? (await loaded.json()) as Json : null;
    }, (value) => value !== null, 20_000);
    return found as Json;
}
export async function getProfile(request: APIRequestContext, metamodelId: string, profileId: string): Promise<Json> {
    const response = await request.get(`${profilesRoute(metamodelId)}/${encodeURIComponent(profileId)}`);
    if (!response.ok()) throw new Error(`GET perfil ${profileId} -> ${response.status()}`);
    return (await response.json()) as Json;
}
export function profileOnDisk(profileId: string): Json | undefined {
    const file = listFiles('data/integrations').find((name) => name.endsWith('.integration.json') && name.includes(profileId));
    return file === undefined ? undefined : readJson<Json>(`data/integrations/${file}`);
}
export const anyProfileOnDisk = (metamodelId: string): boolean => listFiles('data/integrations').some((name) => name.endsWith('.integration.json') && name.includes(metamodelId));

/** Tecnología de la que el catálogo real dice que tiene flujo implementado (no «solo declarada») y no es proyección pura. */
export async function workingTechnology(request: APIRequestContext, metamodelId: string): Promise<string | undefined> {
    const response = await request.get(`${profilesRoute(metamodelId)}/technologies/workflows`);
    if (!response.ok()) return undefined;
    const workflows = (await response.json()) as Json[];
    return workflows.find((item) => item.maturity !== 'DECLARED_ONLY' && item.technology !== 'PLANTUML')?.technology as string | undefined;
}

/**
 * En la vista «Definition», marca como no obligatorios todos los documentos de entrada salvo el primero (el metamodelo). El servidor rechaza
 * el perfil si un documento obligatorio no existe (error de entrada obligatoria sin resolver) y aquí solo existe el metamodelo sembrado.
 */
export async function makeInputsOptional(page: Page): Promise<void> {
    const boxes = page.locator('input[id^="integration-artifact-required-"]');
    const total = await boxes.count();
    for (let index = 1; index < total; index += 1) await boxes.nth(index).setChecked(false);
}

/**
 * Pulsa «Save profile» sin esperar el mensaje de éxito de la interfaz. El servidor guarda el perfil, pero la interfaz lanza «Integration profile
 * collections are incomplete» al leer la respuesta cuando el perfil no tiene capacidades requeridas (el servidor omite las colecciones vacías):
 * defecto de producción. El resultado se contrasta con el servidor.
 */
export async function clickSaveProfile(page: Page): Promise<void> {
    await runToolbar(page, 'Changes', 'Save profile');
    await page.getByRole('button', { name: /Overwrite saved element|Sobrescribir/i }).click({ timeout: 3_000 }).catch(() => undefined);
}

/**
 * Precondición común (no es la etapa certificada): el usuario crea y guarda por la interfaz un perfil con un mapeo.
 */
export async function createSavedProfile(page: Page, request: APIRequestContext, mm: SeededMetamodel): Promise<Json> {
    await openIntegrations(page, mm.id);
    await viewTab(page, 'Definition').click();
    await makeInputsOptional(page);
    await page.getByRole('button', { name: '+ Mapping', exact: true }).click();
    await page.locator('#integration-mapping-id-0').fill('person-mapping');
    await page.locator('#integration-mapping-document-0').fill(mm.id);
    await page.locator('#integration-mapping-element-0').fill(mm.personId);
    await page.locator('#integration-host-qualified-0').fill('people.Person');
    await saveProfile(page);
    // El guardado se hizo en la vista «Definition»: se vuelve explícitamente al asistente (vista «Guided») para los tests que lo usan.
    await viewTab(page, 'Guided').click();
    await expect(page.locator('.hydra-guided-workflow')).toBeVisible();
    return waitForProfile(request, mm.id);
}
