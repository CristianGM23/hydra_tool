import { chromium, type FullConfig } from '@playwright/test';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';

// Navegador de los E2E: Google Chrome estable instalado (canal 'chrome'), no el Chromium empaquetado con Playwright, para que las
// correcciones de seguridad de Chrome lleguen sin esperar a una versión nueva de Playwright. Antes de la primera prueba se lanza el
// mismo canal, se registra el ejecutable y la versión que informa el propio navegador, y se aborta si es anterior a la mínima exigida.
export const HYDRA_E2E_CHANNEL = 'chrome';
// Chrome estable del 03/11/2026 (157.0.8103.38 Linux, .39 Windows; versionhistory.googleapis.com): incluye además las correcciones de
// seguridad del 27/10 (156.0.8078.73/.74) y del 29/10 (156.0.8078.79/.80).
export const HYDRA_E2E_MIN_CHROME = '157.0.8103.38';

const parts = (version: string): number[] => version.split('.').map((n) => Number.parseInt(n, 10) || 0);

export function olderThan(version: string, minimum: string): boolean {
    const a = parts(version), b = parts(minimum);
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
        if ((a[i] ?? 0) !== (b[i] ?? 0)) return (a[i] ?? 0) < (b[i] ?? 0);
    }
    return false;
}

export default async function globalSetup(config: FullConfig): Promise<void> {
    const project = config.projects[0];
    const channel = project?.use.channel;
    if (channel !== HYDRA_E2E_CHANNEL) throw new Error(`HYDRA E2E: el proyecto debe usar channel '${HYDRA_E2E_CHANNEL}' (actual: ${String(channel)})`);
    const server = await chromium.launchServer({ channel, headless: true });
    try {
        const executable = server.process().spawnfile;
        const browser = await chromium.connect(server.wsEndpoint());
        const version = browser.version();
        await browser.close();
        const playwright = (createRequire(import.meta.url)('@playwright/test/package.json') as { version: string }).version;
        const record = { channel, version, minimum: HYDRA_E2E_MIN_CHROME, executable, playwright, platform: process.platform, arch: process.arch, at: new Date().toISOString() };
        console.log(`HYDRA_E2E_BROWSER ${JSON.stringify(record)}`);
        const base = config.configFile ? path.dirname(config.configFile) : process.cwd();
        const file = process.env.HYDRA_E2E_BROWSER_RECORD ?? path.join(base, 'test-results', 'browser', `${path.basename(config.configFile ?? 'playwright')}.json`);
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, JSON.stringify(record, null, 1) + '\n', 'utf8');
        if (olderThan(version, HYDRA_E2E_MIN_CHROME)) {
            throw new Error(`HYDRA E2E: Chrome ${version} es anterior a ${HYDRA_E2E_MIN_CHROME}, la versión mínima con las correcciones de seguridad exigidas; actualiza Google Chrome`);
        }
    } finally {
        await server.close();
    }
}
