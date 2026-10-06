#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
def read(path:str)->str:return (ROOT/path).read_text(encoding='utf-8')
checks=[]
def add(name:str,ok:bool):
    checks.append((name,bool(ok)));print(('PASS' if ok else 'FAIL')+' - '+name)

canvas=read('frontend/src/editor-definition/EditorRuntimeCanvas.tsx')
actions=read('frontend/src/editor-definition/EditorRuntimeSelectionActions.tsx')
layout=read('frontend/src/editor-definition/editorRuntimeLayout.ts')
rep=read('frontend/src/editor-definition/EditorRuntimeRepresentation.tsx')
session=read('frontend/src/editor-definition/EditorRuntimeSession.tsx')
types=read('frontend/src/editor-definition/types.ts')
css1=read('frontend/src/editor-definition/editor-definition-part-1.css')
css2=read('frontend/src/editor-definition/editor-definition-part-2.css')
backend=read('src/main/java/com/example/editor/definition/runtime/session/diagram/EditorSessionDiagramNodeView.java')
assembler=read('src/main/java/com/example/editor/definition/runtime/session/EditorSessionViewAssembler.java')
test=read('frontend/src/editor-definition/EditorRuntimeCanvasContainer.test.tsx')
layout_test=read('frontend/src/editor-definition/editorRuntimeLayout.test.ts')
backend_view_test=read('src/test/java/com/example/editor/definition/runtime/session/EditorModelSessionServiceTests.java')
spanish=read('frontend/src/i18n/currentSpanishCatalogPart16.ts')

add('runtime node no longer renders metaclass name as visible canvas text','<span>{data.className}</span>' not in canvas)
add('runtime node no longer renders representation mapping name as visible canvas text','<small>{data.representationName}</small>' not in canvas)
add('fallback semantic label does not reintroduce the metaclass name','return element.id();' in assembler and '+" · "+element.id()' not in assembler)
add('semantic label is the only primary visible element label','editor-runtime-node-label' in canvas and '{data.label}</strong>' in canvas)
add('semantic labels are hard-bounded by the visual representation','max-width:100%' in css2 and 'max-height:100%' in css2 and 'overflow-wrap:anywhere' in css2 and '.editor-runtime-node-label' in css2)
add('legacy outside label positions are clamped back inside the representation','label-outside_top' in css2 and 'label-outside_bottom' in css2 and 'transform:none' in css2)
add('selection opens a metamodel-style contextual action panel','EditorRuntimeSelectionActions' in canvas and 'editor-runtime-selection-actions' in actions and 'role="dialog"' in actions)
add('contextual selection exposes selected-element code preview',"t('Preview code')" in actions and 'onPreviewCode?.(selectedIds)' in canvas)
add('contextual selection reuses shared align/distribute operations','arrangeRuntimeSelection' in canvas and all(value in actions for value in ['ALIGN_LEFT','ALIGN_TOP','DISTRIBUTE_HORIZONTAL','DISTRIBUTE_VERTICAL']))
add('contextual selection reuses the shared ELK-style engine','elkRuntimeSelection' in canvas and 'layoutHydraElkStyle' in layout and "t('ELK-style selection')" in actions)
add('layout operations preserve semantic containment boundaries','groupSelectedByParent' in layout and 'node.parentId' in layout)
add('multi-node layout persists atomically through one diagram update callback','onDiagramNodesChange' in canvas and 'saveDiagramNodes' in session and 'saveEditorSessionDiagram' in session)
add('global ELK layout also uses the atomic diagram update path','persistVisualNodes' in canvas and 'elkLayoutRuntime' in canvas)
add('runtime diagram node contract persists rotation','rotationDegrees?:number' in types and 'double rotationDegrees' in backend)
add('backend rotation remains backward compatible with old five-argument construction','EditorSessionDiagramNodeView(String elementId, double x, double y, double width, double height)' in backend)
add('backend rotation normalizes arbitrary finite angles','rotationDegrees %= 360' in backend and 'Double.isFinite' in backend)
add('runtime offers common 90/180/270 rotation commands','[90,180,270]' in actions and '>{degrees}°</HydraButton>' in actions)
add('runtime offers validated custom-degree rotation','Custom rotation degrees' in actions and 'Math.abs(parsedRotation)<=3600' in actions)
add('rotation is fitted inside the existing React Flow geometry','rotationFitScale' in canvas and 'rotationFitScale' in layout and 'scale(${fitScale})' in canvas)
add('selection rotation is saved with the canonical runtime diagram','rotateRuntimeDiagramNodes' in canvas and 'onDiagramNodesChange?.(rotateRuntimeDiagramNodes' in canvas)
add('code preview is wired to the existing model-code generation dialog','setCodeGenerationSelectionIds(ids)' in session and 'setCodeGenerationOpen(true)' in session)
add('right inspector tabs use bounded equal-width cells','grid-template-columns:repeat(3,minmax(0,1fr))' in css1 and '.editor-runtime-inspector-tab-label' in css1 and 'editor-runtime-inspector-tab-label' in session)
add('right inspector count badges no longer consume horizontal label width','position:absolute' in css1 and 'right:3px' in css1)
add('canvas regression verifies visible metaclass names are removed','renders only the semantic element label inside the graphical representation' in test and 'fallbackCanvasLabelUsesOnlyTheStableElementId' in backend_view_test)
add('canvas regression verifies contextual code preview and rotation','shows contextual selection actions with code preview and persistent rotation' in test)
add('layout helper regression covers relative rotation and containment-aware arrangement','normalizes persisted rotations and applies relative rotation only to the selection' in layout_test and 'inside each semantic container' in layout_test)
add('new canvas actions are localized in Spanish',all(fragment in spanish for fragment in ['Elementos del modelo seleccionados','Organizar selección con ELK','Rotar selección','Grados personalizados','Disposición visual guardada']))
add('runtime representation forwards contextual canvas contracts','onDiagramNodesChange' in rep and 'onPreviewCode' in rep and 'onDeleteSelection' in rep)

failed=[name for name,ok in checks if not ok]
print(f'V43296_MODEL_EDITOR_CANVAS_ACTIONS={len(checks)-len(failed)}/{len(checks)}')
if failed:sys.exit(1)
