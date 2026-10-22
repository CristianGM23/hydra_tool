import { expect, test, type APIResponse } from '@playwright/test';
import { uid } from '../support';
import {
    COMPILE_RUNTIME, SAVED_MESSAGE, createAndSaveMinimalEditor, editorRoute, loadEditorDoc, openEditors,
    runDesignerCommand, saveExistingEditor, seedEditorMetamodel,
} from './editor-helpers';

// Jornada «editor-compile-runtime-view»: con una definición de editor guardada, el usuario pulsa «Compile runtime» y la
// interfaz pasa a «Runtime preview» mostrando el plan compilado. Oráculo: el plan que sirve la API real de ejecución
// (sourceEtag = etag actual de la definición, mismo nombre) y la definición guardada. Interfaz en inglés.

const normalizeEtag = (value: string): string => value.replace(/^W\//, '').replace(/"/g, '');

interface RuntimeResult { plan: { sourceEtag: string; sourceEditorDefinitionId: string; name: string; representationsById: Record<string, unknown> }; cacheHit: boolean }

test.describe('compilar el runtime y ver el resultado', () => {
    test('no se puede compilar con cambios sin guardar; guardado, compila y muestra la vista Ejecución con el plan del backend', async ({ page, request }) => {
        const metamodel = await seedEditorMetamodel(request, 'edc');
        const firstName = uid('Editor');
        const editorId = await createAndSaveMinimalEditor(page, request, metamodel, firstName);

        // Abrir la definición guardada desde cero
        await openEditors(page, metamodel.id);
        const designer = page.locator('.editor-definition-designer');
        await expect(designer.locator('.hydra-sidebar-heading small')).toContainText('1 representation(s) · 1 tool(s)');
        await expect(designer.getByRole('button', { name: 'Definition', exact: true })).toHaveClass(/active/);

        // Rechazo: con un cambio sin guardar, «Compile runtime» está bloqueado y la vista no cambia
        const secondName = `${firstName}-v2`;
        // (Reabrir con el mismo hash no recarga la página: los ajustes del editor pueden seguir desplegados.)
        const showSettings = designer.locator('.editor-document-disclosure').getByRole('button', { name: 'Show', exact: true });
        if (await showSettings.count() > 0) await showSettings.click();
        await page.locator('#editor-definition-name').fill(secondName);
        await page.getByRole('button', { name: 'Generation', exact: true }).click();
        await expect(page.locator('.hydra-designer-dropdown-popup').getByRole('button', { name: COMPILE_RUNTIME })).toBeDisabled();
        await page.keyboard.press('Escape');
        await expect(designer.getByRole('button', { name: 'Definition', exact: true })).toHaveClass(/active/);
        expect((await loadEditorDoc(request, metamodel.id, editorId)).name).toBe(firstName);

        // Guardar el cambio y compilar
        await saveExistingEditor(page);
        await expect(page.locator('.status-message')).toContainText(SAVED_MESSAGE);
        expect((await loadEditorDoc(request, metamodel.id, editorId)).name).toBe(secondName);
        await runDesignerCommand(page, 'Generation', COMPILE_RUNTIME);

        // La interfaz salta a la vista Ejecución con el plan compilado (etapas vw)
        await expect(designer.getByRole('button', { name: 'Runtime preview', exact: true })).toHaveClass(/active/);
        const preview = page.locator('.editor-runtime-preview');
        await expect(preview).toBeVisible();
        await expect(preview.locator('.editor-runtime-metadata')).toContainText(secondName);
        await expect(preview.locator('.editor-runtime-metadata')).toContainText('1 representation(s)');
        await expect(page.locator('.status-message')).toContainText(/Editor runtime compiled|Compiled editor runtime loaded from cache/);

        // Oráculo independiente: el plan del backend corresponde a la definición guardada actual
        const definition: APIResponse = await request.get(editorRoute(metamodel.id, editorId));
        expect(definition.ok()).toBeTruthy();
        const currentEtag = normalizeEtag(definition.headers()['etag'] ?? '');
        expect(currentEtag).not.toBe('');
        const runtimeResponse = await request.get(`${editorRoute(metamodel.id, editorId)}/runtime`);
        expect(runtimeResponse.ok()).toBeTruthy();
        const runtime = (await runtimeResponse.json()) as RuntimeResult;
        expect(runtime.plan.sourceEditorDefinitionId).toBe(editorId);
        expect(runtime.plan.name).toBe(secondName);
        expect(normalizeEtag(runtime.plan.sourceEtag)).toBe(currentEtag);
        expect(Object.keys(runtime.plan.representationsById)).toHaveLength(1);
    });
});
