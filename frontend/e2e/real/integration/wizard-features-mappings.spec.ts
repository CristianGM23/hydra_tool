import { expect, test } from '@playwright/test';
import { ALERT, anyProfileOnDisk, clickSaveProfile, listProfiles, makeInputsOptional, nextStep, openIntegrations, profileOnDisk, profilesRoute, runToolbar, seedIntegrationMetamodel, waitForProfile, workingTechnology } from './helpers';

// Jornada «asistente de integración»: capacidades (con documentos de entrada autoseleccionados) y mapeos con el anfitrión.
// El perfil lo crea el usuario en el diseñador. Oráculo: GET real del perfil y fichero *.integration.json que escribe el backend.
test('el asistente guarda capacidades, entradas autoseleccionadas y un mapeo; un mapeo incompleto bloquea el guardado', async ({ page, request }) => {
    const mm = await seedIntegrationMetamodel(request, 'intwiz');
    await openIntegrations(page, mm.id);

    // Pasos 1 y 2: identidad y tecnología (se elige una cuyo flujo está implementado para que «Paso siguiente» no quede bloqueado).
    await page.locator('#guided-integration-profile-name').fill('E2E wizard profile');
    await nextStep(page).click();
    const technology = await workingTechnology(request, mm.id);
    if (technology !== undefined) await page.locator('#guided-integration-technology').selectOption(technology);
    await nextStep(page).click();

    // cp · Paso 3: el preajuste de ejecución portátil marca las capacidades y los documentos de entrada se autoseleccionan.
    // (No se sale del asistente: al volver a él se pierden las confirmaciones de los pasos anteriores y «Paso siguiente» queda bloqueado.)
    const validationsCard = page.locator('.integration-guided-input-card', { hasText: /^Validation rules/ });
    await expect(validationsCard.getByRole('checkbox')).not.toBeChecked();
    await page.getByRole('button', { name: 'Apply portable runtime preset', exact: true }).click();
    for (const title of [/^Validation rules/, /^Quick fixes and actions/, /^Reusable conditions/]) {
        await expect(page.locator('.integration-guided-input-card', { hasText: title }).getByRole('checkbox')).toBeChecked();
    }
    await nextStep(page).click();

    // mp · Paso 4: se añade un mapeo con el anfitrión y se completa en el formulario detallado.
    await page.getByRole('button', { name: '+ Mapping', exact: true }).click();
    await page.getByRole('button', { name: 'Edit detailed mapping fields', exact: true }).click();
    await page.locator('#integration-mapping-document-0').fill(mm.id);
    await page.locator('#integration-mapping-element-0').fill(mm.personId);
    await page.locator('#integration-host-qualified-0').fill('people.Person');
    // Los documentos de entrada autoseleccionados aún no existen en el servidor: se marcan como no obligatorios para que el perfil sea aceptable.
    await makeInputsOptional(page);

    // Puerta: un mapeo sin identificador no se guarda (ni en la API ni en disco).
    await page.locator('#integration-mapping-id-0').fill('');
    await runToolbar(page, 'Changes', 'Save profile');
    await expect(page.locator(ALERT)).toBeVisible();
    expect(await listProfiles(request, mm.id)).toHaveLength(0);
    expect(anyProfileOnDisk(mm.id)).toBe(false);

    // Corregido, se guarda.
    await page.locator('#integration-mapping-id-0').fill('person-class-mapping');
    await clickSaveProfile(page); // el resultado se contrasta con el servidor (ver nota de clickSaveProfile)

    const profile = await waitForProfile(request, mm.id);
    expect(profile.name).toBe('E2E wizard profile');
    if (technology !== undefined) expect(profile.target.technology).toBe(technology);
    expect(profile.features).toEqual(expect.arrayContaining(['VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'EVENT_FLOW_RUNTIME', 'GAMIFICATION_RUNTIME']));
    const kinds = (profile.artifacts as Array<{ kind: string; documentId: string; required: boolean }>).map((item) => item.kind);
    expect(kinds).toEqual(expect.arrayContaining(['METAMODEL', 'VALIDATIONS', 'ACTIONS', 'EXPRESSIONS']));
    expect(profile.artifacts.find((item: { kind: string }) => item.kind === 'VALIDATIONS')).toMatchObject({ documentId: `${mm.id}-validations`, required: false });
    expect(profile.mappings).toHaveLength(1);
    expect(profile.mappings[0]).toMatchObject({ id: 'person-class-mapping', canonicalDocumentId: mm.id, canonicalElementId: mm.personId, hostReference: { qualifiedName: 'people.Person' } });

    // La comprobación de compatibilidad del backend responde sobre el perfil guardado.
    const check = await request.post(`${profilesRoute(mm.id)}/check`, { data: profile });
    expect(check.ok()).toBe(true);
    expect(Array.isArray((await check.json()).diagnostics)).toBe(true);

    const onDisk = profileOnDisk(profile.id);
    expect(onDisk, 'el backend debe haber escrito el fichero del perfil').toBeDefined();
    expect(onDisk?.mappings[0].canonicalElementId).toBe(mm.personId);
    expect(onDisk?.features).toEqual(expect.arrayContaining(['VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME']));
});
