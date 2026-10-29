import { defineConfig, devices } from '@playwright/test';

// E2E REAL: navegador real + frontend/dist + backend Spring Boot real con almacenamiento temporal propio.
// Prohibido en estos specs: page.route, mockHydraApi o cualquier simulación de la API.
const web = Number(process.env.HYDRA_E2E_WEB_PORT ?? 4183);
const api = Number(process.env.HYDRA_E2E_API_PORT ?? 18081);

export default defineConfig({
    testDir: './e2e/real',
    outputDir: process.env.HYDRA_E2E_OUTPUT_DIR ?? 'test-results/real',
    timeout: 90_000,
    expect: { timeout: 15_000 },
    fullyParallel: false,
    workers: 1,
    retries: 0,
    reporter: [['json', { outputFile: process.env.PLAYWRIGHT_JSON_OUTPUT_NAME ?? 'test-results/real-e2e.json' }], ['list']],
    use: { baseURL: `http://127.0.0.1:${web}`, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
    webServer: [
        { command: 'node e2e/real-backend.mjs', url: `http://127.0.0.1:${api}/api/system/status`, timeout: 240_000, reuseExistingServer: false, stdout: 'pipe', stderr: 'pipe' },
        { command: 'node e2e/real-web.mjs', url: `http://127.0.0.1:${web}/`, timeout: 30_000, reuseExistingServer: false },
    ],
    projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
