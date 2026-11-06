import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { apiGet, eventually, exists, openApp, readJson, uid, workDir } from '../support';
import { seedProject, session, toRel, writeProjectFile, type SeededProject } from './helpers';

// Jornada «project-home-entry»: desde Inicio el usuario continúa con el artefacto principal, abre un proyecto reciente
// (máximo 6), abre un proyecto por su carpeta y crea uno nuevo. Oráculo: API real (/api/projects/*) y ficheros del backend.

interface Recent { id: string; name: string; directory: string; active: boolean; available: boolean }
const recent = (request: import('@playwright/test').APIRequestContext): Promise<Recent[]> => apiGet<Recent[]>(request, '/api/projects/recent');

test.describe('entrada desde Inicio', () => {
    test('Continuar abre el artefacto principal y los recientes se limitan a 6 y se abren de verdad', async ({ page, request }) => {
        const seeded: SeededProject[] = [];
        for (let i = 0; i < 7; i++) seeded.push(await seedProject(request, `home${i}`));
        const current = seeded[6];
        // El artefacto principal del proyecto activo lo fija el propio backend (configuración real del proyecto).
        const primary = `behavior/validations/${uid('rules')}.validations.json`;
        writeProjectFile(current, primary, '{}\n');
        const configured = await request.put('/api/projects/active/configuration', { data: { primaryArtifact: { kind: 'VALIDATION_MODEL', id: primary, relativePath: primary } } });
        expect(configured.ok()).toBeTruthy();
        expect(readJson<{ primaryArtifact: { relativePath: string } }>(`${current.rel}/modeling-project.json`).primaryArtifact.relativePath).toBe(primary);

        // El registro real de recientes tiene los 7 proyectos recién creados (el más reciente primero)
        const registry = await recent(request);
        expect(registry.length).toBeGreaterThanOrEqual(7);
        expect(registry.slice(0, 7).map(item => item.id)).toEqual([...seeded].reverse().map(item => item.id));

        // Un reciente cuya carpeta desapareció del disco no debe ofrecer una apertura rota
        const vanished = seeded[1];
        fs.rmSync(vanished.directory, { recursive: true, force: true });
        expect(exists(vanished.rel)).toBe(false);

        await openApp(page, '/#/home');
        await expect(page.locator('#hydra-home-title')).toHaveText(current.name);

        // Continuar trabajando (etapa c): la tarjeta nombra el artefacto principal y entra por su módulo
        const fileName = primary.split('/').at(-1) as string;
        await expect(page.locator('.hydra-home-continue-card')).toContainText(fileName);
        await expect(page.locator('.hydra-home-continue-card')).toContainText(primary);
        await page.locator('.hydra-home-continue-card').getByRole('button', { name: /^(Continue|Continuar)$/ }).click();
        await expect(page).toHaveURL(/#\/validations$/);

        // Volver a Inicio y revisar los recientes (etapa d)
        await page.goto('/#/home');
        const cards = page.locator('.hydra-home-recent-card');
        await expect(cards).toHaveCount(6);
        const live = await recent(request);
        const expectedNames = live.slice(0, 6).map(item => item.name);
        await expect(cards.locator('strong')).toHaveText(expectedNames);
        // Solo hay 6 tarjetas aunque el registro tenga más de 6 proyectos
        expect(live.length).toBeGreaterThan(6);

        // El proyecto sin carpeta aparece deshabilitado y se marca no disponible en el registro
        const vanishedCard = cards.filter({ hasText: vanished.name });
        await expect(vanishedCard).toBeDisabled();
        expect(live.find(item => item.id === vanished.id)?.available).toBe(false);

        // Abrir un reciente disponible: se vuelve el proyecto activo del backend y pasa al primer lugar
        const target = seeded[4];
        await cards.filter({ hasText: target.name }).click();
        const opened = await eventually(() => session(request), value => value.project?.id === target.id);
        expect(opened.active).toBe(true);
        expect(path.resolve(opened.directory).toLowerCase()).toBe(path.resolve(target.directory).toLowerCase());
        await expect(page.locator('#hydra-home-title')).toHaveText(target.name);
        expect((await recent(request))[0].id).toBe(target.id);
    });

    test('Abrir proyecto por su carpeta y Crear proyecto nuevo desde las acciones principales', async ({ page, request }) => {
        const first = await seedProject(request, 'home-open');
        const second = await seedProject(request, 'home-active');
        expect((await session(request)).project?.id).toBe(second.id);

        await openApp(page, '/#/home');
        // Devuelve la zona del botón de acción de la tarjeta (la tarjeta también contiene un botón de ayuda).
        const card = (title: RegExp) => page.locator('.hydra-home-action-card').filter({ has: page.getByRole('heading', { name: title }) }).locator('footer');
        const dialog = page.locator('.project-manager-dialog');

        // Abrir un proyecto existente indicando su carpeta
        await card(/^(Open project|Abrir proyecto)$/).getByRole('button').click();
        await expect(dialog).toBeVisible();
        await dialog.locator('#project-open-directory').fill(first.directory);
        await dialog.getByLabel(/Load the project files and models|Cargar los archivos/).uncheck();
        await dialog.locator('footer .project-primary-action').click();
        await expect(dialog).toHaveCount(0);
        const opened = await eventually(() => session(request), value => value.project?.id === first.id);
        expect(opened.active).toBe(true);
        await expect(page.locator('#hydra-home-title')).toHaveText(first.name);

        // Rechazo: una carpeta que no existe no cambia el proyecto activo ni crea nada
        const missing = path.join(first.parent, uid('no-existe'));
        await card(/^(Open project|Abrir proyecto)$/).getByRole('button').click();
        await expect(dialog).toBeVisible();
        await dialog.locator('#project-open-directory').fill(missing);
        await dialog.getByLabel(/Load the project files and models|Cargar los archivos/).uncheck();
        await dialog.locator('footer .project-primary-action').click();
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('alert')).toBeVisible();
        expect((await session(request)).project?.id).toBe(first.id);
        expect(fs.existsSync(missing)).toBe(false);
        await dialog.getByRole('button', { name: /^(Cancel|Cancelar)$/ }).click();
        await expect(dialog).toHaveCount(0);

        // Crear un proyecto nuevo con el asistente
        const parent = path.join(workDir, 'e2e-projects', uid('home-new'));
        fs.mkdirSync(parent, { recursive: true });
        const name = uid('nuevo');
        await card(/^(New project|Nuevo proyecto)$/).getByRole('button').click();
        await expect(dialog).toBeVisible();
        await dialog.locator('#project-create-parent').fill(parent);
        await dialog.locator('#project-create-name').fill(name);
        await dialog.locator('footer .project-primary-action').click();
        await expect(dialog).toHaveCount(0);

        const created = await eventually(() => session(request), value => value.project?.name === name);
        expect(created.active).toBe(true);
        const descriptor = readJson<{ id: string; name: string; createdFromPreset: string }>(`${toRel(path.join(parent, name))}/modeling-project.json`);
        expect(descriptor.name).toBe(name);
        expect(descriptor.id).toBe(name.toLowerCase().replace(/[^a-z0-9]+/g, '-'));
        expect(descriptor.createdFromPreset).toBe('EMPTY');
        await expect(page.locator('#hydra-home-title')).toHaveText(name);
        expect((await recent(request))[0].name).toBe(name);
    });

    // Acción principal, las cuatro tarjetas: Nuevo y Abrir ya se recorren arriba; aquí Importar (con un ZIP real que se
    // importa) y Conectar (abre el diálogo centrado en repositorio y exige perfil y ruta remota). El éxito de CONECTAR con
    // un repositorio real NO se prueba (necesita un servicio externo configurado).
    test('Importar trae un proyecto real desde un ZIP y Conectar exige un repositorio configurado', async ({ page, request }, testInfo) => {
        const source = await seedProject(request, 'home-imp');
        const marker = { rel: 'metamodels/inicio.metamodel.json', bytes: Buffer.from('{ "id": "inicio", "nombre": "Desde Inicio ñ" }\n', 'utf8') };
        writeProjectFile(source, marker.rel, marker.bytes);
        const exported = await request.get('/api/projects/active/export');
        expect(exported.ok()).toBeTruthy();
        const zipPath = testInfo.outputPath('inicio.zip');
        fs.writeFileSync(zipPath, await exported.body());
        const other = await seedProject(request, 'home-other'); // otro proyecto activo distinto del que se va a importar
        expect((await session(request)).project?.id).toBe(other.id);

        await openApp(page, '/#/home');
        const card = (title: RegExp) => page.locator('.hydra-home-action-card').filter({ has: page.getByRole('heading', { name: title }) }).locator('footer');
        const dialog = page.locator('.project-manager-dialog');

        // Conectar: diálogo en modo repositorio, sin selector de origen; confirmar sin datos se rechaza y no cambia nada
        await card(/^(Connect an external location|Conectar)/).getByRole('button').click();
        await expect(dialog).toBeVisible();
        await expect(dialog.locator('#project-import-remote-path')).toBeVisible();
        await expect(dialog.locator('.project-source-options-wide')).toHaveCount(0);
        const parent = path.join(workDir, 'e2e-projects', uid('home-imp-dest'));
        const name = uid('desde-inicio');
        await dialog.locator('#project-import-parent').fill(parent);
        await dialog.locator('#project-import-name').fill(name);
        await dialog.locator('footer .project-primary-action').click();
        await expect(dialog.getByRole('alert')).toBeVisible();
        expect(fs.existsSync(path.join(parent, name))).toBe(false);
        expect((await session(request)).project?.id).toBe(other.id);
        await dialog.getByRole('button', { name: /^(Cancel|Cancelar)$/ }).click();
        await expect(dialog).toHaveCount(0);

        // Importar: desde la tarjeta, ZIP elegido por defecto, y el proyecto del archivo queda importado y activo
        await card(/^(Bring an existing project|Traer un proyecto existente)$/).getByRole('button').click();
        await expect(dialog).toBeVisible();
        await dialog.locator('.project-source-options-wide').getByRole('button', { name: /^(ZIP file|Archivo ZIP)/ }).click();
        await dialog.locator('#project-import-parent').fill(parent);
        await dialog.locator('#project-import-name').fill(name);
        await dialog.locator('#project-import-file').setInputFiles(zipPath);
        await dialog.locator('footer .project-primary-action').click();
        await expect(dialog).toHaveCount(0);

        const destination = path.join(parent, name);
        const imported = await eventually(() => session(request), value => value.project?.id === source.id && path.resolve(value.directory).toLowerCase() === path.resolve(destination).toLowerCase());
        expect(imported.active).toBe(true);
        expect(fs.readFileSync(path.join(destination, marker.rel)).equals(marker.bytes)).toBe(true);
        await expect(page.locator('#hydra-home-title')).toHaveText(source.name);
    });
});
