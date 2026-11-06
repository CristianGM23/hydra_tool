import { expect, test, type Page } from '@playwright/test';
import { apiGet, uid } from '../support';
import { canvasNode, classNamed, expectNotSaved, field, findSavedByName, fitCanvas, openMetamodelDesigner, propertyPanel, readPersistedMetamodel, saveFromToolbar, type PersistedMetamodel } from './helpers';

// Jornada: asistente guiado «Guided metamodel creation» (Identidad → Clasificadores → Estructura → Revisión → Guardar).
// Acción: solo clics y teclado en el navegador. Oráculo: fichero .metamodel.json que escribe el backend + GET a la API real.

test.use({ viewport: { width: 1700, height: 1000 } });

const next = (page: Page) => page.locator('.hydra-guided-workflow-actions').getByRole('button', { name: /Next step|Siguiente paso/i });
const finish = (page: Page) => page.locator('.hydra-guided-workflow-actions').getByRole('button', { name: /Save metamodel|Guardar metamodelo/i });
const currentStep = (page: Page) => page.locator('.hydra-guided-current-step');

test('el asistente guiado exige el nombre, añade clasificadores y guarda el metamodelo completo', async ({ page, request }) => {
    const name = uid('Guided metamodel');
    await openMetamodelDesigner(page);
    const nameInput = page.getByLabel('Guided metamodel name');

    // Puerta de Identidad: sin nombre no se avanza y nada llega al almacenamiento.
    await nameInput.fill('');
    await expect(next(page)).toBeDisabled();
    await expectNotSaved(request, '');
    await nameInput.fill(name);
    await expect(next(page)).toBeEnabled();
    await next(page).click();

    // Clasificadores: botones simples de clase, interfaz, enumerado y tipo de datos.
    await expect(currentStep(page)).toContainText(/Classifiers|Clasificadores/);
    await page.getByRole('button', { name: '+ Class', exact: true }).click();
    await page.getByRole('button', { name: '+ Interface', exact: true }).click();
    await page.getByRole('button', { name: '+ Enumeration', exact: true }).click();
    await page.getByRole('button', { name: '+ Datatype', exact: true }).click();
    await expect(currentStep(page)).toContainText('3 class(es), 1 enum(s), 1 datatype(s)');
    await expectNotSaved(request, name); // hasta guardar, el backend no sabe nada del borrador
    await next(page).click();

    // Estructura (propiedades y relaciones opcionales) y revisión estructural.
    await expect(currentStep(page)).toContainText(/Properties and relationships|Propiedades y relaciones/);
    await expect(currentStep(page)).toContainText('0 relation(s) currently defined');
    await next(page).click();
    await expect(currentStep(page)).toContainText(/Structural review|Revisión estructural/);
    await next(page).click();

    // Guardar: el asistente declara el metamodelo listo y solo entonces se persiste.
    await expect(page.locator('.metamodel-guided-ready-actions')).toHaveClass(/ready/);
    await finish(page).click();
    await expect(page.locator('.metamodel-success-message')).toContainText(`Saved ${name}`);

    const saved = await findSavedByName(request, name);
    const onDisk = readPersistedMetamodel(saved.id);
    const viaApi = await apiGet<PersistedMetamodel>(request, `/api/canonical-metamodels/${saved.id}`);
    expect(onDisk.name).toBe(name);
    expect(onDisk.classes.map(item => item.name).sort()).toEqual(['NewClass', 'NewClass2', 'NewInterface']);
    expect(classNamed(onDisk, 'NewInterface').kind).toBe('INTERFACE');
    expect(classNamed(onDisk, 'NewClass2').kind).toBe('CLASS');
    expect(onDisk.enums.map(item => item.name)).toEqual(['NewEnum']);
    expect(onDisk.enums[0].literals.map(item => item.name)).toEqual(['VALUE']);
    expect(onDisk.dataTypes.map(item => item.name)).toEqual(['NewDataType']);
    expect(onDisk.relations).toEqual([]);
    expect(viaApi.classes.map(item => item.name).sort()).toEqual(onDisk.classes.map(item => item.name).sort());
    expect(viaApi.name).toBe(name);
});

test('desde el asistente se abre el editor visual y el atributo y la relación creados llegan al metamodelo guardado', async ({ page, request }) => {
    const name = uid('Wizard visual');
    await openMetamodelDesigner(page);
    await page.getByLabel('Guided metamodel name').fill(name);
    await next(page).click();
    await expect(currentStep(page)).toContainText(/Classifiers|Clasificadores/);
    await page.getByRole('button', { name: '+ Class', exact: true }).click();

    // El asistente remite al editor visual/manual para el detalle de la estructura.
    await page.getByRole('button', { name: /Open visual \/ manual structure editor/i }).click();
    await expect(page.locator('.metamodel-palette')).toBeVisible();
    await fitCanvas(page);
    await expect(canvasNode(page, 'NewClass2')).toBeVisible();
    await expect(field(page, 'Name')).toHaveValue('NewClass2'); // la última clase añadida sigue seleccionada

    // Atributo desde el panel de propiedades.
    await propertyPanel(page).getByRole('button', { name: 'Add', exact: true }).click();
    await propertyPanel(page).getByRole('button', { name: /^newAttribute : String/ }).click();
    await field(page, 'Name').fill('title');
    await propertyPanel(page).getByRole('button', { name: /^Back to NewClass2/ }).click();

    // Referencia creada sin pasar por el lienzo («Add reference» apunta a la otra clase).
    await propertyPanel(page).getByRole('button', { name: 'Add reference' }).click();
    await field(page, 'Name').fill('owner');
    await expect(canvasNode(page, 'NewClass2')).toBeVisible();

    await saveFromToolbar(page, name, false);

    const saved = await findSavedByName(request, name);
    const onDisk = readPersistedMetamodel(saved.id);
    const owner = classNamed(onDisk, 'NewClass2');
    const other = classNamed(onDisk, 'NewClass');
    expect(owner.attributes).toHaveLength(1);
    expect(owner.attributes[0].name).toBe('title');
    expect(owner.attributes[0].type).toMatchObject({ kind: 'PRIMITIVE', targetId: 'STRING' });
    expect(onDisk.relations).toHaveLength(1);
    expect(onDisk.relations[0]).toMatchObject({ name: 'owner', kind: 'REFERENCE', sourceTypeId: owner.id, targetTypeId: other.id });
    const viaApi = await apiGet<PersistedMetamodel>(request, `/api/canonical-metamodels/${saved.id}`);
    expect(classNamed(viaApi, 'NewClass2').attributes.map(item => item.name)).toEqual(['title']);
});

test('el paso Estructura del asistente muestra las relaciones definidas y remite al editor donde se añaden atributo y relación que se guardan', async ({ page, request }) => {
    const name = uid('Wizard structure');
    await openMetamodelDesigner(page);
    await page.getByLabel('Guided metamodel name').fill(name);
    await next(page).click();
    await page.getByRole('button', { name: '+ Class', exact: true }).click();
    await next(page).click();

    // st: paso «Properties and relationships»; las relaciones son opcionales y aún no hay ninguna.
    await expect(currentStep(page)).toContainText(/Properties and relationships|Propiedades y relaciones/);
    await expect(currentStep(page)).toContainText('0 relation(s) currently defined');
    await expectNotSaved(request, name);
    await page.getByRole('button', { name: /Edit properties and relations visually/i }).click();
    await expect(page.locator('.metamodel-palette')).toBeVisible();
    await fitCanvas(page);
    await expect(field(page, 'Name')).toHaveValue('NewClass2');

    await propertyPanel(page).getByRole('button', { name: 'Add', exact: true }).click();
    await propertyPanel(page).getByRole('button', { name: /^newAttribute : String/ }).click();
    await field(page, 'Name').fill('code');
    await propertyPanel(page).getByRole('button', { name: /^Back to NewClass2/ }).click();
    await propertyPanel(page).getByRole('button', { name: 'Add reference' }).click();
    await field(page, 'Name').fill('peer');
    await saveFromToolbar(page, name, false);

    const saved = await findSavedByName(request, name);
    const onDisk = readPersistedMetamodel(saved.id);
    const owner = classNamed(onDisk, 'NewClass2');
    expect(owner.attributes.map(item => item.name)).toEqual(['code']);
    expect(onDisk.relations).toHaveLength(1);
    expect(onDisk.relations[0]).toMatchObject({ name: 'peer', kind: 'REFERENCE', sourceTypeId: owner.id, targetTypeId: classNamed(onDisk, 'NewClass').id });
    const viaApi = await apiGet<PersistedMetamodel>(request, `/api/canonical-metamodels/${saved.id}`);
    expect(viaApi.relations.map(item => item.name)).toEqual(['peer']);
});
