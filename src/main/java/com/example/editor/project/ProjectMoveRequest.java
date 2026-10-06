package com.example.editor.project;

/** Petición de renombrado o movimiento. */
public record ProjectMoveRequest(String sourcePath,String targetPath,String expectedEtag) { }
