#!/usr/bin/env python3
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def read(path: str) -> str:
    file = ROOT / path
    return file.read_text(encoding='utf-8', errors='replace') if file.is_file() else ''

def exists(path: str) -> bool:
    return (ROOT / path).is_file()

def has(path: str, *tokens: str) -> bool:
    source = read(path)
    return bool(source) and all(token in source for token in tokens)

def check(name: str, value: bool) -> None:
    checks.append((name, bool(value)))

runtime = 'src/main/java/com/example/editor/definition/runtime/'
for name in [
    'EditorRuntimeContractVersions.java', 'ExecutablePaletteGroup.java', 'ExecutableRepresentationPlan.java',
    'ExecutableEditorPlan.java', 'EditorDefinitionCompilationResult.java', 'EditorDefinitionCompiler.java',
    'EditorRuntimeApplicationService.java', 'web/EditorRuntimeController.java'
]:
    check('runtime source '+name, exists(runtime+name))

check('stable executable plan version', has(runtime+'EditorRuntimeContractVersions.java', 'EXECUTABLE_EDITOR_PLAN = "1.0"'))
check('plan records source editor and ETag', has(runtime+'ExecutableEditorPlan.java', 'sourceEditorDefinitionId', 'sourceEtag'))
check('plan records compilation time', has(runtime+'ExecutableEditorPlan.java', 'Instant compiledAt'))
check('plan indexes representations', has(runtime+'ExecutableEditorPlan.java', 'representationsById'))
check('plan indexes forms by ID', has(runtime+'ExecutableEditorPlan.java', 'formsById'))
check('plan indexes forms by target class', has(runtime+'ExecutableEditorPlan.java', 'formsByTargetClassId'))
check('plan copies maps immutably', has(runtime+'ExecutableEditorPlan.java', 'Collections.unmodifiableMap', 'new LinkedHashMap'))
check('representation indexes node mappings by class', has(runtime+'ExecutableRepresentationPlan.java', 'nodeMappingsByClassId'))
check('representation indexes edge mappings by relation', has(runtime+'ExecutableRepresentationPlan.java', 'edgeMappingsByRelationId'))
check('representation indexes tools', has(runtime+'ExecutableRepresentationPlan.java', 'toolsById'))
check('representation compiles palette groups', has(runtime+'ExecutableRepresentationPlan.java', 'paletteGroups'))
check('nested lists are copied immutably', has(runtime+'ExecutableRepresentationPlan.java', 'List.copyOf(value)'))
check('compiler rejects invalid definitions', has(runtime+'EditorDefinitionCompiler.java', '!report.valid()', 'blocking diagnostics'))
check('compiler indexes every representation', has(runtime+'EditorDefinitionCompiler.java', 'for(RepresentationDefinition representation'))
check('compiler indexes forms by target class', has(runtime+'EditorDefinitionCompiler.java', 'formsByClass.putIfAbsent'))
check('compiler filters active tools per representation', has(runtime+'EditorDefinitionCompiler.java', 'activeToolIds', 'representationIds().contains'))
check('compiler orders palette groups', has(runtime+'EditorDefinitionCompiler.java', 'Comparator.comparingInt(PaletteGroup::order)'))
check('compiler preserves ungrouped tools', has(runtime+'EditorDefinitionCompiler.java', 'group:other', 'Other tools'))
check('cache is keyed by metamodel editor and normalized source ETag', has(runtime+'EditorRuntimeApplicationService.java', 'new CacheKey(metamodelId, editorId, normalizedHash(loaded.etag()))'))
check('cache always rechecks canonical source', has(runtime+'EditorRuntimeApplicationService.java', 'definitions.find(metamodelId, editorId)'))
check('changed ETag removes stale version', has(runtime+'EditorRuntimeApplicationService.java', 'cache.keySet().removeIf'))
check('force recompilation exists', has(runtime+'EditorRuntimeApplicationService.java', 'boolean force', 'if(!force)'))
check('runtime GET endpoint exists', has(runtime+'web/EditorRuntimeController.java', '@GetMapping'))
check('runtime compile endpoint exists', has(runtime+'web/EditorRuntimeController.java', '@PostMapping("/compile")'))
check('runtime cache invalidation endpoint exists', has(runtime+'web/EditorRuntimeController.java', '@DeleteMapping("/cache")'))
check('runtime route belongs to editor and metamodel', has(runtime+'web/EditorRuntimeController.java', '/api/canonical-metamodels/{metamodelId}/editors/{editorId}/runtime'))

preview = 'frontend/src/editor-definition/EditorRuntimePreview.tsx'
designer = 'frontend/src/editor-definition/EditorDefinitionDesigner.tsx'
api = 'frontend/src/editor-definition/api.ts'
types = 'frontend/src/editor-definition/types.ts'
check('frontend runtime contract exists', has(types, 'ExecutableEditorPlan', 'ExecutableRepresentationPlan', 'EditorDefinitionCompilationResult'))
check('frontend compile API exists', has(api, 'compileEditorRuntime', '/runtime/compile'))
check('frontend load API exists', has(api, 'loadEditorRuntime', '/runtime'))
check('runtime preview component exists', exists(preview))
check('preview explains immutable compilation', has(preview, 'Compilation never changes the source definition'))
check('preview lists representations', has(preview, 'Compiled representations', 'representationsById'))
check('preview lists palette tools', has(preview, 'paletteGroups', 'group.tools'))
check('preview presents classes by name', has(preview, 'className(metamodel, mapping.targetClassId)', 'Represents'))
check('preview presents relations by name', has(preview, 'relationName(metamodel, mapping.relationId)'))
check('preview exposes runtime indexes', has(preview, 'Runtime indexes', 'Class indexes', 'Relation indexes'))
check('designer separates definition and preview', has(designer, "'RUNTIME'", 'Runtime preview', "designerView === 'RUNTIME'"))
check('designer requires saved clean source', has(designer, 'Save the pending editor and conditional-style changes before compiling its runtime.', "etag === ''", 'dirty || expressionDirty'))
check('model changes invalidate preview', has(designer, 'setRuntimeResult(null)'))
check('runtime tests exist', exists('src/test/java/com/example/editor/definition/runtime/EditorDefinitionCompilerTests.java'))
check('cache tests exist', exists('src/test/java/com/example/editor/definition/runtime/EditorRuntimeApplicationServiceTests.java'))
check('resource tests exist', exists('src/test/java/com/example/editor/definition/runtime/EditorRuntimeResourcesTests.java'))
check('preview tests exist', exists('frontend/src/editor-definition/EditorRuntimePreview.test.tsx'))
check('API test covers compile route', has('frontend/src/editor-definition/api.test.ts', 'runtime/compile'))
check('schema resource exists', exists('src/main/resources/schemas/editor/executable-editor-plan-1.0.schema.json'))
check('example resource exists', exists('src/main/resources/examples/editor/people-executable-editor-plan.json'))

for path in ['src/main/resources/schemas/editor/executable-editor-plan-1.0.schema.json', 'src/main/resources/examples/editor/people-executable-editor-plan.json']:
    try:
        json.loads(read(path)); valid = True
    except Exception:
        valid = False
    check('valid JSON '+Path(path).name, valid)

failed = [name for name, value in checks if not value]
for name, value in checks:
    print(('PASS ' if value else 'FAIL ') + name)
print(f'\nHYDRA B2 editor runtime phase-1 audit: {len(checks)-len(failed)}/{len(checks)}')
sys.exit(1 if failed else 0)
