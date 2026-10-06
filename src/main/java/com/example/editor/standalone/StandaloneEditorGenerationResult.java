package com.example.editor.standalone;

import java.util.List;

/** Resultado reproducible del generador de editor web. */
public record StandaloneEditorGenerationResult(String fileName, byte[] archive, String sha256,
        StandaloneEditorDescriptor descriptor, List<String> generatedFiles) {
    public StandaloneEditorGenerationResult {
        archive = archive == null ? new byte[0] : archive.clone();
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
    }
    /** Descripción: Ejecuta la operación «archive» correspondiente a la responsabilidad de StandaloneEditorGenerationResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] archive() { return archive.clone(); }
}
