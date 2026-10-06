import { afterEach, describe, expect, it, vi } from 'vitest';

import { downloadMetamodelImportCombinedLog, downloadMetamodelImportStageLog } from './importApi';

function downloadResponse(fileName: string): Response {
    return {
        ok: true,
        status: 200,
        headers: { get: (name: string) => name.toLowerCase() === 'content-disposition' ? `attachment; filename="${fileName}"` : null },
        blob: vi.fn().mockResolvedValue(new Blob(['log']))
    } as unknown as Response;
}

describe('metamodel import analysis log API', () => {
    afterEach(() => vi.restoreAllMocks());

    it('downloads one stage log', async () => {
        Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:test') });
        Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(downloadResponse('format-detection.log'));

        await downloadMetamodelImportStageLog('job-1', 'FORMAT_DETECTION');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/metamodel-imports/analysis-jobs/job-1/stages/FORMAT_DETECTION/log');
    });

    it('downloads the unified log', async () => {
        Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:test') });
        Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(downloadResponse('analysis.log'));

        await downloadMetamodelImportCombinedLog('job-1');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/metamodel-imports/analysis-jobs/job-1/logs');
    });
});
