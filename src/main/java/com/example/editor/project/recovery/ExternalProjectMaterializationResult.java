package com.example.editor.project.recovery;

import java.util.List;

/** Resultado explícito de convertir evidencias de un proyecto externo en capas canónicas de HYDRA. */
public record ExternalProjectMaterializationResult(boolean metamodelMaterialized, String metamodelId,
        List<String> editorIds, int visualValidationsImported, int validationRulesImported,
        int quickFixesImported, int reviewOnlyQuickFixes, List<String> materializedSources,
        List<String> diagnostics) {
    public ExternalProjectMaterializationResult {
        metamodelId = metamodelId == null ? "" : metamodelId;
        editorIds = editorIds == null ? List.of() : List.copyOf(editorIds);
        materializedSources = materializedSources == null ? List.of() : List.copyOf(materializedSources);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de ExternalProjectMaterializationResult. Entradas: diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ExternalProjectMaterializationResult empty(List<String> diagnostics) {
        return new ExternalProjectMaterializationResult(false, "", List.of(), 0, 0, 0, 0, List.of(), diagnostics);
    }
}
