package com.example.editor.metamodel.importing;

/** Recibe etapas y actividades reales del análisis de importación. */
public interface MetamodelImportProgressListener {

    /** Notifica el inicio de una etapa real del análisis. */
    /** Descripción: Ejecuta la operación «stageStarted» correspondiente a la responsabilidad de MetamodelImportProgressListener. Entradas: stage: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void stageStarted(MetamodelImportAnalysisStage stage, String message, String details) {
    }

    /** Registra una actividad dentro de una etapa real del análisis. */
    /** Descripción: Ejecuta la operación «log» correspondiente a la responsabilidad de MetamodelImportProgressListener. Entradas: stage: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void log(MetamodelImportAnalysisStage stage, MetamodelImportActivitySeverity severity,
            String code, String message, String details) {
    }

    /** Notifica la finalización de una etapa real del análisis. */
    /** Descripción: Ejecuta la operación «stageCompleted» correspondiente a la responsabilidad de MetamodelImportProgressListener. Entradas: stage: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void stageCompleted(MetamodelImportAnalysisStage stage, String message, String details) {
    }

    /** Devuelve un listener vacío. */
    /** Descripción: Ejecuta la operación «none» correspondiente a la responsabilidad de MetamodelImportProgressListener. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ static MetamodelImportProgressListener none() {
        return new MetamodelImportProgressListener() { };
    }
}
