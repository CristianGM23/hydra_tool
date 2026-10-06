package com.example.editor.project.recovery;

/** Entrada materializada temporalmente durante el recorrido seguro de un contenedor. */
record ExternalProjectArchiveEntry(String path, byte[] content, long size, String sha256) {
    ExternalProjectArchiveEntry {
        path = path == null ? "" : path;
        content = content == null ? new byte[0] : content.clone();
        sha256 = sha256 == null ? "" : sha256;
    }

    /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de ExternalProjectArchiveEntry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ byte[] bytes() {
        return content.clone();
    }
}
