package com.example.editor.metamodel.canonical;

import java.nio.file.Path;

import com.example.editor.metamodel.core.MetaModel;

/**
 * Agrupa el contenido canónico con los metadatos calculados por el repositorio.
 *
 * @param metamodel contiene el modelo cargado o guardado.
 * @param etag identifica exactamente su contenido JSON.
 * @param path indica el archivo utilizado por la persistencia.
 */
public record StoredMetamodel(MetaModel metamodel, String etag, Path path) {
}
