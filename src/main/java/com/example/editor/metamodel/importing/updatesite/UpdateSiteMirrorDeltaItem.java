package com.example.editor.metamodel.importing.updatesite;

/** One plugin or feature delta with before/after checksums and versions. */
public record UpdateSiteMirrorDeltaItem(String coordinate, String classifier,
        String baselineVersion, String candidateVersion, String baselineSha256,
        String candidateSha256, UpdateSiteMirrorDeltaKind kind) {
    public UpdateSiteMirrorDeltaItem {
        coordinate = text(coordinate); classifier = text(classifier);
        baselineVersion = text(baselineVersion); candidateVersion = text(candidateVersion);
        baselineSha256 = text(baselineSha256); candidateSha256 = text(candidateSha256);
        kind = kind == null ? UpdateSiteMirrorDeltaKind.CHANGED : kind;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteMirrorDeltaItem. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
