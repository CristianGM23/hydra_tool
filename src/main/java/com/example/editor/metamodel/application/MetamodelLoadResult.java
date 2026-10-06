package com.example.editor.metamodel.application;

import com.example.editor.metamodel.core.MetaModel;

/**
 * Agrupa un metamodelo cargado con la versión utilizada para concurrencia.
 */
public record MetamodelLoadResult(MetaModel metamodel, String etag) {
}
