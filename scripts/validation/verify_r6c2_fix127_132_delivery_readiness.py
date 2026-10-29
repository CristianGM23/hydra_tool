#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def read(path): return (ROOT/path).read_text(encoding='utf-8')
def require(name, condition): checks.append((name,bool(condition)))

service=read('src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadinessService.java')
controller=read('src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadinessController.java')
descriptor=read('src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryActionDescriptor.java')
api=read('frontend/src/integration-designer/api.ts')
designer=read('frontend/src/integration-designer/IntegrationDesigner.tsx')
journey=read('frontend/src/integration-designer/IntegrationTechnologyJourney.tsx')
normalizer=read('frontend/src/integration-designer/technologyDeliveryReadinessNormalization.ts')
spanish=read('frontend/src/i18n/currentSpanishCatalogPart19.ts')
openapi=read('docs/api/hydra-openapi.yaml')
generated_client=read('frontend/src/api/generated/hydraApiClient.ts')
openapi_generator=read('scripts/api/generate_openapi.py')
journey_test=read('frontend/src/integration-designer/IntegrationTechnologyJourney.test.tsx')

for path in [
 'src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryActionState.java',
 'src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryActionDescriptor.java',
 'src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadiness.java',
 'src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadinessService.java',
 'src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadinessController.java',
 'src/test/java/com/example/editor/integration/workflow/TechnologyDeliveryReadinessServiceTests.java',
 'src/test/java/com/example/editor/integration/workflow/TechnologyDeliveryReadinessControllerTests.java',
 'frontend/src/integration-designer/technologyDeliveryReadinessNormalization.ts',
 'frontend/src/integration-designer/technologyDeliveryReadinessNormalization.test.ts',
 'frontend/src/i18n/currentSpanishCatalogPart19.ts',
 'docs/integration/TECHNOLOGY_DELIVERY_READINESS.md']:
    require('exists:'+path,(ROOT/path).is_file())

require('readiness-is-read-only', '@GetMapping' in controller and '@PostMapping' not in controller)
require('readiness-route-is-profile-specific','{profileId}/technology/readiness' in controller)
require('profile-check-reused','loaded.checkReport()' in service)
require('side-effect-free-compilation-evidence','integrations.compile(semanticScopeId, profileId)' in service)
require('compilation-failure-is-actionable','catch(TechnologyCompilationException' in service and 'blockers.add(compilationFailure)' in service)
require('states-distinguish-external','TechnologyDeliveryActionState.EXTERNAL' in service)
require('projection-does-not-fake-host-injection','EXPORT_PROJECTION' in service and 'NOT_APPLICABLE' in service)
require('apply-remains-preview-first','previewRequired' in descriptor and 'READY preview required' in service)
require('roundtrip-remains-conflict-aware','Manual conflicts must be resolved explicitly' in service)
require('frontend-readiness-api','loadTechnologyDeliveryReadiness' in api and '/readiness' in api)
require('frontend-structural-validation','validateTechnologyIntegration' in api and "'STRUCTURAL'" in designer)
require('frontend-native-validation-explicit',"'NATIVE_IF_AVAILABLE'" in designer and 'nativeVerificationAvailable' in designer)
require('journey-shows-profile-readiness','Profile delivery readiness' in journey and 'readiness-' in journey)
require('normalizer-fails-closed',"'BLOCKED'" in normalizer and "'UNSUPPORTED'" in normalizer and "'ANTLR'" in normalizer)
require('spanish-delivery-readiness','Preparación de entrega del perfil' in spanish and 'Verificar toolchain nativa' in spanish)
require('api-surface-uses-technology-prefix','/technology/compile' in service and '/technology/validate' in service and '/technology/preview' in service)

require('openapi-publishes-readiness-route','technology/readiness' in openapi and 'TechnologyDeliveryReadinessController_get_get' in generated_client)
require('openapi-generator-is-cross-platform-deterministic',
    'from generated_output import write_generated' in openapi_generator
    and openapi_generator.count('write_generated(') == 3
    and 'ensure_ascii=False' in openapi_generator
    and 'sorted(SRC.rglob' in openapi_generator
    and 'ops.sort(' in openapi_generator
    and 'write_generated' in read('scripts/api/generated_output.py')
    and 'expected = content.encode("utf-8")' in read('scripts/api/generated_output.py')
    and 'if path.read_bytes() == expected:' in read('scripts/api/generated_output.py'))
require('designer-fail-closes-generation-action',"generationReadiness?.state === 'BLOCKED'" in designer and "generationReadiness.reason" in designer)
require('designer-clears-stale-readiness',
    'setReadinessSnapshot(null)' in designer
    and 'setTechnologyValidation(null)' in designer
    and 'readinessSnapshot?.key === readinessKey' in designer
    and 'return () => { active = false; };' in designer)
require('projection-summary-uses-generation-state',"const generationReady=" in journey and 'Technology delivery can be generated now.' in journey and 'does not report projection delivery as blocked' in journey_test)

failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(('PASS ' if ok else 'FAIL ')+name)
if failed:
 print(f'R6C2_FIX127_132_DELIVERY_READINESS={len(checks)-len(failed)}/{len(checks)} FAIL')
 raise SystemExit(1)
print(f'R6C2_FIX127_132_DELIVERY_READINESS={len(checks)}/{len(checks)} PASS')
