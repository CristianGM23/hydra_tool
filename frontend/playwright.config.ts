import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
    testDir: './e2e',
    testIgnore: '**/real/**', // los specs de e2e/real usan playwright.real.config.ts (backend real)
    timeout: 30_000,
    fullyParallel: false,
    // Una sola instancia evita contención con las suites Maven/Vitest de HYDRA en Windows.
    workers: 1,
    use: { baseURL: process.env.HYDRA_E2E_BASE_URL ?? 'http://127.0.0.1:4173', trace: 'retain-on-failure' },
    webServer: { command: 'npm run preview -- --host 127.0.0.1 --port 4173', url: 'http://127.0.0.1:4173', reuseExistingServer: true },
    projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
