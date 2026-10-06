import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

const source=(relative:string)=>fs.readFileSync(path.resolve(process.cwd(),relative),'utf8');

describe('transformation designer architecture contract',()=>{
    it('uses the shared guided/manual workflow over one canonical document',()=>{
        const designer=source('src/transformation-designer/TransformationDesigner.tsx');
        expect(designer).toContain('HydraCreationModeSwitch');
        expect(designer).toContain("creationMode === 'GUIDED'");
        expect(designer).toContain('const mappingWorkspace = <TransformationMappingWorkspace document={document}');
        expect(designer).not.toContain('guidedDocument');
        expect(designer).not.toContain('advancedDocument');
    });

    it('keeps the agreed source mapping target workspace and reuses canonical HYDRA editors',()=>{
        const workspace=source('src/transformation-designer/TransformationMappingWorkspace.tsx');
        expect(workspace).toContain('MetamodelStructureTree');
        expect(workspace).toContain('GuidedConditionBuilder');
        expect(workspace).toContain('ExpressionBuilder');
        expect(workspace).toContain('Source metamodel');
        expect(workspace).toContain('Target metamodel');
        expect(workspace).toContain('TransformationBindingRow');
        expect(workspace).toContain('TransformationRelationBindings');
    });

    it('authors M2T and T2M through the same canonical document and exposes side-effect-free preview',()=>{
        const designer=source('src/transformation-designer/TransformationDesigner.tsx');
        const generation=source('src/transformation-designer/TransformationGenerationWorkspace.tsx');
        const text=source('src/transformation-designer/TransformationTextToModelWorkspace.tsx');
        const preview=source('src/transformation-designer/TransformationPreviewPanel.tsx');
        expect(designer).toContain("document.kind === 'M2T'");
        expect(designer).toContain('TransformationGenerationWorkspace');
        expect(designer).toContain('TransformationTextToModelWorkspace');
        expect(generation).toContain('createGenerationRule');
        expect(generation).toContain('GuidedConditionBuilder');
        expect(text).toContain('postTransformationId');
        expect(preview).toContain('previewTransformation');
        expect(preview).toContain('previewModelToText');
        expect(preview).toContain('previewTextToModel');
    });

    it('exports through compatibility-aware adapters instead of embedding a technology in the canonical schema',()=>{
        const panel=source('src/transformation-designer/TransformationExportPanel.tsx');
        const types=source('src/transformation-designer/types.ts');
        expect(panel).toContain('listTransformationExportLanguages');
        expect(panel).toContain('result.compatible');
        expect(panel).toContain('result.fidelity');
        expect(types).toContain("'ATL' | 'ETL' | 'JAVA' | 'XTEND' | 'ACCELEO' | 'EGL'");
        expect(types.slice(0,types.indexOf('TransformationExportLanguage'))).not.toMatch(/ATL|ETL|Acceleo|Xtend/);
    });

    it('is a top-level DSL capability because it selects its own endpoints',()=>{
        const registry=source('src/application-shell/moduleRegistry.ts');
        const capability=source('src/application-shell/HydraCapabilityModule.tsx');
        expect(registry).toContain("id: 'TRANSFORMATIONS'");
        expect(registry).toMatch(/id: 'TRANSFORMATIONS'.*requiresMetamodel: false/);
        expect(capability).toContain("activeModule === 'TRANSFORMATIONS'");
    });

    it('keeps persistent tests coverage and quick fixes inside the same designer review flow',()=>{
        const designer=source('src/transformation-designer/TransformationDesigner.tsx');
        const testing=source('src/transformation-designer/TransformationTestingPanel.tsx');
        const quickFix=source('src/transformation-designer/TransformationQuickFixPanel.tsx');
        const api=source('src/transformation-designer/api.ts');
        expect(designer).toContain('TransformationTestingPanel');
        expect(designer).toContain('TransformationQuickFixPanel');
        expect(testing).toContain('Preview and capture expected');
        expect(testing).toContain('runTransformationTestSuite');
        expect(testing).toContain('ruleCovered');
        expect(testing).toContain('bindingCovered');
        expect(quickFix).toContain('transformationQuickFixes');
        expect(quickFix).toContain('proposedTransformation');
        expect(api).toContain('/tests/run');
        expect(api).toContain('/api/transformations/quick-fixes');
    });


    it('passes loaded endpoint metamodels into the scope instead of reading undeclared variables',()=>{
        const designer=source('src/transformation-designer/TransformationDesigner.tsx');
        expect(designer).toContain('sourceModel={sourceModel}');
        expect(designer).toContain('targetModel={targetModel}');
        expect(designer).toContain('sourceModel: MetaModel | null');
        expect(designer).toContain('targetModel: MetaModel | null');
        expect(designer).toContain('function TransformationScope({ document, sourceId, targetId, sourceModel, targetModel');
    });

});
