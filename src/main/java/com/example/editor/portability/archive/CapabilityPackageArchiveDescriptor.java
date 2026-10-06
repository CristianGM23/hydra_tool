package com.example.editor.portability.archive;

import java.util.List;
import java.util.Map;

/** Descriptor reproducible de integridad y autenticidad del archivo físico. */
public record CapabilityPackageArchiveDescriptor(String schemaVersion, String packageId, String packageVersion,
        String contentHash, String signatureAlgorithm, String signatureKeyId, String signature,
        List<CapabilityPackageArchiveEntry> entries, Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";
    public CapabilityPackageArchiveDescriptor {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;
        packageId = packageId == null ? "" : packageId;
        packageVersion = packageVersion == null ? "" : packageVersion;
        contentHash = contentHash == null ? "" : contentHash;
        signatureAlgorithm = signatureAlgorithm == null || signatureAlgorithm.isBlank() ? "NONE" : signatureAlgorithm;
        signatureKeyId = signatureKeyId == null ? "" : signatureKeyId;
        signature = signature == null ? "" : signature;
        entries = entries == null ? List.of() : List.copyOf(entries);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
    /** Descripción: Ejecuta la operación «signed» correspondiente a la responsabilidad de CapabilityPackageArchiveDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean signed() { return !"NONE".equals(signatureAlgorithm) && !signature.isBlank(); }
}
