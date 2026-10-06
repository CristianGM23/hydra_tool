package com.example.editor.metamodel.importing;

import java.nio.file.Path;

/** Referencia a un upload validado y almacenado temporalmente fuera del heap. */
public record MetamodelImportStagedFile(Path path, String fileName, String mediaType, long sizeBytes, String sha256) {
    public MetamodelImportStagedFile {
        if(path == null){throw new IllegalArgumentException("staged path is required");}
        fileName = fileName == null || fileName.isBlank() ? "imported-metamodel" : fileName.trim();
        mediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType.trim();
        sha256 = sha256 == null ? "" : sha256.trim();
    }
}
