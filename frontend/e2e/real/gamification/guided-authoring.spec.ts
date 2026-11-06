import { expect, test, type Page } from '@playwright/test';
import { uid } from '../support';
import { ALERT, RE, STATUS, catalogExists, openGamification, readBindings, readDefinitions, runCommand, saveGamification, seedMetamodel, tab, text, watchWrites } from './helpers';

// Jornada «guiada»: el usuario crea puntos, premios, logro, reto y una regla en modo guiado, comprueba, ve que un error bloquea el guardado,
// lo corrige y guarda. Oráculo: respuesta real de la comprobación del servidor, peticiones de escritura observadas y ficheros JSON escritos por el backend.
const actions = (page: Page) => page.locator('.hydra-guided-workflow-actions');
const nextStep = (page: Page) => actions(page).getByRole('button', { name: RE.next });

// Al salir de la pestaña guiada (cada «+ añadir» abre el formulario) la guía pierde las confirmaciones de los pasos: el usuario vuelve al paso 1
// y los reconfirma con «Siguiente» hasta el paso que quiere.
async function guidedAt(page: Page, step: number): Promise<void> {
    await tab(page, RE.guided).click();
    await page.locator('.hydra-guided-stepper button').first().click();
    for (let index = 1; index < step; index += 1) await nextStep(page).click();
    await expect(page.locator('.hydra-guided-current-step header span').first()).toContainText(new RegExp(`${step}\\b`));
}

test('el modo guiado crea el catálogo, bloquea el guardado con errores y guarda en disco lo corregido', async ({ page, request }) => {
    const metamodelId = uid('gamguided');
    await seedMetamodel(request, metamodelId);
    const writes = watchWrites(page);
    await openGamification(page, metamodelId);
    await tab(page, RE.guided).click();

    // p: crear el tipo de puntos (el botón lleva al formulario del elemento nuevo).
    await page.locator('.gamification-guided-cards > header button').click();
    await page.locator('#gamification-point-name').fill('Quality points');
    await page.locator('#gamification-point-id').fill('points:quality');

    // r: recompensa, logro y reto (cada «+ Add» abre el formulario del elemento y se vuelve a la guía).
    await guidedAt(page, 2);
    const grid = page.locator('.gamification-guided-category-grid');
    await grid.locator('section').nth(3).locator('header button').click();
    await page.locator('#gamification-reward-name').fill('Quality badge');
    await page.locator('#gamification-reward-id').fill('reward:badge');
    await guidedAt(page, 2);
    await grid.locator('section').nth(1).locator('header button').click();
    await page.locator('#gamification-achievement-id').fill('achievement:first');
    await guidedAt(page, 2);
    await grid.locator('section').nth(2).locator('header button').click();
    await page.locator('#gamification-challenge-id').fill('challenge:ten');
    await page.locator('#gamification-challenge-target').fill('10');

    // ru: regla ligada a «problema resuelto» que concede puntos y recompensa. Se deja primero con un evento NO verificado para provocar un error.
    await guidedAt(page, 3);
    await page.locator('.gamification-guided-cards > header button').click();
    await page.locator('#gamification-rule-name').fill('Reward resolved problem');
    await page.locator('#gamification-rule-id').fill('rule:resolved-quality');
    await page.locator('#gamification-rule-points').fill('3');
    await page.locator('#gamification-rule-reward').selectOption('reward:badge');
    await page.locator('#gamification-rule-repeat').selectOption('UNLIMITED');
    await page.locator('#gamification-rule-event').selectOption('core.quickfix.applied');

    // d (decisión «¿sin errores?»): con el error, la guía no deja pasar del paso 4, «Comprobar» lo rechaza en local y «Guardar» no envía nada.
    await guidedAt(page, 4);
    await expect(nextStep(page)).toBeDisabled();
    await runCommand(page, RE.quality, RE.check);
    await expect(page.locator(ALERT)).toBeVisible();
    expect(writes.checks()).toEqual([]);
    await runCommand(page, RE.changes, RE.save);
    await expect(page.locator(ALERT)).toBeVisible();
    expect(writes.saves()).toEqual([]);
    expect(catalogExists(metamodelId)).toBe(false);

    // Corrección: el evento vuelve a ser el resultado verificado.
    await tab(page, RE.form).click();
    await page.locator('#gamification-rule-event').selectOption('core.validation.violation-resolved');

    // c: Comprobar muestra los avisos del servidor; se contrasta con la respuesta real y con una segunda consulta independiente.
    const checkResponse = page.waitForResponse((response) => response.request().method() === 'POST' && /\/gamification\/check$/.test(new URL(response.url()).pathname));
    await runCommand(page, RE.quality, RE.check);
    const checked = await checkResponse;
    expect(checked.ok()).toBeTruthy();
    const shown = await checked.json();
    const independent = await request.post(`/api/canonical-metamodels/${metamodelId}/gamification/check`, { data: checked.request().postDataJSON() });
    const reference = await independent.json();
    const codes = (report: { diagnostics: Array<{ code: string; severity: string }> }) => report.diagnostics.map((item) => `${item.severity}:${item.code}`).sort();
    expect(codes(shown)).toEqual(codes(reference));
    expect(codes(reference)).toContain('WARNING:UNBOUNDED_RULE');
    expect(codes(reference).filter((entry) => entry.startsWith('ERROR:'))).toEqual([]);
    await expect(page.locator(STATUS)).toContainText(RE.valid);
    await expect(page.locator('[data-hydra-message-code="UNBOUNDED_RULE"]')).toBeVisible();
    await expect(page.locator(ALERT)).toHaveCount(0);
    expect(catalogExists(metamodelId)).toBe(false);

    // sv: Guardar desde la guía (último paso) y comprobar que el backend escribió definiciones y reglas.
    await guidedAt(page, 5);
    await saveGamification(page, () => actions(page).getByRole('button', { name: RE.save }).click());

    const definitions = readDefinitions(metamodelId);
    const bindings = readBindings(metamodelId);
    expect(definitions.pointTypes.map((item: { id: string; name: string }) => [item.id, item.name])).toEqual([['points:quality', 'Quality points']]);
    expect(definitions.rewards.map((item: { id: string }) => item.id)).toEqual(['reward:badge']);
    expect(definitions.achievements.map((item: { id: string }) => item.id)).toEqual(['achievement:first']);
    expect(definitions.challenges).toMatchObject([{ id: 'challenge:ten', targetCount: 10 }]);
    expect(bindings.rules).toHaveLength(1);
    expect(bindings.rules[0]).toMatchObject({ id: 'rule:resolved-quality', name: 'Reward resolved problem', eventTypeId: 'core.validation.violation-resolved', pointsTypeId: 'points:quality', points: 3, rewardId: 'reward:badge', repeatPolicy: 'UNLIMITED', effectKind: 'AWARD' });
    expect(text(bindings.rules[0].achievementId)).toBe('');
    expect(writes.saves().some((entry) => entry.startsWith('POST') && entry.endsWith('/gamification'))).toBe(true);
    // Ningún error del servidor sobre el catálogo guardado.
    const reloaded = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification`);
    const loaded = await reloaded.json();
    expect(loaded.checkReport.diagnostics.map((item: { code: string }) => item.code)).toContain('UNBOUNDED_RULE');
    // El diagrama también se guardó, con la conexión de la regla a sus puntos.
    const diagramResponse = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification/diagram`);
    expect(diagramResponse.ok()).toBeTruthy();
    expect((await diagramResponse.json()).diagram.edges.map((edge: { edgeId: string }) => edge.edgeId)).toContain('rule-points:rule:resolved-quality');
});
