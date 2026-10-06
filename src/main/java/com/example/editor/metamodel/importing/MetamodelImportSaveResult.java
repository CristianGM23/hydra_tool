package com.example.editor.metamodel.importing;

/** Devuelve el identificador, la versión canónica y la vista previa persistida. */
public record MetamodelImportSaveResult(String id, String etag, MetamodelImportPreview preview) {
}
