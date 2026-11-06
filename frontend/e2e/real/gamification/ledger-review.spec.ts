import { expect, test } from '@playwright/test';
import { eventually, readJson, uid } from '../support';
import { RE, buildModels, files, getFeedback, getRuntime, openGamification, postEvent, seedGamification, seedMetamodel, tab } from './helpers';

// Jornada «ledger»: el usuario revisa en la vista de ejecución lo que el motor real concedió y pulsa Actualizar tras una concesión nueva.
// Oráculo: GET real del runtime y del feedback, y el fichero de libro mayor que escribió el backend.
test('la vista de ledger muestra personas, avisos y filas del servidor y Actualizar incorpora una concesión nueva', async ({ page, request }) => {
    const metamodelId = uid('gamledger');
    await seedMetamodel(request, metamodelId);
    await seedGamification(request, metamodelId, buildModels(metamodelId, {
        points: [{ id: 'points:ledger', name: 'Ledger points' }],
        rules: [{ id: 'rule:ledger-award', name: 'Ledger award', pointsTypeId: 'points:ledger', points: 5 }],
    }));
    const first = `event:led-${uid('a')}`;
    const second = `event:led-${uid('b')}`;
    await postEvent(request, metamodelId, { id: first, actorId: 'actor:ana' });
    await postEvent(request, metamodelId, { id: second, actorId: 'actor:ben' });

    await openGamification(page, metamodelId);
    await tab(page, RE.ledger).click();

    // rd: personas, avisos y tabla coinciden con lo que devuelve el servidor.
    const rows = page.locator('.gamification-ledger table tbody tr');
    const actors = page.locator('.gamification-actor-grid article');
    const feedbackCount = page.locator('.gamification-feedback-ledger > header > span');
    const runtime = await getRuntime(request, metamodelId);
    const feedback = await getFeedback(request, metamodelId);
    expect(runtime.ledger.entries).toHaveLength(2);
    await expect(rows).toHaveCount(runtime.ledger.entries.length);
    await expect(actors).toHaveCount(Object.keys(runtime.state.actors).length);
    await expect(actors.filter({ hasText: 'actor:ana' })).toContainText('points:ledger: 5');
    await expect(actors.filter({ hasText: 'actor:ben' })).toContainText('points:ledger: 5');
    await expect(feedbackCount).toHaveText(String(feedback.length));
    expect(feedback.length).toBeGreaterThan(0);
    await expect(rows.filter({ hasText: first })).toContainText('rule:ledger-award');
    await expect(rows.filter({ hasText: first })).toContainText('actor:ana');
    await expect(rows.filter({ hasText: second })).toContainText('actor:ben');
    // Oráculo de disco: el libro mayor escrito por el backend tiene las mismas entradas que la tabla.
    const onDisk = readJson<{ entries: Array<{ sourceEventId: string; actorId: string; pointsDelta: number }> }>(files(metamodelId).ledger);
    expect(onDisk.entries.map((entry) => entry.sourceEventId).sort()).toEqual([first, second].sort());

    // rf: se concede otro evento real por la API; hasta pulsar Actualizar la tabla no cambia, y después aparece la fila nueva arriba.
    const third = `event:led-${uid('c')}`;
    await postEvent(request, metamodelId, { id: third, actorId: 'actor:ana' });
    await expect(rows).toHaveCount(2);
    await expect(rows.filter({ hasText: third })).toHaveCount(0);
    await page.locator('.gamification-ledger > header').getByRole('button', { name: RE.refresh }).click();
    const updated = await eventually(() => getRuntime(request, metamodelId), (value) => value.ledger.entries.length === 3);
    await expect(rows).toHaveCount(updated.ledger.entries.length);
    await expect(rows.first()).toContainText(third);
    await expect(actors.filter({ hasText: 'actor:ana' })).toContainText('points:ledger: 10');
    await expect(actors.filter({ hasText: 'actor:ben' })).toContainText('points:ledger: 5');
    const feedbackAfter = await getFeedback(request, metamodelId);
    expect(feedbackAfter.length).toBeGreaterThan(feedback.length);
    await expect(feedbackCount).toHaveText(String(feedbackAfter.length));
});
