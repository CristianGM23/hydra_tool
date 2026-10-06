import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(process.cwd(), 'src/settings/settings.css'), 'utf8');
const theme = readFileSync(resolve(process.cwd(), 'src/design-system/hydra-theme.css'), 'utf8');

describe('global responsive control style contract', () => {
    it('defines a responsive scale variable', () => expect(css).toContain('--app-responsive-scale'));
    it('defines an adaptive control height', () => expect(css).toContain('--app-control-min-height'));
    it('provides a low-specificity fallback for every button', () => expect(css).toContain(':where(button)'));
    it('uses a pill radius for unstyled buttons', () => expect(css).toContain('border-radius: 999px'));
    it('provides hover feedback for enabled buttons', () => expect(css).toContain('button:hover:not(:disabled)'));
    it('provides keyboard focus feedback for buttons', () => expect(css).toContain('button:focus-visible'));
    it('replaces the native checkbox rendering', () => expect(css).toContain('input[type="checkbox"]'));
    it('increases checkbox size through a shared variable', () => expect(css).toContain('--app-checkbox-size'));
    it('draws a checked-state mark without an image dependency', () => expect(css).toContain('input[type="checkbox"]:checked)::before'));
    it('keeps the checked state visible against form-specific input backgrounds', () => {
        const checkedOverride = css.match(/input\[type="checkbox"\]:checked\s*\{([\s\S]*?)\}/)?.[1] ?? '';

        expect(checkedOverride).toContain('background-color: var(--hydra-palette-blue-600) !important');
        expect(theme).toContain('--hydra-palette-blue-600: #2563eb;');
        expect(css).toContain('transform: translateY(-8%) rotate(-45deg) scale(1) !important');
    });
    it('keeps disabled checkbox feedback', () => expect(css).toContain('input[type="checkbox"]:disabled'));
    it('adapts the workspace columns at narrower widths', () => expect(css).toContain('@media (max-width: 1120px)'));
    it('provides a compact two-column fallback', () => expect(css).toContain('@media (max-width: 820px)'));
});
