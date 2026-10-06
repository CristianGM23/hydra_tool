package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Dashboard, logs y resultados por archivo producidos en una sola pasada. */
public record UpdateSiteObservability(UpdateSiteImportDashboard dashboard,
        List<UpdateSiteImportLogEntry> logs, List<UpdateSiteFileOutcome> fileOutcomes,
        List<UpdateSiteGrammarInspection> grammarInspections) {
    public UpdateSiteObservability {
        dashboard = dashboard == null ? UpdateSiteImportDashboard.empty() : dashboard;
        logs = logs == null ? List.of() : List.copyOf(logs);
        fileOutcomes = fileOutcomes == null ? List.of() : List.copyOf(fileOutcomes);
        grammarInspections = grammarInspections == null ? List.of() : List.copyOf(grammarInspections);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteObservability. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteObservability empty() { return new UpdateSiteObservability(UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of()); }
}
