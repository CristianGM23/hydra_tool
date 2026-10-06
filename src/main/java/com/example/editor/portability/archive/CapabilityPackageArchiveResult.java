package com.example.editor.portability.archive;

import com.example.editor.portability.CapabilityPackageCheckReport;

/** Resultado de exportar un paquete físico, reutilizable por REST y generadores. */
public record CapabilityPackageArchiveResult(String fileName, byte[] archive, String archiveSha256,
        boolean cacheHit, CapabilityPackageArchiveDescriptor descriptor, CapabilityPackageCheckReport checkReport) {
    public CapabilityPackageArchiveResult {
        fileName = fileName == null ? "capabilities.hydra-cap" : fileName;
        archive = archive == null ? new byte[0] : archive.clone();
        archiveSha256 = archiveSha256 == null ? "" : archiveSha256;
    }
    /** Descripción: Ejecuta la operación «archive» correspondiente a la responsabilidad de CapabilityPackageArchiveResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] archive() { return archive.clone(); }
}
