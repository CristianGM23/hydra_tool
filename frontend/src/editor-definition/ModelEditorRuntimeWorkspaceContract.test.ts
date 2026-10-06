import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { translateUiText } from '../i18n/translationCatalog';

function source(path:string):string{return readFileSync(resolve(process.cwd(),path),'utf8');}
function compact(path:string):string{return source(path).replace(/\s+/g,'');}

describe('model editor runtime workspace contract',()=>{
    it('uses the metamodel-editor proportions and keeps the canvas in the flexible centre column',()=>{
        const component=source('src/editor-definition/EditorRuntimeSession.tsx');
        const css=compact('src/editor-definition/editor-definition-part-1.css');
        expect(component).toContain("import './editor-definition.css';");
        expect(component).toContain('HydraResizablePanel');
        expect(component).toContain('hydra-model-editor-palette-width');
        expect(component).toContain('initialWidth={230} minimumWidth={190} maximumWidth={420}');
        expect(component).toContain('hydra-model-editor-inspector-width');
        expect(component).toContain('initialWidth={330} minimumWidth={260} maximumWidth={480}');
        expect(css).toContain('grid-template-columns:auto6pxminmax(0,1fr)6pxauto;');
        expect(css).toContain('.editor-neutral-session-grid>.hydra-ui-panel-resizer{width:6px;min-width:6px;flex-basis:6px;');
    });

    it('gives the configured runtime the full model-editor viewport instead of collapsing the React Flow canvas',()=>{
        const css=compact('src/editor-definition/editor-definition-part-1.css');
        const flowCss=compact('src/editor-definition/editor-definition-part-2.css');
        expect(css).toContain('.model-editor-workbench>.contextual-model-editor{grid-row:1/-1}');
        expect(css).toContain('.contextual-model-editor{display:flex;min-width:0;min-height:0;width:100%;height:100%;flex:11auto;flex-direction:column;overflow:hidden}');
        expect(css).toContain('.contextual-model-editor>.editor-neutral-session{display:flex;min-height:0;height:0;flex:11auto;flex-direction:column}');
        expect(css).toContain('.contextual-model-editor.editor-neutral-session-grid{min-height:0;height:0;flex:11auto;grid-template-rows:minmax(0,1fr);overflow:hidden}');
        expect(css).not.toContain('@media(max-width:1150px){.editor-neutral-session-grid{grid-template-columns:minmax(0,1fr);');
        expect(css).toContain('@media(max-width:760px){.editor-neutral-session-grid{grid-template-columns:minmax(0,1fr);');
        expect(flowCss).toContain('.editor-runtime-flow>.react-flow{width:100%;height:100%;min-width:0;min-height:0;');
    });

    it('restores properties operations and problems as accessible inspector tabs',()=>{
        const component=source('src/editor-definition/EditorRuntimeSession.tsx');
        expect(component).toContain('editor-runtime-inspector-tabs');
        expect(component).toContain('role="tablist"');
        for(const tab of ["'PROPERTIES'","'OPERATIONS'","'PROBLEMS'"]) expect(component).toContain(tab);
        expect(component).toContain("t('Properties')");
        expect(component).toContain("t('Operations')");
        expect(component).toContain("t('Problems')");
    });

    it('persists manual node resizing in the representation layout rather than the semantic model',()=>{
        const canvas=source('src/editor-definition/EditorRuntimeCanvas.tsx');
        const session=source('src/editor-definition/EditorRuntimeSession.tsx');
        expect(canvas).toContain('NodeResizer');
        expect(canvas).toContain('onResizeEnd');
        expect(canvas).toContain('onNodeResizeStop');
        expect(session).toContain('const saveNodePosition=useCallback');
        expect(session).toContain('onNodeDragStop={saveNodePosition}');
        expect(session).toContain('onNodeResizeStop={saveNodePosition}');
        expect(session).not.toContain('onNodeResizeStop={(...args)=>void saveNodePosition(...args)}');
        expect(session).toContain('saveEditorSessionDiagram');
    });

    it('keeps the new inspector controls translated in Spanish',()=>{
        const translations:Readonly<Record<string,string>>={
            'Resize model editor palette':'Redimensionar la paleta del editor de modelos',
            'Resize model editor inspector':'Redimensionar el inspector del editor de modelos',
            'Model element inspector':'Inspector del elemento del modelo',
            'Run operation':'Ejecutar operación'
        };
        for(const [english,spanish] of Object.entries(translations))expect(translateUiText(english,'es')).toBe(spanish);
    });
});
