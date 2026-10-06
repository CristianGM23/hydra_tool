package com.example.editor.project;

import java.time.Instant;
import java.util.List;

/** Índice regenerable del contenido de un proyecto. */
public record ProjectIndexDocument(String schemaVersion,String projectId,Instant generatedAt,List<ProjectIndexedArtifact> artifacts) {
    public ProjectIndexDocument { artifacts = artifacts == null ? List.of() : List.copyOf(artifacts); }
}
