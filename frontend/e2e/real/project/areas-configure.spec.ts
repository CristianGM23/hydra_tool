import { expect, test, type Page } from '@playwright/test';
import { eventually, openApp, readJson } from '../support';
import { seedProject, session, type SeededProject } from './helpers';

// Jornada «project-areas-configure»: el usuario abre el diálogo «Project areas», cambia el modo, marca/desmarca áreas y
// confirma (o cancela). Oráculo: el modeling-project.json que escribe el backend y la sesión de la API real.
// Interfaz en inglés por defecto (HYDRA arranca en inglés si no hay idioma guardado).

const START = ['VALIDATIONS', 'QUICK_FIXES', 'EVENTS', 'EVENT_FLOWS'];

interface Descriptor { enabledCapabilities: string[]; experienceMode: string }
const descriptor = (project: SeededProject): Descriptor => readJson<Descriptor>(`${project.rel}/modeling-project.json`);
const sorted = (values: string[]): string[] => [...values].sort();

async function openDialog(page: Page) {
    const button = page.getByRole('button', { name: /Project areas…|Áreas del proyecto…/ });
    await expect(button).toBeEnabled();
    await button.click();
    const dialog = page.locator('.project-capabilities-dialog');
    await expect(dialog).toBeVisible();
    // El catálogo de áreas llega del backend real: esperar a que existan las casillas.
    await expect(dialog.locator('label.project-capability-option').first()).toBeVisible();
    return dialog;
}

const area = (page: Page, label: RegExp) => page.locator('.project-capabilities-dialog label.project-capability-option')
    .filter({ has: page.locator('strong', { hasText: label }) }).getByRole('checkbox');

test.describe('áreas del proyecto', () => {
    test('modo Libre, dependencias automáticas en ambos sentidos y guardado persistente', async ({ page, request }) => {
        const project = await seedProject(request, 'areas', { preset: 'EMPTY', capabilities: START, mode: 'GUIDED' });
        expect(sorted(descriptor(project).enabledCapabilities)).toEqual(sorted(START));
        expect(descriptor(project).experienceMode).toBe('GUIDED');

        await openApp(page, '/#/home');
        const dialog = await openDialog(page);
        await expect(dialog).toContainText(/4\s+(active areas|áreas activas)/i);

        // Interruptor Guiado/Libre (etapa a)
        await expect(dialog.getByRole('button', { name: /^(Guided|Guiado)$/ })).toHaveClass(/active/);
        await dialog.getByRole('button', { name: /^(Free|Libre)$/ }).click();
        await expect(dialog.getByRole('button', { name: /^(Free|Libre)$/ })).toHaveClass(/active/);

        // Desmarcar «Quality rules» apaga las correcciones sugeridas que dependen de ella (etapas b y c)
        await expect(area(page, /^Quality rules$/)).toBeChecked();
        await area(page, /^Quality rules$/).uncheck();
        await expect(area(page, /^Quality rules$/)).not.toBeChecked();
        await expect(area(page, /^Suggested corrections$/)).not.toBeChecked();
        await expect(dialog).toContainText(/2\s+(active areas|áreas activas)/i);

        // Marcar «Suggested corrections» enciende sus requisitos
        await area(page, /^Suggested corrections$/).check();
        await expect(area(page, /^Suggested corrections$/)).toBeChecked();
        await expect(area(page, /^Quality rules$/)).toBeChecked();
        await expect(dialog).toContainText(/4\s+(active areas|áreas activas)/i);

        // Desmarcar «Events» apaga los flujos de eventos dependientes
        await area(page, /^Events$/).uncheck();
        await expect(area(page, /^Event flows$/)).not.toBeChecked();
        await expect(dialog).toContainText(/2\s+(active areas|áreas activas)/i);

        // Antes de confirmar, nada ha cambiado en el almacenamiento (etapa d: se revisa antes de guardar)
        expect(descriptor(project).experienceMode).toBe('GUIDED');
        expect(sorted(descriptor(project).enabledCapabilities)).toEqual(sorted(START));

        await dialog.getByRole('button', { name: /Apply changes|Aplicar cambios/ }).click();
        await expect(page.locator('.project-capabilities-dialog')).toHaveCount(0);

        // Oráculo independiente: el backend persistió modo y áreas con las dependencias aplicadas
        const saved = await eventually(() => descriptor(project), value => value.experienceMode === 'FREE');
        expect(sorted(saved.enabledCapabilities)).toEqual(['QUICK_FIXES', 'VALIDATIONS']);
        const live = await session(request);
        expect(live.project?.id).toBe(project.id);
        expect(live.project?.experienceMode).toBe('FREE');
        expect(sorted(live.project?.enabledCapabilities ?? [])).toEqual(['QUICK_FIXES', 'VALIDATIONS']);
        await expect(page.locator('.hydra-home-dashboard-header')).toContainText(/Free mode|Modo libre/);
    });

    test('cancelar el diálogo no cambia el proyecto (rechazo)', async ({ page, request }) => {
        const project = await seedProject(request, 'areas-cancel', { preset: 'EMPTY', capabilities: START, mode: 'GUIDED' });
        const before = descriptor(project);

        await openApp(page, '/#/home');
        const dialog = await openDialog(page);
        await dialog.getByRole('button', { name: /^(Free|Libre)$/ }).click();
        await area(page, /^Quality rules$/).uncheck();
        await expect(area(page, /^Suggested corrections$/)).not.toBeChecked();
        await dialog.getByRole('button', { name: /^(Cancel|Cancelar)$/ }).click();
        await expect(page.locator('.project-capabilities-dialog')).toHaveCount(0);

        // Reabrir: el diálogo vuelve a mostrar el estado guardado, no el borrador cancelado
        const reopened = await openDialog(page);
        await expect(reopened.getByRole('button', { name: /^(Guided|Guiado)$/ })).toHaveClass(/active/);
        await expect(area(page, /^Quality rules$/)).toBeChecked();
        await expect(area(page, /^Suggested corrections$/)).toBeChecked();

        const after = descriptor(project);
        expect(after.experienceMode).toBe(before.experienceMode);
        expect(sorted(after.enabledCapabilities)).toEqual(sorted(before.enabledCapabilities));
        const live = await session(request);
        expect(live.project?.experienceMode).toBe('GUIDED');
        expect(sorted(live.project?.enabledCapabilities ?? [])).toEqual(sorted(START));
    });
});
