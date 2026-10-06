package com.example.editor.definition;
/** Definición de editor cargada con su ETag y comprobación. */
public record EditorDefinitionLoadResult(EditorDefinitionModel editorDefinition, String etag, EditorDefinitionCheckReport checkReport) { }
