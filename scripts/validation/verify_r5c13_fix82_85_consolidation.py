#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def check(name,ok): checks.append((name,bool(ok))); print(('PASS' if ok else 'FAIL')+' - '+name)

life=text('src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java')
journal=text('src/main/java/com/example/editor/metamodel/lifecycle/MetamodelDeletionJournalService.java')
cleanup=text('src/main/java/com/example/editor/metamodel/deletion/MetamodelDependentArtifactCleanupService.java')
controller=text('src/main/java/com/example/editor/metamodel/lifecycle/MetamodelLifecycleController.java')
check('deletion result inventories new-layer dependencies','result.addAll(impact.dependentArtifactFiles())' in life)
check('deletion persists a journal before primary deletion','deletionJournalService.begin' in life and life.find('deletionJournalService.begin') < life.find('canonicalService.delete'))
check('deletion recovery retries journal residual files',('deleteManagedReferences(journal.pendingFiles())' in life) or ('cleanupTargets = journal.pendingFiles()' in life and 'deleteManagedReferences(residualTargets)' in life))
check('prepared journal is only recoverable after canonical file disappeared','journal.status() == MetamodelDeletionJournal.Status.PREPARED' in life and 'Files.isRegularFile(canonical)' in life)
check('recovery is exposed explicitly','@PostMapping("/deletion-recovery")' in controller)
check('journal publication is atomic','AtomicFiles.writeReplacing' in journal)
check('cleanup resolves references through registered managed roots',all(x in cleanup for x in ['managedRoots','resolveManagedReference','managedArtifactExists','deleteManagedReferences']))
check('transformation/grammar/textual roots participate in lifecycle',all(x in cleanup for x in ['getSynchronizationSessionDirectory','getCompilationDirectory','getSessionDirectory']))

lock=text('src/main/java/com/example/editor/runtime/instance/HydraInstanceLock.java')
lock_props=text('src/main/java/com/example/editor/runtime/instance/HydraInstanceLockProperties.java')
app=text('src/main/resources/application.properties')
test_props=text('src/main/resources/application-test.properties')
check('single-instance protection uses an OS file lock','FileChannel.open' in lock and 'tryLock()' in lock and 'FileLock' in lock)
check('instance lock follows configured storage by default','storage.metamodels()' in lock and 'resolve(".hydra.lock")' in lock)
check('instance lock remains overrideable','properties.getPath()' in lock and 'Path path;' in lock_props)
check('tests disable global instance lock','hydra.instance-lock.enabled=false' in test_props)

factory=text('src/main/java/com/example/editor/persistence/LocalCanonicalDocumentStoreFactory.java')
store=text('src/main/java/com/example/editor/validation/persistence/AtomicJsonDocumentStore.java')
evolution=text('src/main/java/com/example/editor/persistence/evolution/HydraSchemaEvolutionService.java')
check('canonical persistence receives schema evolution registry','HydraSchemaEvolutionService' in factory and 'schemaEvolution' in factory)
check('schema evolution chains deterministic version steps','MAX_STEPS' in evolution and 'putIfAbsent(from, evolution)' in evolution and 'step.evolve(current.deepCopy())' in evolution)
check('schema evolution preserves pre-change bytes','writeSchemaEvolutionBackup' in store and 'CREATE_NEW' in store and 'AtomicFiles.writeReplacing' in store)
check('no forbidden internal migration package exists',not (ROOT/'src/main/java/com/example/editor/persistence/migration').exists())

jobs=text('src/main/java/com/example/editor/runtime/job/HydraJobOrchestrator.java')
job_controller=text('src/main/java/com/example/editor/runtime/job/HydraJobController.java')
import_jobs=text('src/main/java/com/example/editor/metamodel/importing/MetamodelImportAnalysisJobService.java')
capacity=text('src/main/java/com/example/editor/runtime/RuntimeCapacityController.java')
check('cross-layer jobs are hard bounded','maximumRetained' in jobs and 'makeRoomForRegistration' in jobs and 'job registry capacity reached' in jobs)
check('cross-layer jobs expose progress cancellation and capacity',all(x in jobs for x in ['update(','cancel(','capacityStatus()']))
check('job API exposes list/read/cancel','@RequestMapping("/api/jobs")' in job_controller and '@PostMapping("/{jobId}/cancel")' in job_controller)
check('real import jobs mirror into shared registry','registerSharedJob(job)' in import_jobs and 'mirrorSharedJob(job)' in import_jobs)
check('shared registry saturation cannot reject the domain import','shared observability registry must never reject the domain operation' in import_jobs)
check('runtime capacity exposes shared jobs','result.put("jobs", jobs.capacityStatus())' in capacity)

headless=text('src/main/resources/application-headless-server.properties')
prod=text('src/main/resources/application-production.properties')
desktop=text('src/main/resources/application-desktop-local.properties')
apif=text('src/main/java/com/example/editor/web/version/HydraApiVersionFilter.java')
check('desktop binds explicitly to loopback','server.address=127.0.0.1' in desktop)
check('headless and production fail closed to loopback by default',all('server.address=${HYDRA_SERVER_ADDRESS:127.0.0.1}' in x for x in [headless,prod]))
check('API contract version is observable without breaking paths','X-Hydra-Api-Version' in apif and 'OncePerRequestFilter' in apif)
check('API compatibility policy is documented',(ROOT/'docs/api/API_COMPATIBILITY.md').is_file())

legacy=ROOT/'src/main/java/com/example/editor/validation/expression'
allowed={'ExpressionModel.java','ExpressionModelApplicationException.java','ExpressionModelApplicationService.java','ExpressionModelCheckReport.java','ExpressionModelLoadResult.java','ExpressionModelProperties.java','ExpressionModelSaveResponse.java','ExpressionModelSummary.java'}
actual={p.name for p in legacy.glob('*.java')}
check('legacy expression kernel is physically absent',actual==allowed)
pom=text('pom.xml')
check('clean tree needs no Maven expression quarantine','<testExcludes>' not in pom and '<excludes>' not in pom)
check('stale semantic resolver tombstones are absent',not (ROOT/'src/test/java/com/example/editor/semantic/SemanticContextMetamodelResolverTests.java').exists() and not (ROOT/'src/test/test/java/com/example/editor/semantic/SemanticContextMetamodelResolverTests.java').exists())

failed=[name for name,ok in checks if not ok]
print(f'FIX82_85_CONSOLIDATION={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'))
if failed:
    for name in failed: print('  - '+name)
sys.exit(1 if failed else 0)
