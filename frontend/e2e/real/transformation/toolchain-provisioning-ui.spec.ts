import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { apiGet, eventually, exists, openApp, uid, workDir } from '../support';
import { openDesigner, savedSelector, seedMetamodel, switchToAdvanced } from './helpers';

// Jornada: panel de soporte de ejecución por lenguaje del diseñador de transformaciones. El catálogo de confianza del servidor
// (carpeta de distribución del backend real) se prepara en disco con paquetes de prueba: dos versiones válidas de ATL, una con la
// suma de verificación manipulada y una de ETL. Acciones de usuario: elegir el lenguaje y abrir el selector de versión del panel;
// y, en otra pantalla, el centro de compatibilidad nativa. Oráculo: GET a los dos catálogos reales (el de transformación y el nativo)
// y el estado del disco gestionado. El backend de e2e se lanza con la instalación desactivada: Instalar/Actualizar/Eliminar no se
// pueden accionar (esa etapa no se reclama; aquí solo se prueba que la puerta está cerrada).

interface PackageDescriptor { language: string; packageId: string; packageVersion: string; installable: boolean; installed: boolean; diagnostics: string[] }
interface ToolchainCatalog { provisioningEnabled: boolean; packages: PackageDescriptor[]; installations: Array<{ language: string; present: boolean; hydraManaged: boolean }>; diagnostics: string[] }
interface NativeCatalogEntry { key: string; technology: string }

const DISTRIBUTION = path.join(workDir, 'distribution', 'transformation-toolchains');
const BUNDLE = { schemaVersion: '1.0', bridgeName: 'e2e-bridge', bridgeVersion: '1', command: ['${java}', '-version'] };

interface Fixture { atlPackage: string; etlPackage: string; transformationId: string; roots: string[] }

/** Escribe un paquete del catálogo de confianza (paso previo, no certificado): package.json con sumas SHA-256 + payload/toolchain.json. */
function writePackage(language: 'ATL' | 'ETL', packageId: string, version: string, tamper: boolean): string {
    const versionRoot = path.join(DISTRIBUTION, language.toLowerCase(), packageId, version);
    const payload = path.join(versionRoot, 'payload');
    fs.mkdirSync(payload, { recursive: true });
    const runtime = JSON.stringify({ ...BUNDLE, language, toolchainName: `e2e-${language.toLowerCase()}`, toolchainVersion: version }, null, 2);
    fs.writeFileSync(path.join(payload, 'toolchain.json'), runtime, 'utf8');
    const digest = tamper ? '0'.repeat(64) : createHash('sha256').update(Buffer.from(runtime, 'utf8')).digest('hex');
    fs.writeFileSync(path.join(versionRoot, 'package.json'), JSON.stringify({
        schemaVersion: '1.0', packageId, packageVersion: version, language, bridgeName: BUNDLE.bridgeName, bridgeVersion: BUNDLE.bridgeVersion,
        toolchainName: `e2e-${language.toLowerCase()}`, toolchainVersion: version, sha256: { 'toolchain.json': digest },
    }, null, 2), 'utf8');
    return path.join(DISTRIBUTION, language.toLowerCase(), packageId);
}

async function seedTransformation(request: APIRequestContext): Promise<string> {
    const srcId = uid('e2e-tsrc');
    const tgtId = uid('e2e-ttgt');
    const srcClass = `${srcId}-person`;
    const tgtClass = `${tgtId}-contact`;
    await seedMetamodel(request, { id: srcId, name: srcId, classes: [{ id: srcClass, name: 'Person', attrs: [{ id: `${srcId}-name`, name: 'name' }] }] });
    await seedMetamodel(request, { id: tgtId, name: tgtId, classes: [{ id: tgtClass, name: 'Contact', attrs: [{ id: `${tgtId}-name`, name: 'name' }] }] });
    const endpoint = (id: string, role: string, metamodelId: string) => ({ id, role, metamodelId, metamodelFingerprint: '', alias: id, required: true, annotations: {} });
    const valuePolicy = { collectionMode: 'PRESERVE', conversionMode: 'NONE', enumMapping: {}, errorPolicy: 'FAIL' };
    const doc = {
        schemaVersion: '1.1', id: uid('e2e-prov-tr'), name: uid('e2e-prov-tr'), kind: 'M2M', directionality: 'BIDIRECTIONAL',
        synchronizationPolicy: { conflictPolicy: 'FAIL_ON_CONFLICT', propagateDeletions: false, requireTraceForUpdates: true },
        sources: [endpoint('source', 'SOURCE', srcId)], targets: [endpoint('target', 'TARGET', tgtId)],
        rules: [
            { id: 'rule-forward', name: 'Person to Contact', enabled: true, sourceEndpointId: 'source', sourceTypeId: srcClass, sourceAlias: 'person',
                targets: [{ id: 'tp-forward', endpointId: 'target', typeId: tgtClass, alias: 'contact', annotations: {} }],
                bindings: [{ id: 'bind-forward', targetAlias: 'contact', targetFeatureId: `${tgtId}-name`, kind: 'DIRECT', sourceFeatureId: `${srcId}-name`, valuePolicy, annotations: {} }],
                annotations: {}, direction: 'FORWARD', correspondenceId: 'corr-person-contact' },
            { id: 'rule-reverse', name: 'Contact to Person', enabled: true, sourceEndpointId: 'target', sourceTypeId: tgtClass, sourceAlias: 'contact',
                targets: [{ id: 'tp-reverse', endpointId: 'source', typeId: srcClass, alias: 'person', annotations: {} }],
                bindings: [{ id: 'bind-reverse', targetAlias: 'person', targetFeatureId: `${srcId}-name`, kind: 'DIRECT', sourceFeatureId: `${tgtId}-name`, valuePolicy, annotations: {} }],
                annotations: {}, direction: 'REVERSE', correspondenceId: 'corr-person-contact' },
        ],
        generationRules: [], executionPolicy: { mode: 'BATCH', deterministic: true, failOnBindingError: true },
        tracePolicy: { enabled: true, retainBindingDetails: true }, annotations: {},
    };
    const response = await request.post('/api/transformations', { data: doc });
    if (!response.ok()) throw new Error(`POST /api/transformations -> ${response.status()} ${await response.text()}`);
    return doc.id;
}

async function seedFixture(request: APIRequestContext): Promise<Fixture> {
    const atlPackage = uid('e2e-atl-pkg');
    const etlPackage = uid('e2e-etl-pkg');
    const roots = [
        writePackage('ATL', atlPackage, '1.0.0', false),
        writePackage('ATL', atlPackage, '2.0.0', false),
        writePackage('ATL', atlPackage, '3.0.0', true),
        writePackage('ETL', etlPackage, '1.0.0', false),
    ];
    return { atlPackage, etlPackage, transformationId: await seedTransformation(request), roots };
}

function cleanup(fixture: Fixture | null): void {
    for (const root of fixture?.roots ?? []) fs.rmSync(root, { recursive: true, force: true });
}

const executionLanguage = (page: Page) => page.getByRole('combobox', { name: /^(Execution language|Lenguaje de ejecución)$/ });
const panel = (page: Page) => page.locator('.transformation-toolchain-provisioning');
const packageSelect = (page: Page) => panel(page).getByRole('combobox', { name: /^(Available execution package|Soporte de ejecución disponible)$/ });
const optionValues = (page: Page) => packageSelect(page).locator('option').evaluateAll((items) => items.map((item) => (item as HTMLOptionElement).value));

async function openPanel(page: Page, transformationId: string): Promise<void> {
    await openDesigner(page);
    await expect(savedSelector(page)).toBeVisible();
    await savedSelector(page).selectOption(transformationId);
    await switchToAdvanced(page);
    await expect(panel(page)).toBeVisible();
}

const installableKeys = (catalog: ToolchainCatalog, language: string, packageId: string): string[] =>
    catalog.packages.filter((item) => item.language === language && item.packageId === packageId && item.installable).map((item) => `${item.packageId}@${item.packageVersion}`).sort();

test('el panel ofrece por lenguaje solo los paquetes verificados del catálogo de confianza y nunca el de suma manipulada', async ({ page, request }) => {
    let fixture: Fixture | null = null;
    try {
        fixture = await seedFixture(request);
        const { atlPackage, etlPackage } = fixture;
        await openPanel(page, fixture.transformationId);

        // a: «ATL» es el lenguaje inicial; el selector del panel lista exactamente lo que el backend declara instalable para ese lenguaje.
        await executionLanguage(page).selectOption('ATL');
        const atlOffered = await eventually(() => optionValues(page), (values) => values.some((value) => value.startsWith(`${atlPackage}@`)));
        const catalog = await apiGet<ToolchainCatalog>(request, '/api/transformations/parity/native/toolchains');
        expect(atlOffered.filter((value) => value.startsWith(`${atlPackage}@`)).sort()).toEqual(installableKeys(catalog, 'ATL', atlPackage));
        expect(installableKeys(catalog, 'ATL', atlPackage)).toEqual([`${atlPackage}@1.0.0`, `${atlPackage}@2.0.0`]);
        // El panel de ATL no ofrece nada de ETL.
        expect(atlOffered.some((value) => value.startsWith(`${etlPackage}@`))).toBe(false);

        // b (rechazo): el paquete con suma manipulada existe en el catálogo del servidor pero no es instalable, y la interfaz no lo ofrece.
        const tampered = catalog.packages.find((item) => item.packageId === atlPackage && item.packageVersion === '3.0.0');
        expect(tampered).toBeDefined();
        expect(tampered?.installable).toBe(false);
        expect(tampered?.diagnostics.length).toBeGreaterThan(0); // el servidor rechaza el paquete al verificarlo
        expect(atlOffered).not.toContain(`${atlPackage}@3.0.0`);

        // a: cambiar el lenguaje cambia el catálogo mostrado: ahora solo el paquete de ETL.
        await executionLanguage(page).selectOption('ETL');
        const etlOffered = await eventually(() => optionValues(page), (values) => values.some((value) => value.startsWith(`${etlPackage}@`)));
        expect(etlOffered.filter((value) => value.startsWith(`${etlPackage}@`)).sort()).toEqual(installableKeys(catalog, 'ETL', etlPackage));
        expect(etlOffered.some((value) => value.startsWith(`${atlPackage}@`))).toBe(false);

        // b: elegir una versión en el selector deja esa versión seleccionada (valor de la opción = paquete@versión del catálogo).
        await executionLanguage(page).selectOption('ATL');
        await eventually(() => optionValues(page), (values) => values.includes(`${atlPackage}@2.0.0`));
        await packageSelect(page).selectOption(`${atlPackage}@2.0.0`);
        await expect(packageSelect(page)).toHaveValue(`${atlPackage}@2.0.0`);
    } finally { cleanup(fixture); }
});

test('con la instalación desactivada por el administrador el panel lo dice, no deja instalar ni eliminar y el servidor rechaza la orden sin tocar el disco gestionado', async ({ page, request }) => {
    let fixture: Fixture | null = null;
    try {
        fixture = await seedFixture(request);
        const catalog = await apiGet<ToolchainCatalog>(request, '/api/transformations/parity/native/toolchains');
        // Este spec solo certifica la puerta cerrada: si el backend se lanzase con la instalación habilitada habría que ampliarlo.
        expect(catalog.provisioningEnabled).toBe(false);
        await openPanel(page, fixture.transformationId);
        await executionLanguage(page).selectOption('ATL');
        await eventually(() => optionValues(page), (values) => values.includes(`${fixture?.atlPackage}@1.0.0`));
        await expect(panel(page)).toContainText(/Installing execution support is disabled by the server administrator|La instalación del soporte de ejecución está desactivada por el administrador del servidor/);
        await expect(panel(page).getByRole('button', { name: /^(Install execution support|Instalar soporte de ejecución|Update execution support|Actualizar soporte de ejecución)$/ })).toBeDisabled();
        await expect(panel(page).getByRole('button', { name: /^(Remove execution support|Eliminar soporte de ejecución)$/ })).toBeDisabled();

        // La orden directa al backend real también se rechaza y no crea ningún bundle gestionado.
        const forced = await request.post('/api/transformations/parity/native/toolchains/ATL/install', {
            data: { packageId: fixture.atlPackage, packageVersion: '1.0.0', expectedCurrentPackageId: '', expectedCurrentPackageVersion: '' },
        });
        expect(forced.ok()).toBe(false);
        expect(exists('data/transformation-toolchains/atl/.hydra-package.json')).toBe(false);
        const after = await apiGet<ToolchainCatalog>(request, '/api/transformations/parity/native/toolchains');
        expect(after.packages.find((item) => item.packageId === fixture?.atlPackage && item.packageVersion === '1.0.0')?.installed).toBe(false);
    } finally { cleanup(fixture); }
});

test('el centro de compatibilidad nativa y el panel de transformación muestran catálogos distintos', async ({ page, request }) => {
    let fixture: Fixture | null = null;
    try {
        fixture = await seedFixture(request);
        const { atlPackage, transformationId } = fixture;
        const nativeCatalog = await apiGet<NativeCatalogEntry[]>(request, '/api/toolchain-compatibility/catalog');
        const transformationCatalog = await apiGet<ToolchainCatalog>(request, '/api/transformations/parity/native/toolchains');
        const nativeKeys = nativeCatalog.map((entry) => entry.key);
        // Oráculo: los dos catálogos del servidor son disjuntos.
        expect(transformationCatalog.packages.some((item) => item.packageId === atlPackage)).toBe(true);
        expect(nativeKeys.some((key) => key.includes(atlPackage))).toBe(false);
        expect(transformationCatalog.packages.some((item) => nativeKeys.includes(`${item.packageId}@${item.packageVersion}`) || nativeKeys.includes(item.packageId))).toBe(false);

        // Superficie genérica: una casilla por tecnología nativa y ni rastro de los paquetes de transformación.
        await openApp(page, '/#/compatibility');
        const checkboxes = page.locator('.native-guided-selection input[type="checkbox"]');
        await expect(checkboxes).toHaveCount(nativeCatalog.length);
        await expect(page.locator('.native-guided-selection')).not.toContainText(atlPackage);

        // Superficie del motor de transformación: ofrece los paquetes de transformación y ninguna clave del catálogo nativo.
        await openPanel(page, transformationId);
        await executionLanguage(page).selectOption('ATL');
        const offered = await eventually(() => optionValues(page), (values) => values.includes(`${atlPackage}@1.0.0`));
        expect(offered.some((value) => nativeKeys.includes(value))).toBe(false);
        await expect(page.locator('.native-guided-selection')).toHaveCount(0);
    } finally { cleanup(fixture); }
});
