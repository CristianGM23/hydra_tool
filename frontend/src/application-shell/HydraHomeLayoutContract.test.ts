import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const root = resolve(import.meta.dirname, '..', '..');
const styles = [
    readFileSync(resolve(root, 'src/application-shell/application-shell.css'), 'utf8'),
    readFileSync(resolve(root, 'src/application-shell/application-shell-home-dashboard.css'), 'utf8')
].join('\n');
const rail = readFileSync(resolve(root, 'src/application-shell/HydraModuleRail.tsx'), 'utf8');
const home = readFileSync(resolve(root, 'src/application-shell/HydraHome.tsx'), 'utf8');

describe('Home responsive layout contract', () => {
    it('uses the full available editor width instead of centering a narrow fixed column', () => {
        expect(styles).toContain('.hydra-home-project-dashboard .hydra-home-content > .hydra-home-dashboard');
        expect(styles).toContain('max-width: none !important');
        expect(styles).toContain('width: 100% !important');
    });

    it('keeps navigation in the global left rail instead of rendering a second rail inside Home', () => {
        expect(rail).toContain('hydra-module-rail-home-section');
        expect(rail).toContain('Start or continue');
        expect(rail).toContain('Quick start');
        expect(home).not.toContain('hydra-home-section-rail');
        expect(styles).toContain('.hydra-home-project-dashboard > .hydra-home-section-rail');
        expect(styles).toContain('display: none !important');
    });

    it('allows Home card copy to wrap instead of being clipped by the shared button style', () => {
        expect(styles).toContain('.hydra-home-area-card-shell .hydra-ui-button.hydra-home-area-card');
        expect(styles).toContain('white-space: normal !important');
        expect(styles).toContain('overflow-wrap: break-word');
        expect(styles).toContain('minmax(300px, 1fr)');
    });
});
