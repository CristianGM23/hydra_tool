from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
canvas = (ROOT / 'frontend/src/editor-definition/EditorRuntimeCanvas.tsx').read_text(encoding='utf-8')
session = (ROOT / 'frontend/src/editor-definition/EditorRuntimeSession.tsx').read_text(encoding='utf-8')
test = (ROOT / 'frontend/src/editor-definition/EditorRuntimeCanvasContainer.test.tsx').read_text(encoding='utf-8')

checks = [
    ('canvas keeps NodeResizer functionality', 'NodeResizer' in canvas and 'onResizeEnd=' in canvas),
    ('canvas wires resize persistence to the public callback', 'onResizeStop:onNodeResizeStop' in canvas),
    ('canvas selection callback is memoized', 'useCallback<OnSelectionChangeFunc>' in canvas),
    ('canvas suppresses unchanged controlled selection feedback', 'if(sameStringList(selectedIds,nextIds))return;' in canvas),
    ('session selection reducer is memoized', 'const selectNodes = useCallback' in session),
    ('session avoids replacing identical selectedIds', 'setSelectedIds(current=>sameStringList(current,unique)?current:unique);' in session),
    ('session avoids replacing an identical generated-form draft', 'setDraft(current=>sameRecord(current,nextDraft)?current:nextDraft);' in session),
    ('container test keeps the React Flow NodeResizer double', 'NodeResizer: () => null' in test),
    ('container fixture supplies the resize persistence callback', 'onNodeResizeStop={vi.fn()}' in test),
    ('regression test simulates React Flow selection notifications', 'onSelectionChange?.({nodes:nodes.filter(node=>Boolean(node.selected)),edges:[]});' in test),
    ('regression harness owns controlled selection state', 'function SelectionFeedbackHarness()' in test and 'onSelectionChange={setSelectedIds}' in test),
    ('regression test guards maximum-depth feedback', "does not feed an unchanged controlled selection back into the session state" in test),
]

passed = 0
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
    passed += int(ok)
print(f'V43292_MODEL_EDITOR_SELECTION_FEEDBACK={passed}/{len(checks)}')
if passed != len(checks):
    sys.exit(1)
