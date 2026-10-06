import { expect, test } from '@playwright/test';
import { installMockHydraApi } from './mockHydraApi';

test.describe('keyboard, focus and zoom accessibility', () => {
    test.beforeEach(async ({ page }) => { await installMockHydraApi(page); });
    test('keeps a visible keyboard focus target and no horizontal page overflow at 200% equivalent zoom', async ({ page }) => {
        await page.setViewportSize({ width: 640, height: 900 });
        await page.goto('/');
        await page.keyboard.press('Tab');
        await expect(page.locator(':focus')).toBeVisible();
        const focusVisible = await page.locator(':focus').evaluate(element => getComputedStyle(element).outlineStyle !== 'none' || getComputedStyle(element).boxShadow !== 'none');
        expect(focusVisible).toBeTruthy();
        await page.evaluate(() => { document.documentElement.style.zoom = '2'; });
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow).toBeLessThanOrEqual(2);
    });


    test('keeps Home and shared chrome readable when dark mode is explicitly selected', async ({ page }) => {
        await page.addInitScript(() => {
            window.localStorage.setItem('modeling-editor.appearance.v1', JSON.stringify({ colorScheme: 'DARK' }));
        });
        await page.goto('/');
        await expect(page.locator('html')).toHaveAttribute('data-hydra-theme', 'dark');
        await expect(page.locator('.hydra-home')).toBeVisible();

        const checks = await page.evaluate(() => {
            const parse = (value: string): [number, number, number] => {
                const parts = value.match(/\d+(?:\.\d+)?/g)?.slice(0, 3).map(Number) ?? [0, 0, 0];
                return [parts[0] ?? 0, parts[1] ?? 0, parts[2] ?? 0];
            };
            const luminance = ([r, g, b]: [number, number, number]) => {
                const channel = (value: number) => { const c = value / 255; return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4; };
                return .2126 * channel(r) + .7152 * channel(g) + .0722 * channel(b);
            };
            const ratio = (foreground: string, background: string) => {
                const a = luminance(parse(foreground)); const b = luminance(parse(background));
                return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
            };
            const sample = (selector: string) => {
                const element = document.querySelector(selector);
                if (!(element instanceof HTMLElement)) return null;
                const style = getComputedStyle(element);
                return { background: style.backgroundColor, color: style.color, contrast: ratio(style.color, style.backgroundColor) };
            };
            return {
                home: sample('.hydra-home'),
                rail: sample('.hydra-home-section-rail'),
                card: sample('.hydra-home-overview-grid > article'),
                moduleRow: sample('.hydra-home-module-row')
            };
        });
        for (const sample of Object.values(checks)) {
            if (sample === null) continue;
            expect(sample.background).not.toBe('rgb(255, 255, 255)');
            expect(sample.contrast).toBeGreaterThanOrEqual(4.5);
        }
    });

    test('remains navigable at a 400% narrow-screen equivalent', async ({ page }) => {
        await page.setViewportSize({ width: 320, height: 900 });
        await page.goto('/');
        await page.evaluate(() => { document.documentElement.style.zoom = '2'; });
        for (let index = 0; index < 6; index += 1) await page.keyboard.press('Tab');
        await expect(page.locator(':focus')).toBeVisible();
    });
});
