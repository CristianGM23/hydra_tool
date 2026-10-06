#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []
def check(name, condition):
    checks.append((name, bool(condition)))
    print(('PASS' if condition else 'FAIL') + '  ' + name)

api = (ROOT / 'frontend/src/capability-center/api.ts').read_text(encoding='utf-8')
test = (ROOT / 'frontend/src/capability-center/api.test.ts').read_text(encoding='utf-8')
runner = (ROOT / 'frontend/scripts/hydra-test-runner.mjs').read_text(encoding='utf-8')
policy = (ROOT / 'frontend/scripts/hydra-test-runner-policy.mjs').read_text(encoding='utf-8')
policy_test = (ROOT / 'frontend/scripts/hydra-test-runner-policy.test.mjs').read_text(encoding='utf-8')

check('capability API keeps explicit metamodel and grammar routes',
      "segment = contextType === 'GRAMMAR' ? 'grammar' : 'metamodel'" in api and
      '/api/hydra/capabilities/${segment}/${encodeURIComponent(resolvedId)}' in api)
check('production capability API does not clone Response to hide reusable mocks', '.clone()' not in api)
check('capability test returns a fresh Response for every fetch call',
      '.mockImplementation(async (' in test and 'return new Response(' in test and '.mockResolvedValue(new Response(' not in test)
check('capability regression covers both metamodel and grammar calls',
      "loadCapabilityReadiness('people space')" in test and "loadCapabilityReadiness('GRAMMAR', 'orders.dsl')" in test)
check('runner configures a dedicated full-suite batch pool',
      'resolveBatchPool' in runner and 'HYDRA_TEST_BATCH_POOL' in runner and ('HYDRA_TEST_POOL: batchPool' in runner or 'HYDRA_TEST_POOL: activeBatchPool' in runner))
check('Windows batch pool defaults to threads and remains configurable',
      "platform === 'win32' ? 'threads' : 'forks'" in policy and 'SUPPORTED_POOLS.has(configuredValue)' in policy)
check('runner policy has a permanent Windows batch-pool regression',
      'uses thread batches on Windows by default while keeping the pool configurable' in policy_test)

passed=sum(ok for _,ok in checks)
print(f'FRONTEND_CAPABILITY_RESPONSE_RUNNER={passed}/{len(checks)} ' + ('PASS' if passed==len(checks) else 'FAIL'))
raise SystemExit(0 if passed==len(checks) else 1)
