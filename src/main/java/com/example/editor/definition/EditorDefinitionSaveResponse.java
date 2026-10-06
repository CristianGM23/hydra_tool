package com.example.editor.definition;
/** Respuesta de guardado de una definición de editor. */
public record EditorDefinitionSaveResponse(EditorDefinitionModel editorDefinition, String etag, EditorDefinitionCheckReport checkReport) { }
