#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (ROOT/path).read_text(encoding='utf-8')
def check(name, cond):
    ok=bool(cond); checks.append((name,ok)); print(('PASS' if ok else 'FAIL')+' - '+name)

vs=text(Path('src/main/java/com/example/editor/integration/adapter/vscode/VsCodeExtensionTechnologyAdapter.java'))
ec=text(Path('src/main/java/com/example/editor/integration/adapter/eclipse/EclipseModelingDistributionTechnologyAdapter.java'))
compiler=text(Path('src/main/java/com/example/editor/integration/compiler/TechnologyCompilerService.java'))
fixtures=text(Path('src/test/java/com/example/editor/integration/compiler/TechnologyAdapterTestFixtures.java'))
vs_test=text(Path('src/test/java/com/example/editor/integration/adapter/vscode/VsCodeExtensionTechnologyAdapterTests.java'))
ec_test=text(Path('src/test/java/com/example/editor/integration/adapter/eclipse/EclipseModelingDistributionTechnologyAdapterTests.java'))
xtext_test=text(Path('src/test/java/com/example/editor/integration/adapter/xtext/XtextTechnologyAdapterTests.java'))
langium_test=text(Path('src/test/java/com/example/editor/integration/adapter/langium/LangiumTechnologyAdapterTests.java'))
audit=text(Path('src/main/java/com/example/editor/audit/CoreAuditActivityDescriptorProvider.java'))
audit_test=text(Path('src/test/java/com/example/editor/audit/AuditComprehensiveCaptureTests.java'))
final_gate=text(Path('scripts/validation/verify_final_tree.py'))
doc=(ROOT/'docs/GRAMMAR_FULL_STACK_RUNTIME_MATRIX.md')

# VS Code: 7
check('VS Code declares grammar semantic root', 'Set.of(IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.GRAMMAR)' in vs)
check('VS Code no longer requires physical metamodel artifact', 'request.require(IntegrationArtifactKind.METAMODEL)' not in vs)
check('VS Code resolves optional metamodel', 'request.find(IntegrationArtifactKind.METAMODEL)' in vs)
check('VS Code resolves grammar root', 'TechnologyAdapterTextualCustomizationSupport.grammar(request, mapper)' in vs)
check('VS Code errors only when both roots are absent', 'metamodel == null && grammar == null' in vs)
check('VS Code README exposes semantic context', 'scoped to semantic context `%s`' in vs)
check('VS Code has grammar-only regression', 'acceptsGrammarAsAutonomousSemanticRoot' in vs_test and 'grammar.main-language' in vs_test)

# Eclipse: 8
check('Eclipse declares grammar semantic root', 'Set.of(IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.GRAMMAR)' in ec)
check('Eclipse no longer requires physical metamodel artifact', 'request.require(IntegrationArtifactKind.METAMODEL)' not in ec)
check('Eclipse resolves optional metamodel', 'request.find(IntegrationArtifactKind.METAMODEL)' in ec)
check('Eclipse resolves grammar root', 'TechnologyAdapterTextualCustomizationSupport.grammar(request, mapper)' in ec)
check('Eclipse errors only when both roots are absent', 'metamodel == null && grammar == null' in ec)
check('Eclipse emits Ecore only when a real metamodel exists', 'if(metamodel != null)' in ec and 'ecoreAdapter.toBytes(metamodel)' in ec)
check('Eclipse README exposes semantic context', 'feature-based Eclipse product for semantic context `%s`' in ec)
check('Eclipse has grammar-only regression without synthetic Ecore', 'acceptsGrammarAsAutonomousSemanticRootWithoutSyntheticEcore' in ec_test and 'noneMatch(p->p.endsWith(".ecore"))' in ec_test)

# Compiler/fixtures: 4
check('compiler accepts any adapter-declared semantic root', '!acceptedRoots.contains(artifact.kind())' in compiler)
check('grammar-first fixture and Xtext/Langium regressions use reserved grammar prefix', '"grammar."+grammar.id()' in fixtures and 'grammar.main-language' in xtext_test and 'grammar.main-language' in langium_test)
check('VS Code grammar-first fixture is available', 'vscodeGrammarFirstRequest' in fixtures)
check('Eclipse grammar-first fixture is available', 'eclipseGrammarFirstRequest' in fixtures)

# Audit: 6
check('grammar create/update/delete audit descriptors exist', all(v in audit for v in ['grammar.definition.created','grammar.definition.updated','grammar.definition.deleted']))
check('syntax-style create/update/delete audit descriptors exist', all(v in audit for v in ['textual.syntax.style.created','textual.syntax.style.updated','textual.syntax.style.deleted']))
check('textual context create/update/delete audit descriptors exist', all(v in audit for v in ['textual.editor.definition.created','textual.editor.definition.updated','textual.editor.definition.deleted']))
check('grammar audit descriptors are bilingual', 'Se modificó la gramática' in audit and 'Updated grammar' in audit)
check('textual audit modules have Spanish labels', 'case "grammar" -> "Gramática"' in audit and 'case "textual" -> "Editor textual"' in audit)
check('audit regression asserts all nine semantic authoring mutations', 'grammarStyleAndContextMutationsHaveExplicitBilingualDescriptors' in audit_test)

# Documentation/final gate: 2
check('full-stack grammar runtime matrix is documented', doc.is_file() and 'EventBinding' in doc.read_text(encoding='utf-8') and 'VS Code' in doc.read_text(encoding='utf-8'))
check('export/audit gate is registered in final tree verification', 'verify_c11_semantic_export_audit.py' in final_gate)

if len(checks)!=27:
    print(f'C11_INTERNAL_COUNT_MISMATCH={len(checks)}/27')
    sys.exit(2)
failed=[name for name,ok in checks if not ok]
print(f'C11_SEMANTIC_EXPORT_AUDIT={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'C11_SEMANTIC_EXPORT_AUDIT=FAIL {len(failed)}')
if failed:
    for name in failed: print('  - '+name)
    sys.exit(1)
