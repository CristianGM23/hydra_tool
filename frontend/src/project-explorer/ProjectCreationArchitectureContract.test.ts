import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const manager = readFileSync(resolve(here, 'ProjectManagerDialog.tsx'), 'utf8');
const explorer = readFileSync(resolve(here, 'ProjectExplorerWorkspace.tsx'), 'utf8');
const capabilities = readFileSync(resolve(here, 'ProjectCapabilitiesDialog.tsx'), 'utf8');

describe('HYDRA Project creation architecture', () => {
    it('uses presets as initial area configurations rather than permanent project types', () => {
        expect(manager).toContain('What do you want to create?');
        expect(manager).toContain('Choose project areas');
        expect(manager).toContain('selectPreset');
        expect(manager).toContain('selectedCapabilities');
        expect(manager).toContain("'GUIDED'");
        expect(manager).toContain("'FREE'");
        expect(manager).not.toContain('Customize capabilities');
    });

    it('preserves the contextual project-manager entry point selected from Home', () => {
        expect(manager).toContain('initialEntryPoint');
        expect(manager).toContain('HOME_QUICK_PRESET');
        expect(explorer).toContain('entryPoint');
        expect(explorer).toContain('managerDefaults.entryPoint');
    });

    it('exposes standard context actions for project entries using the current user-facing language', () => {
        for (const label of ['New file…', 'New folder…', 'Cut', 'Copy', 'Duplicate', 'Rename…', 'Copy local path', 'Export selection as ZIP', 'Refresh', 'Delete']) {
            expect(explorer).toContain(label);
        }
        expect(explorer).toContain('Project areas…');
        expect(explorer).toContain('Set as main component');
        expect(explorer).toContain('setProjectCapabilitiesOpen(true)');
        expect(explorer).toContain('setSelectedAsPrimaryArtifact');
        expect(explorer).not.toContain('Project capabilities…');
        expect(explorer).not.toContain('Set as primary artifact');
    });

    it('allows project areas to evolve while preserving dependency consistency and existing files', () => {
        expect(capabilities).toContain('enableWithDependencies');
        expect(capabilities).toContain('descriptor?.requires');
        expect(capabilities).toContain('disableWithDependents');
        expect(capabilities).toContain('dependents.get(id)');
        expect(capabilities).toContain('enabledCapabilities:Array.from(capabilities)');
        expect(capabilities).toContain('Existing files are never deleted when an area is disabled');
    });
});
