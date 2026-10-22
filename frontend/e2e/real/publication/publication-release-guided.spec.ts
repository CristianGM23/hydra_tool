import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { eventually, exists, readJson, uid, workDir } from '../support';
import { openModule, sha256 } from './helpers';

// Jornada: «Publicación y actualizaciones». El usuario identifica el producto, indica la carpeta y el artefacto de la plataforma, elige canal y firma,
// valida y publica; después consulta el canal desde el resolutor de actualizaciones. Oráculo: manifiesto y copia del artefacto que escribió el backend
// (con el SHA-256 recalculado por el test) y peticiones independientes a la API real de validación y de consulta de canal.
test.use({ viewport: { width: 1600, height: 1000 } });

const DISTRIBUTION_ROOT = 'data/standalone-editors';
const PUBLICATION_ROOT = 'data/publications';
const ARTIFACT_BYTES = Buffer.from('contenido del paquete de pruebas E2E de publicación\n'.repeat(40));

interface Manifest { productId: string; version: string; channel: string; signingMode: string; artifacts: Array<{ platform: string; fileName: string; size: number; sha256: string; downloadUrl: string }> }
interface UpdateCheck { updateAvailable: boolean; currentVersion: string; availableVersion: string; message: string; artifact: null | { platform: string; fileName: string } }

/** Paso previo (no es la etapa certificada): carpeta de distribución con un paquete ya construido. */
function seedPackage(label: string, fileName = 'hydra-editor.zip'): string {
    const dir = uid(label);
    const folder = path.join(workDir, DISTRIBUTION_ROOT, dir);
    fs.mkdirSync(folder, { recursive: true });
    fs.writeFileSync(path.join(folder, fileName), ARTIFACT_BYTES);
    return dir;
}

test('el modo guiado valida y publica un paquete: el manifiesto, el canal, la plataforma y el hash quedan escritos y se bloquea lo incompleto', async ({ page, request }) => {
    const product = uid('e2e-pub');
    const dir = seedPackage('e2e-release');
    await openModule(page, '/#/publication');
    await expect(page.getByRole('heading', { name: 'Publish a release with confidence' })).toBeVisible();

    // id: identidad del producto.
    await page.locator('#publication-product').fill(product);
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // ar: carpeta, plataforma y ruta del artefacto. Primero una ruta equivocada para ver el bloqueo.
    await page.locator('#publication-directory').fill(dir);
    await page.getByLabel('Publication platform', { exact: true }).selectOption('linux-x64');
    await page.locator('#publication-artifact').fill('falta.zip');
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // ch: canal y firma. Beta exige una URL pública: el paso no avanza sin ella.
    await page.getByLabel('Publication channel', { exact: true }).selectOption('BETA');
    await expect(page.getByRole('button', { name: 'Next step', exact: true })).toBeDisabled();
    await page.getByLabel('Publication channel', { exact: true }).selectOption('INTERNAL');
    await expect(page.getByRole('button', { name: 'Next step', exact: true })).toBeEnabled();
    await expect(page.getByLabel('Publication signing mode', { exact: true })).toHaveValue('CHECKSUM_ONLY');
    await page.getByRole('button', { name: 'Next step', exact: true }).click();

    // Validación con el artefacto inexistente: bloqueada, no avanza, y la API real opina lo mismo.
    await page.getByRole('button', { name: 'Validate release', exact: true }).click();
    await expect(page.locator('.publication-guided-verification .status-failed')).toHaveText('BLOCKED');
    await expect(page.getByRole('button', { name: 'Next step', exact: true })).toBeDisabled();
    const blocked = await request.post('/api/publications/validate', { data: { productId: product, version: '0.0.0', channel: 'INTERNAL', signingMode: 'CHECKSUM_ONLY', distributionDirectory: dir, updateBaseUrl: '', artifacts: [{ platform: 'linux-x64', relativePath: 'falta.zip', mediaType: 'application/zip' }], metadata: {} } });
    const blockedReport = await blocked.json() as { valid: boolean; diagnostics: Array<{ code: string }> };
    expect(blockedReport.valid).toBe(false);
    expect(blockedReport.diagnostics.map(item => item.code)).toContain('ARTIFACT_MISSING');
    expect(exists(`${PUBLICATION_ROOT}/${product}`)).toBe(false);

    // El usuario vuelve al paso del artefacto, corrige la ruta y revalida.
    await page.getByRole('button', { name: 'Previous', exact: true }).click();
    await page.getByRole('button', { name: 'Previous', exact: true }).click();
    await page.locator('#publication-artifact').fill('hydra-editor.zip');
    await page.getByRole('button', { name: 'Next step', exact: true }).click();
    await page.getByRole('button', { name: 'Next step', exact: true }).click();
    await page.getByRole('button', { name: 'Validate release', exact: true }).click();
    await expect(page.locator('.publication-guided-verification .status-passed')).toHaveText('VALID');
    await page.getByRole('button', { name: 'Next step', exact: true }).click();
    await page.getByRole('button', { name: 'Publish release', exact: true }).click();
    await expect(page.locator('.hydra-guided-review-summary code')).toContainText(product);

    // Oráculo 1: el manifiesto escrito por el backend recoge producto, canal, plataforma y el hash recalculado por el test.
    const channelRoot = `${PUBLICATION_ROOT}/${product}/internal`;
    const latest = readJson<Manifest>(`${channelRoot}/latest.json`);
    expect(latest).toMatchObject({ productId: product, channel: 'INTERNAL', signingMode: 'CHECKSUM_ONLY' });
    expect(latest.artifacts).toHaveLength(1);
    expect(latest.artifacts[0]).toMatchObject({ platform: 'linux-x64', fileName: 'hydra-editor.zip', size: ARTIFACT_BYTES.length, sha256: sha256(ARTIFACT_BYTES) });
    const versioned = readJson<Manifest>(`${channelRoot}/${latest.version}/manifest.json`);
    expect(versioned.artifacts[0].sha256).toBe(sha256(ARTIFACT_BYTES));
    expect(fs.readFileSync(path.join(workDir, channelRoot, latest.version, 'hydra-editor.zip')).equals(ARTIFACT_BYTES)).toBe(true);

    // Las versiones publicadas son inmutables: repetir la publicación se rechaza por la API real y no cambia nada.
    const again = await request.post('/api/publications/publish', { data: { productId: product, version: latest.version, channel: 'INTERNAL', signingMode: 'CHECKSUM_ONLY', distributionDirectory: dir, updateBaseUrl: '', artifacts: [{ platform: 'linux-x64', relativePath: 'hydra-editor.zip', mediaType: 'application/zip' }], metadata: {} } });
    expect(again.ok()).toBe(false);
    expect(readJson<Manifest>(`${channelRoot}/latest.json`).artifacts[0].sha256).toBe(sha256(ARTIFACT_BYTES));
});

test('el resolutor de actualizaciones del modo completo coincide con la consulta independiente del canal', async ({ page, request }) => {
    const dir = seedPackage('e2e-check');
    const product = uid('e2e-upd');
    const windows = { platform: 'windows-x64', relativePath: 'hydra-editor.zip', mediaType: 'application/zip' };
    // Paso previo: una versión 99.0.0 publicada por la API real en el canal interno.
    const seeded = await request.post('/api/publications/publish', { data: { productId: product, version: '99.0.0', channel: 'INTERNAL', signingMode: 'CHECKSUM_ONLY', distributionDirectory: dir, updateBaseUrl: '', artifacts: [windows], metadata: {} } });
    expect(seeded.ok(), `publicación previa: ${seeded.status()} ${await seeded.text()}`).toBeTruthy();

    await openModule(page, '/#/publication');
    await page.getByRole('button', { name: 'Full workspace', exact: true }).click();
    await page.locator('#publication-product').fill(product);
    await page.getByLabel('Publication platform', { exact: true }).selectOption('windows-x64');
    await page.getByLabel('Publication channel', { exact: true }).selectOption('INTERNAL');

    // up: «Check channel» muestra si hay una versión más nueva y el artefacto.
    const checked = page.waitForResponse(item => item.url().endsWith('/api/publications/check') && item.request().method() === 'POST');
    await page.getByRole('button', { name: 'Check channel', exact: true }).click();
    const uiResponse = await (await checked).json() as UpdateCheck;
    const resolver = page.locator('.publication-update-check');
    await expect(resolver).toContainText('UPDATE AVAILABLE');

    const direct = await request.post('/api/publications/check', { data: { productId: product, currentVersion: uiResponse.currentVersion, channel: 'INTERNAL', platform: 'windows-x64' } });
    const directJson = await direct.json() as UpdateCheck;
    expect(directJson.updateAvailable).toBe(true);
    expect(directJson.availableVersion).toBe('99.0.0');
    expect(directJson.artifact).toMatchObject({ platform: 'windows-x64', fileName: 'hydra-editor.zip' });
    await expect(resolver).toContainText(directJson.message);
    await expect(resolver).toContainText('hydra-editor.zip');

    // Un producto sin publicaciones no ofrece actualización.
    await page.locator('#publication-product').fill(uid('e2e-none'));
    await page.getByRole('button', { name: 'Check channel', exact: true }).click();
    await expect(resolver).toContainText('NO UPDATE');
    await expect(resolver).toContainText('No release is published for this channel.');
});

test('el canal y la firma elegidos en la pantalla llegan al manifiesto publicado y la dirección pública y la firma externa se exigen antes de publicar', async ({ page }) => {
    const product = uid('e2e-chan');
    const dir = seedPackage('e2e-chan');
    const artifactName = 'hydra-editor.zip';
    // Paso previo: el paquete y su firma externa «.sig» de acompañamiento están en la carpeta de distribución.
    const withoutSignature = seedPackage('e2e-nosig');
    fs.writeFileSync(path.join(workDir, DISTRIBUTION_ROOT, dir, `${artifactName}.sig`), 'firma externa de pruebas E2E\n');

    await openModule(page, '/#/publication');
    await page.getByRole('button', { name: 'Full workspace', exact: true }).click();
    await page.locator('#publication-product').fill(product);
    await page.locator('#publication-directory').fill(dir);
    await page.getByLabel('Publication platform', { exact: true }).selectOption('linux-x64');
    await page.locator('#publication-artifact').fill(artifactName);
    const results = page.locator('.publication-results');
    const diagnostic = (code: string) => results.locator('article strong', { hasText: code });

    // ch (rechazo 1): Beta sin dirección pública se bloquea y no se puede publicar.
    await page.getByLabel('Publication channel', { exact: true }).selectOption('BETA');
    await page.getByRole('button', { name: 'Validate', exact: true }).click();
    await expect(results.locator('.status-failed')).toHaveText('BLOCKED');
    await expect(diagnostic('UPDATE_URL_REQUIRED')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Publish release', exact: true })).toBeDisabled();
    expect(exists(`${PUBLICATION_ROOT}/${product}`)).toBe(false);

    // ch (rechazo 2): una dirección sin HTTPS tampoco vale.
    await page.locator('#publication-update-url').fill('http://updates.example.test/e2e');
    await page.getByRole('button', { name: 'Validate', exact: true }).click();
    await expect(diagnostic('UPDATE_URL_INSECURE')).toBeVisible();

    // ch (rechazo 3): firma externa elegida y carpeta sin el fichero de firma.
    await page.locator('#publication-update-url').fill('https://updates.example.test/e2e');
    await page.getByLabel('Publication signing mode', { exact: true }).selectOption('EXTERNAL_SIGNATURE');
    await page.locator('#publication-directory').fill(withoutSignature);
    await page.getByRole('button', { name: 'Validate', exact: true }).click();
    await expect(diagnostic('SIGNATURE_MISSING')).toBeVisible();
    expect(exists(`${PUBLICATION_ROOT}/${product}`)).toBe(false);

    // ch (éxito): carpeta con firma, canal Beta con dirección HTTPS y firma externa; se valida y se publica.
    await page.locator('#publication-directory').fill(dir);
    await page.getByRole('button', { name: 'Validate', exact: true }).click();
    await expect(results.locator('.status-passed')).toHaveText('VALID');
    await page.getByRole('button', { name: 'Publish release', exact: true }).click();
    await expect(results.getByText(product, { exact: true })).toBeVisible(); // «Published manifest» muestra el producto publicado

    // Oráculo: el manifiesto escrito por el backend recoge el canal y la firma elegidos, y la firma se copió junto al paquete.
    const channelRoot = `${PUBLICATION_ROOT}/${product}/beta`;
    type SignedManifest = Manifest & { artifacts: Array<{ signatureFile: string; downloadUrl: string }> };
    const latest = (await eventually(() => exists(`${channelRoot}/latest.json`) ? readJson<SignedManifest>(`${channelRoot}/latest.json`) : null, manifest => manifest !== null)) as SignedManifest;
    expect(latest).toMatchObject({ productId: product, channel: 'BETA', signingMode: 'EXTERNAL_SIGNATURE' });
    expect(latest.artifacts[0]).toMatchObject({ platform: 'linux-x64', fileName: artifactName, signatureFile: `${artifactName}.sig`, sha256: sha256(ARTIFACT_BYTES) });
    expect(latest.artifacts[0].downloadUrl).toContain('https://updates.example.test/e2e');
    expect(exists(`${channelRoot}/${latest.version}/${artifactName}.sig`)).toBe(true);
    expect(exists(`${PUBLICATION_ROOT}/${product}/internal`)).toBe(false); // el canal elegido es el único que se escribió
});
