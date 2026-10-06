import { expect, test } from '@playwright/test';
import { installMockHydraApi } from './mockHydraApi';

test('event architecture nodes occupy visible canvas space', async ({ page }) => {
  await installMockHydraApi(page, { semanticContext: 'EVENTS' });
  await page.goto('/#/events');
  const eventViews = page.locator('.event-subtabs');
  await expect(eventViews).toBeVisible({ timeout: 10_000 });
  const architectureTab = eventViews.getByRole('button', { name: /Event architecture|Arquitectura de eventos/i });
  await expect(architectureTab).toBeVisible();
  await architectureTab.click();
  const canvas = page.locator('.event-architecture-canvas').first();
  await expect(canvas).toBeVisible();
  const paletteGroup = page.locator('details.event-architecture-palette-group').filter({
    has: page.locator('.event-architecture-catalog-item:not(.on-canvas)'),
  }).first();
  await expect(paletteGroup.locator('summary')).toBeVisible();
  await paletteGroup.locator('summary').click();
  const paletteItem = paletteGroup.locator('.event-architecture-catalog-item:not(.on-canvas)').first();
  await expect(paletteItem).toBeVisible();
  await paletteItem.click();
  const node = canvas.locator('.react-flow__node').first();
  await expect(node).toBeVisible();
  const canvasBox = await canvas.boundingBox();
  const nodeBox = await node.boundingBox();
  expect(canvasBox).not.toBeNull(); expect(nodeBox).not.toBeNull();
  const intersects = nodeBox!.x < canvasBox!.x + canvasBox!.width && nodeBox!.x + nodeBox!.width > canvasBox!.x
    && nodeBox!.y < canvasBox!.y + canvasBox!.height && nodeBox!.y + nodeBox!.height > canvasBox!.y;
  expect(intersects).toBeTruthy();
});
