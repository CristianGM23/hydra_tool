package com.example.editor.storage;

/** Documento de storage versionado; los bytes son defensivamente copiados. */
public record StorageDocument(byte[] content, String version) {
    public StorageDocument {
        content = content == null ? new byte[0] : content.clone();
        version = version == null ? "" : version;
    }
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de StorageDocument. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content() { return content.clone(); }
}
