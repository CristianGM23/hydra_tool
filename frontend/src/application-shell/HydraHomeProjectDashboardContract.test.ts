import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const root = resolve(import.meta.dirname, '..', '..');
const home = readFileSync(resolve(root, 'src/application-shell/HydraHome.tsx'), 'utf8');
const app = readFileSync(resolve(root, 'src/App.tsx'), 'utf8');
const explorer = readFileSync(resolve(root, 'src/project-explorer/ProjectExplorerWorkspace.tsx'), 'utf8');
const manager = readFileSync(resolve(root, 'src/project-explorer/ProjectManagerDialog.tsx'), 'utf8');
const rail = readFileSync(resolve(root, 'src/application-shell/HydraModuleRail.tsx'), 'utf8');
const shell = readFileSync(resolve(root, 'src/application-shell/HydraApplicationShell.tsx'), 'utf8');

describe('project-first Home dashboard contract', () => {
    it('keeps Home as a card dashboard backed by the active HYDRA project', () => {
        expect(home).toContain('hydra-home-primary-actions');
        expect(home).toContain('hydra-home-card-grid');
        expect(home).toContain('getProjectSession');
        expect(home).toContain('listRecentProjects');
        expect(home).toContain('enabledCapabilities');
    });

    it('keeps project creation and access in the same project explorer workflow', () => {
        expect(app).toContain("['HOME', 'MODEL'");
        expect(home).toContain("hydra:project-manager-open");
        expect(explorer).toContain("hydra:project-manager-open");
        expect(explorer).toContain("hydra:project-open");
        expect(manager).toContain('initialPreset');
        expect(manager).toContain('initialAccessSource');
    });


    it('keeps the HYDRA navigation rail outside Home and before the project explorer', () => {
        expect(shell).toContain('HydraModuleRail');
        expect(shell).toContain('const showRail = true');
        expect(home).not.toContain('hydra-home-section-rail');
        expect(rail).toContain('hydra-module-rail-home-section');
        expect(rail).toContain("hydra:home-section-open");
        expect(rail).toContain('Language and behavior');
        expect(rail).toContain('Models and connections');
        expect(rail).toContain('Import and delivery');
        expect(rail).toContain('Current status and tools');
    });

    it('does not expose low-level toolchain or artifact terminology in Home copy', () => {
        const visibleCopy = home.replace(/primaryArtifact/g, '').replace(/moduleForPrimaryArtifact/g, '').replace(/primaryArtifactLabel/g, '');
        expect(visibleCopy.toLowerCase()).not.toContain('toolchain');
        expect(visibleCopy.toLowerCase()).not.toContain('artifact');
        expect(home).toContain('Main component');
        expect(home).not.toContain('Available tools');
    });
});
