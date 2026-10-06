package com.example.editor.metamodel.importing.updatesite;

/** Recurso relevante descubierto en un bundle o en la raíz del update site. */
public record UpdateSiteArtifactDescriptor(String path, UpdateSiteArtifactKind kind, String bundleId,
        long sizeBytes, String sha256) {
    public UpdateSiteArtifactDescriptor {
        path = path == null ? "" : path;
        kind = kind == null ? UpdateSiteArtifactKind.OTHER : kind;
        bundleId = bundleId == null ? "" : bundleId;
        sha256 = sha256 == null ? "" : sha256;
    }
}
