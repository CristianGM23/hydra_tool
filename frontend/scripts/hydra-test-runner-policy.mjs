const REDUNDANT_RUN_ARGUMENT = '--run';
const MAX_BATCH_SIZE = 64;
const SUPPORTED_POOLS = new Set(['forks', 'threads', 'vmForks', 'vmThreads']);

/**
 * `vitest run` is already selected by hydra-test-runner. npm users commonly
 * append `-- --run`; forwarding it is redundant and used to disable batching.
 */
export function normalizeVitestArguments(arguments_) {
    return arguments_.filter(argument => argument !== REDUNDANT_RUN_ARGUMENT);
}

export function shouldBatchFullSuite(profile, arguments_) {
    return profile === 'full' && normalizeVitestArguments(arguments_).length === 0;
}

export function resolveBatchSize(platform, configuredValue) {
    const fallback = platform === 'win32' ? 12 : 24;
    const parsed = Number.parseInt(configuredValue ?? '', 10);
    return Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, MAX_BATCH_SIZE) : fallback;
}

export function resolveBatchPool(platform, configuredValue) {
    if (SUPPORTED_POOLS.has(configuredValue)) return configuredValue;
    return platform === 'win32' ? 'threads' : 'forks';
}

/**
 * Kept for compatibility with existing callers/tests. This is the preferred
 * recovery pool, not the only pool that HYDRA may use after a proven worker
 * bootstrap failure.
 */
export function resolveRecoveryPool(configuredValue) {
    return SUPPORTED_POOLS.has(configuredValue) ? configuredValue : 'threads';
}

/**
 * Ordered pool fallback used only after Vitest itself reports a worker start
 * failure. Assertion/test failures never advance to another pool.
 */
export function resolveRecoveryPools(platform, configuredValue) {
    const preferred = SUPPORTED_POOLS.has(configuredValue)
        ? configuredValue
        : platform === 'win32' ? 'threads' : 'forks';
    const defaults = platform === 'win32'
        ? ['threads', 'forks', 'vmThreads']
        : ['forks', 'threads', 'vmThreads'];
    return [preferred, ...defaults.filter(pool => pool !== preferred)];
}

/**
 * Reorders the fallback list so pools that already failed to bootstrap in
 * this aggressive run are tried after pools that are still healthy. We keep
 * degraded pools as a last resort so the runner can still recover if the
 * environment changes later in the same process.
 */
export function prioritizeRecoveryPools(platform, configuredValue, degradedValues = []) {
    const ordered = resolveRecoveryPools(platform, configuredValue);
    const degraded = new Set(degradedValues);
    return [
        ...ordered.filter(pool => !degraded.has(pool)),
        ...ordered.filter(pool => degraded.has(pool))
    ];
}

const PRODUCT_FAILURE_SIGNATURE = /AssertionError|Test timed out in \d+ms|SyntaxError|ReferenceError|TypeError|JavaScript heap out of memory|ERR_WORKER_OUT_OF_MEMORY|FATAL ERROR:/i;
const VITEST_UNEXPECTED_WORKER_EXIT = /Error:\s*Worker exited unexpectedly[\s\S]{0,1800}(?:Worker\.emitUnexpectedExit|vitest[\\/]dist[\\/]chunks[\\/]cli-api\.)/i;

/**
 * Recognizes Vitest worker lifecycle failures without converting real product
 * failures into infrastructure retries. An abrupt `Worker exited unexpectedly`
 * is recoverable only when the output still points at Vitest's own pool/worker
 * lifecycle and does not already contain an assertion, test timeout, syntax
 * failure or explicit out-of-memory signal.
 */
export function isWorkerInfrastructureFailure(output) {
    const value = String(output ?? '');
    if (PRODUCT_FAILURE_SIGNATURE.test(value)) return false;
    return /\[vitest-pool\]:\s*Failed to start (?:threads|forks|vmThreads|vmForks) worker/i.test(value)
        || /\[vitest-pool-runner\]:\s*Timeout waiting for worker to respond/i.test(value)
        || /Failed to start (?:threads|forks|vmThreads|vmForks) worker for test files/i.test(value)
        || VITEST_UNEXPECTED_WORKER_EXIT.test(value);
}
