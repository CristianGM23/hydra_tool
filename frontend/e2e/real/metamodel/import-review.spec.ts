import { expect, test } from '@playwright/test';
import { apiGet, uid } from '../support';
import {
    canvasNode, classNamed, clickToolbarCommand, expectNotSaved, field, findSavedByName, fitCanvas, openMetamodelDesigner, propertyPanel, readPersistedMetamodel, saveFromToolbar, selectCanvasNode,
    switchToVisualMode, type PersistedMetamodel
} from './helpers';

// Jornada: «Import metamodel» con un formato de fidelidad ESTRUCTURAL (diagrama PlantUML). El usuario sube el fichero, revisa lo
// que se propone, lo abre en el diseñador, completa a mano lo que falta y guarda. Oráculo: catálogo de formatos de la API real
// (fidelidad declarada), ausencia de persistencia antes de guardar y fichero .metamodel.json final.
test.use({ viewport: { width: 1700, height: 1000 } });

interface ImportFormat { id: string; displayName: string; supportLevel: string }

const SOURCE = [
    '@startuml',
    'class "Person" as Person {',
    '  fullName : String [1]',
    '  age : Integer [0..1]',
    '}',
    'class "Company" as Company {',
    '  title : String [1]',
    '}',
    'Person --> "0..1" Company : employer',
    '@enduml',
    ''
].join('\n');

test('la importación estructural propone el metamodelo sin guardarlo, el usuario lo completa a mano y lo guardado contiene ambas partes', async ({ page, request }) => {
    const stem = uid('review');
    await openMetamodelDesigner(page);
    await clickToolbarCommand(page, 'Interchange', '.metamodel-import-button');
    const dialog = page.getByRole('dialog', { name: /Import metamodel|Importar metamodelo/i });
    await expect(dialog).toBeVisible();

    // Subida del fichero desde el navegador; el análisis arranca solo.
    await dialog.locator('#metamodel-import-file-input').setInputFiles({ name: `${stem}.puml`, mimeType: 'text/plain', buffer: Buffer.from(SOURCE, 'utf8') });
    const preview = dialog.locator('.metamodel-import-preview');
    await expect(preview).toBeVisible({ timeout: 60_000 });

    // Revisión: la fidelidad que informa la pantalla es la que el backend declara para el formato detectado (estructural).
    const headline = (await preview.locator('p').first().innerText()).split('·').map(part => part.trim());
    expect(headline).toHaveLength(3);
    const [previewName, detectedFormat, reportedLevel] = headline;
    const formats = await apiGet<ImportFormat[]>(request, '/api/metamodel-imports/formats');
    const declared = formats.find(item => item.id === detectedFormat);
    expect(declared, `formato detectado ${detectedFormat} presente en el catálogo`).toBeDefined();
    expect(reportedLevel).toBe(declared?.supportLevel);
    expect(reportedLevel).toBe('STRUCTURAL');
    await expect(dialog.locator('.metamodel-import-statistics')).toContainText(/2 classes/);
    await expectNotSaved(request, previewName); // proponer no persiste nada

    // El usuario abre la propuesta en el diseñador (sigue sin persistirse).
    await dialog.getByRole('button', { name: /^Open in designer$|^Abrir en el diseñador$/i }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('.metamodel-success-message')).toContainText(`Imported ${previewName} into the designer`);
    await expectNotSaved(request, previewName);

    // Completa a mano lo que el formato no aporta: un atributo nuevo en la clase importada.
    await switchToVisualMode(page);
    await fitCanvas(page);
    await expect(canvasNode(page, 'Person')).toBeVisible();
    await selectCanvasNode(page, 'Company');
    await expect(field(page, 'Name')).toHaveValue('Company');
    await propertyPanel(page).getByRole('button', { name: 'Add', exact: true }).click();
    await propertyPanel(page).getByRole('button', { name: /^newAttribute : /i }).click();
    await field(page, 'Name').fill('registryCode');

    await saveFromToolbar(page, previewName, false);
    const saved = await findSavedByName(request, previewName);
    const onDisk = readPersistedMetamodel(saved.id);

    // Lo guardado reúne lo propuesto por el importador y lo completado a mano.
    expect(onDisk.classes.map(item => item.name).sort()).toEqual(['Company', 'Person']);
    expect(classNamed(onDisk, 'Person').attributes.map(item => item.name)).toEqual(['fullName', 'age']);
    expect(classNamed(onDisk, 'Company').attributes.map(item => item.name).sort()).toEqual(['registryCode', 'title']);
    expect(onDisk.relations).toHaveLength(1);
    expect(onDisk.relations[0]).toMatchObject({ name: 'employer', sourceTypeId: classNamed(onDisk, 'Person').id, targetTypeId: classNamed(onDisk, 'Company').id });
    const viaApi = await apiGet<PersistedMetamodel>(request, `/api/canonical-metamodels/${saved.id}`);
    expect(classNamed(viaApi, 'Company').attributes.map(item => item.name).sort()).toEqual(['registryCode', 'title']);
});
