import { spawnSync } from 'node:child_process';
import { availableParallelism } from 'node:os';
import { existsSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isWorkerInfrastructureFailure, normalizeVitestArguments, resolveBatchPool, resolveBatchSize, resolveRecoveryPools, shouldBatchFullSuite } from './hydra-test-runner-policy.mjs';

const frontendRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const profile = process.argv[2] ?? 'fast';
const rawPassthrough = process.argv.slice(3);
const passthrough = normalizeVitestArguments(rawPassthrough);
const cpuCount = Math.max(1, availableParallelism());
const profiles = {
    fast: {
        HYDRA_TEST_PROFILE: 'fast',
        HYDRA_TEST_FILE_PARALLELISM: 'false',
        HYDRA_TEST_MAX_WORKERS: '1',
        HYDRA_TEST_TIMEOUT_MS: '10000'
    },
    parallel: {
        HYDRA_TEST_PROFILE: 'parallel',
        HYDRA_TEST_FILE_PARALLELISM: 'true',
        HYDRA_TEST_MAX_WORKERS: String(Math.max(1, Math.floor(cpuCount / 2))),
        HYDRA_TEST_TIMEOUT_MS: '12000'
    },
    full: {
        HYDRA_TEST_PROFILE: 'full',
        HYDRA_TEST_FILE_PARALLELISM: 'false',
        HYDRA_TEST_MAX_WORKERS: '1',
        HYDRA_TEST_TIMEOUT_MS: '30000'
    },
    deterministic: {
        HYDRA_TEST_PROFILE: 'deterministic',
        HYDRA_TEST_FILE_PARALLELISM: 'false',
        HYDRA_TEST_MAX_WORKERS: '1',
        HYDRA_TEST_TIMEOUT_MS: '30000',
        HYDRA_TEST_SEED: '1010'
    }
};
const selected = profiles[profile];
if (selected === undefined) {
    console.error(`Unknown HYDRA frontend test profile: ${profile}`);
    process.exit(2);
}

const executable = process.execPath;
const vitestCli = resolve(frontendRoot, 'node_modules', 'vitest', 'vitest.mjs');
const env = { ...process.env, ...selected };

if (!existsSync(vitestCli)) {
    console.error('[HYDRA] Vitest no está instalado en frontend/node_modules.');
    console.error('[HYDRA] Instala exactamente las dependencias bloqueadas, incluidas las de desarrollo, antes de ejecutar los tests:');
    console.error('        npm.cmd ci --include=dev');
    console.error('[HYDRA] Después repite: npm.cmd test -- --run');
    process.exit(2);
}

function runVitest(arguments_, environmentOverrides = {}) {
    return spawnSync(executable, [vitestCli, 'run', ...arguments_], {
        cwd: frontendRoot,
        env: { ...env, ...environmentOverrides },
        stdio: 'inherit',
        shell: false,
        windowsHide: true
    }).status ?? 1;
}

function runVitestCaptured(arguments_, environmentOverrides = {}) {
    const result = spawnSync(executable, [vitestCli, 'run', ...arguments_], {
        cwd: frontendRoot,
        env: { ...env, ...environmentOverrides },
        encoding: 'utf8',
        maxBuffer: 16 * 1024 * 1024,
        stdio: 'pipe',
        shell: false,
        windowsHide: true
    });
    return {
        status: result.status ?? 1,
        output: `${result.stdout ?? ''}${result.stderr ?? ''}`
    };
}

function printCaptured(result) {
    if (result.output === '') return;
    process.stdout.write(result.output.endsWith('\n') ? result.output : `${result.output}\n`);
}

function collectTests(directory) {
    const tests = [];
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const absolute = join(directory, entry.name);
        if (entry.isDirectory()) tests.push(...collectTests(absolute));
        else if (/\.(?:test|spec)\.(?:[cm]?[jt]s|tsx|jsx)$/.test(entry.name)) tests.push(relative(frontendRoot, absolute));
    }
    return tests;
}

function runExplicitWithInfrastructureRecovery(arguments_) {
    const requestedPool = resolveBatchPool(process.platform, process.env.HYDRA_TEST_POOL);
    const configuredRecovery = process.env.HYDRA_TEST_RECOVERY_POOL ?? requestedPool;
    const fallbackPools = resolveRecoveryPools(process.platform, configuredRecovery);
    const pools = [requestedPool, ...fallbackPools.filter(pool => pool !== requestedPool)];

    let lastStatus = 1;
    for (let index = 0; index < pools.length; index++) {
        const pool = pools[index];
        const result = runVitestCaptured(arguments_, {
            HYDRA_TEST_POOL: pool,
            HYDRA_TEST_FILE_PARALLELISM: 'false',
            HYDRA_TEST_MAX_WORKERS: '1',
            HYDRA_TEST_TIMEOUT_MS: process.env.HYDRA_TEST_TIMEOUT_MS ?? '60000'
        });
        lastStatus = result.status;
        if (result.status === 0) {
            printCaptured(result);
            if (index > 0) {
                console.warn(`[HYDRA] Explicit Vitest run recovered with pool=${pool} after worker lifecycle failures in ${pools.slice(0, index).join(', ')}.`);
            }
            return 0;
        }
        if (!isWorkerInfrastructureFailure(result.output)) {
            printCaptured(result);
            console.error(`[HYDRA] Explicit frontend test run failed with pool=${pool}. This is a product/test failure, not a worker infrastructure failure.`);
            return result.status;
        }
        console.warn(`[HYDRA] Explicit Vitest worker lifecycle failed with pool=${pool}; trying the next isolated pool.`);
    }

    console.error(`[HYDRA] Explicit frontend test run could not start with any recovery pool (${pools.join(', ')}). No product assertion is being reported as the cause.`);
    return lastStatus;
}

if (!shouldBatchFullSuite(profile, rawPassthrough)) process.exit(runExplicitWithInfrastructureRecovery(passthrough));

// Restart Vitest between bounded groups so a Windows/V8 worker failure or a
// JSDOM leak cannot turn dozens of unrelated files into misleading "0 test" failures.
const batchSize = resolveBatchSize(process.platform, process.env.HYDRA_TEST_BATCH_SIZE);
const batchPool = resolveBatchPool(process.platform, process.env.HYDRA_TEST_BATCH_POOL);
let activeBatchPool = batchPool;
const files = [...collectTests(resolve(frontendRoot, 'src')), ...collectTests(resolve(frontendRoot, 'scripts'))].sort();
console.log(`[HYDRA] Full frontend suite: ${files.length} files in batches of ${batchSize} with pool=${activeBatchPool}.`);
for (let index = 0; index < files.length; index += batchSize) {
    const batch = files.slice(index, index + batchSize);
    const number = Math.floor(index / batchSize) + 1;
    const total = Math.ceil(files.length / batchSize);
    console.log(`[HYDRA] Vitest batch ${number}/${total}: ${batch.length} files.`);
    const batchResult = runVitestCaptured(batch, { HYDRA_TEST_POOL: activeBatchPool });
    if (batchResult.status === 0) printCaptured(batchResult);
    if (batchResult.status !== 0) {
        const batchInfrastructureFailure = isWorkerInfrastructureFailure(batchResult.output);
        if (batchInfrastructureFailure) {
            console.warn(`[HYDRA] Vitest worker infrastructure failed in batch ${number}/${total} with pool=${activeBatchPool}. HYDRA will retry the files individually instead of treating that worker failure as a product-test failure.`);
        } else {
            printCaptured(batchResult);
            console.warn(`[HYDRA] Frontend test batch ${number}/${total} failed. Retrying its files individually to identify the exact failing test.`);
        }
        const configuredRecoveryPools = resolveRecoveryPools(process.platform, process.env.HYDRA_TEST_RECOVERY_POOL);
        let preferredRecoveryPool = activeBatchPool;
        let alternatePoolProvedStable = false;
        for (const file of batch) {
            let recovered = false;
            const recoveryPools = [preferredRecoveryPool, ...configuredRecoveryPools.filter(pool => pool !== preferredRecoveryPool)];
            for (let poolIndex = 0; poolIndex < recoveryPools.length; poolIndex++) {
                const recoveryPool = recoveryPools[poolIndex];
                const recoveryResult = runVitestCaptured([file], {
                    HYDRA_TEST_POOL: recoveryPool,
                    HYDRA_TEST_FILE_PARALLELISM: 'false',
                    HYDRA_TEST_MAX_WORKERS: '1',
                    HYDRA_TEST_TIMEOUT_MS: '60000'
                });
                if (recoveryResult.status === 0) {
                    if (poolIndex > 0) {
                        console.warn(`[HYDRA] ${file} recovered with pool=${recoveryPool} after a Vitest worker lifecycle failure in pool=${recoveryPools[poolIndex - 1]}.`);
                        preferredRecoveryPool = recoveryPool;
                        alternatePoolProvedStable = recoveryPool !== activeBatchPool;
                    }
                    recovered = true;
                    break;
                }
                if (!isWorkerInfrastructureFailure(recoveryResult.output)) {
                    printCaptured(recoveryResult);
                    console.error(`[HYDRA] Frontend test recovery found a reproducible test failure in ${file} with pool=${recoveryPool}. This is not classified as a Vitest worker infrastructure failure.`);
                    process.exit(recoveryResult.status);
                }
                console.warn(`[HYDRA] Vitest worker lifecycle failed for ${file} with pool=${recoveryPool}; trying the next isolated pool.`);
            }
            if (!recovered) {
                console.error(`[HYDRA] Could not execute ${file} with any isolated recovery pool (${recoveryPools.join(', ')}).`);
                process.exit(1);
            }
        }
        if (batchInfrastructureFailure && alternatePoolProvedStable && preferredRecoveryPool !== activeBatchPool) {
            console.warn(`[HYDRA] Switching remaining frontend batches from pool=${activeBatchPool} to pool=${preferredRecoveryPool} after isolated recovery proved the alternate pool stable.`);
            activeBatchPool = preferredRecoveryPool;
        }
        console.warn(`[HYDRA] Batch ${number}/${total} recovered: all ${batch.length} files passed individually. The batch failure did not reproduce as a product-test failure.`);
    }
}
console.log(`[HYDRA] Full frontend suite PASS: ${files.length} files executed.`);
