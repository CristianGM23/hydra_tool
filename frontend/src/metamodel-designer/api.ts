import { backendFetch } from '../api/backendFetch';
import { resolveBackendUrl } from '../api/backendConfig';
import { readActionableApiError } from '../api/actionableError';
import type { CanonicalMetamodelSummary, MetaModel, MetamodelDeletionImpact, MetamodelDeletionResult, MetamodelDiagramDocument, MetamodelExportFormatDescriptor, MetamodelSaveResponse } from './types';
import { measureHydraDiagramAsyncOperation } from '../diagram/performanceTelemetry';

async function readJson<T>(response: Response): Promise<T> {
    if(!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

function ensureCanonical(model: MetaModel): MetaModel {
    if (model.schemaVersion !== '2.1') {
        throw new Error(`Unsupported metamodel schemaVersion: ${String(model.schemaVersion)}`);
    }
    return { ...model, classes: model.classes.map(metaClass => ({ ...metaClass, operations: metaClass.operations ?? [] })) };
}

export async function listCanonicalMetamodels(): Promise<CanonicalMetamodelSummary[]> {
    return readJson<CanonicalMetamodelSummary[]>(await backendFetch('/api/canonical-metamodels'));
}

export async function loadMetaModel(metamodelId: string): Promise<{ metamodel: MetaModel; etag: string }> {
    const response = await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}`);
    const metamodel = ensureCanonical(await readJson<MetaModel>(response));
    return { metamodel, etag: response.headers.get('ETag') ?? '' };
}

export async function createMetaModel(model: MetaModel): Promise<MetamodelSaveResponse> {
    const response = await backendFetch('/api/canonical-metamodels', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(model)
    });
    const result = await readJson<MetamodelSaveResponse>(response);
    ensureCanonical(result.metamodel);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function replaceMetaModel(model: MetaModel, expectedEtag: string): Promise<MetamodelSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(model.id)}`, {
        method: 'PUT', headers, body: JSON.stringify(model)
    });
    const result = await readJson<MetamodelSaveResponse>(response);
    ensureCanonical(result.metamodel);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function loadMetamodelDiagram(metamodelId: string): Promise<MetamodelDiagramDocument> {
    return readJson<MetamodelDiagramDocument>(await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/diagram`));
}

export async function saveMetamodelDiagram(metamodelId: string, document: MetamodelDiagramDocument): Promise<MetamodelDiagramDocument> {
    return measureHydraDiagramAsyncOperation(`metamodel:${metamodelId}`, 'commit', async () => readJson<MetamodelDiagramDocument>(await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/diagram`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(document) })), { nodeCount: document.nodes.length, edgeCount: document.edges.length });
}

export async function loadMetamodelDeletionImpact(metamodelId: string): Promise<MetamodelDeletionImpact> {
    return readJson<MetamodelDeletionImpact>(await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/deletion-impact`));
}

export async function deleteMetamodel(metamodelId: string, deleteNotations: boolean, deleteExports: boolean, expectedEtag: string = ''): Promise<MetamodelDeletionResult> {
    const query = new URLSearchParams({ deleteNotations: String(deleteNotations), deleteExports: String(deleteExports) });
    const headers: Record<string, string> = {};
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    return readJson<MetamodelDeletionResult>(await backendFetch(`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/lifecycle?${query.toString()}`, { method: 'DELETE', headers }));
}

export async function reloadNotationDirectory(): Promise<void> {
    const response = await backendFetch('/api/notations/reload', { method: 'POST' });
    if(!response.ok) throw await readActionableApiError(response);
}

export async function listMetamodelExportFormats(metamodelId: string): Promise<MetamodelExportFormatDescriptor[]> {
    return readJson<MetamodelExportFormatDescriptor[]>(await backendFetch(`/api/metamodels/${encodeURIComponent(metamodelId)}/export-formats`));
}

export function getMetamodelExportUrl(metamodelId: string, formatId: string): string {
    return resolveBackendUrl(`/api/metamodels/${encodeURIComponent(metamodelId)}/exports/${encodeURIComponent(formatId)}`);
}
