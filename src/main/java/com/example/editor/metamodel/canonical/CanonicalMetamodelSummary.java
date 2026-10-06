package com.example.editor.metamodel.canonical;

/**
 * Contiene la información reducida utilizada para listar metamodelos canónicos.
 *
 * @param id contiene el identificador estable.
 * @param name contiene el nombre visible.
 * @param schemaVersion contiene la versión del contrato.
 * @param etag identifica la versión almacenada.
 */
public record CanonicalMetamodelSummary(String id, String name, String schemaVersion, String etag) {
}
