import { expect, test, type Page } from '@playwright/test';
import { apiGet, uid } from '../support';
import {
    ELEMENT, classNamed, elementButton, errorNotice, field, findSavedByName, fitCanvas, openMetamodelDesigner, propertyPanel, readPersistedMetamodel, saveExpectingRejection,
    saveFromToolbar, selectCanvasNode, switchToVisualMode, type PersistedMetamodel
} from './helpers';

// Jornada: panel de propiedades del diseñador de metamodelos. El usuario elige qué editar (atributo, operación o relación),
// rellena los campos con el teclado y los selectores, y guarda. Oráculo: fichero .metamodel.json del backend + GET a la API real.
test.use({ viewport: { width: 1700, height: 1000 } });

const button = (page: Page, name: string | RegExp) => propertyPanel(page).getByRole('button', typeof name === 'string' ? { name, exact: true } : { name });

/** Añade un atributo a la clase seleccionada y lo deja abierto en el panel con el nombre indicado. */
async function addAttribute(page: Page, attributeName: string): Promise<void> {
    await button(page, 'Add').click();
    await button(page, /^newAttribute : /).click();
    await field(page, 'Name').fill(attributeName);
    await expect(field(page, 'Name')).toHaveValue(attributeName);
}

test('atributos, operaciones y relaciones editados en el panel se guardan con sus valores y un guardado inválido se rechaza sin tocar lo guardado', async ({ page, request }) => {
    const name = uid('Panel metamodel');
    await openMetamodelDesigner(page);
    await switchToVisualMode(page);
    await field(page, 'Name').fill(name);
    await elementButton(page, ELEMENT.CLASS).click();      // NewClass2
    await elementButton(page, ELEMENT.ENUM).click();       // NewEnum
    await elementButton(page, ELEMENT.DATA_TYPE).click();  // NewDataType
    await fitCanvas(page);

    // Enumerado: el usuario completa sus literales en el panel.
    await selectCanvasNode(page, 'NewEnum');
    await button(page, 'Add literal').click();
    const literals = propertyPanel(page).locator('.metamodel-enum-literal-row');
    await expect(literals).toHaveCount(2);
    await literals.nth(0).getByLabel('Name', { exact: true }).fill('RED');
    await literals.nth(1).getByLabel('Name', { exact: true }).fill('BLUE');

    // d1 → atributo: nombre, tipo básico, multiplicidad, ordenado, único y valor por defecto.
    await selectCanvasNode(page, 'NewClass');
    await expect(field(page, 'Name')).toHaveValue('NewClass');
    await addAttribute(page, 'age');
    await field(page, 'Type').selectOption('primitive:INTEGER');
    await field(page, 'Lower bound').selectOption('1');
    await field(page, 'Upper bound').selectOption('*');
    await field(page, 'Ordered').uncheck();
    await field(page, 'Unique').uncheck();
    await field(page, 'Default value').fill('0');
    await button(page, /^Back to NewClass$/).click();

    // Atributos con tipo enumerado y con tipo de datos propio.
    await addAttribute(page, 'status');
    await field(page, 'Type').selectOption({ label: 'NewEnum' });
    await button(page, /^Back to NewClass$/).click();
    await addAttribute(page, 'unit');
    await field(page, 'Type').selectOption({ label: 'NewDataType' });
    await button(page, /^Back to NewClass$/).click();

    // d1 → operación: firma (retorno y parámetro) y clase de comportamiento.
    await button(page, 'Add operation').click();
    await field(page, 'Name').fill('isAdult');
    await field(page, 'Behavior').selectOption('QUERY');
    await field(page, 'Return type').selectOption('primitive:BOOLEAN');
    await button(page, 'Add parameter').click();
    await field(page, 'Name').fill('minAge');
    await field(page, 'Type').selectOption('primitive:INTEGER');
    await button(page, /^Back to isAdult$/).click();
    await button(page, /^Back to NewClass$/).click();

    // d1 → relación: «Add reference» crea una referencia sin pasar por el lienzo; tipo, nombre, multiplicidad y orden.
    await button(page, 'Add reference').click();
    await field(page, 'Kind').selectOption('AGGREGATION');
    await field(page, 'Name').fill('children');
    await field(page, 'Upper bound').selectOption('*');
    await field(page, 'Ordered').uncheck();
    // Referencia en sentido contrario, para poder enlazarlas como inversas.
    await selectCanvasNode(page, 'NewClass2');
    await button(page, 'Add reference').click();
    await field(page, 'Name').fill('parent');
    await selectCanvasNode(page, 'NewClass');
    await button(page, /^AGGREGATION: children/).click();
    await field(page, 'Opposite').selectOption({ label: 'NewClass2.parent' });

    await saveFromToolbar(page, name, false);

    const saved = await findSavedByName(request, name);
    const onDisk = readPersistedMetamodel(saved.id);
    const owner = classNamed(onDisk, 'NewClass');
    const other = classNamed(onDisk, 'NewClass2');
    const enumeration = onDisk.enums.find(item => item.name === 'NewEnum');
    const dataType = onDisk.dataTypes.find(item => item.name === 'NewDataType');

    // at + opt: nombre, tipo, multiplicidad, ordenado, único y valor por defecto.
    expect(owner.attributes.map(item => item.name)).toEqual(['age', 'status', 'unit']);
    const age = owner.attributes[0];
    expect(age.type).toMatchObject({ kind: 'PRIMITIVE', targetId: 'INTEGER' });
    expect(age.multiplicity).toEqual({ lowerBound: 1, upperBound: null });
    expect(age.ordered).toBe(false);
    expect(age.unique).toBe(false);
    expect(age.defaultValue).toBe('0');
    expect(owner.attributes[1].type).toMatchObject({ kind: 'ENUM', targetId: enumeration?.id });
    expect(owner.attributes[2].type).toMatchObject({ kind: 'DATATYPE', targetId: dataType?.id });
    expect(enumeration?.literals.map(item => `${item.name}=${item.value}`)).toEqual(['RED=0', 'BLUE=1']);

    // op: firma y clase de comportamiento.
    expect(owner.operations).toHaveLength(1);
    expect(owner.operations[0]).toMatchObject({ name: 'isAdult', behaviorKind: 'QUERY', returnType: { kind: 'PRIMITIVE', targetId: 'BOOLEAN' } });
    expect(owner.operations[0].parameters).toHaveLength(1);
    expect(owner.operations[0].parameters[0]).toMatchObject({ name: 'minAge', type: { kind: 'PRIMITIVE', targetId: 'INTEGER' } });

    // rl: tipo, extremos, multiplicidad, orden e inversa.
    const children = onDisk.relations.find(item => item.name === 'children');
    const parent = onDisk.relations.find(item => item.name === 'parent');
    expect(onDisk.relations).toHaveLength(2);
    expect(children).toMatchObject({ kind: 'AGGREGATION', sourceTypeId: owner.id, targetTypeId: other.id, ordered: false });
    expect(children?.targetMultiplicity?.upperBound).toBeNull();
    expect(parent).toMatchObject({ kind: 'REFERENCE', sourceTypeId: other.id, targetTypeId: owner.id });
    expect(children?.oppositeRelationId).toBe(parent?.id);
    expect(parent?.oppositeRelationId).toBe(children?.id);

    // art: lo mismo que el fichero lo devuelve la API real.
    const viaApi = await apiGet<PersistedMetamodel>(request, `/api/canonical-metamodels/${saved.id}`);
    expect(classNamed(viaApi, 'NewClass').attributes.map(item => item.name)).toEqual(['age', 'status', 'unit']);
    expect(classNamed(viaApi, 'NewClass').operations.map(item => item.name)).toEqual(['isAdult']);

    // Rechazo: dos clases con el mismo nombre. El guardado informa del problema y el fichero no cambia.
    const before = JSON.stringify(readPersistedMetamodel(saved.id));
    await selectCanvasNode(page, 'NewClass2');
    await field(page, 'Name').fill('NewClass');
    await saveExpectingRejection(page, true);
    await expect(errorNotice(page).first()).toBeVisible();
    expect(JSON.stringify(readPersistedMetamodel(saved.id))).toBe(before);
    const afterApi = await apiGet<PersistedMetamodel>(request, `/api/canonical-metamodels/${saved.id}`);
    expect(afterApi.classes.map(item => item.name).sort()).toEqual(['NewClass', 'NewClass2']);
});
