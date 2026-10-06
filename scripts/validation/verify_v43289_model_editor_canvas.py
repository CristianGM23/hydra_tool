from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
checks=[]

def add(name,ok): checks.append((name,bool(ok)))
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def compact(rel): return ''.join(text(rel).split())

runtime=text('frontend/src/editor-definition/EditorRuntimeSession.tsx')
css=compact('frontend/src/editor-definition/editor-definition-part-1.css')
flow_css=compact('frontend/src/editor-definition/editor-definition-part-2.css')
metamodel_css=compact('frontend/src/metamodel-designer/metamodel-designer.css')
contract=text('frontend/src/editor-definition/ModelEditorRuntimeWorkspaceContract.test.ts')

add('runtime imports its own editor-definition stylesheet', "import'./editor-definition.css';" in ''.join(runtime.split()))
add('palette starts with metamodel width', 'initialWidth={230}minimumWidth={190}maximumWidth={420}' in ''.join(runtime.split()))
add('inspector starts with metamodel property-panel width', 'initialWidth={330}minimumWidth={260}maximumWidth={480}' in ''.join(runtime.split()))
add('runtime center canvas is the flexible column', 'grid-template-columns:auto6pxminmax(0,1fr)6pxauto;' in css)
add('metamodel reference keeps 230px palette and 330px property panel', 'grid-template-columns:var(--metamodel-palette-width,230px)6pxminmax(0,1fr)330px;' in metamodel_css)
add('configured editor spans the complete model workbench', '.model-editor-workbench>.contextual-model-editor{grid-row:1/-1}' in css)
add('configured editor owns full available height', '.contextual-model-editor{display:flex;min-width:0;min-height:0;width:100%;height:100%;flex:11auto;flex-direction:column;overflow:hidden}' in css)
add('session body grows instead of forcing a viewport minimum', '.contextual-model-editor>.editor-neutral-session{display:flex;min-height:0;height:0;flex:11auto;flex-direction:column}' in css)
add('runtime grid gets the remaining height', '.contextual-model-editor.editor-neutral-session-grid{min-height:0;height:0;flex:11auto;grid-template-rows:minmax(0,1fr);overflow:hidden}' in css)
add('React Flow surface has explicit full width and height', '.editor-runtime-flow>.react-flow{width:100%;height:100%;min-width:0;min-height:0;' in flow_css)
add('wide desktop layouts no longer collapse at 1150px', '@media(max-width:1150px)' not in css)
add('stacking is reserved for genuinely narrow viewports', '@media(max-width:760px){.editor-neutral-session-grid{grid-template-columns:minmax(0,1fr);' in css)
add('narrow side panels stay bounded inside the viewport', '@media(max-width:760px)' in css and 'max-width:100%;' in css)
add('focused canvas geometry regression test exists', 'full model-editor viewport' in contract and 'metamodel-editor proportions' in contract)

for name,ok in checks:
    print(('PASS' if ok else 'FAIL')+' - '+name)
print(f'V43289_MODEL_EDITOR_CANVAS={sum(ok for _,ok in checks)}/{len(checks)}')
raise SystemExit(0 if all(ok for _,ok in checks) else 1)
