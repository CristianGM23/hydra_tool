import { expect, test, type APIRequestContext, type Locator } from '@playwright/test';
import { uid } from '../support';
import { advancedRoot, branchChild, diskDoc, directChild, expectSavedMessage, field, forceEnglish, getExpressions, getValidations, kindSelect, literalType, literalValueInput, modeSwitch, nodeKinds, nodeSelect, openValidationDesigner, saveFromToolbar, seedValidationMetamodel, select, useAdvancedCondition, waitForRule } from './helpers';

// Jornada «formulario completo de reglas»: identidad y contexto, momentos y mensaje, y árbol de expresión (modo avanzado).
// Oráculo: GET real de validaciones y expresiones + ficheros JSON del backend; tras recargar la página, la regla se reabre con lo guardado.
const addRule = (page: import('@playwright/test').Page) => page.getByRole('button', { name: '+ Rule', exact: true });

test('el formulario completo guarda identidad, contexto, momentos, mensaje y el árbol de expresión, y se reabre igual', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'valform');
    await openValidationDesigner(page, mm.id);

    // Grupo con nombre propio y regla nueva en formulario completo.
    await page.getByRole('button', { name: '+ Group', exact: true }).click();
    await page.getByLabel('Validation group name').fill('Customer rules');
    await addRule(page).click();
    await modeSwitch(page, 'Full form').click();

    // idc: identidad y contexto.
    await field(page, 'Name').fill('Edad positiva cliente');
    await field(page, 'Stable problem code').fill('customer_age_positive');
    await select(page, 'Severity').selectOption('WARNING');
    await select(page, 'Group').selectOption({ label: 'Customer rules' });
    await select(page, 'Context').selectOption('ATTRIBUTE');
    await select(page, 'Context class').selectOption(mm.personId);
    await select(page, 'Target feature').selectOption(mm.ageId);

    // tg: momentos y mensaje (se añade un momento a los dos que trae por defecto).
    await page.getByLabel('When an attribute changes').check();
    await field(page, 'Message shown when the rule fails').fill('Age cannot be negative. Enter zero or a positive value.');

    // ex: árbol de expresión en modo avanzado: (age >= 0) AND (fullName no es nulo).
    await useAdvancedCondition(page);
    const root = advancedRoot(page);
    await kindSelect(root).selectOption('BINARY');
    await nodeSelect(root, '-binary-operator').selectOption('AND');
    const left = branchChild(root, 0);
    await kindSelect(left).selectOption('BINARY');
    await nodeSelect(left, '-binary-operator').selectOption('GREATER_OR_EQUAL');
    const age = branchChild(left, 0);
    await kindSelect(age).selectOption('ATTRIBUTE_ACCESS');
    await nodeSelect(age, '-attribute').selectOption(mm.ageId);
    const zero = branchChild(left, 1);
    await literalType(zero).selectOption('INTEGER');
    await literalValueInput(zero).fill('0');
    const right = branchChild(root, 1);
    await kindSelect(right).selectOption('UNARY');
    await nodeSelect(right, '-unary-operator').selectOption('IS_NOT_NULL');
    const operand = directChild(right, 0);
    await kindSelect(operand).selectOption('ATTRIBUTE_ACCESS');
    await nodeSelect(operand, '-attribute').selectOption(mm.nameId);

    await saveFromToolbar(page);
    await expectSavedMessage(page);

    // Oráculo: API real.
    const rule = await waitForRule(request, mm.id, 'CUSTOMER_AGE_POSITIVE');
    expect(rule).toMatchObject({ name: 'Edad positiva cliente', severity: 'WARNING', context: { kind: 'ATTRIBUTE', classId: mm.personId, featureId: mm.ageId }, messageTemplate: 'Age cannot be negative. Enter zero or a positive value.' });
    expect(new Set(rule.triggers as string[])).toEqual(new Set(['ON_SAVE', 'MANUAL', 'ON_ATTRIBUTE_CHANGE']));
    const validations = await getValidations(request, mm.id);
    const group = (validations.ruleSets as Array<Record<string, unknown>>).find((item) => item.name === 'Customer rules');
    expect(group?.ruleIds).toContain(rule.id);

    const expressions = await getExpressions(request, mm.id);
    const expression = (expressions.expressions as Array<Record<string, any>>).find((item) => item.id === rule.conditionExpressionId); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(expression, 'la regla debe apuntar a su expresión guardada').toBeDefined();
    const tree = expression?.root;
    expect(tree).toMatchObject({ kind: 'BINARY', operator: 'AND' });
    expect(tree.left).toMatchObject({ kind: 'BINARY', operator: 'GREATER_OR_EQUAL' });
    expect(tree.left.left).toMatchObject({ kind: 'ATTRIBUTE_ACCESS', attributeId: mm.ageId, target: { kind: 'SELF' } });
    expect(tree.left.right).toMatchObject({ kind: 'LITERAL', valueType: { kind: 'INTEGER' }, value: 0 });
    expect(tree.right).toMatchObject({ kind: 'UNARY', operator: 'IS_NOT_NULL', operand: { kind: 'ATTRIBUTE_ACCESS', attributeId: mm.nameId } });
    expect([...nodeKinds(tree)].sort()).toEqual(['ATTRIBUTE_ACCESS', 'BINARY', 'LITERAL', 'SELF', 'UNARY']);

    // Oráculo independiente: ficheros del backend.
    const diskRule = (diskDoc(mm.id, 'validations')?.rules as Array<Record<string, unknown>>).find((item) => item.code === 'CUSTOMER_AGE_POSITIVE');
    expect(diskRule?.messageTemplate).toBe('Age cannot be negative. Enter zero or a positive value.');
    expect(diskDoc(mm.id, 'expressions')).toBeDefined();

    // Recargar: la regla guardada se reabre con los mismos valores.
    await page.reload();
    await openValidationDesigner(page, mm.id);
    await page.locator('.validation-rule-list-item', { hasText: 'Edad positiva cliente' }).click();
    await modeSwitch(page, 'Full form').click();
    await expect(field(page, 'Name')).toHaveValue('Edad positiva cliente');
    await expect(field(page, 'Stable problem code')).toHaveValue('CUSTOMER_AGE_POSITIVE');
    await expect(select(page, 'Severity')).toHaveValue('WARNING');
    await expect(select(page, 'Group')).toHaveValue(group?.id as string);
    await expect(field(page, 'Message shown when the rule fails')).toHaveValue('Age cannot be negative. Enter zero or a positive value.');
    await expect(page.getByLabel('When an attribute changes')).toBeChecked();
});

test('el constructor de condiciones ofrece los 13 tipos de nodo de expresión', async ({ page, request }) => {
    await forceEnglish(page);
    const mm = await seedValidationMetamodel(request, 'valkinds');
    await openValidationDesigner(page, mm.id);
    await addRule(page).click();
    await modeSwitch(page, 'Full form').click();
    await useAdvancedCondition(page);
    const root = advancedRoot(page);
    const kinds = ['LITERAL', 'SELF', 'MODEL', 'VARIABLE', 'CLASS_INSTANCES', 'ATTRIBUTE_ACCESS', 'RELATION_NAVIGATION', 'OPERATION_CALL', 'UNARY', 'BINARY', 'COLLECTION', 'CONDITIONAL', 'FUNCTION'];
    await expect(kindSelect(root).locator('option')).toHaveCount(kinds.length);
    for (const kind of kinds) {
        await kindSelect(root).selectOption(kind);
        await expect(root).toHaveClass(new RegExp(`kind-${kind.toLowerCase()}`));
    }
});

/** Metamodelo con Person(age, fullName, consulta isAdult), Company y la relación Company --employees--> Person (paso previo, API real). */
async function seedOperationMetamodel(request: APIRequestContext): Promise<{ id: string; personId: string; companyId: string; ageId: string; nameId: string; relationId: string; operationId: string }> {
    const id = uid('valtree');
    const ids = { id, personId: `${id}-person`, companyId: `${id}-company`, ageId: `${id}-person-age`, nameId: `${id}-person-name`, relationId: `${id}-employees`, operationId: `${id}-person-isadult` };
    const attribute = (attrId: string, name: string, type: string) => ({ id: attrId, name, type: { kind: 'PRIMITIVE', targetId: type, externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} });
    const response = await request.post('/api/canonical-metamodels', {
        data: {
            schemaVersion: '2.1', id, name: id, annotations: {}, enums: [], dataTypes: [],
            classes: [
                { id: ids.personId, name: 'Person', kind: 'CLASS', annotations: {}, attributes: [attribute(ids.ageId, 'age', 'INTEGER'), attribute(ids.nameId, 'fullName', 'STRING')],
                    operations: [{ id: ids.operationId, name: 'isAdult', returnType: { kind: 'PRIMITIVE', targetId: 'BOOLEAN', externalName: '' }, returnMultiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: false, parameters: [], exceptionTypes: [], behaviorKind: 'QUERY', annotations: {} }] },
                { id: ids.companyId, name: 'Company', kind: 'CLASS', annotations: {}, operations: [], attributes: [] },
            ],
            relations: [{ id: ids.relationId, name: 'employees', sourceTypeId: ids.companyId, targetTypeId: ids.personId, kind: 'REFERENCE',
                sourceMultiplicity: { lowerBound: 0, upperBound: 1 }, targetMultiplicity: { lowerBound: 0, upperBound: null }, oppositeRelationId: '', ordered: false, unique: true, annotations: {} }],
        },
    });
    if (!response.ok()) throw new Error(`no se pudo sembrar el metamodelo ${id}: ${response.status()} ${await response.text()}`);
    return ids;
}

const textField = (node: Locator, idSuffix: string): Locator => node.locator(`:scope > [data-hydra-field-id$="${idSuffix}"] input`);

// Etapa «ex» (árbol de expresión): el usuario construye en el constructor avanzado una condición que usa los 13 tipos de nodo y el backend
// real guarda la expresión completa, que se lee por API y desde el fichero del backend.
// Antes pendiente (fixme): el guardado devolvía 400 porque el nodo MODEL generaba la dependencia { kind: 'MODEL', elementId: '' } y el backend exige un
// id estable ('model'). Corregido en validationDesignerSelectors.ts (campaña consolidada 2026-10-23); la etapa «ex» sigue sin reclamarse como directa.
test('una condición construida con los 13 tipos de nodo se guarda tal cual en el backend', async ({ page, request }) => {
    test.setTimeout(150_000);
    await forceEnglish(page);
    const mm = await seedOperationMetamodel(request);
    await openValidationDesigner(page, mm.id);
    await addRule(page).click();
    await modeSwitch(page, 'Full form').click();
    await field(page, 'Name').fill('Empresa coherente');
    await field(page, 'Stable problem code').fill('company_consistent');
    await select(page, 'Context').selectOption('CLASS');
    await select(page, 'Context class').selectOption(mm.personId);
    await field(page, 'Message shown when the rule fails').fill('The company data is not consistent. Review its employees.');
    await useAdvancedCondition(page);

    // Raíz: AND( IF-THEN-ELSE , FOR_ALL ).
    const root = advancedRoot(page);
    await kindSelect(root).selectOption('BINARY');
    await nodeSelect(root, '-binary-operator').selectOption('AND');

    // Rama izquierda: condicional «si el modelo existe, alguna persona tiene edad >= 0; si no, verdadero».
    const conditional = branchChild(root, 0);
    await kindSelect(conditional).selectOption('CONDITIONAL');
    const condition = branchChild(conditional, 0);
    await kindSelect(condition).selectOption('UNARY');
    await nodeSelect(condition, '-unary-operator').selectOption('IS_NOT_NULL');
    await kindSelect(directChild(condition, 0)).selectOption('MODEL');
    const exists = branchChild(conditional, 1);
    await kindSelect(exists).selectOption('COLLECTION');
    await nodeSelect(exists, '-collection-operator').selectOption('EXISTS');
    const exPredicate = directChild(exists, 1);
    await kindSelect(exPredicate).selectOption('BINARY');
    await nodeSelect(exPredicate, '-binary-operator').selectOption('GREATER_OR_EQUAL');
    await kindSelect(branchChild(exPredicate, 0)).selectOption('ATTRIBUTE_ACCESS');
    await nodeSelect(branchChild(exPredicate, 0), '-attribute').selectOption(mm.ageId);
    await literalType(branchChild(exPredicate, 1)).selectOption('INTEGER');
    await literalValueInput(branchChild(exPredicate, 1)).fill('0');

    // Rama derecha: FOR_ALL sobre Company.employees con VARIABLE, consulta isAdult y FUNCTION length(fullName) > 0.
    const forAll = branchChild(root, 1);
    await kindSelect(forAll).selectOption('COLLECTION');
    await nodeSelect(forAll, '-collection-operator').selectOption('FOR_ALL');
    const navigation = directChild(forAll, 0);
    await kindSelect(navigation).selectOption('RELATION_NAVIGATION');
    const companies = directChild(navigation, 0);
    await kindSelect(companies).selectOption('CLASS_INSTANCES');
    await nodeSelect(companies, '-class').selectOption(mm.companyId);
    await nodeSelect(navigation, '-relation').selectOption(mm.relationId);
    const predicate = directChild(forAll, 1);
    await kindSelect(predicate).selectOption('BINARY');
    await nodeSelect(predicate, '-binary-operator').selectOption('AND');
    const flags = branchChild(predicate, 0);
    await kindSelect(flags).selectOption('BINARY');
    await nodeSelect(flags, '-binary-operator').selectOption('AND');
    const variable = branchChild(flags, 0);
    await kindSelect(variable).selectOption('VARIABLE');
    await textField(variable, '-variable').fill('enabled');
    const call = branchChild(flags, 1);
    await kindSelect(call).selectOption('OPERATION_CALL');
    await nodeSelect(call, '-operation').selectOption(mm.operationId);
    const longName = branchChild(predicate, 1);
    await kindSelect(longName).selectOption('BINARY');
    await nodeSelect(longName, '-binary-operator').selectOption('GREATER_THAN');
    const lengthCall = branchChild(longName, 0);
    await kindSelect(lengthCall).selectOption('FUNCTION');
    await textField(lengthCall, '-function-name').fill('length');
    // El botón se llama «Function arguments» para el árbol de accesibilidad (su texto visible es «Add argument»).
    await lengthCall.locator(':scope > .validation-expression-action-row').getByRole('button', { name: 'Function arguments', exact: true }).click();
    const nameArgument = directChild(lengthCall, 0);
    await kindSelect(nameArgument).selectOption('ATTRIBUTE_ACCESS');
    await nodeSelect(nameArgument, '-attribute').selectOption(mm.nameId);
    await literalType(branchChild(longName, 1)).selectOption('INTEGER');
    await literalValueInput(branchChild(longName, 1)).fill('0');

    // Antes de guardar el servidor no conoce la regla.
    expect(((await getValidations(request, mm.id)).rules ?? []) as unknown[]).toHaveLength(0);
    await saveFromToolbar(page);
    await expectSavedMessage(page);

    const rule = await waitForRule(request, mm.id, 'COMPANY_CONSISTENT');
    const expressions = await getExpressions(request, mm.id);
    const expression = (expressions.expressions as Array<Record<string, any>>).find((item) => item.id === rule.conditionExpressionId); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(expression, 'la regla debe apuntar a su expresión guardada').toBeDefined();
    const tree = expression?.root;
    expect([...nodeKinds(tree)].sort()).toEqual(['ATTRIBUTE_ACCESS', 'BINARY', 'CLASS_INSTANCES', 'COLLECTION', 'CONDITIONAL', 'FUNCTION', 'LITERAL', 'MODEL', 'OPERATION_CALL', 'RELATION_NAVIGATION', 'SELF', 'UNARY', 'VARIABLE']);
    expect(tree).toMatchObject({ kind: 'BINARY', operator: 'AND' });
    expect(tree.left).toMatchObject({ kind: 'CONDITIONAL', condition: { kind: 'UNARY', operator: 'IS_NOT_NULL', operand: { kind: 'MODEL' } }, whenTrue: { kind: 'COLLECTION', operator: 'EXISTS', source: { kind: 'CLASS_INSTANCES', classId: mm.personId } } });
    expect(tree.left.whenTrue.expression).toMatchObject({ kind: 'BINARY', operator: 'GREATER_OR_EQUAL', left: { kind: 'ATTRIBUTE_ACCESS', attributeId: mm.ageId }, right: { kind: 'LITERAL', valueType: { kind: 'INTEGER' }, value: 0 } });
    expect(tree.right).toMatchObject({ kind: 'COLLECTION', operator: 'FOR_ALL', source: { kind: 'RELATION_NAVIGATION', relationId: mm.relationId, target: { kind: 'CLASS_INSTANCES', classId: mm.companyId } } });
    expect(tree.right.expression.left.left).toMatchObject({ kind: 'VARIABLE', variableName: 'enabled' });
    expect(tree.right.expression.left.right).toMatchObject({ kind: 'OPERATION_CALL', operationId: mm.operationId, target: { kind: 'SELF' } });
    expect(tree.right.expression.right).toMatchObject({ kind: 'BINARY', operator: 'GREATER_THAN', left: { kind: 'FUNCTION', functionName: 'length', arguments: [{ kind: 'ATTRIBUTE_ACCESS', attributeId: mm.nameId }] } });

    // Oráculo independiente: el fichero de expresiones del backend contiene los mismos 13 tipos.
    const diskExpression = (diskDoc(mm.id, 'expressions')?.expressions as Array<Record<string, any>>).find((item) => item.id === rule.conditionExpressionId); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect([...nodeKinds(diskExpression?.root)].sort()).toEqual([...nodeKinds(tree)].sort());
});
