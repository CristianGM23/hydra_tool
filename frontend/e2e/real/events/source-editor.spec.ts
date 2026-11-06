import { expect, test, type Page } from '@playwright/test';
import { apiGet, uid } from '../support';
import {
    EVENTS_ERROR, RE, dismissError, eventNode, eventType, list, openEvents, placeFromPalette, readBindings, readDefinitions, saveEvents,
    saveEventsExpectingRejection, seedEvents, seedMetamodel, tab, text, type Json,
} from './helpers';

const SOURCE = 'textarea[aria-label="Event definitions and bindings JSON source"]';

async function readSource(page: Page): Promise<Json> {
    return JSON.parse(await page.locator(SOURCE).inputValue()) as Json;
}

async function applySource(page: Page, document: unknown): Promise<void> {
    await page.locator(SOURCE).fill(typeof document === 'string' ? document : JSON.stringify(document, null, 2));
    await page.getByRole('button', { name: /^Apply source$/ }).click();
}

// Jornada «editor de texto»: el usuario cambia a la vista de texto, edita tipos y enlaces (válido e inválido), aplica, vuelve a las vistas gráficas y guarda.
// Oráculo: los JSON que el backend real escribió en disco y GET /events de la API real.
test('el editor de texto de eventos normaliza y aplica lo válido, rechaza lo inválido sin tocar el disco y el backend guarda el resultado', async ({ page, request }) => {
    const metamodelId = uid('evs');
    const seededId = `event:seed-${metamodelId}`;
    const addedId = `event:added-${metamodelId}`;
    await seedMetamodel(request, metamodelId);
    await seedEvents(request, metamodelId, 'e2e.source', [eventType('e2e.source', seededId, 'Seeded event')]);
    const before = readDefinitions(metamodelId);
    expect(list<Json>(before.eventTypes).map((item) => item.id)).toEqual([seededId]);

    await openEvents(page, metamodelId);
    await expect(page.locator('.event-designer')).toContainText('1 event(s)');
    await tab(page, RE.sourceTab).click();
    const current = await readSource(page);
    expect(current.definitions.metamodelId).toBe(metamodelId);
    expect(list<Json>(current.definitions.eventTypes).map((item) => item.id)).toEqual([seededId]);

    // a1 (rechazos): texto que no es JSON y un enlace cuyo destino no es un evento propio no se aplican; el modelo y el disco no cambian.
    await applySource(page, '{ esto no es json');
    await expect(page.locator(EVENTS_ERROR)).toBeVisible();
    await dismissError(page, EVENTS_ERROR);
    const withBadTarget = JSON.parse(JSON.stringify(current)) as Json;
    withBadTarget.bindings.bindings = [{ id: `binding:bad-${metamodelId}`, name: 'Bad target', sourceEventTypeId: seededId, targetEventTypeId: 'core.model.saved', enabled: true }];
    await applySource(page, withBadTarget);
    await expect(page.locator(EVENTS_ERROR)).toBeVisible();
    await dismissError(page, EVENTS_ERROR);
    await expect(page.locator('.event-dirty-state')).toHaveText(RE.saved);
    expect(readDefinitions(metamodelId)).toEqual(before);
    expect(list(readBindings(metamodelId).bindings)).toHaveLength(0);

    // a1: edición válida del texto con valores omitidos (el diseñador los normaliza): un tipo nuevo y un enlace desde un evento reservado.
    const edited = JSON.parse(JSON.stringify(current)) as Json;
    edited.definitions.eventTypes.push({ id: addedId, name: 'Added from source', namespace: 'e2e.source', enabled: true });
    edited.bindings.bindings = [{ id: `binding:src-${metamodelId}`, name: 'Saved to added', sourceEventTypeId: 'core.model.saved', targetEventTypeId: addedId, enabled: true }];
    await applySource(page, edited);
    await expect(page.locator('.event-dirty-state')).toHaveText(/^(Unsaved changes|Cambios sin guardar)$/);
    await expect(page.locator('.hydra-independent-workbench .status-message')).toContainText('Event source applied to the HYDRA documents');

    // a3 (rechazo del servidor): una condición inexistente pasa el control local pero el backend no la acepta y el disco queda igual.
    await tab(page, RE.sourceTab).click();
    const withBadCondition = await readSource(page);
    withBadCondition.bindings.bindings[0].conditionExpressionId = 'expression:does-not-exist';
    await applySource(page, withBadCondition);
    expect(await saveEventsExpectingRejection(page)).toBeGreaterThanOrEqual(400);
    expect(readDefinitions(metamodelId)).toEqual(before);
    expect(list(readBindings(metamodelId).bindings)).toHaveLength(0);
    await dismissError(page, EVENTS_ERROR);

    // Se quita la condición desde el texto y se guarda de verdad.
    await tab(page, RE.sourceTab).click();
    const fixed = await readSource(page);
    expect(fixed.bindings.bindings[0].conditionExpressionId).toBe('expression:does-not-exist');
    fixed.bindings.bindings[0].conditionExpressionId = '';
    await applySource(page, fixed);
    await saveEvents(page);

    // Oráculo a3: lo guardado es el modelo normalizado (versión, persistencia y política de error por defecto), con el tipo y el enlace nuevos.
    const savedDefinitions = readDefinitions(metamodelId);
    const types = list<Json>(savedDefinitions.eventTypes);
    expect(types.map((item) => item.id).sort()).toEqual([addedId, seededId].sort());
    const added = types.find((item) => item.id === addedId) as Json;
    expect(added.name).toBe('Added from source');
    expect(added.version).toBe('1.0');
    expect(added.persistencePolicy).toBe('TRANSIENT');
    expect(list(added.fields)).toHaveLength(0);
    expect(types.find((item) => item.id === seededId)).toEqual(list<Json>(before.eventTypes)[0]);
    const savedBindings = list<Json>(readBindings(metamodelId).bindings);
    expect(savedBindings).toHaveLength(1);
    expect(savedBindings[0].sourceEventTypeId).toBe('core.model.saved');
    expect(savedBindings[0].targetEventTypeId).toBe(addedId);
    expect(savedBindings[0].errorPolicy).toBe('REPORT');
    expect(text(savedBindings[0].conditionExpressionId)).toBe('');
    const viaApi = await apiGet<Json>(request, `/api/canonical-metamodels/${metamodelId}/events`);
    expect(list<Json>(viaApi.bindings.bindings).map((item) => item.id)).toEqual([savedBindings[0].id]);
    expect(list<Json>(viaApi.definitions.eventTypes).map((item) => item.id).sort()).toEqual([addedId, seededId].sort());

    // a2: volver a formulario y a arquitectura muestra el resultado: el tipo y el enlace nuevos aparecen en las vistas gráficas.
    await tab(page, RE.formTab).click();
    await expect(page.locator('.event-designer')).toContainText('2 event(s)');
    await expect(page.locator('.event-designer')).toContainText('1 binding(s)');
    await tab(page, RE.architectureTab).click();
    await placeFromPalette(page, 'Model saved');
    await placeFromPalette(page, 'Added from source');
    await expect(eventNode(page, 'Added from source')).toHaveCount(1);
    await expect(page.locator('.react-flow__edge')).toHaveCount(1);
    await expect(page.locator('.event-architecture-toolbar-summary')).toContainText('1 binding(s)');
});

// Normalización previa al envío (a3): lo que el usuario escribe con valores omitidos lo completa el propio diseñador ANTES de hablar con el
// servidor; se comprueba leyendo el texto que muestra el editor sin que haya salido ninguna escritura hacia la API, y después que el backend guarda
// lo mismo. Oráculo: peticiones de escritura observadas en el navegador, ficheros del backend y GET /events de la API real.
test('el editor de texto completa los valores omitidos antes de enviar nada y el backend guarda lo mismo que mostraba', async ({ page, request }) => {
    const metamodelId = uid('evn');
    const seededId = `event:seed-${metamodelId}`;
    const addedId = `event:added-${metamodelId}`;
    const bindingId = `binding:norm-${metamodelId}`;
    await seedMetamodel(request, metamodelId);
    await seedEvents(request, metamodelId, 'e2e.norm', [eventType('e2e.norm', seededId, 'Seeded event')]);
    const definitionsBefore = readDefinitions(metamodelId);
    const bindingsBefore = readBindings(metamodelId);

    await openEvents(page, metamodelId);
    await tab(page, RE.sourceTab).click();
    const writes: string[] = [];
    page.on('request', (item) => {
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(item.method()) && /\/api\/canonical-metamodels\/[^/]+\/events/.test(item.url())) writes.push(`${item.method()} ${new URL(item.url()).pathname}`);
    });

    // Se escribe un tipo y un enlace con solo los campos mínimos (sin versión, persistencia, política de error, copias ni nombre del enlace).
    const edited = await readSource(page);
    edited.definitions.eventTypes.push({ id: addedId, name: 'Added minimal', namespace: 'e2e.norm', enabled: true });
    edited.bindings.bindings = [{ id: bindingId, sourceEventTypeId: 'core.model.saved', targetEventTypeId: addedId, enabled: true }];
    await applySource(page, edited);
    await expect(page.locator('.event-dirty-state')).toHaveText(/^(Unsaved changes|Cambios sin guardar)$/);

    // El texto que vuelve a mostrar el editor ya trae los valores por defecto completados por la pantalla.
    await tab(page, RE.sourceTab).click();
    const shown = await readSource(page);
    const shownType = list<Json>(shown.definitions.eventTypes).find((item) => item.id === addedId) as Json;
    expect(shownType).toMatchObject({ version: '1.0', persistencePolicy: 'TRANSIENT', description: '', category: '', fields: [], allowedSources: [], allowedSubjects: [], abstractType: false, deprecated: false, enabled: true });
    const shownBinding = list<Json>(shown.bindings.bindings)[0];
    expect(shownBinding).toMatchObject({ id: bindingId, name: bindingId, conditionExpressionId: '', payloadMappings: [], enabled: true, errorPolicy: 'REPORT' });

    // Hasta aquí no ha salido ninguna escritura y el disco sigue igual: la normalización es de la pantalla, no del servidor.
    expect(writes).toEqual([]);
    expect(readDefinitions(metamodelId)).toEqual(definitionsBefore);
    expect(readBindings(metamodelId)).toEqual(bindingsBefore);

    await saveEvents(page);
    expect(writes.length).toBeGreaterThan(0);

    // Oráculo: lo guardado coincide con lo que mostraba el editor (el servidor solo omite vacíos).
    const savedType = list<Json>(readDefinitions(metamodelId).eventTypes).find((item) => item.id === addedId) as Json;
    expect(savedType.name).toBe('Added minimal');
    expect(savedType.version).toBe(shownType.version);
    expect(savedType.persistencePolicy).toBe(shownType.persistencePolicy);
    expect(list(savedType.fields)).toHaveLength(0);
    const savedBinding = list<Json>(readBindings(metamodelId).bindings)[0];
    expect(savedBinding.id).toBe(bindingId);
    expect(savedBinding.name).toBe(shownBinding.name);
    expect(savedBinding.errorPolicy).toBe(shownBinding.errorPolicy);
    expect(savedBinding.enabled).toBe(true);
    expect(text(savedBinding.conditionExpressionId)).toBe('');
    expect(list(savedBinding.payloadMappings)).toHaveLength(0);
    const viaApi = await apiGet<Json>(request, `/api/canonical-metamodels/${metamodelId}/events`);
    expect(list<Json>(viaApi.bindings.bindings).map((item) => item.id)).toEqual([bindingId]);
});
