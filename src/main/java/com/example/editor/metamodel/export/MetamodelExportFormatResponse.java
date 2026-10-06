package com.example.editor.metamodel.export;

/**
 * Describe un formato que puede mostrar el frontend.
 *
 * @param id identificador utilizado por la API.
 * @param displayName nombre visible.
 * @param extension extensión descargada.
 * @param category categoría funcional.
 */
public record MetamodelExportFormatResponse(String id, String displayName, String extension, String category) {
}
