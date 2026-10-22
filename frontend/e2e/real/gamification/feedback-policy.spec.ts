import { expect, test } from '@playwright/test';
import { uid } from '../support';
import { RE, buildModels, getFeedback, openGamification, postEvent, readDefinitions, saveGamification, seedGamification, seedMetamodel, tab } from './helpers';

// Jornada «política de avisos»: el usuario ajusta superficies, duración, máximo visible, ventanas y detalle en el editor, guarda el catálogo y
// el siguiente aviso real lleva esa presentación. Oráculo: anotaciones del JSON escrito por el backend y avisos que devuelve el runtime real.
test('la política de avisos editada en la interfaz se guarda en disco y gobierna los avisos siguientes', async ({ page, request }) => {
    const metamodelId = uid('gampolicy');
    await seedMetamodel(request, metamodelId);
    const models = buildModels(metamodelId, {
        points: [{ id: 'points:policy', name: 'Policy points' }],
        rules: [{ id: 'rule:policy', name: 'Policy rule', pointsTypeId: 'points:policy', points: 5, repeatPolicy: 'ONCE' }],
    });
    await seedGamification(request, metamodelId, models);
    const actor = 'actor:policy-one';
    const granted = `event:pol-${uid('a')}`;
    const refused = `event:pol-${uid('b')}`;
    await postEvent(request, metamodelId, { id: granted, actorId: actor });
    await postEvent(request, metamodelId, { id: refused, actorId: actor }); // segunda aplicación de una regla «una vez»: rechazada

    // Línea base (sin política guardada): valores por defecto y rechazos ocultos.
    const baseline = await getFeedback(request, metamodelId, actor);
    const baselineAward = baseline.find((item) => item.kind === 'POINTS_AWARDED' && item.sourceEventId === granted);
    expect(baselineAward.presentation.durationMillis).toBe(4000);
    expect(baselineAward.presentation.maxVisible).toBe(3);
    expect(baselineAward.presentation.showPoints).toBe(true);
    expect([...baselineAward.presentation.surfaces].sort()).toEqual(['ACTIVITY', 'TOAST']);
    expect(baseline.some((item) => item.kind === 'APPLICATION_REJECTED')).toBe(false);

    await openGamification(page, metamodelId);
    await tab(page, RE.form).click();
    await expect(page.locator('#gamification-feedback-surfaces')).toBeVisible();
    await expect(page.locator('.hydra-designer-list-item').filter({ hasText: 'Policy rule' })).toBeVisible(); // catálogo cargado del backend

    // sf + mx + dt: superficies, duración fuera de rango, máximo visible, ventanas y detalle.
    await page.locator('#gamification-feedback-surfaces').fill('TOAST,STATUS');
    await page.locator('#gamification-feedback-duration').fill('99999');
    await page.locator('#gamification-feedback-max').fill('2');
    await page.locator('#gamification-feedback-dedupe').fill('10');
    await page.locator('#gamification-feedback-aggregate').fill('2000');
    await page.locator('#gamification-feedback-points').uncheck();
    await page.locator('#gamification-feedback-progress').uncheck();
    await page.locator('#gamification-feedback-rejected').check();
    await expect(page.locator('#gamification-feedback-duration')).toHaveValue('99999');
    await saveGamification(page);

    // sv: las anotaciones viajan con las definiciones en el JSON del backend (el valor fuera de rango se guarda tal cual).
    const saved = readDefinitions(metamodelId).annotations;
    expect(saved).toMatchObject({
        'hydra.feedback.surfaces': 'TOAST,STATUS', 'hydra.feedback.durationMillis': '99999', 'hydra.feedback.maxVisible': '2',
        'hydra.feedback.dedupeWindowSeconds': '10', 'hydra.feedback.aggregateWindowMillis': '2000',
        'hydra.feedback.showPoints': 'false', 'hydra.feedback.showProgress': 'false', 'hydra.feedback.includeRejected': 'true',
    });

    // art: la política guardada gobierna los avisos del runtime real; la duración se acota a 30 s y ahora se ve el rechazo.
    const after = await getFeedback(request, metamodelId, actor);
    const award = after.find((item) => item.kind === 'POINTS_AWARDED' && item.sourceEventId === granted);
    expect([...award.presentation.surfaces].sort()).toEqual(['STATUS', 'TOAST']);
    expect(award.presentation).toMatchObject({ durationMillis: 30000, maxVisible: 2, dedupeWindowSeconds: 10, aggregateWindowMillis: 2000, showPoints: false, showProgress: false });
    const rejection = after.find((item) => item.kind === 'APPLICATION_REJECTED' && item.sourceEventId === refused);
    expect(rejection).toBeDefined();
    expect(rejection.reason).toBe('Rule can only be applied once');

    // sf (valor válido): 2 s dentro del rango; se guarda de nuevo y un evento nuevo de otra persona lo lleva en su aviso.
    await page.locator('#gamification-feedback-duration').fill('2000');
    await saveGamification(page);
    expect(readDefinitions(metamodelId).annotations['hydra.feedback.durationMillis']).toBe('2000');
    const next = `event:pol-${uid('c')}`;
    await postEvent(request, metamodelId, { id: next, actorId: 'actor:policy-two' });
    const fresh = (await getFeedback(request, metamodelId, 'actor:policy-two')).find((item) => item.kind === 'POINTS_AWARDED' && item.sourceEventId === next);
    expect(fresh.presentation.durationMillis).toBe(2000);
    expect([...fresh.presentation.surfaces].sort()).toEqual(['STATUS', 'TOAST']);
    expect(fresh.presentation.showPoints).toBe(false);

    // Tras recargar, el editor muestra lo persistido.
    await openGamification(page, metamodelId);
    await tab(page, RE.form).click();
    await expect(page.locator('#gamification-feedback-duration')).toHaveValue('2000');
    await expect(page.locator('#gamification-feedback-surfaces')).toHaveValue('TOAST,STATUS');
    await expect(page.locator('#gamification-feedback-points')).not.toBeChecked();
    await expect(page.locator('#gamification-feedback-rejected')).toBeChecked();
});
