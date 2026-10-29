import { expect, test } from '@playwright/test';
import { uid } from '../support';
import { ALERT, RE, STATUS, buildModels, dismissAlert, expectSaveDisabled, getFeedback, getGamification, openGamification, postEvent, readBindings, saveGamification, seedGamification, seedMetamodel, tab, watchWrites } from './helpers';

// Jornada «JSON fuente»: el usuario edita el JSON de la pestaña Origen, aplica y guarda. Oráculo: ninguna petición de escritura sale mientras se
// rechaza (observación pasiva de peticiones), el catálogo del backend no cambia, y tras guardar el JSON de disco y la API llevan las anotaciones.
test('la pestaña de origen rechaza JSON inválido sin tocar el servidor y guarda en disco el JSON válido', async ({ page, request }) => {
    const metamodelId = uid('gamsource');
    await seedMetamodel(request, metamodelId);
    const models = buildModels(metamodelId, {
        points: [{ id: 'points:src', name: 'Source points' }],
        rules: [{ id: 'rule:src', name: 'Source rule', pointsTypeId: 'points:src', points: 2 }],
    });
    await seedGamification(request, metamodelId, models);
    const seeded = await getGamification(request, metamodelId);
    const writes = watchWrites(page);

    await openGamification(page, metamodelId);
    await tab(page, RE.source).click();
    const editor = page.locator('.gamification-source textarea');
    await expect(editor).toHaveValue(/rule:src/); // el JSON viene del catálogo real
    const current = JSON.parse(await editor.inputValue());
    const apply = page.locator('.gamification-source').getByRole('button', { name: RE.apply });
    const stillUntouched = async (): Promise<void> => {
        expect(writes.saves()).toEqual([]);
        const live = await getGamification(request, metamodelId);
        expect(live.definitions).toEqual(seeded.definitions);
        expect(live.bindings).toEqual(seeded.bindings);
        await expectSaveDisabled(page, true);
    };

    // lc: un metamodelId que no coincide se rechaza.
    const foreign = JSON.parse(JSON.stringify(current));
    foreign.definitions.metamodelId = 'other-metamodel';
    await editor.fill(JSON.stringify(foreign, null, 2));
    await apply.click();
    await expect(page.locator(ALERT)).toBeVisible();
    await stillUntouched();
    await dismissAlert(page);

    // lc: una regla con error bloqueante (premio por «arreglo rápido aplicado») se rechaza.
    const blocking = JSON.parse(JSON.stringify(current));
    blocking.bindings.rules[0].eventTypeId = 'core.quickfix.applied';
    await editor.fill(JSON.stringify(blocking, null, 2));
    await apply.click();
    await expect(page.locator(ALERT)).toBeVisible();
    await stillUntouched();
    await dismissAlert(page);

    // ap: texto que no es JSON.
    await editor.fill('{ definitions: ');
    await apply.click();
    await expect(page.locator(ALERT)).toBeVisible();
    await stillUntouched();
    await dismissAlert(page);

    // ed + ap: JSON válido con anotaciones de aviso por regla; se aplica (solo borrador) y el servidor sigue igual hasta guardar.
    const good = JSON.parse(JSON.stringify(current));
    good.bindings.annotations = { ...(good.bindings.annotations ?? {}), 'hydra.feedback.rule.rule:src.surfaces': 'dialog', 'hydra.feedback.rule.rule:src.durationMillis': '5', 'hydra.feedback.rule.rule:src.enabled': 'true' };
    await editor.fill(JSON.stringify(good, null, 2));
    await apply.click();
    await expect(page.locator(STATUS)).toContainText(RE.applied);
    await expect(page.locator(ALERT)).toHaveCount(0);
    expect(writes.saves()).toEqual([]);
    expect((await getGamification(request, metamodelId)).bindings.annotations ?? {}).toEqual(seeded.bindings.annotations ?? {});
    await expectSaveDisabled(page, false);

    // sv: Guardar escribe el JSON aplicado en disco y en la API real.
    await saveGamification(page);
    const annotations = readBindings(metamodelId).annotations;
    expect(annotations).toMatchObject({ 'hydra.feedback.rule.rule:src.surfaces': 'dialog', 'hydra.feedback.rule.rule:src.durationMillis': '5', 'hydra.feedback.rule.rule:src.enabled': 'true' });
    const live = await getGamification(request, metamodelId);
    expect(live.bindings.annotations['hydra.feedback.rule.rule:src.surfaces']).toBe('dialog');
    expect(live.definitions.pointTypes.map((item: { id: string }) => item.id)).toEqual(['points:src']);

    // Los ajustes por regla gobiernan el aviso real siguiente (la duración 5 ms se acota al mínimo de 1000 ms).
    const eventId = `event:src-${uid('a')}`;
    await postEvent(request, metamodelId, { id: eventId, actorId: 'actor:source' });
    const notice = (await getFeedback(request, metamodelId, 'actor:source')).find((item) => item.kind === 'POINTS_AWARDED' && item.sourceEventId === eventId);
    expect(notice.presentation.surfaces).toEqual(['DIALOG']);
    expect(notice.presentation.durationMillis).toBe(1000);

    // Guardar y recargar: la pestaña de origen vuelve a mostrar las anotaciones persistidas.
    await openGamification(page, metamodelId);
    await tab(page, RE.source).click();
    await expect(page.locator('.gamification-source textarea')).toHaveValue(/hydra\.feedback\.rule\.rule:src\.surfaces/);
});
