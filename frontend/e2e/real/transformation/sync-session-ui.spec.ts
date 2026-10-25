import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { apiGet, eventually, listFiles, readJson, uid } from '../support';
import { openDesigner, savedSelector, seedMetamodel, switchToAdvanced, type SyncModel, type SyncModelElement, type SyncSessionDoc } from './helpers';

// Sesiones de sincronización gestionadas desde la interfaz: el usuario crea la sesión, edita uno o ambos lados (textos de modelo
// del panel de sincronización) y sincroniza; el backend real avanza (o no) la revisión y guarda línea base y puntos de control.

interface Fixture { transformationId: string; srcMetamodelId: string; tgtMetamodelId: string; srcClass: string; tgtClass: string; srcName: string; tgtName: string; tgtNote: string }

const POLICY = { propagateDeletions: false, requireTraceForUpdates: true };
const valuePolicy = { collectionMode: 'PRESERVE', conversionMode: 'NONE', enumMapping: {}, errorPolicy: 'FAIL' };

/** Siembra por la API real dos metamodelos y una transformación bidireccional Person↔Contact (paso previo, no certificado). */
async function seedBidirectional(request: APIRequestContext, conflictPolicy: string): Promise<Fixture> {
    const srcId = uid('e2e-ssrc');
    const tgtId = uid('e2e-stgt');
    const fx: Fixture = {
        transformationId: uid('e2e-sync-tr'), srcMetamodelId: srcId, tgtMetamodelId: tgtId, srcClass: `${srcId}-person`, tgtClass: `${tgtId}-contact`,
        srcName: `${srcId}-person-name`, tgtName: `${tgtId}-contact-name`, tgtNote: `${tgtId}-contact-note`,
    };
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: fx.srcClass, name: 'Person', attrs: [{ id: fx.srcName, name: 'name' }] }] });
    await seedMetamodel(request, { id: tgtId, name: tgtId, classes: [{ id: fx.tgtClass, name: 'Contact', attrs: [{ id: fx.tgtName, name: 'name' }, { id: fx.tgtNote, name: 'note' }] }] });
    const endpoint = (id: string, role: string, metamodelId: string) => ({ id, role, metamodelId, metamodelFingerprint: '', alias: id, required: true, annotations: {} });
    const doc = {
        schemaVersion: '1.1', id: fx.transformationId, name: fx.transformationId, kind: 'M2M', directionality: 'BIDIRECTIONAL',
        synchronizationPolicy: { conflictPolicy, ...POLICY },
        sources: [endpoint('source', 'SOURCE', srcId)], targets: [endpoint('target', 'TARGET', tgtId)],
        rules: [
            { id: 'rule-forward', name: 'Person to Contact', enabled: true, sourceEndpointId: 'source', sourceTypeId: fx.srcClass, sourceAlias: 'person',
                targets: [{ id: 'tp-forward', endpointId: 'target', typeId: fx.tgtClass, alias: 'contact', annotations: {} }],
                bindings: [{ id: 'bind-forward', targetAlias: 'contact', targetFeatureId: fx.tgtName, kind: 'DIRECT', sourceFeatureId: fx.srcName, valuePolicy, annotations: {} }],
                annotations: {}, direction: 'FORWARD', correspondenceId: 'corr-person-contact' },
            { id: 'rule-reverse', name: 'Contact to Person', enabled: true, sourceEndpointId: 'target', sourceTypeId: fx.tgtClass, sourceAlias: 'contact',
                targets: [{ id: 'tp-reverse', endpointId: 'source', typeId: fx.srcClass, alias: 'person', annotations: {} }],
                bindings: [{ id: 'bind-reverse', targetAlias: 'person', targetFeatureId: fx.srcName, kind: 'DIRECT', sourceFeatureId: fx.tgtName, valuePolicy, annotations: {} }],
                annotations: {}, direction: 'REVERSE', correspondenceId: 'corr-person-contact' },
        ],
        generationRules: [], executionPolicy: { mode: 'BATCH', deterministic: true, failOnBindingError: true },
        tracePolicy: { enabled: true, retainBindingDetails: true }, annotations: {},
    };
    const response = await request.post('/api/transformations', { data: doc });
    if (!response.ok()) throw new Error(`POST /api/transformations -> ${response.status()} ${await response.text()}`);
    return fx;
}

function modelJson(id: string, metamodelId: string, elements: unknown[], roots: string[]): string {
    return JSON.stringify({ schemaVersion: '1.0', id, metamodelId, rootElementIds: roots, elements, relations: [], annotations: {} }, null, 2);
}

/** Abre la transformación sembrada, rellena los dos modelos y crea la sesión gestionada desde el panel. */
async function createSessionFromUi(page: Page, request: APIRequestContext, fx: Fixture): Promise<string> {
    await openDesigner(page);
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(fx.transformationId);
    await switchToAdvanced(page);
    const srcMeta = fx.srcMetamodelId;
    const tgtMeta = fx.tgtMetamodelId;
    await page.locator('#transformation-sync-source').fill(modelJson(uid('e2e-sm'), srcMeta, [{ id: 'p1', classId: fx.srcClass, attributes: { [fx.srcName]: 'Ada' }, annotations: {} }], ['p1']));
    await page.locator('#transformation-sync-target').fill(modelJson(uid('e2e-tm'), tgtMeta, [], []));
    await page.getByRole('button', { name: /^(Create managed session|Crear sesión gestionada)$/ }).click();
    await expect(page.locator('.transformation-sync-session-message')).toBeVisible();
    const sessions = await eventually(() => apiGet<{ id: string }[]>(request, `/api/transformations/${fx.transformationId}/synchronization-sessions`), (list) => list.length === 1);
    return sessions[0].id;
}

function sessionOnDisk(sessionId: string): SyncSessionDoc {
    const file = listFiles('data/transformation-sync-sessions').find((name) => name.includes(sessionId));
    if (file === undefined) throw new Error('sin fichero de sesión en disco para ' + sessionId);
    return readJson<SyncSessionDoc>('data/transformation-sync-sessions/' + file);
}

function element(model: SyncModel, classId: string): SyncModelElement {
    return model.elements.find((item) => item.classId === classId)!;
}

async function editTextarea(page: Page, selector: string, mutate: (model: SyncModel) => void): Promise<void> {
    const area = page.locator(selector);
    const model = JSON.parse(await area.inputValue());
    mutate(model);
    await area.fill(JSON.stringify(model, null, 2));
}

const syncButton = (page: Page) => page.getByRole('button', { name: /^(Synchronize managed session|Sincronizar sesión gestionada)$/ });

test('editar el modelo origen y sincronizar avanza la sesión a la revisión 2 con el cambio propagado, y sin ediciones no avanza', async ({ page, request }) => {
    const fx = await seedBidirectional(request, 'FAIL_ON_CONFLICT');
    const sessionId = await createSessionFromUi(page, request, fx);

    // Revisión 1 en disco: línea base de ambos lados con el nombre inicial propagado.
    const first = sessionOnDisk(sessionId);
    expect(first.revision).toBe(1);
    expect(element(first.sourceBaseline, fx.srcClass).attributes[fx.srcName]).toBe('Ada');
    expect(element(first.targetBaseline, fx.tgtClass).attributes[fx.tgtName]).toBe('Ada');

    // Rechazo/no-op: sincronizar sin editar nada no crea una revisión nueva.
    await syncButton(page).click();
    await expect(page.locator('.transformation-sync-session-message')).toHaveText(/No changes detected|No se han detectado cambios/);
    expect(sessionOnDisk(sessionId).revision).toBe(1);

    // El usuario edita el modelo origen en el panel y sincroniza.
    await editTextarea(page, '#transformation-sync-source', (model) => { element(model, fx.srcClass).attributes[fx.srcName] = 'Grace'; });
    await syncButton(page).click();
    await expect(page.locator('.transformation-sync-session-message')).toHaveText(/committed|confirmado/);

    const loaded = await eventually(() => apiGet<SyncSessionDoc>(request, `/api/transformations/${fx.transformationId}/synchronization-sessions/${sessionId}`), (value) => value.revision === 2);
    const second = sessionOnDisk(sessionId);
    for (const session of [loaded, second]) {
        expect(session.revision).toBe(2);
        expect(element(session.sourceBaseline, fx.srcClass).attributes[fx.srcName]).toBe('Grace');
        expect(element(session.targetBaseline, fx.tgtClass).attributes[fx.tgtName]).toBe('Grace');
    }
    const checkpoints = listFiles('data/transformation-sync-history').filter((name) => name.includes(sessionId));
    expect(checkpoints.length).toBeGreaterThanOrEqual(2);
    expect(checkpoints.map((name) => readJson<{ operation: string }>('data/transformation-sync-history/' + name).operation)).toContain('SYNCHRONIZED');

    // Recarga: la interfaz recupera la sesión guardada con la revisión 2.
    await page.reload();
    await savedSelector(page).selectOption(fx.transformationId);
    await switchToAdvanced(page);
    await page.getByRole('combobox', { name: /Managed synchronization session|Sesión de sincronización gestionada/ }).selectOption(sessionId);
    await expect(page.locator('.transformation-sync-session-status strong')).toHaveText(/\b2$/);
    expect(JSON.parse(await page.locator('#transformation-sync-target').inputValue()).elements.find((item: SyncModelElement) => item.classId === fx.tgtClass).attributes[fx.tgtName]).toBe('Grace');
});

test('editar ambos lados en elementos distintos fusiona los cambios y editar lo mismo en ambos lados con «parar ante conflicto» bloquea sin avanzar', async ({ page, request }) => {
    const fx = await seedBidirectional(request, 'FAIL_ON_CONFLICT');
    const sessionId = await createSessionFromUi(page, request, fx);

    // Conflicto: el mismo atributo emparejado cambia en ambos lados; con la política de parar, no se avanza.
    await editTextarea(page, '#transformation-sync-source', (model) => { element(model, fx.srcClass).attributes[fx.srcName] = 'Source edit'; });
    await editTextarea(page, '#transformation-sync-target', (model) => { element(model, fx.tgtClass).attributes[fx.tgtName] = 'Target edit'; });
    await syncButton(page).click();
    await expect(page.locator('.transformation-sync-session-message')).toHaveText(/blocked|bloqueado/);
    const blocked = sessionOnDisk(sessionId);
    expect(blocked.revision).toBe(1);
    expect(element(blocked.sourceBaseline, fx.srcClass).attributes[fx.srcName]).toBe('Ada');
    expect(element(blocked.targetBaseline, fx.tgtClass).attributes[fx.tgtName]).toBe('Ada');

    // Cambios disjuntos: el origen cambia el nombre y el destino una propiedad no emparejada; ambos se conservan.
    await editTextarea(page, '#transformation-sync-source', (model) => { element(model, fx.srcClass).attributes[fx.srcName] = 'Grace'; });
    await editTextarea(page, '#transformation-sync-target', (model) => {
        const contact = element(model, fx.tgtClass);
        contact.attributes[fx.tgtName] = 'Ada';
        contact.attributes[fx.tgtNote] = 'only on target';
    });
    await syncButton(page).click();
    await expect(page.locator('.transformation-sync-session-message')).toHaveText(/committed|confirmado/);
    const merged = await eventually(() => sessionOnDisk(sessionId), (value) => value.revision === 2);
    expect(element(merged.sourceBaseline, fx.srcClass).attributes[fx.srcName]).toBe('Grace');
    expect(element(merged.targetBaseline, fx.tgtClass).attributes[fx.tgtName]).toBe('Grace');
    expect(element(merged.targetBaseline, fx.tgtClass).attributes[fx.tgtNote]).toBe('only on target');
});
