import { describe, expect, it } from 'vitest';
import { clearHydraDiagramTimingSamples, hydraDiagramTimingSnapshot, measureHydraDiagramAsyncOperation, measureHydraDiagramOperation } from './performanceTelemetry';

describe('diagram performance telemetry', () => {
    it('records synchronous layout/serialization timings without changing results', () => {
        clearHydraDiagramTimingSamples();
        const result = measureHydraDiagramOperation('test', 'layout', () => 42, { nodeCount: 100 });
        expect(result).toBe(42);
        expect(hydraDiagramTimingSnapshot().at(-1)).toMatchObject({ diagramId: 'test', phase: 'layout', nodeCount: 100 });
    });

    it('records asynchronous commit timings on success', async () => {
        clearHydraDiagramTimingSamples();
        await expect(measureHydraDiagramAsyncOperation('test', 'commit', async () => 'saved')).resolves.toBe('saved');
        expect(hydraDiagramTimingSnapshot().at(-1)?.phase).toBe('commit');
    });
});
