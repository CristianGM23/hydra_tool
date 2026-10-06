package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Reproducible manifest for a self-contained local p2 repository. */
public record UpdateSiteP2MirrorManifest(String schemaVersion, String mirrorId, String snapshotId,
        String snapshotTreeSha256, String mirrorSha256, Instant createdAt,
        List<UpdateSiteP2MirrorArtifact> artifacts, String contentIndexSha256,
        String artifactIndexSha256, boolean licenseReviewComplete) {
    public UpdateSiteP2MirrorManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.trim();
        mirrorId = text(mirrorId); snapshotId = text(snapshotId); snapshotTreeSha256 = text(snapshotTreeSha256);
        mirrorSha256 = text(mirrorSha256); createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        contentIndexSha256 = text(contentIndexSha256); artifactIndexSha256 = text(artifactIndexSha256);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteP2MirrorManifest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
