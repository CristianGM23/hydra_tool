package com.example.editor.project;

/** Coincidencia textual o nominal dentro de un proyecto. */
public record ProjectSearchMatch(String relativePath,ArtifactKind artifactKind,int line,int column,String preview) { }
