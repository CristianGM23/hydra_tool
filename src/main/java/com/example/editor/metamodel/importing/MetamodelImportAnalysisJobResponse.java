package com.example.editor.metamodel.importing;

import java.time.Instant;
import java.util.List;

/**
 * Devuelve una instantánea segura del trabajo de análisis.
 *
 * @param jobId identifica el trabajo.
 * @param status contiene el estado.
 * @param progress contiene el porcentaje exacto de etapas completadas.
 * @param phase contiene la etapa actual.
 * @param createdAt contiene la creación.
 * @param updatedAt contiene la última actualización.
 * @param activities conserva el registro plano para compatibilidad.
 * @param preview contiene el resultado cuando termina.
 * @param errorMessage contiene el error final.
 * @param completedStages contiene las etapas completadas.
 * @param totalStages contiene el total de etapas.
 * @param stages contiene el detalle secuencial.
 * @param combinedLogAvailable indica si el log completo puede descargarse.
 */
public record MetamodelImportAnalysisJobResponse(String jobId, MetamodelImportAnalysisStatus status, int progress, String phase, Instant createdAt, Instant updatedAt, List<MetamodelImportActivityMessage> activities, MetamodelImportPreview preview, String errorMessage, int completedStages, int totalStages, List<MetamodelImportAnalysisStageResponse> stages, boolean combinedLogAvailable) {

    public MetamodelImportAnalysisJobResponse {
        activities = activities == null ? List.of() : List.copyOf(activities);
        errorMessage = errorMessage == null ? "" : errorMessage;
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /** Descripción: Inicializa una instancia de MetamodelImportAnalysisJobResponse con sus dependencias y datos de entrada. Entradas: jobId: identificador que delimita el recurso objetivo; status: dato de entrada necesario para la operación; progress: dato de entrada necesario para la operación; phase: dato de entrada necesario para la operación; createdAt: dato de entrada necesario para la operación; updatedAt: dato de entrada necesario para la operación; activities: dato de entrada necesario para la operación; preview: dato de entrada necesario para la operación; errorMessage: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportAnalysisJobResponse(String jobId, MetamodelImportAnalysisStatus status, int progress, String phase, Instant createdAt, Instant updatedAt, List<MetamodelImportActivityMessage> activities, MetamodelImportPreview preview, String errorMessage) {
        this(jobId, status, progress, phase, createdAt, updatedAt, activities, preview, errorMessage, 0, MetamodelImportAnalysisStage.values().length, List.of(), !activities.isEmpty());
    }
}
