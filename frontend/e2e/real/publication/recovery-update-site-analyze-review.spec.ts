import { expect, test } from '@playwright/test';
import { analyzeDirect, candidateLabel, type Analysis, fixtureRoot, folderFiles, openModule, storeSnapshot } from './helpers';

// Jornada: «Recuperación desde un update-site» en el asistente guiado. El usuario sube la carpeta del fixture sirius-complete, decide qué capas
// neutrales recuperar (sin mapeos), analiza, revisa los candidatos (elige otro y lo reanaliza) y filtra la evidencia. Oráculo: llamadas directas
// e independientes al análisis real con los mismos ficheros y opciones; el análisis nunca escribe en los almacenes del disco.
test.use({ viewport: { width: 1700, height: 1100 } });
test.setTimeout(180_000);

const CONFIDENCE_LABEL: Record<string, string> = { EXACT: 'Exact', RECOVERED: 'Recovered', DERIVED: 'Derived', INFERRED: 'Inferred' };
const isAnalyze = (item: import('@playwright/test').Response): boolean => item.request().method() === 'POST' && item.url().includes('/api/update-site-imports/analyze');

test('origen, plan de capas, análisis, elección de candidato y filtros de evidencia coinciden con el análisis real independiente', async ({ page, request }) => {
    const files = folderFiles(fixtureRoot);
    const before = storeSnapshot();
    await openModule(page, '/#/update-site-imports');

    // a: origen — el usuario sube la carpeta del fixture; la interfaz cuenta los ficheros elegidos.
    await expect(page.getByRole('heading', { name: 'Recover an Eclipse update site step by step' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Next step', exact: true })).toBeDisabled();
    await page.locator('input[aria-label="Guided update site directory"]').setInputFiles(fixtureRoot);
    await expect(page.locator('.update-site-file-summary.selected')).toContainText(`${files.length} file(s) selected`);
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // b: plan de recuperación — se recuperan paletas y representaciones, pero no los mapeos.
    await expect(page.getByRole('checkbox', { name: 'Recover mappings', exact: true })).toBeChecked();
    await page.getByRole('checkbox', { name: 'Recover mappings', exact: true }).uncheck();
    await expect(page.getByRole('checkbox', { name: 'Recover palettes', exact: true })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Recover representations', exact: true })).toBeChecked();
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // Sin análisis no se puede avanzar; el análisis envía la decisión de capas al backend.
    await expect(page.getByRole('button', { name: 'Next step', exact: true })).toBeDisabled();
    const analyzed = page.waitForResponse(isAnalyze);
    await page.getByRole('button', { name: 'Analyze repository', exact: true }).click();
    const fromBrowser = await (await analyzed).json() as Analysis;

    // Oráculo independiente: el mismo análisis pedido directamente a la API real. Que la respuesta del navegador coincida (sin mapeos, con paletas
    // y representaciones) demuestra que la decisión de capas viajó al servidor.
    const direct = await analyzeDirect(request, files, { mappings: false });
    expect(direct.candidates.length).toBeGreaterThan(0);
    expect(direct.recoveredLayers.mappings).toHaveLength(0);
    expect(fromBrowser.recoveredLayers.mappings).toHaveLength(0);
    expect(fromBrowser.recoveredLayers.representations.length).toBe(direct.recoveredLayers.representations.length);
    expect(fromBrowser.recoveredLayers.palettes.length).toBe(direct.recoveredLayers.palettes.length);
    expect(fromBrowser.candidates.map(item => item.id)).toEqual(direct.candidates.map(item => item.id));
    await expect(page.locator('.update-site-guided-analysis .update-site-control-readiness'))
        .toContainText(`${direct.repository.repositoryFileCount} files inspected · ${direct.candidates.length} recovery candidate(s).`, { timeout: 120_000 });
    const metric = (label: string) => page.locator('.update-site-recovered-content-summary article').filter({ has: page.getByText(label, { exact: true }) }).locator('strong');
    await expect(metric('Mappings')).toHaveText('0');
    await expect(metric('Representations')).toHaveText(String(direct.recoveredLayers.representations.length));
    await expect(metric('Palette groups')).toHaveText(String(direct.recoveredLayers.palettes.length));
    await expect(metric('Metamodel candidates')).toHaveText(String(direct.candidates.length));

    // f: revisar — el asistente pasa a la revisión y el usuario abre «Recovery» para ver los candidatos.
    await page.getByRole('button', { name: 'Next step', exact: true }).click();
    await page.getByRole('button', { name: 'Recovery', exact: true }).click();
    const cards = page.locator('.update-site-candidate');
    await expect(cards).toHaveCount(direct.candidates.length);

    // a (workspace): cada candidato muestra «Nivel n · método», su insignia de confianza y su justificación.
    for (const [index, candidate] of direct.candidates.entries()) {
        const card = cards.nth(index);
        await expect(card).toContainText(candidateLabel(candidate));
        await expect(card.locator('.update-site-confidence')).toHaveText(CONFIDENCE_LABEL[candidate.confidence]);
        await expect(card).toContainText(candidate.rationale);
        await expect(card.locator('code')).toHaveText(candidate.sourcePath);
    }
    await expect(cards.locator('input[type="radio"]').nth(direct.candidates.findIndex(item => item.id === direct.selectedCandidateId))).toBeChecked();

    // Elegir otro candidato exige reanalizar: el análisis se pide con ese candidato y el resultado coincide con la API real.
    let active = direct;
    const otherIndex = direct.candidates.findIndex(item => item.id !== direct.selectedCandidateId);
    if (otherIndex >= 0) {
        const other = direct.candidates[otherIndex];
        await cards.nth(otherIndex).locator('input[type="radio"]').check();
        const reanalyze = page.waitForResponse(isAnalyze);
        await page.getByRole('button', { name: 'Analyze selected candidate', exact: true }).click();
        const reanalyzed = await (await reanalyze).json() as Analysis;
        expect(reanalyzed.selectedCandidateId).toBe(other.id);
        active = await analyzeDirect(request, files, { mappings: false, candidateId: other.id });
        expect(active.selectedCandidateId).toBe(other.id);
        expect(reanalyzed.provenance.length).toBe(active.provenance.length);
        await expect(page.getByRole('button', { name: 'Analyze selected candidate', exact: true })).toHaveCount(0, { timeout: 120_000 });
        await expect(cards.nth(otherIndex).locator('input[type="radio"]')).toBeChecked();
        if (active.metamodelPreview !== null) {
            await page.getByRole('button', { name: 'Overview', exact: true }).click();
            await expect(page.getByText(`${active.metamodelPreview.statistics.classes} classes · ${active.metamodelPreview.statistics.relations} relations`)).toBeVisible();
        }
    }

    // b (workspace): filtrar la evidencia por texto (origen, archivo, método…) y por confianza; los recuentos coinciden con la API real.
    await page.getByRole('button', { name: 'Evidence', exact: true }).click();
    expect(active.provenance.length).toBeGreaterThan(0);
    await expect(page.locator('.update-site-evidence-summary article').first().locator('strong')).toHaveText(String(active.provenance.length));
    const haystack = (item: (typeof active.provenance)[number]): string => `${item.elementPath} ${item.origin} ${item.sourcePath} ${item.originalElement} ${item.method}`.toLowerCase();
    const rows = page.locator('.update-site-evidence-list > article');
    const shown = (count: number): number => Math.min(50, count);
    await expect(rows).toHaveCount(shown(active.provenance.length));

    const term = (active.provenance[0].sourcePath.split('/').pop() ?? '').toLowerCase();
    await page.locator('#update-site-evidence-search').fill(term);
    await expect(rows).toHaveCount(shown(active.provenance.filter(item => haystack(item).includes(term)).length));

    const level = active.provenance[0].confidence;
    await page.locator('#update-site-evidence-confidence').selectOption(level);
    await expect(rows).toHaveCount(shown(active.provenance.filter(item => item.confidence === level && haystack(item).includes(term)).length));
    await page.locator('#update-site-evidence-search').fill('');
    await expect(rows).toHaveCount(shown(active.provenance.filter(item => item.confidence === level).length));

    await page.locator('#update-site-evidence-search').fill('zzz-sin-coincidencias-e2e');
    await expect(rows).toHaveCount(0);
    await expect(page.getByText('No provenance entries match the current filters.')).toBeVisible();

    // El análisis es de solo lectura: ningún almacén del backend cambió.
    expect(storeSnapshot()).toEqual(before);
});
