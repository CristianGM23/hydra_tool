import { mergeConfig } from 'vite';
import { configDefaults, defineConfig } from 'vitest/config';
import viteConfig from './vite.config';

function positiveInteger(value: string | undefined, fallback: number): number {
    const parsed = Number.parseInt(value ?? '', 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function testPool(value: string | undefined): 'forks' | 'threads' | 'vmForks' | 'vmThreads' {
    return value === 'threads' || value === 'vmForks' || value === 'vmThreads' || value === 'forks' ? value : 'forks';
}

const fileParallelism = process.env.HYDRA_TEST_FILE_PARALLELISM === 'true';
const maxWorkers = positiveInteger(process.env.HYDRA_TEST_MAX_WORKERS, 1);
const testTimeout = positiveInteger(process.env.HYDRA_TEST_TIMEOUT_MS, 30_000);
const pool = testPool(process.env.HYDRA_TEST_POOL);

export default mergeConfig(viteConfig, defineConfig({
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
        testTimeout,
        hookTimeout: testTimeout,
        // Pool selection is delegated to the HYDRA runner. Windows full-suite batches
        // default to one isolated worker thread to avoid costly fork start-up timeouts; other
        // platforms keep Vitest's process isolation unless HYDRA_TEST_BATCH_POOL overrides it.
        pool,
        isolate: true,
        // Vitest 5 clears mock history before each test by default; HYDRA keeps the Vitest 4 semantics its suites were written against.
        clearMocks: false,
        maxWorkers,
        fileParallelism,
        exclude: [...configDefaults.exclude, 'e2e/**']
    }
}));
