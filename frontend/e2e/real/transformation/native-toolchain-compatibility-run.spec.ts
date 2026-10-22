import { expect, test } from '@playwright/test';
import { apiGet, exists, openApp, readJson } from '../support';

interface CatalogEntry { key: string; technology: string }
interface Matrix { overallStatus: string; results: { technology: string; version: string; status: string; durationMillis: number }[] }

// Centro de compatibilidad nativa en modo guiado: el usuario elige solo una tecnología, lanza las comprobaciones reales y revisa
// el resultado; el backend ejecuta únicamente lo elegido y deja la matriz guardada.
test('lanzar solo la comprobación elegida ejecuta esa tecnología en el backend real y deja su resultado guardado', async ({ page, request }) => {
    const catalog = await apiGet<CatalogEntry[]>(request, '/api/toolchain-compatibility/catalog');
    expect(catalog.length).toBeGreaterThan(1);
    const chosen = catalog.find((entry) => entry.technology === 'EMF') ?? catalog[0];

    await openApp(page, '/#/compatibility');
    const checkboxes = page.locator('.native-guided-selection input[type="checkbox"]');
    await expect(checkboxes).toHaveCount(catalog.length);

    // Etapa a: seleccionar comprobaciones (todas por defecto → vaciar → solo la elegida).
    const actions = page.locator('.native-selection-actions > button');
    await actions.nth(0).click();
    await expect(page.locator('.native-guided-selection input[type="checkbox"]:checked')).toHaveCount(catalog.length);
    await actions.nth(1).click();
    await expect(page.locator('.native-guided-selection input[type="checkbox"]:checked')).toHaveCount(0);
    const next = page.getByRole('button', { name: /^(Next step|Paso siguiente)$/ });
    await expect(next).toBeDisabled();
    await page.getByRole('checkbox', { name: chosen.technology, exact: true }).check();
    await expect(page.locator('.native-guided-selection input[type="checkbox"]:checked')).toHaveCount(1);
    await expect(next).toBeEnabled();
    await next.click();

    // Etapa b: «Ejecutar» lanza las sondas reales; la petición lleva solo lo marcado.
    const runRequest = page.waitForRequest((req) => req.url().includes('/api/toolchain-compatibility/run') && req.method() === 'POST');
    const runResponse = page.waitForResponse((res) => res.url().includes('/api/toolchain-compatibility/run'), { timeout: 70_000 });
    await page.getByRole('button', { name: /^(Run selected probes|Ejecutar las comprobaciones seleccionadas)$/ }).click();
    expect(JSON.parse((await runRequest).postData() ?? '{}')).toEqual({ selectedKeys: [chosen.key] });
    expect((await runResponse).ok()).toBeTruthy();

    // Oráculo independiente: la matriz guardada por el backend contiene solo la tecnología elegida.
    const latest = await apiGet<Matrix>(request, '/api/toolchain-compatibility/latest');
    expect(latest.results).toHaveLength(1);
    expect(latest.results[0].technology).toBe(chosen.technology);
    expect(latest.results[0].status).toBeTruthy();
    expect(latest.results[0].durationMillis).toBeGreaterThanOrEqual(0);
    expect(exists('data/reports/hydra-compatibility.json')).toBe(true);
    const onDisk = readJson<Matrix>('data/reports/hydra-compatibility.json');
    expect(onDisk.results.map((item) => item.technology)).toEqual([chosen.technology]);
    expect(onDisk.overallStatus).toBe(latest.overallStatus);

    // La pantalla de resultados muestra el mismo estado global que el backend guardó.
    await expect(page.locator('.native-guided-run span[class^="status-"]')).toHaveText(latest.overallStatus);
    await next.click();
    await expect(page.locator('.hydra-guided-review-summary strong')).toHaveText(latest.overallStatus);
});
