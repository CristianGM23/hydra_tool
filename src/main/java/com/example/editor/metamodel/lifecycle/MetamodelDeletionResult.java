package com.example.editor.metamodel.lifecycle;

import java.util.List;

/** Resume una eliminación completa o parcial. */
public record MetamodelDeletionResult(String metamodelId, List<String> deletedFiles, List<String> pendingFiles) {
    public MetamodelDeletionResult {
        deletedFiles = deletedFiles == null ? List.of() : List.copyOf(deletedFiles);
        pendingFiles = pendingFiles == null ? List.of() : List.copyOf(pendingFiles);
    }
}
