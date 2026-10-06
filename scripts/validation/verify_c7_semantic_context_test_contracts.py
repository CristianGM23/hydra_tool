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

resolver = text('src/main/java/com/example/editor/metamodel/application/SemanticContextMetamodelResolver.java')
canonical = text('src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java')
resolver_test = text('src/test/java/com/example/editor/metamodel/application/SemanticContextMetamodelResolverTests.java')
canvas_test = text('frontend/src/editor-definition/EditorRuntimeCanvasContainer.test.tsx')
hook = text('frontend/src/designer-ui/useHydraModelEditorNodeResize.ts')
final_gate = text('scripts/validation/verify_final_tree.py')

# The active C6 architecture keeps the transient resolver in metamodel.application.
# The reported compilation break was caused by tests copied from an incompatible
# semantic.* facade branch. Guard the real package/API explicitly.
check('semantic resolver remains in metamodel application package',
      'package com.example.editor.metamodel.application;' in resolver)
check('semantic resolver uses grammar repository architecture',
      'GrammarRepository grammars' in resolver and
      'SemanticContextMetamodelResolver(GrammarRepository grammars, GrammarClassDiagramProjectionService grammarProjection)' in resolver)
check('canonical application owns persisted-first fallback',
      'Optional<StoredMetamodel> stored = repository.findById(id)' in canonical and
      'semanticContextResolver.resolve(id)' in canonical and
      canonical.find('if(stored.isPresent())') < canonical.find('semanticContextResolver.resolve(id)'))
check('incompatible semantic facade source is absent',
      not (ROOT/'src/main/java/com/example/editor/semantic/SemanticContextCanonicalMetamodelApplicationService.java').exists())
legacy_semantic_test = ROOT/'src/test/java/com/example/editor/semantic/SemanticContextMetamodelResolverTests.java'
legacy_semantic_test_duplicate = ROOT/'src/test/test/java/com/example/editor/semantic/SemanticContextMetamodelResolverTests.java'
check('incompatible semantic resolver test is absent', not legacy_semantic_test.exists())
check('nested duplicate incompatible semantic resolver test is absent', not legacy_semantic_test_duplicate.exists())
check('compatibility regression imports no incompatible semantic package',
      'com.example.editor.semantic.' not in resolver_test)

# Mockito regression: project(grammar, null) has two arguments. Either both must
# be matchers or neither. The current test deliberately uses two exact values.
check('grammar projection stubbing uses exact null instead of matcher/raw mix',
      'projection.project(grammar, (com.example.editor.grammar.core.GrammarAnalysisResult)null)' in resolver_test)
check('grammar projection stubbing contains no isNull matcher', 'isNull()' not in resolver_test and 'ArgumentMatchers.isNull' not in resolver_test)
check('grammar transient regression remains covered',
      'resolvesGrammarFirstAsStableTransientMetamodelWithoutPersistingOne' in resolver_test)
check('canonical fallback regression remains covered',
      'canonicalApplicationFallsBackToGrammarProjectionOnlyWhenCanonicalDocumentIsAbsent' in resolver_test)
check('canonical precedence regression remains covered',
      'persistedCanonicalMetamodelWinsEvenInsideReservedPrefix' in resolver_test)
check('no canonical persistence regression remains covered',
      'verify(canonical, never()).create' in resolver_test and 'verify(canonical, never()).replace' in resolver_test)

# Frontend regression from the same report: the canvas test now exercises the
# real XYFlow module instead of replacing it. That is stronger than the former
# partial-mock workaround and guarantees useNodesState/applyNodeChanges remain
# available to the production resize hook.
check('React Flow canvas test keeps the real XYFlow module',
      "vi.mock('@xyflow/react'" not in canvas_test and "vi.doMock('@xyflow/react'" not in canvas_test)
check('React Flow production hook imports useNodesState',
      "import { applyNodeChanges, useNodesState } from '@xyflow/react';" in hook)
check('React Flow production hook consumes applyNodeChanges',
      'applyNodeChanges(effectiveChanges, currentNodes)' in hook)
check('React Flow production hook consumes useNodesState',
      'useNodesState<NodeType>(externalNodes)' in hook)
check('reported regression gate is part of final tree verification',
      'verify_c7_semantic_context_test_contracts.py' in final_gate)

failed = [name for name, ok in checks if not ok]
print(f'C7_REPORTED_TEST_CONTRACTS={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'C7_REPORTED_TEST_CONTRACTS=FAIL {len(failed)}')
if failed:
    for name in failed:
        print('  - ' + name)
    sys.exit(1)
