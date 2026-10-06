#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks = []

def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding='utf-8')

def check(name: str, condition: bool) -> None:
    ok = bool(condition)
    checks.append((name, ok))
    print(('PASS' if ok else 'FAIL') + ' - ' + name)

hook = text('frontend/src/designer-ui/useHydraModelEditorNodeResize.ts')
hook_test = text('frontend/src/designer-ui/useHydraModelEditorNodeResize.test.tsx')
canvas = text('frontend/src/editor-definition/EditorRuntimeCanvas.tsx')
session = text('frontend/src/editor-definition/EditorRuntimeSession.tsx')
session_test = text('frontend/src/editor-definition/EditorRuntimeSession.test.tsx')
final_gate = text('scripts/validation/verify_final_tree.py')

check('external node reconciliation has semantic equality guard',
      'hasSameHydraExternalNodeState' in hook and 'return hasSameHydraExternalNodeState(reconciled, local) ? local : reconciled;' in hook)
check('external sync skips setNodes for identical reconciled references',
      'if (hasSameHydraNodeReferences(reconciled, nodesRef.current)) return;' in hook)
check('React Flow changes are filtered before applyNodeChanges',
      'effectiveChanges = changes.filter' in hook and 'isHydraNoOpNodeChange' in hook)
check('repeated selection frames are explicitly no-op',
      "if (change.type === 'select') return Boolean(node.selected) === change.selected;" in hook)
check('repeated measurement frames are explicitly no-op',
      "if (change.type === 'dimensions')" in hook and 'sameDimensions' in hook and 'sameResizing' in hook)
check('canvas selection uses refs instead of selectedIds callback dependency',
      'const selectedIdsRef=useRef(selectedIds);' in canvas and 'selectionChangeRef.current?.(nextIds);' in canvas)
check('React Flow selection callback is stable',
      'const selectionChange=useCallback<OnSelectionChangeFunc>' in canvas and '},[]);' in canvas[canvas.find('const selectionChange=useCallback<OnSelectionChangeFunc>'):canvas.find('const selectedSet=useMemo')])
check('node click selection callback is stable',
      'const nodeClick=useCallback' in canvas and 'const current=selectedIdsRef.current;' in canvas)
check('diagram reconciliation preserves identity when projection did not change',
      'if(current.representationId===next.representationId&&sameNodes&&sameEdges)return current;' in session)
check('diagnostic programmatic selection is idempotent',
      'setSelectedIds(current=>sameStringList(current,nextSelection)?current:nextSelection)' in session)
check('selection rematerialization regression is covered',
      'does not replace local node state when the parent rematerializes an already-applied selection' in hook_test)
check('repeated selection/measurement regression is covered',
      'treats repeated selection and measurement frames as no-op React Flow changes' in hook_test)
check('reported quick-fix session regression remains covered',
      'shows validation diagnostics and offers safe quick fixes inside the model editor' in session_test)
check('reported operation session regression remains covered',
      'collects operation parameters before executing a compiled operation tool' in session_test)
check('selection-loop gate is part of final tree verification',
      'verify_c8_model_editor_selection_loop.py' in final_gate)

failed = [name for name, ok in checks if not ok]
print(f'C8_MODEL_EDITOR_SELECTION_LOOP={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'C8_MODEL_EDITOR_SELECTION_LOOP=FAIL {len(failed)}')
if failed:
    for name in failed:
        print('  - ' + name)
    sys.exit(1)
