import { expect, test } from '@playwright/test';
import { apiGet, exists, uid } from '../support';
import { EVENTS_ERROR, RE, dismissError, eventsSaved, files, list, openEvents, readDefinitions, saveEvents, saveEventsExpectingRejection, seedMetamodel, text, type Json } from './helpers';

// Jornada «formulario de tipos de evento»: el usuario revisa el espacio de nombres, añade tipos con sus valores por defecto,
// elige categoría (de la lista y personalizada), añade campos (texto, enumerado, objeto) y guarda.
// Oráculo: event-definitions.json que escribió el backend real y GET /events de la API real.
test('el formulario de eventos persiste espacio de nombres, valores por defecto, categorías y campos, y el backend rechaza un enumerado sin tipo', async ({ page, request }) => {
    const metamodelId = uid('evt');
    await seedMetamodel(request, metamodelId);
    expect(eventsSaved(metamodelId)).toBe(false);

    await openEvents(page, metamodelId);
    const addType = page.getByRole('button', { name: /^\+ Event type$/ });
    const listItem = (name: string) => page.locator('.hydra-designer-sidebar .hydra-designer-list-item').filter({ hasText: name });

    // a1: el espacio de nombres se propone a partir del metamodelo.
    const proposed = `editor.${metamodelId.replaceAll('-', '_')}`;
    await expect(page.locator('#event-namespace')).toHaveValue(proposed);

    // a1 (rechazo): un espacio de nombres que no empieza por letra no se guarda y no se escribe nada.
    await page.locator('#event-namespace').fill('1bad');
    expect(await saveEventsExpectingRejection(page)).toBe(0);
    expect(eventsSaved(metamodelId)).toBe(false);
    await dismissError(page, EVENTS_ERROR);
    const namespace = `e2e.${metamodelId.replaceAll('-', '_')}`;
    await page.locator('#event-namespace').fill(namespace);

    // a2: un tipo nuevo aparece con nombre por defecto, categoría de dominio, persistencia transitoria y versión 1.0.
    await addType.click();
    await expect(page.locator('#event-type-name')).toHaveValue('NewEvent');
    await expect(page.locator('#event-type-category')).toHaveValue('DOMAIN');
    await expect(page.locator('#event-type-persistence')).toHaveValue('TRANSIENT');
    await expect(page.locator('#event-type-version')).toHaveValue('1.0');
    await expect(page.locator('#event-type-namespace')).toHaveValue(namespace);

    // a3 + a4 + a5: segundo tipo con categoría de la lista, persistencia de auditoría y tres campos.
    await addType.click();
    await page.locator('#event-type-name').fill('Order paid');
    await page.locator('#event-type-category').selectOption('VALIDATION');
    await page.locator('#event-type-persistence').selectOption('AUDIT');
    const addField = page.getByRole('button', { name: /^\+ Data field$/ });
    await addField.click();
    await expect(page.locator('#event-field-name-0')).toHaveValue('value');
    await expect(page.locator('#event-field-type-0')).toHaveValue('STRING');
    await page.locator('#event-field-name-0').fill('orderId');
    await page.locator('#event-field-type-0').selectOption('DOCUMENT_ID');
    await addField.click();
    await page.locator('#event-field-name-1').fill('status');
    await page.locator('#event-field-type-1').selectOption('ENUM');
    await expect(page.locator('#event-field-enum-1')).toBeVisible();
    await addField.click();
    await page.locator('#event-field-name-2').fill('details');
    await page.locator('#event-field-type-2').selectOption('OBJECT');
    await expect(page.locator('#event-field-object-2')).toBeVisible();

    // a3: tercer tipo con la categoría personalizada.
    await addType.click();
    await page.locator('#event-type-name').fill('Order refunded');
    await page.locator('#event-type-category').selectOption('__CUSTOM__');
    await expect(page.locator('#event-type-custom-category')).toBeVisible();
    await page.locator('#event-type-custom-category').fill('billing');

    // a5 (rechazo): un campo enumerado u objeto sin su tipo no se guarda; el servidor lo rechaza y no escribe ningún fichero.
    expect(await saveEventsExpectingRejection(page)).toBeGreaterThanOrEqual(400);
    expect(eventsSaved(metamodelId)).toBe(false);
    expect(exists(files(metamodelId).bindings)).toBe(false);
    expect((await request.get(`/api/canonical-metamodels/${metamodelId}/events`)).status()).toBe(404);
    await dismissError(page, EVENTS_ERROR);

    // a5: se completan la enumeración y el contrato de objeto del segundo tipo y se guarda.
    await listItem('Order paid').click();
    await page.locator('#event-field-enum-1').fill('status-kind');
    await page.locator('#event-field-object-2').fill('order-details');
    await saveEvents(page);

    // Oráculo: event-definitions.json con espacio de nombres, valores por defecto, categorías y campos tal y como se introdujeron.
    const saved = readDefinitions(metamodelId);
    expect(saved.namespace).toBe(namespace);
    const types = list<Json>(saved.eventTypes);
    expect(types).toHaveLength(3);
    const named = (name: string): Json => types.find((item) => item.name === name) as Json;
    const defaults = named('NewEvent');
    expect(defaults.category).toBe('DOMAIN');
    expect(defaults.persistencePolicy).toBe('TRANSIENT');
    expect(defaults.version).toBe('1.0');
    expect(defaults.namespace).toBe(namespace);
    expect(list(defaults.fields)).toHaveLength(0);
    const paid = named('Order paid');
    expect(paid.category).toBe('VALIDATION');
    expect(paid.persistencePolicy).toBe('AUDIT');
    const fields = list<Json>(paid.fields);
    expect(fields.map((item) => item.name)).toEqual(['orderId', 'status', 'details']);
    expect(fields.map((item) => item.type)).toEqual(['DOCUMENT_ID', 'ENUM', 'OBJECT']);
    expect(text(fields[0].enumTypeId)).toBe('');
    expect(fields[1].enumTypeId).toBe('status-kind');
    expect(fields[2].objectTypeId).toBe('order-details');
    expect(named('Order refunded').category).toBe('billing');
    const viaApi = await apiGet<Json>(request, `/api/canonical-metamodels/${metamodelId}/events`);
    expect(list<Json>(viaApi.definitions.eventTypes).map((item) => item.id).sort()).toEqual(types.map((item) => item.id).sort());

    // Guardar y recargar: la interfaz relee del backend y muestra lo persistido.
    await openEvents(page, metamodelId);
    await expect(page.locator('.event-designer')).toContainText('3 event(s)');
    await expect(page.locator('#event-namespace')).toHaveValue(namespace);
    await listItem('Order paid').click();
    await expect(page.locator('#event-type-category')).toHaveValue('VALIDATION');
    await expect(page.locator('#event-type-persistence')).toHaveValue('AUDIT');
    await expect(page.locator('#event-field-type-1')).toHaveValue('ENUM');
    await expect(page.locator('#event-field-enum-1')).toHaveValue('status-kind');
    await expect(page.locator('#event-field-object-2')).toHaveValue('order-details');
    await listItem('Order refunded').click();
    await expect(page.locator('#event-type-custom-category')).toHaveValue('billing');
    await expect(page.locator('.event-dirty-state')).toHaveText(RE.saved);
});
