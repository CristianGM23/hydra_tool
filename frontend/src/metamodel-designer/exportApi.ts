import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { MetamodelBundleExportRequest, MetamodelExportRequest } from './exportTypes';


export interface GeneratedMetamodelExportArtifact {
    fileName: string;
    blob: Blob;
    contentType: string;
}

/** Genera un formato sin iniciar una descarga, para destinos remotos. */
export async function generateMetamodelExport(metamodelId: string, request: MetamodelExportRequest): Promise<GeneratedMetamodelExportArtifact> {
    const response = await backendFetch(`/api/metamodels/${encodeURIComponent(metamodelId)}/exports`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
    return exportArtifact(response, 'metamodel-export');
}

/** Genera el bundle seleccionado sin descargarlo localmente. */
export async function generateMetamodelExportBundle(metamodelId: string, request: MetamodelBundleExportRequest): Promise<GeneratedMetamodelExportArtifact> {
    const response = await backendFetch(`/api/metamodels/${encodeURIComponent(metamodelId)}/exports/bundle`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
    return exportArtifact(response, 'metamodel-export.zip');
}

/**
 * Descarga un formato usando una petición configurable.
 *
 * @param metamodelId identifica el metamodelo.
 * @param request contiene el formato y las opciones.
 * @returns promesa resuelta al iniciar la descarga.
 */
export async function downloadMetamodelExport(metamodelId: string, request: MetamodelExportRequest): Promise<void> {
    const response = await backendFetch(`/api/metamodels/${encodeURIComponent(metamodelId)}/exports`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
    await downloadResponse(response);
}

/**
 * Descarga un ZIP con los formatos seleccionados.
 *
 * @param metamodelId identifica el metamodelo.
 * @param request contiene los formatos y las opciones.
 * @returns promesa resuelta al iniciar la descarga.
 */
export async function downloadMetamodelExportBundle(metamodelId: string, request: MetamodelBundleExportRequest): Promise<void> {
    const response = await backendFetch(`/api/metamodels/${encodeURIComponent(metamodelId)}/exports/bundle`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
    await downloadResponse(response);
}

/**
 * Interpreta una respuesta como archivo descargable.
 *
 * @param response contiene la respuesta.
 * @returns promesa sin valor.
 */
async function downloadResponse(response: Response): Promise<void> {
    const artifact = await exportArtifact(response, 'metamodel-export');
    const { fileName, blob } = artifact;
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
}

async function exportArtifact(response: Response, fallback: string): Promise<GeneratedMetamodelExportArtifact> {
    if(!response.ok) throw await readActionableApiError(response);
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = disposition.match(/filename="([^"]+)"/);
    const fileName = match?.[1] ?? fallback;
    const blob = await response.blob();
    return { fileName, blob, contentType: blob.type || response.headers.get('Content-Type') || 'application/octet-stream' };
}
