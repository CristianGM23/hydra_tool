import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { MetamodelImportAnalysisJob, MetamodelImportFormatDescriptor, MetamodelImportOptions, MetamodelImportPreview, MetamodelImportSaveResult } from './importTypes';

/**
 * Lee una respuesta JSON o genera un error.
 *
 * @param response contiene la respuesta HTTP.
 * @returns contenido interpretado.
 */
async function readJson<T>(response: Response): Promise<T> {
    if(!response.ok) throw await readActionableApiError(response);

    return response.json() as Promise<T>;
}

/**
 * Devuelve los importadores habilitados.
 *
 * @returns formatos disponibles.
 */
export async function listMetamodelImportFormats(): Promise<MetamodelImportFormatDescriptor[]> {
    const response = await backendFetch('/api/metamodel-imports/formats');
    return readJson<MetamodelImportFormatDescriptor[]>(response);
}

/**
 * Conserva el endpoint síncrono para clientes anteriores.
 *
 * @param file contiene el archivo.
 * @param options contiene la configuración.
 * @returns previsualización editable.
 */
export async function previewMetamodelImport(file: File, options: MetamodelImportOptions): Promise<MetamodelImportPreview> {
    const response = await backendFetch(buildImportUrl('/api/metamodel-imports/preview', options), { method: 'POST', body: formData(file) });
    return readJson<MetamodelImportPreview>(response);
}

/**
 * Inicia un trabajo de análisis.
 *
 * @param file contiene el archivo.
 * @param options contiene la configuración.
 * @param signal permite cancelar la petición.
 * @returns primera instantánea del trabajo.
 */
export async function startMetamodelImportAnalysis(file: File, options: MetamodelImportOptions, signal?: AbortSignal): Promise<MetamodelImportAnalysisJob> {
    const response = await backendFetch(buildImportUrl('/api/metamodel-imports/analysis-jobs', options), { method: 'POST', body: formData(file), signal });
    return readJson<MetamodelImportAnalysisJob>(response);
}

/**
 * Obtiene el estado actual de un trabajo.
 *
 * @param jobId identifica el trabajo.
 * @param signal permite cancelar la petición.
 * @returns instantánea actual.
 */
export async function getMetamodelImportAnalysis(jobId: string, signal?: AbortSignal): Promise<MetamodelImportAnalysisJob> {
    const response = await backendFetch(`/api/metamodel-imports/analysis-jobs/${encodeURIComponent(jobId)}`, { signal });
    return readJson<MetamodelImportAnalysisJob>(response);
}

/**
 * Ejecuta el análisis completo y comunica cada actualización.
 *
 * @param file contiene el archivo.
 * @param options contiene la configuración.
 * @param onUpdate recibe las instantáneas.
 * @param signal permite cancelar el sondeo.
 * @returns previsualización final.
 */
export async function analyzeMetamodelImport(file: File, options: MetamodelImportOptions, onUpdate: (job: MetamodelImportAnalysisJob) => void, signal?: AbortSignal): Promise<MetamodelImportPreview> {
    let job = await startMetamodelImportAnalysis(file, options, signal);
    onUpdate(job);

    while(job.status === 'QUEUED' || job.status === 'RUNNING') {
        await wait(350, signal);
        job = await getMetamodelImportAnalysis(job.jobId, signal);
        onUpdate(job);
    }

    if(job.status === 'COMPLETED' && job.preview !== null) {
        return job.preview;
    }

    throw new Error(job.errorMessage === '' ? 'Metamodel import analysis failed' : job.errorMessage);
}

/**
 * Analiza y guarda un archivo.
 *
 * @param file contiene el archivo.
 * @param options contiene la configuración.
 * @returns resultado persistido.
 */
/**
 * Persiste el metamodelo ya preparado por un trabajo de análisis completado.
 * Reutiliza exactamente el snapshot validado y evita una segunda subida/parsing.
 */
export async function importPreparedMetamodel(jobId: string): Promise<MetamodelImportSaveResult> {
    const response = await backendFetch(`/api/metamodel-imports/analysis-jobs/${encodeURIComponent(jobId)}/import`, { method: 'POST' });
    return readJson<MetamodelImportSaveResult>(response);
}

export async function importAndSaveMetamodel(file: File, options: MetamodelImportOptions): Promise<MetamodelImportSaveResult> {
    const response = await backendFetch(buildImportUrl('/api/metamodel-imports', options), { method: 'POST', body: formData(file) });
    return readJson<MetamodelImportSaveResult>(response);
}

function formData(file: File): FormData {
    const data = new FormData();
    data.append('file', file);
    return data;
}

function buildImportUrl(baseUrl: string, options: MetamodelImportOptions): string {
    const query = new URLSearchParams({
        format: options.formatId,
        targetId: options.targetId,
        overwrite: String(options.overwrite),
        preserveLayout: String(options.preserveLayout),
        preserveStyles: String(options.preserveStyles),
        strict: String(options.strict)
    });
    return `${baseUrl}?${query.toString()}`;
}

/**
 * Espera sin bloquear y admite cancelación.
 *
 * @param milliseconds contiene la espera.
 * @param signal permite cancelar.
 * @returns promesa resuelta al finalizar.
 */
function wait(milliseconds: number, signal?: AbortSignal): Promise<void> {
    return new Promise((resolve, reject) => {
        if(signal?.aborted) {
            reject(new DOMException('The operation was aborted', 'AbortError'));
            return;
        }

        const abort = () => {
            window.clearTimeout(timeout);
            reject(new DOMException('The operation was aborted', 'AbortError'));
        };
        const timeout = window.setTimeout(() => {
            signal?.removeEventListener('abort', abort);
            resolve();
        }, milliseconds);
        signal?.addEventListener('abort', abort, { once: true });
    });
}

/**
 * Descarga el log de una etapa concreta.
 *
 * @param jobId identifica el trabajo.
 * @param stageId identifica la etapa.
 * @returns promesa resuelta al iniciar la descarga.
 */
export async function downloadMetamodelImportStageLog(jobId: string, stageId: string): Promise<void> {
    const response = await backendFetch(`/api/metamodel-imports/analysis-jobs/${encodeURIComponent(jobId)}/stages/${encodeURIComponent(stageId)}/log`);
    await downloadLogResponse(response, `${stageId.toLowerCase()}.log`);
}

/**
 * Descarga el log unificado de todas las etapas.
 *
 * @param jobId identifica el trabajo.
 * @returns promesa resuelta al iniciar la descarga.
 */
export async function downloadMetamodelImportCombinedLog(jobId: string): Promise<void> {
    const response = await backendFetch(`/api/metamodel-imports/analysis-jobs/${encodeURIComponent(jobId)}/logs`);
    await downloadLogResponse(response, 'metamodel-import-analysis.log');
}

/**
 * Interpreta una respuesta de log como descarga.
 *
 * @param response contiene la respuesta.
 * @param fallbackFileName contiene el nombre alternativo.
 * @returns promesa sin valor.
 */
async function downloadLogResponse(response: Response, fallbackFileName: string): Promise<void> {
    if(!response.ok) throw await readActionableApiError(response);

    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = disposition.match(/filename="([^"]+)"/);
    const fileName = match?.[1] ?? fallbackFileName;
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
}

/** Imports a browser-selected directory through the same bounded ZIP pipeline used by server-side metamodel imports. */
export async function importMetamodelBrowserDirectory(files: readonly File[], paths: readonly string[], options: MetamodelImportOptions): Promise<MetamodelImportSaveResult> {
    if(files.length === 0 || files.length !== paths.length) throw new Error('Select a valid metamodel folder before importing');
    const data = new FormData();
    files.forEach((file,index) => { data.append('files',file,file.name); data.append('paths',paths[index] ?? file.name); });
    const response = await backendFetch(buildImportUrl('/api/metamodel-imports/browser-directory', options), { method:'POST', body:data });
    return readJson<MetamodelImportSaveResult>(response);
}
