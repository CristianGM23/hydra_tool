import type { MetaModel, MetamodelDiagramDocument, TargetConfigurationDocument } from './types';

/**
 * Describe un formato de importación disponible.
 */
export interface MetamodelImportFormatDescriptor {
    id: string;
    displayName: string;
    category: 'SEMANTIC' | 'GRAMMAR' | 'DIAGRAM' | 'DOCUMENTATION' | 'SOURCE_CODE' | 'PROJECT';
    supportLevel: 'EXACT' | 'SEMANTIC' | 'STRUCTURAL';
    extensions: string[];
}

/**
 * Configura la lectura de un archivo.
 */
export interface MetamodelImportOptions {
    formatId: string;
    targetId: string;
    overwrite: boolean;
    preserveLayout: boolean;
    preserveStyles: boolean;
    strict: boolean;
}

/**
 * Describe una advertencia de transformación.
 */
export interface MetamodelImportWarning {
    code: string;
    path: string;
    message: string;
}

/**
 * Resume el contenido reconstruido.
 */
export interface MetamodelImportStatistics {
    classes: number;
    enums: number;
    dataTypes: number;
    attributes: number;
    relations: number;
    diagramNodes: number;
    diagramEdges: number;
}

/**
 * Contiene una importación lista para abrir.
 */
export interface MetamodelImportPreview {
    detectedFormat: string;
    supportLevel: 'EXACT' | 'SEMANTIC' | 'STRUCTURAL';
    metamodel: MetaModel;
    targets: TargetConfigurationDocument;
    diagram: MetamodelDiagramDocument;
    warnings: MetamodelImportWarning[];
    statistics: MetamodelImportStatistics;
}

/**
 * Contiene una importación persistida.
 */
export interface MetamodelImportSaveResult {
    id: string;
    etag: string;
    preview: MetamodelImportPreview;
}

/**
 * Identifica el estado de un análisis asíncrono.
 */
export type MetamodelImportAnalysisStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/**
 * Clasifica una entrada del registro de actividad.
 */
export type MetamodelImportActivitySeverity = 'INFO' | 'WARNING' | 'INFORMATION_LOSS' | 'ERROR';

/**
 * Describe una entrada del progreso de importación.
 */
export interface MetamodelImportActivityMessage {
    timestamp: string;
    progress: number;
    phase: string;
    severity: MetamodelImportActivitySeverity;
    code: string;
    message: string;
    details: string;
}

/**
 * Describe el estado exacto de una etapa.
 */
export type MetamodelImportAnalysisStageStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';

/**
 * Contiene una etapa secuencial o paralela del análisis.
 */
export interface MetamodelImportAnalysisStage {
    stageId: string;
    sequence: number;
    label: string;
    parallelGroup: string;
    status: MetamodelImportAnalysisStageStatus;
    startedAt: string | null;
    completedAt: string | null;
    durationMillis: number;
    activities: MetamodelImportActivityMessage[];
    logAvailable: boolean;
}

/**
 * Contiene el estado completo de un trabajo de análisis.
 */
export interface MetamodelImportAnalysisJob {
    jobId: string;
    status: MetamodelImportAnalysisStatus;
    progress: number;
    phase: string;
    createdAt: string;
    updatedAt: string;
    activities: MetamodelImportActivityMessage[];
    preview: MetamodelImportPreview | null;
    errorMessage: string;
    completedStages: number;
    totalStages: number;
    stages: MetamodelImportAnalysisStage[];
    combinedLogAvailable: boolean;
}
