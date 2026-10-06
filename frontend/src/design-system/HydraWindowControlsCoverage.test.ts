import { readCssBundle } from '../test-support/readCssBundle';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

const dialogSources = [
    'src/design-system/HydraModal.tsx',
    'src/metamodel-designer/MetamodelImportDialog.tsx',
    'src/metamodel-designer/MetamodelExportDialog.tsx',
    'src/metamodel-designer/MetamodelSelectionActionsDialog.tsx',
    'src/validation-designer/ValidationFieldHelp.tsx',
    'src/project-explorer/ProjectManagerDialog.tsx',
    'src/editor-definition/ProjectAssetPicker.tsx',
    'src/layer-io/LayerInterchangeDialog.tsx'
] as const;

const embeddedFormSources = [
    'src/validation-designer/ValidationDesigner.tsx',
    'src/event-designer/EventDesigner.tsx',
    'src/event-designer/flow/EventFlowDesigner.tsx',
    'src/gamification-designer/GamificationDesigner.tsx',
    'src/integration-designer/IntegrationDesigner.tsx',
    'src/editor-definition/EditorDefinitionDesigner.tsx',
    'src/editor-definition/EditorRuntimeSession.tsx'
] as const;

describe('HYDRA window-control coverage', () => {
    it('uses the shared upper-right window controls in every dialog that exposes an X close action', () => {
        for(const file of dialogSources) {
            expect(source(file), file).toContain('HydraWindowControls');
        }
    });

    it('uses the same square close treatment in embedded property and runtime forms', () => {
        for(const file of embeddedFormSources) {
            const content = source(file);
            expect(content.includes('hydra-form-close-button') || content.includes('hydra-window-close'), file).toBe(true);
        }
    });

    it('keeps the window controls square and the close state red', () => {
        const css = readCssBundle('src/design-system/hydra-design-system.css');
        expect(css).toContain('--hydra-window-control-size:36px');
        expect(css).toContain('border-radius:0 !important');
        expect(css).toContain('--hydra-window-close:#e81123');
        expect(css).toContain('--hydra-window-close-active:#c42b1c');
        expect(css).toContain('inset-block-start:0');
        expect(css).toContain('inset-inline-end:0');
    });
});
