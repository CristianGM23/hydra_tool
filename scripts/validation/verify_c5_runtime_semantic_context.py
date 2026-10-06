#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, cond):
    checks.append((name,bool(cond)))
    print(('PASS' if cond else 'FAIL')+' - '+name)

canvas=(ROOT/'frontend/src/editor-definition/EditorRuntimeCanvas.tsx').read_text(encoding='utf-8')
session=(ROOT/'frontend/src/editor-definition/EditorRuntimeSession.tsx').read_text(encoding='utf-8')
shell=(ROOT/'frontend/src/application-shell/HydraApplicationShell.tsx').read_text(encoding='utf-8')
registry=(ROOT/'frontend/src/application-shell/moduleRegistry.ts').read_text(encoding='utf-8')
shell_test=(ROOT/'frontend/src/application-shell/HydraApplicationShell.test.tsx').read_text(encoding='utf-8')
readiness=(ROOT/'src/main/java/com/example/editor/workbench/capability/HydraCapabilityReadinessService.java').read_text(encoding='utf-8')
readiness_test=(ROOT/'src/test/java/com/example/editor/workbench/capability/HydraCapabilityReadinessServiceTests.java').read_text(encoding='utf-8')

check('runtime canvas uses bounded local React Flow node state', 'useHydraModelEditorNodeResize(visualNodes' in canvas and 'nodes={flowNodes}' in canvas and 'onNodesChange={onNodesChange}' in canvas)
check('runtime canvas resize callback has stable identity', 'const saveNodePosition=useCallback' in session and 'onNodeDragStop={saveNodePosition}' in session and 'onNodeResizeStop={saveNodePosition}' in session and 'resizeStopRef' not in canvas)
check('runtime canvas click deterministically enters semantic selection', 'const nodeClick=useCallback' in canvas and 'onNodeClick={nodeClick}' in canvas)
check('session selection load is deduplicated by canonical selection', 'selectedIdsRef' in session and 'selectionLoadKeyRef' in session and 'loadEditorSession(result.plan.metamodelId' in session)
check('diagnostic selection preserves problem workflow', "setInspectorTab('PROBLEMS')" in session and ('selectedIdsRef.current=[targetElementId]' in session or ('const nextSelection=[targetElementId]' in session and 'selectedIdsRef.current=nextSelection' in session)))

for module in ('VALIDATIONS','QUICK_FIXES','EVENTS','EVENT_FLOWS','GAMIFICATION'):
    check(f'{module} accepts grammar semantic context', f"'{module}'" in registry)
check('shell loads grammar catalog only for compatible modules', 'listGrammars()' in shell and 'moduleSupportsGrammarContext(activeModule)' in shell)
check('shell exposes grammar-prefixed semantic context options', '`GRAMMAR:${item.id}`' in shell and "'Active semantic context'" in shell and "'Select a metamodel or grammar'" in shell)
check('shell regression covers grammar selection', 'offers grammar-first contexts in portable semantic-layer modules' in shell_test and "toHaveBeenCalledWith('GRAMMAR:orders.dsl')" in shell_test)
check('metamodel-only module regression remains protected', 'keeps metamodel-only modules restricted to real metamodel contexts' in shell_test)
check('grammar-first semantic scope is stable by grammar identity', '? "grammar." + grammar.id()' in readiness and 'GrammarHashing.sources' not in readiness)
check('stable grammar semantic scope has backend regression', 'keepsGrammarFirstSemanticContextStableWhenGrammarSourcesChange' in readiness_test and 'grammar.grammar-orders' in readiness_test)

failed=[name for name,ok in checks if not ok]
print(f'C5_RUNTIME_SEMANTIC_CONTEXT={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'C5_RUNTIME_SEMANTIC_CONTEXT=FAIL {len(failed)}')
if failed:
    for name in failed: print('  - '+name)
    sys.exit(1)
