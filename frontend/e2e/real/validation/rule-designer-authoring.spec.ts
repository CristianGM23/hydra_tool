import { expect, test } from '@playwright/test';
import { listFiles } from '../support';
import { ALERT, META_COMBO, anyDiskDocument, diskDoc, expectSavedMessage, field, forceEnglish, nextStep, openValidationDesigner, openWithoutContext, saveFromToolbar, seedValidationMetamodel, select, validationsStatus, waitForRule } from './helpers';

// Jornada «diseñador de reglas»: sin metamodelo el diseñador no ofrece reglas; con un metamodelo guardado ofrece sus clases y atributos;
// el código se normaliza; un error de diseño bloquea el guardado (nada llega al disco) y, corregido, se guarda.
// Oráculo: GET real de las validaciones y ficheros JSON que escribe el backend en data/validations.
const addRule = (page: import('@playwright/test').Page) => page.getByRole('button', { name: '+ Rule', exact: true });

test('sin contexto el diseñador no permite reglas; con el metamodelo guardado ofrece Person/age, normaliza el código y guarda la regla', async ({ page, request }) => {
    await forceEnglish(page);
    await openWithoutContext(page);
    const combo = page.getByRole('combobox', { name: META_COMBO }).first();
    await expect(combo).toBeVisible();
    // etapa meta (parte de bloqueo): solo comprobable si la aplicación no preselecciona un contexto al arrancar.
    if ((await combo.inputValue()) === '') {
        await expect(page.getByRole('heading', { name: /Select a semantic context/ })).toBeVisible();
        await expect(addRule(page)).toHaveCount(0);
    }

    const mm = await seedValidationMetamodel(request, 'valrule');
    await openValidationDesigner(page, mm.id);
    expect(await validationsStatus(request, mm.id)).not.toBe(200); // aún no hay validaciones guardadas
    await addRule(page).click();

    // id: nombre, código (se fuerza a mayúsculas/números/_) y gravedad.
    await field(page, 'Name').fill('Edad positiva');
    await field(page, 'Stable problem code').fill('edad ok');
    await expect(field(page, 'Stable problem code')).toHaveValue('EDAD_OK');
    await select(page, 'Severity').selectOption('ERROR');
    await nextStep(page).click();

    // meta (parte de oferta): el contexto ofrece las clases y atributos del metamodelo guardado.
    await select(page, 'Context').selectOption('ATTRIBUTE');
    await expect(select(page, 'Context class')).toContainText('Person');
    await select(page, 'Context class').selectOption(mm.personId);
    await expect(select(page, 'Target feature')).toContainText('Person.age');
    await select(page, 'Target feature').selectOption(mm.ageId);

    await saveFromToolbar(page);
    await expectSavedMessage(page);

    const rule = await waitForRule(request, mm.id, 'EDAD_OK');
    expect(rule).toMatchObject({ name: 'Edad positiva', code: 'EDAD_OK', severity: 'ERROR', context: { kind: 'ATTRIBUTE', classId: mm.personId, featureId: mm.ageId } });
    const onDisk = diskDoc(mm.id, 'validations');
    expect(onDisk, 'el backend debe haber escrito el fichero de validaciones').toBeDefined();
    expect((onDisk?.rules as Array<Record<string, unknown>>).map((item) => item.code)).toContain('EDAD_OK');
    expect(listFiles('data/validations').some((name) => name.includes(mm.id))).toBe(true);
});

test('un error de diseño bloquea el guardado: no se escribe nada hasta corregirlo', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'valblock');
    await openValidationDesigner(page, mm.id);
    await addRule(page).click();

    // Error de diseño: regla sin nombre.
    await field(page, 'Name').fill('');
    await saveFromToolbar(page);
    await expect(page.locator(ALERT)).toBeVisible();
    await expect(page.locator(ALERT)).toContainText(/name/i);
    expect(await validationsStatus(request, mm.id)).not.toBe(200);
    expect(anyDiskDocument(mm.id)).toBe(false);

    // Corrección: con nombre ya se guarda y el aviso desaparece.
    await field(page, 'Name').fill('Regla corregida');
    await saveFromToolbar(page);
    await expectSavedMessage(page);
    await expect(page.locator(ALERT)).toHaveCount(0);
    const rule = await waitForRule(request, mm.id, 'NEW_VALIDATION');
    expect(rule.name).toBe('Regla corregida');
    expect(anyDiskDocument(mm.id)).toBe(true);
});

// Etapa «meta» completa y determinista: el usuario deja el selector de contexto vacío (aunque la aplicación preseleccione uno) y el diseñador se
// bloquea; al volver a elegir el metamodelo guardado, el diseñador ofrece exactamente sus clases y atributos, que se contrastan con la API real.
test('sin metamodelo elegido el diseñador queda bloqueado y con el guardado ofrece exactamente sus clases y atributos', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'valmeta');
    await openValidationDesigner(page, mm.id);
    const contextCombo = page.getByRole('combobox', { name: META_COMBO }).first();

    // Bloqueo: sin contexto no hay diseñador ni reglas y el servidor no guarda nada.
    await contextCombo.selectOption('');
    await expect(page.getByRole('heading', { name: /Select a semantic context/ })).toBeVisible();
    await expect(page.locator('.validation-designer')).toHaveCount(0);
    await expect(addRule(page)).toHaveCount(0);
    expect(await validationsStatus(request, mm.id)).not.toBe(200);
    expect(anyDiskDocument(mm.id)).toBe(false);

    // Oferta: con el metamodelo guardado, clases y atributos ofrecidos = los que la API devuelve para ese metamodelo.
    await contextCombo.selectOption(mm.id);
    await expect(page.locator('.validation-designer')).toBeVisible();
    const saved = await (await request.get(`/api/canonical-metamodels/${encodeURIComponent(mm.id)}`)).json() as { classes: Array<{ id: string; name: string; attributes: Array<{ id: string }> }> };
    await addRule(page).click();
    await nextStep(page).click();
    await select(page, 'Context').selectOption('ATTRIBUTE');
    const offeredValues = async (label: string): Promise<string[]> => (await select(page, label).locator('option').evaluateAll((items) => items.map((item) => (item as HTMLOptionElement).value))).filter((value) => value !== '');
    expect((await offeredValues('Context class')).sort()).toEqual(saved.classes.map((item) => item.id).sort());
    expect(saved.classes.map((item) => item.name)).toContain('Person');
    await select(page, 'Context class').selectOption(mm.personId);
    const person = saved.classes.find((item) => item.id === mm.personId);
    expect((await offeredValues('Target feature')).sort()).toEqual((person?.attributes ?? []).map((item) => item.id).sort());
});
