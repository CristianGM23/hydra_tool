package com.example.editor.metamodel.lifecycle;

import java.util.List;

/** Describe todos los artefactos canónicos, visuales y derivados afectados. */
public record MetamodelDeletionImpact(
        String metamodelId,
        String canonicalFile,
        boolean canonicalExists,
        String targetConfigurationFile,
        boolean targetConfigurationExists,
        String projectionManifestFile,
        boolean projectionManifestExists,
        String ecoreFile,
        boolean ecoreExists,
        String diagramFile,
        boolean diagramExists,
        String expressionFile,
        boolean expressionExists,
        String validationFile,
        boolean validationExists,
        String actionFile,
        boolean actionExists,
        String eventDefinitionFile,
        boolean eventDefinitionExists,
        String eventBindingFile,
        boolean eventBindingExists,
        List<String> editorDefinitionFiles,
        List<String> integrationProfileFiles,
        List<String> notationFiles,
        List<String> modelFiles,
        List<String> exportFiles,
        List<String> dependentArtifactFiles,
        String warning) {
    public MetamodelDeletionImpact {
        editorDefinitionFiles = editorDefinitionFiles == null ? List.of() : List.copyOf(editorDefinitionFiles);
        integrationProfileFiles = integrationProfileFiles == null ? List.of() : List.copyOf(integrationProfileFiles);
        notationFiles = notationFiles == null ? List.of() : List.copyOf(notationFiles);
        modelFiles = modelFiles == null ? List.of() : List.copyOf(modelFiles);
        exportFiles = exportFiles == null ? List.of() : List.copyOf(exportFiles);
        dependentArtifactFiles = dependentArtifactFiles == null ? List.of() : List.copyOf(dependentArtifactFiles);
        warning = warning == null ? "" : warning;
    }
}
