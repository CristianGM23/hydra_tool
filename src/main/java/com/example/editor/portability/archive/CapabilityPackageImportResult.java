package com.example.editor.portability.archive;

import java.util.List;

import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.HydraCapabilityPackage;

/** Resultado seguro de importar un paquete, incluyendo recursos diferidos. */
public record CapabilityPackageImportResult(HydraCapabilityPackage capabilityPackage,
        CapabilityPackageArchiveDescriptor descriptor, CapabilityPackageCheckReport checkReport,
        String archiveSha256, boolean cacheHit, List<String> deferredResourcePaths, List<String> warnings) {
    public CapabilityPackageImportResult {
        archiveSha256 = archiveSha256 == null ? "" : archiveSha256;
        deferredResourcePaths = deferredResourcePaths == null ? List.of() : List.copyOf(deferredResourcePaths);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
