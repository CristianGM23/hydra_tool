#!/usr/bin/env python3
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
checks=[]

def text(path): return (ROOT/path).read_text(encoding='utf-8')
def check(name,condition):
    checks.append(bool(condition)); print(('PASS' if condition else 'FAIL')+' - '+name)

process=text('src/main/java/com/example/editor/security/process/HydraProcessTreeTerminator.java')
ext=text('src/main/java/com/example/editor/integration/verification/ExternalToolProcessRunner.java')
neutral=text('src/main/java/com/example/editor/security/process/HydraExternalProcessRunner.java')
py=text('src/main/java/com/example/editor/metamodel/importing/python/PythonProcessAstAnalyzer.java')
git=text('src/main/java/com/example/editor/remote/git/NativeGitRunner.java')
jobs=text('src/main/java/com/example/editor/metamodel/importing/MetamodelImportAnalysisJobService.java')
coord=text('src/main/java/com/example/editor/grammar/application/GrammarAnalysisCoordinator.java')
progress=text('src/main/java/com/example/editor/grammar/application/GrammarCompilationProgressService.java')
audit=text('src/main/java/com/example/editor/audit/DefaultAuditEventPublisher.java')
auth=text('src/main/java/com/example/editor/remote/auth/RemoteAuthService.java')
io=text('src/main/java/com/example/editor/runtime/HydraBlockingIoConfiguration.java')
flow=text('src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java')
game=text('src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java')
props=text('src/main/resources/application.properties')

launchers=[]
for p in (ROOT/'src/main/java').rglob('*.java'):
    s=p.read_text(encoding='utf-8',errors='ignore')
    if 'new ProcessBuilder' in s: launchers.append(p.relative_to(ROOT).as_posix())
check('exactly three explicit native process launch sites remain',len(launchers)==3)
check('process-tree terminator tracks descendants before root exit',all(x in process for x in ['ProcessTreeTracker','snapshot','descendants()','destroyForcibly']))
check('process-tree root status uses Process authority', 'root.isAlive()' in process)
check('external validation delegates to the tracked neutral process runner',
      all(x in ext for x in ['HydraExternalProcessRunner','processRunner.run']) and
      all(x in neutral for x in ['HydraProcessTreeTerminator','track(process','terminateTracked']))
check('Python AST tracks and terminates process trees',all(x in py for x in ['HydraProcessTreeTerminator','track(process','terminateTracked']))
check('native Git tracks and terminates process trees',all(x in git for x in ['HydraProcessTreeTerminator','track(process','terminateTracked']))
check('all native process consumers use bounded permits',all('BoundedPermitPool' in s for s in [ext,py,git]))
check('import jobs own fallback watchdog per compatibility instance','createFallbackWatchdog(), true' in jobs and 'ownsWatchdog' in jobs and 'shutdownOwnedWatchdog' in jobs)
check('import jobs terminalize/cancel deadlines and staged sources',all(x in jobs for x in ['expireExecution','task.cancel(true)','releaseStaged','cancelDeadline']))
check('import job callbacks cannot revive terminal state','if(!job.start(clock.instant()))' in jobs and 'isTerminal()' in jobs)
check('grammar analysis uses bounded executor queue and terminal cancellation',all(x in coord for x in ['ThreadPoolExecutor','getMaximumQueuedAnalyses()','cancel(true)','shutdownNow']))
check('grammar progress exposes explicit terminal states',all(x in progress for x in ['SUCCESS','FAILED','CANCELLED','ensureTerminal']))
check('grammar progress protects active entries from terminal LRU eviction','removeEldestTerminal' in progress or ('RUNNING' in progress and 'trim' in progress))
check('audit storage writes are timeout bounded',all(x in audit for x in ['getWriteTimeoutMillis()','Future','cancel(true)']))
check('audit destroy is idempotent and closes writer','closed.compareAndSet(false, true)' in audit and 'shutdownNow' in audit)
check('OAuth device polling stores terminal outcomes',all(x in auth for x in ['RemoteAuthOutcomeStore','terminalOutcome','devicePollLocks']))
check('OAuth refresh is single-flight and wait bounded',all(x in auth for x in ['refreshLocks','getRefreshLockWait()']))
check('blocking I/O bean cannot wait indefinitely during Spring destruction','destroyMethod = "shutdownNow"' in io)
check('EventFlow releases deployments/subscriptions on shutdown','@PreDestroy' in flow and 'deployments.clear()' in flow and '.close' in flow)
check('Gamification releases event subscription on shutdown','@PreDestroy' in game and 'subscription.close()' in game)
check('completion timeouts/limits are externally configurable',all(k in props for k in [
    'analysis-maximum-duration-seconds','analysis-shutdown-wait-seconds','process-termination-grace-millis',
    'hydra.audit.write-timeout-millis','refresh-lock-wait']))
for harness in [
 'HydraProcessTreeTerminationStressHarness.java','ExternalToolProcessRunnerStressHarness.java',
 'NativeGitRunnerCompletionStressHarness.java','PythonProcessAstAnalyzerCompletionStressHarness.java',
 'MetamodelImportJobCompletionStressHarness.java','GrammarAnalysisCoordinatorCompletionStressHarness.java',
 'GrammarCompilationProgressCompletionStressHarness.java','RemoteAuthDevicePollingCompletionStressHarness.java',
 'DefaultAuditEventPublisherCompletionStressHarness.java','HydraBlockingIoLifecycleStressHarness.java']:
    check('permanent stress harness exists: '+harness,(ROOT/'scripts/validation'/harness).is_file())

passed=sum(checks)
print(f'PROCESS_COMPLETION_CONTRACTS={passed}/{len(checks)} '+('PASS' if passed==len(checks) else 'FAIL'))
raise SystemExit(0 if passed==len(checks) else 1)
