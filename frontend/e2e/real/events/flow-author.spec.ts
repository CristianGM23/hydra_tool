import { expect, test, type Page } from '@playwright/test';
import { apiGet, exists, uid } from '../support';
import { FLOWS_ERROR, RE, dismissError, files, list, openFlows, readFlows, saveFlows, saveFlowsExpectingRejection, seedMetamodel, tab, text, type Json } from './helpers';

/** Selecciona el modo avanzado del formulario de flujo (el formulario arranca en modo guiado cada vez que se muestra). */
async function advancedFlowForm(page: Page): Promise<void> {
    await page.locator('.hydra-creation-mode-switch button').nth(1).click();
    await expect(page.locator('#event-flow-id')).toBeVisible();
}

async function connectNodes(page: Page, source: string, target: string): Promise<void> {
    await page.getByLabel(`Source connection point for ${source}`, { exact: true }).dispatchEvent('click');
    await expect(page.locator('.hydra-diagram-pointer-modes').getByRole('button', { name: RE.connect })).toHaveAttribute('aria-pressed', 'true');
    await page.getByLabel(`Target connection point for ${target}`, { exact: true }).dispatchEvent('click');
    await expect(page.locator('.hydra-diagram-pointer-modes').getByRole('button', { name: RE.connect })).toHaveAttribute('aria-pressed', 'false');
}

// Jornada «autoría de un flujo»: el usuario añade un flujo, elige tipos de paso, configura reintento/plazo, añade variable y compensación,
// conecta el grafo y guarda. Oráculo: el JSON que el backend real escribió en disco y la API real (sin fichero previo del flujo).
test('el diseñador de flujos persiste flujo, disparadores, pasos configurados, variable y compensación, y el backend rechaza el grafo inconexo', async ({ page, request }) => {
    const metamodelId = uid('evf');
    const flowId = `flow:${metamodelId}`;
    await seedMetamodel(request, metamodelId);
    expect(exists(files(metamodelId).flows)).toBe(false);

    await openFlows(page, metamodelId);
    await expect(page.locator('.event-flow-designer')).toContainText('0 flow(s)');

    // a1: añadir un flujo (nace con un paso de inicio y uno de final) y fijar identificador, nombre y evento disparador.
    await page.getByRole('button', { name: /^\+ Flow$/ }).click();
    await expect(page.locator('.event-flow-designer')).toContainText('1 flow(s)');
    await advancedFlowForm(page);
    await page.locator('#event-flow-id').fill(flowId);
    await page.locator('#event-flow-name').fill('E2E flow');
    await page.locator('#event-flow-trigger-picker').selectOption('core.model.saved');
    await page.getByRole('button', { name: /^\+ Trigger$/ }).click();
    await expect(page.locator('.event-flow-trigger-list article')).toHaveCount(1);

    // a2: un paso nuevo desde el selector de tipo de paso es de evento por defecto.
    await expect(page.locator('#event-flow-new-node-kind')).toHaveValue('EVENT_STEP');
    await page.getByRole('button', { name: /^\+ Node$/ }).click();
    await page.locator('#flow-node-name').fill('Check event');
    await page.locator('#flow-node-event-type').selectOption('core.model.saved');

    // a2 + a3: tipos de paso desde la paleta del diagrama y propiedades de reintento y plazo.
    await tab(page, RE.diagramTab).click();
    await page.locator('.event-flow-palette-node.kind-action_step').click();
    await page.locator('#flow-node-name').fill('Run action');
    await page.locator('#flow-node-retry-attempts').fill('3');
    await page.locator('#flow-node-retry-delay').fill('500');
    await page.locator('#flow-node-retry-backoff').fill('2');
    await page.locator('#flow-node-timeout').fill('5000');
    await page.locator('#flow-node-compensable').check();
    await page.locator('.event-flow-palette-node.kind-compensation_step').click();
    await page.locator('#flow-node-name').fill('Undo action');

    // a5: variable y compensación en el formulario del flujo.
    await tab(page, RE.formTab).click();
    await page.locator('.hydra-designer-sidebar .hydra-designer-list-item').filter({ hasText: flowId }).click();
    await advancedFlowForm(page);
    await page.getByRole('button', { name: /^\+ Variable$/ }).click();
    await page.locator('#flow-variable-id-0').fill(`variable:${metamodelId}`);
    await page.locator('#flow-variable-name-0').fill('modelId');
    await page.locator('#flow-variable-type-0').fill('DOCUMENT_ID');
    await page.getByRole('button', { name: /^\+ Compensation$/ }).click();
    await expect(page.locator('#flow-compensation-id-0')).toBeVisible();

    // Rechazo: con los pasos sin conectar el servidor no guarda y no escribe ningún fichero.
    expect(await saveFlowsExpectingRejection(page)).toBeGreaterThanOrEqual(400);
    expect(exists(files(metamodelId).flows)).toBe(false);
    expect((await request.get(`/api/canonical-metamodels/${metamodelId}/event-flows/optional`)).status()).toBe(404);
    await dismissError(page, FLOWS_ERROR);

    // Conectar el grafo: Start -> Check event -> Run action -> Undo action -> Success End.
    await tab(page, RE.diagramTab).click();
    await connectNodes(page, 'Start', 'Check event');
    await connectNodes(page, 'Check event', 'Run action');
    await connectNodes(page, 'Run action', 'Undo action');
    await connectNodes(page, 'Undo action', 'Success End');
    await expect(page.locator('.react-flow__edge')).toHaveCount(4);
    // Guardado natural: flujos y diagrama se escriben y la pantalla confirma «Event flows and diagram saved».
    await saveFlows(page);
    expect(exists(files(metamodelId).flows)).toBe(true);
    expect(exists(files(metamodelId).flowDiagram)).toBe(true);

    // Oráculo: el JSON del backend contiene lo introducido por el usuario.
    const model = readFlows(metamodelId);
    const flows = list<Json>(model.flows);
    expect(flows).toHaveLength(1);
    const flow = flows[0];
    expect(flow.id).toBe(flowId);
    expect(flow.name).toBe('E2E flow');
    expect(list<string>(flow.triggerEventTypeIds)).toEqual(['core.model.saved']);
    const nodes = list<Json>(flow.nodes);
    expect(nodes.map((item) => item.kind).sort()).toEqual(['ACTION_STEP', 'COMPENSATION_STEP', 'EVENT_STEP', 'START', 'SUCCESS_END']);
    const byName = (name: string): Json => nodes.find((item) => item.name === name) as Json;
    expect(byName('Check event').kind).toBe('EVENT_STEP');
    expect(byName('Check event').eventTypeId).toBe('core.model.saved');
    const action = byName('Run action');
    expect(action.kind).toBe('ACTION_STEP');
    expect(action.retryPolicy.maxAttempts).toBe(3);
    expect(action.retryPolicy.delayMillis).toBe(500);
    expect(action.retryPolicy.backoffMultiplier).toBe(2);
    expect(action.timeoutPolicy.timeoutMillis).toBe(5000);
    expect(action.compensable).toBe(true);
    const undo = byName('Undo action');
    expect(undo.kind).toBe('COMPENSATION_STEP');
    const variables = list<Json>(flow.variables);
    expect(variables).toHaveLength(1);
    expect(variables[0].id).toBe(`variable:${metamodelId}`);
    expect(variables[0].name).toBe('modelId');
    expect(variables[0].type).toBe('DOCUMENT_ID');
    const compensations = list<Json>(flow.compensations);
    expect(compensations).toHaveLength(1);
    expect(compensations[0].sourceNodeId).toBe(action.id);
    expect(compensations[0].compensationNodeId).toBe(undo.id);
    const nameOf = (id: string): string => text(nodes.find((item) => item.id === id)?.name);
    expect(list<Json>(flow.transitions).map((item) => `${nameOf(item.sourceNodeId)}>${nameOf(item.targetNodeId)}`).sort())
        .toEqual(['Check event>Run action', 'Run action>Undo action', 'Start>Check event', 'Undo action>Success End']);
    // Oráculo secundario: la API real devuelve el mismo flujo.
    const viaApi = await apiGet<Json>(request, `/api/canonical-metamodels/${metamodelId}/event-flows/optional`);
    expect(list<Json>(viaApi.eventFlowModel.flows).map((item) => item.id)).toEqual([flowId]);

    // Recarga: el diseñador vuelve a leer del backend el flujo guardado (con las cadenas vacías omitidas) y dibuja el diagrama sin fallar.
    await openFlows(page, metamodelId);
    await expect(page.locator('.event-flow-designer')).toContainText('1 flow(s)');
    await expect(page.locator(FLOWS_ERROR)).toHaveCount(0);
    await tab(page, RE.diagramTab).click();
    await expect(page.locator('.react-flow__node')).toHaveCount(5);
    await expect(page.locator('.react-flow__edge')).toHaveCount(4);
});
