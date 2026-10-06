package com.example.editor.project.recovery;

import com.example.editor.project.ProjectSession;

/** Resultado de crear un proyecto HYDRA a partir de un proyecto externo analizado y materializado de forma segura. */
public record ExternalProjectImportResult(ProjectSession session, ExternalProjectRecoveryReport recovery,
        ExternalProjectMaterializationResult materialization) {
    public ExternalProjectImportResult {
        materialization = materialization == null ? ExternalProjectMaterializationResult.empty(java.util.List.of()) : materialization;
    }
}
