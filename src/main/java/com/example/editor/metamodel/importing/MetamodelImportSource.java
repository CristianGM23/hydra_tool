package com.example.editor.metamodel.importing;

import java.util.Arrays;

/**
 * Contiene el archivo recibido por un importador.
 *
 * @param fileName contiene el nombre original.
 * @param mediaType contiene el tipo MIME.
 * @param content contiene los bytes.
 */
public record MetamodelImportSource(String fileName, String mediaType, byte[] content) {

    public MetamodelImportSource {
        if(fileName == null || fileName.isBlank()) {
            fileName = "imported-metamodel";
        }

        if(mediaType == null) {
            mediaType = "application/octet-stream";
        }

        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de MetamodelImportSource. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
