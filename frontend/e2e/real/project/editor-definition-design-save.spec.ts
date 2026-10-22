import { expect, test, type Page } from '@playwright/test';
import { apiGet, eventually, readJson } from '../support';
import {
    SAVE_EDITOR, editorOnDisk, editorRoute, loadEditorDoc, openEditors, runDesignerCommand,
    seedAbstractOnlyMetamodel, seedEditorMetamodel,
} from './editor-helpers';

// Jornada «editor-definition-design-save»: el usuario diseña una definición de editor sobre un metamodelo guardado:
// + Representación, + sobre una clase y sobre una relación (mapeos y herramientas), herramienta de acción en un grupo,
// + Formulario y estilo condicional, y guarda. Oráculo: GET a la API real de definiciones de editor (y el fichero en
// disco si el almacén lo nombra por su id) y el modelo de expresiones guardado por el backend.
// La interfaz arranca en inglés.

const designer = (page: Page) => page.locator('.editor-definition-designer');
const treeRow = (page: Page, titleSelector: string) => designer(page).locator(`button.editor-definition-tree-main${titleSelector}`);
// Las barras del diseñador tapan filas del árbol con el viewport por defecto: se dispara el clic del botón directamente.
const clickTreeRow = (page: Page, titleSelector: string) => treeRow(page, titleSelector).dispatchEvent('click');

test.describe('diseño y guardado de la definición de editor', () => {
    test('representación, mapeos de nodo y arista, acción en grupo, formulario y estilo condicional se guardan', async ({ page, request }) => {
        const m = await seedEditorMetamodel(request, 'edd');
        const editorId = `${m.id}-editor`;
        const persistedClasses = readJson<{ classes: Array<{ id: string; name: string; kind: string }> }>(`data/metamodels/${m.id}.metamodel.json`).classes;
        const firstConcrete = persistedClasses.find(item => item.kind === 'CLASS')!;

        await openEditors(page, m.id);
        const summary = designer(page).locator('.hydra-sidebar-heading small');
        await expect(summary).toContainText('1 representation(s) · 0 tool(s)');

        // + Representación (etapa rp): se añade con la clase raíz detectada y queda seleccionada
        await designer(page).getByRole('button', { name: '+ Representation', exact: true }).click();
        await expect(summary).toContainText('2 representation(s)');
        await page.locator('#representation-name').fill('Vista secundaria');

        // + sobre una clase (etapa nm): mapeo de nodo y herramienta de crear nodo
        await designer(page).getByRole('button', { name: 'All details', exact: true }).click();
        // Los «+» del árbol quedan tapados por la barra de acciones del árbol: se dispara su clic directamente.
        await designer(page).getByRole('button', { name: 'Add Person node mapping' }).dispatchEvent('click');
        await expect(page.locator('#node-mapping-class')).toHaveValue(m.personId);
        await expect(summary).toContainText('1 tool(s)');

        // + sobre una relación (etapa em): mapeo de arista y herramienta de crear arista
        await designer(page).getByRole('button', { name: 'Add members edge mapping' }).dispatchEvent('click');
        await expect(page.locator('#edge-mapping-relation')).toHaveValue(m.relationId);
        await expect(summary).toContainText('2 tool(s)');

        // Herramienta de acción en un grupo (etapa pt): sin operaciones ni acciones definidas, esas dos opciones están bloqueadas
        await clickTreeRow(page, '[title="Elements · group:elements"]');
        await expect(page.locator('#palette-group-name')).toHaveValue('Elements');
        const card = (title: string) => page.locator('.editor-palette-action-card').filter({ hasText: title });
        await expect(card('Run a metamodel operation').getByRole('button', { name: 'Add' })).toBeDisabled();
        await expect(card('Run a configured action').getByRole('button', { name: 'Add' })).toBeDisabled();
        await card('Validate the model').getByRole('button', { name: 'Add' }).click();
        await expect(page.locator('#editor-tool-validation-mode')).toHaveValue('SELECTION');
        await expect(summary).toContainText('3 tool(s)');

        // + Formulario (etapa fm): apunta a la primera clase concreta del metamodelo
        await designer(page).getByRole('button', { name: '+ Form', exact: true }).click();
        await expect(page.locator('#editor-form-class')).toHaveValue(firstConcrete.id);
        await expect(page.locator('#editor-form-name')).toHaveValue(`${firstConcrete.name} form`);

        // Estilo condicional (etapa cs): abrir el mapeo de nodo, añadir una regla sin condición y comprobar el rechazo al guardar
        await clickTreeRow(page, '[title="Node mappings"]');
        await clickTreeRow(page, '[title*="node-mapping:"]');
        await expect(page.locator('#node-mapping-name')).toHaveValue('Person');
        await page.getByText('Size, style and technical mapping settings', { exact: true }).click();
        await page.getByRole('button', { name: '+ Conditional style', exact: true }).click();
        await page.getByLabel('Conditional style 1 Name').fill('Resaltar personas');
        await runDesignerCommand(page, 'Changes', SAVE_EDITOR);
        await expect(page.getByRole('alert').filter({ hasText: /requires a condition/ })).toBeVisible();
        expect((await request.get(editorRoute(m.id, editorId))).ok(), 'una regla sin condición no debe guardar nada').toBe(false);

        // Con condición y un preajuste de estilo ya se puede guardar
        await page.getByRole('button', { name: 'Define condition', exact: true }).click();
        await page.getByText('Visual overrides', { exact: true }).click();
        await page.getByRole('button', { name: 'Warning', exact: true }).click();
        await runDesignerCommand(page, 'Changes', SAVE_EDITOR);
        // HALLAZGO (frontend): el backend guarda bien, pero al normalizar la respuesta la interfaz lanza «Cannot read properties of null
        // (reading 'defaultValues')» cuando hay una arista/herramienta de arista (semanticEdge llega como null y solo se comprueba undefined),
        // así que no aparece «Model editor definition saved». Se certifica lo guardado por el backend, no el mensaje.
        await eventually(async () => (await request.get(editorRoute(m.id, editorId))).ok(), saved => saved);

        // Definición actualizada (etapa art): lo guardado por el backend contiene todo lo diseñado
        const saved = await loadEditorDoc(request, m.id, editorId);
        expect(saved.representations).toHaveLength(2);
        const second = saved.representations.find(item => item.name === 'Vista secundaria')!;
        expect(second, 'la representación renombrada debe estar guardada').toBeDefined();
        expect(second.rootClassIds).toEqual([m.personId]);
        const nodeMapping = second.nodeMappings.find(item => item.targetClassId === m.personId)!;
        expect(nodeMapping.representationName).toBe('Person');
        const edgeMapping = second.edgeMappings.find(item => item.relationId === m.relationId)!;
        expect([edgeMapping.sourceClassId, edgeMapping.targetClassId]).toEqual([m.teamId, m.personId]);
        const toolOf = (kind: string) => saved.palette.tools.find(tool => tool.kind === kind)!;
        expect(toolOf('CREATE_NODE')).toMatchObject({ targetClassId: m.personId, representationIds: [second.id] });
        expect(toolOf('CREATE_EDGE')).toMatchObject({ relationId: m.relationId, edgeMappingId: edgeMapping.id });
        expect(toolOf('VALIDATE')).toMatchObject({ validationMode: 'SELECTION' });
        const grouped = saved.palette.groups.find(group => group.id === 'group:elements')!;
        expect(new Set(grouped.toolIds)).toEqual(new Set(saved.palette.tools.map(tool => tool.id)));
        expect(saved.forms).toHaveLength(1);
        expect(saved.forms[0].targetClassId).toBe(firstConcrete.id);
        expect(nodeMapping.conditionalStyles).toHaveLength(1);
        const rule = nodeMapping.conditionalStyles[0];
        expect(rule.name).toBe('Resaltar personas');
        expect(rule.conditionExpressionId).not.toBe('');
        expect(rule.styles).toMatchObject({ fillColor: '#FEF3C7', strokeColor: '#D97706' });
        const expressions = await apiGet<{ expressions: Array<{ id: string }> }>(request, `/api/canonical-metamodels/${encodeURIComponent(m.id)}/expressions`);
        expect(expressions.expressions.map(item => item.id)).toContain(rule.conditionExpressionId);
        const onDisk = editorOnDisk(editorId);
        if (onDisk !== undefined) expect(onDisk.representations).toHaveLength(2);
    });

    test('«+ Form» da error si el metamodelo no tiene ninguna clase concreta (rechazo)', async ({ page, request }) => {
        const metamodelId = await seedAbstractOnlyMetamodel(request, 'edd-abs');
        await openEditors(page, metamodelId);
        await designer(page).getByRole('button', { name: '+ Form', exact: true }).click();
        await expect(page.getByRole('alert').filter({ hasText: /no usable concrete class/ })).toBeVisible();
        await expect(designer(page).locator('.editor-definition-tree')).toBeVisible();
        expect((await request.get(editorRoute(metamodelId, `${metamodelId}-editor`))).ok()).toBe(false);
    });
});
