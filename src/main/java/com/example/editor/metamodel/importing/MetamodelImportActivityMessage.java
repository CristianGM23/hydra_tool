package com.example.editor.metamodel.importing;

import java.time.Instant;

/**
 * Describe una entrada visible del registro de actividad.
 *
 * @param timestamp contiene el instante.
 * @param progress contiene el progreso exacto de etapas completadas.
 * @param phase identifica la etapa.
 * @param severity contiene la severidad.
 * @param code contiene un código estable.
 * @param message contiene el resumen.
 * @param details contiene la información detallada incluida en el log.
 */
public record MetamodelImportActivityMessage(Instant timestamp, int progress, String phase, MetamodelImportActivitySeverity severity, String code, String message, String details) {

    public MetamodelImportActivityMessage {
        details = details == null ? "" : details;
    }

    /** Descripción: Inicializa una instancia de MetamodelImportActivityMessage con sus dependencias y datos de entrada. Entradas: timestamp: dato de entrada necesario para la operación; progress: dato de entrada necesario para la operación; phase: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportActivityMessage(Instant timestamp, int progress, String phase, MetamodelImportActivitySeverity severity, String code, String message) {
        this(timestamp, progress, phase, severity, code, message, "");
    }
}
