import { expect, test } from '@playwright/test';
import { apiGet, eventually, readJson, uid } from '../support';
import { openModule } from './helpers';

// Jornada: «Conexiones de repositorios» (modo guiado y avanzado). El usuario crea un perfil con proveedor, destino y rama. Guardar un perfil es local
// (no contacta con ningún proveedor ni necesita credenciales). Oráculo: GET a la API real de perfiles y el fichero de perfiles que escribió el backend.
test.use({ viewport: { width: 1600, height: 1000 } });

interface ProfileConfiguration { id: string; displayName: string; provider: string; enabled: boolean; owner: string; repository: string; projectId: string; branch: string; rootPath: string }
interface ManagedFile { profiles: Record<string, Record<string, unknown>> }

const PROFILES = '/api/remote-storage/profile-configurations';
const REGISTRY = 'data/remote-storage/profiles.json';

async function openNewProfileInAdvancedMode(page: import('@playwright/test').Page): Promise<void> {
    await openModule(page, '/#/repositories');
    await page.getByRole('button', { name: 'New repository', exact: true }).click();
    await expect(page.getByText('New repository profile', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Advanced / manual', exact: true }).click();
    await expect(page.locator('.repository-profile-sections')).toBeVisible();
}

test('elegir asistente o avanzado, y el destino cambia con el proveedor hasta guardar un perfil de GitHub en el almacén real', async ({ page, request }) => {
    const profileId = uid('e2e-repo');
    await openModule(page, '/#/repositories');
    await page.getByRole('button', { name: 'New repository', exact: true }).click();

    // a: el usuario alterna entre el asistente guiado (URL única) y el modo avanzado (tres bloques de datos en secciones).
    await expect(page.locator('.repository-guided-workflow')).toBeVisible();
    await expect(page.locator('#repository-guided-url')).toBeVisible();
    await expect(page.locator('.repository-profile-sections')).toHaveCount(0);
    await page.getByRole('button', { name: 'Advanced / manual', exact: true }).click();
    await expect(page.locator('.repository-profile-sections')).toBeVisible();
    await expect(page.locator('.repository-guided-workflow')).toHaveCount(0);
    await page.getByRole('button', { name: 'Guided', exact: true }).click();
    await expect(page.locator('#repository-guided-url')).toBeVisible();
    await page.getByRole('button', { name: 'Advanced / manual', exact: true }).click();
    await expect(page.locator('#repository-profile-id')).toBeVisible();

    // b: identidad del perfil (identificador, nombre visible y proveedor).
    await page.locator('#repository-profile-id').fill(profileId);
    await page.locator('#repository-display-name').fill('Repositorio E2E ' + profileId);

    // c: los campos de destino cambian con el proveedor elegido.
    const provider = page.locator('#repository-provider');
    await provider.selectOption('GITLAB');
    await expect(page.locator('#repository-project-id')).toBeVisible();
    await expect(page.locator('#repository-owner')).toHaveCount(0);
    await provider.selectOption('ONEDRIVE');
    await expect(page.locator('#repository-drive-id')).toBeVisible();
    await expect(page.locator('#repository-branch')).toHaveCount(0);
    await provider.selectOption('GENERIC_GIT');
    await expect(page.locator('#repository-generic-url')).toBeVisible();
    await provider.selectOption('GITHUB');
    await expect(page.locator('#repository-owner')).toBeVisible();
    await expect(page.locator('#repository-name')).toBeVisible();
    await expect(page.locator('#repository-branch')).toBeVisible();
    await page.locator('#repository-owner').fill('e2e-org');
    await page.locator('#repository-name').fill('e2e-repo');
    await page.locator('#repository-branch').fill('release');

    // Rechazo: sin carpeta raíz el perfil no se guarda y el backend no lo conoce.
    await page.getByRole('button', { name: 'Save repository', exact: true }).click();
    await expect(page.locator('.repository-profile-message')).toBeVisible();
    await expect(page.locator('#repository-root-path')).toHaveAttribute('aria-invalid', 'true');
    expect((await apiGet<ProfileConfiguration[]>(request, PROFILES)).some(item => item.id === profileId)).toBe(false);

    await page.locator('#repository-root-path').fill('e2e/root');
    await page.getByRole('button', { name: 'Save repository', exact: true }).click();
    await expect(page.locator('.repository-save-verified')).toContainText(profileId);

    // Oráculo 1: la API real devuelve el perfil con el proveedor y las coordenadas introducidas.
    const saved = (await apiGet<ProfileConfiguration[]>(request, PROFILES)).find(item => item.id === profileId);
    expect(saved).toBeDefined();
    expect(saved).toMatchObject({ provider: 'GITHUB', owner: 'e2e-org', repository: 'e2e-repo', branch: 'release', rootPath: 'e2e/root', enabled: true });

    // Oráculo 2: el fichero de perfiles escrito por el backend contiene lo mismo y ningún secreto.
    const registry = await eventually(() => readJson<ManagedFile>(REGISTRY), file => file.profiles[profileId] !== undefined);
    const stored = registry.profiles[profileId];
    expect(stored).toMatchObject({ provider: 'GITHUB', owner: 'e2e-org', repository: 'e2e-repo', branch: 'release', rootPath: 'e2e/root' });
    expect(JSON.stringify(stored)).not.toMatch(/token|secret|password/i);

    // El perfil sobrevive a recargar la página: sale en la lista del navegador.
    await page.reload();
    await expect(page.locator('.repository-profile-list').getByText('Repositorio E2E ' + profileId)).toBeVisible();
});

test('en un perfil de GitLab el destino pide el proyecto y la rama, y queda guardado con esas coordenadas', async ({ page, request }) => {
    const profileId = uid('e2e-gl');
    await openNewProfileInAdvancedMode(page);
    await page.locator('#repository-profile-id').fill(profileId);
    await page.locator('#repository-display-name').fill('GitLab E2E ' + profileId);
    await page.locator('#repository-provider').selectOption('GITLAB');
    await expect(page.locator('#repository-project-id')).toBeVisible();
    await expect(page.locator('#repository-owner')).toHaveCount(0);
    await page.locator('#repository-project-id').fill('e2e-group/e2e-project');
    await page.locator('#repository-branch').fill('develop');
    await page.locator('#repository-root-path').fill('docs/hydra');
    await page.getByRole('button', { name: 'Save repository', exact: true }).click();
    await expect(page.locator('.repository-save-verified')).toContainText(profileId);

    const saved = (await apiGet<ProfileConfiguration[]>(request, PROFILES)).find(item => item.id === profileId);
    expect(saved).toMatchObject({ provider: 'GITLAB', projectId: 'e2e-group/e2e-project', branch: 'develop', rootPath: 'docs/hydra', owner: '' });
    const registry = await eventually(() => readJson<ManagedFile>(REGISTRY), file => file.profiles[profileId] !== undefined);
    expect(registry.profiles[profileId]).toMatchObject({ provider: 'GITLAB', projectId: 'e2e-group/e2e-project', branch: 'develop', rootPath: 'docs/hydra' });
});

test('el asistente guiado reconoce una URL de GitHub y guarda el mismo perfil (proveedor, repositorio, rama y carpeta) que el modo avanzado', async ({ page, request }) => {
    const repo = uid('guided-repo');
    const displayName = `Guiado E2E ${repo}`;
    await openModule(page, '/#/repositories');
    await page.getByRole('button', { name: 'New repository', exact: true }).click();
    await expect(page.locator('.repository-guided-workflow')).toBeVisible(); // a: el asistente es el modo por defecto (URL única)

    // Rechazo: sin URL el asistente no guarda nada.
    await page.locator('#repository-guided-name').fill(displayName);
    await page.getByRole('button', { name: 'Save repository', exact: true }).click();
    await expect(page.locator('.repository-profile-message')).toBeVisible();
    expect((await apiGet<ProfileConfiguration[]>(request, PROFILES)).some(item => item.displayName === displayName)).toBe(false);

    // Una sola URL: el sistema deduce proveedor, repositorio, rama y carpeta.
    await page.locator('#repository-guided-url').fill(`https://github.com/e2e-guided-org/${repo}/tree/dev/docs/hydra`);
    await page.getByRole('button', { name: 'Recognize destination', exact: true }).click();
    await expect(page.locator('.repository-url-resolution-success')).toContainText('Recognized');
    await page.getByRole('button', { name: 'Save repository', exact: true }).click();
    await expect(page.locator('.repository-save-verified')).toBeVisible();

    // Oráculo: la API real y el fichero de perfiles traen los mismos tres bloques que se habrían escrito a mano en el modo avanzado.
    const saved = await eventually(async () => (await apiGet<ProfileConfiguration[]>(request, PROFILES)).find(item => item.displayName === displayName), value => value !== undefined);
    expect(saved).toMatchObject({ provider: 'GITHUB', owner: 'e2e-guided-org', repository: repo, branch: 'dev', rootPath: 'docs/hydra', enabled: true });
    const registry = readJson<ManagedFile>(REGISTRY);
    expect(registry.profiles[saved!.id]).toMatchObject({ provider: 'GITHUB', owner: 'e2e-guided-org', repository: repo, branch: 'dev', rootPath: 'docs/hydra' });
    expect(JSON.stringify(registry.profiles[saved!.id])).not.toMatch(/token|secret|password/i);
});
