package com.example.editor.metamodel.export.project;

/** Huella de un documento exacto usado como entrada de una exportación completa. */
public record MetamodelProjectGenerationArtifactSnapshot(String kind, String documentId, long bytes, String sha256) { }
