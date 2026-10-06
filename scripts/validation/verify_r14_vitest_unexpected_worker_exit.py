#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks: list[tuple[str, bool]] = []


def check(label: str, condition: bool) -> None:
    checks.append((label, bool(condition)))
    print(('PASS' if condition else 'FAIL') + ' - ' + label)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding='utf-8')


policy = text('frontend/scripts/hydra-test-runner-policy.mjs')
runner = text('frontend/scripts/hydra-test-runner.mjs')
policy_test = text('frontend/scripts/hydra-test-runner-policy.test.mjs')
resize_test = text('frontend/src/designer-ui/useHydraModelEditorNodeResize.test.tsx')

check('policy recognizes Vitest unexpected worker exits', 'Worker exited unexpectedly' in policy and 'VITEST_UNEXPECTED_WORKER_EXIT' in policy)
check('unexpected exit requires Vitest worker lifecycle evidence', 'emitUnexpectedExit' in policy and 'vitest' in policy)
check('real product failures take precedence over infrastructure fallback', 'PRODUCT_FAILURE_SIGNATURE' in policy and 'AssertionError' in policy and 'Test timed out' in policy and 'heap out of memory' in policy)
check('runner can promote a proven recovery pool for remaining batches', 'activeBatchPool' in runner and 'Switching remaining frontend batches' in runner and 'preferredRecoveryPool' in runner)
check('reported useHydraModelEditorNodeResize worker signature is permanently covered', 'useHydraModelEditorNodeResize.test.tsx' not in policy and 'Worker exited unexpectedly' in policy_test)
check('node resize test still covers measurement, settled resize, drag reconciliation and pure reconciliation', all(fragment in resize_test for fragment in [
    'does not persist React Flow measurement-only dimension changes',
    'persists only the settled frame of a real resize',
    'materializes newly added external nodes while another node is being dragged',
    'reconciles semantic additions without discarding active local geometry',
]))
resize_hook = text('frontend/src/designer-ui/useHydraModelEditorNodeResize.ts')
check('node resize hook prevents reference-stable external arrays from feeding back into setNodes', all(fragment in resize_hook for fragment in [
    'hasSameHydraNodeReferences',
    'lastExternalNodesRef',
    'if (hasSameHydraNodeReferences(externalNodes, previousExternalNodes)) return;',
]))
check('node resize regression explicitly covers recreated external arrays without a render loop', all(fragment in resize_test for fragment in [
    'treats recreated arrays with the same immutable node references as the same external snapshot',
    'does not enter an external-sync render loop when the caller recreates the nodes array',
]))

# Direct policy execution avoids depending on Vitest to test Vitest recovery.
node_probe = r'''import { isWorkerInfrastructureFailure } from "./frontend/scripts/hydra-test-runner-policy.mjs";
const reported = `node:events:496\n      throw er; // Unhandled 'error' event\n      ^\n\nError: Worker exited unexpectedly\n    at Worker.emitUnexpectedExit (file:///C:/repo/frontend/node_modules/vitest/dist/chunks/cli-api.BK8pd4xc.js:3023:33)`;
const cases = [
  [reported, true],
  ['[vitest-pool-runner]: Timeout waiting for worker to respond', true],
  ['AssertionError: expected 2 to be 3', false],
  ['Test timed out in 30000ms', false],
  [reported + '\nAssertionError: expected 2 to be 3', false],
  [reported + '\nFATAL ERROR: Reached heap limit JavaScript heap out of memory', false],
  ['Worker exited unexpectedly', false]
];
for (const [value, expected] of cases) if (isWorkerInfrastructureFailure(value) !== expected) process.exit(1);
'''
probe = subprocess.run(['node', '--input-type=module', '-e', node_probe], cwd=ROOT, capture_output=True, text=True)
check('direct policy probe classifies reported crash and rejects assertions/OOMs', probe.returncode == 0)

# Simulate the full runner using a tiny fake Vitest executable. The first threads
# batch and the resize test crash with the exact reported lifecycle signature;
# forks succeeds, and later batches must then use forks automatically.
with tempfile.TemporaryDirectory(prefix='hydra-r14-runner-') as tmp:
    sandbox = Path(tmp)
    (sandbox / 'scripts').mkdir(parents=True)
    (sandbox / 'node_modules/vitest').mkdir(parents=True)
    (sandbox / 'src/designer-ui').mkdir(parents=True)
    (sandbox / 'src/z').mkdir(parents=True)
    shutil.copy2(ROOT / 'frontend/scripts/hydra-test-runner.mjs', sandbox / 'scripts/hydra-test-runner.mjs')
    shutil.copy2(ROOT / 'frontend/scripts/hydra-test-runner-policy.mjs', sandbox / 'scripts/hydra-test-runner-policy.mjs')
    (sandbox / 'src/designer-ui/useHydraModelEditorNodeResize.test.tsx').write_text('', encoding='utf-8')
    for index in range(1, 13):
        (sandbox / f'src/z/dummy{index:02}.test.ts').write_text('', encoding='utf-8')
    (sandbox / 'node_modules/vitest/vitest.mjs').write_text(r'''const args=process.argv.slice(2);
const files=args.filter(a=>/\.(?:test|spec)\./.test(a));
const pool=process.env.HYDRA_TEST_POOL || 'unknown';
const target=files.some(f=>f.includes('useHydraModelEditorNodeResize.test.tsx'));
const crash=()=>{ console.error(`node:events:496\n      throw er; // Unhandled 'error' event\n      ^\n\nError: Worker exited unexpectedly\n    at Worker.emitUnexpectedExit (file:///C:/repo/frontend/node_modules/vitest/dist/chunks/cli-api.BK8pd4xc.js:3023:33)`); process.exit(1); };
if(files.length>1 && pool==='threads') crash();
if(target && pool==='threads') crash();
console.log(`FAKE PASS pool=${pool} files=${files.join(',')}`);
''', encoding='utf-8')
    env = dict(os.environ)
    env.update({'HYDRA_TEST_BATCH_POOL': 'threads', 'HYDRA_TEST_RECOVERY_POOL': 'threads', 'HYDRA_TEST_BATCH_SIZE': '6'})
    result = subprocess.run(['node', str(sandbox / 'scripts/hydra-test-runner.mjs'), 'full'], cwd=sandbox, env=env, capture_output=True, text=True)
    output = result.stdout + result.stderr
    check('runner recovers the reported resize-test crash through forks', result.returncode == 0 and 'useHydraModelEditorNodeResize.test.tsx recovered with pool=forks' in output)
    check('runner switches later batches away from the unstable threads pool', 'Switching remaining frontend batches from pool=threads to pool=forks' in output and 'FAKE PASS pool=forks' in output)
    check('runner completes the simulated full suite after infrastructure recovery', 'Full frontend suite PASS: 13 files executed.' in output)

failed = [label for label, ok in checks if not ok]
print(f'R14_VITEST_UNEXPECTED_WORKER_EXIT={len(checks)-len(failed)}/{len(checks)}')
raise SystemExit(1 if failed else 0)
