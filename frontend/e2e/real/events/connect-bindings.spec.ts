import { expect, test } from '@playwright/test';
import { apiGet, readJson, uid } from '../support';
import {
    EVENTS_ERROR, RE, arrangeCanvas, clearField, connectByClicks, connectByDrag, dismissError, enterConnectMode, eventNode, eventType, files, inspector, list,
    openEvents, placeFromPalette, readBindings, saveEvents, saveEventsExpectingRejection, seedConditionExpressions, seedEvents, seedEventsWithBindings, seedMetamodel, tab, text,
    type Json,
} from './helpers';

// Jornada «conectar eventos»: el usuario elige un evento reservado como origen, conecta con un evento propio en el lienzo,
// intenta conexiones prohibidas, rellena las copias de datos del campo obligatorio y guarda.
// Oráculo: los JSON que el backend real escribió en disco y GET /events de la API real.
test('el lienzo de eventos crea el enlace con valores por defecto, rechaza las conexiones prohibidas y el backend persiste copias y condición válidas', async ({ page, request }) => {
    const metamodelId = uid('evc');
    const plainId = `event:plain-${metamodelId}`;
    const targetId = `event:target-${metamodelId}`;
    const fieldId = 'field:model-id';
    await seedMetamodel(request, metamodelId);
    // Siembra con la API real: un evento propio sin datos y otro con un campo obligatorio (no es la etapa que se certifica).
    await seedEvents(request, metamodelId, 'e2e.connect', [
        eventType('e2e.connect', plainId, 'Plain event'),
        eventType('e2e.connect', targetId, 'Target event', [{ id: fieldId, name: 'modelId', type: 'STRING', required: true }]),
    ]);
    expect(list(readBindings(metamodelId).bindings)).toHaveLength(0);

    await openEvents(page, metamodelId);
    await expect(page.locator('.event-designer')).toContainText('2 event(s)');
    await tab(page, RE.architectureTab).click();
    await placeFromPalette(page, 'Model saved');
    await placeFromPalette(page, 'Plain event');
    await placeFromPalette(page, 'Target event');
    await arrangeCanvas(page);
    const summary = page.locator('.event-architecture-toolbar-summary');
    await expect(summary).toContainText('3 event(s)');
    await expect(summary).toContainText('0 binding(s)');

    // a1: elegir el evento reservado como origen (aparece su ficha de sistema, sin formulario de evento propio).
    await eventNode(page, 'Model saved').locator('.event-architecture-node strong').click();
    await expect(page.locator('.event-reserved-inspector')).toContainText('Model saved');

    // a2 (arrastrar) + a3: del evento reservado al evento propio sin datos. Si el arrastre no engancha, se usa la conexión por clics de la misma herramienta.
    await connectByDrag(page, 'Model saved', 'Plain event');
    const drawn = await expect(page.locator('.react-flow__edge')).toHaveCount(1, { timeout: 4_000 }).then(() => true).catch(() => false);
    if (!drawn) await connectByClicks(page, 'Model saved', 'Plain event');
    await expect(page.locator('.react-flow__edge')).toHaveCount(1);
    await expect(summary).toContainText('1 binding(s)');
    const panel = inspector(page);
    await expect(panel.locator('#event-binding-source')).toHaveValue('core.model.saved');
    await expect(panel.locator('#event-binding-target')).toHaveValue(plainId);
    await expect(panel.locator('#event-binding-enabled')).toBeChecked();
    await expect(panel.locator('#event-binding-error-policy')).toHaveValue('REPORT');
    await expect(panel.locator('#event-binding-condition')).toHaveValue('');
    await panel.locator('#event-binding-name').fill('Saved to plain');
    await saveEvents(page);

    // Oráculo a3: el enlace persistido nace activo, con política REPORT, sin condición ni copias.
    const firstSave = readBindings(metamodelId);
    expect(list<Json>(firstSave.bindings)).toHaveLength(1);
    const plainBinding = list<Json>(firstSave.bindings)[0];
    expect(plainBinding.sourceEventTypeId).toBe('core.model.saved');
    expect(plainBinding.targetEventTypeId).toBe(plainId);
    expect(plainBinding.name).toBe('Saved to plain');
    expect(plainBinding.enabled).toBe(true);
    expect(plainBinding.errorPolicy).toBe('REPORT');
    expect(text(plainBinding.conditionExpressionId)).toBe('');
    expect(list(plainBinding.payloadMappings)).toHaveLength(0);
    const viaApi = await apiGet<Json>(request, `/api/canonical-metamodels/${metamodelId}/events`);
    expect(list<Json>(viaApi.bindings.bindings).map((item) => item.id)).toEqual([plainBinding.id]);

    // d1/a2 (rechazos): ninguna conexión prohibida crea un enlace nuevo.
    // (i) un evento reservado no ofrece puerto de entrada: no puede ser destino.
    await expect(page.getByLabel('Target connection point for Model saved', { exact: true })).toHaveCount(0);
    // (ii) conectar un evento consigo mismo no hace nada.
    await enterConnectMode(page);
    await page.getByLabel('Source connection point for Plain event', { exact: true }).dispatchEvent('click');
    await page.getByLabel('Target connection point for Plain event', { exact: true }).dispatchEvent('click');
    await expect(page.locator('.react-flow__edge')).toHaveCount(1);
    await expect(summary).toContainText('1 binding(s)');
    // (iii) un par ya conectado se rechaza con aviso.
    await connectByClicks(page, 'Model saved', 'Plain event');
    await expect(page.locator(EVENTS_ERROR)).toBeVisible();
    await expect(page.locator('.react-flow__edge')).toHaveCount(1);
    await expect(summary).toContainText('1 binding(s)');
    await dismissError(page, EVENTS_ERROR);
    await expect(page.locator('.event-dirty-state')).toHaveText(RE.saved);
    expect(list(readBindings(metamodelId).bindings)).toHaveLength(1);

    // a1 + a2 + a3 de nuevo: segundo enlace, ahora hacia el evento con campo obligatorio (por clics sobre los puertos).
    await connectByClicks(page, 'Model saved', 'Target event');
    await expect(page.locator('.react-flow__edge')).toHaveCount(2);
    await expect(panel.locator('#event-binding-target')).toHaveValue(targetId);
    await panel.locator('#event-binding-name').fill('Saved to target');

    // a4 (rechazo): sin copia del campo obligatorio el diseñador no deja guardar y el disco queda igual.
    expect(await saveEventsExpectingRejection(page)).toBe(0);
    expect(list(readBindings(metamodelId).bindings)).toHaveLength(1);
    await dismissError(page, EVENTS_ERROR);

    // a4/a6: copia de datos del campo obligatorio: «+ Data mapping» propone el campo y la ruta; se edita la ruta.
    await panel.getByRole('button', { name: /^\+ Data mapping$/ }).click();
    await expect(panel.locator('#event-mapping-target-0')).toHaveValue(fieldId);
    await expect(panel.locator('#event-mapping-path-0')).toHaveValue('payload.modelId');
    await panel.locator('#event-mapping-path-0').fill('context.modelId');

    // a4 (rechazo del servidor): una condición que no existe en ningún modelo de expresiones no se guarda y el disco queda igual.
    await panel.locator('#event-binding-condition').fill('expression:does-not-exist');
    const rejectedStatus = await saveEventsExpectingRejection(page);
    expect(rejectedStatus).toBeGreaterThanOrEqual(400);
    expect(list(readBindings(metamodelId).bindings)).toHaveLength(1);
    await dismissError(page, EVENTS_ERROR);

    // Se quita la condición y se guarda de verdad.
    await clearField(panel.locator('#event-binding-condition'));
    await saveEvents(page);

    // Oráculo a4/a6: dos enlaces; el segundo trae la copia de datos exacta y sigue activo, con política REPORT y sin condición.
    const saved = list<Json>(readBindings(metamodelId).bindings);
    expect(saved).toHaveLength(2);
    const targetBinding = saved.find((item) => item.targetEventTypeId === targetId) as Json;
    expect(targetBinding.sourceEventTypeId).toBe('core.model.saved');
    expect(targetBinding.name).toBe('Saved to target');
    expect(targetBinding.enabled).toBe(true);
    expect(targetBinding.errorPolicy).toBe('REPORT');
    expect(text(targetBinding.conditionExpressionId)).toBe('');
    expect(list<Json>(targetBinding.payloadMappings)).toHaveLength(1);
    expect(list<Json>(targetBinding.payloadMappings)[0].targetFieldId).toBe(fieldId);
    expect(list<Json>(targetBinding.payloadMappings)[0].sourcePath).toBe('context.modelId');
    expect(saved.find((item) => item.targetEventTypeId === plainId)).toEqual(plainBinding);
    const diagram = readJson<Json>(files(metamodelId).diagram);
    expect(list<Json>(diagram.nodes).map((item) => item.nodeId).sort()).toEqual(['core.model.saved', plainId, targetId].sort());
    expect(list<Json>(diagram.edges).map((item) => item.edgeId).sort()).toEqual(saved.map((item) => item.id).sort());

    // Guardar y recargar: la interfaz relee del backend y dibuja los dos enlaces persistidos.
    await openEvents(page, metamodelId);
    await tab(page, RE.architectureTab).click();
    await expect(page.locator('.react-flow__edge')).toHaveCount(2);
    await expect(page.locator('.event-architecture-toolbar-summary')).toContainText('2 binding(s)');
});

// Jornada «editar un enlace ya guardado» (a4 y a6): el enlace, su copia de datos y el modelo de expresiones se siembran con la API real;
// el usuario abre el enlace guardado en el formulario, quita la copia obligatoria (rechazo), la repone con otra ruta, pone una condición
// que SÍ existe y guarda; después la quita desde el panel del lienzo. Oráculo: ficheros del backend y GET /events de la API real.
test('editar un enlace guardado: la copia obligatoria no se puede quitar, una condición existente se guarda y se puede retirar desde el lienzo', async ({ page, request }) => {
    const metamodelId = uid('eve');
    const targetId = `event:target-${metamodelId}`;
    const fieldId = 'field:model-id';
    const bindingId = `binding:saved-${metamodelId}`;
    const conditionId = 'expression:always-true';
    await seedMetamodel(request, metamodelId);
    await seedConditionExpressions(request, metamodelId, [conditionId]);
    await seedEventsWithBindings(request, metamodelId, 'e2e.edit',
        [eventType('e2e.edit', targetId, 'Target event', [{ id: fieldId, name: 'modelId', type: 'STRING', required: true }])],
        [{ id: bindingId, name: 'Saved binding', sourceEventTypeId: 'core.model.saved', targetEventTypeId: targetId, conditionExpressionId: '', payloadMappings: [{ targetFieldId: fieldId, sourcePath: 'payload.modelId', expressionId: '' }], enabled: true, errorPolicy: 'REPORT', annotations: {} }],
        metamodelId);
    const seeded = readBindings(metamodelId);
    expect(list<Json>(seeded.bindings)).toHaveLength(1);
    expect(text(list<Json>(seeded.bindings)[0].conditionExpressionId)).toBe('');
    expect(list<Json>(list<Json>(seeded.bindings)[0].payloadMappings)[0].sourcePath).toBe('payload.modelId');

    // a6: abrir el enlace guardado en el formulario; el panel muestra lo persistido.
    await openEvents(page, metamodelId);
    await tab(page, RE.formTab).click();
    await page.locator('.hydra-designer-list-item').filter({ hasText: bindingId }).click();
    await expect(page.locator('#event-binding-name')).toHaveValue('Saved binding');
    await expect(page.locator('#event-binding-condition')).toHaveValue('');
    await expect(page.locator('#event-mapping-target-0')).toHaveValue(fieldId);
    await expect(page.locator('#event-mapping-path-0')).toHaveValue('payload.modelId');

    // a4/a6 (rechazo): quitar la única copia deja el campo obligatorio sin cubrir; no se guarda y el disco queda como estaba.
    await page.locator('.hydra-designer-row-card').getByRole('button', { name: /^Remove$/ }).click();
    await expect(page.locator('#event-mapping-path-0')).toHaveCount(0);
    await saveEventsExpectingRejection(page);
    expect(readBindings(metamodelId)).toEqual(seeded);
    await dismissError(page, EVENTS_ERROR);

    // a4/a6: se repone la copia con otra ruta y se pone una condición que existe en el modelo de expresiones.
    await page.getByRole('button', { name: /^\+ Data mapping$/ }).click();
    await expect(page.locator('#event-mapping-target-0')).toHaveValue(fieldId);
    await page.locator('#event-mapping-path-0').fill('context.modelId');
    await page.locator('#event-binding-condition').fill(conditionId);
    await saveEvents(page);

    // Oráculo: el mismo enlace (mismo id) con la condición válida y la copia nueva; el servidor la comprueba sin errores.
    const edited = list<Json>(readBindings(metamodelId).bindings);
    expect(edited).toHaveLength(1);
    expect(edited[0].id).toBe(bindingId);
    expect(edited[0].name).toBe('Saved binding');
    expect(edited[0].enabled).toBe(true);
    expect(edited[0].errorPolicy).toBe('REPORT');
    expect(edited[0].conditionExpressionId).toBe(conditionId);
    expect(list<Json>(edited[0].payloadMappings)).toHaveLength(1);
    expect(list<Json>(edited[0].payloadMappings)[0].targetFieldId).toBe(fieldId);
    expect(list<Json>(edited[0].payloadMappings)[0].sourcePath).toBe('context.modelId');
    expect(readBindings(metamodelId).expressionModelId).toBe(metamodelId);
    const viaApi = await apiGet<Json>(request, `/api/canonical-metamodels/${metamodelId}/events`);
    expect(list<Json>(viaApi.bindings.bindings)[0].conditionExpressionId).toBe(conditionId);
    expect(list<Json>(viaApi.checkReport?.diagnostics).filter((item) => item.severity === 'ERROR')).toHaveLength(0);

    // a4 desde el lienzo: tras recargar, se selecciona el enlace dibujado, el panel trae la condición guardada y el usuario la retira.
    await openEvents(page, metamodelId);
    await tab(page, RE.architectureTab).click();
    await placeFromPalette(page, 'Model saved');
    await placeFromPalette(page, 'Target event');
    await arrangeCanvas(page);
    await expect(page.locator('.react-flow__edge')).toHaveCount(1);
    await page.locator('.react-flow__edge').first().dispatchEvent('click');
    const panel = inspector(page);
    await expect(panel.locator('#event-binding-condition')).toHaveValue(conditionId);
    await expect(panel.locator('#event-mapping-path-0')).toHaveValue('context.modelId');
    await clearField(panel.locator('#event-binding-condition'));
    await saveEvents(page);
    const cleared = list<Json>(readBindings(metamodelId).bindings);
    expect(cleared).toHaveLength(1);
    expect(cleared[0].id).toBe(bindingId);
    expect(text(cleared[0].conditionExpressionId)).toBe('');
    expect(list<Json>(cleared[0].payloadMappings)[0].sourcePath).toBe('context.modelId');
});
