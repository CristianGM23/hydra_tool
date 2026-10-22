import { expect, test, type Page } from '@playwright/test';
import { uid } from '../support';
import { RE, catalogExists, openGamification, readBindings, readDefinitions, saveGamification, seedMetamodel, tab, text, ALERT } from './helpers';

// Jornada «formulario»: el usuario crea cada tipo de elemento con el formulario, rellena campos, condición, efecto y repetición.
// Oráculo: ficheros JSON del catálogo escritos por el backend real, más el rechazo real del servidor para un límite diario sin máximo.
const KIND_INDEX = { point: 0, level: 1, achievement: 2, challenge: 3, reward: 4, rule: 5 } as const;

async function addFromSidebar(page: Page, kind: keyof typeof KIND_INDEX): Promise<void> {
    await page.locator('.hydra-sidebar-section').nth(KIND_INDEX[kind]).locator('.hydra-sidebar-section-title button').last().click();
}

test('el formulario de gamificación persiste defaults, campos, condición, efecto y repetición y el servidor rechaza el límite diario sin máximo', async ({ page, request }) => {
    const metamodelId = uid('gamform');
    await seedMetamodel(request, metamodelId);
    await openGamification(page, metamodelId);
    await tab(page, RE.form).click();

    // k + m: crear cada tipo de elemento y completar los campos obligatorios.
    await addFromSidebar(page, 'point');
    await page.locator('#gamification-point-name').fill('Form points');
    await page.locator('#gamification-point-id').fill('points:form');
    await page.locator('#gamification-point-minimum').fill('-10');

    await addFromSidebar(page, 'reward');
    await page.locator('#gamification-reward-name').fill('Form reward');
    await page.locator('#gamification-reward-id').fill('reward:form');
    await page.locator('#gamification-reward-payload').fill('{"badge":"form"}');

    // El logro se deja sin tocar: se persisten los valores por defecto del tipo.
    await addFromSidebar(page, 'achievement');
    await expect(page.locator('#gamification-achievement-name')).toHaveValue('New achievement');

    await addFromSidebar(page, 'challenge');
    await page.locator('#gamification-challenge-name').fill('Form challenge');
    await page.locator('#gamification-challenge-id').fill('challenge:form');
    await page.locator('#gamification-challenge-target').fill('3');
    await page.locator('#gamification-challenge-reward').selectOption('reward:form');

    await addFromSidebar(page, 'level');
    await expect(page.locator('#gamification-level-point-type')).toHaveValue('points:form');
    await page.locator('#gamification-level-name').fill('Form level');
    await page.locator('#gamification-level-id').fill('level:form');
    await page.locator('#gamification-level-minimum').fill('25');
    await page.locator('#gamification-level-reward').selectOption('reward:form');

    // Regla 1 (premio): evento, condición ruta/operador/valor, puntos, reto, recompensa y límite diario SIN máximo todavía.
    await addFromSidebar(page, 'rule');
    await page.locator('#gamification-rule-name').fill('Form rule award');
    await page.locator('#gamification-rule-id').fill('rule:form-award');
    await page.locator('#gamification-rule-event').selectOption('core.model.element-created');
    // cn: operador y valor solo existen con una ruta; el valor se oculta para «existe».
    await expect(page.locator('#gamification-rule-operator')).toHaveCount(0);
    await expect(page.locator('#gamification-rule-expected')).toHaveCount(0);
    await page.locator('#gamification-rule-path').fill('payload.severity');
    await expect(page.locator('#gamification-rule-operator')).toBeVisible();
    await page.locator('#gamification-rule-operator').selectOption('EXISTS');
    await expect(page.locator('#gamification-rule-expected')).toHaveCount(0);
    await page.locator('#gamification-rule-operator').selectOption('EQUALS');
    await page.locator('#gamification-rule-expected').fill('"ERROR"');
    await page.locator('#gamification-rule-points').fill('4');
    await page.locator('#gamification-rule-challenge').selectOption('challenge:form');
    await page.locator('#gamification-rule-reward').selectOption('reward:form');
    // rp: la espera solo aparece con «espera»; el máximo diario solo con «límite diario».
    await expect(page.locator('#gamification-rule-cooldown')).toHaveCount(0);
    await expect(page.locator('#gamification-rule-max-day')).toHaveCount(0);
    await page.locator('#gamification-rule-repeat').selectOption('DAILY_LIMIT');
    await expect(page.locator('#gamification-rule-max-day')).toBeVisible();
    await expect(page.locator('#gamification-rule-cooldown')).toHaveCount(0);
    await page.locator('#gamification-rule-max-actor').fill('7');

    // Regla 2 (penalización): sin ruta no hay operador; al elegir penalización se vacían logro, reto y recompensa.
    await addFromSidebar(page, 'rule');
    await page.locator('#gamification-rule-name').fill('Form rule penalty');
    await page.locator('#gamification-rule-id').fill('rule:form-penalty');
    await expect(page.locator('#gamification-rule-operator')).toHaveCount(0);
    await page.locator('#gamification-rule-points').fill('3');
    await page.locator('#gamification-rule-reward').selectOption('reward:form');
    await expect(page.locator('#gamification-rule-reward')).toHaveValue('reward:form');
    await page.locator('#gamification-rule-effect').selectOption('PENALTY');
    await expect(page.locator('#gamification-rule-reward')).toHaveCount(0);
    await expect(page.locator('#gamification-rule-achievement')).toHaveCount(0);
    await expect(page.locator('#gamification-rule-challenge')).toHaveCount(0);

    // Regla 3 (espera): la espera aparece y el máximo diario no.
    await addFromSidebar(page, 'rule');
    await page.locator('#gamification-rule-name').fill('Form rule cooldown');
    await page.locator('#gamification-rule-id').fill('rule:form-cooldown');
    await page.locator('#gamification-rule-repeat').selectOption('COOLDOWN');
    await expect(page.locator('#gamification-rule-cooldown')).toBeVisible();
    await expect(page.locator('#gamification-rule-max-day')).toHaveCount(0);
    await page.locator('#gamification-rule-cooldown').fill('60');

    // Rechazo real (negativo): el límite diario de la regla 1 no tiene máximo; el servidor lo rechaza y no escribe nada en disco.
    const rejected = page.waitForResponse((response) => response.request().method() === 'POST' && /\/gamification$/.test(new URL(response.url()).pathname));
    await page.locator('.gamification-header').getByRole('button', { name: RE.changes }).click();
    await page.getByRole('group', { name: RE.changes }).getByRole('button', { name: RE.save }).click();
    const rejection = await rejected;
    expect(rejection.status()).toBeGreaterThanOrEqual(400);
    expect(rejection.status()).toBeLessThan(500);
    await expect(page.locator(ALERT)).toBeVisible();
    expect(catalogExists(metamodelId)).toBe(false);

    // Corrección: máximo diario 5 en la regla 1 y guardado correcto.
    await page.locator('.hydra-designer-list-item').filter({ hasText: 'Form rule award' }).click();
    await page.locator('#gamification-rule-max-day').fill('5');
    await saveGamification(page);

    // Oráculo independiente: lo escrito por el backend.
    const definitions = readDefinitions(metamodelId);
    const bindings = readBindings(metamodelId);
    const byId = <T extends { id: string }>(items: T[], id: string): T => items.find((item) => item.id === id) as T;
    expect(byId(definitions.pointTypes, 'points:form')).toMatchObject({ name: 'Form points', minimumBalance: -10 });
    expect(byId(definitions.rewards, 'reward:form')).toMatchObject({ name: 'Form reward', payload: { badge: 'form' } });
    const defaultAchievement = definitions.achievements.find((item: { name: string }) => item.name === 'New achievement');
    expect(defaultAchievement).toBeDefined();
    expect(defaultAchievement.hidden === true).toBe(false);
    expect(text(defaultAchievement.rewardId)).toBe('');
    expect(byId(definitions.challenges, 'challenge:form')).toMatchObject({ name: 'Form challenge', targetCount: 3, rewardId: 'reward:form' });
    expect(byId(definitions.levels, 'level:form')).toMatchObject({ name: 'Form level', pointTypeId: 'points:form', minimumPoints: 25, rewardId: 'reward:form' });

    const award = byId(bindings.rules, 'rule:form-award');
    expect(award).toMatchObject({ eventTypeId: 'core.model.element-created', conditionPath: 'payload.severity', operator: 'EQUALS', expectedValue: 'ERROR', pointsTypeId: 'points:form', points: 4, challengeId: 'challenge:form', rewardId: 'reward:form', effectKind: 'AWARD', repeatPolicy: 'DAILY_LIMIT', maxAwardsPerDay: 5, maxAwardsPerActor: 7 });
    const penalty = byId(bindings.rules, 'rule:form-penalty');
    expect(penalty).toMatchObject({ effectKind: 'PENALTY', points: -3, pointsTypeId: 'points:form' });
    expect(text(penalty.rewardId)).toBe('');
    expect(text(penalty.achievementId)).toBe('');
    expect(text(penalty.challengeId)).toBe('');
    expect(text(penalty.conditionPath)).toBe('');
    const cooldown = byId(bindings.rules, 'rule:form-cooldown');
    expect(cooldown).toMatchObject({ repeatPolicy: 'COOLDOWN', cooldownSeconds: 60 });
    expect(cooldown.maxAwardsPerDay ?? 0).toBe(0);

    // El servidor es la otra barrera: una penalización con recompensa se rechaza con su código.
    const tampered = { definitions, bindings: { ...bindings, rules: bindings.rules.map((rule: { id: string }) => rule.id === 'rule:form-penalty' ? { ...rule, rewardId: 'reward:form' } : rule) } };
    const check = await request.post(`/api/canonical-metamodels/${metamodelId}/gamification/check`, { data: tampered });
    expect(check.ok()).toBeTruthy();
    const report = await check.json();
    expect(report.diagnostics.map((item: { code: string }) => item.code)).toContain('PENALTY_CANNOT_GRANT_REWARD');
});
