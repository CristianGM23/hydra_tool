import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const source = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8');
const compact = (value: string) => value.replace(/\s+/g, '');

describe('grammar designer completion contract', () => {
    it('owns vertical scrolling and adapts to the actual Project Explorer width', () => {
        const css = compact(source('src/grammar-designer/grammar-designer.css'));
        const explorer = compact(source('src/project-explorer/project-explorer.css'));
        expect(css).toContain('.grammar-designer{display:flex;flex-direction:column;gap:12px;width:100%;height:100%;min-width:0;min-height:0;');
        expect(css).toContain('overflow-y:auto');
        expect(css).toContain('overflow-x:hidden');
        expect(css).toContain('scrollbar-gutter:stable');
        expect(css).toContain('container-type:inline-size');
        expect(css).toContain('@container(max-width:1450px)');
        expect(css).toContain('@container(max-width:860px)');
        expect(explorer).toContain('.project-editor-content>.workspace,.project-editor-content>.metamodel-visual-designer,.project-editor-content>.grammar-designer{width:100%;height:100%;min-height:0;flex:11auto;}');
    });

    it('keeps inferred class nodes draggable and resizable through the canonical metamodel renderer', () => {
        const diagram = source('src/grammar-designer/GrammarClassDiagram.tsx');
        const shared = source('src/metamodel-designer/MetamodelReadOnlyCanvas.tsx');
        const classNode = source('src/metamodel-designer/MetamodelClassNode.tsx');
        expect(diagram).toContain('MetamodelReadOnlyCanvas');
        expect(diagram).toContain('allowNodeResize');
        expect(shared).toContain('nodesDraggable');
        expect(shared).toContain('allowNodeResize ? changes');
        expect(shared).toContain('resizeEnabled: allowNodeResize');
        expect(classNode).toContain('HydraNodeResizer');
        expect(classNode).toContain('data.resizeEnabled !== false');
        expect(shared).not.toContain('nodesDraggable={false}');
    });

    it('shows source, inferred diagram, inferred metamodel and generated code views', () => {
        const designer = source('src/grammar-designer/GrammarDesigner.tsx');
        expect(designer).toContain("'SOURCE'");
        expect(designer).toContain("'DIAGRAM'");
        expect(designer).toContain("'METAMODEL'");
        expect(designer).toContain("'GENERATED_CODE'");
        expect(designer).toContain('<GrammarInferredMetamodelView');
        expect(designer).toContain('<GrammarGeneratedCodeView');
    });


    it('refreshes inferred visualizations whenever either visual view is opened and keeps entry-rule information out of the editor feedback', () => {
        const designer = source('src/grammar-designer/GrammarDesigner.tsx');
        expect(designer).toContain("if(workspaceView!=='DIAGRAM'&&workspaceView!=='METAMODEL')return");
        expect(designer).toContain("const expectedSourceHash=currentAnalysis?.sourceHash??''");
        expect(designer).toContain('refreshDraftProjection(true,expectedSourceHash)');
        expect(designer).not.toContain("if(classDiagram!==null&&classDiagram.draftRevision===revision)return;\n        void refreshDraftProjection(true)");
        expect(designer).toContain("visibleDiagnostics.filter(item=>item.code!=='GRAMMAR_EFFECTIVE_ENTRY_RULE')");
        expect(designer).toContain('diagnostics={editorDiagnostics.filter');
    });


    it('uses a unique analysis request for each inferred projection so concurrent diagram/tree refreshes cannot cancel each other', () => {
        const api = source('src/grammar-designer/api.ts');
        expect(api).toContain("requestId:`grammar-diagram:${revision}:${unique}`");
        expect(api).toContain('crypto.randomUUID');
        expect(api).not.toContain('requestId:`grammar-diagram:${revision}`,');
    });

    it('uses immediate local entry candidates while retaining backend analysis authority', () => {
        const designer = source('src/grammar-designer/GrammarDesigner.tsx');
        const identity = source('src/grammar-designer/GrammarIdentityForm.tsx');
        expect(designer).toContain('inspectGrammarLocally(model)');
        expect(identity).toContain('localInspection');
        expect(identity).toContain("'hydra.entryRule.mode'");
        expect(identity).toContain("__HYDRA_AUTOMATIC_ENTRY_RULE__");
    });

    it('never replaces non-empty grammar sources directly when technology changes', () => {
        const model = source('src/grammar-designer/grammarDesignerModel.ts');
        const designer = source('src/grammar-designer/GrammarDesigner.tsx');
        expect(model).toContain('safe conversion preview');
        expect(model).toContain('direct technology replacement is not allowed');
        expect(designer).toContain('prepareGrammarPlatformConversion(model,technology)');
        expect(designer).toContain('preview.semanticallyEquivalent');
        expect(designer).toContain('<GrammarPlatformConversionDialog');
        expect(designer).toContain("model.documents.every(document=>document.content.trim()==='')");
        expect(model).toContain("model.documents.some(document=>document.content.trim()!=='')");
    });


    it('restores and lets the user reopen persisted grammars after changing workbench layers', () => {
        const designer = source('src/grammar-designer/GrammarDesigner.tsx');
        expect(designer).toContain('LAST_GRAMMAR_STORAGE_KEY');
        expect(designer).toContain('loadGrammar(initial.id)');
        expect(designer).toContain('loadActiveGrammarCompilation(initial.id)');
        expect(designer).toContain('async function openGrammar(grammarId:string)');
        expect(designer).toContain("t('Saved grammars')");
        expect(designer).toContain('persisted.map(item=><option');
        expect(designer).toContain('onClick={newGrammar}');
    });

    it('keeps conversion cancel non-mutating and Apply fail-closed', () => {
        const designer = source('src/grammar-designer/GrammarDesigner.tsx');
        const dialog = source('src/grammar-designer/GrammarPlatformConversionDialog.tsx');
        expect(dialog).toContain('disabled={!preview.semanticallyEquivalent}');
        expect(dialog).toContain("t('Keep current grammar')");
        expect(dialog).toContain("t('Apply converted grammar')");
        expect(dialog).toContain("t('Download transformation report')");
        expect(designer).toContain('if(conversionPreview===null||!conversionPreview.semanticallyEquivalent)return');
        const cancelHandler = designer.slice(designer.indexOf('onCancel={()=>{'), designer.indexOf('}} onApply={applyConversion}'));
        expect(cancelHandler).toContain('setConversionPreview(null)');
        expect(cancelHandler).toContain('setRequestedTechnology(null)');
        expect(cancelHandler).toContain("setMessage(t('Conversion cancelled. The current grammar was not changed.'))");
        expect(cancelHandler).not.toContain('mutate(');
        expect(cancelHandler).not.toContain('setModel(');
    });
});

describe('R8 diagnostics and textual customization contract',()=>{
    it('merges local, backend and operation diagnostics instead of replacing local results',()=>{
        const designer=source('src/grammar-designer/GrammarDesigner.tsx');
        expect(designer).toContain('...localFeedback.diagnostics');
        expect(designer).toContain('buildLocalGrammarFeedback(localInspection.diagnostics,localInspection.symbols)');
        expect(designer).toContain('...(currentAnalysis?.diagnostics??[])');
        expect(designer).toContain('mergeGrammarDiagnostics');
        expect(designer).toContain("const blocking=baseDiagnostics.filter(item=>item.severity==='ERROR')");
    });

    it('uses the same effective textual customization in preview and the real textual model editor',()=>{
        const customization=source('src/grammar-designer/GrammarTextualCustomization.tsx');
        const editor=source('src/textual-model-editor/TextualModelEditor.tsx');
        const model=source('src/grammar-designer/textualCustomization.ts');
        expect(model).toContain('effectiveTextualCustomization');
        expect(model).toContain('...persisted.keywords,...inferKeywords(model)');
        expect(customization).toContain('effectiveTextualCustomization(model)');
        expect(editor).toContain('effectiveTextualCustomization(grammarModel)');
    });
});

describe('R5C13 Fix 9 visual parity contract',()=>{
    it('reuses the canonical Metamodel Designer renderer and keeps every diagram export',()=>{
        const diagram=source('src/grammar-designer/GrammarClassDiagram.tsx');
        const shared=source('src/metamodel-designer/MetamodelReadOnlyCanvas.tsx');
        const relation=source('src/metamodel-designer/MetamodelRelationLayer.tsx');
        const exporter=source('src/grammar-designer/grammarClassDiagramExport.ts');
        expect(diagram).toContain('MetamodelReadOnlyCanvas');
        expect(shared).toContain('MetamodelClassNode');
        expect(shared).toContain('buildMetamodelEdges');
        expect(shared).toContain('MetamodelRelationLayer');
        expect(shared).toContain('nodesDraggable');
        expect(shared).toContain('setNodes(current => markProjectionNodes(buildMetamodelNodes(model, diagram, current), allowNodeResize))');
        expect(relation).toContain('metamodel-containment-diamond');
        expect(relation).toContain('metamodel-aggregation-diamond');
        expect(relation).toContain('metamodel-inheritance-triangle');
        expect(diagram).toContain("['png','svg','jpeg','drawio','plantuml']");
        expect(exporter).toContain('buildGrammarPlantUml');
        expect(exporter).toContain('buildGrammarDrawio');
        expect(exporter).toContain('buildGrammarDiagramSvg');
    });
    it('keeps Monaco diagnostics and customization on modern decoration collections',()=>{
        const monaco=source('src/grammar-designer/GrammarMonacoEditor.tsx');
        const css=source('src/grammar-designer/grammar-designer-responsive.css');
        expect(monaco).toContain('createDecorationsCollection');
        expect(monaco).toContain('diagnosticDecorationCollection.current.set(next)');
        expect(monaco).toContain('syntaxDecorationCollection.current.set(next)');
        expect(monaco).toContain('lineNumberClassName:`hydra-monaco-line-number-${kind}`');
        expect(monaco).toContain('registerCodeActionProvider');
        expect(monaco).toContain('registerDefinitionProvider');
        expect(monaco).toContain('registerReferenceProvider');
        expect(monaco).toContain("item.code==='GRAMMAR_UNUSED_RULE'?'unused'");
        expect(css).toContain('.hydra-monaco-line-number-error');
        expect(css).toContain('.hydra-monaco-line-number-unused');
        expect(css).toContain('.hydra-monaco-glyph-warning');
    });
});

