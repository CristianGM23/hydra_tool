import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { apiGet, eventually, exists, openApp, readJson, uid, workDir } from '../support';

// Jornada «generated-project-wizard» (Generated Projects, modo «Full workspace», que es la misma pantalla que el asistente):
// registrar un proyecto generado, elegirlo, ajustar etapas (LAUNCH deshabilitada en el perfil), previsualizar (simulación),
// ejecutar de verdad con un fallo real de la herramienta y reajustar a una ejecución correcta.
// Oráculo: registro que escribe el backend, informe de la API real /api/generated-projects/execute con los mismos
// parámetros, y el contenido real de la carpeta del proyecto generado.
// Perfil «langium»: su comando es npm. Sin package.json la fase «Prepare» falla de forma real (o la herramienta no está
// instalada y también falla); en ambos casos el informe real dice FAILED, que es lo que se comprueba.

interface Step { stage: string; status: string; command: string[]; summary: string; diagnostics: string[]; exitCode: number | null }
interface Report { successful: boolean; resolvedDirectory: string; steps: Step[] }
interface Registry { projects: Array<{ id: string; name: string; profileKey: string; relativeDirectory: string }> }

const REGISTRY_FILE = 'data/generated-projects-registry/generated-projects.json';
const GENERATED_ROOT = path.join(workDir, 'data', 'generated-projects');
const normalize = (value: string): string => value.replace(/\s+/g, ' ').trim();

async function executeViaApi(request: APIRequestContext, projectId: string, stages: string[], dryRun: boolean): Promise<Report> {
    const response = await request.post('/api/generated-projects/execute', { data: { projectId, stages, dryRun, allowOptionalStages: false, continueAfterFailure: false } });
    expect(response.ok(), `execute -> ${response.status()}`).toBeTruthy();
    return (await response.json()) as Report;
}

const stageBox = (page: Page, label: string) => page.getByRole('checkbox', { name: label, exact: true });
const reportStep = (page: Page, heading: string) => page.locator('.generated-project-report article').filter({ has: page.getByRole('heading', { name: heading, exact: true }) });

test.describe('proyectos generados', () => {
    test('registrar, previsualizar sin tocar disco, ejecutar con fallo real y reajustar etapas', async ({ page, request }) => {
        const projectId = uid('gp');
        const relativeDirectory = projectId;
        const directory = path.join(GENERATED_ROOT, relativeDirectory);
        fs.mkdirSync(directory, { recursive: true }); // carpeta del proyecto generado, vacía (sin package.json)

        await openApp(page, '/#/generated-projects');
        await page.getByRole('button', { name: 'Full workspace', exact: true }).click();

        // Rechazo: una carpeta fuera de la raíz de proyectos generados no se registra
        await page.locator('#generated-project-id').fill(`${projectId}-fuera`);
        await page.locator('#generated-project-directory').fill('../fuera-de-la-raiz');
        await page.locator('select[aria-label="Generated project lifecycle profile"]').selectOption('langium');
        await page.getByRole('button', { name: 'Register project', exact: true }).click();
        await expect(page.locator('.generated-project-center').getByRole('alert')).toBeVisible();
        const afterRejected = await apiGet<Registry>(request, '/api/generated-projects');
        expect(afterRejected.projects.some(project => project.id === `${projectId}-fuera`)).toBe(false);

        // Registrar (etapa rg)
        await page.locator('#generated-project-id').fill(projectId);
        await page.locator('#generated-project-name').fill(`Proyecto ${projectId}`);
        await page.locator('#generated-project-directory').fill(relativeDirectory);
        await page.getByRole('button', { name: 'Register project', exact: true }).click();
        const registered = await eventually(() => (exists(REGISTRY_FILE) ? readJson<Registry>(REGISTRY_FILE) : { projects: [] }), value => value.projects.some(project => project.id === projectId));
        const entry = registered.projects.find(project => project.id === projectId)!;
        expect(entry.profileKey).toBe('langium');
        expect(entry.relativeDirectory).toBe(relativeDirectory);
        expect(entry.name).toBe(`Proyecto ${projectId}`);

        // Elegir proyecto y simulación (etapa pj): el proyecto queda elegido y «Dry run» viene marcada
        await expect(page.locator('select[aria-label="Registered generated project"]')).toHaveValue(projectId);
        await expect(page.getByRole('checkbox', { name: 'Dry run', exact: true })).toBeChecked();

        // Elegir etapas (etapa st): el perfil langium no soporta «Launch» y no se puede marcar
        await expect(stageBox(page, 'Launch')).toBeDisabled();
        await expect(stageBox(page, 'Launch')).not.toBeChecked();
        for (const label of ['Prepare', 'Validate', 'Open']) { await expect(stageBox(page, label)).toBeEnabled(); await expect(stageBox(page, label)).toBeChecked(); }

        // Previsualizar (etapa pv): comandos y estados PLANNED, sin ejecutar nada ni crear ficheros
        await page.getByRole('button', { name: 'Preview lifecycle', exact: true }).click();
        const reportBox = page.locator('.generated-project-report');
        await expect(reportBox).toBeVisible();
        await expect(reportBox.locator('header .status-passed')).toHaveText('PASSED');
        const planned = await executeViaApi(request, projectId, ['GENERATE', 'VALIDATE', 'OPEN'], true);
        expect(planned.steps.map(step => step.status)).toEqual(['PLANNED', 'PLANNED', 'PASSED']);
        const labels: Record<string, string> = { GENERATE: 'Prepare', VALIDATE: 'Validate', OPEN: 'Open' };
        for (const step of planned.steps) {
            const article = reportStep(page, labels[step.stage]);
            await expect(article.locator('span').first()).toHaveText(step.status);
            if (step.command.length > 0) await expect(article.locator('code')).toHaveText(step.command.join(' '));
        }
        expect(planned.steps[0].command.join(' ')).toContain('langium:generate');
        expect(fs.readdirSync(directory)).toEqual([]); // la simulación no crea ficheros en la carpeta del proyecto

        // Ejecutar de verdad (etapas ru, ok, rp): «Prepare» falla de forma real y el informe muestra el detalle
        await page.getByRole('checkbox', { name: 'Dry run', exact: true }).uncheck();
        await expect(stageBox(page, 'Open')).toBeChecked();
        await stageBox(page, 'Validate').uncheck();
        await stageBox(page, 'Open').uncheck();
        await expect(stageBox(page, 'Prepare')).toBeChecked();
        await page.getByRole('button', { name: 'Run lifecycle', exact: true }).click();
        await expect(reportBox.locator('header .status-failed')).toHaveText('FAILED');
        const failed = await executeViaApi(request, projectId, ['GENERATE'], false);
        expect(failed.successful).toBe(false);
        expect(failed.steps).toHaveLength(1);
        expect(failed.steps[0].status).toBe('FAILED');
        await expect(reportStep(page, 'Prepare').locator('span').first()).toHaveText('FAILED');
        await expect(reportStep(page, 'Prepare').locator('p')).toHaveText(failed.steps[0].summary);
        // (El texto exacto de los diagnósticos depende de la herramienta npm instalada y del cortacircuitos; no se compara.)
        expect(normalize(failed.steps[0].summary)).not.toBe('');

        // Reajustar etapas: solo «Open» y ejecución real; ahora todo es correcto y el informe lo refleja
        await stageBox(page, 'Prepare').uncheck();
        await stageBox(page, 'Open').check();
        await page.getByRole('button', { name: 'Run lifecycle', exact: true }).click();
        await expect(reportBox.locator('header .status-passed')).toHaveText('PASSED');
        const opened = await executeViaApi(request, projectId, ['OPEN'], false);
        expect(opened.successful).toBe(true);
        expect(opened.steps.map(step => step.status)).toEqual(['PASSED']);
        expect(path.resolve(opened.resolvedDirectory).toLowerCase()).toBe(path.resolve(directory).toLowerCase());
        await expect(reportStep(page, 'Open').locator('span').first()).toHaveText('PASSED');
        await expect(reportBox.locator('header small')).toContainText(path.basename(directory));
        await expect(reportStep(page, 'Open').locator('p')).toContainText(path.basename(directory));
    });
});
