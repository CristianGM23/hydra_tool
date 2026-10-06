import { afterEach, describe, expect, it, vi } from 'vitest';

import { analyzeMetamodelImport, startMetamodelImportAnalysis } from './importApi';
import type { MetamodelImportAnalysisJob, MetamodelImportOptions } from './importTypes';

const options: MetamodelImportOptions = { formatId: 'auto', targetId: '', overwrite: false, preserveLayout: true, preserveStyles: true, strict: false };

function response(value: unknown): Response {
    return { ok: true, status: 200, json: vi.fn().mockResolvedValue(value) } as unknown as Response;
}

function job(status: MetamodelImportAnalysisJob['status'], progress: number): MetamodelImportAnalysisJob {
    return { jobId: 'job-1', status, progress, phase: status, createdAt: '2026-07-23T08:00:00Z', updatedAt: '2026-07-23T08:00:00Z', completedStages: status === 'COMPLETED' ? 11 : 4, totalStages: 11, stages: [], combinedLogAvailable: status === 'COMPLETED', activities: [], preview: status === 'COMPLETED' ? { detectedFormat: 'json', supportLevel: 'EXACT', metamodel: {} as never, targets: {} as never, diagram: { metamodelId: 'sample', nodes: [], edges: [] }, warnings: [], statistics: { classes: 0, enums: 0, dataTypes: 0, attributes: 0, relations: 0, diagramNodes: 0, diagramEdges: 0 } } : null, errorMessage: '' };
}

describe('asynchronous metamodel import analysis API', () => {
    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('starts an analysis with multipart data and options', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response(job('QUEUED', 2)));
        const file = new File(['{}'], 'sample.json', { type: 'application/json' });

        await startMetamodelImportAnalysis(file, options);

        expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/metamodel-imports/analysis-jobs?'), expect.objectContaining({ method: 'POST', body: expect.any(FormData) }));
    });

    it('polls until the preview is completed', async () => {
        vi.useFakeTimers();
        const updates: number[] = [];
        vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response(job('RUNNING', 40))).mockResolvedValueOnce(response(job('COMPLETED', 100)));
        const promise = analyzeMetamodelImport(new File(['{}'], 'sample.json'), options, value => updates.push(value.progress));
        await Promise.resolve();
        await vi.advanceTimersByTimeAsync(350);
        const preview = await promise;

        expect(updates).toEqual([40, 100]);
        expect(preview.detectedFormat).toBe('json');
    });
});
