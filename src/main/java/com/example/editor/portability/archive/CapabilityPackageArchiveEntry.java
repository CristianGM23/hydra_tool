package com.example.editor.portability.archive;

/** Entrada verificable incluida en un archivo .hydra-cap. */
public record CapabilityPackageArchiveEntry(String path, long size, String sha256, String kind) {
    public CapabilityPackageArchiveEntry {
        path = path == null ? "" : path;
        sha256 = sha256 == null ? "" : sha256;
        kind = kind == null ? "" : kind;
    }
}
