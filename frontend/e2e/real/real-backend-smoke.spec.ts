import { expect, test } from '@playwright/test';

// Verificación de la infraestructura E2E REAL (sin page.route): el navegador habla con el frontend compilado y, a través del
// proxy real, con el backend Spring Boot real de HYDRA.
test('el backend real responde a través del proxy y la aplicación se renderiza sin errores crudos', async ({ page, request }) => {
    const status = await request.get('/api/system/status');
    expect(status.ok()).toBeTruthy();
    const body = await status.json();
    expect(body.application).toBe('HYDRA');
    expect(body.status).toBe('UP');
    await page.goto('/');
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('BeanCreationException');
    await expect(page.locator('body')).not.toContainText('NullPointerException');
});