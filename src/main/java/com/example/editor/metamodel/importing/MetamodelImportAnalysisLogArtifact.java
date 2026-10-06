package com.example.editor.metamodel.importing;

/**
 * Contiene un log descargable.
 *
 * @param fileName contiene el nombre.
 * @param mediaType contiene el tipo MIME.
 * @param content contiene el texto.
 */
public record MetamodelImportAnalysisLogArtifact(String fileName, String mediaType, byte[] content) {
}
