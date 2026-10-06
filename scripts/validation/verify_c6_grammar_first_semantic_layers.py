#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (ROOT/path).read_text(encoding='utf-8')
def check(name, cond):
    ok=bool(cond); checks.append((name,ok)); print(('PASS' if ok else 'FAIL')+' - '+name)

resolver=text(Path('src/main/java/com/example/editor/metamodel/application/SemanticContextMetamodelResolver.java'))
canonical=text(Path('src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java'))
readiness=text(Path('src/main/java/com/example/editor/workbench/capability/HydraCapabilityReadinessService.java'))
workbench=text(Path('frontend/src/application-shell/IndependentCapabilityWorkbench.tsx'))
shell=text(Path('frontend/src/application-shell/HydraApplicationShell.tsx'))
registry=text(Path('frontend/src/application-shell/moduleRegistry.ts'))
audit_test=text(Path('frontend/src/audit-log/ActivityCaptureContract.test.ts'))
resolver_test=text(Path('src/test/java/com/example/editor/metamodel/application/SemanticContextMetamodelResolverTests.java'))
final_gate=text(Path('scripts/validation/verify_final_tree.py'))

check('semantic context resolver exists', 'class SemanticContextMetamodelResolver' in resolver)
check('grammar semantic prefix is stable', 'GRAMMAR_PREFIX = "grammar."' in resolver)
check('resolver reads grammar repository', 'grammars.findById(grammarId)' in resolver)
check('resolver projects grammar in memory', 'grammarProjection.project' in resolver)
check('resolver keeps stable semantic id', 'new MetaModel(inferred.schemaVersion(), semanticContextId' in resolver)
check('resolver preserves inferred structure', 'inferred.classes(), inferred.enums(), inferred.dataTypes(), inferred.relations()' in resolver)
check('resolver marks grammar context', 'hydra.semanticContext.kind' in resolver and 'GRAMMAR' in resolver)
check('resolver marks projection transient', 'hydra.semanticContext.transient' in resolver and 'true' in resolver)
check('resolver records grammar identity', 'hydra.grammar.id' in resolver)
check('resolver does not persist canonical metamodel', '.create(' not in resolver and '.replace(' not in resolver and '.delete(' not in resolver)
check('canonical service tries persisted source first', 'Optional<StoredMetamodel> stored = repository.findById(id)' in canonical)
check('canonical source wins before transient fallback', canonical.find('if(stored.isPresent())') < canonical.find('semanticContextResolver.resolve(id)'))
check('canonical service resolves transient semantic context', 'semanticContextResolver.resolve(id)' in canonical)
check('legacy canonical constructors remain compatible', 'Optional.empty()' in canonical and 'CanonicalMetamodelApplicationService(CanonicalMetamodelRepository repository, NeutralMetamodelValidator validator)' in canonical)
check('grammar readiness scope is identity based', '"grammar." + grammar.id()' in readiness)
check('grammar readiness does not hash source into scope', 'GrammarHashing.sources' not in readiness)
check('frontend loads grammar readiness', "loadCapabilityReadiness('GRAMMAR', grammarId)" in workbench)
check('frontend loads grammar structural projection', 'projectGrammarClassDiagram(loaded.grammar, 0)' in workbench)
check('frontend replaces inferred id with stable semantic scope', 'id: readiness.semanticContextId' in workbench)
check('validation designer receives semantic projection', "moduleId === 'VALIDATIONS'" in workbench and 'metamodel={metamodel}' in workbench)
check('quick fix designer receives semantic projection', "moduleId === 'QUICK_FIXES'" in workbench)
check('event designer receives semantic projection', "moduleId === 'EVENTS'" in workbench and 'metamodelId={metamodel.id}' in workbench)
check('event flow designer receives semantic projection', "moduleId === 'EVENT_FLOWS'" in workbench)
check('gamification designer receives semantic projection', "moduleId === 'GAMIFICATION'" in workbench and 'metamodelId={metamodel.id}' in workbench)
check('editor designer receives semantic projection', "moduleId === 'EDITORS'" in workbench)
check('integration designer receives semantic context id', "moduleId === 'INTEGRATIONS'" in workbench and 'metamodelId={metamodel.id}' in workbench)
for module in ('VALIDATIONS','QUICK_FIXES','EVENTS','EVENT_FLOWS','GAMIFICATION','EDITORS','INTEGRATIONS'):
    check(f'{module} selector supports grammar context', f"'{module}'" in registry)
check('shell emits grammar audit activity', "'navigation.grammar.changed':'navigation.metamodel.changed'" in shell)
check('shell audit records semanticContextId', 'semanticContextId' in shell)
check('audit contract accepts metamodel and grammar navigation', "'navigation.grammar.changed':'navigation.metamodel.changed'" in audit_test and 'semanticContextId' in audit_test)
check('resolver regression verifies transient semantics', 'resolvesGrammarFirstAsStableTransientMetamodelWithoutPersistingOne' in resolver_test)
check('resolver regression verifies no canonical persistence', 'never()).create' not in resolver_test if False else 'verify(canonical, never()).create' in resolver_test)
check('resolver regression verifies canonical precedence', 'persistedCanonicalMetamodelWinsEvenInsideReservedPrefix' in resolver_test)
check('C6 gate is part of final tree verification', 'verify_c6_grammar_first_semantic_layers.py' in final_gate)

failed=[name for name,ok in checks if not ok]
print(f'C6_GRAMMAR_FIRST_SEMANTIC_LAYERS={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'C6_GRAMMAR_FIRST_SEMANTIC_LAYERS=FAIL {len(failed)}')
if failed:
    for name in failed: print('  - '+name)
    sys.exit(1)
