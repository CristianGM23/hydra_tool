import { expect, test, type Locator, type Page } from '@playwright/test';
import { eventually, uid } from '../support';
import {
    BLOCKED_MESSAGE, clickSave, combo, findTransformationByName, loadTransformationDoc, openDesigner, readTransformationFromDisk,
    runtimeMessage, savedSelector, seedMetamodel, SAVED_MESSAGE, switchToAdvanced, treeClass, type TransformationBindingDoc,
} from './helpers';

/** Fila de asignación de una propiedad del destino (se localiza por su selector «Tipo de asignación»). */
function bindingRow(page: Page, attribute: string): Locator {
    return page.locator('.transformation-binding-row').filter({
        has: page.getByRole('combobox', { name: new RegExp(`^(Mapping kind for|Tipo de mapeo para) ${attribute}$`) }),
    });
}

function kindSelect(page: Page, attribute: string): Locator {
    return page.getByRole('combobox', { name: new RegExp(`^(Mapping kind for|Tipo de mapeo para) ${attribute}$`) });
}

// Jornada de autoría de correspondencias: reglas de ida y vuelta, mapeo de atributos, tipos de asignación, políticas de valor y
// condición; el backend real guarda la definición y la interfaz la recupera tras recargar.
test('el diseñador de correspondencias guarda reglas de ida y vuelta, asignaciones, políticas de valor y condición, y la puerta rechaza una conversión sin declarar', async ({ page, request }) => {
    const srcId = uid('e2e-msrc');
    const tgtId = uid('e2e-mtgt');
    const a = (suffix: string): string => `${srcId}-person-${suffix}`;
    const b = (suffix: string): string => `${tgtId}-contact-${suffix}`;
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: `${srcId}-person`, name: 'Person', attrs: [
        { id: a('name'), name: 'name' }, { id: a('age'), name: 'age', type: 'INTEGER' }] }] });
    await seedMetamodel(request, { id: tgtId, name: tgtId, classes: [{ id: `${tgtId}-contact`, name: 'Contact', attrs: [
        { id: b('name'), name: 'name' }, { id: b('code'), name: 'code' }, { id: b('years'), name: 'years' }, { id: b('label'), name: 'label' }] }] });
    const name = uid('E2E Mapping');

    await openDesigner(page);
    await switchToAdvanced(page);
    await page.locator('#transformation-name').fill(name);
    await combo(page, 'Source metamodel', 'Metamodelo origen').selectOption(srcId);
    await combo(page, 'Target metamodel', 'Metamodelo destino').selectOption(tgtId);

    // Etapa bi (rama unidireccional): sin bidireccionalidad no se ofrece elegir la dirección de la regla.
    const ruleDirection = combo(page, 'Rule direction', 'Dirección de la regla');
    await expect(ruleDirection).toHaveCount(0);
    await combo(page, 'Directionality', 'Direccionalidad').selectOption('BIDIRECTIONAL');
    await expect(ruleDirection).toBeVisible();

    // Etapas sc / ar: clase de origen y de destino y creación de la regla de ida.
    const createForward = page.getByRole('button', { name: /Create rule from selected classes|Crear regla con las clases seleccionadas/ });
    await expect(createForward).toBeDisabled();
    await treeClass(page, 0, 'Person').click();
    await treeClass(page, 1, 'Contact').click();
    await expect(createForward).toBeEnabled();
    await createForward.click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);

    // Etapa dr: la regla de vuelta se crea en el otro sentido y se empareja con la de ida.
    await ruleDirection.selectOption('REVERSE');
    await treeClass(page, 0, 'Contact').click();
    await treeClass(page, 1, 'Person').click();
    await page.getByRole('button', { name: /Create backward rule|Crear regla de vuelta/ }).click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);
    await expect(combo(page, 'Paired correspondence', 'Correspondencia emparejada')).toBeVisible();
    await ruleDirection.selectOption('FORWARD');
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);

    // Etapa mp: «mapear nombres compatibles» enlaza solo name con name.
    await page.getByRole('button', { name: /Map compatible names|Mapear nombres compatibles/ }).click();
    await expect(kindSelect(page, 'name')).toHaveValue('DIRECT');
    await expect(kindSelect(page, 'code')).toHaveValue('NONE');

    // Etapa kd: valor fijo, copia de otra propiedad y expresión.
    await kindSelect(page, 'code').selectOption('CONSTANT');
    await page.locator('input[id^="constant-"]').fill('K-1');
    await kindSelect(page, 'years').selectOption('DIRECT');
    await bindingRow(page, 'years').getByRole('combobox', { name: /^(Source property for|Propiedad de origen para) years$/ }).selectOption(a('age'));

    // Etapa gd: condición de aplicación de la regla (antes de añadir expresiones para no mezclar constructores).
    await page.getByRole('button', { name: /^(Add condition|Añadir condición)$/ }).click();

    // Rechazo: entero → texto sin conversión declarada no se puede guardar.
    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(BLOCKED_MESSAGE);
    expect(await findTransformationByName(request, name)).toBeUndefined();

    // Etapa vp: el usuario declara la conversión y el resto de políticas del valor.
    const yearsRow = bindingRow(page, 'years');
    await yearsRow.getByRole('combobox', { name: /^(Type conversion|Conversión de tipo)$/ }).selectOption('TO_STRING');
    await yearsRow.getByRole('combobox', { name: /^(Collection adaptation|Adaptación de colección)$/ }).selectOption('FIRST');
    await yearsRow.getByRole('combobox', { name: /^(On mapping error|Ante error de mapeo)$/ }).selectOption('USE_FALLBACK');
    await yearsRow.locator('input[id^="fallback-"]').fill('0');
    await kindSelect(page, 'label').selectOption('EXPRESSION');

    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(SAVED_MESSAGE);
    const summary = await eventually(() => findTransformationByName(request, name), (value) => value !== undefined);
    const id = summary!.id;

    const persisted = await loadTransformationDoc(request, id);
    const onDisk = readTransformationFromDisk(id);
    for (const doc of [persisted, onDisk]) {
        expect(doc.rules).toHaveLength(2);
        const forward = doc.rules.find((rule) => rule.direction === 'FORWARD')!;
        const reverse = doc.rules.find((rule) => rule.direction === 'REVERSE')!;
        expect(forward.sourceTypeId).toBe(`${srcId}-person`);
        expect(forward.targets[0].typeId).toBe(`${tgtId}-contact`);
        expect(reverse.sourceTypeId).toBe(`${tgtId}-contact`);
        expect(reverse.correspondenceId).toBe(forward.correspondenceId);
        expect(forward.correspondenceId).not.toBe('');
        const bindingFor = (feature: string): TransformationBindingDoc => forward.bindings.find((binding) => binding.targetFeatureId === feature)!;
        expect(forward.bindings).toHaveLength(4);
        expect(bindingFor(b('name'))).toMatchObject({ kind: 'DIRECT', sourceFeatureId: a('name') });
        expect(bindingFor(b('code'))).toMatchObject({ kind: 'CONSTANT', literalValue: 'K-1' });
        expect(bindingFor(b('years'))).toMatchObject({ kind: 'DIRECT', sourceFeatureId: a('age') });
        expect(bindingFor(b('years')).valuePolicy).toMatchObject({ conversionMode: 'TO_STRING', collectionMode: 'FIRST', errorPolicy: 'USE_FALLBACK', fallbackValue: '0' });
        expect(bindingFor(b('label')).kind).toBe('EXPRESSION');
        expect(bindingFor(b('label')).expression).toBeTruthy();
        expect(forward.guard).toBeTruthy();
        expect(forward.guard!.declaredReturnType.kind).toBe('BOOLEAN');
    }

    // Recarga: la interfaz muestra lo que el backend guardó.
    await page.reload();
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(id);
    await switchToAdvanced(page);
    await expect(page.locator('.transformation-rule-card').first()).toContainText('4');
    await expect(kindSelect(page, 'code')).toHaveValue('CONSTANT');
    await expect(kindSelect(page, 'label')).toHaveValue('EXPRESSION');
    await expect(bindingRow(page, 'years').getByRole('combobox', { name: /^(Type conversion|Conversión de tipo)$/ })).toHaveValue('TO_STRING');
});

/** Metamodelo con dos clases con un atributo cada una y una relación de referencia de `owner` a `member` (paso previo, API real). */
async function seedRelationalMetamodel(request: import('@playwright/test').APIRequestContext, id: string, memberClass: string, ownerClass: string, ownerAttribute: string, relationName: string): Promise<{ memberId: string; ownerId: string; relationId: string }> {
    const attribute = (attrId: string, attrName: string) => ({ id: attrId, name: attrName, type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} });
    const ids = { memberId: `${id}-${memberClass.toLowerCase()}`, ownerId: `${id}-${ownerClass.toLowerCase()}`, relationId: `${id}-rel` };
    const response = await request.post('/api/canonical-metamodels', {
        data: {
            schemaVersion: '2.1', id, name: id, annotations: {}, enums: [], dataTypes: [],
            classes: [
                { id: ids.memberId, name: memberClass, kind: 'CLASS', annotations: {}, operations: [], attributes: [attribute(`${ids.memberId}-name`, 'name')] },
                { id: ids.ownerId, name: ownerClass, kind: 'CLASS', annotations: {}, operations: [], attributes: [attribute(`${ids.ownerId}-${ownerAttribute}`, ownerAttribute)] },
            ],
            relations: [{ id: ids.relationId, name: relationName, sourceTypeId: ids.ownerId, targetTypeId: ids.memberId, kind: 'REFERENCE',
                sourceMultiplicity: { lowerBound: 0, upperBound: 1 }, targetMultiplicity: { lowerBound: 0, upperBound: null }, oppositeRelationId: '', ordered: false, unique: true, annotations: {} }],
        },
    });
    if (!response.ok()) throw new Error(`POST /api/canonical-metamodels -> ${response.status()} ${await response.text()}`);
    return ids;
}

// Jornada de autoría de correspondencias, etapa «rl» (relaciones): el panel de mapeo de relaciones solo aparece en la regla cuya clase
// de destino tiene relaciones; el usuario elige la relación de origen que la rellena y el backend guarda un enlace de equivalencia por traza.
test('el panel de relaciones enlaza una relación del destino con una del origen y el backend guarda el enlace; sin relaciones no hay panel', async ({ page, request }) => {
    const srcId = uid('e2e-rsrc');
    const tgtId = uid('e2e-rtgt');
    const src = await seedRelationalMetamodel(request, srcId, 'Person', 'Team', 'title', 'member');
    const tgt = await seedRelationalMetamodel(request, tgtId, 'Contact', 'Group', 'title', 'participants');
    const name = uid('E2E Relations');

    await openDesigner(page);
    await switchToAdvanced(page);
    await page.locator('#transformation-name').fill(name);
    await combo(page, 'Source metamodel', 'Metamodelo origen').selectOption(srcId);
    await combo(page, 'Target metamodel', 'Metamodelo destino').selectOption(tgtId);
    const createForward = page.getByRole('button', { name: /Create rule from selected classes|Crear regla con las clases seleccionadas/ });
    const relationPanel = page.locator('.transformation-relation-bindings');
    const relationSelect = page.getByRole('combobox', { name: /^(Source relation for|Relación origen para) participants$/ });
    const mapNames = page.getByRole('button', { name: /Map compatible names|Mapear nombres compatibles/ });

    // La regla Person→Contact (clases sin relaciones salientes) no muestra el panel de relaciones.
    await treeClass(page, 0, 'Person').click();
    await treeClass(page, 1, 'Contact').click();
    await createForward.click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(1);
    await mapNames.click();
    await expect(relationPanel).toHaveCount(0);

    // La regla Team→Group sí lo muestra, con la relación `participants` sin mapear.
    await treeClass(page, 0, 'Team').click();
    await treeClass(page, 1, 'Group').click();
    await createForward.click();
    await expect(page.locator('.transformation-rule-card')).toHaveCount(2);
    await mapNames.click();
    await expect(relationPanel).toBeVisible();
    await expect(relationSelect).toHaveValue('');

    // Elegir y volver a «sin mapear» no deja ningún enlace; elegir la relación `member` crea el enlace por traza.
    await relationSelect.selectOption(src.relationId);
    await expect(relationSelect).toHaveValue(src.relationId);
    await relationSelect.selectOption('');
    await expect(relationSelect).toHaveValue('');
    await relationSelect.selectOption(src.relationId);

    await clickSave(page);
    await expect(runtimeMessage(page)).toHaveText(SAVED_MESSAGE);
    const summary = await eventually(() => findTransformationByName(request, name), (value) => value !== undefined);
    const id = summary!.id;
    const persisted = await loadTransformationDoc(request, id);
    const onDisk = readTransformationFromDisk(id);
    for (const doc of [persisted, onDisk]) {
        const teamRule = doc.rules.find((rule) => rule.sourceTypeId === src.ownerId)!;
        const personRule = doc.rules.find((rule) => rule.sourceTypeId === src.memberId)!;
        expect(teamRule.targets[0].typeId).toBe(tgt.ownerId);
        const relationBindings = teamRule.bindings.filter((binding) => binding.targetFeatureId === tgt.relationId);
        expect(relationBindings).toHaveLength(1);
        expect(relationBindings[0]).toMatchObject({ kind: 'TRACE_EQUIVALENT', sourceFeatureId: src.relationId, targetAlias: teamRule.targets[0].alias });
        // La regla sin relaciones no guarda ningún enlace de relación.
        expect(personRule.bindings.some((binding) => binding.kind === 'TRACE_EQUIVALENT')).toBe(false);
    }

    // Recarga: la interfaz recupera el enlace guardado.
    await page.reload();
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(id);
    await switchToAdvanced(page);
    await page.locator('.transformation-rule-card').filter({ hasText: 'Team' }).click();
    await expect(relationSelect).toHaveValue(src.relationId);
});
