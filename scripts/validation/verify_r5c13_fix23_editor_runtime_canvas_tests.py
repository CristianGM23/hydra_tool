#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (ROOT/path).read_text(encoding='utf-8')
def check(label,condition):
    checks.append((label,bool(condition)))
    print(('PASS' if condition else 'FAIL')+' - '+label)

source=text(Path('frontend/src/editor-definition/EditorRuntimeCanvas.tsx'))
test=text(Path('frontend/src/editor-definition/EditorRuntimeCanvasContainer.test.tsx'))

check('runtime exposes a pure React Flow projection for semantic containment','export function buildEditorRuntimeFlowProjection' in source)
check('runtime projection assigns semantic containment parent ids','containmentParents.set(edge.targetElementId,edge.sourceElementId)' in source and 'parentId' in source)
check('runtime projection constrains children inside visual containers','constrainChildToShape' in source)
check('runtime projection removes only duplicate containment edges','containmentParents.get(edge.targetElementId)!==edge.sourceElementId' in source)
check('selection equality guard is explicit and reusable','export function hasSameRuntimeSelection' in source and 'hasSameRuntimeSelection(selectedIdsRef.current,nextIds)' in source)
check('canvas regression no longer mocks React Flow internals',"vi.mock('@xyflow/react'" not in test)
check('containment tests exercise pure projection','buildEditorRuntimeFlowProjection' in test and "parentId: 'department-1'" in test)
check('edge regression checks retained mentor edge',"['relation-instance:mentor']" in test)
check('project image query uses accessible image name',"getByRole('img',{name:'Department representation'})" in test)
check('project image regression targets active project visual endpoint',"/api/projects/active/visual?path=assets%2Fnotation%2Fdepartment.svg" in test)
check('selection smoke uses React Flow application role',"getByRole('application')" in test)
check('obsolete react-flow test id contract is gone',"getByTestId('react-flow')" not in test)
check('runtime canvas stays within shared logical line budget',max(map(len,source.splitlines()),default=0)<=1400)
check('runtime canvas test stays within shared logical line budget',max(map(len,test.splitlines()),default=0)<=1400)

failed=[label for label,ok in checks if not ok]
print(f'EDITOR_RUNTIME_CANVAS_FIX23={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'EDITOR_RUNTIME_CANVAS_FIX23={len(checks)-len(failed)}/{len(checks)} FAIL')
if failed: raise SystemExit(1)
