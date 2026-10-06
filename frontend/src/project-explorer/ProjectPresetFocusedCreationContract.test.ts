import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { projectPresetGuidance } from './projectPresentation';
import type { ProjectPreset } from './types';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const manager = readFileSync(resolve(here, 'ProjectManagerDialog.tsx'), 'utf8');
const home = readFileSync(resolve(root, 'application-shell/HydraHome.tsx'), 'utf8');
const homeStyles = [
    readFileSync(resolve(root, 'application-shell/application-shell.css'), 'utf8'),
    readFileSync(resolve(root, 'application-shell/application-shell-home-dashboard.css'), 'utf8')
].join('\n');

const presets: ProjectPreset[] = [
    'EMPTY', 'GRAPHICAL_LANGUAGE', 'TEXTUAL_DSL', 'HYBRID_LANGUAGE', 'MODELING_VALIDATION',
    'M2M_TRANSFORMATION', 'M2T_GENERATION', 'T2M_ENGINEERING', 'COMPLETE_EDITOR', 'COMPLETE_MDE'
];

describe('preset-focused project creation and Home guidance', () => {
    it('provides explicit preparation and next-step guidance for every project preset', () => {
        for (const preset of presets) {
            const guidance = projectPresetGuidance(preset);
            expect(guidance.creationTitle.trim()).not.toBe('');
            expect(guidance.summary.trim()).not.toBe('');
            expect(guidance.preparation.length).toBeGreaterThan(0);
            expect(guidance.nextSteps.length).toBeGreaterThan(0);
            expect(guidance.customizationHint.trim()).not.toBe('');
        }
    });

    it('keeps quick-create entry focused on the selected preset until the user asks to change it', () => {
        expect(manager).toContain("useState(initialEntryPoint !== 'HOME_QUICK_PRESET')");
        expect(manager).toContain('PresetFocusCard');
        expect(manager).toContain('Change starting point');
        expect(manager).toContain('PresetGuidancePanel');
        expect(manager).toContain('projectPresetGuidance(preset)');
    });

    it('adds internal help and one shared card geometry across Home card families', () => {
        expect(home).toContain('hydra-home-dashboard-card');
        expect(home).toContain('HydraTooltip');
        expect(home).toContain('projectAreaHelp');
        expect(home).toContain('Information about the main component');
        expect(homeStyles).toContain('.hydra-home-dashboard-card');
        expect(homeStyles).toContain('.hydra-home-card-help');
    });
});
