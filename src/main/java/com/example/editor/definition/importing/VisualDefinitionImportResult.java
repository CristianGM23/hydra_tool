package com.example.editor.definition.importing;

import java.util.List;
import java.util.Map;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.metamodel.core.MetaModel;

/** Preview and deep analysis of an external visual definition and its explicit semantic metamodel association. */
public record VisualDefinitionImportResult(String technology, EditorDefinitionModel editorDefinition,
        VisualDefinitionImportAnalysis analysis, List<VisualDefinitionImportDiagnostic> diagnostics,
        List<VisualDefinitionImportFix> fixes, Map<String,Integer> recovered, List<String> javaServices,
        List<String> validations, List<VisualImportedValidation> validationDetails, List<String> expressions,
        List<String> preservedArtifacts, MetaModel associatedMetamodel, VisualMetamodelAssociation metamodelAssociation) {
    /** Descripción: Obtiene los nombres de los artefactos fuente inspeccionados durante la importación visual, separados de la semántica externa preservada. Entradas: no requiere parámetros de entrada. Salida: devuelve la lista inmutable de nombres de artefactos inspeccionados. */
    /** Descripción: Ejecuta la operación «artifacts» correspondiente a la responsabilidad de VisualDefinitionImportResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> artifacts() {
        String sources = editorDefinition == null ? "" : editorDefinition.annotations().getOrDefault("hydra.import.sources", "");
        if(sources.isBlank()) return List.of();
        return java.util.Arrays.stream(sources.split("\\s*,\\s*")).filter(value -> !value.isBlank()).toList();
    }

    public VisualDefinitionImportResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
        recovered = recovered == null ? Map.of() : Map.copyOf(recovered);
        javaServices = javaServices == null ? List.of() : List.copyOf(javaServices);
        validations = validations == null ? List.of() : List.copyOf(validations);
        validationDetails = validationDetails == null ? List.of() : List.copyOf(validationDetails);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
        preservedArtifacts = preservedArtifacts == null ? List.of() : List.copyOf(preservedArtifacts);
    }
}
