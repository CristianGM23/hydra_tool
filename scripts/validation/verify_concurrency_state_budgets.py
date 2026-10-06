from pathlib import Path
import json
import re,sys
ROOT=Path(__file__).resolve().parents[2]
java=ROOT/'src/main/java'
checks=[]
def check(name,ok): checks.append((name,bool(ok)))
state=json.loads((ROOT/'config/hydra-concurrency-state-budgets.json').read_text(encoding='utf-8'))
actual=sorted(str(p.relative_to(ROOT)).replace('\\','/') for p in java.rglob('*.java') if 'ConcurrentHashMap' in p.read_text(encoding='utf-8',errors='ignore'))
reviewed=sorted(x['path'] for x in state['concurrentMaps'])
check('every dynamic ConcurrentHashMap is inventoried',actual==reviewed)
check('every ConcurrentHashMap has an explicit bound/lifecycle policy',all(x['policy'] in {'HARD_MAX','MAX_TTL','LIFECYCLE'} and x['evidence'].strip() for x in state['concurrentMaps']))
for item in state['concurrentMaps']:
    text=(ROOT/item['path']).read_text(encoding='utf-8')
    evidence=item['evidence']
    if item['policy']=='HARD_MAX':
        check(item['path']+' exposes a maximum/bounded policy',any(k in text for k in ['maximum','Maximum','MAXIMUM','MAX_','maxEntries','maxTrackedModels','getMaximum','getMax']))
    elif item['policy']=='MAX_TTL':
        check(item['path']+' exposes retention/expiry cleanup',any(k in text for k in ['cleanup','purge','retention','expiresAt','idleTimeout']))
    else:
        check(item['path']+' is tied to physical lifecycle cleanup',any(k in text for k in ['retention','Cleanup','cleanup','partition','remove']))
sync=json.loads((ROOT/'config/hydra-synchronization-review.json').read_text(encoding='utf-8'))
actual_sync=[]
for p in java.rglob('*.java'):
    text=p.read_text(encoding='utf-8',errors='ignore')
    if re.search(r'\bsynchronized\b', text): actual_sync.append(str(p.relative_to(ROOT)).replace('\\','/'))
check('every Java file containing synchronized is reviewed',sorted(actual_sync)==sorted(x['path'] for x in sync['reviewedFiles']))
check('all synchronization review entries have a scope and disposition',all(x.get('scope') and x.get('review') for x in sync['reviewedFiles']))
permit=(ROOT/'src/main/java/com/example/editor/concurrent/BoundedPermitPool.java').read_text(encoding='utf-8')
check('shared process/transfer limiter is fixed capacity','Semaphore(this.capacity, true)' in permit and 'Snapshot' in permit)
for rel in ['src/main/java/com/example/editor/integration/verification/ExternalToolProcessRunner.java','src/main/java/com/example/editor/metamodel/importing/python/PythonProcessAstAnalyzer.java','src/main/java/com/example/editor/remote/storage/RemoteStorageService.java']:
    text=(ROOT/rel).read_text(encoding='utf-8'); check(rel+' uses the shared bounded permit pool','BoundedPermitPool' in text and 'capacityStatus' in text)
controller=(ROOT/'src/main/java/com/example/editor/runtime/RuntimeCapacityController.java').read_text(encoding='utf-8')
check('runtime capacity endpoint exposes all critical bounded queues',all(k in controller for k in ['jobs','metamodelImportJobs','externalToolProcesses','pythonAstProcesses','remoteTransfers','auditQueue','editorPendingEvents','editorWorkingCopies']))
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+name)
failed=[n for n,o in checks if not o]
print(f'CONCURRENCY_BUDGETS={len(checks)-len(failed)}/{len(checks)}')
if failed: sys.exit(1)
