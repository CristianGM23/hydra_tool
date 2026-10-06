package com.example.editor.integration.compatibility;

import java.util.List;

/** Safe, server-owned native verification profile exposed to the UI. */
public record NativeToolchainCatalogEntry(String key, String technology, String version,
        String description, List<String> commandIds) {
    public NativeToolchainCatalogEntry {
        if(key == null || key.isBlank()){throw new IllegalArgumentException("key is required");}
        if(technology == null || technology.isBlank()){throw new IllegalArgumentException("technology is required");}
        version = version == null || version.isBlank() ? "configured" : version.trim();
        description = description == null ? "" : description.trim();
        commandIds = List.copyOf(commandIds == null ? List.of() : commandIds);
    }
}
