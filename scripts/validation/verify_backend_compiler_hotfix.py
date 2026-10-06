from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

def check(name: str, condition: bool):
    checks.append((name, condition))
    print(('PASS' if condition else 'FAIL') + ' - ' + name)

visual = (ROOT/'src/main/java/com/example/editor/definition/importing/VisualDefinitionImportService.java').read_text(encoding='utf-8')
result = (ROOT/'src/main/java/com/example/editor/definition/importing/VisualDefinitionImportResult.java').read_text(encoding='utf-8')
code_visual = (ROOT/'src/main/java/com/example/editor/project/recovery/ExternalCodeVisualRecoveryService.java').read_text(encoding='utf-8')
material = (ROOT/'src/main/java/com/example/editor/project/recovery/ExternalProjectSemanticMaterializationService.java').read_text(encoding='utf-8')
fixture = (ROOT/'src/test/java/com/example/editor/definition/importing/SiriusCompleteFixtureImportTests.java').read_text(encoding='utf-8')
runtime_fixture = (ROOT/'src/test/java/com/example/editor/definition/runtime/session/SiriusImportedRepresentationRuntimeTests.java').read_text(encoding='utf-8')

check('lambda feature capture is effectively final', 'final String feature=resolvedFeature;' in visual and 'final String relationExpression=' in visual)
check('neutral MetaClass abstract check uses MetaClassKind', 'metaClass.isAbstract()' not in visual and 'MetaClassKind.ABSTRACT_CLASS' in visual)
check('code visual recovery uses neutral MetaClassKind', 'metaClass.isAbstract()' not in code_visual and 'MetaClassKind.ABSTRACT_CLASS' in code_visual)
check('semantic materializer imports Map', 'import java.util.Map;' in material)
check('visual import result exposes inspected artifacts', 'public List<String> artifacts()' in result)
check('Sirius Java services join source artifact inventory', 'state.artifacts.add(source);' in visual)
check('Sirius fixture asserts source artifacts', 'result.artifacts()' in fixture and 'Services.java' in fixture)
check('visual import constructor no longer retains unused metamodel service field', 'CanonicalMetamodelApplicationService metamodels' not in visual and 'private final CanonicalMetamodelApplicationService' not in visual)
check('Jackson 3 deprecations removed from touched visual importers', '.asText(' not in visual and '.asText(' not in code_visual)
check('fixture constructors use current visual import signature', 'new VisualDefinitionImportService(mapper, analyzer' in fixture and 'mock(com.example.editor.metamodel.application.CanonicalMetamodelApplicationService.class)' not in runtime_fixture)

passed = sum(1 for _, ok in checks if ok)
print(f'BACKEND_COMPILER_HOTFIX={passed}/{len(checks)}')
raise SystemExit(0 if passed == len(checks) else 1)
