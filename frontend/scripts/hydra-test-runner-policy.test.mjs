import { describe, expect, it } from 'vitest';
import { isWorkerInfrastructureFailure, normalizeVitestArguments, resolveBatchPool, resolveBatchSize, prioritizeRecoveryPools, resolveRecoveryPool, resolveRecoveryPools, shouldBatchFullSuite } from './hydra-test-runner-policy.mjs';

describe('HYDRA frontend test runner policy', () => {
    it('keeps full-suite batching when npm appends the redundant --run argument', () => {
        expect(normalizeVitestArguments(['--run'])).toEqual([]);
        expect(shouldBatchFullSuite('full', ['--run'])).toBe(true);
    });

    it('preserves real Vitest selectors and options', () => {
        expect(normalizeVitestArguments(['--run', 'src/model-editor/ContextualModelEditor.test.tsx']))
            .toEqual(['src/model-editor/ContextualModelEditor.test.tsx']);
        expect(shouldBatchFullSuite('full', ['--run', 'src/model-editor/ContextualModelEditor.test.tsx'])).toBe(false);
    });

    it('uses conservative configurable batches on Windows and bounds unsafe values', () => {
        expect(resolveBatchSize('win32', undefined)).toBe(12);
        expect(resolveBatchSize('linux', undefined)).toBe(24);
        expect(resolveBatchSize('win32', '8')).toBe(8);
        expect(resolveBatchSize('win32', '1000')).toBe(64);
        expect(resolveBatchSize('win32', 'invalid')).toBe(12);
        expect(resolveBatchSize('win32', '0')).toBe(12);
    });



    it('uses thread batches on Windows by default while keeping the pool configurable', () => {
        expect(resolveBatchPool('win32', undefined)).toBe('threads');
        expect(resolveBatchPool('linux', undefined)).toBe('forks');
        expect(resolveBatchPool('win32', 'forks')).toBe('forks');
        expect(resolveBatchPool('win32', 'vmThreads')).toBe('vmThreads');
        expect(resolveBatchPool('win32', 'invalid')).toBe('threads');
    });

    it('uses a thread-based recovery pool unless an allowed pool is configured', () => {
        expect(resolveRecoveryPool(undefined)).toBe('threads');
        expect(resolveRecoveryPool('invalid')).toBe('threads');
        expect(resolveRecoveryPool('vmThreads')).toBe('vmThreads');
        expect(resolveRecoveryPool('forks')).toBe('forks');
    });


    it('falls back across isolated pools only for proven worker lifecycle failures', () => {
        expect(resolveRecoveryPools('win32', undefined)).toEqual(['threads', 'forks', 'vmThreads']);
        expect(resolveRecoveryPools('linux', undefined)).toEqual(['forks', 'threads', 'vmThreads']);
        expect(resolveRecoveryPools('win32', 'forks')).toEqual(['forks', 'threads', 'vmThreads']);
        expect(prioritizeRecoveryPools('win32', 'forks', ['forks']))
            .toEqual(['threads', 'vmThreads', 'forks']);
        expect(prioritizeRecoveryPools('win32', 'threads', ['forks']))
            .toEqual(['threads', 'vmThreads', 'forks']);
        expect(isWorkerInfrastructureFailure('[vitest-pool]: Failed to start threads worker for test files src/api/inputErrorExplanation.test.ts.')).toBe(true);
        expect(isWorkerInfrastructureFailure('[vitest-pool-runner]: Timeout waiting for worker to respond')).toBe(true);
        expect(isWorkerInfrastructureFailure(`node:events:496
      throw er; // Unhandled 'error' event
      ^

Error: Worker exited unexpectedly
    at Worker.emitUnexpectedExit (file:///C:/repo/frontend/node_modules/vitest/dist/chunks/cli-api.BK8pd4xc.js:3023:33)`)).toBe(true);
        expect(isWorkerInfrastructureFailure('AssertionError: expected 2 to be 3')).toBe(false);
        expect(isWorkerInfrastructureFailure('Test timed out in 30000ms')).toBe(false);
        expect(isWorkerInfrastructureFailure(`Error: Worker exited unexpectedly
Worker.emitUnexpectedExit vitest/dist/chunks/cli-api.js
FATAL ERROR: Reached heap limit JavaScript heap out of memory`)).toBe(false);
        expect(isWorkerInfrastructureFailure(`Error: Worker exited unexpectedly
Worker.emitUnexpectedExit vitest/dist/chunks/cli-api.js
AssertionError: expected 2 to be 3`)).toBe(false);
    });
});
