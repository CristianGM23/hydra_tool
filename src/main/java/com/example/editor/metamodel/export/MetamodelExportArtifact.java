package com.example.editor.metamodel.export;

/**
 * Representa un archivo generado por un exportador.
 *
 * @param fileName nombre del archivo.
 * @param mediaType tipo MIME.
 * @param content contenido binario.
 */
public record MetamodelExportArtifact(String fileName, String mediaType, byte[] content) {
}
