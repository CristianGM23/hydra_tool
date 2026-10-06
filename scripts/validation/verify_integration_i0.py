from pathlib import Path
import json, hashlib, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, condition):
    checks.append((name,bool(condition)))
def text(path): return (ROOT/path).read_text(encoding='utf-8')

def has(path,*parts):
    value=text(path); return all(part in value for part in parts)

# Core contracts
for path in [
 'src/main/java/com/example/editor/integration/IntegrationProfileModel.java',
 'src/main/java/com/example/editor/integration/IntegrationTechnologyCatalog.java',
 'src/main/java/com/example/editor/integration/InjectionPlan.java',
 'src/main/java/com/example/editor/integration/InjectionManifest.java',
 'src/main/java/com/example/editor/integration/IntegrationProfileChecker.java']:
    check(f'exists {path}', (ROOT/path).is_file())
check('profile schema 1.0', has('src/main/java/com/example/editor/integration/IntegrationContractVersions.java','INTEGRATION_PROFILE_MODEL = "1.0"'))
check('injection plan schema 1.0', has('src/main/java/com/example/editor/integration/IntegrationContractVersions.java','INJECTION_PLAN = "1.0"'))
check('manifest schema 1.0', has('src/main/java/com/example/editor/integration/IntegrationContractVersions.java','INJECTION_MANIFEST = "1.0"'))
check('preview mandatory', has('src/main/java/com/example/editor/integration/IntegrationProfileChecker.java','PREVIEW_REQUIRED'))
check('rollback mandatory', has('src/main/java/com/example/editor/integration/IntegrationProfileChecker.java','ROLLBACK_REQUIRED'))
check('plan verification steps', has('src/main/java/com/example/editor/integration/InjectionPlan.java','IntegrationVerificationStep'))
check('plan rejects ready errors', has('src/main/java/com/example/editor/integration/InjectionPlan.java','READY injection plans cannot contain blocking diagnostics'))
check('projection adapter SPI', (ROOT/'src/main/java/com/example/editor/integration/projection/IntegrationProjectionAdapter.java').is_file())
check('runtime bridge SPI', (ROOT/'src/main/java/com/example/editor/integration/bridge/HostIntegrationBridge.java').is_file())

# Technology distinctions
catalog=text('src/main/java/com/example/editor/integration/IntegrationTechnologyCatalog.java')
for tech in ['EMF','SIRIUS_DESKTOP','XTEXT','LANGIUM','BESSER_SDK','BESSER_WEB_MODELING_EDITOR','ECLIPSE_GLSP','ECLIPSE_MODELING_DISTRIBUTION','VSCODE_EXTENSION','MXGRAPH_DRAWIO','PLANTUML']:
    check(f'catalog {tech}', f'IntegrationTechnology.{tech}' in catalog)
check('BESSER SDK is runtime', 'BESSER Python SDK", IntegrationTargetKind.MODELING_RUNTIME' in catalog)
check('BESSER WME is graphical editor', 'BESSER Web Modeling Editor", IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR' in catalog)
check('BESSER SDK has no UI full support', 'BESSER_SDK_UI_TARGET' in text('src/main/java/com/example/editor/integration/IntegrationProfileChecker.java'))
check('PlantUML projection target', 'PlantUML", IntegrationTargetKind.DIAGRAM_PROJECTION' in catalog)
check('PlantUML projection only', 'Set.of(IntegrationDeliveryMode.PROJECTION_ONLY)' in catalog)
check('PlantUML generation only', 'Set.of(InjectionStrategy.GENERATE_PROJECTION)' in catalog)
check('PlantUML runtime forbidden', 'PLANTUML_RUNTIME_FORBIDDEN' in text('src/main/java/com/example/editor/integration/IntegrationProfileChecker.java'))
check('PlantUML event flow projection', 'IntegrationCapability.EVENT_FLOW_PROJECTION' in catalog)
check('PlantUML gamification projection', 'IntegrationCapability.GAMIFICATION_PROJECTION' in catalog)
check('mxGraph draw.io graphical integration target', 'mxGraph / draw.io", IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR' in catalog)
check('mxGraph draw.io adapter exists', (ROOT/'src/main/java/com/example/editor/integration/adapter/mxgraph/MxGraphDrawioTechnologyAdapter.java').is_file())
check('mxGraph draw.io validator exists', (ROOT/'src/main/java/com/example/editor/integration/verification/MxGraphDrawioProjectStructureValidator.java').is_file())
check('mxGraph draw.io safe bridge', has('src/main/java/com/example/editor/integration/adapter/mxgraph/MxGraphDrawioTechnologyAdapter.java','HydraMxGraphRuntime','Draw.loadPlugin','applyQuickFix','executeEventFlow','never uses `eval` or `new Function`'))
check('mxGraph draw.io frontend target', has('frontend/src/integration-designer/types.ts','MXGRAPH_DRAWIO'))
check('technology catalogue publishes supported recommended integration metadata', all(token in text('src/main/java/com/example/editor/integration/IntegrationTechnologyDescriptor.java') for token in ['recommendedDeliveryMode','recommendedInjectionStrategy','recommendedBuildSystem']))
check('guided UI exposes descriptor-driven host integration methods', has('frontend/src/integration-designer/IntegrationMethodChooser.tsx','descriptor.recommendedInjectionStrategy','orderedInjectionStrategies(descriptor)') and has('frontend/src/integration-designer/integrationDesignerOptions.ts','descriptor.injectionStrategies','descriptor.recommendedInjectionStrategy'))
check('mxGraph draw.io guided quick configuration', has('frontend/src/integration-designer/integrationRecipes.ts', "id: 'MXGRAPH_DRAWIO'", "name: 'mxGraph / draw.io'", 'PORTABLE_GRAPHICAL_INJECTABLE') and has('frontend/src/integration-designer/IntegrationDesigner.tsx', 'IntegrationRecipeChooser', 'integrationRecipe'))

# No external framework dependencies in neutral core
core_files=list((ROOT/'src/main/java/com/example/editor/integration').glob('*.java'))+list((ROOT/'src/main/java/com/example/editor/integration/bridge').glob('*.java'))+list((ROOT/'src/main/java/com/example/editor/integration/projection').glob('*.java'))
external_tokens=['org.eclipse.emf','org.eclipse.sirius','org.eclipse.xtext','langium','besser.B','net.sourceforge.plantuml']
check('neutral core has no technology imports', not any(token in p.read_text(encoding='utf-8') for p in core_files for token in external_tokens))

# Resources
for path in [
 'src/main/resources/schemas/integration/integration-profile-model-1.0.schema.json',
 'src/main/resources/schemas/integration/injection-plan-1.0.schema.json',
 'src/main/resources/schemas/integration/injection-manifest-1.0.schema.json',
 'src/main/resources/examples/integration/people-besser-sdk.integration.json',
 'src/main/resources/examples/integration/people-plantuml.integration.json']:
    try: json.loads(text(path)); valid=True
    except Exception: valid=False
    check(f'valid json {path}',valid)
check('BESSER example runtime kind', json.loads(text('src/main/resources/examples/integration/people-besser-sdk.integration.json'))['target']['kind']=='MODELING_RUNTIME')
check('PlantUML example projection only', json.loads(text('src/main/resources/examples/integration/people-plantuml.integration.json'))['deliveryMode']=='PROJECTION_ONLY')

# Persistence, API, lifecycle
check('repository uses canonical atomic store', has('src/main/java/com/example/editor/integration/persistence/JsonIntegrationProfileRepository.java','CanonicalDocumentStore','CanonicalDocumentStoreFactory'))
check('profile API exists', has('src/main/java/com/example/editor/integration/web/CanonicalIntegrationProfileController.java','/api/canonical-metamodels/{metamodelId}/integrations'))
check('technology endpoint exists', has('src/main/java/com/example/editor/integration/web/CanonicalIntegrationProfileController.java','/technologies'))
check('draft check endpoint exists', has('src/main/java/com/example/editor/integration/web/CanonicalIntegrationProfileController.java','/check'))
check('lifecycle lists integration files', has('src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionImpact.java','integrationProfileFiles'))
check('lifecycle deletes integration files', has('src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java','deleteNamedFiles(integrationProperties'))
check('application properties present', has('src/main/resources/application.properties','dsl.integrations.directory=./data/integrations'))

# Frontend
check('integration designer exists', (ROOT/'frontend/src/integration-designer/IntegrationDesigner.tsx').is_file())
check('integration tab wired', has('frontend/src/metamodel-designer/MetamodelDesigner.tsx','INTEGRATIONS','<LazyIntegrationDesigner'))
check('BESSER targets in frontend', has('frontend/src/integration-designer/types.ts','BESSER_SDK','BESSER_WEB_MODELING_EDITOR'))
check('PlantUML client guard', has('frontend/src/integration-designer/integrationProfileValidation.ts','PlantUML cannot execute runtime features'))
check('delete 204 is not parsed', has('frontend/src/integration-designer/api.ts','if(!response.ok)await readValidationJson'))
check('metamodel manager includes integration-profile deletion impact', has('frontend/src/metamodel-designer/MetamodelLibraryDialog.tsx','integrationProfileFiles'))

# Fixed palette must match R6 hash supplied through optional env/base path
base=ROOT.parent.parent/'base_r6'/'editor_V8.4_CORREGIDO_EVENTOS_EDITORES_B0_B1_R6'/'frontend/src/metamodel-designer/MetamodelPalette.tsx'
current=ROOT/'frontend/src/metamodel-designer/MetamodelPalette.tsx'
if base.exists():
    check('fixed metamodel palette unchanged', hashlib.sha256(base.read_bytes()).digest()==hashlib.sha256(current.read_bytes()).digest())
else:
    check('fixed metamodel palette present', current.is_file())

# Tests and docs
check('backend tests', len(list((ROOT/'src/test/java/com/example/editor/integration').rglob('*Tests.java')))>=6)
check('frontend tests', len(list((ROOT/'frontend/src/integration-designer').glob('*.test.*')))>=4)
check('architecture documentation', (ROOT/'docs/architecture/HYDRA_ARCHITECTURE.md').is_file())
check('validation documentation', (ROOT/'docs/validation/HYDRA_VALIDATION.md').is_file())

failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(('PASS ' if ok else 'FAIL ')+name)
print(f'Integration I0 audit: {len(checks)-len(failed)}/{len(checks)}')
if failed: sys.exit(1)
