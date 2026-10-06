package com.example.editor.metamodel.importing;

import java.time.Instant;
import java.util.List;

/**
 * Devuelve el estado y los mensajes de una etapa.
 *
 * @param stageId identifica la etapa.
 * @param sequence contiene su orden.
 * @param label contiene el nombre visible.
 * @param parallelGroup permite agrupar etapas paralelas en el futuro.
 * @param status contiene el estado.
 * @param startedAt contiene el inicio.
 * @param completedAt contiene el final.
 * @param durationMillis contiene la duración conocida.
 * @param activities contiene los mensajes.
 * @param logAvailable indica si el log puede descargarse.
 */
public record MetamodelImportAnalysisStageResponse(String stageId, int sequence, String label, String parallelGroup, MetamodelImportAnalysisStageStatus status, Instant startedAt, Instant completedAt, long durationMillis, List<MetamodelImportActivityMessage> activities, boolean logAvailable) {

    public MetamodelImportAnalysisStageResponse {
        parallelGroup = parallelGroup == null || parallelGroup.isBlank() ? "SEQUENTIAL" : parallelGroup;
        activities = activities == null ? List.of() : List.copyOf(activities);
    }
}
