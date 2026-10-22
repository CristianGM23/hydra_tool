import { expect, test } from '@playwright/test';
import { uid } from '../support';
import { SAVED_MESSAGE, SAVE_EDITOR, createAndSaveMinimalEditor, loadEditorDoc, openEditors, saveExistingEditor, seedEditorMetamodel, type EditorDoc } from './editor-helpers';

// Jornada «editor-definition-structure-edit»: en la pestaña «Structure tree» el usuario elige un nodo del árbol de la
// definición, cambia un campo en el panel de propiedades y guarda. Oráculo: GET de la definición guardada (el campo del
// nodo elegido tiene el valor nuevo y el resto permanece igual). Interfaz en inglés.

/** Proyección estable de lo que el usuario puede haber tocado (evita falsos positivos por valores por defecto que añade el servidor). */
function projection(doc: EditorDoc) {
    return {
        id: doc.id, name: doc.name, metamodelId: doc.metamodelId,
        representations: doc.representations.map(rep => ({
            id: rep.id, name: rep.name, kind: rep.kind, rootClassIds: rep.rootClassIds, toolIds: rep.toolIds,
            nodeMappings: rep.nodeMappings.map(mapping => ({ id: mapping.id, targetClassId: mapping.targetClassId, representationName: mapping.representationName, shape: mapping.shape })),
            edgeMappings: rep.edgeMappings.map(mapping => ({ id: mapping.id, relationId: mapping.relationId })),
        })),
        groups: doc.palette.groups.map(group => ({ id: group.id, name: group.name, toolIds: group.toolIds })),
        tools: doc.palette.tools.map(tool => ({ id: tool.id, kind: tool.kind, label: tool.label, representationIds: tool.representationIds })),
        forms: doc.forms.map(form => ({ id: form.id, name: form.name, targetClassId: form.targetClassId })),
    };
}

test.describe('edición directa desde el árbol de estructura', () => {
    test('elegir un nodo, cambiar su campo y guardar persiste solo ese cambio', async ({ page, request }) => {
        const metamodel = await seedEditorMetamodel(request, 'eds');
        const editorId = await createAndSaveMinimalEditor(page, request, metamodel, uid('Editor'));
        const before = await loadEditorDoc(request, metamodel.id, editorId);
        const mappingBefore = before.representations[0].nodeMappings[0];
        expect(mappingBefore.representationName).toBe('Person');

        await openEditors(page, metamodel.id);
        const designer = page.locator('.editor-definition-designer');
        await designer.getByRole('button', { name: 'Structure tree', exact: true }).click();
        const panel = page.locator('.editor-structure-panel');
        await expect(panel).toBeVisible();

        // Sin cambios no se puede guardar
        await page.getByRole('button', { name: 'Changes', exact: true }).click();
        await expect(page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name: SAVE_EDITOR })).toBeDisabled();
        await page.keyboard.press('Escape');

        // Elegir un elemento de la definición (etapa sel): el nodo «Person» de «Node mappings»
        await panel.locator('button.editor-definition-tree-main[title="Node mappings"]').click();
        await panel.locator('button.editor-definition-tree-main[title*="node-mapping:"]').click();
        const properties = page.locator('.editor-structure-properties');
        const nameField = properties.locator('#node-mapping-name');
        await expect(nameField).toHaveValue('Person');
        await expect(properties.locator('#node-mapping-class')).toHaveValue(metamodel.personId);

        // Editar su campo (etapa ed) y guardar
        await nameField.fill('Persona');
        await saveExistingEditor(page);
        await expect(page.locator('.status-message')).toContainText(SAVED_MESSAGE);

        // Oráculo: el campo cambió y todo lo demás sigue igual
        const after = await loadEditorDoc(request, metamodel.id, editorId);
        expect(after.representations[0].nodeMappings[0].representationName).toBe('Persona');
        const expected = projection(before);
        expected.representations[0].nodeMappings[0].representationName = 'Persona';
        expect(projection(after)).toEqual(expected);
    });
});
