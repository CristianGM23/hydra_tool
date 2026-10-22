import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { apiGet, eventually, openApp, uid, workDir } from '../support';
import { flattenTree, readZipEntries, seedProject, session, writeProjectFile, zipFileNames, type TreeNodeJson } from './helpers';

// Jornada «project-import-local-zip»: el usuario abre el diálogo de importación desde Inicio, elige el origen «ZIP file»,
// selecciona un archivo ZIP de proyecto, indica destino y nombre y confirma. Oráculo: el ZIP se lee con un lector
// independiente y sus ficheros se comparan byte a byte con la carpeta que el backend creó; más sesión y árbol de la API.
// El ZIP de partida se obtiene exportando un proyecto sembrado con la API real (no hay otro ZIP de proyecto en el repositorio).

const TEXT_A = { rel: 'metamodels/biblioteca.metamodel.json', bytes: Buffer.from('{ "id": "biblioteca", "nombre": "Biblioteca ñ ✓" }\n', 'utf8') };
const TEXT_B = { rel: 'models/semantic/libro.model.json', bytes: Buffer.from('{\n  "id": "libro-1",\n  "titulo": "Don Quijote"\n}\n', 'utf8') };
const BINARY = { rel: 'assets/pixel.bin', bytes: Buffer.from(Array.from({ length: 1024 }, (_, i) => (i * 37 + 11) % 256)) };

async function openImportDialog(page: Page) {
    const card = page.locator('.hydra-home-action-card').filter({ has: page.getByRole('heading', { name: /^(Bring an existing project|Traer un proyecto existente)$/ }) });
    await card.locator('footer').getByRole('button').click();
    const dialog = page.locator('.project-manager-dialog');
    await expect(dialog).toBeVisible();
    // Las opciones de origen disponibles (etapa «Elegir el origen»)
    for (const label of [/Local project/, /ZIP file/, /External project/, /Project folder/, /Repository/]) {
        await expect(dialog.locator('.project-source-options-wide').getByRole('button', { name: label })).toBeVisible();
    }
    await dialog.locator('.project-source-options-wide').getByRole('button', { name: /^(ZIP file|Archivo ZIP)/ }).click();
    return dialog;
}

async function fillImport(page: Page, zipPath: string, parent: string, name: string) {
    const dialog = await openImportDialog(page);
    await dialog.locator('#project-import-parent').fill(parent);
    await dialog.locator('#project-import-name').fill(name);
    await dialog.locator('#project-import-file').setInputFiles(zipPath);
    await expect(dialog).toContainText(path.basename(zipPath));
    return dialog;
}

test.describe('importar un proyecto desde un ZIP local', () => {
    test('el ZIP elegido se importa como proyecto activo con los mismos ficheros y bytes; los rechazos no dejan nada', async ({ page, request }, testInfo) => {
        // Fixture: proyecto fuente con contenido conocido, exportado por la API real
        const source = await seedProject(request, 'zipsrc');
        for (const file of [TEXT_A, TEXT_B, BINARY]) writeProjectFile(source, file.rel, file.bytes);
        const exported = await request.get('/api/projects/active/export');
        expect(exported.ok()).toBeTruthy();
        const zipPath = testInfo.outputPath('proyecto-fuente.zip');
        fs.writeFileSync(zipPath, await exported.body());
        const archive = readZipEntries(fs.readFileSync(zipPath));
        for (const file of [TEXT_A, TEXT_B, BINARY, { rel: 'modeling-project.json' }]) expect(archive.has(file.rel), `el ZIP debe contener ${file.rel}`).toBe(true);

        const destParent = path.join(workDir, 'e2e-projects', uid('imp'));
        const importedName = uid('importado');
        const destination = path.join(destParent, importedName);
        expect(fs.existsSync(destination)).toBe(false);

        await openApp(page, '/#/home');
        const dialog = await fillImport(page, zipPath, destParent, importedName);
        await dialog.locator('footer .project-primary-action').click();
        await expect(page.locator('.project-manager-dialog')).toHaveCount(0);

        // Oráculo: sesión real del backend, carpeta creada y bytes idénticos a los del ZIP
        const imported = await eventually(() => session(request), value => value.project?.id === source.id && path.resolve(value.directory).toLowerCase() === path.resolve(destination).toLowerCase());
        expect(imported.active).toBe(true);
        const names = zipFileNames(archive);
        expect(names.length).toBeGreaterThanOrEqual(4);
        // Los metadatos internos (.modeling/…, p. ej. el índice y la sesión) los regenera el backend al abrir: solo se comparan los ficheros del usuario.
        for (const name of names.filter(item => !item.startsWith('.modeling/'))) {
            const target = path.join(destination, name);
            expect(fs.existsSync(target), `falta ${name} en el proyecto importado`).toBe(true);
            expect(fs.readFileSync(target).equals(archive.get(name) as Buffer), `bytes distintos en ${name}`).toBe(true);
        }
        expect(fs.readFileSync(path.join(destination, BINARY.rel)).equals(BINARY.bytes)).toBe(true);
        const tree = await apiGet<TreeNodeJson>(request, '/api/projects/active/tree?view=PHYSICAL');
        const treePaths = flattenTree(tree).map(node => node.relativePath);
        for (const file of [TEXT_A, TEXT_B, BINARY]) expect(treePaths).toContain(file.rel);
        await expect(page.locator('#hydra-home-title')).toHaveText(source.name);

        // Rechazo 1: importar otra vez en la misma carpeta de destino falla y no modifica lo importado
        const again = await fillImport(page, zipPath, destParent, importedName);
        await again.locator('footer .project-primary-action').click();
        await expect(again).toBeVisible();
        await expect(again.getByRole('alert')).toBeVisible();
        expect(fs.readFileSync(path.join(destination, TEXT_A.rel)).equals(TEXT_A.bytes)).toBe(true);
        await again.getByRole('button', { name: /^(Cancel|Cancelar)$/ }).click();
        await expect(page.locator('.project-manager-dialog')).toHaveCount(0);

        // Rechazo 2: un archivo que no es un ZIP de proyecto no deja ninguna carpeta a medias
        const badZip = testInfo.outputPath('no-es-un-proyecto.zip');
        fs.writeFileSync(badZip, Buffer.from('esto no es un archivo ZIP de ningún proyecto HYDRA'));
        const badName = uid('invalido');
        const bad = await fillImport(page, badZip, destParent, badName);
        await bad.locator('footer .project-primary-action').click();
        await expect(bad.getByRole('alert')).toBeVisible();
        expect(fs.existsSync(path.join(destParent, badName))).toBe(false);
        expect((await session(request)).project?.id).toBe(source.id);
        expect(path.resolve((await session(request)).directory).toLowerCase()).toBe(path.resolve(destination).toLowerCase());
    });

    // Elegir el origen: cada origen del diálogo lleva a un formulario y un camino distintos. ZIP: test anterior.
    // «Project folder»: importa de verdad una carpeta de proyecto del servidor. «Repository» y «External project»: su
    // formulario propio impide confirmar sin los datos que exigen (perfil/ruta remota, análisis previo) y no cambia nada.
    // El éxito de importar desde repositorio o proyecto externo NO se prueba aquí (servicio externo / fixtures propios).
    test('cada origen abre su propio formulario: la carpeta de proyecto se importa y repositorio/externo exigen sus datos', async ({ page, request }) => {
        const source = await seedProject(request, 'dirsrc');
        for (const file of [TEXT_A, TEXT_B, BINARY]) writeProjectFile(source, file.rel, file.bytes);
        const destParent = path.join(workDir, 'e2e-projects', uid('impdir'));
        const importedName = uid('carpeta');
        const destination = path.join(destParent, importedName);

        await openApp(page, '/#/home');
        const dialog = await openImportDialog(page);
        const sources = dialog.locator('.project-source-options-wide button'); // orden: local, ZIP, externo, carpeta, repositorio
        await expect(sources).toHaveCount(5);
        await expect(sources.nth(1)).toHaveAttribute('aria-pressed', 'true'); // ZIP elegido por openImportDialog
        await expect(dialog.locator('#project-import-file')).toHaveCount(1);

        // Repositorio: formulario de perfil + ruta remota; confirmar sin ruta se rechaza y no crea nada
        await sources.nth(4).click();
        await expect(sources.nth(4)).toHaveAttribute('aria-pressed', 'true');
        await expect(sources.nth(1)).toHaveAttribute('aria-pressed', 'false');
        await expect(dialog.locator('#project-import-remote-path')).toBeVisible();
        await expect(dialog.locator('#project-import-file')).toHaveCount(0);
        await dialog.locator('#project-import-parent').fill(destParent);
        await dialog.locator('#project-import-name').fill(importedName);
        await dialog.locator('footer .project-primary-action').click();
        await expect(dialog.getByRole('alert')).toBeVisible();
        expect(fs.existsSync(destination)).toBe(false);
        expect((await session(request)).project?.id).toBe(source.id);

        // Proyecto externo: sin archivo analizado no se puede importar
        await sources.nth(2).click();
        await expect(sources.nth(2)).toHaveAttribute('aria-pressed', 'true');
        await expect(dialog.locator('#external-project-file')).toHaveCount(1);
        await expect(dialog.locator('#project-import-remote-path')).toHaveCount(0);
        await expect(dialog.locator('footer .project-primary-action')).toBeDisabled();
        await expect(dialog.getByRole('button', { name: /^(Analyze project|Analizar proyecto)$/ })).toBeDisabled();
        expect(fs.existsSync(destination)).toBe(false);

        // Carpeta de proyecto: se importa de verdad (ruta de una carpeta con modeling-project.json) y el origen no cambia
        await sources.nth(3).click();
        await expect(sources.nth(3)).toHaveAttribute('aria-pressed', 'true');
        await expect(dialog.locator('#project-import-source-directory')).toBeVisible();
        await dialog.locator('#project-import-source-directory').fill(source.directory);
        await dialog.locator('#project-import-parent').fill(destParent);
        await dialog.locator('#project-import-name').fill(importedName);
        await dialog.locator('footer .project-primary-action').click();
        await expect(page.locator('.project-manager-dialog')).toHaveCount(0);

        const imported = await eventually(() => session(request), value => path.resolve(value.directory).toLowerCase() === path.resolve(destination).toLowerCase());
        expect(imported.active).toBe(true);
        expect(imported.project?.id).toBe(source.id);
        for (const file of [TEXT_A, TEXT_B, BINARY]) {
            expect(fs.readFileSync(path.join(destination, file.rel)).equals(file.bytes), `bytes distintos en ${file.rel}`).toBe(true);
            expect(fs.readFileSync(path.join(source.directory, file.rel)).equals(file.bytes), `el origen cambió en ${file.rel}`).toBe(true);
        }
        const tree = await apiGet<TreeNodeJson>(request, '/api/projects/active/tree?view=PHYSICAL');
        const treePaths = flattenTree(tree).map(node => node.relativePath);
        for (const file of [TEXT_A, TEXT_B, BINARY]) expect(treePaths).toContain(file.rel);
    });
});
