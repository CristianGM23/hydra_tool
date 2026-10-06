#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks=[]
def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
def exists(rel): return (ROOT/rel).is_file()
def check(name, condition):
    ok=bool(condition); checks.append((name,ok)); print(('PASS' if ok else 'FAIL')+' - '+name)

# Fix86 - persisted schema migrations
service='src/main/java/com/example/editor/persistence/evolution/HydraSchemaEvolutionService.java'
plan='src/main/java/com/example/editor/persistence/evolution/HydraSchemaEvolutionPlan.java'
controller='src/main/java/com/example/editor/persistence/evolution/HydraSchemaEvolutionController.java'
check('Fix86 evolution plan contract exists', exists(plan))
check('Fix86 evolution controller exists', exists(controller))
check('Fix86 detects ambiguous migration starts', 'Ambiguous schema evolution' in text(service))
check('Fix86 rejects migration cycles', 'validateAcyclic' in text(service))
check('Fix86 supports explicit target evolution', 'evolveTo(' in text(service))
check('Fix86 exposes a dry planning endpoint', '/api/schema-evolution' in text(controller) and '/plan' in text(controller))

# Fix87 - bounded shared job execution
job_exec='src/main/java/com/example/editor/runtime/job/HydraJobExecutionService.java'
job_cfg='src/main/java/com/example/editor/runtime/job/HydraJobExecutionConfiguration.java'
job_props='src/main/java/com/example/editor/runtime/job/HydraJobProperties.java'
registry_service='src/main/java/com/example/editor/artifact/registry/HydraArtifactRegistryService.java'
check('Fix87 bounded global job executor exists', exists(job_exec) and exists(job_cfg))
check('Fix87 executor uses configured concurrency and queue budgets', 'maximumConcurrent' in text(job_props) and 'maximumQueued' in text(job_props))
check('Fix87 queue rejects overload rather than growing unbounded', 'AbortPolicy' in text(job_cfg))
check('Fix87 provides cooperative cancellation context', exists('src/main/java/com/example/editor/runtime/job/HydraJobContext.java') and 'throwIfCancellationRequested' in text('src/main/java/com/example/editor/runtime/job/HydraJobContext.java'))
check('Fix87 is integrated into a real asynchronous artifact scan', 'scanAsync' in text(registry_service) and 'jobs.submit' in text(registry_service))
check('Fix87 integrates grammar compilation into the shared executor', 'compileAsync' in text('src/main/java/com/example/editor/grammar/web/GrammarCompilationController.java') and 'jobs.submit' in text('src/main/java/com/example/editor/grammar/web/GrammarCompilationController.java'))
check('Fix87 integrates managed toolchain provisioning into the shared executor', 'installAsync' in text('src/main/java/com/example/editor/transformation/nativeexec/TransformationToolchainProvisioningController.java') and 'uninstallAsync' in text('src/main/java/com/example/editor/transformation/nativeexec/TransformationToolchainProvisioningController.java'))

# Fix88 - generic recoverable artifact transactions
transaction='src/main/java/com/example/editor/artifact/transaction/HydraArtifactTransactionService.java'
check('Fix88 generic transaction journal service exists', exists(transaction))
check('Fix88 journals PREPARED before execution', 'prepare(operation, subjectId, resources)' in text(transaction))
check('Fix88 preserves RECOVERY_REQUIRED on failure', 'HydraArtifactTransactionStatus.RECOVERY_REQUIRED' in text(transaction))
check('Fix88 uses atomic journal persistence', 'AtomicFiles.writeReplacing' in text(transaction))
check('Fix88 registry mutations are actually journaled', 'transactions.execute' in text(registry_service))
check('Fix88 transformation create/update/delete are journaled', text('src/main/java/com/example/editor/transformation/TransformationApplicationService.java').count('transactions.execute') >= 3)

# Fix89 - dependency graph is typed and computes transitive impact
edge='src/main/java/com/example/editor/artifact/registry/HydraArtifactGraphEdge.java'
impact='src/main/java/com/example/editor/artifact/registry/HydraArtifactImpact.java'
graph='src/main/java/com/example/editor/artifact/registry/HydraArtifactGraphService.java'
relations='src/main/java/com/example/editor/artifact/registry/HydraArtifactRelationKind.java'
check('Fix89 typed artifact relationship kinds exist', exists(relations) and 'USES_METAMODEL' in text(relations))
check('Fix89 materializes typed graph edges', exists(edge) and 'relationKind' in text(edge))
check('Fix89 exposes transitive impact contract', exists(impact) and 'transitiveDependants' in text(impact))
check('Fix89 calculates safe deletion order', 'deletionOrder(' in text(graph) and 'safeDeletionOrder' in text(impact))

# Fix90 - semantic compatibility/versioning
compat='src/main/java/com/example/editor/artifact/registry/HydraArtifactCompatibilityService.java'
version='src/main/java/com/example/editor/artifact/registry/HydraArtifactVersionPolicy.java'
status='src/main/java/com/example/editor/artifact/registry/HydraArtifactCompatibilityStatus.java'
check('Fix90 compatibility service exists', exists(compat))
check('Fix90 distinguishes migration and revalidation', 'MIGRATION_REQUIRED' in text(status) and 'REVALIDATION_REQUIRED' in text(status))
check('Fix90 checks dependency minimum versions', 'minimumVersion' in text(compat) and 'HydraArtifactVersionPolicy.supports' in text(compat))
check('Fix90 supports unbounded dotted numeric version comparison', 'BigInteger' in text(version))

# Fix91 - incremental/bidirectional consistency
validator='src/main/java/com/example/editor/transformation/runtime/TransformationSynchronizationConsistencyValidator.java'
engine='src/main/java/com/example/editor/transformation/runtime/TransformationIncrementalSynchronizationEngine.java'
check('Fix91 synchronization consistency validator exists', exists(validator))
check('Fix91 validates source/target baseline identity', 'requireIdentity' in text(validator))
check('Fix91 rejects changed metamodel revision without rebase', 'requireMetamodelRevision' in text(validator) and 'rebase' in text(validator))
check('Fix91 validates forward and reverse trace orientation', 'TransformationExecutionDirection.FORWARD' in text(validator) and 'TransformationExecutionDirection.REVERSE' in text(validator))
check('Fix91 rejects ambiguous correspondences', 'ambiguous source correspondence' in text(validator) and 'ambiguous target correspondence' in text(validator))
check('Fix91 is invoked by the incremental engine before delta application', 'TransformationSynchronizationConsistencyValidator().validate(request)' in text(engine))

# Tests / runtime config
expected_tests=[
 'src/test/java/com/example/editor/persistence/evolution/HydraSchemaEvolutionPlanningTests.java',
 'src/test/java/com/example/editor/runtime/job/HydraJobExecutionServiceTests.java',
 'src/test/java/com/example/editor/artifact/transaction/HydraArtifactTransactionServiceTests.java',
 'src/test/java/com/example/editor/artifact/registry/HydraArtifactGraphImpactTests.java',
 'src/test/java/com/example/editor/artifact/registry/HydraArtifactCompatibilityServiceTests.java',
 'src/test/java/com/example/editor/transformation/runtime/TransformationSynchronizationConsistencyValidatorTests.java',
]
check('Fix86-91 focused regression tests all exist', all(exists(item) for item in expected_tests))
props=text('src/main/resources/application.properties')
check('Fix87 bounded job settings are explicit in production config', 'hydra.jobs.maximum-concurrent=' in props and 'hydra.jobs.maximum-queued=' in props)
check('Fix88 artifact transaction directory is explicit in production config', 'hydra.artifact-transactions.directory=' in props)

failed=[name for name,ok in checks if not ok]
print(f"PLATFORM_EVOLUTION_FIX86_91={len(checks)-len(failed)}/{len(checks)} "+('PASS' if not failed else 'FAIL'))
raise SystemExit(1 if failed else 0)
