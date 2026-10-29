import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { apiGet, eventually, openApp } from '../support';
import { flattenTree, reopenProject, seedProject, session, writeProjectFile, type SeededProject, type TreeNodeJson } from './helpers';

// Jornada «project-tree-browse-open»: el usuario despliega grupos del árbol del proyecto, abre un fichero con doble clic,
// suma elementos con Ctrl+clic y ve el aviso cuando un fichero cambia fuera de HYDRA.
// Oráculo: GET /api/projects/active/tree y /active/file del backend real, y el contenido real del disco.

const FILES = {
    metamodel: { rel: 'metamodels/shop.metamodel.json', text: '{ "id": "shop", "classes": [] }\n', kind: 'METAMODEL' },
    model: { rel: 'models/semantic/order.model.json', text: '{ "id": "order-1", "type": "Order" }\n', kind: 'MODEL' },
    rules: { rel: 'behavior/validations/rules.validations.json', text: '{ "rules": [] }\n', kind: 'VALIDATION_MODEL' },
} as const;

const tree = (page: Page) => page.getByRole('tree', { name: 'Project files' });
const row = (page: Page, label: string) => tree(page).locator('.project-tree-row')
    .filter({ has: page.locator('.project-tree-label', { hasText: new RegExp(`^${label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) }) });
const tabsOf = (page: Page) => page.getByRole('tablist', { name: 'Open project files' }).getByRole('tab');

async function seedWithFiles(request: import('@playwright/test').APIRequestContext): Promise<SeededProject> {
    const project = await seedProject(request, 'tree');
    for (const file of Object.values(FILES)) writeProjectFile(project, file.rel, file.text);
    await reopenProject(request, project); // reinicia la línea base del detector de cambios con los ficheros ya presentes
    return project;
}

test.describe('árbol del proyecto', () => {
    test('despliega grupos, abre un metamodelo con doble clic y suma a la selección con Ctrl+clic', async ({ page, request }) => {
        const project = await seedWithFiles(request);
        const apiTree = await apiGet<TreeNodeJson>(request, '/api/projects/active/tree?view=LOGICAL');
        const nodes = flattenTree(apiTree);
        const groups = nodes.filter(node => node.nodeType === 'GROUP');
        const byPath = (rel: string) => nodes.find(node => node.relativePath === rel);
        for (const file of Object.values(FILES)) {
            expect(byPath(file.rel), `el backend debe listar ${file.rel} en el árbol lógico`).toBeDefined();
            expect(byPath(file.rel)?.artifactKind).toBe(file.kind);
        }

        await openApp(page, '/#/home');
        await expect(tree(page)).toBeVisible();

        // Desplegar grupos (etapa a): una fila por grupo y por fichero, con el tipo de artefacto que da el backend
        for (const group of groups) await expect(row(page, group.name)).toBeVisible();
        for (const file of Object.values(FILES)) {
            const node = byPath(file.rel) as TreeNodeJson;
            await expect(row(page, node.name)).toBeVisible();
            await expect(row(page, node.name)).toHaveAttribute('title', `${node.artifactKind} · ${node.relativePath}`);
        }
        // Plegar y desplegar un grupo con contenido
        const metamodelGroup = groups.find(group => group.relativePath === 'metamodels') as TreeNodeJson;
        await row(page, metamodelGroup.name).click();
        await expect(row(page, 'shop.metamodel.json')).toHaveCount(0);
        await row(page, metamodelGroup.name).click();
        await expect(row(page, 'shop.metamodel.json')).toBeVisible();

        // Rechazo: doble clic sobre un grupo no abre ningún fichero
        const modelsGroup = groups.find(group => group.relativePath === 'models/semantic' || group.name === 'Models') as TreeNodeJson;
        await row(page, modelsGroup.name).dblclick();
        await expect(tabsOf(page)).toHaveCount(0);

        // Doble clic en un metamodelo: se abre en su editor con el contenido real del disco (etapas b y c)
        await row(page, 'shop.metamodel.json').dblclick();
        await expect(tabsOf(page)).toHaveCount(1);
        await expect(tabsOf(page).first()).toContainText('shop.metamodel.json');
        const editor = page.getByRole('textbox', { name: 'Content of shop.metamodel.json' });
        await expect(editor).toHaveValue(fs.readFileSync(path.join(project.directory, FILES.metamodel.rel), 'utf8'));
        await expect(page.locator('.project-file-editor header')).toContainText('METAMODEL');
        const served = await apiGet<{ content: string; artifactKind: string }>(request, `/api/projects/active/file?path=${encodeURIComponent(FILES.metamodel.rel)}`);
        expect(served.content).toBe(FILES.metamodel.text);
        expect(served.artifactKind).toBe('METAMODEL');

        // Ctrl+clic suma a la selección sin abrir nada; un clic simple vuelve a una sola fila
        await row(page, 'rules.validations.json').click({ modifiers: ['Control'] });
        await expect(page.locator('.project-selection-count')).toContainText('2 selected items');
        await expect(row(page, 'shop.metamodel.json')).toHaveAttribute('aria-selected', 'true');
        await expect(row(page, 'rules.validations.json')).toHaveAttribute('aria-selected', 'true');
        await expect(tabsOf(page)).toHaveCount(1);
        await row(page, 'order.model.json').click();
        await expect(page.locator('.project-selection-count')).toHaveCount(0);
        await expect(row(page, 'order.model.json')).toHaveAttribute('aria-selected', 'true');
        await expect(row(page, 'shop.metamodel.json')).toHaveAttribute('aria-selected', 'false');
        await expect(tabsOf(page)).toHaveCount(1);
    });

    test('un fichero modificado fuera de HYDRA se marca en el árbol y «Reload» trae el contenido nuevo', async ({ page, request }) => {
        const project = await seedWithFiles(request);
        await openApp(page, '/#/home');
        await row(page, 'shop.metamodel.json').dblclick();
        const editor = page.getByRole('textbox', { name: 'Content of shop.metamodel.json' });
        await expect(editor).toHaveValue(FILES.metamodel.text);
        await expect(row(page, 'order.model.json')).toBeVisible();
        await expect(tree(page).locator('.project-tree-change')).toHaveCount(0);

        // Cambio externo: se reescribe en disco el metamodelo con otro tamaño; el resto de ficheros no se toca
        const edited = '{ "id": "shop", "classes": [ { "name": "EditadoFuera" } ] }\n';
        writeProjectFile(project, FILES.metamodel.rel, edited);

        // El sondeo de cambios de la interfaz (cada ~5 s) debe marcar solo ese fichero
        await expect(row(page, 'shop.metamodel.json').locator('.project-tree-change')).toBeVisible({ timeout: 30_000 });
        await expect(row(page, 'order.model.json').locator('.project-tree-change')).toHaveCount(0);
        await expect(row(page, 'rules.validations.json').locator('.project-tree-change')).toHaveCount(0);
        await expect(row(page, 'shop.metamodel.json')).toHaveClass(/changed/);

        // Oráculo independiente: el backend sirve el contenido nuevo del disco
        const served = await apiGet<{ content: string }>(request, `/api/projects/active/file?path=${encodeURIComponent(FILES.metamodel.rel)}`);
        expect(served.content).toBe(edited);

        // El usuario revisa el cambio recargando el fichero abierto: aparece el texto nuevo y se limpia el aviso
        await page.getByRole('button', { name: /^(Reload|Recargar)$/ }).click();
        await expect(editor).toHaveValue(edited);
        await expect(row(page, 'shop.metamodel.json').locator('.project-tree-change')).toHaveCount(0);
    });

    // Decisión «¿abrir o seleccionar?»: el doble clic abre UN fichero (el backend lo registra como pestaña abierta) y
    // el Ctrl+clic suma a la selección SIN abrir nada; la selección múltiple tiene un efecto real sobre el disco
    // (Eliminar actúa solo sobre lo seleccionado, y cancelar no toca nada). Oráculo: disco, sesión y árbol del backend real.
    test('doble clic abre un solo fichero y Ctrl+clic selecciona varios: Eliminar actúa solo sobre la selección', async ({ page, request }) => {
        const project = await seedWithFiles(request);
        const onDisk = (rel: string): boolean => fs.existsSync(path.join(project.directory, rel));
        await openApp(page, '/#/home');
        await expect(row(page, 'shop.metamodel.json')).toBeVisible();

        // Abrir: doble clic en un fichero -> una pestaña y el backend la guarda como pestaña abierta del proyecto
        await row(page, 'rules.validations.json').dblclick();
        await expect(tabsOf(page)).toHaveCount(1);
        await expect(tabsOf(page).first()).toContainText('rules.validations.json');
        const afterOpen = await eventually(() => session(request), value => value.restoredTabs.length > 0);
        expect(afterOpen.restoredTabs).toEqual([FILES.rules.rel]);

        // Seleccionar: clic + Ctrl+clic suman dos filas y no abren nada nuevo (ni el backend registra más pestañas)
        await row(page, 'shop.metamodel.json').click();
        await row(page, 'order.model.json').click({ modifiers: ['Control'] });
        await expect(page.locator('.project-selection-count')).toContainText('2 selected items');
        await expect(row(page, 'rules.validations.json')).toHaveAttribute('aria-selected', 'false');
        await expect(tabsOf(page)).toHaveCount(1);
        expect((await session(request)).restoredTabs).toEqual([FILES.rules.rel]);

        // Efecto real de la selección, rechazo primero: cancelar el borrado no toca ningún fichero
        // (botón «Delete» de la barra del explorador: el menú contextual es largo y su último ítem queda fuera del viewport;
        // al desplazarlo Playwright, el menú se cierra por el evento scroll)
        const deleteButton = page.getByRole('button', { name: 'Delete', exact: true });
        await deleteButton.click();
        const decision = page.getByRole('dialog');
        await expect(decision).toBeVisible();
        await decision.locator('.hydra-ui-modal-footer button').first().click(); // «Cancel»
        await expect(decision).toHaveCount(0);
        for (const file of Object.values(FILES)) expect(onDisk(file.rel), `${file.rel} no debe borrarse al cancelar`).toBe(true);

        // Confirmar: solo desaparecen los dos ficheros seleccionados; el tercero (abierto en pestaña) sigue en disco
        await expect(page.locator('.project-selection-count')).toContainText('2 selected items'); // la selección sobrevive a cancelar
        await deleteButton.click();
        await expect(decision).toBeVisible();
        await decision.locator('.hydra-ui-modal-footer button').last().click(); // «Delete»
        await expect(row(page, 'shop.metamodel.json')).toHaveCount(0);
        await expect(row(page, 'order.model.json')).toHaveCount(0);
        expect(onDisk(FILES.metamodel.rel)).toBe(false);
        expect(onDisk(FILES.model.rel)).toBe(false);
        expect(onDisk(FILES.rules.rel)).toBe(true);
        const apiTree = await apiGet<TreeNodeJson>(request, '/api/projects/active/tree?view=PHYSICAL');
        const remaining = flattenTree(apiTree).map(node => node.relativePath);
        expect(remaining).toContain(FILES.rules.rel);
        expect(remaining).not.toContain(FILES.metamodel.rel);
        expect(remaining).not.toContain(FILES.model.rel);
        await expect(row(page, 'rules.validations.json')).toBeVisible();
    });

    // Regresión del defecto «abrir varios ficheros a la vez guarda solo la última pestaña»: «Open» del menú contextual sobre una
    // selección múltiple abre todos los ficheros y el backend registra TODAS las pestañas abiertas. Oráculo: sesión del backend real.
    test('«Open» sobre una selección múltiple abre todos los ficheros y el backend los registra como pestañas', async ({ page, request }) => {
        await seedWithFiles(request);
        await openApp(page, '/#/home');
        await expect(row(page, 'shop.metamodel.json')).toBeVisible();
        await row(page, 'shop.metamodel.json').click();
        await row(page, 'order.model.json').click({ modifiers: ['Control'] });
        await expect(page.locator('.project-selection-count')).toContainText('2 selected items');

        await row(page, 'shop.metamodel.json').click({ button: 'right' });
        await page.getByRole('menu').getByRole('menuitem', { name: /^(Open|Abrir)$/ }).click();
        await expect(tabsOf(page)).toHaveCount(2);
        const registered = await eventually(() => session(request), value => value.restoredTabs.length >= 2);
        expect([...registered.restoredTabs].sort()).toEqual([FILES.metamodel.rel, FILES.model.rel].sort());
    });
});
