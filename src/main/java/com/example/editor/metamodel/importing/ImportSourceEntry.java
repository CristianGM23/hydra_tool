package com.example.editor.metamodel.importing;

import java.util.Arrays;

/** Archivo fuente seguro recuperado de una carga simple o un ZIP. */
public record ImportSourceEntry(String path, byte[] content) {
    public ImportSourceEntry {
        path = path == null ? "" : path.replace('\\', '/');
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de ImportSourceEntry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
