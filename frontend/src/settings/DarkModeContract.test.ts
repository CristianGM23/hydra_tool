import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string { return readFileSync(resolve(process.cwd(), path), 'utf8'); }

describe('HYDRA V4.28 dark mode contract', () => {
    it('persists a system/light/dark preference without changing semantic model colors', () => {
        const settings = source('src/settings/appearanceSettings.ts');
        const context = source('src/settings/AppearanceSettingsProvider.tsx');
        const dialog = source('src/settings/SettingsDialog.tsx');
        expect(settings).toContain("'SYSTEM' | 'LIGHT' | 'DARK'");
        expect(context).toContain('dataset.hydraTheme');
        expect(context).toContain("matchMedia?.('(prefers-color-scheme: dark)')");
        expect(dialog).toContain("update('colorScheme'");
        expect(dialog).toContain('never changes semantic colors');
    });

    it('uses semantic theme tokens and keeps React Flow semantic colors untouched', () => {
        const theme = source('src/design-system/hydra-theme.css');
        expect(theme).toContain('[data-hydra-theme="dark"]');
        expect(theme).toContain('--hydra-bg-surface');
        expect(theme).toContain('--hydra-text-primary');
        expect(theme).toContain('--hydra-canvas-bg');
        expect(theme).not.toContain('.react-flow__node {');
    });
});
