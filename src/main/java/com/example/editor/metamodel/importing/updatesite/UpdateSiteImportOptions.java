package com.example.editor.metamodel.importing.updatesite;

/** Opciones del flujo p2. La selección de capas es independiente del metamodelo. */
public record UpdateSiteImportOptions(String targetId, boolean overwrite, boolean strict,
        String candidateId, boolean recoverPalettes, boolean recoverRepresentations,
        boolean recoverMappings, boolean persistRecoveryPackage) {
    public UpdateSiteImportOptions {
        targetId = targetId == null ? "" : targetId.trim();
        candidateId = candidateId == null ? "" : candidateId.trim();
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de UpdateSiteImportOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteImportOptions defaults() {
        return new UpdateSiteImportOptions("", false, false, "", true, true, true, true);
    }
}
