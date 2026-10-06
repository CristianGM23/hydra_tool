package com.example.editor.integration;
/** Archivo creado o modificado por un paquete de inyección. */
public record InjectionFileChange(String path, InjectionFileChangeKind changeKind, InjectionFileOwnership ownership,
        String originalSha256, String generatedSha256, boolean reversible) {
    public InjectionFileChange {
        if(path == null || path.isBlank() || changeKind == null || ownership == null) throw new IllegalArgumentException("file change requires path, kind and ownership");
        path = path.trim(); originalSha256 = normalize(originalSha256); generatedSha256 = normalize(generatedSha256);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de InjectionFileChange. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
