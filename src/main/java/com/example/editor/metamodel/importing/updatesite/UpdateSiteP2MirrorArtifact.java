package com.example.editor.metamodel.importing.updatesite;

/** Artifact published by one reproducible offline p2 mirror. */
public record UpdateSiteP2MirrorArtifact(String classifier, String id, String version,
        String path, long bytes, String sha256, String license) {
    public UpdateSiteP2MirrorArtifact {
        classifier = text(classifier); id = text(id); version = text(version); path = text(path);
        sha256 = text(sha256); license = text(license);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteP2MirrorArtifact. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
