import { readCssBundle } from '../test-support/readCssBundle';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('guided workflow coverage contract', () => {
    it('keeps guided workflows alongside existing full/visual modes across the main creation surfaces', () => {
        const cases = [
            ['src/event-designer/EventDesigner.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'HydraMultiSelectChips', 'Event architecture']],
            ['src/validation-designer/ValidationRuleEditor.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'ExpressionBuilder']],
            ['src/validation-designer/QuickFixDesigner.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'OperationEditor']],
            ['src/event-designer/flow/EventFlowDesigner.tsx', ['HydraGuidedWorkflow', 'Open diagram', "setView('DIAGRAM')", 'Structure tree']],
            ['src/gamification-designer/GamificationDesigner.tsx', ['HydraGuidedWorkflow', 'Open gamification diagram', "selectView('FORM')", "selectView('STRUCTURE')"]],
            ['src/metamodel-designer/MetamodelDesigner.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'Visual / manual', 'MetamodelCanvas', 'MetamodelPropertyPanel']],
            ['src/metamodel-designer/MetamodelImportDialog.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'Full import form']],
            ['src/metamodel-designer/MetamodelExportDialog.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'Advanced / individual downloads']],
            ['src/update-site-import-center/UpdateSiteImportCenter.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'Advanced workspace']],
            ['src/editor-definition/EditorDefinitionDesigner.tsx', ['HydraGuidedWorkflow', "'GUIDED'", "'DESIGN'", "'STRUCTURE'", "'RUNTIME'"]],
            ['src/editor-definition/EditorRuntimePreview.tsx', ['HydraGuidedWorkflow', 'Direct export', 'DOCKER', 'KUBERNETES']],
            ['src/integration-designer/IntegrationDesigner.tsx', ['HydraGuidedWorkflow', "'GUIDED'", "'FORM'", "'STRUCTURE'"]],
            ['src/transformation-designer/TransformationDesigner.tsx', ['HydraGuidedWorkflow', 'HydraCreationModeSwitch', 'DesignerCommandBar', 'Advanced / manual']],
            ['src/distribution-center/StandaloneDistributionCenter.tsx', ['HydraGuidedWorkflow', 'Full workspace', 'Package ZIP']],
            ['src/publication-center/PublicationCenter.tsx', ['HydraGuidedWorkflow', 'Full workspace', 'Publish release']],
            ['src/representable-import-center/RepresentableImportCenter.tsx', ['HydraGuidedWorkflow', 'Full workspace', 'Persist']],
            ['src/roundtrip-center/RoundTripCenter.tsx', ['HydraGuidedWorkflow', 'Technical details', 'Synchronize with backup']],
            ['src/generated-project-center/GeneratedProjectCenter.tsx', ['HydraGuidedWorkflow', 'Full workspace', 'Preview lifecycle']],
            ['src/compatibility-center/NativeCompatibilityCenter.tsx', ['HydraGuidedWorkflow', 'Full workspace', 'Run selected probes']]
        ] as const;

        for (const [path, markers] of cases) {
            const value = path === 'src/event-designer/EventDesigner.tsx'
                ? source(path) + source('src/event-designer/EventDesignerParts.tsx')
                : path === 'src/event-designer/flow/EventFlowDesigner.tsx'
                    ? source(path) + source('src/event-designer/flow/EventFlowDesignerParts.tsx')
                    : source(path);
            for (const marker of markers) expect(value, `${path} should preserve ${marker}`).toContain(marker);
        }
    });

    it('uses the requested red/green circular completion convention', () => {
        const css = readCssBundle('src/design-system/hydra-design-system.css');
        const theme = source('src/design-system/hydra-theme.css');

        // The component must use semantic palette tokens instead of duplicating literal colors.
        expect(css).toMatch(/\.hydra-guided-status-icon\s*\{[^}]*border-radius:\s*50%/s);
        expect(css).toMatch(/\.hydra-guided-status-icon\.complete\s*\{[^}]*background:\s*var\(--hydra-color-success-600,\s*var\(--hydra-palette-green-700\)\)/s);
        expect(css).toMatch(/\.hydra-guided-status-icon\.error\s*\{[^}]*background:\s*var\(--hydra-color-danger-600,\s*var\(--hydra-palette-red-600\)\)/s);

        // Preserve the requested fallback colors in the single canonical palette.
        expect(theme).toContain('--hydra-palette-green-700: #15803d;');
        expect(theme).toContain('--hydra-palette-red-600: #dc2626;');
    });

    it('keeps event source/subject multi-selection and payload help in the complete form', () => {
        const events = source('src/event-designer/EventDesigner.tsx') + source('src/event-designer/EventDesignerParts.tsx') + source('src/event-designer/EventDataFieldsSection.tsx');
        expect(events).toContain('label="Allowed sources"');
        expect(events).toContain('label="Allowed subjects"');
        expect(events.match(/HydraMultiSelectChips/g)?.length ?? 0).toBeGreaterThanOrEqual(2);
        expect(events).toContain("title={t('About Event data')}");
        expect(events).toContain('allowCustom');
        expect(source('src/design-system/HydraMultiSelectChips.tsx')).toContain('Advanced compatibility: add a custom value');
    });
});
