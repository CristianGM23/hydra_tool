from pathlib import Path
import json,sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name,ok): checks.append((name,bool(ok))); print(('PASS' if ok else 'FAIL')+' - '+name)
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
api=text('src/main/java/com/example/editor/web/ApiExceptionHandler.java')
problem=text('src/main/java/com/example/editor/web/HydraProblemDetails.java')
recovery=text('src/main/java/com/example/editor/web/HydraRecoveryAdvice.java')
frontend=text('frontend/src/api/actionableError.ts')
notice=text('frontend/src/design-system/HydraActionableErrorNotice.tsx')
qual=text('scripts/release/qualify_release.py')
policy=json.loads((ROOT/'config/release-qualification-policy.json').read_text(encoding='utf-8'))
check('Fix99 API errors include impact, retryability, recovery and correlation','recoveryActions' in api and 'correlationId' in api and 'impact' in api and 'retryable' in api)
check('Fix99 problem+json exposes operation and affected resource','operation' in problem and 'affectedResource' in problem and 'HydraRecoveryAction' in problem)
check('Fix99 recovery catalogue never marks generic retry as safe','"RETRY",false' in recovery and 'OPEN_DIAGNOSTICS",true' in recovery)
check('Fix99 frontend consumes structured recovery evidence','recoveryActions' in frontend and 'correlationId' in frontend and 'affectedResource' in frontend)
check('Fix99 actionable notice separates technical detail','hydra-actionable-error-technical' in notice and "t('Technical details')" in notice and 'onRetry' in notice)
check('Fix99 has backend and frontend regression tests',(ROOT/'src/test/java/com/example/editor/web/HydraRecoveryAdviceTests.java').is_file() and 'preserves impact, safe recovery and correlation evidence' in text('frontend/src/api/actionableError.test.ts'))
check('Fix100 has machine-readable qualification policy',policy.get('schemaVersion')=='1.0' and len(policy.get('requiredPhases',[]))>=8)
check('Fix100 qualifies source and extracted package','verify_final_tree.py' in qual and 'extracted-structural-gate' in qual and 'package-integrity' in qual)
check('Fix100 requires reproducible source ZIP and SBOM','reproducible-package' in qual and 'reproducible-sbom' in qual and 'release-certificate.json' in qual)
check('Fix100 names recovery, migration, portability and Spring smoke tests',all(x in str(policy) for x in ['HydraFaultInjectionServiceTests','HydraSchemaEvolutionPlanningTests','ProjectPortableManifestTests','EditorApplicationTests','SystemEndpointsTests']))
workflow=text('.github/workflows/hydra-ci.yml')
check('CI invokes release qualification','qualify_release.py' in workflow and '--full' in workflow)
check('Windows release validator invokes qualification','qualify_release.py' in text('VALIDAR_HYDRA_V4.32.107_WINDOWS.cmd'))
failed=[n for n,o in checks if not o]
print(f'FIX99_100_RELEASE_CLOSURE={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
sys.exit(1 if failed else 0)
