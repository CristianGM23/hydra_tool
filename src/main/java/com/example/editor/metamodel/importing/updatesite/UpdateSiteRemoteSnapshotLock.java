package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Reproducible lockfile for a complete bounded remote p2 tree. */
public record UpdateSiteRemoteSnapshotLock(String schemaVersion, String snapshotId, String rootUrl,
        String treeSha256, Instant capturedAt, long totalBytes, List<UpdateSiteRemoteSnapshotResource> resources,
        List<UpdateSiteRemoteSnapshotLicenseEntry> licenses) {
    public UpdateSiteRemoteSnapshotLock {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        resources = resources == null ? List.of() : List.copyOf(resources);
        licenses = licenses == null ? List.of() : List.copyOf(licenses);
    }

    /** Descripción: Ejecuta la operación «licenseReviewComplete» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotLock. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean licenseReviewComplete() {
        return licenses.size() == resources.size() && !licenses.isEmpty()
                && licenses.stream().allMatch(UpdateSiteRemoteSnapshotLicenseEntry::complete);
    }
}
