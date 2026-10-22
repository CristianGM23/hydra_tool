import { expect, test, type Page } from '@playwright/test';
import { createSavedProfile, getProfile, nextStep, profileOnDisk, profilesRoute, runToolbar, saveProfile, seedIntegrationMetamodel, viewTab } from './helpers';

// Jornada «vistas del diseñador de integración»: alternar entre asistente, formulario completo (con informe de compatibilidad) y árbol de estructura,
// elegir nodos del árbol y editar su panel de propiedades. El perfil se guarda desde la interfaz. Oráculo: GET real del perfil, fichero del backend y
// respuestas reales de comprobación y preparación.
const treeNode = (page: Page, titleStart: string) => page.locator(`[aria-label="Integration structure tree"] button.hydra-ui-tree-main[title^="${titleStart}"]`);

test('se alterna entre las vistas y el formulario muestra el informe de compatibilidad que calcula el backend', async ({ page, request }) => {
    const mm = await seedIntegrationMetamodel(request, 'intviews');
    const profile = await createSavedProfile(page, request, mm);

    // sw: elección de vista.
    await expect(page.locator('.hydra-guided-workflow')).toBeVisible();
    await viewTab(page, 'Definition').click();
    await expect(page.locator('.hydra-guided-workflow')).toHaveCount(0);
    await expect(page.locator('#integration-profile-id')).toHaveValue(profile.id);
    await expect(page.locator('#integration-profile-id')).toHaveAttribute('readonly', '');
    await viewTab(page, 'Structure tree').click();
    await expect(page.locator('.integration-structure-workspace')).toBeVisible();
    await expect(page.locator('#integration-profile-id')).toHaveCount(0);
    await viewTab(page, 'Guided').click();
    await expect(page.locator('.hydra-guided-workflow')).toBeVisible();

    // fm: formulario completo con el informe de compatibilidad (por qué / cómo resolver) tras «Analyze».
    await viewTab(page, 'Definition').click();
    await runToolbar(page, 'Quality', 'Analyze');
    const report = page.locator('.integration-report');
    await expect(report).toBeVisible();
    const expected = await request.post(`${profilesRoute(mm.id)}/check`, { data: profile });
    expect(expected.ok()).toBe(true);
    const check = await expected.json();
    await expect(report.locator('> ul').nth(0).locator('> li')).toHaveCount(check.diagnostics.length);
    await expect(report.locator('> ul').nth(1).locator('> li')).toHaveCount(check.artifactResolutions.length);
    expect(check.artifactResolutions.length).toBeGreaterThan(0);
    await expect(report.locator('.integration-resolution-advice').first()).toContainText('Why:');
    await expect(report.locator('.integration-resolution-advice').first()).toContainText(/Next step:|How to resolve it:/);
});

test('elegir nodos del árbol abre su panel y editar propiedades llega al perfil guardado', async ({ page, request }) => {
    const mm = await seedIntegrationMetamodel(request, 'inttree');
    const profile = await createSavedProfile(page, request, mm); // incluye el mapeo «person-mapping»

    // tr: elegir un nodo de cada tipo.
    await viewTab(page, 'Structure tree').click();
    const properties = page.locator('.designer-structure-properties-pane');
    await treeNode(page, 'Target project and technology').click();
    await expect(properties.getByRole('heading', { name: 'Target properties' })).toBeVisible();
    await expect(properties.locator('#structure-integration-project')).toHaveValue(profile.target.projectId);
    await treeNode(page, 'Host integration').click();
    await expect(properties.getByRole('heading', { name: 'Host integration' })).toBeVisible();
    await expect(properties.locator('#structure-generated-root')).toHaveValue(profile.injectionPolicy.generatedRoot);
    await treeNode(page, 'Enabled features').click();
    await expect(properties.getByRole('heading', { name: 'Enabled features' })).toBeVisible();
    await treeNode(page, 'EVENT_DEFINITIONS').click();
    await expect(properties.getByRole('heading', { name: 'HYDRA input document' })).toBeVisible();
    await expect(properties.locator('#structure-artifact-document')).toHaveValue(`${mm.id}-events`);
    await treeNode(page, 'person-mapping').first().click();
    await expect(properties.getByRole('heading', { name: 'Host mapping' })).toBeVisible();
    await expect(properties.locator('#structure-mapping-qualified')).toHaveValue('people.Person');

    // pn: editar propiedades en el panel y guardar.
    await properties.locator('#structure-mapping-qualified').fill('people.EditedPerson');
    await treeNode(page, 'Target project and technology').click();
    await properties.locator('#structure-integration-project').fill('e2e-edited-host');
    await saveProfile(page);

    const saved = await getProfile(request, mm.id, profile.id);
    expect(saved.target.projectId).toBe('e2e-edited-host');
    expect(saved.mappings[0].hostReference.qualifiedName).toBe('people.EditedPerson');
    const onDisk = profileOnDisk(profile.id);
    expect(onDisk?.target.projectId).toBe('e2e-edited-host');
    expect(onDisk?.mappings[0].hostReference.qualifiedName).toBe('people.EditedPerson');
});

test('el panel de preparación del asistente coincide con la preparación que calcula el backend', async ({ page, request }) => {
    const mm = await seedIntegrationMetamodel(request, 'intready');
    const profile = await createSavedProfile(page, request, mm);
    const response = await request.get(`${profilesRoute(mm.id)}/${encodeURIComponent(profile.id)}/technology/readiness`);
    expect(response.ok()).toBe(true);
    const readiness = await response.json();

    // El asistente (paso «Tecnología y destino») muestra el resumen de preparación del perfil guardado.
    await nextStep(page).click();
    const summary = page.locator('.integration-technology-delivery-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText(`Generated files: ${readiness.generatedFileCount} · Native verification steps: ${readiness.nativeVerificationStepCount}`);
    for (const state of new Set<string>((readiness.actions as Array<{ state: string }>).map((action) => action.state))) {
        const cssState = state.toLowerCase().replaceAll('_', '-');
        expect(await page.locator(`.integration-technology-stage-grid article.readiness-${cssState}`).count()).toBeGreaterThan(0);
    }
});

// Etapa «sw» (elección de vista): formulario completo y árbol de estructura son dos vistas de UN mismo borrador. Elegir una u otra no escribe
// nada en el servidor; lo editado en una se ve en la otra y solo «Save profile» lo envía al backend real.
test('elegir la vista del formulario o la del árbol comparte el mismo borrador y solo guardar lo envía al servidor', async ({ page, request }) => {
    const mm = await seedIntegrationMetamodel(request, 'intswitch');
    const profile = await createSavedProfile(page, request, mm);
    const originalProject = profile.target.projectId as string;
    const properties = page.locator('.designer-structure-properties-pane');
    const draftProject = 'e2e-draft-host';
    expect(originalProject).not.toBe(draftProject);

    // Cada vista muestra el proyecto anfitrión que el servidor tiene guardado.
    await viewTab(page, 'Definition').click();
    await expect(page.locator('#integration-host-project')).toHaveValue(originalProject);
    await viewTab(page, 'Structure tree').click();
    await treeNode(page, 'Target project and technology').click();
    await expect(properties.locator('#structure-integration-project')).toHaveValue(originalProject);

    // Se edita en el formulario y se cambia a la vista de árbol: el borrador es el mismo, y el servidor sigue con el valor guardado.
    await viewTab(page, 'Definition').click();
    await page.locator('#integration-host-project').fill(draftProject);
    await expect(page.locator('.integration-dirty-state')).not.toHaveText('Saved');
    await viewTab(page, 'Structure tree').click();
    await treeNode(page, 'Target project and technology').click();
    await expect(properties.locator('#structure-integration-project')).toHaveValue(draftProject);
    await viewTab(page, 'Guided').click();
    await viewTab(page, 'Definition').click();
    await expect(page.locator('#integration-host-project')).toHaveValue(draftProject);
    expect((await getProfile(request, mm.id, profile.id)).target.projectId).toBe(originalProject);
    expect(profileOnDisk(profile.id)?.target.projectId).toBe(originalProject);

    // Solo la orden de guardar lleva el borrador al servidor.
    await saveProfile(page);
    expect((await getProfile(request, mm.id, profile.id)).target.projectId).toBe(draftProject);
    expect(profileOnDisk(profile.id)?.target.projectId).toBe(draftProject);
});
