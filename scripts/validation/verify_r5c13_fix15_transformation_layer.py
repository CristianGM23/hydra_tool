from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

def check(name: str, condition: bool) -> None:
    checks.append((name, bool(condition)))
    print(('PASS' if condition else 'FAIL'), '-', name)

schema = read('src/main/java/com/example/editor/transformation/core/TransformationDefinitionDocument.java')
checker = read('src/main/java/com/example/editor/transformation/TransformationDefinitionChecker.java')
service = read('src/main/java/com/example/editor/transformation/TransformationApplicationService.java')
runtime = read('src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java')
compiler = read('src/main/java/com/example/editor/transformation/runtime/TransformationPlanCompiler.java')
controller = read('src/main/java/com/example/editor/transformation/web/TransformationController.java')
repository = read('src/main/java/com/example/editor/transformation/persistence/JsonTransformationRepository.java')
designer = read('frontend/src/transformation-designer/TransformationDesigner.tsx')
workspace = read('frontend/src/transformation-designer/TransformationMappingWorkspace.tsx')
model = read('frontend/src/transformation-designer/transformationDesignerModel.ts')
registry = read('frontend/src/application-shell/moduleRegistry.ts')
capability = read('frontend/src/application-shell/HydraCapabilityModule.tsx')
spanish = read('frontend/src/i18n/transformationSpanishCatalog.ts')
doc = read('docs/architecture/transformation-layer.md')

check('canonical transformation schema is versioned independently', 'CURRENT_SCHEMA_VERSION = "1.1"' in schema and 'SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0", CURRENT_SCHEMA_VERSION)' in schema)
check('canonical model reserves multiple source and target endpoints', 'List<TransformationEndpoint> sources' in schema and 'List<TransformationEndpoint> targets' in schema)
check('canonical model remains implementation-language neutral', all(token not in schema for token in ['Atl', 'ETL', 'Xtend', 'Acceleo']))
check('canonical document owns execution and trace policies', 'TransformationExecutionPolicy executionPolicy' in schema and 'TransformationTracePolicy tracePolicy' in schema)
check('checker reuses typed semantic expression infrastructure', 'ExpressionTypeChecker' in checker and 'ExpressionCheckResult' in checker)
check('checker validates source and target semantic contexts', 'checkEndpoints(document.sources()' in checker and 'checkEndpoints(document.targets()' in checker)
check('checker rejects unsupported best-effort execution instead of lying about runtime semantics', 'BEST_EFFORT_BINDING_EXECUTION_NOT_SUPPORTED' in checker)
check('checker rejects unsupported non-deterministic execution', 'NON_DETERMINISTIC_EXECUTION_NOT_SUPPORTED' in checker)
check('application service fingerprints both semantic endpoints', 'fingerprint.of(model)' in service)
check('metamodel evolution is surfaced instead of silently refreshed', 'ENDPOINT_METAMODEL_CHANGED' in service and 'fingerprintChanges' in service)
check('repository uses the canonical atomic document store', 'CanonicalDocumentStore' in repository)
check('controller exposes check and persistence endpoints', '/check' in controller and '@PostMapping' in controller and '@PutMapping' in controller)
check('controller exposes canonical execution endpoint', '/{id}/execute' in controller)
check('runtime performs allocation before population', runtime.find('allocate(') < runtime.find('populate('))
check('runtime creates trace links from allocations before binding population', 'TransformationTraceLink' in runtime and runtime.find('TransformationTraceLink') < runtime.find('populate('))
check('runtime resolves reference mappings through trace-equivalent semantics', 'TRACE_EQUIVALENT' in runtime and 'resolveTraceRelation' in runtime)
check('runtime evaluates declarative guards', 'matchesGuard' in runtime)
check('runtime evaluates expression bindings through existing evaluator', 'ExpressionEvaluator' in runtime)
check('compiler fails closed through the checker', 'checker.check' in compiler and 'report.valid()' in compiler)
check('designer uses the shared command bar', 'DesignerCommandBar' in designer)
check('designer uses the shared guided/manual mode switch', 'HydraCreationModeSwitch' in designer)
check('designer uses the shared guided workflow', 'HydraGuidedWorkflow' in designer)
check('guided and advanced modes edit one canonical document state', 'useState<TransformationDefinitionDocument>' in designer and 'mappingWorkspace' in designer)
check('mapping workspace reuses the canonical metamodel structure tree', 'MetamodelStructureTree' in workspace)
check('condition authoring reuses guided and advanced expression builders', 'GuidedConditionBuilder' in workspace and 'ExpressionBuilder' in workspace)
check('mapping workspace presents source and target trees around central rules', workspace.count('<MetamodelPane') >= 2 and 'transformation-mapping-center' in workspace)
check('smart direct mapping is conservative and case-insensitive', "sensitivity: 'base'" in model)
check('transformation designer is a first-class DSL module', "id: 'TRANSFORMATIONS'" in registry and "category: 'DSL'" in registry)
check('transformation module selects its own metamodels', "requiresMetamodel: false" in registry)
check('transformation designer is lazily loaded by the capability shell', 'TransformationDesigner' in capability and "activeModule === 'TRANSFORMATIONS'" in capability)
check('transformation UI has a dedicated Spanish catalogue', 'TRANSFORMATION_SPANISH' in spanish)
check('architecture document records M2M M2T T2M roadmap', all(token in doc for token in ['M2M', 'M2T', 'T2M']))
check('backend has checker unit tests', (ROOT/'src/test/java/com/example/editor/transformation/TransformationDefinitionCheckerTests.java').exists())
check('backend has canonical runtime tests', (ROOT/'src/test/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntimeTests.java').exists())
check('frontend has model and designer contract tests', (ROOT/'frontend/src/transformation-designer/transformationDesignerModel.test.ts').exists() and (ROOT/'frontend/src/transformation-designer/TransformationDesignerContract.test.ts').exists())

passed = sum(ok for _, ok in checks)
print(f'TRANSFORMATION_LAYER_FIX15={passed}/{len(checks)} PASS')
if passed != len(checks):
    sys.exit(1)
