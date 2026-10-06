package com.example.editor.project;

import java.time.Instant;

/** Cambio externo detectado desde la última consulta del frontend. */
public record ProjectFileChange(ProjectFileChangeType type,String relativePath,ArtifactKind artifactKind,Instant detectedAt,String etag) { }
