import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { translateUiText } from '../i18n/translationCatalog';

function source(path:string):string{return readFileSync(resolve(process.cwd(),path),'utf8');}

describe('selected model code generation contract',()=>{
    it('keeps full-model generation separate from selection preview in the configurable runtime',()=>{
        const session=source('src/editor-definition/EditorRuntimeSession.tsx');
        expect(session).toContain('setCodeGenerationSelectionIds(selectedIds)');
        expect(session).toContain('setCodeGenerationSelectionIds([])');
        expect(session).toContain('selectionElementIds={codeGenerationSelectionIds}');
    });

    it('supports rectangle and modifier multi-selection and one bulk deletion in both model editors',()=>{
        const runtime=source('src/editor-definition/EditorRuntimeCanvas.tsx');
        const classic=source('src/model-editor/ModelEditorWorkspace.tsx');
        const api=source('src/api/editorApi.ts');
        expect(runtime).toContain('onSelectionChange');
        expect(classic).toContain('onSelectionChange={selectionChange}');
        expect(classic).toContain('deleteSelectedElements(selectedElementIds)');
        expect(api).toContain('/elements/delete');
    });

    it('previews formatted textual source without duplicating generation logic',()=>{
        const dialog=source('src/model-codegen/ModelCodeGenerationDialog.tsx');
        const api=source('src/model-codegen/api.ts');
        expect(dialog).toContain('formattedPreview');
        expect(dialog).toContain('<pre><code>');
        expect(api).toContain('/preview');
        expect(dialog).toContain("t('Language / target format')");
    });

    it('keeps label font size configurable for nodes and edges',()=>{
        const designer=source('src/editor-definition/EditorDefinitionDesignerParts.tsx');
        expect(designer.match(/label="Label font size"/g)?.length).toBeGreaterThanOrEqual(2);
        expect(translateUiText('Label font size','es')).toBe('Tamaño de fuente de la etiqueta');
    });
});
