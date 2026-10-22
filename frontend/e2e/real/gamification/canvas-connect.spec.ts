import { expect, test } from '@playwright/test';
import { uid } from '../support';
import { ALERT, RE, addFromPalette, arrangeCanvas, catalogExists, connect, connectByDrag, dismissAlert, expectSaveDisabled, openGamification, readBindings, readDefinitions, saveGamification, seedMetamodel, selectNode, tab, text, watchWrites } from './helpers';

// Jornada «lienzo»: el usuario coloca elementos en el diagrama, conecta, intenta una conexión prohibida y quita una relación.
// Oráculo: los JSON que el backend real escribió en disco (la conexión escribe la referencia en el campo del elemento).
test('el lienzo de gamificación escribe, rechaza y quita referencias, y el backend las persiste', async ({ page, request }) => {
    const metamodelId = uid('gamcanvas');
    await seedMetamodel(request, metamodelId);
    const writes = watchWrites(page);
    await openGamification(page, metamodelId);
    await tab(page, RE.diagram).click();

    // n: añadir regla, tipos de puntos, nivel, logro, reto y recompensa desde la paleta (la regla se crea antes que los puntos: sin referencia de puntos).
    await addFromPalette(page, 'rule', 'Canvas rule', 'rule:canvas');
    await addFromPalette(page, 'point', 'Points A', 'points:a');
    await addFromPalette(page, 'point', 'Points B', 'points:b');
    await addFromPalette(page, 'level', 'Canvas level', 'level:canvas');
    await addFromPalette(page, 'achievement', 'Canvas achievement', 'achievement:canvas');
    await addFromPalette(page, 'challenge', 'Canvas challenge', 'challenge:canvas');
    await addFromPalette(page, 'reward', 'Canvas reward', 'reward:canvas');

    // dr + ty (conexiones permitidas): regla->puntos B, puntos B->nivel (sustituye al tipo por defecto «puntos A»), nivel->recompensa, regla->logro, regla->recompensa.
    await connect(page, 'Canvas rule', 'Points B');
    await connect(page, 'Points B', 'Canvas level');
    await connect(page, 'Canvas level', 'Canvas reward');
    await connect(page, 'Canvas rule', 'Canvas achievement');
    await connect(page, 'Canvas rule', 'Canvas reward');
    await expect(page.locator(ALERT)).toHaveCount(0);

    // ty (rechazo): puntos -> regla no está en la matriz de conexiones. El diseñador avisa y no envía nada al servidor.
    await connect(page, 'Points A', 'Canvas rule');
    await expect(page.locator(ALERT)).toBeVisible();
    expect(writes.saves()).toEqual([]);
    expect(catalogExists(metamodelId)).toBe(false);
    await dismissAlert(page);

    // rm: se quita la relación regla->recompensa desde el inspector de relaciones (antes existe en la lista de conexiones de la regla).
    await selectNode(page, 'Canvas rule');
    const reward = page.locator('.gamification-connection-summary button').filter({ hasText: 'Canvas reward' });
    await expect(reward).toHaveCount(1);
    await reward.click();
    await page.getByRole('button', { name: RE.removeRelation }).click();
    await expectSaveDisabled(page, false);
    await selectNode(page, 'Canvas rule');
    await expect(page.locator('.gamification-connection-summary button').filter({ hasText: 'Canvas reward' })).toHaveCount(0);

    // Un único guardado: catálogo y diagrama. La regla queda seleccionada: su formulario debe seguir pintándose tras guardar.
    await selectNode(page, 'Canvas rule');
    await saveGamification(page);
    await expect(page.locator('#gamification-rule-path')).toBeVisible();

    // Oráculo independiente: el JSON escrito por el backend contiene las referencias creadas por las conexiones y no la quitada.
    const savedDefinitions = readDefinitions(metamodelId);
    const savedBindings = readBindings(metamodelId);
    const level = savedDefinitions.levels.find((item: { id: string }) => item.id === 'level:canvas');
    const rule = savedBindings.rules.find((item: { id: string }) => item.id === 'rule:canvas');
    expect(level.pointTypeId).toBe('points:b');
    expect(level.rewardId).toBe('reward:canvas');
    expect(rule.pointsTypeId).toBe('points:b');
    expect(rule.achievementId).toBe('achievement:canvas');
    expect(text(rule.rewardId)).toBe('');
    expect(text(rule.challengeId)).toBe('');
    expect(savedDefinitions.pointTypes.map((item: { id: string }) => item.id).sort()).toEqual(['points:a', 'points:b']);
    expect(savedDefinitions.challenges.map((item: { id: string }) => item.id)).toEqual(['challenge:canvas']);
    expect(savedDefinitions.rewards.map((item: { id: string }) => item.id)).toEqual(['reward:canvas']);
    expect(savedDefinitions.achievements.map((item: { id: string }) => item.id)).toEqual(['achievement:canvas']);
    expect(catalogExists(metamodelId)).toBe(true);
    // El diagrama persistido por el backend lleva las conexiones vigentes y no la quitada.
    const diagramResponse = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification/diagram`);
    expect(diagramResponse.ok()).toBeTruthy();
    const edgeIds = (await diagramResponse.json()).diagram.edges.map((edge: { edgeId: string }) => edge.edgeId);
    expect(edgeIds).toContain('rule-points:rule:canvas');
    expect(edgeIds).toContain('level-reward:level:canvas');
    expect(edgeIds).not.toContain('rule-reward:rule:canvas');

    // Guardar y recargar: la interfaz relee del backend y muestra las relaciones persistidas (y no la quitada).
    await openGamification(page, metamodelId);
    await tab(page, RE.diagram).click();
    await selectNode(page, 'Canvas rule');
    const relations = page.locator('.gamification-connection-summary button');
    await expect(relations.filter({ hasText: 'Points B' })).toHaveCount(1);
    await expect(relations.filter({ hasText: 'Canvas achievement' })).toHaveCount(1);
    await expect(relations.filter({ hasText: 'Canvas reward' })).toHaveCount(0);
});

// Jornada «lienzo con arrastre real»: igual que la anterior, pero las conexiones se hacen arrastrando el ratón entre puertos (sin eventos de clic
// sintéticos sobre los puertos), incluida una conexión prohibida. Oráculo: los JSON que el backend real escribió y el diagrama guardado.
test('el lienzo de gamificación conecta arrastrando el ratón entre puertos, rechaza la conexión prohibida y el backend persiste las referencias', async ({ page, request }) => {
    const metamodelId = uid('gamdrag');
    // Ventana amplia: con 1280x720 el lienzo (entre explorador, paleta e inspector) mide ~160 px y los puertos quedan fuera o tapados.
    await page.setViewportSize({ width: 1920, height: 1080 });
    await seedMetamodel(request, metamodelId);
    const writes = watchWrites(page);
    await openGamification(page, metamodelId);
    await tab(page, RE.diagram).click();

    await addFromPalette(page, 'rule', 'Drag rule', 'rule:drag');
    await addFromPalette(page, 'point', 'Drag points X', 'points:x');
    await addFromPalette(page, 'point', 'Drag points Y', 'points:y');
    await addFromPalette(page, 'level', 'Drag level', 'level:drag');
    await addFromPalette(page, 'reward', 'Drag reward', 'reward:drag');
    await arrangeCanvas(page);

    // dr + ty (permitidas, arrastrando): regla->puntos Y, puntos Y->nivel (sustituye al tipo por defecto «puntos X»), nivel->recompensa.
    await connectByDrag(page, 'Drag rule', 'Drag points Y');
    await expect(page.locator(ALERT)).toHaveCount(0);
    await selectNode(page, 'Drag rule');
    await expect(page.locator('.gamification-connection-summary button').filter({ hasText: 'Drag points Y' })).toHaveCount(1);
    await connectByDrag(page, 'Drag points Y', 'Drag level');
    await connectByDrag(page, 'Drag level', 'Drag reward');
    await expect(page.locator(ALERT)).toHaveCount(0);

    // ty (rechazo, arrastrando): puntos -> regla no está en la matriz; el diseñador avisa y no envía nada al servidor.
    await connectByDrag(page, 'Drag points Y', 'Drag rule');
    await expect(page.locator(ALERT)).toBeVisible();
    expect(writes.saves()).toEqual([]);
    expect(catalogExists(metamodelId)).toBe(false);
    await dismissAlert(page);

    await selectNode(page, 'Drag rule');
    await saveGamification(page);

    // wr: el JSON escrito por el backend lleva las referencias de las conexiones arrastradas y nada de la prohibida.
    const savedDefinitions = readDefinitions(metamodelId);
    const savedBindings = readBindings(metamodelId);
    const level = savedDefinitions.levels.find((item: { id: string }) => item.id === 'level:drag');
    const rule = savedBindings.rules.find((item: { id: string }) => item.id === 'rule:drag');
    expect(level.pointTypeId).toBe('points:y');
    expect(level.rewardId).toBe('reward:drag');
    expect(rule.pointsTypeId).toBe('points:y');
    expect(text(rule.achievementId)).toBe('');
    expect(text(rule.challengeId)).toBe('');
    expect(text(rule.rewardId)).toBe('');
    expect(savedDefinitions.pointTypes.map((item: { id: string }) => item.id).sort()).toEqual(['points:x', 'points:y']);
    const diagramResponse = await request.get(`/api/canonical-metamodels/${metamodelId}/gamification/diagram`);
    expect(diagramResponse.ok()).toBeTruthy();
    const edgeIds = (await diagramResponse.json()).diagram.edges.map((edge: { edgeId: string }) => edge.edgeId);
    expect(edgeIds).toContain('rule-points:rule:drag');
    expect(edgeIds).toContain('level-reward:level:drag');
});
