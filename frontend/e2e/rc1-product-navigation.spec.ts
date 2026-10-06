import { expect, test } from '@playwright/test';

test('RC1 shell is navigable and does not expose raw backend failures', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const url = route.request().url();
    if (url.includes('/system/status')) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ application: 'HYDRA', status: 'UP' }) });
    return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
  await page.goto('/');
  await expect(page.locator('body')).not.toContainText('BeanCreationException');
  await expect(page.locator('body')).not.toContainText('NullPointerException');
  await expect(page.locator('body')).not.toContainText('Something went wrong');
  const body = page.locator('body');
  await expect(body).toBeVisible();
});
