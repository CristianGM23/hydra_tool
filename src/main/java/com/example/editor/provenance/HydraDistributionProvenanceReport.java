package com.example.editor.provenance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;

/** Immutable provenance report and SBOM attestation for one distribution. */
public record HydraDistributionProvenanceReport(String reportId, String schemaVersion,
        String productId, String version, UpdateSiteMirrorChannel channel,
        HydraLicensePolicy licensePolicy, Instant createdAt,
        List<HydraSbomComponent> components, boolean valid,
        List<String> diagnostics, String attestationSha256,
        Map<String, String> annotations) {
    public HydraDistributionProvenanceReport {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        components = components == null ? List.of() : List.copyOf(components);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
