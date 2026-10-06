package com.example.editor.project;

/** Cambio que produciría o produjo una sincronización. */
public record ProjectSyncFileChange(String source,String destination,String operation,ArtifactKind artifactKind,long size) { }
