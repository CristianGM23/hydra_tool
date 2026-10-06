import { backendFetch } from './backendFetch';
import { resolveBackendUrl } from './backendConfig';
import { readActionableApiError } from './actionableError';
import type { EditorBootstrap, MetamodelSummary, ModelDocument, NotationDefinition, NotationSummary, SavedModelSummary, DiagramDocument } from '../types/editor';
import { measureHydraDiagramAsyncOperation } from '../diagram/performanceTelemetry';
/**
 * Lee una respuesta JSON y comprueba el estado HTTP.
 *
 * @param response respuesta recibida del backend.
 * @returns contenido JSON convertido al tipo solicitado.
 */
async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) {
        throw await readActionableApiError(response);
    }

    return response.json() as Promise<T>;
}

/**
 * Obtiene los metamodelos registrados.
 *
 * @returns lista de metamodelos disponibles.
 */
export async function listMetamodels(): Promise<MetamodelSummary[]> {
    const response = await backendFetch('/api/metamodels');
    return readJson<MetamodelSummary[]>(response);
}

/**
 * Obtiene la información inicial del editor.
 *
 * @param metamodelId identificador del metamodelo.
 * @returns información de la paleta y del metamodelo.
 */
export async function loadEditorBootstrap(metamodelId: string): Promise<EditorBootstrap> {
    const response = await backendFetch(`/api/editor/bootstrap/${encodeURIComponent(metamodelId)}`);
    return readJson<EditorBootstrap>(response);
}

/**
 * Crea un documento semántico.
 *
 * @param metamodelId identificador del metamodelo.
 * @param rootClassId identificador de la clase raíz.
 * @returns documento creado.
 */
export async function createModel(metamodelId: string, rootClassId: string): Promise<ModelDocument> {
    const response = await backendFetch('/api/models', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ metamodelId, rootClassId }) });
    return readJson<ModelDocument>(response);
}

/**
 * Añade un elemento contenido.
 *
 * @param modelId identificador del documento.
 * @param containerId identificador del contenedor.
 * @param referenceName nombre de la referencia.
 * @param classId identificador de la clase.
 * @returns documento actualizado.
 */
export async function addModelElement(modelId: string, containerId: string, referenceName: string, classId: string): Promise<ModelDocument> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/elements`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ containerId, referenceName, classId }) });
    return readJson<ModelDocument>(response);
}

/**
 * Modifica un atributo.
 *
 * @param modelId identificador del documento.
 * @param elementId identificador del elemento.
 * @param attributeName nombre del atributo.
 * @param value valor serializado.
 * @returns documento actualizado.
 */
export async function setModelAttribute(modelId: string, elementId: string, attributeName: string, value: string): Promise<ModelDocument> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/attributes`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ elementId, attributeName, value }) });
    return readJson<ModelDocument>(response);
}

/**
 * Establece una referencia semántica.
 *
 * @param modelId identificador del documento.
 * @param sourceId identificador del origen.
 * @param referenceName nombre de la referencia.
 * @param targetId identificador del destino.
 * @returns documento actualizado.
 */
export async function setModelReference(modelId: string, sourceId: string, referenceName: string, targetId: string): Promise<ModelDocument> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/references`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourceId, referenceName, targetId }) });
    return readJson<ModelDocument>(response);
}

/**
 * Guarda un documento como XMI.
 *
 * @param modelId identificador del documento.
 * @returns documento guardado.
 */
export async function saveModel(modelId: string): Promise<ModelDocument> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/save`, { method: 'POST' });
    return readJson<ModelDocument>(response);
}

/**
 * Devuelve los archivos XMI guardados.
 *
 * @returns archivos disponibles.
 */
export async function listSavedModels(): Promise<SavedModelSummary[]> {
    const response = await backendFetch('/api/models/files');
    return readJson<SavedModelSummary[]>(response);
}

/**
 * Abre un archivo XMI.
 *
 * @param metamodelId identificador del metamodelo.
 * @param fileName nombre del archivo XMI.
 * @returns documento abierto.
 */
export async function openSavedModel(metamodelId: string, fileName: string): Promise<ModelDocument> {
    const response = await backendFetch('/api/models/open', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ metamodelId, fileName }) });
    return readJson<ModelDocument>(response);
}

/** Elimina de forma permanente un archivo XMI guardado. */
export async function deleteSavedModel(fileName: string): Promise<void> {
    const response = await backendFetch(`/api/models/files/${encodeURIComponent(fileName)}`, { method: 'DELETE' });
    if (!response.ok) throw await readActionableApiError(response);
}

/**
 * Elimina un elemento semántico.
 *
 * @param modelId identificador del documento.
 * @param elementId identificador del elemento.
 * @returns documento actualizado.
 */
export async function deleteModelElement(modelId: string, elementId: string): Promise<ModelDocument> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/elements/${encodeURIComponent(elementId)}`, { method: 'DELETE' });
    return readJson<ModelDocument>(response);
}

/** Elimina uno o varios elementos semánticos en una única mutación backend. */
export async function deleteModelElements(modelId: string, elementIds: string[]): Promise<ModelDocument> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/elements/delete`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ elementIds }) });
    return readJson<ModelDocument>(response);
}

/**
 * Construye la URL de descarga de un archivo XMI.
 *
 * @param fileName nombre del archivo.
 * @returns URL de descarga.
 */
export function getModelDownloadUrl(fileName: string): string {
    return resolveBackendUrl(`/api/models/files/download?fileName=${encodeURIComponent(fileName)}`);
}

/**
 * Devuelve las notaciones disponibles.
 *
 * @returns lista de notaciones.
 */
export async function listNotations(): Promise<NotationSummary[]> {
    const response = await backendFetch('/api/notations');
    return readJson<NotationSummary[]>(response);
}

/**
 * Obtiene una notación completa.
 *
 * @param notationId identificador de la notación.
 * @returns notación solicitada.
 */
export async function loadNotation(notationId: string): Promise<NotationDefinition> {
    const response = await backendFetch(`/api/notations/${encodeURIComponent(notationId)}`);
    return readJson<NotationDefinition>(response);
}

/**
 * Recarga el directorio de metamodelos del backend.
 *
 * @returns listado actualizado de metamodelos.
 */
export async function reloadMetamodelDirectory(): Promise<MetamodelSummary[]> {
    const response = await backendFetch('/api/metamodels/reload', { method: 'POST' });
    return readJson<MetamodelSummary[]>(response);
}

/**
 * Carga el estado visual de un modelo.
 *
 * @param modelFileName contiene el nombre del archivo XMI.
 * @returns documento visual cargado.
 */
export async function loadDiagram(modelFileName: string): Promise<DiagramDocument> {
    const response = await backendFetch(`/api/diagrams/${encodeURIComponent(modelFileName)}`);
    return readJson<DiagramDocument>(response);
}

/**
 * Guarda el estado visual de un modelo.
 *
 * @param document contiene posiciones, tamaños y estilos.
 * @returns documento visual guardado.
 */
export async function saveDiagram(document: DiagramDocument): Promise<DiagramDocument> {
    return measureHydraDiagramAsyncOperation(`model:${document.modelFileName}`, 'commit', async () => {
        const response = await backendFetch(`/api/diagrams/${encodeURIComponent(document.modelFileName)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ formatVersion: document.formatVersion, notationId: document.notationId, nodes: document.nodes, edges: document.edges }) });
        return readJson<DiagramDocument>(response);
    }, { nodeCount: document.nodes.length, edgeCount: document.edges.length });
}

/**
 * Elimina el estado visual de un modelo.
 *
 * @param modelFileName contiene el nombre del archivo XMI.
 * @returns promesa sin resultado.
 */
export async function deleteDiagram(modelFileName: string): Promise<void> {
    const response = await backendFetch(`/api/diagrams/${encodeURIComponent(modelFileName)}`, { method: 'DELETE' });
    if (response.status === 404) return;
    if (!response.ok) throw await readActionableApiError(response);
}