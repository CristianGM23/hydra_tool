package com.example.editor.integration.injection;

import java.util.Map;

/** Índice interno que permite distinguir código HYDRA regenerable de cambios manuales. */
record ManagedInjectionFileIndex(String schemaVersion, Map<String, Entry> files) {
    ManagedInjectionFileIndex {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        files = files == null ? Map.of() : Map.copyOf(files);
    }
    record Entry(String sha256, String manifestId) { }
}
