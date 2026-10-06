package com.example.editor.codegen;

/** Vista previa textual de un artefacto de generación sin iniciar una descarga. */
public record ModelCodeGenerationPreview(String fileName, String mediaType, String content, int selectedElementCount) { }
