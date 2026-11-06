import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { apiGet, openApp } from '../support';
import { readProjectFile, readZipEntries, reopenProject, seedProject, writeProjectFile, zipFileNames } from './helpers';

// Jornada «project-export-selection-zip»: el usuario selecciona dos elementos del árbol con Ctrl+clic, elige «Export
// selection as ZIP» y descarga el archivo. Oráculo: el ZIP descargado se abre con un lector independiente (no el del
// backend) y se compara byte a byte con los ficheros reales del proyecto en disco.

const SELECTED_A = { rel: 'metamodels/tienda.metamodel.json', text: '{ "id": "tienda", "nombre": "Ñandú ✓", "clases": [] }\n' };
const SELECTED_B = { rel: 'models/semantic/pedido.model.json', text: '{\n  "id": "pedido-1",\n  "lineas": [1, 2, 3]\n}\n' };
const NOT_SELECTED = { rel: 'behavior/validations/reglas.validations.json', text: '{ "reglas": ["no-exportar"] }\n' };

const row = (page: Page, label: string) => page.getByRole('tree', { name: 'Project files' }).locator('.project-tree-row')
    .filter({ has: page.locator('.project-tree-label', { hasText: new RegExp(`^${label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) }) });

test.describe('exportar la selección como ZIP', () => {
    test('el ZIP descargado contiene exactamente los ficheros seleccionados con los mismos bytes', async ({ page, request }, testInfo) => {
        const project = await seedProject(request, 'export');
        for (const file of [SELECTED_A, SELECTED_B, NOT_SELECTED]) writeProjectFile(project, file.rel, file.text);
        await reopenProject(request, project);

        await openApp(page, '/#/home');
        await expect(row(page, 'tienda.metamodel.json')).toBeVisible();

        // Seleccionar varios elementos (project_author_tree.d / project_import_export.x1)
        await row(page, 'tienda.metamodel.json').click();
        await row(page, 'pedido.model.json').click({ modifiers: ['Control'] });
        await expect(page.locator('.project-selection-count')).toContainText('2 selected items');
        await expect(row(page, 'reglas.validations.json')).toHaveAttribute('aria-selected', 'false');

        // Menú contextual sobre la selección: se ofrecen los dos destinos y se elige el ZIP local
        await row(page, 'pedido.model.json').click({ button: 'right' });
        const menu = page.getByRole('menu');
        await expect(menu).toBeVisible();
        const zipItem = menu.getByRole('menuitem', { name: /Export selection as ZIP|Exportar selección como ZIP/ });
        await expect(zipItem).toBeEnabled();
        await expect(menu.getByRole('menuitem', { name: /Export to repository|Exportar a repositorio/ })).toBeEnabled();

        const [download] = await Promise.all([page.waitForEvent('download'), zipItem.click()]);
        expect(download.suggestedFilename()).toBe(`${project.id}-selection.zip`);
        const saved = testInfo.outputPath('seleccion.zip');
        await download.saveAs(saved);

        // Oráculo independiente: lector ZIP propio + ficheros del disco
        const entries = readZipEntries(fs.readFileSync(saved));
        expect(zipFileNames(entries)).toEqual([SELECTED_A.rel, SELECTED_B.rel].sort());
        expect(entries.get(SELECTED_A.rel)?.equals(readProjectFile(project, SELECTED_A.rel))).toBe(true);
        expect(entries.get(SELECTED_B.rel)?.equals(readProjectFile(project, SELECTED_B.rel))).toBe(true);
        expect(entries.get(SELECTED_A.rel)?.toString('utf8')).toBe(SELECTED_A.text);
        expect(entries.has(NOT_SELECTED.rel)).toBe(false);
        expect(entries.has('modeling-project.json')).toBe(false);
        expect(fs.existsSync(path.join(project.directory, NOT_SELECTED.rel))).toBe(true); // el origen no se toca
    });

    test('no se puede exportar la raíz del proyecto como selección ni una selección vacía (rechazo)', async ({ page, request }) => {
        const project = await seedProject(request, 'export-no');
        writeProjectFile(project, SELECTED_A.rel, SELECTED_A.text);
        await reopenProject(request, project);

        await openApp(page, '/#/home');
        // Con la raíz seleccionada, la interfaz deshabilita «Export selection as ZIP»
        await row(page, project.name).click({ button: 'right' });
        const menu = page.getByRole('menu');
        await expect(menu).toBeVisible();
        await expect(menu.getByRole('menuitem', { name: /Export selection as ZIP|Exportar selección como ZIP/ })).toBeDisabled();
        await page.keyboard.press('Escape');

        // El backend real también rechaza esas peticiones y las rutas inexistentes
        const empty = await request.post('/api/projects/active/export-selection', { data: { paths: [] } });
        expect(empty.ok()).toBe(false);
        const root = await request.post('/api/projects/active/export-selection', { data: { paths: [''] } });
        expect(root.ok()).toBe(false);
        const missing = await request.post('/api/projects/active/export-selection', { data: { paths: ['no/existe.json'] } });
        expect(missing.ok()).toBe(false);
        expect(missing.status()).toBeGreaterThanOrEqual(400);
    });

    // Elegir destino (ZIP o repositorio): con la MISMA selección, cada opción del menú lleva a un camino distinto.
    // ZIP = descarga local sin conexión (test 1). Repositorio = diálogo que exige un perfil de repositorio configurado:
    // no descarga nada y no pide el ZIP al backend. El éxito de la exportación remota NO se prueba (servicio externo).
    test('«Exportar a repositorio» abre su diálogo, exige un perfil y no descarga ni empaqueta nada; el ZIP local no lo necesita', async ({ page, request }) => {
        const project = await seedProject(request, 'export-dest');
        for (const file of [SELECTED_A, SELECTED_B]) writeProjectFile(project, file.rel, file.text);
        await reopenProject(request, project);
        // Oráculo: perfiles de repositorio realmente utilizables según el backend
        const profiles = await apiGet<Array<{ id: string; enabled: boolean; configured: boolean }>>(request, '/api/remote-storage/profiles');
        const usable = profiles.filter(profile => profile.enabled && profile.configured);

        const downloads: string[] = [];
        const zipRequests: string[] = [];
        page.on('download', item => downloads.push(item.suggestedFilename()));
        page.on('request', item => { if (item.url().includes('/api/projects/active/export-selection')) zipRequests.push(item.url()); });

        // El menú contextual (12 órdenes) mide más de lo que ProjectExplorerWorkspace.tsx:265 reserva (304 px): «Exportar a repositorio»
        // queda fuera de la ventana y desplazarse cierra el menú, así que ese elemento se activa con un clic enviado al elemento.
        await openApp(page, '/#/home');
        await expect(row(page, 'tienda.metamodel.json')).toBeVisible();
        await row(page, 'tienda.metamodel.json').click();
        await row(page, 'pedido.model.json').click({ modifiers: ['Control'] });
        await expect(page.locator('.project-selection-count')).toContainText('2 selected items');

        // Destino «repositorio»
        await row(page, 'pedido.model.json').click({ button: 'right' });
        await expect(page.getByRole('menu')).toBeVisible();
        await page.getByRole('menu').getByRole('menuitem', { name: /Export to repository|Exportar a repositorio/ }).dispatchEvent('click');
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await expect(dialog.locator('.project-selection-remote-form')).toBeVisible();
        if (usable.length === 0) {
            // Sin perfil utilizable: la interfaz lo dice y el botón de exportar queda deshabilitado
            await expect(dialog.locator('.project-selection-remote-empty')).toBeVisible();
            await expect(dialog.locator('.hydra-ui-modal-footer button').last()).toBeDisabled();
        } else {
            await expect(dialog.locator('.project-selection-remote-empty')).toHaveCount(0);
            await expect(dialog.locator('.project-selection-remote-form input')).toHaveValue(`projects/${project.id}/${project.id}-selection.zip`);
        }
        expect(downloads).toEqual([]);
        expect(zipRequests).toEqual([]);
        await dialog.locator('.hydra-ui-modal-footer button').first().click(); // «Close»
        await expect(dialog).toHaveCount(0);
        expect(downloads).toEqual([]);

        // Destino «ZIP»: la misma selección se descarga localmente sin ningún perfil de repositorio
        await row(page, 'pedido.model.json').click({ button: 'right' });
        const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('menu').getByRole('menuitem', { name: /Export selection as ZIP|Exportar selección como ZIP/ }).click()]);
        expect(download.suggestedFilename()).toBe(`${project.id}-selection.zip`);
        expect(zipRequests).toHaveLength(1);
    });
});
